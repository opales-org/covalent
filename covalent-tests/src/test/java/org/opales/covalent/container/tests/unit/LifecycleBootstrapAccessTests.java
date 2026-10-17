/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.RawComponentLifecycleServices;
import org.opales.covalent.container.lifecycle.impl.HorizontalLifecycleTransitions;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitions;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitionsImpl;
import org.opales.covalent.container.lifecycle.impl.VerticalLifecycleTransitions;
import org.opales.covalent.container.util.Reflection;

@RunWith(Parameterized.class)
public class LifecycleBootstrapAccessTests {

	@Parameterized.Parameters(name = "{0}")
	public static Object[] modes() { return StartMode.values(); }

	private final StartMode mode;
	private CovalentContainer container;
	private LifecycleManagerImpl manager;

	public LifecycleBootstrapAccessTests(final StartMode mode) { this.mode = mode; }

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.manager = (LifecycleManagerImpl) this.container.lcs();
		this.manager.setStartMode(this.mode);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void bootstrapInjectsAllThirteenPrivateDependencies() throws Exception {
		this.assertDependencies(this.manager, 8);
		this.assertDependencies(this.readItem(this.manager), 5);
		Assert.assertTrue(this.manager.isActivated(LifecycleManager.class));
	}

	@Test
	public void bootstrapInitializesVerticalStrategyBeforeConfiguredStartup() throws Exception {
		this.assertStrategy(this.readItem(this.manager), StartMode.vertical);
		Assert.assertEquals(this.mode, this.manager.getStartMode());
	}

	@Test
	public void startupRetainsPrivateFinalItemAndUsesConfiguredStrategy() throws Exception {
		Field field = Reflection.lookupFieldWithAnnotation(LifecycleManagerImpl.class,
				LifecycleTransitionsImpl.class, Item.class);
		Assert.assertTrue(Modifier.isPrivate(field.getModifiers()));
		Assert.assertTrue(Modifier.isFinal(field.getModifiers()));
		LifecycleTransitionsImpl item = this.readItem(this.manager);
		this.container.start();
		Assert.assertSame(item, this.readItem(this.manager));
		this.assertStrategy(item, this.mode);
		this.assertDependencies(this.manager, 8);
		this.assertDependencies(item, 5);
	}

	@Test
	public void restartRetainsItemAndReconfiguresStrategy() throws Exception {
		LifecycleTransitionsImpl item = this.readItem(this.manager);
		this.container.start();
		this.container.stop();
		StartMode otherMode = this.mode == StartMode.vertical ? StartMode.horizontal : StartMode.vertical;
		this.manager.setStartMode(otherMode);
		this.container.start();
		Assert.assertSame(item, this.readItem(this.manager));
		this.assertStrategy(item, otherMode);
		this.assertDependencies(this.manager, 8);
		this.assertDependencies(item, 5);
		Assert.assertTrue(this.manager.isActivated(LifecycleManager.class));
	}

	@Test
	public void containersDoNotShareItemsOrInjectedDependencies() throws Exception {
		CovalentContainer other = Covalent.container();
		try {
			LifecycleManagerImpl otherManager = (LifecycleManagerImpl) other.lcs();
			LifecycleTransitionsImpl item = this.readItem(this.manager);
			LifecycleTransitionsImpl otherItem = this.readItem(otherManager);
			Assert.assertNotSame(item, otherItem);
			for (Object[] pair : List.of(new Object[] { this.manager, otherManager },
					new Object[] { item, otherItem })) {
				for (Field field : pair[0].getClass().getDeclaredFields()) {
					if (field.isAnnotationPresent(UsedServices.class)) {
						field.setAccessible(true);
						Assert.assertNotSame(field.getName(), field.get(pair[0]), field.get(pair[1]));
					}
				}
			}
			this.container.start();
			this.assertStrategy(item, this.mode);
			this.assertStrategy(otherItem, StartMode.vertical);
		} finally {
			other.dispose();
		}
	}

