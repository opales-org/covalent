/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/**
 * Tunes a pool live, from its header: its workers one at a time, its queue capacity by halves or
 * doubles, within {@link ThreadsSnapshot.Tuning}'s bounds, each with its value at connection to
 * go back to. What it shows comes from the samples: a change shows once the pool has taken it.
 */
final class PoolTuningPopup {

	/** What the popup's buttons ask for. */
	interface Tuner {

		void setWorkers(WorkerPoolKind kind, int count);

		void setQueueCapacity(WorkerPoolKind kind, int capacity);
	}

	private final Tuner tuner;

	private final Popup popup = new Popup();

	private final Label workersValue = value();
	private final Label workersOrigin = muted();
	private final Hyperlink workersReset = new Hyperlink(ThreadsTexts.get("tune.reset"));
	private final Button fewerWorkers = stepButton("−", ThreadsTexts.get("tune.fewer"));
	private final Button moreWorkers = stepButton("+", ThreadsTexts.get("tune.more"));

	private final Label queueValue = value();
	private final Label queueOrigin = muted();
	private final Hyperlink queueReset = new Hyperlink(ThreadsTexts.get("tune.reset"));
	private final Button smallerQueue = stepButton("÷2", ThreadsTexts.get("tune.halve"));
	private final Button largerQueue = stepButton("×2", ThreadsTexts.get("tune.double"));

	private final Label footer = muted();

	private ThreadsSnapshot.Pool pool;

	/** The values last asked for, so that quick clicks add up before the samples show them. */
	private int workers;
	private int capacity;

	PoolTuningPopup(final Tuner tuner) {
		this.tuner = tuner;
		Label eyebrow = new Label(ThreadsTexts.get("tune.title"));
		eyebrow.getStyleClass().add("sv-eyebrow");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(eyebrow, spacer, muted(ThreadsTexts.get("tune.live")));
		header.setAlignment(Pos.BASELINE_LEFT);

		this.fewerWorkers.setOnAction(event -> this.askWorkers(this.workers - 1));
		this.moreWorkers.setOnAction(event -> this.askWorkers(this.workers + 1));
		this.workersReset.setOnAction(event -> this.askWorkers(this.pool.tuning().workersAtConnection()));
		this.smallerQueue.setOnAction(event -> this.askCapacity(this.capacity / 2));
		this.largerQueue.setOnAction(event -> this.askCapacity(this.capacity * 2));
		this.queueReset.setOnAction(event -> this.askCapacity(this.pool.tuning().queueCapacityAtConnection()));
		this.footer.setWrapText(true);
		this.footer.getStyleClass().add("sv-threads-tune-footer");

		VBox content = new VBox(14, header,
				row(ThreadsTexts.get("tune.workers"), this.workersOrigin, this.workersReset, this.fewerWorkers, this.workersValue,
						this.moreWorkers),
				row(ThreadsTexts.get("tune.queueCapacity"), this.queueOrigin, this.queueReset, this.smallerQueue, this.queueValue,
						this.largerQueue),
				this.footer);
		content.getStyleClass().addAll("root", "sv-threads-tune");
		content.setPrefWidth(330);
		this.popup.getContent().add(content);
		this.popup.setAutoHide(true);
		this.popup.setHideOnEscape(true);
	}

	/**
	 * Opens under a chip of the pool's header.
	 *
	 * @param anchor the chip
	 * @param pool   the pool, as last sampled
	 */
	void show(final Node anchor, final ThreadsSnapshot.Pool pool) {
		this.pool = pool;
		this.workers = pool.targetWorkers();
		this.capacity = pool.queueCapacity();
		this.render();
		this.popup.getScene().getStylesheets().setAll(anchor.getScene().getStylesheets());
		Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
		this.popup.show(anchor, bounds.getMinX(), bounds.getMaxY() + 6);
	}

	/** @param pool the pool, as just sampled: what the popup shows follows it while open */
	void update(final ThreadsSnapshot.Pool pool) {
		if (this.popup.isShowing() && pool.tuning() != null) {
			this.pool = pool;
			this.workers = pool.targetWorkers();
			this.capacity = pool.queueCapacity();
			this.render();
		}
	}

