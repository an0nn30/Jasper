# Jasper Terminal Architecture and Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the terminal library independently understandable and maintainable through cohesive packages, explicit ownership, fluent immutable options, reusable actions, and tested developer documentation.

**Architecture:** Preserve JediTerm and the existing algorithms while extracting concrete session and view collaborators. Introduce a deliberate supported API and an explicitly internal session/view bridge; no plugin loader or emulator abstraction. Complete one runnable refactor deliverable through independently reviewed checkpoints.

**Tech Stack:** Java/JBR 25, Gradle wrapper, Swing, jediterm-core 3.76, pty4j 0.13.10, JNA 5.14.0, JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** [Approved design](../specs/2026-09-20-jasper-terminal-refactor-design.md).

**Status:** Execution authorized on 2026-09-20, natively, in `codex/terminal-refactor-native`. The user's native choice supersedes per-task subagents; one independent whole-branch review remains required. This execution is independent of the older refactor branch. Tasks 1–5 are implemented; Task 2 value tests are grouped in FluentOptionsTest rather than separate files. Task 4 rejected full-width arrays after measurements; its implemented TerminalRow has locked live and compact detached implementations, with detached style conversion during reads. The verification report records the pinned-vendor assumption and performance evidence. Progress and rulings are recorded in the plan-specific execution ledger and STATUS.

## Global Constraints

- Keep Java/JBR 25, Swing, the two existing Gradle modules, and the pinned runtime dependencies.
- `jasper-terminal` never depends on `jasper-app`.
- No emulator-backend abstraction, new plugin framework, dependency upgrade, or new production interface without two real implementations is part of this work.
- Source and binary compatibility with the old flat package are not acceptance requirements; preserve user-visible behavior and migrate repository callers.
- Force `TERM=xterm-256color` and `COLORTERM=truecolor` for child processes.
- Every buffer read takes its lock. Keep work under the lock bounded; regex and font shaping run outside it.
- Absolute rows are `discardedLines + historyLines + screenRow`; preserve existing invalidation on history clear, reflow, and alternate-buffer transitions.
- Search retains one running and at most one queued request per view; browser dispatch remains shared, lazy, bounded, and never caller-runs.
- No public terminal signature exposes a JediTerm type. Supported application APIs also exclude internal types.
- Use `./gradlew`, never system Gradle. Tests remain headless; no GUI, native benchmark, merge, or push without the applicable user authorization.
- No raw controls, private-use characters, or unpaired surrogates in source; use Java escapes.
- One reviewed commit per task, with a `Co-Authored-By:` trailer. Preserve unrelated working changes.
- Record any execution deviation here and in `docs/STATUS.md` before handing off.

## Review Focus

1. A failure after PTY creation must close the child and connector, even when engine construction or reader startup fails: Task 3's injected-construction failure regression.
2. A queued search completing after reset, detach, or a newer query must neither repaint obsolete matches nor invoke its old callback: Task 7's deterministic queued-completion regression.
3. A reader callback paused across detach/reattach must not claim the new attachment's frame/bell tokens: Task 10's explicit old-token regression.
4. A mouse button released after modifier or reporting-mode changes must retain its press ownership, with no snapshot allocation on report-only paths: Task 9's gesture and existing efficiency regressions.
5. Caller mutation after builder construction must not change a pending launch or live options; validation failure must happen before process creation: Tasks 2–3's defensive-copy and invalid-launch regressions.

## Baseline and execution rules

Planning base: production tree at `0f82c55`; spec commit `2c2045a`. The current
checkout has an unrelated change to `TerminalTitle.java`, untracked
`docs/terminal-refactor-assessment.md`, and `worktrees/`; do not stage, remove, or
copy them into a refactor commit. Start execution from the committed plan in an
isolated worktree using `superpowers:using-git-worktrees`.

Fresh terminal baseline on 2026-09-20:
`./gradlew :jasper-terminal:test --rerun-tasks` — 322 tests, 321 passed, one skipped.
A subsequent full `./gradlew check` failed in **app** tests: 728 tests, 724 passed,
three failures, one skipped. The terminal XML remains from its separate prior
run, not a terminal execution completed by that failed full check.

Failing cases in `TerminalTitleIntegrationTest`:

- `oscTitlesUpdateTheTabWindowAndBubbleWhileACommandRuns`: expected `~ (sh)`, observed `~ (bash)`.
- `programTitlesWorkWithoutShellIntegrationAndManualTabNamesStillWin`: five-second condition timeout.
- `aBurstEndingWithAPromptTitleCannotOverwriteTheCompletedNotice`: five-second condition timeout.

These occurred before implementation. Do not hard-code `(bash)`, increase waits,
remove tests, or report a green full baseline. Task 1 investigates them separately.
If a production bug requires a behavioral fix, write a bounded regression/fix
amendment after diagnosis; do not conceal that work in an extraction task.

### Reading and code-reuse convention

All paths below are relative to the repository root. `MAIN` means
`jasper-terminal/src/main/java/dev/jasper/terminal`; `TEST` means the matching
`src/test/java/dev/jasper/terminal`. Expand these prefixes in file operations.
Names in migration tables identify **whole declarations**, including annotations
and comments. Copy their existing implementations; do not re-create their
algorithms from prose. Exact baseline source is available independently of any
working tree using:

```bash
git show 0f82c55:jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java
git show 0f82c55:jasper-terminal/src/main/java/dev/jasper/terminal/TerminalView.java
```

A refactor's first test run is a green characterization baseline. Existing tests
are not artificially broken merely to claim TDD. New behavior/contracts (builders,
commands, failed-start cleanup, architecture restrictions) receive real RED/GREEN
cycles. Code blocks below supply new logic; extraction tables identify the exact
existing logic and the substitutions needed. Update JavaDoc in the same task.

Each task's last step is: run its specified checks, inspect XML, update the task
checkbox/status and corresponding docs, request an independent review, fix
findings, and commit only that task's files. Example commit form:

```bash
git commit -m 'refactor(terminal): extract session lifecycle ownership' -m 'Co-Authored-By: Codex <noreply@openai.com>'
```

## Final file map and dependency contract

The following table accounts for every existing production class. New classes
are listed in their owning tasks. Package migration happens after extraction,
so earlier tasks use the flat package and preserve a compiling app.

| Existing class | Final package |
| --- | --- |
| TerminalSession | session |
| TerminalView, KeyEncoder, KeyInput, MouseRouting, Viewport | view |
| TerminalOptions, Palette, CursorStyle, BellMode, OptionAsMeta, GridSize | config |
| FindResult | search |
| FontSet | rendering |
| SessionDisplay, UriLink, ShellIntegrationConnector | internal.emulation |
| PtyConnector | internal.emulation (vendor adapter; process ownership extracted) |
| ShellIntegrationFilter | internal.shell |
| Selection, SelectionText, RowText, LogicalLine, WordBoundaries, TerminalSearch, LinkDetector, CommandCapture | internal.text |
| CellStyle, Run, RunBuilder, TerminalPainter, ScreenSnapshot | internal.rendering |

New primary owners:

```text
session/               SessionLaunchOptions, TerminalSessionListener, PtySessionFactory
view/                  TerminalAction, KeyboardController, MouseController,
                       SelectionController, SearchController, RenderScheduler, BellController
search/                SearchQuery
internal/              TerminalAccess (session/view bridge)
internal/process/      PtyChild, ForegroundJobResolver
internal/emulation/    JediTermEngine, BufferQueries, JediCellReader
internal/shell/        ShellCommandTracker, CommandLocation, CompletedCommand
internal/text/         AbsoluteRowState, TerminalRow, CellAttributes, CursorRequest,
                       MouseInput, MouseGeometry, SelectedCells
internal/desktop/      DesktopServices
```

The row representation is the concrete boundary refinement needed to make this
package graph acyclic: `TerminalRow` owns arrays of UTF-16 cells and immutable
Jasper style values; it does not import or return vendor objects. Capture it
once directly from live rows under the lock. Do not first copy a `TerminalLine`
and then copy that into arrays. This replaces the spec's suggested opaque
vendor-row wrapper with a single Jasper-owned copy. It needs measured allocation
and lock-duration comparison in Task 4; reject or revise it if those regress
materially. This is an explicit proposed refinement, not an approved performance
claim. It is the only proposed implementation refinement of the written spec.

Dependency direction:

```text
app -> supported session/view/config/search/rendering types
view -> session + internal bridge + rendering + text + desktop + config/search
session -> internal bridge + engine + process + shell + config
internal bridge -> engine + text + rendering values + shell
engine -> process + shell + text + rendering values + config
internal rendering -> text + config + public FontSet
internal text -> config + public search values
internal shell -> JDK only
internal process -> pty4j/JNA + JDK
config/search/public rendering/desktop -> JDK only
```

`BufferQueries` may depend on text algorithms; text algorithms never depend back
on the engine. `ScreenSnapshot` is a pure Jasper data record in internal
rendering. Its vendor capture method moves into the engine/query adapter.
Shell tracker callbacks use JDK functional interfaces and shell-owned immutable
records, never `TerminalSession` or `JediTermEngine` references. Session listeners
are adapted at the facade; the engine does not import the session package.

## Task 1: Establish a trustworthy baseline and maintenance fixtures

**Files:**
- Create: `docs/terminal-refactor-verification.md`.
- Inspect: `jasper-app/src/test/java/dev/jasper/app/TerminalTitleIntegrationTest.java`, `MAIN/PtyConnector.java`, `jasper-app/src/main/java/dev/jasper/app/TerminalTitle.java`.
- Preserve: existing terminal tests and their input fixtures.

**Interfaces:** No production API changes. Produces the exact baseline commit,
runtime, XML totals, failure classification, and benchmark fixture definition
used by all subsequent task reviews.

- [x] **Step 1: Create the isolated execution worktree and reproduce the full check.**

```bash
git status --short
./gradlew check --rerun-tasks
```

Record the actual exit status and each module separately. Read counts with:

```python
from pathlib import Path
import xml.etree.ElementTree as ET
for module in ('jasper-terminal', 'jasper-app'):
    totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
    for path in Path(module, 'build/test-results/test').glob('TEST-*.xml'):
        root = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(root.get(key, 0))
        for case in root.findall('testcase'):
            for failure in case.findall('failure'):
                print(module, case.get('name'), failure.get('message'))
    print(module, totals)
```

- [x] **Step 2: Diagnose the three known app failures before changing expectations.**

Use `superpowers:systematic-debugging`. Run:

```bash
./gradlew :jasper-app:test --tests '*TerminalTitleIntegrationTest' --rerun-tasks
```

Trace the fixture's executable, `PtyConnector.foregroundJob()`, title composition,
and its asynchronous polling. Compare the actual process metadata with the
asserted shell name. Distinguish executable naming from stale-event/timing
failures; a timeout is not proof of the same cause. Preserve the title and manual
rename assertions. Record a confirmed cause or clearly state it remains open.
Any correction is a separate reviewed regression/fix commit and plan amendment.
Do not begin structural changes with unexplained new failures.

- [x] **Step 3: Record headless comparison fixtures without adding flaky timing assertions.**

Use the existing rendering tests' `BufferedImage`/`FontSet` setup and the same
in-memory connector. Fixture A: 150 columns × 45 rows, default library font and
palette, repeated `"\033[31mRED\033[0m plain\r\n"` until 10,000 history rows.
Fixture B: 150 × 45, alternating style runs and text containing `"\u754c"` and
`"\uD83D\uDE00"`. Fixture C: mouse reporting enabled, 10,000 MOVED/DRAGGED events.
For A/B measure 1,000 warm-up captures/paints and five batches of 1,000 captures/
paints. Use the same process/runtime on both sides and record median/range,
thread allocation bytes when the runtime supports them, and JFR allocation/
monitor evidence. C must retain the existing zero-line-copy/no-repaint assertion.
These are acceptance measurements, not timing pass/fail unit tests.

- [x] **Step 4: Commit the baseline report.**

The report contains environment, commit, commands, XML totals, known failures,
fixture parameters and limitations. No GUI/benchmark task is launched. The
native throughput/RSS checklist remains explicitly pending user execution.

