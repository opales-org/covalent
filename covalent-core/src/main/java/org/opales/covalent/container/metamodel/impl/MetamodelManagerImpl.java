/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.impl;

import org.opales.covalent.container.util.ShutdownResidues;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.metamodel.MetamodelEvents;
import org.opales.covalent.container.metamodel.MetamodelManager;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentItemMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Default {@link MetamodelManager} implementation: owns the registry of
 * built {@link ComponentMetamodel}s and the index maps that resolve a port
 * interface (provided services, produced/consumed events, offered
 * parameters, provided/used subscribers) back to the component type that
 * declares it. Metamodels are built by delegating to a private
 * {@link MetamodelFactory} instance, cached by component type, and kept
 * consistent with their parent/child composition hierarchy as components
 * are created, updated and deleted. This manager also implements
 * {@link MetamodelPortWriteServices} to allow other managers to apply
 * targeted dynamic port mutations, and fires {@link MetamodelEvents} for
 * every metamodel creation, update and deletion so interested managers can
 * react.
 */
@Implementation
public class MetamodelManagerImpl implements MetamodelManager, MetamodelWriteServices, MetamodelPortWriteServices {

	/** Creates the manager; the container injects the services it uses. */
	public MetamodelManagerImpl() {
		// Nothing to initialize.
	}

	/** Used-services field resolving a component implementation's declared component type. */
	@UsedServices
	protected IntrospectionServices useIntrospectionServices;

	/** Registered component metamodels, keyed by component type. */
	private final Map<Class<?>, ComponentMetamodel> metamodels = new HashMap<>();

	/** Index mapping each produced events interface to the metamodel of the component that produces it. */
	private final Map<Class<?>, ComponentMetamodel> metamodelForEvents = new HashMap<>();

	/** Index mapping each provided subscribe interface to the metamodel of the component that provides it. */
	private final Map<Class<?>, ComponentMetamodel> metamodelForSubscriber = new HashMap<>();

	/** Index mapping each registered component type to its implementation class. */
	private final Map<Class<?>, Class<?>> typeToImpl = new HashMap<>();

	/** Index mapping each registered implementation class to its component type. */
	private final Map<Class<?>, Class<?>> implToType = new HashMap<>();

	/** Index mapping each provided services interface to the component type providing it. */
	private final Map<Class<?>, Class<?>> servicesToType = new HashMap<>();

	/** Index mapping each offered parameters interface to the component type offering it. */
	private final Map<Class<?>, Class<?>> parametersToType = new HashMap<>();

	/** Index mapping each produced events interface to the component type producing it. */
	private final Map<Class<?>, Class<?>> eventsToType = new HashMap<>();

	/** Cache of {@link EventsMetamodel}s already resolved by {@link #getEventsMetamodel}, keyed by events interface. */
	private final Map<Class<?>, EventsMetamodel<?>> eventsMetamodels = new ConcurrentHashMap<>();

	/** Factory delegated to for building and completing component metamodels. */
	@Item
	private final MetamodelFactory metamodelFactory = new MetamodelFactory();

	/** Used to report a produced-events/services/parameters ownership collision. */
	@UsedServices
	private ValidationServices useValidationServices;

	/** Produced-events field used to notify listeners of metamodel creation, update and deletion. */
	@ProducedEvents
	private MetamodelEvents produceMetamodelEvents;

	/**
	 * Returns the factory this manager uses to build and complete
	 * component metamodels.
	 *
	 * @return the metamodel factory
	 */
	public MetamodelFactory getMetamodelFactory() {
		return this.metamodelFactory;
	}

	/**
	 * Injects the validation services forwarded to this manager's
	 * {@link MetamodelFactory}, used to enforce structural rules while
	 * building metamodels.
	 *
	 * @param validationServices the validation services to use
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.useValidationServices = validationServices;
		this.metamodelFactory.setValidationServices(validationServices);
	}

	/**
	 * Injects the introspection services used by this manager and forwarded
	 * to its {@link MetamodelFactory} to resolve a component
	 * implementation's declared component type.
	 *
	 * @param introspectionServices the introspection services to use
	 */
	public void setIntrospectionServices(final IntrospectionServices introspectionServices) {
		this.useIntrospectionServices = introspectionServices;
		this.metamodelFactory.setIntrospectionServices(introspectionServices);
	}

	/**
	 * Returns the number of component metamodels currently registered.
	 *
	 * @return the registered metamodel count
	 */
	public int getCount() {
		return this.metamodels.size();
	}

	private void requireNoMetamodel(final Class<?> componentType, final Class<?> implementation) {
		if (this.metamodels.containsKey(componentType)) {
			throw new IllegalArgumentException(
					"A metamodel already exists for component type " + componentType.getSimpleName());
		}
		if (this.implToType.containsKey(implementation)) {
			throw new IllegalArgumentException(
					"A metamodel already exists for implementation " + implementation.getSimpleName());
		}
	}

