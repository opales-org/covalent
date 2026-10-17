/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies how a text is looked up and filled: each placeholder takes its own argument, and what an
 * argument contains is never read as a placeholder.
 */
public class SupervisorMessagesTests {

	private static final ResourceBundle BUNDLE = new ListResourceBundle() {
		@Override
		protected Object[][] getContents() {
			return new Object[][] { { "two", "{0} then {1}" }, { "apostrophe", "it's {0}" },
					{ "reversed", "{1} before {0}" },
					{ "braces", "{ {0} } {x} {" } };
		}
	};

	@Test
	public void eachPlaceholderTakesItsArgument() {
		Assert.assertEquals("a then b", SupervisorMessages.text(BUNDLE, "two", "a", "b"));
		Assert.assertEquals("b before a", SupervisorMessages.text(BUNDLE, "reversed", "a", "b"));
	}

	@Test
	public void anArgumentContainingAPlaceholderIsKeptAsIs() {
		Assert.assertEquals("{1} then b", SupervisorMessages.text(BUNDLE, "two", "{1}", "b"));
		Assert.assertEquals("{0} then {0}", SupervisorMessages.text(BUNDLE, "two", "{0}", "{0}"));
	}

	@Test
	public void anApostropheNeedsNoEscaping() {
		Assert.assertEquals("it's fine", SupervisorMessages.text(BUNDLE, "apostrophe", "fine"));
	}

	@Test
	public void aMissingKeyShowsAsItself() {
		Assert.assertEquals("absent", SupervisorMessages.text(BUNDLE, "absent", "x"));
	}

	@Test
	public void aPlaceholderWithoutArgumentIsLeftAsIs() {
		Assert.assertEquals("a then {1}", SupervisorMessages.text(BUNDLE, "two", "a"));
	}

	@Test
	public void aBraceThatIsNoPlaceholderIsKeptAndDoesNotHideTheNext() {
		Assert.assertEquals("{ A } {x} {", SupervisorMessages.text(BUNDLE, "braces", "A"));
	}
}