## Task 2: Add fluent immutable options and typed queries

**Files:**
- Modify: `MAIN/TerminalOptions.java`, `MAIN/GridSize.java`.
- Create: `MAIN/SessionLaunchOptions.java`, `MAIN/SearchQuery.java`.
- Test: `TEST/TerminalOptionsTest.java`, new `TEST/SessionLaunchOptionsTest.java`, `TEST/SearchQueryTest.java`.

**Interfaces:**
- `TerminalOptions.Builder TerminalOptions.builder()` uses library defaults.
- `TerminalOptions.Builder TerminalOptions.toBuilder()` preserves all values.
- A fluent method for every record component; `TerminalOptions build()` uses the canonical constructor.
- `SessionLaunchOptions(List<String> command, Map<String,String> environment, Path workingDirectory, GridSize grid, int scrollback)`; `builder()` and `toBuilder()`.
- `SearchQuery(String text, boolean regex, boolean caseSensitive)`.
- GridSize becomes public, retaining its existing minimum clamping and `fit` semantics.

- [x] **Step 1: Add defensive-copy and canonical-validation tests before builders.**

```java
@Test void aBuilderRoundTripPreservesEveryOptionAndDoesNotMutateItsSource() {
    TerminalOptions original = TerminalOptions.defaults();
    TerminalOptions changed = original.toBuilder().fontSize(22f)
        .bell(BellMode.NONE).copyOnSelect(true).build();
    assertThat(original.fontSize()).isEqualTo(14f);
    assertThat(changed.fontSize()).isEqualTo(22f);
    assertThat(changed.toBuilder().build()).isEqualTo(changed);
    assertThat(changed.fallbackFonts()).isEqualTo(original.fallbackFonts());
    assertThatThrownBy(() -> original.toBuilder().fontSize(Float.NaN).build())
        .isInstanceOf(IllegalArgumentException.class);
}

@Test void launchOptionsOwnTheirCollectionsAndRequireExplicitProcessInputs() {
    var command = new ArrayList<>(List.of("shell", "-l"));
    var environment = new HashMap<>(Map.of("LANG", "C.UTF-8"));
    var built = SessionLaunchOptions.builder().command(command).environment(environment)
        .workingDirectory(Path.of(".")).grid(new GridSize(80, 24)).scrollback(100).build();
    command.set(0, "changed");
    environment.put("LANG", "changed");
    assertThat(built.command()).containsExactly("shell", "-l");
    assertThat(built.environment()).containsEntry("LANG", "C.UTF-8");
    assertThatThrownBy(() -> SessionLaunchOptions.builder().build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> built.toBuilder().command(List.of()).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> built.toBuilder().scrollback(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
}
```

Use standard JUnit/AssertJ imports plus the named JDK collection/path classes.
Run `./gradlew :jasper-terminal:test --tests '*OptionsTest'`; expected RED:
missing builder/type, not an unrelated failure.

- [x] **Step 2: Generate the complete TerminalOptions builder from its record components.**

The following edit script inserts a complete nested builder; run it only during
execution. It avoids hand-maintained duplication of the twelve component names.

```python
from pathlib import Path
p = Path('jasper-terminal/src/main/java/dev/jasper/terminal/TerminalOptions.java')
s = p.read_text()
assert 'class Builder' not in s
fields = [('String','fontFamily'),('float','fontSize'),('List<String>','fallbackFonts'),
          ('boolean','ligatures'),('Palette','palette'),('CursorStyle','cursorStyle'),
          ('boolean','cursorBlink'),('OptionAsMeta','optionAsMeta'),('int','scrollback'),
          ('boolean','copyOnSelect'),('float','lineHeight'),('BellMode','bell')]
lines = ['    /** Starts a builder with the standalone library defaults. */',
         '    public static Builder builder() { return defaults().toBuilder(); }',
         '    /** Copies every option into a new mutable builder. */',
         '    public Builder toBuilder() { return new Builder(this); }',
         '    /** Mutable, thread-confined construction of immutable terminal options. */',
         '    public static final class Builder {']
for t,n in fields:
    lines.append(f'        private {t} {n};')
lines.append('        private Builder(TerminalOptions source) {')
for t,n in fields:
    lines.append(f'            {n} = source.{n}();')
lines.append('        }')
for t,n in fields:
    value = 'List.copyOf(value)' if n == 'fallbackFonts' else 'value'
    lines += [f'        /** Sets {n}; validation is completed by build(). */',
              f'        public Builder {n}({t} value) {{ {n} = {value}; return this; }}']
lines += ['        /** Builds and validates an independent immutable value. */',
          '        public TerminalOptions build() {',
          '            return new TerminalOptions(' + ', '.join(n for _,n in fields) + ');',
          '        }', '    }']
pos = s.rfind('}')
p.write_text(s[:pos] + '\n' + '\n'.join(lines) + '\n' + s[pos:])
```

Replace generated setter descriptions with the component's units/ranges and live
versus new-session semantics before commit; the source record's existing
canonical validation remains the only value validator.

- [x] **Step 3: Implement the new launch and query values.**

```java
public record SessionLaunchOptions(List<String> command, Map<String, String> environment,
        Path workingDirectory, GridSize grid, int scrollback) {
    public SessionLaunchOptions {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(grid, "grid");
        if (command.isEmpty() || command.getFirst().isBlank())
            throw new IllegalArgumentException("command must name an executable");
        if (command.stream().anyMatch(value -> value.indexOf('\0') >= 0))
            throw new IllegalArgumentException("command must contain no NUL");
        environment.forEach((key, value) -> {
            if (key.isEmpty() || key.indexOf('=') >= 0 || key.indexOf('\0') >= 0
                    || value.indexOf('\0') >= 0)
                throw new IllegalArgumentException("invalid environment entry");
        });
        if (scrollback < 0 || scrollback > 1_000_000)
            throw new IllegalArgumentException("scrollback must be within 0–1000000");
    }
    public static Builder builder() { return new Builder(); }
    public Builder toBuilder() {
        return new Builder().command(command).environment(environment)
            .workingDirectory(workingDirectory).grid(grid).scrollback(scrollback);
    }
    public static final class Builder {
        private List<String> command;
        private Map<String,String> environment;
        private Path workingDirectory;
        private GridSize grid = new GridSize(80, 24);
        private int scrollback = 10_000;
        private Builder() { }
        public Builder command(List<String> v) { command = List.copyOf(v); return this; }
        public Builder environment(Map<String,String> v) { environment = Map.copyOf(v); return this; }
        public Builder workingDirectory(Path v) { workingDirectory = v; return this; }
        public Builder grid(GridSize v) { grid = v; return this; }
        public Builder scrollback(int v) { scrollback = v; return this; }
        public SessionLaunchOptions build() {
            return new SessionLaunchOptions(command, environment, workingDirectory, grid, scrollback);
        }
    }
}
```

Use `java.nio.file.Path`, `java.util.List/Map/Objects`; the initial package is
`dev.jasper.terminal`. Grid defaults here preserve a useful standalone grid, not
the app's separately resolved window grid. Required process inputs have no
implicit defaults. The builder performs no filesystem checks or process I/O.

```java
public record SearchQuery(String text, boolean regex, boolean caseSensitive) {
    public SearchQuery { java.util.Objects.requireNonNull(text, "text"); }
}
```

- [x] **Step 4: Run value tests and full terminal tests; document and commit.**

`./gradlew :jasper-terminal:test`. Add tests for fallback-list mutation and launch
builder reuse. Existing canonical validation tests continue to protect bounds.

## Task 3: Separate PTY construction and process metadata

**Files:**
- Create: `MAIN/PtyChild.java`, `MAIN/PtySessionFactory.java`, `MAIN/ForegroundJobResolver.java`.
- Modify: `MAIN/PtyConnector.java`, `MAIN/TerminalSession.java`.
- Test: new `TEST/PtySessionFactoryTest.java`, existing `TEST/PtyConnectorTest.java`.

**Interfaces:**
- `TerminalSession.start(SessionLaunchOptions) throws IOException`.
- `PtyChild.start(SessionLaunchOptions)` initially; at package migration replace options parameter with `List<String>, Map<String,String>, Path, int, int` to avoid process → session dependency.
- `PtyChild.read(char[],int,int)`, `write(byte[])`, `ready()`, `isConnected()`, `resize(int,int)`, `waitFor()`, `close()`, `foregroundJob()`.
- `PtyConnector(PtyChild)` remains the hidden JediTerm adapter.
- `PtySessionFactory.finish(TtyConnector, Supplier<TerminalSession>)` is package-private until the vendor adapter moves; its final equivalent uses `PtyChild` plus `Supplier<TerminalSession>`.

- [x] **Step 1: Add the startup-cleanup regression.**

```java
@Test void constructionFailureClosesTheAlreadyCreatedConnector() {
    AtomicInteger closes = new AtomicInteger();
    TtyConnector connector = new FakeConnectorForClose(closes);
    RuntimeException failure = new IllegalStateException("engine setup failed");
    assertThatThrownBy(() -> PtySessionFactory.finish(connector, () -> { throw failure; }))
        .isSameAs(failure);
    assertThat(closes).hasValue(1);
}
```

Use this complete nested fixture (with the same vendor imports as FakeConnector):

```java
private static final class FakeConnectorForClose implements TtyConnector {
    private final AtomicInteger closes;
    FakeConnectorForClose(AtomicInteger closes) { this.closes = closes; }
    public int read(char[] buffer,int offset,int length) { return -1; }
    public void write(byte[] bytes) { }
    public void write(String text) { }
    public boolean isConnected() { return false; }
    public boolean ready() { return false; }
    public void resize(TermSize size) { }
    public int waitFor() { return 0; }
    public String getName() { return "closed-fixture"; }
    public void close() { closes.incrementAndGet(); }
}
```

It needs no pipes or threads. Run the test before adding the factory. After
Task 6 replaces the production factory's vendor argument with PtyChild, preserve
this failure assertion at the engine's package-private connector construction
seam, and exercise the final factory's child cleanup with a controlled child.
Do not make PtyChild subclassable solely to retain a fixture.

- [x] **Step 2: Extract process ownership without changing shutdown behavior.**

Move `PtyConnector`'s `process`, `reader`, `input`, `closing`, and close timing
constant to `PtyChild`. Move its I/O, connection, wait, close and closeStreams bodies unchanged;
replace `resize(TermSize size)` with `resize(int columns,int rows)` and construct
`new WinSize(columns,rows)` at the same point. Move `initialProgram`, `loginShell`,
`loginOption`, `foregroundJob`, and `ForegroundGroup` to `ForegroundJobResolver`;
its constructor accepts the same `PtyProcess` and command list. `PtyChild` owns
one resolver and delegates the metadata query.

The adapter's complete delegation body is:

```java
final class PtyConnector implements TtyConnector {
    private final PtyChild child;
    PtyConnector(PtyChild child) { this.child = child; }
    public int read(char[] b, int o, int n) throws IOException { return child.read(b,o,n); }
    public void write(byte[] b) throws IOException { child.write(b); }
    public void write(String s) throws IOException { write(s.getBytes(StandardCharsets.UTF_8)); }
    public boolean isConnected() { return child.isConnected(); }
    public boolean ready() throws IOException { return child.ready(); }
    public void resize(TermSize size) { child.resize(size.getColumns(),size.getRows()); }
    public int waitFor() throws InterruptedException { return child.waitFor(); }
    public String getName() { return "pty"; }
    public void close() { child.close(); }
    Optional<String> foregroundJob() { return child.foregroundJob(); }
}
```

Preserve the old `getName()` text if it is consumed by an existing assertion;
it is diagnostic only. Imports are the existing connector's vendor/JDK imports.
No extra worker or polling process is introduced.

- [x] **Step 3: Centralize environment normalization and failure cleanup.**

Move the `PtyProcessBuilder` chain from `TerminalSession.start` into `PtyChild`'s
factory. Keep `setUnixOpenTtyToPreserveOutputAfterTermination(true)`.

