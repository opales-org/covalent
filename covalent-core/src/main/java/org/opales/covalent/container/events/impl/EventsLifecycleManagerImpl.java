/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import java.util.Set;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.events.EventsLifecycleServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;

/**
 * {@link EventsLifecycleServices} implementation: applies the produced
 * events lifecycle transitions (activate/passivate the producer, publish/
 * unpublish its subscribe port) for every events interface a component
 * declares as {@code @ProducedEvents}, driving {@link EventsManagerImpl}
 * for the producer state and {@link NamingServices} for subscribe-port
 * publication.
 */
@Item
public class EventsLifecycleManagerImpl implements EventsLifecycleServices {
	private static Logger log = Logger.getLogger(EventsLifecycleManagerImpl.class.getName());

	/** Creates a new manager; collaborators are injected via the {@code set*} methods. */
	public EventsLifecycleManagerImpl() {
	}

	private EventsManagerImpl eventsManagerImpl;

	@UsedServices
	private MetamodelPortReadServices useMetamodelPortReadServices;

	@UsedServices
	private NamingServices useNamingServices;

	/**
	 * Sets the internal events manager used to activate/passivate producers
	 * and retrieve provided subscribers.
	 *
	 * @param eventsManagerServicesImpl the events manager implementation
	 */
	public void setEventsManagerImpl(final EventsManagerImpl eventsManagerServicesImpl) {
		this.eventsManagerImpl = eventsManagerServicesImpl;
	}

	/**
	 * Sets the internal service used to read a component's declared
	 * produced events from the metamodel.
	 *
	 * @param metamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.useMetamodelPortReadServices = metamodelPortReadServices;
	}

	/**
	 * Sets the internal service used to publish/unpublish subscribe ports.
	 *
	 * @param namingServices the naming service
	 */
	public void setNamingServices(final NamingServices namingServices) {
		this.useNamingServices = namingServices;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Activates the producer for every events interface the component
	 * declares as {@code @ProducedEvents}.
	 */
	@Override
	public <EVENTS> void activateProducedEvents(final Class<?> componentType) {
		Set<Class<?>> allProducedEvents = this.useMetamodelPortReadServices.getProducedEvents(componentType);
		for (Class<?> producedEvents : allProducedEvents) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) producedEvents;
			log.fine("Automatic activation of produced events " + eventsInterface.getSimpleName());
			this.eventsManagerImpl.activateProducer(eventsInterface);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Passivates the producer for every events interface the component
	 * declares as {@code @ProducedEvents} that is still actively produced -
	 * production may already have been stopped manually beforehand, in
	 * which case that interface is skipped.
	 */
	@Override
	public <EVENTS> void passivateProducedEvents(final Class<?> componentType) {
		Set<Class<?>> allProducedEvents = this.useMetamodelPortReadServices.getProducedEvents(componentType);
		for (Class<?> producedEvents : allProducedEvents) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) producedEvents;
			log.fine("Automatic passivation of produced events " + eventsInterface.getSimpleName());
			// events production could have been manually stopped manually before this
			if (this.eventsManagerImpl.isEventsProduced(eventsInterface)) {
				this.eventsManagerImpl.passivateProducer(eventsInterface);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Binds each produced events interface's provided subscriber under its
	 * subscribe interface, so consumers can look it up and subscribe. Every
	 * produced events interface is expected to have a corresponding
	 * subscribe interface, be it a custom one declared on the events
	 * interface or the generic subscribe interface used when none is.
	 */
	@Override
	public <EVENTS, SUBSCRIBE> void publishProvidedSubscribers(final Class<?> componentType) {
		Set<Class<?>> allProducedEvents = this.useMetamodelPortReadServices.getProducedEvents(componentType);
		// note: each produced events should have a corresponding published subscribe
		// interface, be it the generic subscribe if not subscribe interface is provided
		// by events interface
		for (Class<?> producedEvents : allProducedEvents) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) producedEvents;
			Class<SUBSCRIBE> subscribeInterface = this.useMetamodelPortReadServices.getSubscribeInterface(eventsInterface);
			SUBSCRIBE subscriber = this.eventsManagerImpl.getProvidedSubscriber(eventsInterface);

			log.fine("Publication of provided subscriber " + subscribeInterface.getSimpleName());
			this.useNamingServices.bindSubscriber(eventsInterface, subscriber);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Unbinds the subscribe port of every produced events interface the
	 * component declares that is currently bound.
	 */
	@Override
	public <EVENTS> void unpublishProvidedSubscribers(final Class<?> componentType) {
		Set<Class<?>> allProducedEvents = this.useMetamodelPortReadServices.getProducedEvents(componentType);
		for (Class<?> producedEvents : allProducedEvents) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) producedEvents;
			if (this.useNamingServices.isSubscriberBound(eventsInterface)) {
				this.useNamingServices.unbindSubscriber(eventsInterface);
			}
		}
	}

}
