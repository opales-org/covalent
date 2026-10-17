/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.view;

import java.io.File;

import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Zoom accessors and animation/passivation control for one SequenceDiagram
 * session panel, provided by {@code SequenceDiagramView} and used by
 * {@code SequenceDiagramInteraction} to service that session's
 * {@code ZoomSetScale} and {@code ToggleAnimationActive} ActionKeys, plus
 * the preference-level {@code ToggleDynamicObservationDefault}. Opening
 * and closing a session's tab is not exposed here: {@code
 * SequenceDiagramView} reacts to {@code
 * SequenceDiagramEvents#onSessionCreated}/{@code #onSessionClosed}
 * directly, as it does to {@code #onMessageAppended}
 * and {@code #onSelectedActorsChanged}.
 * <p>
 * Passivation here is entirely user-driven - there is no automatic
 * tie to tab visibility (a hidden recording session keeps rendering
 * live activity, so a user switching tabs to do something else never
 * silently loses messages). Only the user's own explicit choice (this
 * session's control-panel toggle) and the preference-driven default for
 * a session at creation go through this Services interface.
 * <p>
 * All methods may be called from any thread: each one hands its work over to the JavaFX application thread.
 */
@Services
public interface SequenceDiagramViewServices {

	/**
	 * Sets the zoom scale of one session's diagram.
	 *
	 * @param sessionKey the session to set the zoom scale of
	 * @param scale      the new zoom scale
	 */
	void setZoomScale(ViewKey sessionKey, double scale);

	/**
	 * Sets whether a session starts animated (actively rendering live call activity) or passive (requiring the
	 * user to activate it manually) - the "live drawing" preference. Does not affect any session already open.
	 *
	 * @param active the default applied to every session when it is created
	 */
	void setDefaultAnimationActive(boolean active);

	/**
	 * Sets how the timelines show volume and duration - the "timeline style"
	 * preference. Applies to every session, already open or not.
	 *
	 * @param style the style to draw every timeline with
	 */
	void setTimelineStyle(TimelineStyle style);

	/**
	 * Sets one session's own explicit animation preference, from its
	 * control panel's toggle - the sole control of whether this session
	 * renders live activity, regardless of which tab is currently
	 * selected.
	 *
	 * @param sessionKey the session to set the preference of
	 * @param active     {@code true} to animate, {@code false} to stay passive
	 */
	void setAnimationActive(ViewKey sessionKey, boolean active);

	/**
	 * Tells which file one session's live log writes to, shown by its logs folder button.
	 *
	 * @param sessionKey the session
	 * @param file       the file messages are written to, {@code null} once none is
	 */
	void setLiveLog(ViewKey sessionKey, File file);

	/**
	 * Fits the diagram's width to its actors - at most the first six - and brings its first
	 * messages into view.
	 *
	 * @param sessionKey the session to recenter
	 */
	void recenter(ViewKey sessionKey);

	/**
	 * Saves an image of one session's diagram, the actors' band and the messages in view, as a timestamped PNG in
	 * the snapshot folder and places it on the clipboard. Does nothing for an unknown session.
	 *
	 * @param sessionKey the session to export
	 */
	void exportSnapshot(ViewKey sessionKey);
}
