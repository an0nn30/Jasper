# Browser Plugin, Plan 1: Chromium Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a throwaway Swing program that runs JetBrains' JCEF with off-screen rendering on each platform. Write each finding the browser design depends on into a report file, then write those findings back into the design.

**Architecture:** The spike is a standalone Java program under `tools/spikes/browser/` on its own branch. It is never merged and is outside the Gradle build.
- **Compiling and running:** scripts compile it with the JCEF runtime's own `javac` and run it.
- **Rendering:** it reuses four of JetBrains' JCEF test classes, which are the near-JDK-only port of IntelliJ's off-screen component.
- **Around them:** a run-options parser, a report that writes each finding as it arrives, a process and memory sampler, local test pages over HTTP and HTTPS, a Chromium session, and a window. The window holds Swing overlays and measurement buttons.
- **Who runs what:** agents compile it and run its headless `--self-check`. The user runs the window on each platform and returns the report files.

**Tech Stack:** Java 25 on JBR 25.0.4.1 b635.75 with JCEF (`jbrsdk_jcef`), JetBrains JCEF at commit `9df0879ddc3d775be97eb5bcc5cc834985a7e7e6` (CEF 150.0.14, Chromium 150.0.7871.129), Swing, `com.sun.net.httpserver`, `java.net.http`, bash and PowerShell scripts, Python 3 for the summary.

**Spec:** [`docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md`](../specs/2026-10-21-jasper-browser-plugin-design.md). Sections 12 and 13 define this spike.

**Execution status (2026-10-22): in progress, run subagent-driven.** These deviations from the plan's text were ruled
during execution:
- **Task 1, Step 3:** the return `cd` uses the absolute worktree path, because `../../..` was one level short.
  `JCEF_JDK` is set inline on every command, because agent shells do not keep exported variables. The jcef check
  passes at a count of 1 or more, because `java -version` names jcef on two lines. The archive was 379 MB, not about
  250 MB.
- **Scripts (from Task 3):** `spike.sh` and `spike.ps1` drop `--add-modules ALL-SYSTEM`. JBR's `jcef` module resolves
  by default, and the flag printed an incubator-module warning on every run.
- **Task 2, review fixes:**
  - `ProcessStats.noSandboxFlag` trusts only visible command lines, and says "unknown" when some helpers' command lines
    are hidden;
  - `rssKb` closes the child's stdin and waits before reading;
  - `deleteTree` also ignores `UncheckedIOException`;
  - the self-check gains `process_record_writes_keys`, `no_sandbox_flag_cases` and `platform_delete_tree`, and a
    stronger `report_appends_each_put`.

  It has 13 checks after Task 2, and 21 after Task 3.
- **Task 3, review fixes:**
  - the test pages clean up their servers, thread pool and TLS folder after a failed start;
  - a daemon thread pool serves both servers;
  - the keytool timeout fires;
  - a malformed tick gets a 400.
- **Task 4, review fixes:**
  - `popup.native_browser_created` became `popup.native_allowed`, a fixed note: JCEF gives an off-screen native popup
    no Java browser, so it runs invisible at 1x1;
  - the app handler is re-added before a remote restart;
  - a 90 s watchdog records failures JCEF swallows, under `cef.start_error` and `restart.result`;
  - `--eager` skips `JCefAppConfig` off macOS and waits 3 s;
  - restart is refused while Chromium runs;
  - `cef.child_no_sandbox_flag` counts "k of n launches";
  - remote runs record `download.remote_note`, because cef_server has no download support.

  Task 8 reads these keys.
- **Task 5, review fixes:**
  - the toolbar is six rows of at most five buttons, and the window is clamped to the usable screen;
  - `keys.swing_binding_fired` is written only while the page has the focus, and any other owner goes to
    `keys.swing_binding_fired_elsewhere`;
  - the checklist prefills saved answers and asks before it discards them;
  - `scale.device_factor` ignores removal events;
  - Measure hidden requires the Animation page;
  - Quit logs what it does, stops the worker and runs on its own thread.

## The plan series

| Plan | Deliverable | Waits on |
|---|---|---|
| **1 (this plan)** | The spike and its report; the design amended with what it found | Nothing |
| 2 | Runtime switch to `jbrsdk_jcef` for development, CI and packaging | This report, and the user's sandbox decision |
| 3 | Link routing: the SDK link-handler contribution, jasper-terminal's link-opener API, `BROWSER`, `jasper <url>` | Nothing: the tab rework (SDK 0.21.0) is on main since 2026-10-22 |
| 4 | The Browser plugin core | Plans 2 and 3 |
| 5 | Downloads, bookmarks, history, start page | Plan 4 |
| 6 | Saved logins (Vault API and format) | Plan 4 |
| 7 | The Chromium Markdown preview | Plan 4 |
| 8 | Documentation | Plans 4 to 7 |

Each later plan is written when what it waits on is done, because this spike's findings change their content. Plan 3
does not depend on Chromium, so it can be written and built alongside the spike.

**Refreshed against main on 2026-10-22.** The tab rework and its pane flexibility amendment landed on main, and the
design branch was rebased onto it. The spike itself is standalone, so its tasks are unchanged.

**Deviation from the spec:** spec section 12 lists macOS signing of the CEF framework and helpers inside `Jasper.app` as
a spike risk. A spike run from the unpacked JBR cannot test it, because that needs a packaged app. It moves to plan 2,
which changes packaging. Task 8 records this in the spec.

## Already settled by reading the JCEF source (2026-10-21)

An API digest of JetBrains JCEF at the pinned commit settled these. The spike confirms them where running code is needed, and Task 8 copies them into the report.

1. **No in-process restart.** In-process CEF cannot start again after `CefApp.dispose()`: `getInstance` throws "CefApp was terminated", and the native `Shutdown` is guarded to run once. Out-of-process mode (`-Djcef.remote.enabled=true`, `cef_server`) can start a new server. Spec section 9's "memory goes back when unused" therefore needs out-of-process mode.
2. **No accessibility.** Off-screen browsers expose nothing to Java accessibility; nothing in JCEF binds CEF's accessibility tree. Spec section 8's fallback (Markdown preview: Chromium or Basic) is needed.
3. **No favicon or hidden-state callbacks.** There is no favicon callback, which matches spec section 5's globe icon. There is no `WasHidden` binding either, so hiding a page must be done another way (the spike measures `setWindowlessFrameRate`).
4. **Popups.** `onBeforePopup(CefBrowser, CefFrame, String target_url, String target_frame_name)` is the only overload. There is no disposition and no user gesture.
5. **Certificates.** `CefRequestHandler.onCertificateError(..., CefSSLInfo, CefCallback)` with `CefCallback.Continue()` exists, so Chrome's "continue for this session" behaviour is buildable.
6. **Sandbox.** `CefSettings.no_sandbox` defaults to `true`. Setting it to `false`, or passing `-Djcef.use_sandbox=true`, asks for the sandbox.
7. **Downloads.** `CefDownloadHandler.onBeforeDownload` and `CefBeforeDownloadCallback.Continue(path, showDialog)` exist.
8. **DevTools.** Off-screen DevTools need a `CefBrowserOsrWithHandler` subclass that overrides `createDevToolsBrowser`, as IntelliJ does in-process. The plain class returns `null`, and `openDevTools()` then fails with a NullPointerException.
9. **Closing.** `CefBrowser_N.doClose()` returns `true`, which cancels the close, unless `setCloseAllowed()` was called. Once it was, it posts `WINDOW_CLOSING` to the root window, which would close a Jasper window. Jasper's browser class must override `doClose()`.
10. **Initialisation.** `CefApp` pre-initialises through `SwingUtilities.invokeAndWait`, so waiting for initialisation on the EDT deadlocks. Pass non-null `CefSettings`. If `addAppHandler` is used, Chromium's switches come from that handler's constructor arguments.

## Global Constraints

- **The spike is throwaway.**
  - Its code lives under `tools/spikes/browser/` on branch `claude/browser-spike` and is never merged.
  - Only the report, the design amendments and the `docs/STATUS.md` entry go to `claude/browser-plugin-design`.
- **The runtime** is `jbrsdk_jcef` 25.0.4.1 b635.75, from `https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk_jcef-25.0.4.1-<platform>-b635.75.tar.gz`. The platforms are `osx-aarch64`, `osx-x64`, `windows-x64`, `windows-aarch64`, `linux-x64` and `linux-aarch64`. The SHA-512 checksum is at the same URL with `.checksum` appended.
- **Where the JCEF runtime goes.** Never install it where Gradle looks for toolchains:
  - `~/Library/Java/JavaVirtualMachines` and `/Library/Java/JavaVirtualMachines`;
  - `~/.jdks`, `/usr/lib/jvm` and `%USERPROFILE%\.jdks`.

  Jasper's builds would then pick it as their JetBrains 25 toolchain, which matters because other sessions build on this Mac. On the dev Mac, put it in `tools/spikes/browser/.jdk/`.
- **Agents never open the spike window.** They compile and run `--self-check`, which is headless, and nothing else (AGENTS.md: "Do not launch the GUI"). The user runs the window.
- **Off-screen rendering only** (spec section 3). The spike never uses `CefRendering.DEFAULT`.
- **Never wait for Chromium on the EDT** (fact 10).
- **Clean up temporary folders.** Every temporary folder the spike or the self-check creates in the system temp directory is deleted when it is done. Chromium's cache, log and downloads go under `tools/spikes/browser/build/`.
- **Source hygiene** (AGENTS.md): no raw control, private-use or unpaired surrogate characters in sources.
- **Commit messages** end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **Starting Chromium after the window is up on Linux.** `CefApp.startup` calls `XInitThreads`, which X11 wants before any other X call.
   - Lazy start must either work or crash visibly.
   - The `--eager` run then tells whether starting before AWT fixes it.
   - Pinned by `options_eager_popuptab` (Task 2) and the README's Linux rows (Task 6).
2. **A browser close closing the whole window** (fact 9). The spike window must stay open after Close browser and Dispose Chromium, and record any stray close request. This is pinned by `SpikeOsrBrowser.doClose()` (Task 4), the window's `DO_NOTHING_ON_CLOSE` with `frame.window_closing_event` (Task 5), and checklist item `close_dispose`.
3. **A runtime without JCEF.** Running on plain JBR must print a clear message and exit 3, not throw `NoClassDefFoundError`. The baseline and the self-check must still run there.
   - Pinned by `Platform.jcefPresent()` in `Spike.main` (Task 2).
   - Pinned by Task 2's self-check run on the plain JBR.
4. **Losing or mixing results.** Every run writes its own file, and each finding reaches the disk the moment it is recorded, so a crash keeps it. Pinned by `report_appends_each_put` and `report_files_distinct` (Task 2).
5. **Temporary folders left behind.** The self-check's folder and the TLS key folder are deleted afterwards, because leaked temp folders already slow this Mac's builds. Pinned by `pages_close_removes_temp` (Task 3) and `SelfCheck.run`'s `finally` (Task 2).

---

## File structure

All of these are under `tools/spikes/browser/` on branch `claude/browser-spike`:

| File | Responsibility |
|---|---|
| `.gitignore` | Ignores `build/` and `.jdk/` |
| `spike.sh`, `spike.ps1` | Compile with `$JCEF_JDK`, then run with `$SPIKE_RUN_JDK` (default `$JCEF_JDK`) |
| `README.md` | Setup per platform, the run matrix, what to click, how to send reports back |
| `summarize.py` | Prints a Markdown table of the reports (Task 8) |
| `reports/.gitkeep` | Where reports land |
| `src/spike/osr/JBCefOsrComponent.java`, `JBCefOsrHandler.java`, `CefInputMethodAdapter.java`, `JBCefFpsMeter.java` | JetBrains' off-screen component, copied from JCEF `java_tests/tests` at the pinned commit |
| `src/spike/Options.java` | Run options and the report label |
| `src/spike/Platform.java` | OS, architecture, toolkit, JCEF presence, temporary-folder deletion |
| `src/spike/Report.java` | `key = value` findings, written as they arrive |
| `src/spike/ProcessStats.java` | Helper process count, memory and `--no-sandbox` flags of the process tree |
| `src/spike/BaselineWindow.java` | Plain window for start-up timing without Chromium |
| `src/spike/SelfCheck.java` | Headless checks of the plumbing |
| `src/spike/PageHtml.java` | The test pages' HTML |
| `src/spike/TestPages.java` | Local HTTP and HTTPS server, plus the `/result` and `/tick` collectors |
| `src/spike/CountingOsrHandler.java` | Counts paints and signals the first one |
| `src/spike/SpikeOsrBrowser.java` | In-process browser with the `doClose` and DevTools overrides |
| `src/spike/BrowserView.java` | One browser and its component, behind plain methods |
| `src/spike/CefSession.java` | Start, handlers, browser creation, dispose and restart |
| `src/spike/ChecklistDialog.java` | The manual checks, saved into the report |
| `src/spike/SpikeWindow.java` | The window: tabs in a layered pane, overlays, probes, measurement buttons |
| `src/spike/Spike.java` | `main` |

