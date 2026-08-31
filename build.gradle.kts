plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.palantir.git-version") version "3.0.0"
}

group = "io.fabrikt"
val gitVersion: groovy.lang.Closure<*> by extra
version = gitVersion.call()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

val jacksonVersion = "2.18.10"
val jsonOverlayVersion = "4.0.4"
val guavaVersion = "32.0.1-jre"

val codeGeneration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    // Main dependencies
    implementation("com.reprezen.jsonoverlay:jsonoverlay:$jsonOverlayVersion") {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "commons-cli", module = "commons-cli")
        exclude(group = "com.github.javaparser", module = "javaparser-core")
        exclude(group = "org.eclipse.xtend", module = "org.eclipse.xtend.lib")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("javax.mail:javax.mail-api:1.6.1")
    implementation("com.sun.mail:javax.mail:1.6.1")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Dependencies used by JsonOverlay only while generating model sources
    codeGeneration("com.google.guava:guava:$guavaVersion")
    codeGeneration("commons-cli:commons-cli:1.4")
    codeGeneration("com.github.javaparser:javaparser-core:3.5.7")
    codeGeneration("org.eclipse.xtend:org.eclipse.xtend.lib:2.11.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.guava:guava:$guavaVersion")
    testImplementation("org.skyscreamer:jsonassert:1.5.0")
    testImplementation("org.apache.commons:commons-lang3:3.18.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.test {
    useJUnit()
}

val openApi3PackageDirectory = layout.projectDirectory.dir("src/main/java/com/reprezen/kaizen/oasparser")
val openApi3TypeDefinition = openApi3PackageDirectory.file("types3.yaml")
val openApi3GeneratedSources = fileTree(openApi3PackageDirectory) {
    include("model3/**/*.java", "ovl3/**/*.java")
}

fun File.normalizedGeneratedJava(): String {
    val sourceWithoutImports = readLines()
        .filterNot { it.trimStart().startsWith("import ") }
        .joinToString("\n")
    var quotedBy: Char? = null
    var escaped = false

    return buildString {
        sourceWithoutImports.forEach { character ->
            when {
                quotedBy == null && (character == '"' || character == '\'') -> {
                    quotedBy = character
                    append(character)
                }
                quotedBy != null -> {
                    append(character)
                    when {
                        escaped -> escaped = false
                        character == '\\' -> escaped = true
                        character == quotedBy -> quotedBy = null
                    }
                }
                !character.isWhitespace() -> append(character)
            }
        }
    }
}

fun File.externalImports(): Set<String> = readLines()
    .map { it.trim() }
    .filter { it.startsWith("import ") }
    .map { it.removePrefix("import ").removeSuffix(";") }
    .filterNot { it.startsWith("com.reprezen.kaizen.oasparser.model3.") }
    .filterNot { it.startsWith("com.reprezen.kaizen.oasparser.ovl3.") }
    .toSet()

val verificationPackageDirectory = layout.buildDirectory.dir("openapi3-codegen-verification/com/reprezen/kaizen/oasparser")

val generateOpenApi3InTemporaryDirectory by tasks.registering(JavaExec::class) {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath + codeGeneration
    mainClass.set("com.reprezen.kaizen.oasparser.GenOpenApi3")
    workingDir = projectDir
    inputs.file(openApi3TypeDefinition)
    inputs.files(openApi3GeneratedSources)
    outputs.dir(verificationPackageDirectory)

    doFirst {
        val destination = verificationPackageDirectory.get().asFile
        delete(destination)
        copy {
            from(openApi3PackageDirectory)
            include("model3/**/*.java", "ovl3/**/*.java")
            into(destination)
        }
        setArgs(listOf(destination.absolutePath))
    }
}

