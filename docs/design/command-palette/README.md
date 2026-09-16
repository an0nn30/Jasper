# Command palette verification

These images render the real `WindowContent`, `JRootPane`, terminal component,
command registry, in-memory history, and palette overlay. The fixture creates no
`JFrame` and never starts the user's login shell. On macOS and Linux it runs the
repository's controlled `/bin/sh` test command, then closes the owner and waits for
every session exit future.

The recents and `pane` search use real built-in commands. The narrow-layout case
registers five harmless preview-only Swing actions with deliberately long labels;
they exist only in the test-runtime renderer and do nothing if invoked. The
synthetic entries make truncation deterministic without adding product commands.

The History states use a real `ShellHistoryIndex` with no file sources, driven
entirely by fifteen synthetic entries recorded through `index.record(...)` on an
inline executor (worker and delivery both run on the calling thread, so every
entry is indexed before the palette opens) — the same pattern as
`ShellHistoryIndexTest.inline`. Two of the fifteen commands start with `git`;
the thirteenth (`brew upgrade`, inside the five-row cap) carries a synthetic working
directory to exercise the detail line;
the rest carry no directory. Every third synthetic entry is bash and the rest are zsh, so every row is tagged.

| State | Dark | Light |
|---|---|---|
| Three recents | [1×](recents-dark-900x600-1x.png) / [2×](recents-dark-900x600-2x.png) | [1×](recents-light-900x600-1x.png) / [2×](recents-light-900x600-2x.png) |
| `pane`, five results | [1×](pane-query-dark-900x600-1x.png) / [2×](pane-query-dark-900x600-2x.png) | [1×](pane-query-light-900x600-1x.png) / [2×](pane-query-light-900x600-2x.png) |
| No match | [1×](no-match-dark-900x600-1x.png) / [2×](no-match-dark-900x600-2x.png) | [1×](no-match-light-900x600-1x.png) / [2×](no-match-light-900x600-2x.png) |
| Long labels, 360×500 | [1×](long-labels-narrow-dark-360x500-1x.png) / [2×](long-labels-narrow-dark-360x500-2x.png) | [1×](long-labels-narrow-light-360x500-1x.png) / [2×](long-labels-narrow-light-360x500-2x.png) |
| History, most recent (15 entries, the newest 5 shown) | [1×](history-recent-dark-900x600-1x.png) / [2×](history-recent-dark-900x600-2x.png) | [1×](history-recent-light-900x600-1x.png) / [2×](history-recent-light-900x600-2x.png) |
| History, `git` query, two results | [1×](history-query-dark-900x600-1x.png) / [2×](history-query-dark-900x600-2x.png) | [1×](history-query-light-900x600-1x.png) / [2×](history-query-light-900x600-2x.png) |
| Scope picker (`>`) | [1×](scope-picker-dark-900x600-1x.png) / [2×](scope-picker-dark-900x600-2x.png) | [1×](scope-picker-light-900x600-1x.png) / [2×](scope-picker-light-900x600-2x.png) |
| Snippets, five of six shown | [1×](snippets-dark-900x600-1x.png) / [2×](snippets-dark-900x600-2x.png) | [1×](snippets-light-900x600-1x.png) / [2×](snippets-light-900x600-2x.png) |
| Snippet fill-in step (`Deploy`, two fields) | [1×](snippet-fill-in-dark-900x600-1x.png) / [2×](snippet-fill-in-dark-900x600-2x.png) | [1×](snippet-fill-in-light-900x600-1x.png) / [2×](snippet-fill-in-light-900x600-2x.png) |
| History save-name step (`npm run dev`) | [1×](history-save-name-dark-900x600-1x.png) / [2×](history-save-name-dark-900x600-2x.png) | [1×](history-save-name-light-900x600-1x.png) / [2×](history-save-name-light-900x600-2x.png) |

