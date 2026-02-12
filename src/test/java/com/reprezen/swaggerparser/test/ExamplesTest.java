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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.reprezen.jsonoverlay.JsonLoader;
import com.reprezen.kaizen.oasparser.OpenApiParser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.val.ValidationResults.ValidationItem;

@RunWith(Parameterized.class)
public class ExamplesTest extends Assert {

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
			URL fileUrl = ExamplesTest.class.getResource("/openapi-examples/" + fileName);
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
	public void exampleCanBeParsed() throws Exception {
		if (!exampleUrl.toString().contains("callback-example")) {
			OpenApi3 model = (OpenApi3) new OpenApiParser().parse(exampleUrl);
			for (ValidationItem item : model.getValidationItems()) {
				System.out.println(item);
			}
			assertTrue("Example was not valid: " + exampleUrl, model.isValid());
		}
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
