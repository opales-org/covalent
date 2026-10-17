/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.List;

/**
 * A component's activity over the last sampling period, per interface and role.
 *
 * @param ports the ports that saw calls, busiest first; for a composite, those of the
 *        components it contains, among which its own ports, the ones shared with the outside
 */
public record ComponentActivity(List<PortRate> ports) {

	/**
	 * Rate of calls through one port of a component over the sampling period.
	 *
	 * @param portType the port's interface
	 * @param kind the port's kind
	 * @param role the component's role on it
	 * @param callsPerSecond the calls through it
	 */
	public record PortRate(Class<?> portType, ComponentPortKind kind, ComponentPortRole role, double callsPerSecond) {
	}
}
