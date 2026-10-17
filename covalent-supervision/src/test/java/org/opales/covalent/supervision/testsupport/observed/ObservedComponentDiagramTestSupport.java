/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramControllerServices;
import org.opales.covalent.supervision.componentdiagram.controller.impl.ComponentDiagramControllerImpl;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEvents;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelReadServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.componentdiagram.model.impl.ComponentDiagramModelImpl;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerBridge;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyControllerServices;
import org.opales.covalent.supervision.exclusionpolicy.controller.impl.ExclusionPolicyControllerImpl;
import org.opales.covalent.supervision.exclusionpolicy.model.impl.ExclusionPolicyModelImpl;
import org.opales.covalent.supervision.stereotypepolicy.controller.impl.StereotypePolicyControllerImpl;
import org.opales.covalent.supervision.stereotypepolicy.model.impl.StereotypePolicyModelImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.StubSequenceDiagramComponentImpl;

/**
 * Shared harness for {@code ComponentDiagramControllerImpl} tests: two fully isolated
 * {@code CovalentContainer}s - {@link #observed}, standing in for whichever application
 * container is being supervised, and {@link #supervision}, where the MVIC stack under test
 * actually runs - wired exactly like {@code SupervisionApplication}, minus the View/
 * Interaction layers (JavaFX, irrelevant to this logic) and minus {@code SequenceDiagram}/
 * {@code DependencyManagement} (not needed to exercise {@code ComponentDiagram}; {@code
 * ComponentDiagramControllerImpl.useSequenceDiagramServices} is only ever called from {@code
 * startRecording}/{@code stopRecording}, so its surrogate proxy is never invoked by anything
 * tested here).
 * <p>
 * Subclasses deploy their own fixtures into {@link #observed} and call {@link #startBoth()} -
 * the only way to publish the fixtures' full initial topology in one step, since {@code
 * ObservedContainerBridge.connect(...)} is silent and {@code ComponentDiagramControllerImpl}
 * only resynchronizes from {@code onContainerConnected()}. {@code ComponentDiagramControllerImpl} opens a tab only through the explicit
 * {@code openNewSession()} command, issued by the Interaction when the user opens
 * a tab, not from its own {@code @Activation} - {@link #startBoth()} issues it
 * itself, once, so every test here can assume the one tab its own assertions are
 * written against.
 */
public abstract class ObservedComponentDiagramTestSupport implements ComponentDiagramEvents {

	/** The observed container's name: only a managed container can be observed. */
	private static final String OBSERVED = "observed-component-diagram-tests";

	protected CovalentContainer observed;

	private ComponentDiagramModelReadServices componentDiagramModelReadServices;

	protected CovalentContainer supervision;

	protected RawComponentsServices supervisionRcs;

	protected ComponentDiagramControllerServices componentDiagramControllerServices;

	protected ExclusionPolicyControllerServices exclusionPolicyControllerServices;

	private ObservedContainerControl observedContainerControl;

	protected StubSequenceDiagramComponentImpl stubSequenceDiagram;

	/**
	 * The one tab's identity, captured from {@link #onSessionCreated(ViewKey)} when
	 * {@link #startBoth()} issues the one {@code openNewSession()} call every test here
	 * assumes - this makes that assumption explicit instead of hardcoding or guessing a
	 * session identity.
	 */
	private ViewKey sessionKey;

	private final List<ComponentDiagramTopology> topologyHistory = new CopyOnWriteArrayList<>();

	private final List<Set<Class<?>>> selectionHistory = new CopyOnWriteArrayList<>();

	private final List<List<Class<?>>> focusPathHistory = new CopyOnWriteArrayList<>();

	private final List<Class<?>> flashHistory = new CopyOnWriteArrayList<>();

	private final List<Object[]> portActivityHistory = new CopyOnWriteArrayList<>();

	private final List<Object[]> dependenciesVisibilityHistory = new CopyOnWriteArrayList<>();

	@Before
	public void setUpObservedComponentDiagram() {
		this.observed = Covalent.managedContainer(OBSERVED);
		this.observed.getParameters().setSynchronousEvents(true);

		this.supervision = Covalent.container();
		this.supervision.getParameters().setSynchronousEvents(true);

		this.supervisionRcs = this.supervision.rcs();
		// Registered - and every raw port below opened - under this base class's own type,
		// not this.getClass(): the raw-registration requirement resolves an implicit caller
		// type by where the calling code is declared, which is always this base class for
		// every @Before/helper method here, regardless of which concrete test subclass is
		// actually running.
		this.supervisionRcs.registerComponent(ObservedComponentDiagramTestSupport.class, this);
		this.supervisionRcs.subscribe(ObservedComponentDiagramTestSupport.class, ComponentDiagramEvents.class, this);

		this.supervision.deployComponents(
				ObservedContainerBridge.class,
				ExclusionPolicyModelImpl.class,
				ExclusionPolicyControllerImpl.class,
				StereotypePolicyModelImpl.class,
				StereotypePolicyControllerImpl.class,
				ComponentDiagramModelImpl.class,
				ComponentDiagramControllerImpl.class,
				StubSequenceDiagramComponentImpl.class);
	}

