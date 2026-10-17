/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.async;

/**
 * Receives asynchronous task failures that no caller observes directly.
 *
 * <p>Only failures from {@link AsyncServices#fireAndForget} tasks are routed
 * here. A task submitted through {@code submit(...)} reports its failure on
 * the returned {@link java.util.concurrent.CompletionStage} instead, whether
 * or not the caller ever inspects it; that path never reaches this handler.
 * Registered per container via
 * {@link AsyncParameters#setUnhandledFailureHandler(AsyncFailureHandler)}.
 */
@FunctionalInterface
public interface AsyncFailureHandler {

	/**
	 * Called when a {@code fireAndForget} task fails. Implementations should not
	 * throw: an exception escaping this method is logged and the default logging
	 * behavior is used for that failure instead.
	 *
	 * @param message short description of the failure context
	 * @param failure the task's thrown exception
	 */
	void handleFailure(final String message, final Throwable failure);

}
