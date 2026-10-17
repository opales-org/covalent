/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.registry.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.exceptions.ReflectionException;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentItemMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.registry.ItemAndField;
import org.opales.covalent.container.registry.ItemDescriptor;
import org.opales.covalent.container.util.CovalentDiagnostics;
import org.opales.covalent.container.util.Reflection;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Registry-internal factory that builds and maintains {@link ItemDescriptorImpl}
 * / {@link ComponentDescriptorImpl} trees for a component: creates a
 * descriptor from its metamodel, obtains (instantiating or reusing) the
 * live instances for the component and its items/composed components, and
 * completes a descriptor's port index maps by matching provided/used
 * interfaces to the items or fields that implement them. Not part of any
 * public API - it is the container's own bookkeeping used by the
 * descriptor registry.
 */
@Item
public class DescriptorFactory {

	private static final Logger log = Logger.getLogger(DescriptorFactory.class.getName());

	@UsedServices
	private ValidationServices useValidationServices;

	@UsedServices
	private MetamodelReadServices useMetamodelReadServices;

	@UsedServices
	private MetamodelPortReadServices useMetamodelPortReadServices;

	/**
	 * Creates a new, unconfigured factory.
	 */
	public DescriptorFactory() {
	}

	/**
	 * Sets the internal service used to report validation issues found
	 * while completing a descriptor's port indexes.
	 *
	 * @param validationServices the validation service
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.useValidationServices = validationServices;
	}

	/**
	 * Sets the internal service used to read a component's port
	 * information from its metamodel.
	 *
	 * @param metamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.useMetamodelPortReadServices = metamodelPortReadServices;
	}

	/**
	 * Sets the internal service used to read component metamodels and
	 * resolve implementation-to-component-type mappings.
	 *
	 * @param metamodelReadServices the metamodel read service
	 */
	public void setMetamodelReadServices(final MetamodelReadServices metamodelReadServices) {
		this.useMetamodelReadServices = metamodelReadServices;
	}

	/**
	 * Strategy for obtaining the live instance backing an
	 * {@link ItemDescriptorImpl} (or {@link ComponentDescriptorImpl}), and wiring
	 * that instance into its descriptor and parent. Implemented either by
	 * instantiating fresh instances ({@link InstantiatingInstanceFinder}) or by
	 * exploring an already-existing instance tree ({@link ExploringInstanceFinder}).
	 */
	interface InstanceFinder {
		/**
		 * Obtains the instance for the given descriptor and records it on
		 * the descriptor (and, if applicable, its parent).
		 *
		 * @param <T>                the implementation type
		 * @param descriptor         the item or component descriptor to fill in
		 * @param implementationClass the implementation class to obtain an
		 *                            instance of
		 * @return the resolved instance
		 */
		<T> T handleInstance(ItemDescriptorImpl descriptor, Class<T> implementationClass);
	}

	/**
	 * Common {@link InstanceFinder} behavior: obtains an instance through
	 * the subclass-specific {@link #getInstance} and injects it into the
	 * descriptor (and parent, if any), delegating to the component or item
	 * variant depending on the descriptor's actual type.
	 */
	abstract class AbstractInstanceFinder implements InstanceFinder {

		@Override
		public <T> T handleInstance(final ItemDescriptorImpl descriptor, final Class<T> implementationClass) {
			Object instance = this.getInstance(implementationClass);
			if (descriptor instanceof ComponentDescriptorImpl) {
				this.injectComponent((ComponentDescriptorImpl) descriptor, instance);
			} else {
				this.injectItem(descriptor, instance);
			}
			@SuppressWarnings("unchecked")
			T result = (T) instance;
			return result;
		}

		/**
		 * Resolves the instance to use for the given implementation class.
		 *
		 * @param <T>                the implementation type
		 * @param implementationClass the implementation class
		 * @return the resolved instance
		 */
		protected abstract <T> T getInstance(final Class<T> implementationClass);

		/**
		 * Records the given instance as the item's live instance, and, if
		 * the item has a parent component, registers it as one of that
		 * parent's child items.
		 *
		 * @param <T>          the item type
		 * @param descriptor   the item descriptor
		 * @param itemInstance the item's live instance
		 */
		protected <T> void injectItem(final ItemDescriptorImpl descriptor, final T itemInstance) {
			descriptor.setInstance(itemInstance);
			if (descriptor.getParent() != null) {
				ComponentDescriptorImpl parent = descriptor.getParent();
				parent.addItem(descriptor);
			}
		}