On `claude/browser-plugin-design` (Task 8):
- `docs/superpowers/plans/2026-10-21-browser-1-spike-report.md`;
- amendments to the spec;
- an entry in `docs/STATUS.md`.

---

### Task 1: Spike workspace, JCEF runtime and JetBrains' off-screen classes

**Files:**
- Create: `tools/spikes/browser/.gitignore`, `tools/spikes/browser/reports/.gitkeep`, `tools/spikes/browser/spike.sh`, `tools/spikes/browser/spike.ps1`
- Create (copied): `tools/spikes/browser/src/spike/osr/JBCefOsrComponent.java`, `JBCefOsrHandler.java`, `CefInputMethodAdapter.java`, `JBCefFpsMeter.java`

**Interfaces:**
- Produces:
  - `spike.osr.JBCefOsrComponent`, a `JPanel` with `setBrowser(CefBrowser)` and `addNotify()` that creates the browser;
  - `spike.osr.JBCefOsrHandler(JBCefOsrComponent, ScreenBoundsProvider)` (pass `null`), which implements `CefNativeRenderHandler` with public `onPaint(...)` and `onPaintWithSharedMem(...)`;
  - `spike.sh` and `spike.ps1`, which compile `src/` and run `spike.Spike`.

- [ ] **Step 1: Create the spike worktree from the design branch**

```bash
git -C /Users/RQ7RQVF/projects/moray worktree add .claude/worktrees/browser-spike -b claude/browser-spike claude/browser-plugin-design
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-spike
mkdir -p tools/spikes/browser/src/spike/osr tools/spikes/browser/reports
```

Expected: `Preparing worktree (new branch 'claude/browser-spike')`. Run every later command from this worktree.

- [ ] **Step 2: Ask the user before downloading the JCEF runtime**

Ask: "May I download JetBrains' `jbrsdk_jcef-25.0.4.1-osx-aarch64-b635.75.tar.gz` (about 250 MB) from cache-redirector.jetbrains.com into `tools/spikes/browser/.jdk/`? It's ignored by git, and Gradle never sees it there." Wait for a yes.

- [ ] **Step 3: Download, verify and unpack it**

```bash
mkdir -p tools/spikes/browser/.jdk && cd tools/spikes/browser/.jdk
U=https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk_jcef-25.0.4.1-osx-aarch64-b635.75.tar.gz
curl -fSL -o jdk.tar.gz "$U"
test "$(curl -fsSL "$U.checksum" | cut -d' ' -f1)" = "$(shasum -a 512 jdk.tar.gz | cut -d' ' -f1)" && echo CHECKSUM OK
tar -xzf jdk.tar.gz && rm jdk.tar.gz
export JCEF_JDK="$(ls -d "$PWD"/jbrsdk_jcef-*/Contents/Home)"
"$JCEF_JDK/bin/java" -version 2>&1 | grep -c jcef
cd ../../../..
```

Expected:
- `CHECKSUM OK`;
- then `1`, because the version line contains `JBR-25.0.4.1+1-635.75-jcef`.

Keep `JCEF_JDK` exported for the later steps.

- [ ] **Step 4: Write `tools/spikes/browser/.gitignore` and `reports/.gitkeep`**

```gitignore
build/
.jdk/
```

```bash
touch tools/spikes/browser/reports/.gitkeep
```

- [ ] **Step 5: Copy the four JetBrains classes at the pinned commit and adapt them**

```bash
C=9df0879ddc3d775be97eb5bcc5cc834985a7e7e6
for f in JBCefOsrComponent JBCefOsrHandler CefInputMethodAdapter JBCefFpsMeter; do
  curl -fsSL "https://raw.githubusercontent.com/JetBrains/jcef/$C/java_tests/tests/$f.java" -o "tools/spikes/browser/src/spike/osr/$f.java"
done
python3 -I - tools/spikes/browser/src/spike/osr <<'PY'
import pathlib, sys
osr = pathlib.Path(sys.argv[1])
def edit(name, old, new):
    p = osr / name
    text = p.read_text(encoding="utf-8")
    assert text.count(old) == 1, (name, old[:60], text.count(old))
    p.write_text(text.replace(old, new), encoding="utf-8")
for name in ["JBCefOsrComponent.java", "JBCefOsrHandler.java", "CefInputMethodAdapter.java", "JBCefFpsMeter.java"]:
    edit(name, "package tests;", "package spike.osr;")
h = "JBCefOsrHandler.java"
edit(h, "import com.jetbrains.JBR;\n", "")
edit(h, "                if (!JBR.isNativeRasterLoaderSupported())\n                    bufImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);\n",
        "                bufImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);\n")
edit(h, "        if (JBR.isNativeRasterLoaderSupported()) {\n            JBR.getNativeRasterLoader().loadNativeRaster(volatileImage, mem.getPtr(), mem.getWidth(), mem.getHeight(), mem.getPtr() + mem.getWidth()*mem.getHeight()*4, mem.getDirtyRectsCount());\n        } else {\n",
        "        {\n")
edit(h, '        System.err.println("onPaintWithSharedMem spent " + (endMs - startMs) + " ms, load spent " + (endMs - midMs));\n', "")
print("EDITS OK")
PY
grep -c "JBR\." tools/spikes/browser/src/spike/osr/JBCefOsrHandler.java
```

Expected:
- `EDITS OK`;
- then `0`. Without the JBR API jar, the shared-memory path loads frames through `loadBuffered`. That is slower than IntelliJ's native raster loader, but enough to answer the spike's questions.

- [ ] **Step 6: Write `tools/spikes/browser/spike.sh`**

```bash
#!/usr/bin/env bash
# Compiles the spike with $JCEF_JDK and runs it with $SPIKE_RUN_JDK (default $JCEF_JDK).
# Spike options follow, for example: ./spike.sh --mode=remote
set -euo pipefail
cd "$(dirname "$0")"
: "${JCEF_JDK:?Set JCEF_JDK to the jbrsdk_jcef home (on macOS the folder ending in Contents/Home)}"
RUN_JDK="${SPIKE_RUN_JDK:-$JCEF_JDK}"
rm -rf build/classes
"$JCEF_JDK/bin/java" -m jdk.compiler/com.sun.tools.javac.Main --add-modules ALL-SYSTEM -nowarn -encoding UTF-8 -d build/classes $(find src -name '*.java')
exec "$RUN_JDK/bin/java" --add-modules ALL-SYSTEM ${SPIKE_JAVA_OPTS:-} -cp build/classes spike.Spike "$@"
```

```bash
chmod +x tools/spikes/browser/spike.sh
```

- [ ] **Step 7: Write `tools/spikes/browser/spike.ps1`**

```powershell
# Compiles the spike with $env:JCEF_JDK and runs it with $env:SPIKE_RUN_JDK (default $env:JCEF_JDK).
# Spike options follow, for example: .\spike.ps1 --mode=remote
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JCEF_JDK) { throw 'Set JCEF_JDK to the jbrsdk_jcef folder (the one holding bin\java.exe)' }
$runJdk = if ($env:SPIKE_RUN_JDK) { $env:SPIKE_RUN_JDK } else { $env:JCEF_JDK }
if (Test-Path build\classes) { Remove-Item -Recurse -Force build\classes }
$sources = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }
& "$env:JCEF_JDK\bin\java.exe" -m jdk.compiler/com.sun.tools.javac.Main --add-modules ALL-SYSTEM -nowarn -encoding UTF-8 -d build\classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$opts = if ($env:SPIKE_JAVA_OPTS) { $env:SPIKE_JAVA_OPTS -split ' ' } else { @() }
& "$runJdk\bin\java.exe" --add-modules ALL-SYSTEM @opts -cp build\classes spike.Spike @args
exit $LASTEXITCODE
```

- [ ] **Step 8: Compile the copied classes**

```bash
cd tools/spikes/browser && "$JCEF_JDK/bin/java" -m jdk.compiler/com.sun.tools.javac.Main --add-modules ALL-SYSTEM -nowarn -encoding UTF-8 -d build/classes $(find src -name '*.java') && echo COMPILED; cd ../../..
```

Expected: `COMPILED`. The scripts pass `--add-modules ALL-SYSTEM` both to javac and to java, so the `jcef` module resolves even if JBR marks it as not resolved by default. The flag is harmless on a plain JBR.

- [ ] **Step 9: Check source hygiene**

```bash
python3 - tools/spikes/browser/src <<'PY'
import pathlib, sys
for p in pathlib.Path(sys.argv[1]).rglob("*.java"):
    text = p.read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, "bad chars:", bad)
print("HYGIENE DONE")
PY
```

Expected: `HYGIENE DONE` with no "bad chars" line.

- [ ] **Step 10: Commit**

```bash
git add tools/spikes/browser
git commit -m "Browser spike: workspace, scripts and JetBrains' off-screen JCEF classes

Copies JBCefOsrComponent, JBCefOsrHandler, CefInputMethodAdapter and
JBCefFpsMeter from JetBrains/jcef java_tests at 9df0879, with the JBR API
calls removed. Throwaway branch, never merged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Options, platform facts, the report, process stats, the baseline window and the self-check

**Files:**
- Create: `tools/spikes/browser/src/spike/SelfCheck.java`, `Options.java`, `Platform.java`, `Report.java`, `ProcessStats.java`, `BaselineWindow.java`, `Spike.java`

**Interfaces:**
- Consumes: `spike.sh` (Task 1).
- Produces:
  - `record Options(Mode mode, boolean sandbox, boolean eager, PopupPolicy popups, boolean selfCheck, boolean baseline)`, with `static Options parse(List<String>)`, `String label()` and the enums `Options.Mode { INPROCESS, REMOTE }` and `Options.PopupPolicy { ALLOW, TAB }`;
  - `Platform`: `os()`, `arch()`, `id()`, `isMac()`, `isWindows()`, `jcefPresent()` and `deleteTree(Path)`, all static;
  - `Report(Path dir, String label)`, with `file()`, `put(String, Object)`, `note(String)` and `get(String) -> Optional<String>`;
  - `ProcessStats`: `take() -> Snapshot(int helpers, long rssKb, List<String> commands)`, `rssKb(List<Long>)`, `noSandboxFlag(Snapshot)` and `record(Report, String phase)`. `record` writes `proc.<phase>.helpers`, `proc.<phase>.rss_kb` and `proc.<phase>.no_sandbox_flag`;
  - `BaselineWindow.show(Report)`;
  - `SelfCheck.run() -> int`, the exit code. Task 3 adds a `pages()` group to it.

- [ ] **Step 1: Write the self-check first: `src/spike/SelfCheck.java`**

```java
package spike;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Headless checks of the spike's own plumbing; run with --self-check. */
final class SelfCheck {
    private final Path temp;
    private int failures;

    private SelfCheck(Path temp) {
        this.temp = temp;
    }

    static int run() throws Exception {
        Path temp = Files.createTempDirectory("jasper-spike-selfcheck");
        SelfCheck checks = new SelfCheck(temp);
        try {
            System.out.println("platform = " + Platform.id() + ", jcef module present = " + Platform.jcefPresent());
            checks.options();
            checks.report();
            checks.platform();
            checks.processes();
        } finally {
            Platform.deleteTree(temp);
        }
        System.out.println(checks.failures == 0 ? "SELF-CHECK PASSED" : "SELF-CHECK FAILED: " + checks.failures);
        return checks.failures == 0 ? 0 : 1;
    }

    private void options() {
        check("options_default", () -> {
            Options options = Options.parse(List.of());
            expect(options.mode() == Options.Mode.INPROCESS && !options.sandbox() && options.popups() == Options.PopupPolicy.ALLOW, "defaults");
            expect(options.label().equals("inprocess"), "label " + options.label());
        });
        check("options_remote_sandbox", () ->
            expect(Options.parse(List.of("--mode=remote", "--sandbox")).label().equals("remote-sandbox"), "label"));
        check("options_eager_popuptab", () ->
            expect(Options.parse(List.of("--popups=tab", "--eager")).label().equals("inprocess-eager-popuptab"), "label"));
        check("options_rejects_unknown", () ->
            expectThrows(() -> Options.parse(List.of("--bogus")), "Unknown option: --bogus"));
        check("options_baseline_alone", () ->
            expectThrows(() -> Options.parse(List.of("--baseline", "--sandbox")), "--baseline takes no other option"));
    }

    private void report() {
        check("report_appends_each_put", () -> {
            Report report = new Report(temp, "t");
            report.put("a", 1);
            expect(Files.readString(report.file()).contains("a = 1"), "line written at once");
            expect(report.get("a").orElse("").equals("1"), "value kept");
        });
        check("report_files_distinct", () -> {
            Report first = new Report(temp, "same");
            Report second = new Report(temp, "same");
            expect(!first.file().equals(second.file()) && Files.exists(first.file()) && Files.exists(second.file()), "two files");
        });
        check("report_single_line_values", () -> {
            Report report = new Report(temp, "t");
            report.put("b", "x\ny");
            expect(Files.readString(report.file()).contains("b = x y"), "newline replaced");
        });
    }

