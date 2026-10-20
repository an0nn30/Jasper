# Jasper bundled Cygwin for Windows

Status: approved by the user on 2026-10-20. The [implementation plan](../plans/2026-10-20-jasper-bundled-cygwin.md)
is approved for execution. Task 1 probe harnesses are implemented; native Windows
evidence is pending before production work begins.

## 1. Outcome and scope

Windows users receive a ready-to-use **Cygwin Bash** shell with Jasper. Both the
Windows setup installer and `:jasper-app:installWindowsUser` install the same
offline starter environment. Users can add, remove and update packages through
official Cygwin Setup. Jasper updates preserve that environment in its entirety.
PowerShell remains the platform default; an existing saved shell choice remains
effective.

This extends the app's existing shell profiles and Windows packaging. Cygwin is
an external child-process environment, owned by the application integration;
`jasper-terminal` retains its existing process/session boundary. Buddy and the
plugin SDK require no changes.

The parent terminal design and approved terminal/app refactor amendments remain
binding. The deliberate extension is support for bundled Windows `bash.exe`,
including shell integration and local working-directory conversion.

## 2. Starter packages and reproducibility

The starter distribution contains the Cygwin Base category plus explicit
`bash`, `coreutils`, `findutils`, `grep`, `sed`, `gawk`, `diffutils`, `less`,
`tar`, `gzip`, `bzip2`, `xz`, `unzip`, `git`, `openssh`, `curl` and
`ca-certificates`, with their complete dependency closure. Base membership and
dependencies are resolved when refreshing the bundle, then locked as individual
package versions. OpenSSH provides command-line tools; installation does not
configure an SSH server.

A checked-in manifest identifies the Setup executable, architecture, repository
metadata, every binary/source archive, version, byte size and cryptographic
digest. A release uses an immutable retained snapshot, not whatever a mirror
serves on build day. Ordinary builds consume this manifest; refreshing it is an
explicit maintainer operation. A missing or mismatched artifact fails the build.

The payload contains official Setup and a local package cache suitable for its
offline installation mode. Keep signature verification enabled and retain the
signed upstream metadata. Do not hand-extract a few executables: Setup must
create its package database and execute package post-install scripts.

Publish matching source archives and required notices alongside the binary
release, with an inventory linking each shipped package to its license and
corresponding source. Package license review is part of bundle refresh.
Measure compressed payload and installed size from the actual locked snapshot;
neither is estimated as a release guarantee.

The initial payload is x86_64, matching official `setup-x86_64.exe`.
Native Windows ARM64 Jasper requires a separate acceptance gate for that x64
Cygwin payload under Windows emulation, including the ARM64 Java/ConPTY launch
path. Do not advertise ARM64 Cygwin support or silently omit Cygwin from an ARM64
release until that gate passes.

## 3. Persistent layout and ownership

Use the Windows Local AppData known folder, resolved through Windows APIs:

```text
%LOCALAPPDATA%\Jasper\cygwin\
    owner.json
    state.json
    root\                 # Cygwin /, including etc/setup and home
    manager\              # official Setup executable and its verification record
    cache\                # downloaded package archives
    logs\                 # installation/management diagnostics
```

The runtime root has a stable path. It is separate from both the setup-installed
app and `%APPDATA%\JasperApp`, which `installWindowsUser` replaces transactionally.
Both installation methods share this per-Windows-user environment. Changing
Jasper's configuration home does not create a different Cygwin installation.

The ownership record identifies Jasper's environment and layout version, not
the current app release. State records initialization progress and the original
seed manifest; the Cygwin package database is authoritative for installed
packages after initialization.

Only create a new root beneath an absent, safely created owned directory. An
existing unrecognized directory, unexpected reparse point, malformed ownership
record or incompatible layout produces a diagnostic rather than adoption,
replacement or recursive deletion. Preserve legitimate links within the
Cygwin filesystem; the user installer's blanket plain-tree restriction cannot
be applied to this separately managed root.

The default Cygwin home is `/home/<Cygwin user>`, created through normal Cygwin
account/profile behavior. It survives Jasper updates along with `/etc`, package
state and all other root contents. Installer configuration must not encode the
build machine's user identity. User changes to home mapping and startup files
remain effective.

Jasper uninstall preserves this directory, including user-added packages,
keys, repositories and dotfiles. Document its location and manual removal after
closing Cygwin processes. Reinstallation reuses an owned initialized root.

## 4. Installation and app updates

