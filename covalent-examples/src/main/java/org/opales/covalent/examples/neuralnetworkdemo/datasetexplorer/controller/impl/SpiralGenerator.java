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
 * Two interleaved Archimedean spiral arms (label {@code 0.0} and label
 * {@code 1.0}, offset by half a turn), each winding {@value #TURNS} times
 * around a shared center. Unlike {@link CrossGenerator} and
 * {@link StarGenerator}'s handful of sharp corners, a spiral's boundary
 * curves continuously but changes direction rapidly and repeatedly as it
 * winds - a high-frequency boundary, needing many hidden units just to
 * follow it around, rather than a few to place corners. One step below
 * {@link CheckerboardGenerator} on the {@link DatasetShape} ladder: still a
 * single connected boundary per arm (not disconnected regions), but a
 * demanding one.
 */
public final class SpiralGenerator implements DatasetGenerator {

	private static final double CENTER_X = 0.5;
	private static final double CENTER_Y = 0.25;
	private static final double MAX_RADIUS = 0.95;
	private static final double TURNS = 2.0;

	@Override
	public List<TrainingSample> generate(final Random random, final double noise, final int samplesPerClass) {
		List<TrainingSample> all = new ArrayList<>(samplesPerClass * 2);
		addArm(all, random, noise, samplesPerClass, 0.0, 0.0);
		addArm(all, random, noise, samplesPerClass, 1.0, Math.PI);
		return all;
	}

	private static void addArm(final List<TrainingSample> all, final Random random, final double noise,
			final int count, final double label, final double angleOffset) {
		for (int i = 0; i < count; i++) {
			double t = (count <= 1) ? 0.0 : (double) i / (count - 1);
			double radius = t * MAX_RADIUS;
			double angle = t * TURNS * 2 * Math.PI + angleOffset;
			double x = CENTER_X + radius * Math.cos(angle) + random.nextGaussian() * noise;
			double y = CENTER_Y + radius * Math.sin(angle) + random.nextGaussian() * noise;
			all.add(new TrainingSample(new double[] { x, y }, new double[] { label }));
		}
	}
}
