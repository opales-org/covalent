/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.ContainerLifecycleEvents;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncLifecycleServices;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.autowiring.impl.AutoWiringManagerImpl;
import org.opales.covalent.container.autowiring.AutoWiringServices;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.deployment.Deployer;
import org.opales.covalent.container.deployment.DeploymentEvents;
import org.opales.covalent.container.deployment.DeploymentServices;
import org.opales.covalent.container.deployment.impl.DeployerImpl;
import org.opales.covalent.container.directory.Directory;
import org.opales.covalent.container.directory.NamingEvents;
import org.opales.covalent.container.directory.impl.DirectoryImpl;
import org.opales.covalent.container.events.EventsManager;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.events.EventsLifecycleServices;
import org.opales.covalent.container.events.impl.EventsLifecycleManagerImpl;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.introspection.impl.ComponentIntrospectorImpl;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitionsImpl;
import org.opales.covalent.container.metamodel.MetamodelEvents;
import org.opales.covalent.container.metamodel.MetamodelManager;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.impl.MetamodelManagerImpl;
import org.opales.covalent.container.raw.impl.RawComponentsImpl;
import org.opales.covalent.container.registry.ComponentRegistry;
import org.opales.covalent.container.registry.RegistryEvents;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.registry.ComponentInstantiationServices;
import org.opales.covalent.container.registry.impl.ComponentRegistryImpl;
import org.opales.covalent.container.registry.impl.DescriptorRegistryImpl;
import org.opales.covalent.container.registry.impl.PortRegistryManagerImpl;
import org.opales.covalent.container.rr.parameters.ParametersManager;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;
import org.opales.covalent.container.rr.parameters.impl.ParametersLifecycleManagerImpl;
import org.opales.covalent.container.rr.parameters.impl.ParametersManagerImpl;
import org.opales.covalent.container.rr.services.ServicesManager;
import org.opales.covalent.container.rr.services.ServicesManagerEvents;
import org.opales.covalent.container.rr.services.ServicesLifecycleServices;
import org.opales.covalent.container.rr.services.impl.ServicesLifecycleManagerImpl;
import org.opales.covalent.container.rr.services.impl.ServicesManagerImpl;
import org.opales.covalent.container.util.CovalentDiagnostics;
import org.opales.covalent.container.util.Reflection;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;
import org.opales.covalent.container.validation.ValidationServices;
import org.opales.covalent.container.extension.impl.CovalentExtensionsManagerImpl;
import org.opales.covalent.container.jmx.impl.JmxManagerImpl;

/**
 * Builds and wires every internal manager component that makes up a
 * {@link CovalentContainerImpl}, in dependency order, then registers the
 * container itself as a component of its own lifecycle. This is the
 * container's composition root: it instantiates each manager, injects the
 * cross-manager dependencies normal port autowiring cannot provide yet
 * (since autowiring itself is one of the things being bootstrapped),
 * bootstraps each manager's own events port, and finally wires every
 * manager into the container as an item or composed component.
 */
public class Bootstrapper {

	private static final Logger log = Logger.getLogger(Bootstrapper.class.getName());

	private final ComponentValidatorImpl validatorImpl;

	private final ComponentIntrospectorImpl introspectorImpl;

	private final MetamodelManagerImpl metamodelManagerImpl;

	private final DeployerImpl deployerImpl;

	private final PortRegistryManagerImpl portRegistryManagerImpl;

	private final ComponentRegistryImpl componentRegistryImpl;

	private final DescriptorRegistryImpl descriptorRegistryImpl;

	private final DirectoryImpl directoryImpl;

	private final ServicesManagerImpl servicesManagerImpl;

	private final EventsManagerImpl eventsManagerImpl;

	private final ParametersManagerImpl parametersManagerImpl;

	private final AutoWiringManagerImpl autoWiringManagerImpl;

	private final LifecycleManagerImpl lifecycleManagerImpl;

	private final RawComponentsImpl rawComponentsImpl;

	private final AsyncManagerImpl asyncManagerImpl;

	private final CovalentExtensionsManagerImpl extensionsManagerImpl;

	private final JmxManagerImpl jmxManagerImpl;

	private final ServicesLifecycleManagerImpl servicesLifecycleManagerImpl;

