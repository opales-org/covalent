/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Tutorial step 2: services. A {@code Shop} component asks a {@code Pricing} component for the
 * price of an order through a services interface; the container connects the caller to the
 * provider.
 */
public final class Step2 {

    // --- The pricing component: it provides a service -------------------

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
         * @throws IllegalArgumentException if the product is unknown
         */
        int priceInCents(String product, int quantity);
    }

    /**
     * Managed class of the {@link Pricing} component; it provides {@link PricingServices} with a fixed
     * price list.
     */
    @Implementation
    public static final class PricingImpl implements Pricing, PricingServices {

        @Override
        public int priceInCents(final String product, final int quantity) {
            int unitPriceInCents = switch (product) {
                case "coffee" -> 250;
                case "croissant" -> 180;
                default -> throw new IllegalArgumentException("Unknown product: " + product);
            };
            return unitPriceInCents * quantity;
        }
    }

    // --- The shop component: it uses the service -------------------------

    /** Component type of the shop component, which uses {@link PricingServices}. */
    @Component
    public interface Shop {
    }

    /**
     * Managed class of the {@link Shop} component; the container injects the pricing services it
     * declares as used.
     */
    @Implementation
    public static final class ShopImpl implements Shop {

        @UsedServices
        private PricingServices usePricingServices;

        /**
         * Prices two coffees through the used pricing service and prints the total, when the component is
         * activated.
         */
        @Activation
        public void openShop() {
            int total = this.usePricingServices.priceInCents("coffee", 2);
            System.out.println("Two coffees cost " + total + " cents.");
        }
    }

    /**
     * Deploys both components in the main container and starts it, which activates the shop; always
     * stops the container.
     *
     * @param args command-line arguments, ignored
     */
    public static void main(final String[] args) {
        CovalentContainer container = Covalent.main;
        try {
            container.deployComponents(PricingImpl.class, ShopImpl.class);
            container.start();
        } finally {
            if (container.isStarted()) {
                container.stop();
            }
        }
    }
}
