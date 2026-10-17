/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;

import org.opales.covalent.container.exceptions.ReflectionException;

/**
 * Reflection helpers used throughout the container to instantiate
 * component implementations, invoke lifecycle and functional-interface
 * methods, and read or write annotated fields - centralizing the
 * boilerplate of making members accessible and wrapping checked reflective
 * exceptions into unchecked ones.
 */
public final class Reflection {

	private static final Logger log = Logger.getLogger(Reflection.class.getName());

	/** Shared empty parameter-types array, to avoid reallocating one per no-arg lookup. */
	private static final Class<?>[] emptyArrayOfClasses = new Class<?>[0];

	/** Shared empty arguments array, to avoid reallocating one per no-arg invocation. */
	private static final Object[] emptyArrayOfObjects = new Object[0];

	/** Static helpers only: not instantiable. */
	private Reflection() {
	}

	/**
	 * <p>
	 * Returns a public method with no args by its name, from given class. Public
	 * methods inherited from superclasses and interfaces are found as well; a
	 * method that is not public is never found.
	 * </p>
	 *
	 * @param aClass     the class to search
	 * @param methodName the method name
	 * @return the matching public no-arg method, made accessible, or
	 *         {@code null} if none exists
	 */
	public static Method getPublicMethodByName(final Class<?> aClass, final String methodName) {
		return lookupOptionalMethod(aClass, methodName, emptyArrayOfClasses);
	}

	/**
	 * <p>
	 * Looks up a public method by name and parameter types, using
	 * {@link Class#getMethod(String, Class[])}. Unlike the plain JDK lookup,
	 * this returns {@code null} instead of throwing when no matching method
	 * exists, and marks the returned method accessible. This is the shared
	 * primitive behind optional-method lookups such as
	 * {@link #getPublicMethodByName} and filter-method resolution in the events
	 * package.
	 * </p>
	 *
	 * @param aClass         the class to search
	 * @param methodName     the method name
	 * @param parameterTypes the method's parameter types
	 * @return the matching method, made accessible, or {@code null} if none
	 *         exists
	 */
	public static Method lookupOptionalMethod(final Class<?> aClass, final String methodName,
			final Class<?>... parameterTypes) {
		Method method = null;
		try {
			method = aClass.getMethod(methodName, parameterTypes);
			method.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException e) {
			log.fine("Could not look up method " + methodName + " on " + aClass.getName()
					+ " because of following error: " + e);
		}
		return method;
	}

	/**
	 * <p>
	 * Returns the unique method from a functional interface, if it is implemented
	 * by given class.
	 * </p>
	 *
	 *
	 * @param anInterface the functional interface
	 * @param aClass      the class to check for an implementation
	 * @return the functional interface's unique method, or {@code null} if
	 *         {@code aClass} does not implement {@code anInterface}
	 * @throws IllegalArgumentException if {@code anInterface} is not an
	 *                                  interface or {@code aClass} is one, or
	 *                                  if {@code aClass} implements
	 *                                  {@code anInterface} and the latter does
	 *                                  not have exactly one abstract method
	 */
	public static Method getFunctionalInterfaceMethod(final Class<?> anInterface, final Class<?> aClass) {
		RuntimeCheck.isTrue(anInterface.isInterface(), anInterface + " is an interface");
		RuntimeCheck.isTrue(!aClass.isInterface(), aClass + " is a class");
		Method method = null;
		if (anInterface.isAssignableFrom(aClass)) {
			method = getFunctionalInterfaceMethod(anInterface);
		}
		return method;
	}

	/**
	 * <p>
	 * Returns the unique abstract method of a functional interface, inherited
	 * ones included. Default and static methods are ignored, and so are the
	 * abstract redeclarations of the public methods of {@link Object}
	 * (for instance {@code equals} in {@link java.util.Comparator}).
	 * </p>
	 *
	 * @param anInterface the functional interface
	 * @return the interface's unique abstract method
	 * @throws IllegalArgumentException if {@code anInterface} does not have
	 *                                  exactly one abstract method
	 */
	public static Method getFunctionalInterfaceMethod(final Class<?> anInterface) {
		Method found = null;
		for (final Method method : anInterface.getMethods()) {
			if (!Modifier.isAbstract(method.getModifiers()) || redeclaresObjectMethod(method)) {
				continue;
			}
			if (found != null) {
				throw new IllegalArgumentException(
						anInterface + " is not a functional interface: it has several abstract methods, including "
								+ found.getName() + " and " + method.getName());
			}
			found = method;
		}
		if (found == null) {
			throw new IllegalArgumentException(anInterface + " is not a functional interface: it has no abstract method");
		}
		return found;
	}

