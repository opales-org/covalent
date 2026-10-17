/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.UserConfigurationViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.impl.UserConfigurationViewImpl;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Assembles the JavaFX scene graph for the whole demo out of each function's
 * already-built view root(s), read through their {@code *ViewParameters}
 * interfaces, plus a static "Help" menu of its own. Pure assembly: no
 * business logic, no domain event subscriptions - the two Help entries are
 * purely informational (they read no component state and trigger no
 * business action), so they are wired directly here as plain JavaFX
 * {@code MenuItem} handlers rather than through {@code ActionConsumerServices}:
 * the ActionManager pattern used by every other button/slider/spinner in
 * this demo (see e.g. {@code DatasetViewImpl}, {@code UserConfigurationViewImpl})
 * exists to route a View's user-triggered action to the Interaction
 * that actually implements it, decoupling "what a control does" from "how";
 * a static "About" dialog has nothing for an Interaction to implement,
 * so going through that indirection here would add a layer for no reason.
 * <p>
 * The four {@code @AccessedParameters} fields below are this demo's clearest
 * example of a top-level component consuming several functions'
 * {@code *ViewParameters} contracts purely to lay out a UI shell: each one
 * is a settled JavaFX node (see {@link UserConfigurationViewParameters} and
 * {@link DatasetViewParameters} for why {@code getRoot()} is modeled as
 * {@code @Parameters} rather than {@code @Services}), read once and placed
 * into one of the three columns of the window - {@code Training}'s split
 * {@code getChartRoot()}/{@code getControlsRoot()} simply supplies two
 * nodes instead of one for the same treatment.
 * <p>
 * Every {@code *ViewParameters} accessor is read synchronously here, which
 * only works because every View builds its root node(s) synchronously in
 * its own {@code @Activation} rather than deferring to
 * {@code Platform.runLater} - see e.g. {@code DatasetViewImpl}. This
 * component's own top-level window, by contrast, is wrapped in
 * {@code Platform.runLater} (nothing else depends on it synchronously),
 * matching {@code HelloWorldDemoImpl}'s bootstrap convention.
 * <p>
 * Opening the window at {@code @Activation} - rather than at
 * {@code @Initialization}, or eagerly in a constructor - is itself a
 * deliberate lifecycle choice: {@code @Activation} is the point at which a
 * component's dependencies are guaranteed already wired and, for the other
 * functions' Views in this demo's deployment order, already built, so it is
 * the first point at which it is safe to assemble and show a {@code Stage}
 * that embeds their root nodes.
 */
@Implementation
public class HmiBuilderImpl implements HmiBuilder {

	private static final String APP_NAME = "Neural Network Trainer";

	private static final String ABOUT_TEXT = """
			Neural Network Trainer trains a small feed-forward neural network, in \
			real time, on a toy two-dimensional classification problem whose \
			shape you choose (two moons, ring, cross, star, spiral or \
			checkerboard), so you can watch training happen rather than read \
			about it: the dataset, the network's layer/weight structure, and the \
			training loss all update live as you interact with the controls.

			It is built entirely on the Covalent component framework, following \
			this project's own Model / Controller / Interaction / View \
			(MVIC) pattern - one MVIC instance per function (Dataset, Network, \
			Training), plus a thin User Configuration front-end and this top-level \
			assembly component. Studying its source is a self-contained tour of \
			several Covalent mechanisms:

			- @Component / @Implementation, and composite components (a parent \
			@Implementation grouping child @Implementation fields, e.g. one \
			function's Model/Controller/Interaction/View)
			- @Item: an internal sub-part with no component identity of its own \
			(the gradient-descent optimizer inside the network's Controller)
			- @Services / @UsedServices (synchronous operations) versus \
			@Parameters / @AccessedParameters (configuration-like state - \
			including the JavaFX view roots this very window is built from)
			- @Events / @ProducedEvents: fire-and-forget notifications, \
			auto-subscribed to simply by implementing the events interface
			- event subscription delegation (a nested @EventsSubscribe \
			interface): how a late subscriber catches up on a producer's existing \
			state
			- manual subscription without a specific subscribe interface \
			(GenericSubscribe): "Freeze charts" lets the Training view \
			subscribe to and unsubscribe from its own events by hand, while the \
			Training interaction controller keeps consuming the very same events \
			automatically
			- the ActionManager pattern: every user-triggered action in a View \
			is routed to its Interaction through an ActionKey, rather than \
			calling the Interaction directly
			- the component lifecycle: @Initialization, @Activation, \
			@Passivation, @Removal
			""";

	private static final String USAGE_TEXT = """
			The window has three columns.

			User configuration (left column): Learning rate controls how large a \
			step each epoch's gradient descent takes - too high and the loss can \
			oscillate or diverge, too low and training crawls. Hidden layer count \
			and Hidden layer size shape the network's capacity: more/larger \
			layers can fit more complex boundaries, at the cost of slower \
			training and a higher risk of overfitting the small dataset. Batch \
			size sets how many points are averaged into each gradient step. \
			Training pace (epochs/s) only paces the animation, not the mathematics \
			of training. Dataset noise is kept with the training settings but is \
			not applied: "Regenerate dataset" always draws the dataset with a \
			fixed noise of 0.15. The other changes take effect immediately, live, \
			without needing to stop and restart training.

			Network (center column, top): click on the network diagram to \
			highlight a layer; its neurons are drawn in gold. Enter x/y values in \
			the "try it yourself" row and click "Predict" to see the network's \
			current output for that point.

			Training (run controls in the center column, charts in the right \
			column): "Start" begins training, "Pause" suspends it, "Step" runs \
			exactly one epoch, "Reset" reinitializes the network's weights from \
			scratch. The status line reports the current epoch, loss and \
			accuracy, and the two stacked charts plot loss and accuracy over \
			time, each showing both the training and the held-out test subset. \
			"Freeze charts" stops the charts from updating without pausing \
			training itself: training keeps running underneath, and the charts \
			resume - with a visible jump across whatever was missed - once \
			unfrozen.

			Dataset (right column, top): the shape buttons select two moons, \
			ring, cross, star, spiral or checkerboard; "Regenerate dataset" draws \
			a fresh point cloud of the selected shape with a new random seed. A \
			click on the plot copies the x/y coordinates of the clicked point into \
			the "try it yourself" row of the network panel.
			""";

