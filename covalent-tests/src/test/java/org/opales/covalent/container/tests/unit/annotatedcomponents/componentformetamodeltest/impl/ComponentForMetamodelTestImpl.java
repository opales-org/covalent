/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.impl;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTest;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTestEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTestParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTestServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerSomeEvents;

@Implementation
public class ComponentForMetamodelTestImpl
		implements ComponentForMetamodelTestServices, ComponentForMetamodelTest, ComponentForMetamodelTestParameters {

	@ProducedEvents
	private ComponentForMetamodelTestEvents produceComponentForMetamodelTestEvents;

	// not used, only implemented to check metamodel
	@UsedServices
	private final ConsumerComponentServices useConsumerComponentServices = null;

	// not used, only implemented to check metamodel
	@UsedEventsSubscribe
	private final ProducerSomeEvents.ProducerSomeEventsSubscribe useProducerSomeEventsSubscribe = null;

	@AccessedParameters
	private final FullyConnectedParameters parameters = null;

	@Override
	public void fireEvent(final String value) {
		this.produceComponentForMetamodelTestEvents.onEvent(value);

	}

	@Override
	public void setParameter(final String parameters) {
	}

}
