/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.events.EventsManager;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleEvents;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitions;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitionsImpl;
import org.opales.covalent.container.raw.RawComponents;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.raw.impl.RawComponentsImpl;
import org.opales.covalent.container.raw.impl.RawEventsPort;
import org.opales.covalent.container.registry.DescriptorRegistryReadServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.util.Reflection;

@RunWith(Parameterized.class)
public class RawLifecycleFailureTests {
    @Parameterized.Parameters(name = "{0}")
    public static Object[] modes() { return StartMode.values(); }

    private final StartMode mode;
    private CovalentContainer container;
    private RawComponentsServices rcs;
    private RawComponentsImpl raw;
    private LifecycleManagerImpl lifecycle;
    private RegistryReadServices registry;
    private DescriptorRegistryReadServices descriptors;
    private ServicesManagerServices services;
    private ParametersManagerServices parameters;
    private EventsManagerImpl events;
    private NamingServices naming;
    private CapturingLogHandler lifecycleLogs;
    private CapturingLogHandler rawLogs;

    public RawLifecycleFailureTests(final StartMode mode) { this.mode = mode; }

    @Before
    public void setUp() {
        this.lifecycleLogs = CapturingLogHandler.install(LifecycleManagerImpl.class);
        this.rawLogs = CapturingLogHandler.install(RawComponentsImpl.class);
        this.container = Covalent.container();
        this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
        this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
        // Internal lifecycle notifications must stay synchronous even with async application events.
        this.container.getParameters().setSynchronousEvents(false);
        this.rcs = this.container.rcs();
        this.rcs.registerComponent(this);
        this.rcs.accessParameters(LifecycleParameters.class).setStartMode(this.mode);
        this.container.start();
        this.registry = this.rcs.useServices(RegistryReadServices.class);
        this.descriptors = this.rcs.useServices(DescriptorRegistryReadServices.class);
        this.services = this.rcs.useServices(ServicesManagerServices.class);
        this.parameters = this.rcs.useServices(ParametersManagerServices.class);
        this.naming = this.rcs.useServices(NamingServices.class);
        this.lifecycle = this.registry.getComponentInstance(LifecycleManager.class);
        this.raw = this.registry.getComponentInstance(RawComponents.class);
        this.events = this.registry.getComponentInstance(EventsManager.class);
    }

    @After
    public void tearDown() {
        try {
            this.container.dispose();
        } finally {
            this.rawLogs.restore();
            this.lifecycleLogs.restore();
        }
    }

    @Test
    public void rawApplicationFailuresCleanAllManualPortsBeforeReturning() {
        this.rcs.registerComponent(new Dependencies(this.rcs));
        for (String phase : List.of("initialization", "activation", "passivation", "removal")) {
            RawSubject subject = new RawSubject(this.rcs, RawSubject.class, phase);
            if (phase.equals("passivation") || phase.equals("removal")) {
                this.rcs.registerComponent(subject);
                RuntimeException failure = Assert.assertThrows(RuntimeException.class, () -> {
                    if (phase.equals("passivation")) {
                        this.lifecycle.passivateComponent(RawSubject.class);
                    } else {
                        this.lifecycle.removeComponent(RawSubject.class);
                    }
                });
                Assert.assertSame(subject.failure, this.rootCause(failure));
            } else {
                RuntimeException failure = Assert.assertThrows(RuntimeException.class,
                        () -> this.rcs.registerComponent(subject));
                Assert.assertSame(subject.failure, this.rootCause(failure));
            }
            Assert.assertTrue(subject.portsOpened);
            this.assertClean(subject);
            Assert.assertTrue(this.lifecycle.isActivated(Dependencies.class));
            Assert.assertTrue(this.naming.isServicesBound(ServiceA.class));
            Assert.assertTrue(this.naming.isParametersBound(SettingsA.class));
        }
    }

    @Test
    public void explicitRawTypeCleansCallerImplementationPorts() {
        this.rcs.registerComponent(new Dependencies(this.rcs));
        RawSubject subject = new RawSubject(this.rcs, RawType.class, "activation");
        Assert.assertThrows(RuntimeException.class, () -> this.rcs.registerComponent(RawType.class, subject));
        this.assertClean(subject);
    }

