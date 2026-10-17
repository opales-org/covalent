/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.opales.covalent.container.workers.WorkerPoolKind;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The share of busy workers over the window, one line per kind of pool. Hovering shows the
 * values at that moment.
 */
final class OccupancyChart extends Region {

	/** The chart's least height: it grows with the room its card is given. */
	private static final double HEIGHT = 220;

	private static final double LEFT = 40;

	private static final double BOTTOM = 22;

	private static final double TOP = 8;

	private static final Font AXIS_FONT = Font.font("JetBrains Mono", 10);

	private static final Font READOUT_FONT = Font.font(12);

	private final Canvas canvas = new Canvas();

	private double pointerX = Double.NaN;

	private Runnable redraw = () -> {
	};

	OccupancyChart() {
		this.getChildren().add(this.canvas);
		this.setMinHeight(HEIGHT);
		this.setPrefHeight(HEIGHT);
		this.setMaxHeight(Double.MAX_VALUE);
		this.setMinWidth(0);
		this.setOnMouseMoved(event -> {
			this.pointerX = event.getX();
			this.redraw.run();
		});
		this.setOnMouseExited(event -> {
			this.pointerX = Double.NaN;
			this.redraw.run();
		});
		this.widthProperty().addListener(observable -> this.redraw.run());
		this.heightProperty().addListener(observable -> this.redraw.run());
	}

	void setRedraw(final Runnable redraw) {
		this.redraw = redraw;
	}

	@Override
	protected void layoutChildren() {
		this.canvas.setWidth(Math.max(0, this.getWidth()));
		this.canvas.setHeight(Math.max(HEIGHT, this.getHeight()));
	}

	/** The occupancy of each kind of pool at one sample, or {@code NaN} when it had none. */
	static Map<WorkerPoolKind, Double> occupancyByKind(final ThreadsHistory history, final long sample) {
		Map<WorkerPoolKind, double[]> sums = new EnumMap<>(WorkerPoolKind.class);
		int slot = (int) (sample % ThreadsHistory.CAPACITY);
		for (ThreadsHistory.PoolTrack pool : history.pools().values()) {
			if (pool.lastSample < sample || pool.kind == null) {
				continue;
			}
			int workers = pool.workers[slot];
			double[] sum = sums.computeIfAbsent(pool.kind, ignored -> new double[2]);
			sum[0] += pool.occupancy[slot] * workers;
			sum[1] += workers;
		}
		Map<WorkerPoolKind, Double> result = new EnumMap<>(WorkerPoolKind.class);
		for (Map.Entry<WorkerPoolKind, double[]> entry : sums.entrySet()) {
			result.put(entry.getKey(), entry.getValue()[1] == 0 ? Double.NaN : entry.getValue()[0] / entry.getValue()[1]);
		}
		return result;
	}

	void draw(final ThreadsHistory history, final long windowEnd, final int windowSize, final String rangeLabel,
			final Palette palette) {
		double width = this.canvas.getWidth();
		double height = this.canvas.getHeight();
		GraphicsContext graphics = this.canvas.getGraphicsContext2D();
		graphics.clearRect(0, 0, width, height);
		if (width <= LEFT + 20) {
			return;
		}
		double plotWidth = width - LEFT - 8;
		double plotHeight = height - BOTTOM - TOP;
		graphics.setFont(AXIS_FONT);
		graphics.setTextBaseline(VPos.CENTER);
		for (int step = 0; step <= 4; step++) {
			double y = TOP + plotHeight * (1 - step / 4.0);
			graphics.setStroke(palette.color("grid"));
			graphics.setLineWidth(1);
			graphics.strokeLine(LEFT, Math.round(y) + 0.5, width - 8, Math.round(y) + 0.5);
			graphics.setFill(palette.color("axis"));
			graphics.setTextAlign(TextAlignment.RIGHT);
			graphics.fillText(step * 25 + "%", LEFT - 6, y);
		}
		graphics.setTextAlign(TextAlignment.LEFT);
		graphics.setTextBaseline(VPos.TOP);
		graphics.fillText("-" + rangeLabel, LEFT, height - BOTTOM + 6);
		graphics.setTextAlign(TextAlignment.RIGHT);
		graphics.fillText(ThreadsTexts.get("now"), width - 8, height - BOTTOM + 6);

		long first = windowEnd - windowSize + 1;
		long start = Math.max(first, history.oldest());
		double step = plotWidth / Math.max(1, windowSize - 1);
		Map<WorkerPoolKind, List<double[]>> lines = new EnumMap<>(WorkerPoolKind.class);
		for (long sample = start; sample <= windowEnd && history.count() > 0; sample++) {
			double x = LEFT + (sample - first) * step;
			for (Map.Entry<WorkerPoolKind, Double> entry : occupancyByKind(history, sample).entrySet()) {
				if (!entry.getValue().isNaN()) {
					lines.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
							.add(new double[] { x, TOP + plotHeight * (1 - entry.getValue()) });
				}
			}
		}
		for (Map.Entry<WorkerPoolKind, List<double[]>> line : lines.entrySet()) {
			List<double[]> points = line.getValue();
			if (points.size() < 2) {
				continue;
			}
			Color color = palette.color(ThreadsFormat.kindStyle(line.getKey()));
			graphics.setFill(color.deriveColor(0, 1, 1, 0.08));
			graphics.beginPath();
			graphics.moveTo(points.get(0)[0], TOP + plotHeight);
			for (double[] point : points) {
				graphics.lineTo(point[0], point[1]);
			}
			graphics.lineTo(points.get(points.size() - 1)[0], TOP + plotHeight);
			graphics.closePath();
			graphics.fill();
			graphics.setStroke(color);
			graphics.setLineWidth(1.8);
			graphics.beginPath();
			graphics.moveTo(points.get(0)[0], points.get(0)[1]);
			for (double[] point : points) {
				graphics.lineTo(point[0], point[1]);
			}
			graphics.stroke();
		}
		this.drawStuck(graphics, history, windowEnd, first, step, plotHeight, palette);
		if (!Double.isNaN(this.pointerX) && history.count() > 0) {
			long sample = first + Math.round((this.pointerX - LEFT) / step);
			sample = Math.max(start, Math.min(windowEnd, sample));
			this.drawReadout(graphics, history, sample, LEFT + (sample - first) * step, width, plotHeight, palette);
		}
	}

