/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetShape;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.impl.DatasetInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the dataset view, provided by {@code DatasetView}
 * and used by {@code DatasetInteraction}.
 * <p>
 * Unlike {@link DatasetViewParameters#getRoot()}, these are actual
 * operations carried out on the View - toggling its busy indicator, syncing
 * its shape-selector toggle - so they are modeled as a {@code @Services}
 * contract rather than parameters, and it is the Interaction Controller, not
 * {@code HmiBuilder}, that calls them: exactly the role split documented on
 * {@code DatasetInteractionImpl}.
 */
@Services
public interface DatasetViewServices {

	/**
	 * Indicates whether a regeneration is in progress, e.g. to show a busy
	 * indicator over the point cloud.
	 *
	 * @param regenerating {@code true} while a regeneration is in progress
	 */
	void setRegenerating(boolean regenerating);

	/**
	 * Marks {@code shape} as the selected one in the shape-selector toggle
	 * group, without firing {@link DatasetActionKey#SelectShape} back
	 * (unlike a user click) - used both to push the initial shape at
	 * activation and to keep the toggle in sync after a selection is
	 * confirmed.
	 *
	 * @param shape the shape to show as selected
	 */
	void displaySelectedShape(DatasetShape shape);
}
