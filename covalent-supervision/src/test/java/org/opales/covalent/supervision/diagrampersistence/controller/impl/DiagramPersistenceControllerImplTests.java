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

/**
 * The views library: views saved per container under a unique name, read back
 * as saved, listed most recent first, renamed, deleted, exported and
 * imported; a file that is not a view is listed as unreadable.
 */
public class DiagramPersistenceControllerImplTests {

	private static final String CONTAINER = "Ecosystem Demo";

	private static final ComponentDiagramSnapshot SNAPSHOT = new ComponentDiagramSnapshot(
			List.of("org.acme.Foo", "org.acme.Bar"), Set.of("org.acme.Baz"),
			Map.of("org.acme.Baz", new double[] { 120.5, 340.0 }, "org.acme.Qux", new double[] { 400.0, 120.0 }),
			Set.of("org.acme.Qux"), 1.25);

	private static final byte[] THUMBNAIL = { (byte) 0x89, 'P', 'N', 'G' };

	private Path folder;

	private DiagramPersistenceControllerImpl library;

	@Before
	public void setUp() throws IOException {
		this.folder = Files.createTempDirectory("views-library-test");
		this.library = new DiagramPersistenceControllerImpl(this.folder.toFile());
	}

	@After
	public void tearDown() throws IOException {
		try (Stream<Path> paths = Files.walk(this.folder)) {
			paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}

	@Test
	public void aSavedViewIsReadBackAsSaved() throws IOException {
		this.library.saveView(CONTAINER, "Predation: flow / 1", "Controller and model.\nViews hidden.", SNAPSHOT,
				THUMBNAIL);

		List<SavedView> views = this.library.listViews();

		Assert.assertEquals(1, views.size());
		SavedView view = views.get(0);
		Assert.assertTrue(view.readable());
		Assert.assertEquals("Predation: flow / 1", view.name());
		Assert.assertEquals("Controller and model.\nViews hidden.", view.description());
		Assert.assertEquals(CONTAINER, view.containerName());
		Assert.assertNotNull(view.savedAt());
		Assert.assertEquals(SNAPSHOT.focusPath(), view.snapshot().focusPath());
		Assert.assertEquals(SNAPSHOT.hiddenComponents(), view.snapshot().hiddenComponents());
		Assert.assertEquals(SNAPSHOT.selection(), view.snapshot().selection());
		Assert.assertEquals(SNAPSHOT.zoom(), view.snapshot().zoom(), 0.0);
		Assert.assertEquals(SNAPSHOT.positions().keySet(), view.snapshot().positions().keySet());
		for (String key : SNAPSHOT.positions().keySet()) {
			Assert.assertArrayEquals(SNAPSHOT.positions().get(key), view.snapshot().positions().get(key), 0.0);
		}
		Assert.assertArrayEquals(THUMBNAIL, Files.readAllBytes(view.thumbnail().toPath()));
	}

	@Test
	public void aNameIsUniquePerContainer() throws IOException {
		this.library.saveView(CONTAINER, "Overview", "first", SNAPSHOT, null);
		this.library.saveView("Neural Network Demo", "Overview", "other container", SNAPSHOT, null);

		Assert.assertTrue(this.library.hasView(CONTAINER, "Overview"));
		Assert.assertFalse(this.library.hasView(CONTAINER, "Another"));

		this.library.saveView(CONTAINER, "Overview", "replaced", SNAPSHOT, null);

		List<SavedView> views = this.library.listViews();
		Assert.assertEquals(2, views.size());
		Assert.assertEquals("replaced", views.stream().filter(view -> view.containerName().equals(CONTAINER))
				.findFirst().orElseThrow().description());
	}

	@Test
	public void namesThatDifferOnlyByCharactersNotKeptInFileNamesAreDifferentViews() throws IOException {
		String[] names = { "a:b", "a_b", "a/b", "Écosystème", "_cosyst_me", "100%", "100_", "a.", "a", ".hidden", "_.hidden",
				"CON", "Nul" };
		for (String name : names) {
			this.library.saveView(CONTAINER, name, "view " + name, SNAPSHOT, null);
		}

		List<SavedView> views = this.library.listViews();

		Assert.assertEquals(names.length, views.size());
		for (String name : names) {
			Assert.assertTrue("saved: " + name, this.library.hasView(CONTAINER, name));
			Assert.assertEquals("view " + name, views.stream().filter(view -> view.name().equals(name)).findFirst()
					.orElseThrow().description());
		}
	}

	@Test
	public void containersThatDifferOnlyByCharactersNotKeptInFolderNamesKeepTheirViewsApart() throws IOException {
		this.library.saveView("Demo: 1", "Overview", "colon", SNAPSHOT, null);
		this.library.saveView("Demo_ 1", "Overview", "underscore", SNAPSHOT, null);

		Assert.assertEquals(2, this.library.listViews().size());
		Assert.assertEquals("colon", this.library.listViews().stream()
				.filter(view -> view.containerName().equals("Demo: 1")).findFirst().orElseThrow().description());
	}

	@Test
	public void aViewIsNeverReplacedByAViewOfAnotherNameSharingItsFile() throws IOException {
		SavedView saved = this.library.saveView(CONTAINER, "Alpha", "mine", SNAPSHOT, null);
		// What a file system that ignores the case of file names does when "ALPHA" is saved after "Alpha".
		List<String> lines = Files.readAllLines(saved.file().toPath(), StandardCharsets.UTF_8);
		lines.replaceAll(line -> line.startsWith("name=") ? "name=Other" : line);
		Files.write(saved.file().toPath(), lines, StandardCharsets.UTF_8);

		IOException refused = Assert.assertThrows(IOException.class,
				() -> this.library.saveView(CONTAINER, "Alpha", "replacement", SNAPSHOT, null));

		Assert.assertTrue(refused.getMessage(), refused.getMessage().contains("Alpha"));
		SavedView kept = this.library.listViews().get(0);
		Assert.assertEquals("Other", kept.name());
		Assert.assertEquals("mine", kept.description());
	}

	@Test
	public void viewsAreListedMostRecentFirst() throws IOException, InterruptedException {
		this.library.saveView(CONTAINER, "Older", "", SNAPSHOT, null);
		Thread.sleep(5);
		this.library.saveView(CONTAINER, "Newer", "", SNAPSHOT, null);

		List<SavedView> views = this.library.listViews();

		Assert.assertEquals("Newer", views.get(0).name());
		Assert.assertEquals("Older", views.get(1).name());
	}

	@Test
	public void aRenamedViewKeepsItsContentAndThumbnail() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "Before", "kept", SNAPSHOT, THUMBNAIL);

