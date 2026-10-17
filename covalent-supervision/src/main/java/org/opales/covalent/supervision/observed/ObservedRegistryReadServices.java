/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.util.Map;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Mirror of {@link org.opales.covalent.container.registry.RegistryReadServices},
 * rooted in the <em>observed</em> container instead of the supervision container
 * that consumes it. A managed component's own container already owns the
 * real {@code RegistryReadServices} name for its own self-observation (every
 * {@code CovalentContainer} produces/provides these ports about itself from
 * its bootstrap); reusing that same interface inside the supervision container
 * to describe a <em>different</em>, observed container would collide with that
 * native self-observation. This distinct type is how
 * {@code ObservedContainerBridge} republishes the observed container's real
 * data without that collision - see its own class javadoc.
 */
@Services
public interface ObservedRegistryReadServices {

	/**
	 * Returns the instance associated with a component type.
	 *
	 * @param <CT>          component type marker
	 * @param <CI>          component instance type
	 * @param componentType component type to read
	 * @return registered component instance
	 */
	<CT, CI extends CT> CI getComponentInstance(Class<CT> componentType);

	/**
	 * Returns all registered component instances.
	 *
	 * @return registered instances keyed by component type
	 */
	Map<Class<?>, Object> getInstances();

	/**
	 * Reports whether a component type has a registered instance.
	 *
	 * @param <T>           component type marker
	 * @param componentType component type to inspect
	 * @return {@code true} when an instance is registered
	 */
	<T> boolean isRegistered(Class<T> componentType);

}
