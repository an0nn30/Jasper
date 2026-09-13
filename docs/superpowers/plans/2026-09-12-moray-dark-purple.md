# Moray Dark Purple implementation plan

**Goal:** Add `moray-dark-purple` and make it the default app/terminal palette and Follow System dark palette.

**Design authority:** User-requested Eclipse icon palette: near-black purple surfaces, white/gray text, violet accents. Keep ANSI hues distinct and readable. Preserve explicit `moray-dark`, `moray-light`, existing custom file semantics and light-mode behavior.

**Execution:** Bounded follow-up in the shared working tree on `codex/official-app-icon`, preserving the preceding uncommitted icon integration. The user's direct request authorizes the change. Test first, render actual UI headlessly, review, run checks and rebuild the Mac package; no GUI/installation/commit/push.

- [x] Test default parsing/loading, purple built-in resolution, classic backward compatibility, system light/dark transitions, coordinated app/terminal colors and readable ANSI colors.
- [x] Add `Palette.morayDarkPurple()`, make terminal defaults use it; map default `BuiltinTheme.DARK` to the new ID, retain `CLASSIC_DARK` for `moray-dark`. Add a purple FlatDarkLaf subclass/resource, preserving the existing classic resource.
- [x] Update selectors, file lookup, configuration seed/defaults/template and ThemeState dark-family resolution. Custom theme files retain their documented fixed Moray Dark inheritance, while custom chrome follows the new dark default. Existing explicit classic selectors remain classic.
- [x] Update source example/docs and theme-sensitive expectations. Render actual Swing components and terminal ANSI colors without a JFrame or login shell.
- [x] Run headless tests and native Mac package verification, independent review, then record evidence in STATUS and palette documentation.

**Completed:** Full headless check: 584 passed, one existing skip (585 total). Native Mac app/DMG verification passed. Independent review found no actionable issues. Actual UI renders and complete twenty-color TOML reference are documented in `docs/design/moray-dark-purple/README.md`. Existing pixel assertions were updated for the deliberate default-color change; classic expectations and fixed custom inheritance remain tested.

**Integration authorization:** After completion and review, the user explicitly requested merging the combined icon/theme changes to `main` and pushing to `origin`. This supersedes the earlier no-commit/no-push execution scope.
