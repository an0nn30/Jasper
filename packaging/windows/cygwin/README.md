# Windows Cygwin integration

The production integration uses a persistent per-user root and prebuilt POSIX PTY
relay. `check` runs portable lifecycle and launch checks; native probes are explicit
tasks and fail on non-Windows hosts. The checked-in seed locks every binary, source
archive, notice and relay artifact.

## Using the bundled environment

Both the Windows installer and `installWindowsUser` provision the starter set offline
in `%LOCALAPPDATA%\Jasper\cygwin\root`. Choose **Cygwin Bash** in terminal shell
settings. **Manage Cygwin packages…** is available there and in the command palette;
close Cygwin sessions and other processes using this root before starting it.
Setup opens with the existing installation selected. Leave packages at **Keep** to
retain their versions; select additional tools or updates as desired. Downloading
additional packages requires internet access. The supervisor continues if Jasper exits.

App updates, repair, rollback, reinstall and uninstall preserve the entire Cygwin
root, including added packages, home files, `/etc` and repositories. An interrupted
initial install can be retried with its retained seed. Logs are under
`%LOCALAPPDATA%\Jasper\cygwin\logs`; a damaged ready root is repaired through Setup.
Portable ZIP users run `provision-cygwin.cmd` once from the extracted app directory.
To remove the environment deliberately, close all processes using it and remove
`%LOCALAPPDATA%\Jasper\cygwin` separately; the app uninstaller preserves it.

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

The terminal probe uses a separate JVM, checks initial dimensions, and waits for
both command results and the next Bash prompt. It then requires twenty immediate
resize/write cycles. Native testing reproduces first-character input loss despite
the initial size query; the production adapter therefore uses the POSIX PTY relay. See
[the evidence record](../../../docs/superpowers/plans/2026-10-20-jasper-cygwin-probe-results.md).

The user authorized production integration after relay validation on Windows ARM64
and x64 emulation. Native x64 Windows and interactive-app acceptance remain release checks.

## POSIX PTY relay

The user authorized this investigation after direct Cygwin console input lost
characters during resize. The experiment keeps the existing terminal facade:
ConPTY runs `ConPtyRelay.cpp` (native ARM64 or x64), which starts
`CygwinPtyRelay.c` detached from the Windows console. Framed input and resize
messages travel over private pipes; the Cygwin backend runs Bash on `forkpty`.
A Windows job owns the backend process tree and terminates it on frontend exit.

Build the backend explicitly with `:jasper-app:compileCygwinRelayProbe` and
`-Pjasper.cygwinProbeCompiler=<absolute Cygwin gcc.exe>`. Build the frontend from
an MSVC developer environment for the target architecture with
`:jasper-app:compileConPtyRelayProbe` and
`-Pjasper.cygwinProbeMsvc=<absolute cl.exe>`. Run its `--self-test` for UTF-16
and Windows argument quoting. These builds are opt-in probe tasks.

Pass `-Pjasper.cygwinProbeRelay=<absolute backend.exe>` and
`-Pjasper.cygwinProbeFrontend=<absolute frontend.exe>` to the terminal probe.
`verifyCygwinRelayProtocol` additionally takes
`-Pjasper.cygwinProbeShell=<absolute POSIX shell>` and checks resize, Unicode,
exit status, malformed/partial frames and pipe failure. The backend also builds
on macOS for these protocol checks. `RelayCloseProbe` accepts the backend,
owned Cygwin `bin` directory and frontend; it checks close under backpressure
using a unique temporary backend executable.

Production integration requires pinned prebuilt frontends for both Windows
architectures and one x86_64 Cygwin backend, with hashes, sources and notices.
The Gradle user installer must consume those artifacts without a native compiler.
The production `cygwin` shell profile uses the verified manager generation's relay.
`probeCygwinProduction` takes `jasper.cygwinProbeRoot` (new directory) and
`jasper.cygwinProbeRuntime` (existing probe Cygwin root). It copies the runtime into
an isolated fixture, then exercises `CygwinLaunch`, the bundled bootstrap, launch
leases and exclusion of a renamed external process loading the owned Cygwin DLL.
Its small fixture manifest tests launch validation; distribution acceptance uses the real seed.

`verifyCygwinManifest`, `verifyCygwinLifecycle`, and `verifyCygwinProvisioning` run
portable manifest, ownership, concurrency and preservation checks. `stageCygwinSeed` uses the checked-in seed lock. Its JDK-only acquisition task downloads
only missing, pinned HTTPS artifacts into `build/cygwin/downloads`; local signed metadata,
notices, Setup sources and relay binaries come from `seed-support`. No Python, GnuPG,
PowerShell, native compiler or separately installed Cygwin is needed for ordinary builds
or `installWindowsUser`. `-Pjasper.cygwinSnapshot=<retained snapshot>` selects a fully
offline source instead. Missing or mismatched artifacts fail the build. `cygwinToolJar` packages the shared
helper used by MSI, `installWindowsUser`, and `provision-cygwin.cmd`. Neither installer
compiles the relay. The Windows settings button and palette action start a copied
supervisor runtime so app replacement does not interrupt package management.

The 2026-10-20 seed contains 114 packages and 91 corresponding package source archives,
plus Setup 2.937 sources, notices and relay sources. `seed-licenses.json` records upstream
license declarations and notice provenance; `LicenseRef-Upstream-*` entries retain the
actual upstream terms instead of inferring an SPDX expression. `_autorebase` supplies no
explicit declaration in this release, so its notice preserves the complete scripts and README.
The lock records 510,489,120 artifact bytes and 831,247,849 measured installed bytes.
Retain the staged seed alongside release artifacts so removed upstream versions remain available.
Refresh additionally requires `jasper.cygwinRefresh.setup-source` with the tagged Setup
archive, COPYING and hash-bearing provenance.json.

`InstallerAcceptanceTest` is an opt-in headless Windows harness compiled by
`compileCygwinBundle`. It accepts an app image, a new scratch directory, the JBR SDK
and a cache containing the signed extra `hello` package. It requires an absent system
Cygwin root, uses the production MSI template, and verifies initial provisioning,
headless package management, repair, upgrade and uninstall preservation. WiX is needed
only to build the test MSIs; `installWindowsUser` uses the JBR app-image path.
`UserInstallAcceptanceTest` brackets actual Gradle install/update/uninstall/reinstall
runs with a complete root snapshot, including an added package, configuration,
symlink and repository. Both harnesses require an isolated test account.
Provisioning outcomes are retained in `logs/provision-result.json` under the owned
environment, including failures reported by an MSI custom action.

Windows release builds run `verifyCygwinDistribution` and produce a separate
`cygwinSourcesZip` alongside the application assets. `validation.json` binds native
and interactive acceptance to the seed digest for each architecture; missing evidence
blocks publication. Local installation and packaging remain available for validation.
