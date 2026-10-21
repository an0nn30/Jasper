# Bundled Cygwin for Windows

Jasper's Windows packages carry an offline Cygwin starter environment, the seed, which the setup wizard,
`installWindowsUser` and the portable ZIP's `provision-cygwin.cmd` install once into a persistent per-user folder; the
**Cygwin Bash** shell profile then runs that environment's Bash on a POSIX pseudo-terminal through a small relay. This
folder holds the seed's lock and the files the build does not download, the installation helper, the relay's prebuilt
programs and sources, and the native probes and acceptance harnesses.

What users get is in [Cygwin Bash](../../../docs/terminal.md#cygwin-bash), the logs and messages in
[Cygwin diagnostics](../../../docs/diagnostics.md#cygwin-on-windows), and the packaging tasks in
[bundled Cygwin](../../../docs/packaging.md#bundled-cygwin). The design is the spec
`docs/superpowers/specs/2026-10-20-jasper-bundled-cygwin-design.md`.

## Files here

| Path | What it is |
|---|---|
| `seed.lock.json` | The lock: schema, architecture (`x86_64`), measured installed size, and every artifact's path, HTTPS origin, size and SHA-512 digest; each package's name, version, binary and source archive, notice and dependencies; Cygwin Setup, the signed repository metadata, Setup's sources and the relay's files |
| `seed-support/` | The locked files the build takes from the tree instead of downloading: the signed `setup.xz` and its signature, `pubring.asc`, `setup-x86_64.exe` and its signature, Setup's sources, the packages' notices, and the relay's programs, sources and licence. The repository's `.gitattributes` keeps their bytes exact (`-text -whitespace`); `notices/` also keeps, for each source package, a copy of its notice named after the source archive (`<source>-src.tar.*.txt`), which the lock does not name |
| `seed-licenses.json` | The reviewed licence inventory, one entry per package, which a refresh needs |
| `seed_refresh.py`, `seed_refresh_test.py` | The refresh script and its tests (`python3 seed_refresh_test.py` here) |
| `validation.json` | The native acceptance recorded for one seed, per architecture, which releases require |
| `CygwinTool.java` | The installation helper's entry point |
| `BundleTool.java` | The build's seed tool: `acquire`, `verify`, `stage` and `distribution` |
| `CygwinInstallerTemplate.java` | Adds the installation custom action to the selected JBR's WiX template and stages the image for WiX |
| `installer/provision-cygwin.cmd` | The portable ZIP's installation script |
| `relay/prebuilt/` | The relay's tested programs and `provenance.json` ([relay README](relay/README.md)) |
| `probe/` | The relay's sources and the native probes |
| `*Test.java` | The portable checks and the Windows acceptance harnesses |

The helper's classes are `dev.jasper.app.cygwin` (JDK and JNA only, shared with the application) and `CygwinTool`;
`compileCygwinBundle` compiles them with the other Java files here into `jasper-app/build/cygwin/classes`, and
`cygwinToolJar` packs everything except the tests, `BundleTool` and `CygwinInstallerTemplate`, with JNA, into
`cygwin-tool.jar`.

## The environment

```text
%LOCALAPPDATA%\Jasper\
    .cygwin.initialization.lock
    cygwin\
        owner.json            # layout 1, product "Jasper Cygwin" and the owner's Windows SID
        state.json            # INITIALIZING or READY, with the seed's digest
        operation.lock        # held by installation, launch registration and package management
        root\                 # Cygwin's /, with etc/setup/installed.db and home
        manager\<digest>\     # setup.exe, frontend-aarch64.exe, frontend-amd64.exe, backend.exe, seed.lock.json
        manager\current.json  # the generation launches and package management use
        cache\seed-<digest>\  # the seed the first installation copied, kept for retries
        cache\packages\       # Setup's download folder for package management
        logs\
        leases\               # one record per running Jasper Cygwin session
        operations\<id>\      # one folder per package-management run
```

The folder is under the Windows Local AppData known folder, so it is the same for both installation methods, for every
Jasper version and for any Jasper home. Jasper creates it only where nothing exists: it fills a staging folder and
renames it into place. A link or junction anywhere in a control path, or an `owner.json` that does not name this layout,
product and Windows account, makes the folder foreign: Jasper never adopts, replaces or deletes it. Inside `root`,
Cygwin's own links are left alone. The digest is the SHA-512 of the seed's `seed.lock.json`.

`CygwinState` reads the phase without changing anything: absent, initialising, ready (the state says `READY` and the
package database, `bin\bash.exe`, `bin\cygpath.exe` and `bin\cygwin1.dll` exist), damaged or foreign. The Cygwin Bash
profile is offered only when it is ready.

## Installation

The setup wizard's custom action, `installWindowsUser` and `provision-cygwin.cmd` all run, from the installed image:

```text
runtime\bin\java.exe --enable-native-access=ALL-UNNAMED -cp app\cygwin-tool.jar CygwinTool ensure --seed app\cygwin-seed
```

`CygwinTool status` prints the phase and its diagnostic. `ensure` verifies the supplied seed against its lock and then:

- **absent**: creates the layout, takes `operation.lock`, requires free space of twice the archives' size plus twice the
  installed size, copies the seed to `cache\seed-<digest>` and records `INITIALIZING` with its digest. Unless `root` is
  empty, it requires that no process of this account runs from the environment or has loaded a file from `root`. It
  then runs Setup from the kept seed, hidden and offline (`--quiet-mode=hidden --no-admin --local-install
  --no-version-check --no-shortcuts --no-replaceonreboot --no-write-registry`, the kept cache as `--local-package-dir`,
  `--root`, and `--packages` with each locked name and version), for at most 10 minutes, with Setup's signature checks
  on. It requires `etc/setup/installed.db` to list exactly the locked packages and versions, runs `bash --noprofile
  --norc -c "exit 0"`, `git --version`, `ssh -V` and `curl --version`, installs the manager generation and records
  `READY`;
- **initialising**: goes on with the seed recorded in `state.json`, from `cache\seed-<digest>`;
- **ready**: installs this seed's manager generation when it is new and points `current.json` at it; the packages,
  `root` and the state stay as they are, so a newer seed only ever applies to a new environment;
- **damaged** or **foreign**: fails.

The outcome (`CREATED`, `REUSED`, `FAILED` or `RESTART_REQUIRED`, when Setup exits with 118) and its message go to
standard output and `logs\provision-result.json`. The exit status is 0 for `CREATED` and `REUSED`, 2 for
`RESTART_REQUIRED` and 1 otherwise.

The setup wizard runs the command as a deferred custom action, `JasperProvisionCygwin`, impersonating the installing
user, after `InstallFiles` and whenever the product is not being removed, so interactive and silent installations,
repairs and upgrades all run it and fail when it fails. The folder lies outside the MSI's file ownership, so repair,
upgrade and uninstall never touch it. `installWindowsUser` runs it after swapping in the new image, for at most 20
minutes, and rolls the installation back when it fails. Extracting the portable ZIP installs nothing until
`provision-cygwin.cmd` runs.

## Launch

`CygwinLaunch` prepares a Cygwin Bash pane on the launch worker, never on the EDT or the terminal's reader. It
requires the ready phase; reads `manager\current.json`; checks that the generation's folder name is its lock's digest
and that its `backend.exe` and the frontend for the JVM's architecture (`aarch64`, or `amd64` for `amd64` and `x86_64`)
match the lock; and converts paths with the environment's `cygpath -u`, given as arguments, each within 5 seconds and
64 KiB of output. The pane's command is:

```text
manager\<digest>\frontend-<arch>.exe manager\<digest>\backend.exe root\bin\bash.exe --noprofile --rcfile <cygwin.bash> -i
```

Its environment, start-up files and directory are as [Cygwin Bash](../../../docs/terminal.md#cygwin-bash) describes;
`cygwin.bash` is in Jasper's shell integration folder. A starting directory or `HOME` on another computer's network
share is refused. Before it starts, the session registers a lease under `operation.lock`, which fails while package
management holds the lock or a management run was interrupted; the lease goes when the session ends. A program started
in place of the shell (`LaunchSettings.withCommand`) skips all of this and runs directly in ConPTY.

## The relay

Cygwin's console input reader loses characters when the console is resized repeatedly, so Bash never reads the Windows
console:

- `probe/ConPtyRelay.cpp`, the frontend, is a native Windows program (ARM64 and x64, MSVC, static runtime) that runs in
  the pane's ConPTY. It starts the backend detached from the console, suspended and inside a job that kills its whole
  process tree when the frontend ends, with only two private pipes inherited. It reads console input as UTF-16, keeping
  surrogate pairs together, and sends it as UTF-8 frames; it checks the console's size every 20 ms and before each
  input frame and sends a resize frame first when it changed; it copies the backend's output to the console. On a
  failure it prints `Jasper PTY relay: <operation> (Windows error N)` and exits.
- `probe/CygwinPtyRelay.c`, the backend, is an x86_64 Cygwin program that starts Bash with `forkpty` and the initial
  size. Each frame is a type byte, a 32-bit big-endian length and the payload: type 1 is input of at most 4,096 bytes,
  type 2 is rows and columns as two 16-bit big-endian numbers, applied with `TIOCSWINSZ`. A malformed frame, a broken
  pipe or the end of its input ends Bash with `SIGHUP`, then `SIGKILL` after a second. The backend exits with 65 after
  a malformed frame, with 74 after a failed pipe read or write, and otherwise with Bash's status.

The seed carries the tested programs of `relay/prebuilt/` (copied to `seed-support/relay/`) with their sources, so
nothing in a build or an installation compiles native code. [The relay README](relay/README.md) says how to replace
them.

## Package management

`CygwinFeature` runs **Manage Cygwin packages…** on a virtual thread. `CygwinManager` needs a packaged Jasper: it finds
`app\cygwin-tool.jar` beside the image's launcher, or the file the `jasper.cygwinTool` system property names. It accepts
a ready or damaged environment, takes the management lock (which fails while a Jasper Cygwin session's lease is live or
any process uses the environment), records interrupted earlier runs as failed, and copies the running Java runtime and
the helper into a new `operations\<id>\`. It then starts `CygwinTool manage` from that copy, so an app update cannot
replace the files it runs from, and releases its lock for the supervisor to take.

The supervisor holds the lock for the whole run and starts the current generation's verified `setup.exe` with the
wizard shown: `--no-admin --no-shortcuts --no-replaceonreboot --no-write-registry --root <root> --local-package-dir
<cache\packages>`, for at most 24 hours. It writes `result.json`, from Setup's exit status (118: a restart is needed)
and the phase that follows, which the application, polling every half second, turns into the **Cygwin package
management** notification; it then detects the shells again. The supervisor goes on if Jasper exits; a run that ends
without a result blocks new Cygwin launches until **Manage Cygwin packages…** is opened again. The application never
upgrades, removes or repairs packages by itself.

## The seed

The current seed holds Cygwin Setup 2.937, the Base category plus `bash`, `coreutils`, `findutils`, `grep`, `sed`,
`gawk`, `diffutils`, `less`, `tar`, `gzip`, `bzip2`, `xz`, `unzip`, `git`, `openssh`, `curl` and `ca-certificates` with
their dependencies: 114 packages built from 91 source packages, 510,489,120 bytes of artifacts and 831,247,849 bytes
installed. `seed-licenses.json` records each package's upstream licence declaration and where its notice came from; a
`LicenseRef-Upstream-*` expression keeps the upstream terms as they are instead of inferring an SPDX expression.
`_autorebase` declares no licence, so its notice holds its complete scripts and README. Keep each staged seed with the
release it went into: mirrors drop old versions.

`acquireCygwinSeed` fills `jasper-app/build/cygwin/downloads` from `seed-support` and, for the 205 package archives,
from the lock's origins on `mirrors.kernel.org`, with the JDK only: it follows no redirect, resolves no version, and
publishes a file only once its size and digest match. `-Pjasper.cygwinSnapshot=<folder>` uses a complete retained seed
instead, fully offline. `verifyCygwinSeed` checks the seed; `stageCygwinSeed` copies exactly the locked files with the
lock into `jasper-app/build/cygwin/seed`, which `stagePackage` and `stageWindowsUser` copy to `app\cygwin-seed`.

### Refreshing it

`refreshCygwinSeed` runs `seed_refresh.py` (with `-Pjasper.python`, else `python3`) and needs GnuPG. It takes seven
properties, each required:

| Property | Value |
|---|---|
| `jasper.cygwinRefresh.mirror` | The mirror whose metadata was retained |
| `jasper.cygwinRefresh.snapshot` | A new absolute folder for the candidate |
| `jasper.cygwinRefresh.metadata` | A folder with the retained `setup.xz`, `setup.xz.sig` and `pubring.asc` |
| `jasper.cygwinRefresh.licenses` | The reviewed licence inventory (`seed-licenses.json`) |
| `jasper.cygwinRefresh.relays` | A folder with the relay's programs, sources and `provenance.json` |
| `jasper.cygwinRefresh.setup-source` | A folder with the tagged Setup source archive, `COPYING` and a `provenance.json` holding its hash |
| `jasper.cygwinRefresh.installed-bytes` | The measured installed size of this exact selection |

It verifies the metadata's signature against Cygwin's key, resolves the Base category and the starter packages with
their dependencies, downloads Setup 2.937 and every archive checking the metadata's sizes and digests, requires a
reviewed licence entry and notice for every package of the closure, checks the relay's and Setup's files against their
provenance, and writes `seed.lock.json` into the candidate. It never changes the committed lock. To adopt a candidate,
replace `seed.lock.json` and the files it names under `seed-support`, run the native acceptance below, and record it in
`validation.json`.

## Checks

`check` runs the portable checks on every host:

| Task | What it checks |
|---|---|
| `verifyCygwinManifest` (`BundleManifestTest`) | Lock parsing and rejection, artifact sizes and digests, staging, acquisition from local files and downloads, and the release evidence rules |
| `verifyCygwinLifecycle` (`CygwinToolTest`) | Ownership and foreign folders, interrupted initialisation, launch and management locks within one process and across processes, interrupted management runs, a failed supervisor start and the clean-up of old runtime copies |
| `verifyCygwinProvisioning` (`CygwinProvisionerTest`), run with `verifyCygwinLifecycle` | A fresh installation, reuse that leaves `root` as it is, a full disk, a failed Setup kept for retry, a restart and a foreign folder |
| `verifyCygwinProbes` (`ProbeAssertionsTest`) | The probes' own rules: Setup's hidden mode, deadlines, the custom action's scheduling, missing or contradictory evidence and scratch-folder ownership |
| `verifyCygwinProbeRuntime` | That a runtime built with the installer probe's `jlink` options has a Java executable |

Passing them does not establish Windows behaviour.

### Native probes (Windows)

`probeCygwinInstaller`, `probeCygwinTerminal` and `probeCygwinProduction` run with JBR 25 on Windows, from a
standard-user account for the installer probe, and fail on other hosts. Each takes a new absolute scratch folder whose
parent exists (`-Pjasper.cygwinProbeRoot`) and never reuses or removes an existing tree. The installer and terminal
probes write JSON summaries to `jasper-app/build/cygwin-probes/reports/` (`installer.json`, `terminal.json`), which
start as failed and become passed only when every check passes, and name the OS, architecture, JBR version and scratch
folder. A later run of the same probe replaces its summary, so copy the summary and the scratch logs of each
architecture's run. A run that fails before it has a scratch folder fails Gradle and leaves the previous summary in
place.

| Task | What it does |
|---|---|
| `probeCygwinInstaller` | Builds a separate, randomly named `JasperCygwinProbe-*` MSI with a fresh upgrade code, using the selected JBR's `main.wxs` and the WiX that JBR supports on `PATH`, and tests a failed installation's rollback, silent installation, repair, upgrade and uninstallation; a small Java helper records its identity, the installed files and its timing. It runs `msiexec` through `cmd /d /s /c`, so it rejects scratch paths with characters that `cmd` would interpret. It never installs or starts Jasper, and uninstalls its product when it fails (`cleanupExit` 1605 means it was already gone). Never uninstall Jasper with these MSIs |
| `probeCygwinTerminal` | Installs a Cygwin offline cache (`-Pjasper.cygwinProbePayload`, a folder with a signature-checked `setup-x86_64.exe` 2.929 or newer and its complete `cache\`) into the scratch folder, then runs interactive Bash through `TerminalSession` in a separate JVM: start-up profile, paths with spaces and Unicode, explicit directory, OSC 7 and OSC 133, Unicode input and output, sizes after twenty immediate resizes, a Windows command's exit status and Ctrl+C. With `-Pjasper.cygwinProbeRelay` and `-Pjasper.cygwinProbeFrontend` it runs Bash through the relay |
| `probeCygwinProduction` | Copies an existing probe root (`-Pjasper.cygwinProbeRuntime`) into an isolated environment and runs the production `CygwinLaunch`, `cygwin.bash` and leases with the prebuilt relay: shell integration, directory, command status, Unicode, resizes, line wrapping, a Windows command, Ctrl+C, and a renamed external process that loads the environment's `cygwin1.dll` |
| `verifyCygwinRelayProtocol` | Runs a backend (`-Pjasper.cygwinProbeRelay`) with a POSIX shell (`-Pjasper.cygwinProbeShell`) and checks resizes, Unicode, exit status, malformed and partial frames and pipe failure; the backend builds on macOS too |
| `compileCygwinRelayProbe` | Builds the backend with `-Pjasper.cygwinProbeCompiler=<absolute Cygwin gcc.exe>` |
| `compileConPtyRelayProbe` | Builds the frontend from an MSVC developer environment for the target architecture with `-Pjasper.cygwinProbeMsvc=<absolute cl.exe>`; its `--self-test` checks UTF-16 and argument quoting |

Setup and MSI operations have a ten-minute limit, each terminal event 15 seconds, and a reboot-required outcome is a
failure. The terminal probe keeps Bash's standard error in `bash-stderr.log` and its temporary home and Cygwin tree for
diagnosis; stop any remaining processes before you remove them. `RelayCloseProbe` (run with the probe classes: backend,
the environment's `bin` folder and frontend) checks that closing works while output is blocked.

### Acceptance harnesses (Windows)

Both need an isolated test account and run with `jasper-app/build/cygwin/classes` and
`jasper-app/build/cygwin/cygwin-tool.jar` (for JNA) on the class path, after `cygwinToolJar`:

- `InstallerAcceptanceTest <app image> <new scratch folder> <JBR SDK> <cache with the signed extra hello package>`
  builds MSIs with the production template (WiX needed) and checks the first installation, package management without
  a window, repair, upgrade and uninstallation keeping the environment.
- `UserInstallAcceptanceTest prepare|seeded|record|verify <scratch> [<image> <extra cache>]` brackets real
  `installWindowsUser`, update, `uninstallWindowsUser` and reinstall runs with a snapshot of the whole root, including
  an added package, configuration, a link and a git repository.

`InstallerAcceptanceTest` needs the account to have no `%LOCALAPPDATA%\Jasper\cygwin`.
`UserInstallAcceptanceTest prepare` needs the ready environment such a run leaves, and moves it to
`<scratch>\preserved-msi-cygwin` so that the next `installWindowsUser` installs afresh.

### Release evidence

`verifyCygwinDistribution` stages the seed, checks that the helper jar holds `CygwinTool`, and requires
`validation.json` to name this lock's digest as `seed` and to be `true` for the host's architecture both at the top
level (native acceptance) and under `interactiveApps`. The Release workflow runs it on both Windows runners, so a
release waits for native and interactive-program acceptance of the seed on x64 and ARM64. Local packaging and
installation do not check it. `cygwinSourcesZip` publishes the corresponding sources beside the packages:
`Jasper-<version>-cygwin-sources-<arch>.zip` holds the lock, every package source archive, the notices, Setup's sources
and the relay's sources and licence.