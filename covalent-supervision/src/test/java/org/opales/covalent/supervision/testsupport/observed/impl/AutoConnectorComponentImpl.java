/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.testsupport.observed.AutoConnectorComponent;

/**
 * See {@link AutoConnectorComponent}'s javadoc. {@link #target} is set by the test right before
 * {@code deployComponents(...)}, since there is no other way to hand this fixture a per-test
 * container reference before Covalent instantiates it.
 */
@Implementation
public class AutoConnectorComponentImpl implements AutoConnectorComponent {

	public static volatile CovalentContainer target;

	@UsedServices
	private ObservedContainerControl observedContainerControl;

	@Activation
	public void connect() {
		this.observedContainerControl.connect(target);
	}
}
