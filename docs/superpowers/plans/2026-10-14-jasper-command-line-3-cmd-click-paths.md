# Jasper Command Line, Plan 3: Cmd-click File Paths — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Complete on 2026-10-14** (written 2026-10-14) on `claude/default-editor` (at `main` `0f3b3b4b`, plans 1 and 2 merged). Execution rulings: when a position follows, a path restarts after an unclosed mid-word `(`, so a Java stack frame `at com.foo.Bar.baz(Bar.java:42)` gives `Bar.java` at line 42, and a path starts after an `=` only when no `/` or `\` comes before it, so `--config=./x.toml` clicks and `/data/dt=2024-01-01/part.parquet` stays whole (Decision 13); on Windows a path, a `~/` remainder or a pane directory that names a UNC host other than `wsl$` or `wsl.localhost` (compared in ASCII only) resolves to nothing, so a click never makes Windows authenticate to a host named by program output; `LinkDetector` and `PathDetector` share one trailing-punctuation set; the view tests also click a soft-wrapped path and an OSC 8 `file://` link. Known limits: where the desktop supports neither edit nor reveal (often on Linux), a click under System default app only logs; the existence check follows a symbolic link, also to a network share; a click just before quitting can still open its file. Final review: a plain-text `file://` URL's trailing `:LINE[:COLUMN]` gives the line, as IntelliJ's console does; the SDK's editor contract names the Cmd-click route; log lines on the editor route carry no path.

**Goal:** Cmd-click on macOS, or Ctrl-click elsewhere, on a file path in terminal output (`src/Foo.java:42`, `./notes.md`,
`/tmp/x.log`, `~/a.txt`, `Foo.cs(42,5)`, a local `file://` link) opens that file in the default editor at the line.

**Architecture:**
- **Detection (terminal module).** Detection is pure text processing in `jasper-terminal`'s `internal.text`
  (`PathDetector`). It runs over the same soft-wrap-joined `RowText` URL detection uses, after URL detection.
- **Callback (`TerminalView`).** The view reports a new supported value, `dev.jasper.terminal.view.FileLink(path, line,
  column)`, through `TerminalView.setFileLinkHandler`.
- **Opening (app).** The app resolves the path against the pane's local directory in a pure `workspace.FileLinks`
  helper. It checks the file exists off the UI thread, then opens it through `PluginRuntime.open` with the line and the
  pane's window. That goes to the plugin editor first, then the desktop.

**Tech Stack:** Java 25 (JBR), Swing, JediTerm 3.76 (`jasper-terminal` internal only), JUnit 5, AssertJ.

**Spec:** [`docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md`](../specs/2026-10-13-jasper-command-line-and-default-editor-design.md)
(section 7, plus 6.4 for the file editor route, 8 and 9). Plans 1 and 2 are merged.

## Global Constraints

- **Gestures (spec 7).** Cmd-click on macOS, Ctrl-click elsewhere, on text that is not a URL.
  - **Forms:** `PATH`, `PATH:LINE`, `PATH:LINE:COLUMN`, `PATH(LINE)` and `PATH(LINE,COLUMN)`.
  - **What counts as a path:** it is absolute, starts with `./`, `../` or `~/`, or is relative and has a `/` or an
    extension (`Foo.java:42`).
  - **Windows:** `C:\…` and backslash paths count on Windows only.
  - **Wraps:** detection follows soft wraps, as URL detection does.
  - **Feedback:** no hover feedback.
- **Precedence on a click.**
  1. An OSC 8 link on the cell.
  2. Otherwise a plain-text URL across the wrapped line.
  3. Otherwise a path.

  An OSC 8 `http`/`https`/`ftp`/`mailto` link or plain-text URL opens as today. A `file://` link, from OSC 8 or plain
  text, whose host is empty, `localhost` or this machine's name becomes a `FileLink` for its decoded absolute path with
  line 0. Any other OSC 8 target, a `file://` with another host, and a click on a refused OSC 8 cell open nothing.
- **`FileLink(String path, int line, int column)`.**
  - `path` is the text as written, with no resolving: `~/`, `./` and relative paths stay as they are. For a `file://`
    link it is the decoded absolute path.
  - `line` and `column` are 1-based; 0 means none. A digit run longer than 9 is not a line, so the value always fits an
    int.
  - The record rejects a blank path and negative numbers.
- **Trailing punctuation.**
  - `.`, `,`, `;`, `:`, `!`, `?`, `)`, `]`, `}`, `'` and `"` after a path or its position are not part of it. For
    example, `Foo.java:42:` gives line 42, and `(see ./notes.md)` gives `./notes.md`.
  - A path ends at whitespace or at `<>"'` and the backtick (the URL regex's exclusion set). Paths with spaces are found
    only through OSC 8 `file://` links.
- **Lock, row and API rules (AGENTS.md).**
  - Path matching runs **outside** `TerminalTextBuffer`'s lock. Build the joined `RowText` under the lock, within
    `LogicalLine`'s bounds, then match after unlocking, as `BufferQueries.search` does.
  - Rows are absolute rows.
  - No public method in `jasper-terminal` takes or returns a JediTerm type.
  - `FileLink` joins the supported allowlist in `build.gradle.kts` (`verifyTerminalArchitecture`) and the
    `jasper-terminal/README.md` table.
  - The terminal module's `check` runs JavaDoc doclint, so every new public member has Javadoc.
- **Mouse reporting is unchanged.**
  - On macOS a Cmd-click is local even when the program reports the mouse.
  - Elsewhere, while a program reports the mouse, Ctrl-click goes to the program and Ctrl+Shift-click is local.
  - A modified click that finds nothing starts a selection, as today.
- **Resolution (spec 7).**
  - A relative path resolves against the pane's local current directory: OSC 7, under the existing local and remote rule.
    The pane's `session.workingDirectory()` is empty for a remote or attached session.
  - `~/` resolves against `user.home`.
  - A pane whose directory is remote, or a provided session, tries nothing.
  - A local pane that has not reported a directory resolves against the directory it started in (deviation 1).
  - The existence check runs off the UI thread. Only an existing regular file opens, through the file editor route
    (`PluginRuntime.open` with the line and the clicked pane's window). Anything else does nothing and shows nothing.
- **Package and thread rules.**
  - `dev.jasper.app.workspace` may not depend on `dev.jasper.app.plugins` or `dev.jasper.app.application`.
  - The click arrives on the EDT. `JasperApplication` hops to a worker for the check and the open.
  - SDK types stay in `dev.jasper.app.plugins`.
- **No GUI in tests.** Tests never launch the GUI, an editor or the desktop. View tests drive `TerminalView.handleMouse`,
  app tests inject the opener, and nothing calls `Desktop`.
- **Migration ledger.** New Java files that `ThemeMigrationCoverageTest` matches get `docs/design/theme-engine/migration.csv`
  rows, in the `nonvisual-helper` form.
- **Branch.** Work in `.claude/worktrees/default-editor` on `claude/default-editor`, never on `main`. Every commit
  message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- **Source hygiene (AGENTS.md).** No raw control, private-use or unpaired surrogate characters in `.java` sources. Write
  escapes such as `"\u00fc"`, and write JediTerm's continuation cell as the Java escape for U+E000.

## Planned deviations from the spec

Recorded here and, when the plan completes, in the spec's status line and `docs/STATUS.md`:

1. **A local pane without a reported directory** resolves relative paths against the directory it started in. The pane
   knows that directory, and the existence check guards against a wrong guess. The spec says only "the pane's local
   current directory (OSC 7)", and Jasper's shell integration reports it in most shells.
2. **The column is detected but not used.** The terminal reports it in `FileLink`, but the SDK's `FileEditor.edit`
   (plan 2) takes a line only, so the column stops at the app.
3. **Plain-text `file://` URLs become file links** for local hosts, instead of going to the browser. Non-local
   `file://` URLs, plain or OSC 8, open nothing. Spec 7 names OSC 8 `file://` links; plain text follows the same rule.
4. **Paths with spaces** are found only through OSC 8 `file://` links. In plain text a path ends at whitespace.
5. **`FileLink` is a top-level supported type** in `dev.jasper.terminal.view`, added to the allowlist like
   `session.RemoteDirectory`.
6. **A Cmd-click's desktop fallback edits or reveals the file, never opens it** (`ConfigEditor.editOrReveal`): opening an
   existing `.command`, `.app` or `.exe` reached from printed text or an OSC 8 link would run it. Ruled before execution
   (security).

## Decisions the spec left open (ruled while writing the plan)

1. A leading `(`, `[` or `{` is not part of a path.
2. A path ends at its first `:` (after a Windows drive letter); `:LINE[:COLUMN]` must be followed by the end of the
   word or trailing punctuation, so grep's `file:42:text` gives line 42 and a Unix file name holding `:` cannot be
   clicked.
3. A `(` stays in the name unless it starts `(LINE[,COLUMN])` followed only by trailing punctuation, so
   `tmp/a(1).txt` is one path.
4. An extension is a dot after the name's first character followed only by letters and digits, at least one a letter;
   the last name is not empty, `.` or `..`. So `1.2.3`, `.bashrc` and `Makefile` are not paths; `e.g` is, harmlessly
   (the existence check).
5. In the colon forms a digit run longer than 9 drops the line or column and keeps the path; in the parenthesis
   form an over-long number is no position, so rule 3 keeps the `(` in the name.
6. On macOS and Linux a path holding `\` is refused.
7. An OSC 8 link on the cell decides alone: an `http` link opens the browser even if its text looks like a path; a
   `file://` link (OSC 8 or plain) to another host opens nothing and never falls back to the path in its text.
8. `PluginRuntime.open(..., Optional.empty(), ...)` means no window (the editor chooses), not the active window.
9. In a WSL pane an absolute Linux path is taken as a Windows path, so it normally opens nothing; relative paths and
   `file://` links go through the WSL mapper.
10. A failure after the existence check (no desktop action, say) is logged at WARNING and shown to nobody.
11. AGENTS.md's verified JediTerm 3.76 list gains that `HyperlinkStyle` equality ignores the link (Task 4).
12. Spec 7's "line 1" for `file://` links is met by opening with no line, which every editor reads as the file's start.
13. A path starts after an `=` in its word only if no `/` or `\` comes before it (`--config=./x.toml`), and, when a position follows, after the last mid-word `(` that no `)` closes inside the path
    (`at com.foo.Bar.baz(Bar.java:42)` gives `Bar.java`, line 42; `tmp/a(1).txt` and `tmp/a(b)/c.txt` stay whole).
    Ruled during Task 1's review.

## Review Focus

These are inputs the spec implies but no other test pins, most likely to bite first. Each has its test in the owning
task:

1. **Compiler output.** `src/Foo.java:42:5: error: …` and MSBuild's `Foo.cs(42,5): error …`: a click on either opens
   the file at line 42, with the trailing `:` or `):` dropped. (Tasks 1 and 3.)
2. **A path soft-wrapped across two rows** in a narrow window opens as one path, from a click on either row. (Task 2.)
3. **A remote pane.** An ssh session whose shell reports a remote OSC 7 prints `src/Foo.java:3`. A Cmd-click opens
   nothing locally, even when a same-named local file exists. (Task 3.)
4. **Surrounding punctuation and quotes.** `(see ./notes.md)`, `"./a.txt"`, `` `./a.txt` `` and `./a.txt,` each find
   `./a.txt` or `./notes.md`. A version string like `1.2.3`, a bare word and `http://…` are not paths. (Task 1.)
5. **URLs and selection are unchanged.** A modified click on a URL still opens the browser. A modified click on text
   that is no path still starts a selection. With no file handler set, a path click behaves as before. (Task 2.)

## File structure

| Path | Responsibility |
|---|---|
| `jasper-terminal/.../internal/text/PathDetector.java` | The path forms over a `RowText` and a click column: a pure function, `Optional<Match>` with the text range. |
| `jasper-terminal/.../internal/text/LinkDetector.java` | URL detection; `file://` handled with the path rules. |
| `jasper-terminal/.../internal/emulation/BufferQueries.java`, `internal/TerminalAccess.java` | `fileLinkAt(absoluteRow, column)`: OSC 8 and plain `file://` under the host rule, then paths over the joined line. Rows are captured under the lock and matched after it. |
| `jasper-terminal/.../view/FileLink.java`, `internal/text/FileTarget.java` | The supported value; the internal value `fileLinkAt` returns. |
| `jasper-terminal/.../internal/shell/DirectoryProvenance.java` | `localPath(uri)`: the existing local-host rule for one `file://` URI (kept as a `TerminalAccess` field). |
| `jasper-terminal/.../internal/emulation/JediCellReader.java` | Fixes a pre-existing bug: its style cache gave every same-coloured OSC 8 link the first link's target (JediTerm's `HyperlinkStyle` equality ignores the link). |
| `jasper-terminal/.../view/{TerminalView,MouseController}.java` | `setFileLinkHandler`; on a modified press, a URL first, then a file link, then a selection. |
| `build.gradle.kts`, `jasper-terminal/README.md`, `docs/terminal-architecture.md` | Allowlist, supported table, the mouse and OSC 8 paragraphs. |
| `jasper-app/.../workspace/FileLinks.java` | Pure resolution: path text, the pane's local directory and home give `Optional<Path>`. |
| `jasper-app/.../workspace/{WindowContent,TerminalPane}.java` | Install and clear the view's handler; the window hook `onOpenFile`. |
| `jasper-app/.../plugins/{PluginRuntime,FileEditors}.java` | `open(Path, OptionalInt, Optional<UUID>, Consumer<Path>)` and `openFromWorker(...)` with the line. |
| `jasper-app/.../application/{JasperApplication,Rehearsal}.java` | The worker hop, the regular-file check and the open; a silent default for the rehearsal window. |
| Docs | `docs/editor.md`, `docs/configuration.md` (if it describes clicks), `docs/STATUS.md`, the spec's status line. |

