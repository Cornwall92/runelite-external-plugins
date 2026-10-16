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

/** One bounded, cancellable wait. All methods and callbacks run on the EDT. */
final class GpuTransition
{
	interface Renderers
	{
		void pause() throws Exception;
		void resume() throws Exception;
		void queryGpu(Consumer<Boolean> callback);
	}

	interface Cancel
	{
		void cancel();
	}

	interface Scheduler
	{
		Cancel later(int milliseconds, Runnable action);
	}

	static final int TIMEOUT_MS = 5000;
	private static final int POLL_MS = 50;
	private final Renderers renderers;
	private final Scheduler scheduler;
	private long generation;
	private boolean busy;
	private Cancel timeout;
	private Cancel poll;

	GpuTransition(Renderers renderers, Scheduler scheduler)
	{
		this.renderers = renderers;
		this.scheduler = scheduler;
	}

	boolean isBusy()
	{
		return busy;
	}

	void run(Runnable operation, Consumer<Exception> complete)
	{
		if (busy)
		{
			throw new IllegalStateException("A fullscreen transition is already running");
		}
		busy = true;
		long token = ++generation;
		try
		{
			renderers.pause();
			timeout = scheduler.later(TIMEOUT_MS, () -> finish(token,
				new IllegalStateException("The graphics renderer did not stop within 5 seconds. "
					+ "The window was not recreated. Disable GPU/117 HD and try again."), complete));
			check(token, operation, complete);
		}
		catch (Exception ex)
		{
			finish(token, ex, complete);
		}
	}

	private void check(long token, Runnable operation, Consumer<Exception> complete)
	{
		if (!busy || token != generation)
		{
			return;
		}
		try
		{
			renderers.queryGpu(gpu ->
			{
				if (!busy || token != generation)
				{
					return;
				}
				try
				{
					if (gpu)
					{
						poll = scheduler.later(POLL_MS, () -> check(token, operation, complete));
						return;
					}
					operation.run();
				}
				catch (Exception ex)
				{
					finish(token, ex, complete);
					return;
				}
				finish(token, null, complete);
			});
		}
		catch (Exception ex)
		{
			finish(token, ex, complete);
		}
	}

	private void finish(long token, Exception failure, Consumer<Exception> complete)
	{
		if (!busy || token != generation)
		{
			return;
		}
		++generation; // Invalidate outstanding game-thread replies before resuming a renderer.
		if (timeout != null)
		{
			timeout.cancel();
			timeout = null;
		}
		if (poll != null)
		{
			poll.cancel();
			poll = null;
		}
		try
		{
			renderers.resume();
		}
		catch (Exception ex)
		{
			if (failure == null)
			{
				failure = ex;
			}
			else
			{
				failure.addSuppressed(ex);
			}
		}
		busy = false;
		complete.accept(failure);
	}
}
