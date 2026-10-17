/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.function.LongConsumer;

import org.opales.covalent.supervision.threads.model.ThreadActivity;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * One thread's lane: a continuous bar of its activities over the window, on the time axis
 * every lane shares, with its current task's label at the right end and the shared cursor.
 */
final class LaneChart extends Region {

	static final double HEIGHT = 24;

	private static final Font PILL_FONT = Font.font("JetBrains Mono", 10);

	/** Half the width a click picks, in pixels: a thin bar is easy to hit. */
	private static final double PICK_HALF_WIDTH = 3;

	private final Canvas canvas = new Canvas();

	/** The sample under the pointer, or {@code -1} when it leaves. */
	private LongConsumer onHover = sample -> {
	};

	/** A click: the first and last samples under it, a few pixels either side. */
	private MomentClick onClick = (from, to) -> {
	};

	/** What a click on the lane picks. */
	@FunctionalInterface
	interface MomentClick {

		/**
		 * @param from the first sample picked
		 * @param to   the last sample picked
		 */
		void picked(long from, long to);
	}

	private long windowEnd;

	private int windowSize = 1;

	LaneChart() {
		this.getChildren().add(this.canvas);
		this.setMinHeight(HEIGHT);
		this.setPrefHeight(HEIGHT);
		this.setMaxHeight(HEIGHT);
		this.setMinWidth(0);
		this.setOnMouseMoved(event -> this.onHover.accept(this.sampleAt(event.getX())));
		this.setOnMouseExited(event -> this.onHover.accept(-1));
		this.setOnMouseClicked(event -> {
			// The lane picks a moment; the row it sits in, clicked elsewhere, the thread alone.
			event.consume();
			long from = this.sampleAt(event.getX() - PICK_HALF_WIDTH);
			long to = this.sampleAt(event.getX() + PICK_HALF_WIDTH);
			this.onClick.picked(from, Math.max(from, Math.min(to, this.windowEnd)));
		});
	}

	void setOnHover(final LongConsumer onHover) {
		this.onHover = onHover;
	}

	void setOnClick(final MomentClick onClick) {
		this.onClick = onClick;
	}

	@Override
	protected void layoutChildren() {
		this.canvas.setWidth(Math.max(0, this.getWidth()));
		this.canvas.setHeight(Math.max(0, this.getHeight()));
	}

	private long sampleAt(final double x) {
		double width = Math.max(1, this.getWidth());
		long first = this.windowEnd - this.windowSize + 1;
		return first + (long) Math.floor(Math.max(0, Math.min(width - 1, x)) / width * this.windowSize);
	}

