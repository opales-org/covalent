/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

/**
 * What went through one port of one component during a sampling period.
 *
 * @param componentType the component the calls went through
 * @param portType the port's interface
 * @param portKind the port's kind
 * @param receiving whether the component receives the calls - provided services, offered
 *        parameters, consumed events - rather than makes them
 * @param calls how many calls started during the period
 * @param failures how many calls ended with a failure during the period
 * @param p95Nanos the 95th percentile of the durations of the calls that ended during the
 *        period, {@code -1} when none ended
 * @param lastFailedMethod the method of the last call that failed during the period, {@code null}
 *        when none failed
 */
public record ObservedPortRate(Class<?> componentType, Class<?> portType, ObservedPortKind portKind,
		boolean receiving, int calls, int failures, long p95Nanos, String lastFailedMethod) {
}
