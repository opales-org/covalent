# Step 4: Parameters

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 3: Events](03-events.md) | [Next: Step 5: Lifecycle](05-lifecycle.md)
<!-- /doc-nav:top -->

Parameters are how a component is configured. They look like services (a
request/response call through an interface), but their intent is different: a
service call is made for the benefit of the caller, while a parameter call
configures the component that offers it.

## Declaring And Offering Parameters

A parameters interface is annotated with `@Parameters`. Parameters are typically
exposed as setters, with an optional getter.

```java
@Parameters
public interface PricingParameters {
    void setTaxRatePercent(int taxRatePercent);

    int getTaxRatePercent();
}
```

A component offers parameters by implementing the interface, exactly as it
provides services. A component can provide services and offer parameters at the same
time, as `PricingImpl` does here.

```java
@Implementation
public static final class PricingImpl implements Pricing, PricingServices, PricingParameters {
```

## Accessing Parameters

A component that wants to configure another one declares a field of the
parameters interface type, annotated with `@AccessedParameters`.

```java
@AccessedParameters
private PricingParameters accessPricingParameters;
```

Parameters are connected earlier than services, so they are available in the
`@Initialization` callback, before the component is activated. That is the natural
place to configure other components.

```java
@Initialization
public void configurePricing() {
    this.accessPricingParameters.setTaxRatePercent(10);
    System.out.println("Tax rate configured: " + this.accessPricingParameters.getTaxRatePercent() + "%");
}
```

Services, on the other hand, are used later, during activation.

## The Complete Program

```java
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

public final class Step4 {

    // --- The pricing component: it provides a service and offers parameters ---

    @Component
    public interface Pricing {
    }

    @Services
    public interface PricingServices {
        int priceInCents(String product, int quantity);
    }

    @Parameters
    public interface PricingParameters {
        void setTaxRatePercent(int taxRatePercent);

        int getTaxRatePercent();
    }

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

    @Component
    public interface Shop {
    }

    @Implementation
    public static final class ShopImpl implements Shop {

        @AccessedParameters
        private PricingParameters accessPricingParameters;

        @UsedServices
        private PricingServices usePricingServices;

        @Initialization
        public void configurePricing() {
            this.accessPricingParameters.setTaxRatePercent(10);
            System.out.println("Tax rate configured: " + this.accessPricingParameters.getTaxRatePercent() + "%");
        }

        @Activation
        public void openShop() {
            int total = this.usePricingServices.priceInCents("coffee", 2);
            System.out.println("Two coffees cost " + total + " cents, tax included.");
        }
    }

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
```

Its output:

```text
Tax rate configured: 10%
Two coffees cost 550 cents, tax included.
```

## Parameters Are Not Only For Your Components

The container is itself made of Covalent components, and some of them offer
parameters interfaces that you access like your own. `EventsManagerParameters`
(the worker pool of asynchronous events) and `LifecycleParameters` are examples:
declare an `@AccessedParameters` field of that type and configure the container
from a component. `container.getParameters()`, used in the previous step to switch
to synchronous events, is a shortcut for container-level settings.

## What You Learned

- A parameters interface is annotated with `@Parameters` and implemented by the
  component it configures.
- `@AccessedParameters` injects it in the configuring component.
- Parameters are available from the initialization of a component.

Reference: [Parameters](../managed-components.md#parameters).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 3: Events](03-events.md) | [Next: Step 5: Lifecycle](05-lifecycle.md)
<!-- /doc-nav:bottom -->
