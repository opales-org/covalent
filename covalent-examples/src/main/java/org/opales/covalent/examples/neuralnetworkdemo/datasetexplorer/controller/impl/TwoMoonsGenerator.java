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
 * The classic "two interleaving moons" toy binary classification problem:
 * two half-circle arcs, one shifted and flipped so it interleaves with the
 * other, each perturbed by Gaussian noise. A smooth, single-curvature
 * boundary - the easiest of the six {@link DatasetShape}s.
 */
public final class TwoMoonsGenerator implements DatasetGenerator {

	@Override
	public List<TrainingSample> generate(final Random random, final double noise, final int samplesPerClass) {
		List<TrainingSample> all = new ArrayList<>(samplesPerClass * 2);
		// A single sample per class sits at angle 0, instead of dividing zero by zero.
		int steps = Math.max(1, samplesPerClass - 1);

		for (int i = 0; i < samplesPerClass; i++) {
			double angle = Math.PI * i / steps;
			double x = Math.cos(angle) + random.nextGaussian() * noise;
			double y = Math.sin(angle) + random.nextGaussian() * noise;
			all.add(new TrainingSample(new double[] { x, y }, new double[] { 0.0 }));
		}
		for (int i = 0; i < samplesPerClass; i++) {
			double angle = Math.PI * i / steps;
			double x = 1.0 - Math.cos(angle) + random.nextGaussian() * noise;
			double y = 0.5 - Math.sin(angle) + random.nextGaussian() * noise;
			all.add(new TrainingSample(new double[] { x, y }, new double[] { 1.0 }));
		}

		return all;
	}
}
