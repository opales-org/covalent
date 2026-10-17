/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
 * Best-effort orthogonal routing in canvas coordinates, independent of JavaFX.
 * Only the connection's two cards are obstacles. The segments already occupied by
 * other wires are soft constraints: separating parallel segments matters more than a short
 * detour, but shared port exits and crowded corridors may still overlap.
 */
public final class OrthogonalRouter {
	static final double SPACING = 12;
	private static final double MARGIN = 16;
	private static final double BEND_COST = 24;
	private static final double EPSILON = 0.000001;
	private static final int MAX_COORDINATES = 64;

	/**
	 * A point of the canvas.
	 *
	 * @param x the horizontal coordinate
	 * @param y the vertical coordinate
	 */
	public record Point(double x, double y) { }

	/**
	 * The rectangle that a cell occupies on the canvas, which a route must not cross.
	 *
	 * @param left the horizontal coordinate of the left side
	 * @param top the vertical coordinate of the top side
	 * @param right the horizontal coordinate of the right side
	 * @param bottom the vertical coordinate of the bottom side
	 */
	public record Card(double left, double top, double right, double bottom) { }

	/** Direction in which a wire leaves or enters a card: the side of the card where its port sits. */
	public enum Direction {
		RIGHT(1, 0), BOTTOM(0, 1), LEFT(-1, 0), TOP(0, -1);

		final int dx;
		final int dy;

		Direction(int dx, int dy) {
			this.dx = dx;
			this.dy = dy;
		}

		Direction opposite() {
			return values()[(ordinal() + 2) % 4];
		}
	}

	/**
	 * What a route links: two ports, each with the direction its wire takes and the card that carries it.
	 *
	 * @param start the position of the first port
	 * @param startDirection the direction in which the wire leaves the first port
	 * @param source the card that carries the first port
	 * @param end the position of the second port
	 * @param endDirection the direction from which the wire reaches the second port
	 * @param target the card that carries the second port
	 */
	public record Connection(Point start, Direction startDirection, Card source,
			Point end, Direction endDirection, Card target) { }

	/**
	 * A horizontal or vertical stretch of a route.
	 *
	 * @param start the first end of the stretch
	 * @param end the second end of the stretch
	 */
	public record Segment(Point start, Point end) {
		boolean horizontal() { return start.y == end.y; }
		double minX() { return Math.min(start.x, end.x); }
		double maxX() { return Math.max(start.x, end.x); }
		double minY() { return Math.min(start.y, end.y); }
		double maxY() { return Math.max(start.y, end.y); }
		double length() { return maxX() - minX() + maxY() - minY(); }
	}

	public List<Point> route(Connection connection, List<Segment> occupied) {
		if (connection.start.equals(connection.end)) {
			return List.of(connection.start);
		}
		Connection protectedPorts = new Connection(connection.start, connection.startDirection,
				protectPort(connection.source, connection.start, connection.startDirection),
				connection.end, connection.endDirection,
				protectPort(connection.target, connection.end, connection.endDirection));
		Grid grid = new Grid(protectedPorts, occupied);
		List<Point> result = grid.search(false, false, 6, Double.POSITIVE_INFINITY);
		if (result.isEmpty()) {
			// Port banks may overlap when cards are very close. Prefer a route
			// clear of the actual cards before considering a penetrating route.
			grid = new Grid(connection, occupied);
			result = grid.search(false, false, 6, Double.POSITIVE_INFINITY);
		}
		// Overlapping cards or a port inside the opposite card can make a clear
		// route impossible. Keep the wire visible, minimizing penetrations,
		// rather than looping or pretending that a blocked route is clear.
		if (result.isEmpty()) {
			return grid.search(true, false, 6, Double.POSITIVE_INFINITY);
		}
		if (occupied.isEmpty()) {
			return result;
		}
		// Allow one extra dogleg when a facing-port corridor is too narrow for
		// parallel columns. A turn budget and a higher congestion-pass bend cost
		// prevent dense port banks from producing serpentine paths.
		int bends = result.size() - 2;
		double baselineLength = segments(result).stream().mapToDouble(Segment::length).sum();
		List<Point> separated = grid.search(false, true, bends == 0 ? 4 : Math.min(6, bends + 2),
				baselineLength * 3 + SPACING * 4);
		return separated.isEmpty() ? result : separated;
	}

