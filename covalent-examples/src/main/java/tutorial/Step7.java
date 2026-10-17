/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Tutorial step 7: structuring components. Shows how one component is split across several classes
 * with an item (the {@code Cashier} and its receipt book), and how several components are grouped
 * into a composite (the {@code Shop}, which owns {@code Pricing} and {@code Cashier}).
 */
public final class Step7 {

    // --- Pricing: a plain component ------------------------------------------

    /** Component type of the pricing component, which provides {@link PricingServices}. */
    @Component
    public interface Pricing {
    }

    /** Services port provided by {@link Pricing}: a request/response call that prices an order. */
    @Services
    public interface PricingServices {
        /**
         * Computes the price of an order.
         *
         * @param product product name, {@code coffee} or {@code croissant}
         * @param quantity number of units
         * @return the price in cents
         */
        int priceInCents(String product, int quantity);
    }

    /** Managed class of the {@link Pricing} component, with a fixed price list. */
    @Implementation
    public static final class PricingImpl implements Pricing, PricingServices {
        @Override
        public int priceInCents(final String product, final int quantity) {
            return ("coffee".equals(product) ? 250 : 180) * quantity;
        }
    }

    // --- Cashier: a segmented component (implementation + one item) ----------

    /**
     * Component type of the cashier component, which provides {@link CashierServices} and
     * {@link ReceiptServices}.
     */
    @Component
    public interface Cashier {
    }

    /** Services port provided by {@link Cashier} to check an order out. */
    @Services
    public interface CashierServices {
        /**
         * Prices an order and records a receipt line for it.
         *
         * @param product product name, {@code coffee} or {@code croissant}
         * @param quantity number of units
         */
        void checkout(String product, int quantity);
    }

    /**
     * Services port of the cashier, carried by its receipt book item rather than by the implementation
     * class.
     */
    @Services
    public interface ReceiptServices {
        /**
         * Records one receipt line.
         *
         * @param line the receipt line
         */
        void record(String line);

        /**
         * Returns the receipt lines recorded so far.
         *
         * @return a copy of the recorded receipt lines, in recording order
         */
        List<String> readReceipts();
    }

    // The item is an internal part of the Cashier component. It carries the
    // ReceiptServices port; it is not a component of its own.
    /**
     * Item of the {@link Cashier} component: an internal class that carries the
     * {@link ReceiptServices} port and keeps the receipt lines. It is not a component of its own.
     */
    @Item
    public static final class ReceiptBookItem implements ReceiptServices {

        private final List<String> receipts = new ArrayList<>();

        @Override
        public void record(final String line) {
            this.receipts.add(line);
        }

        @Override
        public List<String> readReceipts() {
            return new ArrayList<>(this.receipts);
        }
    }

    /**
     * Managed class of the {@link Cashier} component; it owns a {@link ReceiptBookItem} and uses
     * {@link PricingServices} to price each order.
     */
    @Implementation
    public static final class CashierImpl implements Cashier, CashierServices {

        @Item
        private ReceiptBookItem receiptBook;

        @UsedServices
        private PricingServices usePricingServices;

        @Override
        public void checkout(final String product, final int quantity) {
            int total = this.usePricingServices.priceInCents(product, quantity);
            this.receiptBook.record(quantity + " x " + product + " = " + total + " cents");
        }
    }

    // --- Shop: a composite owning Pricing and Cashier ------------------------

    /** Component type of the shop composite, which groups {@link Pricing} and {@link Cashier}. */
    @Component
    public interface Shop {
    }

    /**
     * Managed class of the {@link Shop} composite; it declares its two parts as owned implementations,
     * so deploying the shop deploys them too.
     */
    @Implementation
    public static final class ShopImpl implements Shop {

        @Implementation
        private PricingImpl pricing;

        @Implementation
        private CashierImpl cashier;
    }

    // --- Application -----------------------------------------------------------

    /** Component type of the application component, which checks orders out and prints the receipts. */
    @Component
    public interface Application {
    }

    /**
     * Managed class of the {@link Application} component; it uses both the cashier and the receipt
     * services, which are provided by the same component.
     */
    @Implementation
    public static final class ApplicationImpl implements Application {

        @UsedServices
        private CashierServices useCashierServices;

        @UsedServices
        private ReceiptServices useReceiptServices;

        /** Checks out two orders, then prints the recorded receipts, when the component is activated. */
        @Activation
        public void run() {
            this.useCashierServices.checkout("coffee", 2);
            this.useCashierServices.checkout("croissant", 3);
            this.useReceiptServices.readReceipts().forEach(System.out::println);
        }
    }

    /**
     * Deploys the shop composite and the application, and starts the container; always stops the
     * container.
     *
     * @param args command-line arguments, ignored
     */
    public static void main(final String[] args) {
        CovalentContainer container = Covalent.main;
        try {
            // Only the composite and the application are deployed explicitly:
            // Pricing and Cashier are deployed with the Shop composite.
            container.deployComponents(ShopImpl.class, ApplicationImpl.class);
            container.start();
        } finally {
            if (container.isStarted()) {
                container.stop();
            }
        }
    }
}