Provide one headless Java provisioning helper, usable by both install flows,
with a small shared lifecycle implementation that the app's package-management
feature can also use. Keep it separate from Swing and terminal implementation
classes. Its dependency placement must preserve the app package DAG; no SDK
types or new public terminal API are required.

On a first installation the helper:

1. Acquires a per-environment cross-process operation lock.
2. Validates paths, ownership, payload and available disk space.
3. Records an initializing state at the final stable root path.
4. Runs official Setup without elevation, using only the bundled local cache,
   the locked package selection, and no separate desktop/Start Menu shortcuts.
5. Checks Setup's outcome and package database, then runs noninteractive probes
   for Bash and the required tools before recording ready state.

Do not install at a temporary root and rename it afterward: initialization may
record root paths. Interrupted initialization remains identifiable and can be
retried against the same owned root. Preserve logs and partial state on failure.
Do not delete an initialized environment as part of app rollback.

For an already initialized owned environment, installation checks its identity
and readiness without invoking package installation or changing root contents.
A new Jasper seed affects fresh installations only. Jasper updates must not
upgrade, downgrade, remove, repair or reinstall packages automatically, including
packages the user deliberately removed. A damaged environment is reported with
the path and package-manager recovery route.

### Installer integration

`packageInstaller` currently wraps a verified app image with jpackage/WiX.
Include the offline cache and helper in that image and add an installer
provisioning hook that runs as the installing user, after helper files are
available and before successful setup completion. It must run for silent
installation as well as interactive installation, with synchronous exit-code
handling and useful logs. No Jasper GUI or login shell is started.

The persistent root is outside MSI file ownership: MSI repair, upgrade and
uninstall must not enumerate or remove its files. An unsuccessful provisioning
step fails installation; app rollback may leave the owned Cygwin state for
retry. It must never report a complete installation with missing initial
Cygwin provision.

Before implementation is planned in detail, validate the selected JBR/WiX
template extension point with a small Windows installer lifecycle probe.
The probe must establish execution identity, sequencing, silent-mode behavior
and rollback. A first-app-launch installer is not an equivalent fallback.
If jpackage cannot support this contract, revise the installer design explicitly
before substituting a bootstrapper.

### Gradle user installation

`packageWindowsUser` stages the same payload. `installWindowsUser` invokes the
same provisioning helper within its installation workflow and reports failure
if initial provisioning fails. An existing app remains recoverable through
the current rollback mechanism. Keep native compilation, WiX, installer
generation, PowerShell execution and AOT training out of this task graph.
`uninstallWindowsUser` continues to build no application artifacts and leaves
the persistent environment untouched.

The Windows portable ZIP carries the payload and an explicit headless
provisioning entry point. Extraction alone does not initialize Cygwin or trigger
downloads; document that step. Development launches discover an already
provisioned environment without silently creating one.

## 5. Shell profile and launch behavior

Add stable profile id `cygwin`, displayed as **Cygwin Bash**, after Command Prompt
and before WSL distributions. Use the existing catalog, terminal shell chooser,
settings and saved profile handling. Background discovery checks the owned
environment and required executables. An unavailable selected profile follows
the existing warning/default fallback behavior.

Launch the absolute `root/bin/bash.exe` through the existing ConPTY session
path. Establish an interactive login environment and reuse Jasper's Bash
integration wrapper with Cygwin-aware path handling. Honor the existing
shell-integration setting. Scope the new behavior to the Cygwin profile rather
than treating every Windows `bash.exe` as Cygwin.

Set the Cygwin environment per child process. Its own `/usr/bin` must precede
other tool distributions; preserve access to normal Windows tools. Do not
modify the system/user PATH or reuse another Cygwin installation's executables.
Keep the existing `TERM=xterm-256color` and `COLORTERM=truecolor` guarantees.

Convert Jasper script locations and requested starting directories with the
owned environment's `cygpath`, using argument arrays rather than interpolated
shell command strings. Conversions run off the EDT, with timeouts and bounded
output. Explicitly requested directories take precedence over a login profile's
default home change. With no requested directory, start in the Cygwin home.

For OSC 7 local directory reports, account for `/home`, `/cygdrive` and custom
mounts rather than assuming a drive-prefix substitution is sufficient. Prefer
having the Cygwin integration emit a Windows-resolvable local path from
`cygpath`; validate the resulting URI through the existing local-host and UNC
protections. Conversion subprocesses must never run under the terminal buffer
lock or on its reader thread. Preserve command markers and exit status.

## 6. Manage Cygwin packages action

