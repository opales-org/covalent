/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.*;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.lifecycle.*;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitions;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleTransitionsImpl;
import org.opales.covalent.container.registry.DescriptorRegistryReadServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.util.Reflection;

@RunWith(Parameterized.class)
public class LifecycleFailureRecoveryTests {
    @Parameterized.Parameters(name = "{0}")
    public static Object[] modes() { return StartMode.values(); }

    private final StartMode mode;
    private CovalentContainer container;
    private LifecycleManagerImpl lifecycle;
    private RegistryReadServices registry;
    private DescriptorRegistryReadServices descriptors;
    private ServicesManagerServices services;
    private ParametersManagerServices parameters;
    private EventsManagerServices events;
    private NamingServices naming;
    private CapturingLogHandler logs;
    private LifecycleTransitions originalTransitions;
    private LifecycleTransitionsImpl transitionsItem;
    private Field transitionsField;
    private String failingHook;
    private boolean failCleanup;
    private final List<String> hooks = new ArrayList<>();
    private static String applicationFailure;
    private static int applicationRemovals;
    private static int constructions;
    private FailureObserver observer;

    public LifecycleFailureRecoveryTests(final StartMode mode) { this.mode = mode; }

    @Before
    public void setUp() throws Exception {
        applicationFailure = null;
        applicationRemovals = 0;
        constructions = 0;
        this.logs = CapturingLogHandler.install(LifecycleManagerImpl.class);
        this.container = Covalent.container();
        this.container.getParameters().setSynchronousEvents(true);
        this.container.rcs().registerComponent(this);
        this.container.rcs().accessParameters(LifecycleParameters.class).setStartMode(this.mode);
        this.container.start();
        this.readServices();
        this.lifecycle = (LifecycleManagerImpl) this.registry.getComponentInstance(LifecycleManager.class);
        this.transitionsField = LifecycleTransitionsImpl.class.getDeclaredField("transitions");
        this.transitionsField.setAccessible(true);
        this.transitionsItem = (LifecycleTransitionsImpl) Reflection.lookupFieldWithAnnotation(
                LifecycleManagerImpl.class, LifecycleTransitionsImpl.class, Item.class).get(this.lifecycle);
        this.originalTransitions = (LifecycleTransitions) this.transitionsField.get(this.transitionsItem);
        this.installFailureInjection();
        this.observer = new FailureObserver(this);
        this.container.rcs().subscribe(LifecycleEvents.class, this.observer);
    }

    private void readServices() {
        this.registry = this.container.rcs().useServices(RegistryReadServices.class);
        this.descriptors = this.container.rcs().useServices(DescriptorRegistryReadServices.class);
        this.services = this.container.rcs().useServices(ServicesManagerServices.class);
        this.parameters = this.container.rcs().useServices(ParametersManagerServices.class);
        this.events = this.container.rcs().useServices(EventsManagerServices.class);
        this.naming = this.container.rcs().useServices(NamingServices.class);
    }

