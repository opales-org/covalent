/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Shows and hides the Threads panel.
 * <p>
 * All methods may be called from any thread: each one hands its work over to the JavaFX application thread.
 */
@Services
public interface ThreadsViewServices {

	/**
	 * Shows the Threads panel.
	 *
	 * @param key the panel to show
	 */
	void showView(ViewKey key);

	/**
	 * Hides the Threads panel.
	 *
	 * @param key the panel to hide
	 */
	void hideView(ViewKey key);
}
