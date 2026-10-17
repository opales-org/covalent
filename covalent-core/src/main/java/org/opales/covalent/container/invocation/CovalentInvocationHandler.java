/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.invocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.util.StackTraceCleaner;

/**
 * Base {@link InvocationHandler} for every JDK dynamic proxy the container
 * creates (services, parameters, events subscribers, request/response
 * client- and provider-side proxies, surrogates). Handles the concerns
 * common to all of them - dispatching {@link Object} methods
 * ({@code equals}/{@code hashCode}/{@code toString})
 * locally instead of forwarding them, unwrapping and cleaning exceptions
 * thrown by the real call, and tracking invalidation/activation state - and
 * leaves the actual per-proxy dispatch to {@link #invokeMethod}, which
 * concrete subclasses implement.
 */
public abstract class CovalentInvocationHandler implements InvocationHandler {

	/**
	 * The methods of {@link Object} a dynamic proxy hands to its handler,
	 * dispatched locally rather than forwarded.
	 */
	private static final List<Method> objectMethods = new ArrayList<>();

	private volatile boolean invalidated;

	private volatile boolean activated = false;

	static {
		createObjectMethodsMapping();
	}

	/** Creates a new handler. */
	public CovalentInvocationHandler() {
	}

	private static void createObjectMethodsMapping() {
		try {
			objectMethods.add(Object.class.getMethod("hashCode"));
			objectMethods.add(Object.class.getMethod("equals", Object.class));
			objectMethods.add(Object.class.getMethod("toString"));
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
		try {
			if (objectMethods.contains(method)) {
				return this.invokeObjectMethods(proxy, method, args);
			}
			return this.invokeMethod(proxy, method, args);
		} catch (Throwable t) {
			Throwable throwable = t;
			if (t instanceof InvocationTargetException) {
				Throwable cause = ((InvocationTargetException) t).getCause();
				throwable = cause != null ? cause : t;
			}
			throw StackTraceCleaner.cleanIfEnabled(throwable);
		}
	}

	/**
	 * Dispatches a proxy method call that is not one of {@link Object}'s own
	 * methods (those are handled by {@link #invokeObjectMethods}). Concrete
	 * subclasses implement the actual per-proxy-kind forwarding behavior
	 * here.
	 *
	 * @param proxy  the proxy instance the method was called on
	 * @param method the method being invoked
	 * @param args   the call's arguments
	 * @return the value to return from the proxy call
	 * @throws Throwable whatever the real call throws, or a handler-specific
	 *                    failure
	 */
	public abstract Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable;

	/**
	 * Handles a call to one of the {@link Object} methods a dynamic proxy hands
	 * to its handler, locally instead of forwarding it: a proxy is equal only to
	 * itself, its hash code is this handler's identity hash code, stable for the
	 * proxy's whole life, and {@code toString} produces a short diagnostic label.
	 *
	 * @param proxy  the proxy instance the method was called on (unused)
	 * @param method the {@link Object} method being invoked
	 * @param args   the call's arguments
	 * @return the value to return from the proxy call
	 */
	public Object invokeObjectMethods(@SuppressWarnings("unused") final Object proxy, final Method method,
			final Object[] args) {
		if (method.getName().equals("hashCode")) {
			return System.identityHashCode(this);
		} else if (method.getName().equals("equals")) {
			Object arg = args[0];
			return arg != null && Proxy.isProxyClass(arg.getClass()) && Proxy.getInvocationHandler(arg) == this;
		}
		return "Proxy[" + this.getClass().getSimpleName() + "]" + System.identityHashCode(this);
	}

	/**
	 * Throws if this handler has been {@link #invalidate() invalidated}.
	 * Subclasses call this at the start of {@link #invokeMethod} to reject
	 * calls on a proxy that no longer has a live backing (e.g. a
	 * used-services proxy whose provider was removed).
	 *
	 * @return {@code true}, always, when this method returns normally -
	 *         convenient for use in an assertion
	 * @throws IllegalStateException if this handler is invalidated
	 */
	protected boolean checkInvalidation() {
		if (this.invalidated) {
			throw new IllegalStateException("Proxy[" + this.getClass().getSimpleName() + "] is invalidated");
		}
		return true;
	}

	/**
	 * True if {@link #invalidate()} has been called on this handler.
	 *
	 * @return {@code true} if invalidated
	 */
	public boolean isInvalidated() {
		return this.invalidated;
	}

	/**
	 * Marks this handler as invalidated: subsequent calls checked with
	 * {@link #checkInvalidation()} will be rejected. Irreversible.
	 */
	public void invalidate() {
		this.invalidated = true;
	}

	/** Marks this handler's proxy as activated. */
	public void activate() {
		this.activated = true;
	}

	/** Marks this handler's proxy as passivated. */
	public void passivate() {
		this.activated = false;
	}

	/**
	 * True if this handler's proxy is currently activated.
	 *
	 * @return {@code true} if activated
	 */
	public boolean isActivated() {
		return this.activated;
	}

	/**
	 * True if this handler's proxy is currently passivated.
	 *
	 * @return {@code true} if passivated
	 */
	public boolean isPassivated() {
		return !this.activated;
	}

}
