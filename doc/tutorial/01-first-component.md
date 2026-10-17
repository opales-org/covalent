# Step 1: Your First Component

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Covalent Tutorial](index.md) | [Next: Step 2: Services](02-services.md)
<!-- /doc-nav:top -->

In this step you write one component, deploy it in a container, start the
container, and stop it.

## The Ingredients

A managed Covalent component has two parts.

The **component type** is a tag interface: an empty interface annotated with
`@Component`. It is the public identity of the component. At runtime there is at
most one instance of a given component type.

```java
@Component
public interface Hello {
}
```

The **implementation** is a class annotated with `@Implementation` that
implements the component type. It holds the behavior.

```java
@Implementation
public static final class HelloImpl implements Hello {
```

The implementation can declare **lifecycle callbacks**. A method annotated with
`@Activation` is called when the component is activated, and a method annotated
with `@Removal` is called when the component is removed. There are also
`@Initialization` and `@Passivation`; we will come back to them in [step
5](05-lifecycle.md).

```java
@Activation
public void sayHello() {
    System.out.println("Hello from a Covalent component!");
}
```

## The Container

Components live in a container. `Covalent.main` is the default container, and the
simplest choice for an application that needs only one.

Starting an application takes two calls: deploy the implementations, then start
the container.

```java
container.deployComponents(HelloImpl.class);
StartupReport report = container.start();
```

`start()` instantiates, initializes and activates every deployed component, and
returns a `StartupReport` that tells whether the startup was `healthy`,
`degraded` or `failed`. `stop()` passivates, removes and undeploys them, and
returns the equivalent `ShutdownReport`.

## The Complete Program

```java
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Removal;

public final class Step1 {

    @Component
    public interface Hello {
    }

    @Implementation
    public static final class HelloImpl implements Hello {

        @Activation
        public void sayHello() {
            System.out.println("Hello from a Covalent component!");
        }

        @Removal
        public void sayGoodbye() {
            System.out.println("Goodbye from a Covalent component!");
        }
    }

    public static void main(final String[] args) {
        CovalentContainer container = Covalent.main;
        try {
            container.deployComponents(HelloImpl.class);
            StartupReport report = container.start();
            System.out.println("Startup status: " + report.getStatus());
            if (report.getStatus() != ContainerStatus.healthy) {
                throw new IllegalStateException("Covalent startup failed: " + report.getStatus());
            }
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
Hello from a Covalent component!
Startup status: healthy
Goodbye from a Covalent component!
```

The greeting is printed during `start()`, when the component is activated. The
farewell is printed during `stop()`, when it is removed.

> Note: The program stops the container in a `finally` block, guarded by
> `container.isStarted()`. `stop()` throws an exception when the container is not
> started; without the guard, a failure during deployment or startup would be
> hidden behind that second exception.

## What You Learned

- A component is a tag interface annotated with `@Component` plus an
  `@Implementation` class.
- Lifecycle callbacks such as `@Activation` and `@Removal` let a component react
  to the container.
- `deployComponents(...)` then `start()` is how an application starts; `stop()`
  shuts it down.

Reference: [Component Type](../managed-components.md#component-type),
[Implementation](../managed-components.md#implementation),
[Covalent Application Lifecycle](../managed-components.md#covalent-application-lifecycle).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Covalent Tutorial](index.md) | [Next: Step 2: Services](02-services.md)
<!-- /doc-nav:bottom -->
