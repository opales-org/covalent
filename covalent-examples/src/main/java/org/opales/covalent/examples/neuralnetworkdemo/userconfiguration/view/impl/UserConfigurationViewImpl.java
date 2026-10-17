/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.impl.HmiBuilderImpl;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.UserConfigurationInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.UserConfigurationView;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.UserConfigurationViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.UserConfigurationViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

/**
 * The configuration composite's View: keeps the last known value of every
 * user-editable parameter, as pushed by
 * {@code UserConfigurationInteraction}, and renders one control per
 * parameter (sliders for continuous values, spinners for integer counts).
 * Has no Model/Events to consume, unlike the other functions' Views: this
 * composite owns no state of its own, it only fronts other functions'
 * Parameters.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation}: {@code
 * HmiBuilder} reads {@link #getRoot()} right after this component
 * activates, and {@code UserConfigurationInteraction} (which depends
 * on this View's services) pushes the initial values into the already-built
 * controls right after, at its own {@code @Activation}.
 * <p>
 * Every control's listener below calls {@code actionConsumerServices.executeAction(...)}
 * with the matching {@link UserConfigurationActionKey}, never
 * {@code UserConfigurationInteraction} directly - the same
 * View/ActionManager/Interaction indirection documented on
 * {@link DatasetActionKey}, applied here to six independent controls
 * instead of one.
 * <p>
 * <b>Re-entrancy guard ({@link #applyingDisplayedValue}):</b> every
 * {@code display*} method below pushes its value onto the matching control
 * via a deferred {@code Platform.runLater(...)} (needed because {@code
 * display*} can be called from outside the JavaFX Application Thread), and
 * every control's change listener reacts to ANY value change - including
 * one caused by this deferred push itself - by calling {@code
 * executeAction(...)} again. When a {@code display*} call's value differs
 * from what the control currently shows at the time its deferred push
 * finally runs (which happens whenever an action is triggered from outside
 * the control itself - e.g. two {@code executeAction} calls issued back to
 * back, or this composite's own initial value push at
 * {@code @Activation} racing a very early external change), the listener
 * fires, re-triggers the action, which schedules yet another deferred push
 * - and so on forever, each round undoing the previous one instead of
 * settling. For instance, setting the hidden layer count and then its size
 * in quick succession would leave the network's topology oscillating
 * indefinitely between old and new values instead of settling on the
 * requested one. The standard "controlled component" guard prevents it: {@link #applyingDisplayedValue} is set
 * around every programmatic {@code setValue(...)} call, and every listener
 * ignores the change it observes while this flag is set. One shared flag is
 * enough for all six controls (rather than one per control) because
 * JavaFX runs every {@code Platform.runLater} task to completion, in order,
 * on this single Application Thread - there is never more than one
 * programmatic update in flight at a time, so nothing here needs to
 * distinguish which control is currently being synced.
 */
