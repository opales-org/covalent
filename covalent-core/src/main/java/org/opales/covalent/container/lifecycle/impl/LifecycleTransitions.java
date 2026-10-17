/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

/**
 * Internal hooks invoked by {@code LifecycleManagerImpl} around each of a
 * component's lifecycle state transitions (see
 * {@link org.opales.covalent.container.lifecycle.ComponentState}), each as
 * a "pre" hook (before the transition's own callback runs) and a "post"
 * hook (after it completes). {@link AbstractLifecycleTransitions} provides
 * the shared cleanup behavior; port-family-specific implementations plug
 * their own publication/subscription/wiring logic into these hooks.
 */
public interface LifecycleTransitions {

	/**
	 * Invoked before a component is torn down after a failure - triggers the
	 * same technical cleanup as removal.
	 *
	 * @param componentType the failed component's type
	 */
	void preFail(final Class<?> componentType);

	/**
	 * Invoked after a component has been torn down after a failure.
	 *
	 * @param componentType the failed component's type
	 */
	void postFail(final Class<?> componentType);

	/**
	 * Invoked after a component type is deployed (known to the container,
	 * not yet instantiated).
	 *
	 * @param componentType the deployed component's type
	 */
	void postDeploy(final Class<?> componentType);

	/**
	 * Invoked before a component is instantiated.
	 *
	 * @param componentType the component type being instantiated
	 */
	void preInstantiate(final Class<?> componentType);

	/**
	 * Invoked after a component has been instantiated.
	 *
	 * @param componentType the instantiated component's type
	 */
	void postInstantiate(final Class<?> componentType);

	/**
	 * Invoked before a component's initialization callback runs.
	 *
	 * @param componentType the component type being initialized
	 */
	void preInitialize(final Class<?> componentType);

	/**
	 * Invoked after a component's initialization callback has run.
	 *
	 * @param componentType the initialized component's type
	 */
	void postInitialize(final Class<?> componentType);

	/**
	 * Invoked before a component's activation callback runs.
	 *
	 * @param componentType the component type being activated
	 */
	void preActivate(final Class<?> componentType);

	/**
	 * Invoked after a component's activation callback has run.
	 *
	 * @param componentType the activated component's type
	 */
	void postActivate(final Class<?> componentType);

	/**
	 * Invoked before a component's passivation callback runs.
	 *
	 * @param componentType the component type being passivated
	 */
	void prePassivate(final Class<?> componentType);

	/**
	 * Invoked after a component's passivation callback has run.
	 *
	 * @param componentType the passivated component's type
	 */
	void postPassivate(final Class<?> componentType);

	/**
	 * Invoked before a component's removal callback runs.
	 *
	 * @param componentType the component type being removed
	 */
	void preRemove(final Class<?> componentType);

	/**
	 * Invoked after a component's removal callback has run.
	 *
	 * @param componentType the removed component's type
	 */
	void postRemove(final Class<?> componentType);

	/**
	 * Invoked before a component type is undeployed (no longer known to the
	 * container).
	 *
	 * @param componentType the component type being undeployed
	 */
	void preUndeploy(final Class<?> componentType);

}
