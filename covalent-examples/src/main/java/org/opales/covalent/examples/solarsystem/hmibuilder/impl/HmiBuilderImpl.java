/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hmibuilder.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardParameters;
import org.opales.covalent.examples.solarsystem.hazardalert.model.Alert;
import org.opales.covalent.examples.solarsystem.hazardalert.view.HazardAlertViewParameters;
import org.opales.covalent.examples.solarsystem.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.solarsystem.orbitalsystem.OrbitalSystem;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalParameters;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;
import org.opales.covalent.examples.solarsystem.orbitalsystem.view.OrbitalViewParameters;
import org.opales.covalent.examples.solarsystem.orbitalwatch.OrbitalWatch;
import org.opales.covalent.examples.solarsystem.orbitalwatch.view.OrbitalWatchViewParameters;
import org.opales.covalent.examples.solarsystem.simulationrunner.SimulationRunner;
import org.opales.covalent.examples.solarsystem.simulationrunner.controller.RunnerParameters;
import org.opales.covalent.examples.solarsystem.simulationrunner.view.RunnerViewParameters;
import org.opales.covalent.examples.solarsystem.userconfiguration.UserConfiguration;
import org.opales.covalent.examples.solarsystem.userconfiguration.view.UserConfigurationViewParameters;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Assembles the JavaFX scene graph for the whole demo out of each function's
 * already-built view root(s), read through their {@code *ViewParameters}
 * interfaces, plus a static "Help" menu. Pure assembly: no business logic,
 * no domain event subscriptions - the help text is fixed documentation, not
 * state read from any function.
 * <p>
 * Every {@code *ViewParameters} accessor is read synchronously here, which
 * only works because every View builds its root node(s) synchronously in
 * its own {@code @Activation}, before HmiBuilder reads them. This
 * component's own top-level window, by contrast, is wrapped in
 * {@code Platform.runLater}, matching {@code HelloWorldDemoImpl}'s
 * bootstrap convention.
 */
@Implementation
public class HmiBuilderImpl implements HmiBuilder {

	private static final String APP_NAME = "Solar System Simulation";

	private static final String ABOUT_TEXT = """
			Solar System Simulation is a small 2D n-body gravitational demo: a \
			Sun and a few planets attract each other and are integrated forward \
			in time (semi-implicit Euler), animated live on an orrery view, with \
			a hazard layer watching for close approaches between bodies.

			It illustrates several Covalent aspects side by side:

			- One MVIC (Model / Controller / Interaction Controller / View) per \
			leaf function: OrbitalSystem (physics + rendering), \
			SimulationRunner (pacing), OrbitalWatch (an unfiltered event log) \
			and HazardAlert (a filtered event log).

			- Composite components: each function is its own @Implementation \
			wiring its parts together, and this very component (HmiBuilder) is \
			a pure assembly composite with no business logic of its own.

			- Cross-function Parameters ports: the configuration panel \
			(UserConfiguration, in the left column) fronts three different functions' Parameters - \
			OrbitalParameters, RunnerParameters and HazardParameters - behind \
			one set of sliders.

			- Event filters: OrbitalWatch and HazardAlert both subscribe to the \
			very same CloseApproachEvents interface, but in two contrasting \
			ways - OrbitalWatch subscribes to every notification through \
			ordinary, automatic autowiring, while HazardAlert subscribes \
			manually with a filter that re-reads its own live thresholds on \
			every notification, no re-subscription needed when you move a \
			slider. The orrery's own "Add Comet" button lets you manufacture a \
			close approach on demand - a comet on a randomized, strongly \
			eccentric orbit crossing every planet's path - rather than waiting \
			for one to arise from numerical drift.

			- Actions routed exclusively through the HMI's ActionManager: no \
			View ever calls an Interaction Controller directly, and every action is \
			registered at @Activation and safely unregistered at @Passivation.

			- Independent lifecycle: this demo runs in its own \
			CovalentContainer, started and stopped from the Launcher window \
			without touching any other demo.
			""";

