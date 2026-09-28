# Windows startup verification — 2026-09-27

Branch: `codex/fix-windows-terminal-startup`.

## Reproduction and fix

A JBR 25 Java process launched through `cmd.exe` inherits the hidden environment
key `=C:`. `SessionLaunchOptions` rejected it with `invalid environment entry`,
preventing terminal process creation. Windows validation now follows the JDK rule:
allow an initial equals sign, reject any later equals sign and all NUL characters.
Other platforms retain their existing validation. Environment entries are preserved.

Before the fix, both `acceptsWindowsHiddenEnvironmentVariables` and
`powershellStartsWithInheritedWindowsDriveEnvironment` failed with the reported
exception. After the fix, the focused command passed:

```powershell
.\gradlew.bat :jasper-terminal:test --tests '*SessionLaunchOptionsEnvironmentTest' --tests '*PtyConnectorTest'
```

Ten tests: four passed, six Unix-only tests skipped. The Windows integration test
starts a real PowerShell child through the PTY, observes its output and terminal
capability variables, and checks its successful exit. Malformed entries remain
rejected. Independent review found no actionable issues. Source hygiene and
`git diff --check` passed.

## Full check and runnable build

```powershell
.\gradlew.bat check :jasper-app:installDist --continue
```

`installDist` completed and includes the updated terminal library. The launcher is
`jasper-app/build/install/jasper-app/bin/jasper-app.bat`; use JBR 25 as `JAVA_HOME`.
No GUI was launched. Manual acceptance: start the rebuilt app, open a PowerShell
tab, and confirm a prompt appears and commands work.

The full check is **not green**: app, Remote, Snippets, Vault, SDK testkit, and
terminal test tasks failed. The Remote worker was stopped after a thread dump
showed it stuck closing its virtual-thread executor in
`SftpControllerTest.obsoleteListingCannotReplaceNewHostAndManualNavigationDisablesOnlyThatPanesFollow`.
Consequently Remote's run is incomplete and there is no complete suite total.

Broader Windows issues include Unix-only path fixtures, symlink privilege failures,
and asset checksums affected by this checkout's `core.autocrlf=true`. All four
IntelliJ theme asset hashes match their pinned hashes after CRLF-to-LF conversion.
These additional issues are outside the startup validator fix; they have not all
been individually diagnosed. The log and Remote thread dump were retained locally
as `%TEMP%/jasper-windows-check.log` and `%TEMP%/jasper-remote-test-threads.json`.

## Known failed test cases — re-verified 2026-09-28

