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

6. Coordinator/shutdown API tests failed before implementation; late arrival, pane-owned exit, once-only off-EDT termination and timeout tests pass. Full app suite: 740 tests, one expected skip.

Task 6: Ruling: Preserve the established pane-owned close sequence: coordinator.close stops admission but does not force-close already admitted sessions. The plan’s forced-close instruction broke terminationWaitsForAClosedShellToExit; the spec preserves existing shutdown behavior. Late arrivals still close immediately. Cost if wrong: unattached previously admitted sessions wait until bounded JVM termination, as before. The app has no accessible fake connector fixture; tests use an isolated child JVM through the supported terminal API, not a shell or GUI. exitFuture returns copies, so assertions follow completion rather than future identity.

7. Bootstrap/rollback tests and Error cleanup regression passed after RED. Full app suite: 746 tests, one expected skip. Main is a thin delegate; acquisition scopes preserve original failures and release endpoint ownership.

Task 7: Ruling: Use an EDT compose transaction with explicit creation/bind/presentation functions instead of unspecified constructor injection. It is the actual production path and exposes no test-only hook. Bootstrap transfers endpoint cleanup to application shutdown and a process hook; application now removes its stored Dock reopen listener. Cost if wrong: startup ordering or endpoint availability; real bind/rebind and failure-before-presentation tests cover the transaction.

### Task 8 — Buddy values

Moved immutable notice identity/options into the new JDK-only Buddy module. Value
tests failed on missing APIs before implementation. `./gradlew check` passed:
app 741 tests (one skip), Buddy 7, terminal 355 (one skip); no failures/errors.

### Task 9 — Buddy facade and presentation

Facade EDT/lifecycle tests failed on missing API, then passed. Full check passes
with 1,105 tests and two expected skips. Existing model, animation, geometry and
paint assertions moved with their owners; application tests use a separate test
fixture artifact. Native construction rollback is explicit; no GUI was opened.

Task 9: Ruling: Use a separate java-test-fixtures artifact exposing controlled companion snapshots to app tests instead of adding producer-only callbacks to production. The existing notifier tests assert replacement, acknowledgement and history as well as emissions. Cost if wrong: an extra test artifact dependency; no fixture is shipped in either production jar.
Task 9: Ruling: The existing native-window tests exercise pure geometry predicates; there is no headless native acquisition seam. Preserve those and animation/drag cancellation tests, add exhaustive EDT/headless facade tests, and inspect native timer/listener disposal without inventing a second window implementation. Native lifecycle acceptance remains user-run. Cost if wrong: a platform-specific cleanup defect may escape headless checks.
Task 9: Ruling: Move Buddy preview tasks to the Buddy module and replace app theme installers with explicit font/dark values. Cost if wrong: developer preview commands change module; documentation will name the new commands. Production appearance still comes from the application.

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
