/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.ContainerStatus;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Removal;

/**
 * Tutorial step 1: the first component. Defines one managed component, deploys it in the main
 * container, starts the container, checks that the startup report is healthy and stops the
 * container; the component prints a message at activation and at removal.
 */
public final class Step1 {

    /**
     * Component type of the greeting component: an empty interface annotated {@code @Component}, which
     * is the public identity of the component.
     */
    @Component
    public interface Hello {
    }

    /**
     * Managed class holding the behavior of the {@link Hello} component, with one lifecycle callback
     * at activation and one at removal.
     */
    @Implementation
    public static final class HelloImpl implements Hello {

        /** Prints a greeting when the component is activated. */
        @Activation
        public void sayHello() {
            System.out.println("Hello from a Covalent component!");
        }

        /** Prints a farewell when the component is removed. */
        @Removal
        public void sayGoodbye() {
            System.out.println("Goodbye from a Covalent component!");
        }
    }

    /**
     * Deploys {@link HelloImpl} in the main container, starts it, prints the startup status and always
     * stops the container; fails if the startup is not healthy.
     *
     * @param args command-line arguments, ignored
     */
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
