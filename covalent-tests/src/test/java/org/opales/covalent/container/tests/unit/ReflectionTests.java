/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.exceptions.ReflectionException;
import org.opales.covalent.container.util.Reflection;

public class ReflectionTests {

	@Test
	public void functionalInterfaceTakesPrecedenceOverAnnotationAndName() {
		InterfaceFixture instance = new InterfaceFixture();
		Reflection.invokeOptionalMethod(InterfaceFixture.class, Action.class, ActionMethod.class, "byName", instance);
		Assert.assertEquals(List.of("interface"), instance.calls);
	}

	@Test
	public void privateAnnotatedMethodTakesPrecedenceOverName() {
		AnnotatedFixture instance = new AnnotatedFixture();
		Reflection.invokeOptionalMethod(AnnotatedFixture.class, Action.class, ActionMethod.class, "byName", instance);
		Assert.assertEquals(List.of("annotation"), instance.calls);
	}

	@Test
	public void conventionalNameIsUsedAsLastResort() {
		NamedFixture instance = new NamedFixture();
		Reflection.invokeOptionalMethod(NamedFixture.class, Action.class, ActionMethod.class, "byName", instance);
		Assert.assertEquals(List.of("name"), instance.calls);
	}

	@Test
	public void nullNameDisablesConventionalLookup() {
		NamedFixture instance = new NamedFixture();
		Reflection.invokeOptionalMethod(NamedFixture.class, Action.class, ActionMethod.class, null, instance);
		Assert.assertTrue(instance.calls.isEmpty());
	}

	@Test
	public void nullNameStillAllowsAnnotatedMethod() {
		AnnotatedFixture instance = new AnnotatedFixture();
		Reflection.invokeOptionalMethod(AnnotatedFixture.class, Action.class, ActionMethod.class, null, instance);
		Assert.assertEquals(List.of("annotation"), instance.calls);
	}

	@Test
	public void missingMethodDoesNothing() {
		NamedFixture instance = new NamedFixture();
		Reflection.invokeOptionalMethod(NamedFixture.class, Action.class, ActionMethod.class, "absent", instance);
		Assert.assertTrue(instance.calls.isEmpty());
	}

