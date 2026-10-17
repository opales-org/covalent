/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsFilter;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Runs the Getting Started example of the README, copied verbatim in
 * {@link Launcher}, the way a reader runs it: in its own JVM, since it uses
 * the {@code Covalent.main} container shared by the other tests of this JVM.
 * Keep {@link Launcher} identical to the README.
 */
public class ReadmeGettingStartedTests {

	@Test
	public void gettingStartedExamplePrintsTheCroissantOrderOnly() throws IOException, InterruptedException {
		String javaBinary = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		Process process = new ProcessBuilder(javaBinary, "-cp", System.getProperty("java.class.path"),
				Launcher.class.getName()).start();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();
		Thread stderrDrain = new Thread(() -> {
			try {
				process.getErrorStream().transferTo(stderr);
			} catch (final IOException ignored) {
				// Nothing left to read once the process has exited.
			}
		});
		stderrDrain.start();
		String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exitCode = process.waitFor();
		stderrDrain.join();

		Assert.assertEquals("the example should exit successfully; stderr was:\n" + stderr, 0, exitCode);
		Assert.assertEquals("Kitchen: preparing 1 x croissant", stdout.trim());
	}

	public static final class Launcher {

		@Component
		public interface Orders { }

		@Services
		public interface OrderServices {
			void placeOrder(String product, int quantity);
		}

		@Events
		public interface OrderEvents {
			void onOrderPlaced(String product, int quantity);

			@EventsSubscribe
			interface OrderEventsSubscribe {
				void subscribe(OrderEvents observer, OrderEventsFilter filter);
			}

			@EventsFilter
			interface OrderEventsFilter {
				boolean filterOrderPlaced(String product, int quantity); // true = filtered out
			}
		}

		@Implementation
		public static final class OrdersImpl implements Orders, OrderServices {
			@ProducedEvents private OrderEvents produceOrderEvents;
			public void placeOrder(final String product, final int quantity) {
				this.produceOrderEvents.onOrderPlaced(product, quantity);
			}
		}

		@Component
		public interface Kitchen { }

		@Implementation
		public static final class KitchenImpl implements Kitchen, OrderEvents {
			@UsedEventsSubscribe private OrderEvents.OrderEventsSubscribe useOrderEventsSubscribe;

			@Activation
			public void subscribeToCroissants() {
				this.useOrderEventsSubscribe.subscribe(this, new CroissantsOnly());
			}

			public void onOrderPlaced(final String product, final int quantity) {
				System.out.println("Kitchen: preparing " + quantity + " x " + product);
			}

			private static final class CroissantsOnly implements OrderEventsFilter {
				public boolean filterOrderPlaced(final String product, final int quantity) {
					return !"croissant".equals(product);
				}
			}
		}

		@Component
		public interface Application { }

		@Implementation
		public static final class ApplicationImpl implements Application {
			@UsedServices private OrderServices useOrderServices;
			@Activation public void run() {
				this.useOrderServices.placeOrder("coffee", 2);
				this.useOrderServices.placeOrder("croissant", 1);
			}
		}

		public static void main(final String[] args) {
			CovalentContainer container = Covalent.main;
			container.getParameters().setSynchronousEvents(true);
			try {
				container.deployComponents(OrdersImpl.class, KitchenImpl.class, ApplicationImpl.class);
				container.start();
			} finally {
				if (container.isStarted()) {
					container.stop();
				}
			}
		}
	}
}
