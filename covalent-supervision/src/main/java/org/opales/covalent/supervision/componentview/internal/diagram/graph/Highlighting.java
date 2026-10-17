/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.graph;

import javafx.util.Duration;

/** What every diagram highlight shares: component flashes, port and link highlights. */
public final class Highlighting {

	/** How long a highlight stays on screen. */
	public static final Duration DURATION = Duration.seconds(2);

	private Highlighting() {
	}
}
