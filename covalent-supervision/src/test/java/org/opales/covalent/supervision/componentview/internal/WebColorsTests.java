/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import javafx.scene.paint.Color;

import org.junit.Assert;
import org.junit.Test;

/** {@link WebColors} turns colors into the {@code #rrggbbaa} strings the layers below the views exchange. */
public class WebColorsTests {

	@Test
	public void aColorIsFormattedAsEightHexDigits() {
		Assert.assertEquals("#ff0000ff", WebColors.toWeb(Color.RED));
		Assert.assertEquals("#ffffffff", WebColors.toWeb(Color.WHITE));
		Assert.assertEquals("#000000ff", WebColors.toWeb(Color.BLACK));
		Assert.assertEquals("#00000000", WebColors.toWeb(Color.TRANSPARENT));
		Assert.assertEquals("#12345678", WebColors.toWeb(Color.rgb(0x12, 0x34, 0x56, 0x78 / 255.0)));
	}

	@Test
	public void theOpacityIsRoundedToTheNearestStep() {
		Assert.assertEquals("#ff000080", WebColors.toWeb(Color.rgb(255, 0, 0, 0.5)));
	}

	@Test
	public void noColorIsNoString() {
		Assert.assertNull(WebColors.toWeb(null));
	}

	@Test
	public void aWebStringIsParsedWithItsOpacity() {
		Color color = WebColors.toColor("#ff000080");

		Assert.assertEquals(1.0, color.getRed(), 0.0);
		Assert.assertEquals(0.0, color.getGreen(), 0.0);
		Assert.assertEquals(0.0, color.getBlue(), 0.0);
		Assert.assertEquals(0x80 / 255.0, color.getOpacity(), 0.001);
	}

	@Test
	public void theUsualWebNotationsAreAccepted() {
		Assert.assertEquals(Color.RED, WebColors.toColor("#ff0000"));
		Assert.assertEquals(Color.RED, WebColors.toColor("red"));
		Assert.assertEquals(Color.web("#112233"), WebColors.toColor("#123"));
	}

	@Test
	public void anUnreadableOrAbsentStringIsNoColor() {
		Assert.assertNull(WebColors.toColor(null));
		Assert.assertNull(WebColors.toColor(""));
		Assert.assertNull(WebColors.toColor("not a color"));
		Assert.assertNull(WebColors.toColor("#12345"));
	}

	@Test
	public void aColorSurvivesTheRoundTrip() {
		for (String web : new String[] { "#12345678", "#00000000", "#ffffffff", "#fedcba01" }) {
			Assert.assertEquals(web, WebColors.toWeb(WebColors.toColor(web)));
		}
	}
}
