/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.controller;

import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Exactly one provider ({@code SequenceDiagramController}); used by the functions offering the
 * shared Record button: {@code ComponentDiagram}, recording a tab's selection, and {@code
 * Threads}, recording what a thread worked for. {@code SequenceDiagramController} only ever
 * lets one recording be active at a time - see its own javadoc - and is the only one to know
 * whether one is: its users ask {@link #isRecording()} rather than keeping their own copy.
 *
 */
@Services
public interface SequenceDiagramServices {

	/**
	 * Starts a recording session observing the given component types.
	 *
	 * @param selectedTypes the component types to observe
	 */
	void startRecording(Set<Class<?>> selectedTypes);

	/** Stops the current recording session, if any. */
	void stopRecording();

	/**
	 * Tells whether a recording session is active.
	 *
	 * @return whether a recording session is active, whoever started it
	 */
	boolean isRecording();
}