	@After
	public void tearDownObservedComponentDiagram() {
		if (this.supervision != null) {
			this.supervision.dispose();
		}
		Covalent.disposeManagedContainer(OBSERVED);
	}

	/**
	 * Starts {@link #observed} first (so its fixtures are fully up before anything reads them),
	 * then {@link #supervision} (whose {@code ComponentDiagramControllerImpl} activates against
	 * nothing observed yet - see its {@code activated} guard), then connects the two, which is
	 * what actually triggers the first real topology build.
	 */
	protected void startBoth() {
		this.observed.start();
		this.supervision.start();

		this.componentDiagramControllerServices =
				this.supervisionRcs.useServices(ComponentDiagramControllerServices.class);
		this.exclusionPolicyControllerServices =
				this.supervisionRcs.useServices(ExclusionPolicyControllerServices.class);
		this.observedContainerControl = this.supervisionRcs.useServices(ObservedContainerControl.class);
		this.componentDiagramModelReadServices = this.supervisionRcs.useServices(ComponentDiagramModelReadServices.class);
		this.stubSequenceDiagram = this.supervisionRcs.useServices(RegistryReadServices.class)
				.getComponentInstance(StubSequenceDiagramComponent.class);

		// Session creation moved out of @Activation (multi-tab support) - open the one tab
		// every test here assumes, before connecting, so its first rebuild already publishes
		// into that session.
		this.componentDiagramControllerServices.openNewSession();
		this.observedContainerControl.connect(this.observed);
	}

	protected void reconnect() {
		this.observedContainerControl.connect(this.observed);
	}

	protected void disconnectObserved() {
		this.observedContainerControl.disconnect();
	}

	protected ComponentDiagramTopology lastTopology() {
		return this.topologyHistory.isEmpty() ? null : this.topologyHistory.get(this.topologyHistory.size() - 1);
	}

	protected int topologyChangeCount() {
		return this.topologyHistory.size();
	}

	protected Set<Class<?>> lastSelection() {
		return this.selectionHistory.isEmpty() ? null : this.selectionHistory.get(this.selectionHistory.size() - 1);
	}

	protected List<Class<?>> lastFocusPath() {
		return this.focusPathHistory.isEmpty() ? null : this.focusPathHistory.get(this.focusPathHistory.size() - 1);
	}

	protected int focusPathChangeCount() {
		return this.focusPathHistory.size();
	}

	protected Class<?> lastFlashed() {
		return this.flashHistory.isEmpty() ? null : this.flashHistory.get(this.flashHistory.size() - 1);
	}

	protected List<Object[]> portActivityHistory() {
		return this.portActivityHistory;
	}

	protected List<Object[]> dependenciesVisibilityHistory() {
		return this.dependenciesVisibilityHistory;
	}

	/**
	 * The one tab's identity - see {@link #sessionKey}. {@code null} until
	 * {@link #startBoth()} has run (the first tab only opens once {@code
	 * supervision} itself starts).
	 */
	protected ComponentDiagramModelReadServices componentDiagramModelReadServices() {
		return this.componentDiagramModelReadServices;
	}

	protected ViewKey sessionKey() {
		return this.sessionKey;
	}

	// --- ComponentDiagramEvents ---

	@Override
	public void onSessionCreated(final ViewKey sessionKey) {
		this.sessionKey = sessionKey;
	}

	@Override
	public void onSessionClosed(final ViewKey sessionKey) {
		// No test here closes the one tab it runs against.
	}

	@Override
	public void onTopologyChanged(final ViewKey sessionKey) {
		this.topologyHistory.add(this.componentDiagramModelReadServices.getTopology(sessionKey));
	}

	@Override
	public void onSelectionChanged(final ViewKey sessionKey, final Set<Class<?>> selectedComponentTypes) {
		this.selectionHistory.add(selectedComponentTypes);
	}

	@Override
	public void onPortActivityChanged(final Class<?> componentType, final Class<?> portType,
			final ComponentPortKind portKind, final boolean active) {
		this.portActivityHistory.add(new Object[] { componentType, portType, portKind, active });
	}

	@Override
	public void onLiveStructureChanged() {
		// Not observed by these tests.
	}

	@Override
	public void onActivitySampled(final long sampleTimeMillis,
			final java.util.Map<Class<?>, org.opales.covalent.supervision.componentdiagram.model.ComponentActivity> activities) {
		// Not observed by these tests.
	}

	@Override
	public void onComponentStatusesChanged() {
		// Not observed by any test here.
	}

	@Override
	public void onDependenciesVisibilityChanged(final ViewKey sessionKey, final Class<?> componentType,
			final boolean visible) {
		this.dependenciesVisibilityHistory.add(new Object[] { componentType, visible });
	}

	@Override
	public void onFocusPathChanged(final ViewKey sessionKey, final List<Class<?>> focusPath) {
		this.focusPathHistory.add(focusPath);
	}

	@Override
	public void onFlashComponentChanged(final ViewKey sessionKey, final Class<?> componentType) {
		this.flashHistory.add(componentType);
	}

	@Override
	public void onLinkHighlightRequested(final ViewKey sessionKey, final Class<?> sourceType,
			final Class<?> targetType) {
		// Not recorded: no test observes it.
	}
}
