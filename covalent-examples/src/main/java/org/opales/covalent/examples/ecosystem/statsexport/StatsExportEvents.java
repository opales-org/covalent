/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.statsexport;

/**
 * Raw events interface produced by {@link StatsExporter} whenever it
 * processes one more stats snapshot, so a raw consumer can keep a
 * running count and a verbatim log of the exported content without
 * polling the exporter or reading its CSV file from disk.
 * Deliberately not annotated with {@code @Events}: raw producers and
 * consumers wire this interface manually through
 * {@code RawComponentsServices}, outside the managed component model
 * (see the Covalent Raw Components Guide).
 */
public interface StatsExportEvents {

    /**
     * Raised each time the exporter processes one more line: once per tick, while export is
     * enabled.
     *
     * @param totalCount  the number of lines processed so far
     * @param headerLine  the CSV header line's exact text, non-null
     *                     only the first time it is produced
     * @param dataLine     the CSV data line's exact text for this tick,
     *                     formatted identically to what is written to
     *                     the export file when saving is enabled
     */
    void onLineExported(int totalCount, String headerLine, String dataLine);
}
