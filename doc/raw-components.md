# Covalent Raw Components Guide

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Managed Components Guide](managed-components.md) | [Next: Covalent Supervision Guide](supervision.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Working with Raw Components](#working-with-raw-components)
  - [Performance Characteristics](#performance-characteristics)
  - [Component Type for Raw Components](#component-type-for-raw-components)
  - [Register an Existing Object as a Raw Component](#register-an-existing-object-as-a-raw-component)
  - [Registration Is Required Before Opening a Port](#registration-is-required-before-opening-a-port)
  - [Port Owner and Component Type](#port-owner-and-component-type)
  - [Provide Services](#provide-services)
  - [Use Services](#use-services)
  - [Offer and Access Parameters](#offer-and-access-parameters)
  - [Produce Events](#produce-events)
  - [Subscribe to Events](#subscribe-to-events)

# Working with Raw Components

Raw components are externally owned objects that Covalent can integrate into its
component model. They are declared and connected through the
`RawComponentsServices` interface.

The purpose of raw components is interoperability. They let an existing
application expose ordinary Java objects to Covalent without turning those
objects into managed Covalent components. This is useful at integration
boundaries, especially when an application already has its own object model,
dependency injection mechanism, UI layer, or external framework lifecycle.

`RawComponentsServices` is therefore not an alternate deployment API for
Covalent-managed components. Managed components must use the managed lifecycle
API. Raw registration is reserved for objects owned by the surrounding
application.

A raw component is always registered from an existing instance. Covalent can then
create the corresponding runtime descriptor, connect manually declared services,
parameters, and events, and drive lifecycle callbacks on that instance.

The raw registration contract is deliberately strict. `RawComponentsServices`
is the bridge between Covalent and the non-Covalent world. Component types and
implementation classes registered through this API must not use Covalent
component model annotations such as `@Component`, `@Implementation`, or `@Item`.
A raw implementation must also not declare managed items or composed components
in its fields, whether annotated with `@Item` or `@ComponentItem`.

Lifecycle callback annotations such as `@Initialization`, `@Activation`,
`@Passivation`, and `@Removal` remain valid on raw components. They describe
lifecycle callbacks on an external object; they do not turn that object into a
managed Covalent component.

Unlike managed components, raw components can be registered directly from an
instance, can target a specific lifecycle state, and can later disconnect
manually declared used services, accessed parameters, produced events, or event
subscriptions.

For managed components, used services and accessed parameters remain connected
for the whole component lifecycle.

The same holds for a proxy returned by `useServices` or `accessParameters`: it
belongs to its user, and keeps working when the provider is unregistered and
registered again, without calling `useServices` or `accessParameters` a second
time. Until the provider is back, calls fail with an `IllegalStateException`
that names the missing interface. Only `unuseServices`, `unaccessParameters` or
the unregistration of the user itself ends the link.

## Performance Characteristics

Raw components carry a runtime cost that managed components do not. A managed
component's identity is already known from deployment, so services, events, and
parameters are dispatched directly. A raw port operation whose component type is
omitted - `useServices(interface)`, `offerParameters(interface)`,
`accessParameters(interface)`, `produceEvents(interface, ...)`, `subscribe`, and
their `unuse`/`unoffer`/`unaccess`/`unproduce`/`unsubscribe` counterparts -
instead has Covalent resolve the calling component by walking the current
thread's call stack to find the first frame outside its own internal dispatch
code. Passing the component type explicitly (see the sections below) skips this
resolution entirely.

This stack walk is bounded and inexpensive in absolute terms, but it is still
real work performed on every such call, on top of what a managed component or
an explicitly-typed raw call needs. For raw port operations called on a hot
path - inside a loop, or at high frequency per request or event - pass the
component type explicitly instead of relying on caller inference.

Caller inference also has a robustness limit worth knowing about, separate from
its cost. The stack walk recognizes Covalent's own internal frames by a fixed,
closed list (its internal manager classes, any class name ending in
`InvocationHandler`, and JDK reflection/proxy frames) and treats everything else
as application code. It has no way to recognize a frame it was not built to
expect: an AOP interceptor, a framework-injected proxy, or another library's own
dispatch layer sitting between the real caller and Covalent looks like ordinary
application code to it. In that case the wrong frame can be resolved as "the
caller" - silently, with no error and no log, since this is different from
exhausting the walk's frame limit (which does log a severe diagnostic). Caller
inference is therefore best suited to a controlled environment where Covalent's
own call stacks are not reshaped by other frameworks. When a raw component is
embedded in a larger framework, wrapped by AOP, or reached through any call path
that cannot be guaranteed free of extra frames, pass the component type
explicitly - the same recommendation as above, here for correctness rather than
performance.

## Component Type for Raw Components

For a raw component, explicitly defining a component type is optional. The
component type is the identity under which Covalent registers and finds the raw
component.

The component type can be selected in several ways:

- by default, the component type is the implementation type;
- the component type can be an interface implemented by the registered instance;
- the component type can be any class or interface chosen by the integration
  layer.

The explicit component type does not have to be implemented by the registered
instance. This makes it possible to expose an external object under an
application-level integration type.

## Register an Existing Object as a Raw Component

Any externally owned object can be registered as a raw component.

```java
POJO pojo = new POJO();

// Covalent.main is the default container. rcs() returns its RawComponentsServices.
Covalent.main.rcs().registerComponent(pojo);
```

In this case, the component type is the implementation type: `POJO`.
`registerComponent` returns this inferred type, which can be passed later to
`unregisterComponent`. The forms that take an explicit component type return
nothing, since the caller already knows the type.

A custom component type can also be provided.

```java
POJO pojo = new POJO();
Covalent.main.rcs().registerComponent(SomeClassOrInterface.class, pojo);
```

The explicit type can be a regular Java interface or class from the surrounding
application.

A raw component cannot carry `@Internal`, on its component type or its
implementation: `@Internal` is reserved to Covalent's own components, kept
across `stop()`, and registering such a component throws
`IllegalArgumentException`.

```java
public interface ExternalBillingGateway {
    void charge(Invoice invoice);
}

public final class StripeBillingGateway implements ExternalBillingGateway {
    @Override
    public void charge(final Invoice invoice) {
        // Call the external billing system.
    }
}

ExternalBillingGateway gateway = new StripeBillingGateway();
Covalent.main.rcs().registerComponent(ExternalBillingGateway.class, gateway);
```

Covalent-managed components must use the managed lifecycle API instead.

```java
@Component
public interface BillingComponent {
}

@Implementation
public final class BillingComponentImpl implements BillingComponent {
}

// Managed component: deploy it through LifecycleServices, not RawComponentsServices.
Covalent.main.lcs().deployComponent(BillingComponentImpl.class);
```

Registering an object that carries Covalent annotations - the component
type, the implementation, an item, or a nested services/parameters port - is
always rejected, whatever the container's verification level:
`registerComponent` throws `IllegalArgumentException` naming the offending
class. This is a fixed contract, not part of the configurable structural and
dynamic verification that governs managed components (see [Verification Level](managed-components.md#verification-level)): raw components skip that
machinery entirely, and the one thing the raw components API refuses outright
is smuggling a managed component through it.

By default, the registered component is brought to the `activated` state. A
specific lifecycle state can be requested when registering the component.

```java
Covalent.main.rcs().registerComponent(pojo, ComponentState.initialized);
```

This is the recommended way to make an externally owned object follow the
Covalent lifecycle. The container registers the provided instance, then
invokes lifecycle callbacks such as `@Initialization`, `@Activation`,
`@Passivation`, and `@Removal` when the requested lifecycle transitions occur.

When several externally owned objects must be prepared first and activated
together later, register them up to the `initialized` state. The usual way to
bring all registered components to `activated` is then to start the container.

```java
Covalent.main.rcs().registerComponent(ExternalBillingGateway.class, gateway,
        ComponentState.initialized);

// Later, when the application decides to activate the registered components.
Covalent.main.start();
```

A single raw component can also be activated explicitly when the application
needs to open that integration point independently from the global container
lifecycle.

```java
Covalent.main.rcs().activateComponent(ExternalBillingGateway.class, gateway);
```

The default target state for registered raw components can also be configured
through raw component parameters.

```java
RawComponentsParameters rawComponentsParameters =
        this.rcs.accessParameters(RawComponentsParameters.class);

rawComponentsParameters.setDefaultStateToReach(ComponentState.initialized);
// All registered raw components are brought to the initialized state
// instead of activated.
```

When the raw component is no longer needed, it can be unregistered.

```java
Covalent.main.rcs().unregisterComponent(POJO.class);
```

## Registration Is Required Before Opening a Port

Creating a raw port - providing or using services, offering or accessing
parameters, producing events, or subscribing to events - requires its
component type to already be registered as a raw component and to have
reached at least the `instantiated` state. This applies to every one of
those operations, on both the producing and the consuming side, whether the
component type is passed explicitly or resolved from the immediate caller's
own class (see [Performance Characteristics](#performance-characteristics)).

```java
public final class BillingScreen {
    private final RawComponentsServices rawComponentsServices = Covalent.main.rcs();

    public void start() {
        this.rawComponentsServices.registerComponent(this);
        this.rawComponentsServices.provideServices(BillingGatewayServices.class);
    }
}
```

Opening a port for a component type that is not yet registered throws
`IllegalStateException`. A managed component is never affected by this: it
declares its ports through annotations, and its component type is registered
before any of them is created. Using the ports of a managed component from a
raw component is not affected either.

A raw port cannot be opened on behalf of one of Covalent's own (`@Internal`)
components, such as `LifecycleManager.class`: it would outlive every `stop()`,
like them, and let a raw component pose as one of them. This throws
`IllegalArgumentException`. A raw component opens its ports on its own behalf.
Using the ports Covalent's own components provide is allowed, within the
limits set by the [Stability Guarantee](public-api.md#stability-guarantee).

## Port Owner and Component Type

Every raw port belongs to a component: the one that provides the services,
offers the parameters, produces the events, subscribes to them, or uses the
services and accesses the parameters of another component. Covalent must know
which component that is. The rule is the same for every port, and is stated
only here:

- **From the component that owns the port, omit the component type.** Covalent
  deduces it from the immediate caller, which must be a raw component that is
  already registered (see [Registration Is Required Before Opening a
  Port](#registration-is-required-before-opening-a-port)). A managed component
  does not use `RawComponentsServices` to declare its own ports: it declares them
  through annotations.
- **From any other object, pass the component type.** This is the case of an
  integration layer that declares ports on behalf of a raw component.
- **Passing the component type is always allowed**, even from the owner itself.
  It also skips the call-stack resolution (see [Performance
  Characteristics](#performance-characteristics)).

The meaning of the component type argument depends on the method:

| Methods | Component type argument | Can be omitted when |
| --- | --- | --- |
| `provideServices`, `unprovideServices` | the provider | the caller is the provider |
| `offerParameters`, `unofferParameters` | the offeror | the caller is the offeror |
| `produceEvents`, `unproduceEvents` | the producer | the caller is the producer |
| `subscribe`, `unsubscribe` | the consumer | the caller is the consumer |
| `useServices`, `accessParameters` | the other side: the provider or the offeror | the other side is a managed component |
| `unuseServices`, `unaccessParameters` | none | always |

For `useServices` and `accessParameters`, the user is always the caller. The
type argument names the component being used or accessed, and is needed only
when that component is a raw component.

## Provide Services

A raw component can manually provide services through `RawComponentsServices`.
From the providing component itself, only the services interface is required.

```java
private final RawComponentsServices rawComponentsServices = Covalent.main.rcs();

public void registerServices() {
    this.rawComponentsServices.provideServices(BillingGatewayServices.class);
}
```

From another object, name the provider, for example when it is exposed under an
explicit component type.

```java
this.rawComponentsServices.provideServices(
        ExternalBillingGateway.class,
        BillingGatewayServices.class);
```

### Stop Providing Services

A raw component can stop providing services, with the same two forms.

```java
this.rawComponentsServices.unprovideServices(BillingGatewayServices.class);

this.rawComponentsServices.unprovideServices(
        ExternalBillingGateway.class,
        BillingGatewayServices.class);
```

Stopping services that are not provided is a dynamic rule violation, handled
according to the dynamic verification level (see [Verification Level](managed-components.md#verification-level)): it is only traced by default and
raises a `ValidationException` under the `strict` level. The same applies to
stopping parameters that are not offered.

## Use Services

A raw component can use services provided by a managed component or by another
raw component.

When the service provider is a managed component, only the services interface is
required.

```java
PricingServices pricingServices =
        this.rawComponentsServices.useServices(PricingServices.class);

Money price = pricingServices.computePrice(invoice);
```

When the service provider is a raw component, its component type must be
specified.

```java
BillingGatewayServices billingGatewayServices =
        this.rawComponentsServices.useServices(
                ExternalBillingGateway.class,
                BillingGatewayServices.class);

billingGatewayServices.charge(invoice);
```

### Stop Using Services

A raw component can stop using services. No component type is needed.

```java
this.rawComponentsServices.unuseServices(BillingGatewayServices.class);
```

## Offer and Access Parameters

Raw components can offer and access parameters through `RawComponentsServices`.

From the offering component itself, only the parameters interface is required.

```java
this.rawComponentsServices.offerParameters(BillingGatewayParameters.class);
```

From another object, name the offeror.

```java
this.rawComponentsServices.offerParameters(
        ExternalBillingGateway.class,
        BillingGatewayParameters.class);
```

When the parameter offeror is a managed component, only the parameters interface
is required to access them.

```java
BillingGatewayParameters billingGatewayParameters =
        this.rawComponentsServices.accessParameters(BillingGatewayParameters.class);

billingGatewayParameters.setSandboxMode(true);
```

When the parameter offeror is a raw component, its component type must be
specified.

```java
BillingGatewayParameters billingGatewayParameters =
        this.rawComponentsServices.accessParameters(
                ExternalBillingGateway.class,
                BillingGatewayParameters.class);

billingGatewayParameters.setSandboxMode(true);
```

### Stop Offering or Accessing Parameters

A raw component can stop offering parameters, with the same two forms as when
offering them.

```java
this.rawComponentsServices.unofferParameters(BillingGatewayParameters.class);

this.rawComponentsServices.unofferParameters(
        ExternalBillingGateway.class,
        BillingGatewayParameters.class);
```

A raw component can stop accessing parameters. No component type is needed.

```java
this.rawComponentsServices.unaccessParameters(BillingGatewayParameters.class);
```

## Produce Events

A raw component can produce events through `RawComponentsServices`. From the
producing component itself, only the events interface is required.

```java
BillingGatewayEvents billingGatewayEvents =
        this.rawComponentsServices.produceEvents(BillingGatewayEvents.class);
```

From another object, name the producer.

```java
BillingGatewayEvents billingGatewayEvents =
        this.rawComponentsServices.produceEvents(
                ExternalBillingGateway.class,
                BillingGatewayEvents.class);
```

A raw event producer can also provide subscription delegates without annotations.
The subscribe delegate is called whenever a consumer subscribes. The unsubscribe
delegate is optional and is called whenever a consumer unsubscribes. The
delegates are passed after the events interface, with or without the producer
component type.

```java
BillingGatewayEvents billingGatewayEvents =
        this.rawComponentsServices.produceEvents(
                BillingGatewayEvents.class,
                this::onSubscribe,
                this::onUnsubscribe);
```

There are no naming conventions for raw subscribe and unsubscribe delegate
methods.

The events fired for a consumer while its subscribe delegate runs are kept and
delivered afterwards, within a limit: see [Event Subscription Delegation](managed-components.md#event-subscription-delegation).

### Stop Producing Events

A raw component can stop producing events, with the same two forms.

```java
this.rawComponentsServices.unproduceEvents(BillingGatewayEvents.class);

this.rawComponentsServices.unproduceEvents(
        ExternalBillingGateway.class,
        BillingGatewayEvents.class);
```

## Subscribe to Events

Raw components can subscribe and unsubscribe from events through
`RawComponentsServices`.

A raw component can subscribe before the producer exists. Covalent records the
subscription as pending and attaches the consumer automatically when a producer
for the events interface is created. If the consumer unsubscribes before the
producer appears, the pending subscription is removed.

This direct raw path does not use a `@UsedEventsSubscribe` surrogate because the
runtime already receives the events interface, the consumer, and the subscription
parameters.

The component type argument is the consumer component, whether or not the
consumer object is that component itself. It is omitted when the call is made by
the consumer component, whatever the object that receives the events.

From the consumer component, with a separate object receiving the events:

```java
BillingGatewayEvents consumer = new BillingGatewayEventsConsumer();

this.rawComponentsServices.subscribe(BillingGatewayEvents.class, consumer);

// ...

this.rawComponentsServices.unsubscribe(BillingGatewayEvents.class, consumer);
```

From the consumer component, when the component itself receives the events:

```java
this.rawComponentsServices.subscribe(BillingGatewayEvents.class, this);

// ...

this.rawComponentsServices.unsubscribe(BillingGatewayEvents.class, this);
```

From another object, the consumer component type must be specified:

```java
this.rawComponentsServices.subscribe(
        BillingScreen.class,
        BillingGatewayEvents.class,
        billingScreen);

// ...

this.rawComponentsServices.unsubscribe(
        BillingScreen.class,
        BillingGatewayEvents.class,
        billingScreen);
```

### Specify an Events Filter

A raw event subscription can also use an event filter. Because raw event
interfaces do not use annotations, the filter is provided through
`SubscribeParameters`.

First, declare the raw events interface and its filter interface.

```java
public interface BillingGatewayEvents {
    void onPaymentAccepted(Invoice invoice);

    void onPaymentRejected(Invoice invoice, String reason);

    interface BillingGatewayEventsFilter {
        // The filter methods must respect the naming convention.
        boolean filterPaymentAccepted(Invoice invoice);

        boolean filterPaymentRejected(Invoice invoice, String reason);
    }
}
```

Then declare event production.

```java
BillingGatewayEvents billingGatewayEvents =
        Covalent.main.rcs().produceEvents(
                ExternalBillingGateway.class,
                BillingGatewayEvents.class);
```

Finally, create subscribe parameters in the consumer and subscribe with them.

```java
SubscribeParameters subscribeParameters =
        Covalent.main.getParameters().newSubscribeParameters();

subscribeParameters.setEventFilter(new BillingGatewayEvents.BillingGatewayEventsFilter() {
    @Override
    public boolean filterPaymentAccepted(final Invoice invoice) {
        return false;
    }

    @Override
    public boolean filterPaymentRejected(final Invoice invoice, final String reason) {
        return invoice.isTestInvoice();
    }
});

BillingGatewayEvents consumer = new BillingGatewayEventsConsumer();

this.rawComponentsServices.subscribe(
        BillingScreen.class,
        BillingGatewayEvents.class,
        consumer,
        subscribeParameters);
```

The container calls the filter method automatically. The event is
delivered only when the corresponding filter method returns `false`.

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Managed Components Guide](managed-components.md) | [Next: Covalent Supervision Guide](supervision.md)
<!-- /doc-nav:bottom -->
