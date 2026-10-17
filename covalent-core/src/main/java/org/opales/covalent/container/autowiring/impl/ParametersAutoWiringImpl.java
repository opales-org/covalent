/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.autowiring.impl;

import java.util.Set;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;

/**
 * Internal item backing the parameters half of
 * {@link AutoWiringManagerImpl}: reads a component's declared offered/
 * accessed parameter interfaces from the metamodel and drives their
 * creation/deletion through {@link ParametersManagerServices}, injecting
 * resolved accessed-parameters instances into the component's
 * {@code @AccessedParameters} fields via the shared
 * {@link FieldInjectionImpl}.
 */
@Item
class ParametersAutoWiringImpl {
	private static final Logger log = Logger.getLogger(ParametersAutoWiringImpl.class.getName());

	private ParametersManagerServices useParametersManagerServices;

	private MetamodelPortReadServices useMetamodelPortReadServices;

	private PortRegistryServices usePortRegistryServices;

	private FieldInjectionImpl fieldInjection;

	/**
	 * Sets the internal service used to create and delete offered/accessed
	 * parameters ports.
	 *
	 * @param parametersManagerServices the parameters manager service
	 */
	void setParametersManagerServices(final ParametersManagerServices parametersManagerServices) {
		this.useParametersManagerServices = parametersManagerServices;
	}

	/**
	 * Sets the internal service used to read a component's declared
	 * offered/accessed parameters from the metamodel.
	 *
	 * @param metamodelPortReadServices the metamodel port read service
	 */
	void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.useMetamodelPortReadServices = metamodelPortReadServices;
	}

	/**
	 * Sets the internal service used to look up the fields to inject
	 * resolved accessed parameters into.
	 *
	 * @param portRegistryServices the port registry service
	 */
	void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.usePortRegistryServices = portRegistryServices;
	}

	/**
	 * Sets the shared field injector used to populate
	 * {@code @AccessedParameters} fields.
	 *
	 * @param fieldInjection the field injection item
	 */
	void setFieldInjection(final FieldInjectionImpl fieldInjection) {
		this.fieldInjection = fieldInjection;
	}

	/**
	 * Creates every offered parameters port the given component type
	 * declares that is not already offered by another component.
	 *
	 * @param componentType the component type whose offered parameters are
	 *                      being created
	 */
	void createOfferedParameters(final Class<?> componentType) {
		Set<Class<?>> allOfferedParameters = this.useMetamodelPortReadServices.getOfferedParameters(componentType);
		for (Class<?> parametersInterface : allOfferedParameters) {
			if (!this.useParametersManagerServices.isParametersOffered(parametersInterface)) {
				log.fine("Automatic creation of offered parameters " + parametersInterface.getSimpleName());
				this.useParametersManagerServices.createOfferedParameters(parametersInterface);
			}
		}
	}

	/**
	 * Deletes every offered parameters port the given component type
	 * declares that is currently offered.
	 *
	 * @param componentType the component type whose offered parameters are
	 *                      being deleted
	 */
	void deleteOfferedParameters(final Class<?> componentType) {
		Set<Class<?>> allOfferedParameters = this.useMetamodelPortReadServices.getOfferedParameters(componentType);
		for (Class<?> parametersInterface : allOfferedParameters) {
			if (this.useParametersManagerServices.isParametersOffered(parametersInterface)) {
				log.fine("Automatic deletion of offered parameters " + parametersInterface.getSimpleName());
				this.useParametersManagerServices.deleteOfferedParameters(parametersInterface);
			}
		}
	}

	/**
	 * Connects the given component type to every parameters interface it
	 * declares as {@code @AccessedParameters}, injecting each resolved
	 * accessor into the matching field.
	 *
	 * @param <PARAMETERS>  the accessed parameters interface type
	 * @param componentType the component type whose accessed parameters are
	 *                      being created
	 */
	<PARAMETERS> void createAccessedParameters(final Class<?> componentType) {
		Set<Class<?>> parametersInterfaces = this.useMetamodelPortReadServices.getAccessedParameters(componentType);
		for (Class<?> aParametersInterface : parametersInterfaces) {
			@SuppressWarnings("unchecked")
			Class<PARAMETERS> parametersInterface = (Class<PARAMETERS>) aParametersInterface;
			this.connectAccessedParameters(componentType, parametersInterface);
		}
	}

	/**
	 * Creates an accessed parameters instance for one interface and injects
	 * it into the accessor component's matching field(s).
	 *
	 * @param <PARAMETERS>                    the accessed parameters
	 *                                        interface type
	 * @param parametersAccessorComponentType the component type accessing
	 *                                        the parameters
	 * @param parametersInterface             the parameters interface being
	 *                                        accessed
	 */
	private <PARAMETERS> void connectAccessedParameters(final Class<?> parametersAccessorComponentType,
			final Class<PARAMETERS> parametersInterface) {
		log.fine("Automatic connection of accessed parameters " + parametersInterface.getSimpleName());
		PARAMETERS accessedParameters = this.useParametersManagerServices
				.createAccessedParameters(parametersAccessorComponentType, parametersInterface);
		this.fieldInjection.injectIfNull(
				this.usePortRegistryServices.getAccessedParametersItemAndField(parametersAccessorComponentType,
						parametersInterface),
				accessedParameters, "accessedParameters");
	}

	/**
	 * Deletes every accessed parameters connection the given component type
	 * currently holds.
	 *
	 * @param <PARAMETERS>  the accessed parameters interface type
	 * @param componentType the component type whose accessed parameters are
	 *                      being deleted
	 */
	<PARAMETERS> void deleteAccessedParameters(final Class<?> componentType) {
		Set<Class<?>> parametersInterfaces = this.useMetamodelPortReadServices.getAccessedParameters(componentType);
		for (Class<?> aParametersInterface : parametersInterfaces) {
			@SuppressWarnings("unchecked")
			Class<PARAMETERS> parametersInterface = (Class<PARAMETERS>) aParametersInterface;
			if (this.useParametersManagerServices.isParametersAccessed(componentType, parametersInterface)) {
				this.useParametersManagerServices.deleteAccessedParameters(componentType, parametersInterface);
			}
		}
	}

}
