/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

import java.util.Set;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Highlighting;

/**
 * Visual representation of a Covalent port on a component cell.
 */
public class ComponentPort {

	private static final Duration ACTIVITY_RELEASE_DELAY = Duration.millis(260);


	private final CovalentComponentCell componentCell;

	private final ComponentPortKind kind;

	private final ComponentPortRole role;

	private final String label;

	private ComponentPortSide side;

	private final DoubleProperty anchorX = new SimpleDoubleProperty();

	private final DoubleProperty anchorY = new SimpleDoubleProperty();

	/**
	 * Whether a visible wire reaches this port - false by default (a port
	 * looks "free" until a wire lands on it). Bound by whoever
	 * creates the {@code AnchoredPortEdge} that lands here (see
	 * {@code ComponentDiagramViewImpl}) to that edge's own visibility, so it
	 * tracks link-kind toggles, composite focus and "Hide this link" without
	 * this port needing to know about any of them itself. A used-service
	 * port's socket "bridge" segment (see {@code CovalentComponentCell})
	 * shows only while this is true - never for a navigable port, which
	 * never has one locally to begin with.
	 */
	private final BooleanProperty connected = new SimpleBooleanProperty(false);

	private final PortAnchorCell anchorCell;

	private ComponentPortActivity activity = ComponentPortActivity.IDLE;

	private int activeActivities = 0;

	private PauseTransition releaseTransition;

	private Consumer<ComponentPortActivity> activityRenderer;

	private boolean highlighted = false;

	private PauseTransition highlightTransition;

	private Consumer<Boolean> highlightRenderer;

	/**
	 * {@code true} for a port standing in for a {@code
	 * ComponentDiagramExternalPort} - drawn with an extra small badge the
	 * card's pad doesn't otherwise have, and clickable there to jump to
	 * {@link #getNavigationTargets()} (one target: go straight there;
	 * several: a picker). Never set for an ordinary same-level port.
	 */
	private boolean navigable = false;

	/**
	 * The real component type(s) this port leads to - one for most
	 * navigable ports, several for a fan-out role with more than one
	 * external taker (see {@code ComponentPortRole.isFanOut()} on the
	 * model-side mirror of this enum). Empty for a non-navigable port.
	 */
	private Set<Class<?>> navigationTargets = Set.of();

	ComponentPort(final CovalentComponentCell componentCell, final ComponentPortKind kind,
			final ComponentPortRole role, final String label, final ComponentPortSide side) {
		this.componentCell = componentCell;
		this.kind = kind;
		this.role = role;
		this.label = label;
		this.side = side;
		this.anchorCell = new PortAnchorCell(this);
	}

	public CovalentComponentCell getComponentCell() {
		return this.componentCell;
	}

	public ComponentPortKind getKind() {
		return this.kind;
	}

	public ComponentPortRole getRole() {
		return this.role;
	}

	public String getLabel() {
		return this.label;
	}

	public ComponentPortSide getSide() {
		return this.side;
	}

	public ComponentPortActivity getActivity() {
		return this.activity;
	}

	public void setSide(final ComponentPortSide side) {
		this.side = side;
	}

	public DoubleProperty anchorXProperty() {
		return this.anchorX;
	}

	public DoubleProperty anchorYProperty() {
		return this.anchorY;
	}

	void setAnchor(final double x, final double y) {
		this.anchorX.set(x);
		this.anchorY.set(y);
	}

	public boolean isNavigable() {
		return this.navigable;
	}

	public Set<Class<?>> getNavigationTargets() {
		return this.navigationTargets;
	}

	public void setNavigationTargets(final Set<Class<?>> navigationTargets) {
		this.navigable = !navigationTargets.isEmpty();
		this.navigationTargets = navigationTargets;
	}

	public PortAnchorCell getAnchorCell() {
		return this.anchorCell;
	}

	public BooleanProperty connectedProperty() {
		return this.connected;
	}

	public boolean isConnected() {
		return this.connected.get();
	}

	public void beginActivity(final ComponentPortActivity activity) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> this.beginActivity(activity));
			return;
		}
		this.cancelReleaseTransition();
		this.activeActivities++;
		this.activity = activity;
		this.renderActivity();
	}

	public void finishActivity(final ComponentPortActivity terminalActivity) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> this.finishActivity(terminalActivity));
			return;
		}
		if (terminalActivity != null) {
			this.activity = terminalActivity;
			this.renderActivity();
		}
		this.activeActivities = Math.max(0, this.activeActivities - 1);
		if (this.activeActivities == 0) {
			this.scheduleIdle();
		}
	}

	void setActivityRenderer(final Consumer<ComponentPortActivity> activityRenderer) {
		this.activityRenderer = activityRenderer;
		this.renderActivity();
	}

	void setHighlightRenderer(final Consumer<Boolean> highlightRenderer) {
		this.highlightRenderer = highlightRenderer;
	}

	/**
	 * Click-to-highlight: briefly paints this port's pad (and, via the
	 * caller iterating connected edges, every wire and far-end pad reachable
	 * from it) before automatically reverting on a timer.
	 */
	public void highlight() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::highlight);
			return;
		}
		this.cancelHighlightTransition();
		this.highlighted = true;
		this.renderHighlight();
		this.highlightTransition = new PauseTransition(Highlighting.DURATION);
		this.highlightTransition.setOnFinished(e -> {
			this.highlighted = false;
			this.renderHighlight();
		});
		this.highlightTransition.playFromStart();
	}

	String key() {
		return key(this.kind, this.role, this.label);
	}

	static String key(final ComponentPortKind kind, final ComponentPortRole role, final String label) {
		return kind.name() + ":" + role.name() + ":" + label;
	}

	private void cancelReleaseTransition() {
		if (this.releaseTransition != null) {
			this.releaseTransition.stop();
			this.releaseTransition = null;
		}
	}

	private void cancelHighlightTransition() {
		if (this.highlightTransition != null) {
			this.highlightTransition.stop();
			this.highlightTransition = null;
		}
	}

	private void scheduleIdle() {
		this.cancelReleaseTransition();
		this.releaseTransition = new PauseTransition(ACTIVITY_RELEASE_DELAY);
		this.releaseTransition.setOnFinished(e -> {
			if (this.activeActivities == 0) {
				this.activity = ComponentPortActivity.IDLE;
				this.renderActivity();
			}
		});
		this.releaseTransition.playFromStart();
	}

	private void renderActivity() {
		if (this.activityRenderer != null) {
			this.activityRenderer.accept(this.activity);
		}
	}

	private void renderHighlight() {
		if (this.highlightRenderer != null) {
			this.highlightRenderer.accept(this.highlighted);
		}
	}
}
