plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "io.fabrikt"
version = "4.0.5-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

val jacksonVersion = "2.9.8"
val jsonOverlayVersion = "4.0.4"

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

    // Test dependencies
    testImplementation("junit:junit:4.12")
    testImplementation("com.google.guava:guava:19.0")
    testImplementation("org.skyscreamer:jsonassert:1.5.0")
    testImplementation("org.apache.commons:commons-lang3:3.7")
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
                        id.set("fabrikt-maintainers")
                        name.set("Fabrikt Maintainers")
                        organization.set("Fabrikt")
                        organizationUrl.set("https://github.com/fabrikt-io/fabrikt")
                    }
                    developer {
                        id.set("andylowry")
                        name.set("Andy Lowry")
                        email.set("andy.lowry@reprezen.com")
                        organization.set("RepreZen")
                        organizationUrl.set("https://www.reprezen.com")
                    }
                    developer {
                        id.set("ghillairet")
                        name.set("Guillaume Hillairet")
                        email.set("g.hillairet@gmail.com")
                        organization.set("RepreZen")
                        organizationUrl.set("https://www.reprezen.com")
                    }
                    developer {
                        id.set("tfesenko")
                        name.set("Tatiana Fesenko")
                        email.set("tatiana.fesenko@reprezen.com")
                        organization.set("RepreZen")
                        organizationUrl.set("https://www.reprezen.com")
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
