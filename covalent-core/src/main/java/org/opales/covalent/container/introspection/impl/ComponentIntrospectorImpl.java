/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.introspection.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.introspection.Introspector;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Internal component backing {@link Introspector}/{@link IntrospectionServices}:
 * answers structural questions about a component's implementation class and
 * its annotated members (its {@code @Component} type, whether an interface
 * is a services/parameters interface, whether a field carries a Covalent
 * port annotation) by reflecting over the class hierarchy.
 */
@Implementation
public class ComponentIntrospectorImpl implements Introspector, IntrospectionServices {

	/** Creates a new instance with no internal services wired yet. */
	public ComponentIntrospectorImpl() {
	}

	@UsedServices
	private ValidationServices useValidationServices;

	@Override
	public Class<?> getComponentType(final Class<?> implementation) {
		Class<?> componentType = null;
		List<Class<?>> componentTypes = new ArrayList<>(
				this.findComponentType(implementation, ComponentAnnotations::isComponent));
		if (componentTypes.size() > 1) {
			String message = "More than one @" + Component.class.getSimpleName()
					+ " interface found for component impl " + implementation.getSimpleName();
			this.useValidationServices.handleStructuralVerification(message);
			componentType = componentTypes.iterator().next();
		} else if (componentTypes.size() == 1) {
			componentType = componentTypes.iterator().next();
		}
		if (componentType == null) {
			return implementation;
		}
		return componentType;
	}

	private Set<Class<?>> findComponentType(final Class<?> classOrInterface, final Predicate<Class<?>> predicate) {
		List<Class<?>> componentInterfaces = Arrays.asList(classOrInterface.getInterfaces());
		Set<Class<?>> interfacesFromType = new HashSet<>();
		for (Class<?> componentInterface : componentInterfaces) {
			if (predicate.test(componentInterface)) {
				interfacesFromType.add(componentInterface);
			}
			// recurse in super-interfaces, so that a component type is also found
			// through an interface extending it
			interfacesFromType.addAll(this.findComponentType(componentInterface, predicate));
		}
		// recurse in superclass
		Class<?> superClass = classOrInterface.getSuperclass();
		if (superClass != null && !Object.class.equals(superClass)) {
			interfacesFromType.addAll(this.findComponentType(superClass, predicate));
		}

		return interfacesFromType;
	}

	@Override
	public boolean isComponentServices(final Class<?> componentInterface) {
		return ComponentAnnotations.isServices(componentInterface);
	}

	@Override
	public boolean isComponentParameters(final Class<?> componentInterface) {
		return ComponentAnnotations.isParameters(componentInterface);
	}

	/**
	 * <p>
	 * Returns true if the field is annotated as an item, a services, a
	 * subscribe or an events field.
	 *
	 * @param field the field to check
	 */
	@Override
	public boolean isComponentField(final Field field) {
		boolean isComponentField = ComponentAnnotations.isItemField(field)
				|| ComponentAnnotations.isServicesField(field) || ComponentAnnotations.isSubscribeField(field)
				|| ComponentAnnotations.isEventsField(field);
		return isComponentField;
	}

	@Override
	public boolean isImplementing(final Class<?> aClass, final Class<?> anInterface) {
		return Arrays.asList(aClass.getInterfaces()).contains(anInterface);
	}

}
