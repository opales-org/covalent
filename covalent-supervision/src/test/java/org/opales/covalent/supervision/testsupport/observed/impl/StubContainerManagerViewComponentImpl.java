/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.containermanager.view.ContainerManagerViewServices;
import org.opales.covalent.supervision.testsupport.observed.StubContainerManagerViewComponent;

@Implementation
public class StubContainerManagerViewComponentImpl
		implements StubContainerManagerViewComponent, ContainerManagerViewServices, ActionProviderServices {

	/** What the view was told, in order: "succeeded name", "failed", "stopped name", "disposed name". */
	public static final List<String> reports = new CopyOnWriteArrayList<>();

	@Override
	public void connectionSucceeded(final String containerName) {
		reports.add("succeeded " + containerName);
	}

	@Override
	public void connectionFailed(final String reason) {
		reports.add("failed");
	}

	@Override
	public void showConnectionError(final String containerName, final String reason) {
		reports.add("error");
	}

	@Override
	public void containerStopped(final String containerName) {
		reports.add("stopped " + containerName);
	}

	@Override
	public void containerDisposed(final String containerName) {
		reports.add("disposed " + containerName);
	}

	@Override
	public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
			final Class<T> anInputDataClass) {
	}

	@Override
	public <T1, T2> void registerActionExecution(final ActionKey anActionKey,
			final BiConsumer<T1, T2> anInteractionImpl, final Class<T1> anInputDataClass1,
			final Class<T2> anInputDataClass2) {
	}

	@Override
	public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl) {
	}

	@Override
	public void unregisterActionExecution(final ActionKey anActionKey) {
	}

	@Override
	public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
	}

	@Override
	public void setActionValue(final ActionKey anActionKey, final Object aValue) {
	}
}
