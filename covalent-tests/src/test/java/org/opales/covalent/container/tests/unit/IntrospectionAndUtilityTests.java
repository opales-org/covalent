/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.util.Reflection;

public class IntrospectionAndUtilityTests {

	// A fresh, private container rather than Covalent.main: these tests never
	// deploy or start anything, they only need a container whose technical
	// services are usable - which a never-started container's pre-first-start
	// bootstrap window already guarantees, without depending on whatever
	// start/stop state other test classes left the shared container in.
	private final RawComponentsServices rcs = Covalent.container().rcs();

	{
		this.rcs.registerComponent(this);
	}

	private final IntrospectionServices introspectionServices = this.rcs.useServices(IntrospectionServices.class);

	@Test
	public void introspectionResolvesComponentTypeFromImplementation() {
		Assert.assertEquals(LocalComponent.class, this.introspectionServices.getComponentType(LocalComponentImpl.class));
		Assert.assertEquals(PlainClass.class, this.introspectionServices.getComponentType(PlainClass.class));
	}

	@Test
	public void introspectionRecognizesPortInterfacesAndComponentFields() throws Exception {
		Field usedServicesField = ComponentWithFields.class.getDeclaredField("useLocalServices");
		Field plainField = ComponentWithFields.class.getDeclaredField("plainValue");

		Assert.assertTrue(this.introspectionServices.isComponentServices(LocalServices.class));
		Assert.assertFalse(this.introspectionServices.isComponentServices(PlainClass.class));
		Assert.assertTrue(this.introspectionServices.isComponentField(usedServicesField));
		Assert.assertFalse(this.introspectionServices.isComponentField(plainField));
	}

	@Test
	public void reflectionDoesNotOverrideExistingFieldWhenSettingOnlyIfNull() throws Exception {
		MutableFieldHolder holder = new MutableFieldHolder();
		Field field = MutableFieldHolder.class.getDeclaredField("value");

		Reflection.setFieldIfNull(field, holder, "injected");
		Reflection.setFieldIfNull(field, holder, "ignored");

		Assert.assertEquals("injected", holder.value);

		Reflection.setField(field, holder, "overridden");

		Assert.assertEquals("overridden", holder.value);
	}

	@Test
	public void reflectionWrapsFailingLifecycleCallbackWithMethodName() throws Exception {
		Method method = FailingCallback.class.getDeclaredMethod("initialize");

		RuntimeException e = Assert.assertThrows("Reflection should wrap failing lifecycle callbacks.",
				RuntimeException.class,
				() -> Reflection.invokeMethod(method, new FailingCallback()));
		Assert.assertTrue(e.getMessage().contains("initialize"));
		Assert.assertTrue(e.getCause() instanceof IllegalStateException);
		Assert.assertTrue(e.getCause().getMessage().contains("callback failure"));
	}

	@Component
	public interface LocalComponent {
	}

	@Services
	public interface LocalServices {
		void call();
	}

	@Implementation
	public static class LocalComponentImpl implements LocalComponent {
	}

	public static class ComponentWithFields {
		@UsedServices
		private LocalServices useLocalServices;

		private String plainValue;
	}

	public static class MutableFieldHolder {
		private String value;
	}

	public static class FailingCallback {
		@SuppressWarnings("unused")
		private void initialize() {
			throw new IllegalStateException("callback failure");
		}
	}

	public static class PlainClass {
	}
}
