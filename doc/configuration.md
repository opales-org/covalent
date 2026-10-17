# Covalent Configuration Reference

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Annotations Cheat Sheet](annotations.md) | [Next: Covalent Glossary](glossary.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Container Parameters](#container-parameters)
- [Internal Component Parameters](#internal-component-parameters)
  - [EventsManagerParameters](#eventsmanagerparameters)
  - [LifecycleParameters](#lifecycleparameters)
  - [AsyncParameters](#asyncparameters)
  - [Observing the Worker Pools](#observing-the-worker-pools)
    - [Reading the Worker Pools through JMX](#reading-the-worker-pools-through-jmx)
    - [Connecting a JMX Tool to the Application](#connecting-a-jmx-tool-to-the-application)
  - [ValidationParameters](#validationparameters)
  - [RawComponentsParameters](#rawcomponentsparameters)
- [Extensions](#extensions)
  - [Writing Your Own Extension](#writing-your-own-extension)

This page gathers the settings of the Covalent runtime that are otherwise described
across the guide. There are three ways to configure Covalent:

- the container parameters, through `container.getParameters()`;
- the parameters interfaces offered by the internal components of the container,
  accessed like any other parameters with `@AccessedParameters`;
- system properties.

Unless stated otherwise, change a setting **before** calling `start()`.

# Container Parameters

Available on any container, for example `Covalent.main.getParameters()`
(`CovalentContainerParameters`).

| Setting | Type | Default | Effect |
|---------|------|---------|--------|
| `setSynchronousEvents` | `boolean` | `false` | dispatches events on the producer thread instead of asynchronously. See [Synchronous or Asynchronous Events](managed-components.md#synchronous-or-asynchronous-events) |
| `setStructuralRulesChecking` | `VerificationLevel` | `trace` | strictness of the structural rules. See [Verification Level](managed-components.md#verification-level) |
| `setDynamicRulesChecking` | `VerificationLevel` | `trace` | strictness of the dynamic rules. See [Verification Level](managed-components.md#verification-level) |
| `setCleanStackTraces` | `boolean` | `false` | removes technical framework frames from exceptions; shared by every container of the JVM. See [Clean Stack Traces](managed-components.md#clean-stack-traces) |
| `setCircularDependencyChecking` | `boolean` | `false` | computes the dependency graph and reports circular dependencies at `start()`. See [Circular Dependency Detection](managed-components.md#circular-dependency-detection) |
| `setDependencyOrderedActivation` | `boolean` | `false` | orders managed component activation by the computed dependency graph's start order (dependencies before dependents) instead of declaration/deployment order; independent of `setCircularDependencyChecking`, which computes the same graph for circular-dependency reporting. See [Circular Dependency Detection](managed-components.md#circular-dependency-detection) |
| `setRegisterShutdownHook` | `boolean` | `true` | disposes the container from a JVM shutdown hook if it is not disposed at exit; a single hook per container, registered by the first `start()` that finds it enabled and removed by `dispose()` |
| `setInstrumented` | `boolean` | `false` | enables runtime instrumentation events, which add a cost to every call of the container; see [Overhead Measurements](supervision.md#appendix-overhead-measurements) |

`VerificationLevel` values are `silent` (ignored), `trace` (fine-grained log),
`warn` (warning log) and `strict` (exception). A common practice is `strict`
during development and in CI, so a broken rule fails the build or the test
immediately at the point it happens, and `trace` or `warn` in production, so
the same rule violation is logged rather than turned into a runtime failure.
`silent` disables the corresponding checks entirely, structural or dynamic;
it trades that diagnostic for the small overhead of skipping them. See
[Verification Level](managed-components.md#verification-level) for the full
behavior, including what each level does for the circular dependency check
enabled by `setCircularDependencyChecking` above.

# Internal Component Parameters

Access them from a component with an `@AccessedParameters` field of the given type.

## EventsManagerParameters

Configures the dispatch of events.

| Setting | Default | Effect |
|---------|---------|--------|
| `setSynchronous` | `false` | synchronous dispatch, like the container setting |
| `setWorkerCount` | number of available processors, at most `4` | threads of the asynchronous dispatch pool |
| `setWorkerQueueCapacity` | `1024` | capacity of the dispatch queue; a notification that does not fit is dropped and logged |

Worker count and queue capacity apply immediately to the running pool. See
[Asynchronous Event Dispatch
Configuration](managed-components.md#asynchronous-event-dispatch-configuration).

## LifecycleParameters

| Setting | Default | Effect |
|---------|---------|--------|
| `setStartMode` | `vertical` | `vertical` or `horizontal` startup. See [Vertical and Horizontal Start Modes](managed-components.md#vertical-and-horizontal-start-modes) |
| `setFindLifecycleMethodByNameConvention` | `false` | finds lifecycle callbacks by method name. See [Callback Method by Naming Convention](managed-components.md#callback-method-by-naming-convention) |

## AsyncParameters

Configures the resource limits of the asynchronous services of the container. Its
main settings are:

| Setting | Effect |
|---------|--------|
| `setUnhandledFailureHandler` | handler for the failures of fire-and-forget tasks |
| `setGeneralWorkerCount` | threads of the shared general pool (number of available processors, at most `4`, by default) |
| `setWorkerQueueCapacity` | capacity of the worker pool queues (`1024` by default) |
| `setDedicatedWorkerIdleReclaimEnabled`, `setDedicatedWorkerIdleTimeout` | reclaim of idle dedicated pools (idle timeout: 30 minutes by default) |
| `setDedicatedWorkerPoolCapReclaimEnabled`, `setMaxDedicatedWorkerPools` | cap on the number of dedicated pools (`256` by default) |

The general worker count and the queue capacity apply immediately, to the running
pools and to the existing dedicated queues.

See [Asynchronous Services](managed-components.md#asynchronous-services).

## Observing the Worker Pools

`AsyncWorkersServices` (async manager) and `EventsWorkersServices` (events manager)
tell supervision tools what the worker pools are doing. `readWorkerPools()` returns
one `WorkerPoolState` per pool: queued tasks and queue capacity, target worker
count, rejected tasks, cancelled tasks (queued tasks replaced by a newer one of the
same message key), and each worker thread with its current task and the
component that task works for: the one that submitted an async task, known from
its `AsyncServices` proxy, or the consumer of an event. Completed
tasks, queue waits and run durations accumulate from the pool's creation, the
durations as `DurationHistogram`s (powers of two microseconds): the difference
between two reads gives what happened in between, and its percentiles. So does
`componentBusyNanos()`, the time the tasks spent working for each component,
the running ones included: divided by the time between two reads, its difference
gives how many workers worked for each component on average, however short its
tasks.

Task tracking is a setting, so it belongs to the parameters of the managers:
`AsyncParameters` and `EventsManagerParameters`.

| Setting | Default | Effect |
|---------|---------|--------|
| `setTaskTracking` | `false` | tracks which task each worker runs, and how long tasks wait and run |

Task tracking costs almost nothing (see [Overhead Measurements](supervision.md#appendix-overhead-measurements)),
but it is only useful to a tool that watches, so it is off by default.
Without it, rejections, queue sizes and worker counts are still read; a worker is
not known to be in a task, and nothing is added to the histograms. The events pool
is listed only while events are dispatched asynchronously.

A task the pools refuse or replace is never lost silently, supervisor or not: a
full queue logs a `WARNING` and makes the shutdown report `degraded`, and pending
tasks cancelled when a pool stops are logged as well.

### Reading the Worker Pools through JMX

A named container (created with `Covalent.managedContainer(name)`) registers an
MXBean in the platform MBean server while it is started, whether the supervisor
runs or not:

`org.opales.covalent:type=Container,name=<container name>`

Its `WorkerPools` attribute is a table with one row per pool (the events pool, the
general async pool and the dedicated ones): `name`, `kind`, `targetWorkerCount`,
`queuedTasks`, `queueCapacity`, `rejectedTasks`, `cancelledTasks`, `completedTasks`
and `taskTracking`. JConsole, VisualVM or any JMX client shows it. The counters
accumulate from the pool's creation: a monitoring tool takes the difference
between two reads. `rejectedTasks` and `cancelledTasks` count whether task
tracking is on or not; `completedTasks` only counts while it is on.

The MBean is read-only. An unnamed container (`Covalent.container()`) registers
nothing. A failure to register is logged at `WARNING` and never prevents the
container from starting.

| System property | Default | Effect |
|-----------------|---------|--------|
| `covalent.jmx.enabled` | `true` | `false` registers no container MBean |
| `covalent.jmx.port` | absent | opens the JVM's remote JMX connector at this port, or the first free one after it |

### Connecting a JMX Tool to the Application

JConsole and VisualVM list the local JVMs without any option, but a tool that
connects by address, such as JConsole's "Remote process", needs the JVM to open a
JMX port. Covalent opens it itself when the system property `covalent.jmx.port` is
set, for example `-Dcovalent.jmx.port=9010`; nothing is opened when it is absent.
The examples module of this repository passes it for you when launched with the
`jmx` Maven profile (`-Pjmx`, port 9010, changed with `-Dexamples.jmxPort=9020`).

The port can already be in use, typically by another Covalent application started on
the same machine. Covalent then tries the next ports, one by one, up to 100 ports
from the one requested, and logs the one it kept:

```
JMX remote connector listening on port 9011 (port 9010 was taken): service:jmx:rmi:///jndi/rmi://localhost:9011/jmxrmi
```

The tool connects to `localhost:9011`, or to that service URL. Applications running
side by side each get their own port, so the same `-Dcovalent.jmx.port=9010` serves
them all. The connector is opened by the first named container of the JVM that
starts and closed by the last one that stops; all the named containers of the JVM
are reachable through it.

The connector accepts connections from the local machine only and has neither
authentication nor SSL, which is why it is off by default. If the port range is
exhausted or the value is not a port number, a `WARNING` is logged and the container
starts normally, without a connector. The connector never keeps the JVM alive: once
the application has no thread of its own left, the connector closes and the JVM exits.

## ValidationParameters

Fine-grained control of the verification, in addition to the two verification levels.
The naming convention checks can each be disabled individually:

| Setting | Naming convention |
|---------|-------------------|
| `setCheckUseServicesFieldNamingConvention` | used services fields |
| `setCheckAccessedParametersFieldNamingConvention` | accessed parameters fields |
| `setCheckProduceEventsFieldNamingConvention` | produced events fields |
| `setCheckUseSubscriberFieldNamingConvention` | used events subscribe fields |
| `setCheckEventsMethodNamingConvention` | events method names |

`setStructuralRulesVerificationLevel` and `setDynamicRulesVerificationLevel` set the two
verification levels, like the container parameters above.

## RawComponentsParameters

| Setting | Default | Effect |
|---------|---------|--------|
| `setDefaultStateToReach` | `activated` | lifecycle state reached when a raw component is registered. See [Register an Existing Object as a Raw Component](raw-components.md#register-an-existing-object-as-a-raw-component) |

# Extensions

Optional extensions are started with the container. List their identifiers,
separated by commas, in the `covalent.extensions` system property:

```text
-Dcovalent.extensions=component-viewer
```

`component-viewer` is provided by the `covalent-supervision` module, which must be
on the classpath. It makes the supervisor available without opening it. Two system
properties adjust that:

| Property | Default | Effect |
|----------|---------|--------|
| `covalent.supervision.startup` | `dormant` | `dormant` leaves the supervisor to the keyboard shortcut and to the JMX operation; `open` also opens it, once, as soon as the first container has started |
| `covalent.supervision.shortcut` | `Ctrl+Shift+F12` | the key combination that summons the supervisor, written as in `javafx.scene.input.KeyCombination` (for example `Ctrl+Shift+F11`); `none` removes the shortcut |

See [Summoning the Supervisor](supervision.md#summoning-the-supervisor).

## Writing Your Own Extension

An extension is any class implementing `CovalentExtension`
(`org.opales.covalent.container.extension`), registered through the standard
Java `ServiceLoader` mechanism:

```java
public final class MetricsExtension implements CovalentExtension {

    @Override
    public String getId() {
        return "metrics";
    }

    @Override
    public void onContainerStarted(final CovalentContainer container) {
        // start whatever this extension does, using the started container
        // like any other Covalent client code - rcs(), lcs(), services, etc.
    }
}
```

Register it in a `META-INF/services/org.opales.covalent.container.extension.CovalentExtension`
file, one fully qualified implementation class name per line:

```text
com.example.MetricsExtension
```

The extension then starts the same way as `component-viewer`: add its id to
the `covalent.extensions` system property.

```powershell
-Dcovalent.extensions=metrics
```

Several extensions can be requested together, comma-separated
(`-Dcovalent.extensions=component-viewer,metrics`); each is started after every
successful start of the container, so an extension must tolerate being started
again after a `stop()`. An identifier that matches no extension on the classpath
is logged as a warning and ignored. An extension that fails to start does not
prevent the others, nor the container, from starting: the failure is logged and
the `StartupReport` is `degraded`. See [Public API](public-api.md) for
where `CovalentExtension` sits relative to the rest of Covalent's public
surface.

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Annotations Cheat Sheet](annotations.md) | [Next: Covalent Glossary](glossary.md)
<!-- /doc-nav:bottom -->
