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

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.OSType;

/** Window mutations happen only after the renderer has released the peer. */
@Slf4j
final class SwingFullscreenWindow implements FullscreenController.Window
{
	private final Frame frame;
	private final FullscreenPlusConfig config;
	private final BooleanSupplier customChrome;
	private Snapshot saved;
	private GraphicsDevice target;
	private FullscreenPlusConfig.Mode mode;
	private Rectangle lastNormalBounds;
	private boolean applied;
	private int rejectedBounds;

	SwingFullscreenWindow(Frame frame, FullscreenPlusConfig config, BooleanSupplier customChrome)
	{
		this.frame = frame;
		this.config = config;
		this.customChrome = customChrome;
		observeWindowedBounds();
	}

	void observeWindowedBounds()
	{
		if (saved == null && (frame.getExtendedState() & (Frame.MAXIMIZED_BOTH | Frame.ICONIFIED)) == 0)
		{
			lastNormalBounds = new Rectangle(frame.getBounds());
		}
	}

	private List<GraphicsDevice> displays()
	{
		GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice primary = environment.getDefaultScreenDevice();
		List<GraphicsDevice> devices = new ArrayList<>(Arrays.asList(environment.getScreenDevices()));
		devices.sort(Comparator.comparing((GraphicsDevice device) -> device != primary)
			.thenComparing(GraphicsDevice::getIDstring));
		return devices;
	}

	@Override
	public void prepare()
	{
		assert SwingUtilities.isEventDispatchThread();
		if (customChrome.getAsBoolean() || frame.isUndecorated())
		{
			throw new IllegalStateException("Disable 'Enable custom window chrome' in RuneLite's settings and restart RuneLite first. "
				+ "Also leave any other fullscreen mode before entering Fullscreen Plus.");
		}
		List<GraphicsDevice> devices = displays();
		String current = frame.getGraphicsConfiguration().getDevice().getIDstring();
		int index = WindowGeometry.selectMonitor(config.monitor(), current,
			devices.stream().map(GraphicsDevice::getIDstring).collect(Collectors.toList()));
		target = devices.get(index);
		mode = config.mode();
		if (mode == FullscreenPlusConfig.Mode.EXCLUSIVE
			&& (!target.isFullScreenSupported() || OSType.getOSType() == OSType.MacOS))
		{
			throw new IllegalStateException("Exclusive fullscreen is unavailable on this display. Choose Borderless.");
		}
		if (target.getFullScreenWindow() != null && target.getFullScreenWindow() != frame)
		{
			throw new IllegalStateException("Another window is using exclusive fullscreen on this monitor.");
		}
		observeWindowedBounds();
		saved = new Snapshot(frame, lastNormalBounds);
		rejectedBounds = 0;
		log.debug("Fullscreen entry: mode={}, monitor={} {}, scale={}, previous={}", mode, target.getIDstring(),
			target.getDefaultConfiguration().getBounds(), target.getDefaultConfiguration().getDefaultTransform(), saved.bounds);
	}

	@Override
	public void enter()
	{
		assert SwingUtilities.isEventDispatchThread();
		try
		{
			applied = true;
			frame.dispose();
			frame.setUndecorated(true);
			frame.setExtendedState(Frame.NORMAL);
			frame.setResizable(false);
			frame.pack();
			// pack resets preferred size; monitor bounds MUST be applied afterwards.
			frame.setBounds(target.getDefaultConfiguration().getBounds());
			frame.setVisible(true);
			if (mode == FullscreenPlusConfig.Mode.EXCLUSIVE)
			{
				target.setFullScreenWindow(frame);
			}
			else
			{
				enforceBounds();
			}
		}
		catch (RuntimeException ex)
		{
			try
			{
				restore(true);
			}
			catch (RuntimeException rollback)
			{
				ex.addSuppressed(rollback);
				try
				{
					recoverWithoutDisposing();
				}
				catch (RuntimeException recoveryFailure)
				{
					ex.addSuppressed(recoveryFailure);
				}
			}
			throw ex;
		}
	}

