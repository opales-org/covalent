/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view.impl;

import java.util.EnumMap;
import java.util.Map;

import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;

import javafx.scene.paint.Color;

/**
 * Default port/wire palette of the dark theme. The light theme's defaults
 * are {@code PortPalette}'s own initial values. Keeps the light palette's
 * color logic - services blue, events amber/orange, parameters violet, a
 * deeper shade for the providing/producing/offering side - with lightness
 * raised to stay readable on a dark canvas, and inverts the card chrome:
 * dark fills, light outline and text.
 */
final class DarkPortPalette {

	private DarkPortPalette() {
	}

	/** @return a fresh, mutable copy of every key's dark-theme default color */
	static Map<PortColorKey, Color> defaults() {
		Map<PortColorKey, Color> palette = new EnumMap<>(PortColorKey.class);
		palette.put(PortColorKey.GRAPHITE, Color.web("#8e8eda"));
		palette.put(PortColorKey.BODY, Color.web("#161e2b"));
		palette.put(PortColorKey.HEADER, Color.web("#1d2636"));
		palette.put(PortColorKey.CARD_BORDER_SOFT, Color.web("#2f3b51"));
		palette.put(PortColorKey.HEADER_DIVIDER, Color.web("#2a3446"));
		palette.put(PortColorKey.ICON_DIVIDER, Color.web("#3b4860"));
		palette.put(PortColorKey.STATUS_ACTIVATED, Color.web("#34d399"));
		palette.put(PortColorKey.STATUS_PASSIVATED, Color.web("#60a5fa"));
		palette.put(PortColorKey.STATUS_STARTING, Color.web("#facc15"));
		palette.put(PortColorKey.STATUS_INACTIVE, Color.web("#94a3b8"));
		palette.put(PortColorKey.STATUS_FAILED, Color.web("#f87171"));
		palette.put(PortColorKey.STATUS_MIXED, Color.web("#a78bfa"));
		palette.put(PortColorKey.STATUS_DEGRADED, Color.web("#fb923c"));
		palette.put(PortColorKey.SLOT_IDLE, Color.web("#0f141d"));
		palette.put(PortColorKey.SERVICE_PROVIDED, Color.web("#3b3bff"));
		palette.put(PortColorKey.SERVICE_USED, Color.web("#418afe"));
		palette.put(PortColorKey.EVENT_PRODUCED, Color.web("#f59e0b"));
		palette.put(PortColorKey.EVENT_CONSUMED, Color.web("#ff7a33"));
		palette.put(PortColorKey.PARAMETER, Color.web("#8d05b9"));
		palette.put(PortColorKey.PARAMETER_ACCESSED, Color.web("#b84bf3"));
		palette.put(PortColorKey.COMPONENT_TYPE, Color.web("#8591a6"));
		palette.put(PortColorKey.SERVICE_ACTIVITY, Color.web("#8584e6"));
		palette.put(PortColorKey.SERVICE_FAILURE, Color.web("#fd3030"));
		palette.put(PortColorKey.EVENT_ACTIVITY, Color.web("#77bca3"));
		palette.put(PortColorKey.HIGHLIGHT, Color.web("#fdd447"));
		palette.put(PortColorKey.HOVER, Color.web("#1e2b40"));
		palette.put(PortColorKey.SELECTION, Color.web("#818cf8"));
		return palette;
	}
}
