/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.NetworkEvents;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.impl.NetworkModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkView;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkViewServices;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/**
 * The network's View: keeps the latest known topology and weights per layer,
 * and the currently highlighted layer, and renders the layer structure as a
 * simple node-link diagram, plus a small "try it yourself" prediction panel.
 * Consumes {@link NetworkEvents} directly: implementing the events
 * interface is enough for Covalent to auto-subscribe this component, no
 * manual subscription call needed.
 * <p>
 * Holds no {@code @UsedServices} on {@code NetworkServices} at all: this
 * View is built purely from what {@link NetworkEvents} gives it. Even
 * though {@code NetworkController} may already have initialized the
 * network before this View activates, {@code NetworkModelImpl} implements
 * Covalent's event subscription delegation (see its javadoc), so the
 * moment this View's automatic subscription is registered it immediately
 * receives a {@link #networkReset(int[])} call with the current topology
 * followed by one {@link #weightsChanged(int, double[][])} call per
 * existing layer - a full catch-up. From then on the same two methods keep
 * this View in sync: {@link #networkReset(int[])} for any topology change
 * made during this View's lifetime (a hidden-layer count/size change) and
 * {@link #weightsChanged(int, double[][])} for each layer's weights.
 * <p>
 * {@code xField}/{@code yField} are instance fields, not local variables
 * confined to {@link #buildRoot()}, precisely so {@link #setPreviewInput(double, double)}
 * (the pipette's effect, reached from the dataset function through
 * {@code NetworkPreviewServices} and {@code NetworkInteractionImpl}) can reach and update them from
 * outside the method that built them.
 * <p>
 * Builds its JavaFX root synchronously (not wrapped in
 * {@code Platform.runLater}): {@code HmiBuilder} reads {@link #getRoot()}
 * right after this component activates.
 */
@Implementation
public class NetworkViewImpl implements NetworkView, NetworkViewServices, NetworkViewParameters, NetworkEvents {

	private static final double DIAGRAM_WIDTH = 360;

	private static final double DIAGRAM_HEIGHT = 190;

	private static final int MAX_DRAWN_NEURONS = 10;

	private static final double NEURON_RADIUS = 9;

	@UsedServices
	private ActionConsumerServices actionConsumerServices;

	private final Map<Integer, double[][]> layerWeights = new ConcurrentHashMap<>();

	private volatile int[] layerSizes = new int[0];

	private volatile int highlightedLayer = -1;

	private volatile double[] lastPrediction = new double[0];

	private Node root;

	private Canvas diagramCanvas;

	private Label predictionLabel;

	private TextField xField;

	private TextField yField;

	// See DatasetViewImpl for why this guard exists.
	private volatile boolean active;

