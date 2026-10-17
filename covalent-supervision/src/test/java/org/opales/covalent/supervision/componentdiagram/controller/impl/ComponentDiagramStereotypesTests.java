/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.stereotypepolicy.BuiltInStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.MetamodelStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypePolicyControllerServices;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersAccessorComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersAccessorComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersOfferorComponentImpl;

/**
 * What each card says it stands for, and the implementation behind its type: the stereotype the table gives the component's name or, when
 * no name ending matches, what the metamodel says - and that a change of the table reaches the
 * topology the tabs show.
 */
public class ComponentDiagramStereotypesTests extends ObservedComponentDiagramTestSupport {

	@Test
	public void aComponentWithNoMatchingEndingStandsForWhatTheMetamodelSays() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		this.startBoth();

		Assert.assertEquals(MetamodelStereotypes.COMPONENT,
				this.lastTopology().getStereotype(FlatParametersOfferorComponent.class));
	}

	@Test
	public void aComponentTakesTheStereotypeOfTheEndingOfItsName() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class,
				FlatParametersAccessorComponentImpl.class);
		this.startBoth();

		this.stereotypeServices().setRules(this.withRule(new StereotypeRule("OfferorComponent", "Offeror", false)));

		Assert.assertEquals("a name ending like a rule takes its stereotype", "Offeror",
				this.lastTopology().getStereotype(FlatParametersOfferorComponent.class));
		Assert.assertEquals("another name is not affected", MetamodelStereotypes.COMPONENT,
				this.lastTopology().getStereotype(FlatParametersAccessorComponent.class));
	}

	@Test
	public void theLongestEndingWinsOnACard() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		this.startBoth();

		this.stereotypeServices().setRules(this.withRule(new StereotypeRule("Component", "Comp", false),
				new StereotypeRule("OfferorComponent", "Offeror", false)));

		Assert.assertEquals("Offeror", this.lastTopology().getStereotype(FlatParametersOfferorComponent.class));
	}

	@Test
	public void removingARuleGivesTheCardBackWhatTheMetamodelSays() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		this.startBoth();
		this.stereotypeServices().setRules(this.withRule(new StereotypeRule("OfferorComponent", "Offeror", false)));
		Assert.assertEquals("Offeror", this.lastTopology().getStereotype(FlatParametersOfferorComponent.class));

		this.stereotypeServices().setRules(BuiltInStereotypes.rules());

		Assert.assertEquals(MetamodelStereotypes.COMPONENT,
				this.lastTopology().getStereotype(FlatParametersOfferorComponent.class));
	}

	@Test
	public void aCardKnowsTheImplementationBehindItsType() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		this.startBoth();

		Assert.assertEquals(FlatParametersOfferorComponentImpl.class,
				this.lastTopology().getImplementation(FlatParametersOfferorComponent.class));
		Assert.assertNull("an unknown component has none", this.lastTopology().getImplementation(String.class));
	}

	private StereotypePolicyControllerServices stereotypeServices() {
		return this.supervisionRcs.useServices(StereotypePolicyControllerServices.class);
	}

	private List<StereotypeRule> withRule(final StereotypeRule... added) {
		List<StereotypeRule> rules = new ArrayList<>(BuiltInStereotypes.rules());
		rules.addAll(List.of(added));
		return rules;
	}
}