Paths below abbreviate `jasper-terminal/src/main/java/dev/jasper/terminal/` as `terminal/`, its tests as
`terminal-test/`, and `jasper-app/src/main/java/dev/jasper/app/` as `app/` (tests `app-test/`).

## Interfaces at a glance

```java
// Task 1: terminal, internal.text (pure)
public final class PathDetector {
    /** A path the click column falls in: its text, line and column (0 when none), and its index range in row.text(). */
    public record Match(String path, int line, int column, int start, int end) { }
    public static Optional<Match> pathAt(RowText row, int column, boolean windows);
}

// Task 2: terminal, supported and internal
public record FileLink(String path, int line, int column)                     // dev.jasper.terminal.view
public void setFileLinkHandler(Consumer<FileLink> handler)                    // TerminalView; null removes it; EDT
public record FileTarget(String path, int line, int column)                   // internal.text: what fileLinkAt finds; MouseController
                                                                              //   builds the supported FileLink from it (view -> internal only)
public Optional<FileTarget> fileLinkAt(long absoluteRow, int column, boolean windows)                     // TerminalAccess (internal)
public Optional<FileTarget> fileLinkAt(long absoluteRow, int column, boolean windows,
                                       Function<String, Optional<Path>> localFiles)                       // BufferQueries (internal)
public Optional<Path> localPath(String fileUri)                               // DirectoryProvenance: the OSC 7 host rule for one file URI

// Task 3: app
public final class FileLinks {                                                // dev.jasper.app.workspace (pure)
    public static Optional<Path> resolve(String path, Optional<Path> directory, Path home, boolean windows);
}
public java.util.function.BiConsumer<Path, Integer> onOpenFile                // WindowContent hook (window id is the content's own)
public void open(Path file, OptionalInt line, Optional<UUID> window, Consumer<Path> desktop)   // PluginRuntime, worker
boolean openFromWorker(Path file, OptionalInt line, Supplier<Optional<UUID>> window, Consumer<Path> desktop)  // FileEditors;
                                                                              //   true when the provided editor opened it
void openFileAt(Path file, int line, UUID window)                             // JasperApplication, package-private for tests; EDT, hops to a worker
```

---

### Task 1: `PathDetector`, the path forms under a click

**Files:**
- Create: `terminal/internal/text/PathDetector.java`, `terminal-test/internal/text/PathDetectorTest.java`
- No `migration.csv` row: `ThemeMigrationCoverageTest` scans `jasper-app`, `jasper-sdk`, `jasper-sdk-testkit` and
  `plugins/*` sources, not `jasper-terminal`.

**Interfaces:**
- Consumes: `internal.text.RowText` (existing): `text()`, and `columns()`/`lastColumns()`, the first and last cell of each
  UTF-16 unit, wide characters folded.
- Produces: `PathDetector.pathAt(RowText row, int column, boolean windows)` → `Optional<PathDetector.Match>`, and
  `PathDetector.Match(String path, int line, int column, int start, int end)`, as in Interfaces at a glance.

The rules, in the order `pathAt` applies them:

1. The click's text index is the one whose cells cover `column`, so a wide character's second cell counts. A click on
   whitespace, `<>"'` or the backtick finds nothing.
2. The **token** is the run around it up to whitespace or `<>"'` and the backtick. A token holding `://` is a URL and
   no path, so `http://x/y` gives nothing even before URL detection has run.
3. Leading `(`, `[` and `{` are dropped, so `(./notes.md)` starts at `.`.
4. On Windows a drive (`C:\` or `C:/`) is kept. The **path** then ends at the first `:`, or at a `(` that starts
   `(LINE)` or `(LINE,COLUMN)` followed only by trailing punctuation. Any other `(` is part of the name
   (`tmp/a(1).txt`).
5. **The position.** `:LINE` and `:LINE:COLUMN` must end at the token's end or at punctuation, so grep's
   `src/Foo.java:42:text` gives line 42. A run of more than 9 digits is no line or column (it would not fit an int):
   the path still matches, without that position. With no position, trailing `.,;:!?)]}'"` is dropped from the path.
6. **Qualification.** On other systems a path holding `\` is refused. The name after the last separator must not be
   empty, `.` or `..`, which refuses `src/`, `~/` and `/`. Then a path with a separator counts: absolute, `./`,
   `../`, `~/`, or relative with a directory. Without one it needs an extension of letters and digits with at least one
   letter (`Foo.java`, not `1.2.3`, `.bashrc` or `Makefile`).
7. The click must fall within the path and its position, from `start` to `end`. A click on the dropped punctuation
   finds nothing.

- [ ] **Step 1: Write the failing test**

`terminal-test/internal/text/PathDetectorTest.java`:

```java
package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.internal.emulation.EmulationFixture;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PathDetectorTest {

    @Test
    void everyFormGivesThePathAndThePositionWrittenAfterIt() {
        assertThat(at("see src/Foo.java here", 6)).contains(new PathDetector.Match("src/Foo.java", 0, 0, 4, 16));
        assertThat(at("src/Foo.java:42", 3)).contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
        assertThat(at("src/Foo.java:42:5", 3)).contains(new PathDetector.Match("src/Foo.java", 42, 5, 0, 17));
        assertThat(at("Foo.cs(42)", 1)).contains(new PathDetector.Match("Foo.cs", 42, 0, 0, 10));
        assertThat(at("Foo.cs(42,5)", 1)).contains(new PathDetector.Match("Foo.cs", 42, 5, 0, 12));
    }

    /** Review Focus 1: compiler output opens at line 42, without the trailing colon or the parenthesis and colon. */
    @Test
    void compilerOutputGivesTheLineAndDropsWhatFollowsIt() {
        assertThat(at("src/Foo.java:42:5: error: cannot find symbol", 2))
            .contains(new PathDetector.Match("src/Foo.java", 42, 5, 0, 17));
        assertThat(at("Foo.cs(42,5): error CS1002: ; expected", 2))
            .contains(new PathDetector.Match("Foo.cs", 42, 5, 0, 12));
        assertThat(at("src/Foo.java:42:    return x;", 2)).as("grep -n output").contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
        assertThat(at("src/Foo.java:42:return x;", 2)).as("grep -n output with no indent")
            .contains(new PathDetector.Match("src/Foo.java", 42, 0, 0, 15));
    }

    /** Review Focus 4: punctuation and quotes around a path are not part of it. */
    @Test
    void surroundingPunctuationAndQuotesAreNotPartOfThePath() {
        assertThat(path("(see ./notes.md)", 6)).contains("./notes.md");
        assertThat(path("(./notes.md)", 3)).contains("./notes.md");
        assertThat(path("\"./a.txt\"", 2)).contains("./a.txt");
        assertThat(path("`./a.txt`", 2)).contains("./a.txt");
        assertThat(path("./a.txt,", 2)).contains("./a.txt");
        assertThat(path("see ./a.txt.", 6)).contains("./a.txt");
        assertThat(at("Foo.java:42:", 2)).map(PathDetector.Match::line).contains(42);
        assertThat(at("(see ./notes.md)", 15)).as("the closing parenthesis").isEmpty();
        assertThat(at("\"./a.txt\"", 0)).as("the quote").isEmpty();
    }

    /** Review Focus 4: a version string, a bare word and a URL are not paths. */
    @Test
    void versionsBareWordsAndUrlsAreNotPaths() {
        assertThat(at("version 1.2.3 ready", 9)).isEmpty();
        assertThat(at("hello world", 2)).isEmpty();
        assertThat(at("Makefile", 2)).isEmpty();
        assertThat(at("http://x/y", 8)).isEmpty();
        assertThat(at("see https://jasper.dev/docs/a.txt", 25)).isEmpty();
        assertThat(at("mailto:me@example.com", 10)).isEmpty();
        assertThat(at(".bashrc", 2)).as("a dot file needs ./ or ~/").isEmpty();
        assertThat(at("src/ ~/ ./ ../ /", 1)).isEmpty();
        assertThat(at("src/ ~/ ./ ../ /", 5)).isEmpty();
        assertThat(at("src/ ~/ ./ ../ /", 15)).isEmpty();
    }

    @Test
    void homeDotAndAbsolutePathsCount() {
        assertThat(path("~/a.txt", 2)).contains("~/a.txt");
        assertThat(path("./notes", 2)).contains("./notes");
        assertThat(path("../up/LICENSE", 2)).contains("../up/LICENSE");
        assertThat(path("/tmp/x.log", 2)).contains("/tmp/x.log");
        assertThat(path("/usr/bin/env", 2)).as("absolute, no extension").contains("/usr/bin/env");
        assertThat(path("~/.bashrc", 4)).contains("~/.bashrc");
    }

    @Test
    void aRelativePathNeedsADirectoryOrAnExtension() {
        assertThat(path("docs/notes", 2)).contains("docs/notes");
        assertThat(path("Foo.java", 2)).contains("Foo.java");
        assertThat(at("Foo.java:42", 2)).contains(new PathDetector.Match("Foo.java", 42, 0, 0, 11));
        assertThat(path("tmp/a(1).txt", 2)).as("a parenthesis that is no line is part of the name").contains("tmp/a(1).txt");
    }

    @Test
    void windowsPathsCountOnlyOnWindows() {
        assertThat(PathDetector.pathAt(row("C:\\src\\Foo.cs(42,5): error"), 3, true))
            .contains(new PathDetector.Match("C:\\src\\Foo.cs", 42, 5, 0, 19));
        assertThat(PathDetector.pathAt(row("C:\\src\\Foo.cs(42,5): error"), 3, false)).isEmpty();
        assertThat(PathDetector.pathAt(row("src\\Foo.cs:3"), 2, true)).contains(new PathDetector.Match("src\\Foo.cs", 3, 0, 0, 12));
        assertThat(PathDetector.pathAt(row("src\\Foo.cs:3"), 2, false)).isEmpty();
        assertThat(PathDetector.pathAt(row("C:/src/Foo.cs"), 5, true)).map(PathDetector.Match::path).contains("C:/src/Foo.cs");
        assertThat(PathDetector.pathAt(row("C:/src/Foo.cs"), 5, false)).isEmpty();
        assertThat(PathDetector.pathAt(row(".\\a.txt"), 3, true)).map(PathDetector.Match::path).contains(".\\a.txt");
        assertThat(PathDetector.pathAt(row("src/Foo.cs:3"), 2, true)).as("forward slashes count on Windows too")
            .contains(new PathDetector.Match("src/Foo.cs", 3, 0, 0, 12));
    }

    @Test
    void aDigitRunLongerThanNineIsNoLine() {
        assertThat(at("Foo.java:123456789", 2)).contains(new PathDetector.Match("Foo.java", 123456789, 0, 0, 18));
        assertThat(at("Foo.java:1234567890", 2)).contains(new PathDetector.Match("Foo.java", 0, 0, 0, 8));
        assertThat(at("Foo.java:1234567890", 12)).as("the digits are not part of it").isEmpty();
        assertThat(at("Foo.java:42:1234567890", 2)).contains(new PathDetector.Match("Foo.java", 42, 0, 0, 11));
        assertThat(at("Foo.cs(1234567890)", 2)).as("nor a line in parentheses").isEmpty();
    }

    @Test
    void aClickOnThePositionFindsThePath() {
        assertThat(path("src/Foo.java:42", 14)).contains("src/Foo.java");
        assertThat(path("src/Foo.java:42:5", 16)).contains("src/Foo.java");
        assertThat(path("Foo.cs(42,5)", 11)).contains("Foo.cs");
        assertThat(at("src/Foo.java:42 next", 16)).isEmpty();
    }

    @Test
    void wideCharactersMapColumnsThroughTheirCells() {
        // Cells: 0-1 wide, 2 space, 3 '.', 4 '/', 5-6 wide, 7-10 ".txt", 11 ':', 12 '7'.
        RowText wide = row("\u65e5\uE000 ./\u672c\uE000.txt:7 x");
        var expected = new PathDetector.Match("./\u672c.txt", 7, 0, 2, 11);
        assertThat(PathDetector.pathAt(wide, 3, false)).contains(expected);
        assertThat(PathDetector.pathAt(wide, 6, false)).as("the wide character's second cell").contains(expected);
        assertThat(PathDetector.pathAt(wide, 12, false)).as("the line after it").contains(expected);
        assertThat(PathDetector.pathAt(wide, 13, false)).isEmpty();
        assertThat(PathDetector.pathAt(wide, 1, false)).isEmpty();
    }

    @Test
    void columnsOutsideTheTextFindNothing() {
        assertThat(at("./a.txt", -1)).isEmpty();
        assertThat(at("./a.txt", 59)).isEmpty();
        assertThat(at("./a.txt", 60)).isEmpty();
    }

    private static Optional<PathDetector.Match> at(String text, int column) {
        return PathDetector.pathAt(row(text), column, false);
    }

    private static Optional<String> path(String text, int column) {
        return at(text, column).map(PathDetector.Match::path);
    }

    private static RowText row(String text) {
        return RowText.of(EmulationFixture.capture(new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text))), 60), 60);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.internal.text.PathDetectorTest'`
Expected: compilation fails: cannot find symbol `PathDetector`.

- [ ] **Step 3: Write `PathDetector`**

`terminal/internal/text/PathDetector.java`:

