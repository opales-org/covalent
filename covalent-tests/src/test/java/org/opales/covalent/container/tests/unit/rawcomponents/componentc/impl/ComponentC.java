/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawcomponents.componentc.impl;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.lifecycle.ComponentLifecycleTransition;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.ComponentCEvents;

/**
 * A plain raw-component fixture producing {@link ComponentCEvents}, used by
 * tests that need a third raw component alongside {@code ComponentA}/
 * {@code ComponentB} but have nothing to do with the HMI action framework.
 * The action-registering variant of this fixture lives in the tests of the
 * {@code covalent-hmi} module instead.
 */
public class ComponentC implements ComponentLifecycleTransition {

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private ComponentCEvents produceComponentCEvents;

	@Override
	public void activate() {
		this.produceComponentCEvents = this.rcs.produceEvents(ComponentC.class, ComponentCEvents.class);
	}

	@Override
	public void passivate() {
		this.rcs.unproduceEvents(ComponentC.class, ComponentCEvents.class);
	}

	public void produceEvents(final boolean produce) {
		if (produce) {
			this.produceComponentCEvents = this.rcs.produceEvents(ComponentC.class, ComponentCEvents.class);
		} else {
			this.rcs.unproduceEvents(ComponentC.class, ComponentCEvents.class);
		}
	}

	public ComponentCEvents getProduceComponentCEvents() {
		return this.produceComponentCEvents;
	}

	@Override
	public void initialize() {

	}

	@Override
	public void remove() {

	}
}
