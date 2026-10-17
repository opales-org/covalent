/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.extension;

import org.opales.covalent.container.CovalentContainer;

/**
 * Runtime extension discovered through the Java service provider mechanism.
 */
public interface CovalentExtension {

	/**
	 * Returns the stable identifier used to activate this extension at runtime.
	 *
	 * @return extension identifier
	 */
	String getId();

	/**
	 * Starts the extension after the Covalent container runtime is fully started.
	 *
	 * @param container started Covalent container
	 */
	void onContainerStarted(CovalentContainer container);

}
