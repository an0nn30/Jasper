# Workspace tabs 9b: two views of one file Implementation Plan

**Status: executed on 2026-10-22** (`ab55ecde6..e6c33c193`, then the documentation commit); see `docs/STATUS.md`.
Written against `claude/workspace-tabs` at `ff322c478` ("Spec: pane flexibility approved by the user; the tabs and panes
spec points at it") with plan 9a's final text (`2026-10-21-workspace-9a-open-beside-and-move-into-tabs.md`, 4,401 lines,
2026-10-21) applied on top, after its review; this plan's own review's findings are in (Rulings 3, 13, 14, 18-20). See
Workspace for what was replayed, compiled and run. Deviations from the plan's text: (1) Ruling 3, amended after Task
1a's review (a controller ruling; fix rounds `4fec5fb25` and `a1ff84d6f`): another view keeps showing the same text, not
the same pixels, where the plan restored its scroll offset; a Vim view's cursor stays on its text in every mode outside
the command line and the window scrolls the least that shows it, with `scrolloff`, a view without Vim keeps its first
line, and a whole-text replace and its undo keep each view's line number, an insert at a view's first-line start and at
offset 0 keeps that line on top, an edit that takes away the first shown line shows the text after the deletion, and a
Markdown view returns from Preview to the same text; `share` enforces its preconditions, a closed view cannot be shared,
and one put-back is queued per view per event; (2) Task 2's fix round (`fc9d4d147`): a closing view keeps the
most-recent order, and `EditorWindow.step(int)` and the no-argument `mayClose()` are gone; (3) Task 3's fix round
(`a6b4ff402`), following Vim (the user's rule): an uppercase mark in another view of the same file acts in the current
window, a new view starts at the change list's end, only the editing window's place in it moves, file marks pass to the
file's most recent other view when their view closes (`EditorTab.shutdown`, `marksHeir`), a split copies the jumplist,
`''`, the change-list place and the window-local options, and buffer-local options (`sw`, `et`, `ts`, `sts` and the
others) are the file's while `number`, `relativenumber` and `wrap` are the pane's, which supersedes Ruling 13's clause
that options stay global; the Vim reference changed six rows, not two; (4) Task 4: `openRestored` passes
`views(open).getFirst()`, as `openView` does after Task 2's fix; (5) found during execution, fixed in `e6c33c193`:
`EditorTextView` read token lists from the text area's current document while its lines came from its own, so closing a
view after another view's Vim edit threw a `NullPointerException` in `unshare`; it reads the tokens from its own
document now; (6) Task 1b's `TextViewsTest` has one case more than the plan (a second view's Vim groups), and the plan's
counts are lower than what ran (the Editor's suite has 5,223 tests, not 5,177); (7) Task 5: `docs/vim-divergences.md`
says what Task 3 made true. Tasks 1a to 4 ran their scripts with no anchor re-found.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Editor shows one file in several views, each the item of a pane: Split File Right and Split File Down, and
Open File Right… and Down… of a file that is already open, open another view beside it. The views share one text and
one undo history; each has its own caret, selection, scroll position, find bar, Vim state and Markdown mode; the ●
marker, Save, Revert to Saved and a reload act on the file; only the file's last view asks about unsaved changes; Vim
sees one buffer in many windows; and every view comes back when Jasper restores its windows, the file read once.

**Architecture:** A file's views are `EditorTab`s of one `DocumentSession`. Their `VimTextArea`s show one
`RSyntaxDocument` (`VimTextArea.share`) and one `VimUndoManager`, the first view's, which RSyntaxTextArea's
`createUndoManager` hook hands every view; the manager takes each edit once, brackets each view's own Vim steps, and
keeps the other views' carets where they were when one view undoes. A new `TextViews` is the session's text: it reads
from the views and replaces the text once for all of them. `EditorWindow` keeps its views most recently active first and
asks about a file only when its last view closes; its Vim buffers are files, not tabs (`VimHost.Buffer.windows`,
`Documents.quit` and `show`). The restorer opens an open key as another view. No SDK, testkit or app code changes.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing, RSyntaxTextArea 3.6.0 (the Editor's text areas and their undo),
Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always
`python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-21-jasper-pane-flexibility-design.md`](../specs/2026-10-21-jasper-pane-flexibility-design.md)
(section 6, with 3's "a file that is already open" and 8), which amends the
[tabs and panes spec](../specs/2026-10-20-jasper-tabs-and-panes-design.md). It builds on plan 9a's text
(`2026-10-21-workspace-9a-open-beside-and-move-into-tabs.md`), which executes first.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`. On a `git archive` copy of `ff322c478`, plan 9a's 24 file
creations and edit scripts (its final text, 4,401 lines) replayed in order, each task committed, and then every file
creation and edit script of this plan's Tasks 1a-5, Task 2's Steps 1b and 3b on 9a's code included, with no failure;
every pre-flight `grep` below printed 1 on 9a's result. On that copy JBR 25's `javac`, with `-Xlint:all
-Xlint:-serial`, compiled the main and test sources of `jasper-sdk`, the testkit with its fixtures, the Editor,
Enterprise, the Vault, Remote and Project Index, with no warning in the Editor's main or test sources, and `javac`
compiled `jasper-ui`, `jasper-buddy`, `jasper-terminal` and `jasper-app`'s main and test sources.
`PluginPresentationCheck` passed on the Editor's classes (`java gradle/architecture/PluginPresentationCheck.java`);
`tools/sdk-reference/SdkReference.java` regenerated `docs/sdk-reference.html` (plan 9a's and this plan's Javadoc),
after which its `--check` passed; `VimDocsTest` run with `jasper.updateDocs` wrote exactly the two rows Task 3 names.
A plain JUnit launcher then ran, headless (`-Djava.awt.headless=true`, no Gradle, no window), the Editor's whole
suite (5,177 tests: 5,173 passed, the 4 opt-in performance tests skipped), among them `VimUndoManagerTest` (8),
`TextViewsTest` (8), `EditorViewsTest` (12), `ExFilesTest` (10), `CompletionTest` (5), `EditorPluginTest` (31),
`EditorWindowTest` (79, plan 9a's cases with it), `VimDocsTest` (2) and the Vim corpus; Remote's (570) and Project
Index's (99); and, from `jasper-app` with the copy's plugins staged as the build stages them,
`TabShortcutCollisionTest`, `EditorCompositionTest`, `EditorDocumentPreservationTest`, `ThemeMigrationCoverageTest`
and `AppDocumentationTest` (20, with both plans in `docs/superpowers/plans/`, which `docs/STATUS.md` links): no
failure. Each task's RED
was checked on the commit before it: Tasks 1a, 1b, 2 and 3's tests fail to compile as their Step 2 says, plan 9a's
reworked case fails without Step 3b, Task 3's marks case fails on the old `Marks`, and Task 4's three cases fail; the
review's six new cases (the scroll position, Cancel in a loading view twice, the two listener cases, the split view's
caret) each fail with its fix taken out. The source hygiene script found nothing. Line numbers are `ff322c478`'s, and
"after 9a" marks plan 9a's.

Run every command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message
when that text is not found the stated number of times. Then re-find the quoted text (a change since may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later tasks
anchor on text that earlier tasks of this plan wrote, and quote it as they leave it.

**Hand-offs absorbed from plan 9a** (names fixed by both plans' briefs; its "Left for plan 9b"):
- `EditorWindow.openBeside(WindowOwner, TabPlacement)` shows the Editor's file chooser for one file and calls
  `openDocumentBeside(EditorDocument document, WindowOwner owner, TabPlacement placement)`, whose already-open branch,
  `selectOpen(existing.get())` under the comment "Until plan 9b's second view", Task 2 Step 3b swaps for another view;
  `selectOpen(DocumentSession)` stays `open`'s;
- 9a's `EditorWindowTest.openBesideOfAFileThatIsOpenShowsItsOpenPane` pins that branch; Task 2 Step 1b turns it into the
  second view's case;
- the Editor's split item `EditorPlugin.OPEN_FILE` ("Open File Right…", "Open File Down…"), the action
  `OPEN_IN_PANE`, and the Editor at 0.14.0 in `plugin.toml`, `docs/plugin-apis.md` and STATUS's table;
- 9a's `docs/editor.md` sentence "A file that is open already shows its open pane instead", which Task 5 rewords.

Every anchor in text plan 9a writes quotes it as 9a's plan leaves it; Task 2's Steps 1b and 3b are the ones on 9a's
Editor code, marked **(on plan 9a's code)**. When plan 9a's execution words any of them otherwise, re-anchor (Workspace
says what was replayed).

## Spec issues

None. Where the spec leaves a choice (the find bar's owner, the language and soft wrap, what a view's restore state
holds, Vim's marks, `jasper editor --wait`), the Rulings record it.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (MVP): `EditorWindow.openRestored` returns nothing, `VimHost.Buffer` and `VimHost.Documents` take their
  new components, `DocumentArea` has one constructor and `EditorTab.replaceText` goes, with no alias, overload or shim.
- The SDK, the testkit, the contract cases, the application and every plugin but the Editor stay as they are; the SDK
  stays 0.21.0. Only the Editor's exported `dev.jasper.editor.api` changes, and only in Javadoc (Rulings 15).
- Plugin versions: the Editor takes the next minor, 0.15.0 (plan 9a's is 0.14.0); no other plugin changes.
- `PluginPresentationCheck` (`verifyPluginArchitecture`): the Editor constructs no Swing control outside its documented
  exceptions. `EditorTab` may construct `DocumentArea`; `DocumentArea`'s own constructor may not call another of its
  constructors, which the check reads as constructing a control (Verified facts 9), so it keeps one.
- `html.disable` before text: the two new right-click entries are Editor commands with fixed titles, built by
  `DocumentArea.menuItem` as the other commands are; no plugin- or user-supplied text reaches a new menu.
- No animations: a view opens, closes and shows at once, as any pane does.
- Every action is bindable: Split File Right and Down are plugin actions, which Settings › Keymap lists under the
  Editor; neither has a default shortcut (spec 6).
- The theme-migration ledger `docs/design/theme-engine/migration.csv`: a new file that matches
  `ThemeMigrationCoverageTest`'s pattern gets `ledger.add(...)` with "New in workspace-tabs plan 9b Task N" (plan 4a
  Rulings 21): `TextViews`, `TextViewsTest` and `EditorViewsTest`.
- `docs/editor-vim-reference.md` is generated: `./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true`
  rewrites it; never edit it by hand. `docs/sdk-reference.html` is generated: `./gradlew sdkReference`.
- Javadoc and `-Xlint:all`: no raw `<`, `>` or `&` in a comment, every `{@link}` resolves, and every new public member
  has its `@param` and `@return`.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- User-facing text says "view" for one of a file's panes and "pane" and "tab" as before (spec 6); the commands read
  "Split File Right" and "Split File Down".
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): text areas, views and the Editor's window driven directly on the
  testkit's `FakePluginHost`.

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan9b/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan9b
cp build/plan7c/edits.py build/plan7c/ledger.py build/plan9b/
ls build/plan9b/edits.py build/plan9b/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `rename(old, new, surface)`, `drop(path, successor)` and `add(path, owner,
surface, evidence, disposition)`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan9b")`. Java text inside a script is in triple-quoted strings; where it holds a
Java backslash (a `\n` in a string literal) it is written `\\`. A file a task replaces whole is written from the plan's
code block.

## Review Focus

1. **Undo across views while one view's Vim change is open** (Insert mode in one view, an edit and an undo in the
   other, then Esc and `u` in the first). Each undo takes the newest edit made in either view, in time order; the first
   view's engine still closes what it opened; nothing is undone twice. Task 1a:
   `VimUndoManagerTest.aVimChangeOpenInOneViewEndsWhereAnotherViewEdits`, `twoViewsOfADocumentShareOneUndoHistory`
   (each edit taken once); Task 1b: `TextViewsTest.vimInOneViewAndNotInTheOther`.
2. **Closing a view of a changed file, its last view, a window holding one view, and Quit with two views in two
   windows.** A view that is not the file's last closes without asking and keeps the changes in the others; the last
   view asks "Save changes to …?"; a window close asks nothing while another window shows the file; Quit asks once and
   names the file once. Task 2: `EditorViewsTest.closingAViewAsksNothingAndTheFilesLastViewAsks` and
   `aWindowWithOneViewOfAChangedFileClosesSilentlyAndQuitAsksOnce`; Task 3's `:q` in
   `vimSeesOneBufferPerFileAndQuitClosesOnlyItsPane`.
3. **Revert to Saved, or an outside change's reload, while another view's caret is past the new end.** The text is
   replaced once, each view's caret goes to its line or the new last line, and the one history starts again. Task 1b:
   `TextViewsTest.aRevertReplacesTheTextOnceAndEachViewKeepsItsCaretLine`.
4. **The view a file was first opened in closes while another stays.** The other keeps the text, its undo history, the
   session's reads and reloads; the closed text area lets go of the document, and its listeners leave the session. Task
   1b: `TextViewsTest.closingTheFirstViewLeavesTheOtherTheTextAndItsHistory` and
   `aClosedViewLeavesNoListenerOnTheSession`; Task 1a: `VimUndoManagerTest.aViewThatLetsGoLeavesTheOtherTheTextAndItsHistory`;
   Task 2: `EditorViewsTest.closedViewsLeaveNoListenerOnTheirFile`.
5. **A restore that brings back two views of a remote file, and a remote file's second view while its connection is
   gone.** Both views come back waiting, the first shown reads once, quietly, for both, and a second view opened later
   reads nothing. Task 4: `EditorPluginTest.everyViewOfAFileComesBackAndItsFileIsReadOnceForAllOfThem` and the reworked
   `aRestoredRemoteDocumentReadsQuietlyOnlyOnceItsTabIsActive`; Task 2:
   `EditorViewsTest.aRemoteFilesSecondViewReadsNothingThoughItsConnectionIsGone`.

Also pinned: Save As from one view renames every view and its restore state (Task 2,
`saveAsFromOneViewRenamesEveryView`); a Markdown preview in one view and the source in the other (Task 1b,
`aMarkdownPreviewInOneViewAndTheSourceInTheOther`); an undo moves only its own view's caret and an edit leaves the
other view's scroll position (Task 1a, `anUndoMovesOnlyItsOwnViewsCaretToTheChange`,
`anEditInOneViewLeavesTheOtherViewsScrollPosition`); Cancel in one view of a loading file (Task 1b
`cancelInOneViewOfALoadingFileLeavesTheOtherTheRead`, Task 2 `cancelInOneViewOfALoadingFileLeavesTheOtherItsRead`); a
split view's start (Task 2, `aSplitViewStartsAtItsSourcesCaretAndSelection`); marks across views (Task 3,
`TextViewsTest.lowercaseMarksAndTheLastChangeBelongToTheFile`).

## Verified facts

From `ff322c478`, and from it with plan 9a's text applied where noted ("after 9a"); RSyntaxTextArea from its 3.6.0
source.

1. **RSyntaxTextArea's undo is per text area.** `RTextArea` holds `private transient RUndoManager undoManager` (`:165`),
   made by the protected hook `createUndoManager()` (`:548-550`) in `init()` (`:900-902`) and again in
   `discardAllEdits()` (`:557-567`: it empties the old manager, removes it from the document, asks the hook for a new
   one and adds that), and moved by `setDocument(Document)` (`:1481-1497`: remove from the old document, add to the
   new, then `discardAllEdits()`). `undoLastAction()`, `redoLastAction()`, `canUndo()`, `beginAtomicEdit()` and
   `endAtomicEdit()` go to that field; `paste`, a macro's playback and `replaceRange` bracket their edits with it
   (`:993-997`, `:1010-1026`, `:1206-1212`). `RUndoManager(RTextArea)` keeps its area in a private field that only its
   own grouping and atomic edits read (`startCompoundEdit`, `undoableEditHappened`, `RUndoManager.java:50-176`), which
   `VimUndoManager` overrides whole; given null, nothing reads it, so a shared history keeps no closed view alive. So two text areas on one document would each
   record every edit, and the second undo of the same `AbstractDocument.DefaultDocumentEvent` throws
   `CannotUndoException`, which `undoLastAction` prints and swallows.
2. **One document, many listeners.** `AbstractDocument` fires one `UndoableEditEvent` per edit to every
   `UndoableEditListener` in turn, and `EventListenerList` keeps a listener added twice twice. A history every view
   registers is handed each edit once per view (Rulings 1).
3. **An undo moves every caret.** `DefaultCaret`'s document handler, for an `AbstractDocument.UndoRedoDocumentEvent`,
   sets the dot to the change (`DefaultCaret.java:1795`, `:1878`, `:1940` in JBR 25's sources), in every text area on
   the document. A dot an edit moves, in any text area on the document, is then scrolled into view later, through the
   event queue (`changeCaretPosition`, `repaintNewCaret`, `adjustVisibility`, `:1384-1398`): an edit before another
   view's caret scrolls that view back to it. `AbstractDocument` tells its document listeners of an edit, the carets
   among them, before its undoable-edit listeners (`AbstractDocument.java:774-778`, `:645-649`).
4. **`RSyntaxTextArea.setDocument`** takes the document's syntax style (`RSyntaxTextArea.java:2574-2588`); the fold
   manager, the parser manager and the gutter follow the `"document"` property. `createDefaultModel()` is protected
   (`:702`).
5. **The Editor binds a text area's first document.** `EditorTab` builds its `DocumentArea` in a field initializer
   (`EditorTab.java:28`), registers its edit listener on that area's document and attaches the session's text to the
   area (`session.attach(area()::getText, this::replaceText)`, `:127-128`); `replaceText` sets the text and discards
   the area's history (`:214-224`). `DocumentArea` creates its `VimTextArea` in a field initializer (`:29`) and its
   `ScrollLink`, which listens to that area's document, in its constructor (`:116`; `ScrollLink.java:49`). `VimEditor`
   reads `area.getDocument()` when Vim turns on (`VimEditor.java:111-124`). `VimTextArea.createUndoManager` makes a new
   `VimUndoManager` each time (`VimTextArea.java:36-40`). `DocumentSession` has `addListener` and no way to remove one
   (`DocumentSession.java:104-105`); `EditorTab` and `EditorWindow.add` each add one per tab (`EditorTab.java:131`,
   `EditorWindow.java:878-883`); `EditorTab`'s Cancel (DISMISS) cancels the session's read (`EditorTab.java:253`).
6. **`EditorWindow` has one tab per session.** `add(session, ref, placement)` (`EditorWindow.java:850-901`) adds the
   session to the model and numbers it with its one tab; `remove(tab)` (`:943-955`) closes the session with its tab;
   `select(session)`, `owner(session)`, `openLinked`, `moveCaret`, `closed` and `tabNumbered` take the session's first
   tab (`:956-958`, `:265`, `:247-251`, `:352-367`, `:343-350`, `:747-749`); `approveTab` (`:572-583`) and
   `mayClose(List)` (`:663-696`) ask about each closing tab's session; `buffers` lists every tab as a Vim buffer
   (`:737-745`); `previous` is a tab (`:89`). After 9a, `open` and `openDocumentBeside` each send an open document to
   `selectOpen` (`selectOpen(existing.get())`, the second under "Until plan 9b's second view"), a new one to
   `openSession`, which calls `add(created, ref, placement)` and `select(created)`; the lines above are as they were.
7. **Restore declines an open key.** `openRestored` returns false when `model.find(key)` is present (`:217-229`), and
   the restorer then declines "the file is open in another tab" (`EditorPlugin.java:165-166`), which
   `EditorPluginTest.java:420` pins; `EditorWindowTest.java:233`, `:237` and `:255` call `openRestored` as a boolean.
8. **Vim's buffers and `:q`.** `VimHost.Buffer` has eight components and `Documents` eight functions
   (`VimHost.java:83-110`); `ExFiles.quit` refuses a modified buffer with `E37` (`ExFiles.java:121-125`), and
   `writeQuit`, `exit` and `quit` close by buffer number (`:38-52`, `:124`); `MarkCommands.elsewhere` selects a mark's
   buffer by number (`MarkCommands.java:104-117`). `ExFilesTest` and `CompletionTest` build `Buffer` and `Documents`
   (`ExFilesTest.java:41-71`, `:102-103`; `CompletionTest.java:20-25`). Vim's `ex_quit` checks for changes only when
   the buffer is in at most one window (`check_changed` and `b_nwindows`). Each `VimEditor` makes its own `Marks` on its
   area's document (`VimEditor.java:112`), which holds the named marks, the Visual area, the jumplist and the change
   list, and follows the document's edits by its own document listener as Vim's mark_adjust does (`Marks.java:24-63`,
   `:262-326`); four classes read its fields `visualMode` and `visualCurswant` (`MarkCommands.java:42`, `:62`,
   `Numbers.java:142`, `ReplaceWithRegister.java:64`, `Visual.java:95-96`, `:128-130`). Vim keeps marks, `b_visual` and
   the change list per buffer, the jumplist and `w_changelistidx` per window.
9. **The presentation check.** Run on the Editor's classes (`java gradle/architecture/PluginPresentationCheck.java
   <classes>`), a `DocumentArea()` that calls `this(null)` fails: "DocumentArea constructs visible control
   DocumentArea.<init>(Ldev/jasper/editor/ui/DocumentArea;)V". `EditorTab` may construct `DocumentArea`
   (`PluginPresentationCheck.java:91`). Three tests build a `DocumentArea` alone (`MarkdownPerformanceTest.java:49`,
   `PreviewPaneTest.java:308`, `ScrollLinkTest.java:29`).
10. **The Editor API's users.** Remote opens through `editor.open(new RemoteDocument(…), from)`
    (`RemoteEditing.java:139`), whose sign-in owner is fixed when the document is made; Project Index finds the
    `Editor` service and opens files (`IndexerPlugin.java:131`). Neither keeps a tab or reads a document twice. Recent
    Files keeps one entry per key (`RecentFiles.java:70`).
11. **The fake host.** `FakeContent.requestClose()` asks the plugin's guard with `batch` false and closes on yes;
    `FakePluginHost.requestQuit()` asks it about every content tab with `batch` true and closes nothing, and the fake
    has no way to close one window's tabs as a batch, so `EditorViewsTest` asks the Editor's guard (`tabsClosing`)
    for a window's batch itself;
    `FakeContent.select()` makes it the active item (`FakeContent.java:46`, `:93`; `FakePluginHost.java:1308-1320`). A
    restored tab (`restoreTab`) is placed, not shown.
12. **Generated documents.** `VimDocsTest` compares `docs/editor-vim-reference.md` with `VimCommands` and Ex's
    descriptions; `verifySdkReference` compares `docs/sdk-reference.html` with the SDK's and the bundled plugins'
    exported API Javadoc, the Editor's `dev.jasper.editor.api` among them (`gradle/sdk-reference.gradle.kts:15`).

## Rulings

Record them in `docs/STATUS.md` (Task 5). "What — why — cost if wrong".

1. **One undo history per document: the first view's `VimUndoManager`.** Every view's `VimTextArea` gets it from
   `createUndoManager()`, which RSyntaxTextArea calls again in `discardAllEdits()` and so in `setDocument` (Verified
   facts 1); the history is reused across discards instead of made anew, and takes each edit once, though each view's
   registration hands it to it (Verified facts 2). — VS Code and IntelliJ keep one undo stack per document; the spec says
   undo in any view undoes the last edit made in any view. — Cost if wrong: per-view stacks, where an undo in one view
   cannot undo the other's edit.
2. **The view that edits now** is the one the user last typed in, clicked or focused, or whose Vim engine asked
   (`VimTextArea`: key and input-method events, `replaceSelection`, `beginAtomicEdit`, `undoLastAction`,
   `redoLastAction`, focus, `vimUndo()`). Its caret groups typing; each view brackets its own Vim steps, and another
   view's open change ends where the next view edits and opens again, empty, at the same depth. — A Swing edit event
   does not say which text area made it; Vim ends a change when the cursor goes to another window (u_sync). — Cost if
   wrong: an edit made by code into a view that is not the current one groups by the other view's caret.
3. **An undo or redo moves only its own view's caret, and an edit leaves the other views' scroll positions.** On an undo
   or redo the other views get back their caret, selection and scroll position, which Swing's caret sets to the change;
   on an edit that moves another view's caret, that view's scroll position is put back after Swing scrolls the caret
   into sight (Verified facts 3). — Spec 6: each view has its own caret and scroll position; VS Code moves only the
   active editor's cursor and keeps the others' viewports. — Cost if wrong: views jump to edits and undos made
   elsewhere.
4. **A view that closes while others stay lets go of the document** (`VimTextArea.unshare`: an empty document and a
   history of its own; the swap's `discardAllEdits` leaves the shared history alone). — The closed view's text area,
   caret, folds and parser listen to the document (Verified facts 4). — Cost if wrong: every closed view keeps working
   on each edit and stays in memory.
5. **The session's text goes through `TextViews`.** A load or reload sets the text once, which every view shows,
   discards the one history once, and gives each view its caret line back (the last line when the text is shorter);
   the session reads the text from any view, or what the last view showed. — `DocumentSession.attach` takes one reader
   and one replacer (Verified facts 5). — Cost if wrong: one view's caret reset by another's reload.
6. **The language, the tab setting and soft wrap stay the file's** (`DocumentSession`): Language… and Toggle Soft Wrap
   act on every view. — VS Code keeps a file's language mode for every editor of it; the session already holds them. —
   Cost if wrong: soft wrap per view, a field moved from the session to the view.
7. **A file's views are listed most recently active first** (`EditorWindow.recent`): Open in Editor…, Recent Files,
   `jasper editor` and `Editor.open` of an open file select the first, the session's prompts show over it, and `:b`
   selects it. — Spec 6. — Cost if wrong: an open selects a view the user left.
8. **Only a file's last view asks.** A view of a file another view shows closes without a question; a batch close (a
   window, Close Tab of a tab of several views, Quit) asks about a file only when every view of it is among the closing
   tabs, and then once. — Spec 6. — Cost if wrong: a question for a view whose changes stay in another.
9. **Split File Right and Split File Down** are Editor commands (`split-file-right`, `split-file-down`: the text's
   right-click menu after Revert to Saved, the tab's item commands, Vim's `:action`) and actions
   (`dev.jasper.editor.split_file_right`, `dev.jasper.editor.split_file_down`, "Split File Right", "Split File Down",
   no default key), acting on the window's file as Revert to Saved does. They select their view first, so the new view
   opens beside it though another pane had the focus. — Spec 6; plan 8b Rulings 20's pattern. — Cost if wrong: an
   entry's place.
10. **Open File Right… and Down… of an open file** open another view beside the focused pane and report the file to
    Recent Files, as Open in Editor… of an open file reports it again; Split File and a restore report nothing; Recent
    Files keeps one entry per file (Verified facts 10). — Spec 6 "Recent Files records the file once". — Cost if wrong:
    the order of Recent Files.
11. **A view's restore state is its file's key alone**, as one view's was: no caret, scroll, view marker or Markdown
    mode. The restorer opens an open key as another view, which reads nothing, and a waiting remote file's views share
    the one quiet read the first shown makes. — Spec 6; every view's state names the same key, which is what makes it a
    view of that file. — Cost if wrong: a restored view's caret at the top.
12. **Vim: one buffer per file, its views the windows.** `:ls` lists a file once (its line the current view's, or the
    view active last); `:b`, `:bn`, `:tabn`, `gt` and `gT` select a file's view active last; `:q`, `:wq`, `:x`, `ZZ`
    and `ZQ` close the current view (`Documents.quit`), and `E37` refuses only a changed file's last view
    (`Buffer.windows`, Vim's `b_nwindows`); `:qa` and `:xa` close every view; the alternate buffer is the file selected
    before, another than the current; an uppercase mark selects the view it was set in (`Documents.show`). — Vim
    (Verified facts 8). — Cost if wrong: a Vim user's `:q` refused while another view keeps the changes.
13. **Vim's buffer marks belong to the file; the rest of Vim's state is each view's** (the controller's ruling). The
    marks `a` to `z`, `'[`, `']`, `'<`, `'>`, `'^`, `'.` and `'"`, the last Visual area and the change list are kept
    once per document, in a `Marks.Buffer` the document holds as a property and every view's editor shares; it follows
    every edit to the document, whichever view made it, by Vim's mark_adjust, so a deleted line takes its lowercase
    marks as in one view. Each view keeps its mode, pending command, jumplist, `''` and place in the change list; A to Z
    marks remember their view (Rulings 12); registers and options stay global. The store keeps `Marks`' line and column
    positions rather than Swing `Position`s, which move by character and cannot drop a deleted line's marks, so the Vim
    corpus's marks cases hold unchanged. — Vim keeps marks and the change list per buffer and the jumplist per window. —
    Cost if wrong: none for one view; a view's `''` or jumplist shared.
