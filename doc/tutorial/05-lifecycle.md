# Step 5: Lifecycle

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 4: Parameters](04-parameters.md) | [Next: Step 6: Advanced Events](06-advanced-events.md)
<!-- /doc-nav:top -->

You have already used two lifecycle callbacks. This step shows the whole picture:
the transitions a component goes through, the callbacks attached to them, and how
to drive them yourself.

## The Transitions

A managed component goes through seven transitions:

1. **Deployment**: the implementation is introspected and its metamodel is created.
2. **Instantiation**: the classes of the component are instantiated.
3. **Initialization**: the component is prepared for use. This happens only once.
   It is the place to access parameters and prepare resources.
4. **Activation**: the services the component provides and the events it produces are
   published and can be used by other components. Activation can happen again after a
   passivation, so keep it fast.
5. **Passivation**: the provided services and produced events are unpublished. The
   offered parameters stay available. The component can be activated again.
6. **Removal**: the component releases its resources.
7. **Undeployment**: the component disappears from the container.

The first six transitions have an associated state: `deployed`, `instantiated`,
`initialized`, `activated`, `passivated` and `removed`. A seventh state, `failed`,
is reached when a transition fails (see [When A Transition Fails](#when-a-transition-fails)).

## Callbacks

A component reacts to the transitions of initialization, activation, passivation
and removal with callbacks. The simplest way to declare them is with annotations,
on any method of the implementation:

```java
@Initialization
public void prepare() {
```

```java
@Activation
public void open() {
```

```java
@Passivation
public void close() {
```

```java
@Removal
public void release() {
```

Callbacks can also be declared by implementing interfaces (`Initializable`,
`Activable`, `Passivatable`, `Removable`) or, if you enable it, by naming
convention. See [Lifecycle Callbacks](../managed-components.md#lifecycle-callbacks).

## When A Transition Fails

If a callback throws, the transition fails and the component does not stay half-way:
the whole application composite that owns it goes to the `failed` state. Covalent
cleans up its ports and instances without calling your passivation or removal
callbacks, and logs the problem. A failed component is skipped by `start()`, and
Covalent does not restart it by itself.

Once the cause is fixed, `resetFailedComponent` brings the composite back to
`deployed`:

```java
lifecycle.resetFailedComponent(Ticker.class);
```

Reset does not start the component. A later activation creates fresh instances.

## Driving The Lifecycle

`container.start()` is the simple way to bring everything to `activated`. When you
need finer control, `container.lcs()` returns the `LifecycleServices`, which can
read the state of a component and move it to a target state:

```java
LifecycleServices lifecycle = container.lcs();
```

```java
lifecycle.initializeComponent(Ticker.class);
System.out.println("State after initializeComponent: " + lifecycle.readComponentState(Ticker.class));
```

Lifecycle services target the component **type** (`Ticker.class`), not the
implementation. Deployment is the exception: it works on implementations and
must be done explicitly, first.

The lifecycle methods are smart: `activateComponent` initializes the component
first if it is not initialized yet.

## The Complete Program

```java
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

public final class Step5 {

    @Component
    public interface Ticker {
    }

    @Implementation
    public static final class TickerImpl implements Ticker {

        @Initialization
        public void prepare() {
            System.out.println("  [Ticker] initialization: prepare resources (once)");
        }

        @Activation
        public void open() {
            System.out.println("  [Ticker] activation: ports are published");
        }

        @Passivation
        public void close() {
            System.out.println("  [Ticker] passivation: ports are unpublished");
        }

        @Removal
        public void release() {
            System.out.println("  [Ticker] removal: release resources");
        }
    }

    public static void main(final String[] args) {
        CovalentContainer container = Covalent.main;
        LifecycleServices lifecycle = container.lcs();
        try {
            container.deployComponents(TickerImpl.class);
            System.out.println("State after deployment: " + lifecycle.readComponentState(Ticker.class));

            lifecycle.initializeComponent(Ticker.class);
            System.out.println("State after initializeComponent: " + lifecycle.readComponentState(Ticker.class));

            container.start();
            System.out.println("State after start: " + lifecycle.readComponentState(Ticker.class));

            lifecycle.passivateComponent(Ticker.class);
            System.out.println("State after passivateComponent: " + lifecycle.readComponentState(Ticker.class));

            lifecycle.activateComponent(Ticker.class);
            System.out.println("State after activateComponent: " + lifecycle.readComponentState(Ticker.class));
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
State after deployment: deployed
  [Ticker] initialization: prepare resources (once)
State after initializeComponent: initialized
  [Ticker] activation: ports are published
State after start: activated
  [Ticker] passivation: ports are unpublished
State after passivateComponent: passivated
  [Ticker] activation: ports are published
State after activateComponent: activated
  [Ticker] passivation: ports are unpublished
  [Ticker] removal: release resources
```

Look at how the callbacks are called:

- the component is initialized once, before `start()`, because the program asked
  for it; `start()` then only has to activate it;
- passivation and activation can alternate, and activation is called each time;
- `stop()` passivates the component, then removes it.

> Tip: To temporarily withdraw a component from the application while the container keeps
> running, passivate it with `LifecycleServices` and activate it again later. Do not
> use `stop()` and `start()` for this: they operate on the whole container.

## What You Learned

- A component goes from deployment to undeployment through well-defined states.
- Callbacks (`@Initialization`, `@Activation`, `@Passivation`, `@Removal`) let it react.
- `LifecycleServices` reads the state of a component and moves it, one component at a
  time.
- A failed transition puts the owning composite in `failed`; `resetFailedComponent`
  brings it back to `deployed`.

Reference: [Lifecycle](../managed-components.md#lifecycle) (with every failure path),
[Vertical and Horizontal Start Modes](../managed-components.md#vertical-and-horizontal-start-modes).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 4: Parameters](04-parameters.md) | [Next: Step 6: Advanced Events](06-advanced-events.md)
<!-- /doc-nav:bottom -->
