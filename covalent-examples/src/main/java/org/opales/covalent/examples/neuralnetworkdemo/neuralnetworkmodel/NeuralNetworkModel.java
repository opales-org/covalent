/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.impl.NeuralNetworkModelImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the network model MVIC (Model/Controller/Interaction/View).
 * Deliberately kept as a single composite for the whole network, not split per layer.
 * <p>
 * See {@link NetworkController} for what a bare {@code @Component} marker
 * interface means in Covalent; this one names the whole four-part MVIC
 * composite rather than one of its individual parts. See
 * {@link NeuralNetworkModelImpl} for how the composite's
 * {@code @Implementation} groups those four parts together for deployment.
 */
@Component
public interface NeuralNetworkModel {

}
