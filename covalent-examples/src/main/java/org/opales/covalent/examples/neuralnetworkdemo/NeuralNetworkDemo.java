/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the whole Neural Network Trainer demo: a single
 * top-level composite grouping every function of the application (dataset
 * exploration, the network model, training, user configuration, and the
 * HMI assembly) under one {@code @Implementation}.
 * <p>
 * See {@code NetworkController} (in the {@code neuralnetworkmodel.controller}
 * package) for what a bare {@code @Component} marker interface means in
 * Covalent; this one names the demo's own root composite rather than one of
 * its individual parts. See {@code NeuralNetworkDemoImpl} for how the
 * composite groups the whole application together for deployment.
 */
@Component
public interface NeuralNetworkDemo {

}
