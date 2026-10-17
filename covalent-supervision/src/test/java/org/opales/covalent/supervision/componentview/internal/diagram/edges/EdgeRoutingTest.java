/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import static org.junit.Assert.*;
import static org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Direction.RIGHT;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Point;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Pane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;

public class EdgeRoutingTest {
    private final ArrayDeque<Runnable> scheduled = new ArrayDeque<>();
    private final EdgeRouting routing = new EdgeRouting(scheduled::add);
    private final Pane canvas = new Pane();

    @Test
    public void batchesCoordinatesAndReroutesCompetingWires() {
        Wire a = new Wire("a");
        Wire b = new Wire("b");
        canvas.getChildren().addAll(a.owner, b.owner);
        assertEquals(1, scheduled.size());
        flush();
        List<Point> before = points(b.line);
        a.startY.set(-450);
        a.endY.set(-250);
        a.sourceTop.set(-500);
        a.targetTop.set(-300);
        assertEquals(1, scheduled.size());
        assertEquals(before, points(b.line));
        flush();
        assertEquals(new Point(128, -450), points(a.line).getFirst());
        assertNotEquals(before, points(b.line));
    }

    @Test
    public void hidingAndRemovingWiresReleasesTheirCorridorAndListeners() {
        Wire a = new Wire("a");
        Wire b = new Wire("b");
        canvas.getChildren().addAll(a.owner, b.owner);
        flush();
        List<Point> alone = points(a.line);
        assertNotEquals(alone, points(b.line));
        a.owner.setVisible(false);
        flush();
        assertEquals(alone, points(b.line));
        a.owner.setVisible(true);
        flush();
        assertNotEquals(alone, points(b.line));
        canvas.getChildren().remove(a.owner);
        flush();
        assertEquals(alone, points(b.line));
        a.startY.set(70);
        a.style.set(EdgeRoutingStyle.STRAIGHT);
        assertTrue("Detached wire still schedules work", scheduled.isEmpty());
        canvas.getChildren().add(a.owner);
        flush();
        assertEquals(2, points(a.line).size());
    }

    @Test
    public void semanticOrderDoesNotDependOnGraphicCreationOrder() {
        Wire b = new Wire("b");
        Wire a = new Wire("a");
        canvas.getChildren().addAll(b.owner, a.owner);
        flush();
        List<Point> expectedA = points(a.line);
        List<Point> expectedB = points(b.line);
        canvas.getChildren().clear();
        flush();
        Wire nextA = new Wire("a");
        Wire nextB = new Wire("b");
        canvas.getChildren().addAll(nextA.owner, nextB.owner);
        flush();
        assertEquals(expectedA, points(nextA.line));
        assertEquals(expectedB, points(nextB.line));
    }

    @Test
    public void directModeAndSynchronousFitFlushUseCurrentEndpoints() {
        Wire a = new Wire("a");
        canvas.getChildren().add(a.owner);
        routing.layoutNow();
        assertEquals(4, points(a.line).size());
        a.style.set(EdgeRoutingStyle.STRAIGHT);
        a.endY.set(280);
        routing.layoutNow();
        assertEquals(List.of(new Point(128, 50), new Point(128, 280)), points(a.line));
        flush();
        a.style.set(EdgeRoutingStyle.CORNER);
        flush();
        assertEquals(4, points(a.line).size());
    }

    private void flush() {
        while (!scheduled.isEmpty()) scheduled.remove().run();
    }

    private List<Point> points(RoutedLine line) {
        List<Point> points = new ArrayList<>();
        line.getElements().forEach(element -> {
            if (element instanceof MoveTo move) points.add(new Point(move.getX(), move.getY()));
            if (element instanceof LineTo to) points.add(new Point(to.getX(), to.getY()));
        });
        return points;
    }

    private final class Wire {
        final Pane owner = new Pane();
        final SimpleDoubleProperty startY = new SimpleDoubleProperty(50);
        final SimpleDoubleProperty endY = new SimpleDoubleProperty(250);
        final SimpleDoubleProperty sourceTop = new SimpleDoubleProperty(0);
        final SimpleDoubleProperty targetTop = new SimpleDoubleProperty(200);
        final SimpleObjectProperty<EdgeRoutingStyle> style = new SimpleObjectProperty<>(EdgeRoutingStyle.CORNER);
        final RoutedLine line;

        Wire(String key) {
            line = new RoutedLine(routing, key, owner, new SimpleDoubleProperty(128), startY,
                    new SimpleDoubleProperty(128), endY, () -> RIGHT, () -> RIGHT,
                    new RoutedLine.CardBounds(new SimpleDoubleProperty(0), sourceTop, new SimpleDoubleProperty(100), new SimpleDoubleProperty(100)),
                    new RoutedLine.CardBounds(new SimpleDoubleProperty(0), targetTop, new SimpleDoubleProperty(100), new SimpleDoubleProperty(100)),
                    style);
            owner.getChildren().add(line);
        }
    }
}
