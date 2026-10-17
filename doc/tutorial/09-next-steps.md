# Step 9: Next Steps

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 8: Existing Objects](08-existing-objects.md) | [Next: Covalent User Guide](../user-guide.md)
<!-- /doc-nav:top -->

You have seen the main ideas of Covalent: components, services, events,
parameters, lifecycle, segmentation, composition, and integration of existing
objects. Here are pointers to go further.

## Observe Your Application With The Component Viewer

A running Covalent container can be inspected. The component viewer is an optional
extension, provided by the `covalent-supervision` module, that lets you look at the
components of a container. Enable it by adding that module to the classpath and setting the
following system property:

```text
-Dcovalent.extensions=component-viewer
```

The viewer opens on demand: press <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>F12</kbd> in a window of
your application, or see [Summoning the Supervisor](../supervision.md#summoning-the-supervisor)
for the other ways.

## When Something Goes Wrong

The [troubleshooting page](../troubleshooting.md) lists common symptoms (validation
errors, proxy errors, lifecycle failures, circular dependencies, asynchronous
failures) and the first checks to make.

## Where To Read More

| If you want to... | Read |
|-------------------|------|
| understand the ideas behind the model (DIP, DI, IoC) | [Foundations](../foundations.md) |
| know every option of managed components | [Managed Components Guide](../managed-components.md) |
| adopt Covalent progressively | [Raw Components Guide](../raw-components.md) |
| see full applications built on Covalent | [Examples Guide](../examples.md) |
| build a JavaFX UI on top of Covalent components | [HMI Guide](../hmi.md) |
| find the documentation index | [User Guide](../user-guide.md) |

## Exercises

- Add a `Bakery` component that consumes `OrderEvents` and only wants `croissant`
  orders, using a filter (step 6).
- Register a second existing object as a raw component and let a managed component
  use both (step 8).
- Read the tax rate of step 4 back from a second component, using the getter of
  the same parameters interface (step 4).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 8: Existing Objects](08-existing-objects.md) | [Next: Covalent User Guide](../user-guide.md)
<!-- /doc-nav:bottom -->
