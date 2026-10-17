/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Cloned;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentItemMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentItemMetamodelImpl;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentMetamodelImpl;
import org.opales.covalent.container.metamodel.metamodel.impl.EventsMetamodelImpl;
import org.opales.covalent.container.util.TriConsumer;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Builds {@link ComponentMetamodel} instances by reflectively introspecting
 * a component's implementation class (and, recursively, its child items
 * and superclasses/superinterfaces) for the container's structural
 * annotations ({@code @Services}, {@code @Events}, {@code @Parameters},
 * {@code @Subscribe}, {@code @Item}, {@code @Cloned}, etc). This is the
 * container's single source of metamodel construction: it walks interfaces
 * and fields to populate provided/used services, produced/consumed events,
 * offered/accessed parameters and provided/used/implemented subscribers,
 * then, in a second pass, introspects the produced events for clone
 * behavior and maps consumed events to used subscribers (and produced
 * events to provided subscribers) so the rest of the container can resolve
 * a port to the field or item that implements it.
 * <p>
 * This class is itself a Covalent item ({@code @Item}), wired with the
 * {@link ValidationServices} and {@link IntrospectionServices} it depends
 * on to validate structural rules and resolve a component's declared type.
 */
@Item
public class MetamodelFactory {
	/** Logger used to report non-fatal structural assumptions made during introspection. */
	private static Logger log = Logger.getLogger(MetamodelFactory.class.getName());

	/**
	 * Creates a metamodel factory, to be wired with its
	 * {@link ValidationServices} and {@link IntrospectionServices} before
	 * use.
	 */
	public MetamodelFactory() {
	}

	/** Used-services item service resolving a component implementation's declared component type. */
	protected IntrospectionServices introspectionServices;

	/** Used-services item service enforcing the container's structural rules during metamodel construction. */
	private ValidationServices validationServices;

	/**
	 * Injects the validation services used to enforce structural rules
	 * while building metamodels.
	 *
	 * @param validationServices the validation services to use
	 */
	void setValidationServices(final ValidationServices validationServices) {
		this.validationServices = validationServices;
	}

	/**
	 * Injects the introspection services used to resolve a component
	 * implementation's declared component type.
	 *
	 * @param introspectionServices the introspection services to use
	 */
	void setIntrospectionServices(final IntrospectionServices introspectionServices) {
		this.introspectionServices = introspectionServices;
	}

	/**
	 * Builds a complete metamodel for the given component type and
	 * implementation, checking that the implementation is properly
	 * annotated as a component implementation, then completes it (events
	 * introspection and subscriber/event mapping).
	 *
	 * @param componentType  the component type (interface or class) the
	 *                       metamodel describes
	 * @param implementation the component's implementation class
	 * @return the fully built and completed component metamodel
	 */
	public ComponentMetamodel createMetamodel(final Class<?> componentType, final Class<?> implementation) {
		ComponentMetamodel metamodel = this.createMetamodel(componentType, implementation, true);
		this.completeMetamodel(metamodel);
		return metamodel;
	}

	/**
	 * Builds a complete metamodel for the given component type and
	 * implementation, like {@link #createMetamodel(Class, Class)} but
	 * without checking that the implementation is annotated as a component
	 * implementation. Used where the implementation class is not expected
	 * to carry the usual component-implementation annotation (e.g. for
	 * synthetic implementations).
	 *
	 * @param componentType  the component type (interface or class) the
	 *                       metamodel describes
	 * @param implementation the component's implementation class
	 * @return the fully built and completed component metamodel
	 */
	public ComponentMetamodel createRawMetamodel(final Class<?> componentType, final Class<?> implementation) {
		ComponentMetamodel metamodel = this.createMetamodel(componentType, implementation, false);
		this.completeMetamodel(metamodel);
		return metamodel;
	}

	/**
	 * Creates a new {@link ComponentMetamodelImpl}, sets its implementation
	 * and component type, and fills it by introspecting the implementation
	 * class (items, composed components, and, for each, their ports).
	 *
	 * @param componentType                 the component type the metamodel
	 *                                      describes
	 * @param implementation                the component's implementation
	 *                                      class
	 * @param checkImplementationAnnotation whether to validate that
	 *                                      {@code implementation} carries
	 *                                      the component-implementation
	 *                                      annotation
	 * @return the filled (but not yet completed) component metamodel
	 */
	private ComponentMetamodel createMetamodel(final Class<?> componentType, final Class<?> implementation,
			final boolean checkImplementationAnnotation) {
		ComponentMetamodel metamodel = new ComponentMetamodelImpl();
		metamodel.setImplementation(implementation);
		metamodel.setComponentType(componentType);
		this.fillMetamodel(metamodel, checkImplementationAnnotation);
		return metamodel;
	}

