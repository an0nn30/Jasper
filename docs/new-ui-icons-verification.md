# New UI icon verification — 2026-09-27

Branch: `codex/intellij-new-icons`, based on `90c2e574`.
Worktree: `/Users/dustin/.codex/worktrees/intellij-new-icons/moray`.

- `./gradlew check :jasper-app:installDist`: passed. XML totals: **2,009 tests, 0 failures,
  0 errors, 5 skipped**. Architecture guards, compiled docs and Javadoc are included.
- Complete platform New UI plus Terminal collection: **1,744 SVGs / 885 choices**, pinned to
  intellij-community `60b8fbadc2f08ef18a6ecab0c138a849e14f13fb`. Every upstream byte hash and
  exact manifest/resource membership checked, including by the independent reviewer.
- All catalog entries parsed and painted in light/dark at 1x/2x. Tests cover source provenance,
  retained-instance live switching, differing variant dimensions, proportional fitting,
  disabled rendering, invalid IDs, SDK defaults, testkit selection and host loader isolation.
- Vault and SFTP regressions failed against their private painters, then passed using host
  semantic icons. The final reviewer caught an Escape action-key regression and an aspect-ratio
  problem in `welcome/open` (20×20 light, 21×20 dark). Each gained a failing-before/passing-after
  regression. Focused follow-up review reported no remaining actionable findings.
- Installed distribution jar contains all 1,744 SVGs and 885 catalog IDs. Java source hygiene and
  `git diff --check` passed. No native application/benchmark launched; no merge or push.

The KEY semantic retains its existing centrally tinted Tabler symbol because the upstream set
has no key artwork. The sample plugin retains its intentional custom-SVG API demonstration.
Title bar, layout sizes, themes and application branding are unchanged.

## Inspect or run

`python3 tools/icons/render-preview.py` produces the headless before/after contact sheet at
`jasper-app/build/icon-preview/comparison.png`. It uses the configured Gradle JBR toolchain,
creates no native window, and starts no terminal session.

From the worktree, the user can run `./gradlew :jasper-app:run`. The repository's development
launch uses its own `jasper-app/build/dev-home`, separate from the installed application's home.
Check toolbar/rail/tab/find icons, Vault controls, SFTP paging, disabled controls and a live theme
switch. The title row and toolbar should remain in their existing positions.

See [SDK usage](sdk-icons.md) for `Appearance.icon(IconName)` and `Appearance.icon(BundledIcon)`.
