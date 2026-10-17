/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One pool in the lanes card, in its own compartment: its header - kind, then its figures in
 * aligned columns (workers, queue, occupancy, throughput, latencies), then its verdict - then
 * one lane per thread, its current task and state under its name. Built once, then updated in
 * place.
 */
final class PoolSection {

	static final double NAME_WIDTH = 240;

	/** How far back the pill looks for the component a thread worked for most. */
	private static final int DOMINANT_SECONDS = 5;

	final VBox root = new VBox(6);

	private final ThreadsViewImpl owner;

	/** The pool's verdict as a colored bar, at the left of its header. */
	private final Region verdictBar = new Region();
	private final Label title = new Label();
	private final Label badge = new Label();
	private final TunableChip workers = new TunableChip();
	private final TunableChip queue = new TunableChip();
	private final Label occupancy = statValue();
	private final Label throughput = statValue();
	private final Hyperlink action = new Hyperlink();
	private final Label waits = statValue();
	private final Label durations = statValue();
	private final Tooltip latencyTip = Tooltips.install(this.waits, "");
	private final Label contention = new Label();
	/** A group's figures, in place of a pool's. */
	private final Label groupDetail = new Label();
	/** A pool's figures, in aligned columns: label above, value below. */
	private final HBox stats = new HBox();
	private final List<Node> latencyCells = new ArrayList<>();
	private final VBox rows = new VBox(4);
	private final Map<Long, LaneRow> lanes = new HashMap<>();
	private List<Long> laneOrder = List.of();

	/** The pool shown, as last sampled; {@code null} for a group of threads. */
	private ThreadsSnapshot.Pool pool;

	/** Its tuning, opened from its workers or queue figure; built when first opened. */
	private PoolTuningPopup tuning;

	/** Below this header width, the latencies are left out: they stay in the occupancy's tooltip. */
	private static final double LATENCIES_MIN_WIDTH = 900;

	PoolSection(final ThreadsViewImpl owner) {
		this.owner = owner;
		this.verdictBar.getStyleClass().add("sv-threads-verdict-bar");
		this.title.getStyleClass().add("sv-threads-pool-title");
		this.badge.getStyleClass().add("sv-threads-kind-label");
		VBox name = new VBox(1, this.title, this.badge);
		name.setAlignment(Pos.CENTER_LEFT);
		name.setMinWidth(NAME_WIDTH - 13);
		name.setPrefWidth(NAME_WIDTH - 13);
		name.setMaxWidth(NAME_WIDTH - 13);
		this.contention.getStyleClass().add("sv-threads-contention");
		this.contention.setMinWidth(Region.USE_PREF_SIZE);
		Tooltips.install(this.durations, ThreadsTexts.get("pool.durations.tip"));
		Tooltips.install(this.throughput, ThreadsTexts.get("pool.throughput.tip"));
		this.action.setVisible(false);
		this.action.setManaged(false);
		this.action.getStyleClass().add("sv-cockpit-threads-link");
		this.workers.getStyleClass().add("sv-threads-stat-chip");
		this.queue.getStyleClass().add("sv-threads-stat-chip");
		this.workers.setOnOpen(() -> this.openTuning(this.workers));
		this.queue.setOnOpen(() -> this.openTuning(this.queue));
		this.groupDetail.getStyleClass().add("sv-threads-group-detail");
		this.groupDetail.setMinWidth(Region.USE_PREF_SIZE);
		this.stats.getChildren().addAll(statCell("stat.workers", this.workers), statCell("stat.queue", this.queue),
				statCell("stat.occupancy", this.occupancy), statCell("stat.throughput", this.throughput));
		this.latencyCells.add(statCell("stat.wait", this.waits));
		this.latencyCells.add(statCell("stat.run", this.durations));
		this.stats.getChildren().addAll(this.latencyCells);
		HBox.setHgrow(this.stats, Priority.ALWAYS);
		Region groupSpacer = new Region();
		HBox.setHgrow(groupSpacer, Priority.ALWAYS);
		groupSpacer.managedProperty().bind(this.groupDetail.managedProperty());
		HBox header = new HBox(10, this.verdictBar, name, this.stats, groupSpacer, this.action, this.groupDetail,
				this.contention);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getStyleClass().add("sv-threads-pool-header");
		header.widthProperty().addListener((observable, before, width) -> {
			boolean shown = width.doubleValue() >= LATENCIES_MIN_WIDTH;
			for (Node cell : this.latencyCells) {
				cell.setVisible(shown);
				cell.setManaged(shown);
			}
		});
		this.root.getChildren().addAll(header, this.rows);
		this.root.getStyleClass().add("sv-threads-compartment");
	}

