/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

import static org.junit.Assert.*;
import static org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.Direction.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.opales.covalent.supervision.componentview.internal.diagram.edges.OrthogonalRouter.*;

public class OrthogonalRouterTest {
    private final OrthogonalRouter router = new OrthogonalRouter();
    private final Card left = new Card(0, 0, 100, 100);
    private final Card right = new Card(400, 0, 500, 100);

    @Test
    public void facingAlignedPortsHaveNoHook() {
        Connection c = new Connection(new Point(128, 50), RIGHT, left, new Point(372, 50), LEFT, right);
        assertEquals(List.of(c.start(), c.end()), route(c));
    }

    @Test
    public void closeFacingPortsDoNotOvershootEachOther() {
        Connection c = new Connection(new Point(128, 50), RIGHT, left, new Point(148, 50), LEFT,
                new Card(176, 0, 276, 100));
        assertEquals(List.of(c.start(), c.end()), route(c));
    }

    @Test
    public void staggeredFacingPortsUseOnlyTwoBends() {
        Connection c = new Connection(new Point(128, 40), RIGHT, left, new Point(372, 80), LEFT, right);
        List<Point> path = route(c);
        assertEquals(4, path.size());
        assertEquals(284, length(path), 0.001);
        assertClear(c, path);
    }

    @Test
    public void oppositePortsOnStackedCardsCrossTheFreeGap() {
        // The former +80 lane offset bounced between y=204 and y=310,
        // although the gap between the cards is open.
        Connection c = new Connection(new Point(400, 200), RIGHT, new Card(0, 0, 336, 214),
                new Point(-60, 310), LEFT, new Card(0, 300, 336, 514));
        List<Point> path = route(c);
        assertClear(c, path);
        assertTrue(path.toString(), OrthogonalRouter.segments(path).stream().anyMatch(s ->
                s.horizontal() && s.minX() < 0 && s.maxX() > 336 && s.start().y() > 214 && s.start().y() < 300));
    }

    @Test
    public void narrowVerticalGapRemainsUsable() {
        Connection c = new Connection(new Point(128, 70), RIGHT, left,
                new Point(-28, 130), LEFT, new Card(0, 108, 100, 208));
        List<Point> path = route(c);
        assertClear(c, path);
        assertTrue(path.toString(), path.stream().anyMatch(p -> p.y() > 100 && p.y() < 108));
    }

    @Test
    public void sameSidePortsRouteOutsideBothCards() {
        Connection c = new Connection(new Point(128, 40), RIGHT, left,
                new Point(148, 250), RIGHT, new Card(20, 200, 120, 300));
        List<Point> path = route(c);
        assertClear(c, path);
        assertEquals(4, path.size());
        assertTrue(path.get(1).x() > 148);
    }

    @Test
    public void bottomPortsAndCompositionBoundaryAnchorsRespectTheirDirections() {
        Connection parameters = new Connection(new Point(50, 103), BOTTOM, left,
                new Point(450, 103), BOTTOM, right);
        assertClear(parameters, route(parameters));
        Connection composition = new Connection(new Point(120, 50), RIGHT, left,
                new Point(400, 50), LEFT, right);
        assertClear(composition, route(composition));
    }

    @Test
    public void occupiedVerticalTrunksAreSeparatedBeyondElevenWires() {
        List<Segment> occupied = new ArrayList<>();
        Set<Double> columns = new HashSet<>();
        Card below = new Card(0, 400, 100, 500);
        for (int i = 0; i < 16; i++) {
            Connection c = new Connection(new Point(128, 10 + i * 5), RIGHT, left,
                    new Point(128, 410 + i * 5), RIGHT, below);
            List<Point> path = router.route(c, occupied);
            assertClear(c, path);
            Segment trunk = OrthogonalRouter.segments(path).stream()
                    .filter(s -> !s.horizontal() && s.minY() < 250 && s.maxY() >= 250)
                    .findFirst().orElseThrow(() -> new AssertionError(path.toString()));
            assertTrue("Shared trunk: " + path, columns.add(trunk.start().x()));
            assertTrue("Too many bends: " + path, path.size() <= 6);
            occupied.addAll(OrthogonalRouter.segments(path));
        }
    }

    @Test
    public void sharedSourceWithNarrowFacingGapCanUseAnExtraDogleg() {
        List<Segment> occupied = new ArrayList<>();
        Set<Double> columns = new HashSet<>();
        Card target = new Card(170, 300, 270, 500);
        for (int i = 0; i < 6; i++) {
            Connection c = new Connection(new Point(128, 50), RIGHT, left,
                    new Point(142, 320 + 25 * i), LEFT, target);
            List<Point> path = router.route(c, occupied);
            assertClear(c, path);
            assertTrue("Too many bends: " + path, path.size() <= 6);
            assertClear(new Connection(c.start(), RIGHT, new Card(0, 0, 128, 100),
                    c.end(), LEFT, new Card(142, 300, 270, 500)), path);
            assertTrue("Excessive detour: " + path, length(path) <= length(route(c)) * 3 + 48);
            OrthogonalRouter.segments(path).stream()
                    .filter(s -> !s.horizontal() && s.minY() < 200 && s.maxY() >= 200)
                    .forEach(s -> columns.add(s.start().x()));
            occupied.addAll(OrthogonalRouter.segments(path));
        }
        assertTrue("All wires collapsed onto one column: " + columns, columns.size() >= 4);
    }