    private void installFailureInjection() throws Exception {
        LifecycleTransitions injected = (LifecycleTransitions) Proxy.newProxyInstance(
                LifecycleTransitions.class.getClassLoader(), new Class<?>[] { LifecycleTransitions.class },
                (proxy, method, args) -> {
                    this.hooks.add(method.getName() + ":" + ((Class<?>) args[0]).getSimpleName());
                    if (this.failCleanup && method.getName().equals("preFail")) {
                        throw new IllegalStateException("injected cleanup failure");
                    }
                    try {
                        Object result = method.invoke(this.originalTransitions, args);
                        if (method.getName().equals(this.failingHook) && args[0] == Subject.class) {
                            throw new IllegalStateException("injected " + this.failingHook);
                        }
                        return result;
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        this.transitionsField.set(this.transitionsItem, injected);
    }

    @After
    public void tearDown() throws Exception {
        applicationFailure = null;
        this.failingHook = null;
        this.failCleanup = false;
        this.transitionsField.set(this.transitionsItem, this.originalTransitions);
        try {
            this.container.dispose();
        } finally {
            this.logs.restore();
        }
    }

    @Test
    public void everyTechnicalPhaseFailureCleansPortsAndRequiresExplicitReset() {
        for (String hook : List.of("preInstantiate", "postInstantiate", "preInitialize", "postInitialize",
                "preActivate", "postActivate", "prePassivate", "postPassivate", "preRemove", "postRemove")) {
            this.lifecycle.deployComponent(SubjectImpl.class);
            if (hook.contains("Passivate") || hook.contains("Remove")) {
                this.lifecycle.activateComponent(Subject.class);
            }
            this.failingHook = hook;
            Runnable transition = hook.contains("Instantiate") ? () -> this.lifecycle.instantiateComponent(Subject.class)
                    : hook.contains("Initialize") ? () -> this.lifecycle.initializeComponent(Subject.class)
                    : hook.contains("Activate") ? () -> this.lifecycle.activateComponent(Subject.class)
                    : hook.contains("Passivate") ? () -> this.lifecycle.passivateComponent(Subject.class)
                    : () -> this.lifecycle.removeComponent(Subject.class);
            Assert.assertThrows(hook, RuntimeException.class, transition::run);
            this.assertFailedAndClean(Subject.class);
            Assert.assertThrows(IllegalStateException.class, () -> this.lifecycle.activateComponent(Subject.class));
            this.failingHook = null;
            this.lifecycle.resetFailedComponent(Subject.class);
            Assert.assertEquals(ComponentState.deployed, this.lifecycle.readComponentState(Subject.class));
            this.lifecycle.activateComponent(Subject.class);
            Assert.assertTrue(this.lifecycle.isActivated(Subject.class));
            this.lifecycle.undeployComponent(SubjectImpl.class);
        }
    }

    @Test
    public void constructorFailureKeepsMetamodelButNoRuntimeObjects() {
        applicationFailure = "constructor";
        this.lifecycle.deployComponent(SubjectImpl.class);
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.instantiateComponent(Subject.class));
        this.assertFailedAndClean(Subject.class);
        Assert.assertTrue(this.lifecycle.isDeployed(Subject.class));
        applicationFailure = null;
        this.lifecycle.resetFailedComponent(Subject.class);
        this.lifecycle.activateComponent(Subject.class);
        Assert.assertTrue(this.lifecycle.isActivated(Subject.class));
    }

    @Test
    public void itemConstructorFailureCleansPartiallyInstantiatedComponent() {
        this.lifecycle.deployComponent(BrokenItemOwnerImpl.class);
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.instantiateComponent(BrokenItemOwner.class));
        Assert.assertTrue(this.lifecycle.isFailed(BrokenItemOwner.class));
        Assert.assertFalse(this.registry.isRegistered(BrokenItemOwner.class));
        Assert.assertNull(this.descriptors.readComponentDescriptor(BrokenItemOwner.class));
    }

    @Test
    public void applicationFailureInNestedChildFailsAndCleansEntireComposite() {
        this.lifecycle.deployComponent(RootImpl.class);
        applicationFailure = "activation";
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.activateComponent(Root.class));
        for (Class<?> type : List.of(Root.class, Branch.class, Subject.class)) {
            Assert.assertTrue(this.lifecycle.isFailed(type));
            Assert.assertFalse(this.registry.isRegistered(type));
        }
        List<String> preFailHooks = this.hooks.stream().filter(hook -> hook.startsWith("preFail:")).toList();
        Assert.assertEquals(List.of("preFail:Subject", "preFail:Branch", "preFail:Root"), preFailHooks);
        Assert.assertEquals(0, applicationRemovals);
        Assert.assertEquals(List.of(Subject.class, Branch.class, Root.class), this.observer.failedTypes);
        Assert.assertTrue(this.observer.allFailedBeforeNotification);
        Assert.assertTrue(this.observer.allPostFailBeforeNotification);
        applicationFailure = null;
        this.lifecycle.resetFailedComponent(Subject.class);
        for (Class<?> type : List.of(Root.class, Branch.class, Subject.class)) {
            Assert.assertEquals(ComponentState.deployed, this.lifecycle.readComponentState(type));
        }
        this.lifecycle.activateComponent(Root.class);
        Assert.assertTrue(this.lifecycle.isActivated(Root.class));
    }

