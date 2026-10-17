/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opales.covalent.container.component.annotations.Cloned;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;

/**
 * Default {@link EventsMetamodel} implementation. Built by
 * {@code MetamodelFactory} for each produced events interface: it caches
 * the interface's declared methods (for {@link #containsMethod}) and
 * records the clone behavior introspected from the {@code @Cloned}
 * annotation, either at the whole-interface level ({@link #setCloneable})
 * or at the method/parameter level ({@link #setHasCloneableElements}).
 *
 * @param <EVENTS> the events interface type
 */
public class EventsMetamodelImpl<EVENTS> implements EventsMetamodel<EVENTS> {

	/** The events interface described by this metamodel. */
	private final Class<EVENTS> eventsInterface;

	/**
	 * Whether at least one event method or event method parameter is
	 * individually annotated with {@code @Cloned}.
	 */
	private boolean hasCloneableElements;

	/**
	 * Whether the whole events interface is annotated with {@code @Cloned},
	 * meaning every event payload fired through it is cloned.
	 */
	private boolean cloneable;

	/** The events interface's declared methods, cached for {@link #containsMethod}. */
	private List<Method> methods = new ArrayList<>();

	/**
	 * Creates a metamodel for the given events interface, capturing its
	 * declared methods.
	 *
	 * @param eventsInterface the events interface to describe
	 */
	public EventsMetamodelImpl(final Class<EVENTS> eventsInterface) {
		this.eventsInterface = eventsInterface;
		this.methods = Arrays.asList(eventsInterface.getMethods());
	}

	@Override
	public boolean isCloneable() {
		return this.cloneable;
	}

	@Override
	public boolean isMethodCloneable(final Method eventMethod) {
		return eventMethod.isAnnotationPresent(Cloned.class);
	}

	@Override
	public boolean isMethodParameterClonable(final Parameter eventMethodParameter) {
		return eventMethodParameter.isAnnotationPresent(Cloned.class);
	}

	@Override
	public boolean hasCloneableElements() {
		return this.hasCloneableElements;
	}

	/**
	 * Records whether the events interface has at least one method or
	 * parameter individually annotated with {@code @Cloned}.
	 *
	 * @param hasCloneableElements {@code true} if such an element exists
	 */
	public void setHasCloneableElements(final boolean hasCloneableElements) {
		this.hasCloneableElements = hasCloneableElements;
	}

	@Override
	public boolean containsMethod(final Method method) {
		return this.methods.contains(method);
	}

	/**
	 * Marks the whole events interface as cloneable, i.e. every event
	 * payload fired through it is cloned by interface-level rule.
	 */
	public void setCloneable() {
		this.cloneable = true;
	}

	@Override
	public Class<EVENTS> getEvents() {
		return this.eventsInterface;
	}

}
