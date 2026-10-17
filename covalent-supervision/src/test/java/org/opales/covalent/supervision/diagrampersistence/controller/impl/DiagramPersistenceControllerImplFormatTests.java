/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.diagrampersistence.controller.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

/**
 * Verifies the views library's file naming, its escaping of text fields, the rejection of files
 * with a missing or invalid field, the handling of thumbnails when a view is saved or renamed, and
 * the choice of the views folder from the supervisor's settings.
 */
public class DiagramPersistenceControllerImplFormatTests {

	private static final String CONTAINER = "Demo";

	private static final ComponentDiagramSnapshot SNAPSHOT = new ComponentDiagramSnapshot(List.of(), Set.of(),
			Map.of(), Set.of(), 1.0);

	private static final byte[] THUMBNAIL = { 1, 2, 3 };

	private Path folder;

	private DiagramPersistenceControllerImpl library;

	@Before
	public void setUp() throws IOException {
		this.folder = Files.createTempDirectory("views-format-test");
		this.library = new DiagramPersistenceControllerImpl(this.folder.toFile());
	}

	@After
	public void tearDown() throws IOException {
		try (Stream<Path> paths = Files.walk(this.folder)) {
			paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}

	private Path writeRaw(final String fileName, final String... lines) throws IOException {
		Path containerFolder = Files.createDirectories(this.folder.resolve(CONTAINER));
		Path file = containerFolder.resolve(fileName);
		Files.write(file, List.of(lines), StandardCharsets.UTF_8);
		return file;
	}

	@Test
	public void viewsFolderFollowsTheSettingsOtherwiseTheUserHome() {
		String previous = SupervisorSettings.getString(SupervisorSettings.VIEWS_DIRECTORY);
		DiagramPersistenceControllerImpl following = new DiagramPersistenceControllerImpl();
		try {
			java.util.prefs.Preferences.userRoot().node("org/opales/covalent/supervisor")
					.remove(SupervisorSettings.VIEWS_DIRECTORY);
			Assert.assertEquals(new File(System.getProperty("user.home"), ".covalent/supervisor/views"),
					following.getViewsFolder());
			SupervisorSettings.putString(SupervisorSettings.VIEWS_DIRECTORY, this.folder.toString());
			Assert.assertEquals(this.folder.toFile(), following.getViewsFolder());
		} finally {
			if (previous != null) {
				SupervisorSettings.putString(SupervisorSettings.VIEWS_DIRECTORY, previous);
			} else {
				java.util.prefs.Preferences.userRoot().node("org/opales/covalent/supervisor")
						.remove(SupervisorSettings.VIEWS_DIRECTORY);
			}
		}
		Assert.assertEquals(this.folder.toFile(), this.library.getViewsFolder());
	}

	@Test
	public void missingViewsFolderListsNoView() throws IOException {
		DiagramPersistenceControllerImpl absent = new DiagramPersistenceControllerImpl(
				this.folder.resolve("absent").toFile());
		Assert.assertTrue(absent.listViews().isEmpty());
	}

	@Test
	public void forbiddenCharactersInNamesAreWrittenAsTheirBytesInFileNames() throws IOException {
		SavedView view = this.library.saveView("a/b", "x:y*z", "", SNAPSHOT, null);
		Assert.assertEquals("a%2Fb", view.file().getParentFile().getName());
		Assert.assertEquals("x%3Ay%2Az.cow", view.file().getName());
		Assert.assertEquals("%C3%89t%C3%A9.cow", this.library.saveView(CONTAINER, "Été", "", SNAPSHOT, null).file().getName());
		Assert.assertEquals("100%25.cow", this.library.saveView(CONTAINER, "100%", "", SNAPSHOT, null).file().getName());
		Assert.assertTrue(this.library.hasView("a/b", "x:y*z"));
	}

	@Test
	public void namesTheFileSystemsTreatSpeciallyAreMadeSafeInFileNames() throws IOException {
		Assert.assertEquals("%2Ehidden.cow", this.library.saveView(CONTAINER, ".hidden", "", SNAPSHOT, null).file().getName());
		Assert.assertEquals("a%2E.cow", this.library.saveView(CONTAINER, "a.", "", SNAPSHOT, null).file().getName());
		Assert.assertEquals("%20%20.cow", this.library.saveView(CONTAINER, "  ", "", SNAPSHOT, null).file().getName());
		Assert.assertEquals("%43ON.cow", this.library.saveView(CONTAINER, "CON", "", SNAPSHOT, null).file().getName());
		Assert.assertEquals("%6Eul.txt.cow", this.library.saveView(CONTAINER, "nul.txt", "", SNAPSHOT, null).file().getName());
		Assert.assertEquals("Console.cow", this.library.saveView(CONTAINER, "Console", "", SNAPSHOT, null).file().getName());
	}

	@Test
	public void backslashesAndLineBreaksInTextFieldsSurviveTheRoundTrip() throws IOException {
		this.library.saveView("C:\\dir", "back\\slash", "line1\r\nline2\\n end\\", SNAPSHOT, null);
		SavedView view = this.library.listViews().get(0);
		Assert.assertEquals("C:\\dir", view.containerName());
		Assert.assertEquals("back\\slash", view.name());
		Assert.assertEquals("line1\nline2\\n end\\", view.description());
	}

	@Test
	public void savingWithoutThumbnailRemovesTheThumbnailOfAnEarlierSave() throws IOException {
		SavedView first = this.library.saveView(CONTAINER, "v", "", SNAPSHOT, THUMBNAIL);
		Assert.assertTrue(first.thumbnail().isFile());
		this.library.saveView(CONTAINER, "v", "", SNAPSHOT, null);
		Assert.assertFalse(first.thumbnail().exists());
	}

	@Test
	public void renamingKeepsTheThumbnailWithTheView() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "old", "d", SNAPSHOT, THUMBNAIL);
		SavedView renamed = this.library.renameView(view, "new");
		Assert.assertEquals("new", renamed.name());
		Assert.assertFalse(view.file().exists());
		Assert.assertFalse(view.thumbnail().exists());
		Assert.assertArrayEquals(THUMBNAIL, Files.readAllBytes(renamed.thumbnail().toPath()));
		Assert.assertEquals("new", this.library.listViews().get(0).name());
	}

	@Test
	public void renamingWithoutThumbnailLeavesNoThumbnail() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "old", "", SNAPSHOT, null);
		SavedView renamed = this.library.renameView(view, "new");
		Assert.assertTrue(renamed.file().isFile());
		Assert.assertFalse(renamed.thumbnail().exists());
	}

	@Test
	public void renamingToTheSameFileRewritesTheViewInPlace() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "name", "d", SNAPSHOT, THUMBNAIL);
		SavedView renamed = this.library.renameView(view, "name");
		Assert.assertEquals(view.file(), renamed.file());
		Assert.assertTrue(renamed.thumbnail().isFile());
		Assert.assertEquals(1, this.library.listViews().size());
	}

	@Test
	public void exportedFileOverwritesTheTarget() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "v", "d", SNAPSHOT, null);
		File target = this.folder.resolve("out.cow").toFile();
		Files.writeString(target.toPath(), "old");
		this.library.exportView(view, target);
		Assert.assertEquals("d", this.library.readView(target).description());
	}

	@Test
	public void fileWithoutNameContainerOrDateIsNotAView() throws IOException {
		Path noName = this.writeRaw("noname.cow", "container=C", "savedAt=2026-01-01T00:00:00Z");
		Path noContainer = this.writeRaw("nocontainer.cow", "name=N", "savedAt=2026-01-01T00:00:00Z");
		Path noDate = this.writeRaw("nodate.cow", "name=N", "container=C");
		for (Path file : List.of(noName, noContainer, noDate)) {
			Assert.assertThrows(file.toString(), IOException.class, () -> this.library.readView(file.toFile()));
		}
		Assert.assertEquals(3, this.library.listViews().stream().filter(view -> !view.readable()).count());
	}

	@Test
	public void invalidDateOrZoomOrPositionsMakeTheFileUnreadable() throws IOException {
		Path badDate = this.writeRaw("date.cow", "name=N", "container=C", "savedAt=yesterday");
		Path badZoom = this.writeRaw("zoom.cow", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z", "zoom=big");
		Path shortPosition = this.writeRaw("pos.cow", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z",
				"positions=a:1");
		for (Path file : List.of(badDate, badZoom, shortPosition)) {
			Assert.assertThrows(file.toString(), IOException.class, () -> this.library.readView(file.toFile()));
		}
	}

	@Test
	public void absentOptionalFieldsDefaultToEmptyValuesAndUnitZoom() throws IOException {
		Path file = this.writeRaw("minimal.cow", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z",
				"ignored line without separator");
		SavedView view = this.library.readView(file.toFile());
		Assert.assertEquals("", view.description());
		Assert.assertEquals(1.0, view.snapshot().zoom(), 0.0);
		Assert.assertTrue(view.snapshot().focusPath().isEmpty());
		Assert.assertTrue(view.snapshot().positions().isEmpty());
		Assert.assertTrue(view.readable());
	}

	@Test
	public void aSavedFileStartsWithItsFormatVersion() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "v", "d", SNAPSHOT, null);

		Assert.assertEquals("format=1", Files.readAllLines(view.file().toPath(), StandardCharsets.UTF_8).get(0));
		Assert.assertEquals("d", this.library.readView(view.file()).description());
	}

	@Test
	public void aFileWithoutAFormatVersionIsReadAsTheFirstOne() throws IOException {
		Path file = this.writeRaw("noformat.cow", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z");

		Assert.assertEquals("N", this.library.readView(file.toFile()).name());
	}

	@Test
	public void namesAndPositionsAreWrittenInAlphabeticalOrderWhateverTheOrderOfTheirSets() throws IOException {
		Set<String> hidden = new LinkedHashSet<>(List.of("org.acme.Zed", "org.acme.Alpha", "org.acme.Mid"));
		Set<String> selected = new LinkedHashSet<>(List.of("org.acme.Mid", "org.acme.Beta"));
		Map<String, double[]> positions = new LinkedHashMap<>();
		positions.put("org.acme.Zed", new double[] { 1, 2 });
		positions.put("org.acme.Alpha", new double[] { 3, 4 });
		ComponentDiagramSnapshot scrambled = new ComponentDiagramSnapshot(List.of("org.acme.Top", "org.acme.Sub"),
				hidden, positions, selected, 1.0);

		SavedView view = this.library.saveView(CONTAINER, "v", "", scrambled, null);

		List<String> lines = Files.readAllLines(view.file().toPath(), StandardCharsets.UTF_8);
		Assert.assertTrue(lines.contains("hiddenComponents=org.acme.Alpha,org.acme.Mid,org.acme.Zed"));
		Assert.assertTrue(lines.contains("selection=org.acme.Beta,org.acme.Mid"));
		Assert.assertTrue(lines.contains("positions=org.acme.Alpha:3.0:4.0,org.acme.Zed:1.0:2.0"));
		// The focus path is a path, not a set: its order is its meaning.
		Assert.assertTrue(lines.contains("focusPath=org.acme.Top,org.acme.Sub"));
	}

	@Test
	public void aFileOfANewerFormatIsRefusedWithItsOwnMessageAndListedAsUnreadable() throws IOException {
		Path file = this.writeRaw("newer.cow", "format=2", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z");

		IOException refused = Assert.assertThrows(IOException.class, () -> this.library.readView(file.toFile()));

		Assert.assertTrue(refused.getMessage(), refused.getMessage().contains("newer.cow"));
		Assert.assertFalse(refused.getMessage(), refused.getMessage().contains("format"));
		SavedView listed = this.library.listViews().get(0);
		Assert.assertFalse(listed.readable());
		Assert.assertEquals(refused.getMessage(), listed.problem());
	}

	@Test
	public void aFormatVersionThatIsNotAPositiveNumberMakesTheFileUnreadable() throws IOException {
		Path text = this.writeRaw("text.cow", "format=one", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z");
		Path zero = this.writeRaw("zero.cow", "format=0", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z");
		Path blank = this.writeRaw("blank.cow", "format=", "name=N", "container=C", "savedAt=2026-01-01T00:00:00Z");
		for (Path file : List.of(text, zero, blank)) {
			IOException refused = Assert.assertThrows(file.toString(), IOException.class,
					() -> this.library.readView(file.toFile()));
			Assert.assertTrue(refused.getMessage(), refused.getMessage().contains("format"));
		}
	}

	@Test
	public void unreadableViewsAreListedAfterReadableOnes() throws IOException {
		this.library.saveView(CONTAINER, "good", "", SNAPSHOT, null);
		this.writeRaw("bad.cow", "zoom=x");
		List<SavedView> views = this.library.listViews();
		Assert.assertEquals(2, views.size());
		Assert.assertTrue(views.get(0).readable());
		Assert.assertFalse(views.get(1).readable());
	}
}
