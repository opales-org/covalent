/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * Verifies which lifecycle commands the console offers from each component state, for container
 * components and for raw components, and which commands ask for confirmation.
 */
public class LifecycleCommandTests {

	@Test
	public void unknownStateAllowsNoCommand() {
		for (LifecycleCommand command : LifecycleCommand.values()) {
			Assert.assertFalse(command.name(), command.isAllowedFrom(null));
		}
	}

	@Test
	public void removedComponentCanBeBroughtBackThroughEveryActivationCommand() {
		Assert.assertTrue(LifecycleCommand.INSTANTIATE.isAllowedFrom(ComponentState.removed));
		Assert.assertTrue(LifecycleCommand.INITIALIZE.isAllowedFrom(ComponentState.removed));
		Assert.assertTrue(LifecycleCommand.ACTIVATE.isAllowedFrom(ComponentState.removed));
	}

	@Test
	public void passivateAndRestartApplyOnlyToActivatedComponents() {
		for (ComponentState state : ComponentState.values()) {
			boolean activated = state == ComponentState.activated;
			Assert.assertEquals(state.name(), activated, LifecycleCommand.PASSIVATE.isAllowedFrom(state));
			Assert.assertEquals(state.name(), activated, LifecycleCommand.RESTART.isAllowedFrom(state));
		}
	}

	@Test
	public void resetAppliesOnlyToFailedComponents() {
		for (ComponentState state : ComponentState.values()) {
			Assert.assertEquals(state.name(), state == ComponentState.failed, LifecycleCommand.RESET.isAllowedFrom(state));
		}
	}

	@Test
	public void rawComponentsAreNeverInstantiatedNorInitializedByTheConsole() {
		for (ComponentState state : ComponentState.values()) {
			Assert.assertFalse(LifecycleCommand.INSTANTIATE.isAllowedForRawFrom(state));
			Assert.assertFalse(LifecycleCommand.INITIALIZE.isAllowedForRawFrom(state));
		}
	}

	@Test
	public void rawComponentIsActivatedOnlyFromPassivated() {
		for (ComponentState state : ComponentState.values()) {
			Assert.assertEquals(state.name(), state == ComponentState.passivated,
					LifecycleCommand.ACTIVATE.isAllowedForRawFrom(state));
		}
	}

	@Test
	public void rawComponentFollowsTheGeneralRuleForOtherCommands() {
		for (ComponentState state : ComponentState.values()) {
			Assert.assertEquals(LifecycleCommand.PASSIVATE.isAllowedFrom(state),
					LifecycleCommand.PASSIVATE.isAllowedForRawFrom(state));
			Assert.assertEquals(LifecycleCommand.REMOVE.isAllowedFrom(state),
					LifecycleCommand.REMOVE.isAllowedForRawFrom(state));
		}
		Assert.assertFalse(LifecycleCommand.RESET.isAllowedForRawFrom(null));
	}

	@Test
	public void onlyRemoveAndUndeployAskForConfirmation() {
		for (LifecycleCommand command : LifecycleCommand.values()) {
			boolean destructive = command == LifecycleCommand.REMOVE || command == LifecycleCommand.UNDEPLOY;
			Assert.assertEquals(command.name(), destructive, command.isDestructive());
		}
	}
}
