# Documentation audit — 2026-09-21

Audited the three-module code at `9b30dc2` after both refactors were merged locally.
The maintained module READMEs, architecture and maintenance guides were checked
against owners, public signatures, lifecycle code, build tasks and regression tests.
Shared configuration, palette, diagnostics, packaging, benchmark and asset instructions
were checked against their loaders, defaults and scripts. Dated plans and measurement
records remain historical evidence; they are not rewritten as current implementation.
The [documentation index](README.md) explains that distinction and source precedence.

## Corrections

- Terminal: three-module onboarding; volatile metadata versus locked buffer reads;
  listener/error/exit ordering; row-local search, epochs and best-effort cancellation;
  explicit option reconstruction sites in `setPalette` and `setFontSize`.
- Application: real subscription type and scope API; setting owners and capture times;
  palette generation guards versus already-submitted side effects; empty-query history
  de-ranking, timestamp ordering and in-memory live capture; bounded application wait
  versus separate shutdown hooks.
- Buddy: dedicated architecture and maintenance guides; five supported top-level types;
  notice replacement/acknowledgement/orphaning; EDT, hide/close and lazy native lifetime;
  animation and presentation owners; 20-frame sprite and current module resource paths.
- Shared guides: current shortcut defaults and tab presentation, startup filesystem
  effects, standalone `--config` semantics and default data paths, implemented logging,
  current Silver Desk Buddy icon, dark/light themes and moved preview commands.
- Handoff: merged refactor status and completed review markers; resolved both terminal
  documentation follow-ups. Older STATUS entries and superseded render instructions
  are explicitly historical. Six broken local references were repaired or identified
  as unavailable historical artifacts rather than linked as present.
- Maintenance tooling: the Buddy generator now targets the actual library resource.
  `--verify-only` reads existing assets and does not overwrite the master or PNG.
  Existing documentation tests cover the shared guides and historical links. Markdown
  files are declared Gradle test inputs so docs-only changes invalidate cached checks.

No terminal, application or Buddy runtime algorithms were changed. Java edits are
contracts/comments and explanatory comments in the generated TOML template; the
asset generator's output path and documentation verification wiring are intentional
maintenance-tool changes.

## Verification

One full run exposed an existing test-ordering race in
`SessionLaunchCoordinatorTest.admittedSessionExitsRemainTrackedUntilPaneOwnerClosesThem`:
the copied exit future completed before its sibling removal callback. The test now
awaits the coordinator's existing drain future before asserting its registry is empty;
it retains the pane-close, exit and rejected-admission assertions. Production shutdown
code is unchanged. The failing run was followed by focused and full verification.


- Clean baseline `./gradlew check` passed.
- Full architecture/check/installDist verification passed: **1,127 tests, 1,125 passed,
  two expected skips, zero failures/errors** (app 609, Buddy 163, terminal 355).
- All repository Markdown local file links and heading fragments were scanned;
  the expanded existing guide tests check current/shared and archived document links.
  Marked snippets still match their compiled example sources.
- Javadoc doclint and both architecture guards passed. Missing-comment/tag warnings
  remain nonfatal; this audit does not claim every Java member has exhaustive Javadoc.
- Buddy `generate.py --verify-only` passed with Pillow: all 20 master frames match
  the runtime PNG in the Buddy module. No asset regeneration was performed.
- Source-character hygiene and `git diff --check` passed.

No GUI, native benchmark or Windows runtime check was performed. Historical native
measurements retain their original revisions; no current performance claim is inferred.
