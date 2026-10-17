/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import java.util.Objects;

import org.opales.covalent.container.events.SubscribeParameters;

/**
 * Internal record pairing one subscriber instance (a
 * {@code @UsedEventsSubscribe} delegate or a consumer's callback) with the
 * {@link SubscribeParameters} it subscribed with and the component type
 * that owns it. The events subsystem keeps these as the entries of a
 * produced event's subscriber list, using them both to dispatch
 * notifications and, through {@link #equals}/{@link #hashCode} (based on
 * instance and subscribe parameters only), to find and remove a specific
 * subscription again.
 *
 * @param <EVENT> the subscriber/consumer instance type
 */
public class ConsumerMetadata<EVENT> {
	private EVENT instance;

	private SubscribeParameters subscribeParameters;

	private Object subscribeArgument;

	private Object[] subscribeArguments;

	private Class<?> consumerComponentType;

	/**
	 * Creates a new, empty subscription record.
	 */
	public ConsumerMetadata() {
	}

	/**
	 * Returns the subscriber/consumer instance.
	 *
	 * @return the subscriber instance
	 */
	public EVENT getInstance() {
		return this.instance;
	}

	/**
	 * Sets the subscriber/consumer instance.
	 *
	 * @param instance the subscriber instance
	 */
	public void setInstance(final EVENT instance) {
		this.instance = instance;
	}

	/**
	 * Returns the parameters this subscription was made with.
	 *
	 * @return the subscribe parameters
	 */
	public SubscribeParameters getSubscribeParameters() {
		return this.subscribeParameters;
	}

	/**
	 * Sets the parameters this subscription was made with.
	 *
	 * @param subscribeParameters the subscribe parameters
	 */
	public void setSubscribeParameters(final SubscribeParameters subscribeParameters) {
		this.subscribeParameters = subscribeParameters;
	}

	/**
	 * Returns the second argument of the subscribe call, as it was made: an
	 * events filter or subscribe parameters, {@code null} for a plain
	 * subscription. Subscribing again with it calls the same subscribe
	 * method, which matters when the producer implements the subscribe
	 * interface.
	 *
	 * @return the subscribe call's second argument, or {@code null}
	 */
	public Object getSubscribeArgument() {
		return this.subscribeArgument != null ? this.subscribeArgument : this.subscribeParameters;
	}

	/**
	 * Sets the second argument of the subscribe call, as it was made.
	 *
	 * @param subscribeArgument an events filter or subscribe parameters
	 */
	public void setSubscribeArgument(final Object subscribeArgument) {
		this.subscribeArgument = subscribeArgument;
	}

	/**
	 * Returns the arguments of the subscribe call that created this metadata,
	 * the consumer-side proxy included, in their original positions.
	 *
	 * @return a copy of the original subscribe arguments, or {@code null} when
	 *         this metadata was not created from a subscribe call (a subscriber
	 *         pending since before its producer existed, for instance)
	 */
	public Object[] getSubscribeArguments() {
		return this.subscribeArguments == null ? null : this.subscribeArguments.clone();
	}

	/**
	 * Records the arguments of the subscribe call that created this metadata,
	 * so that the same call can be replayed.
	 *
	 * @param subscribeArguments the subscribe call's arguments, {@code null}
	 *                           entries included
	 */
	public void setSubscribeArguments(final Object[] subscribeArguments) {
		this.subscribeArguments = subscribeArguments == null ? null : subscribeArguments.clone();
	}

	/**
	 * Sets the component type that owns this subscription.
	 *
	 * @param consumerComponentType the consumer component type
	 */
	public void setConsumerComponentType(final Class<?> consumerComponentType) {
		this.consumerComponentType = consumerComponentType;
	}

	/**
	 * Returns the component type that owns this subscription.
	 *
	 * @return the consumer component type
	 */
	public Class<?> getConsumerComponentType() {
		return this.consumerComponentType;
	}

	/**
	 * Consistent with {@link #equals(Object)}: based on the instance and
	 * subscribe parameters only.
	 */
	@Override
	public int hashCode() {
		return Objects.hash(this.instance, this.subscribeParameters);
	}

	/**
	 * Two {@code ConsumerMetadata} are equal when they share the same
	 * subscriber instance and the same subscribe parameters; the owning
	 * component type is not part of the comparison.
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		@SuppressWarnings("unchecked")
		ConsumerMetadata<EVENT> other = (ConsumerMetadata<EVENT>) obj;
		if (!Objects.equals(this.instance, other.instance)) {
			return false;
		}
		if (!Objects.equals(this.subscribeParameters, other.subscribeParameters)) {
			return false;
		}
		return true;
	}

}