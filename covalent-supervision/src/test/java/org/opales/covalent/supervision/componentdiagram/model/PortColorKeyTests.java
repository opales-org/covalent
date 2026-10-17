/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies that every palette entry is listed under a label and a section of the colors panel.
 */
public class PortColorKeyTests {

	@Test
	public void everyEntryHasALabelAndACategory() {
		for (PortColorKey key : PortColorKey.values()) {
			Assert.assertFalse(key.name(), key.getLabel().isBlank());
			Assert.assertFalse(key.name(), key.getCategory().isBlank());
		}
	}

	@Test
	public void entriesAreGroupedUnderTheirSection() {
		Assert.assertEquals("Status badge", PortColorKey.STATUS_FAILED.getCategory());
		Assert.assertEquals("Failed", PortColorKey.STATUS_FAILED.getLabel());
		Assert.assertEquals("Interaction", PortColorKey.SELECTION.getCategory());
	}
}
