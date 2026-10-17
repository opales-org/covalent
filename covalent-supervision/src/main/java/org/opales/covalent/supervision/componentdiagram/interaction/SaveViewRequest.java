/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

/**
 * What the save dialog confirms: the view's name and description.
 *
 * @param name        the view's name
 * @param description the view's description, empty if none
 */
public record SaveViewRequest(String name, String description) {
}
