/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view;

import java.util.Set;

/**
 * What the save dialog shows: the name and description it proposes, the
 * facts of the view to save, and the names already used for its container.
 *
 * @param name           the proposed name, empty if none
 * @param description    the proposed description, empty if none
 * @param containerName  the container the view is saved from
 * @param level          the level shown, root to current, e.g. {@code Top › Ecosystem}
 * @param componentCount the components in the diagram, the hidden ones included
 * @param hiddenCount    the components hidden
 * @param zoom           the zoom scale
 * @param existingNames  the names of the container's views: saving under one replaces it
 */
public record SaveViewForm(String name, String description, String containerName, String level, int componentCount,
		int hiddenCount, double zoom, Set<String> existingNames) {
}
