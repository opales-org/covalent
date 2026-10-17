/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.impl;

import org.opales.covalent.examples.neuralnetworkdemo.NeuralNetworkDemo;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.impl.DatasetExplorerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.impl.HmiBuilderImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.impl.NeuralNetworkModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.impl.TrainingMonitorImpl;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.impl.UserConfigurationImpl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.hmi.impl.HmiCoreImpl;

/**
 * The {@code NeuralNetworkDemo} composite: deploys the whole application as
 * a single {@code @Implementation}. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 * <p>
 * Each field is itself {@code @Implementation}-annotated and typed to a
 * concrete implementation class, one per function of the application, in
 * the exact order their activation must proceed: {@code HmiCoreImpl} first
 * (so {@code ActionManagerImpl}, composed inside it, is ready before any
 * function registers actions against it), then each function's own
 * composite ({@link DatasetExplorerImpl}, {@link NeuralNetworkModelImpl},
 * {@link TrainingMonitorImpl}, {@link UserConfigurationImpl}), and finally
 * {@link HmiBuilderImpl} (which reads every other function's
 * {@code *ViewParameters} to assemble the final {@code Stage}, so it must
 * activate last). Composition can nest to any depth: this composite's own
 * children are themselves composites of their own MVIC parts (see each
 * function's own {@code *Impl} composite for that inner grouping), and
 * activation/passivation both proceed children-before-parent, in
 * field-declaration order, at every level.
 */
@Implementation
public class NeuralNetworkDemoImpl implements NeuralNetworkDemo {

	@Implementation
	private HmiCoreImpl hmiCore;

	@Implementation
	private DatasetExplorerImpl datasetExplorer;

	@Implementation
	private NeuralNetworkModelImpl neuralNetworkModel;

	@Implementation
	private TrainingMonitorImpl trainingMonitor;

	@Implementation
	private UserConfigurationImpl userConfiguration;

	@Implementation
	private HmiBuilderImpl hmiBuilder;
}
