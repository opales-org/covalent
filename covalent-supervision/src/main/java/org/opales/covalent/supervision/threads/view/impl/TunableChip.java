/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import org.opales.covalent.supervision.componentview.internal.Tooltips;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * A pool header's figure that can open the pool's tuning: its text, then a tuning icon while the
 * pool can be tuned, and a dot in front once tuned away from its value at connection. The queue's
 * chip also shows how full the queue is, as a thin bar along its bottom.
 */
final class TunableChip extends HBox {

	private final Label text = new Label();

	private final Region dot = new Region();

	private final Group icon = tuneIcon();

	/** How full, as a bar along the bottom; hidden until {@link #setFill(double)}. */
	private final Region fill = new Region();

	private double fillRatio = -1;

	private final Tooltip tip;

	private boolean tipInstalled = true;

	private boolean tunable;

	private Runnable onOpen = () -> {
	};

	TunableChip() {
		super(5);
		this.getStyleClass().add("sv-threads-chip");
		this.setAlignment(Pos.CENTER_LEFT);
		this.setMinWidth(Region.USE_PREF_SIZE);
		this.text.getStyleClass().add("sv-threads-chip-text");
		this.dot.getStyleClass().add("sv-threads-chip-dot");
		this.fill.getStyleClass().add("sv-threads-chip-fill");
		this.fill.setManaged(false);
		this.fill.setVisible(false);
		this.fill.setMouseTransparent(true);
		this.getChildren().addAll(this.dot, this.text, this.icon, this.fill);
		// The bar follows the chip's rounded ends.
		Rectangle clip = new Rectangle();
		clip.widthProperty().bind(this.widthProperty());
		clip.heightProperty().bind(this.heightProperty());
		clip.arcWidthProperty().bind(this.heightProperty());
		clip.arcHeightProperty().bind(this.heightProperty());
		this.setClip(clip);
		this.tip = Tooltips.install(this, "");
		this.setOnMouseClicked(event -> {
			if (this.tunable) {
				this.onOpen.run();
			}
		});
		this.show(false, false, null);
	}

	void setText(final String text) {
		this.text.setText(text);
	}

	/**
	 * @param ratio how full, from 0 to 1: a bar from the first waiting task on, amber from half full,
	 *              red when full
	 */
	void setFill(final double ratio) {
		this.fillRatio = Math.max(0, Math.min(1, ratio));
		this.fill.setVisible(this.fillRatio > 0);
		ThreadsViewImpl.setStyle(this.fill, "sv-threads-chip-fill-half", this.fillRatio >= 0.5 && this.fillRatio < 1);
		ThreadsViewImpl.setStyle(this.fill, "sv-threads-chip-fill-full", this.fillRatio >= 1);
		ThreadsViewImpl.setStyle(this, "sv-threads-chip-full", this.fillRatio >= 1);
		this.requestLayout();
	}

	@Override
	protected void layoutChildren() {
		super.layoutChildren();
		if (this.fillRatio > 0) {
			double height = 3;
			// A few waiting tasks in a large queue still show.
			double width = Math.max(height * 2, this.fillRatio * this.getWidth());
			this.fill.resizeRelocate(0, this.getHeight() - height, width, height);
		}
	}

	void setOnOpen(final Runnable onOpen) {
		this.onOpen = onOpen;
	}

	/**
	 * @param tunable whether a click opens the pool's tuning
	 * @param changed whether it was tuned away from its value at connection
	 * @param tooltip what its tooltip says, {@code null} for none
	 */
	void show(final boolean tunable, final boolean changed, final String tooltip) {
		this.tunable = tunable;
		this.icon.setVisible(tunable);
		this.icon.setManaged(tunable);
		this.dot.setVisible(changed);
		this.dot.setManaged(changed);
		ThreadsViewImpl.setStyle(this, "sv-threads-chip-tunable", tunable);
		ThreadsViewImpl.setStyle(this, "sv-threads-chip-changed", changed);
		if (tooltip != null) {
			this.tip.setText(tooltip);
		}
		if (tooltip != null && !this.tipInstalled) {
			Tooltip.install(this, this.tip);
		} else if (tooltip == null && this.tipInstalled) {
			Tooltip.uninstall(this, this.tip);
		}
		this.tipInstalled = tooltip != null;
	}

	/** Two sliders: the usual sign of settings to adjust. */
	private static Group tuneIcon() {
		Group icon = new Group();
		for (double y : new double[] { 2.5, 8.5 }) {
			Line line = new Line(0, y, 11, y);
			line.getStyleClass().add("sv-threads-chip-icon-line");
			icon.getChildren().add(line);
		}
		Circle upper = new Circle(4, 2.5, 1.9);
		Circle lower = new Circle(7, 8.5, 1.9);
		upper.getStyleClass().add("sv-threads-chip-icon-knob");
		lower.getStyleClass().add("sv-threads-chip-icon-knob");
		icon.getChildren().addAll(upper, lower);
		return icon;
	}
}
