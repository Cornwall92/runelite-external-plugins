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
import java.util.List;

final class WindowGeometry
{
	private WindowGeometry()
	{
	}

	static int selectMonitor(int configured, String current, List<String> ids)
	{
		if (ids.isEmpty())
		{
			throw new IllegalStateException("No display is available");
		}
		if (configured > 0)
		{
			if (configured > ids.size())
			{
				throw new IllegalStateException("Monitor " + configured + " is unavailable. Choose 0 for the current monitor.");
			}
			return configured - 1;
		}
		int index = ids.indexOf(current);
		return index >= 0 ? index : 0;
	}

	static Rectangle fit(Rectangle saved, Rectangle screen)
	{
		int width = Math.max(1, Math.min(saved.width, screen.width));
		int height = Math.max(1, Math.min(saved.height, screen.height));
		int x = Math.max(screen.x, Math.min(saved.x, screen.x + screen.width - width));
		int y = Math.max(screen.y, Math.min(saved.y, screen.y + screen.height - height));
		return new Rectangle(x, y, width, height);
	}

	static Rectangle restore(Rectangle saved, List<Rectangle> available)
	{
		if (available.isEmpty())
		{
			throw new IllegalStateException("No display is available");
		}
		Rectangle best = available.get(0);
		long largest = 0;
		for (Rectangle screen : available)
		{
			Rectangle intersection = saved.intersection(screen);
			long area = intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
			if (area > largest)
			{
				largest = area;
				best = screen;
			}
		}
		return fit(saved, best);
	}
}
