/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.directory.impl;

import java.util.List;
import java.util.ArrayList;
import org.opales.covalent.container.util.ShutdownResidues;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.directory.Directory;
import org.opales.covalent.container.directory.NamingEvents;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.exceptions.NotBoundException;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Default implementation of the naming directory: a simple in-memory
 * registry mapping a services, events-subscribe or parameters interface to
 * its currently bound instance, with {@link NamingEvents} notifications
 * fired on every bind/unbind.
 */
@Implementation
public class DirectoryImpl implements Directory, NamingServices {
	private static Logger log = Logger.getLogger(DirectoryImpl.class.getName());

	@UsedServices
	private MetamodelPortReadServices useMetamodelPortReadServices;

	/** Bound services interface -> its published instance. */
	private final Map<Class<?>, Object> publishedServices = new HashMap<>();

	/** Bound events interface -> its published subscribe-side instance. */
	private final Map<Class<?>, Object> publishedEventsSubscribe = new HashMap<>();

	/** Bound parameters interface -> its published instance. */
	private final Map<Class<?>, Object> publishedParameters = new HashMap<>();

	@ProducedEvents
	private NamingEvents produceNamingEvents;

	/**
	 * Creates a new, empty directory.
	 */
	public DirectoryImpl() {
	}

	@Override
	public <SERVICES> void bindServices(final Class<SERVICES> servicesInterface, final SERVICES services) {
		RuntimeCheck.notNull(servicesInterface, "servicesInterface");
		RuntimeCheck.notNull(services, "services");
		this.publishedServices.put(servicesInterface, services);
		this.produceNamingEvents.onServicesBound(servicesInterface);
	}

	@Override
	public <SERVICES> void unbindServices(final Class<SERVICES> servicesInterface) {
		RuntimeCheck.notNull(servicesInterface, "servicesInterface");
		this.publishedServices.remove(servicesInterface);
		this.produceNamingEvents.onServicesUnbound(servicesInterface);
	}

	@Override
	public <SERVICES> boolean isServicesBound(final Class<SERVICES> servicesInterface) {
		return this.publishedServices.containsKey(servicesInterface);
	}

	@Override
	public <EVENTS, SUBSCRIBE> void bindSubscriber(final Class<EVENTS> eventsInterface, final SUBSCRIBE subscribe) {
		log.fine("binding subscriber for " + eventsInterface);
		RuntimeCheck.notNull(eventsInterface, "eventsInterface");
		RuntimeCheck.notNull(subscribe, "subscribe");
		this.publishedEventsSubscribe.put(eventsInterface, subscribe);
		this.produceNamingEvents.onSubscriberBound(eventsInterface);
	}

	@Override
	public <EVENTS> void unbindSubscriber(final Class<EVENTS> eventsInterface) {
		RuntimeCheck.notNull(eventsInterface, "eventsInterface");
		this.publishedEventsSubscribe.remove(eventsInterface);
		this.produceNamingEvents.onSubscriberUnbound(eventsInterface);
	}

	@Override
	public <EVENTS> boolean isSubscriberBound(final Class<EVENTS> eventsInterface) {
		return this.publishedEventsSubscribe.containsKey(eventsInterface);
	}

	@Override
	public <SERVICES> SERVICES lookupServices(final Class<SERVICES> servicesInterface) {
		RuntimeCheck.notNull(servicesInterface, "servicesInterface");
		@SuppressWarnings("unchecked")
		final SERVICES services = (SERVICES) this.publishedServices.get(servicesInterface);
		if (services == null) {
			throw new NotBoundException(
					"Services interface " + servicesInterface.getSimpleName() + " is not bound in directory.");
		}
		return services;
	}

	@Override
	public <EVENTS, SUBSCRIBE> SUBSCRIBE lookupSubscriber(final Class<EVENTS> eventsInterface) {
		RuntimeCheck.notNull(eventsInterface, "eventsInterface");
		@SuppressWarnings("unchecked")
		final SUBSCRIBE publishedEventsSubscribe = (SUBSCRIBE) this.publishedEventsSubscribe.get(eventsInterface);

		if (publishedEventsSubscribe == null) {
			String message = "Subscribe interface for events interface " + eventsInterface.getSimpleName()
					+ " is not bound in directory.";
			if (!eventsInterface.isAnnotationPresent(Events.class)) {
				message += " Hint: interface is not annotated with @Events, is this really an events interface?";
			}
			throw new NotBoundException(message);
		}
		return publishedEventsSubscribe;
	}

	/**
	 * Lists what is left behind once the business components are torn down: every
	 * entry describes something that is not an internal component. Run by the
	 * container's {@code stop()}, which reports the result in the shutdown report.
	 *
	 * @return the residues found, empty when only internal components remain
	 */
	public List<String> listNonInternalResidues() {
		List<String> residues = new ArrayList<>();
		for (Class<?> servicesInterface : this.publishedServices.keySet()) {
			ShutdownResidues.addIfNotInternal(residues, "Directory",
					"services " + servicesInterface.getName() + " still published, provided by",
					this.useMetamodelPortReadServices.getComponentTypeForProvidedServices(servicesInterface));
		}
		for (Class<?> eventsInterface : this.publishedEventsSubscribe.keySet()) {
			ShutdownResidues.addIfNotInternal(residues, "Directory",
					"events " + eventsInterface.getName() + " still published, produced by",
					this.useMetamodelPortReadServices.getComponentTypeForProducedEvents(eventsInterface));
		}
		return residues;
	}


	@Override
	public <PARAMETERS> void bindParameters(final Class<PARAMETERS> parametersInterface, final PARAMETERS parameters) {
		RuntimeCheck.notNull(parametersInterface, "parametersInterface");
		RuntimeCheck.notNull(parameters, "parameters");
		this.publishedParameters.put(parametersInterface, parameters);
		this.produceNamingEvents.onParametersBound(parametersInterface);
	}

	@Override
	public <PARAMETERS> void unbindParameters(final Class<PARAMETERS> parametersInterface) {
		RuntimeCheck.notNull(parametersInterface, "parametersInterface");
		this.publishedParameters.remove(parametersInterface);
		this.produceNamingEvents.onParametersUnbound(parametersInterface);
	}

	@Override
	public <PARAMETERS> boolean isParametersBound(final Class<PARAMETERS> parametersInterface) {
		return this.publishedParameters.containsKey(parametersInterface);
	}

	@Override
	public <PARAMETERS> PARAMETERS lookupParameters(final Class<PARAMETERS> parametersInterface) {
		RuntimeCheck.notNull(parametersInterface, "parametersInterface");
		@SuppressWarnings("unchecked")
		final PARAMETERS parameters = (PARAMETERS) this.publishedParameters.get(parametersInterface);
		if (parameters == null) {
			throw new NotBoundException(
					"Parameters interface " + parametersInterface.getSimpleName() + " is not bound in directory.");
		}
		return parameters;
	}

}
