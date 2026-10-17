/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A navigable port on a visible component, standing in for a real
 * dependency edge whose other end falls outside the current drill-down
 * focus level. Built by {@code
 * ComponentDiagramControllerImpl.focusTopology(...)}, which also decides
 * {@link #getTargets()}'s dedup granularity per {@link
 * ComponentPortRole#isFanOut()} - see that enum's javadoc.
 * <p>
 * {@link #getOwner()} is always a member of the focused {@code
 * ComponentDiagramTopology#getComponentTypes()} this instance travels
 * with; {@link #getTargets()} are real component types that may not be -
 * each one is resolved to its own level (its parent, for navigation
 * purposes) only when the user actually clicks through, via {@code
 * ComponentDiagramControllerServices.navigateToComponent(ViewKey, Class)}.
 */
public final class ComponentDiagramExternalPort {

	private final Class<?> owner;
	private final ComponentPortKind kind;
	private final ComponentPortRole role;
	private final Set<Class<?>> targets;

	/**
	 * Creates an external port; the target set is copied.
	 *
	 * @param owner the visible component the port is drawn on
	 * @param kind the kind of the underlying dependency
	 * @param role which side of the dependency the owner plays
	 * @param targets the real component types on the other end
	 */
	public ComponentDiagramExternalPort(final Class<?> owner, final ComponentPortKind kind,
			final ComponentPortRole role, final Set<Class<?>> targets) {
		this.owner = Objects.requireNonNull(owner, "owner");
		this.kind = Objects.requireNonNull(kind, "kind");
		this.role = Objects.requireNonNull(role, "role");
		this.targets = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(targets, "targets")));
	}

	/**
	 * Returns the component this port is drawn on.
	 *
	 * @return the visible component this port is drawn on
	 */
	public Class<?> getOwner() {
		return this.owner;
	}

	/**
	 * Returns the kind of the underlying dependency.
	 *
	 * @return the kind of the underlying dependency, never {@link ComponentPortKind#COMPONENT_TYPE}
	 */
	public ComponentPortKind getKind() {
		return this.kind;
	}

	/**
	 * Returns the side of the dependency the owner plays.
	 *
	 * @return which side of the underlying dependency {@link #getOwner()} plays
	 */
	public ComponentPortRole getRole() {
		return this.role;
	}

	/**
	 * Returns the components on the other end of the dependency.
	 *
	 * @return the real, possibly not-visible-anywhere component
	 *         types on the other end - one for a specific (non-fan-out)
	 *         role, one or more for a fan-out role (see {@link
	 *         ComponentPortRole#isFanOut()})
	 */
	public Set<Class<?>> getTargets() {
		return this.targets;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ComponentDiagramExternalPort)) {
			return false;
		}
		ComponentDiagramExternalPort that = (ComponentDiagramExternalPort) other;
		return this.owner.equals(that.owner) && this.kind == that.kind && this.role == that.role
				&& this.targets.equals(that.targets);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.owner, this.kind, this.role, this.targets);
	}
}
