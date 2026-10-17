/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Tutorial step 8: existing objects. Registers an object that has no Covalent annotation in the
 * container as a raw component, declares the services port it provides, and uses it from a managed
 * component.
 */
public final class Step8 {

    // --- A plain Java interface: no Covalent annotation, since it is the contract
    //     of a non-Covalent object ------------------------------------------------

    /**
     * Plain Java interface, with no Covalent annotation, describing the services of the existing stock
     * object.
     */
    public interface StockServices {
        /**
         * Returns the quantity in stock of a product.
         *
         * @param product product name
         * @return the number of units in stock
         */
        int quantityInStock(String product);
    }

    // --- An existing object: no Covalent annotation, no Covalent import needed
    //     other than the raw services used to declare its ports ----------------

    /**
     * Existing object that knows nothing about Covalent; it is registered in the container as a raw
     * component and provides {@link StockServices}.
     */
    public static final class ExistingStock implements StockServices {
        @Override
        public int quantityInStock(final String product) {
            return "coffee".equals(product) ? 42 : 7;
        }
    }

    // --- A managed component using the existing object's service ------------------

    /** Component type of the shop component, which uses {@link StockServices}. */
    @Component
    public interface Shop {
    }

    /**
     * Managed class of the {@link Shop} component; the services it uses are provided by the raw
     * component.
     */
    @Implementation
    public static final class ShopImpl implements Shop {

        @UsedServices
        private StockServices useStockServices;

        /** Prints the stock of coffee, read through the used services, when the component is activated. */
        @Activation
        public void openShop() {
            System.out.println("Coffee in stock: " + this.useStockServices.quantityInStock("coffee"));
        }
    }

    /**
     * Registers the existing object and declares its services, then deploys the shop in the main
     * container and starts it; always stops the container.
     *
     * @param args command-line arguments, ignored
     */
    public static void main(final String[] args) {
        CovalentContainer container = Covalent.main;
        RawComponentsServices rawComponents = container.rcs();
        ExistingStock stock = new ExistingStock();
        try {
            // 1. Register the existing object. Its component type is its class.
            rawComponents.registerComponent(stock);
            // 2. Declare the service port it provides.
            rawComponents.provideServices(ExistingStock.class, StockServices.class);

            // 3. Managed components are deployed and started as usual.
            container.deployComponents(ShopImpl.class);
            container.start();
        } finally {
            if (container.isStarted()) {
                container.stop();
            }
        }
    }
}
