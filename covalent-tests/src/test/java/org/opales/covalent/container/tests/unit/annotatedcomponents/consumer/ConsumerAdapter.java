/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer;

import java.util.ArrayList;
import java.util.List;

public class ConsumerAdapter implements ConsumerServices {
	
	private boolean eventReceived = false;
	
	private List<String> events = new ArrayList<>();
	
	public void onEvent(final String message) {
		this.eventReceived = true;
		this.events.add(message);
	}
	
	@Override
	public boolean checkEventReceived() {
		return this.eventReceived;
	}
	
	@Override
	public boolean checkCustomConsumerEventReceived() {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	@Override
	public void subscribe() {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	@Override
	public void unsubscribe() {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	@Override
	public int getEventsCount() {
		return this.events.size();
	}
	
	@Override
	public List<String> getEvents() {
		return this.events;
	}
	
}
