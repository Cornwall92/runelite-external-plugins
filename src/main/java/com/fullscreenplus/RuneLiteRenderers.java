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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.gpu.GpuPlugin;

/** Only restarts renderers actually paused by this transition and still enabled by the user. */
final class RuneLiteRenderers implements GpuTransition.Renderers
{
	private final Client client;
	private final ClientThread clientThread;
	private final PluginManager plugins;
	private final List<Plugin> paused = new ArrayList<>();

	RuneLiteRenderers(Client client, ClientThread clientThread, PluginManager plugins)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.plugins = plugins;
	}

	private Set<Plugin> renderers()
	{
		Set<Plugin> result = new LinkedHashSet<>();
		for (Plugin plugin : plugins.getPlugins())
		{
			if (plugin instanceof GpuPlugin)
			{
				result.add(plugin);
				result.addAll(plugins.conflictsForPlugin(plugin));
				break;
			}
		}
		return result;
	}

	@Override
	public void pause() throws Exception
	{
		assert SwingUtilities.isEventDispatchThread();
		paused.clear();
		for (Plugin plugin : renderers())
		{
			if (plugins.isPluginActive(plugin))
			{
				// PluginManager removes its active flag even if shutDown throws.
				paused.add(plugin);
				if (!plugins.stopPlugin(plugin))
				{
					paused.remove(plugin);
				}
			}
		}
	}

	@Override
	public void resume() throws Exception
	{
		assert SwingUtilities.isEventDispatchThread();
		Exception failure = null;
		for (Plugin plugin : new ArrayList<>(paused))
		{
			boolean anotherRenderer = renderers().stream()
				.anyMatch(other -> other != plugin && plugins.isPluginActive(other));
			if (anotherRenderer || !plugins.isPluginEnabled(plugin) || plugins.isPluginActive(plugin))
			{
				continue;
			}
			try
			{
				plugins.startPlugin(plugin);
			}
			catch (Exception ex)
			{
				if (failure == null)
				{
					failure = new IllegalStateException("The graphics renderer could not restart. Re-enable GPU/117 HD.", ex);
				}
				else
				{
					failure.addSuppressed(ex);
				}
			}
		}
		paused.clear();
		if (failure != null)
		{
			throw failure;
		}
	}

	@Override
	public void queryGpu(Consumer<Boolean> callback)
	{
		clientThread.invokeLater(() ->
		{
			boolean gpu = client.isGpu();
			SwingUtilities.invokeLater(() -> callback.accept(gpu));
		});
	}
}
