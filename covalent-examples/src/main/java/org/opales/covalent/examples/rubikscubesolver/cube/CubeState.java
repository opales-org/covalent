/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * The 54 facelet colors of a Rubik's cube (6 faces of 9 facelets each),
 * plus the move-application logic that permutes them.
 * <p>
 * Internally, every facelet is treated as a point on the surface of a 3x3x3
 * grid of unit cubies, at integer coordinates in {@code -1, 0, 1} on each
 * axis (x: {@link Face#LEFT}/{@link Face#RIGHT}, y: {@link Face#DOWN}/
 * {@link Face#UP}, z: {@link Face#BACK}/{@link Face#FRONT}), together with
 * the outward-facing direction it points in. A face's own 3x3 grid of
 * facelets (row 0..2 top to bottom, column 0..2 left to right, as seen by
 * someone looking directly at that face from outside the cube) maps onto
 * these coordinates through {@link #NORMAL}, {@link #RIGHT_DIRECTION} and
 * {@link #UP_DIRECTION} (see {@link #position(Face, int, int)}).
 * <p>
 * {@link #applyMove(Move)} rotates every facelet belonging to the turned
 * layer (every facelet whose position has the turned face's fixed
 * coordinate) by a physical 90/180 degree rotation of both its position and
 * its facing direction around that face's own outward normal, then reads
 * off which (face, row, column) the rotated position/facing now correspond
 * to. This is plain rigid-body rotation, applied per facelet: it needs no
 * hand-maintained table of which strips of three facelets cycle into which
 * for each of the six faces, and is exactly as correct for a quarter turn
 * as for a half turn (two quarter turns) since it recomputes the rotation
 * from first principles each time.
 * <p>
 * Implements {@link Cloneable} with a real deep copy: {@code CubeEvents}'s
 * {@code stateChanged} snapshot argument is {@code @Cloned} (see that
 * interface's javadoc), which asks Covalent to clone this object with
 * {@link #clone()} before delivering it to each subscriber - the internal
 * facelet grid is mutated in place by {@link #applyMove(Move)}, so a
 * subscriber that kept a plain reference could alter the state the Model
 * holds. {@code CubeController} never mutates the live state itself: it
 * applies a move to a clone and replaces the Model's state with the result.
 */
public final class CubeState implements Cloneable {

	private static final int FACE_COUNT = Face.values().length;

	private static final int FACELETS_PER_FACE = 9;

	/** Each face's outward unit normal, as {@code {x, y, z}}. */
	private static final Map<Face, int[]> NORMAL = buildNormals();

	/** Each face's "viewer's right" direction, as {@code {x, y, z}}. */
	private static final Map<Face, int[]> RIGHT_DIRECTION = buildRightDirections();

	/** Each face's "viewer's up" direction, as {@code {x, y, z}}. */
	private static final Map<Face, int[]> UP_DIRECTION = buildUpDirections();

	/**
	 * The color a solved cube shows on each face, in the conventional
	 * Western color scheme: white opposite yellow, green opposite blue, red
	 * opposite orange.
	 */
	private static final Map<Face, CubeColor> SOLVED_COLOR = buildSolvedColors();

	private static Map<Face, int[]> buildNormals() {
		Map<Face, int[]> normals = new EnumMap<>(Face.class);
		normals.put(Face.FRONT, new int[] { 0, 0, 1 });
		normals.put(Face.BACK, new int[] { 0, 0, -1 });
		normals.put(Face.RIGHT, new int[] { 1, 0, 0 });
		normals.put(Face.LEFT, new int[] { -1, 0, 0 });
		normals.put(Face.UP, new int[] { 0, 1, 0 });
		normals.put(Face.DOWN, new int[] { 0, -1, 0 });
		return normals;
	}

	private static Map<Face, int[]> buildRightDirections() {
		Map<Face, int[]> rights = new EnumMap<>(Face.class);
		rights.put(Face.FRONT, new int[] { 1, 0, 0 });
		rights.put(Face.BACK, new int[] { -1, 0, 0 });
		rights.put(Face.RIGHT, new int[] { 0, 0, -1 });
		rights.put(Face.LEFT, new int[] { 0, 0, 1 });
		rights.put(Face.UP, new int[] { 1, 0, 0 });
		rights.put(Face.DOWN, new int[] { 1, 0, 0 });
		return rights;
	}

	private static Map<Face, int[]> buildUpDirections() {
		Map<Face, int[]> ups = new EnumMap<>(Face.class);
		ups.put(Face.FRONT, new int[] { 0, 1, 0 });
		ups.put(Face.BACK, new int[] { 0, 1, 0 });
		ups.put(Face.RIGHT, new int[] { 0, 1, 0 });
		ups.put(Face.LEFT, new int[] { 0, 1, 0 });
		ups.put(Face.UP, new int[] { 0, 0, -1 });
		ups.put(Face.DOWN, new int[] { 0, 0, 1 });
		return ups;
	}

	private static Map<Face, CubeColor> buildSolvedColors() {
		Map<Face, CubeColor> colors = new EnumMap<>(Face.class);
		colors.put(Face.UP, CubeColor.WHITE);
		colors.put(Face.DOWN, CubeColor.YELLOW);
		colors.put(Face.FRONT, CubeColor.GREEN);
		colors.put(Face.BACK, CubeColor.BLUE);
		colors.put(Face.RIGHT, CubeColor.RED);
		colors.put(Face.LEFT, CubeColor.ORANGE);
		return colors;
	}

	/**
	 * {@code facelets[face.ordinal()][row * 3 + col]} is the color at
	 * {@code (row, col)} on {@code face}, row and column 0..2 as seen by a
	 * viewer looking directly at that face from outside the cube.
	 */
	private final CubeColor[][] facelets;

	/** A freshly solved cube: every face showing a single uniform color. */
	public CubeState() {
		this.facelets = new CubeColor[FACE_COUNT][FACELETS_PER_FACE];
		for (Face face : Face.values()) {
			CubeColor color = SOLVED_COLOR.get(face);
			for (int i = 0; i < FACELETS_PER_FACE; i++) {
				this.facelets[face.ordinal()][i] = color;
			}
		}
	}

	private CubeState(final CubeColor[][] facelets) {
		this.facelets = facelets;
	}

	/**
	 * Reads the color of one sticker of the cube.
	 *
	 * @param face the face to read
	 * @param row  0..2, top to bottom as seen from outside the cube
	 * @param col  0..2, left to right as seen from outside the cube
	 * @return the color at that facelet
	 */
	public CubeColor getFacelet(final Face face, final int row, final int col) {
		return this.facelets[face.ordinal()][row * 3 + col];
	}

	/**
	 * Reads the nine colors of one face.
	 *
	 * @param face the face to read
	 * @return a defensive copy of that face's 9 colors, row-major (index
	 *         {@code row * 3 + col})
	 */
	public CubeColor[] getFace(final Face face) {
		return this.facelets[face.ordinal()].clone();
	}

	/**
	 * Tells whether the cube is solved.
	 *
	 * @return {@code true} if every face shows a single uniform color
	 */
	public boolean isSolved() {
		for (Face face : Face.values()) {
			CubeColor[] faceColors = this.facelets[face.ordinal()];
			for (int i = 1; i < faceColors.length; i++) {
				if (faceColors[i] != faceColors[0]) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Applies one quarter or half turn, updating this cube's facelets in
	 * place. See this class's javadoc for the rigid-rotation approach: every
	 * facelet in the turned layer (this face's own 9, plus the matching
	 * border strip of 3 on each of the 4 adjacent faces) has its 3D position
	 * and facing direction rotated by 90/180 degrees around this face's own
	 * outward normal, then is read back into the (face, row, column) grid
	 * that position/facing now correspond to.
	 * <p>
	 * "Clockwise", for {@link RotationDirection#CLOCKWISE}, means as seen by
	 * a viewer looking directly at {@code move.face()} from outside the
	 * cube - the conventional meaning for cube notation.
	 *
	 * @param move the move to apply
	 */
	public void applyMove(final Move move) {
		Face face = move.face();
		int[] normal = NORMAL.get(face);
		int axis = axisComponent(normal);
		int axisSign = normal[axis];

		int clockwiseQuarterTurns = switch (move.direction()) {
			case CLOCKWISE -> 1;
			case HALF_TURN -> 2;
			case COUNTERCLOCKWISE -> 3;
		};
		// See this class's javadoc: a turn that looks clockwise to a viewer
		// outside the face is a NEGATIVE (mathematically) rotation around
		// that face's own outward normal, viewed from the tip of the
		// normal looking back at the origin - the standard right-hand-rule
		// convention. Expressing that as whole quarter turns of the
		// (always-positive) coordinate axis this face's normal lies on
		// absorbs the face's own normal sign here, once, rather than in
		// every call site.
		int quarterTurns = Math.floorMod(axisSign * -clockwiseQuarterTurns, 4);

		CubeColor[][] rotated = new CubeColor[FACE_COUNT][FACELETS_PER_FACE];
		for (Face sourceFace : Face.values()) {
			CubeColor[] sourceColors = this.facelets[sourceFace.ordinal()];
			int[] sourceNormal = NORMAL.get(sourceFace);
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					int[] position = position(sourceFace, row, col);
					if (position[axis] != axisSign) {
						// Not in the turned layer: stays exactly where it is.
						rotated[sourceFace.ordinal()][row * 3 + col] = sourceColors[row * 3 + col];
						continue;
					}
					int[] newPosition = rotateQuarterTurns(position, axis, quarterTurns);
					int[] newFacing = rotateQuarterTurns(sourceNormal, axis, quarterTurns);
					Face newFace = faceForNormal(newFacing);
					int[] rowCol = rowColumn(newFace, newPosition);
					rotated[newFace.ordinal()][rowCol[0] * 3 + rowCol[1]] = sourceColors[row * 3 + col];
				}
			}
		}
		System.arraycopy(rotated, 0, this.facelets, 0, FACE_COUNT);
	}

	/**
	 * Returns where the small cube carrying a given facelet sits, as three coordinates each equal to -1, 0 or 1
	 * (x towards the right face, y towards the up face, z towards the front face). Facelets of one small cube share the
	 * same coordinates: two for an edge, three for a corner.
	 *
	 * @param face the face the facelet is on
	 * @param row  the facelet's row on that face, 0 to 2
	 * @param col  the facelet's column on that face, 0 to 2
	 * @return the coordinates of the small cube carrying the facelet
	 */
	public static int[] cubieOf(final Face face, final int row, final int col) {
		return position(face, row, col);
	}

	/**
	 * Returns the outward direction of a face, as three coordinates in the same system as {@link #cubieOf}.
	 *
	 * @param face the face
	 * @return its outward direction
	 */
	public static int[] normalOf(final Face face) {
		return NORMAL.get(face).clone();
	}

	/**
	 * Returns the color a face shows when the cube is solved.
	 *
	 * @param face the face
	 * @return its solved color
	 */
	public static CubeColor solvedColor(final Face face) {
		return SOLVED_COLOR.get(face);
	}

	/**
	 * Converts a facelet's grid coordinates to its 3D position.
	 *
	 * @param face the face whose grid coordinates to convert
	 * @param row  0 to 2
	 * @param col  0 to 2
	 * @return the 3D position, as {@code {x, y, z}} in {@code -1, 0, 1}, of
	 *         that facelet
	 */
	private static int[] position(final Face face, final int row, final int col) {
		int[] normal = NORMAL.get(face);
		int[] right = RIGHT_DIRECTION.get(face);
		int[] up = UP_DIRECTION.get(face);
		int[] position = new int[3];
		for (int i = 0; i < 3; i++) {
			position[i] = normal[i] + right[i] * (col - 1) - up[i] * (row - 1);
		}
		return position;
	}

	/**
	 * The inverse of {@link #position(Face, int, int)}: recovers a face's
	 * row/column from a 3D position already known to lie on that face.
	 *
	 * @param face     the face the position lies on
	 * @param position the 3D position, as {@code {x, y, z}}
	 * @return {@code {row, col}}
	 */
	private static int[] rowColumn(final Face face, final int[] position) {
		int[] right = RIGHT_DIRECTION.get(face);
		int[] up = UP_DIRECTION.get(face);
		int col = 1 + dot(position, right) - dot(NORMAL.get(face), right);
		int row = 1 - (dot(position, up) - dot(NORMAL.get(face), up));
		return new int[] { row, col };
	}

	private static int dot(final int[] a, final int[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static int axisComponent(final int[] normal) {
		for (int i = 0; i < 3; i++) {
			if (normal[i] != 0) {
				return i;
			}
		}
		throw new IllegalStateException("a face normal must have exactly one nonzero component");
	}

	private static Face faceForNormal(final int[] normal) {
		for (Face face : Face.values()) {
			int[] candidate = NORMAL.get(face);
			if (candidate[0] == normal[0] && candidate[1] == normal[1] && candidate[2] == normal[2]) {
				return face;
			}
		}
		throw new IllegalStateException("no face has normal " + Arrays.toString(normal));
	}

	/**
	 * Rotates {@code vector} by {@code quarterTurns} lots of 90 degrees
	 * (mathematically positive, right-hand rule) around the coordinate axis
	 * named by {@code axis} (0 = x, 1 = y, 2 = z).
	 *
	 * @param vector       the vector to rotate, as {@code {x, y, z}}
	 * @param axis         which coordinate axis to rotate around
	 * @param quarterTurns how many 90 degree turns to apply, 0..3
	 * @return the rotated vector
	 */
	private static int[] rotateQuarterTurns(final int[] vector, final int axis, final int quarterTurns) {
		int[] result = vector;
		for (int turn = 0; turn < quarterTurns; turn++) {
			result = rotateOneQuarterTurn(result, axis);
		}
		return result;
	}

	private static int[] rotateOneQuarterTurn(final int[] v, final int axis) {
		int x = v[0];
		int y = v[1];
		int z = v[2];
		return switch (axis) {
			case 0 -> new int[] { x, -z, y };
			case 1 -> new int[] { z, y, -x };
			case 2 -> new int[] { -y, x, z };
			default -> throw new IllegalArgumentException("axis must be 0, 1 or 2");
		};
	}

	@Override
	public CubeState clone() {
		CubeColor[][] copy = new CubeColor[FACE_COUNT][];
		for (int i = 0; i < FACE_COUNT; i++) {
			copy[i] = this.facelets[i].clone();
		}
		return new CubeState(copy);
	}
}
