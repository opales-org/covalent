/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import javafx.scene.paint.Color;

/**
 * Conversion between JavaFX colors and the web strings ({@code #rrggbbaa}) that carry colors outside the Views:
 * in the actions, the services and the stored preferences.
 */
public final class WebColors {

	private WebColors() {
	}

	/**
	 * Formats a color as {@code #rrggbbaa}.
	 *
	 * @param color the color, may be {@code null}
	 * @return its web string, {@code null} for {@code null}
	 */
	public static String toWeb(final Color color) {
		if (color == null) {
			return null;
		}
		return String.format("#%02x%02x%02x%02x", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
				Math.round(color.getBlue() * 255), Math.round(color.getOpacity() * 255));
	}

	/**
	 * Parses a web color.
	 *
	 * @param web the web string, may be {@code null}
	 * @return the color, {@code null} when {@code web} is {@code null} or unreadable
	 */
	public static Color toColor(final String web) {
		if (web == null) {
			return null;
		}
		try {
			return Color.web(web);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
