/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.view;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Shows and hides the cockpit's panel.
 * <p>
 * All methods may be called from any thread: each one hands its work over to the JavaFX application thread.
 */
@Services
public interface CockpitViewServices {

	/**
	 * Opens the cockpit's overview and console as panels of the supervisor, the overview being the one shown.
	 *
	 * @param key the cockpit's view key
	 */
	void showView(ViewKey key);

	/**
	 * Closes the console and the panel the given key names.
	 *
	 * @param key the key of the panel to close
	 */
	void hideView(ViewKey key);
}
