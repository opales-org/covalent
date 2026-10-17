/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * Verifies the badge of a leaf from its lifecycle state, the badge of a composite aggregated from
 * its leaves, and the label and palette entry of each status.
 */
public class ComponentStatusTests {

	@Test
	public void labelIsTheNameOfTheStatus() {
		for (ComponentStatus status : ComponentStatus.values()) {
			Assert.assertEquals(status.name(), status.getLabel());
			Assert.assertNotNull(status.getColorKey());
		}
	}

	@Test
	public void activatedAndFailedStatusesUseTheirOwnPaletteEntries() {
		Assert.assertEquals(PortColorKey.STATUS_ACTIVATED, ComponentStatus.ACTIVATED.getColorKey());
		Assert.assertEquals(PortColorKey.STATUS_FAILED, ComponentStatus.FAILED.getColorKey());
		Assert.assertEquals(PortColorKey.STATUS_DEGRADED, ComponentStatus.DEGRADED.getColorKey());
	}

	@Test
	public void unknownLeafStateIsDeployed() {
		Assert.assertEquals(ComponentStatus.DEPLOYED, ComponentStatus.ofLeaf(null));
		Assert.assertEquals(ComponentStatus.DEPLOYED, ComponentStatus.ofLeaf(ComponentState.deployed));
	}

	@Test
	public void everyLeafStateHasItsBadge() {
		Assert.assertEquals(ComponentStatus.INSTANTIATED, ComponentStatus.ofLeaf(ComponentState.instantiated));
		Assert.assertEquals(ComponentStatus.INITIALIZED, ComponentStatus.ofLeaf(ComponentState.initialized));
		Assert.assertEquals(ComponentStatus.ACTIVATED, ComponentStatus.ofLeaf(ComponentState.activated));
		Assert.assertEquals(ComponentStatus.PASSIVATED, ComponentStatus.ofLeaf(ComponentState.passivated));
		Assert.assertEquals(ComponentStatus.REMOVED, ComponentStatus.ofLeaf(ComponentState.removed));
		Assert.assertEquals(ComponentStatus.FAILED, ComponentStatus.ofLeaf(ComponentState.failed));
	}

	@Test
	public void compositeWithoutLeavesIsMixed() {
		Assert.assertEquals(ComponentStatus.MIXED, ComponentStatus.aggregate(List.of()));
	}

	@Test
	public void compositeWhoseLeavesAllFailedIsFailed() {
		Assert.assertEquals(ComponentStatus.FAILED,
				ComponentStatus.aggregate(List.of(ComponentState.failed, ComponentState.failed)));
	}

	@Test
	public void compositeWithSomeFailedLeavesIsDegraded() {
		Assert.assertEquals(ComponentStatus.DEGRADED,
				ComponentStatus.aggregate(List.of(ComponentState.failed, ComponentState.activated)));
	}

	@Test
	public void compositeWhoseLeavesAreAllActivatedIsActivated() {
		Assert.assertEquals(ComponentStatus.ACTIVATED,
				ComponentStatus.aggregate(List.of(ComponentState.activated, ComponentState.activated)));
	}

	@Test
	public void compositeWhoseLeavesAreAllPassivatedIsPassivated() {
		Assert.assertEquals(ComponentStatus.PASSIVATED,
				ComponentStatus.aggregate(List.of(ComponentState.passivated)));
	}

	@Test
	public void compositeWithLeavesInDifferentStatesIsMixed() {
		Assert.assertEquals(ComponentStatus.MIXED,
				ComponentStatus.aggregate(List.of(ComponentState.activated, ComponentState.passivated)));
	}
}