The 1×/2× labels above describe output pixels: both paint the same logical Swing
geometry. A separate fresh JVM with `flatlaf.uiScale=2x` produced the
[actual UI-scale render](actual-ui-scale-2x.png) and [dimension record](actual-ui-scale-2x.txt).
The assertions measured a 1120px preferred card width, 112px input row and 80px
result row, exactly twice the 560/56/40 logical geometry and therefore scaled once.

All 28 matrix PNGs and the UI-scale image were inspected with the image tool. The
controller independently inspected a cross-theme/state subset. The first render
pass exposed blank command rows because the null-layout cell renderer had not laid
out its labels at the final paint width, and it showed the Escape hint stretched
through the full input row. Focused component regressions reproduced both issues.
The renderer now lays out at paint time and a transparent wrapper centers a compact
Escape hint. The regenerated matrix has visible titles and badges, preserves badges
before truncating narrow titles, stays horizontally centered in the upper half, and has readable selection and
muted text in dark and light.

The three new History/picker states were inspected the same way. In both History
states the chip reads "History" with its clock icon, the footer shows "⏎ Paste
⌘⏎ Paste and run", and every row's shell tag sits right-aligned at the row's far
edge. `history-recent` shows only the newest five of the fifteen entries (the
`palette.max_results` hard cap, no scrolling), tags mixing `bash`/`zsh`, and a
muted detail line under the `brew upgrade` row reading
`/Users/preview/projects/moray` — the only row with
one; numbered badges (⌘1–⌘5) appear on the first five rows only, and the rest
show no badge. `history-query` filters to the two `git` commands with the same
chip, footer and tags. `scope-picker` shows the "Commands" chip (unchanged,
since typing `>` opens the picker without switching the active scope), a
"Scopes" section label, and two rows — Commands and History — each with its icon,
description and application shortcut as its tag. No footer shows in the picker,
since the underlying active scope (Commands) has one verb.

Native focus, IME behavior, accessibility announcements, window deactivation and
physical-display placement cannot be inferred from headless PNGs. They remain
unchecked in the [native acceptance checklist](../../superpowers/plans/2026-09-12-jasper-command-palette-manual-check.md).

## Reproduce

From the repository root, choose an absolute output directory:

```bash
OUTPUT="$(pwd)/docs/design/command-palette"
./gradlew :jasper-app:commandPalettePreview --args="$OUTPUT"
./gradlew :jasper-app:commandPalettePreview -Pjasper.uiScale=2x \
  --args="$OUTPUT --expect-ui-scale=2"
./gradlew :jasper-app:commandSearchMeasurement --args="$OUTPUT"
./gradlew :jasper-app:shellHistorySearchMeasurement --args="$OUTPUT"
```

These tasks are opt-in and are not dependencies of `check`. The UI task is
headless; the output-pixel scaling is separate from the fresh-JVM FlatLaf scale
property. The [search report](search-measurement.md) uses 1,000 entries indexed
once, 5,000 warmup calls and 10,000 measured calls for exact, prefix, fuzzy and
zero-match queries. Its workstation numbers describe matching only, not native
key-to-paint latency, and set no CI threshold.

The [shell-history search report](history-search-measurement.md) covers
`ShellHistoryScope.search` alone over a synthetic 50,000-entry snapshot (200
warmup calls, 1,000 measured calls per query), for an empty query, a two-word
query, a query that matches by directory-adjacent module number, an exact
trailing command and a query with no match. It records medians only and sets no
CI threshold, matching the command-search report's descriptive intent.

## Final verification and review

All six task reviews are approved. Whole-branch review of `1db31b1..a12d851`
found one small-window issue: rebuilding a scrolled list could leave its selected
command outside the viewport. Fix `28a7ff0` updates bounded viewport geometry and
reveals the selected row. Query-reset, preserved-ID reorder and result-growth
regressions passed after reproducing the defect. Scoped review approved the fix
with no new breakage; no review findings remain open.

