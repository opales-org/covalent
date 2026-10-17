/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

import javafx.scene.paint.Color;

/**
 * Single source of truth for every color used to render a component card
 * (CovalentComponentCell) and the wires between its ports (AnchoredPortEdge).
 * Every field here is a plain mutable static {@code Color} (not {@code
 * final}) because SupervisionHmi's Preferences panel can
 * change any of them at runtime through {@code
 * ComponentDiagramViewServices.setPortColor(...)}, the palette's only
 * writer. Every rendering call site - here and in AnchoredPortEdge - just
 * reads {@code PortPalette.NAME} and picks up the current value the next
 * time it runs. Nothing here
 * observes the change itself: {@code ComponentDiagramViewImpl.setPortColor(...)}
 * is what forces a repaint, by calling {@code CovalentComponentCell.refreshPalette()}
 * on every live cell and {@code AnchoredPortEdge.refreshColor()} on every
 * live edge right after the assignment - see those methods' javadoc for
 * exactly what gets redrawn and what, by nature, can't be (the three live
 * activity-flash colors only show up during an actual activity pulse).
 * <p>
 * A single shared set of fields keeps cards and wires structurally unable
 * to drift out of sync.
 */
public final class PortPalette {

	private PortPalette() {
	}

	// --- Wire widths, shared by the wires and the stems between a port's pad and its shape ---

	/** A wire's width, and its stems'. */
	public static final double WIRE_WIDTH = 1.8;

	/** A highlighted wire's width, and its stems'. */
	public static final double WIRE_HIGHLIGHT_WIDTH = 3.0;

	// --- Card chrome: body, header, borders, dividers, title/subtitle text ---

	public static Color GRAPHITE = Color.web("#0f1720");

	public static Color BODY = Color.web("#fbfcfd");

	public static Color HEADER = Color.web("#f5f8fb");

	/** Inner card border, and the "ACTIVE" status badge's own border. */
	public static Color CARD_BORDER_SOFT = Color.web("#d7dce2");

	/** Line under the header, separating it from the card body. */
	public static Color HEADER_DIVIDER = Color.web("#e2e8f0");

	/** Short vertical line next to the header's cube icon. */
	public static Color ICON_DIVIDER = Color.web("#cbd5e1");

	// --- Lifecycle status badge, shown on composite/component cards only: one
	// color per status, for the badge's dot and text, the pill being a tint
	// of it over BODY ---

	public static Color STATUS_ACTIVATED = Color.web("#10b981");

	public static Color STATUS_PASSIVATED = Color.web("#3b82f6");

	/** Instantiated or initialized, not yet activated. */
	public static Color STATUS_STARTING = Color.web("#eab308");

	/** Deployed but not instantiated, or removed. */
	public static Color STATUS_INACTIVE = Color.web("#94a3b8");

	public static Color STATUS_FAILED = Color.web("#ef4444");

	/** A composite whose leaves are in different states, none failed. */
	public static Color STATUS_MIXED = Color.web("#8b5cf6");

	/** A composite some of whose leaves failed, not all. */
	public static Color STATUS_DEGRADED = Color.web("#f97316");

	// --- Ports & wires: pads, shapes, stems - shared by a port and its wire ---

	public static Color SLOT_IDLE = Color.web("#ffffff");

	public static Color SERVICE_PROVIDED = Color.web("#2249c8");

	public static Color SERVICE_USED = Color.web("#10b4f7");

	public static Color EVENT_PRODUCED = Color.web("#f49a04");

	public static Color EVENT_CONSUMED = Color.web("#FF5E0F");

	/**
	 * Offered parameters port. Also the color of every parameter wire, since
	 * AnchoredPortEdge paints a wire in its supplying end's color.
	 */
	public static Color PARAMETER = Color.web("#6d28d9");

	public static Color PARAMETER_ACCESSED = Color.web("#c4b5fd");

	/** Component-type marker (card-top; never wired/connected, no edge color needed). */
	public static Color COMPONENT_TYPE = Color.web("#64717f");

	// --- Live activity flash on a pad: distinct per kind. Changing one of these only
	// takes effect the next time that activity actually occurs - see
	// CovalentComponentCell.refreshPalette()'s javadoc. ---

	public static Color SERVICE_ACTIVITY = Color.web("#a0afec");

	public static Color SERVICE_FAILURE = Color.web("#e95151");

	public static Color EVENT_ACTIVITY = Color.web("#98e0ab");

	// --- Interaction feedback ---

	/** Body of a composite card under the pointer. */
	public static Color HOVER = Color.web("#eff6ff");

	/** Click-to-highlight (pad + wire). */
	public static Color HIGHLIGHT = Color.web("#fde047");

	/**
	 * Card selection highlight, also used for the graph's rectangle-select
	 * overlay, so both selection affordances read the same.
	 */
	public static Color SELECTION = Color.web("#8066cc");
}
