# Jasper integration handoff

Use `/Users/dustin/projects/moray/.worktrees/jasper-rebrand`, branch
`codex/jasper-rebrand`. The original checkout and `vanilla-swing` worktree remain
available with their original uncommitted work. Integration snapshots were made
with temporary Git indexes so neither source index needed to be staged or reset.

## Included work

- Rebrand snapshot `e365e8acd8ab9c872da7d97434c2db178411352e`:
  Jasper app name, `dev.jasper` packages, renamed modules/docs/assets, native
  packaging, and approved Silver Desk Buddy icon.
- Vanilla Swing snapshot `d09b82e09c0b5ab3fda76bf83f383f896e5c7350`:
  native title bar, standard Swing controls/tabs, OldGnome2 toolbar icons,
  built-in look-and-feel configuration, and classic white-on-black terminal.
- The tab-close-button follow-up that arrived in the vanilla source during
  integration: transparent 20px target with the supplied PNG visually centered.

Both snapshots share base `a42c20fd0ad274476d1ed9c51cba3c4676307661`.
Name-only conflicts resolve to Jasper; UI behavior resolves to vanilla Swing.
Removed custom-chrome/theme/motion implementations stay removed. The older
FlatLaf-only divider addition is superseded by each LAF's own divider painter.
The selected turtle icon and terminal implementation remain intact.

Motif is the combined default, following the earlier rebrand preference. Metal
remains selectable and is still the fallback for unavailable platform LAFs.
Settings use Jasper paths; see [migration notes](rebranding.md).

## Use and verification

```sh
cd /Users/dustin/projects/moray/.worktrees/jasper-rebrand
./gradlew :jasper-app:run
```

The command above is for the user: it opens the app and starts the login shell.
The agent used only headless checks and preview fixtures.

`./gradlew check :jasper-app:packageDist` passed after conflict resolution and
follow-up changes. XML totals: 598 tests, 597 passed, one existing font skip.
Both suites ran in this worktree; final terminal checks were up to date after
the first successful combined run. The reduced count versus the rebrand-only
branch reflects removal of obsolete custom-LAF/theme/motion tests with that UI.

The Mac bundle is `jasper-app/build/packaging/image/Jasper.app`; the distribution
is `jasper-app/build/packaging/dist/Jasper-1.0.0-macos-aarch64.dmg`. Package checks
verified the Jasper identity, native icon, launcher, bundled runtime and payload.
The ICNS, ICO and editable SVG match the selected rebrand assets exactly.
Production JARs contain no legacy package names.

[Headless previews](design/vanilla-swing/README.md) were regenerated for Metal,
Nimbus and Motif. Independent integration review and a scoped follow-up found
no actionable issues. Live Dock/titlebar behavior and native Windows packaging
remain user/platform acceptance checks. No GUI was launched and nothing was pushed.
