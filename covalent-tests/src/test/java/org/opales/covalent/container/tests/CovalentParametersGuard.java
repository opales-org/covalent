/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests;

import org.junit.rules.ExternalResource;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.raw.RawComponentsParameters;

/**
 * Restores the parameters of {@link Covalent#main} to the values they had before each test, whatever
 * the test did and whether it passed or not. The main container is shared by the whole suite, so a
 * parameter changed by a test and not restored would leak into the tests that follow.
 * <p>
 * The values are captured before the test's {@code @Before} methods and restored after its
 * {@code @After} methods.
 */
public final class CovalentParametersGuard extends ExternalResource {

	private boolean instrumented;

	private boolean cleanStackTraces;

	private VerificationLevel structuralRulesChecking;

	private VerificationLevel dynamicRulesChecking;

	private boolean synchronousEvents;

	private boolean circularDependencyChecking;

	private boolean dependencyOrderedActivation;

	private StartMode startMode;

	private boolean findLifecycleMethodByNameConvention;

	private ComponentState defaultStateToReach;

	@Override
	protected void before() {
		CovalentContainerParameters parameters = Covalent.main.getParameters();
		this.instrumented = parameters.isInstrumented();
		this.cleanStackTraces = parameters.isCleanStackTraces();
		this.structuralRulesChecking = parameters.getStructuralRulesChecking();
		this.dynamicRulesChecking = parameters.getDynamicRulesChecking();
		this.synchronousEvents = parameters.isSynchronousEvents();
		this.circularDependencyChecking = parameters.isCircularDependencyChecking();
		this.dependencyOrderedActivation = parameters.isDependencyOrderedActivation();

		LifecycleParameters lifecycleParameters = (LifecycleParameters) Covalent.main.lcs();
		this.startMode = lifecycleParameters.getStartMode();
		this.findLifecycleMethodByNameConvention = lifecycleParameters.isFindLifecycleMethodByNameConvention();

		this.defaultStateToReach = ((RawComponentsParameters) Covalent.main.rcs()).getDefaultStateToReach();
	}

	@Override
	protected void after() {
		CovalentContainerParameters parameters = Covalent.main.getParameters();
		parameters.setInstrumented(this.instrumented);
		parameters.setCleanStackTraces(this.cleanStackTraces);
		parameters.setStructuralRulesChecking(this.structuralRulesChecking);
		parameters.setDynamicRulesChecking(this.dynamicRulesChecking);
		parameters.setSynchronousEvents(this.synchronousEvents);
		parameters.setCircularDependencyChecking(this.circularDependencyChecking);
		parameters.setDependencyOrderedActivation(this.dependencyOrderedActivation);

		LifecycleParameters lifecycleParameters = (LifecycleParameters) Covalent.main.lcs();
		lifecycleParameters.setStartMode(this.startMode);
		lifecycleParameters.setFindLifecycleMethodByNameConvention(this.findLifecycleMethodByNameConvention);

		((RawComponentsParameters) Covalent.main.rcs()).setDefaultStateToReach(this.defaultStateToReach);
	}

}
