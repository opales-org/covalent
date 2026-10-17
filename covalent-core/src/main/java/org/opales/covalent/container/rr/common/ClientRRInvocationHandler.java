/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.common;

import java.lang.reflect.Method;

/**
 * Client-side request/response invocation handler: backs a proxy handed to
 * a used-services or accessed-parameters consumer, forwarding every method
 * call directly to the current provider-side implementation ({@code RR}).
 * Used by both the services and parameters flavors of the request/response
 * ({@code RR}) infrastructure. See {@link RRManagerImpl#connectClientProxy}
 * for how this handler gets wired to its target, and
 * {@link RRProxyFactory.RRSurrogateProxyInvocationHandler} for the
 * temporary substitute used when no provider is declared yet.
 *
 * @param <RR> the request/response interface (a services or parameters
 *             interface) this handler proxies calls for
 */
public class ClientRRInvocationHandler<RR> extends RRInvocationHandler {

	/** The current provider-side target this handler forwards calls to. */
	private volatile RR clientRR;

	/**
	 * Creates a new handler forwarding to the given provider-side target.
	 *
	 * @param clientRR the initial provider-side target
	 */
	public ClientRRInvocationHandler(final RR clientRR) {
		this.clientRR = clientRR;
	}

	/**
	 * Replaces the provider-side target this handler forwards calls to -
	 * used when a surrogate proxy resolves to the genuine provider.
	 *
	 * @param clientRR the new provider-side target
	 */
	public void replaceClientRR(final RR clientRR) {
		this.clientRR = clientRR;
	}

	@Override
	public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
		this.checkInvalidation();
		return this.invokeTarget(this.clientRR, method, args);
	}

	@Override
	protected String describeCall(final Method method) {
		return method.getName() + " from " + method.getDeclaringClass();
	}

}
