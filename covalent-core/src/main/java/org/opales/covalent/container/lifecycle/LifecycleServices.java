/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

import java.util.List;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;

/**
 * Drives deployment, instantiation, initialization, activation, passivation,
 * removal, and undeployment of managed components.
 *
 * Operations and state reads are serialized per container, including state
 * validation. Concurrent callers wait and then observe the preceding operation's
 * result. Nested calls from the same thread are allowed. Application lifecycle
 * methods and synchronous event consumers must not wait for another thread that
 * needs these services on the same container.
 */
@Services
public interface LifecycleServices {

	/**
	 * Reports whether a component requires explicit recovery after a failed
	 * transition.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when a component requires explicit recovery after
	 *         a failed transition
	 */
	boolean isFailed(Class<?> componentType);

	/**
	 * Cleans a failed application composite and returns its whole tree to deployed.
	 * Does not instantiate or start it. A child argument resolves to its composite root.
	 * If cleanup cannot complete, the whole tree remains failed.
	 *
	 * @param componentType component type to clean, or one of its composite
	 *                      children
	 */
	void resetFailedComponent(Class<?> componentType);

	/**
	 * Deploys several component implementations and registers their metamodels.
	 *
	 * @param implementations implementation classes to deploy
	 * @return metamodels produced for the deployed components
	 */
	List<ComponentMetamodel> deployComponents(final Class<?>... implementations);

	/**
	 * Deploys one component implementation and registers its metamodel.
	 *
	 * @param implementation implementation class to deploy
	 * @return metamodel produced for the deployed component
	 */
	ComponentMetamodel deployComponent(final Class<?> implementation);

	/**
	 * Reports whether a component type has been deployed.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is deployed
	 */
	boolean isDeployed(final Class<?> componentType);

	/**
	 * Instantiates all deployed components and injects their dependencies.
	 */
	void instantiate() throws RuntimeException;

	/**
	 * Instantiates the deployed component type and injects its dependencies.
	 * 
	 * @param componentType component type to instantiate
	 */
	void instantiateComponent(final Class<?> componentType);

	/**
	 * Reports whether a component type has been instantiated.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is instantiated
	 */
	boolean isInstantiated(final Class<?> componentType);

	/**
	 * Initializes all deployed and instantiated components.
	 */
	void initialize() throws RuntimeException;

	/**
	 * Initializes component. Should have been previously deployed. The component is
	 * automatically instantiated if this has not been already done.
	 * 
	 * @param componentType the component type
	 */
	void initializeComponent(final Class<?> componentType) throws RuntimeException;

	/**
	 * Reports whether a component type has been initialized.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is initialized
	 */
	boolean isInitialized(final Class<?> componentType);

	/**
	 * Reads the component type associated with an implementation class.
	 * 
	 * @param implementation implementation class to resolve
	 * @return component type associated with the implementation
	 */
	public Class<?> readComponentType(Class<?> implementation);

	/**
	 * Activates all initialized components.
	 */
	void activate() throws RuntimeException;

	/**
	 * Activates the component. Must have been previously deployed. The component is
	 * automatically instantiated and initialized if this has not been already done.
	 * 
	 * @param componentType the component type
	 */
	void activateComponent(final Class<?> componentType) throws RuntimeException;

	/**
	 * Reports whether a component type has been activated.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is activated
	 */
	boolean isActivated(final Class<?> componentType);

	/**
	 * Passivates all instantiated components.
	 */
	void passivate() throws RuntimeException;

	/**
	 * Passivates the component. Must have been previously deployed.
	 * 
	 * @param componentType the component type
	 */
	void passivateComponent(final Class<?> componentType) throws RuntimeException;

	/**
	 * Reports whether a component type has been passivated.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is passivated
	 */
	boolean isPassivated(final Class<?> componentType);

	/**
	 * Removes all instantiated components.
	 */
	void remove();

	/**
	 * Removes the component type from the managed lifecycle.
	 *
	 * @param componentType component type to remove
	 */
	void removeComponent(final Class<?> componentType) throws RuntimeException;

	/**
	 * Undeploys all components and removes their lifecycle descriptors.
	 */
	void undeploy();

	/**
	 * Reports whether a component type has been removed.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is removed
	 */
	boolean isRemoved(final Class<?> componentType);

	/**
	 * Undeploys the component represented by the implementation class.
	 *
	 * @param implementation implementation class whose component type is undeployed
	 */
	void undeployComponent(final Class<?> implementation);

	/**
	 * Reports whether a component type has been undeployed.
	 *
	 * @param componentType component type to inspect
	 * @return {@code true} when the component type is undeployed
	 */
	boolean isUndeployed(final Class<?> componentType);

	/**
	 * Reads the lifecycle state currently associated with a component type.
	 *
	 * @param <T>           component type marker
	 * @param componentType component type to inspect
	 * @return current component lifecycle state
	 */
	<T> ComponentState readComponentState(final Class<T> componentType);

	/**
	 * Defines how startup lifecycle transitions are ordered across components.
	 */
	public enum StartMode {
		/**
		 * Runs each startup transition for all components before moving to the next
		 * transition.
		 */
		horizontal,
		/**
		 * Starts each component through instantiation, initialization, and activation
		 * before moving to the next component.
		 */
		vertical;
	}

}
