/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model;

import java.util.Set;

import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Produced by {@code SequenceDiagramModel} and consumed by
 * {@code SequenceDiagramView} and {@code SequenceDiagramInteraction}
 * (managed autowiring). {@code onSessionCreated} is also how the
 * Interaction learns of a new session to show.
 */
@Events
public interface SequenceDiagramEvents {

	/**
	 * Fired once, when a new recording session starts.
	 *
	 * @param sessionKey             the new session's identity
	 * @param observedComponentTypes the component types this session observes
	 */
	void onSessionCreated(ViewKey sessionKey, Set<Class<?>> observedComponentTypes);

	/**
	 * Fired every time a message is reconciled for a session.
	 *
	 * @param sessionKey the session this message belongs to
	 * @param message    the reconciled message
	 */
	void onMessageAppended(ViewKey sessionKey, SequenceTraceMessage message);

	/**
	 * Fired whenever a session's selected actor set changes.
	 *
	 * @param sessionKey the session whose selection changed
	 * @param actorTypes the new selection
	 */
	void onSelectedActorsChanged(ViewKey sessionKey, Set<Class<?>> actorTypes);

	/**
	 * Fired once, when a session's tab closes and its Model entry is
	 * discarded for good.
	 *
	 * @param sessionKey the closed session's identity
	 */
	void onSessionClosed(ViewKey sessionKey);

	/**
	 * Fired once, when a session's recording was stopped because it reached the most messages
	 * one recording keeps. The messages recorded up to then stay in the session.
	 *
	 * @param sessionKey the session whose recording was stopped
	 * @param limit      the most messages one recording keeps
	 */
	void onRecordingLimitReached(ViewKey sessionKey, int limit);
}
