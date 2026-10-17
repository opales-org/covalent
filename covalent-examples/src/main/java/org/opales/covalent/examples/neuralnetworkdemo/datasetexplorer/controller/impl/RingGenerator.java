/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetGenerator;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetShape;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A filled inner disk (label {@code 0.0}) surrounded by a separate outer
 * annulus (label {@code 1.0}), both centered on the dataset's plotted
 * range. A single closed, curved boundary like {@link TwoMoonsGenerator},
 * but a full circle rather than two open arcs - slightly harder than two
 * moons (the boundary wraps all the way around) but still just one smooth
 * curvature, one step up the {@link DatasetShape} difficulty ladder.
 */
public final class RingGenerator implements DatasetGenerator {

	private static final double CENTER_X = 0.5;
	private static final double CENTER_Y = 0.25;
	private static final double INNER_DISK_MAX_RADIUS = 0.35;
	private static final double OUTER_RING_MIN_RADIUS = 0.55;
	private static final double OUTER_RING_MAX_RADIUS = 0.95;

	@Override
	public List<TrainingSample> generate(final Random random, final double noise, final int samplesPerClass) {
		List<TrainingSample> all = new ArrayList<>(samplesPerClass * 2);

		for (int i = 0; i < samplesPerClass; i++) {
			all.add(annulusSample(random, noise, 0.0, 0.0, INNER_DISK_MAX_RADIUS));
		}
		for (int i = 0; i < samplesPerClass; i++) {
			all.add(annulusSample(random, noise, 1.0, OUTER_RING_MIN_RADIUS, OUTER_RING_MAX_RADIUS));
		}

		return all;
	}

	/**
	 * Draws one point uniformly at random within the annulus
	 * {@code [minRadius, maxRadius]} (a plain disk when {@code minRadius}
	 * is {@code 0}) centered on this shape's center, then adds Gaussian
	 * coordinate jitter.
	 * <p>
	 * The radius is drawn as {@code sqrt(random.nextDouble())}, not
	 * {@code random.nextDouble()} directly: since area grows with the
	 * square of radius, sampling the radius itself uniformly would bunch
	 * points too densely near the center (or the annulus's inner edge) -
	 * the square root correction makes the resulting point density uniform
	 * per unit <em>area</em>, not per unit radius.
	 */
	private static TrainingSample annulusSample(final Random random, final double noise, final double label,
			final double minRadius, final double maxRadius) {
		double angle = random.nextDouble() * 2 * Math.PI;
		double radius = minRadius + Math.sqrt(random.nextDouble()) * (maxRadius - minRadius);
		double x = CENTER_X + radius * Math.cos(angle) + random.nextGaussian() * noise;
		double y = CENTER_Y + radius * Math.sin(angle) + random.nextGaussian() * noise;
		return new TrainingSample(new double[] { x, y }, new double[] { label });
	}
}
