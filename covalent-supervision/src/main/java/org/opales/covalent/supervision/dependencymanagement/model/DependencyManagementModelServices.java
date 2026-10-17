/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.model;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Provided by {@code DependencyManagementModel}, used by
 * {@code DependencyManagementController}. No logic beyond storing what it
 * is given and firing the matching {@link DependencyManagementEvents}
 * notification.
 */
@Services
public interface DependencyManagementModelServices {

	/**
	 * Replaces the whole graph snapshot at once - matches
	 * {@code DependencyGraph}'s own nature (an immutable, one-shot
	 * computation, never incrementally updated).
	 *
	 * @param graph the new snapshot
	 */
	void setGraph(DependencyManagementGraph graph);
}
