# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.0.0] - 2026-10-17

Initial release.

### Added

- A component model and runtime container for a single JVM process (Java 21, no
  third-party runtime dependency in the core).
- Managed components declared with annotations, and raw components registered
  from existing Java objects.
- Three kinds of ports: services, events (per-subscriber filters, asynchronous
  dispatch by default) and parameters (typed configuration available from the
  initialization of a component).
- Composition, which organizes an application as a tree of components, and items,
  which segment the implementation of a single component.
- A managed lifecycle from deployment to undeployment, with contained failures,
  dependency cycle detection, and a report for every startup and shutdown.
- The Supervisor, a JavaFX application to inspect a running container: component
  diagram, dependency graph, sequence diagrams and threads, in English and French.
- An HMI module with a core and an action manager for building user interfaces.
- Four examples (Solar System, Neural Network, Ecosystem and Rubik's Cube) and a
  tutorial, a user guide and reference guides.