	/**
	 * Fills a component metamodel: introspects the component's own ports
	 * (via {@link #fillItemMetamodel}), validates the implementation
	 * annotation (unless skipped), resolves the component type from the
	 * implementation when not already set, discovers child item and
	 * composed-component fields, builds and fills a metamodel for each
	 * child item, and validates each composed component class.
	 *
	 * @param metamodel                     the component metamodel to fill
	 * @param checkImplementationAnnotation whether to validate the
	 *                                      component-implementation
	 *                                      annotation
	 */
	private void fillMetamodel(final ComponentMetamodel metamodel, final boolean checkImplementationAnnotation) {
		this.fillItemMetamodel(metamodel);
		Class<?> implementation = metamodel.getImplementation();

		if (checkImplementationAnnotation) {
			this.validationServices.checkImplementation(implementation);
		}

		if (metamodel.getComponentType() == null) {
			metamodel.setComponentType(this.introspectionServices.getComponentType(implementation));
		}

		if (checkImplementationAnnotation) {
			this.validationServices.checkComponentType(metamodel.getComponentType());
		}

		Map<Predicate<Field>, TriConsumer<ComponentMetamodel, Class<?>, Field>> fieldsSeeker = new LinkedHashMap<>();
		fieldsSeeker.put(ComponentAnnotations::isItemField, this::addItem);
		fieldsSeeker.put(ComponentAnnotations::isImplementationField, this::addComposedComponent);
		this.fillMetamodelWithFields(implementation, metamodel, fieldsSeeker);

		List<Class<?>> itemsTypes = metamodel.getItemTypes();
		List<ComponentItemMetamodel> itemMetamodels = new ArrayList<>();
		for (Class<?> itemClass : itemsTypes) {
			this.validationServices.checkItem(itemClass);

			ComponentItemMetamodel itemMetamodel = new ComponentItemMetamodelImpl();
			itemMetamodel.setImplementation(itemClass);
			this.fillItemMetamodel(itemMetamodel);

			itemMetamodels.add(itemMetamodel);
		}
		metamodel.setItemMetamodels(itemMetamodels);

		for (Class<?> composedComponentClass : metamodel.getComposedTypes()) {
			this.validationServices.checkComposedComponent(composedComponentClass);
		}
	}

	/**
	 * Second-pass completion of an already filled metamodel: builds the
	 * {@link org.opales.covalent.container.metamodel.metamodel.EventsMetamodel}
	 * for each produced events interface, then maps consumed events to
	 * used subscribers and produced events to provided subscribers. Split
	 * from {@link #fillMetamodel} because these mappings require the full
	 * (aggregated) port sets, which are only available once the whole
	 * item/composed-component tree has been filled.
	 *
	 * @param metamodel the metamodel to complete
	 */
	public void completeMetamodel(final ComponentMetamodel metamodel) {
		this.introspectMetamodelEvents(metamodel);
		this.mapConsumedEventsAndUsedSubscribers(metamodel);
		this.mapProducedEventsAndProvidedSubscribers(metamodel);
	}

