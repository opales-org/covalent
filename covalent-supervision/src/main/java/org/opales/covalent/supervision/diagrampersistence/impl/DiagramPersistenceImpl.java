/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.diagrampersistence.impl;

import org.opales.covalent.supervision.diagrampersistence.DiagramPersistence;
import org.opales.covalent.supervision.diagrampersistence.controller.impl.DiagramPersistenceControllerImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code DiagramPersistence} composite: deploys its one MVIC part - a
 * Controller only, no Model/View/Interaction, see {@code
 * DiagramPersistence}'s own class javadoc for why. Only this class needs
 * to be passed to {@code container.deployComponents(...)}.
 */
@Implementation
public class DiagramPersistenceImpl implements DiagramPersistence {

	/** Creates the composite; the container instantiates its controller part. */
	public DiagramPersistenceImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private DiagramPersistenceControllerImpl controller;
}
