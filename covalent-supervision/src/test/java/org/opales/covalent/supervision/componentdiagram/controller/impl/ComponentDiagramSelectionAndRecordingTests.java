/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.testsupport.observed.CompositeAComponent;
import org.opales.covalent.supervision.testsupport.observed.CompositeBComponent;
import org.opales.covalent.supervision.testsupport.observed.CompositionChildComponent;
import org.opales.covalent.supervision.testsupport.observed.CompositionParentComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafBProviderComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeAComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeBComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositionParentComponentImpl;

/**
 * Covers {@code ComponentDiagramControllerServices}'s selection/recording/navigation commands -
 * everything in {@code ComponentDiagramControllerImpl} that {@link ComponentDiagramTopologyTests}
 * does not, since that class only exercises topology shape.
 */
public class ComponentDiagramSelectionAndRecordingTests extends ObservedComponentDiagramTestSupport {

	@Test
	public void selectComponentsReplacesTheWholeSelection() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.selectComponents(this.sessionKey(), Set.of(CompositeAComponent.class));
		Assert.assertEquals(Set.of(CompositeAComponent.class), this.lastSelection());

		this.componentDiagramControllerServices
				.selectComponents(this.sessionKey(), Set.of(CompositeAComponent.class, CompositeBComponent.class));
		Assert.assertEquals(Set.of(CompositeAComponent.class, CompositeBComponent.class), this.lastSelection());
	}

	@Test
	public void excludingASelectedComponentRemovesItFromTheSelection() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
		this.componentDiagramControllerServices
				.selectComponents(this.sessionKey(), Set.of(CompositeAComponent.class, CompositeBComponent.class));

		this.exclusionPolicyControllerServices.addExcludedComponent(CompositeAComponent.class);

		Assert.assertEquals(Set.of(CompositeBComponent.class), this.lastSelection());
	}

	@Test
	public void excludingAnUnselectedComponentLeavesTheSelectionAlone() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
		this.componentDiagramControllerServices.selectComponents(this.sessionKey(), Set.of(CompositeBComponent.class));

		this.exclusionPolicyControllerServices.addExcludedComponent(CompositeAComponent.class);

		Assert.assertEquals(Set.of(CompositeBComponent.class), this.lastSelection());
	}

	@Test
	public void toggleComponentSelectionAddsThenRemoves() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.toggleComponentSelection(this.sessionKey(), CompositeAComponent.class);
		Assert.assertEquals(Set.of(CompositeAComponent.class), this.lastSelection());

		this.componentDiagramControllerServices.toggleComponentSelection(this.sessionKey(), CompositeBComponent.class);
		Assert.assertEquals(Set.of(CompositeAComponent.class, CompositeBComponent.class), this.lastSelection());

		this.componentDiagramControllerServices.toggleComponentSelection(this.sessionKey(), CompositeAComponent.class);
		Assert.assertEquals(Set.of(CompositeBComponent.class), this.lastSelection());
	}

	@Test
	public void toggleDependenciesVisibilityForwardsVerbatimToTheModel() {
		this.observed.deployComponents(CompositeAComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.toggleDependenciesVisibility(this.sessionKey(), CompositeAComponent.class, true);
		Assert.assertEquals(1, this.dependenciesVisibilityHistory().size());
		Assert.assertArrayEquals(new Object[] { CompositeAComponent.class, true },
				this.dependenciesVisibilityHistory().get(0));

		this.componentDiagramControllerServices.toggleDependenciesVisibility(this.sessionKey(), CompositeAComponent.class, false);
		Assert.assertArrayEquals(new Object[] { CompositeAComponent.class, false },
				this.dependenciesVisibilityHistory().get(1));
	}

	@Test
	public void navigateToComponentSetsFocusPathToTheTargetsParentAndFlashesIt() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);

		// From inside CompositeA, navigate to LeafBProviderComponent (CompositeB's child,
		// reached through the external port built in ComponentDiagramTopologyTests).
		this.componentDiagramControllerServices.navigateToComponent(this.sessionKey(), LeafBProviderComponent.class);

		Assert.assertEquals(List.of(CompositeBComponent.class), this.lastFocusPath());
		Assert.assertEquals(LeafBProviderComponent.class, this.lastFlashed());
		Assert.assertEquals(Set.of(LeafBProviderComponent.class), this.lastTopology().getComponentTypes());
	}

	@Test
	public void showCompositeSetsTheFocusPathToTheCompositeItselfAndSelectsIt() {
		this.observed.deployComponents(CompositionParentComponentImpl.class);
		this.startBoth();

		this.componentDiagramControllerServices.showComposite(this.sessionKey(), CompositionParentComponent.class);

		Assert.assertEquals(List.of(CompositionParentComponent.class), this.lastFocusPath());
		Assert.assertEquals(Set.of(CompositionChildComponent.class), this.lastTopology().getComponentTypes());
		Assert.assertEquals(Set.of(CompositionParentComponent.class), this.lastSelection());
	}

	@Test
	public void showCompositeIgnoresNull() {
		this.observed.deployComponents(CompositionParentComponentImpl.class);
		this.startBoth();
		List<Class<?>> before = this.lastFocusPath();

		this.componentDiagramControllerServices.showComposite(this.sessionKey(), null);

		Assert.assertEquals(before, this.lastFocusPath());
	}

	@Test
	public void navigateToComponentClearsSelectionLikeAnyFocusChange() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
		this.componentDiagramControllerServices.selectComponents(this.sessionKey(), Set.of(CompositeAComponent.class));

		this.componentDiagramControllerServices.navigateToComponent(this.sessionKey(), LeafBProviderComponent.class);

		Assert.assertEquals(Set.of(), this.lastSelection());
	}

	@Test
	public void navigatingToAClassWithNoMetamodelStillResetsToTopLevelAndFlashesItLiterally() {
		// componentType(Class) falls back to the argument itself when metamodelReadServices
		// has no type for it (rather than null, which would mean "componentClass was null"),
		// so navigateToComponent(...) only truly no-ops on a literal null argument -
		// anything else, known or not, resolves to parentPathOf(...) (empty here, since
		// an unknown class has no metamodel parent chain either) and still flashes verbatim.
		this.observed.deployComponents(CompositeAComponentImpl.class);
		this.startBoth();
		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);

		this.componentDiagramControllerServices.navigateToComponent(this.sessionKey(), String.class);

		Assert.assertEquals(List.of(), this.lastFocusPath());
		Assert.assertEquals(String.class, this.lastFlashed());
	}

	@Test
	public void navigatingToALiteralNullComponentTypeIsANoOp() {
		this.observed.deployComponents(CompositeAComponentImpl.class);
		this.startBoth();
		int focusChangesBefore = this.focusPathChangeCount();

		this.componentDiagramControllerServices.navigateToComponent(this.sessionKey(), null);

		Assert.assertEquals(focusChangesBefore, this.focusPathChangeCount());
		Assert.assertNull(this.lastFlashed());
	}

	@Test
	public void startRecordingForwardsTheCurrentSelectionToSequenceDiagramServices() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
		this.componentDiagramControllerServices.selectComponents(this.sessionKey(), Set.of(CompositeAComponent.class));

		this.componentDiagramControllerServices.startRecording(this.sessionKey());

		Assert.assertEquals(1, this.stubSequenceDiagram.startRecordingCalls);
		Assert.assertTrue(this.stubSequenceDiagram.recording);
		Assert.assertEquals(Set.of(CompositeAComponent.class), this.stubSequenceDiagram.lastSelectedTypes);
	}

	@Test
	public void stopRecordingForwardsToSequenceDiagramServices() {
		this.startBoth();

		this.componentDiagramControllerServices.startRecording(this.sessionKey());
		this.componentDiagramControllerServices.stopRecording();

		Assert.assertEquals(1, this.stubSequenceDiagram.stopRecordingCalls);
		Assert.assertFalse(this.stubSequenceDiagram.recording);
	}

}
