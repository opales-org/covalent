# Step 6: Advanced Events

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 5: Lifecycle](05-lifecycle.md) | [Next: Step 7: Structuring Components](07-structuring-components.md)
<!-- /doc-nav:top -->

Automatic subscription, as in [step 3](03-events.md), covers most needs. Covalent
events go further: a consumer can subscribe explicitly, receive only the events it
is interested in through a **filter**, the producer can be told about new
subscribers (**subscription delegation**), and event arguments can be **cloned** so
a consumer cannot alter the producer's data.

This step puts the four mechanisms together in one events interface.

## The Events Contract

Everything below is part of the events interface itself, so that producers and
consumers agree on it.

The `@Cloned` annotation on the interface asks the container to clone the
arguments of the events before delivering them:

```java
@Events
@Cloned
public interface OrderEvents {
    void onOrderPlaced(Order order);
```

A nested interface annotated with `@EventsSubscribe` declares how to subscribe.
One of its methods takes a filter as its second argument:

```java
@EventsSubscribe
interface OrderEventsSubscribe {
    void subscribe(OrderEvents observer);

    void subscribe(OrderEvents observer, OrderEventsFilter filter);

    void unsubscribe(OrderEvents observer);

    // Needed to end a subscription made with a filter.
    void unsubscribe(OrderEvents observer, OrderEventsFilter filter);
}
```

A nested interface annotated with `@EventsFilter` declares the filter. It has one
method per event, named after the event with `on` replaced by `filter`, and with
the same arguments. The filter method returns `true` to filter the event **out**:

```java
@EventsFilter
interface OrderEventsFilter {
    // Returning true means: filter this event out.
    boolean filterOrderPlaced(Order order);
}
```

## Manual Subscription

A consumer that wants explicit control over its subscription declares a field of
the subscribe interface type, annotated with `@UsedEventsSubscribe`, and calls the
subscribe method itself, typically in its activation callback.

```java
@UsedEventsSubscribe
private OrderEventsSubscribe useOrderEventsSubscribe;

@Activation
public void subscribeToOrders() {
    this.useOrderEventsSubscribe.subscribe(this);
}
```

An events interface that has filters requires explicit subscription, since the
consumer must pass its filter.

## Filters

A filter belongs to one subscription, not to the producer. Here, the `CroissantAlert`
component only wants croissant orders, while `Audit` subscribes without a filter
and receives everything:

```java
this.useOrderEventsSubscribe.subscribe(this, new CroissantOrdersOnly());
```

```java
private static final class CroissantOrdersOnly implements OrderEventsFilter {
    @Override
    public boolean filterOrderPlaced(final Order order) {
        return !"croissant".equals(order.getProduct()); // true = filtered out
    }
}
```

The container calls the filter before delivering each event to that subscriber, so
the consumer's code only sees the events it asked for.

A subscription is identified by its observer and its filter, so it is ended by
`unsubscribe(this, filter)` with the same filter, not by `unsubscribe(this)`. The
consumer therefore has to keep the filter it passed, for instance in a field. A
filter without a meaningful `equals` is matched by identity, so the same instance
must be used for both calls.

## Subscription Delegation

A producer sometimes needs to know when a consumer subscribes: typically, to send
it the current state so it can start from a consistent situation. To be notified,
the producer implements the subscribe interface:

```java
@Implementation
public static final class OrdersImpl
        implements Orders, OrderServices, OrderEvents.OrderEventsSubscribe {
```

The delegation is partial: the container still registers the subscriber and
dispatches the events, and the producer is only notified. Its implementation of
the subscribe method that accepts a filter should ignore the filter, since the
container applies it.

## Cloning

Events are fire-and-forget, so a consumer should treat its arguments as read-only.
With `@Cloned`, the container hands the consumers a clone of the arguments (which
must implement `Cloneable`), so that a consumer that does modify them cannot alter
the producer's own object:

