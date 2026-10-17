/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi;

import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;

/**
 * {@link SupervisorSettings} keeps the supervisor's settings in the user's preferences. The tests use keys of
 * their own and remove them afterwards, so the settings of the user running them are left untouched.
 */
public class SupervisorSettingsTests {

	private static final Preferences STORE = Preferences.userRoot().node("org/opales/covalent/supervisor");

	private static final String KEY = "settings-tests.key";

	private static final String ELEMENT = "settings-tests.element";

	private static final String PANEL = "settings-tests.panel";

	private enum Sample {
		FIRST, SECOND
	}

	@Before
	@After
	public void clean() throws BackingStoreException {
		STORE.remove(KEY);
		STORE.remove("color." + SupervisorTheme.LIGHT.name() + "." + ELEMENT);
		STORE.remove("color." + SupervisorTheme.DARK.name() + "." + ELEMENT);
		STORE.remove("panel." + PANEL + ".collapsed");
		if (STORE.nodeExists(KEY)) {
			STORE.node(KEY).removeNode();
		}
		STORE.flush();
	}

	@Test
	public void aBooleanIsStoredAndTheDefaultAnswersWhenNone() {
		Assert.assertTrue(SupervisorSettings.getBoolean(KEY, true));
		Assert.assertFalse(SupervisorSettings.getBoolean(KEY, false));

		SupervisorSettings.putBoolean(KEY, true);
		Assert.assertTrue(SupervisorSettings.getBoolean(KEY, false));
		SupervisorSettings.putBoolean(KEY, false);

		Assert.assertFalse(SupervisorSettings.getBoolean(KEY, true));
	}

	@Test
	public void aDoubleIsStoredAndTheDefaultAnswersWhenNone() {
		Assert.assertEquals(1.5, SupervisorSettings.getDouble(KEY, 1.5), 0.0);

		SupervisorSettings.putDouble(KEY, 42.25);

		Assert.assertEquals(42.25, SupervisorSettings.getDouble(KEY, 1.5), 0.0);
	}

	@Test
	public void aDoubleThatCannotBeReadAnswersTheDefault() {
		STORE.put(KEY, "not a number");

		Assert.assertEquals(7.0, SupervisorSettings.getDouble(KEY, 7.0), 0.0);
	}

	@Test
	public void aTextIsStoredAndNoneAnswersNull() {
		Assert.assertNull(SupervisorSettings.getString(KEY));

		SupervisorSettings.putString(KEY, "C:\\some folder\\ünïcode");

		Assert.assertEquals("C:\\some folder\\ünïcode", SupervisorSettings.getString(KEY));
	}

	@Test
	public void anEnumIsStoredByNameAndTheDefaultAnswersWhenNoneOrUnknown() {
		Assert.assertEquals(Sample.FIRST, SupervisorSettings.getEnum(KEY, Sample.FIRST));

		SupervisorSettings.putEnum(KEY, Sample.SECOND);
		Assert.assertEquals(Sample.SECOND, SupervisorSettings.getEnum(KEY, Sample.FIRST));
		Assert.assertEquals("SECOND", SupervisorSettings.getString(KEY));

		SupervisorSettings.putString(KEY, "NO_SUCH_CONSTANT");
		Assert.assertEquals(Sample.FIRST, SupervisorSettings.getEnum(KEY, Sample.FIRST));
	}

	@Test
	public void aListOfTextsKeepsItsOrderAndItsItems() {
		List<String> values = List.of("b=B", "", "a b", "ünï", "x".repeat(3000));

		SupervisorSettings.putStringList(KEY, values);

		Assert.assertEquals(values, SupervisorSettings.getStringList(KEY));
	}

	@Test
	public void aListNeverStoredIsAbsentButAnEmptyOneIsNot() {
		Assert.assertNull(SupervisorSettings.getStringList(KEY));

		SupervisorSettings.putStringList(KEY, List.of());

		Assert.assertEquals(List.of(), SupervisorSettings.getStringList(KEY));
	}

	@Test
	public void storingAListReplacesTheOneBeforeWhateverItsLength() {
		SupervisorSettings.putStringList(KEY, List.of("1", "2", "3", "4"));

		SupervisorSettings.putStringList(KEY, List.of("only"));

		Assert.assertEquals(List.of("only"), SupervisorSettings.getStringList(KEY));
	}

	@Test
	public void aListIsReadUpToTheFirstMissingItem() throws BackingStoreException {
		SupervisorSettings.putStringList(KEY, List.of("a", "b", "c"));
		STORE.node(KEY).remove("1");

		Assert.assertEquals(List.of("a"), SupervisorSettings.getStringList(KEY));
	}

	@Test
	public void aColorIsStoredPerThemeAndElement() {
		Assert.assertNull(SupervisorSettings.getColor(SupervisorTheme.LIGHT, ELEMENT));

		SupervisorSettings.putColor(SupervisorTheme.LIGHT, ELEMENT, "#112233ff");
		SupervisorSettings.putColor(SupervisorTheme.DARK, ELEMENT, "#aabbccdd");

		Assert.assertEquals("#112233ff", SupervisorSettings.getColor(SupervisorTheme.LIGHT, ELEMENT));
		Assert.assertEquals("#aabbccdd", SupervisorSettings.getColor(SupervisorTheme.DARK, ELEMENT));
		Assert.assertNull(SupervisorSettings.getColor(SupervisorTheme.LIGHT, ELEMENT + ".other"));
	}

	@Test
	public void removingColorsForgetsThoseOfTheThemeOnly() {
		SupervisorSettings.putColor(SupervisorTheme.LIGHT, ELEMENT, "#112233ff");
		SupervisorSettings.putColor(SupervisorTheme.DARK, ELEMENT, "#aabbccdd");

		SupervisorSettings.removeColors(SupervisorTheme.LIGHT, List.of(ELEMENT));

		Assert.assertNull(SupervisorSettings.getColor(SupervisorTheme.LIGHT, ELEMENT));
		Assert.assertEquals("#aabbccdd", SupervisorSettings.getColor(SupervisorTheme.DARK, ELEMENT));
	}

	@Test
	public void aPanelIsExpandedUnlessLeftFolded() {
		Assert.assertFalse(SupervisorSettings.isPanelCollapsed(PANEL));

		SupervisorSettings.setPanelCollapsed(PANEL, true);
		Assert.assertTrue(SupervisorSettings.isPanelCollapsed(PANEL));
		SupervisorSettings.setPanelCollapsed(PANEL, false);

		Assert.assertFalse(SupervisorSettings.isPanelCollapsed(PANEL));
	}

	@Test
	public void whatIsStoredIsSeenByAnotherReaderOfTheSameStore() {
		SupervisorSettings.putString(KEY, "shared");

		Assert.assertEquals("shared", STORE.get(KEY, null));
	}
}
