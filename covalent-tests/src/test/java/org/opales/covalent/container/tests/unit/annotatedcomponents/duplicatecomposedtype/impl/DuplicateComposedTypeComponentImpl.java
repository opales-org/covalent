/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.duplicatecomposedtype.impl;

import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicatecomposedtype.DuplicateComposedTypeComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.duplicatecomposedtype.DuplicateComposedTypeComponentServices;

/**
 * Declares two {@code @ComposedComponent} fields of the exact same
 * implementation type (CounterComponentImpl), each meant to be a fully
 * independent instance. This is the composition-axis analog of
 * DuplicateItemTypeComponentImpl (see DuplicateItemTypeFieldsTests):
 * ComponentMetamodelImpl indexes composed-component fields the same way
 * it indexes item fields - by the field's declared class, in a
 * {@code Map<Class<?>, Field>} (composedComponentFields /
 * addComposedComponentField) - so the second field's registration
 * overwrites the first's, and only one of the two fields ever receives a
 * live instance.
 */
@Implementation
public class DuplicateComposedTypeComponentImpl
		implements DuplicateComposedTypeComponent, DuplicateComposedTypeComponentServices {

	@ComposedComponent
	private CounterComponentImpl firstCounter;

	@ComposedComponent
	private CounterComponentImpl secondCounter;

	@Override
	public void incrementFirst() {
		this.firstCounter.increment();
	}

	@Override
	public void incrementSecond() {
		this.secondCounter.increment();
	}

	@Override
	public int getFirstCount() {
		return this.firstCounter.getCount();
	}

	@Override
	public int getSecondCount() {
		return this.secondCounter.getCount();
	}
}
