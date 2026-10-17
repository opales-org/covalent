/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import org.opales.covalent.container.invocation.CovalentInvocationHandler;

/**
 * Common part of the client-side and provider-side request/response
 * invocation handlers: the instrumentation hooks and the forwarding of a call
 * to its target, with the same measurement and the same handling of failures on
 * both sides. See {@link ClientRRInvocationHandler} and
 * {@link ServerRRInvocationHandler}.
 */
public abstract class RRInvocationHandler extends CovalentInvocationHandler {

	/** Creates a handler with no instrumentation supplier. */
	protected RRInvocationHandler() {
		// Nothing to initialize.
	}

	/** Supplies whether calls to this handler should be instrumented. */
	protected BooleanSupplier instrumentationSupplier;

	/**
	 * Sets the supplier consulted before each call to decide whether
	 * instrumentation callbacks ({@link #notifyCallStarted}/
	 * {@link #notifyCallFinished}) should fire.
	 *
	 * @param instrumentationSupplier supplies whether instrumentation is
	 *                                currently enabled
	 */
	public void setInstrumentationSupplier(final BooleanSupplier instrumentationSupplier) {
		this.instrumentationSupplier = instrumentationSupplier;
	}

	/**
	 * Forwards a call to {@code target}, notifying the instrumentation hooks
	 * when instrumentation is enabled. A failure thrown by the target is
	 * rethrown as is; a failure of the reflective call itself (access denied,
	 * illegal argument, security) is reported as a {@link RuntimeException}
	 * naming the call, see {@link #describeCall(Method)}.
	 *
	 * @param target the object the call is forwarded to
	 * @param method the method being invoked
	 * @param args   the call's arguments
	 * @return whatever the target returns
	 * @throws Throwable whatever the target throws
	 */
	protected final Object invokeTarget(final Object target, final Method method, final Object[] args)
			throws Throwable {
		boolean instrumented = this.isInstrumentationEnabled();
		if (instrumented) {
			this.notifyCallStarted(method, args);
		}

		long startedAt = System.nanoTime();
		Throwable failure = null;
		Object returnValue = null;
		try {
			returnValue = method.invoke(target, args);
			return returnValue;
		} catch (InvocationTargetException e) {
			failure = e.getCause();
			throw failure;
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException e) {
			RuntimeException invocationFailure = new RuntimeException(
					"Exception while invoking " + this.describeCall(method), e);
			failure = invocationFailure;
			throw invocationFailure;
		} finally {
			if (instrumented) {
				this.notifyCallFinished(method, args, System.nanoTime() - startedAt, failure, returnValue);
			}
		}
	}

	/**
	 * Names the call being forwarded, for the message of a failure of the
	 * reflective call itself.
	 *
	 * @param method the method being invoked
	 * @return a short description of the call
	 */
	protected abstract String describeCall(Method method);

	private boolean isInstrumentationEnabled() {
		return this.instrumentationSupplier != null && this.instrumentationSupplier.getAsBoolean();
	}

	/**
	 * Instrumentation hook invoked just before a call is forwarded to its
	 * target, when instrumentation is enabled. No-op by default; subclasses
	 * override to observe calls (e.g. for the supervision UI).
	 *
	 * @param method the method being invoked
	 * @param args   the call's arguments
	 */
	protected void notifyCallStarted(final Method method, final Object[] args) {
	}

	/**
	 * Instrumentation hook invoked just after a forwarded call completes
	 * (successfully or not), when instrumentation is enabled. No-op by
	 * default; subclasses override to observe calls.
	 *
	 * @param method        the method that was invoked
	 * @param args          the call's arguments
	 * @param durationNanos how long the call took, in nanoseconds
	 * @param failure       the exception the call threw, or {@code null} if
	 *                      it succeeded
	 * @param returnValue   the value the call returned, {@code null} when it
	 *                      failed or returns nothing
	 */
	protected void notifyCallFinished(final Method method, final Object[] args, final long durationNanos,
			final Throwable failure, final Object returnValue) {
	}
}
