/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Services;

/**
 * Mutator-only port of {@link ObservedContainerBridge}, separate from the
 * ten read/consume interfaces it also implements for exactly the same ISP
 * reason as {@code ExclusionPolicyControllerServices}: only {@code
 * ContainerManager}'s own {@code Interaction} - the sole component
 * that ever changes which container is observed - may call {@link
 * #connect(CovalentContainer)}/{@link #disconnect()}; {@code
 * ComponentDiagram}/{@code SequenceDiagram}/{@code DependencyManagement}
 * only ever read through the other ten interfaces.
 * <p>
 * Thread contract: the caller makes these calls one at a time ({@code ContainerManager}'s
 * {@code Interaction} serializes them). A switch is not atomic for the readers: while it goes on, some of the read
 * interfaces can already answer for the new container while the others still answer for none, and a reader that
 * runs then simply gets the next answer on its next call. A connection that fails midway can leave some read
 * interfaces connected to the failed target until the next call to {@link #connect(CovalentContainer)} overwrites
 * them.
 */
@Services
public interface ObservedContainerControl {

	/**
	 * Starts observing {@code target}: disconnects from whichever container
	 * was previously observed first, if any. Only a managed container can be
	 * observed: it reports its lifecycle, so it is left as soon as it is
	 * stopping or being disposed.
	 *
	 * @param target the managed container to observe from now on
	 * @throws IllegalArgumentException if {@code target} is not managed
	 */
	void connect(CovalentContainer target);

	/** Stops observing the currently observed container, if any. */
	void disconnect();

	/**
	 * Returns the name of the observed container.
	 *
	 * @return its name, {@code null} if none is observed
	 */
	String getObservedContainerName();
}
