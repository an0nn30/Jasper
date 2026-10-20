# Cygwin probe implementation and evidence

2026-10-20. Task 1 is **in progress**, not complete. Worktree branch:
`codex/cygwin-probes`, base `6a6544185237f769b61e801ac115bedae7ab2b17`.

Implemented two opt-in headless Windows probes and portable evidence checks.
The MSI probe uses an isolated randomly named product and generated override
from the selected JBR resource; the ConPTY probe uses an explicit verified local
payload and a fresh scratch root. See [run instructions](../../../packaging/windows/cygwin/README.md).

Local evidence (macOS):

- RED: missing installer evidence was accepted by the stub; test failed.
- GREEN: evidence validator rejects absent/mismatched identities, missing/false
  lifecycle facts, timed-out events, relative/existing roots and root reuse.
- RED: template stub added no provisioning action; test failed.
- GREEN: XML hook test checks deferred, impersonated, checked execution and
  argument escaping; unsupported template shape is refused.
- Both Java probes compile against the current supported terminal facade.
- Explicit native tasks fail with `Cygwin installer probe requires Windows` and
  `Cygwin terminal probe requires Windows`, respectively.

Native results below establish the MSI hook and identify a resize/input-loss
defect in the Cygwin/ConPTY path. Production Tasks 2–8 remain gated on completing the
launch contract and native x64 Windows coverage.

Execution rulings:

1. Use the managed isolated worktree and copy only the approved uncommitted
   plan/spec; the original checkout has active unrelated editor changes.
2. Native probes run in the user-authorized Parallels Windows 11 ARM64 VM as
   its logged-in standard user. x64 JBR runs under emulation, not on native x64 Windows.
3. Probe evidence records decoded terminal events rather than unsupported raw
   output access. The probe now checks a full command-completion event, but native execution
   uses the VM; Task 1 cannot be marked complete from this harness alone.
4. The Windows foreground-job API is unavailable, so Ctrl+C uses a foreground
   Bash child that installs its INT handler before emitting the readiness title.
5. The baseline full headless check is red before the probe change. Keep the
   unrelated editor failures visible and leave their fixes to the editor work.

Baseline command: `./gradlew check -Pjasper.python=/opt/homebrew/bin/python3.13`.
Three pre-existing failures in `:jasper-app:test` (3,657 tests, 19 skipped):

- `ThemeMigrationCoverageTest.sourceDiscoveryIncludesTestFixturesAndHelperFalsePositives`
  — editor typography files missing from the expected inventory.
- `EditorDocumentPreservationTest.cancellingTheActualEditorsSettingsPageDiscardsInputAndKeepsDocumentConfiguration`
  — expected `13`, observed `13.0`.
- `SettingsPagesMockTest.theRemainingPagesMatchTheirMocks`
  — Editor General page separator pixels differ from the fixture.

No Jasper GUI, AOT training, production installation, merge or push performed.
Two rejected msiexec argument forms opened usage dialogs; both were dismissed
and cleanup confirmed the disposable products absent (1605).

Scoped independent review found two issues, both reproduced RED and fixed GREEN:
- The candidate MSI runtime used jpackage defaults that strip `bin/java`; explicit
  jlink options now retain it. A real portable jlink test verifies the executable.
- Setup's ordinary quiet mode opens a wizard; the probe now requests
  `--quiet-mode=hidden` and documents the minimum Setup version.

Final scoped checks: 30 portable assertions and the real runtime-image test pass.
Ctrl+C now submits its status-report command before starting the child and sends
only ETX after readiness, avoiding a terminal-input race. Shell startup stderr
is retained alongside decoded event evidence. Full OSC 133 completion/status
coverage passes in the native VM runs recorded below.

Final full check (`--continue`): 11,643 Java tests, three baseline failures,
zero errors and 25 skips; native launcher/CLI and architecture checks passed.
The initially supplied Python path was unavailable. Re-running `:checkThemeTool`
with `/Users/dustin/.local/share/uv/python/cpython-3.13.15-macos-aarch64-none/bin/python3.13`
passed all 71 tests. The three Java failures above remain unchanged. No green
whole-repository result is claimed.

## Native VM evidence

Windows 11 ARM64, JBR SDK `25.0.4.1+1-b583.48` (aarch64 and amd64 under
emulation), WiX 3.14.1. Both MSI lifecycle runs passed: deferred impersonated
helper identity, installed-file availability, synchronous completion, forced
failure/rollback (1603), silent install, repair, upgrade and uninstall. The
outside-install sentinel survived. Reports: `reports/arm64-03/installer.json`
and `reports/x64-emulated-01/installer.json` in the VM evidence directory.

