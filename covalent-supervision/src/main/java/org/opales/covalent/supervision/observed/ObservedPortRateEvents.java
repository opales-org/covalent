/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.util.List;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Call rates of the observed container's ports, sampled at a fixed period while connected:
 * counted where calls go through, so that measuring costs no notification per call.
 */
@Events
public interface ObservedPortRateEvents {

	/**
	 * Notifies the call rates measured over the period that just ended.
	 *
	 * @param sampleTimeMillis when the period ended
	 * @param periodMillis the period's length
	 * @param rates every port that saw calls during the period; empty when none did
	 */
	void onPortRatesSampled(long sampleTimeMillis, long periodMillis, List<ObservedPortRate> rates);
}