@Implementation
public class UserConfigurationViewImpl
		implements UserConfigurationView, UserConfigurationViewServices, UserConfigurationViewParameters {

	@UsedServices
	private ActionConsumerServices actionConsumerServices;

	private volatile double learningRate;

	private volatile int hiddenLayerCount;

	private volatile int hiddenLayerSize;

	private volatile int batchSize;

	private volatile double datasetNoise;

	private volatile double epochsPerSecond;

	private Node root;

	private Slider learningRateSlider;

	private Spinner<Integer> hiddenLayerCountSpinner;

	private Spinner<Integer> hiddenLayerSizeSpinner;

	private Spinner<Integer> batchSizeSpinner;

	private Slider datasetNoiseSlider;

	private Slider epochsPerSecondSlider;

	// See DatasetViewImpl for why this guard exists.
	private volatile boolean active;

	// See the class javadoc's "Re-entrancy guard" section: true while a
	// display* method is applying a value to its control programmatically,
	// so the control's own change listener knows to ignore that change
	// instead of re-triggering executeAction(...). Only ever touched on the
	// JavaFX Application Thread (from inside a Platform.runLater task), so
	// it needs no synchronization despite being read from listener
	// callbacks that also only ever run on that same thread.
	private boolean applyingDisplayedValue;

	/**
	 * Builds the controls grid synchronously (see the class javadoc for why
	 * this cannot be deferred to {@code Platform.runLater} like
	 * {@code HmiBuilderImpl}'s own window creation), seeding each control
	 * from the last value pushed via {@code UserConfigurationViewServices}
	 * (zero-valued defaults until {@code UserConfigurationInteraction}
	 * pushes the real ones at its own {@code @Activation}), and wires each
	 * control's change listener to execute the matching
	 * {@link UserConfigurationActionKey} through {@code ActionConsumerServices}
	 * - except while {@link #applyingDisplayedValue} is set, see the class
	 * javadoc.
	 */
	@Activation
	public void buildRoot() {
		GridPane grid = new GridPane();
		grid.setPadding(new Insets(8));
		grid.setHgap(8);
		grid.setVgap(6);
		ColumnConstraints labelColumn = new ColumnConstraints(170);
		ColumnConstraints controlColumn = new ColumnConstraints(160);
		grid.getColumnConstraints().addAll(labelColumn, controlColumn);

		this.learningRateSlider = new Slider(0.001, 1.0, this.learningRate);
		this.hiddenLayerCountSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 8,
				this.hiddenLayerCount));
		this.hiddenLayerSizeSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 64,
				Math.max(1, this.hiddenLayerSize)));
		this.batchSizeSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 256,
				Math.max(1, this.batchSize)));
		this.datasetNoiseSlider = new Slider(0.0, 0.5, this.datasetNoise);
		this.epochsPerSecondSlider = new Slider(0.5, 30.0, this.epochsPerSecond);

		this.hiddenLayerCountSpinner.setEditable(true);
		this.hiddenLayerSizeSpinner.setEditable(true);
		this.batchSizeSpinner.setEditable(true);

		int row = 0;
		row = this.addRow(grid, row, "Learning rate", this.learningRateSlider);
		row = this.addRow(grid, row, "Hidden layer count", this.hiddenLayerCountSpinner);
		row = this.addRow(grid, row, "Hidden layer size", this.hiddenLayerSizeSpinner);
		row = this.addRow(grid, row, "Batch size", this.batchSizeSpinner);
		row = this.addRow(grid, row, "Dataset noise", this.datasetNoiseSlider);
		row = this.addRow(grid, row, "Training pace (epochs/s)", this.epochsPerSecondSlider);

		// Each listener only ever asks ActionConsumerServices to execute the
		// matching UserConfigurationActionKey - it never calls
		// UserConfigurationInteraction's implementation directly - and
		// ignores changes caused by our own display* methods applying a
		// value programmatically (see the class javadoc's "Re-entrancy
		// guard" section).
		this.learningRateSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices
						.executeAction(UserConfigurationActionKey.SetLearningRate, newValue.doubleValue());
			}
		});
		this.hiddenLayerCountSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices.executeAction(UserConfigurationActionKey.SetHiddenLayerCount, newValue);
			}
		});
		this.hiddenLayerSizeSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices.executeAction(UserConfigurationActionKey.SetHiddenLayerSize, newValue);
			}
		});
		this.batchSizeSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices.executeAction(UserConfigurationActionKey.SetBatchSize, newValue);
			}
		});
		this.datasetNoiseSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices
						.executeAction(UserConfigurationActionKey.SetDatasetNoise, newValue.doubleValue());
			}
		});
		this.epochsPerSecondSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (!this.applyingDisplayedValue) {
				this.actionConsumerServices
						.executeAction(UserConfigurationActionKey.SetEpochsPerSecond, newValue.doubleValue());
			}
		});

		grid.getStyleClass().add("card");
		this.root = grid;
		this.active = true;
	}

	/**
	 * Marks the view inactive, so that queued JavaFX callbacks stop using the services of the passivated component.
	 */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	private int addRow(final GridPane grid, final int row, final String labelText, final Node control) {
		grid.add(new Label(labelText), 0, row);
		grid.add(control, 1, row);
		return row + 1;
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	/**
	 * Runs {@code apply} with {@link #applyingDisplayedValue} set, so any
	 * control-change listener triggered by it (directly, or through a
	 * chain of {@code Platform.runLater} tasks queued while it runs) knows
	 * to ignore that change instead of re-triggering
	 * {@code executeAction(...)} - see the class javadoc's "Re-entrancy
	 * guard" section. Always called from inside a {@code Platform.runLater}
	 * task, so this never races the listeners it is guarding against.
	 */
	private void applyDisplayedValue(final Runnable apply) {
		this.applyingDisplayedValue = true;
		try {
			apply.run();
		} finally {
			this.applyingDisplayedValue = false;
		}
	}

	@Override
	public void displayLearningRate(final double learningRate) {
		this.learningRate = learningRate;
		Platform.runLater(() -> {
			if (this.active && this.learningRateSlider != null) {
				this.applyDisplayedValue(() -> this.learningRateSlider.setValue(learningRate));
			}
		});
	}

	@Override
	public void displayHiddenLayerCount(final int hiddenLayerCount) {
		this.hiddenLayerCount = hiddenLayerCount;
		Platform.runLater(() -> {
			if (this.active && this.hiddenLayerCountSpinner != null) {
				this.applyDisplayedValue(() -> this.hiddenLayerCountSpinner.getValueFactory().setValue(hiddenLayerCount));
			}
		});
	}

	@Override
	public void displayHiddenLayerSize(final int hiddenLayerSize) {
		this.hiddenLayerSize = hiddenLayerSize;
		Platform.runLater(() -> {
			if (this.active && this.hiddenLayerSizeSpinner != null) {
				this.applyDisplayedValue(() -> this.hiddenLayerSizeSpinner.getValueFactory().setValue(hiddenLayerSize));
			}
		});
	}

	@Override
	public void displayBatchSize(final int batchSize) {
		this.batchSize = batchSize;
		Platform.runLater(() -> {
			if (this.active && this.batchSizeSpinner != null) {
				this.applyDisplayedValue(() -> this.batchSizeSpinner.getValueFactory().setValue(batchSize));
			}
		});
	}

	@Override
	public void displayDatasetNoise(final double noise) {
		this.datasetNoise = noise;
		Platform.runLater(() -> {
			if (this.active && this.datasetNoiseSlider != null) {
				this.applyDisplayedValue(() -> this.datasetNoiseSlider.setValue(noise));
			}
		});
	}

	@Override
	public void displayEpochsPerSecond(final double epochsPerSecond) {
		this.epochsPerSecond = epochsPerSecond;
		Platform.runLater(() -> {
			if (this.active && this.epochsPerSecondSlider != null) {
				this.applyDisplayedValue(() -> this.epochsPerSecondSlider.setValue(epochsPerSecond));
			}
		});
	}

	/**
	 * Returns the learning rate last pushed to the view.
	 *
	 * @return the last displayed learning rate
	 */
	public double getLearningRate() {
		return this.learningRate;
	}

	/**
	 * Returns the number of hidden layers last pushed to the view.
	 *
	 * @return the last displayed number of hidden layers
	 */
	public int getHiddenLayerCount() {
		return this.hiddenLayerCount;
	}

	/**
	 * Returns the hidden layer size last pushed to the view.
	 *
	 * @return the last displayed hidden layer size
	 */
	public int getHiddenLayerSize() {
		return this.hiddenLayerSize;
	}

	/**
	 * Returns the mini-batch size last pushed to the view.
	 *
	 * @return the last displayed mini-batch size
	 */
	public int getBatchSize() {
		return this.batchSize;
	}

	/**
	 * Returns the dataset noise level last pushed to the view.
	 *
	 * @return the last displayed dataset noise level
	 */
	public double getDatasetNoise() {
		return this.datasetNoise;
	}

	/**
	 * Returns the training pace last pushed to the view.
	 *
	 * @return the last displayed training pace, in epochs per second
	 */
	public double getEpochsPerSecond() {
		return this.epochsPerSecond;
	}
}
