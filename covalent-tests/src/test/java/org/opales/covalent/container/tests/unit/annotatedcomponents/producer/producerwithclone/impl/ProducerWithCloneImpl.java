/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClone;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithCloneServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedAttributeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedMethodEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedNoAccessibleCloneMethodEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithclone.ProducerWithClonedThrowingEvents;
import org.opales.covalent.container.tests.unit.data.mutable.MutableData;
import org.opales.covalent.container.tests.unit.data.mutable.impl.MutableCloneableDataImpl;
import org.opales.covalent.container.tests.unit.data.mutable.impl.MutableCloneableThrowingDataImpl;
import org.opales.covalent.container.tests.unit.data.mutable.impl.MutableCloneableWithoutPublicCloneDataImpl;
import org.opales.covalent.container.tests.unit.data.mutable.impl.MutableNotCloneableDataImpl;

@Implementation
public class ProducerWithCloneImpl implements ProducerWithClone, ProducerWithCloneServices {

	@ProducedEvents
	ProducerWithClonedEvents produceProducerWithClonedEvents;

	@ProducedEvents
	ProducerWithClonedMethodEvents produceProducerWithClonedMethodEvents;

	@ProducedEvents
	ProducerWithClonedAttributeEvents produceProducerWithClonedAttributeEvents;

	@ProducedEvents
	ProducerWithClonedThrowingEvents produceProducerWithClonedThrowingEvents;

	@ProducedEvents
	ProducerWithClonedNoAccessibleCloneMethodEvents produceProducerWithClonedNoAccessibleCloneMethodEvents;

	@Override
	public void fireProducerWithClonedEvents() {
		MutableData cloneable = this.newCloneable();
		MutableData notCloneable = this.newNonCloneable();
		this.produceProducerWithClonedEvents.onEvents(cloneable, notCloneable);

		this.mutatedCloneableOnClonedEvents = cloneable.getState().equals("mutated");
		this.mutatedNotCloneableOnClonedEvents = notCloneable.getState().equals("mutated");

	}

	@Override
	public void fireProducerWithClonedMethodEvents() {
		MutableData cloneable = this.newCloneable();
		MutableData notCloneable = this.newNonCloneable();
		this.produceProducerWithClonedMethodEvents.onEvents(cloneable, notCloneable);

		this.mutatedCloneableOnClonedMethodEvents = cloneable.getState().equals("mutated");
		this.mutatedNotCloneableOnClonedMethodEvents = notCloneable.getState().equals("mutated");

	}

	@Override
	public void fireProducerWithClonedAttributeEvents() {
		MutableData cloneable = this.newCloneable();
		MutableData notCloneable = this.newNonCloneable();
		MutableData cloneable2 = this.newCloneable();
		MutableData notCloneable2 = this.newNonCloneable();
		this.produceProducerWithClonedAttributeEvents.onEvents(cloneable, notCloneable, cloneable2, notCloneable2);

		this.mutatedCloneableOnClonedAttributeEvents = cloneable.getState().equals("mutated");
		this.mutatedNotCloneableOnClonedAttributeEvents = notCloneable.getState().equals("mutated");
		this.mutatedCloneableOnNotClonedAttributeEvents = cloneable2.getState().equals("mutated");
		this.mutatedNotCloneableOnNotClonedAttributeEvents = notCloneable2.getState().equals("mutated");
	}

	@Override
	public void fireProducerWithClonedThrowingEvents() {
		this.produceProducerWithClonedThrowingEvents.onEvents(new MutableCloneableThrowingDataImpl("cloneable"));
	}

	@Override
	public void fireProducerWithClonedNoAccessibleCloneMethodEvents() {
		this.produceProducerWithClonedNoAccessibleCloneMethodEvents
				.onEvents(new MutableCloneableWithoutPublicCloneDataImpl("cloneable"));
	}

	private MutableData newCloneable() {
		return new MutableCloneableDataImpl("cloneable");
	}

	private MutableData newNonCloneable() {
		return new MutableNotCloneableDataImpl("cloneable");
	}

	@Override
	public boolean isMutatedCloneableOnClonedEvents() {
		return this.mutatedCloneableOnClonedEvents;
	}

	@Override
	public boolean isMutatedNotCloneableOnClonedEvents() {
		return this.mutatedNotCloneableOnClonedEvents;
	}

	@Override
	public boolean isMutatedCloneableOnClonedMethodEvents() {
		return this.mutatedCloneableOnClonedMethodEvents;
	}

	@Override
	public boolean isMutatedNotCloneableOnClonedMethodEvents() {
		return this.mutatedNotCloneableOnClonedMethodEvents;
	}

	@Override
	public boolean isMutatedCloneableOnClonedAttributeEvents() {
		return this.mutatedCloneableOnClonedAttributeEvents;
	}

	@Override
	public boolean isMutatedNotCloneableOnClonedAttributeEvents() {
		return this.mutatedNotCloneableOnClonedAttributeEvents;
	}

	@Override
	public boolean isMutatedCloneableOnNotClonedAttributeEvents() {
		return this.mutatedCloneableOnNotClonedAttributeEvents;
	}

	@Override
	public boolean isMutatedNotCloneableOnNotClonedAttributeEvents() {
		return this.mutatedNotCloneableOnNotClonedAttributeEvents;
	}

	private boolean mutatedCloneableOnClonedEvents;

	private boolean mutatedNotCloneableOnClonedEvents;

	private boolean mutatedCloneableOnClonedMethodEvents;

	private boolean mutatedNotCloneableOnClonedMethodEvents;

	private boolean mutatedCloneableOnClonedAttributeEvents;

	private boolean mutatedNotCloneableOnClonedAttributeEvents;

	private boolean mutatedCloneableOnNotClonedAttributeEvents;

	private boolean mutatedNotCloneableOnNotClonedAttributeEvents;

	@Override
	public void reset() {
		this.mutatedCloneableOnClonedEvents = false;
		this.mutatedNotCloneableOnClonedEvents = false;
		this.mutatedCloneableOnClonedMethodEvents = false;
		this.mutatedNotCloneableOnClonedMethodEvents = false;
		this.mutatedCloneableOnClonedAttributeEvents = false;
		this.mutatedNotCloneableOnClonedAttributeEvents = false;
		this.mutatedCloneableOnNotClonedAttributeEvents = false;
		this.mutatedNotCloneableOnNotClonedAttributeEvents = false;
	}
}
