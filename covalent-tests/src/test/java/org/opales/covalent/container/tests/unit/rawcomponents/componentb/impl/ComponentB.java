/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawcomponents.componentb.impl;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.ComponentCEvents;

public class ComponentB implements ComponentAEvents, ComponentCEvents {
	
	protected RawComponentsServices rcs = Covalent.main.rcs();
	
	public int onCreate = 0;
	
	public int onUpdate = 0;
	
	public int onDelete = 0;
	
	public int onEmpty = 0;
	
	public int onCEvent = 0;
	
	private ComponentAServices useComponentAServices;
	
	@Activation
	public void activate() {
		this.useComponentAServices = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
	}
	
	public void consumeEventA(final boolean consume) {
		if (consume) {
			this.rcs.subscribe(ComponentB.class, ComponentAEvents.class, this);
		} else {
			this.rcs.unsubscribe(ComponentB.class, ComponentAEvents.class, this);
		}
	}
	
	public void consumeEventC(final boolean consume) {
		if (consume) {
			this.rcs.subscribe(ComponentB.class, ComponentCEvents.class, this);
		} else {
			this.rcs.unsubscribe(ComponentB.class, ComponentCEvents.class, this);
		}
	}
	
	@Passivation
	public void passivate() {
		this.rcs.unsubscribe(ComponentB.class, ComponentAEvents.class, this);
		this.rcs.unsubscribe(ComponentB.class, ComponentCEvents.class, this);
	}
	
	@Override
	public void onCreate(final String data) {
		this.onCreate++;
	}
	
	@Override
	public void onUpdate(final String data) {
		this.onUpdate++;
	}
	
	@Override
	public void onDelete(final String data) {
		this.onDelete++;
	}
	
	@Override
	public void onEmpty() {
		this.onEmpty++;
	}
	
	@Override
	public void onCEvent(final int value) {
		this.onCEvent++;
	}
	
	public ComponentAServices getUseComponentAServices() {
		return this.useComponentAServices;
	}
	
}
