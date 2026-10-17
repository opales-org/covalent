/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.Objects;

/**
 * A directed edge between two component types in the topology graph, e.g. a
 * services user pointing at its provider. Plain value object, part of
 * {@link ComponentDiagramTopology}.
 */
public final class ComponentDiagramEdge {

	private final Class<?> from;
	private final Class<?> to;
	private final ComponentPortKind kind;

	/**
	 * The actual service/parameters/event interface this edge is about -
	 * e.g. for a {@code SERVICE} edge, the service interface itself, not
	 * just the fact that it is a service. {@code null} for a {@code
	 * COMPONENT_TYPE} (composition) edge, which has no such interface.
	 * Part of equality/hashing: two components can be connected through
	 * more than one distinct relation of the same kind (e.g. A uses two
	 * different services from B), and each must survive as its own edge
	 * rather than collapsing into one.
	 */
	private final Class<?> relation;

	/**
	 * Creates an edge.
	 *
	 * @param from the component type the edge starts from
	 * @param to the component type the edge points to
	 * @param kind the kind of relation the edge stands for
	 * @param relation the service, parameters or event interface the edge is about, {@code null} for a composition edge
	 */
	public ComponentDiagramEdge(final Class<?> from, final Class<?> to, final ComponentPortKind kind,
			final Class<?> relation) {
		this.from = Objects.requireNonNull(from, "from");
		this.to = Objects.requireNonNull(to, "to");
		this.kind = Objects.requireNonNull(kind, "kind");
		this.relation = relation;
	}

	/**
	 * Returns the component type the edge starts from.
	 *
	 * @return the component type the edge starts from, such as a service user
	 */
	public Class<?> getFrom() {
		return this.from;
	}

	/**
	 * Returns the component type the edge points to.
	 *
	 * @return the component type the edge points to, such as a service provider
	 */
	public Class<?> getTo() {
		return this.to;
	}

	/**
	 * Returns the kind of relation the edge stands for.
	 *
	 * @return the kind of relation the edge stands for
	 */
	public ComponentPortKind getKind() {
		return this.kind;
	}

	/**
	 * Returns the interface the edge is about.
	 *
	 * @return the service, parameters or event interface the edge is about, {@code null} for a composition edge
	 */
	public Class<?> getRelation() {
		return this.relation;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ComponentDiagramEdge)) {
			return false;
		}
		ComponentDiagramEdge that = (ComponentDiagramEdge) other;
		return this.from.equals(that.from) && this.to.equals(that.to) && this.kind == that.kind
				&& Objects.equals(this.relation, that.relation);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.from, this.to, this.kind, this.relation);
	}
}