```java
static Map<String,String> environment(Map<String,String> source) {
    Map<String,String> copy = new HashMap<>(source);
    copy.put("TERM", "xterm-256color");
    copy.put("COLORTERM", "truecolor");
    return copy;
}

static TerminalSession finish(TtyConnector connector, Supplier<TerminalSession> make) {
    try {
        TerminalSession session = make.get();
        session.startReading();
        return session;
    } catch (RuntimeException | Error failure) {
        try { connector.close(); }
        catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
        throw failure;
    }
}
```

The new `start(options)` constructs the child/connector then calls `finish` with
`new TerminalSession(connector, grid.columns(), grid.rows(), scrollback)`.
Retain the old start overload temporarily as a typed-options forwarding method.
At the final package split the session factory calls the vendor-free engine
constructor and `finish(PtyChild, Supplier<TerminalSession>)`, keeping identical
cleanup behavior. `JediTermEngine` retains a package-private `TtyConnector`
constructor for emulator tests; no public vendor constructor is introduced.

Add a pure environment test asserting forced values and unchanged source map,
and a suppressed-cleanup-error test. No real PTY is needed for these tests.

- [x] **Step 4: Run process/session tests and full check; document and commit.**

`./gradlew :jasper-terminal:test --tests '*Pty*' --tests '*TerminalSessionTest'`.
Then `./gradlew check`, recording any unchanged baseline failures separately.
Verify no child/thread remains after the existing forced-close regression.

## Task 4: Establish a vendor-free row, style, cursor, and mouse boundary

**Files:**
- Create: `MAIN/TerminalRow.java`, `MAIN/CellAttributes.java`, `MAIN/CursorRequest.java`, `MAIN/MouseInput.java`, `MAIN/JediCellReader.java`.
- Modify: `ScreenSnapshot`, `RunBuilder`, `CellStyle`, `Palette`, `CursorStyle`, `RowText`, `SelectionText`, `LogicalLine`, `WordBoundaries`, `CommandCapture`, `TerminalSearch`, `TerminalSession`, `TerminalView` under MAIN.
- Tests: corresponding text/render/input tests; new `TEST/TerminalRowTest.java`.

**Interfaces:**
- `CellAttributes(int foreground,int background,int flags,String link)`; color `-1` means default, `0..255` indexed, `0x01000000 | rgb` truecolor.
- Flags: `BOLD=1`, `ITALIC=2`, `UNDERLINE=4`, `INVERSE=8`, `DIM=16`, `HIDDEN=32`.
- `CursorRequest(CursorStyle style, boolean blink)`; null request means configured behavior.
- `MouseInput(Type type, Button button, boolean shift, boolean alt, boolean control, int wheelDirection)`; nested enums reproduce current event/button names.
- `TerminalRow.capture(int width,String text,boolean wrapped,BiConsumer<char[],CellAttributes[]> fill)`, `getText()`, `isWrapped()`, `length()`, `readCells(...)`, `attributesAt(int)`.

- [x] **Step 1: Protect detached-row ownership and run existing Unicode fixtures.**

```java
@Test void aCapturedRowRemainsIndependentOfItsProducer() {
    char[] source = {'a', 'b'};
    TerminalRow row = TerminalRow.capture(2, "ab", false, (chars, styles) -> {
        System.arraycopy(source,0,chars,0,2);
        Arrays.fill(styles, CellAttributes.DEFAULT);
    });
    source[0] = 'z';
    char[] read = new char[2];
    row.readCells(read, null);
    assertThat(read).containsExactly('a','b');
    read[0] = 'q';
    row.readCells(read, null);
    assertThat(read).containsExactly('a','b');
}
```

Run `./gradlew :jasper-terminal:test --tests '*Row*' --tests '*Selection*' --tests '*RunBuilderTest' --tests '*TerminalPainterTest'`.
The new type test is RED; existing fixtures establish current behavior.
In this task the emulator adapter is still TerminalSession plus JediCellReader:
move snapshot/mouse conversion helpers there first. Task 5 moves snapshot queries
into BufferQueries and Task 6 moves protocol input into JediTermEngine. Do not
reference a not-yet-created engine from the runnable Task 4 checkpoint.

- [x] **Step 2: Implement the concrete immutable row boundary.**

```java
public final class TerminalRow {
    public static final char CONTINUATION = '\uE000';
    private final String text;
    private final boolean wrapped;
    private final char[] cells;
    private final CellAttributes[] styles;
    private TerminalRow(String text, boolean wrapped, char[] cells, CellAttributes[] styles) {
        this.text = text; this.wrapped = wrapped; this.cells = cells; this.styles = styles;
    }
    public static TerminalRow capture(int width, String text, boolean wrapped,
            BiConsumer<char[],CellAttributes[]> fill) {
        char[] cells = new char[width];
        CellAttributes[] styles = new CellAttributes[width];
        Arrays.fill(cells, ' ');
        Arrays.fill(styles, CellAttributes.DEFAULT);
        fill.accept(cells, styles);
        return new TerminalRow(text, wrapped, cells, styles);
    }
    public String getText() { return text; }
    public boolean isWrapped() { return wrapped; }
    public int length() { return cells.length; }
    public CellAttributes attributesAt(int column) { return styles[column]; }
    public void readCells(char[] target, CellAttributes[] targetStyles) {
        readCells(target.length, target, targetStyles);
    }
    public void readCells(int width, char[] target, CellAttributes[] targetStyles) {
        if (width < 0 || target.length < width
                || (targetStyles != null && targetStyles.length < width))
            throw new IllegalArgumentException("cell target too small");
        Arrays.fill(target,0,width,' ');
        int count = Math.min(width,cells.length);
        System.arraycopy(cells,0,target,0,count);
        if (targetStyles != null) {
            Arrays.fill(targetStyles,0,width,CellAttributes.DEFAULT);
            System.arraycopy(styles,0,targetStyles,0,count);
        }
    }
}
```

Use `java.util.Arrays` and `java.util.function.BiConsumer`. Document that capture
is internal, synchronous, and the filler must not retain either writable array.
The only production filler is `JediCellReader`, called with the buffer locked.
No arrays escape through row accessors.

The complete new style/cursor/mouse value declarations are:

```java
public record CellAttributes(int foreground, int background, int flags, String link) {
    public static final int BOLD = 1, ITALIC = 2, UNDERLINE = 4, INVERSE = 8, DIM = 16, HIDDEN = 32;
    public static final CellAttributes DEFAULT = new CellAttributes(-1,-1,0,null);
    public boolean has(int flag) { return (flags & flag) != 0; }
}

public record CursorRequest(CursorStyle style, boolean blink) {
    public static CursorStyle effective(CursorRequest requested, CursorStyle configured) {
        return requested == null ? configured : requested.style();
    }
    public static boolean effectiveBlink(CursorRequest requested, boolean configured) {
        return requested == null ? configured : requested.blink();
    }
}

public record MouseInput(Type type, Button button, boolean shift, boolean alt,
                         boolean control, int wheelDirection) {
    public enum Type { PRESSED, RELEASED, DRAGGED, MOVED, WHEEL }
    public enum Button { LEFT, MIDDLE, RIGHT, NONE }
}
```

Store each public type in its own file. `MouseRouting.Button` is removed and all
its consumers use `MouseInput.Button`; there are not two competing button enums.
Mouse gestures retain three booleans for the press modifiers. The adapter alone
converts those booleans to vendor modifier flags. CursorRequest conversion is a
six-case switch over vendor CursorShape, preserving null for configured behavior.

Move palette vendor conversion to `JediCellReader.color(TerminalColor)`; its complete encoding is:

```java
private static int color(TerminalColor value) {
    if (value == null) return -1;
    if (value.isIndexed()) return value.getColorIndex();
    var rgb = value.toColor();
    return 0x01000000 | (rgb.getRed() << 16) | (rgb.getGreen() << 8) | rgb.getBlue();
}
```

Move the old `readCells` iteration into `JediCellReader.capture(TerminalLine,int)`
(package-private). Convert NUL to space, preserve DWC/surrogates, obtain flags
from the six corresponding `TextStyle.Option` values, and preserve URI only for
`HyperlinkStyle` with `UriLink`. Hyperlinks force underline as before. Cache
`TextStyle -> CellAttributes` per reader under the buffer lock; clear after
4,096 entries, matching the former bound. `TerminalRow.capture` receives
`line.getText()`, `line.isWrapped()`, and the synchronous cell-fill lambda.

- [x] **Step 3: Migrate algorithms using this exact substitution map.**

| Existing code | Replacement |
| --- | --- |
| Text algorithm `TerminalLine` parameters/local variables | `TerminalRow` |
| `CharUtils.DWC` outside adapter | `TerminalRow.CONTINUATION` |
| `RunBuilder.readCells(line,width,chars,styles)` | `line.readCells(width,chars,styles)` |
| Text-only `TextStyle[]` allocation | Remove; pass null to `readCells` |
| `RunBuilder` style array/cache key | `CellAttributes[]` / `CellAttributes` |
| `CellStyle.resolve(TextStyle,Palette)` | `resolve(CellAttributes,Palette)` using encoded colors/flags |
| `ScreenSnapshot` lines/cursor fields | `List<TerminalRow>` / `CursorRequest` |
| `ScreenSnapshot.capture` | Move to adapter; return the pure record |
| `CursorStyle.effective` / `effectiveBlink` | Move to `CursorRequest` static methods accepting `CursorRequest` |
| View vendor mouse type/button/modifier use | `MouseInput` values; translate inside engine |
| Session OSC8 inspection | `row.attributesAt(column).link()` plus the existing allowed-scheme check |

Color resolution in `CellStyle` is exactly: default uses the palette's foreground
or background; tagged RGB uses `new Color(encoded & 0x00ffffff)`; otherwise use
`palette.indexed(encoded)`. Apply inverse, dim, hidden, bold, italic and underline
in the existing order. Preserve the style cache and palette replacement behavior.
Move `jediEvent`'s conversion body from view to the emulator adapter; preserve
its reversed X11 wheel naming. Expose no vendor enum through Jasper input values.

- [x] **Step 4: Compare allocation/capture cost before accepting this boundary.**

Run the Task 1 fixtures and all terminal tests. Capture direct from the live row
once; avoid a vendor `.copy()` followed by arrays. Reuse `RunBuilder` scratch
arrays across rows/frames, growing to required width, because rows already own
detached cells. Use width-limited loops so a smaller later grid never reads stale
scratch cells. Keep the existing `build(char[],styles,width)` test entry point.
If measured results show a repeatable regression outside baseline variability,
stop this checkpoint and revise the representation before continuing; record
what changed and why. Do not quietly drop the comparison.

- [x] **Step 5: Review and commit the boundary independently of later package moves.**

Retain tests for default/indexed/RGB colors, inversions/dimming/hidden text,
hyperlinks, CJK/emoji, clipping, copy, soft wraps, and malformed surrogates.

## Task 5: Extract buffer queries and shell command state

**Files:**
- Create under MAIN: `BufferQueries`, `AbsoluteRowState`, `ShellCommandTracker`, `CommandLocation`, `CompletedCommand`, `MouseGeometry`, `SelectedCells`.
- Modify: `TerminalSession`, `ScreenSnapshot`, `SessionDisplay`.
- Tests: existing shell/scrollback/search suites; new `ShellCommandTrackerTest` and `AbsoluteRowStateTest`.

**Interfaces:**
- `AbsoluteRowState`: `long discarded()`, `void discard(int)`, `long epoch()`, `void invalidate()`, `List<Long> prompts()`, `boolean recordPrompt(long)`, `void clearPrompts()`.
- `BufferQueries`: copied snapshot/geometry, cursor eligibility, text, selections, search, links, word/line selection, prompt/cursor location and command capture, using the same public-internal signatures listed for TerminalAccess in Task 6.
- `CommandLocation(long row,int column)` and `CompletedCommand(String command,OptionalInt status,Optional<Path> directory,Duration duration)`.
- Tracker constructor: `(LongSupplier clock, Supplier<CommandLocation> cursor, Function<CommandLocation,String> capture, BooleanSupplier recordPrompt, Consumer<Path> cwdChanged, Consumer<String> commandStarted, Consumer<CompletedCommand> commandFinished, Runnable integrationDetected, Runnable cursorReset)`.
- Tracker methods: `accept(List<String>)`, `discardUnusedPayload()`, `Optional<Path> workingDirectory()`, `boolean detected()`.

