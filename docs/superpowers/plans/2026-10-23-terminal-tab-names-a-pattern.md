# Terminal tab names A: the pattern, the setting and its live preview — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Not started.** Deviations from the spec, decided while planning:
- The preview's sample `{process}`, used while no Terminal tab is open, is `zsh` on macOS, `powershell` on Windows and
  `bash` elsewhere, not "the configured shell's label": Settings cannot learn the label without launching a shell.
- "The most recently focused Terminal tab" for the preview is the active window's shown tab when it is a terminal, else
  that window's first terminal tab (the terminal registry tracks the active window, not focus history).

**Goal:** Terminal tabs are named by a `terminal.tab_name` pattern such as `{dir} {process} {title}`, which Settings
edits with a live preview.

**Architecture:** A pure `config.TitleTemplate` parses `{name}` pieces and fills them with `WindowTitle.format`'s
separator rule; `WindowTitle` and `config.TabNames` (the tab-name rules) both use it. Each `TerminalPane` reports its
built-in pieces; `TerminalContent.title()` fills its window's pattern from them unless the tab was renamed.
`WorkspaceConfiguration` re-presents every terminal tab when the saved pattern changes. Settings' Tab name row is a
custom `ValueEditor` whose preview reads the active terminal tab's pieces through `TabEntry.namePieces`.

**Tech Stack:** Java 25 (JBR), Swing, jasper-ui kit nodes, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-23-jasper-terminal-tab-names-design.md` (sections 3 to 5, 8 and 9). Plan B
(`2026-10-23-terminal-tab-names-b-plugin-pieces.md`) adds plugin pieces on top of this plan.

## Global Constraints

- Work in the worktree `.claude/worktrees/terminal-tab-names` on branch `claude/terminal-tab-names`. Never on `main`.
- Use `./gradlew`; tests are headless. Never launch the GUI (`:jasper-app:run`, benchmarks) or train the AOT cache.
- Every commit message ends with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Setting: key `terminal.tab_name`, label `Tab name`, default `"{dir} {process} {title}"`, 1 to 200 characters, not
  blank, applies live.
- Pieces: lowercase letters, digits and `_`, starting with a letter, at most 32 characters. Built-ins are `dir`, `full`,
  `process` and `title`. `{{` and `}}` write a brace.
- Separator rule: the text between two pieces shows only when both have a value (an empty piece goes with the text
  before it, or after it when it comes first); text before the first piece and after the last always stays.
- A name given with Rename Tab wins over the pattern. The main window's title (`WindowHeaderContext`, the look's
  `window_title`) and notification wording do not change.
- Unknown pieces are never a configuration error: they show nothing.
- Jasper is an MVP: delete code the change makes dead (`TabState.title`, `TerminalPane.tabTitle`, `TerminalTitle.tab`);
  no compatibility paths.
- Package rules: `dev.jasper.app.config` stays pure (no Swing); `settingsui` reaches only its allowed packages
  (`config`, not `workspace` or `terminals`); `verifyApplicationArchitecture` runs in `check`.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in source.
- macOS, Windows and Linux all get the feature; tests that need a PTY stay `@DisabledOnOs(OS.WINDOWS)` like their
  neighbours.

## Review Focus

- A pattern with fixed text and only empty pieces (`Tab {title}` before any title) shows `Tab`, not the shell's name
  (Task 2, `fixedTextCountsSoOnlyAnEmptyResultFallsBack`).
- The home folder itself shows `~` for both `{dir}` and `{full}`, and `/` shows `/` (Task 3, `PaneNamePiecesTest`).
- A remote pane's path ending in `/`, or the remote root, gives a sensible `{dir}` (Task 3,
  `remoteNamesUseTheLastSegment`).
- A title holding a line break or control characters stays on one line (Task 2,
  `cleanPutsAValueOnOneTrimmedLine`).
- A `config.toml` with a malformed pattern loads with one diagnostic and the default pattern (Task 2,
  `aMalformedSavedPatternFallsBackToTheDefault`).

---

### Task 1: The shared title template

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/TitleTemplate.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTitle.java` (`format`, `directory`, the three regex
  constants)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/TitleTemplateTest.java`

**Interfaces:**
- Produces: `public record TitleTemplate(List<String> literals, List<String> names)` with
  `static TitleTemplate parse(String text)` (throws `IllegalArgumentException` with a message for the user),
  `static Optional<String> problem(String text)` and `String fill(Function<String, String> values)` (a `null` or
  blank value is empty). `WindowTitle.homeRelative(Path directory, Path home)` (package-private static) for Task 3.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tab names spec 3: free text with {name} pieces, shared by tab names and a look's window title. */
class TitleTemplateTest {
    private static String fill(String template, Map<String, String> values) {
        return TitleTemplate.parse(template).fill(values::get);
    }

    @Test void piecesAndTheTextAroundThemAreSplitInOrder() {
        var template = TitleTemplate.parse("[{dir}] {process}|{title}");
        assertThat(template.names()).containsExactly("dir", "process", "title");
        assertThat(template.literals()).containsExactly("[", "] ", "|", "");
    }

    @Test void doubledBracesWriteABrace() {
        assertThat(TitleTemplate.parse("{{x}} {dir}").literals()).containsExactly("{x} ", "");
        assertThat(fill("{{{dir}}}", Map.of("dir", "moray"))).isEqualTo("{moray}");
        assertThat(fill("no pieces {{here}}", Map.of())).isEqualTo("no pieces {here}");
    }

    @Test void theTextBetweenTwoPiecesShowsOnlyWhenBothHaveAValue() {
        var values = Map.of("dir", "moray", "process", "zsh", "title", "");
        assertThat(fill("{dir} {process} {title}", values)).isEqualTo("moray zsh");
        assertThat(fill("{dir} | {process} | {title}", values)).isEqualTo("moray | zsh");
        assertThat(fill("{title} - {dir}", values)).isEqualTo("moray");
        assertThat(fill("{dir} | {title} - {process}", values)).as("an empty piece goes with the text before it")
            .isEqualTo("moray - zsh");
        assertThat(fill("{dir} {host}", values)).as("a piece nobody fills is empty").isEqualTo("moray");
    }

    @Test void textAroundTheEndsStays() {
        assertThat(fill("{dir} ({host})", Map.of("dir", "moray"))).isEqualTo("moray)");
        assertThat(fill("[{title}]", Map.of())).isEqualTo("[]");
        assertThat(fill("Tab {title}", Map.of())).isEqualTo("Tab");
        assertThat(fill("{title} at work", Map.of())).isEqualTo("at work");
    }

    @Test void valuesAreUsedAsGiven() {
        assertThat(fill("{a}|{b}", Map.of("a", " x ", "b", "y"))).isEqualTo(" x |y");
        assertThat(fill("{a}|{b}", Map.of("a", "  ", "b", "y"))).as("blank is empty").isEqualTo("y");
    }

    @Test void malformedTemplatesSayWhatIsWrong() {
        assertThat(TitleTemplate.problem("{dir} {process}")).isEmpty();
        assertThat(TitleTemplate.problem("{dir")).hasValueSatisfying(message -> assertThat(message).contains("has no }"));
        assertThat(TitleTemplate.problem("dir}")).hasValueSatisfying(message -> assertThat(message).contains("has no {"));
        for (String bad : List.of("{}", "{Dir}", "{1st}", "{a-b}", "{a b}", "{" + "a".repeat(33) + "}"))
            assertThat(TitleTemplate.problem(bad)).as(bad).hasValueSatisfying(message -> assertThat(message).contains("not a piece name"));
        assertThat(TitleTemplate.problem("{" + "a".repeat(32) + "}")).isEmpty();
        assertThatThrownBy(() -> TitleTemplate.parse("{dir")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*TitleTemplateTest'`
