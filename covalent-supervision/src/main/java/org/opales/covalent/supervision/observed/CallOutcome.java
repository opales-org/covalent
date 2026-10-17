/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

/**
 * How an observed call ended, as the supervisor shows it: it returned nothing, returned
 * {@code null}, returned a value - described as text where the call happened - or failed.
 *
 * @param kind which of these it is
 * @param description the value's or the failure's description, {@code null} for the other kinds
 */
public record CallOutcome(Kind kind, String description) {

	/** The ways a call can end. */
	public enum Kind {
		/** The method returns nothing. */
		VOID,
		/** The method returned {@code null}. */
		NULL,
		/** The method returned a value. */
		VALUE,
		/** The method threw. */
		FAILED
	}

	/** A call to a method that returns nothing. */
	public static final CallOutcome VOID = new CallOutcome(Kind.VOID, null);

	/** A call that returned {@code null}. */
	public static final CallOutcome NULL = new CallOutcome(Kind.NULL, null);

	/**
	 * Creates the outcome of a call that returned a value.
	 *
	 * @param description the returned value, described
	 * @return a call that returned that value
	 */
	public static CallOutcome value(final String description) {
		return new CallOutcome(Kind.VALUE, description);
	}

	/**
	 * Creates the outcome of a call that threw.
	 *
	 * @param description the failure, described
	 * @return a call that failed so
	 */
	public static CallOutcome failed(final String description) {
		return new CallOutcome(Kind.FAILED, description);
	}

	/**
	 * Renders the outcome as the text the supervisor shows for the call.
	 *
	 * @return the outcome as one line of text: {@code void}, {@code null}, the value or the failure
	 */
	public String toText() {
		return switch (this.kind) {
			case VOID -> "void";
			case NULL -> "null";
			case VALUE, FAILED -> this.description;
		};
	}
}
