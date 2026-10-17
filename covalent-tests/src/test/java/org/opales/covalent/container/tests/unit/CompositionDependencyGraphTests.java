/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.lifecycle.DependencyGraphServices;

/**
 * The dependency graph exposes the composition apart from the dependencies:
 * a composite and its direct children. Start order and cycles take the
 * composition into account: a composite comes after its composed children,
 * and depending on a component held by a composite means depending on that
 * whole composite. A child using a port of its own composite is a
 * dependency, not a cycle with the composition.
 */
public class CompositionDependencyGraphTests {

	private CovalentContainer container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void theCompositionIsExposedApartFromTheDependencies() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.container.deployComponents(ParentImpl.class);
		this.container.rcs().registerComponent(this);

		this.container.start();

		DependencyGraph graph = this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
		Assert.assertEquals(Map.of(Parent.class, Set.of(Child.class)), graph.getCompositions());
		Assert.assertEquals(Set.of(Parent.class), graph.getDependencies().get(Child.class));
		Assert.assertFalse(graph.hasCircularDependencies());
	}

	@Test
	public void aCycleInducedByCompositionIsReportedAsACompletePath() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.container.deployComponents(AImpl.class, BImpl.class);
		this.container.rcs().registerComponent(this);

		this.container.start();

		DependencyGraph graph = this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
		Assert.assertEquals(Set.of(B2.class), graph.getDependencies().get(A1.class));
		Assert.assertEquals(Set.of(A2.class), graph.getDependencies().get(B1.class));
		Assert.assertEquals(List.of(List.of(A.class, A1.class, B.class, B1.class)), graph.getCircularDependencies());
	}

	@Test
	public void aCycleInducedByCompositionFailsAStrictStart() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.container.deployComponents(AImpl.class, BImpl.class);

		RuntimeException failure = Assert.assertThrows(RuntimeException.class, () -> this.container.start());
		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("A -> A1 -> B -> B1 -> A"));
	}

	@Test
	public void aCycleBetweenSiblingsIsStillReported() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.container.deployComponents(SiblingsImpl.class);
		this.container.rcs().registerComponent(this);

		this.container.start();

		DependencyGraph graph = this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
		Assert.assertEquals(List.of(List.of(Sibling1.class, Sibling2.class)), graph.getCircularDependencies());
	}

	@Test
	public void dependingOnAComposedComponentOrdersItsWholeCompositeFirst() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.container.deployComponents(UserImpl.class, AImpl.class);
		this.container.rcs().registerComponent(this);

		this.container.start();

		DependencyGraph graph = this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
		List<Class<?>> order = graph.getStartOrder();
		Assert.assertFalse(graph.hasCircularDependencies());
		Assert.assertTrue(order.indexOf(A1.class) < order.indexOf(A.class));
		Assert.assertTrue(order.indexOf(A2.class) < order.indexOf(A.class));
		Assert.assertTrue(order.indexOf(A.class) < order.indexOf(User.class));
	}

	@Component
	public interface Parent {
	}

	@Component
	public interface Child {
	}

	@Services
	public interface ParentServices {
		void ping();
	}

	@Implementation
	public static class ParentImpl implements Parent, ParentServices {

		@ComposedComponent
		private final ChildImpl child = null;

		@Override
		public void ping() {
			// reachability only
		}
	}

	@Implementation
	public static class ChildImpl implements Child {

		@UsedServices
		private ParentServices useParentServices;
	}

	// A composes A1 and A2, B composes B1 and B2; A1 uses B2, B1 uses A2.

	@Component
	public interface A {
	}

	@Component
	public interface A1 {
	}

	@Component
	public interface A2 {
	}

	@Component
	public interface B {
	}

	@Component
	public interface B1 {
	}

	@Component
	public interface B2 {
	}

	@Services
	public interface A2Services {
		void ping();
	}

	@Services
	public interface B2Services {
		void ping();
	}

	@Implementation
	public static class AImpl implements A {

		@ComposedComponent
		private final A1Impl a1 = null;

		@ComposedComponent
		private final A2Impl a2 = null;
	}

	@Implementation
	public static class A1Impl implements A1 {

		@UsedServices
		private B2Services useB2Services;
	}

	@Implementation
	public static class A2Impl implements A2, A2Services {

		@Override
		public void ping() {
			// reachability only
		}
	}

	@Implementation
	public static class BImpl implements B {

		@ComposedComponent
		private final B1Impl b1 = null;

		@ComposedComponent
		private final B2Impl b2 = null;
	}

	@Implementation
	public static class B1Impl implements B1 {

		@UsedServices
		private A2Services useA2Services;
	}

	@Implementation
	public static class B2Impl implements B2, B2Services {

		@Override
		public void ping() {
			// reachability only
		}
	}

	// User uses A2, composed by A.

	@Component
	public interface User {
	}

	@Implementation
	public static class UserImpl implements User {

		@UsedServices
		private A2Services useA2Services;
	}

	// Siblings composes Sibling1 and Sibling2, which use each other.

	@Component
	public interface Siblings {
	}

	@Component
	public interface Sibling1 {
	}

	@Component
	public interface Sibling2 {
	}

	@Services
	public interface Sibling1Services {
		void ping();
	}

	@Services
	public interface Sibling2Services {
		void ping();
	}

	@Implementation
	public static class SiblingsImpl implements Siblings {

		@ComposedComponent
		private final Sibling1Impl sibling1 = null;

		@ComposedComponent
		private final Sibling2Impl sibling2 = null;
	}

	@Implementation
	public static class Sibling1Impl implements Sibling1, Sibling1Services {

		@UsedServices
		private Sibling2Services useSibling2Services;

		@Override
		public void ping() {
			// reachability only
		}
	}

	@Implementation
	public static class Sibling2Impl implements Sibling2, Sibling2Services {

		@UsedServices
		private Sibling1Services useSibling1Services;

		@Override
		public void ping() {
			// reachability only
		}
	}
}
