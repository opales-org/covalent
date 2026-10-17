/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.controller.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.hmi.HmiCore;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersOfferorComponentImpl;

/**
 * {@code isExcluded(Class)} is a pure, side-effect-free predicate reading only this
 * Controller's own local fields ({@code excludedComponents}/{@code excludeInternal}) - no
 * container, no deployment, needed to test it directly. {@code setExcludedComponents}/{@code
 * addExcludedComponent}/{@code setExcludeInternalComponents} DO call through {@code
 * useModelServices}, which is left un-wired here (a Covalent-managed field on a plain
 * {@code new}-constructed instance, never autowired): those three mutators are instead covered
 * in {@link ExclusionPolicyControllerImplMutatorsTests}, deployed for real.
 */
public class ExclusionPolicyControllerImplTests {

	private ExclusionPolicyControllerImpl controller;

	@Before
	public void setUp() {
		this.controller = new ExclusionPolicyControllerImpl();
	}

	@Test
	public void nullComponentTypeIsAlwaysExcluded() {
		Assert.assertTrue(this.controller.isExcluded(null));
	}

	@Test
	public void ordinaryComponentIsNotExcludedByDefault() {
		Assert.assertFalse(this.controller.isExcluded(FlatParametersOfferorComponent.class));
		Assert.assertFalse(this.controller.isExcluded(FlatParametersOfferorComponentImpl.class));
	}

	@Test
	public void internalAnnotatedComponentIsExcludedByDefault() {
		Assert.assertTrue(this.controller.isExcluded(LifecycleManager.class));
	}

	@Test
	public void hmiCoreImplIsExcludedByDefault() {
		// Part of the default excludedComponents list, independently of the @Internal check -
		// see the class javadoc on ExclusionPolicyControllerImpl for why.
		Assert.assertTrue(this.controller.isExcluded(HmiCore.class));
	}

	// setExcludedComponents/addExcludedComponent/setExcludeInternalComponents all call through
	// to this.useModelServices (a @UsedServices field) - null on a bare `new`-constructed
	// instance outside any container (unlike a deployed component, where the container installs
	// a non-throwing surrogate before any business code can run), so exercising them needs a
	// real deployment - see ExclusionPolicyControllerImplMutatorsTests.
}
