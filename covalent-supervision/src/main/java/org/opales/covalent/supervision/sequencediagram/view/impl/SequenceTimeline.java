/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.view.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongConsumer;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.sequencediagram.view.TimelineStyle;

/**
 * A recording session's timeline: over time since the recording started, how many messages
 * of each kind happened (the volume), how long the slowest invocation took (the duration),
 * where the failures are, and which time span the diagram currently shows. Clicking or
 * dragging on it asks for the diagram to be brought to that moment.
 * <p>
 * Time is cut into slices one bar wide. The duration of a slice is the longest one
 * among the invocations that started in it, on a logarithmic scale, colored by the kind of
 * that invocation. How volume and duration are combined depends on the {@link TimelineStyle}.
 * <p>
 * The diagram is laid out in message order, not in time: the span shown is that of the
 * first and last messages in view, as {@link Source#viewedRange()} reports it.
 */
final class SequenceTimeline extends VBox {

	/** What the timeline draws from. */
	interface Source {

		/** @return every message of the session, in arrival order */
		List<SequenceTraceMessage> messages();

		/** @return how the invocations without a return message ended, in arrival order */
		List<SequenceTraceMessage> completions();

		/** @return whether the kind filters let this message through */
		boolean isShown(SequenceTraceMessage message);

		/** @return the relative times of the first and last messages in view, {@code null} when none */
		long[] viewedRange();
	}

	private static final double STRIP_HEIGHT = 36;

	/** Height of the volume density band of {@link TimelineStyle#DENSITY}, and its gap. */
	private static final double DENSITY_HEIGHT = 12;
	private static final double DENSITY_GAP = 4;

	/** The shortest time the duration scale shows, in nanoseconds. */
	private static final double DURATION_FLOOR_NANOS = 1_000;

	/** Width of one histogram bar, gap included. */
	private static final double BIN_WIDTH = 4;

	/** The shortest span the timeline covers, so a young recording does not look stretched. */
	private static final long MIN_SPAN_MILLIS = 1_000;

	/** {@code SequenceTraceMessage.Facts#kind()} values, bottom to top, and their style suffix. */
	private static final String[][] KINDS = {
			{ "Service", "service" },
			{ "Event", "event" },
			{ "Parameter", "parameter" },
			{ "Action", "action" } };

