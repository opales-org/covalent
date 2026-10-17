/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.opales.covalent.container.ContainerLifecycleEvents;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.async.impl.AsyncManagerImpl;
import org.opales.covalent.container.autowiring.impl.AutoWiringManagerImpl;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.deployment.impl.DeployerImpl;
import org.opales.covalent.container.directory.impl.DirectoryImpl;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.extension.CovalentExtensionsServices;
import org.opales.covalent.container.extension.impl.CovalentExtensionsManagerImpl;
import org.opales.covalent.container.jmx.impl.JmxManagerImpl;
import org.opales.covalent.container.introspection.impl.ComponentIntrospectorImpl;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.metamodel.impl.MetamodelManagerImpl;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.raw.impl.RawComponentsImpl;
import org.opales.covalent.container.registry.impl.PortRegistryManagerImpl;
import org.opales.covalent.container.rr.parameters.impl.ParametersManagerImpl;
import org.opales.covalent.container.rr.services.impl.ServicesManagerImpl;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Default Covalent container implementation.
 */
@Implementation
public class CovalentContainerImpl implements CovalentContainer {

	private static Logger log = Logger.getLogger(CovalentContainerImpl.class.getName());

	/** Validates structural component model rules. */
	@ComposedComponent
	private ComponentValidatorImpl componentValidatorImpl;

	/** Builds read-only metamodels from annotated component implementations. */
	@ComposedComponent
	private ComponentIntrospectorImpl componentIntrospectorImpl;

	/** Tracks the metamodels of components deployed into this container. */
	@ComposedComponent
	private MetamodelManagerImpl metamodelManagerImpl;

	/** Turns deployed implementation classes into managed components. */
	@ComposedComponent
	private DeployerImpl deployerImpl;

	/** Registry of the ports (services, parameters, events) exposed by components. */
	@ComposedComponent
	private PortRegistryManagerImpl portRegistryManagerImpl;

	/** Publishes and resolves provider-side proxies for bound ports. */
	@ComposedComponent
	private DirectoryImpl directoryImpl;

	/** Coordinates event production, subscription and dispatch. */
	@ComposedComponent
	private EventsManagerImpl eventsManagerImpl;

	/** Coordinates the request/response "services" port family. */
	@ComposedComponent
	private ServicesManagerImpl servicesManagerImpl;

	/** Coordinates the request/response "parameters" port family. */
	@ComposedComponent
	private ParametersManagerImpl parametersManagerImpl;

	/** Creates and tears down components' automatically-wired ports. */
	@ComposedComponent
	private AutoWiringManagerImpl autoWiringManagerImpl;

	/** Manages asynchronous task execution and its worker pools. */
	@ComposedComponent
	private AsyncManagerImpl asyncManagerImpl;

	/** Drives component instantiation, activation, passivation and removal. */
	@ComposedComponent
	private LifecycleManagerImpl lifecycleManagerImpl;

	/** Tracks components deployed without full Covalent lifecycle management. */
	@ComposedComponent
	private RawComponentsImpl rawComponentsImpl;

	/** Starts optional runtime extensions requested by the application. */
	@ComposedComponent
	private CovalentExtensionsManagerImpl extensionsManagerImpl;

	/** Registers the container's MBean in the platform MBean server (named containers only). */
	@ComposedComponent
	private JmxManagerImpl jmxManagerImpl;

	/** This container's own mutable configuration, exposed via {@link #getParameters()}. */
	@Item
	private CovalentContainerParametersImpl covalentContainerParameters;

	/** Notifies this container's own lifecycle, injected at bootstrap. */
	@ProducedEvents
	private ContainerLifecycleEvents produceContainerLifecycleEvents;

	/** This container's name, {@code null} for an unmanaged container. */
	private final String name;

	/** The JVM shutdown hook registered by {@code start()}, guarded by the lifecycle manager's lock. */
	private Thread shutdownHook;

	/**
	 * Creates a new unmanaged container, without a name, and bootstraps its
	 * internal component graph, wiring every composed component and item
	 * above together.
	 */
	public CovalentContainerImpl() {
		this(null);
	}

