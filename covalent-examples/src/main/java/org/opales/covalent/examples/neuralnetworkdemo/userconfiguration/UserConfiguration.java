/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.DatasetExplorer;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.impl.UserConfigurationImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the user-facing configuration composite. See
 * {@link DatasetController} for what a bare {@code @Component} tag interface
 * (no methods, no body) means in general - here too it is a pure type
 * marker, fulfilled by exactly one {@code @Implementation} class,
 * {@link UserConfigurationImpl}.
 * <p>
 * Architecturally, this composite is deliberately <b>not</b> a full MVIC
 * function like {@code DatasetExplorer}, {@code NeuralNetworkModel} or
 * {@code TrainingMonitor}: it owns no domain state of its own, so it has
 * only a View/Interaction pair - no Model, no Business Controller, no
 * Events. Instead it is a thin, cross-cutting front-end that gathers the
 * user-editable {@code @Parameters} of every other function, whichever one
 * actually owns each ({@link NetworkParameters}, {@link TrainingParameters}),
 * behind a single place the user can reach them from. How these are grouped
 * and presented (a single preferences panel, controls split across each
 * function's own view, ...) is a UI concern, deliberately left open here
 * rather than baked into the architecture: a parameter always lives on the
 * Parameters interface of the function that actually uses it (see
 * {@link NetworkParameters}'s and {@link TrainingParameters}'s own javadoc),
 * never on this composite, regardless of how it is surfaced to the user.
 */
@Component
public interface UserConfiguration {

}
