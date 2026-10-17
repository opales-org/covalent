/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.threads.Threads;
import org.opales.covalent.supervision.threads.controller.impl.ThreadsControllerImpl;
import org.opales.covalent.supervision.threads.interaction.impl.ThreadsInteractionImpl;
import org.opales.covalent.supervision.threads.model.impl.ThreadsModelImpl;
import org.opales.covalent.supervision.threads.view.impl.ThreadsViewImpl;

/** The Threads function's composite. */
@Implementation
public class ThreadsImpl implements Threads {

	/** Creates the composite; the container instantiates its four parts. */
	public ThreadsImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private ThreadsModelImpl model;

	@Implementation
	private ThreadsControllerImpl controller;

	@Implementation
	private ThreadsViewImpl view;

	@Implementation
	private ThreadsInteractionImpl interaction;
}
