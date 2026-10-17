/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

/**
 * Tutorial step 5: lifecycle. Shows the transitions of a component (deployment, initialization,
 * activation, passivation, removal), the callbacks attached to them, and how to drive the
 * transitions through the container's lifecycle services while printing the component state.
 */
public final class Step5 {

    /** Component type of the component whose lifecycle is traced. */
    @Component
    public interface Ticker {
    }

    /**
     * Managed class of the {@link Ticker} component, with one callback per lifecycle transition, each
     * printing a trace line.
     */
    @Implementation
    public static final class TickerImpl implements Ticker {

        /** Called once, when the component is initialized, to prepare its resources. */
        @Initialization
        public void prepare() {
            System.out.println("  [Ticker] initialization: prepare resources (once)");
        }

        /** Called at each activation, when the ports of the component are published. */
        @Activation
        public void open() {
            System.out.println("  [Ticker] activation: ports are published");
        }

        /**
         * Called at each passivation, when the ports are unpublished; the component can be activated
         * again.
         */
        @Passivation
        public void close() {
            System.out.println("  [Ticker] passivation: ports are unpublished");
        }

        /** Called when the component is removed, to release its resources. */
        @Removal
        public void release() {
            System.out.println("  [Ticker] removal: release resources");
        }
    }

    /**
     * Deploys the component, then initializes, starts, passivates and reactivates it through the
     * lifecycle services, printing its state after each step.
     *
     * @param args command-line arguments, ignored
     */
    public static void main(final String[] args) {
        CovalentContainer container = Covalent.main;
        LifecycleServices lifecycle = container.lcs();
        try {
            container.deployComponents(TickerImpl.class);
            System.out.println("State after deployment: " + lifecycle.readComponentState(Ticker.class));

            lifecycle.initializeComponent(Ticker.class);
            System.out.println("State after initializeComponent: " + lifecycle.readComponentState(Ticker.class));

            container.start();
            System.out.println("State after start: " + lifecycle.readComponentState(Ticker.class));

            lifecycle.passivateComponent(Ticker.class);
            System.out.println("State after passivateComponent: " + lifecycle.readComponentState(Ticker.class));

            lifecycle.activateComponent(Ticker.class);
            System.out.println("State after activateComponent: " + lifecycle.readComponentState(Ticker.class));
        } finally {
            if (container.isStarted()) {
                container.stop();
            }
        }
    }
}
