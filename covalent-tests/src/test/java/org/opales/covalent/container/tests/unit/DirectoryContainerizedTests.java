/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.exceptions.NotBoundException;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;

public class DirectoryContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@Test
	public void lookupOfAbsentDirectoryEntriesFailsConsistently() {
		NamingServices namingServices = this.rcs.useServices(NamingServices.class);

		Assert.assertFalse(namingServices.isServicesBound(ProducerServices.class));
		Assert.assertThrows(NotBoundException.class, () -> namingServices.lookupServices(ProducerServices.class));

		Assert.assertFalse(namingServices.isParametersBound(ProducerParameters.class));
		Assert.assertThrows(NotBoundException.class, () -> namingServices.lookupParameters(ProducerParameters.class));

		Assert.assertFalse(namingServices.isSubscriberBound(ProducerEvents.class));
		Assert.assertThrows(NotBoundException.class, () -> namingServices.lookupSubscriber(ProducerEvents.class));
	}

}
