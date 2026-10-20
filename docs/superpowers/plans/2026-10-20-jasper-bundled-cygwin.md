# Jasper Bundled Cygwin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, with one independent whole-branch review after implementation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install an offline, extensible Cygwin environment through both Windows install paths and expose Cygwin Bash and Manage Cygwin packages in Jasper.

**Architecture:** Official Cygwin Setup provisions a stable per-user environment outside the application directory. A focused headless helper owns provisioning and management coordination; app launch code discovers and starts that environment through the existing terminal facade.

**Tech Stack:** Java/JBR 25, Gradle wrapper, JNA 5.14.0, official Cygwin x86_64 Setup and signed package metadata, existing jpackage/WiX and ConPTY.

**Spec:** [Approved design](../specs/2026-10-20-jasper-bundled-cygwin-design.md).

**Status:** Implementation complete and integrated into local `main`, 2026-10-20. User-approved deviation: pinned prebuilt POSIX PTY relay artifacts replace direct Cygwin console input; ordinary Gradle installation requires no PowerShell, package manager or native compiler. The locked seed/source inventory, distribution gates, production lifecycle/discovery/management, MSI and Gradle install paths are complete. Real disconnected ARM64 MSI and Gradle lifecycle acceptance passes, including added packages and exact preservation of 8,275 root entries. Native x64 Windows and interactive-app acceptance remain explicit release gates at the user's direction; their matrix steps stay unchecked. The full merged headless check passes: 11,878 tests, zero failures/errors and 29 skips. [Final evidence](2026-10-20-jasper-cygwin-verification.md); [probe history and rulings](2026-10-20-jasper-cygwin-probe-results.md).

## Global Constraints

- Starter set: Cygwin Base plus `bash`, `coreutils`, `findutils`, `grep`, `sed`, `gawk`, `diffutils`, `less`, `tar`, `gzip`, `bzip2`, `xz`, `unzip`, `git`, `openssh`, `curl`, `ca-certificates`, and the complete locked dependency closure.
- Persistent location: `%LOCALAPPDATA%\Jasper\cygwin`, resolved by Windows known-folder APIs; Cygwin `/` is its `root` child.
- Preserve the entire initialized root across app upgrades, repair, rollback, uninstall and reinstall. Never reconcile it back to the starter manifest.
- Shell id `cygwin`; label `Cygwin Bash`; after Command Prompt, before WSL; platform default unchanged.
- Action id `manage_cygwin_packages`; label `Manage Cygwin packages…`; no default shortcut; Windows palette and terminal shell settings.
- `TERM=xterm-256color` and `COLORTERM=truecolor`.
- Both installation paths provision before reporting success, offline and without elevation. The Gradle user-install graph excludes native compilation, WiX, installer generation, PowerShell execution and AOT.
- `jasper-terminal`, Buddy and SDK keep their boundaries; app production code uses the supported terminal facade.
- No interface without two real implementations. Prefer concrete helpers and existing Java function types for test injection.
- No GUI or AOT training without the user's authorization. Run native headless probes on a Windows test machine; hand interactive acceptance to the user.
- Use a dedicated `codex/` branch at execution, preserving other work. One commit per task with a `Co-Authored-By:` trailer. No merge or push without authorization.
- Record changes to this plan in its status banner and `docs/STATUS.md`.

## Review Focus

1. **Same environment, two installers:** neither an MSI repair nor the Gradle installer may reseed user-upgraded or removed packages (Tasks 3, 4, 8).
2. **Race between launch and management:** manager exclusivity must survive the parent app exiting, and a launch already being prepared must not slip through (Tasks 3, 5, 6).
3. **Slow discovery at startup:** a saved Cygwin choice must await authoritative discovery, with bounded behavior and the existing warning fallback only when appropriate (Task 5).
4. **User-controlled paths and mounts:** spaces, Unicode, shell metacharacters, legitimate Cygwin links and foreign UNC paths must preserve correct quoting and ownership protections (Tasks 3, 5).
5. **Partial success:** a reboot-required Setup outcome, broken root, interrupted provisioning or incomplete source bundle must never masquerade as a ready distributable (Tasks 1–4, 7).

