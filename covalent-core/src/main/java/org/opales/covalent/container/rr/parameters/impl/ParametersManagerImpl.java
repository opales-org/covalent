/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters.impl;

import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.rr.common.RRManagerImpl;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;
import org.opales.covalent.container.rr.parameters.ParametersManager;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Parameters flavor-specific instantiation of the generic request/response
 * ({@code RR}) machinery: implements {@link ParametersManager} (the
 * internal component) and {@link ParametersManagerServices} (creation,
 * lookup, activation, passivation, and deletion of parameters ports),
 * reusing {@link RRManagerImpl} for the bookkeeping shared with the
 * services flavor and supplying the {@link ParametersProxyFactory} and
 * {@link ParametersLifecycleManagerImpl} collaborators specific to
 * parameters. This is the parameters flavor-specific counterpart of
 * {@code org.opales.covalent.container.rr.services.impl.ServicesManagerImpl}.
 */
@Implementation
public class ParametersManagerImpl extends RRManagerImpl
		implements ParametersManager, ParametersManagerServices {

	/** Injected produced-events sink; notifications are sent through it, never invoked directly here. */
	@ProducedEvents
	private ParametersManagerEvents produceParametersManagerEvents;

	/** Collaborating lifecycle manager for the parameters flavor. */
	@Item
	private final ParametersLifecycleManagerImpl parametersLifecycleManagerImpl;

	/** Creates the provider-side, client-side, and surrogate proxies for parameters ports. */
	private final ParametersProxyFactory parametersProxyFactory;

	/**
	 * Creates a new parameters manager, wiring its dedicated proxy factory
	 * and lifecycle manager collaborators.
	 */
	public ParametersManagerImpl() {
		super("Parameters");
		this.parametersProxyFactory = new ParametersProxyFactory();
		this.parametersProxyFactory.setProducedParametersManagerEvents(() -> this.produceParametersManagerEvents);
		super.setProxyFactory(this.parametersProxyFactory);
		this.parametersLifecycleManagerImpl = new ParametersLifecycleManagerImpl();
		super.setLifecycleManager(this.parametersLifecycleManagerImpl);
		this.parametersLifecycleManagerImpl.setParametersManagerImpl(this);
	}

	/**
	 * Returns the collaborating lifecycle manager.
	 *
	 * @return the parameters lifecycle manager
	 */
	public ParametersLifecycleManagerImpl getParametersLifecycleManagerImpl() {
		return this.parametersLifecycleManagerImpl;
	}

	/**
	 * Returns the collaborating lifecycle manager, typed as the
	 * {@code @Services} port it also implements.
	 *
	 * @return the parameters lifecycle services port
	 */
	public ParametersLifecycleServices getParametersLifecycleServices() {
		return this.parametersLifecycleManagerImpl;
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getOfferorToParameters() {
		return this.getComponentTypeToProvided();
	}

	@Override
	public Map<Class<?>, Set<Class<?>>> getParametersToAccessor() {
		return this.getProvidedToClients();
	}

	@Override
	public <PARAMETERS> void createOfferedParameters(final Class<PARAMETERS> parametersInterface) {
		RuntimeCheck.notNull(parametersInterface, "parametersInterface");
		Class<?> componentType = this.MetamodelPortReadServices.getComponentTypeForOfferedParameters(parametersInterface);
		this.createOfferedParameters(componentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> void createOfferedParameters(final Class<?> componentType,
			final Class<PARAMETERS> parametersInterface) {
		super.createProvidedRR(componentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> PARAMETERS getParametersOfferor(final Class<PARAMETERS> parametersInterface) {
		return super.getProvider(parametersInterface);
	}

	@Override
	public <PARAMETERS> void deleteOfferedParameters(final Class<PARAMETERS> parametersInterface) {
		super.deleteProvider(parametersInterface);
	}

	@Override
	public boolean isParametersAccessed(final Class<?> componentType, final Class<?> parametersInterface) {
		return this.isUsed(componentType, parametersInterface);
	}

	@Override
	public boolean isParametersOffered(final Class<?> parametersInterface) {
		return super.isProvided(parametersInterface);
	}

	@Override
	public <PARAMETERS> void activateParameters(final Class<PARAMETERS> parametersInterface) {
		super.activateRR(parametersInterface);
	}

	@Override
	public <PARAMETERS> void passivateParameters(final Class<PARAMETERS> parametersInterface) {
		super.passivateRR(parametersInterface);
	}

	/**
	 * True if the offeror-side proxy for the given parameters interface is
	 * currently activated.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface the parameters interface to check
	 * @return {@code true} if its offeror-side proxy is activated
	 */
	public <PARAMETERS> boolean isActivated(final Class<PARAMETERS> parametersInterface) {
		return super.isActivatedRR(parametersInterface);
	}

	@Override
	public <PARAMETERS> PARAMETERS createAccessedParameters(final Class<?> parametersAccessorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		return super.createClientProxy(parametersAccessorComponentType, parametersInterface);
	}

	@Override
	public <PARAMETERS> void deleteAccessedParameters(final Class<?> parametersAccessorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		super.deleteClientProxy(parametersAccessorComponentType, parametersInterface);
	}

	@Override
	protected <PARAMETERS> Class<?> getComponentTypeForProvider(final Class<PARAMETERS> parametersInterface) {
		return this.MetamodelPortReadServices.getComponentTypeForOfferedParameters(parametersInterface);
	}

	@Override
	protected <PARAMETERS> PARAMETERS newProxy(final Class<PARAMETERS> parametersInterface) {
		return this.parametersProxyFactory.newOfferedParametersProxy(parametersInterface);
	}

	@Override
	protected <PARAMETERS> boolean isBound(final Class<PARAMETERS> parametersInterface) {
		return this.namingServices.isParametersBound(parametersInterface);
	}

	@Override
	protected <PARAMETERS> PARAMETERS lookup(final Class<PARAMETERS> parametersInterface) {
		return this.namingServices.lookupParameters(parametersInterface);
	}

	@Override
	protected <PARAMETERS> PARAMETERS newSurrogateProxy(final Class<PARAMETERS> parametersInterface) {
		return this.parametersProxyFactory.newSurrogateProxy(parametersInterface, this.namingServices);
	}

	@Override
	protected <PARAMETERS> PARAMETERS newClientProxy(final Class<?> clientComponentType,
			final Class<PARAMETERS> parametersInterface, final PARAMETERS providedParameters) {
		if (!ComponentAnnotations.isParameters(parametersInterface)) {
			if (ComponentAnnotations.isServices(parametersInterface)) {
				throw new IllegalArgumentException(
						"Assumed parameters is indeed a services interface: " + parametersInterface.getSimpleName());
			}
			if (ComponentAnnotations.isEvents(parametersInterface)) {
				throw new IllegalArgumentException(
						"Assumed parameters is indeed an events interface: " + parametersInterface.getSimpleName());
			}
		}
		return this.parametersProxyFactory.newAccessedParametersProxy(parametersInterface, providedParameters,
				clientComponentType);
	}

	@Override
	protected <SERVICES> void notifyProvided(final Class<?> componentType, final Class<SERVICES> parametersInterface) {
		this.produceParametersManagerEvents.onParametersOffered(componentType, parametersInterface);
	}

	@Override
	protected <SERVICES> void notifyUnprovided(final Class<?> componentType,
			final Class<SERVICES> parametersInterface) {
		this.produceParametersManagerEvents.onParametersUnoffered(componentType, parametersInterface);
	}

	@Override
	protected <SERVICES> void notifyUsed(final Class<?> parametersAccessorComponentType,
			final Class<SERVICES> parametersInterface) {
		this.produceParametersManagerEvents.onParametersAccessed(parametersAccessorComponentType, parametersInterface);
	}

	@Override
	protected <SERVICES> void notifyUnused(final Class<?> parametersAccessorComponentType,
			final Class<SERVICES> parametersInterface) {
		this.produceParametersManagerEvents.onParametersUnaccessed(parametersAccessorComponentType,
				parametersInterface);
	}

}
