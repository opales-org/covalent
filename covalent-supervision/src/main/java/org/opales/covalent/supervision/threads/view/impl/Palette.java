/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

/**
 * The colors the Threads panel's canvases draw with, taken from the theme: each one is the
 * background of a hidden swatch styled by {@code supervision.css} as
 * {@code sv-threads-swatch-<name>}, so that they follow the light and dark themes.
 */
final class Palette {

	static final String[] NAMES = { "runs", "waits", "long", "blocked", "idle", "untracked", "lane", "grid", "axis",
			"cursor", "events", "general", "dedicated", "pill", "pill-ink", "pill-edge", "readout", "readout-border", "ink", "load", "load-unknown" };

	private final Pane holder = new Pane();

	private final Map<String, Region> swatches = new HashMap<>();

	Palette() {
		for (String name : NAMES) {
			Region swatch = new Region();
			swatch.getStyleClass().add("sv-threads-swatch-" + name);
			this.swatches.put(name, swatch);
			this.holder.getChildren().add(swatch);
		}
		this.holder.setManaged(false);
		this.holder.setVisible(false);
		this.holder.setMouseTransparent(true);
	}

	/** @return the hidden swatches, to place in the panel so that they are styled */
	Node node() {
		return this.holder;
	}

	Color color(final String name) {
		Region swatch = this.swatches.get(name);
		Background background = swatch == null ? null : swatch.getBackground();
		if (background != null && !background.getFills().isEmpty()) {
			Paint fill = background.getFills().get(0).getFill();
			if (fill instanceof Color color) {
				return color;
			}
		}
		return Color.GRAY;
	}
}
