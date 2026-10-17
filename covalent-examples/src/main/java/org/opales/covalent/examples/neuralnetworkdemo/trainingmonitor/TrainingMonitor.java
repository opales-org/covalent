/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.impl.TrainingMonitorImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the training run MVIC (Model/Controller/Interaction/View).
 * <p>
 * See {@link TrainingController} for what an empty {@code @Component} marker
 * interface is and why it carries no methods in general. This particular
 * marker is a <em>composite</em> one: it groups the whole training-run
 * function (Model + Controller + Interaction + View) rather than a
 * single MVIC role, and is fulfilled by a single composite
 * {@code @Implementation}, {@link TrainingMonitorImpl}, which is the only
 * class of this function that needs to be passed to
 * {@code CovalentContainer.deployComponents(...)}.
 */
@Component
public interface TrainingMonitor {

}
