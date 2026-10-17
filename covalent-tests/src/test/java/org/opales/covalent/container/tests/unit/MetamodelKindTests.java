/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentMetamodelImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;

/** What a component metamodel tells about the kind of component it describes. */
public class MetamodelKindTests {

	/** An implementation that is its own component type, with no annotated interface. */
	@Implementation
	public static class SelfTypedImplementation {
	}

	/** A class the container knows nothing about. */
	public static class PlainObject {
	}

	private static ComponentMetamodelImpl metamodel(final Class<?> type, final Class<?> implementation) {
		ComponentMetamodelImpl metamodel = new ComponentMetamodelImpl();
		metamodel.setComponentType(type);
		metamodel.setImplementation(implementation);
		return metamodel;
	}

	@Test
	public void aComponentWhoseTypeIsAnnotatedIsManaged() {
		Assert.assertFalse(metamodel(SimpleProducer.class, SimpleProducerImpl.class).isRaw());
	}

	@Test
	public void anAnnotatedImplementationWithoutAnnotatedTypeIsManaged() {
		Assert.assertFalse(metamodel(SelfTypedImplementation.class, SelfTypedImplementation.class).isRaw());
	}

	@Test
	public void aClassWithoutComponentAnnotationsIsRaw() {
		Assert.assertTrue(metamodel(PlainObject.class, PlainObject.class).isRaw());
	}

	@Test
	public void aRawComponentWithoutImplementationIsRaw() {
		Assert.assertTrue(metamodel(PlainObject.class, null).isRaw());
	}

	@Test
	public void aComponentIsCompositeWhenOthersAreComposedIntoIt() {
		ComponentMetamodelImpl metamodel = metamodel(SimpleProducer.class, SimpleProducerImpl.class);
		Assert.assertFalse(metamodel.isComposite());
		metamodel.setComposedTypes(List.of(SelfTypedImplementation.class));
		Assert.assertTrue(metamodel.isComposite());
	}
}
