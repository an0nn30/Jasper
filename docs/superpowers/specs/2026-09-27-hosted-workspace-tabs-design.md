# App-owned workspace tabs and Editor integration

Status: Approved on 2026-09-27. User requested implementation using native inline execution,
with no additional plan-review checkpoint. Main-app file tabs are the default; separate windows are optional.

## Outcome

Editor files should look and behave like native Jasper workspace tabs. The application owns
shared chrome: tab presentation, rounded content frames, toolbar and status layout, menus,
focus routing and native window integration. Plugins supply their specialized content and
behavior through the SDK. A future style change must reach terminal and plugin tabs together.

This amends the separate-Editor-window decision and main-tab exclusion in the approved
2026-09-27 Editor design. Document loading, encoding, conflict detection, watching and safe
local/SFTP saves remain the Editor and Remote plugins' responsibilities.

## Alternatives considered

1. Restyle Editor's existing JTabbedPane. Smallest patch, but leaves two tab implementations
   and does not support main-workspace files. Not the selected direction.
2. Put the existing multi-file Editor panel into one main tab. Preserves most Editor code,
   but creates nested tab strips and leaves plugin-owned window chrome. Not recommended.
3. App-hosted content tabs with one shared chrome implementation. Recommended: one document
   per workspace tab, and the same app-owned presentation in optional separate windows.

## Visible behavior

- Opening a file from SFTP opens or selects its file tab in that main window. An Open in
  Editor action uses its invoking window. A caller without a window uses the last active
  main window, or creates a main window if none exists.
- Terminal and file tabs share one row, rounded selected backgrounds, theme-derived accent,
  padding, close controls, scrolling, edge fades and the all-tabs menu. The whole content
  region uses the same corner radius and outer spacing as the current main workspace.
- Tabs show a file icon, filename, dirty marker and optional remote host. Duplicate basenames
  receive parent-folder context; full location is available as literal tooltip text.
- Opening a document that is already open selects its existing tab and brings its owning
  window forward. There is one editing session per document key across all hosts.
- The header reflects the active document or terminal. Editor breadcrumbs show the document
  path and remote host. Local and remote paths remain distinct; no remote string becomes a
  local filesystem Path.
- With a file active, the host status bar renders line/column, language, encoding, line ending,
  indentation and wrap. Language and wrap remain actionable. Global plugin status items stay
  visible; terminal-only status such as grid dimensions is replaced by the file's context.
- Selecting a tab focuses its content. Find, Save, Copy/Paste, Close and tab-navigation commands
  route to the active content. Terminal-only actions must not affect a hidden terminal.
  New Terminal Tab remains a terminal action; New Editor File is a separate action.
- Rail and SFTP panels remain available while editing. Automatic SFTP following must not
  switch to a different host or interpret a file tab as a terminal directory report.

## Separate-window option

The tab menu offers Open in Separate Window and Move to Main Window. This moves the live tab,
including unsaved text, undo history, caret and find state; it does not reopen the file or
create a second DocumentSession. Both destinations use the same app-owned tab strip,
rounded frame and status layout. When moving back, use the last active main window or create
one if necessary. Initial scope supports these explicit commands, not dragging between
windows, arbitrary docking or editor splits. Same-window tab reorder remains available.

## Ownership and architecture

Main and detached windows share the existing WindowContent, WindowTabs and WorkspaceRegions
owners. Detached windows start without a terminal shell. Reusing the full workspace preserves
one renderer and an acyclic package graph without extracting a second hosting implementation.
Do not copy the renderer or expose application classes to plugins.

The workspace selection model represents terminal tabs and hosted content tabs explicitly.
TerminalTab continues owning its SplitTree and terminal sessions. Hosted tabs own plugin
content registrations. TerminalRegistry continues enumerating terminal tabs/panes only;
a selected editor tab has no active terminal pane. Window identity remains usable by
existing SDK actions, but consumers must tolerate an empty pane.

Add a JDK/Swing-only SDK hosted-content service, implemented by the app and testkit. Its
contract provides:

- Open/select a plugin-scoped stable content key in an explicit WindowOwner or the fallback
  host; supply content component and its preferred focus target.
