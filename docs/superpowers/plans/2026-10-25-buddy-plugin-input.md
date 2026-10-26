# Buddy Plugin Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development task-by-task.

**Goal:** Explicit plugin input/resume states and truthful combined Buddy working animation.
**Architecture:** Existing SDK activity lifecycle, runtime and fake own state/ordering; app bridge maps states;
ActivityNotifier publishes plugin-running intent and BuddyIntegration combines it with terminal intent.
**Tech Stack:** JDK-only SDK/Buddy, Java25 JBR, shared JUnit contracts, generated SDK reference.
**Spec:** docs/superpowers/specs/2026-10-25-buddy-plugin-input-design.md

Status: complete on `codex/buddy-plugin-input`; task, whole-branch and scoped fix reviews passed. Full check passed in 8m 5s (13,054 tests, zero failures/errors, 30 skips); external-plugin check/pluginZip passed. Test-only SDK-version fixtures and File Server handle doubles were corrected after the first full run. Clean existing checkout reused on the dedicated branch.
Deviation: implementation bodies delegated; exact contracts below replace duplicate production code. Initial lifecycle RED was missing-API compilation; the Sample activation example also has a behavioral RED. The compiled example adds a Tools confirmation action under the existing `demo_ui` setting.

## Global Constraints

- needsInput(String) and resume(String), NEEDS_INPUT and RESUMED, same ActivityEvent record signature.
- progress/detail preserve current phase; repeated phase coalesces detail, not another transition.
- Every actual phase transition ordered/noncoalesced; no stale progress or post-terminal resurrection.
- Preserve fraction/id/source/title/action; current includes waiting work; runtime/fake agree after stop.
- SDK0.22.0; generated reference and external-plugin build required.
- Retained Buddy state mutation preserves dismissal/visibility; no plugin control of presentation.
- Terminal OR running-plugin intent; waiting and connections excluded; late close callbacks inert.
- No new grouping/cancellation/artwork, GUI/AOT/benchmark/merge/push.

## Review Focus

- Rapid wait/resume and progress queued around boundaries must not reorder phases.
- Old held fake/runtime handles after plugin stop must not resurrect work.
- Dismissed waiting/resumed work must stay dismissed while still affecting actual running intent.
- Mixed terminal/plugin completions must keep animation until the last running source ends.
- API version/docs/examples must match generated release/testkit artifacts and external-plugin consumers.

### Task 1: End-to-end lifecycle and animation integration

Files: sdk/activity ActivityHandle, ActivityEvent, Activities and JasperSdk version; app/plugins
ActivityHub, PluginRuntime; sdk-testkit FakePluginHost and PluginContractTest; app notifications
ActivityNotifier; application BuddyIntegration/JasperApplication; BuddyCompanion/BuddyDeck state
update and tests; existing sample/docs and SDK reference sources/generated output as needed.

- [x] Add failing shared contract tests: STARTED -> progress(.4) -> NEEDS_INPUT -> detail still NEEDS_INPUT
  -> RESUMED -> PROGRESS -> SUCCEEDED; unchanged id/action/fraction and current snapshot; no wait/resume outcome.
- [x] Add rapid transition/coalescing and deterministic concurrency boundary tests; repeated phases update
  detail only; stop waiting/running then call held handle methods and prove exactly one terminal event.
- [x] Implement needsInput/resume in runtime and fake with queue scheduling under per-handle ordering;
  update exhaustive subscribers and metadata, preserving old lifecycle tests.
- [x] Add failing Buddy state-update and notifier tests for retained visibility/dismissal, waiting/resume,
  multiple live tasks, unknown/ended events, close and connections excluded from working.
- [x] Implement neutral notifier phase mapping and narrow retained state updates; aggregate terminal/plugin
  booleans in BuddyIntegration; wire both producers. Test actual composition callbacks headlessly.
- [x] Update compiled example, version/release references and current docs; run sdkReference.
- [x] Run affected tests and architecture/doc checks GREEN, then commit with Co-Authored-By trailer.
- [x] Fresh task and whole-branch reviews; fix findings. Parent runs full check and external-plugin check/pluginZip
  with -PjasperSdkDir="$PWD/build/sdk-release". Record verification and finish locally.
