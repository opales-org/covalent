/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Tutorial step 3: events. An {@code Orders} component announces each placed order; the
 * {@code Audit} and {@code Kitchen} components react to it just by implementing the events
 * interface, without the producer knowing them.
 */
public final class Step3 {

    // --- The events contract ----------------------------------------------

    /**
     * Events contract for orders: a producer announces that an order was placed and expects no answer;
     * every component implementing this interface is notified.
     */
    @Events
    public interface OrderEvents {
        /**
         * Announces that an order was placed.
         *
         * @param product product name, {@code coffee} or {@code croissant}
         * @param quantity number of units
         */
        void onOrderPlaced(String product, int quantity);
    }

    // --- The producer: it also provides a service to place orders ---------

    /**
     * Component type of the order producer, which provides {@link OrderServices} and produces
     * {@link OrderEvents}.
     */
    @Component
    public interface Orders {
    }

    /** Services port provided by {@link Orders} to place orders. */
    @Services
    public interface OrderServices {
        /**
         * Places an order, which is then announced through {@link OrderEvents}.
         *
         * @param product product name, {@code coffee} or {@code croissant}
         * @param quantity number of units
         */
        void placeOrder(String product, int quantity);
    }

    /**
     * Managed class of the {@link Orders} component; it declares the events it produces and fires one
     * for each placed order.
     */
    @Implementation
    public static final class OrdersImpl implements Orders, OrderServices {

        @ProducedEvents
        private OrderEvents produceOrderEvents;

        @Override
        public void placeOrder(final String product, final int quantity) {
            System.out.println("Orders: placing " + quantity + " x " + product);
            this.produceOrderEvents.onOrderPlaced(product, quantity);
        }
    }

    // --- Two consumers: they only implement the events interface ----------

    /** Component type of the audit consumer, which receives {@link OrderEvents}. */
    @Component
    public interface Audit {
    }

    /**
     * Managed class of the {@link Audit} component; implementing {@link OrderEvents} is enough for the
     * container to subscribe it automatically.
     */
    @Implementation
    public static final class AuditImpl implements Audit, OrderEvents {

        @Override
        public void onOrderPlaced(final String product, final int quantity) {
            System.out.println("Audit: order recorded (" + quantity + " x " + product + ")");
        }
    }

    /** Component type of the kitchen consumer, which receives {@link OrderEvents}. */
    @Component
    public interface Kitchen {
    }

    /**
     * Managed class of the {@link Kitchen} component; like the audit, it is subscribed automatically
     * because it implements {@link OrderEvents}.
     */
    @Implementation
    public static final class KitchenImpl implements Kitchen, OrderEvents {

        @Override
        public void onOrderPlaced(final String product, final int quantity) {
            System.out.println("Kitchen: preparing " + quantity + " x " + product);
        }
    }

    // --- The application: it places some orders ---------------------------

    /** Component type of the application component, which places the orders. */
    @Component
    public interface Application {
    }

    /**
     * Managed class of the {@link Application} component; it uses {@link OrderServices} to place
     * orders.
     */
    @Implementation
    public static final class ApplicationImpl implements Application {

        @UsedServices
        private OrderServices useOrderServices;

        /** Places an order for two coffees and another for one croissant, when the component is activated. */
        @Activation
        public void run() {
            this.useOrderServices.placeOrder("coffee", 2);
            this.useOrderServices.placeOrder("croissant", 1);
        }
    }

    /**
     * Switches events to synchronous dispatch so that the output order is deterministic, deploys the
     * components in the main container and starts it.
     *
     * @param args command-line arguments, ignored
     */
    public static void main(final String[] args) {
        CovalentContainer container = Covalent.main;
        // Events are asynchronous by default. Switching to synchronous dispatch
        // keeps this tutorial output deterministic.
        container.getParameters().setSynchronousEvents(true);
        try {
            container.deployComponents(
                    OrdersImpl.class, AuditImpl.class, KitchenImpl.class, ApplicationImpl.class);
            container.start();
        } finally {
            if (container.isStarted()) {
                container.stop();
            }
        }
    }
}