	private static Label statValue() {
		Label label = new Label();
		label.getStyleClass().add("sv-threads-stat-value");
		label.setMinWidth(Region.USE_PREF_SIZE);
		return label;
	}

	/** One figure of the header: its label above, its value below, its columns' share of the width. */
	private static VBox statCell(final String key, final Node value) {
		Label caption = new Label(ThreadsTexts.get(key));
		caption.getStyleClass().add("sv-threads-stat-label");
		VBox cell = new VBox(1, caption, value);
		cell.setAlignment(Pos.CENTER_LEFT);
		cell.getStyleClass().add("sv-threads-stat");
		cell.setMinWidth(Region.USE_PREF_SIZE);
		cell.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(cell, Priority.ALWAYS);
		return cell;
	}

	private void openTuning(final Node anchor) {
		if (this.pool != null && this.pool.tuning() != null) {
			if (this.tuning == null) {
				this.tuning = new PoolTuningPopup(new PoolTuningPopup.Tuner() {
					@Override
					public void setWorkers(final WorkerPoolKind kind, final int count) {
						PoolSection.this.owner.tunePoolWorkers(kind, count);
					}

					@Override
					public void setQueueCapacity(final WorkerPoolKind kind, final int capacity) {
						PoolSection.this.owner.tunePoolQueueCapacity(kind, capacity);
					}
				});
			}
			this.tuning.show(anchor, this.pool);
		}
	}

	/** The workers and queue chips: tunable for the events and general pools, marked once tuned. */
	private void showTuning(final ThreadsSnapshot.Pool pool) {
		ThreadsSnapshot.Tuning tuning = pool.tuning();
		if (tuning == null) {
			this.workers.show(false, false,
					pool.kind() == WorkerPoolKind.DEDICATED ? ThreadsTexts.get("pool.dedicated.tip") : null);
			this.queue.show(false, false, null);
			return;
		}
		boolean workersChanged = pool.targetWorkers() != tuning.workersAtConnection();
		boolean queueChanged = pool.queueCapacity() != tuning.queueCapacityAtConnection();
		String tune = ThreadsTexts.get("pool.tune.tip");
		this.workers.show(true, workersChanged,
				workersChanged ? ThreadsTexts.get("pool.changed.tip", tuning.workersAtConnection()) : tune);
		this.queue.show(true, queueChanged,
				queueChanged ? ThreadsTexts.get("pool.changed.tip", tuning.queueCapacityAtConnection()) : tune);
		if (this.tuning != null) {
			this.tuning.update(pool);
		}
	}

	void update(final ThreadsSnapshot.Pool pool, final ThreadsHistory history, final long windowStart) {
		this.pool = pool;
		this.showTuning(pool);
		this.title.setText(pool.title());
		this.badge.setText(ThreadsFormat.kindBadge(pool.kind()));
		this.workers.setText(String.valueOf(pool.workers().size()));
		this.queue.setText(pool.queuedTasks() + " / " + pool.queueCapacity());
		this.queue.setFill(pool.queueCapacity() <= 0 ? 0 : (double) pool.queuedTasks() / pool.queueCapacity());
		this.occupancy.setText(ThreadsFormat.percent(pool.occupancy()));
		ThreadsHistory.PoolTrack track = history.pool(pool.name());
		double rate = track == null ? 0 : history.throughput(track, track.lastSample);
		boolean tracked = pool.workers().stream().anyMatch(worker -> worker.busyShare() >= 0);
		this.throughput.setText(tracked ? ThreadsTexts.get("stat.perSecond", ThreadsFormat.rate(rate))
				: ThreadsTexts.get("stat.none"));
		List<DurationHistogram> window = track == null ? List.of(DurationHistogram.EMPTY, DurationHistogram.EMPTY)
				: history.histogramsBetween(track, windowStart);
		boolean measured = window.get(1).total() > 0;
		this.waits.setText(measured ? ThreadsFormat.duration(window.get(0).percentileNanos(95)) : ThreadsTexts.get("stat.none"));
		this.durations.setText(measured ? ThreadsFormat.duration(window.get(1).percentileNanos(95)) : ThreadsTexts.get("stat.none"));
		this.latencyTip.setText(!measured ? ThreadsTexts.get("pool.latency.none")
				: ThreadsTexts.get("pool.latency.tip", ThreadsFormat.duration(window.get(0).percentileNanos(50)),
						ThreadsFormat.duration(window.get(0).percentileNanos(95)), window.get(1).total()));
		String verdict = pool.contention().name().toLowerCase(Locale.ROOT);
		this.contention.setText(ThreadsFormat.label(pool.contention()));
		this.contention.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-contention-"));
		this.contention.getStyleClass().add("sv-threads-contention-" + verdict);
		this.verdictBar.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-verdict-bar-"));
		this.verdictBar.getStyleClass().add("sv-threads-verdict-bar-" + verdict);
		this.showPoolFigures(true);
		this.updateLanes(pool.workers());
	}