	private final Source source;
	private final LongConsumer onSeek;
	private final Label viewingLabel = new Label();
	private final Pane histogram = new Pane();
	private final Pane failures = new Pane();
	private final Region window = new Region();
	private final Pane strip = new Pane();
	private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(200));
	private final HBox modeSwitch;
	private final ToggleButton durationMode = new ToggleButton(SequenceDiagramTexts.get("timeline.duration"));

	private TimelineStyle presentation;

	private static final String SEEK_HINT = SequenceDiagramTexts.get("timeline.seekHint");

	private final Tooltip stripTooltip;

	/** The slices last drawn, for the scale and the tooltip; {@code null} before the first drawing. */
	private Slices drawnSlices;

	/** Scale values written over the strip, left and right. */
	private final Label leftScaleTop = scaleLabel();
	private final Label leftScaleBottom = scaleLabel();
	private final Label rightScaleTop = scaleLabel();
	private final Label rightScaleBottom = scaleLabel();
	private long spanMillis = MIN_SPAN_MILLIS;
	private boolean refreshPending = false;

	SequenceTimeline(final Source source, final LongConsumer onSeek, final TimelineStyle presentation) {
		super(6);
		this.source = source;
		this.onSeek = onSeek;
		this.presentation = presentation;
		this.getStyleClass().add("sv-seq-timeline");

		Label title = new Label(SequenceDiagramTexts.get("timeline.title"));
		title.getStyleClass().add("sv-eyebrow");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		this.viewingLabel.getStyleClass().add("sv-seq-p95");
		ToggleButton volumeMode = new ToggleButton(SequenceDiagramTexts.get("timeline.volume"));
		ToggleGroup modes = new ToggleGroup();
		volumeMode.setToggleGroup(modes);
		this.durationMode.setToggleGroup(modes);
		volumeMode.setSelected(true);
		modes.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
			if (newToggle == null) {
				modes.selectToggle(oldToggle);
			} else {
				this.refresh();
			}
		});
		Tooltips.install(volumeMode, SequenceDiagramTexts.get("timeline.volume.tip"));
		Tooltips.install(this.durationMode,
				SequenceDiagramTexts.get("timeline.duration.tip"));
		this.modeSwitch = new HBox(volumeMode, this.durationMode);
		this.modeSwitch.getStyleClass().add("sv-seq-timeline-modes");
		HBox heading = new HBox(10, title, spacer, this.modeSwitch, this.viewingLabel);
		heading.setAlignment(Pos.CENTER_LEFT);

		this.window.getStyleClass().add("sv-seq-timeline-window");
		this.window.setManaged(false);
		this.window.setMouseTransparent(true);
		this.histogram.setMouseTransparent(true);
		this.failures.setMouseTransparent(true);
		this.strip.getChildren().addAll(this.histogram, this.failures, this.window, this.leftScaleTop,
				this.leftScaleBottom, this.rightScaleTop, this.rightScaleBottom);
		this.strip.getStyleClass().add("sv-seq-timeline-strip");
		this.strip.setCursor(Cursor.HAND);
		this.stripTooltip = Tooltips.install(this.strip, SEEK_HINT);
		this.strip.addEventHandler(MouseEvent.MOUSE_MOVED, event -> this.describeSliceAt(event.getX()));
		this.strip.widthProperty().addListener((observable, oldWidth, newWidth) -> this.refresh());
		this.strip.addEventHandler(MouseEvent.MOUSE_PRESSED, this::seek);
		this.strip.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::seek);

		this.getChildren().addAll(heading, this.strip);
		this.applyPresentation();
		this.refreshDelay.setOnFinished(event -> {
			this.refreshPending = false;
			this.refresh();
		});
		this.refresh();
	}

	/** Schedules a redraw: messages arrive far faster than the timeline needs repainting. */
	void invalidate() {
		if (!this.refreshPending) {
			this.refreshPending = true;
			this.refreshDelay.playFromStart();
		}
	}

	/** Changes how volume and duration are shown, and redraws. */
	void setPresentation(final TimelineStyle presentation) {
		this.presentation = presentation;
		this.applyPresentation();
		this.refresh();
	}

	private void applyPresentation() {
		boolean switchable = this.presentation == TimelineStyle.SWITCH;
		this.modeSwitch.setVisible(switchable);
		this.modeSwitch.setManaged(switchable);
		double height = this.stripHeight();
		this.strip.setMinHeight(height);
		this.strip.setPrefHeight(height);
		this.strip.setMaxHeight(height);
	}

	private double stripHeight() {
		return this.presentation == TimelineStyle.DENSITY ? STRIP_HEIGHT + DENSITY_GAP + DENSITY_HEIGHT : STRIP_HEIGHT;
	}

	/** Redraws now, e.g. after the kind filters changed. */
	void refresh() {
		List<SequenceTraceMessage> messages = this.source.messages();
		long lastMillis = messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getRelativeTimeMillis();
		this.spanMillis = Math.max(MIN_SPAN_MILLIS, lastMillis);
		double width = this.strip.getWidth();
		int binCount = Math.max(1, (int) (width / BIN_WIDTH));
		Slices slices = this.measure(messages, binCount);
		this.drawnSlices = slices;

		this.histogram.getChildren().clear();
		this.failures.getChildren().clear();
		switch (this.presentation) {
		case SWITCH -> {
			if (this.durationMode.isSelected()) {
				this.drawDurationBars(slices);
			} else {
				this.drawVolumeBars(slices);
			}
		}
		case OVERLAY -> {
			this.drawVolumeBars(slices);
			this.drawDurationLine(slices);
		}
		case DENSITY -> {
			this.drawDurationBars(slices);
			this.drawDensityBand(slices);
		}
		}
		this.updateScale(slices);
		this.refreshWindow();
	}

	/** Writes what the strip's top and bottom stand for, on the side of each scale drawn. */
	private void updateScale(final Slices slices) {
		String volumeTop = SequenceDiagramTexts.count("timeline.scale.messages", slices.highestCount());
		String durationTop = formatNanos(slices.durationCeilingNanos());
		String durationBottom = formatNanos(DURATION_FLOOR_NANOS);
		boolean durationBars = this.presentation == TimelineStyle.DENSITY
				|| this.presentation == TimelineStyle.SWITCH && this.durationMode.isSelected();
		this.leftScaleTop.setText(durationBars ? durationTop : volumeTop);
		this.leftScaleBottom.setText(durationBars ? durationBottom : "0");
		boolean overlay = this.presentation == TimelineStyle.OVERLAY;
		this.rightScaleTop.setText(overlay ? durationTop : "");
		this.rightScaleBottom.setText(overlay ? durationBottom : "");
		this.rightScaleTop.setVisible(overlay);
		this.rightScaleBottom.setVisible(overlay);
		this.placeScaleLabels();
	}

	private void placeScaleLabels() {
		double width = this.strip.getWidth();
		for (Label label : List.of(this.leftScaleTop, this.leftScaleBottom, this.rightScaleTop, this.rightScaleBottom)) {
			label.autosize();
		}
		this.leftScaleTop.relocate(3, 1);
		this.leftScaleBottom.relocate(3, STRIP_HEIGHT - this.leftScaleBottom.getHeight() - 1);
		this.rightScaleTop.relocate(width - this.rightScaleTop.getWidth() - 3, 1);
		this.rightScaleBottom.relocate(width - this.rightScaleBottom.getWidth() - 3,
				STRIP_HEIGHT - this.rightScaleBottom.getHeight() - 1);
	}

	/** Puts the pointed slice's values in the strip's tooltip. */
	private void describeSliceAt(final double x) {
		Slices slices = this.drawnSlices;
		if (slices == null || slices.size() == 0) {
			return;
		}
		int bin = Math.max(0, Math.min(slices.size() - 1, (int) (x / BIN_WIDTH)));
		double fromMillis = (double) bin / slices.size() * this.spanMillis;
		double toMillis = (double) (bin + 1) / slices.size() * this.spanMillis;
		StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%.2f – %.2f s", fromMillis / 1000,
				toMillis / 1000));
		int total = 0;
		StringBuilder kinds = new StringBuilder();
		for (int kind = 0; kind < KINDS.length; kind++) {
			int count = slices.counts()[bin][kind];
			total += count;
			if (count > 0) {
				kinds.append(kinds.length() == 0 ? "" : ", ")
						.append(SequenceDiagramTexts.count("timeline.slice." + KINDS[kind][0], count));
			}
		}
		String messages = SequenceDiagramTexts.count("timeline.slice.messages", total);
		text.append("\n").append(total > 0 ? SequenceDiagramTexts.get("timeline.slice.messagesByKind", messages, kinds)
				: messages);
		if (slices.slowestNanos()[bin] > 0) {
			text.append("\n").append(SequenceDiagramTexts.get("timeline.slice.slowest",
					formatNanos(slices.slowestNanos()[bin]),
					SequenceDiagramTexts.get("timeline.kindName." + KINDS[slices.slowestKind()[bin]][0])));
		}
		if (slices.failed()[bin]) {
			text.append("\n").append(SequenceDiagramTexts.get("timeline.slice.failure"));
		}
		text.append("\n\n").append(SEEK_HINT);
		this.stripTooltip.setText(text.toString());
	}

	private static Label scaleLabel() {
		Label label = new Label();
		label.getStyleClass().add("sv-seq-timeline-scale");
		label.setManaged(false);
		label.setMouseTransparent(true);
		return label;
	}

	private static String formatNanos(final double nanos) {
		if (nanos < 1_000_000) {
			return String.format(Locale.ROOT, "%.0f µs", nanos / 1_000);
		}
		if (nanos < 1_000_000_000) {
			double millis = nanos / 1_000_000;
			return String.format(Locale.ROOT, millis < 10 ? "%.1f ms" : "%.0f ms", millis);
		}
		return String.format(Locale.ROOT, "%.2f s", nanos / 1_000_000_000);
	}

	/** Volume and duration of every time slice. */
	private record Slices(int[][] counts, int highestCount, double[] slowestNanos, int[] slowestKind,
			boolean[] failed, double durationCeilingNanos) {

		int size() {
			return this.failed.length;
		}

		/** @return the slice's duration as a fraction of the strip height, 0 when nothing started in it */
		double durationFraction(final int bin) {
			if (this.slowestNanos[bin] <= 0) {
				return 0;
			}
			double nanos = Math.max(DURATION_FLOOR_NANOS, this.slowestNanos[bin]);
			return Math.max(0.06, Math.log(nanos / DURATION_FLOOR_NANOS)
					/ Math.log(this.durationCeilingNanos / DURATION_FLOOR_NANOS));
		}
	}

	private Slices measure(final List<SequenceTraceMessage> messages, final int binCount) {
		int[][] counts = new int[binCount][KINDS.length];
		double[] slowestNanos = new double[binCount];
		int[] slowestKind = new int[binCount];
		boolean[] failed = new boolean[binCount];
		// A duration is known by the message ending an invocation; it is charged to the
		// slice where that invocation started.
		Map<Long, Long> startMillisByInvocation = new HashMap<>();
		double longestNanos = 0;
		for (SequenceTraceMessage message : messages) {
			if (!this.source.isShown(message)) {
				continue;
			}
			SequenceTraceMessage.Facts facts = message.getFacts();
			int kind = kindIndex(facts == null ? null : facts.kind());
			int bin = this.binOf(message.getRelativeTimeMillis(), binCount);
			counts[bin][kind]++;
			if (facts == null) {
				continue;
			}
			if (facts.isFailure()) {
				failed[bin] = true;
			}
			if (message.getStyle() == SequenceTraceMessage.Style.CALL) {
				startMillisByInvocation.putIfAbsent(facts.invocationId(), message.getRelativeTimeMillis());
			}
			longestNanos = Math.max(longestNanos,
					this.chargeDuration(message, kind, bin, binCount, startMillisByInvocation, slowestNanos, slowestKind));
		}
		for (SequenceTraceMessage completion : this.source.completions()) {
			SequenceTraceMessage.Facts facts = completion.getFacts();
			if (facts == null || !this.source.isShown(completion)) {
				continue;
			}
			int bin = this.binOf(completion.getRelativeTimeMillis(), binCount);
			longestNanos = Math.max(longestNanos, this.chargeDuration(completion, kindIndex(facts.kind()), bin,
					binCount, startMillisByInvocation, slowestNanos, slowestKind));
			if (facts.isFailure()) {
				Long startMillis = startMillisByInvocation.get(facts.invocationId());
				failed[startMillis == null ? bin : this.binOf(startMillis, binCount)] = true;
			}
		}
		int highest = 1;
		for (int[] bin : counts) {
			int total = 0;
			for (int count : bin) {
				total += count;
			}
			highest = Math.max(highest, total);
		}
		double ceiling = Math.max(DURATION_FLOOR_NANOS * 100, longestNanos);
		return new Slices(counts, highest, slowestNanos, slowestKind, failed, ceiling);
	}

	/**
	 * Charges a known duration to the slice where its invocation started.
	 *
	 * @return the duration, 0 when this message does not know it
	 */
	private double chargeDuration(final SequenceTraceMessage message, final int kind, final int bin, final int binCount,
			final Map<Long, Long> startMillisByInvocation, final double[] slowestNanos, final int[] slowestKind) {
		SequenceTraceMessage.Facts facts = message.getFacts();
		if (facts.durationNanos() < 0) {
			return 0;
		}
		Long startMillis = startMillisByInvocation.get(facts.invocationId());
		int startBin = startMillis == null ? bin : this.binOf(startMillis, binCount);
		if (facts.durationNanos() >= slowestNanos[startBin]) {
			slowestNanos[startBin] = Math.max(1, facts.durationNanos());
			slowestKind[startBin] = kind;
		}
		return facts.durationNanos();
	}

	private void drawVolumeBars(final Slices slices) {
		for (int bin = 0; bin < slices.size(); bin++) {
			double x = bin * BIN_WIDTH;
			double bottom = STRIP_HEIGHT;
			for (int kind = 0; kind < KINDS.length; kind++) {
				int count = slices.counts()[bin][kind];
				if (count == 0) {
					continue;
				}
				double height = Math.max(1, (STRIP_HEIGHT - 2) * count / slices.highestCount());
				Rectangle bar = new Rectangle(x, bottom - height, BIN_WIDTH - 1, height);
				bar.getStyleClass().addAll("sv-seq-timeline-bar", "sv-kind-" + KINDS[kind][1]);
				this.histogram.getChildren().add(bar);
				bottom -= height;
			}
			if (slices.failed()[bin]) {
				Rectangle mark = new Rectangle(x, 0, Math.max(2, BIN_WIDTH - 1), STRIP_HEIGHT);
				mark.getStyleClass().add("sv-seq-timeline-failure");
				this.failures.getChildren().add(mark);
			}
		}
	}

	private void drawDurationBars(final Slices slices) {
		for (int bin = 0; bin < slices.size(); bin++) {
			double fraction = slices.durationFraction(bin);
			if (fraction <= 0) {
				continue;
			}
			double height = (STRIP_HEIGHT - 2) * fraction;
			Rectangle bar = new Rectangle(bin * BIN_WIDTH, STRIP_HEIGHT - height, BIN_WIDTH - 1, height);
			bar.getStyleClass().add(slices.failed()[bin] ? "sv-seq-timeline-failure"
					: "sv-kind-" + KINDS[slices.slowestKind()[bin]][1]);
			bar.getStyleClass().add("sv-seq-timeline-bar");
			this.histogram.getChildren().add(bar);
		}
	}

	private void drawDurationLine(final Slices slices) {
		Polyline line = new Polyline();
		line.getStyleClass().add("sv-seq-timeline-duration");
		for (int bin = 0; bin < slices.size(); bin++) {
			double fraction = slices.durationFraction(bin);
			if (fraction <= 0) {
				continue;
			}
			double y = STRIP_HEIGHT - 1 - (STRIP_HEIGHT - 2) * fraction;
			line.getPoints().addAll(bin * BIN_WIDTH + BIN_WIDTH / 2, y);
			if (slices.failed()[bin]) {
				Circle dot = new Circle(bin * BIN_WIDTH + BIN_WIDTH / 2, y, 2.6);
				dot.getStyleClass().add("sv-seq-timeline-failure");
				this.failures.getChildren().add(dot);
			}
		}
		this.histogram.getChildren().add(line);
	}

	private void drawDensityBand(final Slices slices) {
		double top = STRIP_HEIGHT + DENSITY_GAP;
		for (int bin = 0; bin < slices.size(); bin++) {
			int total = 0;
			for (int count : slices.counts()[bin]) {
				total += count;
			}
			if (total == 0) {
				continue;
			}
			Rectangle cell = new Rectangle(bin * BIN_WIDTH, top, BIN_WIDTH, DENSITY_HEIGHT);
			cell.getStyleClass().add("sv-seq-timeline-density");
			cell.setOpacity(0.15 + 0.85 * total / slices.highestCount());
			this.histogram.getChildren().add(cell);
		}
	}

	/** Moves the highlighted span to what the diagram currently shows. */
	void refreshWindow() {
		long[] range = this.source.viewedRange();
		double width = this.strip.getWidth();
		if (range == null || width <= 0) {
			this.window.setVisible(false);
			this.viewingLabel.setText("");
			return;
		}
		double from = width * range[0] / this.spanMillis;
		double to = width * range[1] / this.spanMillis;
		double windowWidth = Math.max(6, to - from);
		this.window.resizeRelocate(Math.min(from, width - windowWidth), -3, windowWidth, this.stripHeight() + 6);
		this.window.setVisible(true);
		this.viewingLabel.setText(SequenceDiagramTexts.get("timeline.viewing", seconds(range[0]), seconds(range[1])));
	}

	private void seek(final MouseEvent event) {
		double width = this.strip.getWidth();
		if (width <= 0) {
			return;
		}
		double ratio = Math.max(0, Math.min(1, event.getX() / width));
		this.onSeek.accept((long) (ratio * this.spanMillis));
		event.consume();
	}

	private int binOf(final long relativeTimeMillis, final int binCount) {
		int bin = (int) ((double) relativeTimeMillis / this.spanMillis * binCount);
		return Math.max(0, Math.min(binCount - 1, bin));
	}

	private static int kindIndex(final String kind) {
		for (int index = 0; index < KINDS.length; index++) {
			if (KINDS[index][0].equals(kind)) {
				return index;
			}
		}
		return 0;
	}

	private static String seconds(final long millis) {
		return String.format(Locale.ROOT, "%.2f", millis / 1000.0);
	}
}
