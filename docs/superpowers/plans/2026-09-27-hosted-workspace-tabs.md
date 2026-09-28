# Hosted Workspace Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Native inline execution selected by the user; no further review checkpoint. Steps use checkbox syntax for tracking.

**Goal:** Host Editor files as native workspace tabs, with app-owned styling and optional separate windows.
**Architecture:** An app-native content registry connects plugin adapters and workspace hosts without package cycles. Main and detached windows share WindowContent, WindowTabs and WorkspaceRegions. Editor supplies document content, commands, metadata and close decisions through a JDK-only SDK contract.
**Tech Stack:** Java 25/JBR, Swing/FlatLaf in app, RSyntaxTextArea in Editor, Gradle/JUnit headless tests.
**Spec:** ../specs/2026-09-27-hosted-workspace-tabs-design.md

## Global Constraints

- Colors come from the active theme; shared tab and frame painting remains app-owned.
- SDK types occur in app only under plugins; Editor compiles against SDK, not app or FlatLaf.
- Remote paths never become local Paths. Existing safe save and document identity rules remain.
- No native GUI launch; headless checks/previews and package build only.
- One local commit per deliverable, Co-Authored-By trailer; no main merge or push in this task.

## Review Focus

- Closing during a save or prompt cannot lose newer edits or dispose unrelated tabs.
- Moving a dirty file preserves the actual component/session and resolves future prompts to its new host.
- Activating Editor must expose no hidden terminal pane or terminal keyboard dispatch.
- Closing/reloading a plugin cannot leak content, subscriptions, host handles or callbacks.
- Theme changes and mixed remote/local tabs preserve literal labels and semantic surface colors.

## Task 1: Hosted-content model and SDK

Files: create contributions/ContentRegistry.java, ContentEntry.java, ContentHost.java, ContentCommand.java, ContentStatus.java; SDK ui/ContentTab.java, ContentSpec.java, ContentCommand.java, ContentStatus.java; modify Contributions, Windows, HostedUi and testkit FakeUi/FakePluginHost.
Interfaces: Windows.openTab(ContentSpec, WindowOwner, JComponent) returns ContentTab. Optional null owner selects fallback. ContentTab updates presentation, commands and status, focuses, moves, requests close, and emits activation/disposal. Registry hosts mount/select/remove entries by UUID. Close decisions return CompletableFuture<Boolean> and are serialized; plugin-wide batch guards receive the closing subset.

- [x] Write model tests: open chooses origin/fallback, stable key selects existing content; moving preserves component and emits no disposal; cancelled close leaves every tab; repeated pending close shares decision; stale callbacks do nothing.
- [x] Run tests red with :jasper-app:test --tests '*ContentRegistryTest'.
- [x] Implement app-native model with callback-based host records and close guards; SDK values validate immutable inputs; add compatible default methods and app/testkit adapters.
- [x] Verify focused tests, SDK doclint and SDK/plugin architecture.
- [x] Commit deliverable.

## Task 2: Mixed workspace tabs and shared window chrome

Files: WindowContent, WindowTabs, WindowTerminals, WorkspaceActions, WindowStatusBar, WindowHeaderContext, TerminalWindow, JasperApplication; new workspace/HostedContentTabs.java where useful.
Interfaces: WindowContent connects its registry host with detached flag, mount/remove/select callbacks and window identity; currentTab/currentPane remain terminal-only nullable lookups. Shared tab strip reads general components through owner presentation methods. Application factory creates a shell-free workspace for detached content and an ordinary workspace for fallback.

- [x] Write mixed-tab tests asserting terminal registry filtering, shared strip titles/icons, focus target, metadata, command routing, reorder and close; no shell spawned for detached host.
- [x] Run new tests red, then generalize tab rendering and selection without copying painting code.
- [x] Render typed status/breadcrumbs and commands using app-owned components. Add move commands to tab popup. Handle native close/quit/restart through content guards before terminal shutdown.
- [x] Verify existing workspace/registry tests and mixed-tab tests; inspect dark/light headless previews.
- [x] Commit deliverable.

## Task 3: Editor and Remote migration

Files: EditorWindow (document controller), EditorTab, EditorTheme, StatusLine replacement, EditorPlugin, EditorEnvironment, Editor API and RemoteEditing. Remove custom TabHeader/JTabbedPane and legacy Editor window framing.
Interfaces: Editor.open(Path, WindowOwner) and open(EditorDocument, WindowOwner) preserve existing overloads with fallback hosting. Controller holds document-to-ContentTab map, publishes typed commands/status and per-plugin batch close decisions. Tab owner follows moves.

- [x] Write Editor tests for one hosted file per document, owner propagation, existing-document focus, dirty moves preserving undo, save failure/cancel keeping tabs, close during save, batch close, plugin disposal and remote ownership.
- [x] Run red, migrate host calls while retaining existing DocumentSession/save logic.
- [x] Route find/edit shortcuts only for active content; supply theme-derived editor surface/gutter palette and literal remote location metadata.
- [x] Run all Editor/Remote and SDK-host contract tests.
- [x] Commit deliverable.

## Task 4: Integration, documentation and independent review

- [x] Bump SDK/plugin floors, document hosted-content contract, update architecture allowlists and approved spec amendments/status.
- [x] Run ./gradlew check and fix failures; inspect mixed-tab headless previews and build :jasper-app:packageDist.
- [x] One independent whole-branch review; fix material findings with regression checks.
- [x] Record actual verification and limitations, commit and deliver installer. Local main merge subsequently authorized and completed; no push.

## Decisions and execution notes

- Approved native workflow supersedes the skill's extra user review stop.
- Share the entire WindowContent for optional detached editor windows instead of extracting its renderer into a second presentation owner. This is less duplication, preserves every established chrome feature, and avoids a workspace/windows dependency cycle. Detached windows start without a shell and use the same application-managed frame lifecycle.

Final verification: `check :jasper-app:packageDist` passed with 2,281 tests, zero failures/errors, five skips. Independent review’s two confirmed findings were fixed with RED→GREEN regressions; no deferred findings. Native GUI was not launched.
