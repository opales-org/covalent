/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.introspection;

import java.lang.reflect.Field;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Reads component model metadata from classes and fields.
 */
@Services
public interface IntrospectionServices {

	/**
	 * Returns the component type declared by an implementation class.
	 *
	 * @param implementationClass implementation class to inspect
	 * @return declared component type
	 */
	Class<?> getComponentType(final Class<?> implementationClass);

	/**
	 * Reports whether an interface is a services interface.
	 *
	 * @param componentInterface interface to inspect
	 * @return {@code true} when the interface is a services interface
	 */
	boolean isComponentServices(final Class<?> componentInterface);

	/**
	 * Reports whether an interface is a parameters interface.
	 *
	 * @param componentInterface interface to inspect
	 * @return {@code true} when the interface is a parameters interface
	 */
	boolean isComponentParameters(Class<?> componentInterface);

	/**
	 * Reports whether a field is annotated with any Covalent component model
	 * annotation.
	 * 
	 * @param field field to inspect
	 * @return {@code true} when the field is a component model field
	 */
	boolean isComponentField(final Field field);

	/**
	 * Reports whether a class implements an interface.
	 *
	 * @param aClass      class to inspect
	 * @param anInterface interface expected on the class
	 * @return {@code true} when the class implements the interface
	 */
	boolean isImplementing(Class<?> aClass, Class<?> anInterface);

}
