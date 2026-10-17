/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.view;

import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Provided by {@code StereotypePolicyView}, used by {@code StereotypePolicyInteraction}.
 * <p>
 * All methods may be called from any thread: each one hands its work over to the JavaFX application thread.
 */
@Services
public interface StereotypePolicyViewServices {

	/**
	 * Opens this function's single panel, or brings it forward when it is already open.
	 *
	 * @param key this function's view key
	 */
	void showView(ViewKey key);

	/**
	 * Closes this function's panel.
	 *
	 * @param key this function's view key
	 */
	void hideView(ViewKey key);
}
