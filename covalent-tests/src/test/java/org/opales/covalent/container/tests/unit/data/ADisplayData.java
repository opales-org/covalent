/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.data;

import java.util.UUID;

public class ADisplayData implements Cloneable {

	private UUID key;
	private AData data;
	private boolean visible;
	
	public ADisplayData() {
	}
	
	
	@Override
	public Object clone() {
		ADisplayData clone = new ADisplayData();
		clone.setVisible(this.visible);
		clone.setKey(this.key);

		if (this.data != null) {
			clone.setData(new AData());
			for (String v : this.data.getDatas()) {
				clone.getData().getDatas().add(v);
			}
			clone.getData().setLat(this.data.getLat());
			clone.getData().setLon(this.data.getLon());
			clone.getData().setName(new String(this.data.getName()));
		}
		return clone;
	}

	public AData getData() {
		return this.data;
	}


	public void setData(AData track) {
		this.data = track;
	}


	public boolean isVisible() {
		return this.visible;
	}


	public void setVisible(boolean visible) {
		this.visible = visible;
	}
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.key == null) ? 0 : this.key.hashCode());
		result = prime * result + ((this.data == null) ? 0 : this.data.hashCode());
		result = prime * result + (this.visible ? 1231 : 1237);
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		ADisplayData other = (ADisplayData) obj;
		if (this.key == null) {
			if (other.key != null)
				return false;
		} else if (!this.key.equals(other.key))
			return false;
		if (this.data == null) {
			if (other.data != null)
				return false;
		} else if (!this.data.equals(other.data))
			return false;
		if (this.visible != other.visible)
			return false;
		return true;
	}


	public UUID getKey() {
		return this.key;
	}


	public void setKey(UUID key) {
		this.key = key;
	}


	
}