## File and dependency map

Paths below are repository-relative. Each task lists its exact files. New names are proposed interfaces to finalize after Task 1.

- `packaging/windows/cygwin/`: bundle manifest, probe inputs, lifecycle CLI and its portable standalone tests.
- `gradle/cygwin.gradle`: helper compilation, payload preparation, probe and verification tasks; applied from `jasper-app/build.gradle.kts`.
- `jasper-app/src/main/java/dev/jasper/app/cygwin/`: concrete shared lifecycle/path/process helpers, independent of Swing and other Jasper packages; compiled into both the app and the small provisioning CLI from the same source files.
- `jasper-app/src/main/java/dev/jasper/app/launch/`: profile discovery and Cygwin launch adaptation; outgoing dependency on `cygwin`.
- `jasper-app/src/main/java/dev/jasper/app/application/CygwinFeature.java`: lifetime owner, background operations, notification callbacks and catalog refresh.
- `commands`, `workspace`, `settingsui`: action declaration and UI callbacks; they do not directly import provisioning internals.

The shared source set uses only JDK/JNA. Compile selected shared sources into a standalone helper jar, not the full app; tests verify both compilation paths. Avoid a new public library module. Update package-info and the architecture allowlist for the new leaf package and its callers.

---

### Task 1: Establish Windows installation and launch evidence

**Files**
- Create `gradle/cygwin.gradle`.
- Create `packaging/windows/cygwin/probe/InstallerProbe.java`.
- Create `packaging/windows/cygwin/probe/ConPtyProbe.java`.
- Create `packaging/windows/cygwin/probe/ProbeAssertions.java`.
- Modify `jasper-app/build.gradle.kts` to apply the script.
- Create `docs/superpowers/plans/2026-10-20-jasper-cygwin-probe-results.md` when results exist.

**Interfaces**
- Gradle tasks `:jasper-app:probeCygwinInstaller` and `:jasper-app:probeCygwinTerminal`.
- Both require Windows and explicit `-Pjasper.cygwinProbeRoot=<absolute scratch path>`.
- Terminal probe additionally takes `-Pjasper.cygwinProbePayload=<verified local cache>`.
- Probe processes emit UTF-8 JSON results under `build/cygwin-probes`; never target the real per-user Jasper installation.

- [x] **Step 1: Write probe assertions before the probes.** `ProbeAssertions` fails on missing evidence as well as incorrect values. Installer assertions:

```java
assert result.installingUser().equals(result.helperUser());
assert result.helperFilesAvailable();
assert result.helperFinishedBeforeInstallerSuccess();
assert result.silentModeRanHelper();
assert result.helperFailureFailedInstaller();
assert result.uninstallPreservedSentinel();
assert result.upgradePreservedSentinel();
```

  Model results as records in `ProbeAssertions`; enable assertions with `-ea`.
  Also require actual nonzero failure exit and retained MSI logs, not a boolean
  emitted by the helper alone. The harness derives outcomes from installer logs,
  exit codes and sentinel files.

- [x] **Step 2: Register probe tasks and confirm RED.** On Windows run
  `./gradlew :jasper-app:probeCygwinInstaller -Pjasper.cygwinProbeRoot=<scratch>`.
  Expected: absent lifecycle evidence fails. On another OS the explicit task
  fails with “Cygwin installer probe requires Windows”; it must not silently pass.

- [x] **Step 3: Implement the installer probe.** Inspect the selected JBR's
  jpackage resource templates and actual WiX version. Build an isolated probe
  product with its own name, upgrade UUID, directory and shortcuts disabled.
  Use a non-GUI Java helper to write identity/sequence evidence and optionally
  return failure. Exercise fresh silent install, induced helper failure, repair,
  upgrade and uninstall. Retain generated templates and MSI logs. Determine the
  exact resource override and scheduling conditions needed by spec section 4.
  Do not modify Jasper's production installer yet.