		/**
		 * Records the given instance as the component's live instance, and,
		 * if the component has a parent component, registers it as one of
		 * that parent's child (composed) components.
		 *
		 * @param <T>               the component type
		 * @param descriptor        the component descriptor
		 * @param componentInstance the component's live instance
		 */
		protected <T> void injectComponent(final ComponentDescriptorImpl descriptor, final T componentInstance) {
			descriptor.setInstance(componentInstance);

			if (descriptor.getParent() != null) {
				ComponentDescriptorImpl parent = descriptor.getParent();
				parent.addComponent(descriptor);
			}
		}

	}

	/**
	 * {@link InstanceFinder} that instantiates fresh instances with their
	 * default constructor. For a top-level descriptor (no parent) it always
	 * creates a new instance; for a nested item or composed component it
	 * first checks whether the parent's already-instantiated field already
	 * holds an instance (reusing it if so) before creating and wiring in a
	 * new one - this is the path used when the container instantiates a
	 * whole component tree itself.
	 */
	class InstantiatingInstanceFinder extends AbstractInstanceFinder {

		@Override
		public <T> T handleInstance(final ItemDescriptorImpl descriptor, final Class<T> implementationClass) {
			if (descriptor.getParent() == null) {
				return super.handleInstance(descriptor, implementationClass);
			}
			if (descriptor instanceof ComponentDescriptorImpl) {
				return this.handleComposedComponentInstance((ComponentDescriptorImpl) descriptor, implementationClass);
			}
			return this.handleItemInstance(descriptor, implementationClass);
		}

		@Override
		public <T> T getInstance(final Class<T> implementationClass) {
			RuntimeCheck.notNull(implementationClass, "itemImplementationClass");
			final Object itemInstance = this.instantiateWithDefaultConstructor(implementationClass);
			@SuppressWarnings("unchecked")
			T result = (T) itemInstance;
			return result;
		}

		/**
		 * Instantiates the given implementation class through its default
		 * constructor.
		 *
		 * @param implementation the implementation class to instantiate
		 * @return the new instance
		 * @throws IllegalArgumentException if reported as such by
		 *                                  {@link Reflection#instantiateWithDefaultConstructor}
		 * @throws RuntimeException         if instantiation otherwise fails,
		 *                                  typically because no default
		 *                                  constructor is available
		 */
		private Object instantiateWithDefaultConstructor(final Class<?> implementation) {
			Object instance = null;
			try {
				instance = Reflection.instantiateWithDefaultConstructor(implementation);
			} catch (ReflectionException e) {
				String message = "Could not instantiate component impl " + implementation.getSimpleName()
						+ ". Check that it provides a default empty constructor\n"
						+ " (or, if it is an internal class, that it is defined as public and static)";
				throw new RuntimeException(message, e);
			}
			return instance;
		}

		/**
		 * Resolves (reusing or instantiating) the instance for a nested
		 * item and injects it into its parent's item field.
		 *
		 * @param <T>        the item type
		 * @param descriptor the item descriptor
		 * @param itemClass  the item's implementation class
		 * @return the resolved item instance
		 */
		private <T> T handleItemInstance(final ItemDescriptorImpl descriptor, final Class<T> itemClass) {
			ComponentDescriptorImpl parent = descriptor.getParent();
			ReadOnlyComponentMetamodel parentMetamodel = parent.getMetamodel();
			Field itemField = parentMetamodel.getItemField(itemClass);

			T itemInstance = this.resolveNestedInstance(parent, itemField, itemClass);
			super.injectItem(descriptor, itemInstance);
			return itemInstance;
		}

		/**
		 * Resolves (reusing or instantiating) the instance for a composed
		 * (nested) component and injects it into its parent's component
		 * field.
		 *
		 * @param <T>                the component type
		 * @param descriptor         the component descriptor
		 * @param implementationClass the component's implementation class
		 * @return the resolved component instance
		 */
		private <T> T handleComposedComponentInstance(final ComponentDescriptorImpl descriptor,
				final Class<T> implementationClass) {
			ComponentDescriptorImpl parent = descriptor.getParent();
			ReadOnlyComponentMetamodel parentMetamodel = parent.getMetamodel();
			Field componentField = parentMetamodel.getComposedComponentField(implementationClass);

			T componentInstance = this.resolveNestedInstance(parent, componentField, implementationClass);
			super.injectComponent(descriptor, componentInstance);
			return componentInstance;
		}

