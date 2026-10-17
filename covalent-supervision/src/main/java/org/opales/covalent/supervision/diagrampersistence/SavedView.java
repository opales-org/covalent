/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.diagrampersistence;

import java.io.File;
import java.time.Instant;

import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;

/**
 * A component diagram view kept in the views library: its name, unique for
 * its container, its description, the container it was saved from, when it
 * was saved, and the diagram state itself.
 * <p>
 * A file of the library that cannot be read is listed too, so that it can be
 * deleted: {@link #problem()} then tells why, its name is the file's, and it
 * has no container, date or snapshot.
 *
 * @param name          the view's name
 * @param description   the view's description, empty if none
 * @param containerName the name of the container the view was saved from
 * @param savedAt       when the view was saved
 * @param snapshot      the diagram state
 * @param file          the view's file
 * @param thumbnail     the view's thumbnail image file, which may not exist
 * @param problem       why the file cannot be read, {@code null} if it can
 */
public record SavedView(String name, String description, String containerName, Instant savedAt,
		ComponentDiagramSnapshot snapshot, File file, File thumbnail, String problem) {

	/**
	 * Returns whether the view's file could be read.
	 *
	 * @return {@code true} if the view can be opened
	 */
	public boolean readable() {
		return this.problem == null;
	}
}
