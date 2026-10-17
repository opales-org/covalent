/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentItemMetamodelImpl;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentMetamodelImpl;

/**
 * A metamodel can be printed before its implementation is set.
 */
public class MetamodelToStringTests {

	@Test
	public void anItemMetamodelWithoutImplementationCanBePrinted() {
		Assert.assertNotNull(new ComponentItemMetamodelImpl().toString());
	}

	@Test
	public void aComponentMetamodelWithoutImplementationCanBePrinted() {
		Assert.assertNotNull(new ComponentMetamodelImpl().toString());
	}
}
