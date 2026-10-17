/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.view;

/**
 * How a recording session's timeline shows, per time slice, how many messages
 * happened and how long the slowest invocation took.
 */
public enum TimelineStyle {

	/** One strip showing either the volume or the duration, with a switch between the two. */
	SWITCH,

	/** The volume as bars, the duration as a line drawn over them. */
	OVERLAY,

	/** The duration as bars, the volume as a density band under them. */
	DENSITY;

	/** @return its label, in the supervisor's language */
	@Override
	public String toString() {
		return SequenceDiagramViewTexts.get("timelineStyle." + this.name());
	}
}