```java
package dev.jasper.terminal.internal.text;

import java.util.Optional;

/**
 * Finds a file path written as plain text under a click, as compilers and tools print one: {@code PATH},
 * {@code PATH:LINE}, {@code PATH:LINE:COLUMN}, {@code PATH(LINE)} or {@code PATH(LINE,COLUMN)}. A path is absolute,
 * starts with {@code ./}, {@code ../} or {@code ~/}, or is relative and has a separator or an extension; {@code C:\}
 * and backslash paths count only on Windows. It ends at whitespace, at {@code <>"'} and the backtick, and before
 * trailing punctuation, and it never contains {@code ://}, so a URL is no path. Pure text work: no buffer, lock or file
 * system.
 */
public final class PathDetector {
    /**
     * A path the click column falls in: its text, line and column (0 when none), and its index range in row.text().
     *
     * @param path the path as written
     * @param line the 1-based line written after it, or 0
     * @param column the 1-based column written after the line, or 0
     * @param start the index of the path's first character in the row's text
     * @param end the index after the path and its position, exclusive
     */
    public record Match(String path, int line, int column, int start, int end) { }

    private static final String ENDS = "<>\"'`";
    private static final String OPENERS = "([{";
    private static final String TRAILING = ".,;:!?)]}'\"";
    private static final int MAX_DIGITS = 9;

    private PathDetector() {
    }

    /**
     * The path whose text, or the position written after it, covers {@code column}.
     *
     * @param row the row, or soft-wrapped rows joined into one
     * @param column the clicked cell on {@code row}
     * @param windows whether {@code C:\} and backslash paths count
     * @return the path, or empty when the click is on anything else
     */
    public static Optional<Match> pathAt(RowText row, int column, boolean windows) {
        String text = row.text();
        int clicked = indexAt(row, column);
        if (clicked < 0 || ends(text.charAt(clicked))) return Optional.empty();
        int tokenStart = clicked;
        int tokenEnd = clicked + 1;
        while (tokenStart > 0 && !ends(text.charAt(tokenStart - 1))) tokenStart--;
        while (tokenEnd < text.length() && !ends(text.charAt(tokenEnd))) tokenEnd++;
        if (text.substring(tokenStart, tokenEnd).contains("://")) return Optional.empty();
        int start = tokenStart;
        while (start < tokenEnd && OPENERS.indexOf(text.charAt(start)) >= 0) start++;
        int pathEnd = windows && drive(text, start, tokenEnd) ? start + 2 : start;
        int[] position = null;
        while (pathEnd < tokenEnd && text.charAt(pathEnd) != ':'
            && (text.charAt(pathEnd) != '(' || (position = lineInParentheses(text, pathEnd, tokenEnd)) == null)) {
            pathEnd++;
        }
        if (position == null && pathEnd < tokenEnd) position = lineAfterColon(text, pathEnd, tokenEnd);
        int end;
        if (position == null) {
            while (pathEnd > start && TRAILING.indexOf(text.charAt(pathEnd - 1)) >= 0) pathEnd--;
            end = pathEnd;
        } else {
            end = position[2];
        }
        String path = text.substring(start, pathEnd);
        if (clicked < start || clicked >= end || !qualifies(path, windows)) return Optional.empty();
        return Optional.of(new Match(path, position == null ? 0 : position[0], position == null ? 0 : position[1], start, end));
    }

    /** The text index whose cells cover {@code column}, or -1. */
    private static int indexAt(RowText row, int column) {
        for (int i = 0; i < row.text().length(); i++) {
            if (column >= row.columns()[i] && column <= row.lastColumns()[i]) return i;
        }
        return -1;
    }

    private static boolean ends(char c) {
        return Character.isWhitespace(c) || ENDS.indexOf(c) >= 0;
    }

    /** Whether a drive such as {@code C:\} or {@code C:/} starts at {@code start}. */
    private static boolean drive(String text, int start, int tokenEnd) {
        if (start + 2 >= tokenEnd) return false;
        char letter = text.charAt(start);
        char separator = text.charAt(start + 2);
        return (letter >= 'A' && letter <= 'Z' || letter >= 'a' && letter <= 'z') && text.charAt(start + 1) == ':'
            && (separator == '\\' || separator == '/');
    }

    /**
     * {@code (LINE)} or {@code (LINE,COLUMN)} at {@code open}, followed only by trailing punctuation, as
     * {@code {line, column, end}}; null when it is not one, and the parenthesis is then part of the path.
     */
    private static int[] lineInParentheses(String text, int open, int tokenEnd) {
        int lineEnd = digits(text, open + 1, tokenEnd);
        if (lineEnd < 0) return null;
        int columnEnd = -1;
        if (lineEnd < tokenEnd && text.charAt(lineEnd) == ',') {
            columnEnd = digits(text, lineEnd + 1, tokenEnd);
            if (columnEnd < 0) return null;
        }
        int close = columnEnd < 0 ? lineEnd : columnEnd;
        if (close >= tokenEnd || text.charAt(close) != ')') return null;
        for (int i = close + 1; i < tokenEnd; i++) {
            if (TRAILING.indexOf(text.charAt(i)) < 0) return null;
        }
        return new int[] {number(text, open + 1, lineEnd), columnEnd < 0 ? 0 : number(text, lineEnd + 1, columnEnd), close + 1};
    }

    /**
     * {@code :LINE} or {@code :LINE:COLUMN} at {@code colon}, as {@code {line, column, end}}; null when no line follows.
     * A line or column ends at the token's end or at punctuation; what follows that, such as grep's matched text, is
     * not part of the match.
     */
    private static int[] lineAfterColon(String text, int colon, int tokenEnd) {
        int lineEnd = digits(text, colon + 1, tokenEnd);
        if (lineEnd < 0 || !endsNumber(text, lineEnd, tokenEnd)) return null;
        int line = number(text, colon + 1, lineEnd);
        if (lineEnd < tokenEnd && text.charAt(lineEnd) == ':') {
            int columnEnd = digits(text, lineEnd + 1, tokenEnd);
            if (columnEnd >= 0 && endsNumber(text, columnEnd, tokenEnd)) {
                return new int[] {line, number(text, lineEnd + 1, columnEnd), columnEnd};
            }
        }
        return new int[] {line, 0, lineEnd};
    }

    /** The end of a run of 1 to 9 digits at {@code from}, or -1: a longer run is no line, so a line always fits an int. */
    private static int digits(String text, int from, int tokenEnd) {
        int end = from;
        while (end < tokenEnd && text.charAt(end) >= '0' && text.charAt(end) <= '9') end++;
        return end == from || end - from > MAX_DIGITS ? -1 : end;
    }

    private static boolean endsNumber(String text, int at, int tokenEnd) {
        return at == tokenEnd || TRAILING.indexOf(text.charAt(at)) >= 0;
    }

    private static int number(String text, int from, int to) {
        return Integer.parseInt(text, from, to, 10);
    }

    /**
     * Whether {@code path} names a file: one with a separator (absolute, {@code ./}, {@code ../}, {@code ~/}, or a
     * relative path with a directory) or a name with an extension, and in both cases a name that is not {@code .} or
     * {@code ..}.
     */
    private static boolean qualifies(String path, boolean windows) {
        if (!windows && path.indexOf('\\') >= 0) return false;
        int separator = windows ? Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) : path.lastIndexOf('/');
        String name = path.substring(separator + 1);
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return false;
        return separator >= 0 || hasExtension(name);
    }

    /** {@code Foo.java} has one; {@code .bashrc}, {@code 1.2.3} and {@code Makefile} do not. */
    private static boolean hasExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return false;
        boolean letter = false;
        for (int i = dot + 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c)) return false;
            letter |= Character.isLetter(c);
        }
        return letter;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.internal.text.PathDetectorTest'`
Expected: PASS, 11 tests.

Then check the two files for raw private-use or control characters (the test writes U+E000 as an escape):

```bash
python3 - jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/PathDetectorTest.java jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/PathDetector.java <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/PathDetector.java \
  jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/PathDetectorTest.java
git commit -m "$(cat <<'EOF'
Terminal: find the file path under a click in a row's text

PathDetector reads PATH, PATH:LINE, PATH:LINE:COLUMN, PATH(LINE) and
PATH(LINE,COLUMN) as compilers print them, drops surrounding punctuation
and quotes, refuses URLs, versions and bare words, and admits C:\ and
backslash paths only on Windows. It maps the click through each
character's cells, so wide characters work. Pure text work, ready for
the soft-wrap-joined row.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `FileLink`, `fileLinkAt` and the view's file link handler

**Files:**
- Create: `terminal/internal/text/FileTarget.java`, `terminal/view/FileLink.java`, `terminal-test/view/FileLinkTest.java`
- Modify: `terminal/internal/text/LinkDetector.java`, `terminal/internal/shell/DirectoryProvenance.java`,
  `terminal/internal/emulation/{BufferQueries,JediCellReader}.java`, `terminal/internal/TerminalAccess.java`,
  `terminal/view/{TerminalView,MouseController}.java`, `build.gradle.kts`, `jasper-terminal/README.md`,
  `docs/terminal-architecture.md`
- Test: `terminal-test/internal/text/LinkDetectorTest.java`, `terminal-test/internal/shell/DirectoryProvenanceTest.java`,
  `terminal-test/internal/emulation/SessionInputTest.java`, `terminal-test/view/{TerminalViewInteractionTest,FileLinkTest}.java`

**Interfaces:**
- Consumes: `PathDetector.pathAt` (Task 1); `DirectoryProvenance.classify` (existing).
- Produces (supported): `record FileLink(String path, int line, int column)` in `dev.jasper.terminal.view`, rejecting
  a null or blank path and negative numbers; `TerminalView.setFileLinkHandler(Consumer<FileLink>)` (EDT; null removes
  it). `FileLink` joins the `supported` allowlist and the README table.
- Produces (internal): `record FileTarget(String path, int line, int column)` in `internal.text`;
  `TerminalAccess.fileLinkAt(long absoluteRow, int column, boolean windows)` → `Optional<FileTarget>`;
  `BufferQueries.fileLinkAt(long absoluteRow, int column, boolean windows, Function<String, Optional<Path>> localFiles)`
  → `Optional<FileTarget>`; `DirectoryProvenance.localPath(String uri)` → `Optional<Path>`;
  `LinkDetector.fileUrlAt(RowText, int)` → `Optional<String>`, with `file` gone from `urlAt`'s schemes;
  `MouseController(terminal, selection, viewport, boolean macOs, boolean windows, cellWidth, cellHeight, copyOnSelect,
  requestFocus, copySelection, Consumer<String> openLink, Supplier<Consumer<FileLink>> openFile, contextMenu, repaint)`.

How it fits:
- **No cycle.** `view` already depends on `internal` (`TerminalAccess`), so internal code returns the internal
  `FileTarget`, and `MouseController` builds the supported `FileLink` from it. This keeps the package graph acyclic,
  which `verifyTerminalArchitecture` checks, and no internal type names a view type.
- **The host rule** is the session's own. `TerminalAccess` keeps the `DirectoryProvenance` it already builds and passes
  `provenance::localPath`. A `file:` URI is local by the OSC 7 rule (a local process, and a host that is empty,
  `localhost` or one of the launch's `localHostNames`) and goes through the session's `localPathMapper`. An attached
  session's links are never local.
- **Precedence in `fileLinkAt`.**
  1. An OSC 8 link on the cell decides alone: a local `file:` target gives its path with line 0, and anything else
     gives nothing, whatever the cell's text says.
  2. Otherwise `wrappedRowLocked` joins the soft-wrapped rows under the lock, as URL detection does. After unlocking, a
     plain-text `file://` URL under the click gives its path when local and nothing otherwise.
  3. Then `PathDetector`.
- **The click.** `MouseController` asks `linkAt` first and opens a URL as today. Only when there is none and a handler
  is set does it ask `fileLinkAt`. A file link makes the press `OPEN_LINK`, like a URL, so its drag and release do
  nothing. Anything else starts a selection, as before.
- **A pre-existing bug, fixed here.** `JediCellReader` caches `CellAttributes` by `TextStyle`, and JediTerm 3.76's
  `HyperlinkStyle` does not override `equals`: every OSC 8 link in the same colours read back as the first one's
  target. That would open the first name of `ls --hyperlink` for every name. Hyperlink styles are now converted each
  time. `twoOsc8LinksInTheSameColoursKeepTheirOwnTargets` pins it, and the existing
  `osc8LinksWithOtherSchemesAreRefused` passed column 6 for the wrong reason: the cell read back as the `file:` link.

- [ ] **Step 1: Write the failing tests**

`terminal-test/view/FileLinkTest.java`:

