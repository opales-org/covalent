/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A best-effort snapshot of the managed component dependency graph, computed
 * once at the very start of
 * {@link org.opales.covalent.container.CovalentContainer#start()} over
 * whichever managed components were already deployed by then.
 *
 * Nodes are the top-level deployable component types (the classes accepted
 * by {@link LifecycleServices#deployComponents(Class[])}) and their composed
 * descendants; a dependency edge is derived
 * from every service, event, and parameter port declared by a component's
 * implementation or any of its items, pointing from the port's user to the
 * port's provider/producer/offeror. Raw components are never nodes: wiring
 * their own dependencies, cycles included, through
 * {@code RawComponentsServices} directly is part of the freedom they trade
 * the managed model's guardrails for. A component holding a direct Java
 * reference to another component's implementation, bypassing its proxy
 * entirely, stays invisible to the graph either way.
 *
 * A graph instance is immutable and reflects the state of the covered
 * components at the moment it was computed; it is never updated afterward,
 * including for components deployed after {@code start()} began.
 *
 * @see org.opales.covalent.container.CovalentContainerParameters#setCircularDependencyChecking(boolean)
 */
public interface DependencyGraph {

	/**
	 * Returns every covered component type, in dependency order wherever a
	 * dependency order can be established: a type never appears before a
	 * type it depends on, and a composite never appears before its composed
	 * children, unless the types involved belong to the same circular
	 * dependency - by definition, no order can satisfy that. Covalent does
	 * not drop a component from this list for belonging to a cycle: it still
	 * needs a position to start it in, even a best-effort one, and refusing
	 * to say where would only trade one problem for another. Use
	 * {@link #hasCircularDependencies()} and
	 * {@link #getCircularDependencies()} to find which components those are
	 * and act on it - report it, or fail via
	 * {@link org.opales.covalent.container.CovalentContainerParameters#getStructuralRulesChecking()
	 * strict} verification - rather than relying on this list to hide them.
	 *
	 * @return every covered component type, in dependency order except among
	 *         components that share a circular dependency
	 */
	List<Class<?>> getStartOrder();

	/**
	 * Reports whether the covered components contain at least one circular
	 * dependency.
	 *
	 * @return {@code true} when at least one circular dependency was found
	 */
	boolean hasCircularDependencies();

	/**
	 * Returns every circular dependency found among the covered components.
	 * Each element is one cycle, listed as the component types encountered
	 * along it in traversal order (the cycle closes from the last type back
	 * to the first). Empty when {@link #hasCircularDependencies()} is
	 * {@code false}.
	 * <p>
	 * Cycles take composition into account, since a composite is started
	 * only after all of its composed children: depending on a component held
	 * by a composite means waiting for that whole composite. A cycle may
	 * therefore go through composites. Read each step {@code X -> Y} of a
	 * cycle as follows: when {@code X} composes {@code Y}, it is a
	 * composition step; otherwise, {@code X} depends on {@code Y} or on one
	 * of its composed descendants. For example, with {@code A} composing
	 * {@code A1} and {@code A2}, {@code B} composing {@code B1} and
	 * {@code B2}, {@code A1} depending on {@code B2} and {@code B1} depending
	 * on {@code A2}, the cycle is {@code A -> A1 -> B -> B1 -> A}. A composed
	 * component using a port of a composite that holds it does not create a
	 * cycle.
	 *
	 * @return every circular dependency found, each as its component types
	 *         in traversal order
	 */
	List<List<Class<?>>> getCircularDependencies();

	/**
	 * Returns every direct dependency edge among the covered components: for
	 * each covered component type, the set of other covered component types
	 * it directly depends on (a used service, a consumed event, or an
	 * accessed parameter whose interface that other component's
	 * implementation or items provide, produce, or offer). Together with
	 * {@link #getCompositions()}, this is the structural information
	 * {@link #getStartOrder()} and {@link #getCircularDependencies()} are
	 * derived from - use it to render the graph itself (for example, a
	 * Supervisor's dependency diagram), not just its order or its cycles.
	 * <p>
	 * A component type present in {@link #getStartOrder()} but absent as a
	 * key here (or mapped to an empty set) has no direct dependency among the
	 * covered components - not that it was left uncovered.
	 *
	 * @return for each covered component type, the other covered component
	 *         types it directly depends on
	 */
	Map<Class<?>, Set<Class<?>>> getDependencies();

	/**
	 * Returns the composition among the covered components: each composite
	 * and its direct composed children, apart from the dependencies through
	 * ports. {@link #getStartOrder()} and {@link #getCircularDependencies()}
	 * are computed from both: a composite comes after its composed children,
	 * and depending on a component held by a composite means depending on
	 * that whole composite.
	 *
	 * @return for each covered composite, its direct composed children; a
	 *         component that composes nothing is absent
	 */
	Map<Class<?>, Set<Class<?>>> getCompositions();

}
