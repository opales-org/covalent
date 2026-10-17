/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawcomponents.componentwithlifecycleannotations;

import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

public class ComponentWithLifecycleAnnotation {
	
	@Initialization
	public void a() {
		this.state = ComponentState.initialized;
	}
	
	@Activation
	public void b() {
		this.state = ComponentState.activated;
	}
	
	@Passivation
	public void c() {
		this.state = ComponentState.passivated;
	}

	@Removal
	public void d() {
		this.state = ComponentState.removed;
	}

	public ComponentState state;
	
}