- [x] **Step 1: Pin repeated-prompt and monotonic-start behavior without a PTY.**

```java
@Test void repeatedPromptDoesNotFinishACommandBeforeItsStatus() {
    AtomicLong clock = new AtomicLong(0);
    List<CompletedCommand> done = new ArrayList<>();
    AtomicBoolean firstPrompt = new AtomicBoolean(true);
    ShellCommandTracker tracker = new ShellCommandTracker(clock::get,
        () -> new CommandLocation(0,0), at -> "echo test",
        () -> firstPrompt.getAndSet(false), path -> {}, text -> {}, done::add,
        () -> {}, () -> {});
    tracker.accept(List.of("jasper","mark","A"));
    tracker.accept(List.of("jasper","mark","B"));
    tracker.accept(List.of("jasper","mark","C"));
    tracker.accept(List.of("jasper","mark","A"));
    assertThat(done).isEmpty();
    clock.set(25);
    tracker.accept(List.of("jasper","mark","D","7"));
    assertThat(done).hasSize(1);
    assertThat(done.getFirst().status()).hasValue(7);
    assertThat(done.getFirst().duration()).isEqualTo(Duration.ofNanos(25));
}
```

Run this RED before extraction. Existing shell tests remain end-to-end checks.

- [x] **Step 2: Move query methods with their locks into BufferQueries.**

Constructor is package-private `(TerminalTextBuffer buffer, JediTerminal terminal,
SessionDisplay display, AbsoluteRowState rows, JediCellReader cells)`; these types
are all in the final emulation package. Expose only Jasper/JDK argument/results.
Use the exact baseline method bodies after Task 4's row conversion:

```text
snapshot()/snapshot(long), mouseGeometry(long), blinkingCursorInView(long,boolean),
lineText(long), text(Selection), selectedLiveCells(Selection), selectionUnchanged(List),
selectedText(Selection,List), selectionUnchangedLocked(List), search(String,boolean,boolean),
linkAt(long,int), openableScheme(String), urlAcrossWrappedRows(long,int),
wordSelection(long,int), lineSelection(long), absoluteRow(int), lineAtLocked(long)
```

`lineAtLocked` returns the new captured row; use cell-only read helpers for the
selection-overwrite loop to avoid capturing styles it does not use. Preserve
validation and extraction under one lock. `MouseGeometry` and `SelectedCells`
move out of session as records with their existing components. Queries do not
hold a `TerminalSession` reference.

Move the snapshot capture body into this class, with `cells.capture(...)` in
place of vendor `.copy()`. `ScreenSnapshot` becomes only the record, constants,
and `lineText` accessor. Search captures once under lock and matches afterward.
`CommandLocation cursor()` reads both coordinates under one lock;
`captureCommand(CommandLocation)` computes the old end-row rule and calls
`CommandCapture.text` under that same lock. `recordPrompt()` captures the
absolute cursor row under the lock and delegates to `AbsoluteRowState`.

- [x] **Step 3: Move row identity and shell state to their owners.**

`AbsoluteRowState` owns the existing atomic epoch, volatile discard count and
copy-on-write prompt list. `discard(n)` increments the count and prunes earlier
prompts. `invalidate()` increments the epoch only; callers explicitly preserve
whether the old transition also cleared prompts. `recordPrompt(row)` rejects
only a duplicate last row. These mutations occur under the buffer lock except
atomic reads and immutable prompt snapshots.

Move all shell fields and methods from `TerminalSession` as follows:

| Source member | Tracker adaptation |
| --- | --- |
| `workingDirectory`, `shellIntegrationDetected`, command capture/pending/clock fields | Owned only by tracker; volatile publication for metadata as before |
| `onCustomCommand` | Rename `accept`; replace display/listener calls with constructor callbacks |
| `markCommandStart` | Save `cursor.get()` as one CommandLocation |
| `captureCommand` | Consume pending payload; otherwise call capture callback; timestamp and publish started after capture callback returns |
| `recordPrompt` | Invoke injected BooleanSupplier |
| `flushPendingCommand` | Publish CompletedCommand through consumer |
| `decodeCommand`, `exitStatus`, `directoryFromUri`, MAX_COMMAND_BYTES | Move unchanged; directory helper test moves with owner |

Callbacks must return before publishing command-start, preserving its
outside-buffer-lock guarantee. Do not move the entire tracker invocation under
the buffer lock. History/alternate changes call `discardUnusedPayload()` to
preserve the current invalidation semantics; do not add a command-finish event
when a session closes mid-command.

- [x] **Step 4: Run shell, row, search and full tests; document/commit.**

`./gradlew :jasper-terminal:test --tests '*Shell*' --tests '*Scrollback*' --tests '*Search*' --tests '*Selection*'`.
Check existing duplicate-A, absent-B, malformed/overlong payload, command-duration,
CWD, clear/reflow and alternate-screen tests before the full check.

## Task 6: Introduce the engine facade, internal bridge, and listener contract

**Files:**
- Create: `MAIN/JediTermEngine.java`, `MAIN/TerminalAccess.java`, `MAIN/TerminalSessionListener.java`.
- Modify: `TerminalSession`, `PtySessionFactory`, `TerminalView`, all session listener consumers.
- Test: `TerminalSessionTest`, `SessionInputTest`, app terminal integration tests.

**Interfaces:**
- `TerminalSession` owns one `TerminalAccess`; exposes supported lifecycle/metadata operations and `internalAccess()` returning the bridge, explicitly unsupported and forbidden to app callers.
- `TerminalAccess` is public only for intra-module collaboration. It owns engine/query/tracker composition and is not an SDK capability.
- Engine public construction uses `PtyChild`; its test-only-in-use connector constructor remains package-private in emulation.
- Session listener is the existing nested Listener moved to a top-level type, with corrected callback/thread documentation.

- [ ] **Step 1: Pin consumer behavior and listener threading before moving it.**

Keep `TerminalSessionTest`, `SessionInputTest`, and `TerminalAppIntegrationTest` as
characterization tests. Add this direct callback test beside the engine fixture:

```java
@Test void clearHistoryNotificationRunsOnTheCallingThread() throws Exception {
    AtomicReference<Thread> delivered = new AtomicReference<>();
    session.addListener(new TerminalSessionListener() {
        @Override public void scrollbackReset() { delivered.set(Thread.currentThread()); }
    });
    connector.feed("one\r\ntwo\r\nthree\r\nfour\r\nfive");
    Await.until(() -> session.internalAccess().snapshot().historyLines() > 0, "history exists");
    Thread caller = Thread.currentThread();
    session.clearScrollback();
    assertThat(delivered.get()).isSameAs(caller);
}
```

This characterizes caller-thread history delivery; the command-start test must
also verify it can arrange a second thread's buffer query without deadlock while
the listener is running, using bounded latches rather than sleeps.

The final construction boundary uses an immutable callback bundle nested inside
the engine package, so the engine never imports its facade/bridge:

```java
public record Events(Runnable screenChanged, Consumer<String> titleChanged,
        Runnable bell, Runnable scrollbackReset, Consumer<Boolean> alternateBufferChanged,
        Consumer<Path> workingDirectoryChanged, Consumer<String> commandStarted,
        Consumer<CompletedCommand> commandFinished) { }
```

This is `JediTermEngine.Events`, an internal concrete record of callbacks, not an
SDK event bus. The facade constructs it from its listener list before publishing
the session. Final constructor signatures are:

```java
// Package-private session constructor, shared with session test sources.
TerminalSession(Function<JediTermEngine.Events,TerminalAccess> create)
// Public internal production bridge constructor.
TerminalAccess(PtyChild child,int columns,int rows,int scrollback,JediTermEngine.Events events)
// Public internal composition constructor; also used by test-source adapters.
TerminalAccess(JediTermEngine engine,JediTermEngine.Events events,LongSupplier clock)
// Public internal engine constructor; vendor adapter is built privately.
JediTermEngine(PtyChild child,int columns,int rows,int scrollback,Events events)
// Package-private engine constructor for in-memory emulator tests.
JediTermEngine(TtyConnector connector,int columns,int rows,int scrollback,Events events)
```

The first bridge constructor delegates to the second with a new engine and
`System::nanoTime`. The second creates the tracker from `engine.queries()`
callbacks, then installs its shell hooks before the factory calls startReading. The engine
provides `setShellHooks(Consumer<List<String>> customCommands,Runnable discardUnusedPayload)`,
permitted only before reader startup; defaults are no-op during vendor
constructor/reset initialization. Constructor-time callbacks must not dereference
an uninitialized tracker. The factory starts reading only after construction has
returned successfully. The composition constructor does not start a thread.

- [ ] **Step 2: Extract the remaining emulator implementation, not query/shell code.**

Move the remaining constructor initialization, reader loop, exit marker, input,
resize, clear, key encoding, mouse report and bracketed paste bodies to
`JediTermEngine`. Its collaborators are the connector, buffer, display, row state,
cell reader and `BufferQueries`. Retain the locked reset override, hyperlink
filter, environment-independent construction, and future copy semantics.
Inject callback functions for display events and custom shell commands; do not
import session listeners into the engine. Construct callbacks before starting
the reader; do not publish the partially initialized session.

The supported session delegates are exactly:

```java
public void write(byte[] bytes) { access.write(bytes); }
public void write(String text) { access.write(text); }
public void resize(int columns, int rows) { access.resize(columns, rows); }
public void clearScrollback() { access.clearScrollback(); }
public int columns() { return access.columns(); }
public int rows() { return access.rows(); }
public String title() { return access.title(); }
public Optional<Path> workingDirectory() { return access.workingDirectory(); }
public Optional<String> foregroundJob() { return access.foregroundJob(); }
public boolean shellIntegrationDetected() { return access.shellIntegrationDetected(); }
public CompletableFuture<Integer> exitFuture() { return access.exitFuture(); }
public void addListener(TerminalSessionListener listener) { listeners.add(listener); }
public void removeListener(TerminalSessionListener listener) { listeners.remove(listener); }
@Override public void close() { access.close(); }
/** Internal module bridge; unsupported for application or plugin use. */
public TerminalAccess internalAccess() { return access; }
```

Keep listeners in the facade; adapt them once when constructing access. The
bridge may use a constructor with explicit callback parameters (Runnable for
screen/bell/reset, Consumer for title/cwd/alternate/command events); do not add a
new event interface with one implementation. The facade maps CompletedCommand
to the existing four-argument commandExecuted callback.

- [ ] **Step 3: Freeze the complete bridge allowlist and migrate view calls.**

Besides the supported lifecycle delegates above, the internal bridge exposes:

```java
void startReading(); // construction factory/test fixture only; once per engine
ScreenSnapshot snapshot();
ScreenSnapshot snapshot(long topRow);
MouseGeometry mouseGeometry(long topRow);
boolean blinkingCursorInView(long topRow, boolean configuredBlink);
byte[] codeForKey(int keyCode, int modifiers);
long absoluteRowEpoch();
List<Long> promptRows();
String lineText(long row);
String text(Selection selection);
List<SelectedCells> selectedLiveCells(Selection selection);
boolean selectionUnchanged(List<SelectedCells> cells);
Optional<String> selectedText(Selection selection, List<SelectedCells> cells);
List<TerminalSearch.Match> search(SearchQuery query);
Optional<String> linkAt(long row, int column);
Selection wordSelection(long row, int column);
Selection lineSelection(long row);
boolean mouseReporting();
boolean usingAlternateBuffer();
boolean reportMouse(int column, int row, MouseInput event);
void paste(String text);
```

All these are forwarding methods to engine/query/tracker owners; none duplicates
state. Keep a package-private session startReading delegate for its factory;
the bridge startReading forwards to the engine and rejects a second start.
There is no `display()`, `buffer()`, `terminal()`, generic service lookup,
or arbitrary lock callback on the bridge. Engine-internal state remains private.
The view stores `session.internalAccess()` once; controllers receive the bridge
or smaller callbacks as listed below. App code receives only TerminalSession.

