/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * Makes a side panel of a {@link SplitPane} collapsible: a slim strip along its inner edge
 * holds a chevron that folds the panel down to that strip, and unfolds it back to the width
 * it had.
 */
public final class CollapsibleSide {

	/** Width of the strip left when the panel is folded. */
	private static final double STRIP_WIDTH = 18;

	private static final String CHEVRON_LEFT = "M7 2.5 L3.5 6 L7 9.5";
	private static final String CHEVRON_RIGHT = "M5 2.5 L8.5 6 L5 9.5";

	private final SplitPane split;
	private final int dividerIndex;
	private final boolean leftSide;
	private final Region content;
	private final HBox wrapper;
	private final SVGPath chevron = new SVGPath();
	private boolean collapsed = false;
	private double expandedDividerPosition = -1;

	/** The name its folded state is stored under, {@code null} when not stored. */
	private String settingsKey;

	private CollapsibleSide(final SplitPane split, final Region content, final boolean leftSide) {
		this.split = split;
		this.leftSide = leftSide;
		this.content = content;
		this.dividerIndex = leftSide ? 0 : split.getItems().size() - 2;

		Button toggle = new Button();
		toggle.getStyleClass().add("sv-collapse-toggle");
		toggle.setFocusTraversable(false);
		this.chevron.getStyleClass().add("sv-collapse-chevron");
		toggle.setGraphic(this.chevron);
		toggle.setOnAction(event -> this.setCollapsed(!this.collapsed));
		Tooltips.install(toggle, ComponentViewTexts.get("side.toggle.tip"));

		VBox strip = new VBox(toggle);
		strip.setAlignment(Pos.TOP_CENTER);
		strip.getStyleClass().add("sv-collapse-strip");
		strip.setMinWidth(STRIP_WIDTH);
		strip.setPrefWidth(STRIP_WIDTH);
		strip.setMaxWidth(STRIP_WIDTH);

		HBox.setHgrow(content, Priority.ALWAYS);
		// The panel gives way when narrowed, so the strip and its toggle stay in view.
		content.setMinWidth(0);
		this.wrapper = leftSide ? new HBox(content, strip) : new HBox(strip, content);
		this.wrapper.setMinWidth(STRIP_WIDTH);
		this.wrapper.setMaxWidth(this.expandedMaxWidth());
		this.updateChevron();
	}

	/**
	 * Wraps a side panel and puts it in place in the split pane.
	 *
	 * @param split the split pane, holding {@code content} as its first or last item
	 * @param content the side panel
	 * @param leftSide whether the panel is the first item, rather than the last
	 * @return the node standing for the panel in the split pane
	 */
	public static Region install(final SplitPane split, final Region content, final boolean leftSide) {
		return install(split, content, leftSide, null);
	}

	/**
	 * Same as {@link #install(SplitPane, Region, boolean)}, the panel opening folded or not as
	 * it was left.
	 *
	 * @param split the split pane, holding {@code content} as its first or last item
	 * @param content the side panel
	 * @param leftSide whether the panel is the first item, rather than the last
	 * @param settingsKey the name the panel's folded state is stored under
	 * @return the node standing for the panel in the split pane
	 */
	public static Region install(final SplitPane split, final Region content, final boolean leftSide,
			final String settingsKey) {
		int index = split.getItems().indexOf(content);
		CollapsibleSide side = new CollapsibleSide(split, content, leftSide);
		split.getItems().set(index, side.wrapper);
		SplitPane.setResizableWithParent(side.wrapper, false);
		if (settingsKey != null && SupervisorSettings.isPanelCollapsed(settingsKey)) {
			// Folded once the split pane is laid out and its divider placed, so unfolding restores it.
			split.widthProperty().addListener(new ChangeListener<Number>() {
				@Override
				public void changed(final ObservableValue<? extends Number> observable,
						final Number oldWidth, final Number newWidth) {
					if (newWidth.doubleValue() > 0) {
						observable.removeListener(this);
						Platform.runLater(() -> side.setCollapsed(true));
					}
				}
			});
		}
		side.settingsKey = settingsKey;
		return side.wrapper;
	}

	private void setCollapsed(final boolean collapse) {
		if (collapse == this.collapsed) {
			return;
		}
		this.collapsed = collapse;
		if (this.settingsKey != null) {
			SupervisorSettings.setPanelCollapsed(this.settingsKey, collapse);
		}
		// The other dividers keep their place on screen, so the space freed or taken goes to
		// and comes from the central area, never another side panel.
		double width = this.split.getWidth();
		double[] positions = this.split.getDividerPositions();
		double ownPosition;
		if (collapse) {
			this.expandedDividerPosition = positions[this.dividerIndex];
			this.content.setVisible(false);
			this.content.setManaged(false);
			this.wrapper.setMaxWidth(STRIP_WIDTH);
			ownPosition = width <= 0 ? positions[this.dividerIndex]
					: this.leftSide ? STRIP_WIDTH / width : 1 - STRIP_WIDTH / width;
		} else {
			this.content.setVisible(true);
			this.content.setManaged(true);
			this.wrapper.setMaxWidth(this.expandedMaxWidth());
			ownPosition = this.expandedDividerPosition >= 0 ? this.expandedDividerPosition : positions[this.dividerIndex];
		}
		positions[this.dividerIndex] = ownPosition;
		this.split.setDividerPositions(positions);
		Platform.runLater(() -> this.split.setDividerPositions(positions));
		this.updateChevron();
	}


	/** The panel's own width limit, if it sets one, plus the strip. */
	private double expandedMaxWidth() {
		double contentMax = this.content.getMaxWidth();
		return contentMax > 0 && contentMax < Double.MAX_VALUE ? contentMax + STRIP_WIDTH : Double.MAX_VALUE;
	}

	private void updateChevron() {
		// Points where the panel goes: inwards to fold it, outwards to unfold it.
		boolean pointLeft = this.leftSide != this.collapsed;
		this.chevron.setContent(pointLeft ? CHEVRON_LEFT : CHEVRON_RIGHT);
	}
}
