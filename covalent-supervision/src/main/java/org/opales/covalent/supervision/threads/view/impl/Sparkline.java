/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** A small line of values, with the area under it and a dot at its end. */
final class Sparkline extends Canvas {

	Sparkline(final double width, final double height) {
		super(width, height);
	}

	/**
	 * @param values the values, oldest first; {@code NaN} ones are skipped
	 * @param max    the value at the top, or {@code 0} to use the largest one
	 * @param color  the line's color
	 */
	void draw(final double[] values, final double max, final Color color) {
		GraphicsContext graphics = this.getGraphicsContext2D();
		double width = this.getWidth();
		double height = this.getHeight();
		graphics.clearRect(0, 0, width, height);
		if (values.length < 2) {
			return;
		}
		double top = max;
		if (top <= 0) {
			for (double value : values) {
				if (!Double.isNaN(value)) {
					top = Math.max(top, value);
				}
			}
		}
		top = Math.max(top, 1e-9);
		double step = (width - 4) / (values.length - 1);
		graphics.beginPath();
		graphics.moveTo(2, height - 2);
		double lastX = 2;
		double lastY = height - 2;
		for (int index = 0; index < values.length; index++) {
			double value = Double.isNaN(values[index]) ? 0 : values[index];
			lastX = 2 + index * step;
			lastY = height - 2 - (height - 4) * Math.min(1, value / top);
			graphics.lineTo(lastX, lastY);
		}
		graphics.lineTo(lastX, height - 2);
		graphics.closePath();
		graphics.setFill(color.deriveColor(0, 1, 1, 0.15));
		graphics.fill();
		graphics.beginPath();
		for (int index = 0; index < values.length; index++) {
			double value = Double.isNaN(values[index]) ? 0 : values[index];
			double x = 2 + index * step;
			double y = height - 2 - (height - 4) * Math.min(1, value / top);
			if (index == 0) {
				graphics.moveTo(x, y);
			} else {
				graphics.lineTo(x, y);
			}
		}
		graphics.setStroke(color);
		graphics.setLineWidth(1.5);
		graphics.stroke();
		graphics.setFill(color);
		graphics.fillOval(lastX - 2.5, lastY - 2.5, 5, 5);
	}
}
