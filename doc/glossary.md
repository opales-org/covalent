# Covalent Glossary

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Configuration Reference](configuration.md) | [Next: Covalent Troubleshooting](troubleshooting.md)
<!-- /doc-nav:top -->

# Table of Contents

- [A - C](#a---c)
  - [Activation](#activation)
  - [Callback](#callback)
  - [Component](#component)
  - [Component Type](#component-type)
  - [Composite](#composite)
  - [Consumer](#consumer)
  - [Container](#container)
- [D - I](#d---i)
  - [Delegation (Event Subscription Delegation)](#delegation-event-subscription-delegation)
  - [Deployment](#deployment)
  - [Dynamic Rules](#dynamic-rules)
  - [Event](#event)
  - [Events Filter](#events-filter)
  - [Extension](#extension)
  - [Horizontal Start Mode](#horizontal-start-mode)
  - [Implementation](#implementation)
  - [Initialization](#initialization)
  - [Instantiation](#instantiation)
  - [Internal Component](#internal-component)
  - [Item](#item)
- [L - P](#l---p)
  - [Lifecycle](#lifecycle)
  - [Managed Component](#managed-component)
  - [Metamodel](#metamodel)
  - [MVIC](#mvic)
  - [Parameters](#parameters)
  - [Passivation](#passivation)
  - [Port](#port)
  - [Producer](#producer)
  - [Provider](#provider)
- [R - Z](#r---z)
  - [Raw Component](#raw-component)
  - [Removal](#removal)
  - [Root Composite](#root-composite)
  - [Service](#service)
  - [Structural Rules](#structural-rules)
  - [Surrogate](#surrogate)
  - [Undeployment](#undeployment)
  - [User (Services User)](#user-services-user)
  - [Verification Level](#verification-level)
  - [Vertical Start Mode](#vertical-start-mode)

Short definitions of the terms used throughout the Covalent documentation, in
alphabetical order. Each entry points to the page that explains it in detail.
For the annotation that implements a given concept, see the [Annotations Cheat
Sheet](annotations.md).

# A - C

## Activation

The lifecycle transition in which a component publishes its provided services and
produced events so that other components can use them. Its offered parameters are
available since initialization. It can happen several times, after a passivation. See
[Lifecycle](managed-components.md#lifecycle).

## Callback

A method of a component implementation that the container calls on a lifecycle
transition: initialization, activation, passivation or removal. See [Lifecycle
Callbacks](managed-components.md#lifecycle-callbacks).

## Component

The unit of structure of a Covalent application. It is coarser than a class: it has
a type, an implementation, possibly items and sub-components, ports, and a
lifecycle. See [What Is a Component Model](foundations.md#what-is-a-component-model).

## Component Type

The public identity of a component. For a managed component, it is a tag interface
annotated with `@Component`. There is at most one instance of a given component type
at runtime. See [Component Type](managed-components.md#component-type).

## Composite

A component that owns sub-components, in the UML composition sense. The lifecycle of a
sub-component is tied to the lifecycle of its composite. See
[Composition](managed-components.md#composition).

## Consumer

For events, a component that receives them, by implementing the events interface.
See [Consuming Events](managed-components.md#consuming-events).

## Container

The runtime that deploys components, connects their ports and drives their
lifecycle. `Covalent.main` is the default container. The container is itself
implemented with Covalent components. See [What Is a
Container](foundations.md#what-is-a-container).

# D - I

## Delegation (Event Subscription Delegation)

The mechanism that lets an events producer be notified when a consumer subscribes
or unsubscribes, by implementing the subscribe interface of its events. The
container still registers the consumer and dispatches the events. See [Event
Subscription Delegation](managed-components.md#event-subscription-delegation).

## Deployment

The lifecycle transition in which the implementation of a component is introspected
and its metamodel is created. Deployment targets implementation classes, and it is
never performed automatically. See [Lifecycle](managed-components.md#lifecycle).

## Dynamic Rules

The verification rules that check the behavior of the application at runtime, such
as using the same service twice or subscribing twice to the same events. See
[Verification Level](managed-components.md#verification-level).

## Event

A fire-and-forget notification sent by a producer to the subscribed consumers,
represented by a method of an events interface annotated with `@Events`. See
[Events](managed-components.md#events).

## Events Filter

An interface nested in an events interface, annotated with `@EventsFilter`, that
decides for one subscription whether an event is delivered. A filter method
returning `true` means the event is filtered out and not delivered - the
opposite convention from `Stream.filter`, matching `Collection.removeIf`
instead. Applies identically to managed and raw event filters. See [Event
Filters](managed-components.md#event-filters).

## Extension

An optional piece of runtime started by the container, selected with the
`covalent.extensions` system property, and implemented against the public
`CovalentExtension` SPI. The component viewer is an extension. See
[Configuration](configuration.md#extensions) for enabling and writing one,
and the [Covalent Supervision Guide](supervision.md) for the component
viewer itself.

## Horizontal Start Mode

The startup mode in which all components reach a lifecycle state before the container
moves to the next state. See [Vertical and Horizontal Start
Modes](managed-components.md#vertical-and-horizontal-start-modes).

## Implementation

The class that holds the behavior of a component, annotated with `@Implementation`.
It must be `public` with a `public` no-argument constructor. See
[Implementation](managed-components.md#implementation).

## Initialization

The lifecycle transition in which a component is prepared for use. It happens once,
and parameters are available. See [Lifecycle](managed-components.md#lifecycle).

## Instantiation

The lifecycle transition in which the classes of a component are instantiated. See
[Lifecycle](managed-components.md#lifecycle).

## Internal Component

A component of the container itself, marked with `@Internal`, as opposed to an
application component. Internal components are active from the container's
construction to its disposal, kept across `stop()`; `@Internal` is reserved to
them and refused on application components. See [Starting a Covalent
Application](managed-components.md#starting-a-covalent-application).

## Item

An internal part of a segmented component. It follows the lifecycle of its
component, is not a component of its own, and can carry ports. See
[Items](managed-components.md#items).

# L - P

## Lifecycle

The states and transitions a component goes through, from deployment to
undeployment. See [Lifecycle](managed-components.md#lifecycle).

## Managed Component

A component written for Covalent, with a component type, an implementation and
annotated ports, deployed and driven by the container through its complete
lifecycle. See [Covalent Managed Components](managed-components.md).

## Metamodel

The description of a component that the container builds at deployment by
introspecting its implementation. See [Lifecycle](managed-components.md#lifecycle).

## MVIC

Short for Model / Controller / Interaction Controller / View: the pattern used by
every function in the example demos, and internally by the HMI and
supervision components, to keep a function's state, its logic, its HMI
wiring, and its rendering in four separate, independently testable
components. Not a Covalent mechanism of its own - just a naming convention
layered on ordinary composition. See [The MVIC Pattern](examples.md#the-mvic-pattern).

## Parameters

A request/response interface used to configure the component that offers it,
declared with `@Parameters`. Parameters are available from the initialization of a
component. See [Parameters](managed-components.md#parameters).

## Passivation

The lifecycle transition in which a component unpublishes its provided services and
produced events. Its offered parameters stay available. The component can be
activated again. See [Lifecycle](managed-components.md#lifecycle).

## Port

A service, events or parameters interface through which a component is connected to
the others. A component provides, uses, offers, accesses, produces or consumes
ports. See [Annotations](annotations.md).

## Producer

For events, a component that fires them, through a `@ProducedEvents` field. See
[Producing Events](managed-components.md#producing-events).

## Provider

For services, a component that implements a services interface. See [Providing
Services](managed-components.md#providing-services).

# R - Z

## Raw Component

An externally owned object registered in the container, whose ports are declared
through `RawComponentsServices` instead of annotations. See [Covalent Raw Components
Guide](raw-components.md).

## Removal

The lifecycle transition in which a component releases its resources. See
[Lifecycle](managed-components.md#lifecycle).

## Root Composite

The single composite an application deploys as its one unit: every other
component is one of its (possibly nested) sub-components, so one
`deployComponents(...)`/`start()` call activates the whole tree in
dependency order, and one `stop()` tears it down the same way. See
[Composition](managed-components.md#composition).

## Service

A request/response interaction between components, represented by a method of a
services interface annotated with `@Services`. See
[Services](managed-components.md#services).

## Structural Rules

The verification rules that check the shape of components and their annotations. See
[Verification Level](managed-components.md#verification-level).

## Surrogate

A substitute injected in place of a used port that is not published yet. It resolves
the real port when it is first used. It does not make an unavailable port usable
before its component is activated. See [Vertical and Horizontal Start
Modes](managed-components.md#vertical-and-horizontal-start-modes).

## Undeployment

The lifecycle transition in which the instances of a component are removed from the
registry, leaving no trace of it in the container. See
[Lifecycle](managed-components.md#lifecycle).

## User (Services User)

For services, a component that declares a `@UsedServices` field. See [Using
Services](managed-components.md#using-services).

## Verification Level

The strictness with which Covalent enforces the component model rules: `silent`,
`trace`, `warn` or `strict`, configured separately for structural and dynamic rules.
See [Verification Level](managed-components.md#verification-level).

## Vertical Start Mode

The default startup mode, in which each component is fully started before the next
one. See [Vertical and Horizontal Start
Modes](managed-components.md#vertical-and-horizontal-start-modes).

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Configuration Reference](configuration.md) | [Next: Covalent Troubleshooting](troubleshooting.md)
<!-- /doc-nav:bottom -->
