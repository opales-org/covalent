/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumerofeventswithsubscriber.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerAdapter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumerofeventswithsubscriber.SimpleConsumerOfEventsWithSubscriber;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;

@Implementation
public class SimpleConsumerOfEventsWithSubscriberImpl extends ConsumerAdapter
		implements SimpleConsumerOfEventsWithSubscriber, ConsumerServices, ProducerWithSubscribeEvents {

}