```java
package dev.jasper.terminal.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FileLinkTest {
    @Test
    void aFileLinkNeedsAPathAndNoNegativeNumbers() {
        assertThat(new FileLink("~/a.txt", 0, 0)).isEqualTo(new FileLink("~/a.txt", 0, 0));
        assertThatNullPointerException().isThrownBy(() -> new FileLink(null, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLink(" ", 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLink("a.txt", -1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLink("a.txt", 1, -1));
    }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:80], t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')

def append_to_class(path, code):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')

tests = 'jasper-terminal/src/test/java/dev/jasper/terminal/'

# LinkDetectorTest: file:// is no browser URL any more; it has its own search.
edit(tests + 'internal/text/LinkDetectorTest.java',
    r'''    @Test
    void fileUrlsAreLinks() {
        assertThat(LinkDetector.urlAt(row("open file:///tmp/x.txt"), 6)).contains("file:///tmp/x.txt");
    }
''',
    r'''    @Test
    void fileUrlsAreFoundApartFromBrowserUrls() {
        assertThat(LinkDetector.urlAt(row("open file:///tmp/x.txt"), 6)).as("not for the browser").isEmpty();
        assertThat(LinkDetector.fileUrlAt(row("open file:///tmp/x.txt."), 6)).contains("file:///tmp/x.txt");
        assertThat(LinkDetector.fileUrlAt(row("see https://jasper.dev/docs"), 10)).isEmpty();
    }
''')

# DirectoryProvenanceTest: a clicked file: link follows the OSC 7 host rule.
append_to_class(tests + 'internal/shell/DirectoryProvenanceTest.java', r'''
    @Test void aFileLinkIsLocalByTheSameRuleAndGivesItsPath() {
        assertThat(local.localPath("file:///tmp/My%20Notes.txt")).contains(Path.of("/tmp/My Notes.txt"));
        assertThat(local.localPath("file://workstation/tmp/a.txt")).contains(Path.of("/tmp/a.txt"));
        assertThat(local.localPath("file://build-host/tmp/a.txt")).as("another host").isEmpty();
        assertThat(new DirectoryProvenance(false, Set.of()).localPath("file:///tmp/a.txt")).as("an attached session").isEmpty();
        assertThat(local.localPath("https://example.com/a.txt")).isEmpty();
    }
''')

# SessionInputTest: fileLinkAt over OSC 8, plain file:// URLs, paths, wraps and Windows forms.
session = tests + 'internal/emulation/SessionInputTest.java'
edit(session,
    r'''import dev.jasper.terminal.internal.text.MouseInput;
''',
    r'''import dev.jasper.terminal.internal.text.FileTarget;
import dev.jasper.terminal.internal.text.MouseInput;
''')
edit(session,
    r'''import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
''',
    r'''import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
''')
edit(session,
    r'''    private TerminalSession session;

    @BeforeEach
''',
    r'''    private TerminalSession session;
    private int feeds;

    @BeforeEach
''')
edit(session,
    r'''        assertThat(session.internalAccess().linkAt(0, 1)).isEmpty();
        assertThat(session.internalAccess().linkAt(0, 6)).isEmpty();
    }
''',
    r'''        assertThat(session.internalAccess().linkAt(0, 1)).as("a file link is not for the browser").isEmpty();
        assertThat(session.internalAccess().linkAt(0, 6)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 6, false)).as("nor a file").isEmpty();
    }
''')
append_to_class(session, r'''
    @Test
    void osc8FileLinksToThisMachineAreFileLinks() throws Exception {
        feedAll("\033]8;;file:///tmp/My%20Notes.txt\007notes\033]8;;\007 "
            + "\033]8;;file://localhost/tmp/b.txt\007b\033]8;;\007 \033]8;;file://far/tmp/c.txt\007c\033]8;;\007");

        assertThat(session.internalAccess().fileLinkAt(0, 1, false)).contains(new FileTarget(Path.of("/tmp/My Notes.txt").toString(), 0, 0));
        assertThat(session.internalAccess().fileLinkAt(0, 6, false)).contains(new FileTarget(Path.of("/tmp/b.txt").toString(), 0, 0));
        assertThat(session.internalAccess().fileLinkAt(0, 8, false)).as("another host").isEmpty();
        assertThat(session.internalAccess().linkAt(0, 1)).as("not for the browser").isEmpty();
    }

    @Test
    void anOsc8LinkOnTheCellComesBeforeThePathInItsText() throws Exception {
        feedAll("\033]8;;https://example.com\007./a.txt\033]8;;\007 \033]8;;x-custom://thing\007./b.txt\033]8;;\007");

        assertThat(session.internalAccess().linkAt(0, 2)).contains("https://example.com");
        assertThat(session.internalAccess().fileLinkAt(0, 2, false)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 10, false)).as("a refused link is not replaced by its text").isEmpty();
    }

    @Test
    void plainTextFileUrlsAreFileLinksForThisMachineOnly() throws Exception {
        feedAll("file:///tmp/x.txt.\r\nfile://far/y.txt");

        assertThat(session.internalAccess().linkAt(0, 3)).as("not for the browser").isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 3, false)).contains(new FileTarget(Path.of("/tmp/x.txt").toString(), 0, 0));
        assertThat(session.internalAccess().fileLinkAt(1, 3, false)).as("another host, and no path either").isEmpty();
    }

    @Test
    void aPathIsAFileLinkWithItsLineAndColumn() throws Exception {
        feedAll("src/A.java:42:5: e");

        assertThat(session.internalAccess().fileLinkAt(0, 2, false)).contains(new FileTarget("src/A.java", 42, 5));
        assertThat(session.internalAccess().fileLinkAt(0, 14, false)).as("a click on the column").contains(new FileTarget("src/A.java", 42, 5));
        assertThat(session.internalAccess().fileLinkAt(0, 15, false)).as("the trailing colon").isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 17, false)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(0, 20, false)).isEmpty();
        assertThat(session.internalAccess().fileLinkAt(99, 2, false)).isEmpty();
    }

    /** Review Focus 2: a path soft-wrapped across two rows is one path, from a click on either row. */
    @Test
    void aSoftWrappedPathIsOnePathFromEitherRow() throws Exception {
        feedAll("src/main/java/dev/jasper/Foo.java:3"); // 35 cells on 20 columns: "sper/Foo.java:3" wraps to row 1

        var expected = new FileTarget("src/main/java/dev/jasper/Foo.java", 3, 0);
        assertThat(session.internalAccess().fileLinkAt(0, 2, false)).contains(expected);
        assertThat(session.internalAccess().fileLinkAt(1, 5, false)).contains(expected);
        assertThat(session.internalAccess().fileLinkAt(1, 14, false)).as("the line on the second row").contains(expected);
    }

    @Test
    void aUrlIsNoPath() throws Exception {
        feedAll("https://x.dev/a.txt");

        assertThat(session.internalAccess().linkAt(0, 15)).contains("https://x.dev/a.txt");
        assertThat(session.internalAccess().fileLinkAt(0, 15, false)).isEmpty();
    }

    @Test
    void windowsPathsAreFileLinksOnlyOnWindows() throws Exception {
        feedAll("C:\\src\\A.cs(3,1): x");

        assertThat(session.internalAccess().fileLinkAt(0, 4, true)).contains(new FileTarget("C:\\src\\A.cs", 3, 1));
        assertThat(session.internalAccess().fileLinkAt(0, 4, false)).isEmpty();
    }

    @Test
    void twoOsc8LinksInTheSameColoursKeepTheirOwnTargets() throws Exception {
        feedAll("\033]8;;https://one.example\007one\033]8;;\007 \033]8;;https://two.example\007two\033]8;;\007");

        assertThat(session.internalAccess().linkAt(0, 1)).contains("https://one.example");
        assertThat(session.internalAccess().linkAt(0, 5)).contains("https://two.example");
    }

    /** Feeds {@code output} and waits for all of it, wrapped rows included: a title set after it arrives last. */
    private void feedAll(String output) throws Exception {
        String title = "fed " + ++feeds;
        connector.feed(output + "\033]0;" + title + "\007");
        Await.until(() -> session.title().equals(title), "the output before title " + title);
    }
''')

# TerminalViewInteractionTest: the handler gets paths; URLs and selection are unchanged.
interaction = tests + 'view/TerminalViewInteractionTest.java'
edit(interaction,
    r'''import static org.junit.jupiter.api.Assumptions.assumeTrue;
''',
    r'''import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
''')
edit(interaction,
    r'''    @Test
    void horizontalScrollingIsIgnoredOnMacOs() throws Exception {
''',
    r'''    @Test
    void theLinkModifierHandsAPathToTheFileLinkHandler() throws Exception {
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        show("at src/A.java:42:5:", 0, "at src/A.java:42:5:");

        view.handleMouse(press(5, 0, 1, LINK));
        view.handleMouse(release(5, 0));

        assertThat(clicked.get()).isEqualTo(new FileLink("src/A.java", 42, 5));
        assertThat(view.hasSelection()).isFalse();
    }

    /** Review Focus 5: a URL still opens the browser, and the file link handler hears nothing. */
    @Test
    void aUrlStillOpensTheBrowserWhenFileLinksAreHandled() throws Exception {
        AtomicReference<String> opened = new AtomicReference<>();
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setLinkOpener(opened::set);
        view.setFileLinkHandler(clicked::set);
        show("https://m.dev/a.txt", 0, "https://m.dev/a.txt");

        view.handleMouse(press(15, 0, 1, LINK));

        assertThat(opened.get()).isEqualTo("https://m.dev/a.txt");
        assertThat(clicked.get()).isNull();
    }

    /** Review Focus 5: a modified click on text that is no path still starts a selection. */
    @Test
    void aModifiedClickOnTextThatIsNoPathStillSelects() throws Exception {
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        show("hello world", 0, "hello world");

        selectByDragging(0, 0, 4, 0, LINK);

        assertThat(view.selectedText()).contains("hello");
        assertThat(clicked.get()).isNull();
    }

    /** Review Focus 5: with no file link handler, or once it is removed, a path click selects as before. */
    @Test
    void withoutAFileLinkHandlerAPathClickSelects() throws Exception {
        show("./notes.md x", 0, "./notes.md x");
        selectByDragging(0, 0, 9, 0, LINK);
        assertThat(view.selectedText()).contains("./notes.md");

        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        view.setFileLinkHandler(null);
        selectByDragging(2, 0, 9, 0, LINK);
        assertThat(view.selectedText()).contains("notes.md");
        assertThat(clicked.get()).isNull();
    }

    @Test
    void commandClickOpensAPathEvenWhenTheProgramWantsTheMouse() throws Exception {
        assumeTrue(MAC);
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        enableSgrMouse();
        show("./notes.md:3", 0, "./notes.md:3");

        view.handleMouse(press(4, 0, 1, InputEvent.META_DOWN_MASK));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, InputEvent.META_DOWN_MASK,
            x(4), y(0), 1, false, MouseEvent.BUTTON1));

        assertThat(clicked.get()).isEqualTo(new FileLink("./notes.md", 3, 0));
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void ctrlClickGoesToAProgramThatWantsTheMouseAndCtrlShiftClickOpensThePath() throws Exception {
        assumeFalse(MAC);
        AtomicReference<FileLink> clicked = new AtomicReference<>();
        view.setFileLinkHandler(clicked::set);
        enableSgrMouse();
        show("./notes.md:3", 0, "./notes.md:3");

        view.handleMouse(press(4, 0, 1, InputEvent.CTRL_DOWN_MASK));
        view.handleMouse(release(4, 0));
        assertThat(clicked.get()).as("the program has the click").isNull();
        assertThat(connector.written()).isNotEmpty();

        view.handleMouse(press(4, 0, 1, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(clicked.get()).isEqualTo(new FileLink("./notes.md", 3, 0));
    }

    @Test
    void horizontalScrollingIsIgnoredOnMacOs() throws Exception {
''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-terminal:compileTestJava`
Expected: compilation fails: cannot find symbol `FileTarget`, `FileLink`, `fileLinkAt`, `fileUrlAt`, `localPath` and
`setFileLinkHandler`.

- [ ] **Step 3: Write the values, the detection and the click**

`terminal/internal/text/FileTarget.java`:

```java
package dev.jasper.terminal.internal.text;

/**
 * The file a click names, before the view reports it as a supported {@code FileLink}: a path as written, or the decoded
 * path of a {@code file} URI on this machine.
 *
 * @param path the path
 * @param line the 1-based line written after the path, or 0
 * @param column the 1-based column written after the line, or 0
 */
public record FileTarget(String path, int line, int column) { }
```

`terminal/view/FileLink.java`:

```java
package dev.jasper.terminal.view;

import java.util.Objects;

/**
 * A file that a Cmd-click (Ctrl-click on Linux and Windows) named in terminal output, reported to
 * {@link TerminalView#setFileLinkHandler}. The view resolves nothing: what the path means, and whether the file exists
 * and opens, is the application's decision.
 *
 * @param path the path as written ({@code ~/}, {@code ./} and relative paths unresolved), or the decoded absolute path
 *             of a {@code file:} link to this machine
 * @param line the 1-based line written after the path, or 0 when there is none
 * @param column the 1-based column written after the line, or 0 when there is none
 */
public record FileLink(String path, int line, int column) {
    /**
     * Validates the components.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws IllegalArgumentException when {@code path} is blank, or {@code line} or {@code column} is negative
     */
    public FileLink {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) throw new IllegalArgumentException("A file link needs a path");
        if (line < 0 || column < 0) throw new IllegalArgumentException("Line and column are 1-based, or 0 for none");
    }
}
```

Replace `terminal/internal/text/LinkDetector.java` with:

```java
package dev.jasper.terminal.internal.text;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a URL written as plain text on a row: one for the browser, or a {@code file://} URL for the file rules. */
public final class LinkDetector {
    private static final Pattern URL = Pattern.compile("(?:https?|ftp)://[^\\s<>\"'`]+");
    private static final Pattern FILE_URL = Pattern.compile("file://[^\\s<>\"'`]+");
    private static final String TRAILING = ".,;:!?)]}'\"";

    private LinkDetector() {
    }

    /** The {@code http}, {@code https} or {@code ftp} URL covering {@code column}, without trailing punctuation. */
    public static Optional<String> urlAt(RowText row, int column) {
        return at(URL, row, column);
    }

    /** The {@code file://} URL covering {@code column}; whether its host is this machine is the caller's decision. */
    public static Optional<String> fileUrlAt(RowText row, int column) {
        return at(FILE_URL, row, column);
    }

    private static Optional<String> at(Pattern pattern, RowText row, int column) {
        String text = row.text();
        Matcher matcher = pattern.matcher(text);
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

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:80], t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')

terminal = 'jasper-terminal/src/main/java/dev/jasper/terminal/'

# DirectoryProvenance: the same host rule for a file a click names.
edit(terminal + 'internal/shell/DirectoryProvenance.java',
    r'''    private Optional<Report> local(String path) {''',
    r'''    /**
     * The path a {@code file} URI names when it is on this machine by the same rule, through the local path mapper;
     * empty for a remote one and for anything that is not a {@code file} URI with a path.
     */
    public Optional<Path> localPath(String uri) {
        return classify(uri).flatMap(report -> report instanceof Report.Local local ? Optional.of(local.directory()) : Optional.empty());
    }

    private Optional<Report> local(String path) {''')