	@Override
	public void restore(boolean recover)
	{
		assert SwingUtilities.isEventDispatchThread();
		if (saved == null)
		{
			return;
		}
		releaseExclusive();
		frame.dispose();
		frame.setUndecorated(saved.undecorated);
		frame.setExtendedState(Frame.NORMAL);
		frame.setAlwaysOnTop(saved.alwaysOnTop);
		frame.setResizable(saved.resizable);
		frame.pack();
		frame.setOpacity(saved.opacity);
		Rectangle bounds = restoredBounds();
		frame.setBounds(bounds);
		frame.setVisible(true);
		frame.setBounds(bounds);
		if (!recover)
		{
			frame.setExtendedState(saved.state & ~Frame.ICONIFIED);
		}
		applied = false;
		log.debug("Fullscreen exit: bounds={}, state={}, recovery={}", bounds, frame.getExtendedState(), recover);
	}

	private void releaseExclusive()
	{
		if (target != null && target.getFullScreenWindow() == frame)
		{
			target.setFullScreenWindow(null);
		}
	}

	private Rectangle restoredBounds()
	{
		List<Rectangle> screens = displays().stream().map(device -> workArea(device.getDefaultConfiguration()))
			.collect(Collectors.toList());
		return WindowGeometry.restore(saved != null ? saved.bounds : frame.getBounds(), screens);
	}

	private Rectangle workArea(GraphicsConfiguration graphics)
	{
		Rectangle bounds = new Rectangle(graphics.getBounds());
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphics);
		bounds.x += insets.left;
		bounds.y += insets.top;
		bounds.width -= insets.left + insets.right;
		bounds.height -= insets.top + insets.bottom;
		return bounds;
	}

	@Override
	public void recoverWithoutDisposing()
	{
		assert SwingUtilities.isEventDispatchThread();
		releaseExclusive();
		frame.setExtendedState(Frame.NORMAL);
		if (saved != null)
		{
			frame.setAlwaysOnTop(saved.alwaysOnTop);
			frame.setResizable(saved.resizable);
			frame.setOpacity(saved.opacity);
		}
		frame.setBounds(restoredBounds());
		frame.setVisible(true);
		// If the renderer timed out, keep the peer intact. Decorations may require a client restart.
		applied = false;
	}

	@Override
	public void clear()
	{
		saved = null;
		target = null;
		applied = false;
		observeWindowedBounds();
	}

	boolean monitorAvailable()
	{
		return target == null || displays().stream().anyMatch(device -> device.getIDstring().equals(target.getIDstring()));
	}

	void enforceBounds()
	{
		if (!applied || mode != FullscreenPlusConfig.Mode.BORDERLESS || !monitorAvailable())
		{
			return;
		}
		if ((frame.getExtendedState() & Frame.ICONIFIED) != 0)
		{
			return;
		}
		// Refresh bounds after DPI, resolution or monitor-position changes; keep AWT's logical coordinates.
		GraphicsDevice device = displays().stream().filter(d -> d.getIDstring().equals(target.getIDstring()))
			.findFirst().orElse(target);
		Rectangle bounds = device.getDefaultConfiguration().getBounds();
		if (frame.getExtendedState() != Frame.NORMAL)
		{
			frame.setExtendedState(Frame.NORMAL);
		}
		if (frame.isResizable())
		{
			frame.setResizable(false);
		}
		if (!frame.getBounds().equals(bounds))
		{
			if (++rejectedBounds > 8)
			{
				throw new IllegalStateException("The window manager keeps resizing fullscreen. "
					+ "Check RuneLite's 'Contain in screen' setting and other window-management software.");
			}
			frame.setBounds(bounds);
		}
		else
		{
			rejectedBounds = 0;
		}
		boolean onTop = saved.alwaysOnTop || (config.focusedOnTop() && frame.isFocused());
		if (frame.isAlwaysOnTop() != onTop)
		{
			frame.setAlwaysOnTop(onTop);
		}
	}

	private static final class Snapshot
	{
		private final Rectangle bounds;
		private final int state;
		private final boolean alwaysOnTop;
		private final boolean resizable;
		private final boolean undecorated;
		private final float opacity;

		private Snapshot(Frame frame, Rectangle normalBounds)
		{
			state = frame.getExtendedState();
			bounds = new Rectangle((state & Frame.MAXIMIZED_BOTH) != 0 && normalBounds != null
				? normalBounds : frame.getBounds());
			alwaysOnTop = frame.isAlwaysOnTop();
			resizable = frame.isResizable();
			undecorated = frame.isUndecorated();
			opacity = frame.getOpacity();
		}
	}
}
