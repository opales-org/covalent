/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.hmibuilder.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.view.CubeViewParameters;
import org.opales.covalent.examples.rubikscubesolver.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.rubikscubesolver.solver.view.SolverViewParameters;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.view.UserConfigurationViewParameters;

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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Assembles the JavaFX scene graph for the whole demo out of each function's already-built view root,
 * read through their {@code *ViewParameters} interfaces, plus a static "Help" menu of its own. Pure
 * assembly: no business logic, no domain event subscriptions - see {@code HmiBuilderImpl} in the
 * Neural Network Trainer demo for the fuller rationale, including why every {@code *ViewParameters}
 * accessor is read synchronously here (it only works because every View builds its root node
 * synchronously in its own {@code @Activation}) while this component's own top-level window is wrapped
 * in {@code Platform.runLater}.
 * <p>
 * Layout: a top-level {@link VBox} holding the "Help" {@link MenuBar} above a {@link BorderPane} - the
 * user configuration panel on the left, the cube view in the center, the solver view on the right.
 */
@Implementation
public class HmiBuilderImpl implements HmiBuilder {

	private static final String APP_NAME = "Rubik's Cube Solver";

	private static final String ABOUT_TEXT = """
			Rubik's Cube Solver is a cube model that two independent solving \
			algorithms, Kociemba's two-phase strategy and the layer-by-layer \
			method a person follows, can each be set loose on: pick an algorithm, \
			start a solve, and watch it run off the UI thread while its progress is reported live.

			It illustrates several Covalent aspects side by side:

			- No single root composite: unlike the other demos, Cube, \
			Solver, UserConfiguration and HmiBuilder are passed directly to \
			deployComponents(...) as several independent top-level composites, \
			rather than one.

			- Extending services: CubeServices extends CubeReadServices - the \
			cube's mutating operations (applyMove, scramble and resetToSolved) \
			are reachable only through the \
			wider interface, while UserConfiguration is written against the \
			read-only one alone.

			- Asynchronous services: SolverController drives each solve \
			through AsyncServices, the container's own Application Utility \
			Service, off the UI thread, on a dedicated queue key that also \
			makes cancellation meaningful.

			- Event data cloning: CubeEvents.stateChanged carries its \
			CubeState argument @Cloned, since CubeState is mutable and the \
			instance delivered is the one the Model holds: each subscriber \
			receives its own copy.

			- Event subscription delegation: CubeModelImpl's own delegation \
			pushes a real catch-up snapshot to a late subscriber, rather than \
			leaving it to miss whatever happened before it subscribed.

			- Actions routed exclusively through the HMI's ActionManager, \
			exactly as in the other demos.
			""";

	private static final String USAGE_TEXT = """
			Center (the cube view): a classic unfolded cube net. Click any \
			facelet to rotate that face clockwise; "Scramble" randomizes the \
			cube.

			Right side (the solver panel): pick Kociemba or Human, "Solve" starts \
			a solve, "Cancel" stops it, and "Reset" abandons the current \
			attempt and returns the cube to solved - it lives here, not next \
			to the cube, since starting over is the solver's call. A progress \
			label reports the search's current state, and a move history \
			panel can be toggled on to trace every cube state change while it \
			is open.

			Left panel (User configuration): the Kociemba phase-2 maximum \
			depth, a hard cap on how long a phase-2 search is allowed to run \
			before giving up, and the playback speed, the number of moves per \
			second at which a found solution is played onto the cube. A \
			read-only "solved" indicator shows whether the cube was solved \
			when the panel activated; it does not follow later moves.
			""";

	private static final String STYLESHEET =
			HmiBuilderImpl.class.getResource("/org/opales/covalent/examples/theme/dark-theme.css").toExternalForm();

	@AccessedParameters
	private CubeViewParameters accessCubeViewParameters;

	@AccessedParameters
	private SolverViewParameters accessSolverViewParameters;

	@AccessedParameters
	private UserConfigurationViewParameters accessUserConfigurationViewParameters;

	private Stage stage;

	/**
	 * Reads the three view roots, lays them out under a Help menu bar (user configuration on the left, cube in
	 * the center, solver on the right) and shows the window on the JavaFX thread.
	 */
	@Activation
	public void assembleAndShow() {
		Node cubeRoot = this.accessCubeViewParameters.getRoot();
		Node solverRoot = this.accessSolverViewParameters.getRoot();
		Node userConfigurationRoot = this.accessUserConfigurationViewParameters.getRoot();

		BorderPane root = new BorderPane();
		root.setPadding(new Insets(12));
		root.setLeft(userConfigurationRoot);
		root.setCenter(cubeRoot);
		root.setRight(solverRoot);
		BorderPane.setMargin(userConfigurationRoot, new Insets(0, 12, 0, 0));
		BorderPane.setMargin(solverRoot, new Insets(0, 0, 0, 12));

		VBox windowRoot = new VBox(this.buildMenuBar(), root);
		windowRoot.getStyleClass().add("app-rubik");
		VBox.setVgrow(root, Priority.ALWAYS);

		Platform.runLater(() -> {
			this.stage = new Stage();
			this.stage.setTitle(APP_NAME);
			Scene scene = new Scene(windowRoot);
			scene.getStylesheets().add(STYLESHEET);
			this.stage.setScene(scene);
			this.stage.sizeToScene();
			this.stage.show();
		});
	}

	/** Closes the application window, if it is open, on the JavaFX thread. */
	@Passivation
	public void closeWindow() {
		Platform.runLater(() -> {
			if (this.stage != null) {
				this.stage.close();
				this.stage = null;
			}
		});
	}

	/**
	 * Builds the top-level "Help" menu: "About {@value #APP_NAME}" (what
	 * this application does, and which Covalent mechanisms it illustrates)
	 * and "Using {@value #APP_NAME}" (how to operate its controls). Both
	 * entries just show a static, read-only help window.
	 *
	 * @return the assembled menu bar, left-aligned by the default
	 *         {@link MenuBar} behavior
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
	 * left open for reference while a solve keeps running.
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
}
