/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramControllerServices;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramActionKey.Role;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramInteraction;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramPreferencesActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramToolActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ExportViewRequest;
import org.opales.covalent.supervision.componentdiagram.interaction.RenameViewRequest;
import org.opales.covalent.supervision.componentdiagram.interaction.SaveViewRequest;
import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEvents;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelReadServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.componentdiagram.view.ComponentDiagramViewServices;
import org.opales.covalent.supervision.componentdiagram.view.SaveViewForm;
import org.opales.covalent.supervision.componentdiagram.view.ViewLibraryContent;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.diagrampersistence.controller.DiagramPersistenceServices;
import org.opales.covalent.supervision.sequencediagram.controller.RecordingStateEvents;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * ComponentDiagram's Interaction Controller: registers this function's
 * genuinely tool-wide preference ActionKeys via {@code ActionProviderServices}
 * at its own {@code @Activation}, then mints and disposes a fresh {@link
 * ComponentDiagramActionKey} set per tab, on {@code
 * ComponentDiagramEvents#onSessionCreated}/{@code #onSessionClosed}, as
 * {@code SequenceDiagramInteractionImpl} does for its sessions; the
 * always-open first tab goes through the same {@code onSessionCreated} path
 * as any tab opened with "+ New diagram", since {@code
 * ComponentDiagramControllerImpl} mints it the same way. Never touches JavaFX.
 * Keeps no recording state of its own: whether a recording runs is asked to
 * {@code SequenceDiagram} each time it matters, so that a recording started
 * or stopped anywhere else leaves every tab right.
 */
@Implementation
public class ComponentDiagramInteractionImpl
		implements ComponentDiagramInteraction, ComponentDiagramEvents, RecordingStateEvents {

	/** Creates the interaction controller; its collaborators are injected by the container. */
	public ComponentDiagramInteractionImpl() {
		// Nothing to initialize.
	}

	private static final Logger log = Logger.getLogger(ComponentDiagramInteractionImpl.class.getName());

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private ComponentDiagramViewServices useComponentDiagramViewServices;

	@UsedServices
	private ComponentDiagramControllerServices useComponentDiagramControllerServices;

	@UsedServices
	private ComponentDiagramModelReadServices useComponentDiagramModelReadServices;

	@UsedServices
	private DiagramPersistenceServices useDiagramPersistenceServices;

	/**
	 * Tracked only so {@code @Passivation} can defensively retire whatever is still open. In the order the tabs
	 * opened, and safe to read from the threads that deliver the events.
	 */
	private final Set<ViewKey> openSessions = new CopyOnWriteArraySet<>();

	/** Per tab: whether it has components selected, which it would record. */
	private final Map<ViewKey, Boolean> selectionBySession = new ConcurrentHashMap<>();

	/**
	 * Registers the tool-wide preference and tool actions (auto-layout on hide, port colors, theme, new diagram,
	 * show link, open composite), applies the stored settings to the View, then opens the always-open first tab.
	 */
	@Activation
	public void registerPreferenceActions() {
		this.actionManagerServices.registerActionExecution(ComponentDiagramPreferencesActionKey.ToggleAutoLayoutOnHide,
				this::onToggleAutoLayoutOnHide, Boolean.class);
		this.actionManagerServices.registerActionExecution(ComponentDiagramPreferencesActionKey.SetPortColor,
				this::onSetPortColor, PortColorKey.class, String.class);
		this.actionManagerServices.registerActionExecution(ComponentDiagramPreferencesActionKey.SetTheme,
				this::onSetTheme, SupervisorTheme.class);
		this.actionManagerServices.registerActionExecution(ComponentDiagramPreferencesActionKey.ResetPortColors,
				this::onResetPortColors);
		this.actionManagerServices.registerActionExecution(ComponentDiagramToolActionKey.NewDiagram, this::onNewSession);
		this.actionManagerServices.registerActionExecution(ComponentDiagramToolActionKey.ShowLink, this::onShowLink,
				List.class);
		this.actionManagerServices.registerActionExecution(ComponentDiagramToolActionKey.OpenComposite,
				this::onOpenComposite, Class.class);
		this.useComponentDiagramViewServices.setAutoLayoutOnHide(
				SupervisorSettings.getBoolean(SupervisorSettings.AUTO_LAYOUT_ON_HIDE, false));
		SupervisorTheme theme = SupervisorSettings.getEnum(SupervisorSettings.THEME, SupervisorTheme.LIGHT);
		if (theme != SupervisorTheme.LIGHT) {
			this.useComponentDiagramViewServices.setTheme(theme);
		}
		this.applyStoredColors(theme);
		this.pushPortColorsSnapshot();

		// Opens the always-open first tab - deliberately done HERE, last, rather than
		// from ComponentDiagramControllerImpl's own @Activation (see the comment on that
		// class's "activated" field for why): vertical-mode activation runs
		// this composite's four parts one at a time, fully activating each before the
		// next begins, in declaration order (Model, Controller, View,
		// Interaction - see ComponentDiagramImpl); this Interaction
		// @UsedServices both the Controller and the View, so it is necessarily the
		// LAST of the four to activate, guaranteeing the View has already subscribed
		// to ComponentDiagramEvents (a consumed-events port auto-subscribes during its
		// own component's preInitialize, before that component's own @Activation) by
		// the time this call fires ComponentDiagramEvents#onSessionCreated through the
		// Model.
		this.onNewSession();
	}

	/**
	 * Retires every tab still open and unregisters the tool-wide preference and tool actions, tolerating an
	 * action manager that is already passivated.
	 */
	@Passivation
	public void unregisterEverything() {
		for (ViewKey sessionKey : Set.copyOf(this.openSessions)) {
			this.unregisterSessionActions(sessionKey);
		}
		for (ComponentDiagramPreferencesActionKey key : ComponentDiagramPreferencesActionKey.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(key);
			} catch (IllegalStateException e) {
				// ActionManager already passivated as part of the same container-stop cascade.
			}
		}
		for (ComponentDiagramToolActionKey key : ComponentDiagramToolActionKey.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(key);
			} catch (IllegalStateException e) {
				// ActionManager already passivated as part of the same container-stop cascade.
			}
		}
	}

	/** Opens a diagram if none is, then shows the composite's inside there, brought to front. */
	private void onOpenComposite(final Class<?> compositeType) {
		if (compositeType == null) {
			return;
		}
		if (this.openSessions.isEmpty()) {
			this.useComponentDiagramControllerServices.openNewSession();
		}
		if (this.openSessions.isEmpty()) {
			return;
		}
		ViewKey sessionKey = this.openSessions.iterator().next();
		this.useComponentDiagramControllerServices.exitToDepth(sessionKey, 0);
		this.useComponentDiagramControllerServices.enterComposite(sessionKey, compositeType);
		this.useComponentDiagramViewServices.bringToFront(sessionKey);
	}

	private void onShowLink(final List<?> ends) {
		if (ends == null || ends.size() != 2 || !(ends.get(0) instanceof Class<?> source)
				|| !(ends.get(1) instanceof Class<?> target) || this.openSessions.isEmpty()) {
			return;
		}
		this.useComponentDiagramControllerServices.showLink(this.openSessions.iterator().next(), source, target);
	}

	// --- ComponentDiagramEvents: tab lifecycle drives per-tab ActionKey registration;
	// onSelectionChanged drives that tab's Record availability; everything else
	// (topology/port-activity/dependencies-visibility/focus-path/flash) is the View's
	// own concern. ---

	@Override
	public void onSessionCreated(final ViewKey sessionKey) {
		this.openSessions.add(sessionKey);
		this.selectionBySession.put(sessionKey, false);

		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.RecordToggle),
				() -> this.onRecordToggle(sessionKey));
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.ZoomSetScale),
				scale -> this.onZoomSetScale(sessionKey, scale), Double.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.ZoomChanged),
				scale -> this.onZoomChanged(sessionKey, scale), Double.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.Recenter),
				() -> this.onRecenter(sessionKey));
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.Export),
				() -> this.onExport(sessionKey));
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.Close),
				() -> this.onClose(sessionKey));
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.SelectComponents),
				componentTypes -> this.onSelectComponents(sessionKey, componentTypes), Set.class);
		this.actionManagerServices.registerActionExecution(
				new ComponentDiagramActionKey(sessionKey, Role.ToggleComponentSelection),
				componentType -> this.onToggleComponentSelection(sessionKey, componentType), Class.class);
		this.actionManagerServices.registerActionExecution(
				new ComponentDiagramActionKey(sessionKey, Role.ToggleDependenciesVisibility),
				(componentType, show) -> this.onToggleDependenciesVisibility(sessionKey, componentType, show),
				Class.class, Boolean.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.EnterComposite),
				compositeType -> this.onEnterComposite(sessionKey, compositeType), Class.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.ShowComposite),
				compositeType -> this.onShowComposite(sessionKey, compositeType), Class.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.ExitToDepth),
				depth -> this.onExitToDepth(sessionKey, depth), Integer.class);
		this.actionManagerServices.registerActionExecution(
				new ComponentDiagramActionKey(sessionKey, Role.NavigateToComponent),
				componentType -> this.onNavigateToComponent(sessionKey, componentType), Class.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.NewSession),
				this::onNewSession);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.SaveView),
				() -> this.onSaveView(sessionKey));
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.QuickSaveView),
				() -> this.onQuickSaveView(sessionKey));
		this.actionManagerServices.registerActionExecution(
				new ComponentDiagramActionKey(sessionKey, Role.ConfirmSaveView),
				request -> this.onConfirmSaveView(sessionKey, request), SaveViewRequest.class);
		this.actionManagerServices.registerActionExecution(
				new ComponentDiagramActionKey(sessionKey, Role.OpenViewLibrary), () -> this.onOpenViewLibrary(sessionKey));
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.OpenView),
				this.useComponentDiagramControllerServices::openView, SavedView.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.RenameView),
				request -> this.onRenameView(sessionKey, request), RenameViewRequest.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.DeleteView),
				view -> this.onDeleteView(sessionKey, view), SavedView.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.ExportView),
				request -> this.onExportView(sessionKey, request), ExportViewRequest.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.ImportView),
				file -> this.onImportView(sessionKey, file), File.class);
		this.actionManagerServices.registerActionExecution(new ComponentDiagramActionKey(sessionKey, Role.RenameTab),
				title -> this.onRenameTab(sessionKey, title), String.class);

		this.publishRecordAvailability(sessionKey);
	}

	@Override
	public void onSessionClosed(final ViewKey sessionKey) {
		this.unregisterSessionActions(sessionKey);
	}

	private void unregisterSessionActions(final ViewKey sessionKey) {
		this.openSessions.remove(sessionKey);
		this.selectionBySession.remove(sessionKey);
		for (Role role : Role.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(new ComponentDiagramActionKey(sessionKey, role));
			} catch (IllegalStateException e) {
				// ActionManager already passivated as part of the same container-stop cascade.
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void onSelectComponents(final ViewKey sessionKey, final Set<?> componentTypes) {
		this.useComponentDiagramControllerServices.selectComponents(sessionKey, (Set<Class<?>>) componentTypes);
	}

	private void onToggleComponentSelection(final ViewKey sessionKey, final Class<?> componentType) {
		this.useComponentDiagramControllerServices.toggleComponentSelection(sessionKey, componentType);
	}

	private void onToggleDependenciesVisibility(final ViewKey sessionKey, final Class<?> componentType, final Boolean show) {
		this.useComponentDiagramControllerServices.toggleDependenciesVisibility(sessionKey, componentType, show);
	}

	private void onEnterComposite(final ViewKey sessionKey, final Class<?> compositeType) {
		this.useComponentDiagramControllerServices.enterComposite(sessionKey, compositeType);
	}

	private void onShowComposite(final ViewKey sessionKey, final Class<?> compositeType) {
		this.useComponentDiagramControllerServices.showComposite(sessionKey, compositeType);
	}

	private void onExitToDepth(final ViewKey sessionKey, final Integer depth) {
		this.useComponentDiagramControllerServices.exitToDepth(sessionKey, depth);
	}

	private void onNavigateToComponent(final ViewKey sessionKey, final Class<?> componentType) {
		this.useComponentDiagramControllerServices.navigateToComponent(sessionKey, componentType);
	}

	/**
	 * The shared Record button, on this tab: stops the recording in progress, whoever started
	 * it, or else records this tab's selection. Its pressed state is {@code SequenceDiagram}'s
	 * own, shown through {@code SequenceRecordingActionKey.RecordingState}.
	 */
	private void onRecordToggle(final ViewKey sessionKey) {
		if (this.useComponentDiagramControllerServices.isRecording()) {
			this.useComponentDiagramControllerServices.stopRecording();
		} else if (this.selectionBySession.getOrDefault(sessionKey, false)) {
			this.useComponentDiagramControllerServices.startRecording(sessionKey);
		}
	}

	/** Record serves on a tab while a recording runs, to stop it, or when it has a selection to record. */
	private void publishRecordAvailability(final ViewKey sessionKey) {
		this.actionManagerServices.setActionAvailable(new ComponentDiagramActionKey(sessionKey, Role.RecordToggle),
				this.useComponentDiagramControllerServices.isRecording()
						|| this.selectionBySession.getOrDefault(sessionKey, false));
	}

	// --- RecordingStateEvents: notices only, the state is asked again. ---

	@Override
	public void onRecordingStarted() {
		this.selectionBySession.keySet().forEach(this::publishRecordAvailability);
	}

	@Override
	public void onRecordingStopped() {
		this.selectionBySession.keySet().forEach(this::publishRecordAvailability);
	}

	private void onZoomSetScale(final ViewKey sessionKey, final Double scale) {
		this.useComponentDiagramViewServices.setZoomScale(sessionKey, scale);
	}

	private void onZoomChanged(final ViewKey sessionKey, final Double scale) {
		this.actionManagerServices.setActionValue(new ComponentDiagramActionKey(sessionKey, Role.ZoomCurrentScale), scale);
	}

	private void onRecenter(final ViewKey sessionKey) {
		this.useComponentDiagramViewServices.recenter(sessionKey);
	}

	private void onClose(final ViewKey sessionKey) {
		// No View call is needed: ComponentDiagramViewImpl reacts to onSessionClosed
		// directly and closes its own panel, as SequenceDiagramViewImpl does.
		this.useComponentDiagramControllerServices.closeSession(sessionKey);
	}

	private void onNewSession() {
		this.useComponentDiagramControllerServices.openNewSession();
	}

	// --- Views library ---

	/** Shows the save dialog, proposing the tab's title as the name. */
	private void onSaveView(final ViewKey sessionKey) {
		String containerName = this.useComponentDiagramControllerServices.getObservedContainerName();
		if (containerName == null) {
			this.useComponentDiagramViewServices.showError(sessionKey,
					ComponentDiagramInteractionTexts.get("save.noContainer"));
			return;
		}
		SavedView current = this.sessionViewOf(sessionKey, containerName);
		String tabTitle = this.useComponentDiagramViewServices.getTabTitle(sessionKey);
		Set<String> existingNames = new LinkedHashSet<>();
		for (SavedView view : this.useDiagramPersistenceServices.listViews()) {
			if (view.readable() && view.containerName().equals(containerName)) {
				existingNames.add(view.name());
			}
		}
		StringBuilder level = new StringBuilder(ComponentDiagramInteractionTexts.get("level.top"));
		for (Class<?> composite : this.useComponentDiagramModelReadServices.getFocusPath(sessionKey)) {
			level.append(" \u203a ").append(composite.getSimpleName());
		}
		this.useComponentDiagramViewServices.showSaveViewDialog(sessionKey, new SaveViewForm(
				tabTitle, current == null ? "" : current.description(), containerName,
				level.toString(), this.useComponentDiagramViewServices.getPositions(sessionKey).size(),
				this.useComponentDiagramViewServices.getHiddenComponents(sessionKey).size(),
				this.useComponentDiagramViewServices.getZoomScale(sessionKey), existingNames));
	}

	/** Replaces the tab's view directly if it has one for the observed container, shows the save dialog otherwise. */
	private void onQuickSaveView(final ViewKey sessionKey) {
		SavedView current = this.sessionViewOf(sessionKey,
				this.useComponentDiagramControllerServices.getObservedContainerName());
		if (current == null) {
			this.onSaveView(sessionKey);
		} else {
			this.onConfirmSaveView(sessionKey, new SaveViewRequest(current.name(), current.description()));
		}
	}

	/** The view a tab was opened from or saved to, if it is one of this container's. */
	private SavedView sessionViewOf(final ViewKey sessionKey, final String containerName) {
		SavedView view = this.useComponentDiagramControllerServices.getSessionView(sessionKey);
		return view != null && view.containerName().equals(containerName) ? view : null;
	}

	/** Saves the tab as a view, which then names the tab. */
	private void onConfirmSaveView(final ViewKey sessionKey, final SaveViewRequest request) {
		try {
			this.useComponentDiagramControllerServices.saveView(sessionKey, request.name(), request.description(),
					this.useComponentDiagramViewServices.getHiddenComponents(sessionKey),
					this.useComponentDiagramViewServices.getPositions(sessionKey),
					this.useComponentDiagramViewServices.getZoomScale(sessionKey),
					this.useComponentDiagramViewServices.captureThumbnail(sessionKey));
			this.useComponentDiagramViewServices.setTabTitle(sessionKey, request.name());
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to save the view " + request.name(), e);
			this.useComponentDiagramViewServices.showError(sessionKey, failure("error.save", request.name(), e));
		}
	}

	/** A tab renamed by the user: its title is the name proposed when saving it. */
	private void onRenameTab(final ViewKey sessionKey, final String title) {
		this.useComponentDiagramViewServices.setTabTitle(sessionKey, title);
		this.useComponentDiagramControllerServices.renameSession(sessionKey, title);
	}

	private void onOpenViewLibrary(final ViewKey sessionKey) {
		this.useComponentDiagramViewServices.showViewLibrary(sessionKey, this.viewLibraryContent());
	}

	/** The views library as it is now, each view of the observed container checked for missing components. */
	private ViewLibraryContent viewLibraryContent() {
		List<ViewLibraryContent.Entry> entries = new ArrayList<>();
		for (SavedView view : this.useDiagramPersistenceServices.listViews()) {
			entries.add(new ViewLibraryContent.Entry(view,
					this.useComponentDiagramControllerServices.getMissingComponents(view)));
		}
		return new ViewLibraryContent(this.useComponentDiagramControllerServices.getObservedContainerName(),
				this.useDiagramPersistenceServices.getViewsFolder(), List.copyOf(entries));
	}

	private void onRenameView(final ViewKey sessionKey, final RenameViewRequest request) {
		try {
			this.useDiagramPersistenceServices.renameView(request.view(), request.newName());
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to rename the view " + request.view().name(), e);
			this.useComponentDiagramViewServices.showError(sessionKey,
					failure("error.rename", request.view().name(), e));
		}
		this.useComponentDiagramViewServices.updateViewLibrary(this.viewLibraryContent());
	}

	private void onDeleteView(final ViewKey sessionKey, final SavedView view) {
		try {
			this.useDiagramPersistenceServices.deleteView(view);
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to delete the view " + view.name(), e);
			this.useComponentDiagramViewServices.showError(sessionKey, failure("error.delete", view.name(), e));
		}
		this.useComponentDiagramViewServices.updateViewLibrary(this.viewLibraryContent());
	}

	private void onExportView(final ViewKey sessionKey, final ExportViewRequest request) {
		try {
			this.useDiagramPersistenceServices.exportView(request.view(), request.file());
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to export the view " + request.view().name(), e);
			this.useComponentDiagramViewServices.showError(sessionKey,
					failure("error.export", request.view().name(), e));
		}
	}

	/** Imports a view file, after confirmation if it replaces a view of the same name. */
	private void onImportView(final ViewKey sessionKey, final File file) {
		try {
			SavedView view = this.useDiagramPersistenceServices.readView(file);
			if (this.useDiagramPersistenceServices.hasView(view.containerName(), view.name())
					&& !this.useComponentDiagramViewServices.confirm(sessionKey,
							ComponentDiagramInteractionTexts.get("import.title"),
							ComponentDiagramInteractionTexts.get("import.replaces", view.name(), view.containerName()),
							ComponentDiagramInteractionTexts.get("import.replace"))) {
				return;
			}
			this.useDiagramPersistenceServices.importView(file);
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to import the view file " + file, e);
			this.useComponentDiagramViewServices.showError(sessionKey, failure("error.import", file.getName(), e));
		}
		this.useComponentDiagramViewServices.updateViewLibrary(this.viewLibraryContent());
	}

	/**
	 * What an error dialog says when an operation on a view fails: what could not be done, then the reason, which is
	 * the name of the failure when it gave none.
	 */
	private static String failure(final String key, final String subject, final IOException cause) {
		String detail = cause.getMessage() != null && !cause.getMessage().isBlank() ? cause.getMessage()
				: cause.getClass().getSimpleName();
		return ComponentDiagramInteractionTexts.get(key, subject, detail);
	}

	private void onToggleAutoLayoutOnHide(final Boolean autoLayoutOnHide) {
		this.useComponentDiagramViewServices.setAutoLayoutOnHide(autoLayoutOnHide);
	}

	private void onSetPortColor(final PortColorKey key, final String value) {
		this.useComponentDiagramViewServices.setPortColor(key, value);
		this.pushPortColorsSnapshot();
	}

	private void onSetTheme(final SupervisorTheme theme) {
		this.useComponentDiagramViewServices.setTheme(theme);
		this.applyStoredColors(theme);
		this.pushPortColorsSnapshot();
	}

	private void onResetPortColors() {
		this.useComponentDiagramViewServices.resetPortColors();
		this.pushPortColorsSnapshot();
	}

	/** Applies the colors the user chose in this theme over its own. */
	private void applyStoredColors(final SupervisorTheme theme) {
		for (PortColorKey key : PortColorKey.values()) {
			String color = SupervisorSettings.getColor(theme, key.name());
			if (color != null) {
				this.useComponentDiagramViewServices.setPortColor(key, color);
			}
		}
	}

	/**
	 * Pushes every {@code PortColorKey}'s current color as one {@code
	 * PortColorsSnapshot} value - see that key's javadoc for why a push/bind
	 * value key rather than a direct cross-function Services call - and makes
	 * {@code ResetPortColors} available only while one of them differs from
	 * its default.
	 */
	private void pushPortColorsSnapshot() {
		Map<PortColorKey, String> snapshot = new EnumMap<>(PortColorKey.class);
		for (PortColorKey key : PortColorKey.values()) {
			snapshot.put(key, this.useComponentDiagramViewServices.getPortColor(key));
		}
		this.actionManagerServices.setActionValue(ComponentDiagramPreferencesActionKey.PortColorsSnapshot, snapshot);
		boolean customized = false;
		for (PortColorKey key : PortColorKey.values()) {
			if (!Objects.equals(snapshot.get(key),
					this.useComponentDiagramViewServices.getDefaultPortColor(key))) {
				customized = true;
				break;
			}
		}
		this.actionManagerServices.setActionAvailable(ComponentDiagramPreferencesActionKey.ResetPortColors,
				customized);
	}

	/** Has the View save the tab's rendering to a timestamped PNG and place it on the clipboard. */
	private void onExport(final ViewKey sessionKey) {
		this.useComponentDiagramViewServices.exportSnapshot(sessionKey);
	}

	@Override
	public void onTopologyChanged(final ViewKey sessionKey) {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onSelectionChanged(final ViewKey sessionKey, final Set<Class<?>> selectedComponentTypes) {
		if (this.selectionBySession.containsKey(sessionKey)) {
			this.selectionBySession.put(sessionKey, !selectedComponentTypes.isEmpty());
			this.publishRecordAvailability(sessionKey);
		}
	}

	@Override
	public void onPortActivityChanged(final Class<?> componentType, final Class<?> portType,
			final ComponentPortKind portKind, final boolean active) {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onLiveStructureChanged() {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onActivitySampled(final long sampleTimeMillis, final Map<Class<?>, ComponentActivity> activities) {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onComponentStatusesChanged() {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onDependenciesVisibilityChanged(final ViewKey sessionKey, final Class<?> componentType, final boolean visible) {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onFocusPathChanged(final ViewKey sessionKey, final List<Class<?>> focusPath) {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onFlashComponentChanged(final ViewKey sessionKey, final Class<?> componentType) {
		// No action here: the View reacts to this event.
	}

	@Override
	public void onLinkHighlightRequested(final ViewKey sessionKey, final Class<?> sourceType,
			final Class<?> targetType) {
		// No action here: the View reacts to this event.
	}
}
