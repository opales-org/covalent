/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Highlighting;
import org.opales.covalent.supervision.componentview.internal.HoverCards;
import org.opales.covalent.supervision.componentview.internal.Tooltips;
import org.opales.covalent.supervision.componentview.internal.diagram.graph.Graph;

import javafx.beans.property.BooleanProperty;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * JavaFX cell dedicated to Covalent components, with explicit component-model
 * ports rendered around the component card.
 */
public class CovalentComponentCell extends AbstractCell {

	private Graph graph;

	private static final double GRAPHIC_WIDTH = 520;

	private static final double GRAPHIC_HEIGHT = 300;

	private static final double CARD_X = 92;

	/** Where the stereotype's zone starts, from the card's left edge: just past the icon's separator. */
	private static final double STEREOTYPE_LEFT = 62;

	/** The room kept on each side of the stereotype, from the separator and from the status pill. */
	private static final double STEREOTYPE_GAP = 6;

	private static final double CARD_Y = 44;

	private static final double CARD_WIDTH = 336;

	private static final double CARD_HEIGHT = 214;

	private static final double PORT_STEM = 38;

	private static final double PORT_SLOT_LONG_SIDE = 42;

	private static final double PORT_SLOT_SHORT_SIDE = 14;

	private static final double PORT_SLOT_RADIUS = 9;

	private static final double PORT_PAD_STROKE_WIDTH = 0.9;

	public static final double SERVICE_RECEPTACLE_RADIUS = 15;

	/** A provided service's ball. */
	public static final double SERVICE_BALL_RADIUS = 15;

	/** How long the landing flash stays fully lit before fading - see {@link #flash()}. */
	private static final Duration FLASH_HOLD = Duration.seconds(1);

	/**
	 * An event's notched arrow, pointing along x from its center: its back corners, its shoulders,
	 * its tip and its notch, and its half height.
	 */
	public static final double EVENT_BACK = 16;
	public static final double EVENT_SHOULDER = 10;
	public static final double EVENT_TIP = 24;
	public static final double EVENT_NOTCH = 2;
	public static final double EVENT_HALF_HEIGHT = 16;

	private static final double PARAMETER_STEM = 28;

	public static final double PARAMETER_SHAPE_WIDTH = 44;

	public static final double PARAMETER_SHAPE_HEIGHT = 9;

	public static final double PARAMETER_BRACKET_WIDTH = 52;

	public static final double PARAMETER_BRACKET_HEIGHT = 11;

	public static final double SOCKET_BAND = 7;

	public static final double SOCKET_BAND_BORDER = 1.3;

	public static final double BRACKET_BAND = 8;

	/**
	 * Nav-badge color: a fixed blue, outside the preference palette, which only
	 * styles the component cards themselves.
	 */
	private static final Color NAV_BADGE_COLOR = Color.web("#2563eb");

	/** Nav-badge halo/ring/dot radii - see {@link #navBadge}. */
	private static final double NAV_BADGE_HALO_RADIUS = 9;

	private static final double NAV_BADGE_RING_RADIUS = 6;

	private static final double NAV_BADGE_DOT_RADIUS = 2.5;

	/** Invisible, larger click target behind the badge - see {@link #navBadge}'s javadoc. */
	private static final double NAV_BADGE_HIT_RADIUS = 14;

	/** Hover feedback of a nav badge: it grows, and its halo and glow brighten. */
	private static final Duration NAV_BADGE_HOVER_DURATION = Duration.millis(140);

	private static final double NAV_BADGE_HOVER_SCALE = 1.45;

	private static final double NAV_BADGE_HALO_OPACITY = 0.18;

	private static final double NAV_BADGE_HOVER_HALO_OPACITY = 0.45;

	private static final double NAV_BADGE_GLOW_RADIUS = 10;

	private static final double NAV_BADGE_HOVER_GLOW_RADIUS = 16;

	/**
	 * Springy ease-out, the cubic Bezier (0.34, 1.56, 0.64, 1): overshoots
	 * slightly past its target before settling, so a hovered badge "pops".
	 * {@link Interpolator#SPLINE} cannot express it, since it requires
	 * control points within the unit square.
	 */
	private static final Interpolator NAV_BADGE_SPRING = new Interpolator() {
		@Override
		protected double curve(final double t) {
			double low = 0;
			double high = 1;
			double s = t;
			for (int i = 0; i < 20; i++) {
				s = (low + high) / 2;
				if (bezier(s, 0.34, 0.64) < t) {
					low = s;
				} else {
					high = s;
				}
			}
			return bezier(s, 1.56, 1);
		}

		private double bezier(final double s, final double p1, final double p2) {
			double u = 1 - s;
			return 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s;
		}
	};

	// Every port/wire color comes from PortPalette, shared with AnchoredPortEdge
	// so cards and wires always agree.

	private final String componentType;

	private final String componentClassName;

	private final Map<String, ComponentPort> portsByKey = new LinkedHashMap<>();

	private Pane chromeLayer;

	private Pane portLayer;

	private Pane view;

	private Rectangle selectionHighlight;

	/** The card's own outer rounded rect, promoted from a {@link #buildCard()} local so {@link #flash()} can repaint it. */
	private Rectangle outerCard;

	private Timeline flashTransition;

	/** The card body's inner panel, repainted with {@link #outerCard} on hover. */
	private Rectangle innerCard;

	/** Whether the pointer is over this card - see {@link #setHovered}. */
	private boolean hovered;

	/** Shown until a real lifecycle status is known - see {@link #setStatus}. */
	private String statusLabel = CellsTexts.get("status.initial");

	/** {@code null} until a real lifecycle status is known: {@code PortPalette.STATUS_ACTIVATED} meanwhile. */
	private Color statusColor;

	/** What the component stands for, written in the header between the icon and the status pill; {@code null} for none. */
	private String stereotype;

	/** Told in a tooltip on the card's name: the implementation behind the component type; {@code null} for none. */
	private String implementationTip;

	private boolean component = false;

	/**
	 * Whether this card is a composite (an assembly containing other
	 * components) rather than a leaf - set once by {@code
	 * ComponentDiagramViewImpl} from {@code
	 * ComponentDiagramTopology.getCompositeTypes()}, before the first
	 * {@link #getGraphic(Graph)}. Distinct from {@link #component}, which is
	 * unconditionally true for every card on this canvas (every card is a
	 * Covalent {@code @Component}) and says nothing about whether it
	 * happens to be an assembly of others. Chooses which center icon
	 * {@link #renderCenter()} draws.
	 */
	private boolean composite = false;

	/** Whether the container does not manage this component. */
	private boolean raw = false;

	private Consumer<MouseEvent> onClickConsumer;

	private Consumer<ContextMenuEvent> onContextMenuConsumer;

	private Consumer<ComponentPort> onPortClickConsumer;

	private BiConsumer<ComponentPort, ContextMenuEvent> onPortContextMenuConsumer;

	private Consumer<ComponentPort> onPortNavigateConsumer;

	public CovalentComponentCell(final String componentType, final String componentClassName) {
		this.componentType = componentType;
		this.componentClassName = componentClassName;
	}

	public String getComponentClass() {
		return this.componentClassName;
	}

	public double getCardLeftX() {
		return CARD_X;
	}

	public double getCardRightX() {
		return CARD_X + CARD_WIDTH;
	}

	public double getCardTopY() {
		return CARD_Y;
	}

	public double getCardBottomY() {
		return CARD_Y + CARD_HEIGHT;
	}