	/**
	 * Fills an item (or component, since {@link ComponentMetamodel}
	 * extends {@link ComponentItemMetamodel}) metamodel by walking the
	 * implementation class's interfaces (recursively, including
	 * superclasses) for structural annotations, and the fields declared in
	 * its class hierarchy for the field-based ports (used services,
	 * accessed parameters, produced events, used subscribers). Also looks
	 * for subscribe interfaces nested inside each produced events
	 * interface, since that is the only place a provided subscriber may be
	 * declared.
	 *
	 * @param metamodel the item or component metamodel to fill
	 */
	private void fillItemMetamodel(final ComponentItemMetamodel metamodel) {
		Class<?> implementation = metamodel.getImplementation();

		Map<Predicate<Class<?>>, BiConsumer<ComponentItemMetamodel, Class<?>>> interfacesSeeker = new LinkedHashMap<>();
		interfacesSeeker.put(ComponentAnnotations::isServices, this::addProvidedServices);
		interfacesSeeker.put(ComponentAnnotations::isParameters, this::addOfferedParameters);
		interfacesSeeker.put(ComponentAnnotations::isEvents, this::addConsumedEvents);
		interfacesSeeker.put(ComponentAnnotations::isSubscribe, this::addImplementedSubscriber);

		this.fillMetamodel(implementation, metamodel, interfacesSeeker, new HashSet<>());

		Map<Predicate<Field>, TriConsumer<ComponentItemMetamodel, Class<?>, Field>> fieldsSeeker = new LinkedHashMap<>();
		fieldsSeeker.put(ComponentAnnotations::isServicesField, this::addUsedServices);
		fieldsSeeker.put(ComponentAnnotations::isParametersField, this::addAccessedParameters);
		fieldsSeeker.put(ComponentAnnotations::isEventsField, this::addProducedEvents);
		fieldsSeeker.put(ComponentAnnotations::isSubscribeField, this::addUsedSubscriber);

		this.fillMetamodelWithFields(implementation, metamodel, fieldsSeeker);

		// look for subscribe interfaces, only nested in events interfaces
		Map<Predicate<Class<?>>, BiConsumer<ComponentItemMetamodel, Class<?>>> subscribeInterfaceSeeker = new LinkedHashMap<>();
		subscribeInterfaceSeeker.put(ComponentAnnotations::isSubscribe, this::addProvidedSubscriber);
		for (Class<?> eventsInterface : metamodel.getProducedEvents()) {
			this.fillMetamodelWithNestedClasses(eventsInterface, metamodel, subscribeInterfaceSeeker);
		}
	}

	/**
	 * Validates and records a services interface discovered on the
	 * implementation as a provided service.
	 *
	 * @param metamodel         the item/component metamodel being filled
	 * @param servicesInterface the discovered {@code @Services} interface
	 */
	private void addProvidedServices(final ComponentItemMetamodel metamodel, final Class<?> servicesInterface) {
		this.validationServices.checkServices(servicesInterface);
		List<Class<?>> providedServices = metamodel.getProvidedServices();
		providedServices.add(servicesInterface);
		metamodel.setProvidedServices(providedServices);
	}

	/**
	 * Records a {@code @UsedServices}-annotated field as a used service,
	 * rejecting a second field for the same services interface (an item
	 * may use a given services interface through only one field).
	 *
	 * @param metamodel         the item/component metamodel being filled
	 * @param servicesInterface the used services interface (the field's type)
	 * @param field             the field declaring the usage
	 * @throws IllegalStateException if a field for this services interface
	 *                               was already recorded
	 */
	private void addUsedServices(final ComponentItemMetamodel metamodel, final Class<?> servicesInterface,
			final Field field) {
		if (metamodel.getUsedServicesField(servicesInterface) != null) {
			throw new IllegalStateException(
					"A services user cannot have more than one @" + UsedServices.class.getSimpleName()
							+ " field for services interface "
							+ servicesInterface + " in item " + metamodel.getImplementation().getSimpleName());
		}
		List<Class<?>> usedServices = metamodel.getUsedServices();
		usedServices.add(servicesInterface);
		metamodel.setUsedServices(usedServices);
		metamodel.addUsedServicesField(servicesInterface, field);
	}

	/**
	 * Validates and records a parameters interface discovered on the
	 * implementation as an offered parameters interface.
	 *
	 * @param metamodel           the item/component metamodel being filled
	 * @param parametersInterface the discovered {@code @Parameters} interface
	 */
	private void addOfferedParameters(final ComponentItemMetamodel metamodel, final Class<?> parametersInterface) {
		this.validationServices.checkParameters(parametersInterface);
		List<Class<?>> offeredParameters = metamodel.getOfferedParameters();
		offeredParameters.add(parametersInterface);
		metamodel.setOfferedParameters(offeredParameters);
	}

	/**
	 * Records a {@code @AccessedParameters}-annotated field as an accessed
	 * parameters usage, rejecting a second field for the same parameters
	 * interface.
	 *
	 * @param metamodel           the item/component metamodel being filled
	 * @param parametersInterface the accessed parameters interface (the
	 *                            field's type)
	 * @param field               the field declaring the access
	 * @throws IllegalStateException if a field for this parameters
	 *                               interface was already recorded
	 */
	private void addAccessedParameters(final ComponentItemMetamodel metamodel, final Class<?> parametersInterface,
			final Field field) {
		if (metamodel.getAccessedParametersField(parametersInterface) != null) {
			throw new IllegalStateException(
					"A parameters accessor cannot have more than one @" + AccessedParameters.class.getSimpleName()
							+ " field for parameters interface "
							+ parametersInterface + " in item " + metamodel.getImplementation().getSimpleName());
		}
		List<Class<?>> accessedParameters = metamodel.getAccessedParameters();
		accessedParameters.add(parametersInterface);
		metamodel.setAccessedParameters(accessedParameters);
		metamodel.addAccessedParametersField(parametersInterface, field);
	}

