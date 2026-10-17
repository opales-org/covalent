/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests;

import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class CovalentTests {

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
