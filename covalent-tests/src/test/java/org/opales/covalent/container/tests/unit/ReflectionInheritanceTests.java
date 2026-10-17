/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.util.Reflection;

/**
 * How {@link Reflection} treats inheritance: field lookups walk up the
 * superclasses (the most derived class first), annotated methods are found in
 * superclasses and on interfaces, public methods are found by name including
 * inherited ones, and the unique method of a functional interface is the single
 * abstract one.
 */
public class ReflectionInheritanceTests {

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Marker {
	}

	public static class Parent {
		@Marker
		Integer inheritedMarked = 1;

		String inheritedText = "parent";

		@SuppressWarnings("unused")
		private Long inheritedPrivate = 3L;

		public void inheritedPublic() {
			// found by name from a subclass
		}

		void inheritedPackagePrivate() {
			// not public: never found by name
		}

		@Marker
		void inheritedAnnotated() {
			// found from a subclass
		}
	}

	public static class Child extends Parent {
		Integer own = 2;

		String inheritedText = "child";
	}

	public interface AnnotatedInterface {
		@Marker
		void marked();

		void unmarked();
	}

	public interface PlainInterface {
		void plain();
	}

	public interface TwoAbstractMethods {
		void first();

		void second();
	}

	public interface NoAbstractMethod {
		default void onlyDefault() {
			// no abstract method: not functional
		}
	}

	public interface OneAbstractAmongDefaults {
		default void helper() {
			// not the functional method
		}

		void action();

		@Override
		boolean equals(Object other);

		@Override
		String toString();
	}

	public interface ExtendingFunctional extends Runnable {
		default void helper() {
			// not the functional method
		}
	}

	// -- fields

	@Test
	public void aFieldLookupFindsAFieldDeclaredInASuperclass() {
		Field found = Reflection.lookupField(Child.class, Long.class);

		Assert.assertNotNull(found);
		Assert.assertEquals("inheritedPrivate", found.getName());
		Assert.assertEquals(Parent.class, found.getDeclaringClass());
	}

	@Test
	public void aFieldLookupWithAnnotationFindsAnInheritedAnnotatedField() {
		Field found = Reflection.lookupFieldWithAnnotation(Child.class, Integer.class, Marker.class);

		Assert.assertNotNull(found);
		Assert.assertEquals("inheritedMarked", found.getName());
	}

	@Test
	public void aFieldLookupPrefersTheMostDerivedClass() {
		Field found = Reflection.lookupField(Child.class, String.class);

		Assert.assertEquals(Child.class, found.getDeclaringClass());
	}

	@Test
	public void aFieldLookupFindsNothingWhenNeitherTheClassNorItsSuperclassesDeclareTheType() {
		Assert.assertNull(Reflection.lookupField(Child.class, Double.class));
	}

	// -- annotated methods

	@Test
	public void anAnnotatedMethodIsFoundInASuperclass() {
		Method found = Reflection.getAnnotatedMethod(Child.class, Marker.class);

		Assert.assertNotNull(found);
		Assert.assertEquals("inheritedAnnotated", found.getName());
	}

	@Test
	public void anAnnotatedMethodIsFoundOnAnInterface() {
		Method found = Reflection.getAnnotatedMethod(AnnotatedInterface.class, Marker.class);

		Assert.assertNotNull(found);
		Assert.assertEquals("marked", found.getName());
	}

	@Test
	public void noAnnotatedMethodOnAnInterfaceIsReportedAsNull() {
		Assert.assertNull(Reflection.getAnnotatedMethod(PlainInterface.class, Marker.class));
	}

	// -- public methods by name

	@Test
	public void aPublicMethodIsFoundByNameIncludingInheritedOnes() {
		Method found = Reflection.getPublicMethodByName(Child.class, "inheritedPublic");

		Assert.assertNotNull(found);
		Assert.assertEquals(Parent.class, found.getDeclaringClass());
	}

	@Test
	public void aNonPublicOrMissingMethodIsNotFoundByName() {
		Assert.assertNull(Reflection.getPublicMethodByName(Child.class, "inheritedPackagePrivate"));
		Assert.assertNull(Reflection.getPublicMethodByName(Child.class, "absent"));
	}

	// -- functional interface method

	@Test
	public void aFunctionalInterfaceWithSeveralAbstractMethodsIsRejected() {
		Assert.assertThrows(IllegalArgumentException.class,
				() -> Reflection.getFunctionalInterfaceMethod(TwoAbstractMethods.class));
	}

	@Test
	public void aFunctionalInterfaceWithoutAbstractMethodIsRejected() {
		Assert.assertThrows(IllegalArgumentException.class,
				() -> Reflection.getFunctionalInterfaceMethod(NoAbstractMethod.class));
	}

	@Test
	public void theFunctionalMethodIsTheSingleAbstractOneIgnoringDefaultsAndObjectMethods() {
		Assert.assertEquals("action", Reflection.getFunctionalInterfaceMethod(OneAbstractAmongDefaults.class).getName());
		Assert.assertEquals("compare", Reflection.getFunctionalInterfaceMethod(Comparator.class).getName());
		Assert.assertEquals("run", Reflection.getFunctionalInterfaceMethod(ExtendingFunctional.class).getName());
	}

	@Test
	public void aClassImplementingAnInterfaceWithSeveralAbstractMethodsIsRejected() {
		Assert.assertThrows(IllegalArgumentException.class,
				() -> Reflection.getFunctionalInterfaceMethod(TwoAbstractMethods.class, TwoImplementation.class));
	}

	/** Implements {@link TwoAbstractMethods}. */
	public static class TwoImplementation implements TwoAbstractMethods {
		@Override
		public void first() {
		}

		@Override
		public void second() {
		}
	}
}
