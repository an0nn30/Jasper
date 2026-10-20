# Windows Cygwin integration probes

These are development probes for the approved bundled-Cygwin design. They do not
add Cygwin to Jasper yet. `check` runs their portable evidence checks; native
probes are explicit tasks and fail on non-Windows hosts.

## Portable verification

```text
./gradlew :jasper-app:verifyCygwinProbes
```

The task compiles both probes using JBR 25 and checks rejection of missing or
contradictory evidence, event deadlines, scratch-directory ownership, and the
candidate installer action. `verifyCygwinProbeRuntime` additionally builds a small
runtime and verifies its Java executable; `check` includes both tasks. Passing either task is not Windows acceptance.

## Installer lifecycle probe (Windows)

Use a standard-user Windows test account with JBR 25 and the WiX toolset supported
by that JBR on PATH. Give each run a new absolute scratch directory whose parent
already exists. No pre-existing tree is reused or removed.

```text
.\gradlew.bat :jasper-app:probeCygwinInstaller -Pjasper.cygwinProbeRoot=C:\probe-work\installer-01
```

This builds a separate randomly named `JasperCygwinProbe-*` MSI product, using a
fresh upgrade UUID. It tests failed-install rollback, silent install, repair,
upgrade and uninstall. A small installed Java helper runs as a deferred,
impersonated, synchronous action after InstallFiles. It records its Windows
identity, installed-file availability and completion time. Jasper is never
launched or installed by this probe.

The probe invokes msiexec through `cmd /d /s /c` to preserve its `PROPERTY="value"`
syntax. Shell-sensitive characters in scratch paths are rejected.

The candidate override is derived from the selected JBR's own `main.wxs`.
Unsupported template shapes fail with the original retained for inspection;
a successful portable transformation test does not establish JBR/WiX compatibility.
The generated MSI, template, verbose jpackage output, Windows Installer logs and
helper records remain in the scratch directory. The task attempts to uninstall
its probe product on failure. Inspect `cleanupExit`/`cleanupError` in the report;
1605 means it is already absent. If Windows Installer was interrupted, confirm
that the randomly named product is uninstalled before removing scratch files.
Never use these MSI paths to uninstall Jasper.

## Cygwin/ConPTY probe (Windows)

Prepare an official Cygwin offline download cache with Base and the packages in
the approved design. The payload directory must contain:

```text
payload\
    setup-x86_64.exe
    cache\                 # complete Setup download tree, including signed metadata
```

Use Setup 2.929 or newer (hidden quiet mode), and verify the Setup executable
against its official signature before supplying it.
The probe records its SHA-256 and version but does not implement the future
release-manifest verifier. Keep the signed upstream repository metadata: Setup
verification is left enabled. The probe selects local-install mode and disables
the Setup version check; use a disconnected test machine to establish offline
acceptance rather than inferring it from flags alone.

```text
.\gradlew.bat :jasper-app:probeCygwinTerminal -Pjasper.cygwinProbeRoot=C:\probe-work\terminal-01 -Pjasper.cygwinProbePayload=C:\probe-work\payload
```

The probe installs only into its new scratch root. It records Setup help/version,
exit status, installed package database, process commands and conversion output.
It then starts a fresh JVM for interactive Bash with the supported `TerminalSession` facade
and no Swing window. Tests cover a sourced startup profile, spaces/Unicode in
paths, explicit cwd, OSC 7, OSC 133 prompt and command-completion events,
Unicode input/output, initial `stty` dimensions and resize results,
a Windows command's exit status and Ctrl+C of a foreground child. Each terminal
event has a 15-second deadline. Setup and MSI operations have a ten-minute timeout.
No reboot-required outcome counts as success.

Terminal evidence uses decoded OSC title/directory/prompt events. The supported
session API does not expose raw output bytes, so this harness does not capture a
raw terminal transcript. Shell startup stderr is retained separately in
`bash-stderr.log`. This evidence limitation is recorded in the plan. Startup uses a probe rcfile that
sources `/etc/profile` and an isolated `.bash_profile`; it does not yet validate
Jasper's production Bash wrapper. The temporary home and Cygwin tree are retained
for diagnosis. Stop any remaining scratch processes before manually removing it.

## Results and handoff

JSON summaries are in `jasper-app/build/cygwin-probes/reports/installer.json` and
`terminal.json`. Both begin as failed and become passed only after all implemented
checks succeed. Reports include OS, architecture, JBR version and scratch path;
copy summaries and scratch logs separately for x64 and ARM64 runs, since another
run of the same task replaces its summary.

A run that fails before allocating scratch storage (wrong OS, missing payload or
invalid path) fails Gradle directly. Do not interpret an older report as the
result of a failed new invocation. Native results are recorded in the linked evidence document.

Sources for the candidate hook and Setup flags:
- https://github.com/openjdk/jdk/blob/jdk-25-ga/src/jdk.jpackage/windows/classes/jdk/jpackage/internal/resources/main.wxs
- https://cygwin.com/faq.html#faq.setup.cli
- https://sourceware.org/pipermail/cygwin-announce/2024-February/011525.html

Native testing found that direct Bash/SSH preflight commands can affect later
ConPTY behavior in the same JVM. Provisioning and terminal checks now use separate
processes, matching the planned helper/app boundary. An initial `stty size` query
establishes and checks the Cygwin console before resizing; the failure observed
when resizing before that query remains a production launch constraint. See
[the evidence record](../../../docs/superpowers/plans/2026-10-20-jasper-cygwin-probe-results.md).

The approved implementation plan requires native evidence before production
packaging or shell integration starts. Run both probes on Windows x64 and ARM64;
retain failures as evidence for revising the hook or launch mechanism.
