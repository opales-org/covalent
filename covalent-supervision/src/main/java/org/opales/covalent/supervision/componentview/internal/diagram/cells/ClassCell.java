/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

import org.opales.covalent.supervision.componentview.internal.HoverCards;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;

import java.util.Locale;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Cell drawn as a rounded card: the icon of a leaf, composite or raw component, a thin divider and a short name, with
 * the qualified name in a hover card. It can be selected, highlighted with a colored border, marked as a neighbor
 * with a dashed border, clicked and given a context menu. Its look is left to the style class
 * {@code sv-class-cell} so that the stylesheet owns it.
 */
public class ClassCell extends AbstractCell {

	/**
	 * Style class added while this cell is selected. The cell sets no background or border of its own except
	 * the inline look of a highlight or selected color: the Supervisor's themes style {@code sv-class-cell} and
	 * the classes below.
	 */
	private static final String SELECTED_STYLE_CLASS = "sv-class-cell-selected";
	private static final String FOCUS_STYLE_CLASS = "sv-class-cell-focus";
	private static final String NEIGHBOR_STYLE_CLASS = "sv-class-cell-neighbor";
	private static final String NEIGHBOR_PLAIN_STYLE_CLASS = "sv-class-cell-neighbor-plain";

	/** The longest name drawn whole; a longer one is cut and ends with an ellipsis. */
	private static final int MAX_NAME_LENGTH = 30;

	private static final double ICON_SIZE = 24;
	private static final double DIVIDER_HEIGHT = 24;

	/**
	 * A hexagon around a smaller one crossed by three diagonals joining its opposite corners: a leaf component, as
	 * the component diagram draws it.
	 */
	private static final String LEAF_ICON = "M20.23 16.75L12 21.5L3.77 16.75L3.77 7.25L12 2.5L20.23 7.25Z "
			+ "M15.7 14.14L12 16.27L8.3 14.14L8.3 9.86L12 7.72L15.7 9.86Z "
			+ "M15.7 14.14L8.3 9.86 M12 16.27L12 7.72 M8.3 14.14L15.7 9.86";

	/** Three touching leaf drawings: a composite component. */
	private static final String COMPOSITE_ICON = "M17.1 10.53L12 13.47L6.9 10.53L6.9 4.64L12 1.69L17.1 4.64Z "
			+ "M14.3 8.91L12 10.23L9.7 8.91L9.7 6.26L12 4.93L14.3 6.26Z "
			+ "M14.3 8.91L9.7 6.26 M12 10.23L12 4.93 M9.7 8.91L14.3 6.26 "
			+ "M12 19.36L6.9 22.31L1.8 19.36L1.8 13.47L6.9 10.53L12 13.47Z "
			+ "M9.19 17.74L6.9 19.07L4.6 17.74L4.6 15.09L6.9 13.77L9.19 15.09Z "
			+ "M9.19 17.74L4.6 15.09 M6.9 19.07L6.9 13.77 M4.6 17.74L9.19 15.09 "
			+ "M22.2 19.36L17.1 22.31L12 19.36L12 13.47L17.1 10.53L22.2 13.47Z "
			+ "M19.4 17.74L17.1 19.07L14.81 17.74L14.81 15.09L17.1 13.77L19.4 15.09Z "
			+ "M19.4 17.74L14.81 15.09 M17.1 19.07L17.1 13.77 M14.81 17.74L19.4 15.09";

	/** A hexagon around a plain object drawn as a circle: a raw component, which the container does not manage. */
	private static final String RAW_ICON = "M20.23 16.75L12 21.5L3.77 16.75L3.77 7.25L12 2.5L20.23 7.25Z "
			+ "M14.85 12A2.85 2.85 0 1 1 9.15 12A2.85 2.85 0 1 1 14.85 12Z";

	private final String aClass;
	private final String name;
	private final String id;
	private boolean isComponent = false;
	private boolean composite;
	private boolean raw;
	private boolean neighbor;
	private Runnable onClick;
	private Consumer<ContextMenuEvent> onContextMenuRequested;

	/**
	 * Overrides the plain selected/neutral look with a colored border and a
	 * light tint of the same color - null restores whichever of those two
	 * the {@link #selected} flag picks. Lets a caller (e.g. a
	 * dependency-direction highlight) mark a cell without touching {@link
	 * #selected}, which keeps its own meaning (e.g. cycle membership).
	 */
	private Color highlightColor;

