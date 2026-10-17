/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

import java.util.List;

/**
 * A thread's stack at one moment.
 *
 * @param threadId       the thread
 * @param frames         its Java frames, innermost first; empty when it ended
 * @param componentCalls the methods of the observed components among them, as
 *                       {@code Component.method()}, innermost first
 */
public record ThreadStack(long threadId, List<String> frames, List<String> componentCalls) {

	/** No stack read. */
	public static final ThreadStack NONE = new ThreadStack(-1, List.of(), List.of());

	/**
	 * Creates a stack, copying its two lists.
	 *
	 * @param threadId       the thread
	 * @param frames         its frames, copied
	 * @param componentCalls its component calls, copied
	 */
	public ThreadStack {
		frames = List.copyOf(frames);
		componentCalls = List.copyOf(componentCalls);
	}
}
