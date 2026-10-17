/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
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
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.metamodel.MetamodelPortWriteServices;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.metamodel.metamodel.EventsMetamodel;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Verifies the structural checks the metamodel factory applies while building
 * metamodels: an events interface whose method returns a value or declares
 * exceptions breaks the fire-and-forget paradigm and is rejected under the
 * strict structural level (only reported under the warn level), and a
 * subscribe interface must be nested inside an {@code @Events} interface (a
 * top-level one, or one nested in a holder that is not annotated as events, is
 * reported) and take that events interface as the first parameter of its
 * subscribe and unsubscribe methods. Such problems are only logged under the
 * warn level, which keeps the component deployable.
 */
public class MetamodelFactoryValidationTests {

	private CapturingLogHandler componentValidatorLogs;

	@Events
	public interface ReturningEvents {
		String onReturning();
	}

	@Events
	public interface ThrowingEvents {
		void onThrowing() throws Exception;
	}

	@Events
	public interface CleanEvents {
		void onClean();
	}

	/** Not annotated as events, although used as such by its nested subscribe interface. */
	public interface UnannotatedHolder {
		void onHeld();

		@EventsSubscribe
		interface HolderSubscribe {
			void subscribe(UnannotatedHolder observer);
		}
	}

	@Component
	public interface Listener {
	}

	@Implementation
	public static class ListenerImpl implements Listener, UnannotatedHolder {
		@UsedEventsSubscribe
		private UnannotatedHolder.HolderSubscribe useHolderSubscribe;

		@Override
		public void onHeld() {
		}
	}

	@Events
	public interface MismatchEvents {
		void onMismatch();

		@EventsSubscribe
		interface MismatchSubscribe {
			void subscribe(Object observer);
		}
	}

	@Component
	public interface MismatchListener {
	}

	@Implementation
	public static class MismatchListenerImpl implements MismatchListener, MismatchEvents {
		@UsedEventsSubscribe
		private MismatchEvents.MismatchSubscribe useMismatchSubscribe;

		@Override
		public void onMismatch() {
		}
	}

	/** A subscribe interface that is not nested in any events interface. */
	@EventsSubscribe
	public interface TopLevelSubscribe {
		void subscribe(CleanEvents observer);
	}

	@Component
	public interface TopLevelListener {
	}

	@Implementation
	public static class TopLevelListenerImpl implements TopLevelListener, CleanEvents {
		@UsedEventsSubscribe
		private TopLevelSubscribe useTopLevelSubscribe;

		@Override
		public void onClean() {
		}
	}

	/** Produced although not annotated as events, with a nested subscribe interface. */
	public interface UnannotatedProduced {
		void onProduced();

	}

	@Component
	public interface UnannotatedProducer {
	}

	@Implementation
	public static class UnannotatedProducerImpl implements UnannotatedProducer {
		@ProducedEvents
		private UnannotatedProduced produceUnannotated;
	}

	private CovalentContainer container;

	private MetamodelPortReadServices portRead;

	@Before
	public void setUp() {
		this.componentValidatorLogs = CapturingLogHandler.install(ComponentValidatorImpl.class);
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.rcs().registerComponent(this);
		this.portRead = this.container.rcs().useServices(MetamodelPortReadServices.class);
	}

	@After
	public void restoreTheLogs() {
		this.componentValidatorLogs.restore();
	}

