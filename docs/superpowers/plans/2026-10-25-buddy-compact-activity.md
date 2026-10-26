# Compact Buddy Activity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development task-by-task.

**Goal:** One quiet summary opening a stable drawer grouped by tab context.
**Architecture:** Keep retained BuddyDeck and public notices unchanged. Add internal summary/grouped-row projections as needed, and use them in presentation. Separate explicit opening from refresh lifecycle.
**Tech Stack:** Java 25 JBR, Swing, headless JUnit and Java2D previews.
**Spec:** docs/superpowers/specs/2026-10-25-buddy-compact-activity-design.md

Status: authorized continuous execution. Existing clean checkout reused on dedicated branch.
Deviation: detailed implementation bodies delegated to implementer; exact behaviors and verification
below replace duplicating the production implementation in this plan.

## Global Constraints

- No SDK/facade, sprite, dependency or qualification changes.
- Zero or one summary; visible-only counts; task/connection distinction; no inferred tab count.
- Attention priority NEEDS_INPUT > DOWN > DEGRADED > ordinary; stable highest-priority ties.
- No continuous shimmer, arrival travel or placement spring for activity surfaces.
- Stable retained-identity order and expansion; dismiss all includes collapsed but excludes suppressed.
- Drawer opens only explicitly, persists on pointer exit, closes on Escape/focus loss/activation/empty/hide.
- No GUI, benchmark, AOT, merge or push.

## Review Focus

- Mixed plugin connections and tasks must never be mislabeled as all running tasks.
- Visibility changes and reparenting must not lose activation or resurrect dismissal.
- Collapsed headers and rows must share painting/hit geometry, especially when scrolled.
- Hidden stale anchors must not reopen the drawer; refresh must not request focus.
- Model updates must not reorder existing rows or leak retained presentation state.

### Task 1: Compact summary and grouped drawer

Files: internal/model/BuddyGroups.java and internal presentation BuddyColumnPanel/Window,
BuddyDeckPanel/Window/Layout, BuddyCard, BuddyWindow; their corresponding headless tests and
preview tasks under jasper-buddy; current Buddy README and docs/buddy-* and docs/notifications.md.
Keep unrelated bubble menu behavior intact. Remove obsolete stack-specific internals when unused.
Interfaces: public BuddyNotice/BuddyDeck inputs unchanged; internal pure projection/geometry and
production-used lifecycle seams may be introduced with concrete responsibilities.

- [ ] RED: add behavior tests for empty/single/multiple/mixed summary counts, priority and stable ties;
  singleton and multiple capsule clicks both open drawer without invoking notice activation.
- [ ] RED: add grouped-header expand/collapse tests, stable update ordering, title/group changes,
  duplicate labels, visibility, dismiss all with collapsed and suppressed notices, row activation,
  viewport-clipped hit tests and externally shrinking scroll.
- [ ] RED: cover persistent drawer lifetime and explicit-open-only focus behavior through production
  methods, plus no continuous summary/drawer animation demand while ordinary work runs.
- [ ] Implement projections and presentation. Prefer concrete internal types over new interfaces.
  Bind geometry to displayed rows; preserve state by identity; clamp against actual viewport.
- [ ] GREEN: run targeted affected tests, then :jasper-buddy:check and relevant architecture checks.
  Adapt old stack/arrival assumptions deliberately; remove dead tests with removed internals only.
- [ ] Generate headless light/dark previews of one summary, mixed/attention summary and grouped drawer
  including collapsed group. Inspect at rendered size. Update current docs and STATUS minimally.
- [ ] Commit with Co-Authored-By trailer; task review and final whole-branch review, fix findings.
- [ ] Run ./gradlew check --continue with Python 3.13; record results and finish locally.
