# Validation

## Automated checks

Run `./gradlew clean build` on JDK 11 and 17. The source uses `--release 11`.

The regression suite has 35 tests:

| Area | Tests |
| --- | ---: |
| GPU transition ordering, timeout, stale replies, failure cleanup | 10 |
| Toggle coalescing, startup/shutdown state, recovery and retry | 14 |
| Monitor selection and restoration geometry | 9 |
| Packaged sidebar images and conflict declaration | 2 |

Local execution on Linux with Temurin 11.0.32.1 and 17.0.20.1 passed all 35 tests on each JDK. The development launcher also built on Java 17. A separate build using the official Plugin Hub tooling v5 standard build, target initialization script and API recorder produced a plugin JAR against RuneLite 1.13.2 on Java 11. The plugin classes have Java 11 class-file version 55, and all three PNG assets have the intended dimensions and transparency.

No desktop or RuneScape client interaction was performed. Upstream CI and eligibility review remain separate gates; the local standard packaging build is not the complete remote Hub pipeline, and passing unit tests is not evidence of Windows compatibility or Hub eligibility.

## Manual acceptance checks — pending

Use a real Windows client and the user's usual two-monitor layout. Do not automate game input.

1. Disable custom window chrome and restart. Confirm Fullscreen Plus gives a clear message if chrome is still enabled.
2. Start with a small normal window. Enter borderless with F11 and with the bottom-sidebar button. Confirm all monitor edges fill and the taskbar is covered while focused.
3. Repeat from a maximized window and a window with its size locked. Exit and check previous bounds, maximize state, resizability, opacity and always-on-top behavior.
4. Open and close plugin panels with both automatic-resize settings. Confirm the window stays on its selected monitor and does not spill over. Prefer Keep window size for normal use.
5. Test current-monitor and explicit-monitor selection on each screen, including a display left of the primary, different resolutions, and 100%/125%/150% scaling. Change resolution while fullscreen and confirm bounds update.
6. Alt-Tab to another application, minimize, restore, and return. Confirm the taskbar-coverage option does not force RuneLite over the focused application or immediately undo minimization.
7. Toggle rapidly, then disable the plugin during entry and during fullscreen. Confirm it restores safely, removes the sidebar button, and unregisters both shortcuts.
8. Repeat with software rendering, core GPU, and 117 HD separately. Verify the same enabled renderer returns and the game renders normally after every transition. Change renderer selection during a delayed transition and verify the user's selection is respected.
9. Test exclusive mode on each supported display. Return to windowed mode and verify the original device is released. Check that no desktop resolution was changed.
10. Test Ctrl+Shift+F11 and the button's recovery menu, including when the fullscreen monitor is disconnected. Check that a visible normal window is recovered on a remaining monitor.
11. Enable Fullscreen Plus while Fullscreen or Client Resizer is enabled. Confirm RuneLite disables the conflicting plugin; reverse the process and verify Fullscreen Plus shuts down cleanly.
12. Restart the client with startup fullscreen off, then on. Test enabling the plugin while already maximized. Confirm the documented fallback bounds are acceptable.
13. For the game canvas, test both OSRS resizable layouts. Fixed mode and Stretched Mode should retain their own behavior.

Capture the RuneLite version, launcher Java version, renderer/version, monitor layout and scaling, fullscreen settings, and relevant `com.fullscreenplus` log lines when reporting a failure. Remove account details or other private log content before publishing.