# BufferQueries: fileLinkAt captures under the lock and matches after it.
queries = terminal + 'internal/emulation/BufferQueries.java'
edit(queries,
    r'''import dev.jasper.terminal.internal.text.CursorRequest;
import dev.jasper.terminal.internal.text.LinkDetector;
''',
    r'''import dev.jasper.terminal.internal.text.CursorRequest;
import dev.jasper.terminal.internal.text.FileTarget;
import dev.jasper.terminal.internal.text.LinkDetector;
''')
edit(queries,
    r'''import dev.jasper.terminal.internal.text.MouseGeometry;
import dev.jasper.terminal.internal.text.RowText;
''',
    r'''import dev.jasper.terminal.internal.text.MouseGeometry;
import dev.jasper.terminal.internal.text.PathDetector;
import dev.jasper.terminal.internal.text.RowText;
''')
edit(queries,
    r'''import java.util.ArrayList;
import java.util.List;
''',
    r'''import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
''')
edit(queries,
    r'''import java.util.Set;
import java.util.regex.Pattern;
''',
    r'''import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
''')
edit(queries,
    r'''    private Optional<String> urlAcrossWrappedRows(long absoluteRow, int column) {
        int width = buffer.getWidth();
        LogicalLine range = LogicalLine.around(absoluteRow, width, this::lineAtLocked);
        if (range.truncated()) return Optional.empty();
''',
    r'''    private Optional<String> urlAcrossWrappedRows(long absoluteRow, int column) {
        return wrappedRowLocked(absoluteRow, column).flatMap(wrapped -> LinkDetector.urlAt(wrapped.text(), wrapped.column()));
    }

    /**
     * The file a click at an absolute row and column names. An OSC 8 link on the cell counts when {@code localFiles}
     * maps it to this machine; a cell with any other OSC 8 target names nothing, whatever its text says. Otherwise the
     * soft-wrapped rows around the row are joined under the lock, as for URLs, and read after it: a plain-text
     * {@code file://} URL under the column counts the same way, and anything else is left to {@link PathDetector}.
     *
     * @param absoluteRow the clicked row
     * @param column the clicked column
     * @param windows whether {@code C:\} and backslash paths count
     * @param localFiles the path a {@code file} URI names on this machine; empty for a remote one or any other URI
     * @return the file, or empty when the click names none
     */
    public Optional<FileTarget> fileLinkAt(long absoluteRow, int column, boolean windows,
                                           Function<String, Optional<Path>> localFiles) {
        String link;
        Optional<WrappedRow> wrapped;
        buffer.lock();
        try {
            if (column < 0 || column >= buffer.getWidth()) return Optional.empty();
            TerminalRow line = lineAtLocked(absoluteRow);
            if (line == null) return Optional.empty();
            link = column < line.length() ? line.attributesAt(column).link() : null;
            wrapped = link == null ? wrappedRowLocked(absoluteRow, column) : Optional.empty();
        } finally {
            buffer.unlock();
        }
        // Match outside the lock, as search does, so a long logical line cannot stall the reader thread.
        if (link != null) return localFile(link, localFiles);
        if (wrapped.isEmpty()) return Optional.empty();
        RowText text = wrapped.get().text();
        int at = wrapped.get().column();
        Optional<String> url = LinkDetector.fileUrlAt(text, at);
        if (url.isPresent()) return localFile(url.get(), localFiles);
        return PathDetector.pathAt(text, at, windows).map(match -> new FileTarget(match.path(), match.line(), match.column()));
    }

    private static Optional<FileTarget> localFile(String uri, Function<String, Optional<Path>> localFiles) {
        return localFiles.apply(uri).map(path -> new FileTarget(path.toString(), 0, 0));
    }

    /** Soft-wrapped rows joined into one, and the clicked column on it. */
    private record WrappedRow(RowText text, int column) { }

    /**
     * The wrapped screen rows around {@code absoluteRow} joined into one row, within {@link LogicalLine}'s bounds, and
     * {@code column} on it; empty when the line was truncated. Call with the buffer lock held.
     */
    private Optional<WrappedRow> wrappedRowLocked(long absoluteRow, int column) {
        int width = buffer.getWidth();
        LogicalLine range = LogicalLine.around(absoluteRow, width, this::lineAtLocked);
        if (range.truncated()) return Optional.empty();
''')
edit(queries,
    r'''        int virtualColumn = (int) ((absoluteRow - range.firstRow()) * width) + column;
        RowText combined = new RowText(text.toString(), columns, lastColumns);
        return LinkDetector.urlAt(combined, virtualColumn);
    }
''',
    r'''        int virtualColumn = (int) ((absoluteRow - range.firstRow()) * width) + column;
        return Optional.of(new WrappedRow(new RowText(text.toString(), columns, lastColumns), virtualColumn));
    }
''')

# TerminalAccess: the session's own host rule decides whether a file: link is local.
access = terminal + 'internal/TerminalAccess.java'
edit(access,
    r'''import dev.jasper.terminal.internal.text.MouseGeometry;
''',
    r'''import dev.jasper.terminal.internal.text.FileTarget;
import dev.jasper.terminal.internal.text.MouseGeometry;
''')
edit(access,
    r'''    private final ShellCommandTracker shell;
''',
    r'''    private final ShellCommandTracker shell;
    private final DirectoryProvenance provenance;
''')
edit(access,
    r'''        this.engine = engine;
        queries = engine.queries();
''',
    r'''        this.engine = engine;
        this.provenance = provenance;
        queries = engine.queries();
''')
edit(access,
    r'''    public Optional<String> linkAt(long absoluteRow, int column) { return queries.linkAt(absoluteRow, column); }
''',
    r'''    public Optional<String> linkAt(long absoluteRow, int column) { return queries.linkAt(absoluteRow, column); }
    /**
     * The file a click at an absolute row and column names: an OSC 8 or plain-text {@code file} link that this session's
     * directory rule places on this machine, else a path written in the soft-wrapped line; {@code windows} admits
     * {@code C:\} and backslash paths. Unsupported module collaboration API.
     */
    public Optional<FileTarget> fileLinkAt(long absoluteRow, int column, boolean windows) {
        return queries.fileLinkAt(absoluteRow, column, windows, provenance::localPath);
    }
''')

# MouseController: a URL first, then a file link when the application handles them, then a selection.
mouse = terminal + 'view/MouseController.java'
edit(mouse,
    r'''import java.util.function.Consumer;
import javax.swing.SwingUtilities;
''',
    r'''import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
''')
edit(mouse,
    r'''    private final boolean macOs;
''',
    r'''    private final boolean macOs, windows;
''')
edit(mouse,
    r'''    private final Consumer<String> openLink;
    private final Consumer<MouseEvent> contextMenu;
    MouseController(TerminalAccess terminal, SelectionController selection, Viewport viewport, boolean macOs,
                    IntSupplier cellWidth, IntSupplier cellHeight, BooleanSupplier copyOnSelect,
                    Runnable requestFocus, Runnable copySelection, Consumer<String> openLink,
                    Consumer<MouseEvent> contextMenu, Runnable repaint) {
        this.terminal = terminal; this.selection = selection; this.viewport = viewport; this.macOs = macOs;
        this.cellWidth = cellWidth; this.cellHeight = cellHeight; this.copyOnSelect = copyOnSelect;
        this.requestFocus = requestFocus; this.copySelection = copySelection; this.openLink = openLink;
        this.contextMenu = contextMenu; this.repaint = repaint;
    }
''',
    r'''    private final Consumer<String> openLink;
    /** The application's file link handler, or null while it has none. */
    private final Supplier<Consumer<FileLink>> openFile;
    private final Consumer<MouseEvent> contextMenu;
    MouseController(TerminalAccess terminal, SelectionController selection, Viewport viewport, boolean macOs, boolean windows,
                    IntSupplier cellWidth, IntSupplier cellHeight, BooleanSupplier copyOnSelect,
                    Runnable requestFocus, Runnable copySelection, Consumer<String> openLink,
                    Supplier<Consumer<FileLink>> openFile, Consumer<MouseEvent> contextMenu, Runnable repaint) {
        this.terminal = terminal; this.selection = selection; this.viewport = viewport; this.macOs = macOs; this.windows = windows;
        this.cellWidth = cellWidth; this.cellHeight = cellHeight; this.copyOnSelect = copyOnSelect;
        this.requestFocus = requestFocus; this.copySelection = copySelection; this.openLink = openLink; this.openFile = openFile;
        this.contextMenu = contextMenu; this.repaint = repaint;
    }
''')
edit(mouse,
    r'''                Optional<String> link = terminal.linkAt(absoluteRow, column);
                if (link.isPresent()) {
                    openLink.accept(link.get());
                    action = MouseRouting.Action.OPEN_LINK;
                } else {
''',
    r'''                Optional<String> link = terminal.linkAt(absoluteRow, column);
                Consumer<FileLink> files = openFile.get();
                Optional<FileLink> file = link.isPresent() || files == null ? Optional.empty()
                    : terminal.fileLinkAt(absoluteRow, column, windows)
                        .map(target -> new FileLink(target.path(), target.line(), target.column()));
                if (link.isPresent()) {
                    openLink.accept(link.get());
                    action = MouseRouting.Action.OPEN_LINK;
                } else if (file.isPresent()) {
                    files.accept(file.get());
                    action = MouseRouting.Action.OPEN_LINK;
                } else {
''')

# TerminalView: the supported handler, and the platform's path rules.
view = terminal + 'view/TerminalView.java'
edit(view,
    r'''    private Consumer<String> linkOpener = DesktopServices::openBrowser;
''',
    r'''    private Consumer<String> linkOpener = DesktopServices::openBrowser;
    private Consumer<FileLink> fileLinkHandler;
''')
edit(view,
    r'''        this.mouseInput = new MouseController(access, selection, viewport, macOs,
            () -> fonts.cellWidth(), () -> fonts.cellHeight(), () -> this.options.copyOnSelect(),
            this::requestFocusInWindow, this::copySelection, link -> linkOpener.accept(link),
            event -> contextMenuHandler.accept(event), this::repaint);
''',
    r'''        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        this.mouseInput = new MouseController(access, selection, viewport, macOs, windows,
            () -> fonts.cellWidth(), () -> fonts.cellHeight(), () -> this.options.copyOnSelect(),
            this::requestFocusInWindow, this::copySelection, link -> linkOpener.accept(link),
            () -> fileLinkHandler, event -> contextMenuHandler.accept(event), this::repaint);
''')
edit(view,
    r'''    public void setFindResultListener(Consumer<FindResult> listener) {
        findResultListener = listener == null ? result -> { } : listener;
    }
''',
    r'''    public void setFindResultListener(Consumer<FindResult> listener) {
        findResultListener = listener == null ? result -> { } : listener;
    }

    /**
     * Receives the file a Cmd-click (macOS) or Ctrl-click (Linux and Windows) names: a path written in the output, such
     * as {@code src/Foo.java:42} or {@code Foo.cs(42,5)}, following soft wraps, or a {@code file:} link, OSC 8 or plain
     * text, to this machine. A URL under the pointer still opens in the browser. Without a handler, the default, such a
     * click starts a selection. Called on the Event Dispatch Thread, so the handler must not block.
     *
     * @param handler the receiver, or {@code null} for none
     */
    public void setFileLinkHandler(Consumer<FileLink> handler) {
        fileLinkHandler = handler;
    }
''')

# JediCellReader: HyperlinkStyle equality ignores the link, so the style cache gave every OSC 8 link with the same
# colours the first one's target. Links are converted each time instead.
edit(terminal + 'internal/emulation/JediCellReader.java',
    r'''    CellAttributes attributes(TextStyle style) {
        if (styles.size() > 4096) styles.clear();
''',
    r'''    CellAttributes attributes(TextStyle style) {
        // HyperlinkStyle equality ignores the link: a cached one would give every link in the same colours the first's target.
        if (style instanceof HyperlinkStyle) return convert(style);
        if (styles.size() > 4096) styles.clear();
''')
PY
```

- [ ] **Step 4: The allowlist, the README table and the architecture paragraphs**

```bash
python3 - <<'PY'
import pathlib

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:80], t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')

# The supported allowlist (verifyTerminalArchitecture) and its table.
edit('build.gradle.kts',
    '''            "view.TerminalView", "view.TerminalAction", "config.TerminalOptions", "config.Palette",''',
    '''            "view.TerminalView", "view.TerminalAction", "view.FileLink", "config.TerminalOptions", "config.Palette",''')
edit('jasper-terminal/README.md',
    '''[TerminalAction](src/main/java/dev/jasper/terminal/view/TerminalAction.java) | Swing component and reusable operations |''',
    '''[TerminalAction](src/main/java/dev/jasper/terminal/view/TerminalAction.java), [FileLink](src/main/java/dev/jasper/terminal/view/FileLink.java) | Swing component, reusable operations and the file a Cmd-click names |''')

# The mouse and OSC 8 paragraphs.
edit('docs/terminal-architecture.md',
    '''Shift bypasses application mouse capture. Fractional wheel motion accumulates.
''',
    '''Shift bypasses application mouse capture. Fractional wheel motion accumulates.
A Cmd-click (Ctrl-click elsewhere) opens the URL under the pointer in the browser;
otherwise, once the application set `setFileLinkHandler`, it reports the path or
local `file:` link there as a `FileLink`; otherwise it starts a selection.
`internal.text.PathDetector` reads the soft-wrap-joined row, which `BufferQueries`
captures under the buffer lock and matches after releasing it.
''')
edit('docs/terminal-architecture.md',
    '''OSC 7 and 133 are rewritten because the pinned emulator swallows them. OSC 8
links use the installed hyperlink filter. CSI `q` lacking a space intermediate
''',
    '''OSC 7 and 133 are rewritten because the pinned emulator swallows them. OSC 8
links use the installed hyperlink filter: `http`, `https`, `ftp` and `mailto`
targets open in the browser, a `file:` target that the OSC 7 host rule places on
this machine is a file link, and other targets open nothing. CSI `q` lacking a space intermediate
''')
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.internal.text.*' --tests 'dev.jasper.terminal.internal.shell.DirectoryProvenanceTest' --tests 'dev.jasper.terminal.internal.emulation.*' --tests 'dev.jasper.terminal.view.*'`
Expected: PASS. On macOS `ctrlClickGoesToAProgramThatWantsTheMouseAndCtrlShiftClickOpensThePath` is skipped, and on
Linux and Windows the three `assumeTrue(MAC)` ones are.

