# Plan 4d — macOS and Windows packaging

**Status:** Approved by the user on 2026-09-12; implemented and reviewed through `de4ec8c`. Final forced full-suite/package verification passed. Integration and native desktop/Windows execution remain pending. See [verification](../plans/2026-09-12-moray-plan-4d-verification.md).

**Scope:** The user requested `jpackage` builds for macOS and Windows, with Windows desktop acceptance performed manually by the user. This advances packaging ahead of the broader app-logging cleanup. It extends the Phase 1 design's macOS `.app` deliverable to Windows without changing the terminal-first product scope.

## Delivery

Use Gradle tasks that invoke the selected JetBrains Runtime 25 SDK's `jpackage`. Build native packages on their target operating system and CPU architecture; do not attempt cross-compilation. Users of packaged Moray do not need a separate Java installation.

Proposed first outputs:

| Host | Application image | Distribution |
|---|---|---|
| macOS Apple Silicon or Intel, with matching JBR SDK | `Moray.app` | `.dmg` containing the application |
| Windows x64, with matching JBR SDK | `Moray/`, including `Moray.exe` and its runtime | Portable ZIP containing the whole `Moray/` directory |

The approved first Windows output is a portable ZIP. It avoids an installer dependency and permits testing by extraction and double-click. If an installer is selected, add an explicit Windows installer task using `jpackage` and document its WiX prerequisite. Do not silently replace the portable output or require WiX for portable builds. Windows ARM64 and Linux packaging are outside this first deliverable; ordinary Linux compilation and headless tests must remain available.

The first packages are development builds without Developer ID signing, Apple notarization or Windows certificate signing. Signing and release publication are separate follow-ups. Keep jpackage's default application icon for this first packaging deliverable; Moray branding can replace it later without changing the build architecture.

## Approach and alternatives

**Recommended: direct Gradle tasks around jpackage.** Reuse the repository's application plugin, dependency configurations and Java toolchain. Stage the runtime classpath, create one application image, then wrap that same image in the host distribution format. This makes the native command and output inspectable while adding no packaging-plugin dependency.

A third-party Gradle packaging plugin would shorten some task declarations but add its own compatibility and configuration layer. Shipping only Gradle's `installDist` output would be simpler, but would not provide the requested native application with a bundled runtime. Neither alternative is selected.

## Build contract

Add the packaging task definitions in a focused Gradle script applied by `moray-app/build.gradle.kts`. Keep the existing two Java modules and avoid a new runtime abstraction or installer framework.

- `:moray-app:packageApp` creates the native application image on the current supported host.
- `:moray-app:packageDist` creates the host distribution from that image.
- `:moray-app:verifyPackage` checks the packaged contents and launcher configuration without starting the desktop or a shell.
- `./gradlew check` remains headless and does not implicitly invoke packaging or require platform packaging tools.

Stage the application JAR, terminal JAR, all runtime dependency JARs, `config.example.toml` and a short package README in a dedicated build directory. Preserve dependency resources, including native libraries and bundled notices. The example is informational: packaging and verification must not install or overwrite a user's configuration. Native launch continues to load configuration through the existing `AppDirs` and `--config` paths.

Use the same Java 25 JetBrains toolchain for compilation, runtime generation and jpackage. Resolve `jpackage` from that installation instead of the shell's PATH. Retain all existing application JVM arguments, especially `--enable-native-access=ALL-UNNAMED` and the macOS application name. The entry point remains `dev.moray.app.Main`.

Generate the runtime from that SDK using jpackage's non-modular application support. Preserve service bindings explicitly with `--bind-services` in the jlink options: JDK 25 no longer includes them by default. Do not use a hand-pruned jdeps module list in this first version; reflective/native dependencies need verification before size optimization. Confirm the resulting runtime identifies as JetBrains and includes desktop/native support.

Native version correction: the selected macOS jpackage rejects a zero major version. Use a positive major version on both hosts rather than rewriting generated bundle metadata. This is package metadata for development builds, not a declaration of product completeness.

Metadata: application name `Moray`, initial package version `1.0.0`, macOS identifier `dev.moray.app`. Permit `-PmorayVersion=X.Y.Z`; reject values incompatible with native package versions before calling jpackage. Include OS and architecture in distribution filenames. Put all generated staging, application images and distributions under `moray-app/build/packaging/`.

