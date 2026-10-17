/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.componentview.internal.Tooltips;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The lanes grouped by component: a thread belongs to no component, as a pool's worker takes
 * any task, so each component gets one row showing, for each kind of pool, the share of its workers
 * that worked for it over the window ({@link ComponentLoadChart}). The rows keep their order while the window
 * slides: the busiest first when they appear, the tasks of no known component last.
 */
final class ComponentLoadRows {

	final VBox root = new VBox(4);

	private final ThreadsViewImpl owner;

	private final Map<String, Row> rows = new HashMap<>();

	private List<String> order = new ArrayList<>();

	private final Label none = new Label(ThreadsTexts.get("load.none"));

	ComponentLoadRows(final ThreadsViewImpl owner) {
		this.owner = owner;
		this.none.getStyleClass().add("sv-cockpit-muted");
		this.root.getStyleClass().add("sv-threads-compartment");
	}

	/** Sorts the rows again from scratch at the next draw, the busiest first: the window changed. */
	void resort() {
		this.order = new ArrayList<>();
	}

	/**
	 * @param history    the samples
	 * @param windowEnd  the last sample shown
	 * @param windowSize the samples across the window
	 * @param cursor     the sample under the pointer, {@code -1} if none
	 * @param palette    the theme's colors
	 */
	void draw(final ThreadsHistory history, final long windowEnd, final int windowSize, final long cursor,
			final Palette palette) {
		Map<String, Map<WorkerPoolKind, float[]>> shares = history.componentShares(windowEnd - windowSize + 1,
				windowEnd);
		Map<String, Double> totals = new HashMap<>();
		shares.forEach((name, byKind) -> {
			double sum = 0;
			for (float[] values : byKind.values()) {
				sum += mean(values);
			}
			if (sum > 0) {
				totals.put(name, sum);
			}
		});
		List<String> order = new ArrayList<>(this.order);
		order.retainAll(totals.keySet());
		totals.keySet().stream().filter(name -> !order.contains(name))
				.sorted(Comparator.comparing((String name) -> -totals.get(name)).thenComparing(name -> name))
				.forEach(order::add);
		if (order.remove(ThreadsHistory.UNKNOWN_COMPONENT)) {
			order.add(ThreadsHistory.UNKNOWN_COMPONENT);
		}
		this.rows.keySet().retainAll(order);
		if (!order.equals(this.order) || this.root.getChildren().isEmpty()) {
			this.order = order;
			List<Node> nodes = new ArrayList<>();
			for (String name : order) {
				nodes.add(this.rows.computeIfAbsent(name, Row::new).root);
			}
			if (nodes.isEmpty()) {
				nodes.add(this.none);
			}
			this.root.getChildren().setAll(nodes);
		}
		for (String name : order) {
			Map<WorkerPoolKind, float[]> byKind = shares.get(name);
			List<String> averages = new ArrayList<>();
			List<String> peaks = new ArrayList<>();
			for (WorkerPoolKind kind : WorkerPoolKind.values()) {
				float[] values = byKind.get(kind);
				if (values == null) {
					continue;
				}
				float peak = 0;
				for (float value : values) {
					peak = Math.max(peak, value);
				}
				averages.add(ThreadsFormat.kindLabel(kind) + " " + ThreadsFormat.share(mean(values)));
				peaks.add(ThreadsFormat.kindLabel(kind) + " " + ThreadsFormat.share(peak));
			}
			Row row = this.rows.get(name);
			row.sub.setText(String.join(" · ", averages));
			row.subTip.setText(ThreadsTexts.get("load.sub.tip", String.join(" · ", peaks)));
			row.chart.draw(byKind, windowEnd, windowSize, cursor, palette);
		}
	}

	private static double mean(final float[] values) {
		double sum = 0;
		for (float value : values) {
			sum += value;
		}
		return values.length == 0 ? 0 : sum / values.length;
	}

	/** One component: its name, its average share of each kind of pool, and its load. */
	private final class Row {

		final HBox root;
		final Label name = new Label();
		final Label sub = new Label();
		final Tooltip subTip;
		final ComponentLoadChart chart = new ComponentLoadChart();

		Row(final String component) {
			boolean unknown = component.equals(ThreadsHistory.UNKNOWN_COMPONENT);
			this.name.setText(unknown ? ThreadsTexts.get("load.unknown") : component);
			this.name.getStyleClass().add("sv-threads-lane-name");
			if (unknown) {
				this.name.getStyleClass().add("sv-threads-load-unknown");
				Tooltips.install(this.name, ThreadsTexts.get("load.unknown.tip"));
			}
			this.sub.getStyleClass().add("sv-threads-lane-sub");
			this.subTip = Tooltips.install(this.sub, "");
			VBox label = new VBox(1, this.name, this.sub);
			label.setAlignment(Pos.CENTER_LEFT);
			label.setMinWidth(PoolSection.NAME_WIDTH);
			label.setPrefWidth(PoolSection.NAME_WIDTH);
			label.setMaxWidth(PoolSection.NAME_WIDTH);
			HBox.setHgrow(this.chart, Priority.ALWAYS);
			this.root = new HBox(label, this.chart);
			this.root.setAlignment(Pos.CENTER_LEFT);
			this.root.getStyleClass().add("sv-threads-lane");
			this.chart.setOnHover(sample -> ComponentLoadRows.this.owner.hoverComponent(component, sample));
		}
	}
}