    private void platform() {
        check("platform_id_shape", () ->
            expect(Platform.id().matches("(macos|windows|linux)-[a-z0-9_]+(-wayland)?"), Platform.id()));
    }

    private void processes() {
        check("process_rss_of_self", () ->
            expect(ProcessStats.rssKb(List.of(ProcessHandle.current().pid())) > 0, "rss > 0"));
        check("process_snapshot", () -> expect(ProcessStats.take().helpers() >= 0, "helpers >= 0"));
    }

    private void check(String name, Check body) {
        try {
            body.run();
            System.out.println("PASS " + name);
        } catch (Skip skip) {
            System.out.println("SKIP " + name + ": " + skip.getMessage());
        } catch (Throwable failure) {
            failures++;
            System.out.println("FAIL " + name + ": " + failure);
        }
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static void expectThrows(Runnable body, String messageStart) {
        try {
            body.run();
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().startsWith(messageStart)) throw new AssertionError("message was: " + e.getMessage());
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    @FunctionalInterface
    private interface Check {
        void run() throws Exception;
    }

    private static final class Skip extends RuntimeException {
        Skip(String why) {
            super(why);
        }
    }
}
```

- [ ] **Step 2: Write `src/spike/Spike.java` so the self-check can run**

```java
package spike;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** The browser spike's entry point; see README.md. */
public final class Spike {
    private Spike() {
    }

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = Options.parse(List.of(args));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        if (options.selfCheck()) {
            System.exit(SelfCheck.run());
            return;
        }
        Report report = new Report(Path.of(System.getProperty("spike.reports", "reports")), options.label());
        report.put("run.start", Instant.now());
        report.put("run.options", String.join(" ", args));
        report.put("platform", Platform.id());
        report.put("runtime.java", System.getProperty("java.runtime.version"));
        report.put("runtime.vendor", System.getProperty("java.vendor.version", "?"));
        report.put("runtime.home", System.getProperty("java.home"));
        report.put("runtime.jcef_module_present", Platform.jcefPresent());
        report.put("runtime.awt_toolkit_name", System.getProperty("awt.toolkit.name", "default"));
        System.out.println("Report: " + report.file().toAbsolutePath());
        if (options.baseline()) {
            SwingUtilities.invokeLater(() -> BaselineWindow.show(report));
            return;
        }
        if (!Platform.jcefPresent()) {
            System.err.println("This runtime has no jcef module. Point JCEF_JDK at a jbrsdk_jcef build (see README.md).");
            report.put("run.error", "no jcef module");
            System.exit(3);
            return;
        }
        System.err.println("Chromium runs arrive in a later task of the spike plan.");
        System.exit(4);
    }
}
```

- [ ] **Step 3: Compile to see it fail**

```bash
tools/spikes/browser/spike.sh --self-check
```

Expected: compilation errors ("cannot find symbol") for `Options`, `Platform`, `Report`, `ProcessStats` and `BaselineWindow`.

- [ ] **Step 4: Write `src/spike/Options.java`**

```java
package spike;

import java.util.List;

/** The options of one spike run, parsed from the command line. */
record Options(Mode mode, boolean sandbox, boolean eager, PopupPolicy popups, boolean selfCheck, boolean baseline) {
    enum Mode { INPROCESS, REMOTE }

    /** What the spike does when a page asks for a new window. */
    enum PopupPolicy { ALLOW, TAB }

    static final String USAGE = """
        Usage: spike.sh [options]
          --mode=inprocess   Chromium inside the JVM (default)
          --mode=remote      Chromium in JBR's out-of-process cef_server
          --sandbox          Ask Chromium to run its sandbox
          --eager            Call CefApp.startup before AWT starts (Linux fallback)
          --popups=allow     Let Chromium create popups itself (default)
          --popups=tab       Cancel popups and open their URL in a new spike tab
          --baseline         Show a plain window without Chromium (start-up timing)
          --self-check       Run the headless checks and exit""";

    static Options parse(List<String> args) {
        Mode mode = Mode.INPROCESS;
        PopupPolicy popups = PopupPolicy.ALLOW;
        boolean sandbox = false;
        boolean eager = false;
        boolean selfCheck = false;
        boolean baseline = false;
        for (String arg : args) {
            switch (arg) {
                case "--mode=inprocess" -> mode = Mode.INPROCESS;
                case "--mode=remote" -> mode = Mode.REMOTE;
                case "--sandbox" -> sandbox = true;
                case "--eager" -> eager = true;
                case "--popups=allow" -> popups = PopupPolicy.ALLOW;
                case "--popups=tab" -> popups = PopupPolicy.TAB;
                case "--self-check" -> selfCheck = true;
                case "--baseline" -> baseline = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg + "\n" + USAGE);
            }
        }
        if (baseline && args.size() > 1) throw new IllegalArgumentException("--baseline takes no other option\n" + USAGE);
        return new Options(mode, sandbox, eager, popups, selfCheck, baseline);
    }

    /** A short name for report files, for example {@code remote-sandbox}. */
    String label() {
        if (baseline) return "baseline";
        if (selfCheck) return "selfcheck";
        StringBuilder label = new StringBuilder(mode == Mode.REMOTE ? "remote" : "inprocess");
        if (sandbox) label.append("-sandbox");
        if (eager) label.append("-eager");
        if (popups == PopupPolicy.TAB) label.append("-popuptab");
        return label.toString();
    }
}
```

- [ ] **Step 5: Write `src/spike/Platform.java`**

```java
package spike;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Facts about the machine and runtime, read without starting AWT or Chromium. */
final class Platform {
    private Platform() {
    }

    static String os() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("mac")) return "macos";
        if (name.contains("win")) return "windows";
        return "linux";
    }

    static String arch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        };
    }

    /** For example {@code linux-x64-wayland}: the OS, the architecture, and JBR's Wayland toolkit when chosen. */
    static String id() {
        return os() + "-" + arch() + ("WLToolkit".equals(System.getProperty("awt.toolkit.name")) ? "-wayland" : "");
    }

    static boolean isMac() {
        return os().equals("macos");
    }

    static boolean isWindows() {
        return os().equals("windows");
    }

    /** Whether this runtime carries JBR's jcef module. */
    static boolean jcefPresent() {
        return ModuleLayer.boot().findModule("jcef").isPresent();
    }

    /** Deletes a folder and everything in it, ignoring failures; for the spike's temporary folders. */
    static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
```

- [ ] **Step 6: Write `src/spike/Report.java`**

```java
package spike;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** One run's findings as {@code key = value} lines, written as each arrives so a crash keeps them. */
final class Report {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private final Path file;
    private final Map<String, String> values = new LinkedHashMap<>();

