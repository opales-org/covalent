/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry;

import java.lang.reflect.Field;

/**
 * Pairing of a live item (or component) instance with one of its reflective
 * port fields (a used-services, produced-events, accessed-parameters or
 * used-subscribe field). Reports, for a given port interface, every field
 * across a component's items that references it, along with the instance
 * each field lives on so it can be read or written reflectively.
 */
public class ItemAndField {
	/**
	 * Creates a new pairing of an instance with one of its port fields.
	 *
	 * @param item  the item or component instance the field is declared on
	 * @param field the reflective port field
	 */
	public ItemAndField(final Object item, final Field field) {
		this.item = item;
		this.field = field;
	}

	/**
	 * Returns the item or component instance the field is declared on.
	 *
	 * @return the item instance
	 */
	public Object getItem() {
		return this.item;
	}

	/**
	 * Returns the reflective port field.
	 *
	 * @return the field
	 */
	public Field getField() {
		return this.field;
	}

	/** The item or component instance the field is declared on. */
	private final Object item;

	/** The reflective port field. */
	private final Field field;
}
