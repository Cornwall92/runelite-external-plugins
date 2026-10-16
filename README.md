# Fullscreen Plus

![Fullscreen Plus icon](icon.png)

A RuneLite fullscreen plugin with a bottom-sidebar resize button, borderless and exclusive modes, monitor selection, and window recovery.

**Development candidate — not available in Plugin Hub.** RuneLite's [current rejected-features policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) excludes new plugins that manipulate the root frame; older plugins may be grandfathered. This project requires an eligibility decision from RuneLite before any Hub release. Automated checks do not establish Windows, GPU-driver, or in-game compatibility.

## Features

- **Borderless fullscreen** applies the monitor's full bounds after recreating and packing the window, then corrects later sidebar/layout changes. This addresses the old behavior where packing restored the previous window size.
- **Exclusive fullscreen** uses Java's display-device fullscreen API when supported. It does not change display resolution.
- A simple **resize button at the bottom of the plugin sidebar**, with a different restore icon while fullscreen. Right-click it for **Recover window**.
- Configurable **F11** toggle and **Ctrl+Shift+F11** recovery shortcuts.
- Monitor selection: **0** uses the current monitor, **1** is the primary display, and **2+** are the remaining devices ordered by device ID. A missing explicitly selected display produces an error instead of silently choosing another display.
- Preserves the prior window bounds, maximize state, resizability, opacity, and always-on-top setting on a normal exit. Restoration fits the window into an available monitor's work area when needed.
- Responds to monitor disconnection and resolution changes. Uses AWT logical coordinates without applying DPI scaling a second time.
- Optional fullscreen after enabling the plugin; **off by default**.
- Borderless taskbar coverage while RuneLite has focus, without adding always-on-top behavior when another application has focus. An existing RuneLite always-on-top preference is preserved.
- Serializes toggles and renderer shutdown. Waits for the game thread to report that GPU rendering has stopped before recreating the window. Stops waiting after five seconds and reports the failure.

## Setup and use

1. In RuneLite's settings, turn **Enable custom window chrome** off, then restart RuneLite. Fullscreen Plus refuses to recreate an undecorated/custom-chrome window.
2. Enable Fullscreen Plus. RuneLite's built-in conflict handling disables **Fullscreen** and **Client Resizer** to prevent competing window changes. Enabling either of those plugins subsequently disables Fullscreen Plus. Disabling Fullscreen Plus does not automatically re-enable the old plugins.
3. Choose **Borderless** and monitor **0** to start. Mode and monitor changes apply on the next fullscreen entry.
4. Click the bottom-sidebar resize button or press **F11**.
5. Open and close a plugin panel. RuneLite's **Automatic resize → Keep window size** gives the smoothest behavior; the plugin does not change this setting for you.
6. Press **F11** again to restore the previous window. If the window becomes misplaced, use **Ctrl+Shift+F11** while RuneLite has keyboard focus, or right-click the button and choose **Recover window**.

The OS window fills the monitor. To make the **game canvas** use the available area, choose a resizable layout in Old School RuneScape's display settings. Fixed mode remains fixed; Stretched Mode remains a separate choice. This plugin does not change game settings or simulate game input.

## Compatibility and limitations

- The core GPU renderer and plugins declaring a conflict with GPU, such as 117 HD, are paused through RuneLite's plugin lifecycle during a window transition. Only previously active renderers still enabled by the user are restarted; an active replacement renderer is respected. Compatibility with individual renderer releases still requires live testing.
- Custom window chrome must stay disabled. Window-management plugins or external tools not covered by the declared conflicts may still interfere. Repeated rejected bounds cause recovery instead of an endless resize loop.
- Exclusive mode is unavailable on macOS and is rejected when the selected device does not support it. Borderless behavior depends on the desktop window manager.
- Recovery shortcuts are client shortcuts, not system-wide hotkeys. A hung RuneLite game thread can prevent the normal fullscreen transition from completing.
- If shutdown or explicit recovery times out while GPU rendering is still active, recovery avoids disposing the peer. The window is moved back into view, but restoring decorations may require restarting RuneLite. A failed ordinary exit leaves fullscreen active so it can be retried.
- If the plugin first starts while RuneLite is already maximized, the normal pre-maximize bounds are not publicly available. It preserves maximized state and uses the observed bounds as the restoration fallback. Restore and resize RuneLite once before entering fullscreen if exact normal bounds matter.
- Exiting RuneLite while fullscreen may cause core RuneLite to remember the fullscreen bounds for the next launch. Fullscreen Plus does not overwrite core persistent window settings.
- Changing monitor numbering, chrome, or window-management settings while fullscreen is best done after returning to windowed mode.
- **Windows, mixed-DPI multi-monitor behavior, taskbar coverage, GPU/117 HD, and in-game rendering remain unverified until tested on a real client.**

## Build and tests

Use a JDK 11 or newer. The project targets Java 11 bytecode and uses the official RuneLite example-plugin Gradle structure, including `build=standard` metadata for Hub packaging.

```sh
./gradlew clean build
./gradlew run
```

Windows PowerShell:

```powershell
.\gradlew.bat clean build
.\gradlew.bat run
```

`run` starts a development client with Fullscreen Plus loaded. For Jagex accounts, follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) instructions. Do not share launcher tokens or login credentials in issues or chat.

To build a standalone development launcher, run `./gradlew shadowJar`. The normal plugin JAR is not an installer and should not be copied into the production client's directories.

The JUnit suite covers delayed/missing/stale GPU replies, error cleanup, rapid toggles, shutdown, recovery, disconnected/negative-origin monitor geometry, declared conflicts, and packaged icons. It does not simulate a real desktop or graphics driver. See [TESTING.md](TESTING.md) for the manual acceptance checks.

## Development

Window operations and plugin start/stop run on the Swing EDT. GPU state is read on the RuneLite client thread. A generation token ignores stale callbacks; a bounded timeout prevents an indefinite transition. There is no reflection, JNI/JNA, native memory access, external process execution, networking, telemetry, or game-input automation in plugin code.

The PNGs are rendered from the small SVG sources in `assets/`. The Hub icon is 48×48; sidebar icons are 16×16, all with transparent backgrounds.

Based on concepts from dekvall's BSD-licensed Fullscreen plugin, with a new transition controller, recovery logic, monitor handling, configuration and icons. Original copyright notices are retained. See [LICENSE](LICENSE).