	@Override
	public ComponentMetamodel createMetamodel(final Class<?> componentType, final Class<?> implementation) {
		this.requireNoMetamodel(componentType, implementation);
		ComponentMetamodel metamodel = this.metamodelFactory.createMetamodel(componentType, implementation);
		this.attachToParent(metamodel);
		this.registerMetamodel(metamodel);
		this.notifyMetamodelCreated(metamodel);
		return metamodel;
	}

	@Override
	public ComponentMetamodel createRawMetamodel(final Class<?> componentType, final Class<?> implementation) {
		this.requireNoMetamodel(componentType, implementation);
		ComponentMetamodel metamodel = this.metamodelFactory.createRawMetamodel(componentType, implementation);
		this.attachToParent(metamodel);
		this.registerMetamodel(metamodel);
		this.notifyMetamodelCreated(metamodel);
		return metamodel;
	}

	@Override
	public ComponentMetamodel readMetamodel(final Class<?> componentType) {
		return this.metamodels.get(componentType);
	}

	@Override
	public void reorderComposedChildren(final Class<?> parentComponentType, final List<Class<?>> order) {
		ComponentMetamodel parent = this.readMetamodel(parentComponentType);
		RuntimeCheck.notNull(parent, "parent");
		List<Class<?>> currentTypes = parent.getComposedTypes();
		RuntimeCheck.isTrue(order.size() == currentTypes.size() && order.containsAll(currentTypes),
				"order must be a permutation of " + parentComponentType.getSimpleName() + "'s composed types");

		Map<Class<?>, ComponentMetamodel> byImplementation = new HashMap<>();
		for (ComponentMetamodel child : parent.getComposedMetamodels()) {
			byImplementation.put(child.getImplementation(), child);
		}
		List<ComponentMetamodel> reorderedMetamodels = new ArrayList<>();
		for (Class<?> implementation : order) {
			reorderedMetamodels.add(byImplementation.get(implementation));
		}

		parent.setComposedTypes(order);
		parent.setComposedMetamodels(reorderedMetamodels);
	}

	@Override
	public void updateMetamodel(final ComponentMetamodel metamodel) {
		ComponentMetamodel previousMetamodel = this.metamodels.get(metamodel.getComponentType());
		RuntimeCheck.notNull(previousMetamodel, "previousMetamodel");
		this.unregisterMetamodelIndexes(previousMetamodel);
		this.metamodelFactory.completeMetamodel(metamodel);
		this.registerMetamodel(metamodel);
		this.notifyMetamodelUpdated(metamodel);
	}

	@Override
	public ComponentMetamodel deleteMetamodel(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		ComponentMetamodel metamodel = this.metamodels.remove(componentType);
		RuntimeCheck.notNull(metamodel, "componentMetamodel");
		this.detachFromParent(metamodel);
		this.unregisterMetamodelIndexes(metamodel);
		this.notifyMetamodelDeleted(metamodel);
		return metamodel;
	}

	/**
	 * Fires {@link MetamodelEvents#onMetamodelCreated} when this manager has
	 * a wired producer for those events.
	 *
	 * @param metamodel the metamodel that was created
	 */
	private void notifyMetamodelCreated(final ComponentMetamodel metamodel) {
		if (this.produceMetamodelEvents != null) {
			this.produceMetamodelEvents.onMetamodelCreated(metamodel);
		}
	}

	/**
	 * Fires {@link MetamodelEvents#onMetamodelUpdated} when this manager has
	 * a wired producer for those events.
	 *
	 * @param metamodel the metamodel that was updated
	 */
	private void notifyMetamodelUpdated(final ComponentMetamodel metamodel) {
		if (this.produceMetamodelEvents != null) {
			this.produceMetamodelEvents.onMetamodelUpdated(metamodel);
		}
	}

	/**
	 * Fires {@link MetamodelEvents#onMetamodelDeleted} when this manager has
	 * a wired producer for those events.
	 *
	 * @param metamodel the metamodel that was deleted
	 */
	private void notifyMetamodelDeleted(final ComponentMetamodel metamodel) {
		if (this.produceMetamodelEvents != null) {
			this.produceMetamodelEvents.onMetamodelDeleted(metamodel);
		}
	}

	/**
	 * Looks for a registered metamodel that composes the given metamodel's
	 * implementation class and, when found, links the two as parent/child
	 * in the composition hierarchy.
	 *
	 * @param metamodel the newly created metamodel to attach
	 */
	private void attachToParent(final ComponentMetamodel metamodel) {
		Class<?> implementation = metamodel.getImplementation();
		for (ComponentMetamodel parent : this.metamodels.values()) {
			if (parent.getComposedTypes().contains(implementation)) {
				metamodel.setParentMetamodel(parent);
				List<ComponentMetamodel> children = new ArrayList<>(parent.getComposedMetamodels());
				children.add(metamodel);
				parent.setComposedMetamodels(children);
				break;
			}
		}
	}