Migrate `TerminalSession.Listener` references to `TerminalSessionListener` across
both modules, tests, and benchmark code. Forward legacy flat constructors only
until Task 11 migrates fixtures and consumers; no compatibility facade survives
acceptance.

- [ ] **Step 4: Run session/input/app integration suites, document callback contracts, commit.**

`./gradlew :jasper-terminal:test :jasper-app:test --tests '*Terminal*'` is not used
because `--tests` applies ambiguously across tasks; run module-specific commands
separately, then `./gradlew check`. Record any baseline-only failures explicitly.

## Task 7: Give search its own worker, result state, and publication contract

**Files:**
- Create: `MAIN/SearchController.java`, `TEST/SearchControllerTest.java`.
- Modify: `TerminalView`, `TerminalAppIntegrationTest`, `TerminalSearchPaintingTest`.

**Interfaces:**
- Constructor `(Function<SearchQuery,List<TerminalSearch.Match>> search, LongConsumer reveal, Runnable repaint)`.
- `FindResult find(SearchQuery)`, `void findAsync(SearchQuery,Consumer<FindResult>)`, `FindResult next()`, `previous()`, `void clear()`, `void cancelPending()`, `List<TerminalSearch.Match> matches()`, `int currentIndex()`.
- All result state belongs to EDT; async admission/generation/queue state is guarded by the existing search lock.

- [ ] **Step 1: Add a deterministic stale-completion regression.**

```java
@Test void clearRejectsACompletionAlreadyQueuedForTheEdt() throws Exception {
    AtomicInteger callbacks = new AtomicInteger();
    AtomicReference<SearchController> reference = new AtomicReference<>();
    CountDownLatch searched = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(() -> {
        SearchController controller = new SearchController(query -> {
            searched.countDown();
            return List.of(new TerminalSearch.Match(0,0,0));
        }, row -> {}, () -> {});
        reference.set(controller);
        controller.findAsync(new SearchQuery("a",false,false), result -> callbacks.incrementAndGet());
        try {
            assertThat(searched.await(5,TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) { throw new AssertionError(e); }
        controller.clear();
    });
    SwingUtilities.invokeAndWait(() -> {
        assertThat(reference.get().matches()).isEmpty();
        assertThat(callbacks).hasValue(0);
        reference.get().cancelPending();
    });
}
```

The worker may publish before or after `clear`; both must reject its generation.
No arbitrary sleep is needed. Add a second test with a blocked first query and a
queued second/third query; only the latest callback may publish and the queue
never exceeds one. Retain existing integration tests for detach and row reset.

- [ ] **Step 2: Extract the complete search state machine.**

Move the following fields and declarations from TerminalView:

```text
SEARCH_IDLE_MILLIS, matches, currentMatch, searchLock, searchGeneration,
searchExecutor, pendingSearch, find, findAsync, calculateFind, applyFind,
searchExecutor(), invalidatePendingSearch, stepMatch, revealCurrentMatch, findResult
```

Use these precise substitutions:

```text
session.search(query,regex,caseSensitive) -> search.apply(new SearchQuery(query,regex,caseSensitive))
viewport.reveal(match.row(),session.snapshot(viewport.topRow())) -> reveal.accept(match.row())
repaint() -> repaint.run()
find(String,boolean,boolean) -> find(SearchQuery query); use query.text()/regex()/caseSensitive()
findAsync(String,boolean,boolean,Consumer) -> findAsync(SearchQuery query,Consumer)
invalidatePendingSearch -> cancelPending
findNext/findPrevious -> next/previous
```

The owner fields/constructor and clear operation are:

```java
private final Function<SearchQuery,List<TerminalSearch.Match>> search;
private final LongConsumer reveal;
private final Runnable repaint;
SearchController(Function<SearchQuery,List<TerminalSearch.Match>> search,
        LongConsumer reveal, Runnable repaint) {
    this.search = Objects.requireNonNull(search);
    this.reveal = Objects.requireNonNull(reveal);
    this.repaint = Objects.requireNonNull(repaint);
}
List<TerminalSearch.Match> matches() { return matches; }
int currentIndex() { return currentMatch; }
void clear() {
    cancelPending();
    matches = List.of();
    currentMatch = -1;
    repaint.run();
}
```

Preserve the exact executor construction, idle timeout, latest-generation
publication, and invalid-regex handling from the source. In particular, no
unbounded executor or one executor per request. Result lists are immutable
snapshots before publication (`List.copyOf(found)`). View delegates its find
methods and constructs `reveal` with the existing viewport/snapshot operation.
The controller never owns the viewport or entire view. Add typed public view
entry points and temporarily forward the old string/boolean overloads to them:

```java
public FindResult find(SearchQuery query) { return search.find(query); }
public void findAsync(SearchQuery query, Consumer<FindResult> callback) {
    search.findAsync(query,callback);
}
```

At Task 11, FindBar and all repository callers construct SearchQuery and the
old overloads are removed. Next/previous/clear retain their existing API names.

- [ ] **Step 3: Rewire reset/hidden/detached behavior and painting.**

`forgetAbsoluteRows()` calls `search.clear()`. `removeNotify()` and hidden-view
rendering suppression call `search.cancelPending()`. Highlights read
`search.matches()` and `search.currentIndex()`, retaining binary search for the
first visible match. Move private-search reflection tests to the controller
package; retain real component painting/selection tests. A package-private
`queuedRequests()` returning executor queue size is acceptable only if it is
also used as a documented diagnostic; otherwise inspect the owner in the test
rather than expose this on the public view.

- [ ] **Step 4: Run search, painting and app integration regressions; document/commit.**

`./gradlew :jasper-terminal:test --tests '*Search*' --tests '*TerminalAppIntegrationTest'`.
Then the full terminal suite. Test invalid regex, empty query, next/previous wrap,
reset during work, and supplementary-character highlight width explicitly.

## Task 8: Extract selection and desktop services

**Files:**
- Create: `MAIN/SelectionController.java`, `MAIN/DesktopServices.java`.
- Modify: `TerminalView`, `TerminalBrowserDispatchTest`, `TerminalViewInteractionTest`.
- Test: new `TEST/SelectionControllerTest.java`; browser tests move beside DesktopServices later.

**Interfaces:**
- Selection constructor `(TerminalAccess terminal)`.
- `Selection range()`, `boolean hasSelection()`, `void set(Selection)`, `void clear()`, `Optional<String> selectedText()`, `void validate()`, `void start(long,int,boolean)`, `void word(long,int)`, `void line(long)`, `void extend(long,int)`, `void finish()`.
- Desktop static methods `readClipboard()`, `writeClipboard(String)`, `openBrowser(String)`; package-private `dispatchBrowserAction(Runnable)` for owner tests.

- [ ] **Step 1: Protect stale selected text using the real buffer.**

Keep `TerminalViewInteractionTest.selectedLiveOverwriteClearsBeforeCopyEvenWithoutPainting`.
Add this owner-level test
using the normal engine fixture:

```java
@Test void overwrittenLiveSelectionCannotBeCopied() throws Exception {
    connector.feed("first");
    Await.until(() -> access.snapshot().lineText(0).equals("first"), "first text");
    SelectionController selection = new SelectionController(access);
    selection.set(new Selection(0,0,0,4,false));
    connector.feed("\rother");
    Await.until(() -> access.snapshot().lineText(0).equals("other"), "overwrite");
    assertThat(selection.selectedText()).isEmpty();
    assertThat(selection.hasSelection()).isFalse();
}
```

Execute controller operations on the EDT using the existing `onEdt` fixture
wrapper, with output feeding/waits outside it. Keep an equivalent actual-view
copy assertion using injected clipboard callbacks.

- [ ] **Step 2: Move selection state and transitions as one owner.**

Move `selection`, `pendingAnchor`, `wordAnchor`, `selectedLiveCells`, and the old
`setSelection` body. The controller's basic operations are:

```java
Selection range() { return selection; }
boolean hasSelection() { return selection != null; }
void set(Selection next) {
    selection = next;
    selectedLiveCells = next == null ? List.of() : terminal.selectedLiveCells(next);
}
void clear() { set(null); pendingAnchor = null; wordAnchor = null; }
Optional<String> selectedText() {
    if (selection == null) return Optional.empty();
    Optional<String> text = terminal.selectedText(selection,selectedLiveCells);
    if (text.isEmpty()) set(null);
    return text;
}
void validate() {
    if (selection != null && !terminal.selectionUnchanged(selectedLiveCells)) set(null);
}
void start(long row,int column,boolean block) {
    set(null); wordAnchor = null; pendingAnchor = Selection.at(row,column,block);
}
void word(long row,int column) {
    wordAnchor = terminal.wordSelection(row,column); set(wordAnchor); pendingAnchor = null;
}
void line(long row) {
    wordAnchor = null; set(terminal.lineSelection(row)); pendingAnchor = null;
}
void finish() { pendingAnchor = null; wordAnchor = null; }
```

For `extend`, move the complete `EXTEND_SELECTION` switch arm from `handleMouse`,
replacing `session` with `terminal` and `setSelection` with `set`. Preserve its
before/after word comparison and anchor direction. Copy-on-select stays in mouse
orchestration: call `finish()`, then invoke the supplied copy action if enabled.
No clipboard, viewport, repaint, or Swing component is stored by selection.

- [ ] **Step 3: Move desktop operations with their bounded worker.**

Move these whole declarations from TerminalView into a final `DesktopServices`
class: `readSystemClipboard`, `writeSystemClipboard`, `openInBrowser`,
`dispatchBrowserAction`, `BrowserWorker`, and a class-local logger. Rename the
first three to the interface names above. Keep every catch/log message and
executor bound; no URI is interpolated into diagnostics.

View retains its injectable `Supplier<String> clipboardReader`,
`Consumer<String> clipboardWriter`, and `Consumer<String> linkOpener`, initialized
with DesktopServices method references. Retain package-private setters for the
headless component tests. Replace browser-test reflection on TerminalView with
direct owner-package calls to `DesktopServices.dispatchBrowserAction`.

- [ ] **Step 4: Run selection, view interaction and desktop worker tests; commit.**

`./gradlew :jasper-terminal:test --tests '*Selection*' --tests '*Interaction*' --tests '*Browser*'`.
Verify the existing blocked-worker test still proves EDT responsiveness, queue
capacity eight, rejection rather than caller-runs, fixed diagnostics and recovery.

## Task 9: Extract keyboard and mouse routing and introduce reusable actions

**Files:**
- Create: `MAIN/KeyboardController.java`, `MAIN/MouseController.java`, `MAIN/TerminalAction.java`.
- Modify: `TerminalView`, `KeyEncoder`, `KeyInput`, `MouseRouting`.
- Tests: `TerminalViewTest`, `TerminalViewInteractionTest`, `SessionInputTest`, `MouseReportingEfficiencyTest`, new `TerminalActionTest`.
- App adaptation later: `jasper-app/src/main/java/dev/jasper/app/TerminalPane.java` and its existing action wiring.

**Interfaces:**
- Keyboard constructor `(TerminalAccess terminal, KeyEncoder keys, Predicate<KeyEvent> shortcut, BooleanSupplier exited, Runnable closeRequest, Runnable inputAccepted)`.
- Keyboard methods: `handle(KeyEvent)`, `setEncoder(KeyEncoder)`, `focusLost()`.
- Mouse constructor `(TerminalAccess terminal, SelectionController selection, Viewport viewport, boolean macOs, IntSupplier cellWidth, IntSupplier cellHeight, BooleanSupplier copyOnSelect, Runnable requestFocus, Runnable copySelection, Consumer<String> openLink, Consumer<MouseEvent> contextMenu, Runnable repaint)`.
- Mouse methods: `handle(MouseEvent)`, `focusLost()`.
- `TerminalView.execute(TerminalAction)` is EDT-only, synchronous, returns void.

- [ ] **Step 1: Protect both actual routing and command invocation.**

Keep existing right-button/Shift/mode-change gesture tests and
`reportsReadNoScreenLinesAndRequestNoRepaint`. Add:

