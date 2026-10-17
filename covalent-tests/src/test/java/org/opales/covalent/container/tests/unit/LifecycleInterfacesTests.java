/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComponentItem;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.lifecycle.Activable;
import org.opales.covalent.container.lifecycle.ComponentLifecycleTransition;
import org.opales.covalent.container.lifecycle.Initializable;
import org.opales.covalent.container.lifecycle.Passivatable;
import org.opales.covalent.container.lifecycle.Removable;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;

/**
 * A component implementation may implement {@link Initializable}, {@link Activable},
 * {@link Passivatable} and {@link Removable} (together: {@link ComponentLifecycleTransition}) instead
 * of annotating methods: the container calls them on the corresponding transitions. A field annotated
 * {@link ComponentItem} declares an item of the owning component.
 */
public class LifecycleInterfacesTests {

	private static final List<String> CALLS = new ArrayList<>();

	private CovalentContainer container;

	@Before
	public void setUp() {
		CALLS.clear();
		this.container = Covalent.container();
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void aComponentImplementingTheLifecycleInterfacesIsCalledOnEachTransition() {
		this.container.deployComponents(TransitionsComponentImpl.class);

		this.container.start();
		Assert.assertEquals(List.of("component:initialize", "component:activate"), CALLS);

		CALLS.clear();
		this.container.lcs().passivateComponent(TransitionsComponent.class);
		Assert.assertEquals(List.of("component:passivate"), CALLS);

		CALLS.clear();
		this.container.lcs().removeComponent(TransitionsComponent.class);
		Assert.assertEquals(List.of("component:remove"), CALLS);
	}

	@Test
	public void aComponentItemFieldDeclaresAnItemOfTheOwningComponent() {
		ComponentMetamodel metamodel = this.container.lcs().deployComponent(ItemOwnerImpl.class);

		Assert.assertEquals(1, metamodel.getItemMetamodels().size());
	}

	@Component
	public interface TransitionsComponent {
	}

	@Implementation
	public static class TransitionsComponentImpl implements TransitionsComponent, ComponentLifecycleTransition {

		@Override
		public void initialize() {
			CALLS.add("component:initialize");
		}

		@Override
		public void activate() {
			CALLS.add("component:activate");
		}

		@Override
		public void passivate() {
			CALLS.add("component:passivate");
		}

		@Override
		public void remove() {
			CALLS.add("component:remove");
		}
	}

	@Component
	public interface ItemOwner {
	}

	@Implementation
	public static class ItemOwnerImpl implements ItemOwner {

		@ComponentItem
		private OwnedItem item;
	}

	@Item
	public static class OwnedItem {
	}
}
