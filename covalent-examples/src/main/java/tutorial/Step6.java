/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Cloned;
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
 * Tutorial step 6: advanced events. Extends the events of step 3 with cloning of event arguments,
 * explicit subscription through a subscribe interface, filtering of events by the consumer, and
 * subscription delegation, where the producer is told about its subscribers.
 */
public final class Step6 {

    // --- The event payload: mutable, but cloneable --------------------------

    /**
     * Event payload describing an order; mutable but cloneable, so that the container can hand each
     * consumer its own copy.
     */
    public static final class Order implements Cloneable {
        private String product;
        private int quantity;

        /**
         * Creates an order.
         *
         * @param product product name
         * @param quantity number of units
         */
        public Order(final String product, final int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        /**
         * Returns the product name.
         *
         * @return the product name
         */
        public String getProduct() {
            return this.product;
        }

        /**
         * Returns the number of units ordered.
         *
         * @return the number of units
         */
        public int getQuantity() {
            return this.quantity;
        }

        /**
         * Changes the number of units ordered.
         *
         * @param quantity number of units
         */
        public void setQuantity(final int quantity) {
            this.quantity = quantity;
        }

        @Override
        public Order clone() {
            try {
                return (Order) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString() {
            return this.quantity + " x " + this.product;
        }
    }

    // --- The events contract: cloning, subscription, filter -----------------

    /**
     * Events contract for orders. It is {@code @Cloned}, so each consumer receives a clone of the
     * {@link Order}, and it declares its own subscribe and filter interfaces.
     */
    @Events
    @Cloned
    public interface OrderEvents {
        /**
         * Announces that an order was placed.
         *
         * @param order the placed order; consumers receive a clone
         */
        void onOrderPlaced(Order order);

        /**
         * Subscribe interface of {@link OrderEvents}: used by a consumer to subscribe explicitly, with or
         * without a filter, and implemented by the producer to be told about subscribers.
         */
        @EventsSubscribe
        interface OrderEventsSubscribe {
            /**
             * Subscribes a consumer to every order event.
             *
             * @param observer the consumer to notify
             */
            void subscribe(OrderEvents observer);

            /**
             * Subscribes a consumer to the order events accepted by a filter.
             *
             * @param observer the consumer to notify
             * @param filter decides which events are filtered out
             */
            void subscribe(OrderEvents observer, OrderEventsFilter filter);

            /**
             * Cancels a subscription.
             *
             * @param observer the consumer to stop notifying
             */
            void unsubscribe(OrderEvents observer);
        }

        /**
         * Filter interface of {@link OrderEvents}: lets a consumer receive only the events it is
         * interested in.
         */
        @EventsFilter
        interface OrderEventsFilter {
            // Returning true means: filter this event out.
            /**
             * Decides whether an order event is dropped for the subscriber that registered this filter.
             *
             * @param order the placed order
             * @return {@code true} to filter the event out, {@code false} to deliver it
             */
            boolean filterOrderPlaced(Order order);
        }
    }

    // --- The producer: implementing the subscribe interface enables delegation

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
         * @param product product name
         * @param quantity number of units
         */
        void placeOrder(String product, int quantity);
    }

    /**
     * Managed class of the {@link Orders} component; by implementing the subscribe interface it is
     * told about each subscription, while the container keeps registering subscribers and dispatching
     * events.
     */
    @Implementation
    public static final class OrdersImpl
            implements Orders, OrderServices, OrderEvents.OrderEventsSubscribe {

        @ProducedEvents
        private OrderEvents produceOrderEvents;

        private int orderCount;

        @Override
        public void placeOrder(final String product, final int quantity) {
            this.orderCount++;
            Order order = new Order(product, quantity);
            this.produceOrderEvents.onOrderPlaced(order);
            System.out.println("Orders: my own order is unchanged: " + order);
        }

        // Delegation: the container still registers the subscriber and dispatches
        // events; the producer is just notified.
        @Override
        public void subscribe(final OrderEvents observer) {
            System.out.println("Orders: a subscriber joined (" + this.orderCount + " orders so far)");
        }

        @Override
        public void subscribe(final OrderEvents observer, final OrderEvents.OrderEventsFilter filter) {
            System.out.println("Orders: a filtered subscriber joined (" + this.orderCount + " orders so far)");
        }

        @Override
        public void unsubscribe(final OrderEvents observer) {
            System.out.println("Orders: a subscriber left");
        }
    }

    // --- Consumer 1: receives everything -----------------------------------

    /** Component type of the audit consumer, which receives every order. */
    @Component
    public interface Audit {
    }

    /**
     * Managed class of the {@link Audit} component; it subscribes explicitly to all orders, and shows
     * that changing the received order does not affect the producer's copy.
     */
    @Implementation
    public static final class AuditImpl implements Audit, OrderEvents {

        @UsedEventsSubscribe
        private OrderEventsSubscribe useOrderEventsSubscribe;

        /** Subscribes this component to every order event, when it is activated. */
        @Activation
        public void subscribeToOrders() {
            this.useOrderEventsSubscribe.subscribe(this);
        }

        @Override
        public void onOrderPlaced(final Order order) {
            System.out.println("Audit: " + order);
            // The consumer only sees a clone: even if it (wrongly) modifies it,
            // the producer's own object is not affected.
            order.setQuantity(0);
        }
    }

    // --- Consumer 2: receives only croissant orders ------------------------------

    /** Component type of the consumer that is alerted for croissant orders only. */
    @Component
    public interface CroissantAlert {
    }

    /**
     * Managed class of the {@link CroissantAlert} component; it subscribes with a filter that drops
     * every order which is not a croissant order.
     */
    @Implementation
    public static final class CroissantAlertImpl implements CroissantAlert, OrderEvents {

        @UsedEventsSubscribe
        private OrderEventsSubscribe useOrderEventsSubscribe;

        /**
         * Subscribes this component to the order events with a filter keeping only croissant orders, when
         * it is activated.
         */
        @Activation
        public void subscribeToCroissantOrders() {
            this.useOrderEventsSubscribe.subscribe(this, new CroissantOrdersOnly());
        }

        @Override
        public void onOrderPlaced(final Order order) {
            System.out.println("Alert: a croissant order arrived, product = " + order.getProduct());
        }

        private static final class CroissantOrdersOnly implements OrderEventsFilter {
            @Override
            public boolean filterOrderPlaced(final Order order) {
                return !"croissant".equals(order.getProduct()); // true = filtered out
            }
        }
    }

    // --- The application ---------------------------------------------------

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

        /**
         * Places an order for two coffees and another for twelve croissants, when the component is
         * activated.
         */
        @Activation
        public void run() {
            this.useOrderServices.placeOrder("coffee", 2);
            this.useOrderServices.placeOrder("croissant", 12);
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
        container.getParameters().setSynchronousEvents(true);
        try {
            container.deployComponents(
                    OrdersImpl.class, AuditImpl.class, CroissantAlertImpl.class, ApplicationImpl.class);
            container.start();
        } finally {
            if (container.isStarted()) {
                container.stop();
            }
        }
    }
}
