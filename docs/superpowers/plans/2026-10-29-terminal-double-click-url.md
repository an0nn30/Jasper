# Terminal double-click selects a whole URL — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status (2026-10-29): done** on `claude/double-click-url` (from local main `2a3e8d0d0`), not merged or pushed.
> Deviations: executed inline (one session, no per-task subagents), then one whole-branch review (no Critical or
> Important findings). Its fixes: `SelectionController.extend` (not in Task 2's file list) now lets the pointer, not the
> unit under it, decide which side of the anchor a drag is on and keeps the anchor whole, since a URL can now lie inside
> a longer word (`url=https://…/docs.`); tests added for that, for rows below the scrollback, OSC 8 text with wide
> characters, a drag from a two-row URL, a wide last URL character and the truncated logical line
> (`TerminalLogicalLineTest`). The full `check` ran once, after those fixes.

**Goal:** A double-click in a terminal pane on a link selects exactly the link: the cells `LinkDetector` finds there
(soft wraps included), or an OSC 8 hyperlink's whole visible span; anywhere else it selects a word as today.

**Architecture:** `LinkDetector` reports where a plain-text URL lies on a joined row, not just its text.
`BufferQueries.wordSelection` (which both the double-click and the word-by-word drag after it call) reads, under the
buffer lock, the clicked cell's OSC 8 link and walks that link's cells across the soft-wrapped line, or joins the
wrapped rows as `fileLinkAt` does and matches outside the lock; it maps the joined row's cells back to absolute rows. With
no link it returns today's `WordBoundaries` word. Nothing public changes: `TerminalAccess.wordSelection` keeps its
signature.

**Tech Stack:** Java 25, jediterm-core 3.76 (internal only), JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md`, section 9 (the terminal's right-click
menu): "these follow the selection, not the pointer; double-clicking a URL selects it whole". Open item from plan 3b's
final review (I1, ruling R14) and plan 4a's ruling R45.

## Global Constraints

- `jasper-terminal` only; no public method takes or returns a JediTerm type; `verifyTerminalArchitecture` must pass
  (the new types stay in `internal.text` / `internal.emulation`).
- Rows are absolute rows (`discardedLines + historyLines + screenRow`); every buffer read holds `TerminalTextBuffer`'s
  lock, and regex matching runs outside it.
- The selected URL is the one Cmd-click opens: the same regexes, the same trailing-punctuation rule
  (`PathDetector.TRAILING`), the same `LogicalLine` bounds (a truncated logical line has no plain-text URL).
- Source hygiene: no raw control or private-use characters in source; write `\033`, `\007`, `\uE000` escapes.
- Never launch the GUI.

## Review Focus

1. Prose punctuation after a URL (`see https://jasper.dev/docs.` or `(… https://jasper.dev/a_(b)_c).`) is not selected,
   and a `(` inside the URL is — Task 2 tests the trailing `.` and the parenthesised URL.
2. A wide character before a URL or inside OSC 8 text keeps the selection on whole cells — Task 2,
   `aWideCharacterBeforeAUrlLeavesTheUrlCellsExact`.
3. Dragging on after double-clicking a URL extends by whole URLs and words in both directions, from a multi-cell anchor
   — Task 2, `draggingOnFromADoubleClickedUrlExtendsByWholeUnits`.
4. Two OSC 8 links side by side with different URIs: only the clicked one is selected — Task 2,
   `adjacentOsc8LinksSelectOnlyTheClickedOne`.
5. An OSC 8 link with a non-web scheme (`ls --hyperlink`'s `file://`) and spaces in its text selects its visible span
   too: the program marked it as one link — Task 2, `anOsc8FileLinkSelectsItsWholeNameWithSpaces`.

---

### Task 1: `LinkDetector` reports a URL's cells

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/LinkDetector.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/LinkDetectorTest.java`

**Interfaces:**
- Produces: `public record LinkDetector.UrlSpan(String url, int firstColumn, int lastColumn)` (columns are the row's
  cells, inclusive, continuation cells of a wide last character included) and
  `public static Optional<UrlSpan> LinkDetector.urlSpanAt(RowText row, int column)`: the `http`/`https`/`ftp` URL
  covering `column`, else the `file://` URL (with its `:LINE[:COLUMN]`), as `urlAt` and `fileUrlAt` find them.
  `urlAt` and `fileUrlAt` keep their behaviour and share the matching.

- [x] **Step 1: Write the failing tests** in `LinkDetectorTest`:
  - `aSpanCoversTheUrlsCellsWithoutTrailingPunctuation`: `urlSpanAt(row("(see https://jasper.dev/a_(b)_c)."), 12)`
    is `new UrlSpan("https://jasper.dev/a_(b)_c", 5, 30)`.
  - `aSpanFindsAFileUrlWithItsPosition`: `urlSpanAt(row("e: file:///tmp/Foo.kt:12:5 boom"), 6)` is
    `new UrlSpan("file:///tmp/Foo.kt:12:5", 3, 25)`.
  - `aSpanCountsWideCharacterCells`: a row whose text is `日\uE000 https://a.dev` (width 60): `urlSpanAt(row, 5)` is
    `new UrlSpan("https://a.dev", 3, 15)`.
  - `noSpanOutsideAUrl`: `urlSpanAt(row("see https://jasper.dev now"), 1)` is empty.
- [x] **Step 2: Run** `./gradlew :jasper-terminal:test --tests '*LinkDetectorTest'` — expect compile failure.
- [x] **Step 3: Implement** `UrlSpan` and `urlSpanAt`; make the private `at(Pattern, RowText, int)` return
  `Optional<UrlSpan>` and `urlAt`/`fileUrlAt` map it to the text.
- [x] **Step 4: Run** the same command — expect PASS.
- [x] **Step 5: Commit** `Terminal: LinkDetector reports the cells a URL covers`.

### Task 2: A double-click selects the link under it

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/emulation/BufferQueries.java`
  (`wordSelection`, `WrappedRow`, `wrappedRowLocked`)
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/TerminalAccess.java` (the `wordSelection`
  Javadoc), `jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/WordBoundaries.java` (its class Javadoc:
  the rule where no link is)
- Create: `jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewLinkSelectionTest.java`
- Modify: `docs/terminal.md` (Selecting text), `docs/terminal-maintenance.md` (the selection recipe's step 2)

**Interfaces:**
- Consumes: `LinkDetector.urlSpanAt(RowText, int)` and `UrlSpan` from Task 1.
- Produces: `BufferQueries.wordSelection(long row, int column)` (signature unchanged) returns, in order: the clicked
  cell's OSC 8 link's cells (any scheme), walked left and right through cells whose `CellAttributes.link()` equals it,
  across the rows of `LogicalLine.around(row, …)`; else the `urlSpanAt` span on the joined wrapped row (none when the
  logical line is truncated); else `WordBoundaries.wordAt` on the clicked row. The column is clamped to the width first.
  `WrappedRow` gains `firstRow` and `width` and a `Selection cells(int from, int to)` that maps joined-row cells
  (`rowIndex * width + column`) back to absolute rows.

- [x] **Step 1: Write the failing tests** in `TerminalViewLinkSelectionTest` (40 × 4 grid, `EmulationFixture.unstarted`,
  `FakeConnector`, the press/drag helpers `TerminalViewInteractionTest` uses; each double-click is
  `press(column, row, 2, 0)` then `release`), asserting `view.selectedText()`:
  - `doubleClickingAUrlSelectsItWithoutTheFullStopAfterIt`: `see https://jasper.dev/docs. Then` at column 10 →
    `https://jasper.dev/docs`.
  - `doubleClickingASoftWrappedUrlSelectsItWholeFromEitherRow`: `see https://jasper.dev/docs/terminal/selection/rules`
    (52 cells: row 0 and 12 cells of row 1), at (10, 0) and at (5, 1) → both
    `https://jasper.dev/docs/terminal/selection/rules`.
  - `doubleClickingAUrlInParenthesesKeepsItsOwnParentheses`: `(see https://jasper.dev/a_(b)_c).` at column 12 →
    `https://jasper.dev/a_(b)_c`.
  - `doubleClickingAnOsc8LinkSelectsItsWholeVisibleText`: `\033]8;;https://jasper.dev/docs\007the docs page\033]8;;\007 more`
    at column 5 → `the docs page`.
  - `doubleClickingASoftWrappedOsc8LinkSelectsItFromEitherRow`: `ok \033]8;;https://jasper.dev/\007` + 45 cells of
    link text + `\033]8;;\007 end`, at (10, 0) and (2, 1) → the 45-cell link text.
  - `doubleClickingAWordThatIsNoLinkSelectsTheWord`: `echo foo.bar baz` at column 6 → `foo.bar`; at column 14 → `baz`.
  - Review Focus 2–5: `aWideCharacterBeforeAUrlLeavesTheUrlCellsExact` (`日本 https://jasper.dev/x` at column 9 →
    `https://jasper.dev/x`), `draggingOnFromADoubleClickedUrlExtendsByWholeUnits` (`go https://jasper.dev/(b)_c now`:
    double-click column 8, drag to column 29 → `https://jasper.dev/(b)_c now`, drag to column 0 →
    `go https://jasper.dev/(b)_c`), `adjacentOsc8LinksSelectOnlyTheClickedOne`
    (`\033]8;;https://a.dev/\007alpha\033]8;;https://b.dev/\007beta\033]8;;\007` at column 6 → `beta`),
    `anOsc8FileLinkSelectsItsWholeNameWithSpaces` (`\033]8;;file:///tmp/My%20Notes.txt\007My Notes.txt\033]8;;\007`
    at column 4 → `My Notes.txt`).
- [x] **Step 2: Run** `./gradlew :jasper-terminal:test --tests '*TerminalViewLinkSelectionTest'` — expect the URL and
  OSC 8 cases to fail (today's word rule) and the plain-word case to pass.
- [x] **Step 3: Implement** `wordSelection` as the Interfaces block says: under the lock read the clicked row, the
  clicked cell's link, the OSC 8 walk (reading each row's styles once with `readCells(width, chars, styles)`), or the
  `WrappedRow` and the `WordBoundaries` fallback; run `urlSpanAt` after unlocking. Update the two Javadocs.
- [x] **Step 4: Run** the new test class, `SelectionSessionTest`, `TerminalViewInteractionTest` and
  `WordBoundariesTest` — expect PASS.
- [x] **Step 5: Docs.** `docs/terminal.md`: the Double-click row reads "a word, or a whole link; dragging on extends
  word by word"; the paragraph under the table says a double-click on a link selects the URL a Cmd-click (Ctrl-click)
  would open, across wrapped rows and without trailing prose punctuation, or an OSC 8 link's whole text, and keeps the
  word rule for everything else. `docs/terminal-maintenance.md` step 2: `WordBoundaries` is the word rule and
  `LinkDetector` the link a double-click selects.
- [x] **Step 6: Commit** `Terminal: a double-click selects the whole link under it`.

### Task 3: Record and verify

**Files:**
- Modify: `docs/STATUS.md` (new top entry), this plan's banner if anything deviated.

- [x] **Step 1:** `./gradlew :jasper-terminal:check`, then `./gradlew check` once (background); read the totals from
  `*/build/test-results/test/*.xml`.
- [x] **Step 2:** STATUS entry: branch, the behaviour, that plan 3b's I1 / plan 4a's R45 follow-up is done (the Open URL
  in Split items now appear after a double-click on a URL), the exact totals, no GUI launched.
- [x] **Step 3: Commit** `Docs: STATUS records the double-click URL selection`.