    @Test
    public void occupiedHorizontalTrunksAreSeparated() {
        List<Segment> occupied = new ArrayList<>();
        Set<Double> rows = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            Connection c = new Connection(new Point(10 + i * 10, 103), BOTTOM, left,
                    new Point(410 + i * 10, 103), BOTTOM, right);
            List<Point> path = router.route(c, occupied);
            assertClear(c, path);
            Segment trunk = OrthogonalRouter.segments(path).stream()
                    .filter(s -> s.horizontal() && s.minX() <= 100 && s.maxX() >= 400)
                    .findFirst().orElseThrow();
            assertTrue("Shared trunk: " + path, rows.add(trunk.start().y()));
            occupied.addAll(OrthogonalRouter.segments(path));
        }
    }

    @Test
    public void repeatedRoutingIsDeterministicAndTranslationInvariant() {
        Connection c = new Connection(new Point(128, 40), RIGHT, left, new Point(372, 80), LEFT, right);
        List<Point> first = route(c);
        assertEquals(first, route(c));
        Connection moved = new Connection(translate(c.start()), RIGHT, translate(left),
                translate(c.end()), LEFT, translate(right));
        assertEquals(first.stream().map(this::translate).toList(), route(moved));
    }

    @Test(timeout = 5000)
    public void overlappingCardsStillProduceAFiniteOrthogonalWire() {
        Connection c = new Connection(new Point(128, 50), RIGHT, left, new Point(22, 50), LEFT,
                new Card(50, 0, 150, 100));
        List<Point> path = route(c);
        assertOrthogonal(c, path);
        assertTrue(path.size() < 20);
    }

    @Test
    public void allSidePairsRouteAroundSeparatedCards() {
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                Card target = new Card(180, 180, 280, 280);
                Connection c = new Connection(anchor(left, a), a, left, anchor(target, b), b, target);
                assertClear(c, route(c));
            }
        }
    }

    private List<Point> route(Connection c) { return router.route(c, List.of()); }
    private double length(List<Point> path) { return OrthogonalRouter.segments(path).stream().mapToDouble(Segment::length).sum(); }
    private Point translate(Point p) { return new Point(p.x() - 200, p.y() + 300); }
    private Card translate(Card r) { return new Card(r.left() - 200, r.top() + 300, r.right() - 200, r.bottom() + 300); }

    private Point anchor(Card card, Direction d) {
        return switch (d) {
            case LEFT -> new Point(card.left() - 28, (card.top() + card.bottom()) / 2);
            case RIGHT -> new Point(card.right() + 28, (card.top() + card.bottom()) / 2);
            case TOP -> new Point((card.left() + card.right()) / 2, card.top() - 28);
            case BOTTOM -> new Point((card.left() + card.right()) / 2, card.bottom() + 28);
        };
    }

    private void assertClear(Connection c, List<Point> path) {
        assertOrthogonal(c, path);
        for (Segment segment : OrthogonalRouter.segments(path)) {
            for (Card card : List.of(c.source(), c.target())) {
                boolean crosses = segment.horizontal()
                        ? segment.start().y() > card.top() && segment.start().y() < card.bottom()
                            && segment.maxX() > card.left() && segment.minX() < card.right()
                        : segment.start().x() > card.left() && segment.start().x() < card.right()
                            && segment.maxY() > card.top() && segment.minY() < card.bottom();
                assertFalse("Card crossed: " + c + " / " + path, crosses);
            }
        }
    }

    private void assertOrthogonal(Connection c, List<Point> path) {
        assertTrue(path.toString(), path.size() >= 2);
        assertEquals(c.start(), path.getFirst());
        assertEquals(c.end(), path.getLast());
        List<Segment> segments = OrthogonalRouter.segments(path);
        for (Segment segment : segments) {
            assertTrue(segment.toString(), segment.start().x() == segment.end().x()
                    || segment.start().y() == segment.end().y());
            assertTrue(segment.length() > 0);
        }
        assertDirection(c.start(), path.get(1), c.startDirection());
        assertDirection(c.end(), path.get(path.size() - 2), c.endDirection());
        for (int i = 1; i < segments.size(); i++) {
            assertNotEquals("Redundant bend or reversal: " + path,
                    segments.get(i - 1).horizontal(), segments.get(i).horizontal());
        }
    }

    private void assertDirection(Point a, Point b, Direction d) {
        assertTrue("Wrong port direction: " + a + " -> " + b + " / " + d,
                (b.x() - a.x()) * d.dx + (b.y() - a.y()) * d.dy > 0);
    }
}