Expected: FAIL to compile: `cannot find symbol class TitleTemplate`.

- [ ] **Step 3: Implement `TitleTemplate`**

```java
package dev.jasper.app.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A title template: free text with {@code {name}} pieces, as {@code terminal.tab_name} and a look's
 * {@code [chrome].window_title} use (tab names spec 3); {@code {{} and {@code }}} write a brace. The text between two
 * pieces shows only when both have a value: an empty piece goes with the text before it, or with the text after it
 * when it comes first. When every piece is empty, the leading and trailing text stay without the separators next to
 * the pieces. A template without pieces is its text.
 *
 * @param literals the text before the first piece, between pieces and after the last: one more than {@code names}
 * @param names the pieces' names, in order
 */
public record TitleTemplate(List<String> literals, List<String> names) {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    /** Spaces and the separators leading text may end with. */
    private static final Pattern TRAILING = Pattern.compile("[\\s—–·:|-]+$");
    /** Spaces and the separators trailing text may start with. */
    private static final Pattern LEADING = Pattern.compile("^[\\s—–·:|-]+");

    public TitleTemplate {
        literals = List.copyOf(literals);
        names = List.copyOf(names);
        if (literals.size() != names.size() + 1) throw new IllegalArgumentException("A template has one more text than pieces");
    }

    /**
     * Reads a template.
     *
     * @throws IllegalArgumentException for an unmatched brace or a name that is not a piece name, with a message for the user
     */
    public static TitleTemplate parse(String text) {
        var literals = new ArrayList<String>();
        var names = new ArrayList<String>();
        var literal = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean doubled = i + 1 < text.length() && text.charAt(i + 1) == c;
            if ((c == '{' || c == '}') && doubled) { literal.append(c); i++; continue; }
            if (c == '}') throw new IllegalArgumentException("The } at character " + (i + 1) + " has no {");
            if (c != '{') { literal.append(c); continue; }
            int end = text.indexOf('}', i + 1);
            if (end < 0) throw new IllegalArgumentException("The { at character " + (i + 1) + " has no }");
            String name = text.substring(i + 1, end);
            if (!NAME.matcher(name).matches())
                throw new IllegalArgumentException("{" + name + "} is not a piece name: use up to 32 lowercase letters, digits and _, starting with a letter");
            literals.add(literal.toString());
            literal.setLength(0);
            names.add(name);
            i = end;
        }
        literals.add(literal.toString());
        return new TitleTemplate(literals, names);
    }

    /** Empty when {@code text} is a template; otherwise what is wrong with it. */
    public static Optional<String> problem(String text) {
        try {
            parse(text);
            return Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.of(invalid.getMessage());
        }
    }

    /** The template with each piece's value from {@code values}; a null or blank value is empty. */
    public String fill(Function<String, String> values) {
        if (names.isEmpty()) return literals.getFirst();
        var out = new StringBuilder(literals.getFirst());
        boolean any = false;
        for (int i = 0; i < names.size(); i++) {
            String value = values.apply(names.get(i));
            if (value == null || value.isBlank()) continue;
            if (any) out.append(literals.get(i));
            out.append(value);
            any = true;
        }
        if (!any) {
            String head = TRAILING.matcher(literals.getFirst()).replaceAll("");
            String tail = head.isBlank() ? LEADING.matcher(literals.getLast()).replaceAll("") : literals.getLast();
            return (head + tail).strip();
        }
        return out.append(literals.getLast()).toString();
    }
}
```

- [ ] **Step 4: Make `WindowTitle.format` use it**

In `WindowTitle.java`, delete `TOKEN`, `TRAILING` and `LEADING` and the `Matcher`/`Pattern`/`ArrayList` imports they
leave unused. Keep the class Javadoc. Replace `format` and extract `homeRelative` from `directory`:

```java
    /** {@code format} with its tokens filled in; {@code {context}} is used as it is, the others stripped. */
    static String format(TitleFormat format, String context, String session, String directory) {
        var values = Map.of("context", Objects.requireNonNull(context), "session", Objects.requireNonNull(session).strip(),
            "directory", Objects.requireNonNull(directory).strip());
        return TitleTemplate.parse(format.template()).fill(values::get);
    }
```

```java
    static String directory(PaneSnapshot pane, Path home) {
        if (pane == null) return "";
        if (pane.providerId().isPresent() || pane.remoteDirectory().isPresent())
            return pane.remoteDirectory().map(location -> clean(location.path())).orElse("");
        return homeRelative(pane.workingDirectory().orElse(home), home);
    }

    /** A local directory as titles write it: the home folder as {@code ~}, and {@code /} after it. */
    static String homeRelative(Path directory, Path home) {
        Path absolute = directory.toAbsolutePath().normalize();
        Path absoluteHome = home.toAbsolutePath().normalize();
        String path = absolute.equals(absoluteHome) ? "~" : absolute.startsWith(absoluteHome)
            ? "~/" + absoluteHome.relativize(absolute).toString().replace('\\', '/') : absolute.toString();
        return clean(path);
    }
```

