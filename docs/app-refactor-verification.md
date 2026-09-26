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

Task 4: Ruling: Reuse the pane’s existing immutable UUID as opaque activity identity instead of allocating a second Object — it cannot retain UI and already identifies that producer. Wire pane lifetime at creation, before shell readiness, to cover early close. Existing config integration tests already exercise unchanged overrides, changed fonts and captured next-session settings; retain those instead of duplicating them. Replace the proposed record-shape test with observable OPENED/CLOSED and unsubscribe tests. Cost if wrong: producer identity or early-lifetime event coverage could diverge; tests cover creation and closure.

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

### Task 10 — Application Buddy policy

Missing owner/lifecycle APIs were RED, then focused tests and full `check` passed:
app 594, Buddy 160, terminal 355; 1,109 total, two expected skips, no failures/errors.
Saved callbacks are explicitly invoked after producer closure and notifier shutdown.
Availability, hide-preserves-model, listener removal and strict position writes pass.

Task 10: Ruling: Add an ownership predicate and a JDK activity-registration callback to BuddyIntegration rather than coupling it to TerminalWindow. The plan’s four-argument test seam omitted appearance and key-listener ownership inputs; the actual seam takes BuddyOptions, companion, visibility, show, ownership and registration. Cost if wrong: extra constructor inputs; production filtering and headless exactly-once removal use the same path. Position persistence is tested through the real options factory and drag callback, without adding public inspection methods.

### Task 11 — Final packages and artifacts

The new bytecode checker rejected the flat root before migration. Both full package
DAGs, Buddy JDK-only dependencies and API signatures now pass without exemptions.
`verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist`
passes: app 596, Buddy 162, terminal 355; 1,113 total, two expected skips, zero errors.
Actual jar resources decode and exclude test fixtures. Distribution includes all
three Jasper jars. The controlled child JVM now has a JDK-only nested entry point;
its moved public fixture method otherwise caused JVM method discovery to resolve
the terminal library before main. No desktop launcher was run.

Task 11: Ruling: Place integration tests with the workspace they drive and keep package-local test access fixtures beside private owners; share pure worker/config/layout fixtures under testsupport. This avoids exposing palette widgets, bootstrap hooks or native session test seams in production. Public configuration/value factories are cross-feature contracts, while ConfigurationController stays package-private. Cost if wrong: more small test support files to navigate; no test artifact enters production. FlatLaf defaults retain their resource path but name the relocated public SplitDividerBorder; icon loads use absolute resource paths. Both regressions were observed RED and pass after correction.

### Task 12 — Guides, executable recipes and clean verification

Guide/package tests were RED while files were absent. The fluent setting example
first failed on inaccessible builder methods, then all seven app recipes and the
Buddy embedding example passed. Strict Javadoc found malformed brace comments and
a relocated link; corrected without disabling doclint. A clean full run caught
an old terminal guide link, also corrected. Javadoc missing-tag warnings remain
nonfatal (existing app APIs plus supported Buddy members with prose contracts).

Fresh `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check
:jasper-app:installDist --rerun-tasks` passed with **1,124 tests: app 606 (one skip),
Buddy 163, terminal 355 (one skip), zero failures/errors**. All 23 tasks executed.
The three-module production source-character scan and `git diff --check` pass.
No GUI, native performance run, merge or push.

Task 12: Ruling: Expose ConfigSnapshot.Builder/toBuilder across app feature packages because the compiled setting recipe revealed inaccessible fluent methods; canonical validation remains unchanged. Update packaged benchmark scripts as well as Gradle entry points, detecting old versus new jar class names to preserve baseline images. Cost if wrong: public app collaboration surface grows and script entry selection could fail; copied examples and a fake-runtime old/new-image test cover both without opening a GUI. Windows script execution remains manual.

## Architecture

Existing terminal allowlist, internal-access and package DAG checks pass.
App/Buddy bytecode DAG, JDK-only Buddy dependency and supported-signature guards pass
without exemptions and are wired into check.

## Packaged resources

Baseline application resource entries:

