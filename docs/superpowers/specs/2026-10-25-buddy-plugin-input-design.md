# SDK activity input and unified Buddy working intent

Status: design, plan and build authorized by the user on 2026-10-25.

## Intent and approach

Extend the existing activity lifecycle rather than adding direct plugin control of Buddy. Plugins
already own background task identity, progress and activation. Add explicit input/resume states;
aggregate running intent at application composition. A separate Buddy-specific plugin API would
split task lifecycle ownership and is rejected. Grouping, cancellation and new artwork are deferred.

## SDK lifecycle

Add ActivityHandle.needsInput(String detail) and resume(String detail), safe from any thread.
Add ActivityEvent.State.NEEDS_INPUT and RESUMED. Begin starts running; needsInput moves to waiting,
resume moves to running. Both preserve id, source, title, fraction and activation action. Neither
is terminal or creates an outcome notification. Repeating the current phase may update detail
(coalesced) but does not emit another phase transition. progress/detail never implicitly resume.
While waiting their coalesced latest snapshot/event remains NEEDS_INPUT; while running it is
PROGRESS. current() includes waiting activities, with the latest accurate phase and fraction.

Phase transitions are delivered in order and never coalesced away. Pending progress/detail at a
phase boundary may be dropped, as at termination, but cannot arrive after the newer phase and
change its meaning. Serialise per-handle state and queue ordering together; concurrent callers
must not enqueue old progress after a newer phase or terminal event. Exactly one terminal event;
all subsequent valid reports are inert, including after plugin shutdown. Ending a waiting task
uses the existing success/failure/cancellation outcome policy. Runtime and testkit must agree,
including held handles after stop. Preserve old constructors and ordinary activity behavior.

Increment SDK 0.21.0 to 0.22.0 for the new lifecycle surface, add @since tags and update versioned
release references using repository conventions. New callers require the new SDK version. Document
new enum cases for activity-event subscribers; keep the record constructor unchanged.

## Buddy integration

Map waiting to Buddy NEEDS_INPUT and resumed to RUNNING. Retain the same notice, detail supplier,
activation and ordering. State updates do not resurrect dismissed/evicted notices or alter their
visibility. Add a narrow BuddyCompanion.updateState(id,state) facade operation backed by the deck,
if needed to update retained notices without posting again; validate kind/state compatibility.
The attention summary already prioritizes NEEDS_INPUT. Users click its drawer row to run the
plugin's registered action; plugin owns collecting input and explicitly resumes when ready.

ActivityNotifier maintains live plugin task phase separately from visible notices, reports whether
any task is running, and removes entries on terminal events/close. Dismissal and Buddy visibility
must not affect intent. Connections never contribute. Application BuddyIntegration combines
terminalWorking OR pluginWorking and forwards only aggregate changes to the companion. Waiting
last running task causes the existing stand-up; resuming starts existing laptop opening. One
producer finishing cannot stop animation while the other still runs. Preserve terminal thresholds
and qualified work in visible tabs. Guard late callbacks after close; shutdown clears intent.
SDK types stay inside app.plugins; notifier uses neutral app values/callbacks.

## Verification and docs

Shared runtime/testkit contract tests pin start/progress/wait/detail/resume/end, repeated phase,
accurate current snapshots, retained fraction/action, ordered rapid transitions, terminal suppression,
shutdown and held handles. Deterministic concurrency/order regression tests cover queue boundaries.
Notifier/facade tests cover state mutation, dismissal, working aggregation and multiple tasks;
composition tests cover terminal+plugin combinations and close. Existing sample/docs gain a compiled
input/resume example using a real action seam, without inventing plugin UI or adding dialogs.
Update generated sdk-reference via sdkReference and minimal current guides/STATUS. Run targeted
checks, full headless check with Python 3.13 and the external-plugin SDK build required by AGENTS.
No GUI/AOT/benchmark/merge/push. Native visual acceptance remains user-run.