val generateOpenApi3 by tasks.registering {
    group = "code generation"
    description = "Regenerates structurally changed OpenAPI 3 model sources from types3.yaml."
    dependsOn(generateOpenApi3InTemporaryDirectory)

    doLast {
        val destinationRoot = openApi3PackageDirectory.asFile
        val generatedRoot = verificationPackageDirectory.get().asFile
        val updated = fileTree(generatedRoot) {
            include("model3/**/*.java", "ovl3/**/*.java")
        }.files.mapNotNull { generatedFile ->
            val relativePath = generatedFile.relativeTo(generatedRoot).invariantSeparatorsPath
            val destinationFile = destinationRoot.resolve(relativePath)
            if (!destinationFile.exists() ||
                destinationFile.normalizedGeneratedJava() != generatedFile.normalizedGeneratedJava()
            ) {
                generatedFile.copyTo(destinationFile, overwrite = true)
                relativePath
            } else {
                null
            }
        }

        if (updated.isEmpty()) {
            logger.lifecycle("The committed OpenAPI 3 model sources are already up to date.")
        } else {
            logger.lifecycle("Updated OpenAPI 3 model sources: ${updated.sorted().joinToString()}")
        }
    }
}

val verifyGeneratedOpenApi3 by tasks.registering {
    group = "verification"
    description = "Checks that the committed OpenAPI 3 model structure matches types3.yaml."
    dependsOn(generateOpenApi3InTemporaryDirectory)

    doLast {
        val expectedRoot = openApi3PackageDirectory.asFile
        val actualRoot = verificationPackageDirectory.get().asFile
        val expectedFiles = openApi3GeneratedSources.files.associateBy { it.relativeTo(expectedRoot).invariantSeparatorsPath }
        val actualFiles = fileTree(actualRoot) {
            include("model3/**/*.java", "ovl3/**/*.java")
        }.files.associateBy { it.relativeTo(actualRoot).invariantSeparatorsPath }

        val missing = expectedFiles.keys - actualFiles.keys
        val unexpected = actualFiles.keys - expectedFiles.keys
        val changed = expectedFiles.keys.intersect(actualFiles.keys).filterNot { relativePath ->
            val expected = expectedFiles.getValue(relativePath)
            val actual = actualFiles.getValue(relativePath)
            expected.normalizedGeneratedJava() == actual.normalizedGeneratedJava() &&
                actual.externalImports().containsAll(expected.externalImports())
        }

        check(missing.isEmpty() && unexpected.isEmpty() && changed.isEmpty()) {
            buildString {
                appendLine("Generated OpenAPI 3 model source structure is out of date.")
                if (missing.isNotEmpty()) appendLine("Missing: ${missing.sorted().joinToString()}")
                if (unexpected.isNotEmpty()) appendLine("Unexpected: ${unexpected.sorted().joinToString()}")
                if (changed.isNotEmpty()) appendLine("Changed: ${changed.sorted().joinToString()}")
                append("Run ./gradlew generateOpenApi3 and commit the resulting changes.")
            }
        }
    }
}

tasks.check {
    dependsOn(verifyGeneratedOpenApi3)
}

// Publishing configuration (based on fabrikt)
publishing {
    repositories {
        maven {
            name = "ossrh-staging-api"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USER_TOKEN_USERNAME")
                password = System.getenv("OSSRH_USER_TOKEN_PASSWORD")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("KaiZen OpenAPI Parser")
                description.set("KaiZen OpenAPI Parser - Maintained by Fabrikt (originally by RepreZen)")
                url.set("https://github.com/fabrikt-io/kaizen-openapi-parser")
                inceptionYear.set("2017")
                
                licenses {
                    license {
                        name.set("Eclipse Public License - Version 1.0")
                        url.set("https://www.eclipse.org/legal/epl-v10.html")
                    }
                }
                
                developers {
                    developer {
                        id.set("cjbooms")
                        name.set("Conor Gallagher")
                        email.set("cjbooms@gmail.com")
                        organization.set("Fabrikt")
                        organizationUrl.set("https://github.com/fabrikt-io")
                    }
                    // Original authors from RepreZen
                    developer {
                        id.set("andylowry")
                        name.set("Andy Lowry")
                        organization.set("RepreZen")
                    }
                    developer {
                        id.set("ghillairet")
                        name.set("Guillaume Hillairet")
                        organization.set("RepreZen")
                    }
                    developer {
                        id.set("tfesenko")
                        name.set("Tatiana Fesenko")
                        organization.set("RepreZen")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/fabrikt-io/kaizen-openapi-parser.git")
                    developerConnection.set("scm:git:git@github.com:fabrikt-io/kaizen-openapi-parser.git")
                    url.set("https://github.com/fabrikt-io/kaizen-openapi-parser/tree/main")
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}