14. **One find bar, in the selected view**: it opens in the view it was asked in and searches that view, and selecting
    another view ends it, as selecting another file's tab does today, so no view's search leaks into another. — The
    controller's reading of spec 6's "its own find bar"; the Editor has one `FindBar` per window. — Cost if wrong: a find
    bar per view.
15. **Other plugins see one document per key.** The Editor reads, saves and reloads an `EditorDocument` once for all
    its views; `Editor.open` of an open key selects its view focused last. Remote and Project Index need no change and
    keep their versions; the API's Javadoc says so, and `docs/sdk-reference.html` is regenerated. — Spec 6. — Cost if
    wrong: none for today's users.
16. **Editor 0.15.0** (plan 9a takes 0.14.0). — The series' ruling: the next minor at execution.
17. **No `:split` or `:vsplit`.** — Not in the spec; `:action split-file-down` reaches the command. — Cost if wrong:
    two Ex commands later.
18. **`jasper editor --wait` returns once the file's last view has closed** (`EditorWindow.closed`); the Editor guide
    and `docs/command-line.md`'s option row say so, and the usage text is left as it is. — A file is done when no view of
    it is open. — Cost if wrong: a wait ends while the file is still open in a view.
19. **A split view starts at its source's caret, selection and scroll position** (Split File Right and Down). — VS
    Code's Split Editor and IntelliJ's Split Vertically open the new editor where the old one is. — Cost if wrong: the
    new view at the top of the file.
20. **Cancel in one view of a file that is still loading closes that view alone**: the read goes on for the file's other
    views, and the last view's Cancel stops it, as one view's did. — Spec 6: closing a view closes only that view. —
    Cost if wrong: the other views wait on a read that never ends.

## File structure

- Editor `dev.jasper.editor.vim`: `VimUndoManager` (Task 1a, replaced), `VimTextArea` (Task 1a), `VimHost`, `ExFiles`,
  `Ex`, `MarkCommands`, `Numbers`, `ReplaceWithRegister`, `Visual`, `Marks` (Task 3, `Marks` replaced).
- Editor `dev.jasper.editor.ui`: new `TextViews`, `DocumentArea`, `EditorTab` (Task 1b); `EditorWindow` (Tasks 2, 3, 4).
- Editor `dev.jasper.editor.session`: `DocumentSession` (Task 1b).
- Editor `dev.jasper.editor`: `EditorPlugin` (Tasks 2, 4); `api/Editor`, `api/EditorDocument` (Task 2, Javadoc);
  `plugin.toml` (Task 2).
