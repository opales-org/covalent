/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.diagrampersistence.controller.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.diagrampersistence.controller.DiagramPersistenceController;
import org.opales.covalent.supervision.diagrampersistence.controller.DiagramPersistenceServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

/**
 * DiagramPersistence's Controller: the views library, stored as plain files -
 * no JSON/XML library exists anywhere in this repo's dependencies, and this
 * deliberately does not add one.
 * <p>
 * Layout: {@code <views folder>/<container>/<view>.cow}, with the thumbnail
 * beside it in {@code <view>.png}; folder and file names are the container's
 * and the view's names, in which every byte (UTF-8) other than ASCII letters and
 * digits, space, {@code .}, {@code -} and {@code _} is written {@code %XX}, as are a
 * {@code .} or a space at either end of the name and the first letter of a reserved
 * name such as {@code CON}, so that two different names never share a file.
 * <p>
 * File shape (one {@code key=value} per line, UTF-8; in the name, the
 * container and the description, {@code \} is written {@code \\} and a line
 * break {@code \n}; class names and numbers contain neither {@code ,} nor
 * {@code :}):
 *
 * <pre>
 * format=1
 * name=Ecosystem - predation flow
 * description=Controller and model only.
 * container=Ecosystem Demo
 * savedAt=2026-10-04T14:32:00Z
 * focusPath=org.acme.Foo,org.acme.Bar
 * hiddenComponents=org.acme.Baz
 * selection=org.acme.Qux
 * zoom=1.25
 * positions=org.acme.Baz:120.5:340.0,org.acme.Qux:400.0:120.0
 * </pre>
 *
 * {@code format} is the version of the file shape. A file without it is read as version 1, and a file of a version
 * above the one this class writes is refused with its own message, since a newer shape could mean something else by
 * a field this class knows.
 * <p>
 * A file missing the name, the container or the date, or with an
 * unreadable value, is not a view: it is reported, never partially
 * interpreted.
 */
@Implementation
public class DiagramPersistenceControllerImpl implements DiagramPersistenceController, DiagramPersistenceServices {

	private static final Logger LOGGER = Logger.getLogger(DiagramPersistenceControllerImpl.class.getName());

	private static final String VIEW_EXTENSION = ".cow";
	private static final String THUMBNAIL_EXTENSION = ".png";

	/** The names some file systems reserve, with or without an extension. */
	private static final Pattern RESERVED_FILE_NAME = Pattern
			.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?");

	/** The version of the file shape written, and the highest one read. */
	private static final int FORMAT_VERSION = 1;

	private static final String KEY_FORMAT = "format";
	private static final String KEY_NAME = "name";
	private static final String KEY_DESCRIPTION = "description";
	private static final String KEY_CONTAINER = "container";
	private static final String KEY_SAVED_AT = "savedAt";
	private static final String KEY_FOCUS_PATH = "focusPath";
	private static final String KEY_HIDDEN_COMPONENTS = "hiddenComponents";
	private static final String KEY_SELECTION = "selection";
	private static final String KEY_ZOOM = "zoom";
	private static final String KEY_POSITIONS = "positions";

	/** The views folder imposed at construction, {@code null} to follow the settings. */
	private final File fixedViewsFolder;

	/**
	 * Creates the views library, in the views folder chosen in the
	 * supervisor's settings.
	 */
	public DiagramPersistenceControllerImpl() {
		this(null);
	}

	/**
	 * Creates the views library in the given views folder.
	 *
	 * @param viewsFolder the views folder, {@code null} to follow the settings
	 */
	DiagramPersistenceControllerImpl(final File viewsFolder) {
		this.fixedViewsFolder = viewsFolder;
	}

	@Override
	public File getViewsFolder() {
		if (this.fixedViewsFolder != null) {
			return this.fixedViewsFolder;
		}
		String chosen = SupervisorSettings.getString(SupervisorSettings.VIEWS_DIRECTORY);
		if (chosen != null) {
			return new File(chosen);
		}
		return new File(System.getProperty("user.home"), ".covalent/supervisor/views");
	}

