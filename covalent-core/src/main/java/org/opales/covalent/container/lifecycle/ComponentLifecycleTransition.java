/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

/**
 * Convenience interface combining all four lifecycle marker interfaces
 * ({@link Activable}, {@link Passivatable}, {@link Initializable},
 * {@link Removable}) for a component implementation class that
 * wants to hook every lifecycle transition, instead of implementing each
 * interface separately.
 */
public interface ComponentLifecycleTransition extends Activable, Passivatable, Initializable, Removable {

}
