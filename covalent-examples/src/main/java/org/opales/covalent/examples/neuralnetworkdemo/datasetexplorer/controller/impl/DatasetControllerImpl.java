/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetControllerServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetGenerator;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetShape;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.DatasetModel;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.DatasetModelServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Initialization;

/**
 * The dataset's Controller: generates a toy 2D classification dataset -
 * one of six {@link DatasetShape}s, each produced by its own
 * {@link DatasetGenerator} strategy (see {@link #GENERATORS}) - and exposes
 * it both internally ({@link DatasetControllerServices}) and publicly
 * ({@link DatasetServices}).
 * <p>
 * {@code @Implementation} marks the concrete class that fulfills a
 * {@code @Component} type (here {@link DatasetController}) - the actual
 * deployable unit the container instantiates, wires and runs through the
 * component lifecycle. A single implementation class is free to fulfill
 * several component/services/events contracts at once, as this one does:
 * it is the {@code DatasetController} component, and it provides both
 * {@code DatasetControllerServices} (internal use) and {@code DatasetServices}
 * (public use) from the same underlying state and logic, without those two
 * contracts having to know about each other.
 * <p>
 * Every generation also draws a held-out test split (see
 * {@link #splitRandomly(List, int, Random)}): one sample in
 * {@value #TEST_EVERY_NTH}, drawn uniformly at random across the whole
 * dataset (not by position along the shape's boundary - see
 * {@value #TEST_EVERY_NTH}'s javadoc for why that mattered), is set aside
 * as test data and never appears in {@link #getBatch(int)} or
 * {@link #getTrainSamples()} - see {@link DatasetServices} for why. This
 * split is centralized here, applied uniformly to whatever any
 * {@link DatasetGenerator} returns, rather than duplicated per shape.
 */
@Implementation
public class DatasetControllerImpl implements DatasetController, DatasetControllerServices, DatasetServices {

	/** Default random seed used at startup, before any explicit regeneration. */
	private static final long DEFAULT_SEED = 0L;

	/** Default class-overlap noise level used at startup. */
	private static final double DEFAULT_NOISE = 0.15;

	/** Default shape generated at startup, before any explicit shape selection. */
	private static final DatasetShape DEFAULT_SHAPE = DatasetShape.TWO_MOONS;

	/** Number of samples generated per label (so the dataset has twice this many samples). */
	private static final int SAMPLES_PER_CLASS = 150;

	/**
	 * Held-out fraction of the dataset set aside for testing, expressed as
	 * "1 sample in N" - an 80/20 split. Which samples land in the test
	 * subset is decided by shuffling the whole dataset with the same
	 * seeded {@link Random} used to generate it (see
	 * {@link #splitRandomly(List, int, Random)}), then taking every
	 * {@value #TEST_EVERY_NTH}th one from the shuffled order - reproducible
	 * per seed, but not tied to each point's position within its shape.
	 * <p>
	 * A random split is used rather than a positional one (every {@value #TEST_EVERY_NTH}th sample along a
	 * shape's arc): a positional split is evenly spread geometrically but not a fair sample of how
	 * <em>hard</em> a point is to classify. With a fixed noise level, a few points always land in the
	 * region where the classes overlap, and a deterministic split can put every one of them on the train
	 * side, letting test loss/accuracy look better than train's for reasons unrelated to the network.
	 * Shuffling distributes that difficulty fairly between train and test, for every shape.
	 */
	private static final int TEST_EVERY_NTH = 5;

	/**
	 * One {@link DatasetGenerator} per {@link DatasetShape}, keyed by shape -
	 * the strategy-object mapping {@link #regenerate(long, double)} and
	 * {@link #selectShape(DatasetShape)} dispatch through. Adding a new
	 * shape means adding one {@link DatasetGenerator} implementation and one
	 * entry here; nothing else in this class (the split, the batching, the
	 * public/internal services) needs to know a new shape exists.
	 */
	private static final Map<DatasetShape, DatasetGenerator> GENERATORS = buildGenerators();

	private static Map<DatasetShape, DatasetGenerator> buildGenerators() {
		Map<DatasetShape, DatasetGenerator> generators = new EnumMap<>(DatasetShape.class);
		generators.put(DatasetShape.TWO_MOONS, new TwoMoonsGenerator());
		generators.put(DatasetShape.RING, new RingGenerator());
		generators.put(DatasetShape.CROSS, new CrossGenerator());
		generators.put(DatasetShape.STAR, new StarGenerator());
		generators.put(DatasetShape.SPIRAL, new SpiralGenerator());
		generators.put(DatasetShape.CHECKERBOARD, new CheckerboardGenerator());
		return Collections.unmodifiableMap(generators);
	}

