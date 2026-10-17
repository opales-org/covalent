/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import org.opales.covalent.container.async.AsyncLifecycleServices;
import org.opales.covalent.container.autowiring.AutoWiringServices;
import org.opales.covalent.container.events.EventsLifecycleServices;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;

/**
 * Base {@link LifecycleTransitions} implementation providing the technical
 * cleanup shared by failure and removal: unpublishing every port a
 * component provided, tearing down its autowired ports, and passivating
 * what remains. Port-family-specific subclasses add their own hooks for the
 * other transitions.
 */
public abstract class AbstractLifecycleTransitions implements LifecycleTransitions {

	/** Creates a new instance with no internal services wired yet. */
	protected AbstractLifecycleTransitions() {
	}

	/** The autowiring service used to tear down autowired ports during cleanup. */
	protected AutoWiringServices autoWiringServices;

	/** The services lifecycle service used to unpublish and passivate provided services during cleanup. */
	protected ServicesLifecycleServices servicesLifecycleServices;

	/** The events lifecycle service used to unpublish and passivate provided event subscribers during cleanup. */
	protected EventsLifecycleServices eventsLifecycleServices;

	/** The parameters lifecycle service used to unpublish and passivate offered parameters during cleanup. */
	protected ParametersLifecycleServices parametersLifecycleServices;

	/** The async lifecycle service used to cancel a component's periodic tasks at passivation, removal and cleanup. */
	protected AsyncLifecycleServices asyncLifecycleServices;

	@Override
	public void preFail(final Class<?> componentType) {
		this.cleanupComponent(componentType);
	}

	/**
	 * Technical cleanup also accepts partially created components.
	 *
	 * @param componentType the component type to clean up
	 */
	protected void cleanupComponent(final Class<?> componentType) {
		this.asyncLifecycleServices.cancelPeriodicTasks(componentType);
		this.servicesLifecycleServices.unpublishProvidedServices(componentType);
		this.eventsLifecycleServices.unpublishProvidedSubscribers(componentType);
		this.parametersLifecycleServices.unpublishOfferedParameters(componentType);
		this.autoWiringServices.deleteUsedServices(componentType);
		this.autoWiringServices.deleteAccessedParameters(componentType);
		this.autoWiringServices.deleteConsumedEvents(componentType);
		this.autoWiringServices.deleteUsedSubscribers(componentType);
		this.servicesLifecycleServices.passivateProvidedServices(componentType);
		this.eventsLifecycleServices.passivateProducedEvents(componentType);
		this.parametersLifecycleServices.passivateOfferedParameters(componentType);
		this.autoWiringServices.deleteProvidedServices(componentType);
		this.autoWiringServices.deleteProducedEvents(componentType);
		this.autoWiringServices.deleteOfferedParameters(componentType);
	}

	@Override
	public void postFail(final Class<?> componentType) {
	}

	/**
	 * Sets the internal service used to tear down autowired ports during
	 * cleanup.
	 *
	 * @param autoWiringServices the autowiring service
	 */
	public void setAutoWiringServices(final AutoWiringServices autoWiringServices) {
		this.autoWiringServices = autoWiringServices;
	}

	/**
	 * Sets the internal service used to unpublish and passivate provided
	 * services during cleanup.
	 *
	 * @param servicesLifecycleServices the services lifecycle service
	 */
	public void setServicesLifecycleServices(final ServicesLifecycleServices servicesLifecycleServices) {
		this.servicesLifecycleServices = servicesLifecycleServices;
	}

	/**
	 * Sets the internal service used to unpublish and passivate provided
	 * event subscribers during cleanup.
	 *
	 * @param eventsLifecycleServices the events lifecycle service
	 */
	public void setEventsLifecycleServices(final EventsLifecycleServices eventsLifecycleServices) {
		this.eventsLifecycleServices = eventsLifecycleServices;
	}

	/**
	 * Sets the internal service used to unpublish and passivate offered
	 * parameters during cleanup.
	 *
	 * @param parametersLifecycleServices the parameters lifecycle service
	 */
	public void setParametersLifecycleServices(final ParametersLifecycleServices parametersLifecycleServices) {
		this.parametersLifecycleServices = parametersLifecycleServices;
	}

	/**
	 * Sets the internal service used to cancel the periodic tasks of a
	 * component being passivated, removed or cleaned up.
	 *
	 * @param asyncLifecycleServices the async lifecycle service
	 */
	public void setAsyncLifecycleServices(final AsyncLifecycleServices asyncLifecycleServices) {
		this.asyncLifecycleServices = asyncLifecycleServices;
	}

}
