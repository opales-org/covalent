/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import java.util.regex.Pattern;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.stage.Screen;
import javafx.util.Duration;

/**
 * The Supervisor's tooltips: shown after a short pause, kept as long as the pointer stays on
 * their node, and placed under that node rather than at the pointer. A tooltip at the pointer
 * can open beneath it - near a screen edge, or for a large text - which takes the pointer off
 * the node, hides the tooltip, and starts over: the flicker this placement avoids.
 */
public final class Tooltips {

	private static final Duration SHOW_DELAY = Duration.millis(450);
	private static final Duration HIDE_DELAY = Duration.millis(120);
	private static final double MAX_WIDTH = 380;

	/** A dot directly followed by a letter: a break opportunity in a qualified name. */
	private static final Pattern DOT_BEFORE_LETTER = Pattern.compile("\\.(?=\\p{L})");

	/** Space between the node and its tooltip. */
	private static final double GAP = 6;

	private Tooltips() {
	}

	/**
	 * Gives this node a tooltip.
	 *
	 * @param node the node the tooltip describes
	 * @param text the tooltip's text
	 * @return the tooltip, installed on the node
	 */
	public static Tooltip install(final Node node, final String text) {
		Tooltip tooltip = new Tooltip(text);
		tooltip.setShowDelay(SHOW_DELAY);
		tooltip.setHideDelay(HIDE_DELAY);
		tooltip.setShowDuration(Duration.INDEFINITE);
		tooltip.setWrapText(true);
		tooltip.setMaxWidth(MAX_WIDTH);
		breakAtDots(tooltip);
		// Moved under its node before its window opens, and hidden until centered there: a window
		// opened at the pointer takes the pointer off the node, which hides the tooltip at once.
		tooltip.setOnShowing(event -> {
			tooltip.setOpacity(0);
			placeUnder(tooltip, node);
		});
		tooltip.setOnShown(event -> {
			placeUnder(tooltip, node);
			tooltip.setOpacity(1);
		});
		if (node instanceof Control control) {
			control.setTooltip(tooltip);
		} else {
			Tooltip.install(node, tooltip);
		}
		return tooltip;
	}

	/**
	 * Lets a qualified name wrap after its dots rather than in the middle of a word: a hair space,
	 * the narrowest space that JavaFX wraps at, follows each dot that a letter follows, whatever
	 * text the tooltip is given.
	 */
	private static void breakAtDots(final Tooltip tooltip) {
		tooltip.textProperty().addListener((property, previous, text) -> breakAtDots(tooltip, text));
		breakAtDots(tooltip, tooltip.getText());
	}

	private static void breakAtDots(final Tooltip tooltip, final String text) {
		if (text != null) {
			String breakable = breakable(text);
			if (!breakable.equals(text)) {
				tooltip.setText(breakable);
			}
		}
	}

	/**
	 * Lets a qualified name wrap after its dots - see {@code breakAtDots(Tooltip)}.
	 *
	 * @param text a text, possibly a qualified name
	 * @return the text with a hair space after each dot that a letter follows
	 */
	static String breakable(final String text) {
		return DOT_BEFORE_LETTER.matcher(text).replaceAll(".\u200A");
	}

	/** Centers the tooltip under its node, or above it when the screen ends below. */
	static void placeUnder(final Tooltip tooltip, final Node node) {
		Bounds bounds = node.localToScreen(node.getBoundsInLocal());
		if (bounds == null) {
			return;
		}
		double width = tooltip.getWidth();
		double height = tooltip.getHeight();
		Rectangle2D screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(),
				bounds.getHeight()).stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
		double x = bounds.getMinX() + (bounds.getWidth() - width) / 2;
		x = Math.max(screen.getMinX(), Math.min(screen.getMaxX() - width, x));
		double y = bounds.getMaxY() + GAP;
		if (y + height > screen.getMaxY()) {
			y = bounds.getMinY() - GAP - height;
		}
		tooltip.setX(x);
		tooltip.setY(y);
	}
}
