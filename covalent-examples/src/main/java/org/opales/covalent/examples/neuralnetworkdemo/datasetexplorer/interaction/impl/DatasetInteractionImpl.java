/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetControllerServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetShape;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetViewServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkPreviewServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.impl.UserConfigurationInteractionImpl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The dataset's Interaction Controller: registers its actions with the HMI's
 * {@code ActionManager} at activation - the view's "regenerate" button,
 * shape selector and canvas-click pipette all trigger them through
 * {@code ActionConsumerServices.executeAction(...)}, not by calling this
 * class directly.
 * <p>
 * This class is the pivot of the MVIC pattern: it is the only piece of the
 * function that touches both a View's {@code @UsedServices ViewServices}
 * (to command the View, e.g. {@link DatasetViewServices#setRegenerating(boolean)})
 * and a Controller's {@code @UsedServices ControllerServices}/{@code Services}
 * (to trigger business logic, e.g. {@link DatasetControllerServices#regenerate(long, double)}),
 * and it is where {@code ActionProviderServices#registerActionExecution}
 * calls live. It has no JavaFX code of its own: building and laying out
 * nodes is entirely the View's responsibility.
 * <p>
 * The pipette ({@link DatasetActionKey#PickPredictionPoint}) is this
 * function's one cross-function reach: it needs to push the picked
 * coordinates into the <em>network</em> view's Predict fields, not its own
 * dataset view's - so, exactly like {@code UserConfigurationInteractionImpl}
 * reaching into {@code NetworkParameters}/{@code TrainingParameters} through
 * extra {@code @AccessedParameters} fields, this Interaction Controller holds an
 * extra {@code @UsedServices} field into {@code NeuralNetworkModel}'s
 * function - {@link NetworkPreviewServices} - purely to call
 * {@link NetworkPreviewServices#previewInput(double, double)}. That
 * interface is {@code NeuralNetworkModel}'s own public Interaction-layer
 * entry point, which internally delegates to its otherwise strictly
 * private {@code NetworkViewServices}; nothing else about
 * {@code NeuralNetworkModel} is touched.
 * <p>
 * A View never calls its Interaction Controller directly for a user-triggered
 * action; instead it calls {@code actionConsumerServices.executeAction(SomeActionKey, args...)}
 * (from a {@code @UsedServices ActionConsumerServices} field on the View),
 * which routes to whatever was registered here under that
 * {@link DatasetActionKey}. This indirection decouples "what a control does"
 * (the View) from "how that gets implemented" (this Interaction Controller), and
 * lets the framework centrally track which actions currently exist and are
 * available.
 */
@Implementation
public class DatasetInteractionImpl implements DatasetInteraction {

	@UsedServices
	private ActionProviderServices actionProviderServices;

	@UsedServices
	private DatasetViewServices useDatasetViewServices;

	@UsedServices
	private DatasetControllerServices useDatasetControllerServices;

	/**
	 * See this class's javadoc for why this field exists: it is the sole
	 * reach into {@code NeuralNetworkModel}'s function, used only by
	 * {@link #pickPredictionPoint(double[])}.
	 */
	@UsedServices
	private NetworkPreviewServices useNetworkPreviewServices;

	/**
	 * {@code @Activation} runs once dependencies are wired and the component
	 * is becoming live: it is the point at which registering this function's
	 * actions with the {@code ActionManager} is meaningful, since the View
	 * that will invoke them (through {@code ActionConsumerServices.executeAction(...)})
	 * can reach them. {@link #unregisterActions()} undoes this
	 * registration at {@code @Passivation}, the mirror lifecycle hook.
	 * <p>
	 * Also pushes the currently selected shape to the View, exactly like
	 * {@code UserConfigurationInteractionImpl} pushes each parameter's
	 * current value at activation - so the shape-selector toggle that
	 * starts out checked always matches what {@code DatasetController}
	 * actually generated, rather than an assumption baked into the View.
	 */
	@Activation
	public void registerActions() {
		this.actionProviderServices.registerActionExecution(
				DatasetActionKey.Regenerate, this::regenerateDataset, Long.class, Double.class);
		this.actionProviderServices.registerActionExecution(
				DatasetActionKey.SelectShape, this::selectShape, DatasetShape.class);
		this.actionProviderServices.registerActionExecution(
				DatasetActionKey.PickPredictionPoint, this::pickPredictionPoint, double[].class);

		this.useDatasetViewServices.displaySelectedShape(this.useDatasetControllerServices.getShape());
	}

	/** Unregisters at passivation the actions registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.actionProviderServices.unregisterActionExecution(DatasetActionKey.Regenerate);
		this.actionProviderServices.unregisterActionExecution(DatasetActionKey.SelectShape);
		this.actionProviderServices.unregisterActionExecution(DatasetActionKey.PickPredictionPoint);
	}

	/**
	 * Requests a dataset regeneration, flagging the view as busy first.
	 *
	 * @param seed  the random seed to regenerate with
	 * @param noise the class-overlap noise level
	 */
	private void regenerateDataset(final Long seed, final Double noise) {
		this.useDatasetViewServices.setRegenerating(true);
		this.useDatasetControllerServices.regenerate(seed, noise);
	}

	/**
	 * Switches the generated shape, flagging the view as busy first (a
	 * shape change regenerates the whole dataset - see
	 * {@link DatasetControllerServices#selectShape(DatasetShape)}) and
	 * confirms the new selection back to the View so its toggle group
	 * stays in sync even if the click came from somewhere other than the
	 * toggle itself.
	 *
	 * @param shape the shape to switch to
	 */
	private void selectShape(final DatasetShape shape) {
		this.useDatasetViewServices.setRegenerating(true);
		this.useDatasetControllerServices.selectShape(shape);
		this.useDatasetViewServices.displaySelectedShape(shape);
	}

	/**
	 * The pipette: pushes a canvas-picked point straight into the network
	 * view's Predict panel fields - see this class's javadoc for why that
	 * crosses into {@link NetworkPreviewServices}. Deliberately does not also
	 * trigger a prediction: the pipette only fills the coordinates,
	 * and the user presses "Predict" themselves.
	 *
	 * @param xy the picked point, as {@code {x, y}}
	 */
	private void pickPredictionPoint(final double[] xy) {
		this.useNetworkPreviewServices.previewInput(xy[0], xy[1]);
	}
}