- [x] **Step 4: Implement the headless terminal probe.** Use the app test runtime
  classpath and supported `TerminalSession` APIs to launch the scratch root's
  absolute Bash. Verify an interactive command round trip, Unicode, resize,
  Ctrl+C, launching `cmd.exe`, startup profile execution, requested directory,
  and OSC 7/133 events. Exercise spaces/Unicode in the root and script path.
  Give each awaited event a 15-second deadline; retain raw escaped output on
  failure. Setup provisioning uses a local cache and never opens a terminal UI.

- [ ] **Step 5: Run GREEN on Windows x64, then ARM64.** Record JBR/Windows/WiX,
  Setup version and digest, exact Setup arguments and exit semantics, installed
  package list, architecture and both probe outcomes. Setup help/source is the
  authority for option and result interpretation. Measure actual disk usage.
  Run unit assertions that a missing event times out instead of passing.

- [x] **Step 6: Finalize this plan from evidence and commit the probe task.**
  Pin the installer resource filename, WiX fragment, return-code mapping and
  accepted Bash invocation in Tasks 3–5. If the hook cannot satisfy the design,
  present a design amendment before implementing a bootstrapper. ARM64 failure
  blocks that release target; it does not authorize dropping it from the matrix.
  Without a Windows executor, report Task 1 as pending and do not manufacture
  its result. Commit message: `test: establish Cygwin Windows integration probes`.

### Task 2: Produce and verify the immutable offline payload

**Files**
- Create `packaging/windows/cygwin/seed.lock.json`.
- Create `packaging/windows/cygwin/README.md`.
- Create `packaging/windows/cygwin/BundleManifest.java`.
- Create `packaging/windows/cygwin/BundleManifestTest.java`.
- Modify `gradle/cygwin.gradle`.

**Interfaces**
- `BundleManifest.read(Path file)` returns a validated immutable manifest.
- `BundleManifest.verify(Path cache)` rejects missing/mismatched files.
- Manifest schema 1: architecture, Setup artifact, metadata/signature artifacts,
  package name/version/binary/source/license entries and `installedBytes`.
  Artifact entries contain relative path, HTTPS origin, byte size and SHA-512.
- Tasks `refreshCygwinSeed`, `verifyCygwinSeed`, `stageCygwinSeed`;
  staged output `jasper-app/build/cygwin/seed`.
- Refresh requires an explicit upstream mirror and snapshot destination;
  it never runs as a dependency of build, check or installation.

- [x] **Step 1: Write `BundleManifestTest`.** Fixtures reject absolute/traversing
  archive names, duplicate paths, unknown schema, wrong architecture, incorrect
  size/digest, missing dependencies, missing source/license and changed metadata.
  One complete fixture passes; changing one byte causes verification to fail.
- [x] **Step 2: Run `./gradlew :jasper-app:verifyCygwinSeed` against fixtures;
  expect RED before verifier implementation.** Separate fixture verification
  from real payload acquisition so headless `check` needs no network.
- [x] **Step 3: Implement manifest verification and the explicit refresh task.**
  Resolve Base and the exact requested packages from signed metadata, compute the
  closure, download binaries and corresponding sources, preserve repository
  layout and signatures, and produce a candidate lock. Verify the Setup
  signature using the documented trusted key. Require maintainer review before
  replacing the committed manifest. Ordinary staging verifies the committed
  lock and only fetches its exact artifacts.
- [x] **Step 4: Validate real offline Setup in Task 1's scratch environment.**
  Verify signatures remain enabled and every installed package matches the seed
  inventory. Record compressed and installed sizes; include the latter in the
  space check. Verify a missing artifact fails without falling back to a mirror.
  Retain an immutable distributable snapshot; if publication is needed, prepare
  the artifact and obtain the required publication authorization.
- [x] **Step 5: Run fixture verification and real staging; commit**
  `build: lock and stage the Cygwin starter distribution`.

### Task 3: Implement persistent provisioning and process coordination

**Files**
- Create under `jasper-app/src/main/java/dev/jasper/app/cygwin/`:
  `package-info.java`, `CygwinLayout.java`, `CygwinState.java`,
  `CygwinOperations.java`, `CygwinProcesses.java`, `CygwinProvisioner.java`.