	/** Builds the network diagram canvas and the prediction preview row at activation. */
	@Activation
	public void buildRoot() {
		this.diagramCanvas = new Canvas(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
		this.diagramCanvas.setOnMouseClicked(event -> this.handleDiagramClick(event.getX()));

		this.xField = new TextField("0.5");
		this.xField.setPrefWidth(60);
		this.yField = new TextField("0.5");
		this.yField.setPrefWidth(60);
		this.predictionLabel = new Label("output: -");

		Button predictButton = new Button("Predict");
		predictButton.setOnAction(event -> {
			double x = parseOrZero(this.xField.getText());
			double y = parseOrZero(this.yField.getText());
			this.actionConsumerServices.executeAction(NetworkActionKey.PreviewPrediction, new double[] { x, y });
		});

		HBox previewRow = new HBox(6, new Label("x:"), this.xField, new Label("y:"), this.yField, predictButton,
				this.predictionLabel);
		previewRow.setPadding(new Insets(6, 0, 0, 0));

		BorderPane pane = new BorderPane();
		pane.setPadding(new Insets(8));
		pane.setCenter(this.diagramCanvas);
		pane.setBottom(previewRow);

		pane.getStyleClass().add("card");
		this.root = pane;
		this.active = true;
		this.redraw();
	}

	/**
	 * Marks the view inactive, so that queued JavaFX callbacks stop using the services of the passivated component.
	 */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	private static double parseOrZero(final String text) {
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	private void handleDiagramClick(final double clickX) {
		if (!this.active) {
			return;
		}
		int layerCount = this.layerSizes.length;
		if (layerCount <= 0) {
			return;
		}
		double step = DIAGRAM_WIDTH / layerCount;
		int layerIndex = (int) Math.min(layerCount - 1, Math.max(0, clickX / step));
		this.actionConsumerServices.executeAction(NetworkActionKey.SelectLayer, layerIndex);
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	@Override
	public void setHighlightedLayer(final int layerIndex) {
		this.highlightedLayer = layerIndex;
		Platform.runLater(this::redraw);
	}

	@Override
	public void networkReset(final int[] layerSizes) {
		// The topology changed (a hidden-layer count/size change made
		// during this View's lifetime): drop everything we knew, including
		// any transition index that may no longer exist, so a shrinking
		// topology never leaves a stale, mismatched entry behind.
		this.layerSizes = layerSizes.clone();
		this.layerWeights.clear();
		Platform.runLater(this::redraw);
	}

	@Override
	public void weightsChanged(final int layerIndex, final double[][] weightsSnapshot) {
		this.layerWeights.put(layerIndex, weightsSnapshot);
		Platform.runLater(this::redraw);
	}

	@Override
	public void displayPrediction(final double[] output) {
		this.lastPrediction = output;
		Platform.runLater(() -> {
			if (this.active && this.predictionLabel != null && output.length > 0) {
				String cls = output[0] >= 0.5 ? "1" : "0";
				this.predictionLabel.setText(String.format("output: %.3f (class %s)", output[0], cls));
			}
		});
	}

	@Override
	public void setPreviewInput(final double x, final double y) {
		Platform.runLater(() -> {
			if (this.active && this.xField != null && this.yField != null) {
				this.xField.setText(String.format("%.3f", x));
				this.yField.setText(String.format("%.3f", y));
			}
		});
	}

	private void redraw() {
		if (!this.active || this.diagramCanvas == null) {
			return;
		}
		GraphicsContext gc = this.diagramCanvas.getGraphicsContext2D();
		gc.setFill(Color.web("#080c13"));
		gc.fillRect(0, 0, DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

		int[] layerSizes = this.layerSizes;
		int layerCount = layerSizes.length;
		if (layerCount == 0) {
			return;
		}

		double[] columnX = new double[layerCount];
		for (int l = 0; l < layerCount; l++) {
			columnX[l] = (layerCount == 1) ? DIAGRAM_WIDTH / 2.0
					: 40 + l * (DIAGRAM_WIDTH - 80) / (layerCount - 1);
		}

		// Edges first, so neurons draw on top.
		for (int l = 0; l < layerCount - 1; l++) {
			double[][] weights = this.layerWeights.get(l);
			if (weights == null) {
				continue;
			}
			int drawnIn = Math.min(layerSizes[l], MAX_DRAWN_NEURONS);
			int drawnOut = Math.min(layerSizes[l + 1], MAX_DRAWN_NEURONS);
			double maxAbsWeight = 1e-6;
			for (int o = 0; o < drawnOut; o++) {
				for (int i = 0; i < drawnIn; i++) {
					maxAbsWeight = Math.max(maxAbsWeight, Math.abs(weights[o][i]));
				}
			}
			for (int o = 0; o < drawnOut; o++) {
				double toY = this.neuronY(o, drawnOut);
				for (int i = 0; i < drawnIn; i++) {
					double fromY = this.neuronY(i, drawnIn);
					double w = weights[o][i];
					double opacity = Math.min(1.0, Math.abs(w) / maxAbsWeight * 0.9 + 0.1);
					gc.setStroke(w >= 0 ? Color.rgb(129, 140, 248, opacity) : Color.rgb(244, 114, 182, opacity));
					gc.setLineWidth(1.0);
					gc.strokeLine(columnX[l], fromY, columnX[l + 1], toY);
				}
			}
		}

		for (int l = 0; l < layerCount; l++) {
			int drawnCount = Math.min(layerSizes[l], MAX_DRAWN_NEURONS);
			boolean highlighted = (l == this.highlightedLayer);
			for (int n = 0; n < drawnCount; n++) {
				double y = this.neuronY(n, drawnCount);
				gc.setFill(highlighted ? Color.GOLD : Color.web("#131a26"));
				gc.fillOval(columnX[l] - NEURON_RADIUS, y - NEURON_RADIUS, NEURON_RADIUS * 2, NEURON_RADIUS * 2);
				gc.setStroke(Color.web("#d5dbe7"));
				gc.strokeOval(columnX[l] - NEURON_RADIUS, y - NEURON_RADIUS, NEURON_RADIUS * 2, NEURON_RADIUS * 2);
			}
			if (layerSizes[l] > MAX_DRAWN_NEURONS) {
				gc.setFill(Color.web("#8591a6"));
				gc.fillText("+" + (layerSizes[l] - MAX_DRAWN_NEURONS), columnX[l] - 10, DIAGRAM_HEIGHT - 6);
			}
		}
	}

	private double neuronY(final int index, final int count) {
		double usableHeight = DIAGRAM_HEIGHT - 40;
		if (count <= 1) {
			return DIAGRAM_HEIGHT / 2.0;
		}
		return 20 + index * usableHeight / (count - 1);
	}

	/**
	 * Returns the last weight matrix received for a layer.
	 *
	 * @param layerIndex the layer index, 0-based
	 * @return the last known weights for the given layer, or {@code null} if none yet
	 */
	public double[][] getLayerWeights(final int layerIndex) {
		return this.layerWeights.get(layerIndex);
	}
}
