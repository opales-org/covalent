/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;

/**
 * Creates, updates, deletes, and reads component metamodels.
 *
 * <p>Same concurrency contract as {@link MetamodelReadServices}: not
 * independently synchronized, relies on the caller going through
 * {@link org.opales.covalent.container.lifecycle.LifecycleServices}'s
 * per-container lock.
 */
@Services
public interface MetamodelWriteServices extends MetamodelReadServices {
	/**
	 * Returns the mutable metamodel registered for a component type.
	 *
	 * @param componentType component type to read
	 * @return registered component metamodel
	 */
	@Override
	ComponentMetamodel readMetamodel(final Class<?> componentType);

	/**
	 * Creates a metamodel for one managed component and its items. Declared
	 * composed implementations are identified but their metamodels are not created.
	 * An existing parent declaring this implementation is linked before notification.
	 * 
	 * @param componentType  component type represented by the metamodel
	 * @param implementation implementation class described by the metamodel
	 * @return created component metamodel
	 */
	ComponentMetamodel createMetamodel(final Class<?> componentType, final Class<?> implementation);

	/**
	 * Creates a metamodel for an externally owned raw component instance.
	 *
	 * @param componentType  raw component type represented by the metamodel
	 * @param implementation raw component implementation class
	 * @return created raw component metamodel
	 */
	ComponentMetamodel createRawMetamodel(final Class<?> componentType, final Class<?> implementation);

	/**
	 * Updates an existing component metamodel.
	 *
	 * @param metamodel metamodel to store
	 */
	void updateMetamodel(ComponentMetamodel metamodel);

	/**
	 * Deletes only the metamodel registered for a component type and detaches it
	 * from its parent. The caller must remove child metamodels first.
	 *
	 * @param componentType component type whose metamodel is deleted
	 * @return deleted component metamodel
	 */
	ComponentMetamodel deleteMetamodel(final Class<?> componentType);

	/**
	 * Resequences {@code parentComponentType}'s composed children to match
	 * {@code order} (implementation classes, as returned by
	 * {@link ComponentMetamodel#getComposedTypes()}), both as declared
	 * types and as resolved metamodels - without adding, removing, or
	 * losing any child.
	 *
	 * @param parentComponentType the composite whose children are reordered
	 * @param order                the children's implementation classes, in
	 *                              the desired order
	 * @throws IllegalArgumentException if {@code order} is not a permutation
	 *                                    of the parent's current composed types
	 */
	void reorderComposedChildren(final Class<?> parentComponentType, final List<Class<?>> order);

}
