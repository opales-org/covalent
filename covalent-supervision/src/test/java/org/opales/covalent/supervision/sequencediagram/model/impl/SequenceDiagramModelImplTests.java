/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramEvents;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Verifies that the Model keeps each session's messages, completions, scope and start, and lets
 * them go when the session closes.
 */
public class SequenceDiagramModelImplTests {

	private static final ViewKey SESSION = new ViewKey() {
	};

	private final List<String> notices = new ArrayList<>();
	private SequenceDiagramModelImpl model;

	@Before
	public void setUp() throws Exception {
		this.model = new SequenceDiagramModelImpl();
		SequenceDiagramEvents events = (SequenceDiagramEvents) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { SequenceDiagramEvents.class }, (proxy, method, arguments) -> {
					this.notices.add(method.getName());
					return null;
				});
		Field field = SequenceDiagramModelImpl.class.getDeclaredField("produceSequenceDiagramEvents");
		field.setAccessible(true);
		field.set(this.model, events);
	}

	private static SequenceTraceMessage message(final String label, final SequenceTraceMessage.Style style) {
		return new SequenceTraceMessage(String.class, Integer.class, label, "", 0, style);
	}

	@Test
	public void aSessionKeepsItsMessagesApartFromItsCompletions() {
		this.model.createSession(SESSION, Set.of(String.class));
		SequenceTraceMessage call = message("call", SequenceTraceMessage.Style.CALL);
		SequenceTraceMessage completion = message("done", SequenceTraceMessage.Style.COMPLETION);
		SequenceTraceMessage reply = message("return", SequenceTraceMessage.Style.RETURN);

		this.model.appendMessage(SESSION, call);
		this.model.appendMessage(SESSION, completion);
		this.model.appendMessage(SESSION, reply);

		Assert.assertEquals(List.of(call, reply), this.model.getMessages(SESSION));
		Assert.assertEquals(List.of(completion), this.model.getCompletions(SESSION));
		Assert.assertEquals(Set.of(String.class), this.model.getObservedComponentTypes(SESSION));
		Assert.assertNotNull(this.model.getStart(SESSION));
		Assert.assertEquals(List.of("onSessionCreated", "onMessageAppended", "onMessageAppended", "onMessageAppended"),
				this.notices);
	}

	@Test
	public void aClosedSessionHoldsNothingMore() {
		this.model.createSession(SESSION, Set.of(String.class));
		this.model.appendMessage(SESSION, message("call", SequenceTraceMessage.Style.CALL));

		this.model.closeSession(SESSION);

		Assert.assertTrue(this.model.getMessages(SESSION).isEmpty());
		Assert.assertTrue(this.model.getCompletions(SESSION).isEmpty());
		Assert.assertTrue(this.model.getObservedComponentTypes(SESSION).isEmpty());
		Assert.assertNull(this.model.getStart(SESSION));
	}
}
