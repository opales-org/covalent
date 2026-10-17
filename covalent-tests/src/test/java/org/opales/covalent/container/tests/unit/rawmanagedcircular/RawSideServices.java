/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawmanagedcircular;

/**
 * Services provided by the raw component fixture, used by the managed
 * component fixture through a {@code @UsedServices} port.
 * <p>
 * Deliberately NOT annotated with {@code @Services}: a raw component's
 * implementation class (or any interface it implements) must not carry
 * Covalent component-model annotations, per
 * {@code RawComponentsImpl.checkRawComponentContract}. Raw components
 * publish and consume ports imperatively through
 * {@link org.opales.covalent.container.raw.RawComponentsServices} instead.
 * </p>
 */
public interface RawSideServices {

	String ping();

}
