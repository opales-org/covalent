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
import org.opales.covalent.container.rr.services.ServicesManagerServices;

/**
 * Internal item backing the services half of {@link AutoWiringManagerImpl}:
 * reads a component's declared provided/used service interfaces from the
 * metamodel and drives their creation/deletion through
 * {@link ServicesManagerServices}, injecting resolved used-services
 * instances into the component's {@code @UsedServices} fields via the
 * shared {@link FieldInjectionImpl}.
 */
@Item
class ServicesAutoWiringImpl {
	private static final Logger log = Logger.getLogger(ServicesAutoWiringImpl.class.getName());

	private ServicesManagerServices useServicesManagerServices;

	private MetamodelPortReadServices useMetamodelPortReadServices;

	private PortRegistryServices usePortRegistryServices;

	private FieldInjectionImpl fieldInjection;

	/**
	 * Sets the internal service used to create and delete provided/used
	 * services ports.
	 *
	 * @param servicesManagerServices the services manager service
	 */
	void setServicesManagerServices(final ServicesManagerServices servicesManagerServices) {
		this.useServicesManagerServices = servicesManagerServices;
	}

	/**
	 * Sets the internal service used to read a component's declared
	 * provided/used services from the metamodel.
	 *
	 * @param metamodelPortReadServices the metamodel port read service
	 */
	void setMetamodelPortReadServices(final MetamodelPortReadServices metamodelPortReadServices) {
		this.useMetamodelPortReadServices = metamodelPortReadServices;
	}

	/**
	 * Sets the internal service used to look up the fields to inject
	 * resolved used services into.
	 *
	 * @param portRegistryServices the port registry service
	 */
	void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.usePortRegistryServices = portRegistryServices;
	}

	/**
	 * Sets the shared field injector used to populate {@code @UsedServices}
	 * fields.
	 *
	 * @param fieldInjection the field injection item
	 */
	void setFieldInjection(final FieldInjectionImpl fieldInjection) {
		this.fieldInjection = fieldInjection;
	}

	/**
	 * Creates every provided services port the given component type
	 * declares that is not already provided by another component.
	 *
	 * @param componentType the component type whose provided services are
	 *                      being created
	 */
	void createProvidedServices(final Class<?> componentType) {
		Set<Class<?>> allProvidedServices = this.useMetamodelPortReadServices.getProvidedServices(componentType);
		for (Class<?> servicesInterface : allProvidedServices) {
			if (!this.useServicesManagerServices.isServicesProvided(servicesInterface)) {
				log.fine("Automatic creation of provided services " + servicesInterface.getSimpleName());
				this.useServicesManagerServices.createProvidedServices(servicesInterface);
			}
		}
	}

	/**
	 * Deletes every provided services port the given component type
	 * declares that is currently provided.
	 *
	 * @param componentType the component type whose provided services are
	 *                      being deleted
	 */
	void deleteProvidedServices(final Class<?> componentType) {
		Set<Class<?>> allProvidedServices = this.useMetamodelPortReadServices.getProvidedServices(componentType);
		for (Class<?> servicesInterface : allProvidedServices) {
			if (this.useServicesManagerServices.isServicesProvided(servicesInterface)) {
				log.fine("Automatic deletion of provided services " + servicesInterface.getSimpleName());
				this.useServicesManagerServices.deleteProvidedServices(servicesInterface);
			}
		}
	}

	/**
	 * Connects the given component type to every services interface it
	 * declares as {@code @UsedServices}, injecting each resolved services
	 * instance into the matching field.
	 *
	 * @param <SERVICES>    the used services interface type
	 * @param componentType the component type whose used services are being
	 *                      created
	 */
	<SERVICES> void createUsedServices(final Class<?> componentType) {
		Set<Class<?>> servicesInterfaces = this.useMetamodelPortReadServices.getUsedServices(componentType);
		for (Class<?> aServicesInterface : servicesInterfaces) {
			@SuppressWarnings("unchecked")
			Class<SERVICES> servicesInterface = (Class<SERVICES>) aServicesInterface;
			this.connectUsedServices(componentType, servicesInterface);
		}
	}

	/**
	 * Reconnects one used services interface for the given component type
	 * and injects it into the user component's matching field(s).
	 * <p>
	 * Uses {@code reconnectUsedServices}, not {@code createUsedServices}:
	 * this runs at every {@code preActivate} in horizontal mode (see
	 * {@link org.opales.covalent.container.lifecycle.impl.HorizontalLifecycleTransitions}),
	 * so a component that
	 * alternates activate/passivate without being removed legitimately asks
	 * again for a used services connection that is still live (used
	 * services are never torn down on passivation, only on removal). That
	 * must not be reported as a duplicate usage the way an actual duplicate
	 * manual use from application code still is.
	 *
	 * @param <SERVICES>                 the used services interface type
	 * @param servicesUserComponentType  the component type using the
	 *                                   services
	 * @param servicesInterface          the services interface being used
	 */
	private <SERVICES> void connectUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface) {
		log.fine("Automatic connection of used services " + servicesInterface.getSimpleName());
		// reconnectUsedServices(), not createUsedServices(): this runs at
		// every preActivate in horizontal mode (see
		// HorizontalLifecycleTransitions), so a component that alternates
		// activate/passivate without being removed legitimately asks again
		// for a used services connection that is still live (used services
		// are never torn down on passivation, only on removal - see
		// LifecycleManagerImpl#passivateComponent's javadoc). That must not
		// be reported as a duplicate usage the way an actual duplicate
		// manual use from application code still is.
		SERVICES usedServices = this.useServicesManagerServices.reconnectUsedServices(servicesUserComponentType,
				servicesInterface);
		this.fieldInjection.injectIfNull(
				this.usePortRegistryServices.getUsedServicesItemAndField(servicesUserComponentType,
						servicesInterface),
				usedServices, "usedServices");
	}

	/**
	 * Deletes every used services connection the given component type
	 * currently holds.
	 *
	 * @param <SERVICES>    the used services interface type
	 * @param componentType the component type whose used services are being
	 *                      deleted
	 */
	<SERVICES> void deleteUsedServices(final Class<?> componentType) {
		Set<Class<?>> servicesInterfaces = this.useMetamodelPortReadServices.getUsedServices(componentType);
		for (Class<?> aServicesInterface : servicesInterfaces) {
			@SuppressWarnings("unchecked")
			Class<SERVICES> servicesInterface = (Class<SERVICES>) aServicesInterface;
			if (this.useServicesManagerServices.isServicesUsed(componentType, servicesInterface)) {
				this.useServicesManagerServices.deleteUsedServices(componentType, servicesInterface);
			}
		}
	}

}