	@Test
	public void managerHasNoBootstrapSettersOrTransitionItemGetter() {
		Set<String> removed = Set.of("setIntrospectionServices", "setValidationServices", "setMetamodelPortReadServices",
				"setMetamodelReadServices", "setDeploymentServices", "setRegistryReadServices",
				"setComponentInstantiationServices", "getLifecycleTransitions");
		for (Method method : LifecycleManagerImpl.class.getDeclaredMethods()) {
			Assert.assertFalse(method.toString(), removed.contains(method.getName()));
		}
	}

	@Test
	public void itemHasNoBootstrapSetters() {
		Set<String> removed = Set.of("setAutoWiringServices", "setServicesLifecycleServices",
				"setEventsLifecycleServices", "setParametersLifecycleServices", "setAsyncLifecycleServices");
		for (Method method : LifecycleTransitionsImpl.class.getDeclaredMethods()) {
			Assert.assertFalse(method.toString(), removed.contains(method.getName()));
		}
	}

	@Test
	public void internalStopAndInstantiationOverloadArePrivate() throws Exception {
		Assert.assertTrue(Modifier
				.isPrivate(LifecycleManagerImpl.class.getDeclaredMethod("stopBusinessCascade").getModifiers()));
		Assert.assertTrue(Modifier.isPrivate(LifecycleManagerImpl.class
				.getDeclaredMethod("instantiateComponent", Class.class, Class.class).getModifiers()));
		Assert.assertThrows(NoSuchMethodException.class,
				() -> LifecycleManagerImpl.class.getMethod("stopBusinessCascade"));
		Assert.assertThrows(NoSuchMethodException.class,
				() -> LifecycleManagerImpl.class.getMethod("instantiateComponent", Class.class, Class.class));
	}

	@Test
	public void functionalServiceAndContainerEntryPointsRemainPublic() throws Exception {
		for (Class<?> services : List.of(LifecycleServices.class, LifecycleParameters.class,
				RawComponentLifecycleServices.class)) {
			for (Method method : services.getMethods()) {
				Method implementation = LifecycleManagerImpl.class.getMethod(method.getName(), method.getParameterTypes());
				Assert.assertTrue(Modifier.isPublic(implementation.getModifiers()));
				Assert.assertTrue(Modifier.isSynchronized(implementation.getModifiers()));
			}
		}
		Assert.assertNotNull(LifecycleManagerImpl.class.getMethod("start"));
		Assert.assertNotNull(LifecycleManagerImpl.class.getMethod("stop"));
		Assert.assertNotNull(LifecycleManagerImpl.class.getMethod("deployRawComponent", Class.class, Class.class));
	}

	private LifecycleTransitionsImpl readItem(final LifecycleManagerImpl lifecycle) throws Exception {
		return (LifecycleTransitionsImpl) Reflection.lookupFieldWithAnnotation(
				LifecycleManagerImpl.class, LifecycleTransitionsImpl.class, Item.class).get(lifecycle);
	}

	private void assertDependencies(final Object target, final int expectedCount) throws Exception {
		int count = 0;
		for (Field field : target.getClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(UsedServices.class)) {
				count++;
				Assert.assertTrue(field.toString(), Modifier.isPrivate(field.getModifiers()));
				field.setAccessible(true);
				Object value = field.get(target);
				Assert.assertNotNull(field.getName(), value);
				Assert.assertTrue(field.getName(), field.getType().isInstance(value));
			}
		}
		Assert.assertEquals(expectedCount, count);
	}

	private void assertStrategy(final LifecycleTransitionsImpl item, final StartMode expected) throws Exception {
		Object strategy = Reflection.lookupField(LifecycleTransitionsImpl.class, LifecycleTransitions.class).get(item);
		Assert.assertNotNull(strategy);
		Assert.assertEquals(expected == StartMode.vertical ? VerticalLifecycleTransitions.class
				: HorizontalLifecycleTransitions.class, strategy.getClass());
	}
}
