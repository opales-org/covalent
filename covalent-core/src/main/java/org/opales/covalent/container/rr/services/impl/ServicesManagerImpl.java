/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services.impl;

import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.rr.common.RRManagerImpl;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;
import org.opales.covalent.container.rr.services.ServicesManager;
import org.opales.covalent.container.rr.services.ServicesManagerEvents;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Services-flavor instantiation of the generic {@link RRManagerImpl}
 * machinery: implements {@link ServicesManager} (the component-model
 * marker) and {@link ServicesManagerServices} (the services port exposed
 * to other managers), and supplies the collaborating {@link ServicesProxyFactory}
 * and {@link ServicesLifecycleManagerImpl} used to create proxies and apply
 * lifecycle transitions to services ports.
 */
@Implementation
public class ServicesManagerImpl extends RRManagerImpl
		implements ServicesManager, ServicesManagerServices {

	/** The events port instrumented and lifecycle notifications are sent to. */
	@ProducedEvents
	private ServicesManagerEvents produceServicesManagerEvents;

	/** The collaborating lifecycle manager for the services flavor. */
	@Item
	private final ServicesLifecycleManagerImpl servicesLifecycleManagerImpl;

	/** The collaborating proxy factory for the services flavor. */
	private final ServicesProxyFactory servicesProxyFactory;

	/**
	 * Creates a new services manager, wiring its collaborating proxy
	 * factory and lifecycle manager.
	 */
	public ServicesManagerImpl() {
		super("Services");
		this.servicesProxyFactory = new ServicesProxyFactory();
		this.servicesProxyFactory.setProducedServiceManagerEvents(() -> this.produceServicesManagerEvents);
		super.setProxyFactory(this.servicesProxyFactory);
		this.servicesLifecycleManagerImpl = new ServicesLifecycleManagerImpl();
		super.setLifecycleManager(this.servicesLifecycleManagerImpl);
		this.servicesLifecycleManagerImpl.setServicesManagerImpl(this);
	}

	/**
	 * Returns the collaborating lifecycle manager for the services flavor.
	 *
	 * @return the services lifecycle manager
	 */
	public ServicesLifecycleManagerImpl getServicesLifecycleManagerImpl() {
		return this.servicesLifecycleManagerImpl;
	}

	/**
	 * Returns the collaborating lifecycle manager, exposed through its
	 * {@link ServicesLifecycleServices} port.
	 *
	 * @return the services lifecycle services port
	 */
	public ServicesLifecycleServices getServicesLifecycleServices() {
		return this.servicesLifecycleManagerImpl;
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getProviderToServices() {
		return this.getComponentTypeToProvided();
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getServiceToUsers() {
		return this.getProvidedToClients();
	}

	@Override
	public <SERVICES> void createProvidedServices(final Class<SERVICES> servicesInterface) {
		RuntimeCheck.notNull(servicesInterface, "servicesInterface");
		Class<?> componentType = this.MetamodelPortReadServices.getComponentTypeForProvidedServices(servicesInterface);
		this.createProvidedServices(componentType, servicesInterface);
	}

	@Override
	public <SERVICES> void createProvidedServices(final Class<?> componentType,
			final Class<SERVICES> servicesInterface) {
		super.createProvidedRR(componentType, servicesInterface);
	}

	@Override
	public <SERVICES> SERVICES getServicesProvider(final Class<SERVICES> servicesInterface) {
		return super.getProvider(servicesInterface);
	}

	@Override
	public <SERVICES> void deleteProvidedServices(final Class<SERVICES> servicesInterface) {
		super.deleteProvider(servicesInterface);
	}

	@Override
	public boolean isServicesUsed(final Class<?> componentType, final Class<?> servicesInterface) {
		return this.isUsed(componentType, servicesInterface);
	}

	@Override
	public boolean isServicesProvided(final Class<?> servicesInterface) {
		return super.isProvided(servicesInterface);
	}

	@Override
	public <SERVICES> void activateServices(final Class<SERVICES> servicesInterface) {
		super.activateRR(servicesInterface);
	}

	@Override
	public <SERVICES> void passivateServices(final Class<SERVICES> servicesInterface) {
		super.passivateRR(servicesInterface);
	}

	@Override
	public <SERVICES> boolean isActivated(final Class<SERVICES> servicesInterface) {
		return super.isActivatedRR(servicesInterface);
	}

	@Override
	public <SERVICES> SERVICES createUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		return super.createClientProxy(servicesUserComponentType, servicesInterface);
	}

	@Override
	public <SERVICES> SERVICES reconnectUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		return super.reconnectClientProxy(servicesUserComponentType, servicesInterface);
	}

	@Override
	public <SERVICES> void deleteUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		super.deleteClientProxy(servicesUserComponentType, servicesInterface);
	}

	/**
	 * Resolves the component type that currently provides the given
	 * services interface.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the provided services interface
	 * @return the providing component type
	 */
	@Override
	protected <SERVICES> Class<?> getComponentTypeForProvider(final Class<SERVICES> servicesInterface) {
		return this.MetamodelPortReadServices.getComponentTypeForProvidedServices(servicesInterface);
	}

	/**
	 * Creates a new provider-side proxy for the given services interface
	 * via the collaborating proxy factory.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the interface to create a proxy for
	 * @return the new provider-side proxy
	 */
	@Override
	protected <SERVICES> SERVICES newProxy(final Class<SERVICES> servicesInterface) {
		return this.servicesProxyFactory.newProvidedServicesProxy(servicesInterface);
	}

	/**
	 * True if the given services interface currently has a provider bound
	 * in the directory.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the interface to check
	 * @return {@code true} if bound to a provider
	 */
	@Override
	protected <SERVICES> boolean isBound(final Class<SERVICES> servicesInterface) {
		return this.namingServices.isServicesBound(servicesInterface);
	}

	/**
	 * Looks up the current provider-side proxy for the given services
	 * interface in the directory.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the interface to look up
	 * @return the resolved provider-side proxy
	 */
	@Override
	protected <SERVICES> SERVICES lookup(final Class<SERVICES> servicesInterface) {
		return this.namingServices.lookupServices(servicesInterface);
	}

	/**
	 * Creates a temporary surrogate proxy for the given services interface,
	 * used while no real provider is declared yet.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param servicesInterface the interface to create a surrogate for
	 * @return the new surrogate proxy
	 */
	@Override
	protected <SERVICES> SERVICES newSurrogateProxy(final Class<SERVICES> servicesInterface) {
		return this.servicesProxyFactory.newSurrogateProxy(servicesInterface, this.namingServices);
	}

	/**
	 * Creates a new client-side proxy for the given client component type
	 * and services interface, first checking that the interface is really
	 * annotated {@code @Services} (rather than {@code @Parameters} or
	 * {@code @Events}, which would indicate a caller-side mistake) before
	 * delegating to the collaborating proxy factory.
	 *
	 * @param <SERVICES>          the services interface type
	 * @param clientComponentType the client component type
	 * @param servicesInterface   the services interface to use
	 * @param providedServices    the provider-side proxy (real or
	 *                            surrogate) to forward calls to
	 * @return the new client-side proxy
	 * @throws IllegalArgumentException if {@code servicesInterface} is
	 *                                  actually a parameters or events
	 *                                  interface
	 */
	@Override
	protected <SERVICES> SERVICES newClientProxy(final Class<?> clientComponentType,
			final Class<SERVICES> servicesInterface, final SERVICES providedServices) {
		if (!ComponentAnnotations.isServices(servicesInterface)) {
			if (ComponentAnnotations.isParameters(servicesInterface)) {
				throw new IllegalArgumentException(
						"Assumed services is indeed a parameters interface: " + servicesInterface.getSimpleName());
			}
			if (ComponentAnnotations.isEvents(servicesInterface)) {
				throw new IllegalArgumentException(
						"Assumed services is indeed an events interface: " + servicesInterface.getSimpleName());
			}
		}
		return this.servicesProxyFactory.newUsedServicesProxy(servicesInterface, providedServices, clientComponentType);
	}

	/**
	 * Notifies {@link ServicesManagerEvents} that the given component type
	 * now provides the given services interface.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param componentType     the providing component type
	 * @param servicesInterface the newly provided services interface
	 */
	@Override
	protected <SERVICES> void notifyProvided(final Class<?> componentType, final Class<SERVICES> servicesInterface) {
		this.produceServicesManagerEvents.onServicesProvided(componentType, servicesInterface);
	}

	/**
	 * Notifies {@link ServicesManagerEvents} that the given component type
	 * no longer provides the given services interface.
	 *
	 * @param <SERVICES>        the services interface type
	 * @param componentType     the component type that stopped providing it
	 * @param servicesInterface the services interface no longer provided
	 */
	@Override
	protected <SERVICES> void notifyUnprovided(final Class<?> componentType, final Class<SERVICES> servicesInterface) {
		this.produceServicesManagerEvents.onServicesUnprovided(componentType, servicesInterface);
	}

	/**
	 * Notifies {@link ServicesManagerEvents} that the given component type
	 * now uses the given services interface.
	 *
	 * @param <SERVICES>                 the services interface type
	 * @param servicesUserComponentType the client component type
	 * @param servicesInterface          the newly used services interface
	 */
	@Override
	protected <SERVICES> void notifyUsed(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		this.produceServicesManagerEvents.onServicesUsed(servicesUserComponentType, servicesInterface);
	}

	/**
	 * Notifies {@link ServicesManagerEvents} that the given component type
	 * no longer uses the given services interface.
	 *
	 * @param <SERVICES>                 the services interface type
	 * @param servicesUserComponentType the client component type that
	 *                                    stopped using it
	 * @param servicesInterface          the services interface no longer
	 *                                    used
	 */
	@Override
	protected <SERVICES> void notifyUnused(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		this.produceServicesManagerEvents.onServicesUnused(servicesUserComponentType, servicesInterface);
	}

}
