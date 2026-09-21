# Application and Buddy refactor verification

## Baseline

Revision `e92eff5`, native execution on `codex/app-architecture-design`.
Fresh `./gradlew verifyTerminalArchitecture check --rerun-tasks`: BUILD SUCCESSFUL.

| Module | Tests | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| jasper-app | 728 | 0 | 0 | 1 |
| jasper-terminal | 355 | 0 | 0 | 1 |

The environment skips are the existing font/platform and unavailable fish-shell cases.
Existing javac/doclint warnings are nonfatal and will be reviewed with documentation.

## Task checkpoints

1. Baseline captured; all 114 production app types mapped by the approved plan.
2. Settings/subscription/builder tests failed on absent APIs, then passed. Full app suite passed (732 tests, one expected skip). Existing config/search behavior retained.

3. Theme subscription/title-bar APIs failed before extraction, then focused and full app tests passed (733 tests, one expected skip).

Task 3: Ruling: Keep pre-pack initial snapshot application in TerminalWindow, then register with ConfigurationController from the application — preserves geometry before pack while removing reverse ownership; wrong ordering would change initial sizing. WindowContent.installTitleBar is the workspace adapter exercised by existing headless title-bar tests. No trivial callback-record-only test; real wiring is covered by those integration tests.

4. Workspace activity tests failed on absent API; new lifecycle tests and full app suite pass (735 tests, one expected skip). Existing config/action coverage passes after owner extraction.

Task 4: Ruling: Reuse the pane’s existing immutable UUID as opaque activity identity instead of allocating a second Object — it cannot retain UI and already identifies that producer. Wire pane lifetime at creation, before shell readiness, to cover early close. Existing config integration tests already exercise unchanged overrides, changed fonts and captured next-session settings; retain those instead of duplicating them. Replace the proposed record-shape test with observable OPENED/CLOSED and unsubscribe tests.

5. Queued reused-step completion regression failed before the generation guard; all three invalidation paths now pass. Full app suite: 736 tests, one expected skip. Logger-capture test follows the new controller owner.

Task 5: Ruling: PaletteController creates its card and accepts explicit action-refresh/error/reopen callbacks; the plan’s injected card could not wire its final callbacks without a construction cycle. PaletteKeyRouter now consumes the controller plus an open callback, removing another reverse workspace dependency. Keep integration tests beside the workspace instead of duplicating fixtures in a controller-only test. Cost if wrong: keyboard/focus or failure-reporting regressions; existing integration suites cover these paths.

## Architecture

Existing terminal allowlist, internal-access and package DAG checks pass.
App/Buddy guards will be added at the package migration checkpoint.

## Packaged resources

Baseline application resource entries:

- `dev/jasper/app/buddy/jasper-buddy.png`
- `dev/jasper/app/icons/LICENSE.txt`
- `dev/jasper/app/icons/SOURCE.txt`
- `dev/jasper/app/icons/app/macos/icon-1024.png`
- `dev/jasper/app/icons/app/macos/icon-128.png`
- `dev/jasper/app/icons/app/macos/icon-16.png`
- `dev/jasper/app/icons/app/macos/icon-256.png`
- `dev/jasper/app/icons/app/macos/icon-32.png`
- `dev/jasper/app/icons/app/macos/icon-512.png`
- `dev/jasper/app/icons/app/macos/icon-64.png`
- `dev/jasper/app/icons/app/windows/icon-128.png`
- `dev/jasper/app/icons/app/windows/icon-16.png`
- `dev/jasper/app/icons/app/windows/icon-20.png`
- `dev/jasper/app/icons/app/windows/icon-24.png`
- `dev/jasper/app/icons/app/windows/icon-256.png`
- `dev/jasper/app/icons/app/windows/icon-30.png`
- `dev/jasper/app/icons/app/windows/icon-32.png`
- `dev/jasper/app/icons/app/windows/icon-36.png`
- `dev/jasper/app/icons/app/windows/icon-40.png`
- `dev/jasper/app/icons/app/windows/icon-48.png`
- `dev/jasper/app/icons/app/windows/icon-60.png`
- `dev/jasper/app/icons/app/windows/icon-64.png`
- `dev/jasper/app/icons/app/windows/icon-72.png`
- `dev/jasper/app/icons/app/windows/icon-80.png`
- `dev/jasper/app/icons/app/windows/icon-96.png`
- `dev/jasper/app/icons/app-window.svg`
- `dev/jasper/app/icons/bookmark.svg`
- `dev/jasper/app/icons/columns-2.svg`
- `dev/jasper/app/icons/command.svg`
- `dev/jasper/app/icons/history.svg`
- `dev/jasper/app/icons/maximize.svg`
- `dev/jasper/app/icons/refresh.svg`
- `dev/jasper/app/icons/search.svg`
- `dev/jasper/app/icons/settings.svg`
- `dev/jasper/app/icons/square-plus.svg`
- `dev/jasper/app/icons/title/SOURCE.txt`
- `dev/jasper/app/icons/title/plus.svg`
- `dev/jasper/app/icons/title/terminal-2.svg`
- `dev/jasper/app/icons/title/x.svg`
- `dev/jasper/app/shell-integration/bash/rc.bash`
- `dev/jasper/app/shell-integration/fish/fish/vendor_conf.d/jasper.fish`
- `dev/jasper/app/shell-integration/jasper.bash`
- `dev/jasper/app/shell-integration/jasper.fish`
- `dev/jasper/app/shell-integration/jasper.zsh`
- `dev/jasper/app/shell-integration/zsh/.zlogin`
- `dev/jasper/app/shell-integration/zsh/.zprofile`
- `dev/jasper/app/shell-integration/zsh/.zshenv`
- `dev/jasper/app/shell-integration/zsh/.zshrc`
- `dev/jasper/app/themes/FlatDarkLaf.properties`
- `dev/jasper/app/themes/FlatLaf.properties`
- `dev/jasper/app/themes/FlatLightLaf.properties`

Launcher: `dev.jasper.app.Main`. Benchmark/preview launchers are declared in
`jasper-app/build.gradle.kts`; packaging reads the application main class and
runtime classpath, without a hard-coded two-module jar list.

## Documentation

Approved design and plan are linked from `docs/STATUS.md`.
Historical unrelated STATUS links to vault/credential-manager/dark-purple design
artifacts were already absent in this checkout; no new links point to them.

## Independent review

Pending the complete implementation; one fresh review follows native execution.

## Manual acceptance

Desktop visual/keyboard/clipboard behavior, native performance measurements and
Windows runtime acceptance remain pending user execution. No GUI was launched.