Fresh `./gradlew check --rerun-tasks` after the final code change executed all eight
tasks: app 365 passed; terminal 295 passed plus one existing font skip. Total:
661 tests, 660 passed, one skipped, zero failures/errors. The controller independently
read the XML. Existing negative-fixture logs remain documented; no palette output
or unrelated fixture cleanup was introduced. Source hygiene and final branch-wide
diff checks passed. The accidentally tracked temporary Task 1 report was removed
with final bookkeeping; the useful evidence and decisions remain here.

The renders above verify the theme/geometry fixes in `8e7292c`; the final correction
only changes selected-row scrolling, covered by actual Swing viewport assertions.
Search code is unchanged from the measured revision. Native acceptance stays
unchecked in the linked checklist. Following user-approved integration on 2026-09-13,
use the main checkout; the completed feature worktree is being cleaned up.

## Durable execution record

The feature used the existing project-local ignored worktree and branch so `main`
remained unchanged. Plan source blocks were treated as recipes below the approved
design; proven defects received focused regressions and are recorded here rather
than being mechanically preserved. Task 4 excluded the future `command_palette`
ID by string until Task 5 added the enum member, preserving sequential builds.

The app-level byte-isolation check used the existing controlled PTY on macOS
because `FakeConnector` is intentionally package-private to the terminal module;
synthetic routing retains cross-platform coverage, while native Windows PTY input
remains manual. Command-history decoding reports malformed UTF-8 instead of
silently replacing it. Persistence is staged before callbacks so a reentrant
listener close cannot discard an accepted command.

Key routing tracks press provenance so a fresh foreign press is not swallowed by
an older owned typed tail. An EDT-confined roster of installed window dispatchers
gives a held sequence's residual events priority across windows; disposal removes
the owner. These choices preserve owned-tail containment without creating a public
input or terminal testing API. Existing unrelated negative-fixture output was not
expanded into baseline cleanup.

### Execution decisions and tradeoffs

- use the existing branch in a project-local ignored worktree — follows the user's established subagent/worktree workflow while preserving main — no runtime behavior cost.
- implementation code in the plan is a recipe, not authority above the spec; correct proven source defects with TDD and record changes in the plan status and STATUS — avoids mechanically preserving bugs — may require scoped re-review.
- Task 4 can expose and test palette through its controller before Task 5 introduces COMMAND_PALETTE; exclude the future ID by string as already specified — preserves sequential compilability — no shipped behavior difference.
- Task 5 may prove terminal-byte isolation using the existing controlled PTY fixture where FakeConnector is inaccessible across the module boundary — avoid adding a public terminal testing API or a JediTerm dependency to jasper-app — synthetic routing remains cross-platform, PTY integration is OS-qualified. Record exact evidence and platform limitation.
- Task 2 may replace new String UTF-8 decoding with a strict REPORT decoder if malformed bytes otherwise survive in TOML comments — malformed-history policy requires rejection rather than silent replacement — no valid history behavior changes. Sent to implementer for regression-backed confirmation.
- stage/pump persistence before listener notification in record and loaded, with real reentrant close tests — required to preserve accepted history and close contract — changes callback order internally only.
- correct blanket typed-event swallowing if an intervening fresh foreign press proves the recipe consumes unrelated input — preserve both owned-tail containment and window-scoped fresh input — requires a small provenance state and regression, with native sequence acceptance still pending.
- permit an EDT roster of installed window palette dispatch owners to give callback-free owned tails priority over any new owner action — KFM registration order must not let an older destination execute a held key from a newer source — adds bounded shared dispatcher bookkeeping requiring cleanup and multi-window regression coverage.
- restore selected-row visibility after result rebuild and updated layout, including query reset and availability reorder — approved small-window behavior overrides the recipe omission — adds bounded layout/scroll work on result changes, verified by actual viewport regressions.

## Placement adjustment — 2026-09-13

The user requested a higher position. The card now targets one-third of the way
down the full terminal area with its vertical center, while retaining horizontal
centering and the existing small-window clearance clamps. The PNGs and separate
2× UI-scale artifact were regenerated for this placement.

