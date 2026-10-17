/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.supervision.observed.ObservedMetamodelReadServices;
import org.opales.covalent.supervision.testsupport.observed.StubObservedMetamodelComponent;

@Implementation
public class StubObservedMetamodelComponentImpl implements StubObservedMetamodelComponent, ObservedMetamodelReadServices {

	@Override
	public ReadOnlyComponentMetamodel readMetamodel(final Class<?> componentType) {
		return null;
	}

	@Override
	public <CT, CI> Class<CT> getType(final Class<CI> implementation) {
		return null;
	}

	@Override
	public <CT, CI> Class<CI> getImplementation(final Class<CT> componentType) {
		return null;
	}
}
