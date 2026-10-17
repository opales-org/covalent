/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the {@code UserConfiguration} composite's View. See
 * {@link DatasetController} for what a bare {@code @Component} marker
 * interface means in Covalent in general; see {@link UserConfiguration} for
 * why this composite has a View/Interaction pair but no Model or
 * Business Controller.
 */
@Component
public interface UserConfigurationView {

}
