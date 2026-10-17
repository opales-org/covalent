/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl.TrainingInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingEvents;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingView;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The training run's View: keeps the latest known metrics (epoch, loss,
 * accuracy, convergence, control-enabled state) and renders them as a
 * stacked pair of two live charts (training/test loss, training/test
 * accuracy) plus a run-controls panel. Consumes {@link TrainingEvents}
 * directly (managed autowiring).
 * <p>
 * Builds its two JavaFX roots synchronously at {@code @Activation} (not
 * wrapped in {@code Platform.runLater}): {@code HmiBuilder} reads
 * {@link #getChartRoot()} / {@link #getControlsRoot()} right after this
 * component activates.
 * <p>
 * This class demonstrates the View side of two mechanisms documented in
 * more depth elsewhere in this function: its button handlers call
 * {@code actionConsumerServices.executeAction(...)} rather than any method on
 * {@code TrainingInteractionImpl} directly (see that class's javadoc
 * for the full round trip), and it fulfils {@link TrainingViewParameters}
 * by exposing its two built root nodes as read-only configuration state.
 * <p>
 * Implements {@link TrainingEvents} to receive it, same as every other
 * simple consumer in this project, but - unlike them - does not rely on
 * Covalent's automatic subscribe/unsubscribe at activation/passivation:
 * the "Freeze charts" checkbox needs to stop and resume receiving epoch
 * notifications on demand, while training keeps running underneath, so
 * this class declares a {@code @UsedEventsSubscribe} field of type
 * {@link GenericSubscribe} (see {@code doc/managed-components.md}'s
 * "Manual Subscription Without a Specific Subscribe Interface" section -
 * allowed here because {@link TrainingEvents} declares no
 * {@code @EventsSubscribe} interface of its own, and this class consumes
 * only that one kind of events) and calls
 * {@link GenericSubscribe#subscribe(Object)}/
 * {@link GenericSubscribe#unsubscribe(Object)} on it directly, with
 * {@code this} as the subscriber, from {@link #setChartsFrozen(boolean)}.
 * {@code TrainingInteractionImpl} also implements {@link TrainingEvents}
 * independently, still through ordinary automatic subscription - freezing
 * this View's charts never affects its own, separate reaction to
 * convergence and reset.
 */
@Implementation
public class TrainingViewImpl implements TrainingView, TrainingViewServices, TrainingViewParameters, TrainingEvents {

	/**
	 * Shared height of both charts, and each chart's starting preferred
	 * width; {@code HmiBuilder} stretches {@link #chartRoot} to the width
	 * of the column it places it in.
	 */
	private static final double CHART_HEIGHT = 190;

	private static final double CHART_STARTING_WIDTH = 360;

	private static final double CHART_SPACING = 8;

	@UsedServices
	private ActionConsumerServices actionConsumerServices;

	/**
	 * See this class's javadoc: manual subscription, through
	 * {@code GenericSubscribe} rather than automatic autowiring, is what
	 * lets {@link #setChartsFrozen(boolean)} stop and resume this class's
	 * own reception of {@link TrainingEvents} on demand. Declaring this
	 * field is also what suppresses automatic subscription at activation,
	 * so {@link #buildRoots()} subscribes explicitly instead.
	 */
	@UsedEventsSubscribe
	private GenericSubscribe useTrainingEventsSubscribe;

	private volatile boolean chartsFrozen;

	private volatile boolean controlsEnabled = true;

	private volatile int lastEpoch = -1;

	private volatile double lastLoss;

	private volatile double lastAccuracy;

	private volatile double lastTestLoss;

	private volatile double lastTestAccuracy;

	private volatile int convergedAtEpoch = -1;

	private Node chartRoot;

	private Node controlsRoot;

	private XYChart.Series<Number, Number> lossSeries;

	private XYChart.Series<Number, Number> testLossSeries;

	private XYChart.Series<Number, Number> accuracySeries;

	private XYChart.Series<Number, Number> testAccuracySeries;

	private Label statusLabel;

	private Button startButton;

	private Button pauseButton;

	private Button stepButton;

	private Button resetButton;

	private CheckBox freezeChartsCheckBox;

	// See DatasetViewImpl for why this guard exists.
	private volatile boolean active;

	/**
	 * {@code @Activation}: builds the two JavaFX scene-graph roots this
	 * component exposes through {@link TrainingViewParameters}
	 * ({@link #getChartRoot()}, {@link #getControlsRoot()}) and wires each
	 * control button to fire the matching {@link TrainingActionKey} through
	 * {@link ActionConsumerServices#executeAction} - never by calling
	 * {@code TrainingInteractionImpl} directly (see that class's
	 * javadoc for why). Done synchronously here, not deferred to
	 * {@code Platform.runLater}, because {@code HmiBuilder} needs both roots
	 * to already exist the moment this component finishes activating.
	 */
	@Activation
	public void buildRoots() {
		NumberAxis xAxis = new NumberAxis();
		xAxis.setLabel("Epoch");
		NumberAxis yAxis = new NumberAxis();
		yAxis.setLabel("Loss");

		LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
		chart.setTitle("Training loss");
		chart.setAnimated(false);
		chart.setCreateSymbols(false);
		this.lossSeries = new XYChart.Series<>();
		this.lossSeries.setName("training loss");
		chart.getData().add(this.lossSeries);

		this.testLossSeries = new XYChart.Series<>();
		this.testLossSeries.setName("test loss");
		chart.getData().add(this.testLossSeries);

		NumberAxis accuracyXAxis = new NumberAxis();
		accuracyXAxis.setLabel("Epoch");
		NumberAxis accuracyYAxis = new NumberAxis(0, 100, 10);
		accuracyYAxis.setLabel("Accuracy (%)");

		LineChart<Number, Number> accuracyChart = new LineChart<>(accuracyXAxis, accuracyYAxis);
		accuracyChart.setTitle("Accuracy");
		accuracyChart.setAnimated(false);
		accuracyChart.setCreateSymbols(false);
		this.accuracySeries = new XYChart.Series<>();
		this.accuracySeries.setName("training accuracy");
		accuracyChart.getData().add(this.accuracySeries);

		this.testAccuracySeries = new XYChart.Series<>();
		this.testAccuracySeries.setName("test accuracy");
		accuracyChart.getData().add(this.testAccuracySeries);

		// Stacked, both the same size: the shared ALWAYS grow priority
		// below splits any extra height equally, and the width is decided
		// by HmiBuilder stretching this whole column, never by this method.
		chart.setPrefSize(CHART_STARTING_WIDTH, CHART_HEIGHT);
		accuracyChart.setPrefSize(CHART_STARTING_WIDTH, CHART_HEIGHT);
		chart.setMaxWidth(Double.MAX_VALUE);
		accuracyChart.setMaxWidth(Double.MAX_VALUE);
		VBox charts = new VBox(CHART_SPACING, chart, accuracyChart);
		charts.setPadding(new Insets(8));
		charts.getStyleClass().add("card");
		VBox.setVgrow(chart, Priority.ALWAYS);
		VBox.setVgrow(accuracyChart, Priority.ALWAYS);
		this.chartRoot = charts;

		this.startButton = new Button("Start");
		this.pauseButton = new Button("Pause");
		this.stepButton = new Button("Step");
		this.resetButton = new Button("Reset");

		this.startButton.setOnAction(event -> this.actionConsumerServices.executeAction(TrainingActionKey.Start));
		this.pauseButton.setOnAction(event -> this.actionConsumerServices.executeAction(TrainingActionKey.Pause));
		this.stepButton.setOnAction(event -> this.actionConsumerServices.executeAction(TrainingActionKey.Step));
		this.resetButton.setOnAction(event -> this.actionConsumerServices.executeAction(TrainingActionKey.Reset));

		HBox buttons = new HBox(6, this.startButton, this.pauseButton, this.stepButton, this.resetButton);
		this.statusLabel = new Label("epoch: - | loss: - | accuracy: -");

		this.freezeChartsCheckBox = new CheckBox("Freeze charts");
		this.freezeChartsCheckBox.setOnAction(event -> this.actionConsumerServices.executeAction(
				TrainingActionKey.ToggleFreezeCharts, this.freezeChartsCheckBox.isSelected()));

		VBox controls = new VBox(8, buttons, this.freezeChartsCheckBox, this.statusLabel);
		controls.setPadding(new Insets(8));
		controls.getStyleClass().add("card");
		this.controlsRoot = controls;
		this.active = true;

		// Reproduces the automatic subscription this class would otherwise
		// get for free (see this class's javadoc for why it instead
		// declares @UsedEventsSubscribe): subscribed from here until
		// deactivate(), unless setChartsFrozen(true) is called first.
		this.useTrainingEventsSubscribe.subscribe(this);
	}

	/**
	 * {@code @Passivation}: the mirror of {@link #buildRoots()}. Clears the
	 * {@code active} guard so any {@code Platform.runLater} callback still
	 * queued from an event or action in flight becomes a no-op instead of
	 * touching JavaFX controls (or a stale {@code @UsedServices} proxy) after
	 * this component is no longer live.
	 */
	@Passivation
	public void deactivate() {
		this.active = false;
		// Mirrors the subscribe() call in buildRoots() - but only if the
		// charts are not currently frozen, since a frozen view is already
		// unsubscribed (see setChartsFrozen(boolean)); unsubscribing twice
		// is rejected by Covalent.
		if (!this.chartsFrozen) {
			this.useTrainingEventsSubscribe.unsubscribe(this);
		}
	}

	@Override
	public Node getChartRoot() {
		return this.chartRoot;
	}

	@Override
	public Node getControlsRoot() {
		return this.controlsRoot;
	}

	@Override
	public void setControlsEnabled(final boolean enabled) {
		this.controlsEnabled = enabled;
		Platform.runLater(() -> {
			if (this.active && this.startButton != null) {
				this.startButton.setDisable(!enabled);
				this.stepButton.setDisable(!enabled);
			}
		});
	}

	@Override
	public void highlightConvergence(final int atEpoch) {
		this.convergedAtEpoch = atEpoch;
		Platform.runLater(() -> {
			if (this.active && this.statusLabel != null) {
				this.statusLabel.setText(this.statusLabel.getText() + "  -  converged at epoch " + atEpoch);
			}
		});
	}

	/**
	 * See this class's javadoc. Guarded against redundant subscribe/unsubscribe
	 * calls (Covalent rejects both): a no-op if {@code frozen} already
	 * matches {@link #chartsFrozen}. Keeps {@link #freezeChartsCheckBox} in
	 * sync, so a forced unfreeze - {@code TrainingInteractionImpl}
	 * calls this with {@code false} right before a reset, so the charts are
	 * subscribed again in time to receive and act on
	 * {@link TrainingEvents#trainingReset()} - also unchecks the box.
	 */
	@Override
	public void setChartsFrozen(final boolean frozen) {
		if (frozen == this.chartsFrozen) {
			return;
		}
		this.chartsFrozen = frozen;
		if (frozen) {
			this.useTrainingEventsSubscribe.unsubscribe(this);
		} else {
			this.useTrainingEventsSubscribe.subscribe(this);
		}
		if (this.freezeChartsCheckBox != null) {
			this.freezeChartsCheckBox.setSelected(frozen);
		}
	}

	@Override
	public void epochStarted(final int epoch) {
		this.lastEpoch = epoch;
	}

	@Override
	public void epochCompleted(final int epoch, final double loss, final double accuracy) {
		this.lastEpoch = epoch;
		this.lastLoss = loss;
		this.lastAccuracy = accuracy;
		Platform.runLater(() -> {
			if (!this.active) {
				return;
			}
			if (this.lossSeries != null) {
				this.lossSeries.getData().add(new XYChart.Data<>(epoch, loss));
			}
			if (this.accuracySeries != null) {
				this.accuracySeries.getData().add(new XYChart.Data<>(epoch, accuracy * 100));
			}
			this.refreshStatusLabel();
		});
	}

	@Override
	public void testEpochCompleted(final int epoch, final double testLoss, final double testAccuracy) {
		this.lastTestLoss = testLoss;
		this.lastTestAccuracy = testAccuracy;
		Platform.runLater(() -> {
			if (!this.active) {
				return;
			}
			if (this.testLossSeries != null) {
				this.testLossSeries.getData().add(new XYChart.Data<>(epoch, testLoss));
			}
			if (this.testAccuracySeries != null) {
				this.testAccuracySeries.getData().add(new XYChart.Data<>(epoch, testAccuracy * 100));
			}
			this.refreshStatusLabel();
		});
	}

	@Override
	public void trainingConverged(final int epoch, final double finalLoss) {
		this.lastEpoch = epoch;
		this.lastLoss = finalLoss;
	}

	@Override
	public void trainingReset() {
		this.lastEpoch = -1;
		this.lastLoss = 0.0;
		this.lastAccuracy = 0.0;
		this.lastTestLoss = 0.0;
		this.lastTestAccuracy = 0.0;
		this.convergedAtEpoch = -1;
		Platform.runLater(() -> {
			if (!this.active) {
				return;
			}
			if (this.lossSeries != null) {
				this.lossSeries.getData().clear();
			}
			if (this.testLossSeries != null) {
				this.testLossSeries.getData().clear();
			}
			if (this.accuracySeries != null) {
				this.accuracySeries.getData().clear();
			}
			if (this.testAccuracySeries != null) {
				this.testAccuracySeries.getData().clear();
			}
			if (this.statusLabel != null) {
				this.statusLabel.setText("epoch: - | loss: - | accuracy: - | test loss: - | test accuracy: -");
			}
		});
	}

	/**
	 * Rebuilds {@link #statusLabel}'s text from every metric currently held
	 * ({@link #lastEpoch}, {@link #lastLoss}, {@link #lastAccuracy},
	 * {@link #lastTestLoss}, {@link #lastTestAccuracy}). Called from both
	 * {@link #epochCompleted(int, double, double)} and
	 * {@link #testEpochCompleted(int, double, double)}: those two
	 * notifications always arrive for the same epoch, one right after the
	 * other (see {@link TrainingEvents}'s javadoc), but keeping one shared
	 * formatting method - rather than duplicating the
	 * {@code String.format(...)} call in both - means the label is always
	 * built from every metric held so far, whichever of the two notifications
	 * most recently arrived. Must only run on the JavaFX Application Thread
	 * (both callers already wrap it in {@code Platform.runLater}).
	 */
	private void refreshStatusLabel() {
		if (this.statusLabel == null) {
			return;
		}
		this.statusLabel.setText(String.format(
				"epoch: %d | loss: %.4f | accuracy: %.1f%% | test loss: %.4f | test accuracy: %.1f%%",
				this.lastEpoch, this.lastLoss, this.lastAccuracy * 100, this.lastTestLoss,
				this.lastTestAccuracy * 100));
	}

	/**
	 * Returns the number of the last epoch reported.
	 *
	 * @return the last epoch reported, or -1 if none yet
	 */
	public int getLastEpoch() {
		return this.lastEpoch;
	}

	/**
	 * Returns the held-out test-subset loss of the last reported epoch.
	 *
	 * @return the last reported held-out test-subset loss
	 */
	public double getLastTestLoss() {
		return this.lastTestLoss;
	}

	/**
	 * Returns the held-out test-subset accuracy of the last reported epoch.
	 *
	 * @return the last reported held-out test-subset accuracy, in [0, 1]
	 */
	public double getLastTestAccuracy() {
		return this.lastTestAccuracy;
	}
}
