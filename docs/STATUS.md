# Jasper — Status and Handoff

## Current state — 2026-09-21

Both architecture refactors are merged into local `main`: terminal through
`c7b796b`, application/Buddy through `9b30dc2`. The completed app-refactor branch
and worktree were removed. Those merge operations did not push to origin.

The three modules are `jasper-app` (product composition), `jasper-terminal`
(terminal library) and `jasper-buddy` (JDK-only companion library). Start at the
[documentation index](README.md) for each module's onboarding, architecture and
maintenance guides. Plugin SDK plan 1 (core and runtime) is implemented and merged into
local `main`, as is plan 2 (actions and chrome placements). Plan 3a (rail, panels and plugin
windows) is merged too; the spec's plan 3 was split into 3a and 3b. Plan 3b (Plugins manager,
install and restart) is merged too. The spec's plan 4 is split into 4a (terminal API: observe,
inject, open) and 4b (plugin-provided sessions); 4a and 4b are merged too. Working-directory provenance moved from 4b to a small plan 4c, which is
merged too. With it the plugin SDK plans are complete. The palette-plugins design
(`superpowers/specs/2026-09-21-jasper-palette-plugins-design.md`) is approved; plan 5a (the
contribution surface) is merged at `3eb143a`, plan 5b (the History and Snippets plugins) is
merged at `0550bad`. The plugin-home design (`superpowers/specs/2026-09-22-jasper-plugin-home-design.md`)
is merged at `17c0476`. The Credential Vault design
(`superpowers/specs/2026-09-22-jasper-vault-design.md`) has plan 6a (core and API) integrated into local `main` at `c8141b6`; plan 6b (UI)
is being continued in `.worktrees/vault-6b` on `claude/vault-6b`, then the SSH plugin spec. A development launch keeps its own home under
`jasper-app/build/dev-home` (`jasper.home`, merged at `8d5566e`).

### Remote plan 7a (SSH) — 2026-09-22

Implementation in the native worktree `/Users/dustin/.codex/worktrees/remote-7a/moray` on
`codex/remote-7a`, not merged or pushed. The [7a plan](superpowers/plans/2026-09-22-jasper-remote-plan-7a-ssh.md)
implements the approved [Remote design](superpowers/specs/2026-09-22-jasper-remote-design.md): saved hosts,
config import, host-key trust, Vault/agent authentication, shared sessions, ProxyJump, hosts panel,
editor, palette, actions and status. [Guide and native acceptance](remote.md). Tunnels (7b) and SFTP (7c) follow.

Native inline execution, one commit per task, followed by one independent review and a tested fix commit.
`./gradlew check :jasper-app:installDist` passed: **1,604 tests, 1,601 passed, three expected skips,
zero failures/errors**. All review findings are addressed; no deferred minors.
[Execution and review record](remote-7a-verification.md).
Plan corrections so far: current-main base preserves the shell revert; native branch/worktree and accurate
Codex attribution replace example Claude names. Fixed the store queue's completion type, strict trust
validation before host filtering, MINA connection-context target lookup and wrapped DNS errors.
Added lifecycle regressions and owned session references/cancellation for Vault, prompts, jump dependencies
and natural shell exit; callbacks return to the UI executor. Test fixtures use fresh credentials,
BC Ed25519 keys, short Unix socket paths, complete resize replies, correct fake activation and test-only UI
access bridges. These corrections preserve the intended behavior. The planned split/collapse/credential
ordering/fake-Vault deviations are recorded in the spec. GUI acceptance remains user-run.
The review fix pass composes queued host mutations, protects broken external edits, fixes optional
Vault loading, accepts multiple legitimate keys per trust file while rejecting conflicts/revocation,
bounds/cancels agent I/O, preserves OpenSSH first-value/quoted/commented imports, and reports
mutation failures after editor closure. SDK 0.7.2 adds `Panels.toggle` (app and testkit) for lazy
per-window Hosts action toggling; Remote requires it. Enter, star clicks and stale selection are fixed.

### Remote SSH card/dialog follow-up — 2026-09-22

User-approved adjustments on the same `codex/remote-7a` worktree: renameable default group
(persisted without rewriting hosts), larger searchable host cards with cached detected OS and IP,
active-session badges, double-click/Enter to focus the most recently used running pane, and a
Cancel/Retry progress dialog before tab/split creation (also used on reconnect). Explicit Connect
still creates another session. The host editor shows the renamed default group as a placeholder.

Metadata runs only over a user-established authenticated session, via bounded separate exec
channels. No proactive passwordless connections, no commands injected into the user's shell.
Unknown/unsupported OS discovery leaves the terminal usable. Results are cached against the
actual authenticated endpoint; direct peer IPs are shown when useful, never a jump forward's IP.

One independent review found accessibility and endpoint-cache association issues plus invisible
keyboard selection on group headers; all are addressed with regressions. Headless FlatLaf renders
at 360px and 240px sidebar widths and the connection dialog were inspected. Native acceptance
remains user-run. `./gradlew check :jasper-app:installDist` passed: **1,617 tests, 1,614 passed,
three expected skips, zero failures/errors**; Remote **69/69**. No merge or push.

### Remote host-list styling revision — 2026-09-22

At the user's request, replaced the rounded host cards with flat, indented tree-style rows.
Host labels are two points larger than the base list font, with eight points of vertical padding.
Search, favorites, group renaming, session counts and last-session activation remain. OS/IP details
are shown in the selected-host area and tooltips and remain searchable/accessibly named.
The separately requested connection overlay is still a design draft pending approval/Cancel choice.
`./gradlew check :jasper-app:installDist` passed: 1,617 tests, 1,614 passed, three expected skips,
zero failures/errors. Headless renders at 360px and 240px were inspected.

### Remote list density refinement — 2026-09-22

The user found the padded tree rows too loose. Reduced row padding from eight to three points
vertically and the font increase from two points to one. Group headers use the base bold font
with tighter spacing; an active-session dot (plus a count for multiple sessions) replaces the
wordy row status. Selected-host details now align flush left with their action controls.
Interaction, accessible status, search and connection behavior are unchanged.
`./gradlew check :jasper-app:installDist` passed: 1,617 tests, 1,614 passed, three expected skips,
zero failures/errors. Headless renders using Jasper’s actual dark theme at 360px and 240px were inspected.

### App-wide UI typography — 2026-09-23

User-approved `ui.font.family` / `ui.font.size` settings update chrome and plugin controls live,
independently of terminal `[font]`. Omitted settings preserve platform defaults; sizes accept 8–32.
Relative headings, palette/status labels, tabs and titles follow updates. Remote headings refresh
in existing panels and progress content grows to fit. Missing families fall back to the platform.
`./gradlew :jasper-app:test :jasper-plugin-remote:test` passed, followed by the complete check below.
Large and fractional sizes use exact point values; live palette rows and secondary sections resize
with the UI font, and host-star geometry follows its label metrics.

### Centered connection overlay — 2026-09-23

Completed the approved [overlay plan](superpowers/plans/2026-09-22-jasper-connection-overlay.md)
natively in this worktree. SDK 0.7.3 adds `Windows.overlay(OverlaySpec)` with app/testkit ownership
parity. Remote progress stays centered inside its owner with an explicit Cancel button, Retry/Close
on failure and no Escape/outside dismissal. Duplicate requests focus the existing attempt. Native
trust/Vault prompts stay usable, and owner/plugin closure cancels pending work. Bounds, scrolling,
focus restoration and keyboard/native-menu containment have headless regression coverage.

One independent review found five issues (native menu bypass, cached palette geometry, content
revalidation, border-induced scrollbars and scaled font offsets). All are fixed and re-reviewed;
no findings remain. Follow-up fractional-font verification also caught and fixed FlatLaf’s button
styling rounding. Native GUI acceptance remains user-run; app-themed headless renders at default
and 18-point UI size were inspected. No GUI was launched.

`./gradlew check :jasper-app:installDist` passed: **1,634 tests, 1,631 passed, three expected skips,
zero failures/errors**; Remote **70/70**. Distribution rebuilt with the updated app, SDK and plugins.
No merge or push.

### Overlay scrollbar correction — 2026-09-23

The user reported scrollbars in native connection progress and explicitly rejected any scrolling
fallback. Removed the host's JScrollPane completely: a plain bordered panel now sizes to its
content and lays it out directly. This supersedes the overlay plan's constrained-window scrolling
fallback. No SDK or Remote connection lifecycle changes.

`./gradlew :jasper-app:test --tests '*WindowOverlay*' :jasper-app:installDist` passed: six overlay
layout/focus tests, zero failures. The regression verifies no host scroll widgets even in a
constrained root, fitted content bounds, and natural relayout. An actual app-themed headless
ConnectionPanel probe confirmed that all visible labels/buttons fit at 12, 14, 18, 24 and 32 points
in progress and failure states. Default/18-point previews inspected. Distribution rebuilt; no native
GUI launched, merge or push.

### Remote Sessions toolbar — 2026-09-23

User-approved bounded follow-up, implemented natively in the same worktree. Remote contributes
**Sessions** through the existing SDK dropdown, listing saved hosts alphabetically by name with
their addresses. Selection focuses the last used running session or starts a connection in the
invoking window. Host additions, edits, imports and removals refresh the menu; invocation resolves
the current host record. **Manage Sessions...** shows the Hosts panel without hiding it if already
visible, and remains available with no hosts. Chrome and popup use the existing app typography.

Tests first failed on the missing toolbar/actions, then passed. The bundled-app integration test
now verifies both the sample button and the new Sessions dropdown. Independent read-only review
found no actionable issues. `./gradlew check :jasper-app:installDist` passed: **1,636 tests,
1,633 passed, three expected skips, zero failures/errors**; Remote **71/71**. Distribution rebuilt.
No GUI launched; native appearance remains user acceptance. No merge or push.

### Remote View menu entry — 2026-09-23

Registered the existing SSH Hosts toggle action under **View > SSH Hosts**, so it shows or hides
the panel in the invoking window. The existing **Cmd+Shift+H** (Ctrl+Shift+H elsewhere) binding
already opens the palette directly in the SSH scope; no additional shortcut or action is needed.
`./gradlew :jasper-plugin-remote:test --tests '*RemotePluginTest' :jasper-app:installDist` passed:
ten plugin integration tests, zero failures. Distribution rebuilt; no GUI launched, merge or push.

### Terminal resize and local startup follow-up — 2026-09-23

Reproduced prompt fragmentation headlessly with a clean zsh (`-dfi`, no user startup files): a
burst of immediate grid changes interleaves prompt redraws with later reflows. `TerminalView`
now coalesces layout resizes for 120 ms, then changes the emulator and shell together. Zero-area
layout no longer collapses the grid. Pending changes cancel on removal and refit on reattachment;
explicit session resizes and font changes retain their immediate behavior. View-driven clean-zsh
probes at burst and 10 ms resize intervals showed one intact prompt after narrowing/widening.

Three regressions failed before the fix and pass afterward. Independent review found no production
issues; strengthened its identified detach-cancellation test gap. `./gradlew check :jasper-app:installDist`
passed: **1,639 tests, 1,636 passed, three expected skips, zero failures/errors**. App rebuilt.
No native GUI launched, merge or push.

The local-only prompt startup delay was traced separately to a user shell startup block appending
Homebrew initialization on every launch. After explicit permission, backed up both user zsh files,
removed 191 duplicate profile commands and stopped further appending; other configuration is
preserved. Isolated profile evaluation measured 1.848 s before and 0.014 s after (not whole shell
startup). Both files pass zsh syntax checks. No user configuration is tracked in this repository.

### Remote configurable navigation shortcuts — 2026-09-23

Implemented the user-approved `[shortcuts]` table in `dev.jasper.remote.toml`: `toggle_panel`
defaults to `cmd+shift+s`, `open_palette` to `cmd+shift+h`. Missing values use defaults; blank
strings disable the plugin preference. Reloads replace only changed action registrations under
stable ids, refreshing shortcuts in existing windows while preserving menu/status placements.
App-level `[keybindings]` overrides retain priority. No SDK extension or plugin restart required.

Two real staged-plugin/TOML integration tests failed first, then passed: defaults, live changes,
disable/reset, unrelated-settings stability, menu preservation, override precedence, and invalid
or colliding bindings. Independent review confirmed action/window behavior and caught an
inaccurate logging promise, now removed: automatic plugin reload leaves invalid/colliding bindings
unbound but does not necessarily log the resolution problem until an app reload/start.

`./gradlew check :jasper-app:installDist` passed: **1,641 tests, 1,638 passed, three expected skips,
zero failures/errors**. Rebuilt distribution again after the settings-template comment correction.
No GUI launched, merge or push.

### Remote sidebar keyboard entry — 2026-09-23

The user clarified this request targets the SSH sidebar, not the scoped command palette. Showing
the sidebar now highlights the first visible host (skipping headings/errors) and requests list
focus after mounting, so arrows and Enter work immediately. A hide before focus delivery cancels
the request. Late host loading/filter results select the first available host; ordinary refresh
preserves the selected host or folder. With all folders collapsed, the first folder is selected
so Enter can expand it, without changing saved collapse state on opening.

Independent review caught folder selection jumping to an unrelated host after collapse; fixed
with a failing-then-passing collapse/expand regression. Selection-on-show, reopening, Down/Enter,
late loading and refresh/filter cases are covered too. `./gradlew :jasper-plugin-remote:test
:jasper-app:installDist` passed: **74 Remote tests, zero failures**. Distribution rebuilt; native
focus acceptance remains user-run. No GUI launched, merge or push.

### macOS default shell refresh reverted — 2026-09-22

At the user's request, reverted `dddfc57` and its integration note `238769f`:
per-launch macOS account lookup caused noticeable shell-start latency in native use.
Shell selection again uses inherited `SHELL`, with the existing platform fallbacks;
there is no per-launch `dscl` query. Shell integration and launch scheduling are
restored to their previous behavior. The account reports `/bin/zsh`, while the
running apps inherited `/opt/homebrew/bin/bash`; the user plans to refresh the login
environment by rebooting. No system settings changed and no GUI launched.

Verification: code and tests match `75dd8a3` exactly; only this status note differs.
`./gradlew check :jasper-app:installDist -q` passed on retry: 1,546 tests,
1,543 passed and three expected skips. The first run hit an unchanged test race in
`AttachedSessionTest.shellIntegrationWorksButNeverReportsALocalDirectory` at line 74:
streaming a synchronized list during concurrent writes throws
`ConcurrentModificationException`. No terminal code/test changes were made; that
intermittent test issue remains deferred. The revert is integrated into local `main`;
no push or replacement of the running installed application.

### Plugin home — 2026-09-22

Merged at `17c0476` (not pushed). The work implements the
[plugin-home plan](superpowers/plans/2026-09-22-jasper-plugin-home-plan.md) of the
[plugin-home design](superpowers/specs/2026-09-22-jasper-plugin-home-design.md): everything about a
plugin lives under `<home>/plugins/<id>/` (`jars/` for an installed plugin, `<id>.toml` settings,
`data/`), for bundled and development plugins too; `plugin-data/` is gone and migrated once, as are
flat `plugins/<id>/*.jar` layouts. A zip dropped into `plugins/` is staged at the next launch without
consent (an unusable one becomes `.rejected`); an update replaces `jars/` only; Remove… takes the
whole folder after a confirmation. Each plugin's settings file is the only source of its settings:
seeded from an old `[plugins."<id>"]` table (kept for that and reported as moved), else the jar's
`settings.toml` (History and the sample ship one), else a header; `PluginSettingsFiles` polls the
files once a second and a broken file keeps its last good values, reported under `plugins.<id>`.
The SDK's `PluginConfig` gained `file()` (0.7.0); the testkit mirrors the layout. The manager's
rows carry the three paths; its list has a context menu (the row's actions, then Open Settings, Open
Plugin Folder, Open Data Folder) whose openers go through `ConfigEditor`.

Scope decisions, as recorded at the top of the plan: `start(tables, dark)` and
`configurationChanged(tables)` keep their signatures (tables seed only); `plugins.toml` and
`plugins.lock` stay files beside `config.toml`; a drop-in zip records no consent; folders are
prepared in `PluginRuntime.start`; settings files are polled, not watched; a parse failure's report
is sticky until the next configuration snapshot (`ConfigurationController.report` keeps reports until
then); `ConfirmView` is its own small view; the fake's data layout is `<root>/<id>/data`.