	/**
	 * @param history    the samples
	 * @param track      the thread's samples
	 * @param windowEnd  the last sample shown, on the right edge
	 * @param windowSize the samples across the lane
	 * @param cursor     the sample under the pointer, {@code -1} if none
	 * @param pill       what the thread works on, shown at the right end, {@code null} if nothing
	 * @param pillEnded  whether that is a task ended a while ago rather than one of now: outlined
	 *                   rather than filled
	 * @param pillDot    the color of the pill's dot
	 * @param palette    the theme's colors
	 * @param momentFrom the first sample of the moment picked on this lane, {@code -1} if none
	 * @param momentTo   the last sample of that moment
	 */
	void draw(final ThreadsHistory history, final ThreadsHistory.Track track, final long windowEnd,
			final int windowSize, final long cursor, final String pill, final boolean pillEnded, final Color pillDot,
			final Palette palette, final long momentFrom, final long momentTo) {
		this.windowEnd = windowEnd;
		this.windowSize = Math.max(1, windowSize);
		double width = this.canvas.getWidth();
		double height = this.canvas.getHeight();
		GraphicsContext graphics = this.canvas.getGraphicsContext2D();
		graphics.clearRect(0, 0, width, height);
		if (width <= 0) {
			return;
		}
		graphics.setFill(palette.color("lane"));
		graphics.fillRoundRect(0, 0, width, height, 6, 6);
		long first = windowEnd - this.windowSize + 1;
		long start = Math.max(first, history.oldest());
		double step = width / this.windowSize;
		ThreadActivity runActivity = null;
		double runStart = 0;
		for (long sample = start; sample <= windowEnd + 1; sample++) {
			ThreadActivity activity = sample <= windowEnd ? history.activity(track, sample) : null;
			double x = (sample - first) * step;
			if (activity != runActivity || sample > windowEnd) {
				if (runActivity != null) {
					graphics.setFill(color(runActivity, palette));
					graphics.fillRect(runStart, 0, Math.max(1, x - runStart), height);
					if (runActivity == ThreadActivity.UNTRACKED) {
						hatch(graphics, runStart, Math.max(1, x - runStart), height, palette.color("lane"));
					}
				}
				runActivity = activity;
				runStart = x;
			}
		}
		// Short tasks between two samples, on an idle span: a bar rising from the bottom by the
		// share of the interval spent in tasks, at least a thin line so that any activity shows.
		graphics.setFill(palette.color("runs"));
		for (long sample = start; sample <= windowEnd; sample++) {
			ThreadActivity activity = history.activity(track, sample);
			float busy = history.busy(track, sample);
			if (busy > 0 && (activity == ThreadActivity.IDLE || activity == ThreadActivity.UNTRACKED)) {
				double barHeight = Math.max(3, height * busy);
				double x = (sample - first) * step;
				graphics.fillRect(x, height - barHeight, Math.max(1, step), barHeight);
			}
		}
		if (pill != null) {
			this.drawPill(graphics, pill, pillEnded, pillDot, width, height, palette);
		}
		if (momentFrom >= 0 && momentTo >= first && momentFrom <= windowEnd) {
			double from = (Math.max(momentFrom, first) - first) * step;
			double to = (Math.min(momentTo, windowEnd) - first + 1) * step;
			Color ink = palette.color("ink");
			graphics.setFill(Color.color(ink.getRed(), ink.getGreen(), ink.getBlue(), 0.18));
			graphics.fillRect(from, 0, Math.max(2, to - from), height);
			graphics.setStroke(ink);
			graphics.setLineWidth(2);
			graphics.strokeRect(from + 1, 1, Math.max(2, to - from - 2), height - 2);
		}
		if (cursor >= 0 && cursor >= first && cursor <= windowEnd) {
			double x = (cursor - first + 0.5) * step;
			graphics.setStroke(palette.color("cursor"));
			graphics.setLineWidth(1);
			graphics.strokeLine(Math.round(x) + 0.5, 0, Math.round(x) + 0.5, height);
		}
	}

	/** Diagonal stripes over a span: waiting or idle, which cannot be told apart. */
	private static void hatch(final GraphicsContext graphics, final double x, final double width, final double height,
			final Color stripe) {
		graphics.save();
		graphics.beginPath();
		graphics.rect(x, 0, width, height);
		graphics.clip();
		graphics.setStroke(stripe);
		graphics.setLineWidth(2);
		double start = Math.floor(x / 8) * 8 - height;
		for (double stripeX = start; stripeX < x + width; stripeX += 8) {
			graphics.strokeLine(stripeX, height, stripeX + height, 0);
		}
		graphics.restore();
	}

	static Color color(final ThreadActivity activity, final Palette palette) {
		return palette.color(switch (activity) {
			case RUNS -> "runs";
			case WAITS -> "waits";
			case BLOCKED -> "blocked";
			case IDLE -> "idle";
			case UNTRACKED -> "idle";
		});
	}

	/** A neutral pill at the right end of the lane: a dot, then the name, as long as the lane allows. */
	private void drawPill(final GraphicsContext graphics, final String pill, final boolean ended, final Color dotColor,
			final double width, final double height, final Palette palette) {
		Text measure = new Text(pill);
		measure.setFont(PILL_FONT);
		double dot = 7;
		double pillWidth = Math.min(width * 0.6, measure.getLayoutBounds().getWidth() + dot + 22);
		double x = width - pillWidth - 4;
		double pillHeight = height - 8;
		if (ended) {
			graphics.setFill(palette.color("lane"));
			graphics.fillRoundRect(x, 4, pillWidth, pillHeight, pillHeight, pillHeight);
			graphics.setStroke(palette.color("pill-edge"));
			graphics.setLineWidth(1.2);
			graphics.strokeRoundRect(x + 0.6, 4.6, pillWidth - 1.2, pillHeight - 1.2, pillHeight - 1.2, pillHeight - 1.2);
		} else {
			graphics.setFill(palette.color("pill"));
			graphics.fillRoundRect(x, 4, pillWidth, pillHeight, pillHeight, pillHeight);
		}
		graphics.setFill(dotColor);
		graphics.fillOval(x + 9, height / 2 - dot / 2, dot, dot);
		graphics.setFill(palette.color(ended ? "ink" : "pill-ink"));
		graphics.setFont(PILL_FONT);
		graphics.setTextAlign(TextAlignment.LEFT);
		graphics.setTextBaseline(VPos.CENTER);
		graphics.fillText(pill, x + 9 + dot + 6, height / 2, pillWidth - dot - 25);
	}
}
