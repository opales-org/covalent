/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetGenerator;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;

/** The dataset generators honor their size contract, including on the smallest sizes. */
public class GeneratorsTest {

	private static DatasetGenerator[] generators() {
		return new DatasetGenerator[] { new TwoMoonsGenerator(), new RingGenerator(), new CrossGenerator(),
				new StarGenerator(), new SpiralGenerator(), new CheckerboardGenerator() };
	}

	@Test
	public void everyGeneratorReturnsTwiceTheRequestedCountOfFiniteSamples() {
		for (DatasetGenerator generator : generators()) {
			for (int samplesPerClass : new int[] { 0, 1, 2, 50 }) {
				List<TrainingSample> samples = generator.generate(new Random(1L), 0.1, samplesPerClass);
				assertEquals(generator.getClass().getSimpleName() + " with " + samplesPerClass,
						2 * samplesPerClass, samples.size());
				for (TrainingSample sample : samples) {
					for (double coordinate : sample.input()) {
						assertFalse(generator.getClass().getSimpleName() + " produced " + coordinate,
								Double.isNaN(coordinate) || Double.isInfinite(coordinate));
					}
				}
			}
		}
	}

	@Test
	public void everyGeneratorExceptTheCheckerboardBalancesTheLabels() {
		for (DatasetGenerator generator : generators()) {
			if (generator instanceof CheckerboardGenerator) {
				continue;
			}
			List<TrainingSample> samples = generator.generate(new Random(2L), 0.0, 40);
			long ones = samples.stream().filter(s -> s.label()[0] == 1.0).count();
			assertEquals(generator.getClass().getSimpleName(), 40L, ones);
		}
	}
}
