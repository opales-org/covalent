/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl.TrainingInteractionImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the TrainingMonitor's Interaction Controller (TrainingInteraction), per the MVIC pattern.
 * <p>
 * See {@link TrainingController} for what an empty {@code @Component} marker
 * interface is and why it carries no methods. Fulfilled by
 * {@link TrainingInteractionImpl}.
 */
@Component
public interface TrainingInteraction {

}