Add `import dev.jasper.app.config.TitleTemplate;`. `KitParser` keeps validating `window_title` its own way, so a
look's template never holds `{{` or an unknown token.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*TitleTemplateTest' --tests '*WindowTitleTest' --tests '*KitParserTest'`
Expected: PASS (the `WindowTitleTest` cases are unchanged).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config/TitleTemplate.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowTitle.java jasper-app/src/test/java/dev/jasper/app/config/TitleTemplateTest.java
git commit -m "Tab names: a title template shared with the window title

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: The tab-name rules and the `terminal.tab_name` setting

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/TabNames.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (new setting beside
  `TERMINAL_SHELL_INTEGRATION`; `SCHEMA` gets it after `TERMINAL_SHELL_INTEGRATION`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/TerminalConfig.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java` (`from`, the `TerminalConfig` it builds)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java` (Terminal page)
- Modify: `config.example.toml`, `docs/configuration.md` (settings table)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/TabNamesTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java`

**Interfaces:**
- Consumes: `TitleTemplate` (Task 1).
- Produces: `public final class TabNames` with `DEFAULT = "{dir} {process} {title}"`, `DEFAULT_PATTERN`,
  `BUILT_IN = List.of("dir", "full", "process", "title")`, `MAX_LENGTH = 200`, `static boolean valid(String)`,
  `static String format(TitleTemplate pattern, Map<String, String> pieces, String fallback)`,
  `static String clean(String value)`, `static List<String> unknown(TitleTemplate pattern, Set<String> known)`.
  `CoreSettings.TERMINAL_TAB_NAME` (`Setting<String>`). `TerminalConfig.tabName()` and
  `TerminalConfig withTabName(String value)`.

- [ ] **Step 1: Write the failing tests**

`TabNamesTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tab names spec 3: how a pattern becomes a tab's name. */
class TabNamesTest {
    private static String name(String pattern, Map<String, String> pieces, String fallback) {
        return TabNames.format(TitleTemplate.parse(pattern), pieces, fallback);
    }

    @Test void theDefaultIsFolderProcessAndTitle() {
        assertThat(TabNames.DEFAULT).isEqualTo("{dir} {process} {title}");
        var claude = Map.of("dir", "moray", "full", "~/projects/moray", "process", "claude", "title", "Fix tab names");
        assertThat(TabNames.format(TabNames.DEFAULT_PATTERN, claude, "zsh")).isEqualTo("moray claude Fix tab names");
        assertThat(TabNames.format(TabNames.DEFAULT_PATTERN, Map.of("dir", "moray", "process", "zsh", "title", ""), "zsh"))
            .as("no trailing space without a title").isEqualTo("moray zsh");
    }

    @Test void fixedTextCountsSoOnlyAnEmptyResultFallsBack() {
        assertThat(name("Tab {title}", Map.of(), "zsh")).isEqualTo("Tab");
        assertThat(name("{title}", Map.of(), "zsh")).isEqualTo("zsh");
        assertThat(name("  {title}  ", Map.of(), "zsh")).isEqualTo("zsh");
        assertThat(name("{title}", Map.of(), " ")).isEqualTo("Terminal");
    }

    @Test void cleanPutsAValueOnOneTrimmedLine() {
        assertThat(TabNames.clean(" Fix\ntab\tnames\007 ")).isEqualTo("Fix tab names");
        assertThat(TabNames.clean(null)).isEmpty();
    }

    @Test void unknownListsPiecesNothingProvidesOnce() {
        var pattern = TitleTemplate.parse("{dir} {host} {branch} {host} {title}");
        assertThat(TabNames.unknown(pattern, Set.of())).containsExactly("host", "branch");
        assertThat(TabNames.unknown(pattern, Set.of("host"))).containsExactly("branch");
    }

    @Test void aValidPatternIsOneTo200CharactersAndWellFormed() {
        assertThat(TabNames.valid("{dir} | {host} {{x}}")).isTrue();
        assertThat(TabNames.valid("x".repeat(200))).isTrue();
        assertThat(TabNames.valid("x".repeat(201))).isFalse();
        assertThat(TabNames.valid("")).isFalse();
        assertThat(TabNames.valid("   ")).isFalse();
        assertThat(TabNames.valid("{dir")).isFalse();
        assertThat(TabNames.valid("{Dir}")).isFalse();
    }
}
```

In `CoreSettingsTest`, change `hasSize(45)` to `hasSize(46)` and add:

```java
    @Test void theTabNameIsALivePatternDefaultingToFolderProcessAndTitle() {
        var setting = CoreSettings.TERMINAL_TAB_NAME;
        assertThat(setting.name()).isEqualTo("terminal.tab_name");
        assertThat(setting.label()).isEqualTo("Tab name");
        assertThat(setting.defaultValue()).isEqualTo("{dir} {process} {title}");
        assertThat(setting.applies()).isEqualTo(Applies.LIVE);
        assertThat(setting.kind().check("{dir} | {host}")).isEmpty();
        assertThat(setting.kind().check("{dir")).isPresent();
        assertThat(setting.kind().check(" ")).isPresent();
    }
```

In `ConfigLoaderTest`, add:

```java
    @Test void aMalformedSavedPatternFallsBackToTheDefault() {
        var good = parse("[terminal]\ntab_name = '{full} | {process}'");
        assertThat(good.diagnostics()).isEmpty();
        assertThat(good.snapshot().terminal().tabName()).isEqualTo("{full} | {process}");
        var bad = parse("[terminal]\ntab_name = '{dir'");
        assertThat(bad.diagnostics()).hasSize(1);
        assertThat(bad.snapshot().terminal().tabName()).isEqualTo("{dir} {process} {title}");
        assertThat(parse("[terminal]\ntab_name = '{dir} {host}'").diagnostics()).as("an unknown piece is not an error").isEmpty();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*TabNamesTest' --tests '*CoreSettingsTest' --tests '*ConfigLoaderTest'`
Expected: FAIL to compile: `cannot find symbol class TabNames`, `TERMINAL_TAB_NAME`, `tabName()`.

- [ ] **Step 3: Implement `TabNames`**

