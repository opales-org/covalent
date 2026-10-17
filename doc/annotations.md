# Covalent Annotations Cheat Sheet

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Rubik's Cube Solver - Architecture](architecture/examples/cube-solver-demonstration/index.md) | [Next: Covalent Configuration Reference](configuration.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Component Structure](#component-structure)
- [Services, Parameters And Events](#services-parameters-and-events)
- [Lifecycle Callbacks](#lifecycle-callbacks)
- [Infrastructure](#infrastructure)
- [Where Each Annotation Lives](#where-each-annotation-lives)
- [Naming Conventions](#naming-conventions)

Every annotation of the Covalent component model on one page. For each one, the
table gives where it goes and what it means; follow the link for the full
explanation. For a plain-language definition of the underlying concept, see
the [Glossary](glossary.md).

# Component Structure

| Annotation | Placed on | Meaning | Reference |
|------------|-----------|---------|-----------|
| `@Component` | interface | declares a component type (an empty tag interface) | [Component Type](managed-components.md#component-type) |
| `@Implementation` | class; field of a composite | declares the implementation class of a component, or a sub-component field in a composite | [Implementation](managed-components.md#implementation), [Composition](managed-components.md#composition) |
| `@Item` | class; field | declares a component item class, or the field that holds it | [Items](managed-components.md#items) |
| `@ComponentItem` | see [Items](managed-components.md#items) | alternative to `@Item` on the field that aggregates an item | [Items](managed-components.md#items) |
| `@ComposedComponent` | field | alternative to `@Implementation` on the field that owns a sub-component | [Composition](managed-components.md#composition) |

# Services, Parameters And Events

| Annotation | Placed on | Meaning | Reference |
|------------|-----------|---------|-----------|
| `@Services` | interface; field | declares a services interface; on a field, same as `@UsedServices` | [Services](managed-components.md#services) |
| `@UsedServices` | field | the component uses these services; the container injects the provider | [Using Services](managed-components.md#using-services) |
| `@Parameters` | interface; field | declares a parameters interface; on a field, same as `@AccessedParameters` | [Parameters](managed-components.md#parameters) |
| `@AccessedParameters` | field | the component accesses these parameters; the container injects them | [Accessing Parameters](managed-components.md#accessing-parameters) |
| `@Events` | interface; field | declares an events interface; on a field, same as `@ProducedEvents` | [Events](managed-components.md#events) |
| `@ProducedEvents` | field | the component produces these events | [Producing Events](managed-components.md#producing-events) |
| `@EventsSubscribe` | interface nested in an events interface | declares the subscribe interface of the events | [Event Subscription](managed-components.md#event-subscription) |
| `@UsedEventsSubscribe` | field | the component subscribes manually, through a subscribe interface | [Event Subscription](managed-components.md#event-subscription) |
| `@EventsFilter` | interface nested in an events interface | declares the filter interface of the events | [Event Filters](managed-components.md#event-filters) |
| `@Cloned` | events interface; event method; event method argument | clones the event arguments before delivery | [Event Data Cloning](managed-components.md#event-data-cloning) |

# Lifecycle Callbacks

These annotations go on a method of the implementation, which must take no
argument. Each one is equivalent to implementing the interface in the second column.

| Annotation | Equivalent interface | Called | Reference |
|------------|----------------------|--------|-----------|
| `@Initialization` | `Initializable` | once, when the component is initialized | [Lifecycle Callbacks](managed-components.md#lifecycle-callbacks) |
| `@Activation` | `Activable` | each time the component is activated | [Lifecycle Callbacks](managed-components.md#lifecycle-callbacks) |
| `@Passivation` | `Passivatable` | each time the component is passivated | [Lifecycle Callbacks](managed-components.md#lifecycle-callbacks) |
| `@Removal` | `Removable` | once, when the component is removed | [Lifecycle Callbacks](managed-components.md#lifecycle-callbacks) |

`ComponentLifecycleTransition` declares the four callbacks in a single interface.

# Infrastructure

| Annotation | Placed on | Meaning | Reference |
|------------|-----------|---------|-----------|
| `@Internal` | class | marks a component of the container itself, as opposed to an application component; reserved to Covalent, refused on application components | [Starting a Covalent Application](managed-components.md#starting-a-covalent-application) |

# Where Each Annotation Lives

- The component model annotations are in the package
  `org.opales.covalent.container.component.annotations`.
- The lifecycle annotations and interfaces are in
  `org.opales.covalent.container.lifecycle`.
- `@Internal` is in `org.opales.covalent.container.component.internals`.

# Naming Conventions

Covalent recommends naming conventions for the elements it injects and calls. They
are optional, unless structural rule verification is set to its strictest level;
each kind can also be disabled individually. See [Verification
Level](managed-components.md#verification-level).

| Element | Convention | Example |
|---------|------------|---------|
| services interface | ends with `Services` | `PricingServices` |
| used services field | `use` + services interface name | `usePricingServices` |
| parameters interface | ends with `Parameters` | `PricingParameters` |
| accessed parameters field | `access` + parameters interface name | `accessPricingParameters` |
| events interface | ends with `Events` | `OrderEvents` |
| event method | `on` + past-tense verb | `onOrderPlaced` |
| produced events field | `produce` + events interface name | `produceOrderEvents` |
| events subscribe interface | ends with `Subscribe` (mandatory) | `OrderEventsSubscribe` |
| used events subscribe field | `use` + subscribe interface name | `useOrderEventsSubscribe` |
| filter interface | events interface name + `Filter` (mandatory for managed components) | `OrderEventsFilter` |
| filter method | event method name with `on` replaced by `filter` (mandatory) | `filterOrderPlaced` |
| subscribe and unsubscribe methods | prefixed with `subscribe` and `unsubscribe` (mandatory) | `subscribe(observer)` |

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Rubik's Cube Solver - Architecture](architecture/examples/cube-solver-demonstration/index.md) | [Next: Covalent Configuration Reference](configuration.md)
<!-- /doc-nav:bottom -->
