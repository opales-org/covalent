/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

/**
 * Every color {@code componentview.internal.diagram.cells.PortPalette}
 * exposes, kept JavaFX-free here the same way {@link ComponentPortKind} is -
 * the actual {@code javafx.scene.paint.Color} values live only behind
 * {@code ComponentDiagramPreferencesActionKey.SetPortColor}'s payload and {@code
 * ComponentDiagramViewServices.setPortColor(...)/getPortColor(...)}.
 * {@link #getCategory()} groups entries for the Preferences panel; it
 * carries no other meaning.
 * <p>
 * Changing SERVICE_ACTIVITY, SERVICE_FAILURE or EVENT_ACTIVITY only takes
 * effect the next time that activity actually occurs on a port - see
 * {@code CovalentComponentCell.refreshPalette()}'s javadoc for why a forced
 * repaint can't show it any sooner.
 */
public enum PortColorKey {

	// --- Card chrome ---
	/** Color of the card outline and text. */
	GRAPHITE("Card outline / text", "Card chrome"),
	/** Fill color of the card body. */
	BODY("Card body fill", "Card chrome"),
	/** Fill color of the header band. */
	HEADER("Header band fill", "Card chrome"),
	/** Color of the inner card border. */
	CARD_BORDER_SOFT("Inner card border", "Card chrome"),
	/** Color of the line under the header. */
	HEADER_DIVIDER("Header divider line", "Card chrome"),
	/** Color of the divider beside the header icon. */
	ICON_DIVIDER("Header icon divider", "Card chrome"),

	// --- Lifecycle status badge: one color per status, for its dot and text,
	// the pill itself being a tint of it over the card body ---
	/** Status badge of an activated component. */
	STATUS_ACTIVATED("Activated", "Status badge"),
	/** Status badge of a passivated component. */
	STATUS_PASSIVATED("Passivated", "Status badge"),
	/** Status badge of an instantiated or initialized component. */
	STATUS_STARTING("Instantiated / initialized", "Status badge"),
	/** Status badge of a deployed or removed component. */
	STATUS_INACTIVE("Deployed / removed", "Status badge"),
	/** Status badge of a failed component. */
	STATUS_FAILED("Failed", "Status badge"),
	/** Status badge of a composite whose leaves are in different states, none failed. */
	STATUS_MIXED("Mixed (composite)", "Status badge"),
	/** Status badge of a composite some of whose leaves failed. */
	STATUS_DEGRADED("Degraded (composite)", "Status badge"),

	// --- Ports & wires ---
	/** Color of an idle port pad. */
	SLOT_IDLE("Pad, idle", "Ports & wires"),
	/** Color of a provided service port. */
	SERVICE_PROVIDED("Service provided", "Ports & wires"),
	/** Color of a used service port. */
	SERVICE_USED("Service used", "Ports & wires"),
	/** Color of a produced event port. */
	EVENT_PRODUCED("Event produced", "Ports & wires"),
	/** Color of a consumed event port. */
	EVENT_CONSUMED("Event consumed", "Ports & wires"),
	/** Color of an offered parameter port. */
	PARAMETER("Parameter offered", "Ports & wires"),
	/** Color of an accessed parameter port. */
	PARAMETER_ACCESSED("Parameter accessed", "Ports & wires"),
	/** Color of composition links. */
	COMPONENT_TYPE("Component type", "Ports & wires"),

	// --- Live activity flash ---
	/** Color a service port flashes while a call goes through it. */
	SERVICE_ACTIVITY("Service activity", "Live activity"),
	/** Color a service port flashes when a call through it fails. */
	SERVICE_FAILURE("Service failure", "Live activity"),
	/** Color an event port flashes while an event goes through it. */
	EVENT_ACTIVITY("Event activity", "Live activity"),

	// --- Interaction feedback ---
	/** Color of what a click highlights. */
	HIGHLIGHT("Click-to-highlight", "Interaction"),
	/** Color of a composite card under the pointer. */
	HOVER("Composite card under the pointer", "Interaction"),
	/** Color of a selected card. */
	SELECTION("Card selection", "Interaction");

	private final String label;

	private final String category;

	PortColorKey(final String label, final String category) {
		this.label = label;
		this.category = category;
	}

	/**
	 * Returns the label under which the entry is listed.
	 *
	 * @return the panel's display label for this entry.
	 */
	public String getLabel() {
		return this.label;
	}

	/**
	 * Returns the section the entry is listed under.
	 *
	 * @return the panel's section header for this entry.
	 */
	public String getCategory() {
		return this.category;
	}
}