		/**
		 * Reads the given field on the parent instance; if it already holds
		 * a non-{@code null} value that value is reused, otherwise a new
		 * instance is created and written back into the field.
		 *
		 * @param <T>                the nested instance type
		 * @param parent             the owning component's descriptor
		 * @param field              the parent's item or composed-component
		 *                           field
		 * @param implementationClass the nested implementation class
		 * @return the existing or newly created nested instance
		 */
		private <T> T resolveNestedInstance(final ComponentDescriptorImpl parent, final Field field,
				final Class<T> implementationClass) {
			Object parentInstance = parent.getInstance();
			T nestedInstance = Reflection.getFieldValue(field, parentInstance, implementationClass);
			if (nestedInstance == null) {
				nestedInstance = this.getInstance(implementationClass);
				Reflection.setFieldValueOrThrow(field, parentInstance, nestedInstance);
			}
			return nestedInstance;
		}

	}

	/**
	 * {@link InstanceFinder} that never instantiates: instead it walks an
	 * already-existing instance tree once (recursively following item and
	 * composed-component fields), indexes every instance it finds by its
	 * implementation class, and then hands those existing instances back on
	 * request. Used when the container is asked to adopt (rather than
	 * create) an already-constructed component tree.
	 */
	class ExploringInstanceFinder extends AbstractInstanceFinder {

		private final Map<Class<?>, Object> classToInstance = new HashMap<>();

		/**
		 * Creates a new explorer, immediately walking the given instance
		 * tree to index its item and composed-component instances.
		 *
		 * @param metamodel the root component's metamodel
		 * @param instance  the root component's already-existing instance
		 */
		ExploringInstanceFinder(final ReadOnlyComponentMetamodel metamodel, final Object instance) {
			this.extractItemsFromInstance(metamodel, instance);
		}

		@Override
		public <T> T getInstance(final Class<T> itemImplementationClass) {
			@SuppressWarnings("unchecked")
			T result = (T) this.classToInstance.get(itemImplementationClass);
			return result;
		}

		/**
		 * Recursively indexes the given component instance, its item field
		 * values, and its composed-component field values (descending into
		 * each composed component's own metamodel) by implementation class.
		 * A field that cannot be read is logged and skipped rather than
		 * failing the whole walk.
		 *
		 * @param parentMetamodel the component's metamodel
		 * @param parentInstance  the component's already-existing instance
		 */
		private void extractItemsFromInstance(final ReadOnlyComponentMetamodel parentMetamodel,
				final Object parentInstance) {
			this.classToInstance.put(parentMetamodel.getImplementation(), parentInstance);
			final List<Field> itemFields = parentMetamodel.getItemsFields();
			for (final Field itemField : itemFields) {
				final Class<?> fieldType = itemField.getType();
				try {
					final Object itemInstance = Reflection.getFieldValue(itemField, parentInstance);
					if (itemInstance != null) {
						this.classToInstance.put(fieldType, itemInstance);
					}
				} catch (ReflectionException e) {
					CovalentDiagnostics.severe(log,
							"Unable to read item field " + itemField.getName() + " from component instance.", e);
				}
			}
			final List<Field> componentFields = parentMetamodel.getComposedComponentsFields();
			for (final Field componentField : componentFields) {
				final Class<?> fieldType = componentField.getType();
				try {
					final Object componentInstance = Reflection.getFieldValue(componentField, parentInstance);
					if (componentInstance != null) {
						this.classToInstance.put(fieldType, componentInstance);
					}
					// assuming field is of the type of component implementation
					Class<?> implementation = fieldType;
					Class<?> componentType = DescriptorFactory.this.useMetamodelReadServices.getType(implementation);
					// find child metamodel and recurse
					ReadOnlyComponentMetamodel metamodel = DescriptorFactory.this.useMetamodelReadServices
							.readMetamodel(componentType);
					this.extractItemsFromInstance(metamodel, componentInstance);

				} catch (ReflectionException e) {
					CovalentDiagnostics.severe(log,
							"Unable to read composed component field " + componentField.getName()
									+ " from component instance.",
							e);
				}
			}
		}
	}

	/**
	 * Creates a new (unpopulated) descriptor for the given component type,
	 * reading its metamodel and verifying it declares an implementation
	 * class.
	 *
	 * @param componentType the component type; must not be {@code null}
	 * @param parent        the owning component's descriptor, or
	 *                      {@code null} for a top-level component
	 * @return the new component descriptor
	 * @throws IllegalStateException if the component type has no known
	 *                                implementation class
	 */
	ComponentDescriptorImpl createDescriptor(final Class<?> componentType, final ComponentDescriptorImpl parent) {
		RuntimeCheck.notNull(componentType, "componentType");

		ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		final Class<?> implementationClass = metamodel.getImplementation();
		if (implementationClass == null) {
			throw new IllegalStateException("Unknown implementation for component type: " + componentType);
		}

		return new ComponentDescriptorImpl(parent, metamodel);
	}