	/** @return whether it is open on that pool */
	boolean shows(final String poolName) {
		return this.popup.isShowing() && this.pool != null && this.pool.name().equals(poolName);
	}

	void hide() {
		this.popup.hide();
	}

	private void askWorkers(final int count) {
		this.workers = Math.clamp(count, ThreadsSnapshot.Tuning.MIN_WORKERS, ThreadsSnapshot.Tuning.MAX_WORKERS);
		this.tuner.setWorkers(this.pool.kind(), this.workers);
		this.render();
	}

	private void askCapacity(final int capacity) {
		this.capacity = Math.clamp(capacity, ThreadsSnapshot.Tuning.MIN_QUEUE_CAPACITY,
				ThreadsSnapshot.Tuning.MAX_QUEUE_CAPACITY);
		this.tuner.setQueueCapacity(this.pool.kind(), this.capacity);
		this.render();
	}

	private void render() {
		ThreadsSnapshot.Tuning tuning = this.pool.tuning();
		this.workersValue.setText(Integer.toString(this.workers));
		this.workersOrigin.setText(ThreadsTexts.get("tune.atConnection", tuning.workersAtConnection()));
		boolean workersChanged = this.workers != tuning.workersAtConnection();
		this.workersReset.setVisible(workersChanged);
		ThreadsViewImpl.setStyle(this.workersValue, "sv-threads-tune-changed", workersChanged);
		this.fewerWorkers.setDisable(this.workers <= ThreadsSnapshot.Tuning.MIN_WORKERS);
		this.moreWorkers.setDisable(this.workers >= ThreadsSnapshot.Tuning.MAX_WORKERS);

		this.queueValue.setText(Integer.toString(this.capacity));
		this.queueOrigin.setText(ThreadsTexts.get("tune.atConnection", tuning.queueCapacityAtConnection()));
		boolean queueChanged = this.capacity != tuning.queueCapacityAtConnection();
		this.queueReset.setVisible(queueChanged);
		ThreadsViewImpl.setStyle(this.queueValue, "sv-threads-tune-changed", queueChanged);
		this.smallerQueue.setDisable(this.capacity / 2 < ThreadsSnapshot.Tuning.MIN_QUEUE_CAPACITY);
		this.largerQueue.setDisable(this.capacity * 2 > ThreadsSnapshot.Tuning.MAX_QUEUE_CAPACITY);

		this.footer.setText(ThreadsTexts.get(this.pool.kind() == WorkerPoolKind.GENERAL ? "tune.footer.general"
				: "tune.footer"));
	}

	private static HBox row(final String name, final Label origin, final Hyperlink reset, final Button less,
			final Label value, final Button more) {
		Label title = new Label(name);
		title.getStyleClass().add("sv-threads-tune-name");
		reset.getStyleClass().add("sv-threads-tune-reset");
		HBox originLine = new HBox(4, origin, reset);
		originLine.setAlignment(Pos.CENTER_LEFT);
		VBox labels = new VBox(1, title, originLine);
		HBox.setHgrow(labels, Priority.ALWAYS);
		HBox stepper = new HBox(less, value, more);
		stepper.setAlignment(Pos.CENTER);
		stepper.getStyleClass().add("sv-threads-tune-stepper");
		HBox row = new HBox(12, labels, stepper);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private static Label value() {
		Label label = new Label();
		label.getStyleClass().add("sv-threads-tune-value");
		label.setMinWidth(56);
		label.setAlignment(Pos.CENTER);
		return label;
	}

	private static Label muted() {
		Label label = new Label();
		label.getStyleClass().add("sv-threads-tune-muted");
		return label;
	}

	private static Label muted(final String text) {
		Label label = muted();
		label.setText(text);
		return label;
	}

	private static Button stepButton(final String text, final String description) {
		Button button = new Button(text);
		button.getStyleClass().add("sv-threads-tune-step");
		button.setAccessibleText(description);
		button.setFocusTraversable(false);
		return button;
	}
}
