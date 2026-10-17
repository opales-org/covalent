/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Turns the values an observed call carries - its arguments, its return value - into text
 * for the supervisor to display. Meant to run where the call happens, so that the text
 * shows the values as they were then and the supervisor keeps no reference to the observed
 * application's objects.
 * <p>
 * A value is described as {@code Type : text}. The text of an application object is its
 * {@code toString()}, guarded: one that fails is reported rather than propagated. Every
 * text is cut to {@link #MAX_VALUE_LENGTH} characters; arrays, collections and maps are
 * summed up by their type, their size and their first elements.
 */
public final class DataPrinter {

	/** The longest text kept for one value. */
	public static final int MAX_VALUE_LENGTH = 300;

	/** How many elements of an array, a collection or a map are shown. */
	private static final int MAX_ELEMENTS = 5;

	/** How many levels of arrays, collections and maps nested in one another are summed up. */
	private static final int MAX_DEPTH = 2;

	private static final String ELLIPSIS = "…";

	private DataPrinter() {
	}

	/**
	 * Describes a call's arguments, one per line.
	 *
	 * @param arguments the arguments, {@code null} or empty when there are none
	 * @return the description
	 */
	public static String describeArguments(final Object[] arguments) {
		if (arguments == null || arguments.length == 0) {
			return ObservedTexts.get("arguments.none");
		}
		StringBuilder text = new StringBuilder();
		for (Object argument : arguments) {
			if (text.length() > 0) {
				text.append('\n');
			}
			text.append(describe(argument));
		}
		return text.toString();
	}

	/**
	 * Describes how a call ended.
	 *
	 * @param method the method called
	 * @param returnValue the value it returned
	 * @param failure what it failed with, {@code null} when it did not fail
	 * @return the outcome
	 */
	public static CallOutcome describeOutcome(final Method method, final Object returnValue, final Throwable failure) {
		if (failure != null) {
			return CallOutcome.failed(cut(guarded(failure::toString)));
		}
		if (method != null && method.getReturnType() == void.class) {
			return CallOutcome.VOID;
		}
		return returnValue == null ? CallOutcome.NULL : CallOutcome.value(describe(returnValue));
	}

	/**
	 * Describes one value as {@code Type : text}.
	 *
	 * @param value the value
	 * @return its description
	 */
	public static String describe(final Object value) {
		if (value == null) {
			return "null";
		}
		return typeName(value) + " : " + cut(text(value, 1));
	}

	private static String typeName(final Object value) {
		Class<?> type = value.getClass();
		String name = type.getSimpleName();
		return name.isEmpty() ? type.getName() : name;
	}

	private static String text(final Object value, final int depth) {
		if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			StringBuilder text = new StringBuilder("(" + length + ") [");
			for (int index = 0; index < Math.min(length, MAX_ELEMENTS); index++) {
				text.append(index == 0 ? "" : ", ").append(elementText(Array.get(value, index), depth));
			}
			return text.append(length > MAX_ELEMENTS ? ", " + ELLIPSIS : "").append(']').toString();
		}
		if (value instanceof Collection<?> collection) {
			return guarded(() -> {
				StringBuilder text = new StringBuilder("(" + collection.size() + ") [");
				Iterator<?> elements = collection.iterator();
				for (int index = 0; index < MAX_ELEMENTS && elements.hasNext(); index++) {
					text.append(index == 0 ? "" : ", ").append(elementText(elements.next(), depth));
				}
				return text.append(elements.hasNext() ? ", " + ELLIPSIS : "").append(']').toString();
			});
		}
		if (value instanceof Map<?, ?> map) {
			return guarded(() -> {
				StringBuilder text = new StringBuilder("(" + map.size() + ") {");
				Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
				for (int index = 0; index < MAX_ELEMENTS && entries.hasNext(); index++) {
					Map.Entry<?, ?> entry = entries.next();
					text.append(index == 0 ? "" : ", ").append(elementText(entry.getKey(), depth)).append('=')
							.append(elementText(entry.getValue(), depth));
				}
				return text.append(entries.hasNext() ? ", " + ELLIPSIS : "").append('}').toString();
			});
		}
		return guarded(value::toString);
	}

	/**
	 * Describes an element of an array, a collection or a map: an array, a collection or a map nested in it is
	 * summed up the same way, down to {@link #MAX_DEPTH} levels.
	 */
	private static String elementText(final Object element, final int depth) {
		if (element == null) {
			return "null";
		}
		if (depth < MAX_DEPTH && (element.getClass().isArray() || element instanceof Collection<?>
				|| element instanceof Map<?, ?>)) {
			return cut(text(element, depth + 1), 40);
		}
		try {
			return cut(String.valueOf(element.toString()), 40);
		} catch (RuntimeException | StackOverflowError | LinkageError failure) {
			// Reported whole: if it were cut, it would not read as a failure.
			return failedText(failure);
		}
	}

	/** Runs an application object's {@code toString()}, reporting its failure instead of propagating it. */
	private static String guarded(final Supplier<String> text) {
		try {
			return String.valueOf(text.get());
		} catch (RuntimeException | StackOverflowError | LinkageError failure) {
			return failedText(failure);
		}
	}

	private static String failedText(final Throwable failure) {
		return ObservedTexts.get("toString.failed", failure.getClass().getSimpleName());
	}

	private static String cut(final String text) {
		return cut(text, MAX_VALUE_LENGTH);
	}

	private static String cut(final String text, final int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + ELLIPSIS;
	}
}
