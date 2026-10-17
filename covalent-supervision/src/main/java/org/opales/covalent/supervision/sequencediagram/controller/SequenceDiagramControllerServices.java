/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.controller;

import java.util.Set;

import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Actor-selection and session-lifecycle commands, provided by
 * {@code SequenceDiagramController} and used by
 * {@code SequenceDiagramInteraction}. Distinct from the external
 * {@code SequenceDiagramServices}.
 * <p>
 * {@link #closeSession(ViewKey)} lets the Interaction's tab-close gesture reach
 * {@code SequenceDiagramModelServices.closeSession(...)}, which only the Controller may call.
 */
@Services
public interface SequenceDiagramControllerServices {

	/**
	 * Replaces a session's selection wholesale.
	 *
	 * @param sessionKey the session to select actors in
	 * @param actorTypes the new selection
	 */
	void selectActors(ViewKey sessionKey, Set<Class<?>> actorTypes);

	/**
	 * Adds or removes a single actor type from a session's selection.
	 *
	 * @param sessionKey the session to toggle a selection in
	 * @param actorType  the actor type to toggle
	 */
	void toggleActorSelection(ViewKey sessionKey, Class<?> actorType);

	/**
	 * Starts a recording session observing the given component types, unless one runs.
	 *
	 * @param selectedTypes the component types to observe
	 */
	void startRecording(Set<Class<?>> selectedTypes);

	/** Stops whichever session is currently recording, if any. */
	void stopRecording();

	/**
	 * Returns when the current recording started.
	 *
	 * @return when the current recording started, in epoch milliseconds, {@code 0} while none runs
	 */
	long recordingStartMillis();

	/**
	 * Discards a session's Model entry for good - called once, when its tab
	 * closes.
	 *
	 * @param sessionKey the session to discard
	 */
	void closeSession(ViewKey sessionKey);
}