- `dev/jasper/app/buddy/jasper-buddy.png`
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
- `dev/jasper/app/icons/intellij/LICENSE.txt`
- `dev/jasper/app/icons/intellij/NOTICE.txt`
- `dev/jasper/app/icons/intellij/SOURCE.md`
- `dev/jasper/app/icons/intellij/assets.tsv`
- `dev/jasper/app/icons/intellij/*.svg` (IntelliJ toolbar, tab and find-bar artwork, light and `_dark` variants)
- `dev/jasper/app/shell-integration/bash/rc.bash`
- `dev/jasper/app/shell-integration/fish/fish/vendor_conf.d/jasper.fish`
- `dev/jasper/app/shell-integration/jasper.bash`
- `dev/jasper/app/shell-integration/jasper.fish`
- `dev/jasper/app/shell-integration/jasper.zsh`
- `dev/jasper/app/shell-integration/zsh/.zlogin`
- `dev/jasper/app/shell-integration/zsh/.zprofile`
- `dev/jasper/app/shell-integration/zsh/.zshenv`
- `dev/jasper/app/shell-integration/zsh/.zshrc`
- `dev/jasper/app/themes/intellij/LICENSE.txt`
- `dev/jasper/app/themes/intellij/Light.theme.json`
- `dev/jasper/app/themes/intellij/darcula.theme.json`
- `dev/jasper/app/themes/intellij/intellijlaf.theme.json`
- `dev/jasper/app/themes/jasper-dark.theme.json`

Launcher: `dev.jasper.app.Main`. Benchmark/preview launchers are declared in
`jasper-app/build.gradle.kts`; packaging reads the application main class and
runtime classpath, without a hard-coded two-module jar list.

## Documentation

Approved design and plan are linked from `docs/STATUS.md`.
Historical unrelated STATUS links to vault/credential-manager/dark-purple design
artifacts were already absent in this checkout; no new links point to them.

## Independent review

One fresh independent whole-branch review of `c7b796b..1081047` found two
Important shutdown defects, no Critical findings, no deferred minors and no
items declined for judgment. Both were confirmed and fixed in one native pass:

- Endpoint close previously took a cross-process lock on EDT, before the exit
  timeout. Bootstrap now transfers endpoint ownership directly to the application;
  registered cleanup runs on daemon workers inside the bounded wait, including
  presentation-failure rollback. `contendedEndpointCannotBlockEdtOrBoundedTermination`
  failed for both ordinary quit and rollback with a real child-JVM-held lock,
  then passed. No window or login shell was opened.
- Accepted launches were absent from the shutdown snapshot until `track` received
  their sessions. The coordinator now accounts for accepted workers and late-child
  exits through a drain future. `shutdownWaitIncludesAcceptedLaunchAndLateChildExit`
  failed on premature termination, then passed using a controlled child JVM.
  Already admitted sessions retain their pane-owned close sequence.

Final fresh full verification after fixes:
`./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist --rerun-tasks`
— BUILD SUCCESSFUL, all 23 tasks executed. **1,127 tests: app 609 (one skip),
Buddy 163, terminal 355 (one skip); 1,125 passed, zero failures/errors.**
Production source hygiene and `git diff --check` pass. Distribution contains all
three Jasper jars and no test fixtures. Javadoc missing-tag warnings remain nonfatal.

The fresh-reader exercise started at the app README and located all three routes
without navigation gaps: command/shortcut owners and tests; saved/live font defaults
and preservation tests; Buddy posting/orphaning policy and lifecycle tests.
The reviewer assessed every execution ruling as reasonable; the Task 6 and Task 7
shutdown gaps above are now covered. Native desktop/Windows acceptance remains manual.

## Manual acceptance

Desktop visual/keyboard/clipboard behavior, native performance measurements and
Windows runtime acceptance remain pending user execution. No GUI was launched.

## Local integration — 2026-09-21

User-authorized fast-forward merge placed the completed refactor on local `main`
at `9b30dc2`. Full merged-result architecture/check/installDist verification passed:
1,127 tests, 1,125 passed, two expected skips, zero failures/errors. The completed
app-refactor worktree and branch were removed. No push was performed.
