/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Plain base for a {@link SupervisorTabDescriptor} implementer - not a
 * Covalent type. The three accessors of the toolbar functions
 * ({@link #recordActionKey()}, {@link #zoomSetScaleActionKey()} and
 * {@link #zoomCurrentScaleActionKey()}) throw {@link UnsupportedOperationException}
 * by default, while {@link #railEntry()} and {@link #supportedToolbarFunctions()}
 * stay abstract and the other accessors keep the defaults of
 * {@link SupervisorTabDescriptor}. A function's own descriptor extends this
 * class and overrides only the accessors matching what it declares in
 * {@link #supportedToolbarFunctions()}. The shared toolbar always consults
 * {@link #supportedToolbarFunctions()} first and only calls the accessor for
 * a declared {@link ToolbarFunction}, so the exception path only fires on a
 * genuine programming error, never in normal operation.
 */
public abstract class SupervisorTabDescriptorAdapter implements SupervisorTabDescriptor {

	/** Creates a descriptor adapter, for subclasses. */
	public SupervisorTabDescriptorAdapter() {
		// Nothing to initialize.
	}

	@Override
	public ActionKey recordActionKey() {
		throw new UnsupportedOperationException(ToolbarFunction.RECORD_TOGGLE + " not supported by this descriptor");
	}

	@Override
	public ActionKey zoomSetScaleActionKey() {
		throw new UnsupportedOperationException(ToolbarFunction.ZOOM_SLIDER + " not supported by this descriptor");
	}

	@Override
	public ActionKey zoomCurrentScaleActionKey() {
		throw new UnsupportedOperationException(ToolbarFunction.ZOOM_SLIDER + " not supported by this descriptor");
	}
}
