/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The head of the Pool health card: a verdict in a callout, one sentence on the most pressed
 * pool and its cause, then, in a compartment, one line per pool - its occupancy as a meter, its
 * verdict, its threads' dots and why it is pressed - with a way to tune it. Every figure spans
 * the last minute, as the panel's tiles do. Lines are updated in place, so that their links keep
 * their clicks.
 */
final class PoolHealth {

	/** How far back a thread's dot looks for its work. */
	private static final int RECENT_SECONDS = 2;

	private static final int MINUTE = ThreadsHistory.PER_SECOND * 60;

	private final ThreadsViewImpl owner;

	private final Label verdict = new Label();

	/** The verdict's cause, under it: the most pressed pool's reasons. */
	private final Label cause = new Label();

	private final HBox callout;

	private final VBox lines = new VBox();

	/** How wide a pool's occupancy meter is. */
	private static final double METER_WIDTH = 56;

	private final Map<String, Line> byPool = new HashMap<>();

	/** The tuning opened from a line's link, built when first opened. */
	private PoolTuningPopup tuning;

	private final ThreadDots callerDots = new ThreadDots();

	private final VBox callersRow = new VBox(4);

	/** What stands out among a group of threads: those stuck, or in a deadlock. */
	private record Stuck(int tasks, long longest, int deadlocked, boolean critical) {

		static final Stuck NONE = new Stuck(0, -1, 0, false);

		static Stuck of(final List<ThreadDot> dots) {
			int tasks = 0;
			long longest = -1;
			int deadlocked = 0;
			boolean critical = false;
			for (ThreadDot dot : dots) {
				if (dot.deadlocked()) {
					deadlocked++;
					critical = true;
				} else if (dot.stuck()) {
					tasks++;
					longest = Math.max(longest, dot.taskNanos());
					critical |= dot.critical();
				}
			}
			return new Stuck(tasks, longest, deadlocked, critical);
		}

		boolean any() {
			return this.tasks > 0 || this.deadlocked > 0;
		}

		/** @return whether this is worse than another */
		boolean worseThan(final Stuck other) {
			if (this.critical != other.critical) {
				return this.critical;
			}
			if (this.deadlocked != other.deadlocked) {
				return this.deadlocked > other.deadlocked;
			}
			return this.longest > other.longest;
		}
	}

	/** One pool's line. */
	private final class Line {

		final VBox root;
		final Label name = new Label();
		final Label badge = new Label();
		final Region meter = new Region();
		final Region meterFill = new Region();
		final Label occupancy = new Label();
		final Label detail = new Label();
		/** The detail's whole text, when there is one. */
		final Tooltip detailTip = Tooltips.install(new Region(), "");
		final Hyperlink tune = new Hyperlink(ThreadsTexts.get("health.tune"));
		final ThreadDots dots = new ThreadDots();
		ThreadsSnapshot.Pool pool;

