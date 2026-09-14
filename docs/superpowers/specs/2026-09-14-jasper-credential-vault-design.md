# Jasper credential vault — design

**Date:** 2026-09-14. **Status:** Approved in conversation; awaiting the user's review of this written spec before an implementation plan is written. No implementation yet.

**Branch:** `codex/credential-vault`, based on main `5bc288f` (rebrand + vanilla Swing).

## Decision record

The user asked for a credential vault that stores usernames, passwords and SSH keys for later use by SSH and SFTP, with a lock icon in the status bar and an entry under a Tools menu, showing a categorized list and a way to add credentials. `/Users/dustin/projects/TermLab/plugins/vault` was supplied as the reference for the non-UI architecture.

Investigation found `codex/rail-vault-implementation` (worktree `.worktrees/rail-vault`, 44 commits, based on pre-rebrand `a42c20f`), where Codex had already built a vault core, native keychain adapters, a manager UI, a window rail and an SSH client on the `dev.moray` packages with FlatLaf. Its last recorded check was 777 tests passing. Its core improves on TermLab in the ways TermLab's own comments flag as weaknesses: secrets are `char[]`/`byte[]` end to end, imported SSH keys are copied into the encrypted vault, locking is generation-safe, and file publication is atomic with conflict detection. A trial merge against main produces 76 conflicts, nearly all directory renames and FlatLaf removal.

Decisions the user made in this session:

1. **Port the core package; build the UI new.** Copy the branch's `dev.moray.app.vault` package (main and test sources) onto main as `dev.jasper.app.vault`, as a rename-and-copy rather than a git merge. Build the UI against the current vanilla Swing chrome. The rail and SSH code stay on the branch for the SSH phase.
2. **Credential types: logins and SSH keys only.** No API keys or secure notes in this delivery. The payload format is unchanged.
3. **Keep the full unlock policy** from the [2026-09-13 product contract](2026-09-13-jasper-vault-product-design.md) on the design branch: startup locked, master password, optional remembered device access in the OS keychain (7 days default, configurable), inactivity auto-lock (15 minutes default, configurable, 0 disables), auto-lock keeps remembered access, explicit lock clears it.
4. **Manager follows the supplied mocks in structure only**, rendered with standard Swing components under the active look and feel. The mocks' custom dark styling is not the target.
5. **Entry points:** a status-bar padlock that toggles lock/unlock, and a Tools menu with Credential Vault… and Lock Vault / Unlock Vault…. Both commands are in the command palette.

The product contract's earlier entry points (rail key icon, toolbar vault control) and its "Used by" / "Associated hosts" content are superseded or deferred as described below. Everything else in that contract still applies.

## Scope

In scope: the ported core, the application-owned controller and activity tracker, status-bar and Tools-menu entry points, the manager window with sidebar, table, editors, and its dialogs (unlock/create, add login, import key, settings), headless tests, rendered previews and documentation.

Out of scope: SSH, SFTP, the window rail, host associations, API keys, secure notes, password change or rekey, export, sharing, sync, key generation. No consumer of the vault ships in this delivery; the consumer API is exposed for the SSH phase.

## Architecture

### Module and packages

Everything lives in `jasper-app`. No new module, no plugin framework, no terminal dependency. The single interface, `DeviceAccessStore`, has four real implementations (macOS Keychain, Windows Credential Manager, libsecret, in-memory test store), satisfying the repository's two-implementation rule.

**Ported core, package `dev.jasper.app.vault`** (17 main files, 10 test files). Copied verbatim except for:

- package rename `dev.moray.app.vault` → `dev.jasper.app.vault`;
- file magic `MORAYVLT` → `JASPRVLT` (8 bytes; the 72-byte header layout is unchanged);
- keychain label "Moray remembered vault access" → "Jasper remembered vault access";
- worker thread name `moray-vault-worker` → `jasper-vault-worker`.

