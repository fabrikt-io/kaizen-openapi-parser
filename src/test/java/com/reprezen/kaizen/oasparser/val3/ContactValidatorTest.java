package com.reprezen.kaizen.oasparser.val3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URI;

import org.junit.Test;

import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;

public class ContactValidatorTest {

	@Test
	public void shouldAcceptValidEmail() throws Exception {
		OpenApi3 model = parseContact("user@example.org");

		assertTrue(model.isValid());
		assertTrue(model.getValidationItems().isEmpty());
	}

	@Test
	public void shouldRejectInvalidEmail() throws Exception {
		OpenApi3 model = parseContact("invalid address");

		assertFalse(model.isValid());
		assertEquals(1, model.getValidationItems().size());
		assertTrue(model.getValidationItems().iterator().next().getMsg().contains("invalid address"));
	}

	private OpenApi3 parseContact(String email) throws MalformedURLException {
		String spec = "openapi: 3.0.0\ninfo:\n  title: Contact validation\n  version: '1.0'\n"
				+ "  contact:\n    email: '" + email + "'\npaths: {}\n";
		return new OpenApi3Parser().parse(spec, URI.create("file:/contact-validation.yaml").toURL(), true);
	}
}
