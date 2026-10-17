/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.Map;
import java.util.Set;

/**
 * The layout a tab opened from a saved view starts with, applied by the View
 * once that tab shows its components: hidden components, positions and zoom.
 *
 * @param hiddenComponents the components to hide
 * @param positions        component to {@code [layoutX, layoutY]}
 * @param zoom             the zoom scale
 */
public record ComponentDiagramLayout(Set<Class<?>> hiddenComponents, Map<Class<?>, double[]> positions, double zoom) {
}
