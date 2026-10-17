# Step 7: Structuring Components

<!-- doc-nav:top -->
[Home](../../README.md) | [Previous: Step 6: Advanced Events](06-advanced-events.md) | [Next: Step 8: Existing Objects](08-existing-objects.md)
<!-- /doc-nav:top -->

A Covalent component is coarser than a class. Covalent gives you two ways to
structure things, at two different scales:

- **items** split the implementation of *one* component across several classes;
- **composition** organizes *several* components into a tree.

## Items: Segmenting One Component

When the implementation of a component grows, you do not have to put everything in
one class. The implementation class remains the root of the component, and it
delegates part of the work to **items**: internal parts of the same component. An item is
not a component of its own, but it can carry ports.

In this step, the `Cashier` component keeps its receipts in a `ReceiptBookItem`.
The item is annotated with `@Item`, and so is the field that holds it in the
implementation:

```java
@Item
public static final class ReceiptBookItem implements ReceiptServices {
```

```java
@Item
private ReceiptBookItem receiptBook;
```

Covalent creates the item and injects it in the field. Because the item implements
`ReceiptServices`, that service is published as a port of the `Cashier` component:
other components use it without knowing that it is implemented by an item.

Items follow the lifecycle of their component. Lifecycle callbacks stay on the
implementation class; if an item needs initialization or cleanup, the
implementation coordinates it.

## Composition: A Tree Of Components

Composition is the UML kind: a **composite** component owns sub-components, and their
lifecycle is tied to the lifecycle of the composite. Sub-components can be
composites themselves, with no depth limit. It is the tool to structure a large
application as a few root components, each refined into finer components.

A composite declares its sub-components on fields of its implementation:

```java
@Implementation
public static final class ShopImpl implements Shop {

    @Implementation
    private PricingImpl pricing;

    @Implementation
    private CashierImpl cashier;
}
```

The sub-components are regular components, with their own type, ports and
lifecycle. They can use services, events and parameters of any other component. In
the program below, only the composite is deployed explicitly: `Pricing` and
`Cashier` are deployed with it.

## Items Or Sub-Components?

| | Item | Composed component |
|---|------|--------------------|
| What it is | an internal part of one component | an independent component |
| Own component type | no | yes |
| Own lifecycle state | no, it follows its component | yes, tied to its composite |
| Structures | the implementation of a component | the architecture of the application |

## The Complete Program

```java
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

public final class Step7 {

    // --- Pricing: a plain component ------------------------------------------

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
            return ("coffee".equals(product) ? 250 : 180) * quantity;
        }
    }

    // --- Cashier: a segmented component (implementation + one item) ----------

    @Component
    public interface Cashier {
    }

    @Services
    public interface CashierServices {
        void checkout(String product, int quantity);
    }

    @Services
    public interface ReceiptServices {
        void record(String line);

        List<String> readReceipts();
    }

    // The item is an internal part of the Cashier component. It carries the
    // ReceiptServices port; it is not a component of its own.
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

    @Component
    public interface Shop {
    }

    @Implementation
    public static final class ShopImpl implements Shop {

        @Implementation
        private PricingImpl pricing;

        @Implementation
        private CashierImpl cashier;
    }

    // --- Application -----------------------------------------------------------

    @Component
    public interface Application {
    }

    @Implementation
    public static final class ApplicationImpl implements Application {

        @UsedServices
        private CashierServices useCashierServices;

        @UsedServices
        private ReceiptServices useReceiptServices;

        @Activation
        public void run() {
            this.useCashierServices.checkout("coffee", 2);
            this.useCashierServices.checkout("croissant", 3);
            this.useReceiptServices.readReceipts().forEach(System.out::println);
        }
    }

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
```

Its output:

```text
2 x coffee = 500 cents
3 x croissant = 540 cents
```

## What You Learned

- `@Item` segments the implementation of a component; items can carry ports.
- A composite owns its sub-components, and their lifecycle follows its own.
- Items structure a component; composition structures an application.

Reference: [Items](../managed-components.md#items),
[Composition](../managed-components.md#composition).

<!-- doc-nav:bottom -->
[Home](../../README.md) | [Previous: Step 6: Advanced Events](06-advanced-events.md) | [Next: Step 8: Existing Objects](08-existing-objects.md)
<!-- /doc-nav:bottom -->
