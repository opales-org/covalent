/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.sequence;

import org.opales.covalent.supervision.observed.CallOutcome;

/**
 * Immutable message emitted by the sequence trace reconciler.
 */
public final class SequenceTraceMessage {

	/** Role of a message in the trace. */
	public enum Style {
		/** The start of an invocation, drawn from its caller to its callee. */
		CALL,
		/** The end of an invocation, drawn from its callee back to its caller. */
		RETURN,
		/**
		 * How an invocation without a return message ended - its duration and failure - for
		 * an event: never drawn, it completes what its call message shows.
		 */
		COMPLETION
	}

	private final Class<?> sourceType;

	private final Class<?> targetType;

	private final String label;

	private final String details;

	private final long relativeTimeMillis;

	private final Style style;

	private final Facts facts;

	private final String sourceStereotype;

	private final String targetStereotype;

	/**
	 * The structured description of a message, for views that lay its details out rather than
	 * printing {@link #getDetails()}.
	 *
	 * @param invocationId shared by the call and the return of one same invocation
	 * @param kind "Service", "Event", "Parameter" or "Action"
	 * @param portName the port interface's simple name, or the action's name
	 * @param methodName the invoked method's name, {@code null} for an action
	 * @param threadName the calling thread's name
	 * @param receiverDescription the object receiving the call inside the callee component when it
	 *        is not the component itself, {@code null} otherwise
	 * @param argumentsDescription the printed arguments
	 * @param outcome how the invocation ended - only on a return message, {@code null} otherwise
	 * @param durationNanos the invocation's duration, {@code -1} when not known by this message
	 * @param failureDescription the failure the invocation ended with, {@code null} when none
	 * @param asynchronous whether the invocation runs on another thread than its caller's
	 * @param queueWaitNanos how long an event notification waited between the moment the event was
	 *        fired and its start, {@code -1} when not known or not an event
	 */
	public record Facts(long invocationId, String kind, String portName, String methodName, String threadName,
			String receiverDescription, String argumentsDescription, CallOutcome outcome, long durationNanos,
			String failureDescription, boolean asynchronous, long queueWaitNanos) {

		/**
		 * Tells whether the invocation ended with a failure.
		 *
		 * @return {@code true} when a failure description is present
		 */
		public boolean isFailure() {
			return this.failureDescription != null;
		}
	}

	/**
	 * Creates a message that carries no structured description.
	 *
	 * @param sourceType the type of the component the message leaves
	 * @param targetType the type of the component the message reaches
	 * @param label the text drawn on the message
	 * @param details the multi-line text describing the message
	 * @param relativeTimeMillis the time since the start of the recording, in milliseconds
	 * @param style the role of the message
	 */
	public SequenceTraceMessage(final Class<?> sourceType, final Class<?> targetType, final String label,
			final String details, final long relativeTimeMillis, final Style style) {
		this(sourceType, targetType, label, details, relativeTimeMillis, style, null);
	}

	/**
	 * Creates a message with its structured description.
	 *
	 * @param sourceType the type of the component the message leaves
	 * @param targetType the type of the component the message reaches
	 * @param label the text drawn on the message
	 * @param details the multi-line text describing the message
	 * @param relativeTimeMillis the time since the start of the recording, in milliseconds
	 * @param style the role of the message
	 * @param facts the structured description, {@code null} when there is none
	 */
	public SequenceTraceMessage(final Class<?> sourceType, final Class<?> targetType, final String label,
			final String details, final long relativeTimeMillis, final Style style, final Facts facts) {
		this(sourceType, targetType, label, details, relativeTimeMillis, style, facts, null, null);
	}

	private SequenceTraceMessage(final Class<?> sourceType, final Class<?> targetType, final String label,
			final String details, final long relativeTimeMillis, final Style style, final Facts facts,
			final String sourceStereotype, final String targetStereotype) {
		this.facts = facts;
		this.sourceStereotype = sourceStereotype;
		this.targetStereotype = targetStereotype;
		this.sourceType = sourceType;
		this.targetType = targetType;
		this.label = label;
		this.details = details;
		this.relativeTimeMillis = relativeTimeMillis;
		this.style = style;
	}

	/**
	 * Returns the type of the component the message leaves.
	 *
	 * @return the source type
	 */
	public Class<?> getSourceType() {
		return this.sourceType;
	}

	/**
	 * Returns the type of the component the message reaches.
	 *
	 * @return the target type
	 */
	public Class<?> getTargetType() {
		return this.targetType;
	}

	/**
	 * Returns the text drawn on the message.
	 *
	 * @return the label, the port and method names for a call
	 */
	public String getLabel() {
		return this.label;
	}

	/**
	 * Returns the multi-line text describing the message in full.
	 *
	 * @return the details
	 */
	public String getDetails() {
		return this.details;
	}

	/**
	 * Returns the time of the message since the start of the recording.
	 *
	 * @return the time in milliseconds
	 */
	public long getRelativeTimeMillis() {
		return this.relativeTimeMillis;
	}

	/**
	 * Returns the role of the message in the trace.
	 *
	 * @return the style
	 */
	public Style getStyle() {
		return this.style;
	}

	/**
	 * Returns the structured description of this message, for views that lay its details out.
	 *
	 * @return this message's structured description, {@code null} when it was built without one
	 */
	public Facts getFacts() {
		return this.facts;
	}

	/**
	 * Returns a copy of this message that tells what its two lifelines stand for.
	 *
	 * @param source what the source lifeline stands for, e.g. "Model" or "Composite"
	 * @param target what the target lifeline stands for
	 * @return a copy of this message with these stereotypes
	 */
	public SequenceTraceMessage withStereotypes(final String source, final String target) {
		return new SequenceTraceMessage(this.sourceType, this.targetType, this.label, this.details,
				this.relativeTimeMillis, this.style, this.facts, source, target);
	}

	/**
	 * Returns what the source lifeline stands for.
	 *
	 * @return what the source lifeline stands for, {@code null} when not known
	 */
	public String getSourceStereotype() {
		return this.sourceStereotype;
	}

	/**
	 * Returns what the target lifeline stands for.
	 *
	 * @return what the target lifeline stands for, {@code null} when not known
	 */
	public String getTargetStereotype() {
		return this.targetStereotype;
	}
}