	public double getCardCenterX() {
		return CARD_X + CARD_WIDTH / 2;
	}

	public double getCardCenterY() {
		return CARD_Y + CARD_HEIGHT / 2;
	}

	public void setIsComponent(final boolean isComponent) {
		this.component = isComponent;
		if (isComponent) {
			this.getOrCreatePort(ComponentPortKind.COMPONENT_TYPE, ComponentPortRole.COMPONENT_TYPE, CellsTexts.get("port.componentType"),
					ComponentPortSide.TOP);
		}
	}

	public void setIsComposite(final boolean isComposite) {
		this.composite = isComposite;
	}

	/** @param isRaw whether the container does not manage this component: it then has no core in its icon */
	public void setIsRaw(final boolean isRaw) {
		this.raw = isRaw;
	}

	public ComponentPort getOrCreatePort(final ComponentPortKind kind, final ComponentPortRole role,
			final String label, final ComponentPortSide side) {
		ComponentPort port = new ComponentPort(this, kind, role, label, this.fixedSide(kind, side));
		ComponentPort existing = this.portsByKey.putIfAbsent(port.key(), port);
		ComponentPort result = existing == null ? port : existing;
		this.refreshPorts();
		return result;
	}

	public ComponentPort getPort(final ComponentPortKind kind, final ComponentPortRole role, final String label) {
		return this.portsByKey.get(ComponentPort.key(kind, role, label));
	}

	public void refreshPorts() {
		if (this.portLayer == null) {
			return;
		}
		this.portLayer.getChildren().clear();

		Map<ComponentPortSide, List<ComponentPort>> portsBySide = new EnumMap<>(ComponentPortSide.class);
		for (ComponentPortSide side : ComponentPortSide.values()) {
			portsBySide.put(side, new ArrayList<>());
		}
		for (ComponentPort port : this.portsByKey.values()) {
			portsBySide.get(this.fixedSide(port.getKind(), port.getSide())).add(port);
		}

		for (ComponentPortSide side : ComponentPortSide.values()) {
			this.renderSidePorts(side, portsBySide.get(side));
		}
	}

	/**
	 * Clears and rebuilds the card body/header/title chrome from {@code
	 * PortPalette}'s current values - the body/border/divider/badge/text
	 * colors {@link #buildCard()}, {@link #renderHeader()} and {@link
	 * #renderCenter()} draw once into {@link #chromeLayer}, separate from
	 * {@link #portLayer} so a palette change can rebuild either
	 * independently.
	 */
	public void refreshChrome() {
		if (this.chromeLayer == null) {
			return;
		}
		this.chromeLayer.getChildren().clear();
		this.buildCard();
	}

	/** Re-reads {@code PortPalette.SELECTION} onto the built selection outline. */
	public void refreshSelectionColor() {
		if (this.selectionHighlight != null) {
			this.selectionHighlight.setStroke(PortPalette.SELECTION);
		}
	}

	/**
	 * Repaints everything this cell owns from {@code PortPalette}'s current
	 * values - called by {@code ComponentDiagramViewImpl.setPortColor(...)}
	 * for every live cell after a Preferences-panel color change. Covers the
	 * ports/stems/shapes/labels ({@link #refreshPorts()}), the card chrome
	 * ({@link #refreshChrome()}) and the selection outline ({@link
	 * #refreshSelectionColor()}) - everything except the three live
	 * activity-flash colors (SERVICE_ACTIVITY/FAILURE, EVENT_ACTIVITY),
	 * which by nature only render while a port is actually mid-activity and
	 * so pick up a changed color the next time that happens on its own,
	 * never from a forced repaint.
	 */
	public void refreshPalette() {
		this.refreshPorts();
		this.refreshChrome();
		this.refreshSelectionColor();
	}

