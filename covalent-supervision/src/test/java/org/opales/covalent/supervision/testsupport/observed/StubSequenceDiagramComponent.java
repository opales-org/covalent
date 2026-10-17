/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Deployed in the SUPERVISION container (not the observed one) alongside {@code
 * ComponentDiagramControllerImpl}, standing in for the not-yet-implemented real {@code
 * SequenceDiagram} function so {@code startRecording}/{@code stopRecording} pass-through can be
 * tested without a surrogate-proxy "not bound" failure.
 */
@Component
public interface StubSequenceDiagramComponent {

}