    Report(Path dir, String label) throws IOException {
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        file = dir.resolve(Platform.id() + "-" + label + "-" + stamp + "-" + ProcessHandle.current().pid()
            + "-" + SEQUENCE.incrementAndGet() + ".txt");
        Files.writeString(file, "# Jasper browser spike report" + System.lineSeparator(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    Path file() {
        return file;
    }

    synchronized void put(String key, Object value) {
        String text = String.valueOf(value);
        values.put(key, text);
        append(key + " = " + text);
    }

    synchronized void note(String text) {
        append("# " + text);
    }

    synchronized Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    private void append(String line) {
        try {
            Files.writeString(file, line.replaceAll("\\R", " ") + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Could not write the report: " + e);
        }
    }
}
```

- [ ] **Step 7: Write `src/spike/ProcessStats.java`**

```java
package spike;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** The spike's process tree: how many Chromium helpers run, how much memory the tree holds, and their sandbox flags. */
final class ProcessStats {
    record Snapshot(int helpers, long rssKb, List<String> commands) {
    }

    private ProcessStats() {
    }

    static Snapshot take() {
        List<ProcessHandle> tree = new ArrayList<>();
        tree.add(ProcessHandle.current());
        ProcessHandle.current().descendants().forEach(tree::add);
        List<String> commands = tree.stream().skip(1)
            .map(p -> p.info().commandLine().orElse(p.info().command().orElse("?")))
            .toList();
        return new Snapshot(tree.size() - 1, rssKb(tree.stream().map(ProcessHandle::pid).toList()), commands);
    }

    /** Resident memory of these processes in KiB, or -1 when the system tool fails. */
    static long rssKb(List<Long> pids) {
        if (pids.isEmpty()) return 0;
        String joined = pids.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<String> command = Platform.isWindows()
            ? List.of("powershell", "-NoProfile", "-Command",
                "(Get-Process -Id " + joined + " -ErrorAction SilentlyContinue | Measure-Object -Property WorkingSet64 -Sum).Sum")
            : List.of("ps", "-o", "rss=", "-p", joined);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            long total = 0;
            for (String line : out.split("\\R")) {
                String value = line.trim();
                if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) total += Long.parseLong(value);
            }
            return Platform.isWindows() ? total / 1024 : total;
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** "present", "absent", "no helpers", or "unknown" when the system hides helpers' arguments. */
    static String noSandboxFlag(Snapshot snapshot) {
        if (snapshot.commands().isEmpty()) return "no helpers";
        if (snapshot.commands().stream().noneMatch(c -> c.contains(" "))) return "unknown (no command lines visible)";
        return snapshot.commands().stream().anyMatch(c -> c.contains("--no-sandbox")) ? "present" : "absent";
    }

    static void record(Report report, String phase) {
        Snapshot snapshot = take();
        report.put("proc." + phase + ".helpers", snapshot.helpers());
        report.put("proc." + phase + ".rss_kb", snapshot.rssKb());
        report.put("proc." + phase + ".no_sandbox_flag", noSandboxFlag(snapshot));
    }
}
```

- [ ] **Step 8: Write `src/spike/BaselineWindow.java`**

```java
package spike;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.management.ManagementFactory;
import java.time.Instant;

/** A plain window, so start-up can be timed on a runtime without touching Chromium. */
final class BaselineWindow {
    private BaselineWindow() {
    }

    static void show(Report report) {
        JFrame frame = new JFrame("Jasper browser spike: baseline (no Chromium)");
        frame.add(new JLabel("Baseline run: Chromium is not loaded. Close this window after a few seconds.",
            SwingConstants.CENTER));
        frame.setSize(640, 240);
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                report.put("startup.jvm_to_window_ms",
                    System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime());
                new Thread(() -> ProcessStats.record(report, "window_shown"), "spike-baseline-stats").start();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                report.put("run.end", Instant.now());
                System.exit(0);
            }
        });
        frame.setVisible(true);
    }
}
```

- [ ] **Step 9: Run the self-check on the JCEF runtime**

```bash
tools/spikes/browser/spike.sh --self-check
```

Expected: `platform = macos-aarch64, jcef module present = true`, eleven `PASS` lines, then `SELF-CHECK PASSED`.

- [ ] **Step 10: Run the self-check on the plain JBR, which has no JCEF**

```bash
SPIKE_RUN_JDK="$HOME/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48/Contents/Home" tools/spikes/browser/spike.sh --self-check
ls "${TMPDIR:-/tmp}" | grep -c jasper-spike-selfcheck
```

Expected:
- `jcef module present = false`;
- eleven `PASS` lines and `SELF-CHECK PASSED`;
- then `0`, because the self-check deleted its temp folder.

- [ ] **Step 11: Commit**

```bash
git add tools/spikes/browser/src/spike
git commit -m "Browser spike: options, report, process stats, baseline window and self-check

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Local test pages over HTTP and HTTPS

**Files:**
- Create: `tools/spikes/browser/src/spike/PageHtml.java`, `tools/spikes/browser/src/spike/TestPages.java`
- Modify: `tools/spikes/browser/src/spike/SelfCheck.java` (adds the `pages()` group)

**Interfaces:**
- Consumes: `Report`, `Platform` (Task 2).
- Produces:
  - `TestPages implements AutoCloseable`, with these members:
    - `static TestPages start(Report, Path keytool) throws IOException`;
    - `static Path defaultKeytool()`;
    - `int httpPort()`, and `int httpsPort()`, which returns -1 when HTTPS is unavailable;
    - `String url(String path)`, `String secureUrl(String path)`, `long rafCount()`, `long timerCount()`, `Path keyDir()`, `close()`.
  - **Pages:** `/`, `/popup`, `/popup-child`, `/input`, `/find`, `/anim`, `/download`, `/file.bin` and `/cert`.
  - **`/result?k=v`** records `page.<k> = v` in the report.
  - **`/tick?raf=N&timer=M`** updates `rafCount()` and `timerCount()`.
  - **HTTPS keys:** `https.available` and `https.unavailable_reason`.

- [ ] **Step 1: Add the failing checks to `SelfCheck.java`**

Add these imports:

```java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLHandshakeException;
```

Add `checks.pages();` after `checks.processes();` in `run()`, and add this method:

```java
    private void pages() throws Exception {
        Report report = new Report(temp, "pages");
        try (TestPages pages = TestPages.start(report, TestPages.defaultKeytool())) {
            HttpClient client = HttpClient.newHttpClient();
            check("pages_serve_index", () -> {
                HttpResponse<String> index = client.send(HttpRequest.newBuilder(URI.create(pages.url("/"))).build(),
                    HttpResponse.BodyHandlers.ofString());
                expect(index.statusCode() == 200 && index.body().contains("Jasper browser spike"), "index");
            });
            check("pages_record_results", () -> {
                client.send(HttpRequest.newBuilder(URI.create(pages.url("/result?popup.opener.window.open=present"))).build(),
                    HttpResponse.BodyHandlers.discarding());
                expect(report.get("page.popup.opener.window.open").orElse("").equals("present"), "recorded");
            });
            check("pages_tick_counts", () -> {
                client.send(HttpRequest.newBuilder(URI.create(pages.url("/tick?raf=42&timer=7"))).build(),
                    HttpResponse.BodyHandlers.discarding());
                expect(pages.rafCount() == 42 && pages.timerCount() == 7, "ticks");
            });
            check("pages_download_is_attachment", () -> {
                HttpResponse<byte[]> file = client.send(HttpRequest.newBuilder(URI.create(pages.url("/file.bin"))).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                expect(file.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"), "attachment");
                expect(file.body().length == 1 << 20, "1 MiB");
            });
            check("pages_unknown_path_404", () -> {
                HttpResponse<Void> missing = client.send(HttpRequest.newBuilder(URI.create(pages.url("/nope"))).build(),
                    HttpResponse.BodyHandlers.discarding());
                expect(missing.statusCode() == 404, "404");
            });
            check("pages_https_is_untrusted", () -> {
                if (pages.httpsPort() < 0) throw new Skip("no HTTPS: " + report.get("https.unavailable_reason").orElse("?"));
                try {
                    client.send(HttpRequest.newBuilder(URI.create(pages.secureUrl("/cert"))).build(),
                        HttpResponse.BodyHandlers.ofString());
                    throw new AssertionError("a self-signed certificate was trusted");
                } catch (SSLHandshakeException expected) {
                    // self-signed, as the browser's certificate test needs
                } catch (IOException e) {
                    expect(e.getCause() instanceof SSLHandshakeException, "handshake failure, was " + e);
                }
            });
        }
        check("pages_without_keytool", () -> {
            Report missing = new Report(temp, "nokeytool");
            try (TestPages pages = TestPages.start(missing, temp.resolve("no-such-keytool"))) {
                expect(pages.httpsPort() == -1, "https off");
                expect(missing.get("https.available").orElse("").equals("false"), "reason recorded");
            }
        });
        check("pages_close_removes_temp", () -> {
            Report closing = new Report(temp, "closing");
            Path keyDir;
            try (TestPages pages = TestPages.start(closing, TestPages.defaultKeytool())) {
                keyDir = pages.keyDir();
            }
            expect(!Files.exists(keyDir), "TLS folder deleted");
        });
    }
```

- [ ] **Step 2: Compile to see it fail**

```bash
tools/spikes/browser/spike.sh --self-check
```

Expected: "cannot find symbol" for `TestPages`.

- [ ] **Step 3: Write `src/spike/PageHtml.java`**

```java
package spike;

/** HTML of the spike's test pages. Pages report what they see to /result on their own server. */
final class PageHtml {
    private PageHtml() {
    }

    static final String INDEX = """
        <!doctype html><meta charset=utf-8><title>Jasper browser spike</title>
        <h1>Jasper browser spike</h1>
        <ul>
        <li><a href=/popup>Popup test</a>
        <li><a href=/input>Input test</a>
        <li><a href=/find>Find page</a>
        <li><a href=/anim>Animation (for Measure hidden)</a>
        <li><a href=/download>Download test</a>
        </ul>
        <p>The HTTPS test is on its own port: use the Cert (HTTPS) button.</p>
        """;

    static final String POPUP = """
        <!doctype html><meta charset=utf-8><title>Popup test</title>
        <h1>Popup test</h1>
        <p><button id=open>window.open popup</button></p>
        <p><a href="/popup-child?via=blank" target="_blank">target=_blank link</a></p>
        <p><a href="/popup-child?via=noopener" target="_blank" rel="noopener">noopener link</a></p>
        <p id=out>No message from a child yet.</p>
        <script>
        document.getElementById('open').onclick = () => window.open('/popup-child?via=window.open', 'child', 'width=480,height=360');
        window.addEventListener('message', e => {
          document.getElementById('out').textContent = 'Message from child: ' + e.data;
          fetch('/result?popup.message=' + encodeURIComponent(e.data));
        });
        </script>
        """;

    static final String POPUP_CHILD = """
        <!doctype html><meta charset=utf-8><title>Popup child</title>
        <p id=out></p>
        <script>
        const via = new URLSearchParams(location.search).get('via') || 'unknown';
        const has = !!window.opener;
        if (has) window.opener.postMessage('child via ' + via, '*');
        fetch('/result?popup.opener.' + via + '=' + (has ? 'present' : 'missing'));
        document.getElementById('out').textContent = 'Opened via ' + via + '; opener ' + (has ? 'present' : 'missing');
        </script>
        """;

    private static final String INPUT = """
        <!doctype html><meta charset=utf-8><title>Input test</title>
        <h1>Input test</h1>
        <p><input id=line placeholder="Type here (try your input method)" size=40></p>
        <p><textarea rows=4 cols=60 placeholder="Several lines; try copy and paste"></textarea></p>
        <div contenteditable style="border:1px solid #888;padding:6px;width:420px">Editable text</div>
        <p><select>{options}</select></p>
        <p><input type=file></p>
        <p id=keys>Press the palette shortcut (Cmd+Shift+P or Ctrl+Shift+P) with the focus here.</p>
        <div style="height:3000px;background:linear-gradient(#fff,#9cf)">Scroll down</div>
        <script>
        document.addEventListener('keydown', e => {
          if ((e.metaKey || e.ctrlKey) && e.shiftKey && e.key.toLowerCase() === 'p') {
            document.getElementById('keys').textContent = 'The page saw the palette shortcut too.';
            fetch('/result?keys.page_saw_palette_shortcut=yes');
          }
        });
        document.getElementById('line').addEventListener('input', e => fetch('/result?input.typed=' + encodeURIComponent(e.target.value)));
        </script>
        """;

    static final String ANIM = """
        <!doctype html><meta charset=utf-8><title>Animation</title>
        <canvas id=c width=600 height=200></canvas><p id=s></p>
        <script>
        let raf = 0, timer = 0;
        const c = document.getElementById('c').getContext('2d');
        function frame(t) { raf++; c.fillStyle = '#fff'; c.fillRect(0, 0, 600, 200); c.fillStyle = '#36c'; c.fillRect((t / 5) % 600, 80, 40, 40); requestAnimationFrame(frame); }
        requestAnimationFrame(frame);
        setInterval(() => timer++, 100);
        setInterval(() => { document.getElementById('s').textContent = 'raf ' + raf + ', timer ' + timer; fetch('/tick?raf=' + raf + '&timer=' + timer); }, 1000);
        </script>
        """;

    static final String DOWNLOAD = """
        <!doctype html><meta charset=utf-8><title>Download test</title>
        <h1>Download test</h1>
        <p><a href=/file.bin>Download a 1 MiB file</a></p>
        """;

    static final String CERT = """
        <!doctype html><meta charset=utf-8><title>HTTPS page</title>
        <h1>HTTPS page with a self-signed certificate</h1>
        <script>fetch('/result?cert.loaded=yes');</script>
        """;

    static String input() {
        StringBuilder options = new StringBuilder();
        for (int i = 1; i <= 20; i++) options.append("<option>Choice ").append(i).append("</option>");
        return INPUT.replace("{options}", options);
    }

    static String find() {
        StringBuilder body = new StringBuilder("<!doctype html><meta charset=utf-8><title>Find page</title><h1>Find page</h1>");
        for (int i = 1; i <= 40; i++) body.append("<p>Paragraph ").append(i).append(": the jasper spike checks find in page.</p>");
        return body.toString();
    }
}
```

- [ ] **Step 4: Write `src/spike/TestPages.java`**

```java
package spike;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** The spike's test pages over HTTP, and over HTTPS with a self-signed certificate when keytool is available. */
final class TestPages implements AutoCloseable {
    private static final char[] PASSWORD = "spikepass".toCharArray();
    private final Report report;
    private final HttpServer http;
    private final HttpsServer https;
    private final Path keyDir;
    private final AtomicLong raf = new AtomicLong();
    private final AtomicLong timer = new AtomicLong();

    private TestPages(Report report, HttpServer http, HttpsServer https, Path keyDir) {
        this.report = report;
        this.http = http;
        this.https = https;
        this.keyDir = keyDir;
    }

    static Path defaultKeytool() {
        return Path.of(System.getProperty("java.home"), "bin", Platform.isWindows() ? "keytool.exe" : "keytool");
    }

    static TestPages start(Report report, Path keytool) throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HttpServer http = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        Path keyDir = Files.createTempDirectory("jasper-spike-tls");
        HttpsServer https = null;
        try {
            SSLContext context = selfSigned(keytool, keyDir.resolve("spike.p12"));
            https = HttpsServer.create(new InetSocketAddress(loopback, 0), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(context));
            report.put("https.available", true);
        } catch (Exception e) {
            report.put("https.available", false);
            report.put("https.unavailable_reason", e.toString());
        }
        TestPages pages = new TestPages(report, http, https, keyDir);
        http.createContext("/", pages::handle);
        http.start();
        if (https != null) {
            https.createContext("/", pages::handle);
            https.start();
        }
        report.put("pages.http_port", pages.httpPort());
        report.put("pages.https_port", pages.httpsPort());
        return pages;
    }

    int httpPort() {
        return http.getAddress().getPort();
    }

    int httpsPort() {
        return https == null ? -1 : https.getAddress().getPort();
    }

    String url(String path) {
        return "http://127.0.0.1:" + httpPort() + path;
    }

    String secureUrl(String path) {
        return "https://127.0.0.1:" + httpsPort() + path;
    }

    long rafCount() {
        return raf.get();
    }

    long timerCount() {
        return timer.get();
    }

    Path keyDir() {
        return keyDir;
    }

    @Override
    public void close() {
        http.stop(0);
        if (https != null) https.stop(0);
        Platform.deleteTree(keyDir);
    }

    private static SSLContext selfSigned(Path keytool, Path store) throws Exception {
        if (!Files.isRegularFile(keytool)) throw new IOException("keytool not found at " + keytool);
        Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "spike", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "7", "-dname", "CN=127.0.0.1", "-ext", "SAN=ip:127.0.0.1",
            "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", "spikepass", "-keypass", "spikepass")
            .redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IOException("keytool failed: " + out);
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            keys.load(in, PASSWORD);
        }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keys, PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(factory.getKeyManagers(), null, null);
        return context;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        try {
            switch (path) {
                case "/" -> html(exchange, PageHtml.INDEX);
                case "/popup" -> html(exchange, PageHtml.POPUP);
                case "/popup-child" -> html(exchange, PageHtml.POPUP_CHILD);
                case "/input" -> html(exchange, PageHtml.input());
                case "/find" -> html(exchange, PageHtml.find());
                case "/anim" -> html(exchange, PageHtml.ANIM);
                case "/download" -> html(exchange, PageHtml.DOWNLOAD);
                case "/cert" -> html(exchange, PageHtml.CERT);
                case "/file.bin" -> download(exchange);
                case "/result" -> {
                    record(query);
                    exchange.sendResponseHeaders(204, -1);
                }
                case "/tick" -> {
                    tick(query);
                    exchange.sendResponseHeaders(204, -1);
                }
                default -> exchange.sendResponseHeaders(404, -1);
            }
        } finally {
            exchange.close();
        }
    }

    private void record(String rawQuery) {
        if (rawQuery == null) return;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) report.put("page." + decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
        }
    }

    private void tick(String rawQuery) {
        if (rawQuery == null) return;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = pair.substring(0, eq);
            long value = Long.parseLong(pair.substring(eq + 1));
            if (key.equals("raf")) raf.set(value);
            if (key.equals("timer")) timer.set(value);
        }
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static void html(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void download(HttpExchange exchange) throws IOException {
        byte[] bytes = new byte[1 << 20];
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"spike-download.bin\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
```

- [ ] **Step 5: Run the self-check**

```bash
tools/spikes/browser/spike.sh --self-check
ls "${TMPDIR:-/tmp}" | grep -c "jasper-spike-"
```

Expected:
- twenty-one `PASS` lines (`pages_https_is_untrusted` among them, because the JBR SDK has `keytool`), then `SELF-CHECK PASSED`;
- then `0`.

- [ ] **Step 6: Commit**

```bash
git add tools/spikes/browser/src/spike
git commit -m "Browser spike: local test pages over HTTP and self-signed HTTPS

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: The Chromium session

**Files:**
- Create: `tools/spikes/browser/src/spike/CountingOsrHandler.java`, `SpikeOsrBrowser.java`, `BrowserView.java`, `CefSession.java`

**Interfaces:**
- Consumes:
  - `JBCefOsrComponent` and `JBCefOsrHandler` (Task 1);
  - `Options` and `Report` (Task 2).
- Produces:
  - **`CefSession(Options, Report, CefSession.Listener)`**, with these members:
    - `static void startupEarly()`;
    - `boolean ready()`;
    - `void start(Runnable onReady)`, which calls `onReady` on the EDT;
    - `BrowserView createBrowser(String url, Runnable onFirstPaint)`, called on the EDT; `onFirstPaint` runs on Chromium's thread;
    - `void disposeAsync()`, called on the EDT;
    - `boolean awaitTerminated(long seconds)`;
    - `boolean restart(Runnable onReady)`, which returns false when the mode cannot restart.
  - **`CefSession.Listener`**: `log(String)` from any thread, and `titleChanged(BrowserView, String)`, `popupAsTab(String url)` and `devToolsCreated(JComponent)`, all on the EDT.
  - **`BrowserView`**, with these members:
    - `JComponent component()`, `long paints()` and `String url()`;
    - navigation: `load(String)`, `back()` and `reload()`;
    - `find(String)`, `zoomBy(double)` and `String openDevTools()`;
    - `setFrameRate(int)`, `int frameRate()` and `double deviceScale()`;
    - `close()`.
  - **Report keys:**
    - start-up: `cef.version`, `cef.remote_supported`, `cef.remote_enabled`, `cef.args`, `cef.no_sandbox_setting`, `cef.state`, `cef.child_launch`, `cef.child_no_sandbox_flag`, `<tag>.init_ms`, `cef.start_error`;
    - popups and browsers: `popup.requested`, `popup.native_browser_created`, `browser.before_close`;
    - pages: `load.error`, `cert.error`, `cert.choice`, `download.started`, `download.complete`, `download.canceled`;
    - restart: `restart.same_instance`, `restart.result`.

- [ ] **Step 1: Write `src/spike/CountingOsrHandler.java`**

```java
package spike;

import org.cef.browser.CefBrowser;
import spike.osr.JBCefOsrComponent;
import spike.osr.JBCefOsrHandler;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/** JetBrains' off-screen handler, counting frames and signalling the first one. */
final class CountingOsrHandler extends JBCefOsrHandler {
    private final AtomicLong paints = new AtomicLong();
    private final Runnable onFirstPaint;

