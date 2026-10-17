# Covalent User Guide

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Step 9: Next Steps](tutorial/09-next-steps.md) | [Next: Covalent Foundations](foundations.md)
<!-- /doc-nav:top -->

Covalent is a lightweight Java component model and runtime container.

It is designed for applications that need clearer runtime structure than plain
object graphs usually provide: explicit components, services, events,
parameters, lifecycle transitions, and a container-managed metamodel.

Covalent can be adopted progressively. Existing Java objects can first be
registered as raw components, while new or refactored code can use fully managed
components with annotated ports and lifecycle callbacks. This makes it possible
to improve architectural control without rewriting an application in one step.

The core runtime remains small and has no third-party dependency. The full
repository also contains JavaFX HMI tooling, tests, and examples.

> Note: To be more specific, Covalent is an [_ED-SOA_](managed-components.md#what-is-an-event) component model. Its container implements the _Dependency Inversion Principle_, using a declarative programming paradigm resting on Java annotations (all of this will be explained later). Also, the container is itself a Covalent component, decomposed into several sub-components using [composition](managed-components.md#composition).

# Table of Contents

## [Covalent Foundations](foundations.md)

- [What Is a Component Model](foundations.md#what-is-a-component-model)
- [What Is a Container](foundations.md#what-is-a-container)
  - [How Component Containers Work](foundations.md#how-component-containers-work)
  - [What Is the Dependency Inversion Principle](foundations.md#what-is-the-dependency-inversion-principle)
  - [What Is Dependency Injection](foundations.md#what-is-dependency-injection)
  - [What Is Inversion of Control](foundations.md#what-is-inversion-of-control)
- [Managed and Raw Components](foundations.md#managed-and-raw-components)
- [Covalent Main Features](foundations.md#covalent-main-features)
- [When To Use Covalent](foundations.md#when-to-use-covalent)
- [Compared To Other Approaches](foundations.md#compared-to-other-approaches)

## [Covalent Public API](public-api.md)

- [What Is Public](public-api.md#what-is-public)
  - [Container Entry Points](public-api.md#container-entry-points)
  - [Component Model Annotations](public-api.md#component-model-annotations)
  - [Integration APIs](public-api.md#integration-apis)
  - [Extension SPI](public-api.md#extension-spi)
  - [Technical Services and Types](public-api.md#technical-services-and-types)
  - [Exceptions](public-api.md#exceptions)
- [What Is Not Public](public-api.md#what-is-not-public)
- [Stability Guarantee](public-api.md#stability-guarantee)
- [Accessing Containers](public-api.md#accessing-containers)

## [Covalent Build and Requirements](requirements.md)

- [Build and Tests](requirements.md#build-and-tests)
- [Runtime Requirements and Compatibility](requirements.md#runtime-requirements-and-compatibility)
  - [Java Version and Module Configuration](requirements.md#java-version-and-module-configuration)
  - [GraalVM / Native-Image](requirements.md#graalvm--native-image)
  - [Security-Manager-Equivalent Constraints](requirements.md#security-manager-equivalent-constraints)
  - [Instantiation Contract for Component Implementations](requirements.md#instantiation-contract-for-component-implementations)

## [Covalent Managed Components Guide](managed-components.md)

- [Covalent Managed Components](managed-components.md#covalent-managed-components)
  - [Component Type](managed-components.md#component-type)
  - [Implementation](managed-components.md#implementation)
  - [Items](managed-components.md#items)
  - [Composition](managed-components.md#composition)
  - [Services](managed-components.md#services)
  - [Events](managed-components.md#events)
  - [Parameters](managed-components.md#parameters)
  - [Lifecycle](managed-components.md#lifecycle)
  - [Covalent Application Lifecycle](managed-components.md#covalent-application-lifecycle)
  - [Application Utility Services](managed-components.md#application-utility-services)
  - [Verification Level](managed-components.md#verification-level)

## [Covalent Raw Components Guide](raw-components.md)

- [Working with Raw Components](raw-components.md#working-with-raw-components)
  - [Performance Characteristics](raw-components.md#performance-characteristics)
  - [Component Type for Raw Components](raw-components.md#component-type-for-raw-components)
  - [Register an Existing Object as a Raw Component](raw-components.md#register-an-existing-object-as-a-raw-component)
  - [Provide Services](raw-components.md#provide-services)
  - [Use Services](raw-components.md#use-services)
  - [Offer and Access Parameters](raw-components.md#offer-and-access-parameters)
  - [Produce Events](raw-components.md#produce-events)
  - [Subscribe to Events](raw-components.md#subscribe-to-events)

## [Covalent Supervision Guide](supervision.md)

- [Covalent Supervision](supervision.md#covalent-supervision)
- [Cockpit](supervision.md#cockpit)
- [Components](supervision.md#components)
- [Views Library](supervision.md#views-library)
- [Sequences](supervision.md#sequences)
- [Dependency Graph](supervision.md#dependency-graph)
- [Threads](supervision.md#threads)
- [Exclusions](supervision.md#exclusions)
- [Settings](supervision.md#settings)
- [Writing a Custom Extension](supervision.md#writing-a-custom-extension)

## [Covalent HMI Guide](hmi.md)

- [Covalent HMI](hmi.md#covalent-hmi)
- [HmiCore](hmi.md#hmicore)
- [ActionManager](hmi.md#actionmanager)

## [Covalent Examples Guide](examples.md)

- [Covalent Examples](examples.md#covalent-examples)
- [Capability Matrix](examples.md#capability-matrix)
- [Neural Network Trainer](examples.md#neural-network-trainer)
- [Solar System Simulation](examples.md#solar-system-simulation)
- [Ecosystem](examples.md#ecosystem)
- [Rubik's Cube Solver](examples.md#rubiks-cube-solver)

## Quick Reference

- [Covalent Annotations Cheat Sheet](annotations.md)
- [Covalent Configuration Reference](configuration.md)
- [Covalent Glossary](glossary.md)

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Step 9: Next Steps](tutorial/09-next-steps.md) | [Next: Covalent Foundations](foundations.md)
<!-- /doc-nav:bottom -->