	/**
	 * Creates a new container with the given name, and bootstraps its
	 * internal component graph.
	 *
	 * @param name the name {@code Covalent} manages this container under, or
	 *             {@code null} for an unmanaged container
	 */
	public CovalentContainerImpl(final String name) {
		this.name = name;
		new Bootstrapper(this);
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public RawComponentsServices rcs() {
		return this.rawComponentsImpl;
	}

	@Override
	public LifecycleServices lcs() {
		return this.lifecycleManagerImpl;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Once {@code onContainerStarting} is notified, the start is concluded by
	 * exactly one notification. A failure before the business components are
	 * started - a strict circular dependency verification in particular -
	 * leaves the container not started: {@code onContainerStartFailed} is
	 * notified and the failure thrown. An unexpected failure after they are
	 * started leaves the container started: {@code onContainerStarted} is
	 * notified, with a {@link ContainerStatus#failed failed} report, and the
	 * failure thrown.
	 */
	@Override
	public StartupReport start() {
		// Share the lifecycle monitor so stop cannot interleave with reporting or extensions.
		synchronized (this.lifecycleManagerImpl) {
			if (this.lifecycleManagerImpl.isStarted()) {
				throw new IllegalStateException("Already started");
			}
			this.produceContainerLifecycleEvents.onContainerStarting(this.name);
			float containerDurationSeconds;
			try {
				// the worker threads are started ahead of the business components
				this.asyncManagerImpl.startWorkers();
				this.eventsManagerImpl.startWorkers();
				this.jmxManagerImpl.registerContainerMBean();
				containerDurationSeconds = this.lifecycleManagerImpl.start();
			} catch (RuntimeException | Error failure) {
				// Not started: the worker threads started above are stopped again,
				// leaving the container as it was.
				this.stopWorkersAfter(failure);
				this.produceContainerLifecycleEvents.onContainerStartFailed(this.name, failure);
				throw failure;
			}
			StartupReport report = null;
			try {
				ContainerReportBuilder summaryBuilder = new ContainerReportBuilder(this.deployerImpl,
						this.lifecycleManagerImpl, this.asyncManagerImpl, this.eventsManagerImpl);
				report = summaryBuilder.buildStartupReport(containerDurationSeconds);
				log.info("Covalent container started in " + this.formatDurationSeconds(containerDurationSeconds)
						+ " seconds: " + ContainerReportBuilder.formatApplicationSummary(report) + ".");

				// Before the extensions, so that the hook is in place whatever they do.
				this.registerShutdownHookIfEnabled();
				CovalentExtensionsServices.ExtensionsStartup extensions = this.extensionsManagerImpl
						.startRequestedExtensions(this);
				if (extensions.durationSeconds() > 0f) {
					log.info("Covalent extensions started in " + this.formatDurationSeconds(extensions.durationSeconds())
							+ " seconds.");
				}
				if (!extensions.failedExtensionIds().isEmpty()) {
					log.warning("Covalent container started, but the extensions " + extensions.failedExtensionIds()
							+ " failed to start: the startup is reported as degraded.");
					report = degradedStartupReport(report);
				}
			} catch (RuntimeException | Error failure) {
				// Started all the same: the start is concluded, with a failed report.
				this.produceContainerLifecycleEvents.onContainerStarted(this.name,
						failedStartupReport(report, containerDurationSeconds));
				throw failure;
			}
			this.produceContainerLifecycleEvents.onContainerStarted(this.name, report);
			return report;
		}
	}

	/**
	 * Stops the worker threads after a failed start, any further failure being
	 * attached to the original one.
	 */
	private void stopWorkersAfter(final Throwable failure) {
		try {
			this.jmxManagerImpl.unregisterContainerMBean();
			this.eventsManagerImpl.stopWorkers();
			this.asyncManagerImpl.stopWorkers();
		} catch (RuntimeException | Error secondary) {
			failure.addSuppressed(secondary);
		}
	}

	private static StartupReport degradedStartupReport(final StartupReport report) {
		if (report.getStatus() != ContainerStatus.healthy) {
			return report;
		}
		return new StartupReport(ContainerStatus.degraded, report.getDurationSeconds(), report.getComponentCounts(),
				report.getFailedComponents());
	}

	private static StartupReport failedStartupReport(final StartupReport report, final float durationSeconds) {
		if (report == null) {
			return new StartupReport(ContainerStatus.failed, durationSeconds, new EnumMap<>(ComponentState.class),
					List.of());
		}
		return new StartupReport(ContainerStatus.failed, report.getDurationSeconds(), report.getComponentCounts(),
				report.getFailedComponents());
	}

	private static ShutdownReport failedShutdownReport(final ShutdownReport report, final float durationSeconds,
			final List<String> residues) {
		if (report == null) {
			return new ShutdownReport(ContainerStatus.failed, durationSeconds, new EnumMap<>(ComponentState.class),
					List.of(), residues);
		}
		return new ShutdownReport(ContainerStatus.failed, report.getDurationSeconds(), report.getComponentCounts(),
				report.getFailedComponents(), residues);
	}

	/**
	 * Registers the JVM shutdown hook of this container, so that it is disposed
	 * if the JVM exits without an explicit {@link #dispose()} - unless
	 * {@link CovalentContainerParameters#isRegisterShutdownHook()} is
	 * {@code false} right now, in which case nothing is registered.
	 * <p>
	 * This can only be decided here, not any earlier (such as at
	 * construction): {@code isRegisterShutdownHook()} is only settable,
	 * via {@link #getParameters()}, after the constructor has already
	 * returned to the caller, so reading it any sooner would always read
	 * its unset default. By the time {@link #start()} runs, the caller has
	 * necessarily already had the chance to set it.
	 * <p>
	 * A single hook serves the container for its whole life: it is registered by
	 * the first {@code start()} that finds the parameter enabled, kept across
	 * {@code stop()} and the restarts that follow (the internal components stay
	 * active after a {@code stop()}, so the safety net must stay), and removed by
	 * {@link #dispose()}. The hook reads the parameter again when it runs, so
	 * disabling it later still takes effect.
	 */
	private void registerShutdownHookIfEnabled() {
		if (this.shutdownHook == null && this.covalentContainerParameters.isRegisterShutdownHook()) {
			Thread hook = new Thread(new Bootstrapper.ShutdownHookRunnable(this), "covalent-shutdown-hook");
			Runtime.getRuntime().addShutdownHook(hook);
			this.shutdownHook = hook;
		}
	}

	/**
	 * Removes this container's JVM shutdown hook, if any, once it is disposed.
	 * Nothing to remove while the JVM is already shutting down (the hook is then
	 * the one running the disposal).
	 */
	private void removeShutdownHook() {
		Thread hook = this.shutdownHook;
		this.shutdownHook = null;
		if (hook != null) {
			try {
				Runtime.getRuntime().removeShutdownHook(hook);
			} catch (IllegalStateException shuttingDown) {
				// the JVM is exiting: the hook is running or about to, nothing to remove
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Once {@code onContainerStopping} is notified, the stop is concluded by
	 * {@code onContainerStopped}, the container always ending stopped. An
	 * unexpected failure while emptying the worker pools or building the
	 * report is notified as a {@link ContainerStatus#failed failed} report,
	 * then thrown.
	 */
	@Override
	public ShutdownReport stop() {
		synchronized (this.lifecycleManagerImpl) {
			if (!this.lifecycleManagerImpl.isStarted()) {
				throw new IllegalStateException("Not started");
			}
			this.produceContainerLifecycleEvents.onContainerStopping(this.name);
			long startNanos = System.nanoTime();
			// isolates its own failures: the container always ends stopped
			this.lifecycleManagerImpl.stop();
			Throwable failure = null;
			List<String> residues = List.of();
			try {
				residues = this.listNonInternalResidues();
				if (!residues.isEmpty()) {
					log.warning("Covalent container stopped with " + residues.size()
							+ " residue(s) left in its internal components: " + residues);
				}
			} catch (RuntimeException | Error checkFailure) {
				failure = checkFailure;
			}
			try {
				// The worker pools are emptied of the business components' work and
				// their threads stopped; the internal components themselves are not
				// affected. Built right after, the report reads what the emptying
				// recorded (threads left alive, notifications dropped).
				this.jmxManagerImpl.unregisterContainerMBean();
				this.eventsManagerImpl.stopWorkers();
				this.asyncManagerImpl.stopWorkers();
			} catch (RuntimeException | Error stopFailure) {
				failure = attach(failure, stopFailure);
			}
			float durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000f;
			ShutdownReport report = null;
			try {
				ContainerReportBuilder summaryBuilder = new ContainerReportBuilder(this.deployerImpl,
						this.lifecycleManagerImpl, this.asyncManagerImpl, this.eventsManagerImpl);
				report = summaryBuilder.buildShutdownReport(durationSeconds, residues);
			} catch (RuntimeException | Error reportFailure) {
				failure = attach(failure, reportFailure);
			}
			log.info("Covalent container stopped.");
			if (failure != null) {
				this.produceContainerLifecycleEvents.onContainerStopped(this.name,
						failedShutdownReport(report, durationSeconds, residues));
				throw rethrown(failure);
			}
			this.produceContainerLifecycleEvents.onContainerStopped(this.name, report);
			return report;
		}
	}

	private static Throwable attach(final Throwable first, final Throwable next) {
		if (first == null) {
			return next;
		}
		first.addSuppressed(next);
		return first;
	}

	/** Returns an unchecked failure as is, to be thrown by the caller. */
	private static RuntimeException rethrown(final Throwable failure) {
		if (failure instanceof Error error) {
			throw error;
		}
		return (RuntimeException) failure;
	}

	/**
	 * Lists what the business components left behind in the internal
	 * components once they are torn down: the container is in the state of a
	 * new one when the list is empty.
	 */
	private List<String> listNonInternalResidues() {
		List<String> residues = new ArrayList<>();
		residues.addAll(this.deployerImpl.listNonInternalResidues());
		residues.addAll(this.lifecycleManagerImpl.listNonInternalResidues());
		residues.addAll(this.metamodelManagerImpl.listNonInternalResidues());
		residues.addAll(this.portRegistryManagerImpl.getComponentRegistryImpl().listNonInternalResidues());
		residues.addAll(this.portRegistryManagerImpl.getDescriptorRegistryImpl().listNonInternalResidues());
		residues.addAll(this.directoryImpl.listNonInternalResidues());
		residues.addAll(this.servicesManagerImpl.listNonInternalResidues());
		residues.addAll(this.parametersManagerImpl.listNonInternalResidues());
		residues.addAll(this.eventsManagerImpl.listNonInternalResidues());
		return residues;
	}

	@Override
	public boolean isStarted() {
		return this.lifecycleManagerImpl.isStarted();
	}

	@Override
	public void dispose() {
		synchronized (this.lifecycleManagerImpl) {
			if (!this.lifecycleManagerImpl.isDisposed()) {
				this.produceContainerLifecycleEvents.onContainerDisposing(this.name);
			}
			// a container disposed while started leaves nothing in the MBean server
			this.jmxManagerImpl.unregisterContainerMBean();
			this.lifecycleManagerImpl.dispose();
			this.removeShutdownHook();
			log.info("Covalent container disposed.");
		}
	}

	@Override
	public boolean isDisposed() {
		return this.lifecycleManagerImpl.isDisposed();
	}

	@Override
	public List<ComponentMetamodel> deployComponents(final Class<?>... implementations) {
		return this.lifecycleManagerImpl.deployComponents(implementations);
	}

	@Override
	public CovalentContainerParameters getParameters() {
		return this.covalentContainerParameters;
	}

	/**
	 * Formats a duration in seconds to three decimal places for log messages.
	 *
	 * @param durationSeconds the duration to format, in seconds
	 * @return the formatted duration, e.g. {@code "0.042"}
	 */
	private String formatDurationSeconds(final float durationSeconds) {
		return String.format(Locale.ROOT, "%.3f", durationSeconds);
	}

}
