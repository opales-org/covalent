/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawcomponents.componentc.impl;

import java.util.function.Consumer;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.lifecycle.ComponentLifecycleTransition;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.ComponentCActionKey;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.ComponentCEvents;
import org.opales.covalent.hmi.action.ActionProviderServices;

public class ComponentC implements ComponentLifecycleTransition {

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private ComponentCEvents produceComponentCEvents;

	private ActionProviderServices actionManagerServices;

	public int firstAction = 0;

	public int secondAction = 0;

	public int thirdAction = 0;

	@Override
	public void activate() {
		this.actionManagerServices = this.rcs.useServices(ActionProviderServices.class);
		this.produceComponentCEvents = this.rcs.produceEvents(ComponentC.class, ComponentCEvents.class);

		this.actionManagerServices.registerActionExecution(ComponentCActionKey.FIRST_ACTION, this::firstAction);
		this.actionManagerServices.registerActionExecution(ComponentCActionKey.SECOND_ACTION, this::secondAction,
				Integer.class);
		this.actionManagerServices.registerActionExecution(ComponentCActionKey.THIRD_ACTION, this::thirdAction,
				Integer.class, Consumer.class);
	}

	public void firstAction() {
		this.firstAction++;
	}

	public void secondAction(final Integer value) {
		this.secondAction = value;
	}

	public void thirdAction(final Integer value, final Consumer<Integer> consumer) {
		this.thirdAction = value;
		consumer.accept(value);
	}

	public void unRegisterActions() {

		this.actionManagerServices.unregisterActionExecution(ComponentCActionKey.FIRST_ACTION);
		this.actionManagerServices.unregisterActionExecution(ComponentCActionKey.SECOND_ACTION);
		this.actionManagerServices.unregisterActionExecution(ComponentCActionKey.THIRD_ACTION);
	}

	@Override
	public void passivate() {
		this.actionManagerServices.unregisterActionExecution(ComponentCActionKey.FIRST_ACTION);
		this.actionManagerServices.unregisterActionExecution(ComponentCActionKey.SECOND_ACTION);
		this.actionManagerServices.unregisterActionExecution(ComponentCActionKey.THIRD_ACTION);

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