	/**
	 * Returns the parent component type declared by the given component
	 * type's metamodel, if it is nested inside another component.
	 *
	 * @param componentType the component type
	 * @return the parent component type, or {@code null} for a top-level
	 *         component
	 */
	Class<?> readParentComponentType(final Class<?> componentType) {
		ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		RuntimeCheck.notNull(metamodel, "metamodel");
		return metamodel.getParentComponentType();
	}

	/**
	 * Instantiates the given (already created but not yet populated)
	 * component descriptor's component and item instances from scratch,
	 * using {@link InstantiatingInstanceFinder}.
	 *
	 * @param componentDescriptor the component descriptor to populate
	 * @return the component's live instance
	 */
	Object instantiateOrAttachInstances(final ComponentDescriptorImpl componentDescriptor) {
		InstanceFinder instanceFinder = new InstantiatingInstanceFinder();
		return this.instantiateOrAttachInstances(componentDescriptor, instanceFinder);
	}

	/**
	 * Populates the given (already created but not yet populated) component
	 * descriptor by attaching it to an already-existing component instance
	 * tree, using {@link ExploringInstanceFinder} to locate the item and
	 * composed-component instances within it.
	 *
	 * @param componentDescriptor the component descriptor to populate
	 * @param componentInstance   the already-existing component instance to
	 *                            attach
	 * @return the component's live instance (the given {@code componentInstance})
	 */
	Object instantiateOrAttachInstances(final ComponentDescriptorImpl componentDescriptor,
			final Object componentInstance) {
		ReadOnlyComponentMetamodel metamodel = componentDescriptor.getMetamodel();
		RuntimeCheck.notNull(metamodel, "metamodel");
		InstanceFinder instanceFinder = new ExploringInstanceFinder(metamodel, componentInstance);
		return this.instantiateOrAttachInstances(componentDescriptor, instanceFinder);
	}

	/**
	 * Resolves the component's own instance and, for each of its declared
	 * items, creates an {@link ItemDescriptorImpl} and resolves its instance
	 * too, all through the given strategy.
	 *
	 * @param componentDescriptor the component descriptor to populate
	 * @param instanceFinder      the instantiation/attachment strategy to use
	 * @return the component's live instance
	 */
	private Object instantiateOrAttachInstances(final ComponentDescriptorImpl componentDescriptor,
			final InstanceFinder instanceFinder) {
		final ReadOnlyComponentMetamodel metamodel = componentDescriptor.getMetamodel();
		final Class<?> implementationClass = metamodel.getImplementation();
		final Object componentInstance = instanceFinder.handleInstance(componentDescriptor, implementationClass);

		// manage component items
		final List<? extends ReadOnlyComponentItemMetamodel> itemsMetamodels = metamodel.getItemMetamodels();
		for (final ReadOnlyComponentItemMetamodel itemMetamodel : itemsMetamodels) {
			final ItemDescriptorImpl itemDescriptor = new ItemDescriptorImpl(componentDescriptor, itemMetamodel);
			final Class<?> itemImplementationClass = itemMetamodel.getImplementation();
			RuntimeCheck.notNull(itemImplementationClass, "itemImplementationClass");
			instanceFinder.handleInstance(itemDescriptor, itemImplementationClass);
		}
		return componentInstance;
	}

	/**
	 * Resets and rebuilds the given component descriptor's port index maps
	 * (provided services, used services, offered/accessed parameters,
	 * produced/consumed events, subscribe delegates, used subscribers) by
	 * matching the component's declared ports against the items and fields
	 * that implement or use them. Called once instances are attached, and
	 * again whenever a component's item set changes.
	 *
	 * @param componentDescriptor the component descriptor to complete
	 */
	void completeDescriptor(final ComponentDescriptorImpl componentDescriptor) {
		componentDescriptor.clearPortIndexes();
		this.findProvidedServices(componentDescriptor);
		this.findUsedServices(componentDescriptor);
		this.findOfferedParameters(componentDescriptor);
		this.findAccessedParameters(componentDescriptor);
		this.findProducedEvents(componentDescriptor);
		this.findConsumedEvents(componentDescriptor);
		this.findSubscribeDelegates(componentDescriptor);
		this.findUsedSubscribers(componentDescriptor);
	}

