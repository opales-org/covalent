/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl.DatasetControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.impl.DatasetViewImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.OptimizerStrategy;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl.GradientDescentOptimizerImpl;

import java.util.List;
import java.util.Random;

/**
 * A strategy object that generates one {@link DatasetShape}'s raw points -
 * plain {@code @Item}s with no component lifecycle of their own, exactly
 * like {@code OptimizerStrategy}'s {@code GradientDescentOptimizerImpl}:
 * interchangeable algorithms selected by {@code DatasetControllerImpl}'s
 * shape-to-generator mapping, never {@code @Component}s in their own right.
 * <p>
 * Implementations do not perform the train/test split themselves - that
 * stays centralized in {@code DatasetControllerImpl.splitRandomly(List,
 * int, Random)}, applied uniformly to whatever a generator returns, so every
 * shape gets the same fair, random split - only the points and their labels
 * are shape-specific.
 * <p>
 * {@code random} is passed in already seeded (by the caller, from the
 * regeneration's seed) rather than each implementation seeding its own: the
 * same {@link Random} instance is reused afterward for
 * {@code splitRandomly}, so a generator that draws more or fewer random
 * values than another still produces a reproducible, seed-determined split -
 * whatever this method consumes from {@code random}, the split continues
 * from exactly where generation left off.
 */
public interface DatasetGenerator {

	/**
	 * Generates the full (unsplit) dataset for this shape: exactly
	 * {@code 2 * samplesPerClass} samples in total, {@code samplesPerClass}
	 * labeled {@code 0.0} and {@code samplesPerClass} labeled {@code 1.0}
	 * (implementations are free to interleave or order these however suits
	 * the shape - {@code DatasetViewImpl}'s point-cloud rendering does not
	 * depend on ordering). {@code CheckerboardGenerator} is the one exception:
	 * it returns {@code 2 * samplesPerClass} samples in total, but a label's share is
	 * only close to {@code samplesPerClass}, since it follows from where each point lands.
	 *
	 * @param random         a seeded generator to draw all randomness from
	 * @param noise          the standard deviation of the Gaussian coordinate
	 *                       jitter applied to each point (0 = a perfectly
	 *                       clean, noiseless shape)
	 * @param samplesPerClass the number of samples to generate per label
	 * @return the generated samples, unsplit
	 */
	List<TrainingSample> generate(Random random, double noise, int samplesPerClass);
}
