/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.userconfiguration.impl;

import org.opales.covalent.examples.rubikscubesolver.userconfiguration.UserConfiguration;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.interaction.impl.UserConfigurationInteractionImpl;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.view.impl.UserConfigurationViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code UserConfiguration} composite: deploys its View and Interaction
 * Controller together, the same composed-sub-component mechanism
 * {@code DatasetExplorerImpl} documents in full in the Neural Network
 * Trainer demo. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 * <p>
 * Two fields instead of the usual four: no Model, no Business Controller,
 * because - as detailed on {@link UserConfiguration} - this composite owns
 * no domain state of its own.
 */
@Implementation
public class UserConfigurationImpl implements UserConfiguration {

	@Implementation
	private UserConfigurationViewImpl view;

	@Implementation
	private UserConfigurationInteractionImpl interaction;
}
