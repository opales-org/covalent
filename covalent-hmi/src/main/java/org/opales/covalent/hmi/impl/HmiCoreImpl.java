/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.impl;

import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.hmi.HmiCore;
import org.opales.covalent.hmi.action.impl.ActionManagerImpl;

/** Implementation of {@link HmiCore}: composes the {@link ActionManagerImpl} that manages the actions. */
@Implementation
public class HmiCoreImpl implements HmiCore {

	/** Creates the HMI core. */
	public HmiCoreImpl() {
		// The action manager is composed by the container.
	}

	@ComposedComponent
	ActionManagerImpl actionManager;

}
