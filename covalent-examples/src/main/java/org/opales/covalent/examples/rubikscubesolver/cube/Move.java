/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

/**
 * One quarter or half turn of a single {@link Face}, in a chosen
 * {@link RotationDirection}. This is the cube's whole move vocabulary: a
 * solving algorithm's result is an array of these, applied in order (see
 * {@code CubeServices#applyMove(Move)}), and a manual rotation from the
 * view goes through the same shape (see {@code CubeControllerServices#rotateFace}).
 *
 * @param face      the face being turned
 * @param direction which way, and how far, it is turned
 */
public record Move(Face face, RotationDirection direction) {
}
