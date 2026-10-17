/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceReconciler.InteractionKind;
import org.opales.covalent.supervision.sequencediagram.SequenceLog;
import org.opales.covalent.supervision.sequencediagram.view.TimelineStyle;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.ThreadActivity;

/**
 * Where the code builds a text's key from the name of an enum value, that value has its text in both
 * languages: a value added without its text would show its bare key, or fail, only on screen.
 */
public class TextBundlesEnumKeysTests {

	private static final String ROOT = "/org/opales/covalent/supervision/";

	private static Properties load(final String bundle, final String suffix) throws IOException {
		Properties texts = new Properties();
		try (InputStream stream = TextBundlesEnumKeysTests.class
				.getResourceAsStream(ROOT + bundle + "/messages" + suffix + ".properties")) {
			Assert.assertNotNull(bundle + suffix, stream);
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				texts.load(reader);
			}
		}
		return texts;
	}

	private static void check(final List<String> missing, final String bundle, final String prefix,
			final String suffix, final Enum<?>[] values) throws IOException {
		for (String language : new String[] { "", "_fr" }) {
			Properties texts = load(bundle, language);
			for (Enum<?> value : values) {
				String key = prefix + value.name() + suffix;
				if (!texts.containsKey(key)) {
					missing.add(bundle + "/messages" + language + ": " + key);
				}
			}
		}
	}

	@Test
	public void everyValueBehindAComposedKeyHasItsTexts() throws IOException {
		List<String> missing = new ArrayList<>();
		check(missing, "cockpit/view/impl", "command.", "", LifecycleCommand.values());
		check(missing, "cockpit/view/impl", "all.", "", LifecycleCommand.values());
		check(missing, "cockpit/view/impl", "state.", "", ComponentState.values());
		check(missing, "cockpit/view/impl", "status.", "", ComponentStatus.values());
		check(missing, "componentdiagram/view/impl", "status.", "", ComponentStatus.values());
		check(missing, "componentdiagram/view/impl", "role.", "", ComponentPortRole.values());
		check(missing, "componentdiagram/view", "color.", "", PortColorKey.values());
		check(missing, "componentview/sequence", "details.call.", "", InteractionKind.values());
		check(missing, "componentview/sequence", "details.return.", "", InteractionKind.values());
		check(missing, "sequencediagram/view", "timelineStyle.", "", TimelineStyle.values());
		check(missing, "sequencediagram/view/impl", "logFormat.", "", SequenceLog.Format.values());
		check(missing, "supervisionhmi/view/impl", "logFormat.", "", SequenceLog.Format.values());
		check(missing, "supervisionhmi/view/impl", "rail.", "", RailEntry.values());
		check(missing, "threads/view/impl", "activity.", "", ThreadActivity.values());
		check(missing, "threads/view/impl", "contention.", "", Contention.values());

		Assert.assertEquals("Texts missing for enum values", List.of(), missing);
	}

	@Test
	public void everyStatusColorHasItsBadgeText() throws IOException {
		List<String> missing = new ArrayList<>();
		for (PortColorKey key : PortColorKey.values()) {
			if (key.name().startsWith("STATUS_")) {
				for (String language : new String[] { "", "_fr" }) {
					String textKey = "badge." + key.name().substring("STATUS_".length());
					if (!load("supervisionhmi/view/impl", language).containsKey(textKey)) {
						missing.add(language + ": " + textKey);
					}
				}
			}
		}

		Assert.assertEquals("Badge texts missing", List.of(), missing);
	}
}
