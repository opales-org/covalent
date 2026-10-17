/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.util;

/**
 * Argument- and state-checking helpers used throughout the container in
 * place of hand-written {@code if}/{@code throw} guards, so failures share
 * a consistent {@link IllegalArgumentException} message format.
 */
public class RuntimeCheck {

	/** Not meant to be instantiated; every member is static. */
	private RuntimeCheck() {
	}

	/** Default tolerance used by {@link #equals(float, float)} for float comparisons. */
	public static final float EPSILON_FLOAT = 1e-6f;

	/** Default tolerance used by {@link #equals(double, double)} for double comparisons. */
	public static final double EPSILON_DOUBLE = 1e-12d;

	/**
	 * Checks that the given value is not {@code null}.
	 *
	 * @param <T>       the value's type
	 * @param value     the value to check
	 * @param valueName the value's name, used in the exception message
	 * @throws IllegalArgumentException if {@code value} is {@code null}
	 */
	public static <T> void notNull(final T value, final Object valueName) throws IllegalArgumentException {
		if (value == null)
			throw new IllegalArgumentException(valueName + " must not be null.");
	}

	/**
	 * Checks that the given value is {@code null}.
	 *
	 * @param <T>       the value's type
	 * @param value     the value to check
	 * @param valueName the value's name, used in the exception message
	 * @throws IllegalArgumentException if {@code value} is not {@code null}
	 */
	public static <T> void isNull(final T value, final Object valueName) throws IllegalArgumentException {
		if (value != null)
			throw new IllegalArgumentException(valueName + " must be null.");
	}

	/**
	 * Checks that the given value is {@code true}.
	 *
	 * @param value     the value to check
	 * @param valueName the value's name, used in the exception message
	 * @throws IllegalArgumentException if {@code value} is {@code false}
	 */
	public static void isTrue(final boolean value, final Object valueName) {
		if (value == false)
			throw new IllegalArgumentException(valueName + " must be true.");
	}

	/**
	 * Checks that the given value is {@code false}.
	 *
	 * @param value     the value to check
	 * @param valueName the value's name, used in the exception message
	 * @throws IllegalArgumentException if {@code value} is {@code true}
	 */
	public static void isFalse(final boolean value, final Object valueName) {
		if (value == true)
			throw new IllegalArgumentException(valueName + " must be false.");
	}

	/**
	 * Checks that two {@code float} values are equal within
	 * {@link #EPSILON_FLOAT}.
	 *
	 * @param expected the expected value
	 * @param actual   the actual value
	 * @throws IllegalArgumentException if the values differ by more than
	 *                                   {@link #EPSILON_FLOAT}
	 */
	public static void equals(final float expected, final float actual) {
		RuntimeCheck.equals(expected, actual, EPSILON_FLOAT);
	}

	/**
	 * Checks that two {@code double} values are equal within
	 * {@link #EPSILON_DOUBLE}.
	 *
	 * @param expected the expected value
	 * @param actual   the actual value
	 * @throws IllegalArgumentException if the values differ by more than
	 *                                   {@link #EPSILON_DOUBLE}
	 */
	public static void equals(final double expected, final double actual) {
		RuntimeCheck.equals(expected, actual, EPSILON_DOUBLE);
	}

	/**
	 * Checks that two {@code float} values are equal within the given
	 * tolerance.
	 *
	 * @param expected the expected value
	 * @param actual   the actual value
	 * @param epsilon  the maximum tolerated difference
	 * @throws IllegalArgumentException if the values differ by more than
	 *                                   {@code epsilon}
	 */
	public static void equals(final float expected, final float actual, final float epsilon) {
		if (Double.compare(expected, actual) == 0) {
			return;
		}
		if (!(Math.abs(expected - actual) <= epsilon))
			throw new IllegalArgumentException("The expected float = " + expected + " is not equal to the actual = "
				+ actual + " with epsilon = " + epsilon);
	}

	/**
	 * Checks that two {@code double} values are equal within the given
	 * tolerance.
	 *
	 * @param expected the expected value
	 * @param actual   the actual value
	 * @param epsilon  the maximum tolerated difference
	 * @throws IllegalArgumentException if the values differ by more than
	 *                                   {@code epsilon}
	 */
	public static void equals(final double expected, final double actual, final double epsilon) {
		if (Double.compare(expected, actual) == 0) {
			return;
		}
		if (!(Math.abs(expected - actual) <= epsilon))
			throw new IllegalArgumentException("The expected double = " + expected + " is not equal to the actual = "
				+ actual + " with epsilon = " + epsilon);
	}

}
