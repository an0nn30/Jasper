# Buddy: tab titles, visibility and stacks

Status: proposed for approval. The user selected the combined deliverable on 2026-10-24;
the stand-up animation follows as a separate deliverable. Implementation has not started.

## Intent

Buddy identifies work running out of sight. Terminal work belongs to the visible workspace
tab containing its pane. Several running panes in that tab form one expandable stack.
The user approved hiding a tab's bubbles while that tab is visible in the active window,
and restoring them when switching away while the work continues.

## Approaches

We considered shipping titles and visibility before stacking, or delivering all three
together. The user selected the combined approach: one coherent tab-based interaction,
with titles, visibility and grouping driven by the same workspace facts. The smaller
alternative would temporarily preserve separate, ungrouped pane bubbles.

## Current behavior and boundaries

`CommandNotifier` retains live commands by pane, promotes them on a timeout or pane blur,
and forwards title changes to Buddy. Gaining focus does not hide a promoted notice.
`TerminalPane.title()` favors `reportedTitle`; the tab display uses `tabTitle()` and
`displayTitle`, which is cleared when a new command begins. This divergence is a likely
cause of stale shell titles and must be pinned by a regression test during implementation.

The visible tab is a `ParentTabId` and may contain several internal `contributions.Tab`
items. Its strip title comes from its selected item's presentation. Grouping and watched
state must use parent membership, rather than equality with the selected item.

The app owns workspace facts, command lifetimes and visibility policy. `jasper-buddy`
owns generic notice presentation and motion, with only JDK and supported Buddy types in
its API. Notification policy receives neutral values rather than workspace objects.
These retain the boundaries in the approved app/Buddy refactor amendment.

Plugins currently contribute through SDK activities and connections; Jasper translates
these into Buddy notices. Direct Buddy classes are unavailable to plugins. This
deliverable leaves that SDK contract intact and keeps unassociated plugin notices as
individual cards. A future SDK extension can associate plugin work with workspace tabs.

## Behavior

### Titles

- A collapsed terminal group displays the current visible tab-strip label, including a
  user rename, using the workspace's existing title resolution and plain-text handling.
- Expanded entries identify their own pane using its current terminal display label:
  program title when available, otherwise the existing command/shell fallback. The
  implementation shares the existing resolver rather than creating Buddy-only heuristics
  for recognizing Claude or stripping arbitrary title strings.
- A new command must not inherit the preceding prompt's title merely because it was the
  last reported OSC title. Subsequent program title changes update the entry and, when
  relevant, its group title. Existing titles are seeded when work becomes eligible.
- Rename and title changes update in place without replaying arrivals or changing order.
- Two identical titles remain separate identities. Expanded entries include a stable
  per-pane discriminator within the group when their display labels collide.

### Visibility and lifetime

- Hide all terminal entries belonging to the selected parent tab of an active, showing,
  non-minimized workspace window, regardless of which split pane or control has focus.
- Selecting another parent tab or deactivating that window reveals its still-running
  work immediately. Selecting a tab in an inactive window does not hide its work.
- A command started in a hidden tab appears immediately. A visible tab remains suppressed
  even after the long-command threshold. The existing zero setting disables these command
  notices; completion notifications retain their existing threshold and delivery policy.
- Suppression preserves the command's start time, title, identity and ordering. It is not
  completion or user dismissal. Work ending while suppressed never reappears.
- Explicit dismissal lasts for that command invocation; changing tabs must not resurrect
  it. A subsequent command in the same pane is new work. Clear all applies to the entries
  shown in the current drawer, including its group-filtered form.
- Keep typing intent independent of suppression: a command already qualifying as running
  continues to count while its bubble is hidden. Visible work qualifies at the existing
  threshold; hidden work qualifies immediately. Clearing or hiding bubbles does not end
  the command. The animation itself is unchanged in this deliverable.
- Preserve Buddy's existing overall enablement and window-visibility policy. This changes
  which bubbles are shown whenever Buddy is available, not when his desktop window exists.

### Stacks and expansion

- Group eligible terminal notices by the containing parent tab's stable identity, with
  source-qualified group keys. A single running pane displays one ordinary bubble.
- Two or more running panes display a front bubble with offset bubble silhouettes behind
  it and a running-pane count. Use at most two backing silhouettes; the count remains exact.
  Idle panes do not contribute to that count. The collapsed multi-pane detail is
  `N running`; elapsed times belong to the expanded entries.
- Clicking a stack opens Buddy's drawer filtered to that group without activating Jasper.
  It has the tab title as its header and one entry per running pane with its live elapsed
  time. Clicking an entry raises the owning window, selects its parent tab and focuses the
  pane. A single-pane bubble activates directly, preserving today's convenient behavior.
- Clicking Buddy still opens the all-notices drawer. The existing pointer-exit, activation,
  host-hide and empty-drawer closing behavior also applies to the filtered drawer.
- The compact column keeps its limit of three, now counting groups and ungrouped cards.
  Its overflow count reports groups/cards, and the all-notices drawer exposes everything.
  Groups sort by their newest member's arrival; members retain arrival order. Suppression,
  title updates and regrouping preserve those arrival times.
- Recompute membership after pane moves, tab merges, detachments and window moves. Activation
  resolves the current owner when clicked. Structural moves must not simulate command
  completion or restart. Closing a pane removes its entry; closing a tab removes its group.
- An open filtered drawer follows its group across window moves, updates as members leave
  or finish, and closes if that group empties or becomes suppressed. If reduced to one
  member, it can remain open until its normal closing condition.
- Stack arrival/expansion uses the existing Buddy motion style;
  exact offsets and timing are left to implementation and headless visual review.

## Implementation shape and builder decisions

Publish a coherent workspace snapshot or equivalent neutral events containing pane identity,
parent group identity, group title, pane title and watched state. Refresh after title,
selection, activation and committed layout changes, including both ends of a move. Avoid
intermediate detach states flashing cards. The builder chooses the event/snapshot seam
after inspecting workspace transaction hooks.

Retain command lifecycle and user-dismissal state separately from the visible projection.
Extend Buddy's supported values/facade minimally for generic group metadata and suppression;
keep existing ungrouped callers working. The precise value types and method names are left
to the plan, with architecture allowlists and API tests updated together. No app types or
SDK types enter Buddy, and no app caller reaches Buddy internals.

The interaction details above beyond the user's explicit grouping and visibility choices
are proposed defaults: filtered drawer, single-card activation, counts, ordering, dismissal
semantics and preserved typing intent. They are reviewable choices, not prior user rulings.

## Verification and delivery

Use failing regression tests for title provenance and parent-tab visibility; exercise
multiple panes, focus within a tab, another active window, restored work, zero threshold,
completion while hidden, dismissal, rename, duplicate titles and layout moves. Test grouping,
filtered expansion, hit targets, overflow and shrinking groups in Buddy's headless suites.
Render headless previews for a single card, stacked groups, a filtered drawer and overflow.
Run the relevant architecture/doc checks and `./gradlew check` with supported Python.
Update current Buddy guides with the smallest accurate edits and record the result in STATUS.

After approval, write a complete-code implementation plan and use the repository's per-task
implementer/reviewer workflow plus a whole-branch review. Work on a dedicated branch; no
push or merge is authorized. GUI acceptance is handed to the user: run a titled program
such as Claude CLI, switch tabs/windows, expand a split-tab stack, activate a pane and move
the tab. No unattended GUI, benchmark or AOT training is part of this delivery.
