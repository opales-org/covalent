/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.util;

/**
 * Three-argument analog of {@link java.util.function.BiConsumer}, for the
 * handful of internal callbacks that need a third argument.
 *
 * @param <T> the type of the first argument
 * @param <U> the type of the second argument
 * @param <V> the type of the third argument
 */
public interface TriConsumer<T, U, V> {

	/**
	 * Performs this operation on the given arguments.
	 *
	 * @param t the first argument
	 * @param u the second argument
	 * @param v the third argument
	 */
	void accept(T t, U u, V v);
}