    @Test
    public void failedRawInstanceCanBeExplicitlyRegisteredAgain() {
        this.rcs.registerComponent(new Dependencies(this.rcs));
        RawSubject subject = new RawSubject(this.rcs, RawSubject.class, "activation");
        Assert.assertThrows(RuntimeException.class, () -> this.rcs.registerComponent(subject));
        this.assertClean(subject);
        subject.failAt = null;
        this.rcs.registerComponent(subject);
        Assert.assertSame(subject, this.registry.getComponentInstance(RawSubject.class));
        Assert.assertTrue(this.services.isServicesUsed(RawSubject.class, ServiceA.class));
        Assert.assertTrue(this.events.isSubscribed(SharedEvents.class, subject.first));
        subject.failAt = "passivation";
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.passivateComponent(RawSubject.class));
        this.assertClean(subject);
    }

    @Test
    public void rawFailurePreservesOtherOwnersSubscriptionsAndPorts() {
        this.rcs.registerComponent(new Dependencies(this.rcs));
        OtherConsumer healthy = new OtherConsumer(this.rcs);
        this.rcs.registerComponent(healthy);
        RawSubject subject = new RawSubject(this.rcs, RawSubject.class, "activation");
        Assert.assertThrows(RuntimeException.class, () -> this.rcs.registerComponent(subject));
        this.assertClean(subject);
        Assert.assertTrue(this.events.isSubscribed(SharedEvents.class, healthy.listener));
        Assert.assertTrue(this.services.isServicesUsed(OtherConsumer.class, ServiceA.class));
        Assert.assertEquals(7, healthy.service.read());
        Assert.assertTrue(this.lifecycle.isActivated(OtherConsumer.class));
    }

    @Test
    public void pendingSubscriptionsDoNotReconnectAfterRawFailure() {
        this.rcs.registerComponent(new Dependencies(this.rcs));
        RawSubject subject = new RawSubject(this.rcs, RawSubject.class, "activation");
        Assert.assertThrows(RuntimeException.class, () -> this.rcs.registerComponent(subject));
        Assert.assertFalse(this.events.isSubscribed(FutureEvents.class, subject.future));
        // produceEvents below uses this test class as an explicit, otherwise-
        // unregistered componentType, purely as a tag for this one call.
        // RawPortSupport.requireKnownComponentType requires componentType to
        // actually be registered before a producer-side port can be created for it -
        // no unregister needed, this test's @Before/@After discard the whole
        // container per test.
        this.rcs.registerComponent(RawLifecycleFailureTests.class, this);
        this.rcs.produceEvents(RawLifecycleFailureTests.class, FutureEvents.class);
        Assert.assertTrue(this.events.getSubscribers().get(FutureEvents.class).isEmpty());
    }

    @Test
    public void cleanupFailureKeepsRawFailedAndPreservesOriginalCause() throws Exception {
        this.rcs.registerComponent(new Dependencies(this.rcs));
        RawSubject subject = new RawSubject(this.rcs, RawSubject.class, "activation");
        // A raw subscription of a listener object is released only by the raw
        // events port: the managed lifecycle's own cleanup, driven by the
        // metamodel, does not know that listener. The fault is injected there.
        Field collaboratorField = RawComponentsImpl.class.getDeclaredField("rawEventsPort");
        collaboratorField.setAccessible(true);
        RawEventsPort eventsPort = (RawEventsPort) collaboratorField.get(this.raw);
        Field field = RawEventsPort.class.getDeclaredField("useEventsManagerServices");
        field.setAccessible(true);
        Object original = field.get(eventsPort);
        EventsManagerServices failing = (EventsManagerServices) Proxy.newProxyInstance(
                EventsManagerServices.class.getClassLoader(), new Class<?>[] { EventsManagerServices.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("unsubscribe") && args[1] == subject.first) {
                        throw new IllegalStateException("manual cleanup failure");
                    }
                    try { return method.invoke(original, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        try {
            field.set(eventsPort, failing);
            RuntimeException failure = Assert.assertThrows(RuntimeException.class,
                    () -> this.rcs.registerComponent(subject));
            Assert.assertSame(subject.failure, this.rootCause(failure));
            Assert.assertTrue(this.lifecycle.isFailed(RawSubject.class));
            Assert.assertTrue(this.events.isSubscribed(SharedEvents.class, subject.first));
            Assert.assertTrue(this.rawLogs.containsSevereMessageWithFailure(
                    "RAW failure cleanup incomplete for RawSubject.", "manual cleanup failure"));
        } finally {
            field.set(eventsPort, original);
            // Retry the event reaction once the injected cleanup failure is removed.
            this.raw.onComponentFailed(RawSubject.class);
        }
        this.assertClean(subject);
    }

    @Test
    public void rawTechnicalInstantiationFailureIsRemovedBeforeReturning() throws Exception {
        Field field = LifecycleTransitionsImpl.class.getDeclaredField("transitions");
        field.setAccessible(true);
        LifecycleTransitionsImpl transitionsItem = (LifecycleTransitionsImpl) Reflection.lookupFieldWithAnnotation(
                LifecycleManagerImpl.class, LifecycleTransitionsImpl.class, Item.class).get(this.lifecycle);
        Object original = field.get(transitionsItem);
        IllegalStateException expected = new IllegalStateException("raw post-instantiation failure");
        LifecycleTransitions failing = (LifecycleTransitions) Proxy.newProxyInstance(
                LifecycleTransitions.class.getClassLoader(), new Class<?>[] { LifecycleTransitions.class },
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(original, args);
                        if (method.getName().equals("postInstantiate") && args[0] == RawSubject.class) {
                            throw expected;
                        }
                        return result;
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        RawSubject subject = new RawSubject(this.rcs, RawSubject.class, null);
        try {
            field.set(transitionsItem, failing);
            RuntimeException failure = Assert.assertThrows(RuntimeException.class,
                    () -> this.rcs.registerComponent(subject));
            Assert.assertSame(expected, this.rootCause(failure));
            this.assertClean(subject);
            Assert.assertFalse(subject.portsOpened);
        } finally {
            field.set(transitionsItem, original);
        }
    }

    @Test
    public void stopRemovesRawWhosePassivationFails() {
        this.rcs.registerComponent(new Dependencies(this.rcs));
        RawSubject subject = new RawSubject(this.rcs, RawSubject.class, "passivation");
        this.rcs.registerComponent(subject);
        this.container.stop();
        Assert.assertTrue(this.lifecycle.isUndeployed(RawSubject.class));
        Assert.assertEquals(0, subject.removals);
        Assert.assertTrue(this.events.isSubscribed(LifecycleEvents.class, this.raw));
        this.container.start();
        this.rcs.registerComponent(this);
        this.registry = this.rcs.useServices(RegistryReadServices.class);
        this.descriptors = this.rcs.useServices(DescriptorRegistryReadServices.class);
        this.services = this.rcs.useServices(ServicesManagerServices.class);
        this.parameters = this.rcs.useServices(ParametersManagerServices.class);
        this.naming = this.rcs.useServices(NamingServices.class);
        this.rcs.registerComponent(new Dependencies(this.rcs));
        RawSubject restarted = new RawSubject(this.rcs, RawSubject.class, "activation");
        Assert.assertThrows(RuntimeException.class, () -> this.rcs.registerComponent(restarted));
        this.assertClean(restarted);
    }

    @Test
    public void stopRetainsExactlyTheInternalLifecycleConsumer() throws Exception {
        this.container.stop();
        Field field = EventsManagerImpl.class.getDeclaredField("realToProxyUsedSubscriber");
        field.setAccessible(true);
        Map<?, ?> subscribers = (Map<?, ?>) field.get(this.events);
        Assert.assertEquals(1, subscribers.size());
        Assert.assertTrue(subscribers.containsKey(this.raw));
        Assert.assertTrue(this.events.isSubscribed(LifecycleEvents.class, this.raw));
        this.container.start();
        Assert.assertEquals(1, subscribers.size());
    }

    @Test
    public void anUnexpectedConsumerIsListedAsAResidue() {
        // The stop-time consistency check lists it as a residue, whatever the JVM assertion setting.
        LifecycleFailureContainerizedTests.LifecycleEventRecorder extra = new LifecycleFailureContainerizedTests.LifecycleEventRecorder();
        this.events.subscribe(LifecycleEvents.class, extra, null);
        try {
            Assert.assertTrue(this.events.listNonInternalResidues().toString(),
                    this.events.listNonInternalResidues().stream()
                            .anyMatch(r -> r.contains("not one of Covalent's own")));
        } finally {
            this.events.unsubscribe(LifecycleEvents.class, extra);
        }
        Assert.assertEquals(List.of(), this.events.listNonInternalResidues());
    }

    @Test
    public void aMissingInternalConsumerIsListedAsAResidue() {
        // The stop-time consistency check lists it as a residue, whatever the JVM assertion setting.
        this.events.unsubscribe(LifecycleEvents.class, this.raw);
        try {
            Assert.assertTrue(this.events.listNonInternalResidues().toString(),
                    this.events.listNonInternalResidues().stream()
                            .anyMatch(r -> r.contains("unexpected internal subscribers")));
        } finally {
            this.events.subscribe(LifecycleEvents.class, this.raw, null);
        }
        Assert.assertEquals(List.of(), this.events.listNonInternalResidues());
    }

    private void assertClean(final RawSubject subject) {
        Assert.assertTrue(this.lifecycle.isUndeployed(subject.owner));
        Assert.assertFalse(this.registry.isRegistered(subject.owner));
        Assert.assertNull(this.descriptors.readComponentDescriptor(subject.owner));
        Assert.assertFalse(this.services.isServicesUsed(RawSubject.class, ServiceA.class));
        Assert.assertFalse(this.services.isServicesUsed(RawSubject.class, ServiceB.class));
        Assert.assertFalse(this.parameters.isParametersAccessed(RawSubject.class, SettingsA.class));
        Assert.assertFalse(this.parameters.isParametersAccessed(RawSubject.class, SettingsB.class));
        Assert.assertFalse(this.services.isServicesProvided(OwnService.class));
        Assert.assertFalse(this.parameters.isParametersOffered(OwnSettings.class));
        Assert.assertFalse(this.events.isEventsProduced(OwnEventsA.class));
        Assert.assertFalse(this.events.isEventsProduced(OwnEventsB.class));
        Assert.assertFalse(this.naming.isServicesBound(OwnService.class));
        Assert.assertFalse(this.naming.isParametersBound(OwnSettings.class));
        Assert.assertFalse(this.naming.isSubscriberBound(OwnEventsA.class));
        Assert.assertFalse(this.naming.isSubscriberBound(OwnEventsB.class));
        Assert.assertFalse(this.events.isSubscribed(SharedEvents.class, subject.first));
        Assert.assertFalse(this.events.isSubscribed(SharedEvents.class, subject.second));
        Assert.assertFalse(this.events.isSubscribed(FutureEvents.class, subject.future));
    }

    private Throwable rootCause(final Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) { cause = cause.getCause(); }
        return cause;
    }

    public interface RawType { }
    public interface ServiceA { int read(); }
    public interface ServiceB { int readOther(); }
    public interface SettingsA { int value(); }
    public interface SettingsB { int otherValue(); }
    public interface OwnService { void execute(); }
    public interface OwnSettings { int ownValue(); }
    @Events public interface SharedEvents { void onChange(); }
    @Events public interface FutureEvents { void onFuture(); }
    @Events public interface OwnEventsA { void onFirst(); }
    @Events public interface OwnEventsB { void onSecond(); }

    public static class Listener implements SharedEvents {
        @Override public void onChange() { }
    }
    public static class FutureListener implements FutureEvents {
        @Override public void onFuture() { }
    }

    public static class Dependencies implements ServiceA, ServiceB, SettingsA, SettingsB {
        private final RawComponentsServices rcs;
        Dependencies(final RawComponentsServices rcs) { this.rcs = rcs; }
        @Activation public void activate() {
            this.rcs.provideServices(Dependencies.class, ServiceA.class);
            this.rcs.provideServices(Dependencies.class, ServiceB.class);
            this.rcs.offerParameters(Dependencies.class, SettingsA.class);
            this.rcs.offerParameters(Dependencies.class, SettingsB.class);
            this.rcs.produceEvents(Dependencies.class, SharedEvents.class);
        }
        @Override public int read() { return 7; }
        @Override public int readOther() { return 8; }
        @Override public int value() { return 9; }
        @Override public int otherValue() { return 10; }
    }

    public static class OtherConsumer {
        private final RawComponentsServices rcs;
        final Listener listener = new Listener();
        ServiceA service;
        OtherConsumer(final RawComponentsServices rcs) { this.rcs = rcs; }
        @Activation public void activate() {
            this.service = this.rcs.useServices(ServiceA.class);
            this.rcs.subscribe(OtherConsumer.class, SharedEvents.class, this.listener);
        }
    }

    public static class RawSubject implements RawType, OwnService, OwnSettings {
        private final RawComponentsServices rcs;
        final Class<?> owner;
        String failAt;
        final IllegalStateException failure = new IllegalStateException("raw application failure");
        final Listener first = new Listener();
        final Listener second = new Listener();
        final FutureListener future = new FutureListener();
        boolean portsOpened;
        int removals;
        RawSubject(final RawComponentsServices rcs, final Class<?> owner, final String failAt) {
            this.rcs = rcs;
            this.owner = owner;
            this.failAt = failAt;
        }
        @Initialization public void initialize() {
            this.rcs.useServices(ServiceA.class);
            this.rcs.useServices(ServiceB.class);
            this.rcs.accessParameters(SettingsA.class);
            this.rcs.accessParameters(SettingsB.class);
            this.rcs.provideServices(this.owner, OwnService.class);
            this.rcs.offerParameters(this.owner, OwnSettings.class);
            this.rcs.produceEvents(this.owner, OwnEventsA.class);
            this.rcs.produceEvents(this.owner, OwnEventsB.class);
            this.rcs.subscribe(this.owner, SharedEvents.class, this.first);
            this.rcs.subscribe(this.owner, SharedEvents.class, this.second);
            this.rcs.subscribe(this.owner, FutureEvents.class, this.future);
            this.portsOpened = true;
            if ("initialization".equals(this.failAt)) { throw this.failure; }
        }
        @Activation public void activate() {
            if ("activation".equals(this.failAt)) { throw this.failure; }
        }
        @Passivation public void passivate() {
            if ("passivation".equals(this.failAt)) { throw this.failure; }
        }
        @Removal public void remove() {
            if ("removal".equals(this.failAt)) { throw this.failure; }
            this.removals++;
        }
        @Override public void execute() { }
        @Override public int ownValue() { return 11; }
    }
}
