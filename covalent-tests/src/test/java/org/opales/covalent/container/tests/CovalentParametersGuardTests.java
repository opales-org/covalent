/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.raw.RawComponentsParameters;

/**
 * {@link CovalentParametersGuard} restores the parameters of the main container after a test that
 * changed them, whether that test passed or failed.
 */
public class CovalentParametersGuardTests {

	@Test
	public void parametersChangedByAPassingTestAreRestored() {
		ParametersSnapshot before = ParametersSnapshot.read();

		Result result = JUnitCore.runClasses(PassingTestChangingParameters.class);

		Assert.assertEquals(0, result.getFailureCount());
		Assert.assertEquals(before, ParametersSnapshot.read());
	}

	@Test
	public void parametersChangedByAFailingTestAreRestored() {
		ParametersSnapshot before = ParametersSnapshot.read();

		Result result = JUnitCore.runClasses(FailingTestChangingParameters.class);

		Assert.assertEquals(1, result.getFailureCount());
		Assert.assertEquals(before, ParametersSnapshot.read());
	}

	private static void changeEveryParameter() {
		CovalentContainerParameters parameters = Covalent.main.getParameters();
		parameters.setInstrumented(!parameters.isInstrumented());
		parameters.setCleanStackTraces(!parameters.isCleanStackTraces());
		parameters.setStructuralRulesChecking(other(parameters.getStructuralRulesChecking()));
		parameters.setDynamicRulesChecking(other(parameters.getDynamicRulesChecking()));
		parameters.setSynchronousEvents(!parameters.isSynchronousEvents());
		parameters.setCircularDependencyChecking(!parameters.isCircularDependencyChecking());
		parameters.setDependencyOrderedActivation(!parameters.isDependencyOrderedActivation());

		LifecycleParameters lifecycleParameters = (LifecycleParameters) Covalent.main.lcs();
		lifecycleParameters.setStartMode(
				lifecycleParameters.getStartMode() == StartMode.vertical ? StartMode.horizontal : StartMode.vertical);
		lifecycleParameters
				.setFindLifecycleMethodByNameConvention(!lifecycleParameters.isFindLifecycleMethodByNameConvention());

		RawComponentsParameters rawParameters = (RawComponentsParameters) Covalent.main.rcs();
		rawParameters.setDefaultStateToReach(rawParameters.getDefaultStateToReach() == ComponentState.instantiated
				? ComponentState.activated
				: ComponentState.instantiated);
	}

	private static VerificationLevel other(final VerificationLevel level) {
		return level == VerificationLevel.silent ? VerificationLevel.strict : VerificationLevel.silent;
	}

	/** The values of the parameters restored by the guard. */
	private record ParametersSnapshot(boolean instrumented, boolean cleanStackTraces,
			VerificationLevel structuralRulesChecking, VerificationLevel dynamicRulesChecking,
			boolean synchronousEvents, boolean circularDependencyChecking, boolean dependencyOrderedActivation,
			StartMode startMode, boolean findLifecycleMethodByNameConvention, ComponentState defaultStateToReach) {

		static ParametersSnapshot read() {
			CovalentContainerParameters parameters = Covalent.main.getParameters();
			LifecycleParameters lifecycleParameters = (LifecycleParameters) Covalent.main.lcs();
			return new ParametersSnapshot(parameters.isInstrumented(), parameters.isCleanStackTraces(),
					parameters.getStructuralRulesChecking(), parameters.getDynamicRulesChecking(),
					parameters.isSynchronousEvents(), parameters.isCircularDependencyChecking(),
					parameters.isDependencyOrderedActivation(), lifecycleParameters.getStartMode(),
					lifecycleParameters.isFindLifecycleMethodByNameConvention(),
					((RawComponentsParameters) Covalent.main.rcs()).getDefaultStateToReach());
		}
	}

	/** Run by JUnit from the tests above, not by the build. */
	public static class PassingTestChangingParameters extends CovalentTests {
		@Test
		public void changesEveryParameter() {
			changeEveryParameter();
		}
	}

	/** Run by JUnit from the tests above, not by the build. */
	public static class FailingTestChangingParameters extends CovalentTests {
		@Test
		public void changesEveryParameterThenFails() {
			changeEveryParameter();
			Assert.fail("expected failure");
		}
	}
}
