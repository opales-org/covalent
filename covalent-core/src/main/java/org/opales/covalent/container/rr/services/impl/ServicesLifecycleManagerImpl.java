/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services.impl;

import java.util.Set;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.rr.common.RRLifecycleManagerImpl;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;

/**
 * Applies lifecycle transitions (activate/passivate/publish/unpublish) to
 * every services interface provided by a given component type, delegating
 * the actual per-interface work to the collaborating
 * {@link ServicesManagerImpl}. Wired into {@link ServicesManagerImpl} via
 * {@link #setServicesManagerImpl} and, as its {@link RRLifecycleManagerImpl}
 * base, into {@link org.opales.covalent.container.rr.common.RRManagerImpl}.
 */
@Item
public class ServicesLifecycleManagerImpl extends RRLifecycleManagerImpl implements ServicesLifecycleServices {

	private static Logger log = Logger.getLogger(ServicesLifecycleManagerImpl.class.getName());

	/** Creates a new services lifecycle manager. */
	public ServicesLifecycleManagerImpl() {
	}

	/** The manager this lifecycle manager delegates per-interface work to. */
	private ServicesManagerImpl servicesManagerImpl;

	/**
	 * Sets the manager this lifecycle manager delegates per-interface work
	 * to.
	 *
	 * @param servicesManagerImpl the collaborating services manager
	 */
	public void setServicesManagerImpl(final ServicesManagerImpl servicesManagerImpl) {
		this.servicesManagerImpl = servicesManagerImpl;
	}

	/**
	 * Activates every services interface provided by the given component
	 * type that is not already activated.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType the provider component type to activate
	 */
	@Override
	public <SERVICES> void activateProvidedServices(final Class<?> componentType) {
		Set<Class<?>> allProvidedServices = this.MetamodelPortReadServices.getProvidedServices(componentType);
		for (Class<?> services : allProvidedServices) {
			@SuppressWarnings("unchecked")
			Class<SERVICES> servicesInterface = (Class<SERVICES>) services;
			if (!this.servicesManagerImpl.isActivated(servicesInterface)) {
				log.fine("Automatic activation of provided services " + servicesInterface.getSimpleName());
				this.servicesManagerImpl.activateServices(servicesInterface);
			}
		}
	}

	/**
	 * Passivates every services interface provided by the given component
	 * type that is currently provided and activated.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType the provider component type to passivate
	 */
	@Override
	public <SERVICES> void passivateProvidedServices(final Class<?> componentType) {
		Set<Class<?>> allProvidedServices = this.MetamodelPortReadServices.getProvidedServices(componentType);
		for (Class<?> services : allProvidedServices) {
			@SuppressWarnings("unchecked")
			Class<SERVICES> servicesInterface = (Class<SERVICES>) services;
			if (this.servicesManagerImpl.isServicesProvided(servicesInterface)
					&& this.servicesManagerImpl.isActivated(servicesInterface)) {
				log.fine("Automatic passivation of provided services " + servicesInterface.getSimpleName());
				this.servicesManagerImpl.passivateServices(servicesInterface);
			}
		}
	}

	/**
	 * Publishes (binds in the directory) every services interface provided
	 * by the given component type that is not already bound.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType the provider component type to publish
	 */
	@Override
	public <SERVICES> void publishProvidedServices(final Class<?> componentType) {
		Set<Class<?>> allProvidedServices = this.MetamodelPortReadServices.getProvidedServices(componentType);
		for (Class<?> services : allProvidedServices) {
			@SuppressWarnings("unchecked")
			Class<SERVICES> servicesInterface = (Class<SERVICES>) services;
			if (!this.namingServices.isServicesBound(servicesInterface)) {
				SERVICES servicesProvider = this.servicesManagerImpl.getServicesProvider(servicesInterface);
				log.fine("Publication of provided services " + servicesInterface.getSimpleName());
				this.namingServices.bindServices(servicesInterface, servicesProvider);
			}
		}
	}

	/**
	 * Unpublishes (unbinds from the directory) every services interface
	 * provided by the given component type that is currently bound.
	 *
	 * @param <SERVICES>    services interface type
	 * @param componentType the provider component type to unpublish
	 */
	@Override
	public <SERVICES> void unpublishProvidedServices(final Class<?> componentType) {
		Set<Class<?>> allProvidedServices = this.MetamodelPortReadServices.getProvidedServices(componentType);
		for (Class<?> services : allProvidedServices) {
			@SuppressWarnings("unchecked")
			Class<SERVICES> servicesInterface = (Class<SERVICES>) services;
			if (this.namingServices.isServicesBound(servicesInterface)) {
				log.fine("Unpublication of provided services " + servicesInterface.getSimpleName());
				this.namingServices.unbindServices(servicesInterface);
			}
		}
	}

}