	/**
	 * Records an events interface discovered on the implementation as a
	 * consumed events interface.
	 *
	 * @param metamodel       the item/component metamodel being filled
	 * @param eventsInterface the discovered {@code @Events} interface
	 */
	private void addConsumedEvents(final ComponentItemMetamodel metamodel, final Class<?> eventsInterface) {
		List<Class<?>> consumedEvents = metamodel.getConsumedEvents();
		consumedEvents.add(eventsInterface);
		metamodel.setConsumedEvents(consumedEvents);
	}

	/**
	 * Validates and records a {@code @ProducedEvents}-annotated field as a
	 * produced events usage, rejecting a second field for the same events
	 * interface.
	 *
	 * @param metamodel       the item/component metamodel being filled
	 * @param eventsInterface the produced events interface (the field's type)
	 * @param field           the field declaring the production
	 * @throws IllegalStateException if a field for this events interface
	 *                               was already recorded
	 */
	private void addProducedEvents(final ComponentItemMetamodel metamodel, final Class<?> eventsInterface,
			final Field field) {
		this.validationServices.checkEvents(eventsInterface);

		if (metamodel.getProducedEventsField(eventsInterface) != null) {
			throw new IllegalStateException(
					"An events producer cannot have more than one @" + ProducedEvents.class.getSimpleName()
							+ " field for events interface "
							+ eventsInterface + " in item " + metamodel.getImplementation().getSimpleName());
		}

		List<Class<?>> producedEvents = metamodel.getProducedEvents();
		producedEvents.add(eventsInterface);
		metamodel.setProducedEvents(producedEvents);
		metamodel.addProducedEventsField(eventsInterface, field);
	}

	/**
	 * Validates and records a subscribe interface, found nested inside a
	 * produced events interface, as a provided subscribe interface.
	 *
	 * @param metamodel          the item/component metamodel being filled
	 * @param subscribeInterface the discovered {@code @Subscribe} interface
	 */
	private void addProvidedSubscriber(final ComponentItemMetamodel metamodel, final Class<?> subscribeInterface) {
		this.validationServices.checkSubscribe(subscribeInterface);
		List<Class<?>> providedSubscribers = metamodel.getProvidedSubscribers();
		providedSubscribers.add(subscribeInterface);
		metamodel.setProvidedSubscribers(providedSubscribers);
	}

	/**
	 * Records a {@code @UsedEventsSubscribe}-annotated field as a used
	 * subscriber, rejecting a second field for the same subscribe
	 * interface.
	 *
	 * @param metamodel          the item/component metamodel being filled
	 * @param subscribeInterface the used subscribe interface (the field's
	 *                           type)
	 * @param field              the field declaring the usage
	 * @throws IllegalStateException if a field for this subscribe
	 *                               interface was already recorded
	 */
	private void addUsedSubscriber(final ComponentItemMetamodel metamodel, final Class<?> subscribeInterface,
			final Field field) {
		if (metamodel.getUsedSubscriberField(subscribeInterface) != null) {
			throw new IllegalStateException(
					"A subscriber cannot have more than one @" + UsedEventsSubscribe.class.getSimpleName()
							+ " field for subscribe interface "
							+ subscribeInterface + " in item " + metamodel.getImplementation().getSimpleName());
		}
		List<Class<?>> usedSubscribers = metamodel.getUsedSubscribers();
		usedSubscribers.add(subscribeInterface);
		metamodel.setUsedSubscribers(usedSubscribers);
		metamodel.addUsedSubscriberField(subscribeInterface, field);
	}

	/**
	 * Records a subscribe interface implemented directly by the
	 * implementation class (a subscribe delegate) as an implemented
	 * subscriber.
	 *
	 * @param metamodel          the item/component metamodel being filled
	 * @param subscribeInterface the implemented {@code @Subscribe} interface
	 */
	private void addImplementedSubscriber(final ComponentItemMetamodel metamodel, final Class<?> subscribeInterface) {
		List<Class<?>> implementedSubscribers = metamodel.getImplementedSubscribers();
		implementedSubscribers.add(subscribeInterface);
		metamodel.setImplementedSubscribers(implementedSubscribers);
	}

