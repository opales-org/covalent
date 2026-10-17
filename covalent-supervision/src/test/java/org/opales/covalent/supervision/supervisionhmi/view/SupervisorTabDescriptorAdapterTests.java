/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link SupervisorTabDescriptorAdapter} refuses the toolbar actions a tab does not support, naming the function
 * the tab would have had to declare.
 */
public class SupervisorTabDescriptorAdapterTests {

	private final SupervisorTabDescriptorAdapter adapter = new SupervisorTabDescriptorAdapter() {
		@Override
		public RailEntry railEntry() {
			return null;
		}

		@Override
		public Set<ToolbarFunction> supportedToolbarFunctions() {
			return Set.of();
		}
	};

	@Test
	public void recordingIsRefused() {
		UnsupportedOperationException refusal = Assert.assertThrows(UnsupportedOperationException.class,
				this.adapter::recordActionKey);

		Assert.assertTrue(refusal.getMessage(), refusal.getMessage().contains(ToolbarFunction.RECORD_TOGGLE.toString()));
	}

	@Test
	public void zoomingIsRefused() {
		UnsupportedOperationException setScale = Assert.assertThrows(UnsupportedOperationException.class,
				this.adapter::zoomSetScaleActionKey);
		UnsupportedOperationException currentScale = Assert.assertThrows(UnsupportedOperationException.class,
				this.adapter::zoomCurrentScaleActionKey);

		Assert.assertTrue(setScale.getMessage().contains(ToolbarFunction.ZOOM_SLIDER.toString()));
		Assert.assertTrue(currentScale.getMessage().contains(ToolbarFunction.ZOOM_SLIDER.toString()));
	}

	@Test
	public void theOtherAnswersKeepTheirDefaults() {
		Assert.assertEquals("", this.adapter.title());
		Assert.assertFalse(this.adapter.renamable());
		Assert.assertNull(this.adapter.renameActionKey());
		Assert.assertNull(this.adapter.closeActionKey());
		Assert.assertTrue(this.adapter.closable());
		Assert.assertNull(this.adapter.recordingStateActionKey());
	}
}
