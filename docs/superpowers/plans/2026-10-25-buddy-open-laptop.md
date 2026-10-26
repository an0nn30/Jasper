# Buddy Laptop Opening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Animate sitting and opening the laptop with reversible existing poses.
**Architecture:** Extend the clock-driven BuddyAnimator; presentation already reschedules on intent changes.
**Tech Stack:** Java 25 JBR, headless JUnit and production-sprite GIF previews.
**Spec:** docs/superpowers/specs/2026-10-25-buddy-open-laptop-design.md

Status: complete; implementation 6b45d7726, task and whole-branch reviews clean. Full headless check passed (13,049 tests, zero failures/errors, 30 skips). Existing clean checkout reused on a dedicated branch.
Plan deviation: implementation bodies delegated to the implementer, with exact behavior below;
this keeps the single small deliverable proportionate instead of duplicating production code.

## Global Constraints

- Reuse all 23 sprite cells unchanged; no SDK or facade changes.
- Full standing entry: RISE 150 ms, LOWER_LAPTOP 125 ms, CLOSE_LAPTOP 125 ms, TYPE_REST 100 ms.
- Reversal retains current pose with its full duration; repeated intent retains deadline.
- Busy stages finish into latest intent; hidden state retains intent only.
- No GUI, benchmark, AOT, merge or push.

## Review Focus

- Intent changes between ticks must schedule from the supplied current time.
- Multiple reversals must preserve pose continuity and bounded work.
- Seated/sleeping starts must avoid jumping directly to typing.
- Late ticks must preserve exact completion origin and typing cadence.
- Existing stand-up previews and guides must match revised interruptions.

### Task 1: Reversible laptop transition, tests, previews and guides

Files: BuddyAnimator.java and BuddyAnimatorTest.java in jasper-buddy animation packages;
BuddyStandUpPreview.java plus opening preview in test presentation package; jasper-buddy/build.gradle.kts;
current Buddy README, architecture and maintenance guides; docs/STATUS.md.
Interfaces: existing setWorking(boolean,long), tick(long), frame(), nextDueNanos(); no public additions.

- [x] Add failing boundary tests: after spawn at 2000 ms, true gives RISE/due2150;
  tick2150 gives LOWER_LAPTOP/due2275; tick2275 CLOSE_LAPTOP/due2400;
  tick2400 TYPE_REST/due2500; tick2500 TYPE_A/due2750.
- [x] Test each pose reversing in both directions, preserving frame and assigning its full duration;
  repeated value and greet/poke retain deadline. Test seated entry, sleep wake, busy-stage cancellation,
  hide/show, late ticks, and arithmetic typing cadence after the new 500 ms entry.
- [x] Run focused animator tests, capture meaningful RED failures.
- [x] Implement LOWERING and finite advancement; wire all starts and reversals through current pose.
  Reuse rising frame/duration tables where clear; no general animation framework.
- [x] Run focused tests GREEN, then Buddy check. Fix affected old assumptions in integration tests.
- [x] Add production-sprite normal/reversal opening GIFs and contact sheets; refresh stand-up preview.
  Inspect generated images and minimally update guides and STATUS.
- [x] Commit with Co-Authored-By trailer; fresh task review, fix findings, final whole-branch review.
- [x] Run full ./gradlew check --continue with Python 3.13; record results and finish locally.
