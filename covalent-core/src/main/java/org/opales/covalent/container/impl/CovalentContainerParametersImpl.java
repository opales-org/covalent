/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.impl;

import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.util.StackTraceCleaner;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Default implementation of {@link CovalentContainerParameters}: holds the
 * simple flags (instrumentation, stack trace cleaning, shutdown hook
 * registration) directly, and delegates the verification-level, events, and
 * circular dependency settings to the collaborating
 * {@link ComponentValidatorImpl}, {@link EventsManagerImpl}, and
 * {@link LifecycleManagerImpl}, which are the actual owners of that state.
 */
@Item
public class CovalentContainerParametersImpl implements CovalentContainerParameters {

	/**
	 * Whether runtime instrumentation events are enabled - volatile, since it
	 * can be switched at runtime and is read from every instrumented call's
	 * own thread.
	 */
	private volatile boolean instrumented = false;

	/** Whether the JVM shutdown hook is registered on container start. */
	private volatile boolean registerShutdownHook = true;

	/** Owner of the structural/dynamic rules verification-level settings. */
	private ComponentValidatorImpl componentValidatorImpl;

	/** Owner of the synchronous-events setting and subscription parameters. */
	private EventsManagerImpl eventsManagerImpl;

	/** Owner of the circular dependency verification-level setting. */
	private LifecycleManagerImpl lifecycleManagerImpl;

	/**
	 * Creates a new instance with the default parameter values.
	 */
	public CovalentContainerParametersImpl() {
	}

	/**
	 * Sets the collaborating component validator that actually holds the
	 * structural/dynamic rules verification levels.
	 *
	 * @param componentValidatorImpl the component validator
	 */
	public void setComponentValidatorImpl(final ComponentValidatorImpl componentValidatorImpl) {
		this.componentValidatorImpl = componentValidatorImpl;
	}

	/**
	 * Sets the collaborating events manager that actually holds the
	 * synchronous-events setting.
	 *
	 * @param eventsManagerImpl the events manager
	 */
	public void setEventsManagerImpl(final EventsManagerImpl eventsManagerImpl) {
		this.eventsManagerImpl = eventsManagerImpl;
	}

	/**
	 * Sets the collaborating lifecycle manager that actually holds the
	 * circular dependency verification level.
	 *
	 * @param lifecycleManagerImpl the lifecycle manager
	 */
	public void setLifecycleManagerImpl(final LifecycleManagerImpl lifecycleManagerImpl) {
		this.lifecycleManagerImpl = lifecycleManagerImpl;
	}

	@Override
	public void setInstrumented(final boolean instrumented) {
		this.instrumented = instrumented;
	}

	@Override
	public boolean isInstrumented() {
		return this.instrumented;
	}

	@Override
	public void setCleanStackTraces(final boolean cleanStackTraces) {
		StackTraceCleaner.setEnabled(cleanStackTraces);
	}

	@Override
	public boolean isCleanStackTraces() {
		return StackTraceCleaner.isEnabled();
	}

	@Override
	public void setStructuralRulesChecking(final VerificationLevel structuralRulesChecking) {
		this.componentValidatorImpl.setStructuralRulesVerificationLevel(structuralRulesChecking);
	}

	@Override
	public VerificationLevel getStructuralRulesChecking() {
		return this.componentValidatorImpl.getStructuralRulesVerificationLevel();
	}

	@Override
	public void setDynamicRulesChecking(final VerificationLevel dynamicRulesChecking) {
		this.componentValidatorImpl.setDynamicRulesVerificationLevel(dynamicRulesChecking);
	}

	@Override
	public VerificationLevel getDynamicRulesChecking() {
		return this.componentValidatorImpl.getDynamicRulesVerificationLevel();
	}

	@Override
	public void setSynchronousEvents(final boolean synchronousEvents) {
		this.eventsManagerImpl.setSynchronous(synchronousEvents);
	}

	@Override
	public boolean isSynchronousEvents() {
		return this.eventsManagerImpl.isSynchronous();
	}

	@Override
	public SubscribeParameters newSubscribeParameters() {
		return this.eventsManagerImpl.newSubscribeParameters();
	}

	@Override
	public void setRegisterShutdownHook(final boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	@Override
	public boolean isRegisterShutdownHook() {
		return this.registerShutdownHook;
	}

	@Override
	public void setCircularDependencyChecking(final boolean circularDependencyChecking) {
		this.lifecycleManagerImpl.setCircularDependencyChecking(circularDependencyChecking);
	}

	@Override
	public boolean isCircularDependencyChecking() {
		return this.lifecycleManagerImpl.isCircularDependencyChecking();
	}

	@Override
	public void setDependencyOrderedActivation(final boolean dependencyOrderedActivation) {
		this.lifecycleManagerImpl.setDependencyOrderedActivation(dependencyOrderedActivation);
	}

	@Override
	public boolean isDependencyOrderedActivation() {
		return this.lifecycleManagerImpl.isDependencyOrderedActivation();
	}

}
