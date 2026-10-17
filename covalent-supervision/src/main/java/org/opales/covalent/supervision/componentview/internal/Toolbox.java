/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import org.opales.covalent.supervision.componentview.internal.diagram.edges.EdgeRoutingStyle;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * Building blocks of a diagram toolbox: titled categories of icon-only
 * controls, each control labeled by its tooltip.
 */
public final class Toolbox {

	/** Icons as SVG path data on a 16x16 grid, stroked by the "sv-tool-icon" style class. */
	public static final String ICON_EDGE_STRAIGHT = "M2.5 13.5 L13.5 2.5";
	/** Icon of the corner link shape: one vertical and one horizontal segment. */
	public static final String ICON_EDGE_CORNER = "M2.5 13.5 V8 H13.5 V2.5";
	/** Icon of the round link shape: a corner link with rounded bends. */
	public static final String ICON_EDGE_ROUND = "M2.5 13.5 V11 Q2.5 8 5.5 8 H10.5 Q13.5 8 13.5 5 V2.5";
	/** Three bulleted lines: shows or hides a diagram's legend. */
	public static final String ICON_LEGEND = "M2.5 4 H3.5 M6 4 H13.5 M2.5 8 H3.5 M6 8 H13.5 M2.5 12 H3.5 M6 12 H13.5";
	/** Icon of the recentering action: a target with four ticks. */
	public static final String ICON_RECENTER = "M8 1.5 V4.5 M8 11.5 V14.5 M1.5 8 H4.5 M11.5 8 H14.5 "
			+ "M8 5 A3 3 0 1 1 7.99 5 Z";
	/** Icon of the snapshot action: a camera. */
	public static final String ICON_SNAPSHOT = "M1.5 5 H4.5 L6 3 H10 L11.5 5 H14.5 V13 H1.5 Z "
			+ "M8 6.5 A2.5 2.5 0 1 1 7.99 6.5 Z";

	private Toolbox() {
	}

	/**
	 * The toolbox row, holding categories side by side.
	 *
	 * @param categories the categories, as built by {@link #category(String, Node...)}
	 * @return the toolbox row
	 */
	public static HBox bar(final Node... categories) {
		HBox toolbox = new HBox(8, categories);
		toolbox.getStyleClass().add("sv-toolbox");
		toolbox.setPadding(new Insets(6, 8, 6, 8));
		return toolbox;
	}

	/**
	 * A titled category whose controls stay on one row, sized to its content.
	 *
	 * @param title the category's title
	 * @param controls the icon controls of the category
	 * @return the category
	 */
	public static Region category(final String title, final Node... controls) {
		Label header = new Label(title);
		header.getStyleClass().add("sv-category-title");
		HBox row = new HBox(6, controls);
		row.setAlignment(Pos.CENTER_LEFT);
		VBox box = new VBox(5, header, row);
		box.setPadding(new Insets(6, 8, 8, 8));
		box.getStyleClass().add("sv-category");
		box.setMinWidth(Region.USE_PREF_SIZE);
		box.setMaxWidth(Region.USE_PREF_SIZE);
		return box;
	}

	/**
	 * Creates a push button showing an icon, labeled by its tooltip.
	 *
	 * @param iconPath the icon as SVG path data on a 16x16 grid
	 * @param tooltip the text of the button's tooltip
	 * @return the button
	 */
	public static Button iconButton(final String iconPath, final String tooltip) {
		Button button = new Button();
		decorate(button, iconPath, tooltip);
		return button;
	}

	/**
	 * Creates a toggle button showing an icon, labeled by its tooltip.
	 *
	 * @param iconPath the icon as SVG path data on a 16x16 grid
	 * @param tooltip the text of the button's tooltip
	 * @return the toggle button
	 */
	public static ToggleButton iconToggle(final String iconPath, final String tooltip) {
		ToggleButton toggle = new ToggleButton();
		decorate(toggle, iconPath, tooltip);
		return toggle;
	}

	/**
	 * Straight / corner / round link shape, kept in sync with the given property both ways.
	 *
	 * @param style the link shape to show and to change
	 * @return the selector, always with one shape selected
	 */
	public static HBox routingStyleSelector(final ObjectProperty<EdgeRoutingStyle> style) {
		ToggleGroup group = new ToggleGroup();
		HBox selector = new HBox(2,
				styleToggle(group, ICON_EDGE_STRAIGHT, ComponentViewTexts.get("routing.straight"), EdgeRoutingStyle.STRAIGHT),
				styleToggle(group, ICON_EDGE_CORNER, ComponentViewTexts.get("routing.corner"), EdgeRoutingStyle.CORNER),
				styleToggle(group, ICON_EDGE_ROUND, ComponentViewTexts.get("routing.round"), EdgeRoutingStyle.ROUND));
		selector.getStyleClass().add("sv-segmented");
		selectStyle(group, style.get());
		group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
			if (newToggle != null) {
				style.set((EdgeRoutingStyle) newToggle.getUserData());
			} else if (oldToggle != null) {
				// A ToggleButton deselects on a second click; the selector always keeps one.
				group.selectToggle(oldToggle);
			}
		});
		style.addListener((observable, oldStyle, newStyle) -> selectStyle(group, newStyle));
		return selector;
	}

	private static ToggleButton styleToggle(final ToggleGroup group, final String iconPath, final String tooltip,
			final EdgeRoutingStyle style) {
		ToggleButton toggle = iconToggle(iconPath, tooltip);
		toggle.setToggleGroup(group);
		toggle.setUserData(style);
		return toggle;
	}

	private static void selectStyle(final ToggleGroup group, final EdgeRoutingStyle style) {
		for (Toggle toggle : group.getToggles()) {
			if (toggle.getUserData() == style) {
				group.selectToggle(toggle);
			}
		}
	}

	private static void decorate(final ButtonBase control, final String iconPath, final String tooltip) {
		SVGPath icon = new SVGPath();
		icon.setContent(iconPath);
		icon.getStyleClass().add("sv-tool-icon");
		control.setGraphic(icon);
		Tooltips.install(control, tooltip);
		control.getStyleClass().add("sv-icon-button");
		control.setMinWidth(Region.USE_PREF_SIZE);
	}
}
