# Covalent FAQ

<!-- doc-nav:top -->
[Home](../README.md) | [Previous: Covalent Troubleshooting](troubleshooting.md)
<!-- /doc-nav:top -->

# Table of Contents

- [Adopting Covalent](#adopting-covalent)
  - [Which Java version does Covalent need?](#which-java-version-does-covalent-need)
  - [Does Covalent need JavaFX or a graphical interface?](#does-covalent-need-javafx-or-a-graphical-interface)
  - [Does Covalent work with JPMS?](#does-covalent-work-with-jpms)
  - [Can I use Covalent with Spring or Guice?](#can-i-use-covalent-with-spring-or-guice)
  - [What does the stability guarantee cover?](#what-does-the-stability-guarantee-cover)
  - [What is the memory footprint and startup time?](#what-is-the-memory-footprint-and-startup-time)
- [Using Covalent](#using-covalent)
  - [Is Covalent thread-safe?](#is-covalent-thread-safe)
  - [Are events guaranteed to be delivered?](#are-events-guaranteed-to-be-delivered)
  - [Can I add, remove or replace a component while the application runs?](#can-i-add-remove-or-replace-a-component-while-the-application-runs)
  - [Can I test Covalent components with JUnit?](#can-i-test-covalent-components-with-junit)
  - [What should I check when a component does not start?](#what-should-i-check-when-a-component-does-not-start)
  - [Can I run the supervisor in production, and what does it cost?](#can-i-run-the-supervisor-in-production-and-what-does-it-cost)
- [Licensing](#licensing)
  - [Can I use Covalent in a commercial or closed-source application?](#can-i-use-covalent-in-a-commercial-or-closed-source-application)
  - [Do I have to publish the source code of my application?](#do-i-have-to-publish-the-source-code-of-my-application)
  - [What must I do if I modify Covalent?](#what-must-i-do-if-i-modify-covalent)
  - [Can I distribute Covalent inside my product?](#can-i-distribute-covalent-inside-my-product)
  - [Do the fonts and other third-party files have the same license?](#do-the-fonts-and-other-third-party-files-have-the-same-license)
- [About Covalent](#about-covalent)
  - [Why the name "Covalent"?](#why-the-name-covalent)

Short answers to the questions early adopters ask most often. Each links to the
page that covers it in full.

# Adopting Covalent

## Which Java version does Covalent need?

Java 21. It is the version Covalent is built and tested against, and the only one
actually verified; later versions are expected to keep working. See [Java Version and
Module Configuration](requirements.md#java-version-and-module-configuration).

## Does Covalent need JavaFX or a graphical interface?

No. `covalent-core`, the container itself, has no third-party runtime dependency and
runs as well in a console or server application as in a desktop one. JavaFX 21 is only
needed by the modules that draw something: `covalent-hmi`, the supervisor
(`covalent-supervision`) and the examples. The supervisor is an optional extension,
available only when `covalent.extensions=component-viewer` is set, and opened on demand
(keyboard shortcut or JMX); see [Extensions](configuration.md#extensions) and
[Summoning the Supervisor](supervision.md#summoning-the-supervisor).

## Does Covalent work with JPMS?

Not as a named module. Covalent uses reflection and dynamic proxies
extensively, and it requires running in the unnamed module, on the
classpath. See [Runtime Requirements and
Compatibility](requirements.md#runtime-requirements-and-compatibility) for the
full picture, including GraalVM native-image status.

## Can I use Covalent with Spring or Guice?

Covalent has no Spring or Guice integration module, and none is tested. It is a
container of its own, with an opinion about components, ports and lifecycle, which
overlaps with what those frameworks cover rather than plugging into them. See
[Compared To Other Approaches](foundations.md#compared-to-other-approaches) for what
each one does and does not do.

## What does the stability guarantee cover?

Covalent guarantees a stable container as long as the APIs documented in the guides are
used as described. The ports of its own internal components remain reachable, for
advanced uses, but using them is at your own risk: a container left inconsistent through
them is outside the guarantee. See [Stability
Guarantee](public-api.md#stability-guarantee).

## What is the memory footprint and startup time?

There is no formal benchmark. `covalent-core` has no third-party runtime
dependency, and its jar is a few hundred kilobytes. Startup time depends on
your own application - the number of components and what their callbacks do -
and `StartupReport` (returned by `start()`) reports the actual duration for
your application: measure it directly rather than rely on a figure that would
not reflect your own component count.
See [Starting a Covalent
Application](managed-components.md#starting-a-covalent-application).

# Using Covalent

## Is Covalent thread-safe?

The container itself is: lifecycle operations (deploy, start, stop, and
individual component transitions) are serialized per container, so two threads
racing to start or stop the same container cannot corrupt its state. See
[Lifecycle](managed-components.md#lifecycle).

Your own component implementations are a different matter. A service call runs
synchronously on the caller's thread, and an asynchronous event can be delivered
on any worker thread, with several notifications able to run concurrently and no
ordering guarantee between them (see [Threading
Model](managed-components.md#threading-model)). If the same component instance
can be reached from more than one thread at once - a mix of service calls and
asynchronous event deliveries is the common case - your implementation is
responsible for its own thread-safety, exactly as any plain Java object would
be. Covalent does not add locking around your business logic.

## Are events guaranteed to be delivered?

Not in every case. Events are in memory, inside one JVM: Covalent does not persist
them and does not retry them. By default they are asynchronous and fire-and-forget:
each notification is queued for a worker pool (1024 pending notifications by default),
the producing call is never blocked and never throws, and when the queue is full the
notification is dropped and logged at `SEVERE`. Two notifications can also run
concurrently, in no particular order, unless the worker count is set to `1`. A passivated
producer sends nothing, while a passivated consumer keeps receiving events.

Where a flow cannot tolerate any loss, do not carry it on asynchronous events: use a
service call, whose result the caller checks, or synchronous events, which run on the
producer's thread in the order it fired them. See [Asynchronous Event Dispatch
Configuration](managed-components.md#asynchronous-event-dispatch-configuration),
[Threading Model](managed-components.md#threading-model) and
[Lifecycle](managed-components.md#lifecycle).

## Can I add, remove or replace a component while the application runs?

Yes. Nothing ties deployment to the start of the container: application code can take
an individual component through its lifecycle while the container is running, with
`LifecycleServices` (`deployComponent`, `activateComponent`, `passivateComponent`,
`removeComponent` and `undeployComponent`), and deploy it again afterwards. For a raw
component, `unregisterComponent` followed by `registerComponent` with another instance
replaces it. The users of its services and parameters need to do nothing: their
links stay in place, fail with a clear message while the provider is absent, and
reach the new provider as soon as it is there. An existing subscriber keeps
receiving the events of a replaced producer, and the old instance's own event
proxy fails with an `IllegalStateException` if it is used again. Use these, rather than
`stop()` and `start()`, which are meant for the whole container. See [Restarting a
Covalent Application](managed-components.md#restarting-a-covalent-application) and
[Register an Existing Object as a Raw
Component](raw-components.md#register-an-existing-object-as-a-raw-component). The supervisor shows the
components and their states, but has no control to deploy or replace one.

## Can I test Covalent components with JUnit?

Yes; `covalent-tests` (JUnit 4) is Covalent's own test suite and a working
reference for patterns, and the [executable tutorial](tutorial/index.md) is
itself a small JUnit suite (`covalent-examples`) that runs each tutorial
program as its own process and checks its output. Two container patterns are
worth knowing:

- `Covalent.main` is the default container, shared for the process; most tests
  deploy their components, start it, run assertions, and stop it, typically in
  the test method itself or in `@Before`/`@After`.
- `Covalent.container()` creates a new, fully isolated container instead, if
  you would rather give each test its own container than share and reset one.

See [Accessing Containers](public-api.md#accessing-containers).

## What should I check when a component does not start?

`start()` returns a `StartupReport` with the container status, the number of components
by state and the list of failed components, so start there. A failed transition puts the
whole owning composite in the `failed` state, and failed components are skipped by the
global startup. [Troubleshooting](troubleshooting.md) lists the symptoms and the
first checks to make, and the [supervisor](supervision.md#covalent-supervision) shows
the state of every component as it runs.

## Can I run the supervisor in production, and what does it cost?

The supervisor is optional: nothing of it runs unless you enable the extension, and it
needs JavaFX. Inside it, task tracking and the data flows are off by default. Task
tracking made no measurable difference in our tests; the flows add a cost to each
call that depends on the call rate, and keep some memory. The figures, with the
conditions they were measured in, are in the [overhead
measurements](supervision.md#appendix-overhead-measurements). Whether that cost is
acceptable depends on your application, so measure it with your own load.

# Licensing

Plain-language answers about the license, not legal advice: the
[license text](../LICENSE) is what counts.

## Can I use Covalent in a commercial or closed-source application?

Yes. Covalent is released under the [Mozilla Public License
2.0](../LICENSE) (MPL 2.0), which allows using, modifying and distributing it for any
purpose, commercial ones included. Its copyleft is limited to the files of Covalent
itself: your own source files, which only use Covalent, stay yours, under whatever
license you choose.

## Do I have to publish the source code of my application?

Not because you use Covalent. The MPL's copyleft works file by file, not on a whole
program: an application made of its own files and of Covalent, used as it is, is what
the license calls a Larger Work, and can be distributed under other terms. The
obligations that remain concern Covalent's own files, which the next two questions cover.

## What must I do if I modify Covalent?

If you modify files of Covalent and distribute the result, in source or compiled form,
you must make the source of those modified files available under the MPL 2.0, keep their
copyright and license notices, and tell the recipients how to get that source. Changes
you keep for yourself, or that stay on your own servers without being handed to anyone,
do not oblige you to publish anything. New files that contain no Covalent code are
yours.

## Can I distribute Covalent inside my product?

Yes. When you ship a product that contains Covalent in compiled form, include the
license text, keep the notices, and tell the recipients how to get the source of the
Covalent version you ship, for example with a link to it. That does not extend to your own
code in the same product, which can be under other terms, and you must not restrict
what the license gives recipients over Covalent's files.

## Do the fonts and other third-party files have the same license?

No. Third-party files keep their own license, and Covalent's own license does not replace
it. The supervisor embeds three fonts under the SIL Open Font License 1.1, listed in
[THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md), each with its license text beside
it. The libraries the supervisor depends on, such as JavaFX, are not part of Covalent
either and keep their own licenses.

# About Covalent

## Why the name "Covalent"?

A covalent bond is a loose coupling: two atoms share a pair of electrons
instead of one absorbing the other, and that looseness is exactly what lets
atoms combine into the enormous variety of stable molecules found in nature.
A tightly bound, all-or-nothing attachment could not do either - not the
variety, and not the stability.

Covalent components are coupled the same way, and for the same reason. A
component never holds a reference to another component's implementation; it
shares a typed contract instead - a services, events or parameters interface.
`Orders` and `Kitchen` (see [Getting Started](../README.md#getting-started))
share `OrderEvents`; neither one knows the other's class exists. That
looseness is what lets an application be assembled, like a molecule, from
parts that can each change or be replaced without the others noticing, while
the application as a whole stays stable. The container is what makes the
coupling loose in practice: lookup, injection, subscription and lifecycle are
handled by container logic that every component shares and that has been
exercised across every component that uses it, rather than by wiring code
hand-written and maintained separately inside each one. The direct payoff is
components that read, and stay easy to understand and change, on their own;
less code overall to own and maintain; and fewer of the wiring bugs that
hand-written lookups, injections and callback registrations tend to invite.

The same idea carries into the component viewer's visual language: each
component type is drawn as a hexagon, the shape of a benzene ring and of most
molecule diagrams in chemistry, and its ports are drawn with the classic
component-diagram symbols - a facet for a provided service, a receptacle arc
for a used one, a tab for parameters, small polygons for produced and
consumed events. A running application, seen through the viewer, reads as a
diagram of bonded structures for the same reason the name does.

<!-- doc-nav:bottom -->
[Home](../README.md) | [Previous: Covalent Troubleshooting](troubleshooting.md)
<!-- /doc-nav:bottom -->
