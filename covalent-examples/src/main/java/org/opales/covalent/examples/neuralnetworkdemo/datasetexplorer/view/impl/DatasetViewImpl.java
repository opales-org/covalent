/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetGenerator;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetShape;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.DatasetEvents;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.impl.DatasetModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetView;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetViewServices;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.impl.NetworkViewImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.impl.TrainingViewImpl;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.impl.UserConfigurationViewImpl;

import java.util.EnumMap;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionConsumerServices;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The dataset's View: keeps the latest known dataset state (samples and
 * regeneration status) and renders it as a 2D point cloud, with its
 * coordinate reference frame (grid, tick labels, origin) drawn alongside
 * it, a row of shape-selector toggle buttons (one per {@link DatasetShape},
 * each with a small procedurally-drawn icon), and the "pipette": clicking
 * directly on the point cloud fires {@link DatasetActionKey#PickPredictionPoint}
 * with the click's data-space coordinates, so the user can pick a
 * prediction input visually instead of typing it into the network view's
 * Predict fields. Consumes {@link DatasetEvents} directly: simply
 * implementing the events interface is enough for Covalent to auto-subscribe
 * this component to it, no manual subscription call needed.
 * <p>
 * This View never needs to pull anything from {@link DatasetServices}: even
 * though {@code DatasetController} already generates the first dataset at
 * its own {@code @Initialization} - before this View has even activated -
 * {@code DatasetModelImpl} implements Covalent's event subscription
 * delegation (see its javadoc), so the moment this View's automatic
 * subscription is registered, it immediately receives a
 * {@link #datasetChanged(TrainingSample[])} catch-up call with whatever
 * samples exist at that point. From this View's perspective there is no
 * difference between that catch-up call and an ordinary one.
 * <p>
 * The shape-selector toggle group reuses the same reentrancy-guard idiom
 * {@code UserConfigurationViewImpl} uses for its Spinners: a
 * {@link #applyingDisplayedShape} flag set around every programmatic
 * {@link #displaySelectedShape(DatasetShape)} call, checked by the toggle
 * group's selection listener before firing {@link DatasetActionKey#SelectShape} -
 * without it, {@link #displaySelectedShape(DatasetShape)} selecting a
 * toggle would itself re-fire that same listener, which would call back
 * into the Interaction Controller, which would call
 * {@link #displaySelectedShape(DatasetShape)} again, and so on.
 * <p>
 * Builds its JavaFX root synchronously at {@code @Activation} (not wrapped
 * in {@code Platform.runLater}, unlike a top-level demo bootstrap): {@code
 * HmiBuilder} reads {@link #getRoot()} right after this component activates
 * and needs the node to already exist.
 */
@Implementation
public class DatasetViewImpl implements DatasetView, DatasetViewServices, DatasetViewParameters, DatasetEvents {

	// Roughly the span of every DatasetShape (see the individual
	// DatasetGenerator implementations, all centered near (0.5, 0.25) - the
	// midpoint of this very range). This is the same coordinate space
	// NetworkViewImpl's "Predict" x/y fields feed straight into the
	// network, unscaled - which is exactly why this View draws that
	// space's reference frame on the canvas, and why the pipette's inverse
	// transform (see #handlePipetteClick) uses these same constants: a
	// point picked here is a point that can be typed there as-is.
	private static final double DATA_MIN_X = -1.5;
	private static final double DATA_MAX_X = 2.5;
	private static final double DATA_MIN_Y = -1.0;
	private static final double DATA_MAX_Y = 1.5;

	// MARGIN_LEFT/MARGIN_BOTTOM add room around the plotted area for the
	// axis tick labels, MARGIN_TOP/MARGIN_RIGHT just keep points at the
	// extreme edge of the data range from being clipped by the canvas
	// border.
	private static final double PLOT_SIZE = 190;
	private static final double MARGIN_LEFT = 34;
	private static final double MARGIN_TOP = 6;
	private static final double MARGIN_RIGHT = 6;
	private static final double MARGIN_BOTTOM = 20;
	private static final double CANVAS_WIDTH = MARGIN_LEFT + PLOT_SIZE + MARGIN_RIGHT;
	private static final double CANVAS_HEIGHT = MARGIN_TOP + PLOT_SIZE + MARGIN_BOTTOM;

	// Spacing between grid lines/tick labels, in data units - not derived
	// from DATA_MIN_*/DATA_MAX_* (those are themselves fixed constants), so
	// this stays a fixed, legible step across the whole plotted range.
	private static final double AXIS_TICK_STEP = 0.5;

	// Not tied to TrainingParameters#getDatasetNoise, which stores the noise
	// level the user sets without this button using it: this button always
	// regenerates at a fixed noise level, with a fresh random seed each time.
	private static final double REGENERATE_NOISE = 0.15;

	// Size, in pixels, of each shape-selector toggle button's icon canvas.
	private static final double SHAPE_ICON_SIZE = 22;

	@UsedServices
	private ActionConsumerServices actionConsumerServices;

	private volatile TrainingSample[] samples = new TrainingSample[0];

	private volatile boolean regenerating;

	private Node root;

	private Canvas canvas;

	private ToggleGroup shapeGroup;

	private final Map<DatasetShape, ToggleButton> shapeButtons = new EnumMap<>(DatasetShape.class);

	// See this class's javadoc: guards displaySelectedShape's programmatic
	// toggle selection against re-firing the toggle group's own selection
	// listener - the same guard as UserConfigurationViewImpl's
	// applyingDisplayedValue, applied here to a single shared toggle group
	// instead of six independent Spinners/Sliders.
	private volatile boolean applyingDisplayedShape;

	// Guards against Platform.runLater callbacks (queued from an event or
	// action still in flight) executing after this component has been
	// passivated, e.g. right when the container is stopped mid-training -
	// at that point any @UsedServices proxy is invalidated, so touching one
	// from a stale callback would throw.
	private volatile boolean active;

	/** Builds the dataset canvas, the shape selector and the regenerate button at activation. */
	@Activation
	public void buildRoot() {
		this.canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
		this.canvas.setOnMouseClicked(event -> this.handlePipetteClick(event.getX(), event.getY()));

		Button regenerateButton = new Button("Regenerate dataset");
		regenerateButton.setOnAction(event -> this.actionConsumerServices
				.executeAction(DatasetActionKey.Regenerate, System.nanoTime(), REGENERATE_NOISE));

		HBox shapeRow = this.buildShapeSelector();

		VBox controls = new VBox(6, shapeRow, regenerateButton);
		controls.setAlignment(Pos.CENTER);

		BorderPane pane = new BorderPane();
		pane.setPadding(new Insets(8));
		pane.setCenter(this.canvas);
		BorderPane.setAlignment(controls, Pos.CENTER);
		BorderPane.setMargin(controls, new Insets(8, 0, 0, 0));
		pane.setBottom(controls);

		pane.getStyleClass().add("card");
		this.root = pane;
		this.active = true;
		this.redraw();
	}

	/**
	 * Builds the row of shape-selector toggle buttons, one per
	 * {@link DatasetShape}, each showing a small icon drawn by
	 * {@link #drawShapeIcon(GraphicsContext, DatasetShape)}. All buttons
	 * share one {@link ToggleGroup} so exactly one shape is ever selected;
	 * its selection listener is what actually fires
	 * {@link DatasetActionKey#SelectShape} - see this class's javadoc for
	 * why it is guarded by {@link #applyingDisplayedShape}.
	 *
	 * @return the assembled row, ready to add to the root layout
	 */
	private HBox buildShapeSelector() {
		this.shapeGroup = new ToggleGroup();
		HBox shapeRow = new HBox(4);
		shapeRow.setAlignment(Pos.CENTER);

		for (DatasetShape shape : DatasetShape.values()) {
			Canvas icon = new Canvas(SHAPE_ICON_SIZE, SHAPE_ICON_SIZE);
			drawShapeIcon(icon.getGraphicsContext2D(), shape);

			ToggleButton button = new ToggleButton();
			button.setGraphic(icon);
			button.setTooltip(new Tooltip(shape.getLabel()));
			button.setToggleGroup(this.shapeGroup);
			button.setUserData(shape);

			this.shapeButtons.put(shape, button);
			shapeRow.getChildren().add(button);
		}

		this.shapeGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
			if (!this.applyingDisplayedShape && newToggle != null) {
				DatasetShape shape = (DatasetShape) newToggle.getUserData();
				this.actionConsumerServices.executeAction(DatasetActionKey.SelectShape, shape);
			}
		});

		return shapeRow;
	}

	/**
	 * Marks the view inactive, so that queued JavaFX callbacks stop using the services of the passivated component.
	 */
	@Passivation
	public void deactivate() {
		this.active = false;
	}

	@Override
	public Node getRoot() {
		return this.root;
	}

	@Override
	public void setRegenerating(final boolean regenerating) {
		this.regenerating = regenerating;
		Platform.runLater(() -> {
			if (this.active && this.canvas != null) {
				this.canvas.setOpacity(regenerating ? 0.4 : 1.0);
			}
		});
	}

	@Override
	public void displaySelectedShape(final DatasetShape shape) {
		Platform.runLater(() -> {
			if (!this.active) {
				return;
			}
			this.applyingDisplayedShape = true;
			try {
				ToggleButton button = this.shapeButtons.get(shape);
				if (button != null) {
					button.setSelected(true);
				}
			} finally {
				this.applyingDisplayedShape = false;
			}
		});
	}

	@Override
	public void datasetChanged(final TrainingSample[] samples) {
		this.samples = samples;
		this.regenerating = false;
		Platform.runLater(this::redraw);
	}

	/**
	 * The pipette: translates a click on the canvas into data-space
	 * coordinates (the exact inverse of the transform {@link #redraw()}
	 * uses to place each sample) and fires
	 * {@link DatasetActionKey#PickPredictionPoint} with them - but only
	 * when the click actually lands inside the plotted square, not in the
	 * margin around it (where the axis tick labels are drawn), so a click
	 * there is silently ignored rather than picking a point far outside
	 * the visible data.
	 *
	 * @param clickX the click's canvas-space x coordinate
	 * @param clickY the click's canvas-space y coordinate
	 */
	private void handlePipetteClick(final double clickX, final double clickY) {
		if (!this.active) {
			return;
		}
		if (clickX < MARGIN_LEFT || clickX > MARGIN_LEFT + PLOT_SIZE
				|| clickY < MARGIN_TOP || clickY > MARGIN_TOP + PLOT_SIZE) {
			return;
		}
		double x = DATA_MIN_X + (clickX - MARGIN_LEFT) / PLOT_SIZE * (DATA_MAX_X - DATA_MIN_X);
		double y = DATA_MIN_Y + (MARGIN_TOP + PLOT_SIZE - clickY) / PLOT_SIZE * (DATA_MAX_Y - DATA_MIN_Y);
		this.actionConsumerServices.executeAction(DatasetActionKey.PickPredictionPoint, new double[] { x, y });
	}

	private void redraw() {
		if (!this.active || this.canvas == null) {
			return;
		}
		GraphicsContext gc = this.canvas.getGraphicsContext2D();
		gc.setFill(Color.web("#080c13"));
		gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
		this.canvas.setOpacity(1.0);
		this.drawAxes(gc);
		for (TrainingSample sample : this.samples) {
			double[] input = sample.input();
			double px = MARGIN_LEFT + (input[0] - DATA_MIN_X) / (DATA_MAX_X - DATA_MIN_X) * PLOT_SIZE;
			double py = MARGIN_TOP + PLOT_SIZE - (input[1] - DATA_MIN_Y) / (DATA_MAX_Y - DATA_MIN_Y) * PLOT_SIZE;
			gc.setFill(sample.label()[0] >= 0.5 ? Color.web("#f472b6") : Color.web("#818cf8"));
			gc.fillOval(px - 3, py - 3, 6, 6);
		}
	}

	/**
	 * Draws the point cloud's coordinate reference frame: a light grid at
	 * {@value #AXIS_TICK_STEP}-unit intervals with tick labels along the
	 * bottom and left edges, a bolder line through the origin, and a frame
	 * around the plotted area. Drawn before the samples themselves so the
	 * points sit on top of it.
	 * <p>
	 * This is a plain, hand-drawn overlay on a raw {@link Canvas}, not a
	 * JavaFX {@code Chart} (unlike {@code TrainingViewImpl}'s loss chart,
	 * which needs axes that rescale to whatever loss values training
	 * produces): the dataset's coordinate range is fixed
	 * (from {@code DATA_MIN_X} to {@code DATA_MAX_Y}), so a {@code Chart}'s dynamic
	 * axis machinery would be more than this needs.
	 *
	 * @param gc the canvas's graphics context
	 */
	private void drawAxes(final GraphicsContext gc) {
		gc.setFont(Font.font(9));

		gc.setLineWidth(1);
		gc.setTextBaseline(VPos.TOP);
		gc.setTextAlign(TextAlignment.CENTER);
		for (double x = firstTick(DATA_MIN_X); x <= DATA_MAX_X + 1e-9; x += AXIS_TICK_STEP) {
			double px = MARGIN_LEFT + (x - DATA_MIN_X) / (DATA_MAX_X - DATA_MIN_X) * PLOT_SIZE;
			gc.setStroke(Color.web("#1f2a3b"));
			gc.strokeLine(px, MARGIN_TOP, px, MARGIN_TOP + PLOT_SIZE);
			gc.setFill(Color.web("#8591a6"));
			gc.fillText(formatTick(x), px, MARGIN_TOP + PLOT_SIZE + 4);
		}

		gc.setTextBaseline(VPos.CENTER);
		gc.setTextAlign(TextAlignment.RIGHT);
		for (double y = firstTick(DATA_MIN_Y); y <= DATA_MAX_Y + 1e-9; y += AXIS_TICK_STEP) {
			double py = MARGIN_TOP + PLOT_SIZE - (y - DATA_MIN_Y) / (DATA_MAX_Y - DATA_MIN_Y) * PLOT_SIZE;
			gc.setStroke(Color.web("#1f2a3b"));
			gc.strokeLine(MARGIN_LEFT, py, MARGIN_LEFT + PLOT_SIZE, py);
			gc.setFill(Color.web("#8591a6"));
			gc.fillText(formatTick(y), MARGIN_LEFT - 4, py);
		}

		// Bolder line through the origin, when it falls inside the plotted
		// range (it always does for this fixed range, but this stays
		// correct if DATA_MIN_*/DATA_MAX_* ever change).
		gc.setStroke(Color.web("#2a384e"));
		gc.setLineWidth(1.2);
		if (DATA_MIN_X <= 0 && 0 <= DATA_MAX_X) {
			double px = MARGIN_LEFT + (0 - DATA_MIN_X) / (DATA_MAX_X - DATA_MIN_X) * PLOT_SIZE;
			gc.strokeLine(px, MARGIN_TOP, px, MARGIN_TOP + PLOT_SIZE);
		}
		if (DATA_MIN_Y <= 0 && 0 <= DATA_MAX_Y) {
			double py = MARGIN_TOP + PLOT_SIZE - (0 - DATA_MIN_Y) / (DATA_MAX_Y - DATA_MIN_Y) * PLOT_SIZE;
			gc.strokeLine(MARGIN_LEFT, py, MARGIN_LEFT + PLOT_SIZE, py);
		}

		gc.setStroke(Color.web("#2a384e"));
		gc.setLineWidth(1);
		gc.strokeRect(MARGIN_LEFT, MARGIN_TOP, PLOT_SIZE, PLOT_SIZE);
	}

	/**
	 * Draws a small, simplified glyph representing {@code shape} into an
	 * icon-sized canvas, purely for the shape-selector toggle buttons -
	 * these are stylized cues (e.g. the star icon is a plain five-pointed
	 * star polygon), not to scale with or derived from the corresponding
	 * {@link DatasetGenerator}'s actual parameters.
	 *
	 * @param gc    the icon canvas's graphics context
	 * @param shape the shape to draw a glyph for
	 */
	private static void drawShapeIcon(final GraphicsContext gc, final DatasetShape shape) {
		double size = SHAPE_ICON_SIZE;
		double cx = size / 2.0;
		double cy = size / 2.0;
		gc.setStroke(Color.web("#b3bdcd"));
		gc.setFill(Color.web("#b3bdcd"));
		gc.setLineWidth(1.4);

		switch (shape) {
			case TWO_MOONS -> {
				gc.strokeArc(2, 3, size - 4, size - 6, 20, 200, javafx.scene.shape.ArcType.OPEN);
				gc.strokeArc(4, 6, size - 4, size - 6, 200, 200, javafx.scene.shape.ArcType.OPEN);
			}
			case RING -> {
				gc.strokeOval(2, 2, size - 4, size - 4);
				gc.fillOval(cx - 2.5, cy - 2.5, 5, 5);
			}
			case CROSS -> {
				double armHalf = size * 0.16;
				gc.fillRect(2, cy - armHalf, size - 4, armHalf * 2);
				gc.fillRect(cx - armHalf, 2, armHalf * 2, size - 4);
			}
			case STAR -> {
				double[][] xy = starPolygon(cx, cy, size * 0.46, size * 0.19, 5);
				double[] xs = xy[0];
				double[] ys = xy[1];
				gc.fillPolygon(xs, ys, xs.length);
			}
			case SPIRAL -> {
				int steps = 60;
				double maxRadius = size * 0.46;
				double turns = 2.2;
				double prevX = cx;
				double prevY = cy;
				for (int i = 1; i <= steps; i++) {
					double t = (double) i / steps;
					double radius = t * maxRadius;
					double angle = t * turns * 2 * Math.PI;
					double x = cx + radius * Math.cos(angle);
					double y = cy + radius * Math.sin(angle);
					gc.strokeLine(prevX, prevY, x, y);
					prevX = x;
					prevY = y;
				}
			}
			case CHECKERBOARD -> {
				int cells = 4;
				double cellSize = (size - 4) / cells;
				for (int row = 0; row < cells; row++) {
					for (int col = 0; col < cells; col++) {
						if ((row + col) % 2 == 0) {
							gc.fillRect(2 + col * cellSize, 2 + row * cellSize, cellSize, cellSize);
						}
					}
				}
			}
		}
	}

	/**
	 * @param cx           the star's center x
	 * @param cy           the star's center y
	 * @param outerRadius  the distance from center to each point's tip
	 * @param innerRadius  the distance from center to each valley between points
	 * @param starPoints   how many points the star has
	 * @return {@code {xs, ys}}: the polygon's vertex coordinates, alternating
	 *         outer/inner, ready for {@code GraphicsContext#fillPolygon}
	 */
	private static double[][] starPolygon(final double cx, final double cy, final double outerRadius,
			final double innerRadius, final int starPoints) {
		int vertexCount = starPoints * 2;
		double[] xs = new double[vertexCount];
		double[] ys = new double[vertexCount];
		for (int i = 0; i < vertexCount; i++) {
			double radius = (i % 2 == 0) ? outerRadius : innerRadius;
			double angle = -Math.PI / 2 + i * Math.PI / starPoints;
			xs[i] = cx + radius * Math.cos(angle);
			ys[i] = cy + radius * Math.sin(angle);
		}
		return new double[][] { xs, ys };
	}

	/**
	 * @param dataMin the plotted range's lower bound, in data units
	 * @return the lowest {@link #AXIS_TICK_STEP} multiple at or above
	 *         {@code dataMin}
	 */
	private static double firstTick(final double dataMin) {
		return Math.ceil(dataMin / AXIS_TICK_STEP) * AXIS_TICK_STEP;
	}

	/**
	 * @param value a tick's data-space coordinate
	 * @return {@code value} formatted to {@link #AXIS_TICK_STEP}'s one
	 *         decimal of precision, without a stray "-0.0" at the origin
	 */
	private static String formatTick(final double value) {
		double rounded = Math.round(value * 10) / 10.0;
		return String.format("%.1f", rounded == 0.0 ? 0.0 : rounded);
	}

	/**
	 * Returns the samples most recently received from the dataset model event.
	 *
	 * @return the samples last received via {@link DatasetEvents#datasetChanged(TrainingSample[])}
	 */
	public TrainingSample[] getSamples() {
		return this.samples;
	}
}
