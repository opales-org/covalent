/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.controller.impl;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.opales.covalent.examples.rubikscubesolver.cube.CubeState;
import org.opales.covalent.examples.rubikscubesolver.cube.Face;
import org.opales.covalent.examples.rubikscubesolver.cube.Move;
import org.opales.covalent.examples.rubikscubesolver.cube.RotationDirection;
import org.opales.covalent.examples.rubikscubesolver.cube.model.CubeModelServices;

/** Moves made from several threads are all applied, none is lost. */
public class CubeControllerImplTest {

	@Test
	public void concurrentMovesAreNotLost() throws Exception {
		AtomicReference<CubeState> state = new AtomicReference<>(new CubeState());
		CubeModelServices model = (CubeModelServices) Proxy.newProxyInstance(
				CubeModelServices.class.getClassLoader(), new Class<?>[] { CubeModelServices.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getState")) {
						return state.get();
					}
					if (method.getName().equals("setState")) {
						state.set((CubeState) args[0]);
					}
					return null;
				});
		CubeControllerImpl controller = new CubeControllerImpl();
		for (Field field : CubeControllerImpl.class.getDeclaredFields()) {
			if (field.getType() == CubeModelServices.class) {
				field.setAccessible(true);
				field.set(controller, model);
			}
		}

		// Four quarter turns bring the cube back to solved: 4 threads x 1000 turns is a multiple
		// of four only if no turn is lost.
		Move move = new Move(Face.UP, RotationDirection.CLOCKWISE);
		Thread[] threads = new Thread[4];
		for (int t = 0; t < threads.length; t++) {
			threads[t] = new Thread(() -> {
				for (int i = 0; i < 1000; i++) {
					controller.applyMove(move);
				}
			});
			threads[t].start();
		}
		for (Thread thread : threads) {
			thread.join();
		}
		assertTrue("a move was lost", state.get().isSolved());
	}
}
