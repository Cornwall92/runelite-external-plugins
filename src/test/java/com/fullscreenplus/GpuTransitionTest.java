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

import java.util.Arrays;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuTransitionTest
{
	@Test public void waitsForGpuBeforeTouchingThePeer()
	{
		TransitionFixture f = new TransitionFixture();
		f.run();
		f.renderer.reply(true);
		assertEquals(Arrays.asList("pause"), f.events);
		f.clock.advance(50);
		f.renderer.reply(false);
		assertEquals(Arrays.asList("pause", "window", "resume"), f.events);
		assertEquals(1, f.results.size());
		assertNull(f.results.get(0));
		assertFalse(f.transition.isBusy());
		assertTrue(f.clock.idle());
	}

	@Test public void missingGameThreadReplyTimesOutWithoutDisposing()
	{
		TransitionFixture f = new TransitionFixture();
		f.run();
		f.clock.advance(5000);
		assertEquals(Arrays.asList("pause", "resume"), f.events);
		assertNotNull(f.results.get(0));
		assertFalse(f.transition.isBusy());
		assertTrue(f.clock.idle());
	}

	@Test public void timedOutReplyCannotMutateANewerTransition()
	{
		TransitionFixture f = new TransitionFixture();
		f.run();
		Consumer<Boolean> old = f.renderer.replies.get(0);
		f.clock.advance(5000);
		f.run();
		old.accept(false);
		assertFalse(f.events.contains("window"));
		assertTrue(f.transition.isBusy());
		f.renderer.reply(false);
		assertEquals(2, f.results.size());
		assertNull(f.results.get(1));
	}

	@Test public void operationFailureStillRestartsRenderer()
	{
		TransitionFixture f = new TransitionFixture();
		RuntimeException cause = new IllegalStateException("window failed");
		f.transition.run(() -> { throw cause; }, f.results::add);
		f.renderer.reply(false);
		assertSame(cause, f.results.get(0));
		assertEquals(Arrays.asList("pause", "resume"), f.events);
	}

	@Test public void partialPauseFailureStillRestartsRenderer()
	{
		TransitionFixture f = new TransitionFixture();
		f.renderer.pauseFailure = new IllegalStateException("stop failed");
		f.run();
		assertEquals(Arrays.asList("pause", "resume"), f.events);
		assertSame(f.renderer.pauseFailure, f.results.get(0));
		assertTrue(f.clock.idle());
	}

	@Test public void restartFailureIsReportedAndDoesNotLeaveBusyFlag()
	{
		TransitionFixture f = new TransitionFixture();
		f.renderer.resumeFailure = new IllegalStateException("restart failed");
		f.run();
		f.renderer.reply(false);
		assertSame(f.renderer.resumeFailure, f.results.get(0));
		assertFalse(f.transition.isBusy());
	}

	@Test public void retainsBothOperationAndRestartFailures()
	{
		TransitionFixture f = new TransitionFixture();
		RuntimeException cause = new IllegalStateException("window failed");
		f.renderer.resumeFailure = new IllegalStateException("restart failed");
		f.transition.run(() -> { throw cause; }, f.results::add);
		f.renderer.reply(false);
		assertSame(cause, f.results.get(0));
		assertArrayEquals(new Throwable[]{f.renderer.resumeFailure}, cause.getSuppressed());
	}

	@Test public void subsequentPollExceptionIsCaught()
	{
		TransitionFixture f = new TransitionFixture();
		f.run();
		f.renderer.reply(true);
		f.renderer.queryFailure = new IllegalStateException("query failed");
		f.clock.advance(50);
		assertSame(f.renderer.queryFailure, f.results.get(0));
		assertFalse(f.transition.isBusy());
		assertTrue(f.clock.idle());
	}

	@Test public void synchronousReplyCancelsTimeout()
	{
		TransitionFixture f = new TransitionFixture();
		f.renderer.synchronous = true;
		f.run();
		f.clock.advance(6000);
		assertEquals(Arrays.asList("pause", "window", "resume"), f.events);
		assertEquals(1, f.results.size());
		assertTrue(f.clock.idle());
	}

	@Test(expected = IllegalStateException.class)
	public void rejectsOverlappingOperations()
	{
		TransitionFixture f = new TransitionFixture();
		f.run();
		f.run();
	}
}
