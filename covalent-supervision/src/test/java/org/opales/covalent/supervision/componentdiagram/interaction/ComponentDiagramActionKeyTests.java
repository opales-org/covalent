/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies that a per-tab action key has structural equality on its tab key and role, and a readable description.
 */
public class ComponentDiagramActionKeyTests {

	@Test
	public void keysOfTheSameTabAndRoleAreEqual() {
		ComponentDiagramSessionKey tab = new ComponentDiagramSessionKey();
		ComponentDiagramActionKey a = new ComponentDiagramActionKey(tab, ComponentDiagramActionKey.Role.RecordToggle);
		ComponentDiagramActionKey b = new ComponentDiagramActionKey(tab, ComponentDiagramActionKey.Role.RecordToggle);
		Assert.assertEquals(a, b);
		Assert.assertEquals(a, a);
		Assert.assertEquals(a.hashCode(), b.hashCode());
		Assert.assertSame(tab, a.getSessionKey());
		Assert.assertEquals(ComponentDiagramActionKey.Role.RecordToggle, a.getRole());
	}

	@Test
	public void keysOfAnotherTabOrRoleAreDistinct() {
		ComponentDiagramSessionKey tab = new ComponentDiagramSessionKey();
		ComponentDiagramActionKey a = new ComponentDiagramActionKey(tab, ComponentDiagramActionKey.Role.RecordToggle);
		Assert.assertNotEquals(a, new ComponentDiagramActionKey(new ComponentDiagramSessionKey(),
				ComponentDiagramActionKey.Role.RecordToggle));
		Assert.assertNotEquals(a, new ComponentDiagramActionKey(tab, ComponentDiagramActionKey.Role.RenameTab));
		Assert.assertNotEquals(a, "other");
		Assert.assertNotEquals(a, null);
	}

	@Test
	public void descriptionNamesTheTabAndTheRole() {
		ComponentDiagramSessionKey tab = new ComponentDiagramSessionKey();
		String text = new ComponentDiagramActionKey(tab, ComponentDiagramActionKey.Role.RenameTab).toString();
		Assert.assertTrue(text, text.contains(tab.toString()));
		Assert.assertTrue(text, text.contains("RenameTab"));
	}
}