	/**
	 * {@code @UsedServices} injects a live proxy to whatever component
	 * provides {@link DatasetModelServices} (here, {@code DatasetModel}) -
	 * a synchronous, call-and-return contract: calling a method on this
	 * field blocks until the Model has handled it and returns its result,
	 * unlike the fire-and-forget {@code @Events} contracts used elsewhere
	 * in this function.
	 */
	@UsedServices
	private DatasetModelServices useDatasetModelServices;

	private final Random batchRandom = new Random();

	/** The shape currently selected; see {@link #selectShape(DatasetShape)}. */
	private volatile DatasetShape currentShape = DEFAULT_SHAPE;

	/** The noise level last generated with; reused by {@link #selectShape(DatasetShape)}. */
	private volatile double lastNoise = DEFAULT_NOISE;

	/**
	 * {@code @Initialization} runs once, early, when the component is first
	 * set up - before activation, while dependencies may not yet be fully
	 * wired for live interaction - and is typically used to seed initial
	 * state, as it is here: it produces the first dataset so the view has
	 * something to render as soon as it activates.
	 */
	@Initialization
	public void generateInitialDataset() {
		this.regenerate(DEFAULT_SEED, DEFAULT_NOISE);
	}

	@Override
	public void regenerate(final long seed, final double noise) {
		this.lastNoise = noise;
		Random random = new Random(seed);
		DatasetGenerator generator = GENERATORS.get(this.currentShape);
		List<TrainingSample> all = generator.generate(random, noise, SAMPLES_PER_CLASS);
		DatasetSplit split = splitRandomly(all, TEST_EVERY_NTH, random);
		this.useDatasetModelServices.replaceSamples(split.all());
		this.useDatasetModelServices.replaceTrainSamples(split.train());
		this.useDatasetModelServices.replaceTestSamples(split.test());
	}

	@Override
	public void selectShape(final DatasetShape shape) {
		this.currentShape = shape;
		// A shape change is a full regeneration, at a fresh seed (so the
		// newly selected shape isn't stuck rendering the same points every
		// time it's picked) but the same noise level as the last explicit
		// regeneration - mirroring what the "Regenerate dataset" button
		// does when clicked, just triggered from the shape selector instead.
		this.regenerate(System.nanoTime(), this.lastNoise);
	}

	@Override
	public DatasetShape getShape() {
		return this.currentShape;
	}

	@Override
	public TrainingSample[] getBatch(final int batchSize) {
		TrainingSample[] train = this.useDatasetModelServices.getTrainSamples();
		if (train.length == 0 || batchSize <= 0) {
			return new TrainingSample[0];
		}
		TrainingSample[] batch = new TrainingSample[Math.min(batchSize, train.length)];
		for (int i = 0; i < batch.length; i++) {
			batch[i] = train[this.batchRandom.nextInt(train.length)];
		}
		return batch;
	}

	@Override
	public TrainingSample[] getTrainSamples() {
		return this.useDatasetModelServices.getTrainSamples();
	}

	@Override
	public TrainingSample[] getTestSamples() {
		return this.useDatasetModelServices.getTestSamples();
	}

	/**
	 * Splits {@code all} into train/test by shuffling a copy of it with
	 * {@code random} (the same seeded generator a {@link DatasetGenerator}
	 * just drew its points from, continued rather than re-seeded, so the
	 * split is reproducible per seed without needing a separate seed
	 * parameter) and taking every {@code testEveryNth}th sample from the
	 * shuffled order for the test subset - see {@value #TEST_EVERY_NTH}'s
	 * javadoc for why a random split matters here, rather than one based on
	 * each sample's position in {@code all}.
	 *
	 * @param all          every generated sample, in its original (unshuffled)
	 *                     order - not mutated
	 * @param testEveryNth hold out one sample in this many for the test subset
	 * @param random       the seeded generator to shuffle with
	 * @return the full/train/test split; {@code all} is returned exactly as
	 *         given, only train/test membership is randomized
	 */
	private static DatasetSplit splitRandomly(final List<TrainingSample> all, final int testEveryNth,
			final Random random) {
		List<TrainingSample> shuffled = new ArrayList<>(all);
		Collections.shuffle(shuffled, random);
		List<TrainingSample> train = new ArrayList<>(all.size());
		List<TrainingSample> test = new ArrayList<>(all.size() / testEveryNth + 2);
		for (int i = 0; i < shuffled.size(); i++) {
			if (i % testEveryNth == 0) {
				test.add(shuffled.get(i));
			} else {
				train.add(shuffled.get(i));
			}
		}
		return new DatasetSplit(all.toArray(new TrainingSample[0]), train.toArray(new TrainingSample[0]),
				test.toArray(new TrainingSample[0]));
	}

	/**
	 * One generation's full output, already split.
	 *
	 * @param all   every generated sample (what {@code DatasetView} renders)
	 * @param train the training subset ({@link #getBatch(int)}'s source)
	 * @param test  the held-out test subset, never trained on
	 */
	private record DatasetSplit(TrainingSample[] all, TrainingSample[] train, TrainingSample[] test) {
	}
}
