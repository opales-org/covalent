/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.metamodel.MetamodelEvents;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Verifies that a metamodel update is announced only when the metamodel
 * really changed, that using services or accessing parameters never writes into
 * the metamodel of their provider or offeror, and that withdrawing a services
 * or parameters interface that is not provided or offered is reported according
 * to the dynamic rules checking level instead of failing unconditionally.
 */
public class MetamodelUpdateCoherenceTests {

	public interface SharedServices {
		String hello();
	}

	public interface SharedParameters {
		String getName();
	}

	public static class OwnerA implements SharedServices, SharedParameters {
		@Override
		public String hello() {
			return "hello";
		}

		@Override
		public String getName() {
			return "name";
		}
	}

	public static class OwnerB {
	}

	private final List<Class<?>> updatedTypes = Collections.synchronizedList(new ArrayList<>());

	private CovalentContainer container;
	private RawComponentsServices rcs;
	private MetamodelPortWriteServices portWrite;
	private MetamodelPortReadServices portRead;
	private MetamodelWriteServices metamodelWrite;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
		this.container.start();
		this.rcs.subscribe(MetamodelEvents.class, new MetamodelEvents() {
			@Override
			public void onMetamodelCreated(final ComponentMetamodel metamodel) {
			}

			@Override
			public void onMetamodelUpdated(final ComponentMetamodel metamodel) {
				MetamodelUpdateCoherenceTests.this.updatedTypes.add(metamodel.getComponentType());
			}

			@Override
			public void onMetamodelDeleted(final ComponentMetamodel metamodel) {
			}
		});
		this.portWrite = this.rcs.useServices(MetamodelPortWriteServices.class);
		this.portRead = this.rcs.useServices(MetamodelPortReadServices.class);
		this.metamodelWrite = this.rcs.useServices(MetamodelWriteServices.class);
		this.rcs.registerComponent(new OwnerA());
		this.rcs.registerComponent(new OwnerB());
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	private long updates(final Class<?> componentType) {
		synchronized (this.updatedTypes) {
			return this.updatedTypes.stream().filter(componentType::equals).count();
		}
	}

	@Test
	public void aDeclarationAlreadyRecordedDoesNotAnnounceAnUpdate() {
		this.portWrite.addProvidedServices(OwnerA.class, SharedServices.class);
		long afterFirst = this.updates(OwnerA.class);

		this.portWrite.addProvidedServices(OwnerA.class, SharedServices.class);

		Assert.assertTrue("the first declaration changes the metamodel", afterFirst > 0);
		Assert.assertEquals(afterFirst, this.updates(OwnerA.class));
	}

	@Test
	public void aRejectedDeclarationDoesNotAnnounceAnUpdate() {
		this.portWrite.addProvidedServices(OwnerA.class, SharedServices.class);
		this.portWrite.addOfferedParameters(OwnerA.class, SharedParameters.class);

		this.portWrite.addProvidedServices(OwnerB.class, SharedServices.class);
		this.portWrite.addOfferedParameters(OwnerB.class, SharedParameters.class);

		Assert.assertEquals(0, this.updates(OwnerB.class));
		Assert.assertEquals(OwnerA.class, this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
	}

	@Test
	public void usingServicesOfAProviderThatNoLongerProvidesThemDoesNotDeclareThemAgain() {
		this.rcs.provideServices(OwnerA.class, SharedServices.class);
		this.rcs.unprovideServices(OwnerA.class, SharedServices.class);

		this.rcs.useServices(OwnerA.class, SharedServices.class);

		Assert.assertNull(this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
		Assert.assertFalse(this.metamodelWrite.readMetamodel(OwnerA.class).getProvidedServices()
				.contains(SharedServices.class));
	}

	@Test
	public void accessingParametersOfAnOfferorThatNoLongerOffersThemDoesNotDeclareThemAgain() {
		this.rcs.offerParameters(OwnerA.class, SharedParameters.class);
		this.rcs.unofferParameters(OwnerA.class, SharedParameters.class);

		this.rcs.accessParameters(OwnerA.class, SharedParameters.class);

		Assert.assertNull(this.portRead.getComponentTypeForOfferedParameters(SharedParameters.class));
		Assert.assertFalse(this.metamodelWrite.readMetamodel(OwnerA.class).getOfferedParameters()
				.contains(SharedParameters.class));
	}

	@Test
	public void unprovidingServicesTwiceIsOnlyTracedByDefault() {
		this.rcs.provideServices(OwnerA.class, SharedServices.class);
		this.rcs.unprovideServices(OwnerA.class, SharedServices.class);

		this.rcs.unprovideServices(OwnerA.class, SharedServices.class);

		Assert.assertNull(this.portRead.getComponentTypeForProvidedServices(SharedServices.class));
	}

	@Test
	public void unprovidingServicesTwiceIsRejectedUnderTheStrictLevel() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.rcs.provideServices(OwnerA.class, SharedServices.class);
		this.rcs.unprovideServices(OwnerA.class, SharedServices.class);

		ValidationException failure = Assert.assertThrows(ValidationException.class,
				() -> this.rcs.unprovideServices(OwnerA.class, SharedServices.class));

		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("SharedServices"));
	}

	@Test
	public void unofferingParametersTwiceIsOnlyTracedByDefault() {
		this.rcs.offerParameters(OwnerA.class, SharedParameters.class);
		this.rcs.unofferParameters(OwnerA.class, SharedParameters.class);

		this.rcs.unofferParameters(OwnerA.class, SharedParameters.class);

		Assert.assertNull(this.portRead.getComponentTypeForOfferedParameters(SharedParameters.class));
	}

	@Test
	public void unofferingParametersTwiceIsRejectedUnderTheStrictLevel() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.rcs.offerParameters(OwnerA.class, SharedParameters.class);
		this.rcs.unofferParameters(OwnerA.class, SharedParameters.class);

		ValidationException failure = Assert.assertThrows(ValidationException.class,
				() -> this.rcs.unofferParameters(OwnerA.class, SharedParameters.class));

		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("SharedParameters"));
	}
}
