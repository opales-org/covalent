# Covalent Public API

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Foundations](foundations.md) | [Next: Covalent Build and Requirements](requirements.md)
<!-- /doc-nav:top -->

# Table of Contents

- [What Is Public](#what-is-public)
  - [Container Entry Points](#container-entry-points)
  - [Component Model Annotations](#component-model-annotations)
  - [Integration APIs](#integration-apis)
  - [Extension SPI](#extension-spi)
  - [Technical Services and Types](#technical-services-and-types)
  - [Exceptions](#exceptions)
- [What Is Not Public](#what-is-not-public)
- [Stability Guarantee](#stability-guarantee)
- [Accessing Containers](#accessing-containers)

# What Is Public

Covalent's public API is what these guides document. It is organized by usage
level, from the entry points every application uses to the technical services
reserved for advanced uses.

| Category | Types | Used by |
|----------|-------|---------|
| [Container entry points](#container-entry-points) | `Covalent`, `CovalentContainer`, `CovalentContainerParameters`, `StartupReport`, `ShutdownReport`, `ContainerStatus` | every application |
| [Component model annotations](#component-model-annotations) | `@Component`, `@Implementation`, `@Item`, `@ComponentItem`, `@ComposedComponent`, and the port, event contract and lifecycle annotations | every application |
| [Integration APIs](#integration-apis) | `RawComponentsServices`, `LifecycleServices` | existing applications, tools, tests |
| [Extension SPI](#extension-spi) | `CovalentExtension` | optional runtime extensions |
| [Technical services and types](#technical-services-and-types) | `AsyncServices`, `AsyncParameters`, `DependencyGraphServices`, the registry descriptors, technical events such as `LifecycleEvents`, `SubscribeParameters`, `ComponentState`, `VerificationLevel` | advanced uses |
| [Exceptions](#exceptions) | `ValidationException`, `NamingException`, `ReflectionException`, `ComponentLifecycleException` | every application |

## Container Entry Points

The standard container API is the main entry point for ordinary applications:

- `Covalent` exposes the supported container access modes: `main`,
  `container()`, `managedContainer(name)`, managed-container lookup,
  managed-container disposal, and managed-container lifecycle observation (see
  [Accessing Containers](#accessing-containers));
- `CovalentContainer` exposes application-level container operations:
  `getName()`, `deployComponents(...)`, `start()`, `stop()`, `isStarted()`,
  `getParameters()`, `rcs()`, and `lcs()` - `getName()` returns the name a
  managed container is managed under, `null` for an unmanaged one;
- `CovalentContainerParameters`, `StartupReport`, `ShutdownReport`, and
  `ContainerStatus` expose container configuration and operation results.

## Component Model Annotations

The component model annotations are public API:

- `@Component`, `@Implementation`, `@Item`, `@ComponentItem`, and
  `@ComposedComponent`;
- port annotations such as `@Services`, `@UsedServices`, `@Parameters`,
  `@AccessedParameters`, `@Events`, `@ProducedEvents`, `@EventsSubscribe`,
  and `@UsedEventsSubscribe`;
- event contract annotations `@EventsFilter` and `@Cloned`;
- lifecycle annotations such as `@Initialization`, `@Activation`,
  `@Passivation`, and `@Removal`.

## Integration APIs

`RawComponentsServices`, available through `CovalentContainer.rcs()`, is the
public integration API for progressively bringing externally owned Java objects
into Covalent. It is not a secondary or private API: raw components are an
official adoption path for existing applications.

`LifecycleServices`, available through `CovalentContainer.lcs()`, is the public
API for fine-grained lifecycle control. Most applications can use
`CovalentContainer.deployComponents(...)`, `start()`, and `stop()`, while tools,
tests, and advanced applications can use `LifecycleServices` to drive or inspect
individual lifecycle transitions.

## Extension SPI

`CovalentExtension`, discovered through the standard Java `ServiceLoader`
mechanism, is the public SPI for optional runtime extensions - the
`component-viewer` extension provided by `covalent-supervision` is the one
shipped example. Implementing it takes two methods: `getId()`, the stable
identifier an application opts into through the `covalent.extensions` system
property (see [Extensions](configuration.md#extensions)), and
`onContainerStarted(CovalentContainer)`, called after each successful start of the
container, so it can be called again after a `stop()`. `CovalentExtensionsManager`,
which runs the requested extensions, is `@Internal`: applications implement
`CovalentExtension`, they do not call the manager directly.

## Technical Services and Types

Some technical services are also public, and are used like any other service
port, through `@UsedServices`:

- `AsyncServices`, with `AsyncParameters`, for asynchronous execution;
- `DependencyGraphServices`, which exposes the `DependencyGraph` of the
  component dependencies;
- `ComponentDescriptor`, `ItemDescriptor` and `ItemAndField`, the read-only
  types through which the registry describes a deployed component: its live
  instance, its metamodel, its items and composed components, the instances
  that provide, consume, offer or subscribe its ports, and the fields that
  use them. They are returned by `PortRegistryServices.getComponentDescriptor`
  and `DescriptorRegistryReadServices.readComponentDescriptor`.

Technical events, such as `LifecycleEvents` (component instantiation,
initialization, activation, passivation, removal and failure), are consumed the
same way as any application events: implement the events interface. Subscribing
to a technical component's events, or writing a new infrastructure-level
component that itself uses services provided by another internal component, is
a normal, supported use of the component model - not a boundary the
`@Internal` annotation is meant to discourage.

`SubscribeParameters` carries the options of a raw event subscription, such as
its filter. `ComponentState` describes the lifecycle state of a component, and
`CovalentContainerParameters.VerificationLevel` sets the strictness of the
structural and dynamic rules checking.

## Exceptions

The exceptions `ValidationException`, `NamingException`, `ReflectionException`
and `ComponentLifecycleException` are public: they are the exceptions Covalent raises to
application code. Calling through a port before its provider is declared
raises the JDK's own `IllegalStateException` instead, with a message naming
the missing provider - see [Proxy Errors](troubleshooting.md#proxy-errors).

# What Is Not Public

The internal implementation details are the classes, not their ports:
implementation classes, classes named `*Impl`, component managers, registry
implementations, and metamodel write-side services are not part of the public
API and are not meant to be referenced directly.

The services, events, and parameters that an internal component publishes are
ordinary Covalent ports, like any other component's. The `@Internal` annotation
marks Covalent infrastructure components in the runtime component model; it does
not by itself mean that a Java type, or its ports, are outside the public API. It
is reserved to Covalent: an application component carrying it is refused, whether
deployed or registered as a raw component.

# Stability Guarantee

Covalent guarantees a stable container as long as the APIs documented in these
guides are used as described. The other ports of Covalent's own (internal)
components, write-side services in particular, remain reachable through
ordinary `useServices` or `accessParameters` calls, for advanced uses, but
using them is at the user's own risk: a container left
inconsistent through them is outside this guarantee.

# Accessing Containers

Containers are obtained through the static `Covalent` entry point:

- `Covalent.main` is the default managed container and the simplest official
  mode for applications that only need one container;
- `Covalent.container()` creates a new isolated, unmanaged container;
- `Covalent.managedContainer("name")` returns a named managed container,
  creating it on first access for multi-container applications or supervision;
- `Covalent.getManagedContainers()` returns the currently known managed
  containers;
- `Covalent.getManagedContainerNames()` returns the currently known managed
  container names;
- `Covalent.getManagedContainer(name)` returns an existing managed container
  without creating it;
- `Covalent.disposeManagedContainer(name)` disposes a named managed
  container, stopping it first only if it is currently started; it then
  leaves the managed containers. Disposing a managed container directly, by
  its own `dispose()`, has the same effect. `Covalent.main` cannot be disposed
  this way;
- `Covalent.disposeManagedContainers()` disposes every managed container
  except `Covalent.main`;
- `Covalent.subscribe(observer)` and `Covalent.unsubscribe(observer)` manage
  the observers of `ManagedContainerEvents`: the creation and disposal of every
  managed container, and its lifecycle events relayed with its name (see
  [Observing Managed Containers](managed-components.md#observing-managed-containers)).

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Foundations](foundations.md) | [Next: Covalent Build and Requirements](requirements.md)
<!-- /doc-nav:bottom -->
