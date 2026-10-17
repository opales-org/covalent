# Covalent Foundations

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent User Guide](user-guide.md) | [Next: Covalent Public API](public-api.md)
<!-- /doc-nav:top -->

# Table of Contents

- [What Is a Component Model](#what-is-a-component-model)
- [What Is a Container](#what-is-a-container)
  - [How Component Containers Work](#how-component-containers-work)
  - [What Is the Dependency Inversion Principle](#what-is-the-dependency-inversion-principle)
  - [What Is Dependency Injection](#what-is-dependency-injection)
  - [What Is Inversion of Control](#what-is-inversion-of-control)
- [Managed and Raw Components](#managed-and-raw-components)
- [Covalent Main Features](#covalent-main-features)
- [When To Use Covalent](#when-to-use-covalent)
- [Compared To Other Approaches](#compared-to-other-approaches)

# What Is a Component Model

A software component is a unit of code with an explicit structure and explicit
interaction points, called ports.

A component model defines how components are structured, how they interact, how
they are managed by a runtime, and which quality-of-service features are
available. In many component models, this structure is represented by a
metamodel: a runtime description of components, ports, connections, lifecycle
state, and related metadata.

One key idea of component-based software engineering, or CBSE, is to keep a
clear boundary between data and processing. This is not the only possible design
choice: in object-oriented programming, behavior is often placed directly on the
objects that hold the data. Martin Fowler famously described excessive
separation between data and behavior as the Anemic Domain Model anti-pattern.

However, component-based architecture can be valuable when an application grows.
Processing logic often changes faster than the data model. Keeping processing in
explicit components can make the data model more stable and make architectural
dependencies easier to control.

The main goal is to reduce the complexity that appears when a large codebase is
organized only as a network of fine-grained objects. Components have a coarser
granularity than classes. They provide clearer architectural boundaries, more
explicit dependencies, and a better runtime model for large applications.

Component-based architectures can also reduce boilerplate. Publishing services,
connecting service users to providers, subscribing event consumers to event
producers, and managing lifecycle transitions are repetitive tasks. A component
container can centralize this work so application code remains focused on
business behavior.

As Jeff Atwood put it, in ["The Best Code is No Code At
All"](https://blog.codinghorror.com/the-best-code-is-no-code-at-all/):

> The best code is no code at all.

# What Is a Container

A container is the runtime responsible for hosting components and providing
technical services such as quality-of-service features, lifecycle management,
service wiring, event wiring, and parameter injection.

In Covalent, the container is called `CovalentContainer`. It is itself a
Covalent component, composed of several internal Covalent components that
implement different parts of the component model, such as services and events.

For applications that need several managed containers, Covalent keeps track of
the default container and of every named managed container created through the
static `Covalent` entry point. This allows tooling such as the component viewer
to inspect and switch between managed containers at runtime without creating
new ones implicitly.

## How Component Containers Work

Using a component container can feel unusual at first. Components appear to find
each other automatically, services are injected, events are connected, and
lifecycle callbacks are invoked by the runtime.

This behavior is not magic. It comes from the combination of two architectural
principles and one design pattern: the Dependency Inversion Principle,
Inversion of Control, and Dependency Injection.

## What Is the Dependency Inversion Principle

The Dependency Inversion Principle, or DIP, is a software design and
architecture principle that can be summarized as follows:

> High-level modules must not depend on low-level modules. Both must depend on abstractions.

At its simplest level, DIP encourages code to depend on interfaces rather than
concrete implementations. This reduces coupling between high-level application
logic and lower-level implementation details.

DIP can also be applied at a broader architectural level. Technical concerns
such as threading, event dispatching, cloning, lifecycle management, or service
wiring should not leak into business code. Instead, the runtime infrastructure
should adapt itself around the business code.

This leads to another useful formulation of DIP:

> Business code must not depend on technical code. Instead, technical code must depend on business code.

Covalent applies this principle through interception. Technical delegates are
inserted between components and are responsible for handling framework-level
behavior, while component code remains focused on application behavior.

## What Is Dependency Injection

Dependency Injection, or DI, is a design pattern that reduces coupling between
objects and components. Instead of creating or looking up the objects it needs,
a component declares its dependencies and lets an external mechanism provide
them.

This declaration may be done through configuration, convention, or annotations.
The component can then depend on interfaces rather than concrete
implementations, which makes it easier to test, replace, and reuse.

In Covalent, dependency injection is implemented with the Java Reflection API.
Fields are injected automatically by the container. They can be private
and even final, so encapsulation does not require public setters.

For instance:

```java
// an object implementing MyServices interface will be
// automatically injected in myServices field by the container
@Services
private MyServices myServices;

@Services
private final MyOtherServices myOtherServices = null;
```

## What Is Inversion of Control

Inversion of Control, or IoC, means that part of the program execution flow is
controlled by a framework rather than directly by application code.

This is one of the main differences between a library and a framework. A
library is called by user code. A framework calls user code at the appropriate
time, according to its own runtime model.

DIP and DI mainly describe how dependencies are defined and supplied. IoC
describes who controls the execution flow. In a component container, this is
especially important for lifecycle management, event dispatching, service
wiring, and threading concerns.

By delegating these responsibilities to the container, business code can remain
smaller, clearer, and better separated from technical infrastructure.

> Note: Inversion of Control has sometimes been humorously described as
> _The Hollywood Principle_: "Don't call us, we'll call you."

# Managed and Raw Components

Covalent is designed to support different levels of framework integration.

When the container manages both the component lifecycle and its ports, the
component is a managed component. When the container manages only part of the
component, it is a raw component, also called a non-managed component.

A raw component can be any externally instantiated POJO. It does not need to
extend a framework class, implement a framework interface, or use Covalent
annotations.

Covalent provides a raw components API through the `RawComponentsServices`
interface. This API allows raw components to declare provided services or
offered parameters, use other components' services, access parameters, publish
events, and consume events produced by other components.

Externally instantiated raw components must be registered in the Covalent
container. Once registered, they are partially managed by the container.
For example, if they provide lifecycle callbacks, these callbacks are invoked in
the same way as callbacks on managed components.

During raw component registration, the container tries to build an
internal representation of the component in the metamodel. However, because raw
components declare their structure through API calls rather than annotations,
the available information may not be sufficient to build a complete metamodel.

The raw components API can also be used to connect ordinary Java code to managed
or raw Covalent components. This makes it possible to integrate Covalent into an
existing application, or to interface Covalent components with other component
models and frameworks.

The recommended way to use Covalent is to implement managed components. Managed
components explicitly define their structure and quality-of-service features
through Java annotations, and delegate their lifecycle management to the
container.

This gives the component access to the full Covalent feature set. When a managed
component is deployed, the container introspects it and builds a complete
metamodel representation, which gives the container better control over
lifecycle transitions, connections, and quality-of-service behavior.

Some features are only available through Covalent annotations, not through the
raw components API. Automatic event data cloning is one example.

# Covalent Main Features

Covalent provides the following component model and container features:

- ED-SOA support: components can expose services and publish events through
  explicit interfaces.
- Component reuse through configurable parameters.
- A complete lifecycle model, from deployment to activation, passivation, and
  undeployment.
- Automatic wiring of services, parameters, events, and event subscribers.
- Event filtering, optional manual subscription, and subscription delegation.
- Automatic cloning of event data when required by the event contract.
- Automatic asynchronous event dispatching.
- Component segmentation and composition.

All of these features are explained in this guide.

# When To Use Covalent

Covalent is a good fit when you want a Java application to keep an explicit,
inspectable structure as it grows:

- **Explicit component boundaries.** Components declare their services, events and
  parameters through interfaces, and go through a well-defined lifecycle, so the
  architecture is visible in the code and not hidden in object graphs.
- **Sophisticated events.** Event filters, subscription delegation, automatic cloning
  of event data and asynchronous dispatch are part of the model.
- **Structuring at two scales.** Composition organizes a large application as a tree
  of components, and items segment the implementation of a single component.
- **Configuration through typed interfaces.** Parameters are declared like services,
  and are available from the initialization of a component.
- **A robust and observable container.** Lifecycle failures are contained and
  reported, and the running container can be inspected with the component viewer.
- **Progressive adoption.** Existing objects can be integrated as raw components,
  without rewriting the application in one step.
- **A small footprint.** The core has no third-party runtime dependency.

Some current characteristics deserve attention before adopting it:

- there is at most one instance of a given component type, and several
  implementations of the same component type are not supported (see [Component Type](managed-components.md#component-type));
- Covalent targets Java 21 and runs on the classpath: running managed applications
  as named JPMS modules is not supported, and GraalVM native-image has not been
  validated (see [Runtime Requirements and Compatibility](requirements.md#runtime-requirements-and-compatibility));
- it relies on reflection and dynamic proxies, which implies rules such as the
  [instantiation contract](requirements.md#instantiation-contract-for-component-implementations) for component
  implementations;
- its scope is the component model and its container, as described in [Covalent Main
  Features](#covalent-main-features).

# Compared To Other Approaches

Java already has mature ways to structure an application. This section is an
honest comparison, not a case against them: pick the tool that fits the
problem.

**Spring.** Spring is a dependency-injection container first, with a very large
ecosystem built around it (Spring Boot, Spring Data, Spring Cloud, and more). Its
breadth is not something Covalent tries to match: Covalent has no starters, no
auto-configuration, no data-access or web layer.

**Guice.** Guice is a lighter dependency-injection library, with no built-in event
model, no lifecycle beyond instance scoping, and no observability tooling.
Covalent overlaps with what Guice covers (service publication and lookup through
`@UsedServices`), but is not a general-purpose DI library: it has an opinion
about components, ports and lifecycle that Guice deliberately leaves open.

**OSGi.** OSGi is the closest in ambition: a service registry, a component
lifecycle, and an event admin service. It is also the closest in complexity:
bundle classloading, versioned package imports and exports, and a container of
its own (Equinox, Felix). Covalent has no module isolation or classloading
story - components share the same classpath and the same classloader - which is
a deliberate simplification, not an oversight. If you need true runtime
module isolation, OSGi's scope goes further than Covalent's.

**Feature by feature.** ✅ built in, 🟡 partial or possible but not first-class,
❌ not available. The first block covers the component-model and container
ground the four share; the second block turns the comparison around and
lists capability areas where Spring, and to a lesser degree OSGi, go beyond
Covalent - included so the table does not read as one-sided.

| | Covalent | Spring | Guice | OSGi |
|---|:---:|:---:|:---:|:---:|
| Per-subscription event filters | ✅ | ❌ | ❌ | ✅ |
| Producer notified when a consumer subscribes | ✅ | ❌ | ❌ | ❌ |
| Automatic event data cloning | ✅ | ❌ | ❌ | ❌ |
| Automatic subscription (implement the interface) | ✅ | 🟡 | ❌ | 🟡 |
| Explicit lifecycle (deploy, activate, passivate, remove, undeploy) | ✅ | 🟡 | ❌ | ✅ |
| Configuration exposed as typed ports, like services | ✅ | 🟡 | ❌ | 🟡 |
| Item segmentation of one component | ✅ | ❌ | ❌ | ❌ |
| Composite components (owned sub-components, tied lifecycle) | ✅ | 🟡 | ❌ | ❌ |
| Progressive adoption of existing objects (raw components) | ✅ | 🟡 | 🟡 | 🟡 |
| Configurable structural/dynamic verification | ✅ | ❌ | 🟡 | ❌ |
| Several independent containers in one process | ✅ | 🟡 | ✅ | 🟡 |
| Built-in runtime viewer | ✅ | 🟡 | ❌ | 🟡 |
| Structured startup/shutdown reporting and container status | ✅ | 🟡 | ❌ | 🟡 |
| Optional runtime extensions via a public SPI (`ServiceLoader`-style) | ✅ | 🟡 | ❌ | ✅ |
| Zero third-party runtime dependency (core) | ✅ | ❌ | 🟡 | 🟡 |
| Web layer (HTTP endpoints, MVC or similar) | ❌ | ✅ | 🟡 | 🟡 |
| Data access / ORM integration (repositories, JPA, JDBC) | ❌ | ✅ | 🟡 | 🟡 |
| Convention-based auto-configuration | ❌ | ✅ | ❌ | ❌ |
| Cloud-native tooling (service discovery, config servers, circuit breakers) | ❌ | ✅ | ❌ | ❌ |
| Classloader-level module isolation | ❌ | ❌ | ❌ | ✅ |
| Large third-party ecosystem and integrations | ❌ | ✅ | 🟡 | 🟡 |

A few rows worth a word. Spring's listener beans are auto-discovered too
(🟡, not ❌) - the mechanism differs (`@EventListener`/`ApplicationListener`
versus implementing the events interface) more than the outcome. OSGi's
`EventAdmin` supports topic and property-based filtering, which is why it
gets a ✅ on that row despite the complexity difference described above.
Spring's `@ConfigurationProperties` classes are a real typed-configuration
mechanism (🟡, not ❌), but bound once at startup via reflection rather than
modeled as a port with its own lifecycle timing the way a Covalent parameter
is; OSGi's `ConfigurationAdmin` is dynamic and first-class (also 🟡), but
property dictionaries, not strongly-typed interfaces. On the second block,
Startup/shutdown reporting and status: Spring Boot Actuator gives genuinely
strong health and metrics endpoints (🟡, not ❌), but it is a separate
starter dependency, not something core Spring provides on its own; OSGi has
framework and bundle lifecycle events plus some monitoring specifications
(also 🟡), rather than one cohesive report object. On the extension SPI row,
Spring's own auto-configuration classes are discovered the same
`ServiceLoader`-adjacent way (a `META-INF` file listing implementation
classes), which is why it is 🟡 and not ❌ despite being a different shape
of extension (auto-wiring beans, not an opt-in callback); Guice's Modules
are the closest primitive, but they are wired explicitly in code, not
discovered - no equivalent of an opt-in flag activating a third-party jar's
extension, hence ❌; OSGi's bundle and service registry is this same idea
taken further, with extensions installable and removable at runtime, not
just at startup - hence ✅.

Guice's official `guice-servlet` and `guice-persist` extensions cover basic
web and JPA integration respectively (🟡, not ❌), and OSGi's HTTP Service
and JPA Service specifications play the same role (🟡) - real, but neither
central to the framework nor comparable in depth to Spring's.

A few qualities run across most of these rows rather than sitting in one of
them.

Configuration by naming convention has no real equivalent in any of the
three: an event filter method is recognized by name alone - the event
method's name with `on` replaced by `filter`, matched automatically to that
event (see [Event Filters](managed-components.md#event-filters)) - and
lifecycle callbacks can optionally be recognized the same way
(`initializeComponent`, `activateComponent`, `passivateComponent`,
`removeComponent` - see [Callback Method by Naming Convention](managed-components.md#callback-method-by-naming-convention)),
neither one needing an annotation.

The validation model and raw components are deliberately complementary,
rather than one mechanism trying to serve both worlds: managed components get
configurable structural and dynamic verification - annotations, naming
conventions, port declarations, runtime operations, checked at a level you
choose - while raw components skip that machinery entirely and are governed
instead by a single fixed, always-on contract check that keeps the two worlds
cleanly separated (see [Register an Existing Object as a Raw Component](raw-components.md#register-an-existing-object-as-a-raw-component)).
Neither model is asked to do the other's job.

The container itself is extensively configurable, and so is nearly every
technical component behind it, through the same mechanism an application uses
for its own components: `CovalentContainerParameters` alone exposes
verification levels and event dispatch mode among others, and internal
components such as the async subsystem, the lifecycle manager, and raw
components each publish their own `@AccessedParameters` port
(`AsyncParameters`, `LifecycleParameters`, `RawComponentsParameters`) rather
than living behind a separate settings system.

And flexibility itself: raw components for what managed components do not fit,
verification that relaxes from `strict` to `silent`, vertical or horizontal
startup, and annotations usable at either the type or the field level -
`@Item`/`@ComponentItem`, `@Services`/`@UsedServices` - for the same concept,
so a codebase can pick semantic rigor or a lighter, more approachable style as
it prefers.

**What this means in practice.** Covalent is a component model and a container
for a single JVM process: no bean-factory reflection sprawl, no bundle
classloading, and events, lifecycle and observability as first-class parts of
the model rather than assembled from separate libraries. It is not a full
application framework: no web layer, no data access, no auto-configuration. See
[When To Use Covalent](#when-to-use-covalent) above for what Covalent itself is
and is not a good fit for.

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent User Guide](user-guide.md) | [Next: Covalent Public API](public-api.md)
<!-- /doc-nav:bottom -->
