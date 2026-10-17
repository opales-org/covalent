/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
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
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Verifies how the descriptor factory wires ports across a component and its
 * items when the container starts: an item may itself produce events, a
 * component whose items both implement the same services, parameters,
 * consumed events or subscribe interface is rejected under the strict
 * structural level and resolved to the first implementor under the warn
 * level, and a class without a default constructor leaves its
 * component in the failed state.
 */
public class DescriptorFactoryContainerTests {

	private CapturingLogHandler componentValidatorLogs;
	private CapturingLogHandler lifecycleManagerLogs;

	@Events
	public interface HostedEvents {
		void onHosted(String value);
	}

	@Services
	public interface HostServices {
		void fire(String value);
	}

	@Component
	public interface Host {
	}

	@Implementation
	public static class HostImpl implements Host, HostServices {
		@Item
		private HostItem item;

		@Override
		public void fire(final String value) {
			this.item.fire(value);
		}
	}

	@Item
	public static class HostItem {
		@ProducedEvents
		private HostedEvents produceHostedEvents;

		void fire(final String value) {
			this.produceHostedEvents.onHosted(value);
		}
	}

	@Services
	public interface WhoServices {
		String who();

		List<String> log();
	}

	@Parameters
	public interface WhoParameters {
		String owner();
	}

	@Events
	public interface DupConsumedEvents {
		void onDup(String value);
	}

	@Events
	public interface DupProducedEvents {
		void onProduced(String value);

		@EventsSubscribe
		interface Subscribe {
			void subscribe(DupProducedEvents observer);

			void unsubscribe(DupProducedEvents observer);
		}
	}

	@Component
	public interface Dup {
	}

	@Implementation
	public static class DupImpl implements Dup {
		@Item
		private DupItemOne one;

		@Item
		private DupItemTwo two;

		@ProducedEvents
		private DupProducedEvents produceDupProducedEvents;
	}

	public abstract static class DupItemBase
			implements WhoServices, WhoParameters, DupConsumedEvents, DupProducedEvents.Subscribe {
		protected final List<String> log = new ArrayList<>();

		@Override
		public List<String> log() {
			return this.log;
		}

		@Override
		public void onDup(final String value) {
			this.log.add("consumed:" + value);
		}

		@Override
		public void subscribe(final DupProducedEvents observer) {
			this.log.add("subscribed");
		}

		@Override
		public void unsubscribe(final DupProducedEvents observer) {
		}
	}

	@Item
	public static class DupItemOne extends DupItemBase {
		@Override
		public String who() {
			return "one";
		}

		@Override
		public String owner() {
			return "one";
		}
	}

	@Item
	public static class DupItemTwo extends DupItemBase {
		@Override
		public String who() {
			return "two";
		}

		@Override
		public String owner() {
			return "two";
		}
	}

	@Component
	public interface NoDefault {
	}

	@Implementation
	public static class NoDefaultImpl implements NoDefault {
		public NoDefaultImpl(final String required) {
		}
	}

	public static class RawProducer {
	}

	public static class RawConsumer {
	}

	private CovalentContainer container;

	private RawComponentsServices rcs;

	@Before
	public void setUp() {
		this.componentValidatorLogs = CapturingLogHandler.install(ComponentValidatorImpl.class);
		this.lifecycleManagerLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
		this.componentValidatorLogs.restore();
		this.lifecycleManagerLogs.restore();
	}

	@Test
	public void eventsProducedByAnItemReachTheirSubscribers() {
		this.container.deployComponents(HostImpl.class);
		this.container.start();
		List<String> received = new ArrayList<>();
		this.rcs.registerComponent(new RawConsumer());
		this.rcs.subscribe(RawConsumer.class, HostedEvents.class, (HostedEvents) received::add);

		this.rcs.useServices(HostServices.class).fire("hello");

		Assert.assertEquals(List.of("hello"), received);
	}

	@Test
	public void duplicateImplementorsAreRejectedUnderStrictStructuralLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.container.deployComponents(DupImpl.class);

		this.container.start();

		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(Dup.class));
		Assert.assertEquals(List.of("Component Dup failed; cleaning composite Dup.", "Component Dup failed during vertical start."),
				this.lifecycleManagerLogs.messages(Level.SEVERE));
	}

	@Test
	public void duplicateImplementorsResolveToTheFirstItemUnderWarnLevel() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);
		this.container.deployComponents(DupImpl.class);
		this.container.start();
		this.rcs.registerComponent(new RawProducer());
		DupConsumedEvents producer = this.rcs.produceEvents(RawProducer.class, DupConsumedEvents.class);
		this.rcs.registerComponent(new RawConsumer());

		this.rcs.subscribe(RawConsumer.class, DupProducedEvents.class, (DupProducedEvents) value -> {
		});
		producer.onDup("x");

		WhoServices who = this.rcs.useServices(WhoServices.class);
		Assert.assertEquals("one", who.who());
		Assert.assertEquals("one", this.rcs.accessParameters(WhoParameters.class).owner());
		Assert.assertEquals(List.of("subscribed", "consumed:x"), who.log());
		Assert.assertEquals(Set.of("Services provider Dup cannot implement services interface WhoServices more than once.",
				"Parameters offeror Dup cannot implement parameters interface WhoParameters more than once.",
				"Events consumer Dup cannot implement events interface DupConsumedEvents more than once.",
				"Events producer Dup cannot implement subscribe interface Subscribe more than once."),
				Set.copyOf(this.componentValidatorLogs.messages(Level.WARNING)));
	}

	@Test
	public void componentWithoutDefaultConstructorIsFailedAndTheCauseNamesTheImplementation() {
		this.container.deployComponents(NoDefaultImpl.class);
		this.container.start();

		Assert.assertEquals(ComponentState.failed, this.container.lcs().readComponentState(NoDefault.class));
		Assert.assertEquals(List.of("Component NoDefault failed; cleaning composite NoDefault.",
				"Component NoDefault failed during vertical start."), this.lifecycleManagerLogs.messages(Level.SEVERE));
		Assert.assertTrue(this.lifecycleManagerLogs.failures(Level.SEVERE).toString(),
				this.lifecycleManagerLogs.failures(Level.SEVERE).stream().anyMatch(
						failure -> this.causeChainMessages(failure).contains("NoDefaultImpl")));
	}

	private String causeChainMessages(final Throwable failure) {
		StringBuilder messages = new StringBuilder();
		for (Throwable current = failure; current != null; current = current.getCause()) {
			messages.append(current).append('\n');
		}
		return messages.toString();
	}
}
