/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.impl;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl.GradientDescentOptimizerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl.NetworkControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.impl.NetworkInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.impl.NetworkModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.impl.NetworkViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code NeuralNetworkModel} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 * <p>
 * Each field is itself {@code @Implementation}-annotated and typed to a
 * concrete implementation class, one per MVIC part
 * ({@code NetworkModelImpl}, {@link NetworkControllerImpl},
 * {@code NetworkViewImpl}, {@link NetworkInteractionImpl}): this is
 * how a composite {@code @Implementation} declares the several
 * {@code @Component} types it groups together for deployment, each part
 * keeping its own full component identity, lifecycle hooks, and wiring
 * ({@code @UsedServices}/{@code @AccessedParameters}) resolved by the
 * container as usual. This differs from an {@code @Item} (see
 * {@link GradientDescentOptimizerImpl}): every field here names an actual
 * {@code @Component} type with its own lifecycle, whereas an {@code @Item}
 * has neither.
 */
@Implementation
public class NeuralNetworkModelImpl implements NeuralNetworkModel {

	@Implementation
	private NetworkModelImpl model;

	@Implementation
	private NetworkControllerImpl controller;

	@Implementation
	private NetworkViewImpl view;

	@Implementation
	private NetworkInteractionImpl interaction;
}