	/**
	 * Removes the given metamodel from its parent's list of composed
	 * metamodels and clears its parent reference, if it has a parent.
	 *
	 * @param metamodel the metamodel being deleted
	 */
	private void detachFromParent(final ComponentMetamodel metamodel) {
		ComponentMetamodel parent = metamodel.getParentMetamodel();
		if (parent == null) {
			return;
		}
		List<ComponentMetamodel> siblings = new ArrayList<>(parent.getComposedMetamodels());
		siblings.remove(metamodel);
		parent.setComposedMetamodels(siblings);
		metamodel.setParentMetamodel(null);
	}

	/**
	 * Adds a metamodel to the {@link #metamodels} registry and (re)populates
	 * every port index (events, subscribers, services, parameters) from its
	 * aggregated ports.
	 *
	 * @param metamodel the metamodel to register
	 */
	private void registerMetamodel(final ComponentMetamodel metamodel) {
		this.metamodels.put(metamodel.getComponentType(), metamodel);
		this.implToType.put(metamodel.getImplementation(), metamodel.getComponentType());
		this.typeToImpl.put(metamodel.getComponentType(), metamodel.getImplementation());
		for (Class<?> eventsInterface : metamodel.getAllProducedEvents()) {
			Class<?> otherOwner = otherOwner(this.eventsToType, eventsInterface, metamodel.getComponentType());
			if (otherOwner != null) {
				this.useValidationServices.handleStructuralVerification(alreadyClaimedMessage("Events", "produced",
						"produce", eventsInterface, otherOwner, metamodel.getComponentType()));
			} else {
				this.metamodelForEvents.put(eventsInterface, metamodel);
				this.eventsToType.put(eventsInterface, metamodel.getComponentType());
				// A provided subscriber is nested in a produced events interface: it follows the
				// owner of that events interface, and is not indexed for a rejected claim.
				for (Class<?> subscribeInterface : metamodel.getAllProvidedSubscribers()) {
					if (eventsInterface.equals(metamodel.getProducedEventsForProvidedSubscriber(subscribeInterface))) {
						this.metamodelForSubscriber.put(subscribeInterface, metamodel);
					}
				}
			}
			this.eventsMetamodels.remove(eventsInterface);
		}
		for (Class<?> servicesInterface : metamodel.getAllProvidedServices()) {
			Class<?> otherOwner = otherOwner(this.servicesToType, servicesInterface, metamodel.getComponentType());
			if (otherOwner != null) {
				this.useValidationServices.handleStructuralVerification(alreadyClaimedMessage("Services", "provided",
						"provide", servicesInterface, otherOwner, metamodel.getComponentType()));
			} else {
				this.servicesToType.put(servicesInterface, metamodel.getComponentType());
			}
		}
		for (Class<?> parametersInterface : metamodel.getAllOfferedParameters()) {
			Class<?> otherOwner = otherOwner(this.parametersToType, parametersInterface, metamodel.getComponentType());
			if (otherOwner != null) {
				this.useValidationServices.handleStructuralVerification(alreadyClaimedMessage("Parameters", "offered",
						"offer", parametersInterface, otherOwner, metamodel.getComponentType()));
			} else {
				this.parametersToType.put(parametersInterface, metamodel.getComponentType());
			}
		}
	}

	/**
	 * Returns the component type currently owning {@code portInterface} in
	 * {@code index}, if it is owned by a component other than
	 * {@code componentType} - {@code null} when the interface is unclaimed,
	 * or already owned by that same component type (the normal case on an
	 * update, where the previous owner was already unregistered first by
	 * {@link #unregisterMetamodelIndexes}).
	 *
	 * @param index         the ownership index to check ({@link #eventsToType},
	 *                      {@link #servicesToType}, or {@link #parametersToType})
	 * @param portInterface the port interface being claimed
	 * @param componentType the component type claiming it
	 * @return the other, already-registered owner, or {@code null} when the
	 *         claim does not conflict with anything
	 */
	private static Class<?> otherOwner(final Map<Class<?>, Class<?>> index, final Class<?> portInterface,
			final Class<?> componentType) {
		Class<?> existingOwner = index.get(portInterface);
		return (existingOwner != null && !existingOwner.equals(componentType)) ? existingOwner : null;
	}

	/**
	 * Formats the structural/dynamic verification message reported when a
	 * component tries to produce, provide, or offer a services, events, or
	 * parameters interface that another, still-registered component already
	 * owns. Each such interface has exactly one owner; the earlier
	 * registration is kept and the later one is rejected rather than
	 * silently taking over.
	 *
	 * @param kind           "Events", "Services", or "Parameters", matching
	 *                       the interface kind
	 * @param pastParticiple "produced", "provided", or "offered"
	 * @param verb           "produce", "provide", or "offer"
	 * @param portInterface  the contested interface
	 * @param existingOwner  the component type that already owns it
	 * @param rejectedOwner  the component type whose claim is rejected
	 * @return the formatted verification message
	 */
	private static String alreadyClaimedMessage(final String kind, final String pastParticiple, final String verb,
			final Class<?> portInterface, final Class<?> existingOwner, final Class<?> rejectedOwner) {
		return kind + " " + portInterface.getSimpleName() + " is already " + pastParticiple + " by component "
				+ existingOwner.getSimpleName() + "; " + rejectedOwner.getSimpleName() + " cannot also " + verb
				+ " it. Each " + pastParticiple + " " + kind.toLowerCase() + " interface must have exactly one owner.";
	}

