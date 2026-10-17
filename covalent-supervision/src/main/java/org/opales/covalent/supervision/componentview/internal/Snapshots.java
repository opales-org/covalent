/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelReader;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

/** Diagram snapshots: saved as a PNG and placed on the clipboard. */
public final class Snapshots {

	private static final Logger log = Logger.getLogger(Snapshots.class.getName());

	private Snapshots() {
	}

	/**
	 * Returns snapshot parameters filled with what shows behind a node on screen: the background of
	 * the nearest region, from the node up, that paints one - the theme's, not the default white.
	 *
	 * @param node the node to capture
	 * @return the snapshot parameters
	 */
	public static SnapshotParameters onScreenBackground(final Node node) {
		SnapshotParameters parameters = new SnapshotParameters();
		parameters.setFill(backgroundBehind(node));
		return parameters;
	}

	/**
	 * Captures a node cropped to what it draws, with a margin, over the background shown behind it
	 * on screen - see {@link #onScreenBackground(Node)}. A node that draws nothing is captured whole.
	 *
	 * @param node   the node to capture
	 * @param margin the margin kept around what it draws, in pixels
	 * @return the capture
	 */
	public static Image captureContent(final Node node, final int margin) {
		SnapshotParameters transparent = new SnapshotParameters();
		transparent.setFill(Color.TRANSPARENT);
		Image cropped = cropToContent(node.snapshot(transparent, null), margin);
		if (cropped == null) {
			return node.snapshot(onScreenBackground(node), null);
		}
		return new ImageView(cropped).snapshot(onScreenBackground(node), null);
	}

	/**
	 * Returns the part of an image that is not transparent, with a margin.
	 *
	 * @param image  the image to crop
	 * @param margin the margin kept around the non-transparent part, in pixels
	 * @return the cropped image, {@code null} if the whole image is transparent
	 */
	public static Image cropToContent(final Image image, final int margin) {
		PixelReader reader = image.getPixelReader();
		int width = (int) image.getWidth();
		int height = (int) image.getHeight();
		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if ((reader.getArgb(x, y) >>> 24) != 0) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0) {
			return null;
		}
		int x = Math.max(0, minX - margin);
		int y = Math.max(0, minY - margin);
		return new WritableImage(reader, x, y, Math.min(width, maxX + margin + 1) - x,
				Math.min(height, maxY + margin + 1) - y);
	}

	private static Paint backgroundBehind(final Node node) {
		for (Node current = node; current != null; current = current.getParent()) {
			if (current instanceof Region region) {
				Background background = region.getBackground();
				if (background != null) {
					for (int i = background.getFills().size() - 1; i >= 0; i--) {
						BackgroundFill fill = background.getFills().get(i);
						if (fill.getFill() instanceof Color color && color.getOpacity() > 0) {
							return color;
						}
					}
				}
			}
		}
		return Color.WHITE;
	}

	/**
	 * Saves the image to a timestamped PNG in the snapshot folder chosen in the preferences, the
	 * working directory by default, and places it on the system clipboard.
	 *
	 * @param image the snapshot
	 * @param filePrefix the start of the file's name, e.g. "arch"
	 */
	public static void saveAndCopy(final Image image, final String filePrefix) {
		String fileName = filePrefix + "_"
				+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".png";
		try {
			ImageIO.write(toBufferedImage(image), "png", new File(directory(), fileName));
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to save the snapshot to " + fileName, e);
		}
		ClipboardContent clipboardContent = new ClipboardContent();
		clipboardContent.putImage(image);
		Clipboard.getSystemClipboard().setContent(clipboardContent);
	}

	/**
	 * Returns the folder snapshots are saved in: the one chosen in the preferences when it exists, the working
	 * directory otherwise.
	 *
	 * @return the folder snapshots are saved in
	 */
	public static File directory() {
		String chosen = SupervisorSettings.getString(SupervisorSettings.SNAPSHOT_DIRECTORY);
		if (chosen != null) {
			File folder = new File(chosen);
			if (folder.isDirectory()) {
				return folder;
			}
		}
		return new File(".").getAbsoluteFile().getParentFile();
	}

	/**
	 * Encodes an image as PNG.
	 *
	 * @param image the image
	 * @return its PNG bytes, {@code null} if it could not be encoded
	 */
	public static byte[] toPng(final Image image) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(toBufferedImage(image), "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to encode an image as PNG", e);
			return null;
		}
	}

	/** Plain pixel-by-pixel conversion, avoiding a {@code javafx.swing} module dependency. */
	private static BufferedImage toBufferedImage(final Image image) {
		int width = (int) image.getWidth();
		int height = (int) image.getHeight();
		BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		PixelReader pixelReader = image.getPixelReader();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				bufferedImage.setRGB(x, y, pixelReader.getArgb(x, y));
			}
		}
		return bufferedImage;
	}
}