	/**
	 * A group of threads that is not a pool: the calling threads.
	 *
	 * @param title   the group's name
	 * @param badge   what kind of group it is
	 * @param detail  its figures
	 * @param workers its threads
	 */
	void updateGroup(final String title, final String badge, final String detail,
			final List<ThreadsSnapshot.Worker> workers) {
		this.pool = null;
		if (this.tuning != null) {
			this.tuning.hide();
		}
		this.workers.show(false, false, null);
		this.title.setText(title);
		this.badge.setText(badge);
		this.groupDetail.setText(detail);
		this.showPoolFigures(false);
		this.updateLanes(workers);
	}

	/**
	 * @param text what the header's link does, {@code null} for no link
	 * @param run  what it runs
	 */
	void setAction(final String text, final Runnable run) {
		this.action.setVisible(text != null);
		this.action.setManaged(text != null);
		if (text != null) {
			this.action.setText(text);
			this.action.setOnAction(event -> run.run());
		}
	}

	/** A pool shows its figures, its verdict and its bar; a group, its detail alone. */
	private void showPoolFigures(final boolean shown) {
		for (Node figure : List.of(this.stats, this.contention, this.verdictBar)) {
			figure.setVisible(shown);
			figure.setManaged(shown);
		}
		this.groupDetail.setVisible(!shown);
		this.groupDetail.setManaged(!shown);
	}

	private void updateLanes(final List<ThreadsSnapshot.Worker> workers) {
		List<Long> order = new ArrayList<>();
		for (ThreadsSnapshot.Worker worker : workers) {
			order.add(worker.threadId());
			this.lanes.computeIfAbsent(worker.threadId(), id -> new LaneRow(id)).name.setText(worker.name());
		}
		this.lanes.keySet().retainAll(order);
		if (!order.equals(this.laneOrder)) {
			this.laneOrder = order;
			List<Node> nodes = new ArrayList<>();
			for (Long id : order) {
				nodes.add(this.lanes.get(id).root);
			}
			this.rows.getChildren().setAll(nodes);
		}
	}

	/**
	 * @param moment the moment picked, as its thread, first and last samples, {@code null} if none
	 */
	void draw(final ThreadsHistory history, final long windowEnd, final int windowSize, final long cursor,
			final Palette palette, final boolean paused, final long[] moment) {
		for (LaneRow row : this.lanes.values()) {
			boolean picked = moment != null && moment[0] == row.threadId;
			row.draw(history, windowEnd, windowSize, cursor, palette, paused, picked ? moment[1] : -1,
					picked ? moment[2] : -1);
		}
	}

	void select(final long threadId) {
		for (LaneRow row : this.lanes.values()) {
			ThreadsViewImpl.setStyle(row.root, "sv-threads-lane-selected", row.threadId == threadId);
		}
	}

	/**
	 * @return what a thread's row says of its worker's task: its label - an event's {@code
	 *         Consumer.method}, an async task's queue key - otherwise the component it works for,
	 *         as a task submitted to the general pool has no label of its own
	 */
	static String taskText(final ThreadsSnapshot.Worker worker) {
		if (worker.taskLabel() != null) {
			return worker.taskLabel();
		}
		return worker.taskComponent() != null ? worker.taskComponent().getSimpleName() : ThreadsTexts.get("task");
	}

	/** One thread: its name, what it does - a state dot, its task, its state - and its lane. */
	private final class LaneRow {

		final long threadId;

		/** The component its pill shows as the one it works for most, kept unless another clearly takes over. */
		private Class<?> shownComponent;
		final HBox root;
		final Label name = new Label();
		final Region stateDot = new Region();
		final Label sub = new Label();
		final LaneChart lane = new LaneChart();