	@Override
	public List<SavedView> listViews() {
		List<SavedView> views = new ArrayList<>();
		File[] containerFolders = this.getViewsFolder().listFiles(File::isDirectory);
		if (containerFolders == null) {
			return views;
		}
		for (File containerFolder : containerFolders) {
			File[] files = containerFolder.listFiles((folder, fileName) -> fileName.endsWith(VIEW_EXTENSION));
			if (files == null) {
				continue;
			}
			for (File file : files) {
				try {
					views.add(this.read(file));
				} catch (IOException e) {
					String fileName = file.getName();
					views.add(new SavedView(fileName.substring(0, fileName.length() - VIEW_EXTENSION.length()), "",
							null, null, null, file, thumbnailOf(file), e.getMessage()));
				}
			}
		}
		views.sort(Comparator.comparing((SavedView view) -> view.savedAt() == null ? Instant.MIN : view.savedAt())
				.reversed());
		return views;
	}

	@Override
	public boolean hasView(final String containerName, final String name) {
		return this.fileOf(containerName, name).isFile();
	}

	@Override
	public SavedView saveView(final String containerName, final String name, final String description,
			final ComponentDiagramSnapshot snapshot, final byte[] thumbnailPng) throws IOException {
		File file = this.fileOf(containerName, name);
		this.checkNotSharedWithAnotherView(file, containerName, name);
		Files.createDirectories(file.getParentFile().toPath());
		SavedView view = new SavedView(name, description, containerName, Instant.now(), snapshot, file,
				thumbnailOf(file), null);
		this.write(view, file);
		File thumbnail = thumbnailOf(file);
		if (thumbnailPng != null) {
			writeAtomically(thumbnail, thumbnailPng);
		} else {
			Files.deleteIfExists(thumbnail.toPath());
		}
		return view;
	}

	@Override
	public SavedView renameView(final SavedView view, final String newName) throws IOException {
		File file = this.fileOf(view.containerName(), newName);
		if (!file.equals(view.file()) && file.exists()) {
			throw new IOException(DiagramPersistenceTexts.get("error.nameTaken", newName, view.containerName()));
		}
		SavedView renamed = new SavedView(newName, view.description(), view.containerName(), view.savedAt(),
				view.snapshot(), file, thumbnailOf(file), null);
		this.write(renamed, file);
		if (!file.equals(view.file())) {
			try {
				if (view.thumbnail().isFile()) {
					Files.move(view.thumbnail().toPath(), renamed.thumbnail().toPath(),
							StandardCopyOption.REPLACE_EXISTING);
				}
				Files.deleteIfExists(view.file().toPath());
			} catch (IOException | RuntimeException e) {
				this.undoRename(view, renamed);
				throw e;
			}
		}
		return renamed;
	}

	/** Puts a view back as it was after a rename failed half way: its thumbnail home, the new file gone. */
	private void undoRename(final SavedView view, final SavedView renamed) {
		try {
			if (!view.thumbnail().isFile() && renamed.thumbnail().isFile()) {
				Files.move(renamed.thumbnail().toPath(), view.thumbnail().toPath());
			}
			Files.deleteIfExists(renamed.file().toPath());
		} catch (IOException | RuntimeException e) {
			LOGGER.log(Level.WARNING, "A failed rename could not be undone for " + view.file(), e);
		}
	}

	@Override
	public void deleteView(final SavedView view) throws IOException {
		Files.deleteIfExists(view.file().toPath());
		Files.deleteIfExists(view.thumbnail().toPath());
	}

