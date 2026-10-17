/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.model.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.model.Alert;
import org.opales.covalent.examples.solarsystem.hazardalert.model.HazardAlertEvents;
import org.opales.covalent.examples.solarsystem.hazardalert.model.HazardAlertModel;
import org.opales.covalent.examples.solarsystem.hazardalert.model.HazardAlertModelServices;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The hazard alert's Model: holds the currently active and acknowledged
 * alerts, and notifies {@link HazardAlertEvents} whenever they change.
 * Carries no escalation logic of its own.
 */
@Implementation
public class HazardAlertModelImpl implements HazardAlertModel, HazardAlertModelServices {

	@ProducedEvents
	private HazardAlertEvents produceHazardAlertEvents;

	private final List<Alert> alerts = new ArrayList<>();

	@Override
	public synchronized void raiseAlert(final Alert alert) {
		this.alerts.add(alert);
		this.produceHazardAlertEvents.onAlertRaised(alert);
	}

	@Override
	public synchronized void acknowledgeAll() {
		for (int i = 0; i < this.alerts.size(); i++) {
			this.alerts.set(i, this.alerts.get(i).acknowledge());
		}
		this.produceHazardAlertEvents.onAllAcknowledged();
	}

	@Override
	public synchronized Alert[] getActiveAlerts() {
		return this.alerts.stream().filter(alert -> !alert.acknowledged()).toArray(Alert[]::new);
	}
}