    CountingOsrHandler(JBCefOsrComponent component, Runnable onFirstPaint) {
        super(component, null);
        this.onFirstPaint = onFirstPaint;
    }

    long paints() {
        return paints.get();
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        count();
        super.onPaint(browser, popup, dirtyRects, buffer, width, height);
    }

    @Override
    public void onPaintWithSharedMem(CefBrowser browser, boolean popup, int dirtyRectsCount, String sharedMemName,
                                     long sharedMemHandle, int width, int height) {
        count();
        super.onPaintWithSharedMem(browser, popup, dirtyRectsCount, sharedMemName, sharedMemHandle, width, height);
    }

    private void count() {
        if (paints.getAndIncrement() == 0) onFirstPaint.run();
    }
}
```

- [ ] **Step 2: Write `src/spike/SpikeOsrBrowser.java`**

```java
package spike;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.browser.CefRequestContext;
import org.cef.handler.CefRenderHandler;
import spike.osr.JBCefOsrComponent;
import spike.osr.JBCefOsrHandler;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Point;
import java.util.function.Consumer;

/** In-process off-screen browser that keeps the window open on close and draws DevTools off-screen too. */
final class SpikeOsrBrowser extends CefBrowserOsrWithHandler {
    private final Consumer<JComponent> devToolsSink;
    private final boolean devTools;

    SpikeOsrBrowser(CefClient client, String url, CefRenderHandler handler, Component component,
                    Consumer<JComponent> devToolsSink) {
        super(client, url, null, handler, component);
        this.devToolsSink = devToolsSink;
        this.devTools = false;
    }

    private SpikeOsrBrowser(CefClient client, String url, CefRequestContext context, CefRenderHandler handler,
                            Component component, CefBrowser parent, Point inspectAt, Consumer<JComponent> devToolsSink) {
        super(client, url, context, handler, component, parent, inspectAt);
        this.devToolsSink = devToolsSink;
        this.devTools = true;
    }

    boolean isDevTools() {
        return devTools;
    }

    /** CefBrowser_N.doClose asks the root window to close once close is allowed; false lets Chromium close only this browser. */
    @Override
    public boolean doClose() {
        return false;
    }

    @Override
    protected CefBrowser createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser parent,
                                               Point inspectAt) {
        JBCefOsrComponent component = new JBCefOsrComponent();
        JBCefOsrHandler handler = new JBCefOsrHandler(component, null);
        SpikeOsrBrowser browser = new SpikeOsrBrowser(client, url, context, handler, component, parent, inspectAt, devToolsSink);
        component.setBrowser(browser);
        devToolsSink.accept(component);
        return browser;
    }
}
```

- [ ] **Step 3: Write `src/spike/BrowserView.java`**

```java
package spike;

import com.jetbrains.cef.JCefAppConfig;
import org.cef.browser.CefBrowser;
import spike.osr.JBCefOsrComponent;

import javax.swing.JComponent;
import java.util.concurrent.TimeUnit;

/** One browser and its Swing component, behind plain methods so the window needs no JCEF types. */
final class BrowserView {
    private final JBCefOsrComponent component;
    private final CountingOsrHandler handler;
    private final CefBrowser browser;

    BrowserView(JBCefOsrComponent component, CountingOsrHandler handler, CefBrowser browser) {
        this.component = component;
        this.handler = handler;
        this.browser = browser;
    }

    JComponent component() {
        return component;
    }

    long paints() {
        return handler.paints();
    }

    String url() {
        return browser.getURL();
    }

    void load(String url) {
        browser.loadURL(url);
    }

    void back() {
        if (browser.canGoBack()) browser.goBack();
    }

    void reload() {
        browser.reload();
    }

    void find(String text) {
        browser.find(text, true, false, false);
    }

    void zoomBy(double delta) {
        browser.setZoomLevel(browser.getZoomLevel() + delta);
    }

    /** "requested", or why it failed (remote browsers and the plain OSR class cannot open DevTools). */
    String openDevTools() {
        try {
            browser.openDevTools();
            return "requested";
        } catch (Throwable failure) {
            return "failed: " + failure;
        }
    }

    void setFrameRate(int framesPerSecond) {
        browser.setWindowlessFrameRate(framesPerSecond);
    }

    int frameRate() {
        try {
            return browser.getWindowlessFrameRate().get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return -1;
        }
    }

    double deviceScale() {
        return JCefAppConfig.getDeviceScaleFactor(component);
    }

    /** IntelliJ's close sequence (JBCefBrowserBase.dispose). */
    void close() {
        browser.stopLoad();
        browser.setCloseAllowed();
        browser.close(true);
    }

    CefBrowser browser() {
        return browser;
    }
}
```

- [ ] **Step 4: Write `src/spike/CefSession.java`**

```java
package spike;

import com.jetbrains.cef.JCefAppConfig;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.SystemBootstrap;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefRendering;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefDownloadHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.security.CefSSLInfo;
import spike.osr.JBCefOsrComponent;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Starts, uses, disposes and restarts Chromium for the spike, writing what happens into the report. */
final class CefSession {
    interface Listener {
        void log(String line);

        void titleChanged(BrowserView view, String title);

        void popupAsTab(String url);

        void devToolsCreated(JComponent component);
    }

    private final Options options;
    private final Report report;
    private final Listener listener;
    private final Path workDir = Path.of("build", "cef-" + ProcessHandle.current().pid());
    private final Map<CefBrowser, BrowserView> views = new ConcurrentHashMap<>();
    private volatile CountDownLatch terminated = new CountDownLatch(1);
    private volatile CefApp app;
    private volatile CefClient client;
    private String[] argv;
    private CefSettings settings;
    private File serverExe;

    CefSession(Options options, Report report, Listener listener) {
        this.options = options;
        this.report = report;
        this.listener = listener;
    }

    /** For --eager: CefApp.startup before AWT starts, which Linux may need for XInitThreads. */
    static void startupEarly() {
        JCefAppConfig config = JCefAppConfig.getInstance();
        SystemBootstrap.setLoader(config.getLoader());
        CefApp.startup(config.getAppArgs());
    }

    boolean ready() {
        return client != null;
    }

    /** Starts Chromium off the EDT (its pre-initialisation needs the EDT free) and calls onReady on the EDT. */
    void start(Runnable onReady) {
        long started = System.nanoTime();
        Thread thread = new Thread(() -> {
            try {
                JCefAppConfig config = JCefAppConfig.getInstance();
                report.put("cef.version", JCefAppConfig.getVersion());
                report.put("cef.remote_supported", config.isRemoteSupported());
                report.put("cef.remote_enabled", config.isRemoteEnabled());
                CefApp.setIsRemoteEnabled(options.mode() == Options.Mode.REMOTE);
                SystemBootstrap.setLoader(config.getLoader());
                if (!options.eager()) CefApp.startup(config.getAppArgs());
                Files.createDirectories(workDir.resolve("downloads"));
                settings = config.getCefSettings();
                settings.windowless_rendering_enabled = true;
                settings.no_sandbox = !options.sandbox();
                settings.cache_path = workDir.resolve("cache").toAbsolutePath().toString();
                settings.persist_session_cookies = true;
                settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
                settings.log_file = workDir.resolve("chromium.log").toAbsolutePath().toString();
                List<String> args = new ArrayList<>(List.of(config.getAppArgs()));
                args.add("--disable-component-update");
                argv = args.toArray(String[]::new);
                serverExe = config.getServerExe();
                report.put("cef.args", String.join(" ", argv));
                report.put("cef.no_sandbox_setting", settings.no_sandbox);
                CefApp.addAppHandler(new CefAppHandlerAdapter(argv) {
                    @Override
                    public void stateHasChanged(CefApp.CefAppState state) {
                        report.put("cef.state", state);
                        if (state == CefApp.CefAppState.TERMINATED) terminated.countDown();
                    }

                    @Override
                    public void onBeforeChildProcessLaunch(String commandLine) {
                        report.put("cef.child_launch", commandLine);
                        if (commandLine.contains("--no-sandbox")) report.put("cef.child_no_sandbox_flag", true);
                        super.onBeforeChildProcessLaunch(commandLine);
                    }
                });
                initialize(CefApp.getInstance(argv, settings, serverExe), started, "cef", onReady);
            } catch (Throwable failure) {
                report.put("cef.start_error", failure);
                listener.log("Chromium failed to start: " + failure);
            }
        }, "spike-cef-start");
        thread.setDaemon(true);
        thread.start();
    }

    /** Creates an off-screen browser; on the EDT. onFirstPaint runs on Chromium's thread. */
    BrowserView createBrowser(String url, Runnable onFirstPaint) {
        JBCefOsrComponent component = new JBCefOsrComponent();
        CountingOsrHandler handler = new CountingOsrHandler(component, onFirstPaint);
        CefBrowser browser = options.mode() == Options.Mode.REMOTE
            ? client.createBrowser(url, new CefRendering.CefRenderingWithHandler(handler, component), false)
            : new SpikeOsrBrowser(client, url, handler, component, listener::devToolsCreated);
        component.setBrowser(browser);
        BrowserView view = new BrowserView(component, handler, browser);
        views.put(browser, view);
        return view;
    }

    /** Closes every browser and starts Chromium's shutdown; on the EDT. */
    void disposeAsync() {
        CefApp current = app;
        if (current == null || current.isTerminated()) {
            terminated.countDown();
            return;
        }
        for (BrowserView view : List.copyOf(views.values())) view.close();
        CefClient closing = client;
        client = null;
        if (closing != null) closing.dispose();
        current.dispose();
    }

    boolean awaitTerminated(long seconds) {
        try {
            return terminated.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Starts a new Chromium after dispose. Returns false when it will not call onReady (in-process cannot restart). */
    boolean restart(Runnable onReady) {
        if (argv == null) {
            report.put("restart.result", "Chromium never started");
            return false;
        }
        if (options.mode() == Options.Mode.INPROCESS) {
            try {
                CefApp.getInstance(argv, settings, serverExe);
                report.put("restart.result", "in-process getInstance returned without error");
            } catch (RuntimeException refused) {
                report.put("restart.result", "refused: " + refused.getMessage());
            }
            return false;
        }
        long started = System.nanoTime();
        terminated = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                CefApp next = CefApp.getInstance(argv, settings, serverExe);
                CefApp.setDefaultInstance(next);
                report.put("restart.same_instance", next == app);
                initialize(next, started, "restart", () -> {
                    report.put("restart.result", "initialized");
                    onReady.run();
                });
            } catch (Throwable failure) {
                report.put("restart.result", "failed: " + failure);
            }
        }, "spike-cef-restart");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private void initialize(CefApp instance, long started, String tag, Runnable onReady) {
        app = instance;
        instance.onInitialization(state -> {
            report.put(tag + ".init_ms", (System.nanoTime() - started) / 1_000_000);
            SwingUtilities.invokeLater(() -> {
                CefClient created = instance.createClient();
                wire(created);
                client = created;
                onReady.run();
            });
        });
    }

