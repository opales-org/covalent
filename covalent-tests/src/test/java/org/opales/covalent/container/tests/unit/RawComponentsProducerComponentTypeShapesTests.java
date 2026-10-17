/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * <p>
 * {@code RawPortSupport.requireKnownComponentType} (the guard against "ghost ports" - see {@code RawComponentsPortManagedTypeRedirectTests}) gates
 * {@code provideServices}/{@code offerParameters} purely on whether
 * {@code componentType} has a metamodel entry. It does not, and must not,
 * care about the <em>shape</em> of {@code componentType}: the raw component
 * contract ({@code RawComponentsImpl.checkRawComponentContract}) performs no
 * assignability check between {@code componentType} and the implementation
 * class, so a raw component's type can legitimately be a plain interface
 * implemented by the instance, the instance's own implementation class, or a
 * class with no structural relation to the instance at all ("arbitrary"). A
 * fourth shape - the calling class, resolved implicitly through
 * {@code CallerFinder} - is already covered elsewhere: the success case by
 * {@code RawComponentsImplicitOwnerOverloadTests}, the failure case by
 * {@code RawComponentsPortManagedTypeRedirectTests}.
 * </p>
 * <p>
 * These tests cover the remaining gaps:
 * </p>
 * <ul>
 * <li>a properly registered componentType of the "plain interface" or
 * "arbitrary class" shape must still let {@code provideServices}/
 * {@code offerParameters} succeed normally - the guard must not reject a legitimate shape;</li>
 * <li>the explicit two-argument overload (not the implicit-type/
 * {@code CallerFinder} path already covered elsewhere) must fail immediately
 * when its componentType argument was never registered at all.</li>
 * </ul>
 */
public class RawComponentsProducerComponentTypeShapesTests {

	private CovalentContainer container;

	// No tearDown(): these tests never call start() on their fresh container
	// (exercising the pre-first-start bootstrap window), and stop()
	// requires having been started first; the throwaway container is simply
	// discarded after each test.

	private boolean isExceptionIncludingText(final Throwable failure, final String text) {
		Throwable current = failure;
		while (current != null) {
			if (current.getMessage() != null && current.getMessage().contains(text)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	// --- shape: plain interface implemented by the instance ---

	public interface MarkerInterfaceType {
	}

	public interface InterfaceShapeServices {
		String ping();
	}

	public static class InterfaceShapeProvider implements MarkerInterfaceType, InterfaceShapeServices {
		@Override
		public String ping() {
			return "interface-shape";
		}
	}

	@Test
	public void provideServicesSucceedsWhenComponentTypeIsARegisteredPlainInterface() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		InterfaceShapeProvider instance = new InterfaceShapeProvider();
		rcs.registerComponent(MarkerInterfaceType.class, instance);

		rcs.provideServices(MarkerInterfaceType.class, InterfaceShapeServices.class);
		InterfaceShapeServices services = rcs.useServices(MarkerInterfaceType.class, InterfaceShapeServices.class);
		Assert.assertEquals("interface-shape", services.ping());

		rcs.unprovideServices(MarkerInterfaceType.class, InterfaceShapeServices.class);
		rcs.unregisterComponent(MarkerInterfaceType.class);
	}

	public interface InterfaceShapeParameters {
		String getValue();
	}

	public static class InterfaceShapeOfferor implements MarkerInterfaceType, InterfaceShapeParameters {
		@Override
		public String getValue() {
			return "interface-shape";
		}
	}

	@Test
	public void offerParametersSucceedsWhenComponentTypeIsARegisteredPlainInterface() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		InterfaceShapeOfferor instance = new InterfaceShapeOfferor();
		rcs.registerComponent(MarkerInterfaceType.class, instance);

		rcs.offerParameters(MarkerInterfaceType.class, InterfaceShapeParameters.class);
		InterfaceShapeParameters parameters = rcs.accessParameters(MarkerInterfaceType.class,
				InterfaceShapeParameters.class);
		Assert.assertEquals("interface-shape", parameters.getValue());

		rcs.unofferParameters(MarkerInterfaceType.class, InterfaceShapeParameters.class);
		rcs.unregisterComponent(MarkerInterfaceType.class);
	}

	// --- shape: arbitrary class, structurally unrelated to the instance ---

	public static class ArbitraryMarker {
		// Deliberately NOT implemented by either fixture instance below: the raw
		// component contract (RawComponentsImpl.checkRawComponentContract) performs
		// no assignability check between componentType and the implementation class,
		// so this is a legitimate, if unusual, componentType.
	}

	public interface ArbitraryShapeServices {
		String ping();
	}

	public static class ArbitraryShapeProvider implements ArbitraryShapeServices {
		@Override
		public String ping() {
			return "arbitrary-shape";
		}
	}

	@Test
	public void provideServicesSucceedsWhenComponentTypeIsAnArbitraryRegisteredClass() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		ArbitraryShapeProvider instance = new ArbitraryShapeProvider();
		rcs.registerComponent(ArbitraryMarker.class, instance);

		rcs.provideServices(ArbitraryMarker.class, ArbitraryShapeServices.class);
		ArbitraryShapeServices services = rcs.useServices(ArbitraryMarker.class, ArbitraryShapeServices.class);
		Assert.assertEquals("arbitrary-shape", services.ping());

		rcs.unprovideServices(ArbitraryMarker.class, ArbitraryShapeServices.class);
		rcs.unregisterComponent(ArbitraryMarker.class);
	}

	public interface ArbitraryShapeParameters {
		String getValue();
	}

	public static class ArbitraryShapeOfferor implements ArbitraryShapeParameters {
		@Override
		public String getValue() {
			return "arbitrary-shape";
		}
	}

	@Test
	public void offerParametersSucceedsWhenComponentTypeIsAnArbitraryRegisteredClass() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		ArbitraryShapeOfferor instance = new ArbitraryShapeOfferor();
		rcs.registerComponent(ArbitraryMarker.class, instance);

		rcs.offerParameters(ArbitraryMarker.class, ArbitraryShapeParameters.class);
		ArbitraryShapeParameters parameters = rcs.accessParameters(ArbitraryMarker.class,
				ArbitraryShapeParameters.class);
		Assert.assertEquals("arbitrary-shape", parameters.getValue());

		rcs.unofferParameters(ArbitraryMarker.class, ArbitraryShapeParameters.class);
		rcs.unregisterComponent(ArbitraryMarker.class);
	}

	// --- negative: explicit two-argument overload, componentType never registered ---

	public interface NeverRegisteredMarker {
	}

	public interface ExplicitTypeFailureServices {
		String ping();
	}

	public interface ExplicitTypeFailureParameters {
		String getValue();
	}

	@Test
	public void provideServicesFailsImmediatelyForExplicitTypeNeverRegistered() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class,
				() -> rcs.provideServices(NeverRegisteredMarker.class, ExplicitTypeFailureServices.class));
		Assert.assertTrue(this.isExceptionIncludingText(failure, "NeverRegisteredMarker"));
	}

	@Test
	public void offerParametersFailsImmediatelyForExplicitTypeNeverRegistered() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class,
				() -> rcs.offerParameters(NeverRegisteredMarker.class, ExplicitTypeFailureParameters.class));
		Assert.assertTrue(this.isExceptionIncludingText(failure, "NeverRegisteredMarker"));
	}

}
