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
import org.junit.Test;
import static org.junit.Assert.*;

public class FullscreenControllerTest
{
	private final TransitionFixture f = new TransitionFixture();
	private final Window window = new Window();
	private final List<Exception> errors = new ArrayList<>();
	private int changes;
	private final FullscreenController controller = new FullscreenController(window, f.transition, () -> ++changes, errors::add);

	private void enter()
	{
		controller.start();
		controller.toggle();
		f.renderer.reply(false);
		assertTrue(controller.isActive());
	}

	@Test public void roundTripRestoresOriginalWindow()
	{
		enter();
		controller.toggle();
		f.renderer.reply(false);
		assertFalse(controller.isActive());
		assertFalse(window.lastRecovery);
		assertEquals(1, window.entries);
		assertEquals(1, window.exits);
		assertEquals(1, window.clears);
		assertEquals(2, changes);
	}

	@Test public void secondToggleCancelsPendingEntry()
	{
		controller.start();
		controller.toggle();
		controller.toggle();
		f.renderer.reply(false);
		assertFalse(controller.isActive());
		assertEquals(0, window.entries);
		assertFalse(controller.isBusy());
	}

	@Test public void tripleToggleCoalescesIntoOneEntry()
	{
		controller.start();
		controller.toggle();
		controller.toggle();
		controller.toggle();
		f.renderer.reply(false);
		assertTrue(controller.isActive());
		assertEquals(1, window.entries);
	}

	@Test public void shutdownCancelsPendingEntryAndIgnoresFurtherToggles()
	{
		controller.start();
		controller.toggle();
		controller.stop();
		controller.toggle();
		f.renderer.reply(false);
		assertEquals(0, window.entries);
		assertFalse(controller.isActive());
		assertTrue(f.clock.idle());
	}

	@Test public void shutdownRestoresAnActiveWindow()
	{
		enter();
		controller.stop();
		f.renderer.reply(false);
		assertEquals(1, window.exits);
		assertFalse(controller.isActive());
	}

	@Test public void shutdownTimeoutUsesRecoveryWithoutDisposing()
	{
		enter();
		controller.stop();
		f.clock.advance(5000);
		assertEquals(0, window.exits);
		assertEquals(1, window.safeRecoveries);
		assertFalse(controller.isActive());
		assertEquals(1, errors.size());
	}

	@Test public void explicitRecoveryTimeoutAlsoUsesSafeFallback()
	{
		enter();
		controller.recover();
		f.clock.advance(5000);
		assertEquals(1, window.safeRecoveries);
		assertFalse(controller.isActive());
	}

	@Test public void toggleDuringExitReentersAfterRestoring()
	{
		enter();
		controller.toggle();
		controller.toggle();
		f.renderer.reply(false);
		assertTrue(controller.isBusy());
		assertFalse(controller.isActive());
		f.renderer.reply(false);
		assertTrue(controller.isActive());
		assertEquals(2, window.entries);
		assertEquals(1, window.exits);
	}

	@Test public void recoveryDuringPendingEntryDoesNotAffectLaterNormalExit()
	{
		controller.start();
		controller.toggle();
		controller.recover();
		f.renderer.reply(false);
		controller.toggle();
		f.renderer.reply(false);
		controller.toggle();
		f.renderer.reply(false);
		assertFalse(window.lastRecovery);
	}

	@Test public void prepareFailureDoesNotPauseGpuOrRetry()
	{
		window.failPrepare = true;
		controller.start();
		controller.toggle();
		assertEquals(1, errors.size());
		assertTrue(f.events.isEmpty());
		assertFalse(controller.isBusy());
	}

	@Test public void entryFailureLeavesUsWindowedAndAllowsRetry()
	{
		window.failEnter = true;
		controller.start();
		controller.toggle();
		f.renderer.reply(false);
		assertFalse(controller.isActive());
		assertEquals(1, errors.size());
		window.failEnter = false;
		controller.toggle();
		f.renderer.reply(false);
		assertTrue(controller.isActive());
	}

	@Test public void rendererRestartFailureKeepsActualWindowState()
	{
		f.renderer.resumeFailure = new IllegalStateException("restart");
		enter();
		assertEquals(1, errors.size());
		assertFalse(controller.isBusy());
	}

	@Test public void recoveryFailureIsReportedWithoutEscapingCallback()
	{
		enter();
		window.failRecovery = true;
		controller.stop();
		f.clock.advance(5000);
		assertEquals(1, errors.size());
		assertEquals(1, errors.get(0).getSuppressed().length);
		assertFalse(controller.isBusy());
	}

	@Test public void windowedRecoveryDoesNotRestartRenderer()
	{
		controller.start();
		controller.recover();
		assertEquals(1, window.safeRecoveries);
		assertTrue(f.events.isEmpty());
	}

	private static final class Window implements FullscreenController.Window
	{
		int entries, exits, clears, safeRecoveries;
		boolean lastRecovery, failPrepare, failEnter, failRecovery;
		@Override public void prepare()
		{
			if (failPrepare) { throw new IllegalStateException("chrome"); }
		}
		@Override public void enter()
		{
			if (failEnter) { throw new IllegalStateException("entry"); }
			++entries;
		}
		@Override public void restore(boolean recover) { ++exits; lastRecovery = recover; }
		@Override public void recoverWithoutDisposing()
		{
			if (failRecovery) { throw new IllegalStateException("recovery"); }
			++safeRecoveries;
		}
		@Override public void clear() { ++clears; }
	}
}
