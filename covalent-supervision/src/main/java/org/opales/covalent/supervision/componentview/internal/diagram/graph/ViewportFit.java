/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import javafx.geometry.Bounds;

/**
 * The scale and translation that fit a canvas's content to the visible area: the content, with a
 * margin, as large as it fits, at most at 100 % and at least at the minimum scale, its center on
 * the area's center. The canvas scales around its own center, as a JavaFX node does, and lies at
 * the area's origin.
 *
 * @param scale the canvas's scale
 * @param translateX the canvas's horizontal translation
 * @param translateY the canvas's vertical translation
 */
public record ViewportFit(double scale, double translateX, double translateY) {

	/**
	 * Fits a content to an area.
	 *
	 * @param content the content's bounds in the canvas's own coordinates
	 * @param canvasWidth the canvas's width, whose middle it scales around
	 * @param canvasHeight the canvas's height, whose middle it scales around
	 * @param areaWidth the visible area's width
	 * @param areaHeight the visible area's height
	 * @param margin the room left around the content, in the area, over both sides together
	 * @param minScale the smallest scale allowed
	 * @return the canvas's scale and translation
	 */
	public static ViewportFit of(final Bounds content, final double canvasWidth, final double canvasHeight,
			final double areaWidth, final double areaHeight, final double margin, final double minScale) {
		double scale = Math.min((areaWidth - margin) / content.getWidth(), (areaHeight - margin) / content.getHeight());
		scale = Math.max(minScale, Math.min(1.0, scale));
		double pivotX = canvasWidth / 2;
		double pivotY = canvasHeight / 2;
		double translateX = areaWidth / 2 - pivotX - scale * (content.getCenterX() - pivotX);
		double translateY = areaHeight / 2 - pivotY - scale * (content.getCenterY() - pivotY);
		return new ViewportFit(scale, translateX, translateY);
	}
}
