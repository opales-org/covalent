/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.DatasetExplorer;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl.DatasetControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * User-facing dataset control, provided by {@code DatasetController} and
 * used by {@code DatasetInteraction} to regenerate the dataset or
 * change its shape from the UI. Internal to the {@code DatasetExplorer}
 * function - distinct from the public {@link DatasetServices}, which only
 * exposes read access for other functions.
 * <p>
 * {@code @Services} models a synchronous, call-and-return contract: the
 * provider implements the {@code @Services}-annotated interface, and each
 * consumer declares a field of that interface type annotated
 * {@code @UsedServices}, which the container injects as a live proxy to the
 * provider. This project deliberately keeps
 * {@code DatasetControllerServices} and {@link DatasetServices} as two
 * separate interfaces, even though {@link DatasetControllerImpl} implements
 * both from the same state: it keeps operations meaningful only within this
 * MVIC function, from its own Interaction Controller - out of the contract
 * other functions (e.g. {@code TrainingController}) see and depend on.
 */
@Services
public interface DatasetControllerServices {

	/**
	 * Regenerates the dataset from scratch with a new random seed and noise
	 * level, keeping the currently selected shape.
	 *
	 * @param seed  the random seed to regenerate with
	 * @param noise the class-overlap noise level (0 = perfectly separable)
	 */
	void regenerate(long seed, double noise);

	/**
	 * Switches the generated shape and regenerates immediately (at a fresh
	 * seed, the same noise level as the last regeneration).
	 *
	 * @param shape the shape to generate from now on
	 */
	void selectShape(DatasetShape shape);

	/**
	 * Returns the shape the dataset is currently generated from.
	 *
	 * @return the currently selected shape
	 */
	DatasetShape getShape();
}
