/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.events.impl.ConsumerMetadata;

/**
 * Verifies the value semantics of {@link ConsumerMetadata}: two metadata are
 * equal exactly when they share the same subscriber instance and the same
 * subscribe parameters (the owning component type is ignored), hashing is
 * consistent with equality, and the subscribe argument falls back to the
 * subscribe parameters when no explicit argument was set.
 */
public class ConsumerMetadataTests {

	private static class FakeParameters implements SubscribeParameters {
		private Object filter;

		@Override
		@SuppressWarnings("unchecked")
		public <EVENT_FILTER> void setEventFilter(final EVENT_FILTER anEventFilter) {
			this.filter = anEventFilter;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <EVENT_FILTER> EVENT_FILTER getEventFilter() {
			return (EVENT_FILTER) this.filter;
		}
	}

	private static ConsumerMetadata<Runnable> metadata(final Runnable instance, final SubscribeParameters parameters,
			final Class<?> componentType) {
		ConsumerMetadata<Runnable> metadata = new ConsumerMetadata<>();
		metadata.setInstance(instance);
		metadata.setSubscribeParameters(parameters);
		metadata.setConsumerComponentType(componentType);
		return metadata;
	}

	@Test
	public void metadataSharingInstanceAndParametersAreEqualWhateverTheComponentType() {
		Runnable instance = () -> {
		};
		SubscribeParameters parameters = new FakeParameters();

		ConsumerMetadata<Runnable> first = metadata(instance, parameters, String.class);
		ConsumerMetadata<Runnable> second = metadata(instance, parameters, Integer.class);

		Assert.assertEquals(first, second);
		Assert.assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void metadataWithAnotherInstanceOrParametersAreNotEqual() {
		Runnable instance = () -> {
		};
		SubscribeParameters parameters = new FakeParameters();
		ConsumerMetadata<Runnable> reference = metadata(instance, parameters, String.class);

		Assert.assertNotEquals(reference, metadata(() -> {
		}, parameters, String.class));
		Assert.assertNotEquals(reference, metadata(instance, new FakeParameters(), String.class));
		Assert.assertNotEquals(reference, metadata(instance, null, String.class));
	}

	@Test
	public void metadataIsNotEqualToNullOrToAnotherType() {
		ConsumerMetadata<Runnable> reference = metadata(() -> {
		}, null, String.class);

		Assert.assertEquals(reference, reference);
		Assert.assertNotEquals(reference, null);
		Assert.assertNotEquals(reference, "metadata");
	}

	@Test
	public void subscribeArgumentFallsBackToSubscribeParameters() {
		SubscribeParameters parameters = new FakeParameters();
		ConsumerMetadata<Runnable> metadata = metadata(null, parameters, String.class);

		Assert.assertSame(parameters, metadata.getSubscribeArgument());

		Object explicit = new Object();
		metadata.setSubscribeArgument(explicit);
		Assert.assertSame(explicit, metadata.getSubscribeArgument());
		Assert.assertEquals(String.class, metadata.getConsumerComponentType());
	}

	@Test
	public void subscribeArgumentsAreAbsentUntilRecorded() {
		Assert.assertNull(new ConsumerMetadata<Runnable>().getSubscribeArguments());
	}

	@Test
	public void subscribeArgumentsKeepNullEntriesAndAreCopiedOnTheWayInAndOut() {
		ConsumerMetadata<Runnable> metadata = new ConsumerMetadata<>();
		Object[] original = { "consumer", null, "extra" };

		metadata.setSubscribeArguments(original);
		original[0] = "changed";
		Object[] first = metadata.getSubscribeArguments();
		first[2] = "changed";

		Assert.assertArrayEquals(new Object[] { "consumer", null, "extra" }, metadata.getSubscribeArguments());

		metadata.setSubscribeArguments(null);
		Assert.assertNull(metadata.getSubscribeArguments());
	}
}
