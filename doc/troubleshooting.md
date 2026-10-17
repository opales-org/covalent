# Covalent Troubleshooting

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Glossary](glossary.md) | [Next: Covalent FAQ](faq.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Exception Reference](#exception-reference)
- [Validation Errors](#validation-errors)
- [Proxy Errors](#proxy-errors)
- [Lifecycle Failures](#lifecycle-failures)
- [Mutual (Circular) Dependencies Between Components](#mutual-circular-dependencies-between-components)
- [Async Failures](#async-failures)
- [Event Consumer Failures](#event-consumer-failures)
- [Noisy Stack Traces](#noisy-stack-traces)

This page lists common runtime symptoms and the first checks to make when a
Covalent application fails during wiring, lifecycle transitions, proxy calls, or
asynchronous work.

## Exception Reference

Covalent raises four public exception types (see [Public API](public-api.md)). The type tells you which section below to
read.

| Exception | Thrown when | See |
|-----------|-------------|-----|
| `ValidationException` | a structural or dynamic rule is broken at `strict` verification level, or a required event data clone fails at `strict` | [Validation Errors](#validation-errors), [Event Data Cloning](managed-components.md#event-data-cloning) |
| `NamingException` | a field or method violates a checked naming convention at `strict` verification level | [Validation Errors](#validation-errors), [Annotations cheat sheet](annotations.md) |
| `ComponentLifecycleException` | a lifecycle operation (deployment, instantiation, initialization, activation, passivation or removal) fails on a component; the message starts with the phase and the component, as in `During activation of component Foo`, and the cause is the failure itself | [Lifecycle Failures](#lifecycle-failures) |
| `ReflectionException` | reflective instantiation or invocation fails, typically an `@Implementation` class or its no-argument constructor is not `public` | [Instantiation Contract for Component Implementations](requirements.md#instantiation-contract-for-component-implementations) |

`IllegalStateException` and `IllegalArgumentException`, from the JDK, are also
used. Most of the time this is for ordinary programming errors (calling
`stop()` on a container that is not started, registering a raw component
under an already-occupied component type, and similar cases), not part of the
component-model contract above. One specific case is common enough to name:
code that calls through a used service, accessed parameters, or used
subscriber proxy before its real provider has ever been declared gets an
`IllegalStateException` whose message names the missing provider's interface
- see [Proxy Errors](#proxy-errors) below.

## Validation Errors

Covalent validates both component structure and runtime operations. Structural
errors usually point to annotations, field declarations, component type /
implementation pairing, or invalid port declarations. Dynamic errors usually
point to runtime operations such as duplicate service usage, duplicate event
subscriptions, or missing providers.

When investigating a validation error:

- keep structural and dynamic verification enabled while developing;
- read the first Covalent message before the Java stack trace;
- check whether the failing element is a component type, implementation,
  service interface, events interface, parameters interface, item, or composed
  component;
- switch to stricter verification when a warning should fail fast.

Related reference: [Verification Level](managed-components.md#verification-level), [Annotations cheat sheet](annotations.md), [Configuration](configuration.md#container-parameters).

## Proxy Errors

Covalent ports are usually represented by proxies. A proxy error often means
that the port is no longer connected to an active provider or that the component
state does not allow the call.

Typical causes:

- a service provider was passivated, removed, undeployed or unprovided: the call
  fails with a message naming the missing or passivated provider, and works again
  by itself when the provider is back - a proxy obtained earlier does not have to
  be requested again;
- a component calls through a used service, accessed parameters, or used
  subscriber proxy before any provider for it has ever been declared -
  `IllegalStateException`, message names the missing provider's interface;
- a proxy is used after its own user ended it: the component that uses it was
  removed or unregistered, or `unuseServices` or `unaccessParameters` was called -
  `IllegalStateException`, message "is invalidated".

Check the provider lifecycle state first. A proxy is invalidated only when the
link of its user ends, never because its provider went away.

Related reference: [Lifecycle](managed-components.md#lifecycle), [Using Services](managed-components.md#using-services), [Raw components](raw-components.md).

## Lifecycle Failures

If instantiation, initialization, activation, passivation, or removal fails,
Covalent attempts technical cleanup and marks the entire owning application
composite `failed`. Cleanup does not invoke application passivation or removal
methods. A cleanup error is logged and does not prevent the `failed` state.

When a lifecycle callback fails:

- inspect the component state through `LifecycleServices`;
- check whether services or events were already published for the failed state;
- use `resetFailedComponent(componentType)` only after investigating the cause;
- the reset retries cleanup and returns the whole composite to `deployed` only
  when cleanup succeeds; a subsequent explicit activation creates fresh instances;
- call `stop()` on the affected container during tests to verify cleanup remains safe;
- look at structured Covalent logs for the transition that failed.

Direct lifecycle operations propagate the original failure after handling it.
Global startup continues with independent components after component-level
runtime exceptions. Failed components are not automatically restarted.
Lifecycle failure notifications are emitted after `postFail`, even if cleanup
was incomplete. Internal components do not use this application recovery policy.

For registered raw components, the internal `RawComponentsImpl` listener cleans
manual ports and subscriptions, then removes and undeploys the failed component.
If this cleanup fails, it logs the problem and leaves the component failed.
The original lifecycle failure is still propagated to the direct caller. The
external owner decides whether its instance can safely be registered again.

`stop()` removes every component, which ends the links of their users. After
stopping and restarting a container, reacquire service and parameter proxies
obtained before the stop: the old client proxies are invalidated.

Calling `dispose()` more than once - directly, from a `finally` block, and
from a JVM shutdown hook, in any combination - is expected and safe:
`dispose()` is idempotent and a repeat call is simply a no-op, whether the
container was stopped explicitly beforehand or never started at all.

Related reference: [Lifecycle](managed-components.md#lifecycle), [Vertical and Horizontal Start Modes](managed-components.md#vertical-and-horizontal-start-modes), [Disposing a Covalent Application](managed-components.md#disposing-a-covalent-application).

## Mutual (Circular) Dependencies Between Components

By default, Covalent does not detect or reject circular dependencies between
components, whether raw, managed, or a mix of both: two components that each
depend on a service provided by the other register successfully regardless
of which one registers first. An opt-in check, off by default, can detect
circular dependencies among managed components explicitly - see below.

This is safe by construction. `useServices`, `accessParameters`, and
`subscribe` (and the equivalent declarative `@UsedServices` /
`@AccessedParameters` ports on managed components) never require the
provider to already be published. When the requested service is not yet
bound, Covalent hands back a surrogate proxy instead of failing, and rebinds
it transparently to the real provider-side proxy once that provider is
created. The consuming component's field or local reference is never
refreshed manually: the same proxy instance keeps working before and after
the real provider appears.

Practical consequences:

- registering component A before component B, or B before A, produces the
  same wiring when they depend on each other's services;
- a raw component can call `RawComponentsServices.useServices(...)` for a
  service that a managed component provides, and a managed component can
  declare `@UsedServices` for a service that a raw component provides, in
  either activation order;
- what still fails, exactly as described in [Proxy Errors](#proxy-errors)
  above, is calling through the proxy before the real provider has
  published: the call reaches an unbound surrogate and fails. Declaring a
  circular dependency is safe; calling through it too early is not.

An opt-in check can detect a circular dependency explicitly and compute a
dependency-ordered start sequence - see
[Circular Dependency Detection](managed-components.md#circular-dependency-detection)
in the user guide. It is deliberately best-effort, not exhaustive: the check
runs once, at the very start of `start()`, over whichever managed components
are already deployed by then - a component deployed later, after `start()`
has begun, is not covered by it. Raw components are excluded by design, not
as a gap to close later: a raw component trades the managed model's
guardrails for freedom, and wiring its own dependencies - cycles included -
through `RawComponentsServices` directly is exactly what that freedom is
for. A component holding a direct Java reference to another component's
implementation, bypassing its proxy entirely, stays invisible to the check
either way.

Related reference: [Circular Dependency Detection](managed-components.md#circular-dependency-detection), [Configuration](configuration.md#container-parameters).

## Async Failures

Submitted async tasks expose their failure through their returned
`CompletionStage`. Fire-and-forget tasks have no caller-side result, so their
failures are routed to the global async failure handler configured through
`AsyncParameters`.

```java
this.accessAsyncParameters.setUnhandledFailureHandler((message, failure) -> {
    // log or route the failure according to the application policy
});
```

If a queued task is cancelled before execution, its future is completed with a
`CancellationException`.

Related reference: [Asynchronous Services](managed-components.md#asynchronous-services), [AsyncParameters](configuration.md#asyncparameters).

## Event Consumer Failures

An exception thrown by an event consumer's callback never reaches the
producer: it is caught, logged at `SEVERE` ("Event consumer callback
failed."), and dispatch continues with the other subscribers. This applies to
both synchronous and asynchronous dispatch, and follows directly from the
fire-and-forget contract (see [What Is an Event](managed-components.md#what-is-an-event)).

Consequences to keep in mind:

- a producer can never detect, from the call site, that a consumer failed;
- one failing consumer does not prevent other consumers of the same event from
  being notified;
- if consumer-side failures need to be observed, log them from within the
  consumer itself, or search application logs for that message text.

Related reference: [What Is an Event](managed-components.md#what-is-an-event), [Threading Model](managed-components.md#threading-model).

## Noisy Stack Traces

Framework dispatch often involves proxies, reflection, and asynchronous worker
frames. For application debugging, Covalent can remove these technical frames
before relaunching or logging exceptions.

```java
Covalent.main.getParameters().setCleanStackTraces(true);
```

This option preserves the exception type, message, cause, and suppressed
exceptions. It only removes stack frames that belong to technical dispatch
machinery.

Related reference: [Clean Stack Traces](managed-components.md#clean-stack-traces), [Configuration](configuration.md#container-parameters).

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Glossary](glossary.md) | [Next: Covalent FAQ](faq.md)
<!-- /doc-nav:bottom -->
