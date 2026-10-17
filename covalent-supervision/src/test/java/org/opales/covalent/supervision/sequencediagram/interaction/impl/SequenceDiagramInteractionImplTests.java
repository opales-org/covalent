/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction.impl;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramControllerServices;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModelReadServices;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramActionKey;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramActionKey.Role;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramPreferenceActionKey;
import org.opales.covalent.supervision.sequencediagram.interaction.SequenceRecordingActionKey;
import org.opales.covalent.supervision.sequencediagram.view.SequenceDiagramViewServices;
import org.opales.covalent.supervision.sequencediagram.view.TimelineStyle;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;
import org.opales.covalent.supervision.testsupport.SequenceLogWords;

/**
 * {@link SequenceDiagramInteractionImpl} against hand-written fakes of the services it uses. It reads two of the
 * supervisor's preferences, so each test starts from an empty store and puts back what was there.
 */
public class SequenceDiagramInteractionImplTests {

	private static final Preferences STORE = Preferences.userRoot().node("org/opales/covalent/supervisor");

	private static final String LIVE_DRAWING = SupervisorSettings.LIVE_DRAWING_DEFAULT;

	private static final String TIMELINE = SupervisorSettings.TIMELINE_STYLE;

	private static final class TestViewKey implements ViewKey {
		private final String name;

		TestViewKey(final String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	/** The actions the interaction registers, runnable by the test, and what it says about them. */
	private static final class FakeActionProviderServices implements ActionProviderServices {
		final Map<ActionKey, Consumer<Object>> consumers = new LinkedHashMap<>();
		final Map<ActionKey, Runnable> runnables = new LinkedHashMap<>();
		final Set<ActionKey> registered = new HashSet<>();
		final List<ActionKey> unregistered = new ArrayList<>();
		final Map<ActionKey, Boolean> availability = new HashMap<>();
		final Map<ActionKey, Object> values = new HashMap<>();
		boolean failOnUnregister;

		@Override
		@SuppressWarnings("unchecked")
		public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
				final Class<T> anInputDataClass) {
			this.registered.add(anActionKey);
			this.consumers.put(anActionKey, (Consumer<Object>) anInteractionImpl);
		}

		@Override
		public <T1, T2> void registerActionExecution(final ActionKey anActionKey,
				final BiConsumer<T1, T2> anInteractionImpl, final Class<T1> anInputDataClass1,
				final Class<T2> anInputDataClass2) {
			this.registered.add(anActionKey);
		}

		@Override
		public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl) {
			this.registered.add(anActionKey);
			this.runnables.put(anActionKey, anInteractionImpl);
		}

		@Override
		public void unregisterActionExecution(final ActionKey anActionKey) {
			if (this.failOnUnregister) {
				throw new IllegalStateException("already passivated");
			}
			this.unregistered.add(anActionKey);
		}

		@Override
		public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
			this.availability.put(anActionKey, isAvailable);
		}

		@Override
		public void setActionValue(final ActionKey anActionKey, final Object aValue) {
			this.values.put(anActionKey, aValue);
		}

		void fire(final ActionKey key, final Object input) {
			this.consumers.get(key).accept(input);
		}

