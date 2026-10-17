/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.action.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.util.CallerFinder;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.hmi.action.ActionEvents;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionManager;
import org.opales.covalent.hmi.action.ActionObservationServices;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.hmi.action.ActionStatus;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

/**
 * Default implementation of {@link ActionManager}, providing
 * {@link ActionProviderServices}, {@link ActionConsumerServices}, and
 * {@link ActionObservationServices}. Owns the whole action lifecycle in
 * memory: registering an action's implementation, tracking its
 * availability, value, and running/idle status, executing it on demand,
 * and relaying its own availability and value into any JavaFX
 * {@link Node}/{@link Consumer} bound to it. All state is kept in
 * per-{@link ActionKey} maps, cleared on {@link #passivate()} via
 * {@link #stop()}, except the {@link ActionEvents} observers, which stay
 * registered until they remove themselves.
 */
@Implementation
public class ActionManagerImpl
		implements ActionManager, ActionProviderServices, ActionConsumerServices, ActionObservationServices {

	/** Creates an action manager with no action registered. */
	public ActionManagerImpl() {
		// The state is held by the per-action maps declared below.
	}

	/**
	 * Container-level parameters, autowired by the framework. Only
	 * {@link CovalentContainerParameters#isInstrumented()} is read, to
	 * decide whether {@link #getActionCaller()} is worth paying for on
	 * every {@code registerActionExecution(...)} call.
	 */
	@AccessedParameters
	private CovalentContainerParameters covalentContainerParameters;

	/**
	 * The registered implementation for each action, keyed by
	 * {@link ActionKey} - a {@link SimpleAction} for the no-argument and
	 * one-argument overloads, a {@link BiAction} for the two-argument one.
	 */
	private final Map<ActionKey, Action> actionExecutionImplementations = new HashMap<>();

	/**
	 * Availability listeners registered through
	 * {@link #addActionAvailabilityHandler(ActionKey, Consumer)}, keyed by
	 * {@link ActionKey}. Distinct from {@link #actionAvailableStatusBinding}:
	 * these are arbitrary callbacks, not JavaFX nodes.
	 */
	private final Map<ActionKey, List<Consumer<Boolean>>> actionAvailabilityHandlers = new HashMap<>();

	/**
	 * The last availability reported for each action via
	 * {@link #setActionAvailable(ActionKey, boolean)}. A missing entry
	 * means "available" - see {@link #isActionAvailable(ActionKey)}.
	 */
	private final Map<ActionKey, Boolean> actionAvailableStatus = new HashMap<>();

	/**
	 * The current {@link ActionStatus} (running/idle) of each action, set
	 * around every {@code executeAction(...)} call. A missing entry means
	 * idle - see {@link #getActionStatus(ActionKey)}.
	 */
	private final Map<ActionKey, ActionStatus> actionExecutionStatus = new HashMap<>();

	/**
	 * JavaFX nodes bound to an action's availability through
	 * {@link #bindActionAvailability(ActionKey, Node)}, keyed by
	 * {@link ActionKey}. Each bound node has its
	 * {@code disableProperty()} kept in sync with
	 * {@link #actionAvailableStatus}.
	 */
	private final Map<ActionKey, List<Node>> actionAvailableStatusBinding = new HashMap<>();

	/**
	 * The {@link ChangeListener} installed by
	 * {@link #bindActionAvailability(ActionKey, Node)} on each bound
	 * node's {@code disableProperty()}, keyed by that node - kept so
	 * {@link #unbindActionAvailability(ActionKey, Node)} can remove
	 * exactly that listener.
	 */
	private final Map<Node, ChangeListener<Boolean>> actionAvailabilityBindingListeners = new HashMap<>();

	/**
	 * Callbacks registered through
	 * {@link #bindActionValue(ActionKey, Consumer)}, keyed by
	 * {@link ActionKey}.
	 */
	private final Map<ActionKey, List<Consumer<Object>>> actionValueBindings = new HashMap<>();

	/**
	 * The last value reported for each action via
	 * {@link #setActionValue(ActionKey, Object)}. A missing entry means
	 * no value has been reported yet.
	 */
	private final Map<ActionKey, Object> actionValueStatus = new HashMap<>();

	/**
	 * Third-party observers registered through
	 * {@link #addActionEventsListener(ActionEvents)}, notified by
	 * {@code notifyAction(ActionKey, Class, Object[])} every time an action
	 * is about to run.
	 */
	protected List<ActionEvents> actionEventsObservers = new ArrayList<>();

	/**
	 * The component type that registered each action's implementation,
	 * captured once at {@code registerActionExecution(...)} time (only
	 * when {@link #isInstrumented()}) and reported back to
	 * {@link ActionEvents#onActionTriggered(ActionKey, Class, Class, Object...)}
	 * as the {@code actionImplementer}.
	 */
	private final Map<ActionKey, Class<?>> actionImplementationComponent = new HashMap<>();

	/**
	 * Lifecycle hook, run once at deployment. Does nothing: all state
	 * here starts empty on construction, so nothing needs priming.
	 */
	@Activation
	public void activate() {
	}

	/**
	 * Lifecycle hook, run once at teardown. Delegates to {@link #stop()}
	 * to release every action, binding, and observer this instance holds.
	 */
	@Passivation
	public void passivate() {
		this.stop();
	}

	/**
	 * Registers a one-argument action implementation.
	 *
	 * @param <T>                input data type
	 * @param anActionKey        action identifier
	 * @param anInteractionImpl  action implementation
	 * @param anInputDataClass   input data class
	 */
	@Override
	public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
			final Class<T> anInputDataClass) {
		SimpleAction<T> action = new SimpleAction<>();
		action.setFunction(anInteractionImpl);
		this.actionExecutionImplementations.put(anActionKey, action);

		if (this.isInstrumented()) {
			Class<?> actionImplementer = this.getActionCaller();
			this.actionImplementationComponent.put(anActionKey, actionImplementer);
		}
	}

	/**
	 * Registers a two-argument action implementation.
	 *
	 * @param <T1>               first input data type
	 * @param <T2>               second input data type
	 * @param anActionKey        action identifier
	 * @param anInteractionImpl  action implementation
	 * @param anInputDataClass1  first input data class
	 * @param anInputDataClass2  second input data class
	 */
	@Override
	public <T1, T2> void registerActionExecution(final ActionKey anActionKey,
			final BiConsumer<T1, T2> anInteractionImpl, final Class<T1> anInputDataClass1,
			final Class<T2> anInputDataClass2) {
		BiAction<T1, T2> action = new BiAction<>();
		action.setFunction(anInteractionImpl);
		this.actionExecutionImplementations.put(anActionKey, action);

		if (this.isInstrumented()) {
			Class<?> actionImplementer = this.getActionCaller();
			this.actionImplementationComponent.put(anActionKey, actionImplementer);
		}
	}

	/**
	 * Registers a no-argument action implementation.
	 *
	 * @param anActionKey        action identifier
	 * @param anInteractionImpl  action implementation
	 */
	@Override
	public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl) {
		SimpleAction<Void> action = new SimpleAction<>();
		action.setFunction(anInteractionImpl);
		this.actionExecutionImplementations.put(anActionKey, action);

		if (this.isInstrumented()) {
			Class<?> actionImplementer = this.getActionCaller();
			this.actionImplementationComponent.put(anActionKey, actionImplementer);
		}
	}

	/**
	 * Whether the owning container was started with instrumentation
	 * enabled - the only condition under which
	 * {@code registerActionExecution(...)} pays for
	 * {@link #getActionCaller()} to populate
	 * {@link #actionImplementationComponent}.
	 *
	 * @return {@code true} when the container is instrumented
	 */
	private boolean isInstrumented() {
		return this.covalentContainerParameters.isInstrumented();
	}

	/**
	 * Removes the action implementation associated with an action identifier.
	 *
	 * @param anActionKey action identifier
	 */
	@Override
	public void unregisterActionExecution(final ActionKey anActionKey) {
		this.actionExecutionImplementations.remove(anActionKey);
	}

	/**
	 * Executes a two-argument action.
	 *
	 * @param <T1>         first input data type
	 * @param <T2>         second input data type
	 * @param anActionKey  action identifier
	 * @param anInputData1 first input data passed to the action
	 * @param anInputData2 second input data passed to the action
	 * @return {@code true} when an available action implementation was executed
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T1, T2> boolean executeAction(final ActionKey anActionKey, final T1 anInputData1, final T2 anInputData2) {
		Class<?> actionCaller = this.getActionCaller();
		boolean executed = false;

		if (this.actionAvailableStatus.get(anActionKey) == null
				|| this.actionAvailableStatus.get(anActionKey) == true) {
			Action action = this.actionExecutionImplementations.get(anActionKey);
			if (action != null) {
				if (action instanceof BiAction) {
					BiAction<T1, T2> biAction = (BiAction<T1, T2>) action;
					if (biAction.getFunction() != null) {
						this.actionExecutionStatus.put(anActionKey, ActionStatus.running);
						try {
							this.notifyAction(anActionKey, actionCaller, new Object[] { anInputData1, anInputData2 });
							biAction.getFunction().accept(anInputData1, anInputData2);
						} finally {
							this.actionExecutionStatus.put(anActionKey, ActionStatus.idle);
						}
						executed = true;
					}
				}
			}
			return executed;
		}
		return false;
	}

	/**
	 * Notifies every registered {@link ActionEvents} observer that an
	 * action is about to run, reporting both who asked for it (the caller)
	 * and who provides it (looked up from
	 * {@link #actionImplementationComponent}). Iterates over a defensive
	 * copy of {@link #actionEventsObservers} so an observer
	 * adding/removing itself mid-notification cannot corrupt the loop.
	 *
	 * @param anActionKey  action identifier
	 * @param actionCaller component type that requested the action
	 * @param inputData    the input data the action was launched with, empty
	 *                     for a no-argument action
	 */
	private void notifyAction(final ActionKey anActionKey, final Class<?> actionCaller, final Object[] inputData) {
		List<ActionEvents> actionEventsObservers = new ArrayList<>(this.actionEventsObservers);
		for (ActionEvents actionEvent : actionEventsObservers) {
			actionEvent.onActionTriggered(anActionKey, actionCaller,
					this.actionImplementationComponent.get(anActionKey), inputData);
		}
	}

	/**
	 * Resolves the business component that called into this
	 * {@code ActionManagerImpl}, by inspecting the call stack through
	 * {@link CallerFinder}. This class itself is excluded from the stack scan
	 * so the result is the real caller, not an internal delegation
	 * within {@code ActionManagerImpl}.
	 *
	 * @return the calling component's type, or {@code null} if it could
	 *         not be resolved
	 */
	private Class<?> getActionCaller() {
		List<Class<?>> excluded = new ArrayList<>();
		excluded.add(this.getClass());
		return CallerFinder.getCaller(excluded);
	}

	/**
	 * Executes a one-argument action.
	 *
	 * @param <T>         input data type
	 * @param anActionKey action identifier
	 * @param anInputData input data passed to the action
	 * @return {@code true} when an available action implementation was executed
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> boolean executeAction(final ActionKey anActionKey, final T anInputData) {
		Class<?> actionCaller = this.getActionCaller();
		boolean executed = false;

		if (this.actionAvailableStatus.get(anActionKey) == null
				|| this.actionAvailableStatus.get(anActionKey) == true) {
			Action action = this.actionExecutionImplementations.get(anActionKey);
			if (action != null) {
				if (action instanceof SimpleAction) {
					SimpleAction<T> simpleAction = (SimpleAction<T>) action;
					if (simpleAction.getFunction() != null) {
						this.actionExecutionStatus.put(anActionKey, ActionStatus.running);
						try {
							this.notifyAction(anActionKey, actionCaller, new Object[] { anInputData });
							simpleAction.getFunction().accept(anInputData);
						} finally {
							this.actionExecutionStatus.put(anActionKey, ActionStatus.idle);
						}
						executed = true;
					}
				}
			}
			return executed;
		}
		return false;
	}

	/**
	 * Executes a no-argument action.
	 *
	 * @param anActionKey action identifier
	 * @return {@code true} when an available action implementation was executed
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean executeAction(final ActionKey anActionKey) {
		Class<?> actionCaller = this.getActionCaller();
		boolean executed = false;

		if (this.actionAvailableStatus.get(anActionKey) == null
				|| this.actionAvailableStatus.get(anActionKey) == true) {
			Action action = this.actionExecutionImplementations.get(anActionKey);
			if (action != null) {
				if (action instanceof SimpleAction) {
					SimpleAction<Void> simpleAction = (SimpleAction<Void>) action;
					if (simpleAction.getVoidFunction() != null) {
						this.actionExecutionStatus.put(anActionKey, ActionStatus.running);
						try {
							this.notifyAction(anActionKey, actionCaller, new Object[0]);
							simpleAction.getVoidFunction().run();
						} finally {
							this.actionExecutionStatus.put(anActionKey, ActionStatus.idle);
						}
						executed = true;
					}
				}
			}
			return executed;
		}
		return false;
	}

	/**
	 * Registers an arbitrary callback to be notified of every
	 * availability change for an action, in addition to (not instead of)
	 * any JavaFX node bound via
	 * {@link #bindActionAvailability(ActionKey, Node)}. Not part of
	 * ActionProviderServices/ActionConsumerServices/ActionObservationServices -
	 * called directly on this implementation class. If the
	 * action already has a known availability, the handler is invoked
	 * immediately with that value.
	 *
	 * @param anActionKey         action identifier
	 * @param anAvailabilityHandler callback invoked with the new
	 *                              availability every time it changes
	 */
	public void addActionAvailabilityHandler(final ActionKey anActionKey,
			final Consumer<Boolean> anAvailabilityHandler) {
		List<Consumer<Boolean>> listOfHandler = this.actionAvailabilityHandlers.getOrDefault(anActionKey,
				new ArrayList<>());
		listOfHandler.add(anAvailabilityHandler);
		this.actionAvailabilityHandlers.put(anActionKey, listOfHandler);

		Boolean status = this.actionAvailableStatus.get(anActionKey);
		if (status != null) {
			anAvailabilityHandler.accept(status);
		}
	}

	/**
	 * Sets whether an action can be executed. Reported unconditionally by
	 * the action's own provider, whether or not anything currently
	 * listens: propagates to every registered
	 * {@code actionAvailabilityHandlers} callback and disables/enables
	 * every JavaFX node bound via
	 * {@link #bindActionAvailability(ActionKey, Node)}.
	 *
	 * @param anActionKey action identifier
	 * @param isAvailable {@code true} makes the action executable
	 */
	@Override
	public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
		this.actionAvailableStatus.put(anActionKey, isAvailable);

		List<Consumer<Boolean>> list = this.actionAvailabilityHandlers.getOrDefault(anActionKey, new ArrayList<>());
		list.forEach(c -> c.accept(isAvailable));

		List<Node> bindings = this.actionAvailableStatusBinding.getOrDefault(anActionKey, new ArrayList<>());
		bindings.forEach(c -> c.setDisable(!isAvailable));
	}

	/**
	 * Whether an action can currently be executed. Not part of
	 * ActionProviderServices/ActionConsumerServices/ActionObservationServices -
	 * called directly on this implementation class, and internally by
	 * {@link #bindActionAvailability(ActionKey, Node)}; the
	 * {@code executeAction(...)} overloads apply the same rule inline.
	 *
	 * @param anActionKey action identifier
	 * @return {@code true} when no availability was ever reported, or the
	 *         last reported availability was {@code true}
	 */
	public boolean isActionAvailable(final ActionKey anActionKey) {
		return this.actionAvailableStatus.get(anActionKey) == null || this.actionAvailableStatus.get(anActionKey);
	}

	/**
	 * The current running/idle status of an action. Not part of
	 * ActionProviderServices/ActionConsumerServices/ActionObservationServices -
	 * called directly on this implementation class.
	 *
	 * @param anActionKey action identifier
	 * @return {@link ActionStatus#running} while the action's
	 *         implementation is executing, {@link ActionStatus#idle}
	 *         otherwise (including when the action is unknown)
	 */
	public ActionStatus getActionStatus(final ActionKey anActionKey) {
		return this.actionExecutionStatus.getOrDefault(anActionKey, ActionStatus.idle);
	}

	/**
	 * Binds the disabled state of a JavaFX node to action availability.
	 * The node's {@code disableProperty()} is kept in sync with every
	 * {@link #setActionAvailable(ActionKey, boolean)} call, and
	 * immediately set to match the action's current availability. A
	 * defensive listener re-applies {@code setDisable(true)} if something
	 * else tries to re-enable the node while the action is unavailable.
	 * A node follows a single action: binding it again to the same action
	 * changes nothing, and binding it to another action while it is still
	 * bound is rejected. While bound, the node's disabled state belongs to
	 * the action: it is disabled whenever the action is unavailable and
	 * enabled whenever the action becomes available, so any other condition
	 * for disabling the node is expressed through the action's availability.
	 *
	 * @param anActionKey action identifier
	 * @param aNode       JavaFX node bound to action availability
	 * @throws IllegalStateException if the node is already bound to another
	 *                               action
	 */
	@Override
	public void bindActionAvailability(final ActionKey anActionKey, final Node aNode) {
		for (Map.Entry<ActionKey, List<Node>> binding : this.actionAvailableStatusBinding.entrySet()) {
			if (!binding.getKey().equals(anActionKey) && binding.getValue().contains(aNode)) {
				throw new IllegalStateException("Node is already bound to the availability of action "
						+ binding.getKey() + ": unbind it before binding it to " + anActionKey);
			}
		}
		List<Node> listOfHandler = this.actionAvailableStatusBinding.getOrDefault(anActionKey, new ArrayList<>());
		if (listOfHandler.contains(aNode)) {
			return;
		}
		listOfHandler.add(aNode);
		this.actionAvailableStatusBinding.put(anActionKey, listOfHandler);

		ChangeListener<Boolean> listener = (s, o, n) -> {
			if (!this.isActionAvailable(anActionKey) && !n.booleanValue()) {
				aNode.setDisable(true);
			}
		};
		this.actionAvailabilityBindingListeners.put(aNode, listener);
		aNode.disableProperty().addListener(listener);

		if (!this.isActionAvailable(anActionKey)) {
			aNode.setDisable(true);
		}
	}

	/**
	 * Removes a binding previously established by
	 * {@link #bindActionAvailability(ActionKey, Node)}: stops the node's
	 * {@code disableProperty()} from following that action's
	 * availability, and removes the defensive listener installed there.
	 *
	 * @param anActionKey action identifier
	 * @param aNode       JavaFX node to unbind from action availability
	 */
	@Override
	public void unbindActionAvailability(final ActionKey anActionKey, final Node aNode) {
		List<Node> listOfHandler = this.actionAvailableStatusBinding.get(anActionKey);
		if (listOfHandler == null || !listOfHandler.remove(aNode)) {
			return;
		}

		ChangeListener<Boolean> listener = this.actionAvailabilityBindingListeners.remove(aNode);
		if (listener != null) {
			aNode.disableProperty().removeListener(listener);
		}
	}

	/**
	 * Reports an action's current value, propagating it to every
	 * consumer registered via
	 * {@link #bindActionValue(ActionKey, Consumer)}.
	 *
	 * @param anActionKey action identifier
	 * @param aValue      the action's current value
	 */
	@Override
	public void setActionValue(final ActionKey anActionKey, final Object aValue) {
		this.actionValueStatus.put(anActionKey, aValue);

		List<Consumer<Object>> consumers = this.actionValueBindings.getOrDefault(anActionKey, new ArrayList<>());
		consumers.forEach(c -> c.accept(aValue));
	}

	/**
	 * Subscribes to an action's value. Invoked immediately with the
	 * action's current value, if {@link #setActionValue(ActionKey, Object)}
	 * has already reported one.
	 *
	 * @param anActionKey    action identifier
	 * @param aValueConsumer callback invoked with the action's value
	 */
	@Override
	public void bindActionValue(final ActionKey anActionKey, final Consumer<Object> aValueConsumer) {
		List<Consumer<Object>> consumers = this.actionValueBindings.getOrDefault(anActionKey, new ArrayList<>());
		consumers.add(aValueConsumer);
		this.actionValueBindings.put(anActionKey, consumers);

		if (this.actionValueStatus.containsKey(anActionKey)) {
			aValueConsumer.accept(this.actionValueStatus.get(anActionKey));
		}
	}

	/**
	 * Removes a binding previously established by
	 * {@link #bindActionValue(ActionKey, Consumer)} - matched by the same
	 * consumer reference.
	 *
	 * @param anActionKey    action identifier
	 * @param aValueConsumer the same callback reference passed to
	 *                       {@link #bindActionValue(ActionKey, Consumer)}
	 */
	@Override
	public void unbindActionValue(final ActionKey anActionKey, final Consumer<Object> aValueConsumer) {
		List<Consumer<Object>> consumers = this.actionValueBindings.get(anActionKey);
		if (consumers != null) {
			consumers.remove(aValueConsumer);
		}
	}

	/**
	 * Adds an action event listener.
	 *
	 * @param obs listener to add
	 */
	@Override
	public void addActionEventsListener(final ActionEvents obs) {
		this.actionEventsObservers.add(obs);
	}

	/**
	 * Removes an action event listener.
	 *
	 * @param obs listener to remove
	 */
	@Override
	public void removeActionEventsListener(final ActionEvents obs) {
		this.actionEventsObservers.remove(obs);
	}

	/**
	 * Releases every action implementation, availability handler,
	 * availability/value binding, execution status, and implementer
	 * mapping this instance holds, and detaches the defensive listeners
	 * from the bound nodes. Called from {@link #passivate()};
	 * does not clear {@link #actionEventsObservers} (observers are
	 * expected to remove themselves via
	 * {@link #removeActionEventsListener(ActionEvents)}).
	 */
	public void stop() {
		this.actionAvailabilityHandlers.clear();
		this.actionAvailableStatus.clear();
		this.actionAvailableStatusBinding.clear();
		this.actionAvailabilityBindingListeners
				.forEach((node, listener) -> node.disableProperty().removeListener(listener));
		this.actionAvailabilityBindingListeners.clear();
		this.actionValueBindings.clear();
		this.actionValueStatus.clear();
		this.actionExecutionImplementations.clear();
		this.actionExecutionStatus.clear();
		this.actionImplementationComponent.clear();
	}

	/**
	 * Marker for a registered action implementation, regardless of its
	 * arity - see {@link SimpleAction} (no-argument/one-argument) and
	 * {@link BiAction} (two-argument).
	 */
	interface Action {
	}

	/**
	 * A registered no-argument or one-argument action implementation.
	 *
	 * @param <T> input data type (unused for the no-argument case, where
	 *            {@link #voidInteractionImpl} is set instead of
	 *            {@link #interactionImpl})
	 */
	class SimpleAction<T> implements Action {

		/** The one-argument implementation, when this action takes an input. */
		private Consumer<T> interactionImpl;

		/** The no-argument implementation, when this action takes no input. */
		private Runnable voidInteractionImpl;

		/**
		 * Sets this action's one-argument implementation.
		 *
		 * @param anInteractionImpl the implementation to run on execution
		 */
		public void setFunction(final Consumer<T> anInteractionImpl) {
			this.interactionImpl = anInteractionImpl;
		}

		/**
		 * Sets this action's no-argument implementation.
		 *
		 * @param anInteractionImpl the implementation to run on execution
		 */
		public void setFunction(final Runnable anInteractionImpl) {
			this.voidInteractionImpl = anInteractionImpl;
		}

		/**
		 * @return this action's one-argument implementation, or
		 *         {@code null} for the no-argument case
		 */
		public Consumer<T> getFunction() {
			return this.interactionImpl;
		}

		/**
		 * @return this action's no-argument implementation, or
		 *         {@code null} for the one-argument case
		 */
		public Runnable getVoidFunction() {
			return this.voidInteractionImpl;
		}

	}

	/**
	 * A registered two-argument action implementation.
	 *
	 * @param <T1> first input data type
	 * @param <T2> second input data type
	 */
	class BiAction<T1, T2> implements Action {

		/** The two-argument implementation. */
		private BiConsumer<T1, T2> interactionImpl;

		/**
		 * Sets this action's two-argument implementation.
		 *
		 * @param anInteractionImpl the implementation to run on execution
		 */
		public void setFunction(final BiConsumer<T1, T2> anInteractionImpl) {
			this.interactionImpl = anInteractionImpl;
		}

		/**
		 * @return this action's two-argument implementation
		 */
		public BiConsumer<T1, T2> getFunction() {
			return this.interactionImpl;
		}

	}

}
