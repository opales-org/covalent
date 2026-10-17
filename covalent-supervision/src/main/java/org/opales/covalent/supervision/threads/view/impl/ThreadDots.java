/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.threads.model.ThreadActivity;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * A strip of dots, one per thread, the worst first: how a group of threads is doing at a glance.
 * It shows as many as its line holds, never wrapping, and counts the others: they are less
 * severe, so they are taken for fine. Its dots and their tooltips are updated in place.
 */
final class ThreadDots extends HBox {

	/** A dot's width, as the stylesheet sets it. */
	private static final double DOT = 10;

	/** The space between two dots. */
	private static final double GAP = 5;

	/** The dots' room the count of the others takes, such as {@code +12}. */
	private static final int MORE_SLOTS = 2;

	/** The share of its time from which a thread's green dot is full. */
	private static final double FULL_SHARE = 0.8;

	/** How strong the palest green dot is. */
	private static final double MIN_INTENSITY = 0.35;

	/** How many strips have a blinking dot: the shared blinking runs only then. */
	private static int blinking;

	private static final DoubleProperty BLINK_OPACITY = new SimpleDoubleProperty(1);

	private static final Timeline BLINK = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(BLINK_OPACITY, 1)),
			new KeyFrame(Duration.millis(550), new KeyValue(BLINK_OPACITY, 0.2)));

	static {
		BLINK.setAutoReverse(true);
		BLINK.setCycleCount(Animation.INDEFINITE);
	}

	private final List<Region> dots = new ArrayList<>();

	private final List<Tooltip> tooltips = new ArrayList<>();

	private final Label more = new Label();

	private boolean hasBlinking;

	/** The threads last shown, the worst first. */
	private List<ThreadDot> sorted = List.of();

	ThreadDots() {
		super(GAP);
		this.setAlignment(Pos.CENTER_LEFT);
		this.more.getStyleClass().add("sv-cockpit-mono-muted");
		this.more.setMinWidth(Region.USE_PREF_SIZE);
		// Narrower or wider, it shows as many dots as it now holds.
		this.widthProperty().addListener((observable, before, width) -> this.show());
		// A strip leaving the screen stops asking for the shared blinking; its next update asks again.
		this.sceneProperty().addListener((observable, before, scene) -> {
			if (scene == null) {
				this.setBlinking(false);
			}
		});
	}

	/** As wide as all its dots: it gives way to its neighbors only when they need the room. */
	@Override
	protected double computePrefWidth(final double height) {
		int count = this.sorted.size();
		return count == 0 ? 0 : count * (DOT + GAP) - GAP + this.snappedLeftInset() + this.snappedRightInset();
	}

	@Override
	protected double computeMinWidth(final double height) {
		return this.sorted.isEmpty() ? 0 : (1 + MORE_SLOTS) * (DOT + GAP);
	}

	/**
	 * Shows these threads.
	 *
	 * @param workers    the threads
	 * @param deadlocked the names of the threads in a deadlock
	 * @param threshold  the long task threshold, in nanoseconds
	 * @param recent     each thread's share of the last seconds spent in tasks, from 0 to 1
	 * @return how they read, the worst first
	 */
	List<ThreadDot> update(final List<ThreadsSnapshot.Worker> workers, final Set<String> deadlocked,
			final long threshold, final ToDoubleFunction<ThreadsSnapshot.Worker> recent) {
		List<ThreadDot> sorted = new ArrayList<>(workers.size());
		for (ThreadsSnapshot.Worker worker : workers) {
			sorted.add(ThreadDot.of(worker, deadlocked.contains(worker.name()), threshold,
					recent.applyAsDouble(worker)));
		}
		sorted.sort(Comparator.comparingInt(ThreadDot::severity).reversed()
				.thenComparing(Comparator.comparingLong(ThreadDot::taskNanos).reversed())
				.thenComparing(dot -> dot.worker().name()));
		this.sorted = sorted;
		this.requestLayout();
		this.show();
		this.setVisible(!sorted.isEmpty());
		this.setManaged(!sorted.isEmpty());
		return sorted;
	}

	/** Shows as many of the threads as the line holds, the worst first, and counts the others. */
	private void show() {
		List<ThreadDot> sorted = this.sorted;
		double width = this.getWidth() - this.snappedLeftInset() - this.snappedRightInset();
		int fits = width <= 0 ? sorted.size() : (int) Math.floor((width + GAP) / (DOT + GAP));
		int shown = sorted.size() <= fits ? sorted.size() : Math.max(1, fits - MORE_SLOTS);
		while (this.dots.size() < shown) {
			Region dot = new Region();
			this.dots.add(dot);
			this.tooltips.add(Tooltips.install(dot, ""));
		}
		boolean anyBlinking = false;
		for (int index = 0; index < this.dots.size(); index++) {
			Region dot = this.dots.get(index);
			if (index >= shown) {
				dot.opacityProperty().unbind();
				dot.setOpacity(1);
				continue;
			}
			ThreadDot state = sorted.get(index);
			dot.getStyleClass().setAll("sv-threads-dot", "sv-threads-dot-" + state.state().style());
			this.tooltips.get(index).setText(tooltip(state));
			if (state.blink()) {
				anyBlinking = true;
				if (!dot.opacityProperty().isBound()) {
					dot.opacityProperty().bind(BLINK_OPACITY);
				}
			} else {
				dot.opacityProperty().unbind();
				dot.setOpacity(intensity(state));
			}
		}
		this.setBlinking(anyBlinking);
		List<Node> children = new ArrayList<>(this.dots.subList(0, shown));
		if (sorted.size() > shown) {
			this.more.setText("+" + (sorted.size() - shown));
			children.add(this.more);
		}
		if (!this.getChildren().equals(children)) {
			this.getChildren().setAll(children);
		}
	}

	/** The shared blinking runs while a strip has a blinking dot. */
	private void setBlinking(final boolean blinks) {
		if (blinks == this.hasBlinking) {
			return;
		}
		this.hasBlinking = blinks;
		blinking += blinks ? 1 : -1;
		if (blinking > 0 && BLINK.getStatus() != Animation.Status.RUNNING) {
			BLINK.play();
		} else if (blinking <= 0) {
			BLINK.stop();
			BLINK_OPACITY.set(1);
		}
	}

	/**
	 * @return how strong a green dot is: pale for a thread a little busy over the last seconds,
	 *         full from {@link #FULL_SHARE} of their time; other dots are always full
	 */
	static double intensity(final ThreadDot dot) {
		if (dot.state() != ThreadDot.State.OK || dot.recent() <= 0) {
			return 1;
		}
		return MIN_INTENSITY + (1 - MIN_INTENSITY) * Math.min(1, dot.recent() / FULL_SHARE);
	}

	/** @return what a dot says of its thread, in words */
	static String tooltip(final ThreadDot dot) {
		ThreadsSnapshot.Worker worker = dot.worker();
		StringBuilder text = new StringBuilder(worker.name()).append('\n');
		String duration = ThreadsFormat.duration(dot.taskNanos());
		if (dot.deadlocked()) {
			text.append(ThreadsTexts.get("dot.deadlock"));
		} else if (worker.activity() == ThreadActivity.BLOCKED) {
			text.append(worker.lockOwnerName() != null
					? ThreadsTexts.get("dot.blockedHeld", duration, worker.lockName(), worker.lockOwnerName())
					: ThreadsTexts.get("dot.blocked", duration, worker.lockName()));
		} else if (worker.activity() == ThreadActivity.RUNS) {
			text.append(ThreadsTexts.get("dot.runs", duration));
		} else if (worker.activity() == ThreadActivity.WAITS) {
			text.append(ThreadsTexts.get("dot.waits", duration));
		} else if (worker.activity() == ThreadActivity.UNTRACKED) {
			text.append(ThreadsTexts.get("dot.untracked"));
		} else {
			text.append(ThreadsTexts.get("dot.idle"));
		}
		if (dot.recent() > 0 && !dot.deadlocked()) {
			text.append('\n').append(ThreadsTexts.get("dot.recent", ThreadsFormat.share(dot.recent())));
		}
		if (worker.taskLabel() != null && worker.activity().isBusy()) {
			text.append('\n').append(worker.taskLabel());
		}
		if (!dot.deadlocked() && dot.stuck()) {
			if (dot.state() == ThreadDot.State.LONG_RUN) {
				text.append('\n').append(ThreadsTexts.get("dot.hint.run"));
			} else if (worker.activity() == ThreadActivity.WAITS) {
				text.append('\n').append(ThreadsTexts.get("dot.hint.wait"));
			} else if (dot.critical()) {
				text.append('\n').append(ThreadsTexts.get("dot.hint.critical"));
			}
		}
		return text.toString();
	}
}