```java
package dev.jasper.app.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code terminal.tab_name} (tab names spec 3): the built-in pieces, the default pattern and how a Terminal tab's name
 * is made from a pattern.
 */
public final class TabNames {
    /** The default pattern: the folder's name, the running program and the title a program set. */
    public static final String DEFAULT = "{dir} {process} {title}";
    public static final TitleTemplate DEFAULT_PATTERN = TitleTemplate.parse(DEFAULT);
    /** The built-in pieces, in the order Settings lists them. */
    public static final List<String> BUILT_IN = List.of("dir", "full", "process", "title");
    public static final int MAX_LENGTH = 200;

    private TabNames() { }

    /** Whether {@code text} can be saved: 1 to 200 characters, not blank, and a well-formed template. */
    public static boolean valid(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_LENGTH && TitleTemplate.problem(text).isEmpty();
    }

    /**
     * A tab's name: {@code pattern} filled from {@code pieces} (a piece they lack is empty), trimmed; {@code fallback}
     * when that is empty, and "Terminal" when {@code fallback} is blank too.
     */
    public static String format(TitleTemplate pattern, Map<String, String> pieces, String fallback) {
        String name = pattern.fill(piece -> pieces.getOrDefault(piece, "")).strip();
        if (!name.isEmpty()) return name;
        String shell = clean(fallback);
        return shell.isEmpty() ? "Terminal" : shell;
    }

    /** A piece's value on one line: control characters, line breaks among them, become spaces; then trimmed. */
    public static String clean(String value) {
        if (value == null) return "";
        var result = new StringBuilder(value.length());
        value.codePoints().forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }

    /** The pieces {@code pattern} names that are neither built in nor in {@code known}, in order, each once. */
    public static List<String> unknown(TitleTemplate pattern, Set<String> known) {
        var names = new LinkedHashSet<String>();
        for (String name : pattern.names()) if (!BUILT_IN.contains(name) && !known.contains(name)) names.add(name);
        return List.copyOf(names);
    }
}
```

- [ ] **Step 4: Add the setting**

In `CoreSettings.java`, after `TERMINAL_SHELL_INTEGRATION`:

```java
    public static final Setting<String> TERMINAL_TAB_NAME = Setting.of("terminal.tab_name",
            SettingKind.string(TabNames::valid,
                "Use 1 to 200 characters, with pieces such as {dir}, {full}, {process} and {title}, and {{ or }} for a brace; using the default."),
            TabNames.DEFAULT)
        .label("Tab name").keywords("tab", "title", "name", "format", "pattern", "folder", "directory", "process")
        .description("Live. How Terminal tabs are named: free text with pieces in braces. {dir} is the current folder's name,\n"
            + "{full} its whole path, {process} the running program and {title} the title the shell or a program set;\n"
            + "plugins add their own, such as Remote's {host}. Text between two pieces shows only when both have a value.\n"
            + "A tab named with Rename Tab keeps its name.").build();
```

Add `TERMINAL_TAB_NAME` to `SCHEMA` right after `TERMINAL_SHELL_INTEGRATION`.

- [ ] **Step 5: Carry it in `TerminalConfig` and `ConfigSnapshot`**

`TerminalConfig` gains a last component `String tabName`. In the compact constructor add:

```java
        Objects.requireNonNull(tabName, "tabName");
        if (!TabNames.valid(tabName)) throw new IllegalArgumentException("The tab name must be a valid pattern.");
```

Add the 11-argument constructor the old canonical one becomes, and the copy method:

```java
    public TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                   CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                   boolean copyOnSelect, BellMode bell, ShellExitBehavior onExit, ShellIntegrationMode shellIntegration) {
        this(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes, copyOnSelect, bell, onExit,
            shellIntegration, TabNames.DEFAULT);
    }

    /** This configuration with another {@code terminal.tab_name} pattern. */
    public TerminalConfig withTabName(String value) {
        return new TerminalConfig(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes,
            copyOnSelect, bell, onExit, shellIntegration, value);
    }
```

In `ConfigSnapshot.from`, pass `values.get(CoreSettings.TERMINAL_TAB_NAME)` after
`values.get(CoreSettings.TERMINAL_SHELL_INTEGRATION)` in the `TerminalConfig` it builds.

- [ ] **Step 6: Place the row and document the key**

In `CorePages.specs(...)`, make it the Terminal page's first row:

```java
            SchemaPage.page("terminal", "Terminal")
                .row(TERMINAL_TAB_NAME, Editors.technical())
                .row(TERMINAL_SCROLLBACK, Editors.stepper("lines"))
```

In `config.example.toml`, between `shell_integration = "auto"` and the scheme's comment:

```toml
# Live. How Terminal tabs are named: free text with pieces in braces. {dir} is the current folder's name, {full} its
# whole path, {process} the running program and {title} the title the shell or a program set; plugins add their own,
# such as Remote's {host}. Text between two pieces shows only when both have a value; {{ and }} write a brace.
tab_name = "{dir} {process} {title}"
```

In `docs/configuration.md`'s settings table, after the `terminal.dim_inactive_panes` row:

