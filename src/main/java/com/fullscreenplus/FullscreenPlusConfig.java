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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(FullscreenPlusConfig.GROUP)
public interface FullscreenPlusConfig extends Config
{
	String GROUP = "fullscreenplus";

	enum Mode
	{
		BORDERLESS("Borderless"), EXCLUSIVE("Exclusive");

		private final String label;

		Mode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(keyName = "mode", name = "Fullscreen mode", position = 0,
		description = "Borderless fills the monitor. Exclusive uses Java's exclusive fullscreen support. Applies on next entry.")
	default Mode mode()
	{
		return Mode.BORDERLESS;
	}

	@Range(min = 0, max = 16)
	@ConfigItem(keyName = "monitor", name = "Monitor", position = 1,
		description = "0 = current monitor, 1 = primary, 2+ = other displays ordered by device ID. Applies on next entry.")
	default int monitor()
	{
		return 0;
	}

	@ConfigItem(keyName = "toggleKey", name = "Toggle fullscreen", position = 2,
		description = "Keyboard shortcut to enter or leave fullscreen.")
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_F11, 0);
	}

	@ConfigItem(keyName = "recoveryKey", name = "Recover window", position = 3,
		description = "Leave fullscreen and bring the window onto an available display. Works while RuneLite has focus.")
	default Keybind recoveryKey()
	{
		return new Keybind(KeyEvent.VK_F11, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(keyName = "startupFullscreen", name = "Fullscreen on startup", position = 4,
		description = "Enter fullscreen after enabling the plugin. Off by default.")
	default boolean startupFullscreen()
	{
		return false;
	}

	@ConfigItem(keyName = "focusedOnTop", name = "Cover taskbar when focused", position = 5,
		description = "Keep the focused borderless window above the taskbar. Release this when another app has focus.")
	default boolean focusedOnTop()
	{
		return true;
	}
}