	private Card protectPort(Card card, Point anchor, Direction side) {
		// Protect the active port bank as well as the card body. A detour must
		// not leave a handle correctly, then double back through its glyph.
		return switch (side) {
			case LEFT -> new Card(Math.min(card.left, anchor.x), card.top, card.right, card.bottom);
			case RIGHT -> new Card(card.left, card.top, Math.max(card.right, anchor.x), card.bottom);
			case TOP -> new Card(card.left, Math.min(card.top, anchor.y), card.right, card.bottom);
			case BOTTOM -> new Card(card.left, card.top, card.right, Math.max(card.bottom, anchor.y));
		};
	}

	public static List<Segment> segments(List<Point> points) {
		List<Segment> segments = new ArrayList<>();
		for (int i = 1; i < points.size(); i++) {
			segments.add(new Segment(points.get(i - 1), points.get(i)));
		}
		return segments;
	}

	private static double overlap(double a1, double a2, double b1, double b2) {
		return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
	}

	private static double penetration(Segment segment, Card card) {
		// Boundary contacts are allowed, in particular for composition anchors.
		// The candidate grid supplies exterior rails with a preferred margin.
		if (segment.horizontal()) {
			return segment.start.y > card.top + EPSILON && segment.start.y < card.bottom - EPSILON
					? overlap(segment.minX(), segment.maxX(), card.left, card.right) : 0;
		}
		return segment.start.x > card.left + EPSILON && segment.start.x < card.right - EPSILON
				? overlap(segment.minY(), segment.maxY(), card.top, card.bottom) : 0;
	}

	private static double congestion(Segment segment, List<Segment> occupied) {
		double cost = 0;
		for (Segment other : occupied) {
			if (segment.horizontal() == other.horizontal()) {
				double distance = segment.horizontal() ? Math.abs(segment.start.y - other.start.y)
						: Math.abs(segment.start.x - other.start.x);
				if (distance < SPACING) {
					double shared = segment.horizontal()
							? overlap(segment.minX(), segment.maxX(), other.minX(), other.maxX())
							: overlap(segment.minY(), segment.maxY(), other.minY(), other.maxY());
					cost += shared * 40 * (1 - distance / SPACING);
				}
			} else {
				Segment h = segment.horizontal() ? segment : other;
				Segment v = segment.horizontal() ? other : segment;
				// Half-open intervals count a crossing once on the subdivided grid.
				if (v.start.x > h.minX() && v.start.x <= h.maxX()
						&& h.start.y > v.minY() && h.start.y <= v.maxY()) {
					cost += 18;
				}
			}
		}
		return cost;
	}

