/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services.impl;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.rr.common.ClientRRInvocationHandler;
import org.opales.covalent.container.rr.services.ServicesManagerEvents;
import org.opales.covalent.container.workers.impl.TaskSubmitter;

/**
 * Client-side invocation handler for the services flavor of the
 * request/response ({@code RR}) infrastructure: backs the proxy handed to a
 * used-services consumer, forwarding every call to the current
 * provider-side implementation and reporting instrumented calls through
 * {@link ServicesManagerEvents}. Created by
 * {@link ServicesProxyFactory#newUsedServicesProxy}.
 *
 * @param <SERVICES> the services interface this handler proxies calls for
 */
public class UsedServicesInvocationHandler<SERVICES> extends ClientRRInvocationHandler<SERVICES> {
	/** The services interface this handler proxies calls for. */
	private final Class<SERVICES> servicesInterface;

	/** The client component type using {@link #servicesInterface}. */
	private final Class<?> servicesUser;

	/** Supplies the events port instrumented calls are reported through. */
	private final Supplier<ServicesManagerEvents> servicesManagerEventsSupplier;

	/** Whether {@link #servicesInterface} is {@link AsyncServices}, whose calls tell who submits a task. */
	private final boolean asyncServices;

	/**
	 * Creates a new handler forwarding to the given provider-side target.
	 *
	 * @param servicesInterface             the services interface this
	 *                                       handler proxies calls for
	 * @param usedServices                   the initial provider-side target
	 * @param servicesUser                   the client component type using
	 *                                        {@code servicesInterface}
	 * @param servicesManagerEventsSupplier supplies the events port
	 *                                        instrumented calls are reported
	 *                                        through
	 */
	public UsedServicesInvocationHandler(final Class<SERVICES> servicesInterface, final SERVICES usedServices,
			final Class<?> servicesUser, final Supplier<ServicesManagerEvents> servicesManagerEventsSupplier) {
		super(usedServices);
		this.servicesInterface = servicesInterface;
		this.servicesUser = servicesUser;
		this.servicesManagerEventsSupplier = servicesManagerEventsSupplier;
		this.asyncServices = servicesInterface == AsyncServices.class;
	}

	/**
	 * Forwards the call; an {@link AsyncServices} call also tells the async manager which
	 * component submits the task - see {@link TaskSubmitter}.
	 */
	@Override
	public Object invokeMethod(final Object proxy, final Method method, final Object[] args) throws Throwable {
		if (!this.asyncServices) {
			return super.invokeMethod(proxy, method, args);
		}
		Class<?> previous = TaskSubmitter.enter(this.servicesUser);
		try {
			return super.invokeMethod(proxy, method, args);
		} finally {
			TaskSubmitter.exit(previous);
		}
	}

	@Override
	protected void notifyCallStarted(final Method method, final Object[] args) {
		this.servicesManagerEventsSupplier.get().onUsedServiceCallStarted(this.servicesInterface, method,
				this.servicesUser, args);
	}

	@Override
	protected void notifyCallFinished(final Method method, final Object[] args, final long durationNanos,
			final Throwable failure, final Object returnValue) {
		this.servicesManagerEventsSupplier.get().onUsedServiceCallFinished(this.servicesInterface, method,
				this.servicesUser, durationNanos, failure, returnValue, args);
	}
}
