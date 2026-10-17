/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.supervision.componentview.internal.diagram.graph.Cell;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Starting point of the cells of a graph. It keeps the lists of parent and child cells and the visibility and
 * selection state, which the drawn region binds to. A concrete cell only has to build that region in
 * {@link #getGraphic}.
 */
public abstract class AbstractCell implements Cell {

	private final List<Cell> parents = new ArrayList<>();
	private final List<Cell> children = new ArrayList<>();
	protected SimpleBooleanProperty visible = new SimpleBooleanProperty(true);
	protected SimpleBooleanProperty selected = new SimpleBooleanProperty(false);

	@Override
	public List<Cell> getParents() {
		return this.parents;
	}

	@Override
	public void addParent(final Cell cell) {
		this.parents.add(cell);
	}

	@Override
	public List<Cell> getChildren() {
		return this.children;
	}

	@Override
	public void addChild(final Cell cell) {
		this.children.add(cell);
	}

	@Override
	public void removeChild(final Cell cell) {
		this.children.remove(cell);
	}

	@Override
	public void setVisible(final boolean isVisible) {
		this.visible.set(isVisible);
	}

	@Override
	public BooleanProperty visibleProperty() {
		return this.visible;
	}

	@Override
	public void setSelected(final boolean isSelected) {
		this.selected.set(isSelected);
	}

	@Override
	public BooleanProperty selectedProperty() {
		return this.selected;
	}

}
