/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.controller.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeReadServices;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeServices;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;
import org.opales.covalent.examples.rubikscubesolver.cube.controller.CubeController;
import org.opales.covalent.examples.rubikscubesolver.cube.controller.CubeControllerServices;
import org.opales.covalent.examples.rubikscubesolver.cube.model.CubeModelServices;

import java.util.Random;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * The cube's Controller: the one place moves are actually applied to the
 * cube's state, and the provider of both this function's internal
 * {@link CubeControllerServices} and its public {@link CubeServices}/
 * {@link CubeReadServices} - see {@link CubeController}'s javadoc for why
 * those two public ports live here rather than on {@code CubeModel}, and
 * {@code DatasetControllerImpl} (Neural Network Trainer demo) for why one
 * implementation class is free to provide several Services contracts from
 * the same underlying state without those contracts needing to know about
 * each other.
 * <p>
 * Every mutating entry point ({@link #applyMove(Move)},
 * {@link #rotateFace(Face, RotationDirection)}, {@link #scramble(long)},
 * {@link #resetToSolved()}) builds a new state and pushes it through
 * {@link CubeModelServices#setState(CubeState)}, so the Model's own state
 * replacement, and the resulting {@code CubeEvents#stateChanged} firing,
 * happen in one place. A move is applied to a clone of the current state
 * (never to the live state directly, since
 * {@code CubeModelServices#getState()} hands out the instance
 * {@code CubeModel} still holds), while a scramble and a reset start from
 * a fresh solved {@link CubeState}. A state is therefore never modified once
 * published: every change replaces the Model's reference.
 */
@Implementation
public class CubeControllerImpl implements CubeController, CubeControllerServices, CubeServices {

	@UsedServices
	private CubeModelServices useCubeModelServices;

	/**
	 * Makes {@link #applyMove(Move)} atomic: the solver's thread and the user's thread can both
	 * move the cube, and two moves each read the same state would lose one of them.
	 */
	private final Object moveLock = new Object();

	@Override
	public void rotateFace(final Face face, final RotationDirection direction) {
		this.applyMove(new Move(face, direction));
	}

	@Override
	public void applyMove(final Move move) {
		synchronized (this.moveLock) {
			CubeState next = this.useCubeModelServices.getState().clone();
			next.applyMove(move);
			this.useCubeModelServices.setState(next);
		}
	}

	@Override
	public void scramble(final long seed) {
		Random random = new Random(seed);
		Face[] faces = Face.values();
		RotationDirection[] directions = RotationDirection.values();
		CubeState next = new CubeState();
		// A fresh solved state scrambled from scratch, rather than the
		// current state scrambled further: repeated "Scramble" clicks
		// should always reach a comparably shuffled cube, not compound
		// indefinitely with whatever state came before.
		int scrambleMoveCount = 25;
		for (int i = 0; i < scrambleMoveCount; i++) {
			Face face = faces[random.nextInt(faces.length)];
			RotationDirection direction = directions[random.nextInt(directions.length)];
			next.applyMove(new Move(face, direction));
		}
		this.useCubeModelServices.setState(next);
	}

	@Override
	public void resetToSolved() {
		// A fresh CubeState() is already solved, per its own constructor:
		// reusing that is the single source of truth for what "solved"
		// means, rather than hand-building a solved state a second way.
		this.useCubeModelServices.setState(new CubeState());
	}

	@Override
	public CubeState getState() {
		return this.useCubeModelServices.getState();
	}

	@Override
	public boolean isSolved() {
		return this.useCubeModelServices.getState().isSolved();
	}
}
