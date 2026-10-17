/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import org.opales.covalent.supervision.diagrampersistence.SavedView;

/**
 * A view of the views library to rename.
 *
 * @param view    the view
 * @param newName its new name
 */
public record RenameViewRequest(SavedView view, String newName) {
}
