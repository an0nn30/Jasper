# Moray Plan 2 — Terminal Completeness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Moray's single terminal pane complete for daily use: scrollback viewing, mouse reporting, selection with copy and paste, search with highlighting, shell integration (OSC 7 working directory, OSC 133 prompt marks and jumps, OSC 8 and detected links with ⌘-click), cursor reset via DECSCUSR 0, and an in-pane exit message.

**Architecture:** Everything stays in `moray-terminal` except one change to `Main`. A streaming `ShellIntegrationFilter`, wrapped around the PTY connector, rewrites the sequences JediTerm ignores (OSC 7, OSC 133, DECSCUSR 0) into JediTerm's `OSC 1341` custom commands. JediTerm then delivers them in emulator order, so the cursor is exactly where the sequence appeared. Lines get stable **absolute row** numbers (`discarded + history + screenRow`), so selections, prompt marks, search matches and the scrolled-back viewport stay attached to their text while output streams. Pure classes (`Selection`, `SelectionText`, `WordBoundaries`, `RowText`, `TerminalSearch`, `LinkDetector`, `Viewport`, `MouseRouting`) carry the logic; `TerminalView` wires them to Swing.

**Tech Stack:** Java 25 on JBR 25, Gradle 9.7.0, Swing, `jediterm-core` 3.76, pty4j 0.13.10, JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md` (§4.1, §4.3–§4.6; plan 1 delivered the rest of §4).
**Previous plan:** `docs/superpowers/plans/2026-09-10-moray-plan-1-terminal-core.md` (merged to `main`).

## Global Constraints

- Everything from plan 1's Global Constraints still applies. In particular: packages `dev.moray.terminal` / `dev.moray.app`; JediTerm stays an `implementation` dependency of `moray-terminal`; **no public method in `moray-terminal` takes or returns a JediTerm type**; no interface without two real implementations; `moray-terminal` never depends on `moray-app`.
- JediTerm OSC facts (verified against 3.76 on 2026-09-10):
  - OSC 0/1/2 set the title.
  - **OSC 7 is swallowed.**
  - OSC 8 creates hyperlinks **only if** `JediTerminal.setUrlHyperlinkFilter` is set, and only when the filter returns one `LinkResultItem` spanning `0..uri.length()`.
  - OSC 10/11 are colour queries.
  - **OSC 104 and 1341** call `Terminal.processCustomCommand(argsAfterTheNumber)` in emulator order, with arguments split on `;`.
  - Every other OSC, **including 133**, is dropped.
- JediTerm mouse facts:
  - `JediTerminal.onMouseEvent(column, row, MouseEvent(Type, buttonCode, modifierFlags), MouseEventProcessingSettings(reportingEnabled, alternateBuffer, simulateScrollWithArrows))` takes **0-based** cells, encodes the report and sends it through the terminal output.
  - In SGR mode a right-button press at (0,0) sends `ESC[<2;1;1M`, its release at (4,2) sends `ESC[<2;5;3m`, and a wheel-up sends button 65.
  - A press with the Shift flag sends nothing.
  - With reporting disabled it returns `false` and sends nothing.
- JediTerm buffer facts:
  - `TextBufferChangesListener.linesDiscardedFromHistory(lines)` fires when scrollback overflows.
  - `TerminalLine.isWrapped()` marks a soft-wrapped row.
  - `ArrayTerminalDataStream(char[])` feeds text to an emulator and ends with an `EOF`.
- **Absolute rows:** `absoluteRow = linesDiscardedSoFar + historyLines + screenRow` (screen row 0 = top of the live screen; negative buffer rows are scrollback). A given line keeps its absolute row as output streams, until it is discarded from scrollback.
- **Shortcuts wired directly into `TerminalView` until plan 3's keymap replaces them:**
  - Copy: ⌘C on macOS, Ctrl+Shift+C on Linux/Windows.
  - Paste: ⌘V on macOS, Ctrl+Shift+V on Linux/Windows.
  - Previous/next prompt: ⌘↑/⌘↓ on macOS, Ctrl+Shift+↑/↓ on Linux/Windows.
  - Scroll one page: Shift+PageUp/PageDown.
  - Open link: ⌘-click on macOS, Ctrl-click on Linux/Windows.
- Option+Left/Right keep sending `ESC[1;3D` / `ESC[1;3C` (user decision, 2026-09-10) — no change to `KeyEncoder`.

## Scope of this plan

In scope (spec §4.1 exit message, §4.3 mouse/paste/wheel, §4.4, §4.5 search API, §4.6), plus the plan 1 final-review items routed here:
- DECSCUSR 0 restores the configured cursor.
- An in-pane exit message replaces "exit the app when the shell exits".
- A real Nerd Font fallback test.
- A font-change run split test.
- A lone surrogate renders as U+FFFD.
- An underline-cursor pixel test.

Deliberately not here:
- **Plan 3:** the find bar UI and the context menu, and a configurable keymap (the shortcuts above are hard-wired until then).
- **Later list:** autoscroll while drag-selecting past the edge, and a draggable scrollbar (the scroll indicator is display-only).
- **Accepted limit:** search matches do not span soft-wrapped rows (matching is per physical row).

## File Structure

```
moray-terminal/src/main/java/dev/moray/terminal/
  ShellIntegrationFilter.java    NEW  streaming rewrite: OSC 7 / OSC 133 / DECSCUSR 0 → OSC 1341 custom commands
  ShellIntegrationConnector.java NEW  TtyConnector wrapper that runs the filter on everything read
  SessionDisplay.java            MOD  resetCursorShape()
  TerminalSession.java           MOD  custom commands, absolute rows, working directory, prompt marks, snapshots at a
                                      top row, exit message, selection text, search, mouse, paste, links
  ScreenSnapshot.java            MOD  firstRow, scrollOffset, historyLines, alternateBuffer; FOLLOW_OUTPUT
  Selection.java                 NEW  stream/block selection over absolute rows
  SelectionText.java             NEW  selection → text (joins soft wraps, keeps wide chars, trims trailing spaces)
  WordBoundaries.java            NEW  word range for double-click
  RowText.java                   NEW  a row's text with the column of every character
  TerminalSearch.java            NEW  plain/regex, case-sensitive or not, over rows
  UriLink.java                   NEW  OSC 8 link target stored in JediTerm's HyperlinkStyle
  LinkDetector.java              NEW  plain-text URL at a column
  Palette.java                   MOD  selection colour
  CellStyle.java                 MOD  hyperlinks are underlined
  TerminalPainter.java           MOD  highlights (selection, matches), scroll indicator
  Viewport.java                  NEW  follow output or stay anchored at an absolute top row
  MouseRouting.java              NEW  which action a mouse event means (report / select / scroll / arrows / link)
  FindResult.java                NEW  public result of a find: count, current, error
  TerminalOptions.java           MOD  copyOnSelect
  TerminalView.java              MOD  scrolling, mouse, selection, clipboard, links, find, prompt jumps, exit
  RunBuilder.java                MOD  lone surrogates render as U+FFFD
moray-app/src/main/java/dev/moray/app/Main.java   MOD  close the window on the view's close request
docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md  MOD  record findings (§11)
```

Tests live next to the existing ones in `moray-terminal/src/test/java/dev/moray/terminal/` and reuse `FakeConnector` and `Await`.

---

### Task 1: The shell-integration filter

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/ShellIntegrationFilter.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/ShellIntegrationFilterTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces (package-private): `final class ShellIntegrationFilter` with `static final String PREFIX = "\033]1341;moray;"`, `void filter(char[] input, int offset, int length, StringBuilder out)`, `void finish(StringBuilder out)`.

Rewrites (everything else passes through unchanged):
- `ESC ] 7 ; data` (terminated by `BEL` or `ESC \`) → `ESC ] 1341 ; moray ; cwd ; data BEL`
- `ESC ] 133 ; data` (terminated by `BEL` or `ESC \`) → `ESC ] 1341 ; moray ; mark ; data BEL`
- `ESC [ 0 SP q` and `ESC [ SP q` → unchanged, followed by `ESC ] 1341 ; moray ; cursor-reset BEL`

Matching rules:
- A sequence split across reads is held until it completes.
- A held OSC longer than 4096 characters is released unchanged.
- A held prefix that turns out not to be one of these is released unchanged. If it ends in `ESC`, that `ESC` is kept as the possible start of a new sequence.

- [ ] **Step 1: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/ShellIntegrationFilterTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShellIntegrationFilterTest {
    private static final String P = ShellIntegrationFilter.PREFIX;

    @Test
    void plainTextPassesThrough() {
        assertThat(run("hello\r\nworld")).isEqualTo("hello\r\nworld");
    }

    @Test
    void osc7WithBellBecomesACwdCommand() {
        assertThat(run("a\033]7;file://host/tmp\007b")).isEqualTo("a" + P + "cwd;file://host/tmp\007b");
    }

    @Test
    void osc7WithStringTerminatorBecomesACwdCommand() {
        assertThat(run("\033]7;file:///x\033\\")).isEqualTo(P + "cwd;file:///x\007");
    }

    @Test
    void osc133BecomesAMarkCommand() {
        assertThat(run("\033]133;A\007$ \033]133;D;0\007"))
            .isEqualTo(P + "mark;A\007$ " + P + "mark;D;0\007");
    }

    @Test
    void otherOscSequencesPassUnchanged() {
        String input = "\033]0;title\007\033]1341;other\007\033]13;x\007\033]8;;http://a\007link\033]8;;\007";
        assertThat(run(input)).isEqualTo(input);
    }

    @Test
    void cursorStyleResetIsFollowedByACursorResetCommand() {
        assertThat(run("\033[0 q")).isEqualTo("\033[0 q" + P + "cursor-reset\007");
        assertThat(run("\033[ q")).isEqualTo("\033[ q" + P + "cursor-reset\007");
    }

    @Test
    void otherCsiSequencesPassUnchanged() {
        String input = "\033[2 q\033[0m\033[1;31mX\033[?1049h";
        assertThat(run(input)).isEqualTo(input);
    }

    @Test
    void sequencesSplitAcrossReadsAreStillRewritten() {
        String input = "x\033]7;file:///tmp\033\\y\033[0 qz";
        String expected = "x" + P + "cwd;file:///tmp\007y\033[0 q" + P + "cursor-reset\007z";

        assertThat(run(input)).isEqualTo(expected);
        assertThat(runOneCharAtATime(input)).isEqualTo(expected);
    }

    @Test
    void escapeInsideOscDataThatIsNotATerminatorPassesUnchanged() {
        String input = "\033]7;ab\033[0mc";
        assertThat(run(input)).isEqualTo(input);
    }

    @Test
    void overlongOscPassesUnchanged() {
        String input = "\033]7;" + "x".repeat(5000) + "\007";
        assertThat(run(input)).isEqualTo(input);
    }

    @Test
    void doubledEscapeKeepsLookingForASequence() {
        assertThat(run("\033\033]7;x\007")).isEqualTo("\033" + P + "cwd;x\007");
    }

    @Test
    void finishReleasesAnIncompleteSequence() {
        ShellIntegrationFilter filter = new ShellIntegrationFilter();
        StringBuilder out = new StringBuilder();
        char[] input = "\033]7;file".toCharArray();

        filter.filter(input, 0, input.length, out);
        assertThat(out.toString()).isEmpty();

        filter.finish(out);
        assertThat(out.toString()).isEqualTo("\033]7;file");
    }

    private static String run(String input) {
        ShellIntegrationFilter filter = new ShellIntegrationFilter();
        StringBuilder out = new StringBuilder();
        char[] chars = input.toCharArray();
        filter.filter(chars, 0, chars.length, out);
        filter.finish(out);
        return out.toString();
    }

    private static String runOneCharAtATime(String input) {
        ShellIntegrationFilter filter = new ShellIntegrationFilter();
        StringBuilder out = new StringBuilder();
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            filter.filter(chars, i, 1, out);
        }
        filter.finish(out);
        return out.toString();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.ShellIntegrationFilterTest`
Expected: FAIL — compilation error, `cannot find symbol: class ShellIntegrationFilter`.

- [ ] **Step 3: Implement `ShellIntegrationFilter`**

`moray-terminal/src/main/java/dev/moray/terminal/ShellIntegrationFilter.java`:
```java
package dev.moray.terminal;

/**
 * Rewrites the shell-integration sequences JediTerm ignores into its OSC 1341 custom commands,
 * which JediTerm hands to {@code TerminalCustomCommandListener}s in emulator order:
 * <pre>
 *   OSC 7 ; data ST        →  OSC 1341 ; moray ; cwd ; data BEL
 *   OSC 133 ; data ST      →  OSC 1341 ; moray ; mark ; data BEL
 *   CSI 0 SP q, CSI SP q   →  unchanged, then OSC 1341 ; moray ; cursor-reset BEL
 * </pre>
 * Everything else passes through unchanged. Reader thread only.
 */
final class ShellIntegrationFilter {
    static final String PREFIX = "\033]1341;moray;";

    private static final char ESC = '\033';
    private static final char BEL = '\007';
    private static final int MAX_HELD = 4096;

    private enum State { TEXT, ESCAPE, OSC_NUMBER, OSC_DATA, OSC_DATA_ESCAPE, CSI, CSI_ZERO, CSI_SPACE }

    private final StringBuilder held = new StringBuilder();
    private final StringBuilder oscNumber = new StringBuilder();
    private State state = State.TEXT;
    private String command;
    private int dataStart;

    void filter(char[] input, int offset, int length, StringBuilder out) {
        for (int i = offset; i < offset + length; i++) {
            accept(input[i], out);
        }
    }

    /** End of stream: releases anything still held, unchanged. */
    void finish(StringBuilder out) {
        out.append(held);
        reset();
    }

    private void accept(char c, StringBuilder out) {
        if (state == State.TEXT) {
            if (c == ESC) {
                held.append(c);
                state = State.ESCAPE;
            } else {
                out.append(c);
            }
            return;
        }
        held.append(c);
        switch (state) {
            case ESCAPE -> {
                if (c == ']') {
                    oscNumber.setLength(0);
                    state = State.OSC_NUMBER;
                } else if (c == '[') {
                    state = State.CSI;
                } else {
                    pass(out);
                }
            }
            case OSC_NUMBER -> {
                if (c >= '0' && c <= '9') {
                    oscNumber.append(c);
                    String number = oscNumber.toString();
                    if (!"7".startsWith(number) && !"133".startsWith(number)) {
                        pass(out);
                    }
                } else if (c == ';' && (isNumber("7") || isNumber("133"))) {
                    command = isNumber("7") ? "cwd" : "mark";
                    dataStart = held.length();
                    state = State.OSC_DATA;
                } else {
                    pass(out);
                }
            }
            case OSC_DATA -> {
                if (c == BEL) {
                    complete(out, held.substring(dataStart, held.length() - 1));
                } else if (c == ESC) {
                    state = State.OSC_DATA_ESCAPE;
                } else if (held.length() > MAX_HELD) {
                    pass(out);
                }
            }
            case OSC_DATA_ESCAPE -> {
                if (c == '\\') {
                    complete(out, held.substring(dataStart, held.length() - 2));
                } else {
                    pass(out);
                }
            }
            case CSI -> {
                if (c == '0') {
                    state = State.CSI_ZERO;
                } else if (c == ' ') {
                    state = State.CSI_SPACE;
                } else {
                    pass(out);
                }
            }
            case CSI_ZERO -> {
                if (c == ' ') {
                    state = State.CSI_SPACE;
                } else {
                    pass(out);
                }
            }
            case CSI_SPACE -> {
                if (c == 'q') {
                    out.append(held).append(PREFIX).append("cursor-reset").append(BEL);
                    reset();
                } else {
                    pass(out);
                }
            }
            default -> throw new IllegalStateException("unexpected state " + state);
        }
    }

    private boolean isNumber(String number) {
        return oscNumber.toString().equals(number);
    }

    private void complete(StringBuilder out, String data) {
        out.append(PREFIX).append(command).append(';').append(data).append(BEL);
        reset();
    }

    /** Not one of ours: release the held characters unchanged, keeping a trailing ESC as a possible new start. */
    private void pass(StringBuilder out) {
        int last = held.length() - 1;
        if (last > 0 && held.charAt(last) == ESC) {
            out.append(held, 0, last);
            reset();
            held.append(ESC);
            state = State.ESCAPE;
        } else {
            out.append(held);
            reset();
        }
    }

    private void reset() {
        held.setLength(0);
        state = State.TEXT;
        command = null;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.ShellIntegrationFilterTest`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add moray-terminal/src
git commit -m "Add shell-integration filter that turns OSC 7/133 and DECSCUSR 0 into custom commands"
```

### Task 2: Shell integration in the session

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/ShellIntegrationConnector.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/SessionDisplay.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/ShellIntegrationConnectorTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/ShellIntegrationSessionTest.java`

**Interfaces:**
- Consumes: `ShellIntegrationFilter` (Task 1); `FakeConnector` (`feed`, `finish`, `written`, `lastResize`) and `Await.until` from plan 1's tests.
- Produces:
  - `final class ShellIntegrationConnector implements TtyConnector` (package-private) — `ShellIntegrationConnector(TtyConnector inner)`.
  - `SessionDisplay.resetCursorShape()` (package-private) — sets the application cursor shape back to `null` and notifies.
  - On `TerminalSession`:
    - `Listener.workingDirectoryChanged(Path directory)` — a new default method.
    - Public: `Optional<Path> workingDirectory()`.
    - Package-private: `List<Long> promptRows()`, `String lineText(long absoluteRow)`, `static Optional<Path> directoryFromUri(String uri)`.
    - Private helpers used by later tasks: `long absoluteRow(int bufferRow)` and `TerminalLine lineAtLocked(long absoluteRow)`, both called with the buffer lock held; and the `volatile long discardedLines` field.
- Every connector the session is given is wrapped in `ShellIntegrationConnector`, including `FakeConnector` in tests.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/ShellIntegrationConnectorTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ShellIntegrationConnectorTest {

    @Test
    void rewritesWhatItReads() throws Exception {
        FakeConnector inner = new FakeConnector();
        ShellIntegrationConnector connector = new ShellIntegrationConnector(inner);

        inner.feed("\033]7;file:///tmp\007x");
        inner.finish();

        assertThat(readAll(connector, 5)).isEqualTo(ShellIntegrationFilter.PREFIX + "cwd;file:///tmp\007x");
    }

    @Test
    void releasesAnIncompleteSequenceAtEndOfStream() throws Exception {
        FakeConnector inner = new FakeConnector();
        ShellIntegrationConnector connector = new ShellIntegrationConnector(inner);

        inner.feed("ok\033]7;par");
        inner.finish();

        assertThat(readAll(connector, 64)).isEqualTo("ok\033]7;par");
    }

    @Test
    void delegatesWritesAndResizes() throws Exception {
        FakeConnector inner = new FakeConnector();
        ShellIntegrationConnector connector = new ShellIntegrationConnector(inner);

        connector.write("ls\r");
        connector.resize(new TermSize(30, 5));

        assertThat(inner.written()).isEqualTo("ls\r");
        assertThat(inner.lastResize()).isEqualTo(new TermSize(30, 5));
    }

    private static String readAll(TtyConnector connector, int chunkSize) throws IOException {
        char[] buffer = new char[chunkSize];
        StringBuilder text = new StringBuilder();
        int count;
        while ((count = connector.read(buffer, 0, buffer.length)) != -1) {
            text.append(buffer, 0, count);
        }
        return text.toString();
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/ShellIntegrationSessionTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ShellIntegrationSessionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // POSIX path in the URI
    void osc7SetsTheWorkingDirectory() throws Exception {
        AtomicReference<Path> reported = new AtomicReference<>();
        session.addListener(new TerminalSession.Listener() {
            @Override
            public void workingDirectoryChanged(Path directory) {
                reported.set(directory);
            }
        });

        connector.feed("\033]7;file://host/Users/me/My%20Dir\007");

        Await.until(() -> session.workingDirectory().isPresent(), "working directory from OSC 7");
        assertThat(session.workingDirectory()).contains(Path.of("/Users/me/My Dir"));
        assertThat(reported.get()).isEqualTo(Path.of("/Users/me/My Dir"));
    }

    @Test
    void onlyFileUrisWithAPathAreWorkingDirectories() {
        assertThat(TerminalSession.directoryFromUri("https://example.com/x")).isEmpty();
        assertThat(TerminalSession.directoryFromUri("not a uri")).isEmpty();
        assertThat(TerminalSession.directoryFromUri("file://host")).isEmpty();
    }

    @Test
    void onlyPromptStartMarksAreRecordedWithTheirRows() throws Exception {
        connector.feed("\033]133;A\007$ \033]133;B\007ls\r\n\033]133;C\007out\r\n\033]133;D;0\007\033]133;A\007$ ");

        Await.until(() -> session.promptRows().size() == 2, "two prompt marks");
        assertThat(session.promptRows()).containsExactly(0L, 2L);
    }

    @Test
    void promptRowsStayWithTheirLinesAsOutputScrolls() throws Exception {
        connector.feed("\033]133;A\007p0\r\n" + "l\r\n".repeat(9) + "end");

        Await.until(() -> "end".equals(session.snapshot().lineText(3)), "scrolled output");
        long prompt = session.promptRows().getFirst();
        assertThat(prompt).isZero();
        assertThat(session.lineText(prompt)).isEqualTo("p0");
    }

    @Test
    void cursorStyleResetRestoresTheConfiguredCursor() throws Exception {
        connector.feed("\033[6 q");
        Await.until(() -> session.display().cursorShape() == CursorShape.STEADY_VERTICAL_BAR, "beam requested");

        connector.feed("\033[0 q");

        Await.until(() -> session.display().cursorShape() == null, "back to the configured cursor");
    }

    @Test
    void customCommandsFromOtherToolsAreIgnored() throws Exception {
        connector.feed("\033]1341;other;cwd;file:///tmp\007done");

        Await.until(() -> "done".equals(session.snapshot().lineText(0)), "text after the command");
        assertThat(session.workingDirectory()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.ShellIntegrationConnectorTest' --tests 'dev.moray.terminal.ShellIntegrationSessionTest'`
Expected: FAIL — compilation errors (`ShellIntegrationConnector`, `workingDirectory()`, `promptRows()`, `lineText(long)`, `directoryFromUri`, `Listener.workingDirectoryChanged`).

- [ ] **Step 3: Implement `ShellIntegrationConnector`**

`moray-terminal/src/main/java/dev/moray/terminal/ShellIntegrationConnector.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;

import java.io.IOException;

/** Runs everything read from the program through a {@link ShellIntegrationFilter}; all else is delegated. */
final class ShellIntegrationConnector implements TtyConnector {
    private final TtyConnector inner;
    private final ShellIntegrationFilter filter = new ShellIntegrationFilter();
    private final StringBuilder pending = new StringBuilder();
    private final char[] chunk = new char[8192];
    private boolean innerEnded;

    ShellIntegrationConnector(TtyConnector inner) {
        this.inner = inner;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        while (pending.isEmpty()) {
            if (innerEnded) {
                return -1;
            }
            int count = inner.read(chunk, 0, chunk.length);
            if (count < 0) {
                innerEnded = true;
                filter.finish(pending);
            } else {
                filter.filter(chunk, 0, count, pending);
            }
        }
        int count = Math.min(length, pending.length());
        pending.getChars(0, count, buf, offset);
        pending.delete(0, count);
        return count;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        inner.write(bytes);
    }

    @Override
    public void write(String string) throws IOException {
        inner.write(string);
    }

    @Override
    public boolean isConnected() {
        return inner.isConnected();
    }

    @Override
    public void resize(TermSize size) {
        inner.resize(size);
    }

    @Override
    public int waitFor() throws InterruptedException {
        return inner.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return !pending.isEmpty() || inner.ready();
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public void close() {
        inner.close();
    }
}
```

- [ ] **Step 4: Add `resetCursorShape` to `SessionDisplay`**

In `moray-terminal/src/main/java/dev/moray/terminal/SessionDisplay.java`, add this method directly after `setCursorShape(CursorShape shape)`:
```java
    /** DECSCUSR 0 (via the shell-integration filter): back to the configured cursor. */
    void resetCursorShape() {
        cursorShape = null;
        onCursorChange.run();
    }
```

- [ ] **Step 5: Extend `TerminalSession`**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`:

1. Add these imports:
```java
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TextBufferChangesListener;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
```

2. In `Listener`, add after `bell()`:
```java
        default void workingDirectoryChanged(Path directory) {
        }
```

3. Add these fields after `private final CompletableFuture<Integer> exit = new CompletableFuture<>();`:
```java
    private final List<Long> promptRows = new CopyOnWriteArrayList<>();
    /** Lines dropped off the top of the scrollback so far; the base of absolute row numbers. */
    private volatile long discardedLines;
    private volatile Path workingDirectory;
```

4. In the package-private constructor, replace `this.connector = connector;` with:
```java
        this.connector = new ShellIntegrationConnector(connector);
```
and add these lines at the end of the constructor, after `buffer.addModelListener(...)`:
```java
        terminal.addCustomCommandListener(this::onCustomCommand);
        buffer.addChangesListener(new TextBufferChangesListener() {
            @Override
            public void linesDiscardedFromHistory(List<TerminalLine> lines) {
                discardedLines += lines.size(); // reader thread, under the buffer lock
            }

            @Override
            public void historyCleared() {
                promptRows.clear();
            }
        });
```

5. Add these methods after `exitFuture()`:
```java
    /** The directory the shell last reported with OSC 7, if any. */
    public Optional<Path> workingDirectory() {
        return Optional.ofNullable(workingDirectory);
    }
```

6. Add these package-private and private methods at the end of the class (before the final `}`):
```java
    /** Absolute rows of the prompts the shell marked with OSC 133;A that are still in the scrollback, oldest first. */
    List<Long> promptRows() {
        long oldest = discardedLines;
        return promptRows.stream().filter(row -> row >= oldest).toList();
    }

    /** The text of the line at an absolute row, or null when it is no longer in the scrollback. */
    String lineText(long absoluteRow) {
        buffer.lock();
        try {
            TerminalLine line = lineAtLocked(absoluteRow);
            return line == null ? null : line.getText();
        } finally {
            buffer.unlock();
        }
    }

    /**
     * The absolute row of a buffer row (0 = top of the live screen, negative = scrollback). An absolute row stays
     * attached to its line while output scrolls. Call with the buffer lock held.
     */
    private long absoluteRow(int bufferRow) {
        return discardedLines + buffer.getHistoryLinesCount() + bufferRow;
    }

    /** The line at an absolute row, or null outside the scrollback and screen. Call with the buffer lock held. */
    private TerminalLine lineAtLocked(long absoluteRow) {
        int history = buffer.getHistoryLinesCount();
        long bufferRow = absoluteRow - discardedLines - history;
        if (bufferRow < -history || bufferRow >= buffer.getHeight()) {
            return null;
        }
        return buffer.getLine((int) bufferRow);
    }

    private void onCustomCommand(List<String> args) {
        if (args.size() < 2 || !"moray".equals(args.get(0))) {
            return;
        }
        switch (args.get(1)) {
            case "cwd" -> directoryFromUri(String.join(";", args.subList(2, args.size()))).ifPresent(directory -> {
                workingDirectory = directory;
                listeners.forEach(l -> l.workingDirectoryChanged(directory));
            });
            case "mark" -> {
                if (args.size() > 2 && "A".equals(args.get(2))) {
                    recordPrompt();
                }
            }
            case "cursor-reset" -> display.resetCursorShape();
            default -> {
                // A command from a newer Moray shell-integration script; nothing to do.
            }
        }
    }

    private void recordPrompt() {
        buffer.lock();
        try {
            long row = absoluteRow(terminal.getCursorY() - 1);
            if (promptRows.isEmpty() || promptRows.getLast() != row) {
                promptRows.add(row);
            }
        } finally {
            buffer.unlock();
        }
    }

    /** The local path of an OSC 7 {@code file://host/path} URI; the host is ignored. */
    static Optional<Path> directoryFromUri(String uri) {
        try {
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (!"file".equalsIgnoreCase(parsed.getScheme()) || path == null || path.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Path.of(new URI("file", null, path, null)));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.ShellIntegrationConnectorTest' --tests 'dev.moray.terminal.ShellIntegrationSessionTest'`
Expected: PASS (3 + 6 tests; `osc7SetsTheWorkingDirectory` skipped on Windows).

- [ ] **Step 7: Run the module tests (the wrapper now sits under every session)**

Run: `./gradlew :moray-terminal:check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add moray-terminal/src
git commit -m "Track working directory, prompt marks and cursor reset through shell integration"
```

### Task 3: Scrollback snapshots and the exit message

**Files:**
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/ScreenSnapshot.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/ScrollbackTest.java`

**Interfaces:**
- Consumes: `discardedLines` and the buffer lock inside `TerminalSession` (Task 2).
- Produces:
  - `ScreenSnapshot` gains four components after `cursorShape`: `long firstRow` (absolute row of the top visible line), `int scrollOffset` (lines scrolled back from the live screen), `int historyLines` (scrollback available to scroll into; 0 in the alternate screen), `boolean alternateBuffer`.
  - `static final long FOLLOW_OUTPUT = Long.MAX_VALUE` — pass it to show the live screen.
  - `cursorRow` is now a viewport row: it moves down by `scrollOffset` and can be ≥ `height` when the cursor is out of view.
  - `TerminalSession.snapshot(long topRow)` (package-private) shows the rows starting at an absolute `topRow`, clamped to the available scrollback; `snapshot()` still means `snapshot(FOLLOW_OUTPUT)`.
  - When the program exits, the session writes `\r\n[process exited with code N]` to the screen *before* `exitFuture()` completes.

- [ ] **Step 1: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/ScrollbackTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ScrollbackTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 10, 3, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void followingShowsTheLiveScreen() throws Exception {
        fiveLines();

        ScreenSnapshot snapshot = session.snapshot();

        assertThat(texts(snapshot)).containsExactly("c", "d", "e");
        assertThat(snapshot.firstRow()).isEqualTo(2);
        assertThat(snapshot.scrollOffset()).isZero();
        assertThat(snapshot.historyLines()).isEqualTo(2);
    }

    @Test
    void anchoredTopRowShowsScrollback() throws Exception {
        fiveLines();

        ScreenSnapshot snapshot = session.snapshot(0);

        assertThat(texts(snapshot)).containsExactly("a", "b", "c");
        assertThat(snapshot.firstRow()).isZero();
        assertThat(snapshot.scrollOffset()).isEqualTo(2);
        assertThat(snapshot.cursorRow()).isEqualTo(4); // the live cursor row 2, two rows below the viewport
    }

    @Test
    void topRowIsClampedToTheAvailableScrollback() throws Exception {
        fiveLines();

        assertThat(session.snapshot(-10).firstRow()).isZero();
        assertThat(session.snapshot(99).scrollOffset()).isZero();
    }

    @Test
    void anAnchoredViewKeepsItsLinesWhileOutputArrives() throws Exception {
        fiveLines();
        assertThat(texts(session.snapshot(1))).containsExactly("b", "c", "d");

        connector.feed("\r\nf\r\ng");
        Await.until(() -> "g".equals(session.snapshot().lineText(2)), "more output");

        ScreenSnapshot anchored = session.snapshot(1);
        assertThat(texts(anchored)).containsExactly("b", "c", "d");
        assertThat(anchored.scrollOffset()).isEqualTo(3);
    }

    @Test
    void theAlternateScreenHasNoScrollback() throws Exception {
        fiveLines();
        connector.feed("\033[?1049h");
        Await.until(() -> session.snapshot().alternateBuffer(), "alternate screen");

        ScreenSnapshot snapshot = session.snapshot(0);

        assertThat(snapshot.scrollOffset()).isZero();
        assertThat(snapshot.historyLines()).isZero();
    }

    @Test
    void theExitMessageIsWrittenToTheScreen() throws Exception {
        connector.feed("bye");
        connector.finish();

        assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isZero();
        assertThat(String.join("", texts(session.snapshot()))).contains("[process exited with code 0]");
    }

    private void fiveLines() throws Exception {
        connector.feed("a\r\nb\r\nc\r\nd\r\ne");
        Await.until(() -> "e".equals(session.snapshot().lineText(2)), "five lines on a three-row screen");
    }

    private static java.util.List<String> texts(ScreenSnapshot snapshot) {
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (int row = 0; row < snapshot.height(); row++) {
            texts.add(snapshot.lineText(row));
        }
        return texts;
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.ScrollbackTest`
Expected: FAIL — compilation errors (`firstRow()`, `scrollOffset()`, `historyLines()`, `alternateBuffer()`, `snapshot(long)`).

- [ ] **Step 3: Replace `ScreenSnapshot`**

Replace the whole of `moray-terminal/src/main/java/dev/moray/terminal/ScreenSnapshot.java` with:
```java
package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * The visible rows, copied under the buffer lock so painting never races the emulator. Rows start at the absolute row
 * {@code firstRow}; {@code cursorRow} is a viewport row (≥ {@code height} when the cursor is scrolled out of view).
 */
record ScreenSnapshot(int width, int height, List<TerminalLine> lines,
                      int cursorColumn, int cursorRow, boolean cursorVisible, CursorShape cursorShape,
                      long firstRow, int scrollOffset, int historyLines, boolean alternateBuffer) {

    /** Requests the live screen, following new output. */
    static final long FOLLOW_OUTPUT = Long.MAX_VALUE;

    /** Call with the buffer lock held, so {@code discardedLines} and the buffer agree. */
    static ScreenSnapshot capture(TerminalTextBuffer buffer, JediTerminal terminal, SessionDisplay display,
                                  long discardedLines, long requestedTopRow) {
        buffer.lock();
        try {
            int width = buffer.getWidth();
            int height = buffer.getHeight();
            boolean alternate = buffer.isUsingAlternateBuffer();
            int history = buffer.getHistoryLinesCount();
            int scrollable = alternate ? 0 : history;
            long liveTop = discardedLines + history;
            int offset = requestedTopRow == FOLLOW_OUTPUT
                ? 0
                : (int) Math.max(0, Math.min(scrollable, liveTop - requestedTopRow));
            List<TerminalLine> lines = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                lines.add(buffer.getLine(row - offset).copy());
            }
            return new ScreenSnapshot(width, height, lines,
                terminal.getCursorX() - 1, terminal.getCursorY() - 1 + offset,
                display.cursorVisible(), display.cursorShape(),
                liveTop - offset, offset, scrollable, alternate);
        } finally {
            buffer.unlock();
        }
    }

    String lineText(int row) {
        return lines.get(row).getText();
    }
}
```

- [ ] **Step 4: Update `TerminalSession`**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`:

1. Add the import `import com.jediterm.terminal.ArrayTerminalDataStream;`.

2. Replace the existing `snapshot()` method with:
```java
    ScreenSnapshot snapshot() {
        return snapshot(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    /** The rows starting at an absolute top row (clamped to the scrollback), or the live screen for FOLLOW_OUTPUT. */
    ScreenSnapshot snapshot(long topRow) {
        buffer.lock();
        try {
            return ScreenSnapshot.capture(buffer, terminal, display, discardedLines, topRow);
        } finally {
            buffer.unlock();
        }
    }
```

3. In `readLoop()`, add a call to `writeExitMessage(code);` on the line directly before `exit.complete(code);`, and add this method after `readLoop()`:
```java
    private void writeExitMessage(int code) {
        char[] message = ("\r\n[process exited with code " + code + "]").toCharArray();
        JediEmulator emulator = new JediEmulator(new ArrayTerminalDataStream(message), terminal);
        try {
            while (emulator.hasNext()) {
                emulator.next();
            }
        } catch (IOException endOfMessage) {
            // ArrayTerminalDataStream signals its end with EOF.
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.ScrollbackTest`
Expected: PASS (6 tests).

- [ ] **Step 6: Run the module tests**

Run: `./gradlew :moray-terminal:check`
Expected: BUILD SUCCESSFUL (the painter and view compile unchanged: they read `ScreenSnapshot` fields by name).

- [ ] **Step 7: Commit**

```bash
git add moray-terminal/src
git commit -m "Snapshot any scrollback position by absolute row and write an exit message"
```

### Task 4: Selections

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/Selection.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/SelectionText.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/WordBoundaries.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/SelectionTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/SelectionTextTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/WordBoundariesTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/SelectionSessionTest.java`

**Interfaces:**
- Consumes: `RunBuilder.readCells(TerminalLine, int, char[], TextStyle[])` (plan 1); `TerminalSession.lineAtLocked(long)` and the buffer lock (Task 2).
- Produces (all package-private):
  - `record Selection(long anchorRow, int anchorColumn, long focusRow, int focusColumn, boolean block)` — absolute rows; both ends inclusive; `static Selection at(long row, int column, boolean block)`, `Selection withFocus(long row, int column)`, `long startRow()`, `long endRow()`, `int startColumn()`, `int endColumn()`, `boolean contains(long row, int column)`, `int[] columnsOn(long row, int width)` (inclusive `{from, to}`, or `null` when the row is not selected).
  - `final class SelectionText` — `static String extract(Selection selection, LongFunction<TerminalLine> lineAt, int width)`.
  - `final class WordBoundaries` — `static int[] wordAt(TerminalLine line, int width, int column)` (inclusive `{from, to}`).
  - On `TerminalSession`: `String text(Selection)`, `Selection wordSelection(long row, int column)`, `Selection lineSelection(long row)`.

Rules:
- **Text extraction:** each row contributes its selected cells, skipping `CharUtils.DWC` continuation cells. A stream selection joins a soft-wrapped row to the next without a newline; every other row break becomes `\n`. Trailing spaces are trimmed from each row that ends a line. Rows no longer in the scrollback (`lineAt` returns `null`) are skipped.
- **Word characters:** letters, digits, surrogates and `_-./~:@%+=?&#`, so paths and URLs select as one word. A non-word cell selects only itself.
- **Line selection:** covers the whole logical line, including the soft-wrapped rows before and after.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/SelectionTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTest {

    @Test
    void backwardsDragsAreNormalized() {
        Selection selection = new Selection(5, 3, 2, 7, false);

        assertThat(selection.startRow()).isEqualTo(2);
        assertThat(selection.startColumn()).isEqualTo(7);
        assertThat(selection.endRow()).isEqualTo(5);
        assertThat(selection.endColumn()).isEqualTo(3);
    }

    @Test
    void sameRowBackwardsDragIsNormalized() {
        Selection selection = new Selection(0, 6, 0, 2, false);

        assertThat(selection.startColumn()).isEqualTo(2);
        assertThat(selection.endColumn()).isEqualTo(6);
    }

    @Test
    void streamSelectionContainsWholeMiddleRows() {
        Selection selection = new Selection(1, 4, 3, 2, false);

        assertThat(selection.contains(1, 3)).isFalse();
        assertThat(selection.contains(1, 4)).isTrue();
        assertThat(selection.contains(2, 0)).isTrue();
        assertThat(selection.contains(2, 79)).isTrue();
        assertThat(selection.contains(3, 2)).isTrue();
        assertThat(selection.contains(3, 3)).isFalse();
        assertThat(selection.contains(4, 0)).isFalse();
    }

    @Test
    void blockSelectionIsARectangle() {
        Selection selection = new Selection(1, 5, 3, 2, true);

        assertThat(selection.contains(2, 3)).isTrue();
        assertThat(selection.contains(2, 1)).isFalse();
        assertThat(selection.contains(2, 6)).isFalse();
        assertThat(selection.columnsOn(2, 80)).containsExactly(2, 5);
    }

    @Test
    void columnsOnGivesEachRowItsRange() {
        Selection selection = new Selection(1, 4, 3, 2, false);

        assertThat(selection.columnsOn(0, 10)).isNull();
        assertThat(selection.columnsOn(1, 10)).containsExactly(4, 9);
        assertThat(selection.columnsOn(2, 10)).containsExactly(0, 9);
        assertThat(selection.columnsOn(3, 10)).containsExactly(0, 2);
    }

    @Test
    void atIsOneCellAndWithFocusKeepsTheAnchor() {
        Selection selection = Selection.at(7, 3, false);
        assertThat(selection.contains(7, 3)).isTrue();
        assertThat(selection.contains(7, 4)).isFalse();

        Selection extended = selection.withFocus(8, 5);
        assertThat(extended.anchorRow()).isEqualTo(7);
        assertThat(extended.anchorColumn()).isEqualTo(3);
        assertThat(extended.focusRow()).isEqualTo(8);
        assertThat(extended.focusColumn()).isEqualTo(5);
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/SelectionTextTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTextTest {

    @Test
    void aRowKeepsItsSelectedCharacters() {
        assertThat(extract(new Selection(0, 0, 0, 4, false), 12, line("hello world", false))).isEqualTo("hello");
    }

    @Test
    void trailingSpacesAreTrimmed() {
        assertThat(extract(new Selection(0, 0, 0, 7, false), 8, line("ab   ", false))).isEqualTo("ab");
    }

    @Test
    void rowsAreJoinedWithNewlines() {
        assertThat(extract(new Selection(0, 0, 1, 4, false), 5, line("one", false), line("two", false)))
            .isEqualTo("one\ntwo");
    }

    @Test
    void softWrappedRowsAreJoinedWithoutANewline() {
        assertThat(extract(new Selection(0, 2, 1, 1, false), 5, line("01234", true), line("56", false)))
            .isEqualTo("23456");
    }

    @Test
    void wideCharactersAreKeptWhole() {
        assertThat(extract(new Selection(0, 0, 0, 2, false), 4, line("日\uE000x", false))).isEqualTo("日x");
    }

    @Test
    void blockSelectionTakesTheSameColumnsFromEveryRow() {
        assertThat(extract(new Selection(0, 1, 1, 2, true), 4, line("abcd", false), line("efgh", false)))
            .isEqualTo("bc\nfg");
    }

    @Test
    void rowsNoLongerInTheScrollbackAreSkipped() {
        TerminalLine kept = line("xyz", false);

        String text = SelectionText.extract(new Selection(0, 0, 1, 2, false), row -> row == 1 ? kept : null, 5);

        assertThat(text).isEqualTo("xyz");
    }

    private static TerminalLine line(String text, boolean wrapped) {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text)));
        line.setWrapped(wrapped);
        return line;
    }

    private static String extract(Selection selection, int width, TerminalLine... lines) {
        return SelectionText.extract(selection, row -> row >= 0 && row < lines.length ? lines[(int) row] : null, width);
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/WordBoundariesTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordBoundariesTest {

    @Test
    void selectsTheWordAroundTheColumn() {
        assertThat(WordBoundaries.wordAt(line("echo foo.bar baz"), 20, 7)).containsExactly(5, 11);
    }

    @Test
    void pathsAndUrlsAreOneWord() {
        assertThat(WordBoundaries.wordAt(line("see https://moray.dev/x?a=1 now"), 40, 12)).containsExactly(4, 26);
    }

    @Test
    void aSpaceSelectsOnlyItself() {
        assertThat(WordBoundaries.wordAt(line("echo foo"), 20, 4)).containsExactly(4, 4);
    }

    @Test
    void wideCharactersStayWhole() {
        assertThat(WordBoundaries.wordAt(line("日\uE000本\uE000"), 4, 1)).containsExactly(0, 3);
    }

    @Test
    void theColumnIsClampedToTheRow() {
        assertThat(WordBoundaries.wordAt(line("abc"), 5, 99)).containsExactly(4, 4);
    }

    private static TerminalLine line(String text) {
        return new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text)));
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/SelectionSessionTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionSessionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void textOfASelectionOnScreen() throws Exception {
        show("hello world", 0, "hello world");

        assertThat(session.text(new Selection(0, 0, 0, 4, false))).isEqualTo("hello");
    }

    @Test
    void wordSelectionFindsTheWord() throws Exception {
        show("echo foo.bar baz", 0, "echo foo.bar baz");

        assertThat(session.text(session.wordSelection(0, 7))).isEqualTo("foo.bar");
    }

    @Test
    void lineSelectionIncludesSoftWrappedRows() throws Exception {
        show("x".repeat(25) + "\r\nnext", 2, "next");

        assertThat(session.text(session.lineSelection(1))).isEqualTo("x".repeat(25));
    }

    @Test
    void selectionsStayWithTheirTextAfterItScrolls() throws Exception {
        show("keep\r\n" + "l\r\n".repeat(6) + "end", 3, "end");

        assertThat(session.text(new Selection(0, 0, 0, 3, false))).isEqualTo("keep");
    }

    private void show(String output, int row, String expected) throws Exception {
        connector.feed(output);
        Await.until(() -> expected.equals(session.snapshot().lineText(row)), "\"" + expected + "\" on row " + row);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.SelectionTest' --tests 'dev.moray.terminal.SelectionTextTest' --tests 'dev.moray.terminal.WordBoundariesTest' --tests 'dev.moray.terminal.SelectionSessionTest'`
Expected: FAIL — compilation errors for `Selection`, `SelectionText`, `WordBoundaries`, `text`, `wordSelection`, `lineSelection`.

- [ ] **Step 3: Implement `Selection`**

`moray-terminal/src/main/java/dev/moray/terminal/Selection.java`:
```java
package dev.moray.terminal;

/**
 * A selection over absolute rows (see {@code TerminalSession}) and columns, both ends inclusive. A stream selection
 * runs like text from its start cell to its end cell; a block selection is a rectangle.
 */
record Selection(long anchorRow, int anchorColumn, long focusRow, int focusColumn, boolean block) {

    static Selection at(long row, int column, boolean block) {
        return new Selection(row, column, row, column, block);
    }

    Selection withFocus(long row, int column) {
        return new Selection(anchorRow, anchorColumn, row, column, block);
    }

    long startRow() {
        return Math.min(anchorRow, focusRow);
    }

    long endRow() {
        return Math.max(anchorRow, focusRow);
    }

    /** First selected column on the start row (stream), or the left edge (block). */
    int startColumn() {
        if (block) {
            return Math.min(anchorColumn, focusColumn);
        }
        return anchorFirst() ? anchorColumn : focusColumn;
    }

    /** Last selected column on the end row (stream), or the right edge (block). */
    int endColumn() {
        if (block) {
            return Math.max(anchorColumn, focusColumn);
        }
        return anchorFirst() ? focusColumn : anchorColumn;
    }

    boolean contains(long row, int column) {
        if (row < startRow() || row > endRow()) {
            return false;
        }
        if (block) {
            return column >= startColumn() && column <= endColumn();
        }
        if (row == startRow() && column < startColumn()) {
            return false;
        }
        return row != endRow() || column <= endColumn();
    }

    /** The selected columns of a row as inclusive {from, to}, clipped to the width; null when the row is not selected. */
    int[] columnsOn(long row, int width) {
        if (row < startRow() || row > endRow()) {
            return null;
        }
        int from = block || row == startRow() ? startColumn() : 0;
        int to = block || row == endRow() ? endColumn() : width - 1;
        return new int[] {Math.max(0, from), Math.min(width - 1, to)};
    }

    private boolean anchorFirst() {
        return anchorRow < focusRow || (anchorRow == focusRow && anchorColumn <= focusColumn);
    }
}
```

- [ ] **Step 4: Implement `SelectionText` and `WordBoundaries`**

`moray-terminal/src/main/java/dev/moray/terminal/SelectionText.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

import java.util.function.LongFunction;

/** Turns a selection into the text a user expects to paste. */
final class SelectionText {
    private SelectionText() {
    }

    static String extract(Selection selection, LongFunction<TerminalLine> lineAt, int width) {
        StringBuilder out = new StringBuilder();
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        for (long row = selection.startRow(); row <= selection.endRow(); row++) {
            TerminalLine line = lineAt.apply(row);
            if (line == null) {
                continue;
            }
            RunBuilder.readCells(line, width, chars, styles);
            int[] columns = selection.columnsOn(row, width);
            StringBuilder rowText = new StringBuilder();
            for (int column = columns[0]; column <= columns[1]; column++) {
                if (chars[column] != CharUtils.DWC) {
                    rowText.append(chars[column]);
                }
            }
            boolean joinsNextRow = !selection.block() && row < selection.endRow() && line.isWrapped();
            if (!joinsNextRow) {
                stripTrailingSpaces(rowText);
            }
            out.append(rowText);
            if (row < selection.endRow() && !joinsNextRow) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static void stripTrailingSpaces(StringBuilder text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        text.setLength(end);
    }
}
```

`moray-terminal/src/main/java/dev/moray/terminal/WordBoundaries.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

/** What a double-click selects: a run of word characters, where paths and URLs count as one word. */
final class WordBoundaries {
    private static final String WORD_PUNCTUATION = "_-./~:@%+=?&#";

    private WordBoundaries() {
    }

    /** The word covering a column, as inclusive {from, to}; a non-word cell selects only itself. */
    static int[] wordAt(TerminalLine line, int width, int column) {
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        RunBuilder.readCells(line, width, chars, styles);
        int at = Math.max(0, Math.min(width - 1, column));
        if (chars[at] == CharUtils.DWC && at > 0) {
            at--;
        }
        if (!isWordChar(chars[at])) {
            return new int[] {at, at};
        }
        int from = at;
        while (from > 0 && (isWordChar(chars[from - 1]) || chars[from - 1] == CharUtils.DWC)) {
            from--;
        }
        int to = at;
        while (to < width - 1 && (isWordChar(chars[to + 1]) || chars[to + 1] == CharUtils.DWC)) {
            to++;
        }
        return new int[] {from, to};
    }

    static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || Character.isSurrogate(c) || WORD_PUNCTUATION.indexOf(c) >= 0;
    }
}
```

- [ ] **Step 5: Add selection queries to `TerminalSession`**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`, add these methods next to `lineText(long)`:
```java
    /** The text of a selection: soft-wrapped rows joined, wide characters whole, trailing spaces trimmed. */
    String text(Selection selection) {
        buffer.lock();
        try {
            return SelectionText.extract(selection, this::lineAtLocked, buffer.getWidth());
        } finally {
            buffer.unlock();
        }
    }

    /** The word at an absolute row and column, as a stream selection. */
    Selection wordSelection(long row, int column) {
        buffer.lock();
        try {
            TerminalLine line = lineAtLocked(row);
            if (line == null) {
                return Selection.at(row, column, false);
            }
            int[] word = WordBoundaries.wordAt(line, buffer.getWidth(), column);
            return new Selection(row, word[0], row, word[1], false);
        } finally {
            buffer.unlock();
        }
    }

    /** The whole logical line at an absolute row, soft-wrapped rows included. */
    Selection lineSelection(long row) {
        buffer.lock();
        try {
            long first = row;
            while (true) {
                TerminalLine above = lineAtLocked(first - 1);
                if (above == null || !above.isWrapped()) {
                    break;
                }
                first--;
            }
            long last = row;
            while (true) {
                TerminalLine line = lineAtLocked(last);
                if (line == null || !line.isWrapped() || lineAtLocked(last + 1) == null) {
                    break;
                }
                last++;
            }
            return new Selection(first, 0, last, buffer.getWidth() - 1, false);
        } finally {
            buffer.unlock();
        }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.SelectionTest' --tests 'dev.moray.terminal.SelectionTextTest' --tests 'dev.moray.terminal.WordBoundariesTest' --tests 'dev.moray.terminal.SelectionSessionTest'`
Expected: PASS (6 + 7 + 5 + 4 tests).

- [ ] **Step 7: Commit**

```bash
git add moray-terminal/src
git commit -m "Add stream and block selections with word, line and wrap-aware text extraction"
```

### Task 5: Search

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/RowText.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSearch.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/RowTextTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalSearchTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/SearchSessionTest.java`

**Interfaces:**
- Consumes: `RunBuilder.readCells` (plan 1); `absoluteRow(int)` and the buffer lock in `TerminalSession` (Task 2).
- Produces (package-private):
  - `record RowText(String text, int[] columns, int[] lastColumns)` with `static RowText of(TerminalLine line, int width)`. `text` has one character per cell, skipping `DWC` continuation cells. `columns[i]` is the first column of `text.charAt(i)` and `lastColumns[i]` its last (a wide character covers two). Task 7's `LinkDetector` reuses it.
  - `final class TerminalSearch`:
    - nested `record Match(long row, int startColumn, int endColumn)` (absolute row, inclusive columns)
    - `static Pattern pattern(String query, boolean regex, boolean caseSensitive)` (throws `PatternSyntaxException` for a bad regex)
    - `static List<Match> find(Pattern pattern, long firstRow, List<TerminalLine> lines, int width)`
  - `TerminalSession.search(String query, boolean regex, boolean caseSensitive)` returns matches in the scrollback and screen, oldest first; an empty query returns an empty list.

Matching is per physical row (a match never spans a soft wrap), and empty regex matches are skipped.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/RowTextTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RowTextTest {

    @Test
    void eachCharacterMapsToItsColumn() {
        RowText row = RowText.of(line("ab"), 4);

        assertThat(row.text()).isEqualTo("ab  ");
        assertThat(row.columns()).containsExactly(0, 1, 2, 3);
        assertThat(row.lastColumns()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void aWideCharacterCoversTwoColumns() {
        RowText row = RowText.of(line("日\uE000x"), 4);

        assertThat(row.text()).isEqualTo("日x ");
        assertThat(row.columns()).containsExactly(0, 2, 3);
        assertThat(row.lastColumns()).containsExactly(1, 2, 3);
    }

    private static TerminalLine line(String text) {
        return new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text)));
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/TerminalSearchTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalSearchTest {
    private static final int WIDTH = 20;

    @Test
    void caseInsensitiveSearchFindsEveryMatchWithAbsoluteRows() {
        assertThat(find("foo", false, false, 10, "Foo bar", "xx foo"))
            .containsExactly(new TerminalSearch.Match(10, 0, 2), new TerminalSearch.Match(11, 3, 5));
    }

    @Test
    void caseSensitiveSearch() {
        assertThat(find("foo", false, true, 10, "Foo bar", "xx foo"))
            .containsExactly(new TerminalSearch.Match(11, 3, 5));
    }

    @Test
    void plainQueriesAreNotRegexes() {
        assertThat(find("a.c", false, true, 0, "abc a.c"))
            .containsExactly(new TerminalSearch.Match(0, 4, 6));
    }

    @Test
    void regexSearch() {
        assertThat(find("[0-9]+", true, true, 0, "id 42 and 7"))
            .containsExactly(new TerminalSearch.Match(0, 3, 4), new TerminalSearch.Match(0, 10, 10));
    }

    @Test
    void aMatchOnAWideCharacterCoversBothCells() {
        assertThat(find("日", false, true, 0, "a日\uE000b"))
            .containsExactly(new TerminalSearch.Match(0, 1, 2));
    }

    @Test
    void emptyMatchesAreSkipped() {
        assertThat(find("x*", true, true, 0, "axb"))
            .containsExactly(new TerminalSearch.Match(0, 1, 1));
    }

    @Test
    void anInvalidRegexIsReported() {
        assertThatThrownBy(() -> TerminalSearch.pattern("(", true, true)).isInstanceOf(PatternSyntaxException.class);
    }

    private static List<TerminalSearch.Match> find(String query, boolean regex, boolean caseSensitive, long firstRow,
                                                   String... rows) {
        List<TerminalLine> lines = Arrays.stream(rows)
            .map(text -> new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))))
            .toList();
        return TerminalSearch.find(TerminalSearch.pattern(query, regex, caseSensitive), firstRow, lines, WIDTH);
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/SearchSessionTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSessionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 10, 3, 100);
        session.startReading();
        connector.feed("foo\r\nbar\r\nfoo bar\r\nbaz\r\nqux");
        Await.until(() -> "qux".equals(session.snapshot().lineText(2)), "five lines on a three-row screen");
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void searchCoversScrollbackAndScreenOldestFirst() {
        assertThat(session.search("foo", false, false))
            .containsExactly(new TerminalSearch.Match(0, 0, 2), new TerminalSearch.Match(2, 0, 2));
    }

    @Test
    void anEmptyQueryFindsNothing() {
        assertThat(session.search("", false, false)).isEmpty();
    }

    @Test
    void noMatchesIsAnEmptyList() {
        assertThat(session.search("zzz", false, false)).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.RowTextTest' --tests 'dev.moray.terminal.TerminalSearchTest' --tests 'dev.moray.terminal.SearchSessionTest'`
Expected: FAIL — compilation errors for `RowText`, `TerminalSearch`, `search`.

- [ ] **Step 3: Implement `RowText` and `TerminalSearch`**

`moray-terminal/src/main/java/dev/moray/terminal/RowText.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

import java.util.Arrays;

/**
 * A row as searchable text: one character per cell except wide-character continuation cells, with the first and last
 * column each character covers.
 */
record RowText(String text, int[] columns, int[] lastColumns) {

    static RowText of(TerminalLine line, int width) {
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        RunBuilder.readCells(line, width, chars, styles);
        StringBuilder text = new StringBuilder(width);
        int[] columns = new int[width];
        int[] lastColumns = new int[width];
        int count = 0;
        for (int column = 0; column < width; column++) {
            if (chars[column] == CharUtils.DWC) {
                if (count > 0) {
                    lastColumns[count - 1] = column;
                }
                continue;
            }
            text.append(chars[column]);
            columns[count] = column;
            lastColumns[count] = column;
            count++;
        }
        return new RowText(text.toString(), Arrays.copyOf(columns, count), Arrays.copyOf(lastColumns, count));
    }
}
```

`moray-terminal/src/main/java/dev/moray/terminal/TerminalSearch.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.model.TerminalLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds text row by row; a match does not span a soft wrap. */
final class TerminalSearch {

    /** A match on an absolute row; columns inclusive. */
    record Match(long row, int startColumn, int endColumn) {
    }

    private TerminalSearch() {
    }

    /** @throws java.util.regex.PatternSyntaxException when {@code regex} is true and the query is not a valid regex */
    static Pattern pattern(String query, boolean regex, boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(regex ? query : Pattern.quote(query), flags);
    }

    static List<Match> find(Pattern pattern, long firstRow, List<TerminalLine> lines, int width) {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            RowText row = RowText.of(lines.get(i), width);
            Matcher matcher = pattern.matcher(row.text());
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    matches.add(new Match(firstRow + i,
                        row.columns()[matcher.start()], row.lastColumns()[matcher.end() - 1]));
                }
            }
        }
        return matches;
    }
}
```

- [ ] **Step 4: Add `search` to `TerminalSession`**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`, add the imports `java.util.ArrayList` and `java.util.regex.Pattern`, and add this method next to `text(Selection)`:
```java
    /** Every match in the scrollback and on screen, oldest first; an empty query finds nothing. */
    List<TerminalSearch.Match> search(String query, boolean regex, boolean caseSensitive) {
        if (query.isEmpty()) {
            return List.of();
        }
        Pattern pattern = TerminalSearch.pattern(query, regex, caseSensitive);
        buffer.lock();
        try {
            int history = buffer.getHistoryLinesCount();
            List<TerminalLine> lines = new ArrayList<>(history + buffer.getHeight());
            for (int row = -history; row < buffer.getHeight(); row++) {
                lines.add(buffer.getLine(row));
            }
            return TerminalSearch.find(pattern, absoluteRow(-history), lines, buffer.getWidth());
        } finally {
            buffer.unlock();
        }
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.RowTextTest' --tests 'dev.moray.terminal.TerminalSearchTest' --tests 'dev.moray.terminal.SearchSessionTest'`
Expected: PASS (2 + 7 + 3 tests).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Add scrollback search with plain, regex and case options"
```

### Task 6: Painting selections, matches, links and the scroll position

**Files:**
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/Palette.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/CellStyle.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalPainter.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalView.java` (the `paint` call only)
- Test: `moray-terminal/src/test/java/dev/moray/terminal/PaletteTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/CellStyleTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalPainterTest.java`

**Interfaces:**
- Consumes: `ScreenSnapshot.scrollOffset()`, `historyLines()`, `TerminalSession.snapshot(long)` (Task 3).
- Produces:
  - `Palette` becomes `record Palette(Color foreground, Color background, Color cursor, Color selection, List<Color> ansi)`; `morayDark()` uses selection colour `#3e4451`.
  - `CellStyle.resolve` underlines text whose style is a JediTerm `HyperlinkStyle` (an OSC 8 link).
  - `TerminalPainter`:
    - nested `record Highlight(int row, int startColumn, int endColumn, Color color)` (viewport row, inclusive columns)
    - `paint(Graphics2D g, ScreenSnapshot snapshot, CursorLook cursor, List<Highlight> highlights, int widthPx, int heightPx)` — highlights are filled after cell backgrounds and before text
    - a translucent 4 px scroll indicator on the right edge whenever `scrollOffset > 0`

- [ ] **Step 1: Write the failing tests**

In `moray-terminal/src/test/java/dev/moray/terminal/PaletteTest.java`, change `rejectsAnsiListOfWrongSize` to pass a selection colour, and add a test:
```java
    @Test
    void rejectsAnsiListOfWrongSize() {
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, Color.WHITE, Color.GRAY, Collections.nCopies(8, Color.RED)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("16");
    }

    @Test
    void theThemeHasASelectionColor() {
        assertThat(palette.selection()).isEqualTo(new Color(0x3e4451));
    }
```

In `moray-terminal/src/test/java/dev/moray/terminal/CellStyleTest.java`, add the imports `com.jediterm.terminal.HyperlinkStyle` and `com.jediterm.terminal.model.hyperlinks.LinkInfo`, and add:
```java
    @Test
    void hyperlinksAreUnderlined() {
        TextStyle link = new HyperlinkStyle(TextStyle.EMPTY, new LinkInfo(() -> { }));

        assertThat(CellStyle.resolve(link, palette).underline()).isTrue();
    }
```

In `moray-terminal/src/test/java/dev/moray/terminal/TerminalPainterTest.java`:

1. Replace the `paint(ScreenSnapshot, TerminalPainter.CursorLook)` helper with these two helpers:
```java
    private BufferedImage paint(ScreenSnapshot snapshot, TerminalPainter.CursorLook cursor) {
        return paint(snapshot, cursor, List.of());
    }

    private BufferedImage paint(ScreenSnapshot snapshot, TerminalPainter.CursorLook cursor,
                                List<TerminalPainter.Highlight> highlights) {
        BufferedImage image = new BufferedImage(COLUMNS * cw, ROWS * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            painter.paint(g, snapshot, cursor, highlights, image.getWidth(), image.getHeight());
        } finally {
            g.dispose();
        }
        return image;
    }
```

2. Add these tests:
```java
    @Test
    void highlightsFillTheirCells() throws Exception {
        BufferedImage image = paint(snapshotAfter("", 0), cursorOff(),
            List.of(new TerminalPainter.Highlight(1, 2, 3, Color.RED)));

        assertThat(rgb(image, 2 * cw + cw / 2, ch + ch / 2)).isEqualTo(rgb(Color.RED));
        assertThat(rgb(image, 3 * cw + cw / 2, ch + ch / 2)).isEqualTo(rgb(Color.RED));
        assertThat(rgb(image, 4 * cw + cw / 2, ch + ch / 2)).isEqualTo(rgb(palette.background()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void underlineCursorSitsOnTheBottomOfItsCell() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.UNDERLINE, true, true));

        assertThat(rgb(image, 2 * cw + cw / 2, ch - 1)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void aScrollIndicatorShowsOnlyWhenScrolledBack() throws Exception {
        FakeConnector connector = new FakeConnector();
        TerminalSession session = new TerminalSession(connector, COLUMNS, ROWS, 10);
        session.startReading();
        try {
            connector.feed("a\r\nb\r\nc\r\nd\r\ne");
            Await.until(() -> "e".equals(session.snapshot().lineText(2)), "two lines of scrollback");

            BufferedImage scrolled = paint(session.snapshot(0), cursorOff());
            BufferedImage live = paint(session.snapshot(), cursorOff());

            assertThat(rgb(scrolled, COLUMNS * cw - 2, 1)).isNotEqualTo(rgb(palette.background()));
            assertThat(rgb(live, COLUMNS * cw - 2, 1)).isEqualTo(rgb(palette.background()));
        } finally {
            session.close();
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.PaletteTest' --tests 'dev.moray.terminal.CellStyleTest' --tests 'dev.moray.terminal.TerminalPainterTest'`
Expected: FAIL — compilation errors (`Palette` has no 5-argument constructor or `selection()`; `TerminalPainter.Highlight` and the 6-argument `paint` do not exist).

- [ ] **Step 3: Add the selection colour to `Palette`**

In `moray-terminal/src/main/java/dev/moray/terminal/Palette.java`, change the record header to:
```java
public record Palette(Color foreground, Color background, Color cursor, Color selection, List<Color> ansi) {
```
and in `morayDark()` insert the selection colour after the cursor colour, so its first line reads:
```java
            new Color(0xd7dae0), new Color(0x1e2127), new Color(0xd7dae0), new Color(0x3e4451),
```

- [ ] **Step 4: Underline hyperlinks in `CellStyle`**

In `moray-terminal/src/main/java/dev/moray/terminal/CellStyle.java`, add `import com.jediterm.terminal.HyperlinkStyle;` and change the last argument of the `return new CellStyle(...)` in `resolve` from `style.hasOption(Option.UNDERLINED)` to:
```java
            style.hasOption(Option.UNDERLINED) || style instanceof HyperlinkStyle);
```

- [ ] **Step 5: Paint highlights and the scroll indicator**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalPainter.java`:

1. Add after the `CursorLook` record:
```java
    /** A background fill over one viewport row, columns inclusive: a selection or a search match. */
    record Highlight(int row, int startColumn, int endColumn, Color color) {
    }
```

2. Add after `BAR_THICKNESS`:
```java
    private static final int INDICATOR_WIDTH = 4;
    private static final int INDICATOR_MIN_HEIGHT = 12;
```

3. Change the `paint` signature to
`void paint(Graphics2D g, ScreenSnapshot snapshot, CursorLook cursor, List<Highlight> highlights, int widthPx, int heightPx)`.

4. Inside the row loop in `paint`, between the loop that fills run backgrounds and the loop that calls `drawRun`, add:
```java
            for (Highlight highlight : highlights) {
                if (highlight.row() == row) {
                    g.setColor(highlight.color());
                    g.fillRect(highlight.startColumn() * cellWidth, top,
                        (highlight.endColumn() - highlight.startColumn() + 1) * cellWidth, cellHeight);
                }
            }
```

5. Replace the final `paintCursor(g, snapshot, cursor);` line of `paint` with:
```java
        paintCursor(g, snapshot, cursor);
        if (snapshot.scrollOffset() > 0) {
            paintScrollIndicator(g, snapshot, widthPx, heightPx);
        }
```

6. Add this method after `paintCursor`:
```java
    /** Where the view sits in the scrollback: a translucent thumb on the right edge. */
    private void paintScrollIndicator(Graphics2D g, ScreenSnapshot snapshot, int widthPx, int heightPx) {
        int total = snapshot.historyLines() + snapshot.height();
        int thumbHeight = Math.max(INDICATOR_MIN_HEIGHT, heightPx * snapshot.height() / total);
        int linesAboveView = snapshot.historyLines() - snapshot.scrollOffset();
        int thumbTop = (heightPx - thumbHeight) * linesAboveView / Math.max(1, snapshot.historyLines());
        Color foreground = palette.foreground();
        g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 0x70));
        g.fillRect(widthPx - INDICATOR_WIDTH, thumbTop, INDICATOR_WIDTH, thumbHeight);
    }
```

- [ ] **Step 6: Keep `TerminalView` compiling**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalView.java`, add `import java.util.List;` and change the painter call at the end of `paintComponent` to pass no highlights (Task 9 supplies real ones):
```java
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused), List.of(),
            getWidth(), getHeight());
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.PaletteTest' --tests 'dev.moray.terminal.CellStyleTest' --tests 'dev.moray.terminal.TerminalPainterTest'`
Expected: PASS (7 + 6 + 11 tests).

- [ ] **Step 8: Run the module tests**

Run: `./gradlew :moray-terminal:check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add moray-terminal/src
git commit -m "Paint selection and match highlights, link underlines and a scroll indicator"
```

### Task 7: Mouse reports, paste and links in the session

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/UriLink.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/LinkDetector.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/LinkDetectorTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/SessionInputTest.java`

**Interfaces:**
- Consumes: `RowText` (Task 5); `lineAtLocked(long)` and the buffer lock (Task 2); `SessionDisplay.mouseMode()` and `bracketedPaste()` (plan 1).
- Produces (package-private):
  - `final class UriLink extends LinkInfo` — `UriLink(String uri)`, `String uri()`; the target of an OSC 8 link, stored by JediTerm in the cell's `HyperlinkStyle`.
  - `final class LinkDetector` — `static Optional<String> urlAt(RowText row, int column)`: a plain-text `http`, `https`, `ftp` or `file` URL covering the column. Trailing `.,;:!?)]}'"` characters are not part of the URL.
  - On `TerminalSession`:
    - `boolean mouseReporting()`
    - `boolean usingAlternateBuffer()`
    - `boolean reportMouse(int column, int row, com.jediterm.core.input.MouseEvent event)` — screen cell, clamped onto the screen; returns false and sends nothing unless the program enabled mouse reporting
    - `void paste(String text)` — `\r\n` and `\n` become `\r`; in bracketed-paste mode the text is wrapped in `ESC[200~` … `ESC[201~`, with any embedded `ESC[201~` removed
    - `Optional<String> linkAt(long absoluteRow, int column)` — the OSC 8 link on that cell, or else a detected URL
  - The session installs a `HyperlinkFilter` so that OSC 8 links are recorded at all.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/LinkDetectorTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkDetectorTest {

    @Test
    void findsTheUrlUnderTheColumn() {
        assertThat(LinkDetector.urlAt(row("see https://moray.dev/docs now"), 10)).contains("https://moray.dev/docs");
    }

    @Test
    void trailingPunctuationIsNotPartOfTheUrl() {
        assertThat(LinkDetector.urlAt(row("(see https://moray.dev/docs)."), 10)).contains("https://moray.dev/docs");
    }

    @Test
    void columnsOutsideTheUrlFindNothing() {
        assertThat(LinkDetector.urlAt(row("see https://moray.dev now"), 1)).isEmpty();
        assertThat(LinkDetector.urlAt(row("see https://moray.dev now"), 22)).isEmpty();
    }

    @Test
    void fileUrlsAreLinks() {
        assertThat(LinkDetector.urlAt(row("open file:///tmp/x.txt"), 6)).contains("file:///tmp/x.txt");
    }

    @Test
    void textWithoutAUrlFindsNothing() {
        assertThat(LinkDetector.urlAt(row("no links here"), 3)).isEmpty();
    }

    private static RowText row(String text) {
        return RowText.of(new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))), 60);
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/SessionInputTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.input.MouseEvent;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionInputTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void rightClickIsReportedInSgrMode() throws Exception {
        enableSgrMouse();

        boolean reported = session.reportMouse(0, 0, new MouseEvent(MouseEvent.Type.PRESSED, MouseButtonCodes.RIGHT, 0));

        assertThat(reported).isTrue();
        assertThat(connector.written()).isEqualTo("\033[<2;1;1M");
    }

    @Test
    void reportsAreClampedOntoTheScreen() throws Exception {
        enableSgrMouse();

        session.reportMouse(-3, 99, new MouseEvent(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT, 0));

        assertThat(connector.written()).isEqualTo("\033[<0;1;4M");
    }

    @Test
    void nothingIsReportedWithoutMouseMode() {
        assertThat(session.mouseReporting()).isFalse();

        boolean reported = session.reportMouse(1, 1, new MouseEvent(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT, 0));

        assertThat(reported).isFalse();
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void pasteTurnsNewlinesIntoCarriageReturns() {
        session.paste("a\nb\r\nc");

        assertThat(connector.written()).isEqualTo("a\rb\rc");
    }

    @Test
    void bracketedPasteWrapsTheTextAndRemovesEmbeddedEndMarkers() throws Exception {
        connector.feed("\033[?2004h");
        Await.until(() -> session.display().bracketedPaste(), "bracketed paste on");

        session.paste("x\033[201~y");

        assertThat(connector.written()).isEqualTo("\033[200~xy\033[201~");
    }

    @Test
    void osc8LinksAreFoundUnderTheirText() throws Exception {
        connector.feed("\033]8;;https://example.com\007link\033]8;;\007 plain");
        Await.until(() -> "link plain".equals(session.snapshot().lineText(0)), "linked text");

        assertThat(session.linkAt(0, 1)).contains("https://example.com");
        assertThat(session.linkAt(0, 6)).isEmpty();
    }

    @Test
    void urlsInPlainTextAreFound() throws Exception {
        connector.feed("see https://moray.dev/docs.");
        Await.until(() -> session.snapshot().lineText(0).startsWith("see https"), "url text");

        assertThat(session.linkAt(0, 10)).contains("https://moray.dev/docs");
    }

    @Test
    void theAlternateScreenIsReported() throws Exception {
        assertThat(session.usingAlternateBuffer()).isFalse();

        connector.feed("\033[?1049h");

        Await.until(session::usingAlternateBuffer, "alternate screen");
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session::mouseReporting, "mouse reporting on");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.LinkDetectorTest' --tests 'dev.moray.terminal.SessionInputTest'`
Expected: FAIL — compilation errors for `LinkDetector`, `reportMouse`, `mouseReporting`, `paste`, `linkAt`, `usingAlternateBuffer`.

- [ ] **Step 3: Implement `UriLink` and `LinkDetector`**

`moray-terminal/src/main/java/dev/moray/terminal/UriLink.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;

/** The target of an OSC 8 hyperlink; JediTerm keeps it in the cell's HyperlinkStyle. The view opens it. */
final class UriLink extends LinkInfo {
    private final String uri;

    UriLink(String uri) {
        super(() -> {
            // Opening is the view's job (it knows the platform); JediTerm never calls this.
        });
        this.uri = uri;
    }

    String uri() {
        return uri;
    }
}
```

`moray-terminal/src/main/java/dev/moray/terminal/LinkDetector.java`:
```java
package dev.moray.terminal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a URL written as plain text on a row. */
final class LinkDetector {
    private static final Pattern URL = Pattern.compile("(?:https?|ftp|file)://[^\\s<>\"'`]+");
    private static final String TRAILING = ".,;:!?)]}'\"";

    private LinkDetector() {
    }

    static Optional<String> urlAt(RowText row, int column) {
        String text = row.text();
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && TRAILING.indexOf(text.charAt(end - 1)) >= 0) {
                end--;
            }
            if (end == matcher.start()) {
                continue;
            }
            int firstColumn = row.columns()[matcher.start()];
            int lastColumn = row.lastColumns()[end - 1];
            if (column >= firstColumn && column <= lastColumn) {
                return Optional.of(text.substring(matcher.start(), end));
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Extend `TerminalSession`**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`:

1. Add these imports:
```java
import com.jediterm.core.input.MouseEvent;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.emulator.mouse.MouseEventProcessingSettings;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
```

2. In the package-private constructor, directly after `terminal.addCustomCommandListener(this::onCustomCommand);`, add:
```java
        // Without a filter JediTerm drops OSC 8 links; one item spanning the whole URI makes it keep them.
        terminal.setUrlHyperlinkFilter(uri -> new LinkResult(new LinkResultItem(0, uri.length(), new UriLink(uri))));
```

3. Add these methods next to `search(...)`:
```java
    /** Whether the program asked for mouse reports, so clicks go to it instead of to local selection. */
    boolean mouseReporting() {
        return display.mouseMode() != MouseMode.MOUSE_REPORTING_NONE;
    }

    boolean usingAlternateBuffer() {
        buffer.lock();
        try {
            return buffer.isUsingAlternateBuffer();
        } finally {
            buffer.unlock();
        }
    }

    /** Reports a mouse event at a screen cell, clamped onto the screen; false when the program did not ask for it. */
    boolean reportMouse(int column, int row, MouseEvent event) {
        if (!mouseReporting()) {
            return false;
        }
        int x = Math.max(0, Math.min(columns - 1, column));
        int y = Math.max(0, Math.min(rows - 1, row));
        return terminal.onMouseEvent(x, y, event, new MouseEventProcessingSettings(true, usingAlternateBuffer(), false));
    }

    /** Pastes text: newlines become carriage returns, wrapped in bracketed-paste markers when the program asked. */
    void paste(String text) {
        String normalized = text.replace("\r\n", "\r").replace('\n', '\r');
        if (display.bracketedPaste()) {
            write("\033[200~" + normalized.replace("\033[201~", "") + "\033[201~");
        } else {
            write(normalized);
        }
    }

    /** The link at an absolute row and column: an OSC 8 hyperlink, or else a URL written in the text. */
    Optional<String> linkAt(long absoluteRow, int column) {
        buffer.lock();
        try {
            TerminalLine line = lineAtLocked(absoluteRow);
            if (line == null) {
                return Optional.empty();
            }
            if (column < line.length()
                && line.getStyleAt(column) instanceof HyperlinkStyle hyperlink
                && hyperlink.getLinkInfo() instanceof UriLink link) {
                return Optional.of(link.uri());
            }
            return LinkDetector.urlAt(RowText.of(line, buffer.getWidth()), column);
        } finally {
            buffer.unlock();
        }
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.LinkDetectorTest' --tests 'dev.moray.terminal.SessionInputTest'`
Expected: PASS (5 + 8 tests).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Add mouse reporting, paste and link lookup to the session"
```

### Task 8: The viewport and mouse routing

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/Viewport.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/MouseRouting.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/ViewportTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/MouseRoutingTest.java`

**Interfaces:**
- Consumes: `ScreenSnapshot` and `ScreenSnapshot.FOLLOW_OUTPUT` (Task 3).
- Produces (package-private; both used only by `TerminalView` in Task 9):
  - `final class Viewport`, used on the Event Dispatch Thread only:
    - `long topRow()` (`FOLLOW_OUTPUT` while following) and `boolean following()`
    - `void follow()`
    - `void scrollBy(int lines, ScreenSnapshot current)` (negative scrolls back into the scrollback)
    - `void showAtTop(long row, ScreenSnapshot current)`
    - `void reveal(long row, ScreenSnapshot current)`

    Every move is clamped between the oldest scrollback line and the live screen. Reaching the live screen switches back to following.
  - `final class MouseRouting`:
    - nested `enum Button { LEFT, MIDDLE, RIGHT, NONE }`
    - nested `enum Action { REPORT, START_SELECTION, SELECT_WORD, SELECT_LINE, EXTEND_SELECTION, END_SELECTION, OPEN_LINK, SCROLL_VIEW, SEND_ARROWS, NONE }`
    - `static Action decide(MouseEvent.Type type, Button button, int clickCount, boolean shift, boolean linkModifier, boolean reporting, boolean alternateBuffer)`, where `MouseEvent` is `com.jediterm.core.input.MouseEvent`

Routing rules, in order:
1. If the program wants mouse reports and Shift is not held → `REPORT`.
2. The wheel scrolls the view, or sends arrow keys in the alternate screen.
3. For the left button:
   - Press with the link modifier → `OPEN_LINK`.
   - Press with 3+ clicks → line; 2 clicks → word; 1 click → start a selection.
   - Drag → extend.
   - Release → end.
4. Anything else → `NONE`.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/ViewportTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViewportTest {
    // Ten lines of scrollback above a three-row screen whose top is absolute row 10.
    private static final ScreenSnapshot LIVE = snapshot(10, 0);

    @Test
    void startsFollowingTheOutput() {
        Viewport viewport = new Viewport();

        assertThat(viewport.following()).isTrue();
        assertThat(viewport.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    @Test
    void scrollingBackAnchorsAtAnAbsoluteRow() {
        Viewport viewport = new Viewport();

        viewport.scrollBy(-3, LIVE);

        assertThat(viewport.topRow()).isEqualTo(7);
        assertThat(viewport.following()).isFalse();
    }

    @Test
    void scrollingStopsAtTheOldestLine() {
        Viewport viewport = new Viewport();

        viewport.scrollBy(-50, LIVE);

        assertThat(viewport.topRow()).isZero();
    }

    @Test
    void scrollingDownToTheLiveScreenFollowsAgain() {
        Viewport viewport = new Viewport();
        viewport.scrollBy(-3, LIVE);

        viewport.scrollBy(5, snapshot(7, 3));

        assertThat(viewport.following()).isTrue();
    }

    @Test
    void showAtTopIsClamped() {
        Viewport viewport = new Viewport();

        viewport.showAtTop(4, LIVE);
        assertThat(viewport.topRow()).isEqualTo(4);

        viewport.showAtTop(99, LIVE);
        assertThat(viewport.following()).isTrue();
    }

    @Test
    void revealMovesOnlyAsFarAsNeeded() {
        Viewport viewport = new Viewport();
        viewport.showAtTop(5, LIVE);
        ScreenSnapshot showingRowsFiveToSeven = snapshot(5, 5);

        viewport.reveal(6, showingRowsFiveToSeven);
        assertThat(viewport.topRow()).isEqualTo(5);

        viewport.reveal(9, showingRowsFiveToSeven);
        assertThat(viewport.topRow()).isEqualTo(7);

        viewport.reveal(2, showingRowsFiveToSeven);
        assertThat(viewport.topRow()).isEqualTo(2);
    }

    @Test
    void followReturnsToTheLiveScreen() {
        Viewport viewport = new Viewport();
        viewport.showAtTop(3, LIVE);

        viewport.follow();

        assertThat(viewport.following()).isTrue();
    }

    private static ScreenSnapshot snapshot(long firstRow, int scrollOffset) {
        return new ScreenSnapshot(10, 3, List.of(), 0, 0, true, null, firstRow, scrollOffset, 10, false);
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/MouseRoutingTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.input.MouseEvent.Type;
import org.junit.jupiter.api.Test;

import static dev.moray.terminal.MouseRouting.Action;
import static dev.moray.terminal.MouseRouting.Button;
import static org.assertj.core.api.Assertions.assertThat;

class MouseRoutingTest {

    @Test
    void programsThatAskForTheMouseGetIt() {
        assertThat(decide(Type.PRESSED, Button.RIGHT, 1, false, false, true, false)).isEqualTo(Action.REPORT);
        assertThat(decide(Type.WHEEL, Button.NONE, 0, false, false, true, true)).isEqualTo(Action.REPORT);
    }

    @Test
    void shiftKeepsTheMouseLocalEvenWhenReporting() {
        assertThat(decide(Type.PRESSED, Button.LEFT, 1, true, false, true, false)).isEqualTo(Action.START_SELECTION);
    }

    @Test
    void theWheelScrollsTheViewOrSendsArrowsInTheAlternateScreen() {
        assertThat(decide(Type.WHEEL, Button.NONE, 0, false, false, false, false)).isEqualTo(Action.SCROLL_VIEW);
        assertThat(decide(Type.WHEEL, Button.NONE, 0, false, false, false, true)).isEqualTo(Action.SEND_ARROWS);
    }

    @Test
    void clickCountsChooseTheSelectionUnit() {
        assertThat(decide(Type.PRESSED, Button.LEFT, 1, false, false, false, false)).isEqualTo(Action.START_SELECTION);
        assertThat(decide(Type.PRESSED, Button.LEFT, 2, false, false, false, false)).isEqualTo(Action.SELECT_WORD);
        assertThat(decide(Type.PRESSED, Button.LEFT, 3, false, false, false, false)).isEqualTo(Action.SELECT_LINE);
    }

    @Test
    void theLinkModifierOpensLinks() {
        assertThat(decide(Type.PRESSED, Button.LEFT, 1, false, true, false, false)).isEqualTo(Action.OPEN_LINK);
    }

    @Test
    void draggingExtendsAndReleasingEnds() {
        assertThat(decide(Type.DRAGGED, Button.LEFT, 1, false, false, false, false)).isEqualTo(Action.EXTEND_SELECTION);
        assertThat(decide(Type.RELEASED, Button.LEFT, 1, false, false, false, false)).isEqualTo(Action.END_SELECTION);
    }

    @Test
    void otherButtonsAndMovesDoNothingLocally() {
        assertThat(decide(Type.PRESSED, Button.RIGHT, 1, false, false, false, false)).isEqualTo(Action.NONE);
        assertThat(decide(Type.MOVED, Button.NONE, 0, false, false, false, false)).isEqualTo(Action.NONE);
    }

    private static Action decide(Type type, Button button, int clicks, boolean shift, boolean linkModifier,
                                 boolean reporting, boolean alternate) {
        return MouseRouting.decide(type, button, clicks, shift, linkModifier, reporting, alternate);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.ViewportTest' --tests 'dev.moray.terminal.MouseRoutingTest'`
Expected: FAIL — compilation errors for `Viewport` and `MouseRouting`.

- [ ] **Step 3: Implement `Viewport`**

`moray-terminal/src/main/java/dev/moray/terminal/Viewport.java`:
```java
package dev.moray.terminal;

/**
 * Which rows the view shows: the live screen, following new output, or a scrolled-back position anchored at an
 * absolute row so its lines stay put while output arrives. Event Dispatch Thread only.
 */
final class Viewport {
    private long topRow = ScreenSnapshot.FOLLOW_OUTPUT;

    long topRow() {
        return topRow;
    }

    boolean following() {
        return topRow == ScreenSnapshot.FOLLOW_OUTPUT;
    }

    void follow() {
        topRow = ScreenSnapshot.FOLLOW_OUTPUT;
    }

    /** Scrolls by lines (negative = back into the scrollback) from what {@code current} shows. */
    void scrollBy(int lines, ScreenSnapshot current) {
        moveTo(current.firstRow() + lines, current);
    }

    /** Puts an absolute row at the top of the view, as far as the scrollback allows. */
    void showAtTop(long row, ScreenSnapshot current) {
        moveTo(row, current);
    }

    /** Scrolls the least needed to make an absolute row visible. */
    void reveal(long row, ScreenSnapshot current) {
        long top = current.firstRow();
        long bottom = top + current.height() - 1;
        if (row < top) {
            moveTo(row, current);
        } else if (row > bottom) {
            moveTo(row - current.height() + 1, current);
        }
    }

    private void moveTo(long requestedTop, ScreenSnapshot current) {
        long liveTop = current.firstRow() + current.scrollOffset();
        long oldestTop = liveTop - current.historyLines();
        long top = Math.max(oldestTop, Math.min(liveTop, requestedTop));
        topRow = top == liveTop ? ScreenSnapshot.FOLLOW_OUTPUT : top;
    }
}
```

- [ ] **Step 4: Implement `MouseRouting`**

`moray-terminal/src/main/java/dev/moray/terminal/MouseRouting.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.input.MouseEvent;

/** Decides what a mouse event means for the terminal view. */
final class MouseRouting {

    enum Button { LEFT, MIDDLE, RIGHT, NONE }

    enum Action {
        REPORT, START_SELECTION, SELECT_WORD, SELECT_LINE, EXTEND_SELECTION, END_SELECTION,
        OPEN_LINK, SCROLL_VIEW, SEND_ARROWS, NONE
    }

    private MouseRouting() {
    }

    /**
     * @param shift        Shift held: the mouse stays local even when the program wants reports
     * @param linkModifier ⌘ on macOS, Ctrl on Linux and Windows
     * @param reporting    the program enabled mouse reporting
     */
    static Action decide(MouseEvent.Type type, Button button, int clickCount, boolean shift, boolean linkModifier,
                         boolean reporting, boolean alternateBuffer) {
        if (reporting && !shift) {
            return Action.REPORT;
        }
        if (type == MouseEvent.Type.WHEEL) {
            return alternateBuffer ? Action.SEND_ARROWS : Action.SCROLL_VIEW;
        }
        if (button != Button.LEFT) {
            return Action.NONE;
        }
        return switch (type) {
            case PRESSED -> linkModifier ? Action.OPEN_LINK
                : clickCount >= 3 ? Action.SELECT_LINE
                : clickCount == 2 ? Action.SELECT_WORD
                : Action.START_SELECTION;
            case DRAGGED -> Action.EXTEND_SELECTION;
            case RELEASED -> Action.END_SELECTION;
            default -> Action.NONE;
        };
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.ViewportTest' --tests 'dev.moray.terminal.MouseRoutingTest'`
Expected: PASS (7 + 7 tests).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Add the scrollback viewport and mouse routing rules"
```

### Task 9: The view — scrolling, mouse, selection, clipboard, links, find, prompts, exit

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/FindResult.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalOptions.java`
- Modify (replace whole file): `moray-terminal/src/main/java/dev/moray/terminal/TerminalView.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalViewInteractionTest.java`

**Interfaces:**
- Consumes:
  - Task 2: `promptRows()`.
  - Task 3: `snapshot(long)`, `ScreenSnapshot.firstRow/scrollOffset/alternateBuffer`.
  - Task 4: `Selection`, `text`, `wordSelection`, `lineSelection`.
  - Task 5: `search`, `TerminalSearch.Match`.
  - Task 6: `TerminalPainter.Highlight`, the 6-argument `paint`, `Palette.selection()`, `CellStyle.blend`.
  - Task 7: `mouseReporting`, `reportMouse`, `paste`, `linkAt`, `codeForKey`.
  - Task 8: `Viewport`, `MouseRouting`.
  - Plan 1: `KeyEncoder`, `FontSet`, `CursorStyle`, `GridSize`.
- Produces:
  - `public record FindResult(int count, int current, String error)`, where `current` is 1-based and 0 when there are no matches, and `error` is a regex error message or `null`.
  - `TerminalOptions` gains a final component `boolean copyOnSelect`, which `defaults()` sets to `false`.
  - Public on `TerminalView`:
    - `paste(String)`
    - `Optional<String> selectedText()`
    - `copySelection()`
    - `FindResult find(String query, boolean regex, boolean caseSensitive)`, which starts at the newest match
    - `FindResult findNext()` (newer) and `FindResult findPrevious()` (older), both wrapping around
    - `clearFind()`
    - `scrollToPreviousPrompt()` and `scrollToNextPrompt()`
    - `setOnCloseRequest(Runnable)`
  - Package-private, for tests: `handleMouse(java.awt.event.MouseEvent)`, `setClipboard(Supplier<String>, Consumer<String>)`, `setLinkOpener(Consumer<String>)`, `long topRow()`, `boolean exited()`.

Behaviour:
- **Wheel:** scrolls 3 lines per notch through `Viewport`. In the alternate screen it sends arrow keys, and when mouse reporting is on it is reported to the program.
- **Shift+PageUp/PageDown:** scrolls a page (rows − 1).
- **Following the output again:** any key sent to the shell returns the view to the live screen and clears the selection; paste also returns it to the live screen.
- **Selecting:**
  - A click starts a selection anchor, and dragging creates and extends the selection. A click without a drag clears it.
  - Alt-drag makes a block selection.
  - Double-click selects a word; triple-click selects the logical line.
  - `copyOnSelect` copies when the mouse is released.
- **Links:** ⌘-click (Ctrl-click on Linux/Windows) opens the link under the pointer.
- **Hard-wired shortcuts** (see Global Constraints): copy, paste, and previous/next prompt. They are handled on KEY_PRESSED before the key encoder, and the KEY_TYPED event that follows is suppressed.
- **Painting:** search matches use yellow blended with the background (70% of the way to the background, 35% for the current match); the selection uses `palette.selection()`.
- **After the program exits:** once `exitFuture()` completes, the next non-modifier key press calls the close request instead of writing to the program.

- [ ] **Step 1: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/TerminalViewInteractionTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalViewInteractionTest {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    private static final int PRIMARY = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    private static final int LINK = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    private final TerminalOptions options = TerminalOptions.defaults();
    private final FontSet fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
        view = new TerminalView(session, options);
        view.setSize(20 * fonts.cellWidth(), 4 * fonts.cellHeight());
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void theWheelScrollsBackIntoTheScrollback() throws Exception {
        tenLines();

        view.handleMouse(wheel(-1));

        assertThat(view.topRow()).isNotEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
        assertThat(session.snapshot(view.topRow()).scrollOffset()).isEqualTo(3);
    }

    @Test
    void shiftPageUpScrollsAPage() throws Exception {
        tenLines();

        view.handleKey(pressed(KeyEvent.VK_PAGE_UP, InputEvent.SHIFT_DOWN_MASK));

        assertThat(session.snapshot(view.topRow()).scrollOffset()).isEqualTo(3);
    }

    @Test
    void typingReturnsToTheLiveScreen() throws Exception {
        tenLines();
        view.handleMouse(wheel(-1));

        view.handleKey(pressed(KeyEvent.VK_A, 0));
        view.handleKey(typed('a'));

        assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    @Test
    void draggingSelectsText() throws Exception {
        show("hello world", 0, "hello world");

        selectByDragging(0, 0, 4, 0, 0);

        assertThat(view.selectedText()).contains("hello");
    }

    @Test
    void aClickWithoutDraggingClearsTheSelection() throws Exception {
        show("hello world", 0, "hello world");
        selectByDragging(0, 0, 4, 0, 0);

        view.handleMouse(press(6, 0, 1, 0));
        view.handleMouse(release(6, 0));

        assertThat(view.selectedText()).isEmpty();
    }

    @Test
    void doubleClickSelectsAWord() throws Exception {
        show("hello world", 0, "hello world");

        view.handleMouse(press(7, 0, 2, 0));

        assertThat(view.selectedText()).contains("world");
    }

    @Test
    void tripleClickSelectsTheLine() throws Exception {
        show("hello world", 0, "hello world");

        view.handleMouse(press(3, 0, 3, 0));

        assertThat(view.selectedText()).contains("hello world");
    }

    @Test
    void altDraggingSelectsABlock() throws Exception {
        show("abcd\r\nefgh", 1, "efgh");

        selectByDragging(1, 0, 2, 1, InputEvent.ALT_DOWN_MASK);

        assertThat(view.selectedText()).contains("bc\nfg");
    }

    @Test
    void theCopyShortcutPutsTheSelectionOnTheClipboard() throws Exception {
        AtomicReference<String> copied = new AtomicReference<>();
        view.setClipboard(() -> null, copied::set);
        show("hello world", 0, "hello world");
        selectByDragging(0, 0, 4, 0, 0);

        view.handleKey(pressed(KeyEvent.VK_C, PRIMARY));

        assertThat(copied.get()).isEqualTo("hello");
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void thePasteShortcutSendsTheClipboard() {
        view.setClipboard(() -> "a\nb", text -> { });

        view.handleKey(pressed(KeyEvent.VK_V, PRIMARY));

        assertThat(connector.written()).isEqualTo("a\rb");
    }

    @Test
    void typingClearsTheSelection() throws Exception {
        show("hello world", 0, "hello world");
        selectByDragging(0, 0, 4, 0, 0);

        view.handleKey(pressed(KeyEvent.VK_X, 0));
        view.handleKey(typed('x'));

        assertThat(view.selectedText()).isEmpty();
    }

    @Test
    void programsThatWantTheMouseGetClicks() throws Exception {
        enableSgrMouse();

        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK,
            x(2), y(1), 1, false, MouseEvent.BUTTON3));

        assertThat(connector.written()).isEqualTo("\033[<2;3;2M");
    }

    @Test
    void shiftDraggingSelectsEvenWhenTheProgramWantsTheMouse() throws Exception {
        show("hello", 0, "hello");
        enableSgrMouse();

        selectByDragging(0, 0, 4, 0, InputEvent.SHIFT_DOWN_MASK);

        assertThat(view.selectedText()).contains("hello");
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void theLinkModifierOpensTheLinkUnderThePointer() throws Exception {
        AtomicReference<String> opened = new AtomicReference<>();
        view.setLinkOpener(opened::set);
        show("go https://m.dev x", 0, "go https://m.dev x");

        view.handleMouse(press(8, 0, 1, LINK));

        assertThat(opened.get()).isEqualTo("https://m.dev");
    }

    @Test
    void findReportsAndStepsThroughMatches() throws Exception {
        show("foo\r\nbar foo", 1, "bar foo");

        assertThat(view.find("foo", false, false)).isEqualTo(new FindResult(2, 2, null));
        assertThat(view.findPrevious()).isEqualTo(new FindResult(2, 1, null));
        assertThat(view.findNext()).isEqualTo(new FindResult(2, 2, null));

        FindResult invalid = view.find("(", true, false);
        assertThat(invalid.count()).isZero();
        assertThat(invalid.error()).isNotBlank();
    }

    @Test
    void promptJumpsMoveBetweenMarkedPrompts() throws Exception {
        connector.feed("\033]133;A\007$ one\r\n" + "x\r\n".repeat(8) + "\033]133;A\007$ two");
        Await.until(() -> "$ two".equals(session.snapshot().lineText(3)), "second prompt on the last row");

        view.scrollToPreviousPrompt();
        assertThat(session.snapshot(view.topRow()).lineText(0)).isEqualTo("$ one");

        view.scrollToNextPrompt();
        assertThat(view.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    @Test
    void aKeyAfterTheProgramExitsRequestsClose() throws Exception {
        AtomicBoolean closeRequested = new AtomicBoolean();
        view.setOnCloseRequest(() -> closeRequested.set(true));
        connector.finish();
        Await.until(view::exited, "the view saw the exit");

        view.handleKey(pressed(KeyEvent.VK_A, 0));

        assertThat(closeRequested).isTrue();
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void theSelectionIsPainted() throws Exception {
        show("     x", 0, "     x");
        selectByDragging(0, 0, 3, 0, 0);

        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            view.paint(g);
        } finally {
            g.dispose();
        }

        assertThat(image.getRGB(x(1) + fonts.cellWidth() / 2, y(0) + fonts.cellHeight() / 2) & 0xFFFFFF)
            .isEqualTo(options.palette().selection().getRGB() & 0xFFFFFF);
    }

    private void tenLines() throws Exception {
        connector.feed("1\r\n2\r\n3\r\n4\r\n5\r\n6\r\n7\r\n8\r\n9\r\n10");
        Await.until(() -> "10".equals(session.snapshot().lineText(3)), "ten lines on a four-row screen");
    }

    private void show(String output, int row, String expectedStart) throws Exception {
        connector.feed(output);
        Await.until(() -> session.snapshot().lineText(row).startsWith(expectedStart), "\"" + expectedStart + "\"");
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session::mouseReporting, "mouse reporting on");
    }

    private void selectByDragging(int fromColumn, int fromRow, int toColumn, int toRow, int modifiers) {
        view.handleMouse(press(fromColumn, fromRow, 1, modifiers));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK,
            x(toColumn), y(toRow), 1, false, MouseEvent.NOBUTTON));
        view.handleMouse(release(toColumn, toRow));
    }

    private MouseEvent press(int column, int row, int clicks, int modifiers) {
        return new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK,
            x(column), y(row), clicks, false, MouseEvent.BUTTON1);
    }

    private MouseEvent release(int column, int row) {
        return new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0, x(column), y(row), 1, false, MouseEvent.BUTTON1);
    }

    private MouseWheelEvent wheel(int rotation) {
        return new MouseWheelEvent(view, MouseEvent.MOUSE_WHEEL, 0, 0, x(1), y(1), 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }

    private KeyEvent pressed(int keyCode, int modifiers) {
        return new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private KeyEvent typed(char c) {
        return new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
    }

    private int x(int column) {
        return column * fonts.cellWidth() + 1;
    }

    private int y(int row) {
        return row * fonts.cellHeight() + 1;
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.TerminalViewInteractionTest`
Expected: FAIL — compilation errors (`handleMouse`, `setClipboard`, `setLinkOpener`, `topRow`, `exited`, `selectedText`, `find`, `FindResult`, …).

- [ ] **Step 3: Add `FindResult` and `copyOnSelect`**

`moray-terminal/src/main/java/dev/moray/terminal/FindResult.java`:
```java
package dev.moray.terminal;

/** The outcome of a find: how many matches, which one is current (1-based, 0 when none), and a regex error or null. */
public record FindResult(int count, int current, String error) {
}
```

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalOptions.java`, add `boolean copyOnSelect` as the last record component (after `int scrollback`), and change the end of `defaults()` to `OptionAsMeta.LEFT, 10_000, false);`.

- [ ] **Step 4: Replace `TerminalView`**

Replace the whole of `moray-terminal/src/main/java/dev/moray/terminal/TerminalView.java` with:
```java
package dev.moray.terminal;

import com.jediterm.core.input.MouseEvent.Type;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;

/**
 * The Swing component that shows a {@link TerminalSession}: painting, keyboard, mouse, selection, scrollback, search
 * and prompt jumps.
 */
public final class TerminalView extends JComponent {
    private static final int FRAME_MILLIS = 8;
    private static final int BLINK_MILLIS = 530;
    private static final int WHEEL_LINES = 3;

    private final TerminalSession session;
    private final TerminalOptions options;
    private final FontSet fonts;
    private final TerminalPainter painter;
    private final KeyEncoder keys;
    private final boolean macOs;
    private final Viewport viewport = new Viewport();
    private final Color matchColor;
    private final Color currentMatchColor;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Timer frameTimer;
    private final Timer blinkTimer;
    private final TerminalSession.Listener listener = new TerminalSession.Listener() {
        @Override
        public void screenChanged() {
            dirty.set(true);
        }
    };
    private boolean blinkOn = true;
    private boolean suppressNextTyped;
    private boolean leftAltHeld;
    private boolean rightAltHeld;
    private Selection selection;
    /** Where a click-drag started; becomes the selection on the first drag. */
    private Selection pendingAnchor;
    private List<TerminalSearch.Match> matches = List.of();
    private int currentMatch = -1;
    private volatile boolean exited;
    private Runnable onCloseRequest = () -> { };
    private Supplier<String> clipboardReader = TerminalView::readSystemClipboard;
    private Consumer<String> clipboardWriter = TerminalView::writeSystemClipboard;
    private Consumer<String> linkOpener = TerminalView::openInBrowser;

    public TerminalView(TerminalSession session, TerminalOptions options) {
        this.session = session;
        this.options = options;
        this.fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
        this.painter = new TerminalPainter(fonts, options.palette());
        this.macOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
        this.keys = new KeyEncoder(options.optionAsMeta(), macOs);
        Color yellow = options.palette().ansi().get(3);
        this.matchColor = CellStyle.blend(yellow, options.palette().background(), 0.7f);
        this.currentMatchColor = CellStyle.blend(yellow, options.palette().background(), 0.35f);
        this.frameTimer = new Timer(FRAME_MILLIS, e -> {
            if (dirty.getAndSet(false)) {
                repaint();
            }
        });
        this.blinkTimer = new Timer(BLINK_MILLIS, e -> {
            blinkOn = !blinkOn;
            repaint();
        });

        setOpaque(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(options.palette().background());
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeSessionToFit();
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                handleMouse(e);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                restartBlink();
            }

            @Override
            public void focusLost(FocusEvent e) {
                leftAltHeld = false;
                rightAltHeld = false;
                repaint();
            }
        });
        session.exitFuture().thenAccept(code -> {
            exited = true;
            dirty.set(true);
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        session.addListener(listener);
        frameTimer.start();
        blinkTimer.start();
    }

    @Override
    public void removeNotify() {
        frameTimer.stop();
        blinkTimer.stop();
        session.removeListener(listener);
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(session.columns() * fonts.cellWidth(), session.rows() * fonts.cellHeight());
    }

    /** Pastes text into the program (bracketed when it asked) and returns the view to the live screen. */
    public void paste(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        viewport.follow();
        session.paste(text);
        repaint();
    }

    public Optional<String> selectedText() {
        return selection == null ? Optional.empty() : Optional.of(session.text(selection));
    }

    public void copySelection() {
        selectedText().filter(text -> !text.isEmpty()).ifPresent(clipboardWriter);
    }

    /** Finds matches in the scrollback and screen and shows the newest one. */
    public FindResult find(String query, boolean regex, boolean caseSensitive) {
        try {
            matches = session.search(query, regex, caseSensitive);
        } catch (PatternSyntaxException invalid) {
            matches = List.of();
            currentMatch = -1;
            repaint();
            return new FindResult(0, 0, invalid.getDescription());
        }
        currentMatch = matches.size() - 1;
        revealCurrentMatch();
        return findResult();
    }

    /** Moves to the next newer match, wrapping around. */
    public FindResult findNext() {
        return stepMatch(1);
    }

    /** Moves to the next older match, wrapping around. */
    public FindResult findPrevious() {
        return stepMatch(-1);
    }

    public void clearFind() {
        matches = List.of();
        currentMatch = -1;
        repaint();
    }

    /** Scrolls so the nearest prompt above the view is at the top. */
    public void scrollToPreviousPrompt() {
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        List<Long> prompts = session.promptRows();
        for (int i = prompts.size() - 1; i >= 0; i--) {
            if (prompts.get(i) < snapshot.firstRow()) {
                viewport.showAtTop(prompts.get(i), snapshot);
                repaint();
                return;
            }
        }
    }

    /** Scrolls so the next prompt below the top of the view is at the top, or back to the live screen. */
    public void scrollToNextPrompt() {
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        for (long prompt : session.promptRows()) {
            if (prompt > snapshot.firstRow()) {
                viewport.showAtTop(prompt, snapshot);
                repaint();
                return;
            }
        }
        viewport.follow();
        repaint();
    }

    /** Called when a key is pressed after the program has exited; the owner closes the view. */
    public void setOnCloseRequest(Runnable action) {
        this.onCloseRequest = action;
    }

    @Override
    protected void paintComponent(Graphics g) {
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        CursorStyle style = CursorStyle.effective(snapshot.cursorShape(), options.cursorStyle());
        boolean blinks = CursorStyle.effectiveBlink(snapshot.cursorShape(), options.cursorBlink());
        boolean focused = isFocusOwner();
        boolean on = !blinks || !focused || blinkOn;
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused),
            highlights(snapshot), getWidth(), getHeight());
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        handleKey(e);
        if (!e.isConsumed()) {
            super.processKeyEvent(e);
        }
    }

    void handleKey(KeyEvent e) {
        trackAltKeys(e);
        if (e.getID() == KeyEvent.KEY_PRESSED && handleViewShortcut(e)) {
            suppressNextTyped = true;
            e.consume();
            return;
        }
        if (exited) {
            if (e.getID() == KeyEvent.KEY_PRESSED && !isModifierOnly(e.getKeyCode())) {
                onCloseRequest.run();
            }
            e.consume();
            return;
        }
        KeyInput input = new KeyInput(e.getKeyCode(), e.getKeyChar(), e.getModifiersEx(), leftAltHeld, rightAltHeld);
        byte[] bytes = switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                byte[] pressed = keys.pressed(input, session::codeForKey);
                suppressNextTyped = pressed != null; // re-decided on every press
                yield pressed;
            }
            case KeyEvent.KEY_TYPED -> {
                if (suppressNextTyped) {
                    suppressNextTyped = false;
                    e.consume();
                    yield null;
                }
                yield keys.typed(input);
            }
            default -> null;
        };
        if (bytes != null) {
            session.write(bytes);
            viewport.follow();
            selection = null;
            restartBlink();
            e.consume();
        }
    }

    void handleMouse(MouseEvent e) {
        Type type = typeOf(e);
        if (type == null || (type == Type.MOVED && !session.mouseReporting())) {
            return;
        }
        ScreenSnapshot snapshot = session.snapshot(viewport.topRow());
        int column = Math.max(0, Math.min(snapshot.width() - 1, e.getX() / fonts.cellWidth()));
        int row = Math.max(0, Math.min(snapshot.height() - 1, e.getY() / fonts.cellHeight()));
        long absoluteRow = snapshot.firstRow() + row;
        if (type == Type.PRESSED) {
            requestFocusInWindow();
        }
        boolean linkModifier = macOs ? e.isMetaDown() : e.isControlDown();
        MouseRouting.Action action = MouseRouting.decide(type, buttonOf(e), e.getClickCount(), e.isShiftDown(),
            linkModifier, session.mouseReporting(), snapshot.alternateBuffer());
        switch (action) {
            case REPORT -> session.reportMouse(column, row - snapshot.scrollOffset(), jediEvent(e, type));
            case START_SELECTION -> {
                selection = null;
                pendingAnchor = Selection.at(absoluteRow, column, e.isAltDown());
            }
            case SELECT_WORD -> {
                selection = session.wordSelection(absoluteRow, column);
                pendingAnchor = null;
            }
            case SELECT_LINE -> {
                selection = session.lineSelection(absoluteRow);
                pendingAnchor = null;
            }
            case EXTEND_SELECTION -> {
                if (selection == null && pendingAnchor != null) {
                    selection = pendingAnchor;
                }
                if (selection != null) {
                    selection = selection.withFocus(absoluteRow, column);
                }
            }
            case END_SELECTION -> {
                pendingAnchor = null;
                if (options.copyOnSelect() && selection != null) {
                    copySelection();
                }
            }
            case OPEN_LINK -> session.linkAt(absoluteRow, column).ifPresent(linkOpener);
            case SCROLL_VIEW -> scrollBy(((MouseWheelEvent) e).getWheelRotation() * WHEEL_LINES);
            case SEND_ARROWS -> sendArrows(((MouseWheelEvent) e).getWheelRotation());
            case NONE -> {
                // nothing to do locally
            }
        }
        repaint();
    }

    void resizeSessionToFit() {
        GridSize grid = GridSize.fit(getWidth(), getHeight(), fonts.cellWidth(), fonts.cellHeight());
        session.resize(grid.columns(), grid.rows());
        dirty.set(true);
    }

    void setClipboard(Supplier<String> reader, Consumer<String> writer) {
        this.clipboardReader = reader;
        this.clipboardWriter = writer;
    }

    void setLinkOpener(Consumer<String> opener) {
        this.linkOpener = opener;
    }

    long topRow() {
        return viewport.topRow();
    }

    boolean exited() {
        return exited;
    }

    /** Copy, paste, prompt jumps and page scrolling; plan 3 moves these into the configurable keymap. */
    private boolean handleViewShortcut(KeyEvent e) {
        int code = e.getKeyCode();
        boolean primary = macOs ? e.isMetaDown() : e.isControlDown() && e.isShiftDown();
        if (primary && code == KeyEvent.VK_C) {
            copySelection();
            return true;
        }
        if (primary && code == KeyEvent.VK_V) {
            paste(clipboardReader.get());
            return true;
        }
        if (primary && code == KeyEvent.VK_UP) {
            scrollToPreviousPrompt();
            return true;
        }
        if (primary && code == KeyEvent.VK_DOWN) {
            scrollToNextPrompt();
            return true;
        }
        if (!primary && e.isShiftDown() && code == KeyEvent.VK_PAGE_UP) {
            scrollBy(-(session.rows() - 1));
            return true;
        }
        if (!primary && e.isShiftDown() && code == KeyEvent.VK_PAGE_DOWN) {
            scrollBy(session.rows() - 1);
            return true;
        }
        return false;
    }

    private List<TerminalPainter.Highlight> highlights(ScreenSnapshot snapshot) {
        List<TerminalPainter.Highlight> highlights = new ArrayList<>();
        long first = snapshot.firstRow();
        long last = first + snapshot.height() - 1;
        for (int i = 0; i < matches.size(); i++) {
            TerminalSearch.Match match = matches.get(i);
            if (match.row() >= first && match.row() <= last) {
                highlights.add(new TerminalPainter.Highlight((int) (match.row() - first), match.startColumn(),
                    match.endColumn(), i == currentMatch ? currentMatchColor : matchColor));
            }
        }
        if (selection != null) {
            for (int row = 0; row < snapshot.height(); row++) {
                int[] columns = selection.columnsOn(first + row, snapshot.width());
                if (columns != null) {
                    highlights.add(new TerminalPainter.Highlight(row, columns[0], columns[1],
                        options.palette().selection()));
                }
            }
        }
        return highlights;
    }

    private void scrollBy(int lines) {
        viewport.scrollBy(lines, session.snapshot(viewport.topRow()));
        repaint();
    }

    private void sendArrows(int rotation) {
        byte[] arrow = session.codeForKey(rotation < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN, 0);
        if (arrow == null) {
            return;
        }
        for (int i = 0; i < Math.abs(rotation); i++) {
            session.write(arrow);
        }
    }

    private FindResult stepMatch(int direction) {
        if (matches.isEmpty()) {
            return findResult();
        }
        currentMatch = Math.floorMod(currentMatch + direction, matches.size());
        revealCurrentMatch();
        return findResult();
    }

    private void revealCurrentMatch() {
        if (currentMatch >= 0) {
            viewport.reveal(matches.get(currentMatch).row(), session.snapshot(viewport.topRow()));
        }
        repaint();
    }

    private FindResult findResult() {
        return new FindResult(matches.size(), currentMatch + 1, null);
    }

    private void trackAltKeys(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_ALT) {
            return;
        }
        boolean down = e.getID() == KeyEvent.KEY_PRESSED;
        if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) {
            rightAltHeld = down;
        } else {
            leftAltHeld = down;
        }
    }

    private void restartBlink() {
        blinkOn = true;
        blinkTimer.restart();
        repaint();
    }

    private static boolean isModifierOnly(int keyCode) {
        return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT
            || keyCode == KeyEvent.VK_ALT_GRAPH || keyCode == KeyEvent.VK_META;
    }

    private static Type typeOf(MouseEvent e) {
        return switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED -> Type.PRESSED;
            case MouseEvent.MOUSE_RELEASED -> Type.RELEASED;
            case MouseEvent.MOUSE_DRAGGED -> Type.DRAGGED;
            case MouseEvent.MOUSE_MOVED -> Type.MOVED;
            case MouseEvent.MOUSE_WHEEL -> Type.WHEEL;
            default -> null;
        };
    }

    private static MouseRouting.Button buttonOf(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            return MouseRouting.Button.LEFT;
        }
        if (SwingUtilities.isMiddleMouseButton(e)) {
            return MouseRouting.Button.MIDDLE;
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            return MouseRouting.Button.RIGHT;
        }
        return MouseRouting.Button.NONE;
    }

    private static com.jediterm.core.input.MouseEvent jediEvent(MouseEvent e, Type type) {
        int modifiers = (e.isShiftDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG : 0)
            | (e.isAltDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG : 0)
            | (e.isControlDown() ? MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG : 0);
        if (e instanceof MouseWheelEvent wheel) {
            int button = wheel.getWheelRotation() < 0 ? MouseButtonCodes.SCROLLUP : MouseButtonCodes.SCROLLDOWN;
            return new com.jediterm.core.input.MouseWheelEvent(button, modifiers, wheel.getUnitsToScroll());
        }
        int button = SwingUtilities.isLeftMouseButton(e) ? MouseButtonCodes.LEFT
            : SwingUtilities.isMiddleMouseButton(e) ? MouseButtonCodes.MIDDLE
            : SwingUtilities.isRightMouseButton(e) ? MouseButtonCodes.RIGHT
            : MouseButtonCodes.RELEASE; // motion with no button held
        return new com.jediterm.core.input.MouseEvent(type, button, modifiers);
    }

    private static String readSystemClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException | IllegalStateException | HeadlessException e) {
            return null;
        }
    }

    private static void writeSystemClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException | HeadlessException e) {
            // The clipboard is busy or unavailable; plan 4 logs this.
        }
    }

    private static void openInBrowser(String uri) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(uri));
            }
        } catch (IOException | URISyntaxException | UnsupportedOperationException e) {
            // Nothing can open this link; plan 4 logs this.
        }
    }
}
```

- [ ] **Step 5: Run the new tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.TerminalViewInteractionTest`
Expected: PASS (18 tests).

- [ ] **Step 6: Run the module tests (the existing `TerminalViewTest` must still pass)**

Run: `./gradlew :moray-terminal:check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add moray-terminal/src
git commit -m "Wire scrolling, mouse, selection, clipboard, links, find, prompt jumps and exit into the view"
```

### Task 10: The app closes after the exit message

**Files:**
- Modify (replace whole file): `moray-app/src/main/java/dev/moray/app/Main.java`

**Interfaces:**
- Consumes: `TerminalView.setOnCloseRequest(Runnable)` (Task 9); the exit message written by the session (Task 3).
- Produces: when the shell exits, the window stays open showing `[process exited with code N]`. The next key press disposes the window, and the existing `windowClosed` handler closes the session and exits. Closing the window directly still exits immediately.

This replaces plan 1's "exit the app the moment the shell's output ends". It also removes the case plan 1's final review noted, where a shell that dies instantly made the app vanish silently. `Main` has no automated test for window behaviour; the build compiles it, and Step 3 is the user's manual check.

- [ ] **Step 1: Replace `Main`**

Replace the whole of `moray-app/src/main/java/dev/moray/app/Main.java` with:
```java
package dev.moray.app;

import dev.moray.terminal.TerminalOptions;
import dev.moray.terminal.TerminalSession;
import dev.moray.terminal.TerminalView;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        TerminalOptions options = TerminalOptions.defaults();
        TerminalSession session = startShellOrExit(options);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Moray");
            TerminalView view = new TerminalView(session, options);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    session.close();
                    System.exit(0);
                }
            });
            // The shell has exited and the user pressed a key after reading the exit message.
            view.setOnCloseRequest(frame::dispose);
            session.addListener(new TerminalSession.Listener() {
                @Override
                public void titleChanged(String title) {
                    SwingUtilities.invokeLater(() -> frame.setTitle(windowTitle(title)));
                }
            });
            frame.setTitle(windowTitle(session.title())); // a title the shell set before the listener existed
            frame.add(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            view.requestFocusInWindow();
        });
    }

    /** The window title for a shell-reported title; "Moray" when the shell has not set one. */
    static String windowTitle(String shellTitle) {
        return shellTitle == null || shellTitle.isBlank() ? "Moray" : shellTitle;
    }

    private static TerminalSession startShellOrExit(TerminalOptions options) throws Exception {
        try {
            return TerminalSession.start(
                DefaultShell.command(System.getProperty("os.name"), System.getenv()),
                System.getenv(), Path.of(System.getProperty("user.home")), 120, 36, options.scrollback());
        } catch (IOException e) {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                "Moray could not start your shell:\n" + e.getMessage(), "Moray", JOptionPane.ERROR_MESSAGE));
            System.exit(1);
            throw e; // not reached: System.exit does not return
        }
    }
}
```

- [ ] **Step 2: Build and test**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (`MainTest` and `DefaultShellTest` still pass).

- [ ] **Step 3: Manual check (the user, not the implementer — never launch the app from a subagent)**

Run `./gradlew :moray-app:run` and type `exit`. The window should stay open showing `[process exited with code 0]` until you press a key, then close.

- [ ] **Step 4: Commit**

```bash
git add moray-app/src
git commit -m "Keep the window open on the exit message until a key is pressed"
```

### Task 11: Font fallback coverage, lone surrogates and spec notes

**Files:**
- Create: `moray-terminal/src/test/java/dev/moray/terminal/TestFonts.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/RunBuilder.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/RunBuilderTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/FontSetTest.java`
- Modify: `docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md` (§11)

**Interfaces:**
- Consumes: `FontSet`, `RunBuilder`, `Run` (plan 1).
- Produces: `RunBuilder` renders a surrogate without its partner as U+FFFD instead of passing a raw surrogate value to `FontSet.fontFor`.

**Why:** plan 1's final review found three gaps:
- Nothing tested the fallback font on a glyph the primary font really lacks.
- Nothing tested a run splitting because the font changed.
- A lone surrogate reached `fontFor` as an invalid code point.

It also recorded two limitations that belong in the spec. On this machine's JetBrains Runtime, `JetBrains Mono.canDisplay` is **false** for the Nerd Font git icon U+F113 and true for CJK and emoji. So fallback fonts matter for Nerd Font icons, while CJK and emoji already render through macOS's own cascade. The new tests skip on machines without a Nerd Font, such as CI.

**Source note:** write every non-ASCII test character as an ASCII cast, such as `(char) 0xF113`. Never type the raw character or a `\u` escape for private-use or surrogate code points.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/TestFonts.java`:
```java
package dev.moray.terminal;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Optional;

/** Fonts that exist only on some machines; tests that need them skip elsewhere. */
final class TestFonts {
    /** The Nerd Font git icon, missing from JetBrains Mono. */
    static final int GIT_ICON = 0xF113;

    private TestFonts() {
    }

    /** An installed Nerd Font family that has the git icon, if any (CI machines usually have none). */
    static Optional<String> nerdFont() {
        return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .filter(name -> name.contains("Nerd Font"))
            .filter(name -> new Font(name, Font.PLAIN, 14).canDisplay(GIT_ICON))
            .findFirst();
    }
}
```

In `moray-terminal/src/test/java/dev/moray/terminal/FontSetTest.java`, add:
```java
    @Test
    void aNerdFontIconFallsBackToAConfiguredNerdFont() {
        String nerdFont = TestFonts.nerdFont().orElse(null);
        assumeTrue(nerdFont != null, "no Nerd Font installed");
        assumeTrue(!new Font(MONO, Font.PLAIN, 14).canDisplay(TestFonts.GIT_ICON), MONO + " already has the icon");

        FontSet fonts = new FontSet(MONO, 14f, List.of(nerdFont), true);

        assertThat(fonts.fontFor(TestFonts.GIT_ICON, false, false).getFamily()).isEqualTo(nerdFont);
        assertThat(fonts.fontFor('a', false, false).getFamily()).isEqualTo(MONO);
    }
```

In `moray-terminal/src/test/java/dev/moray/terminal/RunBuilderTest.java`, add the imports `java.awt.Font` and `static org.junit.jupiter.api.Assumptions.assumeTrue`, and add:
```java
    @Test
    void aFontChangeStartsANewRun() {
        String nerdFont = TestFonts.nerdFont().orElse(null);
        assumeTrue(nerdFont != null, "no Nerd Font installed");
        assumeTrue(!new Font("JetBrains Mono", Font.PLAIN, 14).canDisplay(TestFonts.GIT_ICON), "primary has the icon");
        RunBuilder withFallback = new RunBuilder(new FontSet("JetBrains Mono", 14f, List.of(nerdFont), true), palette);
        char gitIcon = (char) TestFonts.GIT_ICON;

        List<Run> runs = withFallback.build(new char[] {'a', gitIcon, 'b'}, styles(TextStyle.EMPTY, 3), 3);

        assertThat(runs).hasSize(3);
        assertThat(runs.get(1).font().getFamily()).isEqualTo(nerdFont);
    }

    @Test
    void loneSurrogatesRenderAsTheReplacementCharacter() {
        char high = (char) 0xD83D;
        char low = (char) 0xDE80;
        String replacement = String.valueOf((char) 0xFFFD);

        Run run = builder.build(new char[] {'a', high, 'b', low, ' ', ' '}, styles(TextStyle.EMPTY, WIDTH), WIDTH)
            .getFirst();

        assertThat(new String(run.text())).isEqualTo("a" + replacement + "b" + replacement + "  ");
    }

    @Test
    void aHighSurrogateInTheLastColumnRendersAsTheReplacementCharacter() {
        char high = (char) 0xD83D;

        List<Run> runs = builder.build(new char[] {'a', 'b', 'c', 'd', 'e', high}, styles(TextStyle.EMPTY, WIDTH), WIDTH);

        assertThat(new String(runs.getLast().text())).endsWith(String.valueOf((char) 0xFFFD));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.RunBuilderTest' --tests 'dev.moray.terminal.FontSetTest'`
Expected: FAIL — the two surrogate tests fail (the raw surrogates are copied into the run). On a machine with a Nerd Font installed, the two fallback tests pass already (the fallback works; they only lacked coverage); elsewhere they are skipped.

- [ ] **Step 3: Replace lone surrogates in `RunBuilder`**

In `moray-terminal/src/main/java/dev/moray/terminal/RunBuilder.java`:

1. Add after `MAX_CACHED_STYLES`:
```java
    private static final char REPLACEMENT_CHARACTER = (char) 0xFFFD;
```

2. In `build(char[], TextStyle[], int)`, change the `if (first == CharUtils.DWC) { first = ' '; }` block inside the `else` branch to:
```java
                if (first == CharUtils.DWC) {
                    first = ' ';
                } else if (Character.isSurrogate(first)) {
                    first = REPLACEMENT_CHARACTER; // half of a pair whose partner is missing
                }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.RunBuilderTest' --tests 'dev.moray.terminal.FontSetTest'`
Expected: PASS (the Nerd Font tests pass on this Mac, skipped where no Nerd Font is installed).

- [ ] **Step 5: Record the findings in the spec**

In `docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md`, append this block after the last bullet of §11 ("Risks and items the plan must verify"):
```markdown

**Findings from plans 1–2 (2026-09-10):**
- **Strikethrough (§4.2) is not shown.** jediterm-core 3.76's `TextStyle` has no strikethrough option, so SGR 9 is dropped before Moray sees it. Supporting it would mean tracking SGR 9 in the shell-integration filter; deferred.
- **Font fallback on macOS.** The JetBrains Runtime reports `canDisplay` as true for CJK and emoji in any font, because the system font cascade draws them. The configured fallback list therefore takes effect only for glyphs the primary font truly lacks, in practice Nerd Font icons. Plan 4's config should make naming a Nerd Font fallback easy.
- **Search matches do not span soft-wrapped rows** (§4.5); matching is per physical row.
- **Shell integration (§4.6).** JediTerm ignores OSC 7 and OSC 133; Moray rewrites them, and DECSCUSR 0, into OSC 1341 custom commands so they arrive in emulator order.
```

- [ ] **Step 6: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add moray-terminal/src docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md
git commit -m "Cover Nerd Font fallback, replace lone surrogates, and record spec findings"
```

---

## Spec coverage for this plan

| Spec section | Covered here | Deferred to |
|---|---|---|
| §4.1 exit message in the pane; close on next key | Tasks 3, 9, 10 | — |
| §4.2 DECSCUSR 0 restores the configured cursor | Tasks 1, 2 | strikethrough: not supported by jediterm-core (Task 11 note) |
| §4.3 mouse reporting, right-click, Shift bypass, no negative coordinates | Tasks 7, 8, 9 | — |
| §4.3 bracketed paste; wheel (scroll / arrows / report) | Tasks 7, 8, 9 | — |
| §4.4 click-drag, double-click word, triple-click line, Alt block; copy; `copy_on_select`; scrollback | Tasks 3, 4, 6, 8, 9 | scrollbar is a display-only indicator; `copy_on_select` config key → plan 4 |
| §4.5 search: plain/regex, case, scrollback, highlight all + current, wrap-around | Tasks 5, 6, 9 | find bar UI → plan 3 |
| §4.6 OSC 7, OSC 133 prompt jumps, OSC 8 + detected URLs with ⌘/Ctrl-click, title | Tasks 1, 2, 7, 9 | new tabs start in the working directory, status bar → plan 3 |
| §5.1 copy / paste / prompt-jump shortcuts | hard-wired in Task 9 | configurable keymap → plan 3 |
| §8 tests for all of the above | every task | — |
| Plan 1 final-review items routed to plan 2 | Tasks 2, 3, 6, 10, 11 | — |
