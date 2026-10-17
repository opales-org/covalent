/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * <p>
 * Every explicit-component-type overload on {@code RawServicesAndParametersPort}
 * and {@code RawEventsPort} starts with the same guard: if the explicit
 * {@code componentType} argument is itself annotated {@code @Implementation}
 * (a managed component's implementation class, not a raw one),
 * {@code RawPortSupport.isImplementation} redirects the call to the
 * implicit-type overload instead, which then resolves the real owner through
 * {@code CallerFinder.getCaller()}. Only one of the seven port method pairs
 * this applies to ({@code accessParameters}, via
 * {@code RawComponentsContractTests.
 * rawParametersAccessAcceptsManagedImplementationAsOfferorType}) had a test
 * actually taking this branch; the other six
 * ({@code provideServices}/{@code unprovideServices}, {@code useServices},
 * {@code offerParameters}/{@code unofferParameters}, {@code produceEvents}
 * /{@code unproduceEvents}, {@code subscribe}/{@code unsubscribe}) did not.
 * </p>
 * <p>
 * Separately, the three producer-side implicit-type resolvers
 * ({@code tryToGetComponentTypeForProvidedServices},
 * {@code tryToGetComponentTypeForOfferedParameters},
 * {@code tryToGetComponentTypeForEventsProducer}) each have a fallback
 * branch for when {@code CallerFinder.getCaller()} itself resolves to a
 * class with no registered component type - unlike the shared
 * {@code RawPortSupport.resolveManualPortOwner} used by the consumer-side
 * resolvers, which already had coverage for both outcomes. This was also
 * untested.
 * </p>
 * <p>
 * These close the last uncovered branches in
 * {@code RawServicesAndParametersPort} and {@code RawEventsPort}
 * identified while reducing {@code RawComponentsImpl}.
 * </p>
 * <p>
 * When the resolved producer-side componentType has no metamodel entry at all
 * (never passed to {@code registerComponent} nor deployed as a managed
 * component), creating a port for it must fail immediately:
 * {@code provideServices}/{@code offerParameters} would otherwise create the port
 * successfully but leave it permanently unreachable through {@code useServices}/
 * {@code accessParameters} (a "ghost port" - see
 * {@code RawPortSupport.requireKnownComponentType}'s javadoc for the mechanism).
 * {@code produceEvents} does not share this particular failure mode (event
 * producers are looked up by consumers purely through {@code eventsInterface},
 * never through the metamodel's reverse index), but a componentType always
 * denotes an actual component regardless of port family, so creating any of
 * these three ports for one that was never componentified at all is equally
 * invalid. All three fail immediately and unconditionally.
 * </p>
 */
public class RawComponentsPortManagedTypeRedirectTests {

	private CovalentContainer container;

	// No tearDown(): these tests never call start() on their fresh container
	// (exercising the pre-first-start bootstrap window), and stop()
	// requires having been started first; the throwaway container is simply
	// discarded after each test.

	@Test
	public void servicesRedirectWhenExplicitTypeIsManagedImplementation() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();

		RedirectServicesProvider provider = new RedirectServicesProvider(rcs);
		rcs.registerComponent(provider);
		RedirectServicesConsumer consumer = new RedirectServicesConsumer(rcs);
		rcs.registerComponent(consumer);

		Assert.assertEquals("redirected", consumer.services.ping());

		rcs.unregisterComponent(RedirectServicesConsumer.class);
		rcs.unregisterComponent(RedirectServicesProvider.class);
	}

	@Test
	public void parametersRedirectWhenExplicitTypeIsManagedImplementation() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		RedirectParametersOfferor offeror = new RedirectParametersOfferor(rcs);
		rcs.registerComponent(offeror);

		RedirectParameters parameters = rcs.accessParameters(RedirectParametersOfferor.class, RedirectParameters.class);
		Assert.assertEquals("redirected", parameters.getValue());

		rcs.unregisterComponent(RedirectParametersOfferor.class);
	}

	@Test
	public void eventsRedirectWhenExplicitTypeIsManagedImplementation() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		RedirectEventsProducer producer = new RedirectEventsProducer(rcs);
		rcs.registerComponent(producer);
		RedirectEventsConsumer consumer = new RedirectEventsConsumer(rcs);
		rcs.registerComponent(consumer);

		producer.events.onPing("data");
		Assert.assertEquals(1, consumer.received.size());
		Assert.assertEquals("data", consumer.received.get(0));

		rcs.unregisterComponent(RedirectEventsConsumer.class);
		rcs.unregisterComponent(RedirectEventsProducer.class);
	}

	@Test
	public void implicitTypeProducerOverloadsFailImmediatelyWhenCallerIsNotRegistered() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		// Even at the least strict level, creating a producer-side port for a
		// componentType with no metamodel entry at all must fail: componentType
		// always denotes an actual component per the raw component contract, and
		// there is no legitimate use case for creating one of these ports for a type
		// that was never componentified at all - independent of whether doing so
		// happens to cause a further failure down the line for a given port family
		// (see RawPortSupport.requireKnownComponentType's javadoc: provideServices/offerParameters would additionally create an unreachable
		// "ghost" port this way, produceEvents would not, but both are equally invalid).
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		RawComponentsServices rcs = this.container.rcs();

		// UnregisteredCaller is never passed to registerComponent, so
		// useMetamodelWriteServices.getType(UnregisteredCaller.class) resolves to
		// null: CallerFinder.getCaller() itself becomes the fallback owner, on all
		// three producer-side implicit-type resolvers - and that fallback owner has
		// no metamodel entry either.
		UnregisteredCaller caller = new UnregisteredCaller(rcs);

		IllegalStateException eventsFailure = Assert.assertThrows(IllegalStateException.class,
				caller::produceEventsImplicitly);
		Assert.assertTrue(this.isExceptionIncludingText(eventsFailure, "UnregisteredCaller"));

		IllegalStateException servicesFailure = Assert.assertThrows(IllegalStateException.class,
				caller::provideServicesImplicitly);
		Assert.assertTrue(this.isExceptionIncludingText(servicesFailure, "UnregisteredCaller"));

		IllegalStateException parametersFailure = Assert.assertThrows(IllegalStateException.class,
				caller::offerParametersImplicitly);
		Assert.assertTrue(this.isExceptionIncludingText(parametersFailure, "UnregisteredCaller"));

		// Nothing was actually created, and this container was never started, so
		// there is nothing left to clean up for UnregisteredCaller.
	}

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

	@Implementation
	public static class ManagedTypeMarker {
	}

	// --- services fixtures ---

	public interface RedirectServices {
		String ping();
	}

	public static class RedirectServicesProvider implements RedirectServices {

		private final RawComponentsServices rcs;

		public RedirectServicesProvider(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.rcs.provideServices(ManagedTypeMarker.class, RedirectServices.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unprovideServices(ManagedTypeMarker.class, RedirectServices.class);
		}

		@Override
		public String ping() {
			return "redirected";
		}

	}

	public static class RedirectServicesConsumer {

		private final RawComponentsServices rcs;

		RedirectServices services;

		public RedirectServicesConsumer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.services = this.rcs.useServices(ManagedTypeMarker.class, RedirectServices.class);
		}

	}

	// --- parameters fixtures ---

	public interface RedirectParameters {
		String getValue();
	}

	public static class RedirectParametersOfferor implements RedirectParameters {

		private final RawComponentsServices rcs;

		public RedirectParametersOfferor(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.rcs.offerParameters(ManagedTypeMarker.class, RedirectParameters.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unofferParameters(ManagedTypeMarker.class, RedirectParameters.class);
		}

		@Override
		public String getValue() {
			return "redirected";
		}

	}

	// --- events fixtures ---

	public interface RedirectEvents {
		void onPing(String data);
	}

	public static class RedirectEventsProducer {

		private final RawComponentsServices rcs;

		RedirectEvents events;

		public RedirectEventsProducer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.events = this.rcs.produceEvents(ManagedTypeMarker.class, RedirectEvents.class, null, null);
		}

		@Passivation
		public void passivate() {
			this.rcs.unproduceEvents(ManagedTypeMarker.class, RedirectEvents.class);
		}

	}

	public static class RedirectEventsConsumer implements RedirectEvents {

		private final RawComponentsServices rcs;

		final List<String> received = new ArrayList<>();

		public RedirectEventsConsumer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.rcs.subscribe(ManagedTypeMarker.class, RedirectEvents.class, this);
		}

		@Passivation
		public void passivate() {
			this.rcs.unsubscribe(ManagedTypeMarker.class, RedirectEvents.class, this);
		}

		@Override
		public void onPing(final String data) {
			this.received.add(data);
		}

	}

	// --- unresolvable-caller fixtures ---

	public interface UnresolvableEvents {
		void onPing(String data);
	}

	public interface UnresolvableServices {
		String ping();
	}

	public interface UnresolvableParameters {
		String getValue();
	}

	/**
	 * Deliberately never registered with {@code registerComponent}: calling the
	 * implicit-type producer-side overloads from here means
	 * {@code CallerFinder.getCaller()} resolves to this class, and
	 * {@code useMetamodelWriteServices.getType(UnregisteredCaller.class)} then
	 * resolves to null, taking the "could not retrieve component type" fallback
	 * branch in each of the three producer-side implicit-type resolvers.
	 */
	public static class UnregisteredCaller implements UnresolvableEvents, UnresolvableServices, UnresolvableParameters {

		private final RawComponentsServices rcs;

		public UnregisteredCaller(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		UnresolvableEvents produceEventsImplicitly() {
			return this.rcs.produceEvents(UnresolvableEvents.class);
		}

		void provideServicesImplicitly() {
			this.rcs.provideServices(UnresolvableServices.class);
		}

		void offerParametersImplicitly() {
			this.rcs.offerParameters(UnresolvableParameters.class);
		}

		@Override
		public void onPing(final String data) {
		}

		@Override
		public String ping() {
			return "unresolved";
		}

		@Override
		public String getValue() {
			return "unresolved";
		}

	}

}
