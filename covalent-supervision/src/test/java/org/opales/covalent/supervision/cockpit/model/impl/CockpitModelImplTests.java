/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.supervision.cockpit.model.CockpitEvents;

/** {@link CockpitModelImpl} keeps the last snapshot and the connection state, and tells when they change. */
public class CockpitModelImplTests {

	private final List<String> events = new ArrayList<>();

	private CockpitModelImpl model;

	@Before
	public void setUp() throws Exception {
		this.model = new CockpitModelImpl();
		Field field = CockpitModelImpl.class.getDeclaredField("produceCockpitEvents");
		field.setAccessible(true);
		field.set(this.model, new CockpitEvents() {
			@Override
			public void onSnapshotChanged() {
				CockpitModelImplTests.this.events.add("snapshot");
			}

			@Override
			public void onConnectionChanged() {
				CockpitModelImplTests.this.events.add("connection");
			}
		});
	}

	@Test
	public void itStartsWithoutSnapshotAndDisconnected() {
		Assert.assertNull(this.model.getSnapshot());
		Assert.assertFalse(this.model.isConnected());
		Assert.assertTrue(this.events.isEmpty());
	}

	@Test
	public void aNewSnapshotIsAnnounced() {
		this.model.setSnapshot(null);

		Assert.assertEquals(List.of("snapshot"), this.events);
	}

	@Test
	public void theConnectionStateIsKeptAndAnnouncedEachTime() {
		this.model.setConnected(true);
		Assert.assertTrue(this.model.isConnected());
		this.model.setConnected(false);

		Assert.assertFalse(this.model.isConnected());
		Assert.assertEquals(List.of("connection", "connection"), this.events);
	}
}