	/**
	 * Resolves, for every events interface the component provides a
	 * subscribe delegate for, which single item (or the component itself)
	 * implements the corresponding subscribe interface, and records it.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #resolveSingleImplementors
	 */
	private void findSubscribeDelegates(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>resolveSingleImplementors(componentDescriptor,
				metamodelOf(componentDescriptor).getAllProvidedSubscribers(), "Events producer",
				"subscribe interface", ItemDescriptor::implementsSubscriber, componentDescriptor::setSubscribeDelegate);
	}

	/**
	 * Resolves, for every events interface consumed by the component, which
	 * single item (or the component itself) implements that consumer
	 * interface, and records it.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #resolveSingleImplementors
	 */
	private void findConsumedEvents(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>resolveSingleImplementors(componentDescriptor,
				metamodelOf(componentDescriptor).getAllConsumedEvents(), "Events consumer", "events interface",
				ItemDescriptor::implementsConsumer, componentDescriptor::setEventsConsumer);
	}

	/**
	 * Resolves, for every services interface the component provides, which
	 * single item (or the component itself) implements that services
	 * interface, and records it.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #resolveSingleImplementors
	 */
	private void findProvidedServices(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>resolveSingleImplementors(componentDescriptor,
				metamodelOf(componentDescriptor).getAllProvidedServices(), "Services provider",
				"services interface", ItemDescriptor::implementsServices, componentDescriptor::setServicesProvider);
	}

	/**
	 * Resolves, for every parameters interface the component offers, which
	 * single item (or the component itself) implements that parameters
	 * interface, and records it.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #resolveSingleImplementors
	 */
	private void findOfferedParameters(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>resolveSingleImplementors(componentDescriptor,
				metamodelOf(componentDescriptor).getAllOfferedParameters(), "Parameters offeror",
				"parameters interface", ItemDescriptor::implementsParameters, componentDescriptor::setParametersOfferor);
	}

	/**
	 * For every given interface, finds the single item (or the component
	 * itself) that implements it and records it. An interface implemented
	 * more than once within the component is reported through
	 * {@link ValidationServices#handleStructuralVerification} and only the
	 * first implementor is kept; the component itself takes precedence over
	 * its items.
	 *
	 * @param <T>                  the type recorded for an interface
	 * @param componentDescriptor  the component descriptor to update
	 * @param interfaces           the interfaces to resolve
	 * @param role                 the role of the component named in the
	 *                             duplicate message, for instance "Services
	 *                             provider"
	 * @param interfaceKind        the kind of interface named in the duplicate
	 *                             message, for instance "services interface"
	 * @param implementsInterface  tells whether an item or the component
	 *                             implements an interface
	 * @param record               records the resolved implementor on the
	 *                             descriptor
	 */
	private <T> void resolveSingleImplementors(final ComponentDescriptorImpl componentDescriptor,
			final Set<Class<?>> interfaces, final String role, final String interfaceKind,
			final BiPredicate<ItemDescriptor, Class<?>> implementsInterface, final BiConsumer<Class<T>, T> record) {
		final ReadOnlyComponentMetamodel metamodel = componentDescriptor.getMetamodel();
		for (final Class<?> anInterface : interfaces) {
			final List<Object> implementors = new ArrayList<>();
			if (implementsInterface.test(componentDescriptor, anInterface)) {
				implementors.add(componentDescriptor.getInstance());
			} else {
				for (final ItemDescriptor itemDescriptor : componentDescriptor.getItems()) {
					if (implementsInterface.test(itemDescriptor, anInterface)) {
						implementors.add(itemDescriptor.getInstance());
					}
				}
			}
			if (implementors.size() > 1) {
				final String message = role + " " + metamodel.getComponentType().getSimpleName() + " cannot implement "
						+ interfaceKind + " " + anInterface.getSimpleName() + " more than once.";
				this.useValidationServices.handleStructuralVerification(message);
			}
			if (!implementors.isEmpty()) {
				// keep only the first implementor
				@SuppressWarnings("unchecked")
				final Class<T> typedInterface = (Class<T>) anInterface;
				@SuppressWarnings("unchecked")
				final T implementor = (T) implementors.get(0);
				record.accept(typedInterface, implementor);
			}
		}
	}