    private void wire(CefClient target) {
        target.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                report.put("popup.requested", targetUrl + " name=" + targetFrameName + " policy=" + options.popups());
                if (options.popups() == Options.PopupPolicy.TAB) {
                    SwingUtilities.invokeLater(() -> listener.popupAsTab(targetUrl));
                    return true;
                }
                return false;
            }

            @Override
            public void onAfterCreated(CefBrowser browser) {
                boolean ours = views.containsKey(browser) || browser instanceof SpikeOsrBrowser osr && osr.isDevTools();
                if (!ours) {
                    report.put("popup.native_browser_created", browser.getClass().getName() + " windowless=" + browser.isWindowless());
                }
            }

            @Override
            public void onBeforeClose(CefBrowser browser) {
                report.put("browser.before_close", browser.getIdentifier());
                views.remove(browser);
            }
        });
        target.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                BrowserView view = views.get(browser);
                if (view != null) SwingUtilities.invokeLater(() -> listener.titleChanged(view, title));
            }
        });
        target.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode errorCode, String errorText,
                                    String failedUrl) {
                report.put("load.error", errorCode + " " + errorText + " " + failedUrl);
            }
        });
        target.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onCertificateError(CefBrowser browser, CefLoadHandler.ErrorCode certError, String requestUrl,
                                              CefSSLInfo sslInfo, CefCallback callback) {
                report.put("cert.error", certError + " " + requestUrl + " callback=" + (callback != null));
                if (callback == null) return false;
                SwingUtilities.invokeLater(() -> {
                    int answer = JOptionPane.showConfirmDialog(null,
                        "Certificate error " + certError + " for\n" + requestUrl + "\n\nContinue to this site (unsafe)?",
                        "Spike: certificate", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    boolean proceed = answer == JOptionPane.YES_OPTION;
                    report.put("cert.choice", proceed ? "continue" : "cancel");
                    if (proceed) callback.Continue(); else callback.cancel();
                });
                return true;
            }
        });
        target.addDownloadHandler(new CefDownloadHandlerAdapter() {
            @Override
            public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem item, String suggestedName,
                                            CefBeforeDownloadCallback callback) {
                String name = suggestedName == null || suggestedName.isBlank() ? "download.bin" : suggestedName;
                Path destination = workDir.resolve("downloads").resolve(name).toAbsolutePath();
                report.put("download.started", item.getURL() + " -> " + destination);
                callback.Continue(destination.toString(), false);
                return true;
            }

            @Override
            public void onDownloadUpdated(CefBrowser browser, CefDownloadItem item, CefDownloadItemCallback callback) {
                if (item.isComplete()) report.put("download.complete", item.getFullPath() + " bytes=" + item.getReceivedBytes());
                else if (item.isCanceled()) report.put("download.canceled", item.getURL());
            }
        });
    }
}
```

- [ ] **Step 5: Compile and run the self-check**

```bash
tools/spikes/browser/spike.sh --self-check
```

Expected:
- the sources compile;
- twenty-one `PASS` lines and `SELF-CHECK PASSED`. The self-check never touches `CefSession`.

If an adapter method fails to compile, read its signature in the JCEF runtime and match it exactly, then record the difference in this plan's banner. Read it with `"$JCEF_JDK/bin/javap" -p --module jcef org.cef.handler.<Adapter>`.

- [ ] **Step 6: Commit**

```bash
git add tools/spikes/browser/src/spike
git commit -m "Browser spike: Chromium session with off-screen browsers, handlers, dispose and restart

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: The spike window and the checklist

**Files:**
- Create: `tools/spikes/browser/src/spike/ChecklistDialog.java`, `tools/spikes/browser/src/spike/SpikeWindow.java`
- Modify: `tools/spikes/browser/src/spike/Spike.java` (replaces the "later task" exit)

**Interfaces:**
- Consumes: `CefSession`, `BrowserView`, `TestPages`, `ProcessStats`, `Report`, `Options` and `Platform`.
- Produces:
  - `SpikeWindow.show(Options, Report, TestPages)`, called on the EDT;
  - `ChecklistDialog.show(JFrame, Report)`;
  - report keys:
    - start-up: `startup.jvm_to_window_ms`, `cef.first_paint_ms`, `restart.first_paint_ms`, `tab.first_paint_ms`;
    - processes: `proc.window_shown.*`, `proc.before_cef.*`, `proc.cef_initialized.*`, `proc.cef_first_paint.*`, `proc.after_close.*`, `proc.after_dispose_2s.*`, `proc.after_dispose_10s.*`, `proc.restart_first_paint.*`, `proc.quit.*`;
    - scaling, input and window: `scale.device_factor`, `keys.swing_binding_fired`, `input.pane_drag_press_seen`, `frame.window_closing_event`;
    - the Swing overlays: `ui.overlay_toggled`, `ui.dim_toggled`, `ui.menu_shown`;
    - DevTools and closing: `devtools.result`, `devtools.component_added`, `dispose.terminated`, `browser.closed_from_spike`;
    - hidden pages: `hidden.frame_rate_before`, `hidden.visible`, `hidden.hidden`, `hidden.hidden_1fps`, `hidden.visible_again`;
    - the checklist: `manual.<id>` and `manual.notes`;
    - the run's end: `run.end`.

- [ ] **Step 1: Write `src/spike/ChecklistDialog.java`**

```java
package spike;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** The checks a person answers by eye after a run; the answers go into the report as manual.<id>. */
final class ChecklistDialog {
    record Item(String id, String question) {
    }

    static final List<Item> ITEMS = List.of(
        new Item("crisp", "Text and images on the page look crisp at this display's scale."),
        new Item("scale_move", "Moving the window to a monitor with a different scale keeps the page crisp and correctly sized."),
        new Item("overlay", "The overlay (Overlay button or the palette shortcut) paints over the page and takes typing."),
        new Item("dim", "The Dim layer paints over the page."),
        new Item("menu_tooltip", "The Swing menu and the Swing tooltip paint over the page."),
        new Item("typing", "Typing in the Input test fields works, including your input method if you use one."),
        new Item("scroll", "Wheel and trackpad scrolling feel right (speed and direction)."),
        new Item("select", "The dropdown on the Input test opens in the right place."),
        new Item("clipboard", "Copy and paste in a page field work with the usual keys."),
        new Item("file_chooser", "The file field opens a file chooser."),
        new Item("devtools", "DevTools open in a tab and work (in-process runs)."),
        new Item("find_zoom", "Find \"jasper\" highlights matches, and Zoom + and Zoom - change the page size."),
        new Item("sandbox_page", "chrome://sandbox (Windows and Linux): copy its summary into the note."),
        new Item("real_site", "github.com renders and scrolls smoothly."),
        new Item("popup", "What appeared for window.open, the target=_blank link and the noopener link: describe it in the note."),
        new Item("close_dispose", "Close browser, Dispose Chromium and Restart Chromium behaved as the README describes, and the window stayed open."));

    private ChecklistDialog() {
    }

    static void show(JFrame owner, Report report) {
        JDialog dialog = new JDialog(owner, "Spike checklist", true);
        JPanel grid = new JPanel(new GridBagLayout());
        List<JComboBox<String>> answers = new ArrayList<>();
        List<JTextField> notes = new ArrayList<>();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < ITEMS.size(); i++) {
            JComboBox<String> answer = new JComboBox<>(new String[] {"not tried", "yes", "no", "partly"});
            JTextField note = new JTextField(24);
            c.gridy = i;
            c.gridx = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            grid.add(new JLabel("<html><body style='width:420px'>" + ITEMS.get(i).question() + "</body></html>"), c);
            c.gridx = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            grid.add(answer, c);
            c.gridx = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            grid.add(note, c);
            answers.add(answer);
            notes.add(note);
        }
        JTextArea free = new JTextArea(4, 60);
        JButton save = new JButton("Save to report");
        save.addActionListener(e -> {
            for (int i = 0; i < ITEMS.size(); i++) {
                report.put("manual." + ITEMS.get(i).id(), answers.get(i).getSelectedItem() + " | " + notes.get(i).getText());
            }
            report.put("manual.notes", free.getText());
            dialog.dispose();
        });
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(new JLabel("Anything else you noticed:"), BorderLayout.NORTH);
        bottom.add(new JScrollPane(free), BorderLayout.CENTER);
        bottom.add(save, BorderLayout.EAST);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(grid), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(1000, 720);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
```

- [ ] **Step 2: Write `src/spike/SpikeWindow.java`**

