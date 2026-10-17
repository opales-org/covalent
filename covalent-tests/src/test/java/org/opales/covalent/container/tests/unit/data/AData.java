/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AData {

	private final UUID key;
	private String name;
	private double lat;
	private double lon;
	private final List<String> datas = new ArrayList<>();

	public AData() {
		this.key = UUID.randomUUID();
		for (int i = 0; i < 100; i++) {
			this.datas.add("A" + i);
		}
	}

	public UUID getKey() {
		return this.key;
	}

	public String getName() {
		return this.name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public double getLat() {
		return this.lat;
	}

	public void setLat(final double lat) {
		this.lat = lat;
	}

	public double getLon() {
		return this.lon;
	}

	public void setLon(final double lon) {
		this.lon = lon;
	}

	public List<String> getDatas() {
		return this.datas;
	}
}
