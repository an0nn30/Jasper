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

## Observed failed test cases

- `IntellijThemesTest > manifestPinsTheClassicLightChainAndTheDefaultSchemesToTheirUpstreamFiles()`
- `SessionLaunchCoordinatorTest > shutdownWaitIncludesAcceptedLaunchAndLateChildExit()`
- `SessionLaunchCoordinatorTest > admittedSessionExitsRemainTrackedUntilPaneOwnerClosesThem()`
- `SessionLaunchCoordinatorTest > lateSessionAfterPaneAndApplicationCloseIsNeverAttached()`
- `CommandHistoryTest > saveFailureIsLoggedAndDoesNotPreventMemoryRecency()`
- `LaunchSettingsTest > userEnvOverlayCanStillOverrideTheIntegrationDirectory()`
- `ShellIntegrationScriptsTest > installReplacesASymlinkRatherThanWritingThroughIt()`
- `ShellIntegrationScriptsTest > installWritesEveryFileOnceAndRewritesOnlyChangedContent()`
- `AppDirsTest > daemonFilesShareOwnTheirOwnSubdirectoryOfTheRoot()`
- `AppDirsTest > buddyStateLivesBesideCommandHistory()`
- `IntellijIconsTest > manifestPinsEveryBundledSvgToItsUpstreamPathAndHash()`
- `NamedIconsTest > manifestAndNoticeCoverAllStandardArtwork()`
- `AppContractTest > openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane()`
- `BundledSamplePluginTest > remoteLoadsWithoutVaultAndUsesHostIconsForEveryPlacement()`
- `HostedChooserTest > terminalOwnerSelectsMultipleFilesOrOneDirectory()`
- `HostedTerminalsTest > openingATabOrASplitReturnsTheNewPane()`
- `HandoffSocketTest > aLivenessProbeLogsQuietlyRatherThanAlarmingly()`
- `LaunchRequestTest > aRequestSurvivesEncodingIncludingPathsWithSeparatorsInThem()`
- `TerminalTitleTest > idleTitleUsesDirectoryAndLoginShellLikeITerm()`
- `WindowContributionsTest > aContributedScopeIsInThisWindowsPaletteItsShortcutSwitchesWhileOpenAndRequestsAreRouted()`
- `WindowTabsTest > tooltipsAndTheTabListStayLiteralAndBounded()`
- `WindowTabsTest > tooltipsCarryTheTitleAndTheLiveSelectShortcut()`
- `ConnectionsTest > failuresHaveTheSpecsMessages(Path)`
- `ConnectionsTest > cancellingSecondKeyRequestClosesFirstCopy(Path)`
- `ConfigImportTest > resolvesTokensOnceAndRejectsUnsupportedSyntax()`
- `ConfigImportTest > reimportPreservesIdentityAndOrganization()`
- `SshConfigTest > readsFromDiskWithGlobbedIncludes(Path)`
- `FileEndpointTest > localContract()`
- `FileEndpointTest > rejectsIntermediateLinksWithoutTouchingTheirReferents()`
- `SftpEndpointTest > sftpV3ContractAndSubsystemCloseKeepsSharedSessionAlive()`
- `TransferCoordinatorTest > blockedPayloadDoesNotBlockPauseAndResumeUsesOwnedPartial()`
- `TransferCoordinatorTest > folderTransferPreservesEmptyDirectoriesLinksAndUsesBoundedQueue()`
- `TransferCoordinatorTest > retryFailedCopiesFixedItemsAndLeavesSkippedItemsSkipped()`
- `TransferRecoveryTest > pauseCommitsShortSegmentResumeTruncatesOnlyOwnedTailAndRejectsEditedPrefix()`
- `KnownHostsTest > ownDanglingSymlinkIsNotAnEmptyTrustFile(Path)`
- `FileOperationTest > recursiveDeleteNeverFollowsLinksAndTransferReservationsBlockIt(Path)`
- `SftpControllerTest > explicitDirectoryLinkNavigationShowsResolvedPathAndDoesNotChangeNoFollowOperations()`
- `SnippetFileTest > parseIsLenientPerEntryAndStrictPerFile()`
- `ManagedSshKeyTest > readsIndependentLegacyFixture()`
- `FakeChooserTest > scriptsMultipleFilesAndDirectoryAndDiscardsClosedOwnerResults()`
- `FakeContractTest > openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane()`
- `ShellIntegrationSessionTest > aDirectoryReportedUnderAnotherHostIsRemoteUntilALocalOneFollows()`
- `ShellCommandTrackerTest > aRemoteReportClearsTheLocalDirectoryAndALocalOneClearsTheRemote()`
