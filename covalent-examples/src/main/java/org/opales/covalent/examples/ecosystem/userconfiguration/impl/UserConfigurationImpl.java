/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.userconfiguration.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.examples.ecosystem.userconfiguration.UserConfiguration;
import org.opales.covalent.examples.ecosystem.userconfiguration.interaction.impl.UserConfigurationInteractionImpl;
import org.opales.covalent.examples.ecosystem.userconfiguration.view.impl.UserConfigurationViewImpl;

/**
 * The UserConfiguration composite: deploys its View and Interaction
 * Controller together. No Model/Events part.
 */
@Implementation
public class UserConfigurationImpl implements UserConfiguration {

    @Implementation
    private UserConfigurationViewImpl view;

    @Implementation
    private UserConfigurationInteractionImpl interaction;
}
