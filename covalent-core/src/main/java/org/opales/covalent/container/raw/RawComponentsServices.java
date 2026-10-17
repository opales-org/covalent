/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.raw;

import java.util.function.Consumer;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * Bridges Covalent with externally owned, non-Covalent objects.
 * <p>
 * Raw component registration accepts ordinary Java instances whose structure is
 * not declared with Covalent component model annotations. Managed Covalent
 * components, component items, and composed components must be handled by the
 * managed lifecycle API.
 * </p>
 * <p>
 * Operations that open or close a port ({@code produceEvents},
 * {@code subscribe}, {@code provideServices}, {@code useServices},
 * {@code offerParameters}, {@code accessParameters} and their counterparts)
 * throw an {@link IllegalArgumentException} when the component type is one of
 * Covalent's own (internal) components, and an {@link IllegalStateException}
 * when the component type has not been registered with
 * {@code registerComponent} yet. The {@code registerComponent} overloads throw
 * an {@link IllegalArgumentException} when the component is internal, uses
 * Covalent annotations or when the state to reach is not allowed, and an
 * {@link IllegalStateException} when another instance is already registered
 * under the same component type.
 * </p>
 */
@Services
public interface RawComponentsServices {
	// Events

	/**
	 * Creates a produced events proxy when the producer component type can be
	 * resolved from a managed component or from the immediate caller.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface produced events interface
	 * @return produced events proxy
	 */
	<EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface);

	/**
	 * Creates a produced events proxy with a subscribe delegate when the producer
	 * component type can be resolved from a managed component or from the immediate
	 * caller.
	 *
	 * @param <EVENTS>                  events interface type
	 * @param eventsInterface           produced events interface
	 * @param subscribeDelegateConsumer delegate called when a consumer subscribes
	 * @return produced events proxy
	 */
	<EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer);

	/**
	 * Creates a produced events proxy with subscribe and unsubscribe delegates when
	 * the producer component type can be resolved from a managed component or from
	 * the immediate caller.
	 *
	 * @param <EVENTS>                    events interface type
	 * @param eventsInterface             produced events interface
	 * @param subscribeDelegateConsumer   delegate called when a consumer subscribes
	 * @param unsubscribeDelegateConsumer delegate called when a consumer
	 *                                    unsubscribes
	 * @return produced events proxy
	 */
	<EVENTS> EVENTS produceEvents(final Class<EVENTS> eventsInterface, final Consumer<EVENTS> subscribeDelegateConsumer,
			final Consumer<EVENTS> unsubscribeDelegateConsumer);

	/**
	 * Creates a produced events proxy for an explicit producer component type.
	 *
	 * @param <EVENTS>              events interface type
	 * @param producerComponentType producer component type
	 * @param eventsInterface             produced events interface
	 * @return produced events proxy
	 */
	<EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface);

	/**
	 * Creates a produced events proxy with a subscribe delegate for an explicit
	 * producer component type.
	 *
	 * @param <EVENTS>              events interface type
	 * @param producerComponentType producer component type
	 * @param eventsInterface             produced events interface
	 * @param subscribeDelegateConsumer     delegate called when a consumer subscribes
	 * @return produced events proxy
	 */
	<EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer);

	/**
	 * Creates a produced events proxy with subscribe and unsubscribe delegates for
	 * an explicit producer component type.
	 *
	 * @param <EVENTS>              events interface type
	 * @param producerComponentType producer component type
	 * @param eventsInterface             produced events interface
	 * @param subscribeDelegateConsumer     delegate called when a consumer subscribes
	 * @param unsubscribeDelegateConsumer   delegate called when a consumer unsubscribes
	 * @return produced events proxy
	 */
	<EVENTS> EVENTS produceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface,
			final Consumer<EVENTS> subscribeDelegateConsumer, final Consumer<EVENTS> unsubscribeDelegateConsumer);

	/**
	 * Removes produced events when the producer component type can be resolved from
	 * a managed component or from the immediate caller.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface produced events interface
	 */
	<EVENTS> void unproduceEvents(final Class<EVENTS> eventsInterface);

	/**
	 * Removes produced events for an explicit producer component type.
	 *
	 * @param <EVENTS>              events interface type
	 * @param producerComponentType producer component type
	 * @param eventsInterface       produced events interface
	 */
	<EVENTS> void unproduceEvents(final Class<?> producerComponentType, final Class<EVENTS> eventsInterface);

	/**
	 * Subscribes a consumer to an events interface when the consumer component type
	 * is the immediate caller.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to consume
	 * @param subscriber        subscriber receiving event method calls
	 */
	<EVENTS> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber);

	/**
	 * Subscribes a consumer to an events interface with subscription parameters
	 * when the consumer component type is the immediate caller.
	 *
	 * @param <EVENTS>            events interface type
	 * @param eventsInterface     events interface to consume
	 * @param subscriber            subscriber receiving event method calls
	 * @param subscribeParameters subscription options
	 */
	<EVENTS> void subscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber,
			final SubscribeParameters subscribeParameters);

	/**
	 * Subscribes a consumer to an events interface for an explicit consumer
	 * component type.
	 *
	 * @param <EVENTS>             events interface type
	 * @param consumerComponentType consumer component type
	 * @param eventsInterface             events interface to consume
	 * @param subscriber              subscriber receiving event method calls
	 */
	<EVENTS> void subscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface, final EVENTS subscriber);

	/**
	 * Subscribes a consumer to an events interface with subscription parameters for
	 * an explicit consumer component type.
	 *
	 * @param <EVENTS>              events interface type
	 * @param consumerComponentType consumer component type
	 * @param eventsInterface             events interface to consume
	 * @param subscriber              subscriber receiving event method calls
	 * @param subscribeParameters   subscription options
	 */
	<EVENTS> void subscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface, final EVENTS subscriber,
			final SubscribeParameters subscribeParameters);

	/**
	 * Unsubscribes a consumer from an events interface when the consumer component
	 * type is the immediate caller.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to unsubscribe from
	 * @param subscriber      subscribed consumer to remove
	 */
	<EVENTS> void unsubscribe(final Class<EVENTS> eventsInterface, final EVENTS subscriber);

	/**
	 * Unsubscribes a consumer from an events interface for an explicit consumer
	 * component type.
	 *
	 * @param <EVENTS>              events interface type
	 * @param consumerComponentType consumer component type
	 * @param eventsInterface       events interface to unsubscribe from
	 * @param subscriber            subscribed consumer to remove
	 */
	<EVENTS> void unsubscribe(final Class<?> consumerComponentType, final Class<EVENTS> eventsInterface,
			final EVENTS subscriber);

	// Services

	/**
	 * Provides services when the provider component type can be resolved from a
	 * managed component or from the immediate caller.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to provide
	 */
	<SERVICES> void provideServices(final Class<SERVICES> servicesInterface);

	/**
	 * Provides services for an explicit provider component type.
	 *
	 * @param <SERVICES>            services interface type
	 * @param providerComponentType provider component type
	 * @param servicesInterface     services interface to provide
	 */
	<SERVICES> void provideServices(final Class<?> providerComponentType, final Class<SERVICES> servicesInterface);

	/**
	 * Removes provided services when the provider component type can be resolved
	 * from a managed component or from the immediate caller.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to remove
	 */
	<SERVICES> void unprovideServices(final Class<SERVICES> servicesInterface);

	/**
	 * Removes provided services for an explicit provider component type.
	 *
	 * @param <SERVICES>            services interface type
	 * @param providerComponentType provider component type
	 * @param servicesInterface     services interface to remove
	 */
	<SERVICES> void unprovideServices(final Class<?> providerComponentType, final Class<SERVICES> servicesInterface);

	/**
	 * Creates a used services proxy when the provider is a managed component.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to use
	 * @return user-side services proxy
	 */
	<SERVICES> SERVICES useServices(final Class<SERVICES> servicesInterface);

	/**
	 * Creates a used services proxy for an explicit provider component type.
	 *
	 * @param <SERVICES>            services interface type
	 * @param providerComponentType provider component type
	 * @param servicesInterface     services interface to use
	 * @return user-side services proxy
	 */
	<SERVICES> SERVICES useServices(final Class<?> providerComponentType, final Class<SERVICES> servicesInterface);

	/**
	 * Removes a used services proxy from the immediate caller component.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface used services interface
	 */
	<SERVICES> void unuseServices(final Class<SERVICES> servicesInterface);

	// Parameters

	/**
	 * Offers parameters when the offeror component type can be resolved from a
	 * managed component or from the immediate caller.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to offer
	 */
	<PARAMETERS> void offerParameters(final Class<PARAMETERS> parametersInterface);

	/**
	 * Offers parameters for an explicit offeror component type.
	 *
	 * @param <PARAMETERS>         parameters interface type
	 * @param offerorComponentType offeror component type
	 * @param parametersInterface  parameters interface to offer
	 */
	<PARAMETERS> void offerParameters(final Class<?> offerorComponentType, final Class<PARAMETERS> parametersInterface);

	/**
	 * Removes offered parameters when the offeror component type can be resolved
	 * from a managed component or from the immediate caller.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to remove
	 */
	<PARAMETERS> void unofferParameters(final Class<PARAMETERS> parametersInterface);

	/**
	 * Removes offered parameters for an explicit offeror component type.
	 *
	 * @param <PARAMETERS>         parameters interface type
	 * @param offerorComponentType offeror component type
	 * @param parametersInterface  parameters interface to remove
	 */
	<PARAMETERS> void unofferParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Creates an accessed parameters proxy when the offeror is a managed component.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to access
	 * @return accessor-side parameters proxy
	 */
	<PARAMETERS> PARAMETERS accessParameters(final Class<PARAMETERS> parametersInterface);

	/**
	 * Creates an accessed parameters proxy for an explicit offeror component type.
	 *
	 * @param <PARAMETERS>         parameters interface type
	 * @param offerorComponentType offeror component type
	 * @param parametersInterface  parameters interface to access
	 * @return accessor-side parameters proxy
	 */
	<PARAMETERS> PARAMETERS accessParameters(final Class<?> offerorComponentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Removes an accessed parameters proxy from the immediate caller component.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface accessed parameters interface
	 */
	<PARAMETERS> void unaccessParameters(final Class<PARAMETERS> parametersInterface);

	/**
	 * <p>
	 * Registers a raw component with its implementation class as component type.
	 * </p>
	 * 
	 * <p>
	 * Managed Covalent components must be deployed through the managed lifecycle
	 * API, not registered through raw component services.
	 * </p>
	 * 
	 * @param componentInstance externally owned raw component instance
	 * @return the inferred raw component type
	 */
	Class<?> registerComponent(final Object componentInstance);

	/**
	 * Registers a raw component with its implementation class as component type and
	 * brings it to the requested lifecycle state.
	 *
	 * @param componentInstance externally owned raw component instance
	 * @param stateToReach     target lifecycle state
	 * @return the inferred raw component type
	 */
	Class<?> registerComponent(final Object componentInstance, final ComponentState stateToReach);

	/**
	 * Registers a raw component with an explicit component type.
	 *
	 * @param componentType     raw component type exposed to Covalent
	 * @param componentInstance externally owned raw component instance
	 */
	void registerComponent(final Class<?> componentType, final Object componentInstance);

	/**
	 * Registers a raw component with an explicit component type and brings it to the
	 * requested lifecycle state.
	 *
	 * @param componentType     raw component type exposed to Covalent
	 * @param componentInstance externally owned raw component instance
	 * @param stateToReach     target lifecycle state
	 */
	void registerComponent(final Class<?> componentType, final Object componentInstance,
			final ComponentState stateToReach);

	/**
	 * Unregisters a raw component and removes the ports published for its component
	 * type.
	 *
	 * @param componentType raw component type exposed to Covalent
	 */
	void unregisterComponent(final Class<?> componentType);

	/**
	 * Activates an externally owned raw component instance with an explicit
	 * component type.
	 * <p>
	 * This method is intended for raw components that were registered up to an
	 * earlier lifecycle state, typically with
	 * {@link #registerComponent(Class, Object, ComponentState)} and
	 * {@link ComponentState#initialized}, before a later coordinated activation.
	 * If the raw component is not initialized yet, registration is completed up to
	 * the initialized state first.
	 * </p>
	 * 
	 * @param componentType     raw component type exposed to Covalent
	 * @param componentInstance externally owned raw component instance
	 * @throws IllegalStateException if another instance is already registered
	 *                               under {@code componentType}
	 */
	void activateComponent(final Class<?> componentType, final Object componentInstance) throws RuntimeException;

}