    @Test
    public void failedComponentWithoutComponentInterfaceIsResetNotUndeployed() {
        // A managed component whose component type is its own implementation, with no
        // @Component interface, is not a raw component: its failure keeps it deployed.
        this.lifecycle.deployComponent(TypelessImpl.class);
        applicationFailure = "activation";
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.activateComponent(TypelessImpl.class));
        Assert.assertTrue(this.lifecycle.isFailed(TypelessImpl.class));
        Assert.assertTrue(this.lifecycle.isDeployed(TypelessImpl.class));
        applicationFailure = null;
        this.lifecycle.resetFailedComponent(TypelessImpl.class);
        Assert.assertEquals(ComponentState.deployed, this.lifecycle.readComponentState(TypelessImpl.class));
        this.lifecycle.activateComponent(TypelessImpl.class);
        Assert.assertTrue(this.lifecycle.isActivated(TypelessImpl.class));
    }

    @Test
    public void cleanupFailureStillForcesFailedAndPreventsReset() {
        this.lifecycle.deployComponent(SubjectImpl.class);
        this.failingHook = "postActivate";
        this.failCleanup = true;
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.activateComponent(Subject.class));
        Assert.assertTrue(this.lifecycle.isFailed(Subject.class));
        Assert.assertTrue(this.registry.isRegistered(Subject.class));
        Assert.assertEquals(List.of(Subject.class), this.observer.failedTypes);
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.resetFailedComponent(Subject.class));
        Assert.assertTrue(this.lifecycle.isFailed(Subject.class));
        Assert.assertTrue(this.logs.containsSevereMessageWithFailure(
                "Failure cleanup incomplete for Subject.", "injected cleanup failure"));
        this.failCleanup = false;
        this.failingHook = null;
        this.lifecycle.resetFailedComponent(Subject.class);
        Assert.assertEquals(ComponentState.deployed, this.lifecycle.readComponentState(Subject.class));
        Assert.assertFalse(this.registry.isRegistered(Subject.class));
    }

    @Test
    public void failedCompositeIsNotRestartedByGlobalPhases() {
        this.lifecycle.deployComponents(SubjectImpl.class, HealthyImpl.class);
        this.failingHook = "postInstantiate";
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.instantiateComponent(Subject.class));
        int before = constructions;
        this.failingHook = null;
        this.lifecycle.instantiate();
        this.lifecycle.initialize();
        this.lifecycle.activate();
        Assert.assertEquals(before, constructions);
        Assert.assertTrue(this.lifecycle.isFailed(Subject.class));
        Assert.assertTrue(this.lifecycle.isActivated(Healthy.class));
    }

    @Test
    public void startupContinuesWithHealthyRootsAndIsStillStarted() {
        this.container.stop();
        this.lifecycle.deployComponents(SubjectImpl.class, HealthyImpl.class);
        applicationFailure = "activation";
        this.container.start();
        this.container.rcs().registerComponent(this);
        this.readServices();
        this.assertFailedAndClean(Subject.class);
        Assert.assertTrue(this.lifecycle.isActivated(Healthy.class));
        Assert.assertThrows(IllegalStateException.class, () -> this.lifecycle.start());
    }

    @Test
    public void stopRemovesAlreadyFailedComponentWithoutApplicationCalls() {
        this.lifecycle.deployComponent(SubjectImpl.class);
        this.failingHook = "postActivate";
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.activateComponent(Subject.class));
        this.failingHook = null;
        this.container.stop();
        Assert.assertTrue(this.lifecycle.isUndeployed(Subject.class));
        Assert.assertEquals(0, applicationRemovals);
        // This test exercises stop() itself; restart so tearDown()'s own,
        // unconditional stop() below does not run on an already-stopped container.
        this.container.start();
    }

    @Test
    public void stopStillRemovesComponentWhosePassivationFails() {
        this.lifecycle.deployComponent(SubjectImpl.class);
        this.lifecycle.activateComponent(Subject.class);
        applicationFailure = "passivation";
        this.container.stop();
        Assert.assertTrue(this.lifecycle.isUndeployed(Subject.class));
        Assert.assertEquals(0, applicationRemovals);
        // This test exercises stop() itself; restart so tearDown()'s own,
        // unconditional stop() below does not run on an already-stopped container.
        this.container.start();
    }

    @Test
    public void invalidLifecycleCommandDoesNotFailHealthyComponent() {
        this.lifecycle.deployComponent(SubjectImpl.class);
        this.lifecycle.activateComponent(Subject.class);
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.activateComponent(Subject.class));
        Assert.assertFalse(this.lifecycle.isFailed(Subject.class));
        Assert.assertTrue(this.lifecycle.isActivated(Subject.class));
    }

    @Test
    public void resetIsRejectedForHealthyComponent() {
        this.lifecycle.deployComponent(SubjectImpl.class);
        Assert.assertThrows(IllegalStateException.class, () -> this.lifecycle.resetFailedComponent(Subject.class));
        Assert.assertEquals(ComponentState.deployed, this.lifecycle.readComponentState(Subject.class));
    }

    @Test
    public void failedIsNotAnAllowedRawRegistrationTarget() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> this.container.rcs().registerComponent(new Object(), ComponentState.failed));
        Assert.assertTrue(this.lifecycle.isUndeployed(Object.class));
    }

    @Test
    public void failedConsumerDisconnectsUsedPortsAndSubscriptionsOnly() {
        this.lifecycle.deployComponents(SubjectImpl.class, ClientImpl.class);
        this.lifecycle.activateComponent(Subject.class);
        this.lifecycle.activateComponent(Client.class);
        ClientImpl client = this.registry.getComponentInstance(Client.class);
        Assert.assertTrue(this.services.isServicesUsed(Client.class, ServiceA.class));
        Assert.assertTrue(this.services.isServicesUsed(Client.class, ServiceB.class));
        Assert.assertTrue(this.parameters.isParametersAccessed(Client.class, Settings.class));
        Assert.assertTrue(this.events.isSubscribed(Changes.class, client));

        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.passivateComponent(Client.class));
        Assert.assertTrue(this.lifecycle.isFailed(Client.class));
        Assert.assertFalse(this.services.isServicesUsed(Client.class, ServiceA.class));
        Assert.assertFalse(this.services.isServicesUsed(Client.class, ServiceB.class));
        Assert.assertFalse(this.parameters.isParametersAccessed(Client.class, Settings.class));
        Assert.assertFalse(this.events.isSubscribed(Changes.class, client));
        Assert.assertTrue(this.lifecycle.isActivated(Subject.class));
        Assert.assertTrue(this.naming.isServicesBound(ServiceA.class));
        Assert.assertTrue(this.naming.isParametersBound(Settings.class));
        this.lifecycle.resetFailedComponent(Client.class);
        this.lifecycle.activateComponent(Client.class);
        Assert.assertNotSame(client, this.registry.getComponentInstance(Client.class));
    }

    @Test
    public void removalFromFailedNeverCallsApplicationMethods() {
        this.lifecycle.deployComponent(SubjectImpl.class);
        this.failingHook = "postActivate";
        Assert.assertThrows(RuntimeException.class, () -> this.lifecycle.activateComponent(Subject.class));
        this.failingHook = null;
        this.lifecycle.removeComponent(Subject.class);
        Assert.assertEquals(ComponentState.removed, this.lifecycle.readComponentState(Subject.class));
        Assert.assertEquals(0, applicationRemovals);
    }

    private void assertFailedAndClean(final Class<?> type) {
        Assert.assertTrue(this.lifecycle.isFailed(type));
        Assert.assertFalse(this.registry.isRegistered(type));
        Assert.assertNull(this.descriptors.readComponentDescriptor(type));
        Assert.assertFalse(this.services.isServicesProvided(ServiceA.class));
        Assert.assertFalse(this.services.isServicesProvided(ServiceB.class));
        Assert.assertFalse(this.parameters.isParametersOffered(Settings.class));
        Assert.assertFalse(this.events.isEventsProduced(Changes.class));
        Assert.assertFalse(this.naming.isServicesBound(ServiceA.class));
        Assert.assertFalse(this.naming.isServicesBound(ServiceB.class));
        Assert.assertFalse(this.naming.isParametersBound(Settings.class));
        Assert.assertFalse(this.naming.isSubscriberBound(Changes.class));
    }

    @Test
    public void stopStillRemovesComponentWhoseRemovalFails() {
        this.lifecycle.deployComponent(SubjectImpl.class);
        this.lifecycle.activateComponent(Subject.class);
        applicationFailure = "removal";
        this.container.stop();
        Assert.assertTrue(this.lifecycle.isUndeployed(Subject.class));
        // This test exercises stop() itself; restart so tearDown()'s own,
        // unconditional stop() below does not run on an already-stopped container.
        this.container.start();
    }

    @Test
    public void successEventsObserveCompletedStateAndPorts() throws Exception {
        Field field = LifecycleManagerImpl.class.getDeclaredField("produceLifecycleEvents");
        field.setAccessible(true);
        Object original = field.get(this.lifecycle);
        List<String> notifications = new ArrayList<>();
        LifecycleEvents observer = (LifecycleEvents) Proxy.newProxyInstance(LifecycleEvents.class.getClassLoader(),
                new Class<?>[] { LifecycleEvents.class }, (proxy, method, args) -> {
                    if (args[0] != Subject.class) { return null; }
                    notifications.add(method.getName());
                    switch (method.getName()) {
                    case "onComponentInstantiated":
                        Assert.assertEquals(ComponentState.instantiated, this.lifecycle.readComponentState(Subject.class));
                        Assert.assertTrue(this.naming.isParametersBound(Settings.class));
                        break;
                    case "onComponentInitialized":
                        Assert.assertEquals(ComponentState.initialized, this.lifecycle.readComponentState(Subject.class));
                        Assert.assertTrue(this.services.isServicesProvided(ServiceA.class));
                        break;
                    case "onComponentActivated":
                        Assert.assertEquals(ComponentState.activated, this.lifecycle.readComponentState(Subject.class));
                        Assert.assertTrue(this.naming.isServicesBound(ServiceA.class));
                        Assert.assertTrue(this.naming.isSubscriberBound(Changes.class));
                        break;
                    case "onComponentPassivated":
                        Assert.assertEquals(ComponentState.passivated, this.lifecycle.readComponentState(Subject.class));
                        Assert.assertFalse(this.naming.isServicesBound(ServiceA.class));
                        break;
                    case "onComponentRemoved":
                        Assert.assertEquals(ComponentState.removed, this.lifecycle.readComponentState(Subject.class));
                        Assert.assertFalse(this.registry.isRegistered(Subject.class));
                        break;
                    default: Assert.fail(method.getName());
                    }
                    return null;
                });
        try {
            field.set(this.lifecycle, observer);
            this.lifecycle.deployComponent(SubjectImpl.class);
            this.lifecycle.activateComponent(Subject.class);
            this.lifecycle.removeComponent(Subject.class);
            Assert.assertEquals(5, notifications.size());
        } finally {
            field.set(this.lifecycle, original);
        }
    }

    @Test
    public void notificationFailureDoesNotFailCompletedTransition() throws Exception {
        Field field = LifecycleManagerImpl.class.getDeclaredField("produceLifecycleEvents");
        field.setAccessible(true);
        Object original = field.get(this.lifecycle);
        try {
            field.set(this.lifecycle, Proxy.newProxyInstance(LifecycleEvents.class.getClassLoader(),
                    new Class<?>[] { LifecycleEvents.class }, (proxy, method, args) -> {
                        throw new IllegalStateException("injected notification failure");
                    }));
            this.lifecycle.deployComponent(SubjectImpl.class);
            this.lifecycle.activateComponent(Subject.class);
            Assert.assertTrue(this.lifecycle.isActivated(Subject.class));
            Assert.assertTrue(this.naming.isServicesBound(ServiceA.class));
            Assert.assertTrue(this.logs.containsSevereMessageWithFailure(
                    "Lifecycle notification failed after a completed transition.", "injected notification failure"));
        } finally {
            field.set(this.lifecycle, original);
        }
    }

    @Component public interface Subject { }
    @Component public interface Healthy { }
    @Component public interface Root { }
    @Component public interface Branch { }
    @Component public interface BrokenItemOwner { }
    @Component public interface Client { }
    @Services public interface ServiceA { void first(); }
    @Services public interface ServiceB { void second(); }
    @Services public interface ServiceC { void third(); }
    @Parameters public interface Settings { int value(); }
    @Events public interface Changes { void changed(); }

    @Implementation
    public static class SubjectImpl implements Subject, ServiceA, ServiceB, Settings {
        @ProducedEvents private Changes changes;
        public SubjectImpl() {
            constructions++;
            if ("constructor".equals(applicationFailure)) { throw new IllegalStateException("constructor failure"); }
        }
        @Activation public void activate() {
            if ("activation".equals(applicationFailure)) { throw new IllegalStateException("activation failure"); }
        }
        @Passivation public void passivate() {
            if ("passivation".equals(applicationFailure)) { throw new IllegalStateException("passivation failure"); }
        }
        @Removal public void remove() {
            if ("removal".equals(applicationFailure)) { throw new IllegalStateException("removal failure"); }
            applicationRemovals++;
        }
        @Override public void first() { }
        @Override public void second() { }
        @Override public int value() { return 1; }
    }

    @Implementation public static class HealthyImpl implements Healthy { }
    @Implementation public static class TypelessImpl implements ServiceC {
        @Activation public void activate() {
            if ("activation".equals(applicationFailure)) { throw new IllegalStateException("activation failure"); }
        }
        @Override public void third() { }
    }
    @Implementation public static class ClientImpl implements Client, Changes {
        @UsedServices private ServiceA first;
        @UsedServices private ServiceB second;
        @AccessedParameters private Settings settings;
        @Override public void changed() { }
        @Passivation public void passivate() { throw new IllegalStateException("client passivation failure"); }
    }

    public static class FailureObserver implements LifecycleEvents {
        private final LifecycleFailureRecoveryTests test;
        final List<Class<?>> failedTypes = new ArrayList<>();
        boolean allFailedBeforeNotification = true;
        boolean allPostFailBeforeNotification = true;
        FailureObserver(final LifecycleFailureRecoveryTests test) { this.test = test; }
        @Override public void onComponentInstantiated(final Class<?> type) { }
        @Override public void onComponentInitialized(final Class<?> type) { }
        @Override public void onComponentActivated(final Class<?> type) { }
        @Override public void onComponentPassivated(final Class<?> type) { }
        @Override public void onComponentRemoved(final Class<?> type) { }
        @Override public void onComponentFailed(final Class<?> type) {
            this.failedTypes.add(type);
            this.allFailedBeforeNotification &= this.test.lifecycle.isFailed(type);
            this.allPostFailBeforeNotification &= this.test.hooks.contains("postFail:" + type.getSimpleName());
            if (this.test.lifecycle.isDeployed(Root.class)) {
                for (Class<?> member : List.of(Root.class, Branch.class, Subject.class)) {
                    this.allFailedBeforeNotification &= this.test.lifecycle.isFailed(member);
                    this.allPostFailBeforeNotification &= this.test.hooks.contains("postFail:" + member.getSimpleName());
                }
            }
        }
    }
    @Implementation public static class RootImpl implements Root {
        @ComposedComponent private final BranchImpl branch = null;
    }
    @Implementation public static class BranchImpl implements Branch {
        @ComposedComponent private final SubjectImpl subject = null;
    }
    @Implementation public static class BrokenItemOwnerImpl implements BrokenItemOwner {
        @Item private final BrokenItem item = null;
    }
    @Item public static class BrokenItem {
        public BrokenItem() { throw new IllegalStateException("item construction failure"); }
    }
}