	private final EventsLifecycleManagerImpl eventsLifecycleManagerImpl;

	private final ParametersLifecycleManagerImpl parametersLifecycleManagerImpl;

	private final VerificationLevel structuralRulesChecking = VerificationLevel.trace;

	private final VerificationLevel dynamicRulesChecking = VerificationLevel.trace;

	private final BooleanSupplier instrumentationSupplier;

	/** Consulted by every services/parameters provider-side proxy: see {@link LifecycleManagerImpl#isDisposed()}. */
	private final BooleanSupplier disposedSupplier;

	private final CovalentContainerImpl container;

	private final CovalentContainerParametersImpl covalentContainerParameters;

	/**
	 * Creates every internal manager for the given container and runs the
	 * full bootstrap sequence.
	 *
	 * @param container the container being bootstrapped
	 * @throws RuntimeException if any step of the bootstrap sequence fails
	 */
	public Bootstrapper(final CovalentContainerImpl container) {
		this.container = container;
		this.covalentContainerParameters = new CovalentContainerParametersImpl();
		this.instrumentationSupplier = () -> this.covalentContainerParameters.isInstrumented();

		this.validatorImpl = new ComponentValidatorImpl();
		this.introspectorImpl = new ComponentIntrospectorImpl();
		this.metamodelManagerImpl = new MetamodelManagerImpl();
		this.deployerImpl = new DeployerImpl();
		this.portRegistryManagerImpl = new PortRegistryManagerImpl();
		this.componentRegistryImpl = this.portRegistryManagerImpl.getComponentRegistryImpl();
		this.descriptorRegistryImpl = this.portRegistryManagerImpl.getDescriptorRegistryImpl();
		this.directoryImpl = new DirectoryImpl();
		this.servicesManagerImpl = new ServicesManagerImpl();
		this.servicesLifecycleManagerImpl = this.servicesManagerImpl.getServicesLifecycleManagerImpl();
		this.eventsManagerImpl = new EventsManagerImpl();
		this.eventsLifecycleManagerImpl = this.eventsManagerImpl.getEventsLifecycleManagerImpl();
		this.parametersManagerImpl = new ParametersManagerImpl();
		this.parametersLifecycleManagerImpl = this.parametersManagerImpl.getParametersLifecycleManagerImpl();
		this.autoWiringManagerImpl = new AutoWiringManagerImpl();
		this.lifecycleManagerImpl = new LifecycleManagerImpl();
		this.disposedSupplier = () -> this.lifecycleManagerImpl.isDisposed();
		this.rawComponentsImpl = new RawComponentsImpl();
		this.asyncManagerImpl = new AsyncManagerImpl();
		this.extensionsManagerImpl = new CovalentExtensionsManagerImpl();
		this.jmxManagerImpl = new JmxManagerImpl();

		this.bootstrap();
	}

	private void bootstrap() {
		try {
			this.injectDependencies();
			this.bootstrapInternalEvents();
			this.injectContainerItems();
			this.injectContainerComposedComponents();
			this.finishBootstrap();
		} catch (Throwable t) {
			throw new RuntimeException("Could not bootstrap container.", t);
		}
	}

