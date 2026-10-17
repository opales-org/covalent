# Covalent Build and Requirements

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Public API](public-api.md) | [Next: Covalent Managed Components Guide](managed-components.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Build and Tests](#build-and-tests)
- [Runtime Requirements and Compatibility](#runtime-requirements-and-compatibility)
  - [Java Version and Module Configuration](#java-version-and-module-configuration)
  - [GraalVM / Native-Image](#graalvm--native-image)
  - [Security-Manager-Equivalent Constraints](#security-manager-equivalent-constraints)
  - [Instantiation Contract for Component Implementations](#instantiation-contract-for-component-implementations)

# Build and Tests

Covalent uses a Maven multi-module build:

- `covalent-core`: component model and runtime container core, with no
  third-party runtime dependency;
- `covalent-hmi`: JavaFX HMI runtime integration (`HmiCore`, `ActionManager`);
- `covalent-supervision`: JavaFX supervision and diagnostics tooling;
- `covalent-tests`: unit and non-regression tests;
- `covalent-examples`: runnable examples and demos.

Build the repository, run every test and check the Javadoc with the Maven
Wrapper, on Windows:

```powershell
.\mvnw clean verify
```

or on Linux and macOS:

```bash
./mvnw clean verify
```

The core's tests live in `covalent-tests`. Build and test the core alone, without
the JavaFX modules, with:

```powershell
.\mvnw -pl covalent-tests -am clean verify
```

Enable optional runtime extensions, such as the component viewer, with:

```powershell
-Dcovalent.extensions=component-viewer
```

See [Extensions](configuration.md#extensions) for the full mechanism, and the
[Covalent Supervision Guide](supervision.md) for what the component viewer
itself provides and how to summon it on a running application.

# Runtime Requirements and Compatibility

Covalent relies heavily on runtime reflection and dynamic proxies to implement
dependency injection, service/events/parameters ports, and lifecycle
management. This section documents the runtime environment this relies on,
and the constructor contract implied by it, so that constraints are known
up front rather than discovered through a runtime failure.

## Java Version and Module Configuration

Covalent targets Java 21 (`maven.compiler.release` in the root `pom.xml`) and
is built and tested against that version. No code intentionally depends on
Java-21-only APIs beyond ordinary reflection, so more recent JDK versions are
expected to keep working, but only Java 21 is verified.

No module in the Maven reactor (`covalent-core`, `covalent-hmi`,
`covalent-supervision`, `covalent-tests`, `covalent-examples`) declares a `module-info.java`.
Covalent runs entirely on the classpath, in the unnamed module. This means:

- Covalent does not distribute a JPMS module descriptor, and nothing
  in the project enforces or exercises strong encapsulation between Covalent
  and the applications that use it;
- if a consuming application is itself packaged as a named JPMS module and
  keeps its `@Implementation` classes in packages that are not `opens`ed
  (unconditionally, or at least to Covalent), Covalent's reflective
  instantiation and field injection will fail at runtime with an
  `InaccessibleObjectException`-based failure, because deep reflective access
  across named module boundaries requires an explicit `opens` on the
  declaring side;
- running Covalent-managed applications as named JPMS modules is therefore not
  a supported configuration. Classpath (or automatic-module) execution
  is the only configuration this project builds and tests against.

## GraalVM / Native-Image

Covalent has not been built, tested, or otherwise validated under GraalVM
native-image. Native-image's closed-world static analysis cannot discover, by
itself, the framework's dynamic proxy creation, private/final field
injection, or caller-stack inspection. Using Covalent under native-image would
require the consuming application to supply its own reflection configuration
(covering every `@Implementation` class Covalent instantiates or injects
into) and dynamic-proxy configuration (covering every service, parameters,
and events interface exposed through a Covalent proxy). The project does not
provide or maintain such a configuration.

## Security-Manager-Equivalent Constraints

Covalent does not install, require, or test against a `SecurityManager`. The
`SecurityManager` API has been deprecated for removal since Java 17 (JEP
411), and this project does not rely on it. For completeness: Covalent's
reflective operations call `setAccessible(true)` on constructors, methods,
and fields it needs to reach, which under an active `SecurityManager` would
require `ReflectPermission("suppressAccessChecks")` (and, for proxies,
`ReflectPermission("newProxyInPackage.*")` in some configurations). Running
under a restrictive `SecurityManager` policy has never been exercised and is
not a supported configuration.

## Instantiation Contract for Component Implementations

Covalent's internal `Reflection.instantiateWithDefaultConstructor` method is
what the container uses to instantiate `@Implementation` classes (among other
reflectively-created instances). It enforces a specific contract:

- the implementation class must not be abstract and must not be an interface;
- the class must declare a **public**, no-argument constructor. It is looked
  up with `Class.getConstructor()`, which only finds public members, not
  `getDeclaredConstructor()` -- a package-private, protected, or private
  no-arg constructor is not found, and instantiation fails;
- for that public constructor to be reachable through reflection from
  outside its own package, the implementation class itself must also be
  public;
- any failure along this path (missing constructor, inaccessible class,
  exception during construction) is wrapped in a `ReflectionException`.

Concretely: declare every `@Implementation` class, and its no-argument
constructor, as `public`. This is not enforced at compile time; violating it
surfaces as a runtime `ReflectionException` at container startup.

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Public API](public-api.md) | [Next: Covalent Managed Components Guide](managed-components.md)
<!-- /doc-nav:bottom -->
