/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters.impl;

import java.util.Set;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.rr.common.RRLifecycleManagerImpl;
import org.opales.covalent.container.rr.parameters.ParametersLifecycleServices;

/**
 * Parameters flavor-specific lifecycle manager: implements
 * {@link ParametersLifecycleServices} by iterating, for a given component
 * type, over every parameters interface it offers (as declared in the
 * metamodel) and driving each one through the requested transition on the
 * collaborating {@link ParametersManagerImpl}, skipping any interface
 * already in the target state. This is the parameters flavor-specific
 * counterpart of
 * {@code org.opales.covalent.container.rr.services.impl.ServicesLifecycleManagerImpl}.
 */
@Item
public class ParametersLifecycleManagerImpl extends RRLifecycleManagerImpl implements ParametersLifecycleServices {

	private static Logger log = Logger.getLogger(ParametersLifecycleManagerImpl.class.getName());

	/** Creates a new parameters lifecycle manager. */
	public ParametersLifecycleManagerImpl() {
	}

	/** The collaborating manager transitions are actually applied to. */
	private ParametersManagerImpl parametersManagerImpl;

	/**
	 * Sets the collaborating manager this lifecycle manager drives.
	 *
	 * @param parametersManagerImpl the parameters manager
	 */
	public void setParametersManagerImpl(final ParametersManagerImpl parametersManagerImpl) {
		this.parametersManagerImpl = parametersManagerImpl;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <PARAMETERS> void activateOfferedParameters(final Class<?> componentType) {
		Set<Class<?>> allOfferedParameters = this.MetamodelPortReadServices.getOfferedParameters(componentType);
		for (Class<?> parameters : allOfferedParameters) {
			@SuppressWarnings("unchecked")
			Class<PARAMETERS> parametersInterface = (Class<PARAMETERS>) parameters;
			if (!this.parametersManagerImpl.isActivated(parametersInterface)) {
				log.fine("Automatic activation of offered parameters " + parametersInterface.getSimpleName());
				this.parametersManagerImpl.activateParameters(parametersInterface);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <PARAMETERS> void passivateOfferedParameters(final Class<?> componentType) {
		Set<Class<?>> allOfferedParameters = this.MetamodelPortReadServices.getOfferedParameters(componentType);
		for (Class<?> parameters : allOfferedParameters) {
			@SuppressWarnings("unchecked")
			Class<PARAMETERS> parametersInterface = (Class<PARAMETERS>) parameters;
			if (this.parametersManagerImpl.isParametersOffered(parametersInterface)
					&& this.parametersManagerImpl.isActivated(parametersInterface)) {
				log.fine("Automatic passivation of offered parameters " + parametersInterface.getSimpleName());
				this.parametersManagerImpl.passivateParameters(parametersInterface);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <PARAMETERS> void publishOfferedParameters(final Class<?> componentType) {
		Set<Class<?>> allOfferedParameters = this.MetamodelPortReadServices.getOfferedParameters(componentType);
		for (Class<?> parameters : allOfferedParameters) {
			@SuppressWarnings("unchecked")
			Class<PARAMETERS> parametersInterface = (Class<PARAMETERS>) parameters;
			if (!this.namingServices.isParametersBound(parametersInterface)) {
				PARAMETERS parametersOfferor = this.parametersManagerImpl.getParametersOfferor(parametersInterface);
				log.fine("Publication of offered parameters " + parametersInterface.getSimpleName());
				this.namingServices.bindParameters(parametersInterface, parametersOfferor);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <PARAMETERS> void unpublishOfferedParameters(final Class<?> componentType) {
		Set<Class<?>> allOfferedParameters = this.MetamodelPortReadServices.getOfferedParameters(componentType);
		for (Class<?> parameters : allOfferedParameters) {
			@SuppressWarnings("unchecked")
			Class<PARAMETERS> parametersInterface = (Class<PARAMETERS>) parameters;
			if (this.namingServices.isParametersBound(parametersInterface)) {
				log.fine("Unpublication of offered parameters " + parametersInterface.getSimpleName());
				this.namingServices.unbindParameters(parametersInterface);
			}
		}
	}

}
