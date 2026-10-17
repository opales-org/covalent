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
 * A "+"-shaped region (label {@code 1.0}: the union of a horizontal and a
 * vertical bar through the shape's center) against its complement within a
 * bounding square (label {@code 0.0}). Unlike {@link TwoMoonsGenerator} and
 * {@link RingGenerator}'s single smooth curvature, the cross's boundary has
 * four sharp inward corners (where the two bars' edges meet) - the first
 * shape on the {@link DatasetShape} ladder that needs the boundary to bend
 * sharply rather than just curve, which a network approximates by combining
 * several straight hidden-unit decision lines rather than one smooth one.
 */
public final class CrossGenerator implements DatasetGenerator {

	private static final double CENTER_X = 0.5;
	private static final double CENTER_Y = 0.25;
	private static final double HALF_EXTENT = 1.0;
	private static final double ARM_HALF_WIDTH = 0.28;

	// Rejection sampling (draw a random point in the bounding square, keep
	// it only if it falls on the wanted side of the boundary) is simple and
	// exact for a region this cheap to test membership in; the attempt cap
	// just guards against a degenerate parameter choice looping forever.
	private static final int MAX_ATTEMPTS_PER_SAMPLE = 500;

	@Override
	public List<TrainingSample> generate(final Random random, final double noise, final int samplesPerClass) {
		List<TrainingSample> all = new ArrayList<>(samplesPerClass * 2);
		all.addAll(samplePoints(random, noise, samplesPerClass, 1.0, true));
		all.addAll(samplePoints(random, noise, samplesPerClass, 0.0, false));
		return all;
	}

	private static List<TrainingSample> samplePoints(final Random random, final double noise, final int count,
			final double label, final boolean insideCross) {
		List<TrainingSample> samples = new ArrayList<>(count);
		int attempts = 0;
		int maxAttempts = count * MAX_ATTEMPTS_PER_SAMPLE;
		while (samples.size() < count && attempts < maxAttempts) {
			attempts++;
			double x = CENTER_X + (random.nextDouble() * 2 - 1) * HALF_EXTENT;
			double y = CENTER_Y + (random.nextDouble() * 2 - 1) * HALF_EXTENT;
			boolean inside = Math.abs(x - CENTER_X) <= ARM_HALF_WIDTH || Math.abs(y - CENTER_Y) <= ARM_HALF_WIDTH;
			if (inside == insideCross) {
				samples.add(new TrainingSample(
						new double[] { x + random.nextGaussian() * noise, y + random.nextGaussian() * noise },
						new double[] { label }));
			}
		}
		return samples;
	}
}
