/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.controller.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyModelServices;

/**
 * Verifies, with a hand-written fake Model, that {@link StereotypePolicyControllerImpl} resolves
 * stereotypes from its own copy of the rule table, writes every change through to the Model and
 * forwards the state publication.
 */
public class StereotypePolicyControllerImplTests {

	private static final class OrderService {
	}

	private final List<List<StereotypeRule>> written = new ArrayList<>();
	private int published;
	private StereotypePolicyControllerImpl controller;

	@Before
	public void setUp() throws Exception {
		this.controller = new StereotypePolicyControllerImpl();
		Field field = StereotypePolicyControllerImpl.class.getDeclaredField("useModelServices");
		field.setAccessible(true);
		field.set(this.controller, new StereotypePolicyModelServices() {
			@Override
			public void setRules(final List<StereotypeRule> rules) {
				StereotypePolicyControllerImplTests.this.written.add(rules);
			}

			@Override
			public void publishState() {
				StereotypePolicyControllerImplTests.this.published++;
			}
		});
	}

	@Test
	public void nullTypeHasNoStereotype() {
		Assert.assertNull(this.controller.stereotypeFor(null));
	}

	@Test
	public void replacedRulesDecideTheStereotypeAndAreWrittenToTheModel() {
		StereotypeRule rule = new StereotypeRule("Service", "svc", false);
		List<StereotypeRule> rules = new ArrayList<>(List.of(rule));
		this.controller.setRules(rules);
		rules.clear();
		Assert.assertEquals("svc", this.controller.stereotypeFor(OrderService.class));
		Assert.assertEquals(1, this.written.size());
		Assert.assertEquals(List.of(rule), this.written.get(0));
	}

	@Test
	public void emptyRulesGiveNoStereotype() {
		this.controller.setRules(List.of());
		Assert.assertNull(this.controller.stereotypeFor(OrderService.class));
	}

	@Test
	public void publishStateIsForwardedToTheModel() {
		this.controller.publishState();
		Assert.assertEquals(1, this.published);
	}
}
