/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.impl.DatasetModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetView;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;

/**
 * Dataset change notifications, produced by {@code DatasetModel} and
 * consumed by {@code DatasetView} to refresh the point cloud rendering.
 * <p>
 * This is the one notification this function needs: the Model has no other
 * state changes worth broadcasting, so a single {@code datasetChanged}
 * method is enough. See {@link DatasetModelServices} for how the Model
 * produces it, and {@code DatasetViewImpl}'s own javadoc for how the View
 * consumes it (auto-subscription, and the {@code active} guard against
 * updating a passivated view).
 */
@Events
public interface DatasetEvents {

	/**
	 * Fired whenever the dataset's contents change (regeneration).
	 *
	 * @param samples the full, current set of samples
	 */
	void datasetChanged(TrainingSample[] samples);

	/**
	 * Subscribe interface for {@link DatasetEvents}, nested here as required
	 * by Covalent (a subscribe interface must be declared inside the events
	 * interface it subscribes to - there is no framework-provided generic
	 * substitute for a producer-side subscribe interface: see
	 * {@code DatasetModelImpl}'s javadoc). {@code DatasetModelImpl}
	 * implements this interface to enable event subscription delegation:
	 * Covalent calls {@link #subscribe(DatasetEvents)} whenever a consumer
	 * subscribes to {@link DatasetEvents}, so the Model can replay its
	 * current state to that new consumer.
	 */
	@EventsSubscribe
	interface DatasetEventsSubscribe {

		/**
		 * Called by Covalent when a consumer subscribes to
		 * {@link DatasetEvents} (automatically, by implementing that
		 * interface, or manually).
		 *
		 * @param subscriber the new subscriber
		 */
		void subscribe(DatasetEvents subscriber);

		/**
		 * Called by Covalent when a subscriber unsubscribes from
		 * {@link DatasetEvents}.
		 *
		 * @param subscriber the subscriber that just unsubscribed
		 */
		void unsubscribe(DatasetEvents subscriber);
	}
}