## Main integration — 2026-09-13

The user approved merging and publishing the palette, including the higher placement
in `c4f3335`. The conflict-free merged result passed `./gradlew check --rerun-tasks`:
all eight tasks executed in 18 seconds, 365 app tests passed, and 295 terminal tests
passed with one existing font skip (661 total; no failures/errors). Native acceptance
remains user-run.

## Palette scopes and shell history — 2026-09-15

Task 11 of the [palette scopes plan](../../superpowers/plans/2026-09-15-jasper-palette-scopes.md)
extended this matrix with the three History/picker states above and added
`ShellHistorySearchMeasurement`. The preview fixture switches the already-open
palette between scopes with `owner.commandPalette().open(scenario.scope)` before
setting each scenario's query text, reusing the one `WindowContent`/`JRootPane`
instance across all seven scenarios. Both `commandPalettePreview` runs (default
and `-Pjasper.uiScale=2x --expect-ui-scale=2`) passed their in-process assertions,
including the 15-entry/12-visible split for `history-recent`, the two-row `git`
filter for `history-query`, and the two-scope picker listing for `scope-picker`.

Fresh `./gradlew check --rerun-tasks` executed all eight tasks in 18 seconds:
jasper-app 437 tests passed; jasper-terminal 302 tests, 301 passed and one
existing font skip. Total 739 tests, 738 passed, one skipped, zero
failures/errors — the same total as before this task, since it adds no new
`@Test` methods. Source hygiene over both modules and `git diff --check` passed.
No GUI, merge or push was performed.

## Palette snippets — 2026-09-16

Task 6 of the [palette snippets plan](../../superpowers/plans/2026-09-15-jasper-snippets.md)
extended this matrix with the three Snippets/save-step states above. `Fixture.create`
writes a `snippets.toml` with six snippets into a temporary directory
(`Files.createTempDirectory("jasper-preview-snippets")`, deleted in `close()`), two
of them carrying placeholders (`Rebase onto main` has `{{branch}}`; `Deploy` has
`{{env}}` and `{{tag}}`), and opens a `SnippetStore` over it with the same inline
worker/delivery executor used for the synthetic history so every snippet is indexed
before the palette opens. The store is the twelfth `WindowContent` constructor
argument, matching the production wiring.

`snippets` shows the Snippets scope with its bookmark chip, the five-of-six row cap
(`palette.max_results`), an `n field`/`n fields` tag on each row with placeholders,
the command text as a muted detail line under each name, and the three-verb footer
"⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Edit file". `snippet-fill-in` selects the `Deploy` row with
`card.selectRow(SnippetsScope.rowId("Deploy"))` and presses Enter
(`owner.commandPalette().enterPressed(0)`); the card shows the snippet name
("Deploy") as its heading and two labelled fields, `env` and `tag`, in the order
the placeholders first appear in the command. `history-save-name` opens the
History scope and presses Shift+Enter's verb (`owner.commandPalette().enterPressed(2)`)
on the default-selected newest entry, `npm run dev`; the card heads with "Save as
snippet: npm run dev" and one field, `Name`, prefilled `npm run` by
`ShellHistoryScope.suggestedName`. All twelve new PNGs (three scenarios, two
themes, two pixel scales) were inspected with the image tool and match this
description; the existing seven scenarios were re-rendered by the same run and
were spot-checked for no unintended change, including the scope picker, which now
lists three rows (Commands, History, Snippets) instead of two.

Fresh `./gradlew check --rerun-tasks` executed all eight tasks: jasper-app 464
tests passed; jasper-terminal 303 tests, 302 passed and one existing font skip.
Total 767 tests, 766 passed, one skipped, zero failures/errors — the same total
as before this task, since it adds no new `@Test` methods (`CommandPalettePreview`
is an opt-in `main`, not a JUnit test). Source hygiene over `jasper-app/src` and
`git diff --check` passed. No GUI, merge or push was performed.