Optionally, run the Linux and Windows routing on a Mac by giving the test JVM another `os.name` through an init
script in a scratch folder (not in the repository):

```bash
INIT="${TMPDIR:-/tmp}/jasper-init" && mkdir -p "$INIT" && cat > "$INIT/linux.gradle" <<'EOF'
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'os.name', 'Linux'
        outputs.upToDateWhen { false }
    }
}
EOF
./gradlew -I "$INIT/linux.gradle" :jasper-terminal:test --tests 'dev.jasper.terminal.view.TerminalViewInteractionTest'
```

Expected: PASS, with the Ctrl-click test run and the macOS ones skipped. The next run without the init script runs the
tests again with the real `os.name`.

Then run: `./gradlew :jasper-terminal:check verifyTerminalArchitecture`
Expected: BUILD SUCCESSFUL. JavaDoc doclint passes on `FileLink` and `setFileLinkHandler`, no package cycle appears,
and the app still names only supported types; `FileLink` is on the allowlist before the app uses it in Task 3.

- [ ] **Step 6: Commit**

```bash
git add jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/FileTarget.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/view/FileLink.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/LinkDetector.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/DirectoryProvenance.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/internal/emulation/BufferQueries.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/internal/emulation/JediCellReader.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/internal/TerminalAccess.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java \
  jasper-terminal/src/main/java/dev/jasper/terminal/view/MouseController.java \
  jasper-terminal/src/test/java/dev/jasper/terminal/view/FileLinkTest.java \
  jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/LinkDetectorTest.java \
  jasper-terminal/src/test/java/dev/jasper/terminal/internal/shell/DirectoryProvenanceTest.java \
  jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/SessionInputTest.java \
  jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java \
  build.gradle.kts \
  jasper-terminal/README.md \
  docs/terminal-architecture.md
git commit -m "$(cat <<'EOF'
Terminal: a Cmd-click on a path or a local file link reports a FileLink

TerminalView.setFileLinkHandler receives the supported FileLink(path,
line, column) for a path in the soft-wrapped line or a file: link, OSC 8
or plain text, that the session's OSC 7 host rule places on this machine.
A URL still opens the browser first, another OSC 8 target opens nothing,
and without a handler a path click selects as before. Rows are joined
under the buffer lock and matched after it. Plain-text file:// URLs no
longer go to the browser.

JediCellReader no longer caches hyperlink styles: HyperlinkStyle equality
ignores the link, so every OSC 8 link in the same colours read back as
the first one's target.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: The app opens the clicked file

**Files:**
- Create: `app/workspace/FileLinks.java`, `app-test/workspace/{FileLinksTest,FileLinkClickTest}.java`
- Modify: `app/workspace/{WindowContent,TerminalPane}.java`, `app/plugins/{PluginRuntime,FileEditors}.java`,
  `app/application/{JasperApplication,Rehearsal}.java`, `docs/design/theme-engine/migration.csv` (one row:
  `FileLinkClickTest` imports `java.awt`, so `ThemeMigrationCoverageTest` matches it; `FileLinks` and `FileLinksTest` do
  not match)
- Test: `app-test/workspace/{FileLinksTest,FileLinkClickTest}.java`, `app-test/plugins/{FileEditorsTest,PluginRuntimeTest}.java`,
  `app-test/application/{JasperApplicationPluginsTest,RehearsalTest}.java`

**Interfaces:**
- Consumes: `FileLink`, `TerminalView.setFileLinkHandler` (Task 2); `FileEditors.editFromWorker` (plan 2);
  `PaneSnapshot.providerId()`/`remoteDirectory()` (the remote predicate of `WindowHeaderContext`).
- Produces:
  - `FileLinks.resolve(String path, Optional<Path> directory, Path home, boolean windows)` → `Optional<Path>`
    (`workspace`, pure).
  - Package-private `TerminalPane.localDirectory()` → `Optional<Path>`: the reported local directory, else the launch
    directory (deviation 1); empty for a provided session or a remote report.
  - `public BiConsumer<Path, Integer> WindowContent.onOpenFile`, a no-op by default, reset by `close()` and silenced
    by `Rehearsal.silence`.
  - `PluginRuntime.open(Path, OptionalInt, Optional<UUID>, Consumer<Path>)`. The existing `open(Path, Consumer<Path>)`
    delegates to the same private worker method with the active window.
  - `boolean FileEditors.openFromWorker(Path, OptionalInt, Supplier<Optional<UUID>>, Consumer<Path>)`. It is true when
    the provided editor opened the file. The three-argument form delegates with no line.
  - Package-private `JasperApplication.openFileAt(Path file, int line, UUID window)`. It is package-private for its
    test: a headless `newWindow()` throws `HeadlessException`, so `composeWindow`'s wiring line cannot run in a test.

The flow:
1. The click arrives on the EDT. `WindowContent.configurePane` installs the handler on each new view, including
   a provided pane's view after a reconnect. The handler asks `pane.localDirectory()`; empty means nothing at all
   happens, absolute paths and `file:` links included.
2. Otherwise it resolves the path with `FileLinks` against that directory and `user.home`, and calls
   `onOpenFile.accept(file, line)`.
3. `JasperApplication` gives each window `(file, line) -> openFileAt(file, line, windowId)`. That starts a virtual
   thread that checks `Files.isRegularFile`. When the check passes it calls `plugins.open(file, line > 0 ?
   OptionalInt.of(line) : OptionalInt.empty(), Optional.of(windowId), desktop::open)`, or `ConfigEditor.open` before
   the plugins start.
4. A failure is logged and shown to nobody. The column stops here (deviation 2).

- [ ] **Step 1: Write the failing tests**

`app-test/workspace/FileLinksTest.java`:

```java
package dev.jasper.app.workspace;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.assertThat;

class FileLinksTest {
    private static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");
    private static final Path HERE = HOME.resolve("project");

    @Test void aRelativePathIsUnderThePanesDirectoryAndNeedsOne() {
        assertThat(FileLinks.resolve("src/Foo.java", Optional.of(HERE), HOME, false)).contains(HERE.resolve("src").resolve("Foo.java"));
        assertThat(FileLinks.resolve("./notes.md", Optional.of(HERE), HOME, false)).contains(HERE.resolve("notes.md"));
        assertThat(FileLinks.resolve("../up/a.txt", Optional.of(HERE), HOME, false)).contains(HOME.resolve("up").resolve("a.txt"));
        assertThat(FileLinks.resolve("src/Foo.java", Optional.empty(), HOME, false)).as("no directory to resolve against").isEmpty();
    }

    @Test void homeAndAbsolutePathsNeedNoDirectory() {
        assertThat(FileLinks.resolve("~/a.txt", Optional.empty(), HOME, false)).contains(HOME.resolve("a.txt"));
        assertThat(FileLinks.resolve("~/x/../b.txt", Optional.of(HERE), HOME, false)).as("normalized").contains(HOME.resolve("b.txt"));
        Path absolute = HOME.resolve("logs").resolve("x.log");
        assertThat(FileLinks.resolve(absolute.toString(), Optional.empty(), HOME, false)).contains(absolute);
        assertThat(FileLinks.resolve(absolute + File.separator + ".." + File.separator + "y.log", Optional.of(HERE), HOME, false))
            .contains(HOME.resolve("logs").resolve("y.log"));
    }

    @Test void aPathThisFileSystemCannotHoldNamesNothing() {
        assertThat(FileLinks.resolve("a\0b.txt", Optional.of(HERE), HOME, false)).isEmpty();
    }

    @Test void onWindowsATildeAndABackslashMeanHomeToo() {
        assertThat(FileLinks.resolve("~\\a.txt", Optional.empty(), HOME, true)).contains(HOME.resolve("a.txt"));
        assertThat(FileLinks.resolve("~\\a.txt", Optional.empty(), HOME, false)).as("elsewhere it is a relative name").isEmpty();
    }

    @Test @EnabledOnOs(OS.WINDOWS) void driveAndBackslashPathsResolveOnWindows() {
        assertThat(FileLinks.resolve("C:\\src\\Foo.cs", Optional.empty(), HOME, true)).contains(Path.of("C:\\src\\Foo.cs"));
        assertThat(FileLinks.resolve("src\\Foo.cs", Optional.of(Path.of("D:\\work")), HOME, true)).contains(Path.of("D:\\work\\src\\Foo.cs"));
        assertThat(FileLinks.resolve("C:/src/Foo.cs", Optional.empty(), HOME, true)).contains(Path.of("C:\\src\\Foo.cs"));
    }
}
```

`app-test/workspace/FileLinkClickTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

/** A Cmd-click (Ctrl-click elsewhere) on a file in a pane, through the real view, to the window's {@code onOpenFile}. */
class FileLinkClickTest {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    private static final int LINK = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    @TempDir Path directory;
    private final PipedOutputStream remote = new PipedOutputStream();
    private final List<String> opened = Collections.synchronizedList(new ArrayList<>());
    private final List<WindowContent> windows = new ArrayList<>();
    private int writes;

    @AfterEach void close() throws Exception {
        edt(() -> windows.forEach(WindowContent::close));
        remote.close();
    }

    /** Review Focus 1, the window's half: compiler output in a local pane names the file under the pane's directory, at its line. */
    @Test void compilerOutputInALocalPaneOpensTheFileAtItsLine() throws Exception {
        TerminalPane pane = localPane(attached());
        Path log = directory.resolve("logs").resolve("x.log");
        write(pane, "src/Foo.java:42:5: error: cannot find symbol\r\nFoo.cs(7,3): error CS1002: ; expected\r\n"
            + "see ~/notes.txt.\r\n" + log + ":12\r\n");

        click(pane, 2, 0);
        click(pane, 2, 1);
        click(pane, 6, 2);
        click(pane, 2, 3);
        click(pane, 22, 0);

        assertThat(opened).as("a pane that reported no directory resolves against the one it started in").containsExactly(
            directory.resolve("src").resolve("Foo.java") + ":42", directory.resolve("Foo.cs") + ":7",
            Path.of(System.getProperty("user.home")).resolve("notes.txt") + ":0", log + ":12");
    }

    /** Review Focus 3: once the shell reports a remote directory, nothing opens, not even a same-named local file. */
    @Test void aPaneWhoseShellReportsARemoteDirectoryOpensNothing() throws Exception {
        Path file = Files.createDirectories(directory.resolve("src")).resolve("Foo.java");
        Files.writeString(file, "class Foo { }\n");
        TerminalPane pane = localPane(attached());
        write(pane, "src/Foo.java:3\r\n" + file + "\r\n");
        click(pane, 2, 0);
        assertThat(opened).as("before any report").containsExactly(file + ":3");

        write(pane, "\033]7;file://build-host/srv/app\007");
        until(() -> pane.snapshot().remoteDirectory().isPresent());
        click(pane, 2, 0);
        click(pane, 2, 1);

        assertThat(opened).as("neither the relative path nor the absolute one").containsExactly(file + ":3");
    }

    /** Review Focus 3: a provided session, such as an ssh plugin's, opens nothing. */
    @Test @DisabledOnOs(OS.WINDOWS) void aProvidedSessionOpensNothing() throws Exception {
        List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = window(launcher(new ArrayDeque<>()))
            .openTab(directory, new SessionRequest("dev.example.ssh", "build-host", false, attempts::add, Runnable::run)).focusedPane());
        until(() -> !attempts.isEmpty());
        attempts.getFirst().attach(connection());
        until(() -> pane[0].view() != null);
        write(pane[0], directory.resolve("notes.txt") + "\r\n./notes.txt\r\n");

        click(pane[0], 2, 0);
        click(pane[0], 2, 1);

        assertThat(opened).isEmpty();
    }

    /** The directory a local shell reported comes before the one the pane started in, and its file: links open too. */
    @Test @DisabledOnOs(OS.WINDOWS) void aLocalShellsReportedDirectoryResolvesItsPaths() throws Exception {
        Path reported = Files.createDirectories(directory.resolve("reported"));
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c",
                    "printf '\\033]7;file://localhost%s\\007src/Foo.java:9\\r\\n\\033]8;;file://localhost%s/b.txt\\007link\\033]8;;\\007"
                        + "\\r\\n\\033]0;ready\\007' \"$1\" \"$1\"; read answer", "sh", reported.toString()))
                    .environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = window(launcher));
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null && owner[0].currentPane().session().title().equals("ready")
            && owner[0].currentPane().session().workingDirectory().isPresent());
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = owner[0].currentPane());

        click(pane[0], 2, 0);
        click(pane[0], 1, 1);

        assertThat(opened).containsExactly(reported.resolve("src").resolve("Foo.java") + ":9", reported.resolve("b.txt") + ":0");
    }

    private WindowContent window(ShellLauncher launcher) {
        WindowContent owner = new WindowContent(launcher, directory, path -> { }, () -> { }, () -> { });
        owner.onOpenFile = (file, line) -> opened.add(file + ":" + line);
        windows.add(owner);
        return owner;
    }

    /** A local pane (no provider) over an attached session this test writes to: it never reports a local directory. */
    private TerminalPane localPane(TerminalSession session) throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = window(new ShellLauncher(pending::add, path -> session, "sh")));
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = owner[0].currentPane());
        return pane[0];
    }

    private TerminalSession attached() throws IOException {
        return TerminalSession.attach(connection(), new GridSize(200, 24), 100);
    }

    private AttachedConnection connection() throws IOException {
        var output = new PipedInputStream(remote, 1 << 16);
        return new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, new CompletableFuture<>(),
            () -> { try { output.close(); } catch (IOException ignored) { } });
    }

    /** Writes {@code text} to the attached session and waits until the pane shows all of it. */
    private void write(TerminalPane pane, String text) throws Exception {
        String title = "shown " + ++writes;
        remote.write((text + "\033]0;" + title + "\007").getBytes(StandardCharsets.UTF_8));
        remote.flush();
        until(() -> pane.session().title().equals(title));
    }

    /** A Cmd-click (Ctrl-click elsewhere) on a cell, through the view's own mouse listener. */
    private static void click(TerminalPane pane, int column, int row) throws Exception {
        edt(() -> {
            TerminalView view = pane.view();
            int x = column * (view.getPreferredSize().width / pane.session().columns()) + 1;
            int y = row * (view.getPreferredSize().height / pane.session().rows()) + 1;
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, LINK | InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
                MouseEvent.BUTTON1));
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, LINK, x, y, 1, false, MouseEvent.BUTTON1));
        });
    }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:80], t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')

