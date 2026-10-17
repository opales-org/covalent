/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;

/**
 * Mirror of {@link org.opales.covalent.container.metamodel.MetamodelReadServices},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original.
 */
@Services
public interface ObservedMetamodelReadServices {

	/**
	 * Returns the metamodel registered for a component type.
	 *
	 * @param componentType component type to read
	 * @return registered component metamodel
	 */
	ReadOnlyComponentMetamodel readMetamodel(Class<?> componentType);

	/**
	 * Returns the component type associated with an implementation class.
	 *
	 * @param <CT>           component type marker
	 * @param <CI>           component implementation type
	 * @param implementation implementation class to resolve
	 * @return component type associated with the implementation
	 */
	<CT, CI> Class<CT> getType(Class<CI> implementation);

	/**
	 * Returns the implementation class associated with a component type.
	 *
	 * @param <CT>          component type marker
	 * @param <CI>          component implementation type
	 * @param componentType component type to resolve
	 * @return implementation class associated with the component type
	 */
	<CT, CI> Class<CI> getImplementation(Class<CT> componentType);

}