```java
@Test void pasteActionUsesTheSameBracketedPastePathAsDirectPaste() throws Exception {
    connector.feed("\033[?2004hREADY");
    Await.until(() -> access.snapshot().lineText(0).equals("READY"), "paste mode enabled");
    SwingUtilities.invokeAndWait(() -> {
        view.setClipboard(() -> "a\nb", text -> {});
        view.execute(TerminalAction.PASTE_CLIPBOARD);
    });
    assertThat(connector.written()).isEqualTo("\033[200~a\rb\033[201~");
}

@Test void actionDispatchRejectsOffEdtCalls() {
    assertThatThrownBy(() -> view.execute(TerminalAction.COPY_SELECTION))
        .isInstanceOf(IllegalStateException.class);
}
```

Run new action tests RED before the enum/dispatch code. Existing input tests
remain green characterizations before extraction.

- [ ] **Step 2: Move keyboard state and exact dispatch logic.**

Move `suppressNextTyped`, `leftAltHeld`, `rightAltHeld`, `keys`, `handleKey`,
`trackAltKeys`, and `isModifierOnly`. Substitute:

```text
session -> terminal
handleShortcut(e) -> shortcut.test(e)
exited -> exited.getAsBoolean()
onCloseRequest.run() -> closeRequest.run()
viewport.follow(); setSelection(null); restartBlink(); -> inputAccepted.run()
```

`setEncoder` replaces `keys`; `focusLost` clears only the two Alt flags, preserving
current suppression semantics. The view's inputAccepted callback follows output,
clears selection and restarts blink. The view retains shortcut policy and
`processKeyEvent` delegation; the controller handles key events and byte encoding.
App shortcut/context setters remain view methods, and callbacks capture those
current fields rather than their initial values.

- [ ] **Step 3: Move mouse state and routing without changing press ownership.**

Move `WHEEL_LINES`, gestures map, `Gesture`, gestureSequence, wheelRemainder,
`handleMouse`, `gestureButton`, `notches`, `typeOf`, `buttonOf`, mouse modifier
conversion and `sendArrows`. All event types are now MouseInput/Jasper values.
`jediEvent` was already moved into the engine at Task 4.

Apply these substitutions to the existing method bodies:

| View operation | Mouse owner operation |
| --- | --- |
| `fonts.cellWidth()/cellHeight()` | `cellWidth.getAsInt()/cellHeight.getAsInt()` |
| `session` | `terminal` |
| `requestFocusInWindow()` | `requestFocus.run()` |
| selection switch arms | `selection.start/word/line/extend/finish` from Task 8 |
| `options.copyOnSelect()` | `copyOnSelect.getAsBoolean()` |
| `copySelection()` | `copySelection.run()` |
| `linkOpener.accept` | `openLink.accept` |
| `contextMenuHandler.accept` | `contextMenu.accept` |
| `repaint()` | `repaint.run()` |
| `scrollBy(lines)` | `viewport.scrollBy(lines,terminal.snapshot(viewport.topRow())); repaint.run()` |

`focusLost()` removes only OPEN_LINK gestures, exactly as the existing view
listener does. Each release removes its own button; preserve gestureSequence's
choice for NOBUTTON drag events. Do not fetch a snapshot before the REPORT early
return. Coordinate clamping and scrolled-history screenRow checks remain intact.

- [ ] **Step 4: Add the complete command surface and adapt standalone routing.**

```java
public enum TerminalAction {
    COPY_SELECTION, PASTE_CLIPBOARD, CLEAR_SCROLLBACK,
    FIND_NEXT, FIND_PREVIOUS, PREVIOUS_PROMPT, NEXT_PROMPT
}
```

```java
public void execute(TerminalAction action) {
    if (!SwingUtilities.isEventDispatchThread())
        throw new IllegalStateException("Terminal actions require the EDT");
    switch (Objects.requireNonNull(action,"action")) {
        case COPY_SELECTION -> copySelection();
        case PASTE_CLIPBOARD -> pasteClipboard();
        case CLEAR_SCROLLBACK -> clearScrollback();
        case FIND_NEXT -> findNext();
        case FIND_PREVIOUS -> findPrevious();
        case PREVIOUS_PROMPT -> scrollToPreviousPrompt();
        case NEXT_PROMPT -> scrollToNextPrompt();
    }
}
```

Map existing standalone copy/paste/prompt shortcuts to `execute` without changing
their modifier tests. Migrate tests that invoke these shortcuts off the EDT to
`SwingUtilities.invokeAndWait`; do not weaken the new documented action contract.
Page scrolling remains view-owned and precedes app routing as before. Direct
find-next/previous methods keep returning FindResult for the app find bar.

- [ ] **Step 5: Run keyboard, mouse, view, action and efficiency tests; document/commit.**

`./gradlew :jasper-terminal:test --tests '*Input*' --tests '*Mouse*' --tests '*TerminalView*' --tests '*TerminalActionTest'`.
Review typing after shortcuts, Enter pressed/typed suppression, AltGr,
left/right Option-as-Meta, Unicode pairs, post-exit key handling and wheel fractions.

## Task 10: Extract repaint scheduling and bells with attachment ownership

**Files:**
- Create: `MAIN/RenderScheduler.java`, `MAIN/BellController.java`.
- Modify: `TerminalView`, `TerminalRenderingLifecycleTest`, `TerminalBellTest`.
- Tests: new `RenderSchedulerTest`, `BellControllerTest` in TEST.

**Interfaces:**
- Render constructor `(Runnable reconcileRows,Runnable repaint,Runnable cancelSearch,BooleanSupplier cursorEligible)`.
- Render methods: `long attach()`, `void detach()`, `void showing(boolean)`, `void markDirty(long)`, `void restartBlink()`, `void reconcileBlink()`, `boolean blinkOn()`, `long generation()`.
- Bell constructor `(Supplier<BellMode> mode,Runnable repaint,Runnable sound)`.
- Bell methods: `void attach(long)`, `void detach()`, `void modeChanged()`, `void signal(long)`, `boolean visual()`; `setSound(Runnable)` for injected live test callback.
- Timer tick/publication methods remain package-private for owner tests, never supported API.

- [ ] **Step 1: Move the deterministic old-token regression to the scheduler owner.**

Retain the real component regression
`anOldFramePublicationCannotClaimTheReattachedViewsToken`. Replace its reflection
into the view with this owner test:

```java
@Test void anOldPublicationCannotUseANewAttachmentsToken() throws Exception {
    AtomicInteger paints = new AtomicInteger();
    AtomicReference<RenderScheduler> owner = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
        RenderScheduler scheduler = new RenderScheduler(() -> {}, paints::incrementAndGet,
            () -> {}, () -> false);
        owner.set(scheduler);
        long oldGeneration = scheduler.attach();
        scheduler.showing(true);
        AtomicBoolean oldToken = scheduler.pendingToken();
        scheduler.detach();
        scheduler.attach();
        scheduler.showing(true);
        scheduler.frameTimerFinished();
        paints.set(0);
        scheduler.publishDirty(oldGeneration,oldToken);
    });
    SwingUtilities.invokeAndWait(() -> {
        RenderScheduler scheduler = owner.get();
        assertThat(paints).hasValue(0);
        scheduler.markDirty(scheduler.generation());
    });
    SwingUtilities.invokeAndWait(() -> {
        RenderScheduler scheduler = owner.get();
        scheduler.frameTimerFinished();
        assertThat(paints.get()).isPositive();
        scheduler.detach();
    });
}
```

`pendingToken()` is package-private owner state inspection, not a view API. This
preserves the original regression: stale delivery does not repaint or claim the
new token, and a subsequent current notification can still schedule a frame.
It deliberately does not assert that an obsolete callback cannot set the shared
dirty bit; the original algorithm allows that harmless bit without scheduling.

- [ ] **Step 2: Move scheduling fields and bodies with one attachment authority.**

Move `FRAME_MILLIS=8`, `BLINK_MILLIS=530`, dirty/pendingFrame/renderingActive,
attachmentGeneration, frameTimer/blinkTimer, blinkOn, `markDirty`, `publishDirty`,
`frameTimerFinished`, `restartBlink`, `refreshRendering`, `reconcileBlink`.
The view no longer has a second attachment generation.

Adapt callbacks and constructor timer actions exactly:

```java
this.frameTimer = new Timer(8, event -> frameTimerFinished());
this.frameTimer.setRepeats(false);
this.blinkTimer = new Timer(530, event -> {
    reconcileBlink();
    if (((Timer) event.getSource()).isRunning()) {
        blinkOn = !blinkOn;
        repaint.run();
    }
});
```

`attach()` increments generation, creates a fresh frame token and marks attached;
`detach()` increments it, marks detached/inactive, replaces the token, and stops
both timers. `showing(value)` sets active to attached && value, uses the old
refreshRendering body, and invokes cancelSearch when inactive. `cursorEligible`
is the view callback containing its focus/exit/session cursor check; scheduler
adds the renderingActive condition.

Keep `markDirty`'s capture-before-validation ordering and `publishDirty`'s
existing token handling. Retain validation inside the queued EDT runnable.
Use `volatile` for generation/token/active as in the existing code. Do not add a
behavioral race fix as part of moving this already-regressed algorithm.

- [ ] **Step 3: Move bell coalescing and expiry as one owner.**

Move visualBell, pendingBell, bellTimer, bellSound and the old ring/clear methods.
Bell stores the attachment generation passed by RenderScheduler; it does not
advance that generation. `attach` creates a fresh token. `detach` clears the
token and visual state. `modeChanged` replaces the token only if attached and
clears the flash. `signal(generation)` is the old listener bell method, using
this owner's generation/token and posting the same EDT delivery checks.
Timer remains one-shot 150ms; painting still overlays 15% foreground alpha.
Changing the sound callback must affect subsequent deliveries, not retain the
old constructor callback.

The view lifecycle is ordered:

```java
@Override public void addNotify() {
    super.addNotify();
    long generation = rendering.attach();
    bells.attach(generation);
    listener = listenerFor(generation);
    session.addListener(listener);
    reconcileAbsoluteRows();
    rendering.showing(isShowing());
}
@Override public void removeNotify() {
    rendering.detach();
    bells.detach();
    if (listener != null) {
        session.removeListener(listener);
        listener = null;
    }
    search.cancelPending();
    super.removeNotify();
}
```

Construct controllers before installing `session.exitFuture().thenAccept(...)`:
an already-completed future may run its continuation synchronously during view
construction. Its continuation updates the existing volatile exit flag and calls
`rendering.markDirty(rendering.generation())`. Keep those initialization and
cross-thread publication guarantees.

View listener's frame/bell methods only delegate with captured generation.
Reset/alternate callbacks post to EDT, verify the same render generation, and
then reconcile absolute rows. The view keeps one observed row epoch and one
`forgetAbsoluteRows` orchestration path (selection.clear, search.clear,
viewport.follow, repaint, find-result notification).

- [ ] **Step 4: Run lifecycle, bell, live-option and painting suites; document/commit.**

`./gradlew :jasper-terminal:test --tests '*Lifecycle*' --tests '*Bell*' --tests '*LiveOptions*' --tests '*Painting*'`.
Recheck hidden output, focused/unfocused cursor, pending-wrap cursor, exit,
detach/reattach, bell mode switches, coalescing, and cancellation of stale sound.

## Task 11: Move packages, migrate callers, and enforce boundaries

**Files:**
- Move all MAIN/TEST files according to the final map above.
- Add `package-info.java` in each final production package.
- Modify affected imports/types under `jasper-app/src/main/java`, `src/test/java`, and benchmark classes.
- Modify `build.gradle.kts` and `jasper-terminal/build.gradle.kts` for architecture/docs checks.
- Create `jasper-terminal/src/test/java/dev/jasper/terminal/architecture/TerminalArchitectureTest.java`.

**Interfaces:** Final packages and supported allowlist from the spec. Remove old
flat forwarding classes and deprecated constructor/start overloads after all
repository callers have migrated. `internalAccess()` remains a deliberately
unsupported bridge member, despite being on a supported type; checks prohibit
app bytecode from calling it.

