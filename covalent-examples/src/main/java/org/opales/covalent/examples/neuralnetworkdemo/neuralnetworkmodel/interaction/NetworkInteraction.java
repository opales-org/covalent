/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the NeuralNetworkModel's Interaction Controller (NetworkInteraction), per the MVIC pattern.
 * <p>
 * See {@link NetworkController} for what a bare {@code @Component} marker
 * interface means in Covalent; this one names the Interaction part of
 * the same MVIC composite.
 */
@Component
public interface NetworkInteraction {

}
