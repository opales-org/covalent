/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.model;

import org.opales.covalent.container.component.annotations.Events;

/** Produced by {@code DependencyManagementModel}, consumed by the View and Interaction. */
@Events
public interface DependencyManagementEvents {

	/**
	 * Fired whenever the whole graph is replaced: the new graph is read
	 * through {@link DependencyManagementModelReadServices}.
	 */
	void onGraphChanged();
}
