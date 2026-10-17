/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.interaction.impl;

import java.util.Set;

import org.opales.covalent.container.workers.WorkerPoolKind;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.sequencediagram.controller.RecordingStateEvents;
import org.opales.covalent.supervision.threads.controller.ThreadsControllerServices;
import org.opales.covalent.supervision.threads.interaction.ThreadsActionKey;
import org.opales.covalent.supervision.threads.interaction.ThreadsInteraction;
import org.opales.covalent.supervision.threads.interaction.ThreadsViewKey;
import org.opales.covalent.supervision.threads.view.ThreadsViewServices;

/**
 * Opens and closes the Threads panel, and passes its commands to the controller. Keeps what the
 * shared Record button records on the panel, as its View's selection gives it, and keeps no
 * recording state: whether a recording runs is asked each time it matters.
 */
@Implementation
public class ThreadsInteractionImpl implements ThreadsInteraction, RecordingStateEvents {

	/** Creates the Interaction Controller; the container injects the services it uses. */
	public ThreadsInteractionImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private ThreadsViewServices useViewServices;

	@UsedServices
	private ThreadsControllerServices useControllerServices;

	private volatile boolean shown = false;

	/** What the shared Record button records on this panel, as the View's selection gives it. */
	private volatile Set<Class<?>> recordScope = Set.of();

	/** Registers the actions of the Threads panel and sets whether the Record button is available. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.Toggle, this::onToggle);
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.Show, this::onShow);
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.SetTaskTracking,
				this.useControllerServices::setTaskTracking, Boolean.class);
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.ReadStack,
				this.useControllerServices::readStack, Long.class);
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.SetRecordScope, this::onSetRecordScope,
				Set.class);
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.InterruptThreads, this::onInterruptThreads,
				Set.class);
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.RecordToggle,
				() -> this.useControllerServices.toggleRecording(this.recordScope));
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.SetPoolWorkers,
				this.useControllerServices::setWorkerCount, WorkerPoolKind.class, Integer.class);
		this.actionManagerServices.registerActionExecution(ThreadsActionKey.SetPoolQueueCapacity,
				this.useControllerServices::setQueueCapacity, WorkerPoolKind.class, Integer.class);
		this.publishRecordAvailability();
	}

	@SuppressWarnings("unchecked")
	private void onInterruptThreads(final Set<?> threadIds) {
		this.useControllerServices.interruptThreads(Set.copyOf((Set<Long>) threadIds));
	}

	@SuppressWarnings("unchecked")
	private void onSetRecordScope(final Set<?> components) {
		this.recordScope = Set.copyOf((Set<Class<?>>) components);
		this.publishRecordAvailability();
	}

	/** Record serves while a recording runs, to stop it, or when the selection has components to record. */
	private void publishRecordAvailability() {
		this.actionManagerServices.setActionAvailable(ThreadsActionKey.RecordToggle,
				this.useControllerServices.isRecording() || !this.recordScope.isEmpty());
	}

	// --- RecordingStateEvents: notices only, the state is asked again. ---

	@Override
	public void onRecordingStarted() {
		this.publishRecordAvailability();
	}

	@Override
	public void onRecordingStopped() {
		this.publishRecordAvailability();
	}

	/** Unregisters the actions of the Threads panel and closes it. */
	@Passivation
	public void unregisterActions() {
		for (ThreadsActionKey key : ThreadsActionKey.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(key);
			} catch (IllegalStateException e) {
				// The action manager is already passivated, as part of the same container stop.
			}
		}
		this.onClose();
	}

	private void onToggle() {
		if (this.shown) {
			this.onClose();
		} else {
			this.onShow();
		}
	}

	private void onShow() {
		this.shown = true;
		this.useViewServices.showView(ThreadsViewKey.INSTANCE);
	}

	private void onClose() {
		if (this.shown) {
			this.shown = false;
			this.useViewServices.hideView(ThreadsViewKey.INSTANCE);
		}
	}
}
