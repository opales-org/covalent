/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Verifies component-type resolution and port-field recognition of the
 * introspection services: the component type is found through direct
 * interfaces and through superclasses, a class implementing two component
 * interfaces is reported at the structural level (rejected when strict,
 * resolved to one of them otherwise), and every kind of Covalent field
 * (item, produced events, used subscribe) is recognized as a component field
 * while an unannotated field is not.
 */
public class ComponentIntrospectorTests {

	private CapturingLogHandler componentValidatorLogs;

	@Component
	public interface FirstComponent {
	}

	@Component
	public interface SecondComponent {
	}

	@Parameters
	public interface SampleParameters {
	}

	@Events
	public interface SampleEvents {
		void onSample();

		@EventsSubscribe
		interface SampleEventsSubscribe {
			void subscribe(SampleEvents observer);
		}
	}

	public static class BaseImpl implements FirstComponent {
	}

	public static class DerivedImpl extends BaseImpl {
	}

	/** Extends a component interface without being annotated itself. */
	public interface ExtendedComponent extends FirstComponent {
	}

	public static class ExtendedImpl implements ExtendedComponent {
	}

	public static class ParametersOnlyImpl implements SampleParameters {
	}

	public static class MultiImpl implements FirstComponent, SecondComponent {
	}

	public static class Fields {
		@Item
		Object item;

		@ProducedEvents
		SampleEvents produceSampleEvents;

		@UsedEventsSubscribe
		SampleEvents.SampleEventsSubscribe useSampleEventsSubscribe;

		Object plain;
	}

	private CovalentContainer container;

	private IntrospectionServices introspection;

	@Before
	public void setUp() {
		this.componentValidatorLogs = CapturingLogHandler.install(ComponentValidatorImpl.class);
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
		this.introspection = this.container.rcs().useServices(IntrospectionServices.class);
	}

	@After
	public void restoreTheLogs() {
		this.componentValidatorLogs.restore();
	}

	@Test
	public void theComponentTypeIsFoundThroughAnInterfaceExtendingIt() {
		Assert.assertEquals(FirstComponent.class, this.introspection.getComponentType(ExtendedImpl.class));
	}

	@Test
	public void componentTypeIsFoundThroughTheSuperclassHierarchy() {
		Assert.assertEquals(FirstComponent.class, this.introspection.getComponentType(BaseImpl.class));
		Assert.assertEquals(FirstComponent.class, this.introspection.getComponentType(DerivedImpl.class));
	}

	@Test
	public void classWithoutComponentInterfaceIsItsOwnComponentType() {
		Assert.assertEquals(ParametersOnlyImpl.class, this.introspection.getComponentType(ParametersOnlyImpl.class));
	}

	@Test
	public void severalComponentInterfacesAreRejectedUnderStrictStructuralLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		ValidationException e = Assert.assertThrows(ValidationException.class,
				() -> this.introspection.getComponentType(MultiImpl.class));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("More than one @Component interface"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("MultiImpl"));
	}

	@Test
	public void severalComponentInterfacesResolveToOneOfThemUnderWarnLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);

		Class<?> componentType = this.introspection.getComponentType(MultiImpl.class);

		Assert.assertTrue(Set.of(FirstComponent.class, SecondComponent.class).contains(componentType));
		Assert.assertEquals(List.of("More than one @Component interface found for component impl MultiImpl"),
				this.componentValidatorLogs.messages(Level.WARNING));
	}

	@Test
	public void parametersInterfacesAreRecognized() {
		Assert.assertTrue(this.introspection.isComponentParameters(SampleParameters.class));
		Assert.assertFalse(this.introspection.isComponentParameters(BaseImpl.class));
	}

	@Test
	public void everyKindOfCovalentFieldIsRecognizedAsAComponentField() throws Exception {
		Assert.assertTrue(this.introspection.isComponentField(field("item")));
		Assert.assertTrue(this.introspection.isComponentField(field("produceSampleEvents")));
		Assert.assertTrue(this.introspection.isComponentField(field("useSampleEventsSubscribe")));
		Assert.assertFalse(this.introspection.isComponentField(field("plain")));
	}

	@Test
	public void implementingChecksDirectInterfacesOnly() {
		Assert.assertTrue(this.introspection.isImplementing(BaseImpl.class, FirstComponent.class));
		Assert.assertFalse(this.introspection.isImplementing(BaseImpl.class, SecondComponent.class));
		Assert.assertFalse(this.introspection.isImplementing(DerivedImpl.class, FirstComponent.class));
	}

	private static Field field(final String name) throws NoSuchFieldException {
		return Fields.class.getDeclaredField(name);
	}
}
