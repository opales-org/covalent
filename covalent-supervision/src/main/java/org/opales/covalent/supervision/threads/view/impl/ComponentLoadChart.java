/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.Map;
import java.util.function.LongConsumer;

import org.opales.covalent.container.workers.WorkerPoolKind;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * One component's load: for each kind of pool it worked in, the share of that kind's workers
 * that worked for it at each sample of the window, from 0 to 100 %, one line per kind overlaid
 * on the time axis the lanes share, in the occupancy chart's colors.
 */
final class ComponentLoadChart extends Region {

	static final double HEIGHT = 30;

	private final Canvas canvas = new Canvas();

	/** The sample under the pointer, or {@code -1} when it leaves. */
	private LongConsumer onHover = sample -> {
	};

	private long windowEnd;

	private int windowSize = 1;

	ComponentLoadChart() {
		this.getChildren().add(this.canvas);
		this.setMinHeight(HEIGHT);
		this.setPrefHeight(HEIGHT);
		this.setMaxHeight(HEIGHT);
		this.setMinWidth(0);
		this.setOnMouseMoved(event -> this.onHover.accept(this.sampleAt(event.getX())));
		this.setOnMouseExited(event -> this.onHover.accept(-1));
	}

	void setOnHover(final LongConsumer onHover) {
		this.onHover = onHover;
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
	 * @param shares     per kind of pool, the share of its workers at each sample of the window,
	 *                   the first one on the left edge
	 * @param windowEnd  the last sample shown, on the right edge
	 * @param windowSize the samples across the chart
	 * @param cursor     the sample under the pointer, {@code -1} if none
	 * @param palette    the theme's colors
	 */
	void draw(final Map<WorkerPoolKind, float[]> shares, final long windowEnd, final int windowSize, final long cursor,
			final Palette palette) {
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
		double top = 3;
		double usable = height - top;
		// The dashed line: all of a pool's workers.
		graphics.setStroke(palette.color("grid"));
		graphics.setLineWidth(1);
		graphics.setLineDashes(3, 3);
		graphics.strokeLine(0, top + 0.5, width, top + 0.5);
		graphics.setLineDashes((double[]) null);
		int columns = (int) Math.ceil(width);
		for (WorkerPoolKind kind : WorkerPoolKind.values()) {
			float[] share = shares.get(kind);
			if (share == null) {
				continue;
			}
			double[] values = this.columns(share, width, columns);
			Color color = palette.color(ThreadsFormat.kindStyle(kind));
			graphics.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.18));
			for (int column = 0; column < columns; column++) {
				double barHeight = Math.min(usable, values[column] * usable);
				if (barHeight > 0) {
					graphics.fillRect(column, height - barHeight, 1, barHeight);
				}
			}
			graphics.setStroke(color);
			graphics.setLineWidth(1.5);
			graphics.beginPath();
			for (int column = 0; column < columns; column++) {
				double y = height - Math.min(usable, values[column] * usable) - 0.75;
				if (column == 0) {
					graphics.moveTo(0, y);
				} else {
					graphics.lineTo(column, y);
				}
			}
			graphics.stroke();
		}
		long first = windowEnd - this.windowSize + 1;
		if (cursor >= 0 && cursor >= first && cursor <= windowEnd) {
			double x = (cursor - first + 0.5) * width / this.windowSize;
			graphics.setStroke(palette.color("cursor"));
			graphics.setLineWidth(1);
			graphics.strokeLine(Math.round(x) + 0.5, 0, Math.round(x) + 0.5, height);
		}
	}

	/** One column per pixel: the mean of its samples, so that a long window stays readable. */
	private double[] columns(final float[] share, final double width, final int columns) {
		double[] values = new double[columns];
		for (int column = 0; column < columns; column++) {
			int from = (int) Math.floor((double) column / width * this.windowSize);
			int to = Math.max(from + 1, (int) Math.floor((double) (column + 1) / width * this.windowSize));
			double sum = 0;
			int counted = 0;
			for (int index = from; index < to && index < share.length; index++) {
				sum += share[index];
				counted++;
			}
			values[column] = counted == 0 ? 0 : sum / counted;
		}
		return values;
	}
}