	private static double[] coordinates(Connection c, List<Segment> occupied, boolean horizontal) {
		double start = horizontal ? c.start.x : c.start.y;
		double end = horizontal ? c.end.x : c.end.y;
		TreeSet<Double> base = new TreeSet<>();
		for (double value : new double[] { start, end, (start + end) / 2,
				start - SPACING, start + SPACING, end - SPACING, end + SPACING }) {
			base.add(value);
		}
		for (Card card : List.of(c.source, c.target)) {
			base.add((horizontal ? card.left : card.top) - MARGIN);
			base.add((horizontal ? card.right : card.bottom) + MARGIN);
		}
		double aMin = horizontal ? c.source.left : c.source.top;
		double aMax = horizontal ? c.source.right : c.source.bottom;
		double bMin = horizontal ? c.target.left : c.target.top;
		double bMax = horizontal ? c.target.right : c.target.bottom;
		if (aMax < bMin) {
			base.add((aMax + bMin) / 2);
		}
		if (bMax < aMin) {
			base.add((bMax + aMin) / 2);
		}

		// Wires reserve actual coordinates, not a modulo lane number. Add rails
		// beside occupied segments, prioritizing nearby ones to bound drag cost.
		double extension = MARGIN + SPACING * occupied.size();
		double low = base.first() - extension;
		double high = base.last() + extension;
		double crossLow = Math.min(Math.min(c.start.y, c.end.y), Math.min(c.source.top, c.target.top));
		double crossHigh = Math.max(Math.max(c.start.y, c.end.y), Math.max(c.source.bottom, c.target.bottom));
		if (!horizontal) {
			crossLow = Math.min(Math.min(c.start.x, c.end.x), Math.min(c.source.left, c.target.left));
			crossHigh = Math.max(Math.max(c.start.x, c.end.x), Math.max(c.source.right, c.target.right));
		}
		TreeSet<Double> candidates = new TreeSet<>();
		for (Segment segment : occupied) {
			if (segment.horizontal() == horizontal) {
				continue;
			}
			double min = horizontal ? segment.minY() : segment.minX();
			double max = horizontal ? segment.maxY() : segment.maxX();
			if (max < crossLow - MARGIN || min > crossHigh + MARGIN) {
				continue;
			}
			double value = horizontal ? segment.start.x : segment.start.y;
			if (value >= low && value <= high) {
				candidates.add(value - SPACING);
				candidates.add(value + SPACING);
			}
		}
		double center = (start + end) / 2;
		candidates.stream().sorted(Comparator.<Double>comparingDouble(v -> Math.abs(v - center))
				.thenComparingDouble(v -> v)).limit(MAX_COORDINATES - base.size()).forEach(base::add);
		return base.stream().mapToDouble(Double::doubleValue).toArray();
	}

	private record Step(int state, double cost) { }

	private static final class Grid {
		private final Connection connection;
		private final double[] xs;
		private final double[] ys;
		private final double[][] travel;
		private final double[][] geometry;
		private final double[][] blocked;
		private final int start;
		private final int end;

		Grid(Connection connection, List<Segment> occupied) {
			this.connection = connection;
			xs = coordinates(connection, occupied, true);
			ys = coordinates(connection, occupied, false);
			int count = xs.length * ys.length;
			travel = new double[count][4];
			geometry = new double[count][4];
			blocked = new double[count][4];
			start = index(connection.start);
			end = index(connection.end);
			for (int node = 0; node < count; node++) {
				for (Direction direction : List.of(Direction.RIGHT, Direction.BOTTOM)) {
					int next = neighbor(node, direction);
					if (next < 0) {
						continue;
					}
					Segment segment = new Segment(point(node), point(next));
					double preferredX = corridorCenter(connection.source.left, connection.source.right,
							connection.target.left, connection.target.right, connection.start.x, connection.end.x);
					double preferredY = corridorCenter(connection.source.top, connection.source.bottom,
							connection.target.top, connection.target.bottom, connection.start.y, connection.end.y);
					double deviation = segment.horizontal()
							? Math.abs(segment.start.y - preferredY) / Math.max(1, ys[ys.length - 1] - ys[0])
							: Math.abs(segment.start.x - preferredX) / Math.max(1, xs[xs.length - 1] - xs[0]);
					// Prefer the center of a free corridor among otherwise equal
					// paths, instead of hugging an arbitrary card edge.
					double geometricCost = segment.length() * (1 + 0.01 * deviation);
					double cost = geometricCost + congestion(segment, occupied);
					double collision = penetration(segment, connection.source) + penetration(segment, connection.target);
					travel[node][direction.ordinal()] = travel[next][direction.opposite().ordinal()] = cost;
					geometry[node][direction.ordinal()] = geometry[next][direction.opposite().ordinal()] = geometricCost;
					blocked[node][direction.ordinal()] = blocked[next][direction.opposite().ordinal()] = collision;
				}
			}
		}

		private double corridorCenter(double aMin, double aMax, double bMin, double bMax, double start, double end) {
			if (aMax < bMin) {
				return (aMax + bMin) / 2;
			}
			if (bMax < aMin) {
				return (bMax + aMin) / 2;
			}
			return (start + end) / 2;
		}