	private static final String USAGE_TEXT = """
			The window has a left column (configuration, then run controls), the \
			orrery in the center, the two event logs on the right, and the \
			readout at the bottom.

			Left column, top (User configuration) - four live sliders shared \
			with the functions that own them:

			- Gravitational constant: scales the attraction between every pair \
			of bodies (this demo's own toy unit system, not SI) - raise it and \
			orbits tighten and speed up, lower it and they loosen and slow down.
			- Time scale: how much simulation time each tick advances, so the \
			orbits stay watchable instead of jumping ahead; it applies from the \
			next tick on.
			- Hazard distance threshold: an approach closer than this may qualify \
			as hazardous. Close approaches are only detected when two bodies come \
			within 1.5 units of each other, so a threshold above 1.5 adds nothing.
			- Hazard size threshold: at least one of the two bodies' radii must \
			reach this for the approach to qualify.

			Both hazard thresholds are re-read by HazardAlert's own filter on \
			every single close-approach notification, so moving either slider \
			changes what counts as hazardous starting from the very next \
			approach, with no need to stop the simulation or re-subscribe to \
			anything. Two bodies that touch or visibly overlap (their distance \
			is at most the larger of 0.4 and the sum of their radii) are always \
			hazardous, whatever the size threshold.

			Left column, bottom (Run controls) - Start/Pause toggles continuous \
			stepping, Step advances exactly one tick, Reset restarts the system \
			from its initial layout. The slider next to them also sets the time \
			scale, but the two time scale sliders are not kept in step with each \
			other: the last one you move sets the value, and the other one does \
			not follow.

			Center (the orrery) - the live 2D view of the system, with an "Add \
			Comet" button above it: each click drops in one more comet on a \
			randomized, strongly eccentric orbit crossing every planet's path, a \
			quick way to manufacture close approaches for Orbital Watch and \
			Hazard Alert to react to instead of waiting for one to arise on its \
			own. Click a body to select it (a white ring appears around it) and \
			see its details - mass, radius, distance from the Sun and speed, as \
			they were when you clicked - in the label underneath.

			Bottom (Readout) - elapsed simulation time. It is shown briefly in \
			bold when you press Step.

			Right side, top (Orbital Watch) - every close approach ever \
			detected between two bodies, regardless of the hazard thresholds. \
			"Clear" empties the log.

			Right side, bottom (Hazard Alert) - only the approaches that \
			currently cross both the distance and size thresholds set above. \
			"Acknowledge all" clears the list; tightening or loosening the \
			thresholds changes what qualifies from the next approach on.

			View menu - switches the whole window between a dark theme \
			(the default) and a light theme; purely cosmetic, with no effect \
			on the simulation itself.

			Closing this window does not stop the demo: its CovalentContainer \
			keeps running until you stop it with its button in the Launcher \
			window.
			""";

	private static final String DARK_THEME_STYLESHEET =
			HmiBuilderImpl.class.getResource("/org/opales/covalent/examples/theme/dark-theme.css").toExternalForm();

	private static final String LIGHT_THEME_STYLESHEET =
			HmiBuilderImpl.class.getResource("/org/opales/covalent/examples/theme/light-theme.css").toExternalForm();

	@AccessedParameters
	private OrbitalViewParameters accessOrbitalViewParameters;

	@AccessedParameters
	private RunnerViewParameters accessRunnerViewParameters;

	@AccessedParameters
	private OrbitalWatchViewParameters accessOrbitalWatchViewParameters;

	@AccessedParameters
	private HazardAlertViewParameters accessHazardAlertViewParameters;

	@AccessedParameters
	private UserConfigurationViewParameters accessUserConfigurationViewParameters;

	private Stage stage;