		LaneRow(final long threadId) {
			this.threadId = threadId;
			this.name.getStyleClass().add("sv-threads-lane-name");
			this.sub.getStyleClass().add("sv-threads-lane-sub");
			this.stateDot.getStyleClass().add("sv-threads-lane-dot");
			this.sub.setMinWidth(0);
			HBox doing = new HBox(6, this.stateDot, this.sub);
			doing.setAlignment(Pos.CENTER_LEFT);
			VBox label = new VBox(2, this.name, doing);
			label.setAlignment(Pos.CENTER_LEFT);
			label.setMinWidth(NAME_WIDTH);
			label.setPrefWidth(NAME_WIDTH);
			label.setMaxWidth(NAME_WIDTH);
			HBox.setHgrow(this.lane, Priority.ALWAYS);
			this.root = new HBox(label, this.lane);
			this.root.setAlignment(Pos.CENTER_LEFT);
			this.root.getStyleClass().add("sv-threads-lane");
			this.root.setOnMouseClicked(event -> PoolSection.this.owner.select(this.threadId));
			this.lane.setOnHover(sample -> PoolSection.this.owner.hover(sample));
			this.lane.setOnClick((from, to) -> PoolSection.this.owner.pickMoment(this.threadId, from, to));
		}

		void draw(final ThreadsHistory history, final long windowEnd, final int windowSize, final long cursor,
				final Palette palette, final boolean paused, final long momentFrom, final long momentTo) {
			ThreadsHistory.Track track = history.track(this.threadId);
			if (track == null || windowEnd < 0) {
				return;
			}
			long last = Math.min(windowEnd, track.lastSample);
			ThreadActivity activity = history.activity(track, last);
			ThreadsSnapshot.Worker worker = track.latest;
			String since = ThreadsFormat.clock(history.runLength(track, last) / ThreadsHistory.PER_SECOND);
			String sub = "";
			String taskName = null;
			if (activity != null) {
				sub = switch (activity) {
					case BLOCKED -> ThreadsTexts.get("lane.blocked", since);
					case IDLE -> ThreadsTexts.get(track.caller ? "lane.outsideCalls" : "lane.idle", since);
					case UNTRACKED -> ThreadsTexts.get("lane.untracked", since);
					case RUNS, WAITS -> !paused && worker != null && worker.taskNanos() >= 0
							? ThreadsTexts.get(activity == ThreadActivity.WAITS ? "lane.waitsTask" : "lane.runsTask",
									ThreadsFormat.duration(worker.taskNanos()))
							: ThreadsTexts.get(activity == ThreadActivity.WAITS ? "lane.waits" : "lane.runs", since);
				};
				if (!paused && worker != null && worker.taskNanos() >= 0) {
					taskName = taskText(worker);
				}
			}
			// Idle at the sample, yet busy in between: short tasks, which the share tells better.
			double recent = 0;
			for (long sample = last; sample > last - ThreadsHistory.PER_SECOND && sample >= history.oldest(); sample--) {
				recent += Math.max(0, history.busy(track, sample));
			}
			recent /= ThreadsHistory.PER_SECOND;
			if (recent > 0 && (activity == ThreadActivity.IDLE || activity == ThreadActivity.UNTRACKED)) {
				sub = ThreadsTexts.get("lane.shortTasks", ThreadsFormat.share(recent));
			}
			// The pill at the lane's end says what the thread works on: its task while one lasts;
			// otherwise the component it worked for most over the last seconds, however short its
			// tasks; otherwise, outlined, the last one it worked for, and how long ago.
			String pill = taskName;
			boolean pillEnded = false;
			ThreadActivity dot = activity == null ? ThreadActivity.IDLE : activity;
			if (pill == null) {
				ThreadsHistory.Work dominant = history.dominantWork(track, last, DOMINANT_SECONDS * ThreadsHistory.PER_SECOND,
						paused ? null : this.shownComponent);
				this.shownComponent = paused ? this.shownComponent : dominant == null ? null : dominant.component();
				if (dominant != null) {
					pill = ThreadsTexts.get("lane.pill.share", dominant.component().getSimpleName(),
							ThreadsFormat.share(dominant.share()));
					dot = ThreadActivity.RUNS;
				} else {
					ThreadsHistory.Work previous = history.lastWork(track, last);
					if (previous != null) {
						pill = ThreadsTexts.get("lane.pill.ago", previous.component().getSimpleName(),
								ThreadsFormat.age((last - previous.sample()) / ThreadsHistory.PER_SECOND));
						pillEnded = true;
						dot = ThreadActivity.RUNS;
					}
				}
			}
			this.sub.setText(sub);
			ThreadActivity shown = activity == null ? ThreadActivity.IDLE : activity;
			this.stateDot.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-bg-"));
			this.stateDot.getStyleClass().add("sv-threads-bg-" + ThreadsFormat.styleSuffix(shown));
			this.lane.draw(history, track, windowEnd, windowSize, cursor, pill, pillEnded, LaneChart.color(dot, palette),
					palette, momentFrom, momentTo);
		}
	}
}