Official Setup 2.937 verified with the Cygwin signing-key fingerprint
`56405CF6FCC81574682A5D561A698DE9E2E56300`. Executable SHA-256:
`2c9f2fb56e1fb687b5d9680afa8f8b06e6214f0e483096af0eae1946431226c5`.
The kernel.org Cygwin mirror supplied signed metadata and the fixed package
closure. Local-install returned 0 without elevation. Inventory: 114 packages,
87,363,659 cache bytes, approximately 831,238,000 installed bytes (per-root
configuration accounts for small differences). Key versions: Cygwin 3.6.11-1,
Bash 5.2.21-1, coreutils 9.0-1, Git 2.51.0-1, OpenSSH 10.6p1-1, curl 8.23.0-1.
These are probe inputs, not a release lockfile or redistributable payload.

Native RED/GREEN findings:

- msiexec rejects Java's whole-property quoting for an installation path with
  spaces. `cmd /d /s /c` preserves `INSTALLDIR="value"`; the probe rejects shell
  expansion characters. Both complete MSI lifecycles passed with that invocation.
- Redirecting interactive Bash stderr hid command echo from OSC 133 capture.
  Restore stderr after sourcing startup files; full command/status capture passes.
- In controlled same-root diagnostics, `bash --version` or `ssh -V` preflight
  before ConPTY causes the later size query to time out; Git/curl preflight does
  not. A fresh child JVM passes that diagnostic. The harness now reflects the
  planned separate provisioning-helper/app processes.
- A fresh ARM64 run still timed out at resize with that separation, while the
  fresh x64-emulated run passed startup, OSC 7/133, cwd, Unicode, resize, Windows
  exit status and Ctrl+C. Adding an initial `stty size` query before resizing
  passes the previously failing diagnostic. The final harness checks that
  initial grid explicitly. Repeated-resize testing below supersedes the earlier
  interpretation of this as an initialization-only problem.

Final disconnected fresh-install runs **passed** with both ARM64 JBR and x64
JBR under emulation. Parallels net0 was disconnected; the guest reported its
physical Ethernet adapter disconnected (the Tailscale virtual adapter remained
administratively up). The adapter was restored in a finally block and its
connected state confirmed. Both runs passed startup, OSC 7/133 command status,
cwd, Unicode, initial dimensions, resize, Windows exit status and Ctrl+C.
Reports: `reports/arm64-offline-01/terminal.json` and
`reports/x64-emulated-offline-01/terminal.json`. Exact installed sizes were
831,238,039 and 831,238,278 bytes respectively.

VM evidence is retained under
`C:\Users\dustin\vmtask\cygwin-probes-20261020`. An exported copy is in the
worktree's ignored `jasper-app/build/cygwin-probes/native-evidence.zip`, with
normalized files under `native-evidence/` and the adapter execution record in
`native-evidence/offline-execution.log`. It includes failed runs, generated WiX
overrides, installer/Setup logs, package databases and terminal evidence.

Task 1 remains in progress: native x64 Windows coverage and the production
resize/input reliability are outstanding. Full-screen interactive acceptance,
production Bash-wrapper startup, and a release payload with matching sources
remain later implementation/release work. The initial-size query does not resolve the resize/input defect.

Scoped checks after native-driven fixes: 30 portable assertions, the real
runtime-image check, source hygiene and diff checks pass. Independent review of
the MSI quoting, stderr restoration, process split and initial-grid check found
no remaining actionable issues. The three baseline full-check failures above
remain outside this change.

## Repeated-resize investigation

The follow-up regression performs twenty immediate resize/write cycles and
waits for both each command result and the subsequent Bash prompt. The original
initial-size query is retained as a dimension assertion, not a readiness fix.
The stronger native check fails: Jasper sends `probe_title ...` in full, but the
returned console echo and Bash error contain `robe_title ...`. This is input
loss, not a hung `stty` command. Headless rendering of the terminal confirms it.

Temporary boundary instrumentation records complete UTF-8 writes into pty4j and
the truncated echo coming back, with no intervening terminal-query reply.
Cygwin strace records SIGWINCH/select processing at the failing resize. This
localizes the loss downstream of Jasper's PTY write; an exact upstream code fix
has not been established.

Controlled scratch diagnostics reproduce the failure with Cygwin 3.6.11-1,
3.6.9-1 and the signed upstream test runtime
3.7.0-0.631.g7fd5f9c932c0. The older and test DLLs ran in separate scratch copies;
the original runtime hash was unchanged. Initial `stty size`, a fresh JVM,
prompt synchronization, ignoring WINCH in Bash, and a Cygwin `script` bridge do
not eliminate repeated-resize loss. The system-ConPTY diagnostic also fails
(earlier, while submitting the first command), so it is not an accepted fix.
None of these diagnostics changes Jasper's production launch behavior.

