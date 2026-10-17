/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

/** The three kinds of call-carrying ports whose activity {@link ObservedPortActivityEvents} reports. */
public enum ObservedPortKind {

	/** Ports that provide or use services. */
	SERVICE,

	/** Ports that offer or access parameters. */
	PARAMETER,

	/** Ports that produce or consume events. */
	EVENT;
}
