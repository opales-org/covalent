/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import javafx.beans.property.DoubleProperty;
import javafx.scene.Group;
import javafx.scene.shape.Line;

/**
 * A line with an arrow head at its end point.
 * <p>
 * The arrow is a {@link Group} of three {@link Line}s: the shaft, from the start point to the end
 * point, and two barbs that start on the end point and fold back towards the start point. The
 * barbs are recomputed whenever one of the four coordinates changes, so the arrow can be bound to
 * moving nodes like a plain line.
 * <p>
 * The coordinate accessors and properties delegate to the shaft.
 */
public class Arrow extends Group {

	/** Default length of the barbs, measured along the shaft. */
	private static final double DEFAULT_HEAD_LENGTH = 10;

	/** Default offset of the barbs from the shaft, measured perpendicular to it. */
	private static final double DEFAULT_HEAD_WIDTH = 5;

	private final Line shaft;
	private final Line leftBarb;
	private final Line rightBarb;
	private final double headLength;
	private final double headWidth;

	/** Creates an arrow with default barb size. */
	public Arrow() {
		this(new Line(), new Line(), new Line());
	}

	/**
	 * Creates an arrow from three lines, with default barb size.
	 *
	 * @param shaft the line from the start point to the end point
	 * @param leftBarb the first barb
	 * @param rightBarb the second barb
	 */
	public Arrow(final Line shaft, final Line leftBarb, final Line rightBarb) {
		this(shaft, leftBarb, rightBarb, DEFAULT_HEAD_LENGTH, DEFAULT_HEAD_WIDTH);
	}

	/**
	 * Creates an arrow from three lines.
	 *
	 * @param shaft the line from the start point to the end point
	 * @param leftBarb the first barb
	 * @param rightBarb the second barb
	 * @param headLength the length of the barbs along the shaft
	 * @param headWidth the offset of the barbs from the shaft, perpendicular to it
	 */
	public Arrow(final Line shaft, final Line leftBarb, final Line rightBarb,
			final double headLength, final double headWidth) {
		super(shaft, leftBarb, rightBarb);
		this.shaft = shaft;
		this.leftBarb = leftBarb;
		this.rightBarb = rightBarb;
		this.headLength = headLength;
		this.headWidth = headWidth;

		shaft.startXProperty().addListener(o -> updateHead());
		shaft.startYProperty().addListener(o -> updateHead());
		shaft.endXProperty().addListener(o -> updateHead());
		shaft.endYProperty().addListener(o -> updateHead());
		updateHead();
	}

	/**
	 * Places the free end of each barb behind the end point, on both sides of the shaft. A shaft
	 * of length zero has no direction, so both barbs collapse onto the end point.
	 */
	private void updateHead() {
		final double endX = this.shaft.getEndX();
		final double endY = this.shaft.getEndY();
		final double backX = this.shaft.getStartX() - endX;
		final double backY = this.shaft.getStartY() - endY;
		final double length = Math.hypot(backX, backY);

		this.leftBarb.setEndX(endX);
		this.leftBarb.setEndY(endY);
		this.rightBarb.setEndX(endX);
		this.rightBarb.setEndY(endY);

		if (length == 0) {
			this.leftBarb.setStartX(endX);
			this.leftBarb.setStartY(endY);
			this.rightBarb.setStartX(endX);
			this.rightBarb.setStartY(endY);
			return;
		}

		// Unit vector pointing from the end point back along the shaft.
		final double ux = backX / length;
		final double uy = backY / length;
		// Base of the head, on the shaft.
		final double baseX = endX + ux * this.headLength;
		final double baseY = endY + uy * this.headLength;
		// The unit vector rotated by 90 degrees gives the offset direction of the barbs.
		this.leftBarb.setStartX(baseX - uy * this.headWidth);
		this.leftBarb.setStartY(baseY + ux * this.headWidth);
		this.rightBarb.setStartX(baseX + uy * this.headWidth);
		this.rightBarb.setStartY(baseY - ux * this.headWidth);
	}

	/**
	 * @param value the new x coordinate of the start point
	 */
	public final void setStartX(final double value) {
		this.shaft.setStartX(value);
	}

	/**
	 * @return the x coordinate of the start point
	 */
	public final double getStartX() {
		return this.shaft.getStartX();
	}

	public final DoubleProperty startXProperty() {
		return this.shaft.startXProperty();
	}

	/**
	 * @param value the new y coordinate of the start point
	 */
	public final void setStartY(final double value) {
		this.shaft.setStartY(value);
	}

	/**
	 * @return the y coordinate of the start point
	 */
	public final double getStartY() {
		return this.shaft.getStartY();
	}

	public final DoubleProperty startYProperty() {
		return this.shaft.startYProperty();
	}

	/**
	 * @param value the new x coordinate of the end point
	 */
	public final void setEndX(final double value) {
		this.shaft.setEndX(value);
	}

	/**
	 * @return the x coordinate of the end point
	 */
	public final double getEndX() {
		return this.shaft.getEndX();
	}

	public final DoubleProperty endXProperty() {
		return this.shaft.endXProperty();
	}

	/**
	 * @param value the new y coordinate of the end point
	 */
	public final void setEndY(final double value) {
		this.shaft.setEndY(value);
	}

	/**
	 * @return the y coordinate of the end point
	 */
	public final double getEndY() {
		return this.shaft.getEndY();
	}

	public final DoubleProperty endYProperty() {
		return this.shaft.endYProperty();
	}

}
