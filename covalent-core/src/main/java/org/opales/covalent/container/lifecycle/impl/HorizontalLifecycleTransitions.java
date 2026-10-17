/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

/**
 * {@link LifecycleTransitions} implementation for the "horizontal" start
 * mode: output ports (used services, consumed events, used subscribers) are
 * created during pre-activation rather than pre-initialize (contrast with
 * {@link VerticalLifecycleTransitions}). This is required because a
 * component cannot use services during its own initialization in horizontal
 * mode, as services provided by other components deployed alongside it are
 * not necessarily ready yet.
 * <p>
 * Both modes share the constraint that provided ports (provided services and
 * produced events) are activated/published on activation and
 * unpublished/passivated on passivation, to support chaining multiple
 * activation/passivation sequences on the same component.
 * <p>
 * Horizontal mode trades this initialization-time restriction for faster
 * activation once every component has already been initialized, whereas
 * vertical mode allows a component to use the ports of a previously started
 * component during its own initialization at the cost of a stricter
 * initialization order.
 */
public class HorizontalLifecycleTransitions extends AbstractLifecycleTransitions implements LifecycleTransitions {

	/**
	 * Creates a new instance with no internal services wired yet.
	 */
	public HorizontalLifecycleTransitions() {
	}

	/** No-op in horizontal mode. */
	@Override
	public void postDeploy(final Class<?> componentType) {

	}

	/** No-op in horizontal mode. */
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

	/** Creates the component's accessed parameters port before initialization runs. */
	@Override
	public void preInitialize(final Class<?> componentType) {

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
	 * Creates the component's used services, consumed events and used
	 * subscribers ports right before activation - the defining trait of
	 * horizontal mode, deferring output ports past initialization since
	 * other components' provided ports may not be ready until then.
	 */
	@Override
	public void preActivate(final Class<?> componentType) {
		// USED SERVICES
		this.autoWiringServices.createUsedServices(componentType);

		// CONSUMED EVENTS with automatic subscribe
		this.autoWiringServices.createConsumedEvents(componentType);

		// USED SUBSCRIBERS
		this.autoWiringServices.createUsedSubscribers(componentType);

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

	/** No-op in horizontal mode. */
	@Override
	public void postRemove(final Class<?> componentType) {

	}

	/** No-op in horizontal mode. */
	@Override
	public void preUndeploy(final Class<?> componentType) {

	}

}
