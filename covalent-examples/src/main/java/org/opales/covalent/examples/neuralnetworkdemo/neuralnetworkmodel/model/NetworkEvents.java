/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.impl.NetworkModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkView;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.impl.NetworkViewImpl;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

/**
 * Network weight change notifications, produced by {@code NetworkModel} and
 * consumed by {@code NetworkView}. The updated weights are carried directly
 * in the event payload, so the View never needs to pull them back from the
 * Model: it only renders what it is given.
 * <p>
 * {@code @Events} marks a fire-and-forget, one-producer-to-many-consumers
 * notification contract - as opposed to a synchronous, call-and-return
 * {@code @Services} contract, or passive configuration state read through
 * {@code @Parameters}. The producer annotates a field of this interface
 * type with {@code @ProducedEvents} (see {@code NetworkModelImpl}) and
 * fires calls on it; a consumer needs no special annotation to receive
 * them - simply implementing this interface directly on a component's
 * implementation class (see {@code NetworkViewImpl}) is enough for
 * Covalent to auto-subscribe it at that component's activation, and
 * auto-unsubscribe it at passivation.
 */
@Events
public interface NetworkEvents {

	/**
	 * Fired whenever the network's layer structure itself is (re)initialized
	 * (startup, or a hidden-layer count/size change): announces the full new
	 * topology before any per-layer {@link #weightsChanged(int, double[][])}
	 * call for it, so the View can discard whatever it knew about the
	 * previous topology - including any transition index that no longer
	 * exists - before those calls arrive.
	 *
	 * @param layerSizes each layer's new size (neuron count), input to output
	 */
	void networkReset(int[] layerSizes);

	/**
	 * Fired whenever one layer's weights change (training update, or reset).
	 *
	 * @param layerIndex      the layer index, 0-based
	 * @param weightsSnapshot the layer's new weight matrix
	 */
	void weightsChanged(int layerIndex, double[][] weightsSnapshot);

	/**
	 * Subscribe interface for {@link NetworkEvents}, nested here as required
	 * by Covalent (a subscribe interface must be declared inside the events
	 * interface it subscribes to). {@code NetworkModelImpl} implements this
	 * interface to enable event subscription delegation: Covalent calls
	 * {@link #subscribe(NetworkEvents)} whenever a consumer subscribes to
	 * {@link NetworkEvents}, so the Model can replay the full topology and
	 * every layer's current weights to that new consumer.
	 */
	@EventsSubscribe
	interface NetworkEventsSubscribe {

		/**
		 * Called by Covalent when a consumer subscribes to
		 * {@link NetworkEvents} (automatically, by implementing that
		 * interface, or manually).
		 *
		 * @param subscriber the new subscriber
		 */
		void subscribe(NetworkEvents subscriber);

		/**
		 * Called by Covalent when a subscriber unsubscribes from
		 * {@link NetworkEvents}.
		 *
		 * @param subscriber the subscriber that just unsubscribed
		 */
		void unsubscribe(NetworkEvents subscriber);
	}
}
