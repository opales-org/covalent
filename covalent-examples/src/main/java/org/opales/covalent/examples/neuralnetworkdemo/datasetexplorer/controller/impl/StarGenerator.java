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
 * A non-convex five-pointed star polygon (label {@code 1.0}: inside the
 * star) against its complement within a bounding square (label
 * {@code 0.0}). Deliberately a star rather than a simpler convex polygon
 * (e.g. a triangle): a convex polygon's inside is just the intersection of
 * a handful of half-planes, each one directly representable by a single
 * hidden unit's linear decision boundary - genuinely easy for this network.
 * A star's points and inward valleys make it non-convex, so no small set of
 * straight cuts carves out its inside without also cutting into it -
 * meaningfully harder, and the next step up the {@link DatasetShape}
 * ladder after {@link CrossGenerator}'s four corners.
 */
public final class StarGenerator implements DatasetGenerator {

	private static final double CENTER_X = 0.5;
	private static final double CENTER_Y = 0.25;
	private static final double OUTER_RADIUS = 0.95;
	private static final double INNER_RADIUS = 0.4;
	private static final int STAR_POINTS = 5;

	// The bounding square rejection sampling draws from must be big enough
	// to fully contain the star (OUTER_RADIUS) with some margin around it,
	// so the "outside" class also has room to be sampled from nearby the
	// star's points, not just far away.
	private static final double BOUNDING_RADIUS = OUTER_RADIUS * 1.15;
	private static final int MAX_ATTEMPTS_PER_SAMPLE = 500;

	@Override
	public List<TrainingSample> generate(final Random random, final double noise, final int samplesPerClass) {
		List<TrainingSample> all = new ArrayList<>(samplesPerClass * 2);
		all.addAll(samplePoints(random, noise, samplesPerClass, 1.0, true));
		all.addAll(samplePoints(random, noise, samplesPerClass, 0.0, false));
		return all;
	}

	private static List<TrainingSample> samplePoints(final Random random, final double noise, final int count,
			final double label, final boolean insideStar) {
		List<TrainingSample> samples = new ArrayList<>(count);
		int attempts = 0;
		int maxAttempts = count * MAX_ATTEMPTS_PER_SAMPLE;
		while (samples.size() < count && attempts < maxAttempts) {
			attempts++;
			double x = CENTER_X + (random.nextDouble() * 2 - 1) * BOUNDING_RADIUS;
			double y = CENTER_Y + (random.nextDouble() * 2 - 1) * BOUNDING_RADIUS;
			if (isInsideStar(x, y) == insideStar) {
				samples.add(new TrainingSample(
						new double[] { x + random.nextGaussian() * noise, y + random.nextGaussian() * noise },
						new double[] { label }));
			}
		}
		return samples;
	}

	/**
	 * A point is inside the star iff its distance from the center does not
	 * exceed the star boundary's radius at that same angle, where the
	 * boundary radius is linearly interpolated (in polar coordinates)
	 * between {@link #STAR_POINTS} outer vertices at {@link #OUTER_RADIUS}
	 * and {@link #STAR_POINTS} inner vertices (the valleys between points)
	 * at {@link #INNER_RADIUS}, alternating every {@code 360 / (2 *
	 * STAR_POINTS)} degrees.
	 */
	private static boolean isInsideStar(final double x, final double y) {
		double dx = x - CENTER_X;
		double dy = y - CENTER_Y;
		double r = Math.sqrt(dx * dx + dy * dy);
		double theta = Math.atan2(dy, dx);
		if (theta < 0) {
			theta += 2 * Math.PI;
		}
		double period = 2 * Math.PI / (STAR_POINTS * 2);
		int k = (int) (theta / period);
		double frac = (theta - k * period) / period;
		double r0 = (k % 2 == 0) ? OUTER_RADIUS : INNER_RADIUS;
		double r1 = ((k + 1) % 2 == 0) ? OUTER_RADIUS : INNER_RADIUS;
		double boundary = r0 + (r1 - r0) * frac;
		return r <= boundary;
	}
}
