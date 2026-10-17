/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

/**
 * Opaque technical identifier for one panel hosted through
 * {@link PanelHostingServices}. Each function that hosts a panel supplies
 * its own {@code ViewKey} instance - a single constant for a function with
 * exactly one panel, or one instance per dynamic panel for a function that
 * hosts several at once.
 */
public interface ViewKey {
}