	@Override
	public void exportView(final SavedView view, final File file) throws IOException {
		Files.copy(view.file().toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public SavedView readView(final File file) throws IOException {
		return this.read(file);
	}

	@Override
	public SavedView importView(final File file) throws IOException {
		SavedView view = this.read(file);
		return this.saveView(view.containerName(), view.name(), view.description(), view.snapshot(), null);
	}

	// --- Files ---

	/**
	 * Refuses to replace the file of a view named differently: a file system that ignores the case of file names
	 * gives the same file to {@code Alpha} and {@code ALPHA}. A file that is not a view is replaced.
	 */
	private void checkNotSharedWithAnotherView(final File file, final String containerName, final String name)
			throws IOException {
		if (!file.isFile()) {
			return;
		}
		SavedView existing;
		try {
			existing = this.read(file);
		} catch (IOException notAView) {
			return;
		}
		if (!existing.name().equals(name) || !existing.containerName().equals(containerName)) {
			throw new IOException(DiagramPersistenceTexts.get("error.nameTaken", name, containerName));
		}
	}

	private File fileOf(final String containerName, final String name) {
		return new File(new File(this.getViewsFolder(), fileName(containerName)), fileName(name) + VIEW_EXTENSION);
	}

	private static File thumbnailOf(final File viewFile) {
		String fileName = viewFile.getName();
		return new File(viewFile.getParentFile(),
				fileName.substring(0, fileName.length() - VIEW_EXTENSION.length()) + THUMBNAIL_EXTENSION);
	}

	/**
	 * A name usable as a file name on any platform, and a different one for each name: ASCII letters, digits,
	 * space, {@code .}, {@code -} and {@code _} are kept, every other byte of the name (in UTF-8) is written
	 * {@code %XX}, as is a {@code .} or a space at either end of the name and the first letter of a name the
	 * file systems reserve (such as {@code CON}). The empty name is written {@code %}.
	 */
	private static String fileName(final String name) {
		if (name.isEmpty()) {
			return "%";
		}
		byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
		int last = bytes.length - 1;
		boolean reserved = RESERVED_FILE_NAME.matcher(name).matches();
		StringBuilder fileName = new StringBuilder();
		for (int index = 0; index <= last; index++) {
			int value = bytes[index] & 0xFF;
			boolean atEnd = index == 0 || index == last;
			boolean kept = value < 0x80 && (Character.isLetterOrDigit(value) || value == '_' || value == '-'
					|| (value == '.' || value == ' ') && !atEnd);
			if (kept && !(reserved && index == 0)) {
				fileName.append((char) value);
			} else {
				fileName.append('%').append(String.format("%02X", value));
			}
		}
		return fileName.toString();
	}

	// --- Format ---

	private void write(final SavedView view, final File file) throws IOException {
		ComponentDiagramSnapshot snapshot = view.snapshot();
		List<String> lines = List.of(
				KEY_FORMAT + "=" + FORMAT_VERSION,
				KEY_NAME + "=" + escape(view.name()),
				KEY_DESCRIPTION + "=" + escape(view.description()),
				KEY_CONTAINER + "=" + escape(view.containerName()),
				KEY_SAVED_AT + "=" + view.savedAt(),
				KEY_FOCUS_PATH + "=" + String.join(",", snapshot.focusPath()),
				KEY_HIDDEN_COMPONENTS + "=" + String.join(",", sorted(snapshot.hiddenComponents())),
				KEY_SELECTION + "=" + String.join(",", sorted(snapshot.selection())),
				KEY_ZOOM + "=" + snapshot.zoom(),
				KEY_POSITIONS + "=" + joinPositions(snapshot.positions()));
		writeAtomically(file, String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Writes a file whole or not at all: to a neighbor that {@link #listViews()} ignores, then moved over the
	 * target, so that a crash never leaves a truncated view.
	 */
	private static void writeAtomically(final File file, final byte[] content) throws IOException {
		Path target = file.toPath();
		Path temporary = Files.createTempFile(target.getParent(), "write-", ".tmp");
		try {
			Files.write(temporary, content);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private SavedView read(final File file) throws IOException {
		Map<String, String> fields = new LinkedHashMap<>();
		for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
			int separator = line.indexOf('=');
			if (separator > 0) {
				fields.put(line.substring(0, separator), line.substring(separator + 1));
			}
		}
		checkFormat(fields.get(KEY_FORMAT), file);
		String name = unescape(required(fields, KEY_NAME, file));
		String containerName = unescape(required(fields, KEY_CONTAINER, file));
		Instant savedAt;
		try {
			savedAt = Instant.parse(required(fields, KEY_SAVED_AT, file).trim());
		} catch (DateTimeParseException e) {
			throw unreadable(file, KEY_SAVED_AT);
		}
		String description = unescape(fields.getOrDefault(KEY_DESCRIPTION, ""));
		ComponentDiagramSnapshot snapshot = new ComponentDiagramSnapshot(splitList(fields.get(KEY_FOCUS_PATH)),
				new LinkedHashSet<>(splitList(fields.get(KEY_HIDDEN_COMPONENTS))),
				parsePositions(fields.get(KEY_POSITIONS), file),
				new LinkedHashSet<>(splitList(fields.get(KEY_SELECTION))), parseZoom(fields.get(KEY_ZOOM), file));
		return new SavedView(name, description, containerName, savedAt, snapshot, file, thumbnailOf(file), null);
	}

	/** Refuses a file of a version this class does not know; one without a version is the first. */
	private static void checkFormat(final String value, final File file) throws IOException {
		if (value == null) {
			return;
		}
		int version;
		try {
			version = Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			throw unreadable(file, KEY_FORMAT);
		}
		if (version < 1) {
			throw unreadable(file, KEY_FORMAT);
		}
		if (version > FORMAT_VERSION) {
			throw new IOException(DiagramPersistenceTexts.get("error.newer", file.getName()));
		}
	}

	private static String required(final Map<String, String> fields, final String key, final File file)
			throws IOException {
		String value = fields.get(key);
		if (value == null || value.isBlank()) {
			throw new IOException(DiagramPersistenceTexts.get("error.missing", key, file.getName()));
		}
		return value;
	}

	private static IOException unreadable(final File file, final String key) {
		return new IOException(DiagramPersistenceTexts.get("error.invalid", key, file.getName()));
	}

	private static String escape(final String text) {
		return text.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n");
	}

	private static String unescape(final String text) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character == '\\' && index + 1 < text.length()) {
				char next = text.charAt(++index);
				builder.append(next == 'n' ? '\n' : next);
			} else {
				builder.append(character);
			}
		}
		return builder.toString();
	}

	/** The names in alphabetical order, so that a file does not depend on the order of the set they come from. */
	private static List<String> sorted(final Collection<String> names) {
		List<String> sorted = new ArrayList<>(names);
		Collections.sort(sorted);
		return sorted;
	}

	private static String joinPositions(final Map<String, double[]> positions) {
		List<String> entries = new ArrayList<>();
		for (Entry<String, double[]> entry : new TreeMap<>(positions).entrySet()) {
			double[] xy = entry.getValue();
			entries.add(entry.getKey() + ":" + xy[0] + ":" + xy[1]);
		}
		return String.join(",", entries);
	}

	private static List<String> splitList(final String value) {
		if (value == null || value.isEmpty()) {
			return List.of();
		}
		return List.of(value.split(",", -1));
	}

	private static double parseZoom(final String value, final File file) throws IOException {
		try {
			return value == null ? 1.0 : Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			throw unreadable(file, KEY_ZOOM);
		}
	}

	private static Map<String, double[]> parsePositions(final String value, final File file) throws IOException {
		Map<String, double[]> positions = new LinkedHashMap<>();
		for (String entry : splitList(value)) {
			String[] parts = entry.split(":", -1);
			if (parts.length != 3) {
				throw unreadable(file, KEY_POSITIONS);
			}
			try {
				positions.put(parts[0], new double[] { Double.parseDouble(parts[1]), Double.parseDouble(parts[2]) });
			} catch (NumberFormatException e) {
				throw unreadable(file, KEY_POSITIONS);
			}
		}
		return positions;
	}
}
