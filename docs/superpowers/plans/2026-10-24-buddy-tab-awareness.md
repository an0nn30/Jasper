# Tab-aware Buddy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Show live terminal work by workspace tab, hiding watched tabs and expanding split-tab stacks.

**Architecture:** Workspace publishes current parent-tab presentation as neutral values. CommandNotifier retains command lifetime independently of visibility; Buddy retains suppressed notices and projects visible groups into its existing column and drawer.

**Tech Stack:** Java 25/JBR, Swing, JUnit/AssertJ, Gradle wrapper; no new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-24-buddy-tab-awareness-design.md` (approved).

**Status:** execution authorized. Tasks 1–3 complete and reviewed (`a5a55a3c1`, `49e30f72e`, `5ef84d7c9`); Task 4 documentation and integration verification complete; whole-branch review clean (`e26b1c75b`). Full serialized check passed in 7m 50s: 12,865 tests, zero failures/errors, 29 skips, with installed uv Python 3.13. Task 3 review fixed stale scroll when a filtered drawer shrinks. Task 2 broad test run collided with a focused run in Gradle output; serialized focused verification passed, and the full-suite rerun is consolidated into Task 4. Deviation from the repository's complete-code convention: this plan pins public contracts, algorithms and behavioral tests, leaving detailed Swing rendering bodies and existing test-fixture adaptation to implementers. This avoids duplicating the implementation before its failing tests; record final deviations and results in STATUS. The dedicated current checkout is reused on `codex/buddy-tab-awareness` (base `f8aa4bca6`), preserving its existing shared-display-scaling ancestry. No additional worktree is needed.

## Global Constraints

- `jasper-buddy` uses only JDK and supported Buddy types; notification policy receives neutral values rather than workspace objects.
- Hide all terminal entries in the selected parent tab of an active, showing, non-minimized workspace window, regardless of pane focus.
- Zero long-command threshold disables command notices; completion notification policy remains unchanged.
- The compact column limit is three groups/ungrouped cards; use at most two backing silhouettes and exact running-pane counts.
- Preserve elapsed time, arrival order and command identity through suppression and layout moves. Explicit dismissal lasts for that command invocation.
- Preserve plugin SDK activities/connections and overall Buddy visibility/typing policy. Stand-up animation is a later deliverable.
- No GUI, benchmarks or AOT training. No merge or push. Commits end with `Co-Authored-By: Codex <noreply@openai.com>`.

## Review Focus

1. Split panes with focus in an editor or toolbar: whole parent tab stays suppressed (Task 2).
2. Moving tabs/panes between windows: preserve identity, dismissal, activation and timers (Task 2).
3. Prompt title immediately preceding a command: no stale shell title leaks into its bubble (Task 2).
4. Filtered drawer loses or hides its final member: close cleanly; Clear all affects only its displayed entries (Task 3).
5. Suppressed notices at retention capacity: bounded state, no accidental resurrection or callback retention (Task 1).

### Task 1: Generic retained notice context

**Files:** `jasper-buddy/src/main/java/dev/jasper/buddy/notice/BuddyNotice.java`, `internal/model/BuddyDeck.java`, `view/BuddyCompanion.java`; their tests under `jasper-buddy/src/test/java/dev/jasper/buddy/`; `jasper-buddy/src/testFixtures/java/dev/jasper/buddy/view/BuddyTestSupport.java`; architecture allowlists where needed.

**Interfaces:** Add nested `BuddyNotice.Group(BuddyNoticeId id, String title)` with nonnull id and nonblank title. Add nullable `Group group` as final record component; retain the existing six-argument constructor delegating with null. Add `BuddyCompanion.post(BuddyNotice notice, boolean visible)` and `updateContext(BuddyNoticeId id, BuddyNotice.Group group, boolean visible)`, both void/EDT/closed-inert like existing methods. The existing post defaults to visible. `BuddyDeck` mirrors these methods; `notices`, `size`, `isEmpty` describe its visible projection. Retained state is bounded to 50 including suppressed notices. No new top-level supported type.

- [x] Write failing notice/model/facade tests, using existing builders and EDT helpers. Pin these behaviors:

```java
var id = new BuddyNoticeId("terminal", "pane");
var group = new BuddyNotice.Group(new BuddyNoticeId("terminal-tab", "tab"), "Claude");
var notice = new BuddyNotice(id, BuddyNotice.Kind.TASK, "Claude", BuddyNotice.State.RUNNING,
    () -> "Running · 12s", () -> {}, group);