		void fire(final ActionKey key) {
			this.runnables.get(key).run();
		}
	}

	/** The Model's sessions, as the recording fills them. */
	private static final class FakeModelReadServices implements SequenceDiagramModelReadServices {
		final Map<ViewKey, List<SequenceTraceMessage>> messages = new HashMap<>();
		final Map<ViewKey, List<SequenceTraceMessage>> completions = new HashMap<>();
		final Map<ViewKey, Instant> starts = new HashMap<>();

		@Override
		public List<SequenceTraceMessage> getMessages(final ViewKey sessionKey) {
			return List.copyOf(this.messages.getOrDefault(sessionKey, List.of()));
		}

		@Override
		public List<SequenceTraceMessage> getCompletions(final ViewKey sessionKey) {
			return List.copyOf(this.completions.getOrDefault(sessionKey, List.of()));
		}

		@Override
		public Set<Class<?>> getObservedComponentTypes(final ViewKey sessionKey) {
			return Set.of(ArrayList.class);
		}

		@Override
		public Instant getStart(final ViewKey sessionKey) {
			return this.starts.get(sessionKey);
		}
	}

	private static final class FakeViewServices implements SequenceDiagramViewServices {
		final List<Boolean> defaultAnimationCalls = new ArrayList<>();
		final List<TimelineStyle> timelineStyleCalls = new ArrayList<>();
		final List<Object[]> zoomCalls = new ArrayList<>();
		final List<Object[]> animationCalls = new ArrayList<>();
		final List<ViewKey> recentered = new ArrayList<>();
		final List<ViewKey> exported = new ArrayList<>();
		final List<Object[]> liveLogs = new ArrayList<>();

		@Override
		public void setZoomScale(final ViewKey sessionKey, final double scale) {
			this.zoomCalls.add(new Object[] { sessionKey, scale });
		}

		@Override
		public void setDefaultAnimationActive(final boolean active) {
			this.defaultAnimationCalls.add(active);
		}

		@Override
		public void setTimelineStyle(final TimelineStyle style) {
			this.timelineStyleCalls.add(style);
		}

		@Override
		public void setAnimationActive(final ViewKey sessionKey, final boolean active) {
			this.animationCalls.add(new Object[] { sessionKey, active });
		}

		@Override
		public void setLiveLog(final ViewKey sessionKey, final File file) {
			this.liveLogs.add(new Object[] { sessionKey, file });
		}

		@Override
		public void recenter(final ViewKey sessionKey) {
			this.recentered.add(sessionKey);
		}

		@Override
		public void exportSnapshot(final ViewKey sessionKey) {
			this.exported.add(sessionKey);
		}
	}

	private static final class FakeControllerServices implements SequenceDiagramControllerServices {
		final List<Object[]> selectCalls = new ArrayList<>();
		final List<Object[]> toggleCalls = new ArrayList<>();
		final List<Set<Class<?>>> startCalls = new ArrayList<>();
		final List<ViewKey> closedSessions = new ArrayList<>();
		int stopCalls;
		long recordingStart;

		@Override
		public void selectActors(final ViewKey sessionKey, final Set<Class<?>> actorTypes) {
			this.selectCalls.add(new Object[] { sessionKey, actorTypes });
		}

		@Override
		public void toggleActorSelection(final ViewKey sessionKey, final Class<?> actorType) {
			this.toggleCalls.add(new Object[] { sessionKey, actorType });
		}

		@Override
		public void startRecording(final Set<Class<?>> selectedTypes) {
			this.startCalls.add(selectedTypes);
		}

		@Override
		public void stopRecording() {
			this.stopCalls++;
		}

		@Override
		public long recordingStartMillis() {
			return this.recordingStart;
		}

		@Override
		public void closeSession(final ViewKey sessionKey) {
			this.closedSessions.add(sessionKey);
		}
	}

	private CapturingLogHandler sequenceDiagramInteractionLogs;

	private CapturingLogHandler liveLogLogs;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static final List<String> LOG_SETTINGS = List.of(SupervisorSettings.SEQUENCE_LIVE_LOG,
			SupervisorSettings.SEQUENCE_LOG_FORMAT, SupervisorSettings.SEQUENCE_LOG_DIRECTORY);

	private String savedLiveDrawing;
	private String savedTimeline;
	private final Map<String, String> savedLogSettings = new HashMap<>();
	private File logsFolder;

	private FakeActionProviderServices actions;
	private FakeViewServices view;
	private FakeControllerServices controller;
	private FakeModelReadServices model;
	private SequenceDiagramInteractionImpl interaction;
	private final ViewKey session = new TestViewKey("session-1");
	private final ViewKey other = new TestViewKey("session-2");

	@Before
	public void setUp() throws Exception {
		this.sequenceDiagramInteractionLogs = CapturingLogHandler.install(SequenceDiagramInteractionImpl.class);
		this.liveLogLogs = CapturingLogHandler.install(LiveSequenceLog.class);
		this.savedLiveDrawing = STORE.get(LIVE_DRAWING, null);
		this.savedTimeline = STORE.get(TIMELINE, null);
		STORE.remove(LIVE_DRAWING);
		STORE.remove(TIMELINE);
		for (String key : LOG_SETTINGS) {
			this.savedLogSettings.put(key, STORE.get(key, null));
			STORE.remove(key);
		}
		this.logsFolder = this.folder.newFolder("logs");
		SupervisorSettings.putString(SupervisorSettings.SEQUENCE_LOG_DIRECTORY, this.logsFolder.getAbsolutePath());

		this.actions = new FakeActionProviderServices();
		this.view = new FakeViewServices();
		this.controller = new FakeControllerServices();
		this.interaction = new SequenceDiagramInteractionImpl();
		inject("actionManagerServices", this.actions);
		inject("useSequenceDiagramViewServices", this.view);
		inject("useSequenceDiagramControllerServices", this.controller);
		this.model = new FakeModelReadServices();
		inject("useSequenceDiagramModelReadServices", this.model);
	}

	@After
	public void tearDown() throws Exception {
		STORE.remove(LIVE_DRAWING);
		STORE.remove(TIMELINE);
		if (this.savedLiveDrawing != null) {
			STORE.put(LIVE_DRAWING, this.savedLiveDrawing);
		}
		if (this.savedTimeline != null) {
			STORE.put(TIMELINE, this.savedTimeline);
		}
		for (String key : LOG_SETTINGS) {
			STORE.remove(key);
			if (this.savedLogSettings.get(key) != null) {
				STORE.put(key, this.savedLogSettings.get(key));
			}
		}
		STORE.flush();
		this.sequenceDiagramInteractionLogs.restore();
		this.liveLogLogs.restore();
	}

	private void inject(final String fieldName, final Object value) throws Exception {
		Field field = SequenceDiagramInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(this.interaction, value);
	}

	private SequenceDiagramActionKey key(final ViewKey sessionKey, final Role role) {
		return new SequenceDiagramActionKey(sessionKey, role);
	}

	private void open(final ViewKey sessionKey) {
		this.model.starts.put(sessionKey, Instant.now());
		this.interaction.registerPreferenceAction();
		this.interaction.onSessionCreated(sessionKey, Set.of());
	}

	@Test
	public void activationRegistersTheToolWideActions() {
		this.interaction.registerPreferenceAction();

		Assert.assertEquals(
				Set.of(SequenceDiagramPreferenceActionKey.ToggleDynamicObservationDefault,
						SequenceDiagramPreferenceActionKey.SetTimelineStyle, SequenceRecordingActionKey.StartRecording,
						SequenceRecordingActionKey.StopRecording),
				this.actions.registered);
	}

	@Test
	public void activationAppliesTheDefaultPreferencesToTheView() {
		this.interaction.registerPreferenceAction();

		Assert.assertEquals(List.of(true), this.view.defaultAnimationCalls);
		Assert.assertEquals(List.of(TimelineStyle.SWITCH), this.view.timelineStyleCalls);
	}

	@Test
	public void activationAppliesTheStoredPreferencesToTheView() {
		SupervisorSettings.putBoolean(LIVE_DRAWING, false);
		SupervisorSettings.putEnum(TIMELINE, TimelineStyle.OVERLAY);

		this.interaction.registerPreferenceAction();

		Assert.assertEquals(List.of(false), this.view.defaultAnimationCalls);
		Assert.assertEquals(List.of(TimelineStyle.OVERLAY), this.view.timelineStyleCalls);
	}

	@Test
	public void thePreferenceActionsAreForwardedToTheView() {
		this.interaction.registerPreferenceAction();

		this.actions.fire(SequenceDiagramPreferenceActionKey.ToggleDynamicObservationDefault, Boolean.FALSE);
		this.actions.fire(SequenceDiagramPreferenceActionKey.SetTimelineStyle, TimelineStyle.OVERLAY);

		Assert.assertEquals(List.of(true, false), this.view.defaultAnimationCalls);
		Assert.assertEquals(List.of(TimelineStyle.SWITCH, TimelineStyle.OVERLAY), this.view.timelineStyleCalls);
	}

	@Test
	public void theRecordingStateIsPublishedAtActivationAndWhenItChanges() {
		this.interaction.registerPreferenceAction();
		Assert.assertEquals(0L, this.actions.values.get(SequenceRecordingActionKey.RecordingState));
		Assert.assertTrue(this.actions.availability.get(SequenceRecordingActionKey.StartRecording));

		this.controller.recordingStart = 1234L;
		this.interaction.onRecordingStarted();
		Assert.assertEquals(1234L, this.actions.values.get(SequenceRecordingActionKey.RecordingState));
		Assert.assertFalse("a recording cannot start while one runs",
				this.actions.availability.get(SequenceRecordingActionKey.StartRecording));

		this.controller.recordingStart = 0L;
		this.interaction.onRecordingStopped();
		Assert.assertEquals(0L, this.actions.values.get(SequenceRecordingActionKey.RecordingState));
		Assert.assertTrue(this.actions.availability.get(SequenceRecordingActionKey.StartRecording));
	}

	@Test
	public void startingARecordingHandsAnImmutableCopyOfTheComponentsToTheController() {
		this.interaction.registerPreferenceAction();
		Set<Class<?>> chosen = new HashSet<>(Set.of(ArrayList.class));

		this.actions.fire(SequenceRecordingActionKey.StartRecording, chosen);
		chosen.add(HashMap.class);

		Assert.assertEquals(List.of(Set.of(ArrayList.class)), this.controller.startCalls);
	}

	@Test
	public void startingARecordingOfNothingDoesNothing() {
		this.interaction.registerPreferenceAction();

		this.actions.fire(SequenceRecordingActionKey.StartRecording, Set.of());

		Assert.assertTrue(this.controller.startCalls.isEmpty());
	}

	@Test
	public void stoppingARecordingIsForwarded() {
		this.interaction.registerPreferenceAction();

		this.actions.fire(SequenceRecordingActionKey.StopRecording);

		Assert.assertEquals(1, this.controller.stopCalls);
	}

	@Test
	public void aNewSessionGetsItsOwnNineActions() {
		this.interaction.registerPreferenceAction();
		Set<ActionKey> before = new HashSet<>(this.actions.registered);

		this.interaction.onSessionCreated(this.session, Set.of());

		Set<ActionKey> added = new HashSet<>(this.actions.registered);
		added.removeAll(before);
		Set<ActionKey> expected = new HashSet<>();
		for (Role role : Role.values()) {
			if (role != Role.ZoomCurrentScale) {
				expected.add(this.key(this.session, role));
			}
		}
		Assert.assertEquals(expected, added);
		Assert.assertEquals(9, added.size());
	}

	@Test
	public void theSessionsCommandsAreForwardedWithTheSessionKey() {
		this.open(this.session);

		this.actions.fire(this.key(this.session, Role.ZoomSetScale), 1.5);
		this.actions.fire(this.key(this.session, Role.ToggleAnimationActive), Boolean.TRUE);
		this.actions.fire(this.key(this.session, Role.Recenter));
		this.actions.fire(this.key(this.session, Role.Snapshot));
		this.actions.fire(this.key(this.session, Role.Close));
		Set<Class<?>> actors = Set.of(ArrayList.class);
		this.actions.fire(this.key(this.session, Role.SelectActors), actors);
		this.actions.fire(this.key(this.session, Role.ToggleActorSelection), HashMap.class);

		Assert.assertArrayEquals(new Object[] { this.session, 1.5 }, this.view.zoomCalls.get(0));
		Assert.assertArrayEquals(new Object[] { this.session, true }, this.view.animationCalls.get(0));
		Assert.assertEquals(List.of(this.session), this.view.recentered);
		Assert.assertEquals(List.of(this.session), this.view.exported);
		Assert.assertEquals(List.of(this.session), this.controller.closedSessions);
		Assert.assertArrayEquals(new Object[] { this.session, actors }, this.controller.selectCalls.get(0));
		Assert.assertArrayEquals(new Object[] { this.session, HashMap.class }, this.controller.toggleCalls.get(0));
	}

	@Test
	public void theCurrentZoomScaleIsPublishedAsTheSessionsValue() {
		this.open(this.session);

		this.actions.fire(this.key(this.session, Role.ZoomChanged), 2.0);

		Assert.assertEquals(2.0, this.actions.values.get(this.key(this.session, Role.ZoomCurrentScale)));
	}

	private static SequenceTraceMessage message(final String label, final long millis) {
		return new SequenceTraceMessage(ArrayList.class, HashMap.class, label, "details of " + label, millis,
				SequenceTraceMessage.Style.CALL);
	}

	/** The Model holds a message, then notifies it, as the recording does. */
	private void record(final ViewKey sessionKey, final SequenceTraceMessage message) {
		(message.getStyle() == SequenceTraceMessage.Style.COMPLETION ? this.model.completions : this.model.messages)
				.computeIfAbsent(sessionKey, key -> new ArrayList<>()).add(message);
		this.interaction.onMessageAppended(sessionKey, message);
	}

	/** Waits for the live log's writer thread to have written {@code expected}. */
	private static String awaitContent(final File file, final String expected) throws Exception {
		String content = "";
		for (int attempt = 0; attempt < 100; attempt++) {
			if (file.exists()) {
				content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
				if (content.contains(expected)) {
					return content;
				}
			}
			Thread.sleep(20);
		}
		return content;
	}

	@Test
	public void savingTheLogWritesEveryMessageInTheFormatOfTheFile() throws Exception {
		this.open(this.session);
		this.record(this.session, message("first()", 4));
		this.record(this.session, message("second()", 9));
		File text = this.folder.newFile("trace.txt");
		File csv = this.folder.newFile("trace.csv");
		File jsonLines = this.folder.newFile("trace.jsonl");

		this.actions.fire(this.key(this.session, Role.ExportTrace), text);
		this.actions.fire(this.key(this.session, Role.ExportTrace), csv);
		this.actions.fire(this.key(this.session, Role.ExportTrace), jsonLines);

		String textContent = Files.readString(text.toPath(), StandardCharsets.UTF_8);
		Assert.assertTrue(textContent.startsWith(SequenceLogWords.get("log.title") + "\n"));
		Assert.assertTrue(textContent.contains("#2  T0 + 9 ms  ArrayList -> HashMap  second()\n"));
		Assert.assertTrue(textContent.contains(SequenceLogWords.endOfLog("saved")));
		Assert.assertEquals(3, Files.readAllLines(csv.toPath()).size());
		List<String> lines = Files.readAllLines(jsonLines.toPath());
		Assert.assertEquals(4, lines.size());
		Assert.assertTrue(lines.get(3).startsWith("{\"type\":\"end\",\"reason\":\"saved\""));
	}

	@Test
	public void savingTheLogWithoutAFileDoesNothing() {
		this.open(this.session);

		this.actions.fire(this.key(this.session, Role.ExportTrace), (Object) null);

		Assert.assertEquals(0, this.logsFolder.list().length);
	}

	@Test
	public void aLogThatCannotBeWrittenIsReportedNotThrown() throws Exception {
		this.open(this.session);
		File directory = this.folder.newFolder("a-directory");

		this.actions.fire(this.key(this.session, Role.ExportTrace), directory);

		Assert.assertTrue(directory.isDirectory());
		List<String> warnings = this.sequenceDiagramInteractionLogs.messages(Level.WARNING);
		Assert.assertEquals(1, warnings.size());
		Assert.assertTrue(warnings.get(0), warnings.get(0).startsWith("Failed to save the sequence log to "));
	}

	@Test
	public void theLiveLogStartsWithTheSessionWhenThePreferencesAskForIt() throws Exception {
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);
		this.open(this.session);

		this.record(this.session, message("first()", 4));
		this.interaction.onRecordingStopped();

		Assert.assertEquals(2, this.view.liveLogs.size());
		Object[] started = this.view.liveLogs.get(0);
		File file = (File) started[1];
		Assert.assertEquals(this.session, started[0]);
		Assert.assertEquals(this.logsFolder, file.getParentFile());
		Assert.assertNull(this.view.liveLogs.get(1)[1]);
		String content = awaitContent(file, SequenceLogWords.endOfLog("stopped"));
		Assert.assertTrue(content, content.contains("#1  T0 + 4 ms  ArrayList -> HashMap  first()"));
		Assert.assertTrue(content, content.contains(SequenceLogWords.endOfLog("stopped")));
	}

	@Test
	public void theLiveLogIsWrittenInTheFormatOfThePreferences() throws Exception {
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);
		SupervisorSettings.putEnum(SupervisorSettings.SEQUENCE_LOG_FORMAT,
				org.opales.covalent.supervision.sequencediagram.SequenceLog.Format.JSON_LINES);
		this.open(this.session);

		this.record(this.session, message("before()", 1));
		this.record(this.session, message("after()", 2));
		this.interaction.onRecordingStopped();

		File file = (File) this.view.liveLogs.get(0)[1];
		Assert.assertTrue(file.getName().endsWith(".jsonl"));
		String content = awaitContent(file, "\"type\":\"end\"");
		List<String> lines = content.lines().toList();
		Assert.assertEquals(content, 4, lines.size());
		Assert.assertTrue(lines.get(1).contains("\"label\":\"before()\""));
		Assert.assertTrue(lines.get(2).contains("\"label\":\"after()\""));
		Assert.assertTrue(lines.get(3).contains("\"reason\":\"stopped\""));
	}

	@Test
	public void aMessageHeldByTheModelBeforeTheLiveLogStartsIsWrittenOnceWhenItsNoticeComesLater() throws Exception {
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);
		SequenceTraceMessage early = message("early()", 1);
		this.model.messages.computeIfAbsent(this.session, key -> new ArrayList<>()).add(early);
		this.open(this.session);

		this.interaction.onMessageAppended(this.session, early);
		this.record(this.session, message("next()", 2));
		this.interaction.onRecordingStopped();

		File file = (File) this.view.liveLogs.get(0)[1];
		String content = awaitContent(file, SequenceLogWords.endOfLog("stopped"));
		Assert.assertEquals(content, 1, content.split("HashMap  early\\(\\)", -1).length - 1);
		Assert.assertTrue(content, content.contains("#2  T0 + 2 ms  ArrayList -> HashMap  next()"));
		Assert.assertTrue(content, content.contains("· 2 messages ·"));
	}

	private static SequenceTraceMessage failedCompletion(final String label) {
		SequenceTraceMessage.Facts facts = new SequenceTraceMessage.Facts(9L, "service call", "Port", "run", "worker-1",
				"Impl@1", "()", null, 1_000L, "IllegalStateException: boom", false, -1L);
		return new SequenceTraceMessage(ArrayList.class, HashMap.class, label, "details of " + label, 3,
				SequenceTraceMessage.Style.COMPLETION, facts);
	}

	@Test
	public void theFailureOfACompletionNotifiedWhileTheLiveLogIsWrittenIsCountedInItsEnd() throws Exception {
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);
		this.open(this.session);

		this.record(this.session, message("first()", 4));
		this.record(this.session, failedCompletion("late()"));
		this.interaction.onRecordingStopped();

		File file = (File) this.view.liveLogs.get(0)[1];
		String content = awaitContent(file, SequenceLogWords.endOfLog("stopped"));
		Assert.assertTrue(content, content.contains("· " + SequenceLogWords.get("log.messages.one") + " · "
				+ SequenceLogWords.get("log.failures.one")));
		Assert.assertFalse(content, content.contains("late()"));
	}

	@Test
	public void theFailureOfACompletionHeldByTheModelBeforeTheLiveLogStartsIsCountedInItsEnd() throws Exception {
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);
		this.model.completions.computeIfAbsent(this.session, key -> new ArrayList<>()).add(failedCompletion("early()"));
		this.open(this.session);

		this.record(this.session, message("first()", 4));
		this.interaction.onRecordingStopped();

		File file = (File) this.view.liveLogs.get(0)[1];
		String content = awaitContent(file, SequenceLogWords.endOfLog("stopped"));
		Assert.assertTrue(content, content.contains("· " + SequenceLogWords.get("log.messages.one") + " · "
				+ SequenceLogWords.get("log.failures.one")));
	}

	@Test
	public void closingASessionWhileItsLiveLogIsWrittenEndsTheLogAsClosed() throws Exception {
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);
		this.open(this.session);
		this.record(this.session, message("first()", 4));
		File file = (File) this.view.liveLogs.get(0)[1];

		this.interaction.onSessionClosed(this.session);

		String content = awaitContent(file, SequenceLogWords.endOfLog("closed"));
		Assert.assertTrue(content, content.contains("#1  T0 + 4 ms  ArrayList -> HashMap  first()"));
		Assert.assertTrue(content, content.contains(SequenceLogWords.endOfLog("closed")));
	}

	@Test
	public void passivationEndsTheLiveLogsOfTheSessionsStillOpenAsClosed() throws Exception {
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);
		this.open(this.session);
		this.record(this.session, message("first()", 4));
		File file = (File) this.view.liveLogs.get(0)[1];

		this.interaction.unregisterAllRemainingSessions();

		String content = awaitContent(file, SequenceLogWords.endOfLog("closed"));
		Assert.assertTrue(content, content.contains(SequenceLogWords.endOfLog("closed")));
	}

	@Test
	public void aLiveLogThatCannotBeWrittenIsDroppedAndTheViewIsToldSo() throws Exception {
		File blocker = this.folder.newFile("blocker");
		SupervisorSettings.putString(SupervisorSettings.SEQUENCE_LOG_DIRECTORY,
				new File(blocker, "logs").getAbsolutePath());
		SupervisorSettings.putBoolean(SupervisorSettings.SEQUENCE_LIVE_LOG, true);

		this.open(this.session);
		this.record(this.session, message("first()", 4));

		for (int attempt = 0; attempt < 250 && this.view.liveLogs.size() < 2; attempt++) {
			Thread.sleep(20);
		}
		Assert.assertEquals(2, this.view.liveLogs.size());
		Assert.assertNotNull(this.view.liveLogs.get(0)[1]);
		Assert.assertNull(this.view.liveLogs.get(1)[1]);
		Assert.assertEquals(1, this.liveLogLogs.messagesStartingWith("Failed to write the sequence log ").size());
		// the recording goes on and ends without the dropped log
		this.record(this.session, message("second()", 5));
		this.interaction.onRecordingStopped();
		Assert.assertEquals(2, this.view.liveLogs.size());
	}

	@Test
	public void noLiveLogIsWrittenWithoutThePreference() {
		this.open(this.session);
		this.record(this.session, message("first()", 4));
		this.interaction.onRecordingStopped();

		Assert.assertTrue(this.view.liveLogs.isEmpty());
		Assert.assertEquals(0, this.logsFolder.list().length);
	}

	@Test
	public void closingASessionRetiresItsActionsAndOnlyThose() {
		this.interaction.registerPreferenceAction();
		this.interaction.onSessionCreated(this.session, Set.of());
		this.interaction.onSessionCreated(this.other, Set.of());

		this.interaction.onSessionClosed(this.session);

		Set<ActionKey> expected = new HashSet<>();
		for (Role role : Role.values()) {
			expected.add(this.key(this.session, role));
		}
		Assert.assertEquals(expected, new HashSet<>(this.actions.unregistered));
		Assert.assertFalse(this.actions.unregistered.contains(this.key(this.other, Role.Close)));
	}

	@Test
	public void passivationRetiresTheOpenSessionsAndTheToolWideActions() {
		this.interaction.registerPreferenceAction();
		this.interaction.onSessionCreated(this.session, Set.of());
		this.interaction.onSessionCreated(this.other, Set.of());

		this.interaction.unregisterAllRemainingSessions();

		Assert.assertTrue(this.actions.unregistered.contains(this.key(this.session, Role.Close)));
		Assert.assertTrue(this.actions.unregistered.contains(this.key(this.other, Role.Close)));
		Assert.assertTrue(this.actions.unregistered
				.contains(SequenceDiagramPreferenceActionKey.ToggleDynamicObservationDefault));
		Assert.assertTrue(this.actions.unregistered.contains(SequenceRecordingActionKey.StartRecording));
		Assert.assertTrue(this.actions.unregistered.contains(SequenceRecordingActionKey.StopRecording));
	}

	@Test
	public void passivationRetiresEveryToolWideActionToo() {
		this.interaction.registerPreferenceAction();

		this.interaction.unregisterAllRemainingSessions();

		Assert.assertEquals(this.actions.registered, new HashSet<>(this.actions.unregistered));
	}

	@Test
	public void passivationToleratesAnActionManagerThatIsAlreadyPassivated() {
		this.interaction.registerPreferenceAction();
		this.interaction.onSessionCreated(this.session, Set.of());
		this.actions.failOnUnregister = true;

		this.interaction.unregisterAllRemainingSessions();

		Assert.assertTrue(this.actions.unregistered.isEmpty());
	}

	@Test
	public void theSelectionAndAMissingMessageAreNotItsConcern() {
		this.open(this.session);

		this.interaction.onMessageAppended(this.session, null);
		this.interaction.onSelectedActorsChanged(this.session, Set.of(ArrayList.class));

		Assert.assertTrue(this.controller.selectCalls.isEmpty());
		Assert.assertTrue(this.view.recentered.isEmpty());
	}
}
