/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.LifecycleServices;

/**
 * A usage link (used services, accessed parameters, consumed events) belongs
 * to the component that declares it: it survives the passivation, removal or
 * undeployment of the provider, fails clearly while the provider is absent,
 * and reaches the next provider as soon as there is one.
 */
public class PortRebindingTests {

	private static final String ABSENT = "No provider is currently declared";

	static final List<String> received = new CopyOnWriteArrayList<>();

	static int providerCount;

	static int producerCount;

	static int userCount;

	private CovalentContainer container;

	private LifecycleServices lcs;

	@Before
	public void setUp() {
		received.clear();
		providerCount = 0;
		producerCount = 0;
		userCount = 0;
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.lcs = this.container.lcs();
		this.container.rcs().registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	private UC user() {
		return this.container.rcs().useServices(U.class, UC.class);
	}

	private QS producer() {
		return this.container.rcs().useServices(Q.class, QS.class);
	}

	private void startAll() {
		this.container.deployComponents(PImpl.class, UImpl.class, QImpl.class);
		this.container.start();
	}

	private void removeAndUndeploy(final Class<?> type, final Class<?> implementation) {
		this.lcs.passivateComponent(type);
		this.lcs.removeComponent(type);
		this.lcs.undeployComponent(implementation);
	}

	private void assertAbsent(final Runnable call) {
		IllegalStateException e = Assert.assertThrows("The call must fail while no provider is declared.",
				IllegalStateException.class,
				() -> call.run());
		Assert.assertTrue(e.getMessage(), e.getMessage().contains(ABSENT));
	}

	@Test
	public void providerRemovedThenReactivatedIsReachedAgain() {
		this.startAll();
		Assert.assertEquals("pong-P1", this.user().callPing());

		this.lcs.passivateComponent(P.class);
		this.lcs.removeComponent(P.class);
		this.assertAbsent(() -> this.user().callPing());
		this.assertAbsent(() -> this.user().readColor());

		this.lcs.activateComponent(P.class);
		Assert.assertEquals("pong-P2", this.user().callPing());
		Assert.assertEquals("red-P2", this.user().readColor());
	}

	@Test
	public void providerUndeployedThenRedeployedIsReachedAgain() {
		this.startAll();
		this.removeAndUndeploy(P.class, PImpl.class);
		this.assertAbsent(() -> this.user().callPing());
		this.assertAbsent(() -> this.user().readColor());

		this.lcs.deployComponent(PImpl.class);
		this.lcs.activateComponent(P.class);
		Assert.assertEquals("pong-P2", this.user().callPing());
		Assert.assertEquals("red-P2", this.user().readColor());
	}

	@Test
	public void providerReplacedByAnotherImplementationIsReached() {
		this.startAll();
		this.removeAndUndeploy(P.class, PImpl.class);

		this.lcs.deployComponent(PImpl2.class);
		this.lcs.activateComponent(P.class);
		Assert.assertEquals("pong-P2impl", this.user().callPing());
		Assert.assertEquals("blue-P2impl", this.user().readColor());
	}

	@Test
	public void providerPassivatedThenActivatedIsReachedAgain() {
		this.startAll();
		this.lcs.passivateComponent(P.class);
		IllegalStateException e = Assert.assertThrows("A passivated provider must not be reachable.",
				IllegalStateException.class,
				() -> this.user().callPing());
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("passivated"));
		Assert.assertEquals("Offered parameters stay available while passivated.", "red-P1", this.user().readColor());

		this.lcs.activateComponent(P.class);
		Assert.assertEquals("pong-P1", this.user().callPing());
	}

	@Test
	public void usageLinkDeclaredBeforeTheProviderExists() {
		this.container.deployComponents(UImpl.class);
		this.container.start();
		this.assertAbsent(() -> this.user().callPing());
		this.assertAbsent(() -> this.user().readColor());

		this.lcs.deployComponent(PImpl.class);
		this.lcs.activateComponent(P.class);
		Assert.assertEquals("pong-P1", this.user().callPing());
		Assert.assertEquals("red-P1", this.user().readColor());
	}

