/*******************************************************************************
 *  Copyright (c) 2017 ModelSolv, Inc. and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *     ModelSolv, Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.reprezen.swaggerparser.test;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.reprezen.jsonoverlay.JsonLoader;
import com.reprezen.jsonoverlay.Overlay;
import com.reprezen.jsonoverlay.SerializationOptions.Option;
import com.reprezen.kaizen.oasparser.OpenApiParser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.model3.Schema;
import com.reprezen.kaizen.oasparser.ovl3.OpenApi3Impl;

@RunWith(Enclosed.class)
public class SimpleSerializationTest extends Assert {

	private static final String SPEC_REPO = "OAI/OpenAPI-Specification";
	private static final String EXAMPLES_BRANCH = "main";
	private static final String EXAMPLES_ROOT = "_archive_/schemas/v3.0/pass";

	private static ObjectMapper mapper = new ObjectMapper();
	private static ObjectMapper yamlMapper = new YAMLMapper();

	@RunWith(Parameterized.class)
	public static class ParameterizedTests extends Assert {
		@Parameters(name = "{index}: {1}")
		public static Collection<Object[]> findExamples() throws IOException {
			Collection<Object[]> examples = Lists.newArrayList();
			
			// Use local test resources instead of downloading from GitHub
			String[] exampleFiles = {
				"api-with-examples.yaml",
				"callback-example.yaml",
				"link-example.yaml",
				"petstore-expanded.yaml",
				"petstore.yaml",
				"uspto.yaml"
			};
			
			for (String fileName : exampleFiles) {
				URL fileUrl = SimpleSerializationTest.class.getResource("/openapi-examples/" + fileName);
				if (fileUrl != null) {
					examples.add(new Object[] { fileUrl, fileName });
				}
			}
			
			return examples;
		}

		@Parameter
		public URL exampleUrl;

		@Parameter(1)
		public String fileName;

		@Test
		public void serializeExample() throws Exception {
			if (!exampleUrl.toString().contains("callback-example")) {
				OpenApi3 model = (OpenApi3) new OpenApiParser().parse(exampleUrl);
				JsonNode serialized = Overlay.toJson((OpenApi3Impl) model);
				JsonNode expected = yamlMapper.readTree(exampleUrl);
				JSONAssert.assertEquals(mapper.writeValueAsString(expected), mapper.writeValueAsString(serialized),
						JSONCompareMode.STRICT);
			}
		}
	}

	public static class NonParameterizedTests {

		@Test
		public void toJsonNoticesChanges() throws Exception {
			OpenApi3 model = parseLocalModel("simpleTest");
			assertEquals("simple model", model.getInfo().getTitle());
			assertEquals("simple model", Overlay.of(model).toJson().at("/info/title").asText());
			// this changes the overlay value but does not refresh cached JSON -
			// just marks
			// it as out-of-date
			model.getInfo().setTitle("changed title");
			assertEquals("changed title", model.getInfo().getTitle());
			assertEquals("changed title", Overlay.of(model).toJson().at("/info/title").asText());
		}

		@Test
		public void toJsonFollowsRefs() throws Exception {
			OpenApi3 model = parseLocalModel("simpleTest");
			Schema xSchema = model.getSchema("X");
			assertEquals("#/components/schemas/Y", Overlay.of(xSchema).toJson().at("/properties/y/$ref").asText());
			assertEquals("integer", Overlay.of(xSchema).toJson(Option.FOLLOW_REFS).at("/properties/y/type").asText());
		}
	}

	private static OpenApi3 parseLocalModel(String name) throws Exception {
		URL url = SimpleSerializationTest.class.getResource("/models/" + name + ".yaml");
		return (OpenApi3) new OpenApiParser().parse(url);
	}

	private static <T> Iterable<T> iterable(final Iterator<T> iterator) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return iterator;
			}
		};
	}
}
