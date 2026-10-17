/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.impl;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl.TrainingControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl.TrainingInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModel;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.impl.TrainingModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingView;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.impl.TrainingViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code TrainingMonitor} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 * <p>
 * A composite {@code @Implementation} fulfils its own {@code @Component}
 * ({@link TrainingMonitor}) not by implementing it directly with behavior,
 * but by holding one {@code @Implementation}-annotated field per child
 * component it groups. Each field's declared type ({@link TrainingModelImpl},
 * {@link TrainingControllerImpl}, {@link TrainingViewImpl},
 * {@link TrainingInteractionImpl}) is itself an {@code @Implementation}
 * that fulfils its own {@code @Component} ({@link TrainingModel},
 * {@link TrainingController}, {@link TrainingView},
 * {@link TrainingInteraction} respectively). The container discovers
 * and deploys all four children when this composite is deployed, wires up
 * their {@code @Services}/{@code @UsedServices},
 * {@code @Parameters}/{@code @AccessedParameters} and
 * {@code @Events}/{@code @ProducedEvents} links between each other, and runs
 * each child's own lifecycle hooks - callers never need to name the children
 * individually.
 */
@Implementation
public class TrainingMonitorImpl implements TrainingMonitor {

	@Implementation
	private TrainingModelImpl model;

	@Implementation
	private TrainingControllerImpl controller;

	@Implementation
	private TrainingViewImpl view;

	@Implementation
	private TrainingInteractionImpl interaction;
}
