/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.DatasetExplorer;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl.DatasetControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.impl.DatasetExplorerImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the DatasetExplorer's Business Controller (DatasetController), per the MVIC pattern.
 * <p>
 * {@code @Component} on a bare interface (no methods, no body) is a pure
 * <em>type marker</em>: it names one logical piece of an MVIC decomposition
 * so the container's lifecycle and wiring machinery has a stable type to key
 * on. Covalent component types are mono-instantiated - at runtime there is
 * exactly one instance of {@code DatasetController} - and this type is
 * fulfilled by exactly one {@code @Implementation} class,
 * {@link DatasetControllerImpl}. The interface carries no methods of its
 * own: everything callable is declared on separate {@code @Services} (or
 * {@code @Events}, {@code @Parameters}) contracts, kept distinct from the
 * component type itself so a caller depends only on the contract it needs,
 * never on "being a DatasetController". See {@link DatasetExplorerImpl} for
 * how the four MVIC component types (Model, Controller, Interaction,
 * View) are grouped and deployed together as one composite.
 */
@Component
public interface DatasetController {

}
