/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.impl;

import org.opales.covalent.supervision.dependencymanagement.DependencyManagement;
import org.opales.covalent.supervision.dependencymanagement.controller.impl.DependencyManagementControllerImpl;
import org.opales.covalent.supervision.dependencymanagement.interaction.impl.DependencyManagementInteractionImpl;
import org.opales.covalent.supervision.dependencymanagement.model.impl.DependencyManagementModelImpl;
import org.opales.covalent.supervision.dependencymanagement.view.impl.DependencyManagementViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code DependencyManagement} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 */
@Implementation
public class DependencyManagementImpl implements DependencyManagement {

	/** Creates the composite; the container deploys its four MVIC parts as its own parts. */
	public DependencyManagementImpl() {
		// Nothing to initialize.
	}

	@Implementation
	private DependencyManagementModelImpl model;

	@Implementation
	private DependencyManagementControllerImpl controller;

	@Implementation
	private DependencyManagementViewImpl view;

	@Implementation
	private DependencyManagementInteractionImpl interaction;
}