```java
package spike;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The spike window: browser tabs in a layered pane under Swing overlays, with probes and measurement buttons. */
final class SpikeWindow {
    private final Options options;
    private final Report report;
    private final TestPages pages;
    private final JFrame frame;
    private final CefSession session;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel hiddenPlaceholder = new JPanel();
    private final JPanel overlay = new JPanel(new BorderLayout(8, 8));
    private final JTextField overlayField = new JTextField("Overlay focus test: type here");
    private final JComponent dim = new DimLayer();
    private final JTextArea logArea = new JTextArea(6, 100);
    private final Map<Component, BrowserView> views = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "spike-worker");
        thread.setDaemon(true);
        return thread;
    });
    private String firstPaintTag = "cef";
    private int snapshots;
    private boolean quitting;

    private SpikeWindow(Options options, Report report, TestPages pages) {
        this.options = options;
        this.report = report;
        this.pages = pages;
        this.frame = new JFrame("Jasper browser spike: " + Platform.id() + " " + options.label());
        this.session = new CefSession(options, report, new CefSession.Listener() {
            @Override
            public void log(String line) {
                SpikeWindow.this.log(line);
            }

            @Override
            public void titleChanged(BrowserView view, String title) {
                int index = tabs.indexOfComponent(view.component());
                if (index >= 0) tabs.setTitleAt(index, title.length() > 28 ? title.substring(0, 27) + "..." : title);
            }

            @Override
            public void popupAsTab(String url) {
                openTab(url);
            }

            @Override
            public void devToolsCreated(JComponent component) {
                tabs.addTab("DevTools", component);
                tabs.setSelectedComponent(component);
                report.put("devtools.component_added", true);
            }
        });
    }

    static void show(Options options, Report report, TestPages pages) {
        new SpikeWindow(options, report, pages).open();
    }

    private void open() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                report.put("startup.jvm_to_window_ms",
                    System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime());
                worker.execute(() -> ProcessStats.record(report, "window_shown"));
                Timer later = new Timer(500, event -> startChromium());
                later.setRepeats(false);
                later.start();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (quitting) return;
                report.put("frame.window_closing_event", "received at " + Instant.now());
                int answer = JOptionPane.showConfirmDialog(frame, "The window got a close request. Quit the spike?", "Spike",
                    JOptionPane.YES_NO_OPTION);
                if (answer == JOptionPane.YES_OPTION) quit();
            }
        });
        logArea.setEditable(false);
        frame.setLayout(new BorderLayout());
        frame.add(toolbar(), BorderLayout.NORTH);
        frame.add(stage(), BorderLayout.CENTER);
        frame.add(new JScrollPane(logArea), BorderLayout.SOUTH);
        installProbes();
        frame.setSize(1280, 900);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JComponent toolbar() {
        JPanel rows = new JPanel(new GridLayout(3, 1));
        rows.add(row(
            button("Index", "The spike's index page", () -> navigate(pages.url("/"))),
            button("Popup test", "window.open, target=_blank and noopener", () -> navigate(pages.url("/popup"))),
            button("Input test", "Fields, input method, dropdown, file chooser, shortcuts, scrolling", () -> navigate(pages.url("/input"))),
            button("Find page", "Text for Find \"jasper\"", () -> navigate(pages.url("/find"))),
            button("Animation", "The page Measure hidden uses", () -> navigate(pages.url("/anim"))),
            button("Download", "A 1 MiB attachment", () -> navigate(pages.url("/download"))),
            button("Cert (HTTPS)", "A page with a self-signed certificate", this::openCertPage),
            button("github.com", "A real site", () -> navigate("https://github.com/")),
            button("chrome://version", "Chromium's version and command line", () -> navigate("chrome://version")),
            button("chrome://sandbox", "Sandbox status (Windows and Linux)", () -> navigate("chrome://sandbox"))));
        rows.add(row(
            button("New tab", "Opens the index in a new tab", () -> openTab(pages.url("/"))),
            button("Back", "Back in this tab", () -> current().ifPresent(BrowserView::back)),
            button("Reload", "Reloads this tab", () -> current().ifPresent(BrowserView::reload)),
            button("Find \"jasper\"", "Finds on this page", () -> current().ifPresent(view -> view.find("jasper"))),
            button("Zoom +", "Zooms in", () -> current().ifPresent(view -> view.zoomBy(0.5))),
            button("Zoom -", "Zooms out", () -> current().ifPresent(view -> view.zoomBy(-0.5))),
            button("DevTools", "Opens DevTools in a tab (in-process)", this::openDevTools),
            button("Overlay", "A Swing card over the page (stands in for the palette)", this::toggleOverlay),
            button("Dim", "A translucent Swing layer over the page", this::toggleDim),
            menuButton()));
        rows.add(row(
            button("Measure hidden", "Painting and page timers while visible, hidden, hidden at 1 fps, visible again (about 25 s)", this::measureHidden),
            button("Snapshot", "Records helper processes and memory now", () -> snapshot("manual_" + (++snapshots))),
            button("Close browser", "Closes this tab's browser", this::closeCurrent),
            button("Dispose Chromium", "Closes every browser and shuts Chromium down", this::disposeChromium),
            button("Restart Chromium", "Starts Chromium again after Dispose", this::restartChromium),
            button("Checklist...", "Answers the checks by eye", () -> ChecklistDialog.show(frame, report)),
            button("Quit", "Saves the end of the report and exits", this::quit)));
        return rows;
    }

    private JComponent stage() {
        JLayeredPane stage = new JLayeredPane() {
            @Override
            public void doLayout() {
                tabs.setBounds(0, 0, getWidth(), getHeight());
                dim.setBounds(0, 0, getWidth(), getHeight());
                overlay.setBounds((getWidth() - 460) / 2, 60, 460, overlay.getPreferredSize().height);
            }
        };
        hiddenPlaceholder.add(new JLabel("Placeholder tab: browsers in other tabs are not showing."));
        tabs.addTab("Hidden", hiddenPlaceholder);
        overlay.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        overlay.setBackground(new Color(250, 250, 240));
        overlay.add(new JLabel("Swing overlay over the page (stands in for Jasper's palette)"), BorderLayout.NORTH);
        overlay.add(overlayField, BorderLayout.CENTER);
        overlay.setVisible(false);
        dim.setVisible(false);
        stage.add(tabs, JLayeredPane.DEFAULT_LAYER);
        stage.add(dim, JLayeredPane.PALETTE_LAYER);
        stage.add(overlay, JLayeredPane.MODAL_LAYER);
        return stage;
    }

    private void installProbes() {
        int menuMask = Platform.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        KeyStroke palette = KeyStroke.getKeyStroke(KeyEvent.VK_P, menuMask | InputEvent.SHIFT_DOWN_MASK);
        JRootPane root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(palette, "spike.palette");
        root.getActionMap().put("spike.palette", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                report.put("keys.swing_binding_fired", "yes, focus in " + (owner == null ? "nothing" : owner.getClass().getSimpleName()));
                toggleOverlay();
            }
        });
        int dragMask = Platform.isMac() ? InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            : InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseEvent mouse && mouse.getID() == MouseEvent.MOUSE_PRESSED
                && (mouse.getModifiersEx() & dragMask) == dragMask && views.containsKey(mouse.getComponent())) {
                report.put("input.pane_drag_press_seen", "yes at " + mouse.getPoint());
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void startChromium() {
        log("Starting Chromium (" + options.label() + ")");
        worker.execute(() -> ProcessStats.record(report, "before_cef"));
        session.start(() -> {
            log("Chromium is ready");
            worker.execute(() -> ProcessStats.record(report, "cef_initialized"));
            openTab(pages.url("/"));
        });
    }

    private void openTab(String url) {
        if (!session.ready()) {
            log("Chromium is not ready");
            return;
        }
        String tag = firstPaintTag;
        firstPaintTag = "tab";
        long created = System.nanoTime();
        BrowserView view = session.createBrowser(url, () -> {
            report.put(tag + ".first_paint_ms", (System.nanoTime() - created) / 1_000_000);
            worker.execute(() -> ProcessStats.record(report, tag + "_first_paint"));
        });
        view.component().setToolTipText("Swing tooltip over the page");
        view.component().addPropertyChangeListener("graphicsConfiguration",
            event -> report.put("scale.device_factor", view.deviceScale()));
        views.put(view.component(), view);
        tabs.addTab("Loading", view.component());
        tabs.setSelectedComponent(view.component());
        report.put("scale.device_factor", view.deviceScale());
    }

    private Optional<BrowserView> current() {
        return Optional.ofNullable(views.get(tabs.getSelectedComponent()));
    }

    private void navigate(String url) {
        current().ifPresentOrElse(view -> view.load(url), () -> openTab(url));
    }

    private void openCertPage() {
        if (pages.httpsPort() < 0) {
            log("HTTPS is unavailable: " + report.get("https.unavailable_reason").orElse("?"));
            return;
        }
        navigate(pages.secureUrl("/cert"));
    }

    private void openDevTools() {
        if (options.mode() == Options.Mode.REMOTE) {
            report.put("devtools.result", "not wired in remote mode");
            log("DevTools are not wired in remote mode");
            return;
        }
        current().ifPresent(view -> report.put("devtools.result", view.openDevTools()));
    }

    private void toggleOverlay() {
        overlay.setVisible(!overlay.isVisible());
        report.put("ui.overlay_toggled", overlay.isVisible());
        if (overlay.isVisible()) overlayField.requestFocusInWindow();
    }

    private void toggleDim() {
        dim.setVisible(!dim.isVisible());
        report.put("ui.dim_toggled", dim.isVisible());
    }

    private JButton menuButton() {
        JButton button = new JButton("Menu");
        button.setToolTipText("A Swing popup menu over the page");
        JPopupMenu menu = new JPopupMenu();
        for (int i = 1; i <= 6; i++) menu.add(new JMenuItem("Swing menu item " + i));
        button.addActionListener(e -> {
            menu.show(tabs, 40, 40);
            report.put("ui.menu_shown", true);
        });
        return button;
    }

    private void measureHidden() {
        Optional<BrowserView> selected = current();
        if (selected.isEmpty()) {
            log("Open the Animation page in a tab first");
            return;
        }
        BrowserView view = selected.get();
        Component tab = view.component();
        log("Measuring visible, hidden, hidden at 1 fps, visible again (about 25 s). Leave the window alone.");
        worker.execute(() -> {
            try {
                int before = view.frameRate();
                report.put("hidden.frame_rate_before", before);
                phase("visible", view, () -> tabs.setSelectedComponent(tab));
                phase("hidden", view, () -> tabs.setSelectedComponent(hiddenPlaceholder));
                view.setFrameRate(1);
                phase("hidden_1fps", view, () -> { });
                view.setFrameRate(before > 0 ? before : 30);
                phase("visible_again", view, () -> tabs.setSelectedComponent(tab));
                log("Measurement done");
            } catch (Exception failure) {
                report.put("hidden.error", failure);
            }
        });
    }

    private void phase(String name, BrowserView view, Runnable ui) throws Exception {
        SwingUtilities.invokeAndWait(ui);
        Thread.sleep(1000);
        long paints = view.paints();
        long raf = pages.rafCount();
        long timer = pages.timerCount();
        Thread.sleep(5000);
        report.put("hidden." + name, String.format(Locale.ROOT, "paints/s=%.1f raf/s=%.1f timer/s=%.1f showing=%s",
            (view.paints() - paints) / 5.0, (pages.rafCount() - raf) / 5.0, (pages.timerCount() - timer) / 5.0,
            view.component().isShowing()));
    }

    private void snapshot(String phase) {
        worker.execute(() -> ProcessStats.record(report, phase));
        log("Snapshot " + phase + " recorded");
    }

    private void closeCurrent() {
        current().ifPresent(view -> {
            view.close();
            views.remove(view.component());
            tabs.remove(view.component());
            report.put("browser.closed_from_spike", Instant.now());
            worker.execute(() -> {
                sleep(3000);
                ProcessStats.record(report, "after_close");
            });
        });
    }

    private void disposeChromium() {
        for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
            if (tabs.getComponentAt(i) != hiddenPlaceholder) tabs.removeTabAt(i);
        }
        views.clear();
        log("Disposing Chromium");
        session.disposeAsync();
        worker.execute(() -> {
            boolean terminated = session.awaitTerminated(30);
            report.put("dispose.terminated", terminated);
            sleep(2000);
            ProcessStats.record(report, "after_dispose_2s");
            sleep(8000);
            ProcessStats.record(report, "after_dispose_10s");
            log("Dispose finished: terminated=" + terminated);
        });
    }

    private void restartChromium() {
        log("Restarting Chromium");
        firstPaintTag = "restart";
        boolean willOpen = session.restart(() -> {
            log("Chromium restarted");
            openTab(pages.url("/"));
        });
        if (!willOpen) {
            firstPaintTag = "tab";
            log("This mode cannot restart: " + report.get("restart.result").orElse("?"));
        }
    }

    private void quit() {
        if (quitting) return;
        quitting = true;
        report.put("run.end", Instant.now());
        session.disposeAsync();
        worker.execute(() -> {
            ProcessStats.record(report, "quit");
            session.awaitTerminated(15);
            pages.close();
            System.exit(0);
        });
    }

    private void log(String line) {
        report.note(line);
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static JButton button(String label, String tooltip, Runnable action) {
        JButton button = new JButton(label);
        button.setToolTipText(tooltip);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JPanel row(JComponent... items) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (JComponent item : List.of(items)) row.add(item);
        return row;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A translucent layer that passes mouse events through to the page. */
    private static final class DimLayer extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
```

- [ ] **Step 3: Replace the placeholder end of `Spike.main`**

In `src/spike/Spike.java`, replace

```java
        System.err.println("Chromium runs arrive in a later task of the spike plan.");
        System.exit(4);
```

with

```java
        if (options.mode() == Options.Mode.REMOTE) System.setProperty("jcef.remote.enabled", "true");
        if (options.eager()) CefSession.startupEarly();
        TestPages pages = TestPages.start(report, TestPages.defaultKeytool());
        SwingUtilities.invokeLater(() -> SpikeWindow.show(options, report, pages));
```

- [ ] **Step 4: Compile and run the self-check**

```bash
tools/spikes/browser/spike.sh --self-check
```

Expected: twenty-one `PASS` lines, then `SELF-CHECK PASSED`. Do not run the spike without `--self-check`: that opens the window, which is the user's job (Global Constraints).

- [ ] **Step 5: Commit**

```bash
git add tools/spikes/browser/src/spike
git commit -m "Browser spike: window with Swing overlays, probes, measurements and checklist

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: The README and the hand-off to the user

**Files:**
- Create: `tools/spikes/browser/README.md`

**Interfaces:**
- Consumes: every option and button of Tasks 2 to 5.
- Produces: the run matrix the user follows, with report files in `tools/spikes/browser/reports/`.

- [ ] **Step 1: Write `tools/spikes/browser/README.md`**

````markdown
# Browser spike (throwaway)

This program answers the questions in section 12 of the
[browser design](../../../docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md) by running JetBrains' JCEF
(Chromium 150.0.7871.129) with off-screen rendering in a Swing window on each platform. Every run writes a report to
`reports/`. Nothing here ships. This branch (`claude/browser-spike`) is never merged.

`src/spike/osr/` holds four classes copied from JetBrains/jcef `java_tests/tests` at commit `9df0879`, under that
repository's licences: BSD-3-Clause, and Apache-2.0 where a file's header says so. The JBR API calls were removed.

## Set up a machine

1. Download `jbrsdk_jcef` 25.0.4.1 b635.75 for the machine, unpack it, and check it against its `.checksum` (SHA-512).
   - Name: `jbrsdk_jcef-25.0.4.1-<platform>-b635.75.tar.gz` (Windows also has `.zip`).
   - Platforms: `osx-aarch64`, `osx-x64`, `windows-x64`, `windows-aarch64`, `linux-x64`, `linux-aarch64`.
   - URL: `https://cache-redirector.jetbrains.com/intellij-jbr/<name>`.
   - If there is no `jbrsdk_jcef` for the platform, `jbr_jcef` works too.
2. Unpack it outside every folder where Gradle looks for JDKs. Otherwise Jasper's builds on that machine could pick it
   up. The folders to avoid:
   - `~/Library/Java/JavaVirtualMachines` and `/Library/Java/JavaVirtualMachines`;
   - `~/.jdks`, `/usr/lib/jvm` and `%USERPROFILE%\.jdks`.

   On the dev Mac it is already in `.jdk/`.
3. Point `JCEF_JDK` at it. On macOS that is the folder ending in `Contents/Home`; elsewhere it is the folder that holds
   `bin/`.
4. Get this folder onto the machine: the `claude/browser-spike` branch, or a copy of `tools/spikes/browser` without
   `.jdk/` and `build/`.

Run `./spike.sh <options>` on macOS and Linux. On Windows, run
`powershell -ExecutionPolicy Bypass -File .\spike.ps1 <options>`. Each run compiles first. `--self-check` needs no
window and should print `SELF-CHECK PASSED`.

## The runs

Do the rows that apply to the machine. Rows 4 to 8 follow "What to do in a run" below.