	/**
	 * Removes a metamodel's entries from every port index (events,
	 * subscribers, services, parameters) and from the implementation/type
	 * maps, without removing it from the {@link #metamodels} registry
	 * itself.
	 *
	 * @param metamodel the metamodel whose index entries are removed
	 */
	private void unregisterMetamodelIndexes(final ComponentMetamodel metamodel) {
		this.implToType.remove(metamodel.getImplementation());
		this.typeToImpl.remove(metamodel.getComponentType());
		// The 2-argument, value-checking Map.remove(key, value) below only
		// removes an entry that still points at this metamodel/component type.
		// A port interface this metamodel lost a claim on in registerMetamodel
		// (another, still-registered component already owned it) was never
		// its entry to begin with, so unregistering this metamodel must not
		// remove that other component's still-valid entry.
		for (Class<?> eventsInterface : metamodel.getAllProducedEvents()) {
			this.metamodelForEvents.remove(eventsInterface, metamodel);
			this.eventsToType.remove(eventsInterface, metamodel.getComponentType());
			this.eventsMetamodels.remove(eventsInterface);
		}
		for (Class<?> subscribeInterface : metamodel.getAllProvidedSubscribers()) {
			this.metamodelForSubscriber.remove(subscribeInterface, metamodel);
		}
		for (Class<?> servicesInterface : metamodel.getAllProvidedServices()) {
			this.servicesToType.remove(servicesInterface, metamodel.getComponentType());
		}
		for (Class<?> parametersInterface : metamodel.getAllOfferedParameters()) {
			this.parametersToType.remove(parametersInterface, metamodel.getComponentType());
		}
	}