		Line() {
			this.name.setMinWidth(Region.USE_PREF_SIZE);
			this.badge.setMinWidth(Region.USE_PREF_SIZE);
			this.occupancy.getStyleClass().add("sv-threads-health-occupancy");
			this.occupancy.setMinWidth(36);
			this.occupancy.setAlignment(Pos.CENTER_RIGHT);
			this.badge.setMinWidth(80);
			this.badge.setAlignment(Pos.CENTER);
			this.meter.getStyleClass().add("sv-threads-meter");
			this.meterFill.getStyleClass().add("sv-threads-meter-fill");
			this.meterFill.setManaged(false);
			StackPane meterBox = new StackPane(this.meter, this.meterFill);
			meterBox.setAlignment(Pos.CENTER_LEFT);
			for (Region part : List.of(this.meter, this.meterFill)) {
				part.setMinHeight(6);
				part.setPrefHeight(6);
				part.setMaxHeight(6);
			}
			this.meter.setMinWidth(METER_WIDTH);
			this.meter.setPrefWidth(METER_WIDTH);
			this.meter.setMaxWidth(METER_WIDTH);
			Tooltips.install(this.occupancy, ThreadsTexts.get("health.occupancy.tip"));
			// One line, cut with "...", its whole text in a tooltip: the card keeps its height.
			this.detail.getStyleClass().add("sv-threads-health-detail");
			this.detail.setWrapText(false);
			this.detail.setTextOverrun(OverrunStyle.ELLIPSIS);
			this.detail.setMinWidth(0);
			this.detail.setMinHeight(Region.USE_PREF_SIZE);
			Tooltip.uninstall(this.detail, this.detailTip);
			this.tune.getStyleClass().add("sv-cockpit-threads-link");
			this.tune.setMinWidth(Region.USE_PREF_SIZE);
			this.tune.setOnAction(event -> PoolHealth.this.openTuning(this.tune, this.pool));
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox top = new HBox(10, this.name, spacer, meterBox, this.occupancy, this.badge);
			top.setAlignment(Pos.CENTER_LEFT);
			HBox.setHgrow(this.detail, Priority.ALWAYS);
			this.detail.setMaxWidth(Double.MAX_VALUE);
			HBox bottom = new HBox(8, this.dots, this.detail, this.tune);
			bottom.setAlignment(Pos.CENTER_LEFT);
			this.root = new VBox(6, top, bottom);
			this.root.getStyleClass().add("sv-threads-health-row");
		}
	}

	PoolHealth(final ThreadsViewImpl owner) {
		this.owner = owner;
		this.verdict.setWrapText(true);
		this.verdict.setAlignment(Pos.TOP_LEFT);
		this.cause.getStyleClass().add("sv-threads-callout-cause");
		this.cause.setTextOverrun(OverrunStyle.ELLIPSIS);
		this.cause.setMinWidth(0);
		Region bar = new Region();
		bar.getStyleClass().add("sv-threads-callout-bar");
		VBox text = new VBox(2, this.verdict, this.cause);
		text.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(text, Priority.ALWAYS);
		this.callout = new HBox(10, bar, text);
		this.callout.getStyleClass().add("sv-threads-callout");
		this.lines.getStyleClass().addAll("sv-threads-compartment", "sv-threads-compartment-padded");
		Label callersName = new Label(ThreadsTexts.get("health.callers"));
		callersName.getStyleClass().add("sv-threads-health-detail");
		this.callersRow.getChildren().setAll(callersName, this.callerDots);
		this.callersRow.getStyleClass().addAll("sv-threads-health-row", "sv-threads-health-row-last");
	}

	/** @return the verdict's callout */
	Node verdict() {
		return this.callout;
	}

	VBox lines() {
		return this.lines;
	}

