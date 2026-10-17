/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import java.io.File;

import org.opales.covalent.supervision.diagrampersistence.SavedView;

/**
 * A view of the views library to export.
 *
 * @param view the view
 * @param file the file to write
 */
public record ExportViewRequest(SavedView view, File file) {
}