- [ ] **Step 1: Add a failing architecture check before relocation.**

Create architecture tests that assert no production Java source remains directly
in `dev/jasper/terminal`, no app dependency on `.internal.`, no vendor imports
outside emulation/connector adaptation, and no app call to `internalAccess`.
The initial flat-package assertion is genuinely RED.

Use `jdeps` for the package graph rather than inferring dependencies from import
statements. It sees fully qualified references and bytecode signatures. Add a
root verification task using the JBR toolchain's `jdeps` and `javap`:

```kotlin
val terminalProject = project(":jasper-terminal")
val appProject = project(":jasper-app")
val verifyTerminalArchitecture by tasks.registering {
    dependsOn(":jasper-terminal:classes", ":jasper-app:classes")
    doLast {
        val toolchains = terminalProject.extensions.getByType<JavaToolchainService>()
        val launcher = toolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get()
        val bin = launcher.metadata.installationPath.dir("bin").asFile
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        fun tool(name: String, args: List<String>): String {
            val process = ProcessBuilder(listOf(bin.resolve(name + suffix).absolutePath) + args)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }
            return output
        }
        val terminalClasses = terminalProject.layout.buildDirectory.dir("classes/java/main").get().asFile
        val appClasses = appProject.layout.buildDirectory.dir("classes/java/main").get().asFile
        val runtime = terminalProject.configurations.getByName("runtimeClasspath").asPath
        val output = tool("jdeps", listOf("--ignore-missing-deps", "-verbose:class", "-filter:none",
            "--class-path", runtime, terminalClasses.absolutePath))
        val edgePattern = Regex("^\\s*(dev\\.jasper\\.terminal\\.\\S+)\\s+->\\s+(\\S+).*$")
        val graph = mutableMapOf<String, MutableSet<String>>()
        output.lineSequence().forEach { line ->
            val match = edgePattern.matchEntire(line) ?: return@forEach
            val fromClass = match.groupValues[1]
            val toClass = match.groupValues[2]
            val from = fromClass.substringBeforeLast('.')
            check(!toClass.startsWith("dev.jasper.app.")) { line }
            if (toClass.startsWith("com.jediterm."))
                check(from == "dev.jasper.terminal.internal.emulation") { line }
            if (toClass.startsWith("dev.jasper.terminal.")) {
                val to = toClass.substringBeforeLast('.')
                if (from != to) graph.getOrPut(from) { mutableSetOf() }.add(to)
            }
        }
        fun visit(node: String, stack: MutableSet<String>, done: MutableSet<String>) {
            if (node in done) return
            check(stack.add(node)) { "Terminal package cycle: $stack -> $node" }
            graph[node].orEmpty().forEach { visit(it, stack, done) }
            stack.remove(node); done.add(node)
        }
        val done = mutableSetOf<String>()
        graph.keys.forEach { visit(it, linkedSetOf(), done) }
        val appTypes = appClasses.walkTopDown().filter { it.extension == "class" }.map {
            it.relativeTo(appClasses).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.')
        }.toList()
        for (type in appTypes) {
            val bytecode = tool("javap", listOf("-classpath", appClasses.absolutePath, "-c", "-p", type))
            check(!bytecode.contains("dev/jasper/terminal/internal/")) { "$type uses terminal internals" }
            check(!bytecode.contains("TerminalSession.internalAccess")) { "$type calls internalAccess" }
        }
    }
}
```

Wire the root task after all subprojects have applied their plugins:

```kotlin
gradle.projectsEvaluated {
    appProject.tasks.named("check") { dependsOn(verifyTerminalArchitecture) }
}
```

Use JDK `java.nio.file` APIs or Gradle file trees in source-level tests; do not
require an external Python installation at build time. Root task can be wired
from the app's `check` (which already needs terminal classes); do not make
terminal unit tests depend on app compilation. Batch javap calls if tool startup
is material, but preserve one-class attribution in failure messages.

- [ ] **Step 2: Relocate cohesive groups and fixtures.**

Move the value packages first, rendering/text next, internal process/shell/engine
next, and session/view last. Use IDE/compiler assistance or explicit imports;
never replace encapsulation with wildcard imports of every internal package.
Cross-package concrete classes needed by collaborators become public internal
classes, but vendor-facing constructors/methods remain package-private.

Test placement:

| Test family | Final package |
| --- | --- |
| Value, palette and grid tests | config |
| Font tests | rendering |
| Text/search/selection algorithms | internal.text |
| Run/painter tests | internal.rendering |
| Emulator/session protocol tests | internal.emulation |
| Process and foreground metadata | internal.process |
| Shell filter/tracker | internal.shell |
| View/controllers/input/lifecycle | view |
| Browser dispatch | internal.desktop |
| Supported API embedding tests | session or examples |

Move FakeConnector into `internal.emulation` test sources; its vendor methods
never become production API. Add a **test-source-only** public `TestSession`
fixture in the session test package to wrap a supplied internal TerminalAccess
through the package-private session constructor. Add an emulation test-source
factory with this contract, so view tests need no vendor imports or reflection
into private engine fields:

```java
// Test source in session: accessible to tests, never packaged in the main jar.
public static TerminalSession create(Function<JediTermEngine.Events,TerminalAccess> make) {
    return new TerminalSession(make);
}
// EmulationFixture owns FakeConnector, creates the engine using its package-private
// constructor and uses TestSession.create(events -> new TerminalAccess(engine,events,clock)).
// Public test-fixture accessors:
TerminalSession session();
TerminalAccess access();
void feed(String text) throws IOException;
void finish() throws IOException;
String written();
GridSize lastResize();
void close();
```

`EmulationFixture.open(int columns,int rows,int scrollback,LongSupplier clock)`
constructs the connector, builds engine/access inside that factory lambda, then
starts reading once all owners exist; it implements AutoCloseable by closing the
session. Its convenience open overload uses System::nanoTime. Construction
failure closes the fake connector. All mutation-capable fixture methods are
confined to test sources. Keep
Await and TestFonts in a test-support package and make their required members
public within test artifacts only. Do not export test fixtures from the main jar.

- [ ] **Step 3: Migrate all app and benchmark consumers and the action adapter.**

Find the complete inventory with:

```bash
rg -n 'dev\.jasper\.terminal|TerminalSession\.start|TerminalSession\.Listener' jasper-app
```

At minimum: TerminalPane, ShellLauncher, JasperApplication, FindBar, BuiltinTheme,
ResolvedTheme, TerminalConfig, ConfigSnapshot, ConfigLoader, InitialWindowSize,
WindowContent, WindowStatusBar, BenchmarkRun and BenchmarkRendering, plus tests.
Replace old `start(command,environment,directory,columns,rows,scrollback)` with
`start(SessionLaunchOptions.builder()...)` using the already-captured launch
settings. Do not change when settings are captured or resolve them in the factory.

In TerminalPane's existing actions, dispatch copy/paste/clear/prompt commands to
`view.execute(...)`. Keep parameterized operations and FindResult-returning paths
direct. No second app action catalog. Replace repeated TerminalOptions
construction used for one-value changes with `toBuilder` where behavior matches;
app config construction may retain the canonical constructor.

- [ ] **Step 4: Enforce public signature and supported-type rules.**

Use reflection in `TerminalArchitectureTest` over compiled terminal classes:
for each public class, inspect constructors, declared public/protected methods,
fields, supertype/interfaces, and recursively inspect generic ParameterizedType,
GenericArrayType, WildcardType, TypeVariable bounds. Fail on a
`com.jediterm` type anywhere. Package-private vendor adapters are intentionally
excluded as classes but must not appear in a public type's hierarchy/signature.
Use a visited set for recursive generic bounds.

App `jdeps` output must reference only the approved supported classes (including
their nested builders), not merely any non-internal package. Maintain the
allowlist explicitly in this task:

```text
session.TerminalSession
session.SessionLaunchOptions
session.TerminalSessionListener
view.TerminalView
view.TerminalAction
config.TerminalOptions
config.Palette
config.CursorStyle
config.BellMode
config.OptionAsMeta
config.GridSize
search.SearchQuery
search.FindResult
rendering.FontSet
```

Add this code inside the architecture task:

```kotlin
val supported = setOf(
    "session.TerminalSession", "session.SessionLaunchOptions", "session.TerminalSessionListener",
    "view.TerminalView", "view.TerminalAction", "config.TerminalOptions", "config.Palette",
    "config.CursorStyle", "config.BellMode", "config.OptionAsMeta", "config.GridSize",
    "search.SearchQuery", "search.FindResult", "rendering.FontSet"
).map { "dev.jasper.terminal.$it" }.toSet()
val appOutput = tool("jdeps", listOf("--ignore-missing-deps", "-verbose:class", "-filter:none",
    "--class-path", runtime + java.io.File.pathSeparator + terminalClasses.absolutePath,
    appClasses.absolutePath))
val appEdge = Regex("^\\s*(dev\\.jasper\\.app\\.\\S+)\\s+->\\s+(dev\\.jasper\\.terminal\\.\\S+).*$")
appOutput.lineSequence().forEach { line ->
    val match = appEdge.matchEntire(line) ?: return@forEach
    val target = match.groupValues[2].substringBefore('$')
    check(target in supported) { "Unsupported terminal API: $line" }
}
```

Use this class body with the generic-signature helpers immediately below it.
Imports: `java.nio.file.Files`, `java.nio.file.Path`, JUnit `Test`, and static
AssertJ `assertThat`. Package: `dev.jasper.terminal.architecture`.

```java
class TerminalArchitectureTest {
    private static Path repo() {
        return Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    }
    @Test void noProductionTypesRemainInTheFlatPackage() throws Exception {
        Path flat = repo().resolve("jasper-terminal/src/main/java/dev/jasper/terminal");
        try (var files = Files.list(flat)) {
            assertThat(files.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("package-info.java")).toList()).isEmpty();
        }
    }
    @Test void publiclyAccessibleSignaturesDoNotExposeJediTerm() throws Exception {
        Path classes = repo().resolve("jasper-terminal/build/classes/java/main");
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = classes.relativize(file).toString().replace('\\','.').replace('/','.');
                name = name.substring(0,name.length()-6);
                if (name.endsWith("package-info") || name.equals("module-info")) continue;
                checkSignatures(Class.forName(name,false,getClass().getClassLoader()));
            }
        }
    }
}
```

Insert these complete helpers inside that class:

```java
private static void assertVendorFree(java.lang.reflect.Type type,
        java.util.Set<java.lang.reflect.Type> seen) {
    if (type == null || !seen.add(type)) return;
    if (type instanceof Class<?> c) {
        assertThat(c.getName()).doesNotStartWith("com.jediterm.");
        if (c.isArray()) assertVendorFree(c.getComponentType(),seen);
    } else if (type instanceof java.lang.reflect.ParameterizedType p) {
        assertVendorFree(p.getRawType(),seen);
        assertVendorFree(p.getOwnerType(),seen);
        for (var t : p.getActualTypeArguments()) assertVendorFree(t,seen);
    } else if (type instanceof java.lang.reflect.GenericArrayType a) {
        assertVendorFree(a.getGenericComponentType(),seen);
    } else if (type instanceof java.lang.reflect.WildcardType w) {
        for (var t : w.getUpperBounds()) assertVendorFree(t,seen);
        for (var t : w.getLowerBounds()) assertVendorFree(t,seen);
    } else if (type instanceof java.lang.reflect.TypeVariable<?> v) {
        for (var t : v.getBounds()) assertVendorFree(t,seen);
    }
}
private static boolean exportedMember(int modifiers) {
    return java.lang.reflect.Modifier.isPublic(modifiers)
        || java.lang.reflect.Modifier.isProtected(modifiers);
}
private static void checkSignatures(Class<?> type) {
    for (Class<?> owner = type; owner != null; owner = owner.getEnclosingClass())
        if (!java.lang.reflect.Modifier.isPublic(owner.getModifiers())) return;
    var seen = new java.util.HashSet<java.lang.reflect.Type>();
    assertVendorFree(type.getGenericSuperclass(),seen);
    for (var t : type.getGenericInterfaces()) assertVendorFree(t,seen);
    for (var t : type.getTypeParameters()) assertVendorFree(t,seen);
    for (var m : type.getDeclaredMethods()) if (exportedMember(m.getModifiers())) {
        assertVendorFree(m.getGenericReturnType(),seen);
        for (var t : m.getGenericParameterTypes()) assertVendorFree(t,seen);
        for (var t : m.getGenericExceptionTypes()) assertVendorFree(t,seen);
        for (var t : m.getTypeParameters()) assertVendorFree(t,seen);
    }
    for (var c : type.getDeclaredConstructors()) if (exportedMember(c.getModifiers())) {
        for (var t : c.getGenericParameterTypes()) assertVendorFree(t,seen);
        for (var t : c.getGenericExceptionTypes()) assertVendorFree(t,seen);
        for (var t : c.getTypeParameters()) assertVendorFree(t,seen);
    }
    for (var f : type.getDeclaredFields()) if (exportedMember(f.getModifiers()))
        assertVendorFree(f.getGenericType(),seen);
}
```

