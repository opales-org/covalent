/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentItemMetamodelImpl;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentMetamodelImpl;

/**
 * Verifies that metamodels answer their ports and fields in a stable order
 * (the declaration order), not in the order of an identity-hash based map, and
 * that an interface reachable through several paths is recorded once.
 */
public class MetamodelDeterminismTests {

	/** Distinct field types, declared in an order unrelated to any hash. */
	@SuppressWarnings("unused")
	private static class Fields {
		String a;
		Integer b;
		Long c;
		Short d;
		Byte e;
		Character f;
		Boolean g;
		Float h;
		Double i;
		List<?> j;
		Map<?, ?> k;
		Set<?> l;
		Runnable m;
		Thread n;
		Number o;
		Class<?> p;
	}

	@Services
	public interface Greeting {
		void greet();
	}

	public interface Left extends Greeting {
	}

	public interface Right extends Greeting {
	}

	@Component
	public interface DiamondComponent extends Left, Right {
	}

	@Implementation
	public static class DiamondComponentImpl implements DiamondComponent {
		@Override
		public void greet() {
		}
	}

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
	}

	private static List<Field> declaredFields() {
		return new ArrayList<>(Arrays.asList(Fields.class.getDeclaredFields()));
	}

	@Test
	public void anItemMetamodelAnswersItsFieldsInInsertionOrder() {
		ComponentItemMetamodelImpl metamodel = new ComponentItemMetamodelImpl();
		List<Field> fields = declaredFields();
		for (Field field : fields) {
			metamodel.addUsedServicesField(field.getType(), field);
			metamodel.addProducedEventsField(field.getType(), field);
			metamodel.addUsedSubscriberField(field.getType(), field);
			metamodel.addAccessedParametersField(field.getType(), field);
		}

		Assert.assertEquals(fields, metamodel.getUsedServicesFields());
		Assert.assertEquals(fields, metamodel.getProducedEventsFields());
		Assert.assertEquals(fields, metamodel.getUsedSubscriberFields());
		Assert.assertEquals(fields, metamodel.getAccessedParametersFields());
	}

	@Test
	public void aComponentMetamodelAnswersItsItemAndComposedFieldsInInsertionOrder() {
		ComponentMetamodelImpl metamodel = new ComponentMetamodelImpl();
		List<Field> fields = declaredFields();
		for (Field field : fields) {
			metamodel.addItemField(field.getType(), field);
			metamodel.addComposedComponentField(field.getType(), field);
		}

		Assert.assertEquals(fields, metamodel.getItemsFields());
		Assert.assertEquals(fields, metamodel.getComposedComponentsFields());
	}

	@Test
	public void anInterfaceReachedThroughSeveralPathsIsRecordedOnce() {
		this.container.deployComponents(DiamondComponentImpl.class);

		ComponentMetamodel metamodel = this.container.rcs().useServices(MetamodelWriteServices.class)
				.readMetamodel(DiamondComponent.class);

		Assert.assertEquals(List.of(Greeting.class), metamodel.getProvidedServices());
	}
}
