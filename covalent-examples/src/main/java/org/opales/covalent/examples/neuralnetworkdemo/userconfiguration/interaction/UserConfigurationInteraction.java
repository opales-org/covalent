/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the {@code UserConfiguration} composite's Interaction
 * Controller. See {@link DatasetController} for what a bare {@code
 * @Component} marker interface means in Covalent in general; see
 * {@link UserConfiguration} for why this composite has a
 * Interaction/View pair but no Model or Business Controller.
 */
@Component
public interface UserConfigurationInteraction {

}
