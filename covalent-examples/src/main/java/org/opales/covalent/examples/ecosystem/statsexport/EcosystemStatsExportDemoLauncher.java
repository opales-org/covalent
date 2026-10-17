/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.statsexport;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.examples.ecosystem.impl.EcosystemDemoImpl;
import org.opales.covalent.examples.launcher.DemoLauncher;

/**
 * The Ecosystem demo's own {@link DemoLauncher}: wires the raw {@link
 * StatsExporter} / {@link StatsExportWidget} pair around the managed
 * EcosystemDemo composite. Raw components cannot be wired from inside a
 * managed component, so this ordinary Java class - not a Covalent
 * component of any kind - owns that sequencing instead, on behalf of
 * whichever caller holds the {@link CovalentContainer} (the shared
 * launcher's "Ecosystem Demo" button, in this module).
 * <p>
 * Wiring order matters on {@link #start}:
 * <ol>
 *   <li>{@link StatsExportWidget} is built and registered first, and
 *   offers its {@link StatsExportWidgetParameters} immediately, before
 *   the managed composite deploys, so HmiBuilder's own
 *   {@code @AccessedParameters} field for it resolves once HmiBuilder
 *   itself activates.</li>
 *   <li>The EcosystemDemo composite is then deployed and started, which
 *   activates Ecosystem (publishing {@code EcosystemStatsServices}) and
 *   HmiBuilder (embedding the widget's root node).</li>
 *   <li>Only then is {@link StatsExporter} registered and wired: it
 *   needs {@code EcosystemStatsServices}, which exists only once
 *   Ecosystem is activated.</li>
 * </ol>
 * {@link #stop} undoes this in reverse: the managed composite first (so
 * HmiBuilder closes its window like every other demo), then the raw
 * exporter's own resources. {@link CovalentContainer#stop} already walks
 * every deployed component - raw ones included, since {@link
 * RawComponentsServices#registerComponent} deploys through the same
 * lifecycle machinery as a managed component - so both raw components are
 * already undeployed by the time this method runs; it does not unregister
 * them again, since by then neither is left registered to unregister.
 * <p>
 * The two raw components and the {@code EcosystemDemo} composite depend on
 * each other in a cycle, which the dependency graph reports. It is kept on
 * purpose: see "A Deliberate Dependency Cycle" in the demo's architecture
 * page ({@code doc/architecture/examples/ecosystem-demonstration}).
 */
public class EcosystemStatsExportDemoLauncher implements DemoLauncher {

    private StatsExporter exporter;
    private StatsExportWidget widget;

    @Override
    public void start(final CovalentContainer container) {
        RawComponentsServices rawComponentsServices = container.rcs();

        Path exportFilePath = Paths.get("ecosystem-stats-export.csv");
        this.exporter = new StatsExporter(rawComponentsServices, exportFilePath);
        this.widget = new StatsExportWidget(rawComponentsServices, this.exporter);
        this.widget.register();

        container.deployComponents(EcosystemDemoImpl.class);
        container.start();

        this.exporter.registerAndWire();
    }

    @Override
    public void stop(final CovalentContainer container) {
        // The exporter first: its tick pulls from Ecosystem, which the container passivates.
        if (this.exporter != null) {
            this.exporter.stop();
        }
        container.stop();
    }
}
