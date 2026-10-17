/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumer.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerAdapter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.ConsumerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumer.simpleconsumer.SimpleConsumer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerEvents;

@Implementation
public class SimpleConsumerImpl extends ConsumerAdapter implements ConsumerServices, ProducerEvents, SimpleConsumer {

}
