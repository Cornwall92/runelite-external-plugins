/*
 * Copyright (c) 2020, dekvall <https://github.com/dekvall>
 * Copyright (c) 2026, Cornwall92
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.fullscreenplus;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class WindowGeometryTest
{
	@Test public void currentMonitorUsesDeviceId()
	{
		assertEquals(1, WindowGeometry.selectMonitor(0, "left", Arrays.asList("primary", "left")));
	}
	@Test public void primaryMonitorIsFirstAndMissingCurrentFallsBack()
	{
		assertEquals(0, WindowGeometry.selectMonitor(1, "left", Arrays.asList("primary", "left")));
		assertEquals(0, WindowGeometry.selectMonitor(0, "removed", Arrays.asList("primary", "left")));
	}
	@Test(expected = IllegalStateException.class) public void unavailableExplicitMonitorIsRejected()
	{
		WindowGeometry.selectMonitor(3, "primary", Arrays.asList("primary", "left"));
	}
	@Test(expected = IllegalStateException.class) public void noMonitorsIsRejected()
	{
		WindowGeometry.selectMonitor(0, "primary", Collections.emptyList());
	}
	@Test public void negativeOriginsAndLogicalScalingArePreserved()
	{
		Rectangle logicalDisplay = new Rectangle(-1707, -200, 1707, 960);
		Rectangle saved = new Rectangle(-1600, -150, 900, 600);
		assertEquals(saved, WindowGeometry.restore(saved, Arrays.asList(new Rectangle(0, 0, 1920, 1040), logicalDisplay)));
	}
	@Test public void disconnectedMonitorRestoresOntoPrimaryWorkArea()
	{
		Rectangle result = WindowGeometry.restore(new Rectangle(2400, 100, 900, 600),
			Collections.singletonList(new Rectangle(0, 0, 1920, 1040)));
		assertEquals(new Rectangle(1020, 100, 900, 600), result);
	}
	@Test public void oversizedWindowFitsWithoutChangingOriginalSnapshot()
	{
		Rectangle saved = new Rectangle(-2000, -500, 3000, 2000);
		Rectangle copy = new Rectangle(saved);
		assertEquals(new Rectangle(-1280, 0, 1280, 680), WindowGeometry.fit(saved, new Rectangle(-1280, 0, 1280, 680)));
		assertEquals(copy, saved);
	}
	@Test public void straddlingWindowUsesGreatestOverlap()
	{
		assertEquals(new Rectangle(-800, 10, 800, 600), WindowGeometry.restore(new Rectangle(-600, 10, 800, 600),
			Arrays.asList(new Rectangle(0, 0, 1920, 1040), new Rectangle(-1280, 0, 1280, 1000))));
	}
	@Test(expected = IllegalStateException.class) public void cannotRestoreWithoutDisplays()
	{
		WindowGeometry.restore(new Rectangle(1, 1, 800, 600), Collections.emptyList());
	}
}
