/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

/**
 * The role a component plays at one end of a {@link ComponentDiagramEdge},
 * mirroring {@code componentview.internal.diagram.cells.ComponentPortRole}
 * the same JavaFX-free way {@link ComponentPortKind} mirrors its own
 * counterpart - used only by {@link ComponentDiagramExternalPort}, since
 * {@link ComponentDiagramEdge} itself carries no role (an ordinary,
 * same-level edge's direction already says who provides and who uses). No
 * {@code COMPONENT_TYPE} entry: composition edges never cross a focus
 * boundary (see {@code ComponentDiagramControllerImpl.focusTopology(...)}),
 * so an external port never needs it.
 * <p>
 * {@link #isFanOut()} is what the rollup in {@code focusTopology(...)}
 * actually branches on: {@code PROVIDED}/{@code PRODUCED}/{@code OFFERED}
 * are the "one giver, any number of external takers" roles - a single
 * external port collects every one of them regardless of which specific
 * component is on the other end. {@code USED}/{@code CONSUMED}/{@code
 * ACCESSED} are the opposite - each distinct external provider/producer/
 * offeror gets its own port, so navigating it always leads somewhere
 * specific; several internal descendants reaching the very same external
 * target still collapse into one port, since from outside this level
 * that is one connection, not several.
 */
public enum ComponentPortRole {

	/** The component provides a services interface; fan-out. */
	PROVIDED(true),
	/** The component uses a services interface of a provider; one port per provider. */
	USED(false),
	/** The component produces an events interface; fan-out. */
	PRODUCED(true),
	/** The component consumes an events interface of a producer; one port per producer. */
	CONSUMED(false),
	/** The component offers a parameters interface; fan-out. */
	OFFERED(true),
	/** The component accesses a parameters interface of an offeror; one port per offeror. */
	ACCESSED(false);

	private final boolean fanOut;

	ComponentPortRole(final boolean fanOut) {
		this.fanOut = fanOut;
	}

	/**
	 * Tells whether the role is one of the fan-out ones.
	 *
	 * @return {@code true} for a role where one giver serves any number of takers ({@link #PROVIDED},
	 * {@link #PRODUCED}, {@link #OFFERED}), whose external ports collapse into a single one
	 */
	public boolean isFanOut() {
		return this.fanOut;
	}
}
