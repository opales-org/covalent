/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.impl.DatasetExplorerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.UserConfiguration;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.impl.UserConfigurationInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.impl.UserConfigurationViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code UserConfiguration} composite: deploys its View and Interaction
 * Controller together, the same composed-sub-component mechanism
 * {@link DatasetExplorerImpl} documents in full - each field below is a
 * sub-component in its own right (own type, ports and lifecycle state),
 * resolved and wired by the container when it instantiates this class. Only
 * this class needs to be passed to {@code container.deployComponents(...)}.
 * <p>
 * Two fields instead of the usual four: no Model, no Business Controller,
 * because - as detailed on {@link UserConfiguration} - this composite owns
 * no domain state of its own; it only fronts other functions' Parameters
 * through {@link UserConfigurationInteractionImpl}.
 */
@Implementation
public class UserConfigurationImpl implements UserConfiguration {

	@Implementation
	private UserConfigurationViewImpl view;

	@Implementation
	private UserConfigurationInteractionImpl interaction;
}
