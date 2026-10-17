/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.rr.services.ServicesManager;

/**
 * <p>
 * The parameters manager handles Covalent parameters. Parameters use the same
 * request-response semantics as services (see {@link ServicesManager}. However,
 * the goal is different.
 * </p>
 * 
 * <p>
 * Parameters are used to adapt the component to its current usage context.
 * Allowing a component to be parameterized is a great way to improve its
 * reusability.
 * </p>
 * 
 * <p>
 * While with services, the benefit is for the caller, using a parameters
 * interface provides a benefit to the callee. The caller is only helping the
 * component which implements the parameters interface, by setting its
 * parameters.
 * </p>
 */
@Internal
@Component
public interface ParametersManager {
	// Marker interface: the internal component implemented by
	// org.opales.covalent.container.rr.parameters.impl.ParametersManagerImpl,
	// whose operations are exposed through the ParametersManagerServices
	// port declared below.
}
