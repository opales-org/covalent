/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.metamodel.metamodel.ComponentItemMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;

/**
 * Default {@link ComponentMetamodel} implementation. Extends
 * {@link ComponentItemMetamodelImpl} (which handles the item-level ports:
 * services, events, parameters, subscribers) with everything specific to a
 * whole component: its component type, its place in the composition
 * hierarchy (parent metamodel), its child items and composed components
 * (with the fields that declare them), the per-events-interface
 * {@link EventsMetamodel} registry, and the aggregation methods that merge
 * a component's own ports with those of its child items. Instances are
 * built and populated by {@code MetamodelFactory}.
 */
public class ComponentMetamodelImpl extends ComponentItemMetamodelImpl implements ComponentMetamodel {

	/** The component type described by this metamodel. */
	private Class<?> componentType = null;

	/** The parent component's metamodel, or {@code null} for a root component. */
	private ComponentMetamodel parentMetamodel = null;

	/** Metamodels of this component's child items, in declaration order. */
	private List<ComponentItemMetamodel> componentItemsMetamodels = new ArrayList<>();

	/** Metamodels of the components composed into this component. */
	private List<ComponentMetamodel> composedComponentsMetamodels = new ArrayList<>();

	/** Implementation classes of this component's child items. */
	private final List<Class<?>> itemsTypes = new ArrayList<>();

	/** Events metamodels, keyed by their described events interface. */
	private final Map<Class<?>, EventsMetamodel<?>> eventsMetamodels = new LinkedHashMap<>();

	/** Fields declaring a child item, keyed by the item's implementation class. */
	private final Map<Class<?>, Field> itemFields = new LinkedHashMap<>();

	/** Fields declaring a composed component, keyed by the composed component class. */
	private final Map<Class<?>, Field> composedComponentFields = new LinkedHashMap<>();

	/** Types of the components composed into this component. */
	private final List<Class<?>> composedComponentTypes = new ArrayList<>();

	/**
	 * Creates a new, empty component metamodel.
	 */
	public ComponentMetamodelImpl() {
	}

	@Override
	public Class<?> getComponentType() {
		return this.componentType;
	}

	@Override
	public void setComponentType(final Class<?> componentType) {
		this.componentType = componentType;
	}

	@Override
	public ComponentMetamodel getParentMetamodel() {
		return this.parentMetamodel;
	}

	@Override
	public void setParentMetamodel(final ComponentMetamodel parentMetamodel) {
		this.parentMetamodel = parentMetamodel;
	}

	@Override
	public Class<?> getParentComponentType() {
		if (this.parentMetamodel == null) {
			return null;
		}
		return this.parentMetamodel.getComponentType();
	}

