# Windows 11 workspace glass prototype

Status: **throwaway spike, native acceptance pending**. The user authorized a prototype on 2026-10-24 and will test
on Windows. This intentionally uses the brainstorming spike exception: no implementation plan, per-task commits or
subagents. Keep or replace the experiment only after seeing its results. Base: `5db3524b486b5f82781132b1d0e5e78c4dcb4eb7`.

## What this tests

An opt-in `WindowsGlass` backend uses the existing workspace opacity painting and `WindowGlass` lifecycle.
`-Pjasper.windows.glass=dwm` on `:jasper-app:run` extends DWM into the client area and selects Desktop Acrylic when
Background blur is On. `layered` instead asks the AWT peer for per-pixel translucency and deliberately has no blur.
Both require Windows build 22621 or later. The default process remains unchanged. The existing macOS backend is
unchanged. Settings exposes the Workspace rows during either Windows experiment.

DWM's acceptance of an attribute is **not proof that Swing presents usable alpha**. Black/opaque backgrounds,
washed-out text, missing blur, or damage during resizing are possible experimental results. The two modes isolate
DWM material composition from AWT's layered-window painting. DWM mode with blur Off also tests whether the normal
AWT surface can reveal the unblurred desktop. Layered mode with blur On logs that blur is unavailable.

Native calls use the existing JNA dependency, check HRESULTs and log failures. RtlGetVersion checks the actual Windows
build. Setup failure restores opaque workspace painting; a failed blur request is logged. The DWM path restores its
extended frame and saved title-bar dark preference when opacity is disabled. No new SDK API or native library ships.

## Run on Windows

Use a checkout containing the prototype commit, with JBR SDK 25 and the normal Jasper build prerequisites. The branch
is local and unpushed. To transfer it without pushing, copy `jasper-windows-glass-prototype.patch` to Windows, create a
branch in your Jasper checkout, then run `git apply --check <path-to-patch>` followed by `git apply <path-to-patch>`.
If the check reports conflicts, stop and use a checkout at the base above or transfer the committed branch instead.

From the repository root in PowerShell:

```powershell
.\gradlew.bat :jasper-app:run "-Pjasper.windows.glass=dwm" "-Pjasper.home=$PWD\build\windows-glass-home" --console=plain
```

This opens Jasper and starts its normal terminal session. The separate home keeps this experiment's settings and
workspace separate from the installed app. In **Settings > Appearance & Behavior > Appearance > Workspace**, turn
on **Enable opacity**, set **Opacity** to 70 and **Background blur** to On. The inherited setting descriptions still
say macOS; the Windows rows are exposed only by this prototype flag. Test 30, 70, 95 and 100 percent, blur On/Off,
and disabling Enable opacity. Text and controls should stay solid while the terminal and editor backgrounds change.

Quit the prototype completely before each mode change. Then run the comparison with the same home:

```powershell
.\gradlew.bat :jasper-app:run "-Pjasper.windows.glass=layered" "-Pjasper.home=$PWD\build\windows-glass-home" --console=plain
```

Set Background blur to Off for this comparison. As an opaque baseline, quit and run without the experiment flag:

```powershell
.\gradlew.bat :jasper-app:run "-Pjasper.home=$PWD\build\windows-glass-home" --console=plain
```

The Gradle property forwards the mode and the two required Java module-opening flags to the app only. For an installed
build made from this prototype, put these three lines in that build's `jasper.vmoptions` and fully restart it:

```text
-Djasper.windows.glass=dwm
--add-opens=java.desktop/java.awt=ALL-UNNAMED
--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED
```

Remove those lines to return to ordinary Windows behavior. Prefer the separate development home above for testing.

## Report back

For each mode, capture a screenshot with another window behind Jasper and record:

- Windows build (`winver`), display scale, and active Jasper look/theme.
- Whether desktop content shows through, whether it is blurred, and whether changing opacity changes the effect.
- Text/cursor/selection clarity; terminal, Editor and Markdown-preview backgrounds; title bar and controls.
- Resize, maximize/restore, minimize/restore, moving between displays and changing light/dark themes.
- A detached workspace window and closing/reopening windows; disabling opacity should restore a solid background.
- Windows Transparency effects On/Off and any difference between active and inactive windows.

Attach the relevant `Windows glass prototype` entries and exceptions from `build\windows-glass-home\logs`.
A successful native-call log with an opaque/black workspace is useful evidence that the normal Swing surface needs a
different composition path; it does not establish Acrylic support. Production handling of Windows setting changes,
accessibility policy, remote sessions and rendering performance remains for the design after this spike.

## Local verification

Headless compilation, focused glass/settings/workspace tests (66 tests, 0 failures, 0 errors, 0 skipped),
and `verifyApplicationArchitecture` passed on macOS. The full repository check was not run for this spike.
Windows visual behavior is unverified. No GUI or AOT training was launched by the agent.

API references: [DWM system backdrop](https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwm_systembackdrop_type)
and [DWM client-area alpha](https://learn.microsoft.com/en-us/windows/win32/dwm/customframe).
