/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * Covalent injects port fields, and the fields of the container's own
 * composition, by reflection after construction. A {@code final} field cannot
 * be written reliably by reflection (recent JDKs warn about it, then refuse
 * it), so the fields Covalent injects in its own components are not declared
 * {@code final}.
 */
public class InjectedFieldsNotFinalTests {

	private static final String BASE = "org.opales.covalent.container.";

	@Test
	public void portFieldsOfTheInternalComponentsAreNotFinal() throws ClassNotFoundException {
		List<String> finalFields = new ArrayList<>();
		for (String className : new String[] { "impl.CovalentContainerImpl", "events.impl.EventsManagerImpl",
				"rr.services.impl.ServicesManagerImpl", "rr.parameters.impl.ParametersManagerImpl" }) {
			this.collectFinalFields(finalFields, Class.forName(BASE + className), ProducedEvents.class,
					UsedServices.class, AccessedParameters.class, UsedEventsSubscribe.class);
		}
		Assert.assertEquals("final port fields: " + finalFields, 0, finalFields.size());
	}

	@Test
	public void composedFieldsOfTheContainerAreNotFinal() throws ClassNotFoundException {
		List<String> finalFields = new ArrayList<>();
		this.collectFinalFields(finalFields, Class.forName(BASE + "impl.CovalentContainerImpl"),
				ComposedComponent.class, Item.class);
		Assert.assertEquals("final composed fields: " + finalFields, 0, finalFields.size());
	}

	@SafeVarargs
	private final void collectFinalFields(final List<String> result, final Class<?> owner,
			final Class<? extends Annotation>... annotations) {
		for (Field field : owner.getDeclaredFields()) {
			for (Class<? extends Annotation> annotation : annotations) {
				if (field.isAnnotationPresent(annotation) && Modifier.isFinal(field.getModifiers())) {
					result.add(owner.getSimpleName() + "." + field.getName());
					break;
				}
			}
		}
	}
}
