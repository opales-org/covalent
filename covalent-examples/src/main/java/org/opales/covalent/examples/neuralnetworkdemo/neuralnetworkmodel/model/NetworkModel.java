/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the NeuralNetworkModel's Model (NetworkModel), per the MVIC pattern.
 * <p>
 * See {@link NetworkController} for what a bare {@code @Component} marker
 * interface means in Covalent; this one names the Model part of the same
 * MVIC composite.
 */
@Component
public interface NetworkModel {

}
