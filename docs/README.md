# Jasper documentation

Use these guides for the code currently in the repository. Jasper's application composes
two independent libraries and hosts plugins written against `jasper-sdk`. Public Java
visibility inside the app does not constitute a plugin API; the SDK does.

## Contributor routes

| Module | Start here | Architecture | Maintenance and extensions |
| --- | --- | --- | --- |
| `jasper-terminal` | [Onboarding and embedding](../jasper-terminal/README.md) | [Owners, API and threading](terminal-architecture.md) | [Keys, protocols, options and lifecycle](terminal-maintenance.md) |
| `jasper-app` | [Onboarding](../jasper-app/README.md) | [Composition and feature ownership](app-architecture.md) | [Commands, scopes, settings, providers and platform hooks](app-maintenance.md) |
| `jasper-buddy` | [Embedding](../jasper-buddy/README.md) | [Facade, model and presentation](buddy-architecture.md) | [Notices, options, animation and assets](buddy-maintenance.md) |
| `jasper-sdk` and plugins | [SDK packages](../jasper-sdk/README.md) | [Loading, threading and lifetimes](sdk-architecture.md) | [Writing and testing a plugin](plugin-authoring.md) |

The [current handoff](STATUS.md) records integration and pending acceptance. The
[documentation audit](documentation-audit.md) records source checks and corrected drift.
[AGENTS.md](../AGENTS.md) covers toolchain, architectural constraints and unattended
work. Use the wrapper with a JetBrains Runtime SDK 25:

```sh
./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist
```

This runs headless checks, doclint, copied example tests and architecture guards,
then assembles the application distribution. It opens no native window. Native
`run` and benchmark tasks require explicit user authorization for coding agents.

## User and operational guides

- [Configuration](configuration.md): defaults, saved/live settings, data paths and shell integration.
- [Command palette](command-palette.md): commands, the bundled History and Snippets plugins, and scope registration.
- [Plugins](../README.md#plugins): where installed plugins live, File → Manage Plugins…, safe mode and `--plugin-dir`.
- [Diagnostics](diagnostics.md): log storage, bounds and privacy.
- [Packaging](packaging.md): native builds and user-run acceptance checklists.
- [Benchmarks](benchmarks.md): reproducible native measurement commands and interpretation.
- [Rebranding](rebranding.md): migrate Moray settings into Jasper.
- [App icon assets](../packaging/icons/README.md) and [Buddy sprite assets](../packaging/buddy/README.md).

## Specifications and historical evidence

The [original Phase 1 design](superpowers/specs/2026-09-10-jasper-phase-1-terminal-design.md)
is amended structurally by the approved
[terminal refactor](superpowers/specs/2026-09-20-jasper-terminal-refactor-design.md)
and [app/Buddy refactor](superpowers/specs/2026-09-20-jasper-app-buddy-refactor-design.md).
Use these together for requirements; later approved amendments take precedence.

Dated files under `superpowers/specs/` and `superpowers/plans/` preserve design and
implementation decisions. Their code blocks, intermediate names, old package paths
and task commands describe the stage at which they were written; they are not a
replacement for current maintenance recipes. Completed refactor plans record their
final deviations and review evidence. An unchecked native checklist remains unverified.

`design/`, `benchmarks/` and the [readiness report](terminal-readiness.md) preserve
screenshots and measurements from their recorded revisions. A historical success,
platform CI run or benchmark number does not establish acceptance of the current
refactored build. The [terminal](terminal-refactor-verification.md) and
[app/Buddy](app-refactor-verification.md) verification reports retain their original
counts and review findings, with later integration/follow-up sections explicitly dated.

- [Credential Vault](credential-vault.md): manager, credentials, grants, key generation and native acceptance.
- [Remote](remote.md): SSH hosts, import from ~/.ssh/config, host-key trust, shared sessions and the hosts panel.
