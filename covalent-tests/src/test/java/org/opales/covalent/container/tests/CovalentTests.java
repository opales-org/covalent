/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Rule;

/**
 * Base class of the tests that use the shared main container. The parameters of the main container
 * are restored after each test by {@link CovalentParametersGuard}, so a test may change them without
 * restoring them itself.
 */
public abstract class CovalentTests {

	@Rule
	public final CovalentParametersGuard parametersGuard = new CovalentParametersGuard();

	protected boolean isInvalidatedException(final RuntimeException e) {
		return this.isExceptionIncludingText(e, "is invalidated");
	}

	protected boolean isPassivatedException(final RuntimeException e) {
		return this.isExceptionIncludingText(e, "is passivated");
	}

	protected boolean isAlreadySubscribedException(final RuntimeException e) {
		return this.isExceptionIncludingText(e, "already subscribed");
	}

	protected boolean isSurrogateException(final RuntimeException e) {
		return this.isExceptionIncludingText(e, "Surrogate");
	}

	protected boolean isExceptionIncludingText(final RuntimeException e, final String text) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String stackTrace = sw.toString();
		boolean containsText = stackTrace.contains(text);
		return containsText;
	}

}
