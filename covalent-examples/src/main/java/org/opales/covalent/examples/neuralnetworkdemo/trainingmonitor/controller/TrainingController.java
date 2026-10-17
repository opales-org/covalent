/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl.TrainingControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModel;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingView;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the TrainingMonitor's Business Controller (TrainingController), per the MVIC pattern.
 * <p>
 * A {@code @Component} interface is a pure <em>type marker</em>: it names one
 * logical piece of an MVIC decomposition (here, the Controller) so the
 * container's lifecycle and registry machinery has a stable type to key on -
 * it declares no methods of its own. What the Controller actually does for
 * callers is expressed separately, through the {@code @Services} and
 * {@code @Parameters} interfaces it implements (see {@link TrainingServices},
 * {@link TrainingParameters}); this interface is fulfilled by exactly one
 * {@code @Implementation} class, {@link TrainingControllerImpl}. The same
 * pattern - an empty {@code @Component} marker plus one implementing class -
 * recurs identically for {@link TrainingModel}, {@link TrainingView} and
 * {@link TrainingInteraction}; see those for the roles they mark
 * rather than repeating this note.
 */
@Component
public interface TrainingController {

}
