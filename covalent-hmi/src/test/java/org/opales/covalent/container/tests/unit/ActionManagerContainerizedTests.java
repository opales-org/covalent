/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.ComponentCActionKey;
import org.opales.covalent.container.tests.unit.rawcomponents.componentc.impl.ComponentC;
import org.opales.covalent.hmi.HmiCore;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.hmi.action.ActionEvents;
import org.opales.covalent.hmi.action.ActionObservationServices;
import org.opales.covalent.hmi.impl.HmiCoreImpl;

public class ActionManagerContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	private ActionConsumerServices actionManagerServices;

	@Before
	public void deploy() {
		this.rcs.registerComponent(this);
		this.activateHmiCore();
		this.actionManagerServices = Covalent.main.rcs().useServices(ActionConsumerServices.class);
	}

	@Test
	public void actionsTest() throws InterruptedException {
		ComponentC componentC = new ComponentC();
		this.rcs.registerComponent(componentC);

		this.actionManagerServices.executeAction(ComponentCActionKey.FIRST_ACTION);
		Assert.assertEquals(1, componentC.firstAction);

		this.actionManagerServices.executeAction(ComponentCActionKey.SECOND_ACTION, 42);
		Assert.assertEquals(42, componentC.secondAction);

		AtomicInteger consumerActionResult = new AtomicInteger();
		this.actionManagerServices.executeAction(ComponentCActionKey.THIRD_ACTION, 100,
				(Consumer<Integer>) t -> consumerActionResult.set(t));
		Assert.assertEquals(100, componentC.thirdAction);
		Assert.assertEquals(100, consumerActionResult.get());
	}

	@Test
	public void actionsAfterUnregisterTest() throws InterruptedException {
		ComponentC componentC = new ComponentC();
		this.rcs.registerComponent(componentC);

		componentC.unRegisterActions();

		this.actionManagerServices.executeAction(ComponentCActionKey.FIRST_ACTION);
		Assert.assertEquals(0, componentC.firstAction);

		this.actionManagerServices.executeAction(ComponentCActionKey.SECOND_ACTION, 42);
		Assert.assertEquals(0, componentC.secondAction);

		AtomicInteger consumerActionResult = new AtomicInteger();
		this.actionManagerServices.executeAction(ComponentCActionKey.THIRD_ACTION, 100,
				(Consumer<Integer>) t -> consumerActionResult.set(t));
		Assert.assertEquals(0, componentC.thirdAction);
		Assert.assertEquals(0, consumerActionResult.get());
	}

	@Test
	public void anObserverRegisteredThroughTheServicesReceivesTheInputDataOfTheActions() {
		ComponentC componentC = new ComponentC();
		this.rcs.registerComponent(componentC);
		List<List<Object>> seen = new ArrayList<>();
		ActionEvents observer = (key, caller, implementer, data) -> seen.add(Arrays.asList(data));
		ActionObservationServices observation = Covalent.main.rcs().useServices(ActionObservationServices.class);
		observation.addActionEventsListener(observer);
		try {
			this.actionManagerServices.executeAction(ComponentCActionKey.FIRST_ACTION);
			this.actionManagerServices.executeAction(ComponentCActionKey.SECOND_ACTION, 42);
		} finally {
			observation.removeActionEventsListener(observer);
		}

		Assert.assertEquals(List.of(List.of(), List.of(42)), seen);
	}

	private void activateHmiCore() {
		this.lifecycleServices.deployComponent(HmiCoreImpl.class);
		this.lifecycleServices.activateComponent(HmiCore.class);
	}

}