- Create `packaging/windows/cygwin/CygwinTool.java` and `CygwinToolTest.java`.
- Create `jasper-app/src/test/java/dev/jasper/app/cygwin/CygwinLifecycleTest.java`.
- Modify `gradle/cygwin.gradle` and `gradle/application-architecture.gradle.kts`.

**Interfaces**
- `CygwinLayout.system()` resolves the known-folder path; `CygwinLayout.at(Path)`
  supplies explicit fixture roots.
- `CygwinState.inspect(CygwinLayout)` returns `ABSENT`, `INITIALIZING`, `READY`,
  `DAMAGED` or `FOREIGN`, plus diagnostic and seed identity where available.
- `CygwinProvisioner.ensure(CygwinLayout, Path seed)` returns a structured
  result identifying created, reused, incomplete, failed or restart-required.
- `CygwinOperations.acquireManagement(CygwinLayout)` yields an AutoCloseable
  exclusive operation; launch registration uses the same cross-process lock.
- `CygwinTool.main(String[])`: `ensure --seed <path>`, `manage`,
  `status`; fixture-only explicit root injection is kept out of normal app UI.
- `verifyCygwinLifecycle` compiles and tests the standalone helper on all hosts.

- [x] **Step 1: Write lifecycle regressions.** Assert:

```java
assertEquals(before, snapshot(root)); // reuse of READY root, including removed packages
assertEquals(0, setupInvocations.get()); // no seed reconciliation on app upgrade
assertFalse(Files.exists(foreign.resolve("owner.json"))); // foreign tree untouched
assertEquals(INITIALIZING, stateAfterInterruptedSetup);
assertEquals(oldManagerDigest, managerDigestAfterFailedReplacement);
```

  Define fixture snapshot as path/type/content digest without following links.
  Add disk-full injection, malformed records, root junction, legitimate internal
  links, changed user identity, and a retry using the original seed identity.
- [x] **Step 2: Run `./gradlew :jasper-app:verifyCygwinLifecycle`; expect RED.**
- [x] **Step 3: Implement state and provisioning.** Use atomic metadata writes;
  validate control-directory ancestry without recursively rejecting Cygwin
  links. Initialize only at the final path. Ensure disk-space requirements
  include archive cache, installed files and temporary extraction space.
  Run Setup through bounded-output process handling using Task 1's exact flags.
  Verify the package database and noninteractive tool probes before ready.
  Keep initializing state on failure; retain enough seed metadata to retry
  deterministically after an app version change.
- [x] **Step 4: Implement launch/management arbitration.** Under a short
  cross-process mutex, reserve management or register a launch before starting
  Bash. Management refuses pending launches and live processes. Use PID plus
  process start identity for leases, reclaiming only demonstrably dead owners.
  Keep the reservation through session startup; release in session close/failure
  paths. Detect root-owned executables and processes loading its Cygwin DLL
  through Windows APIs, not substring matching names. Unknown process inspection
  must produce a diagnostic before management, not a false all-clear.
- [x] **Step 5: Add child-JVM race tests and run GREEN.** Simultaneous launch and
  management admits one side; a killed owner leaves recoverable state; PID reuse
  does not hold a stale lease. READY-root reuse does not require stopping running
  shells. The standalone helper and app compile from the same shared sources
  and pass `verifyApplicationArchitecture`.
- [x] **Step 6: Commit** `feat: provision persistent Cygwin environments`.

### Task 4: Wire both Windows installation paths and portable provisioning

**Files**
- Modify `gradle/packaging.gradle`, `gradle/packaging-support-test.gradle`.
- Modify `packaging/windows/user-install/UserInstall.java` and `UserInstallTest.java`.
- Modify `packaging/windows/Verify-Installer.ps1`.
- Create `packaging/windows/cygwin/installer/` resource overrides with exact
  names and content established in Task 1; record them in this task before coding.
- Modify `gradle/cygwin.gradle` and `packaging/windows/cygwin/README.md`.

**Interfaces**
- App-image payload at `app/cygwin-seed`; helper jar at `app/cygwin-tool.jar`.
- Both installer entry points call `CygwinTool ensure --seed <absolute path>`
  through their JBR with an explicit classpath; never through a shell string.