		private int index(Point point) {
			return Arrays.binarySearch(ys, point.y) * xs.length + Arrays.binarySearch(xs, point.x);
		}

		private Point point(int node) {
			return new Point(xs[node % xs.length], ys[node / xs.length]);
		}

		private int neighbor(int node, Direction direction) {
			int x = node % xs.length + direction.dx;
			int y = node / xs.length + direction.dy;
			return x < 0 || x >= xs.length || y < 0 || y >= ys.length ? -1 : y * xs.length + x;
		}

		private List<Point> search(boolean allowPenetration, boolean avoidWires, int maxBends, double maxLength) {
			// Direction is part of the state so bends have a cost and a cheap
			// arrival from the wrong side cannot suppress a valid port approach.
			int count = xs.length * ys.length;
			double[] distance = new double[count * 5 * (maxBends + 1)];
			double[] lengths = new double[distance.length];
			int[] previous = new int[distance.length];
			Arrays.fill(distance, Double.POSITIVE_INFINITY);
			Arrays.fill(previous, -1);
			PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingDouble(Step::cost)
					.thenComparingInt(Step::state));
			int initial = start * 5 + 4;
			distance[initial] = 0;
			queue.add(new Step(initial, 0));
			while (!queue.isEmpty()) {
				Step step = queue.remove();
				if (step.cost > distance[step.state]) {
					continue;
				}
				int node = step.state / 5 % count;
				int bends = step.state / 5 / count;
				int incoming = step.state % 5;
				if (node == end) {
					return path(step.state, previous);
				}
				for (Direction direction : Direction.values()) {
					if (node == start && direction != connection.startDirection) {
						continue;
					}
					if (incoming != 4 && direction.ordinal() == (incoming + 2) % 4) {
						continue;
					}
					int next = neighbor(node, direction);
					if (next < 0 || next == start) {
						continue;
					}
					if (next == end && direction != connection.endDirection.opposite()) {
						continue;
					}
					double collision = blocked[node][direction.ordinal()];
					if (collision > EPSILON && !allowPenetration) {
						continue;
					}
					boolean turn = incoming != 4 && incoming != direction.ordinal();
					int nextBends = bends + (turn ? 1 : 0);
					if (nextBends > maxBends) {
						continue;
					}
					Point a = point(node);
					Point b = point(next);
					double length = lengths[step.state] + Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
					double remaining = Math.abs(b.x - connection.end.x) + Math.abs(b.y - connection.end.y);
					// Avoid touring around a whole component merely to remove a
					// short overlap. This is a bounded best-effort search: a
					// cheaper but longer arrival can suppress a shorter one;
					// the baseline remains available if the budget is exhausted.
					if (length + remaining > maxLength) {
						continue;
					}
					double cost = step.cost + (avoidWires ? travel[node][direction.ordinal()]
							: geometry[node][direction.ordinal()]) + collision * 10000;
					if (turn) {
						cost += BEND_COST * (avoidWires ? 5 : 1);
					}
					int state = (nextBends * count + next) * 5 + direction.ordinal();
					if (cost + EPSILON < distance[state]) {
						distance[state] = cost;
						lengths[state] = length;
						previous[state] = step.state;
						queue.add(new Step(state, cost));
					}
				}
			}
			return List.of();
		}

		private List<Point> path(int state, int[] previous) {
			List<Point> reversed = new ArrayList<>();
			for (int current = state; current >= 0; current = previous[current]) {
				reversed.add(point(current / 5 % (xs.length * ys.length)));
			}
			Collections.reverse(reversed);
			List<Point> result = new ArrayList<>();
			for (Point point : reversed) {
				if (result.size() >= 2) {
					Point a = result.get(result.size() - 2);
					Point b = result.get(result.size() - 1);
					if ((a.x == b.x && b.x == point.x) || (a.y == b.y && b.y == point.y)) {
						result.remove(result.size() - 1);
					}
				}
				result.add(point);
			}
			return List.copyOf(result);
		}
	}
}
