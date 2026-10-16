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

import java.util.function.Consumer;

/** Serializes user intent, GPU shutdown, window changes and plugin shutdown on the EDT. */
final class FullscreenController
{
	interface Window
	{
		void prepare();
		void enter();
		void restore(boolean recover);
		void recoverWithoutDisposing();
		void clear();
	}

	private final Window window;
	private final GpuTransition transition;
	private final Runnable changed;
	private final Consumer<Exception> error;
	private boolean enabled;
	private boolean wanted;
	private boolean active;
	private boolean recover;

	FullscreenController(Window window, GpuTransition transition, Runnable changed, Consumer<Exception> error)
	{
		this.window = window;
		this.transition = transition;
		this.changed = changed;
		this.error = error;
	}

	void start()
	{
		enabled = true;
		wanted = false;
	}

	void toggle()
	{
		if (!enabled)
		{
			return;
		}
		wanted = !wanted;
		reconcile();
	}

	void recover()
	{
		recover = true;
		wanted = false;
		if (!active && !transition.isBusy())
		{
			try
			{
				window.recoverWithoutDisposing();
			}
			catch (RuntimeException ex)
			{
				error.accept(ex);
			}
			recover = false;
		}
		reconcile();
	}

	void stop()
	{
		enabled = false;
		wanted = false;
		reconcile();
	}

	boolean isActive()
	{
		return active;
	}

	boolean isBusy()
	{
		return transition.isBusy();
	}

	private void reconcile()
	{
		if (transition.isBusy() || active == wanted)
		{
			return;
		}
		boolean entering = wanted;
		if (entering)
		{
			try
			{
				window.prepare();
			}
			catch (RuntimeException ex)
			{
				wanted = false;
				error.accept(ex);
				return;
			}
		}
		transition.run(() ->
		{
			if (entering)
			{
				// A second toggle, recovery, or shutdown can cancel an entry during the GPU wait.
				if (enabled && wanted)
				{
					window.enter();
					active = true;
				}
			}
			else
			{
				window.restore(recover);
				active = false;
				recover = false;
			}
		}, failure ->
		{
			if (failure != null)
			{
				wanted = active; // Do not repeatedly retry a failed transition.
				if ((!enabled || recover) && active)
				{
					// Never dispose an active GPU peer just to restore its decorations.
					try
					{
						window.recoverWithoutDisposing();
						active = false;
						wanted = false;
					}
					catch (RuntimeException recoveryFailure)
					{
						failure.addSuppressed(recoveryFailure);
					}
				}
				error.accept(failure);
			}
			if (!active)
			{
				recover = false;
				window.clear();
			}
			changed.run();
			reconcile();
		});
	}
}
