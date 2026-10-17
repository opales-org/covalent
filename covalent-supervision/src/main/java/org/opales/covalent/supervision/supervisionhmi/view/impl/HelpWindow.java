/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view.impl;

import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorMessages;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;

import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The supervisor's help: one window, kept beside the supervisor, showing the embedded HTML help
 * ({@code help/help.html}, or its translation in the supervisor's language, such as
 * {@code help/help_fr.html}). It is created when first asked for, opens on the section of the
 * function shown, follows the supervisor's theme and remembers its place and size.
 */
final class HelpWindow {

	private static final Logger log = Logger.getLogger(HelpWindow.class.getName());

	private static final String PAGE = "help/help.html";

	private static final double DEFAULT_WIDTH = 980;

	private static final double DEFAULT_HEIGHT = 720;

	private Stage stage;

	private WebView view;

	private boolean loaded;

	private SupervisorTheme theme;

	/** The section to show once the page is loaded. */
	private String pendingSection;

	/**
	 * Shows the help on a section, bringing the window to the front if it is already open.
	 *
	 * @param owner   the supervisor's window
	 * @param section the id of the section to show
	 * @param theme   the supervisor's current theme
	 */
	void show(final Window owner, final String section, final SupervisorTheme theme) {
		if (this.stage == null) {
			this.create(owner, section, theme);
		} else {
			this.setTheme(theme);
			if (this.loaded) {
				this.showSection(section);
			} else {
				this.pendingSection = section;
			}
		}
		this.stage.show();
		this.stage.setIconified(false);
		this.stage.toFront();
	}

	/** Applies the supervisor's theme to the help, if it is open. */
	void setTheme(final SupervisorTheme theme) {
		this.theme = theme;
		if (this.stage != null) {
			this.stage.getScene().setFill(background(theme));
			if (this.loaded) {
				this.view.getEngine().executeScript(
						"document.documentElement.setAttribute('data-theme', '" + themeName(theme) + "')");
			}
		}
	}

	void close() {
		if (this.stage != null) {
			this.stage.close();
			this.stage = null;
			this.view = null;
			this.loaded = false;
		}
	}

	private void create(final Window owner, final String section, final SupervisorTheme theme) {
		this.theme = theme;
		WebView webView = new WebView();
		this.view = webView;
		webView.setContextMenuEnabled(false);
		this.pendingSection = section;
		webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
			if (this.view != webView) {
				// The window was closed while the page was loading.
				return;
			}
			this.loaded = newState == Worker.State.SUCCEEDED;
			if (this.loaded) {
				this.setTheme(this.theme);
				this.showSection(this.pendingSection);
			} else if (newState == Worker.State.FAILED) {
				log.log(Level.WARNING, "Failed to load the help page " + pageUrl(),
						webView.getEngine().getLoadWorker().getException());
			}
		});
		// The page is loaded without query nor fragment: some platforms do not resolve them on
		// file and jar URLs. The theme and the section are applied once it is loaded.
		webView.getEngine().load(pageUrl());

		Scene scene = new Scene(webView, DEFAULT_WIDTH, DEFAULT_HEIGHT, background(theme));
		scene.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				this.stage.close();
			}
		});
		this.stage = new Stage();
		this.stage.setTitle(SupervisionHmiTexts.get("help.title"));
		if (owner != null) {
			this.stage.initOwner(owner);
			if (owner instanceof Stage ownerStage) {
				this.stage.getIcons().setAll(ownerStage.getIcons());
			}
		}
		this.stage.setScene(scene);
		this.place(owner);
		this.rememberPlacement();
	}

	/** Puts the window back where it was left, or beside the supervisor the first time. */
	private void place(final Window owner) {
		double x = SupervisorSettings.getDouble(SupervisorSettings.HELP_WINDOW_X, Double.NaN);
		double y = SupervisorSettings.getDouble(SupervisorSettings.HELP_WINDOW_Y, Double.NaN);
		double width = SupervisorSettings.getDouble(SupervisorSettings.HELP_WINDOW_WIDTH, Double.NaN);
		double height = SupervisorSettings.getDouble(SupervisorSettings.HELP_WINDOW_HEIGHT, Double.NaN);
		if (!Double.isNaN(x) && !Double.isNaN(y) && width > 200 && height > 150
				&& !Screen.getScreensForRectangle(x, y, width, height).isEmpty()) {
			this.stage.setX(x);
			this.stage.setY(y);
			this.stage.setWidth(width);
			this.stage.setHeight(height);
		} else if (owner != null) {
			this.stage.setX(owner.getX() + Math.max(0, owner.getWidth() - DEFAULT_WIDTH - 40));
			this.stage.setY(owner.getY() + 60);
		}
	}

	private void rememberPlacement() {
		Stage window = this.stage;
		PauseTransition settle = new PauseTransition(Duration.millis(400));
		settle.setOnFinished(event -> {
			if (window.isIconified() || window.isMaximized()) {
				return;
			}
			SupervisorSettings.putDouble(SupervisorSettings.HELP_WINDOW_X, window.getX());
			SupervisorSettings.putDouble(SupervisorSettings.HELP_WINDOW_Y, window.getY());
			SupervisorSettings.putDouble(SupervisorSettings.HELP_WINDOW_WIDTH, window.getWidth());
			SupervisorSettings.putDouble(SupervisorSettings.HELP_WINDOW_HEIGHT, window.getHeight());
		});
		for (ObservableValue<?> property : List.of(window.xProperty(), window.yProperty(),
				window.widthProperty(), window.heightProperty())) {
			property.addListener(observable -> settle.playFromStart());
		}
	}

	private void showSection(final String section) {
		if (section != null) {
			this.view.getEngine().executeScript("showSection('" + section + "')");
		}
	}

	private static String pageUrl() {
		String language = SupervisorMessages.locale().getLanguage();
		URL translated = HelpWindow.class.getResource("help/help_" + language + ".html");
		return (translated != null ? translated : HelpWindow.class.getResource(PAGE)).toExternalForm();
	}

	private static String themeName(final SupervisorTheme theme) {
		return theme == SupervisorTheme.DARK ? "dark" : "light";
	}

	private static Color background(final SupervisorTheme theme) {
		return theme == SupervisorTheme.DARK ? Color.web("#0b0f16") : Color.web("#d9dfe7");
	}
}
