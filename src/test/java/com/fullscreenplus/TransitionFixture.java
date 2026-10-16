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
import java.util.List;
import java.util.function.Consumer;

final class TransitionFixture
{
	final List<String> events = new ArrayList<>();
	final Clock clock = new Clock();
	final Renderer renderer = new Renderer();
	final GpuTransition transition = new GpuTransition(renderer, clock);
	final List<Exception> results = new ArrayList<>();

	void run()
	{
		transition.run(() -> events.add("window"), results::add);
	}

	final class Renderer implements GpuTransition.Renderers
	{
		final List<Consumer<Boolean>> replies = new ArrayList<>();
		RuntimeException pauseFailure;
		RuntimeException resumeFailure;
		RuntimeException queryFailure;
		boolean synchronous;

		@Override public void pause()
		{
			events.add("pause");
			if (pauseFailure != null) { throw pauseFailure; }
		}

		@Override public void resume()
		{
			events.add("resume");
			if (resumeFailure != null) { throw resumeFailure; }
		}

		@Override public void queryGpu(Consumer<Boolean> reply)
		{
			if (queryFailure != null) { throw queryFailure; }
			replies.add(reply);
			if (synchronous) { reply.accept(false); }
		}

		void reply(boolean gpu)
		{
			replies.get(replies.size() - 1).accept(gpu);
		}
	}

	static final class Clock implements GpuTransition.Scheduler
	{
		long now;
		final List<Job> jobs = new ArrayList<>();

		@Override public GpuTransition.Cancel later(int delay, Runnable action)
		{
			Job job = new Job(now + delay, action);
			jobs.add(job);
			return () -> job.cancelled = true;
		}

		void advance(int milliseconds)
		{
			long end = now + milliseconds;
			while (true)
			{
				Job next = jobs.stream().filter(job -> !job.cancelled && job.at <= end)
					.min(java.util.Comparator.comparingLong(job -> job.at)).orElse(null);
				if (next == null) { break; }
				now = next.at;
				next.cancelled = true;
				next.action.run();
			}
			now = end;
		}

		boolean idle()
		{
			return jobs.stream().allMatch(job -> job.cancelled);
		}

		static final class Job
		{
			final long at;
			final Runnable action;
			boolean cancelled;
			Job(long at, Runnable action) { this.at = at; this.action = action; }
		}
	}
}