	@Override
	public Region getGraphic(final Graph graph) {
		this.graph = graph;
		this.view = new Pane();
		this.view.setPrefSize(GRAPHIC_WIDTH, GRAPHIC_HEIGHT);
		this.view.setMinSize(GRAPHIC_WIDTH, GRAPHIC_HEIGHT);
		this.view.setMaxSize(GRAPHIC_WIDTH, GRAPHIC_HEIGHT);
		// Only the card and its ports answer the pointer, not the whole box
		// around them, so the wires passing through that box stay clickable.
		this.view.setPickOnBounds(false);

		this.chromeLayer = new Pane();
		this.chromeLayer.setPickOnBounds(false);
		this.view.getChildren().add(this.chromeLayer);
		this.refreshChrome();

		this.portLayer = new Pane();
		this.portLayer.setPickOnBounds(false);
		this.view.getChildren().add(this.portLayer);
		this.refreshPorts();

		this.selectionHighlight = this.roundedRectangle(CARD_X - 5, CARD_Y - 5, CARD_WIDTH + 10, CARD_HEIGHT + 10, 32,
				Color.TRANSPARENT, PortPalette.SELECTION, 3.0);
		this.selectionHighlight.setManaged(false);
		this.selectionHighlight.setMouseTransparent(true);
		this.selectionHighlight.setVisible(false);
		this.view.getChildren().add(this.selectionHighlight);

		this.visible.addListener((s, o, n) -> this.view.setVisible(n.booleanValue()));
		this.selected.addListener((s, o, n) -> this.updateSelectedState(n.booleanValue()));
		this.view.setVisible(this.visible.get());
		this.updateSelectedState(this.selected.get());

		this.view.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> this.setHovered(true));
		this.view.addEventHandler(MouseEvent.MOUSE_EXITED, e -> this.setHovered(false));
		this.view.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
			// A real drag (move) just finished: JavaFX still synthesizes a MOUSE_CLICKED
			// after the release regardless of the movement in between, but per the
			// unified ergonomic contract a drag is a distinct gesture from a click and
			// must not also trigger select.
			if (this.graph != null && this.graph.getNodeGestures().wasLastGestureADrag()) {
				return;
			}
			if (this.onClickConsumer != null) {
				this.onClickConsumer.accept(e);
			}
		});
		this.view.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
			if (this.onContextMenuConsumer != null) {
				this.onContextMenuConsumer.accept(e);
			}
		});

		return this.view;
	}

	public void onClick(final Consumer<MouseEvent> onClickConsumer) {
		this.onClickConsumer = onClickConsumer;
	}

	/**
	 * The card's right-click (or platform context-menu key) gesture, a
	 * complement to the toolbox panel.
	 * The consumer is responsible for showing and for consuming the event
	 * (so a card's own menu takes priority over the canvas background's).
	 */
	public void onContextMenuRequested(final Consumer<ContextMenuEvent> onContextMenuConsumer) {
		this.onContextMenuConsumer = onContextMenuConsumer;
	}

	public void onPortClick(final Consumer<ComponentPort> onPortClickConsumer) {
		this.onPortClickConsumer = onPortClickConsumer;
	}

	/**
	 * A port's own right-click (or platform context-menu key) gesture, a
	 * complement to the pad's click-to-highlight and the nav badge.
	 */
	public void onPortContextMenuRequested(final BiConsumer<ComponentPort, ContextMenuEvent> onPortContextMenuConsumer) {
		this.onPortContextMenuConsumer = onPortContextMenuConsumer;
	}

	/**
	 * Registers the handler for a click on a navigable port's nav badge (see
	 * {@link #navBadge}) - distinct from {@link #onPortClick}, which fires
	 * from the pad instead and drives click-to-highlight, not navigation.
	 */
	public void onPortNavigate(final Consumer<ComponentPort> onPortNavigateConsumer) {
		this.onPortNavigateConsumer = onPortNavigateConsumer;
	}

	/**
	 * Paints this card's body in {@code PortPalette.HIGHLIGHT}, holds it for
	 * {@link #FLASH_HOLD} so the eye has time to find the card, then fades it
	 * back to {@code PortPalette.BODY} over {@code Highlighting.DURATION} - the
	 * landing-card flash after a cross-composite port-navigation jump.
	 */
	public void flash() {
		if (this.outerCard == null) {
			return;
		}
		if (this.flashTransition != null) {
			this.flashTransition.stop();
		}
		this.outerCard.setFill(PortPalette.HIGHLIGHT);
		this.flashTransition = new Timeline(
				new KeyFrame(Duration.ZERO, new KeyValue(this.outerCard.fillProperty(), PortPalette.HIGHLIGHT)),
				new KeyFrame(FLASH_HOLD, new KeyValue(this.outerCard.fillProperty(), PortPalette.HIGHLIGHT)),
				new KeyFrame(FLASH_HOLD.add(Highlighting.DURATION),
						new KeyValue(this.outerCard.fillProperty(), PortPalette.BODY, Interpolator.EASE_OUT)));
		this.flashTransition.setOnFinished(e -> this.applyHoverFill());
		this.flashTransition.playFromStart();
	}

	private ComponentPortSide fixedSide(final ComponentPortKind kind, final ComponentPortSide side) {
		if (kind == ComponentPortKind.COMPONENT_TYPE) {
			return ComponentPortSide.TOP;
		}
		if (kind == ComponentPortKind.PARAMETER) {
			return ComponentPortSide.BOTTOM;
		}
		return side;
	}

	private void buildCard() {
		this.outerCard = this.roundedRectangle(CARD_X, CARD_Y, CARD_WIDTH, CARD_HEIGHT, 28, PortPalette.BODY, PortPalette.GRAPHITE, 1.8);
		Rectangle inner = this.roundedRectangle(CARD_X + 10, CARD_Y + 10, CARD_WIDTH - 20, CARD_HEIGHT - 20, 22, PortPalette.BODY,
				PortPalette.CARD_BORDER_SOFT, 1.0);
		this.innerCard = inner;
		this.applyHoverFill();
		Rectangle header = this.roundedRectangle(CARD_X + 12, CARD_Y + 12, CARD_WIDTH - 24, 44, 18, PortPalette.HEADER,
				Color.TRANSPARENT, 0);
		Line headerBottom = new Line(CARD_X + 12, CARD_Y + 56, CARD_X + CARD_WIDTH - 12, CARD_Y + 56);
		headerBottom.setStroke(PortPalette.HEADER_DIVIDER);

		this.chromeLayer.getChildren().addAll(this.outerCard, inner, header, headerBottom);
		this.renderHeader();
		this.renderCenter();
	}

	private void renderHeader() {
		Group icon = this.cubeIcon(CARD_X + 38, CARD_Y + 31, 8, PortPalette.GRAPHITE);
		Line separator = new Line(CARD_X + 60, CARD_Y + 19, CARD_X + 60, CARD_Y + 42);
		separator.setStroke(PortPalette.ICON_DIVIDER);
		separator.setStrokeWidth(1.5);
		this.chromeLayer.getChildren().addAll(icon, separator);

		if (this.component) {
			this.chromeLayer.getChildren().add(this.statusBadge());
			if (this.stereotype != null && !this.stereotype.isEmpty()) {
				this.chromeLayer.getChildren().add(this.stereotypeLabel());
			}
		}
	}

	/**
	 * The lifecycle status pill, right-aligned in the header: a dot and a
	 * label in the status color (the label a touch closer to
	 * {@code PortPalette.GRAPHITE}, for contrast), on a tint of it over
	 * {@code PortPalette.BODY}, sized to the label.
	 */
	private Group statusBadge() {
		Font font = statusFont();
		Text measure = new Text(this.statusLabel);
		measure.setFont(font);
		double width = this.statusBadgeWidth();
		double left = this.statusBadgeLeft();
		Color color = this.statusColor == null ? PortPalette.STATUS_ACTIVATED : this.statusColor;
		Color tint = PortPalette.BODY.interpolate(color, 0.12);
		Color border = PortPalette.BODY.interpolate(color, 0.4);
		Color ink = color.interpolate(PortPalette.GRAPHITE, 0.25);
		Rectangle pill = this.roundedRectangle(left, CARD_Y + 18, width, 26, 18, tint, border, 1.0);
		Circle dot = new Circle(left + 14, CARD_Y + 31, 5, color);
		Label text = new Label(this.statusLabel);
		text.setTextFill(ink);
		text.setFont(font);
		text.setLayoutX(left + 26);
		text.setLayoutY(CARD_Y + 31 - measure.getLayoutBounds().getHeight() / 2);
		return new Group(pill, dot, text);
	}

	/** The status pill's font. */
	private static Font statusFont() {
		return Font.font("System", FontWeight.BOLD, 9);
	}

	/** The stereotype's font: larger than the status pill's, which it must outweigh. */
	private static Font stereotypeFont() {
		return Font.font("System", FontWeight.MEDIUM, 22);
	}

	/** The status pill's width: its dot and spacing, plus the label in {@link #statusFont()}. */
	private double statusBadgeWidth() {
		return 32 + Math.ceil(this.textWidth(this.statusLabel, statusFont()));
	}

	/** The status pill's left edge: its right edge sits 18 from the card's, whatever its width. */
	private double statusBadgeLeft() {
		return CARD_X + CARD_WIDTH - 18 - this.statusBadgeWidth();
	}

	/**
	 * The component's stereotype between guillemets, centered between the icon's separator and the
	 * status pill, with at least 6 on each side of it. Cut with an ellipsis when it does not fit,
	 * and then fully told by a tooltip.
	 */
	private Label stereotypeLabel() {
		double x = CARD_X + STEREOTYPE_LEFT + STEREOTYPE_GAP;
		double available = this.statusBadgeLeft() - STEREOTYPE_GAP - x;
		String text = "\u00ab" + this.stereotype + "\u00bb";
		Label label = new Label(text);
		label.setFont(stereotypeFont());
		Color tint = PortPalette.GRAPHITE.interpolate(PortPalette.HEADER, 0.58);
		label.setTextFill(tint);
		// The Supervisor's stylesheet colors every label; only an inline style outranks it.
		label.setStyle(String.format("-fx-text-fill: #%02x%02x%02x;", Math.round(tint.getRed() * 255),
				Math.round(tint.getGreen() * 255), Math.round(tint.getBlue() * 255)));
		label.setAlignment(Pos.CENTER);
		label.setLayoutX(x);
		label.setLayoutY(CARD_Y + 16);
		label.setPrefSize(available, 30);
		label.setMaxWidth(available);
		if (this.textWidth(text, stereotypeFont()) > available) {
			Tooltips.install(label, this.stereotype);
		}
		return label;
	}

	/**
	 * Shows what this component stands for in its header - for a component card only, a type card
	 * has none.
	 *
	 * @param text the stereotype, {@code null} or empty for none
	 */
	public void setStereotype(final String text) {
		if (Objects.equals(text, this.stereotype)) {
			return;
		}
		this.stereotype = text;
		this.refreshChrome();
	}

	/**
	 * Tells, in the hover card on the card's name, which implementation stands behind the component type.
	 *
	 * @param text the tooltip, {@code null} or empty for none
	 */
	public void setImplementationTip(final String text) {
		if (Objects.equals(text, this.implementationTip)) {
			return;
		}
		this.implementationTip = text;
		this.refreshChrome();
	}

	/**
	 * Hover feedback on a composite card, which a double-click enters: its
	 * body takes {@code PortPalette.HOVER} while the pointer is over it. A
	 * leaf card, which cannot be entered, stays unchanged.
	 */
	private void setHovered(final boolean hovered) {
		this.hovered = hovered;
		this.applyHoverFill();
	}

	private void applyHoverFill() {
		if (!this.composite || this.outerCard == null || this.innerCard == null) {
			return;
		}
		Color fill = this.hovered ? PortPalette.HOVER : PortPalette.BODY;
		if (this.flashTransition == null || this.flashTransition.getStatus() != Animation.Status.RUNNING) {
			this.outerCard.setFill(fill);
		}
		this.innerCard.setFill(fill);
	}

	/**
	 * Shows a lifecycle status in this card's header pill - for a
	 * component card only, a type card has none.
	 *
	 * @param label the status text
	 * @param color the status color - the dot, the text and the pill's tint
	 */
	public void setStatus(final String label, final Color color) {
		if (label.equals(this.statusLabel) && color.equals(this.statusColor)) {
			return;
		}
		this.statusLabel = label;
		this.statusColor = color;
		this.refreshChrome();
	}

	private void renderCenter() {
		Font titleFont = Font.font("System", FontWeight.BOLD, 28);
		List<String> titleLines = this.wrapComponentName(this.componentType, titleFont, CARD_WIDTH - 100);
		// The icon and the name stand together in the middle of the body: a name on two lines
		// gets a slightly smaller, tighter title, and the pair moves up to leave it room.
		boolean twoLines = titleLines.size() > 1;
		if (twoLines) {
			titleFont = Font.font("System", FontWeight.BOLD, 25);
		}
		double iconY = CARD_Y + (twoLines ? 91 : 102);

		Group icon = this.composite ? this.compositeTypeIcon(CARD_X + CARD_WIDTH / 2, iconY, 22)
				: this.raw ? this.rawObjectIcon(CARD_X + CARD_WIDTH / 2, iconY, 22)
						: this.hexagonCubeIcon(CARD_X + CARD_WIDTH / 2, iconY, 22);

		Label title = new Label(String.join("\n", titleLines));
		title.setFont(titleFont);
		if (twoLines) {
			title.setLineSpacing(-5);
		}
		title.setTextFill(PortPalette.GRAPHITE);
		title.setAlignment(Pos.CENTER);
		title.setTextAlignment(TextAlignment.CENTER);
		title.setLayoutX(CARD_X + 50);
		title.setLayoutY(CARD_Y + (twoLines ? 122 : 141));
		title.setPrefWidth(CARD_WIDTH - 100);
		HoverCards.install(title, this.componentClassName, this.implementationTip);

		this.chromeLayer.getChildren().addAll(icon, title);
	}

	/**
	 * Measures {@code text} in {@code font} the way it will actually be laid out, via a
	 * throwaway {@link Text} node - the only reliable way to get a pixel width out of JavaFX
	 * without rendering anything.
	 */
	private double textWidth(final String text, final Font font) {
		Text measure = new Text(text);
		measure.setFont(font);
		return measure.getLayoutBounds().getWidth();
	}

	/**
	 * Wraps a component name onto at most two lines instead of letting it get truncated.
	 * If {@code name} fits in {@code maxWidth} it
	 * comes back unchanged (one line). Otherwise every CamelCase hump (a lowercase letter or
	 * digit immediately followed by an uppercase one - component names tend to be CamelCase)
	 * and every plain separator (space, dash, underscore) is a split candidate; the candidate
	 * chosen is whichever leaves the WIDER of the two resulting lines as narrow as possible,
	 * measured in actual pixels via {@link #textWidth(String, Font)}, not character count. A
	 * name with no such boundary at all (one long run of same-case letters) falls back to the
	 * single best character split by the same pixel-balancing rule, so it still gets exactly
	 * one line break rather than being truncated.
	 */
	private List<String> wrapComponentName(final String name, final Font font, final double maxWidth) {
		if (this.textWidth(name, font) <= maxWidth) {
			return List.of(name);
		}

		int bestSplit = -1;
		double bestScore = Double.MAX_VALUE;
		for (int i = 1; i < name.length(); i++) {
			char before = name.charAt(i - 1);
			boolean isSeparator = before == ' ' || before == '-' || before == '_';
			boolean isCamelHump = Character.isUpperCase(name.charAt(i))
					&& (Character.isLowerCase(before) || Character.isDigit(before));
			if (!isSeparator && !isCamelHump) {
				continue;
			}
			String firstLine = isSeparator ? name.substring(0, i - 1) : name.substring(0, i);
			String secondLine = name.substring(i);
			double score = Math.max(this.textWidth(firstLine, font), this.textWidth(secondLine, font));
			if (score < bestScore) {
				bestScore = score;
				bestSplit = i;
			}
		}

		if (bestSplit < 0) {
			for (int i = 1; i < name.length(); i++) {
				double score = Math.max(this.textWidth(name.substring(0, i), font), this.textWidth(name.substring(i), font));
				if (score < bestScore) {
					bestScore = score;
					bestSplit = i;
				}
			}
			return List.of(name.substring(0, bestSplit), name.substring(bestSplit));
		}

		char beforeBest = name.charAt(bestSplit - 1);
		boolean bestIsSeparator = beforeBest == ' ' || beforeBest == '-' || beforeBest == '_';
		String firstLine = bestIsSeparator ? name.substring(0, bestSplit - 1) : name.substring(0, bestSplit);
		String secondLine = name.substring(bestSplit);
		return List.of(firstLine, secondLine);
	}

	private void renderSidePorts(final ComponentPortSide side, final List<ComponentPort> ports) {
		if (ports.isEmpty()) {
			return;
		}

		if (side == ComponentPortSide.BOTTOM) {
			this.renderParameterPorts(ports);
			return;
		}

		if (side == ComponentPortSide.TOP) {
			this.renderTopPorts(ports);
			return;
		}

		double gap = CARD_HEIGHT / (ports.size() + 1);
		for (int i = 0; i < ports.size(); i++) {
			ComponentPort port = ports.get(i);
			double y = CARD_Y + gap * (i + 1);
			if (side == ComponentPortSide.LEFT) {
				this.renderSidePort(port, CARD_X, y, side);
			} else {
				this.renderSidePort(port, CARD_X + CARD_WIDTH, y, side);
			}
		}
	}

	private void renderTopPorts(final List<ComponentPort> ports) {
		double gap = CARD_WIDTH / (ports.size() + 1);
		for (int i = 0; i < ports.size(); i++) {
			ComponentPort port = ports.get(i);
			double x = CARD_X + gap * (i + 1);
			double padX = x - PORT_SLOT_LONG_SIDE / 2;
			double padY = CARD_Y - PORT_SLOT_SHORT_SIDE / 2;
			Rectangle slot = this.roundedRectangle(padX, padY, PORT_SLOT_LONG_SIDE, PORT_SLOT_SHORT_SIDE,
					PORT_SLOT_RADIUS, PortPalette.SLOT_IDLE, PortPalette.GRAPHITE, PORT_PAD_STROKE_WIDTH);
			this.configurePortSlot(slot, port);
			this.portLayer.getChildren().add(slot);
			Line stem = new Line(x, padY - 24, x, padY);
			stem.setStroke(PortPalette.GRAPHITE);
			stem.setStrokeWidth(1.6);
			Circle type = new Circle(x, padY - 34, 11, PortPalette.COMPONENT_TYPE);
			type.setStroke(PortPalette.GRAPHITE);
			type.setStrokeWidth(1.4);
			// No name over it: every component has this port, and its name would only repeat that.
			this.portLayer.getChildren().addAll(stem, type);
			port.setAnchor(x, padY - 34);
		}
	}

	private void renderSidePort(final ComponentPort port, final double cardEdgeX, final double y,
			final ComponentPortSide side) {
		double direction = side == ComponentPortSide.LEFT ? -1 : 1;
		double padX = cardEdgeX - PORT_SLOT_SHORT_SIDE / 2;
		double shapeX = cardEdgeX + direction * (PORT_STEM + 12);
		double stemStart = cardEdgeX + direction * (PORT_SLOT_SHORT_SIDE / 2);
		double stemEnd = this.portStemEndX(port, shapeX, direction);

		Rectangle slot = this.roundedRectangle(padX, y - PORT_SLOT_LONG_SIDE / 2, PORT_SLOT_SHORT_SIDE,
				PORT_SLOT_LONG_SIDE, PORT_SLOT_RADIUS, PortPalette.SLOT_IDLE, PortPalette.GRAPHITE, PORT_PAD_STROKE_WIDTH);
		double anchorX = this.portAnchorX(port, shapeX, direction);
		// The stem is the wire's sheath, from the pad to the port's shape: graphite, as wide as a
		// wire; the wire's own color only runs between the two ports' shapes.
		Line stem = new Line(stemStart, y, stemEnd, y);
		stem.setStroke(PortPalette.GRAPHITE);
		stem.setStrokeWidth(PortPalette.WIRE_WIDTH);
		// Under the pad, so the pad's contour stays whole where the stem meets it.
		this.portLayer.getChildren().addAll(stem, slot);
		Line bridge = null;
		if (port.getKind() == ComponentPortKind.SERVICE && port.getRole() == ComponentPortRole.USED
				&& !port.isNavigable()) {
			// The socket arc only covers its own half of the circle (the half
			// facing the card); its other, open half is where a real wire's
			// anchor point (anchorX, at that half's own rim - see
			// #portAnchorX) sits. This segment fills that open half so the
			// wire reads as one continuous line reaching the socket's rim,
			// but only while a real wire is actually there to continue:
			// hidden while the port is free, so a port with nothing plugged
			// into it shows no stray line past the socket's drawn curve. It is
			// part of the wire, so it takes the wire's color: the provided one.
			bridge = new Line(stemEnd, y, anchorX, y);
			bridge.setStroke(PortPalette.SERVICE_PROVIDED);
			bridge.setStrokeWidth(PortPalette.WIRE_WIDTH);
			bridge.visibleProperty().bind(port.connectedProperty());
			this.portLayer.getChildren().add(bridge);
		}
		this.configurePortSlot(slot, port, bridge == null ? new Line[] { stem } : new Line[] { stem, bridge });
		this.wirePortClick(slot, port);
		this.portLayer.getChildren().add(this.portShape(port, shapeX, y, side));
		if (port.isNavigable()) {
			this.portLayer.getChildren().add(this.navBadge(this.navBadgeAnchorX(port, anchorX, direction), y, port));
		}

		double labelX = side == ComponentPortSide.LEFT ? shapeX - 200 : shapeX + 30;
		Label label = this.portLabel(port.getLabel(), labelX, y - 11, 192, PortPalette.GRAPHITE);
		this.portLayer.getChildren().add(label);
		port.setAnchor(anchorX, y);
	}

	private void renderParameterPorts(final List<ComponentPort> ports) {
		// Same anchored-port language as every other side: a black-outlined slot
		// pad flush against the card edge, a short stem, then the port's own
		// shape. The shape depends on the role: OFFERED (the component providing
		// the parameter) draws a filled rounded bar, ACCESSED (the component
		// reading it) draws an open three-sided bracket, which uses a two-stroke
		// technique for the black contour since the shape itself has no fill.
		double gap = CARD_WIDTH / (ports.size() + 1);
		double edgeY = CARD_Y + CARD_HEIGHT;
		for (int i = 0; i < ports.size(); i++) {
			ComponentPort port = ports.get(i);
			double x = CARD_X + gap * (i + 1);
			double padX = x - PORT_SLOT_LONG_SIDE / 2;
			double padY = edgeY - PORT_SLOT_SHORT_SIDE / 2;
			Rectangle slot = this.roundedRectangle(padX, padY, PORT_SLOT_LONG_SIDE, PORT_SLOT_SHORT_SIDE,
					PORT_SLOT_RADIUS, PortPalette.SLOT_IDLE, PortPalette.GRAPHITE, PORT_PAD_STROKE_WIDTH);
			// The stem starts at the pad's outward face, not its center, so the
			// shape never overlaps the pad - same convention as side ports:
			// stemStart = cardEdge + direction*(PORT_SLOT_SHORT_SIDE/2).
			double stemStart = padY + PORT_SLOT_SHORT_SIDE;
			double shapeY = stemStart + PARAMETER_STEM;
			boolean accessed = port.getRole() == ComponentPortRole.ACCESSED;
			// Both shapes are centered on shapeY, the bracket just a touch taller than
			// the rectangle it nests around - not merely hung below shapeY.
			double half = (accessed ? PARAMETER_BRACKET_HEIGHT : PARAMETER_SHAPE_HEIGHT) / 2;
			double stemEnd = shapeY - half;
			double anchorY = shapeY + half;
			Color shapeColor = accessed ? PortPalette.PARAMETER_ACCESSED : PortPalette.PARAMETER;

			Line stem = new Line(x, stemStart, x, stemEnd);
			stem.setStroke(PortPalette.GRAPHITE);
			stem.setStrokeWidth(PortPalette.WIRE_WIDTH);
			// Under the pad, so the pad's contour stays whole where the stem meets it.
			this.portLayer.getChildren().addAll(stem, slot);

			Line bridge = null;
			if (accessed) {
				// Open three-sided bracket (left / top / right, open on the far side
				// toward the external wire), drawn with the same wider-black-band-under-
				// narrower-colored-band technique as the service USED crescent. It has no
				// fill either, so it needs the same bridge across its own span.
				bridge = new Line(x, stemEnd, x, anchorY);
				bridge.setStroke(PortPalette.PARAMETER);
				bridge.setStrokeWidth(PortPalette.WIRE_WIDTH);
				this.portLayer.getChildren().add(bridge);
				this.portLayer.getChildren().add(this.bracketShape(x, stemEnd, anchorY, PARAMETER_BRACKET_WIDTH / 2));
			} else {
				Rectangle bar = this.roundedRectangle(x - PARAMETER_SHAPE_WIDTH / 2, stemEnd, PARAMETER_SHAPE_WIDTH,
						PARAMETER_SHAPE_HEIGHT, PARAMETER_SHAPE_HEIGHT, PortPalette.PARAMETER, PortPalette.GRAPHITE, 1.2);
				this.portLayer.getChildren().add(bar);
			}
			this.configurePortSlot(slot, port, bridge == null ? new Line[] { stem } : new Line[] { stem, bridge });
			this.wirePortClick(slot, port);

			if (port.isNavigable()) {
				this.portLayer.getChildren().add(this.navBadge(x, anchorY, port));
			}

			Label label = this.portLabel(port.getLabel(), x - 90, anchorY + 6, 180, shapeColor);
			this.portLayer.getChildren().add(label);
			port.setAnchor(x, anchorY);
		}
	}

	private Group bracketShape(final double x, final double topY, final double bottomY, final double halfWidth) {
		Group bracket = new Group();
		Path outline = this.bracketPath(x, topY, bottomY, halfWidth);
		outline.setStroke(PortPalette.GRAPHITE);
		outline.setStrokeWidth(BRACKET_BAND + 2 * SOCKET_BAND_BORDER);
		outline.setStrokeLineCap(StrokeLineCap.ROUND);
		outline.setStrokeLineJoin(StrokeLineJoin.ROUND);
		Path fill = this.bracketPath(x, topY, bottomY, halfWidth);
		fill.setStroke(PortPalette.PARAMETER_ACCESSED);
		fill.setStrokeWidth(BRACKET_BAND);
		fill.setStrokeLineCap(StrokeLineCap.ROUND);
		fill.setStrokeLineJoin(StrokeLineJoin.ROUND);
		bracket.getChildren().addAll(outline, fill);
		return bracket;
	}

	private Path bracketPath(final double x, final double topY, final double bottomY, final double halfWidth) {
		Path path = new Path();
		path.setFill(null);
		path.getElements().addAll(new MoveTo(x - halfWidth, bottomY), new LineTo(x - halfWidth, topY),
				new LineTo(x + halfWidth, topY), new LineTo(x + halfWidth, bottomY));
		return path;
	}

	private Node portShape(final ComponentPort port, final double x, final double y, final ComponentPortSide side) {
		if (port.getKind() == ComponentPortKind.SERVICE && port.getRole() == ComponentPortRole.PROVIDED) {
			Circle circle = new Circle(x, y, SERVICE_BALL_RADIUS, PortPalette.SERVICE_PROVIDED);
			circle.setStroke(PortPalette.GRAPHITE);
			circle.setStrokeWidth(1.3);
			return circle;
		}
		if (port.getKind() == ComponentPortKind.SERVICE && port.getRole() == ComponentPortRole.USED) {
			double startAngle = side == ComponentPortSide.LEFT ? -90 : 90;
			// Two stacked arcs (wider graphite one behind a narrower colored one),
			// both round-capped: an Arc is stroke-only (no fill), so this is how it
			// gets the same "colored shape with a black contour" look the filled
			// shapes (Circle, Polygon) have for free from fill+stroke.
			Group crescent = new Group();
			Arc outline = new Arc(x, y, SERVICE_RECEPTACLE_RADIUS, SERVICE_RECEPTACLE_RADIUS, startAngle, 180);
			outline.setFill(null);
			outline.setStroke(PortPalette.GRAPHITE);
			outline.setStrokeWidth(SOCKET_BAND + 2 * SOCKET_BAND_BORDER);
			outline.setStrokeLineCap(StrokeLineCap.ROUND);
			outline.setType(ArcType.OPEN);
			Arc fill = new Arc(x, y, SERVICE_RECEPTACLE_RADIUS, SERVICE_RECEPTACLE_RADIUS, startAngle, 180);
			fill.setFill(null);
			fill.setStroke(PortPalette.SERVICE_USED);
			fill.setStrokeWidth(SOCKET_BAND);
			fill.setStrokeLineCap(StrokeLineCap.ROUND);
			fill.setType(ArcType.OPEN);
			crescent.getChildren().addAll(outline, fill);
			return crescent;
		}
		return this.eventShape(port, x, y, side);
	}

	private double portStemEndX(final ComponentPort port, final double shapeX, final double direction) {
		if (port.getKind() == ComponentPortKind.SERVICE) {
			return shapeX - direction * SERVICE_RECEPTACLE_RADIUS;
		}
		if (port.getKind() == ComponentPortKind.EVENT) {
			return this.eventStemEndX(port, shapeX, direction);
		}
		return shapeX - direction * 16;
	}

	private double portAnchorX(final ComponentPort port, final double shapeX, final double direction) {
		// The real connection anchor: where a routed wire actually terminates, right
		// at the port shape's own rim - no artificial lead-in stub beyond it, which
		// would show as a visible "tail" in the port's own color, poking out past
		// where the routed wire's corner begins. This value also feeds
		// port.setAnchor(...), which every AnchoredPortEdge binds to for its own line
		// endpoint (see PortAnchorCell#getXAnchor) - so it must stay flush with the
		// shape for every role. Any extra offset the nav badge alone needs belongs in
		// navBadgeAnchorX instead, never here, or the edge line inherits it too and
		// stops short of the shape it should be touching.
		if (port.getKind() == ComponentPortKind.SERVICE) {
			return shapeX + direction * SERVICE_RECEPTACLE_RADIUS;
		}
		if (port.getKind() == ComponentPortKind.EVENT) {
			return this.eventExternalConnectorStartX(port, shapeX, direction);
		}
		return shapeX;
	}

	/**
	 * The nav badge's own display position, derived from {@link #portAnchorX}.
	 * Like every other port, a service port keeps its badge in contact with
	 * its shape: outside the rim of the solid PROVIDED lollipop (the anchor
	 * plus the badge's own halo radius), and inside the hollow of the USED
	 * socket, tangent to the inner edge of its band, opposite the mouth. This
	 * offset is cosmetic to the badge only; it must never reach the edge/line
	 * endpoint (see {@link #portAnchorX}).
	 */
	private double navBadgeAnchorX(final ComponentPort port, final double anchorX, final double direction) {
		if (port.getKind() == ComponentPortKind.SERVICE && port.getRole() == ComponentPortRole.PROVIDED) {
			return anchorX + direction * NAV_BADGE_HALO_RADIUS;
		}
		if (port.getKind() == ComponentPortKind.SERVICE && port.getRole() == ComponentPortRole.USED) {
			double socketInnerRadius = SERVICE_RECEPTACLE_RADIUS - SOCKET_BAND / 2 - SOCKET_BAND_BORDER;
			double badgeCenterOffset = socketInnerRadius - NAV_BADGE_HALO_RADIUS;
			return anchorX - direction * (SERVICE_RECEPTACLE_RADIUS + badgeCenterOffset);
		}
		return anchorX;
	}

	/**
	 * Wires the pad's own activity/highlight state, plus every other fixed-color {@code Line} that visually continues the
	 * pad out to the card edge (the stem) or across a shape's own open span
	 * (the used-service crescent's bridge, the accessed-parameter bracket's
	 * bridge): on a highlighted click, the whole run from the card edge to
	 * the wire reads as one continuous highlighted line, with each extra line
	 * reverting to its own original color (graphite for a stem, a port color
	 * for a bridge) once the highlight ends.
	 */
	private void configurePortSlot(final Rectangle slot, final ComponentPort port, final Line... extraHighlightLines) {
		Paint[] baseStrokes = new Paint[extraHighlightLines.length];
		double[] baseWidths = new double[extraHighlightLines.length];
		for (int i = 0; i < extraHighlightLines.length; i++) {
			baseStrokes[i] = extraHighlightLines[i].getStroke();
			baseWidths[i] = extraHighlightLines[i].getStrokeWidth();
		}
		port.setActivityRenderer(activity -> this.applyPortSlotActivity(slot, activity));
		port.setHighlightRenderer(highlighted -> {
			this.applyPortSlotHighlight(slot, port, highlighted);
			for (int i = 0; i < extraHighlightLines.length; i++) {
				extraHighlightLines[i].setStroke(highlighted ? PortPalette.HIGHLIGHT : baseStrokes[i]);
				extraHighlightLines[i].setStrokeWidth(highlighted ? PortPalette.WIRE_HIGHLIGHT_WIDTH : baseWidths[i]);
			}
		});
	}

	/**
	 * Click-to-highlight entry point: the pad is the click target,
	 * the one that lights up; navigation goes through the nav badge
	 * instead. Consumes the event so it never also triggers the card's
	 * own click/selection handling underneath.
	 */
	private void wirePortClick(final Rectangle slot, final ComponentPort port) {
		slot.setCursor(Cursor.HAND);
		slot.setOnMouseClicked(event -> {
			event.consume();
			if (this.onPortClickConsumer != null) {
				this.onPortClickConsumer.accept(port);
			}
		});
		slot.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
			if (this.onPortContextMenuConsumer != null) {
				this.onPortContextMenuConsumer.accept(port, event);
			}
		});
	}

	/**
	 * Small "navigable" badge (halo + ring + dot in {@link
	 * #NAV_BADGE_COLOR}, ring filled with {@code PortPalette.SLOT_IDLE}) stamped when {@code port.isNavigable()},
	 * centered on the port's own anchor point - the port's actual front,
	 * where a real wire would start. Carries a soft blue glow, which grows
	 * with the badge itself while hovered (see {@link #animateNavBadgeHover}), and an
	 * invisible {@code NAV_BADGE_HIT_RADIUS} circle well past the visible
	 * halo so the click target stays comfortable even when the card is
	 * small on screen. Unlike {@link #wirePortClick}, which reacts on the
	 * pad, this reacts on the badge itself and drives navigation rather than
	 * click-to-highlight; the two gestures never share a click target.
	 */
	private Node navBadge(final double x, final double y, final ComponentPort port) {
		Circle haloCircle = new Circle(x, y, NAV_BADGE_HALO_RADIUS, NAV_BADGE_COLOR);
		haloCircle.setOpacity(NAV_BADGE_HALO_OPACITY);
		Circle ring = new Circle(x, y, NAV_BADGE_RING_RADIUS, PortPalette.SLOT_IDLE);
		ring.setStroke(NAV_BADGE_COLOR);
		ring.setStrokeWidth(1.5);
		Circle dot = new Circle(x, y, NAV_BADGE_DOT_RADIUS, NAV_BADGE_COLOR);
		Circle hitArea = new Circle(x, y, NAV_BADGE_HIT_RADIUS, Color.TRANSPARENT);

		DropShadow glow = new DropShadow();
		glow.setColor(NAV_BADGE_COLOR);
		glow.setRadius(NAV_BADGE_GLOW_RADIUS);
		glow.setSpread(0.35);

		Group badge = new Group(hitArea, haloCircle, ring, dot);
		badge.setEffect(glow);
		badge.setCursor(Cursor.HAND);
		Tooltips.install(badge, navigationTooltip(port));
		Timeline hover = new Timeline();
		badge.setOnMouseEntered(event -> animateNavBadgeHover(hover, badge, haloCircle, glow, true));
		badge.setOnMouseExited(event -> animateNavBadgeHover(hover, badge, haloCircle, glow, false));
		badge.setOnMouseClicked(event -> {
			event.consume();
			if (this.onPortNavigateConsumer != null) {
				this.onPortNavigateConsumer.accept(port);
			}
		});
		if (this.graph != null) {
			BooleanProperty linksVisible = this.linksVisibleProperty(port.getKind());
			badge.visibleProperty().bind(linksVisible);
			badge.managedProperty().bind(linksVisible);
		}
		return badge;
	}

	/** The nav badge's tooltip: where a click on it leads. */
	private static String navigationTooltip(final ComponentPort port) {
		List<String> names = port.getNavigationTargets().stream().map(Class::getSimpleName).sorted().toList();
		if (names.size() == 1) {
			return CellsTexts.get("nav.goTo", names.get(0));
		}
		return CellsTexts.get("nav.goToOneOf", String.join(", ", names));
	}

	/** Plays a badge's hover transition toward its hovered or resting look, from wherever it is. */
	private static void animateNavBadgeHover(final Timeline hover, final Group badge, final Circle halo,
			final DropShadow glow, final boolean hovered) {
		double scale = hovered ? NAV_BADGE_HOVER_SCALE : 1;
		double haloOpacity = hovered ? NAV_BADGE_HOVER_HALO_OPACITY : NAV_BADGE_HALO_OPACITY;
		double glowRadius = hovered ? NAV_BADGE_HOVER_GLOW_RADIUS : NAV_BADGE_GLOW_RADIUS;
		hover.stop();
		hover.getKeyFrames().setAll(new KeyFrame(NAV_BADGE_HOVER_DURATION,
				new KeyValue(badge.scaleXProperty(), scale, NAV_BADGE_SPRING),
				new KeyValue(badge.scaleYProperty(), scale, NAV_BADGE_SPRING),
				new KeyValue(halo.opacityProperty(), haloOpacity, Interpolator.EASE_OUT),
				new KeyValue(glow.radiusProperty(), glowRadius, Interpolator.EASE_OUT)));
		hover.playFromStart();
	}

	/**
	 * Which of {@code Graph}'s three per-kind link-visibility toggles a
	 * given port's badge should follow - the nav badge is the only visual
	 * stand-in a hidden external link has (there is no wire to hide, since
	 * the far end isn't on screen), so it follows the same toggle an
	 * ordinary same-level wire of that kind would.
	 */
	private BooleanProperty linksVisibleProperty(final ComponentPortKind kind) {
		if (kind == ComponentPortKind.SERVICE) {
			return this.graph.showServiceLinksProperty();
		}
		if (kind == ComponentPortKind.EVENT) {
			return this.graph.showEventLinksProperty();
		}
		return this.graph.showParameterLinksProperty();
	}

	private void applyPortSlotHighlight(final Rectangle slot, final ComponentPort port, final boolean highlighted) {
		if (highlighted) {
			slot.setFill(PortPalette.HIGHLIGHT);
			slot.setStroke(PortPalette.GRAPHITE);
			return;
		}
		this.applyPortSlotActivity(slot, port.getActivity());
	}

	private void applyPortSlotActivity(final Rectangle slot, final ComponentPortActivity activity) {
		if (activity == ComponentPortActivity.IDLE) {
			slot.setFill(PortPalette.SLOT_IDLE);
			slot.setStroke(PortPalette.GRAPHITE);
			return;
		}
		// The chosen activity color goes straight onto the fill, exactly as
		// picked - no blend with SLOT_IDLE, which would make the lit color
		// drift with the theme (SLOT_IDLE itself is lighter in light theme,
		// darker in dark theme) instead of showing the color the preferences
		// panel sets. The border stays graphite, like every other slot state
		// (idle, highlight above): the activity color would bury the
		// socket's own outline.
		slot.setFill(this.activityColor(activity));
		slot.setStroke(PortPalette.GRAPHITE);
	}

	private Color activityColor(final ComponentPortActivity activity) {
		if (activity == ComponentPortActivity.SERVICE_FAILED) {
			return PortPalette.SERVICE_FAILURE;
		}
		if (activity == ComponentPortActivity.PARAMETER_FAILED) {
			return PortPalette.SERVICE_FAILURE;
		}
		if (activity == ComponentPortActivity.EVENT_RUNNING) {
			return PortPalette.EVENT_ACTIVITY;
		}
		if (activity == ComponentPortActivity.PARAMETER_RUNNING) {
			return PortPalette.PARAMETER;
		}
		return PortPalette.SERVICE_ACTIVITY;
	}

	private Polygon eventShape(final ComponentPort port, final double x, final double y, final ComponentPortSide side) {
		double outwardDirection = side == ComponentPortSide.LEFT ? -1 : 1;
		double direction = this.eventArrowDirection(port, outwardDirection);
		Color color = port.getRole() == ComponentPortRole.PRODUCED ? PortPalette.EVENT_PRODUCED : PortPalette.EVENT_CONSUMED;
		Polygon polygon = new Polygon(x - direction * EVENT_BACK, y - EVENT_HALF_HEIGHT,
				x + direction * EVENT_SHOULDER, y - EVENT_HALF_HEIGHT, x + direction * EVENT_TIP, y,
				x + direction * EVENT_SHOULDER, y + EVENT_HALF_HEIGHT, x - direction * EVENT_BACK, y + EVENT_HALF_HEIGHT,
				x - direction * EVENT_NOTCH, y);
		polygon.setFill(color);
		polygon.setStroke(PortPalette.GRAPHITE);
		polygon.setStrokeWidth(1.2);
		return polygon;
	}

	private double eventStemEndX(final ComponentPort port, final double shapeX, final double outwardDirection) {
		double arrowDirection = this.eventArrowDirection(port, outwardDirection);
		if (port.getRole() == ComponentPortRole.CONSUMED) {
			return shapeX + arrowDirection * 24;
		}
		return shapeX - arrowDirection * 2;
	}

	private double eventExternalConnectorStartX(final ComponentPort port, final double shapeX,
			final double outwardDirection) {
		double arrowDirection = this.eventArrowDirection(port, outwardDirection);
		if (port.getRole() == ComponentPortRole.CONSUMED) {
			return shapeX - arrowDirection * 2;
		}
		return shapeX + arrowDirection * 24;
	}

	private double eventArrowDirection(final ComponentPort port, final double outwardDirection) {
		return port.getRole() == ComponentPortRole.CONSUMED ? -outwardDirection : outwardDirection;
	}

	private Label portLabel(final String text, final double x, final double y, final double width, final Color color) {
		Label label = new Label(text);
		label.setFont(Font.font("System", 22));
		label.setTextFill(color);
		label.setLayoutX(x);
		label.setLayoutY(y);
		label.setPrefWidth(width);
		label.setAlignment(Pos.CENTER);
		if (this.graph != null) {
			label.visibleProperty().bind(this.graph.showPortNamesProperty());
		}
		return label;
	}

	private Rectangle roundedRectangle(final double x, final double y, final double width, final double height,
			final double radius, final Color fill, final Color stroke, final double strokeWidth) {
		Rectangle rectangle = new Rectangle(x, y, width, height);
		rectangle.setArcWidth(radius);
		rectangle.setArcHeight(radius);
		rectangle.setFill(fill);
		rectangle.setStroke(stroke);
		rectangle.setStrokeWidth(strokeWidth);
		return rectangle;
	}

	/**
	 * Header brand mark: three touching hexagon outlines, placed with the same
	 * {@code sqrt(3)*size} touching-distance formula as
	 * {@link #compositeTypeIcon(double, double, double)} - see that method's javadoc for the
	 * geometry.
	 */
	private Group cubeIcon(final double x, final double y, final double size, final Color color) {
		Group group = new Group();
		double dx = Math.sqrt(3) * size / 2;
		double refY = y + 0.75 * size;
		group.getChildren().add(this.hexagon(x, refY - 1.5 * size, size, Color.TRANSPARENT, color));
		group.getChildren().add(this.hexagon(x - dx, refY, size, Color.TRANSPARENT, color));
		group.getChildren().add(this.hexagon(x + dx, refY, size, Color.TRANSPARENT, color));
		return group;
	}

	/**
	 * One hex-with-spokes glyph: outer hexagon outline, a concentric inner
	 * hexagon at {@code size*0.45}, and 3 diagonals joining that inner
	 * hexagon's opposite vertex pairs. Shared by {@code hexagonCubeIcon()}
	 * below and the composite variant, which stamps it three times.
	 */
	private Group hexWithSpokes(final double x, final double y, final double size) {
		Group group = new Group();
		group.getChildren().add(this.hexagon(x, y, size, Color.TRANSPARENT, PortPalette.GRAPHITE));
		group.getChildren().add(this.hexagon(x, y, size * 0.45, Color.TRANSPARENT, PortPalette.GRAPHITE));
		Line a = new Line(x, y - size * 0.45, x, y + size * 0.45);
		Line b = new Line(x - size * 0.39, y - size * 0.2, x + size * 0.39, y + size * 0.2);
		Line c = new Line(x + size * 0.39, y - size * 0.2, x - size * 0.39, y + size * 0.2);
		a.setStroke(PortPalette.GRAPHITE);
		b.setStroke(PortPalette.GRAPHITE);
		c.setStroke(PortPalette.GRAPHITE);
		group.getChildren().addAll(a, b, c);
		return group;
	}

	/**
	 * Raw component's center icon: the hexagon without the managed core, a plain object in it
	 * drawn as a circle.
	 */
	private Group rawObjectIcon(final double x, final double y, final double size) {
		Circle object = new Circle(x, y, size * 0.3, Color.TRANSPARENT);
		object.setStroke(PortPalette.GRAPHITE);
		object.setStrokeWidth(1.4);
		return new Group(this.hexagon(x, y, size, Color.TRANSPARENT, PortPalette.GRAPHITE), object);
	}

	/** Leaf card's center icon: a single {@link #hexWithSpokes(double, double, double)} glyph. */
	private Group hexagonCubeIcon(final double x, final double y, final double size) {
		return this.hexWithSpokes(x, y, size);
	}

	/**
	 * Composite card's center icon: three touching hex-with-spokes glyphs in
	 * a hex-grid cluster - "three leaf components wired together". Ratios:
	 * {@code clusterSize = overallSize*0.62}, centers
	 * {@code sqrt(3)*clusterSize/2} apart so every pair of pointy-top
	 * hexagons shares an edge (touches) in every direction, not just
	 * top-to-bottom.
	 */
	private Group compositeTypeIcon(final double cx, final double centerY, final double overallSize) {
		Group group = new Group();
		double clusterSize = overallSize * 0.62;
		double dx = Math.sqrt(3) * clusterSize / 2;
		double refY = centerY + 0.75 * clusterSize;
		group.getChildren().add(this.hexWithSpokes(cx, refY - 1.5 * clusterSize, clusterSize));
		group.getChildren().add(this.hexWithSpokes(cx - dx, refY, clusterSize));
		group.getChildren().add(this.hexWithSpokes(cx + dx, refY, clusterSize));
		return group;
	}

	private Polygon hexagon(final double x, final double y, final double size, final Color fill, final Color stroke) {
		Polygon hexagon = new Polygon();
		for (int i = 0; i < 6; i++) {
			double angle = Math.PI / 6 + Math.PI / 3 * i;
			hexagon.getPoints().addAll(x + Math.cos(angle) * size, y + Math.sin(angle) * size);
		}
		hexagon.setFill(fill);
		hexagon.setStroke(stroke);
		hexagon.setStrokeWidth(1.4);
		return hexagon;
	}

	private void updateSelectedState(final boolean selected) {
		if (this.selectionHighlight == null) {
			return;
		}
		this.selectionHighlight.setVisible(selected);
	}
}
