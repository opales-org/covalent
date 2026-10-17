/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.extension.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.extension.CovalentExtension;
import org.opales.covalent.container.extension.CovalentExtensionsManager;
import org.opales.covalent.container.extension.CovalentExtensionsServices;
import org.opales.covalent.container.util.CovalentDiagnostics;

/**
 * Default manager for optional runtime extensions.
 */
@Implementation
public class CovalentExtensionsManagerImpl implements CovalentExtensionsManager, CovalentExtensionsServices {

	private static final Logger log = Logger.getLogger(CovalentExtensionsManagerImpl.class.getName());

	/**
	 * System property listing the comma-separated ids of extensions to start,
	 * e.g. {@code -Dcovalent.extensions=metrics,tracing}.
	 */
	private static final String EXTENSIONS_PROPERTY = "covalent.extensions";

	/**
	 * Creates a new extensions manager.
	 */
	public CovalentExtensionsManagerImpl() {
	}

	@Override
	public ExtensionsStartup startRequestedExtensions(final CovalentContainer container) {
		long startNanos = System.nanoTime();
		Set<String> requestedExtensions = this.readRequestedExtensions();
		if (requestedExtensions.isEmpty()) {
			return new ExtensionsStartup(0f, List.of());
		}
		List<String> failedExtensionIds = new ArrayList<>();

		Map<String, CovalentExtension> discoveredExtensions = this.discoverExtensions();
		for (String extensionId : requestedExtensions) {
			CovalentExtension extension = discoveredExtensions.get(extensionId);
			if (extension == null) {
				log.warning("Requested Covalent extension not found on classpath: " + extensionId + ".");
				continue;
			}
			try {
				extension.onContainerStarted(container);
			} catch (VirtualMachineError fatal) {
				throw fatal;
			} catch (RuntimeException | Error failure) {
				// An optional extension must not prevent the others, nor the container, from starting.
				failedExtensionIds.add(extensionId);
				CovalentDiagnostics.severe(log, "Covalent extension " + extensionId + " failed to start.", failure);
			}
		}

		return new ExtensionsStartup((System.nanoTime() - startNanos) / 1_000_000_000f, failedExtensionIds);
	}

	/**
	 * Discovers every {@link CovalentExtension} available on the classpath
	 * through {@link ServiceLoader}, keyed by {@link CovalentExtension#getId()}.
	 * When two extensions declare the same id, the first one discovered is
	 * kept and the duplicate is logged as a warning rather than failing
	 * startup.
	 *
	 * @return discovered extensions keyed by extension id
	 */
	private Map<String, CovalentExtension> discoverExtensions() {
		Map<String, CovalentExtension> discoveredExtensions = new LinkedHashMap<>();
		for (CovalentExtension extension : ServiceLoader.load(CovalentExtension.class)) {
			CovalentExtension existingExtension = discoveredExtensions.putIfAbsent(extension.getId(), extension);
			if (existingExtension != null) {
				log.warning("Duplicate Covalent extension id discovered on classpath: " + extension.getId()
						+ ". Keeping the first implementation.");
			}
		}
		return discoveredExtensions;
	}

	/**
	 * Reads and parses the {@value #EXTENSIONS_PROPERTY} system property into
	 * the set of requested extension ids, trimming whitespace and ignoring
	 * empty tokens.
	 *
	 * @return the requested extension ids, or an empty set when the property
	 *         is not set or blank
	 */
	private Set<String> readRequestedExtensions() {
		String configuredExtensions = System.getProperty(EXTENSIONS_PROPERTY);
		if (configuredExtensions == null || configuredExtensions.isBlank()) {
			return Set.of();
		}

		Set<String> requestedExtensions = new LinkedHashSet<>();
		for (String extensionToken : configuredExtensions.split(",")) {
			String extensionId = extensionToken.trim();
			if (!extensionId.isEmpty()) {
				requestedExtensions.add(extensionId);
			}
		}
		return requestedExtensions;
	}

}