	/** The color the selected look takes instead of the stylesheet's, {@code null} for the stylesheet's. */
	private Color selectedColor;

	/**
	 * Marks the cell as the focus of its view, drawn by the stylesheet's
	 * {@code .sv-class-cell-focus} so it follows the theme. Wins over {@link
	 * #highlightColor}.
	 */
	private boolean focused;
	private Runnable appearanceRefresh;

	public ClassCell(final String name, final String id, final String aClass) {
		this.name = name;
		this.id = id;
		this.aClass = aClass;
	}

	public ClassCell(final String name, final String aClass) {
		this(name, null, aClass);
	}

	public ClassCell() {
		this(null, null, null);
	}

	public String getComponentClass() {
		return this.aClass;
	}

	@Override
	public Region getGraphic(final Graph graph) {
		final BorderPane view = new BorderPane();
		view.getStyleClass().add("sv-class-cell");
		view.setMinHeight(44);
		view.setMinWidth(150);

		view.setPadding(new Insets(0, 14, 0, 10));

		// No hardcoded background/border here: left at their default (CSS-controlled)
		// value, so the stylesheet's .sv-class-cell owns them from the start -
		// see inlineLook for why they must stay released like this.
		HBox row = new HBox(9);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getChildren().add(this.icon());
		Region divider = new Region();
		divider.getStyleClass().add("sv-class-cell-divider");
		divider.setMinSize(1, DIVIDER_HEIGHT);
		divider.setPrefSize(1, DIVIDER_HEIGHT);
		divider.setMaxSize(1, DIVIDER_HEIGHT);
		row.getChildren().add(divider);
		if (this.name != null) {
			Label nameLabel = new Label(this.name.length() > MAX_NAME_LENGTH
					? this.name.substring(0, MAX_NAME_LENGTH) + "…" : this.name);
			nameLabel.setStyle("-fx-font-weight: bold;");
			nameLabel.getStyleClass().add("sv-class-cell-name");
			row.getChildren().add(nameLabel);
		}
		view.setCenter(row);

		if (this.id != null) {
			Label idLabel = new Label(this.id);
			idLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
			idLabel.getStyleClass().add("sv-class-cell-id");
			HBox box = new HBox(5, idLabel);
			box.setAlignment(Pos.CENTER_LEFT);
			view.setBottom(box);
		}

		if (this.isComponent) {
			// draw as a component
			Label com = new Label(" ");
			com.setPadding(new Insets(0));
			com.setMinWidth(15);
			com.setMinHeight(15);
			com.setBackground(new Background(new BackgroundFill(Color.DARKCYAN, null, null)));
			com.setTextFill(Color.WHITE);
			VBox box = new VBox(com);
			box.setAlignment(Pos.CENTER);
			box.setTranslateX(-5);
			box.setTranslateY(10);
			box.setManaged(false);
			view.setLeft(box);
		} else {
			view.setLeft(null);
		}

		if (this.aClass != null) {
			HoverCards.install(view, this.aClass, null);
		}

		this.visible.addListener((s, o, n) -> {
			view.setVisible(n.booleanValue());
		});

		Runnable refreshAppearance = () -> {
			view.getStyleClass().removeAll(SELECTED_STYLE_CLASS, FOCUS_STYLE_CLASS, NEIGHBOR_STYLE_CLASS,
					NEIGHBOR_PLAIN_STYLE_CLASS);
			boolean marked = true;
			if (this.focused) {
				view.setStyle(null);
				view.getStyleClass().add(FOCUS_STYLE_CLASS);
			} else if (this.highlightColor != null) {
				view.setStyle(this.inlineLook(this.highlightColor));
			} else if (this.selected.get() && this.selectedColor != null) {
				view.setStyle(this.inlineLook(this.selectedColor));
			} else {
				view.setStyle(null);
				if (this.selected.get()) {
					view.getStyleClass().add(SELECTED_STYLE_CLASS);
				} else {
					marked = false;
				}
			}
			if (this.neighbor) {
				view.getStyleClass().add(NEIGHBOR_STYLE_CLASS);
				if (!marked) {
					view.getStyleClass().add(NEIGHBOR_PLAIN_STYLE_CLASS);
				}
			}
		};
		this.selected.addListener((s, o, n) -> refreshAppearance.run());
		refreshAppearance.run();
		this.appearanceRefresh = refreshAppearance;

		view.setVisible(this.visible.get());

		view.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
			if (this.onClick != null) {
				this.onClick.run();
			}
		});
		view.setOnContextMenuRequested(e -> {
			if (this.onContextMenuRequested != null) {
				this.onContextMenuRequested.accept(e);
			}
		});

		return view;
	}

	/** The icon of a leaf, composite or raw component, drawn by the stylesheet's {@code .sv-class-cell-icon}. */
	private StackPane icon() {
		SVGPath path = new SVGPath();
		path.setContent(this.composite ? COMPOSITE_ICON : this.raw ? RAW_ICON : LEAF_ICON);
		path.getStyleClass().add("sv-class-cell-icon");
		StackPane box = new StackPane(path);
		box.setMinSize(ICON_SIZE, ICON_SIZE);
		box.setPrefSize(ICON_SIZE, ICON_SIZE);
		box.setMaxSize(ICON_SIZE, ICON_SIZE);
		return box;
	}

	/**
	 * A colored border and a light tint of the same color, as an inline style: it beats the stylesheet, and
	 * clearing it hands the cell back to the stylesheet. A Border/Background set from code, even set back to
	 * null, wipes the stylesheet's look, and it comes back only when the cell's style classes change: a cell
	 * without a highlight would stay bare after a click. The border is dashed for a neighbor.
	 */
	private String inlineLook(final Color color) {
		return "-fx-background-color: " + css(color.deriveColor(0, 1, 1, 0.16))
				+ "; -fx-border-color: " + css(color)
				+ "; -fx-border-style: " + (this.neighbor ? "segments(5, 3)" : "solid") + "; -fx-border-width: 2.5;"
				+ " -fx-background-radius: 11; -fx-border-radius: 11;";
	}

	public void setIsComponent(final boolean isComponent) {
		this.isComponent = isComponent;
	}

	/**
	 * Draws the cell with the composite component's icon instead of the leaf's. Applies to the graphic built
	 * afterwards.
	 *
	 * @param composite whether the cell stands for a composite component
	 */
	public void setComposite(final boolean composite) {
		this.composite = composite;
	}

	/**
	 * Draws the cell with the raw component's icon instead of the leaf's, unless it is also drawn as a composite.
	 * Applies to the graphic built afterwards.
	 *
	 * @param raw whether the cell stands for a component the container does not manage
	 */
	public void setRaw(final boolean raw) {
		this.raw = raw;
	}

	public void onClick(final Runnable onClick) {
		this.onClick = onClick;
	}

	public void onContextMenuRequested(final Consumer<ContextMenuEvent> handler) {
		this.onContextMenuRequested = handler;
	}

	/**
	 * Sets or clears (null) the colored-border override - see {@link
	 * #highlightColor}. Only a value set before {@link #getGraphic} has run
	 * is read when the graphic is built; afterwards the graphic is refreshed.
	 *
	 * @param color the override color, {@code null} to clear it
	 */
	public void setHighlightColor(final Color color) {
		this.highlightColor = color;
		if (this.appearanceRefresh != null) {
			this.appearanceRefresh.run();
		}
	}

	/**
	 * Sets or clears (null) the color the selected look takes, instead of the stylesheet's.
	 *
	 * @param color the selected look's color, {@code null} for the stylesheet's
	 */
	public void setSelectedColor(final Color color) {
		this.selectedColor = color;
		if (this.appearanceRefresh != null) {
			this.appearanceRefresh.run();
		}
	}

	/** Sets or clears the focus look - see {@link #focused}. */
	public void setFocused(final boolean focused) {
		this.focused = focused;
		if (this.appearanceRefresh != null) {
			this.appearanceRefresh.run();
		}
	}

	/**
	 * Marks the cell as a neighbor of what the view is scoped to: its border is dashed, whatever else it shows.
	 *
	 * @param neighbor whether the cell is a neighbor
	 */
	public void setNeighbor(final boolean neighbor) {
		this.neighbor = neighbor;
		if (this.appearanceRefresh != null) {
			this.appearanceRefresh.run();
		}
	}

	/** @return a color as CSS, such as {@code rgba(13, 148, 136, 0.160)} */
	private static String css(final Color color) {
		return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", Math.round(color.getRed() * 255),
				Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255), color.getOpacity());
	}
}