	void render(final ThreadsSnapshot snapshot, final ThreadsHistory history) {
		List<ThreadsSnapshot.Pool> pools = snapshot.pools();
		List<ThreadsSnapshot.Pool> pressed = pools.stream().filter(pool -> pool.contention() != Contention.FLUID)
				.sorted(Comparator.comparing((ThreadsSnapshot.Pool pool) -> pool.contention() == Contention.SATURATED)
						.thenComparingLong(ThreadsSnapshot.Pool::rejectedLastMinute)
						.thenComparingDouble(pool -> busy(history, pool)).reversed())
				.toList();
		Contention worst = pressed.isEmpty() ? Contention.FLUID : pressed.get(0).contention();
		Set<String> deadlocked = new HashSet<>();
		snapshot.deadlocks().forEach(deadlock -> deadlocked.addAll(deadlock.threadNames()));
		long threshold = ThreadDot.thresholdNanos();
		// Green as long as a thread worked at all over the last seconds: its short tasks show.
		ToDoubleFunction<ThreadsSnapshot.Worker> recent = worker -> history.recentBusy(worker.threadId(),
				RECENT_SECONDS * ThreadsHistory.PER_SECOND);
		Map<String, Stuck> stuckByPool = new HashMap<>();
		for (ThreadsSnapshot.Pool pool : pools) {
			Line line = this.byPool.computeIfAbsent(pool.name(), ignored -> new Line());
			stuckByPool.put(pool.name(), Stuck.of(line.dots.update(pool.workers(), deadlocked, threshold, recent)));
		}
		boolean showCallers = snapshot.callers().dataFlows() && !snapshot.callers().threads().isEmpty();
		Stuck callersStuck = showCallers
				? Stuck.of(this.callerDots.update(snapshot.callers().threads(), deadlocked, threshold, recent))
				: Stuck.NONE;
		String stuckName = null;
		Stuck stuckWorst = Stuck.NONE;
		for (ThreadsSnapshot.Pool pool : pools) {
			Stuck stuck = stuckByPool.get(pool.name());
			if (stuck.any() && (!stuckWorst.any() || stuck.worseThan(stuckWorst))) {
				stuckName = name(pool);
				stuckWorst = stuck;
			}
		}
		if (callersStuck.any() && (!stuckWorst.any() || callersStuck.worseThan(stuckWorst))) {
			stuckName = ThreadsTexts.get("health.callers");
			stuckWorst = callersStuck;
		}
		String verdictText;
		String causeText = "";
		String headline = "sv-threads-headline-" + worst.name().toLowerCase(Locale.ROOT);
		if (pools.isEmpty()) {
			verdictText = "";
		} else if (!pressed.isEmpty()) {
			verdictText = sentence(pressed.get(0), busy(history, pressed.get(0)))
					+ (pressed.size() > 1 ? " " + ThreadsTexts.count("health.more", pressed.size() - 1) : "");
			causeText = detail(pressed.get(0), busy(history, pressed.get(0)), stuckByPool.get(pressed.get(0).name()));
		} else if (stuckWorst.any()) {
			// Every pool is fluid on average, yet threads are stuck: a pool's average hides them.
			verdictText = stuckSentence(stuckName, stuckWorst);
			headline = stuckWorst.critical() ? "sv-threads-headline-saturated" : "sv-threads-headline-under_strain";
		} else {
			verdictText = ThreadsTexts.get(pools.size() == 1 ? "health.fluid.one" : "health.fluid.all");
		}
		this.verdict.setText(verdictText);
		this.verdict.getStyleClass().setAll("label", "sv-threads-health-verdict", headline);
		this.cause.setText(causeText);
		this.cause.setVisible(!causeText.isEmpty());
		this.cause.setManaged(!causeText.isEmpty());
		this.callout.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-callout-"));
		this.callout.getStyleClass().add("sv-threads-callout-" + headline.substring("sv-threads-headline-".length()));
		this.callout.setVisible(!pools.isEmpty());
		this.callout.setManaged(!pools.isEmpty());

		List<Node> nodes = new ArrayList<>();
		for (ThreadsSnapshot.Pool pool : pools) {
			Line line = this.byPool.get(pool.name());
			line.pool = pool;
			line.name.setText(pool.title());
			line.badge.setText(ThreadsFormat.label(pool.contention()));
			line.badge.getStyleClass().setAll("label", "sv-threads-contention",
					"sv-threads-contention-" + pool.contention().name().toLowerCase(Locale.ROOT));
			double busy = busy(history, pool);
			line.occupancy.setText(ThreadsFormat.percent(busy));
			line.meterFill.resize(Math.max(0, Math.min(1, busy)) * METER_WIDTH, 6);
			line.meterFill.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sv-threads-meter-fill-"));
			line.meterFill.getStyleClass().add("sv-threads-meter-fill-" + pool.contention().name().toLowerCase(Locale.ROOT));
			line.root.getStyleClass().remove("sv-threads-health-row-last");
			if (pool == pools.get(pools.size() - 1) && !showCallers) {
				line.root.getStyleClass().add("sv-threads-health-row-last");
			}
			String detail = detail(pool, busy, stuckByPool.get(pool.name()));
			// A space rather than nothing: an empty label would lose its line's height.
			line.detail.setText(detail.isEmpty() ? " " : detail);
			line.detailTip.setText(detail);
			if (detail.isEmpty()) {
				Tooltip.uninstall(line.detail, line.detailTip);
			} else {
				Tooltip.install(line.detail, line.detailTip);
			}
			boolean tunable = pool.tuning() != null && pool.contention() != Contention.FLUID;
			line.tune.setVisible(tunable);
			line.tune.setManaged(tunable);
			nodes.add(line.root);
			if (this.tuning != null && this.tuning.shows(pool.name())) {
				this.tuning.update(pool);
			}
		}
		if (showCallers) {
			nodes.add(this.callersRow);
		}
		this.byPool.keySet().retainAll(pools.stream().map(ThreadsSnapshot.Pool::name).toList());
		if (!this.lines.getChildren().equals(nodes)) {
			this.lines.getChildren().setAll(nodes);
		}
		this.lines.setVisible(!nodes.isEmpty());
		this.lines.setManaged(!nodes.isEmpty());
	}