No user vault has ever been written by either brand, so there is no migration. The public surface stays as it is: `VaultService`, `VaultSnapshot` (metadata only; empty lists while locked), `VaultSettings`, `CredentialMaterial` (`AutoCloseable`, wiped on close), `DeviceAccessStore`, `DeviceAccessStores.system()`. `VaultService.setHostReferenceGuard` is retained for the SSH phase; this delivery installs no guard.

The [technical design](2026-09-13-jasper-vault-technical-design.md) on the design branch remains the authority for the file format, payload limits, atomic publication, imported-key rules, locking generations and remembered-access payload. This spec does not restate it.

**Dependencies added to `jasper-app`:** `org.bouncycastle:bcprov-jdk18on:1.85.2`, `org.bouncycastle:bcpkix-jdk18on:1.85`, `org.apache.sshd:sshd-common:2.18.0` (private-key parsing only), and `net.java.dev.jna:jna:5.14.0` declared explicitly at the version pty4j already brings transitively. No `sshd-core`.

**Files.** `vault.enc` and its nonsecret sidecar `vault.enc.device` in the existing `AppDirs.root()`, next to `config.toml`. POSIX permissions 0600 for files and 0700 for a newly created directory; Windows owner-only ACL. These rules are already implemented in the ported `VaultFiles`.

### Ownership

- `JasperApplication` constructs one `VaultController` in its constructor and closes it in `shutdown()`. Service construction reads only the encrypted header and the sidecar; it never touches the keychain, so startup cost is a file stat.
- Every `TerminalWindow` receives the controller. `WindowContent` subscribes to `onChange` to repaint its padlock and retitle its menu item, and unsubscribes on close. The controller registers each window's content as an activity root.
- One manager window is created lazily by the controller and reused across windows. Closing it hides it; it never locks or unlocks. Reopening reads the current snapshot.
- Headless construction (`GraphicsEnvironment.isHeadless()`) skips the AWT activity listener and the native frame; tests drive the panel and forms directly.

## Components

All UI classes are package-private in `dev.jasper.app`, matching the rest of the app.

**`VaultController`** — ported from the branch and trimmed. Keeps: the single daemon worker thread, generation counter, dialog presentation hook, busy state, clipboard access for copy, and the `onChange` listener list. Drops: rail hooks, `setHostUsage`, host-usage map. Adds: `openManager()`, `toggleLock()` (unlock dialog when locked, `lock(EXPLICIT)` when unlocked), `snapshot()`, `resolveLogin(id)`, `resolveKey(id, username)`.

**`VaultActivity`** — ported as is. An `AWTEventListener` counts key presses, mouse presses, moves, drags and wheel events whose source lies inside a registered root (windows, manager, dialogs, popups via their invoker). A 1-second Swing `Timer` calls `checkInactivity()` and reports a lock transition through `onChange`. Repaints, focus changes, terminal output and background work never count.

**`WindowStatusBar`** — gains a padlock `JButton` in the right segment, before the configuration button. The icon is a `PadlockIcon` (Java2D, 12 px, closed or open, painted in `Label.foreground`); the OldGnome2 set has no padlock and carries no license record that would justify adding more artwork from it. Tooltip: "Credential vault locked. Click to unlock." / "Credential vault unlocked. Click to lock." with "Auto-lock in N min" appended when unlocked and auto-lock is enabled. Accessible name mirrors the tooltip. `getText()` (used by tests and accessibility) is unchanged.

**`WindowChrome`** — adds a Tools menu after Tab with two new `ActionId`s:

| ActionId | Menu title | Behaviour |
|---|---|---|
| `VAULT_MANAGER` | Credential Vault… | `controller.openManager()` |
| `VAULT_LOCK` | Lock Vault / Unlock Vault… (toggles with state) | `controller.toggleLock()` |

Neither has a default key binding. `WindowCommands` registers both in the palette with keywords `vault`, `credential`, `password`, `ssh key`, `lock`, `unlock`, and refreshes the toggle title in `refresh()`.

**`VaultManagerWindow`** — a thin `JFrame` boundary ("Credential Vault", application icon, `HIDE_ON_CLOSE`, Escape closes only when no dialog is open) around **`VaultManagerPanel`**, a lightweight component. Panel structure, top to bottom:

1. Toolbar row: search `JTextField` (filters name and username, case-insensitive), Add credential, Import key, horizontal glue, Settings, Lock vault. Toolbar buttons and sidebar entries share Swing `Action`s.
2. Horizontal `JSplitPane`: left, a `JList` sidebar with two labelled groups — VAULT: All credentials, Logins, SSH keys, each with a count; MANAGEMENT: Settings, Import key. Selecting a MANAGEMENT entry runs its action and returns the selection to the previous VAULT entry. Right, a vertical `JSplitPane` of the table over the editor.
3. Table: `JTable`, single selection, columns Name, Username, Authentication (Password / SSH key / SSH key + Password / for keys: the algorithm). Rows sorted by name, case-insensitive. The mock's "Used by" column is omitted until hosts exist.
4. Editor area: `VaultLoginForm` or `VaultKeyForm` depending on selection; an empty-state label when nothing is selected; the unlock/create form replaces the whole split pane while locked or absent.
5. Status strip: "N credentials", "Auto-lock N min" or "Auto-lock off", and on the right "Device access until <date time>" or "No remembered device access". Values come from the snapshot, never from the keychain.

Sizing: preferred content about 900 by 600 logical pixels; minimum width lets the editor scroll rather than forcing the window wide.

**`VaultLoginForm`** — heading with the login name; fields Name, Username, SSH key (`JComboBox` of None plus key names, showing algorithm), Password (`JPasswordField` with Reveal and Copy buttons; shows "Not set" placeholder when absent). Below the key combo: "<algorithm> · shared by N logins" for the selected key. Buttons Revert and Save at the lower right; Save enabled only when the draft is dirty and valid (nonblank name and username, and a key or a password). Delete is a toolbar-less action in the editor's heading row.

**`VaultKeyForm`** — heading with the key name; fields Name (editable), Algorithm, Fingerprint, Public key (read-only text area with Copy), "Used by N logins". Revert, Save, Delete. Private material is never shown.

**Dialogs owned by the manager** (modal to the manager window only; terminals stay usable):

- `VaultUnlockForm` — shown inline in the manager when locked and as a dialog when invoked from the status bar or menu. Create mode when no vault file exists: Master password, Confirm, Remember on this device checkbox with duration in days. Unlock mode: if the sidecar reports unexpired remembered access, the Unlock button first tries `unlockRemembered()` and shows the expiry; otherwise, or on a miss, the password field is shown in the same form with the Remember checkbox. Inline error label; buttons disabled while the worker runs; Cancel always enabled.
- `VaultAddLoginDialog` — the mock's Add credential form: Login name (initial focus), Username, SSH key (None), Password; Import SSH key… at bottom left; Cancel and Add login at bottom right. Escape and close cancel. Import from here selects the imported key and keeps the rest of the draft.
- `VaultImportForm` — Choose file… (bytes read into memory, 1 MiB cap), Name, Passphrase (asked when `inspectKey` reports an encrypted key), then a preview of algorithm and fingerprint; Import commits. A duplicate fingerprint selects the existing key and says so.
- `VaultSettingsForm` — Auto-lock minutes (0–1440, 0 disables), Remembered duration days (1–365), current remembered expiry, Forget this device button (revokes without locking). Remember is disabled with a reason when `DeviceAccessStore.available()` is false.

**Consumer API for later phases.** `VaultController.resolveLogin(id)` and `resolveKey(id, username)` return `CredentialMaterial` for worker-thread callers, and `snapshot()` supplies metadata for pickers. Nothing in this delivery calls them.

## Data flow

**Unlock.** Click runs on the EDT; the controller opens the unlock form. Remembered unlock and password unlock (Argon2, roughly 250 ms) run on the worker with buttons disabled and Lock still available. Completion posts to the EDT, the controller checks its generation, then fires `onChange`. Every window repaints its padlock and retitles Lock Vault / Unlock Vault…; the manager, if open, replaces the unlock form with the split pane.

