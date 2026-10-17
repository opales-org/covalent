/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.util;

import java.util.List;

import org.opales.covalent.container.component.ComponentAnnotations;

/**
 * Helpers shared by the internal managers to describe what a container leaves
 * behind once its business components are torn down (see
 * {@code CovalentContainerImpl#stop()}): each manager lists, as readable
 * messages, whatever it still holds that does not belong to an internal
 * component.
 */
public final class ShutdownResidues {

	private ShutdownResidues() {
	}

	/**
	 * Adds a residue message.
	 *
	 * @param residues the list to complete
	 * @param owner    the manager reporting the residue
	 * @param message  what was found
	 */
	public static void add(final List<String> residues, final String owner, final String message) {
		residues.add(owner + ": " + message);
	}

	/**
	 * Adds a residue message when the given component type is not an internal
	 * component (a {@code null} type is reported too: the owner is unknown).
	 *
	 * @param residues      the list to complete
	 * @param owner         the manager reporting the residue
	 * @param what          what was found, followed by the component type
	 * @param componentType the component type concerned, possibly {@code null}
	 */
	public static void addIfNotInternal(final List<String> residues, final String owner, final String what,
			final Class<?> componentType) {
		if (componentType == null) {
			add(residues, owner, what + " an unknown component type");
		} else if (!ComponentAnnotations.isInternalComponent(componentType)) {
			add(residues, owner, what + " " + componentType.getName());
		}
	}
}