	/**
	 * Records an {@code @Item}-annotated field as declaring a child item.
	 *
	 * @param metamodel the component metamodel being filled
	 * @param itemClass the item implementation class (the field's type)
	 * @param field     the field declaring the item
	 */
	private void addItem(final ComponentMetamodel metamodel, final Class<?> itemClass, final Field field) {
		if (metamodel.getItemTypes().contains(itemClass)) {
			throw new IllegalStateException("Component implementation " + metamodel.getImplementation().getSimpleName()
					+ " declares more than one @" + Item.class.getSimpleName() + " field of type "
					+ itemClass.getSimpleName() + " (" + metamodel.getItemField(itemClass).getName() + ", "
					+ field.getName() + "). Only one @" + Item.class.getSimpleName()
					+ " field per item type is supported.");
		}
		List<Class<?>> itemTypes = metamodel.getItemTypes();
		itemTypes.add(itemClass);
		metamodel.setItemTypes(itemTypes);
		metamodel.addItemField(itemClass, field);
	}

	/**
	 * Records a field declaring a composed (implementation-role) component
	 * as a composed component.
	 *
	 * @param metamodel     the component metamodel being filled
	 * @param composedClass the composed component's implementation class
	 *                      (the field's type)
	 * @param field         the field declaring the composed component
	 */
	private void addComposedComponent(final ComponentMetamodel metamodel, final Class<?> composedClass,
			final Field field) {
		if (metamodel.getComposedTypes().contains(composedClass)) {
			throw new IllegalStateException("Component implementation " + metamodel.getImplementation().getSimpleName()
					+ " declares more than one @" + ComposedComponent.class.getSimpleName() + " field of type "
					+ composedClass.getSimpleName() + " ("
					+ metamodel.getComposedComponentField(composedClass).getName() + ", " + field.getName()
					+ "). Only one @" + ComposedComponent.class.getSimpleName()
					+ " field per composed type is supported.");
		}
		List<Class<?>> composedTypes = metamodel.getComposedTypes();
		composedTypes.add(composedClass);
		metamodel.setComposedTypes(composedTypes);
		metamodel.addComposedComponentField(composedClass, field);
	}

	/**
	 * Walks the interfaces directly implemented by {@code classOrInterface}
	 * (dispatching each one, and its own super-interfaces, through
	 * {@code interfacesSeeker}), then recurses into its superclasses so
	 * that inherited interfaces are picked up as well.
	 *
	 * @param classOrInterface the class to inspect
	 * @param metamodel        the item/component metamodel being filled
	 * @param interfacesSeeker predicate/action pairs matching a structural
	 *                         annotation (e.g. {@code @Services}) to the
	 *                         method recording it
	 * @param visited          the interfaces already dispatched, so that an
	 *                         interface reachable through several paths is
	 *                         recorded once
	 */
	private void fillMetamodel(final Class<?> classOrInterface, final ComponentItemMetamodel metamodel,
			final Map<Predicate<Class<?>>, BiConsumer<ComponentItemMetamodel, Class<?>>> interfacesSeeker,
			final Set<Class<?>> visited) {
		List<Class<?>> componentInterfaces = Arrays.asList(classOrInterface.getInterfaces());
		for (Class<?> componentInterface : componentInterfaces) {
			this.fillMetamodelWithInterface(componentInterface, metamodel, interfacesSeeker, visited);
		}
		// recurse in superclasses
		this.fillMetamodelWithSuperclasses(classOrInterface, metamodel, interfacesSeeker, visited);

	}

	/**
	 * Applies {@code fieldsSeeker} to every field declared on
	 * {@code classOrInterface} and on its superclasses (stopping at
	 * {@link Object}), whatever their visibility. Superclass fields are
	 * visited first, as Java initializes them first.
	 *
	 * @param <MM>             the metamodel type being filled
	 * @param classOrInterface the class whose declared fields are inspected
	 * @param metamodel        the metamodel being filled
	 * @param fieldsSeeker     predicate/action pairs matching a
	 *                         field-based annotation (e.g.
	 *                         {@code @UsedServices}) to the method
	 *                         recording it
	 */
	private <MM extends ComponentItemMetamodel> void fillMetamodelWithFields(final Class<?> classOrInterface,
			final MM metamodel, final Map<Predicate<Field>, TriConsumer<MM, Class<?>, Field>> fieldsSeeker) {
		if (fieldsSeeker == null) {
			return;
		}
		Class<?> superClass = classOrInterface.getSuperclass();
		if (superClass != null && !Object.class.equals(superClass)) {
			this.fillMetamodelWithFields(superClass, metamodel, fieldsSeeker);
		}
		for (Field field : classOrInterface.getDeclaredFields()) {
			this.fillMetamodelWithField(field, metamodel, fieldsSeeker);
		}
	}