Add **Manage Cygwin packages…**, id `manage_cygwin_packages`, with no default
shortcut. Expose it in the Windows command palette and beside the terminal
shell selection in Settings. Both use one application-owned controller and
the existing notification/error presentation.

The controller opens official Setup graphically with the owned root, persistent
cache, and no-admin mode, using online package selection. The user chooses
packages and updates in Setup. Normal dependency resolution can update the
initially bundled versions; the seed manifest is not a runtime version pin.
Describe Setup's Keep selection in user documentation for users who want to
avoid a general upgrade.

Before opening Setup, require Jasper Cygwin sessions and other processes using
this root to exit. Report active use without killing processes. Acquire the
cross-process operation lock and block new Jasper Cygwin launches while Setup
runs; PowerShell, Command Prompt and WSL remain usable. Serialize management
across app windows and separate Jasper processes.

Run Setup through a small independent supervisor holding the operation lock,
so closing Jasper does not release the lock while Setup is still operating.
Avoid holding files in the Cygwin root open from the supervisor. Direct
user-launched Setup is outside Jasper's lock protocol; detect observable
external use and retain Setup's own in-use checks rather than claiming
exclusive control over all external programs.

When Setup exits, inspect its result and refresh shell availability off the
EDT. Cancellation, network failure, restart-required status and missing Bash
receive distinct actionable outcomes. Do not assume external Setup changes
can be rolled back atomically.

The manager executable is replaceable tooling outside the root. App releases
may supply a newer verified manager, installed only when no management
operation is active. Copy it to persistent storage before starting it so an
app update cannot replace the running manager. Keep the prior valid copy if
replacement fails.

## 7. Verification and implementation gates

Portable tests cover manifest parsing and integrity, lifecycle state and
ownership, path quoting, profile resolution, action visibility, failure
reporting, and lock contention. Installer tests cover fresh install, retry,
rollback and switching between the two installation methods.

Native Windows acceptance must demonstrate:

- Offline installation through both entry points as a standard user, including
  silent setup, without starting the Jasper GUI during provisioning.
- Bash, Git, SSH and curl availability, certificate validation, interactive
  typing, Ctrl+C, resizing, Unicode and execution of Windows tools through
  the real ConPTY path.
- Homes and install paths containing spaces and non-ASCII characters;
  startup scripts, explicit working directories, OSC 7 and prompt markers.
- Adding a package, modifying dotfiles and `/etc`, then upgrading, repairing,
  uninstalling and reinstalling Jasper without changing those contents.
- User-upgraded and user-removed packages surviving an app upgrade unchanged.
- Multiple Jasper processes, externally started Cygwin processes, management
  cancellation, a terminated installer, disk-full failure and interrupted setup.
- Standard app updates while Cygwin is running leave the environment untouched;
  package updates require its processes to stop.

The installer hook probe and Cygwin/ConPTY launch probe are prerequisites for
locking down the implementation plan. Windows ARM64 compatibility is an
additional release gate. These are unverified design assumptions, not completed
validation. GUI checks remain user-run under repository instructions.

Execution should follow the repository's native inline app workflow with one
independent final review, subject to the user's review of the written plan.
Run targeted tests, full headless `./gradlew check`, packaging graph checks and
Windows lifecycle checks. Record native checks that could not be run.

## 8. Sources and existing integration points

Upstream references checked on 2026-10-20:

- [Cygwin installation and package management](https://cygwin.com/install.html):
  official Setup, offline cache installation, no-admin mode and package updates.
- [Cygwin FAQ](https://cygwin.com/faq.html): separate installation behavior,
  setup options and Windows/POSIX path conversion.
- [Cygwin licensing](https://cygwin.com/licensing.html): runtime licensing and
  redistribution considerations; each bundled package also retains its license.

Existing code to extend:

- `gradle/packaging.gradle` and `gradle/packaging-support-test.gradle`
- `packaging/windows/Verify-Installer.ps1`
- `packaging/windows/user-install/UserInstall.java` and its lifecycle tests
- `jasper-app/.../launch/ShellProfiles.java`, `ShellProfileCatalog.java`,
  `LaunchSettings.java`, and the Bash integration resources
- `jasper-app/.../commands/ActionId.java`, application action wiring and
  terminal settings UI

Keep payload preparation in a focused Gradle script and provisioning/management
in focused Java files rather than expanding the main packaging script with the
entire subsystem. The implementation plan must choose exact files and signatures
after the Windows probes establish the integration mechanisms.