	private void injectDependencies() throws IllegalAccessException {
		this.jmxManagerImpl.setContainer(this.container.getName(), this.asyncManagerImpl, this.eventsManagerImpl);

		// note: only the minimum dependencies are injected manually here,
		// those which are required during boostrap.
		// others are injected normally during start of composite CovalentContainer
		this.validatorImpl.setStructuralRulesVerificationLevel(this.structuralRulesChecking);
		this.validatorImpl.setDynamicRulesVerificationLevel(this.dynamicRulesChecking);

		this.metamodelManagerImpl.setValidationServices(this.validatorImpl);
		this.metamodelManagerImpl.setIntrospectionServices(this.introspectorImpl);

		this.deployerImpl.setMetamodelWriteServices(this.metamodelManagerImpl);

		this.portRegistryManagerImpl.setValidationServices(this.validatorImpl);
		this.portRegistryManagerImpl.setMetamodelPortReadServices(this.metamodelManagerImpl);
		this.portRegistryManagerImpl.setMetamodelReadServices(this.metamodelManagerImpl);

		this.eventsManagerImpl.setValidationServices(this.validatorImpl);
		this.eventsManagerImpl.setValidationParameters(this.validatorImpl);
		this.eventsManagerImpl.setMetamodelPortReadServices(this.metamodelManagerImpl);
		this.eventsManagerImpl.setMetamodelReadServices(this.metamodelManagerImpl);
		this.eventsManagerImpl.setPortRegistryServices(this.portRegistryManagerImpl);
		this.eventsManagerImpl.setNamingServices(this.directoryImpl);
		this.eventsManagerImpl.setInstrumentationSupplier(this.instrumentationSupplier);

		this.servicesManagerImpl.setIntrospectionServices(this.introspectorImpl);
		this.servicesManagerImpl.setValidationServices(this.validatorImpl);
		this.servicesManagerImpl.setMetamodelPortReadServices(this.metamodelManagerImpl);
		this.servicesManagerImpl.setPortRegistryServices(this.portRegistryManagerImpl);
		this.servicesManagerImpl.setNamingServices(this.directoryImpl);
		this.servicesManagerImpl.setInstrumentationSupplier(this.instrumentationSupplier);
		this.servicesManagerImpl.setDisposedSupplier(this.disposedSupplier);

		this.parametersManagerImpl.setIntrospectionServices(this.introspectorImpl);
		this.parametersManagerImpl.setValidationServices(this.validatorImpl);
		this.parametersManagerImpl.setMetamodelPortReadServices(this.metamodelManagerImpl);
		this.parametersManagerImpl.setPortRegistryServices(this.portRegistryManagerImpl);
		this.parametersManagerImpl.setNamingServices(this.directoryImpl);
		this.parametersManagerImpl.setInstrumentationSupplier(this.instrumentationSupplier);
		this.parametersManagerImpl.setDisposedSupplier(this.disposedSupplier);

		this.autoWiringManagerImpl.setMetamodelPortReadServices(this.metamodelManagerImpl);
		this.autoWiringManagerImpl.setPortRegistryServices(this.portRegistryManagerImpl);
		this.autoWiringManagerImpl.setServicesManagerServices(this.servicesManagerImpl);
		this.autoWiringManagerImpl.setEventManagerServices(this.eventsManagerImpl);
		this.autoWiringManagerImpl.setParametersManagerServices(this.parametersManagerImpl);

		// Bootstrap wires annotated fields before normal port injection is available.
		this.setFieldValue(LifecycleManagerImpl.class, this.lifecycleManagerImpl, IntrospectionServices.class,
				this.introspectorImpl, UsedServices.class);
		this.setFieldValue(LifecycleManagerImpl.class, this.lifecycleManagerImpl, ValidationServices.class,
				this.validatorImpl, UsedServices.class);
		this.setFieldValue(LifecycleManagerImpl.class, this.lifecycleManagerImpl, MetamodelPortReadServices.class,
				this.metamodelManagerImpl, UsedServices.class);
		this.setFieldValue(LifecycleManagerImpl.class, this.lifecycleManagerImpl, MetamodelReadServices.class,
				this.metamodelManagerImpl, UsedServices.class);
		this.setFieldValue(LifecycleManagerImpl.class, this.lifecycleManagerImpl, DeploymentServices.class,
				this.deployerImpl, UsedServices.class);
		this.setFieldValue(LifecycleManagerImpl.class, this.lifecycleManagerImpl, RegistryReadServices.class,
				this.componentRegistryImpl, UsedServices.class);
		this.setFieldValue(LifecycleManagerImpl.class, this.lifecycleManagerImpl, ComponentInstantiationServices.class,
				this.portRegistryManagerImpl, UsedServices.class);
		final LifecycleTransitionsImpl lifecycleTransitions = (LifecycleTransitionsImpl) Reflection
				.lookupFieldWithAnnotation(LifecycleManagerImpl.class, LifecycleTransitionsImpl.class, Item.class)
				.get(this.lifecycleManagerImpl);
		this.setFieldValue(LifecycleTransitionsImpl.class, lifecycleTransitions, AutoWiringServices.class,
				this.autoWiringManagerImpl, UsedServices.class);
		this.setFieldValue(LifecycleTransitionsImpl.class, lifecycleTransitions, ServicesLifecycleServices.class,
				this.servicesLifecycleManagerImpl, UsedServices.class);
		this.setFieldValue(LifecycleTransitionsImpl.class, lifecycleTransitions, EventsLifecycleServices.class,
				this.eventsLifecycleManagerImpl, UsedServices.class);
		this.setFieldValue(LifecycleTransitionsImpl.class, lifecycleTransitions, ParametersLifecycleServices.class,
				this.parametersLifecycleManagerImpl, UsedServices.class);
		this.setFieldValue(LifecycleTransitionsImpl.class, lifecycleTransitions, AsyncLifecycleServices.class,
				this.asyncManagerImpl, UsedServices.class);
		// Internal components always start vertically, before the container's configured startup mode.
		lifecycleTransitions.initialize(StartMode.vertical);
	}

