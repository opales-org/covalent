/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.impl;

import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.UserConfiguration;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.UserConfigurationInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.UserConfigurationViewServices;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The configuration composite's Interaction Controller: registers one
 * {@code ActionManager} action per user-editable parameter (instead of
 * exposing plain public methods for an HMI to call directly, unlike
 * this demo's other Interaction Controllers), and applies each one directly to
 * whichever function's Parameters interface actually owns it:
 * {@link NetworkParameters} for the learning rate and hidden-layer
 * structure, {@link TrainingParameters} for batch size, dataset noise and
 * training pace.
 * <p>
 * The two {@code @AccessedParameters} fields below are this composite's
 * concrete illustration of its cross-cutting, non-owning role documented on
 * {@link UserConfiguration}: this single component reaches across two other
 * functions' boundaries purely through their public {@code @Parameters}
 * contracts, without needing to know anything else about
 * {@code NeuralNetworkModel} or {@code TrainingMonitor}. Note that, unlike
 * {@code HmiBuilder}'s read-only {@code *ViewParameters} accesses, this
 * component actually calls the parameters' setters - {@code @Parameters} is
 * a configuration contract, not an inherently read-only one, and here the
 * whole point of fronting these parameters is to let the user change them.
 */
@Implementation
public class UserConfigurationInteractionImpl implements UserConfigurationInteraction {

	@UsedServices
	private ActionProviderServices actionProviderServices;

	@UsedServices
	private UserConfigurationViewServices useUserConfigurationViewServices;

	@AccessedParameters
	private NetworkParameters accessNetworkParameters;

	@AccessedParameters
	private TrainingParameters accessTrainingParameters;

	/**
	 * Registers one {@code ActionProviderServices} handler per
	 * {@link UserConfigurationActionKey}, then pushes every parameter's
	 * current value (read from {@link NetworkParameters} /
	 * {@link TrainingParameters}) to {@link UserConfigurationViewServices}
	 * so the View's controls show the real, already-applied values as soon
	 * as they exist, rather than starting from arbitrary defaults.
	 */
	@Activation
	public void registerActions() {
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetLearningRate, this::setLearningRate, Double.class);
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetHiddenLayerCount, this::setHiddenLayerCount, Integer.class);
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetHiddenLayerSize, this::setHiddenLayerSize, Integer.class);
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetBatchSize, this::setBatchSize, Integer.class);
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetDatasetNoise, this::setDatasetNoise, Double.class);
		this.actionProviderServices.registerActionExecution(
				UserConfigurationActionKey.SetEpochsPerSecond, this::setEpochsPerSecond, Double.class);

		// Push the current values so the view has something to display right away.
		this.useUserConfigurationViewServices.displayLearningRate(this.accessNetworkParameters.getLearningRate());
		this.useUserConfigurationViewServices
				.displayHiddenLayerCount(this.accessNetworkParameters.getHiddenLayerCount());
		this.useUserConfigurationViewServices
				.displayHiddenLayerSize(this.accessNetworkParameters.getHiddenLayerSize());
		this.useUserConfigurationViewServices.displayBatchSize(this.accessTrainingParameters.getBatchSize());
		this.useUserConfigurationViewServices.displayDatasetNoise(this.accessTrainingParameters.getDatasetNoise());
		this.useUserConfigurationViewServices
				.displayEpochsPerSecond(this.accessTrainingParameters.getEpochsPerSecond());
	}

	/**
	 * Unregisters every action handler registered in {@link #registerActions()},
	 * mirroring it exactly - the standard {@code @Activation}/{@code @Passivation}
	 * pairing for {@code ActionProviderServices} registrations.
	 */
	@Passivation
	public void unregisterActions() {
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetLearningRate);
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetHiddenLayerCount);
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetHiddenLayerSize);
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetBatchSize);
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetDatasetNoise);
		this.actionProviderServices.unregisterActionExecution(UserConfigurationActionKey.SetEpochsPerSecond);
	}

	private void setLearningRate(final Double learningRate) {
		this.accessNetworkParameters.setLearningRate(learningRate);
		this.useUserConfigurationViewServices.displayLearningRate(learningRate);
	}

	private void setHiddenLayerCount(final Integer hiddenLayerCount) {
		this.accessNetworkParameters.setHiddenLayerCount(hiddenLayerCount);
		this.useUserConfigurationViewServices.displayHiddenLayerCount(hiddenLayerCount);
	}

	private void setHiddenLayerSize(final Integer hiddenLayerSize) {
		this.accessNetworkParameters.setHiddenLayerSize(hiddenLayerSize);
		this.useUserConfigurationViewServices.displayHiddenLayerSize(hiddenLayerSize);
	}

	private void setBatchSize(final Integer batchSize) {
		this.accessTrainingParameters.setBatchSize(batchSize);
		this.useUserConfigurationViewServices.displayBatchSize(batchSize);
	}

	private void setDatasetNoise(final Double noise) {
		this.accessTrainingParameters.setDatasetNoise(noise);
		this.useUserConfigurationViewServices.displayDatasetNoise(noise);
	}

	private void setEpochsPerSecond(final Double epochsPerSecond) {
		this.accessTrainingParameters.setEpochsPerSecond(epochsPerSecond);
		this.useUserConfigurationViewServices.displayEpochsPerSecond(epochsPerSecond);
	}
}