- Tests: `VimUndoManagerTest` (Task 1a); new `TextViewsTest` (Tasks 1b, 3), `MarkdownPerformanceTest`, `PreviewPaneTest`,
  `ScrollLinkTest` (Task 1b); new `EditorViewsTest` (Tasks 2, 3), `EditorPluginTest` (Tasks 2, 4), `TabShortcutCollisionTest` (Task 2);
  `ExFilesTest`, `CompletionTest` (Task 3); `EditorWindowTest` (Task 2, plan 9a's case; Task 4).
- Documents: `docs/sdk-reference.html` (Task 2, generated), `docs/editor-vim-reference.md` (Task 3, generated);
  `docs/editor.md`, `docs/command-line.md`, `docs/plugin-apis.md`, `docs/STATUS.md`, this plan's banner (Task 5); the
  ledger (Tasks 1b, 2).

## Before you start

Plan 9a is executed first; its last commit is its documentation and status commit (or its final fix wave).

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing: plan 9a's last commit
grep -c 'session.attach(area()::getText, this::replaceText);' plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java  # 1
grep -c '    public boolean openRestored(EditorDocument document, WindowOwner owner) {' plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java  # 1
grep -c "            // Until plan 9b's second view: the file's open pane shows, as Open in Editor… shows it." plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java  # 1
grep -c 'void openBesideOfAFileThatIsOpenShowsItsOpenPane()' plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java  # 1
grep -c 'RELOAD_VIMRC = "dev.jasper.editor.reload_vimrc", OPEN_IN_PANE = "dev.jasper.editor.open_in_pane";' plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java  # 1
grep -c 'version = "0.14.0"' plugins/editor/src/main/resources/plugin.toml                                   # 1 (else the Editor moved: take its next minor)
grep -c 'A file that is open already shows its open pane instead' docs/editor.md                              # 1 (plan 9a's sentence)
grep -c '\*\*Toggle Comment\*\* and \*\*Revert to Saved\*\*, then \*\*Split\*\*' docs/editor.md                # 1
grep -c '"dev.jasper.editor.reload_vimrc"' jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java  # 1
grep -c 'void theEditorsNewActionsHaveNoDefaultAndCanBeBound()' jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java  # 1
```

Every command prints the stated result. Each executed plan of this series added a `docs/STATUS.md` entry at the top;
this plan's goes above plan 9a's. Create the two helper scripts (Global Constraints).

---

### Task 1a: One undo history for every text area of a document

Spec 6's undo and its own scroll position below the Editor's tabs (Rulings 1-4): a `VimTextArea` can show another's
document and share its undo history, which takes each edit once, brackets each view's own Vim steps, ends one view's
open change where another edits, leaves the other views' scroll positions through an edit, and keeps their carets on an
undo. A text area that lets go of the document leaves the others its history.

**Files:**
- Replace: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimUndoManager.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java` (class Javadoc, `:33-42`, `attach`
  `:55-57`, `processKeyEvent` `:123`, `processInputMethodEvent` `:214`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/VimUndoManagerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `public void VimTextArea.share(VimTextArea other)`: this area shows `other`'s document and undo history from now on;
  - `public void VimTextArea.unshare()`: this area takes an empty document and a history of its own; nothing while no
    other area shows the document;
  - `VimUndoManager`: `void join(RTextArea)`, `boolean shared()`, `void leave(RTextArea, Runnable swap)`,
    `void view(RTextArea)` (the view that edits now); `vim(boolean)`, `beginStep`, `endStep`, `stepCursor`, `sync`,
    `stepOpen`, `nextUndo`, `nextRedo`, `doneSeqs` and `undoneSeqs` act on the view that edits now.

- [ ] **Step 1: Write the failing tests**

Add five cases to `VimUndoManagerTest`; the last runs each step as an event of its own, off the event thread, so the
scrolls Swing queues run between them:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
edit("plugins/editor/src/test/java/dev/jasper/editor/vim/VimUndoManagerTest.java", [(
"""            assertThat(undo.stepOpen()).as("the outside undo reopens the step it had to close").isTrue();
            assertThat(area.getText()).isEqualTo("x");
        });
    }
}
""",
"""            assertThat(undo.stepOpen()).as("the outside undo reopens the step it had to close").isTrue();
            assertThat(area.getText()).isEqualTo("x");
        });
    }

    /** Pane flexibility spec 6: two views of one document share its one history, so an undo in either undoes its last edit. */
    @Test void twoViewsOfADocumentShareOneUndoHistory() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            assertThat(b.getDocument()).isSameAs(a.getDocument());
            assertThat(b.vimUndo()).isSameAs(a.vimUndo());
            a.setCaretPosition(1);
            a.replaceSelection("a");
            assertThat(a.vimUndo().nextUndo().edits).as("taken once, though each view registers the history with the document")
                .isEqualTo(1);
            b.setCaretPosition(0);
            b.replaceSelection("b");
            assertThat(a.getText()).isEqualTo("bxa");
            a.undoLastAction();
            assertThat(b.getText()).as("the last edit, made in the other view").isEqualTo("xa");
            b.undoLastAction();
            assertThat(a.getText()).isEqualTo("x");
            assertThat(a.canUndo()).isFalse();
            b.redoLastAction();
            a.redoLastAction();
            assertThat(a.getText()).isEqualTo("bxa");
        });
    }

    /** An undo puts only its own view's caret on the change; the other view keeps its selection on the text it was on. */
    @Test void anUndoMovesOnlyItsOwnViewsCaretToTheChange() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("one\\ntwo\\nthree\\n");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            b.select(8, 13);
            a.setCaretPosition(0);
            a.replaceSelection("zero\\n");
            assertThat(b.getSelectedText()).as("an edit above moves the other view's selection with its text").isEqualTo("three");
            a.undoLastAction();
            assertThat(a.getCaretPosition()).as("the undoing view's caret goes to the change").isZero();
            assertThat(b.getSelectedText()).as("the other view keeps its selection").isEqualTo("three");
            assertThat(b.getCaretPosition()).isEqualTo(13);
            a.redoLastAction();
            assertThat(b.getSelectedText()).isEqualTo("three");
            assertThat(b.getSelectionStart()).isEqualTo(13);
        });
    }

    /**
     * A Vim change open in one view ends where another view edits, as Vim's u_sync ends a change where the cursor goes to
     * another window: undo takes the other view's edit first, then the change, and the first view's engine still closes
     * what it opened.
     */
    @Test void aVimChangeOpenInOneViewEndsWhereAnotherViewEdits() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            VimUndoManager undo = a.vimUndo();
            undo.vim(true);
            undo.beginStep(0, 1);
            a.setCaretPosition(1);
            a.replaceSelection("a");
            b.setCaretPosition(0);
            b.replaceSelection("b");
            assertThat(b.getText()).isEqualTo("bxa");
            b.undoLastAction();
            assertThat(a.getText()).as("the other view's edit, the last").isEqualTo("xa");
            assertThat(a.vimUndo().stepOpen()).as("the change stays open for the first view's engine").isTrue();
            a.undoLastAction();
            assertThat(a.getText()).as("then the first view's change").isEqualTo("x");
            a.vimUndo().endStep();
            assertThat(a.vimUndo().stepOpen()).isFalse();
            assertThat(a.canUndo()).isFalse();
            a.redoLastAction();
            a.redoLastAction();
            assertThat(b.getText()).isEqualTo("bxa");
        });
    }

    /** The view a document was first shown in lets go of it: the other keeps the text and its history, and it gets an empty one. */
    @Test void aViewThatLetsGoLeavesTheOtherTheTextAndItsHistory() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            b.setCaretPosition(1);
            b.replaceSelection("b");
            var shared = b.getDocument();
            a.unshare();
            assertThat(a.getDocument()).isNotSameAs(shared);
            assertThat(a.getText()).isEmpty();
            assertThat(a.canUndo()).as("a history of its own").isFalse();
            a.replaceSelection("z");
            assertThat(b.getText()).isEqualTo("xb");
            assertThat(b.canUndo()).as("the other keeps its history").isTrue();
            b.undoLastAction();
            assertThat(b.getText()).isEqualTo("x");
            assertThat(a.getText()).isEqualTo("z");
            b.unshare();
            assertThat(b.getDocument()).as("the only view keeps its document").isSameAs(shared);
        });
    }

    /**
     * Each view keeps its own scroll position (pane flexibility spec 6): an edit in one view that moves another view's
     * caret leaves that view where it was scrolled, though Swing scrolls a moved caret into sight. Each step runs as an
     * event of its own, so the scrolls Swing queues run between them.
     */
    @Test void anEditInOneViewLeavesTheOtherViewsScrollPosition() throws Exception {
        var a = new java.util.concurrent.atomic.AtomicReference<VimTextArea>();
        var b = new java.util.concurrent.atomic.AtomicReference<VimTextArea>();
        var port = new java.util.concurrent.atomic.AtomicReference<javax.swing.JViewport>();
        onEdt(() -> {
            a.set(new VimTextArea());
            a.get().setText("line\\n".repeat(600));
            a.get().discardAllEdits();
            b.set(new VimTextArea());
            b.get().share(a.get());
            var scroll = new org.fife.ui.rtextarea.RTextScrollPane(a.get());
            scroll.setSize(400, 200);
            scroll.doLayout();
            scroll.getViewport().doLayout();
            port.set(scroll.getViewport());
            try { a.get().setCaretPosition(a.get().getLineStartOffset(10)); }
            catch (javax.swing.text.BadLocationException impossible) { throw new AssertionError(impossible); }
        });
        onEdt(() -> port.get().setViewPosition(new java.awt.Point(0, 4500)));
        onEdt(() -> {
            b.get().setCaretPosition(0);
            b.get().replaceSelection("x");
        });
        onEdt(() -> { });
        onEdt(() -> assertThat(port.get().getViewPosition().y).as("the first view stays where it was scrolled").isEqualTo(4500));
    }
}
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:compileTestJava`
Expected: FAIL: `VimTextArea.share` and `unshare` are not found (`cannot find symbol`).

- [ ] **Step 3: Implement**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimUndoManager.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RUndoManager;

/**
 * A document's undo history (spec 3.3), shared by every text area that shows the document (pane flexibility spec 6):
 * an undo in any of them undoes the last edit made in any of them. With Vim off it groups edits as RSyntaxTextArea's
 * own manager does (neighbouring typed characters form one step). With Vim on it never guesses: each Vim change the
 * engine brackets with {@link #beginStep} and {@link #endStep} is one step, and any other edit (the Editor's Replace, a
 * paste through ⌘V) is a step of its own. RSyntaxTextArea's atomic edits inside a Vim step join that step.
 *
 * <p>Each view brackets its own steps. The view that edits now is the one the user last typed in, clicked or focused,
 * or whose Vim engine last asked ({@link #view}); when another view edits, the open steps of the one before end there,
 * as Vim's u_sync ends a change when the cursor goes to another window. An edit leaves every other view's scroll
 * position where it was, and an undo or redo moves only its own view's caret to the change: every other view keeps its
 * caret, selection and scroll position.
 */
final class VimUndoManager extends RUndoManager {
    /** One undo step, remembering where the cursor was before it, for Vim's cursor placement after undo. */
    static final class Step extends CompoundEdit {
        int cursorLine, cursorCol;
        int edits;
        /** Its change number, counting every step added (Vim's uh_seq). */
        int seq;

        Step(int cursorLine, int cursorCol) {
            this.cursorLine = cursorLine;
            this.cursorCol = cursorCol;
        }

        void take(UndoableEdit edit) {
            addEdit(edit);
            edits++;
        }

        // The manager treats a step as finished; addEdit above still works until end() because it reads the field.
        @Override public boolean isInProgress() { return false; }
        @Override public String getUndoPresentationName() { return UIManager.getString("AbstractUndoableEdit.undoText"); }
        @Override public String getRedoPresentationName() { return UIManager.getString("AbstractUndoableEdit.redoText"); }
    }

    /** One view's open steps and whether Vim groups its edits. */
    private static final class View {
        final RTextArea area;
        boolean vim;
        Step vimStep, internal;
        int stepDepth, internalDepth;

        View(RTextArea area) { this.area = area; }
    }

    private final List<View> views = new ArrayList<>();
    /** The view that edits now. */
    private View view;
    private Step typing;
    private int lastOffset;
    /** The change numbers of the steps on the stack, oldest first; the first {@link #done} are done. */
    private final List<Integer> seqs = new ArrayList<>();
    private int done, seqLast;
    /** The last edit taken: every view registers this history with the document, which hands it each edit once per view. */
    private UndoableEdit heard;
    /** While a view lets go of the document ({@link #leave}): its text area's swap discards nothing here. */
    private boolean leaving;

    VimUndoManager(RTextArea area) {
        // RUndoManager keeps its area only for the grouping and atomic edits this class overrides, so it is given none: the
        // history outlives the view it was made for, which may close first.
        super(null);
        view = new View(area);
        views.add(view);
        setLimit(1000);
    }

    /**
     * {@code area} shows this history's document too, and edits through it from now on.
     *
     * @param area a text area that has not shown the document before
     */
    void join(RTextArea area) { find(area); }

    /** @return whether more than one text area shows the document */
    boolean shared() { return views.size() > 1; }

    /**
     * {@code area} stops showing the document. Its open steps end here, and {@code swap}, which gives it a document of
     * its own, discards nothing of this history; the first of the other views edits next.
     *
     * @param area one of the views, not the only one
     * @param swap gives the area another document
     */
    void leave(RTextArea area, Runnable swap) {
        View leaving = views.stream().filter(candidate -> candidate.area == area).findFirst().orElse(null);
        if (leaving == null || views.size() == 1) return;
        closeAll(leaving);
        typing = seal(typing);
        views.remove(leaving);
        if (view == leaving) view = views.getFirst();
        this.leaving = true;
        try { swap.run(); } finally { this.leaving = false; }
    }

    /**
     * {@code area} edits from now on. When another view edited before, its open steps that changed something end here
     * and open again, empty, at the same depth, so its engine's own {@link #endStep} still balances; the typing step ends
     * too.
     *
     * @param area the text area that edits now
     */
    void view(RTextArea area) {
        if (view.area == area) return;
        View next = find(area);
        sync(view);
        typing = seal(typing);
        view = next;
    }

    /** @param on whether Vim mode groups the edits of the view that edits now */
    void vim(boolean on) {
        view.vim = on;
        typing = seal(typing);
    }

    /**
     * The cursor moved before the open step's first change (an operator goes to its span's start, as Vim's
     * do_pending_operator does before u_save): the step records the cursor there.
     */
    void stepCursor(int cursorLine, int cursorCol) {
        if (view.vimStep != null && view.vimStep.edits == 0) {
            view.vimStep.cursorLine = cursorLine;
            view.vimStep.cursorCol = cursorCol;
        }
    }

    /** Opens a Vim step, or nests inside the open one. */
    void beginStep(int cursorLine, int cursorCol) { beginStep(view, cursorLine, cursorCol); }

    private void beginStep(View in, int cursorLine, int cursorCol) {
        if (in.stepDepth++ == 0) {
            typing = seal(typing);
            in.vimStep = new Step(cursorLine, cursorCol);
        }
    }

    /** Closes one level; the outermost close puts the step on the undo stack if it changed anything. */
    void endStep() { endStep(view); }

    private void endStep(View in) {
        if (in.stepDepth == 0 || --in.stepDepth > 0) return;
        Step step = in.vimStep;
        in.vimStep = null;
        step.end();
        if (step.edits > 0) add(step);
        updateActions();
    }

    /** Adds a new step to the stack, numbering it; the steps that were undone are gone. */
    private void add(Step step) {
        step.seq = ++seqLast;
        while (seqs.size() > done) seqs.removeLast();
        seqs.add(step.seq);
        done++;
        if (seqs.size() > getLimit()) {
            seqs.removeFirst();
            done--;
        }
        addEdit(step);
    }

    /** @return the change numbers of the done steps, oldest first */
    List<Integer> doneSeqs() { return List.copyOf(seqs.subList(0, done)); }

    /** @return the change numbers of the undone steps, in the order redo brings them back */
    List<Integer> undoneSeqs() { return List.copyOf(seqs.subList(done, seqs.size())); }

    boolean stepOpen() { return view.stepDepth > 0; }

    /** u_sync: the open step ends here if it changed anything, and a new one goes on at the same depth. */
    void sync() { sync(view); }

    private void sync(View in) {
        if (in.vimStep == null || in.vimStep.edits == 0) return;
        int depth = in.stepDepth;
        closeAll(in);
        reopen(in, depth);
    }

    /** @return the step {@link #undo} would undo, or null when there is none or it is not a Vim step */
    Step nextUndo() { return editToBeUndone() instanceof Step step ? step : null; }

    /** @return the step {@link #redo} would redo, or null */
    Step nextRedo() { return editToBeRedone() instanceof Step step ? step : null; }

    @Override public void undoableEditHappened(UndoableEditEvent event) {
        UndoableEdit edit = event.getEdit();
        if (edit == heard) return;
        heard = edit;
        if (views.size() > 1) keepOtherViewports(view);
        if (view.vimStep != null) {
            view.vimStep.take(edit);
            return;
        }
        if (view.internalDepth > 0) {
            view.internal.take(edit);
            return;
        }
        if (view.vim) {
            Step step = new Step(-1, -1);
            step.take(edit);
            step.end();
            add(step);
            updateActions();
            return;
        }
        // RSyntaxTextArea's grouping: an edit next to the previous one continues its step.
        int caret = view.area.getCaretPosition();
        if (typing != null && Math.abs(caret - lastOffset) <= 1) {
            typing.take(edit);
            lastOffset = caret;
            return;
        }
        typing = seal(typing);
        typing = new Step(-1, -1);
        typing.take(edit);
        add(typing);
        lastOffset = caret;
        updateActions();
    }

    @Override public void beginInternalAtomicEdit() {
        if (view.internalDepth++ == 0 && view.vimStep == null) {
            typing = seal(typing);
            view.internal = new Step(-1, -1);
        }
    }

    @Override public void endInternalAtomicEdit() { endInternal(view); }

    private void endInternal(View in) {
        if (in.internalDepth == 0 || --in.internalDepth > 0 || in.internal == null) return;
        Step step = in.internal;
        in.internal = null;
        step.end();
        if (step.edits > 0) add(step);
        updateActions();
    }

    /** Counts an open step's edits, since RTextArea asks before it undoes (⌘Z in the middle of an insert). */
    @Override public synchronized boolean canUndo() {
        return view.vimStep != null && view.vimStep.edits > 0 || view.internal != null && view.internal.edits > 0 || super.canUndo();
    }

    /**
     * Nothing is redoable while an open step has edits: closing it for the redo would put it on the undo stack and
     * trim the redo stack, so the redo would fail (an outside ⌘⇧Z in the middle of an insert).
     */
    @Override public synchronized boolean canRedo() {
        if (view.vimStep != null && view.vimStep.edits > 0 || view.internal != null && view.internal.edits > 0) return false;
        return super.canRedo();
    }

    /**
     * An outside undo (⌘Z) while the engine has a step open: closes it so it is undoable, then reopens the same
     * nesting once the undo runs, so the engine's own {@link #endStep} later balances (spec: the rest of an insert
     * in progress stays one step). The other views keep their carets, selections and scroll positions.
     */
    @Override public void undo() {
        View in = view;
        int depth = in.stepDepth;
        closeAll(in);
        Runnable keep = keepOthers(in);
        try {
            super.undo();
            done--;
        } finally {
            keep.run();
            reopen(in, depth);
        }
    }

    /** The redo counterpart of {@link #undo}. */
    @Override public void redo() {
        View in = view;
        int depth = in.stepDepth;
        closeAll(in);
        Runnable keep = keepOthers(in);
        try {
            super.redo();
            done++;
        } finally {
            keep.run();
            reopen(in, depth);
        }
    }

    /**
     * Every view but {@code acting} keeps its scroll position through an edit: an edit before a view's caret moves the
     * caret, which Swing then scrolls into sight later, through the event queue; the view's place is put back after that.
     */
    private void keepOtherViewports(View acting) {
        for (View other : views) {
            if (other == acting || !(other.area.getParent() instanceof JViewport port)) continue;
            java.awt.Point shown = port.getViewPosition();
            SwingUtilities.invokeLater(() -> { if (other.area.getParent() == port) port.setViewPosition(shown); });
        }
    }

    /**
     * Where each view but {@code acting} has its caret, selection and scroll position: an undo or redo sets every caret
     * on the document to the change (Swing's {@code DefaultCaret} reads it from the undo's event), and the views that
     * did not undo go back to the text they were on, as an edit elsewhere leaves them.
     *
     * @return puts them back
     */
    private Runnable keepOthers(View acting) {
        List<Runnable> back = new ArrayList<>();
        for (View other : views) {
            if (other == acting) continue;
            RTextArea area = other.area;
            try {
                Position dot = area.getDocument().createPosition(area.getCaret().getDot());
                Position mark = area.getDocument().createPosition(area.getCaret().getMark());
                JViewport port = area.getParent() instanceof JViewport parent ? parent : null;
                java.awt.Point shown = port == null ? null : port.getViewPosition();
                back.add(() -> {
                    area.getCaret().setDot(mark.getOffset());
                    area.getCaret().moveDot(dot.getOffset());
                    // The caret shows itself later, through the event queue: the view's place is put back after that.
                    if (port != null) SwingUtilities.invokeLater(() -> { if (area.getParent() == port) port.setViewPosition(shown); });
                });
            } catch (BadLocationException impossible) {
                // The caret's offsets are in the document.
            }
        }
        return () -> back.forEach(Runnable::run);
    }

    /** Reopens {@code depth} levels of Vim step after an outside undo/redo, cursor taken from the area's caret now. */
    private void reopen(View in, int depth) {
        if (depth == 0) return;
        int line = 0, col = 0;
        try {
            int offset = in.area.getCaretPosition();
            line = in.area.getLineOfOffset(offset);
            col = offset - in.area.getLineStartOffset(line);
        } catch (BadLocationException ignored) {
            // Falls back to (0, 0); the reopened step's remembered cursor is only advisory.
        }
        for (int n = 0; n < depth; n++) beginStep(in, line, col);
    }

    @Override public void discardAllEdits() {
        if (leaving) return;
        for (View each : views) {
            each.stepDepth = each.internalDepth = 0;
            each.vimStep = each.internal = null;
        }
        typing = null;
        heard = null;
        seqs.clear();
        done = 0;
        seqLast = 0;
        super.discardAllEdits();
    }

    /** Ends every open step of a view, as an undo from outside Vim (⌘Z during an insert) must. */
    private void closeAll(View in) {
        while (in.stepDepth > 0) endStep(in);
        if (in.internal != null) {
            in.internalDepth = 1;
            endInternal(in);
        }
        typing = seal(typing);
    }

    /** @return the view for {@code area}, a new one when it has none */
    private View find(RTextArea area) {
        for (View each : views) if (each.area == area) return each;
        View added = new View(area);
        views.add(added);
        return added;
    }

    private static Step seal(Step step) {
        if (step != null) step.end();
        return null;
    }
}
```

`VimTextArea` keeps one history, shares another area's and lets go of it, and says which view edits now:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
V = "plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java"
edit(V, [(
"""/**
 * Every Editor tab's text area (spec 1.1). With Vim off it is an ordinary RSyntaxTextArea. With Vim on, key events
 * reach the attached {@link VimEditor} before Swing's key bindings: a key Vim takes is consumed here, so neither the
 * text area's bindings nor the Editor's window shortcuts see it (the handler rule, spec 2.2).
 */""",
"""/**
 * Every Editor tab's text area (spec 1.1). With Vim off it is an ordinary RSyntaxTextArea. With Vim on, key events
 * reach the attached {@link VimEditor} before Swing's key bindings: a key Vim takes is consumed here, so neither the
 * text area's bindings nor the Editor's window shortcuts see it (the handler rule, spec 2.2). Several text areas can
 * show one document ({@link #share}): they share its undo history, and the one the user types in, clicks or focuses is
 * the one that edits it.
 */""", 1), (
"""    /** An empty text area. */
    public VimTextArea() { }

    @Override protected RUndoManager createUndoManager() {
        undo = new VimUndoManager(this);
        undo.vim(vim != null);
        return undo;
    }

    VimUndoManager vimUndo() { return undo; }
""",
"""    /** Whether the area tells its history when it gains the focus: once another area shares its document. */
    private boolean followsFocus;

    /** An empty text area. */
    public VimTextArea() { }

    /**
     * The area keeps one history: the superclass constructor asks for it, and {@code discardAllEdits} asks again after
     * emptying it, which keeps the history a shared document's views have in common (pane flexibility spec 6).
     */
    @Override protected RUndoManager createUndoManager() {
        if (undo == null) {
            undo = new VimUndoManager(this);
            undo.vim(vim != null);
        }
        return undo;
    }

    /** @return the document's undo history, with this area as the one that edits now, since its Vim engine asks */
    VimUndoManager vimUndo() {
        edits();
        return undo;
    }

    /** This area edits the document now: the history's steps go by its caret and its Vim engine. */
    private void edits() { if (undo != null) undo.view(this); }

    /** From now on the area edits the document whenever it gains the focus, as one of several views of it. */
    private void followFocus() {
        if (followsFocus) return;
        followsFocus = true;
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { edits(); }
        });
    }

    /**
     * Shows {@code other}'s document from now on, as another view of it (pane flexibility spec 6): the same text and
     * language, and the one undo history, so an undo in either undoes the document's last edit. Call it before anything
     * else listens to this area's document.
     *
     * @param other a text area showing the document
     */
    public void share(VimTextArea other) {
        VimUndoManager history = other.undo;
        history.join(this);
        other.followFocus();
        followFocus();
        // setDocument's discardAllEdits empties this area's own history, then createUndoManager hands it the shared one.
        undo = history;
        setDocument(other.getDocument());
    }

    /**
     * Stops showing a shared document: this area gets an empty document and a history of its own, and the other views
     * keep the text and its history, which this area's swap leaves as it was. Nothing happens while no other area shows
     * the document.
     */
    public void unshare() {
        VimUndoManager history = undo;
        if (!history.shared()) return;
        undo = null;
        history.leave(this, () -> setDocument(createDefaultModel()));
    }

    @Override public void replaceSelection(String text) {
        edits();
        super.replaceSelection(text);
    }

    @Override public void beginAtomicEdit() {
        edits();
        super.beginAtomicEdit();
    }

    @Override public void undoLastAction() {
        edits();
        super.undoLastAction();
    }

    @Override public void redoLastAction() {
        edits();
        super.redoLastAction();
    }
""", 1), (
"""    void attach(VimEditor editor) {
        vim = editor;
        undo.vim(editor != null);
""",
"""    void attach(VimEditor editor) {
        vim = editor;
        edits();
        undo.vim(editor != null);
""", 1), (
"""    @Override protected void processKeyEvent(KeyEvent event) {
        VimEditor editor = vim;
""",
"""    @Override protected void processKeyEvent(KeyEvent event) {
        edits();
        VimEditor editor = vim;
""", 1), (
"""    @Override protected void processInputMethodEvent(InputMethodEvent event) {
        VimEditor editor = vim;
""",
"""    @Override protected void processInputMethodEvent(InputMethodEvent event) {
        edits();
        VimEditor editor = vim;
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.*' --continue`
Expected: PASS: the five new cases, and every existing Vim case (one area's history is reused across its discards
instead of made anew, which changes nothing a test reads; `RUndoManager` is given no area, Verified facts 1).

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim
git commit -F - <<'EOF'
Editor: one undo history for every text area of a document

A VimTextArea can show another's document and share its
VimUndoManager, which RSyntaxTextArea's createUndoManager hook hands
every text area: the history takes each edit once, brackets each
view's own Vim steps, ends one view's open change where another view
edits, leaves the other views' scroll positions through an edit and
keeps their carets, selections and scroll positions when one view
undoes. An area that lets go of the document keeps nothing of it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 1b: A file's views share its text

Spec 6's "one document, many views" in the Editor's tabs (Rulings 5, 20): a second `EditorTab` of a session shows the
first's text through Task 1a's shared text area; `TextViews` is the session's text for all its views, replacing it once
for every view. Cancel in one view of a file that is still loading leaves the read to the others, and a closed view
takes its listener off the session.

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/TextViews.java` (and its ledger row)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/DocumentArea.java` (the constructor, `:95`),
  `EditorTab.java` (class Javadoc `:22`, `:28`, the constructor `:120-132`, `shutdown` `:150-154`, `replaceText`
  `:214-224`, DISMISS `:253`), `plugins/editor/src/main/java/dev/jasper/editor/session/DocumentSession.java`
  (`addListener` `:104-105`)
- Test: create `plugins/editor/src/test/java/dev/jasper/editor/ui/TextViewsTest.java` (and its ledger row);
  `MarkdownPerformanceTest`, `PreviewPaneTest`, `ScrollLinkTest` (the constructor)

**Interfaces:**
- Consumes: Task 1a's `VimTextArea.share` and `unshare`.
- Produces:
  - `DocumentArea(DocumentArea shares)`, its only constructor (null for a document's first view);
  - `EditorTab(UiKit kit, DocumentSession session, Consumer<EditorTab> close, boolean debounce, EditorTab sharing)`
    (package-private; the four-argument constructor passes null): another view of `sharing`'s session;
  - `int EditorTab.beginReplace(boolean keepPosition)`, `void endReplace()`, `void replaced(int line)`;
  - `final class TextViews` (package-private): `TextViews(DocumentSession)`, `void add(EditorTab)`,
    `void remove(EditorTab)`, `int size()`;
  - `public void DocumentSession.removeListener(Runnable listener)`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/ui/TextViewsTest.java`:

```java
package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.vim.KeyInput;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.sdk.testing.FakeKit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pane flexibility spec 6: several views of one document, each an {@link EditorTab}, show one text with one history. */
@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class TextViewsTest {
    @TempDir Path folder;
    private final List<Runnable> background = new ArrayList<>();
    private final List<Question> asked = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(background::add, Runnable::run,
        question -> { asked.add(question); return question.choices().getFirst(); }, () -> 1 << 20);
    private final List<EditorTab> views = new ArrayList<>();

    @BeforeEach void languages() { Languages.register(); }

    @AfterEach void close() { views.forEach(EditorTab::dispose); }

    private void drain() { while (!background.isEmpty()) background.removeFirst().run(); }

    private EditorTab first(DocumentSession session) {
        var view = new EditorTab(new FakeKit("dev.jasper.editor"), session, closed -> { }, false);
        views.add(view);
        return view;
    }

    private EditorTab first(Path file) {
        var view = first(new DocumentSession(new LocalDocument(file, background::add), environment));
        view.session().load();
        drain();
        return view;
    }

    private EditorTab second(EditorTab first) {
        var view = new EditorTab(new FakeKit("dev.jasper.editor"), first.session(), closed -> { }, false, first);
        views.add(view);
        return view;
    }

    @Test void aSecondViewShowsTheSameTextAndEveryEditAtOnce() throws Exception {
        var one = first(Files.writeString(folder.resolve("a.txt"), "alpha\n"));
        var two = second(one);
        assertThat(two.area().getDocument()).isSameAs(one.area().getDocument());
        assertThat(two.area().getText()).isEqualTo("alpha\n");
        two.area().setCaretPosition(5);
        two.area().replaceSelection(" beta");
        assertThat(one.area().getText()).isEqualTo("alpha beta\n");
        assertThat(one.session().dirty()).isTrue();
        one.area().undoLastAction();
        assertThat(two.area().getText()).as("one history: an undo in either view undoes the last edit").isEqualTo("alpha\n");
        assertThat(two.session().dirty()).isFalse();
    }

    /** Revert to Saved from one view replaces the text once; each view keeps its caret's line, or the last when it is gone. */
    @Test void aRevertReplacesTheTextOnceAndEachViewKeepsItsCaretLine() throws Exception {
        Path file = Files.writeString(folder.resolve("lines.txt"), "1\n2\n3\n4\n5\n");
        var one = first(file);
        var two = second(one);
        one.area().setCaretPosition(one.area().getLineStartOffset(1));
        two.area().setCaretPosition(two.area().getLineStartOffset(4));
        two.area().replaceSelection("x");
        Files.writeString(file, "a\nb\n");
        two.session().revert();
        drain();
        assertThat(asked).extracting(Question::title).as("asked once, about the document").containsExactly("Revert to Saved");
        assertThat(one.area().getText()).isEqualTo("a\nb\n");
        assertThat(one.area().getCaretLineNumber()).isEqualTo(1);
        assertThat(two.area().getCaretLineNumber()).as("past the new end: the last line").isEqualTo(2);
        assertThat(one.area().canUndo() || two.area().canUndo()).as("the one history starts again").isFalse();
        assertThat(one.session().dirty()).isFalse();
    }

    /** Closing a document's first view leaves the other its text, its history and the session's reads. */
    @Test void closingTheFirstViewLeavesTheOtherTheTextAndItsHistory() throws Exception {
        Path file = Files.writeString(folder.resolve("keep.txt"), "keep\n");
        var one = first(file);
        var two = second(one);
        one.area().setCaretPosition(4);
        one.area().replaceSelection("!");
        var shared = one.area().getDocument();
        one.dispose();
        assertThat(one.area().getDocument()).as("the closed view lets go of the document").isNotSameAs(shared);
        assertThat(two.area().getDocument()).isSameAs(shared);
        assertThat(two.session().dirty()).as("the session reads the text of the view left").isTrue();
        two.area().undoLastAction();
        assertThat(two.area().getText()).isEqualTo("keep\n");
        Files.writeString(file, "changed\n");
        two.session().reload();
        drain();
        assertThat(two.area().getText()).as("a reload reaches the view left").isEqualTo("changed\n");
    }

    /** Cancel in one view of a file that is still loading closes that view alone: the read goes on for the other. */
    @Test void cancelInOneViewOfALoadingFileLeavesTheOtherTheRead() throws Exception {
        Path file = Files.writeString(folder.resolve("slow.txt"), "slow\n");
        var closing = new ArrayList<EditorTab>();
        var one = new EditorTab(new FakeKit("dev.jasper.editor"), new DocumentSession(new LocalDocument(file, background::add), environment),
            closing::add, false);
        views.add(one);
        one.session().load();
        var two = second(one);
        dev.jasper.editor.testsupport.Kits.click(one.view(), EditorTab.DISMISS);
        assertThat(closing).as("the view asks to close").containsExactly(one);
        one.dispose();
        drain();
        assertThat(two.session().state()).isEqualTo(DocumentSession.State.READY);
        assertThat(two.area().getText()).isEqualTo("slow\n");
    }

    /** A closed view leaves nothing behind on its file's session, which outlives it while other views show the file. */
    @Test void aClosedViewLeavesNoListenerOnTheSession() throws Exception {
        var one = first(Files.writeString(folder.resolve("listeners.txt"), "x\n"));
        int baseline = listeners(one.session());
        for (int n = 0; n < 5; n++) new EditorTab(new FakeKit("dev.jasper.editor"), one.session(), closed -> { }, false, one).dispose();
        assertThat(listeners(one.session())).isEqualTo(baseline);
    }

    private static int listeners(DocumentSession session) throws ReflectiveOperationException {
        var field = DocumentSession.class.getDeclaredField("listeners");
        field.setAccessible(true);
        return ((List<?>) field.get(session)).size();
    }

    /** Each view has its own Vim state: Vim in one view and not in the other, whose undo takes Vim's change. */
    @Test void vimInOneViewAndNotInTheOther() throws Exception {
        var one = first(Files.writeString(folder.resolve("vim.txt"), "abc\n"));
        var two = second(one);
        one.vim(Optional.of(new Vim(VimHost.detached())));
        assertThat(one.vimEditor()).isPresent();
        assertThat(two.vimEditor()).as("Vim in the first view only").isEmpty();
        one.area().setCaretPosition(0);
        for (KeyInput key : KeyInput.parse("x")) one.vimEditor().orElseThrow().feed(key);
        assertThat(two.area().getText()).isEqualTo("bc\n");
        two.area().undoLastAction();
        assertThat(one.area().getText()).as("the other view's undo takes Vim's change").isEqualTo("abc\n");
        for (KeyInput key : KeyInput.parse("u")) one.vimEditor().orElseThrow().feed(key);
        assertThat(one.vimEditor().orElseThrow().message()).as("nothing older to undo").isEqualTo("Already at oldest change");
    }

    /** Each view has its own Markdown mode: the preview in one and the source in the other, each switched on its own. */
    @Test void aMarkdownPreviewInOneViewAndTheSourceInTheOther() {
        var one = first(DocumentSession.untitled("Untitled", environment));
        var two = second(one);
        for (EditorTab view : List.of(one, two)) view.previews(background::add, Runnable::run);
        one.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        two.area().setText("# One\n");
        one.markdownMode(MarkdownMode.PREVIEW, false);
        assertThat(one.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
        assertThat(two.markdown().mode()).as("each view its own mode").isEqualTo(MarkdownMode.SOURCE);
        assertThat(two.focusedContent()).as("the other view keeps its source").isSameAs(two.area());
        two.markdownMode(MarkdownMode.SPLIT, false);
        assertThat(one.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
        assertThat(two.markdown().mode()).isEqualTo(MarkdownMode.SPLIT);
    }
}
```

The three tests that build a `DocumentArea` alone name its one constructor:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
U = "plugins/editor/src/test/java/dev/jasper/editor/ui/"
edit(U + "MarkdownPerformanceTest.java", [("var area=new DocumentArea();", "var area=new DocumentArea(null);", 1)])
edit(U + "PreviewPaneTest.java", [("var area=new DocumentArea();", "var area=new DocumentArea(null);", 1)])
edit(U + "ScrollLinkTest.java", [("area=new DocumentArea();", "area=new DocumentArea(null);", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:compileTestJava`
Expected: FAIL: the five-argument `EditorTab` is not found, and `new DocumentArea(null)` cannot be applied to the
no-argument constructor.

- [ ] **Step 3: Implement**

Create `plugins/editor/src/main/java/dev/jasper/editor/ui/TextViews.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.vim.VimTextArea;
import java.util.ArrayList;
import java.util.List;

/**
 * The views of one document (pane flexibility spec 6): their text areas show one Swing document with one undo history,
 * and the session reads its text from them and replaces it once for all of them, each view keeping its own caret line.
 * UI thread only.
 */
final class TextViews {
    private final List<EditorTab> views = new ArrayList<>();
    /** The text the last view showed when it closed, which the session may still read. */
    private String detached = "";

    /** @param session the document's session, which from now on reads and replaces the text through these views */
    TextViews(DocumentSession session) { session.attach(this::text, this::replace); }

    /** @param view a view whose text area shows the document */
    void add(EditorTab view) { views.add(view); }

    /** @return how many views show the document */
    int size() { return views.size(); }

    /**
     * {@code view} has closed: the other views keep the document and its history, and its text area lets go of them.
     *
     * @param view one of the views
     */
    void remove(EditorTab view) {
        if (!views.remove(view)) return;
        if (views.isEmpty()) detached = view.area().getText();
        else view.area().unshare();
    }

    private String text() { return views.isEmpty() ? detached : views.getFirst().area().getText(); }

    /** A load or reload: the text is set once, which every view shows, and the one history starts again. */
    private void replace(String text, boolean keepPosition) {
        if (views.isEmpty()) {
            detached = text;
            return;
        }
        List<EditorTab> open = List.copyOf(views);
        int[] lines = new int[open.size()];
        for (int i = 0; i < lines.length; i++) lines[i] = open.get(i).beginReplace(keepPosition);
        VimTextArea area = open.getFirst().area();
        try { area.setText(text); }
        finally { for (EditorTab view : open) view.endReplace(); }
        area.discardAllEdits();
        for (int i = 0; i < lines.length; i++) open.get(i).replaced(lines[i]);
    }
}
```

`DocumentArea` shares its text area before its `ScrollLink` listens to the document; `EditorTab` builds another view of
a session, reads and replaces the text through `TextViews`, leaves a read to the file's other views on Cancel and
takes its listener off the session when it closes:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
D = "plugins/editor/src/main/java/dev/jasper/editor/ui/DocumentArea.java"
edit(D, [(
"""    DocumentArea() {
        setLayout(new BorderLayout());
""",
"""    /**
     * The text column of a view; with {@code shares}, of another view of the same document, whose text area this one's
     * shares before anything listens to it (pane flexibility spec 6).
     *
     * @param shares a view's text column, or null for the document's first
     */
    DocumentArea(DocumentArea shares) {
        if (shares != null) area.share(shares.area);
        setLayout(new BorderLayout());
""", 1)])
T = "plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java"
edit(T, [(
"""/** One document's descriptor controller. Only {@link DocumentArea} owns specialized Swing document content. */
public final class EditorTab {""",
"""/**
 * One view of a document: its descriptor controller. Only {@link DocumentArea} owns specialized Swing document content.
 * A document can have several views (pane flexibility spec 6), which show one text with one undo history; each has its
 * own caret, selection, scroll position, find bar, Vim state and Markdown mode.
 */
public final class EditorTab {""", 1), (
"""    private final DocumentSession session;
    private final DocumentArea document = new DocumentArea();
""",
"""    private final DocumentSession session;
    private final DocumentArea document;
    /** The document's views, this one among them, through which its session reads and replaces the text. */
    private final TextViews views;
    /** The view's listener on its session, which goes when the view closes: the session may outlive it. */
    private final Runnable refreshing = this::refresh;
""", 1), (
"""    /** Creates an owned kit view; closing the hosted view ends document refreshes as well. */
    public EditorTab(UiKit kit, DocumentSession session, Consumer<EditorTab> close, boolean debounce) {
        this.session = session;
        this.close = close;
        this.debounce = debounce;
        edits = new Timer(300, event -> { if (!closed) session.textEdited(); });
        edits.setRepeats(false);
        area().getDocument().addDocumentListener(editListener);
        session.attach(area()::getText, this::replaceText);
""",
"""    /** Creates an owned kit view, the session's first; closing the hosted view ends document refreshes as well. */
    public EditorTab(UiKit kit, DocumentSession session, Consumer<EditorTab> close, boolean debounce) {
        this(kit, session, close, debounce, null);
    }

    /**
     * Creates an owned kit view of {@code session}; with {@code sharing}, another view of it, which shows the same text
     * and undo history (pane flexibility spec 6).
     *
     * @param sharing an open view of the same session, or null for its first
     */
    EditorTab(UiKit kit, DocumentSession session, Consumer<EditorTab> close, boolean debounce, EditorTab sharing) {
        if (sharing != null && sharing.session != session) throw new IllegalArgumentException("A view shares its own document's text");
        this.session = session;
        this.close = close;
        this.debounce = debounce;
        document = new DocumentArea(sharing == null ? null : sharing.document);
        views = sharing == null ? new TextViews(session) : sharing.views;
        views.add(this);
        edits = new Timer(300, event -> { if (!closed) session.textEdited(); });
        edits.setRepeats(false);
        area().getDocument().addDocumentListener(editListener);
""", 1), (
"""        session.addListener(this::refresh);
        refresh();
    }
""",
"""        session.addListener(refreshing);
        refresh();
    }
""", 1), (
"""        if (vim != null) { vimBar.detach(); vim.close(); vim = null; }
        document.close();
        find = null;
    }
""",
"""        if (vim != null) { vimBar.detach(); vim.close(); vim = null; }
        document.close();
        find = null;
        session.removeListener(refreshing);
        // Last: the listeners above leave the document this view shared, which its other views keep.
        views.remove(this);
    }
""", 1), (
"""            case DISMISS -> { session.cancelLoad(); close.accept(this); }
""",
"""            // A read the file's other views wait for goes on (pane flexibility spec 6); the last view's Cancel stops it.
            case DISMISS -> { if (views.size() == 1) session.cancelLoad(); close.accept(this); }
""", 1), (
"""    private void replaceText(String text, boolean keepPosition) {
        if (closed) return;
        int line = keepPosition ? area().getCaretLineNumber() : 0;
        replacing = true;
        try { area().setText(text); } finally { replacing = false; }
        area().discardAllEdits();
        try { area().setCaretPosition(area().getLineStartOffset(Math.min(line, area().getLineCount() - 1))); }
        catch (BadLocationException impossible) { area().setCaretPosition(0); }
        if (vim != null) vim.textReplaced();
        if (markdown != null) markdown.sourceReloaded();
    }
""",
"""    /**
     * A load or reload replaces the text every view of the document shows ({@link TextViews}): no edit is counted
     * meanwhile.
     *
     * @param keepPosition whether the caret keeps its line
     * @return the line the caret goes back to, 0-based
     */
    int beginReplace(boolean keepPosition) {
        replacing = true;
        return keepPosition ? area().getCaretLineNumber() : 0;
    }

    /** The replaced text is in: edits count again. */
    void endReplace() { replacing = false; }

    /**
     * After a replace, the caret goes to the start of {@code line}, or of the last line when the text is shorter, and
     * Vim and the preview read the text again.
     *
     * @param line the line {@link #beginReplace} gave
     */
    void replaced(int line) {
        try { area().setCaretPosition(area().getLineStartOffset(Math.min(line, area().getLineCount() - 1))); }
        catch (BadLocationException impossible) { area().setCaretPosition(0); }
        if (vim != null) vim.textReplaced();
        if (markdown != null) markdown.sourceReloaded();
    }
""", 1)])
edit("plugins/editor/src/main/java/dev/jasper/editor/session/DocumentSession.java", [(
"""    /** @param listener runs after any change a view shows */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }
""",
"""    /** @param listener runs after any change a view shows */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    /** @param listener one {@link #addListener} added, which runs no more */
    public void removeListener(Runnable listener) { listeners.remove(listener); }
""", 1)])
PY
```

The ledger rows:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from ledger import add
add("plugins/editor/src/main/java/dev/jasper/editor/ui/TextViews.java", "dev.jasper.editor", "TextViews",
    "plugins/editor/src/test/java/dev/jasper/editor/ui/TextViewsTest.java",
    "New in workspace-tabs plan 9b Task 1; a document's views share one Swing document and undo history")
add("plugins/editor/src/test/java/dev/jasper/editor/ui/TextViewsTest.java", "dev.jasper.editor", "TextViewsTest",
    "plugins/editor/src/test/java/dev/jasper/editor/ui/TextViewsTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 9b Task 1; several views of one document")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*' --tests 'dev.jasper.editor.session.*' --continue`
Expected: PASS: `TextViewsTest` (7) and every existing tab, Markdown and session case. Then `./gradlew
:jasper-plugin-editor:check verifyPluginArchitecture`: PASS (`DocumentArea` has one constructor, Verified facts 9).

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Editor: a file's views share its text

A second view of a document shares the first's text area document
and undo history; the session reads and replaces its text through
TextViews, once for every view, each keeping its caret line. Cancel in
one view of a file that is still loading closes that view and leaves
the read to the others, and a closed view takes its listener off the
session.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The Editor's views of a file: Split File, opening and closing

Spec 6 in the Editor's window (Rulings 7-10, 15, 16, 19): `EditorWindow` keeps a file's views, most recently active
first, opens another view for Split File Right and Down, which starts at its source's caret and scroll position, and
for Open File Right… of an open file (plan 9a's branch), selects a file's view active last, and asks about unsaved
changes only when a file's last view closes, its closing guard a method of its own. A closed view takes its listener
off the session. Its Vim buffers become files (Task 3 gives Vim its windows). The Editor API says what other plugins
see.

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (`POPUP` `:53-54`, fields `:65`,
  `:85-89`, `commands` `:186`, the closing guard `:196-203`, `open` `:231-235`, `openLinked` `:247-251`, `owner(DocumentSession)` `:265`, `closed`
  `:342-350`, `moveCaret` `:354`, `approveTab` `:574-575`, `mayClose` `:655-663`, `:671`, `:679`, before `shutDown`
  `:698`, `buffers` and `tabNumbered` `:737-749`, `add` `:850-854`, `:884-888`, `:898-903`, `remove`, `select` and
  `activate` `:943-966`; on plan 9a's code, `openDocumentBeside`'s branch)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java` (constants `:48-50`, as 9a leaves them;
  after Reload vimrc's registration `:131-132`), `plugins/editor/src/main/resources/plugin.toml` (version),
  `plugins/editor/src/main/java/dev/jasper/editor/api/Editor.java` and `EditorDocument.java` (Javadoc),
  `docs/sdk-reference.html` (generated)
- Create: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorViewsTest.java` (and its ledger row)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java`; on plan 9a's code
  `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java` (9a's already-open case)

**Interfaces:**
- Consumes: Task 1b's five-argument `EditorTab` and `DocumentSession.removeListener`; plan 9a's `openBeside(WindowOwner, TabPlacement)`,
  `openDocumentBeside(EditorDocument, WindowOwner owner, TabPlacement placement)`, its `selectOpen` branch and its
  `EditorPlugin.OPEN_IN_PANE`.
- Produces:
  - `public void EditorWindow.splitFile(TabPlacement placement)` and `splitFile(WindowHandle window, TabPlacement
    placement)`;
  - `CompletableFuture<Boolean> EditorWindow.tabsClosing(List<ContentTab> handles, boolean batch)` (package-private):
    the closing guard the Editor registers with `onTabsClosing`;
  - private `EditorTab addView(DocumentSession, TabPlacement, EditorTab sharing)`, `void openView(DocumentSession,
    WindowOwner, TabPlacement)`, `List<EditorTab> views(DocumentSession)` (most recently active first), `void
    forget(DocumentSession)`, `List<DocumentSession> lastViews(List<EditorTab>)`; fields `DocumentSession previous`,
    `Map<EditorTab, Runnable> sessionListeners`;
  - Editor commands `split-file-right` "Split File Right" and `split-file-down` "Split File Down";
    `EditorPlugin.SPLIT_FILE_RIGHT` = `"dev.jasper.editor.split_file_right"`, `SPLIT_FILE_DOWN` =
    `"dev.jasper.editor.split_file_down"`;
  - `EditorViewsTest` and its fixture (`host`, `context`, `window`, `main`, `answers`, `asked`, `recorded`,
    `opened(Path)`, `split(TabPlacement)`, `handle(int)`), which Task 3 extends.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorViewsTest.java`:

```java
package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.open.Locations;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.testsupport.StubDocument;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.TabPlacement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pane flexibility spec 6 in the Editor's window: a file shown in several views, each a pane of its own, with one text,
 * one ● marker and one closing question.
 */
@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class EditorViewsTest {
    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, List.of(), false, 1.0, false, 4, false, 1, false));
    private final List<String> recorded = new ArrayList<>();
    private EditorWindow window;
    private UUID main;

    @BeforeEach void open() {
        Languages.register();
        host = new FakePluginHost(home.resolve("plugins"));
        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of("terminal.open")), Set.of(), Set.of(), c -> { });
        var environment = new EditorEnvironment(context, Runnable::run,
            owner -> question -> {
                asked.add(question);
                return answers.isEmpty() ? question.dismissed() : answers.removeFirst();
            },
            owner -> (message, saves) -> {
                drain();
                return saves.getNow(false);
            },
            settings::get, on -> CompletableFuture.completedFuture(null), false);
        window = new EditorWindow(environment, () -> { }, document -> recorded.add(document.key()), request -> false, new Locations());
        main = host.addTerminalWindow();
    }

    @AfterEach void close() { host.close(); }

    private void drain() { while (host.runBackground() > 0) { } }

    private Path file(String name, String text) throws Exception { return Files.writeString(home.resolve(name), text); }

    private EditorTab opened(Path file) {
        window.openLocal(file);
        drain();
        return window.selected().orElseThrow();
    }

    private EditorTab split(TabPlacement placement) {
        window.splitFile(placement);
        return window.selected().orElseThrow();
    }

    /** The content tab of the {@code n}th view opened, counting from 0. */
    private ContentTab handle(int n) { return host.contentTabs().get(n); }

    private static JMenuItem item(EditorTab tab, String title) {
        for (var component : tab.area().getPopupMenu().getComponents())
            if (component instanceof JMenuItem item && title.equals(item.getText())) return item;
        throw new AssertionError("No " + title + " in the right-click menu");
    }

    @Test void splitFileRightShowsTheSelectedFileInAnotherViewBesideIt() throws Exception {
        Path file = file("a.txt", "alpha\n");
        EditorTab first = opened(file);
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        assertThat(second).isNotSameAs(first);
        assertThat(second.session()).isSameAs(first.session());
        assertThat(window.sessions()).as("one file").hasSize(1);
        assertThat(host.windowTabs(main)).as("one tab: the file's pane, then the new one beside it").singleElement()
            .satisfies(panes -> assertThat(panes).hasSize(2));
        assertThat(second.area().getText()).isEqualTo("alpha\n");
        second.area().setCaretPosition(5);
        second.area().replaceSelection(" beta");
        assertThat(first.area().getText()).as("an edit shows in every view at once").isEqualTo("alpha beta\n");
        assertThat(host.contentTabs()).hasSize(2)
            .allSatisfy(tab -> assertThat(host.contentPresentation(tab).dirty()).as("the marker on every view").isTrue());
        assertThat(recorded).as("Recent Files records the file once").containsExactly(LocalDocument.key(file));
    }

    /** The right-click menu's Split File Down acts on its own view, which it selects, though another tab showed. */
    @Test void splitFileDownFromAViewsMenuOpensBesideThatView() throws Exception {
        opened(file("a.txt", "a\n"));
        EditorTab b = opened(file("b.txt", "b\n"));
        handle(0).select();
        assertThat(host.windowTabs(main)).hasSize(2);
        item(b, "Split File Down").doClick();
        EditorTab view = window.selected().orElseThrow();
        assertThat(view.session()).isSameAs(b.session());
        assertThat(host.windowTabs(main)).as("b's tab holds b and its new view").element(1)
            .satisfies(panes -> assertThat(panes).hasSize(2));
        assertThat(host.windowTabs(main).getFirst()).as("a's tab is left alone").hasSize(1);
        assertThat(item(b, "Split File Right")).isNotNull();
    }

    @Test void saveFromAnyViewSavesTheFileAndClearsEveryMarker() throws Exception {
        Path file = file("save.txt", "one\n");
        EditorTab first = opened(file);
        EditorTab second = split(TabPlacement.SPLIT_DOWN);
        first.area().append("two\n");
        assertThat(host.contentPresentation(handle(1)).dirty()).isTrue();
        handle(1).select();
        window.save();
        drain();
        assertThat(Files.readString(file)).isEqualTo("one\ntwo\n");
        assertThat(host.contentTabs()).allSatisfy(tab -> assertThat(host.contentPresentation(tab).dirty()).isFalse());
        assertThat(second.session().dirty()).isFalse();
    }

    @Test void openInEditorOfAnOpenFileSelectsItsMostRecentlyFocusedView() throws Exception {
        Path file = file("recent.txt", "x\n");
        EditorTab first = opened(file);
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        handle(0).select();
        window.openLocal(file);
        assertThat(window.selected()).as("the view focused last").containsSame(first);
        handle(1).select();
        window.openLocal(file);
        assertThat(window.selected()).containsSame(second);
        assertThat(handle(1).isActive()).isTrue();
        assertThat(host.contentTabs()).as("no new view").hasSize(2);
    }

    @Test void closingAViewAsksNothingAndTheFilesLastViewAsks() throws Exception {
        EditorTab first = opened(file("close.txt", "x\n"));
        split(TabPlacement.SPLIT_RIGHT);
        first.area().append("edit\n");
        assertThat(handle(1).requestClose().join()).as("a view of a file another view shows").isTrue();
        assertThat(asked).as("nothing asked").isEmpty();
        assertThat(window.sessions()).hasSize(1);
        assertThat(first.area().getText()).as("the edit stays in the view left").isEqualTo("x\nedit\n");
        answers.add("Cancel");
        assertThat(handle(0).requestClose().join()).isFalse();
        assertThat(asked).extracting(Question::message).containsExactly("Save changes to close.txt?");
        answers.add("Don't Save");
        assertThat(handle(0).requestClose().join()).isTrue();
        assertThat(window.sessions()).isEmpty();
    }

    /**
     * A window that closes with one view of a file whose other view another window shows asks nothing: the closing
     * guard's batch, which Jasper asks for a window's tabs. Quitting, which closes both, asks once and names the file once.
     */
    @Test void aWindowWithOneViewOfAChangedFileClosesSilentlyAndQuitAsksOnce() throws Exception {
        opened(file("two.txt", "x\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        handle(1).moveToNewWindow();
        assertThat(handle(1).window().id()).isNotEqualTo(handle(0).window().id());
        second.area().append("edit\n");
        assertThat(window.tabsClosing(List.of(handle(1)), true).join()).as("the main window keeps the file's other view").isTrue();
        assertThat(window.tabsClosing(List.of(handle(0)), true).join()).isTrue();
        assertThat(asked).isEmpty();
        answers.add("Cancel");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::message).containsExactly("two.txt has unsaved changes.");
    }

    @Test void saveAsFromOneViewRenamesEveryView() throws Exception {
        EditorTab first = opened(file("old.txt", "x\n"));
        split(TabPlacement.SPLIT_RIGHT);
        Path target = home.resolve("new.txt");
        host.queueFileChoice(List.of(new FileLocation.Local(target)));
        window.saveAs();
        drain();
        assertThat(Files.readString(target)).isEqualTo("x\n");
        var state = dev.jasper.sdk.RestoreState.of(EditorWindow.RESTORE_KIND, EditorWindow.RESTORE_KEY, LocalDocument.key(target));
        assertThat(host.contentTabs()).hasSize(2).allSatisfy(tab -> {
            assertThat(host.contentPresentation(tab).title()).isEqualTo("new.txt");
            assertThat(host.restoreState(tab)).as("each view comes back as a view of the file's new key").contains(state);
        });
        assertThat(first.session().name()).isEqualTo("new.txt");
    }

    /** Cancel in one view of a file that is still loading closes that view; the read goes on for the other. */
    @Test void cancelInOneViewOfALoadingFileLeavesTheOtherItsRead() throws Exception {
        window.openLocal(file("slow.txt", "slow\n"));
        EditorTab first = window.selected().orElseThrow();
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        dev.jasper.editor.testsupport.Kits.click(first.view(), EditorTab.DISMISS);
        drain();
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(second.session().state()).isEqualTo(DocumentSession.State.READY);
        assertThat(second.area().getText()).isEqualTo("slow\n");
    }

    /** A split view starts where its source view is: its caret and selection (Rulings 19). */
    @Test void aSplitViewStartsAtItsSourcesCaretAndSelection() throws Exception {
        EditorTab first = opened(file("place.txt", "one\ntwo\nthree\n"));
        first.area().select(4, 7);
        EditorTab second = split(TabPlacement.SPLIT_DOWN);
        assertThat(second.area().getCaretPosition()).isEqualTo(first.area().getCaretPosition()).isEqualTo(7);
        assertThat(second.area().getSelectedText()).isEqualTo("two");
    }

    /** Closed views leave nothing on their file's session: five views opened and closed, and its listeners are as before. */
    @Test void closedViewsLeaveNoListenerOnTheirFile() throws Exception {
        EditorTab first = opened(file("leak.txt", "x\n"));
        int baseline = listeners(first.session());
        for (int n = 0; n < 5; n++) {
            split(TabPlacement.SPLIT_RIGHT);
            host.contentTabs().getLast().close();
        }
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(listeners(first.session())).isEqualTo(baseline);
    }

    private static int listeners(DocumentSession session) throws ReflectiveOperationException {
        var field = DocumentSession.class.getDeclaredField("listeners");
        field.setAccessible(true);
        return ((List<?>) field.get(session)).size();
    }

    /** A remote file's second view reads nothing, so it opens though the file's connection has gone since the first read. */
    @Test void aRemoteFilesSecondViewReadsNothingThoughItsConnectionIsGone() {
        var remote = new StubDocument("stub:/srv/notes.txt", "notes.txt", "box:/srv/notes.txt", "box");
        window.open(remote);
        drain();
        assertThat(remote.reads).isEqualTo(1);
        remote.failing(new IOException("Not connected to box"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(second.session().state()).isEqualTo(DocumentSession.State.READY);
        assertThat(second.area().getText()).isEqualTo("text\n");
        assertThat(remote.reads).as("no second read").isEqualTo(1);
    }
}
```

The plugin's actions, and the keymap check of the Editor's actions without a default:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit, regex
edit("plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java", [(
"""    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */
""",
"""    /**
     * Pane flexibility spec 6: Split File Right and Split File Down, with no default key, show the window's file in
     * another view, in a new pane beside its own; the Tab menu's item commands list them too.
     */
    @Test void splitFileRightAndDownShowTheWindowsFileInAnotherViewBesideIt() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow();
            assertThat(host.actions()).contains("dev.jasper.editor.split_file_right|Split File Right|true",
                "dev.jasper.editor.split_file_down|Split File Down|true");
            for (String id : List.of("dev.jasper.editor.split_file_right", "dev.jasper.editor.split_file_down"))
                assertThat(host.actionSpec(id)).as(id).hasValueSatisfying(spec -> {
                    assertThat(spec.defaultBinding()).as("no default key").isEmpty();
                    assertThat(spec.keywords()).contains("split", "file");
                });
            assertThat(host.invoke("dev.jasper.editor.split_file_right", main, null)).isTrue();
            assertThat(host.contentTabs()).as("no file, nothing to split").isEmpty();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            consumer.get().services().require(Editor.class).open(Files.writeString(home.resolve("view.txt"), "x\\n"));
            drain(host);
            assertThat(host.invoke("dev.jasper.editor.split_file_right", main, null)).isTrue();
            assertThat(host.invoke("dev.jasper.editor.split_file_down", main, null)).isTrue();
            assertThat(host.windowTabs(main)).as("one tab of three views of the file").singleElement()
                .satisfies(panes -> assertThat(panes).hasSize(3));
            assertThat(host.contentTabs()).hasSize(3)
                .allSatisfy(tab -> assertThat(host.contentPresentation(tab).title()).isEqualTo("view.txt"));
            assertThat(host.contentCommands(host.contentTabs().getFirst())).extracting(dev.jasper.sdk.ui.ContentCommand::title)
                .as("the Tab menu's item commands").contains("Split File Right", "Split File Down");
        }
    }

    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */
""", 1)])
T = "jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java"
# The Editor's actions with no default key: its list, and a sentence at the end of the case's Javadoc.
regex(T, r'("dev\.jasper\.editor\.reload_vimrc")', r'\1,\n            "dev.jasper.editor.split_file_right", "dev.jasper.editor.split_file_down"', 1)
regex(T, r"(\n     \*/\n    @Test void theEditorsNewActionsHaveNoDefaultAndCanBeBound\(\))",
    r"\n     * So are pane flexibility spec 6's Split File Right and Split File Down.\1", 1)
PY
```

The ledger row:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from ledger import add
add("plugins/editor/src/test/java/dev/jasper/editor/ui/EditorViewsTest.java", "dev.jasper.editor", "EditorViewsTest",
    "plugins/editor/src/test/java/dev/jasper/editor/ui/EditorViewsTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 9b Task 2; a file's views in the Editor's window")
PY
```

- [ ] **Step 1b: Plan 9a's Open File Right… case of an open file (on plan 9a's code)**

The case plan 9a wrote for its already-open branch now pins the second view:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import regex
# Plan 9a's case for its already-open branch, its Javadoc and body whole, up to the next case's Javadoc.
regex("plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java",
    r"(?s)    /\*\*(?:(?!\*/).)*\*/\n    @Test void openBesideOfAFileThatIsOpenShowsItsOpenPane\(\) throws Exception \{.*?\n    \}\n\n(?=    /\*\* Pane flexibility spec 3: cancelling)",
    """    /**
     * Pane flexibility spec 3 and 6: Open File Right of a file that is open already shows it in another view, in a new
     * pane beside the focused pane, and records it again, as Open in Editor… of an open file does.
     */
    @Test void openBesideOfAFileThatIsOpenShowsAnotherViewBesideTheFocusedPane() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        Path a = file("a.txt", "a");
        window.openLocal(a, handle);
        drain();
        UUID editor = host.windowTabs(main).get(1).getFirst();
        host.focusTerminalPane(pane);
        host.queueFileChoice(List.of(new FileLocation.Local(a)));
        window.openBeside(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        drain();
        assertThat(host.contentTabs()).as("another view of the file").hasSize(2);
        assertThat(window.sessions()).as("one file").hasSize(1);
        assertThat(host.windowTabs(main)).hasSize(2);
        assertThat(host.windowTabs(main).getFirst()).as("beside the focused shell, in its tab").hasSize(2).startsWith(shell);
        assertThat(host.windowTabs(main).get(1)).containsExactly(editor);
        assertThat(window.selected().orElseThrow().session()).isSameAs(window.sessions().getFirst());
        assertThat(recorded).containsExactly(LocalDocument.key(a), LocalDocument.key(a));
    }

""", 1)
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:compileTestJava`
Expected: FAIL: `EditorWindow.splitFile(TabPlacement)` and `tabsClosing` are not found (`cannot find symbol`). With
Step 3 alone in place, plan 9a's reworked case
`openBesideOfAFileThatIsOpenShowsAnotherViewBesideTheFocusedPane` fails ("another view of the file": one content tab,
not two) until Step 3b.

- [ ] **Step 3: Implement**

`EditorWindow` keeps a file's views, opens another, and asks about a file at its last view:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
W = "plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java"
edit(W, [(
"""        closeRegistration = context.windows().onTabsClosing((handles, batch) -> {
            var closing = hosted.entrySet().stream().filter(e -> handles.contains(e.getValue())).map(java.util.Map.Entry::getKey).toList();
            if (closing.isEmpty()) return CompletableFuture.completedFuture(true);
            if (!batch && closing.size() == 1) return approveTab(closing.getFirst());
            promptOwner = handles.getFirst();
            try { return CompletableFuture.completedFuture(mayClose(closing)); }
            finally { promptOwner = null; }
        });
""",
"""        closeRegistration = context.windows().onTabsClosing(this::tabsClosing);
""", 1), (
"""    /**
     * Opens a document Jasper restores with its windows""",
"""    /**
     * The closing guard Jasper asks before content tabs close: one tab's own close ({@code batch} false), or a window's,
     * a quit's or a restart's batch. A view of a file another view still shows closes without a question (pane
     * flexibility spec 6).
     *
     * @param handles the closing tabs, the Editor's among them
     * @param batch whether a window, a quit or a restart closes them
     * @return whether they may close
     */
    CompletableFuture<Boolean> tabsClosing(List<ContentTab> handles, boolean batch) {
        var closing = hosted.entrySet().stream().filter(e -> handles.contains(e.getValue())).map(java.util.Map.Entry::getKey).toList();
        if (closing.isEmpty()) return CompletableFuture.completedFuture(true);
        if (!batch && closing.size() == 1) return approveTab(closing.getFirst());
        promptOwner = handles.getFirst();
        try { return CompletableFuture.completedFuture(mayClose(closing)); }
        finally { promptOwner = null; }
    }

    /**
     * Opens a document Jasper restores with its windows""", 1), (
"""    // Revert to Saved too: the tab strip's menu, its other place, hides with one tab (workspace plan 8b Rulings 20).
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment", "revert");
""",
"""    // Revert to Saved too: the tab strip's menu, its other place, hides with one tab (workspace plan 8b Rulings 20). Split
    // File Right and Down too (pane flexibility spec 6).
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment", "revert", "split-file-right",
        "split-file-down");
""", 1), (
"""    private final java.util.Map<EditorTab, ContentTab> hosted = new java.util.LinkedHashMap<>();
""",
"""    private final java.util.Map<EditorTab, ContentTab> hosted = new java.util.LinkedHashMap<>();
    /** Every view, the most recently active first (pane flexibility spec 6): a file's first here is the one an open selects. */
    private final List<EditorTab> recent = new ArrayList<>();
    /** Each file's question owner: the tab of its most recently active view, over which its prompts show. */
    private final java.util.Map<DocumentSession, java.util.concurrent.atomic.AtomicReference<WindowOwner>> questionOwners = new java.util.HashMap<>();
    /** What {@link #closed} handed out for a file, completed when its last view closes. */
    private final java.util.Map<DocumentSession, List<CompletableFuture<Void>>> lastViewClosed = new java.util.HashMap<>();
    /** Each view's listener on its file's session, which goes with the view: the session outlives it while others show it. */
    private final java.util.Map<EditorTab, Runnable> sessionListeners = new java.util.HashMap<>();
""", 1), (
"""    /** Each tab's Vim buffer number (Vim spec 6.2), kept while it is open. */
    private final java.util.Map<DocumentSession, Integer> numbers = new java.util.HashMap<>();
    private int nextNumber = 1;
    /** The tab selected before the current one: Vim's alternate buffer. */
    private EditorTab previous;
""",
"""    /** Each file's Vim buffer number (Vim spec 6.2), kept while it is open, however many views show it. */
    private final java.util.Map<DocumentSession, Integer> numbers = new java.util.HashMap<>();
    private int nextNumber = 1;
    /** The file selected before the current one, another than it: Vim's alternate buffer. */
    private DocumentSession previous;
""", 1), (
"""            new Command("revert", "Revert to Saved", Optional.empty(), false, this::revert),
""",
"""            new Command("revert", "Revert to Saved", Optional.empty(), false, this::revert),
            new Command("split-file-right", "Split File Right", Optional.empty(), false, () -> splitFile(dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT)),
            new Command("split-file-down", "Split File Down", Optional.empty(), false, () -> splitFile(dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN)),
""", 1), (
"""    /** @param document opened in a new tab, or its open tab selected; the window comes to the front */
    public void open(EditorDocument document) { open(document, null); }

    /** Opens a file in the window the request came from; null chooses the main window. */
    public void open(EditorDocument document, WindowOwner owner) {""",
"""    /** @param document opened in a new tab, or its open tab selected, of several the one focused last; the window comes to the front */
    public void open(EditorDocument document) { open(document, null); }

    /**
     * Opens a file in the window the request came from; null chooses the main window. A file already open has its view
     * selected, of several the one focused last (pane flexibility spec 6).
     */
    public void open(EditorDocument document, WindowOwner owner) {""", 1), (
"""        open(document, hosted.get(originTab));
        var session = model.find(document.key()).orElseThrow();
        return hosted.keySet().stream().filter(tab -> tab.session() == session).findFirst().orElseThrow();
    }
""",
"""        open(document, hosted.get(originTab));
        return views(model.find(document.key()).orElseThrow()).getFirst();
    }
""", 1), (
"""    private WindowOwner owner(DocumentSession session) { return hosted.entrySet().stream().filter(e -> e.getKey().session() == session).map(e -> (WindowOwner)e.getValue()).findFirst().orElseGet(this::owner); }
""",
"""    private WindowOwner owner(DocumentSession session) { return views(session).stream().findFirst().map(hosted::get).map(WindowOwner.class::cast).orElseGet(this::owner); }
""", 1), (
"""    /** Completes once the session's tab has closed: when the user closes it, its window closes or the Editor stops. */
    public CompletableFuture<Void> closed(DocumentSession session) {
        var done = new CompletableFuture<Void>();
        var handle = hosted.entrySet().stream().filter(entry -> entry.getKey().session() == session)
            .map(Map.Entry::getValue).findFirst();
        if (handle.isEmpty() || handle.get().isClosed()) done.complete(null);
        else handle.get().onClosed(() -> done.complete(null));
        return done;
    }
""",
"""    /**
     * Completes once the session's last view has closed: when the user closes it, its window closes or the Editor stops
     * (pane flexibility spec 6).
     */
    public CompletableFuture<Void> closed(DocumentSession session) {
        var done = new CompletableFuture<Void>();
        if (views(session).isEmpty()) done.complete(null);
        else lastViewClosed.computeIfAbsent(session, waiting -> new ArrayList<>()).add(done);
        return done;
    }
""", 1), (
"""        hosted.keySet().stream().filter(tab -> tab.session() == session).findFirst().ifPresent(tab -> {
            var area = tab.area();""",
"""        views(session).stream().findFirst().ifPresent(tab -> {
            var area = tab.area();""", 1), (
"""        if (discarding.remove(tab)) return CompletableFuture.completedFuture(true);
        DocumentSession session = tab.session();
""",
"""        if (discarding.remove(tab)) return CompletableFuture.completedFuture(true);
        DocumentSession session = tab.session();
        // Pane flexibility spec 6: the file's other views keep its changes, so only its last view asks.
        if (views(session).size() > 1) return CompletableFuture.completedFuture(true);
""", 1), (
"""     * would otherwise wait on saves that cannot finish until the outer prompt returns.
     */
    boolean mayClose() { return mayClose(editorTabs()); }
    private boolean mayClose(List<EditorTab> closing) {""",
"""     * would otherwise wait on saves that cannot finish until the outer prompt returns. It asks about a file only when
     * every view of it closes (pane flexibility spec 6), and then once.
     */
    boolean mayClose() { return mayClose(editorTabs()); }
    private boolean mayClose(List<EditorTab> closing) {""", 1), (
"""                List<DocumentSession> running = closing.stream().map(EditorTab::session).filter(model.sessions()::contains).filter(DocumentSession::saving).toList();""",
"""                List<DocumentSession> running = lastViews(closing).stream().filter(DocumentSession::saving).toList();""", 1), (
"""                List<DocumentSession> dirty = closing.stream().map(EditorTab::session).filter(model.sessions()::contains).filter(DocumentSession::dirty).toList();""",
"""                List<DocumentSession> dirty = lastViews(closing).stream().filter(DocumentSession::dirty).toList();""", 1), (
"""    private void shutDown() {
        ticker.stop();""",
"""    /** The open files every view of which is among {@code closing}, each once: only they lose their changes. */
    private List<DocumentSession> lastViews(List<EditorTab> closing) {
        return closing.stream().map(EditorTab::session).distinct().filter(model.sessions()::contains)
            .filter(session -> closing.containsAll(views(session))).toList();
    }

    private void shutDown() {
        ticker.stop();""", 1), (
"""    private List<VimHost.Buffer> buffers(dev.jasper.editor.vim.VimEditor editor) {
        var current = tabOf(editor).orElse(null);
        return editorTabs().stream().map(tab -> {
            DocumentSession session = tab.session();
            Optional<Path> file = localFile(session).or(() -> Optional.ofNullable(newFiles.get(session)));
            return new VimHost.Buffer(numbers.get(session), session.location(), file, tab == current, tab == previous && tab != current,
                session.dirty(), session.untitled() && !newFiles.containsKey(session), tab.area().getCaretLineNumber() + 1);
        }).toList();
    }

    private Optional<EditorTab> tabNumbered(int number) {
        return editorTabs().stream().filter(tab -> numbers.get(tab.session()) == number).findFirst();
    }
""",
"""    /**
     * The open files as Vim's buffers, one each however many views show it, as Vim shows one buffer in many windows
     * (pane flexibility spec 6): the current one is {@code editor}'s, and its line is the caret's in {@code editor}'s view,
     * another's in its most recently active view.
     */
    private List<VimHost.Buffer> buffers(dev.jasper.editor.vim.VimEditor editor) {
        var current = tabOf(editor).orElse(null);
        DocumentSession here = current == null ? null : current.session();
        return model.sessions().stream().filter(session -> !views(session).isEmpty()).map(session -> {
            Optional<Path> file = localFile(session).or(() -> Optional.ofNullable(newFiles.get(session)));
            EditorTab view = session == here ? current : views(session).getFirst();
            return new VimHost.Buffer(numbers.get(session), session.location(), file, session == here, session == previous && session != here,
                session.dirty(), session.untitled() && !newFiles.containsKey(session), view.area().getCaretLineNumber() + 1);
        }).toList();
    }

    /** @return the most recently active view of the file with buffer {@code number} */
    private Optional<EditorTab> tabNumbered(int number) {
        return model.sessions().stream().filter(session -> numbers.get(session) == number).findFirst()
            .flatMap(session -> views(session).stream().findFirst());
    }
""", 1), (
"""    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef,
                     dev.jasper.sdk.ui.TabPlacement placement) {
        EditorSettings settings = environment.settings().get();
        session.setWrap(settings.softWrap());
        var tab = new EditorTab(context.kit(), session, this::closeTab, environment.timers());
""",
"""    /** A newly open file and its first view, where {@code placement} puts it; {@code ownerRef} is its question owner. */
    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef,
                     dev.jasper.sdk.ui.TabPlacement placement) {
        session.setWrap(environment.settings().get().softWrap());
        model.add(session);
        numbers.put(session, nextNumber++);
        questionOwners.put(session, ownerRef);
        addView(session, placement, null);
    }

    /**
     * A view of an open file where {@code placement} puts it: its first, or with {@code sharing} another, which shows the
     * same text and undo history (pane flexibility spec 6).
     *
     * @param sharing an open view of the file, or null for its first
     * @return the new view
     */
    private EditorTab addView(DocumentSession session, dev.jasper.sdk.ui.TabPlacement placement, EditorTab sharing) {
        EditorSettings settings = environment.settings().get();
        var tab = new EditorTab(context.kit(), session, this::closeTab, environment.timers(), sharing);
""", 1), (
"""        model.add(session);
        numbers.put(session, nextNumber++);
        var spec = new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name());
        var handle = context.windows().openTab(restoreState(session).map(spec::withRestore).orElse(spec), origin, tab.view(), placement);
        hosted.put(tab, handle); ownerRef.set(handle);
""",
"""        var spec = new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name());
        var handle = context.windows().openTab(restoreState(session).map(spec::withRestore).orElse(spec), origin, tab.view(), placement);
        hosted.put(tab, handle); recent.addFirst(tab); questionOwners.get(session).set(handle);
""", 1), (
"""        session.addListener(() -> {
            if (!hosted.containsKey(tab)) return;
            refreshCommands(tab);
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        });
""",
"""        Runnable refreshing = () -> {
            if (!hosted.containsKey(tab)) return;
            refreshCommands(tab);
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        };
        session.addListener(refreshing);
        sessionListeners.put(tab, refreshing);
""", 1), (
"""        activate(tab);
        readVimrcOnce();
        refresh();
    }

    private void refreshCommands(EditorTab tab) {""",
"""        activate(tab);
        readVimrcOnce();
        refresh();
        return tab;
    }

    /**
     * Split File Right and Split File Down (pane flexibility spec 6): the selected file in another view, in a new pane
     * beside its own, which it selects first, so the new pane opens there though another pane had the focus. The new view
     * starts at its source's caret, selection and scroll position.
     *
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     */
    public void splitFile(dev.jasper.sdk.ui.TabPlacement placement) {
        selected().ifPresent(tab -> {
            ContentTab handle = hosted.get(tab);
            if (handle == null || handle.isClosed()) return;
            handle.select();
            origin = handle.window();
            EditorTab view = addView(tab.session(), placement, tab);
            // Rulings 19: the new view starts where its source is, as VS Code's Split Editor and IntelliJ's Split Vertically do.
            var caret = tab.area().getCaret();
            view.area().getCaret().setDot(caret.getMark());
            view.area().getCaret().moveDot(caret.getDot());
            java.awt.Point shown = tab.scroll().getViewport().getViewPosition();
            javax.swing.SwingUtilities.invokeLater(() -> { if (!view.closed()) view.scroll().getViewport().setViewPosition(shown); });
        });
    }

    /**
     * Split File Right or Down as an action run in {@code window} does it, from the palette or a key: as
     * {@link #splitFile(dev.jasper.sdk.ui.TabPlacement)}, for the file {@link #revert(WindowHandle)} would revert; nothing
     * while the window shows no file.
     *
     * @param window the window the action ran in
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     */
    public void splitFile(WindowHandle window, dev.jasper.sdk.ui.TabPlacement placement) {
        fileIn(window).ifPresent(tab -> { activate(tab); splitFile(placement); });
    }

    /**
     * Open File Right or Down of a file that is already open (pane flexibility spec 6): another view of it, in a new pane
     * beside the focused pane of {@code owner}'s window, as Open in Editor… of an open file reports it again.
     *
     * @param session the open file
     * @param owner the window or tab it was asked in
     * @param placement {@code SPLIT_RIGHT} or {@code SPLIT_DOWN}
     */
    private void openView(DocumentSession session, WindowOwner owner, dev.jasper.sdk.ui.TabPlacement placement) {
        origin = owner;
        addView(session, placement, views(session).stream().findFirst().orElse(null));
        if (session.state() == DocumentSession.State.READY) session.document().ifPresent(opened);
    }

    /** A file's open views, the most recently active first (pane flexibility spec 6). */
    private List<EditorTab> views(DocumentSession session) {
        return recent.stream().filter(tab -> tab.session() == session).toList();
    }

    private void refreshCommands(EditorTab tab) {""", 1), (
"""    private void remove(EditorTab tab) {
        if (hosted.remove(tab) == null) return;
        markdownCommandsReady.remove(tab);
        tab.dispose(); tab.session().cancelLoad(); model.remove(tab.session());
        numbers.remove(tab.session());
        newFiles.remove(tab.session());
        unrestorable.remove(tab.session());
        discarding.remove(tab);
        if (previous == tab) previous = null;
        if (selectedTab == tab) selectedTab = hosted.keySet().stream().findFirst().orElse(null);
        if (selectedTab != null) activate(selectedTab);
        refresh();
    }
    private void select(DocumentSession session) {
        for (var entry : hosted.entrySet()) if (entry.getKey().session() == session) { activate(entry.getKey()); entry.getValue().select(); return; }
    }
    private void activate(EditorTab tab) {
        if (!hosted.containsKey(tab)) return;
        if (selectedTab != tab && selectedTab != null) {
            selectedTab.find(null);
            previous = selectedTab;
        }
        selectedTab = tab; tab.find(findBar); refreshStatus();
    }
""",
"""    private void remove(EditorTab tab) {
        if (hosted.remove(tab) == null) return;
        markdownCommandsReady.remove(tab);
        recent.remove(tab);
        discarding.remove(tab);
        DocumentSession session = tab.session();
        Runnable refreshing = sessionListeners.remove(tab);
        if (refreshing != null) session.removeListener(refreshing);
        tab.dispose();
        if (views(session).isEmpty()) forget(session);
        else questionOwners.get(session).set(hosted.get(views(session).getFirst()));
        if (selectedTab == tab) selectedTab = hosted.keySet().stream().findFirst().orElse(null);
        if (selectedTab != null) activate(selectedTab);
        refresh();
    }

    /** A file's last view has closed (pane flexibility spec 6): the file is no longer open. */
    private void forget(DocumentSession session) {
        session.cancelLoad(); model.remove(session);
        numbers.remove(session);
        newFiles.remove(session);
        unrestorable.remove(session);
        questionOwners.remove(session);
        if (previous == session) previous = null;
        var waiting = lastViewClosed.remove(session);
        if (waiting != null) waiting.forEach(done -> done.complete(null));
    }

    /** Selects the file's most recently active view (pane flexibility spec 6). */
    private void select(DocumentSession session) {
        views(session).stream().findFirst().ifPresent(tab -> { activate(tab); hosted.get(tab).select(); });
    }
    private void activate(EditorTab tab) {
        if (!hosted.containsKey(tab)) return;
        if (selectedTab != tab && selectedTab != null) {
            selectedTab.find(null);
            if (selectedTab.session() != tab.session()) previous = selectedTab.session();
        }
        recent.remove(tab);
        recent.addFirst(tab);
        questionOwners.get(tab.session()).set(hosted.get(tab));
        selectedTab = tab; tab.find(findBar); refreshStatus();
    }
""", 1)])
PY
```

The actions, the version and the Editor API's words (Rulings 15, 16):

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit, regex
P = "plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java"
# The constants line plan 9a ends with OPEN_IN_PANE.
regex(P, r'(OPEN_IN_PANE = "[^"]+");',
    r'\1,\n        SPLIT_FILE_RIGHT = "dev.jasper.editor.split_file_right", SPLIT_FILE_DOWN = "dev.jasper.editor.split_file_down";', 1)
edit(P, [(
"""        context.actions().register(ActionSpec.of(RELOAD_VIMRC, "Reload vimrc").withKeywords(List.of("editor", "vim", "vimrc", "reload")),
            invoked -> window().reloadVimrc(invoked.window()));
""",
"""        context.actions().register(ActionSpec.of(RELOAD_VIMRC, "Reload vimrc").withKeywords(List.of("editor", "vim", "vimrc", "reload")),
            invoked -> window().reloadVimrc(invoked.window()));
        // Pane flexibility spec 6: the window's file in another view, in a new pane beside its own; no default key.
        context.actions().register(ActionSpec.of(SPLIT_FILE_RIGHT, "Split File Right")
                .withKeywords(List.of("editor", "split", "file", "right", "view", "pane")),
            invoked -> window().splitFile(invoked.window(), dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT));
        context.actions().register(ActionSpec.of(SPLIT_FILE_DOWN, "Split File Down")
                .withKeywords(List.of("editor", "split", "file", "down", "view", "pane")),
            invoked -> window().splitFile(invoked.window(), dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN));
""", 1)])
# The next minor above plan 9a's 0.14.0.
edit("plugins/editor/src/main/resources/plugin.toml", [('version = "0.14.0"\n', 'version = "0.15.0"\n', 1)])
A = "plugins/editor/src/main/java/dev/jasper/editor/api/"
edit(A + "Editor.java", [(
"""     * Opens a local file in a new tab of the main window, or selects the tab already showing it, and brings that tab's
     * window to the front.""",
"""     * Opens a local file in a new tab of the main window, or selects the tab already showing it (of several, the one
     * focused last), and brings that tab's window to the front.""", 1), (
"""     * Opens a document another plugin provides in a new tab of the main window, or selects the tab whose document has the
     * same key, and brings that tab's window to the front.""",
"""     * Opens a document another plugin provides in a new tab of the main window, or selects the tab whose document has the
     * same key (of several, the one focused last), and brings that tab's window to the front.""", 1), (
"""     * null; a file already open is selected in its tab, wherever that is. The interface default ignores {@code owner}.""",
"""     * null; a file already open is selected in its tab, wherever that is (of several, the one focused last). The
     * interface default ignores {@code owner}.""", 1), (
"""     * null; a document whose key is already open is selected in its tab, wherever that is. The interface default ignores
     * {@code owner}.""",
"""     * null; a document whose key is already open is selected in its tab, wherever that is (of several, the one focused
     * last). The interface default ignores {@code owner}.""", 1)])
edit(A + "EditorDocument.java", [(
"""     * The document's stable identity. Opening a document whose key is already open selects that tab; Recent Files
     * records the key, and {@link Editor#reopener} resolves it by its prefix. Start it with a prefix of the plugin's""",
"""     * The document's stable identity. Opening a document whose key is already open selects its tab (of several, the one
     * focused last), and every tab that shows it shares one read, one text and one save. Recent Files records the key,
     * and {@link Editor#reopener} resolves it by its prefix. Start it with a prefix of the plugin's""", 1)])
PY
```

- [ ] **Step 3b: Open File Right… of an open file (on plan 9a's code)**

Plan 9a's `openDocumentBeside` shows an open document's pane through `selectOpen`; with this plan it opens another view
of it beside the focused pane. `selectOpen` stays, for `open`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import regex
W = "plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java"
# openDocumentBeside's Javadoc sentence on an open document, however it is wrapped and whichever ruling it cites.
regex(W, r"A document that is(?:\s|\*)+open(?:\s|\*)+already(?:\s|\*)+shows(?:\s|\*)+its(?:\s|\*)+open(?:\s|\*)+pane[^.]*\.",
    "A document that is open already shows there in another\n     * view of it (pane flexibility spec 6), reported again as {@link #open} reports it.", 1)
# Its already-open branch, under plan 9a's comment.
regex(W, r"( *)// Until plan 9b's second view:[^\n]*\n\s*selectOpen\(([^;]*)\);",
    r"\1// Pane flexibility spec 6: another view of the file, beside the focused pane.\n\1openView(\2, owner, placement);", 1)
PY
```

Regenerate the SDK reference, which carries the Editor API's Javadoc:

```bash
./gradlew sdkReference
git diff --stat docs/sdk-reference.html
```

Expected: `docs/sdk-reference.html` changes in the five Javadoc sentences of `Editor` and `EditorDocument`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --continue`
Expected: PASS: `EditorViewsTest` (11), the new `EditorPluginTest` case, plan 9a's reworked
`openBesideOfAFileThatIsOpenShowsAnotherViewBesideTheFocusedPane` and every other Editor case.
Then `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.TabShortcutCollisionTest'` and `./gradlew
:jasper-sdk:check :jasper-plugin-editor:check verifyPluginArchitecture`: PASS (`verifySdkReference` reads the
regenerated page).

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor jasper-app/src/test docs/sdk-reference.html docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Editor: several views of one file in the window

Split File Right and Split File Down (the text's right-click menu,
the tab's item commands and the palette, with no default key) show the
selected file in another view beside its own, and Open File Right… of
an open file shows one beside the focused pane. The window lists a
file's views most recently active first: an open of an open file
selects the first, its prompts show over it, and only the file's last
view asks about unsaved changes, a window close or a quit asking once
per file whose every view closes. Vim's buffers are files. Editor
0.15.0; the Editor API says that its plugins see one document per key.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Vim: one buffer, many windows

The brief's "do what Vim does" for a file's views (Rulings 12, 13): a file is one buffer, numbered once, and its views
are the buffer's windows. `:q` closes the current view and refuses only a changed file's last view; `:qa` closes every
view; an uppercase mark selects the view it was set in. The buffer's marks (`a`-`z`, the special marks, the last Visual
area and the change list) are the file's, shared by its views' editors; the jumplist stays each view's.

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java` (imports, `Buffer` `:70-84`, `Documents`
  `:86-110`), `ExFiles.java` (`writeQuit` `:37-42`, `exit` `:49-52`, `quit` `:120-125`), `Ex.java` (`ZQ` `:185`,
  `quit` `:114`), `MarkCommands.java` (`setmark` `:42`, `:62`, `elsewhere` `:100-117`), `Numbers.java` (`:142`),
  `ReplaceWithRegister.java` (`:64`), `Visual.java` (`remember` `:95-96`, `reselect` `:128-130`)
- Replace: `plugins/editor/src/main/java/dev/jasper/editor/vim/Marks.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (`documents()`, Task 2's `buffers`)
- Modify: `docs/editor-vim-reference.md` (generated)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/ExFilesTest.java`, `CompletionTest.java`;
  `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorViewsTest.java` (Task 2's), `TextViewsTest.java` (Task 1b's)

**Interfaces:**
- Consumes: Task 2's `views(DocumentSession)`, `tabNumbered`, `activate`, `hosted`, `discarding`, `closeTab`.
- Produces:
  - `VimHost.Buffer(int number, String name, Optional<Path> file, boolean current, boolean alternate, boolean modified,
    boolean untitled, int line, int windows)`;
  - `VimHost.Documents(buffers, save, saveAs, BiConsumer<Integer, Boolean> close, BiConsumer<VimEditor, Boolean> quit,
    IntConsumer select, Consumer<VimEditor> show, reload, open, read)`: `close` closes every view of a buffer, `quit`
    the view `editor` is in, `show` selects the view `editor` is in;
  - private `void EditorWindow.close(EditorTab tab, boolean discard)`;
  - `Marks`: one `Marks.Buffer` per document, held as a document property, shared by the `Marks` of every editor of
    it; `Mode visualMode()`, `void visualMode(Mode)`, `int visualCurswant()`, `void visualCurswant(int)` in place of
    the fields `visualMode` and `visualCurswant`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
T = "plugins/editor/src/test/java/dev/jasper/editor/vim/"
edit(T + "CompletionTest.java", [(
"""            editor -> List.of(new Buffer(1, file.toString(), Optional.of(file), true, false, false, false, 1),
                new Buffer(2, "/elsewhere/notes todo.md", Optional.of(Path.of("/elsewhere/notes todo.md")), false, false, false, false, 1),
                new Buffer(3, "Untitled 1", Optional.empty(), false, false, false, true, 1)),
            number -> CompletableFuture.completedFuture(true), (number, target) -> CompletableFuture.completedFuture(true),
            (number, discard) -> { }, number -> { }, number -> { }, (editor, target) -> { }, path -> Optional.empty());""",
"""            editor -> List.of(new Buffer(1, file.toString(), Optional.of(file), true, false, false, false, 1, 1),
                new Buffer(2, "/elsewhere/notes todo.md", Optional.of(Path.of("/elsewhere/notes todo.md")), false, false, false, false, 1, 1),
                new Buffer(3, "Untitled 1", Optional.empty(), false, false, false, true, 1, 1)),
            number -> CompletableFuture.completedFuture(true), (number, target) -> CompletableFuture.completedFuture(true),
            (number, discard) -> { }, (editor, discard) -> { }, number -> { }, editor -> { }, number -> { }, (editor, target) -> { },
            path -> Optional.empty());""", 1)])
edit(T + "ExFilesTest.java", [(
"""        boolean saves = true;
        int current = 1;
""",
"""        boolean saves = true;
        int current = 1;
        /** How many panes show the editor's own buffer. */
        int windows = 1;
""", 1), (
"""                (number, discard) -> calls.add("close " + number + (discard ? " discard" : "")),
                number -> calls.add("select " + number),
""",
"""                (number, discard) -> calls.add("close " + number + (discard ? " discard" : "")),
                (editor, discard) -> calls.add("quit" + (discard ? " discard" : "")),
                number -> calls.add("select " + number),
                editor -> calls.add("show"),
""", 1), (
"""            all.add(new Buffer(1, name, file, current == 1, false, editor.documentModified(), untitled, editor.cursorLine()));
            for (Buffer other : others) all.add(new Buffer(other.number(), other.name(), other.file(), current == other.number(),
                other.alternate(), other.modified(), other.untitled(), other.line()));""",
"""            all.add(new Buffer(1, name, file, current == 1, false, editor.documentModified(), untitled, editor.cursorLine(), windows));
            for (Buffer other : others) all.add(new Buffer(other.number(), other.name(), other.file(), current == other.number(),
                other.alternate(), other.modified(), other.untitled(), other.line(), other.windows()));""", 1), (
"""        tabs.others.add(new Buffer(2, folder.resolve("two.txt").toString(), Optional.of(folder.resolve("two.txt")), false, true, false, false, 4));
        tabs.others.add(new Buffer(3, "Untitled 1", Optional.empty(), false, false, true, true, 1));""",
"""        tabs.others.add(new Buffer(2, folder.resolve("two.txt").toString(), Optional.of(folder.resolve("two.txt")), false, true, false, false, 4, 1));
        tabs.others.add(new Buffer(3, "Untitled 1", Optional.empty(), false, false, true, true, 1, 1));""", 1), (
"""    @Test void quitClosesTheTabUnlessItHasChanges() throws Exception {
        named("one.txt");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\\n");
            modified = true;
            type(editor, ":q<CR>");
            assertThat(editor.message()).isEqualTo("E37: No write since last change (add ! to override)");
            assertThat(tabs.calls).isEmpty();
            type(editor, ":q!<CR>");
            assertThat(tabs.calls).containsExactly("close 1 discard");
            modified = false;
            type(editor, ":q<CR>");
            assertThat(tabs.calls).last().isEqualTo("close 1");""",
"""    @Test void quitClosesThePaneUnlessItIsTheLastOfAChangedFile() throws Exception {
        named("one.txt");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\\n");
            modified = true;
            type(editor, ":q<CR>");
            assertThat(editor.message()).isEqualTo("E37: No write since last change (add ! to override)");
            assertThat(tabs.calls).isEmpty();
            type(editor, ":q!<CR>");
            assertThat(tabs.calls).containsExactly("quit discard");
            tabs.windows = 2;
            tabs.calls.clear();
            type(editor, ":q<CR>");
            assertThat(tabs.calls).as("another pane shows the file, which keeps its changes there").containsExactly("quit");
            tabs.windows = 1;
            modified = false;
            type(editor, ":q<CR>");
            assertThat(tabs.calls).last().isEqualTo("quit");""", 1), (
"""            type(editor, "ZQ");
            assertThat(tabs.calls).containsExactly("close 1 discard");
        });
    }
""",
"""            type(editor, "ZQ");
            assertThat(tabs.calls).containsExactly("quit discard");
        });
    }
""", 1), (
"""            type(editor, ":wq<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "close 1");
            tabs.calls.clear();
            type(editor, ":x<CR>");
            assertThat(tabs.calls).as("no changes: no save").containsExactly("close 1");
            tabs.calls.clear();
            modified = true;
            type(editor, "ZZ");
            assertThat(tabs.calls).containsExactly("save 1", "close 1");""",
"""            type(editor, ":wq<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "quit");
            tabs.calls.clear();
            type(editor, ":x<CR>");
            assertThat(tabs.calls).as("no changes: no save").containsExactly("quit");
            tabs.calls.clear();
            modified = true;
            type(editor, "ZZ");
            assertThat(tabs.calls).containsExactly("save 1", "quit");""", 1), (
"""            type(editor, ":exi<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "close 1");""",
"""            type(editor, ":exi<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "quit");""", 1)])
edit("plugins/editor/src/test/java/dev/jasper/editor/ui/TextViewsTest.java", [(
"""    /** Each view has its own Markdown mode: the preview in one and the source in the other, each switched on its own. */""",
"""    private static void type(EditorTab view, String keys) {
        for (KeyInput key : KeyInput.parse(keys)) view.vimEditor().orElseThrow().feed(key);
    }

    /**
     * Vim's buffer marks belong to the file, as Vim keeps them per buffer: a mark set in one view is the other's too, and
     * {@code '.} and the change list follow an edit made in either view; the jumplist stays each view's own.
     */
    @Test void lowercaseMarksAndTheLastChangeBelongToTheFile() throws Exception {
        var one = first(Files.writeString(folder.resolve("marks.txt"), "one\\ntwo\\nthree\\nfour\\n"));
        var two = second(one);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(one, "jma");
        type(two, "gg'a");
        assertThat(two.area().getCaretLineNumber()).as("the other view's mark a").isEqualTo(1);
        type(one, "jjx");
        type(two, "gg'.");
        assertThat(two.area().getCaretLineNumber()).as("the last change, made in the other view").isEqualTo(3);
        type(two, "g;");
        assertThat(two.area().getCaretLineNumber()).as("the change list").isEqualTo(3);
        type(one, "gg");
        type(one, "dd");
        type(two, "gg'a");
        assertThat(two.area().getCaretLineNumber()).as("the mark moved with the other view's deletion").isZero();
        assertThat(two.vimEditor().orElseThrow().message()).isEmpty();
    }

    /** Each view has its own Markdown mode: the preview in one and the source in the other, each switched on its own. */""", 1)])
PY
```

The window's case, in `EditorViewsTest`:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
edit("plugins/editor/src/test/java/dev/jasper/editor/ui/EditorViewsTest.java", [(
"""    /** A remote file's second view reads nothing, so it opens though the file's connection has gone since the first read. */""",
"""    private static void vimKeys(EditorTab tab, String keys) throws Exception {
        var vim = tab.vimEditor().orElseThrow();
        dev.jasper.editor.testsupport.Kits.edt(() -> { for (var key : dev.jasper.editor.vim.KeyInput.parse(keys)) vim.feed(key); });
    }

    /**
     * Vim sees one buffer per file, however many views show it, as Vim shows one buffer in many windows: :ls lists it
     * once, an uppercase mark goes back to the view it was set in, :q in a view of a changed file that another view shows
     * closes its pane, and the file's last view needs !.
     */
    @Test void vimSeesOneBufferPerFileAndQuitClosesOnlyItsPane() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, List.of(), false, 1.0, false, 4, false, 1, true));
        EditorTab first = opened(file("one.txt", "one\\n"));
        EditorTab second = split(TabPlacement.SPLIT_RIGHT);
        EditorTab other = opened(file("two.txt", "two\\n"));
        vimKeys(second, ":ls<CR>");
        assertThat(second.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .as("one buffer per file, the current one the view's").satisfiesExactly(
                line -> assertThat(line).startsWith("  1 %a"), line -> assertThat(line).startsWith("  2 "));
        vimKeys(second, "<CR>");
        vimKeys(first, "mA");
        vimKeys(other, "'A");
        assertThat(window.selected()).as("the view the mark was set in, though another view of its file was focused later")
            .containsSame(first);
        ContentTab firstPane = handle(0), secondPane = handle(1);
        first.area().append("edit\\n");
        vimKeys(second, ":q<CR>");
        assertThat(secondPane.isClosed()).as("its pane closes; the other view keeps the changes").isTrue();
        assertThat(asked).isEmpty();
        assertThat(window.sessions()).hasSize(2);
        assertThat(first.session().dirty()).isTrue();
        vimKeys(first, ":q<CR>");
        assertThat(first.vimEditor().orElseThrow().message()).isEqualTo("E37: No write since last change (add ! to override)");
        assertThat(firstPane.isClosed()).isFalse();
    }

    /** A remote file's second view reads nothing, so it opens though the file's connection has gone since the first read. */""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:compileTestJava`
Expected: FAIL: the constructors of the records `Buffer` (nine arguments) and `Documents` (ten) cannot be applied to the
given types. With the records in place and the old `Marks`, `TextViewsTest.lowercaseMarksAndTheLastChangeBelongToTheFile`
fails ("the other view's mark a": each editor keeps its own).

- [ ] **Step 3: Implement**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Marks.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * One editor's marks (spec 3.6): {@code a}–{@code z}, the special marks, the Visual area for {@code gv}, the
 * jumplist and the change list. They follow the text as Vim's mark_adjust and mark_col_adjust move them (mark.c):
 * by line, not by character. Adding or removing lines moves the marks below; deleting a line deletes the
 * lowercase, {@code '.} and {@code '^} marks on it, while the others (Vim's one_adjust_nodel) move to the first
 * line after the deletion. Joining and splitting lines move the marks on the joined or split part. Edits inside a
 * line leave columns alone, as in Vim.
 *
 * <p>As in Vim, the named marks, the Visual area and the change list belong to the buffer: every editor of one
 * document, in each of its views, shares them (pane flexibility spec 6), in one store the document holds, which follows
 * every edit to the document, whichever view made it. The jumplist, the previous-context mark and the place in the
 * change list are each editor's own, as they are a Vim window's.
 *
 * <p>The table keeps the positions of the text's line breaks so that a removal, which a document event reports only
 * after the text is gone, can still be read as lines and columns.
 */
final class Marks {
    /** Marks a line deletion removes (one_adjust) rather than moves (one_adjust_nodel). */
    private static final String DELETABLE = "abcdefghijklmnopqrstuvwxyz.^\"";
    /** The document property under which a document holds its {@link Buffer}. */
    private static final Object BUFFER = new Object();

    /** A document's marks, shared by the editors of its views (Vim's buffer marks). */
    private static final class Buffer {
        final Document document;
        final List<Marks> editors = new ArrayList<>();
        final Map<Character, Pos> marks = new HashMap<>();
        final List<Pos> changes = new ArrayList<>();
        final List<Pos> extra = new ArrayList<>();
        /** The lines {@code :global} marked (Vim's ml_setmarked): they move with the text and go when deleted. */
        final List<Pos> marked = new ArrayList<>();
        final DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { inserted(event.getOffset(), event.getLength()); }
            @Override public void removeUpdate(DocumentEvent event) { removed(event.getOffset(), event.getLength()); }
            @Override public void changedUpdate(DocumentEvent event) { }
        };
        /** Offsets of the line breaks, ascending. */
        int[] breaks = new int[0];
        int breakCount;
        int documentLengthBefore;
        /** The mode of the last Visual area, for {@code gv}. */
        Mode visualMode;
        /** The remembered column of the last Visual area. */
        int visualCurswant = -1;
        /** Whether the next change starts a new change-list entry (Vim's b_new_change, set when an undo step begins). */
        boolean newChange = true;
        /** Set while a join removes a line break, so a blank joined line's marks move rather than go (do_join). */
        boolean joining;

        Buffer(Document document) {
            this.document = document;
            rebuild();
            document.addDocumentListener(listener);
        }

        /**
         * changed_common: the {@code '.} mark moves to the change, and the change list gains an entry for the first change
         * of an undo step unless it is on the same line as the last entry and within 79 columns of it. Every editor's
         * place in the list goes to its end.
         */
        void changed(int line, int col) {
            Pos at = new Pos(line, col);
            marks.put('.', at.copy());
            if (!newChange && !changes.isEmpty()) return;
            boolean add;
            if (changes.isEmpty()) add = true;
            else {
                Pos last = changes.getLast();
                add = last.line != line || last.col + 79 < col || col + 79 < last.col;
            }
            if (add) {
                newChange = false;
                if (changes.size() == LIST_SIZE) changes.removeFirst();
                changes.add(at.copy());
            } else changes.set(changes.size() - 1, at.copy());
            for (Marks editor : editors) editor.changeIndex = changes.size();
        }

        void inserted(int offset, int length) {
            String text;
            try { text = document.getText(offset, length); }
            catch (BadLocationException impossible) { rebuild(); return; }
            int line = lineBefore(offset), col = offset - lineStart(line);
            int newlines = 0, last = -1;
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') { newlines++; last = i; }
            int lineLength = lineLength(line);
            shiftBreaks(offset, length, text);
            if (newlines > 0) {
                int tail = text.length() - last - 1;
                if (col == 0 && text.endsWith("\n")) adjust(line, Integer.MAX_VALUE, newlines, 0);
                else {
                    adjust(line + 1, Integer.MAX_VALUE, newlines, 0);
                    for (Pos pos : all()) if (pos.line == line && pos.col >= col) {
                        pos.line += newlines;
                        pos.col = pos.col - col + tail;
                    }
                }
            }
            // Lines added after a line's end are a change at the first new line (appended_lines_mark).
            if (newlines > 0 && col == lineLength && col > 0 && text.startsWith("\n")) changed(line + 1, 0);
            else changed(line, col);
        }

        void removed(int offset, int length) {
            int line1 = lineBefore(offset), col1 = offset - lineStart(line1);
            int line2 = lineBefore(offset + length), col2 = offset + length - lineStart(line2);
            int len1 = lineLength(line1), len2 = lineLength(line2);
            dropBreaks(offset, length);
            int lines = line2 - line1;
            if (lines > 0) {
                if (col1 == 0 && col2 == 0 && !joining) {
                    delete(line1, line2 - 1);
                    adjust(line2, Integer.MAX_VALUE, -lines, 0);
                } else if (col1 == len1 && col2 == len2 && !joining) {
                    delete(line1 + 1, line2);
                    adjust(line2 + 1, Integer.MAX_VALUE, -lines, 0);
                } else {
                    delete(line1 + 1, line2 - 1);
                    // The joined line is gone, and with it its :global mark.
                    marked.removeIf(pos -> pos.line == line2);
                    for (Pos pos : all()) {
                        if (pos.line == line2) {
                            pos.line = line1;
                            pos.col = Math.max(0, pos.col - col2 + col1);
                        } else if (pos.line > line2) pos.line -= lines;
                    }
                }
            }
            // Whole lines deleted after a line's end (the last lines) are a change at the first deleted line (del_lines).
            if (lines > 0 && col1 == len1 && col2 == len2 && col1 > 0 && !joining) changed(line1 + 1, 0);
            else changed(line1, col1);
        }

        /** mark_adjust with an amount: lines {@code from}..{@code to} move by {@code amount}. */
        void adjust(int from, int to, int amount, int amountAfter) {
            for (Pos pos : all()) {
                if (pos.line >= from && pos.line <= to) pos.line += amount;
                else if (amountAfter != 0 && pos.line > to) pos.line += amountAfter;
            }
        }

        /** mark_adjust with MAXLNUM: marks on deleted lines {@code from}..{@code to} are deleted or moved to {@code from}. */
        void delete(int from, int to) {
            if (to < from) return;
            marks.entrySet().removeIf(entry -> DELETABLE.indexOf(entry.getKey()) >= 0 && entry.getValue().line >= from && entry.getValue().line <= to);
            marked.removeIf(pos -> pos.line >= from && pos.line <= to);
            for (Pos pos : all()) if (pos.line >= from && pos.line <= to) {
                pos.line = from;
                pos.col = 0;
            }
        }

        /** Every position that follows the text: the buffer's, and each editor's jumplist and previous contexts. */
        List<Pos> all() {
            List<Pos> all = new ArrayList<>(marks.values());
            for (Marks editor : editors) {
                if (editor.pcmark != null) all.add(editor.pcmark);
                if (editor.prevPcmark != null) all.add(editor.prevPcmark);
                all.addAll(editor.jumps);
            }
            all.addAll(changes);
            all.addAll(extra);
            all.addAll(marked);
            return all;
        }

        /** @return the line holding {@code offset}, by the break table as it was before the current change */
        int lineBefore(int offset) {
            int found = Arrays.binarySearch(breaks, 0, breakCount, offset);
            return found >= 0 ? found : -found - 1;
        }

        int lineStart(int line) { return line == 0 ? 0 : breaks[line - 1] + 1; }

        int lineLength(int line) {
            int end = line < breakCount ? breaks[line] : documentLengthBefore;
            return end - lineStart(line);
        }

        void shiftBreaks(int offset, int length, String text) {
            int at = lineBefore(offset);
            int added = 0;
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') added++;
            int[] next = new int[Math.max(16, breakCount + added)];
            System.arraycopy(breaks, 0, next, 0, at);
            int n = at;
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') next[n++] = offset + i;
            for (int i = at; i < breakCount; i++) next[n++] = breaks[i] + length;
            breaks = next;
            breakCount = n;
            documentLengthBefore = document.getLength();
        }

        void dropBreaks(int offset, int length) {
            int n = 0;
            for (int i = 0; i < breakCount; i++) {
                int b = breaks[i];
                if (b < offset) breaks[n++] = b;
                else if (b >= offset + length) breaks[n++] = b - length;
            }
            breakCount = n;
            documentLengthBefore = document.getLength();
        }

        void rebuild() {
            String text;
            try { text = document.getText(0, document.getLength()); }
            catch (BadLocationException impossible) { text = ""; }
            breaks = new int[Math.max(16, text.length() / 20)];
            breakCount = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) != '\n') continue;
                if (breakCount == breaks.length) breaks = Arrays.copyOf(breaks, breaks.length * 2);
                breaks[breakCount++] = i;
            }
            documentLengthBefore = text.length();
        }
    }

    private final Buffer buffer;
    private final List<Pos> jumps = new ArrayList<>();
    /** The jumplist index: jumps.size() when not moving through the list (Vim's w_jumplistidx). */
    int jumpIndex;
    /** The change-list index (Vim's w_changelistidx). */
    int changeIndex;
    /** The previous-context mark ({@code ''}) and the one before it (Vim's w_pcmark, w_prev_pcmark); null when unset. */
    private Pos pcmark, prevPcmark;
    /** The jumplist's and change list's size limit (Vim's JUMPLISTSIZE). */
    static final int LIST_SIZE = 100;

    /**
     * An editor's marks; the buffer's are the ones the document's other editors already have, else new.
     *
     * @param document the document the editor shows
     */
    Marks(Document document) {
        Buffer shared = document.getProperty(BUFFER) instanceof Buffer held ? held : null;
        if (shared == null) {
            shared = new Buffer(document);
            document.putProperty(BUFFER, shared);
        }
        buffer = shared;
        buffer.editors.add(this);
        pcmark = new Pos(0, 0);
    }

    /** The editor goes; the buffer's marks go with the document's last editor. */
    void close() {
        buffer.editors.remove(this);
        if (!buffer.editors.isEmpty()) return;
        buffer.document.removeDocumentListener(buffer.listener);
        if (buffer.document.getProperty(BUFFER) == buffer) buffer.document.putProperty(BUFFER, null);
    }

    /**
     * @param name a mark name: {@code a}–{@code z}, or one of {@code [ ] < > ^ . " '}
     * @return the mark, or null when it is not set
     */
    Pos get(char name) {
        Pos pos = buffer.marks.get(name);
        return pos == null ? null : pos.copy();
    }

    /** Sets a mark to a copy of the position; null clears it. */
    void set(char name, Pos pos) {
        if (pos == null) buffer.marks.remove(name);
        else buffer.marks.put(name, pos.copy());
    }

    /** @return the mode of the last Visual area, for {@code gv}; null when there was none */
    Mode visualMode() { return buffer.visualMode; }

    /** @param mode the mode of the last Visual area */
    void visualMode(Mode mode) { buffer.visualMode = mode; }

    /** @return the remembered column of the last Visual area */
    int visualCurswant() { return buffer.visualCurswant; }

    /** @param curswant the remembered column of the last Visual area */
    void visualCurswant(int curswant) { buffer.visualCurswant = curswant; }

    /** clrallmarks ({@code :delmarks!}): the lowercase marks, {@code '[ '] '^ '.} and the change list go; {@code '"} goes to the start. */
    void clearAll() {
        buffer.marks.keySet().removeIf(name -> name >= 'a' && name <= 'z' || "[]^.".indexOf(name) >= 0);
        buffer.marks.put('"', new Pos(0, 0));
        buffer.changes.clear();
        changeIndex = 0;
    }

    /** Clears every mark and list, after the text was replaced. */
    void clear() {
        buffer.marks.clear();
        jumps.clear();
        buffer.changes.clear();
        jumpIndex = changeIndex = 0;
        buffer.visualMode = null;
        pcmark = new Pos(0, 0);
        prevPcmark = null;
        buffer.newChange = true;
        buffer.rebuild();
    }

    /** @return the previous-context mark, or null */
    Pos pcmark() { return pcmark == null ? null : pcmark.copy(); }

    /** While {@code :global} runs, the previous context stays where the command started (setpcmark does nothing). */
    boolean frozen;

    /** setpcmark: the cursor becomes the previous context and a new jumplist entry. */
    void setpcmark(Pos cursor) {
        if (frozen) return;
        prevPcmark = pcmark;
        pcmark = cursor.copy();
        jumps.add(cursor.copy());
        if (jumps.size() > LIST_SIZE) jumps.removeFirst();
        jumpIndex = jumps.size();
    }

    /** m' and m`: like setpcmark, but the mark stays even when the cursor does not move. */
    void setpcmarkKeep(Pos cursor) {
        setpcmark(cursor);
        prevPcmark = pcmark.copy();
    }

    /** checkpcmark: after a jump that stayed on the same position, the previous context is kept. */
    void checkpcmark(Pos cursor) {
        if (prevPcmark != null && (pcmark == null || pcmark.equals(cursor))) pcmark = prevPcmark;
        prevPcmark = null;
    }

    /**
     * movemark: {@code count} entries along the jumplist (negative for {@code <C-o>}); the first move from the end
     * adds the cursor. Adjacent entries on the same line are merged first (cleanup_jumplist).
     *
     * @return the entry, or null when the list does not reach that far
     */
    Pos movemark(int count, Pos cursor) {
        cleanupJumps();
        if (jumps.isEmpty()) return null;
        if (jumpIndex + count < 0 || jumpIndex + count >= jumps.size()) return null;
        if (jumpIndex == jumps.size()) {
            setpcmark(cursor);
            jumpIndex--;
            if (jumpIndex + count < 0) return null;
        }
        jumpIndex += count;
        return jumps.get(jumpIndex).copy();
    }

    /** cleanup_jumplist: an entry with a later entry on the same line goes. */
    void cleanupJumps() {
        int to = 0;
        List<Pos> kept = new ArrayList<>();
        for (int from = 0; from < jumps.size(); from++) {
            if (jumpIndex == from) jumpIndex = to;
            boolean duplicate = false;
            for (int i = from + 1; i < jumps.size(); i++) {
                if (jumps.get(i).line == jumps.get(from).line) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(jumps.get(from));
                to++;
            }
        }
        if (jumpIndex == jumps.size()) jumpIndex = to;
        jumps.clear();
        jumps.addAll(kept);
    }

    /**
     * movechangelist: {@code count} entries along the change list (negative for {@code g;}), stopping at its ends.
     *
     * @return the entry, or null when already at that end
     */
    Pos movechangelist(int count) {
        List<Pos> changes = buffer.changes;
        if (changes.isEmpty()) return null;
        int n = changeIndex;
        if (n + count < 0) {
            if (n == 0) return null;
            n = 0;
        } else if (n + count >= changes.size()) {
            if (n == changes.size() - 1) return null;
            n = changes.size() - 1;
        } else n += count;
        changeIndex = n;
        return changes.get(n).copy();
    }

    /** Runs a removal of line breaks that joins lines: marks on a joined line move onto the line above. */
    void joining(Runnable removal) {
        buffer.joining = true;
        try { removal.run(); }
        finally { buffer.joining = false; }
    }

    /** A new undo step began: its first change may start a new change-list entry (u_savecommon's b_new_change). */
    void newChange() { buffer.newChange = true; }

    List<Pos> jumps() { return jumps; }

    List<Pos> changes() { return buffer.changes; }

    /**
     * do_move's mark_adjust: the marks on lines {@code first}..{@code last} (0-based) follow the lines to where
     * {@code edit} moves them, the first to {@code target}. The edit deletes and inserts lines, which alone would
     * delete those marks. {@code :global}'s marks stay behind, as Vim's line flags do.
     */
    void moving(int first, int last, int target, Runnable edit) {
        Map<Character, Pos> named = new HashMap<>();
        for (var entry : buffer.marks.entrySet())
            if (entry.getKey() != '.' && entry.getValue().line >= first && entry.getValue().line <= last) named.put(entry.getKey(), entry.getValue());
        Map<Pos, Pos> was = new java.util.IdentityHashMap<>();
        for (Pos pos : buffer.all()) if (pos.line >= first && pos.line <= last && buffer.marked.stream().noneMatch(m -> m == pos)) was.put(pos, pos.copy());
        edit.run();
        for (var entry : was.entrySet()) {
            entry.getKey().line = target + entry.getValue().line - first;
            entry.getKey().col = entry.getValue().col;
        }
        buffer.marks.putAll(named);
    }

    /** ml_setmarked: marks lines (0-based) for {@code :global}'s second pass. */
    void markLines(List<Integer> lines) {
        buffer.marked.clear();
        for (int line : lines) buffer.marked.add(new Pos(line, 0));
    }

    /** ml_firstmarked: the first marked line, which is unmarked; -1 when none is left. */
    int firstMarked() {
        Pos first = null;
        for (Pos pos : buffer.marked) if (first == null || pos.line < first.line) first = pos;
        if (first == null) return -1;
        buffer.marked.remove(first);
        return first.line;
    }

    /** ml_clearmarked. */
    void clearMarked() { buffer.marked.clear(); }

    /** Positions owned elsewhere (uppercase marks in this document) that move with the text too. */
    void track(Pos pos) { buffer.extra.add(pos); }

    void untrack(Pos pos) { buffer.extra.removeIf(p -> p == pos); }
}
```

The buffers, their windows and the Visual area's callers:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
V = "plugins/editor/src/main/java/dev/jasper/editor/vim/"
edit(V + "VimHost.java", [(
"""import java.util.function.BiPredicate;
import java.util.function.Function;
""",
"""import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
""", 1), (
"""    /**
     * An Editor tab as a Vim buffer (spec 6.2).
     *""",
"""    /**
     * An open file as a Vim buffer (spec 6.2): one however many views show it, as Vim shows one buffer in many windows
     * (pane flexibility spec 6).
     *""", 1), (
"""     * @param untitled whether it has never been saved
     * @param line its cursor line, 1-based
     */
    public record Buffer(int number, String name, Optional<Path> file, boolean current, boolean alternate, boolean modified,
                         boolean untitled, int line) { }
""",
"""     * @param untitled whether it has never been saved
     * @param line its cursor line, 1-based: the current view's, or the view focused last
     * @param windows how many views show it (Vim's b_nwindows)
     */
    public record Buffer(int number, String name, Optional<Path> file, boolean current, boolean alternate, boolean modified,
                         boolean untitled, int line, int windows) { }
""", 1), (
"""     * @param close closes a buffer's tab; {@code true} discards its changes without asking
     * @param select selects a buffer's tab
""",
"""     * @param close closes every view of a buffer, as {@code :qa} does; {@code true} discards its changes without asking
     * @param quit closes the view {@code editor} is in, as Vim's {@code :q} closes its window; {@code true} discards the
     *     changes of a buffer it was the last view of without asking
     * @param select selects a buffer's view, the one focused last
     * @param show selects the view {@code editor} is in
""", 1), (
"""    public record Documents(Function<VimEditor, List<Buffer>> buffers, IntFunction<CompletableFuture<Boolean>> save,
                            BiFunction<Integer, Path, CompletableFuture<Boolean>> saveAs, BiConsumer<Integer, Boolean> close,
                            IntConsumer select, IntConsumer reload, BiConsumer<VimEditor, Optional<Path>> open,
                            Function<Path, Optional<String>> read) {
        /** No tabs: an editor alone, which is buffer 1 and cannot be saved, closed or left. */
        public static final Documents NONE = new Documents(
            editor -> List.of(new Buffer(1, editor.documentName(), Optional.empty(), true, false, editor.documentModified(), true,
                editor.cursorLine())),
            number -> CompletableFuture.completedFuture(false), (number, file) -> CompletableFuture.completedFuture(false),
            (number, discard) -> { }, number -> { }, number -> { }, (editor, file) -> { }, file -> Optional.empty());
    }
""",
"""    public record Documents(Function<VimEditor, List<Buffer>> buffers, IntFunction<CompletableFuture<Boolean>> save,
                            BiFunction<Integer, Path, CompletableFuture<Boolean>> saveAs, BiConsumer<Integer, Boolean> close,
                            BiConsumer<VimEditor, Boolean> quit, IntConsumer select, Consumer<VimEditor> show,
                            IntConsumer reload, BiConsumer<VimEditor, Optional<Path>> open, Function<Path, Optional<String>> read) {
        /** No tabs: an editor alone, which is buffer 1 and cannot be saved, closed or left. */
        public static final Documents NONE = new Documents(
            editor -> List.of(new Buffer(1, editor.documentName(), Optional.empty(), true, false, editor.documentModified(), true,
                editor.cursorLine(), 1)),
            number -> CompletableFuture.completedFuture(false), (number, file) -> CompletableFuture.completedFuture(false),
            (number, discard) -> { }, (editor, discard) -> { }, number -> { }, editor -> { }, number -> { }, (editor, file) -> { },
            file -> Optional.empty());
    }
""", 1)])
edit(V + "ExFiles.java", [(
"""    /** {@code :wq [file]}: writes, then closes the tab once the write succeeded. */
    static void writeQuit(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        int number = current(buffers(cmd)).number();
        write(cmd, () -> documents(cmd).close().accept(number, false));
    }
""",
"""    /** {@code :wq [file]}: writes, then closes the pane once the write succeeded. */
    static void writeQuit(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        VimEditor editor = cmd.editor;
        write(cmd, () -> documents(cmd).quit().accept(editor, false));
    }
""", 1), (
"""        if (!buffer.modified() && arg == null) {
            cmd.editor.afterKey(() -> documents(cmd).close().accept(buffer.number(), false));
            return;
        }
""",
"""        if (!buffer.modified() && arg == null) {
            cmd.editor.afterKey(() -> documents(cmd).quit().accept(cmd.editor, false));
            return;
        }
""", 1), (
"""    /** {@code :q}: closes the tab; with changes only with {@code !}, which discards them. */
    static void quit(ExCommand cmd) {
        Buffer buffer = current(buffers(cmd));
        if (buffer.modified() && !cmd.forceit) throw new VimFailure("E37: No write since last change (add ! to override)");
        cmd.editor.afterKey(() -> documents(cmd).close().accept(buffer.number(), cmd.forceit));
    }
""",
"""    /**
     * {@code :q}: closes the pane. A buffer with changes loses its last pane only with {@code !}, which discards them; a
     * pane of one that another pane shows closes, as Vim closes a window of a buffer another window shows.
     */
    static void quit(ExCommand cmd) {
        Buffer buffer = current(buffers(cmd));
        if (buffer.modified() && buffer.windows() <= 1 && !cmd.forceit) throw new VimFailure("E37: No write since last change (add ! to override)");
        cmd.editor.afterKey(() -> documents(cmd).quit().accept(cmd.editor, cmd.forceit));
    }
""", 1)])
edit(V + "Ex.java", [(
"""        commands.action("ZQ", "close the pane, discarding its changes (`:q!`)", false, c -> c.editor.ex().execute("q!"));""",
"""        commands.action("ZQ", "close the pane, as `:q!` does", false, c -> c.editor.ex().execute("q!"));""", 1), (
"""        def("quit", BANG | TRLBAR, Addr.NONE, "close the pane; `!` discards its changes", ExFiles::quit);""",
"""        def("quit", BANG | TRLBAR, Addr.NONE, "close the pane; a file with changes loses its last pane only with `!`, which discards them", ExFiles::quit);""", 1)])
edit(V + "MarkCommands.java", [(
"""    /**
     * An uppercase mark in another tab (spec 3.6): its tab is selected and its cursor goes to the mark. An operator
     * or Visual mode cannot reach into another tab, so there the command fails.
     */""",
"""    /**
     * An uppercase mark in another tab (spec 3.6): the view it was set in is selected and its cursor goes to the mark. An
     * operator or Visual mode cannot reach into another tab, so there the command fails.
     */""", 1), (
"""        other.forgetColumn();
        c.vim.host().documents().select().accept(buffer.number());
""",
"""        other.forgetColumn();
        c.vim.host().documents().show().accept(other);
""", 1)])
W = "plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java"
edit(W, [(
"""    /** The tabs as Vim's buffers (Vim spec 6.2): numbered, and saved, closed, selected and opened the Editor's way. */
    private VimHost.Documents documents() {
        return new VimHost.Documents(this::buffers,
            number -> tabNumbered(number).map(tab -> saveSession(tab.session())).orElse(CompletableFuture.completedFuture(false)),
            (number, file) -> tabNumbered(number).map(tab -> tab.session().saveAs(new LocalDocument(file, context.background())))
                .orElse(CompletableFuture.completedFuture(false)),
            (number, discard) -> tabNumbered(number).ifPresent(tab -> {
                if (discard) discarding.add(tab);
                closeTab(tab);
            }),
            number -> tabNumbered(number).ifPresent(tab -> select(tab.session())),
""",
"""    /**
     * The open files as Vim's buffers (Vim spec 6.2), their views as its windows (pane flexibility spec 6): numbered, and
     * saved, closed, selected and opened the Editor's way.
     */
    private VimHost.Documents documents() {
        return new VimHost.Documents(this::buffers,
            number -> tabNumbered(number).map(tab -> saveSession(tab.session())).orElse(CompletableFuture.completedFuture(false)),
            (number, file) -> tabNumbered(number).map(tab -> tab.session().saveAs(new LocalDocument(file, context.background())))
                .orElse(CompletableFuture.completedFuture(false)),
            (number, discard) -> tabNumbered(number).ifPresent(tab -> { for (EditorTab view : views(tab.session())) close(view, discard); }),
            (editor, discard) -> tabOf(editor).ifPresent(tab -> close(tab, discard)),
            number -> tabNumbered(number).ifPresent(tab -> select(tab.session())),
            editor -> tabOf(editor).ifPresent(tab -> { activate(tab); hosted.get(tab).select(); }),
""", 1), (
"""            EditorWindow::readText);
    }
""",
"""            EditorWindow::readText);
    }

    /** Closes a view for Vim; {@code discard} lets its file's changes go without asking when it is the file's last view. */
    private void close(EditorTab tab, boolean discard) {
        if (discard) discarding.add(tab);
        closeTab(tab);
    }
""", 1), (
"""            return new VimHost.Buffer(numbers.get(session), session.location(), file, session == here, session == previous && session != here,
                session.dirty(), session.untitled() && !newFiles.containsKey(session), view.area().getCaretLineNumber() + 1);""",
"""            return new VimHost.Buffer(numbers.get(session), session.location(), file, session == here, session == previous && session != here,
                session.dirty(), session.untitled() && !newFiles.containsKey(session), view.area().getCaretLineNumber() + 1,
                views(session).size());""", 1)])
# The last Visual area is the buffer's, as the marks it names are (Marks.visualMode and visualCurswant).
edit(V + "MarkCommands.java", [(
"""            if (marks.visualMode == null) marks.visualMode = Mode.VISUAL;""",
"""            if (marks.visualMode() == null) marks.visualMode(Mode.VISUAL);""", 1), (
"""                if (pos != null && marks.visualMode == Mode.VISUAL_LINE) pos = new Pos(pos.line, name == '<' ? 0 : Visual.MAX);""",
"""                if (pos != null && marks.visualMode() == Mode.VISUAL_LINE) pos = new Pos(pos.line, name == '<' ? 0 : Visual.MAX);""", 1)])
edit(V + "Numbers.java", [(
"""c.editor.marks().visualCurswant == Visual.MAX""", """c.editor.marks().visualCurswant() == Visual.MAX""", 1)])
edit(V + "ReplaceWithRegister.java", [(
"""editor.marks().visualMode == Mode.VISUAL_BLOCK""", """editor.marks().visualMode() == Mode.VISUAL_BLOCK""", 1)])
edit(V + "Visual.java", [(
"""        marks.visualMode = mode;
        marks.visualCurswant = editor.curswant();""",
"""        marks.visualMode(mode);
        marks.visualCurswant(editor.curswant());""", 1), (
"""        if (start == null || end == null || marks.visualMode == null) throw Context.fail();
        Mode mode = marks.visualMode;
        int curswant = marks.visualCurswant;""",
"""        if (start == null || end == null || marks.visualMode() == null) throw Context.fail();
        Mode mode = marks.visualMode();
        int curswant = marks.visualCurswant();""", 1)])
PY
```

Regenerate the Vim reference, whose `ZQ` and `:q[uit]` rows change:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true
git diff docs/editor-vim-reference.md
```

Expected: two rows change. The `ZQ` row reads "close the pane, as `:q!` does", and the `:q[uit]` row "close the pane;
a file with changes loses its last pane only with `!`, which discards them".

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.*' --tests 'dev.jasper.editor.ui.*' --continue`
Expected: PASS: `ExFilesTest`'s quit cases (`:q`, `:wq`, `:x`, `ZZ`, `ZQ` quit the pane; `:qa` and `:xa` close each
buffer), `CompletionTest`, `VimDocsTest`, `EditorWindowTest`'s Vim cases (`vimWritesOpensListsSelectsAndClosesTabs`,
`anUppercaseMarkSelectsItsTab`), `EditorViewsTest.vimSeesOneBufferPerFileAndQuitClosesOnlyItsPane`,
`TextViewsTest.lowercaseMarksAndTheLastChangeBelongToTheFile` and the Vim corpus (`VimCorpusTest`), whose marks cases
pin Vim's mark_adjust in one view.

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor/src docs/editor-vim-reference.md
git commit -F - <<'EOF'
Editor: Vim sees one buffer per file and its views as windows

A file is one Vim buffer however many views show it. :q, :wq, :x, ZZ
and ZQ close the view they run in, and E37 refuses only a changed
file's last view, as Vim's :q closes a window of a buffer another
window shows; :qa and :xa close every view. :ls lists a file once,
:b and gt select its view active last, and an uppercase mark goes back
to the view it was set in. The marks a to z, the special marks, the
last Visual area and the change list are the file's, as a Vim
buffer's, shared by its views; the jumplist stays each view's. The Vim
reference is regenerated.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Every view comes back

Spec 6's restore (Rulings 11): a saved tab whose key is open comes back as another view of the file, reading nothing,
so every view of a file comes back and a remote file's waiting views share the one quiet read the first shown makes.
The restorer no longer declines "the file is open in another tab".

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (`openRestored` `:207-229`),
  `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java` (the restorer, `:165-166`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java` (the remote restore case `:371-422`,
  one case added), `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java` (`:233`, `:237`, `:255`)

**Interfaces:**
- Consumes: Task 2's `addView` and `views`.
- Produces: `public void EditorWindow.openRestored(EditorDocument document, WindowOwner owner)` (was boolean).

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
E = "plugins/editor/src/test/java/dev/jasper/editor/"
edit(E + "ui/EditorWindowTest.java", [(
"""        assertThat(window.openRestored(new LocalDocument(file, context.background()), null)).isTrue();
        drain();
        assertThat(window.sessions()).singleElement().extracting(DocumentSession::state).isEqualTo(DocumentSession.State.READY);
        assertThat(recorded).isEmpty();
        assertThat(window.openRestored(new LocalDocument(file, context.background()), null)).as("open already").isFalse();
""",
"""        window.openRestored(new LocalDocument(file, context.background()), null);
        drain();
        assertThat(window.sessions()).singleElement().extracting(DocumentSession::state).isEqualTo(DocumentSession.State.READY);
        assertThat(recorded).isEmpty();
        window.openRestored(new LocalDocument(file, context.background()), null);
        assertThat(window.sessions()).as("open already: it comes back as another view of the file").hasSize(1);
        assertThat(host.contentTabs()).hasSize(2);
        assertThat(recorded).isEmpty();
""", 1), (
"""        assertThat(window.openRestored(new LocalDocument(file("restored.txt", "restored\\n"), context.background()), restored)).isTrue();
""",
"""        window.openRestored(new LocalDocument(file("restored.txt", "restored\\n"), context.background()), restored);
""", 1)])
edit(E + "EditorPluginTest.java", [(
"""    /**
     * Workspace tabs spec 13: a restored document on another host waits until its tab is first active, then reads asking
     * nothing; a document already open restores no second tab.
     */""",
"""    /**
     * Workspace tabs spec 13: a restored document on another host waits until its tab is first active, then reads asking
     * nothing; a document already open comes back as another view of it, which reads nothing (pane flexibility spec 6).
     */""", 1), (
"""            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).as("already open").isEmpty();
            assertThat(host.declinedRestores()).singleElement().asString().contains("the file is open in another tab");
        }
    }
""",
"""            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", state)).as("already open: another view of it").isPresent();
            drain(host);
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.contentTabs()).as("two views of notes.md, and other.md").hasSize(3);
            assertThat(quiet.get() + loud.get()).as("the new view shares the file's one read").isEqualTo(1);
        }
    }

    /**
     * Pane flexibility spec 6: every view of a file comes back, each saved with the file's key; a remote file's views
     * wait, and the first of them shown reads the file once, quietly, for all of them; a local file's views share one
     * read too.
     */
    @Test void everyViewOfAFileComesBackAndItsFileIsReadOnceForAllOfThem() throws Exception {
        var plugin = plugin();
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID main = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            dev.jasper.editor.api.EditorDocument remote = new dev.jasper.editor.api.EditorDocument() {
                @Override public String key() { return "test:/srv/two.md"; }
                @Override public String name() { return "two.md"; }
                @Override public String location() { return "box:/srv/two.md"; }
                @Override public Optional<String> host() { return Optional.of("box"); }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Content> read(long limitBytes) {
                    reads.incrementAndGet();
                    byte[] text = "# two\\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.editor.api.Content(text, new dev.jasper.editor.api.Revision(1, text.length)));
                }
                @Override public java.util.concurrent.CompletableFuture<Optional<dev.jasper.editor.api.Revision>> revision() {
                    return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
                }
                @Override public java.util.concurrent.CompletableFuture<dev.jasper.editor.api.Revision> write(byte[] content) {
                    return java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("read only"));
                }
            };
            consumer.get().services().require(Editor.class).reopener("test:", key -> Optional.of(owner -> remote));
            var state = dev.jasper.sdk.RestoreState.of("document", "key", remote.key());
            assertThat(host.restoreTab("dev.jasper.editor", main, "two.md", state)).isPresent();
            assertThat(host.restoreTab("dev.jasper.editor", main, "two.md", state)).as("another view of it").isPresent();
            drain(host);
            assertThat(reads).as("both views wait").hasValue(0);
            var views = List.copyOf(host.contentTabs());
            assertThat(views).hasSize(2).allSatisfy(tab -> assertThat(host.restoreState(tab)).contains(state));
            views.getLast().select();
            drain(host);
            assertThat(reads).as("one read for both views").hasValue(1);
            views.getFirst().select();
            drain(host);
            assertThat(reads).hasValue(1);
            Path notes = Files.writeString(home.resolve("notes.md"), "# notes\\n");
            var local = dev.jasper.sdk.RestoreState.of("document", "key", "local:" + notes.toAbsolutePath().normalize());
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", local)).isPresent();
            assertThat(host.restoreTab("dev.jasper.editor", main, "notes.md", local)).isPresent();
            drain(host);
            var windowField = EditorPlugin.class.getDeclaredField("window");
            windowField.setAccessible(true);
            var window = (EditorWindow) windowField.get(plugin);
            assertThat(window.sessions()).as("two files, four views").hasSize(2);
            assertThat(host.contentTabs()).hasSize(4);
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests '*EditorPluginTest' --tests '*EditorWindowTest' --continue`
Expected: FAIL: `aRestoredRemoteDocumentReadsQuietlyOnlyOnceItsTabIsActive` and
`everyViewOfAFileComesBackAndItsFileIsReadOnceForAllOfThem` (the second restore of a key is declined, "the file is open
in another tab"), and `aRestoredDocumentIsNotRecordedAsOpened` (one content tab, not two).

- [ ] **Step 3: Implement**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit
edit("plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java", [(
"""     * asking anything ({@code EditorDocument.readQuietly}); Retry then reads it as usual, which may sign in. Recent Files
     * does not record it: coming back is not an open (workspace plan 6b's review).
     *
     * @param document the saved document
     * @param owner the window being restored
     * @return false when the document is open already, and no tab opened
     */
    public boolean openRestored(EditorDocument document, WindowOwner owner) {
        if (model.find(document.key()).isPresent()) return false;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var created = new DocumentSession(document, sessionEnvironment(ref));
        // The tab opens in the window being restored, which the user did not choose: the Editor's prompts and its own
        // open command keep the window they used before (workspace plan 6b's final review, M4).
        WindowOwner chosen = origin;
        origin = owner;
        try { add(created, ref); }
        finally { origin = chosen; }
        if (document.host().isPresent()) created.defer(); else created.load();
        return true;
    }
""",
"""     * asking anything ({@code EditorDocument.readQuietly}); Retry then reads it as usual, which may sign in. Recent Files
     * does not record it: coming back is not an open (workspace plan 6b's review). A document already open comes back as
     * another view of it (pane flexibility spec 6), which reads nothing: a file's views share one read, which waits for
     * the first of them to be active when the file is on another host.
     *
     * @param document the saved document
     * @param owner the window being restored
     */
    public void openRestored(EditorDocument document, WindowOwner owner) {
        Optional<DocumentSession> open = model.find(document.key());
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        DocumentSession created = open.isPresent() ? null : new DocumentSession(document, sessionEnvironment(ref));
        // The tab opens in the window being restored, which the user did not choose: the Editor's prompts and its own
        // open command keep the window they used before (workspace plan 6b's final review, M4).
        WindowOwner chosen = origin;
        origin = owner;
        try {
            if (created == null) addView(open.get(), dev.jasper.sdk.ui.TabPlacement.NEW_TAB, views(open.get()).stream().findFirst().orElse(null));
            else add(created, ref);
        }
        finally { origin = chosen; }
        if (created == null) return;
        if (document.host().isPresent()) created.defer(); else created.load();
    }
""", 1)])
edit("plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java", [(
"""            return window().openRestored(document.get().apply(request.window()), request.window())
                ? Optional.empty() : Optional.of("the file is open in another tab");
""",
"""            // Pane flexibility spec 6: a file already open comes back as another view of it.
            window().openRestored(document.get().apply(request.window()), request.window());
            return Optional.empty();
""", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --continue`
Expected: PASS, every Editor case.

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor/src
git commit -F - <<'EOF'
Editor: every view of a file comes back

A saved tab whose file is already open comes back as another view of
it, which reads nothing: every view of a file comes back when Jasper
restores its windows, and the views of a remote file that wait share
the one quiet read the first of them shown makes. The restorer no
longer declines an open key, and openRestored returns nothing.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Documentation, STATUS and the whole build

The guides say what Tasks 1-4 made true, each edit the smallest that makes its sentence true (AGENTS.md
"Documentation edits"): the Editor guide's opening (plan 9a's Open File sentence too), tabs, a section on a file's
views, its commands and actions and its Vim buffers; the plugin APIs' Editor version and paragraph. Then the STATUS entry and the whole build.

**Files:**
- Modify: `docs/editor.md` (`:43-44`, `:51`, `:65`, before `## Status bar` `:80`, the commands table `:120`, the
  actions table `:141`, `### Tabs, files and the window` `:666-670`), `docs/plugin-apis.md` (`:12`, `:302`, `:305-306`),
  `docs/STATUS.md` (the Editor's row, a new entry at the top of the handoff log), this plan's status banner (line 3)

**Interfaces:**
- Consumes: every task's names, as they landed.
- Produces: the STATUS entry.

- [ ] **Step 1: Edit the documents**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit, regex
# Plan 9a's sentence on an open file, however it is wrapped.
regex("docs/editor.md", r"A file that is(\s+)open(\s+)already(\s+)shows(\s+)its(\s+)open(\s+)pane(\s+)instead;",
    "A file that is open already shows in another [view](#views-of-one-file);", 1)
# The right-click paragraph's line before the Split list plan 9a extends.
regex("docs/editor.md", r"\*\*Toggle Comment\*\* and(\s+)\*\*Revert to Saved\*\*,(\s+)then(\s+)\*\*Split\*\*",
    r"**Toggle Comment**, **Revert to Saved**, **Split File Right** and **Split File\1Down**,\2then\3**Split**", 1)
# jasper editor --wait waits for each file's last view.
regex("docs/editor.md", r"returns once every tab(\s+)it opened has closed,", r"returns once the last view of each file\1it opened has closed,", 1)
edit("docs/command-line.md", [(
"""| `-w`, `--wait` | Return once every tab this call opened has closed, by you or with its window.""",
"""| `-w`, `--wait` | Return once the last view of each file this call opened has closed, by you or with its window.""", 1)])
edit("docs/editor.md", [(
"""selects its tab; `jasper editor` and a Cmd-click also move the caret to the line. While a file loads, its tab shows
"Loading *name*…" ("Loading *name* from *host*…" for a remote file) with **Cancel**, which closes the tab. A file that
""",
"""selects its tab, of several [views](#views-of-one-file) the one you were in last; `jasper editor` and a Cmd-click also
move the caret to the line. While a file loads, its tab shows "Loading *name*…" ("Loading *name* from *host*…" for a
remote file) with **Cancel**, which closes the tab. A file that
""", 1), (
"""Each file is the item of one pane: a tab of its own, or a pane beside others in a tab""",
"""Each file is the item of a pane, or of several as [views](#views-of-one-file): a tab of its own, or a pane beside
others in a tab""", 1), (
"""## Status bar
""",
"""## Views of one file

**Split File Right** and **Split File Down** show the file in another view, in a new pane to the right of its own or
below it: from the text's right-click menu, the tab's menu or the command palette, with no default shortcut (Settings ›
Keymap can give them one). **Open File Right…** and **Open File Down…** of a file that is already open put another view
of it beside the focused pane. A new view starts where its source view is, with its caret, selection and scroll
position. In Vim mode `:action split-file-right` and `:action split-file-down` run them.

A file's views show one text: an edit in one shows in the others at once, and Undo and Redo in any view undo and redo
the last edit made in any of them, the undoing view's caret going to the change while the others stay where they were.
The ● marker, **Save**, **Save As…**, **Revert to Saved** and a reload act on the file from any view, and a reload
keeps each view's caret on its line. Each view has its own caret, selection, scroll position, folds, find bar, Vim
state and Markdown view; the language and soft wrap are the file's. Vim's marks `a` to `z`, `'.` and its change list
are the file's too, as they are a Vim buffer's; its jumplist is each view's. Closing a view closes only that view:
Jasper asks about unsaved changes when the file's last view closes, and a window that closes with one view of a file
another window still shows asks nothing. **Cancel** in one view of a file that is still loading closes that view, and
the file goes on loading for the others. **Open in Editor…** of an open file selects the view you were in last, and
Recent files lists the file once. When Jasper restores its windows every view comes back, and the file is read once
for all of them.

## Status bar
""", 1), (
"""| Revert to Saved | | | `revert` |
""",
"""| Revert to Saved | | | `revert` |
| Split File Right | | | `split-file-right` |
| Split File Down | | | `split-file-down` |
""", 1), (
"""| Reload vimrc | `dev.jasper.editor.reload_vimrc` | | | |
""",
"""| Reload vimrc | `dev.jasper.editor.reload_vimrc` | | | |
| Split File Right | `dev.jasper.editor.split_file_right` | | | The text's right-click menu |
| Split File Down | `dev.jasper.editor.split_file_down` | | | The text's right-click menu |
""", 1), (
"""- The tabs are Vim's buffers. `:w` saves through the Editor's Save (`:w file` saves as the file), `:q` closes the tab
  (with unsaved changes it fails with `E37`; `:q!` closes it and discards them), `:wq`, `:x` and `ZZ` save and close,
  `:e file` opens a file in a tab (a relative name starts from the current file's folder), `:r file` puts a file's
  lines below the cursor, `:ls` lists the tabs with their buffer numbers, and `:b N`, `:bn`, `:tabn`, `gt` and `gT`
  move between them. An uppercase mark (`mA`) set in one tab takes you back to that tab (`'A`).""",
"""- The open files are Vim's buffers, one each however many [views](#views-of-one-file) show it, and the views are its
  windows. `:w` saves through the Editor's Save (`:w file` saves as the file), `:q` closes the pane (in a file's last
  view, with unsaved changes, it fails with `E37`; `:q!` closes it and discards them), `:wq`, `:x` and `ZZ` save and
  close the pane, `:qa` closes every view, `:e file` opens a file in a tab (a relative name starts from the current
  file's folder), `:r file` puts a file's lines below the cursor, `:ls` lists the files with their buffer numbers, and
  `:b N`, `:bn`, `:tabn`, `gt` and `gT` move between them, each to the file's view you were in last. An uppercase mark
  (`mA`) set in one view takes you back to that view (`'A`).""", 1)])
edit("docs/plugin-apis.md", [
    ("""| Editor, `dev.jasper.editor` | 0.14.0 |""", """| Editor, `dev.jasper.editor` | 0.15.0 |""", 1),
    ("""(`dev.jasper.editor`, 0.14.0). The user's side is in [Editor](editor.md).""",
     """(`dev.jasper.editor`, 0.15.0). The user's side is in [Editor](editor.md).""", 1), (
"""open selects its tab, keeps it in the window it is in, and raises that window.""",
"""open selects its tab (of several views of it, the one focused last), keeps it in the window it is in, and raises that
window. A document shown in several views is one document to its plugin: the Editor reads it once, and saves and
reloads it for all of them.""", 1)])
PY
```

The first `docs/editor.md` anchor is plan 9a's Open File bullet; the right-click anchor sits inside the paragraph 9a
rewords, outside its edit.

- [ ] **Step 2: Add the STATUS entry**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan9b")
from edits import edit, regex
edit("docs/STATUS.md", [("""| Editor | `dev.jasper.editor` | 0.14.0 |""", """| Editor | `dev.jasper.editor` | 0.15.0 |""", 1), ("""## Handoff log

Newest first. Each entry describes its change as it stood when the entry was written.

""", """## Handoff log

Newest first. Each entry describes its change as it stood when the entry was written.

**Workspace tabs, plan 9b: two views of one file — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-21-workspace-9b-two-views-of-a-file.md),
[spec](superpowers/specs/2026-10-21-jasper-pane-flexibility-design.md), section 6). The Editor (0.15.0) shows one file
in several views, each the item of a pane. **Split File Right** and **Split File Down** (the text's right-click menu,
the tab's item commands, the palette as `dev.jasper.editor.split_file_right` and `_down`, Vim's `:action`; no default
key) open another view beside the file's own, which they select first; **Open File Right…** and **Down…** of an open
file (plan 9a's `openDocumentBeside`) open one beside the focused pane. A file's views share one Swing document
(`VimTextArea.share`) and one undo history: the first view's `VimUndoManager` is the document's, which every view's
text area gets back from `createUndoManager` (RSyntaxTextArea asks for it again in `discardAllEdits`), each view's
registration with the document handing it each edit once, so it takes each edit once; each view brackets its own Vim
steps, the one the user last typed in, clicked or focused edits now, and another view's open change ends where the next
one edits (Vim's u_sync); an edit leaves the other views' scroll positions, and an undo or redo moves only its own
view's caret to the change, the others keeping caret, selection and scroll position. A view that closes while others
stay lets go of the document (`unshare`: an empty document and a history of its own, discarding nothing of the shared
one) and takes its listeners off the session (`DocumentSession.removeListener`); Cancel in one view of a loading file
leaves the read to the others; a split view starts at its source's caret, selection and scroll position. The session reads and replaces its text
through `TextViews`, once for every view, each keeping its caret line. In `EditorWindow` a file's views are listed most
recently active first (`recent`): Open in Editor… and `Editor.open` of an open file select the first, prompts show over
it, and only the file's last view asks about unsaved changes, a batch close asking once per file whose every view
closes. Vim has one buffer per file and the views as its windows (`VimHost.Buffer.windows`, `Documents.quit` and
`show`): `:q` closes its pane and gives `E37` only in a changed file's last view; the buffer's marks, the last Visual
area and the change list are kept once per document (`Marks.Buffer`, a document property) for every view's editor,
the jumplist per view. Restoring, an open key comes back as
another view, which reads nothing (`openRestored` returns nothing now; the restorer no longer declines "the file is open
in another tab"). Remote and Project Index are unchanged: they see one document per key, read and saved once. Rulings
(plan, "Rulings"): (1) one undo history per document, the first view's `VimUndoManager`, shared through
`RTextArea.createUndoManager` and `setDocument`, taking each edit once; (2) the view that edits now is the one last
typed in, clicked or focused, or whose Vim engine asked, and another view's open change ends where the next edits; (3)
an undo or redo moves only its own view's caret, and an edit leaves the other views' scroll positions; (4) a closing view lets go of the document while others stay; (5) the
session's text goes through `TextViews`, replaced once, each view keeping its caret line; (6) the language, tabs and
soft wrap are the file's; (7) a file's views are listed most recently active first, and an open of an open file selects
the first; (8) only a file's last view asks, and a batch asks once per file; (9) Split File Right and Down are Editor
commands and actions with no default key, selecting their view first; (10) Open File Right… of an open file opens
another view and reports it to Recent Files as Open in Editor… does, Split File and a restore report nothing; (11) a
view's restore state is its file's key alone, and an open key restores as another view; (12) Vim: one buffer per file,
its views the windows, `:q` closing the pane and `E37` in a changed file's last view only, an uppercase mark returning
to its view; (13) Vim's buffer marks, the last Visual area and the change list belong to the file, the jumplist and
`''` to each view (the controller's ruling); (14) one find bar, in the selected view, so no view's search leaks into
another (the controller's reading of spec 6); (15)
the Editor API's plugins see one document per key, and `Editor.open` of an open key selects its view focused last; (16)
Editor 0.15.0; (17) no `:split` or `:vsplit`; (18) `jasper editor --wait` returns once the file's last view has closed;
(19) a split view starts at its source's caret, selection and scroll position; (20) Cancel in one view of a loading
file closes that view alone, the read going on for the others.
Spec issues: none. Deviations: <none, or each with its cause>. `./gradlew check --continue`: **<counts>**.
GUI checks for the user:
- Split File Right from a file's right-click menu: the file shows in a new pane to the right; typing in either pane shows
  in both at once, the ● marker on both; Cmd+Z (Ctrl+Z) in the other pane undoes it, that pane's caret going to the
  change while the first pane's caret, selection and scroll position stay;
- Split File Down from the middle of a long file with some text selected: the new view shows the same place and the
  same selection;
- Vim on: `ma` in one view, then `'a` in the other goes to the mark; an edit in either, then `'.` and `g;` in the
  other, go to it;
- Cancel in one view of a remote file that is still loading: that view closes, and the other shows the file once read;
- scroll one view far from its caret with the wheel, then type near the top in the other view: the first view stays
  where it was scrolled;
- Vim on: Insert mode in one view, click the other view and type, press Esc in the first, then `u` in each: each undo
  takes the newest edit, in time order;
- a Markdown file: Preview in one view, Source in the other; typing in the source updates the preview;
- close one view of a changed file: no question; close its last view: "Save changes to …?"; move one view to a new
  window, change the file, close that window: no question; then Quit: one question naming the file once;
- Revert to Saved from one view while the other view's caret is near the end of a file the revert shortens;
- Save As… from one view: both panes take the new name; quit and start: both views come back, each where it was;
- a remote file in two views (one in a child window): quit and start; both come back waiting, and showing either reads
  the file once, quietly;
- Open File Right… of a file that is already open: another view beside the focused pane; Open in Editor… of it then
  selects the view you were in last;
- Vim `:ls` in a view of a file with two views lists it once; `:q` there closes only that pane, and `:q` in its last
  view of a changed file says `E37`.

Not merged, not pushed.

""", 1)])
PY
```

Set this plan's status banner (line 3) to "**Status: executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`."
and list any deviation from the plan's text there and in the entry.

- [ ] **Step 3: Check the sources and build everything**

```bash
python3 - <<'PY'
import pathlib
for name in [str(p) for root in ("jasper-terminal/src", "jasper-ui/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
print("checked")
PY
git grep -n -e 'the file is open in another tab' -e 'replaceText' -e 'shows its open pane instead' -- plugins docs/editor.md docs/plugin-apis.md
```

Expected: `checked` alone, and the `git grep` prints nothing.

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Read the counts from the XML in `*/build/test-results/test/` and put them in the STATUS entry
in place of `<counts>`, with the dates and commit range.

Run: `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
Expected: BUILD SUCCESSFUL (the SDK did not change; the example builds as before).

- [ ] **Step 4: Commit**

```bash
git add docs/editor.md docs/command-line.md docs/plugin-apis.md docs/STATUS.md \
    docs/superpowers/plans/2026-10-21-workspace-9b-two-views-of-a-file.md
git commit -F - <<'EOF'
Editor: two views of one file, documentation and status

The Editor guide describes a file's views: Split File Right and Down,
Open File Right… of an open file, the shared text and undo history,
what each view keeps of its own, closing, restoring and Vim's buffers
and windows; the plugin APIs say that a document shown in several
views is one document to its plugin. STATUS records plan 9b with its
rulings and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Self-review

Checked against the spec, the briefs and plan 9a's fixed names with this plan's text in hand:

- **Spec coverage.** 6's opening a second view: Open File Right… and Down… of an open file (Task 2 Steps 1b and 3b,
  Rulings 10), Split File Right and Down in the right-click menu, the tab's commands, the palette and the keymap with no
  default (Task 2, Rulings 9), the new view where its source is (Task 2, Rulings 19). 6's one document, many views: an
  edit in every view at once (Task 1b `aSecondViewShowsTheSameTextAndEveryEditAtOnce`, Task 2 `splitFileRight…`); own
  caret, selection, scroll, find bar, Vim state and Markdown mode (Task 1a's caret and scroll cases, Task 1b's Vim and
  Markdown cases, Task 3's marks; Rulings 3, 13, 14); undo and redo of the document (Task 1a, Rulings 1-3); the ●
  marker, Save, Revert to Saved and Reload on the document (Task 2's marker and save cases, Task 1b's revert and reload
  cases). 6's closing (Task 2, Rulings 8 and 20; Task 3's `:q`). 6's other behaviour:
  Open in Editor… selects the view focused last (Task 2, Rulings 7), Recent Files once (Task 2, Rulings 10), every
  view comes back and a remote file is read once (Task 4, Rulings 11). 3's "a file that is already open" (Task 2 Step
  3b). 8: no SDK change, the Editor's next minor (Rulings 15, 16). The brief's Vim numbering (Task 3, Rulings 12) and
  the Editor API's users (Rulings 15, Verified facts 10).
- **Type consistency.** `VimTextArea.share(VimTextArea)` and `unshare()` (Task 1a) are what `DocumentArea(DocumentArea)`
  and `TextViews.remove` (Task 1b) call; `VimUndoManager.join`, `leave`, `view`, `shared` are what `VimTextArea` calls;
  the five-argument `EditorTab` and `DocumentSession.removeListener` (Task 1b) are what Task 2's `addView` and `remove`
  use; `EditorTab.beginReplace`, `endReplace` and `replaced` are what `TextViews.replace` calls, and `TextViews.size`
  what DISMISS reads; `EditorWindow.views`, `addView`, `openView`, `splitFile`, `forget`, `lastViews` and `tabsClosing`
  (Task 2) are what Tasks 3 and 4 and the tests call; `Marks.visualMode()`, `visualMode(Mode)`, `visualCurswant()` and
  `visualCurswant(int)` (Task 3) are what `MarkCommands`, `Numbers`, `ReplaceWithRegister` and `Visual` call; `VimHost.Buffer`'s `windows` and
  `Documents`' `quit` and `show` (Task 3) are what `EditorWindow.documents()` and `buffers()`, `ExFiles` and
  `MarkCommands` use; `openRestored`'s void (Task 4) is what `EditorPlugin`'s restorer and `EditorWindowTest` call.
- **Field initializers.** `EditorTab`'s `document` and `views` move from initializers to the constructor, before
  anything reads them, and its `refreshing` is a method reference, run only after the constructor; `VimTextArea`'s
  `undo` and `followsFocus` keep no initializer, since the superclass constructor assigns `undo` through
  `createUndoManager`, and the focus listener goes on in `share`, not in the constructor (no `this-escape`).
- **Review Focus.** Each line names its test and its task.
- **Proportion.** The plan carries complete tests and new files, and every edit as an anchor and its replacement, as
  the brief requires; prose decides only names, rules and wording.
