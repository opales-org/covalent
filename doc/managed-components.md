# Covalent Managed Components Guide

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Build and Requirements](requirements.md) | [Next: Covalent Raw Components Guide](raw-components.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Covalent Managed Components](#covalent-managed-components)
  - [Component Type](#component-type)
  - [Implementation](#implementation)
  - [Items](#items)
  - [Composition](#composition)
  - [Services](#services)
  - [Events](#events)
  - [Parameters](#parameters)
  - [Lifecycle](#lifecycle)
  - [Covalent Application Lifecycle](#covalent-application-lifecycle)
  - [Application Utility Services](#application-utility-services)
    - [Asynchronous Services](#asynchronous-services)
    - [Clean Stack Traces](#clean-stack-traces)
  - [Verification Level](#verification-level)

# Covalent Managed Components

This section introduces the main concepts of the Covalent component model,
starting with component types and implementations.

New to Covalent? The [tutorial](tutorial/index.md) walks through a small
application step by step and is the fastest way to see these concepts work
together. This page is the reference for each of them individually; reading
it start to finish once, in order (Component Type, Services and Events
first, Lifecycle last), gives the fullest picture, and it is written to be
revisited afterward, one section at a time, whenever a detail is needed.

## Component Type

Components do not replace objects; they extend the object-oriented programming
model with explicit runtime structure and container-managed behavior.

Like ordinary Java objects, Covalent components have a type. However, a Covalent
component type is mono-instantiated: at runtime, there can be only one instance
of a given component type.

Covalent does not support multiple implementations of the same
component type. Supporting that model would require introducing an explicit
contract associated with the component type.

In managed components, the component type is defined by a tag interface, which
is an empty interface annotated with `@Component`.

For instance:

```java
// this is a Covalent component type
@Component
interface MyComponentType { }
```

The component type interface is a tag interface: it carries no method, and
serves only to declare and identify the type of the component. A component
type never offers services. What a component offers to others is declared in
a separate [`@Services`](#services) interface, which the implementation also implements:

```java
@Component
interface Billing { }                          // identifies the type, nothing else

@Services
interface BillingServices {                    // what the component offers
    void charge(String account, long cents);
}

@Implementation
public class BillingImpl implements Billing, BillingServices {
    // ...
}
```

The same goes for settings (a `@Parameters` interface) and for events (an
`@Events` interface). A component type that declares a method breaks a
structural rule: see [Verification Level](#verification-level).

## Implementation

The component implementation contains the component processing logic.

In a managed component, the implementation class is identified with the
`@Implementation` annotation.

For instance:

```java
@Implementation
public class MyComponentImpl implements MyComponentType {
    // ...
}
```

The implementation class must implement the component type interface. It
must also be declared `public` with a `public` no-argument constructor: see
[Instantiation Contract for Component Implementations](requirements.md#instantiation-contract-for-component-implementations) for
the reason and the failure mode when this is not the case.

An implementation class can extend a base class. The fields that declare ports,
items or composed components (`@UsedServices`, `@AccessedParameters`,
`@ProducedEvents`, `@UsedEventsSubscribe`, `@Item`, `@ComposedComponent`) are read
on the whole class hierarchy of the implementation, whatever their visibility, and
are injected like the fields of the implementation class itself. Declaring the same
port twice in the hierarchy is refused, as it is within a single class.

Lifecycle callbacks follow the Java rules. Only one method is invoked per transition:
the first annotated method found from the implementation class upward. A callback
declared in the implementation class therefore replaces the one of its superclass,
and calls the superclass method itself when it needs it (`super.activate()`).

## Items

Components have a coarser granularity than classes. A component implementation
can therefore be split across several classes. This is called a segmented
component implementation.

In Covalent, a segmented component contains one implementation class and one or
more items. The implementation is the root of the component. Items are internal
parts of that same component: they are not independent components, but they can
carry part of the component behavior and declare ports of their own.

Component items are declared with dedicated annotations:

- `@Item` on the item class declaration.
- `@Item`, or alternatively `@ComponentItem`, on the corresponding field in the
  implementation class.

When Covalent instantiates a managed component, it also resolves the items
declared in the component metamodel. If an item field is already initialized,
Covalent adopts that instance. Otherwise, Covalent creates the item with its
default constructor and injects it into the field.

For example, an item can provide a service for its owning component. Services
are explained in the next section; here, the important point is that the service
implementation is carried by the item, not by the root implementation class.

```java
@Services
public interface AuditServices {
    void record(String message);

    List<String> readMessages();
}
```

```java
@Implementation
public class AuditComponentImpl implements AuditComponent {

    @Item
    private AuditBufferItem auditBuffer;
}
```

```java
@Item
public class AuditBufferItem implements AuditServices {

    private final List<String> messages = new ArrayList<>();

    @Override
    public void record(final String message) {
        this.messages.add(message);
    }

    @Override
    public List<String> readMessages() {
        return new ArrayList<>(this.messages);
    }
}
```

Adding `@Item` to the item class declaration is recommended, but not mandatory
unless structural rule verification is set to its strictest level. See
[Verification Level](#verification-level).

Items follow the lifecycle of their owning component. They are instantiated or
adopted when the component is instantiated, and their ports are wired,
activated, passivated, and removed as part of the component lifecycle.

Lifecycle callbacks, however, belong to the component implementation. Covalent
does not treat items as independent lifecycle roots. If an item needs explicit
initialization or cleanup, the component implementation should coordinate it from
its own lifecycle callbacks.

When a component instance is provided from outside the container, Covalent does
not create missing items. It explores the existing instance and registers the
items already present in the annotated fields.

Choosing between a single annotation, `@Item`, and two distinct annotations,
`@Item` on the class and `@ComponentItem` on the field, is a matter of semantic
precision. Strictly speaking, implementing an item and aggregating an item are
two different concepts. Some projects may prefer to make that distinction
explicit, for example to integrate more clearly with modeling or code generation
tools.

> Important: Component items are not allowed in raw components.

## Composition

Covalent components can be organized as composites, in the UML composition
sense. A composite component owns one or more sub-components. These
sub-components can themselves be composites, with no fixed depth limit.

Composition is an architectural structuring mechanism. It makes it possible to
build large applications from a small number of root components that represent
major application areas, then refine each area through successive levels of
sub-components down to more focused functional units.

In agreement with UML composition, the lifecycle of a sub-component is tied to
the lifecycle of its composite. A sub-component is instantiated, initialized,
activated, passivated, removed, and undeployed as part of its composite tree.

Sub-components are declared on fields of the composite implementation. Covalent
can use either a single annotation or two distinct annotations:

- `@Implementation` on the sub-component implementation class and on the
  corresponding field in the composite implementation.
- `@Implementation` on the sub-component implementation class and
  `@ComposedComponent` on the corresponding field in the composite
  implementation.

When Covalent instantiates a managed composite, it also resolves its declared
sub-components. If a sub-component field is already initialized, Covalent adopts
that instance. Otherwise, Covalent creates the sub-component with its default
constructor and injects it into the field.

For example:

```java
@Implementation
public class BillingCompositeImpl implements BillingComposite {

    @Implementation // or @ComposedComponent
    private InvoiceComponentImpl invoiceComponent;

    @Implementation
    private PaymentComponentImpl paymentComponent;
}
```

The sub-components are regular Covalent components:

```java
@Implementation
public class InvoiceComponentImpl implements InvoiceComponent, InvoiceServices {
    // ...
}
```

```java
@Implementation
public class PaymentComponentImpl implements PaymentComponent, PaymentServices {
    // ...
}
```

Although items and composed components may look similar, they have different
semantics.

Items are internal parts of one component. They share the responsibility of
implementing that component and may carry some of its ports. They are tightly
coupled to the component implementation.

Composed components are independent components owned by a composite. Each
composed component has its own component type, implementation, ports, lifecycle
state, and runtime descriptor. A composed component can use services, events, and
parameters from sibling components or from any other component, according to the
usual Covalent wiring rules.

Composition structures the application at the architectural level. Items
structure the implementation of a single component.

## Services

### What Is a Service

A service is a request-response interaction between components.

Services are designed for low coupling: service providers and service users are
connected through interfaces. The caller depends on a service contract, not on a
concrete implementation class.

A service is called for the benefit of the caller. The provider acknowledges the
request in one of two ways:

- by returning a result, when the method has a return value;
- by completing normally, when the method returns `void`.

If the provider cannot complete the request, it throws an exception.

In Covalent, a service is represented by a Java method. A services interface
groups several service methods, hence the recommended `Services` suffix on
services interface names.

Service method arguments are free from framework constraints. They can be POJOs,
interfaces, records, value objects, or any other application type. This keeps
business APIs independent from the Covalent runtime.

### Declaring Services

A services interface is declared with the `@Services` annotation.

```java
@Services
public interface MyServices {
    void doSomething(Data someData);
}
```

Each service method can have as many arguments as needed.

> Note: Service method names should not be getters or setters. In Covalent, getters
> and setters belong to the parameter semantics rather than the service
> semantics. This convention is not checked by the verification
> manager.

### Providing Services

A component that implements a services interface is a services provider.

```java
@Implementation
public class MyComponentImpl implements MyComponentType, MyServices {
    @Override
    public void doSomething(final Data someData) {
        // ...
    }
}
```

Each services interface has exactly one provider among the deployed
components, the same single-owner rule and the same conflict handling as
for produced events - see [Producing Events](#producing-events).

### Using Services

To use services, a component declares a field of the services interface type and
annotates it with `@UsedServices` or `@Services`.

```java
@UsedServices // or @Services
private MyServices useMyServices;
```

The container automatically injects the service implementation into this
field during component startup. See [Dependency Injection](foundations.md#what-is-dependency-injection).

A component that declares such a field is a services user.

> Tip: Used services fields should start with `use` and end with the services
> interface name. This naming convention is optional unless structural rule
> verification is set to its strictest level. See
> [Verification Level](#verification-level).

### Extending Services

Services interfaces can use inheritance to follow the Interface Segregation
Principle, or ISP: clients should not depend on methods they do not need.

A common pattern is to separate read-only services from read-write services.
For example, instead of exposing all CRUD operations through one interface, an
application can define a read-only interface for simple clients and a
read-write interface for clients with extended rights.

The read-only interface could be:

```java
@Services
interface DataReadServices {
    Data readData(DataId id);
}
```

The read-write interface could extend it:

```java
@Services
interface DataReadWriteServices extends DataReadServices {
    void createData(DataId id, Data data);

    void updateData(DataId id, Data data);

    void deleteData(DataId id);
}
```

## Events

### What Is an Event

An event is a notification sent by a component for the benefit of other
components.

Services are request-response interactions: the caller asks for something and
expects the provider to complete the request. Events use a different semantic:
the producer announces that something happened and does not expect a result.

An architecture based only on services is called a service-oriented
architecture (SOA). When it also relies on events, it is called an Event-Driven
Service-Oriented Architecture (ED-SOA). Event-driven architectures have long been
used in command-and-control systems (finance, transportation, defense, and
similar domains), and have become widespread with technologies such as event
buses, data streaming, data aggregation, and rich user interfaces. Covalent
components can combine services and events in this way.

Events are designed for very low coupling:

- event producers and event consumers are connected through interfaces;
- an event method returns `void`;
- an event method must not declare exceptions;
- the producer does not know which consumers receive the event;
- exceptions raised by consumers are handled by Covalent and are not returned to
  the producer;
- event dispatch is asynchronous by default, unless configured otherwise;
- consumers should treat event arguments as read-only.

When event arguments are mutable, the producer can ask Covalent to clone them
automatically with the `@Cloned` annotation. Cloning can be configured at the
events interface level, at the event method level, or at the individual argument
level.

In Covalent, an event is represented by a Java method, not by an event object.
An events interface groups several event methods, hence the recommended
`Events` suffix on events interface names.

As with services, event method arguments are ordinary application types. Covalent
does not require event payloads to inherit from framework classes or implement
framework-specific interfaces.

### Declaring Events

An events interface is declared with the `@Events` annotation.

```java
@Events
public interface MyEvents {
    void onSomethingChanged(Object someEventAttribute);
}
```

Each event is a method and can have as many arguments as needed.

> Tip: Event method names should start with `on` and end with a past-tense verb, for
> example `onDataUpdated`. This naming convention is optional unless structural
> rule verification is set to its strictest level. See
> [Verification Level](#verification-level).

### Producing Events

To produce events, a component declares a field of the events interface type and
annotates it with `@ProducedEvents` or `@Events`.

```java
@ProducedEvents // or @Events
private MyEvents produceMyEvents;
```

The component is then an events producer.

Each events interface has exactly one producer among the deployed
components. A second component that also declares `@ProducedEvents` (or
`@Events`) for the same interface never becomes an additional producer -
the first-registered one is kept - and the conflicting declaration is
reported at the configured structural verification level (see
[Verification Level](#verification-level)): ignored at `silent`, logged at
`trace` or `warn`, or rejected outright at `strict`, which is what catches
this at deploy time instead of leaving it to surface later as an unrelated
runtime symptom.

To fire an event, the component calls the event method. The container
dispatches the event to the subscribed consumers.

```java
public void someMethod(DataId id, Data data) {
    // ...
    this.produceMyEvents.onDataUpdated(id, data);
}
```

### Consuming Events

A component that implements an events interface is an events consumer.

```java
@Implementation
public class MyEventsConsumerImpl implements MyEventsConsumer, MyEvents {
    @Override
    public void onDataUpdated(final DataId id, final Data data) {
        // ...
    }
}
```

By default, event consumers automatically subscribe to the producers of the
events they consume during startup. There is nothing else to declare.

### Event Subscription

Most event consumers do not need to manage subscriptions manually. By default,
Covalent automatically subscribes an event consumer to the producers of the
events it consumes during startup.

Manual subscription is useful when a consumer needs explicit control over when it
receives events. This can happen when:

- the component only consumes events during part of its lifecycle;
- the observer is an object owned by the component, not the component itself;
- the component needs to subscribe and unsubscribe several times according to
  application logic.

To subscribe manually, the consumer declares a field of the producer's subscribe
interface type and annotates it with `@UsedEventsSubscribe`.

```java
@UsedEventsSubscribe
private AnEventsSubscribe useAnEventsSubscribe;
```

The event producer does not need to implement subscription storage. The component
manager handles subscription and unsubscription for the producer.

> Tip: Used events subscribe fields should start with `use` and end with the subscribe
> interface name. This naming convention is optional unless structural rule
> verification is set to its strictest level. See
> [Verification Level](#verification-level).

> Note: Subscribing more than once to the same events with the same arguments is
> ignored unless dynamic rule verification is set to its strictest level. See
> [Custom Event Subscription](#custom-event-subscription) and
> [Verification Level](#verification-level).

#### Subscribe Interface Provided by an Events Interface

A subscribe interface must be nested inside its events interface and annotated with
`@EventsSubscribe`. The first parameter of each of its subscribe and unsubscribe
methods (the observer) must be that events interface.

```java
@Events
public interface AnEvents {
    void onEvent(String message);

    @EventsSubscribe
    interface AnEventsSubscribe {
        void subscribe(AnEvents observer);

        void unsubscribe(AnEvents observer);
    }
}
```

The consumer can then declare the subscribe interface and call it when needed.

```java
class EventConsumerImpl implements EventsConsumer, AnEvents {
    @UsedEventsSubscribe
    private AnEventsSubscribe useAnEventsSubscribe;

    public void someMethod() {
        this.useAnEventsSubscribe.subscribe(this);
        // ...
    }
}
```

Covalent intercepts this call and manages the actual subscription.

> Note: For event subscription to work, subscribe methods must be prefixed with
> `subscribe`, and unsubscribe methods must be prefixed with `unsubscribe`.

#### Manual Subscription Without a Specific Subscribe Interface

Manual subscription is still possible when the events interface does not provide
a specific subscribe interface. In that case, the consumer can use
`GenericSubscribe`.

This is allowed only when:

- the producer does not provide a subscribe interface for the consumed events;
- the consumer consumes only one kind of events.

```java
class EventConsumerImpl implements EventsConsumer, AnEventsWithoutSubscribeInterface {
    @UsedEventsSubscribe
    private GenericSubscribe useGenericSubscribe;

    public void someMethod() {
        this.useGenericSubscribe.subscribe(this);
        // ...
    }
}
```

#### Subscribing an Object Instead of the Consumer Component

When a subscribe interface is provided, the consumer component can subscribe
another object instead of subscribing itself. This is useful when the component
owns several observers for the same events.

```java
class EventConsumerImpl implements EventsConsumer {
    @UsedEventsSubscribe
    private AnEventsSubscribe useAnEventsSubscribe;

    public void someMethod() {
        AnEvents observer = new ConsumerObject();
        this.useAnEventsSubscribe.subscribe(observer);
        // ...
    }

    public class ConsumerObject implements AnEvents {
        @Override
        public void onEvent(final String message) {
            // Called each time the producer fires the event.
        }
    }
}
```

#### Subscribe Method Arguments

A subscribe interface follows these rules, checked when Covalent analyzes the
component model:

- it must be nested inside its `@Events` interface, otherwise a structural
  verification problem is reported;
- the first parameter of every subscribe and unsubscribe method, the observer,
  must be exactly that events interface, otherwise a structural verification
  problem is reported for that method;
- its methods are prefixed with `subscribe` or `unsubscribe`; Covalent logs a
  warning for, and ignores, any other method.

See [Verification Level](#verification-level) for what each level does with a
reported problem.

The other parameters depend on what the call does:

| Call | Effect |
| --- | --- |
| `subscribe(observer)` | Subscribes the observer to every event. |
| `subscribe(observer, filter)` | Subscribes the observer; the events the filter blocks are not delivered to it. The filter is an instance of the `@EventsFilter` interface of the events (see [Event Filters](#event-filters)). |
| `subscribe(observer, subscribeParameters)` | Subscribes the observer with the options carried by a `SubscribeParameters`, such as a filter. See [Subscribe to Events](raw-components.md#subscribe-to-events). |
| `subscribe(observer, ...)` with any other arguments | A custom subscription: the arguments only reach the producer. See [Custom Event Subscription](#custom-event-subscription). |
| `unsubscribe(observer)` or `unsubscribe(observer, ...)` | Ends the subscription made with the same observer and the same other arguments. |

A few points follow from how Covalent reads these calls:

- A filter or a `SubscribeParameters` is only recognized as the second argument
  of a call that has exactly two arguments. Anywhere else, in a call with more
  arguments for instance, it is a custom argument like any other: it reaches the
  producer, but it filters nothing.
- Covalent finds the method a call targets from the runtime types of its
  arguments. A primitive parameter accepts the matching boxed value, so
  `subscribe(observer, 3, "news")` can target `subscribe(Events, int, String)`.
- A `null` argument matches any parameter type. A `null` filter or a `null`
  `SubscribeParameters` means "none": the subscription is plain. Because Covalent
  cannot tell which overload a `null` is meant for, avoid it when the subscribe
  interface declares several overloads with the same number of parameters.
- A subscription is identified by its observer and its other arguments,
  compared with `equals`. To end it, `unsubscribe` must be called with the same
  arguments as the `subscribe` call, which means the subscribe interface must
  declare an unsubscribe method taking them. In particular, `unsubscribe(observer)`
  does not end a subscription made with a filter or a custom argument: the call
  is ignored, the events keep being delivered, and the mismatch is reported only
  when dynamic rule verification is set to its strictest level.

```java
@EventsSubscribe
interface FilteredEventsSubscribe {
    void subscribe(FilteredEvents observer);

    void subscribe(FilteredEvents observer, FilteredEventsFilter filter);

    void unsubscribe(FilteredEvents observer);

    // Needed to end a subscription made with a filter.
    void unsubscribe(FilteredEvents observer, FilteredEventsFilter filter);
}
```

### Event Subscription Delegation

Some event producers need to be notified when a consumer subscribes to their
events.

The main use case is state initialization. A model component can send its current
state when a consumer subscribes, so the consumer can build a consistent local
state before receiving later business events.

This mechanism is called event subscription delegation. The delegation is only
partial: the container still registers the consumer and dispatches
events. The producer is only notified that a subscription or unsubscription
occurred.

To enable subscription delegation, the producer implements the events subscribe
interface.

```java
@Implementation
public class ProducerWithDelegationImpl implements ProducerWithDelegation, AnEventsSubscribe {
    @ProducedEvents
    private AnEvents produceAnEvents;

    @Override
    public void subscribe(final AnEvents subscriber) {
        // Send the current state to the new subscriber if needed.
    }

    @Override
    public void unsubscribe(final AnEvents subscriber) {
        // React to unsubscription if needed.
    }
}
```

Covalent calls these methods when a consumer subscribes or unsubscribes, whether
the subscription is automatic or manual.

The producer receives exactly the call the consumer made: the same method, with
the same arguments in the same order, custom arguments included (see [Custom
Event Subscription](#custom-event-subscription)). The observer is the one
exception: it is a Covalent proxy that delivers the events to the consumer, not
the consumer object itself, so a producer should not rely on its identity.

Subscriptions are replayed. When the producer is passivated and activated again,
Covalent makes the same subscribe calls again, with the same arguments, a filter
being the same instance and still applying. The producer can then send its state
again, and the consumers do not have to subscribe a second time.

With asynchronous events, the producer is notified on an events worker thread, and
the subscribing call does not wait for it. Until the producer's `subscribe` method
returns, Covalent keeps the events fired for this consumer, including those the
producer sends from that method, and delivers them in the order they were fired
once it has returned. The consumer receives them like any other events.

This buffer belongs to the subscription and holds at most as many events as the
dispatch queue (`setWorkerQueueCapacity`, `1024` by default). Beyond that, an event
is dropped for this consumer only: it is logged and counted as a rejected
notification, and the producer is neither blocked nor failed. A `subscribe` method
that stalls, or a producer faster than it, can therefore lose events, so keep the
method short. If the method throws, the subscription is cancelled and the buffered
events are discarded. With synchronous events, the method runs on the calling
thread, before the subscribing call returns.

### Event Filters

Covalent events can provide filters. A filter belongs to an events interface and
decides, for one subscriber, whether a produced event should be delivered.

> Note: A filter method returning `true` means the event is filtered OUT: it is
> NOT delivered. This is the opposite convention from `Stream.filter`, where
> `true` keeps the element. It matches `Collection.removeIf`, where `true` means
> "remove this one" instead. The same convention applies identically to raw
> event filters (see [Specify an Events Filter](raw-components.md#specify-an-events-filter)).

A filter is declared as an interface nested inside the corresponding events
interface, with the `@EventsFilter` annotation. Like a subscribe interface, it is
part of the events contract.

> Tip: The filter interface name should be the events interface name suffixed with
> `Filter`. For managed components, this naming rule is mandatory.

> Important: Each filter method must match one event method. Its name must be the event
> method name with `on` replaced by `filter`, and it must take the exact same
> arguments.

Filtered events require explicit subscription:

- the events interface must provide a subscribe interface;
- the subscribe interface must provide a subscribe method whose second argument
  is the filter;
- automatic subscription cannot be used, because the consumer must pass a filter
  instance explicitly.

```java
@Events
public interface FilteredEvents {
    void onEvent(Data data);

    @EventsSubscribe
    interface FilteredEventsSubscribe {
        void subscribe(FilteredEvents observer);

        void subscribe(FilteredEvents observer, FilteredEventsFilter filter);

        void unsubscribe(FilteredEvents observer);
    }

    @EventsFilter
    interface FilteredEventsFilter {
        boolean filterEvent(Data data);
    }
}
```

To subscribe with a filter, the consumer calls the subscribe method that accepts
a filter instance.

```java
@Implementation
public class EventsConsumerWithFilterImpl implements EventsConsumerWithFilter, FilteredEvents {
    @UsedEventsSubscribe
    private FilteredEventsSubscribe useFilteredEventsSubscribe;

    private final FilteredEventsFilter eventsFilter = new EventsFilter();

    private void subscribeWithFilter() {
        this.useFilteredEventsSubscribe.subscribe(this, this.eventsFilter);
    }

    @Override
    public void onEvent(final Data data) {
        // ...
    }

    private final class EventsFilter implements FilteredEventsFilter {
        @Override
        public boolean filterEvent(final Data data) {
            if (data == null) {
                return true; // The event is filtered out and will not be delivered.
            }

            return false; // The event is accepted and will be delivered.
        }
    }
}
```

Covalent calls the filter before delivering the event to this subscriber. The
event is delivered only when the filter method returns `false`.

The filter is specific to the subscription, not to the producer:

- if a subscribe method without a filter is available, another consumer can
  subscribe and receive all events;
- another consumer can subscribe with a different filter instance and receive
  events under different conditions.

> Warning: When a subscribe method accepts a filter, the filter must be the second method
> argument, in a call that has exactly two arguments. A filter anywhere else is only a
> custom argument: it reaches the producer, but it filters nothing. The filter must be
> an instance of a class implementing the `@EventsFilter` interface; a lambda is enough.

> Note: A filtered subscription is ended by an `unsubscribe` call that passes the same
> filter, not by `unsubscribe(observer)` alone. See
> [Subscribe Method Arguments](#subscribe-method-arguments).

> Note: If the event producer implements subscription delegation, it must implement the
> subscribe method that accepts a filter argument. The producer should ignore the
> filter, because Covalent handles filtering automatically.

### Event Data Cloning

Covalent can automatically clone event arguments before delivering them to
consumers. This protects the fire-and-forget model: a consumer should not be able
to modify the objects received from the producer.

Automatic cloning is declared with the `@Cloned` annotation.

Cloning can be requested at three levels:

- on the events interface, to clone all arguments of all event methods;
- on an event method, to clone all arguments of that method;
- on an event method argument, to clone only that argument.

> Important: Automatic cloning only works for data that can actually be cloned. The data
> must implement the JDK `Cloneable` interface and expose a usable clone
> operation. If cloning fails, the application continues to run unless dynamic
> rule verification is set to its strictest level. See
> [Verification Level](#verification-level).

Arrays are cloned too, whatever their element type. As with the `clone()` of any
Java array, the copy is shallow: the elements themselves are shared with the
original array. A consumer that receives an array of mutable objects can still
modify those objects, and the producer will see the change.

#### Interface-Level Cloning

```java
@Events
@Cloned // this events interface declares to use automatic cloning when possible
public interface ProducerWithClonedEvents {
    // NotCloneableData does not implement @Cloneable
    void onEvents(CloneableData cloneableData, NotCloneableData notCloneableData);
    // ...
}
```

Covalent tries to clone every argument of every event method. In this example,
only `cloneableData` can be cloned.

#### Method-Level Cloning

```java
@Events
public interface ProducerWithClonedMethodEvents {
    @Cloned // this method declares to use automatic cloning when possible
    void onEvents(CloneableData cloneableData, NotCloneableData notCloneableData);
}
```

Here again, only `cloneableData` can be cloned.

#### Argument-Level Cloning

```java
@Events
public interface ProducerWithClonedAttributeEvents {
    void onEvents(@Cloned CloneableData cloneableData, NotCloneableData notCloneableData);
}
```

Only the annotated argument is cloned. In this case, Covalent does not even try
to clone `notCloneableData`.

`@Cloned` is a property of the events interface itself, independent of which
component produces it: a raw event producer (see [Produce Events](raw-components.md#produce-events)) honors `@Cloned` exactly like a
managed producer, at all three levels above.

### Synchronous or Asynchronous Events

By default, Covalent events are asynchronous. This prevents the producer from
being blocked by event consumers.

For debugging, tests, or workflows where asynchronous dispatch is not needed, the
container can be configured to dispatch events synchronously.

```java
Covalent.main.getParameters().setSynchronousEvents(true);
```

### Asynchronous Event Dispatch Configuration

Asynchronous event dispatch runs on an internal worker pool, sized and bounded
through `EventsManagerParameters`.

```java
@AccessedParameters // or @Parameters
private EventsManagerParameters accessEventsManagerParameters;

this.accessEventsManagerParameters.setWorkerCount(8);
this.accessEventsManagerParameters.setWorkerQueueCapacity(2048);
```

By default, the worker count is the number of available processors, at most `4`,
and the queue capacity is `1024`. A configuration change applies immediately to the
running worker pool: added workers start at once, while surplus workers finish
their current delivery before terminating. A reduced capacity keeps the
notifications already queued; new ones are dropped until the queue is back
under it.

Event delivery stays strictly fire-and-forget for the producer, even when the
dispatch queue is full: the producing call never throws and is never blocked
by it. Instead, the notification that could not be queued is dropped and
logged at `SEVERE`. A dropped notification is always a sign that consumers
cannot keep up with producers - there is no throttling or backpressure
mechanism on event production - so it should be treated as an
application-level problem to investigate, not as routine behavior.

Asynchronous events are therefore best-effort under overload: the loss is visible
(`SEVERE` log, `degraded` shutdown report, supervision), but the producer is not
slowed down and nothing is retried. When a flow cannot tolerate any loss, do not carry
it on asynchronous events. Use a synchronous service call, whose result the caller
checks, or a queue owned by the application. When only some flows are critical, a
dedicated channel is a fallback: give the critical flow its own queue and thread, owned
by the consuming component, instead of the shared worker pool. This does not guarantee
the absence of loss, but it protects that flow from other producers saturating the
shared pool.

> Note: A dropped notification is also reflected in the container's `ShutdownReport`:
> `stop()` reports a `degraded` status rather than `healthy` if any notification
> was dropped since the container last started, the same way it does when an
> event consumer callback does not honor interruption during shutdown. See
> [Stopping a Covalent Application](#stopping-a-covalent-application).

### Threading Model

Covalent does not create an application-level threading model of its own; it
uses a small, fixed number of thread pools and otherwise runs on the caller's
thread.

- **Deployment and startup.** `deployComponents(...)` and `start()` run
  entirely on the calling thread, sequentially. Lifecycle callbacks
  (`@Initialization`, `@Activation`, ...) run on that same thread, in the
  order the vertical or horizontal start mode calls them.
- **Services.** A service call is a plain synchronous method call on the
  caller's thread; Covalent never switches threads for it.
- **Synchronous events.** With `setSynchronousEvents(true)` (or
  `EventsManagerParameters.setSynchronous(true)`), a consumer's event method
  runs directly on the producer's thread, in the order the producer fired the
  events.
- **Asynchronous events (the default).** Each event notification is submitted
  to the internal worker pool described above. With the default worker count
  greater than one, two notifications - even from the same producer, even for
  the same consumer - can run concurrently on different worker threads, and
  nothing orders their execution relative to each other or to the producer's
  own thread after the call returns. Setting the worker count to `1` serializes
  dispatch (first-in-first-out), at the cost of giving up concurrency.
- **Asynchronous services** (`AsyncServices`, see [Asynchronous
  Services](#asynchronous-services)) follow the same rule: no ordering across
  tasks unless they share a `queueKey`, which serializes them on the same
  dedicated single thread.

### Custom Event Subscription

Covalent supports custom subscribe and unsubscribe methods with additional
arguments besides the observer and optional filter.

Custom arguments can be any number of objects or primitive values. They do not
change which events are delivered, since only a filter does that. They tell the
producer something about the subscription, such as the topic whose current state
it should send. If the producer implements the subscribe interface (see [Event
Subscription Delegation](#event-subscription-delegation)), it receives them
unchanged and in order, and Covalent passes them again when it replays the
subscription after a passivation.

```java
@Events
public interface NewsEvents {
    void onNews(String headline);

    @EventsSubscribe
    interface NewsEventsSubscribe {
        void subscribe(NewsEvents observer, String topic);

        void subscribe(NewsEvents observer, int priority, String topic);

        void unsubscribe(NewsEvents observer, String topic);

        void unsubscribe(NewsEvents observer, int priority, String topic);
    }
}
```

```java
@Implementation
public class NewsDeskImpl implements NewsDesk, NewsEvents.NewsEventsSubscribe {
    @ProducedEvents
    private NewsEvents produceNewsEvents;

    @Override
    public void subscribe(final NewsEvents observer, final String topic) {
        // Send the current headlines of this topic to the new subscriber.
    }

    @Override
    public void subscribe(final NewsEvents observer, final int priority, final String topic) {
        // Same, knowing the priority the subscriber asked for.
    }

    // The unsubscribe methods are implemented the same way.
}
```

A consumer ends a custom subscription with the same arguments it subscribed with:
`subscribe(this, "news")` is ended by `unsubscribe(this, "news")`.

With custom subscription, a consumer can subscribe several times to the same
event producer, as long as the custom arguments are different. This follows the
same principle as filtered subscriptions, where a consumer can subscribe to the
same producer with different filter instances.

Covalent checks whether the same custom subscription has already been registered
with the same arguments. If it has, the second subscription is ignored unless
dynamic rule verification is set to its strictest level.

> Caution: Custom subscribe arguments must have consistent `equals` implementations. If
> all arguments compare equal between two subscribe calls, Covalent considers the
> second call to be a duplicate subscription. If at least one argument compares
> different, Covalent considers it to be a distinct subscription. An exception is
> thrown only when dynamic rule verification is set to its strictest level. See
> [Verification Level](#verification-level).
>
> The same comparison finds the subscription to end when `unsubscribe` is called, so
> a lambda or any object without a meaningful `equals` can only be unsubscribed by
> passing the very same instance.

## Parameters

### What Is a Parameter

Parameters are request-response interactions used to configure a component.

They are close to services, but their intent is different. A service call is made
for the benefit of the caller. A parameter call configures the provider, so it is
made for the benefit of the component that exposes the parameter.

Parameters are connected earlier than services in the component lifecycle. This
allows a component to access parameters during initialization, before it is
activated. They also stay available while the component is passivated, as they were
after initialization. See [Lifecycle](#lifecycle).

In Covalent, a parameter is represented by a Java method. A parameters interface
groups several parameter methods, hence the recommended `Parameters` suffix on
parameters interface names.

Parameter method arguments are ordinary Java objects.

### Declaring Parameters

A parameters interface is declared with the `@Parameters` annotation.

```java
@Parameters
public interface MyParameters {
    void setParam(Param parameter);

    Param getParam();
}
```

Each parameter is typically exposed through a setter method. A corresponding
getter can also be provided when needed.

> Note: Parameter method names should be getters and setters. This convention is not
> checked by the verification manager. For that reason, parameter
> methods can technically have more than one argument, although this is not
> recommended.

### Offering Parameters

A component that implements a parameters interface offers parameters.

```java
@Implementation
public class MyComponentImpl implements MyComponentType, MyParameters {
    @Override
    public void setParam(final Param parameter) {
        // Store or apply the provided configuration value.
    }

    @Override
    public Param getParam() {
        // Return the current configuration value.
        return this.param;
    }
}
```

Each parameters interface has exactly one offeror among the deployed
components, the same single-owner rule and the same conflict handling as
for produced events - see [Producing Events](#producing-events).

### Accessing Parameters

To access parameters, a component declares a field of the parameters interface
type and annotates it with `@AccessedParameters` or `@Parameters`.

```java
@AccessedParameters // or @Parameters
private MyParameters accessMyParameters;
```

The container automatically injects the parameters implementation into
this field during component startup. See
[Dependency Injection](foundations.md#what-is-dependency-injection).

A component that declares such a field is a parameters accessor.

> Tip: Accessed parameters fields should start with `access` and end with the
> parameters interface name. This naming convention is optional unless structural
> rule verification is set to its strictest level. See
> [Verification Level](#verification-level).

## Lifecycle

A managed component normally goes through seven lifecycle transitions:

- **Deployment**: the component implementation is introspected and its metamodel
  is created.
- **Instantiation**: the component classes are instantiated. A monolithic
  component has one instance; a segmented component can have several instances,
  including its items.
- **Initialization**: the component is prepared for use. This is the recommended
  transition for accessing parameters, configuring the component, and preparing
  resources. Initialization happens only once during the component lifetime.
- **Activation**: the component's provided services and produced events are
  published and can be used by other components. Its offered parameters are
  already available, since initialization. Activation can happen several times,
  after passivation. Activation
  callbacks should therefore remain fast. Manual event subscription is a typical
  activation task.
- **Passivation**: the component's provided services and produced events are
  unpublished and can no longer be used by other components. Its offered
  parameters stay available, as they were after initialization, so that the
  component can still be configured while it is passivated. The component can be
  activated again later.

  A passivated component keeps its used services, accessed parameters, and event
  subscriptions connected: it is only its own *provided* services and *produced*
  events that other components can no longer reach, while its offered parameters
  remain reachable. This is deliberate, not an oversight. A
  passivated producer sends nothing - firing an event while passivated is a
  no-op, silently discarded - but a passivated *consumer* keeps receiving events
  from active producers, and services or parameters it uses keep working, exactly
  as when it was active. This lets a passivated component keep itself up to date
  while withdrawn, so it can switch back to activated quickly - for example, a
  standby instance that is kept passivated but ready to take over.
- **Removal**: the component releases what instantiation and initialization
  created: its instances, and all its ports, provided or used. It stays deployed,
  ready to be instantiated and initialized again, and then activated. Its provided
  ports are not usable until it is activated again.
- **Undeployment**: the component instances are removed from the registry. No
  trace of the component remains in the container.

<img src="architecture/core/lifecycle-light.svg#gh-light-mode-only" alt="State diagram of a component's lifecycle: deployed, instantiated, initialized, activated, passivated and removed, with a failed state reachable from every state before removal and a reset back to deployed" width="720">
<img src="architecture/core/lifecycle-dark.svg#gh-dark-mode-only" alt="State diagram of a component's lifecycle: deployed, instantiated, initialized, activated, passivated and removed, with a failed state reachable from every state before removal and a reset back to deployed" width="720">

The first six transitions have an associated lifecycle state: `deployed`,
`instantiated`, `initialized`, `activated`, `passivated`, and `removed`. A
seventh state, `failed`, is reached when any transition fails, and left by a reset.

A failed transition from instantiation through removal puts the entire owning
application composite in `failed`. Covalent attempts technical port and instance
cleanup without invoking application passivation or removal methods. Cleanup
errors are logged; they do not prevent the transition to `failed`.
`LifecycleEvents.onComponentFailed(componentType)` reports each affected component
after `postFail`. Successful transition notifications are likewise emitted only
after their technical post-processing and state update.

Failed managed components are skipped by global startup. After investigating the
cause, call `LifecycleServices.resetFailedComponent(componentType)` to retry cleanup
and return the complete composite to `deployed`. Reset does not start it.
A later explicit activation creates fresh instances. Cleanup failure prevents
reset. During container shutdown, failed components can instead be removed and
undeployed. Invalid lifecycle requests against healthy components do not mark
them failed. Internal components are outside this application recovery policy.

For a registered raw component, `RawComponentsImpl` receives
`onComponentFailed` synchronously, including when application events are
asynchronous. It cleans that component's manually declared ports and subscriptions,
then removes and undeploys it. A cleanup failure is logged and leaves the component
failed. The external owner remains responsible for its instance and decides whether
to register it again. Direct lifecycle calls still propagate the original failure.
The lifecycle manager does not depend on raw-component cleanup services.

Lifecycle operations and state reads are serialized per container. A concurrent
caller waits for the current operation, then checks the resulting state. Two
simultaneous `start()` calls therefore do not start the container twice: the second
call waits and reports `Already started`. Different containers remain independent.
Startup reporting and the synchronous extension startup call share this protection;
it does not wait for asynchronously scheduled UI work.

The lock is reentrant: nested calls from the same thread, including RAW cleanup
from lifecycle events, remain possible. Application lifecycle methods and
synchronous event consumers must not wait for another thread that calls lifecycle
services on the same container, because that thread is waiting for the operation
to finish. This guarantee concerns lifecycle operations, not arbitrary application
service calls or a sequence of separate RAW API calls.

For a composite, deployment proceeds component by component, parent before
children. Each component's metamodel is created and its deployment notification
is published before proceeding to its children. A parent's deployment notification
therefore does not mean that its entire subtree is already deployed. Undeployment
proceeds children before parent, removing each component's metamodel and deployment
registration individually.

The current state of a component can be read through lifecycle services.

```java
@Services
private LifecycleServices useLifecycleServices;

// ...

// MyComponent is the component type.
ComponentState myComponentState =
        this.useLifecycleServices.readComponentState(MyComponent.class);
```

Lifecycle services can also move a component to a target state. For example, to
bring a component to the initialized state:

```java
private final CovalentContainer covalentContainer = Covalent.main;

// ...

// First, deploy the component implementation.
this.covalentContainer.deployComponents(AComponentImpl.class);

// Then instantiate and initialize the component type.
this.useLifecycleServices.initializeComponent(AComponent.class);
```

Lifecycle services also provide methods that apply to all deployed components.
For example, to activate all deployed components:

```java
this.useLifecycleServices.activate();
```

### Lifecycle Callbacks

A component can define callbacks for the main lifecycle transitions:
initialization, activation, passivation, and removal.

Callbacks allow the component to run its own transition logic, such as preparing
resources during initialization or subscribing to events during activation.

There are three ways to declare lifecycle callbacks.

#### Callback Method Annotations

Any method can be used as a callback method when it is annotated with one of the
lifecycle annotations:

- `@Initialization` for the initialization callback;
- `@Activation` for the activation callback;
- `@Passivation` for the passivation callback;
- `@Removal` for the removal callback.

#### Callback Interfaces

A component can also implement the corresponding lifecycle interfaces:

- `Initializable` for the initialization callback;
- `Activable` for the activation callback;
- `Passivatable` for the passivation callback;
- `Removable` for the removal callback.

Alternatively, the component can implement `ComponentLifecycleTransition` to
declare all four lifecycle callbacks in one interface.

### Callback Method by Naming Convention

Lifecycle callbacks can also be declared by naming convention.

This mechanism is disabled by default. To enable it, set
`findLifecycleMethodByNameConvention` to `true` through lifecycle parameters.

```java
@AccessedParameters // or @Parameters
private LifecycleParameters accessLifecycleParameters;

// ...

this.accessLifecycleParameters.setFindLifecycleMethodByNameConvention(true);
```

The expected method names are:

- `initializeComponent` for initialization callback method;
- `activateComponent` for activation callback method;
- `passivateComponent` for passivation callback method;
- `removeComponent` for removal callback method.

### Vertical and Horizontal Start Modes

The lifecycle manager supports two start modes: vertical and horizontal. The
default start mode is vertical.

In vertical mode, each component is fully started before the next one. For each
component, Covalent runs instantiation, initialization, and activation.

In horizontal mode, all components reach the same lifecycle state before Covalent
moves to the next state. All components are instantiated first, then all
components are initialized, then all components are activated.

Both modes are useful:

- horizontal mode can make activation faster when all components have already
  been initialized;
- vertical mode allows a component to use services or subscriptions from
  components that were started before it during its own initialization.

Parameters are available during initialization in both modes: offered parameters
are created, activated, and published after instantiation, and accessed
parameters are prepared before initialization. Services and event subscriptions
are different. In horizontal mode, a component should not use services or
subscriptions from other components during initialization, because those ports
are prepared and published later, around activation.

Fine-grained lifecycle methods can be used when an application needs a specific
startup order. For example, some components can be activated first, then the
remaining components can be started in horizontal mode.

Lifecycle methods move components through missing intermediate states when
needed. For example, `activateComponent` first checks whether the component is
initialized. If it is not, `initializeComponent` is called first. Likewise,
`initializeComponent` first checks whether the component is instantiated.

Deployment is not performed automatically. It must be done explicitly first,
because deployment targets implementation classes, while later lifecycle
transitions target component types.

Covalent also provides dependency surrogates to reduce startup-order coupling
for injected used ports. When a used service, accessed parameter, or
`@UsedEventsSubscribe` port is not published yet, Covalent can inject a
surrogate instead of the real port. For services and parameters, the surrogate
looks up the real port and replaces itself in the component field when the
component later uses the dependency.

A used service or accessed parameters link belongs to the component that
declares it. It stays in place when its provider is passivated, removed or
undeployed, and it reaches the next provider of the interface as soon as there
is one, even a different implementation or a new instance, with nothing to
declare again. While no provider is available, a call fails with an
`IllegalStateException` that says so ("No provider is currently declared for
the services interface ..."), or that the provider is passivated. The link only
ends when the component that uses it is removed.

Automatic event subscriptions and direct raw subscriptions have an additional
startup-order rule: a consumer can subscribe before the producer is published.
Covalent records the consumer as pending and automatically attaches it when the
producer becomes ready.

A manually injected `@UsedEventsSubscribe` port uses a surrogate for a different
reason: the surrogate lets Covalent inject the subscribe port before the
producer-side subscribe proxy is published. The actual subscription still goes
through the real producer-side subscribe proxy once it is available.

A surrogate does not make an unavailable service or parameter usable before its
component is activated. For example, component A can be activated before
component B and still receive a surrogate for B services, but A cannot
successfully use B services until B is activated.

> Note: Controlling dependencies is an important characteristic of good architecture.
> Whenever possible, prefer explicit dependency control over relying on
> surrogates.

### Circular Dependency Detection

Circular dependencies between managed components are safe by construction, as
described above: startup order does not affect whether wiring succeeds.
Covalent can additionally detect and report a circular dependency explicitly,
and order component activation by the computed dependency graph, as two
independent opt-in behaviors sharing the same underlying computation.

```java
Covalent.main.getParameters().setCircularDependencyChecking(true);
Covalent.main.getParameters().setDependencyOrderedActivation(true);
```

Both disabled by default, since computing the graph is extra work. Either
flag alone is enough to make Covalent compute it; enabling one does not
require the other. Both are read live at the very start of every `start()`
call, over whichever managed components are already deployed by then; a
component deployed later, after `start()` has begun, is not covered. Raw
components are never covered either: wiring their own dependencies, cycles
included, directly through `RawComponentsServices` is part of the freedom
they trade the managed model's guardrails for.

Enabling `setCircularDependencyChecking` only turns the computation on; it
does not by itself decide what happens when a cycle is found. That is driven
by the structural verification level (see [Verification
Level](#verification-level)): at `silent`, a found cycle is not even
reported; at `trace` or `warn`, it is logged at that level and `start()`
proceeds regardless; at `strict`, `start()` throws before starting anything,
leaving the container unstarted rather than starting components in an
unreliable order.

Enabling `setDependencyOrderedActivation` makes Covalent reorder the deployed
component lists - the top-level list and, within each composite, its own
composed children - to match the computed start order, once, right after the
graph is computed and before anything is instantiated. This only happens when
a graph was actually computed and it is free of circular dependencies: when a
cycle exists, the computed order is not trusted enough to be applied, and
declaration/deployment order is kept instead, whatever verification level
`setCircularDependencyChecking` resolves to for that same `start()` call. A
component the graph does not cover - deployed after `start()` began, or a raw
component - keeps its original relative position rather than being dropped,
duplicated, or moved.

The computed graph is exposed through `DependencyGraphServices`, a regular
Covalent service port:

```java
@UsedServices
private DependencyGraphServices dependencyGraphServices;

// ...

if (this.dependencyGraphServices.hasDependencyGraph()) {
    DependencyGraph graph = this.dependencyGraphServices.getDependencyGraph();
    List<Class<?>> startOrder = graph.getStartOrder();
    boolean hasCycles = graph.hasCircularDependencies();
    List<List<Class<?>>> cycles = graph.getCircularDependencies();
}
```

The covered component types are the deployed top-level types and their
composed descendants. `getDependencies()` returns, for each covered component
type, the types it directly depends on through its ports. `getCompositions()`
returns, apart from them, each composite and its direct composed children.

The start order and the circular dependencies are computed from both. A
composite is started only once all of its composed children are, so:

- a composite comes after its composed children;
- depending on a component held by a composite means depending on that whole
  composite: the dependency is carried up to the ancestor of the target that
  sits directly under the closest composite shared with the dependent
  component, or to the target's top-level composite when they share none;
- a composed component using a port of a composite that holds it does not
  make that composite wait for anything else, and is not a cycle.

`getStartOrder()` returns every covered component type positioned so that a
type never appears before a type it depends on, nor a composite before its
composed children, except among components that share a circular dependency,
where no such order can exist by definition - those components still get a
best-effort position rather than being dropped from the list.

`getCircularDependencies()` returns each cycle as a complete path, closing
from its last component back to its first. A step `X -> Y` of a cycle reads as
follows: when `X` composes `Y`, it is a composition step; otherwise, `X`
depends on `Y` or on one of its composed descendants. For example, with `A`
composing `A1` and `A2`, `B` composing `B1` and `B2`, `A1` depending on `B2`
and `B1` depending on `A2`, neither `A` nor `B` can be started first, and the
cycle is `A -> A1 -> B -> B1 -> A`.

> Note: `start()`'s own vertical or horizontal cascade, described above, is
> unchanged either way - `setDependencyOrderedActivation` only changes the
> order in which components are fed into that cascade, never the cascade
> mechanism itself.

The graph is computed via a three-color depth-first traversal. This is
implemented recursively: an extremely long, purely linear chain of
dependencies (tens of thousands of components deep) could exhaust the JVM
call stack. Ordinary deployments never come close to that depth.

## Covalent Application Lifecycle

### Starting a Covalent Application

The simplest way to start an application with Covalent is:

1. deploy the component implementations;
2. start the container.

```java
public class ApplicationLauncher {
    private final CovalentContainer covalentContainer = Covalent.main;
    // The main container is also available through the static Covalent.main field.

    public void start() {
        this.covalentContainer.deployComponents(
                ComponentAImpl.class,
                ComponentBImpl.class,
                ComponentCImpl.class,
                ComponentDImpl.class,
                ComponentEImpl.class,
                ComponentFImpl.class);

        this.covalentContainer.start();
    }
}
```

When more control is required, lifecycle services can be used as many times as
needed before starting the container. Once the required components are
ready, call `start()` on the container. The method returns a `StartupReport`
with the startup status (`healthy`, `degraded`, or `failed`), the startup
duration, application component counts by state, and failed components.

```java
StartupReport report = this.covalentContainer.start();

report.getStatus();                 // ContainerStatus: healthy, degraded, or failed
report.getDurationSeconds();        // how long start() took
report.getComponentCounts();        // Map<ComponentState, Integer>
report.getApplicationComponentCount(); // total application components covered
report.getFailedComponents();       // List<Class<?>>, empty when nothing failed
```

`degraded` means the container itself came up but at least one application
component did not, or a requested extension failed to start (the failure is
logged, the other extensions and the container start anyway): nothing about a `healthy` startup guarantees every
declared component actually activated, so an application that must know
should check the report rather than assume `start()` returning normally means
full success. `failed` means an internal (technical) component itself could
not activate - a container in that state should not be relied upon.
`stop()` returns the symmetrical `ShutdownReport`, with the same shape.
It also lists, in `getResidues()`, what the shutdown left behind in the
container's internal components on behalf of torn-down components (for
example a producer, provider or subscriber that is still declared). The list
is empty when the container is back in the state of a new one; otherwise the
status is `degraded`, a warning is logged, and the application most likely did
not release something it set up through the raw components services.

> Note: The Covalent container is itself implemented with Covalent components.
> It is a composite made of internal technical components, deployed on first
> access to the container and never undeployed.
>
> These internal components provide services, parameters, and events interfaces
> that can be used like regular Covalent component ports. For example, a
> component that needs to observe lifecycle transitions can subscribe to
> `LifecycleEvents`. If it also needs deployment and undeployment notifications,
> it can subscribe to `DeploymentEvents`.
>
> Internal components are identified with the `@Internal` annotation. This
> protects them from generic lifecycle methods that target application
> components.

### Stopping a Covalent Application

To stop the application, call `stop()` on the container. The method returns a
`ShutdownReport` with the same status and component summary shape as startup.

```java
this.covalentContainer.stop();
```

All deployed application components are moved from their current state to
undeployment, either vertically or horizontally depending on the component
manager start mode. This applies uniformly to managed and raw components:
both are torn down through the same deployer-driven cascade, so a raw
component's published services, offered parameters, and produced or
subscribed events are unpublished exactly as a managed component's
declarative ports are - nothing manual is required on the raw side to
match the managed teardown.

After `stop()`, the container is clean and can be used to deploy new
components and start again.

### Restarting a Covalent Application

The container's `start()`/`stop()` pair is an infrastructure-level operation,
not a functional one. It exists so that a whole container can be brought down
and back up - for recovery, for tests, for an orderly shutdown - not as a
tool for day-to-day component management. For ordinary functional needs
(temporarily withdrawing a component's ports, bringing it back later), use
`LifecycleServices` to activate and passivate individual components while
the container keeps running; `stop()`/`start()` should not be called as part
of that flow.

The container can be stopped and started again any number of times. Each
`stop()` puts it back in exactly the state it was in before its very first
`start()`. Internal (technical) components - the ones providing services such
as deployment, the metamodel, raw component registration, asynchronous tasks
or event dispatch - are active from the moment the container is constructed
until it is disposed, and `start()`/`stop()` do not affect them: ports can be
used, and raw components registered, before the first `start()` as well as
between a `stop()` and the following `start()`.

The worker threads of the asynchronous services and of the asynchronous event
dispatch follow the business activity: `start()` starts them ahead, `stop()`
cancels the pending tasks and notifications, interrupts the running ones and
stops every thread, and a business component active while the container is not
started gets its threads on demand, at its first task or asynchronous
notification. A stopped or new container has no worker thread.

A raw component's ports are closed when the component is removed, by the
`stop()` cascade in particular: a raw component observing the container no
longer receives anything once it is removed itself.

### Container Lifecycle Events

A container notifies its own lifecycle through `ContainerLifecycleEvents`.
Each notification carries the container's name (`getName()`, `null` for an
unmanaged container).

| Notification | Emitted when |
|---|---|
| `onContainerStarting` | `start()` is accepted, before anything is started |
| `onContainerStarted` | the start transition completed, with the `StartupReport` |
| `onContainerStartFailed` | the start transition was refused as a whole, with the failure |
| `onContainerStopping` | `stop()` is accepted, every business component still in place |
| `onContainerStopped` | the stop transition completed, with the `ShutdownReport` |
| `onContainerDisposing` | `dispose()` is called on a container not yet disposed |

#### Transition outcome and report status

Two questions are answered separately: did the transition take place, and how
well did the components go through it.

- **Start.** A start can be refused as a whole: in strict mode, a circular
  dependency detected before any component is started. The container then
  stays not started, as it was before the call, `onContainerStartFailed` is
  notified and the failure is thrown to the caller of `start()`. Otherwise the
  container ends started and `onContainerStarted` is notified, even when some
  components failed: their failures are isolated and reported in the
  `StartupReport`.
- **Stop.** A stop is never refused once accepted: the container always ends
  stopped, in the state of a new one. Components failing to stop are isolated
  and reported in the `ShutdownReport` (healthy, degraded or failed), carried by
  `onContainerStopped`. There is therefore no stop failure notification.

An observer thus learns the outcome of every accepted transition: started or
start failed, then stopped. The quality of that outcome is in the report, not
in the choice of notification.

This holds even for an unexpected failure, outside the components (the worker
pools failing to empty, the report failing to build): the transition is still concluded, by
`onContainerStarted` or `onContainerStopped` with a report whose status is
`failed`, and the failure is then thrown to the caller.

A call refused before the transition is accepted (`start()` on a started
container, `stop()` on a stopped one) throws without notifying anything: the
container's state does not change.

#### Delivery

Notifications are delivered synchronously, on the thread calling `start()`,
`stop()` or `dispose()`, while the container holds its lifecycle lock: a
consumer must neither block nor wait for another thread acting on the
container. A business component subscribed to them is itself torn down by
`stop()`, so it is notified up to `onContainerStopping`. To follow a container
across its stops, observe it through `Covalent` (see
[Observing Managed Containers](#observing-managed-containers)).

### Observing Managed Containers

`Covalent` reports the lifecycle of every managed container, `main` included,
to its `ManagedContainerEvents` observers:

- `onContainerCreated(name)`, when `Covalent.managedContainer(name)` creates
  it;
- every `ContainerLifecycleEvents` notification of the container, relayed with
  its name;
- `onContainerDisposed(name)`, once it is disposed.

```java
Covalent.subscribe(observer);
...
Covalent.unsubscribe(observer);
```

The observers are a plain list, not components: they are not torn down by
`stop()` and receive every notification, `onContainerStopped` included. Each
notification is delivered synchronously, on the thread acting on the
container, with the same constraint as above: an observer must neither block
nor wait for another thread acting on the container. Each observer is
isolated: a failing observer is logged and does not deprive the others.

The relay is a raw component Covalent registers on each managed container it
creates. It is the only component kept across `stop()` besides Covalent's own
internal components, and the only one of them visible as a raw component:
supervision tools exclude it with the other internal components.

A managed container ends the same way whether it is disposed through
`Covalent.disposeManagedContainer(name)` or by its own `dispose()` (the JVM
shutdown hook in particular): once its business components are torn down, it
leaves the managed containers and `onContainerDisposed` is notified.
`Covalent.disposeManagedContainers()` disposes every managed container except
`main`, and `disposeManagedContainer("main")` is refused. When `main` is
disposed directly, it leaves the managed containers too; the `Covalent.main`
field keeps referencing it, and any further call on it is refused, as on any
disposed container. Unmanaged containers, created by `Covalent.container()`,
are not reported.

### Disposing a Covalent Application

`stop()` leaves a container usable right away: it can be started again, with
new or the same components redeployed. `dispose()` goes further - it stops
the container first if still started, then also passivates and removes its
internal (technical) component tree for good. A
disposed container cannot be restarted; discard it and, if a new one is
needed, create a fresh one instead.

```java
this.covalentContainer.dispose();
```

`dispose()` is lenient and genuinely idempotent: calling it on a container
that was never started, is already stopped, or has already been disposed is
always safe and simply does nothing on repeat calls. This is what makes it
safe to call unconditionally from a shutdown hook or a `finally` block, even
after an earlier explicit `dispose()` already ran. `isDisposed()` reports
whether `dispose()` has been called. `CovalentContainer` also implements
`AutoCloseable`, with `close()` delegating to `dispose()`, so a container can
be used with try-with-resources.

## Application Utility Services

### Asynchronous Services

Covalent provides an internal asynchronous service for components that need to
delegate work to Covalent-managed workers instead of creating their own threads.

```java
@UsedServices
private AsyncServices useAsyncServices;
```

Use `submit(...)` when the caller needs to observe completion, failure, or
cancellation. The returned `CompletionStage` is the task contract.

```java
CompletionStage<UUID> createdReportKey =
        this.useAsyncServices.submit(() -> this.reportServices.createReport(report));

createdReportKey.thenAccept(key -> System.out.println("Created report: " + key));
```

If the task fails, the returned stage is completed exceptionally. If a pending
task is removed before execution, for example because it is replaced by a newer
task with the same message key, the stage is completed with a
`CancellationException`.

Use `fireAndForget(...)` only when the caller intentionally does not need to
observe the result.

```java
this.useAsyncServices.fireAndForget(() -> this.reportServices.deleteReport(reportKey));
```

Failures from fire-and-forget tasks are routed to the global async failure
handler configured through `AsyncParameters`.

```java
@AccessedParameters
private AsyncParameters accessAsyncParameters;

this.accessAsyncParameters.setUnhandledFailureHandler((message, failure) -> {
    // log or route the failure according to the application policy
});
```

For ordered asynchronous work, `queueKey` selects a dedicated FIFO queue. All
tasks submitted with the same queue key are executed sequentially, while
different queue keys can progress independently.

```java
this.useAsyncServices.submit("customer-42", () -> this.refreshCustomer(customerId));
```

Within a dedicated queue, `messageKey` identifies pending tasks that may be
replaced. When `flushPending` is `true`, pending tasks with the same message key
are cancelled before the new task is enqueued.

```java
this.useAsyncServices.submit("ui-refresh", "selected-row", () -> this.refreshSelectedRow(), true);
```

Each dedicated queue runs on its own single worker thread, created the first
time a task is submitted to it. Left unmanaged, an application that creates
many queue keys (one per customer, one per session, ...) accumulates one
thread per key forever. Two independent, opt-in policies reclaim dedicated
queues automatically, configured through `AsyncParameters`:

```java
this.accessAsyncParameters.setDedicatedWorkerIdleReclaimEnabled(true);
this.accessAsyncParameters.setDedicatedWorkerIdleTimeout("30mn"); // default when enabled

this.accessAsyncParameters.setDedicatedWorkerPoolCapReclaimEnabled(true);
this.accessAsyncParameters.setMaxDedicatedWorkerPools(256); // default when enabled
```

Idle reclaim stops and discards a dedicated queue's worker after it has had no
task submitted or completed for the configured timeout. The LRU cap keeps at
most the configured number of dedicated queues alive, evicting the
least-recently-used one when a new queue key would exceed it. Both are
disabled by default: a dedicated queue otherwise lives for as long as the
container runs. A queue can also be released explicitly, regardless of these
policies:

```java
this.useAsyncServices.releaseQueue("customer-42");
```

Releasing (or reclaiming) a queue only stops its worker thread; the queue key
itself is not reserved and is recreated transparently the next time a task is
submitted with it.

The general pool (used by `submit(...)` and `fireAndForget(...)` without a
`queueKey`) shares the same bounded-queue behavior described above for
events: a submission that does not fit in the queue fails immediately with
`RejectedExecutionException` instead of blocking or growing without limit.
`setWorkerQueueCapacity` controls its capacity, `1024` by default, for the general
pool and the dedicated queues alike. The general pool has the number of
available processors as threads by default, at most `4`; `setGeneralWorkerCount`
changes it.

`setGeneralWorkerCount` and `setWorkerQueueCapacity` apply immediately, to the
running pools and to the existing dedicated queues: added workers start at once,
surplus workers finish their current task before terminating, and a reduced
capacity keeps the tasks already queued while rejecting new submissions until the
queue is back under it.

#### Periodic Tasks

`scheduleWithFixedDelay(...)` runs a task again and again: the first run after
an initial delay, each following run one delay after the previous one finished,
so two runs of the same task never overlap. It returns a `ScheduledTask`, whose
`cancel()` stops it.

```java
private ScheduledTask refreshTask;

@Activation
public void activate() {
    this.refreshTask = this.useAsyncServices.scheduleWithFixedDelay(
            Duration.ofSeconds(5), Duration.ofSeconds(30), () -> this.refreshCache());
}
```

The task runs on the general pool, or on the dedicated queue of a `queueKey`
(`scheduleWithFixedDelay("cache", initialDelay, delay, task)`), in order with the
other tasks of that queue.

A periodic task belongs to the component that schedules it, and stops with it:
when the component is passivated, removed or fails, and when the container
stops, its tasks are cancelled without any cleanup code in the component. After
a passivation, a component that is activated again schedules its tasks again,
which is why the activation method is the natural place to do it (`isCancelled()`
tells whether a handle is still live). A task scheduled by code that is not a
component belongs to nobody: it stops only on `cancel()` or when the container
stops.

A failing run is reported to the unhandled failure handler of `AsyncParameters`,
like a `fireAndForget(...)` task, and the repetition goes on. A run refused
because the queue is full is skipped, logged and counted like any other refused
task, and the next one is scheduled normally. A run already started when the task
is cancelled is not interrupted. Timing is approximate (the clock is checked about
once a second at worst, and a busy pool delays a run): periodic tasks suit
housekeeping and polling, not precise timing.

### Clean Stack Traces

Covalent can clean stack traces before exceptions are shown to application
developers. This is useful when debugging through component proxies, reflection,
event dispatch, or asynchronous workers.

When clean stack traces are enabled, Covalent removes technical framework frames
from exceptions before relaunching or logging them. The exception type, message,
cause, and suppressed exceptions are preserved.

Clean stack traces are disabled by default. The setting applies to the whole
JVM: changing it on one container changes it for every container of the JVM.
Containers that need different settings run in separate JVMs.

```java
Covalent.main.getParameters().setCleanStackTraces(true);
```

## Verification Level

Covalent can adapt how strictly it enforces component model rules. This allows a
team to choose the level of constraint that fits its practices.

The internal `ComponentValidator` component manages these checks. Verification
rules are divided into two categories.

**Structural rules** check the shape of components, including:

- overall component structure, such as having exactly one component type and one
  component implementation;
- annotations and naming conventions on structural fields, such as used services,
  accessed parameters, produced events, and used event subscribers;
- annotations on item and composed component fields;
- annotations on component classes, such as implementations, items, and types,
  and the shape of a component type, which must be a tag interface without any
  method (see [Component Type](#component-type));
- annotations on ports, such as services, events, and parameters;
- structural consistency rules, such as preventing one component from consuming
  the same events interface more than once through multiple items.

**Dynamic rules** check runtime behavior, including:

- using, providing, accessing, or offering the same service or parameters
  interface more than once;
- subscribing to the same events more than once with the same arguments; see
  [Custom Event Subscription](#custom-event-subscription);
- unsubscribing from events to which the component is not subscribed;
- event data cloning rules; see [Event Data Cloning](#event-data-cloning).

Covalent provides four verification levels:

- **silent**: the rule is ignored, as long as the application can still run;
- **trace**: a fine-grained log entry is written for the broken rule;
- **warn**: a warning is written and is visible in the application logs by
  default;
- **strict**: an exception is thrown.

Structural and dynamic verification levels can be configured independently
through validation parameters. These parameters are provided by the internal
`ComponentValidator` component.

The container also exposes convenience methods on its parameters.

```java
Covalent.main.getParameters().setDynamicRulesChecking(VerificationLevel.silent);
Covalent.main.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
```

Naming convention checks can be controlled more precisely. By default, all naming
conventions are checked according to the structural verification level, but each
kind of naming convention can be disabled individually.

```java
@AccessedParameters // or @Parameters
private ValidationParameters accessValidationParameters;

// ...

// Disable verification of the produced events field naming convention.
this.accessValidationParameters.setCheckProduceEventsFieldNamingConvention(false);

// Disable verification of the events method naming convention.
this.accessValidationParameters.setCheckEventsMethodNamingConvention(false);
```

### Choosing a Verification Level

There is no single right level for a whole application's lifetime, and no
single axis that decides it. Verification levels exist to offer flexibility
that different teams, and different moments in a project, can use very
differently - none of the following is more "correct" than another.

- **A steady-state team preference.** Some teams keep `strict` on
  permanently, to always stay in tight, immediate control of their
  component model - any drift fails loudly and immediately. Others prefer
  `warn` or `trace` as their permanent baseline, precisely to avoid being
  interrupted prematurely by a rule they intend to address in their own
  time, on their own schedule. Neither is a fallback for the other; both
  are legitimate ongoing working styles.
- **Tuned by stage of the pipeline.** A team can also vary the level by
  where code is in its path to production: `warn` day-to-day so a broken
  rule shows up in the logs without stopping anyone, `strict` in automated
  tests and CI so the same violation fails the build (`covalent-tests`
  itself runs under `strict`), and a deliberate choice in production -
  `strict` for the fastest possible failure when the test suite already
  exercises the same paths, `warn` when integrating less predictable
  input, such as raw components wrapping existing code.
- **As a ratchet during a progressive raw-to-managed migration.** While
  converting part of an application from raw components to managed ones
  piece by piece, the managed components introduced first are rarely
  fully idiomatic yet. Starting structural rules at `silent` or `trace`
  avoids drowning that transition in noise; raising the level gradually as
  the migration progresses turns the level itself into a ratchet - each
  tightening makes whatever hasn't been converted yet loud again, pushing
  the migration to actually finish rather than stall.
- **Temporarily, to investigate.** Independently of environment or
  migration state, a level can be lowered for the duration of a single
  investigation, to cut the noise of already-known issues that will be
  fixed later, so the problem actually being chased stays visible - then
  raised back once the investigation is done.
- **`silent` is for one deliberate exception, not a baseline.** Disabling a
  check entirely removes Covalent's ability to report a violation at all;
  reach for it when a specific rule is known not to apply to one component
  (see the naming convention overrides above), not as a way to quiet noisy
  logs generally.
- **Structural and dynamic verification are configured independently**,
  and naming convention checks more granularly still (see above): nothing
  forces a single level for the whole application, in any of the
  situations above.

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Build and Requirements](requirements.md) | [Next: Covalent Raw Components Guide](raw-components.md)
<!-- /doc-nav:bottom -->
