/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.OptimizerParameters;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.OptimizerStrategy;

import org.opales.covalent.container.component.annotations.Item;

/**
 * Internal item of {@code NetworkController}: offers {@link OptimizerParameters}
 * to configure which {@link OptimizerStrategy} is active, and is itself the
 * default strategy (plain gradient descent: {@code value - learningRate * gradient})
 * used until a different one is set.
 * <p>
 * {@code @Item} marks an internal, non-component-type sub-object: a plain
 * Java object constructed and owned directly by an {@code @Implementation}
 * class - here, {@link NetworkControllerImpl}, through its
 * {@code @Item}-annotated field - for internal decomposition that does not
 * need its own component identity, lifecycle callbacks visible to the outside,
 * or a deployment entry of its own. Items are instantiated (or adopted, if
 * the field is already set) together with their owning component, follow
 * that component's lifecycle as a passenger rather than as an independent
 * lifecycle root, and are never passed to
 * {@code CovalentContainer.deployComponents(...)} - there is no
 * {@code @Component} type naming this class at all.
 * <p>
 * This is the right choice here because the optimizer is purely an
 * implementation detail of how {@code NetworkController} updates weights:
 * nothing outside this composite ever needs to address "the optimizer" as
 * its own addressable unit, observe its own activation/passivation, or
 * locate it in deployment configuration - {@link OptimizerParameters#setOptimizerStrategy}
 * is reached through the owning {@code NetworkController}'s own ports, not
 * through a separate component identity. Contrast this with
 * {@code @ComposedComponent} (used elsewhere in this codebase, e.g.
 * {@code HmiCoreImpl} composing {@code ActionManagerImpl}): a composed
 * component keeps its own full component identity and lifecycle, which
 * would be unwarranted machinery for a small, owner-only strategy object
 * like this one.
 * <p>
 * {@link #getLearningRate()} / {@link #setLearningRate(double)} are plain
 * public methods (not part of any Covalent port): {@code NetworkController}
 * owns this item as a direct Java field and calls them straight through, to
 * implement its own {@link NetworkParameters} port.
 */
@Item
public class GradientDescentOptimizerImpl implements OptimizerStrategy, OptimizerParameters {

	private static final double DEFAULT_LEARNING_RATE = 0.1;

	private volatile double learningRate = DEFAULT_LEARNING_RATE;

	private volatile OptimizerStrategy activeStrategy = this;

	@Override
	public void setOptimizerStrategy(final OptimizerStrategy strategy) {
		this.activeStrategy = strategy != null ? strategy : this;
	}

	@Override
	public double[] computeUpdate(final String parameterId, final double[] gradient, final double[] currentValues) {
		if (this.activeStrategy != this) {
			return this.activeStrategy.computeUpdate(parameterId, gradient, currentValues);
		}
		double[] updated = new double[currentValues.length];
		for (int i = 0; i < currentValues.length; i++) {
			updated[i] = currentValues[i] - this.learningRate * gradient[i];
		}
		return updated;
	}

	/**
	 * Returns the learning rate of the plain gradient-descent update.
	 *
	 * @return this item's own learning rate (used while it is the active strategy)
	 */
	public double getLearningRate() {
		return this.learningRate;
	}

	/**
	 * Sets the learning rate of the plain gradient-descent update.
	 *
	 * @param learningRate the new learning rate to use from now on
	 */
	public void setLearningRate(final double learningRate) {
		this.learningRate = learningRate;
	}
}
