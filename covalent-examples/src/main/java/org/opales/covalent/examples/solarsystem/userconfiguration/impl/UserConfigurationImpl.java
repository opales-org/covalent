/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.userconfiguration.impl;

import org.opales.covalent.examples.solarsystem.userconfiguration.UserConfiguration;
import org.opales.covalent.examples.solarsystem.userconfiguration.interaction.UserConfigurationInteraction;
import org.opales.covalent.examples.solarsystem.userconfiguration.interaction.impl.UserConfigurationInteractionImpl;
import org.opales.covalent.examples.solarsystem.userconfiguration.view.impl.UserConfigurationViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code UserConfiguration} composite: deploys its View and Interaction
 * Controller together. No Model/Events part: this composite owns no state
 * of its own, it only fronts other functions' Parameters through
 * {@code UserConfigurationInteraction}.
 */
@Implementation
public class UserConfigurationImpl implements UserConfiguration {

	@Implementation
	private UserConfigurationViewImpl view;

	@Implementation
	private UserConfigurationInteractionImpl interaction;
}