- Update title, icon, detail, tooltip, dirty state and location/breadcrumb metadata.
- Publish active-tab commands and typed status items (text, tooltip, enabled state and
  optional action); the host builds the controls and owns spacing, fonts and colors.
- Observe activation, request close, register asynchronous close guards and receive one
  disposal callback. A content handle acts as a WindowOwner for app-owned dialogs/pickers;
  the app resolves its current containing native window after a move.
- Move the same content registration between main and auxiliary hosts without disposal.

SDK objects are adapted in dev.jasper.app.plugins to app-native values and callbacks.
No SDK types leak into workspace implementation, and no app/FlatLaf/RSyntaxTextArea types
enter the public SDK. New interface methods need compatible defaults where existing host
implementations would otherwise break. Bump the SDK version and bundled plugin minimums
when this contract ships; add testkit parity and architecture checks.

Existing plugin windows, panels and ordinary Swing form content remain supported. The app
already styles standard controls through the global look and feel. New document surfaces
must use the host contract for outer chrome; specialized text widgets remain plugin-owned.

## Theme responsibilities

The app renders frame and tab geometry and reads semantic colors from the active theme.
Editor content uses a host-provided editor palette for background, text, caret, selection,
current-line highlight and gutter. Add missing semantic tokens with fallbacks for imported
themes; edit Jasper Dark's theme values for desired contrast. Do not add literal UI colors
or duplicate FlatLaf styling strings to Editor's tab/window implementation.

RSyntaxTextArea and language token classification stay inside Editor. Its existing light/dark
syntax token palettes can remain for this deliverable; full syntax-scheme import is separate
work. Surface theme changes and UI-font changes must update existing hosted content live.

## Editor and Remote migration

Separate the current EditorWindow's document/session controller from native hosting and
remove its JTabbedPane, custom TabHeader and outer status layout. Each EditorTab becomes
hosted document content. Keep existing DocumentSession, EditorModel identity rules, loading
and error states, find/replace, language selection, encoding handling and save behavior.
Editor supplies command and status descriptions; the application renders their chrome.

Add owner-aware Editor.open overloads while retaining old entry points as fallback-host
calls. RemoteEditing passes the SFTP originating owner explicitly. Credential prompts still
follow the existing remote connection ownership rules; document prompts follow the tab's
current host. A late async open against a closed origin uses the documented fallback host,
not a dead frame. Reopening an existing document always selects its current live host.

## Closing, shutdown and errors

A tab close delegates to the document guard: Save, Don't Save or Cancel. Saving is asynchronous;
errors keep the tab open. Edits made during a pending save must still be checked before close.
Repeated close commands share the pending request instead of creating duplicate prompts.

Closing a native host asks all relevant tab guards before removing any tab. Quit/restart
consults hosted tabs and legacy plugin windows before shutting anything down; cancel or save
failure leaves content intact. Batch Save All/Discard/Cancel remains available for Editor
files, without duplicate prompts from both legacy window and new tab guards.

Moving a tab is not closing it. Defer moves while a close decision or document-modal dialog
is pending. Plugin shutdown disposes registrations and watchers exactly once; disabling or
restarting the Editor must consult dirty-document guards before unloading it. Late callbacks
to disposed handles are harmless and must never reattach content or steal focus.

## Verification and scope

- Shared renderer previews: dark/light themes, UI scaling, narrow/overflowing tab rows,
  active/inactive window, dirty remote/local titles and all four rounded corners.
- SDK/testkit contracts: ownership validation, closed handles, activation, title/status
  updates, move without disposal, and failed/cancelled/asynchronous close requests.
- Workspace tests: mixed tabs, reorder/scroll, focus, context-sensitive commands, no terminal
  input leakage, terminal registry filtering and global contributions while a file is active.
- Editor/Remote tests: origin-window routing, document de-duplication across windows,
  preserved undo/caret on move, local/SFTP save errors, prompt ownership, close during save,
  late callbacks and quit/restart cancellation.
- Run all architecture checks and ./gradlew check; rebuild and verify the app package.
  Headless previews are automated; native GUI acceptance remains user-run.

No restart restoration of documents, persistent unsaved drafts, arbitrary docking, editor
splits or cross-window drag gestures in this deliverable. Terminal split behavior stays
within terminal tabs. Those features can build on this hosting contract later.