	@AccessedParameters
	private DatasetViewParameters accessDatasetViewParameters;

	@AccessedParameters
	private NetworkViewParameters accessNetworkViewParameters;

	@AccessedParameters
	private TrainingViewParameters accessTrainingViewParameters;

	@AccessedParameters
	private UserConfigurationViewParameters accessUserConfigurationViewParameters;

	private Stage stage;

	private static final String STYLESHEET =
			HmiBuilderImpl.class.getResource("/org/opales/covalent/examples/theme/dark-theme.css").toExternalForm();

	/** Gap between the three columns, and between the views stacked in a column. */
	private static final double SPACING = 8;

	/**
	 * Reads every function's already-built view root(s) and lays them out in
	 * three columns under a "Help" {@link MenuBar} of this component's own,
	 * then shows the application window asynchronously (see the class javadoc
	 * for why the window itself is deferred to {@code Platform.runLater} while
	 * the layout reads above it are not).
	 * <p>
	 * Left column: User Configuration. Center column: Network above the
	 * training run controls. Right column: Dataset above the training charts.
	 * Each column takes the width of its widest view, and the center column
	 * absorbs any extra window width.
	 * <p>
	 * The scene is sized to its content ({@link Stage#sizeToScene()}) rather
	 * than to a hardcoded width/height: this root's actual preferred size is
	 * the sum of independently-sized child views (Dataset, Network,
	 * Training, User Configuration) that this component has no reason to
	 * know the exact pixel dimensions of.
	 */
	@Activation
	public void assembleAndShow() {
		Node networkRoot = this.accessNetworkViewParameters.getRoot();
		VBox left = new VBox(SPACING, this.accessUserConfigurationViewParameters.getRoot());
		VBox center = new VBox(SPACING, networkRoot, this.accessTrainingViewParameters.getControlsRoot());
		VBox.setVgrow(networkRoot, Priority.ALWAYS);
		VBox right = new VBox(SPACING, this.accessDatasetViewParameters.getRoot(),
				this.accessTrainingViewParameters.getChartRoot());

		HBox columns = new HBox(SPACING, left, center, right);
		HBox.setHgrow(center, Priority.ALWAYS);
		columns.setPadding(new Insets(8));

		VBox root = new VBox(this.buildMenuBar(), columns);
		root.getStyleClass().add("app-neural");

		Platform.runLater(() -> {
			this.stage = new Stage();
			this.stage.setTitle(APP_NAME);
			Scene scene = new Scene(root);
			scene.getStylesheets().add(STYLESHEET);
			this.stage.setScene(scene);
			this.stage.sizeToScene();
			this.stage.show();
		});
	}

	/**
	 * Builds the top-level "Help" menu: "About {@value #APP_NAME}" (what
	 * this application does, and which Covalent mechanisms it illustrates)
	 * and "Using {@value #APP_NAME}" (how to operate its controls). Both
	 * entries just show a static, read-only help window - see this class's
	 * javadoc for why that bypasses {@code ActionConsumerServices}.
	 * <p>
	 * A bare {@link MenuBar} already left-aligns its menus by default, so
	 * no extra wrapping or spacer is needed to position it.
	 *
	 * @return the assembled menu bar
	 */
	private MenuBar buildMenuBar() {
		MenuItem about = new MenuItem("About " + APP_NAME);
		about.setOnAction(event -> this.showHelpDialog("About " + APP_NAME, ABOUT_TEXT));

		MenuItem usage = new MenuItem("Using " + APP_NAME);
		usage.setOnAction(event -> this.showHelpDialog("Using " + APP_NAME, USAGE_TEXT));

		Menu helpMenu = new Menu("Help");
		helpMenu.getItems().addAll(about, usage);

		return new MenuBar(helpMenu);
	}

	/**
	 * Shows one help topic in its own small, non-modal window, so it can be
	 * left open for reference while training keeps running.
	 *
	 * @param title the help window's title
	 * @param text  the help topic's fixed text
	 */
	private void showHelpDialog(final String title, final String text) {
		TextArea textArea = new TextArea(text);
		textArea.setEditable(false);
		textArea.setWrapText(true);

		Stage dialog = new Stage();
		dialog.initOwner(this.stage);
		dialog.initModality(Modality.NONE);
		dialog.setTitle(title);
		Scene dialogScene = new Scene(textArea, 560, 440);
		dialogScene.getStylesheets().add(STYLESHEET);
		dialog.setScene(dialogScene);
		dialog.show();
	}

	/**
	 * Closes the application window - the mirror of {@link #assembleAndShow()},
	 * run when this demo's container is stopped.
	 */
	@Passivation
	public void closeWindow() {
		Platform.runLater(() -> {
			if (this.stage != null) {
				this.stage.close();
				this.stage = null;
			}
		});
	}
}