Deviations from the plan text: `PluginDiscovery.scan` skips jar-less folders quietly (bundled and
development plugins' settings-and-data folders) rather than reporting them, an addition the plan's
migration test needed; `PluginSettings`' nested `View` also implements `file()`; the plugin-tables
tests and `PluginInstallerTest` were extended for the moved warning and the `jars/` path; the
manager test seeds `plugins.toml` as text because `PluginStateStore` is package-private. No
behaviour deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,433 tests: 1,431 passed, two expected environment skips, no failures or
errors** (app 766, Buddy 163, terminal 377, SDK 21, testkit 45, sample plugin 10, History plugin
40, Snippets plugin 11). Native acceptance (the five steps at the end of the plan) is pending and is
the user's.

### Credential Vault plan 6a — 2026-09-22

Integrated into local `main` at `c8141b6` (not pushed): the bundled `dev.jasper.vault` plugin's
core and API from the [vault design](superpowers/specs/2026-09-22-jasper-vault-design.md) per the
[6a plan](superpowers/plans/2026-09-22-jasper-vault-plan-6a-core-and-api.md). A device-bound
Argon2id/AES-256-GCM file with a binary plaintext that never becomes a `String`; the device secret in
the platform keychain tool with a 0600-file fallback; a lock manager with inactivity auto-lock; an
OpenSSH key generator; `VaultApi` published per consumer with once/always/deny grants stored in the
vault, one prompt per request kind, and cancellation that withdraws a request. UI so far: the create,
unlock, grant and picker dialogs, `Open Vault...` (F8) and `Lock Vault`. Plan 6b adds the manager
window, status item, rail action, key generator dialog and the Vault palette scope.

### Vault layout and TermLab theme — 2026-09-22

The manager lock action now uses an outlined padlock with a rounded body and centered
keyhole, matching the rail icon's visual language. Verified with a headless manager render,
the manager panel tests and `:jasper-app:installDist`.

Latest follow-up in `.worktrees/vault-6b`: the user's reference screenshots and local TermLab
source supersede the earlier split-pane manager and button-style iterations. The manager now has
one searchable, type-filtered list with two-line names/details and trailing type labels; compact
icons provide add/edit/delete/copy-public-key/lock. The + menu includes import and generation;
More includes grants and master-password changes. Double-click/Enter edits, palette navigation
reveals filtered-out entries, and lock clears the list and dialogs. Cancel/Okay close the manager;
individual editor saves remain immediate. Device storage source, auto-lock and file path stay in
the footer. Secret notes are displayed only in their editor, not a retained manager preview.

`TermLab/core/resources/themes/TermLabDark.theme.json`, `TermLabLight.theme.json` and its
`plugins/vault/.../VaultDialog.java` supplied the palette and row layout. Ordinary Swing buttons
use 24-pixel minimum height, 72-pixel minimum text-button width, 12-point labels, gray secondary
fills and blue default buttons. Editors align actions right and attach/release their default
button with the form lifetime. The terminal toolbar keeps its independent geometry. The dark
secondary button text is slightly lighter (#A7AEBB instead of #A0A7B4), preserving Jasper's existing
4.5:1 contrast guard; disabled text keeps its tested defaults. No JetBrains platform dependency,
SDK change, credential-type addition or vault format change. [Authoring guidance](plugin-authoring.md#consistent-buttons-flexible-layouts).

`./gradlew check :jasper-app:installDist -q` passed: **1,546 tests, 1,543 passed, three expected
skips, no failures/errors**. Independent review found two P2 issues (visible security status and
row accessible names), both fixed. Tests cover filtering, selection, palette reveal, menu actions,
lock cleanup, default-button lifetime and theme state. Headless dark/light manager, empty-state,
login and generator renders inspected; native appearance remains user acceptance. Not merged or pushed.

### Native file browsing — 2026-09-22

Follow-up to vault 6b in `.worktrees/vault-6b`: all editable key-path fields now have inline
**Browse...** buttons (SSH private/public files and login private keys). The native chooser is
owned by the editor dialog, starts from the current path, and preserves text on cancellation.
Closing or locking the vault while choosing discards any late result. Manual entry stays available.

SDK 0.7.1 adds `WindowSurface.chooseFile(title, initialPath)` for windows and dialogs; the vault
requires that version. The app owns native selection and disposal; the fake host queues responses.
The existing plugin ZIP picker shares the same native implementation. This is an additive extension
to the 6b plan; no vault format or consumer API change. `./gradlew check :jasper-app:installDist -q`
passed: **1,542 tests, 1,539 passed, three expected skips, no failures/errors**. Independent
review found only Windows test path assumptions, corrected before the final check. Headless
form rendering and source hygiene passed; native chooser acceptance remains user-run.

### Credential Vault plan 6b continuation — 2026-09-22

Implemented in `.worktrees/vault-6b` on `claude/vault-6b`, from `c8141b6`; not merged or pushed.
The resumed handoff held an uncommitted draft with only palette/status tasks. The completed
[6b plan](superpowers/plans/2026-09-22-jasper-vault-plan-6b-ui.md) now covers the palette,
self-clearing clipboard, padlock/rail, persisted manager operations, account/key/note editors,
master-password change, singleton manager, grant revocation and key generation with an optional
login account. [User guide and native acceptance](credential-vault.md).

**Resolved user decision:** the user chose passphrase encryption on 2026-09-22. The generator
now accepts and confirms an optional passphrase. Nonempty values encrypt every supported algorithm
in OpenSSH v1 format using bcrypt_pbkdf (24 rounds, fresh 16-byte salt) and AES-256-CTR; empty
fields retain the existing unencrypted output. An optional generated login stores its own
passphrase copy in the encrypted vault; standalone key records retain only paths/metadata.
Owned request, UTF-8, private-key encoding and derived-key arrays are cleared. No new dependency,
production process invocation, SDK/consumer API change or vault storage-format change.
Native acceptance and the opt-in real-keychain test remain user-run.

Execution: Task 1 used a subagent and independent reviewer, with one RED/GREEN fix round for
clipboard failure preventing shutdown locking. Tasks 2 onward ran inline; final whole-branch
review found two password-change issues; both are fixed in `a06dcb8` with RED/GREEN
regressions. Failed rekey writes retain the old key; queued saves use the resulting key. Closing
before commit cancels; after commit starts, the form says closing will not cancel and reports
write failures even after closure. Each task has its own commit and failing-before-passing tests. Baseline
`check` was 1,483 tests (1,480 passed, three expected skips). Verification on `a06dcb8`: `./gradlew check :jasper-app:installDist -q` passed with
**1,526 tests: 1,523 passed, three expected skips, no failures/errors**. Vault: 93 tests,
92 passed and the opt-in keychain test skipped. The installed vault jar contains the manager,
generator, scope and icons beside its BouncyCastle dependency. Source hygiene and diff checks pass.
Initial full checks caught eager clipboard access at plugin startup and old sample-only rail/status
assertions; a RED/GREEN default-start regression and updated bundled-plugin expectations cover them.

Deviations/rulings: both username and password copies expire per the spec; locked palette rows
remain enabled for Enter; the inherited branch/worktree was retained. A small 6a prerequisite
(`737f90c`) rejects/zeroes late create/unlock results after lock, including stop; prompt cancellation
also invalidates in-flight derivation. Wipeable Swing documents clear editable buffers and avoid
secret Strings in persistence; Swing's required Content.getString is the explicit UI boundary.
Generation failures remove created files. Headless dark-theme renders prompted GridBag forms,
readable kind labels and hiding the unused note area. No SDK, consumer API or vault storage-format change.
The independent review found no other actionable issues. Native behavior and a fresh audit of
unchanged 6a cryptography/platform storage remain outside this headless UI review; the two new
UI-reachable core failures were fixed. No deferred minor findings. The key-generation decision is implemented; native acceptance remains user-run; this branch and its execution ledger are retained.

Passphrase-extension verification: all five encrypted algorithms are independently readable by
`ssh-keygen -y` with the exact synthetic Unicode/whitespace passphrase and match their generated
public keys. BouncyCastle rejects absent/wrong passphrases. Tests also cover confirmation, UI/request
wiping, stored account ownership after lock/reopen, rejection, failed-save cleanup and lock-time
cleanup. Final `./gradlew check :jasper-app:installDist -q` passed with **1,534 tests: 1,531
passed, three expected skips, zero failures/errors** (vault: 101 total, 100 passed, one opt-in
keychain skip). The scoped independent review found no actionable issues. The generator was
rendered headlessly and inspected; source hygiene, diff checks and installed encrypted-encoder
packaging passed. The design decision is fully implemented; no merge/push or native launch.

### Vault native acceptance: shared chrome corrections — 2026-09-22

The user's native screenshots exposed three app-provider issues during vault acceptance. An
opaque unused tab panel covered auxiliary title bars outside the traffic-light region; title-only
mode now hides it. SDK dialogs already use `NativeShells`, but that path had skipped the app title
bar; frames and dialogs now share title setup, and JBR's Frame/Dialog overloads provide the same
custom chrome. Dialog ownership/modality, close guards, dynamic titles, activation, theme updates
and disposal remain app-owned. The SDK contract already supplies this surface; no API extension
was needed. This also fixes the Plugins manager and all other app-provided auxiliary surfaces.

The toolbar and rail now use `Jasper.titleBackground` in light and dark themes. Settings and Reload
config were removed from the toolbar; Settings remains in the rail, and both commands remain in
menus/palette. Regression tests reproduce the title-bar pixel boundary, verify both auxiliary
surface kinds, and cover theme colors, toolbar contents and plugin contributions. Full-check
failures identified two older screenshot/layout expectations, updated to the new color and five
buttons. Headless dark/light renders were inspected. Final `./gradlew check :jasper-app:installDist -q`
passed with **1,536 tests: 1,533 passed, three expected skips, no failures/errors**. The independent
review found no production-code regression; its two stale-test findings are resolved. Source
hygiene and diff checks passed. Native macOS rendering/dragging/modality remain a user-run visual
check; no GUI, merge or push was performed. Changes remain on `claude/vault-6b`.


Toolbar spacing follow-up: reduced the row from 53 to 42 logical pixels, leaving the 30-pixel
buttons and horizontal spacing intact. This gives 6 pixels of top/bottom padding and 11 more pixels
of terminal height. Updated existing geometry/pixel expectations; 15 relevant layout/chrome tests
and `:jasper-app:installDist` passed. No full-suite rerun for this isolated spacing change.


### macOS 26 window controls in the packaged app — 2026-09-22

The user's screenshots showed Jasper's stoplights in the pre-macOS-26 flat style while TermLab
(Tauri) had the new controls. Cause, proven by a spike with a plain Swing frame and no shell: AppKit
draws the macOS 26 controls only for a process whose main executable records a macOS 26 or later SDK
in its `LC_BUILD_VERSION`; JBR 25.0.4.1's `java` and jpackage's launcher stub both record SDK 13.3.
Rewriting that load command on a copy of `java` with `xcrun vtool -set-build-version macos 11.0 27.0
-replace` and re-signing gave the new controls with no other change. The jpackage launcher is
self-contained (links only Cocoa, libc++ and libSystem; parses `Jasper.cfg` itself), so no C stub is
needed. `packageApp` now runs that rewrite on `Jasper.app/Contents/MacOS/Jasper` after jpackage and
re-signs the bundle ad hoc with preserved metadata; `verifyPackage` asserts the launcher records the
installed SDK and warns below 26. Without `xcrun` both steps log and skip. `./gradlew :jasper-app:run`
keeps the legacy controls because it runs the stock `java`. See [packaging](packaging.md#macos-26-window-controls).
Branch `claude/termlab-tabs`, worktree `.worktrees/termlab-tabs`.

The wider design this came out of, TermLab-style tabs under native title bars on every platform
(`superpowers/specs/2026-09-22-jasper-termlab-tabs-native-titlebar-design.md`), is approved but
deferred: the user chose the launcher fix alone once it was clear the controls do not depend on the
tab rework. The spec's banner records the narrowing; no plan was written for sections 3 to 5.

### Terminal dark palette — 2026-09-22

`Palette.jasperDark()` now carries the TermLab Dark palette ported from the conch project's
xterm.js theme (Dracula-derived: background `#282a36`, foreground `#f8f8f2`, selection `#44475a`).
Only the terminal palette changed; the dark FlatLaf chrome keeps its own values, and
`TabbedPane.selectedBackground` in `FlatDarkLaf.properties` is pinned to the new terminal
background so selected tabs still match the terminal. Branch `claude/termlab-dark-palette`.

### Palette plugins plan 5b — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-22 at `0550bad` (not pushed). The work implements
[plan 5b](superpowers/plans/2026-09-21-jasper-palette-plugins-plan-5b-history-snippets-plugins.md):
the History and Snippets palette scopes are now two bundled plugins. `plugins/snippets`
(`dev.jasper.snippets`, bundling tomlj) carries the snippet value, `snippets.toml` format and store
and the Snippets scope, publishes `dev.jasper.snippets.api.SnippetService`, and moves a pre-plugin
`<home>/snippets.toml` into its data directory once. `plugins/history` (`dev.jasper.history`) carries
the five shell-history parsers, the polling index, discovery and the History scope; it captures live
commands from `COMMAND_FINISHED` with `PaneInfo.shell`, reads `trivial_commands` and
`deprioritize_trivial` from `[plugins."dev.jasper.history"]`, and shows "Save as snippet…" only when
the service is present (`requires … optional = true`, the Vault/SSH shape). The application lost
`palette.builtin.{ShellHistoryScope,SnippetsScope}`, the `snippets` and shell-history code,
`HistorySettings`, `ConfigSnapshot.historyEnabled/trivialCommands`, `ActionId.HISTORY_PALETTE/SNIPPETS_PALETTE`
and every wiring arm (3,473 lines); `CommandsScope` moved into `palette`, `CommandHistory` into
`commands`, `PaletteContext` lost `trivialCommands` and `PaletteTarget` lost `shellName`.
`ConfigLoader` reports `[palette.scopes.history]` as moved. See
[command palette](command-palette.md), [configuration](configuration.md#shell-history) and
[plugin authoring](plugin-authoring.md#palette-scopes).

Scope decisions, as recorded at the top of the plan:

1. The legacy snippets file is found relative to the plugin's data directory (the home is its
   grandparent), not passed through configuration.
2. The Snippets plugin bundles tomlj; `stagePlugins` copies each plugin's runtime classpath.
3. `ConfigSnapshot` loses `historyEnabled` and `trivialCommands`; the old table is reported as moved.
4. The two scope tests were rewritten against `FakePluginHost`; the rest moved with their code.
5. `CommandPalettePreview` lost its History and Snippets scenarios.
6. "Edit file" goes through `platform().openInEditor`.
7. `KeyBindings` keeps only `COMMAND_PALETTE` and `CLEAR_SCROLLBACK` special.
8. `PluginConfig.stringList` cannot tell absent from empty, so `deprioritize_trivial = false` replaces
   `trivial_commands = []`.

Deviations from the plan text: `FakePluginHost(Path dataRoot)` was added to the testkit (the plan
anticipated it); the View menu separator index moved with the two removed items;
`CommandHistoryFile` became public for one app test; `config.example.toml` gained the plugin's
table in place of `[palette.scopes.history]`; the plan's test for the Snippets error row needed a
TOML syntax error rather than a missing key, and its migration test uses a consumer plugin to reach
the service (a plugin cannot `find` its own publication). No behaviour deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,420 tests: 1,418 passed, two expected environment skips, no failures or
errors** (app 753, Buddy 163, terminal 377, SDK 21, testkit 45, sample plugin 10, History plugin
40, Snippets plugin 11). The installed image's `lib/plugins/` holds all three bundled plugins, the
Snippets one with tomlj and its antlr runtime. Native acceptance (the five steps at the end of the
plan) is pending and is the user's.

### Palette plugins plan 5a — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `3eb143a` (not pushed). The work implements
[plan 5a](superpowers/plans/2026-09-21-jasper-palette-plugins-plan-5a-contribution-surface.md) of
the [palette-plugins design](superpowers/specs/2026-09-21-jasper-palette-plugins-design.md): a plugin
can contribute a command-palette scope, open the palette, see a pane's shell label, show an error
notice and open a file in the user's editor. The SDK is 0.6.0: new package `dev.jasper.sdk.palette`
(`Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`,
`PaletteStep`, `PaletteQuery`), `ui.Notices`, `ui.Platform`, `PaneInfo.shell`, capability
`palette.contribute`, and `PluginContext.palette()/notices()/platform()`. The app's `Contributions`
model carries scopes and palette requests; `WindowContributions` registers every contributed scope in
its window and routes requests; `PaletteKeyRouter` treats a contributed scope's shortcut like a
built-in one while the palette is open; `plugins.HostedPalette` adapts a scope with contained calls
and hands the plugin its own rows back; `PaneSnapshot.shell` feeds `PaneInfo.shell`. The testkit
gained `FakePalette` and nine host accessors; the contract suite is 35 cases. The sample plugin
contributes a "Greetings" scope behind `demo_scope`. The application's own History and Snippets
scopes are untouched. See [SDK architecture](sdk-architecture.md#palette-scopes) and
[plugin authoring](plugin-authoring.md#palette-scopes).

Scope decisions, as recorded at the top of the plan:

1. `PaneInfo.shell` and `PaneSnapshot.shell` are last components, so existing constructor calls
   grew by one appended argument.
2. `PaletteTarget` carries the window and pane ids; a windowless target (fixtures only) yields no
   rows from a contributed scope.
3. A contributed scope's shortcut is intercepted only while the palette is open; closed, the plugin's
   own action handler runs and calls `Palette.open`.
4. The adapter keeps the plugin's row as the app row's token.
5. `Palette.open` on the showing scope dismisses and applies neither query nor row.
6. `Notices.error` goes to the last active window's `onError` (a dialog today), or the log;
   `Platform.openInEditor` runs the app's `ConfigEditor` on the plugin's executor and reports
   failure as a notice.
7. The contract's `addTerminalPane` keeps its signature; both harnesses create `zsh` panes.

Deviations from the plan text: three existing tests pinned the capability list
(`TerminalValuesTest`, `DescriptorParserTest`) or built a `PaneInfo` the plan's grep missed
(`FakeContractTest`); each expectation was extended. The SDK's one-line Javadocs were rewritten as
multi-line comments to satisfy doclint. No code deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,435 tests: 1,433 passed, two expected environment skips, no failures or
errors** (app 819, Buddy 163, terminal 377, SDK 21, testkit 45, sample plugin 10). Native acceptance
(the five steps at the end of the plan, driven by the sample plugin's `demo_scope`) is pending and is
the user's.

### Official Ember application icon — 2026-09-21

Implemented on `codex/ember-app-icon` in a dedicated worktree; local integration
was authorized by the user. No push was performed. The approved Ember pack (faceted white turtle,
warm gradient, no prompt) is the official icon for all platforms. macOS ICNS,
Windows ICO, all runtime PNGs, and Linux PNG/hicolor assets use that artwork.
Linux windows now select their own resource set and portable distributions carry
the hicolor hierarchy. Existing native installer support remains macOS/Windows.

The approved raster source and a reproducible Core Graphics generator replace the
old SVG app-icon pipeline. Buddy retains its silver character reference and sprites.
See [the icon guide](../packaging/icons/README.md) for provenance, sizes and generation.
Verification: `./gradlew check :jasper-app:installDist :jasper-app:packageDist`
passed after integrating main's development-home change with **1,437 tests: 1,435
passed, two expected skips, no failures or errors** (app 821, Buddy 163, terminal
377, SDK 21, testkit 45, sample plugin 10). The macOS
app/DMG passed native verification, including exact ICNS bytes in the bundle.
All runtime PNGs and ICNS/ICO containers match the approved Ember preview pack
byte-for-byte. All ten Linux hicolor sizes match their portable-distribution
copies. Windows and Linux native desktop checks remain unrun. No Jasper GUI or
benchmark was launched.

### Plugin SDK plan 4c — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `9fbdc49` (not pushed). The work implements
[plan 4c](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-4c-directory-provenance.md): a
directory reported under another host name is no longer treated as a local path.
`internal.shell.DirectoryProvenance` classifies every OSC 7 report; `ShellCommandTracker` keeps one
of local and remote and clears the other; `jasper-terminal` gained `session.RemoteDirectory` (the
only addition to the app-facing allowlist), `TerminalSession.remoteDirectory()`,
`TerminalSessionListener.remoteDirectoryChanged` and `SessionLaunchOptions.localHostNames`. The app
resolves the machine's names once, off the EDT, in `launch.LocalHostNames`, carries the remote
directory through `terminals.RemoteLocation`, `PaneSnapshot` and `TerminalEvent`, and shows
`host:path` in the status bar. SDK `PaneInfo.remoteDirectory`, `CWD_CHANGED` and `COMMAND_FINISHED`
now carry it; the SDK is 0.5.1; the testkit gained `remoteCwdChanged`. See
[terminal architecture](terminal-architecture.md#working-directory-provenance).

Scope decisions, as recorded at the top of the plan:

1. A report with no host, or with `localhost`, is local in a local session even when the machine's
   names could not be resolved; the spec's "unresolved names mean remote" applies to reports that
   name a host.
2. The local names are what `hostname` prints plus the `HOSTNAME` and `COMPUTERNAME` variables,
   which is what the bundled integration scripts report. `InetAddress.getLocalHost()` is not used.
3. `commandExecuted` keeps its signature; the app reads `session.remoteDirectory()` inside the
   callback on the reader thread.
4. An attached session now reports remote directories; plan 4b's suppression wrapper is gone.
5. The status bar shows `host:path` for a remote directory; tab titles keep falling back to the
   local directory.
6. SDK `RemoteDirectory` accepts an empty host (the program named none); the sample's range
   `>=0.5, <0.6` is unchanged.
7. A stronger locality signal, such as a per-session token from Jasper's own integration, stays
   deferred, as in the spec.

This changes local sessions: a local pane in which the user runs `ssh` by hand reports no local
directory while the remote shell reports its own, so New Tab and Split start where the local shell
was. The classification is best effort: OSC 7 is unauthenticated text.

Deviation from the plan text: Task 0's separate baseline `check` was skipped, because the branch
starts at the main commit verified minutes earlier. No code deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,415 tests: 1,413 passed, two expected environment skips, no failures or
errors** (app 810, Buddy 163, terminal 377, SDK 18, testkit 38, sample plugin 9). The contract
suite is 30 cases, run against both the testkit and the application. Native acceptance (the five
steps at the end of the plan) is pending and is the user's: step 5 matters most, because a machine
whose `hostname` differs from what its shell reports would see local directories classified as
remote.

### Plugin SDK plan 4b — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `5f21a0e` (not pushed); the merged
result passed the same verification. The work implements
[plan 4b](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-4b-plugin-sessions.md): a plugin opens
a pane whose session it provides. `jasper-terminal` gained `TerminalSession.attach(AttachedConnection,
GridSize, scrollback)`, the only addition to the app-facing allowlist, over a JDK-only
`internal.transport.AttachedTransport` (one writer thread behind a 4 MiB queue, whole-write
rejection reported through `TerminalSessionListener.inputDropped`, coalesced and ordered resizes, a
two-second drain after exit, first failure wins, `close` exactly once) and a thin
`internal.emulation.AttachedConnector`, so attached output runs through the same shell-integration
chain as a PTY. The app gained `terminals.{OpenSpec, SessionRequest, SessionAttempt}`, pending and
disconnected pane states with Reconnect, `plugins.CleanupWorker` and `HostedSessions`; the SDK
gained `SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy` and
`OpenRequest.session` behind `session.provide`, and is 0.5.0; the testkit gained `FakeSessions`;
the bundled sample provides a loopback echo session. The local PTY path is unchanged. See
[terminal architecture](terminal-architecture.md#attached-sessions) and
[SDK architecture](sdk-architecture.md#provided-sessions).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,403 tests: 1,401 passed, two expected environment skips, no failures or
errors** (app 806, Buddy 163, terminal 370, SDK 18, testkit 37, sample plugin 9). The contract
suite grew from 23 to 29 cases and passes for the testkit fake and for the application. The new
threading tests (`AttachedTransportTest`, `AttachedSessionTest`, `SessionPaneTest`,
`AppContractTest`) were each rerun three to five times without a failure. Native acceptance (the
eight-step checklist at the end of the plan) is pending: agents do not launch the GUI.

Scope decisions, recorded in the plan: working-directory provenance (classifying OSC 7 reports by
host, `RemoteDirectory`, the machine's local names in `SessionLaunchOptions`) moved to a small
plan 4c, and 4b implements the one rule it needs, that an attached session never reports a local
working directory; an attached session writes no exit line into the buffer; an unknown exit status
or a transport failure is an exceptionally completed `exitFuture()`; Reconnect starts a fresh
session and view in the same pane and does not carry scrollback over; cancelling closes the pane
only when no session was ever shown in it, and otherwise returns to the disconnected bar; the
attempt's grid is the window's configured initial grid; `SessionSpec.icon` is accepted and unused;
a user's split of a provided pane opens a local shell at home; and the SDK version is 0.5.0.

Deviations from the plan text: Task 0's separate baseline `check` was not run: the branch starts at the commit local `main` had just been verified at. `AttachedTransportTest`'s cut-off case accepts end of stream as well as an `IOException`: the fixture's `close` ends the pipe cleanly, and both are valid ways for the blocked read to end. `AttachedSessionTest` feeds shell-integration marks in the form the module's own tests use (BEL terminators, the command line ended by CR LF, a following prompt) instead of the ST-terminated sequence in the plan. In `TerminalPane` the `noticeTransient` field is declared before the timer whose initializer reads it; the plan's order was an illegal forward reference. `FakePluginHost.terminalPanes()` was added in Task 6 with the other drivers rather than in Task 8. `HostedTerminals` got a small `capability(OpenRequest)` helper for the audit line, and `plugins/package-info.java` lists `dev.jasper.terminal.session`. The terminal module's allowlist is documented in `jasper-terminal/README.md`'s package table; `docs/terminal-architecture.md` gained the "Attached sessions" section before "Threads and failures".

### Plugin SDK plan 4a — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `2eee9c7` (not pushed); the merged
result passed the same verification. The work implements
[plan 4a](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-4a-terminal-api.md): plugins find the
active window, tab and pane through `context.terminals()`, read pane metadata, follow sixteen
`TerminalEvents` topics, type and paste into a pane, read the selection, and open local tabs and
splits, each behind the capability the user consented to (`MissingCapabilityException` otherwise,
with an audit log that never contains content). New app package `dev.jasper.app.terminals`
(`TerminalRegistry`: pull-based window, tab and pane entries, id-only events, the derived active
pane); `workspace.WindowTerminals`; `plugins.CapabilityGate`, `HostedTerminals` and
`TerminalBridge`; the testkit's `FakeWorkspace` and `FakeTerminals`. The SDK is 0.4.0 and the
bundled sample declares `terminal.observe` and `terminal.inject`. `jasper-terminal` is untouched.
See [SDK architecture](sdk-architecture.md#terminal-api).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,359 tests: 1,357 passed, two expected environment skips, no failures or
errors** (app 787, Buddy 163, terminal 355, SDK 16, testkit 30, sample plugin 8). The contract
suite grew from 17 to 23 cases and passes for the testkit fake and for the application. Native
acceptance (the seven-step checklist at the end of the plan) is pending: agents do not launch the GUI.

Scope decisions, recorded in the plan: the spec's plan 4 is split into 4a (this) and 4b
(`TerminalSession.attach` with the app-owned writer, drain and exactly-once `close`,
`PendingSession`, pending and disconnected pane states, Reconnect, the cleanup worker,
`OpenRequest.session`, and working-directory provenance; the only part that touches
`jasper-terminal`); event payloads carry ids rather than handles, because a handle is bound to the
plugin that obtained it; `openTab` and `split` return `Optional<PaneHandle>`; `LocalSpec` carries
only a working directory; `terminal.observe` gates `info()`, `foregroundJob()`, `TabHandle.title()`
and every `jasper.terminal.*` topic, while identity, structure and visible navigation need no
capability; injection, selection reads, opens and a plugin's first terminal subscription are
audited at INFO, metadata queries are not; the 4b shapes (`remoteDirectory`, `SessionKind.PLUGIN`,
`CONNECTING`) are already in the SDK and unused; `foregroundJob()` completes on a worker thread;
and the SDK version is 0.4.0.

Deviations from the plan text: Task 0's separate baseline `check` was not run: the branch starts at the commit local `main` was fast-forwarded to, and that merged result had just passed the full verification. `MissingCapabilityException`'s two private fields carry Javadoc comments, because doclint warns about uncommented fields of a serializable class. `TerminalBridgeTest` builds `new Containment(() -> true)`; the plan assumed a no-argument constructor and said to follow `EventBusTest` otherwise. `AppContractTest` needed `import java.util.UUID`, which the plan's harness code assumed. The testkit's `openRequests()` also lists `front|<window id>` lines for `toFront()`, documented in its Javadoc; both contract harnesses filter them out, so the shared format is unchanged. Task 6's second harness block repeated what Task 4 had already put into `AppContractTest.newHarness()` and was not applied twice. Every compiled example in `docs/plugin-authoring.md` was re-copied from the sample source, not only the new one: `start()` gained the `demo_terminal` line, so the `plugin` example changed too.

### Plugin SDK plan 3b — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `4cf8b3a` (not pushed); the merged
result passed the same verification. The work implements
[plan 3b](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-3b-manager-install-restart.md):
the application-owned Plugins manager (File → Manage Plugins…, action `plugins.manage`) that lists
every plugin with state, reason, capabilities, requirements and error count; enables and disables
plugins; reviews capabilities and records consent; installs a plugin from a zip and removes one;
and restarts Jasper to apply the change, including safe mode's Restart Normally, which retires a
resident process first. New packages: `dev.jasper.app.pluginmanager` and the leaf
`dev.jasper.app.restart`; new runtime classes `PluginMaintenance`, `PluginInstaller`,
`PluginCatalog` and `PluginAdmin`; `PluginRuntime` gained the public records `Row`, `Snapshot`,
`Inspection` and `Outcome`. The SDK is unchanged at 0.3.0. See
[SDK architecture](sdk-architecture.md#plugins-manager-install-and-restart).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,326 tests: 1,324 passed, two expected environment skips, no failures or
errors** (app 768, Buddy 163, terminal 355, SDK 12, testkit 22, sample plugin 6).
`RestartCommandTest.theRunningJvmReportsItsOwnCommandLine` passes on the development Mac under
JBR 25: `ProcessHandle.info()` reports the process's own command and arguments, so Restart Now
can replay them. Native acceptance (the eleven-step checklist at the end of the plan) is pending:
agents do not launch the GUI, and no test spawns a Jasper process.

Scope decisions, recorded in the plan: the quit path has no running-session confirmation today,
so restart and retire call `quit()` as it is; installs are always staged in
`plugins/.pending/<id>/` and applied, like removals, by launch maintenance inside the state lock;
that maintenance runs on the main thread in the bootstrap, before the desktop starts; the manager
is app UI over `PluginRuntime`'s public records, and `PluginRuntime` stays the only public
top-level type of its package; `retire` is handoff protocol 1 with a sixth field, so an ordinary
open request is byte-identical and the stale-build upgrade path is untouched; waiting for the
resident means waiting until nothing answers on the socket, 30 seconds, cancelable; a replacement
spawned while process cleanup has not finished gets `--standalone`; a zip whose plugin needs
another SDK is rejected at install; an entry in `plugins.toml` means "reviewed", so an unreviewed
plugin offers only Review and Remove; zips hold jars at the root or in one folder, within fixed
limits; the manager does not open by itself in safe mode; `plugins.manage` is contributed by the
application before plugins start and has no default shortcut; and the sample plugin is unchanged.

Deviations from the plan text: Task 0's separate baseline `check` was not run: the branch started from the merged `main` that had just passed the full verification, with documentation-only changes on top. The new `LaunchRequestTest` case is `@DisabledOnOs(WINDOWS)`, as Task 5 allowed, because it asserts the exact encoding of a Unix path. In `docs/plugin-authoring.md` the obsolete passage that told users to consent by hand in `plugins.toml` was replaced by a pointer to File → Manage Plugins…, rather than kept above the new section. No production or test code differs from the plan text.

### Plugin SDK plan 3a — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `88fe4a0` (not pushed); the merged
result passed the same verification. The work implements
[plan 3a](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-3a-rail-panels-windows.md):
plugin panels in left, right and bottom regions toggled from a single left rail, rail action
buttons, and application-built plugin windows and dialogs, with panel placement, rail
visibility and window bounds remembered in `ui-state.toml`. The SDK is 0.3.0. New app packages
and types: `dev.jasper.app.windows` (`AuxiliarySurface`, `AuxiliaryWindows`, `NativeShells`),
`workspace.WorkspaceRegions` and `workspace.WindowRail`, `persistence.UiState`, and panels and
rail actions in the `contributions` model. See
[SDK architecture](sdk-architecture.md#panels-the-rail-and-plugin-windows).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,277 tests: 1,275 passed, two expected environment skips, no failures or
errors** (app 719, Buddy 163, terminal 355, SDK 12, testkit 22, sample plugin 6). The contract
suite grew from 14 to 17 cases and passes for the testkit fake and for the application.
Native acceptance (the checklist at the end of the plan) is pending: agents do not launch the GUI.

Scope decisions, recorded in the plan: the spec's plan 3 is split into 3a (this) and 3b (the
Plugins manager, zip install and consent, the restart banner, the retire request and "Restart
normally"); `WindowOwner` is an ordinary interface in `dev.jasper.sdk` because a sealed
interface cannot permit subtypes in other packages of the unnamed module; `onClosing` and
`onClosed` return a `Subscription`; regions have no header or close button; rail visibility
is app state in `ui-state.toml`, not a `config.toml` key; quit closes plugin windows without
consulting their closing guards, closing by hand consults them; `MacTitleBar` needed no new
mode; and the SDK version is 0.3.0 with the sample's range `>=0.3, <0.4`. The process now
stays alive while a plugin window is open, even with no terminal window and residency off.

Deviations from the plan text: `PanelAndWindowValuesTest` needed `import dev.jasper.sdk.WindowOwner`, which the plan's test omitted. `AuxiliaryWindowsTest`'s singleton case uses distinct ids for its singleton and non-singleton windows: a singleton request returns any open window with that id, so the plan's shared id made the expected count wrong. The headless split-pane fallback in Task 4 was not needed; exact pixel sizes pass headlessly. Test code reaches the headless `AuxiliaryWindows` through a new `AppContractTest.headlessWindows()` helper instead of repeating the construction, because `PluginRuntimeTest` has a parameter named `dev` that shadows the `dev.jasper` package in a qualified name. `JasperApplicationPluginsTest.bindingProblemsSeparateUnknownUserIdsFromDroppedPluginDefaults` now awaits termination after `quit`: shutdown writes `ui-state.toml` into the temporary home, which raced JUnit's deletion of that directory.

### Plugin SDK plan 2 — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 (not pushed); the merged result passed
the same verification. The work implements
[plan 2](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-2-actions-chrome.md): plugin
actions that are palette commands with rebindable shortcuts, and their placements in the
toolbar, menu bar, terminal context menu and status bar, all drawn by the application.
`KeyBindings` is keyed by action id; the new app-native `dev.jasper.app.contributions` model
keeps SDK types confined to `dev.jasper.app.plugins`; the SDK is 0.2.0. See
[SDK architecture](sdk-architecture.md#chrome-contributions).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist`
passed with **1,246 tests: 1,244 passed, two expected environment skips, no failures or
errors** (app 695, Buddy 163, terminal 355, SDK 10, testkit 18, sample plugin 5). The
contract suite grew from 9 to 14 cases and passes for the testkit fake and for the application.

Scope decisions, recorded in the plan: window and pane handles carry identity only until plan
4; the sample plugin's UI is off unless `demo_ui = true`; status items are global; a plugin's
dropped default shortcut is logged rather than shown as a configuration warning. Deviations
from the plan text: an invalid shortcut for a namespaced (plugin) action id is a per-entry
warning instead of an error that resets every keybinding, because the plugin may not even be
installed (a pre-existing loader test exposed this); empty contributed status rows collapse at
the origin and rows are clamped inside the bar, because the first layout broke the status
bar's bounded-layout contract at very narrow widths; and `:jasper-app:test` now takes only
`plugins/sample/src` as an input, fixing a plan-1 wiring bug in which the whole `plugins/`
tree, including the sample's build outputs, was declared and Gradle rejected builds that ran
both projects' tasks.

Known limit: keybinding diagnostics are evaluated when plugins start and at each configuration
reload; an action registered later is rebound in windows at once but not re-evaluated for
diagnostics until the next reload. Native acceptance is pending and user-run; the checklist is
at the end of the plan.

### Plugin SDK plan 1 — 2026-09-21

Merged into local `main` by fast-forward through `13b9646` on 2026-09-21 (not pushed); the
merged result passed the same verification. The work implements
[plan 1](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md) of the
[plugin SDK design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md): the JDK-only
`jasper-sdk`, `jasper-sdk-testkit` with a contract suite that both the fake and the
application pass, the `dev.jasper.app.plugins` runtime, standalone `--safe-mode`,
`--plugin-dir` and `--standalone` launches, an EDT-independent exit deadline, the activity
bridge to Buddy and a bundled sample plugin. Start at [SDK architecture](sdk-architecture.md).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist`
passed with **1,207 tests: 1,205 passed, two expected environment skips, no failures or
errors** (app 667, Buddy 163, terminal 355, SDK 8, testkit 11, sample plugin 3). Each new
architecture guard was shown to fail on a deliberate violation before being restored.

Deviations from the spec, all recorded in the plan: `PluginContext` carries only plan-1
accessors; the cleanup worker and `MissingCapabilityException` wait for plan 4; the runtime
is one package; activities reach Buddy without a threshold and do not drive its working
animation; the per-consumer service overload is `publishPerConsumer` (two `publish` overloads
are ambiguous for functional service interfaces). Deviations from the plan text: two tests
were corrected (`PluginTablesTest` expects the loader's quoted diagnostic keys;
`ConfigurationReportTest` builds `ConfigService` off the EDT, as production must), one racing
assertion in `PluginHostTest` now waits on a latch, and `installDist` also carries bundled
plugins in `lib/plugins`, which the plan had omitted.

Known flake, not caused by this branch: `TerminalAppIntegrationTest.rowResetRejectsACompletedSearchAlreadyQueuedAheadOfReconciliation`
(`"reflow"` case) fails about one full `check --rerun-tasks` in eight under parallel load, at
the same rate on the baseline code (`claude/plugin-sdk-design`, identical to `main` apart from
documents) as here; it passed six of six isolated reruns. Its final `findAsync` callback asserts
inside the callback, so a wrong count and a slow search both surface as the same five-second
latch timeout. `jasper-terminal` is untouched by this branch. Every other test was green in all
20 full runs made while investigating.

Open items: the sample plugin ships in the application image and should leave it when the
Vault plugin arrives. Until plan 3's Plugins manager, a user plugin can only be consented by
editing `plugins.toml`. Native acceptance is pending and user-run; the checklist is at the end
of the plan. Native packaging (`packageApp`/`verifyPackage`) was changed to bundle and verify
plugins but was not run.

The approved [terminal amendment](superpowers/specs/2026-09-20-jasper-terminal-refactor-design.md)
and [app/Buddy amendment](superpowers/specs/2026-09-20-jasper-app-buddy-refactor-design.md)
extend the original Phase 1 design. Their plans and verification reports preserve
execution decisions: [terminal](terminal-refactor-verification.md),
[app/Buddy](app-refactor-verification.md).

Merged-result verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist`
passed with **1,127 tests: 1,125 passed, two expected environment skips, no failures
or errors** (app 609, Buddy 163, terminal 355). Architecture guards, Javadoc doclint,
compiled guide examples and packaged-resource checks passed. Javadoc missing-tag
warnings are nonfatal; headless checks do not establish native acceptance.

Independent reviews are complete. The terminal queued-search/reset defect and
the app endpoint-lock/accepted-launch shutdown defects were fixed with regression
tests. The two terminal documentation follow-ups (option reconstruction sites and
plan completion markers) are addressed by the [documentation audit](documentation-audit.md). App review
left no deferred minors.

## Open acceptance work

- User-run desktop behavior: window placement, physical keyboard/clipboard,
  fonts/IME, Buddy native windows, shell exits and residency.
- Windows native packaging and desktop acceptance.
- Fresh native throughput/RSS measurements on the refactored code; the dated
  [readiness measurements](terminal-readiness.md) apply only to their recorded revisions.
- The two-week daily-use trial has not started.

Use the [packaging checklist](packaging.md#native-acceptance-checklist) and
[benchmark protocol](benchmarks.md). Agents must not launch native windows or
benchmarks without the user's applicable authorization.

## Historical implementation notes

The remainder is an archive of earlier handoffs, not a current task queue.
Branch names, paths, package layouts, test counts, approvals and “next steps”
below describe their dated checkpoints. They do not override the current state
above or the maintained guides. In particular, older custom-theme/Follow System
features and flat app/Buddy packages have since been replaced. Historical artifacts
absent from this checkout are identified as such rather than linked as available.

**Background residency (2026-09-17):** On `claude/background-daemon` (from main `1f1afeb`, with `main` `b56d098` since merged in), Jasper gains an
opt-in `[background] enabled` setting (default `false`) that keeps the process running with no
windows after the last one closes, so the next launch reveals a window from an already-warm JVM
instead of paying a full cold start — JVM, AWT toolkit, FlatLaf, font resolution, theme,
shell-integration extraction, shell-history discovery. **Quit still exits completely**; that is
the off switch. A resident Jasper holds no shell, no PTY and no child process — sessions have
already exited through the normal pane-close path before the last window goes.

*Roles and handoff.* Startup now resolves one of four roles. **Launcher** (default) attempts an
`AF_UNIX` handoff before any AWT/Swing initialization and exits 0 if a resident process accepts.
**Resident** (`--background`, the form written into the login item) runs a full warm-up pass —
install FlatLaf and realize the toolkit, resolve the font family and fallbacks, resolve the
theme, extract the shell-integration scripts, refresh the shell-history index — binds the socket,
and opens no window; if the bind fails, it logs a warning and exits instead of opening a window
at login, which the plan's pre-flight scan caught as a real conflict (the spec's "log and
continue as an ordinary app" is written for a foreground launch, not an unattended one). **App**
(today's path) additionally binds the socket and, with the setting on, stays resident when its
last window closes; a reopen listener (`AppReopenedListener`/`APP_EVENT_REOPENED`, confirmed
present on JBR 25) is registered unconditionally beside the existing quit handler, so a Dock
click on a running, windowless Jasper raises a window through the same `openOrRaise` path the
socket uses, with no new process created. **Standalone** (`--config <path>`) is today's path and
nothing else — see below. The endpoint is a `daemon` subdirectory of the per-user config root
(`AppDirs.daemonDir/daemonSocket/daemonToken/daemonLock`), bound under a `FileLock` that makes
stale-socket recovery (check, connect, unlink, bind) atomic across concurrent launches, gated by
an owner-only token, and carrying the launcher's own code-source path and modification time so a
resident process from an older build replies `refused stale`, unbinds, and exits only if it has
no windows open — an upgrade is never served a window built from stale code and never kills a
running terminal.

*Login item.* Enabling the setting also plans a per-platform login item through `LoginItem.plan`,
a pure resolver in the style of `AppDirs.resolve`/`LaunchSettings.resolve`: macOS gets
`~/Library/LaunchAgents/dev.jasper.background.plist` (`RunAtLoad` true, `KeepAlive` false, no
`launchctl` call), Windows gets a `Jasper` value under
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run` via `reg add`/`reg delete`. Only a packaged
install (detected through the `jpackage.app-path` system property) registers; a `./gradlew run`
session stays resident but logs a warning and skips the login item. The login item is reconciled
on every config load, immediately; residency itself is decided once at startup and is never
renegotiated while the process runs.

*A `--config` launch is fully standalone — a gap the review found in the spec, not just the
code.* The design originally said only that a `--config` launch never hands off, because the
resident process holds a different configuration; it did not say such a launch must not *bind*.
Since the endpoint's path comes from the real `AppDirs` rather than the override, `jasper
--config other.toml` with `background.enabled = true` inside `other.toml` would have bound the
shared endpoint, and a later plain `jasper` would have handed off to it and received a window
built from `other.toml` — exactly the invariant refusing the handoff exists to protect. Both the
spec and the code now make a `--config` launch fully standalone: no handoff, no bind, never
resident, login item untouched, regardless of what `other.toml` sets.

**Deviations from the plan.** Two of the plan's code blocks did not compile as written. Task 3's
wire-format code declared both `static LaunchRequest decode(String line)` and a
same-erasure `private static Path decode(String encoded)`, so the private helper is
`decodePath` instead. Task 4's `codeSource()` catch list read `URISyntaxException |
InvalidPathException | RuntimeException`, which javac rejects because `InvalidPathException` is a
`RuntimeException` subclass listed beside it; it was dropped from the catch list with no behavior
change, since `RuntimeException` already covers it. Several tests initially proved nothing:
mutation testing during Task 6's review found `openOrRaise`'s shutdown guard could be deleted
with the suite still green, because `newWindow`'s own guard covers every headlessly reachable
case, so the test was renamed to `openOrRaiseDoesNotThrowOnceTheApplicationHasQuit` with a
comment on exactly what it does and does not cover, and the raise-during-shutdown branch moved to
the manual checklist below instead; the warm-up test did not fail when `shellHistory.refresh()`
was removed until the implementer also stopped the shell-history index's poll timer that
`onChanged` starts; Task 4's first fix round separately found two tests that asserted a refusal
they reached for the wrong reason. The handoff's read deadline had to be split into two constants
after a regression test proved flaky: fixing a wedged accept thread (a silent client) first set
the server's read deadline equal to the launcher's own 2000ms reply budget, and a test
pitting a stalled peer against a concurrent handoff then raced those two clocks toward *different*
outcomes, passing 3 times in 10; `serve` now reads with its own 500ms `READ_TIMEOUT_MILLIS`
and the launcher's wait uses a separate 2000ms `HANDOFF_TIMEOUT_MILLIS`, and the regression
test is 10/10, confirmed to fail deterministically without the fix. The login item's live-reload
test needed its trigger fixed, not its assertion: `ConfigService.start()` queues its own initial
publish, so constructing `JasperApplication` and reconciling the login item in the same EDT turn
let that queued duplicate land as a second event on the freshly installed listener; the test now
spans two EDT turns with an intervening pump, matching the existing `ConfigurationControllerTest`
pattern, and no assertion changed.

Accepted without new coverage: `LoginItem.run()`'s 10-second timeout and `destroyForcibly` path
(the only commands it ever runs are `reg add`/`reg delete`, which do not hang in practice, and a
10-second test was judged not worth adding to a suite this size); `shutdown()` clears the quit
handler but never removes the constructor's reopen listener (inert, since the JVM is exiting
anyway, but asymmetric). Separately worth knowing before searching for it: `config.example.toml`'s
`[background]` table round-trips through
`ConfigTemplateTest.repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`, not
`ExpandedConfigTest`, which never reads the example file at all.

`main` gained a `[notifications]` config table and a substantially rewritten `JasperApplication`
(NativeNotifier, BuddyDeck, CommandNotifier, new listener wiring) after this branch started;
expect mechanical conflicts in `ConfigLoader.FIELDS`'s root set, `ConfigSnapshot`'s record
components, `config.example.toml`/`ConfigTemplate`'s table insertion, and `JasperApplication`'s
listener/constructor wiring at merge time. Not acted on here; merging is a separate step with the
user's say-so.

**Next step:** each of tasks 1–7 is individually reviewed clean (task 4 after three fix rounds,
tasks 3 and 5–7 after one each, tasks 1–2 with none needed), but the whole-branch review the plan
calls for after this task has not run yet. The plan asks it to pay particular attention to the
accept loop's error handling (the only non-daemon thread; a thrown error there would silently end
residency), whether anything in `shutdown()` is now reachable twice or not at all on the residency
path, and the stale-build handoff, which is the one path that deliberately closes the endpoint
from inside a request.

Fresh `./gradlew check --rerun-tasks`: jasper-app 568 tests, 0 failures, 1 skip
(`ShellIntegrationScriptTest.fishReWrapsAPromptDefinedAfterTheIntegrationLoaded`, fish not
installed on this machine); jasper-terminal 316 tests, 0 failures, 1 skip
(`FontSetTest.fallsBackWhenPrimaryCannotDisplay`) — 884 tests, 0 failures, 2 skips overall,
unchanged from the end of Task 7 since this task adds no code or tests. A second, plain
`./gradlew check` run after the documentation edits matched exactly. [Design
spec](superpowers/specs/2026-09-17-jasper-background-daemon-design.md),
[plan](superpowers/plans/2026-09-17-jasper-background-daemon.md), [configuration
guide](configuration.md#background-residency), [packaging
checklist](packaging.md#native-acceptance-checklist).

**Still user-run.** Nothing about this feature's actual purpose has been verified, and no GUI was
launched at any point during this work:

- Cold-versus-warm launch timing — the number this whole feature is judged on.
- The resident process's idle memory (`ps -o rss= -p <pid>`).
- Login start on macOS (LaunchAgent, log out/in) and on Windows (`Run` value, sign out/in), both
  from a real packaged install.
- `AF_UNIX` sockets on Windows at all.
- The macOS Dock icon persisting with zero windows, and a Dock click producing a window
  noticeably faster than a cold start.
- Quit really exiting completely and the next launch being cold.
- Survival across sleep/wake and a monitor change while resident.
- The plist/`Run` value being removed the moment `enabled = false` is saved, without restarting
  Jasper.
- A handoff request or a Dock click arriving while Jasper is quitting not raising a window that
  is being disposed — the `openOrRaise` guard is inspected and unit-tested for the reachable
  branch, but the disposing-window branch itself needs a live window and cannot be exercised
  headlessly.

No GUI, merge or push.

**Buddy drag performance and presentation coordination (2026-09-17):** The user
reported dropped frames and tearing while dragging. Pointer events previously
moved the buddy immediately, rebuilt/revalidated the notification column, queried
monitor geometry, and resized/showed its native window while an independent column
timer also moved/repainted it. Dragging now consumes the latest pointer position
on one coalescing 16ms EDT clock, moving buddy and column in the same callback. The
column's independent timer is suspended during dragging and resumes afterward;
release flushes the last position, and hide/dispose cancel pending moves. A paused
pointer still advances spring/shimmer frames. Geometry-only moves retain notice
motion and use the monitor snapshot captured at drag start; native size/location/
visibility setters run only when changed.

Static translucent capsule material (fill, outline and clipped outer shadow) is
cached in bounded 2x premultiplied images. Immutable deck snapshots are cached
until mutation, and settled column shimmer invalidates only the subtext region.
The actual 2x three-card headless benchmark improved from median **0.983ms / p95
1.251ms / p99 1.454ms** to **0.731ms / 0.896ms / 0.981ms**. Before/after RGBA renders
differed in only 12 pixels, by at most one channel value. This measures Java2D
paint CPU cost, not native dragging FPS or WindowServer presentation. Separate
native windows still cannot promise atomic/vsynced presentation; desktop tearing
acceptance remains user-run, with no unverified claim that it is eliminated.

`./gradlew check`: **1,004 tests, 1,002 passed, two existing skips, no failures/errors**
(app 682/681/1; terminal 322/321/1). New drag-clock tests cover pointer bursts,
paused-pointer animation, final-position flushing and cancellation. Existing buddy
geometry, material, title/state, hit-testing and motion regressions pass. Source
hygiene and diff checks pass. Reproduce CPU measurements with
`:jasper-app:buddyPerformanceMeasurement` (headless; no native window or shell).
No GUI, commit or push.


**Buddy shimmer and fluid screen placement (2026-09-17):** The additional
14:35:58 recording was extracted into all 482 original frames with ffmpeg, with
ffprobe timestamps. On the same notification worktree, RUNNING subtext now has a
soft left-to-right glyph highlight, shared by column and drawer, with steady
completed/waiting/orphaned text. Running surfaces request 16ms frames; timers stop
when hidden/disposed and return to a one-second cadence for other live details.

Dragging follows the buddy directly until its anchor-relative placement changes.
The column switches sides around the screen midpoint with an eight-point dead
band and an approximately reference-fitted damped spring (8.6/s decay, 8 rad/s,
800ms settle). Reversals preserve position and velocity; internal stack direction
also interpolates. Every intermediate window position is clamped to the usable
monitor, including offset/negative-origin screens. Initial showing snaps placement
without altering the separate capsule arrival animation.

`./gradlew check`: **1,002 tests, 1,000 passed, two existing skips, no failures or
errors** (app 680/679/1; terminal 322/321/1). Eight new tests cover highlight
movement, steady completed text, repaint state, continuous/reversed placement,
midpoint hysteresis and screen bounds. Actual headless light/dark drag/shimmer
previews are in `jasper-app/build/reports/buddy-motion/{dark,light}.mp4`, generated
by `:jasper-app:buddyMotionPreview` plus ffmpeg. Native desktop acceptance remains
user-run under AGENTS.md. No GUI, commit or push. The shimmer's band/timing is
visual tuning, not a claimed exact measurement from the subtle reference shader.
[Measurements and reproduction](design/buddy-notification-reference.md#running-subtext-and-dragging-follow-up-2026-09-17).


**iTerm-style tabs and title correction (2026-09-17):** Continuing on
`claude/command-notifications` in `.claude/worktrees/practical-shamir-1252c5`.
The user's three screenshots supersede the fixed 160-point tabs, duplicate
right-hand window title and sliding underline. A single session now hides the
strip and uses a centered window title; multiple tabs stretch across the macOS
title bar with centered system-font text, thin dividers, hover close buttons,
actual shortcut labels and a fixed right-edge add button. The 38-point default,
native stoplights/gestures, tab ordering and overflow navigation remain.

Automatic tab/native titles now combine the OSC title (or directory) with the
foreground job: `~ (-zsh)`, `~ (sleep)`, `Reviewing files (tmux)`. A small Unix PTY
metadata query reads the foreground process group, then Java process metadata,
every 500ms off the EDT. It needs no shell integration or helper subprocess and
stops polling on exit/disposal. Unsupported process metadata falls back to the
configured program. Manual names remain overrides. Buddy titles remain the
program's text without the job suffix, with the captured command as fallback;
finished-notification ordering and the matched bubble motion remain unchanged.
The existing system-font resolver is shared as `SystemFonts`.

The title wrapper was also present in iTerm: tmux's default `set-titles-string`
added the session/window number and quoted pane title. With explicit user
permission, `~/.tmux.conf` now forwards custom pane titles and shortens its default
hostname/user@hostname:path shell title to `~` or the directory basename. The
running server received only this option; no plugin/config reload. Original
saved as `~/.tmux.conf.jasper-backup-20260917-145844`. The running shell evaluates
to `~`, while custom task titles stay intact.

Validation: `./gradlew check` passes **994 tests: 992 passed, two existing skips,
zero failures/errors** (app 672/671/1, terminal 322/321/1). Real PTY tests cover
foreground job changes, background jobs, login shells, real tmux forwarding,
OSC 0/1/2, manual naming and completed-notice snapshots. Headless title-bar
renders cover single/multiple/narrow layouts in both themes; source hygiene and
diff checks pass. One pre-existing dangling Javadoc warning remains in
`LaunchSettingsTest`. Native desktop acceptance remains user-run under AGENTS.md;
no GUI, commit or push. [Reference and reproduction](design/iterm-title-bar-reference.md).



**Program titles in tabs and buddy notices (2026-09-17):** Continuing in
`.claude/worktrees/practical-shamir-1252c5` on `claude/command-notifications`.
The user requested using the title set by the command/application for the tab
and bubble. OSC 0/1/2 reception and automatic tab/window titles already existed;
the missing connection was from title events to notices, and tabs had no command
fallback when no OSC title was supplied. The running local tmux server was also
observed with `set-titles off`, which prevents its pane titles reaching Jasper.
Optional session-scoped forwarding settings are now documented; the user's tmux
configuration is unchanged.

`TerminalPane` keeps ordered EDT snapshots of title/start/end events. Reading
`session.title()` later would race with a subsequent prompt title, so a real PTY
test holds Swing until the full title/finish/prompt burst has been parsed and
checks the completed notice still names the command's last task. Automatic tabs,
the native window and live bubbles use the program title, then running-command
fallback; idle automatic tabs still fall back to the directory. Manual tab names
remain explicit overrides. Finished notices/native notifications retain the last
title from that execution.

A title update changes a notice in place, preserving order, acknowledgement and
arrival state, and cannot resurrect a dismissed notice. Full titles are retained;
renderers fit them to available width, with code-point-safe binary truncation for
the capsule instead of repeatedly shortening a potentially long title one
character at a time. No terminal-module API or shell script changes were needed.

Validation: eight new tests cover OSC 0/1/2 through the actual PTY/parser/tab/window/
bubble path, command fallback and title clearing, preserved manual names,
completion ordering, per-pane identity, pre-threshold titles, dismissal and long
Unicode titles. `./gradlew check`: app 674 (673 passed, one fish skip), terminal
320 (319 passed, one existing font skip): **994 total, 992 passed, two skipped,
zero failures/errors**. Source hygiene and diff checks pass. Native GUI remains
user-run. [Title behavior and tmux settings](configuration.md#automatic-tab-and-notification-titles).

**Buddy notification reference correction (2026-09-17):** On
`claude/command-notifications` in `.claude/worktrees/practical-shamir-1252c5`, the
user supplied light/dark recordings and asked to replace the notification styling
and animation. ffmpeg extracted every original frame and ffprobe supplied its
variable-rate timestamp. The replacement uses 330 × 56 logical-pixel capsules,
13-point macOS system text, measured light/dark fills, partial transparency and a
cached soft outer shadow. Thought-tail dots, card overlap and scale/hover growth
are removed. The capsule descends 32 logical pixels with a fitted 600ms spring;
the actual headless render tracks the recorded arrival with 0.95 physical-pixel
RMS error and a maximum two-pixel difference.

Motion is keyed by notice identity: status updates stay in place and interrupted
stack moves preserve the current position. Live detail text ticks once a second
after motion settles; theme changes repaint visible capsules. Drawer dismissal
now refreshes the column, and input ignores transparent rounded corners. Existing
notice routing, three-visible limit and drawer history remain. The reference's
stop-task control has no corresponding Jasper cancellation API; existing open
and dismiss actions are retained rather than adding a misleading button.

`./gradlew check` passes: app 666 tests (665 passed, one fish skip), terminal 320
(319 passed, one existing font skip): **986 total, 984 passed, two skipped, zero
failures/errors**. `:jasper-app:buddyNotificationPreview` renders actual components
without a window or shell. Light/dark frames, the visual comparison and a
reference-left/Jasper-right motion MP4 are in
`jasper-app/build/reports/buddy-notifications/`. Source hygiene and diff checks
pass. Native desktop acceptance remains user-run, especially transparency over
a textured background: a flat video does not uniquely identify source alpha or
native blur. [Measurements, evidence limits and reproduction](design/buddy-notification-reference.md).
The thought-column spec and plan banners record this user-requested deviation.

**Finished-command notifications (2026-09-16):** On
`claude/command-notifications` (from main `1f1afeb`), a command that ran past a
threshold and finished in a tab you were not looking at now tells you, through
the desk buddy — who also gains the status bubble that was built and deferred.

*Duration.* `commandExecuted` carried no elapsed time, so `TerminalSession` now
stamps a monotonic clock at the command-start mark and subtracts at the end; the
clock is injectable so tests drive it without sleeping. The first cut used
`commandStartedAt == 0` as a "never started" sentinel — the same mistake as
history ranking treating timestamp 0 as "older than everything" — and the test
clock, which legitimately starts at zero, caught it. No sentinel is needed.

*The rule.* `CommandNotice.shouldNotify` is pure over three booleans the app
already tracks, so all eight combinations are tested without a window. It
notifies for another tab of the focused window, and for anything while Jasper is
in the background. Deliberately quiet: the tab you are looking at, and a command
in a different Jasper window while another Jasper window has focus.

*Sprites.* Three frames appended — `TYPE_A`, `TYPE_B`, `TYPE_REST` — so existing
column indices are untouched; the strip is 840x48. The plan said to check the
`.ase` master had not been hand-edited, because `generate.py` warns that
rerunning it destroys hand edits. It had four commits rather than the expected
one, so the check earned its place: `generate.py` changed in all four and
rerunning it reproduces the committed PNG byte for byte. The script has always
been the source of truth and the README now says so. The first laptop was drawn
large and centred and read as a buddy behind a monitor, with shell, belly and
glasses all hidden; rendering at 8x showed it at once, and it is now compact and
sits in his lap.

*Animation and routing.* `BuddyAnimator` gains a `WORKING` mode that returns
before the tuck and sleep deadlines, so a working buddy never falls asleep —
pinned by ninety seconds of ticks.

*Two surfaces.* Live notices rise in a **thought column above his head**;
everything that happened lives in a **drawer** he opens on a single click.
Position is a claim about lifetime, and a permanent stack of finished cards
beside him was a record pretending to be a status display.

`BuddyDeck` holds `BuddyNotice`s keyed by `(source, key)` and knows nothing
about panes: the terminal is one producer and passes the pane as its key,
which is what makes "one card per tab/pane/window" true today without the
deck learning why. `Kind` splits **task** (begins and ends) from
**connection** (up until it is not), because a tunnel never completes and a
model built only around completion could not express one; `State.fits(Kind)`
makes an impossible notice unconstructible. `detail` is a `Supplier<String>`
so a running card ticks without being re-posted and a future transfer can
report bytes through the same field.

A bubble appears the moment its pane stops being watched — `focusLost` covers
another pane, another tab, another window and Jasper itself going to the
background — with `long_command_seconds` as the fallback for a command
running in the pane you are still watching.

The surfaces run their own repaint timers. They previously had none: the
buddy's timer repaints only his sprite canvas, so the column painted a
single frame per event at elapsed zero — scale 0.6, and opacity 0 because it
rode the same spring curve — and the bubble was invisible until an unrelated
repaint happened. Opacity now fades over the first 40% of the arrival rather
than riding the spring, and the timers stop as soon as `animating()` goes
false, so a settled column costs nothing. Bubbles overlap by six pixels so
the column reads as a stack, and the ones above a new arrival spring up over
its place and overshoot once before settling.

Column membership is `live() || !acknowledged`. Acknowledgement is deck
state, not notice state, because it is a fact about the reader. Focusing a
pane acknowledges it; a command finishing in the focused pane is
acknowledged on the spot and never appears; closing a pane acknowledges as
well as orphans, since its pane can never be focused again. `live()`
excludes orphans — a pane closed mid-command is left `RUNNING` but nothing
is still happening, and without that it sat above his head all session.

`BuddyCard` paints one card for both surfaces. `BuddyDeckLayout` and
`BubbleMotion` stay pure, so the column offsets, the flip, the scroll clamp,
the hit tests and the bounce curve are all tested headlessly. Rendering the
column under the sprite is what showed the tail was invisible at 7px on a
dark desktop and trailed off his centre line; no assertion would have.

`NEEDS_INPUT` is defined and rendered but set by nothing yet: OSC 133 `A`
means "at a prompt, ready for input", so for the shell it is the same event
as a finished command. Catching a blocked **sub-process** (`sudo`, `[y/N]`)
needs the pty's foreground process group and is its own work. Connection
producers, and grouping healthy connections into one "2 tunnels up" bubble,
are deliberately deferred until something posts one rather than shipping
untested machinery for a caller that does not exist.

`TerminalSession.Listener.commandStarted` is new. Nothing previously reached
the app at the C mark, so `CommandNotifier.passedThreshold` had no
production caller at all and the typing animation could never have run.

Notification widened from the tab to the pane: only the pane you were typing
in is quiet. `CommandNotifier.Channel` was deleted rather than given a second
implementation. `NativeNotifier` passes the command line to `osascript` as
argv rather than inside an interpolated AppleScript string, which a test
asserts with a command containing quotes and a newline.

*tmux.* The scripts wrap every OSC in tmux's passthrough sequence when `$TMUX`
is set, and turn on `allow-passthrough` for their own pane. Measured against tmux
3.5a: without the wrapper **none** of the marks reach the outer terminal, so the
whole feature was silently dead in a pane while history — which is read from the
shell's history file, not the marks — kept working and hid it. With the wrapper
all four marks, the OSC 1341 command payload and OSC 7 arrive. The option is set
pane-scoped so a user's global tmux configuration is untouched.

*Setting.* `notifications.long_command_seconds`, default 10, live, 0 disables.
**It needs shell integration**: the duration comes from the OSC 133 marks, so a
shell without them produces no notifications and no cards at all, which the
configuration guide states plainly rather than guessing a duration.

Fresh `./gradlew check --rerun-tasks`: jasper-app 554 tests,
553 passed and one fish skip;
jasper-terminal 318 tests,
317 passed and one existing
font skip. Total 872 tests, 870 passed, 2 skipped,
zero failures/errors. [Configuration
guide](configuration.md#finished-command-notifications), [design
spec](superpowers/specs/2026-09-16-jasper-finished-command-notifications-design.md),
[plan](superpowers/plans/2026-09-16-jasper-command-notifications.md). Still
user-run: a long command in a background tab on the real desktop, watching the
typing animation and the bubble, and the same with the buddy disabled. No GUI,
merge or push.

**History ranking, freshness and tmux (2026-09-16):** On
`claude/history-scope-ranking` (from main `18fd513`), three confirmed defects in
the shipped History scope and one preference, each measured against the dev
machine before anything was changed.

*Ranking.* `ShellHistorySnapshot` sorted on `timestamp`, which is `0` when
unknown, so `0` meant both "unknown" and "older than everything". bash writes no
timestamps unless `HISTTIMEFORMAT` is set — measured here: 10,004 timestamped
zsh entries against 500 untimestamped bash ones, and `HISTTIMEFORMAT` set
nowhere — so no bash command could ever rank however recently it ran. An entry
without a timestamp now ranks just before its source file was last written,
stepping back a second per entry from the end. `build` takes
`Source(entries, modified)` instead of a bare list.

*Freshness.* The index had exactly two refresh triggers: the first window, and
the History scope being activated. A one-second Swing timer now calls the
existing `refresh()`, started on the first listener and stopped on the last, so
a window with no palette pays nothing. `refresh()` already coalesces and
`readSource` already returns on unchanged size and mtime, so a quiet tick is one
stat per source — pinned by `aQuietTickCostsNoRead`.

*tmux.* `inject` matched only `zsh`, `bash` and `fish`, so a user whose
configured shell is tmux got no integration at all. Measured: a pane of a tmux
server Jasper started *does* inherit `ZDOTDIR` and `JASPER_SHELL_INTEGRATION`,
but tmux replaces `TERM_PROGRAM` with `tmux` and every script guarded on that.
Jasper now exports `JASPER_TERMINAL=1`, which tmux leaves alone, and `inject`
gains a tmux arm applying the environment mechanism for the basename of
`$SHELL`. Driving a real tmux 3.5a then exposed a second problem, introduced by
the previous feature: tmux runs its `default-command` as `$SHELL -c ...`, so
under `set -g default-command ${SHELL}` — which this machine's `~/.tmux.conf`
sets — the interactive shell is the child of a non-interactive one, and the
`.zshenv` fix that stopped a non-interactive zsh leaking `ZDOTDIR` stranded it.
`JASPER_INTEGRATION_LOADED` separates the two cases. Verified against real tmux
with that configuration: the integration loads and `__jasper_precmd` is defined.

*Trivial de-ranking.* `palette.scopes.history.trivial_commands` (live) partitions rather than
scores: the listed commands keep their order among themselves and follow
everything else, and are still listed and searchable. It defaults to `exit`,
`clear`, `ls`, `ll`, `la`, `cd`, `pwd`, `c`, `q` and `logout`. Only a short
command whose first word is listed counts, so `cd deep/path && ./gradlew build`
is real work and `clearcache` is not `clear`. Setting the key replaces the
default list; an empty list turns de-ranking off. It shipped first as a boolean
over a built-in set and became a list on request — the ranking code did not
change, only what it is handed. Entries are single words (one containing
whitespace could never match and is rejected), lower-cased and de-duplicated.
Both History-scope settings then moved from a top-level `[history]` table to
`[palette.scopes.history]`, so a future scope adds a table beside it rather than
another top-level one; `palette.max_results` stays put because it applies to
every scope rather than one. The old `[history]` table is not accepted as an
alias — it is reported as unknown — which is safe because no config in use set
it.

Deviations from the plan: the flag rides on `PaletteContext` beside
`maxResults`, not on a config snapshot the scope never receives, so the
partition lives in `ShellHistoryScope` and `ShellHistorySnapshot` stays pure
merged data; `ShellIntegrationEndToEndTest`-style prompt-row coverage was not
extended, since `promptRows()` is package-private in `jasper-terminal`. Three
existing tests asserted orders that only held under the old sentinel or the old
ranking and were updated, each with a comment saying why: two index fixtures
stamped zsh entries at epoch 100–500 while the bash file's mtime was the wall
clock, and one scope test pinned a row position that `ls` being de-ranked moves.

**Known gap:** bash inside tmux still gets nothing, because bash's mechanism is
a `--rcfile` argument tmux never passes; documented with the manual `source`
line. Attaching to a tmux server started before Jasper gets nothing either.

Fresh `./gradlew check --rerun-tasks`: jasper-app 523 tests, 522 passed and one
fish skip; jasper-terminal 316 tests, 315 passed and one existing font skip.
Total 839 tests, 837 passed, two skipped, zero failures/errors; after merging
`main`, which had brought in the palette input-row anchor and its three tests,
842 tests, 840 passed, two skipped, zero failures. [Configuration
guide](configuration.md#shell-history), [palette
guide](command-palette.md#shell-history), [design
spec](superpowers/specs/2026-09-16-jasper-history-ranking-design.md),
[plan](superpowers/plans/2026-09-16-jasper-history-ranking.md). Still user-run:
the History palette inside tmux on the real desktop, confirming a just-run
command appears within about a second and that bash entries interleave sensibly.
Merged to `main` and pushed. No GUI.

**Palette input-row anchor (2026-09-16):** On `claude/command-palette-y-axis`
(from `18fd513`), the palette card is anchored by its **top** edge, one-fifth of
the way down the terminal area, instead of by its vertical center one-third
down. The old anchor subtracted half the card height from the top, so every
change in result count — and therefore every scope, since Commands, History and
Snippets return different numbers of rows — put the text box at a different
height; the user reported the jump from screenshots. Only the small-window
clearance clamp may still move the top edge, and only when the card would not
otherwise fit. One-fifth keeps the default Commands card within a few pixels of
its 2026-09-13 position. `WindowCommandPalette.positioned` carries the change;
three tests cover it (a pure geometry check across four card heights, the
clamped small-window case, and a real scope switch plus two row-count changes
asserting the card's `y` never moves while its height does) and all three fail
against the old formula. `./gradlew check`: 821 tests, 0 failures, 2 pre-existing
skips (`FontSetTest.fallsBackWhenPrimaryCannotDisplay`, and the fish script test
that needs fish installed). The binding spec, the scopes spec, the palette plan
and `docs/design/command-palette/README.md` record the new anchor, and the
24-image render matrix plus the 2× UI-scale artifact were regenerated headlessly
with `commandPalettePreview`. Native GUI confirmation remains user-run.

**Shell integration scripts (2026-09-16):** On `claude/shell-integration`
(from main `27999fe`), Jasper ships and auto-loads its own zsh, bash and fish
integration scripts instead of relying on the user's own shell configuration.
`ShellIntegrationScripts.install` extracts `jasper.zsh`/`jasper.bash`/
`jasper.fish` plus their per-shell wrapper files (zsh `ZDOTDIR` wrappers,
`bash/rc.bash`, the fish vendor snippet) to `shell-integration` under the
application directory, writing only files whose content changed so upgrades
replace stale copies in place. Each script emits OSC 7 on directory change,
OSC 133 A/B/C/D with the exit status in D, and the exact command line just
before C on Jasper's own OSC 1341 channel; it guards on an interactive shell,
`TERM_PROGRAM=Jasper` and an unset `JASPER_INTEGRATION_LOADED`, so a nested
shell gets no marks unless it sources the script itself.
`terminal.shell_integration` (`"auto"`/`"manual"`/`"off"`, default `"auto"`,
new panes only) controls whether `LaunchSettings` injects the script for a
launch whose resolved program is exactly `zsh`, `bash` or `fish` (zsh via
`ZDOTDIR`, bash via `--rcfile` with `-l`/`--login` emulated through
`JASPER_LOGIN_SHELL`, fish via `XDG_DATA_DIRS`) or only exports
`JASPER_SHELL_INTEGRATION`; `TERM_PROGRAM=Jasper` is always set and a
`[terminal.env]` override wins. `TerminalSession` learns the `cmd` payload
(base64-decoded, preferred over the screen read) and a
`shellIntegrationDetected()` flag that goes true at the first A mark; the
status bar shows a filled dot after the shell name once detected and a hollow
one otherwise, with matching tooltips. `Main` extracts the scripts and passes
the resulting directory into `JasperApplication` and `ShellLauncher` before the
first window opens.

**Whole-branch review and its fixes (2026-09-16):** Three reviewers covered the
scripts, the launch/extraction Java and the receiving side plus tests and docs.
No Critical findings; everything below was fixed on the branch under
[plan](superpowers/plans/2026-09-16-jasper-shell-integration-review-fixes.md),
each with a test that fails without the fix.

*Scripts.* fish wrapped the prompt once at load with no marker check, and fish
sources `vendor_conf.d` before `config.fish`, so starship, Tide or any
hand-written `fish_prompt` replaced the wrapper for the session and the B mark
was lost — the wrap now happens in the `fish_prompt` event with a marker check,
like zsh and bash, and the invented `prompt_pwd` fallback is gone. `set -u`
printed "unbound variable" on every new pane; a readonly `PS1` or
`PROMPT_COMMAND` printed an error every prompt and aborted the hook; a command
containing the substring `__jasper_` was dropped entirely; `printf`, `base64`
and `tr` were callable as user functions. `rc.bash` sourced both
`/etc/bash.bashrc` and `/etc/bashrc`; `.zshenv` left `ZDOTDIR` pointing at the
wrapper directory for a top-level non-interactive zsh.

*Launch and extraction.* `JASPER_*` is now scrubbed from the inherited
environment — the scripts export `JASPER_INTEGRATION_LOADED` and return early on
it, so a Jasper launched from an integrated pane (including `gradlew run`, the
way this branch gets tested by hand) had integration silently off in every
pane. bash injection is skipped when the user passed `--norc`, `--rcfile`,
`--init-file` or any form of `-c`, because bash would ignore Jasper's rc file
while `-l` had already been stripped, leaving a login shell with neither login
emulation nor an rc file; `-l` is recognised in a short cluster and left alone
past `--`; `--noprofile` no longer sets `JASPER_LOGIN_SHELL`; `XDG_DATA_DIRS`
no longer accumulates. Extraction writes through a staged file and an atomic
move, never through a symlink, owner-only.

*Receiving side.* A repeated A flushed the cycle with no status, so fish 4 —
the one shell with no automated coverage — lost the marker for every command.
The `cmd` text survived D, RIS and the alternate-screen switch. The payload had
no bound while `ShellHistoryParser` caps disk lines at 16 KiB. Invalid UTF-8
was substituted to U+FFFD and reported as the command. Detection now notifies
listeners: the A mark draws nothing, so the status dot previously updated only
when the following prompt happened to trigger a repaint — it worked by luck,
and the new `ConfiguredTerminalBehaviorTest` case fails without the fix. This
one was not in any reviewer's report; writing the wiring test found it.

*Behaviour change to review.* `HISTCONTROL=ignorespace` (or `ignoreboth`) is now
honoured: a space-hidden command sends neither the `cmd` payload nor C, so it
reaches neither shell history nor Jasper's. The reviewer raised this as a
spec-level privacy question rather than a bug, and the previous behaviour was
pinned by a passing test; that test is now inverted. Revert the `HISTCONTROL`
case in `jasper.bash` if the original behaviour was intended.

*Deliberately not done.* The status bar now draws a running/stopped dot and an
integration dot side by side ("● zsh ○"), and `Segment.doLayout` clips the
integration dot first at narrow widths. Both are visual judgements that cannot
be verified headlessly and would churn the design render fixtures on a guess —
left for the user's desktop pass.

Deviations from the plan text: extraction runs on the **EDT** in `Main` before
the first window is created (a few small file writes, measured at ~9 ms cold),
not on a configuration worker as the spec proposed — the earlier STATUS wording
said "main thread", which was wrong. The script line budget is now per-shell:
`jasper.bash` is 109 lines against zsh's 57 and fish's 55, because only bash
needs the `DEBUG`-trap plumbing, both `PROMPT_COMMAND` forms, the readonly
guards and the `HISTCONTROL` check. Known limitations, all disclosed in the
[configuration guide](configuration.md#shell-integration): a bash `DEBUG` trap
the user installs after the first prompt displaces Jasper's; the chained user
trap sees `$?`/`$_`/`BASH_COMMAND` from Jasper's wrapper; `jasper.fish` is still
untested on the dev Mac because fish is not installed, though it now has a test
that reports as a skip rather than being absent. [Configuration
guide](configuration.md#shell-integration), [design
spec](superpowers/specs/2026-09-16-jasper-shell-integration-design.md),
[plan](superpowers/plans/2026-09-16-jasper-shell-integration.md). A **second whole-branch review** then found three regressions these fixes had
introduced, all since fixed with their own tests: honouring `HISTCONTROL`
suppressed repeated commands under `ignoreboth` (Ubuntu's stock value) and every
command when history was disabled, and tightening the `A` arm moved
`commandStartRow = -1` and the payload clear behind the row check, reopening the
stale-capture leak the same round had closed — only the flush belongs there. It
also found the five wrapper files had never been made unset-safe (round one
fixed only the three main scripts, so under `set -u` Jasper silently skipped the
user's own `~/.bashrc`/`~/.zshrc`), two bare `printf` calls left in the bash
DEBUG trap, `-o`/`-O` option arguments ending the bash option scan early, and a
staged extraction path that could itself be a symlink.

A direct check of the `HISTCONTROL` matrix against real bash afterwards caught
a hole none of the tests had: a space-hidden command typed as the **first**
command of a session leaves history empty, which the "history is switched off"
carve-out then read as permission to report it — the exact case of opening a
terminal and typing ` export TOKEN=…`. Suppression now keys on whether history
is enabled (`[[ -o history ]]` and `HISTSIZE`), not on whether it has entries.
The eight-row matrix (hidden/visible × ignorespace/ignoreboth/ignoredups/none ×
history on/off) is verified against `/bin/bash`, and the new test fails if the
condition is reverted. Fresh `./gradlew check --rerun-tasks` on the branch: jasper-app
503 tests, 502 passed and one fish skip; jasper-terminal 313 tests, 312 passed
and one existing font skip — 816 tests, 814 passed, two skipped. After merging
`main` (which had brought in the cursor-shape fix and its three tests): 819
tests, 817 passed, two skipped, zero failures/errors. Still user-run: each
shell (zsh, bash, and fish if installed) on the real macOS desktop with the
user's own dotfiles and prompt theme, confirming marks, the status-bar dot and
that nested shells stay quiet; plus a look at the two adjacent status-bar dots
and at the status bar at a narrow window width. Merged to `main` and pushed.
No GUI.

**Configured cursor shape under tmux (2026-09-16):** On
`claude/shell-cursor-shape-bug-9981da`, `terminal.cursor.shape` was ignored for
anyone whose shell sends a terminal query. Only `CSI ... SP q` is DECSCUSR, but
jediterm-core 3.76 reads *every* CSI ending in `q` as one — measured:
`CSI > q` (XTVERSION), `CSI Ps q` (DECLL) and `CSI ? q` all arrive as
`BLINK_BLOCK`, indistinguishable from an application asking for a block cursor.
tmux sends XTVERSION when it opens a session, so `display.cursorShape()` was
non-null within milliseconds of launch and `CursorStyle.effective` returned the
program's shape forever after; the config never lost a value, it was outranked.
`ShellIntegrationFilter` now parses CSI properly (parameter bytes `0x30–0x3F`,
intermediates `0x20–0x2F`, final `0x40–0x7E`) and **drops** a `q`-final sequence
that carries no space intermediate, which also retires the `CSI_ZERO`/`CSI_SPACE`
states. JediTerm answers none of these queries (verified: no reply bytes), so
nothing is lost. Real DECSCUSR is untouched: `CSI 0 SP q` / `CSI SP q` still pass
through and append `cursor-reset`, `CSI 5 SP q` still sets a beam, and a query
arriving afterwards no longer discards it. Verified end to end against the user's
own `config.toml` and real tmux: shape stays null, effective style `UNDERLINE`.
`./gradlew check`: 773 tests, zero failures, one existing font skip. Jasper does
not reply to XTVERSION — deliberate, matching the previous behaviour; worth
revisiting only if a program needs the answer. Merged to `main` and pushed; the
branch is deleted. No GUI.

**Palette snippets (2026-09-16):** On `claude/snippets` in `.worktrees/snippets` (from
main `b9efd41`), the command palette gains a third scope, `SnippetsScope` (id
`jasper.snippets`, bookmark icon, aliases `snip`/`snippets`), backed by an
application-wide `SnippetStore` over `snippets.toml` (one serial worker, EDT
snapshots, append-only writes, so hand edits and comments survive). Snippets are
named commands with `{{placeholder}}` tokens (`\{{` for a literal); Paste and
Paste-and-run on a snippet with placeholders open a fill-in step — one field per
placeholder in first-appearance order, Tab/Shift+Tab wrap, values remembered per
placeholder for the process — and Shift+Enter is "Edit file" (creates
`snippets.toml` with its header if missing, then opens it in the OS editor).
From History, Shift+Enter is "Save as snippet…": a one-field name step prefilled
with the command's first word and argument, appending to the store and, on
success, reopening Snippets with the new row selected; a duplicate name or a
write failure keeps the step open with an error message. Cmd+J/Ctrl+Shift+J
always opens Snippets (a prior override collides with the new `snippets_palette`
default exactly as `history_palette` does). Deviations from the plan text,
recorded in the plan and spec status banners: scopes expose steps through
`PaletteScope.step(row, verb, context)`, consulted before `execute`, rather than
`execute` itself returning a step as the spec's prose suggested; `available`
gained a verb parameter so History refuses Paste on a dead pane but still allows
Save; the controller ignores Enter while a step's asynchronous `complete()` is
pending (a `completing` guard) so two quick presses cannot double-submit; the
History duplicate-name message names the *existing* snippet's own canonical
name, not the typed one; `WindowContent` now assigns its snippet store
before building the History scope, fixing a constructor-order bug in passing;
Snippets' paste verbs (`available`) require a live target while Edit file does
not, and the error row is likewise available only for Edit file; and reopening
the palette after a save sets the query field to the new snippet's name (a new
`PaletteStep.Result.reopen(scopeId, rowId, query)` overload) so the row is
still visible under `palette.max_results` even when it sorts last in the file.

Task 6 extended the [headless render matrix](design/command-palette/README.md)
with three new states — the Snippets list, the `Deploy` fill-in step and the
History save-name step — rendered from a six-snippet fixture file (two of them
with placeholders) written into a temporary directory, all inspected in dark and
light. [Guide](command-palette.md#snippets),
[design spec](superpowers/specs/2026-09-15-jasper-snippets-design.md),
[plan](superpowers/plans/2026-09-15-jasper-snippets.md). Fresh
`./gradlew check --rerun-tasks`: jasper-app 464 tests passed; jasper-terminal 303
tests, 302 passed and one existing font skip. Total 767 tests, 766 passed, one
skipped, zero failures/errors. Still user-run: Cmd+J on a real macOS desktop,
editing `snippets.toml` in the real OS editor, and the fill-in step with an input
method active. No GUI, merge or push.

**Palette result cap (2026-09-15):** `palette.max_results` (1–20, default 5, live) is now the
hard cap every scope returns: `PaletteContext` carries it to `CommandsScope` (through a
`CommandSearch.find` limit) and `ShellHistoryScope` (which no longer holds fifty rows and
scrolls twelve), the card sizes to exactly that many rows, and `PaletteScope.preferredRows()`
is gone. Cmd/Ctrl+1–5 still number the first five. Tests cover parsing, the range, both
scopes and the live change on an open palette. History renders regenerated.

**Palette scopes and shell history (2026-09-15):** On `claude/palette-scopes` in
`.worktrees/palette-scopes` (from main `86352b9`), the command palette is now
scope-based: one `PaletteScope` contract with two implementations, `CommandsScope`
(wraps the existing registry/search/three-recents behaviour unchanged) and
`ShellHistoryScope` (substring search over an application-wide `ShellHistoryIndex`
fed by zsh/bash/fish/nushell/PowerShell history files and OSC 133 B/C live
capture; nushell is looked up under `$XDG_CONFIG_HOME` or `~/.config` and, on macOS,
also under `~/Library/Application Support/nushell`). Cmd+K/Ctrl+K
always opens Commands, Cmd+R/Ctrl+Shift+R always opens History (a prior user override
already bound to that shortcut now collides with the `history_palette` default and reverts
all keybinding overrides to defaults until the user sets `history_palette = "none"` or
rebinds it); a scope's own shortcut dismisses it while already active, even from the
picker. Typing `>` at the start of an empty query opens an in-card scope picker
(Tab/Enter commits, Escape or deleting the `>` reverts); a visible chip shows the
active scope and doubles as a picker button. History rows show a shell tag,
optional working-directory detail line and, on Enter, paste the command into the
focused pane; Cmd+Enter pastes and runs. `history.enabled` (live) registers or
removes the scope entirely. Deviations from the plan text: the picker state is
derived statelessly from the query text (`query.startsWith(">")`) rather than
tracked as a transition, since `JTextField.setText` fires remove+insert and a
transition-based check cannot see a `>`-prefixed value the whole way through;
`PaletteKeyRouter` claims a key only when its target scope is registered, so a
disabled History shortcut is consumed but not held; `ShellHistoryParser.consumed`
tracks raw byte offsets so a dangling zsh/PowerShell continuation is re-read
later and a trailing fish block is re-parsed on the next tail read; clipped
16 MiB tail reads trim forward to the next newline; history rows use the logical
`Font.MONOSPACED` face rather than the pane's own terminal font, so the palette
needs no font plumbing from the terminal view; `CommandCapture` and its test
fixture use JediTerm's `CharUtils.DWC`/`CharBuffer` directly, confined to
`jasper-terminal`; `ShellHistoryScope` hides the shell tag on a row when only one
shell contributed to the snapshot, rather than always showing the shell name as
the tag as the spec described. Task 11 extended the [headless render matrix](design/command-palette/README.md)
with three new states (History recent/query, scope picker) and added
`ShellHistorySearchMeasurement`, a substring-ranking benchmark over a synthetic
50,000-entry snapshot (medians only, no CI threshold; [report](design/command-palette/history-search-measurement.md)).
[Guide](command-palette.md), [design spec](superpowers/specs/2026-09-15-jasper-palette-scopes-design.md),
[plan](superpowers/plans/2026-09-15-jasper-palette-scopes.md). Fresh
`./gradlew check --rerun-tasks` after the final-review fix wave executed all eight
tasks: jasper-app 438 tests passed; jasper-terminal 303 tests, 302 passed and one
existing font skip. Total 741 tests, 740 passed, one skipped, zero failures/errors.
A 2026-09-15 follow-up (`claude/history-hardening`) then made the rewrite
fingerprint always cover the last 64 file bytes before the offset (kept across a
zero-progress read) and added the nushell candidates above. Still user-run: Cmd+R on a real
macOS desktop, input-method composition with the chip present, chip rendering on
the native title-bar theme, and paste/paste-and-run into a real zsh with
bracketed paste enabled. No GUI, merge or push.

**Fast quit (2026-09-14):** On `claude/fast-quit` in `.worktrees/fast-quit` (from main `0c6463a`), the JVM no longer lingers after the last window closes. Nothing called `System.exit`, so exit relied on AWT's auto-shutdown, which waits a full quiet second after the last peer is disposed (JBR 25 `AWTAutoShutdown.SAFETY_TIMEOUT`; measured 1.0 s headlessly) before the JVM tears down. `JasperApplication.shutdown()` now waits, bounded at two seconds, for the command-history file and the exit of every shell it started (`track`), then runs an injected terminator off the EDT; `Main` passes `System.exit(0)`, tests pass a no-op. The bounded pty force-kill fallback is preserved because closed shells' `exitFuture`s are awaited. One INFO line, `Shutdown finished in N ms`, records the timeline in the app log. New headless `JasperApplicationShutdownTest`; `./gradlew check` passed. Desktop check is user-run: type `exit` (with `on_exit = "close_on_success"`) or close the last window and the Dock icon should vanish at once.

**Two modern theme variants (2026-09-14):** On `claude/modern-restore`, the purple theme, the OS-following appearance mode (and its jSystemThemeDetector dependency), custom Alacritty-style palette files and the `[colors]` table are removed. `ui.theme.variant` (`"dark"` default, or `"light"`) selects the bundled FlatLaf Dark + Jasper Dark or FlatLaf Light + Jasper Light pairing; View → Appearance keeps Light/Dark as a temporary override that clears when the saved variant changes; Reload config retries a failed chrome installation. No Java built-in look and feel is used or selectable. `ThemeState` is now `(saved, override)`, `ConfigService.State` carries no palette, `ConfigurationController` has no appearance source, and `AppDirs` has no themes directory. Legacy `[colors]` settings warn as unknown and are ignored ([migration](rebranding.md), [configuration](configuration.md#theme-variant)). `./gradlew check` after the rebase onto `b779c08` with the desk buddy carried across: 656 tests, 655 passed, one known font skip. Headless previews regenerated: `design/mock-ui-dark.png`, `design/mock-ui-light.png`, and the 16-image [command palette matrix](design/command-palette/README.md). Not done: GUI check (user-run), merge, push. The classic vanilla Swing look (`d09b82e`/`5bc288f`) is still to be added back later as a selectable option.

**Modern Jasper restored (2026-09-14):** On `claude/modern-restore` in `.worktrees/modern`, the user chose the modern FlatLaf app as Jasper's baseline again. This commit puts the tree of the rebrand snapshot `e365e8a` (Jasper rebrand + silver turtle icon on the pre-vanilla FlatLaf chrome: native macOS title bar with tabs, Tabler toolbar, coordinated dark/light/purple themes, custom TOML palettes, System appearance) back on top of main. The branch was then rebased onto `b779c08` (main after the desk-buddy merge), re-applying the desk buddy's small hooks in the config loader, snapshot, template, controller, application, terminal window, window chrome and commands onto the modern files; the buddy's own classes, sprite, tests and docs came across unchanged. Main `5bc288f` had merged the rebrand with the vanilla Swing snapshot `d09b82e`. Nothing from that merge besides the vanilla UI and its documentation was dropped; the merge's edits to `rebranding.md` and the spec's visual amendment were vanilla-only. The vanilla Swing implementation stays in history at `d09b82e` and `5bc288f` (standard controls, OS title bar, GNOME 2 icons, `ui.laf` built-in look and feel selection, headless previews under `docs/design/vanilla-swing/`), and a FlatLaf-light/dark `ui.laf` addition to that version is on `claude/flatlaf` at `618f157`. Direction: keep the modern theme as the default and later add the classic vanilla Swing appearance back as a selectable option rather than a replacement. `./gradlew check` on the restored tree before the theme change: 662 tests, 661 passed, one known font skip. No GUI, merge or push.

**Desk buddy (2026-09-14):** On `claude/desk-buddy` in `.worktrees/desk-buddy` (from main `5bc288f`), the pixel-art Jasper desk buddy is implemented: a translucent always-on-top utility window that blinks while idle, greets on hover, drags with a remembered position (`buddy.toml`), raises the last active terminal on double-click, and shows only while a terminal window is open and not minimized. `buddy.enabled` (live) plus View → Show Jasper / palette / right-click toggle him. Sprite pipeline: `packaging/buddy/jasper-buddy.ase` (LibreSprite master) → committed PNG strip; the bootstrap `generate.py` drew the first frames. Deviations from the plan text are recorded in the plan's Deviations log (shared `TomlStateFile` helper, hook placement before `configuration.register`, `syncBuddy` refresh/fault isolation, silent headless path). Approved follow-up (2026-09-14, commits on the same branch): left alone he now sits at 20 s, tucks into his shell at 60 s and sleeps with rising Zs (six new frames, strip 588 × 48), hovering plays a fixed one-second greeting and restarts the boredom clock (waking him first if he is tucked or asleep), postures are absolute deadlines so a lid-close tick resolves in O(1), `hoverExited` is gone and a double-click rather than a single click raises the last active terminal. `./gradlew check` passed: 629 tests, 628 passed, one existing font skip. Right-click now opens a reusable translucent bubble beside him reading Hide Jasper (`BuddyBubbleContent`, `BuddyBubblePanel`, `BuddyBubblePlacement`, `BuddyBubble`) instead of a `JPopupMenu`, on `claude/buddy-bubble` in `.worktrees/buddy-bubble` from main `e9f921c` with `./gradlew check` at 675 tests, 674 passed and the one existing font skip; status messages themselves are still deferred, though the component and its `above` placement are ready for them. Every appearance now opens with a two-second spawn — he fades in over half a second while three new sparkle overlay frames cycle around him (strip 714 × 48), then waves for a second before standing, with hover ignored until it ends — on `claude/buddy-spawn` in `.worktrees/buddy-spawn` from main `0c6463a` with `./gradlew check` at 682 tests, 681 passed and the one existing font skip. He now also notices the terminal itself: activating a Jasper window greets him (`BuddyAnimator.greet`, which `hoverEntered` now delegates to) and typing in one keeps him awake without a wave (`poke`, via one throttled toolkit key listener the application installs with the buddy and removes on shutdown), on `claude/buddy-attention` in `.worktrees/buddy-attention` from main `d11b817` with `./gradlew check` at 690 tests, 689 passed and the one existing font skip; terminal *output* still does not count as activity. Desktop acceptance is user-run: [checklist](superpowers/plans/2026-09-14-jasper-desk-buddy-manual-check.md). [Design](superpowers/specs/2026-09-14-jasper-desk-buddy-design.md), [plan](superpowers/plans/2026-09-14-jasper-desk-buddy.md). No GUI, merge or push.

**Full Jasper rebrand (2026-09-14):** On `codex/jasper-silver-icon`, the application, Java packages (`dev.jasper.app` / `dev.jasper.terminal`), module directories, Gradle tasks/properties, classes, resources, built-in theme IDs, shell-integration markers, logs/config paths, packaging names/identifier, icon filenames, tools, and documentation now use Jasper. Repository directory and origin remain `moray` / `https://github.com/an0nn30/moray.git`. The first forced check found one hard-coded URL endpoint in `WordBoundariesTest` that needed 26 → 27 for the longer name; corrected it and the subsequent `./gradlew check :jasper-app:packageDist` passed. Both module suites executed across these runs: 662 tests, 661 passed, one known font skip. Native verification confirms `Jasper.app`, `dev.jasper.app`, `Jasper.icns`, Jasper-only application JAR names, and a rebuilt `Jasper-1.0.0-macos-aarch64.dmg`. Production JARs contain no legacy package/resource names. [Migration notes](rebranding.md) describe the new settings locations and theme selectors; existing user data is untouched. Historical benchmark manifests retain actual old artifact names/hashes. Windows native packaging and live GUI acceptance remain unrun. Existing unrelated edits were retained through the file moves. No commit, merge, push, Git repository rename, or GUI launch.

**Jasper silver app icon (2026-09-14):** On `codex/jasper-silver-icon`, the user-selected Silver Desk Buddy turtle replaces Eclipse in the editable master, platform SVGs, macOS ICNS, Windows ICO, and all Swing/Dock PNG sizes. The master matches the approved silver study exactly; Windows uses tighter corners and a larger footprint. The generator and [icon guide](../packaging/icons/README.md) now describe Jasper. `./gradlew check :jasper-app:packageDist` passed: XML totals 662 tests, 661 passed, one existing font skip; terminal checks were up to date. Mac app package verification passed and the DMG rebuilt. All 15 ICO frames match their runtime PNGs byte-for-byte. Native Windows packaging and live desktop appearance remain unverified. Application names/identifiers remain Jasper for this icon-only change. Existing unrelated working changes were preserved; no GUI, commit, merge, or push.

**Rail, vault and SSH planning (2026-09-13):** On `codex/rail-vault-design`, the user requested the rail and working SSH connections, with a credential vault first. The `2026-09-13-jasper-vault-product-design.md` (not present in this checkout) consolidates approved conversation decisions: dedicated manager, separate logins and imported encrypted keys, explicit Unlock with optional OS-stored remembered access (seven days, configurable), and inactivity locking (15 minutes, configurable/disabled). Auto-lock retains valid remembered access; explicit Lock clears it. Existing SSH sessions survive either lock. The user-supplied UI mocks (the historical credential-manager folder is not present in this checkout) now govern manager and Add credential layout: navigation sidebar, upper table, lower editor and explicit Save/Revert. Product-spec review and technical design precede implementation plans. This updates sequencing without declaring the Phase 1 trial complete. Existing split-divider working changes are preserved and unrelated. No implementation, GUI, merge or push for this planning work.

**Split divider visibility (2026-09-13):** On `codex/visible-split-dividers`, terminal splits now have a continuous 2px separator inside an 8px resize target, with brighter grips and hover/pressed backgrounds. Purple, classic dark, and light each supply a contrasting separator color. The rendered-pixel regression failed before the change and passes afterward across both orientations and theme replacement; existing nested split, ratio, and zoom tests pass. `./gradlew check` passed: 662 tests, 661 passed, one existing font skip (terminal tests up to date). Headless nested-pane previews are reproducible with `./gradlew :jasper-app:mockUiPreview --args='/absolute/output --splits'`; purple and light renders were visually inspected. The preview explicitly paints the real AWT divider because peerless Swing printing skips it. No native GUI, merge, or push for this follow-up.

**Command palette integration (2026-09-13):** The user approved merging the palette, including the higher placement in `c4f3335`, into `main` and pushing to `origin`. The conflict-free merged result passed `./gradlew check --rerun-tasks` in 18 seconds with all eight tasks executed: 661 tests, 660 passed, one existing font skip, zero failures/errors. Continue from `/Users/dustin/projects/moray` on `main`; the completed feature worktree and branch are being cleaned up after publication. [Guide](command-palette.md), [previews and verification](design/command-palette/README.md). Native desktop acceptance remains user-run; no GUI or packaging was run during integration. The dated development notes below are historical.

**Palette placement follow-up (2026-09-13):** Moved the card upward at the user's request: its center targets one-third of the way down the terminal area, retaining horizontal centering and small-window clearance clamps. The updated position assertion failed before the change; all 15 palette integration tests pass afterward. Regenerated the 24-image theme/state matrix and separate 2× UI-scale artifact; representative normal/narrow renders were inspected. This is a focused follow-up to the full verification below; native GUI checks remain user-run.

**Command palette implementation (2026-09-12):** Complete and independently reviewed on `codex/command-palette-design` in `.worktrees/command-palette`, branched from main `1db31b1`; the main checkout remains unchanged. Cmd/Ctrl+K opens the centered themed palette, Cmd/Ctrl+1–5 runs numbered results, and three shared recents persist across restarts. Commands register through window-owned Swing actions without a plugin framework. All six task gates and final whole-branch/scoped review are complete through runtime fix `28a7ff0`. Final review corrected selected-result visibility after query/reorder changes in short scrolled cards; two actual-viewport regressions reproduced the defect before the fix. Fresh `./gradlew check --rerun-tasks` executed all eight tasks: 661 tests, 660 passed, one existing font skip, zero failures/errors. Actual purple/classic/light renders and separate 2× UI scaling were inspected; matching-only medians were 18.3–93.9 µs over 1,000 indexed commands. Branch-wide diff checks and source hygiene passed after temporary-report cleanup. [Guide and extension example](command-palette.md), [visuals, measurements and review record](design/command-palette/README.md), [plan](superpowers/plans/2026-09-12-jasper-command-palette.md), [native acceptance checklist](superpowers/plans/2026-09-12-jasper-command-palette-manual-check.md). Native focus, IME, accessibility, Windows desktop and real restart acceptance remain user-run. No GUI, packaging, merge or push.

**Default purple theme (2026-09-12):** `jasper-dark-purple` now supplies the default app chrome and terminal colors, including the dark half of Follow System. Explicit `jasper-dark` remains classic; `jasper-light` is unchanged. Custom files keep their existing fixed classic palette inheritance. Source defaults, generated config template, root example and documentation are updated. `./gradlew check` executed both modules: 585 tests, 584 passed and one existing font skip. Actual purple/classic/light Swing + ANSI previews were rendered headlessly and inspected; independent review found no actionable issues. The Mac app/DMG was rebuilt and package verification passed with the official Eclipse icon. The user has approved merging the completed icon/theme work into `main` and publishing it to `origin`. No live settings, GUI or installation changes were performed. Historical palette/render folder `design/jasper-dark-purple/` is not present in this checkout.

**Official app icon (2026-09-12):** The user-approved brighter Eclipse design is integrated into native packaging and runtime windows. Native macOS ICNS and Windows ICO inputs, runtime window/Dock PNGs and a reproducible SVG export script are in place. macOS uses one 100px margin on the 1024px canvas and a rounded squircle; Windows uses tighter corners and a 32px margin. `./gradlew check :jasper-app:packageDist` passed: 580 tests in result XML, 579 passed and one existing skip (unchanged terminal checks up to date). Native Mac image/DMG, bundle icon bytes, strict signature and all icon containers were verified; independent review found no issues. Live Mac Dock/Cmd+Tab and Windows desktop/native packaging acceptance remain pending. The 444 rejected/exploratory design files were moved out of the repo to Trash after verifying the official master and all archived hashes; this is explicitly authorized cleanup and supersedes the earlier preservation note below. Main integration and publication are authorized; no GUI launch or installation performed. [Official icon assets and evidence](../packaging/icons/README.md).

**Current readiness work (2026-09-12):** Logging, terminal hardening and repeated packaged measurements are integrated locally on `main` after user approval. Continue from `/Users/dustin/projects/moray`. Task and whole-branch/scoped reviews are complete. Published `cc3f957` passed [three-platform CI](https://github.com/an0nn30/moray/actions/runs/34724932663). Merged verification exposed one incomplete-input test wait, corrected by test-only `7481114`; the fresh merged check passed 576 tests (575 passed, one known skip), all eight tasks executed. All 312 pre-existing untracked icon-option files were preserved byte-for-byte with unchanged permissions. The URL-test correction and integration bookkeeping remain local; main has not been pushed. The verified DMG and 896-file benchmark archive were preserved under `build/readiness-2026-09-12/` before cleaning up the completed feature worktree. Runtime code is unchanged from measured `8d84e7d`; the full memory comparison remains attributed to `10cc444`. [Readiness handoff, measurements and remaining native/trial gates](terminal-readiness.md). The two-week trial has not started.

The following dated milestone notes describe earlier verification; the readiness handoff above is the current state.

**Plan 4d implementation:** The approved macOS and Windows packaging work is fully reviewed, verified and integrated locally on `main` through `0162293`, including direct README build/DMG instructions. Continue from `/Users/dustin/projects/moray`; the merged feature worktree is being cleaned up. [Build commands and native checklist](packaging.md), [design](superpowers/specs/2026-09-12-jasper-plan-4d-packaging-design.md), [implementation plan](superpowers/plans/2026-09-12-jasper-plan-4d-packaging.md). Gradle creates and verifies a native image with JBR 25, then a macOS DMG or portable Windows ZIP. Native macOS metadata, runtime, dependency payloads, strict bundle seal and DMG integrity were verified, including alternate version and paths with spaces. Windows/unsupported-host execution and desktop acceptance remain unrun. The initial package version is `1.0.0` because macOS jpackage rejects a zero major version; this remains a development build. No generated bundle metadata is changed to satisfy verification: vendor and architecture are read from the bundled runtime and native files. The launcher now removes inherited terminal identity variables before explicit configuration overlays and supplies absent/blank macOS LANG with `en_US.UTF-8`. The example and configuration documentation are updated. `origin` is configured at `https://github.com/an0nn30/moray.git`; no push has been performed. Broader app logging remains pending.

**As of:** 2026-09-12. Plan 4d and the README build instructions are integrated locally on `main`. Fresh merged `./gradlew build :jasper-app:packageDist --rerun-tasks` executed all 16 tasks in 55 seconds: 508 tests, 507 passed, one known font skip, no failures/errors. Native image verification and DMG integrity checks passed. The main checkout was clean before integration. The current DMG is `jasper-app/build/packaging/dist/Jasper-1.0.0-macos-aarch64.dmg` in the main checkout. Windows execution, native UI, benchmark and daily-use acceptance remain open.

**Shell-exit follow-up:** `terminal.on_exit` now supports `keep_open` (the default), `close_on_success` and `close`. Policy reloads apply to future exits in existing and pending panes without changing view options; retained output stays open. Closure follows the existing pane → empty tab → empty window lifecycle and preserves siblings. This bounded follow-up uses implementer TDD/self-review plus independent root review, with no new architecture or historical-plan rewrite. Fresh `./gradlew check --rerun-tasks` executed all eight tasks: 463 tests, 462 passed, one known font skip, no failures/errors. Source hygiene and diff checks passed. Independent review approved with no findings; merged main reproduced the full verification. The merged branch/worktree are cleaned up; [native shell-exit checks](superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md#shell-exit-follow-up) remain user-run. At that milestone, remaining Plan 4 work was custom themes/system appearance, logging/launcher cleanup and packaging; current Plan 4c progress is recorded below.

**Compact padding/config example follow-up:** The terminal pane now uses a 4px inset on every side, superseding the old screenshot-matched 24px inset. Initial window sizing includes the same 8px total per dimension. The root [copyable example](../config.example.toml) contains all supported non-shortcut defaults and optional commented platform-specific shortcut examples. Independent review approved with no findings. Headless layout and actual-file parse coverage passed; [dark/light renders](design/compact-padding-comparison.md) were visually inspected. Native visual sizing remains user-run.

**Plan 4b work:** Full terminal configuration is integrated on `main`. It was developed on `codex/plan-4b-terminal-config`, based on `ab672fa`; the merged worktree and branch are cleaned up after verification. All four task reviews approved through `9d8b9b4`; final whole-branch review approved after the documentation-only correction `7ab277b`, with no open findings. Fresh `./gradlew check --rerun-tasks` executed all eight tasks: 442 tests, 441 passed, one known font skip, no failures/errors or test output. Both modules passed source hygiene and the branch passed diff checks. Native acceptance remains user-run. See [design](superpowers/specs/2026-09-12-jasper-plan-4b-terminal-config-design.md), [implementation plan](superpowers/plans/2026-09-12-jasper-plan-4b-terminal-config.md) and [native checklist](superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md).

**Plan 4a progress:** Saved settings and live reload are integrated on `main`, with all task and final reviews complete through `acd5ea3`. Fresh full verification: 394 tests, 393 passed and one known font skip, no failures/errors. Actual status renders passed visual inspection in dark/light at normal/narrow widths. Final whole-branch review approved after correcting Windows-specific test fixtures; no findings remain open. Native and Windows execution remain unverified. See [configuration usage](configuration.md), [status renders](design/config-status-comparison.md) and [native acceptance](superpowers/plans/2026-09-11-jasper-plan-4a-manual-check.md).

**Current follow-up:** Default title/tab height is now 38px, configurable from 28–72 through View → Tab height… for the current window. Both Swing and native height use the same value; reset restores 38, cancellation preserves the previous value. View height changes remain temporary; `window.tab_height` supplies the saved default and applies live when edited. Opening and closing tabs and the selected underline animate with a shared 180ms eased settle; selection/focus/launch remain immediate and timers stop when idle/hidden/disposed. The existing shortcut engine now provides Cmd/Ctrl+1–9 and Cmd/Ctrl+{ / } (Shift+brackets), with plain Ctrl for tab navigation outside macOS. Unrelated shortcut behavior is retained. [Design](superpowers/specs/2026-09-11-jasper-tab-motion-design.md), [plan](superpowers/plans/2026-09-11-jasper-tab-motion.md), [native checks](superpowers/plans/2026-09-11-jasper-tab-motion-manual-check.md).

**Active revision:** The user's exact mock supersedes the earlier separate title and two-tone toolbar choices. Implemented title-bar tabs, horizontal neutral toolbar, and seamless terminal/status surface. See the [design](superpowers/specs/2026-09-11-jasper-mock-ui-design.md), [implementation plan](superpowers/plans/2026-09-11-jasper-mock-ui.md), [original reference](design/mock-ui-reference.png), and [measured comparison](design/mock-ui-comparison.md). The current compact height supersedes the original mock geometry; measured colors are retained. Font metrics, real grid dimensions, native window appearance and motion still require visual acceptance.

Read [AGENTS.md](../AGENTS.md) for repository rules, [README.md](../README.md) for running, and the [Phase 1 design](superpowers/specs/2026-09-10-jasper-phase-1-terminal-design.md) for binding product requirements. Normal GUI acceptance is user-run. The user explicitly authorized controlled readiness benchmarks; game/VM prerequisites were checked before every run.

## Plan 4c implementation — custom themes and system appearance

Implemented on `codex/plan-4c-themes` and integrated locally on main after user approval. The completed feature worktree and branch are being cleaned up after successful merged verification. All four task reviews passed; whole-branch review findings were resolved in `3c3af48`, and scoped re-review approved with no new findings. See [design](superpowers/specs/2026-09-12-jasper-plan-4c-themes-design.md), [implementation plan](superpowers/plans/2026-09-12-jasper-plan-4c-themes.md), [configuration](configuration.md), [headless comparison](design/plan-4c-themes/comparison.md) and [native checklist](superpowers/plans/2026-09-12-jasper-plan-4c-manual-check.md). The main checkout was clean at integration; the updated example is now available at the repository root.

Resolved themes separate chrome from custom terminal/padding/status colors. Follow System, explicit choices, accepted config palettes and source warnings now reach retained/hidden/zoomed/pending/new views. Palette-only updates preserve delegates and sessions; configuration refresh preserves custom status colors. Main alone creates the deferred production source, and controller close removes its listener and guards late callbacks. Headless source tests use synthetic bindings only.

Task 4 deviations: remember latest OS reading outside the committed reducer so failed LAF installation preserves effective theme/menu while Follow System can retry without a second event (controller-approved). Native title callback stays at the existing `MacTitleBar.install` ownership boundary and extracts resolved chrome. Pending panes receive palette backgrounds before attachment. Status palette-derived readability also applies when a custom palette equals the opposite built-in palette. Installation-specific exceptions keep arbitrary application callback errors outside the theme failure-reporting catch, proven by a focused RED/GREEN regression. The existing Task 1 configuration bridge is removed. The detector-only JNA exclusion is retained because pty4j supplies both JNA 5.14.0 modules.

Fresh `./gradlew check --rerun-tasks` passed with all eight tasks executed: 502 tests, 501 passed, one known `FontSetTest.fallsBackWhenPrimaryCannotDisplay()` skip, zero failures/errors. Both modules passed source hygiene (152 Java files) and `git diff --check` passed. Native detector/GUI/physical screens/benchmark remain unrun; all native checklist items are unchecked. Logging/launcher cleanup and packaging remain pending. Final review correction `3c3af48` makes explicit Reload retry unchanged desired theme state through the existing delivery guards, makes service-test timestamps deterministic, and adds the combined malformed-main-config/theme-poll regression. Both actual OS and saved-config failure paths recover through the real Reload action. Root final verification ran all eight tasks in 13 seconds: 506 tests, 505 passed, one known font skip, zero failures/errors; scoped re-review approved all three findings. No review findings remain open.

## 1. Goal and delivery phases

Jasper is a Java Swing terminal workstation with a MobaXterm-style layout, built to replace `~/projects/conch` as the daily terminal. Product phases: terminal → SSH session management → credential vault → SFTP → tunnels → plugins and editor. Reuse tested non-UI Java from `~/projects/termlab-bundle` in later phases. Do not redesign the terminal foundation before the Phase 1 switch-over test.

| Plan | State |
|---|---|
| 1 — Terminal core | Complete on main (`6daa61f`..`d0ccbfb`) |
| 2 — Terminal completeness | Complete on main (`196e24d`..`0ce4add`) |
| 3 — App chrome | Implemented and reviewed on main; all automated checks passed; native acceptance pending |
| 3.5 — macOS chrome, themes and toolbar | Integrated on main and fully reviewed; native acceptance pending |
| Screenshot UI revision | Implemented and fully reviewed; compact/motion follow-up implemented and fully reviewed; native acceptance pending; integrated on main |
| 4a — Saved settings and live reload | Integrated on main and fully reviewed; native acceptance pending |
| 4b — Full terminal configuration | Integrated on main and fully reviewed; native acceptance pending |
| 4c — Custom themes/system appearance | Integrated on main and fully reviewed; native acceptance pending |
| 4d — Native packaging/launcher environment | Integrated locally on main and fully reviewed; native acceptance pending |
| Remaining Plan 4 | App logging integrated locally on main and reviewed |
| Terminal hardening | Implemented, reviewed, measured and integrated locally on main; CI passed; native acceptance/daily-use gate pending |

Plan 3 design: [application design](superpowers/specs/2026-09-11-jasper-plan-3-app-chrome-design.md). Execution: [implementation plan](superpowers/plans/2026-09-11-jasper-plan-3-app-chrome.md). The per-plan scratch workspace was removed after final review; this handoff, completed plan checkboxes and git history preserve the record. Do not restart completed plans: Plans 4a–4d are integrated on main. Logging and hardening are now integrated locally on main. Next are native acceptance and then the daily-use gate; authorized publication and three-platform CI are complete.

Plan 3.5 was developed on `codex/plan-3-5-chrome-themes` and merged locally. That milestone is available in `/Users/dustin/projects/moray` on `main`; Plan 4a is also integrated there. [Title/theme design](superpowers/specs/2026-09-11-jasper-plan-3-5-titlebar-themes-design.md) and [execution plan](superpowers/plans/2026-09-11-jasper-plan-3-5-titlebar-themes-implementation.md) cover the first runnable deliverable. [Toolbar study](design/plan-3-5-toolbar-study.html) is an illustrative discussion aid; the user selected B (fuller, two-tone colored icons), now included as Task 4.

Previous milestone: [Plan 3.5 — macOS chrome, themes and toolbar](superpowers/plans/2026-09-11-jasper-plan-3-5-chrome-and-themes.md). The user has tried Plan 3 during development and reports that it looks good overall; this is qualitative feedback, not a claim that every native checklist item or benchmark was completed.

## 2. Implemented behavior

The existing terminal retains ligatures, fallback fonts, wide characters, colors, cursor modes, Option input, mouse reporting, scrollback, selection, clipboard, bracketed paste, prompt jumps, OSC integration and clickable links.

Plan 3 adds:

- Multiple independently owned windows and tabs; close buttons, middle-click close, drag reorder, F2 rename with shell-title override retention.
- Pure split-tree model; nested right/down splits, directional focus, divider ratios, zoom/restore without restarting shells.
- Shared action catalog and keybinding parser; application shortcuts before terminal encoding; native editing in find fields retained.
- FlatLaf menus, seven-button toolbar with Tabler outline SVGs, view visibility controls, light/dark chrome, focused shell/directory/dimensions status, and inactive-pane dimming.
- Async find bar with debounce, case/regex options, next/previous, count and retained error state, Escape/close, and stale-result rejection.
- Local context menus that preserve terminal-program mouse ownership.
- Per-pane font controls, screen-preserving history clear, working-directory inheritance with launch-time validation.
- Background shell launch with close-before-completion cleanup; native Quit routes through app cleanup.

Plan 4a now reads saved TOML settings, polls for changes, enables Settings/Reload and exposes positioned diagnostics through status. Configured height, toolbar/status visibility, font size, built-in theme and shortcuts apply live across owners. Unrelated reloads preserve temporary View/font overrides; Settings creates a template only when absent. The integrated UI baseline on main predates this configuration behavior.

Plan 4b adds live font family/fallback/ligatures/line height, Option-as-Meta, cursor defaults, copy-on-select, configurable inactive dimming and visual/sound/none bells. Retained and pending views use current live settings without restarting sessions; per-field manual size/theme overrides survive unrelated edits. Shell executable/arguments/environment and scrollback are captured for each new request. New windows use configured rows/columns and shared font metrics, constrained to the usable display. The expanded TOML template and [configuration guide](configuration.md) document all fields, validation and lifecycle rules.

The earlier Plan 3.5 implementation (visual geometry superseded below) includes:

- Live dark/light terminal palettes and coordinated FlatLaf themes (`09e382e`, `6bdd298`). Existing hidden/zoomed panes, delayed launches and new windows use the selected theme while retaining sessions, fonts, selection/find state and split ratios. Explicit terminal truecolor remains unchanged.
- A slim macOS title surface (`7833692`) using the decorated frame and supported public properties. Native traffic lights and window behavior remain owned by macOS; separate tabs and toolbar sit beneath the title. Other platforms retain their existing decorations.
- The user's selected toolbar B (`116709b`): 28-pixel two-tone Tabler icons, distinct action colors in both themes, labels and visibility modes retained. Settings/Reload remain muted while disabled.

Actual headless Swing previews: [dark](design/plan-3-5-titlebar-dark.png) and [light](design/plan-3-5-titlebar-light.png). Native traffic lights are absent from these renders. Native acceptance remains user-run; Plan 4 retains configuration, custom theme files, persistence, automatic system appearance and packaging.

The screenshot revision now supplies:

- A single 54px title/tab row with native traffic-light space, persistent tab controls, overflow navigation and a clipped trailing title. Public JBR title-height integration retains native decoration with a fallback.
- A 53px horizontal toolbar using 16px neutral Tabler outlines, inline labels, highlighted New tab, separators and right-aligned Settings/Reload. Existing actions, menus and visibility modes remain functional.
- A 30px status bar that shares the terminal background, live shell/path/grid metadata and running dot; the current 4px terminal padding supersedes the old 24px mock geometry, and the app default/reset font size remains 16.
- Measured dark surface and visible ANSI accent colors, with equivalent light geometry. Actual headless previews: [dark](design/mock-ui-dark.png), [light](design/mock-ui-light.png). Reproduce with `./gradlew :jasper-app:mockUiPreview`.

## 3. Fixes included during Plan 3

- `openingLink` capture clears on new press and focus loss; a missed release cannot swallow a later gesture.
- Alternate-screen transitions invalidate selections, matches, pending find and viewport row references.
- A session row epoch reconciles history/alternate/reflow resets missed during temporary view detachment.
- Search keeps one bounded per-view executor through reparenting; its thread retires after idle. New requests supersede queued work; cancellation cannot multiply workers on repeated zoom/reparent.
- Expired prompt rows are physically pruned during history eviction.
- PTY close returns promptly to the EDT, sends Unix hangup, applies a bounded force fallback, and closes acquired streams. A non-daemon cleanup worker survives final-window disposal long enough to perform fallback. A real headless child ignoring HUP/TERM is covered on Unix.
- Find navigation requested during debounce/inflight search is retained; invalid regex errors persist until the query changes. Reparenting clears canceled search state.
- Copy action enablement checks selection presence in constant time without extracting text or taking the buffer lock.
- Font and nested-split minimum dimensions propagate to the native frame; PTY, view and emulator agree on the pinned JediTerm minimum of 5 columns × 2 rows.
- Appearance menus synchronize their selection with the active global theme when opened, including changes made from sibling windows.
- Long directory/status text and tab names cannot inflate native minimum width; full tab titles remain in tooltips.
- Global shortcuts are installed on the root pane, with native text-field editing retained.
- F13–F24 override parsing uses Java's separate high-function-key range; actual F13/F24 strokes are regression-tested.

## 4. Verification still required

1. User-run [compact tab/motion checklist](superpowers/plans/2026-09-11-jasper-tab-motion-manual-check.md), then [screenshot UI checklist](superpowers/plans/2026-09-11-jasper-mock-ui-manual-check.md): native controls, title-tab hit testing, dragging/double-click, fullscreen, scaling and exact visual comparison in real windows. The earlier Plan 3.5 appearance checklist is superseded by this reference. Also retain the [Plan 3 acceptance checklist](superpowers/plans/2026-09-11-jasper-plan-3-manual-check.md): native windows/menu/Quit, split/zoom/focus/dividers, tab gestures, find, clipboard, links, mouse reporting, directory inheritance and chrome readability. Headless tests do not establish these native visual results.
2. Packaged readiness benchmarking is recorded in the [measurement report](benchmarks/2026-09-12-terminal-readiness.md). Final runtime `8d84e7d` passed three full throughput runs at 36.91–37.28 MB/s. The 35 MB/s floor passes; 45 MB/s target remains unmet. These controlled fixtures do not replace normal native acceptance.
3. [three-platform CI run](https://github.com/an0nn30/moray/actions/runs/34724855954) passed macOS, Ubuntu and Windows at `c793925`. Windows ConPTY/packaged desktop and Linux desktop/font behavior still require native acceptance. The branch was published and then merged locally; main has not been pushed.
4. After Plan 4, satisfy the Phase 1 manual checklist and two-week Jasper-only trial. Record every reason to reopen conch and fix the blockers before SSH begins.

Known skip: `FontSetTest.fallsBackWhenPrimaryCannotDisplay` skips on this Mac because its probe finds no differentiating glyph. Nerd Font-specific fallback tests run with a suitable installed font and skip on systems lacking it.

## 5. Remaining terminal hardening

The integrated readiness work resolves the carried-over gesture ownership, bounded logical-line traversal, idle frame/blink work, pure mouse-report snapshots, visible search scanning, EDT browser dispatch, multi-notch wheel and selection integrity findings. See the [handoff](terminal-readiness.md) for evidence. Remaining limitations:

- Search does not span physical soft-wrapped rows. Java regex cancellation remains best effort; a pathological running regex can delay the next request in that pane, although queued work and worker allocation are bounded.
- Width reflow or history erase clears row-based selection/matches/prompts and returns to live. This is intentional with JediTerm's row model.
- Strikethrough is unsupported by jediterm-core 3.76 TextStyle.
- macOS font fallback uses JBR/system cascading for CJK/emoji; explicit fallback chiefly affects missing Nerd Font glyphs.

Readiness added astral/edge/overlapping search, bounded wrapped continuation, button ownership and multi-notch regressions. Retained coverage opportunities include large-history indicator geometry, empty-scrollback viewport, copy-on-select, alternate-screen wheel/arrows, paste-to-live, Shift+PageDown and match colors. Add behavioral regressions when working in these areas, not tests that mirror source constants.

## 6. Native acceptance and remaining Plan 4 work

The screenshot revision supersedes Plan 3.5 geometry and artwork. User visual/native acceptance remains open; the user approved integration and starting Plan 4. The integrated UI baseline runs from `/Users/dustin/projects/moray` with `./gradlew :jasper-app:run`. That checkout now includes Plan 4a live reload and Plan 4b full terminal configuration. No complete pixel-identity claim: native frame controls/focus/font rasterization are not headless-verifiable, and the existing terminal renderer computes 91 × 35 cells versus the mock's 100 × 40 at the measured content size. See the comparison report for exact prompt ink bounds. Built-in palettes and live theme application have moved forward from Plan 4.

Plans 4a–4d supply saved settings, full terminal configuration, custom themes/system appearance, launcher cleanup and native packaging. The integrated readiness work adds logging and the recorded interaction/performance hardening. [Measurements](benchmarks/2026-09-12-terminal-readiness.md) report baseline/final memory, allocation, GC, throughput, EDT delay, cleanup and diagnostic limits. The controlled Java fixture uses argument lists on both platforms; Windows execution remains unverified.

Remaining opportunities: logical-pixel font-cell rounding, cosmetic ShellIntegrationFilter hold-limit prefix counting, and deeper retention/dependency/font-cache profiling if real usage warrants it. No speculative heap or backend change is part of this slice. Three-platform CI passed; normal native acceptance remains necessary before the two-week trial.

## 7. Decisions and implementation deviations

- Keep JediTerm, own renderer, two one-way modules, no emulator abstraction/plugin API.
- Option+Left/Right remains ESC[1;3D / ESC[1;3C, not ESC b / ESC f.
- OSC8 schemes remain http/https/ftp/mailto; detected text may include file URLs. Refused OSC8 targets do not fall through to text detection.
- macOS Command-click links takes precedence over program mouse reports; Shift provides local mouse behavior; horizontal Shift-wheel on macOS is ignored.
- The user's approved Phase 1 direction and Plan 3 go-ahead were used for execution; a supplemental written design records concrete app contracts.
- On Linux/Windows, cmd already means Ctrl+Shift. Defaults explicitly written cmd+shift add Alt to avoid collisions; user overrides parse literally and collisions are errors. macOS defaults are unchanged.
- Settings/reload and saved configuration were deferred from Plan 3 to Plan 4a and are now implemented. Plan 3.5 supplied coordinated built-in themes and macOS title-bar work; automatic system appearance remains a later Plan 4 deliverable.
- Four direct multi-pane carryovers were included; the remaining hardening list above is follow-up scope.
- Native acceptance is user-run per newly merged AGENTS.md. No agent GUI launch or benchmark followed discovery of that rule.
- The user selected the local `~/projects/tabler-icons` repository. Seven outline assets and full MIT license are bundled; SOURCE.txt records the source commit and theme-color adaptation.

## 8. Workflow and historical Plan 3 verification

Superpowers spec/plan → task implementer with TDD → separate task review → scoped fix/re-review → final branch review. Reviewed commits: models `a75d5fa`; terminal hooks `56b251d`; lifecycle fixes `73f105c`; desktop `acfc79f`; desktop review fixes `6fac903`/`dcc0b00`; final minor fixes `dda35e2`. Task reviews and final whole-branch review approved. Final fixes synchronize appearance menu state and make the forced-close test wait for installed signal traps. A mutation check confirmed that removing force fallback makes the latter test fail; production cleanup was restored before verification.

Root final verification on `dda35e2`: all 8 Gradle tasks executed successfully in 8 seconds; 277 tests, zero failures/errors, one expected font skip. Source-hygiene and `git diff --check` passed. No compiler warnings. Final reviewer compared the exact default key catalog with the parent spec and found it aligned; selected shortcut regressions for live overrides belong in Plan 4, without duplicating source constants in tests.

Plan 3 was merged locally and its feature worktree/branch removed after merged checks passed (277 tests, zero failures/errors, one expected skip). Plan 3.5 is now integrated on main; continue native acceptance from `/Users/dustin/projects/moray`, retaining the daily-use gate.

## 9. Execution rulings preserved from the completed Plan 3 ledger

These are historical Plan 3 scope decisions. The user's subsequent Plan 3.5 amendment above changes the sequencing of built-in theme work.

- The existing approved Phase 1 design plus the user's Plan 3 go-ahead authorizes execution; use a supplemental execution design instead of restarting approval — avoids repeating settled layout decisions — costs rework if the detailed choices differ from user intent.
- Non-mac defaults containing explicit cmd+shift add Alt because cmd already means Ctrl+Shift — prevents duplicate destructive shortcuts — costs non-mac shortcut familiarity; macOS unchanged.
- Settings/reload remain visibly disabled and status says Built-in defaults until Plan 4 — no configuration subsystem in Plan 3 — costs waiting for settings until the next planned milestone.
- Light/dark chrome is selectable in Plan 3; automatic system appearance remains Plan 4 with config — keeps scope aligned — costs manual appearance selection temporarily.
- Fold four immediate STATUS carryovers into Task 2 (lost link gesture, alternate-buffer invalidation, prompt pruning, child termination), retain remaining performance/selection findings for follow-up — these directly affect reliable multiple panes — costs additional Task 2 work and later hardening before Phase 1 acceptance.
- GUI smoke and final benchmark are user-run under newly added AGENTS.md — respects desktop-operation restriction — costs leaving visual/performance acceptance pending after headless completion.
- Keep disabled Settings/Reload tooltips user-facing, without internal Plan 4 numbers — developer instructions exclude implementation details from product flows and implementation already explains unavailable config — costs losing an internal milestone reference in tooltips, which remains in docs. Corrected ambiguous plan phrase before re-review.

## 10. Plan 3.5 execution rulings

- The user's go-ahead authorizes implementation of the requested title/theme direction with reviewable initial colors — avoids repeated approval of the already requested milestone — costs visual rework if the initial palette differs from intent.
- Start with slim custom title surface and separate tabs unless the pending optional layout answer changes it — limits geometry changes before toolbar discussion — costs a later layout revision if tabs should be integrated.
- Deliver title/themes as 3.5a and discuss toolbar artwork as 3.5b before implementing that selection — honors the explicit request to discuss toolbar appearance — costs leaving existing icons during the first deliverable.
- Reuse supported decorated-frame macOS full-content properties and FlatLaf bounds rather than add JBR API/native dependencies — verified in pinned source and retains native behavior — costs revisiting integration if native user checks reveal a platform limitation.

The third ruling was superseded when the user selected toolbar B: Task 4 delivers the chosen artwork in this same milestone. The later screenshot explicitly resolves the title layout: integrated title-bar tabs replace that initial layout.

Task reviews: palettes `09e382e`, application themes `6bdd298`, title surface `7833692`, toolbar `116709b` approved. Final whole-branch review covered `942f5f7..0b8461b`. Its only minor finding was closed by `f487631`: the rendered icon test now distinguishes soft fields from enclosed face fills. A mutation removing the four face fills failed the new test while the earlier aggregate test still passed; all resources were restored. The scoped fix review approved with no residual findings. The alpha-specific test runs in dark mode against shared SVG opacity layers; existing color tests cover both themes.

Root final verification on `f487631`: `./gradlew check --rerun-tasks` ran all eight tasks in 11 seconds, with 299 tests, zero failures/errors and one known font skip. Source hygiene and `git diff --check` passed; no compiler warnings. Production code/resources are unchanged since `116709b`. The plan-specific scratch workspace is removed after final review; this handoff, plan checkboxes, committed previews and git history preserve the result.

The user selected local integration. The completed feature worktree and branch are cleaned up after merged verification succeeds. No GUI or benchmark was launched and no remote is configured. Native title-bar/appearance checks and the terminal daily-use gate remain open.

## 11. Screenshot revision execution rulings

- Treat the supplied mock and explicit exact-match instruction as the approved visual design, superseding earlier colored-toolbar and separate-title decisions — avoids asking approval for the mock the user just selected — costs rework for any mistaken measurements.
- Interpret the144dpi screenshot as2x and convert its monitor ICC colors to sRGB for Swing values — aligns logical sizes and displayed colors — costs refinement if the mock's intended logical scale differs.
- Add public jbr-api1.9.0 native title-height integration with fallback — exact54px native-control placement needs more than oldfullcontentproperties — costs native-platform validation and a small app dependency.
- Use sampled visible green/blue/cursor colors and refine toolbar text to11.5px after actual-render comparison — user’s exact mock takes precedence over the initial retain-ANSI/12px approximation — costs changing those built-in dark ANSI accents and potential font refinement on other systems.

Both task reviews and the final whole-branch review (`182b9fb..be39bbf`) approved without actionable findings. The screenshot-only production/test commit was `63a3325`; the follow-up below changes it. Fresh root verification and independent reviewer XML checks confirm 308 total tests, 307 passed, one known skip. This plan’s scratch workspace is removed after review; the committed plan, comparison report, previews and this handoff retain the evidence and rulings. The feature worktree remains available for user inspection and integration approval.

## 12. Compact tab follow-up

Task 1 (`0c4ab43`) approved without findings: shared live native/Swing height, current-window numeric control, real platform shortcut/override dispatch, 80 app tests. Task 2 (`e46eaae`) implements 180ms entry/underline motion; nine deterministic real-strip tests cover intermediate/final rendered positions, retargeting, metadata continuity, overflow/resize/removal and idle/lifecycle cleanup. Fresh root full verification is recorded at the top. The updated previews show settled 38px geometry; the original 54px mock remains a historical reference. Persistence remains Plan 4.

- Treat the user's concrete follow-up as authorization to implement these reversible refinements — avoids repeating approval already given — costs visual rework if chosen defaults differ from intent.
- Default to 38px height and 180ms motion with 3.5% overshoot — makes the row materially shorter and movement quick with a small settle — costs tuning after native viewing.
- Provide a live current-window height control; persistence stays Plan 4 — keeps existing session-only appearance behavior consistent — costs reapplying height after restart.

Final whole-branch review (`182b9fb..7449ac4`) found one integration issue: the active title’s preferred width changed tab-strip allocation and prematurely settled animation. The single fix wave `20764b3` preserves motion when allocation changes leave tab coordinates valid, and adds three real MacTitleBar/JRootPane regressions. Scoped review (`7449ac4..20764b3`) marked it addressed with no new findings. Twelve motion tests now pass; root fresh final verification is 328 total, 327 passed, one known skip. Native smoothness, controls and physical keyboard-layout acceptance remain user-run. The plan scratch workspace is removed after review; committed reports, plan, source and this handoff preserve the result. At that milestone the feature remained in `.worktrees/mock-ui`; section 14 records its subsequent integration and removal.

## 13. Reverse close animation

User-requested bounded follow-up `0f4cd74` reverses the existing motion for visible tab closure. The tab and shell leave the live model immediately; a disabled departing entry contracts while following tabs fill its space and the underline targets the new selection. Closing during entry uses its current width; consecutive closes have independent deadlines. Completed, hidden, reordered, cramped/overflow and disposed departing visuals are removed. Final-tab closure is not delayed.

Four new real MacTitleBar/JRootPane regressions cover active/inactive closes, closing during entry, consecutive deadlines and cleanup. Initial three regressions failed against the prior behavior; the empty-owner timer regression also failed before its fix. Final covering tests:27 passed. Fresh full `./gradlew check --rerun-tasks`:332 total,331 passed, one known font skip; all eight tasks executed, no warnings. Source hygiene and diff checks pass. Independent review of `600c378..0f4cd74` approved without actionable findings; native reverse-motion acceptance remains user-run. This bounded extension updates the existing spec without a new subsystem or plan.

## 14. Approved integration and Plan 4 start

The user explicitly approved merging all current UI work into main and beginning Plan 4 with saved settings and live reload. Main received the complete UI branch at `1bd8b49`. Fresh merged verification passed 332 tests (331 passed, one known font skip), and the completed mock-ui worktree/branch were removed. Plan 4a then started in the isolated `.worktrees/plan-4` worktree. Native UI/benchmark/daily-use acceptance remains open and is not implied by merge approval.

Plan 4a covers existing live controls only: tab height, toolbar/status, font size, built-in theme and shortcut overrides. Runtime View choices remain temporary; saved defaults are in the user-edited file. Additional terminal options, custom themes, system appearance and packaging follow in separate runnable slices. TomlJ 1.1.1 is selected for precise TOML source positions.

## 15. Plan 4a implementation and execution rulings

Parser/paths/CLI: `8c6fa0e`. File service/template: `e25b4c8`. Live application integration: `a18de61`. Review fixes: `9c0ae64` (all modifiers in rendered toolbar hints and removal of obsolete configuration tooltip). Task reviews are complete with no open findings. Full check after fixes executed all eight Gradle tasks: app 158 passed; terminal 235 passed and one known skip. Source hygiene and diff checks passed. No terminal-module changes were needed.

The configuration service retains its parse platform for application of shortcuts. Existing Action-backed toolbar hints and pending-pane hooks were reused; the toolbar formatter was corrected after review exposed its omission of extra macOS modifiers. Native editor, real window and daily-use acceptance remain user-run. The user selected local integration on 2026-09-12; the implementation is now on main.

- Ruling: The user approved local UI merge and starting the proposed first Plan4 deliverable; execute without repeating design approval — follows explicit go-ahead — costs rework if detailed choices differ from intent.
- Ruling: Split Plan4 into runnable stages, first covering existing live controls and config lifecycle — follows the accepted saved-settings/live-reload first step and repository one-deliverable-per-plan rule — costs waiting for additional terminal options, custom themes, automatic appearance and packaging.
- Ruling: Saved defaults are file contents; runtime View choices remain temporary and unrelated reloads preserve them — respects the parent prohibition on unasked config writes — costs editing the file to persist a menu adjustment.
- Ruling: Select TomlJ1.1.1 for positioned diagnostics instead of Jackson binding — upstream API provides parser and key positions directly, an alternative allowed by the parent — costs one parser dependency and validation code.
- Ruling: Invalid or colliding shortcut values fall back to the entire default binding map, while unknown action names warn and are ignored — bindings are mutually constrained and per-action fallbacks can introduce collisions — costs resetting valid custom bindings in the same invalid map until corrected.
- Ruling: Expose the service’s immutable parse platform to the controller and use it to materialize snapshot bindings — resolves the task review’s cross-platform collision concern without changing immutable snapshot data — costs a small package-private accessor.

Final whole-branch review (`1bd8b49..b23ee71`) found one P2 test portability issue: Windows-invalid filename characters and Unix-only expected path separators. The single combined fix wave `acd5ea3` uses portable fixtures and native path formatting while retaining plain-text safety assertions. Scoped re-review approved with no new findings. Fresh final `./gradlew check --rerun-tasks` ran all eight tasks in 10 seconds: 394 total, 393 passed, one known skip, zero failures/errors. Source hygiene and diff checks passed. Production code remains `9c0ae64`; the final fix changed tests only. Native and Windows execution remain unverified. This plan’s scratch workspace is removed after preserving these decisions and evidence; the user subsequently approved local integration, completed on 2026-09-12.

Merged-result verification on `05a94d4`: `./gradlew check --rerun-tasks` passed in 11 seconds, all eight tasks executed; XML confirms 394 tests, 393 passed and one known skip, zero failures/errors. The completed feature worktree is no longer the development entry point; use the main checkout.

## Plan 4b recorded decisions

- Ruling: Treat the user's go-ahead as approval of the full Plan4b scope just presented, continuing the established spec-driven workflow without repeating approval — explicit scope and live/new-pane semantics were accepted — costs rework if detailed choices differ from intent.
- Ruling: Extend the existing terminal options API with backward-compatible constructors and applyOptions rather than replacing views — preserves sessions and existing callers — costs maintaining compatibility overloads.
- Ruling: Keep app font16 and use the parent spec's150x45 new-window grid; bound columns5–500, lines2–200, lineHeight1–3 and scrollback0–1000000 — preserves approved font while bounding new values and avoiding compressed glyph overlap — costs limiting unusual extreme configurations and changing initial window dimensions.
- Ruling: Capture session settings at request time and window grid once per new window; append configured args to the resolved default shell command when program is empty — prevents queued launches from changing under reload and preserves login-shell defaults — costs requiring an explicit executable to replace default login arguments.
- Ruling: Coalesce attached-view bells; visual defaults to a150ms15%-foreground flash, sound is explicit, and stale attachment callbacks are rejected — implements the spec bell choice without late sound/flashes or unbounded queued events — costs tuning visual strength/duration after native use and ignoring bells while detached.
- Ruling: Anchor array validation diagnostics to the containing key’s exact position instead of TomlJ’s misleading element start — TomlJ includes preceding whitespace/comments in array-element locations, and existing diagnostics identify fields — costs locating the invalid element within the named list rather than pointing directly to its token.

## Plan 4c execution rulings

Ruling: Exclude the detector dependency’s transitive net.java.dev.jna group while retaining existing pty4j-provided JNA and JNA-platform 5.14.0 — detector metadata requested an unpublished jpms variant during Gradle test resolution; this avoids replacing the established runtime — costs revisiting the exclusion if native dependency requirements change. Implementer must include runtime dependency evidence.

Ruling: Keep the latest OS appearance reading separately from the committed effective theme and use it when retrying selection/configuration — the plan’s transactional pseudocode otherwise forgets an OS change when LAF installation fails — costs one extra state field, but preserves visual rollback and permits Follow System retry without another OS event. Task 4 implements and tests it.

Ruling: Explicit Reload publishes one accepted state through the existing revision/close guard even when its value is unchanged, while polling and Settings still suppress unchanged values — equal-state suppression prevented the promised failed-theme retry — costs one deliberate UI reapplication per user reload and avoids a second unguarded completion path.


## Packaging verification notes

Task 1 was independently approved with no required fixes. Task 2 added RED/GREEN environment regressions; full `./gradlew check` passed with 508 tests, 507 passed and one known font skip. Final forced `./gradlew check :jasper-app:packageDist --rerun-tasks` passed all 13 tasks in 44 seconds: 508 tests, 507 passed, one known font skip, zero failures/errors; package and DMG checks passed. Whole-branch review fixes in `de4ec8c` were approved by scoped re-review with no open findings. [Verification, artifact checksum and execution decisions](superpowers/plans/2026-09-12-jasper-plan-4d-verification.md). One intermediate forced run observed a concurrent RIS snapshot NPE in `TerminalLiveOptionsTest` in unchanged terminal source/tests; targeted and subsequent full runs passed. The readiness branch reproduced and fixed this reset-lock race with a deterministic concurrent-reset regression, distinct from the known font skip. No claim of Windows or desktop runtime acceptance is made.

## Authorized publication and Windows CI test portability

The readiness branch was pushed after explicit user approval. Initial CI passed macOS/Linux but failed three Windows app tests. Test-only `a5f4dc1` uses the platform line separator for the exact logging fallback assertion and adds method-level Windows exclusions for two existing `/bin/sh` fixtures. Production code, package and measured runtime remain unchanged. Focused 15/15 and local full 575 passed/one known skip; `c793925` additionally fixes an off-EDT test resize race without weakening its grid/connector assertions; focused8/full575pass1skip and the confirming three-platform run passed. Details and the controller-review substitution caused by the agent service's thread limit are recorded in the [readiness handoff](terminal-readiness.md#publication-and-ci-follow-up).

App refactor execution ruling: coordinator shutdown preserves pane-owned session close; late arrivals close immediately. The controlled lifecycle tests use a child JVM through supported APIs because app tests cannot access terminal fake connectors.