	/**
	 * Assembles every function's view root into one scene and shows it in an application window, on the JavaFX thread.
	 */
	@Activation
	public void assembleAndShow() {
		VBox controls = new VBox(12, this.accessUserConfigurationViewParameters.getRoot(),
				this.accessRunnerViewParameters.getControlsRoot());
		controls.setPadding(new Insets(8, 0, 8, 8));

		VBox watchers = new VBox(12, this.accessOrbitalWatchViewParameters.getRoot(),
				this.accessHazardAlertViewParameters.getRoot());
		watchers.setPadding(new Insets(8, 8, 8, 0));

		BorderPane root = new BorderPane();
		root.setLeft(controls);
		root.setCenter(this.accessOrbitalViewParameters.getRoot());
		root.setRight(watchers);
		root.setBottom(this.accessRunnerViewParameters.getReadoutRoot());
		root.setPadding(new Insets(4));

		// Wrapped in a ScrollPane so that on a smaller screen or a higher
		// DPI scale - where the panels' natural size no longer fits the
		// available screen height - the controls stay reachable by
		// scrolling instead of being pushed off-window and lost.
		ScrollPane scrollPane = new ScrollPane(root);
		scrollPane.setFitToWidth(true);

		VBox windowRoot = new VBox(this.buildMenuBar(), scrollPane);
		windowRoot.getStyleClass().add("app-solar");
		VBox.setVgrow(scrollPane, Priority.ALWAYS);

		Platform.runLater(() -> {
			this.stage = new Stage();
			this.stage.setTitle(APP_NAME);
			// No fixed pixel size: sizeToScene() below measures the actual
			// preferred size once the stylesheet is applied, so the window
			// starts at its natural size, capped to the screen's work area -
			// the ScrollPane above takes over from there if it still overflows.
			Scene scene = new Scene(windowRoot);
			scene.getStylesheets().add(DARK_THEME_STYLESHEET);
			this.stage.setScene(scene);
			this.stage.sizeToScene();
			this.stage.centerOnScreen();
			this.stage.show();
		});
	}

	/** Closes the application window on the JavaFX thread, if it is open. */
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
	 * Builds the top-level "View" and "Help" menus: "View" is this demo's
	 * own dark/light theme switch (see {@link #buildViewMenu()}); "Help"
	 * carries "About {@value #APP_NAME}" and "Using {@value #APP_NAME}",
	 * each showing a static, read-only help window.
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

		return new MenuBar(this.buildViewMenu(), helpMenu);
	}

	/**
	 * Builds the "View" menu's dark/light theme switch: two mutually
	 * exclusive {@link RadioMenuItem}s swapping the whole scene's
	 * stylesheet - dark is the default, matching the initial stylesheet set
	 * in {@link #assembleAndShow()}.
	 */
	private Menu buildViewMenu() {
		ToggleGroup themeGroup = new ToggleGroup();

		RadioMenuItem darkTheme = new RadioMenuItem("Dark Theme");
		darkTheme.setToggleGroup(themeGroup);
		darkTheme.setSelected(true);
		darkTheme.setOnAction(event -> this.applyTheme(DARK_THEME_STYLESHEET));

		RadioMenuItem lightTheme = new RadioMenuItem("Light Theme");
		lightTheme.setToggleGroup(themeGroup);
		lightTheme.setOnAction(event -> this.applyTheme(LIGHT_THEME_STYLESHEET));

		Menu viewMenu = new Menu("View");
		viewMenu.getItems().addAll(darkTheme, lightTheme);
		return viewMenu;
	}

	/**
	 * Replaces the window's whole stylesheet with the requested theme.
	 *
	 * @param stylesheetUrl the theme stylesheet's external form URL
	 */
	private void applyTheme(final String stylesheetUrl) {
		if (this.stage != null && this.stage.getScene() != null) {
			this.stage.getScene().getStylesheets().setAll(stylesheetUrl);
		}
	}

	/**
	 * Shows one help topic in its own small, non-modal window, so it can be
	 * left open for reference while the simulation keeps running.
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
		dialog.setScene(new Scene(textArea, 560, 440));
		dialog.show();
	}
}
