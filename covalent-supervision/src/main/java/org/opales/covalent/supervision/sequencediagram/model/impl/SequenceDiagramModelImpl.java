/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramEvents;
import org.opales.covalent.supervision.sequencediagram.model.RecordedMessages;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModel;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModelReadServices;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModelServices;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * SequenceDiagram's Model: one entry per session key, each holding that
 * session's messages, scope, start and selected actors. The messages are read by the View and
 * the Interaction through {@link SequenceDiagramModelReadServices} while the recording adds to
 * them - see {@link RecordedMessages}. No logic beyond storing what it
 * is given and firing the matching notification.
 */
@Implementation
public class SequenceDiagramModelImpl
		implements SequenceDiagramModel, SequenceDiagramModelServices, SequenceDiagramModelReadServices {

	/** Creates the Model; the container injects the producer of its events. */
	public SequenceDiagramModelImpl() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private SequenceDiagramEvents produceSequenceDiagramEvents;

	/**
	 * A session's messages - calls and returns, in one list, the completions of the invocations
	 * without a return, in another - its scope and its start.
	 */
	private record Session(RecordedMessages messages, RecordedMessages completions, Set<Class<?>> observedComponentTypes,
			Instant start) {
	}

	private final Map<ViewKey, Session> sessions = new ConcurrentHashMap<>();

	@Override
	public void createSession(final ViewKey sessionKey, final Set<Class<?>> observedComponentTypes) {
		this.sessions.put(sessionKey, new Session(new RecordedMessages(), new RecordedMessages(),
				Set.copyOf(observedComponentTypes), Instant.now()));
		this.produceSequenceDiagramEvents.onSessionCreated(sessionKey, observedComponentTypes);
	}

	@Override
	public void appendMessage(final ViewKey sessionKey, final SequenceTraceMessage message) {
		Session session = this.sessions.get(sessionKey);
		if (session != null) {
			(message.getStyle() == SequenceTraceMessage.Style.COMPLETION ? session.completions() : session.messages())
					.add(message);
		}
		this.produceSequenceDiagramEvents.onMessageAppended(sessionKey, message);
	}

	@Override
	public void setSelectedActors(final ViewKey sessionKey, final Set<Class<?>> actorTypes) {
		this.produceSequenceDiagramEvents.onSelectedActorsChanged(sessionKey, actorTypes);
	}

	@Override
	public void closeSession(final ViewKey sessionKey) {
		this.sessions.remove(sessionKey);
		this.produceSequenceDiagramEvents.onSessionClosed(sessionKey);
	}

	@Override
	public void recordingLimitReached(final ViewKey sessionKey, final int limit) {
		this.produceSequenceDiagramEvents.onRecordingLimitReached(sessionKey, limit);
	}

	// --- SequenceDiagramModelReadServices ---

	@Override
	public List<SequenceTraceMessage> getMessages(final ViewKey sessionKey) {
		Session session = this.sessions.get(sessionKey);
		return session == null ? List.of() : session.messages().upToNow();
	}

	@Override
	public List<SequenceTraceMessage> getCompletions(final ViewKey sessionKey) {
		Session session = this.sessions.get(sessionKey);
		return session == null ? List.of() : session.completions().upToNow();
	}

	@Override
	public Set<Class<?>> getObservedComponentTypes(final ViewKey sessionKey) {
		Session session = this.sessions.get(sessionKey);
		return session == null ? Set.of() : session.observedComponentTypes();
	}

	@Override
	public Instant getStart(final ViewKey sessionKey) {
		Session session = this.sessions.get(sessionKey);
		return session == null ? null : session.start();
	}
}