- Portable entry point `provision-cygwin.cmd` invokes the same helper using its
  adjacent packaged runtime and quoted paths.

- [x] **Step 1: Extend user-install tests.** Inject a concrete provisioning
  callback via `Consumer<Path>` overload; make it fail and assert old app and
  shortcut restoration. Assert ready Cygwin state is unchanged on success,
  rollback and uninstall. Add a fresh-install failure and retained partial root.
- [x] **Step 2: Run `./gradlew :jasper-app:verifyWindowsUserInstall`; expect RED.**
- [x] **Step 3: Stage identical payloads in standard and user app images.**
  Invoke provisioning before install success and preserve current app rollback.
  Apply the proven MSI override, restricted to install/repair with files present,
  and exclude uninstall. Keep persistent files out of MSI components.
  Package verification checks helper, cache, notices and provisioning entry point.
- [x] **Step 4: Extend graph and MSI checks.** Keep uninstall depending only on
  helper compilation. Verify user install adds no forbidden tasks; shared helper
  compilation must not pull the application compile graph into uninstall.
  MSI checks assert user execution identity, synchronous failure propagation,
  scheduling and no persistent-root file ownership.
- [ ] **Step 5: Run portable tests and the native lifecycle matrix.**
  `./gradlew :jasper-app:verifyWindowsUserInstall :jasper-app:verifyCygwinLifecycle`
  and `./gradlew :jasper-app:installWindowsUser --dry-run`.
  On an isolated Windows test account, install offline by each route, upgrade
  in both orders, repair, uninstall/reinstall, and induce Setup failure.
  Expected: correct exit outcomes and byte-identical pre-existing root.
- [x] **Step 6: Commit** `feat: install Cygwin through both Windows distribution paths`.

### Task 5: Expose Cygwin Bash and adapt shell integration

**Files**
- Modify `jasper-app/src/main/java/dev/jasper/app/launch/{ShellProfiles,ShellProfileCatalog,LaunchSettings,ShellLauncher}.java` and `package-info.java`.
- Create `jasper-app/src/main/java/dev/jasper/app/launch/CygwinLaunch.java`.
- Modify `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`.
- Modify Bash resources under `jasper-app/src/main/resources/dev/jasper/app/shell-integration/`.
- Modify corresponding tests `ShellProfilesTest`, `ShellProfileCatalogTest`,
  `ShellIntegrationScriptTest`, `ShellProfileLaunchTest`.
- Create `jasper-app/src/test/java/dev/jasper/app/launch/CygwinLaunchTest.java`.

**Interfaces**
- `ShellProfiles.CYGWIN = "cygwin"`; new `available` overload accepts
  `Optional<String> cygwinBash` after `pwsh`; existing overload delegates empty.
- Catalog adds a background `Supplier<Optional<String>>` for Cygwin discovery.
- `CygwinLaunch.prepare(LaunchSettings, Optional<Path>, Path integrationDir,
  CygwinLayout)` returns prepared settings and process working directory.
  Define its immutable result record in that file. It runs only on the launch
  worker, after catalog confirmation and before terminal process creation.

- [x] **Step 1: Write profile and launch regressions.** Assert exact id/name/order,
  PowerShell default, Linux/macOS absence, discovery refresh after package change,
  stale refresh rejection, and a saved Cygwin selection on slow first discovery.
  Add:

```java
assertEquals("cygwin", launched.profile().id());
assertEquals("Cygwin Bash", launched.profile().name());
assertEquals(before, inheritedEnvironment); // parent environment unchanged
assertEquals(requestedDirectory, observedShellDirectory); // profile cd cannot override it
```

  Include missing Bash, timeout, quoting/metacharacters, custom mounts, HOME
  mapping and foreign UNC rejection. Custom Windows Bash retains old behavior.
- [x] **Step 2: Run the four targeted test classes; expect RED for new cases.**
- [x] **Step 3: Implement discovery and confirmation.** No executable guessed from
  PATH. When Cygwin is selected before first detection, confirm on the worker
  rather than preserving a premature fallback captured on the EDT. Retain the
  catalog generation/close guards and bounded wait.
