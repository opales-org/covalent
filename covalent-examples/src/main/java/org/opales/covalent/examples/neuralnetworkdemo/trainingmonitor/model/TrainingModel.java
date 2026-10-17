/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.impl.TrainingModelImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the TrainingMonitor's Model (TrainingModel), per the MVIC pattern.
 * <p>
 * See {@link TrainingController} for what an empty {@code @Component} marker
 * interface is and why it carries no methods. Fulfilled by
 * {@link TrainingModelImpl}.
 */
@Component
public interface TrainingModel {

}