Invoke checkSignatures on each class below terminal's `build/classes/java/main`,
using its path relative to that directory (replace separators with dots and
remove `.class`) with `Class.forName(name,false,getClass().getClassLoader())`.
Do not initialize vendor/native classes merely to inspect their signatures.
No unlisted bridge type is added to the allowlist just to make a failure pass.

- [ ] **Step 5: Run architecture checks, both modules and benchmark compilation; commit.**

`./gradlew verifyTerminalArchitecture check`. Benchmark source lives in the app
main sources, so compiling it does not launch benchmark windows. Inspect source
hygiene and all test XML. Remove temporary flat-package forwarding code and
obsolete vendor imports only after all callers compile.

## Task 12: Deliver onboarding, maintenance recipes, and acceptance evidence

**Files:**
- Create: `jasper-terminal/README.md`, `docs/terminal-architecture.md`, `docs/terminal-maintenance.md`.
- Update: root README, all package-info/JavaDoc, `docs/terminal-refactor-verification.md`, STATUS, this plan and spec banners.
- Create: `jasper-terminal/src/test/java/dev/jasper/terminal/examples/TerminalExamplesTest.java`.
- Modify: terminal Gradle verification wiring for JavaDoc and documentation checks.

**Interfaces:** A new developer can start at the module README, follow a compiled
example, then locate the owner/test for an action, live option, or shell event.
No historical plan reading is required for normal maintenance.

- [ ] **Step 1: Add build-checked usage examples.**

Use this complete example test for the builder/query/action catalog. The launch
example is compiled as a method but not invoked by the headless test runner.

```java
package dev.jasper.terminal.examples;

import dev.jasper.terminal.config.*;
import dev.jasper.terminal.session.*;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.view.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalExamplesTest {
    @Test void configureATerminalWithoutStartingAProcess() {
        TerminalOptions options = TerminalOptions.defaults().toBuilder()
            .fontSize(16f).copyOnSelect(true).bell(BellMode.NONE).build();
        SessionLaunchOptions launch = SessionLaunchOptions.builder()
            .command(List.of("example-shell", "-l"))
            .environment(Map.of("LANG", "C.UTF-8"))
            .workingDirectory(Path.of("."))
            .grid(new GridSize(80,24)).scrollback(options.scrollback()).build();
        assertThat(options.fontSize()).isEqualTo(16f);
        assertThat(launch.grid()).isEqualTo(new GridSize(80,24));
        assertThat(new SearchQuery("hello",false,false).text()).isEqualTo("hello");
    }

    // Compiled documentation example. The caller runs this off the EDT and owns
    // the returned session. In a real app, add the component to its pane on EDT.
    static TerminalSession startForEmbedding(SessionLaunchOptions launch,
            TerminalOptions options, java.util.function.Consumer<TerminalView> attach)
            throws IOException {
        if (SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Start processes off the EDT");
        TerminalSession session = TerminalSession.start(launch);
        SwingUtilities.invokeLater(() -> {
            try {
                TerminalView view = new TerminalView(session, options);
                attach.accept(view);
            } catch (RuntimeException | Error failure) {
                session.close();
                throw failure;
            }
        });
        return session;
    }
}
```

Document owner cancellation between launch and attachment: app code must close
an unneeded session rather than attach to a closed pane. This example does not
invent a new launch manager; Jasper's existing ShellLauncher already owns that
application concern. Session close must retain its existing bounded/asynchronous
process cleanup rather than blocking the EDT on a process wait.

- [ ] **Step 2: Write the module README with this concrete reading path.**

```markdown
# Jasper terminal

A Swing terminal component backed by JediTerm and a local PTY. Applications own
windows, configuration files, shortcuts and session placement. This library owns
terminal emulation, rendering, input, selection, search and shell events.

## Start here

1. Install/select JetBrains Runtime SDK 25. Use the repository Gradle wrapper;
   its toolchain requires the JetBrains vendor.
2. Run `./gradlew :jasper-terminal:test` from the repository root.
3. Read [the architecture](../docs/terminal-architecture.md), then inspect
   TerminalSession and TerminalView in their session/view packages.
4. Read the compiled examples in `src/test/java/dev/jasper/terminal/examples`.
5. Choose a recipe in [maintenance](../docs/terminal-maintenance.md).

`./gradlew check` verifies both modules; `./gradlew verifyTerminalArchitecture`
checks package/API boundaries. Tests are headless. GUI and native benchmark
checks are user-run and described in the root guides.

## Ownership

Start a session off the Event Dispatch Thread. Construct and use its Swing view
on the EDT. Removing a view stops presentation work; closing the session ends
the process. The application owns both decisions. Do not treat a session exit
as a shell command finishing: shell events describe commands inside the session.

Use TerminalOptions and SessionLaunchOptions builders for immutable settings.
Live view options do not restart the process; shell/environment/scrollback are
new-session settings. Use typed view methods or TerminalAction for operations.

## Supported API

The supported packages contain documented session/view entry points, config
values, search query/results, and FontSet for sizing. `internal` classes and
TerminalSession.internalAccess() are module implementation details, even when
Java visibility is public. They are not plugin extension points.
```

Add the final package table, compiled configuration/embedding code linked to its
source, exit/listener example, and teardown example. Link all public entry points
to their source or generated JavaDoc. Do not copy stale historical package paths.

- [ ] **Step 3: Write architecture and maintenance guides using final owners.**

Architecture must contain the final dependency graph; input/output/search/event
sequence diagrams; the spec's ownership/thread/failure tables; examples of
absolute rows before/after output, discard and reset; and live/new-session option
semantics. Explain the single-copy row representation actually accepted by Task 4
and measured limitations. Distinguish publicly visible internal bridges from
supported APIs and future SDK capabilities.

Maintenance recipes have these exact routes:

| Change | Implementation route | Verification route |
| --- | --- | --- |
| Add a terminal action | TerminalAction → TerminalView.execute → existing controller; app action adapter separately | TerminalActionTest plus real view behavior |
| Change key encoding | view.KeyEncoder for bytes; KeyboardController for event lifecycle | SessionInputTest and TerminalViewTest |
| Add a live option | config.TerminalOptions + builder → TerminalView.applyOptions → owning controller; app parser/template separately | TerminalOptionsTest, TerminalLiveOptionsTest, app config tests |
| Add a shell event | ShellIntegrationFilter → ShellCommandTracker → facade listener mapping | filter split-chunk tests, tracker unit test, ShellIntegrationSessionTest |
| Change rendering | JediCellReader if representation changes; RunBuilder/TerminalPainter/FontSet otherwise | text/style/Unicode tests and Task 1 comparison fixtures |
| Extend search | SearchQuery, internal.text.TerminalSearch, SearchController | algorithm tests, stale-worker and real highlight tests |
| Change mouse routing | MouseRouting → MouseController → MouseInput → engine translation | gesture tests and MouseReportingEfficiencyTest |
| Change process metadata | internal.process.ForegroundJobResolver | platform-aware resolver/process tests and title integration |

Every recipe gives an ordered edit/test sequence, concrete current source links,
a small working example using actual APIs, and invariants not to break. For
example: a new live option requires a builder round-trip test, existing-session
application test, and unrelated-reload retention test; changing TerminalOptions
alone is insufficient. A shell event must preserve emulator order and must not
block while the buffer lock is held. Feature recipes explain where future SDK
work attaches without claiming an SDK exists now.

- [ ] **Step 4: Complete JavaDoc/package contracts and make documentation checks executable.**

For each package write a package-info describing its single responsibility,
allowed dependencies, internal/supported status, thread owner, and principal
classes. Document every production type, every supported public/protected
member and record component, and non-obvious internal methods/fields. Internal
public bridge methods explicitly say unsupported. Remove old milestone comments
such as `selection arrives in plan 2`; explain the actual division of ownership.

Configure terminal JavaDoc with this build code:

```kotlin
tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
    }
}
tasks.named("check") { dependsOn(tasks.named("javadoc")) }
tasks.withType<Test>().configureEach {
    systemProperty("jasper.repoRoot", rootProject.projectDir.absolutePath)
}
```

This enables doclint and UTF-8, makes `check` depend on JavaDoc, and fails on
documentation errors. The existing compiler warning policy
remains intact. Add a test that checks Markdown relative links in the three new
guides and package-info source links using `Path.resolve(...).normalize()`; ignore
external URLs and validate fragments against generated heading anchors where
used. Keep Markdown embedding examples sourced from the compiled example file,
or compare marked code blocks with that source so examples cannot drift silently.

- [ ] **Step 5: Perform human onboarding acceptance and whole-branch verification.**

A fresh reviewer follows the README without conversation history and identifies
(a) the owner/test for a key change, (b) the event path for a shell mark, (c) all
places a live option needs edits, and (d) session/view shutdown and callback
thread contracts. Record their findings and close documentation gaps.

Run:

```bash
./gradlew verifyTerminalArchitecture check --rerun-tasks
git diff --check
```

Record per-module XML counts and expected skips. Repeat the Task 1 headless
comparisons against the same runtime/fixtures; report numbers and variability.
Run the repository source-hygiene script. Complete a whole-branch code review,
resolve findings, and run focused regressions plus the full check after fixes.
Do not claim native visual/throughput/RSS acceptance from headless evidence.
The verification report keeps those user-run checks pending with exact commands.

- [ ] **Step 6: Commit the completed documentation/evidence and hand off integration.**

Update STATUS and spec/plan banners with completion, deviations, tests and pending
native checks. Report the branch and commit. Do not merge or push without the
user's authorization; do not re-request authorization already supplied later in
this session. Preserve unrelated changes and user-owned artifacts throughout.

## Self-review and spec coverage

| Spec requirement | Tasks |
| --- | --- |
| Human maintainability and scope | All tasks; onboarding acceptance in 12 |
| Supported packages/API and caller migration | 2, 6, 9, 11 |
| Process, session, shell ownership | 3, 5, 6 |
| Buffer, text, rendering boundary | 4, 5, 11 |
| View controller ownership | 7–10 |
| Builders/factory/commands/adapters/observer | 2–6, 9 |
| Threading, lifecycle, failure contracts | 3, 5–10 |
| Future SDK boundary without runtime | 6, 11, 12 |
| Onboarding, JavaDoc, recipes and compiled examples | Every task, completed in 12 |
| Migration, regression/resource evidence and reviews | 1, each checkpoint, 12 |

Self-review completed on 2026-09-20: all twelve spec sections map to tasks;
Review Focus cases map to regression steps; constructor and callback ownership
were reconciled across session, bridge, engine and test fixtures; Task 4's
intermediate adapter location is explicit; the stale-frame regression preserves
the original dirty-bit semantics. Markdown fences/local links and the two Python
plan blocks were checked (Python parsed only, not executed). Java and Kotlin
excerpts have not been compiled as implementation. No production changes or
implementation scripts were run.

Task 4's row representation refinement is explicitly pending plan approval and
measurement. Baseline title-test diagnosis is a prerequisite, not a proposed
unverified fix. Recheck signatures against the execution checkout before editing;
any mismatch is recorded and resolved before proceeding, not guessed around.
