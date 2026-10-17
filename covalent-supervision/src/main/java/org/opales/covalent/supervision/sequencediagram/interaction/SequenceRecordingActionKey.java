/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * The one recording the tool runs at a time, whoever started it: registered by {@code
 * SequenceDiagramInteractionImpl}, whose Controller holds the recording state. Every
 * gesture that starts a recording goes through {@link #StartRecording}, and its controls follow
 * that action's availability; every panel offering the shared Record button reflects {@link
 * #RecordingState}, and anything can stop the recording through {@link #StopRecording}.
 */
public enum SequenceRecordingActionKey implements ActionKey {

	/**
	 * Starts a recording of the given components - {@code Set<Class<?>>} payload. Available only
	 * while none runs: one at a time.
	 */
	StartRecording,

	/** Stops the recording in progress, if any - no payload. */
	StopRecording,

	/**
	 * Value key only, never an execution: the recording's start time in epoch milliseconds,
	 * {@code 0} while none runs.
	 */
	RecordingState;
}
