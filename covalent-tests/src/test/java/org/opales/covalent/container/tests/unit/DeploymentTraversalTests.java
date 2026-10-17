/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.deployment.DeploymentEvents;
import org.opales.covalent.container.deployment.DeploymentServices;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.MetamodelEvents;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;

public class DeploymentTraversalTests {

	private static final List<Class<?>> TREE = Arrays.asList(Root.class, Branch.class, Leaf.class, Sibling.class);
	// Reverse of construction order on both axes (self-before-children, and
	// children visited in reverse of their declared/activated order) - see
	// LifecycleManagerImpl's doPassivate()/doRemove()/doUndeploy() javadoc.
	private static final List<Class<?>> REMOVAL_ORDER = Arrays.asList(Root.class, Sibling.class, Branch.class, Leaf.class);
	private CovalentContainer container;
	private DeploymentServices deployment;
	private MetamodelWriteServices metamodels;
	private final List<String> notifications = new ArrayList<>();
	private final List<Class<?>> undeployed = new ArrayList<>();
	private final List<Boolean> parentStatesAtChildCreation = new ArrayList<>();
	private final List<Integer> childrenAtCreation = new ArrayList<>();

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
		this.container.getParameters().setSynchronousEvents(true);
		this.connectServices();
		this.observe();
	}

	private void connectServices() {
		this.deployment = this.container.rcs().useServices(DeploymentServices.class);
		this.metamodels = this.container.rcs().useServices(MetamodelWriteServices.class);
	}

	private void observe() {
		Observer observer = new Observer();
		this.container.rcs().subscribe(MetamodelEvents.class, observer);
		this.container.rcs().subscribe(DeploymentEvents.class, observer);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void metamodelCreationDescribesOneComponentAndItsItems() {
		try {
			ComponentMetamodel root = this.metamodels.createMetamodel(Root.class, RootImpl.class);
			Assert.assertEquals(Arrays.asList(BranchImpl.class, SiblingImpl.class), root.getComposedTypes());
			Assert.assertEquals(1, root.getItemMetamodels().size());
			Assert.assertTrue(root.getComposedMetamodels().isEmpty());
			Assert.assertNull(this.metamodels.readMetamodel(Branch.class));
			Assert.assertFalse(this.deployment.isDeployed(Root.class));

			ComponentMetamodel branch = this.metamodels.createMetamodel(Branch.class, BranchImpl.class);
			Assert.assertSame(root, branch.getParentMetamodel());
			Assert.assertEquals(Arrays.asList(branch), root.getComposedMetamodels());
			Assert.assertNull(this.metamodels.readMetamodel(Leaf.class));

			this.metamodels.deleteMetamodel(Branch.class);
			Assert.assertNull(branch.getParentMetamodel());
			Assert.assertTrue(root.getComposedMetamodels().isEmpty());
		} finally {
			for (Class<?> type : REMOVAL_ORDER) {
				if (this.metamodels.readMetamodel(type) != null) {
					this.metamodels.deleteMetamodel(type);
				}
			}
		}
	}

	@Test
	public void deployerTreatsOneComponentWithoutLifecycleTraversal() {
		try {
			ComponentMetamodel root = this.deployment.deploy(Root.class, RootImpl.class);
			Assert.assertSame(root, this.metamodels.readMetamodel(Root.class));
			Assert.assertTrue(this.deployment.isDeployed(Root.class));
			Assert.assertFalse(this.container.lcs().isDeployed(Root.class));
			Assert.assertNull(this.metamodels.readMetamodel(Branch.class));
			Assert.assertEquals(Arrays.asList("created:Root", "deployed:Root"), this.notifications);
			Assert.assertThrows(RuntimeException.class, () -> this.deployment.deploy(Root.class, RootImpl.class));
			Assert.assertSame(root, this.metamodels.readMetamodel(Root.class));
		} finally {
			this.deployment.undeploy(RootImpl.class);
		}
		Assert.assertNull(this.metamodels.readMetamodel(Root.class));
		Assert.assertFalse(this.deployment.isDeployed(Root.class));
	}

	@Test
	public void deployerUpdatesAnExistingMetamodel() {
		ComponentMetamodel root = this.metamodels.createMetamodel(Root.class, RootImpl.class);
		try {
			Assert.assertSame(root, this.deployment.deploy(Root.class, RootImpl.class));
			Assert.assertEquals(Arrays.asList("created:Root", "updated:Root", "deployed:Root"), this.notifications);
		} finally {
			this.deployment.undeploy(RootImpl.class);
		}
	}

	@Test
	public void lifecycleDeploysAndUndeploysEachNodeInOrder() {
		ComponentMetamodel root = this.container.lcs().deployComponent(RootImpl.class);
		Assert.assertEquals(Arrays.asList("created:Root", "deployed:Root", "created:Branch", "deployed:Branch",
				"created:Leaf", "deployed:Leaf", "created:Sibling", "deployed:Sibling"), this.notifications);
		Assert.assertEquals(Arrays.asList(0, 0, 0, 0), this.childrenAtCreation);
		Assert.assertEquals(Arrays.asList(true, true, true), this.parentStatesAtChildCreation);
		List<Class<?>> deployedTree = this.deployment.getDeployedComponentTypes();
		deployedTree.retainAll(TREE);
		Assert.assertEquals(TREE, deployedTree);
		for (Class<?> type : TREE) {
			Assert.assertTrue(this.container.lcs().isDeployed(type));
		}

		this.notifications.clear();
		this.container.lcs().undeployComponent(RootImpl.class);
		Assert.assertEquals(Arrays.asList("deleted:Root", "undeployed:Root", "deleted:Sibling", "undeployed:Sibling",
				"deleted:Branch", "undeployed:Branch", "deleted:Leaf", "undeployed:Leaf"), this.notifications);
		Assert.assertTrue(root.getComposedMetamodels().isEmpty());
		this.assertTreeRemoved();
		this.container.lcs().deployComponent(RootImpl.class);
		Assert.assertEquals(2, this.metamodels.readMetamodel(Root.class).getComposedMetamodels().size());
	}

	@Test
	public void verticalStartStopAndRestartPreserveTheWholeTree() {
		this.assertStartStopAndRestart(StartMode.vertical);
	}

	@Test
	public void horizontalStartStopAndRestartPreserveTheWholeTree() {
		this.assertStartStopAndRestart(StartMode.horizontal);
	}

	@Test
	public void rawRegistrationPreservesItsInstanceAcrossDeployment() {
		RawFixture instance = new RawFixture();
		this.container.rcs().registerComponent(RawFixture.class, instance);
		RegistryReadServices registry = this.container.rcs().useServices(RegistryReadServices.class);
		Assert.assertSame(instance, registry.getComponentInstance(RawFixture.class));
		Assert.assertTrue(this.deployment.isDeployed(RawFixture.class));
		this.container.rcs().unregisterComponent(RawFixture.class);
		Assert.assertFalse(this.deployment.isDeployed(RawFixture.class));
		Assert.assertNull(this.metamodels.readMetamodel(RawFixture.class));
		this.container.rcs().registerComponent(RawFixture.class, instance);
		Assert.assertSame(instance, registry.getComponentInstance(RawFixture.class));
	}

	private void assertStartStopAndRestart(final StartMode mode) {
		this.container.rcs().accessParameters(LifecycleParameters.class).setStartMode(mode);
		CapturingLogHandler logs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		try {
			for (int cycle = 0; cycle < 2; cycle++) {
				this.container.deployComponents(RootImpl.class);
				this.container.start();
				if (cycle > 0) {
					// stop() (below, previous cycle) removed this raw component and
					// closed its observation ports, like any business component's.
					this.container.rcs().registerComponent(this);
					this.observe();
				}
				for (Class<?> type : TREE) {
					Assert.assertTrue(type.getSimpleName(), this.container.lcs().isActivated(type));
				}
				// This raw component is itself removed by the stop cascade, its
				// ports closed, so it cannot observe the whole teardown: the order
				// is read from the lifecycle manager's own trace instead.
				int undeploysBefore = logs.messagesStartingWith("Undeploy ").size();
				Logger lifecycleLogger = Logger.getLogger(LifecycleManagerImpl.class.getName());
				Level previousLevel = lifecycleLogger.getLevel();
				lifecycleLogger.setLevel(Level.FINE);
				try {
					this.container.stop();
				} finally {
					lifecycleLogger.setLevel(previousLevel);
				}
				List<String> undeployedTree = new ArrayList<>();
				List<String> undeploys = logs.messagesStartingWith("Undeploy ");
				for (String message : undeploys.subList(undeploysBefore, undeploys.size())) {
					String name = message.substring("Undeploy ".length());
					if (TREE.stream().anyMatch(type -> type.getSimpleName().equals(name))) {
						undeployedTree.add(name);
					}
				}
				Assert.assertEquals(REMOVAL_ORDER.stream().map(Class::getSimpleName).toList(), undeployedTree);
				// Unlike lifecycleDeploysAndUndeploysEachNodeInOrder() above (which
				// undeploys without ever stopping the container), the container is
				// genuinely stopped here, so its raw ports - including the ones
				// assertTreeRemoved() would reconnect - are themselves passivated
				// and cannot be used. Check removal through the direct container
				// facade instead, which stays usable while stopped.
				this.assertTreeRemovedViaFacade();
				for (Class<?> type : TREE) {
					Assert.assertFalse(logs.containsSevereMessageWithFailure(
							"Component " + type.getSimpleName() + " failed during passivation.", "Already passivated"));
				}
			}
		} finally {
			logs.restore();
		}
	}

	private void assertTreeRemoved() {
		for (Class<?> type : TREE) {
			Assert.assertNull(this.metamodels.readMetamodel(type));
			Assert.assertFalse(this.deployment.isDeployed(type));
			Assert.assertFalse(this.container.lcs().isDeployed(type));
			Assert.assertFalse(this.container.lcs().isInstantiated(type));
		}
	}

	// Same checks as assertTreeRemoved(), minus the two that go through raw
	// ports (this.metamodels, this.deployment): those require the container to
	// be running, which does not hold for assertStartStopAndRestart's callers,
	// checking right after a genuine stop(). The direct lcs() facade checks
	// below are equivalent evidence that the tree is gone, and stay usable
	// while stopped.
	private void assertTreeRemovedViaFacade() {
		for (Class<?> type : TREE) {
			Assert.assertFalse(this.container.lcs().isDeployed(type));
			Assert.assertFalse(this.container.lcs().isInstantiated(type));
		}
	}

	private class Observer implements MetamodelEvents, DeploymentEvents {
		@Override
		public void onMetamodelCreated(final ComponentMetamodel metamodel) {
			if (TREE.contains(metamodel.getComponentType())) {
				notifications.add("created:" + metamodel.getComponentType().getSimpleName());
				childrenAtCreation.add(metamodel.getComposedMetamodels().size());
				if (metamodel.getParentMetamodel() != null) {
					parentStatesAtChildCreation.add(container.lcs().isDeployed(metamodel.getParentComponentType()));
				}
			}
		}

		@Override
		public void onMetamodelUpdated(final ComponentMetamodel metamodel) {
			if (TREE.contains(metamodel.getComponentType())) {
				notifications.add("updated:" + metamodel.getComponentType().getSimpleName());
			}
		}

		@Override
		public void onMetamodelDeleted(final ComponentMetamodel metamodel) {
			if (TREE.contains(metamodel.getComponentType())) {
				notifications.add("deleted:" + metamodel.getComponentType().getSimpleName());
			}
		}

		@Override
		public void onComponentDeployed(final Class<?> type, final Class<?> implementation) {
			if (TREE.contains(type)) {
				notifications.add("deployed:" + type.getSimpleName());
			}
		}

		@Override
		public void onComponentUndeployed(final Class<?> type, final Class<?> implementation) {
			if (TREE.contains(type)) {
				notifications.add("undeployed:" + type.getSimpleName());
				undeployed.add(type);
			}
		}
	}

	@Component
	public interface Root { }
	@Component
	public interface Branch { }
	@Component
	public interface Leaf { }
	@Component
	public interface Sibling { }

	@Implementation
	public static class RootImpl implements Root {
		@ComposedComponent
		private final BranchImpl branch = null;
		@ComposedComponent
		private final SiblingImpl sibling = null;
		@Item
		private final RootItem item = null;
	}

	@Implementation
	public static class BranchImpl implements Branch {
		@ComposedComponent
		private final LeafImpl leaf = null;
	}

	@Implementation
	public static class LeafImpl implements Leaf { }
	@Implementation
	public static class SiblingImpl implements Sibling { }
	@Item
	public static class RootItem { }
	public static class RawFixture { }
}
