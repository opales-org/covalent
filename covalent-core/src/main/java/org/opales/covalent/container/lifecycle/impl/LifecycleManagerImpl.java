/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import org.opales.covalent.container.exceptions.ComponentLifecycleException;
import org.opales.covalent.container.util.ShutdownResidues;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.opales.covalent.ManagedContainerRelay;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.deployment.DeploymentServices;
import org.opales.covalent.container.impl.Bootstrapper;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.lifecycle.Activable;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initializable;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.Passivatable;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.RawComponentLifecycleServices;
import org.opales.covalent.container.lifecycle.Removable;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.raw.impl.RawComponentsImpl;
import org.opales.covalent.container.registry.ComponentInstantiationServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.util.CallerFinder;
import org.opales.covalent.container.util.CovalentDiagnostics;
import org.opales.covalent.container.util.Reflection;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Lifecycle operations and state reads share this instance's reentrant monitor.
 * The container also holds it through startup reporting and extension startup.
 */
@Implementation
public class LifecycleManagerImpl implements LifecycleManager, LifecycleServices, LifecycleParameters,
		RawComponentLifecycleServices {
	private static Logger log = Logger.getLogger(LifecycleManagerImpl.class.getName());

	@UsedServices
	private IntrospectionServices useIntrospectionServices;

	@UsedServices
	private ValidationServices useValidationServices;

	@UsedServices
	private MetamodelPortReadServices useMetamodelPortReadServices;

	@UsedServices
	private MetamodelReadServices useMetamodelReadServices;

	@UsedServices
	private DeploymentServices useDeploymentServices;

	@UsedServices
	private MetamodelWriteServices useMetamodelWriteServices;

	@UsedServices
	private RegistryReadServices useRegistryReadServices;

	@UsedServices
	private ComponentInstantiationServices useComponentInstantiationServices;

	@Item
	private final LifecycleTransitionsImpl lifecycleTransitions = new LifecycleTransitionsImpl();

	@ProducedEvents
	private LifecycleEvents produceLifecycleEvents;

	/**
	 * Component state by component type
	 */
	private final Map<Class<?>, ComponentState> componentStates = new HashMap<>();

	private StartMode startMode = StartMode.vertical;

	/** Successful instantiation order, used in reverse for removal. Not a state index. */
	private final List<Class<?>> instantiationOrder = new ArrayList<>();

	private boolean findLifecycleMethodByNameConvention = false;

	/**
	 * Owns every piece of state and logic behind circular dependency
	 * detection (enable flag, computed graph, cycle reporting), and provides
	 * {@link org.opales.covalent.container.lifecycle.DependencyGraphServices}
	 * as its own component item: see {@link DependencyGraphChecker}.
	 */
	@Item
	private final DependencyGraphChecker dependencyGraphChecker = new DependencyGraphChecker();

	private static final String activationMethodNameConvention = "activateComponent";

	private static final String passivationMethodNameConvention = "passivateComponent";

	private static final String initializationMethodNameConvention = "initializeComponent";

	private static final String removalMethodNameConvention = "removeComponent";

	/** Creates a new lifecycle manager. */
	public LifecycleManagerImpl() {
		this.dependencyGraphChecker.setLiveGraphSource(() -> new DependencyGraphAnalyzer()
				.analyze(this.getDeployedBusinessRootComponentTypes(), this.useMetamodelReadServices));
	}

	/**
	 * Reports whether a lifecycle failure was already wrapped with a
	 * "During ..." message naming the component, so that it is rethrown as is
	 * instead of being wrapped a second time. An exception without any message
	 * has not been wrapped.
	 *
	 * @param e the failure to check
	 * @return {@code true} if the failure already carries a "During ..." message
	 */
	private static boolean isAlreadyReported(final Exception e) {
		return e instanceof ComponentLifecycleException;
	}

	private static String failureMessage(final String phase, final Exception cause) {
		String causeMessage = cause.getMessage();
		return phase + ": " + (causeMessage != null ? causeMessage : cause.toString());
	}

	@Override
	public synchronized void setStartMode(final StartMode startMode) {
		if (startMode == null) {
			throw new IllegalArgumentException("Start mode must not be null.");
		}
		this.startMode = startMode;
	}

	@Override
	public synchronized StartMode getStartMode() {
		return this.startMode;
	}

	private boolean started = false;

	/**
	 * Reports whether this container is currently started, i.e. whether the
	 * last {@link #start()}/{@link #stop()} call to complete without leaving
	 * the internal component tree mid-transition was a {@link #start()}.
	 *
	 * @return {@code true} when the container is started
	 */
	public synchronized boolean isStarted() {
		return this.started;
	}

	// volatile, and read without the lock dispose() holds (see isDisposed()):
	// every services/parameters provider-side proxy consults isDisposed() on
	// every incoming call, from any other container, possibly while this
	// container's own dispose() is in progress on another thread (the JVM
	// shutdown hook, typically). A synchronized isDisposed() would then block
	// for the full teardown cascade - and since that cascade's component
	// passivation callbacks can themselves make outbound cross-container
	// calls that loop back through another container's own isDisposed()
	// guard, two containers disposed at the same time can each end up
	// waiting on the other's lock: a cross-container deadlock. disposed only
	// ever moves one way, false to true, so a plain volatile read is enough.
	private volatile boolean disposed = false;

	/**
	 * Reports whether {@link #dispose()} has been called on this container.
	 * Once {@code true}, the internal (technical) component tree stays
	 * passivated for good: unlike {@link #stop()}, {@link #dispose()} never
	 * re-activates it afterward.
	 * <p>
	 * Deliberately not synchronized: see the comment on {@link #disposed}.
	 *
	 * @return {@code true} once this container has been disposed
	 */
	public boolean isDisposed() {
		return this.disposed;
	}

	/**
	 * Starts the container's business components, according to the
	 * configured {@link #getStartMode() start mode}; the internal (technical)
	 * components are already active since the container was built. A strict
	 * circular dependency verification failure is thrown and leaves the
	 * container not started. Past it, a failure while starting is isolated
	 * and reported rather than propagated, so this always marks the
	 * container as started.
	 *
	 * @return the wall-clock duration of this call, in seconds
	 * @throws IllegalStateException if the container is already started
	 */
	public synchronized float start() {
		long startNanos = System.nanoTime();
		if (this.started) {
			throw new IllegalStateException("Already started");
		}
		// Deliberately outside the try/catch below and before anything is
		// activated: a strict circular dependency violation must propagate to
		// the caller and leave the container unstarted, exactly like any other
		// strict structural verification failure - not be swallowed as a
		// component-level start failure.
		this.dependencyGraphChecker.computeIfEnabled(this.getDeployedBusinessRootComponentTypes(),
				this.useMetamodelReadServices, this.useValidationServices);
		this.applyDependencyOrderedActivationIfEnabled();
		try {
			this.doStart();
		} catch (Throwable t) {
			// Every component-level failure is already isolated and reported
			// individually (activateInternalComponents(), startVertical(),
			// activate()); this remains only as a safety net for something
			// unexpected outside that isolation. Either way, start() was
			// genuinely attempted, so this.started below must say so - an
			// exception surfacing from one callback must never corrupt the
			// container's own bookkeeping of whether it is started or stopped.
			CovalentDiagnostics.severe(log, "Covalent container start failed.", t);
		} finally {
			this.started = true;
		}
		return (System.nanoTime() - startNanos) / 1_000_000_000f;
	}

	private void doStart() {
		switch (this.startMode) {
		case horizontal:
			this.startHorizontal();
			break;
		case vertical:
			this.startVertical();
			break;
		default:
			throw new IllegalStateException("unknown start mode: " + this.startMode);
		}
	}

	private void startHorizontal() {
		this.lifecycleTransitions.initialize(StartMode.horizontal);
		this.instantiate();
		this.initialize();
		this.activate();
	}

	private void startVertical() {
		this.lifecycleTransitions.initialize(StartMode.vertical);
		for (final Class<?> componentType : this.getDeployedRootComponentTypes()) {
			try {
				if (this.isDeployed(componentType) && !this.isFailed(componentType) && !this.isActivated(componentType)) {
					this.startComponentVertically(componentType);
				}
			} catch (final RuntimeException t) {
				// catch so that initialization can be continued
				CovalentDiagnostics.severe(log,
						"Component " + componentType.getSimpleName() + " failed during vertical start.", t);
			}
		}
	}

	private void startComponentVertically(final Class<?> componentType) {
		if (!this.isInstantiated(componentType)) {
			this.instantiateComponent(componentType);
		}

		final ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		this.doStartVertically(metamodel);
	}

	private void doStartVertically(final ReadOnlyComponentMetamodel metamodel) {
		for (ReadOnlyComponentMetamodel composed : metamodel.getComposedMetamodels()) {
			this.doStartVertically(composed);
		}

		final Class<?> componentType = metamodel.getComponentType();
		if (!this.isInitialized(componentType)) {
			this.doInitializeFlat(componentType);
		}
		if (!this.isActivated(componentType)) {
			this.doActivateFlat(componentType);
		}
	}

	/**
	 * Stops the container: tears down every deployed business component
	 * according to the configured {@link #getStartMode() start mode}. The
	 * internal (technical) components are not affected: they stay active, so
	 * that a stopped container accepts everything a new one accepts. A failure
	 * while stopping is isolated and reported rather than propagated, so this
	 * always marks the container as stopped once attempted.
	 *
	 * @throws IllegalStateException if the container is not started
	 */
	public synchronized void stop() {
		if (!this.started) {
			throw new IllegalStateException("Not started");
		}
		try {
			this.stopBusinessCascade();
			log.info("Covalent container stopped.");
		} catch (Throwable t) {
			// See start(): every component-level failure is already isolated
			// and reported individually; this remains only as a safety net.
			// stop() was genuinely attempted, so this.started below must say
			// so regardless of what a single callback did.
			CovalentDiagnostics.severe(log, "Covalent container stop failed.", t);
		} finally {
			// Kept in the same finally block, right alongside the started flag
			// itself flipping back to false: this item's own notion of "was the
			// graph computed for the current start() cycle" must go stale at
			// exactly the same moment, or getDependencyGraph() would keep
			// silently answering with the last start()'s graph after stop().
			this.dependencyGraphChecker.stopped();
			this.started = false;
		}
	}

	/**
	 * Definitively ends this container: runs the same business teardown as
	 * {@link #stop()} (via {@link #stopBusinessCascade()}), invokes the removal
	 * callback of the managed container relay, if any, then passivates and
	 * removes the internal (technical) component tree, for good. After
	 * {@code dispose()} returns, the internal components' threads have
	 * terminated, and this container becomes garbage-collectible once nothing
	 * else references it.
	 * <p>
	 * Unlike {@link #stop()}, the business cascade does not depend on
	 * {@link #isStarted()}: a raw component can be registered and activated
	 * through {@code RawComponentsServices} without the container ever being
	 * started, and it must be torn down before the internal components are
	 * passivated.
	 * <p>
	 * Lenient and idempotent, so every caller (an application, the JVM
	 * shutdown hook, {@code Covalent.disposeManagedContainer()}) can call it
	 * unconditionally, whatever the container's state.
	 */
	public synchronized void dispose() {
		if (this.disposed) {
			// Genuine idempotency, not just a safe-to-call guarantee:
			// stopBusinessCascade() is not a harmless no-op
			// on a repeat call - its own consistency assertions expect
			// internal components to still be activated going in, which is
			// never true on a second pass (see passivateInternalComponents()).
			// Every caller this class's own javadoc invites to call dispose()
			// unconditionally - the JVM shutdown hook in particular, which
			// stays registered for the container's weakly-referenced lifetime
			// even after an explicit dispose() - relies on this actually
			// being a no-op rather than re-running the teardown cascade.
			return;
		}
		try {
			try {
				this.stopBusinessCascade();
				this.removeManagedContainerRelay();
			} finally {
				// The internal components are passivated, then removed, for good:
				// the only operation that ends their lifecycle. This must happen
				// even when the checks of the cascade flag a residual business
				// component - an incomplete business shutdown must not also leave
				// the internal workers running.
				this.passivateInternalComponents();
				this.removeInternalComponents();
			}
			log.info("Covalent container disposed.");
		} catch (Throwable t) {
			// See stop(): every component-level failure is already isolated
			// and reported individually; this remains only as a safety net.
			CovalentDiagnostics.severe(log, "Covalent container stop failed.", t);
		} finally {
			this.dependencyGraphChecker.stopped();
			this.started = false;
		}
		this.disposed = true;
	}

	/**
	 * Runs the business-component teardown cascade shared by {@link #stop()}
	 * and {@link #dispose()}: tears down every deployed business component
	 * according to the configured {@link #getStartMode() start mode}. The
	 * internal components are left as they are.
	 */
	private void stopBusinessCascade() {
		switch (this.startMode) {
		case horizontal:
			this.stopHorizontal();
			break;
		case vertical:
			this.stopVertical();
			break;
		default:
			throw new IllegalStateException("unknown start mode: " + this.startMode);
		}
	}

	/**
	 * Lists what is left behind once the business components are torn down:
	 * every entry describes a component that is not internal, or an internal
	 * one in a state it should not be in after the teardown. Run by the
	 * container's {@code stop()}, which reports the result in the shutdown
	 * report.
	 *
	 * @return the residues found, empty when only internal components remain
	 */
	public List<String> listNonInternalResidues() {
		List<String> residues = new ArrayList<>();
		for (Map.Entry<Class<?>, ComponentState> entry : this.componentStates.entrySet()) {
			Class<?> componentType = entry.getKey();
			ShutdownResidues.addIfNotInternal(residues, "LifecycleManager", "component state still tracked for",
					componentType);
			if (entry.getValue() == ComponentState.passivated || entry.getValue() == ComponentState.removed) {
				ShutdownResidues.add(residues, "LifecycleManager",
						"component " + componentType.getName() + " still tracked as " + entry.getValue());
			}
		}
		for (Class<?> componentType : this.instantiationOrder) {
			ShutdownResidues.addIfNotInternal(residues, "LifecycleManager", "component still instantiated:",
					componentType);
		}
		return residues;
	}

	/**
	 * Activates the internal (technical) component tree, once, when the
	 * container is built: called by {@code Bootstrapper} at the end of the
	 * bootstrap. The internal components then stay active until
	 * {@link #dispose()}, whatever {@link #start()} and {@link #stop()} do to
	 * the business components, which may use the technical services at any
	 * time.
	 * <p>
	 * The state transition alone (via {@link #activateComponent(Class)}) does
	 * not invoke each internal component's {@code @Activation} callback - see
	 * {@link #doActivateFlat(Class)} - because that callback may need every
	 * other internal component's services/parameters already unlocked (which
	 * only happens once the state transition below has completed). Once it
	 * has, the real callbacks are invoked directly, in dependency order
	 * (providers before their consumers), so a component's own
	 * {@code @Activation} can safely use another internal component's
	 * services.
	 */
	public synchronized void activateInternalComponents() {
		if (!this.isActivated(CovalentContainer.class)) {
			try {
				this.activateComponent(CovalentContainer.class);
			} catch (final RuntimeException t) {
				// Isolated too, for the same reason as the per-component loop
				// below: this state transition can itself fail because of an
				// unrelated wiring problem surfacing deep in the cascade (e.g. a
				// leftover raw registration from an earlier failure). That
				// failure must not propagate out of start() and leave it unable
				// to report that it did start.
				CovalentDiagnostics.severe(log, "Internal component tree failed to activate.", t);
			}
		}
		for (Class<?> componentType : this.internalComponentTypesInDependencyOrder()) {
			try {
				this.invokeInternalLifecycleCallback(componentType, Activable.class, Activation.class,
						activationMethodNameConvention);
			} catch (final RuntimeException t) {
				// Isolated per component, exactly like business components in
				// startVertical(): one internal component's activation callback
				// failing must not abort activation of the others, nor leave
				// start() unable to report that it did start.
				CovalentDiagnostics.severe(log,
						"Internal component " + componentType.getSimpleName() + " failed during activation.", t);
			}
		}
	}

	/**
	 * Passivates the internal (technical) component tree, if it is currently
	 * activated - by {@link #dispose()} only. Internal components are never
	 * removed or undeployed through the state machine (their
	 * Java identity is fixed for the container's whole life), only passivated:
	 * this locks their services, parameters and events ports (through the same
	 * mechanism used for business components) and lets them release any
	 * resource pool, while still allowing subscriptions to be registered.
	 * <p>
	 * Each internal component's own {@code @Passivation} callback is invoked
	 * first, in reverse dependency order (consumers before the providers they
	 * depend on), while every internal component's services/parameters are
	 * still unlocked - so a component's own {@code @Passivation} can safely
	 * use another internal component's services, e.g. for a consistency check.
	 * Only once every callback has run does the state transition (via
	 * {@link #passivateComponent(Class)}) lock everything.
	 */
	private void passivateInternalComponents() {
		if (!this.isActivated(CovalentContainer.class)) {
			return;
		}
		List<Class<?>> reverseDependencyOrder = this.internalComponentTypesInDependencyOrder();
		Collections.reverse(reverseDependencyOrder);
		for (Class<?> componentType : reverseDependencyOrder) {
			try {
				this.invokeInternalLifecycleCallback(componentType, Passivatable.class, Passivation.class,
						passivationMethodNameConvention);
			} catch (final RuntimeException t) {
				// Isolated per component, exactly like business components in
				// stopVertical(): one internal component's passivation callback
				// failing must not abort passivation of the others, nor leave
				// stop() unable to report that it did stop.
				CovalentDiagnostics.severe(log,
						"Internal component " + componentType.getSimpleName() + " failed during passivation.", t);
			}
		}
		try {
			this.passivateComponent(CovalentContainer.class);
		} catch (final RuntimeException t) {
			// Isolated too, for the same reason as the per-component loop
			// above: this state transition can itself fail unexpectedly, and
			// that failure must not propagate out of stop() and leave it
			// unable to report that it did stop.
			CovalentDiagnostics.severe(log, "Internal component tree failed to passivate.", t);
		}
	}

	/**
	 * Invokes the removal callback of the {@link ManagedContainerRelay} of a
	 * managed container, by {@link #dispose()}, once the business components
	 * are torn down and before the internal tree is passivated. The relay is
	 * internal, so kept across every stop, but registered as a raw component
	 * rather than part of the internal tree: its callback is invoked here.
	 */
	private void removeManagedContainerRelay() {
		if (!this.isInstantiated(ManagedContainerRelay.class)) {
			return;
		}
		try {
			this.invokeInternalLifecycleCallback(ManagedContainerRelay.class, Removable.class, Removal.class,
					removalMethodNameConvention);
		} catch (final RuntimeException t) {
			CovalentDiagnostics.severe(log, "Managed container relay failed during removal.", t);
		}
	}

	/**
	 * Invokes each internal component's {@code @Removal} callback, in reverse
	 * dependency order, once the internal tree is passivated by
	 * {@link #dispose()}. Internal components are never removed through the
	 * state machine - their Java identity is fixed for the container's whole
	 * life - so only their callback runs, letting them release what their
	 * {@code @Initialization} created.
	 */
	private void removeInternalComponents() {
		List<Class<?>> reverseDependencyOrder = this.internalComponentTypesInDependencyOrder();
		Collections.reverse(reverseDependencyOrder);
		for (Class<?> componentType : reverseDependencyOrder) {
			try {
				this.invokeInternalLifecycleCallback(componentType, Removable.class, Removal.class,
						removalMethodNameConvention);
			} catch (final RuntimeException t) {
				// Isolated per component, like the passivation above.
				CovalentDiagnostics.severe(log,
						"Internal component " + componentType.getSimpleName() + " failed during removal.", t);
			}
		}
	}

	/**
	 * The internal (technical) component types, in the order their composed
	 * fields are declared on {@code CovalentContainerImpl} - which is also the
	 * order they depend on each other (each one is wired assuming the earlier
	 * ones are already usable). This is the activation order; passivation uses
	 * its reverse.
	 */
	private List<Class<?>> internalComponentTypesInDependencyOrder() {
		List<Class<?>> types = new ArrayList<>();
		ReadOnlyComponentMetamodel containerMetamodel = this.useMetamodelReadServices.readMetamodel(CovalentContainer.class);
		for (ReadOnlyComponentMetamodel composed : containerMetamodel.getComposedMetamodels()) {
			types.add(composed.getComponentType());
		}
		return types;
	}

	/**
	 * Directly invokes a single internal component's optional lifecycle
	 * callback (the same lookup {@link #doActivateFlat(Class)}/
	 * {@link #doPassivateFlat(Class)} would use), bypassing the state
	 * machine - which {@link #activateInternalComponents()}/
	 * {@link #passivateInternalComponents()} drive separately, through
	 * {@link #activateComponent(Class)}/{@link #passivateComponent(Class)}.
	 */
	private void invokeInternalLifecycleCallback(final Class<?> componentType, final Class<?> functionalInterface,
			final Class<? extends Annotation> annotation, final String conventionMethodName) {
		final Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
		final Object componentInstance = this.useRegistryReadServices.getComponentInstance(componentType);
		Reflection.invokeOptionalMethod(implementation, functionalInterface, annotation,
				this.findLifecycleMethodByNameConvention ? conventionMethodName : null, componentInstance);
	}

	private void stopHorizontal() {
		this.passivate();
		this.remove();
		this.undeploy();
	}

	private List<Class<?>> getDeployedRootComponentTypes() {
		List<Class<?>> roots = new ArrayList<>();
		for (Class<?> componentType : this.useDeploymentServices.getDeployedComponentTypes()) {
			if (this.useMetamodelReadServices.readMetamodel(componentType).getParentMetamodel() == null) {
				roots.add(componentType);
			}
		}
		return roots;
	}

	private void stopVertical() {
		List<Class<?>> deployedComponents = this.getDeployedRootComponentTypes();
		// undeploy in reversed order of deployment
		Collections.reverse(deployedComponents);
		for (final Class<?> componentType : deployedComponents) {
			if (!this.isDeployed(componentType) || this.isInternalComponent(componentType)) {
				continue;
			}
			try {
				try {
					if (!this.isFailed(componentType) && !this.isPassivated(componentType)) {
						this.passivateComponent(componentType);
					}
					if (this.isDeployed(componentType) && !this.isRemoved(componentType)) {
						this.removeComponent(componentType);
					}
				} catch (RuntimeException failure) {
					if (!this.isFailed(componentType) && this.isDeployed(componentType)) {
						throw failure;
					}
					if (this.isFailed(componentType)) {
						this.removeComponent(componentType);
					}
				}
				if (!this.isUndeployed(componentType)) {
					Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
					this.undeployComponent(implementation);
				}
			} catch (final RuntimeException t) {
				// catch so that the vertical stop can be continued
				CovalentDiagnostics.severe(log,
						"Component " + componentType.getSimpleName() + " failed during vertical stop.", t);
			}
		}
	}

	/**
	 * Deploys several components.
	 */
	@Override
	public synchronized List<ComponentMetamodel> deployComponents(final Class<?>... implementations) {
		RuntimeCheck.notNull(implementations, "implementations");
		for (final Class<?> implementation : implementations) {
			RuntimeCheck.notNull(implementation, "implementation");
		}
		final List<ComponentMetamodel> result = new ArrayList<>();
		for (final Class<?> implementation : implementations) {
			result.add(this.deployComponent(implementation));
		}
		return result;
	}

	/**
	 * Deploys single component.
	 */
	@Override
	public synchronized ComponentMetamodel deployComponent(final Class<?> implementation) {
		RuntimeCheck.notNull(implementation, "implementation");
		ComponentMetamodel metamodel = null;
		try {
			Class<?> componentType = this.useIntrospectionServices.getComponentType(implementation);

			if (!componentType.equals(implementation) && !componentType.isAnnotationPresent(Component.class)) {
				String message = "Component type " + componentType.getSimpleName() + " is not annotated with @"
						+ Component.class.getSimpleName();
				this.useValidationServices.handleStructuralVerification(message);
			}

			if (componentType.equals(implementation) && implementation.isAnnotationPresent(Implementation.class)) {
				String message = "Component implementation " + implementation.getSimpleName()
						+ " has no component type";
				this.useValidationServices.handleStructuralVerification(message);
			}

			metamodel = this.deployComponentInternal(componentType, implementation, true);
		} catch (final Exception e) {
			throw new ComponentLifecycleException(
					failureMessage("During deployment of " + implementation.getSimpleName(), e), e);
		}
		return metamodel;
	}

	/**
	 * Deploys a single component under an explicit component type, distinct
	 * from its implementation class.
	 *
	 * @param componentType  the component type to deploy the implementation under
	 * @param implementation the component implementation class
	 * @return the new component's metamodel
	 */
	public synchronized ComponentMetamodel deployComponent(final Class<?> componentType, final Class<?> implementation) {
		final ComponentMetamodel metamodel = this.deployComponentInternal(componentType, implementation, true);
		return metamodel;
	}

	/**
	 * Deploys a single raw (externally owned, non-Covalent) component,
	 * skipping the {@code @Implementation}/{@code @Component} annotation
	 * checks a managed deployment requires. Reserved for use by
	 * {@link RawComponentsImpl}.
	 *
	 * @param componentType  the raw component type to deploy the implementation under
	 * @param implementation the raw component's implementation class
	 * @return the new component's metamodel
	 */
	public synchronized ComponentMetamodel deployRawComponent(final Class<?> componentType, final Class<?> implementation) {
		final ComponentMetamodel metamodel = this.deployComponentInternal(componentType, implementation, false);
		return metamodel;
	}

	private ComponentMetamodel deployComponentInternal(final Class<?> componentType, final Class<?> implementation,
			final boolean checkImplementationAnnotation) {
		RuntimeCheck.notNull(implementation, "implementation");
		log.fine("Deploy " + implementation.getSimpleName());

		if (implementation.isAnnotationPresent(Component.class)
				&& !implementation.isAnnotationPresent(Implementation.class)) {
			throw new IllegalArgumentException("component impl" + implementation.getSimpleName()
					+ " is indeed a component type, should be an implementation.");
		}
		RuntimeCheck.notNull(componentType, "componentType");
		if (this.isDeployed(componentType)) {
			throw new IllegalStateException("Component " + componentType.getSimpleName() + " is already deployed.");
		}
		// @Internal is reserved to Covalent's own components, deployed by the bootstrap,
		// before the internal tree is activated: a component deployed afterwards carrying
		// it would be kept across stop() like them. ManagedContainerRelay, registered by
		// Covalent as a raw component, is the only exception.
		if ((this.isInternalComponent(componentType) || this.isInternalComponent(implementation))
				&& componentType != ManagedContainerRelay.class && this.isActivated(CovalentContainer.class)) {
			throw new IllegalArgumentException("Component " + implementation.getSimpleName()
					+ " cannot be deployed: @Internal is reserved to Covalent's own components.");
		}

		ComponentMetamodel metamodel = checkImplementationAnnotation
				? this.useDeploymentServices.deploy(componentType, implementation)
				: this.useDeploymentServices.deployRaw(componentType, implementation);
		this.doDeployFlat(componentType);

		for (Class<?> composedImplementation : metamodel.getComposedTypes()) {
			this.deployComponent(composedImplementation);
		}
		return metamodel;
	}

	private void doDeployFlat(final Class<?> componentType) {
		ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		Class<?> implementation = metamodel.getImplementation();

		this.setComponentState(componentType, ComponentState.deployed);
		log.fine("Post deploy " + implementation.getSimpleName());

		this.postDeploy(componentType);
	}

	private void postDeploy(final Class<?> componentType) {
		this.lifecycleTransitions.postDeploy(componentType);
	}

	@Override
	public synchronized boolean isDeployed(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		return this.componentStates.containsKey(componentType);
	}

	/**
	 * Instantiates all deployed components and inject dependencies.
	 */
	@Override
	public synchronized void instantiate() {
		for (final Class<?> componentType : this.getDeployedRootComponentTypes()) {
			if (this.isDeployed(componentType) && !this.isFailed(componentType) && !this.isInstantiated(componentType)) {
				try {
					this.instantiateComponent(componentType);
				} catch (final RuntimeException t) {
					// catch so that instantiation can be continued
					CovalentDiagnostics.severe(log,
							"Component " + componentType.getSimpleName() + " failed during instantiation.", t);
				}
			}
		}
	}

	/**
	 * Instantiates component type. The component should have been previously
	 * deployed.
	 *
	 * @param componentType the component type to instantiate
	 */
	@Override
	public synchronized void instantiateComponent(final Class<?> componentType) {
		Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
		this.instantiateComponent(componentType, implementation);
	}

	private void instantiateComponent(final Class<?> componentType, final Class<?> implementation) {
		this.requireNotFailed(componentType);
		RuntimeCheck.notNull(componentType, "componentType");
		RuntimeCheck.notNull(implementation, "implementation");

		if (this.isInstantiated(componentType)) {
			throw new IllegalStateException("Already instantiated: " + componentType);
		}
		try {
			this.instantiateComponentInternal(componentType);

		} catch (final Exception e) {
			throw new ComponentLifecycleException(
					failureMessage("During instantiation of component " + componentType.getSimpleName(), e), e);
		}
	}

	/**
	 * <p>
	 * pseudo-instantiation. Same steps as regular component instantiation, except
	 * that the component instance is provided as argument.
	 * </p>
	 * 
	 * <p>
	 * Reserved for private usage. This method will throw an exception if called
	 * from outside the container.
	 * </p>
	 * 
	 * @param componentType     the component type being pseudo-instantiated
	 * @param componentInstance the externally provided instance to adopt
	 * @throws IllegalStateException if called from outside the container
	 */
	@Override
	public synchronized void pseudoInstantiateComponent(final Class<?> componentType, final Object componentInstance) {
		this.requireNotFailed(componentType);
		Class<?> caller = CallerFinder.getImmediateCaller();
		if (!(caller.equals(Bootstrapper.class) || caller.equals(RawComponentsImpl.class)
				|| caller.equals(CovalentContainerImpl.class))) {
			throw new IllegalStateException(
					"This method cannot be called from outside the container. Please use RawComponentsServices instead.");
		}

		if (this.isInstantiated(componentType)) {
			String message = "Component already instantiated: " + componentType;
			this.useValidationServices.handleStructuralVerification(message);
		}

		Class<?> implementation = componentInstance.getClass();
		if (!this.isDeployed(componentType)) {
			this.deployComponent(implementation);
		}

		try {
			this.useComponentInstantiationServices.componentify(componentType, componentInstance);
		} catch (RuntimeException | Error failure) {
			this.failComposite(componentType, failure);
			throw failure;
		}
		this.instantiateComponentInternal(componentType, true);
	}

	private void instantiateComponentInternal(final Class<?> componentType) {
		this.instantiateComponentInternal(componentType, false);
	}

	private void instantiateComponentInternal(final Class<?> componentType,
			final boolean rootTechnicalInstantiationDone) {
		final ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		this.doInstantiate(metamodel, rootTechnicalInstantiationDone);
	}

	private void doInstantiate(final ReadOnlyComponentMetamodel metamodel, final boolean technicalInstantiationDone) {
		this.doInstantiateFlat(metamodel.getComponentType(), technicalInstantiationDone);
		for (ReadOnlyComponentMetamodel composed : metamodel.getComposedMetamodels()) {
			this.doInstantiate(composed, false);
		}
	}

	private void doInstantiateFlat(final Class<?> componentType) {
		this.doInstantiateFlat(componentType, false);
	}

	private void doInstantiateFlat(final Class<?> componentType, final boolean technicalInstantiationDone) {
		this.requireNotFailed(componentType);
		if (!this.isDeployed(componentType)) {
			this.deployComponent(componentType);
		}
		if (this.isInstantiated(componentType)) {
			return;
		}

		this.executeTransition(componentType, ComponentState.instantiated, () -> {
			log.fine("Pre instantiate " + componentType.getSimpleName());

			this.preInstantiate(componentType);

			log.fine("Instantiate " + componentType.getSimpleName());

			if (!technicalInstantiationDone) {
				this.useComponentInstantiationServices.instantiateComponent(componentType);
			}

			log.fine("Post instantiate " + componentType.getSimpleName());

			this.postInstantiate(componentType);
		}, () -> this.produceLifecycleEvents.onComponentInstantiated(componentType));
	}

	private void preInstantiate(final Class<?> componentType) {
		this.lifecycleTransitions.preInstantiate(componentType);
	}

	/**
	 * Post instantiation: inject dependencies. Component will be able to use its
	 * dependencies (during initialization).
	 * 
	 * @param componentType
	 */
	private void postInstantiate(final Class<?> componentType) {
		this.lifecycleTransitions.postInstantiate(componentType);
	}

	@Override
	public synchronized boolean isInstantiated(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		final ComponentState state = this.componentStates.get(componentType);
		return state == ComponentState.instantiated || state == ComponentState.initialized
				|| state == ComponentState.activated || state == ComponentState.passivated;
	}

	/**
	 * <p>
	 * Initializes all deployed and instantiated components.
	 */
	@Override
	public synchronized void initialize() {
		final Map<Class<?>, Object> componentInstances = this.useRegistryReadServices.getInstances();
		for (final Class<?> componentType : componentInstances.keySet()) {
			if (this.isDeployed(componentType) && !this.isFailed(componentType) && !this.isInitialized(componentType)) {
				try {
					this.initializeComponent(componentType);
				} catch (final RuntimeException t) {
					// catch so that initialization can be continued
					CovalentDiagnostics.severe(log,
							"Component " + componentType.getSimpleName() + " failed during initialization.", t);
				}
			}
		}
	}

	/**
	 * Initializes component. Should have been previously deployed. The component is
	 * automatically instantiated if this has not been already done.
	 * 
	 * @param componentType the component type
	 */
	@Override
	public synchronized void initializeComponent(final Class<?> componentType) {
		this.requireNotFailed(componentType);
		try {
			RuntimeCheck.notNull(componentType, "componentType");
			if (!this.isDeployed(componentType)) {
				throw new IllegalStateException("Component " + componentType.getSimpleName() + " is not deployed.");
			}

			// first we need to instantiate if not done
			// if the component is composite, all the tree of components
			// will be instantiated there
			if (!this.isInstantiated(componentType)) {
				this.instantiateComponent(componentType);
			}

			final ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
			this.doInitialize(metamodel);

		} catch (final Exception e) {
			if (isAlreadyReported(e)) {
				throw e;
			} else {
				throw new ComponentLifecycleException(
						failureMessage("During initialization of component " + componentType.getSimpleName(), e), e);
			}
		}
	}

	private void doInitialize(final ReadOnlyComponentMetamodel metamodel) {
		// recurse
		for (ReadOnlyComponentMetamodel composed : metamodel.getComposedMetamodels()) {
			this.doInitialize(composed);
		}
		this.doInitializeFlat(metamodel.getComponentType());
	}

	private void doInitializeFlat(final Class<?> componentType) {
		this.requireNotFailed(componentType);

		if (!this.isInstantiated(componentType)) {
			this.doInstantiateFlat(componentType);
		}
		if (this.isInitialized(componentType) && !this.isInternalComponent(componentType)) {
			throw new IllegalStateException("Already initialized: " + componentType);
		}

		this.executeTransition(componentType, ComponentState.initialized, () -> {
			log.fine("Pre initialize " + componentType.getSimpleName());

			this.preInitialize(componentType);

			log.fine("Initialize " + componentType.getSimpleName());

			final Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
			final Object componentInstance = this.useRegistryReadServices.getComponentInstance(componentType);
			Reflection.invokeOptionalMethod(implementation, Initializable.class, Initialization.class,
					this.findLifecycleMethodByNameConvention ? initializationMethodNameConvention : null, componentInstance);

			log.fine("Post initialize " + componentType.getSimpleName());

			this.postInitialize(componentType);
		}, () -> this.produceLifecycleEvents.onComponentInitialized(componentType));
	}

	private void preInitialize(final Class<?> componentType) {
		this.lifecycleTransitions.preInitialize(componentType);
	}

	/**
	 */
	private void postInitialize(final Class<?> componentType) {
		this.lifecycleTransitions.postInitialize(componentType);
	}

	@Override
	public synchronized boolean isInitialized(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		final ComponentState state = this.componentStates.get(componentType);
		return state == ComponentState.initialized || state == ComponentState.activated
				|| state == ComponentState.passivated;
	}

	@Override
	public synchronized Class<?> readComponentType(final Class<?> implementation) {
		RuntimeCheck.notNull(implementation, "implementation");
		final Class<?> componentType = this.useMetamodelReadServices.getType(implementation);
		return componentType;
	}

	/**
	 * activates all initialized components
	 */
	@Override
	public synchronized void activate() {
		final List<Class<?>> deployed = this.getDeployedRootComponentTypes();
		for (final Class<?> componentType : deployed) {
			// CovalentContainer itself is a deployed root (it has no parent metamodel)
			// but is also internal: its activation is driven by Bootstrapper/start(),
			// not by this generic business-component pass. See passivate() below for
			// the same reasoning on the teardown side.
			if (this.isInternalComponent(componentType)) {
				continue;
			}
			if (this.isDeployed(componentType) && !this.isFailed(componentType) && !this.isActivated(componentType)) {
				try {
					this.activateComponent(componentType);
				} catch (final RuntimeException t) {
					// catch so that activation can be continued
					CovalentDiagnostics.severe(log,
							"Component " + componentType.getSimpleName() + " failed during activation.", t);
				}
			}
		}
	}

	/**
	 * Activates the component. Must have been previously deployed. The component is
	 * automatically instantiated and initialized if this has not been already done.
	 * 
	 * @param componentType the component type
	 */
	@Override
	public synchronized void activateComponent(final Class<?> componentType) {
		this.requireNotFailed(componentType);
		try {

			RuntimeCheck.notNull(componentType, "componentType");
			if (!this.isDeployed(componentType)) {
				throw new IllegalStateException("Component " + componentType.getSimpleName() + " is not deployed.");
			}

			// first we need to instantiate if not done
			// if the component is composite, all the tree of components
			// will be instantiated there
			if (!this.isInstantiated(componentType)) {
				this.instantiateComponent(componentType);
			}

			ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
			this.doActivate(metamodel);

		} catch (final Exception e) {
			if (isAlreadyReported(e)) {
				throw e;
			} else {
				throw new ComponentLifecycleException(
						failureMessage("During activation of component " + componentType.getSimpleName(), e), e);
			}
		}
	}

	private void doActivate(final ReadOnlyComponentMetamodel metamodel) {
		// recurse
		for (ReadOnlyComponentMetamodel composed : metamodel.getComposedMetamodels()) {
			this.doActivate(composed);
		}
		this.doActivateFlat(metamodel.getComponentType());
	}

	private void doActivateFlat(final Class<?> componentType) {
		this.requireNotFailed(componentType);
		if (!this.isInitialized(componentType)) {
			// at this point, we know that the component and its children have been
			// instantiated (see #activateComponent(Class)),
			// and activated (see doActivate(ComponentMetamodel) above)
			// so if this component is not activated, we just need to activate
			// it, not its children
			this.doInitializeFlat(componentType);
		}
		if (this.isActivated(componentType) && !this.isInternalComponent(componentType)) {
			throw new IllegalStateException("Already activated: " + componentType);
		}

		this.executeTransition(componentType, ComponentState.activated, () -> {
			log.fine("Pre activate " + componentType.getSimpleName());

			this.preActivate(componentType);

			log.fine("Activate " + componentType.getSimpleName());

			// Internal components' own @Activation callback is invoked separately, by
			// activateInternalComponents(), once every internal component's services
			// are unlocked by this very state transition - not here, where e.g. a
			// sibling internal component's services may still be locked.
			if (!this.isInternalComponent(componentType)) {
				final Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
				final Object componentInstance = this.useRegistryReadServices.getComponentInstance(componentType);
				Reflection.invokeOptionalMethod(implementation, Activable.class, Activation.class,
						this.findLifecycleMethodByNameConvention ? activationMethodNameConvention : null,
						componentInstance);
			}

			log.fine("Post activate " + componentType.getSimpleName());

			this.postActivate(componentType);
		}, () -> this.produceLifecycleEvents.onComponentActivated(componentType));
	}

	private void preActivate(final Class<?> componentType) {
		this.lifecycleTransitions.preActivate(componentType);
	}

	/**
	 * post activation: subscribe to consumed events when using automatic
	 * subscription.
	 * 
	 * The automatic subscription is not performed for all events for which there is
	 * a corresponding subscriber in the component
	 */
	private void postActivate(final Class<?> componentType) {
		this.lifecycleTransitions.postActivate(componentType);
	}

	@Override
	public synchronized boolean isActivated(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		return this.componentStates.get(componentType) == ComponentState.activated;
	}

	/**
	 * <p>
	 * Passivates all instantiated components.
	 * </p>
	 */
	@Override
	public synchronized void passivate() {
		final List<Class<?>> roots = this.getDeployedRootComponentTypes();
		// Children are visited recursively; process roots in reverse deployment order.
		Collections.reverse(roots);
		for (final Class<?> componentType : roots) {
			// CovalentContainer itself is a deployed root (it has no parent metamodel)
			// but is also internal: passivating it here would recursively passivate
			// every internal component through the ordinary business-component walk,
			// far too early (before passivateInternalComponents()'s dedicated,
			// dependency-ordered pass, called separately from stop()). stopVertical()
			// already excludes internal components the same way.
			if (this.isInternalComponent(componentType)) {
				continue;
			}
			if (this.isDeployed(componentType) && !this.isFailed(componentType) && !this.isPassivated(componentType)) {
				try {
					this.passivateComponent(componentType);
				} catch (final RuntimeException t) {
					// catch so that passivation can be continued
					CovalentDiagnostics.severe(log,
							"Component " + componentType.getSimpleName() + " failed during passivation.", t);
				}
			}
		}
	}

	/**
	 * <p>
	 * Passivates the component. Must have been previously deployed. On passivation
	 * :
	 * </p>
	 * <ul>
	 * <li>Provided services are invalidated. If services users try to use them, an
	 * exception is thrown.</li>
	 * <li>Produced events are ignored.</li>
	 * <li>Provided Subscribers are passivated. subscription is pending until
	 * producer is activated again</li>
	 * <li>consumed events and used services are still functioning, allowing the
	 * passivated component to maintain its internal state up to date whenever if
	 * will be activated again.</li>
	 * </ul>
	 * 
	 * @param componentType the component type
	 */
	@Override
	public synchronized void passivateComponent(final Class<?> componentType) {
		this.requireNotFailed(componentType);
		try {
			RuntimeCheck.notNull(componentType, "componentType");

			ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
			RuntimeCheck.notNull(metamodel, "metamodel for " + componentType.getSimpleName());
			this.doPassivate(metamodel);

		} catch (final Exception e) {
			throw new ComponentLifecycleException(
					failureMessage("During passivation of component " + componentType.getSimpleName(), e), e);
		}
	}

	private void doPassivate(final ReadOnlyComponentMetamodel metamodel) {
		// Mirror of doActivate()'s shape, not just its reverse: activation goes
		// self-then-children in construction order because a composite's own
		// @Activation callback routinely needs to call into its (about to be
		// already-active) children (see e.g. HmiBuilderImpl.assembleAndShow()).
		// By the same reasoning, a composite's own @Passivation callback may
		// need to call into a child one last time before that child is torn
		// down - so the composite's own flat passivation must run BEFORE its
		// children are passivated, and its children (like the composites'
		// children in doActivate()) are recursed into in construction order
		// reversed - so a later-constructed sibling, which may depend on an
		// earlier one, is passivated first.
		// Captured before the self-flat op below, not after: getComposedMetamodels()
		// is a snapshot, but the self-flat op must not be able to invalidate it.
		List<ReadOnlyComponentMetamodel> composedMetamodels = new ArrayList<>(metamodel.getComposedMetamodels());
		Collections.reverse(composedMetamodels);
		this.doPassivateFlat(metamodel.getComponentType());
		for (ReadOnlyComponentMetamodel composed : composedMetamodels) {
			// A composed child may already have been passivated out-of-band -
			// typically an application's own LifecycleEvents-driven intervention
			// (e.g. passivating a child from within its own activation
			// notification). Unlike doRemoveFlat, which already tolerates an
			// already-passivated component and simply skips straight to the
			// remove transition, doPassivateFlat unconditionally rejects a
			// double passivation. Reaching an already-passivated child through
			// this cascade is idempotent, not an error: skip it here so the
			// parent's own passivation - and the rest of a stop() sequence -
			// can still proceed. A direct, explicit passivateComponent(...)
			// call on an already-passivated component is unaffected and still
			// rejected by doPassivateFlat below.
			if (this.isPassivated(composed.getComponentType())) {
				continue;
			}
			this.doPassivate(composed);
		}
	}

	private void doPassivateFlat(final Class<?> componentType) {
		this.requireNotFailed(componentType);
		if (this.isPassivated(componentType)) {
			throw new IllegalStateException("Already passivated: " + componentType);
		}
		if (!this.isInitialized(componentType)) {
			// if component has not been initialized, it cannot be passivated
			return;
		}

		this.executeTransition(componentType, ComponentState.passivated, () -> {
			log.fine("Pre passivate " + componentType.getSimpleName());

			this.prePassivate(componentType);

			log.fine("Passivate " + componentType.getSimpleName());

			// Internal components' own @Passivation callback is invoked separately, by
			// passivateInternalComponents(), before any internal component's services
			// get locked by this very state transition - not here, where e.g. a
			// sibling internal component's services may already be locked.
			if (!this.isInternalComponent(componentType)) {
				final Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
				final Object componentInstance = this.useRegistryReadServices.getComponentInstance(componentType);
				Reflection.invokeOptionalMethod(implementation, Passivatable.class, Passivation.class,
						this.findLifecycleMethodByNameConvention ? passivationMethodNameConvention : null,
						componentInstance);
			}

			log.fine("Post passivate " + componentType.getSimpleName());

			this.postPassivate(componentType);
		}, () -> this.produceLifecycleEvents.onComponentPassivated(componentType));
	}

	private void prePassivate(final Class<?> componentType) {
		this.lifecycleTransitions.prePassivate(componentType);
	}

	private void postPassivate(final Class<?> componentType) {
		this.lifecycleTransitions.postPassivate(componentType);
	}

	@Override
	public synchronized boolean isPassivated(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		return this.componentStates.get(componentType) == ComponentState.passivated;
	}

	@Override
	public synchronized void remove() {
		final List<Class<?>> toRemove = new ArrayList<>(this.instantiationOrder);
		// remove in reversed order of instantiation
		Collections.reverse(toRemove);
		for (Class<?> componentType : this.getDeployedRootComponentTypes()) {
			if (this.isFailed(componentType) && !toRemove.contains(componentType)) {
				toRemove.add(componentType);
			}
		}
		for (final Class<?> componentType : toRemove) {
			if (this.isDeployed(componentType) && !this.isRemoved(componentType)) {
				try {
					this.removeComponent(componentType);
				} catch (final RuntimeException t) {
					// catch so that removal can be continued
					CovalentDiagnostics.severe(log,
							"Component " + componentType.getSimpleName() + " failed during removal.", t);
				}
			}
		}
	}

	@Override
	public synchronized void removeComponent(final Class<?> componentType) {
		try {
			RuntimeCheck.notNull(componentType, "componentType");

			ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
			this.doRemove(metamodel);

		} catch (final Exception e) {
			if (isAlreadyReported(e)) {
				throw e;
			} else {
				throw new ComponentLifecycleException(
						failureMessage("During removal of component " + componentType.getSimpleName(), e), e);
			}
		}
	}

	private void doRemove(final ReadOnlyComponentMetamodel metamodel) {
		// See doPassivate(): self before children, children visited in reverse
		// of construction order, for the same reason - a composite's own
		// @Removal callback may need one last call into a child. Captured
		// before the self-flat op, not after, for the same reason as
		// doPassivate(): the self-flat op must not be able to invalidate it.
		List<ReadOnlyComponentMetamodel> composedMetamodels = new ArrayList<>(metamodel.getComposedMetamodels());
		Collections.reverse(composedMetamodels);
		this.doRemoveFlat(metamodel.getComponentType());
		for (ReadOnlyComponentMetamodel composed : composedMetamodels) {
			this.doRemove(composed);
		}
	}

	private void doRemoveFlat(final Class<?> componentType) {
		if (this.isFailed(componentType)) {
			this.cleanComponent(componentType);
			this.setComponentState(componentType, ComponentState.removed);
			this.notifyTransition(() -> this.produceLifecycleEvents.onComponentRemoved(componentType));
			return;
		}
		if (this.isInternalComponent(componentType)) {
			// internal components are not removed
			return;
		}

		if (this.isRemoved(componentType)) {
			String message = "Component " + componentType + " already removed";
			this.useValidationServices.handleDynamicVerification(message);
			return;
		}
		if (!this.isInstantiated(componentType) || this.isRemoved(componentType)) {
			return;
		}
		if (this.isInitialized(componentType) && !this.isPassivated(componentType)) {
			// Self only, not the recursive/public passivateComponent(): this
			// node's children (if any) are passivated-then-removed on their own
			// terms by doRemove()'s own recursion into them, each at the right
			// point in that (now reversed) order - not eagerly, as a side
			// effect of preparing to remove just this node.
			this.doPassivateFlat(componentType);
		}

		this.executeTransition(componentType, ComponentState.removed, () -> {
			log.fine("Pre remove " + componentType.getSimpleName());

			this.preRemove(componentType);

			log.fine("Remove " + componentType.getSimpleName());

			final Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
			final Object componentInstance = this.useRegistryReadServices.getComponentInstance(componentType);
			Reflection.invokeOptionalMethod(implementation, Removable.class, Removal.class,
					this.findLifecycleMethodByNameConvention ? removalMethodNameConvention : null, componentInstance);

			this.useComponentInstantiationServices.removeComponent(componentType);

			log.fine("Post remove " + componentType.getSimpleName());

			this.postRemove(componentType);
		}, () -> this.produceLifecycleEvents.onComponentRemoved(componentType));
	}

	private void preRemove(final Class<?> componentType) {
		this.lifecycleTransitions.preRemove(componentType);
	}

	private void postRemove(final Class<?> componentType) {
		this.lifecycleTransitions.postRemove(componentType);
	}

	@Override
	public synchronized void undeploy() {
		final List<Class<?>> toUndeploy = this.getDeployedRootComponentTypes();
		// undeploy in reversed order of deployment
		Collections.reverse(toUndeploy);
		for (final Class<?> componentType : toUndeploy) {
			if (!this.isDeployed(componentType)) {
				continue;
			}
			try {
				Class<?> implementation = this.useMetamodelReadServices.getImplementation(componentType);
				this.undeployComponent(implementation);
			} catch (final RuntimeException t) {
				// catch so that undeployment can be continued
				CovalentDiagnostics.severe(log,
						"Component " + componentType.getSimpleName() + " failed during undeployment.", t);
			}
		}
	}

	@Override
	public synchronized boolean isRemoved(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		return this.componentStates.get(componentType) == ComponentState.removed;
	}

	@Override
	public synchronized void undeployComponent(final Class<?> implementation) {
		RuntimeCheck.notNull(implementation, "implementation");
		// Note: undeployComponent takes a implementation as argument instead of
		// a component type, although this may be simpler for the caller in some cases.
		// That's because deployComponents takes also a implementation and having a
		// symmetrical API is probably better for user code and avoiding errors.
		Class<?> componentType = this.useMetamodelReadServices.getType(implementation);
		if (componentType == null) {
			if (ComponentAnnotations.isComponent(implementation)) {
				throw new IllegalStateException(
						"Trying to undeploy a component type. Try undeploying component implementation instead: "
								+ implementation);
			}
			throw new IllegalStateException("Not deployed: " + implementation);
		}
		ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		this.doUndeploy(metamodel);
	}

	private void doUndeploy(final ReadOnlyComponentMetamodel metamodel) {
		// See doPassivate(): self before children, children visited in reverse
		// of construction order, for the same reason.
		// getComposedMetamodels returns a snapshot, allowing child detachment -
		// captured before the self-flat op below, not after, since that op is
		// precisely what may detach/delete it.
		List<ReadOnlyComponentMetamodel> composedMetamodels = new ArrayList<>(metamodel.getComposedMetamodels());
		Collections.reverse(composedMetamodels);
		this.doUndeployFlat(metamodel.getComponentType());
		for (ReadOnlyComponentMetamodel composed : composedMetamodels) {
			this.doUndeploy(composed);
		}
	}

	private void doUndeployFlat(final Class<?> componentType) {
		if (this.isInternalComponent(componentType)) {
			// internal components are not undeployed
			return;
		}

		ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		Class<?> implementation = metamodel.getImplementation();
		if (!this.isDeployed(componentType)) {
			// maybe this is a component impl ?
			if (ComponentAnnotations.isComponent(componentType)) {
				throw new IllegalStateException(
						"Trying to undeploy a component type. Try undeploying component implementation instead: "
								+ componentType);
			} else {
				throw new IllegalStateException("Not deployed: " + componentType);
			}
		}
		// Self only, not the recursive/public passivateComponent()/removeComponent():
		// see the identical reasoning in doRemoveFlat(). This node's children (if
		// any) are torn down on their own terms by doUndeploy()'s own recursion.
		if (this.isInitialized(componentType) && !this.isPassivated(componentType)) {
			this.doPassivateFlat(componentType);
		}
		if (this.isFailed(componentType) || (this.isInstantiated(componentType) && !this.isRemoved(componentType))) {
			this.doRemoveFlat(componentType);
		}

		log.fine("Pre undeploy " + componentType.getSimpleName());
		this.preUndeploy(componentType);

		log.fine("Undeploy " + componentType.getSimpleName());

		this.useDeploymentServices.undeploy(implementation);

		this.setComponentState(componentType, null);
	}

	private void preUndeploy(final Class<?> componentType) {
		this.lifecycleTransitions.preUndeploy(componentType);
	}

	@Override
	public synchronized boolean isUndeployed(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		return !this.componentStates.containsKey(componentType);
	}

	private boolean isInternalComponent(final Class<?> componentType) {
		return ComponentAnnotations.isInternalComponent(componentType);
	}

	@Override
	public synchronized void setFindLifecycleMethodByNameConvention(final boolean findLifecycleMethodByNameConvention) {
		this.findLifecycleMethodByNameConvention = findLifecycleMethodByNameConvention;
	}

	@Override
	public synchronized boolean isFindLifecycleMethodByNameConvention() {
		return this.findLifecycleMethodByNameConvention;
	}

	@Override
	public synchronized <T> ComponentState readComponentState(final Class<T> componentType) {
		return this.componentStates.get(componentType);
	}

	/**
	 * Sets whether circular dependency detection is enabled, delegated to
	 * from {@link org.opales.covalent.container.impl.CovalentContainerParametersImpl}
	 * to {@link #dependencyGraphChecker}, the actual owner of that state: it
	 * is read live at the very start of every {@link #activate() start()}
	 * call and is what computes the graph, exposed through
	 * {@link org.opales.covalent.container.lifecycle.DependencyGraphServices}.
	 *
	 * @param circularDependencyChecking {@code true} enables circular
	 *                                    dependency detection
	 */
	public synchronized void setCircularDependencyChecking(final boolean circularDependencyChecking) {
		this.dependencyGraphChecker.setEnabled(circularDependencyChecking);
	}

	/**
	 * Reports whether circular dependency detection is enabled.
	 *
	 * @return {@code true} when circular dependency detection is enabled
	 */
	public synchronized boolean isCircularDependencyChecking() {
		return this.dependencyGraphChecker.isEnabled();
	}

	/**
	 * Sets whether component activation is ordered by the computed
	 * dependency graph (dependencies before dependents, instead of raw
	 * declaration/deployment order), delegated to {@link #dependencyGraphChecker}
	 * the same way as {@link #setCircularDependencyChecking(boolean)} - and
	 * independent of it: either flag alone makes the graph get computed, so
	 * this can be enabled without also enabling circular dependency
	 * reporting, and vice versa.
	 *
	 * @param dependencyOrderedActivation {@code true} orders activation by
	 *                                     computed dependency order
	 */
	public synchronized void setDependencyOrderedActivation(final boolean dependencyOrderedActivation) {
		this.dependencyGraphChecker.setOrderedActivation(dependencyOrderedActivation);
	}

	/**
	 * Reports whether component activation is ordered by the computed
	 * dependency graph.
	 *
	 * @return {@code true} when dependency-ordered activation is enabled
	 */
	public synchronized boolean isDependencyOrderedActivation() {
		return this.dependencyGraphChecker.isOrderedActivation();
	}

	/**
	 * Returns {@code candidates}, reordered to match the computed dependency
	 * graph's {@link org.opales.covalent.container.lifecycle.DependencyGraph#getStartOrder()} when
	 * {@link #isDependencyOrderedActivation()} and a graph was actually
	 * computed; returned as-is (declaration/deployment order) otherwise. A
	 * candidate the graph does not cover - a technical/internal component,
	 * for instance, since {@link #dependencyGraphChecker} is only ever fed
	 * {@link #getDeployedBusinessRootComponentTypes()} - is kept, appended
	 * after every covered candidate, in its original relative order: this
	 * never drops or duplicates a candidate, it only ever reorders the ones
	 * the graph actually knows about.
	 *
	 * @param candidates the component types to order, as found in
	 *                    declaration/deployment order
	 * @return {@code candidates}, reordered by computed dependency order
	 *         when available, else unchanged
	 */
	private List<Class<?>> dependencyOrderedOrAsIs(final List<Class<?>> candidates) {
		if (!this.dependencyGraphChecker.isOrderedActivation() || !this.dependencyGraphChecker.hasStartGraph()) {
			return candidates;
		}
		List<Class<?>> startOrder = this.dependencyGraphChecker.getStartGraph().getStartOrder();
		List<Class<?>> ordered = new ArrayList<>();
		for (final Class<?> componentType : startOrder) {
			if (candidates.contains(componentType)) {
				ordered.add(componentType);
			}
		}
		for (final Class<?> componentType : candidates) {
			if (!ordered.contains(componentType)) {
				ordered.add(componentType);
			}
		}
		return ordered;
	}

	/**
	 * Applies the computed dependency order to the actually deployed
	 * component lists - {@link DeploymentServices#getDeployedComponentTypes()}
	 * and, for every deployed composite, its own children - once, right
	 * after {@link #dependencyGraphChecker} has computed the graph and
	 * before anything is instantiated. A no-op unless
	 * {@link #isDependencyOrderedActivation()} is enabled, a graph was
	 * actually computed, and that graph is free of circular dependencies -
	 * when a cycle exists, the computed order is not trusted enough to be
	 * applied, and declaration order is kept instead.
	 */
	private void applyDependencyOrderedActivationIfEnabled() {
		if (!this.dependencyGraphChecker.isOrderedActivation() || !this.dependencyGraphChecker.hasStartGraph()
				|| this.dependencyGraphChecker.getStartGraph().hasCircularDependencies()) {
			return;
		}

		final List<Class<?>> deployedFlat = this.useDeploymentServices.getDeployedComponentTypes();
		this.useDeploymentServices.reorderDeployedComponentTypes(this.dependencyOrderedOrAsIs(deployedFlat));

		for (final Class<?> componentType : deployedFlat) {
			this.reorderComposedChildrenIfAny(componentType);
		}
	}

	/**
	 * Reorders {@code componentType}'s own composed children (if any) by
	 * computed dependency order, mapping each child's component type back
	 * to its implementation class - {@link MetamodelWriteServices#reorderComposedChildren}
	 * takes implementation classes, matching {@link ComponentMetamodel#getComposedTypes()}.
	 *
	 * @param componentType the composite whose children may need reordering
	 */
	private void reorderComposedChildrenIfAny(final Class<?> componentType) {
		final ReadOnlyComponentMetamodel metamodel = this.useMetamodelReadServices.readMetamodel(componentType);
		final List<? extends ReadOnlyComponentMetamodel> children = metamodel.getComposedMetamodels();
		if (children.isEmpty()) {
			return;
		}

		final List<Class<?>> childComponentTypes = new ArrayList<>();
		final Map<Class<?>, Class<?>> componentTypeToImplementation = new HashMap<>();
		for (final ReadOnlyComponentMetamodel child : children) {
			childComponentTypes.add(child.getComponentType());
			componentTypeToImplementation.put(child.getComponentType(), child.getImplementation());
		}

		final List<Class<?>> orderedComponentTypes = this.dependencyOrderedOrAsIs(childComponentTypes);
		final List<Class<?>> orderedImplementations = new ArrayList<>();
		for (final Class<?> childComponentType : orderedComponentTypes) {
			orderedImplementations.add(componentTypeToImplementation.get(childComponentType));
		}
		this.useMetamodelWriteServices.reorderComposedChildren(componentType, orderedImplementations);
	}

	/**
	 * Every currently deployed root component type that is not internal
	 * (technical) - the set {@link #dependencyGraphChecker} analyzes.
	 */
	private List<Class<?>> getDeployedBusinessRootComponentTypes() {
		List<Class<?>> businessRootComponentTypes = new ArrayList<>();
		for (final Class<?> componentType : this.getDeployedRootComponentTypes()) {
			if (!this.isInternalComponent(componentType)) {
				businessRootComponentTypes.add(componentType);
			}
		}
		return businessRootComponentTypes;
	}

	/** A null state forgets an undeployed component; removal already cleared its instantiation order. */
	private void setComponentState(final Class<?> componentType, final ComponentState state) {
		if (state == null) {
			this.componentStates.remove(componentType);
			return;
		}
		this.componentStates.put(componentType, state);
		if (state == ComponentState.instantiated && !this.instantiationOrder.contains(componentType)) {
			this.instantiationOrder.add(componentType);
		}
		if (state == ComponentState.removed) {
			this.instantiationOrder.remove(componentType);
		}
	}


	private void requireNotFailed(final Class<?> componentType) {
		RuntimeCheck.notNull(componentType, "componentType");
		if (this.isFailed(componentType)) {
			throw new IllegalStateException("Component failed; reset it explicitly before restarting: " + componentType);
		}
	}

	@Override
	public synchronized boolean isFailed(final Class<?> componentType) {
		return this.componentStates.get(componentType) == ComponentState.failed;
	}

	private void executeTransition(final Class<?> componentType, final ComponentState target,
			final Runnable transition, final Runnable notification) {
		try {
			transition.run();
		} catch (RuntimeException | Error failure) {
			this.failComposite(componentType, failure);
			throw failure;
		}
		this.setComponentState(componentType, target);
		this.notifyTransition(notification);
	}

	private void notifyTransition(final Runnable notification) {
		try {
			notification.run();
		} catch (RuntimeException failure) {
			CovalentDiagnostics.severe(log, "Lifecycle notification failed after a completed transition.", failure);
		}
	}

	private ReadOnlyComponentMetamodel compositeRoot(final Class<?> componentType) {
		ReadOnlyComponentMetamodel root = this.useMetamodelReadServices.readMetamodel(componentType);
		RuntimeCheck.notNull(root, "metamodel");
		while (root.getParentMetamodel() != null
				&& !this.isInternalComponent(root.getParentMetamodel().getComponentType())) {
			root = root.getParentMetamodel();
		}
		return root;
	}

	private void collectChildrenFirst(final ReadOnlyComponentMetamodel metamodel, final List<Class<?>> types) {
		for (ReadOnlyComponentMetamodel child : metamodel.getComposedMetamodels()) {
			this.collectChildrenFirst(child, types);
		}
		types.add(metamodel.getComponentType());
	}

	private void cleanComponent(final Class<?> componentType) {
		this.lifecycleTransitions.preFail(componentType);
		this.useComponentInstantiationServices.removeComponent(componentType);
		this.instantiationOrder.remove(componentType);
	}

	private void failComposite(final Class<?> componentType, final Throwable failure) {
		if (this.isInternalComponent(componentType) || this.isFailed(componentType)) {
			return;
		}
		ReadOnlyComponentMetamodel root = this.compositeRoot(componentType);
		List<Class<?>> types = new ArrayList<>();
		this.collectChildrenFirst(root, types);
		CovalentDiagnostics.severe(log, "Component " + componentType.getSimpleName()
				+ " failed; cleaning composite " + root.getComponentType().getSimpleName() + ".", failure);
		for (Class<?> type : types) {
			try {
				this.cleanComponent(type);
			} catch (RuntimeException | Error cleanupFailure) {
				CovalentDiagnostics.severe(log, "Failure cleanup incomplete for " + type.getSimpleName() + ".", cleanupFailure);
			} finally {
				this.setComponentState(type, ComponentState.failed);
			}
		}
		for (Class<?> type : types) {
			this.lifecycleTransitions.postFail(type);
		}
		for (Class<?> type : types) {
			this.notifyTransition(() -> this.produceLifecycleEvents.onComponentFailed(type));
		}
	}

	@Override
	public synchronized void resetFailedComponent(final Class<?> componentType) {
		ReadOnlyComponentMetamodel root = this.compositeRoot(componentType);
		if (!this.isFailed(root.getComponentType())) {
			throw new IllegalStateException("Composite is not failed: " + root.getComponentType());
		}
		List<Class<?>> types = new ArrayList<>();
		this.collectChildrenFirst(root, types);
		// Repeating technical cleanup verifies completion without retaining a failure journal.
		for (Class<?> type : types) {
			this.cleanComponent(type);
		}
		for (Class<?> type : types) {
			this.setComponentState(type, ComponentState.deployed);
		}
	}

}