def append_to_class(path, code):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')

app = 'jasper-app/src/test/java/dev/jasper/app/'

# FileEditorsTest: the line and the window reach the editor; the desktop gets the file without them.
append_to_class(app + 'plugins/FileEditorsTest.java', r'''
    @Test void aWorkerOpensAtTheLineInTheWindowItWasGiven() {
        var direct = new FileEditors(Runnable::run, () -> false, containment);
        UUID window = UUID.randomUUID();
        List<String> calls = new CopyOnWriteArrayList<>();
        List<Path> desktop = new CopyOnWriteArrayList<>();
        var opening = direct.register("dev.x.a", recording("a", calls, true),
            id -> id.equals(window) ? Optional.of(handle("window")) : Optional.empty());
        assertThat(direct.openFromWorker(NOTES, OptionalInt.of(42), () -> Optional.of(window), desktop::add)).isTrue();
        assertThat(direct.openFromWorker(NOTES, OptionalInt.empty(), Optional::empty, desktop::add)).isTrue();
        opening.close();
        direct.register("dev.x.b", recording("declining", calls, false), FileEditorsTest::noWindow);
        assertThat(direct.openFromWorker(NOTES, OptionalInt.of(7), () -> Optional.of(window), desktop::add)).as("the desktop opened it").isFalse();
        assertThat(calls).containsExactly("a|42|window", "a|-|-", "declining|7|-");
        assertThat(desktop).as("the desktop gets the file, without its line").containsExactly(NOTES);
    }
''')

# PluginRuntimeTest: open(file, line, window, desktop) reaches the provided editor with both.
runtime_test = app + 'plugins/PluginRuntimeTest.java'
edit(runtime_test,
    r'''import java.util.Map;
''',
    r'''import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
''')
append_to_class(runtime_test, r'''
    static final String LINE_EDITOR = """
        package fix.lines;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.platform().editor((file, line, window) -> {
                    String where = window.map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-");
                    try {
                        Files.writeString(context.dataDirectory().resolve("edited"),
                            file.getFileName() + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "|" + where + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return true;
                });
            }
        }
        """;

    /** A Cmd-click opens the file at its line in the clicked pane's window; Jasper's own opens keep the active window. */
    @Test void openWithALineAndAWindowReachesTheProvidedEditor() throws Exception {
        PluginJars.build(root.resolve("bundled").resolve("dev.example.lines"), "lines.jar",
            PluginJars.descriptor("dev.example.lines", "1.0.0", "fix.lines.Main") + "capabilities = [\"editor.provide\"]\n",
            Map.of("fix.lines.Main", LINE_EDITOR), List.of());
        TerminalFixture fixture = AppContractTest.onEdtValue(TerminalFixture::new);
        UUID active = AppContractTest.onEdtValue(fixture::addWindow);
        UUID clicked = AppContractTest.onEdtValue(fixture::addWindow);
        onEdt(() -> fixture.activateWindow(active));
        var created = new AtomicReference<PluginRuntime>();
        onEdt(() -> created.set(new PluginRuntime(new PluginRuntime.Options(root.resolve("bundled"), root.resolve("absent"), null, false,
            root.resolve("plugins.toml"), root.resolve("plugins.lock")),
            new ActivityNotifier(deck.companion(), () -> { }), new PluginRuntime.SettingsReports(d -> { }, file -> { }),
            new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), fixture.registry)));
        PluginRuntime runtime = created.get();
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        List<Path> desktop = new CopyOnWriteArrayList<>();
        runtime.open(root.resolve("Foo.java"), OptionalInt.of(42), Optional.of(clicked), desktop::add);
        runtime.open(root.resolve("Foo.java"), OptionalInt.empty(), Optional.empty(), desktop::add);
        runtime.open(root.resolve("config.toml"), desktop::add);
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.lines/data/edited")))
            .containsExactly("Foo.java|42|" + clicked, "Foo.java|-|-", "config.toml|-|" + active);
        assertThat(desktop).isEmpty();
        onEdt(() -> assertThatIllegalStateException().as("never on the UI thread")
            .isThrownBy(() -> runtime.open(root.resolve("Foo.java"), OptionalInt.of(1), Optional.of(clicked), desktop::add)));
        onEdt(runtime::stop);
    }
''')

# JasperApplicationPluginsTest: only an existing regular file opens, at its line, in the provided editor.
append_to_class(app + 'application/JasperApplicationPluginsTest.java', r'''
    private static final String LINE_EDITOR = """
        package fix.lines;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().editor((file, line, window) -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("edited"),
                            file.getFileName() + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return true;
                });
            }
        }
        """;

    /** Review Focus 1, the application's half: a clicked file opens at its line when it is an existing regular file. */
    @Test void aClickedFileOpensAtItsLineOnlyWhenItIsAnExistingRegularFile() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("lines-plugin");
        PluginJars.build(dev, "lines.jar", PluginJars.descriptor("dev.example.lines", "1.0.0", "fix.lines.Main")
            + "capabilities = [\"editor.provide\"]\n", Map.of("fix.lines.Main", LINE_EDITOR), List.of());
        Path file = java.nio.file.Files.writeString(home.resolve("Foo.java"), "class Foo { }\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
            java.util.UUID window = java.util.UUID.randomUUID();
            application[0].openFileAt(home.resolve("Missing.java"), 3, window);
            application[0].openFileAt(home, 4, window);
            application[0].openFileAt(file, 42, window);
            application[0].openFileAt(file, 0, window);
        });
        Path edited = dirs.plugins().resolve("dev.example.lines/data/edited");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline
            && (!java.nio.file.Files.exists(edited) || java.nio.file.Files.readAllLines(edited).size() < 2)) Thread.sleep(10);
        assertThat(java.nio.file.Files.readAllLines(edited)).as("not the missing file, nor the directory")
            .containsExactlyInAnyOrder("Foo.java|42", "Foo.java|-");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
''')

# RehearsalTest: the warm-up window never opens a file.
append_to_class(app + 'application/RehearsalTest.java', r'''
    @Test void aSilencedWindowOpensNoFile() throws Exception {
        var opened = new CopyOnWriteArrayList<String>();
        edt(() -> {
            var content = DesktopTestSupport.content(launcher(new ArrayDeque<>()));
            content.onOpenFile = (file, line) -> opened.add(file + ":" + line);
            Rehearsal.silence(content);
            content.onOpenFile.accept(java.nio.file.Path.of("notes.txt"), 3);
        });
        assertThat(opened).isEmpty();
    }
''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation fails: cannot find symbol `FileLinks`, `onOpenFile` and `openFileAt`; no `openFromWorker` and no
`open` take an `OptionalInt`.

- [ ] **Step 3: Resolve, install the handler and open**

`app/workspace/FileLinks.java`:

```java
package dev.jasper.app.workspace;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Where a file that a Cmd-click in a terminal named is. Pure path work: whether it exists is the caller's question. */
public final class FileLinks {
    private FileLinks() { }

    /**
     * The file {@code path} names. A path starting {@code ~/} is under {@code home}; a relative one is under
     * {@code directory}, the pane's local directory, and names nothing without one; an absolute one is itself. The
     * result is normalized.
     *
     * @param path the path as the terminal reported it
     * @param directory the pane's local directory, if it has one
     * @param home the user's home directory
     * @param windows whether {@code ~\} means the home directory too
     * @return the file, or empty for a relative path without a directory and for a path this file system cannot hold
     */
    public static Optional<Path> resolve(String path, Optional<Path> directory, Path home, boolean windows) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(home, "home");
        try {
            if (path.startsWith("~/") || windows && path.startsWith("~\\")) return Optional.of(home.resolve(path.substring(2)).normalize());
            Path written = Path.of(path);
            if (written.isAbsolute()) return Optional.of(written.normalize());
            return directory.map(base -> base.resolve(written).normalize());
        } catch (InvalidPathException invalid) {
            return Optional.empty();
        }
    }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:80], t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')

app = 'jasper-app/src/main/java/dev/jasper/app/'

# FileEditors: the line-taking open; the old form delegates to it.
edit(app + 'plugins/FileEditors.java',
    r'''    void openFromWorker(Path file, Supplier<Optional<UUID>> window, Consumer<Path> desktop) {
        Objects.requireNonNull(desktop, "desktop");
        if (editFromWorker(file, OptionalInt.empty(), window)) return;
        if (Thread.currentThread().isInterrupted()) {
            LOG.log(System.Logger.Level.INFO, "The worker was interrupted, so the desktop does not open " + file);
            return;
        }
        desktop.accept(file);
    }
''',
    r'''    void openFromWorker(Path file, Supplier<Optional<UUID>> window, Consumer<Path> desktop) {
        openFromWorker(file, OptionalInt.empty(), window, desktop);
    }

    /**
     * As above, at {@code line}: the editor gets it, and the desktop opens the file without it.
     *
     * @return whether the provided editor opened the file; false when the desktop was given it, or nothing opened
     *         because the worker was interrupted
     * @throws IllegalStateException on the UI thread
     */
    boolean openFromWorker(Path file, OptionalInt line, Supplier<Optional<UUID>> window, Consumer<Path> desktop) {
        Objects.requireNonNull(desktop, "desktop");
        if (editFromWorker(file, line, window)) return true;
        if (Thread.currentThread().isInterrupted()) {
            LOG.log(System.Logger.Level.INFO, "The worker was interrupted, so the desktop does not open " + file);
            return false;
        }
        desktop.accept(file);
        return false;
    }
''')

# PluginRuntime: open with a line and the window the click came from.
runtime = app + 'plugins/PluginRuntime.java'
edit(runtime,
    r'''import java.util.Optional;
import java.util.Set;
''',
    r'''import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
''')
edit(runtime,
    r'''    public void open(Path file, Consumer<Path> desktop) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Editor operations require a worker thread");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(desktop, "desktop");
        PluginHost current = host;
        if (current == null) desktop.accept(file);
        else current.fileEditors.openFromWorker(file, () -> terminals.activeWindow().map(dev.jasper.app.terminals.WindowEntry::id), desktop);
    }
''',
    r'''    public void open(Path file, Consumer<Path> desktop) {
        openFromWorker(file, OptionalInt.empty(), () -> terminals.activeWindow().map(dev.jasper.app.terminals.WindowEntry::id), desktop);
    }

    /**
     * As {@link #open(Path, Consumer)}, at {@code line} and with {@code window} instead of the active one: what a
     * Cmd-click on a file in a terminal opens. The desktop opens the file without the line.
     *
     * @param file the file
     * @param line the 1-based line, or empty for the file's start
     * @param window the terminal window the click came from, or empty for none
     * @param desktop the desktop's opener, such as {@code ConfigEditor::open}; its failure propagates
     * @throws IllegalStateException on the UI thread
     */
    public void open(Path file, OptionalInt line, Optional<UUID> window, Consumer<Path> desktop) {
        Objects.requireNonNull(window, "window");
        openFromWorker(file, line, () -> window, desktop);
    }

    private void openFromWorker(Path file, OptionalInt line, Supplier<Optional<UUID>> window, Consumer<Path> desktop) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Editor operations require a worker thread");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(desktop, "desktop");
        PluginHost current = host;
        if (current == null) desktop.accept(file);
        else current.fileEditors.openFromWorker(file, line, window, desktop);
    }
''')

# TerminalPane: the directory a printed relative path means, and the handler cleared on close.
pane = app + 'workspace/TerminalPane.java'
edit(pane,
    r'''    public Path directory() { return session == null ? launchDirectory : session.workingDirectory().orElse(launchDirectory); }
''',
    r'''    public Path directory() { return session == null ? launchDirectory : session.workingDirectory().orElse(launchDirectory); }

    /**
     * The local directory a relative path printed in this pane is relative to: the one the shell last reported, else the
     * one the pane started in. Empty for a provided session, and while the shell reports a directory on another host,
     * since nothing printed there is a path on this machine.
     */
    Optional<Path> localDirectory() {
        PaneSnapshot now = snapshot();
        if (now.providerId().isPresent() || now.remoteDirectory().isPresent()) return Optional.empty();
        return Optional.of(directory());
    }
''')
edit(pane,
    r'''            view.setShortcutHandler(null); view.setContextMenuHandler(null); view.setOnCloseRequest(() -> {});
''',
    r'''            view.setShortcutHandler(null); view.setContextMenuHandler(null); view.setFileLinkHandler(null); view.setOnCloseRequest(() -> {});
''')

# WindowContent: the hook, the view's handler, and the hook reset on close.
window = app + 'workspace/WindowContent.java'
edit(window,
    r'''import dev.jasper.app.terminals.SessionRequest;

