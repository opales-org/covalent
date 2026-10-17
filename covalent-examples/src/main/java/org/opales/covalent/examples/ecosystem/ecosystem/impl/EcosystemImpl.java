/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.ecosystem.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.examples.ecosystem.ecosystem.Ecosystem;
import org.opales.covalent.examples.ecosystem.ecosystem.controller.impl.EcosystemControllerImpl;
import org.opales.covalent.examples.ecosystem.ecosystem.model.impl.EcosystemModelImpl;
import org.opales.covalent.examples.ecosystem.ecosystem.view.impl.EcosystemViewImpl;

/**
 * The Ecosystem composite: deploys its three MVIC parts together. There
 * is no Interaction Controller: EcosystemView consumes
 * EcosystemStateEvents directly, and there is no user action here to
 * route back into EcosystemController.
 */
@Implementation
public class EcosystemImpl implements Ecosystem {

    @Implementation
    private EcosystemModelImpl model;

    @Implementation
    private EcosystemControllerImpl controller;

    @Implementation
    private EcosystemViewImpl view;
}
