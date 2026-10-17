/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.edges;

/**
 * How a {@link RoutedLine} turns its endpoints into a drawn path.
 *
 * <p>{@code STRAIGHT} skips {@link OrthogonalRouter} entirely and draws a direct
 * segment between the two endpoints. {@code CORNER} and {@code ROUND} both route
 * through {@link OrthogonalRouter}; they differ only in how {@link RoutedLine}
 * renders the resulting waypoints, sharp right angles versus eased corners.
 */
public enum EdgeRoutingStyle {
	STRAIGHT, CORNER, ROUND
}