**Lock.** Explicit lock (icon, menu, manager toolbar) calls `lock(EXPLICIT)`: the service seals in-process immediately, then the worker clears the sidecar and keychain entry. Auto-lock calls `lock(AUTO)` and leaves remembered access in place. Both clear drafts and password reveals without prompting; the editor shows "Unsaved edits were discarded when the vault locked" until the next selection. Pending results from an older generation are discarded.

**Manager edits.** Table selection loads the editor from the snapshot. The password field fetches the real value only for Reveal or Copy, and wipes the array when the field is cleared or the vault locks. Save runs `saveLogin` or `renameKey` on the worker; the service persists atomically before returning, the snapshot revision bumps, the table refreshes and keeps its selection by ID. Revert reloads from the snapshot. Changing selection or filter, or closing the manager, with a dirty draft asks Save / Discard / Cancel.

**Import.** `inspectKey` on the worker; a passphrase prompt on demand; fingerprint preview; `importKey` on confirm. Source file untouched; no plaintext temporary file.

**Delete.** Login delete asks once. Key delete is refused by the service while logins reference it; the dialog reports the count.

**Search and categories.** Search and the sidebar category filter compose; counts in the sidebar reflect the unfiltered snapshot.

## Error handling

- Wrong password and corrupted ciphertext share one message, "Password incorrect or vault authentication failed", inline in the unlock form. Damaged header, oversized or unreadable file show a distinct message; the file is never modified on a failed read.
- Save conflict (file changed on disk) fails the save, keeps the draft, and asks the user to lock and unlock to reload.
- Keychain unavailable disables Remember with a readable reason; password unlock keeps working. Failed revocation on explicit lock keeps the vault sealed and shows a warning that remembered access could not be cleared. Nothing falls back to a plain settings file.
- Error text is bounded and excludes secret bytes, key material and native exception payloads. Nothing secret is logged.

## Threading

KDF, encryption, persistence, key parsing and keychain calls run on the controller's single worker thread. Swing state and dialogs stay on the EDT. The service's generation counter and the controller's own generation check prevent a late unlock, save or import from repopulating a locked UI. Monotonic time (`System.nanoTime`) drives inactivity; wall time drives remembered expiry. Tests inject both.

## Testing

- **Ported core tests** (storage, service, key import, three native adapters against fake native calls, store factory) run unchanged apart from the package rename.
- **Headless UI tests** drive `VaultManagerPanel`, the forms and `VaultController` against a temporary directory, the in-memory `DeviceAccessStore` and a mutable clock: create; password unlock; remembered unlock and expiry fallback; add, import, select, save, revert; duplicate key; refused key delete; failed save keeps the draft; dirty-draft prompt; lock during a pending unlock; auto-lock preserves remembered access while explicit lock clears it; manager reopen never unlocks; search and category filtering; controller close mid-operation.
- **Chrome tests** extend `WindowChromeTest` and the status-bar tests: Tools menu contents, toggle title, padlock state after `onChange`, palette lookup of both commands, activity roots registered and removed with windows.
- **Rendered previews** of manager (unlocked, locked, empty), add-login, import, and settings contents under Metal, Motif and Nimbus at 1× and 2×, produced by the existing headless preview tooling and committed under `docs/design/credential-manager/` next to the mocks.
- **User-run acceptance:** real macOS Keychain enrollment, remembered unlock and revocation; native focus and dialogs; window placement. Agents never launch the GUI.

## Documentation

Add `docs/credential-vault.md` (user guide: create, unlock, remember, auto-lock, add, import, settings, file locations, what "explicit lock" clears) and update `docs/STATUS.md`, `docs/configuration.md` (no new config keys; vault settings live inside the vault) and `AGENTS.md` (vault worker and activity conventions, the ported-package origin).

## Follow-on

The implementation plan is written with `superpowers:writing-plans` and executed with `superpowers:subagent-driven-development`, one commit per task, on `codex/credential-vault`. Suggested task order: dependencies and core port with its tests; controller and activity; chrome entry points; manager panel and editors; dialogs; previews and docs; whole-branch review.
