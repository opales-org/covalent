/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.DatasetExplorer;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl.DatasetControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.impl.DatasetInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.impl.DatasetModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code DatasetExplorer} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 * <p>
 * Each field below is itself annotated {@code @Implementation}, on top of
 * its own class already carrying {@code @Implementation}: this is Covalent's
 * composition mechanism (composed sub-components, not the finer-grained
 * "component items" mechanism used to split a single component's own
 * implementation across several classes). The container resolves these
 * fields when it instantiates {@code DatasetExplorerImpl}, and each
 * sub-component keeps its own component type, ports and lifecycle state -
 * they are wired, activated, passivated and removed together with this
 * composite, in the usual Model/Controller/Interaction/View order
 * their services and events dependencies require. Grouping them here means
 * callers only ever need to know about and deploy {@code DatasetExplorerImpl}
 * to get the whole dataset function running; they never construct
 * {@code DatasetModelImpl}, {@code DatasetControllerImpl}, etc. directly.
 */
@Implementation
public class DatasetExplorerImpl implements DatasetExplorer {

	@Implementation
	private DatasetModelImpl model;

	@Implementation
	private DatasetControllerImpl controller;

	@Implementation
	private DatasetViewImpl view;

	@Implementation
	private DatasetInteractionImpl interaction;
}
