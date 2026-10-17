/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.autowiring.impl;

import org.opales.covalent.container.autowiring.AutoWiringManager;
import org.opales.covalent.container.autowiring.AutoWiringServices;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;

/**
 * Internal component backing {@link AutoWiringManager}/
 * {@link AutoWiringServices}: creates and tears down a component's
 * automatically-wired ports (provided/used services, produced/consumed
 * events, offered/accessed parameters, used subscribers) whenever it is
 * declared or removed, delegating to a dedicated item per port family
 * ({@link ServicesAutoWiringImpl}, {@link ParametersAutoWiringImpl},
 * {@link EventsAutoWiringImpl}), all sharing the same
 * {@link FieldInjectionImpl} for reflective field injection.
 */
@Implementation
public class AutoWiringManagerImpl implements AutoWiringManager, AutoWiringServices {

	@Item
	private final FieldInjectionImpl fieldInjection;

	@Item
	private final ServicesAutoWiringImpl servicesAutoWiring;

	@Item
	private final ParametersAutoWiringImpl parametersAutoWiring;

	@Item
	private final EventsAutoWiringImpl eventsAutoWiring;

	/** Creates the four per-port-family items and wires their shared field injector. */
	public AutoWiringManagerImpl() {
		this.fieldInjection = new FieldInjectionImpl();
		this.servicesAutoWiring = new ServicesAutoWiringImpl();
		this.parametersAutoWiring = new ParametersAutoWiringImpl();
		this.eventsAutoWiring = new EventsAutoWiringImpl();
		this.servicesAutoWiring.setFieldInjection(this.fieldInjection);
		this.parametersAutoWiring.setFieldInjection(this.fieldInjection);
		this.eventsAutoWiring.setFieldInjection(this.fieldInjection);
	}

	/**
	 * Sets the internal service used by the services autowiring item.
	 *
	 * @param servicesManagerServices the services manager service
	 */
	public void setServicesManagerServices(final ServicesManagerServices servicesManagerServices) {
		this.servicesAutoWiring.setServicesManagerServices(servicesManagerServices);
	}

	/**
	 * Sets the internal service used by the events autowiring item.
	 *
	 * @param eventsManagerServices the events manager service
	 */
	public void setEventManagerServices(final EventsManagerServices eventsManagerServices) {
		this.eventsAutoWiring.setEventManagerServices(eventsManagerServices);
	}

	/**
	 * Sets the internal service used by the parameters autowiring item.
	 *
	 * @param parametersManagerServices the parameters manager service
	 */
	public void setParametersManagerServices(final ParametersManagerServices parametersManagerServices) {
		this.parametersAutoWiring.setParametersManagerServices(parametersManagerServices);
	}

	/**
	 * Sets the internal service used to read the metamodel's port
	 * information, propagated to every per-port-family item.
	 *
	 * @param metamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.servicesAutoWiring.setMetamodelPortReadServices(metamodelPortReadServices);
		this.parametersAutoWiring.setMetamodelPortReadServices(metamodelPortReadServices);
		this.eventsAutoWiring.setMetamodelPortReadServices(metamodelPortReadServices);
	}

	/**
	 * Sets the internal service used to look up ports in the registry,
	 * propagated to every per-port-family item.
	 *
	 * @param portRegistryServices the port registry service
	 */
	public void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.servicesAutoWiring.setPortRegistryServices(portRegistryServices);
		this.parametersAutoWiring.setPortRegistryServices(portRegistryServices);
		this.eventsAutoWiring.setPortRegistryServices(portRegistryServices);
	}

	@Override
	public void createProvidedServices(final Class<?> componentType) {
		this.servicesAutoWiring.createProvidedServices(componentType);
	}

	@Override
	public void deleteProvidedServices(final Class<?> componentType) {
		this.servicesAutoWiring.deleteProvidedServices(componentType);
	}

	@Override
	public <SERVICES> void createUsedServices(final Class<?> componentType) {
		this.servicesAutoWiring.createUsedServices(componentType);
	}

	@Override
	public <SERVICES> void deleteUsedServices(final Class<?> componentType) {
		this.servicesAutoWiring.deleteUsedServices(componentType);
	}

	@Override
	public <SUBSCRIBE> void createUsedSubscribers(final Class<?> componentType) {
		this.eventsAutoWiring.createUsedSubscribers(componentType);
	}

	@Override
	public <SUBSCRIBE> void deleteUsedSubscribers(final Class<?> componentType) {
		this.eventsAutoWiring.deleteUsedSubscribers(componentType);
	}

	@Override
	public <EVENTS> void createProducedEvents(final Class<?> componentType) {
		this.eventsAutoWiring.createProducedEvents(componentType);
	}

	@Override
	public <EVENTS> void deleteProducedEvents(final Class<?> componentType) {
		this.eventsAutoWiring.deleteProducedEvents(componentType);
	}

	@Override
	public void createConsumedEvents(final Class<?> componentType) {
		this.eventsAutoWiring.createConsumedEvents(componentType);
	}

	@Override
	public void deleteConsumedEvents(final Class<?> componentType) {
		this.eventsAutoWiring.deleteConsumedEvents(componentType);
	}

	@Override
	public void createOfferedParameters(final Class<?> componentType) {
		this.parametersAutoWiring.createOfferedParameters(componentType);
	}

	@Override
	public void deleteOfferedParameters(final Class<?> componentType) {
		this.parametersAutoWiring.deleteOfferedParameters(componentType);
	}

	@Override
	public <PARAMETERS> void createAccessedParameters(final Class<?> componentType) {
		this.parametersAutoWiring.createAccessedParameters(componentType);
	}

	@Override
	public <PARAMETERS> void deleteAccessedParameters(final Class<?> componentType) {
		this.parametersAutoWiring.deleteAccessedParameters(componentType);
	}

}