	/**
	 * Marks when a thread got stuck: a dashed line and a triangle where it started, a band over
	 * its duration - open to the right while it lasts - and a bar along the top. The color is its
	 * state's, red once serious: it stays on the chart after it is over, unlike a log line.
	 */
	private void drawStuck(final GraphicsContext graphics, final ThreadsHistory history, final long windowEnd,
			final long first, final double step, final double plotHeight, final Palette palette) {
		long last = history.count() - 1;
		for (ThreadsHistory.Stuck episode : history.stuckEpisodes()) {
			long end = episode.endSample < 0 ? last : episode.endSample;
			if (episode.startSample > windowEnd || end < first) {
				continue;
			}
			Color color = palette.color(episode.color());
			double x0 = LEFT + (Math.max(episode.startSample, first) - first) * step;
			double x1 = LEFT + (Math.min(end, windowEnd) - first) * step;
			graphics.setFill(color.deriveColor(0, 1, 1, 0.10));
			graphics.fillRect(x0, TOP, Math.max(2, x1 - x0), plotHeight);
			graphics.setFill(color);
			graphics.fillRect(x0, TOP, Math.max(3, x1 - x0), 3);
			if (episode.startSample >= first) {
				graphics.setStroke(color);
				graphics.setLineWidth(1.2);
				graphics.setLineDashes(4, 3);
				graphics.strokeLine(Math.round(x0) + 0.5, TOP, Math.round(x0) + 0.5, TOP + plotHeight);
				graphics.setLineDashes((double[]) null);
				graphics.fillPolygon(new double[] { x0 - 4, x0 + 4, x0 }, new double[] { TOP, TOP, TOP + 7 }, 3);
			}
		}
	}

	private void drawReadout(final GraphicsContext graphics, final ThreadsHistory history, final long sample,
			final double x, final double width, final double plotHeight, final Palette palette) {
		graphics.setStroke(palette.color("cursor"));
		graphics.setLineWidth(1);
		graphics.strokeLine(Math.round(x) + 0.5, TOP, Math.round(x) + 0.5, TOP + plotHeight);
		Map<WorkerPoolKind, Double> values = occupancyByKind(history, sample);
		List<String> lines = new ArrayList<>();
		List<Color> colors = new ArrayList<>();
		lines.add(ThreadsFormat.time(history.timeOf(sample)));
		colors.add(null);
		for (Map.Entry<WorkerPoolKind, Double> entry : values.entrySet()) {
			if (!entry.getValue().isNaN()) {
				lines.add(ThreadsFormat.kindLabel(entry.getKey()) + "  " + ThreadsFormat.percent(entry.getValue()));
				colors.add(palette.color(ThreadsFormat.kindStyle(entry.getKey())));
			}
		}
		boolean stuck = false;
		for (ThreadsHistory.Stuck episode : history.stuckEpisodes()) {
			long end = episode.endSample < 0 ? history.count() - 1 : episode.endSample;
			if (episode.startSample <= sample && sample <= end && lines.size() < values.size() + 4) {
				lines.add(ThreadsTexts.get("chart.stuck", episode.threadName,
						ThreadsFormat.time(history.timeOf(episode.startSample))));
				colors.add(palette.color(episode.color()));
				stuck = true;
			}
		}
		double boxWidth = stuck ? 320 : 160;
		double boxHeight = 10 + lines.size() * 18;
		double boxX = x + 12 + boxWidth > width ? x - 12 - boxWidth : x + 12;
		graphics.setFill(palette.color("readout"));
		graphics.fillRoundRect(boxX, TOP + 4, boxWidth, boxHeight, 8, 8);
		graphics.setStroke(palette.color("readout-border"));
		graphics.strokeRoundRect(boxX + 0.5, TOP + 4.5, boxWidth, boxHeight, 8, 8);
		graphics.setFont(READOUT_FONT);
		graphics.setTextAlign(TextAlignment.LEFT);
		graphics.setTextBaseline(VPos.CENTER);
		for (int line = 0; line < lines.size(); line++) {
			double y = TOP + 4 + 14 + line * 18;
			double textX = boxX + 10;
			if (colors.get(line) != null) {
				graphics.setFill(colors.get(line));
				graphics.fillRect(textX, y - 1.5, 12, 3);
				textX += 18;
			}
			graphics.setFill(colors.get(line) == null ? palette.color("axis") : palette.color("ink"));
			graphics.fillText(lines.get(line), textX, y);
		}
	}
}
