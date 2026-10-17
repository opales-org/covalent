# Step 3: Events

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 2: Services](02-services.md) | [Next: Step 4: Parameters](04-parameters.md)
<!-- /doc-nav:top -->

Services are request/response. Events are different: a producer announces that
something happened and does not expect any answer. It does not even know who is
listening. In this step, an `Orders` component announces each new order, and two
independent components react to it.

## Declaring The Events

An events interface is annotated with `@Events`. Each event is a method that
returns `void` and declares no exception. Its arguments are ordinary Java types.

```java
@Events
public interface OrderEvents {
    void onOrderPlaced(String product, int quantity);
}
```

> Tip: By convention, event method names start with `on` and end with a past-tense
> verb, such as `onOrderPlaced`.

## Producing Events

A producer declares a field of the events interface type, annotated with
`@ProducedEvents`, and calls the event method to fire the event.

```java
@ProducedEvents
private OrderEvents produceOrderEvents;
```

```java
this.produceOrderEvents.onOrderPlaced(product, quantity);
```

The producer does not know who receives the event.

## Consuming Events

A consumer implements the events interface. That is all: by default, the
container automatically subscribes it to the producers of the events it
implements, during startup.

```java
@Implementation
public static final class AuditImpl implements Audit, OrderEvents {

    @Override
    public void onOrderPlaced(final String product, final int quantity) {
        System.out.println("Audit: order recorded (" + quantity + " x " + product + ")");
    }
}
```

`Orders` is not modified when the `Audit` and `Kitchen` components are added, and
neither of them knows about the other.

## The Complete Program

```java
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

public final class Step3 {

    // --- The events contract ----------------------------------------------

    @Events
    public interface OrderEvents {
        void onOrderPlaced(String product, int quantity);
    }

    // --- The producer: it also provides a service to place orders ---------

    @Component
    public interface Orders {
    }

    @Services
    public interface OrderServices {
        void placeOrder(String product, int quantity);
    }

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

    @Component
    public interface Audit {
    }

    @Implementation
    public static final class AuditImpl implements Audit, OrderEvents {

        @Override
        public void onOrderPlaced(final String product, final int quantity) {
            System.out.println("Audit: order recorded (" + quantity + " x " + product + ")");
        }
    }

    @Component
    public interface Kitchen {
    }

    @Implementation
    public static final class KitchenImpl implements Kitchen, OrderEvents {

        @Override
        public void onOrderPlaced(final String product, final int quantity) {
            System.out.println("Kitchen: preparing " + quantity + " x " + product);
        }
    }

    // --- The application: it places some orders ---------------------------

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
            this.useOrderServices.placeOrder("croissant", 1);
        }
    }

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
```

Its output:

```text
Orders: placing 2 x coffee
Audit: order recorded (2 x coffee)
Kitchen: preparing 2 x coffee
Orders: placing 1 x croissant
Audit: order recorded (1 x croissant)
Kitchen: preparing 1 x croissant
```

> Note: The order in which several consumers of the same event are called is not a
> contract. Do not write consumers that depend on it.

## Asynchronous By Default

By default, Covalent dispatches events asynchronously, so a slow consumer never
blocks the producer. The program above switches the container to synchronous
dispatch, only to make its output deterministic:

```java
container.getParameters().setSynchronousEvents(true);
```

Synchronous dispatch is convenient for tests and debugging. In an application, keep the
asynchronous default unless you have a reason not to. Event dispatch is
configurable: see [Asynchronous Event Dispatch
Configuration](../managed-components.md#asynchronous-event-dispatch-configuration).

## What You Learned

- An events interface is annotated with `@Events`; a component that declares a
  `@ProducedEvents` field is a producer.
- A component that implements the events interface is a consumer, and is subscribed
  automatically.
- Producers and consumers are decoupled: they only share the events interface.

Reference: [Events](../managed-components.md#events).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 2: Services](02-services.md) | [Next: Step 4: Parameters](04-parameters.md)
<!-- /doc-nav:bottom -->