	@Test
	public void eventsMethodReturningAValueIsRejectedUnderStrictLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		ValidationException e = Assert.assertThrows(ValidationException.class,
				() -> this.portRead.getEventsMetamodel(ReturningEvents.class));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("onReturning"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("not void"));
	}

	@Test
	public void eventsMethodDeclaringExceptionsIsRejectedUnderStrictLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		ValidationException e = Assert.assertThrows(ValidationException.class,
				() -> this.portRead.getEventsMetamodel(ThrowingEvents.class));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("onThrowing"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("throws exceptions"));
	}

	@Test
	public void cleanEventsAreAcceptedUnderStrictLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		EventsMetamodel<CleanEvents> metamodel = this.portRead.getEventsMetamodel(CleanEvents.class);

		Assert.assertEquals(CleanEvents.class, metamodel.getEvents());
	}

	@Test
	public void contractViolatingEventsAreOnlyReportedUnderWarnLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);

		EventsMetamodel<ReturningEvents> returning = this.portRead.getEventsMetamodel(ReturningEvents.class);
		EventsMetamodel<ThrowingEvents> throwing = this.portRead.getEventsMetamodel(ThrowingEvents.class);

		Assert.assertEquals(ReturningEvents.class, returning.getEvents());
		Assert.assertEquals(ThrowingEvents.class, throwing.getEvents());
		List<String> warnings = this.componentValidatorLogs.messages(Level.WARNING);
		Assert.assertEquals(2, warnings.size());
		Assert.assertTrue(warnings.toString(), warnings.stream().anyMatch(w -> w.startsWith(
				"Event method onThrowing from ThrowingEvents throws exceptions, which is in contradiction with")));
		Assert.assertTrue(warnings.toString(), warnings.stream().anyMatch(w -> w.startsWith(
				"Return of event method onReturning from ReturningEvents is not void, which is in contradiction with")));
	}

	@Test
	public void subscribeInterfaceOfUnannotatedEventsHolderIsRejectedUnderStrictLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		RuntimeException e = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(ListenerImpl.class));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("HolderSubscribe"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("must be nested inside its events interface"));
	}

	@Test
	public void subscribeInterfaceOfUnannotatedEventsHolderIsOnlyReportedUnderWarnLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);

		this.container.deployComponents(ListenerImpl.class);

		ComponentMetamodel metamodel = this.container.rcs().useServices(MetamodelWriteServices.class)
				.readMetamodel(Listener.class);
		Assert.assertTrue(metamodel.getAllUsedSubscribers().contains(UnannotatedHolder.HolderSubscribe.class));
		Assert.assertEquals(List.of("Subscribe interface HolderSubscribe must be nested inside its events interface (annotated with @Events)."),
				this.componentValidatorLogs.messages(Level.WARNING));
	}

	@Test
	public void aSubscribeInterfaceNotNestedInAnEventsInterfaceIsRejectedUnderStrictLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		RuntimeException e = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(TopLevelListenerImpl.class));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("TopLevelSubscribe"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("must be nested inside its events interface"));
	}

	@Test
	public void aSubscribeInterfaceNotNestedInAnEventsInterfaceIsOnlyReportedUnderWarnLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);

		this.container.deployComponents(TopLevelListenerImpl.class);

		ComponentMetamodel metamodel = this.container.rcs().useServices(MetamodelWriteServices.class)
				.readMetamodel(TopLevelListener.class);
		Assert.assertTrue(metamodel.getAllUsedSubscribers().contains(TopLevelSubscribe.class));
		Assert.assertEquals(List.of("Subscribe interface TopLevelSubscribe must be nested inside its events interface (annotated with @Events)."),
				this.componentValidatorLogs.messages(Level.WARNING));
	}

	@Test
	public void aSubscribeMethodWhoseFirstParameterIsNotTheEnclosingEventsIsRejectedUnderStrictLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		RuntimeException e = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(MismatchListenerImpl.class));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("First parameter (the observer) of method subscribe"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("MismatchEvents"));
	}

	@Test
	public void aSubscribeMethodWhoseFirstParameterIsNotTheEnclosingEventsIsOnlyReportedUnderWarnLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);

		this.container.deployComponents(MismatchListenerImpl.class);

		ComponentMetamodel metamodel = this.container.rcs().useServices(MetamodelWriteServices.class)
				.readMetamodel(MismatchListener.class);
		Assert.assertTrue(metamodel.getAllUsedSubscribers().contains(MismatchEvents.MismatchSubscribe.class));
		List<String> warnings = this.componentValidatorLogs.messages(Level.WARNING);
		Assert.assertEquals(1, warnings.size());
		Assert.assertTrue(warnings.get(0), warnings.get(0).startsWith(
				"First parameter (the observer) of method subscribe of subscribe interface MismatchSubscribe must be its events"));
	}

	@Test
	public void aProvidedSubscribeInterfaceWithoutEventsInterfaceIsNotMappedToNull() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);

		this.container.deployComponents(UnannotatedProducerImpl.class);

		ComponentMetamodel metamodel = this.container.rcs().useServices(MetamodelWriteServices.class)
				.readMetamodel(UnannotatedProducer.class);
		Assert.assertNull(metamodel.getProvidedSubscriberForProducedEvents(null));
	}

	@Test
	public void aMetamodelWriteOnAnUnknownComponentTypeNamesTheType() {
		MetamodelPortWriteServices write = this.container.rcs().useServices(MetamodelPortWriteServices.class);

		IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
				() -> write.addProvidedServices(String.class, Runnable.class));

		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("java.lang.String"));
	}
}
