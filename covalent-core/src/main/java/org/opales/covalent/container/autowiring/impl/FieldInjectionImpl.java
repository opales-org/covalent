/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.autowiring.impl;

import java.lang.reflect.Field;
import java.util.List;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.registry.ItemAndField;
import org.opales.covalent.container.util.Reflection;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Internal item shared by every per-port-family autowiring item
 * ({@link ServicesAutoWiringImpl}, {@link ParametersAutoWiringImpl},
 * {@link EventsAutoWiringImpl}): reflectively pushes a resolved port
 * instance into every field of a set of items that is meant to receive it,
 * without ever overwriting a field that has already been set.
 */
@Item
class FieldInjectionImpl {

	/**
	 * Sets {@code value} on every field described by {@code itemAndFields},
	 * but only where that field is currently {@code null} - a field an
	 * application component already populated itself (or that a previous
	 * autowiring pass already injected) is left untouched.
	 *
	 * @param itemAndFields the (item instance, field) pairs to inject into
	 * @param value         the resolved port instance to inject; must not
	 *                      be {@code null}
	 * @param valueName     the name of {@code value}, used in the
	 *                      not-null check's error message
	 */
	void injectIfNull(final List<ItemAndField> itemAndFields, final Object value, final String valueName) {
		for (ItemAndField itemAndField : itemAndFields) {
			Object item = itemAndField.getItem();
			Field field = itemAndField.getField();

			RuntimeCheck.notNull(value, valueName);
			RuntimeCheck.notNull(item, "item");
			RuntimeCheck.notNull(field, "field");

			Reflection.setFieldIfNull(field, item, value);
		}
	}

}
