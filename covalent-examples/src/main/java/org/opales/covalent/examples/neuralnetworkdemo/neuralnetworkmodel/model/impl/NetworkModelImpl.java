/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.impl.DatasetModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.NetworkEvents;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.NetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.NetworkModelServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkView;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The network's Model: stores the layer structure, weights and biases.
 * Carries no computation of its own (no forward/backward pass) - it only
 * stores what {@code NetworkController} computes, and notifies
 * {@link NetworkEvents} on every weight update.
 * <p>
 * Implements {@link NetworkEvents.NetworkEventsSubscribe} for the same
 * reason, and using the same mechanism, as {@code DatasetModelImpl} - see
 * that class's javadoc for the full explanation of Covalent's event
 * subscription delegation (in particular: why the subscribe interface must
 * be nested inside the events interface itself, and why a standalone
 * generic interface cannot be used for this producer-side role). Here, the
 * catch-up state {@link #subscribe(NetworkEvents)} replays is richer than a
 * single event: the full topology ({@link NetworkEvents#networkReset(int[])})
 * followed by every existing layer's weights
 * ({@link NetworkEvents#weightsChanged(int, double[][])} once per layer) -
 * exactly the sequence a live (re)initialization would have produced, so
 * {@code NetworkView} cannot tell the difference between catching up and
 * having been subscribed from the start.
 */
@Implementation
public class NetworkModelImpl
		implements NetworkModel, NetworkModelServices, NetworkEvents.NetworkEventsSubscribe {

	@ProducedEvents
	private NetworkEvents produceNetworkEvents;

	private int[] layerSizes = new int[0];

	private double[][][] weights = new double[0][][];

	private double[][] biases = new double[0][];

	@Override
	public void initializeLayers(final int[] layerSizes) {
		this.layerSizes = layerSizes.clone();
		int transitions = Math.max(0, layerSizes.length - 1);
		this.weights = new double[transitions][][];
		this.biases = new double[transitions][];
		for (int l = 0; l < transitions; l++) {
			this.weights[l] = new double[layerSizes[l + 1]][layerSizes[l]];
			this.biases[l] = new double[layerSizes[l + 1]];
		}
		// Fired through the normal, auto-wired @ProducedEvents producer -
		// this needs no delegate at all, it is just another event sent to
		// whoever is already subscribed, for a topology change that
		// happens *during* the View's lifetime (a hidden-layer count/size
		// change after startup). It also lets NetworkView discard any
		// transition index it cached that may no longer exist after a
		// shrinking topology change.
		this.produceNetworkEvents.networkReset(this.layerSizes.clone());
	}

	@Override
	public int getLayerCount() {
		return this.layerSizes.length;
	}

	@Override
	public int[] getLayerSizes() {
		return this.layerSizes.clone();
	}

	@Override
	public double[][] getLayerWeights(final int layerIndex) {
		return this.weights[layerIndex];
	}

	@Override
	public double[] getLayerBiases(final int layerIndex) {
		return this.biases[layerIndex];
	}

	@Override
	public void setLayerWeights(final int layerIndex, final double[][] weights) {
		this.weights[layerIndex] = weights;
		this.produceNetworkEvents.weightsChanged(layerIndex, weights);
	}

	@Override
	public void setLayerBiases(final int layerIndex, final double[] biases) {
		this.biases[layerIndex] = biases;
	}

	/**
	 * Replays the current topology, then every existing layer's weights, to
	 * a newly-subscribed consumer - see this class's javadoc.
	 *
	 * @param subscriber the new subscriber
	 */
	@Override
	public void subscribe(final NetworkEvents subscriber) {
		subscriber.networkReset(this.layerSizes.clone());
		for (int l = 0; l < this.weights.length; l++) {
			subscriber.weightsChanged(l, this.weights[l]);
		}
	}

	/**
	 * Called by Covalent when a subscriber unsubscribes. Nothing to react to
	 * here: this Model keeps no per-subscriber state.
	 *
	 * @param subscriber the subscriber that just unsubscribed
	 */
	@Override
	public void unsubscribe(final NetworkEvents subscriber) {
		// Nothing to do: no per-subscriber state is kept.
	}
}
