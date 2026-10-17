/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel;

import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;

/**
 * Reports component metamodel changes.
 */
@Events
public interface MetamodelEvents {

	/**
	 * Notifies that a component metamodel has been created.
	 *
	 * @param metamodel created component metamodel
	 */
	void onMetamodelCreated(ComponentMetamodel metamodel);

	/**
	 * Notifies that a component metamodel has been updated.
	 *
	 * @param metamodel updated component metamodel
	 */
	void onMetamodelUpdated(ComponentMetamodel metamodel);

	/**
	 * Notifies that a component metamodel has been deleted.
	 *
	 * @param metamodel deleted component metamodel
	 */
	void onMetamodelDeleted(ComponentMetamodel metamodel);

}