		SavedView renamed = this.library.renameView(view, "After");

		Assert.assertFalse(this.library.hasView(CONTAINER, "Before"));
		Assert.assertTrue(this.library.hasView(CONTAINER, "After"));
		SavedView listed = this.library.listViews().get(0);
		Assert.assertEquals("After", listed.name());
		Assert.assertEquals("kept", listed.description());
		Assert.assertEquals(view.savedAt(), listed.savedAt());
		Assert.assertArrayEquals(THUMBNAIL, Files.readAllBytes(renamed.thumbnail().toPath()));
		Assert.assertFalse(view.thumbnail().exists());
	}

	@Test
	public void renamingToAnExistingNameIsRefused() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "First", "", SNAPSHOT, null);
		this.library.saveView(CONTAINER, "Second", "", SNAPSHOT, null);

		Assert.assertThrows(IOException.class, () -> this.library.renameView(view, "Second"));
		Assert.assertTrue(this.library.hasView(CONTAINER, "First"));
	}

	@Test
	public void aDeletedViewIsGoneWithItsThumbnail() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "Gone", "", SNAPSHOT, THUMBNAIL);

		this.library.deleteView(view);

		Assert.assertTrue(this.library.listViews().isEmpty());
		Assert.assertFalse(view.thumbnail().exists());
	}

	@Test
	public void anExportedViewIsImportedForItsContainer() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "Shared", "to share", SNAPSHOT, null);
		File exported = this.folder.resolve("exported.cow").toFile();
		this.library.exportView(view, exported);
		this.library.deleteView(view);

		SavedView read = this.library.readView(exported);
		Assert.assertEquals("Shared", read.name());
		Assert.assertFalse(this.library.hasView(CONTAINER, "Shared"));

		this.library.importView(exported);

		Assert.assertTrue(this.library.hasView(CONTAINER, "Shared"));
		Assert.assertEquals("to share", this.library.listViews().get(0).description());
	}

	@Test
	public void aFileThatIsNotAViewIsListedAsUnreadable() throws IOException {
		Path containerFolder = Files.createDirectories(this.folder.resolve("Some container"));
		Files.writeString(containerFolder.resolve("Broken.cow"), "zoom=not a number\n", StandardCharsets.UTF_8);

		List<SavedView> views = this.library.listViews();

		Assert.assertEquals(1, views.size());
		Assert.assertFalse(views.get(0).readable());
		Assert.assertEquals("Broken", views.get(0).name());
		Assert.assertNotNull(views.get(0).problem());
		Assert.assertThrows(IOException.class, () -> this.library.readView(containerFolder.resolve("Broken.cow").toFile()));
	}

	@Test
	public void anUnreadableValueIsReportedNotGuessed() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "Damaged", "", SNAPSHOT, null);
		List<String> lines = Files.readAllLines(view.file().toPath(), StandardCharsets.UTF_8);
		lines.replaceAll(line -> line.startsWith("positions=") ? "positions=org.acme.Baz:x:1" : line);
		Files.write(view.file().toPath(), lines, StandardCharsets.UTF_8);

		Assert.assertThrows(IOException.class, () -> this.library.readView(view.file()));
		Assert.assertFalse(this.library.listViews().get(0).readable());
	}

	@Test
	public void aSaveLeavesOnlyTheViewAndItsThumbnailInTheFolder() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "Clean", "", SNAPSHOT, THUMBNAIL);
		this.library.saveView(CONTAINER, "Clean", "again", SNAPSHOT, THUMBNAIL);
		this.library.renameView(view, "Cleaner");

		try (Stream<Path> names = Files.list(view.file().getParentFile().toPath())) {
			Assert.assertEquals(2, names.count());
		}
	}

	@Test
	public void aFailedRenameLeavesTheViewAsItWas() throws IOException {
		SavedView view = this.library.saveView(CONTAINER, "Before", "kept", SNAPSHOT, THUMBNAIL);
		File target = this.library.saveView(CONTAINER, "After", "", SNAPSHOT, null).file();
		Assert.assertTrue(target.delete());
		// The thumbnail cannot move onto a folder that holds something.
		Path blocker = Files.createDirectory(Path.of(target.getPath().replaceAll("\\.cow$", ".png")));
		Files.createFile(blocker.resolve("inside"));

		Assert.assertThrows(IOException.class, () -> this.library.renameView(view, "After"));

		Assert.assertTrue(view.file().isFile());
		Assert.assertTrue(view.thumbnail().isFile());
		Assert.assertFalse(target.exists());
		Assert.assertEquals("kept", this.library.readView(view.file()).description());
	}
}
