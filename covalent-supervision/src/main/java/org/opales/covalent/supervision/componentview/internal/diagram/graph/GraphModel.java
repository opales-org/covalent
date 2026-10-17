/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import java.util.List;
import java.util.Objects;

import org.opales.covalent.supervision.componentview.internal.diagram.cells.AbstractCell;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Region;

/**
 * What a {@link Graph} displays: its cells and its edges. Additions and removals are first recorded as pending
 * changes, so that the graph can draw exactly what changed, and are folded into the full lists by
 * {@link #endUpdate()}.
 */
public class GraphModel {

	/** The items of one kind, with the additions and removals recorded since the last update. */
	private static final class Changes<T> {

		private final ObservableList<T> all = FXCollections.observableArrayList();
		private final ObservableList<T> added = FXCollections.observableArrayList();
		private final ObservableList<T> removed = FXCollections.observableArrayList();

		void commit() {
			this.all.addAll(this.added);
			this.all.removeAll(this.removed);
			this.added.clear();
			this.removed.clear();
		}

	}

	/** Parent of the cells that have no other parent. It draws nothing. */
	private static final class RootCell extends AbstractCell {

		@Override
		public Region getGraphic(final Graph graph) {
			return null;
		}

	}

	private final Cell root = new RootCell();
	private Changes<Cell> cells = new Changes<>();
	private Changes<Edge> edges = new Changes<>();

	/** Forgets every cell and edge, pending or not. The root keeps its children. */
	public void clear() {
		this.cells = new Changes<>();
		this.edges = new Changes<>();
	}

	/**
	 * Makes the pending changes part of the model: every added cell without a parent becomes a child of the
	 * root, removed cells are detached from the root, and the pending lists are emptied.
	 */
	public void endUpdate() {
		this.attachOrphansToRoot(this.cells.added);
		this.detachFromRoot(this.cells.removed);
		this.cells.commit();
		this.edges.commit();
	}

	public Cell getRoot() {
		return this.root;
	}

	public ObservableList<Cell> getAllCells() {
		return this.cells.all;
	}

	public ObservableList<Cell> getAddedCells() {
		return this.cells.added;
	}

	public ObservableList<Cell> getRemovedCells() {
		return this.cells.removed;
	}

	public ObservableList<Edge> getAllEdges() {
		return this.edges.all;
	}

	public ObservableList<Edge> getAddedEdges() {
		return this.edges.added;
	}

	public ObservableList<Edge> getRemovedEdges() {
		return this.edges.removed;
	}

	public void addCell(final Cell cell) {
		this.cells.added.add(Objects.requireNonNull(cell, "Cannot add a null cell"));
	}

	public void removeCell(final Cell cell) {
		this.cells.removed.add(Objects.requireNonNull(cell, "Cannot remove a null cell"));
	}

	public void addEdge(final Edge edge) {
		this.edges.added.add(Objects.requireNonNull(edge, "Cannot add a null edge"));
	}

	public void removeEdge(final Edge edge) {
		this.edges.removed.add(Objects.requireNonNull(edge, "Cannot remove a null edge"));
	}

	private void attachOrphansToRoot(final List<Cell> candidates) {
		for (final Cell cell : candidates) {
			if (cell.getParents().isEmpty()) {
				this.root.addChild(cell);
			}
		}
	}

	private void detachFromRoot(final List<Cell> detached) {
		for (final Cell cell : detached) {
			this.root.removeChild(cell);
		}
	}

}
