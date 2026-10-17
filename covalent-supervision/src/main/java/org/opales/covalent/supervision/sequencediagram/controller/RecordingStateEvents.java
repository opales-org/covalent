/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.controller;

import org.opales.covalent.container.component.annotations.Events;

/**
 * State-change notifications flow the other way from {@link SequenceDiagramServices}, so the
 * functions offering the shared Record button can follow a recording whoever starts or stops
 * it. They are notices only: their consumers read the state itself from its owner, so that
 * notices arriving late or out of order cannot leave them with a wrong one.
 */
@Events
public interface RecordingStateEvents {

	/** Fired when a recording session starts. */
	void onRecordingStarted();

	/** Fired when the current recording session stops. */
	void onRecordingStopped();
}
