# Step 2: Services

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 1: Your First Component](01-first-component.md) | [Next: Step 3: Events](03-events.md)
<!-- /doc-nav:top -->

A service is a request/response interaction between two components: the caller
asks, the provider answers. In this step, a `Shop` component asks a `Pricing`
component for the price of an order.

## Declaring The Service

A services interface is a plain Java interface annotated with `@Services`.
Arguments and return values are ordinary Java types: they need no Covalent
annotation and no framework base class.

```java
@Services
public interface PricingServices {
    int priceInCents(String product, int quantity);
}
```

## Providing The Service

A component provides a service simply by implementing the services interface.

```java
@Implementation
public static final class PricingImpl implements Pricing, PricingServices {
```

## Using The Service

A component that needs a service declares a field of the services interface type,
annotated with `@UsedServices`. The container injects the implementation, so the
user never creates or looks up the provider.

```java
@UsedServices
private PricingServices usePricingServices;
```

The field has the type of the interface, never the type of the implementation:
`ShopImpl` does not know that `PricingImpl` exists. The two components are only
connected through the `PricingServices` contract.

> Tip: By convention, a used services field is named `use` followed by the services
> interface name.

## The Complete Program

```java
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

public final class Step2 {

    // --- The pricing component: it provides a service -------------------

    @Component
    public interface Pricing {
    }

    @Services
    public interface PricingServices {
        int priceInCents(String product, int quantity);
    }

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

    @Component
    public interface Shop {
    }

    @Implementation
    public static final class ShopImpl implements Shop {

        @UsedServices
        private PricingServices usePricingServices;

        @Activation
        public void openShop() {
            int total = this.usePricingServices.priceInCents("coffee", 2);
            System.out.println("Two coffees cost " + total + " cents.");
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
Two coffees cost 500 cents.
```

In this program, the provider is deployed before its user, and the user calls the
service during its own activation. Covalent also has mechanisms to reduce coupling
to the startup order; see [Vertical and Horizontal Start Modes](../managed-components.md#vertical-and-horizontal-start-modes).

## What You Learned

- A services interface is annotated with `@Services`; a component that implements it
  is a provider.
- A component that declares a `@UsedServices` field is a user; the container injects
  the provider.
- Provider and user are only coupled through the interface.

Reference: [Services](../managed-components.md#services).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 1: Your First Component](01-first-component.md) | [Next: Step 3: Events](03-events.md)
<!-- /doc-nav:bottom -->
