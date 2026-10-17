/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;

/**
 * Reads component metamodels.
 *
 * <p>Operations here are not independently synchronized: safe concurrent
 * access relies entirely on the caller going through
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}, whose
 * per-container lock already serializes the lifecycle transitions that
 * create, update, and delete metamodels - the same contract as
 * {@link org.opales.covalent.container.registry.RegistryReadServices}. A
 * caller that reaches these methods outside any lifecycle operation has no
 * guarantee against concurrent modification.
 */
@Services
public interface MetamodelReadServices {

	/**
	 * Returns the metamodel registered for a component type.
	 *
	 * @param componentType component type to read
	 * @return registered component metamodel
	 */
	ReadOnlyComponentMetamodel readMetamodel(final Class<?> componentType);

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
