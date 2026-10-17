/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;

@Services
public interface ConsumerComponentServices {

	List<String> getEventsReceivedByFirstItemA();

	List<String> getEventsReceivedBySecondItemA();

	List<String> getEventsReceivedByItemBObserver();

	List<String> getEventsReceivedByItemB();

	List<String> getEventsReceivedByImpl();

}