	/**
	 * Tests a single field against every predicate in {@code fieldsSeeker}
	 * and invokes the matching action(s) with the field's declared type.
	 * A field may match more than one predicate.
	 *
	 * @param <MM>         the metamodel type being filled
	 * @param field        the field to test
	 * @param metamodel    the metamodel being filled
	 * @param fieldsSeeker predicate/action pairs to test the field against
	 */
	private <MM extends ComponentItemMetamodel> void fillMetamodelWithField(final Field field, final MM metamodel,
			final Map<Predicate<Field>, TriConsumer<MM, Class<?>, Field>> fieldsSeeker) {
		for (Map.Entry<Predicate<Field>, TriConsumer<MM, Class<?>, Field>> entry : fieldsSeeker.entrySet()) {
			Predicate<Field> predicate = entry.getKey();
			TriConsumer<MM, Class<?>, Field> triConsumer = entry.getValue();
			if (predicate.test(field)) {
				triConsumer.accept(metamodel, field.getType(), field);
			}
		}
	}

	/**
	 * Tests a single interface against every predicate in
	 * {@code interfacesSeeker} and invokes the matching action(s), then
	 * recurses into the interface's own super-interfaces so that a
	 * multi-level interface hierarchy (e.g. a {@code @Services} interface
	 * extending another) is fully explored.
	 *
	 * @param anInterface      the interface to test
	 * @param metamodel        the item/component metamodel being filled
	 * @param interfacesSeeker predicate/action pairs to test the interface
	 *                         against
	 * @param visited          the interfaces already dispatched, so that an
	 *                         interface reachable through several paths is
	 *                         recorded once
	 */
	private void fillMetamodelWithInterface(final Class<?> anInterface, final ComponentItemMetamodel metamodel,
			final Map<Predicate<Class<?>>, BiConsumer<ComponentItemMetamodel, Class<?>>> interfacesSeeker,
			final Set<Class<?>> visited) {
		if (!visited.add(anInterface)) {
			return;
		}
		for (Map.Entry<Predicate<Class<?>>, BiConsumer<ComponentItemMetamodel, Class<?>>> entry : interfacesSeeker
				.entrySet()) {
			Predicate<Class<?>> predicate = entry.getKey();
			BiConsumer<ComponentItemMetamodel, Class<?>> biConsumer = entry.getValue();
			if (predicate.test(anInterface)) {
				biConsumer.accept(metamodel, anInterface);
			}
		}
		// recurse in super interfaces
		List<Class<?>> componentInterfaces = Arrays.asList(anInterface.getInterfaces());
		for (Class<?> componentInterface : componentInterfaces) {
			this.fillMetamodelWithInterface(componentInterface, metamodel, interfacesSeeker, visited);
		}

	}

	/**
	 * Applies {@code interfacesSeeker} to every nested interface declared
	 * inside {@code classOrInterface} (nested classes are skipped). Used
	 * to find a {@code @Subscribe} interface nested inside a produced
	 * events interface, which is the only place a provided subscriber may
	 * be declared.
	 *
	 * @param classOrInterface the enclosing class/interface to inspect
	 * @param metamodel        the item/component metamodel being filled
	 * @param interfacesSeeker predicate/action pairs to test each nested
	 *                         interface against
	 */
	private void fillMetamodelWithNestedClasses(final Class<?> classOrInterface, final ComponentItemMetamodel metamodel,
			final Map<Predicate<Class<?>>, BiConsumer<ComponentItemMetamodel, Class<?>>> interfacesSeeker) {
		Class<?>[] nestedClasses = classOrInterface.getDeclaredClasses();
		Set<Class<?>> visited = new HashSet<>();
		for (Class<?> nested : nestedClasses) {
			if (!nested.isInterface())
				continue;
			this.fillMetamodelWithInterface(nested, metamodel, interfacesSeeker, visited);
		}
	}