	private void bootstrapInternalEvents() throws Exception {
		// a few components needs a special treatment, as
		// there is not way to inject events producer normally at this early
		// stage of bootstrap
		this.bootstrapMetamodelEvents();
		this.bootstrapEventsManagerEvents();
		this.bootstrapRegistryEvents();
		this.bootstrapDeploymentEvents();
		this.bootstrapLifecycleEvents();
		this.bootstrapServicesEvents();
		this.bootstrapParametersEvents();
		this.bootstrapDirectoryEvents();
		this.bootstrapContainerLifecycleEvents();
	}

	private void bootstrapContainerLifecycleEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(CovalentContainer.class, CovalentContainerImpl.class,
				ContainerLifecycleEvents.class, this.container);
	}

	private void bootstrapMetamodelEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(MetamodelManager.class, MetamodelManagerImpl.class, MetamodelEvents.class,
				this.metamodelManagerImpl);
	}

	private void bootstrapEventsManagerEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(EventsManager.class, EventsManagerImpl.class, EventsManagerEvents.class,
				this.eventsManagerImpl);
	}

	private void bootstrapRegistryEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(ComponentRegistry.class, ComponentRegistryImpl.class,
				RegistryEvents.class, this.componentRegistryImpl);
	}

	private void bootstrapDeploymentEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(Deployer.class, DeployerImpl.class, DeploymentEvents.class,
				this.deployerImpl);
	}

	private void bootstrapLifecycleEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(LifecycleManager.class, LifecycleManagerImpl.class, LifecycleEvents.class,
				this.lifecycleManagerImpl);
	}

	private void bootstrapServicesEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(ServicesManager.class, ServicesManagerImpl.class,
				ServicesManagerEvents.class, this.servicesManagerImpl);
	}

	private void bootstrapParametersEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(ParametersManager.class, ParametersManagerImpl.class,
				ParametersManagerEvents.class, this.parametersManagerImpl);
	}

	private void bootstrapDirectoryEvents() throws Exception {
		this.bootstrapEventsAndInjectProducer(Directory.class, DirectoryImpl.class, NamingEvents.class,
				this.directoryImpl);
	}

	private <CI, EVENTS> void bootstrapEventsAndInjectProducer(final Class<?> componentType,
			final Class<CI> componentImpl, final Class<EVENTS> eventsInterface, final CI componentInstance) {
		EVENTS producer = this.eventsManagerImpl.createProducerNoEvents(eventsInterface, componentType, null, null);
		// after activation, producer is ready to be used
		this.eventsManagerImpl.activateProducer(eventsInterface);
		this.setFieldValue(componentImpl, componentInstance, eventsInterface, producer, ProducedEvents.class);
	}

	private void injectContainerItems() {
		this.injectItem(CovalentContainerParametersImpl.class, this.covalentContainerParameters);
	}

	private <CI> void injectItem(final Class<CI> itemImpl, final CI itemInstance) {
		this.setFieldValue(CovalentContainerImpl.class, this.container, itemImpl, itemInstance, Item.class);
	}

	private void injectContainerComposedComponents() {
		this.injectComposedComponent(ComponentValidatorImpl.class, this.validatorImpl);
		this.injectComposedComponent(ComponentIntrospectorImpl.class, this.introspectorImpl);
		this.injectComposedComponent(MetamodelManagerImpl.class, this.metamodelManagerImpl);
		this.injectComposedComponent(DeployerImpl.class, this.deployerImpl);
		this.injectComposedComponent(PortRegistryManagerImpl.class, this.portRegistryManagerImpl);
		this.injectComposedComponent(DirectoryImpl.class, this.directoryImpl);
		this.injectComposedComponent(ServicesManagerImpl.class, this.servicesManagerImpl);
		this.injectComposedComponent(EventsManagerImpl.class, this.eventsManagerImpl);
		this.injectComposedComponent(ParametersManagerImpl.class, this.parametersManagerImpl);
		this.injectComposedComponent(AutoWiringManagerImpl.class, this.autoWiringManagerImpl);
		this.injectComposedComponent(LifecycleManagerImpl.class, this.lifecycleManagerImpl);
		this.injectComposedComponent(RawComponentsImpl.class, this.rawComponentsImpl);
		this.injectComposedComponent(AsyncManagerImpl.class, this.asyncManagerImpl);
		this.injectComposedComponent(CovalentExtensionsManagerImpl.class, this.extensionsManagerImpl);
		this.injectComposedComponent(JmxManagerImpl.class, this.jmxManagerImpl);
	}

	private <CI> void injectComposedComponent(final Class<CI> componentImpl, final CI componentInstance) {
		this.setFieldValue(CovalentContainerImpl.class, this.container, componentImpl, componentInstance,
				ComposedComponent.class);
	}

	private <CI, FT> void setFieldValue(final Class<CI> componentImpl, final CI componentInstance,
			final Class<FT> fieldType, final FT fieldValue, final Class<? extends Annotation> annotation) {
		Field field = Reflection.lookupFieldWithAnnotation(componentImpl, fieldType, annotation);
		try {
			field.set(componentInstance, fieldValue);
		} catch (IllegalAccessException e) {
			CovalentDiagnostics.severe(log, "Unable to inject bootstrap field " + field.getName() + ".", e);
		}
	}

	private void finishBootstrap() {
		this.covalentContainerParameters.setComponentValidatorImpl(this.validatorImpl);
		this.covalentContainerParameters.setEventsManagerImpl(this.eventsManagerImpl);
		this.covalentContainerParameters.setLifecycleManagerImpl(this.lifecycleManagerImpl);

		// Finally, register the Covalent container with all its child components.
		this.lifecycleManagerImpl.pseudoInstantiateComponent(CovalentContainer.class, this.container);
		this.lifecycleManagerImpl.activateComponent(CovalentContainer.class);
		// the internal components' own @Activation callbacks, once every internal
		// port is usable: the internal components are active from now on
		this.lifecycleManagerImpl.activateInternalComponents();
	}

	// Runs on JVM exit if the container it was created for was never
	// explicitly disposed. Registered once per container from
	// CovalentContainerImpl.start() and removed by its dispose() - see the
	// javadoc there for why bootstrap itself cannot be where this happens.
	// Holds only a weak reference to the container so an application that
	// dropped every reference to it can still have it collected.
	//
	// A component that throws from its own passivation does not make run() throw, but not because of the try/catch below:
	// LifecycleManagerImpl already catches and logs component-level
	// passivation failures itself (best-effort shutdown), so stop() does not
	// actually throw in that case. The try/catch here is kept anyway, as
	// insurance should that guarantee be lost.
	static final class ShutdownHookRunnable implements Runnable {

		private final WeakReference<CovalentContainerImpl> containerRef;

		ShutdownHookRunnable(final CovalentContainerImpl container) {
			this.containerRef = new WeakReference<>(container);
		}

		@Override
		public void run() {
			CovalentContainerImpl container = this.containerRef.get();
			if (container == null || !container.getParameters().isRegisterShutdownHook()) {
				return;
			}
			try {
				// Unconditional: dispose() is lenient and safe to call regardless of
				// whether the container is currently started (see its own
				// javadoc), and it is what actually terminates the internal
				// component tree's threads for good - a bare stop() would
				// leave them freshly re-activated (see LifecycleManagerImpl),
				// defeating the point of this safety net.
				container.dispose();
			} catch (Throwable t) {
				CovalentDiagnostics.severe(log, "Uncaught error while disposing container from its shutdown hook.", t);
			}
		}

	}

}
