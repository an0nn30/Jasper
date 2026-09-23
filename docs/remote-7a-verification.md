# Remote plan 7a — execution and verification

Implemented natively in `/Users/dustin/.codex/worktrees/remote-7a/moray`, branch
`codex/remote-7a`, from current-main commit `ac02f94` plus the approved spec/plan.
Eight task commits are followed by one review-fix commit. No merge, push, GUI launch,
benchmark, keychain access or external SSH connection was performed.

## Verification

`./gradlew check :jasper-app:installDist` passed on JBR 25. XML totals: 1,604 tests,
1,601 passed, three expected skips, zero failures/errors. Remote: 56/56.
Checks include architecture boundaries, JavaDoc/doc examples, SDK app/testkit contracts,
real loopback SSH authentication (password, agent, encrypted Vault key), shared channels,
ProxyJump, cancellation, resize, natural exits, plugin loading/bundling and headless UI.
Source hygiene and `git diff --check` passed. Headless panel/editor renders were inspected.
Native desktop, system-agent/keychain and Windows acceptance remains user-run in
[the guide](remote.md#native-acceptance). Tunnels and SFTP remain plans 7b/7c.

## Independent review

One read-only review covered `ac02f94..41c7e25`; verdict: with fixes, no Critical,
nine Important and two Minor groups. No second review was requested. Each finding has
a failing regression followed by a passing fix and the full suite above:

| Finding | Covering regression |
| --- | --- |
| Queued mutations overwrite earlier successful edits | HostStoreTest.queuedMutationsComposeWithoutLosingEarlierEdits |
| Unpolled broken/unreadable external edits get overwritten | HostStoreTest.saveChecksDiskBeforeOverwritingAnUnpolledBrokenEdit; unreadableFileIsReportedAndKeepsLastGoodHosts |
| Optional Vault linkage prevents standalone startup | BundledSamplePluginTest.remoteLoadsWithoutTheOptionalVaultPlugin |
| Multiple legitimate host keys rejected | KnownHostsTest.acceptsAnyRecordedKeyInOneFileButRejectsConflictInAnother |
| Trust fail-open and prompt-time revocation/conflict | KnownHostsTest.trustRechecksBothFilesAndCannotUnrevokeAKey; ownDirectoryIsNotAnEmptyTrustFile; ownDanglingSymlinkIsNotAnEmptyTrustFile |
| Silent agent retains blocked work | AgentClientTest.closingMinaAgentUnblocksASilentSocket |
| Import violates ordering/quoting/comments | SshConfigTest.appliesFirstValuesInFileOrderAcrossMatchingBlocks; readsQuotedValuesAndTrailingComments |
| Lost mutation errors and wrong editor ownership | RemotePluginTest.failedMutationsReportAfterTheEditorCloses |
| Hosts action cannot lazily toggle panel | FakePanelsAndWindowsTest.toggleCreatesLazilyThenReusesAndHidesTheWindowPanel; HostedUiTest.panelsAndRailReachTheModelWithContainedFactories; RemotePluginTest.hostsActionCreatesThePanelWithoutOpeningThePalette |
| Stale card and missing Enter/star actions | HostsPanelTest.clearsRemovedSelectionAndBindsEnterAndFavoriteClick |

The two minor groups were treated as documented interaction requirements and fixed.
No findings are deferred.

## Execution rulings

- Ruling: Native managed worktree on codex/remote-7a, based on current main with spec commits cherry-picked — preserves shell-lookup revert and native registration — cost: differs from plan example branch/path. New commits use accurate Codex coauthor attribution.
- Task 1: Ruling: save completion must discard its Read value to satisfy the Void queue contract — compilation defect in plan — no behavior change.
- Task 3: Ruling: validate key blobs before host filtering, reject unresolved own keys — planned corrupt-file test failed because unknown host lines escaped validation — unsupported own key types require fixing the file.
- Task 4: Ruling: test sockets use short unique /tmp paths with cleanup, Ed25519 fixture uses BC keys — macOS Unix path limit and MINA’s BC key adapter reject the plan fixtures — no production behavior change.
- Ruling: task-done exits on empty successful -q output (grep with pipefail); record prior verified completions here and use non-quiet Gradle for the helper — tooling defect only.
- Task 5: Ruling: use MINA connection context for real target, unwrap DNS causes; correct planned fixtures (fresh credentials per fetch, read full WINCH line, Vault auth for network failures, short socket path) — observed failures — tests now exercise intended behavior.
- Task 5: Ruling: replace host-id bookkeeping with owned Shared references and cancellable I/O resources, UI callbacks, cycle validation, jump release and remote-exit release — added RED regressions exposed lifecycle gaps — broader implementation than draft, unchanged intended behavior. Disable MINA implicit config/key loading; remove authentication identities after auth; use reply-based keepalives to meet spec.
- Task 6: Ruling: exhaustive test switch includes Error rows; name validation precedes credential validation — plan compile/test defects — unchanged intended behavior.
- Task 7: Ruling: test-only owner-package UiTestAccess bridge, real editor showError method, fake-window activation and session-split prefix — planned tests crossed package access and used wrong fake assumptions — no public UI field expansion. Explicit plan corrections applied (duplicate loop, HostFile string access, panel-state path, supplier trust constructor).
- Task 7: Ruling: cancel host-key dialogs with their future and guard shutdown callbacks — added cancellation test RED→GREEN — prevents abandoned prompts and stopped-context calls. Optional Vault lookup tolerates an absent API class.
- Task 8: Ruling: bundled test expects five plugins and three status contributions, rail order independent — plan counts stale, dependency ordering changed — no behavior change. A transient concurrent Gradle output collision was resolved by one serial full rerun.
- Final: Ruling: add Panels.toggle and SDK 0.7.2 — the approved Hosts action cannot lazily open/toggle through the previous SDK — cost: Remote now requires SDK 0.7.2.
- Final: Ruling: promote stale card and missing Enter/star interaction to requirement failures — these are documented user actions, not cosmetic polish — cost: small additional Swing interaction regression and fix.
