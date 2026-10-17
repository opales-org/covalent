/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * What a recording session holds, provided by {@code SequenceDiagramModel} and read by the View,
 * which draws the messages, and by the Interaction, which writes the logs. Safe to call from any
 * thread while the session records.
 */
@Services
public interface SequenceDiagramModelReadServices {

	/**
	 * Returns a session's calls and returns, the messages the diagram draws.
	 *
	 * @param sessionKey the session
	 * @return the messages recorded so far, in order, read-only; none when the session is unknown
	 */
	List<SequenceTraceMessage> getMessages(ViewKey sessionKey);

	/**
	 * Returns how a session's invocations without a return message ended: never drawn, each
	 * completes what its call shows.
	 *
	 * @param sessionKey the session
	 * @return the completions recorded so far, in order, read-only; none when the session is unknown
	 */
	List<SequenceTraceMessage> getCompletions(ViewKey sessionKey);

	/**
	 * Returns the component types a session records.
	 *
	 * @param sessionKey the session
	 * @return its scope; none when the session is unknown
	 */
	Set<Class<?>> getObservedComponentTypes(ViewKey sessionKey);

	/**
	 * Returns when a session started.
	 *
	 * @param sessionKey the session
	 * @return when it was created, {@code null} when the session is unknown
	 */
	Instant getStart(ViewKey sessionKey);
}
