/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.statsexport;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.examples.ecosystem.ecosystem.model.EcosystemStatsServices;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Species;

/**
 * Raw component computing the ecosystem's population snapshots at a
 * fixed real-time cadence, while export is enabled, and optionally
 * persisting them to a CSV file, while saving is separately enabled.
 * Registered and wired manually by {@link EcosystemStatsExportDemoLauncher},
 * outside any managed container lifecycle: not annotated with
 * {@code @Component} or {@code @Implementation}, and not deployable
 * through the managed lifecycle API (see the Covalent Raw Components
 * Guide).
 * <p>
 * Pulls {@link EcosystemStatsServices} from the managed Ecosystem
 * component through {@code RawComponentsServices.useServices}, and
 * produces {@link StatsExportEvents} on every tick, carrying the exact
 * header and data line text, so a raw consumer (see {@link
 * StatsExportWidget}) can display a verbatim running log of what is
 * being exported without needing to read the export file itself.
 * Exposes plain {@link #setExportEnabled(boolean)} and {@link
 * #setSaveEnabled(boolean)} switches, called directly by {@link
 * StatsExportWidget}'s two checkboxes rather than through a Covalent
 * port: the former turns the per-tick computation and logging on or
 * off, the latter turns persisting those same lines to the CSV file
 * on or off, independently. The export file is only ever created once
 * saving is enabled for the first time, never merely because export
 * is enabled: a user who only watches the log never gets a file on
 * disk they did not ask for.
 */
public class StatsExporter {

    private static final Logger log = Logger.getLogger(StatsExporter.class.getName());

    private static final long TICK_INTERVAL_MILLIS = 1000L;

    private final RawComponentsServices rawComponentsServices;
    private final Path exportFilePath;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "stats-exporter");
        thread.setDaemon(true);
        return thread;
    });

    private EcosystemStatsServices useEcosystemStatsServices;
    private StatsExportEvents producedEvents;
    private BufferedWriter writer;

    private volatile boolean exportEnabled;
    private volatile boolean saveEnabled;
    private boolean stopped;
    private String headerText;
    private boolean fileHeaderWritten;
    private int totalCount;

    /**
     * Creates an exporter, with export and saving disabled. Nothing is scheduled until {@link
     * #registerAndWire()}.
     * @param rawComponentsServices the services through which this exporter registers and
     *     wires itself
     * @param exportFilePath the CSV file written while saving is enabled, created on first
     *     save
     */
    public StatsExporter(final RawComponentsServices rawComponentsServices, final Path exportFilePath) {
        this.rawComponentsServices = rawComponentsServices;
        this.exportFilePath = exportFilePath;
    }

    /**
     * Registers this exporter as a raw component, pulls {@link
     * EcosystemStatsServices} from the already-activated Ecosystem
     * component, starts producing {@link StatsExportEvents}, and
     * schedules the periodic tick. Must be called only once Ecosystem
     * is activated and publishing its stats services - see {@link
     * EcosystemStatsExportDemoLauncher} for the required wiring order.
     */
    public void registerAndWire() {
        this.rawComponentsServices.registerComponent(StatsExporter.class, this);
        this.useEcosystemStatsServices = this.rawComponentsServices.useServices(EcosystemStatsServices.class);
        this.producedEvents = this.rawComponentsServices.produceEvents(StatsExportEvents.class);
        this.executor.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MILLIS, TICK_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Reports whether the per-tick computation and logging is on.
     * @return {@code true} if export is enabled
     */
    public boolean isExportEnabled() {
        return this.exportEnabled;
    }

    /**
     * Turns the per-tick computation and logging on or off.
     * @param exportEnabled {@code true} to enable export
     */
    public void setExportEnabled(final boolean exportEnabled) {
        this.exportEnabled = exportEnabled;
    }

    /**
     * Reports whether the exported lines are persisted to the CSV file.
     * @return {@code true} if saving is enabled
     */
    public boolean isSaveEnabled() {
        return this.saveEnabled;
    }

    /**
     * Turns persisting the exported lines to the CSV file on or off. The file is created the
     * first time
     * saving is on.
     * @param saveEnabled {@code true} to enable saving
     */
    public void setSaveEnabled(final boolean saveEnabled) {
        this.saveEnabled = saveEnabled;
    }

    /**
     * Stops the periodic tick and closes the export file, if one was ever opened. A tick in
     * progress ends first, and none starts afterwards.
     */
    public void stop() {
        this.executor.shutdownNow();
        synchronized (this) {
            this.stopped = true;
            if (this.writer != null) {
                try {
                    this.writer.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    /**
     * The scheduled task. Any failure is logged and the next tick tries again: an exception
     * escaping a task run at a fixed rate would silently cancel every later run.
     */
    private void tick() {
        try {
            this.exportOnce();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Statistics export tick failed.", e);
        }
    }

    private synchronized void exportOnce() {
        if (this.stopped || !this.exportEnabled) {
            return;
        }
        List<Species> species = this.useEcosystemStatsServices.getSpecies();
        Map<String, Double> populations = this.useEcosystemStatsServices.getPopulations();

        boolean headerIsNew = this.headerText == null;
        if (headerIsNew) {
            this.headerText = species.stream().map(Species::name).collect(Collectors.joining(","));
        }
        String dataText = species.stream().map(s -> String.valueOf(populations.getOrDefault(s.name(), 0.0)))
                .collect(Collectors.joining(","));

        if (this.saveEnabled) {
            try {
                this.appendToFile(dataText);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        this.totalCount++;
        this.producedEvents.onLineExported(this.totalCount, headerIsNew ? this.headerText : null, dataText);
    }

    private void appendToFile(final String dataText) throws IOException {
        if (this.writer == null) {
            this.writer = Files.newBufferedWriter(this.exportFilePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        if (!this.fileHeaderWritten) {
            this.writer.write(this.headerText);
            this.writer.newLine();
            this.fileHeaderWritten = true;
        }
        this.writer.write(dataText);
        this.writer.newLine();
        this.writer.flush();
    }
}
