/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected;

import org.opales.covalent.container.component.annotations.Services;

@Services
public interface FullyConnectedServices {
	public boolean checkProvidedServices();
	
	public boolean checkUsedServices();
	
	public boolean checkProducedEvents();
	
	public boolean checkConsumedEvents();
	
	public boolean checkProvidedSubscribe();
	
	public boolean checkUsedSubscribe();
	
	public void reset();
	
}
