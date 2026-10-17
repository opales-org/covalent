/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramServices;
import org.opales.covalent.supervision.testsupport.observed.StubSequenceDiagramComponent;

@Implementation
public class StubSequenceDiagramComponentImpl implements StubSequenceDiagramComponent, SequenceDiagramServices {

	public volatile boolean recording;

	public final Set<Class<?>> lastSelectedTypes = new LinkedHashSet<>();

	public int startRecordingCalls;

	public int stopRecordingCalls;

	@Override
	public void startRecording(final Set<Class<?>> selectedTypes) {
		this.recording = true;
		this.startRecordingCalls++;
		this.lastSelectedTypes.clear();
		this.lastSelectedTypes.addAll(selectedTypes);
	}

	@Override
	public void stopRecording() {
		this.recording = false;
		this.stopRecordingCalls++;
	}

	@Override
	public boolean isRecording() {
		return this.recording;
	}
}
