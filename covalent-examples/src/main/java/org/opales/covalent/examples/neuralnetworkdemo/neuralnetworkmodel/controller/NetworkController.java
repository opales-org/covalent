/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl.NetworkControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.impl.NeuralNetworkModelImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the NeuralNetworkModel's Business Controller (NetworkController), per the MVIC pattern.
 * <p>
 * A {@code @Component} interface, with no methods of its own, is a pure
 * <em>type marker</em>: it exists only so the framework's lifecycle and
 * registry machinery has a stable, named type to key on for one logical
 * piece of an MVIC decomposition (here, the Controller). Exactly one
 * {@code @Implementation} class fulfills this type - {@link NetworkControllerImpl} -
 * and that class is what actually gets deployed (directly, or as part of a
 * composite, see {@link NeuralNetworkModelImpl}) via
 * {@code CovalentContainer.deployComponents(...)}. Any behavior this
 * component exposes is declared separately, through
 * {@code @Services}/{@code @Parameters}/{@code @Events} ports its
 * implementation also implements.
 */
@Component
public interface NetworkController {

}