Task inputs cover dependency files, the application JAR, bundled docs/config, version, platform, toolchain and launch options. Rebuilding must not retain removed dependencies or mistake a prior failed image for success. Any cleanup is limited to task-owned output directories. Use argument lists rather than shell command strings so repository paths containing spaces work on both platforms. Unsupported hosts or missing SDK tools fail with a direct message, without affecting ordinary `check`.

## Desktop launcher environment

Include the narrow launcher cleanup already recorded in STATUS because Dock launches may provide no locale and a development launch may inherit another terminal's identity.

In the existing `LaunchSettings.resolve` boundary, copy the inherited environment, remove inherited `TERM_PROGRAM`, `TERM_PROGRAM_VERSION`, `TERM_SESSION_ID`, `TMUX`, `TMUX_PANE`, and keys starting with `ITERM_`. Then apply the configured `[terminal.env]` overlay. Intentional configured overrides remain possible; `TERM` and `COLORTERM` retain their existing reserved behavior.

On macOS only, if the resulting `LANG` is absent or blank, set `LANG=en_US.UTF-8`. Preserve explicit nonblank locale choices and `LC_*` values; do not attempt to change the user's locale preferences. Windows and Linux receive no new locale default. Resolve the default shell from the inherited environment before the overlay, as today. Preserve working-directory selection, argument boundaries, capture timing, and session lifecycle. Do not start shell processes from build verification.

Update the configuration guide and example comments to describe these environment rules. Broader application logging remains a separate deliverable.

## Remote

The explicitly requested remote has been configured as `origin` at `https://github.com/an0nn30/moray.git`. `git ls-remote origin` succeeded and returned no refs; the GitHub repository is empty. No commits have been pushed.

Update the current repository instructions to reflect the configured remote. This task does not include a push, GitHub release, certificate setup or a CI deployment. The build commands must work locally on both hosts; a subsequent CI workflow can call those same commands.

## Verification and acceptance

Automated verification:

1. Add behavioral tests for inherited terminal-variable removal, explicit overlay preservation, absent/blank macOS LANG, explicit locale retention, Windows/Linux locale behavior and immutability of the inherited map.
2. Run the full headless suite and source/diff hygiene checks.
3. On this Mac, build the application image and DMG without opening either. Verify the bundle identifier/version, native launcher, main class, JVM options, complete staged classpath and bundled JBR runtime. Check native architecture and required native-library resources. Verify the DMG's integrity without mounting or launching it.
4. Check re-running packaging and a version override so stale output handling and versioned filenames are exercised. Verify paths with spaces through the packaging invocation. Test invalid version and unsupported-host validation at the build boundary where practical.
5. Record Windows tasks as unexecuted on this Mac. Source review and cross-platform unit tests are not Windows runtime acceptance.

User-run macOS acceptance: open the packaged application from Finder and the Dock; verify shell launch and UTF-8 text with a minimal launcher environment, configuration/Settings, tabs/splits, shell exits, native title controls and system theme changes. Test outside the repository so no source-tree or external-Java dependency can hide.

User-run Windows acceptance: build using `gradlew.bat` and a JBR 25 x64 SDK, extract the ZIP to a path with spaces, and launch `Moray.exe` from Explorer with no separately installed Java available to the application. Verify PowerShell/PTY input and resize, Unicode, clipboard, tab shortcuts, settings location and live reload, theme changes, multiple windows and shell-exit cleanup. If an installer is selected, also verify installation, shortcuts, upgrade and uninstall without deleting user configuration.

Headless tests do not establish native desktop acceptance. No GUI or benchmark is launched by agents.

## Implementation sequence

1. Implement and review the native Gradle packaging tasks with the local macOS artifact and verification command.
2. Implement and review launcher-environment behavior and configuration documentation.
3. Complete build instructions and both manual checklists, perform full verification, and run a whole-branch review before offering integration.

## Sources and baseline

- [JDK 25 packaging overview](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html): host-platform requirement, application images, Windows installer prerequisite, and JDK 25 service-binding change.
- Local JBR SDK `jpackage --help` and `java --list-modules` inspected on 2026-09-12.
- Baseline `dbf75d9`, branch `codex/plan-4d-packaging`, worktree `.worktrees/plan-4d-packaging`.
- Fresh worktree `./gradlew check`: all eight tasks executed, 506 tests, 505 passed, one known font skip, no failures or errors. No application launch.