The previous disconnected single-resize results remain historical evidence for
Setup and those individual runs; they do not clear the production terminal gate.
Task 1 remains open. Before Tasks 2–8 can ship, either an upstream runtime fix
must pass the regression or the launch design must be revised and validated.
Native x64 Windows remains a separate release-validation gap.

The checked-in strengthened probe fails on the first resize from fresh installs
with both ARM64 JBR and x64 JBR under emulation (`arm64-stress-01` and
`x64-emulated-stress-01`). Scoped verification passes 31 portable assertions
and the runtime-image test. Independent review corrected a stale README claim
and increased the child timeout to cover all per-event deadlines (RED/GREEN).
The user authorized investigating a dedicated POSIX PTY relay; that experiment
is not yet an accepted production launch mechanism.


## Authorized POSIX PTY relay investigation

The native frontend uses ConPTY console APIs and a detached Cygwin backend with
`forkpty`. A private framed input pipe carries bytes and resize events; output
is raw PTY data. A Windows kill-on-close job contains the backend and descendants.
The initial Java/JNA frontend was replaced by native ARM64 and x64 frontends,
so production adoption would not add a JVM for each terminal.

The native ARM64 diagnostic passes 100 immediate resize/write cycles plus
Unicode, startup profiles, working directory, OSC events, native `cmd.exe`,
Ctrl+C and normal exit. The first fresh ARM64 checked-in run also passes twenty
cycles (`arm64-native-relay-01`). Both frontend builds pass split-surrogate and
argument-quoting self-tests. Native Windows backpressure-close testing passes.
The x64 diagnostic using the earlier Java frontend passed 100 cycles; that is
historical experiment evidence, not native-frontend x64 acceptance.

Independent review found missing output wrapping, malformed frames reporting the
child's successful shutdown status, partial frames blocking child-exit handling,
and cleanup potentially targeting a concurrent probe. The fixes enable wrapping,
retain a separate protocol-error result, poll incremental nonblocking frame reads,
and copy the cleanup helper to a unique per-run path. The malformed-frame test
failed before the fix; protocol checks now pass on macOS and Windows, including
partial-header child exit and closed output. Windows image deletion retries the
brief mapping-release delay after a successful backpressure-close check.

Production integration still requires versioned prebuilt artifacts, installer
staging, the actual Bash integration wrapper, interactive acceptance and native
x64 Windows coverage. No shipped shell launch behavior changes in this experiment.


Final hardened native frontend results: ARM64 passes twenty cycles and the new
cursor-position wrapping assertion (`terminal-native-relay-arm64-02`, session
rerun after correcting OSC evidence encoding). The fresh x64-emulated install
passes the same complete probe (`reports/x64-native-relay-02/terminal.json`).
Both use the native frontend and detached Cygwin backend, not the earlier Java
frontend. Six reports/session evidence files were exported to
`jasper-app/build/cygwin-probes/resize-investigation/`. The failed initial wrapping
report remains historical; its semicolon-bearing OSC title truncated the evidence.
Final scoped Gradle checks pass: 31 portable assertions, helper runtime check and
native protocol checks. Source hygiene and `git diff --check` pass.


## Production integration — 2026-10-20

Actual `CygwinLaunch.prepare` and bundled Bash integration pass in fresh `production-launch-arm64-03` and `production-launch-x64-03` fixtures in the authorized Windows VM: startup/profile/cwd, OSC 7/133, Unicode, twenty immediate resize/input cycles, wrapping, native cmd, Ctrl+C and clean exit. A renamed external executable loading the owned Cygwin DLL is correctly excluded from concurrent management. Lifecycle verification passes 34 checks on each guest JBR and 35 portable checks (the extra symlink case is non-Windows). x64 execution is emulated on ARM64 Windows.

Independent review's six findings were fixed with regressions: abandoned manager requests, interrupted initialization, saved-profile startup fallback, case-insensitive environment precedence, stale staged artifacts and retained manager runtime cleanup. Manifest, provisioning, resolver (five tests), user-install (30 checks), launch and focused UI checks pass. Full headless check reports the same three baseline editor failures: ThemeMigrationCoverageTest, EditorDocumentPreservationTest and SettingsPagesMockTest.

Remaining: approve the seed lock and source/license inventory (205 candidate archives retained and verified, 114 packages/91 source archives), then run real seeded production MSI/user-install and package-management acceptance. Native x64 Windows and interactive-app validation remain release checks. Windows distribution staging requires the approved lock and retained snapshot; this milestone is not a completed distribution.

Production seed and real installer/Gradle acceptance are complete on native ARM64; see the [final verification record](2026-10-20-jasper-cygwin-verification.md). Native x64 and interactive-app acceptance remain release gates.
