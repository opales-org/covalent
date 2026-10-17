/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.launcher;

import java.util.List;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.examples.ecosystem.statsexport.EcosystemStatsExportDemoLauncher;
import org.opales.covalent.examples.helloworld.HelloWorldDemoImpl;
import org.opales.covalent.examples.neuralnetworkdemo.impl.NeuralNetworkDemoImpl;
import org.opales.covalent.examples.rubikscubesolver.cube.impl.CubeImpl;
import org.opales.covalent.examples.rubikscubesolver.hmibuilder.impl.HmiBuilderImpl;
import org.opales.covalent.examples.rubikscubesolver.solver.impl.SolverImpl;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.impl.UserConfigurationImpl;
import org.opales.covalent.examples.solarsystem.impl.SolarSystemDemoImpl;

import org.opales.covalent.hmi.impl.HmiCoreImpl;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Launcher window for every Covalent demo in this module: one button per
 * demo, each demo running in its own, dedicated {@link CovalentContainer} so
 * several demos can run at the same time, independently of one another and
 * of this launcher.
 * <p>
 * Starting and stopping a demo is delegated to its own
 * {@link DemoLauncher} (see {@link DemoDescriptor}): ordinarily
 * {@link ManagedDemoLauncher}, a thin wrapper around
 * {@link CovalentContainer#deployComponents(Class[])} and
 * {@link CovalentContainer#start()}/{@link CovalentContainer#stop()} -
 * never {@link CovalentContainer#dispose()}, so the container stays
 * usable and launching the same demo again is just a fresh deploy and
 * start. A demo whose startup needs more than a fixed set of managed
 * implementations (see {@code EcosystemStatsExportDemoLauncher}) supplies
 * its own {@link DemoLauncher} instead, without this class needing to
 * know anything about it.
 * <p>
 * Each demo is expected to open (on {@code @Activation}) and close (on
 * {@code @Passivation}) its own window; this launcher only ever manages
 * container state, never a demo's JavaFX scene graph directly. Closing a
 * demo's window directly (rather than through this launcher's button)
 * leaves the container itself running - use the button to actually stop it.
 * <p>
 * {@link #toggleDemo} does the actual start/stop off the JavaFX Application
 * Thread: activation can involve real, sometimes slow synchronous work (starting
 * a container and building every view of a demo, for instance), and running
 * that directly on a button's own
 * event handler would freeze this whole launcher window, with the button
 * visibly staying pressed, for however long it takes. See
 * {@link #toggleDemo} for how the button itself stays safe to click again
 * meanwhile.
 */
public class LauncherApplication extends Application {

	// The Neural Network Trainer and Solar System Simulation demos each
	// deploy as a single composite (NeuralNetworkDemoImpl,
	// SolarSystemDemoImpl), which groups every function of the
	// application as @Implementation children, in their correct
	// activation order. See each composite's own javadoc for the full
	// ordering rationale.
	//
	// The Ecosystem Demo uses its own DemoLauncher
	// (EcosystemStatsExportDemoLauncher) instead of ManagedDemoLauncher:
	// its HmiBuilder requires a raw StatsExportWidget to already be
	// registered before the composite deploys, and a raw StatsExporter
	// wired once Ecosystem is activated. Raw components cannot be wired
	// from inside a managed component, so this extra sequencing lives
	// in that dedicated DemoLauncher, keeping this launcher itself free
	// of any Ecosystem-specific or raw-component knowledge.
	private static final List<DemoDescriptor> DEMOS = List.of(
			new DemoDescriptor("Hello World",
					new ManagedDemoLauncher(new Class<?>[] { HelloWorldDemoImpl.class })),
			new DemoDescriptor("Neural Network Trainer",
					new ManagedDemoLauncher(new Class<?>[] { NeuralNetworkDemoImpl.class })),
			new DemoDescriptor("Solar System Simulation",
					new ManagedDemoLauncher(new Class<?>[] { SolarSystemDemoImpl.class })),
			new DemoDescriptor("Ecosystem Demo", new EcosystemStatsExportDemoLauncher()),
			new DemoDescriptor("Rubik's Cube Solver", new ManagedDemoLauncher(new Class<?>[] {
					HmiCoreImpl.class, CubeImpl.class, SolverImpl.class, UserConfigurationImpl.class,
					HmiBuilderImpl.class })));

	/**
	 * Launches the JavaFX launcher application.
	 * @param args command-line arguments, passed to JavaFX
	 */
	public static void main(final String[] args) {
		launch(args);
	}

	@Override
	public void start(final Stage primaryStage) {
		VBox root = new VBox(10);
		root.setPadding(new Insets(16));
		for (DemoDescriptor demo : DEMOS) {
			root.getChildren().add(this.buildDemoRow(demo));
		}

		primaryStage.setTitle("Covalent Demos Launcher");
		primaryStage.setScene(new Scene(root, 320, 120 + DEMOS.size() * 40));
		primaryStage.show();
	}

	private HBox buildDemoRow(final DemoDescriptor demo) {
		// Every demo runs in the "main" container's own family of managed
		// containers, but under its own name, so it is fully isolated from
		// the other demos and from the launcher itself.
		CovalentContainer container = Covalent.managedContainer(demo.name());
		// Enabled here, once per container, rather than per DemoLauncher: it
		// is read live at every start() call (see
		// CovalentContainerParameters#setCircularDependencyChecking), so
		// setting it once after creation already covers every future
		// relaunch of this same container. This is what lets Supervision's
		// Dependency Management function show a given demo's dependency
		// graph once that demo is the observed container.
		container.getParameters().setCircularDependencyChecking(true);

		Label label = new Label(demo.name());
		label.setPrefWidth(180);

		Button button = new Button();
		button.setPrefWidth(100);
		this.refreshButtonLabel(button, container);

		button.setOnAction(event -> this.toggleDemo(button, container, demo));

		return new HBox(10, label, button);
	}

	/**
	 * Starts or stops {@code container} off the JavaFX Application Thread,
	 * on a dedicated background thread, since either can do real,
	 * synchronous work that would otherwise freeze this launcher window
	 * (see this class's javadoc). The button is disabled and shows a
	 * transitional label for the duration, both so its state cannot
	 * mislead while the work is in flight and so a second click cannot
	 * start a second, concurrent start/stop of the very same container;
	 * the final label refresh, like every other touch of this button, is
	 * only ever done back on the FX Application Thread through
	 * {@link Platform#runLater}.
	 *
	 * @param button  the demo row's own button, read and written here
	 * @param container the demo's container to start or stop
	 * @param demo the demo descriptor, for its {@link DemoLauncher} when
	 *             starting or stopping
	 */
	private void toggleDemo(final Button button, final CovalentContainer container, final DemoDescriptor demo) {
		boolean starting = !container.isStarted();
		button.setDisable(true);
		button.setText(starting ? "Launching..." : "Stopping...");

		Thread worker = new Thread(() -> {
			try {
				if (starting) {
					demo.launcher().start(container);
				} else {
					demo.launcher().stop(container);
				}
			} finally {
				Platform.runLater(() -> {
					this.refreshButtonLabel(button, container);
					button.setDisable(false);
				});
			}
		}, "demo-launcher-" + demo.name());
		worker.setDaemon(true);
		worker.start();
	}

	private void refreshButtonLabel(final Button button, final CovalentContainer container) {
		button.setText(container.isStarted() ? "Stop" : "Launch");
	}
}
