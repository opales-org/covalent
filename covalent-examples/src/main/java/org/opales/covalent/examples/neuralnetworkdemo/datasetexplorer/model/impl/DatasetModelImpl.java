/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.DatasetEvents;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.DatasetModel;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.DatasetModelServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetView;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The dataset's Model: holds the current set of samples and notifies
 * {@link DatasetEvents} whenever they change. Carries no logic of its own.
 * <p>
 * <b>Covalent mechanism illustrated here: event subscription delegation.</b>
 * {@code @ProducedEvents} alone only lets this component fire events; it
 * says nothing about a consumer that subscribes <i>after</i> some events
 * have already been fired (here, {@code DatasetController} generates the
 * first dataset at its own {@code @Initialization}, before {@code
 * DatasetView} - which auto-subscribes to {@link DatasetEvents} simply by
 * implementing that interface - has even activated). Without more, that
 * View would start with no data at all, and no further event on the way
 * until the next regeneration.
 * <p>
 * Subscription delegation closes that gap: by implementing a subscribe
 * interface, a producer asks Covalent to notify it whenever a consumer
 * (un)subscribes, so it can replay whatever "catch-up" state that consumer
 * needs. Covalent still does all the actual bookkeeping - registering the
 * consumer, dispatching every future {@code DatasetEvents} call to it - the
 * producer is only told that a subscription happened; the whole thing is a
 * pure QoS add-on on top of {@code @ProducedEvents}, not an alternative to
 * it (this class keeps {@code @ProducedEvents} exactly as it would be
 * without delegation).
 * <p>
 * A subscribe interface must be declared as an {@code @EventsSubscribe}
 * interface <b>nested inside the events interface it subscribes to</b> -
 * Covalent only discovers subscribe interfaces there (see
 * {@link DatasetEvents.DatasetEventsSubscribe}), never on a standalone
 * interface such as the framework's {@code GenericSubscribe} (which is
 * an entirely different, consumer-side mechanism: manual subscription when
 * a producer offers no subscribe interface at all - see
 * {@code doc/managed-components.md}). This class implements
 * {@link DatasetEvents.DatasetEventsSubscribe} to become {@link
 * DatasetEvents}'s subscribe delegate.
 */
@Implementation
public class DatasetModelImpl
		implements DatasetModel, DatasetModelServices, DatasetEvents.DatasetEventsSubscribe {

	@ProducedEvents
	private DatasetEvents produceDatasetEvents;

	private volatile TrainingSample[] samples = new TrainingSample[0];

	// Train/test split of the same generation, stored separately from the
	// full displayed set above and from each other - see this class's
	// implemented interface, DatasetModelServices, for why neither fires an
	// event, and why keeping them as physically separate arrays is what
	// actually guarantees the training pool never includes a test sample.
	private volatile TrainingSample[] trainSamples = new TrainingSample[0];

	private volatile TrainingSample[] testSamples = new TrainingSample[0];

	@Override
	public void replaceSamples(final TrainingSample[] samples) {
		this.samples = samples;
		this.produceDatasetEvents.datasetChanged(samples);
	}

	@Override
	public TrainingSample[] getSamples() {
		return this.samples;
	}

	@Override
	public void replaceTrainSamples(final TrainingSample[] trainSamples) {
		this.trainSamples = trainSamples;
	}

	@Override
	public TrainingSample[] getTrainSamples() {
		return this.trainSamples;
	}

	@Override
	public void replaceTestSamples(final TrainingSample[] testSamples) {
		this.testSamples = testSamples;
	}

	@Override
	public TrainingSample[] getTestSamples() {
		return this.testSamples;
	}

	/**
	 * Called by Covalent, once per new subscriber, the moment that
	 * subscriber's subscription to {@link DatasetEvents} is registered (in
	 * practice: right when {@code DatasetView} activates and its automatic
	 * subscription kicks in). Replays the current samples so the new
	 * subscriber starts consistent, exactly as if it had been subscribed
	 * from the very first {@link DatasetEvents#datasetChanged(TrainingSample[])}
	 * call.
	 *
	 * @param subscriber the new subscriber
	 */
	@Override
	public void subscribe(final DatasetEvents subscriber) {
		subscriber.datasetChanged(this.samples);
	}

	/**
	 * Called by Covalent when a subscriber unsubscribes. Nothing to react to
	 * here: this Model keeps no per-subscriber state.
	 *
	 * @param subscriber the subscriber that just unsubscribed
	 */
	@Override
	public void unsubscribe(final DatasetEvents subscriber) {
		// Nothing to do: no per-subscriber state is kept.
	}
}