- [x] **Step 4: Implement launch adaptation from Task 1's validated invocation.**
  Prepare Cygwin-specific environment and invoke its absolute `cygpath` off EDT,
  with a 5-second timeout and 64 KiB output bound. Arguments remain distinct.
  Honor AUTO/MANUAL/OFF integration behavior. Apply explicit requested cwd after
  user profile initialization; without one, honor the Cygwin home configuration.
  Shell-side OSC 7 conversion preserves the prior command exit status and URI
  escaping; Java reuses host/UNC validation without launching conversions on
  the reader thread. Acquire/release Task 3's launch reservation on all paths.
- [x] **Step 5: Run GREEN and repeat the native ConPTY probe** using production
  launch preparation, verifying script paths, home, mounts, native command
  execution, prompt navigation and `TERM`/`COLORTERM`.
- [x] **Step 6: Commit** `feat: add the Cygwin Bash shell profile`.

### Task 6: Add supervised package management and its UI action

**Files**
- Create `jasper-app/src/main/java/dev/jasper/app/cygwin/CygwinManager.java`.
- Extend `packaging/windows/cygwin/CygwinTool.java`.
- Create `jasper-app/src/main/java/dev/jasper/app/application/CygwinFeature.java`.
- Modify `application/JasperApplication.java`, `commands/ActionId.java`,
  `workspace/WorkspaceActions.java`, `workspace/WindowContent.java`,
  `settingsui/ShellChoices.java`, `settingsui/CorePages.java`.
- Create tests `cygwin/CygwinManagerTest.java`,
  `application/CygwinFeatureTest.java`, `settingsui/CygwinActionTest.java`
  under `jasper-app/src/test/java/dev/jasper/app/`.

**Interfaces**
- `CygwinManager.start(CygwinLayout)` starts an independent helper process and
  returns an operation identity; `status(identity)` returns active/result data.
- `CygwinFeature.manage()` dispatches off EDT and reports on EDT through
  injected callbacks; its lifetime belongs to `JasperApplication`.
- Add `MANAGE_CYGWIN_PACKAGES` to ActionId. Supply UI availability and invocation
  through callbacks; `ShellChoices` retains a delegating three-argument constructor.

- [x] **Step 1: Write tests** for Windows-only visibility, exact label/id, no
  shortcut, both entry points invoking one controller, absent/damaged root
  diagnosis, active sessions, duplicate invocation, cancellation, failed download,
  restart requirement, missing Bash after Setup, and disposed UI callbacks.
- [x] **Step 2: Run new targeted tests; expect RED.**
- [x] **Step 3: Implement supervisor and persistent result protocol.** Use a
  unique operation id and atomic result file, with Task 1's observed exit mapping.
  Distinguish cancellation from failure only where Setup provides evidence;
  report an ambiguous outcome honestly and check readiness afterward.
  The supervisor acquires Task 3's management reservation before launching Setup
  and retains it through completion. Its own runtime and jar must remain runnable
  independently of replaceable app files: copy the required helper/runtime into
  an operation directory outside both app and Cygwin root before starting, verify
  contents, then clean up after exit on a subsequent safe operation.
- [x] **Step 4: Wire action and settings affordance.** Keep non-Windows UI clean;
  generic ActionId enumeration must not inadvertently expose the action there.
  Refresh catalog on completion, including when the initiating window closed.
  Other shell profiles remain launchable while management is active.
- [x] **Step 5: Run child-process lifecycle tests.** Kill the parent app fixture
  while a stand-in manager remains active; a second helper and a Bash launch must
  still be refused. Updating/removing the old app fixture must not break the
  supervisor. Replace the verified manager only while idle, with atomic fallback.
  Real Setup UI acceptance is user-run.
- [x] **Step 6: Commit** `feat: manage Cygwin packages from Jasper`.

### Task 7: Integrate release verification and user documentation

**Files**
- Modify `.github/workflows/release.yml`, `.github/workflows/ci.yml`.
- Modify `README.md`, `jasper-app/README.md`, `docs/app-maintenance.md`,
  `docs/app-architecture.md`, `docs/README.md`.