	private void openTuning(final Node anchor, final ThreadsSnapshot.Pool pool) {
		if (pool == null || pool.tuning() == null) {
			return;
		}
		if (this.tuning == null) {
			this.tuning = new PoolTuningPopup(new PoolTuningPopup.Tuner() {
				@Override
				public void setWorkers(final WorkerPoolKind kind, final int count) {
					PoolHealth.this.owner.tunePoolWorkers(kind, count);
				}

				@Override
				public void setQueueCapacity(final WorkerPoolKind kind, final int capacity) {
					PoolHealth.this.owner.tunePoolQueueCapacity(kind, capacity);
				}
			});
		}
		this.tuning.show(anchor, pool);
	}

	/** @return how busy its workers were over the last minute, from 0 to 1 */
	private static double busy(final ThreadsHistory history, final ThreadsSnapshot.Pool pool) {
		ThreadsHistory.PoolTrack track = history.pool(pool.name());
		return track == null ? pool.occupancy() : history.occupancy(track, MINUTE);
	}

	/** @return the verdict's sentence on the most pressed pool */
	static String sentence(final ThreadsSnapshot.Pool pool, final double busy) {
		String name = name(pool);
		if (pool.rejectedLastMinute() > 0) {
			return ThreadsTexts.count("health.overwhelmed", pool.rejectedLastMinute(), name);
		}
		if (pool.contention() == Contention.SATURATED) {
			return ThreadsTexts.get("health.saturated", name, ThreadsFormat.percent(busy));
		}
		return ThreadsTexts.get(pool.queuedTasks() > 0 ? "health.strainQueueing" : "health.strain", name,
				ThreadsFormat.percent(busy));
	}

	/** @return the verdict's sentence on the threads stuck while no pool is pressed */
	private static String stuckSentence(final String name, final Stuck stuck) {
		if (stuck.deadlocked() > 0) {
			return ThreadsTexts.count("health.deadlocked", stuck.deadlocked(), name);
		}
		return ThreadsTexts.count("health.stuck", stuck.tasks(), name, ThreadsFormat.duration(stuck.longest()));
	}

	/** @return why a pool is pressed - rejections, stuck threads, calmer now - or nothing */
	static String detail(final ThreadsSnapshot.Pool pool, final double busy) {
		return detail(pool, busy, Stuck.NONE);
	}

	private static String detail(final ThreadsSnapshot.Pool pool, final double busy, final Stuck stuck) {
		List<String> parts = new ArrayList<>();
		if (pool.rejectedLastMinute() > 0) {
			parts.add(ThreadsTexts.get("health.detail.rejected", pool.rejectedLastMinute()));
		}
		if (stuck.deadlocked() > 0) {
			parts.add(ThreadsTexts.count("health.detail.deadlocked", stuck.deadlocked()));
		}
		if (stuck.tasks() > 0) {
			parts.add(ThreadsTexts.count("health.detail.stuck", stuck.tasks(), ThreadsFormat.duration(stuck.longest())));
		}
		if (pool.calmerNow()) {
			parts.add(ThreadsTexts.get("health.detail.calmer"));
		}
		return String.join(" · ", parts);
	}

	private static String name(final ThreadsSnapshot.Pool pool) {
		return switch (pool.kind()) {
			case EVENTS -> ThreadsTexts.get("health.pool.events");
			case GENERAL -> ThreadsTexts.get("health.pool.general");
			case DEDICATED -> ThreadsTexts.get("health.pool.dedicated", pool.name());
		};
	}
}