	/**
	 * Tells whether the given method redeclares a public method of {@link Object}.
	 *
	 * @param method the method to check
	 * @return true if {@link Object} has a public method of the same name and
	 *         parameter types
	 */
	private static boolean redeclaresObjectMethod(final Method method) {
		try {
			Object.class.getMethod(method.getName(), method.getParameterTypes());
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	/**
	 * <p>
	 * Returns method with provided annotation from given class. An interface is
	 * searched on its own declared methods, since it has no superclass.
	 * </p>
	 *
	 * @param aClass             the class (and its superclasses) to search
	 * @param searchedAnnotation the method annotation to look for
	 * @return the first annotated method found walking up from
	 *         {@code aClass} to (but not including) {@link Object}, or
	 *         {@code null} if none is found
	 */
	public static Method getAnnotatedMethod(final Class<?> aClass,
			final Class<? extends Annotation> searchedAnnotation) {
		// only returns the first annotated method found, walking up from the given
		// class to (but not including) Object
		Class<?> currentType = aClass;
		while (currentType != null && currentType != Object.class) {
			// iterate though the list of methods declared in the current class
			for (final Method method : currentType.getDeclaredMethods()) {
				if (method.isAnnotationPresent(searchedAnnotation)) {
					return method;
				}
			}
			// move to the upper class in the hierarchy in search for more methods
			currentType = currentType.getSuperclass();
		}
		return null;
	}

	/**
	 * <p>
	 * invokes provided method on given instance, after making the method
	 * accessible.
	 * </p>
	 *
	 * @param method   the no-arg method to invoke
	 * @param instance the instance to invoke it on
	 * @return the method's return value, or {@code null} for a {@code void}
	 *         method
	 */
	public static Object invokeMethod(final Method method, final Object instance) {
		Object result = null;
		try {
			if (!method.canAccess(instance)) {
				method.setAccessible(true);
			}
			result = method.invoke(instance, emptyArrayOfObjects);
		} catch (final InvocationTargetException e) {
			Throwable cause = StackTraceCleaner.cleanIfEnabled(e.getCause());
			throw StackTraceCleaner.cleanIfEnabled(
					new RuntimeException("Exception in lifecycle callback " + method.getName(), cause));
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw new RuntimeException("Exception in lifecycle callback " + method.getName(), e);
		}
		return result;
	}

	/**
	 * Invokes an optional no-argument method, looking first for the functional
	 * interface, then the annotation, then the conventional name. A null name
	 * disables the last lookup. No matching method is a no-op.
	 *
	 * @param implementation       the class to search for a matching method
	 * @param functionalInterface  the lifecycle marker interface to check
	 *                             first (e.g. {@link Reflection}'s callers
	 *                             pass {@code Initializable.class} and
	 *                             similar)
	 * @param annotation           the method annotation to check second
	 * @param conventionMethodName the conventional no-arg method name to
	 *                             check last, or {@code null} to skip it
	 * @param instance             the instance to invoke the resolved
	 *                             method on
	 */
	public static void invokeOptionalMethod(final Class<?> implementation, final Class<?> functionalInterface,
			final Class<? extends Annotation> annotation, final String conventionMethodName, final Object instance) {
		Method method = getFunctionalInterfaceMethod(functionalInterface, implementation);
		if (method == null) {
			method = getAnnotatedMethod(implementation, annotation);
		}
		if (method == null && conventionMethodName != null) {
			method = getPublicMethodByName(implementation, conventionMethodName);
		}
		if (method != null) {
			invokeMethod(method, instance);
		}
	}

	/**
	 * Instantiates the given class through its public no-arg constructor.
	 *
	 * @param implementation the class to instantiate; must be a concrete
	 *                       (non-interface, non-abstract) class with a
	 *                       public no-arg constructor
	 * @return the new instance
	 * @throws IllegalArgumentException if {@code implementation} is an
	 *                                   interface or abstract
	 * @throws ReflectionException      if instantiation otherwise fails
	 */
	public static Object instantiateWithDefaultConstructor(final Class<?> implementation) {
		Object instance = null;
		// Interfaces are also reported abstract by Class#getModifiers(), so this
		// check must come first: otherwise an interface would always be reported
		// as merely "abstract" and the more precise message below would never be
		// reachable.
		if (implementation.isInterface()) {
			throw new IllegalArgumentException("cannot instantiate component :" + implementation.getSimpleName()
					+ ". Implementation class is an interface");
		}
		if (Modifier.isAbstract(implementation.getModifiers())) {
			throw new IllegalArgumentException("cannot instantiate component :" + implementation.getSimpleName()
					+ ". Implementation class is abstract");
		}
		try {
			final Constructor<?> constructor = implementation.getConstructor(emptyArrayOfClasses);
			instance = constructor.newInstance(emptyArrayOfObjects);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new ReflectionException(e);
		}
		return instance;
	}

	/**
	 * Sets the given field's value on the given instance, but only if the
	 * field is currently {@code null} - existing values are left untouched.
	 * Access failures are logged and swallowed, not thrown, since this is
	 * used for best-effort autowiring.
	 *
	 * @param field    the field to set
	 * @param instance the instance to set it on
	 * @param value    the value to set
	 */
	public static void setFieldIfNull(final Field field, final Object instance, final Object value) {
		setFieldValue(field, instance, value, false);
	}

	/**
	 * Unconditionally sets the given field's value on the given instance,
	 * overriding any existing value. Access failures are logged and
	 * swallowed, not thrown, since this is used for best-effort autowiring.
	 *
	 * @param field    the field to set
	 * @param instance the instance to set it on
	 * @param value    the value to set
	 */
	public static void setField(final Field field, final Object instance, final Object value) {
		setFieldValue(field, instance, value, true);
	}

	/**
	 * Shared implementation behind {@link #setField} and
	 * {@link #setFieldIfNull}.
	 *
	 * @param field         the field to set
	 * @param instance      the instance to set it on
	 * @param value         the value to set
	 * @param allowOverride if {@code false}, the field is only set when its
	 *                      current value is {@code null}
	 */
	private static void setFieldValue(final Field field, final Object instance, final Object value,
			final boolean allowOverride) {
		try {
			field.setAccessible(true);
			Object previousValue = field.get(instance);
			if (previousValue == null || allowOverride) {
				// field is not already set, can be safely injected
				field.set(instance, value);
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			CovalentDiagnostics.severe(log, "Unable to inject field " + field.getName() + ".", e);
		}
	}

	/**
	 * <p>
	 * Reads a field's current value, making it accessible first. Unlike
	 * {@link #setField}/{@link #setFieldIfNull}, this does not swallow
	 * access failures: they are wrapped in a {@link ReflectionException} and
	 * propagated, since callers that need a field's value generally cannot
	 * proceed sensibly without it.
	 * </p>
	 *
	 * @param field    the field to read
	 * @param instance the instance to read it from
	 * @return the field's current value
	 */
	public static Object getFieldValue(final Field field, final Object instance) {
		try {
			field.setAccessible(true);
			return field.get(instance);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new ReflectionException(e);
		}
	}

	/**
	 * <p>
	 * Reads a field's current value and casts it to the expected type.
	 * {@code null} is returned as-is, without a type check. A non-null value
	 * of an unexpected type is reported as an {@link IllegalStateException},
	 * since that indicates a metamodel/instance mismatch rather than a
	 * reflective access failure.
	 * </p>
	 *
	 * @param <T>          the expected type
	 * @param field        the field to read
	 * @param instance     the instance to read it from
	 * @param expectedType the type the field's value is expected to be an
	 *                     instance of
	 * @return the field's current value, cast to {@code expectedType}, or
	 *         {@code null}
	 */
	public static <T> T getFieldValue(final Field field, final Object instance, final Class<T> expectedType) {
		Object value = getFieldValue(field, instance);
		if (value == null) {
			return null;
		}
		if (!expectedType.isInstance(value)) {
			throw new IllegalStateException(
					"Field " + field + " contains " + value.getClass().getName() + " but " + expectedType.getName()
							+ " was expected.");
		}
		return expectedType.cast(value);
	}

	/**
	 * <p>
	 * Unconditionally sets a field's value, making it accessible first.
	 * Unlike {@link #setField}, which logs and swallows access failures for
	 * best-effort injection, this propagates access failures wrapped in a
	 * {@link ReflectionException}: it is meant for call sites where the
	 * write must succeed for the caller to remain in a consistent state.
	 * </p>
	 *
	 * @param field    the field to write
	 * @param instance the instance to write it on
	 * @param value    the value to set
	 */
	public static void setFieldValueOrThrow(final Field field, final Object instance, final Object value) {
		try {
			field.setAccessible(true);
			field.set(instance, value);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new ReflectionException(e);
		}
	}

	/**
	 * <p>
	 * True if the given stack frame class name belongs to the JDK's own
	 * reflection or dynamic-proxy machinery (core reflection, or a
	 * JDK-generated dynamic-proxy class), rather than to application or
	 * Covalent code. Proxy classes generated by {@link java.lang.reflect.Proxy}
	 * are not covered by a documented naming contract; this matches the
	 * package prefixes actually used by the supported JDK versions
	 * (see the "Runtime Requirements and Compatibility" section of the user
	 * guide for the versions this project verifies against).
	 * </p>
	 *
	 * @param className a stack frame's declaring class name
	 * @return true if it is a JDK reflection or dynamic-proxy frame
	 */
	public static boolean isJdkReflectionOrProxyFrame(final String className) {
		return className.startsWith("java.lang.reflect.") || className.startsWith("jdk.internal.reflect.")
				|| className.startsWith("jdk.proxy") || className.startsWith("com.sun.proxy.");
	}

	/**
	 * <p>
	 * True if the given stack frame class name belongs to Covalent's own
	 * dynamic dispatch machinery (invocation handlers, remote-request
	 * proxies, events dispatch) rather than to the application code that
	 * calls into Covalent, or that Covalent calls into. Intended for
	 * cosmetic stack-trace filtering (see {@link StackTraceCleaner}), where
	 * hiding every frame in these packages is the goal.
	 * </p>
	 * <p>
	 * Not suitable for caller-identity resolution: some of these same
	 * packages also contain manager classes ({@code EventsManagerImpl}, for
	 * instance) that {@link CallerFinder} deliberately treats as legitimate,
	 * reportable callers rather than frames to skip past.
	 * </p>
	 *
	 * @param className a stack frame's declaring class name
	 * @return true if it is one of Covalent's internal dispatch frames
	 */
	public static boolean isCovalentDispatchFrame(final String className) {
		return className.startsWith("org.opales.covalent.container.invocation.")
				|| className.startsWith("org.opales.covalent.container.rr.common.")
				|| className.startsWith("org.opales.covalent.container.rr.services.impl.")
				|| className.startsWith("org.opales.covalent.container.rr.parameters.impl.")
				|| className.startsWith("org.opales.covalent.container.events.impl.");
	}

	/**
	 * Looks up the first field of the given type, carrying the given
	 * annotation, in the given class or in its superclasses. The most derived
	 * class is searched first.
	 *
	 * @param declaringClass the class to search
	 * @param fieldType      the field's expected type
	 * @param annotation     the field annotation to require
	 * @return the matching field, made accessible, or {@code null} if none
	 *         is found
	 */
	public static Field lookupFieldWithAnnotation(final Class<?> declaringClass, final Class<?> fieldType,
			final Class<? extends Annotation> annotation) {
		return lookupFieldInternal(declaringClass, fieldType, annotation);
	}

	/**
	 * Looks up the first field of the given type in the given class or in its
	 * superclasses, with no annotation requirement. The most derived class is
	 * searched first.
	 *
	 * @param declaringClass the class to search
	 * @param fieldType      the field's expected type
	 * @return the matching field, made accessible, or {@code null} if none
	 *         is found
	 */
	public static Field lookupField(final Class<?> declaringClass, final Class<?> fieldType) {
		return lookupFieldInternal(declaringClass, fieldType, null);
	}

	/**
	 * Shared implementation behind {@link #lookupField} and
	 * {@link #lookupFieldWithAnnotation}.
	 *
	 * @param declaringClass the class to search, then its superclasses
	 * @param fieldType      the field's expected type
	 * @param annotation     the field annotation to require, or
	 *                       {@code null} to accept any
	 * @return the matching field, made accessible, or {@code null} if none is
	 *         found
	 */
	private static Field lookupFieldInternal(final Class<?> declaringClass, final Class<?> fieldType,
			final Class<? extends Annotation> annotation) {
		for (Class<?> current = declaringClass; current != null && current != Object.class; current = current
				.getSuperclass()) {
			for (final Field field : current.getDeclaredFields()) {
				if (field.getType().equals(fieldType)
						&& (annotation == null || field.isAnnotationPresent(annotation))) {
					field.setAccessible(true);
					return field;
				}
			}
		}
		return null;
	}

}
