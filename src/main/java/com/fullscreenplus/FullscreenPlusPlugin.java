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

import com.google.inject.Provides;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ContainableFrame;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Fullscreen Plus",
	configName = "fullscreenplus",
	internalName = "fullscreen-plus",
	description = "Fullscreen with monitor selection, recovery, and renderer-aware transitions",
	tags = {"fullscreen", "borderless", "window", "monitor", "resize"},
	conflicts = {"Fullscreen", "Client Resizer"}
)
public class FullscreenPlusPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientUI clientUI;
	@Inject private ClientToolbar toolbar;
	@Inject private KeyManager keys;
	@Inject private PluginManager plugins;
	@Inject private FullscreenPlusConfig config;
	@Inject private ConfigManager configs;

	private Frame frame;
	private SwingFullscreenWindow window;
	private FullscreenController controller;
	private NavigationButton button;
	private HotkeyListener toggle;
	private HotkeyListener recovery;
	private Timer monitorTimer;
	private Timer startupTimer;
	private boolean running;
	private boolean boundsQueued;
	private JDialog errorDialog;

	private final ComponentAdapter boundsListener = new ComponentAdapter()
	{
		@Override public void componentMoved(ComponentEvent event) { boundsChanged(); }
		@Override public void componentResized(ComponentEvent event) { boundsChanged(); }
	};
	private final WindowAdapter focusListener = new WindowAdapter()
	{
		@Override public void windowGainedFocus(WindowEvent event) { boundsChanged(); }
		@Override public void windowLostFocus(WindowEvent event) { boundsChanged(); }
	};

	@Override
	protected void startUp()
	{
		assert SwingUtilities.isEventDispatchThread();
		frame = findFrame();
		if (frame == null)
		{
			log.warn("Fullscreen Plus could not find the RuneLite window");
			return;
		}
		running = true;
		if (controller == null)
		{
			window = new SwingFullscreenWindow(frame, config,
				() -> configs.getConfig(RuneLiteConfig.class).enableCustomChrome());
			GpuTransition transition = new GpuTransition(new RuneLiteRenderers(client, clientThread, plugins),
				(milliseconds, action) ->
				{
					Timer timer = new Timer(milliseconds, event -> action.run());
					timer.setRepeats(false);
					timer.start();
					return timer::stop;
				});
			controller = new FullscreenController(window, transition, this::transitionFinished, this::showError);
		}
		controller.start();
		registerHotkeys();
		updateButton();
		frame.addComponentListener(boundsListener);
		frame.addWindowFocusListener(focusListener);
		monitorTimer = new Timer(1000, event -> checkWindow());
		monitorTimer.start();
		if (config.startupFullscreen())
		{
			startupTimer = new Timer(1000, event ->
			{
				if (running && !controller.isActive() && !controller.isBusy())
				{
					controller.toggle();
				}
			});
			startupTimer.setRepeats(false);
			startupTimer.start();
		}
	}

	@Override
	protected void shutDown()
	{
		assert SwingUtilities.isEventDispatchThread();
		running = false;
		if (startupTimer != null) { startupTimer.stop(); startupTimer = null; }
		if (monitorTimer != null) { monitorTimer.stop(); monitorTimer = null; }
		unregisterHotkeys();
		if (button != null) { toolbar.removeNavigation(button); button = null; }
		if (frame != null)
		{
			frame.removeComponentListener(boundsListener);
			frame.removeWindowFocusListener(focusListener);
		}
		if (errorDialog != null) { errorDialog.dispose(); errorDialog = null; }
		if (controller != null)
		{
			controller.stop(); // Bounded asynchronous restoration; never block the EDT.
		}
	}

	private void registerHotkeys()
	{
		unregisterHotkeys();
		toggle = new HotkeyListener(config::toggleKey)
		{
			@Override public void hotkeyPressed() { dispatch(false); }
		};
		recovery = new HotkeyListener(config::recoveryKey)
		{
			@Override public void hotkeyPressed() { dispatch(true); }
		};
		keys.registerKeyListener(toggle);
		keys.registerKeyListener(recovery);
	}

	private void unregisterHotkeys()
	{
		if (toggle != null) { keys.unregisterKeyListener(toggle); toggle = null; }
		if (recovery != null) { keys.unregisterKeyListener(recovery); recovery = null; }
	}

	private void dispatch(boolean recover)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (running)
			{
				if (recover) { controller.recover(); }
				else { controller.toggle(); }
			}
		});
	}

	private void updateButton()
	{
		if (button != null) { toolbar.removeNavigation(button); }
		Map<String, Runnable> popup = new LinkedHashMap<>();
		popup.put("Recover window", () -> dispatch(true));
		boolean active = controller.isActive();
		button = NavigationButton.builder()
			.tooltip(active ? "Fullscreen Plus: restore window" : "Fullscreen Plus: enter fullscreen")
			.icon(ImageUtil.loadImageResource(getClass(), active ? "restore.png" : "fullscreen.png"))
			.priority(100)
			.onClick(() -> dispatch(false))
			.popup(popup)
			.build(); // No panel: RuneLite places action buttons at the bottom of the sidebar.
		toolbar.addNavigation(button);
	}

	private void transitionFinished()
	{
		if (running)
		{
			// Recreating the peer may swallow the original key release.
			registerHotkeys();
			updateButton();
			clientUI.requestFocus();
		}
	}

	private void boundsChanged()
	{
		if (!running || boundsQueued) { return; }
		boundsQueued = true;
		SwingUtilities.invokeLater(() ->
		{
			boundsQueued = false;
			if (running) { checkWindow(); }
		});
	}

	private void checkWindow()
	{
		if (controller.isBusy()) { return; }
		if (controller.isActive())
		{
			if (!window.monitorAvailable())
			{
				log.warn("Fullscreen monitor disconnected; restoring the window");
				controller.recover();
			}
			else
			{
				try
				{
					window.enforceBounds();
				}
				catch (RuntimeException ex)
				{
					controller.recover();
					showError(ex);
				}
			}
		}
		else
		{
			window.observeWindowedBounds();
		}
	}

	private void showError(Exception error)
	{
		log.warn("Fullscreen Plus: {}", error.getMessage(), error);
		if (!running)
		{
			log.warn("Window recovery finished after shutdown. If decorations are missing, restart RuneLite.");
			return;
		}
		if (errorDialog != null) { errorDialog.dispose(); }
		JOptionPane pane = new JOptionPane(error.getMessage(), JOptionPane.WARNING_MESSAGE);
		errorDialog = pane.createDialog(frame, "Fullscreen Plus");
		errorDialog.setModal(false);
		errorDialog.setVisible(true);
	}

	private Frame findFrame()
	{
		for (Frame candidate : Frame.getFrames())
		{
			if (candidate instanceof ContainableFrame) { return candidate; }
		}
		return null;
	}

	@Provides
	FullscreenPlusConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(FullscreenPlusConfig.class);
	}
}
