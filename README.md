# Moray

A cross-platform (macOS, Linux, Windows) terminal workstation written in Java Swing, laid out like MobaXterm. Phase 1 — a local terminal good enough to replace iTerm2 / Alacritty — is in progress: two of its four implementation plans are done. Today Moray is a single window running your login shell with ligatures, fallback fonts, truecolour, mouse reporting, scrollback, selection, copy/paste, search (API), shell integration and clickable links. Status and next steps: [`docs/STATUS.md`](docs/STATUS.md).

## Requirements

- **JetBrains Runtime 25** (a JDK build; download a `jbrsdk` from the [JetBrainsRuntime releases](https://github.com/JetBrains/JetBrainsRuntime/releases)). Gradle's toolchain looks for vendor JetBrains, language version 25; on macOS it finds JBRs registered under `~/Library/Java/JavaVirtualMachines`.
- Nothing else: the Gradle wrapper (9.7.0) downloads Gradle, and dependencies come from Maven Central and JetBrains' `intellij-dependencies` repository.

## Build, test, run

```bash
./gradlew check
```

```bash
./gradlew :moray-app:run
```

```bash
./gradlew :moray-app:bench
```

`check` runs all tests headless. `run` opens a window with your login shell. `bench` generates ~100 MB of ANSI-coloured text once, `cat`s it through a Moray window and prints MB/s (41.5 MB/s on an Apple M5 Max at the end of plan 1; the gate is 35 MB/s).

## Layout

```
moray-terminal/   the terminal component (dev.moray.terminal): session, emulator wiring, view, painter, input, selection, search, shell integration
moray-app/        the application (dev.moray.app): Main, DefaultShell, Bench
docs/STATUS.md    where things stand — start here
docs/superpowers/specs/   design spec (binding authority)
docs/superpowers/plans/   implementation plans (1 and 2 done)
AGENTS.md         conventions for coding agents (CLAUDE.md imports it)
```
