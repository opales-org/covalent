/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import java.lang.reflect.Method;

import org.opales.covalent.container.invocation.CovalentInvocationHandler;

/**
 * Events consumer Proxy.
 *
 * @param <EVENTS> the events interface this handler consumes
 */
public class ConsumerInvocationHandler<EVENTS> extends CovalentInvocationHandler {

	private EVENTS consumer;

	/**
	 * The component through whose {@code @UsedEventsSubscribe} port
	 * {@link #consumer} was subscribed, or {@code null} when unknown (a
	 * subscription made directly, not through such a port).
	 */
	private Class<?> subscribingComponentType;

	/**
	 * Creates a new handler wrapping the given real consumer.
	 *
	 * @param consumer the real consumer instance calls are forwarded to
	 */
	public ConsumerInvocationHandler(final EVENTS consumer) {
		// this is the real consumer
		this.consumer = consumer;
	}

	/**
	 * Returns the real consumer instance this handler wraps.
	 *
	 * @return the wrapped consumer instance
	 */
	public EVENTS getConsumer() {
		return this.consumer;
	}

	/**
	 * Returns the component that subscribed the wrapped consumer.
	 *
	 * @return the component that subscribed {@link #getConsumer()}, or
	 *         {@code null} when unknown
	 */
	public Class<?> getSubscribingComponentType() {
		return this.subscribingComponentType;
	}

	/**
	 * Sets the component that subscribed the wrapped consumer.
	 *
	 * @param subscribingComponentType the component that subscribed
	 *                                 {@link #getConsumer()}
	 */
	public void setSubscribingComponentType(final Class<?> subscribingComponentType) {
		this.subscribingComponentType = subscribingComponentType;
	}
	
	@Override
	public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
		method.invoke(this.consumer, args);
		
		return null;
	}
	
}
