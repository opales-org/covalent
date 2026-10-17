# Step 8: Existing Objects

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 7: Structuring Components](07-structuring-components.md) | [Next: Step 9: Next Steps](09-next-steps.md)
<!-- /doc-nav:top -->

Real applications rarely start from a blank page. They already contain objects that
were not written for Covalent: existing classes, objects created by another
framework, objects owned by a UI toolkit. Covalent can integrate such objects
without rewriting them, as **raw components**.

A raw component is an externally owned object registered in the container. Unlike a
managed component, it is instantiated by its owner, it has no Covalent annotation,
and its ports are declared explicitly through the container's `RawComponentsServices`
instead of by annotations.

## The Existing Object

Here is a plain Java class that implements a plain Java interface. Neither has any
reference to Covalent:

```java
public interface StockServices {
    int quantityInStock(String product);
}
```

```java
public static final class ExistingStock implements StockServices {
```

## Registering And Publishing It

The integration happens at the boundary of the application, through
`container.rcs()`:

```java
rawComponents.registerComponent(stock);
```

`registerComponent(...)` registers the existing instance; by default, its component
type is its class, and it is brought to the `activated` state.

```java
rawComponents.provideServices(ExistingStock.class, StockServices.class);
```

`provideServices(...)` declares that it provides `StockServices`; the component
type is given explicitly because the provider is a raw component.

From then on, a managed component uses the service like any other, with a
`@UsedServices` field. It does not know, and does not need to know, that the
provider is a raw component:

```java
@UsedServices
private StockServices useStockServices;
```

> Important: `RawComponentsServices` is meant to interface with the non-Covalent world. It
> rejects classes that carry Covalent annotations: components written for Covalent
> must be deployed as managed components.

## The Complete Program

```java
package tutorial;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.raw.RawComponentsServices;

public final class Step8 {

    // --- A plain Java interface: no Covalent annotation, since it is the contract
    //     of a non-Covalent object ------------------------------------------------

    public interface StockServices {
        int quantityInStock(String product);
    }

    // --- An existing object: no Covalent annotation, no Covalent import needed
    //     other than the raw services used to declare its ports ----------------

    public static final class ExistingStock implements StockServices {
        @Override
        public int quantityInStock(final String product) {
            return "coffee".equals(product) ? 42 : 7;
        }
    }

    // --- A managed component using the existing object's service ------------------

    @Component
    public interface Shop {
    }

    @Implementation
    public static final class ShopImpl implements Shop {

        @UsedServices
        private StockServices useStockServices;

        @Activation
        public void openShop() {
            System.out.println("Coffee in stock: " + this.useStockServices.quantityInStock("coffee"));
        }
    }

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
```

Its output:

```text
Coffee in stock: 42
```

## Going Further

Raw components can do the reverse and more: use services of managed components,
offer and access parameters, produce and subscribe to events, follow the lifecycle
callbacks, and be unregistered. This makes it possible to bring an existing application
into Covalent one object at a time. Raw components have a runtime cost that managed
components do not, which is documented in [Performance
Characteristics](../raw-components.md#performance-characteristics).

Reference: [Raw Components Guide](../raw-components.md),
[Managed and Raw Components](../foundations.md#managed-and-raw-components).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 7: Structuring Components](07-structuring-components.md) | [Next: Step 9: Next Steps](09-next-steps.md)
<!-- /doc-nav:bottom -->
