/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import org.junit.Assert;
import org.junit.Test;

import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/** Verifies that zooming keeps its pivot still, and where the center of a view is. */
public class ViewportGesturesZoomTests {

	private static final double TOLERANCE = 0.01;

	/** A graph whose canvas holds a wide content, panned and offset inside a larger pane. */
	private static Graph pannedGraph() {
		Graph graph = new Graph();
		graph.getCanvas().getChildren().add(new Rectangle(-300, -100, 1600, 900));
		graph.getCanvas().setLayoutX(57);
		graph.getCanvas().setLayoutY(-23);
		graph.getCanvas().setTranslateX(-140);
		graph.getCanvas().setTranslateY(85);
		Pane parent = new Pane(graph.getCanvas());
		parent.resize(1000, 700);
		new StackPane(parent).setLayoutX(120);
		return graph;
	}

	@Test
	public void zoomingKeepsThePivotStill() {
		Graph graph = pannedGraph();
		Point2D pivot = new Point2D(400, 300);
		for (double scale : new double[] { 0.4, 1.0, 1.7, 0.25 }) {
			Point2D underPivot = graph.getCanvas().sceneToLocal(pivot);
			graph.getViewportGestures().zoomTo(scale, pivot.getX(), pivot.getY());
			Point2D after = graph.getCanvas().localToScene(underPivot);
			Assert.assertEquals("x at " + scale, pivot.getX(), after.getX(), TOLERANCE);
			Assert.assertEquals("y at " + scale, pivot.getY(), after.getY(), TOLERANCE);
		}
	}

	@Test
	public void theCenterOfAViewIgnoresTheContentPannedPastItsEdges() {
		Graph graph = pannedGraph();
		Pane view = (Pane) graph.getCanvas().getParent();
		Point2D center = Graph.sceneCenterOf(view);
		Point2D expected = view.localToScene(500, 350);
		Assert.assertEquals(expected.getX(), center.getX(), TOLERANCE);
		Assert.assertEquals(expected.getY(), center.getY(), TOLERANCE);
	}

	@Test
	public void zoomingACanvasNotYetInAViewOnlyScalesIt() {
		Graph graph = new Graph();
		Assert.assertNull(graph.getCanvas().getParent());

		graph.getViewportGestures().zoomTo(1.7, 400, 300);

		Assert.assertEquals(1.7, graph.getCanvas().getScale(), TOLERANCE);
	}
}