	/**
	 * Recurses into {@code classOrInterface}'s superclass (stopping at
	 * {@link Object}), re-running {@link #fillMetamodel(Class, ComponentItemMetamodel, Map, Set)}
	 * on it so that interfaces implemented by a superclass are picked up
	 * too.
	 *
	 * @param classOrInterface the class whose superclass is followed
	 * @param metamodel        the item/component metamodel being filled
	 * @param interfacesSeeker predicate/action pairs forwarded to the
	 *                         superclass walk
	 * @param visited          the interfaces already dispatched, so that an
	 *                         interface reachable through several paths is
	 *                         recorded once
	 */
	private void fillMetamodelWithSuperclasses(final Class<?> classOrInterface, final ComponentItemMetamodel metamodel,
			final Map<Predicate<Class<?>>, BiConsumer<ComponentItemMetamodel, Class<?>>> interfacesSeeker,
			final Set<Class<?>> visited) {
		Class<?> superClass = classOrInterface.getSuperclass();
		if (superClass != null && !Object.class.equals(superClass)) {
			this.fillMetamodel(superClass, metamodel, interfacesSeeker, visited);
		}
	}

	/**
	 * Builds and attaches an {@link EventsMetamodel} for every events
	 * interface produced by the component or its child items.
	 *
	 * @param <EVENTS>  the events interface type (inferred per iteration)
	 * @param metamodel the component metamodel to complete
	 */
	private <EVENTS> void introspectMetamodelEvents(final ComponentMetamodel metamodel) {
		for (Class<?> allEvents : metamodel.getAllProducedEvents()) {
			@SuppressWarnings("unchecked")
			Class<EVENTS> eventsInterface = (Class<EVENTS>) allEvents;
			EventsMetamodel<EVENTS> eventsMetamodel = this.createEventsMetamodel(eventsInterface);
			metamodel.addEventsMetamodel(eventsMetamodel);
		}
	}

	/**
	 * Creates the {@link EventsMetamodel} for a produced events interface:
	 * verifies the interface respects the fire-and-forget contract, then
	 * introspects its clone behavior (whole-interface, per-method or
	 * per-parameter {@code @Cloned}). Package-visible (not {@code private})
	 * because {@link MetamodelManagerImpl#getEventsMetamodel} also calls it
	 * directly for a raw (non-managed) events producer, which has no
	 * {@link ComponentMetamodel} of its own to build this through.
	 *
	 * @param <EVENTS>        the events interface type
	 * @param eventsInterface the events interface to describe
	 * @return the built events metamodel
	 */
	<EVENTS> EventsMetamodel<EVENTS> createEventsMetamodel(final Class<EVENTS> eventsInterface) {
		this.verifyEventsInterface(eventsInterface);
		EventsMetamodelImpl<EVENTS> eventsMetamodel = new EventsMetamodelImpl<>(eventsInterface);

		boolean hasCloneableElement = this.introspectEventsForCloneable(eventsMetamodel);
		eventsMetamodel.setHasCloneableElements(hasCloneableElement);
		if (eventsInterface.isAnnotationPresent(Cloned.class)) {
			eventsMetamodel.setCloneable();
		}
		return eventsMetamodel;
	}

	/**
	 * Verifies that every method of an events interface respects the
	 * fire-and-forget paradigm: {@code void} return type and no declared
	 * exceptions. Violations are reported through
	 * {@link ValidationServices#handleStructuralVerification}, so, depending
	 * on the configured validation policy, this may warn or throw.
	 *
	 * @param <EVENTS>        the events interface type
	 * @param eventsInterface the events interface to verify
	 */
	private <EVENTS> void verifyEventsInterface(final Class<EVENTS> eventsInterface) {
		Method[] methods = eventsInterface.getMethods();
		for (Method method : methods) {
			if (!method.getReturnType().equals(Void.TYPE)) {
				String message = "Return of event method " + method.getName() + " from "
						+ eventsInterface.getSimpleName()
						+ " is not void, which is in contradiction with the fire & forget paradigm";
				this.validationServices.handleStructuralVerification(message);
			}
			if (method.getExceptionTypes().length != 0) {
				String message = "Event method " + method.getName() + " from " + eventsInterface.getSimpleName()
						+ " throws exceptions, which is in contradiction with the fire & forget paradigm";
				this.validationServices.handleStructuralVerification(message);
			}
		}
	}