	/**
	 * Rebuilds the used-services item/field index for every services
	 * interface the component declares as used.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #indexItemsAndFields
	 */
	private void findUsedServices(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>indexItemsAndFields(componentDescriptor, metamodelOf(componentDescriptor).getAllUsedServices(),
				ItemDescriptor::getUsedServicesField, this.useValidationServices::checkUsedServicesField,
				componentDescriptor::addUsedServicesItemAndField);
	}

	/**
	 * Rebuilds the accessed-parameters item/field index for every
	 * parameters interface the component declares as accessed.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #indexItemsAndFields
	 */
	private void findAccessedParameters(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>indexItemsAndFields(componentDescriptor,
				metamodelOf(componentDescriptor).getAllAccessedParameters(),
				ItemDescriptor::getAccessedParametersField, this.useValidationServices::checkAccessedParametersField,
				componentDescriptor::addAccessedParametersItemAndField);
	}

	/**
	 * Rebuilds the used-subscribe item/field index for every events
	 * subscribe interface the component declares as used.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #indexItemsAndFields
	 */
	private void findUsedSubscribers(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>indexItemsAndFields(componentDescriptor,
				metamodelOf(componentDescriptor).getAllUsedSubscribers(), ItemDescriptor::getUsedSubscriberField,
				this.useValidationServices::checkUsedSubscribeField,
				componentDescriptor::addUsedSubscribeItemAndField);
	}

	/**
	 * Rebuilds the produced-events item/field index for every events
	 * interface the component declares as produced.
	 *
	 * @param componentDescriptor the component descriptor to update
	 * @see #indexItemsAndFields
	 */
	private void findProducedEvents(final ComponentDescriptorImpl componentDescriptor) {
		this.<Object>indexItemsAndFields(componentDescriptor,
				metamodelOf(componentDescriptor).getAllProducedEvents(), ItemDescriptor::getProducedEventsField,
				this.useValidationServices::checkProducedEventsField,
				componentDescriptor::addProducedEventsItemAndField);
	}

	private static ReadOnlyComponentMetamodel metamodelOf(final ComponentDescriptorImpl componentDescriptor) {
		return componentDescriptor.getMetamodel();
	}

	/**
	 * Verifies a port field against the validation rules of its kind.
	 */
	@FunctionalInterface
	private interface FieldCheck {

		/**
		 * Verifies the given field.
		 *
		 * @param field                the field to check
		 * @param portInterface        the interface the field is declared for
		 * @param componentImplOrItem  the implementation or item class that
		 *                             declares the field
		 */
		void check(Field field, Class<?> portInterface, Class<?> componentImplOrItem);
	}

	/**
	 * For every given interface, collects the field declared for it on the
	 * component itself and on each of its items, validates each field with the
	 * given check, and records the resulting item/field pairs on the
	 * descriptor.
	 *
	 * @param <T>                  the type of the recorded interface
	 * @param componentDescriptor  the component descriptor to update
	 * @param interfaces           the interfaces to index
	 * @param fieldOf              reads the field declared for an interface on
	 *                             the component or on an item, or null
	 * @param check                validates a field
	 * @param record               records the item/field pairs of an interface
	 *                             on the descriptor
	 */
	private <T> void indexItemsAndFields(final ComponentDescriptorImpl componentDescriptor,
			final Set<Class<?>> interfaces, final BiFunction<ItemDescriptor, Class<?>, Field> fieldOf,
			final FieldCheck check, final BiConsumer<Class<T>, List<ItemAndField>> record) {
		for (final Class<?> anInterface : interfaces) {
			final List<ItemAndField> itemsAndFields = new ArrayList<>();
			this.collectItemAndField(componentDescriptor, anInterface, fieldOf, check, itemsAndFields);
			for (final ItemDescriptor itemDescriptor : componentDescriptor.getItems()) {
				this.collectItemAndField(itemDescriptor, anInterface, fieldOf, check, itemsAndFields);
			}
			@SuppressWarnings("unchecked")
			final Class<T> typedInterface = (Class<T>) anInterface;
			record.accept(typedInterface, itemsAndFields);
		}
	}

	private void collectItemAndField(final ItemDescriptor descriptor, final Class<?> anInterface,
			final BiFunction<ItemDescriptor, Class<?>, Field> fieldOf, final FieldCheck check,
			final List<ItemAndField> itemsAndFields) {
		final Field field = fieldOf.apply(descriptor, anInterface);
		if (field != null) {
			check.check(field, anInterface, descriptor.getMetamodel().getImplementation());
			itemsAndFields.add(new ItemAndField(descriptor.getInstance(), field));
		}
	}

}