| # | Command | What it answers |
|---|---|---|
| 1 | `./spike.sh --self-check` | The machine is set up |
| 2 | `SPIKE_RUN_JDK=<today's plain JBR> ./spike.sh --baseline`, three times; close each window after 3 s | Start-up on today's runtime |
| 3 | `./spike.sh --baseline`, three times | Start-up on the JCEF runtime when Chromium is unused |
| 4 | `./spike.sh` | In-process Chromium started after the window is up |
| 5 | `./spike.sh --sandbox` | Chromium's sandbox, in-process |
| 6 | `./spike.sh --mode=remote` | Out-of-process Chromium: dispose and restart, memory afterwards |
| 7 | `./spike.sh --mode=remote --sandbox` | The sandbox, out of process |
| 8 | `./spike.sh --popups=tab` | Popups opened as tabs instead of windows |
| 9 | Linux in a Wayland session: rows 4 and 6 with `SPIKE_JAVA_OPTS=-Dawt.toolkit.name=WLToolkit` | JBR's Wayland toolkit |
| 10 | Linux, only if row 4 crashed or showed nothing: `./spike.sh --eager` | Whether Chromium must start before AWT |

On Windows, set variables with `$env:SPIKE_RUN_JDK = '...'` before the command. Before row 2, set `SPIKE_RUN_JDK` to
the JBR Jasper builds with today, or any JBR 25 without JCEF.

## What to do in a run

1. Wait for the index page. Chromium starts by itself half a second after the window appears.
2. **Popup test:**
   - click **window.open popup**, then the **target=_blank** link, then the **noopener** link;
   - note what appeared each time (a tab, a separate window, or nothing).
3. **Input test:**
   - type in the fields, using your input method if you have one;
   - open the dropdown, and try the file field;
   - copy and paste;
   - press Cmd+Shift+P (Ctrl+Shift+P elsewhere) with the focus in the page;
   - hold Cmd+Option (Ctrl+Alt elsewhere) and press and drag inside the page;
   - scroll with the wheel or trackpad.
4. **The Swing overlays:**
   - click **Overlay** and type in it;
   - click **Dim**, then **Menu**;
   - hover over the page until the Swing tooltip shows.
5. **Find and zoom:** **Find page**, then **Find "jasper"**, **Zoom +** and **Zoom -**.
6. **DevTools** (rows 4, 5 and 8).
7. **Download**, then click the link.
8. **Cert (HTTPS)**, then answer **Yes** to continue.
9. **Real pages:** **github.com**, **chrome://version**, and **chrome://sandbox** on Windows and Linux.
10. **Animation**, then **Measure hidden**. It takes about 25 seconds; leave the window alone.
11. **Snapshot**.
12. **Close browser**, then **New tab**.
13. **Dispose Chromium**: wait for "Dispose finished" in the log.
14. **Restart Chromium**. Out of process (rows 6 and 7), a page should appear again; in-process, the log says it cannot
    restart.
15. **Checklist...**: answer every line, then click **Save to report**.
16. **Quit**.

If a run crashes, note when, and keep its report: it was written up to the crash.

## Sending results back

Put the files from `reports/` into `tools/spikes/browser/reports/` on the dev Mac's `claude/browser-spike` worktree,
together with any notes. `python3 summarize.py` there prints one table of every run.
````

- [ ] **Step 2: Run the self-check, the plain-JBR checks and the hygiene check once more**

```bash
tools/spikes/browser/spike.sh --self-check
PLAIN="$HOME/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48/Contents/Home"
SPIKE_RUN_JDK="$PLAIN" tools/spikes/browser/spike.sh --self-check
SPIKE_RUN_JDK="$PLAIN" SPIKE_JAVA_OPTS=-Dspike.reports=build/check-reports tools/spikes/browser/spike.sh; echo "exit $?"
python3 - tools/spikes/browser/src <<'PY'
import pathlib, sys
for p in pathlib.Path(sys.argv[1]).rglob("*.java"):
    text = p.read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, "bad chars:", bad)
print("HYGIENE DONE")
PY
```

Expected:
- `SELF-CHECK PASSED` twice, the second time with `jcef module present = false`;
- the message "This runtime has no jcef module. Point JCEF_JDK at a jbrsdk_jcef build (see README.md).", with no window, then `exit 3` (Review Focus 3);
- `HYGIENE DONE` with no "bad chars" line.

- [ ] **Step 3: Commit**

```bash
git add tools/spikes/browser/README.md
git commit -m "Browser spike: README with setup, run matrix and what to click

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

- [ ] **Step 4: Hand the runs to the user**

Tell the user:
- the spike is ready in `.claude/worktrees/browser-spike/tools/spikes/browser`;
- the dev Mac's `JCEF_JDK` is `.jdk/jbrsdk_jcef-25.0.4.1-osx-aarch64-b635.75/Contents/Home`;
- the README's table lists the runs.

Ask whether to push `claude/browser-spike` to `origin` so their Windows and Linux machines can fetch it. Pushing needs
their OK (AGENTS.md); copying the folder works too. Then stop until the reports arrive.

---

### Task 7: The user's runs

This task is the user's. Agents do not open the spike window.

- [ ] **Step 1: The user runs the README's matrix on each machine they have**

The aim is at least macOS aarch64, Windows x64, and Linux x64 under both X11 and Wayland. Windows aarch64, Linux
aarch64 and macOS x64 are welcome. The user puts every report file into
`.claude/worktrees/browser-spike/tools/spikes/browser/reports/` and says which machines and runs are missing.

- [ ] **Step 2: Commit the reports on the spike branch**

```bash
git add tools/spikes/browser/reports
git commit -m "Browser spike: reports from the user's runs

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: The findings, written back into the design

**Files:**
- Create: `tools/spikes/browser/summarize.py` (on `claude/browser-spike`)
- Create: `docs/superpowers/plans/2026-10-21-browser-1-spike-report.md` (on `claude/browser-plugin-design`)
- Modify: `docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md`, its status line and the sections each finding changes (on `claude/browser-plugin-design`)
- Modify: `docs/STATUS.md`, one new entry under "Current state" in that file's existing style (on `claude/browser-plugin-design`)

**Interfaces:**
- Consumes: the report files of Task 7, and the report keys listed in Tasks 2 to 5.
- Produces: the report, which plan 2's author reads first.

- [ ] **Step 1: Write `tools/spikes/browser/summarize.py`**

```python
#!/usr/bin/env python3
"""Prints a Markdown table of the spike reports: one column per run, one row per finding."""
import pathlib
import sys

KEYS = [
    "platform", "run.options", "runtime.vendor", "runtime.awt_toolkit_name", "cef.version",
    "startup.jvm_to_window_ms", "cef.init_ms", "cef.first_paint_ms", "cef.start_error",
    "cef.no_sandbox_setting", "cef.child_no_sandbox_flag", "proc.cef_first_paint.no_sandbox_flag",
    "proc.window_shown.rss_kb", "proc.cef_first_paint.helpers", "proc.cef_first_paint.rss_kb",
    "dispose.terminated", "proc.after_dispose_10s.helpers", "proc.after_dispose_10s.rss_kb",
    "restart.result", "restart.state_before", "restart.init_ms", "restart.first_paint_ms", "proc.restart_first_paint.helpers",
    "popup.requested", "popup.native_allowed", "page.popup.opener.window.open",
    "page.popup.opener.blank", "page.popup.opener.noopener", "page.popup.message",
    "keys.swing_binding_fired", "keys.swing_binding_fired_elsewhere", "page.keys.page_saw_palette_shortcut", "input.pane_drag_press_seen",
    "page.input.typed", "https.available", "cert.error", "cert.choice", "page.cert.loaded",
    "download.complete", "download.remote_note", "devtools.result", "devtools.component_added",
    "hidden.frame_rate_before", "hidden.visible", "hidden.hidden", "hidden.hidden_1fps", "hidden.visible_again",
    "scale.device_factor", "frame.window_closing_event", "browser.before_close", "load.error",
]


def read(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or " = " not in line:
            continue
        key, value = line.split(" = ", 1)
        values[key] = value  # a later line wins, as in the spike's own map
    return values


def main():
    folder = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "reports")
    runs = sorted(folder.glob("*.txt"))
    if not runs:
        sys.exit(f"No reports in {folder}")
    data = [(path.stem, read(path)) for path in runs]
    manual = sorted({key for _, values in data for key in values if key.startswith("manual.")})
    print("| finding | " + " | ".join(name for name, _ in data) + " |")
    print("|---" * (len(data) + 1) + "|")
    for key in KEYS + manual:
        cells = [values.get(key, "").replace("|", "/") for _, values in data]
        if any(cells):
            print(f"| {key} | " + " | ".join(cells) + " |")


main()
```

- [ ] **Step 2: Run it and commit it on the spike branch**

```bash
cd tools/spikes/browser && python3 -I summarize.py > build/summary.md && head -3 build/summary.md; cd ../../..
git add tools/spikes/browser/summarize.py
git commit -m "Browser spike: summary of the reports

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

Expected: a header row naming every run, then the separator row.

- [ ] **Step 3: Write the report on `claude/browser-plugin-design`**

In `/Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-plugin-design`, create
`docs/superpowers/plans/2026-10-21-browser-1-spike-report.md` with these sections, in this order:

1. **Title, date, and the runs.** A table of machine (platform id), runs done (README rows) and runs missing.
2. **"Known from the JCEF source"**: the ten facts at the top of this plan.
3. **One subsection per question.** Each gives the answer, the evidence (run names and report keys from
   `build/summary.md`, or the user's notes) and the consequence for the design. The questions:
   1. Off-screen rendering and Swing overlays: `manual.crisp`, `manual.overlay`, `manual.dim`, `manual.menu_tooltip`.
   2. Lazy start on Linux X11, and `--eager` if it was run.
   3. JBR's Wayland toolkit (the `-wayland` runs).
   4. Windows on ARM.
   5. Display scaling: `scale.device_factor`, `manual.scale_move`.
   6. Chromium's sandbox on each platform: `cef.child_no_sandbox_flag`, `proc.*.no_sandbox_flag`, `manual.sandbox_page`, and the `--sandbox` runs that crashed or worked.
   7. Out-of-process mode:
      - `restart.result`;
      - helper processes and memory after dispose (`proc.after_dispose_10s.*`) against `proc.window_shown.*`;
      - painting speed (`manual.real_site`).
   8. Start-up: the `startup.jvm_to_window_ms` medians of rows 2 and 3, and `cef.init_ms` and `cef.first_paint_ms`.
   9. Hidden pages: the `hidden.*` rates, and whether `setWindowlessFrameRate(1)` is enough.
   10. Popups and openers: `popup.*`, `page.popup.*`, `manual.popup`.
   11. Certificates: `cert.*`, `page.cert.loaded`.
   12. Downloads, DevTools, find, zoom, input, clipboard and file chooser: `download.complete`, `devtools.*`, and the matching `manual.*` items.
   13. Keyboard routing: `keys.swing_binding_fired` with `page.keys.page_saw_palette_shortcut`, and `input.pane_drag_press_seen`.
   14. Closing a browser: `frame.window_closing_event`, `browser.before_close`, `manual.close_dispose`.
   15. macOS signing inside `Jasper.app`: not tested here; moved to plan 2 (see the deviation above).
4. **"Decisions for the user"**. Each gate found, with a recommendation:
   - a platform where the sandbox cannot run (spec section 4 says this comes back before plan 2);
   - out-of-process restart failing, or memory not returning (spec section 9);
   - Wayland or Windows on ARM failing;
   - popups that cannot keep their opener.
5. **"Spec amendments made"**: a list of the section changes of Step 4.
6. **"Summary table"**: the contents of `build/summary.md`.

- [ ] **Step 4: Amend the spec where a finding changes it**

In `docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md`:
- **Status line:** add one sentence: "Amended on <date> with the spike's findings (`../plans/2026-10-21-browser-1-spike-report.md`)."
- **Each changed section:** make the smallest edit that makes it accurate. These are the likely ones:
  - section 4 (sandbox per platform);
  - section 5 (popups; hidden pages, using whatever throttling the measurements support);
  - section 8 (the screen-reader fallback becomes a decision, because fact 2 is certain);
  - section 9 (out-of-process mode for memory; start-up numbers);
  - section 12 (each risk becomes a result);
  - section 13 (plan 2's scope).
- **Gates the user has not decided yet:** mark the section "awaiting the user's decision: see the spike report".

- [ ] **Step 5: Add the `docs/STATUS.md` entry**

Read the top of `docs/STATUS.md` ("Current state") and add one entry in its existing style, covering:
- the browser plugin's spec, this plan and the spike report;
- branch `claude/browser-plugin-design`, and the throwaway branch `claude/browser-spike`;
- the gates waiting on the user;
- the next step, plan 2, after those gates.

- [ ] **Step 6: Commit on `claude/browser-plugin-design`**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-plugin-design
git add docs/superpowers/plans/2026-10-21-browser-1-spike-report.md docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md docs/STATUS.md
git commit -m "Browser plugin: the spike's findings, written into the design

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

- [ ] **Step 7: Bring the gates to the user**

Send the user, in one message:
- a link to the report;
- the gates of Step 3's "Decisions for the user", most important first, each with its recommendation;
- what plan 2 will contain once they decide.

Write plan 2 only after their answers.
