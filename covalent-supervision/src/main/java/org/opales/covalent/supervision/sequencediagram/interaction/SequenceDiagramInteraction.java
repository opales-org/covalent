/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for SequenceDiagram's Interaction Controller. At its own
 * {@code @Activation} it registers the tool-wide actions; it mints and
 * disposes a fresh {@link SequenceDiagramActionKey} set per session, on
 * {@code SequenceDiagramEvents#onSessionCreated}/{@code #onSessionClosed}.
 */
@Component
public interface SequenceDiagramInteraction {

}
