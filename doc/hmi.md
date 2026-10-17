# Covalent HMI Guide

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Supervision Guide](supervision.md) | [Next: Covalent Examples Guide](examples.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Covalent HMI](#covalent-hmi)
- [HmiCore](#hmicore)
- [ActionManager](#actionmanager)
  - [Providing An Action](#providing-an-action)
  - [Consuming An Action](#consuming-an-action)
  - [Observing Every Action](#observing-every-action)

# Covalent HMI

`covalent-hmi` is an optional module: JavaFX HMI runtime integration for
Covalent, built on top of `covalent-core`. It is not required to build or
run a Covalent application - add it to the classpath only when building a
JavaFX-based UI on top of Covalent components.

It provides two managed Covalent components: `HmiCore` and `ActionManager`.
Unlike most of Covalent's own infrastructure, neither is marked
`@Internal` - they are meant to be used directly by an application, not
just looked at.

[`covalent-supervision`](supervision.md#covalent-supervision) is itself
built on `covalent-hmi`, using `ActionManager` for its own UI triggers -
one example of what this module is for, alongside an application's own
JavaFX UI.

# HmiCore

`HmiCore` is the composite component that boots the HMI runtime
integration. Its one job is composing `ActionManager`, which the container
deploys and activates with it:

```java
@Implementation
public class HmiCoreImpl implements HmiCore {

    @ComposedComponent
    ActionManagerImpl actionManager;
}
```

# ActionManager

`ActionManager` decouples a JavaFX UI's triggers - a button, a menu item -
from whichever component actually implements the behavior behind them. An
action is identified by an `ActionKey`, a marker interface an application
implements itself, typically with an enum:

```java
enum ApplicationActions implements ActionKey {
    SAVE, DELETE
}
```

Its services are split by role: a component is injected with the
interface matching which side of an action it is on, never more than one.

## Providing An Action

The component that owns the behavior registers it, through
`ActionProviderServices`:

```java
@UsedServices
private ActionProviderServices actionProviderServices;

// ...

this.actionProviderServices.registerActionExecution(ApplicationActions.SAVE, this::save);
```

Registration accepts zero, one, or two input-data arguments.
`unregisterActionExecution` removes it. The same interface also lets the
provider report its own state unconditionally, whether or not anything
currently listens: `setActionAvailable` for whether the action can run
right now, and `setActionValue` for a value the provider owns and can
change on its own - a scale changed through a gesture the provider itself
handles, for example, rather than through `executeAction`.

## Consuming An Action

The component that triggers or displays an action - typically a View -
uses `ActionConsumerServices`:

```java
@UsedServices
private ActionConsumerServices actionConsumerServices;

// ...

boolean executed = this.actionConsumerServices.executeAction(ApplicationActions.SAVE);
```

Execution accepts zero, one, or two input-data arguments, matching the
provider's registration, and returns whether an available action
implementation actually ran. The same interface binds a JavaFX node's
enabled state to an action's availability, so a button disables itself
automatically when its action cannot currently run:

```java
this.actionConsumerServices.bindActionAvailability(ApplicationActions.SAVE, saveButton);
```

and subscribes to a provider-reported value the same way, with a callback
rather than a fixed widget property, since a value has no single place to
be written to - the callback runs immediately with the current value, if
one is already known, and again on every change:

```java
this.actionConsumerServices.bindActionValue(ApplicationActions.SAVE, value -> updateLabel(value));
```

`unbindActionAvailability` and `unbindActionValue` remove a binding, using
the same node or callback reference passed to bind it - needed before
rebinding, for example a shared toolbar switching what it controls.

## Observing Every Action

A third party interested in every action execution, independently of any
specific provider or consumer, implements `ActionEvents` and registers
through `ActionObservationServices`:

```java
@UsedServices
private ActionObservationServices actionObservationServices;

// ...

this.actionObservationServices.addActionEventsListener(this);
```

`onActionTriggered(actionKey, actionCaller, actionImplementer,
inputData...)` reports which component asked for the action and which one
ran it. `removeActionEventsListener` removes the listener.

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Supervision Guide](supervision.md) | [Next: Covalent Examples Guide](examples.md)
<!-- /doc-nav:bottom -->