deck.post(notice, false);
assertThat(deck.notices()).isEmpty();
int generation = deck.generation();
deck.updateContext(id, group, true);
assertThat(deck.notices()).containsExactly(notice);
assertThat(deck.generation()).isEqualTo(generation);
deck.dismiss(id);
deck.updateContext(id, group, true);
assertThat(deck.notices()).isEmpty();
```

Also test updateTitle preserves group/suppression/order, old constructor is ungrouped, invalid groups fail, repeated context is inert, off-EDT and after-close contracts, suppress/restore among other notices, overflow eviction including hidden entries, and clear releases hidden state.

- [x] Run `./gradlew :jasper-buddy:test` and retain expected RED evidence.
- [x] Implement retained list plus suppressed ID set; cache a filtered visible snapshot. On post remove the old same-ID entry, set suppression atomically, promote once and evict oldest retained entry plus suppression metadata. On context/title update replace in place, never create missing IDs. On dismiss/clear drop retained entry and metadata. Renderers still see ordinary visible notices until Task 3.
- [x] Run `./gradlew :jasper-buddy:check verifyApplicationArchitecture` and record GREEN evidence; adjust supported nested-type checks/Javadocs as necessary.
- [x] Commit `feat: retain Buddy notice groups and visibility`.

### Task 2: Workspace titles and parent-tab visibility

**Files:** `jasper-app/src/main/java/dev/jasper/app/workspace/{WorkspaceActivity,TabHost,WindowContent,TerminalPane}.java`, optional focused `WorkspaceBuddyState.java` in the same package; `application/JasperApplication.java`; `notifications/CommandNotifier.java`; existing `CommandNotifierTest`, workspace activity/parent-tab/move tests and new `WorkspaceBuddyStateTest` if a helper is introduced. Update production-source inventory in architecture checks when adding a class.

**Interfaces:** Task 1 supplies `BuddyNotice.Group`, atomic visible post and updateContext. Add `WorkspaceActivity.Presentation(Object id, Object groupId, String groupTitle, String title, boolean watched, Runnable activate)` to its sealed events. Add `CommandNotifier.presentation(Object key, Object groupKey, String groupTitle, String paneTitle, boolean watched, Runnable activate)`; store current context even before a command starts. Group keys are stable parent-tab UUID values, never Swing objects. Group source is `terminal-tab`.

- [x] Write failing notifier regressions: watched command qualifies at threshold (working true) with no visible notice; hidden restores without elapsed/generation changes; return hides; finish hidden never restores; dismissal does not restore; a fresh command does; zero disables. Update earlier tests expecting watched long-running cards to reflect the approved suppression rule.
- [x] Write failing workspace regressions: two running panes in selected parent tab both watched; focus toolbar/editor still watched; switching parent tabs or window activity republishes all panes' state. Assert event groupTitle equals strip title and paneTitle equals current pane display label. Reproduce command-start after a shell prompt title, subsequent program OSC title, user rename and empty title fallback. Include mixed editor/terminal groups, pane move/whole-tab move/merge, current activation target, close and duplicate-title identities.
- [x] Run focused notifier/workspace tests and retain RED evidence.
- [x] Implement the presentation event from settled workspace state. Iterate all parent views and terminal items, using current membership and selected item's strip label for groupTitle. Derive watched from selected parent plus `isActiveAndOpen`, not selected pane. Emit initial context before Started, and refresh on committed layout/title/activation changes (respect existing `holding` move transactions). Use the existing tab display-title resolver for the pane label. Never seed from `TerminalPane.title()`'s stale reported prompt title. Route new events through JasperApplication; pane BLURRED no longer promotes notices in application wiring.
- [x] In CommandNotifier keep context separate from InFlight. Started captures the command's live clock, seeds display title/current activation and watched state, and schedules qualification. Promotion posts once with `!watched`; presentation changes update context and title in place, and promote unqualified hidden work. Completion/close clears the flight and retained notice; pane close also clears context. Activation resolves current ownership via the workspace's established registry seam. Calls lacking a presentation event remain ungrouped, honoring the Started watched argument. Preserve completion notification policy.
- [x] Run `./gradlew :jasper-app:test :jasper-buddy:test verifyApplicationArchitecture` (headless) and record GREEN evidence. Review all existing notifier/activity tests for policy changes versus unintended regressions.
- [x] Commit `feat: make Buddy terminal notices follow workspace tabs`.

### Task 3: Stacked column and filtered drawer

**Files:** `jasper-buddy/src/main/java/dev/jasper/buddy/internal/model/BuddyGroups.java` (new pure projection), `internal/presentation/{BuddyColumnPanel,BuddyColumnWindow,BuddyDeckPanel,BuddyDeckWindow,BuddyDeckLayout,BuddyCard,BuddyWindow}.java`; corresponding tests and a headless preview entry under test sources. Update architecture/source inventory for the new class.

**Interfaces:** `BuddyGroups` consumes the visible `List<BuddyNotice>` from Task 1. Expose immutable projected entries with stable group-or-notice identity, optional group metadata and ordered member list. Column consumes projected entries; drawer retains a nullable `BuddyNoticeId` group filter. Use callbacks to request opening a filtered drawer through existing presentation ownership. No facade exposes windows or model classes.

- [x] Add failing projection/layout/panel tests: same group collapses despite different member titles, identical labels in distinct groups do not merge, ungrouped plugin notice stays separate, group order follows newest retained arrival, three-card limit counts groups. Pin two backing layers maximum, full front-card hit target, stack click requests group expansion and never activates, singleton click activates, filtered drawer includes only group members and shows tab-title header, filtered clear preserves other groups and hidden entries, empty/suppressed group closes, one-member group stays usable, duplicate pane titles gain stable readable discriminators.
- [x] Run focused Buddy tests and retain RED evidence.
- [x] Build `BuddyGroups` with insertion-ordered grouping of the already newest-first visible list. Keep notice and group identity namespaces distinct. A grouped singleton retains group title in collapsed form and pane title expanded. Multi-member card title is group title and detail is exact `N running`; do not synthesize lifecycle notices or change arrival generation. Compute group changes from members and preserve animation identity through suppression/restoration.
- [x] Paint at most two offset backing silhouettes inside measured stack bounds, then the front bubble. Include backing extents in column geometry, screen placement and hit tests. Expand using the current drawer, filtered by group ID; clicking Buddy clears the filter. Maintain existing pointer-exit/activation/host-hide closure. Clear only currently displayed IDs, via dismissal; all-notices drawer displays all visible members with group context. Preserve per-entry dismiss and live elapsed detail. Stable duplicate-title labels derive from member identity/order retained independently of current filtered position.
- [x] Add headless preview fixture and render single bubble, stacked groups, filtered drawer and overflow (dark/light). Inspect emitted PNGs and correct clipping/overlap/hit-target problems. No native windows.
- [x] Run `./gradlew :jasper-buddy:check verifyApplicationArchitecture` and record GREEN evidence.
- [x] Commit `feat: expand Buddy tab stacks into running panes`.

### Task 4: Documentation, integration verification and final review

**Files:** smallest necessary edits to `jasper-buddy/README.md`, `docs/buddy-architecture.md`, `docs/buddy-maintenance.md`, `docs/notifications.md`, `docs/STATUS.md` and this plan. Generated SDK reference only if its inputs actually change.

**Interfaces:** Document the final supported Buddy signatures and tab behavior from Tasks 1–3. No plugin SDK change is planned.

- [x] Run `./gradlew check --continue -Pjasper.python=/Users/dustin/.local/share/uv/python/cpython-3.13.15-macos-aarch64-none/bin/python3.13`; use a different installed 3.11–3.13 path if needed. Count test-result XML, report every failure by name and fix relevant failures with RED/GREEN evidence. No need to rebuild external plugin unless SDK sources change.
- [x] Update current guides minimally for grouped notices, suppression, drawer actions and supported API; preserve dated records. Add STATUS entry with actual evidence, branch and GUI checks pending. Record plan deviations and exact results.
- [x] Run applicable documentation tests/checks after documentation edits, then `git diff --check` and Java source hygiene check for changed Java files.
- [x] Commit `docs: document tab-aware Buddy notices`.
- [x] Controller dispatches whole-branch review against `f8aa4bca6`, addresses findings with scoped verification, and records final evidence. User GUI acceptance remains pending. Do not merge or push. The stand-up animation proceeds as its own subsequent design/deliverable rather than being silently added here.
