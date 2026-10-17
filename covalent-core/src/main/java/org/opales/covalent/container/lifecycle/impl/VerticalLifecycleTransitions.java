/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

/**
 * {@link LifecycleTransitions} implementation for the "vertical" start
 * mode: output ports (used services, consumed events, used subscribers) are
 * created during pre-initialize rather than pre-activation (contrast with
 * {@link HorizontalLifecycleTransitions}). This lets a component use the
 * ports of a previously started component while it initializes itself,
 * since components are started one at a time and fully activated before the
 * next one begins.
 * <p>
 * Both modes share the constraint that provided ports (provided services and
 * produced events) are activated/published on activation and
 * unpublished/passivated on passivation, to support chaining multiple
 * activation/passivation sequences on the same component.
 * <p>
 * Vertical mode trades a slower, strictly-ordered startup for that ability,
 * whereas horizontal mode activates faster once every component has already
 * been initialized but forbids using services during initialization.
 */
public class VerticalLifecycleTransitions extends AbstractLifecycleTransitions implements LifecycleTransitions {

	/** Creates a new vertical-mode transitions strategy. */
	public VerticalLifecycleTransitions() {
	}

	/** No-op in vertical mode. */
	@Override
	public void postDeploy(final Class<?> componentType) {

	}

	/** No-op in vertical mode. */
	@Override
	public void preInstantiate(final Class<?> componentType) {

	}

	/**
	 * Creates the component's offered parameters port, then activates and
	 * publishes it, so parameters are available as soon as the component
	 * exists.
	 */
	@Override
	public void postInstantiate(final Class<?> componentType) {

		// OFFERED PARAMETERS
		this.autoWiringServices.createOfferedParameters(componentType);
		this.parametersLifecycleServices.activateOfferedParameters(componentType);
		this.parametersLifecycleServices.publishOfferedParameters(componentType);

	}

	/**
	 * Creates every one of the component's output ports (used services,
	 * consumed events, used subscribers, accessed parameters) before
	 * initialization runs - the defining trait of vertical mode, letting a
	 * component use the ports of a previously started component while it
	 * initializes itself.
	 */
	@Override
	public void preInitialize(final Class<?> componentType) {

		// USED SERVICES
		this.autoWiringServices.createUsedServices(componentType);

		// CONSUMED EVENTS with automatic subscribe
		this.autoWiringServices.createConsumedEvents(componentType);

		// USED SUBSCRIBERS
		this.autoWiringServices.createUsedSubscribers(componentType);

		// ACCESSED PARAMETERS
		this.autoWiringServices.createAccessedParameters(componentType);
	}

	/**
	 * Creates the component's produced events and provided services ports
	 * once initialization has completed, so other components may subscribe
	 * to or use them starting at activation.
	 */
	@Override
	public void postInitialize(final Class<?> componentType) {

		// PRODUCED EVENTS
		// allow to subscribe during activation transition
		this.autoWiringServices.createProducedEvents(componentType);

		// PROVIDED SERVICES
		this.autoWiringServices.createProvidedServices(componentType);

	}

	/**
	 * No-op in vertical mode: output ports are already created by
	 * {@link #preInitialize(Class)}.
	 */
	@Override
	public void preActivate(final Class<?> componentType) {

	}

	/**
	 * Activates and publishes the component's provided services and
	 * produced events (with their subscribe port) so they become usable by
	 * other components.
	 */
	@Override
	public void postActivate(final Class<?> componentType) {

		// PROVIDED SERVICES
		this.servicesLifecycleServices.activateProvidedServices(componentType);
		this.servicesLifecycleServices.publishProvidedServices(componentType);

		// PRODUCED EVENTS
		this.eventsLifecycleServices.activateProducedEvents(componentType);
		this.eventsLifecycleServices.publishProvidedSubscribers(componentType);

	}

	/**
	 * Unpublishes the component's provided services and produced
	 * subscribers before passivation, so they stop being reachable by other
	 * components while still allowing a later re-activation.
	 */
	@Override
	public void prePassivate(final Class<?> componentType) {

		// PERIODIC TASKS: none may start once passivation has begun
		this.asyncLifecycleServices.cancelPeriodicTasks(componentType);

		// PROVIDED SERVICES
		this.servicesLifecycleServices.unpublishProvidedServices(componentType);

		// PRODUCED EVENTS
		this.eventsLifecycleServices.unpublishProvidedSubscribers(componentType);

	}

	/** Passivates the component's provided services and produced events. */
	@Override
	public void postPassivate(final Class<?> componentType) {

		// PROVIDED SERVICES
		this.servicesLifecycleServices.passivateProvidedServices(componentType);

		// PRODUCED EVENTS
		this.eventsLifecycleServices.passivateProducedEvents(componentType);

	}

	/**
	 * Tears down every autowired port created for this component (provided
	 * services, produced events, used services, consumed events, accessed
	 * parameters) and unpublishes/passivates its offered parameters, ahead
	 * of the component being fully removed.
	 */
	@Override
	public void preRemove(final Class<?> componentType) {

		// PERIODIC TASKS
		this.asyncLifecycleServices.cancelPeriodicTasks(componentType);

		// PROVIDED SERVICES
		this.autoWiringServices.deleteProvidedServices(componentType);

		// PRODUCED EVENTS
		this.autoWiringServices.deleteProducedEvents(componentType);

		// USED SERVICES
		this.autoWiringServices.deleteUsedServices(componentType);

		// CONSUMED EVENTS
		this.autoWiringServices.deleteConsumedEvents(componentType);

		// ACCESSED PARAMETERS
		this.autoWiringServices.deleteAccessedParameters(componentType);

		// OFFERED PARAMETERS
		this.parametersLifecycleServices.unpublishOfferedParameters(componentType);
		this.parametersLifecycleServices.passivateOfferedParameters(componentType);
		this.autoWiringServices.deleteOfferedParameters(componentType);

	}

	/** No-op in vertical mode. */
	@Override
	public void postRemove(final Class<?> componentType) {

	}

	/** No-op in vertical mode. */
	@Override
	public void preUndeploy(final Class<?> componentType) {

	}

}
