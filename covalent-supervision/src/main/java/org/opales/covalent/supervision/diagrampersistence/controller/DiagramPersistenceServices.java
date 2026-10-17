/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.diagrampersistence.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.diagrampersistence.SavedView;

/**
 * The views library: component diagram views saved in the views folder (see
 * {@link #getViewsFolder()}), one sub-folder per container, each view a
 * {@code .cow} file with a {@code .png} thumbnail beside it. A view's name
 * is unique for its container.
 */
@Services
public interface DiagramPersistenceServices {

	/**
	 * Returns the views folder: the one chosen in the supervisor's settings,
	 * {@code ~/.covalent/supervisor/views} otherwise.
	 *
	 * @return the views folder, which may not exist yet
	 */
	File getViewsFolder();

	/**
	 * Lists every view of the library, every container's, unreadable files
	 * included (see {@link SavedView#problem()}).
	 *
	 * @return the views, most recently saved first
	 */
	List<SavedView> listViews();

	/**
	 * Returns whether a view of this name exists for this container.
	 *
	 * @param containerName the container's name
	 * @param name          the view's name
	 * @return {@code true} if saving under this name replaces a view
	 */
	boolean hasView(String containerName, String name);

	/**
	 * Saves a view, replacing the one of the same name for this container.
	 *
	 * @param containerName the container the view is saved from
	 * @param name          the view's name
	 * @param description   the view's description, empty if none
	 * @param snapshot      the diagram state
	 * @param thumbnailPng  the thumbnail, as PNG bytes, {@code null} for none
	 * @return the saved view
	 * @throws IOException if the view could not be written
	 */
	SavedView saveView(String containerName, String name, String description, ComponentDiagramSnapshot snapshot,
			byte[] thumbnailPng) throws IOException;

	/**
	 * Renames a view.
	 *
	 * @param view    the view to rename
	 * @param newName its new name
	 * @return the renamed view
	 * @throws IOException if a view of this name already exists for its
	 *                     container, or the view could not be renamed
	 */
	SavedView renameView(SavedView view, String newName) throws IOException;

	/**
	 * Deletes a view and its thumbnail.
	 *
	 * @param view the view to delete
	 * @throws IOException if the view could not be deleted
	 */
	void deleteView(SavedView view) throws IOException;

	/**
	 * Writes a view to a file outside the library, to share it.
	 *
	 * @param view the view to export
	 * @param file the file to write
	 * @throws IOException if the file could not be written
	 */
	void exportView(SavedView view, File file) throws IOException;

	/**
	 * Reads a view file outside the library, without importing it.
	 *
	 * @param file the file to read
	 * @return the view it holds
	 * @throws IOException if the file cannot be read as a view
	 */
	SavedView readView(File file) throws IOException;

	/**
	 * Imports a view file into the library, for the container it was saved
	 * from, replacing the view of the same name.
	 *
	 * @param file the file to import
	 * @return the imported view
	 * @throws IOException if the file cannot be read as a view, or the view
	 *                     could not be written
	 */
	SavedView importView(File file) throws IOException;
}