- Finalize `packaging/windows/cygwin/README.md` and `gradle/cygwin.gradle`.

**Interfaces**
- `verifyCygwinDistribution` validates staged binaries, notices and exact
  corresponding source artifacts before release upload.
- Windows native probe tasks remain explicit; ordinary `check` runs only
  headless portable lifecycle/manifest/launch tests.

- [x] **Step 1: Write distribution checks** that fail for missing source archives,
  changed package bytes, missing manager/helper, and unvalidated target architecture.
- [x] **Step 2: Run verification against an intentionally incomplete fixture;
  expect RED.**
- [x] **Step 3: Wire source and binary artifact publication together.** Retain the
  existing Windows x64/ARM64 matrix. Require native evidence for each target;
  a skipped probe cannot green-light release. Keep retained snapshot acquisition
  separate from explicit refresh; ordinary CI does not resolve latest packages.
- [x] **Step 4: Document** selecting Cygwin, managing packages, Setup's Keep
  selection, closing Cygwin processes, internet requirements for extra tools,
  persistent paths, upgrade preservation, portable provisioning, uninstall
  preservation and manual removal. Add maintainer instructions for snapshot
  refresh, signatures, licenses, source retention, size measurement and recovery.
  Make the smallest edits to existing docs; put operational detail in the new
  packaging README.
- [ ] **Step 5: Run distribution checks and both architecture gates; commit**
  `docs: document and verify the bundled Cygwin lifecycle`.

### Task 8: Verify the integrated deliverable and obtain independent review

**Files**
- Update this plan's status/checkboxes and `docs/STATUS.md`.
- Create `docs/superpowers/plans/2026-10-20-jasper-cygwin-verification.md`.

**Interfaces:** completed feature and the evidence from Tasks 1–7.

- [x] **Step 1: Run `./gradlew check`** using JBR 25; read XML totals rather than
  guessing counts. Run `git diff --check` and the repository Java source-hygiene
  scan. Re-run only checks justified by failures or subsequent changes.
- [ ] **Step 2: Complete Windows end-to-end lifecycle verification.** On isolated
  test accounts, seed offline via both install routes; add a package, upgrade one,
  remove one, modify `.bashrc` and `/etc`, create a link and repository, and hash
  the root. Upgrade/repair/switch routes/uninstall/reinstall Jasper; compare
  snapshot contents before opening a shell (shell startup may legitimately
  update history). Test interrupted setup, corrupt cache, disk-full injection,
  simultaneous launch/management and app shutdown during management.
- [x] **Step 3: Hand the interactive checklist to the user.** Cygwin selection in
  tabs/splits/settings, Manage action in Settings/palette, Setup package changes,
  Ctrl+C/resize/Unicode, native programs, prompt navigation and cwd inheritance.
  Distinguish pending acceptance from failed or completed checks.
- [x] **Step 4: Request one independent whole-branch review** under the approved
  native workflow. Review ownership, rollback, process races, installer sequencing,
  source distribution and both Windows architectures. Fix findings and rerun the
  affected verification before marking complete.
- [x] **Step 5: Commit final evidence** as `docs: record Cygwin integration verification`.
  List remaining native/user acceptance explicitly. Do not merge or push as part
  of this planning authorization.

## Planning review and execution handoff

Coverage: spec sections 1/5 → Task 5; 2 → Tasks 1/2/7; 3 → Tasks 3/4/8;
4 → Tasks 1/3/4; 6 → Tasks 3/6; 7 → Tasks 1/7/8; 8 → documentation and file map.
All five Review Focus items have named regression coverage.

This is one end-to-end deliverable with a prerequisite evidence gate, not
independent features suitable for parallel implementation. Native inline
execution with one final independent review is retained from the approved
design. Task-level commits include the named tests and code plus the required
co-author trailer.

Planning deviation: complete production bodies and the MSI template override
are intentionally not invented before the design's mandatory Windows probes.
Task 1 finalizes those details and updates the status banner and STATUS before
dependent implementation. Review this plan now; execution starts only after
plan approval, and unavailable Windows evidence keeps dependent tasks gated.
