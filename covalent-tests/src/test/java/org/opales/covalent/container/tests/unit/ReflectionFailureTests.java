/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.exceptions.ReflectionException;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.util.Reflection;

/**
 * How {@link Reflection} reports field and method access that cannot be
 * performed: best-effort injection logs and leaves the field untouched, the
 * strict accessors wrap the failure in a {@link ReflectionException}, a
 * method invoked on an unrelated instance is reported as a failed lifecycle
 * callback, and field lookups only match the requested type and annotation.
 */
public class ReflectionFailureTests {

	@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
	public @interface Marker {
	}

	/** Holds fields of several types, one of them marked. */
	public static class Holder {

		String text = "initial";

		@Marker
		Integer marked = 1;

		Integer unmarked = 2;

		void ping() {
			// target of the method invocation tests
		}
	}

	public static class Unrelated {
	}

	private CapturingLogHandler logs;

	@Before
	public void setUp() {
		this.logs = CapturingLogHandler.install(Reflection.class);
	}

	@After
	public void tearDown() {
		this.logs.restore();
	}

	private static Field field(final String name) throws Exception {
		return Holder.class.getDeclaredField(name);
	}

	@Test
	public void injectingAValueOfTheWrongTypeIsLoggedAndLeavesTheFieldUnchanged() throws Exception {
		Holder holder = new Holder();

		Reflection.setField(field("text"), holder, Integer.valueOf(3));

		Assert.assertEquals("initial", holder.text);
		Assert.assertEquals(List.of("Unable to inject field text."),
				this.logs.messagesStartingWith("Unable to inject field"));
	}

	@Test
	public void injectingIfNullWithAValueOfTheWrongTypeIsLoggedAndLeavesTheFieldUnchanged() throws Exception {
		Holder holder = new Holder();
		holder.text = null;

		Reflection.setFieldIfNull(field("text"), holder, Integer.valueOf(3));

		Assert.assertNull(holder.text);
		Assert.assertEquals(List.of("Unable to inject field text."),
				this.logs.messagesStartingWith("Unable to inject field"));
	}

	@Test
	public void injectingIfNullKeepsAnExistingValue() throws Exception {
		Holder holder = new Holder();

		Reflection.setFieldIfNull(field("text"), holder, "other");

		Assert.assertEquals("initial", holder.text);
	}

	@Test
	public void injectingOverridesAnExistingValue() throws Exception {
		Holder holder = new Holder();

		Reflection.setField(field("text"), holder, "other");

		Assert.assertEquals("other", holder.text);
	}

	@Test
	public void readingAFieldFromAnUnrelatedInstanceIsAReflectionException() throws Exception {
		Field text = field("text");

		ReflectionException failure = Assert.assertThrows(ReflectionException.class,
				() -> Reflection.getFieldValue(text, new Unrelated()));

		Assert.assertTrue(failure.getCause() instanceof IllegalArgumentException);
	}

	@Test
	public void readingAFieldReturnsItsValue() throws Exception {
		Assert.assertEquals("initial", Reflection.getFieldValue(field("text"), new Holder()));
	}

	@Test
	public void strictlyWritingAValueOfTheWrongTypeIsAReflectionException() throws Exception {
		Holder holder = new Holder();
		Field text = field("text");

		ReflectionException failure = Assert.assertThrows(ReflectionException.class,
				() -> Reflection.setFieldValueOrThrow(text, holder, Integer.valueOf(3)));

		Assert.assertTrue(failure.getCause() instanceof IllegalArgumentException);
		Assert.assertEquals("initial", holder.text);
	}

	@Test
	public void strictlyWritingAFieldChangesItsValue() throws Exception {
		Holder holder = new Holder();

		Reflection.setFieldValueOrThrow(field("text"), holder, "changed");

		Assert.assertEquals("changed", holder.text);
	}

	@Test
	public void invokingAMethodOnAnUnrelatedInstanceIsReportedAsAFailedLifecycleCallback() throws Exception {
		Method ping = Holder.class.getDeclaredMethod("ping");

		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> Reflection.invokeMethod(ping, new Unrelated()));

		Assert.assertEquals("Exception in lifecycle callback ping", failure.getMessage());
		Assert.assertTrue(failure.getCause() instanceof IllegalArgumentException);
	}

	@Test
	public void aFieldLookupRequiresTheExactType() {
		Assert.assertNull(Reflection.lookupField(Holder.class, Long.class));
		Assert.assertEquals("text", Reflection.lookupField(Holder.class, String.class).getName());
	}

	@Test
	public void aFieldLookupWithAnnotationSkipsFieldsOfTheTypeThatLackTheAnnotation() {
		Field found = Reflection.lookupFieldWithAnnotation(Holder.class, Integer.class, Marker.class);

		Assert.assertEquals("marked", found.getName());
	}

	@Test
	public void aFieldLookupWithAnnotationFindsNothingWhenNoFieldOfTheTypeCarriesIt() {
		Assert.assertNull(Reflection.lookupFieldWithAnnotation(Holder.class, String.class, Marker.class));
	}

	@Test
	public void theFunctionalInterfaceMethodLookupRejectsAnInterfaceWhereAClassIsExpectedAndConversely() {
		Assert.assertThrows(IllegalArgumentException.class,
				() -> Reflection.getFunctionalInterfaceMethod(Runnable.class, Runnable.class));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> Reflection.getFunctionalInterfaceMethod(String.class, Thread.class));
	}
}
