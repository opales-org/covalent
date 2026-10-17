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
 * A {@value #GRID_SIZE}x{@value #GRID_SIZE} checkerboard: a grid of square
 * cells over a bounding rectangle, labeled by the parity of
 * {@code column + row} - a generalized XOR, and the hardest of the six
 * {@link DatasetShape}s. Where {@link SpiralGenerator}'s boundary is one
 * continuous (if demanding) curve, the checkerboard's same-label regions
 * are <em>disconnected</em>: {@value #GRID_SIZE}x{@value #GRID_SIZE}
 * separate cells alternating label, none touching another same-label cell
 * except diagonally. A network needs enough hidden capacity to carve out
 * every one of those cells independently, which is exactly the kind of
 * memorization-prone capacity most likely to let a demo-sized network
 * overfit visibly on this shape while it could not on the smoother ones.
 */
public final class CheckerboardGenerator implements DatasetGenerator {

	private static final double CENTER_X = 0.5;
	private static final double CENTER_Y = 0.25;
	private static final double HALF_WIDTH = 1.0;
	private static final double HALF_HEIGHT = 0.9;
	private static final int GRID_SIZE = 4;

	@Override
	public List<TrainingSample> generate(final Random random, final double noise, final int samplesPerClass) {
		int total = samplesPerClass * 2;
		List<TrainingSample> all = new ArrayList<>(total);
		double cellWidth = (2 * HALF_WIDTH) / GRID_SIZE;
		double cellHeight = (2 * HALF_HEIGHT) / GRID_SIZE;

		// Sampled as one uniform pass over the whole bounding rectangle,
		// then labeled by which cell each point lands in - unlike the
		// other generators there is no separate "per label" loop, since a
		// cell's label is a function of position, not something chosen in
		// advance. This gives close to, but not exactly, samplesPerClass
		// points per label - fine for a demo dataset.
		for (int i = 0; i < total; i++) {
			double x = CENTER_X + (random.nextDouble() * 2 - 1) * HALF_WIDTH;
			double y = CENTER_Y + (random.nextDouble() * 2 - 1) * HALF_HEIGHT;
			int col = (int) Math.floor((x - (CENTER_X - HALF_WIDTH)) / cellWidth);
			int row = (int) Math.floor((y - (CENTER_Y - HALF_HEIGHT)) / cellHeight);
			double label = (col + row) % 2 == 0 ? 0.0 : 1.0;
			all.add(new TrainingSample(
					new double[] { x + random.nextGaussian() * noise, y + random.nextGaussian() * noise },
					new double[] { label }));
		}

		return all;
	}
}
