/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.hmibuilder;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.impl.HmiBuilderImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the demo's pure HMI assembly component: builds the
 * JavaFX scene graph and embeds each function's view root(s), one per leaf
 * function. No business logic, and it consumes no domain event - it only
 * accesses each function's {@code *ViewParameters} to read its already-built
 * root node(s).
 * <p>
 * See {@link DatasetController} for what a bare {@code @Component} marker
 * interface means in Covalent in general. Unlike every other
 * {@code @Component} type in this demo, {@code HmiBuilder} is not one part
 * of an MVIC composite: it is a direct child of the {@code NeuralNetworkDemoImpl}
 * composite, declared as its very last field, so that every function's
 * View has already activated - and therefore already built its root
 * node(s) - by the time {@link HmiBuilderImpl} reads them.
 */
@Component
public interface HmiBuilder {

}
