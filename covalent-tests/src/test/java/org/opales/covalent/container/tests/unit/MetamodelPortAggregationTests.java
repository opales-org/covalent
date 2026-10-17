/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.container.metamodel.metamodel.ComponentItemMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentItemMetamodelImpl;
import org.opales.covalent.container.metamodel.metamodel.impl.ComponentMetamodelImpl;

@RunWith(Parameterized.class)
public class MetamodelPortAggregationTests {

	@Parameterized.Parameters(name = "{0}")
	public static Object[][] ports() {
		return new Object[][] {
				{ "provided services", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getProvidedServices,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setProvidedServices,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllProvidedServices },
				{ "used services", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getUsedServices,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setUsedServices,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllUsedServices },
				{ "produced events", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getProducedEvents,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setProducedEvents,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllProducedEvents },
				{ "consumed events", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getConsumedEvents,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setConsumedEvents,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllConsumedEvents },
				{ "offered parameters", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getOfferedParameters,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setOfferedParameters,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllOfferedParameters },
				{ "accessed parameters", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getAccessedParameters,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setAccessedParameters,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllAccessedParameters },
				{ "provided subscribers", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getProvidedSubscribers,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setProvidedSubscribers,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllProvidedSubscribers },
				{ "used subscribers", (Function<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::getUsedSubscribers,
						(BiConsumer<ComponentItemMetamodel, List<Class<?>>>) ComponentItemMetamodel::setUsedSubscribers,
						(Function<ComponentMetamodel, Set<Class<?>>>) ComponentMetamodel::getAllUsedSubscribers }
		};
	}

	private final Function<ComponentItemMetamodel, List<Class<?>>> declaredPorts;
	private final BiConsumer<ComponentItemMetamodel, List<Class<?>>> setDeclaredPorts;
	private final Function<ComponentMetamodel, Set<Class<?>>> allPorts;

	public MetamodelPortAggregationTests(final String name,
			final Function<ComponentItemMetamodel, List<Class<?>>> declaredPorts,
			final BiConsumer<ComponentItemMetamodel, List<Class<?>>> setDeclaredPorts,
			final Function<ComponentMetamodel, Set<Class<?>>> allPorts) {
		this.declaredPorts = declaredPorts;
		this.setDeclaredPorts = setDeclaredPorts;
		this.allPorts = allPorts;
	}

	@Test
	public void componentPortsReflectSuccessiveAdditionsAndRemovals() {
		ComponentMetamodel metamodel = new ComponentMetamodelImpl();
		Assert.assertTrue(this.allPorts.apply(metamodel).isEmpty());
		this.addPort(metamodel, PortA.class);
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
		this.addPort(metamodel, PortB.class);
		Assert.assertEquals(Set.of(PortA.class, PortB.class), this.allPorts.apply(metamodel));
		this.removePort(metamodel, PortA.class);
		Assert.assertEquals(Set.of(PortB.class), this.allPorts.apply(metamodel));
		this.clearPorts(metamodel);
		Assert.assertTrue(this.allPorts.apply(metamodel).isEmpty());
	}

	@Test
	public void itemPortsReflectSuccessiveAdditionsAndRemovals() {
		ComponentMetamodel metamodel = new ComponentMetamodelImpl();
		ComponentItemMetamodel item = new ComponentItemMetamodelImpl();
		metamodel.setItemMetamodels(List.of(item));
		this.addPort(metamodel, PortA.class);
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
		this.addPort(item, PortB.class);
		Assert.assertEquals(Set.of(PortA.class, PortB.class), this.allPorts.apply(metamodel));
		this.removePort(item, PortB.class);
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
	}

	@Test
	public void addingAndRemovingItemsUpdatesAggregatedPorts() {
		ComponentMetamodel metamodel = new ComponentMetamodelImpl();
		ComponentItemMetamodel item = new ComponentItemMetamodelImpl();
		this.addPort(metamodel, PortA.class);
		this.addPort(item, PortB.class);
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
		metamodel.setItemMetamodels(List.of(item));
		Assert.assertEquals(Set.of(PortA.class, PortB.class), this.allPorts.apply(metamodel));
		metamodel.setItemMetamodels(List.of());
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
	}

	@Test
	public void sharedPortRemainsUntilItsLastDeclarationIsRemoved() {
		ComponentMetamodel metamodel = new ComponentMetamodelImpl();
		ComponentItemMetamodel first = new ComponentItemMetamodelImpl();
		ComponentItemMetamodel second = new ComponentItemMetamodelImpl();
		metamodel.setItemMetamodels(List.of(first, second));
		this.addPort(metamodel, PortA.class);
		this.addPort(first, PortA.class);
		this.addPort(second, PortA.class);
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
		this.clearPorts(metamodel);
		this.clearPorts(first);
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
		this.clearPorts(second);
		Assert.assertTrue(this.allPorts.apply(metamodel).isEmpty());
	}

	@Test
	public void modifyingReturnedSetDoesNotAlterDeclarationsOrLaterReads() {
		ComponentMetamodel metamodel = new ComponentMetamodelImpl();
		this.addPort(metamodel, PortA.class);
		Set<Class<?>> result = this.allPorts.apply(metamodel);
		result.clear();
		result.add(PortB.class);
		Assert.assertEquals(List.of(PortA.class), this.declaredPorts.apply(metamodel));
		Assert.assertEquals(Set.of(PortA.class), this.allPorts.apply(metamodel));
	}

	@Test
	public void earlierResultIsNotModifiedByLaterReads() {
		ComponentMetamodel metamodel = new ComponentMetamodelImpl();
		this.addPort(metamodel, PortA.class);
		Set<Class<?>> earlier = this.allPorts.apply(metamodel);
		this.addPort(metamodel, PortB.class);
		Assert.assertEquals(Set.of(PortA.class, PortB.class), this.allPorts.apply(metamodel));
		Assert.assertEquals(Set.of(PortA.class), earlier);
	}

	private void addPort(final ComponentItemMetamodel metamodel, final Class<?> port) {
		List<Class<?>> ports = this.declaredPorts.apply(metamodel);
		ports.add(port);
		this.setDeclaredPorts.accept(metamodel, ports);
	}

	private void removePort(final ComponentItemMetamodel metamodel, final Class<?> port) {
		List<Class<?>> ports = this.declaredPorts.apply(metamodel);
		ports.remove(port);
		this.setDeclaredPorts.accept(metamodel, ports);
	}

	private void clearPorts(final ComponentItemMetamodel metamodel) {
		this.setDeclaredPorts.accept(metamodel, List.of());
	}

	interface PortA { }
	interface PortB { }
}
