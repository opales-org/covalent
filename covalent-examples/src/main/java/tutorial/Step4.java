/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;

/**
 * Tutorial step 4: parameters. A {@code Shop} component configures the tax rate of the
 * {@code Pricing} component through a parameters interface, then uses the pricing services.
 */
public final class Step4 {

    // --- The pricing component: it provides a service and offers parameters ---

    /**
     * Component type of the pricing component, which provides {@link PricingServices} and offers
     * {@link PricingParameters}.
     */
    @Component
    public interface Pricing {
    }

    /** Services port provided by {@link Pricing}: a request/response call that prices an order. */
    @Services
    public interface PricingServices {
        /**
         * Computes the price of an order, tax included.
         *
         * @param product product name, {@code coffee} or {@code croissant}
         * @param quantity number of units
         * @return the price in cents, including the configured tax
         * @throws IllegalArgumentException if the product is unknown
         */
        int priceInCents(String product, int quantity);
    }

    /**
     * Parameters port offered by {@link Pricing}: calls that configure the component itself, as
     * opposed to services used for the benefit of the caller.
     */
    @Parameters
    public interface PricingParameters {
        /**
         * Sets the tax rate applied to every price.
         *
         * @param taxRatePercent tax rate, in percent of the net price
         */
        void setTaxRatePercent(int taxRatePercent);

        /**
         * Returns the tax rate applied to every price.
         *
         * @return tax rate, in percent of the net price
         */
        int getTaxRatePercent();
    }

    /**
     * Managed class of the {@link Pricing} component; it provides the service and the parameters, and
     * adds the configured tax to the net price.
     */
    @Implementation
    public static final class PricingImpl implements Pricing, PricingServices, PricingParameters {

        private int taxRatePercent;

        @Override
        public void setTaxRatePercent(final int taxRatePercent) {
            this.taxRatePercent = taxRatePercent;
        }

        @Override
        public int getTaxRatePercent() {
            return this.taxRatePercent;
        }

        @Override
        public int priceInCents(final String product, final int quantity) {
            int unitPriceInCents = switch (product) {
                case "coffee" -> 250;
                case "croissant" -> 180;
                default -> throw new IllegalArgumentException("Unknown product: " + product);
            };
            int netInCents = unitPriceInCents * quantity;
            return netInCents + netInCents * this.taxRatePercent / 100;
        }
    }

    // --- The shop component: it configures the pricing, then uses it ----------

    /** Component type of the shop component, which configures and uses the pricing component. */
    @Component
    public interface Shop {
    }

    /**
     * Managed class of the {@link Shop} component; it accesses {@link PricingParameters} to configure
     * the pricing and uses {@link PricingServices} to price an order.
     */
    @Implementation
    public static final class ShopImpl implements Shop {

        @AccessedParameters
        private PricingParameters accessPricingParameters;

        @UsedServices
        private PricingServices usePricingServices;

        /**
         * Sets the tax rate to 10 percent through the accessed parameters; runs once at initialization,
         * before the activation that uses the service.
         */
        @Initialization
        public void configurePricing() {
            this.accessPricingParameters.setTaxRatePercent(10);
            System.out.println("Tax rate configured: " + this.accessPricingParameters.getTaxRatePercent() + "%");
        }

        /** Prices two coffees, tax included, and prints the total, when the component is activated. */
        @Activation
        public void openShop() {
            int total = this.usePricingServices.priceInCents("coffee", 2);
            System.out.println("Two coffees cost " + total + " cents, tax included.");
        }
    }

    /**
     * Deploys both components in the main container and starts it; always stops the container.
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
