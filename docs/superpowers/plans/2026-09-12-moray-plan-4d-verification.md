# Plan 4d verification and handoff

Verified on 2026-09-12, macOS aarch64, feature code through `de4ec8c` on `codex/plan-4d-packaging`. Worktree: `.worktrees/plan-4d-packaging`. Main remains `dbf75d9`; no merge or push was performed.

## Final automated verification

```text
./gradlew check :moray-app:packageDist --rerun-tasks
Verified Moray 1.0.0 macos-aarch64 image
Created moray-app/build/packaging/dist/Moray-1.0.0-macos-aarch64.dmg
BUILD SUCCESSFUL in 44s
13 actionable tasks: 13 executed
```

XML: 508 tests, 507 passed, zero failures/errors, one known skip:
`dev.moray.terminal.FontSetTest.fallsBackWhenPrimaryCannotDisplay()`.
Source hygiene passed all 152 Java files; diff hygiene passed.

The actual package verification checked copied dependencies/config/docs, main class,
JVM arguments, runtime classpath, Java 25/JBR identity and CPU architecture, PTY/JNA
native payloads, macOS bundle metadata, strict native bundle seal and DMG integrity.
Only the bundled Java version/properties command was executed; Moray and shell
sessions were not launched. Read-only verification leaves generated metadata intact.

Final default DMG: **82,099,743 bytes** (about 78 MiB).
Unpacked `.app`: approximately 190 MiB on disk.

SHA-256:

```text
2ad2f43a3c44e05f91a699f8e855214a42fcd81ea813641df1bcb2e1ee7e200a
```

Earlier task acceptance verified version 1.0.1, restoring default 1.0.0, an unchanged
up-to-date rebuild, invalid-version failure without changing the valid image/marker,
and a real source fixture in a path containing spaces. The final default artifact
includes the subsequent launcher-environment and documentation fixes.

## Reviews

- Task 1: implementation `e3e276f`, independent spec/quality approval.
- Task 2: implementation `c678ea9`, independent spec/quality approval with one minor documentation correction.
- Whole-branch review through `dc316d4`: four findings (Windows path separator,
  incomplete native checklist, environment wildcard wording, stale README).
- Combined fix `de4ec8c`: all four addressed; scoped re-review approved with no new
  breakage or open findings. Focused config/template/example tests: 27 passed.
- Final root verification above passed after the fixes. Review scratch files were
  removed after preserving this report, the specification, plan and status record.

## Decisions made during execution

1. Use package version **1.0.0**, with positive major required on both hosts. Actual
   macOS jpackage rejects a zero major version. This changes the proposed 0.1.0
   development numbering; it does not declare product completeness.
2. Determine linked-runtime vendor/architecture by querying its bundled Java and
   reading native files. jlink omits SDK-only release keys; adding those keys breaks
   the bundle seal. Verification adds a non-GUI runtime query and leaves all
   generated metadata untouched.

## Unexecuted and remaining work

Windows x64 packaging and unsupported-host execution were not run on this Mac.
Windows source review, including the corrected native path separator handling, is
not Windows execution evidence. Both platforms' desktop acceptance and the Phase 1
benchmark/daily-use gate remain user-run. See [build instructions and separate
native checklists](../../packaging.md).

An intermediate forced run during Task 1 observed a transient RIS snapshot NPE in
`TerminalLiveOptionsTest`; terminal source/tests were unchanged on this branch.
The targeted rerun, subsequent full runs and the final forced run above passed.
Track this as a test-reliability follow-up, separate from the known font skip.

App logging remains the next implementation deliverable. Developer ID signing,
notarization, final app artwork and CI/release publication remain separate work.
The configured origin is `https://github.com/an0nn30/moray.git`; the remote was empty
when configured. No commits were pushed during this task.
