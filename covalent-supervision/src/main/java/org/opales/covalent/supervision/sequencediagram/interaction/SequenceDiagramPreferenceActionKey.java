/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Singleton action identifiers for SequenceDiagram's preference-level
 * settings - unlike {@link SequenceDiagramActionKey}, these are not
 * per-session: they have to exist before any recording session does, so they
 * cannot carry a {@code ViewKey}. Registered once by {@code
 * SequenceDiagramInteractionImpl} at its own {@code @Activation}, each
 * routed straight to the matching {@code SequenceDiagramViewServices} method
 * ({@code setDefaultAnimationActive(boolean)} and {@code setTimelineStyle}) - same
 * single-instance-preference shape as {@code
 * ComponentDiagramPreferencesActionKey#ToggleAutoLayoutOnHide}. Triggered from {@code
 * SupervisionHmiViewImpl}'s settings panel ("New sequence diagrams start with
 * live drawing on" - whether a new recording session starts animated or starts
 * passive, requiring the user to activate it manually).
 */
public enum SequenceDiagramPreferenceActionKey implements ActionKey {

	/** Whether a newly created recording session starts animated by default - {@code Boolean} payload. */
	ToggleDynamicObservationDefault,

	/** How every timeline shows volume and duration - {@code TimelineStyle} payload. */
	SetTimelineStyle;
}
