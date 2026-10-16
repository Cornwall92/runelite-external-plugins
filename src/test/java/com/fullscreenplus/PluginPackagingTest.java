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

import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;
import static org.junit.Assert.*;

public class PluginPackagingTest
{
	@Test public void toolbarImagesArePackagedAndTransparent() throws Exception
	{
		for (String path : Arrays.asList("fullscreen.png", "restore.png"))
		{
			BufferedImage image = ImageIO.read(FullscreenPlusPlugin.class.getResource(path));
			assertEquals(16, image.getWidth());
			assertEquals(16, image.getHeight());
			assertTrue(image.getColorModel().hasAlpha());
		}
	}

	@Test public void conflictsAreDeclaredThroughRuneLite()
	{
		PluginDescriptor descriptor = FullscreenPlusPlugin.class.getAnnotation(PluginDescriptor.class);
		assertEquals("Fullscreen Plus", descriptor.name());
		assertTrue(Arrays.asList(descriptor.conflicts()).containsAll(Arrays.asList("Fullscreen", "Client Resizer")));
		assertFalse(Arrays.asList(descriptor.conflicts()).contains("GPU"));
	}
}
