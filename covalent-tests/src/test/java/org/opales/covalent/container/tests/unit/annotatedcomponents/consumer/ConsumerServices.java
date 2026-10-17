/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;

@Services
public interface ConsumerServices {
	boolean checkEventReceived();
	
	public boolean checkCustomConsumerEventReceived();
	
	void subscribe();
	
	void unsubscribe();
	
	int getEventsCount();
	
	List<String> getEvents();
	
}
