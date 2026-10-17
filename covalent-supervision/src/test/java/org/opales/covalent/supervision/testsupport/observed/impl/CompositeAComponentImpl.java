/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.testsupport.observed.CompositeAComponent;

@Implementation
public class CompositeAComponentImpl implements CompositeAComponent {

	@Implementation
	private LeafAProviderComponentImpl leafAProvider;

	@Implementation
	private LeafAUserComponentImpl leafAUser;
}
