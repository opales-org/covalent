/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.opales.covalent.supervision.diagrampersistence.SavedView;

/**
 * What the views library shows.
 *
 * @param containerName the observed container's name, {@code null} if none
 * @param viewsFolder   the views folder
 * @param entries       every view, most recently saved first
 */
public record ViewLibraryContent(String containerName, File viewsFolder, List<Entry> entries) {

	/**
	 * One view of the library.
	 *
	 * @param view              the view
	 * @param missingComponents the class names of its components that are not
	 *                          in the observed container; checked only for
	 *                          the observed container's views
	 */
	public record Entry(SavedView view, Set<String> missingComponents) {
	}
}
