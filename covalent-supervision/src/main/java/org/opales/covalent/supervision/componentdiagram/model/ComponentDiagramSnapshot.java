/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One tab's state at one drill-down focus level, as a saved view holds it -
 * positions only mean something relative to that level's own topology, so a
 * snapshot never spans more than one. Written and read verbatim by {@code
 * DiagramPersistenceServices}; every class name here is a plain fully-
 * qualified {@link String}, never a {@code Class<?>} - resolving them
 * (and tolerating ones that no longer resolve) happens in {@code
 * ComponentDiagramControllerImpl}, not in the generic persistence reader/
 * writer.
 * <p>
 * Deliberately excludes dependencies-visibility (per-component "show
 * deps" highlighting): same category as port-activity flash, transient
 * rendering state that {@code ComponentDiagramModelServices} itself never
 * persists either.
 *
 * @param focusPath        the drill-down path's class names, root to
 *                         current, empty for the top-level forest
 * @param hiddenComponents class names whose component was hidden at this
 *                         level
 * @param positions        class name to {@code [layoutX, layoutY]}, only
 *                         for components present at this level
 * @param selection        class names selected at this level
 * @param zoom             the graph canvas's zoom scale
 */
public record ComponentDiagramSnapshot(List<String> focusPath, Set<String> hiddenComponents,
		Map<String, double[]> positions, Set<String> selection, double zoom) {

	/**
	 * Returns the class names of every component this snapshot shows or
	 * hides.
	 *
	 * @return the class names, positioned components first
	 */
	public Set<String> componentNames() {
		Set<String> names = new LinkedHashSet<>(this.positions.keySet());
		names.addAll(this.hiddenComponents);
		return names;
	}
}
