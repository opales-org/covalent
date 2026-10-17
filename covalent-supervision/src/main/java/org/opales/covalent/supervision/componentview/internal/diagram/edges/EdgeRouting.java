/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Connection;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Point;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Segment;

import javafx.application.Platform;

/**
 * Coordinates visible wires in the order of their routing keys and batches geometry changes on the FX thread. The
 * routing key of a port or composition wire is made of the class names and port identities of its ends; the one of
 * a dependency link is made of the identity hash codes of its cells.
 */
public final class EdgeRouting {
	private final Set<RoutedLine> lines = new LinkedHashSet<>();
	private final OrthogonalRouter router = new OrthogonalRouter();
	private final Consumer<Runnable> schedule;
	private boolean pending;

	public EdgeRouting() {
		this(Platform::runLater);
	}

	EdgeRouting(Consumer<Runnable> schedule) {
		this.schedule = schedule;
	}

	void add(RoutedLine line) {
		lines.add(line);
		requestLayout();
	}

	void remove(RoutedLine line) {
		lines.remove(line);
		requestLayout();
	}

	public void requestLayout() {
		if (pending) {
			return;
		}
		pending = true;
		schedule.accept(() -> {
			if (pending) {
				layoutNow();
			}
		});
	}

	/** Routes the pending wires at once, before fitting or exporting a canvas, when its bounds must be final. */
	public void layoutNow() {
		if (!pending) {
			return;
		}
		pending = false;
		List<Segment> occupied = new ArrayList<>();
		List<RoutedLine> visible = lines.stream().filter(RoutedLine::isWireVisible)
				.sorted(Comparator.comparing(RoutedLine::routingKey)).toList();
		for (RoutedLine line : visible) {
			Connection connection = line.connection();
			List<Point> points = line.isOrthogonal() ? router.route(connection, occupied)
					: List.of(connection.start(), connection.end());
			line.render(points);
			if (line.isOrthogonal()) {
				occupied.addAll(OrthogonalRouter.segments(points));
			}
		}
	}
}
