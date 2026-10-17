/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.validation.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.StringJoiner;
import java.util.logging.Logger;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.exceptions.NamingException;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ComponentValidator;
import org.opales.covalent.container.validation.ValidationParameters;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Default implementation of the component model validator: checks
 * structural rules (annotations and naming conventions on component
 * implementations, items, and the services/parameters/events/subscribe
 * interfaces and port fields that connect them) and provides the shared
 * mechanism, keyed off the configured {@link VerificationLevel}, for
 * reporting both structural and dynamic (runtime) rule violations - either
 * silently, by logging, or by throwing.
 */
@Implementation
public class ComponentValidatorImpl implements ComponentValidator, ValidationServices, ValidationParameters {
	private static Logger log = Logger.getLogger(ComponentValidatorImpl.class.getName());

	/** Expected prefix for a produced-events field name. */
	private final static String producePrefix = "produce";

	/** Expected prefix for a used-services field name. */
	private final static String usePrefix = "use";

	/** Expected prefix for an accessed-parameters field name. */
	private final static String accessPrefix = "access";

	/** Expected prefix for a used-subscribe field name. */
	private final static String subscribePrefix = "use";

	/** Required suffix for a services interface name. */
	private final static String servicesSuffix = "Services";

	/** Required suffix for an events interface name. */
	private final static String eventsSuffix = "Events";

	/** Required suffix for a parameters interface name. */
	private final static String parametersSuffix = "Parameters";

	/** Required suffix for an events-subscribe interface name. */
	private final static String subscribeSuffix = "Subscribe";

	/** Verification level applied to structural component model rules. */
	private volatile VerificationLevel structuralRulesVerificationLevel;

	/** Verification level applied to dynamic (runtime) rules. */
	private volatile VerificationLevel dynamicRulesVerificationLevel;

	/** Whether used-services field names are checked against the naming convention. */
	private volatile boolean checkUseServicesFieldNamingConvention = true;

	/** Whether accessed-parameters field names are checked against the naming convention. */
	private volatile boolean checkAccessedParametersFieldNamingConvention = true;

	/** Whether produced-events field names are checked against the naming convention. */
	private volatile boolean checkProduceEventsFieldNamingConvention = true;

	/** Whether used-subscribe field names are checked against the naming convention. */
	private volatile boolean checkUseSubscriberFieldNamingConvention = true;

	/** Whether events interface method names are checked against the naming convention. */
	private volatile boolean checkEventsMethodNamingConvention = true;

	/**
	 * Creates a new validator with the default rule and naming-convention
	 * settings.
	 */
	public ComponentValidatorImpl() {
	}

	@Override
	public void setStructuralRulesVerificationLevel(final VerificationLevel structuralRulesVerificationLevel) {
		this.structuralRulesVerificationLevel = structuralRulesVerificationLevel;
	}

	@Override
	public void setDynamicRulesVerificationLevel(final VerificationLevel dynamicRulesVerificationLevel) {
		this.dynamicRulesVerificationLevel = dynamicRulesVerificationLevel;
	}

	@Override
	public VerificationLevel getStructuralRulesVerificationLevel() {
		return this.structuralRulesVerificationLevel;
	}

	@Override
	public VerificationLevel getDynamicRulesVerificationLevel() {
		return this.dynamicRulesVerificationLevel;
	}

	@Override
	public void setCheckUseServicesFieldNamingConvention(final boolean checkUseServicesFieldNamingConvention) {
		this.checkUseServicesFieldNamingConvention = checkUseServicesFieldNamingConvention;
	}

	@Override
	public boolean isCheckUseServicesFieldNamingConvention() {
		return this.checkUseServicesFieldNamingConvention;
	}

	@Override
	public void setCheckAccessedParametersFieldNamingConvention(
			final boolean checkAccessedParametersFieldNamingConvention) {
		this.checkAccessedParametersFieldNamingConvention = checkAccessedParametersFieldNamingConvention;
	}

	@Override
	public boolean isCheckAccessedParametersFieldNamingConvention() {
		return this.checkAccessedParametersFieldNamingConvention;
	}

	@Override
	public void setCheckEventsMethodNamingConvention(final boolean checkEventsMethodNamingConvention) {
		this.checkEventsMethodNamingConvention = checkEventsMethodNamingConvention;
	}

	@Override
	public boolean isCheckEventsMethodNamingConvention() {
		return this.checkEventsMethodNamingConvention;
	}

	@Override
	public void setCheckProduceEventsFieldNamingConvention(final boolean checkProduceEventsFieldNamingConvention) {
		this.checkProduceEventsFieldNamingConvention = checkProduceEventsFieldNamingConvention;
	}