```markdown
| `terminal.tab_name` | string | `"{dir} {process} {title}"` | 1–200 characters: free text with pieces in braces | Live | Terminal › Tab name |
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*TabNamesTest' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*' --tests '*ExpandedConfigTest'`
Expected: PASS (the Settings page tests see the new Terminal row; update any that list the Terminal page's rows).

- [ ] **Step 8: Commit**

```bash
git add config.example.toml docs/configuration.md jasper-app/src/main/java/dev/jasper/app/config jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java jasper-app/src/test/java/dev/jasper/app/config
git commit -m "Tab names: the terminal.tab_name setting

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Terminal tabs follow the pattern

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java` (add `namePieces`, `localName`,
  `remoteName`; delete `tabTitle`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTitle.java` (delete `tab`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabState.java` (delete `title`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalContent.java` (`title`, `namePieces`, `tabName`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java` (`attach`, `detach`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`tabNamePattern`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/terminals/TabEntry.java`,
  `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTerminals.java` (`entry(Tab)`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/PaneNamePiecesTest.java` (new),
  `TerminalTitleIntegrationTest.java`, `TabStateTest.java`; delete `TerminalTitleTest.java`; every other test the
  new default renames (Step 7)

**Interfaces:**
- Consumes: `TitleTemplate`, `TabNames` (Tasks 1 and 2), `WindowTitle.homeRelative` (Task 1),
  `TerminalConfig.tabName()`.
- Produces: `java.util.Map<String, String> TerminalPane.namePieces()`; `java.util.Map<String, String>
  TerminalContent.namePieces()`; `TitleTemplate WindowContent.tabNamePattern()`; `TabEntry` gains a last component
  `Supplier<Map<String, String>> namePieces` (a 6-argument constructor keeps the old shape with `Map::of`). Plan B
  merges plugin pieces into `TerminalContent.namePieces()` and reads `TabEntry.namePieces`.

- [ ] **Step 1: Write the failing tests**

`PaneNamePiecesTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Tab names spec 3: how {dir} names local and remote directories (POSIX paths). */
@DisabledOnOs(OS.WINDOWS)
class PaneNamePiecesTest {
    private static final Path HOME = Path.of("/home/me");

    @Test void localNamesAreTheLastNameWithHomeAsTilde() {
        assertThat(TerminalPane.localName(Path.of("/home/me/projects/moray"), HOME)).isEqualTo("moray");
        assertThat(TerminalPane.localName(HOME, HOME)).isEqualTo("~");
        assertThat(TerminalPane.localName(Path.of("/"), HOME)).isEqualTo("/");
        assertThat(WindowTitle.homeRelative(Path.of("/home/me/projects/moray"), HOME)).isEqualTo("~/projects/moray");
        assertThat(WindowTitle.homeRelative(HOME, HOME)).isEqualTo("~");
        assertThat(WindowTitle.homeRelative(Path.of("/srv/app"), HOME)).isEqualTo("/srv/app");
    }

    @Test void remoteNamesUseTheLastSegment() {
        assertThat(TerminalPane.remoteName("/srv/app")).isEqualTo("app");
        assertThat(TerminalPane.remoteName("/srv/app/")).isEqualTo("app");
        assertThat(TerminalPane.remoteName("/")).isEqualTo("/");
        assertThat(TerminalPane.remoteName("~")).isEqualTo("~");
        assertThat(TerminalPane.remoteName("")).isEmpty();
    }
}
```

In `TerminalTitleIntegrationTest`, the fixture's window starts its tab in `DesktopTestSupport.HOME`, so `{dir}` is `~`.
Change the automatic names the existing tests wait for, keeping each test's point:
- `tabsAndNativeTitlesFollowForegroundJobsWithoutShellIntegration`: `"bash"` becomes `"~ bash"` (both waits) and
  `"sleep"` becomes `"~ sleep"` (the wait; the native title assertion stays `"sleep"`).
- `oscTitlesUpdateTheTabWindowAndBubbleWhileACommandRuns`: the tab's `"worker"` becomes `"~ bash"` (shell integration's
  command text is no longer part of tab names; the notice still says `"worker"`), and the tab's `"Reviewing files"`
  becomes `"~ bash Reviewing files"`.
- `programTitlesWorkWithoutShellIntegrationAndManualTabNamesStillWin`: `"Editor document"` becomes
  `"~ bash Editor document"` and the last `"Another document"` becomes `"~ bash Another document"`.

Add:

```java
    @Test void aChangedPatternRenamesOpenTabsAtOnceAndARenameStillWins() throws Exception {
        Fixture f = open("while read title; do printf '\\033]2;%s\\007' \"$title\"; done");
        edt(() -> f.content.currentPane().session().write("Fix tab names\n"));
        until(() -> f.content.tabs().get(0).title().equals("~ bash Fix tab names"));
        var defaults = dev.jasper.app.config.ConfigSnapshot.defaults();
        edt(() -> {
            f.content.applyConfiguration(defaults.toBuilder().terminal(defaults.terminal().withTabName("{title} | {full}")).build(), false);
            assertThat(f.content.tabs().get(0).title()).isEqualTo("Fix tab names | ~");
            f.content.currentTerminal().rename("Pinned");
            f.content.applyConfiguration(defaults.toBuilder().terminal(defaults.terminal().withTabName("{process}")).build(), false);
            assertThat(f.content.tabs().get(0).title()).isEqualTo("Pinned");
            f.content.currentTerminal().rename("");
            assertThat(f.content.tabs().get(0).title()).isEqualTo("bash");
        });
    }
```

In `TabStateTest`, delete `automaticTitlePrefersShellTitleThenDirectoryThenFallback` and
`userRenameSurvivesFocusedPaneChangesUntilCleared` (the naming they test moves to the pattern); keep
`theNameTheUserGaveIsKeptApartFromTheAutomaticTitle`. Delete `TerminalTitleTest.java` (it tests only `TerminalTitle.tab`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*PaneNamePiecesTest' --tests '*TerminalTitleIntegrationTest'`
Expected: FAIL to compile: `cannot find symbol localName`, `remoteName`.

- [ ] **Step 3: Report the built-in pieces from the pane**

In `TerminalPane.java` add (`TabNames` from `dev.jasper.app.config`; write `java.util.Map` in full, since the file
imports `java.awt.*`):

```java
    /**
     * The built-in tab name pieces (tab names spec 3), each on one line and "" when unknown: {@code dir}, the last name
     * of the pane's directory ({@code ~} for the home folder, the root itself at a root; on another host, the reported
     * path's last name); {@code full}, the directory as the window title writes it, or the reported remote path;
     * {@code process}, the foreground program without a login shell's {@code -}, else the shell's label; {@code title},
     * the title the shell or a program set, as the tab showed it before patterns.
     */
    java.util.Map<String, String> namePieces() {
        Path home = Path.of(System.getProperty("user.home"));
        String dir, full;
        if (request != null || displayRemoteDirectory().isPresent()) {
            full = displayRemoteDirectory().map(RemoteLocation::path).orElse("");
            dir = remoteName(full);
        } else {
            Path directory = directory().toAbsolutePath().normalize();
            dir = localName(directory, home.toAbsolutePath().normalize());
            full = WindowTitle.homeRelative(directory, home);
        }
        String job = foregroundJob.startsWith("-") ? foregroundJob.substring(1) : foregroundJob;
        return java.util.Map.of("dir", TabNames.clean(dir), "full", TabNames.clean(full),
            "process", TabNames.clean(job.isBlank() ? shellLabel : job), "title", TabNames.clean(displayTitle));
    }

    /** A local directory's name for {@code {dir}}: {@code ~} for the home folder, the root itself at a root. */
    static String localName(Path directory, Path home) {
        if (directory.equals(home)) return "~";
        Path name = directory.getFileName();
        return name == null ? directory.toString() : name.toString();
    }

    /** A remote path's name for {@code {dir}}: its last segment, ignoring a trailing {@code /}; the root itself. */
    static String remoteName(String path) {
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 || trimmed.equals("/") ? trimmed : trimmed.substring(slash + 1);
    }
```

Delete `tabTitle()`. Delete `TerminalTitle.tab` (keep `singleLine` and `windowTitle`).

- [ ] **Step 4: Name the tab from its window's pattern**

`TabState`: delete `title(String, Path)` and the `Path` import; it keeps `rename` and `renamed`. Its Javadoc becomes
"The name the user gave one terminal tab, kept apart from its automatic name."

`TerminalContent`:

```java
    /** The tab name pattern of the window holding the tab (tab names spec 3); its {@code TabHost} sets it. */
    java.util.function.Supplier<TitleTemplate> tabName = () -> TabNames.DEFAULT_PATTERN;

    /** The tab's title: the name the user gave it, else its window's pattern filled from {@link #namePieces()}. */
    String title() { return state.renamed().orElseGet(() -> TabNames.format(tabName.get(), namePieces(), pane.shellLabel())); }

    /** The pieces the tab's automatic name is made from (tab names spec 3). */
    java.util.Map<String, String> namePieces() { return pane.namePieces(); }
```

Reset `tabName = () -> TabNames.DEFAULT_PATTERN;` where `TerminalContent` resets its other hooks (beside `onChanged =
() -> { }` near line 121).

`TabHost.attach`: after `terminal.configure = ready -> configure(tab, ready);` add `terminal.tabName =
owner::tabNamePattern;`. `TabHost.detach`: add `terminal.tabName = () -> TabNames.DEFAULT_PATTERN;`.

`WorkspaceConfiguration`:

```java
    private TitleTemplate tabName = TabNames.DEFAULT_PATTERN;
    /** The saved tab name pattern, parsed (tab names spec 3). */
    TitleTemplate tabName() { return tabName; }
```

and in `apply`, after the loop over `owner.terminalTabs()` that applies options:

```java
        if (previous == null || !previous.terminal().tabName().equals(next.terminal().tabName())) {
            tabName = TitleTemplate.parse(next.terminal().tabName());
            for (TerminalContent terminal : owner.terminalTabs()) terminal.onChanged.run();
        }
```

`WindowContent`: `TitleTemplate tabNamePattern() { return workspaceConfiguration.tabName(); }` (package-private).

- [ ] **Step 5: Expose the pieces on the terminal registry's tab entry**

`TabEntry` gains a last component `Supplier<Map<String, String>> namePieces` (Javadoc: "the pieces a terminal tab's
automatic name is made from; empty for a content tab"), and a constructor with the old six parameters that passes
`Map::of`. In `WindowTerminals.entry(Tab)`, the terminal branch passes `terminal::namePieces` after `() ->
owner.selectTab(tab)`; the content branch keeps the six-argument form.

- [ ] **Step 6: Run the focused tests**

Run: `./gradlew :jasper-app:test --tests '*PaneNamePiecesTest' --tests '*TerminalTitleIntegrationTest' --tests '*TabStateTest' --tests '*WindowTitleTest' --tests '*TerminalRegistryTest'`
Expected: PASS.

- [ ] **Step 7: Bring the rest of the suite up to the new default**

Run: `./gradlew :jasper-app:test` and then `./gradlew check -x :jasper-app:test` (plugins, testkit and architecture
checks). Every failure that asserts a Terminal tab's automatic name (a tab title, a Move Pane to Tab or Select Tab
entry, `TabHandle.title()`, a tooltip) is updated to what `{dir} {process} {title}` gives for that test's pane: its
folder's name (`~` for `DesktopTestSupport.HOME`), its program and any title it set. Keep each test's point; never
change product code to restore an old name. Fix any other failure at its cause. List the updated tests in the commit
message.

Expected: both runs PASS.

- [ ] **Step 8: Commit**

```bash
git add -A jasper-app plugins
git commit -m "Tab names: Terminal tabs follow the terminal.tab_name pattern

<one line per updated test: Class.method: old name -> new name>

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: The Tab name row's live preview

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/TabNameChoices.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/TabNameEditor.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java` (the `specs` and `pages` overloads that
  take `NotificationGroupChoices`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (the `CorePages.pages(...)` call
  near line 1399; a `tabNamePreview()` method)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/TabNameEditorTest.java`

**Interfaces:**
- Consumes: `TabNames`, `TitleTemplate`, `CoreSettings.TERMINAL_TAB_NAME`, `TabEntry.namePieces` (Task 3),
  `TerminalRegistry.activeWindow()`, `WindowEntry.selectedTab()`/`tabs()`, `PaneEntry.snapshot()`.
- Produces: `public record TabNameChoices(Supplier<Optional<Preview>> preview)` with `NONE` and
  `public record Preview(Map<String, String> pieces, String fallback)`; `static Editors.Factory<String>
  TabNameEditor.factory(TabNameChoices choices)`; `TabNameEditor.SAMPLE` (a `Preview`); the public overload
  `CorePages.pages(AppearanceChoices, SystemActions, ShellChoices, NotificationGroupChoices, TabNameChoices, Pages)`.
  Plan B adds plugin pieces to `TabNameChoices`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tab names spec 5: the Tab name field, its live preview, unknown pieces and the piece list. */
class TabNameEditorTest {
    private static final String ID = CoreSettings.TERMINAL_TAB_NAME.name();
    private static final TabNameChoices CLAUDE = new TabNameChoices(() -> Optional.of(new TabNameChoices.Preview(
        Map.of("dir", "moray", "full", "~/projects/moray", "process", "claude", "title", "Fix tab names"), "zsh")));

    private static ValueEditor<String> editor(TabNameChoices choices, String value) {
        var editor = TabNameEditor.factory(choices).build().apply(CoreSettings.TERMINAL_TAB_NAME, null);
        editor.show(value);
        return editor;
    }

    private static List<String> texts(ValueEditor<String> editor) {
        var found = new ArrayList<String>();
        collect(editor.node(ID, ControlState.READY), found);
        return found;
    }

    private static void collect(Nodes.Node node, List<String> found) {
        if (node instanceof Nodes.Text text) found.add(text.text());
        if (node instanceof Nodes.Stack stack) for (var child : stack.children()) collect(child, found);
    }

    @Test void thePreviewFillsThePatternFromTheTabAndFollowsEdits() {
        var editor = editor(CLAUDE, "{dir} | {process} | {title}");
        assertThat(texts(editor)).contains("Preview: moray | claude | Fix tab names");
        assertThat(editor.event(ID, new UiEvent.Edited(ID, "{full} {process}"))).isTrue();
        assertThat(editor.value()).isEqualTo("{full} {process}");
        assertThat(texts(editor)).contains("Preview: ~/projects/moray claude");
    }

    @Test void anEmptyResultPreviewsTheShellsName() {
        var tab = new TabNameChoices(() -> Optional.of(new TabNameChoices.Preview(Map.of("dir", "moray", "title", ""), "zsh")));
        assertThat(texts(editor(tab, "{title}"))).contains("Preview: zsh");
    }

    @Test void withNoTerminalTabThePreviewUsesSampleValues() {
        String shell = TabNameEditor.SAMPLE.fallback();
        assertThat(texts(editor(TabNameChoices.NONE, "{dir} {process} {title}"))).contains("Preview: projects " + shell);
    }

    @Test void anUnknownPieceIsNotedButTheValueStaysValid() {
        assertThat(texts(editor(CLAUDE, "{dir} {branch}")))
            .contains("Preview: moray", "Nothing provides {branch} right now, so it shows nothing");
        assertThat(CoreSettings.TERMINAL_TAB_NAME.kind().check("{dir} {branch}")).isEmpty();
    }

    @Test void aMalformedPatternShowsNoPreview() {
        assertThat(texts(editor(CLAUDE, "{dir"))).noneMatch(text -> text.startsWith("Preview:"));
    }

    @Test void thePieceListNamesEveryBuiltIn() {
        assertThat(String.join("\n", texts(editor(CLAUDE, "{dir}")))).contains("{dir}", "{full}", "{process}", "{title}");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*TabNameEditorTest'`
Expected: FAIL to compile: `cannot find symbol class TabNameChoices`.

- [ ] **Step 3: Implement `TabNameChoices`**

```java
package dev.jasper.app.settingsui;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the Tab name row previews (tab names spec 5), supplied by the application: the Terminal tab the preview names,
 * or empty while none is open. EDT only.
 *
 * @param preview the tab the preview uses now
 */
public record TabNameChoices(Supplier<Optional<Preview>> preview) {
    /** No Terminal tab, for pages built without an application: the preview uses sample values. */
    public static final TabNameChoices NONE = new TabNameChoices(Optional::empty);

    public TabNameChoices { Objects.requireNonNull(preview, "preview"); }

    /**
     * One tab's values.
     *
     * @param pieces each piece's value by name, without braces
     * @param fallback what a pattern that comes out empty names the tab: its shell's label
     */
    public record Preview(Map<String, String> pieces, String fallback) {
        public Preview {
            pieces = Map.copyOf(pieces);
            Objects.requireNonNull(fallback, "fallback");
        }
    }
}
```

- [ ] **Step 4: Implement `TabNameEditor`**

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.config.TabNames;
import dev.jasper.app.config.TitleTemplate;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Settings › Terminal › Tab name (tab names spec 5): the pattern's field, then a live preview of the name it gives the
 * active Terminal tab, a note for each piece nothing provides, and the pieces there are.
 */
final class TabNameEditor implements ValueEditor<String> {
    /** What the preview names while no Terminal tab is open. */
    static final TabNameChoices.Preview SAMPLE = sample(System.getProperty("os.name", ""));
    private static final Map<String, String> BUILT_IN = Map.of("dir", "the current folder's name", "full", "its full path",
        "process", "the running program", "title", "the title the shell or a program set");
    private final String label;
    private final TabNameChoices choices;
    private String text = "";

    private TabNameEditor(String label, TabNameChoices choices) { this.label = label; this.choices = choices; }

    static Editors.Factory<String> factory(TabNameChoices choices) {
        return new Editors.Factory<>((setting, context) -> new TabNameEditor(setting.label(), choices),
            List.of("Preview", "{dir}", "{full}", "{process}", "{title}"));
    }

    private static TabNameChoices.Preview sample(String os) {
        String shell = os.startsWith("Mac") ? "zsh" : os.startsWith("Windows") ? "powershell" : "bash";
        return new TabNameChoices.Preview(Map.of("dir", "projects", "full", "~/projects", "process", shell), shell);
    }

    @Override public Nodes.Node node(String id, ControlState state) {
        var children = new ArrayList<Nodes.Node>();
        children.add(new Nodes.Field(id, state, Nodes.FieldKind.TEXT, text, "", label, "", Optional.empty(),
            Nodes.FieldPresentation.TECHNICAL));
        if (TabNames.valid(text)) {
            TitleTemplate pattern = TitleTemplate.parse(text);
            var tab = choices.preview().get().orElse(SAMPLE);
            children.add(new Nodes.Text(id + ":preview", ControlState.READY,
                "Preview: " + TabNames.format(pattern, tab.pieces(), tab.fallback()), Nodes.TextRole.SUPPORTING));
            for (String name : TabNames.unknown(pattern, Set.of()))
                children.add(new Nodes.Text(id + ":unknown-" + name, ControlState.READY,
                    "Nothing provides {" + name + "} right now, so it shows nothing", Nodes.TextRole.SUPPORTING,
                    Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()));
        }
        children.add(new Nodes.Text(id + ":pieces", ControlState.READY, pieces(), Nodes.TextRole.SUPPORTING));
        return new Nodes.Stack(id + ":tab-name", ControlState.READY, Nodes.LayoutRole.VERTICAL, children);
    }

    /** One line per piece: {@code {name}} and what it shows. */
    private static String pieces() {
        var lines = new ArrayList<String>();
        for (String name : TabNames.BUILT_IN) lines.add("{" + name + "} " + BUILT_IN.get(name));
        return String.join("\n", lines);
    }

    @Override public String value() { return text; }
    @Override public void show(String value) { text = value; }
    @Override public boolean event(String id, UiEvent event) {
        if (!(event instanceof UiEvent.Edited edited) || !edited.id().equals(id)) return false;
        text = edited.text();
        return true;
    }
}
```

If `Nodes.Text` refuses a line break or `ModelChecks.id` refuses `-` in an id, emit one `Nodes.Text` per piece line
(ids `id + ":piece-" + name`) and use `_` instead; adjust the test's `String.join` accordingly.

- [ ] **Step 5: Wire it into the Terminal page and the application**

`CorePages`:
- the innermost `specs(appearance, system, shells, navigate, macOs, groups)` gains a last parameter `TabNameChoices
  tabNames`, and the Terminal page's first row becomes `.row(TERMINAL_TAB_NAME, TabNameEditor.factory(tabNames))`;
  every shorter `specs` overload passes `TabNameChoices.NONE`;
- add `public static Function<PageContext, List<SettingsPage>> pages(AppearanceChoices appearance, SystemActions system,
  ShellChoices shells, NotificationGroupChoices groups, TabNameChoices tabNames, Pages supplied)`, which the existing
  `pages(appearance, system, shells, groups, supplied)` calls with `TabNameChoices.NONE`;
- the package-private `pages(..., groups, supplied, glass)` gains `TabNameChoices tabNames` before `supplied` and passes
  it to `specs`.

`JasperApplication`: pass `new dev.jasper.app.settingsui.TabNameChoices(this::tabNamePreview)` after the
`NotificationGroupChoices` argument of `CorePages.pages(...)`, and add:

```java
    /**
     * The Terminal tab Settings' Tab name row previews (tab names spec 5): the active window's shown tab when it is a
     * terminal, else that window's first terminal tab; empty when it has none.
     */
    private Optional<dev.jasper.app.settingsui.TabNameChoices.Preview> tabNamePreview() {
        return terminals.activeWindow().flatMap(window -> window.selectedTab().get()
                .filter(tab -> tab.kind() == TabEntry.Kind.TERMINAL)
                .or(() -> window.tabs().get().stream().filter(tab -> tab.kind() == TabEntry.Kind.TERMINAL).findFirst()))
            .flatMap(tab -> tab.terminal().get().map(pane ->
                new dev.jasper.app.settingsui.TabNameChoices.Preview(tab.namePieces().get(), pane.snapshot().get().shell())));
    }
```

(`TabEntry` from `dev.jasper.app.terminals`.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests '*JasperApplication*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/settingsui jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/settingsui/TabNameEditorTest.java
git commit -m "Tab names: Settings previews the Tab name pattern live

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Documentation and the full check

**Files:**
- Modify: `docs/configuration.md` (a `### Tab names` section after `### Terminal`; the table row's link)
- Modify: `docs/terminal.md` (`## Titles`), `docs/workspace.md` (the tab naming sentence after the tab table),
  `docs/app-maintenance.md` (`### Header and title bar`), `docs/STATUS.md` (a handoff entry)

- [ ] **Step 1: Write the configuration section**

In `docs/configuration.md`, the table row's fourth cell becomes `1–200 characters: free text with pieces in braces
([Tab names](#tab-names))`. After the `### Terminal` section's last paragraph, add:

```markdown
### Tab names

`terminal.tab_name` names every Terminal tab: free text with pieces in braces, applied as you save. A tab you named
with **Rename Tab** keeps that name. The default, `{dir} {process} {title}`, shows `moray claude Fix tab names` while
Claude runs and has set a title, and `moray zsh` at the prompt.

| Piece | Shows |
| --- | --- |
| `{dir}` | The current folder's name, `~` for your home folder; on another host, the last name of the path its shell reports |
| `{full}` | The whole path, with your home folder as `~`; on another host, the path its shell reports |
| `{process}` | The running program, such as `zsh`, `vim` or `claude`; on Windows and in a plugin's session, the shell's or session's name |
| `{title}` | The title the shell or a program set (OSC 0, 1 or 2) |

Plugins add pieces of their own, such as Remote's `{host}`; a piece that nothing provides shows nothing. Text between
two pieces shows only when both have a value, so `{dir} | {process} | {title}` without a title reads `moray | zsh`.
Text before the first piece and after the last always shows. `{{` and `}}` write a brace. A pattern that comes out
empty names the tab after its shell. Settings › Terminal › Tab name previews the name the pattern gives the tab you
are using.
```

- [ ] **Step 2: Update the other guides**

`docs/terminal.md`, `## Titles`: replace the numbered list and its lead-in with:

```markdown
A tab is named after its active pane:

1. the name you gave the pane's terminal with **Rename Tab** (Cmd+I on macOS, Ctrl+Shift+I on Linux and Windows); an
   empty name returns to automatic naming;
2. otherwise the [Tab name](configuration.md#tab-names) pattern, by default `{dir} {process} {title}`: the folder's
   name, the foreground program, checked every half second on macOS and Linux (a login shell shows without its
   leading `-`; on Windows and in a plugin's session, the shell's or session's name), and the title a program set
   with OSC 0, 1 or 2, which with shell integration lasts until the next command starts or finishes.
```

In the next paragraph, replace "followed by ` : ` and the name from steps 3 to 5" with "followed by ` : ` and the
running command with shell integration, else the foreground program, else the shell's name".

`docs/workspace.md`: replace the first line below with the second (fenced so this plan's own link check skips them):

```markdown
otherwise the title a program set, the running command, the foreground program or the shell's name ([Titles](terminal.md#titles))
otherwise the [Tab name](configuration.md#tab-names) pattern filled in from that pane ([Titles](terminal.md#titles))
```

`docs/app-maintenance.md`, `### Header and title bar`: replace "An explicit OSC title replaces the complete automatic
header and tab title. Automatic tabs show only the running command or shell; renamed tabs keep their names." with "An
explicit OSC title replaces the complete automatic header. A tab's automatic name is `terminal.tab_name` filled by
`TabNames.format` from `TerminalContent.namePieces()` (`TitleTemplate`, shared with `WindowTitle`); renamed tabs keep
their names."

- [ ] **Step 3: Add the STATUS entry**

At the top of `docs/STATUS.md`'s `## Handoff log` (after "Newest first…"), add:

```markdown
**Terminal tab names, plan A — 2026-10-23** (`claude/terminal-tab-names`, worktree
`.claude/worktrees/terminal-tab-names`, from `main` at `36fdeedc1`; not merged or pushed): `terminal.tab_name`
(Settings › Terminal › Tab name, live) names Terminal tabs from a pattern of free text and `{dir}`, `{full}`,
`{process}` and `{title}`, by default `{dir} {process} {title}`; Rename Tab still wins. `config.TitleTemplate` is
shared with the look's window title. Settings previews the active terminal tab's name as you type. Spec:
`docs/superpowers/specs/2026-10-23-jasper-terminal-tab-names-design.md`; plan:
`docs/superpowers/plans/2026-10-23-terminal-tab-names-a-pattern.md` (deviations in its status banner). GUI check
pending: the Tab name row's layout and preview, and tab names on macOS, Linux and Windows.
```

- [ ] **Step 4: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (`AppDocumentationTest` resolves `#tab-names`; `ConfigTemplateTest` sees the row).

- [ ] **Step 5: Mark the plan done and commit**

Set this plan's `Status:` to `**Done 2026-10-23.**` followed by the deviations list.

```bash
git add docs
git commit -m "Docs: terminal tab names from the Tab name pattern

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
