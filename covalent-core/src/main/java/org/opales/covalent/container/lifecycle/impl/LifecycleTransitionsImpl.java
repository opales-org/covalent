/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import org.opales.covalent.container.async.AsyncLifecycleServices;
import org.opales.covalent.container.autowiring.AutoWiringServices;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.events.EventsLifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;

/**
 * Default implementation of {@link LifecycleTransitions}: delegates every
 * transition hook to a configured {@link AbstractLifecycleTransitions}
 * instance, chosen according to the container's {@link StartMode}
 * ({@link HorizontalLifecycleTransitions} or
 * {@link VerticalLifecycleTransitions}).
 */
@Item
public class LifecycleTransitionsImpl implements LifecycleTransitions {

	@UsedServices
	private AutoWiringServices useAutoWiringServices;

	@UsedServices
	private ServicesLifecycleServices useServicesLifecycleServices;

	@UsedServices
	private EventsLifecycleServices useEventsLifecycleServices;

	@UsedServices
	private ParametersLifecycleServices useParametersLifecycleServices;

	@UsedServices
	private AsyncLifecycleServices useAsyncLifecycleServices;

	private LifecycleTransitions transitions;

	/**
	 * Creates a new lifecycle transitions component.
	 */
	public LifecycleTransitionsImpl() {
	}

	/**
	 * Called after dependency injection, including vertical initialization during bootstrap.
	 * Selects and configures the transitions delegate matching the given start mode.
	 *
	 * @param startMode ordering strategy for startup transitions across components
	 */
	public void initialize(final StartMode startMode) {
		final AbstractLifecycleTransitions configuredTransitions;
		switch (startMode) {
		case horizontal:
			configuredTransitions = new HorizontalLifecycleTransitions();
			break;
		case vertical:
			configuredTransitions = new VerticalLifecycleTransitions();
			break;
		default:
			throw new IllegalStateException("unknown start mode: " + startMode);
		}
		configuredTransitions.setAutoWiringServices(this.useAutoWiringServices);
		configuredTransitions.setServicesLifecycleServices(this.useServicesLifecycleServices);
		configuredTransitions.setEventsLifecycleServices(this.useEventsLifecycleServices);
		configuredTransitions.setParametersLifecycleServices(this.useParametersLifecycleServices);
		configuredTransitions.setAsyncLifecycleServices(this.useAsyncLifecycleServices);
		this.transitions = configuredTransitions;
	}

	@Override
	public void postDeploy(final Class<?> componentType) {
		this.transitions.postDeploy(componentType);
	}

	@Override
	public void preFail(final Class<?> componentType) {
		this.transitions.preFail(componentType);
	}

	@Override
	public void postFail(final Class<?> componentType) {
		this.transitions.postFail(componentType);
	}

	@Override
	public void preInstantiate(final Class<?> componentType) {
		this.transitions.preInstantiate(componentType);
	}

	@Override
	public void postInstantiate(final Class<?> componentType) {
		this.transitions.postInstantiate(componentType);
	}

	@Override
	public void preInitialize(final Class<?> componentType) {
		this.transitions.preInitialize(componentType);
	}

	@Override
	public void postInitialize(final Class<?> componentType) {
		this.transitions.postInitialize(componentType);
	}

	@Override
	public void preActivate(final Class<?> componentType) {
		this.transitions.preActivate(componentType);
	}

	@Override
	public void postActivate(final Class<?> componentType) {
		this.transitions.postActivate(componentType);
	}

	@Override
	public void prePassivate(final Class<?> componentType) {
		this.transitions.prePassivate(componentType);
	}

	@Override
	public void postPassivate(final Class<?> componentType) {
		this.transitions.postPassivate(componentType);
	}

	@Override
	public void preRemove(final Class<?> componentType) {
		this.transitions.preRemove(componentType);
	}

	@Override
	public void postRemove(final Class<?> componentType) {
		this.transitions.postRemove(componentType);
	}

	@Override
	public void preUndeploy(final Class<?> componentType) {
		this.transitions.preUndeploy(componentType);
	}
}
