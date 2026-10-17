/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramLayout;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersAccessorComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersAccessorComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersOfferorComponentImpl;

/**
 * A saved view always opens in a new tab named after it, with its selection,
 * the layout the View applies, and the view kept as the tab's own until the
 * tab is renamed away from the view's name; the components of a view of the
 * observed container that it no longer has are reported as missing.
 */
public class ComponentDiagramViewsTests extends ObservedComponentDiagramTestSupport {

	private static final String MISSING = "org.acme.Missing";

	private SavedView view(final String containerName) {
		ComponentDiagramSnapshot snapshot = new ComponentDiagramSnapshot(List.of(),
				Set.of(FlatParametersAccessorComponent.class.getName()),
				Map.of(FlatParametersOfferorComponent.class.getName(), new double[] { 10, 20 }, MISSING,
						new double[] { 30, 40 }),
				Set.of(FlatParametersOfferorComponent.class.getName()), 0.85);
		return new SavedView("Parameters", "the parameter link", containerName, Instant.now(), snapshot,
				new File("Parameters.cow"), new File("Parameters.png"), null);
	}

	@Test
	public void aViewOpensInANewTabNamedAfterIt() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class, FlatParametersAccessorComponentImpl.class);
		this.startBoth();
		ViewKey firstTab = this.sessionKey();
		SavedView view = this.view(this.observed.getName());

		this.componentDiagramControllerServices.openView(view);

		ViewKey newTab = this.sessionKey();
		Assert.assertNotEquals(firstTab, newTab);
		Assert.assertEquals("Parameters", this.componentDiagramModelReadServices().getSessionTitle(newTab));
		Assert.assertSame(view, this.componentDiagramControllerServices.getSessionView(newTab));
		ComponentDiagramLayout layout = this.componentDiagramModelReadServices().getInitialLayout(newTab);
		Assert.assertEquals(Set.of(FlatParametersAccessorComponent.class), layout.hiddenComponents());
		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class), layout.positions().keySet());
		Assert.assertEquals(0.85, layout.zoom(), 0.0);
		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class), this.lastSelection());
		Assert.assertNull(this.componentDiagramControllerServices.getSessionView(firstTab));
	}

	@Test
	public void aTabRenamedAwayFromItsViewNameIsNoLongerThatView() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class, FlatParametersAccessorComponentImpl.class);
		this.startBoth();
		SavedView view = this.view(this.observed.getName());
		this.componentDiagramControllerServices.openView(view);
		ViewKey tab = this.sessionKey();

		this.componentDiagramControllerServices.renameSession(tab, "Parameters");
		Assert.assertSame(view, this.componentDiagramControllerServices.getSessionView(tab));

		this.componentDiagramControllerServices.renameSession(tab, "Parameters, revised");
		Assert.assertNull(this.componentDiagramControllerServices.getSessionView(tab));
	}

	@Test
	public void theComponentsTheObservedContainerNoLongerHasAreMissing() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class, FlatParametersAccessorComponentImpl.class);
		this.startBoth();

		Assert.assertEquals(this.observed.getName(), this.componentDiagramControllerServices.getObservedContainerName());
		Assert.assertEquals(Set.of(MISSING),
				this.componentDiagramControllerServices.getMissingComponents(this.view(this.observed.getName())));
		Assert.assertEquals(Set.of(),
				this.componentDiagramControllerServices.getMissingComponents(this.view("Another container")));
	}
}