/** Real window contents''',
    r'''import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.terminal.view.FileLink;

/** Real window contents''')
edit(window,
    r'''    public java.util.function.BooleanSupplier anyWindowActive = () -> true;
''',
    r'''    public java.util.function.BooleanSupplier anyWindowActive = () -> true;
    /**
     * A Cmd-click (Ctrl-click elsewhere) named a file in one of this window's local panes: the resolved path, and the
     * line written after it, 0 for none. The application checks the file off the EDT and opens it; by default nothing
     * opens. EDT.
     */
    public java.util.function.BiConsumer<Path, Integer> onOpenFile = (file, line) -> { };
''')
edit(window,
    r'''            JPopupMenu popup = chrome.contextMenu();
            popup.show(pane.view(), event.getX(), event.getY());
        });
''',
    r'''            JPopupMenu popup = chrome.contextMenu();
            popup.show(pane.view(), event.getX(), event.getY());
        });
        pane.view().setFileLinkHandler(link -> openFileLink(pane, link));
''')
edit(window,
    r'''    void selectTab(Component tab) {
''',
    r'''    /**
     * A Cmd-click on a file in {@code pane}: resolved against the pane's local directory and given to {@link #onOpenFile}.
     * A provided session, or a shell that reports a directory on another host, opens nothing.
     */
    private void openFileLink(TerminalPane pane, FileLink link) {
        Optional<Path> local = pane.localDirectory();
        if (local.isEmpty()) return;
        FileLinks.resolve(link.path(), local, Path.of(System.getProperty("user.home")),
                System.getProperty("os.name", "").startsWith("Windows"))
            .ifPresent(file -> onOpenFile.accept(file, link.line()));
    }

    void selectTab(Component tab) {
''')
edit(window,
    r'''        onToggleBuddy = () -> {}; buddyEnabled = () -> false;
''',
    r'''        onToggleBuddy = () -> {}; buddyEnabled = () -> false; onOpenFile = (file, line) -> {};
''')

# Rehearsal: the warm-up window opens no file.
edit(app + 'application/Rehearsal.java',
    r'''        content.onLaunchFailure = (details, cause) -> report.accept("Could not start the terminal: " + details);
''',
    r'''        content.onLaunchFailure = (details, cause) -> report.accept("Could not start the terminal: " + details);
        content.onOpenFile = (file, line) -> { };
''')

# JasperApplication: each window's clicks open on a worker, at the line, in that window.
application = app + 'application/JasperApplication.java'
edit(application,
    r'''            window.content().onLaunchFailure = (details, cause) -> incidentNotices.launchFailure(windowId, details, cause);
''',
    r'''            window.content().onLaunchFailure = (details, cause) -> incidentNotices.launchFailure(windowId, details, cause);
            window.content().onOpenFile = (file, line) -> openFileAt(file, line, windowId);
''')
edit(application,
    r'''    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */
''',
    r'''    /**
     * Opens a file that a Cmd-click in window {@code window} named, at {@code line} (0 for none), when it is an existing
     * regular file: on a virtual thread, through the editor a plugin provides, else the desktop. Anything else opens
     * nothing and tells nobody. Reads {@link #plugins} when the thread runs; before they start, the desktop alone.
     */
    void openFileAt(Path file, int line, UUID window) {
        Thread.ofVirtual().name("jasper-open-file").start(() -> {
            try {
                if (!java.nio.file.Files.isRegularFile(file)) return;
                var desktop = new dev.jasper.app.platform.ConfigEditor();
                PluginRuntime runtime = plugins;
                if (runtime == null) desktop.open(file);
                else runtime.open(file, line > 0 ? OptionalInt.of(line) : OptionalInt.empty(), Optional.of(window), desktop::open);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not open the clicked file " + file, failure);
            }
        });
    }

    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */
''')

# The new test that matches ThemeMigrationCoverageTest's scan (it sends AWT mouse events).
with open('docs/design/theme-engine/migration.csv', 'a', encoding='utf-8') as csv:
    csv.write('jasper-app/src/test/java/dev/jasper/app/workspace/FileLinkClickTest.java,app.workspace,FileLinkClickTest,nonvisual-helper,none,none,'
              'jasper-app/src/test/java/dev/jasper/app/workspace/FileLinkClickTest.java,Test/fixture ownership; New in the Cmd-click file paths '
              'plan Task 3; drives a terminal view with AWT mouse events and owns no Swing component or theme\n')
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.FileLinksTest' --tests 'dev.jasper.app.workspace.FileLinkClickTest' --tests 'dev.jasper.app.plugins.FileEditorsTest' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest' --tests 'dev.jasper.app.application.RehearsalTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS. Skipped: `driveAndBackslashPathsResolveOnWindows` except on Windows; on Windows,
`aProvidedSessionOpensNothing` and `aLocalShellsReportedDirectoryResolvesItsPaths` (as `SessionPaneTest` and the
`/bin/sh` fixtures are) and all of `JasperApplicationPluginsTest`.

Then run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL. `workspace` names `view.FileLink`, which is now supported, and depends on neither `plugins`
nor `application`; SDK types stay in `plugins`.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace/FileLinks.java \
  jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java \
  jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/FileEditors.java \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/main/java/dev/jasper/app/application/Rehearsal.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/FileLinksTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/FileLinkClickTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/FileEditorsTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java \
  jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java \
  jasper-app/src/test/java/dev/jasper/app/application/RehearsalTest.java \
  docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Cmd-click on a file path opens it in the default editor at its line

A local pane resolves the clicked path against the directory its shell
reported, else the one it started in; a provided session or a remote
directory opens nothing. The window hands the file to the application,
which checks on a virtual thread that it is a regular file and opens it
through the plugin editor with the line and the clicked pane's window,
then the desktop. PluginRuntime.open and FileEditors.openFromWorker gain
the line-taking forms.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Documentation, status and the whole-branch check

**Files:**
- Modify: `docs/editor.md`, `docs/STATUS.md`, `AGENTS.md` (one verified-behaviour bullet),
  `docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md`, this plan's status line
- No change: `docs/configuration.md` does not describe terminal clicks. Task 2 brought
  `docs/terminal-architecture.md` and `jasper-terminal/README.md` up to date.

**Interfaces:**
- Consumes: everything Tasks 1–3 shipped.
- Produces: documentation only. `AppDocumentationTest` checks the links and anchors (`editor.md`'s
  `#default-editor`); `TerminalDocumentationTest` checks the terminal guides.

Follow AGENTS.md's documentation rules: the smallest edit that makes each doc accurate, describing only what the system
does. The negatives in the new bullet are ones a reader would expect otherwise (remote panes and paths with spaces).

- [ ] **Step 1: User documentation**

```bash
python3 - <<'PY'
import pathlib

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:80], t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')

edit('docs/editor.md',
    '''  messages here; Ctrl-C stops waiting and leaves the tabs open.
''',
    '''  messages here; Ctrl-C stops waiting and leaves the tabs open.
- Cmd-click (Ctrl-click on Linux and Windows) a file path in a local terminal pane: `src/Foo.java:42`,
  `Foo.java:42:5`, `Foo.cs(42,5)`, `./notes.md`, `~/a.txt`, `/tmp/x.log`, on Windows also `C:\\…` and backslash
  paths, or a `file://` link to this machine, as `ls --hyperlink` prints. A relative path is relative to the directory
  the shell last reported, else the one the pane started in. An existing file opens in the
  [default editor](#default-editor), at the line when the editor takes one; anything else does nothing. A path with
  spaces opens only from a link. Inside a program that uses the mouse, Ctrl+Shift-click on Linux and Windows. A pane
  whose shell reports a directory on another host, and a session a plugin provides, such as Remote's ssh, open nothing.
''')
edit('AGENTS.md',
    "Jasper's `ShellIntegrationFilter` rewrites OSC 7, OSC 133 and DECSCUSR 0 into `OSC 1341;jasper;…`.\n",
    "Jasper's `ShellIntegrationFilter` rewrites OSC 7, OSC 133 and DECSCUSR 0 into `OSC 1341;jasper;…`.\n"
    "- `HyperlinkStyle` equality ignores its link, so a cache keyed by `TextStyle` gives every OSC 8 link in the same colours the first link's target; `JediCellReader` keys hyperlink styles by their link as well.\n")
PY
```

- [ ] **Step 2: The whole-branch check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. If the call times out, run it again; finished tasks stay up to date.

Run: `./gradlew :jasper-app:verifyPackage`
Expected: BUILD SUCCESSFUL (headless; no GUI, no AOT training).

Run the source hygiene check from AGENTS.md:

```bash
python3 - <<'PY'
import pathlib, sys
for name in sys.argv[1:] or [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 3: Status**

Add the entry at the top of `docs/STATUS.md`, with the totals read from every module's and plugin's JUnit XML from the
run in step 2, and set the spec's and this plan's status lines. `RULINGS` is the one sentence to change by hand: when
the executor recorded rulings or deviations in this plan's banner during Tasks 1–3, replace it with one sentence naming
them.

```bash
python3 - <<'PY'
import datetime, glob, pathlib, xml.etree.ElementTree as ET
RULINGS = "No ruling during execution changed the plan."
counts = [0, 0, 0, 0]
for report in glob.glob('*/build/test-results/test/*.xml') + glob.glob('plugins/*/build/test-results/test/*.xml'):
    suite = ET.parse(report).getroot()
    for i, key in enumerate(('tests', 'failures', 'errors', 'skipped')):
        counts[i] += int(suite.get(key))
tests, failures, errors, skipped = (f"{n:,}" for n in counts)
today = datetime.date.today().isoformat()
entry = f"""**Cmd-click file paths (command line plan 3) — {today}** (`claude/default-editor`, worktree
`.claude/worktrees/default-editor`; [spec](superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md),
[plan](superpowers/plans/2026-10-14-jasper-command-line-3-cmd-click-paths.md)). Cmd-click on macOS, Ctrl-click
elsewhere, on a file path in a local pane's output (`PATH`, `PATH:LINE`, `PATH:LINE:COLUMN`, `PATH(LINE)` and
`PATH(LINE,COLUMN)`; absolute, `./`, `../`, `~/`, or relative with a `/` or an extension; `C:\\…` and backslash paths
on Windows; across soft wraps), or on an OSC 8 or plain-text `file://` link to this machine, opens the file in the
default editor at the line. `jasper-terminal` finds the path (`internal.text.PathDetector`, matched outside the buffer
lock) and reports a new supported value, `view.FileLink`, through `TerminalView.setFileLinkHandler`; a URL still opens
the browser first, and a click on anything else still starts a selection. A plain-text `file://` URL no longer goes to
the browser. The app resolves the path against the pane's local directory (`workspace.FileLinks`), checks on a virtual
thread that it is a regular file, and opens it through `PluginRuntime.open` with the line and the clicked pane's
window; a provided session, or a shell reporting a remote directory, opens nothing. Fixed on the way: `JediCellReader`
cached cell styles by colour, and JediTerm's `HyperlinkStyle` equality ignores the link, so every OSC 8 link in the
same colours opened the first one's target. Deviations: the five in the plan's Planned deviations. {RULINGS}
`./gradlew check --continue`: **{tests} tests, {failures} failures, {errors} errors, {skipped} skipped**. No GUI
launched. GUI checks for the user, in the packaged app: Cmd-click on compiler output (`javac`'s `src/Foo.java:42:`,
`dotnet build`'s `Foo.cs(42,5):`) in a local pane opens the Jasper Editor at the line; in a Remote ssh pane a path
opens nothing; a path soft-wrapped in a narrow window opens from either row; names from `ls --hyperlink` (GNU) or
`eza --hyperlink` open by Cmd-click; on Windows and Linux, Ctrl-click, and Ctrl+Shift-click inside a program that uses
the mouse. Not merged or pushed."""

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:70])
    p.write_text(t.replace(old, new), encoding='utf-8')

edit('docs/STATUS.md', "# Jasper — Status and Handoff\n\n", "# Jasper — Status and Handoff\n\n" + entry + "\n\n")
edit('docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md',
    "its deviations are in that plan's Planned deviations and `docs/STATUS.md`. Branch:",
    "its deviations are in that plan's Planned deviations and `docs/STATUS.md`. Plan 3 (Cmd-click file paths) complete on "
    f"{today}: [`2026-10-14-jasper-command-line-3-cmd-click-paths.md`](../plans/2026-10-14-jasper-command-line-3-cmd-click-paths.md); "
    "its deviations are in that plan's Planned deviations and `docs/STATUS.md`. Branch:")
# The banner is line 5 and may carry rulings recorded during execution: change only its state, keep the rest. The
# plan's own text quotes the old state too, so the edit goes by line, not by anchor.
plan = pathlib.Path('docs/superpowers/plans/2026-10-14-jasper-command-line-3-cmd-click-paths.md')
lines = plan.read_text(encoding='utf-8').split('\n')
assert lines[4].startswith('Status: **Written 2026-10-14**'), lines[4]
lines[4] = lines[4].replace('Status: **Written 2026-10-14**', f'Status: **Complete on {today}** (written 2026-10-14)', 1).replace(' Not started.', '', 1)
plan.write_text('\n'.join(lines), encoding='utf-8')
print(entry.splitlines()[0], counts)
PY
```

Expected: it prints the entry's first line and four totals whose failures and errors are 0.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' :jasper-terminal:test --tests 'dev.jasper.terminal.examples.*'`
Expected: PASS (links and anchors in the edited docs).

- [ ] **Step 4: Commit**

```bash
git add docs/editor.md AGENTS.md \
  docs/STATUS.md \
  docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md \
  docs/superpowers/plans/2026-10-14-jasper-command-line-3-cmd-click-paths.md
git commit -m "$(cat <<'EOF'
Document Cmd-click file paths

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```
