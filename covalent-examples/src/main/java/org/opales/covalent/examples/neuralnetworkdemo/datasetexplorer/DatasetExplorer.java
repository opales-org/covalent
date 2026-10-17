/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl.DatasetControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.impl.DatasetExplorerImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the dataset MVIC (Model/Controller/Interaction/View).
 * See {@link DatasetControllerImpl} and {@link DatasetExplorerImpl} for what
 * fulfilling and composing component types means in practice; unlike the
 * four component types it groups, this one is never used to look up a
 * service or event contract - it exists only as the deployment root, see
 * {@link DatasetExplorerImpl}.
 */
@Component
public interface DatasetExplorer {

}
