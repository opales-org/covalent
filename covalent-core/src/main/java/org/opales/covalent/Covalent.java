/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Static entry point for Covalent containers.
 * <p>
 * A container is either managed, created by {@link #managedContainer(String)}
 * ({@link #main} included) and known by its name until it is disposed, or
 * unmanaged, created by {@link #container()}. The lifecycle of the managed
 * containers is observable through {@link ManagedContainerEvents}.
 */
public final class Covalent {

	private static final String MAIN_CONTAINER_NAME = "main";

	private static final Logger log = Logger.getLogger(Covalent.class.getName());

	private static final Map<String, CovalentContainer> managedContainers = new LinkedHashMap<>();

	private static final CopyOnWriteArrayList<ManagedContainerEvents> observers = new CopyOnWriteArrayList<>();

	/**
	 * Main container for applications that do not need explicit container
	 * isolation.
	 */
	public static final CovalentContainer main;

	static {
		main = createManagedContainer(MAIN_CONTAINER_NAME);
		managedContainers.put(MAIN_CONTAINER_NAME, main);
	}

	private Covalent() {
	}

	/**
	 * Creates a new unmanaged container.
	 *
	 * @return a new isolated container
	 */
	public static CovalentContainer container() {
		return new CovalentContainerImpl();
	}

	/**
	 * Returns the named container, creating it when it does not exist yet:
	 * {@link ManagedContainerEvents#onContainerCreated(String)} is then
	 * notified.
	 *
	 * @param name container name
	 * @return the named container
	 */
	public static CovalentContainer managedContainer(final String name) {
		RuntimeCheck.notNull(name, "name");
		CovalentContainer container;
		synchronized (managedContainers) {
			container = managedContainers.get(name);
			if (container != null) {
				return container;
			}
			container = createManagedContainer(name);
			managedContainers.put(name, container);
		}
		dispatch(observer -> observer.onContainerCreated(name));
		return container;
	}

	/**
	 * Returns every managed container known by Covalent.
	 *
	 * @return immutable managed containers snapshot
	 */
	public static Map<String, CovalentContainer> getManagedContainers() {
		synchronized (managedContainers) {
			return Collections.unmodifiableMap(new LinkedHashMap<>(managedContainers));
		}
	}

	/**
	 * Returns the names of every managed container known by Covalent.
	 *
	 * @return immutable managed container names snapshot
	 */
	public static Set<String> getManagedContainerNames() {
		synchronized (managedContainers) {
			return Collections.unmodifiableSet(new LinkedHashSet<>(managedContainers.keySet()));
		}
	}

	/**
	 * Returns a managed container by name without creating it.
	 *
	 * @param name container name
	 * @return the managed container, or {@code null} when it does not exist
	 */
	public static CovalentContainer getManagedContainer(final String name) {
		synchronized (managedContainers) {
			return managedContainers.get(name);
		}
	}

	/**
	 * Disposes a managed container (see {@link CovalentContainer#dispose()}):
	 * stopped first if still started, it then leaves the managed containers
	 * and {@link ManagedContainerEvents#onContainerDisposed(String)} is
	 * notified. Its own {@code dispose()} has exactly the same effect.
	 * {@link #main} cannot be disposed this way.
	 *
	 * @param name container name
	 * @return {@code true} if a managed container was disposed, {@code false}
	 *         if none has this name or it is {@link #main}
	 */
	public static boolean disposeManagedContainer(final String name) {
		if (MAIN_CONTAINER_NAME.equals(name)) {
			return false;
		}
		CovalentContainer container = getManagedContainer(name);
		if (container == null) {
			return false;
		}
		// Outside Covalent's lock: dispose() takes the container's lifecycle lock
		// and notifies the observers.
		container.dispose();
		return true;
	}

	/**
	 * Disposes every managed container except {@link #main} (see
	 * {@link #disposeManagedContainer(String)}).
	 */
	public static void disposeManagedContainers() {
		for (String name : getManagedContainerNames()) {
			disposeManagedContainer(name);
		}
	}

	/**
	 * Subscribes an observer to the lifecycle of the managed containers. An
	 * observer already subscribed is not added again.
	 *
	 * @param observer the observer
	 */
	public static void subscribe(final ManagedContainerEvents observer) {
		RuntimeCheck.notNull(observer, "observer");
		observers.addIfAbsent(observer);
	}

	/**
	 * Unsubscribes an observer; does nothing if it is not subscribed.
	 *
	 * @param observer the observer
	 */
	public static void unsubscribe(final ManagedContainerEvents observer) {
		observers.remove(observer);
	}

	/**
	 * Delivers a notification to every observer, each one isolated.
	 *
	 * @param notification the notification to deliver
	 */
	static void dispatch(final Consumer<ManagedContainerEvents> notification) {
		for (ManagedContainerEvents observer : observers) {
			try {
				notification.accept(observer);
			} catch (RuntimeException failure) {
				log.log(Level.SEVERE, "A managed container observer failed.", failure);
			}
		}
	}

	/**
	 * Removes a disposed managed container and notifies it, called by its
	 * relay's removal.
	 *
	 * @param name the container's name
	 */
	static void containerDisposed(final String name) {
		synchronized (managedContainers) {
			managedContainers.remove(name);
		}
		dispatch(observer -> observer.onContainerDisposed(name));
	}

	private static CovalentContainer createManagedContainer(final String name) {
		CovalentContainerImpl container = new CovalentContainerImpl(name);
		container.rcs().registerComponent(ManagedContainerRelay.class, new ManagedContainerRelay(name));
		return container;
	}
}
