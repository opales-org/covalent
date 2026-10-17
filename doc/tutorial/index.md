# Covalent Tutorial

<!-- doc-nav:top -->
[Home](../../README.md) | [Next: Step 1: Your First Component](01-first-component.md)
<!-- /doc-nav:top -->

This tutorial introduces Covalent through a small, runnable example: the
back office of a tiny coffee shop. Each step adds one idea, comes with a complete
program you can run, and shows the output you should get.

You do not need any prior knowledge of Covalent. If you are new to component
models in general, the [foundations](../foundations.md#what-is-a-component-model)
page gives the background, but you can also start here and read it later.

# What You Will Learn

| Step | Topic | What it covers |
|------|-------|----------------|
| [1](01-first-component.md) | Your first component | component type, implementation, container, start and stop |
| [2](02-services.md) | Services | request/response ports between components |
| [3](03-events.md) | Events | fire-and-forget notifications, automatic subscription |
| [4](04-parameters.md) | Parameters | configuring a component through its own interface |
| [5](05-lifecycle.md) | Lifecycle | callbacks, states, activating and passivating a component |
| [6](06-advanced-events.md) | Advanced events | manual subscription, filters, subscription delegation, cloning |
| [7](07-structuring-components.md) | Structuring components | items (segmentation) and composition |
| [8](08-existing-objects.md) | Existing objects | bringing a plain Java object into the runtime as a raw component |
| [9](09-next-steps.md) | Next steps | the component viewer, troubleshooting, where to read more |

# Before You Start

- **Java 21.** This is the only version Covalent is built and tested against.
- **Covalent on the classpath.** The core has no third-party dependency. From the
  repository root, build and install it in your local Maven repository:

  ```powershell
  mvn -pl covalent-core -am install
  ```

  `-am` also builds and installs the parent POM that `covalent-core` inherits
  from; without it, an external project fails to resolve `covalent-parent`
  even though `covalent-core` itself installed successfully.

  Then declare it in your project:

  ```xml
  <dependency>
      <groupId>org.opales</groupId>
      <artifactId>covalent-core</artifactId>
      <version>1.0.0</version>
  </dependency>
  ```

- **The classpath, not the module path.** Covalent uses reflection and runs in the
  unnamed module. See [Runtime Requirements and
  Compatibility](../requirements.md#runtime-requirements-and-compatibility).

# How To Read The Examples

Every step ends with a complete program in a single file, so you can copy it,
run it, and modify it. To keep each program in one file, the components are
written as nested `public static` classes of a launcher class. In a real
application, each component type and implementation would normally live in its own
file.

Covalent requires component implementations to be `public` classes with a
`public` no-argument constructor. The nested classes in the examples respect this
rule. See [Instantiation Contract for Component
Implementations](../requirements.md#instantiation-contract-for-component-implementations).

The output shown after each program is what the program prints on the standard
output. Covalent also logs a few `INFO` lines (container started, container
stopped) through `java.util.logging`, on the standard error; they are left out
here.

Ready? Let's start with [your first component](01-first-component.md).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Next: Step 1: Your First Component](01-first-component.md)
<!-- /doc-nav:bottom -->
