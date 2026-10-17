/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Every English text bundle of the supervision has its French counterpart, with the same keys, and no French
 * text uses a placeholder its English text lacks: a missing translation or an invented {@code {1}} would only
 * show on screen. A French text may leave a placeholder out, as a singular form that needs no count does.
 */
public class TextBundlesTests {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

	private static List<Path> bundles(final String suffix) throws IOException, URISyntaxException {
		// every classpath root holding the package: test and main classes live in separate roots
		List<Path> found = new ArrayList<>();
		boolean fileRoot = false;
		Enumeration<URL> roots = TextBundlesTests.class.getClassLoader().getResources("org/opales/covalent/supervision");
		while (roots.hasMoreElements()) {
			URL root = roots.nextElement();
			if (!"file".equals(root.getProtocol())) {
				continue;
			}
			fileRoot = true;
			try (Stream<Path> files = Files.walk(Paths.get(root.toURI()))) {
				files.filter(file -> file.getFileName().toString().equals("messages" + suffix + ".properties"))
						.forEach(found::add);
			}
		}
		Assume.assumeTrue(fileRoot);
		found.sort(null);
		return found;
	}

	private static Properties load(final Path file) throws IOException {
		Properties texts = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
			texts.load(reader);
		}
		return texts;
	}

	private static TreeSet<String> placeholders(final String text) {
		TreeSet<String> found = new TreeSet<>();
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	@Test
	public void thereAreBundlesToCheck() throws Exception {
		Assert.assertFalse(bundles("").isEmpty());
	}

	@Test
	public void everyEnglishBundleHasItsFrenchOne() throws Exception {
		List<String> missing = new ArrayList<>();
		for (Path english : bundles("")) {
			if (!Files.exists(english.resolveSibling("messages_fr.properties"))) {
				missing.add(english.toString());
			}
		}

		Assert.assertEquals("English bundles without a French one", List.of(), missing);
	}

	@Test
	public void everyFrenchBundleHasItsEnglishOne() throws Exception {
		List<String> orphans = new ArrayList<>();
		for (Path french : bundles("_fr")) {
			if (!Files.exists(french.resolveSibling("messages.properties"))) {
				orphans.add(french.toString());
			}
		}

		Assert.assertEquals("French bundles without an English one", List.of(), orphans);
	}

	@Test
	public void bothLanguagesOfABundleHaveTheSameKeys() throws Exception {
		List<String> differences = new ArrayList<>();
		for (Path english : bundles("")) {
			Path french = english.resolveSibling("messages_fr.properties");
			if (!Files.exists(french)) {
				continue;
			}
			TreeSet<String> englishKeys = new TreeSet<>(load(english).stringPropertyNames());
			TreeSet<String> frenchKeys = new TreeSet<>(load(french).stringPropertyNames());
			TreeSet<String> onlyEnglish = new TreeSet<>(englishKeys);
			onlyEnglish.removeAll(frenchKeys);
			TreeSet<String> onlyFrench = new TreeSet<>(frenchKeys);
			onlyFrench.removeAll(englishKeys);
			if (!onlyEnglish.isEmpty() || !onlyFrench.isEmpty()) {
				differences.add(english.getParent().getFileName() + ": only English " + onlyEnglish
						+ ", only French " + onlyFrench);
			}
		}

		Assert.assertEquals("Bundles whose keys differ", List.of(), differences);
	}

	@Test
	public void aFrenchTextUsesNoPlaceholderTheEnglishOneLacks() throws Exception {
		List<String> differences = new ArrayList<>();
		for (Path english : bundles("")) {
			Path french = english.resolveSibling("messages_fr.properties");
			if (!Files.exists(french)) {
				continue;
			}
			Properties englishTexts = load(english);
			Properties frenchTexts = load(french);
			for (String key : englishTexts.stringPropertyNames()) {
				String frenchText = frenchTexts.getProperty(key);
				if (frenchText != null && !placeholders(englishTexts.getProperty(key)).containsAll(placeholders(frenchText))) {
					differences.add(english.getParent().getFileName() + ": " + key);
				}
			}
		}

		Assert.assertEquals("French texts with a placeholder the English one lacks", List.of(), differences);
	}

	@Test
	public void noTextIsEmpty() throws Exception {
		List<String> empty = new ArrayList<>();
		List<Path> all = new ArrayList<>(bundles(""));
		all.addAll(bundles("_fr"));
		for (Path file : all) {
			Properties texts = load(file);
			for (String key : texts.stringPropertyNames()) {
				if (texts.getProperty(key).isBlank()) {
					empty.add(file.getParent().getFileName() + "/" + file.getFileName() + ": " + key);
				}
			}
		}

		Assert.assertEquals("Empty texts", List.of(), empty);
	}
}
