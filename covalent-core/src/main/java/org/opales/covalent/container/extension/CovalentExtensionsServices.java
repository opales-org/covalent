/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.extension;

import java.util.List;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Services;

/**
 * What the container asks of its extensions manager once it has started.
 */
@Services
public interface CovalentExtensionsServices {

	/**
	 * Starts requested extensions for a started container. An extension that
	 * fails does not prevent the others from starting: its failure is logged
	 * and its identifier reported in the result.
	 *
	 * @param container started Covalent container
	 * @return the startup duration and the identifiers of the extensions that
	 *         failed to start
	 */
	ExtensionsStartup startRequestedExtensions(CovalentContainer container);

	/**
	 * The outcome of starting the requested extensions.
	 *
	 * @param durationSeconds    extension startup duration in seconds, or
	 *                           {@code 0} when no extension was requested
	 * @param failedExtensionIds identifiers of the extensions that failed to
	 *                           start, empty when none failed
	 */
	record ExtensionsStartup(float durationSeconds, List<String> failedExtensionIds) {
	}

}
