/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;

import org.opales.covalent.container.invocation.CovalentInvocationHandler;

/**
 * Base class for the invocation handlers backing an {@code @EventsSubscribe}
 * proxy, on both the producer side
 * ({@link ProducerSideNotifierInvocationHandler}) and the consumer side
 * ({@link UsedSubscriberInvocationHandler}). Introspects the subscribe
 * interface once to classify its methods as subscribe/unsubscribe (by the
 * {@code subscribe}/{@code unsubscribe} naming convention), and resolves
 * the matching overload for a given call by argument types.
 *
 * @param <SUBSCRIBE> the events subscribe interface this handler
 *                    introspects and dispatches for
 */
public abstract class AbstractSubscriberInvocationHandler<SUBSCRIBE> extends CovalentInvocationHandler {

	private static Logger log = Logger.getLogger(AbstractSubscriberInvocationHandler.class.getName());

	/** Methods of {@link #subscribeInterface} whose name starts with {@code subscribe}. */
	protected List<Method> subscribeMethods = new ArrayList<>();

	/** Methods of {@link #subscribeInterface} whose name starts with {@code unsubscribe}. */
	protected List<Method> unsubscribeMethods = new ArrayList<>();

	/** The events subscribe interface this handler introspects and dispatches for. */
	protected Class<SUBSCRIBE> subscribeInterface;

	private final static String subscribeStr = "subscribe";

	private final static String unsubscribeStr = "unsubscribe";

	/**
	 * Creates a new handler for the given subscribe interface.
	 *
	 * @param subscribeInterface the events subscribe interface
	 */
	protected AbstractSubscriberInvocationHandler(final Class<SUBSCRIBE> subscribeInterface) {
		this.subscribeInterface = subscribeInterface;
	}

	/**
	 * Classifies every method of {@link #subscribeInterface} into
	 * {@link #subscribeMethods} or {@link #unsubscribeMethods} by its
	 * {@code subscribe}/{@code unsubscribe} name prefix, logging a warning
	 * for any method matching neither.
	 */
	protected void introspectSubscribeInterface() {
		Method[] methods = this.subscribeInterface.getMethods();
		for (Method method : methods) {
			if (method.getName().startsWith(unsubscribeStr)) {
				this.unsubscribeMethods.add(method);
			} else if (method.getName().startsWith(subscribeStr)) {
				this.subscribeMethods.add(method);
			} else {
				log.warning("Unrecognized method in subscribe interface: " + method.getName());
			}
		}
	}

	/**
	 * Returns the wrapper class of a primitive type, so that a boxed argument
	 * (always seen as a wrapper at runtime) matches a primitive parameter.
	 *
	 * @param type the declared parameter type
	 * @return the wrapper class if {@code type} is primitive, otherwise {@code type}
	 */
	private static Class<?> box(final Class<?> type) {
		if (!type.isPrimitive()) {
			return type;
		}
		if (type == int.class) {
			return Integer.class;
		}
		if (type == long.class) {
			return Long.class;
		}
		if (type == boolean.class) {
			return Boolean.class;
		}
		if (type == double.class) {
			return Double.class;
		}
		if (type == float.class) {
			return Float.class;
		}
		if (type == short.class) {
			return Short.class;
		}
		if (type == byte.class) {
			return Byte.class;
		}
		if (type == char.class) {
			return Character.class;
		}
		return Void.class;
	}

	/**
	 * Resolves the single subscribe or unsubscribe method whose parameter
	 * types match the given argument types.
	 *
	 * @param argTypes  the call's argument types ({@code null} entries match
	 *                  any parameter type)
	 * @param subscribe {@code true} to search {@link #subscribeMethods},
	 *                  {@code false} to search {@link #unsubscribeMethods}
	 * @return the matching method
	 * @throws IllegalStateException if no candidate matches, or if several do
	 */
	protected Method findSubscriberMethod(final List<Class<?>> argTypes, final boolean subscribe) {
		Method result = null;
		List<Method> removedCandidates = new ArrayList<>();
		List<Method> methodsToCheck = subscribe ? this.subscribeMethods : this.unsubscribeMethods;
		for (Method candidate : methodsToCheck) {
			Class<?>[] methodArgTypes = candidate.getParameterTypes();
			if (methodArgTypes.length != argTypes.size()) {
				removedCandidates.add(candidate);
				continue;
			}
			for (int i = 0; i < argTypes.size(); i++) {
				Class<?> argType = argTypes.get(i);
				if (argType != null && !box(methodArgTypes[i]).isAssignableFrom(argType)) {
					// remove candidate
					removedCandidates.add(candidate);
					break;
				}
			}
		}
		List<Method> matchingCandidates = new ArrayList<>(methodsToCheck);
		matchingCandidates.removeAll(removedCandidates);
		String subUnsub = null;
		if (subscribe) {
			subUnsub = subscribeStr;
		} else {
			subUnsub = unsubscribeStr;
		}
		if (matchingCandidates.size() > 1) {
			StringJoiner argTypeNames = new StringJoiner(", ", "[", "]");
			for (Class<?> argType : argTypes) {
				argTypeNames.add(argType == null ? "null" : argType.getSimpleName());
			}
			StringJoiner candidateNames = new StringJoiner(", ", "[", "]");
			for (Method candidate : matchingCandidates) {
				StringJoiner parameterNames = new StringJoiner(", ", candidate.getName() + "(", ")");
				for (Class<?> parameterType : candidate.getParameterTypes()) {
					parameterNames.add(parameterType.getSimpleName());
				}
				candidateNames.add(parameterNames.toString());
			}
			throw new IllegalStateException("More than one " + subUnsub + " method of subscribe interface "
					+ this.subscribeInterface.getSimpleName() + " matches argument types " + argTypeNames + ": "
					+ candidateNames);
		} else if (matchingCandidates.size() == 0) {
			String methodType = null;
			if (subscribe) {
				methodType = "subscribe";
			} else {
				methodType = "unsubscribe";
			}
			String message = "No " + methodType + " method found in " + this.subscribeInterface.getSimpleName()
					+ " with arg: ";
			for (Object argType : argTypes) {
				message += argType + " ";
			}
			throw new IllegalStateException(message);
		} else {
			result = matchingCandidates.get(0);
		}
		return result;
	}

	/**
	 * Returns the types of arguments. Used to find the subscribe/unsubscribe
	 * delegate method to call
	 *
	 * @param args the call's arguments
	 * @return the runtime type of each argument, or {@code null} for a
	 *         {@code null} argument
	 */
	public List<Class<?>> getArgTypes(final Object[] args) {
		List<Class<?>> result = new ArrayList<>();
		for (Object arg : args) {
			if (arg == null) {
				result.add(null);
			} else {
				result.add(arg.getClass());
			}
		}
		return result;
	}

}