	@Override
	public <SERVICES> void addProvidedServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(providerComponentType);
		// Ownership is checked BEFORE the component's own metamodel entry is mutated: a
		// rejected declaration (otherOwner != null) must leave that entry untouched, or the
		// rejected component ends up locally claiming to provide an interface it was never
		// actually granted - which then makes unregisterComponent throw later, instead of
		// being a clean no-op, when it tries to unprovide that phantom entry.
		Class<?> otherOwner = otherOwner(this.servicesToType, servicesInterface, providerComponentType);
		if (otherOwner != null) {
			this.useValidationServices.handleDynamicVerification(
					alreadyClaimedMessage("Services", "provided", "provide", servicesInterface, otherOwner,
							providerComponentType));
			// a rejected declaration changes nothing, so nothing is announced
			return;
		}
		boolean changed = false;
		if (!metamodel.getAllProvidedServices().contains(servicesInterface)) {
			List<Class<?>> providedServices = metamodel.getProvidedServices();
			providedServices.add(servicesInterface);
			metamodel.setProvidedServices(providedServices);
			changed = true;
		}
		if (!providerComponentType.equals(this.servicesToType.put(servicesInterface, providerComponentType))) {
			changed = true;
		}
		if (changed) {
			this.notifyMetamodelUpdated(metamodel);
		}
	}

	@Override
	public <SERVICES> void removeProvidedServices(final Class<?> providerComponentType,
			final Class<SERVICES> servicesInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(providerComponentType);
				Class<?> componentType = this.servicesToType.get(servicesInterface);
		if (componentType == null) {
			throw new IllegalArgumentException("Cannot remove services interface " + servicesInterface
					+ " from component type " + providerComponentType + ": no component provides it");
		}
		if (!componentType.equals(providerComponentType)) {
			throw new IllegalArgumentException("Component type " + providerComponentType
					+ " does not match services provider component type " + componentType);
		}
		this.servicesToType.remove(servicesInterface);

		if (metamodel.getProvidedServices().contains(servicesInterface)) {
			List<Class<?>> providedServices = metamodel.getProvidedServices();
			providedServices.remove(servicesInterface);
			metamodel.setProvidedServices(providedServices);
		}
		for (ComponentItemMetamodel item : metamodel.getItemMetamodels()) {
			if (item.getProvidedServices().contains(servicesInterface)) {
				List<Class<?>> providedServices = item.getProvidedServices();
				providedServices.remove(servicesInterface);
				item.setProvidedServices(providedServices);
			}
		}
		this.notifyMetamodelUpdated(metamodel);
	}

	@Override
	public <PARAMETERS> void addOfferedParameters(final Class<?> offeringComponentType,
			final Class<PARAMETERS> parametersInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(offeringComponentType);
		// Ownership is checked BEFORE the component's own metamodel entry is mutated - see
		// the identical comment in addProvidedServices above for why.
		Class<?> otherOwner = otherOwner(this.parametersToType, parametersInterface, offeringComponentType);
		if (otherOwner != null) {
			this.useValidationServices.handleDynamicVerification(
					alreadyClaimedMessage("Parameters", "offered", "offer", parametersInterface, otherOwner,
							offeringComponentType));
			// a rejected declaration changes nothing, so nothing is announced
			return;
		}
		boolean changed = false;
		if (!metamodel.getAllOfferedParameters().contains(parametersInterface)) {
			List<Class<?>> offeredParameters = metamodel.getOfferedParameters();
			offeredParameters.add(parametersInterface);
			metamodel.setOfferedParameters(offeredParameters);
			changed = true;
		}
		if (!offeringComponentType.equals(this.parametersToType.put(parametersInterface, offeringComponentType))) {
			changed = true;
		}
		if (changed) {
			this.notifyMetamodelUpdated(metamodel);
		}
	}

	@Override
	public <PARAMETERS> void removeOfferedParameters(final Class<?> offeringComponentType,
			final Class<PARAMETERS> parametersInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(offeringComponentType);
				Class<?> componentType = this.parametersToType.get(parametersInterface);
		if (componentType == null) {
			throw new IllegalArgumentException("Cannot remove parameters interface " + parametersInterface
					+ " from component type " + offeringComponentType + ": no component offers it");
		}
		if (!componentType.equals(offeringComponentType)) {
			throw new IllegalArgumentException("Component type " + offeringComponentType
					+ " does not match parameters offering component type " + componentType);
		}
		this.parametersToType.remove(parametersInterface);

		if (metamodel.getOfferedParameters().contains(parametersInterface)) {
			List<Class<?>> offeredParameters = metamodel.getOfferedParameters();
			offeredParameters.remove(parametersInterface);
			metamodel.setOfferedParameters(offeredParameters);
		}
		for (ComponentItemMetamodel item : metamodel.getItemMetamodels()) {
			if (item.getOfferedParameters().contains(parametersInterface)) {
				List<Class<?>> offeredParameters = item.getOfferedParameters();
				offeredParameters.remove(parametersInterface);
				item.setOfferedParameters(offeredParameters);
			}
		}
		this.notifyMetamodelUpdated(metamodel);
	}

	@Override
	public <EVENTS> void addProducedEvents(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(producerComponentType);
		// Ownership is checked BEFORE the component's own metamodel entry is mutated (and
		// before completing/wiring the metamodel from it) - see the identical comment in
		// addProvidedServices above for why.
		Class<?> otherOwner = otherOwner(this.eventsToType, eventsInterface, producerComponentType);
		if (otherOwner != null) {
			this.useValidationServices.handleDynamicVerification(
					alreadyClaimedMessage("Events", "produced", "produce", eventsInterface, otherOwner,
							producerComponentType));
		} else {
			if (!metamodel.getAllProducedEvents().contains(eventsInterface)) {
				List<Class<?>> producedEvents = metamodel.getProducedEvents();
				producedEvents.add(eventsInterface);
				metamodel.setProducedEvents(producedEvents);
			}
			this.metamodelFactory.completeMetamodel(metamodel);
			this.metamodelForEvents.put(eventsInterface, metamodel);
			this.eventsToType.put(eventsInterface, producerComponentType);
			for (Class<?> subscribeInterface : metamodel.getAllProvidedSubscribers()) {
				if (eventsInterface.equals(metamodel.getProducedEventsForProvidedSubscriber(subscribeInterface))) {
					this.metamodelForSubscriber.put(subscribeInterface, metamodel);
				}
			}
		}
		this.eventsMetamodels.remove(eventsInterface);
		this.notifyMetamodelUpdated(metamodel);
	}

	@Override
	public <EVENTS> void removeProducedEvents(final Class<?> producerComponentType,
			final Class<EVENTS> eventsInterface) {
				ComponentMetamodel metamodel = this.metamodelForEvents.get(eventsInterface);
		if (metamodel == null) {
			throw new IllegalArgumentException("Cannot remove events interface " + eventsInterface
					+ " from component type " + producerComponentType + ": no component produces it");
		}
		if (!metamodel.getComponentType().equals(producerComponentType)) {
			throw new IllegalArgumentException(
					"Component type " + producerComponentType + " does not produce events " + eventsInterface);
		}
		Class<?> componentType = this.eventsToType.get(eventsInterface);
		if (!componentType.equals(producerComponentType)) {
			throw new IllegalArgumentException("Component type " + producerComponentType
					+ " does not match events producer component type " + componentType);
		}
		this.metamodelForEvents.remove(eventsInterface);
		this.eventsToType.remove(eventsInterface);
		this.eventsMetamodels.remove(eventsInterface);
		metamodel.removeEventsMetamodel(eventsInterface);
		metamodel.removeProvidedSubscriberAndProducedEvents(eventsInterface);

		if (metamodel.getProducedEvents().contains(eventsInterface)) {
			List<Class<?>> producedEvents = metamodel.getProducedEvents();
			producedEvents.remove(eventsInterface);
			metamodel.setProducedEvents(producedEvents);
		}
		for (ComponentItemMetamodel item : metamodel.getItemMetamodels()) {
			item.removeProvidedSubscriberAndProducedEvents(eventsInterface);
			if (item.getProducedEvents().contains(eventsInterface)) {
				List<Class<?>> producedEvents = item.getProducedEvents();
				producedEvents.remove(eventsInterface);
				item.setProducedEvents(producedEvents);
			}
		}
		this.notifyMetamodelUpdated(metamodel);
	}

	@Override
	public <EVENTS> void addConsumedEvents(final Class<?> consumerComponentType,
			final Class<EVENTS> eventsInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(consumerComponentType);
		if (!metamodel.getAllConsumedEvents().contains(eventsInterface)) {
			List<Class<?>> consumedEvents = metamodel.getConsumedEvents();
			consumedEvents.add(eventsInterface);
			metamodel.setConsumedEvents(consumedEvents);
		}
		this.metamodelFactory.completeMetamodel(metamodel);
		this.notifyMetamodelUpdated(metamodel);
	}

	@Override
	public <EVENTS> void removeConsumedEvents(final Class<?> consumerComponentType,
			final Class<EVENTS> eventsInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(consumerComponentType);
		metamodel.removeUsedSubscriberAndConsumedEvents(eventsInterface);

		if (metamodel.getConsumedEvents().contains(eventsInterface)) {
			List<Class<?>> consumedEvents = metamodel.getConsumedEvents();
			consumedEvents.remove(eventsInterface);
			metamodel.setConsumedEvents(consumedEvents);
		}
		for (ComponentItemMetamodel item : metamodel.getItemMetamodels()) {
			item.removeUsedSubscriberAndConsumedEvents(eventsInterface);
			if (item.getConsumedEvents().contains(eventsInterface)) {
				List<Class<?>> consumedEvents = item.getConsumedEvents();
				consumedEvents.remove(eventsInterface);
				item.setConsumedEvents(consumedEvents);
			}
		}
		this.notifyMetamodelUpdated(metamodel);
	}

	@Override
	public <SERVICES> void addUsedServices(final Class<?> userComponentType, final Class<SERVICES> servicesInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(userComponentType);
		if (!metamodel.getAllUsedServices().contains(servicesInterface)) {
			List<Class<?>> usedServices = metamodel.getUsedServices();
			usedServices.add(servicesInterface);
			metamodel.setUsedServices(usedServices);
			this.notifyMetamodelUpdated(metamodel);
		}
	}

	@Override
	public <SERVICES> void removeUsedServices(final Class<?> userComponentType,
			final Class<SERVICES> servicesInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(userComponentType);
		if (metamodel.getUsedServices().contains(servicesInterface)) {
			List<Class<?>> usedServices = metamodel.getUsedServices();
			usedServices.remove(servicesInterface);
			metamodel.setUsedServices(usedServices);
			this.notifyMetamodelUpdated(metamodel);
		}
	}

	@Override
	public <PARAMETERS> void addAccessedParameters(final Class<?> accessorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(accessorComponentType);
		if (!metamodel.getAllAccessedParameters().contains(parametersInterface)) {
			List<Class<?>> accessedParameters = metamodel.getAccessedParameters();
			accessedParameters.add(parametersInterface);
			metamodel.setAccessedParameters(accessedParameters);
			this.notifyMetamodelUpdated(metamodel);
		}
	}

	@Override
	public <PARAMETERS> void removeAccessedParameters(final Class<?> accessorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		ComponentMetamodel metamodel = this.requireMetamodel(accessorComponentType);
		if (metamodel.getAccessedParameters().contains(parametersInterface)) {
			List<Class<?>> accessedParameters = metamodel.getAccessedParameters();
			accessedParameters.remove(parametersInterface);
			metamodel.setAccessedParameters(accessedParameters);
			this.notifyMetamodelUpdated(metamodel);
		}
	}

	/**
	 * Returns the registered metamodel for the given component type,
	 * failing fast when none is registered.
	 *
	 * @param componentType the component type
	 * @return the registered metamodel for that component type
	 * @throws IllegalStateException if no metamodel is registered for
	 *                               {@code componentType}
	 */
	private ComponentMetamodel requireMetamodel(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		RuntimeCheck.notNull(metamodel, "metamodel for " + (componentType == null ? null : componentType.getName()));
		return metamodel;
	}

	@Override
	public <EVENTS> EventsMetamodel<EVENTS> getEventsMetamodel(final Class<EVENTS> eventsInterface) {
		@SuppressWarnings("unchecked")
		EventsMetamodel<EVENTS> eventsMetamodel = (EventsMetamodel<EVENTS>) this.eventsMetamodels.get(eventsInterface);
		if (eventsMetamodel == null) {
			ComponentMetamodel metamodel = this.getMetamodelForProducedEvents(eventsInterface);
			if (metamodel == null) {
				// Raw (non-managed) events producer: there is no ComponentMetamodel to
				// delegate to, so build the EventsMetamodel the same way MetamodelFactory
				// does for a managed one, so raw producers get the same fire-and-forget
				// verification and @Cloned introspection as managed producers.
				eventsMetamodel = this.metamodelFactory.createEventsMetamodel(eventsInterface);
			} else {
				eventsMetamodel = metamodel.getEventsMetamodel(eventsInterface);
			}
			if (eventsMetamodel != null) {
				this.eventsMetamodels.put(eventsInterface, eventsMetamodel);
			}
		}
		return eventsMetamodel;
	}

	@Override
	public Set<Class<?>> getProvidedServices(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return ownedBy(metamodel.getAllProvidedServices(), this.servicesToType, componentType);
	}

	/**
	 * Keeps, among the port interfaces a component type declares, the ones
	 * it owns. An interface declared by several components has a single
	 * owner, the first one registered: the others declare it without ever
	 * owning it, so no port is created, activated, published, passivated
	 * or deleted on their behalf.
	 *
	 * @param declared      the port interfaces the component type declares
	 * @param index         the ownership index of that port kind
	 * @param componentType the component type
	 * @return the declared interfaces the component type owns
	 */
	private static Set<Class<?>> ownedBy(final Set<Class<?>> declared, final Map<Class<?>, Class<?>> index,
			final Class<?> componentType) {
		Set<Class<?>> owned = new HashSet<>();
		for (Class<?> portInterface : declared) {
			if (componentType.equals(index.get(portInterface))) {
				owned.add(portInterface);
			}
		}
		return owned;
	}

	@Override
	public Set<Class<?>> getUsedServices(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return metamodel.getAllUsedServices();
	}

	@Override
	public Set<Class<?>> getProducedEvents(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return ownedBy(metamodel.getAllProducedEvents(), this.eventsToType, componentType);
	}

	@Override
	public Set<Class<?>> getConsumedEvents(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return metamodel.getAllConsumedEvents();
	}

	@Override
	public Set<Class<?>> getOfferedParameters(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return ownedBy(metamodel.getAllOfferedParameters(), this.parametersToType, componentType);
	}

	@Override
	public Set<Class<?>> getAccessedParameters(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return metamodel.getAllAccessedParameters();
	}

	@Override
	public Set<Class<?>> getProvidedSubscribers(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return metamodel.getAllProvidedSubscribers();
	}

	@Override
	public Set<Class<?>> getUsedSubscribers(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return metamodel.getAllUsedSubscribers();
	}

	@Override
	public Set<Class<?>> getConsumedEventsWithoutSubscriber(final Class<?> componentType) {
		ComponentMetamodel metamodel = this.metamodels.get(componentType);
		return metamodel.getAllConsumedEventsWithoutSubscriber();
	}

	@Override
	public <EVENTS, SUBSCRIBE> Class<EVENTS> getEventsForSubscriber(final Class<SUBSCRIBE> subscribeInterface) {
		ComponentMetamodel metamodel = this.getMetamodelForSubscriber(subscribeInterface);
		if (metamodel == null) {
			throw new IllegalStateException(
					"No component found which provides subscriber: " + subscribeInterface.getSimpleName());
		}
		Class<EVENTS> eventsInterface = metamodel.getProducedEventsForProvidedSubscriber(subscribeInterface);
		return eventsInterface;
	}

	@Override
	public <EVENT, SUBSCRIBE> Class<SUBSCRIBE> getSubscribeInterface(final Class<EVENT> eventsInterface) {
		Class<SUBSCRIBE> subscribeInterface = null;
		ComponentMetamodel metamodel = this.getMetamodelForProducedEvents(eventsInterface);
		if (metamodel != null) {
			// subscribe interface can be found only if a producer component is previously
			// deployed
			@SuppressWarnings("unchecked")
			Class<SUBSCRIBE> subscribe = (Class<SUBSCRIBE>) metamodel
					.getProvidedSubscriberForProducedEvents(eventsInterface);
			subscribeInterface = subscribe;
		}

		if (subscribeInterface == null) {
			// use generic subscribe interface
			@SuppressWarnings("unchecked")
			Class<SUBSCRIBE> subscribe = (Class<SUBSCRIBE>) GenericSubscribe.class;
			subscribeInterface = subscribe;
		}
		return subscribeInterface;
	}

	@Override
	public <CT, SERVICES> Class<CT> getComponentTypeForProvidedServices(final Class<SERVICES> servicesInterface) {
		@SuppressWarnings("unchecked")
		Class<CT> componentType = (Class<CT>) this.servicesToType.get(servicesInterface);
		return componentType;
	}

	@Override
	public <CT, EVENTS> Class<CT> getComponentTypeForProducedEvents(final Class<EVENTS> eventsInterface) {
		@SuppressWarnings("unchecked")
		Class<CT> componentType = (Class<CT>) this.eventsToType.get(eventsInterface);
		return componentType;
	}

	@Override
	public <CT, SERVICES> Class<CT> getComponentTypeForOfferedParameters(final Class<SERVICES> parametersInterface) {
		@SuppressWarnings("unchecked")
		Class<CT> componentType = (Class<CT>) this.parametersToType.get(parametersInterface);
		return componentType;
	}

	/**
	 * Returns the component type registered for the given implementation
	 * class.
	 *
	 * @param <CT> the component type
	 * @param <CI> the implementation type
	 * @param implementation the component's implementation class
	 * @return the corresponding component type, or {@code null} if no
	 *         metamodel is registered for that implementation
	 */
	@Override
	public <CT, CI> Class<CT> getType(final Class<CI> implementation) {
		@SuppressWarnings("unchecked")
		Class<CT> componentType = (Class<CT>) this.implToType.get(implementation);
		return componentType;
	}

	/**
	 * Returns the implementation class registered for the given component
	 * type.
	 *
	 * @param <CT> the component type
	 * @param <CI> the implementation type
	 * @param componentType the component type
	 * @return the corresponding implementation class, or {@code null} if no
	 *         metamodel is registered for that component type
	 */
	@Override
	public <CT, CI> Class<CI> getImplementation(final Class<CT> componentType) {
		@SuppressWarnings("unchecked")
		Class<CI> implementation = (Class<CI>) this.typeToImpl.get(componentType);
		return implementation;
	}

	/**
	 * Looks up the metamodel of the component that produces the given
	 * events interface.
	 *
	 * @param <EVENT>         the events interface type
	 * @param eventsInterface the produced events interface
	 * @return the producing component's metamodel, or {@code null} if none
	 *         is registered
	 */
	private <EVENT> ComponentMetamodel getMetamodelForProducedEvents(final Class<EVENT> eventsInterface) {
		ComponentMetamodel metamodelForProducedEvents = this.metamodelForEvents.get(eventsInterface);
		return metamodelForProducedEvents;
	}

	/**
	 * Looks up the metamodel of the component that provides the given
	 * subscribe interface.
	 *
	 * @param <SUBSCRIBE>        the subscribe interface type
	 * @param subscribeInterface the provided subscribe interface
	 * @return the providing component's metamodel, or {@code null} if none
	 *         is registered
	 */
	private <SUBSCRIBE> ComponentMetamodel getMetamodelForSubscriber(final Class<SUBSCRIBE> subscribeInterface) {
		return this.metamodelForSubscriber.get(subscribeInterface);
	}

	/**
	 * Lists what is left behind once the business components are torn down: every
	 * entry describes something that is not an internal component. Run by the
	 * container's {@code stop()}, which reports the result in the shutdown report.
	 *
	 * @return the residues found, empty when only internal components remain
	 */
	public List<String> listNonInternalResidues() {
		List<String> residues = new ArrayList<>();
		List<Class<?>> producedEvents = new ArrayList<>();
		List<Class<?>> providedSubscribes = new ArrayList<>();
		for (Class<?> componentType : this.metamodels.keySet()) {
			ShutdownResidues.addIfNotInternal(residues, "Metamodel", "metamodel still registered for", componentType);
			producedEvents.addAll(this.getProducedEvents(componentType));
			providedSubscribes.addAll(this.getProvidedSubscribers(componentType));
		}
		for (Class<?> componentType : this.typeToImpl.keySet()) {
			ShutdownResidues.addIfNotInternal(residues, "Metamodel", "implementation still mapped for", componentType);
		}
		if (this.typeToImpl.size() != this.implToType.size()) {
			ShutdownResidues.add(residues, "Metamodel", "type-to-implementation index (" + this.typeToImpl.size()
					+ ") out of sync with implementation-to-type index (" + this.implToType.size() + ")");
		}
		for (Class<?> indexedEvents : this.metamodelForEvents.keySet()) {
			if (!producedEvents.contains(indexedEvents)) {
				ShutdownResidues.add(residues, "Metamodel",
						"events index entry without a registered producer: " + indexedEvents.getName());
			}
		}
		for (Class<?> indexedSubscribe : this.metamodelForSubscriber.keySet()) {
			if (!providedSubscribes.contains(indexedSubscribe)) {
				ShutdownResidues.add(residues, "Metamodel",
						"subscribe interface index entry without a registered provider: "
								+ indexedSubscribe.getName());
			}
		}
		for (Class<?> componentType : this.servicesToType.values()) {
			ShutdownResidues.addIfNotInternal(residues, "Metamodel", "services still owned by", componentType);
		}
		for (Class<?> componentType : this.eventsToType.values()) {
			ShutdownResidues.addIfNotInternal(residues, "Metamodel", "events still owned by", componentType);
		}
		for (Class<?> componentType : this.parametersToType.values()) {
			ShutdownResidues.addIfNotInternal(residues, "Metamodel", "parameters still owned by", componentType);
		}
		return residues;
	}


}