	@Test
	public void invocationFailurePreservesCauseWithoutTryingFallback() {
		FailingFixture instance = new FailingFixture();
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> Reflection.invokeOptionalMethod(FailingFixture.class, Action.class, ActionMethod.class,
						"byName", instance));
		Assert.assertSame(instance.failure, failure.getCause());
		Assert.assertTrue(instance.calls.isEmpty());
	}

	@FunctionalInterface
	public interface Action {
		void execute();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ActionMethod {
	}

	public static class NamedFixture {
		final List<String> calls = new ArrayList<>();

		public void byName() {
			this.calls.add("name");
		}
	}

	public static class AnnotatedFixture extends NamedFixture {
		@ActionMethod
		private void annotated() {
			this.calls.add("annotation");
		}
	}

	public static class InterfaceFixture extends NamedFixture implements Action {
		@Override
		public void execute() {
			this.calls.add("interface");
		}

		@ActionMethod
		private void annotated() {
			this.calls.add("annotation");
		}
	}

	public static class FailingFixture extends NamedFixture implements Action {
		final RuntimeException failure = new IllegalStateException("Expected invocation failure");

		@Override
		public void execute() {
			throw this.failure;
		}
	}

	// -- instantiateWithDefaultConstructor: the documented instantiation
	// contract (Runtime Requirements and Compatibility, in doc/user-guide.md).

	@Test
	public void instantiateWithDefaultConstructorRejectsAbstractClass() {
		IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
				() -> Reflection.instantiateWithDefaultConstructor(AbstractFixture.class));
		Assert.assertTrue(failure.getMessage().contains("abstract"));
	}

	@Test
	public void instantiateWithDefaultConstructorRejectsInterface() {
		IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
				() -> Reflection.instantiateWithDefaultConstructor(Action.class));
		Assert.assertTrue(failure.getMessage().contains("interface"));
	}

	@Test
	public void instantiateWithDefaultConstructorRequiresAPublicConstructor() {
		// PackagePrivateConstructorFixture has a no-arg constructor, but it is not
		// public: getConstructor() (not getDeclaredConstructor()) only finds public
		// members, so this must fail rather than silently reaching a non-public one.
		Assert.assertThrows(ReflectionException.class,
				() -> Reflection.instantiateWithDefaultConstructor(PackagePrivateConstructorFixture.class));
	}

	@Test
	public void instantiateWithDefaultConstructorSucceedsForAPublicNoArgConstructor() {
		Object instance = Reflection.instantiateWithDefaultConstructor(NamedFixture.class);
		Assert.assertTrue(instance instanceof NamedFixture);
	}

	// -- getFieldValue / setFieldValueOrThrow

	@Test
	public void getFieldValueReadsAPrivateFieldAndCastsIt() throws NoSuchFieldException {
		FieldFixture instance = new FieldFixture();
		Field field = FieldFixture.class.getDeclaredField("value");

		String value = Reflection.getFieldValue(field, instance, String.class);

		Assert.assertEquals("initial", value);
	}

	@Test
	public void getFieldValueReturnsNullWithoutACheckWhenFieldIsNull() throws NoSuchFieldException {
		FieldFixture instance = new FieldFixture();
		instance.value = null;
		Field field = FieldFixture.class.getDeclaredField("value");

		Assert.assertNull(Reflection.getFieldValue(field, instance, Integer.class));
	}

	@Test
	public void getFieldValueRejectsATypeMismatch() throws NoSuchFieldException {
		FieldFixture instance = new FieldFixture();
		Field field = FieldFixture.class.getDeclaredField("value");

		Assert.assertThrows(IllegalStateException.class,
				() -> Reflection.getFieldValue(field, instance, Integer.class));
	}

	@Test
	public void setFieldValueOrThrowWritesAPrivateField() throws NoSuchFieldException {
		FieldFixture instance = new FieldFixture();
		Field field = FieldFixture.class.getDeclaredField("value");

		Reflection.setFieldValueOrThrow(field, instance, "replaced");

		Assert.assertEquals("replaced", instance.value);
	}

	// -- lookupOptionalMethod

	@Test
	public void lookupOptionalMethodFindsAMatchingPublicMethod() throws NoSuchMethodException {
		Method expected = NamedFixture.class.getMethod("byName");
		Method found = Reflection.lookupOptionalMethod(NamedFixture.class, "byName");
		Assert.assertEquals(expected, found);
	}

	@Test
	public void lookupOptionalMethodReturnsNullInsteadOfThrowingWhenMissing() {
		Assert.assertNull(Reflection.lookupOptionalMethod(NamedFixture.class, "doesNotExist"));
	}

	// -- isJdkReflectionOrProxyFrame / isCovalentDispatchFrame: the shared
	// predicates behind CallerFinder's caller resolution and
	// StackTraceCleaner's technical-frame filtering.

	@Test
	public void isJdkReflectionOrProxyFrameRecognizesKnownJdkPackages() {
		Assert.assertTrue(Reflection.isJdkReflectionOrProxyFrame("java.lang.reflect.Method"));
		Assert.assertTrue(Reflection.isJdkReflectionOrProxyFrame("jdk.internal.reflect.NativeMethodAccessorImpl"));
		Assert.assertTrue(Reflection.isJdkReflectionOrProxyFrame("jdk.proxy1.$Proxy0"));
		Assert.assertTrue(Reflection.isJdkReflectionOrProxyFrame("com.sun.proxy.$Proxy1"));
	}

	@Test
	public void isJdkReflectionOrProxyFrameRejectsApplicationAndCovalentClasses() {
		Assert.assertFalse(Reflection.isJdkReflectionOrProxyFrame("example.MyProxyHandler"));
		Assert.assertFalse(Reflection.isJdkReflectionOrProxyFrame("example.MyInvocationHandler"));
		Assert.assertFalse(
				Reflection.isJdkReflectionOrProxyFrame("org.opales.covalent.container.invocation.CovalentInvocationHandler"));
	}

	@Test
	public void isCovalentDispatchFrameRecognizesCovalentDispatchPackages() {
		Assert.assertTrue(Reflection
				.isCovalentDispatchFrame("org.opales.covalent.container.invocation.CovalentInvocationHandler"));
		Assert.assertTrue(
				Reflection.isCovalentDispatchFrame("org.opales.covalent.container.rr.common.ServerRRInvocationHandler"));
		Assert.assertTrue(Reflection
				.isCovalentDispatchFrame("org.opales.covalent.container.rr.services.impl.ServicesManagerImpl"));
		Assert.assertTrue(Reflection
				.isCovalentDispatchFrame("org.opales.covalent.container.rr.parameters.impl.ParametersManagerImpl"));
		Assert.assertTrue(
				Reflection.isCovalentDispatchFrame("org.opales.covalent.container.events.impl.EventsManagerImpl"));
	}

	@Test
	public void isCovalentDispatchFrameRejectsUnrelatedClasses() {
		Assert.assertFalse(Reflection.isCovalentDispatchFrame("example.BusinessService"));
		Assert.assertFalse(Reflection.isCovalentDispatchFrame("org.opales.covalent.container.util.Reflection"));
	}

	public abstract static class AbstractFixture {
	}

	public static class PackagePrivateConstructorFixture {
		PackagePrivateConstructorFixture() {
		}
	}

	public static class FieldFixture {
		private String value = "initial";
	}
}