The 2026-09-27 run listed 43 failed test cases across app, Remote, Snippets, Vault, SDK testkit and terminal
(see this file's history).
The full `.\gradlew.bat check --continue` on 2026-09-28 (branch `worktree-wsl-hosts`, sessions on ConPTY, see the
next section) completed every module, Remote included, and only the cases below failed or are known to fail on this
machine. Everything else from the earlier list passed, including the three `SessionLaunchCoordinatorTest` cases
(now enabled on Windows), `ShellIntegrationSessionTest > aDirectoryReportedUnderAnotherHostIsRemoteUntilALocalOneFollows()`,
`ShellCommandTrackerTest > aRemoteReportClearsTheLocalDirectoryAndALocalOneClearsTheRemote()` and every Snippets,
Vault and SDK testkit case.

Failed in that run (symlink privilege, Unix path fixtures):

- `ShellIntegrationScriptsTest > installReplacesASymlinkRatherThanWritingThroughIt()`
- `EditorsTest > listsTablesAndExecutablesEditTheirValues()`: the Unix `"/bin/zsh"` fixture becomes `"\bin\zsh"`
  (pre-existing, not caused by the WSL branch).
- `FileEndpointTest > localContract()`
- `FileEndpointTest > rejectsIntermediateLinksWithoutTouchingTheirReferents()`
- `SftpEndpointTest > sftpV3ContractAndSubsystemCloseKeepsSharedSessionAlive()`
- `TransferCoordinatorTest > folderTransferPreservesEmptyDirectoriesLinksAndUsesBoundedQueue()`
- `KnownHostsTest > ownDanglingSymlinkIsNotAnEmptyTrustFile(Path)`
- `FileOperationTest > recursiveDeleteNeverFollowsLinksAndTransferReservationsBlockIt(Path)`

Timing-sensitive under full-check load (passed in that run and alone; failed once in an earlier full run):

- `TerminalAppIntegrationTest > rowResetRejectsACompletedSearchAlreadyQueuedAheadOfReconciliation(String) [3] "reflow"`:
  uses `FakeConnector`, no PTY; tripped its 5 s wait while the whole build was running.

## ConPTY and WSL shell profiles — 2026-09-28

Branch: `worktree-wsl-hosts` (plan `docs/superpowers/plans/2026-09-28-jasper-wsl-a-profiles.md`).

Sessions on Windows now run on ConPTY (`PtyChild.builder` sets `setUseWinConPty(true)`). Headless results on this
machine:

- `PtyConnectorTest > conPtyPassesTitlesAndWorkingDirectoryReportsThrough()`: PASS. OSC 2 titles and OSC 7
  reports reach Jasper through ConPTY.
- `PtyConnectorTest > powershellStartsWithInheritedWindowsDriveEnvironment()`: PASS; the `=C:` handling is
  unchanged.
- `ShellIntegrationSessionTest > aDirectoryReportedUnderAnotherHostIsRemoteUntilALocalOneFollows()` and
  `ShellCommandTrackerTest > aRemoteReportClearsTheLocalDirectoryAndALocalOneClearsTheRemote()`: PASS. They do not
  use a PTY, so ConPTY did not change them; they are off the known-failure list.
- `SessionLaunchCoordinatorTest`: its child signals readiness with an OSC 2 title, which winpty dropped, so it was
  disabled on Windows. Under ConPTY all three cases pass (three consecutive runs); the Windows disable is removed.
- `ShellProfilesTest > anExecutionAliasCountsAsExistingThoughItCannotBeFollowed()` is skipped here: creating its
  symlink needs a privilege this account lacks. It runs on macOS and Linux.
- Full `check --continue`: 3458 tests, 237 skipped, 8 failed (app 1176/144 skipped/2 failed, terminal 391/19/0,
  Editor 1172/13/0, Remote 271/54/6, Vault 139/7/0, Buddy 163/0/0, History 40/0/0, SDK 26/0/0, SDK testkit
  58/0/0, Sample 11/0/0, Snippets 11/0/0) — every failure is on the list above.

Native acceptance for the user (no GUI was launched):

- [ ] A PowerShell tab: prompt, typing, resize (`$Host.UI.RawUI.WindowSize` follows), the title, no stray
      cursor-visibility or title sequences on screen, colours intact.
- [ ] Command Prompt and, if installed, PowerShell 7 from Settings › Terminal › Shell › Profile.
- [ ] A WSL 1 tab and a WSL 2 tab: named after the distribution; `echo $TERM $COLORTERM $TERM_PROGRAM` prints
      `xterm-256color truecolor Jasper` (if `TERM` differs, record it: `WSLENV` already forwards it).
- [ ] A new WSL tab from a window in your Windows home starts in the Linux home (`pwd` prints `/home/...`, not
      `/mnt/c/Users/...`).
- [ ] With the manual `source` line from configuration.md in `~/.bashrc`: the status bar shows the Linux directory
      as a `\\wsl.localhost\...` path; `cd /mnt/c/Users` shows `C:\Users`.
- [ ] A split of a WSL tab starts in the same Linux directory; New Tab starts the default profile.
- [ ] Choosing a WSL distribution as the Profile and applying: the next New Tab starts it; an uninstalled profile id in
      `config.toml` starts PowerShell and shows a warning in the status bar.
- [ ] ↻ next to the Profile dropdown detects the profiles again without freezing the dialog.