	/**
	 * Determines whether an events interface has at least one cloneable
	 * element: either the interface itself is annotated {@code @Cloned}, or
	 * one of its methods is, or one of a method's parameters is (the first
	 * match found stops the search).
	 *
	 * @param <EVENTS>       the events interface type
	 * @param eventsMetamodel the events metamodel under construction
	 * @return {@code true} if a cloneable element was found
	 */
	private <EVENTS> boolean introspectEventsForCloneable(final EventsMetamodel<EVENTS> eventsMetamodel) {
		boolean hasCloneableElement = false;

		if (eventsMetamodel.getEvents().isAnnotationPresent(Cloned.class)) {
			hasCloneableElement = true;
		} else {
			external: for (Method method : eventsMetamodel.getEvents().getMethods()) {
				if (method.isAnnotationPresent(Cloned.class)) {
					hasCloneableElement = true;
					break;
				} else {
					for (Parameter parameter : method.getParameters()) {
						if (parameter.isAnnotationPresent(Cloned.class)) {
							hasCloneableElement = true;
							break external;
						}
					}
				}
			}
		}
		return hasCloneableElement;
	}

	/**
	 * For every used subscriber interface, resolves the events interface
	 * it is meant to subscribe to (via {@link #getEventsForSubscribe}) and,
	 * if that events interface is indeed among the component's consumed
	 * events, records the mapping so consumers can be matched to their
	 * subscribers.
	 *
	 * @param metamodel the component metamodel to map
	 */
	private void mapConsumedEventsAndUsedSubscribers(final ComponentMetamodel metamodel) {
		for (Class<?> subscribeInterface : metamodel.getAllUsedSubscribers()) {
			Class<?> eventsInterface = this.getEventsForSubscribe(subscribeInterface);
			if (metamodel.getAllConsumedEvents().contains(eventsInterface)) {
				metamodel.mapUsedSubscriberAndConsumedEvents(subscribeInterface, eventsInterface);
			}
		}
	}

	/**
	 * For every provided subscriber interface, resolves the events
	 * interface it corresponds to (via {@link #getEventsForSubscribe}) and
	 * records the mapping, so producers can be matched to the subscribers
	 * they trigger.
	 *
	 * @param metamodel the component metamodel to map
	 */
	private void mapProducedEventsAndProvidedSubscribers(final ComponentMetamodel metamodel) {
		for (Class<?> subscribeInterface : metamodel.getAllProvidedSubscribers()) {
			Class<?> eventsInterface = this.getEventsForSubscribe(subscribeInterface);
			if (eventsInterface != null) {
				metamodel.mapProvidedSubscriberAndProducedEvents(subscribeInterface, eventsInterface);
			}
		}
	}

	/**
	 * Resolves the events interface a subscribe interface is associated
	 * with: a subscribe interface must be nested inside its
	 * {@code @Events} interface. If it is not, the problem is reported
	 * through {@link ValidationServices#handleStructuralVerification} and
	 * {@code null} is returned. Otherwise the first parameter of every
	 * {@code subscribe*} and {@code unsubscribe*} method (the observer) must
	 * be exactly that events interface, or a structural verification problem
	 * is reported.
	 *
	 * @param subscribeInterface the subscribe interface to resolve
	 * @return the enclosing events interface, or {@code null} if the
	 *         subscribe interface is not nested inside an events interface
	 */
	private Class<?> getEventsForSubscribe(final Class<?> subscribeInterface) {
		Class<?> enclosing = subscribeInterface.getEnclosingClass();
		if (enclosing == null || !ComponentAnnotations.isEvents(enclosing)) {
			String message = "Subscribe interface " + subscribeInterface.getSimpleName()
					+ " must be nested inside its events interface (annotated with @" + Events.class.getSimpleName()
					+ ").";
			this.validationServices.handleStructuralVerification(message);
			return null;
		}
		for (Method method : subscribeInterface.getMethods()) {
			String name = method.getName();
			if (!name.startsWith("subscribe") && !name.startsWith("unsubscribe")) {
				continue;
			}
			if (method.getParameterCount() == 0 || !method.getParameterTypes()[0].equals(enclosing)) {
				String message = "First parameter (the observer) of method " + name + " of subscribe interface "
						+ subscribeInterface.getSimpleName() + " must be its events interface "
						+ enclosing.getSimpleName() + ".";
				this.validationServices.handleStructuralVerification(message);
			}
		}
		return enclosing;
	}

}