	@Test
	public void eventConsumerKeepsReceivingFromTheReplacedProducer() {
		this.startAll();
		this.producer().fire("one");
		this.removeAndUndeploy(Q.class, QImpl.class);

		this.lcs.deployComponent(QImpl.class);
		this.lcs.activateComponent(Q.class);
		this.producer().fire("two");
		Assert.assertEquals(List.of("one#Q1@U1", "two#Q2@U1"), received);
	}

	@Test
	public void userRemovedAndRedeployedGetsFreshLinksAndOldInstanceStopsReceiving() {
		this.startAll();
		this.removeAndUndeploy(U.class, UImpl.class);
		this.producer().fire("while-absent");
		Assert.assertTrue(received.isEmpty());

		this.lcs.deployComponent(UImpl.class);
		this.lcs.activateComponent(U.class);
		Assert.assertEquals("pong-P1", this.user().callPing());
		this.producer().fire("back");
		Assert.assertEquals(List.of("back#Q1@U2"), received);
	}

	@Test
	public void rawProxiesHeldByTheUserSurviveProviderReplacement() {
		this.container.rcs().registerComponent(new RawP("1"));
		this.container.rcs().provideServices(RawP.class, RawPS.class);
		this.container.rcs().offerParameters(RawP.class, RawPP.class);
		RawPS service = this.container.rcs().useServices(RawP.class, RawPS.class);
		RawPP parameters = this.container.rcs().accessParameters(RawP.class, RawPP.class);
		Assert.assertEquals("raw-pong-1", service.ping());
		Assert.assertEquals("raw-color-1", parameters.color());

		this.container.rcs().unregisterComponent(RawP.class);
		this.assertAbsent(service::ping);
		this.assertAbsent(parameters::color);

		this.container.rcs().registerComponent(new RawP("2"));
		this.container.rcs().provideServices(RawP.class, RawPS.class);
		this.container.rcs().offerParameters(RawP.class, RawPP.class);
		Assert.assertEquals("raw-pong-2", service.ping());
		Assert.assertEquals("raw-color-2", parameters.color());
	}

	public interface RawPS {
		String ping();
	}

	public interface RawPP {
		String color();
	}

	public static class RawP implements RawPS, RawPP {
		private final String tag;

		RawP(final String tag) {
			this.tag = tag;
		}

		@Override
		public String ping() {
			return "raw-pong-" + this.tag;
		}

		@Override
		public String color() {
			return "raw-color-" + this.tag;
		}
	}

	@Component
	public interface P {
	}

	@Services
	public interface PS {
		String ping();
	}

	@Parameters
	public interface PP {
		String getColor();
	}

	@Implementation
	public static class PImpl implements P, PS, PP {
		private final int id = ++providerCount;

		@Override
		public String ping() {
			return "pong-P" + this.id;
		}

		@Override
		public String getColor() {
			return "red-P" + this.id;
		}
	}

	@Implementation
	public static class PImpl2 implements P, PS, PP {
		@Override
		public String ping() {
			return "pong-P2impl";
		}

		@Override
		public String getColor() {
			return "blue-P2impl";
		}
	}

	@Events
	public interface News {
		void onNews(String message);
	}

	@Component
	public interface Q {
	}

	@Services
	public interface QS {
		void fire(String message);
	}

	@Implementation
	public static class QImpl implements Q, QS {
		private final int id = ++producerCount;

		@ProducedEvents
		News events;

		@Override
		public void fire(final String message) {
			this.events.onNews(message + "#Q" + this.id);
		}
	}

	@Component
	public interface U {
	}

	@Services
	public interface UC {
		String callPing();

		String readColor();
	}

	@Implementation
	public static class UImpl implements U, UC, News {
		private final int id = ++userCount;

		@UsedServices
		PS ps;

		@AccessedParameters
		PP pp;

		@Override
		public String callPing() {
			return this.ps.ping();
		}

		@Override
		public String readColor() {
			return this.pp.getColor();
		}

		@Override
		public void onNews(final String message) {
			received.add(message + "@U" + this.id);
		}
	}
}