```java
System.out.println("Audit: " + order);
// The consumer only sees a clone: even if it (wrongly) modifies it,
// the producer's own object is not affected.
order.setQuantity(0);
```

> Warning: The arguments are cloned once per event, not once per consumer: the consumers of
> the same event share the same clone. Cloning protects the producer, not the consumers
> from each other. Consumers must still treat their arguments as read-only.

Cloning can also be requested on a single event method, or on a single argument.

## The Complete Program

```java
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

public final class Step6 {

    // --- The event payload: mutable, but cloneable --------------------------

    public static final class Order implements Cloneable {
        private String product;
        private int quantity;

        public Order(final String product, final int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        public String getProduct() {
            return this.product;
        }

        public int getQuantity() {
            return this.quantity;
        }

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

    @Events
    @Cloned
    public interface OrderEvents {
        void onOrderPlaced(Order order);

        @EventsSubscribe
        interface OrderEventsSubscribe {
            void subscribe(OrderEvents observer);

            void subscribe(OrderEvents observer, OrderEventsFilter filter);

            void unsubscribe(OrderEvents observer);

            // Needed to end a subscription made with a filter.
            void unsubscribe(OrderEvents observer, OrderEventsFilter filter);
        }

        @EventsFilter
        interface OrderEventsFilter {
            // Returning true means: filter this event out.
            boolean filterOrderPlaced(Order order);
        }
    }

    // --- The producer: implementing the subscribe interface enables delegation

    @Component
    public interface Orders {
    }

    @Services
    public interface OrderServices {
        void placeOrder(String product, int quantity);
    }

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

        @Override
        public void unsubscribe(final OrderEvents observer, final OrderEvents.OrderEventsFilter filter) {
            System.out.println("Orders: a filtered subscriber left");
        }
    }

    // --- Consumer 1: receives everything -----------------------------------

    @Component
    public interface Audit {
    }

    @Implementation
    public static final class AuditImpl implements Audit, OrderEvents {

        @UsedEventsSubscribe
        private OrderEventsSubscribe useOrderEventsSubscribe;

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

    @Component
    public interface CroissantAlert {
    }

    @Implementation
    public static final class CroissantAlertImpl implements CroissantAlert, OrderEvents {

        @UsedEventsSubscribe
        private OrderEventsSubscribe useOrderEventsSubscribe;

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

    @Component
    public interface Application {
    }

    @Implementation
    public static final class ApplicationImpl implements Application {

        @UsedServices
        private OrderServices useOrderServices;

        @Activation
        public void run() {
            this.useOrderServices.placeOrder("coffee", 2);
            this.useOrderServices.placeOrder("croissant", 12);
        }
    }

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
```

One possible output (the order between `Audit` and `CroissantAlert` is not a
contract):

```text
Orders: a subscriber joined (0 orders so far)
Orders: a filtered subscriber joined (0 orders so far)
Audit: 2 x coffee
Orders: my own order is unchanged: 2 x coffee
Audit: 12 x croissant
Alert: a croissant order arrived, product = croissant
Orders: my own order is unchanged: 12 x croissant
```

The coffee order is filtered out for `CroissantAlert`; the producer's own copy of
each order is unchanged even though `Audit` modified its clone.

## What You Learned

- Subscribe and filter interfaces are nested in the events interface and are part
  of its contract.
- A filter is attached to one subscription and decides, before delivery, whether the
  event reaches that consumer.
- A producer can be notified of subscriptions by implementing the subscribe interface.
- `@Cloned` protects the producer's data.

Reference: [Event Subscription](../managed-components.md#event-subscription),
[Event Subscription Delegation](../managed-components.md#event-subscription-delegation),
[Event Filters](../managed-components.md#event-filters),
[Event Data Cloning](../managed-components.md#event-data-cloning).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 5: Lifecycle](05-lifecycle.md) | [Next: Step 7: Structuring Components](07-structuring-components.md)
<!-- /doc-nav:bottom -->
