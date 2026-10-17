/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.common;

import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;

/**
 * Base class for the request/response ({@code RR}) flavor-specific
 * lifecycle managers (one for services, one for parameters), holding the
 * two internal services shared by both: metamodel port reads and naming.
 * Wired into an {@link RRManagerImpl} via
 * {@link RRManagerImpl#setLifecycleManager}.
 */
public abstract class RRLifecycleManagerImpl {

	/** Internal service used to read the metamodel's port information. */
	@UsedServices
	protected MetamodelPortReadServices MetamodelPortReadServices;

	/** Internal service used to resolve component naming. */
	@UsedServices
	protected NamingServices namingServices;

	/**
	 * Creates a new RR lifecycle manager.
	 */
	public RRLifecycleManagerImpl() {
	}

	/**
	 * Sets the internal service used to read the metamodel's port
	 * information.
	 *
	 * @param MetamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices MetamodelPortReadServices) {
		this.MetamodelPortReadServices = MetamodelPortReadServices;
	}

	/**
	 * Sets the internal service used to resolve component naming.
	 *
	 * @param namingServices the naming service
	 */
	public void setNamingServices(final NamingServices namingServices) {
		this.namingServices = namingServices;
	}

}