	@Override
	public Set<Class<?>> getAllProvidedServices() {
		Set<Class<?>> allProvidedServices = new HashSet<>(this.getProvidedServices());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allProvidedServices.addAll(itemMetamodel.getProvidedServices());
		}
		return allProvidedServices;
	}

	@Override
	public Set<Class<?>> getAllUsedServices() {
		Set<Class<?>> allUsedServices = new HashSet<>(this.getUsedServices());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allUsedServices.addAll(itemMetamodel.getUsedServices());
		}
		return allUsedServices;
	}

	@Override
	public Set<Class<?>> getAllProducedEvents() {
		Set<Class<?>> allProducedEvents = new HashSet<>(this.getProducedEvents());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allProducedEvents.addAll(itemMetamodel.getProducedEvents());
		}
		return allProducedEvents;
	}

	@Override
	public Set<Class<?>> getAllConsumedEvents() {
		Set<Class<?>> allConsumedEvents = new HashSet<>(this.getConsumedEvents());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allConsumedEvents.addAll(itemMetamodel.getConsumedEvents());
		}
		return allConsumedEvents;
	}

	@Override
	public Set<Class<?>> getAllOfferedParameters() {
		Set<Class<?>> allOfferedParameters = new HashSet<>(this.getOfferedParameters());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allOfferedParameters.addAll(itemMetamodel.getOfferedParameters());
		}
		return allOfferedParameters;
	}

	@Override
	public Set<Class<?>> getAllAccessedParameters() {
		Set<Class<?>> allAccessedParameters = new HashSet<>(this.getAccessedParameters());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allAccessedParameters.addAll(itemMetamodel.getAccessedParameters());
		}
		return allAccessedParameters;
	}

	@Override
	public Set<Class<?>> getAllProvidedSubscribers() {
		Set<Class<?>> allProvidedSubscribers = new HashSet<>(this.getProvidedSubscribers());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allProvidedSubscribers.addAll(itemMetamodel.getProvidedSubscribers());
		}
		return allProvidedSubscribers;
	}

	@Override
	public Set<Class<?>> getAllUsedSubscribers() {
		Set<Class<?>> allUsedSubscribers = new HashSet<>(this.getUsedSubscribers());
		for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
			allUsedSubscribers.addAll(itemMetamodel.getUsedSubscribers());
		}
		return allUsedSubscribers;
	}

	@Override
	public Set<Class<?>> getAllConsumedEventsWithSubscriber() {
		Set<Class<?>> consumedEventsWithSubscriber = new HashSet<>(this.getAllConsumedEvents());
		Set<Class<?>> consumedEventsWithoutSubscriber = this.getAllConsumedEventsWithoutSubscriber();
		consumedEventsWithSubscriber.removeAll(consumedEventsWithoutSubscriber);
		return consumedEventsWithSubscriber;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Starts from every consumed event and removes those covered by a used
	 * subscriber. A used subscriber based on {@link GenericSubscribe}
	 * covers all consumed events at once (a generic subscriber is only
	 * allowed when the consumer consumes a single events type, a rule
	 * enforced elsewhere at wiring time), so its presence clears the whole
	 * result rather than removing a single events interface.
	 */
	@Override
	public Set<Class<?>> getAllConsumedEventsWithoutSubscriber() {
		Set<Class<?>> consumedEventsWithoutSubscriber = new HashSet<>(this.getAllConsumedEvents());
		Set<Class<?>> usedSubscribers = this.getAllUsedSubscribers();
		// for each used subscriber...
		for (Class<?> usedSubscriber : usedSubscribers) {
			if (GenericSubscribe.class.equals(usedSubscriber)) {
				// as manual subscribe using generic subscribe is only allowed if
				// the consumer consumes only one type of events, we assume
				// that there is not other consumed events
				// (respect of these rules will be checked when creating used subscribers,
				// see AutoWiringManager.createUsedSubscribers)
				consumedEventsWithoutSubscriber.clear();
				break;
			} else {
				Class<?> eventsClass = this.getConsumedEventsForUsedSubscriber(usedSubscriber);
				if (eventsClass != null) {
					// this events class has a subscriber, so remove it from result list
					consumedEventsWithoutSubscriber.remove(eventsClass);
				}
			}
		}
		return consumedEventsWithoutSubscriber;
	}

	@Override
	public <EVENT, SUBSCRIBE> Class<EVENT> getConsumedEventsForUsedSubscriber(final Class<SUBSCRIBE> subscriber) {
		Class<EVENT> eventsInterface = this.getItemConsumedEventsForUsedSubscriber(subscriber);
		if (eventsInterface == null) {
			for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
				ComponentItemMetamodelImpl itemMetamodelImpl = (ComponentItemMetamodelImpl) itemMetamodel;
				eventsInterface = itemMetamodelImpl.getItemConsumedEventsForUsedSubscriber(subscriber);
				if (eventsInterface != null) {
					break;
				}
			}

		}
		return eventsInterface;
	}

	@Override
	public <EVENT, SUBSCRIBE> Class<SUBSCRIBE> getProvidedSubscriberForProducedEvents(
			final Class<EVENT> eventsInterface) {
		Class<SUBSCRIBE> subscriber = this.getProvidedSubscriberForItemProducedEvents(eventsInterface);
		if (subscriber == null) {
			for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
				ComponentItemMetamodelImpl itemMetamodelImpl = (ComponentItemMetamodelImpl) itemMetamodel;
				subscriber = itemMetamodelImpl.getProvidedSubscriberForItemProducedEvents(eventsInterface);
				if (subscriber != null) {
					break;
				}
			}
		}
		return subscriber;
	}

	@Override
	public <EVENT, SUBSCRIBE> Class<EVENT> getProducedEventsForProvidedSubscriber(final Class<SUBSCRIBE> subscriber) {
		Class<EVENT> eventsInterface = this.getItemProducedEventsForProvidedSubscriber(subscriber);
		if (eventsInterface == null) {
			for (ComponentItemMetamodel itemMetamodel : this.getItemMetamodels()) {
				ComponentItemMetamodelImpl itemMetamodelImpl = (ComponentItemMetamodelImpl) itemMetamodel;
				eventsInterface = itemMetamodelImpl.getItemProducedEventsForProvidedSubscriber(subscriber);
				if (eventsInterface != null) {
					break;
				}
			}

		}
		return eventsInterface;
	}

	@Override
	public String toString() {
		return this.toString(0);
	}

	/**
	 * Renders this metamodel as an indented, human-readable tree, recursing
	 * into composed components' metamodels at increasing indent levels.
	 * Used by {@link #toString()} for diagnostic/debug output.
	 *
	 * @param indent the current nesting depth, in tab units
	 * @return the rendered tree fragment for this metamodel
	 */
	private String toString(final int indent) {
		StringBuilder strbuff = new StringBuilder();
		strbuff.append(this.space(indent)).append("--------------------------------------------------------------\n");
		strbuff.append(this.space(indent)).append("Type				: " + simpleName(this.componentType) + "\n");
		strbuff.append(this.space(indent))
				.append("Implementation			: " + simpleName(this.getImplementation()) + "\n");

		if (this.componentItemsMetamodels.size() > 0) {
			strbuff.append(this.space(indent)).append("  > Items:\n");
			for (ComponentItemMetamodel metamodel : this.componentItemsMetamodels) {
				strbuff.append(this.space(indent))
						.append("  - " + simpleName(metamodel.getImplementation()) + "\n");
			}
		}

		List<Class<?>> providedServices = this.getProvidedServices();
		if (providedServices.size() > 0) {
			strbuff.append(this.space(indent)).append("  > Provided services:\n");
			for (Class<?> providedService : providedServices) {
				strbuff.append(this.space(indent)).append("    - " + providedService.getSimpleName() + "\n");
			}
		}

		List<Class<?>> usedServices = this.getUsedServices();
		if (usedServices.size() > 0) {
			strbuff.append(this.space(indent)).append("  > Used services:\n");
			for (Class<?> usedService : usedServices) {
				strbuff.append(this.space(indent)).append("    - " + usedService.getSimpleName() + "\n");
			}
		}

		this.appendSection(strbuff, indent, "Produced events", this.getProducedEvents());
		this.appendSection(strbuff, indent, "Consumed events", this.getConsumedEvents());
		this.appendSection(strbuff, indent, "Offered parameters", this.getOfferedParameters());
		this.appendSection(strbuff, indent, "Accessed parameters", this.getAccessedParameters());

		List<Class<?>> providedEventsSubscribes = this.getProvidedSubscribers();
		if (providedEventsSubscribes.size() > 0) {
			strbuff.append(this.space(indent)).append("  > Provided events subscribes:\n");
			for (Class<?> providedEventsSubscribe : providedEventsSubscribes) {
				strbuff.append(this.space(indent)).append("    - " + providedEventsSubscribe.getSimpleName() + "\n");
			}
		}

		this.appendSection(strbuff, indent, "Implemented events subscribes", this.getImplementedSubscribers());

		List<Class<?>> usedEventsSubscribes = this.getUsedSubscribers();
		if (usedEventsSubscribes.size() > 0) {
			strbuff.append(this.space(indent)).append("  > Used events subscribes:\n");
			for (Class<?> usedEventsSubscribe : usedEventsSubscribes) {
				strbuff.append(this.space(indent)).append("    - " + usedEventsSubscribe.getSimpleName() + "\n");
			}
		}

		if (this.composedComponentsMetamodels.size() > 0) {
			strbuff.append(this.space(indent)).append("  > Composed components:\n");
			for (ComponentMetamodel metamodel : this.composedComponentsMetamodels) {
				ComponentMetamodelImpl impl = (ComponentMetamodelImpl) metamodel;
				strbuff.append(this.space(indent))
						.append("  - " + simpleName(metamodel.getImplementation()) + "\n");
				strbuff.append(impl.toString(indent + 1) + "\n");
			}
		}

		return strbuff.toString();
	}

	private void appendSection(final StringBuilder strbuff, final int indent, final String title,
			final List<Class<?>> interfaces) {
		if (interfaces.size() > 0) {
			strbuff.append(this.space(indent)).append("  > " + title + ":\n");
			for (Class<?> anInterface : interfaces) {
				strbuff.append(this.space(indent)).append("    - " + anInterface.getSimpleName() + "\n");
			}
		}
	}

	/**
	 * Returns a tab-indentation prefix for {@link #toString(int)}.
	 *
	 * @param indent the nesting depth, in tab units
	 * @return {@code indent} tab characters
	 */
	private String space(final int indent) {
		return "	".repeat(indent);
	}

	@Override
	public <EVENTS> void addEventsMetamodel(final EventsMetamodel<EVENTS> eventsMetamodel) {
		this.eventsMetamodels.put(eventsMetamodel.getEvents(), eventsMetamodel);
	}

	@Override
	public <EVENTS> void removeEventsMetamodel(final Class<EVENTS> eventsInterface) {
		this.eventsMetamodels.remove(eventsInterface);
	}

	@Override
	public <EVENTS> EventsMetamodel<EVENTS> getEventsMetamodel(final Class<EVENTS> eventsInterface) {
		@SuppressWarnings("unchecked")
		EventsMetamodel<EVENTS> result = (EventsMetamodel<EVENTS>) this.eventsMetamodels.get(eventsInterface);
		return result;
	}

	@Override
	public List<Class<?>> getItemTypes() {
		return new ArrayList<>(this.itemsTypes);
	}

	@Override
	public void setItemTypes(final List<Class<?>> itemTypes) {
		this.itemsTypes.clear();
		this.itemsTypes.addAll(itemTypes);
	}

	@Override
	public List<ComponentItemMetamodel> getItemMetamodels() {
		return new ArrayList<>(this.componentItemsMetamodels);
	}

	@Override
	public void setItemMetamodels(final List<ComponentItemMetamodel> componentItemsMetamodels) {
		this.componentItemsMetamodels = componentItemsMetamodels;
	}

	@Override
	public void addItemField(final Class<?> itemClass, final Field field) {
		this.itemFields.put(itemClass, field);
	}

	@Override
	public List<Field> getItemsFields() {
		return new ArrayList<>(this.itemFields.values());
	}

	@Override
	public Field getItemField(final Class<?> itemClass) {
		return this.itemFields.get(itemClass);
	}

	@Override
	public List<Class<?>> getComposedTypes() {
		return new ArrayList<>(this.composedComponentTypes);
	}

	@Override
	public void setComposedTypes(final List<Class<?>> composedTypes) {
		this.composedComponentTypes.clear();
		this.composedComponentTypes.addAll(composedTypes);
	}

	@Override
	public List<ComponentMetamodel> getComposedMetamodels() {
		return new ArrayList<>(this.composedComponentsMetamodels);
	}

	@Override
	public void setComposedMetamodels(final List<ComponentMetamodel> composedComponentsMetamodels) {
		this.composedComponentsMetamodels = composedComponentsMetamodels;
	}

	@Override
	public void addComposedComponentField(final Class<?> componentClass, final Field field) {
		this.composedComponentFields.put(componentClass, field);
	}

	@Override
	public List<Field> getComposedComponentsFields() {
		return new ArrayList<>(this.composedComponentFields.values());
	}

	@Override
	public Field getComposedComponentField(final Class<?> componentClass) {
		return this.composedComponentFields.get(componentClass);
	}

}
