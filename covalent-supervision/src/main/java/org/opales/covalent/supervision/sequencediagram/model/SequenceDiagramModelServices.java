/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model;

import java.util.Set;

import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records every recording session's messages and actor selection, provided
 * by {@code SequenceDiagramModel} and used by
 * {@code SequenceDiagramController}. No logic beyond storing what it is
 * given and firing the matching {@link SequenceDiagramEvents} notification.
 */
@Services
public interface SequenceDiagramModelServices {

	/**
	 * Creates a new session entry, and fires
	 * {@link SequenceDiagramEvents#onSessionCreated(ViewKey, Set)}.
	 *
	 * @param sessionKey             the new session's identity
	 * @param observedComponentTypes the component types this session observes
	 */
	void createSession(ViewKey sessionKey, Set<Class<?>> observedComponentTypes);

	/**
	 * Appends a message to a session, and fires
	 * {@link SequenceDiagramEvents#onMessageAppended(ViewKey, SequenceTraceMessage)}.
	 *
	 * @param sessionKey the session this message belongs to
	 * @param message    the reconciled message
	 */
	void appendMessage(ViewKey sessionKey, SequenceTraceMessage message);

	/**
	 * Replaces a session's selected actor set, and fires
	 * {@link SequenceDiagramEvents#onSelectedActorsChanged(ViewKey, Set)}.
	 *
	 * @param sessionKey the session whose selection to replace
	 * @param actorTypes the new selection
	 */
	void setSelectedActors(ViewKey sessionKey, Set<Class<?>> actorTypes);

	/**
	 * Discards a session's entry for good, and fires
	 * {@link SequenceDiagramEvents#onSessionClosed(ViewKey)}.
	 *
	 * @param sessionKey the session to discard
	 */
	void closeSession(ViewKey sessionKey);

	/**
	 * Tells that a session's recording was stopped for having reached the most messages one
	 * recording keeps, and fires {@link SequenceDiagramEvents#onRecordingLimitReached(ViewKey, int)}.
	 *
	 * @param sessionKey the session whose recording was stopped
	 * @param limit      the most messages one recording keeps
	 */
	void recordingLimitReached(ViewKey sessionKey, int limit);
}
