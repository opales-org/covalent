/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.supervision.sequencediagram.SequenceLog;
import org.opales.covalent.supervision.sequencediagram.controller.RecordingStateEvents;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramControllerServices;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramActionKey;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramActionKey.Role;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramInteraction;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramPreferenceActionKey;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceRecordingActionKey;
import org.opales.covalent.supervision.sequencediagram.view.TimelineStyle;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramEvents;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModelReadServices;
import org.opales.covalent.supervision.sequencediagram.view.SequenceDiagramViewServices;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * SequenceDiagram's Interaction Controller: it mints a fresh {@link
 * SequenceDiagramActionKey} set for each session as it is created ({@link
 * #onSessionCreated}), and retires that set for good when the session
 * closes ({@link #onSessionClosed}); none is registered at its own
 * {@code @Activation}. See {@code SequenceDiagramActionKey}'s
 * javadoc for why the View needs no instance of these keys handed to it.
 * {@code @Activation} does register the singleton preference actions,
 * {@link SequenceDiagramPreferenceActionKey#ToggleDynamicObservationDefault} and
 * {@link SequenceDiagramPreferenceActionKey#SetTimelineStyle},
 * which exist before any session does and so cannot be session-scoped.
 * It also registers the tool-wide {@link SequenceRecordingActionKey}s: stopping the recording,
 * and its state, read back from the Controller whenever {@link RecordingStateEvents} say it
 * changed.
 * <p>
 * It writes each session's log from what the Model holds: on demand, in the format the file's
 * extension tells, and live, as messages are notified, to a file of the logs folder while the
 * session's Live log toggle is on - from the session's start when the preferences ask for it. A live log ends when the recording stops, when it is turned
 * off or when its session closes.
 */
@Implementation
public class SequenceDiagramInteractionImpl
		implements SequenceDiagramInteraction, SequenceDiagramEvents, RecordingStateEvents {

	/** Creates the Interaction Controller; the container injects the services it uses. */
	public SequenceDiagramInteractionImpl() {
		// Nothing to initialize.
	}

	private static final Logger log = Logger.getLogger(SequenceDiagramInteractionImpl.class.getName());

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private SequenceDiagramViewServices useSequenceDiagramViewServices;

	@UsedServices
	private SequenceDiagramControllerServices useSequenceDiagramControllerServices;

	@UsedServices
	private SequenceDiagramModelReadServices useSequenceDiagramModelReadServices;

	/** Tracked only so {@code @Passivation} can defensively retire whatever is still open. */
	private final Set<ViewKey> openSessions = new HashSet<>();

	/**
	 * A session's live log, while one is written, and what deciding on it needs: how many of the
	 * session's messages were notified so far, and whether the session still records. Guarded by
	 * itself.
	 */
	private static final class SessionLog {
		private int notified;
		private boolean stopped;
		private LiveSequenceLog live;
	}

	private final Map<ViewKey, SessionLog> sessionLogs = new ConcurrentHashMap<>();

	/** The session that records, at most one at a time: the one created last, until the recording stops. */
	private volatile ViewKey recordingSession;

	/**
	 * Registers the tool-wide actions, those of the preferences and of the recording, and applies the stored
	 * preferences to the View.
	 */
	@Activation
	public void registerPreferenceAction() {
		this.actionManagerServices.registerActionExecution(
				SequenceDiagramPreferenceActionKey.ToggleDynamicObservationDefault,
				this.useSequenceDiagramViewServices::setDefaultAnimationActive, Boolean.class);
		this.actionManagerServices.registerActionExecution(SequenceDiagramPreferenceActionKey.SetTimelineStyle,
				this.useSequenceDiagramViewServices::setTimelineStyle, TimelineStyle.class);
		this.useSequenceDiagramViewServices.setDefaultAnimationActive(
				SupervisorSettings.getBoolean(SupervisorSettings.LIVE_DRAWING_DEFAULT, true));
		this.useSequenceDiagramViewServices.setTimelineStyle(
				SupervisorSettings.getEnum(SupervisorSettings.TIMELINE_STYLE, TimelineStyle.SWITCH));
		this.actionManagerServices.registerActionExecution(SequenceRecordingActionKey.StartRecording,
				this::onStartRecording, Set.class);
		this.actionManagerServices.registerActionExecution(SequenceRecordingActionKey.StopRecording,
				this.useSequenceDiagramControllerServices::stopRecording);
		this.publishRecordingState();
	}

	// --- RecordingStateEvents: notices only, the state is read back from the Controller. ---

	@Override
	public void onRecordingStarted() {
		this.publishRecordingState();
	}

	@Override
	public void onRecordingStopped() {
		this.publishRecordingState();
		ViewKey sessionKey = this.recordingSession;
		this.recordingSession = null;
		SessionLog sessionLog = sessionKey == null ? null : this.sessionLogs.get(sessionKey);
		if (sessionLog != null) {
			synchronized (sessionLog) {
				sessionLog.stopped = true;
				this.stopLiveLog(sessionKey, sessionLog, "stopped");
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void onStartRecording(final Set<?> componentTypes) {
		if (!componentTypes.isEmpty()) {
			this.useSequenceDiagramControllerServices.startRecording(Set.copyOf((Set<Class<?>>) componentTypes));
		}
	}

	/** The recording's start time, and whether one can start: only while none runs. */
	private void publishRecordingState() {
		long startMillis = this.useSequenceDiagramControllerServices.recordingStartMillis();
		this.actionManagerServices.setActionValue(SequenceRecordingActionKey.RecordingState, startMillis);
		this.actionManagerServices.setActionAvailable(SequenceRecordingActionKey.StartRecording, startMillis == 0);
	}

	/** Retires the actions of the sessions still open and the tool-wide ones. */
	@Passivation
	public void unregisterAllRemainingSessions() {
		for (ViewKey sessionKey : Set.copyOf(this.openSessions)) {
			this.unregisterSessionActions(sessionKey);
		}
		for (SessionLog sessionLog : this.sessionLogs.values()) {
			synchronized (sessionLog) {
				if (sessionLog.live != null) {
					sessionLog.live.finish("closed");
					sessionLog.live = null;
				}
			}
		}
		this.sessionLogs.clear();
		try {
			this.actionManagerServices
					.unregisterActionExecution(SequenceDiagramPreferenceActionKey.ToggleDynamicObservationDefault);
			this.actionManagerServices.unregisterActionExecution(SequenceDiagramPreferenceActionKey.SetTimelineStyle);
			this.actionManagerServices.unregisterActionExecution(SequenceRecordingActionKey.StartRecording);
			this.actionManagerServices.unregisterActionExecution(SequenceRecordingActionKey.StopRecording);
		} catch (IllegalStateException e) {
			// ActionManager already passivated as part of the same container-stop cascade.
		}
	}

	@Override
	public void onSessionCreated(final ViewKey sessionKey, final Set<Class<?>> observedComponentTypes) {
		this.openSessions.add(sessionKey);
		this.sessionLogs.put(sessionKey, new SessionLog());
		this.recordingSession = sessionKey;

		this.actionManagerServices.registerActionExecution(new SequenceDiagramActionKey(sessionKey, Role.ZoomSetScale),
				scale -> this.onZoomSetScale(sessionKey, scale), Double.class);
		this.actionManagerServices.registerActionExecution(new SequenceDiagramActionKey(sessionKey, Role.ZoomChanged),
				scale -> this.onZoomChanged(sessionKey, scale), Double.class);
		this.actionManagerServices.registerActionExecution(new SequenceDiagramActionKey(sessionKey, Role.SelectActors),
				actorTypes -> this.onSelectActors(sessionKey, actorTypes), Set.class);
		this.actionManagerServices.registerActionExecution(
				new SequenceDiagramActionKey(sessionKey, Role.ToggleActorSelection),
				actorType -> this.onToggleActorSelection(sessionKey, actorType), Class.class);
		this.actionManagerServices.registerActionExecution(new SequenceDiagramActionKey(sessionKey, Role.Close),
				() -> this.onClose(sessionKey));
		this.actionManagerServices.registerActionExecution(
				new SequenceDiagramActionKey(sessionKey, Role.ToggleAnimationActive),
				active -> this.onToggleAnimationActive(sessionKey, active), Boolean.class);
		this.actionManagerServices.registerActionExecution(new SequenceDiagramActionKey(sessionKey, Role.ExportTrace),
				file -> this.onExportTrace(sessionKey, file), File.class);
		this.actionManagerServices.registerActionExecution(new SequenceDiagramActionKey(sessionKey, Role.Recenter),
				() -> this.useSequenceDiagramViewServices.recenter(sessionKey));
		this.actionManagerServices.registerActionExecution(new SequenceDiagramActionKey(sessionKey, Role.Snapshot),
				() -> this.useSequenceDiagramViewServices.exportSnapshot(sessionKey));
		if (SupervisorSettings.getBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, false)) {
			this.startLiveLog(sessionKey);
		}
	}

	/** Saves every message the session recorded, read from the Model, in the format the file's extension tells. */
	private void onExportTrace(final ViewKey sessionKey, final File file) {
		Instant started = this.useSequenceDiagramModelReadServices.getStart(sessionKey);
		if (file == null || started == null) {
			return;
		}
		String text = SequenceLog.whole(SequenceLog.formatOf(file), this.scopeOf(sessionKey), started,
				this.useSequenceDiagramModelReadServices.getMessages(sessionKey),
				this.useSequenceDiagramModelReadServices.getCompletions(sessionKey), "saved", Instant.now());
		try {
			Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to save the sequence log to " + file, e);
		}
	}

	private String scopeOf(final ViewKey sessionKey) {
		return SequenceLog.scopeOf(this.useSequenceDiagramModelReadServices.getObservedComponentTypes(sessionKey));
	}

	/**
	 * Starts the session's live log, as the preferences ask for a new session; one is started only
	 * while the session records. Its file starts with the messages the Model already holds; the ones
	 * notified afterwards follow, each once.
	 */
	private void startLiveLog(final ViewKey sessionKey) {
		SessionLog sessionLog = this.sessionLogs.get(sessionKey);
		if (sessionLog == null) {
			return;
		}
		synchronized (sessionLog) {
			if (sessionLog.live == null && !sessionLog.stopped) {
				SequenceLog.Format format = SequenceLog.preferredFormat();
				String scope = this.scopeOf(sessionKey);
				File file = new File(SequenceLog.folder(), SequenceLog.fileName(scope, Instant.now(), format));
				LiveSequenceLog live = new LiveSequenceLog(file, format, failure -> this.onLiveLogFailed(sessionKey, file));
				sessionLog.live = live;
				Instant started = this.useSequenceDiagramModelReadServices.getStart(sessionKey);
				live.begin(scope, started == null ? Instant.now() : started);
				List<SequenceTraceMessage> recorded = this.useSequenceDiagramModelReadServices.getMessages(sessionKey);
				for (int index = 0; index < recorded.size(); index++) {
					live.write(index + 1, recorded.get(index));
				}
				for (SequenceTraceMessage completion : this.useSequenceDiagramModelReadServices.getCompletions(sessionKey)) {
					live.complete(completion);
				}
				this.useSequenceDiagramViewServices.setLiveLog(sessionKey, file);
			}
		}
	}

	/** Ends the session's live log, if one is written; called while holding the session's log. */
	private void stopLiveLog(final ViewKey sessionKey, final SessionLog sessionLog, final String reason) {
		LiveSequenceLog live = sessionLog.live;
		if (live == null) {
			return;
		}
		sessionLog.live = null;
		live.finish(reason);
		this.useSequenceDiagramViewServices.setLiveLog(sessionKey, null);
	}

	/** The live log's file cannot be written: the log stops. */
	private void onLiveLogFailed(final ViewKey sessionKey, final File file) {
		SessionLog sessionLog = this.sessionLogs.get(sessionKey);
		if (sessionLog == null) {
			return;
		}
		synchronized (sessionLog) {
			if (sessionLog.live != null && sessionLog.live.file().equals(file)) {
				sessionLog.live = null;
			}
		}
		this.useSequenceDiagramViewServices.setLiveLog(sessionKey, null);
	}

	@Override
	public void onSessionClosed(final ViewKey sessionKey) {
		this.unregisterSessionActions(sessionKey);
		SessionLog sessionLog = this.sessionLogs.remove(sessionKey);
		if (sessionLog != null) {
			synchronized (sessionLog) {
				if (sessionLog.live != null) {
					sessionLog.live.finish("closed");
					sessionLog.live = null;
				}
			}
		}
		if (sessionKey.equals(this.recordingSession)) {
			this.recordingSession = null;
		}
	}

	private void unregisterSessionActions(final ViewKey sessionKey) {
		this.openSessions.remove(sessionKey);
		for (Role role : Role.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(new SequenceDiagramActionKey(sessionKey, role));
			} catch (IllegalStateException e) {
				// ActionManager already passivated as part of the same container-stop cascade,
				// or ZoomCurrentScale was never a registered execution to begin with - both fine.
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void onSelectActors(final ViewKey sessionKey, final Set<?> actorTypes) {
		this.useSequenceDiagramControllerServices.selectActors(sessionKey, (Set<Class<?>>) actorTypes);
	}

	private void onToggleActorSelection(final ViewKey sessionKey, final Class<?> actorType) {
		this.useSequenceDiagramControllerServices.toggleActorSelection(sessionKey, actorType);
	}

	private void onToggleAnimationActive(final ViewKey sessionKey, final Boolean active) {
		this.useSequenceDiagramViewServices.setAnimationActive(sessionKey, active);
	}

	private void onZoomSetScale(final ViewKey sessionKey, final Double scale) {
		this.useSequenceDiagramViewServices.setZoomScale(sessionKey, scale);
	}

	private void onZoomChanged(final ViewKey sessionKey, final Double scale) {
		this.actionManagerServices.setActionValue(new SequenceDiagramActionKey(sessionKey, Role.ZoomCurrentScale), scale);
	}

	private void onClose(final ViewKey sessionKey) {
		this.useSequenceDiagramControllerServices.closeSession(sessionKey);
	}

	// --- SequenceDiagramEvents: the messages feed the session's log; their presentation and the
	// selection's are the View's concern. ---

	@Override
	public void onMessageAppended(final ViewKey sessionKey, final SequenceTraceMessage message) {
		SessionLog sessionLog = this.sessionLogs.get(sessionKey);
		if (sessionLog == null || message == null) {
			return;
		}
		synchronized (sessionLog) {
			if (message.getStyle() == SequenceTraceMessage.Style.COMPLETION) {
				// Not an entry of the log, as not a message of the diagram: only its failure counts.
				if (sessionLog.live != null) {
					sessionLog.live.complete(message);
				}
				return;
			}
			// The messages are notified in the order the Model holds them: this one is the next.
			sessionLog.notified++;
			// A live log started meanwhile may have already written it from the Model.
			if (sessionLog.live != null && sessionLog.notified > sessionLog.live.written()) {
				sessionLog.live.write(sessionLog.notified, message);
			}
		}
	}

	@Override
	public void onSelectedActorsChanged(final ViewKey sessionKey, final Set<Class<?>> actorTypes) {
		// Ignored: the selection is shown by the View and does not affect the log.
	}

	@Override
	public void onRecordingLimitReached(final ViewKey sessionKey, final int limit) {
		// Ignored: the View tells the user, and the recording stopping reaches the log as for any stop.
	}
}
