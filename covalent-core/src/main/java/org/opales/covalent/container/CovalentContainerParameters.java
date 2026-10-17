/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container;

import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.events.SubscribeParameters;

/**
 * Configures global Covalent runtime behavior.
 */
@Parameters
public interface CovalentContainerParameters {
	/**
	 * Enables or disables runtime instrumentation events.
	 *
	 * @param instrumented {@code true} enables instrumentation events
	 */
	void setInstrumented(final boolean instrumented);

	/**
	 * Reports whether runtime instrumentation events are enabled.
	 *
	 * @return {@code true} when instrumentation events are enabled
	 */
	boolean isInstrumented();

	/**
	 * Enables or disables stack trace cleaning on framework-raised exceptions.
	 * This setting is shared by every container of the JVM: changing it on one
	 * container changes it for all of them.
	 *
	 * @param cleanStackTraces {@code true} removes internal framework frames
	 */
	void setCleanStackTraces(final boolean cleanStackTraces);

	/**
	 * Reports whether stack trace cleaning is enabled, a setting shared by every
	 * container of the JVM.
	 *
	 * @return {@code true} when stack traces are cleaned
	 */
	boolean isCleanStackTraces();

	/**
	 * Sets the verification level for structural component model rules.
	 *
	 * @param verificationLevel structural verification level
	 */
	void setStructuralRulesChecking(final VerificationLevel verificationLevel);

	/**
	 * Returns the verification level for structural component model rules.
	 *
	 * @return structural verification level
	 */
	VerificationLevel getStructuralRulesChecking();

	/**
	 * Sets the verification level for dynamic runtime rules.
	 *
	 * @param verificationLevel dynamic verification level
	 */
	void setDynamicRulesChecking(final VerificationLevel verificationLevel);

	/**
	 * Returns the verification level for dynamic runtime rules.
	 *
	 * @return dynamic verification level
	 */
	VerificationLevel getDynamicRulesChecking();

	/**
	 * Sets whether event dispatch runs synchronously on the producer call stack.
	 * The setting is read when a producer obtains its notifier, so set it before
	 * the producers are created.
	 *
	 * @param synchronousEvents {@code true} dispatches events synchronously
	 */
	void setSynchronousEvents(boolean synchronousEvents);

	/**
	 * Reports whether event dispatch runs synchronously on the producer call stack.
	 *
	 * @return {@code true} when event dispatch is synchronous
	 */
	boolean isSynchronousEvents();

	/**
	 * Creates subscription parameters for a manual event subscription.
	 *
	 * @return new mutable subscription parameters
	 */
	SubscribeParameters newSubscribeParameters();

	/**
	 * Enables or disables the JVM shutdown hook that stops this container if
	 * it is still started when the JVM exits without an explicit
	 * {@link CovalentContainer#stop()} call. Enabled by default. A fresh hook
	 * is registered on every {@link CovalentContainer#start()} call, reading
	 * this flag at that time; when disabled, no hook is registered at all.
	 * To take effect, this must therefore be set before {@code start()} is
	 * called, not after.
	 *
	 * @param registerShutdownHook {@code true} lets the shutdown hook stop
	 *                              this container on JVM exit
	 */
	void setRegisterShutdownHook(final boolean registerShutdownHook);

	/**
	 * Reports whether the JVM shutdown hook is allowed to stop this
	 * container on JVM exit.
	 *
	 * @return {@code true} when the shutdown hook is enabled
	 */
	boolean isRegisterShutdownHook();

	/**
	 * Enables or disables circular dependency detection between managed
	 * components. Disabled by default: no dependency graph is computed at
	 * all, since the check is opt-in extra work. When enabled, a fresh
	 * {@link org.opales.covalent.container.lifecycle.DependencyGraph} is
	 * computed on every {@link CovalentContainer#start()} call, reading this
	 * flag at the very start of that call - the same live-read-at-start()
	 * timing contract as {@link #setRegisterShutdownHook(boolean)} - over
	 * whichever managed components are already deployed at that point; a
	 * component deployed later, after {@code start()} has begun, is not
	 * covered. To take effect, this must therefore be set before
	 * {@code start()} is called, not after.
	 * <p>
	 * This flag only turns the computation on or off; it does not decide
	 * what happens when a circular dependency is found. That behavior is
	 * driven by {@link #getStructuralRulesChecking()}, the same as every
	 * other structural rule Covalent checks: at
	 * {@link VerificationLevel#silent silent}, a found cycle is not even
	 * reported; at {@link VerificationLevel#trace trace} or
	 * {@link VerificationLevel#warn warn}, it is logged at that level and
	 * {@code start()} proceeds regardless; at {@link VerificationLevel#strict
	 * strict}, {@code start()} throws before starting anything, leaving the
	 * container unstarted rather than starting components in an unreliable
	 * order.
	 * <p>
	 * Raw components are never covered by the graph: wiring their own
	 * dependencies, cycles included, through {@code RawComponentsServices}
	 * directly is part of the freedom they trade the managed model's
	 * guardrails for.
	 *
	 * @param circularDependencyChecking {@code true} computes the dependency
	 *                                    graph on every {@code start()} call
	 */
	void setCircularDependencyChecking(final boolean circularDependencyChecking);

	/**
	 * Reports whether circular dependency detection between managed
	 * components is enabled.
	 *
	 * @return {@code true} when circular dependency detection is enabled
	 */
	boolean isCircularDependencyChecking();

	/**
	 * Enables or disables ordering managed component activation by the
	 * computed dependency graph's
	 * {@link org.opales.covalent.container.lifecycle.DependencyGraph#getStartOrder()}
	 * (dependencies before dependents, instead of raw declaration/deployment
	 * order), raw components excluded as per {@link #setCircularDependencyChecking(boolean)}.
	 * Independent of {@link #setCircularDependencyChecking(boolean)}:
	 * either flag alone is enough to compute the graph, since this one
	 * needs it just as much as cycle detection does - so this can be
	 * enabled on its own, without also enabling circular dependency
	 * reporting. Same live-read-at-start() timing contract. A component not
	 * covered by the graph - deployed after {@code start()} began, or a
	 * cycle participant when {@link #getStructuralRulesChecking()} did not
	 * stop {@code start()} - keeps its original relative position rather
	 * than being dropped or duplicated.
	 *
	 * @param dependencyOrderedActivation {@code true} orders activation by
	 *                                     computed dependency order
	 */
	void setDependencyOrderedActivation(final boolean dependencyOrderedActivation);

	/**
	 * Reports whether managed component activation is ordered by the
	 * computed dependency graph.
	 *
	 * @return {@code true} when dependency-ordered activation is enabled
	 */
	boolean isDependencyOrderedActivation();

	/**
	 * Defines how Covalent handles validation messages.
	 */
	public enum VerificationLevel {
		/**
		 * Ignores validation messages.
		 */
		silent,
		/**
		 * Logs validation messages at fine (trace-like) level.
		 */
		trace,
		/**
		 * Logs validation messages at warning level.
		 */
		warn,
		/**
		 * Throws a {@code ValidationException} for a validation message
		 * instead of merely logging it.
		 */
		strict;
	}

}
