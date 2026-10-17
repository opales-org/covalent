/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;

public class ComponentA implements ComponentAServices {

	private ComponentAEvents produceComponentAEvents;

	private final RawComponentsServices rcs;

	public boolean doSomething = false;

	private boolean initialState = false;

	public ComponentA(final RawComponentsServices rcs) {
		this(rcs, false);
	}

	public ComponentA(final RawComponentsServices rcs, final boolean initialState) {
		this.rcs = rcs;
		this.initialState = initialState;
	}

	public ComponentA(final boolean initialState) {
		this(Covalent.main.rcs(), initialState);
	}

	public ComponentA() {
		this(false);
	}

	@Activation
	public void activate() {
		this.rcs.provideServices(ComponentA.class, ComponentAServices.class);
		if (this.initialState) {
			this.produceComponentAEvents = this.rcs.produceEvents(ComponentA.class, ComponentAEvents.class,
					this::onSubscribeA);
		} else {
			this.produceComponentAEvents = this.rcs.produceEvents(ComponentA.class, ComponentAEvents.class);
		}
	}

	@Passivation
	public void passivate() {
		this.rcs.unprovideServices(ComponentA.class, ComponentAServices.class);

		if (this.initialState) {
			this.rcs.unproduceEvents(ComponentA.class, ComponentAEvents.class);
		}
	}

	private void onSubscribeA(final ComponentAEvents obs) {
		obs.onCreate("init");
	}

	public void produceEvents(final boolean produce) {
		if (produce) {
			if (this.initialState) {
				this.produceComponentAEvents = this.rcs.produceEvents(ComponentA.class, ComponentAEvents.class,
						this::onSubscribeA);
			} else {
				this.produceComponentAEvents = this.rcs.produceEvents(ComponentA.class, ComponentAEvents.class);
			}
		} else {
			this.rcs.unproduceEvents(ComponentA.class, ComponentAEvents.class);
		}
	}

	@Override
	public boolean doSomethingWithBoolean(final boolean aValue) {
		return aValue;
	}

	@Override
	public void doSomething() {
		this.doSomething = true;
	}

	@Override
	public String getData() {
		return "A data";
	}

	public ComponentAEvents getProduceComponentAEvents() {
		return this.produceComponentAEvents;
	}

}