	@Override
	public boolean isCheckProduceEventsFieldNamingConvention() {
		return this.checkProduceEventsFieldNamingConvention;
	}

	@Override
	public void setCheckUseSubscriberFieldNamingConvention(final boolean checkUseSubscriberFieldNamingConvention) {
		this.checkUseSubscriberFieldNamingConvention = checkUseSubscriberFieldNamingConvention;
	}

	@Override
	public boolean isCheckUseSubscriberFieldNamingConvention() {
		return this.checkUseSubscriberFieldNamingConvention;
	}

	@Override
	public void checkImplementation(final Class<?> implementation) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!ComponentAnnotations.isImplementation(implementation)) {
			String message = "Component implementation " + implementation.getSimpleName() + " should be annotated: @"
					+ Implementation.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (ComponentAnnotations.isComponent(implementation)) {
			String message = "Component type " + implementation.getSimpleName() + " should not be annotated: @"
					+ Component.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public void checkComponentType(final Class<?> componentType) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (CovalentContainer.class.equals(componentType)) {
			// a single possible implementation: the type is part of the implementation
			return;
		}
		Method[] methods = componentType.getMethods();
		if (methods.length > 0) {
			StringJoiner methodNames = new StringJoiner(", ");
			for (Method method : methods) {
				methodNames.add(method.getName() + "()");
			}
			String message = "Component type " + componentType.getSimpleName()
					+ " should be a tag interface, without any method: it declares " + methodNames
					+ ". Move them to a Services or Parameters interface that the implementation also implements.";
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public void checkItem(final Class<?> itemClass) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!ComponentAnnotations.isItem(itemClass)) {
			String message = "Component item " + itemClass.getSimpleName() + " should be annotated with @"
					+ Item.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public void checkComposedComponent(final Class<?> composedComponentClass) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (ComponentAnnotations.isItem(composedComponentClass)) {
			String message = "Composed component " + composedComponentClass.getSimpleName()
					+ " should not be annotated with @" + Item.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public boolean isServices(final Class<?> assumedServices) {
		return ComponentAnnotations.isServices(assumedServices);
	}

	@Override
	public void checkServices(final Class<?> assumedServices) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isServices(assumedServices)) {
			String message = "Services interface: " + assumedServices.getSimpleName() + " should be annotated with @"
					+ Services.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (!assumedServices.getSimpleName().endsWith(servicesSuffix)) {
			String message = "Naming Error: Services interface: " + assumedServices.getSimpleName()
					+ " should be suffixed with '" + servicesSuffix + "'";
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public void checkUsedServicesField(final Field usedServicesField, final Class<?> servicesInterface,
			final Class<?> componentImplOrItem) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isServices(usedServicesField.getType())) {
			String message = "Interface " + usedServicesField.getType().getSimpleName()
					+ " is expected to be a Services interface, as it is referred to as such in used services field:\n"
					+ "    " + usedServicesField.getDeclaringClass().getSimpleName() + "." + usedServicesField.getName()
					+ ", however it is not annotated with @" + Services.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (this.checkUseServicesFieldNamingConvention) {
			this.checkUseServicesFieldNamingConvention(usedServicesField.getName(), servicesInterface,
					componentImplOrItem);
		}
	}

	/**
	 * Verifies the name of a used-services field: it starts with {@code use} and ends with
	 * the services interface's simple name.
	 *
	 * @param fieldName           the field's name
	 * @param servicesInterface the interface it is declared for
	 * @param componentImplOrItem the declaring implementation or item class,
	 *                            used to phrase the message
	 * @see #checkFieldNamingConvention
	 */
	private void checkUseServicesFieldNamingConvention(final String fieldName, final Class<?> servicesInterface,
			final Class<?> componentImplOrItem) {
		this.checkFieldNamingConvention(fieldName, servicesInterface, componentImplOrItem, usePrefix, "A", "used services", "services");
	}

	@Override
	public boolean isParameters(final Class<?> assumedParameters) {
		return ComponentAnnotations.isParameters(assumedParameters);
	}

	@Override
	public void checkParameters(final Class<?> assumedParameters) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isParameters(assumedParameters)) {
			String message = "Interface: " + assumedParameters.getSimpleName() + " should be annotated with @"
					+ Parameters.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (!assumedParameters.getSimpleName().endsWith(parametersSuffix)) {
			String message = "Naming Error: Parameters interface: " + assumedParameters.getSimpleName()
					+ " should be suffixed with '" + parametersSuffix + "'";
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public void checkAccessedParametersField(final Field accessedParametersField, final Class<?> parametersInterface,
			final Class<?> componentImplOrItem) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isParameters(accessedParametersField.getType())) {
			String message = "Type of accessed parameters field: " + accessedParametersField
					+ " should be annotated with @" + Parameters.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (this.checkAccessedParametersFieldNamingConvention) {
			this.checkAccessedParametersFieldNamingConvention(accessedParametersField.getName(), parametersInterface,
					componentImplOrItem);
		}
	}

	/**
	 * Verifies the name of a accessed-parameters field: it starts with {@code access} and ends with
	 * the parameters interface's simple name.
	 *
	 * @param fieldName           the field's name
	 * @param parametersInterface the interface it is declared for
	 * @param componentImplOrItem the declaring implementation or item class,
	 *                            used to phrase the message
	 * @see #checkFieldNamingConvention
	 */
	private void checkAccessedParametersFieldNamingConvention(final String fieldName, final Class<?> parametersInterface,
			final Class<?> componentImplOrItem) {
		this.checkFieldNamingConvention(fieldName, parametersInterface, componentImplOrItem, accessPrefix, "An", "accessed parameters", "parameters");
	}

	@Override
	public boolean isEvents(final Class<?> assumedEvents) {
		return ComponentAnnotations.isEvents(assumedEvents);
	}

	@Override
	public void checkEvents(final Class<?> assumedEvents) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isEvents(assumedEvents)) {
			String message = "Interface: " + assumedEvents.getSimpleName() + " should be annotated with @"
					+ Events.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (!assumedEvents.getSimpleName().endsWith(eventsSuffix)) {
			String message = "Naming Error: Events interface: " + assumedEvents.getSimpleName()
					+ " should be suffixed with '" + eventsSuffix + "'";
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public void checkProducedEventsField(final Field producedEventsField, final Class<?> eventsInterface,
			final Class<?> componentImplOrItem) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isEvents(producedEventsField.getType())) {
			String message = "Type of produced events field: " + producedEventsField + " should be annotated with @"
					+ Events.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (this.checkProduceEventsFieldNamingConvention) {
			this.checkProduceEventFieldNamingConvention(producedEventsField.getName(), eventsInterface,
					componentImplOrItem);
		}
	}

	/**
	 * Verifies the name of a produced-events field: it starts with {@code produce} and ends with
	 * the events interface's simple name.
	 *
	 * @param fieldName           the field's name
	 * @param eventsInterface the interface it is declared for
	 * @param componentImplOrItem the declaring implementation or item class,
	 *                            used to phrase the message
	 * @see #checkFieldNamingConvention
	 */
	private void checkProduceEventFieldNamingConvention(final String fieldName, final Class<?> eventsInterface,
			final Class<?> componentImplOrItem) {
		this.checkFieldNamingConvention(fieldName, eventsInterface, componentImplOrItem, producePrefix, "A", "produced events", "events");
	}

	@Override
	public boolean isSubscribe(final Class<?> assumedSubscribe) {
		return ComponentAnnotations.isSubscribe(assumedSubscribe);
	}

	@Override
	public void checkSubscribe(final Class<?> assumedSubscribe) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isSubscribe(assumedSubscribe)) {
			String message = "Interface: " + assumedSubscribe.getSimpleName() + " should be annotated with @"
					+ EventsSubscribe.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (!assumedSubscribe.getSimpleName().endsWith(subscribeSuffix)) {
			String message = "Naming Error: Subscribe interface: " + assumedSubscribe.getSimpleName()
					+ " should be suffixed with '" + subscribeSuffix + "'";
			this.handleStructuralVerification(message);
		}
	}

	@Override
	public void checkUsedSubscribeField(final Field usedSubscribeField, final Class<?> subscribeInterface,
			final Class<?> componentImplOrItem) {
		if (VerificationLevel.silent.equals(this.structuralRulesVerificationLevel)) {
			return;
		}
		if (!this.isSubscribe(usedSubscribeField.getType())) {
			String message = "Type of used subscribe field: " + usedSubscribeField + " should be annotated with @"
					+ EventsSubscribe.class.getSimpleName();
			this.handleStructuralVerification(message);
		}
		if (this.checkUseSubscriberFieldNamingConvention) {
			this.checkUseSubscriberFieldNamingConvention(usedSubscribeField.getName(), subscribeInterface,
					componentImplOrItem);
		}
	}

	/**
	 * Verifies the name of a used-subscribe field: it starts with {@code use} and ends with
	 * the subscribe interface's simple name.
	 *
	 * @param fieldName           the field's name
	 * @param subscribeInterface the interface it is declared for
	 * @param componentImplOrItem the declaring implementation or item class,
	 *                            used to phrase the message
	 * @see #checkFieldNamingConvention
	 */
	private void checkUseSubscriberFieldNamingConvention(final String fieldName, final Class<?> subscribeInterface,
			final Class<?> componentImplOrItem) {
		this.checkFieldNamingConvention(fieldName, subscribeInterface, componentImplOrItem, subscribePrefix, "An", "events subscriber", "subscribe");
	}

	/**
	 * Verifies that a port field name starts with the given prefix and ends
	 * with the simple name of the port interface; reports a violation as a
	 * {@link NamingException} (wrapping whatever
	 * {@link #handleStructuralVerification} does) when it does not.
	 *
	 * @param fieldName           the field's name
	 * @param portInterface       the interface the field is declared for
	 * @param componentImplOrItem the declaring implementation or item class,
	 *                            used to phrase the message
	 * @param prefix              the prefix the field name must start with
	 * @param article             "A" or "An", to phrase the message
	 * @param fieldKind           the kind of field, for instance "used
	 *                            services", to phrase the message
	 * @param interfaceKind       the kind of interface, for instance
	 *                            "services", to phrase the message
	 */
	private void checkFieldNamingConvention(final String fieldName, final Class<?> portInterface,
			final Class<?> componentImplOrItem, final String prefix, final String article, final String fieldKind,
			final String interfaceKind) {
		String interfaceName = portInterface.getSimpleName();
		boolean conventionRespected = fieldName.startsWith(prefix) && fieldName.endsWith(interfaceName);

		if (!conventionRespected) {
			String correctNameExample = prefix + interfaceName;
			String implKind = this.getComponentImplementationKind(componentImplOrItem);
			String message = "Naming Error: In " + implKind + " " + componentImplOrItem.getSimpleName() + ", the "
					+ fieldKind + " field " + fieldName + " for " + interfaceKind + " interface " + interfaceName
					+ "\ndoes not respect the naming convention. " + article + " " + fieldKind
					+ " field should start with '" + prefix + "'\nand end with the name of the " + interfaceKind
					+ " interface. For instance: " + correctNameExample;
			try {
				this.handleStructuralVerification(message);
			} catch (ValidationException e) {
				throw new NamingException(e.getMessage());
			}
		}
	}

	@Override
	public void checkEventMethodNamingConvention(final Class<?> eventsInterface) {
		Method[] declaredMethods = eventsInterface.getDeclaredMethods();
		String badNamedMethods = "";
		for (Method method : declaredMethods) {
			String methodName = method.getName();
			if (!methodName.startsWith("on")) {
				badNamedMethods += "\t- " + methodName + "() -> should be named on"
						+ methodName.substring(0, 1).toUpperCase() + methodName.substring(1) + "\n";
			}
		}
		if (!badNamedMethods.isEmpty()) {
			try {
				String message = "Naming Error: The events interface " + eventsInterface.getSimpleName() + "\n"
						+ "has methods that do not respect the naming convention.\n"
						+ "An event method should start with 'on'.\n"
						+ "The EventsManager should be unable to filter this event if required in a subscribe parameter.\n"
						+ "Methods that don't respect the naming convention:\n" + badNamedMethods;
				this.handleStructuralVerification(message);
			} catch (ValidationException e) {
				throw new NamingException(e.getMessage());
			}
		}
	}

	@Override
	public void handleStructuralVerification(final String message) {
		this.handleVerification(message, this.structuralRulesVerificationLevel);
	}

	@Override
	public void handleDynamicVerification(final String message) {
		this.handleVerification(message, this.dynamicRulesVerificationLevel);
	}

	/**
	 * Common handling for both structural and dynamic verification
	 * messages: does nothing when {@code silent}, logs at {@code fine} when
	 * {@code trace}, logs at {@code warning} when {@code warn}, or throws a
	 * {@link ValidationException} otherwise ({@code strict}).
	 *
	 * @param message           the violation message
	 * @param verificationLevel the verification level to apply; must not be
	 *                          {@code null}
	 * @throws ValidationException if the level requires failing hard
	 */
	private void handleVerification(final String message, final VerificationLevel verificationLevel) {
		RuntimeCheck.notNull(verificationLevel, "verificationLevel");
		if (VerificationLevel.silent.equals(verificationLevel)) {
			return;
		} else if (VerificationLevel.trace.equals(verificationLevel)) {
			log.fine(message);
		} else if (VerificationLevel.warn.equals(verificationLevel)) {
			log.warning(message);
		} else {
			throw new ValidationException(message);
		}
	}

	/**
	 * Phrases the given class as either a "component item" or "component
	 * implementation", for use in validation messages.
	 *
	 * @param componentImplOrItem the implementation or item class
	 * @return a human-readable kind label
	 */
	private String getComponentImplementationKind(final Class<?> componentImplOrItem) {
		String implKind;
		if (ComponentAnnotations.isItem(componentImplOrItem)) {
			implKind = "component item";
		} else {
			implKind = "component implementation";
		}
		return implKind;
	}

}
