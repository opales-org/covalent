/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import javafx.animation.PauseTransition;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * The Supervisor's hover cards: a tooltip that shows a qualified name with a button copying it, which
 * the pointer can reach. It opens after a short pause under its node, stays while the pointer is on the
 * node or on the card, and closes shortly after the pointer leaves both, when the node is pressed, hidden
 * or removed from its scene. The copy button carries no tooltip of its own: a tooltip opened from a node
 * that lies inside another tooltip's window makes JavaFX's style processing loop forever.
 */
public final class HoverCards {

	private static final Duration SHOW_DELAY = Duration.millis(450);
	private static final Duration LEAVE_GRACE = Duration.millis(250);
	private static final Duration COPIED_DURATION = Duration.millis(1500);
	private static final double MAX_WIDTH = 380;
	private static final double GRAPHIC_GAP = 8;

	private static final String COPY_ICON = "M9 9h11v12H9zM5 15V3h11";
	private static final String COPIED_ICON = "M5 12.5l4.5 4.5L19 7";
	private static final double COPY_ICON_SIZE = 15;

	private HoverCards() {
	}

	/**
	 * Gives this node a hover card.
	 *
	 * @param node          the node the card describes
	 * @param qualifiedName the name the card shows and its button copies; no card when {@code null} or empty
	 * @param detail        a line shown under the name, {@code null} for none
	 */
	public static void install(final Node node, final String qualifiedName, final String detail) {
		if (qualifiedName == null || qualifiedName.isEmpty()) {
			return;
		}
		new Card(node, qualifiedName, detail);
	}

	/** One node's card, with the timers deciding when it shows and hides. */
	private static final class Card {

		private final Node node;
		private final String text;
		private final String qualifiedName;
		private final PauseTransition showTimer = new PauseTransition(SHOW_DELAY);
		private final PauseTransition leaveTimer = new PauseTransition(LEAVE_GRACE);
		private final PauseTransition copiedTimer = new PauseTransition(COPIED_DURATION);
		private final SVGPath copyIcon = new SVGPath();
		private final SVGPath copiedIcon = new SVGPath();
		private Tooltip popup;
		private boolean overNode;
		private boolean overPopup;
		private boolean popupWired;

		Card(final Node node, final String qualifiedName, final String detail) {
			this.node = node;
			this.qualifiedName = qualifiedName;
			String name = Tooltips.breakable(qualifiedName);
			this.text = detail == null || detail.isEmpty() ? name : name + "\n" + detail;

			this.showTimer.setOnFinished(event -> this.show());
			this.leaveTimer.setOnFinished(event -> {
				if (!this.overNode && !this.overPopup) {
					this.hide();
				}
			});
			this.copiedTimer.setOnFinished(event -> this.showCopyReady());
			node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
				this.overNode = true;
				this.leaveTimer.stop();
				if (this.popup == null || !this.popup.isShowing()) {
					this.showTimer.playFromStart();
				}
			});
			node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
				this.overNode = false;
				this.showTimer.stop();
				this.leaveTimer.playFromStart();
			});
			// A press on the card itself, redirected to its node by the popup window, must not close it.
			node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
				if (!this.isOnPopup(event.getTarget())) {
					this.hide();
				}
			});
			node.sceneProperty().addListener((property, previous, scene) -> {
				if (scene == null) {
					this.hide();
				}
			});
			node.visibleProperty().addListener((property, previous, visible) -> {
				if (!visible) {
					this.hide();
				}
			});
		}

		private boolean isOnPopup(final EventTarget target) {
			return this.popup != null && this.popup.getScene() != null && target instanceof Node targetNode
					&& targetNode.getScene() == this.popup.getScene();
		}

		private void show() {
			if (this.node.getScene() == null || this.node.getScene().getWindow() == null || !this.node.isVisible()) {
				return;
			}
			Bounds bounds = this.node.localToScreen(this.node.getBoundsInLocal());
			if (bounds == null) {
				return;
			}
			if (this.popup == null) {
				this.popup = this.buildPopup();
			}
			this.popup.show(this.node, bounds.getMinX(), bounds.getMaxY());
		}

		private Tooltip buildPopup() {
			Tooltip card = new Tooltip(this.text);
			card.setWrapText(true);
			card.setMaxWidth(MAX_WIDTH);
			card.setAutoHide(false);
			card.setContentDisplay(ContentDisplay.RIGHT);
			card.setGraphicTextGap(GRAPHIC_GAP);

			// Both drawings stay in the button and one is made transparent: changing the drawing of a
			// single icon resizes the button, which leaves it partly redrawn in the card's window.
			this.copyIcon.setContent(COPY_ICON);
			this.copiedIcon.setContent(COPIED_ICON);
			for (SVGPath icon : new SVGPath[] { this.copyIcon, this.copiedIcon }) {
				icon.getStyleClass().add("sv-tool-icon");
				icon.setScaleX(COPY_ICON_SIZE / 24);
				icon.setScaleY(COPY_ICON_SIZE / 24);
			}
			Button copy = new Button(null, new Group(this.copyIcon, this.copiedIcon));
			copy.getStyleClass().addAll("sv-icon-button", "sv-copy-button");
			copy.setFocusTraversable(false);
			this.showCopyReady();
			copy.setOnAction(event -> {
				ClipboardContent content = new ClipboardContent();
				content.putString(this.qualifiedName);
				Clipboard.getSystemClipboard().setContent(content);
				this.copyIcon.setOpacity(0);
				this.copiedIcon.setOpacity(1);
				this.copiedTimer.playFromStart();
			});
			card.setGraphic(copy);

			// Moved under its node before its window opens, and hidden until placed there: a window
			// opened at the pointer takes the pointer off the node.
			card.setOnShowing(event -> {
				card.setOpacity(0);
				Tooltips.placeUnder(card, this.node);
			});
			card.setOnShown(event -> {
				this.wirePopup(card);
				Tooltips.placeUnder(card, this.node);
				card.setOpacity(1);
			});
			card.setOnHidden(event -> {
				this.overPopup = false;
				this.copiedTimer.stop();
				this.showCopyReady();
			});
			return card;
		}

		private void wirePopup(final Tooltip card) {
			if (this.popupWired || card.getScene() == null) {
				return;
			}
			this.popupWired = true;
			Node root = card.getScene().getRoot();
			root.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
				this.overPopup = true;
				this.leaveTimer.stop();
			});
			root.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
				this.overPopup = false;
				this.leaveTimer.playFromStart();
			});
		}

		private void showCopyReady() {
			this.copyIcon.setOpacity(1);
			this.copiedIcon.setOpacity(0);
		}

		private void hide() {
			this.showTimer.stop();
			this.leaveTimer.stop();
			if (this.popup != null && this.popup.isShowing()) {
				this.popup.hide();
			}
		}
	}
}
