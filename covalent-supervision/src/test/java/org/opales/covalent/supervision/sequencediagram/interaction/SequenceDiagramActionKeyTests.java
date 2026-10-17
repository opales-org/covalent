/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.supervision.sequencediagram.interaction.SequenceDiagramActionKey.Role;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Verifies the structural equality of per-session action keys: two keys are equal when they name
 * the same session and the same role.
 */
public class SequenceDiagramActionKeyTests {

	private static final class Session implements ViewKey {
		@Override
		public String toString() {
			return "session";
		}
	}

	@Test
	public void keysOfSameSessionAndRoleAreEqualWithSameHash() {
		ViewKey session = new Session();
		SequenceDiagramActionKey one = new SequenceDiagramActionKey(session, Role.Close);
		SequenceDiagramActionKey two = new SequenceDiagramActionKey(session, Role.Close);
		Assert.assertEquals(one, two);
		Assert.assertEquals(one.hashCode(), two.hashCode());
		Assert.assertSame(session, one.getSessionKey());
		Assert.assertEquals(Role.Close, one.getRole());
	}

	@Test
	public void keysOfDifferentRolesOrSessionsDiffer() {
		ViewKey session = new Session();
		SequenceDiagramActionKey key = new SequenceDiagramActionKey(session, Role.Close);
		Assert.assertNotEquals(key, new SequenceDiagramActionKey(session, Role.Recenter));
		Assert.assertNotEquals(key, new SequenceDiagramActionKey(new Session(), Role.Close));
	}

	@Test
	public void keyIsNotEqualToNullOrToAnotherType() {
		SequenceDiagramActionKey key = new SequenceDiagramActionKey(new Session(), Role.Close);
		Assert.assertEquals(key, key);
		Assert.assertNotEquals(key, null);
		Assert.assertNotEquals(key, "key");
	}

	@Test
	public void toStringNamesSessionAndRole() {
		Assert.assertEquals("SequenceDiagramActionKey[session, Snapshot]",
				new SequenceDiagramActionKey(new Session(), Role.Snapshot).toString());
	}
}
