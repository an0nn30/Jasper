# Compact Buddy activity

Status: user authorized design, plan and implementation on 2026-10-25.

## Intent and approaches

Make background activity calm and easy to inspect. Retaining three floating stacks would reduce
little of the visual load. Hiding all activity until clicked would lose useful awareness. The
user chose one compact summary plus a grouped drawer and less motion. Preserve tab-aware
suppression, stable notice identity, real titles, dismissal and activation targets.

## Collapsed summary

Show zero or one capsule for visible notices. No backing silhouettes, arrival travel, continuous
shimmer or elapsed timer. Jasper's existing sprite animation carries running activity. One notice
uses its current group title (or notice title) and a concise state label. Multiple ordinary
notices use accurate task/connection counts, e.g. "4 tasks" or "3 tasks · 1 connection", and
"Running" or "View activity" as appropriate. Counts describe visible notices only. The generic
Buddy library must not call arbitrary groups tabs. Actual tab titles appear in the drawer.

Attention priority: NEEDS_INPUT, DOWN, DEGRADED, then ordinary activity. With attention present,
show the highest-priority notice's title and state plus the count of other activities; never
infer attention from terminal text. Tie selection is stable while the selected notice remains
at the highest priority. Clicking the capsule always opens the all-activity drawer, even for a
single notice. Clicking Jasper does the same. Summary positioning follows Buddy and screen
clamping directly; small hover feedback may remain, but no spring movement or idle frame loop.

## Grouped drawer

One section per notice group, titled with its current group title and member count. All groups
start expanded; clicking their header collapses/expands inline. Ungrouped plugin notices remain
individual rows. Rows retain program titles, live detail, state, dismiss control and activation.
Duplicate pane titles retain stable disambiguators. Clear all dismisses all visible notices in
the drawer, including collapsed sections, but never suppressed notices. Label it "Dismiss all"
to distinguish dismissal from cancellation.

Keep groups and member rows in stable first-observed order while their identities are retained;
append new identities rather than promoting them on updates. The generic library has no tab-strip
ordering input, so do not pretend it can mirror workspace order. Preserve expansion and scrolling
on detail/title updates; reconcile membership changes and clamp scroll when content shrinks.
Drop ordering/expansion records when their retained identities disappear; bound memory by the deck.
Use the same computed row geometry for painting and hit tests; clip targets to the viewport.

Open deliberately, keep open across pointer exit, close on Escape, focus loss/outside interaction,
row activation, all visible work ending, or Buddy hide/dispose. A focusable drawer is acceptable
only when explicitly opened; refresh/drag/appearance updates must never steal focus. Ensure hidden
drawers cannot reopen on stale anchors or pending refresh. Release listeners/timers on hide/dispose.
Use a visible focusable Frame/Dialog owner for the drawer JWindow (active owner first, then a
visible eligible fallback), chosen only on explicit opening. Owned-window focus retains its
workspace owner activation. Ensure the drawer panel is focusable. Recreate the native drawer
only when its owner changes on explicit open. If no eligible visible owner exists, use an
undecorated focusable dialog fallback with the same buffered translucent rendering.
All details stay in this one drawer rather than separate filtered drawers. No new completion history.

## Constraints and verification

No SDK/facade changes, new dependencies, sprite changes, command-threshold changes, title guessing,
or delay policy in this iteration. Preserve Linux BuddySurfaceWindow rendering. Headless tests
cover counts, priority and stable ties, grouping/reparenting/collapse, stable order, duplicate names,
visibility, dismissal, scrolling/hit targets, focus/close lifecycle through production-used seams,
and no continuous animation demand. Generate light/dark summary and grouped-drawer previews and
inspect them. Update current guides minimally; dated historical records remain historical. Run
Buddy checks and full headless check with Python 3.13. GUI/AOT/benchmark runs remain user-run.
