# Jasper Command Line, Plan 2: The Default Editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Complete on 2026-10-14** (written 2026-10-14 on `claude/default-editor`, from `main` at `af590a97`, plan 1 merged). Execution deviations: (1) on Windows a custom command runs as `cmd.exe /d /s /c "<line>"`, so a quoted program path keeps its quotes (Task 7).

**Goal:** Users pick a default editor on the Editor's settings page — the Jasper Editor (the default), an editor
installed on the machine, a custom command, or the system's — and Jasper's shells get `EDITOR`/`VISUAL` for it, while
`config.toml`, the plugin settings files, the VM options file and `Platform.openInEditor` open in it.

**Architecture:** SDK 0.17.1 adds four additive hooks: `Terminals.environment` (`terminal.environment`), `Platform.editor`
with `FileEditor` (`editor.provide`), `LocalSpec.command`, and `CommandLine.client()`. The app implements them in
`dev.jasper.app.plugins` (`ShellContributions`, `FileEditors`), threads program tabs through `OpenSpec` and the
workspace to `ShellLauncher`, and routes every editor open through the plugin editor on the UI thread before the
desktop fallback. The Editor (0.9.0) gains a JDK-and-SDK-only package `dev.jasper.editor.external` (the known
editors, detection, the `EDITOR` values and launch lines, the `FileEditor`), four settings and a custom settings page.

**Tech Stack:** Java 25 (JBR), Swing through the SDK kit, Gradle, JUnit 5, AssertJ.

**Spec:** [`docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md`](../specs/2026-10-13-jasper-command-line-and-default-editor-design.md)
(sections 6.1–6.6, 8 and 9, plan 2). Plan 1 (the `jasper` command) is merged; plan 3 (Cmd-click file paths) follows.

## Global Constraints

- SDK `0.17.1`. New capabilities `terminal.environment` and `editor.provide`, appended to `Capabilities.ALL` in that
  order. The Editor becomes `0.9.0` with `sdk = ">=0.17.1, <0.18"` and capabilities `["palette.contribute",
  "terminal.environment", "editor.provide", "terminal.open"]`; the other bundled manifests keep `>=0.17.0, <0.18`.
- Settings keys (spec 6.1), in the Editor's file `plugins/dev.jasper.editor/dev.jasper.editor.toml`, section
  **Default editor**: `default_editor` (`"jasper"`), `custom_command` (`""`), `custom_in_terminal` (`true`),
  `keep_environment_editor` (`true`).
- `default_editor` values: `"jasper"`, `"system"`, `"custom"`, or an id from the known-editor table below.
- A local shell's environment: Jasper's environment, Jasper's own variables, **then contributions**, then
  `[terminal.env]`, then `TERM` and `COLORTERM`; `JASPER_PANE` and `JASPER_COMMAND_SOCKET` stay last (plan 1).
  Contributions reach local shells and program tabs, never WSL profiles or provided sessions (a program tab whose
  default profile is WSL gets none either).
- A contribution's name matches `[A-Za-z_][A-Za-z0-9_]*`; `TERM`, `COLORTERM`, `TERM_PROGRAM`,
  `TERM_PROGRAM_VERSION` and names starting `JASPER_` are reserved; a value holding NUL is dropped. Two plugins setting
  one name: the first by plugin id (sorted) wins; within one plugin, its earlier registration wins. Each dropped or
  losing entry is logged once per plugin and name.
- `FileEditor.edit` runs on the UI thread. `false`, a handler that throws, or no handler opens the file with
  `ConfigEditor`'s desktop fallback, as today. A worker waits at most 30 s for the UI thread's answer, then falls back.
- One editor at a time: a bundled plugin's registration replaces a non-bundled one's; otherwise a later registration
  throws `IllegalStateException("The editor is already provided by <plugin id>")`.
- A program tab (`LocalSpec.command`) runs the program instead of the shell with the default profile's environment; it
  closes when the program exits with status 0 and stays open otherwise, whatever `terminal.on_exit` says. A
  `LocalSpec` may name a profile or a command, not both.
- `EDITOR`/`VISUAL` values (spec 6.3): POSIX paths single-quoted (`'` written `'\''`), Windows paths double-quoted.
  Jasper Editor: `<quoted client> editor --wait`. Terminal editor: `<quoted path>` (`<quoted path> -nw` for emacs).
  App editor: `<quoted path> <wait args>`. Custom: the command as written. System: nothing.
- `keep_environment_editor` true and Jasper's own environment has a non-blank `EDITOR` or `VISUAL`: set neither.
- Page texts (exact): "Jasper's environment has EDITOR=<value>" (or `VISUAL=<value>`, or "EDITOR=<v> and
  VISUAL=<v>"), "Jasper's environment has neither EDITOR nor VISUAL", "An export in a shell startup file, such as
  ~/.zshrc, still wins.", an undetected saved editor "<Name> (not found)", an empty custom command "Enter a command;
  until then files open with your system's default app.".
- Tests never launch an editor, a GUI app or the Jasper GUI: detached starts go through an injected launcher, and
  shell tests run only `/bin/sh` on scripts in temporary folders. Never run `:jasper-app:run`, `trainAotCache`,
  `packageDist` or `-Pjasper.aot=true` (AGENTS.md).
- `dev.jasper.sdk.*` types appear in the app only inside `dev.jasper.app.plugins`. The Editor compiles against the SDK
  and the JDK only, builds UI through the kit (`verifyPluginArchitecture`'s presentation check), and starts processes
  only through `ProcessBuilder` behind its launcher seam.
- New Java files under `jasper-app`, `jasper-sdk`, `jasper-sdk-testkit` and `plugins/*/src` that
  `ThemeMigrationCoverageTest`'s pattern matches (Swing, AWT or `dev.jasper.sdk.ui` imports, collection constructions)
  get rows in `docs/design/theme-engine/migration.csv` (the `nonvisual-helper` form of the plan 1 command-line rows), or
  the test fails.
- Work in `.claude/worktrees/default-editor` on `claude/default-editor`, never on `main`. Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in `.java` sources; write
  escapes (`"\u00fc"`).

## Known editors

Checked against each editor's documentation on 2026-10-14 (spec 6.2 asked for it). `FileEditor.edit` carries a line
and no column, so only line forms appear. Detection looks for the executables on Jasper's `PATH`, then
`/opt/homebrew/bin`, `/usr/local/bin` and `~/.local/bin` (not on Windows), then the macOS app tools under
`/Applications` and `~/Applications`; on Windows each executable name is tried with `.exe`, `.cmd` and `.bat` (VS Code's
`bin` folder holds an extensionless shell script beside `code.cmd`), and
`notepadpp` also in `%ProgramFiles%\Notepad++` and `%ProgramFiles(x86)%\Notepad++`, `notepad` in
`%SystemRoot%\System32`.

| Id | Name | Kind | Executables | macOS app tool | Wait args | Open at line |
| --- | --- | --- | --- | --- | --- | --- |
| `nvim` | Neovim | terminal | `nvim` | — | — | `+LINE FILE` |
| `vim` | Vim | terminal | `vim` | — | — | `+LINE FILE` |
| `vi` | vi | terminal | `vi` | — | — | `+LINE FILE` |
| `nano` | nano | terminal | `nano` | — | — | `+LINE FILE` |
| `micro` | micro | terminal | `micro` | — | — | `+LINE FILE` |
| `helix` | Helix | terminal | `hx`, `helix` | — | — | `FILE:LINE` |
| `emacs` | Emacs | terminal (`-nw`) | `emacs` | — | — | `+LINE FILE` |
| `code` | Visual Studio Code | app | `code` | `Visual Studio Code.app/Contents/Resources/app/bin/code` | `--wait` | `-g FILE:LINE` |
| `cursor` | Cursor | app | `cursor` | `Cursor.app/Contents/Resources/app/bin/cursor` | `--wait` | `-g FILE:LINE` |
| `zed` | Zed | app | `zed` | `Zed.app/Contents/MacOS/cli` | `--wait` | `FILE:LINE` |
| `sublime` | Sublime Text | app | `subl` | `Sublime Text.app/Contents/SharedSupport/bin/subl` | `-w` | `FILE:LINE` |
| `bbedit` | BBEdit | app | `bbedit` | — | `--wait` | `+LINE FILE` |
| `textmate` | TextMate | app | `mate` | `TextMate.app/Contents/Resources/mate` | `-w` | `-l LINE FILE` |
| `idea` | IntelliJ IDEA | app | `idea` | — | `--wait` | `--line LINE FILE` |
| `notepadpp` | Notepad++ | app | `notepad++` | — | `-multiInst -nosession` | `-nLINE FILE` |
| `notepad` | Notepad | app | `notepad` | — | — | `FILE` |

Without a line, every form is just `FILE` (plus `-g` dropped for `code` and `cursor`). Terminal editors and the
`--wait` forms are what `EDITOR` holds; opening a file from Jasper (`FileEditor.edit`) runs terminal editors in a tab and
starts app editors detached **without** their wait arguments.

## Planned deviations from the spec

Recorded here and, when the plan completes, in the spec's status line and `docs/STATUS.md`:

1. **SDK 0.17.1, not a minor.** Every addition is a default method, a new record component behind the old
   constructors, or a constant, as 0.16.1 and 0.16.2 were; only the Editor raises its floor.
2. **`CommandLine.client()`** is new: the spec's Jasper Editor value needs the client's path and no API carried it. The
   app answers with the client inside the installed image, or in a development run the build's (`jasper.commandClient`).
3. **The settings file is `dev.jasper.editor.toml`** (spec 6.1 wrote `editor.toml`).
4. **The Editor's page becomes a custom `SettingsPanel`**: a generated page cannot list detected editors or show the
   environment notice. Its Font, Editing and Files rows are the host's standard rows, unchanged.
5. **`default_editor` is text with a rule**, not a choice: choices are fixed when declared and detection runs later.
   The rule accepts `jasper`, `system`, `custom` and every known id, detected or not.
6. **The known-editor table differs from spec 6.2** as the documentation check found (table above): micro, nano and
   emacs open at a line with `+LINE`; BBEdit waits with `--wait`; TextMate's tool is `Contents/Resources/mate`;
   IntelliJ IDEA is found on `PATH` only (its app binary does not wait); no row passes a column.
7. **The VM options file opens in the default editor too** (System Settings › Edit Custom VM Options…, the palette):
   spec 6.4's list predates it.
8. **Editor opens hop to the UI thread.** Every site that opens a file today runs on a worker (`ConfigEditor` refuses the
   UI thread), so the worker asks the plugin editor on the UI thread, waits up to 30 s, and runs the desktop fallback
   itself.
9. **Contribution rules** (Global Constraints): reserved and invalid names are dropped and logged; a contributor that
   throws adds nothing and counts as a plugin failure; "first by plugin id" sorts ids rather than following start order.
10. **Program tabs** may not name a profile, run with the default profile's resolved environment (none of the
    contributions when that profile is WSL), work for tabs and splits, and pin close-on-success over `terminal.on_exit`.
11. **Terminal editors open in the call's window** when it is a terminal window, else the active window; with no
    terminal window open, `edit` returns `false` and the desktop opens the file.
12. **A bundled editor replaces only a non-bundled one** (spec 6.4: "another plugin's"); a second bundled plugin's
    registration throws like any later one. Only the Editor provides one today.
13. **The saved installed editor is found synchronously when the Editor starts** (a few file checks on the UI thread,
    for that one editor), so the first shell of a launch already gets its `EDITOR`; the full detection still runs in the
    background (spec 6.2). Ruled before execution (pre-flight finding 5).

## Review Focus

Inputs the spec implies but no other test pins, most likely to bite first. Each has its test in the owning task:

1. A path with a space, a quote and a non-ASCII letter — the client at `/Applications/Jasper Beta.app/...` or a file
   `it's ü.txt` — survives quoting: `/bin/sh -c "$EDITOR file"` runs that program with that file — Task 7.
2. The user started Jasper from a terminal with `EDITOR=vim`: Jasper sets neither `EDITOR` nor `VISUAL` (git keeps
   vim), until `keep_environment_editor` is turned off — Tasks 7 and 8.
3. `config.toml` does not exist yet: Open in Editor creates it from the template and the Jasper Editor opens it —
   Task 4.
4. The Editor plugin is off, failed or stopped: new shells get no `EDITOR`/`VISUAL` from it and Open in Editor falls
   back to the desktop — Tasks 2 and 4.
5. The saved editor was uninstalled: the page says "<Name> (not found)", shells get nothing and files open with the
   desktop — Tasks 8 and 9.

## File structure

| Path | Responsibility |
|---|---|
| `jasper-sdk/.../Capabilities.java`, `JasperSdk.java` | `terminal.environment`, `editor.provide`; `0.17.1`. |
| `jasper-sdk/.../terminal/{Terminals,LocalSpec}.java` | `Terminals.environment` (default); `LocalSpec.command`, `LocalSpec.running`. |
| `jasper-sdk/.../ui/{FileEditor,Platform}.java` | The `FileEditor` hook; `Platform.editor` (default). |
| `jasper-sdk/.../cli/CommandLine.java` | `client()` (default). |
| `jasper-sdk-testkit/...` | Fakes for all four, `FakePluginHost` verbs, harness verbs and contract tests. |
| `jasper-app/.../plugins/{ShellContributions,FileEditors}.java` | The two registries, their rules and the UI-thread hop. |
| `jasper-app/.../plugins/{HostedTerminals,HostedContext,HostedCommandLine,PluginHost,PluginRuntime,DescriptorParser}.java` | Wiring. |
| `jasper-app/.../pluginmanager/Capabilities.java` | Consent text for the two capabilities. |
| `jasper-app/.../launch/{LaunchSettings,ShellLauncher}.java` | The contribution step; `withCommand`; program launches. |
| `jasper-app/.../terminals/OpenSpec.java`, `workspace/{WindowTerminals,WindowContent,TerminalTab,TerminalPane}.java` | Program tabs and their pinned exit policy. |
| `jasper-app/.../commandline/CommandInstall.java` | `client(osName, appPath, developmentClient)`. |
| `jasper-app/.../application/JasperApplication.java` | Contributions for each launch, the editor routing for every open, the client path. |
| `jasper-app/.../plugins/{PluginSettingsPage,KitSettingRows,PluginPanelPage}.java`, `settingsui/Editors.java`, `testkit/{KitSettingRows,FakeSettingsPanelHost,FakePluginHost}.java` | Host rows (`SettingsPanelHost.row`) honour the font and file hints as generated pages do (Task 9), so the Editor's custom page keeps its font picker. |
| `plugins/editor/.../external/` | `KnownEditor`, `KnownEditors`, `Detected`, `Detection`, `Choice`, `EditorCommands`, `DefaultEditor`. |
| `plugins/editor/.../ui/EditorWindow.java` | `edit(Path, OptionalInt, Optional<WindowOwner>)`: the Jasper Editor's own `FileEditor`. |
| `plugins/editor/.../ui/{EditorSettings,EditorSettingsPanel}.java` | The four keys (Task 8); the custom page (Task 9). |
| `plugins/editor/.../EditorPlugin.java`, `plugin.toml`, `settings.toml` | Registration, detection at start, manifest 0.9.0, the seeded template (Task 8). |

Tasks: 1 SDK 0.17.1 values · 2 shell contributions · 3 program tabs · 4 the file editor hook and routing · 5 the client
path · 6 known editors and detection · 7 `EDITOR` values and launch lines · 8 settings, `DefaultEditor` and Editor 0.9.0 ·
9 the settings page · 10 docs, status and the whole-branch check.
| Docs | `docs/editor.md`, `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/STATUS.md`, the spec's status line. |

Paths below abbreviate `jasper-app/src/main/java/dev/jasper/app/` as `app/`, `jasper-app/src/test/java/dev/jasper/app/`
as `app-test/`, `jasper-sdk/src/main/java/dev/jasper/sdk/` as `sdk/`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/`
as `testkit/`, its `src/testFixtures/java/dev/jasper/sdk/testing/contract/` as `contract/`, and
`plugins/editor/src/main/java/dev/jasper/editor/` as `editor/`.

## Interfaces at a glance

Every task's own **Interfaces** block repeats what it consumes and produces; this is the whole set.

```java
// SDK 0.17.1 (Task 1)
public static final String TERMINAL_ENVIRONMENT = "terminal.environment";   // Capabilities
public static final String EDITOR_PROVIDE = "editor.provide";               // Capabilities
@FunctionalInterface public interface FileEditor {                          // dev.jasper.sdk.ui
    boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window);
}
default Subscription editor(FileEditor editor)                              // Platform; no-op default
default Subscription environment(Function<Map<String, String>, Map<String, String>> contributor)  // Terminals; no-op default
public record LocalSpec(Optional<Path> workingDirectory, Optional<String> profile, Optional<List<String>> command)
public static LocalSpec running(List<String> command, Optional<Path> workingDirectory)   // LocalSpec
default Optional<Path> client()                                             // CommandLine; empty default

// Testkit (Tasks 2-5)
public Map<String, String> shellEnvironment(Map<String, String> jasperEnvironment)  // FakePluginHost
public void declareBundled(String pluginId)                                 // FakePluginHost, before that plugin starts
public boolean edit(Path file, OptionalInt line, Optional<UUID> windowId)   // FakePluginHost; the provided editor, else false
public Optional<String> editorPlugin()                                      // FakePluginHost
public void setCommandClient(Path client)                                   // FakePluginHost
// openRequests() lines gain "|run:<argv joined by spaces>" after the profile part for a program tab or split

// App (Tasks 2-5)
final class ShellContributions                                              // dev.jasper.app.plugins
    dev.jasper.app.lifecycle.Subscription register(String pluginId, Function<Map<String, String>, Map<String, String>> contributor)
    Map<String, String> variables(Map<String, String> jasperEnvironment)     // UI thread
final class FileEditors                                                     // dev.jasper.app.plugins
    void bundled(Set<String> pluginIds)
    dev.jasper.app.lifecycle.Subscription register(String pluginId, FileEditor editor, Function<UUID, Optional<WindowOwner>> windows)
    boolean edit(Path file, OptionalInt line, Optional<UUID> windowId)      // UI thread
    boolean editFromWorker(Path file, OptionalInt line, Supplier<Optional<UUID>> window)  // not the UI thread; waits up to 30 s
public Map<String, String> shellVariables(Map<String, String> jasperEnvironment)   // PluginRuntime, UI thread
public void open(Path file, java.util.function.Consumer<Path> desktop)              // PluginRuntime, worker thread
public void commandClient(Optional<Path> client)                                    // PluginRuntime, before start
public LaunchSettings withCommand(List<String> program)                             // LaunchSettings
public static Optional<Path> client(String osName, String appPath, String developmentClient)  // CommandInstall
record Local(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program)  // OpenSpec

// Editor 0.9.0 (Tasks 6-9), package dev.jasper.editor.external
public record KnownEditor(String id, String name, Kind kind, List<String> executables, List<String> macApps,
                          List<String> extraArgs, List<String> waitArgs, LineForm lineForm)
public final class KnownEditors { public static final List<KnownEditor> ALL; public static Optional<KnownEditor> byId(String id); }
public record Detected(KnownEditor editor, Path executable)
public final class Detection { public record Probe(String osName, List<Path> path, Path home, Map<String, String> environment, Predicate<Path> executable);
                               public static List<Detected> detect(Probe probe); }
public final class EditorCommands {   // pure
    public static String quote(String text, String osName);
    public static Optional<String> variable(Choice choice, String osName);              // EDITOR and VISUAL value
    public static List<String> openAt(Detected editor, Path file, OptionalInt line);    // no wait args
    public static List<String> custom(String command, Path file, String osName);        // sh -c / cmd.exe /c
    public static boolean keepsEnvironment(Map<String, String> jasperEnvironment, boolean keepSetting);
}
public sealed interface Choice permits Choice.Jasper, Choice.System, Choice.Installed, Choice.Custom
public final class DefaultEditor {
    public record Preference(String editor, String customCommand, boolean customInTerminal, boolean keepEnvironment) { }
    @FunctionalInterface public interface Launcher { boolean start(List<String> command); static Launcher detached(System.Logger log); }
    public DefaultEditor(Supplier<Preference> preference, Supplier<Detection.Probe> probe, Executor background, Executor ui,
                         Terminals terminals, FileEditor jasper, Launcher launcher, Optional<Path> client, String osName, System.Logger log);
    public Map<String, String> variables(Map<String, String> jasperEnvironment);        // the contributor
    public boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window);     // the FileEditor
    public Choice choice();  public List<Detected> detected();  public void detect();     // detection off the UI thread
    public Subscription onDetected(Runnable listener);                                   // UI thread, after each detection
}
public boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window)          // EditorWindow: the Jasper choice
```

---

### Task 1: SDK 0.17.1 values, and the app accepting the new capabilities

**Files:**
- Create: `sdk/ui/FileEditor.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/cli/CommandLineTest.java`
- Modify: `sdk/Capabilities.java`, `sdk/JasperSdk.java`, `sdk/terminal/Terminals.java`, `sdk/terminal/LocalSpec.java`,
  `sdk/ui/Platform.java`, `sdk/cli/CommandLine.java`, `app/plugins/DescriptorParser.java`,
  `app/pluginmanager/Capabilities.java`, `docs/examples/external-plugin/build.gradle.kts`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/{CapabilitiesTest,files/FilesValuesTest,palette/PaletteValuesTest,terminal/TerminalValuesTest,ui/KitBoundaryTest,ui/UiValuesTest}.java`,
  `app-test/plugins/{DescriptorParserTest,VersionRangeTest}.java`, `app-test/pluginmanager/CapabilityDescriptionsTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces (SDK 0.17.1): `Capabilities.TERMINAL_ENVIRONMENT` (`"terminal.environment"`), `Capabilities.EDITOR_PROVIDE`
  (`"editor.provide"`), both appended to `Capabilities.ALL`; `@FunctionalInterface FileEditor.edit(Path file, OptionalInt line,
  Optional<WindowOwner> window)` → `boolean`; `default Subscription Platform.editor(FileEditor)`;
  `default Subscription Terminals.environment(Function<Map<String, String>, Map<String, String>>)`;
  `record LocalSpec(Optional<Path> workingDirectory, Optional<String> profile, Optional<List<String>> command)` with the
  1- and 2-argument constructors, `ofProfile` and `static LocalSpec running(List<String> command, Optional<Path> workingDirectory)`;
  `default Optional<Path> CommandLine.client()`. Every default is a no-op an older host keeps.
- Produces (app): `DescriptorParser.CAPABILITIES` accepts both names; `pluginmanager.Capabilities.describe` gives
  "Set environment variables in new terminal shells (terminal.environment)" and "Open the files Jasper opens for editing
  (editor.provide)".

The external example's default SDK version is pinned by `VersionRangeTest.theExampleExternalPluginBuildsAgainstThisSdkByDefault`
(plan 1 hit that failure), so `docs/examples/external-plugin/build.gradle.kts` moves to 0.17.1 in this task. The bundled
manifests keep `>=0.17.0, <0.18` (the Editor's moves in Task 8); the docs move in Task 10.

- [ ] **Step 1: Write the failing tests**

`jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java` (whole file):

```java
package dev.jasper.sdk;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {
    @Test void theSdkIsVersionSeventeenOneWithTheEnvironmentAndEditorCapabilities() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.17.1");
        assertThat(Capabilities.NETWORK_LISTEN).isEqualTo("network.listen");
        assertThat(Capabilities.CLIPBOARD_READ).isEqualTo("clipboard.read");
        assertThat(Capabilities.TERMINAL_ENVIRONMENT).isEqualTo("terminal.environment");
        assertThat(Capabilities.EDITOR_PROVIDE).isEqualTo("editor.provide");
        assertThat(Capabilities.ALL).endsWith(Capabilities.CLIPBOARD_READ, Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE);
    }
}
```

`jasper-sdk/src/test/java/dev/jasper/sdk/cli/CommandLineTest.java`:

```java
package dev.jasper.sdk.cli;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CommandLineTest {
    @Test void theClientIsADefaultThatAnOlderHostLeavesEmpty() throws Exception {
        assertThat(CommandLine.class.getMethod("client").isDefault()).isTrue();
        CommandLine older = (CommandLine) java.lang.reflect.Proxy.newProxyInstance(CommandLine.class.getClassLoader(), new Class<?>[]{CommandLine.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        assertThat(older.client()).as("a host that names no client").isEmpty();
    }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')

sdk = 'jasper-sdk/src/test/java/dev/jasper/sdk/'
for test in ['ui/KitBoundaryTest.java', 'files/FilesValuesTest.java', 'palette/PaletteValuesTest.java']:
    edit(sdk + test, '"0.17.0"', '"0.17.1"')

edit(sdk + 'terminal/TerminalValuesTest.java',
     r'''"palette.contribute", "files.source", "network.listen", "clipboard.read");''',
     r'''"palette.contribute", "files.source", "network.listen", "clipboard.read", "terminal.environment", "editor.provide");''')
append_to_class(sdk + 'terminal/TerminalValuesTest.java', r'''
    @Test void aLocalSpecMayRunACommandInsteadOfTheShell() {
        Path home = Path.of(System.getProperty("user.home"));
        var words = new ArrayList<>(List.of("nvim", "+3", "notes.txt"));
        LocalSpec spec = LocalSpec.running(words, Optional.of(home));
        words.add("later");
        assertThat(spec.command()).as("copied").contains(List.of("nvim", "+3", "notes.txt"));
        assertThat(spec.workingDirectory()).contains(home);
        assertThat(spec.profile()).isEmpty();
        assertThat(spec).isEqualTo(new LocalSpec(Optional.of(home), Optional.empty(), Optional.of(List.of("nvim", "+3", "notes.txt"))));
        assertThat(OpenRequest.local(spec)).isEqualTo(new OpenRequest.Local(spec));
        assertThat(new LocalSpec(Optional.empty()).command()).as("the old constructors run the shell").isEmpty();
        assertThat(new LocalSpec(Optional.empty(), Optional.of("pwsh")).command()).isEmpty();
        assertThat(LocalSpec.ofProfile("pwsh").command()).isEmpty();
        assertThatIllegalArgumentException().as("no program").isThrownBy(() -> LocalSpec.running(List.of(), Optional.empty()));
        assertThatIllegalArgumentException().as("a blank program").isThrownBy(() -> LocalSpec.running(List.of(" ", "x"), Optional.empty()));
        assertThatNullPointerException().as("a null word").isThrownBy(() -> LocalSpec.running(java.util.Arrays.asList("nvim", null), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new LocalSpec(Optional.empty(), Optional.empty(), null));
        assertThatIllegalArgumentException().as("a profile or a command, not both")
            .isThrownBy(() -> new LocalSpec(Optional.empty(), Optional.of("pwsh"), Optional.of(List.of("nvim"))));
        assertThatIllegalArgumentException().as("the directory rule still applies")
            .isThrownBy(() -> LocalSpec.running(List.of("nvim"), Optional.of(Path.of("relative"))));
    }

    @Test void theEnvironmentHookIsADefaultThatAnOlderHostIgnores() throws Exception {
        assertThat(Terminals.class.getMethod("environment", java.util.function.Function.class).isDefault()).isTrue();
        Terminals older = (Terminals) java.lang.reflect.Proxy.newProxyInstance(Terminals.class.getClassLoader(), new Class<?>[]{Terminals.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        older.environment(environment -> {
            asked.incrementAndGet();
            return java.util.Map.of("EDITOR", "vim");
        }).close();
        assertThat(asked).as("an older host never asks").hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.environment(null));
    }
''')
append_to_class(sdk + 'ui/UiValuesTest.java', r'''
    @Test void theEditorHookIsADefaultThatAnOlderHostIgnores() throws Exception {
        assertThat(Platform.class.getMethod("editor", FileEditor.class).isDefault()).isTrue();
        Platform older = (Platform) java.lang.reflect.Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class<?>[]{Platform.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        older.editor((file, line, window) -> {
            asked.incrementAndGet();
            return true;
        }).close();
        assertThat(asked).as("an older host never asks").hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.editor(null));
    }
''')

app = 'jasper-app/src/test/java/dev/jasper/app/'
edit(app + 'plugins/DescriptorParserTest.java', r'''            "clipboard.read"));''',
     r'''            "clipboard.read", "terminal.environment", "editor.provide"));''')
append_to_class(app + 'plugins/DescriptorParserTest.java', r'''
    @Test void acceptsTheEnvironmentAndEditorCapabilities() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"terminal.environment\", \"editor.provide\""));
        assertThat(descriptor.capabilities()).contains("terminal.environment", "editor.provide");
    }
''')
append_to_class(app + 'pluginmanager/CapabilityDescriptionsTest.java', r'''
    @Test void theEnvironmentAndEditorCapabilitiesAreDescribed() {
        assertThat(Capabilities.describe("terminal.environment"))
            .isEqualTo("Set environment variables in new terminal shells (terminal.environment)");
        assertThat(Capabilities.describe("editor.provide")).isEqualTo("Open the files Jasper opens for editing (editor.provide)");
    }
''')
edit(app + 'plugins/VersionRangeTest.java',
     r'''        assertThat(VersionRange.parse(">=0.17.0, <0.18").contains(host)).isTrue();''',
     r'''        assertThat(VersionRange.parse(">=0.17.0, <0.18").contains(host)).isTrue();
        assertThat(VersionRange.parse(">=0.17.1, <0.18").contains(host)).as("the Editor's range").isTrue();''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test`
Expected: `:jasper-sdk:compileTestJava` fails: cannot find symbol `TERMINAL_ENVIRONMENT`, `EDITOR_PROVIDE`, `FileEditor`,
`LocalSpec.running`, `command()`, `client()` and `environment(...)`.

- [ ] **Step 3: Write the SDK values**

`sdk/ui/FileEditor.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Opens the files Jasper opens for editing, once a plugin registers it with {@link Platform#editor}.
 *
 * @since 0.17.1
 */
@FunctionalInterface
public interface FileEditor {
    /**
     * Opens a file. UI thread; it must not block.
     *
     * @param file the file
     * @param line the line to show, from 1; empty for the file's start
     * @param window the terminal window the open came from, as this plugin's handle; empty when it came from elsewhere
     * @return true when it opened the file; false hands the file to the desktop
     */
    boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window);
}
```

`sdk/terminal/LocalSpec.java` (whole file):

```java
package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A local terminal to open: the user's configured shell, one of the shell profiles Jasper detected, or a program.
 *
 * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
 * @param profile the id of a profile from {@link Terminals#profiles()} (since 0.9.1); empty means the shell Jasper's own
 *     New Tab or Split would start. An id the host does not list opens nothing.
 * @param command a program run instead of the shell, with the shell's environment: the executable, then its arguments,
 *     each passed as it is, with no shell parsing them (since 0.17.1). Its tab or pane closes when the program exits with
 *     status 0 and stays open otherwise. Empty runs the shell.
 */
public record LocalSpec(Optional<Path> workingDirectory, Optional<String> profile, Optional<List<String>> command) {
    /**
     * Rejects null (a null word of the command too), a relative directory, a blank profile id, an empty command or one
     * whose program is blank, and a profile together with a command. The command is copied.
     */
    public LocalSpec {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(command, "command");
        if (workingDirectory.isPresent() && !workingDirectory.get().isAbsolute())
            throw new IllegalArgumentException("A working directory must be absolute: " + workingDirectory.get());
        if (profile.isPresent() && profile.get().isBlank()) throw new IllegalArgumentException("A profile id cannot be blank");
        if (command.isPresent()) {
            List<String> words = List.copyOf(command.get());
            if (words.isEmpty()) throw new IllegalArgumentException("A command needs a program");
            if (words.getFirst().isBlank()) throw new IllegalArgumentException("A command's program cannot be blank");
            if (profile.isPresent()) throw new IllegalArgumentException("A local terminal runs a profile's shell or a command, not both");
            command = Optional.of(words);
        }
    }

    /**
     * The user's shell, or a detected shell profile.
     *
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     * @param profile the id of a profile from {@link Terminals#profiles()}; empty means the shell Jasper's own New Tab or
     *     Split would start
     * @since 0.9.1
     */
    public LocalSpec(Optional<Path> workingDirectory, Optional<String> profile) { this(workingDirectory, profile, Optional.empty()); }

    /**
     * The user's shell.
     *
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     */
    public LocalSpec(Optional<Path> workingDirectory) { this(workingDirectory, Optional.empty(), Optional.empty()); }

    /**
     * A detected shell profile, where Jasper's own New Tab would start it.
     *
     * @param profileId an id from {@link Terminals#profiles()}
     * @return the spec
     * @since 0.9.1
     */
    public static LocalSpec ofProfile(String profileId) { return new LocalSpec(Optional.empty(), Optional.of(profileId)); }

    /**
     * A program run in a tab or split instead of the shell, with the shell's environment. Its tab or pane closes when it
     * exits with status 0 and stays open otherwise.
     *
     * @param command the executable, then its arguments
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     * @return the spec
     * @since 0.17.1
     */
    public static LocalSpec running(List<String> command, Optional<Path> workingDirectory) {
        return new LocalSpec(workingDirectory, Optional.empty(), Optional.of(command));
    }
}
```

`sdk/cli/CommandLine.java` (whole file):

```java
package dev.jasper.sdk.cli;

import dev.jasper.sdk.Subscription;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Answers this plugin's {@code jasper} subcommands. Implemented by the application and by the testkit.
 *
 * @since 0.17.0
 */
public interface CommandLine {
    /**
     * Handles a subcommand this plugin's {@code plugin.toml} declares. UI thread only. The registration ends with the
     * plugin, and a call still running then ends with exit code 1; while no handler is registered, {@code jasper <name>}
     * reports the subcommand unavailable.
     *
     * @param name a name the manifest declares under {@code [[subcommands]]}
     * @param handler runs each call
     * @return removes the handler
     * @throws IllegalArgumentException when the manifest does not declare {@code name}, another plugin provides it, or
     *     it already has a handler
     * @throws IllegalStateException off the UI thread, or after the plugin stopped
     */
    Subscription handle(String name, CommandHandler handler);

    /**
     * The {@code jasper} command of this Jasper, for a plugin that writes it into another program's settings, such as
     * {@code EDITOR}: the client inside the installed app, or in a development run the one the build made. Any thread.
     *
     * @return the client's path; empty when this Jasper has none
     * @since 0.17.1
     */
    default Optional<Path> client() { return Optional.empty(); }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
sdk = 'jasper-sdk/src/main/java/dev/jasper/sdk/'

edit(sdk + 'JasperSdk.java', '"0.17.0"', '"0.17.1"')

edit(sdk + 'Capabilities.java', r'''    public static final String CLIPBOARD_READ = "clipboard.read";
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE, PALETTE_CONTRIBUTE, FILES_SOURCE, NETWORK_LISTEN, CLIPBOARD_READ);''',
r'''    public static final String CLIPBOARD_READ = "clipboard.read";
    /**
     * Adding variables, such as {@code EDITOR}, to the environment of new local shells with
     * {@code Terminals.environment}. @since 0.17.1
     */
    public static final String TERMINAL_ENVIRONMENT = "terminal.environment";
    /** Being the editor Jasper opens files with, through {@code Platform.editor}. @since 0.17.1 */
    public static final String EDITOR_PROVIDE = "editor.provide";
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE, PALETTE_CONTRIBUTE, FILES_SOURCE, NETWORK_LISTEN, CLIPBOARD_READ,
        TERMINAL_ENVIRONMENT, EDITOR_PROVIDE);''')

terminals = sdk + 'terminal/Terminals.java'
edit(terminals, 'import java.util.List;\nimport java.util.Optional;\nimport java.util.UUID;\n',
     'import java.util.List;\nimport java.util.Map;\nimport java.util.Optional;\nimport java.util.UUID;\nimport java.util.function.Function;\n')
edit(terminals, r'''     * Opens and selects a new tab. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.''',
     r'''     * Opens and selects a new tab. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.
     * A request with a command ({@link LocalSpec#command()}) runs that program instead of the shell, with the shell's
     * environment, and its pane closes when the program exits with status 0.''')
edit(terminals, r'''     * Splits a pane. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.''',
     r'''     * Splits a pane. A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing.
     * A request with a command ({@link LocalSpec#command()}) runs that program instead of the shell, with the shell's
     * environment, and its pane closes when the program exits with status 0.''')
edit(terminals, r'''    default Subscription onProfilesChanged(Runnable listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        return () -> { };
    }
''', r'''    default Subscription onProfilesChanged(Runnable listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        return () -> { };
    }

    /**
     * Adds variables to each new local shell, and to each program a {@link LocalSpec#command()} runs. The contributor runs
     * on the UI thread for every launch, must not block or do I/O, and receives Jasper's own environment, read-only; it
     * returns the variables to add, and null adds nothing. A shell's environment is Jasper's own, Jasper's variables,
     * then the contributions, then the user's {@code [terminal.env]}, then {@code TERM} and {@code COLORTERM}. A name must
     * match {@code [A-Za-z_][A-Za-z0-9_]*}; {@code TERM}, {@code COLORTERM}, {@code TERM_PROGRAM},
     * {@code TERM_PROGRAM_VERSION} and names starting {@code JASPER_} are reserved. An invalid or reserved name, and a
     * value holding NUL, are dropped and logged once. When two plugins set one name, the first by plugin id wins and the
     * other is logged. A contributor that throws adds nothing. WSL profiles and provided sessions get no contributions.
     * Needs {@code terminal.environment}. UI thread only. The registration ends with the plugin.
     *
     * @param contributor given Jasper's own environment, returns the variables to add
     * @return the registration
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code terminal.environment}
     * @since 0.17.1
     */
    default Subscription environment(Function<Map<String, String>, Map<String, String>> contributor) {
        java.util.Objects.requireNonNull(contributor, "contributor");
        return () -> { };
    }
''')

platform = sdk + 'ui/Platform.java'
edit(platform, 'import java.net.URI;\n', 'import dev.jasper.sdk.Subscription;\nimport java.net.URI;\n')
edit(platform, r'''     * Opens a file in the user's editor: the configured editor, then the desktop's edit or open
     * action, then revealing the file.''',
     r'''     * Opens a file in the user's editor: the editor a plugin provides (see {@link #editor}), then the desktop's edit or
     * open action, then revealing the file.''')
edit(platform, r'''    Optional<String> clipboardText();
}''', r'''    Optional<String> clipboardText();

    /**
     * Makes this plugin the editor Jasper opens files with: {@code config.toml}, the plugin settings files, the custom VM
     * options file and every {@link #openInEditor} go to {@code editor} first, on the UI thread. One at a time: a bundled
     * plugin's registration replaces a plugin's that is not bundled; otherwise the first stays and any later one, this
     * plugin's own included, throws. When the editor returns false or throws, when no plugin provides one, and when
     * Jasper waited 30 seconds for the UI thread's answer, the desktop opens the file as before. Needs
     * {@code editor.provide}. UI thread only. The registration ends with the plugin.
     *
     * @param editor opens a file
     * @return the registration
     * @throws IllegalStateException when another plugin already provides the editor
     *     ({@code The editor is already provided by <plugin id>}), and off the UI thread
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code editor.provide}
     * @since 0.17.1
     */
    default Subscription editor(FileEditor editor) {
        java.util.Objects.requireNonNull(editor, "editor");
        return () -> { };
    }
}''')
PY
```

- [ ] **Step 4: Let the app accept and describe the capabilities, and move the external example to 0.17.1**

Run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
app = 'jasper-app/src/main/java/dev/jasper/app/'
edit(app + 'plugins/DescriptorParser.java', r'''        "clipboard.read");''',
     r'''        "clipboard.read", "terminal.environment", "editor.provide");''')
edit(app + 'pluginmanager/Capabilities.java', r'''            case "clipboard.read" -> "Read text you copied to the clipboard";''',
     r'''            case "clipboard.read" -> "Read text you copied to the clipboard";
            case "terminal.environment" -> "Set environment variables in new terminal shells";
            case "editor.provide" -> "Open the files Jasper opens for editing";''')
edit('docs/examples/external-plugin/build.gradle.kts', 'getOrElse("0.17.0")', 'getOrElse("0.17.1")')
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.DescriptorParserTest' --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.pluginmanager.CapabilityDescriptionsTest' --tests 'dev.jasper.app.plugins.AppContractTest'`
Expected: PASS, including the SDK's javadoc doclint, `verifySdkRelease` (its jar names follow `JasperSdk.VERSION`) and
`VersionRangeTest.theExampleExternalPluginBuildsAgainstThisSdkByDefault`.
Then run: `./gradlew verifySdkArchitecture`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java \
  jasper-sdk/src/main/java/dev/jasper/sdk/terminal/Terminals.java jasper-sdk/src/main/java/dev/jasper/sdk/terminal/LocalSpec.java \
  jasper-sdk/src/main/java/dev/jasper/sdk/ui/FileEditor.java jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java \
  jasper-sdk/src/main/java/dev/jasper/sdk/cli/CommandLine.java \
  jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java jasper-sdk/src/test/java/dev/jasper/sdk/cli/CommandLineTest.java \
  jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java \
  jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitBoundaryTest.java \
  jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java \
  jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java docs/examples/external-plugin/build.gradle.kts
git commit -m "$(cat <<'EOF'
SDK 0.17.1: environment, editor, program and client hooks

terminal.environment and editor.provide join Capabilities.ALL; FileEditor
and the defaults Platform.editor, Terminals.environment and
CommandLine.client do nothing on a host that predates them; LocalSpec may
run a command instead of the shell. The app accepts and describes the two
capabilities, and the external example builds against 0.17.1.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `Terminals.environment` end to end

**Files:**
- Create: `app/plugins/ShellContributions.java`, `app-test/plugins/ShellContributionsTest.java`
- Modify: `app/plugins/{HostedTerminals,HostedContext,PluginHost,PluginRuntime}.java`, `app/launch/LaunchSettings.java`,
  `app/application/JasperApplication.java`, `testkit/{FakeTerminals,FakePluginContext,FakePluginHost}.java`,
  `contract/{ContractHarness,PluginContractTest}.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`,
  `app-test/plugins/AppContractTest.java`, `docs/design/theme-engine/migration.csv`
- Test: `app-test/plugins/{ShellContributionsTest,HostedTerminalsTest,PluginRuntimeTest}.java`, `app-test/launch/LaunchSettingsTest.java`,
  `app-test/application/ShellProfileLaunchTest.java`, the contract

**Interfaces:**
- Consumes: `Capabilities.TERMINAL_ENVIRONMENT`, `Terminals.environment` (Task 1).
- Produces (app): `ShellContributions(Containment)`; `dev.jasper.app.lifecycle.Subscription register(String pluginId,
  Function<Map<String, String>, Map<String, String>> contributor)`; `Map<String, String> variables(Map<String, String>
  jasperEnvironment)` (UI thread, never null); `PluginHost.shellContributions`; `PluginRuntime.shellVariables(Map)` (UI
  thread; empty before start and after stop); `LaunchSettings.resolve(ConfigSnapshot, ShellProfile, String, Map, int, int,
  Path, Function<Map<String, String>, Map<String, String>> contributions)`; `JasperApplication.windowLauncher(...,
  Function<UUID, Map<String, String>> paneVariables, Function<Map<String, String>, Map<String, String>> contributions)`;
  a 10-argument `HostedTerminals` constructor taking the `ShellContributions`.
- Produces (testkit): `FakePluginHost.shellEnvironment(Map<String, String> jasperEnvironment)` → `Map<String, String>`;
  package-private `FakePluginContext.host()`.
- Produces (contract): `Map<String, String> ContractHarness.shellVariables(Map<String, String> jasperEnvironment)`.

The rules both hosts apply: contributors run plugin by plugin in id order and, within a plugin, in registration order,
each given a read-only copy of Jasper's environment; a throw adds nothing (the app records it through `Containment`) and a
null map counts as empty; a name outside `[A-Za-z_][A-Za-z0-9_]*`, `TERM`, `COLORTERM`, `TERM_PROGRAM`,
`TERM_PROGRAM_VERSION`, a name starting `JASPER_`, a null value and a value holding NUL are dropped; the first contributor
of a name wins. The app logs each dropped or losing entry once per plugin and name. `LaunchSettings` applies the result
after the `JASPER_SHELL_INTEGRATION` block and before `[terminal.env]`, never for a WSL profile, and passes the untouched
inherited map. `settings.apply` runs on the EDT for every launch, and a second time (through `invokeAndWait`) when a
PowerShell 7 launch is confirmed after detection, so a contributor may be asked twice for one launch; it is pure, so that
is harmless.

- [ ] **Step 1: Write the failing tests**

`app-test/plugins/ShellContributionsTest.java`:

```java
package dev.jasper.app.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShellContributionsTest {
    private final Containment containment = new Containment(() -> true);
    private final ShellContributions contributions = new ShellContributions(containment);

    @Test void theFirstPluginByIdWinsWhicheverRegisteredFirst() {
        contributions.register("dev.x.zed", environment -> Map.of("EDITOR", "zed --wait", "ZED_ONLY", "1"));
        contributions.register("dev.x.alpha", environment -> Map.of("EDITOR", "alpha"));
        contributions.register("dev.x.alpha", environment -> Map.of("EDITOR", "alpha again", "ALPHA", "2"));
        assertThat(contributions.variables(Map.of())).as("within a plugin the earlier registration wins too")
            .isEqualTo(Map.of("EDITOR", "alpha", "ZED_ONLY", "1", "ALPHA", "2"));
    }

    @Test void contributorsSeeJaspersEnvironmentReadOnlyAndAThrowAddsNothing() {
        Map<String, String> jasper = new HashMap<>(Map.of("EDITOR", "vim", "HOME", "/home/me"));
        var seen = new AtomicReference<Map<String, String>>();
        contributions.register("dev.x.a", environment -> {
            seen.set(environment);
            return Map.of("SEEN", environment.get("HOME"));
        });
        contributions.register("dev.x.b", environment -> {
            environment.put("HOME", "/elsewhere");
            return Map.of("NEVER", "1");
        });
        contributions.register("dev.x.c", environment -> { throw new IllegalStateException("broken contributor"); });
        contributions.register("dev.x.d", environment -> null);
        assertThat(contributions.variables(jasper)).isEqualTo(Map.of("SEEN", "/home/me"));
        assertThat(seen.get()).isEqualTo(Map.of("EDITOR", "vim", "HOME", "/home/me"));
        assertThat(jasper).as("the caller's map is untouched").isEqualTo(Map.of("EDITOR", "vim", "HOME", "/home/me"));
        assertThat(containment.failures("dev.x.b")).as("writing to the read-only copy throws").isEqualTo(1);
        assertThat(containment.failures("dev.x.c")).isEqualTo(1);
        assertThat(containment.failures("dev.x.d")).as("a null map counts as empty").isZero();
    }

    @Test void invalidReservedAndNulEntriesAreDroppedAndEachProblemIsLoggedOnce() {
        Map<String, String> offered = new HashMap<>();
        for (String name : List.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION", "JASPER_PANE", "JASPER_ANYTHING",
                "1ST", "WITH-DASH", "WITH SPACE", "", "ÜBER"))
            offered.put(name, "x");
        offered.put(null, "x");
        offered.put("NO_VALUE", null);
        offered.put("NUL_VALUE", "a\0b");
        offered.put("EDITOR", "nvim");
        offered.put("_kept_2", "yes");
        contributions.register("dev.x.b", environment -> offered);
        contributions.register("dev.x.a", environment -> Map.of("EDITOR", "code --wait"));
        List<LogRecord> records = logged(() -> {
            assertThat(contributions.variables(Map.of())).isEqualTo(Map.of("EDITOR", "code --wait", "_kept_2", "yes"));
            contributions.variables(Map.of());
        });
        assertThat(records).extracting(LogRecord::getMessage).hasSize(15).doesNotHaveDuplicates()
            .contains("Plugin dev.x.b's shell variable TERM is reserved; it was dropped",
                "Plugin dev.x.b's shell variable JASPER_PANE is reserved; it was dropped",
                "Plugin dev.x.b's shell variable WITH-DASH is not a valid name; it was dropped",
                "Plugin dev.x.b's shell variable null is not a valid name; it was dropped",
                "Plugin dev.x.b's shell variable NO_VALUE has no value; it was dropped",
                "Plugin dev.x.b's shell variable NUL_VALUE holds a NUL character; it was dropped",
                "Plugin dev.x.b's shell variable EDITOR lost to dev.x.a");
        assertThat(records).allSatisfy(record -> assertThat(record.getLevel()).isEqualTo(Level.WARNING));
    }

    @Test void aClosedRegistrationContributesNothing() {
        var first = contributions.register("dev.x.a", environment -> Map.of("A", "1"));
        contributions.register("dev.x.a", environment -> Map.of("B", "2"));
        first.close();
        first.close();
        assertThat(contributions.variables(Map.of())).isEqualTo(Map.of("B", "2"));
    }

    /** What ShellContributions logs while {@code action} runs. */
    private static List<LogRecord> logged(Runnable action) {
        Logger logger = Logger.getLogger(ShellContributions.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
        return records;
    }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')
contract = 'jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/'
app = 'jasper-app/src/test/java/dev/jasper/app/'

append_to_class(contract + 'ContractHarness.java', r'''        /**
         * What plugins' {@code Terminals.environment} contributors add to a new local shell, given Jasper's own environment;
         * asked on the UI thread.
         */
        java.util.Map<String, String> shellVariables(java.util.Map<String, String> jasperEnvironment);
''')

append_to_class(contract + 'PluginContractTest.java', r'''
    @Test void shellVariablesNeedTheCapabilityAndTheFirstPluginByIdWins() {
        var bare = new AtomicReference<PluginContext>();
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        h.start(info("test.beta", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), beta::set);
        h.start(info("test.alpha", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), alpha::set);
        var seen = new AtomicReference<Map<String, String>>();
        h.ui(() -> {
            assertThatThrownBy(() -> bare.get().terminals().environment(environment -> Map.of("A", "1")))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("terminal.environment"));
            beta.get().terminals().environment(environment -> Map.of("EDITOR", "beta", "PAGER", "less"));
            alpha.get().terminals().environment(environment -> {
                seen.set(environment);
                return Map.of("EDITOR", "alpha");
            });
        });
        Map<String, String> jasper = new java.util.HashMap<>(Map.of("HOME", "/home/me", "EDITOR", "vim"));
        assertThat(h.shellVariables(jasper)).as("by plugin id, not by start order").isEqualTo(Map.of("EDITOR", "alpha", "PAGER", "less"));
        assertThat(seen.get()).as("Jasper's own environment").isEqualTo(Map.of("HOME", "/home/me", "EDITOR", "vim"));
    }

    @Test void invalidReservedAndNulEntriesAreDroppedAndAFailingContributorAddsNothing() {
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), a::set);
        Map<String, String> offered = new java.util.HashMap<>();
        for (String name : List.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION", "JASPER_PANE", "JASPER_ANYTHING",
                "1ST", "WITH-DASH", "WITH SPACE", "", "ÜBER"))
            offered.put(name, "x");
        offered.put(null, "x");
        offered.put("NO_VALUE", null);
        offered.put("NUL_VALUE", "a\0b");
        offered.put("KEPT", "yes");
        offered.put("_kept_2", "yes");
        h.ui(() -> {
            var terminals = a.get().terminals();
            terminals.environment(environment -> offered);
            terminals.environment(environment -> { throw new IllegalStateException("broken contributor"); });
            terminals.environment(environment -> {
                environment.put("HOME", "/elsewhere");
                return Map.of("NEVER", "1");
            });
            terminals.environment(environment -> null);
        });
        Map<String, String> jasper = new java.util.HashMap<>(Map.of("HOME", "/home/me"));
        assertThat(h.shellVariables(jasper)).isEqualTo(Map.of("KEPT", "yes", "_kept_2", "yes"));
        assertThat(jasper).as("contributors get a read-only copy").isEqualTo(Map.of("HOME", "/home/me"));
    }

    @Test void aContributorEndsWithItsRegistrationAndWithItsPlugin() {
        var a = new AtomicReference<PluginContext>();
        var b = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), a::set);
        h.start(info("test.b", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), b::set);
        var registration = new AtomicReference<Subscription>();
        h.ui(() -> {
            registration.set(a.get().terminals().environment(environment -> Map.of("A", "1")));
            b.get().terminals().environment(environment -> Map.of("B", "2"));
        });
        assertThat(h.shellVariables(Map.of())).isEqualTo(Map.of("A", "1", "B", "2"));
        h.ui(() -> registration.get().close());
        assertThat(h.shellVariables(Map.of())).isEqualTo(Map.of("B", "2"));
        h.stopAll();
        assertThat(h.shellVariables(Map.of())).as("the registration ends with its plugin").isEmpty();
    }
''')

append_to_class(app + 'plugins/HostedTerminalsTest.java', r'''
    @Test void anEnvironmentContributorNeedsTheCapabilityAndTheUiThreadAndEndsWithThePlugin() {
        var contributions = new ShellContributions(new Containment(() -> true));
        HostedTerminals terminals = new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of(Capabilities.TERMINAL_ENVIRONMENT)),
            fixture.registry, posted::add, onUi::get, open::get,
            new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null, name -> new javax.swing.ImageIcon()),
            PluginRuntime.ShellProfiles.NONE, new Containment(() -> true), contributions);
        assertThatThrownBy(() -> terminals().environment(environment -> java.util.Map.of("A", "1")))
            .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_ENVIRONMENT));
        onUi.set(false);
        assertThatIllegalStateException().isThrownBy(() -> terminals.environment(environment -> java.util.Map.of("A", "1")));
        onUi.set(true);
        var first = terminals.environment(environment -> java.util.Map.of("A", "1"));
        terminals.environment(environment -> java.util.Map.of("B", "2"));
        assertThat(contributions.variables(java.util.Map.of())).isEqualTo(java.util.Map.of("A", "1", "B", "2"));
        onUi.set(false);
        first.close();
        assertThat(contributions.variables(java.util.Map.of())).as("a close off the UI thread is posted to it").containsKey("A");
        onUi.set(true);
        List.copyOf(posted).forEach(Runnable::run);
        assertThat(contributions.variables(java.util.Map.of())).isEqualTo(java.util.Map.of("B", "2"));
        terminals.closeAll();
        assertThat(contributions.variables(java.util.Map.of())).as("the plugin stopped").isEmpty();
    }
''')

append_to_class(app + 'launch/LaunchSettingsTest.java', r'''
    private static ShellProfile login() {
        return new ShellProfile("login", "zsh", ShellProfile.Kind.LOGIN_SHELL, Optional.of("zsh"), List.of("/bin/zsh", "-l"));
    }

    @Test void pluginVariablesComeAfterJaspersOwnAndBeforeTheConfiguredOnes() {
        var config = snapshot("", List.of(), Map.of("EDITOR", "configured"), 100, 80, 24);
        Map<String, String> inherited = Map.of("EDITOR", "inherited", "PAGER", "less", "TERM_PROGRAM", "iTerm.app");
        var given = new ArrayList<Map<String, String>>();
        var settings = LaunchSettings.resolve(config, login(), "Mac OS X", inherited, 80, 24, null, environment -> {
            given.add(environment);
            return Map.of("EDITOR", "plugin", "VISUAL", "plugin", "PAGER", "more", "TERM", "dumb", "COLORTERM", "no");
        });
        assertThat(given).singleElement().as("the untouched inherited map").isSameAs(inherited);
        assertThat(settings.environment()).containsEntry("EDITOR", "configured").containsEntry("VISUAL", "plugin")
            .containsEntry("PAGER", "more").containsEntry("TERM", "xterm-256color").containsEntry("COLORTERM", "truecolor")
            .containsEntry("TERM_PROGRAM", "Jasper");
    }

    @Test void aWslProfileGetsNoPluginVariablesAndTheOtherOverloadsAddNone() {
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), wsl(), "Windows 11", Map.of(), 80, 24, null, environment -> {
            asked.incrementAndGet();
            return Map.of("EDITOR", "plugin");
        });
        assertThat(settings.environment()).doesNotContainKey("EDITOR");
        assertThat(asked).as("only WSLENV's variables reach Linux").hasValue(0);
        assertThat(LaunchSettings.resolve(ConfigSnapshot.defaults(), login(), "Linux", Map.of(), 80, 24, null).environment())
            .doesNotContainKey("EDITOR");
    }
''')

append_to_class(app + 'application/ShellProfileLaunchTest.java', r'''
    @Test void theWindowLauncherAsksPluginsOnTheEdtAndPutsTheirVariablesBeforeTheConfiguredOnes() throws Exception {
        var snapshot = ConfigLoader.parse(directory.resolve("config.toml"), "[terminal.env]\nCONFIGURED = 'configured'\n", false).snapshot();
        var login = new ShellProfile("login", "Login shell", ShellProfile.Kind.LOGIN_SHELL, Optional.empty(), List.of("/bin/zsh", "-l"));
        var wsl = new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.of("WSL 2"), List.of("wsl.exe", "-d", "Ubuntu"));
        Queue<Runnable> pending = new ArrayDeque<>();
        var started = new ArrayList<LaunchSettings>();
        var onEdt = new ArrayList<Boolean>();
        var inheritedMaps = new ArrayList<Map<String, String>>();
        SwingUtilities.invokeAndWait(() -> {
            var launcher = JasperApplication.windowLauncher(pending::add, () -> snapshot, (path, settings) -> {
                started.add(settings);
                throw new IllegalStateException("controlled launch boundary");
            }, null, (profile, shell) -> new ShellProfiles.Resolution(profile.isPresent() ? wsl : login, Optional.empty()),
                () -> false, warning -> { }, pane -> Map.of(), inherited -> {
                    onEdt.add(SwingUtilities.isEventDispatchThread());
                    inheritedMaps.add(inherited);
                    return Map.of("CONTRIBUTED", "plugin", "CONFIGURED", "plugin");
                });
            launcher.launch(directory, Optional.empty(), (session, failure) -> { });
            launcher.launch(directory, Optional.of("wsl:Ubuntu"), (session, failure) -> { });
        });
        while (!pending.isEmpty()) pending.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(started).hasSize(2);
        assertThat(started.get(0).environment()).containsEntry("CONTRIBUTED", "plugin").containsEntry("CONFIGURED", "configured");
        assertThat(started.get(1).environment()).as("a WSL shell gets no plugin variables").doesNotContainKey("CONTRIBUTED");
        assertThat(onEdt).as("asked once, for the local shell, on the EDT").containsExactly(true);
        assertThat(inheritedMaps).singleElement().isEqualTo(System.getenv());
    }
''')

runtime_test = app + 'plugins/PluginRuntimeTest.java'
edit(runtime_test, 'import static org.assertj.core.api.Assertions.assertThat;\n',
     'import static org.assertj.core.api.Assertions.assertThat;\nimport static org.assertj.core.api.Assertions.assertThatIllegalStateException;\n')
append_to_class(runtime_test, r'''
    static final String ENVIRONMENT = """
        package fix.environment;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.terminals().environment(jasper -> java.util.Map.of("EDITOR", "probe --wait", "VISUAL", "probe --wait"));
            }
        }
        """;

    static final String FAILING_ENVIRONMENT = """
        package fix.failing;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.terminals().environment(jasper -> java.util.Map.of("FAILED_PLUGIN", "1"));
                throw new IllegalStateException("fails after registering");
            }
        }
        """;

    /** Review Focus 4: a plugin that failed or stopped adds nothing to new shells. */
    @Test void onlyRunningPluginsAddToNewShells() throws Exception {
        Path bundled = root.resolve("bundled");
        PluginJars.build(bundled.resolve("dev.example.environment"), "environment.jar",
            PluginJars.descriptor("dev.example.environment", "1.0.0", "fix.environment.Main") + "capabilities = [\"terminal.environment\"]\n",
            Map.of("fix.environment.Main", ENVIRONMENT), List.of());
        PluginJars.build(bundled.resolve("dev.example.failing"), "failing.jar",
            PluginJars.descriptor("dev.example.failing", "1.0.0", "fix.failing.Main") + "capabilities = [\"terminal.environment\"]\n",
            Map.of("fix.failing.Main", FAILING_ENVIRONMENT), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        assertThat(AppContractTest.onEdtValue(() -> runtime.shellVariables(Map.of()))).as("before the plugins start").isEmpty();
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        assertThat(runtime.statusLines()).anySatisfy(line -> assertThat(line).contains("dev.example.failing", "FAILED"));
        assertThat(AppContractTest.onEdtValue(() -> runtime.shellVariables(Map.of("EDITOR", "vim"))))
            .as("the failed plugin's contributor went with it").isEqualTo(Map.of("EDITOR", "probe --wait", "VISUAL", "probe --wait"));
        assertThatIllegalStateException().as("off the UI thread").isThrownBy(() -> runtime.shellVariables(Map.of()));
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        assertThat(AppContractTest.onEdtValue(() -> runtime.shellVariables(Map.of()))).as("after the plugin stopped").isEmpty();
    }
''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: compilation fails: `FakeContractTest`'s harness does not override `shellVariables(Map<String,String>)`.
Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation fails: cannot find symbol `ShellContributions`, the 8-argument `LaunchSettings.resolve`, the
9-argument `windowLauncher` and `PluginRuntime.shellVariables`.

- [ ] **Step 3: Write `ShellContributions`**

`app/plugins/ShellContributions.java`:

```java
package dev.jasper.app.plugins;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * What plugins add to the environment of new local shells ({@code Terminals.environment}). For each launch the
 * contributors run plugin by plugin in id order and, within a plugin, in registration order, each inside the plugin's
 * containment, so one that throws adds nothing. An entry whose name is invalid or reserved, or whose value is missing or
 * holds NUL, is dropped; the first contributor of a name wins. Each dropped or losing entry is logged once per plugin and
 * name. UI thread.
 */
final class ShellContributions {
    private static final System.Logger LOG = System.getLogger(ShellContributions.class.getName());
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    /** Jasper sets these itself: TERM and COLORTERM after the contributions, the others as its own markers. */
    private static final Set<String> RESERVED = Set.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION");

    /** One registration, compared by identity: a function registered twice is two contributors. */
    private static final class Contributor {
        final Function<Map<String, String>, Map<String, String>> function;
        Contributor(Function<Map<String, String>, Map<String, String>> function) { this.function = function; }
    }

    /** A plugin and a variable name, so each problem is logged once. */
    private record Logged(String pluginId, String name) { }

    private final Containment containment;
    private final TreeMap<String, List<Contributor>> byPlugin = new TreeMap<>();
    private final Set<Logged> logged = new HashSet<>();

    ShellContributions(Containment containment) { this.containment = Objects.requireNonNull(containment, "containment"); }

    /**
     * Adds a plugin's contributor.
     *
     * @return removes it; idempotent
     */
    dev.jasper.app.lifecycle.Subscription register(String pluginId, Function<Map<String, String>, Map<String, String>> contributor) {
        Objects.requireNonNull(pluginId, "pluginId");
        var entry = new Contributor(Objects.requireNonNull(contributor, "contributor"));
        byPlugin.computeIfAbsent(pluginId, id -> new ArrayList<>()).add(entry);
        return new dev.jasper.app.lifecycle.Subscription(() -> {
            List<Contributor> contributors = byPlugin.get(pluginId);
            if (contributors == null) return;
            contributors.removeIf(registered -> registered == entry);
            if (contributors.isEmpty()) byPlugin.remove(pluginId);
        });
    }

    /**
     * The variables plugins add to a new shell.
     *
     * @param jasperEnvironment Jasper's own environment; each contributor gets a read-only copy
     * @return the variables; never null
     */
    Map<String, String> variables(Map<String, String> jasperEnvironment) {
        if (byPlugin.isEmpty()) return Map.of();
        Map<String, String> environment = Map.copyOf(jasperEnvironment);
        var added = new HashMap<String, String>();
        var owners = new HashMap<String, String>();
        for (var plugin : List.copyOf(byPlugin.entrySet())) {
            String pluginId = plugin.getKey();
            for (Contributor contributor : List.copyOf(plugin.getValue())) {
                var offered = new ArrayList<Map.Entry<String, String>>();
                Throwable failure = containment.attempt(pluginId, "shell environment", () -> {
                    Map<String, String> result = contributor.function.apply(environment);
                    if (result != null) result.forEach((name, value) -> offered.add(new AbstractMap.SimpleImmutableEntry<>(name, value)));
                    return null;
                });
                if (failure != null) continue;
                for (Map.Entry<String, String> entry : offered) {
                    String name = entry.getKey();
                    String problem = problem(name, entry.getValue());
                    if (problem != null) {
                        once(pluginId, name, "Plugin " + pluginId + "'s shell variable " + name + " " + problem + "; it was dropped");
                        continue;
                    }
                    String owner = owners.putIfAbsent(name, pluginId);
                    if (owner == null) added.put(name, entry.getValue());
                    else if (!owner.equals(pluginId)) once(pluginId, name, "Plugin " + pluginId + "'s shell variable " + name + " lost to " + owner);
                }
            }
        }
        return Map.copyOf(added);
    }

    /** Why an entry is dropped, or null when it is kept. */
    private static String problem(String name, String value) {
        if (name == null || !NAME.matcher(name).matches()) return "is not a valid name";
        if (RESERVED.contains(name) || name.startsWith("JASPER_")) return "is reserved";
        if (value == null) return "has no value";
        if (value.indexOf('\0') >= 0) return "holds a NUL character";
        return null;
    }

    private void once(String pluginId, String name, String message) {
        if (logged.add(new Logged(pluginId, name))) LOG.log(System.Logger.Level.WARNING, message);
    }
}
```

- [ ] **Step 4: Wire it through the host, the launch and the testkit**

Run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')
app = 'jasper-app/src/main/java/dev/jasper/app/'
testkit = 'jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/'

terminals = app + 'plugins/HostedTerminals.java'
edit(terminals, 'import java.util.List;\n', 'import java.util.List;\nimport java.util.Map;\n')
edit(terminals, 'import java.util.function.Consumer;\n', 'import java.util.function.Consumer;\nimport java.util.function.Function;\n')
edit(terminals, r'''    private final List<dev.jasper.app.lifecycle.Subscription> profileRegistrations = new ArrayList<>();
''', r'''    private final List<dev.jasper.app.lifecycle.Subscription> profileRegistrations = new ArrayList<>();
    private final ShellContributions shellContributions;
    /** Live {@link #environment} registrations, closed with the plugin. UI thread. */
    private final List<dev.jasper.app.lifecycle.Subscription> environmentRegistrations = new ArrayList<>();
''')
edit(terminals, r'''                    BooleanSupplier open, HostedSessions sessions, PluginRuntime.ShellProfiles shellProfiles, Containment containment) {
        this.pluginId = pluginId; this.gate = gate; this.registry = registry; this.ui = ui; this.onUi = onUi; this.open = open;
        this.sessions = sessions; this.shellProfiles = shellProfiles; this.containment = containment;
    }''', r'''                    BooleanSupplier open, HostedSessions sessions, PluginRuntime.ShellProfiles shellProfiles, Containment containment) {
        this(pluginId, gate, registry, ui, onUi, open, sessions, shellProfiles, containment, new ShellContributions(containment));
    }

    /** As above; {@link #environment} registers into {@code shellContributions}, the host's. */
    HostedTerminals(String pluginId, CapabilityGate gate, TerminalRegistry registry, Consumer<Runnable> ui, BooleanSupplier onUi,
                    BooleanSupplier open, HostedSessions sessions, PluginRuntime.ShellProfiles shellProfiles, Containment containment,
                    ShellContributions shellContributions) {
        this.pluginId = pluginId; this.gate = gate; this.registry = registry; this.ui = ui; this.onUi = onUi; this.open = open;
        this.sessions = sessions; this.shellProfiles = shellProfiles; this.containment = containment;
        this.shellContributions = shellContributions;
    }''')
edit(terminals, r'''    /** A named profile must be one the catalog lists; otherwise nothing opens and the plugin's mistake is logged. */''',
r'''    @Override public Subscription environment(Function<Map<String, String>, Map<String, String>> contributor) {
        Objects.requireNonNull(contributor, "contributor");
        gate.require(Capabilities.TERMINAL_ENVIRONMENT);
        requireUi("environment");
        dev.jasper.app.lifecycle.Subscription registration = shellContributions.register(pluginId, contributor);
        environmentRegistrations.add(registration);
        gate.audit(Capabilities.TERMINAL_ENVIRONMENT, "added variables to new shells");
        var closed = new AtomicBoolean();
        Runnable removal = () -> { environmentRegistrations.remove(registration); registration.close(); };
        return () -> {
            if (!closed.compareAndSet(false, true)) return;
            if (onUi.getAsBoolean()) removal.run(); else ui.accept(removal);
        };
    }

    /** A named profile must be one the catalog lists; otherwise nothing opens and the plugin's mistake is logged. */''')
edit(terminals, r'''    /** The plugin is stopping: cancel its sessions and drop its profile listeners. UI thread. */
    void closeAll() {
        sessions.cancelAll();
        var registrations = List.copyOf(profileRegistrations);
        profileRegistrations.clear();
        registrations.forEach(dev.jasper.app.lifecycle.Subscription::close);
    }''', r'''    /** The plugin is stopping: cancel its sessions and drop its profile listeners and environment contributors. UI thread. */
    void closeAll() {
        sessions.cancelAll();
        var registrations = new ArrayList<>(profileRegistrations);
        registrations.addAll(environmentRegistrations);
        profileRegistrations.clear();
        environmentRegistrations.clear();
        registrations.forEach(dev.jasper.app.lifecycle.Subscription::close);
    }''')

edit(app + 'plugins/HostedContext.java', r'''            () -> state != State.CLOSED, sessions, host.environment.profiles(), host.containment);''',
     r'''            () -> state != State.CLOSED, sessions, host.environment.profiles(), host.containment, host.shellContributions);''')

host = app + 'plugins/PluginHost.java'
edit(host, '    final PluginCommands commands;\n', '''    final PluginCommands commands;
    /** What plugins add to new local shells ({@code Terminals.environment}). UI thread. */
    final ShellContributions shellContributions;
''')
edit(host, '        this.commands = new PluginCommands(environment.ui(), containment);\n',
     '        this.commands = new PluginCommands(environment.ui(), containment);\n        this.shellContributions = new ShellContributions(containment);\n')

edit(app + 'plugins/PluginRuntime.java', r'''    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {''', r'''    /**
     * What running plugins add to a new local shell ({@code Terminals.environment}), given Jasper's own environment.
     * UI thread. Empty before the plugins start and after they stop.
     *
     * @param jasperEnvironment Jasper's own environment, untouched
     * @return the variables, never null
     * @throws IllegalStateException off the UI thread
     */
    public Map<String, String> shellVariables(Map<String, String> jasperEnvironment) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Shell variables are read on the UI thread");
        PluginHost current = host;
        return current == null ? Map.of() : current.shellContributions.variables(jasperEnvironment);
    }

    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {''')

launch = app + 'launch/LaunchSettings.java'
edit(launch, r'''    /** {@code integrationDir} is the extracted script directory, or null when extraction failed or tests want none. */
    public static LaunchSettings resolve(ConfigSnapshot snapshot, ShellProfile profile, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        TerminalConfig terminal = snapshot.terminal();''', r'''    /** {@code integrationDir} is the extracted script directory, or null when extraction failed or tests want none. */
    public static LaunchSettings resolve(ConfigSnapshot snapshot, ShellProfile profile, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        return resolve(snapshot, profile, osName, inherited, windowColumns, windowLines, integrationDir, environment -> Map.of());
    }

    /**
     * As above, with what plugins add ({@code contributions}, given the untouched {@code inherited} map): after Jasper's
     * own variables and before {@code [terminal.env]}, so a configured entry, {@code TERM} and {@code COLORTERM} still
     * win. A WSL profile gets none: only the variables {@code WSLENV} names reach Linux.
     */
    public static LaunchSettings resolve(ConfigSnapshot snapshot, ShellProfile profile, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir,
                                  Function<Map<String, String>, Map<String, String>> contributions) {
        TerminalConfig terminal = snapshot.terminal();''')
edit(launch, '        environment.putAll(terminal.env());\n',
     '        if (profile.kind() != ShellProfile.Kind.WSL) environment.putAll(contributions.apply(inherited));\n        environment.putAll(terminal.env());\n')

application = app + 'application/JasperApplication.java'
edit(application, r'''                                        BooleanSupplier awaitDetection, Consumer<String> problems,
                                        Function<UUID, Map<String, String>> paneVariables) {
        ConfigSnapshot initial = snapshots.get();''', r'''                                        BooleanSupplier awaitDetection, Consumer<String> problems,
                                        Function<UUID, Map<String, String>> paneVariables) {
        return windowLauncher(executor, snapshots, start, integrationDir, profiles, awaitDetection, problems, paneVariables,
            inherited -> Map.of());
    }

    /**
     * As above; a local shell also gets what plugins add ({@code contributions}, given Jasper's own environment, on the
     * EDT) after Jasper's own variables and before {@code [terminal.env]}. A PowerShell 7 launch confirmed after
     * detection resolves again, so its contributions are asked twice.
     */
    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir,
                                        BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution> profiles,
                                        BooleanSupplier awaitDetection, Consumer<String> problems,
                                        Function<UUID, Map<String, String>> paneVariables,
                                        Function<Map<String, String>, Map<String, String>> contributions) {
        ConfigSnapshot initial = snapshots.get();''')
edit(application, r'''            return LaunchSettings.resolve(snapshot, resolution.profile(), os, environment, initial.columns(), initial.lines(), integrationDir);''',
     r'''            return LaunchSettings.resolve(snapshot, resolution.profile(), os, environment, initial.columns(), initial.lines(), integrationDir,
                contributions);''')
edit(application, r'''            () -> shellProfiles.awaitDetection(DETECTION_WAIT), this::shellProfileProblem, this::paneVariables);''',
     r'''            () -> shellProfiles.awaitDetection(DETECTION_WAIT), this::shellProfileProblem, this::paneVariables, this::pluginShellVariables);''')
edit(application, r'''    /** What a new local shell in {@code pane} gets last, over everything else. Any thread. */''',
     r'''    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */
    private Map<String, String> pluginShellVariables(Map<String, String> inherited) {
        PluginRuntime runtime = plugins;
        return runtime == null ? Map.of() : runtime.shellVariables(inherited);
    }

    /** What a new local shell in {@code pane} gets last, over everything else. Any thread. */''')

edit(testkit + 'FakePluginContext.java', '    void requireOpen() {\n', '''    /** The host this context belongs to, for the fakes that keep host-wide registries. */
    FakePluginHost host() { return host; }

    void requireOpen() {
''')

fake_terminals = testkit + 'FakeTerminals.java'
edit(fake_terminals, 'import java.util.List;\n', 'import java.util.List;\nimport java.util.Map;\n')
edit(fake_terminals, 'import java.util.UUID;\n', 'import java.util.UUID;\nimport java.util.function.Function;\n')
edit(fake_terminals, r'''    /** The default shell, or a profile the test listed; anything else opens nothing, as in the application. */''',
r'''    @Override public Subscription environment(Function<Map<String, String>, Map<String, String>> contributor) {
        Objects.requireNonNull(contributor, "contributor");
        require(Capabilities.TERMINAL_ENVIRONMENT);
        context.requireOpen();
        return context.host().contributeShell(context, contributor);
    }

    /** The default shell, or a profile the test listed; anything else opens nothing, as in the application. */''')

fake_host = testkit + 'FakePluginHost.java'
edit(fake_host, '        context.commandLine.closeAll();\n',
     '        context.commandLine.closeAll();\n        shellContributors.removeIf(entry -> entry.context() == context);\n')
append_to_class(fake_host, r'''
    /** A {@code Terminals.environment} contributor and the plugin that registered it. */
    record ShellContributor(FakePluginContext context, Function<Map<String, String>, Map<String, String>> contributor) { }
    final List<ShellContributor> shellContributors = new CopyOnWriteArrayList<>();
    private static final java.util.regex.Pattern SHELL_NAME = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> RESERVED_SHELL_NAMES = Set.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION");

    /** Keeps a contributor until its subscription closes or its plugin stops. */
    Subscription contributeShell(FakePluginContext context, Function<Map<String, String>, Map<String, String>> contributor) {
        var entry = new ShellContributor(context, contributor);
        shellContributors.add(entry);
        Subscription subscription = () -> shellContributors.removeIf(registered -> registered == entry);
        context.own(subscription);
        return subscription;
    }

    /**
     * What plugins' {@code Terminals.environment} contributors add to a new local shell, merged as the application merges
     * them: contributors run plugin by plugin in id order, then in registration order, each given a read-only copy of
     * {@code jasperEnvironment}; one that throws adds nothing (recorded in {@link #failures()}) and a null map counts as
     * empty. A name outside {@code [A-Za-z_][A-Za-z0-9_]*}, {@code TERM}, {@code COLORTERM}, {@code TERM_PROGRAM},
     * {@code TERM_PROGRAM_VERSION}, a name starting {@code JASPER_}, a null value and a value holding NUL are dropped, and
     * the first contributor of a name wins.
     *
     * @param jasperEnvironment Jasper's own environment
     * @return the variables, which the application puts after its own and before {@code [terminal.env]}
     * @since 0.17.1
     */
    public Map<String, String> shellEnvironment(Map<String, String> jasperEnvironment) {
        Map<String, String> environment = Map.copyOf(jasperEnvironment);
        List<ShellContributor> ordered = new ArrayList<>(shellContributors);
        ordered.sort(Comparator.comparing((ShellContributor entry) -> entry.context().plugin().id()));
        Map<String, String> merged = new LinkedHashMap<>();
        for (ShellContributor entry : ordered) {
            String pluginId = entry.context().plugin().id();
            List<Map.Entry<String, String>> offered = new ArrayList<>();
            try {
                Map<String, String> added = entry.contributor().apply(environment);
                if (added != null) added.forEach((name, value) -> offered.add(new java.util.AbstractMap.SimpleImmutableEntry<>(name, value)));
            } catch (RuntimeException failure) {
                failures.add(pluginId + " shell environment: " + failure);
                continue;
            }
            for (Map.Entry<String, String> variable : offered)
                if (keptShellVariable(variable.getKey(), variable.getValue())) merged.putIfAbsent(variable.getKey(), variable.getValue());
        }
        return Map.copyOf(merged);
    }

    /** Whether the application keeps a contributed entry. */
    private static boolean keptShellVariable(String name, String value) {
        return name != null && SHELL_NAME.matcher(name).matches() && !RESERVED_SHELL_NAMES.contains(name) && !name.startsWith("JASPER_")
            && value != null && value.indexOf('\0') < 0;
    }
''')

edit('jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java',
     '            @Override public void close() { host.close(); }\n',
     '''            @Override public java.util.Map<String, String> shellVariables(java.util.Map<String, String> jasperEnvironment) {
                return host.shellEnvironment(jasperEnvironment);
            }
            @Override public void close() { host.close(); }
''')
edit('jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java',
     '            @Override public void close() { stopAll(); flush(); }\n',
     '''            @Override public Map<String, String> shellVariables(Map<String, String> jasperEnvironment) {
                return onEdtValue(() -> host.shellContributions.variables(jasperEnvironment));
            }
            @Override public void close() { stopAll(); flush(); }
''')

with open('docs/design/theme-engine/migration.csv', 'a', encoding='utf-8') as csv:
    csv.write('jasper-app/src/main/java/dev/jasper/app/plugins/ShellContributions.java,app.plugins,ShellContributions,nonvisual-helper,none,none,'
              'jasper-app/src/test/java/dev/jasper/app/plugins/ShellContributionsTest.java,New in the default editor plan Task 2; pure registry '
              'of plugin shell variables with no Swing or theme dependency; matched only by collection construction\n')
    csv.write('jasper-app/src/test/java/dev/jasper/app/plugins/ShellContributionsTest.java,app.plugins,ShellContributionsTest,nonvisual-helper,none,none,'
              'jasper-app/src/test/java/dev/jasper/app/plugins/ShellContributionsTest.java,Test/fixture ownership; New in the default editor plan '
              'Task 2; no Swing or theme dependency\n')
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.launch.LaunchSettingsTest' --tests 'dev.jasper.app.application.ShellProfileLaunchTest' --tests 'dev.jasper.app.workspace.ShellLauncherTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS, the three new contract tests on both hosts included.
Then run: `./gradlew verifySdkArchitecture verifyApplicationArchitecture`
Expected: PASS (`ShellContributions` is in `dev.jasper.app.plugins`; `LaunchSettings` and `JasperApplication` see only JDK
functions).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/ShellContributions.java jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/main/java/dev/jasper/app/launch/LaunchSettings.java \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/ShellContributionsTest.java jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java \
  jasper-app/src/test/java/dev/jasper/app/launch/LaunchSettingsTest.java jasper-app/src/test/java/dev/jasper/app/application/ShellProfileLaunchTest.java \
  jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java \
  jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java \
  jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Plugins add variables to new local shells through Terminals.environment

ShellContributions calls each plugin's contributors on the UI thread, by
plugin id, inside containment; it drops invalid, reserved and NUL entries,
keeps the first plugin's value for a name and logs each problem once.
LaunchSettings puts the result after Jasper's own variables and before
[terminal.env], never for WSL. The testkit fake merges the same way, and
both hosts pass the contract.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Program tabs (`LocalSpec.command`) end to end

**Files:**
- Modify: `app/terminals/OpenSpec.java`, `app/plugins/HostedTerminals.java`,
  `app/workspace/{WindowTerminals,WindowContent,TerminalTab,TerminalPane}.java`, `app/launch/{ShellLauncher,LaunchSettings}.java`,
  `testkit/{FakeTerminals,FakePluginHost}.java`, `contract/{ContractHarness,PluginContractTest}.java`
- Test: `app-test/launch/LaunchSettingsTest.java`, `app-test/workspace/{ShellLauncherTest,ShellExitBehaviorTest,ShellProfilePaneTest}.java`,
  `app-test/plugins/TerminalFixture.java`, the contract

**Interfaces:**
- Consumes: `LocalSpec.command()`, `LocalSpec.running` (Task 1).
- Produces (app): `record OpenSpec.Local(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program)`
  (the 1- and 2-argument constructors kept); `LaunchSettings.withCommand(List<String> program)`; package-private
  `LaunchSettings.programName(String)`; `ShellLauncher.launch(Optional<Path> directory, Optional<String> profile,
  Optional<List<String>> program, Optional<UUID> pane, Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion)`
  → `Launch`; `TerminalPane(Path, ShellLauncher, SessionRequest, Optional<String>, Optional<List<String>> program)`;
  `TerminalTab(Path, ShellLauncher, SessionRequest, Optional<String>, Optional<List<String>>)`;
  `TerminalTab.split(TerminalPane, SplitTree.Axis, Path, SessionRequest, Optional<String>, Optional<List<String>>)`;
  `WindowContent.openTab(Path, SessionRequest, Optional<String>, Optional<List<String>>)`.
- Produces (testkit and the app's contract fixture): `openRequests()` lines gain `|run:<argv joined by spaces>` after
  the profile part for a program tab or split.

How a program pane starts (`ShellLauncher`): the launch captures the default profile's settings on the EDT as for a shell
(contributions included), so `Launch.profile()` names that profile and a user's split of the pane starts its shell; the
label is the program's file name. Off the EDT it starts in `directory.orElse(home)`, which must be a directory, with
`chosen.withCommand(program)` plus the pane variables. It never calls `at()`, because a WSL profile's `--cd` must not be
appended to a program. A launcher without settings (the benchmark's, `Rehearsal`'s) completes with
`IllegalStateException("This window cannot run a program")`. A program pane's exit policy is pinned to
`CLOSE_ON_SUCCESS`; `setShellExitBehavior`, which `WindowContent.configurePane` and `WorkspaceConfiguration` call, leaves
it alone.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new, count=1):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == count, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')
contract = 'jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/'
app = 'jasper-app/src/test/java/dev/jasper/app/'

edit(contract + 'ContractHarness.java', r'''     * each followed by {@code |<profile id>} when the request named a shell profile.''',
     r'''     * each followed by {@code |<profile id>} when the request named a shell profile, then by
     * {@code |run:<the command's words joined by spaces>} when it runs a program.''')
append_to_class(contract + 'PluginContractTest.java', r'''
    @Test void aTabOrASplitMayRunAProgramAndThatNeedsTerminalOpen() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        Path here = Path.of("/tmp").toAbsolutePath();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle editor = terminals.openTab(terminals.window(window).orElseThrow(),
                OpenRequest.local(dev.jasper.sdk.terminal.LocalSpec.running(List.of("nvim", "+3", "notes.txt"), Optional.of(here)))).orElseThrow();
            assertThat(editor.isOpen()).isTrue();
            terminals.split(terminals.pane(only).orElseThrow(), Direction.RIGHT,
                OpenRequest.local(dev.jasper.sdk.terminal.LocalSpec.running(List.of("htop"), Optional.empty()))).orElseThrow();
            var others = bare.get().terminals();
            assertThatThrownBy(() -> others.openTab(others.window(window).orElseThrow(),
                    OpenRequest.local(dev.jasper.sdk.terminal.LocalSpec.running(List.of("htop"), Optional.empty()))))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_OPEN));
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|" + here + "|run:nvim +3 notes.txt", "split|" + only + "|RIGHT|-|run:htop");
    }
''')

edit(app + 'plugins/TerminalFixture.java', r'''                            + local.profile().map(profile -> "|" + profile).orElse(""));''',
     r'''                            + local.profile().map(profile -> "|" + profile).orElse("")
                            + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));''')
edit(app + 'plugins/TerminalFixture.java', r'''                        + local.profile().map(profile -> "|" + profile).orElse(""));''',
     r'''                        + local.profile().map(profile -> "|" + profile).orElse("")
                        + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));''')

edit(app + 'workspace/ShellProfilePaneTest.java', r'''        assertThatNullPointerException().isThrownBy(() -> new OpenSpec.Local(Optional.empty(), null));''',
     r'''        assertThatNullPointerException().isThrownBy(() -> new OpenSpec.Local(Optional.empty(), null));
        assertThat(new OpenSpec.Local(Optional.empty(), Optional.of("wsl:Ubuntu")).program()).as("the old constructors run the shell").isEmpty();
        var words = new ArrayList<>(List.of("nvim", "+3"));
        var local = new OpenSpec.Local(Optional.empty(), Optional.empty(), Optional.of(words));
        words.add("later");
        assertThat(local.program()).as("copied").contains(List.of("nvim", "+3"));
        assertThatNullPointerException().isThrownBy(() -> new OpenSpec.Local(Optional.empty(), Optional.empty(), null));''')

append_to_class(app + 'launch/LaunchSettingsTest.java', r'''
    @Test void aProgramKeepsTheShellsEnvironmentAndProfileAndIsNamedAfterItsFile() {
        var settings = LaunchSettings.resolve(withShell("/bin/zsh", List.of("-l"), ShellIntegrationMode.AUTO), "Mac OS X",
            Map.of("PATH", "/bin"), 150, 45, Path.of("/opt/jasper/si"));
        var program = settings.withCommand(List.of("/opt/homebrew/bin/nvim", "+42", "config.toml"));
        assertThat(program.command()).containsExactly("/opt/homebrew/bin/nvim", "+42", "config.toml");
        assertThat(program.environment()).isEqualTo(settings.environment());
        assertThat(program.profile()).isEqualTo(settings.profile());
        assertThat(program.columns()).isEqualTo(150);
        assertThat(program.label()).isEqualTo("nvim");
        assertThat(LaunchSettings.programName("/usr/bin/vim")).isEqualTo("vim");
        assertThat(LaunchSettings.programName("")).isEqualTo("shell");
        assertThatIllegalArgumentException().isThrownBy(() -> settings.withCommand(List.of()));
    }
''')

append_to_class(app + 'workspace/ShellLauncherTest.java', r'''
    @Test void aProgramRunsInsteadOfTheShellWithTheShellsEnvironmentAndThePanesVariables() throws Exception {
        var configured = snapshot("/bin/zsh", List.of("-l"), Map.of("VALUE", "configured"), 12, 150, 45);
        var expected = LaunchSettings.resolve(configured, "Mac OS X", Map.of("PATH", "/bin"), 150, 45);
        Queue<Runnable> pending = new ArrayDeque<>();
        var received = new ArrayList<Map.Entry<Path, LaunchSettings>>();
        var errors = new ArrayList<Throwable>();
        UUID pane = UUID.randomUUID();
        var launched = new AtomicReference<ShellLauncher.Launch>();
        ShellLauncher launcher = new ShellLauncher(pending::add, requested -> LaunchSettings.resolve(configured, "Mac OS X", Map.of("PATH", "/bin"), 150, 45),
            (requested, captured) -> captured, null, (path, settings) -> {
                received.add(Map.entry(path, settings));
                throw new IllegalStateException("controlled launch boundary");
            }, id -> Map.of("JASPER_PANE", id.toString()));
        edt(() -> launched.set(launcher.launch(Optional.of(directory), Optional.empty(), Optional.of(List.of("/opt/tools/nvim", "+3", "notes.txt")),
            Optional.of(pane), replaced -> { }, (session, failure) -> errors.add(failure))));
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        assertThat(launched.get().label()).as("the tab is named after the program").isEqualTo("nvim");
        assertThat(launched.get().profile()).as("the default profile, which a split of the pane starts").contains(expected.profile().id());
        assertThat(received).singleElement().satisfies(entry -> {
            assertThat(entry.getKey()).isEqualTo(directory);
            assertThat(entry.getValue().command()).containsExactly("/opt/tools/nvim", "+3", "notes.txt");
            assertThat(entry.getValue().environment()).containsEntry("VALUE", "configured").containsEntry("PATH", "/bin")
                .containsEntry("TERM", "xterm-256color").containsEntry("JASPER_PANE", pane.toString());
        });
        assertThat(errors).singleElement().satisfies(error -> assertThat(error).hasMessage("controlled launch boundary"));
    }

    @Test void aProgramStartsInItsDirectoryWithoutAWslProfilesCd() throws Exception {
        var wsl = new dev.jasper.app.launch.ShellProfile("wsl:Ubuntu", "Ubuntu", dev.jasper.app.launch.ShellProfile.Kind.WSL,
            Optional.of("WSL 2"), List.of("wsl.exe", "-d", "Ubuntu"));
        Queue<Runnable> pending = new ArrayDeque<>();
        var received = new ArrayList<Map.Entry<Path, LaunchSettings>>();
        ShellLauncher launcher = new ShellLauncher(pending::add,
            requested -> LaunchSettings.resolve(ConfigSnapshot.defaults(), wsl, "Windows 11", Map.of(), 150, 45, null),
            (requested, captured) -> captured, null, (path, settings) -> {
                received.add(Map.entry(path, settings));
                throw new IllegalStateException("controlled launch boundary");
            });
        Path home = Path.of(System.getProperty("user.home"));
        edt(() -> {
            launcher.launch(Optional.of(directory), Optional.empty(), Optional.of(List.of("notepad.exe", "notes.txt")), Optional.empty(),
                replaced -> { }, (session, failure) -> { });
            launcher.launch(Optional.empty(), Optional.empty(), Optional.of(List.of("notepad.exe")), Optional.empty(),
                replaced -> { }, (session, failure) -> { });
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        assertThat(received).extracting(Map.Entry::getKey).as("the requested directory, else home").containsExactly(directory, home);
        assertThat(received).extracting(entry -> entry.getValue().command())
            .containsExactly(List.of("notepad.exe", "notes.txt"), List.of("notepad.exe"));
    }

    @Test void aLauncherWithoutSettingsCannotRunAProgramAndAMissingDirectoryFails() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var errors = new ArrayList<Throwable>();
        var bare = new ShellLauncher(pending::add, path -> { throw new AssertionError("nothing starts"); }, "sh");
        edt(() -> assertThat(bare.launch(Optional.of(directory), Optional.empty(), Optional.of(List.of("nvim")), Optional.empty(),
            replaced -> { }, (session, failure) -> errors.add(failure)).label()).isEqualTo("sh"));
        edt(() -> { });
        assertThat(pending).as("nothing was scheduled").isEmpty();
        assertThat(errors).singleElement().satisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class)
            .hasMessage("This window cannot run a program"));
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);
        edt(() -> new ShellLauncher(pending::add, () -> settings, (p, s) -> { throw new AssertionError("nothing starts"); })
            .launch(Optional.of(directory.resolve("missing")), Optional.empty(), Optional.of(List.of("nvim")), Optional.empty(),
                replaced -> { }, (session, failure) -> errors.add(failure)));
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        assertThat(errors.getLast()).hasMessageContaining("Directory is unavailable");
    }
''')

append_to_class(app + 'workspace/ShellExitBehaviorTest.java', r'''
    private static final String PROGRAM_SCRIPT = "stty -echo; printf 'READY\\n'; read code; exit \"$code\"";
    /** A program tab's command; the trailing word is only the script's $0, which tells the launches apart. */
    private static final List<String> PROGRAM = List.of("/bin/sh", "-c", PROGRAM_SCRIPT, "program");
    final List<dev.jasper.app.launch.LaunchSettings> started = new CopyOnWriteArrayList<>();

    /** As {@link #start}, with a launcher that resolves settings, so a tab can run a program; the shell runs the same script. */
    private void startWithSettings(String policy) throws Exception {
        Files.writeString(directory.resolve("config.toml"), "terminal.on_exit='" + policy + "'");
        service = new ConfigService(directory.resolve("config.toml"), false);
        var shell = dev.jasper.app.testsupport.ConfigTestSupport.snapshot("/bin/sh", List.of("-c", PROGRAM_SCRIPT, "shell"), Map.of(), 100, 80, 24);
        edt(() -> {
            ThemeController themes = ThemeTestSupport.controller(true);
            controller = new ConfigurationTestSupport(themes, service);
            ShellLauncher launcher = new ShellLauncher(pending::add,
                requested -> dev.jasper.app.launch.LaunchSettings.resolve(shell, "Linux", System.getenv(), 80, 24),
                (requested, captured) -> captured, null, (path, settings) -> {
                    started.add(settings);
                    try {
                        var session = TerminalSession.start(SessionLaunchOptions.builder().command(settings.command())
                            .environment(settings.environment()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
                        sessions.add(session);
                        return session;
                    } catch (Exception failure) { throw new CompletionException(failure); }
                });
            owner = new WindowContent(launcher, directory, path -> {}, () -> {}, () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); empty.incrementAndGet();
            }, themes);
            owner.onError = (title, message) -> failures.add(message);
            controller.register(owner);
        });
    }

    @Test void aProgramTabClosesWhenItsProgramSucceedsWhateverOnExitSays() throws Exception {
        startWithSettings("keep_open"); deliver();
        TerminalTab shellTab = owner.currentTab();
        edt(() -> owner.openTab(directory, null, Optional.empty(), Optional.of(PROGRAM))); deliver();
        TerminalPane program = owner.currentPane();
        assertThat(started.getLast().command()).isEqualTo(PROGRAM);
        edt(() -> exit(program, 0));
        until(() -> owner.tabStrip().getTabCount() == 1);
        edt(() -> {
            assertThat(owner.currentTab()).isSameAs(shellTab);
            assertThat(empty).hasValue(0);
        });
    }

    @Test void aFailedProgramStaysOpenAndAnOnExitChangeDoesNotUnpinIt() throws Exception {
        startWithSettings("keep_open"); deliver();
        TerminalPane shell = owner.currentPane();
        edt(() -> owner.openTab(directory, null, Optional.empty(), Optional.of(PROGRAM))); deliver();
        TerminalPane program = owner.currentPane();
        reload("close");
        edt(() -> exit(program, 1));
        edt(() -> { });
        edt(() -> {
            assertThat(owner.tabStrip().getTabCount()).as("terminal.on_exit = close does not reach a program tab").isEqualTo(2);
            assertThat(program.running()).isFalse();
            assertThat(program.view().find(new SearchQuery("[process exited with code 1]", false, true)).count()).isEqualTo(1);
        });
        edt(() -> exit(shell, 1));
        until(() -> owner.tabStrip().getTabCount() == 1);
        edt(() -> assertThat(owner.currentPane()).as("the reload did reach the shell tab").isSameAs(program));
    }

    @Test void pluginRequestsRunProgramsInTabsAndSplitsThroughTheRegistry() throws Exception {
        startWithSettings("keep_open");
        var registry = new java.util.concurrent.atomic.AtomicReference<dev.jasper.app.terminals.TerminalRegistry>();
        edt(() -> { registry.set(new dev.jasper.app.terminals.TerminalRegistry()); owner.connectTerminals(registry.get(), () -> { }); });
        deliver();
        List<String> split = List.of("/bin/sh", "-c", PROGRAM_SCRIPT, "split");
        edt(() -> assertThat(registry.get().window(owner.id()).orElseThrow().openTab()
            .apply(new dev.jasper.app.terminals.OpenSpec.Local(Optional.of(directory), Optional.empty(), Optional.of(PROGRAM)))).isPresent());
        deliver();
        edt(() -> {
            var pane = registry.get().window(owner.id()).orElseThrow().selectedTab().get().orElseThrow().focusedPane().get().orElseThrow();
            assertThat(pane.split().apply(dev.jasper.app.terminals.SplitAxis.RIGHT,
                new dev.jasper.app.terminals.OpenSpec.Local(Optional.empty(), Optional.empty(), Optional.of(split)))).isPresent();
        });
        deliver();
        assertThat(started).extracting(dev.jasper.app.launch.LaunchSettings::command)
            .containsExactly(List.of("/bin/sh", "-c", PROGRAM_SCRIPT, "shell"), PROGRAM, split);
    }
''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: FAIL: `aTabOrASplitMayRunAProgramAndThatNeedsTerminalOpen`, whose open requests lack the `|run:` part.
Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation fails: cannot find symbol `OpenSpec.Local.program()`, its 3-argument constructor,
`LaunchSettings.withCommand`, `LaunchSettings.programName`, the 6-argument `ShellLauncher.launch` and the 4-argument
`WindowContent.openTab`.

- [ ] **Step 3: Carry the program from the request to the launch**

`app/launch/ShellLauncher.java` (whole file):

```java
package dev.jasper.app.launch;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Captures request settings before dispatch; every completion is delivered on the EDT. */
public final class ShellLauncher {
    private final Executor executor;
    private final Function<Optional<String>, LaunchSettings> settings;
    private final BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm;
    private final Supplier<SessionDefaults> defaults;
    private final BiFunction<Path, LaunchSettings, TerminalSession> start;
    private final Function<UUID, Map<String, String>> variables;
    private final String label;

    /**
     * What a launch request started with.
     *
     * @param label the pane's shell label
     * @param profile the profile id the settings resolved to; empty when the launcher captures no settings
     */
    public record Launch(String label, Optional<String> profile) { }

    public ShellLauncher(Executor executor, Function<Path, TerminalSession> start, String label) {
        this.executor = executor;
        this.settings = null;
        this.confirm = (requested, captured) -> captured;
        this.defaults = null;
        this.start = (directory, captured) -> start.apply(directory);
        this.variables = pane -> Map.of();
        this.label = label;
    }

    /** Every request gets {@code settings}, whatever profile it asks for. */
    public ShellLauncher(Executor executor, Supplier<LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, profileless(Objects.requireNonNull(settings)), start);
    }

    /** {@code settings} resolves a requested profile id (empty: the user's default) on the EDT when a launch is requested. */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, settings, (requested, captured) -> captured, null, start);
    }

    /**
     * As above, and before starting, off the EDT on {@code executor}, {@code confirm} gets the requested id and the
     * captured settings and returns the settings to start (the same ones, or a replacement, for example once detection
     * shows a trusted profile is missing); it may block for a bounded time. {@code defaults} gives
     * {@link #sessionDefaults} on the EDT; null derives them from {@code settings}.
     */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start) {
        this(executor, settings, confirm, defaults, start, pane -> Map.of());
    }

    /** As above; a pane's shell also gets {@code variables} for that pane, last, over everything else. */
    public ShellLauncher(Executor executor, Function<Optional<String>, LaunchSettings> settings,
                  BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm, Supplier<SessionDefaults> defaults,
                  BiFunction<Path, LaunchSettings, TerminalSession> start, Function<UUID, Map<String, String>> variables) {
        this.executor = executor;
        this.settings = Objects.requireNonNull(settings);
        this.confirm = Objects.requireNonNull(confirm);
        this.defaults = defaults;
        this.start = start;
        this.variables = Objects.requireNonNull(variables);
        this.label = "shell";
    }

    private static Function<Optional<String>, LaunchSettings> profileless(Supplier<LaunchSettings> settings) {
        return requested -> settings.get();
    }

    public String label() { return label; }

    /** The grid and scrollback a session starts with when no view has measured the pane yet. */
    public record SessionDefaults(int columns, int lines, int scrollback) { }

    /** From the current launch settings; a conventional terminal when there are none. EDT, like {@link #launch}. */
    public SessionDefaults sessionDefaults() {
        try {
            if (defaults != null) return Objects.requireNonNull(defaults.get());
            LaunchSettings captured = settings == null ? null : settings.apply(Optional.empty());
            if (captured != null) return new SessionDefaults(captured.columns(), captured.lines(), captured.scrollback());
        } catch (RuntimeException unavailable) { /* fall through */ }
        return new SessionDefaults(80, 24, 10_000);
    }

    /** The user's default profile in {@code directory}; returns the pane's label. */
    public String launch(Path directory, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, Optional.empty(), completion).label();
    }

    /** Starts {@code profile} (empty: the user's default) in {@code directory}; settings are captured now, on the EDT. */
    public Launch launch(Path directory, Optional<String> profile, BiConsumer<TerminalSession, Throwable> completion) {
        return launch(Optional.of(directory), profile, replaced -> { }, completion);
    }

    /**
     * Starts {@code profile} (empty: the user's default) in {@code directory}, where empty means nobody asked for one:
     * the shell starts where a new shell starts (the user's home, which a WSL profile reads as its Linux home).
     * Settings are captured now, on the EDT. When confirmation replaces them, {@code replaced} hears what now starts,
     * on the EDT and before {@code completion}.
     */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Consumer<Launch> replaced,
                         BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, profile, Optional.empty(), replaced, completion);
    }

    /** As above for {@code pane}, whose shell also gets the pane's variables. */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Optional<UUID> pane, Consumer<Launch> replaced,
                         BiConsumer<TerminalSession, Throwable> completion) {
        return launch(directory, profile, Optional.empty(), pane, replaced, completion);
    }

    /**
     * As above; a present {@code program} runs instead of the shell, with the profile's environment and the pane's
     * variables, in {@code directory} (empty: the user's home), which must exist; a WSL profile's {@code --cd} is not
     * added. The launch's label is the program's file name. A launcher without settings cannot run a program:
     * {@code completion} gets an {@link IllegalStateException}.
     */
    public Launch launch(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program, Optional<UUID> pane,
                         Consumer<Launch> replaced, BiConsumer<TerminalSession, Throwable> completion) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(replaced, "replaced");
        if (program.isPresent() && settings == null) {
            SwingUtilities.invokeLater(() -> completion.accept(null, new IllegalStateException("This window cannot run a program")));
            return new Launch(label, Optional.empty());
        }
        final LaunchSettings captured;
        try {
            captured = settings == null ? null : Objects.requireNonNull(settings.apply(profile));
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
            return new Launch(label, Optional.empty());
        }
        Launch launched = captured == null ? new Launch(label, Optional.empty()) : launchOf(captured, program);
        Path home = Path.of(System.getProperty("user.home"));
        try {
            executor.execute(() -> {
                TerminalSession session = null;
                Throwable failure = null;
                try {
                    if (captured == null) {
                        Path target = directory.orElse(home);
                        if (!Files.isDirectory(target)) {
                            throw new IllegalArgumentException("Directory is unavailable: " + target);
                        }
                        session = start.apply(target, null);
                    } else {
                        LaunchSettings chosen = Objects.requireNonNull(confirm.apply(profile, captured));
                        Launch now = launchOf(chosen, program);
                        if (!now.equals(launched)) SwingUtilities.invokeLater(() -> replaced.accept(now));
                        Path where;
                        LaunchSettings resolved;
                        if (program.isPresent()) {
                            // Never at(): a WSL profile's --cd belongs to wsl.exe, not to the program.
                            where = directory.orElse(home);
                            if (!Files.isDirectory(where)) throw new IllegalArgumentException("Directory is unavailable: " + where);
                            resolved = chosen.withCommand(program.get());
                        } else {
                            LaunchSettings.Start target = chosen.at(directory, home, Files::isDirectory);
                            where = target.directory();
                            resolved = target.settings();
                        }
                        LaunchSettings started = pane.isEmpty() ? resolved : resolved.withVariables(variables.apply(pane.get()));
                        session = start.apply(where, started);
                    }
                } catch (Exception e) { failure = e; }
                TerminalSession result = session;
                Throwable error = failure;
                SwingUtilities.invokeLater(() -> completion.accept(result, error));
            });
        } catch (RuntimeException failure) {
            SwingUtilities.invokeLater(() -> completion.accept(null, failure));
        }
        return launched;
    }

    private static Launch launchOf(LaunchSettings settings, Optional<List<String>> program) {
        String name = program.map(command -> LaunchSettings.programName(command.getFirst())).orElseGet(settings::label);
        return new Launch(name, Optional.of(settings.profile().id()));
    }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new, count=1):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == count, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
app = 'jasper-app/src/main/java/dev/jasper/app/'
testkit = 'jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/'

spec = app + 'terminals/OpenSpec.java'
edit(spec, 'import java.nio.file.Path;\n', 'import java.nio.file.Path;\nimport java.util.List;\n')
edit(spec, r'''    /**
     * The user's shell, in a directory or where New Tab would start.
     *
     * @param directory where it starts; empty means where New Tab would
     * @param profile the shell profile id; empty means the user's default
     */
    record Local(Optional<Path> directory, Optional<String> profile) implements OpenSpec {
        public Local {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(profile, "profile");
        }

        /** The user's default shell. */
        public Local(Optional<Path> directory) { this(directory, Optional.empty()); }
    }''', r'''    /**
     * The user's shell, in a directory or where New Tab would start, or a program instead of it.
     *
     * @param directory where it starts; empty means where New Tab would
     * @param profile the shell profile id; empty means the user's default
     * @param program the program and its arguments, run instead of the shell with its environment; empty runs the shell
     */
    record Local(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program) implements OpenSpec {
        public Local {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(profile, "profile");
            program = Objects.requireNonNull(program, "program").map(List::copyOf);
        }

        /** A shell profile. */
        public Local(Optional<Path> directory, Optional<String> profile) { this(directory, profile, Optional.empty()); }

        /** The user's default shell. */
        public Local(Optional<Path> directory) { this(directory, Optional.empty(), Optional.empty()); }
    }''')

edit(app + 'plugins/HostedTerminals.java',
     r'''yield new OpenSpec.Local(local.spec().workingDirectory(), local.spec().profile()); }''',
     r'''yield new OpenSpec.Local(local.spec().workingDirectory(), local.spec().profile(), local.spec().command()); }''')

window_terminals = app + 'workspace/WindowTerminals.java'
edit(window_terminals, r'''case OpenSpec.Local local -> tab.split(pane, direction, local.directory().orElse(null), null, local.profile());''',
     r'''case OpenSpec.Local local -> tab.split(pane, direction, local.directory().orElse(null), null, local.profile(), local.program());''')
edit(window_terminals, r'''case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory), null, local.profile());''',
     r'''case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory), null, local.profile(), local.program());''')

edit(app + 'workspace/WindowContent.java', r'''    /** As above; {@code profile} names a local first pane's shell profile, empty for the user's default. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull, Optional<String> profile) {
        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher, requestOrNull, profile);''', r'''    /** As above; {@code profile} names a local first pane's shell profile, empty for the user's default. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull, Optional<String> profile) {
        return openTab(directory, requestOrNull, profile, Optional.empty());
    }

    /** As above; {@code program} runs in the local first pane instead of its shell. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull, Optional<String> profile, Optional<java.util.List<String>> program) {
        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher, requestOrNull, profile, program);''')

tab = app + 'workspace/TerminalTab.java'
edit(tab, r'''    TerminalTab(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile) {
        super(new BorderLayout());
        this.launcher = launcher;
        TerminalPane pane = createPane(directory, requestOrNull, profile);''', r'''    TerminalTab(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile) {
        this(directory, launcher, requestOrNull, profile, Optional.empty());
    }

    /** As above; {@code program} runs in the first pane instead of its shell. */
    TerminalTab(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile,
                Optional<java.util.List<String>> program) {
        super(new BorderLayout());
        this.launcher = launcher;
        TerminalPane pane = createPane(directory, requestOrNull, profile, program);''')
edit(tab, r'''    private TerminalPane createPane(Path directory, SessionRequest requestOrNull, Optional<String> profile) {
        TerminalPane pane = new TerminalPane(directory, launcher, requestOrNull, profile);''', r'''    private TerminalPane createPane(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                    Optional<java.util.List<String>> program) {
        TerminalPane pane = new TerminalPane(directory, launcher, requestOrNull, profile, program);''')
edit(tab, r'''    TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull, SessionRequest requestOrNull, Optional<String> profile) {
        if (closed || target == null || panes.get(target.id()) != target || !target.running()) return null;
        if (focusedPane() != target) focus(target);
        TerminalPane pane = createPane(directoryOrNull == null ? target.inheritableDirectory() : directoryOrNull, requestOrNull, profile);''',
r'''    TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull, SessionRequest requestOrNull, Optional<String> profile) {
        return split(target, axis, directoryOrNull, requestOrNull, profile, Optional.empty());
    }

    /** As above; {@code program} runs in the new local pane instead of its shell. */
    TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull, SessionRequest requestOrNull, Optional<String> profile,
                       Optional<java.util.List<String>> program) {
        if (closed || target == null || panes.get(target.id()) != target || !target.running()) return null;
        if (focusedPane() != target) focus(target);
        TerminalPane pane = createPane(directoryOrNull == null ? target.inheritableDirectory() : directoryOrNull, requestOrNull, profile, program);''')

pane = app + 'workspace/TerminalPane.java'
edit(pane, r'''    private Optional<String> launchedProfile = Optional.empty();
''', r'''    private Optional<String> launchedProfile = Optional.empty();
    /** The program this local pane runs instead of the shell; such a pane closes when it exits with status 0. */
    private final Optional<java.util.List<String>> program;
''')
edit(pane, r'''    TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile) {
        super(new BorderLayout());''', r'''    TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile) {
        this(directory, launcher, requestOrNull, profile, Optional.empty());
    }

    /**
     * As above; {@code program} runs instead of a local pane's shell. Such a pane closes when its program exits with
     * status 0 and stays open otherwise, whatever {@code terminal.on_exit} says.
     */
    TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile,
                 Optional<java.util.List<String>> program) {
        super(new BorderLayout());''')
edit(pane, r'''        this.requestedProfile = java.util.Objects.requireNonNull(profile, "profile");
''', r'''        this.requestedProfile = java.util.Objects.requireNonNull(profile, "profile");
        this.program = java.util.Objects.requireNonNull(program, "program");
        if (program.isPresent()) onExit = ShellExitBehavior.CLOSE_ON_SUCCESS;
''')
edit(pane, r'''                requestedProfile, Optional.of(id), replaced -> {''', r'''                requestedProfile, program, Optional.of(id), replaced -> {''')
edit(pane, r'''    /** Only future exit deliveries use this policy; retained output is never closed retroactively. */
    void setShellExitBehavior(ShellExitBehavior behavior) {
        onExit = java.util.Objects.requireNonNull(behavior);
    }''', r'''    /**
     * Only future exit deliveries use this policy; retained output is never closed retroactively. A program pane keeps
     * its close-on-success policy.
     */
    void setShellExitBehavior(ShellExitBehavior behavior) {
        java.util.Objects.requireNonNull(behavior);
        if (program.isEmpty()) onExit = behavior;
    }''')

launch = app + 'launch/LaunchSettings.java'
edit(launch, r'''    /** Where a launch runs: the PTY's directory, and the settings with a WSL profile's {@code --cd} argument added. */''',
     r'''    /** These settings running {@code program} instead of the shell: the same environment, profile and grid. */
    public LaunchSettings withCommand(List<String> program) {
        return new LaunchSettings(program, environment, columns, lines, scrollback, profile);
    }

    /** Where a launch runs: the PTY's directory, and the settings with a WSL profile's {@code --cd} argument added. */''')
edit(launch, r'''        if (profile.kind() == ShellProfile.Kind.WSL || profile.kind() == ShellProfile.Kind.LOCAL_SHELL) return profile.name();
        try {
            Path name = Path.of(command.getFirst()).getFileName();
            return name == null || name.toString().isBlank() ? "shell" : name.toString();
        } catch (InvalidPathException failure) {
            return "shell";
        }
    }''', r'''        if (profile.kind() == ShellProfile.Kind.WSL || profile.kind() == ShellProfile.Kind.LOCAL_SHELL) return profile.name();
        return programName(command.getFirst());
    }

    /** A program's name for its tab: the file name of {@code program}, or {@code shell} when it has none. */
    static String programName(String program) {
        try {
            Path name = Path.of(program).getFileName();
            return name == null || name.toString().isBlank() ? "shell" : name.toString();
        } catch (InvalidPathException failure) {
            return "shell";
        }
    }''')

fake_terminals = testkit + 'FakeTerminals.java'
edit(fake_terminals, 'profile(local));', 'profile(local) + program(local));', count=2)
edit(fake_terminals, r'''    private static String profile(OpenRequest.Local local) { return local.spec().profile().map(id -> "|" + id).orElse(""); }''',
     r'''    private static String profile(OpenRequest.Local local) { return local.spec().profile().map(id -> "|" + id).orElse(""); }

    private static String program(OpenRequest.Local local) {
        return local.spec().command().map(command -> "|run:" + String.join(" ", command)).orElse("");
    }''')
edit(testkit + 'FakePluginHost.java', r'''(each followed by {@code |<profile id>} when the request named a shell profile) or {@code front|<window id>}''',
     r'''(each followed by {@code |<profile id>} when the request named a shell profile, then by {@code |run:<the command's words joined by spaces>} when it runs a program) or {@code front|<window id>}''')
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.launch.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS, the program contract test on both hosts and the three `ShellExitBehaviorTest` cases (which run only
`/bin/sh` on an inline script) included.
Then run: `./gradlew verifyApplicationArchitecture`
Expected: PASS (`dev.jasper.app.terminals` still depends only on `lifecycle` and the JDK).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/terminals/OpenSpec.java jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java \
  jasper-app/src/main/java/dev/jasper/app/workspace/WindowTerminals.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java \
  jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTab.java jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java \
  jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java jasper-app/src/main/java/dev/jasper/app/launch/LaunchSettings.java \
  jasper-app/src/test/java/dev/jasper/app/launch/LaunchSettingsTest.java jasper-app/src/test/java/dev/jasper/app/workspace/ShellLauncherTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/ShellExitBehaviorTest.java jasper-app/src/test/java/dev/jasper/app/workspace/ShellProfilePaneTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java \
  jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java
git commit -m "$(cat <<'EOF'
A plugin's tab or split can run a program instead of the shell

LocalSpec.command reaches ShellLauncher through OpenSpec and the workspace.
The program gets the default profile's environment and the pane's
variables, starts in the requested directory without WSL's --cd, names the
tab, and its pane closes on exit 0 whatever terminal.on_exit says. Both
hosts record the program in their open requests.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `Platform.editor` end to end, and every open routed through it

**Files:**
- Create: `app/plugins/FileEditors.java`, `app-test/plugins/FileEditorsTest.java`
- Modify: `app/plugins/{HostedContext,PluginHost,PluginRuntime}.java`, `app/application/JasperApplication.java`,
  `testkit/{FakePluginContext,FakePluginHost}.java`, `contract/{ContractHarness,PluginContractTest}.java`,
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `app-test/plugins/AppContractTest.java`,
  `docs/design/theme-engine/migration.csv`
- Test: `app-test/plugins/{FileEditorsTest,PluginRuntimeTest}.java`, the contract

**Interfaces:**
- Consumes: `FileEditor`, `Platform.editor`, `Capabilities.EDITOR_PROVIDE` (Task 1); `PluginHost.shellContributions` (Task 2,
  the anchor for the new field).
- Produces (app): `FileEditors(Consumer<Runnable> ui, BooleanSupplier onUi, Containment containment)` (and a package-private
  constructor taking the wait, for tests); `void bundled(Set<String> pluginIds)`; `dev.jasper.app.lifecycle.Subscription
  register(String pluginId, FileEditor editor, Function<UUID, Optional<WindowOwner>> windows)`; `boolean edit(Path file,
  OptionalInt line, Optional<UUID> windowId)` (UI thread); `boolean editFromWorker(Path file, OptionalInt line,
  Supplier<Optional<UUID>> window)` (not the UI thread; waits up to 30 s); `PluginHost.fileEditors`;
  `PluginRuntime.open(Path file, Consumer<Path> desktop)` (worker thread); `JasperApplication.openForEditing(Path)`.
- Produces (testkit): `FakePluginHost.declareBundled(String pluginId)`, `FakePluginHost.edit(Path, OptionalInt,
  Optional<UUID>)` → `boolean`, `FakePluginHost.editorPlugin()` → `Optional<String>`.
- Produces (contract): `void ContractHarness.declareBundled(String pluginId)` (before that plugin starts),
  `boolean ContractHarness.edit(Path file, OptionalInt line, Optional<UUID> windowId)`.
- Message (both hosts): `IllegalStateException("The editor is already provided by <plugin id>")`.

Every site that opens a file today runs on a worker (`ConfigEditor.open` refuses the UI thread), so the worker asks the
plugin editor on the UI thread through `FileEditors.editFromWorker`, waits at most 30 s, and runs the desktop fallback
itself (planned deviation 8). The sites: `config.toml` from Settings, the errors banner and the problem notices
(`ConfigurationController`'s opener, built in the `JasperApplication` constructor before `plugins` exists, so it reads
the field at call time), a not-running plugin's stub page (`PluginRuntime.openSettingsFile`), the Plugin Manager's
settings files, the custom VM options file, and `Platform.openInEditor`. `ConfigService.openSettings` creates a missing
`config.toml` from its template before it calls the opener, so the provided editor gets the file the template wrote.
Jasper's own opens pass the active terminal window, read on the UI thread inside the hop; `Platform.openInEditor` does
too. The window id becomes the registering plugin's own `WindowHandle` through the function its registration carries.

- [ ] **Step 1: Write the failing tests**

`app-test/plugins/FileEditorsTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.FileEditor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FileEditorsTest {
    private static final Path NOTES = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("notes.txt");
    private final Containment containment = new Containment(() -> true);
    private final FileEditors editors = new FileEditors(Runnable::run, () -> true, containment);

    /** An editor that records {@code <name>|<line or ->|<window or ->} and answers {@code answer}. */
    private static FileEditor recording(String name, List<String> calls, boolean answer) {
        return (file, line, window) -> {
            calls.add(name + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "|" + window.map(Object::toString).orElse("-"));
            return answer;
        };
    }

    private static Optional<WindowOwner> noWindow(UUID id) { return Optional.empty(); }

    /** A window handle that prints as {@code name}. */
    private static WindowOwner handle(String name) {
        return new WindowOwner() { @Override public String toString() { return name; } };
    }

    @Test void oneEditorAtATimeAndOnlyABundledPluginReplacesAnother() {
        editors.bundled(Set.of("dev.jasper.editor", "dev.jasper.other"));
        List<String> calls = new CopyOnWriteArrayList<>();
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).as("nobody provides one").isFalse();
        var user = editors.register("dev.x.user", recording("user", calls, true), FileEditorsTest::noWindow);
        assertThatIllegalStateException().isThrownBy(() -> editors.register("dev.x.second", recording("second", calls, true), FileEditorsTest::noWindow))
            .withMessage("The editor is already provided by dev.x.user");
        assertThatIllegalStateException().as("the same plugin twice")
            .isThrownBy(() -> editors.register("dev.x.user", recording("user", calls, true), FileEditorsTest::noWindow))
            .withMessage("The editor is already provided by dev.x.user");
        var bundled = editors.register("dev.jasper.editor", recording("bundled", calls, true), FileEditorsTest::noWindow);
        assertThatIllegalStateException().as("a bundled plugin does not replace another bundled one")
            .isThrownBy(() -> editors.register("dev.jasper.other", recording("other", calls, true), FileEditorsTest::noWindow))
            .withMessage("The editor is already provided by dev.jasper.editor");
        user.close();
        assertThat(editors.edit(NOTES, OptionalInt.of(12), Optional.empty())).as("the replaced registration's close leaves the bundled one").isTrue();
        bundled.close();
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(calls).containsExactly("bundled|12|-");
    }

    @Test void aDecliningOrThrowingEditorGivesFalseAndTheThrowIsRecorded() {
        var answer = new AtomicBoolean(false);
        editors.register("dev.x.a", (file, line, window) -> {
            if (file.getFileName().toString().equals("boom.txt")) throw new IllegalStateException("editor broke");
            return answer.get();
        }, FileEditorsTest::noWindow);
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(containment.failures("dev.x.a")).isZero();
        assertThat(editors.edit(NOTES.resolveSibling("boom.txt"), OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(containment.failures("dev.x.a")).isEqualTo(1);
        answer.set(true);
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).isTrue();
    }

    @Test void aWindowIdBecomesTheRegisteringPluginsOwnHandle() {
        UUID open = UUID.randomUUID();
        List<String> calls = new CopyOnWriteArrayList<>();
        editors.register("dev.x.a", recording("a", calls, true), id -> id.equals(open) ? Optional.of(handle("window")) : Optional.empty());
        editors.edit(NOTES, OptionalInt.of(3), Optional.of(open));
        editors.edit(NOTES, OptionalInt.of(3), Optional.of(UUID.randomUUID()));
        editors.edit(NOTES, OptionalInt.empty(), Optional.empty());
        assertThat(calls).containsExactly("a|3|window", "a|3|-", "a|-|-");
    }

    @Test void aWorkerAsksOnTheUiThreadAndGivesUpAfterItsWait() throws Exception {
        assertThatIllegalStateException().as("the UI thread never waits for itself")
            .isThrownBy(() -> editors.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty));

        var stuck = new FileEditors(task -> { }, () -> false, containment, Duration.ofMillis(50));
        var asked = new AtomicBoolean();
        stuck.register("dev.x.a", (file, line, window) -> { asked.set(true); return true; }, FileEditorsTest::noWindow);
        long began = System.nanoTime();
        assertThat(stuck.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty)).as("no answer in time").isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(5));
        Thread.currentThread().interrupt();
        try {
            assertThat(stuck.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty)).as("interrupted").isFalse();
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept").isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(asked).isFalse();

        var hopping = new FileEditors(SwingUtilities::invokeLater, SwingUtilities::isEventDispatchThread,
            new Containment(SwingUtilities::isEventDispatchThread));
        List<String> calls = new CopyOnWriteArrayList<>();
        var windowReadOnUi = new AtomicReference<Boolean>();
        UUID window = UUID.randomUUID();
        SwingUtilities.invokeAndWait(() -> hopping.register("dev.x.a", recording("a", calls, true), id -> Optional.of(handle("window"))));
        assertThat(hopping.editFromWorker(NOTES, OptionalInt.of(9), () -> {
            windowReadOnUi.set(SwingUtilities.isEventDispatchThread());
            return Optional.of(window);
        })).isTrue();
        assertThat(windowReadOnUi.get()).as("the window is read on the UI thread").isTrue();
        assertThat(calls).containsExactly("a|9|window");
    }

    @Test void withoutAnEditorAWorkerDoesNotHop() {
        var never = new FileEditors(task -> { throw new AssertionError("nothing to ask"); }, () -> false, containment);
        assertThat(never.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty)).isFalse();
    }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')
contract = 'jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/'
app = 'jasper-app/src/test/java/dev/jasper/app/'

append_to_class(contract + 'ContractHarness.java', r'''        /** Marks a plugin as one that ships with Jasper, whose editor replaces another plugin's; before that plugin starts. */
        void declareBundled(String pluginId);
        /**
         * Opens a file as Jasper's own opens do, with the editor a plugin provides; false when none does, or it declined or
         * threw. Asked on the UI thread.
         */
        boolean edit(java.nio.file.Path file, java.util.OptionalInt line, java.util.Optional<java.util.UUID> windowId);
''')

append_to_class(contract + 'PluginContractTest.java', r'''
    /** An editor that records {@code <file name>|<line or ->|<window id or ->} and answers {@code answer}. */
    private static dev.jasper.sdk.ui.FileEditor recordingEditor(List<String> calls, boolean answer) {
        return (file, line, window) -> {
            calls.add(file.getFileName() + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "|"
                + window.map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-"));
            return answer;
        };
    }

    @Test void providingTheEditorNeedsTheCapabilityAndOnePluginProvidesItAtATime() {
        var bare = new AtomicReference<PluginContext>();
        var a = new AtomicReference<PluginContext>();
        var b = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.b", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), b::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        java.nio.file.Path notes = HERE.resolve("notes.txt");
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).as("no plugin provides one").isFalse();
        h.ui(() -> {
            assertThatThrownBy(() -> bare.get().platform().editor(recordingEditor(calls, true)))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("editor.provide"));
            a.get().platform().editor(recordingEditor(calls, true));
            assertThatIllegalStateException().isThrownBy(() -> b.get().platform().editor(recordingEditor(calls, true)))
                .withMessage("The editor is already provided by test.a");
            assertThatIllegalStateException().as("the same plugin twice").isThrownBy(() -> a.get().platform().editor(recordingEditor(calls, true)))
                .withMessage("The editor is already provided by test.a");
        });
        assertThat(h.edit(notes, java.util.OptionalInt.of(3), Optional.empty())).isTrue();
        assertThat(calls).containsExactly("notes.txt|3|-");
    }

    @Test void aBundledPluginsEditorReplacesAnotherPluginsAndEndsWithItsPlugin() {
        h.declareBundled("test.z");
        var a = new AtomicReference<PluginContext>();
        var z = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.z", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), z::set);
        List<String> byA = new CopyOnWriteArrayList<>(), byZ = new CopyOnWriteArrayList<>();
        var replaced = new AtomicReference<Subscription>();
        java.nio.file.Path notes = HERE.resolve("notes.txt");
        h.ui(() -> {
            replaced.set(a.get().platform().editor(recordingEditor(byA, true)));
            z.get().platform().editor(recordingEditor(byZ, true));
            assertThatIllegalStateException().isThrownBy(() -> a.get().platform().editor(recordingEditor(byA, true)))
                .withMessage("The editor is already provided by test.z");
        });
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).isTrue();
        h.ui(() -> replaced.get().close());
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).as("closing the replaced registration leaves the bundled one").isTrue();
        assertThat(byA).isEmpty();
        assertThat(byZ).containsExactly("notes.txt|-|-", "notes.txt|-|-");
        h.stopAll();
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).as("the registration ends with its plugin").isFalse();
    }

    @Test void anEditorThatThrowsGivesFalseAndOneGetsItsPluginsHandleForAnOpenWindow() {
        UUID window = h.addTerminalWindow();
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        dev.jasper.sdk.ui.FileEditor recording = recordingEditor(calls, true);
        h.ui(() -> a.get().platform().editor((file, line, owner) -> {
            if (file.getFileName().toString().equals("broken.txt")) throw new IllegalStateException("the editor broke");
            return recording.edit(file, line, owner);
        }));
        assertThat(h.edit(HERE.resolve("broken.txt"), java.util.OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(h.edit(HERE.resolve("notes.txt"), java.util.OptionalInt.of(7), Optional.of(window))).isTrue();
        assertThat(h.edit(HERE.resolve("notes.txt"), java.util.OptionalInt.empty(), Optional.of(UUID.randomUUID())))
            .as("a window that is not open is no window").isTrue();
        assertThat(calls).containsExactly("notes.txt|7|" + window, "notes.txt|-|-");
    }

    @Test void openInEditorGoesToTheProvidedEditorFirst() {
        var a = new AtomicReference<PluginContext>();
        var user = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.user"), Set.of(), Set.of(), user::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        h.ui(() -> a.get().platform().editor((file, line, window) -> {
            calls.add(file.getFileName().toString());
            return !file.getFileName().toString().equals("declined.txt");
        }));
        h.ui(() -> {
            user.get().platform().openInEditor(HERE.resolve("notes.txt"));
            user.get().platform().openInEditor(HERE.resolve("declined.txt"));
        });
        h.flush();
        for (int i = 0; i < 200 && (calls.size() < 2 || h.openedInEditor().isEmpty()); i++) {
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        assertThat(calls).containsExactlyInAnyOrder("notes.txt", "declined.txt");
        assertThat(h.openedInEditor()).as("only the declined file reaches the desktop").containsExactly(HERE.resolve("declined.txt"));
    }
''')

runtime_test = app + 'plugins/PluginRuntimeTest.java'
append_to_class(runtime_test, r'''
    static final String EDITOR = """
        package fix.editor;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.platform().editor((file, line, window) -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("edited"), file.getFileName() + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return true;
                });
            }
        }
        """;

    private static Path editorPlugin(Path directory, String id) throws Exception {
        return PluginJars.build(directory, id + ".jar",
            PluginJars.descriptor(id, "1.0.0", "fix.editor.Main") + "capabilities = [\"editor.provide\"]\n",
            Map.of("fix.editor.Main", EDITOR), List.of());
    }

    /** Review Focus 4: before the plugins start, and once the providing plugin stopped, files open with the desktop. */
    @Test void openAsksTheProvidedEditorAndOtherwiseTheDesktop() throws Exception {
        editorPlugin(root.resolve("bundled").resolve("dev.example.editor"), "dev.example.editor");
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), null, false, new ArrayList<>());
        List<Path> desktop = new CopyOnWriteArrayList<>();
        Path before = root.resolve("before.txt"), during = root.resolve("during.txt"), after = root.resolve("after.txt");
        runtime.open(before, desktop::add);
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        runtime.open(during, desktop::add);
        Path edited = root.resolve("absent/dev.example.editor/data/edited");
        assertThat(Files.readAllLines(edited)).containsExactly("during.txt");
        onEdt(() -> assertThatIllegalStateException().as("never on the UI thread").isThrownBy(() -> runtime.open(during, desktop::add)));
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        runtime.open(after, desktop::add);
        assertThat(desktop).containsExactly(before, after);
        assertThat(Files.readAllLines(edited)).as("a stopped plugin's editor is not asked").containsExactly("during.txt");
    }

    /** Review Focus 3: Open in Editor creates a missing config.toml from its template, then the provided editor opens it. */
    @Test void aMissingConfigTomlIsCreatedFromTheTemplateAndThenTheProvidedEditorOpensIt() throws Exception {
        editorPlugin(root.resolve("bundled").resolve("dev.example.editor"), "dev.example.editor");
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        Path config = root.resolve("home").resolve("config.toml");
        List<Path> desktop = new CopyOnWriteArrayList<>();
        var service = new dev.jasper.app.config.ConfigService(config, false);
        try {
            assertThat(service.openSettings(file -> runtime.open(file, desktop::add)).get(10, TimeUnit.SECONDS)).isEqualTo(config);
        } finally {
            service.close();
        }
        assertThat(Files.readString(config)).isEqualTo(dev.jasper.app.config.ConfigTemplate.text(false));
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.editor/data/edited"))).containsExactly("config.toml");
        assertThat(desktop).isEmpty();
        onEdt(runtime::stop);
    }

    @Test void aBundledPluginsEditorReplacesADevelopmentPluginsEditor() throws Exception {
        editorPlugin(root.resolve("bundled").resolve("dev.example.zeditor"), "dev.example.zeditor");
        Path dev = root.resolve("dev").resolve("dev.example.aeditor");
        editorPlugin(dev, "dev.example.aeditor");
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), dev, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        assertThat(runtime.statusLines()).as("the development plugin registered first; the bundled one replaced it")
            .anySatisfy(line -> assertThat(line).contains("dev.example.aeditor", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.example.zeditor", "ACTIVE"));
        runtime.open(root.resolve("notes.txt"), path -> { throw new AssertionError("the bundled editor opens it"); });
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.zeditor/data/edited"))).containsExactly("notes.txt");
        assertThat(root.resolve("absent/dev.example.aeditor/data/edited")).doesNotExist();
        onEdt(runtime::stop);
    }
''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: compilation fails: `FakeContractTest`'s harness does not override `declareBundled(String)` and
`edit(Path,OptionalInt,Optional<UUID>)`.
Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation fails: cannot find symbol `FileEditors` and `PluginRuntime.open`.

- [ ] **Step 3: Write `FileEditors`**

`app/plugins/FileEditors.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.FileEditor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The editor a plugin provides ({@code Platform.editor}), which Jasper's own opens ask before the desktop. One at a
 * time: a bundled plugin's registration replaces a non-bundled one's; any other later registration, the same plugin's
 * included, throws. The editor runs on the UI thread inside its plugin's containment; a worker asks it through
 * {@link #editFromWorker} and waits a bounded time.
 */
final class FileEditors {
    private static final System.Logger LOG = System.getLogger(FileEditors.class.getName());
    /** How long a worker waits for the UI thread's answer before the desktop opens the file. */
    static final Duration WAIT = Duration.ofSeconds(30);

    /** A registration: the plugin, its editor, and its own handle for an open terminal window's id. */
    private record Holder(String pluginId, FileEditor editor, Function<UUID, Optional<WindowOwner>> windows) { }

    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final Containment containment;
    private final Duration wait;
    private volatile Set<String> bundled = Set.of();
    private volatile Holder holder;

    FileEditors(Consumer<Runnable> ui, BooleanSupplier onUi, Containment containment) { this(ui, onUi, containment, WAIT); }

    /** As above, waiting {@code wait} for the UI thread instead of 30 seconds; for tests. */
    FileEditors(Consumer<Runnable> ui, BooleanSupplier onUi, Containment containment, Duration wait) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.onUi = Objects.requireNonNull(onUi, "onUi");
        this.containment = Objects.requireNonNull(containment, "containment");
        this.wait = Objects.requireNonNull(wait, "wait");
    }

    /** The plugins that ship with Jasper; set before any plugin starts. */
    void bundled(Set<String> pluginIds) { bundled = Set.copyOf(pluginIds); }

    /**
     * Makes {@code pluginId}'s editor the one Jasper opens files with. UI thread.
     *
     * @param windows the registering plugin's own handle for an open terminal window's id
     * @return removes the editor, unless a bundled plugin's has replaced it since
     * @throws IllegalStateException when a plugin provides the editor already, unless this is a bundled plugin replacing
     *     a non-bundled one's
     */
    dev.jasper.app.lifecycle.Subscription register(String pluginId, FileEditor editor, Function<UUID, Optional<WindowOwner>> windows) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(windows, "windows");
        Holder current = holder;
        if (current != null) {
            if (!bundled.contains(pluginId) || bundled.contains(current.pluginId()))
                throw new IllegalStateException("The editor is already provided by " + current.pluginId());
            LOG.log(System.Logger.Level.INFO, "The bundled plugin " + pluginId + " provides the editor instead of " + current.pluginId());
        }
        var registered = new Holder(pluginId, editor, windows);
        holder = registered;
        return new dev.jasper.app.lifecycle.Subscription(() -> { if (holder == registered) holder = null; });
    }

    /**
     * Asks the provided editor to open {@code file}. UI thread.
     *
     * @param windowId the terminal window the open came from; the editor gets its plugin's handle while it is open
     * @return true when it opened the file; false when no plugin provides an editor, or it declined or threw
     */
    boolean edit(Path file, OptionalInt line, Optional<UUID> windowId) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(windowId, "windowId");
        Holder current = holder;
        if (current == null) return false;
        Optional<WindowOwner> window = windowId.flatMap(current.windows());
        var opened = new AtomicBoolean();
        Throwable failure = containment.attempt(current.pluginId(), "file editor", () -> {
            opened.set(current.editor().edit(file, line, window));
            return null;
        });
        return failure == null && opened.get();
    }

    /**
     * As {@link #edit}, from a worker: hops to the UI thread, reads {@code window} there, and waits at most 30 seconds.
     * A timeout or an interrupt gives false, with a log line, and a late turn on the UI thread then asks nothing.
     *
     * @throws IllegalStateException on the UI thread
     */
    boolean editFromWorker(Path file, OptionalInt line, Supplier<Optional<UUID>> window) {
        if (onUi.getAsBoolean()) throw new IllegalStateException("Opening a file for editing waits for the UI thread; ask from a worker");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(window, "window");
        if (holder == null) return false;
        var answer = new CompletableFuture<Boolean>();
        var abandoned = new AtomicBoolean();
        try {
            ui.accept(() -> {
                if (abandoned.get()) { answer.complete(false); return; }
                try { answer.complete(edit(file, line, window.get())); }
                catch (Throwable failure) { answer.completeExceptionally(failure); throw failure; }
            });
        } catch (RuntimeException rejected) {
            LOG.log(System.Logger.Level.WARNING, "Could not ask the plugin editor to open " + file, rejected);
            return false;
        }
        try {
            return answer.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            abandoned.set(true);
            LOG.log(System.Logger.Level.WARNING, "The plugin editor did not answer within " + wait.toSeconds() + " s; "
                + file + " opens with the desktop");
            return false;
        } catch (InterruptedException interrupted) {
            abandoned.set(true);
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING, "Interrupted while the plugin editor opened " + file + "; it opens with the desktop");
            return false;
        } catch (ExecutionException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not ask the plugin editor to open " + file, failure.getCause());
            return false;
        }
    }
}
```

- [ ] **Step 4: Route every open through it, and write the testkit**

Run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')
app = 'jasper-app/src/main/java/dev/jasper/app/'
testkit = 'jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/'

host = app + 'plugins/PluginHost.java'
edit(host, '    final ShellContributions shellContributions;\n', '''    final ShellContributions shellContributions;
    /** The editor a plugin provides ({@code Platform.editor}), which Jasper's own opens ask first. */
    final FileEditors fileEditors;
''')
edit(host, '        this.shellContributions = new ShellContributions(containment);\n',
     '        this.shellContributions = new ShellContributions(containment);\n        this.fileEditors = new FileEditors(environment.ui(), environment.onUi(), containment);\n')

context = app + 'plugins/HostedContext.java'
edit(context, r'''                    background().execute(() -> {
                        try { host.environment.editor().accept(file); }
                        catch (RuntimeException failure) {''', r'''                    background().execute(() -> {
                        try {
                            if (host.fileEditors.editFromWorker(file, java.util.OptionalInt.empty(),
                                    () -> host.environment.terminals().activeWindow().map(dev.jasper.app.terminals.WindowEntry::id))) return;
                            host.environment.editor().accept(file);
                        } catch (RuntimeException failure) {''')
edit(context, r'''                    gate.audit(Capabilities.CLIPBOARD_READ, "read " + text.map(String::length).orElse(0) + " characters from the clipboard");
                    return text;
                }
''', r'''                    gate.audit(Capabilities.CLIPBOARD_READ, "read " + text.map(String::length).orElse(0) + " characters from the clipboard");
                    return text;
                }
                @Override public Subscription editor(dev.jasper.sdk.ui.FileEditor editor) {
                    requireOpen();
                    requireUi("Platform.editor");
                    gate.require(Capabilities.EDITOR_PROVIDE);
                    Objects.requireNonNull(editor, "editor");
                    dev.jasper.app.lifecycle.Subscription registration = host.fileEditors.register(id, editor,
                        window -> host.environment.terminals().window(window)
                            .<dev.jasper.sdk.WindowOwner>map(entry -> terminals.windowHandle(entry.id())));
                    gate.audit(Capabilities.EDITOR_PROVIDE, "became the editor Jasper opens files with");
                    // HostedUi.closeAll, which teardown runs, closes it when the plugin stops or fails to start.
                    return ui.tracked(registration::close);
                }
''')

runtime = app + 'plugins/PluginRuntime.java'
edit(runtime, r'''    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {''', r'''    /**
     * Opens a file for editing, on a worker thread: the editor a plugin provides ({@code Platform.editor}), asked on the
     * UI thread with the active terminal window, then {@code desktop} on this thread when no plugin provides one, it
     * declines or throws, or it does not answer within 30 seconds. Before the plugins start, {@code desktop} alone.
     *
     * @param file the file
     * @param desktop the desktop's opener, such as {@code ConfigEditor::open}; its failure propagates
     * @throws IllegalStateException on the UI thread
     */
    public void open(Path file, Consumer<Path> desktop) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Editor operations require a worker thread");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(desktop, "desktop");
        PluginHost current = host;
        if (current != null && current.fileEditors.editFromWorker(file, java.util.OptionalInt.empty(),
                () -> terminals.activeWindow().map(dev.jasper.app.terminals.WindowEntry::id))) return;
        desktop.accept(file);
    }

    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {''')
edit(runtime, r'''        created.commands.declare(Subcommands.choose(resolution.load(), notLoading, notifications.problem()));
''', r'''        created.commands.declare(Subcommands.choose(resolution.load(), notLoading, notifications.problem()));
        created.fileEditors.bundled(resolution.load().stream().filter(candidate -> candidate.origin() == PluginCandidate.Origin.BUNDLED)
            .map(PluginCandidate::id).collect(java.util.stream.Collectors.toSet()));
''')
edit(runtime, '                editor.accept(PluginSettingsFiles.file(options.userDirectory(), id));\n',
     '                open(PluginSettingsFiles.file(options.userDirectory(), id), editor);\n')

application = app + 'application/JasperApplication.java'
edit(application, '    private PluginRuntime plugins;\n', '    private volatile PluginRuntime plugins;\n')
edit(application, r'''new ConfigurationController(themes, service, new dev.jasper.app.platform.ConfigEditor()::open,''',
     r'''new ConfigurationController(themes, service, this::openForEditing,''')
edit(application, r'''            () -> restart(RestartMode.SAME), vmEditor::open, vmEditor::reveal,''',
     r'''            () -> restart(RestartMode.SAME), this::openForEditing, vmEditor::reveal,''')
edit(application, r'''                editor::open, editor::reveal, this::browse));''', r'''                this::openForEditing, editor::reveal, this::browse));''')
edit(application, r'''    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */''',
     r'''    /**
     * Opens a file for editing on a worker thread: the editor a plugin provides, else the desktop's edit or open action,
     * else revealing the file. Reads {@link #plugins} at call time, so openers built before the plugins start use it too.
     */
    private void openForEditing(Path file) {
        var desktop = new dev.jasper.app.platform.ConfigEditor();
        PluginRuntime runtime = plugins;
        if (runtime == null) desktop.open(file); else runtime.open(file, desktop::open);
    }

    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */''')

fake_context = testkit + 'FakePluginContext.java'
edit(fake_context, r'''                @Override public void openInEditor(Path file) { requireOpen(); host.openedInEditor.add(Objects.requireNonNull(file, "file")); }''',
     r'''                @Override public void openInEditor(Path file) {
                    requireOpen();
                    Objects.requireNonNull(file, "file");
                    // As the application: the editor a plugin provides first, here on the caller's thread and with no window.
                    if (!host.editProvided(file, java.util.OptionalInt.empty(), Optional.empty())) host.openedInEditor.add(file);
                }
                @Override public Subscription editor(dev.jasper.sdk.ui.FileEditor editor) {
                    requireOpen();
                    if (!info.capabilities().contains(dev.jasper.sdk.Capabilities.EDITOR_PROVIDE))
                        throw new dev.jasper.sdk.MissingCapabilityException(info.id(), dev.jasper.sdk.Capabilities.EDITOR_PROVIDE);
                    Objects.requireNonNull(editor, "editor");
                    return host.provideEditor(FakePluginContext.this, editor);
                }''')

fake_host = testkit + 'FakePluginHost.java'
edit(fake_host, '        shellContributors.removeIf(entry -> entry.context() == context);\n', '''        shellContributors.removeIf(entry -> entry.context() == context);
        ProvidedEditor provided = providedEditor;
        if (provided != null && provided.context() == context) providedEditor = null;
''')
append_to_class(fake_host, r'''
    /** The editor a plugin provides ({@code Platform.editor}) and the plugin that registered it. */
    record ProvidedEditor(FakePluginContext context, dev.jasper.sdk.ui.FileEditor editor) { }
    private volatile ProvidedEditor providedEditor;
    private final Set<String> bundledPlugins = ConcurrentHashMap.newKeySet();

    /**
     * Marks a plugin as one that ships with Jasper, before it starts: its {@code Platform.editor} registration replaces
     * another plugin's, as the application's bundled plugins' do.
     *
     * @param pluginId the plugin
     * @since 0.17.1
     */
    public void declareBundled(String pluginId) { bundledPlugins.add(Objects.requireNonNull(pluginId, "pluginId")); }

    /** Makes a plugin's editor the one files open with, under the application's one-at-a-time rule. */
    Subscription provideEditor(FakePluginContext context, dev.jasper.sdk.ui.FileEditor editor) {
        String pluginId = context.plugin().id();
        ProvidedEditor current = providedEditor;
        if (current != null && (!bundledPlugins.contains(pluginId) || bundledPlugins.contains(current.context().plugin().id())))
            throw new IllegalStateException("The editor is already provided by " + current.context().plugin().id());
        var provided = new ProvidedEditor(context, editor);
        providedEditor = provided;
        Subscription subscription = () -> { if (providedEditor == provided) providedEditor = null; };
        context.own(subscription);
        return subscription;
    }

    /** Asks the provided editor on the caller's thread; false when there is none, or it declined or threw. */
    boolean editProvided(Path file, OptionalInt line, Optional<dev.jasper.sdk.WindowOwner> window) {
        ProvidedEditor provided = providedEditor;
        return provided != null && editWith(provided, file, line, window);
    }

    private boolean editWith(ProvidedEditor provided, Path file, OptionalInt line, Optional<dev.jasper.sdk.WindowOwner> window) {
        try {
            return provided.editor().edit(file, line, window);
        } catch (RuntimeException failure) {
            failures.add(provided.context().plugin().id() + " file editor: " + failure);
            return false;
        }
    }

    /**
     * Opens a file as Jasper's own opens do (Open in Editor, the settings files): with the editor a plugin provides
     * through {@code Platform.editor}, given that plugin's handle for {@code windowId} while the window is open.
     *
     * @param file the file
     * @param line the line to show, or empty
     * @param windowId the terminal window the open came from, or empty
     * @return what the editor answered; false when no plugin provides one, or it threw, which {@link #failures()} records
     * @since 0.17.1
     */
    public boolean edit(Path file, OptionalInt line, Optional<UUID> windowId) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(windowId, "windowId");
        ProvidedEditor provided = providedEditor;
        if (provided == null) return false;
        Optional<dev.jasper.sdk.WindowOwner> window = windowId.flatMap(id -> provided.context().terminals.window(id))
            .map(handle -> (dev.jasper.sdk.WindowOwner) handle);
        return editWith(provided, file, line, window);
    }

    /**
     * Which plugin provides the editor now.
     *
     * @return its id, or empty
     * @since 0.17.1
     */
    public Optional<String> editorPlugin() { return Optional.ofNullable(providedEditor).map(provided -> provided.context().plugin().id()); }
''')

edit('jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java',
     '            @Override public void close() { host.close(); }\n',
     '''            @Override public void declareBundled(String pluginId) { host.declareBundled(pluginId); }
            @Override public boolean edit(java.nio.file.Path file, java.util.OptionalInt line, java.util.Optional<java.util.UUID> windowId) {
                return host.edit(file, line, windowId);
            }
            @Override public void close() { host.close(); }
''')
contract_app = 'jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java'
edit(contract_app, '        List<Subcommands.Owner> declared = new java.util.concurrent.CopyOnWriteArrayList<>();\n',
     '''        List<Subcommands.Owner> declared = new java.util.concurrent.CopyOnWriteArrayList<>();
        Set<String> bundled = java.util.concurrent.ConcurrentHashMap.newKeySet();
''')
edit(contract_app, '            @Override public void close() { stopAll(); flush(); }\n', '''            @Override public void declareBundled(String pluginId) {
                bundled.add(pluginId);
                onEdt(() -> host.fileEditors.bundled(Set.copyOf(bundled)));
            }
            @Override public boolean edit(Path file, java.util.OptionalInt line, Optional<UUID> windowId) {
                return onEdtValue(() -> host.fileEditors.edit(file, line, windowId));
            }
            @Override public void close() { stopAll(); flush(); }
''')

with open('docs/design/theme-engine/migration.csv', 'a', encoding='utf-8') as csv:
    csv.write('jasper-app/src/main/java/dev/jasper/app/plugins/FileEditors.java,app.plugins,FileEditors,nonvisual-helper,none,none,'
              'jasper-app/src/test/java/dev/jasper/app/plugins/FileEditorsTest.java,New in the default editor plan Task 4; registry of the '
              'plugin editor and its UI-thread hop with no Swing component or theme dependency; matched only by its dev.jasper.sdk.ui import\n')
    csv.write('jasper-app/src/test/java/dev/jasper/app/plugins/FileEditorsTest.java,app.plugins,FileEditorsTest,nonvisual-helper,none,none,'
              'jasper-app/src/test/java/dev/jasper/app/plugins/FileEditorsTest.java,Test/fixture ownership; New in the default editor plan '
              'Task 4; no Swing component or theme dependency\n')
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.vmoptions.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS, the four editor contract tests on both hosts included.
Then run: `./gradlew verifySdkArchitecture verifyApplicationArchitecture`
Expected: PASS (`FileEditors` and every `dev.jasper.sdk` type stay in `dev.jasper.app.plugins`; `JasperApplication`
reaches the editor through `PluginRuntime.open` with JDK types only).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/FileEditors.java jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/FileEditorsTest.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java \
  jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java \
  jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Jasper opens files in the editor a plugin provides, then the desktop

Platform.editor registers one FileEditor at a time (a bundled plugin's
replaces another's); FileEditors asks it on the UI thread inside
containment, and workers wait for the answer at most 30 seconds. Open in
Editor, the problem notices, plugin settings files, the VM options file
and Platform.openInEditor all go through PluginRuntime.open before
ConfigEditor's fallback. The testkit fake follows the same rules.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `CommandLine.client()` end to end

**Files:**
- Modify: `app/commandline/CommandInstall.java`, `app/plugins/{HostedCommandLine,HostedContext,PluginHost,PluginRuntime}.java`,
  `app/application/JasperApplication.java`, `testkit/{FakeCommandLine,FakePluginHost}.java`,
  `contract/{ContractHarness,PluginContractTest}.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`,
  `app-test/plugins/AppContractTest.java`
- Test: `app-test/commandline/CommandInstallTest.java`, `app-test/plugins/PluginRuntimeTest.java`, the contract

**Interfaces:**
- Consumes: `CommandLine.client()` (Task 1); `PluginHost.fileEditors` (Task 4, the anchor for the new field).
- Produces (app): `public static Optional<Path> CommandInstall.client(String osName, String appPath, String developmentClient)`
  (pure); `PluginRuntime.commandClient(Optional<Path> client)` (before `start`; afterwards
  `IllegalStateException("Plugins have already started")`); `PluginHost.commandClient`; `HostedCommandLine.client()`.
- Produces (testkit): `FakePluginHost.setCommandClient(Path client)`; `FakeCommandLine.client()`.
- Produces (contract): `void ContractHarness.commandClient(java.nio.file.Path client)`.

The client is the one inside the installed image (`CommandInstall.clientIn`, from `jpackage.app-path`), else, in a
development run, the `jasper.commandClient` system property: `gradle/launcher.gradle` already sets it to the built
client (`jasper-app/build/cli/bin/jasper`) for the `test` and `run` tasks, and nothing in `src/main` read it until now.
`dev.jasper.app.commandline` may depend only on `dev.jasper.app.platform`, and `client` needs neither.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')
contract = 'jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/'
app = 'jasper-app/src/test/java/dev/jasper/app/'

append_to_class(contract + 'ContractHarness.java', r'''        /** Sets the {@code jasper} command {@code CommandLine.client()} names, as the application names its installed or built one. */
        void commandClient(java.nio.file.Path client);
''')
append_to_class(contract + 'PluginContractTest.java', r'''
    @Test void theCommandLineNamesTheClientOnceTheHostKnowsIt() {
        PluginContext a = started("test.a", Set.of());
        assertThat(a.commandLine().client()).as("no client yet").isEmpty();
        java.nio.file.Path client = HERE.resolve("bin").resolve("jasper");
        h.commandClient(client);
        assertThat(a.commandLine().client()).as("any thread").contains(client);
    }
''')

append_to_class(app + 'commandline/CommandInstallTest.java', r'''
    @Test void theClientIsTheImagesCommandElseTheBuildsElseNone() {
        String built = Path.of("/src/jasper-app/build/cli/bin/jasper").toString();
        assertThat(CommandInstall.client("Mac OS X", "/Applications/Jasper Beta.app/Contents/MacOS/Jasper", built))
            .as("an installed image's own command wins").contains(Path.of("/Applications/Jasper Beta.app/Contents/Resources/bin/jasper"));
        assertThat(CommandInstall.client("Linux", "/opt/jasper/bin/Jasper", null)).contains(Path.of("/opt/jasper/lib/bin/jasper"));
        assertThat(CommandInstall.client("Mac OS X", null, built)).as("a development run's").contains(Path.of(built));
        assertThat(CommandInstall.client("Linux", "", " ")).isEmpty();
        assertThat(CommandInstall.client("Linux", null, null)).isEmpty();
    }
''')

append_to_class(app + 'plugins/PluginRuntimeTest.java', r'''
    static final String CLIENT = """
        package fix.client;
        import java.nio.file.Files;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                try {
                    Files.writeString(context.dataDirectory().resolve("client"),
                        context.commandLine().client().map(Object::toString).orElse("none"));
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        """;

    @Test void pluginsReadTheClientGivenBeforeTheyStart() throws Exception {
        Path dev = root.resolve("dev").resolve("dev.example.client");
        PluginJars.build(dev, "client.jar", PluginJars.descriptor("dev.example.client", "1.0.0", "fix.client.Main"),
            Map.of("fix.client.Main", CLIENT), List.of());
        PluginRuntime runtime = runtime(null, root.resolve("absent"), dev, false, new ArrayList<>());
        Path client = root.resolve("build").resolve("cli").resolve("bin").resolve("jasper");
        onEdt(() -> runtime.commandClient(java.util.Optional.of(client)));
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        assertThat(root.resolve("absent/dev.example.client/data/client")).hasContent(client.toString());
        onEdt(() -> assertThatIllegalStateException().isThrownBy(() -> runtime.commandClient(java.util.Optional.empty()))
            .withMessage("Plugins have already started"));
        onEdt(runtime::stop);
    }
''')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: compilation fails: `FakeContractTest`'s harness does not override `commandClient(Path)`.
Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation fails: cannot find symbol `CommandInstall.client` and `PluginRuntime.commandClient`.

- [ ] **Step 3: Name the client from the host**

`app/plugins/HostedCommandLine.java` (whole file):

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.cli.CommandHandler;
import dev.jasper.sdk.cli.CommandLine;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** One plugin's {@code jasper} subcommands; its handlers end with the plugin. */
final class HostedCommandLine implements CommandLine {
    private final String pluginId;
    private final PluginCommands commands;
    private final HostedUi ui;
    private final HostedTerminals terminals;
    private final Supplier<Optional<Path>> client;

    /** {@code client} gives the {@code jasper} command the application names; any thread. */
    HostedCommandLine(String pluginId, PluginCommands commands, HostedUi ui, HostedTerminals terminals, Supplier<Optional<Path>> client) {
        this.pluginId = pluginId;
        this.commands = commands;
        this.ui = ui;
        this.terminals = terminals;
        this.client = client;
    }

    @Override public Subscription handle(String name, CommandHandler handler) {
        ui.guard("CommandLine.handle");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        return ui.tracked(commands.register(pluginId, name, handler, terminals::windowOfPane));
    }

    @Override public Optional<Path> client() { return client.get(); }
}
```

Then run from the worktree root:

```bash
python3 - <<'PY'
import pathlib
root = pathlib.Path('.')
def edit(path, old, new):
    p = root / path
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old, t.count(old))
    p.write_text(t.replace(old, new), encoding='utf-8')
def append_to_class(path, code):
    p = root / path
    t = p.read_text(encoding='utf-8').rstrip()
    assert t.endswith('}'), path
    p.write_text(t[:-1] + code + '}\n', encoding='utf-8')
app = 'jasper-app/src/main/java/dev/jasper/app/'
testkit = 'jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/'

edit(app + 'commandline/CommandInstall.java', r'''    public boolean available() { return placement.available(); }''', r'''    /**
     * The {@code jasper} command this Jasper names for other programs, such as in {@code EDITOR}: the one inside the
     * installed app image, else in a development run {@code developmentClient} (the {@code jasper.commandClient}
     * property the build sets), else none.
     *
     * @param appPath the launcher's path ({@code jpackage.app-path}); null or blank in a development run
     * @param developmentClient the built client's path, or null or blank
     */
    public static Optional<Path> client(String osName, String appPath, String developmentClient) {
        Optional<Path> installed = clientIn(osName, appPath);
        if (installed.isPresent()) return installed;
        if (developmentClient == null || developmentClient.isBlank()) return Optional.empty();
        try {
            return Optional.of(Path.of(developmentClient));
        } catch (java.nio.file.InvalidPathException unusable) {
            return Optional.empty();
        }
    }

    public boolean available() { return placement.available(); }''')

host = app + 'plugins/PluginHost.java'
edit(host, '    final FileEditors fileEditors;\n', '''    final FileEditors fileEditors;
    /** The {@code jasper} command plugins see in {@code CommandLine.client()}; set before plugins start, read on any thread. */
    volatile Optional<Path> commandClient = Optional.empty();
''')

edit(app + 'plugins/HostedContext.java', '        this.commandLine = new HostedCommandLine(id, host.commands, ui, terminals);\n',
     '        this.commandLine = new HostedCommandLine(id, host.commands, ui, terminals, () -> host.commandClient);\n')

runtime = app + 'plugins/PluginRuntime.java'
edit(runtime, r'''    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {''', r'''    private Optional<Path> commandClient = Optional.empty();

    /** The {@code jasper} command plugins see in {@code CommandLine.client()}; before {@link #start}. */
    public void commandClient(Optional<Path> client) {
        if (host != null) throw new IllegalStateException("Plugins have already started");
        commandClient = Objects.requireNonNull(client, "client");
    }

    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {''')
edit(runtime, '        created.clipboard = clipboard;\n', '        created.clipboard = clipboard;\n        created.commandClient = commandClient;\n')

edit(app + 'application/JasperApplication.java', '        plugins.clipboard(clipboard);\n', '''        plugins.clipboard(clipboard);
        plugins.commandClient(CommandInstall.client(System.getProperty("os.name"), System.getProperty("jpackage.app-path"),
            System.getProperty("jasper.commandClient")));
''')

command_line = testkit + 'FakeCommandLine.java'
edit(command_line, 'import java.util.Objects;\n', 'import java.nio.file.Path;\nimport java.util.Objects;\nimport java.util.Optional;\n')
edit(command_line, r'''    void closeAll() { host.subcommandHandlers.values().removeIf(registered -> registered.context() == context); }''',
     r'''    @Override public Optional<Path> client() { return host.commandClient; }

    void closeAll() { host.subcommandHandlers.values().removeIf(registered -> registered.context() == context); }''')
append_to_class(testkit + 'FakePluginHost.java', r'''
    volatile Optional<Path> commandClient = Optional.empty();

    /**
     * Sets the {@code jasper} command {@code CommandLine.client()} names, as the application names its installed or built
     * client; until then it is empty.
     *
     * @param client the client's path
     * @since 0.17.1
     */
    public void setCommandClient(Path client) { commandClient = Optional.of(Objects.requireNonNull(client, "client")); }
''')

edit('jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java',
     '            @Override public void close() { host.close(); }\n',
     '            @Override public void commandClient(java.nio.file.Path client) { host.setCommandClient(client); }\n'
     '            @Override public void close() { host.close(); }\n')
edit('jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java',
     '            @Override public void close() { stopAll(); flush(); }\n',
     '            @Override public void commandClient(Path client) { onEdt(() -> host.commandClient = Optional.of(client)); }\n'
     '            @Override public void close() { stopAll(); flush(); }\n')
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.commandline.CommandInstallTest' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS, the client contract test on both hosts included.
Then run: `./gradlew verifySdkArchitecture verifyApplicationArchitecture`
Expected: PASS (`CommandInstall.client` uses the JDK only).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/commandline/CommandInstall.java jasper-app/src/main/java/dev/jasper/app/plugins/HostedCommandLine.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/test/java/dev/jasper/app/commandline/CommandInstallTest.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java \
  jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeCommandLine.java jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java \
  jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java \
  jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java
git commit -m "$(cat <<'EOF'
Plugins learn the path of the jasper command through CommandLine.client

CommandInstall.client names the installed image's command, else the
built client a development run gets in jasper.commandClient. PluginRuntime
takes it before the plugins start; the testkit fake answers what
setCommandClient set, and both hosts pass the contract.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

---

### Task 6: Known editors and detection

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/external/{package-info,KnownEditor,KnownEditors,Detected,Detection}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/external/{KnownEditorsTest,DetectionTest}.java`
- Modify: `docs/design/theme-engine/migration.csv` (`ThemeMigrationCoverageTest` scans plugin sources, main and test, and
  matches `new KnownEditor(` and `new ArrayList`)

**Interfaces:**
- Consumes: nothing from Tasks 1–5; the JDK only.
- Produces: `KnownEditor(String id, String name, Kind kind, List<String> executables, List<String> macApps,
  List<String> extraArgs, List<String> waitArgs, LineForm lineForm)` with `enum Kind { TERMINAL, APP }` and
  `enum LineForm { PLUS_LINE_BEFORE, FILE_COLON_LINE, DASH_G_FILE_COLON_LINE, DASH_L_LINE_BEFORE, LINE_OPTION_BEFORE,
  DASH_N_LINE_BEFORE, NONE }`; `KnownEditors.ALL`, `KnownEditors.byId(String)` → `Optional<KnownEditor>`;
  `Detected(KnownEditor editor, Path executable)` (absolute); `Detection.Probe(String osName, List<Path> path, Path home,
  Map<String, String> environment, Predicate<Path> executable)` with `Probe.system()`; `Detection.detect(Probe)` →
  `List<Detected>`, at most one per editor, in `ALL`'s order.

- [ ] **Step 1: Write the failing tests**

`plugins/editor/src/test/java/dev/jasper/editor/external/KnownEditorsTest.java`:

```java
package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnownEditorsTest {
    /** The plan's known-editor table, checked against each editor's documentation on 2026-10-14, row by row. */
    @Test void theTableIsThePlansKnownEditorsInOrder() {
        assertThat(KnownEditors.ALL).extracting(editor -> editor.id() + "|" + editor.name() + "|" + editor.kind() + "|"
                + String.join(",", editor.executables()) + "|" + String.join(",", editor.macApps()) + "|"
                + String.join(" ", editor.extraArgs()) + "|" + String.join(" ", editor.waitArgs()) + "|" + editor.lineForm())
            .containsExactly(
                "nvim|Neovim|TERMINAL|nvim||||PLUS_LINE_BEFORE",
                "vim|Vim|TERMINAL|vim||||PLUS_LINE_BEFORE",
                "vi|vi|TERMINAL|vi||||PLUS_LINE_BEFORE",
                "nano|nano|TERMINAL|nano||||PLUS_LINE_BEFORE",
                "micro|micro|TERMINAL|micro||||PLUS_LINE_BEFORE",
                "helix|Helix|TERMINAL|hx,helix||||FILE_COLON_LINE",
                "emacs|Emacs|TERMINAL|emacs||-nw||PLUS_LINE_BEFORE",
                "code|Visual Studio Code|APP|code|Visual Studio Code.app/Contents/Resources/app/bin/code||--wait|DASH_G_FILE_COLON_LINE",
                "cursor|Cursor|APP|cursor|Cursor.app/Contents/Resources/app/bin/cursor||--wait|DASH_G_FILE_COLON_LINE",
                "zed|Zed|APP|zed|Zed.app/Contents/MacOS/cli||--wait|FILE_COLON_LINE",
                "sublime|Sublime Text|APP|subl|Sublime Text.app/Contents/SharedSupport/bin/subl||-w|FILE_COLON_LINE",
                "bbedit|BBEdit|APP|bbedit|||--wait|PLUS_LINE_BEFORE",
                "textmate|TextMate|APP|mate|TextMate.app/Contents/Resources/mate||-w|DASH_L_LINE_BEFORE",
                "idea|IntelliJ IDEA|APP|idea|||--wait|LINE_OPTION_BEFORE",
                "notepadpp|Notepad++|APP|notepad++|||-multiInst -nosession|DASH_N_LINE_BEFORE",
                "notepad|Notepad|APP|notepad||||NONE");
        assertThat(KnownEditors.ALL.stream().map(KnownEditor::lineForm).distinct())
            .as("every line form is used").containsExactlyInAnyOrder(KnownEditor.LineForm.values());
    }

    @Test void anEditorIsLookedUpByItsId() {
        assertThat(KnownEditors.byId("code")).map(KnownEditor::name).contains("Visual Studio Code");
        assertThat(KnownEditors.byId("notepadpp")).map(KnownEditor::name).contains("Notepad++");
        assertThat(KnownEditors.byId("jasper")).as("a choice, not an editor").isEmpty();
        assertThat(KnownEditors.byId("Code")).as("ids are exact").isEmpty();
    }
}
```

`plugins/editor/src/test/java/dev/jasper/editor/external/DetectionTest.java`:

```java
package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Detection over a fake file system: {@link #files} is every program that exists. Paths hang off this machine's root,
 * so the expectations hold on Windows too, where {@code /opt/homebrew/bin} resolves against the current drive.
 */
class DetectionTest {
    private static final Path ROOT = Path.of("").toAbsolutePath().getRoot();
    private static final Path HOME = ROOT.resolve("Users").resolve("me");
    private static final String MAC = "Mac OS X", LINUX = "Linux", WINDOWS = "Windows 11";

    private final Set<Path> files = new HashSet<>();

    /** A path under the root, as detection names what it finds: absolute and normalized. */
    private static Path path(String relative) { return ROOT.resolve(relative).toAbsolutePath().normalize(); }

    /** A program on the fake file system. */
    private Path program(String relative) {
        Path program = path(relative);
        files.add(program);
        return program;
    }

    private List<String> detect(String osName, List<Path> path, Map<String, String> environment) {
        return Detection.detect(new Detection.Probe(osName, path, HOME, environment, files::contains)).stream()
            .map(found -> found.editor().id() + "=" + found.executable()).toList();
    }

    @Test void pathOrderWinsAndEachEditorIsFoundOnce() {
        Path first = program("a/bin/nvim");
        program("b/bin/nvim");
        Path vim = program("b/bin/vim");
        assertThat(detect(LINUX, List.of(path("a/bin"), path("b/bin")), Map.of())).containsExactly("nvim=" + first, "vim=" + vim);
    }

    @Test void homebrewLocalAndTheUsersBinAreSearchedAfterAShortPath() {
        Path nvim = program("opt/homebrew/bin/nvim");
        Path micro = program("usr/local/bin/micro");
        Path hx = program("Users/me/.local/bin/hx");
        assertThat(detect(MAC, List.of(path("usr/bin"), path("bin")), Map.of()))
            .containsExactly("nvim=" + nvim, "micro=" + micro, "helix=" + hx);
    }

    @Test void macAppToolsAreFoundOnMacOsOnly() {
        Path code = program("Applications/Visual Studio Code.app/Contents/Resources/app/bin/code");
        Path zed = program("Users/me/Applications/Zed.app/Contents/MacOS/cli");
        Path mate = program("Applications/TextMate.app/Contents/Resources/mate");
        assertThat(detect(MAC, List.of(), Map.of())).containsExactly("code=" + code, "zed=" + zed, "textmate=" + mate);
        assertThat(detect(LINUX, List.of(), Map.of())).isEmpty();
    }

    @Test void aToolOnPathWinsOverTheOneInsideItsApp() {
        Path onPath = program("opt/homebrew/bin/code");
        program("Applications/Visual Studio Code.app/Contents/Resources/app/bin/code");
        assertThat(detect(MAC, List.of(), Map.of())).containsExactly("code=" + onPath);
    }

    @Test void intellijsAppBundleIsNeverFound() {
        program("Applications/IntelliJ IDEA.app/Contents/MacOS/idea");
        program("Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea");
        assertThat(detect(MAC, List.of(), Map.of())).as("its binary does not wait").isEmpty();
        Path idea = program("usr/local/bin/idea");
        assertThat(detect(MAC, List.of(), Map.of())).containsExactly("idea=" + idea);
    }

    @Test void helixIsFoundAsHxOrHelix() {
        Path helix = program("usr/bin/helix");
        assertThat(detect(LINUX, List.of(path("usr/bin")), Map.of())).containsExactly("helix=" + helix);
        Path hx = program("usr/bin/hx");
        assertThat(detect(LINUX, List.of(path("usr/bin")), Map.of())).as("hx first").containsExactly("helix=" + hx);
    }

    @Test void windowsTriesExeCmdAndBatAndNotepadsOwnFolders() {
        program("tools/code");                                      // VS Code's script for POSIX shells, beside code.cmd
        Path code = program("tools/code.cmd");
        Path vim = program("tools/vim.bat");
        Path subl = program("tools/subl.exe");
        Path notepadpp = program("Program Files (x86)/Notepad++/notepad++.exe");
        Path notepad = program("Windows/System32/notepad.exe");
        program("opt/homebrew/bin/nvim");
        var environment = Map.of("ProgramFiles", path("Program Files").toString(),
            "ProgramFiles(x86)", path("Program Files (x86)").toString(), "SYSTEMROOT", path("Windows").toString());
        assertThat(detect(WINDOWS, List.of(path("tools")), environment)).as("no Unix folders; names ignore case")
            .containsExactly("vim=" + vim, "code=" + code, "sublime=" + subl, "notepadpp=" + notepadpp, "notepad=" + notepad);
    }

    @Test void anEmptySystemHasNoEditors() {
        assertThat(detect(MAC, List.of(), Map.of())).isEmpty();
        assertThat(detect(LINUX, List.of(path("usr/bin")), Map.of())).isEmpty();
        assertThat(detect(WINDOWS, List.of(), Map.of())).isEmpty();
    }

    @Test void theSystemProbeReadsThisJvm() {
        var probe = Detection.Probe.system();
        assertThat(probe.osName()).isEqualTo(System.getProperty("os.name"));
        assertThat(probe.path()).allSatisfy(folder -> assertThat(folder.isAbsolute()).as("%s", folder).isTrue());
        assertThat(probe.home()).isEqualTo(Path.of(System.getProperty("user.home")));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.external.*'`
Expected: `compileTestJava` FAILED: `cannot find symbol` for `KnownEditors`, `KnownEditor`, `Detection` (the package
`dev.jasper.editor.external` does not exist yet).

- [ ] **Step 3: Write the implementation**

`plugins/editor/src/main/java/dev/jasper/editor/external/package-info.java`:

```java
/**
 * Editors besides Jasper's own: the editors Jasper knows, finding them on this machine, the {@code EDITOR} values and
 * command lines that run them, and the default editor Jasper opens files with and names in its shells'
 * {@code EDITOR} and {@code VISUAL}. Detection runs on the background executor; the default editor's {@code edit},
 * {@code variables} and listeners run on the UI thread. May depend on: the JDK and the SDK.
 */
package dev.jasper.editor.external;
```

`plugins/editor/src/main/java/dev/jasper/editor/external/KnownEditor.java`:

```java
package dev.jasper.editor.external;

import java.util.List;
import java.util.Objects;

/**
 * An editor Jasper knows how to find and run.
 *
 * @param id the {@code default_editor} value that names it
 * @param name what Settings lists
 * @param kind whether it runs in a terminal or as an app of its own
 * @param executables the program names looked for in each folder, in order
 * @param macApps on macOS, its command-line tool inside an app, relative to an Applications folder
 * @param extraArgs arguments it always gets before the file ({@code -nw} for Emacs)
 * @param waitArgs what makes an app editor return only once the file is closed, for {@code EDITOR}
 * @param lineForm how it is told the line to open at
 */
public record KnownEditor(String id, String name, Kind kind, List<String> executables, List<String> macApps,
                          List<String> extraArgs, List<String> waitArgs, LineForm lineForm) {
    /** Where an editor runs. */
    public enum Kind {
        /** In a terminal: Jasper runs it in a tab. */
        TERMINAL,
        /** In a window of its own: Jasper starts it on its own. */
        APP
    }

    /** How an editor is told the line to open at; without a line every form is just {@code FILE}. */
    public enum LineForm {
        /** {@code +LINE FILE} */
        PLUS_LINE_BEFORE,
        /** {@code FILE:LINE} */
        FILE_COLON_LINE,
        /** {@code -g FILE:LINE} */
        DASH_G_FILE_COLON_LINE,
        /** {@code -l LINE FILE} */
        DASH_L_LINE_BEFORE,
        /** {@code --line LINE FILE} */
        LINE_OPTION_BEFORE,
        /** {@code -nLINE FILE} */
        DASH_N_LINE_BEFORE,
        /** {@code FILE}: the editor takes no line */
        NONE
    }

    /** Copies the lists; rejects nulls and an editor without a program name. */
    public KnownEditor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        executables = List.copyOf(executables);
        macApps = List.copyOf(macApps);
        extraArgs = List.copyOf(extraArgs);
        waitArgs = List.copyOf(waitArgs);
        Objects.requireNonNull(lineForm, "lineForm");
        if (executables.isEmpty()) throw new IllegalArgumentException("An editor needs a program name: " + id);
    }
}
```

`plugins/editor/src/main/java/dev/jasper/editor/external/KnownEditors.java`:

```java
package dev.jasper.editor.external;

import dev.jasper.editor.external.KnownEditor.Kind;
import dev.jasper.editor.external.KnownEditor.LineForm;
import java.util.List;
import java.util.Optional;

/** The editors Jasper finds and runs, in the order Settings lists them; checked against each one's documentation on 2026-10-14. */
public final class KnownEditors {
    /** Every known editor. */
    public static final List<KnownEditor> ALL = List.of(
        terminal("nvim", "Neovim", List.of("nvim"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("vim", "Vim", List.of("vim"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("vi", "vi", List.of("vi"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("nano", "nano", List.of("nano"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("micro", "micro", List.of("micro"), List.of(), LineForm.PLUS_LINE_BEFORE),
        terminal("helix", "Helix", List.of("hx", "helix"), List.of(), LineForm.FILE_COLON_LINE),
        terminal("emacs", "Emacs", List.of("emacs"), List.of("-nw"), LineForm.PLUS_LINE_BEFORE),
        app("code", "Visual Studio Code", "code", List.of("Visual Studio Code.app/Contents/Resources/app/bin/code"), List.of("--wait"),
            LineForm.DASH_G_FILE_COLON_LINE),
        app("cursor", "Cursor", "cursor", List.of("Cursor.app/Contents/Resources/app/bin/cursor"), List.of("--wait"),
            LineForm.DASH_G_FILE_COLON_LINE),
        app("zed", "Zed", "zed", List.of("Zed.app/Contents/MacOS/cli"), List.of("--wait"), LineForm.FILE_COLON_LINE),
        app("sublime", "Sublime Text", "subl", List.of("Sublime Text.app/Contents/SharedSupport/bin/subl"), List.of("-w"),
            LineForm.FILE_COLON_LINE),
        app("bbedit", "BBEdit", "bbedit", List.of(), List.of("--wait"), LineForm.PLUS_LINE_BEFORE),
        app("textmate", "TextMate", "mate", List.of("TextMate.app/Contents/Resources/mate"), List.of("-w"), LineForm.DASH_L_LINE_BEFORE),
        app("idea", "IntelliJ IDEA", "idea", List.of(), List.of("--wait"), LineForm.LINE_OPTION_BEFORE),
        app("notepadpp", "Notepad++", "notepad++", List.of(), List.of("-multiInst", "-nosession"), LineForm.DASH_N_LINE_BEFORE),
        app("notepad", "Notepad", "notepad", List.of(), List.of(), LineForm.NONE));

    private KnownEditors() { }

    /**
     * @param id a {@code default_editor} value
     * @return the known editor with exactly this id
     */
    public static Optional<KnownEditor> byId(String id) { return ALL.stream().filter(editor -> editor.id().equals(id)).findFirst(); }

    private static KnownEditor terminal(String id, String name, List<String> executables, List<String> extraArgs, LineForm line) {
        return new KnownEditor(id, name, Kind.TERMINAL, executables, List.of(), extraArgs, List.of(), line);
    }

    private static KnownEditor app(String id, String name, String executable, List<String> macApps, List<String> waitArgs, LineForm line) {
        return new KnownEditor(id, name, Kind.APP, List.of(executable), macApps, List.of(), waitArgs, line);
    }
}
```

`plugins/editor/src/main/java/dev/jasper/editor/external/Detected.java`:

```java
package dev.jasper.editor.external;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A known editor found on this machine.
 *
 * @param editor the editor
 * @param executable its program, absolute
 */
public record Detected(KnownEditor editor, Path executable) {
    /** Rejects nulls and a relative program. */
    public Detected {
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(executable, "executable");
        if (!executable.isAbsolute()) throw new IllegalArgumentException("An editor's program must be absolute: " + executable);
    }
}
```

`plugins/editor/src/main/java/dev/jasper/editor/external/Detection.java`:

```java
package dev.jasper.editor.external;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Finds the known editors installed on this machine. Each editor's first program wins, looked for in the folders of
 * {@code PATH} in order, then in {@code /opt/homebrew/bin}, {@code /usr/local/bin} and {@code ~/.local/bin} (not on
 * Windows; a macOS app started from the Dock has a short {@code PATH}), then, on macOS, as the command-line tool inside
 * its app in {@code /Applications} and {@code ~/Applications}, then, on Windows, in Notepad++'s and Notepad's own
 * folders. On Windows a program name is tried with {@code .exe}, {@code .cmd} and {@code .bat}. Pure apart from
 * {@link Probe#executable()}; it touches the file system, so run it off the UI thread.
 */
public final class Detection {
    private static final List<String> WINDOWS_SUFFIXES = List.of(".exe", ".cmd", ".bat");

    /**
     * What detection looks at.
     *
     * @param osName the {@code os.name} property
     * @param path the folders of {@code PATH}, in order
     * @param home the user's home folder
     * @param environment Jasper's environment, for the Windows program folders
     * @param executable whether a path is a program that can run
     */
    public record Probe(String osName, List<Path> path, Path home, Map<String, String> environment, Predicate<Path> executable) {
        /** Copies the folders and the environment; rejects nulls. */
        public Probe {
            Objects.requireNonNull(osName, "osName");
            path = List.copyOf(path);
            Objects.requireNonNull(home, "home");
            environment = Map.copyOf(environment);
            Objects.requireNonNull(executable, "executable");
        }

        /**
         * This machine: {@code os.name}, the absolute folders of {@code PATH} (blank, relative and malformed entries are
         * skipped), {@code user.home}, {@code System.getenv()}, and regular files that may be executed.
         *
         * @return the probe
         */
        public static Probe system() {
            var folders = new ArrayList<Path>();
            for (String entry : Objects.requireNonNullElse(System.getenv("PATH"), "").split(Pattern.quote(File.pathSeparator))) {
                if (entry.isBlank()) continue;
                try {
                    Path folder = Path.of(entry);
                    if (folder.isAbsolute()) folders.add(folder);
                } catch (InvalidPathException malformed) {
                    // An entry that is not a path names no program.
                }
            }
            return new Probe(System.getProperty("os.name", ""), folders, Path.of(System.getProperty("user.home")), System.getenv(),
                file -> Files.isRegularFile(file) && Files.isExecutable(file));
        }
    }

    private Detection() { }

    /**
     * @param probe what to look at
     * @return each known editor found, once, with the first program found for it, in {@link KnownEditors#ALL}'s order
     */
    public static List<Detected> detect(Probe probe) {
        String os = probe.osName().toLowerCase(Locale.ROOT);
        boolean windows = os.startsWith("windows"), mac = os.startsWith("mac");
        Set<Path> folders = new LinkedHashSet<>(probe.path());
        if (!windows) {
            folders.add(Path.of("/opt/homebrew/bin"));
            folders.add(Path.of("/usr/local/bin"));
            folders.add(probe.home().resolve(".local").resolve("bin"));
        }
        List<Path> applications = mac ? List.of(Path.of("/Applications"), probe.home().resolve("Applications")) : List.of();
        var found = new ArrayList<Detected>();
        for (KnownEditor editor : KnownEditors.ALL) {
            first(editor, folders, applications, windows, probe).ifPresent(program -> found.add(new Detected(editor, program)));
        }
        return List.copyOf(found);
    }

    /** The editor's first program: in the folders, then inside its apps, then in its Windows folders. */
    private static Optional<Path> first(KnownEditor editor, Set<Path> folders, List<Path> applications, boolean windows, Probe probe) {
        for (Path folder : folders) {
            Optional<Path> program = program(editor, folder, windows, probe);
            if (program.isPresent()) return program;
        }
        for (Path folder : applications) {
            for (String tool : editor.macApps()) {
                Optional<Path> program = runnable(folder.resolve(tool), probe);
                if (program.isPresent()) return program;
            }
        }
        if (windows) {
            for (Path folder : windowsFolders(editor, probe)) {
                Optional<Path> program = program(editor, folder, true, probe);
                if (program.isPresent()) return program;
            }
        }
        return Optional.empty();
    }

    /** The editor's first program name in the folder; on Windows each name with .exe, .cmd or .bat. */
    private static Optional<Path> program(KnownEditor editor, Path folder, boolean windows, Probe probe) {
        for (String name : editor.executables()) {
            for (String file : windows ? WINDOWS_SUFFIXES.stream().map(suffix -> name + suffix).toList() : List.of(name)) {
                Optional<Path> program = runnable(folder.resolve(file), probe);
                if (program.isPresent()) return program;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> runnable(Path candidate, Probe probe) {
        Path absolute = candidate.toAbsolutePath().normalize();
        return probe.executable().test(absolute) ? Optional.of(absolute) : Optional.empty();
    }

    /** Where Notepad++ and Notepad install on Windows, from Jasper's environment. */
    private static List<Path> windowsFolders(KnownEditor editor, Probe probe) {
        var folders = new ArrayList<Path>();
        switch (editor.id()) {
            case "notepadpp" -> {
                folder(probe, "ProgramFiles").ifPresent(folder -> folders.add(folder.resolve("Notepad++")));
                folder(probe, "ProgramFiles(x86)").ifPresent(folder -> folders.add(folder.resolve("Notepad++")));
            }
            case "notepad" -> folder(probe, "SystemRoot").ifPresent(folder -> folders.add(folder.resolve("System32")));
            default -> { }
        }
        return folders;
    }

    /** The folder an environment variable names; Windows variable names ignore case. */
    private static Optional<Path> folder(Probe probe, String variable) {
        return probe.environment().entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(variable))
            .map(Map.Entry::getValue).filter(value -> !value.isBlank()).findFirst().flatMap(value -> {
                try {
                    return Optional.of(Path.of(value));
                } catch (InvalidPathException malformed) {
                    return Optional.empty();
                }
            });
    }
}
```

Append the ledger rows:

```bash
python3 - <<'PY'
import pathlib
csv = pathlib.Path('docs/design/theme-engine/migration.csv')
text = csv.read_text(encoding='utf-8')
main, test = 'plugins/editor/src/main/java/dev/jasper/editor/external/', 'plugins/editor/src/test/java/dev/jasper/editor/external/'
rows = [
    main + 'KnownEditor.java,dev.jasper.editor,KnownEditor,nonvisual-helper,none,none,' + test + 'KnownEditorsTest.java,New in the default editor plan Task 6; a known editor value with no Swing or theme dependency',
    main + 'KnownEditors.java,dev.jasper.editor,KnownEditors,nonvisual-helper,none,none,' + test + 'KnownEditorsTest.java,New in the default editor plan Task 6; the known-editor table with no Swing or theme dependency; matched only by the KnownEditor constructor',
    main + 'Detected.java,dev.jasper.editor,Detected,nonvisual-helper,none,none,' + test + 'DetectionTest.java,New in the default editor plan Task 6; a found editor value with no Swing or theme dependency',
    main + 'Detection.java,dev.jasper.editor,Detection,nonvisual-helper,none,none,' + test + 'DetectionTest.java,New in the default editor plan Task 6; finds installed editors with no Swing or theme dependency; matched only by collection construction',
    test + 'KnownEditorsTest.java,dev.jasper.editor,KnownEditorsTest,nonvisual-helper,none,none,' + test + 'KnownEditorsTest.java,Test/fixture ownership; New in the default editor plan Task 6; no Swing or theme dependency',
    test + 'DetectionTest.java,dev.jasper.editor,DetectionTest,nonvisual-helper,none,none,' + test + 'DetectionTest.java,Test/fixture ownership; New in the default editor plan Task 6; no Swing or theme dependency',
]
assert text.endswith('\n')
for row in rows:
    assert row.count(',') == 7, row
    assert '\n' + row.split(',', 1)[0] + ',' not in text, row
csv.write_text(text + ''.join(row + '\n' for row in rows), encoding='utf-8')
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.external.*' verifyPluginArchitecture :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (`KnownEditorsTest` 2, `DetectionTest` 9). `verifyPluginArchitecture` passes: the new classes reference
only `java.*`.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/external/package-info.java \
        plugins/editor/src/main/java/dev/jasper/editor/external/KnownEditor.java \
        plugins/editor/src/main/java/dev/jasper/editor/external/KnownEditors.java \
        plugins/editor/src/main/java/dev/jasper/editor/external/Detected.java \
        plugins/editor/src/main/java/dev/jasper/editor/external/Detection.java \
        plugins/editor/src/test/java/dev/jasper/editor/external/KnownEditorsTest.java \
        plugins/editor/src/test/java/dev/jasper/editor/external/DetectionTest.java \
        docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Editor: the known editors and finding them on this machine

Sixteen editors from the plan's table, found on PATH, then in Homebrew's,
/usr/local/bin and ~/.local/bin, then inside macOS apps, then in Notepad++'s
and Notepad's Windows folders; Windows names carry .exe, .cmd or .bat.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: `EDITOR` values and launch lines

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/external/{Choice,EditorCommands}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/external/EditorCommandsTest.java`
- Modify: `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: `KnownEditor`, `KnownEditors`, `Detected` (Task 6).
- Produces: `sealed interface Choice permits Choice.Jasper, Choice.System, Choice.Installed, Choice.Custom` with
  `record Jasper(Optional<Path> client)`, `record System()`, `record Installed(Detected editor)`,
  `record Custom(String command, boolean inTerminal)`; `EditorCommands.quote(String text, String osName)`,
  `variable(Choice, String osName)` → `Optional<String>`, `openAt(Detected, Path file, OptionalInt line)` →
  `List<String>`, `custom(String command, Path file, String osName)` → `List<String>`,
  `keepsEnvironment(Map<String, String> jasperEnvironment, boolean keepSetting)`.

- [ ] **Step 1: Write the failing tests**

`plugins/editor/src/test/java/dev/jasper/editor/external/EditorCommandsTest.java`:

```java
package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class EditorCommandsTest {
    private static final Path ROOT = Path.of("").toAbsolutePath().getRoot();
    private static final Path FILE = ROOT.resolve("work").resolve("config.toml");
    private static final String MAC = "Mac OS X", LINUX = "Linux", WINDOWS = "Windows 11";

    /** The editor as detection would report it, its first program name in {@code <root>/bin}. */
    private static Detected detected(String id) {
        KnownEditor editor = KnownEditors.byId(id).orElseThrow();
        return new Detected(editor, ROOT.resolve("bin").resolve(editor.executables().getFirst()));
    }

    private static String bin(String name) { return ROOT.resolve("bin").resolve(name).toString(); }

    @Test void posixShellsGetSingleQuotesAndWindowsDoubleQuotes() {
        assertThat(EditorCommands.quote("/opt/homebrew/bin/nvim", MAC)).isEqualTo("'/opt/homebrew/bin/nvim'");
        assertThat(EditorCommands.quote("/Applications/Jasper Beta.app/Contents/Resources/bin/jasper", LINUX))
            .isEqualTo("'/Applications/Jasper Beta.app/Contents/Resources/bin/jasper'");
        assertThat(EditorCommands.quote("it's $(x) `y` \"z\"", MAC)).as("nothing expands inside single quotes")
            .isEqualTo("'it'\\''s $(x) `y` \"z\"'");
        assertThat(EditorCommands.quote("''", LINUX)).isEqualTo("''\\'''\\'''");
        assertThat(EditorCommands.quote("", LINUX)).isEqualTo("''");
        assertThat(EditorCommands.quote("C:\\Program Files\\Microsoft VS Code\\bin\\code.cmd", WINDOWS))
            .isEqualTo("\"C:\\Program Files\\Microsoft VS Code\\bin\\code.cmd\"");
    }

    @Test void editorAndVisualHoldTheQuotedProgramAndWhatMakesItWait() {
        Path client = ROOT.resolve("Applications").resolve("Jasper Beta.app").resolve("Contents").resolve("Resources").resolve("bin").resolve("jasper");
        assertThat(EditorCommands.variable(new Choice.Jasper(Optional.of(client)), MAC)).contains("'" + client + "' editor --wait");
        assertThat(EditorCommands.variable(new Choice.Jasper(Optional.empty()), MAC)).as("no jasper command").isEmpty();
        assertThat(EditorCommands.variable(new Choice.Installed(detected("nvim")), MAC)).contains("'" + bin("nvim") + "'");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("emacs")), LINUX)).contains("'" + bin("emacs") + "' -nw");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("code")), MAC)).contains("'" + bin("code") + "' --wait");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("sublime")), LINUX)).contains("'" + bin("subl") + "' -w");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("notepadpp")), WINDOWS))
            .contains("\"" + bin("notepad++") + "\" -multiInst -nosession");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("notepad")), WINDOWS)).contains("\"" + bin("notepad") + "\"");
        assertThat(EditorCommands.variable(new Choice.Custom("subl -w", true), MAC)).as("as written").contains("subl -w");
        assertThat(EditorCommands.variable(new Choice.Custom("  ", false), MAC)).isEmpty();
        assertThat(EditorCommands.variable(new Choice.System(), MAC)).isEmpty();
    }

    @Test void openAtPutsTheLineWhereEachEditorReadsItAndNeverWaits() {
        String file = FILE.toString();
        var atLine = new LinkedHashMap<String, List<String>>();
        atLine.put("nvim", List.of(bin("nvim"), "+42", file));
        atLine.put("vim", List.of(bin("vim"), "+42", file));
        atLine.put("vi", List.of(bin("vi"), "+42", file));
        atLine.put("nano", List.of(bin("nano"), "+42", file));
        atLine.put("micro", List.of(bin("micro"), "+42", file));
        atLine.put("helix", List.of(bin("hx"), file + ":42"));
        atLine.put("emacs", List.of(bin("emacs"), "-nw", "+42", file));
        atLine.put("code", List.of(bin("code"), "-g", file + ":42"));
        atLine.put("cursor", List.of(bin("cursor"), "-g", file + ":42"));
        atLine.put("zed", List.of(bin("zed"), file + ":42"));
        atLine.put("sublime", List.of(bin("subl"), file + ":42"));
        atLine.put("bbedit", List.of(bin("bbedit"), "+42", file));
        atLine.put("textmate", List.of(bin("mate"), "-l", "42", file));
        atLine.put("idea", List.of(bin("idea"), "--line", "42", file));
        atLine.put("notepadpp", List.of(bin("notepad++"), "-n42", file));
        atLine.put("notepad", List.of(bin("notepad"), file));
        assertThat(atLine.keySet()).as("every known editor").containsExactlyElementsOf(KnownEditors.ALL.stream().map(KnownEditor::id).toList());
        atLine.forEach((id, expected) -> assertThat(EditorCommands.openAt(detected(id), FILE, OptionalInt.of(42))).as(id).isEqualTo(expected));
    }

    @Test void withoutALineEveryEditorGetsJustTheFile() {
        for (KnownEditor editor : KnownEditors.ALL) {
            var expected = new ArrayList<String>();
            expected.add(bin(editor.executables().getFirst()));
            expected.addAll(editor.extraArgs());
            expected.add(FILE.toString());
            assertThat(EditorCommands.openAt(detected(editor.id()), FILE, OptionalInt.empty())).as(editor.id()).isEqualTo(expected);
            assertThat(EditorCommands.openAt(detected(editor.id()), FILE, OptionalInt.of(0))).as(editor.id() + " at line 0").isEqualTo(expected);
        }
        assertThat(EditorCommands.openAt(detected("cursor"), FILE, OptionalInt.empty())).as("no -g without a line").doesNotContain("-g");
    }

    @Test void aCustomCommandGetsTheQuotedFileThroughTheShell() {
        Path file = ROOT.resolve("notes").resolve("it's \u00fc.txt");
        assertThat(EditorCommands.custom("subl -w", file, MAC))
            .containsExactly("/bin/sh", "-c", "subl -w '" + file.toString().replace("'", "'\\''") + "'");
        assertThat(EditorCommands.custom("notepad++ -multiInst", file, WINDOWS)).containsExactly("cmd.exe", "/c", "notepad++ -multiInst \"" + file + "\"");
    }

    /** Review Focus 2: git prefers VISUAL, so either one in Jasper's environment keeps both, unless the setting is off. */
    @Test void editorOrVisualInJaspersEnvironmentKeepsBothUnlessTheSettingIsOff() {
        assertThat(EditorCommands.keepsEnvironment(Map.of("EDITOR", "vim"), true)).isTrue();
        assertThat(EditorCommands.keepsEnvironment(Map.of("VISUAL", "code --wait"), true)).as("VISUAL alone").isTrue();
        assertThat(EditorCommands.keepsEnvironment(Map.of("EDITOR", " ", "VISUAL", ""), true)).as("blank values").isFalse();
        assertThat(EditorCommands.keepsEnvironment(Map.of("PATH", "/usr/bin"), true)).as("neither").isFalse();
        assertThat(EditorCommands.keepsEnvironment(Map.of("EDITOR", "vim"), false)).as("the setting off").isFalse();
    }

    /**
     * Review Focus 1: a program under a folder whose name holds a space, a quote and a non-ASCII letter, and a file
     * named like that, survive {@code /bin/sh -c "$EDITOR file"} and git's own {@code sh -c '$EDITOR "$@"'}. Runs only
     * {@code /bin/sh} and this test's script, which records its arguments.
     */
    @Test @DisabledOnOs(value = OS.WINDOWS, disabledReason = "EDITOR is read by a POSIX shell")
    void quotedValuesRunThatProgramWithThatFileThroughTheShell(@TempDir Path temp) throws Exception {
        Path bin = Files.createDirectories(temp.resolve("Jasper Beta's \u00fc.app").resolve("bin"));
        Path program = Files.writeString(bin.resolve("record"), "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$RECORD\"\n");
        Files.setPosixFilePermissions(program, PosixFilePermissions.fromString("rwx------"));
        Path file = Files.writeString(temp.resolve("it's \u00fc.txt"), "text\n");
        String os = System.getProperty("os.name");
        Detected nvim = new Detected(KnownEditors.byId("nvim").orElseThrow(), program);
        Detected code = new Detected(KnownEditors.byId("code").orElseThrow(), program);
        assertThat(shell(temp, EditorCommands.variable(new Choice.Jasper(Optional.of(program)), os).orElseThrow(), file))
            .containsExactly("editor", "--wait", file.toString());
        assertThat(shell(temp, EditorCommands.variable(new Choice.Installed(nvim), os).orElseThrow(), file)).containsExactly(file.toString());
        assertThat(shell(temp, EditorCommands.variable(new Choice.Installed(code), os).orElseThrow(), file)).containsExactly("--wait", file.toString());
        String custom = EditorCommands.quote(program.toString(), os) + " --new-window";
        assertThat(shell(temp, EditorCommands.variable(new Choice.Custom(custom, false), os).orElseThrow(), file))
            .containsExactly("--new-window", file.toString());
        assertThat(run(temp, EditorCommands.custom(custom, file, os))).as("Jasper's own custom launch line")
            .containsExactly("--new-window", file.toString());
    }

    /** What the program got from {@code sh -c "<value> <quoted file>"}, checked against git's form. */
    private static List<String> shell(Path temp, String value, Path file) throws Exception {
        List<String> quoted = run(temp, List.of("/bin/sh", "-c", value + " " + EditorCommands.quote(file.toString(), System.getProperty("os.name"))));
        assertThat(run(temp, List.of("/bin/sh", "-c", value + " \"$@\"", value, file.toString()))).as("as git runs it").isEqualTo(quoted);
        return quoted;
    }

    private static List<String> run(Path temp, List<String> command) throws Exception {
        Path record = temp.resolve("arguments.txt");
        Files.deleteIfExists(record);
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("RECORD", record.toString());
        Process process = builder.start();
        process.getOutputStream().close();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("the shell finished").isTrue();
        assertThat(process.exitValue()).as(String.join(" ", command)).isZero();
        return Files.readAllLines(record, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.external.EditorCommandsTest'`
Expected: `compileTestJava` FAILED: `cannot find symbol` for `EditorCommands` and `Choice`.

- [ ] **Step 3: Write the implementation**

`plugins/editor/src/main/java/dev/jasper/editor/external/Choice.java`:

```java
package dev.jasper.editor.external;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What the Default editor settings choose, once detection has said what is installed. */
public sealed interface Choice permits Choice.Jasper, Choice.System, Choice.Installed, Choice.Custom {
    /**
     * The Jasper Editor.
     *
     * @param client the {@code jasper} command, for {@code EDITOR}; empty when there is none
     */
    record Jasper(Optional<Path> client) implements Choice {
        /** Rejects null. */
        public Jasper { Objects.requireNonNull(client, "client"); }
    }

    /** The system's default app opens files; {@code EDITOR} and {@code VISUAL} are left alone. */
    record System() implements Choice { }

    /**
     * An editor detection found.
     *
     * @param editor the editor and its program
     */
    record Installed(Detected editor) implements Choice {
        /** Rejects null. */
        public Installed { Objects.requireNonNull(editor, "editor"); }
    }

    /**
     * The user's own command.
     *
     * @param command the command line as written; the file is added at the end
     * @param inTerminal whether it runs in a terminal tab
     */
    record Custom(String command, boolean inTerminal) implements Choice {
        /** Rejects null. */
        public Custom { Objects.requireNonNull(command, "command"); }
    }
}
```

`plugins/editor/src/main/java/dev/jasper/editor/external/EditorCommands.java`:

```java
package dev.jasper.editor.external;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The {@code EDITOR} values and command lines of the default editor. Pure. POSIX shells get single quotes (each
 * {@code '} written {@code '\''}); Windows gets double quotes, which a Windows path never holds.
 */
public final class EditorCommands {
    private EditorCommands() { }

    /**
     * @param text a path or an argument
     * @param osName the {@code os.name} property
     * @return the text quoted for {@code sh}, or for {@code cmd.exe} on Windows
     */
    public static String quote(String text, String osName) {
        return windows(osName) ? "\"" + text + "\"" : "'" + text.replace("'", "'\\''") + "'";
    }

    /**
     * What {@code EDITOR} and {@code VISUAL} hold for a choice. Programs read the value as a shell command line and add
     * the file, so it is the quoted program, its fixed arguments and the arguments that make it wait.
     *
     * @param choice what the settings choose
     * @param osName the {@code os.name} property
     * @return the value; empty for the system's app, a blank custom command, or the Jasper Editor without a
     *     {@code jasper} command
     */
    public static Optional<String> variable(Choice choice, String osName) {
        return switch (choice) {
            case Choice.Jasper jasper -> jasper.client().map(client -> quote(client.toString(), osName) + " editor --wait");
            case Choice.Installed installed -> {
                var words = new ArrayList<String>();
                words.add(quote(installed.editor().executable().toString(), osName));
                words.addAll(installed.editor().editor().extraArgs());
                words.addAll(installed.editor().editor().waitArgs());
                yield Optional.of(String.join(" ", words));
            }
            case Choice.Custom custom -> custom.command().isBlank() ? Optional.empty() : Optional.of(custom.command());
            case Choice.System system -> Optional.empty();
        };
    }

    /**
     * The command that opens a file in an installed editor at a line. It never waits: Jasper runs terminal editors in a
     * tab and starts app editors on their own.
     *
     * @param editor the editor and its program
     * @param file the file, absolute
     * @param line 1-based; empty, or below 1, opens the file without a line
     * @return the program and its arguments
     */
    public static List<String> openAt(Detected editor, Path file, OptionalInt line) {
        var command = new ArrayList<String>();
        command.add(editor.executable().toString());
        command.addAll(editor.editor().extraArgs());
        String path = file.toString();
        if (line.isEmpty() || line.getAsInt() < 1) {
            command.add(path);
            return List.copyOf(command);
        }
        String at = Integer.toString(line.getAsInt());
        switch (editor.editor().lineForm()) {
            case PLUS_LINE_BEFORE -> command.addAll(List.of("+" + at, path));
            case FILE_COLON_LINE -> command.add(path + ":" + at);
            case DASH_G_FILE_COLON_LINE -> command.addAll(List.of("-g", path + ":" + at));
            case DASH_L_LINE_BEFORE -> command.addAll(List.of("-l", at, path));
            case LINE_OPTION_BEFORE -> command.addAll(List.of("--line", at, path));
            case DASH_N_LINE_BEFORE -> command.addAll(List.of("-n" + at, path));
            case NONE -> command.add(path);
        }
        return List.copyOf(command);
    }

    /**
     * A custom command with the quoted file added, run by {@code /bin/sh -c}, or {@code cmd.exe /c} on Windows, so the
     * command line may carry its own arguments and quoting.
     *
     * @param command the command line as written
     * @param file the file, absolute
     * @param osName the {@code os.name} property
     * @return the shell and its arguments
     */
    public static List<String> custom(String command, Path file, String osName) {
        String line = command + " " + quote(file.toString(), osName);
        return windows(osName) ? List.of("cmd.exe", "/c", line) : List.of("/bin/sh", "-c", line);
    }

    /**
     * Whether Jasper's shells keep the {@code EDITOR} and {@code VISUAL} Jasper itself was started with. git prefers
     * {@code VISUAL}, so setting one beside the user's other would take over: either one keeps both.
     *
     * @param jasperEnvironment Jasper's own environment
     * @param keepSetting {@code keep_environment_editor}
     * @return true when the setting is on and either variable is set and not blank
     */
    public static boolean keepsEnvironment(Map<String, String> jasperEnvironment, boolean keepSetting) {
        return keepSetting && (set(jasperEnvironment.get("EDITOR")) || set(jasperEnvironment.get("VISUAL")));
    }

    private static boolean set(String value) { return value != null && !value.isBlank(); }

    private static boolean windows(String osName) { return osName.toLowerCase(Locale.ROOT).startsWith("windows"); }
}
```

Append the ledger rows:

```bash
python3 - <<'PY'
import pathlib
csv = pathlib.Path('docs/design/theme-engine/migration.csv')
text = csv.read_text(encoding='utf-8')
main, test = 'plugins/editor/src/main/java/dev/jasper/editor/external/', 'plugins/editor/src/test/java/dev/jasper/editor/external/'
rows = [
    main + 'Choice.java,dev.jasper.editor,Choice,nonvisual-helper,none,none,' + test + 'EditorCommandsTest.java,New in the default editor plan Task 7; the chosen editor as a value with no Swing or theme dependency',
    main + 'EditorCommands.java,dev.jasper.editor,EditorCommands,nonvisual-helper,none,none,' + test + 'EditorCommandsTest.java,New in the default editor plan Task 7; EDITOR values and command lines with no Swing or theme dependency; matched only by collection construction',
    test + 'EditorCommandsTest.java,dev.jasper.editor,EditorCommandsTest,nonvisual-helper,none,none,' + test + 'EditorCommandsTest.java,Test/fixture ownership; New in the default editor plan Task 7; no Swing or theme dependency; matched only by collection construction',
]
assert text.endswith('\n')
for row in rows:
    assert row.count(',') == 7, row
    assert '\n' + row.split(',', 1)[0] + ',' not in text, row
csv.write_text(text + ''.join(row + '\n' for row in rows), encoding='utf-8')
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.external.*' verifyPluginArchitecture :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS. On macOS and Linux `quotedValuesRunThatProgramWithThatFileThroughTheShell` runs `/bin/sh` nine times
on the temporary script; on Windows it is skipped.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/external/Choice.java \
        plugins/editor/src/main/java/dev/jasper/editor/external/EditorCommands.java \
        plugins/editor/src/test/java/dev/jasper/editor/external/EditorCommandsTest.java \
        docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Editor: EDITOR values and launch lines for the default editor

Quoted for sh (or cmd.exe on Windows): the jasper command with
"editor --wait", a terminal editor's path, an app editor's path and wait
arguments, or a custom command as written. Opening at a line uses each
editor's own form and never waits; either EDITOR or VISUAL in Jasper's
environment keeps both.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: The four settings, `DefaultEditor`, and the Editor using them (Editor 0.9.0)

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/external/DefaultEditor.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/{EditorSettings,EditorWindow,package-info}.java`,
  `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`,
  `plugins/editor/src/main/resources/{plugin,settings}.toml`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/StagedEditorHost.java`, `docs/design/theme-engine/migration.csv`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/external/DefaultEditorTest.java` (new),
  `plugins/editor/src/test/java/dev/jasper/editor/{EditorPluginTest,EditorCommandTest}.java`,
  `plugins/editor/src/test/java/dev/jasper/editor/ui/{EditorSettingsTest,EditorWindowTest}.java`
- Unchanged, checked: `PluginPreviewSupport` starts an in-memory `dev.example.editor` with hand-written declarations and
  no capabilities, not the Editor; `EditorWindowTest`, `DialogsTest`, `IndexerPluginTest` and `RemoteEditingPluginTest`
  start lambda stand-ins. `EditorPluginTest`, `EditorCommandTest` and `StagedEditorHost` are the only starts of the
  real `EditorPlugin` in tests; `BundledSamplePluginTest` and `PluginSettingsPagesEvidenceTest` load the staged plugin
  with its manifest.

**Interfaces:**
- Consumes: SDK 0.17.1 (Task 1): `Capabilities.TERMINAL_ENVIRONMENT`, `Capabilities.EDITOR_PROVIDE`, `FileEditor`,
  `Platform.editor(FileEditor)`, `Terminals.environment(Function<Map<String, String>, Map<String, String>>)`,
  `LocalSpec.running(List<String>, Optional<Path>)`, `CommandLine.client()`. Testkit (Tasks 2–5):
  `FakePluginHost.shellEnvironment(Map)`, `edit(Path, OptionalInt, Optional<UUID>)`, `editorPlugin()`,
  `setCommandClient(Path)`, the `|run:` lines of `openRequests()`, the fake's capability gates and its
  `Platform.openInEditor` asking the provided editor first. App (Tasks 2–5): `DescriptorParser` knows both
  capabilities; `PluginHost` hosts contributors and the file editor without fixture wiring. Tasks 6–7.
- Produces:
  `DefaultEditor(Supplier<DefaultEditor.Preference>, Supplier<Detection.Probe>, Executor background, Executor ui, Terminals,
  FileEditor jasper, DefaultEditor.Launcher, Optional<Path> client, String osName, System.Logger)` with
  `record Preference(String editor, String customCommand, boolean customInTerminal, boolean keepEnvironment)`,
  `@FunctionalInterface interface Launcher { boolean start(List<String> command); static Launcher detached(System.Logger log); }`,
  `detect()`, `detected()` → `List<Detected>`, `onDetected(Runnable)` → `Subscription`, `choice()` → `Choice`,
  `variables(Map<String, String>)` → `Map<String, String>`, `edit(Path, OptionalInt, Optional<WindowOwner>)` → `boolean`;
  `EditorWindow.edit(Path, OptionalInt, Optional<WindowOwner>)` → `boolean` (the Jasper Editor's own file editor);
  `EditorSettings.Declared.defaultEditor()`, `customCommand()`, `customInTerminal()`, `keepEnvironmentEditor()` and the
  package-private handles `defaultEditor`, `customCommand`, `customInTerminal`, `keepEnvironmentEditor` (Task 9);
  `EditorPlugin(Executor, BiFunction<...>, Supplier<Detection.Probe>, Function<System.Logger, DefaultEditor.Launcher>)`
  (package-private; the two-argument one now finds no editor and starts no program); Editor 0.9.0.
- The Jasper choice goes through the `FileEditor` argument, not `Supplier<EditorWindow>`, so `external` stays JDK and
  SDK only (`ui` → `external`, never back). `DefaultEditorTest` records that call; the real tab is asserted in
  `EditorWindowTest` and end to end in `EditorPluginTest`.

- [ ] **Step 1: Write the failing tests**

`plugins/editor/src/test/java/dev/jasper/editor/external/DefaultEditorTest.java`:

```java
package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTests.class)
class DefaultEditorTest {
    private static final String MAC = "Mac OS X", WINDOWS = "Windows 11";
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.9.0", Set.of(Capabilities.PALETTE_CONTRIBUTE,
        Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE, Capabilities.TERMINAL_OPEN));

    /** One call to the Jasper Editor's own file editor. */
    private record Opened(Path file, OptionalInt line, Optional<WindowOwner> window) { }

    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private Path bin, file, client;
    private final Set<Path> programs = new HashSet<>();
    private final AtomicReference<DefaultEditor.Preference> preference =
        new AtomicReference<>(new DefaultEditor.Preference("jasper", "", true, true));
    private final AtomicBoolean failing = new AtomicBoolean();
    private final List<Opened> opened = new ArrayList<>();
    private final List<List<String>> launched = new ArrayList<>();
    private boolean starts = true;

    @BeforeEach void start() throws Exception {
        host = new FakePluginHost(home.resolve("plugins"));
        context = host.start(INFO, Set.of(), Set.of(), c -> { });
        bin = Files.createDirectories(home.resolve("bin"));
        file = Files.writeString(Files.createDirectories(home.resolve("work")).resolve("config.toml"), "[window]\n");
        client = home.resolve("Jasper Beta.app").resolve("bin").resolve("jasper");
    }

    @AfterEach void close() { host.close(); }

    /** The default editor over a machine whose programs are {@link #programs}; the launcher records instead of starting. */
    private DefaultEditor editor(String osName, Optional<Path> jasperCommand) {
        return new DefaultEditor(preference::get, () -> {
                if (failing.get()) throw new IllegalStateException("PATH could not be read");
                return new Detection.Probe(osName, List.of(bin), home, Map.of(), programs::contains);
            },
            context.background(), Runnable::run, context.terminals(),
            (path, line, window) -> { opened.add(new Opened(path, line, window)); return true; },
            command -> { launched.add(command); return starts; }, jasperCommand, osName, context.log());
    }

    /** The default editor once its first detection has run. */
    private DefaultEditor detected(String osName) {
        DefaultEditor editor = editor(osName, Optional.of(client));
        editor.detect();
        drain();
        return editor;
    }

    private void drain() { while (host.runBackground() > 0) { } }

    /** A program detection finds in {@code bin}. */
    private Path install(String name) {
        Path program = bin.resolve(name).toAbsolutePath().normalize();
        programs.add(program);
        return program;
    }

    private void choose(String editor) { preference.set(new DefaultEditor.Preference(editor, "", true, true)); }

    private static Map<String, String> both(String value) { return Map.of("EDITOR", value, "VISUAL", value); }

    private String tab(UUID window, String command) { return "tab|" + window + "|" + file.getParent() + "|run:" + command; }

    @Test void theJasperEditorOpensTheFileItself() {
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.choice()).isEqualTo(new Choice.Jasper(Optional.of(client)));
        assertThat(defaults.edit(file, OptionalInt.of(3), Optional.empty())).isTrue();
        assertThat(opened).containsExactly(new Opened(file, OptionalInt.of(3), Optional.empty()));
        assertThat(launched).isEmpty();
        assertThat(host.openRequests()).isEmpty();
    }

    @Test void aTerminalEditorRunsAtTheLineInATabOfTheCallsWindow() {
        Path nvim = install("nvim");
        choose("nvim");
        DefaultEditor defaults = detected(MAC);
        UUID window = host.addTerminalWindow();
        WindowHandle handle = context.terminals().window(window).orElseThrow();
        assertThat(defaults.edit(file, OptionalInt.of(42), Optional.of(handle))).isTrue();
        assertThat(host.openRequests()).containsExactly(tab(window, nvim + " +42 " + file));
        assertThat(opened).isEmpty();
    }

    @Test void withoutATerminalWindowATerminalEditorUsesTheActiveOneOrLeavesTheFileToTheDesktop() {
        Path emacs = install("emacs");
        choose("emacs");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty())).as("no terminal window is open").isFalse();
        UUID active = host.addTerminalWindow();
        host.activateTerminalWindow(active);
        assertThat(defaults.edit(file, OptionalInt.empty(), Optional.of(new WindowOwner() { }))).as("not a terminal window").isTrue();
        assertThat(host.openRequests()).containsExactly(tab(active, emacs + " -nw " + file));
    }

    @Test void anAppEditorStartsOnItsOwnAtTheLineWithoutWaiting() {
        Path code = install("code");
        choose("code");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.edit(file, OptionalInt.of(7), Optional.empty())).isTrue();
        assertThat(launched).containsExactly(List.of(code.toString(), "-g", file + ":7"));
        starts = false;
        assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty())).as("it did not start").isFalse();
        assertThat(launched.getLast()).containsExactly(code.toString(), file.toString());
        assertThat(host.openRequests()).isEmpty();
    }

    @Test void aCustomCommandRunsInATabOrOnItsOwnWithTheQuotedFileAdded() {
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        preference.set(new DefaultEditor.Preference("custom", "subl -w", true, true));
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.choice()).isEqualTo(new Choice.Custom("subl -w", true));
        assertThat(defaults.edit(file, OptionalInt.of(9), Optional.empty())).isTrue();
        assertThat(host.openRequests()).containsExactly(tab(window, "/bin/sh -c subl -w " + EditorCommands.quote(file.toString(), MAC)));
        preference.set(new DefaultEditor.Preference("custom", "subl -w", false, true));
        assertThat(defaults.edit(file, OptionalInt.of(9), Optional.empty())).isTrue();
        assertThat(launched).containsExactly(List.of("/bin/sh", "-c", "subl -w " + EditorCommands.quote(file.toString(), MAC)));
        assertThat(detected(WINDOWS).edit(file, OptionalInt.empty(), Optional.empty())).isTrue();
        assertThat(launched.getLast()).containsExactly("cmd.exe", "/c", "subl -w \"" + file + "\"");
    }

    @Test void theSystemsAppAndABlankCustomCommandLeaveTheFileToTheDesktop() {
        host.activateTerminalWindow(host.addTerminalWindow());
        choose("system");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.edit(file, OptionalInt.of(1), Optional.empty())).isFalse();
        preference.set(new DefaultEditor.Preference("custom", "  ", true, true));
        assertThat(defaults.choice()).isEqualTo(new Choice.System());
        assertThat(defaults.edit(file, OptionalInt.of(1), Optional.empty())).isFalse();
        assertThat(opened).isEmpty();
        assertThat(launched).isEmpty();
        assertThat(host.openRequests()).isEmpty();
    }

    @Test void editorAndVisualForEachChoice() {
        Path nvim = install("nvim"), emacs = install("emacs"), code = install("code");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.variables(Map.of())).isEqualTo(both(EditorCommands.quote(client.toString(), MAC) + " editor --wait"));
        assertThat(editor(MAC, Optional.empty()).variables(Map.of())).as("no jasper command").isEmpty();
        choose("nvim");
        assertThat(defaults.variables(Map.of())).isEqualTo(both(EditorCommands.quote(nvim.toString(), MAC)));
        choose("emacs");
        assertThat(defaults.variables(Map.of())).isEqualTo(both(EditorCommands.quote(emacs.toString(), MAC) + " -nw"));
        choose("code");
        assertThat(defaults.variables(Map.of())).isEqualTo(both(EditorCommands.quote(code.toString(), MAC) + " --wait"));
        preference.set(new DefaultEditor.Preference("custom", "subl -w", true, true));
        assertThat(defaults.variables(Map.of())).isEqualTo(both("subl -w"));
        preference.set(new DefaultEditor.Preference("custom", "", true, true));
        assertThat(defaults.variables(Map.of())).isEmpty();
        choose("system");
        assertThat(defaults.variables(Map.of())).isEmpty();
    }

    /** Review Focus 2: Jasper started from a terminal with EDITOR=vim sets neither variable until the setting is off. */
    @Test void editorOrVisualInJaspersEnvironmentKeepsBothUntilTheSettingIsOff() {
        DefaultEditor defaults = detected(MAC);
        String jasper = EditorCommands.quote(client.toString(), MAC) + " editor --wait";
        assertThat(defaults.variables(Map.of("EDITOR", "vim"))).isEmpty();
        assertThat(defaults.variables(Map.of("VISUAL", "vim"))).isEmpty();
        assertThat(defaults.variables(Map.of("EDITOR", "", "VISUAL", " "))).as("blank values").isEqualTo(both(jasper));
        preference.set(new DefaultEditor.Preference("jasper", "", true, false));
        assertThat(defaults.variables(Map.of("EDITOR", "vim"))).isEqualTo(both(jasper));
    }

    /** Review Focus 5: the saved editor was uninstalled. */
    @Test void aSavedEditorThatIsNotInstalledIsTheSystemsApp() {
        host.activateTerminalWindow(host.addTerminalWindow());
        choose("nvim");
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.detected()).isEmpty();
        assertThat(defaults.choice()).isEqualTo(new Choice.System());
        assertThat(defaults.variables(Map.of())).isEmpty();
        assertThat(defaults.edit(file, OptionalInt.of(2), Optional.empty())).isFalse();
        assertThat(host.openRequests()).isEmpty();
        assertThat(launched).isEmpty();
    }

    @Test void detectionRunsInTheBackgroundAndTellsItsListenersOnTheUiThread() {
        Path nvim = install("nvim");
        DefaultEditor defaults = editor(MAC, Optional.of(client));
        var heard = new AtomicInteger();
        Subscription listening = defaults.onDetected(heard::incrementAndGet);
        defaults.detect();
        assertThat(defaults.detected()).as("before the background task ran").isEmpty();
        assertThat(heard).hasValue(0);
        drain();
        assertThat(defaults.detected()).extracting(found -> found.editor().id() + "=" + found.executable()).containsExactly("nvim=" + nvim);
        assertThat(heard).hasValue(1);
        listening.close();
        defaults.detect();
        drain();
        assertThat(heard).hasValue(1);
    }

    @Test void aFailedDetectionKeepsWhatWasFound() {
        install("vim");
        DefaultEditor defaults = detected(MAC);
        var heard = new AtomicInteger();
        defaults.onDetected(heard::incrementAndGet);
        failing.set(true);
        defaults.detect();
        drain();
        assertThat(defaults.detected()).extracting(found -> found.editor().id()).containsExactly("vim");
        assertThat(heard).as("nothing new to tell").hasValue(0);
    }
}
```

Add to `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java')
t = p.read_text(encoding='utf-8')
for old, new in [
    ("import java.util.Optional;\n", "import java.util.Optional;\nimport java.util.OptionalInt;\nimport java.util.UUID;\n"),
    ("import dev.jasper.sdk.WindowOwner;\n", "import dev.jasper.sdk.WindowOwner;\nimport dev.jasper.sdk.terminal.WindowHandle;\n"),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
t = t.rstrip()
assert t.endswith('}')
t = t[:-1] + r'''
    @Test void editOpensAnExistingFileAtTheLineAndAMissingOneAsATabItsFirstSaveCreates() throws Exception {
        Path file = file("lines.txt", "one\ntwo\nthree\n");
        assertThat(window.edit(file, OptionalInt.of(3), Optional.empty())).isTrue();
        drain();
        assertThat(window.selected().orElseThrow().area().getCaretPosition()).as("the start of line 3").isEqualTo(8);
        Path missing = home.resolve("new.txt");
        assertThat(window.edit(missing, OptionalInt.empty(), Optional.empty())).isTrue();
        assertThat(host.contentTabs()).hasSize(2);
        assertThat(missing).doesNotExist();
    }

    @Test void editLeavesAFolderAndAFileInAMissingFolderToTheDesktop() {
        assertThat(window.edit(home, OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(window.edit(home.resolve("nowhere").resolve("x.txt"), OptionalInt.of(1), Optional.empty())).isFalse();
        assertThat(host.contentTabs()).isEmpty();
    }

    @Test void editOpensInTheTerminalWindowItWasGivenAndOtherwiseInTheLastMainWindow() throws Exception {
        UUID terminal = host.addTerminalWindow();
        WindowHandle handle = context.terminals().window(terminal).orElseThrow();
        assertThat(window.edit(file("a.txt", "a\n"), OptionalInt.empty(), Optional.of(handle))).isTrue();
        assertThat(host.contentOwner(host.contentTabs().getFirst())).isEqualTo(handle);
        assertThat(window.edit(file("b.txt", "b\n"), OptionalInt.empty(), Optional.of(new WindowOwner() { }))).as("not a terminal window").isTrue();
        assertThat(host.contentTabs()).hasSize(2);
    }
}
'''
p.write_text(t, encoding='utf-8')
PY
```

Edit `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java')
t = p.read_text(encoding='utf-8')
for old, new in [
    ("import java.util.Set;\n", "import java.util.List;\nimport java.util.Set;\n"),
    ('assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "tab_size", "soft_wrap", "vim", "max_file_mib", "recent_files_days");',
     'assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "tab_size", "soft_wrap", "vim", "max_file_mib", "recent_files_days",\n'
     '                "default_editor", "custom_command", "custom_in_terminal", "keep_environment_editor");'),
    ("""    /**
     * Plan 3 Task 8: the generated Settings page shows a section label above a settings card wherever the section
     * changes, so the declarations group the page as Font, Editing and Files, with the two on/off settings as switches.
     */
""", """    /**
     * Plan 3 Task 8 and the default editor plan: the declarations group the settings as Font, Editing, Files and Default
     * editor, as the Editor's own page shows them, with the on/off settings as switches.
     */
"""),
    ("@Test void declaresTheSettingsPageAsFontEditingAndFilesGroups() {", "@Test void declaresTheSettingsAsFontEditingFilesAndDefaultEditorGroups() {"),
    ('"Font/font_family", "Font/font_size", "Editing/tab_size", "Editing/soft_wrap", "Editing/vim", "Files/max_file_mib", "Files/recent_files_days");',
     '"Font/font_family", "Font/font_size", "Editing/tab_size", "Editing/soft_wrap", "Editing/vim", "Files/max_file_mib", "Files/recent_files_days",\n'
     '                "Default editor/default_editor", "Default editor/custom_command", "Default editor/custom_in_terminal",\n'
     '                "Default editor/keep_environment_editor");'),
    ('.extracting(dev.jasper.sdk.settings.SettingSpec::key).containsExactly("soft_wrap", "vim");',
     '.extracting(dev.jasper.sdk.settings.SettingSpec::key).containsExactly("soft_wrap", "vim", "custom_in_terminal", "keep_environment_editor");'),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
t = t.rstrip()
assert t.endswith('}')
t = t[:-1] + r'''
    @Test void theDefaultEditorIsTheJasperEditorAndOnlyKnownChoicesAreAccepted() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var declared = start(host, Set.of());
            assertThat(declared.defaultEditor()).isEqualTo("jasper");
            assertThat(declared.customCommand()).isEmpty();
            assertThat(declared.customInTerminal()).isTrue();
            assertThat(declared.keepEnvironmentEditor()).isTrue();
            for (String value : List.of("system", "custom", "nvim", "code", "notepadpp")) {
                host.setSetting(ID, "default_editor", value);
                assertThat(declared.defaultEditor()).isEqualTo(value);
            }
            host.setSetting(ID, "default_editor", "emacsclient");
            assertThat(declared.defaultEditor()).isEqualTo("jasper");
            assertThat(host.settingsReports(ID)).containsExactly("default_editor: Use jasper, system, custom or an editor id such as nvim or code.");
            host.setSetting(ID, "default_editor", "custom");
            host.setSetting(ID, "custom_command", "subl -w");
            host.setSetting(ID, "custom_in_terminal", false);
            host.setSetting(ID, "keep_environment_editor", false);
            assertThat(declared.customCommand()).isEqualTo("subl -w");
            assertThat(declared.customInTerminal()).isFalse();
            assertThat(declared.keepEnvironmentEditor()).isFalse();
        }
    }
}
'''
p.write_text(t, encoding='utf-8')
PY
```

Edit `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java` (the Editor now starts with its four
capabilities) and add the end-to-end tests:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java')
t = p.read_text(encoding='utf-8')
for old, new in [
    ("import dev.jasper.editor.ui.EditorWindow;\n",
     "import dev.jasper.editor.ui.EditorWindow;\nimport dev.jasper.editor.external.Detection;\nimport dev.jasper.editor.external.EditorCommands;\n"),
    ("import java.util.Optional;\n", "import java.util.HashSet;\nimport java.util.Optional;\nimport java.util.OptionalInt;\n"),
    ('new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of("palette.contribute"));',
     'new PluginInfo("dev.jasper.editor", "Editor", "0.1.0",\n'
     '        Set.of("palette.contribute", "terminal.environment", "editor.provide", "terminal.open"));'),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
t = t.rstrip()
assert t.endswith('}')
t = t[:-1] + r'''
    private final Set<Path> programs = new HashSet<>();
    private final List<List<String>> launched = new ArrayList<>();

    /** The Editor over a machine whose programs are {@link #programs}; app editors and detached commands go to {@link #launched}. */
    private EditorPlugin detecting() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
                owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false),
            () -> new Detection.Probe("Mac OS X", List.of(home.resolve("bin")), home, Map.of(), programs::contains),
            log -> command -> { launched.add(command); return true; });
    }

    /** A program detection finds in {@code home/bin}. */
    private Path install(String name) throws Exception {
        Path program = Files.createDirectories(home.resolve("bin")).resolve(name).toAbsolutePath().normalize();
        programs.add(program);
        return program;
    }

    @Test void theEditorIsJaspersFileEditorAndNamesItselfInEditorAndVisual() throws Exception {
        try (var host = host()) {
            Path client = home.resolve("Jasper Beta.app").resolve("bin").resolve("jasper");
            host.setCommandClient(client);
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.editorPlugin()).contains("dev.jasper.editor");
            Path config = Files.writeString(home.resolve("config.toml"), "[window]\n");
            assertThat(host.edit(config, OptionalInt.of(1), Optional.empty())).isTrue();
            drain(host);
            assertThat(host.contentTabs()).hasSize(1);
            String value = EditorCommands.quote(client.toString(), System.getProperty("os.name", "")) + " editor --wait";
            assertThat(host.shellEnvironment(Map.of())).isEqualTo(Map.of("EDITOR", value, "VISUAL", value));
            assertThat(host.failures()).isEmpty();
            host.stopAll();
            assertThat(host.editorPlugin()).as("stopped").isEmpty();
            assertThat(host.shellEnvironment(Map.of())).isEmpty();
        }
    }

    @Test void anotherPluginsOpenInEditorOpensTheFileHere() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), detecting());
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of(), Set.of(), consumer::set);
            consumer.get().platform().openInEditor(Files.writeString(home.resolve("snippets.toml"), "x = 1\n"));
            drain(host);
            assertThat(host.contentTabs()).hasSize(1);
            assertThat(host.openedInEditor()).as("the desktop was not needed").isEmpty();
        }
    }

    /** Review Focus 2, end to end: Jasper started from a terminal with EDITOR=vim keeps it until keep_environment_editor is off. */
    @Test void anEditorInJaspersOwnEnvironmentStaysUntilTheSettingIsOff() {
        try (var host = host()) {
            host.setCommandClient(home.resolve("jasper"));
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.shellEnvironment(Map.of("EDITOR", "vim"))).as("git keeps vim").isEmpty();
            assertThat(host.shellEnvironment(Map.of("VISUAL", "nvim"))).isEmpty();
            host.setSetting("dev.jasper.editor", "keep_environment_editor", false);
            assertThat(host.shellEnvironment(Map.of("EDITOR", "vim"))).containsOnlyKeys("EDITOR", "VISUAL");
        }
    }

    @Test void anInstalledAppEditorStartsAtTheLineAndATerminalEditorGetsATab() throws Exception {
        try (var host = host()) {
            Path code = install("code"), nvim = install("nvim");
            host.setSetting("dev.jasper.editor", "default_editor", "code");
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            Path file = Files.writeString(home.resolve("notes.txt"), "a\nb\n");
            String os = System.getProperty("os.name", "");
            assertThat(host.shellEnvironment(Map.of())).containsEntry("EDITOR", EditorCommands.quote(code.toString(), os) + " --wait");
            assertThat(host.edit(file, OptionalInt.of(2), Optional.empty())).isTrue();
            assertThat(launched).containsExactly(List.of(code.toString(), "-g", file + ":2"));
            host.setSetting("dev.jasper.editor", "default_editor", "nvim");
            UUID window = host.addTerminalWindow();
            assertThat(host.edit(file, OptionalInt.of(2), Optional.of(window))).isTrue();
            assertThat(host.openRequests()).contains("tab|" + window + "|" + file.getParent() + "|run:" + nvim + " +2 " + file);
            assertThat(host.contentTabs()).as("the Jasper Editor opened nothing").isEmpty();
        }
    }

    /** Review Focus 5, end to end: the saved editor was uninstalled. */
    @Test void anUninstalledSavedEditorGivesShellsNothingAndFilesToTheDesktop() throws Exception {
        try (var host = host()) {
            host.setSetting("dev.jasper.editor", "default_editor", "zed");
            host.start(INFO, Set.of(), Set.of(), detecting());
            drain(host);
            assertThat(host.settingsReports("dev.jasper.editor")).as("a known id stays a valid value").isEmpty();
            assertThat(host.shellEnvironment(Map.of())).isEmpty();
            assertThat(host.edit(Files.writeString(home.resolve("a.txt"), "a\n"), OptionalInt.empty(), Optional.empty())).isFalse();
            assertThat(launched).isEmpty();
            assertThat(host.contentTabs()).isEmpty();
        }
    }
}
'''
p.write_text(t, encoding='utf-8')
PY
```

In `plugins/editor/src/test/java/dev/jasper/editor/EditorCommandTest.java`, give the Editor its capabilities:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/test/java/dev/jasper/editor/EditorCommandTest.java')
t = p.read_text(encoding='utf-8')
old = 'new PluginInfo("dev.jasper.editor", "Editor", "0.8.0", Set.of("palette.contribute"))'
new = ('new PluginInfo("dev.jasper.editor", "Editor", "0.9.0",\n'
       '            Set.of("palette.contribute", "terminal.environment", "editor.provide", "terminal.open"))')
assert t.count(old) == 1
p.write_text(t.replace(old, new), encoding='utf-8')
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test`
Expected: `compileTestJava` FAILED: `cannot find symbol` for `DefaultEditor`, `EditorWindow.edit`,
`EditorSettings.Declared.defaultEditor()` and the four-argument `EditorPlugin` constructor.

- [ ] **Step 3: Write the implementation**

`plugins/editor/src/main/java/dev/jasper/editor/external/DefaultEditor.java`:

```java
package dev.jasper.editor.external;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.LocalSpec;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.FileEditor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The default editor: what the Default editor settings choose, as the editor Jasper opens files with ({@link #edit},
 * the {@code Platform.editor} hook) and as the {@code EDITOR} and {@code VISUAL} of Jasper's shells ({@link #variables},
 * the {@code Terminals.environment} contributor). UI thread, except detection's search, which {@link #detect} runs on
 * the background executor before publishing its result on the UI thread.
 *
 * <p>Known limit: until the first detection has finished, milliseconds after the plugin starts, an installed editor
 * counts as not found, so a shell started before then gets no {@code EDITOR} or {@code VISUAL} for it.
 */
public final class DefaultEditor {
    /**
     * What the four Default editor settings hold now.
     *
     * @param editor {@code default_editor}: jasper, system, custom or a known editor's id
     * @param customCommand {@code custom_command}
     * @param customInTerminal {@code custom_in_terminal}
     * @param keepEnvironment {@code keep_environment_editor}
     */
    public record Preference(String editor, String customCommand, boolean customInTerminal, boolean keepEnvironment) {
        /** Rejects nulls. */
        public Preference {
            Objects.requireNonNull(editor, "editor");
            Objects.requireNonNull(customCommand, "customCommand");
        }
    }

    /** Starts a program on its own: no input, its output discarded, never waited for. */
    @FunctionalInterface
    public interface Launcher {
        /**
         * @param command the program and its arguments
         * @return whether it started
         */
        boolean start(List<String> command);

        /**
         * The production launcher, over {@link ProcessBuilder}. A program that cannot start is logged, by its name
         * only, and reported as not started.
         *
         * @param log where a failure goes
         * @return the launcher
         */
        static Launcher detached(System.Logger log) {
            return command -> {
                Process process;
                try {
                    process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                } catch (IOException | RuntimeException failure) {
                    log.log(System.Logger.Level.WARNING, "Could not start " + (command.isEmpty() ? "an empty command" : command.getFirst()), failure);
                    return false;
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // It started; its input was never going to be used.
                }
                return true;
            };
        }
    }

    private final Supplier<Preference> preference;
    private final Supplier<Detection.Probe> probe;
    private final Executor background, ui;
    private final Terminals terminals;
    private final FileEditor jasper;
    private final Launcher launcher;
    private final Optional<Path> client;
    private final String osName;
    private final System.Logger log;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile List<Detected> detected = List.of();

    /**
     * @param preference the settings, read at each use
     * @param probe what detection looks at, read at each detection
     * @param background where detection runs
     * @param ui the UI thread, where detection's result is published
     * @param terminals opens the tabs terminal editors run in
     * @param jasper the Jasper Editor's own file editor
     * @param launcher starts app editors and custom commands that run on their own
     * @param client the {@code jasper} command, for the Jasper Editor's {@code EDITOR}
     * @param osName the {@code os.name} property: quoting, and how a custom command runs
     * @param log where a failed detection goes
     */
    public DefaultEditor(Supplier<Preference> preference, Supplier<Detection.Probe> probe, Executor background, Executor ui,
                         Terminals terminals, FileEditor jasper, Launcher launcher, Optional<Path> client, String osName, System.Logger log) {
        this.preference = Objects.requireNonNull(preference, "preference");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.terminals = Objects.requireNonNull(terminals, "terminals");
        this.jasper = Objects.requireNonNull(jasper, "jasper");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.client = Objects.requireNonNull(client, "client");
        this.osName = Objects.requireNonNull(osName, "osName");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Looks for installed editors again on the background executor; {@link #onDetected} listeners hear the result on the UI thread. */
    public void detect() {
        background.execute(() -> {
            List<Detected> found;
            try {
                found = Detection.detect(probe.get());
            } catch (RuntimeException failure) {
                log.log(System.Logger.Level.WARNING, "Could not look for installed editors", failure);
                return;
            }
            ui.execute(() -> {
                detected = found;
                for (Runnable listener : listeners) listener.run();
            });
        });
    }

    /** @return what the last detection found, in the known editors' order; empty before the first */
    public List<Detected> detected() { return detected; }

    /**
     * @param listener runs on the UI thread after each detection
     * @return removes it
     */
    public Subscription onDetected(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        Runnable entry = listener::run;
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    /**
     * @return what the settings choose now; a saved id detection did not find, and a custom choice without a command,
     *     are the system's default app
     */
    public Choice choice() {
        Preference now = preference.get();
        return switch (now.editor()) {
            case "jasper" -> new Choice.Jasper(client);
            case "system" -> new Choice.System();
            case "custom" -> now.customCommand().isBlank() ? new Choice.System() : new Choice.Custom(now.customCommand(), now.customInTerminal());
            default -> detected.stream().filter(found -> found.editor().id().equals(now.editor())).findFirst()
                .<Choice>map(Choice.Installed::new).orElseGet(Choice.System::new);
        };
    }

    /**
     * The {@code Terminals.environment} contributor.
     *
     * @param jasperEnvironment Jasper's own environment
     * @return {@code EDITOR} and {@code VISUAL} for the choice; nothing when Jasper's environment keeps its own or the
     *     choice names none
     */
    public Map<String, String> variables(Map<String, String> jasperEnvironment) {
        if (EditorCommands.keepsEnvironment(jasperEnvironment, preference.get().keepEnvironment())) return Map.of();
        return EditorCommands.variable(choice(), osName).map(value -> Map.of("EDITOR", value, "VISUAL", value)).orElse(Map.of());
    }

    /**
     * The {@code Platform.editor} file editor: opens a file in the chosen editor. A terminal editor, and a custom command
     * that runs in a terminal, get a new tab in {@code window} when it is a terminal window, else in the active one.
     *
     * @param file the file
     * @param line 1-based, when known
     * @param window the window Jasper opened the file from
     * @return whether it opened; false hands the file to the desktop
     */
    public boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window) {
        Path target = file.toAbsolutePath().normalize();
        return switch (choice()) {
            case Choice.Jasper _ -> jasper.edit(target, line, window);
            case Choice.Installed installed -> installed.editor().editor().kind() == KnownEditor.Kind.TERMINAL
                ? inTab(EditorCommands.openAt(installed.editor(), target, line), target, window)
                : launcher.start(EditorCommands.openAt(installed.editor(), target, line));
            case Choice.Custom custom -> custom.inTerminal()
                ? inTab(EditorCommands.custom(custom.command(), target, osName), target, window)
                : launcher.start(EditorCommands.custom(custom.command(), target, osName));
            case Choice.System _ -> false;
        };
    }

    /** A new tab running the command in the file's folder; false when no terminal window is open. */
    private boolean inTab(List<String> command, Path file, Optional<WindowOwner> window) {
        Optional<WindowHandle> target = window.filter(WindowHandle.class::isInstance).map(WindowHandle.class::cast)
            .filter(WindowHandle::isOpen).or(terminals::activeWindow);
        return target.isPresent() && terminals.openTab(target.get(),
            OpenRequest.local(LocalSpec.running(command, Optional.ofNullable(file.getParent())))).isPresent();
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, add the Jasper Editor's own file editor
before `closed`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java')
t = p.read_text(encoding='utf-8')
old = "    /** Completes once the session's tab has closed: when the user closes it, its window closes or the Editor stops. */\n"
new = r'''    /**
     * Opens a local file for the default editor ({@code FileEditor.edit}): an existing file with the caret on
     * {@code line}, a missing one whose folder exists as a tab its first save creates. The tab opens in {@code window}
     * when that is a terminal window, else in the last main window.
     *
     * @return false for a folder or a file whose folder does not exist, which the desktop then opens or reports
     */
    public boolean edit(Path file, java.util.OptionalInt line, Optional<WindowOwner> window) {
        Path target = file.toAbsolutePath().normalize();
        WindowOwner owner = window.filter(WindowHandle.class::isInstance).orElse(null);
        if (java.nio.file.Files.isRegularFile(target)) {
            openAt(target, line.orElse(0), 0, owner);
            return true;
        }
        Path folder = target.getParent();
        if (java.nio.file.Files.exists(target) || folder == null || !java.nio.file.Files.isDirectory(folder)) return false;
        openNew(target, owner);
        return true;
    }

''' + old
assert t.count(old) == 1
p.write_text(t.replace(old, new), encoding='utf-8')
PY
```

Declare the four settings in `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java')
t = p.read_text(encoding='utf-8')
edits = [
    ("import dev.jasper.sdk.settings.EditorHint;\n", "import dev.jasper.editor.external.KnownEditors;\nimport dev.jasper.sdk.settings.EditorHint;\n"),
    ("        private final Setting<Boolean> softWrap, vim;\n", r'''        private final Setting<Boolean> softWrap, vim;
        final Setting<String> defaultEditor, customCommand;
        final Setting<Boolean> customInTerminal, keepEnvironmentEditor;

        /** {@code default_editor}'s default: the Jasper Editor. */
        static final String JASPER = "jasper";
        /** The {@code default_editor} values besides a known editor's id. */
        static final Set<String> CHOICES = Set.of(JASPER, "system", "custom");
        static final String DEFAULT_EDITOR_LABEL = "Default editor";
        static final String DEFAULT_EDITOR_DESCRIPTION = "Opens the files Jasper opens, such as config.toml, and is EDITOR and VISUAL "
            + "in Jasper's shells, so git commit uses it.";
        static final String COMMAND_LABEL = "Custom command";
        static final String COMMAND_DESCRIPTION = "For Custom command: the command line, run by the shell; the file's path is added at the end.";
'''),
    ('                .section("Files").keywords("editor", "recent", "history"));\n', r'''                .section("Files").keywords("editor", "recent", "history"));
            defaultEditor = settings.declare(SettingSpec.of("default_editor",
                    SettingKind.text(value -> CHOICES.contains(value) || KnownEditors.byId(value).isPresent(),
                        "Use jasper, system, custom or an editor id such as nvim or code."), JASPER)
                .label(DEFAULT_EDITOR_LABEL).description(DEFAULT_EDITOR_DESCRIPTION).section("Default editor")
                .keywords("editor", "default", "EDITOR", "VISUAL", "git"));
            customCommand = settings.declare(SettingSpec.of("custom_command", SettingKind.text(), "")
                .label(COMMAND_LABEL).description(COMMAND_DESCRIPTION).section("Default editor").keywords("editor", "custom", "command"));
            customInTerminal = settings.declare(SettingSpec.of("custom_in_terminal", SettingKind.bool(), true)
                .label("Run in a terminal tab").description("For Custom command: run it in a new terminal tab; off starts it on its own.")
                .section("Default editor").keywords("editor", "custom", "terminal"));
            keepEnvironmentEditor = settings.declare(SettingSpec.of("keep_environment_editor", SettingKind.bool(), true)
                .label("Keep EDITOR and VISUAL from Jasper's environment")
                .description("When Jasper was started with EDITOR or VISUAL set, its shells keep both and the default editor sets neither.")
                .section("Default editor").keywords("editor", "EDITOR", "VISUAL", "environment"));
'''),
    ("        public int recentFilesDays() { return recentFilesDays.get(); }\n", r'''        public int recentFilesDays() { return recentFilesDays.get(); }

        /** @return {@code default_editor}: jasper, system, custom or a known editor's id */
        public String defaultEditor() { return defaultEditor.get(); }

        /** @return {@code custom_command}, as written */
        public String customCommand() { return customCommand.get(); }

        /** @return {@code custom_in_terminal}: whether the custom command runs in a terminal tab */
        public boolean customInTerminal() { return customInTerminal.get(); }

        /** @return {@code keep_environment_editor}: whether an EDITOR or VISUAL in Jasper's environment keeps both */
        public boolean keepEnvironmentEditor() { return keepEnvironmentEditor.get(); }
'''),
]
for old, new in edits:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
p.write_text(t, encoding='utf-8')
PY
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/package-info.java`, `ui` now reaches `external`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/main/java/dev/jasper/editor/ui/package-info.java')
t = p.read_text(encoding='utf-8')
old = "{@code vim}, {@code vim.ui}, the SDK, RSyntaxTextArea."
assert t.count(old) == 1
p.write_text(t.replace(old, "{@code vim}, {@code vim.ui}, {@code external}, the SDK, RSyntaxTextArea."), encoding='utf-8')
PY
```

Wire it in `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java')
t = p.read_text(encoding='utf-8')
edits = [
    ("import dev.jasper.editor.command.EditorCommand;\n",
     "import dev.jasper.editor.command.EditorCommand;\nimport dev.jasper.editor.external.DefaultEditor;\nimport dev.jasper.editor.external.Detection;\n"),
    ("import java.util.List;\n", "import java.util.List;\nimport java.util.Map;\n"),
    ("import java.util.function.Predicate;\n", "import java.util.function.Predicate;\nimport java.util.function.Supplier;\n"),
    ("    private final BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments;\n",
     "    private final BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments;\n"
     "    private final Supplier<Detection.Probe> probes;\n"
     "    private final Function<System.Logger, DefaultEditor.Launcher> launchers;\n"),
    (r'''    /** The production plugin: Swing's event queue, modal dialogs and running timers. */
    public EditorPlugin() {
        this(SwingUtilities::invokeLater, (context, settings) -> EditorEnvironment.production(context, SwingUtilities::invokeLater, settings));
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments) {
        this.ui = ui;
        this.environments = environments;
    }
''', r'''    /** The production plugin: Swing's event queue, modal dialogs, running timers, this machine's editors and real programs. */
    public EditorPlugin() {
        this(SwingUtilities::invokeLater, (context, settings) -> EditorEnvironment.production(context, SwingUtilities::invokeLater, settings),
            Detection.Probe::system, DefaultEditor.Launcher::detached);
    }

    /** For tests: finds no installed editor and starts no program. */
    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments) {
        this(ui, environments, () -> new Detection.Probe(System.getProperty("os.name", ""), List.of(), Path.of(System.getProperty("user.home")),
            Map.of(), file -> false), log -> command -> false);
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments,
                 Supplier<Detection.Probe> probes, Function<System.Logger, DefaultEditor.Launcher> launchers) {
        this.ui = ui;
        this.environments = environments;
        this.probes = probes;
        this.launchers = launchers;
    }
'''),
    ('        context.commandLine().handle("editor", new EditorCommand(this::window)::run);\n', r'''        context.commandLine().handle("editor", new EditorCommand(this::window)::run);
        DefaultEditor defaults = new DefaultEditor(() -> new DefaultEditor.Preference(settings.defaultEditor(), settings.customCommand(),
                settings.customInTerminal(), settings.keepEnvironmentEditor()),
            probes, context.background(), ui, context.terminals(), (file, line, owner) -> window().edit(file, line, owner),
            launchers.apply(context.log()), context.commandLine().client(), System.getProperty("os.name", ""), context.log());
        defaults.detect();
        context.terminals().environment(defaults::variables);
        context.platform().editor(defaults::edit);
'''),
]
for old, new in edits:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
p.write_text(t, encoding='utf-8')
PY
```

Raise the manifest and seed the template:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/main/resources/plugin.toml')
t = p.read_text(encoding='utf-8')
for old, new in [
    ('version = "0.8.0"\n', 'version = "0.9.0"\n'),
    ('sdk = ">=0.17.0, <0.18"\n', 'sdk = ">=0.17.1, <0.18"\n'),
    ('capabilities = ["palette.contribute"]\n', 'capabilities = ["palette.contribute", "terminal.environment", "editor.provide", "terminal.open"]\n'),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
p.write_text(t, encoding='utf-8')
s = pathlib.Path('plugins/editor/src/main/resources/settings.toml')
t = s.read_text(encoding='utf-8')
old = '# recent_files_days = 30\n'
assert t.count(old) == 1 and t.endswith(old)
s.write_text(t + '''
# The default editor: jasper (the Jasper Editor), system (your system's default app; Jasper's shells get no EDITOR
# or VISUAL), custom (custom_command below) or an installed editor's id: nvim, vim, vi, nano, micro, helix, emacs,
# code, cursor, zed, sublime, bbedit, textmate, idea, notepadpp or notepad. Files Jasper opens, such as config.toml,
# open in it, and Jasper's shells get EDITOR and VISUAL for it.
# default_editor = "jasper"

# For custom: the command line; the file's path is added at the end.
# custom_command = ""

# For custom: run the command in a terminal tab; false starts it on its own.
# custom_in_terminal = true

# When Jasper itself was started with EDITOR or VISUAL set, leave both alone in its shells.
# keep_environment_editor = true
''', encoding='utf-8')
PY
```

In `jasper-app/src/test/java/dev/jasper/app/plugins/StagedEditorHost.java`, the staged Editor starts with its manifest's
capabilities:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('jasper-app/src/test/java/dev/jasper/app/plugins/StagedEditorHost.java')
t = p.read_text(encoding='utf-8')
old = 'new PluginInfo("dev.jasper.editor", "Editor", "0.8.0", Set.of(Capabilities.PALETTE_CONTRIBUTE));'
new = ('new PluginInfo("dev.jasper.editor", "Editor", "0.9.0", Set.of(Capabilities.PALETTE_CONTRIBUTE,\n'
       '        Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE, Capabilities.TERMINAL_OPEN));')
assert t.count(old) == 1
p.write_text(t.replace(old, new), encoding='utf-8')
PY
```

Append the ledger rows:

```bash
python3 - <<'PY'
import pathlib
csv = pathlib.Path('docs/design/theme-engine/migration.csv')
text = csv.read_text(encoding='utf-8')
main, test = 'plugins/editor/src/main/java/dev/jasper/editor/external/', 'plugins/editor/src/test/java/dev/jasper/editor/external/'
rows = [
    main + 'DefaultEditor.java,dev.jasper.editor,DefaultEditor,nonvisual-helper,none,none,' + test + 'DefaultEditorTest.java;plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java,New in the default editor plan Task 8; the FileEditor and shell variables with no Swing or theme dependency; matched only by the dev.jasper.sdk.ui.FileEditor import',
    test + 'DefaultEditorTest.java,dev.jasper.editor,DefaultEditorTest,nonvisual-helper,none,none,' + test + 'DefaultEditorTest.java,Test/fixture ownership; New in the default editor plan Task 8; no Swing or theme dependency; matched only by collection construction',
]
assert text.endswith('\n')
for row in rows:
    assert row.count(',') == 7, row
    assert '\n' + row.split(',', 1)[0] + ',' not in text, row
csv.write_text(text + ''.join(row + '\n' for row in rows), encoding='utf-8')
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test verifyPluginArchitecture :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS. The bundled-plugin tests in `jasper-app` load the 0.9.0 manifest (its range and both capabilities
must be known to the app), and the `StagedEditorHost` tests start the production Editor, whose detection reads this
machine's `PATH` on the plugin's executor and starts nothing. `verifyPluginArchitecture` passes: `DefaultEditor`
reaches only `java.*` and `dev.jasper.sdk.*`.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/external/DefaultEditor.java \
        plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java \
        plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java \
        plugins/editor/src/main/java/dev/jasper/editor/ui/package-info.java \
        plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java \
        plugins/editor/src/main/resources/plugin.toml \
        plugins/editor/src/main/resources/settings.toml \
        plugins/editor/src/test/java/dev/jasper/editor/external/DefaultEditorTest.java \
        plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java \
        plugins/editor/src/test/java/dev/jasper/editor/EditorCommandTest.java \
        plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java \
        plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java \
        jasper-app/src/test/java/dev/jasper/app/plugins/StagedEditorHost.java \
        docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Editor 0.9.0: the default editor opens Jasper's files and fills EDITOR

default_editor (jasper, system, custom or a known id), custom_command,
custom_in_terminal and keep_environment_editor. The Editor registers the
file editor (a Jasper tab, a terminal editor's program tab, an app editor
started on its own, a custom command, or false for the desktop) and the
EDITOR/VISUAL contributor, and looks for installed editors in the background
at start. SDK range >=0.17.1, with terminal.environment, editor.provide and
terminal.open.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: The Editor's custom settings page

The first four steps keep the Font row unchanged on the custom page. `SettingsPanelHost.row` gives a setting whose
spec carries `EditorHint.Font` the generated page's font picker, and one carrying `EditorHint.File` the generated
page's path with Browse…, in the app and in the testkit. The generated page and the host rows then follow one
hint-to-editor choice. Neither page honours `EditorHint.Directory` (the Vault's key folder), so it stays a text field
on both.

**Files:**
- Modify (host rows):
  - `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginSettingsPage,KitSettingRows,PluginPanelPage}.java`
  - `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java`
  - `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{KitSettingRows,FakeSettingsPanelHost,FakePluginHost}.java`
- Test (host rows): `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java`,
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettingsPanel.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`,
  `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`, `docs/design/theme-engine/migration.csv`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsPanelTest.java` (new),
  `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`
- No new jasper-app or testkit file: the shared choice is the nested `PluginSettingsPage.Hint`, and every app and
  testkit file touched already has its `migration.csv` row.

**Interfaces:**
- Consumes (host rows):
  - `EditorHint.Font(monospaced)` and `EditorHint.File`;
  - the generated page's editors `Editors.font(boolean)` and `Editors.path(String)`, and `Fonts.families` behind them;
  - `PageContext.chooseFile`;
  - the testkit's `FakePluginHost.queueFileSelection` and `takeFileSelection`.
- Produces (host rows):
  - `PluginSettingsPage.Hint { FONT, MONOSPACED_FONT, FILE, NONE }`, with `static Hint of(SettingSpec<?>)`,
    `boolean font()` and `FILE_CHOOSER_TITLE` (`"Choose a file"`). This is the one hint-to-editor choice that
    `PluginSettingsPage.editor` (the generated page) and the app's `KitSettingRows.row` (host rows) both follow.
    `dev.jasper.sdk` types stay inside `dev.jasper.app.plugins`.
  - `Editors.fontFamilies(boolean monospaced)` and `Editors.NO_FONT_MATCHES`, public in `settingsui`, which
    `plugins` may already depend on. `FontField` reads the same constant.
  - The app's `KitSettingRows(host, specs, unset, BiFunction<String, Optional<Path>, Optional<Path>> chooseFile)`:
    - A Font-hinted text setting's row control is a `SEARCHABLE` free-entry `Nodes.Choice` over the installed (or
      monospaced) families. It is equal, after `KitValues.toModule`, to the generated page's `FontField` node.
    - A File-hinted setting's `PATH` field gets the trailing action `settings:<key>:browse`. That action asks
      `chooseFile("Choose a file", current)` and stages the chosen path; cancelling stages nothing.
    - `PluginPanelPage` passes its `PageContext.chooseFile`.
  - The testkit's `KitSettingRows` gives the same rows (families from `GraphicsEnvironment`, read once).
    `FakeSettingsPanelHost(settings, Supplier<Optional<Path>> chooseFile)` is fed from
    `FakePluginHost.takeFileSelection`, so `host.queueFileSelection(Optional.of(path))` answers a row's Browse; with
    nothing queued, Browse cancels.
- Consumes (page): `DefaultEditor.detect()`, `detected()`, `onDetected(Runnable)` and `EditorSettings.Declared`'s
  handles (Task 8); `KnownEditors.byId` (Task 6); SDK `SettingsPanel`, `SettingsPanelHost` (`row`, `pending`,
  `stage`, `changed`), `Nodes`, `Option`, `UiEvent`; testkit `FakePluginHost.settingsPanel`, `settingsPanelView`,
  `pendingSettings`, `settingsPanelChanges`, `applySettingsPanel`, `closeSettingsPanel`, `highlightSettingsPanel`,
  `savedSettings`, `FakeKitView.emit`.
- Produces (page):
  - `EditorSettingsPanel(UiKit kit, EditorSettings.Declared settings, DefaultEditor defaults, Map<String, String> environment)`,
    implementing `SettingsPanel`;
  - package-private `environmentText(Map)`, the node ids `PAGE`, `FONT`, `EDITING`, `FILES`, `DEFAULT`, `CHOICE`,
    `COMMAND` and `ENVIRONMENT`, and the texts `NOTE` and `NO_COMMAND`.

  `EditorPlugin.start` gives the page with `context.settings().page(...)`.
- Page notes:
  - The custom command field is the page's own `Nodes.Field`, staged with `host.stage`. The host takes a host row's
    edits without telling the page, so the empty-command error could not follow typing.
  - `custom_in_terminal` and `keep_environment_editor` are host rows. `custom_in_terminal`'s row is re-marked
    `dependent`; the host keeps a row's `dependent` flag when it re-projects the row.
  - The page does not override `reset`, because Settings calls it while closing, when the host no longer answers.

- [ ] **Step 1: Write the failing host-row tests**

App side, in `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java`. The expected controls are
built by the generated page's own editors (`Editors.font(true)` and `Editors.path("Choose a file")`) over an
application setting with the same label, and compared through `KitValues.toModule`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java')
t = p.read_text(encoding='utf-8').rstrip()
assert t.endswith('}')
t = t[:-1] + r'''
    /**
     * A plugin page's standard row for a hinted setting is the control the generated page gives that setting: the font
     * picker for {@code EditorHint.font}, the path with Browse for {@code EditorHint.file}.
     */
    @Test void hintedHostRowsAreTheGeneratedPagesFontPickerAndPathWithBrowse() throws Exception {
        var view = new AtomicReference<HostedKitView>();
        var rows = new AtomicReference<SettingsPanelHost>();
        var font = new AtomicReference<Setting<String>>();
        var log = new AtomicReference<Setting<String>>();
        var asked = new ArrayList<String>();
        Path chosen = data.resolve("app.log");
        var browsing = new PageContext(null, id -> { }, Runnable::run,
            (title, initial) -> { asked.add(title + "|" + initial.map(Path::toString).orElse("-")); return Optional.of(chosen); },
            () -> changes[0]++, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            getClass().getClassLoader(), () -> plugin -> {
                font.set(plugin.settings().declare(SettingSpec.of("font", SettingKind.text(), "Menlo").label("Font")
                    .editor(dev.jasper.sdk.settings.EditorHint.font(true))));
                log.set(plugin.settings().declare(SettingSpec.of("log", SettingKind.text(), "").label("Log file")
                    .editor(dev.jasper.sdk.settings.EditorHint.file())));
                plugin.settings().page((SettingsPanel) panelHost -> {
                    rows.set(panelHost);
                    var made = (HostedKitView) plugin.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",
                        dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL,
                        List.of(panelHost.row(font.get()), panelHost.row(log.get())))), event -> { });
                    view.set(made);
                    return made;
                });
            })));
        var page = onEdtValue(() -> (PluginPanelPage) host.settingsPages(browsing).getFirst());
        onEdt(() -> {
            page.component();
            var picker = hostControl(view.get(), 0);
            var generatedFont = dev.jasper.app.settingsui.Editors.font(true).build().apply(appText("font", "Font"), browsing);
            generatedFont.show("Menlo");
            assertThat(KitValues.toModule(picker)).as("the generated page's font picker")
                .isEqualTo(generatedFont.node("settings:font", KitValues.toModule(picker.state())));
            var path = hostControl(view.get(), 1);
            var generatedPath = dev.jasper.app.settingsui.Editors.path("Choose a file").build().apply(appText("log", "Log file"), browsing);
            generatedPath.show("");
            assertThat(KitValues.toModule(path)).as("the generated page's path with Browse")
                .isEqualTo(generatedPath.node("settings:log", KitValues.toModule(path.state())));
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Edited("settings:font", "Courier New"));
            assertThat(rows.get().pending(font.get())).isEqualTo("Courier New");
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("settings:log:browse"));
            assertThat(asked).containsExactly("Choose a file|-");
            assertThat(rows.get().pending(log.get())).isEqualTo(chosen.toString());
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("settings:log:browse"));
            assertThat(asked).as("Browse starts at the current file").containsExactly("Choose a file|-", "Choose a file|" + chosen);
            assertThat(page.modified()).isTrue();
        });
        onEdt(page::dispose);
    }

    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */
    private static dev.jasper.sdk.ui.Nodes.Node hostControl(HostedKitView view, int index) {
        var row = (dev.jasper.sdk.ui.Nodes.SettingRow) ((dev.jasper.sdk.ui.Nodes.Stack) view.spec().root()).children().get(index);
        return ((dev.jasper.sdk.ui.Nodes.Stack) row.control()).children().getFirst();
    }

    /** An application text setting labelled as a declared one, to build the generated page's editor beside it. */
    private static dev.jasper.app.settings.Setting<String> appText(String key, String label) {
        return dev.jasper.app.settings.Setting.of(key, dev.jasper.app.settings.SettingKind.string(value -> true, "Not an allowed value."), "")
            .label(label).build();
    }
}
'''
p.write_text(t, encoding='utf-8')
PY
```

Testkit side, in `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java')
t = p.read_text(encoding='utf-8')
for old, new in [
    ("import dev.jasper.sdk.settings.Choice;\n", "import dev.jasper.sdk.settings.Choice;\nimport dev.jasper.sdk.settings.EditorHint;\n"),
    ("import java.util.ArrayList;\n", "import java.nio.file.Path;\nimport java.util.ArrayList;\n"),
    ("import java.util.List;\n", "import java.util.List;\nimport java.util.Optional;\n"),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
t = t.rstrip()
assert t.endswith('}')
t = t[:-1] + r'''
    /** As the host: a font hint's row is the font picker, a file hint's a path whose Browse takes the next queued file selection. */
    @Test void hintedRowsAreTheFontPickerAndAPathWithBrowse() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        var font = new AtomicReference<Setting<String>>();
        var log = new AtomicReference<Setting<String>>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            font.set(context.settings().declare(SettingSpec.of("font", SettingKind.text(), "Menlo").label("Font").editor(EditorHint.font(true))));
            log.set(context.settings().declare(SettingSpec.of("log", SettingKind.text(), "").label("Log file").editor(EditorHint.file())));
            context.settings().page(rows -> context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",
                dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL, List.of(rows.row(font.get()), rows.row(log.get())))), e -> {}));
        });
        host.settingsPanel("test.fake").orElseThrow();
        var view = host.settingsPanelView("test.fake").orElseThrow();
        var installed = List.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        assertThat(control(view, 0)).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Choice.class, picker -> {
            assertThat(picker.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.ChoiceKind.SEARCHABLE);
            assertThat(picker.selectedId()).as("free entry owns the value").isEmpty();
            assertThat(picker.search().orElseThrow().entry()).contains("Menlo");
            assertThat(picker.search().orElseThrow().noMatches()).isEqualTo("No installed font matches");
            assertThat(picker.options()).extracting(dev.jasper.sdk.ui.Option::label).allSatisfy(family -> assertThat(installed).contains(family));
        });
        view.emit(new dev.jasper.sdk.ui.UiEvent.Edited("settings:font", "Courier New"));
        assertThat(host.pendingSettings("test.fake")).containsEntry("font", "Courier New");
        assertThat(control(view, 1)).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Field.class, path -> {
            assertThat(path.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldKind.PATH);
            assertThat(path.trailingAction()).isEqualTo("settings:log:browse");
        });
        view.emit(new dev.jasper.sdk.ui.UiEvent.Action("settings:log:browse"));
        assertThat(host.pendingSettings("test.fake")).as("nothing queued: Browse cancels").doesNotContainKey("log");
        Path chosen = host.dataRoot().resolve("app.log").toAbsolutePath().normalize();
        host.queueFileSelection(Optional.of(chosen));
        view.emit(new dev.jasper.sdk.ui.UiEvent.Action("settings:log:browse"));
        assertThat(host.pendingSettings("test.fake")).containsEntry("log", chosen.toString());
        assertThat(((dev.jasper.sdk.ui.Nodes.Field) control(view, 1)).value()).isEqualTo(chosen.toString());
        assertThat(host.failures()).isEmpty();
    }); }

    /** The control in the trailing slot of the {@code index}th host row of a page that stacks them. */
    private static dev.jasper.sdk.ui.Nodes.Node control(FakeKitView view, int index) {
        var row = (dev.jasper.sdk.ui.Nodes.SettingRow) ((dev.jasper.sdk.ui.Nodes.Stack) view.spec().root()).children().get(index);
        return ((dev.jasper.sdk.ui.Nodes.Stack) row.control()).children().getFirst();
    }
}
'''
p.write_text(t, encoding='utf-8')
PY
```

- [ ] **Step 2: Run the host-row tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakePluginSettingsTest' :jasper-app:test --tests 'dev.jasper.app.plugins.PluginPanelPageTest'`
Expected: both new tests FAIL at runtime; everything compiles.
- `hintedRowsAreTheFontPickerAndAPathWithBrowse`: the font row's control is a `Nodes.Field`, not a `Nodes.Choice`.
- `hintedHostRowsAreTheGeneratedPagesFontPickerAndPathWithBrowse`: `the generated page's font picker` fails, because
  the host row is a `TEXT` field and the generated page's control is a `SEARCHABLE` choice.

The rest of both classes passes.

- [ ] **Step 3: Give host rows the generated page's hinted controls**

In `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java`, publish the family list and the prompt the font
editor uses:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java')
t = p.read_text(encoding='utf-8')
assert t.count('"No installed font matches"') == 2
t = t.replace('"No installed font matches"', 'NO_FONT_MATCHES')
old = "    /** Interface font with a readable platform-default choice, retaining arbitrary typed families. */\n"
new = r'''    /** What a font family editor says when no installed family matches the text typed. */
    public static final String NO_FONT_MATCHES = "No installed font matches";

    /**
     * The families a font family editor offers, read once; plugin pages' standard rows offer the same.
     *
     * @param monospaced only the families that draw 'i' and 'W' at the same width
     * @return the installed and bundled families
     */
    public static List<String> fontFamilies(boolean monospaced) { return Fonts.families(monospaced); }

''' + old
assert t.count(old) == 1
p.write_text(t.replace(old, new), encoding='utf-8')
PY
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsPage.java`, make the hint-to-editor choice one
value both pages follow:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsPage.java')
t = p.read_text(encoding='utf-8')
for old, new in [
    ("""        EditorHint hint = spec.editor().orElse(null);
        if (hint instanceof EditorHint.Font font) return Editors.font(font.monospaced());
        if (hint instanceof EditorHint.File) return Editors.path("Choose a file");
""", """        Hint hint = Hint.of(spec);
        if (hint.font()) return Editors.font(hint == Hint.MONOSPACED_FONT);
        if (hint == Hint.FILE) return Editors.path(Hint.FILE_CHOOSER_TITLE);
"""),
    ("        return names;\n    }\n", r'''        return names;
    }

    /**
     * The control a declared setting's editor hint asks for. The generated page ({@link #editor}) and plugin pages'
     * standard rows ({@code KitSettingRows}) both follow it, so a hinted setting gets the same control on either.
     * {@code EditorHint.Directory} has none yet: its setting's kind decides.
     */
    enum Hint {
        /** A font family over every installed family. */
        FONT,
        /** A font family over the installed monospaced families. */
        MONOSPACED_FONT,
        /** A path with Browse, which asks for an existing file. */
        FILE,
        /** No hint either page honours: the kind decides. */
        NONE;

        /** The title of the file chooser a {@link #FILE} setting's Browse opens. */
        static final String FILE_CHOOSER_TITLE = "Choose a file";

        static Hint of(dev.jasper.sdk.settings.SettingSpec<?> spec) {
            return switch (spec.editor().orElse(null)) {
                case EditorHint.Font font -> font.monospaced() ? MONOSPACED_FONT : FONT;
                case EditorHint.File _ -> FILE;
                case null, default -> NONE;
            };
        }

        boolean font() { return this == FONT || this == MONOSPACED_FONT; }
    }
'''),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
p.write_text(t, encoding='utf-8')
PY
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java`, build the hinted controls and answer
Browse. `PluginPanelPage` passes its dialog's file chooser through a method reference, because a lambda in a field
initializer may not read the blank final `context`, which the constructor assigns:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java')
t = p.read_text(encoding='utf-8')
for old, new in [
    ("    KitSettingRows(SettingsPanelHost host, Function<Setting<?>, SettingSpec<?>> specs, java.util.function.Consumer<Setting<?>> unset) { this.host=host;this.specs=specs;this.unset=unset; }\n",
     r'''    /** A path's Browse action is its row id followed by this. */
    static final String BROWSE=":browse";
    private final java.util.function.BiFunction<String,Optional<java.nio.file.Path>,Optional<java.nio.file.Path>> chooseFile;
    /** @param chooseFile asks for an existing file, as the generated page's Browse does: title and initial path in, choice out */
    KitSettingRows(SettingsPanelHost host, Function<Setting<?>, SettingSpec<?>> specs, java.util.function.Consumer<Setting<?>> unset,
                   java.util.function.BiFunction<String,Optional<java.nio.file.Path>,Optional<java.nio.file.Path>> chooseFile) {
        this.host=host;this.specs=specs;this.unset=unset;this.chooseFile=chooseFile;
    }
'''),
    ("            control=new Nodes.Choice(id,state,Nodes.ChoiceKind.DROPDOWN,options,selected,spec.label(),id);\n        }else {\n",
     r'''            control=new Nodes.Choice(id,state,Nodes.ChoiceKind.DROPDOWN,options,selected,spec.label(),id);
        }else if(PluginSettingsPage.Hint.of(spec).font() && spec.kind() instanceof SettingKind.Text){
            // As the generated pages: free text over the installed families, which filter as you type.
            control=font(id,state,spec,edits.getOrDefault(id,value.toString()));
        }else {
'''),
    ("spec.editor().orElse(null) instanceof EditorHint.File?Nodes.FieldKind.PATH:Nodes.FieldKind.TEXT;",
     "PluginSettingsPage.Hint.of(spec)==PluginSettingsPage.Hint.FILE?Nodes.FieldKind.PATH:Nodes.FieldKind.TEXT;"),
    ('control=new Nodes.Field(id,state,kind,edits.getOrDefault(id,text),numeric.isPresent()?spec.unit():"",spec.label(),"",numeric,',
     '// As the generated pages: a path ends with Browse.\n'
     '            control=new Nodes.Field(id,state,kind,edits.getOrDefault(id,text),numeric.isPresent()?spec.unit():"",spec.label(),kind==Nodes.FieldKind.PATH?id+BROWSE:"",numeric,'),
    ("    /** The bounds of a numeric setting; decimals show two places and step by a tenth, as the generated pages do. */\n",
     r'''    /** As the generated pages' font editor: free text over the installed families, which filter as you type. */
    private static Nodes.Choice font(String id,ControlState state,SettingSpec<?> spec,String text){
        List<String> families=dev.jasper.app.settingsui.Editors.fontFamilies(PluginSettingsPage.Hint.of(spec)==PluginSettingsPage.Hint.MONOSPACED_FONT);
        var options=new ArrayList<Option>();
        for(int i=0;i<families.size();i++)options.add(new Option("family-"+i,families.get(i),Optional.empty()));
        return new Nodes.Choice(id,state,Nodes.ChoiceKind.SEARCHABLE,options,"",spec.label(),"","",
            Optional.of(new Nodes.ChoiceSearch(0,dev.jasper.app.settingsui.Editors.NO_FONT_MATCHES,Optional.of(text))));
    }
    /** Where a path's Browse starts: the current path, when the text is one. */
    private static Optional<java.nio.file.Path> initial(String current){
        if(current.isBlank())return Optional.empty();
        try{return Optional.of(java.nio.file.Path.of(current));}catch(java.nio.file.InvalidPathException notAPath){return Optional.empty();}
    }
    /** The bounds of a numeric setting; decimals show two places and step by a tenth, as the generated pages do. */
'''),
    ("""        boolean reset=event instanceof UiEvent.Action && event.id().endsWith(":reset");
        String id=reset?event.id().substring(0,event.id().length()-6):event.id();
""", """        boolean reset=event instanceof UiEvent.Action && event.id().endsWith(":reset");
        boolean browse=event instanceof UiEvent.Action && event.id().endsWith(BROWSE);
        String id=reset?event.id().substring(0,event.id().length()-6):browse?event.id().substring(0,event.id().length()-BROWSE.length()):event.id();
"""),
    ("            if(reset){unset.accept(setting);host.changed();return true;}\n", r'''            if(reset){unset.accept(setting);host.changed();return true;}
            else if(browse){
                if(PluginSettingsPage.Hint.of(spec)!=PluginSettingsPage.Hint.FILE)return false;
                // As the generated pages: Browse asks for an existing file, starting at the current one; cancelling changes nothing.
                var chosen=chooseFile.apply(PluginSettingsPage.Hint.FILE_CHOOSER_TITLE,initial(String.valueOf(host.pending(setting))));
                if(chosen.isEmpty())return true;
                value=chosen.get().toString();
            }
'''),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
p.write_text(t, encoding='utf-8')
q = pathlib.Path('jasper-app/src/main/java/dev/jasper/app/plugins/PluginPanelPage.java')
t = q.read_text(encoding='utf-8')
old = "    private final KitSettingRows kitRows = new KitSettingRows(rowHost, this::settingSpec, rowHost::unset);\n"
new = ("    private final KitSettingRows kitRows = new KitSettingRows(rowHost, this::settingSpec, rowHost::unset, this::chooseFile);\n"
       "    /** The dialog's file chooser, for a host row's Browse. */\n"
       "    private Optional<java.nio.file.Path> chooseFile(String title, Optional<java.nio.file.Path> initial) { return context.chooseFile().apply(title, initial); }\n")
assert t.count(old) == 1
q.write_text(t.replace(old, new), encoding='utf-8')
PY
```

The testkit mirrors the host: `KitSettingRows` builds the same two controls, `FakeSettingsPanelHost` answers Browse
from the host's file-selection queue, and `FakePluginHost` hands it that queue:

```bash
python3 - <<'PY'
import pathlib
base = pathlib.Path('jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing')

def edit(name, pairs):
    p = base / name
    t = p.read_text(encoding='utf-8')
    for old, new in pairs:
        assert t.count(old) == 1, (name, old)
        t = t.replace(old, new)
    p.write_text(t, encoding='utf-8')

edit('KitSettingRows.java', [
    ("            input = new Nodes.Choice(id, state, Nodes.ChoiceKind.DROPDOWN, options, selected, spec.label(), id);\n        } else {\n",
     r'''            input = new Nodes.Choice(id, state, Nodes.ChoiceKind.DROPDOWN, options, selected, spec.label(), id);
        } else if (spec.kind() instanceof SettingKind.Text && spec.editor().orElse(null) instanceof EditorHint.Font font) {
            // As the host: free text over the installed families, which filter as you type.
            List<String> families = font.monospaced() ? InstalledFonts.MONOSPACED : InstalledFonts.ALL;
            var options = new ArrayList<Option>();
            for (int i = 0; i < families.size(); i++) options.add(new Option("family-" + i, families.get(i), Optional.empty()));
            input = new Nodes.Choice(id, state, Nodes.ChoiceKind.SEARCHABLE, options, "", spec.label(), "", "",
                Optional.of(new Nodes.ChoiceSearch(0, "No installed font matches", Optional.of(invalidText != null ? invalidText : String.valueOf(value)))));
        } else {
'''),
    ('            input = new Nodes.Field(id, state, kind, text, numeric.isPresent() ? spec.unit() : "", spec.label(), "", numeric,\n',
     '            // As the host: a path ends with Browse.\n'
     '            input = new Nodes.Field(id, state, kind, text, numeric.isPresent() ? spec.unit() : "", spec.label(),\n'
     '                kind == Nodes.FieldKind.PATH ? id + ":browse" : "", numeric,\n'),
    ("    static boolean matches(SettingSpec<?> spec, String query) {\n", r'''    /** The installed families a font setting's row offers, read once: all, and those that draw 'i' and 'W' alike. */
    private static final class InstalledFonts {
        static final List<String> ALL = List.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        static final List<String> MONOSPACED = monospaced();

        private static List<String> monospaced() {
            var graphics = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                var fixed = new ArrayList<String>();
                for (String family : ALL) {
                    var metrics = graphics.getFontMetrics(new java.awt.Font(family, java.awt.Font.PLAIN, 12));
                    if (metrics.charWidth('i') == metrics.charWidth('W')) fixed.add(family);
                }
                return List.copyOf(fixed);
            } finally {
                graphics.dispose();
            }
        }
    }

    static boolean matches(SettingSpec<?> spec, String query) {
'''),
])
edit('FakeSettingsPanelHost.java', [
    ("    private final Subscription reloads;\n",
     "    private final Subscription reloads;\n    private final java.util.function.Supplier<Optional<java.nio.file.Path>> chooseFile;\n"),
    ("    FakeSettingsPanelHost(FakePluginSettings settings) {\n        this.settings = settings;\n",
     "    FakeSettingsPanelHost(FakePluginSettings settings, java.util.function.Supplier<Optional<java.nio.file.Path>> chooseFile) {\n"
     "        this.settings = settings;\n        this.chooseFile = chooseFile;\n"),
    ("        if (event instanceof UiEvent.Action) {\n            row = rows.values()", r'''        if (event instanceof UiEvent.Action) {
            Row browsed = rows.values().stream().filter(value -> id.equals("settings:" + value.setting.key() + ":browse")
                && value.setting.spec().editor().orElse(null) instanceof dev.jasper.sdk.settings.EditorHint.File).findFirst().orElse(null);
            if (browsed != null) {
                // As the host: Browse asks for a file (the host's next queued file selection); cancelling changes nothing.
                Optional<java.nio.file.Path> chosen = chooseFile.get();
                if (chosen.isEmpty()) return true;
                try { stageValue(browsed.setting, chosen.get().toString()); }
                catch (RuntimeException invalid) {
                    browsed.error = invalid.getMessage() == null ? "Enter a valid value." : invalid.getMessage();
                    browsed.invalidText = chosen.get().toString();
                }
                changed();
                refresh();
                return true;
            }
            row = rows.values()'''),
])
edit('FakePluginHost.java', [
    ("var rows = new FakeSettingsPanelHost(context.settings);", "var rows = new FakeSettingsPanelHost(context.settings, this::takeFileSelection);"),
    ("     * Queues a response for the next native file chooser. With no queued response, it cancels.\n",
     "     * Queues a response for the next native file chooser, including a settings page row's Browse. With no queued response,\n"
     "     * it cancels.\n"),
])
PY
```

- [ ] **Step 4: Run the host-row tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test verifySdkArchitecture verifyApplicationArchitecture :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.settingsui.*'`
Expected: PASS. `PluginSettingsPageTest` and `EditorsTest` still pass, because the generated page's editors are
unchanged. `verifySdkArchitecture` accepts the testkit's `java.awt` font reading, which is JDK.
`verifyApplicationArchitecture` accepts `plugins` → `settingsui`, already among the allowed dependencies in the
`plugins` package-info.

- [ ] **Step 5: Write the failing page tests**

`plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsPanelTest.java`:

```java
package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.external.DefaultEditor;
import dev.jasper.editor.external.Detection;
import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTests.class)
class EditorSettingsPanelTest {
    private static final String ID = "dev.jasper.editor";
    private static final PluginInfo INFO = new PluginInfo(ID, "Editor", "0.9.0", Set.of());

    @TempDir Path home;
    private final Set<Path> programs = new HashSet<>();
    private FakePluginHost host;
    private DefaultEditor defaults;
    private Path bin;

    @BeforeEach void open() throws Exception {
        host = new FakePluginHost(home.resolve("plugins"));
        bin = Files.createDirectories(home.resolve("bin"));
    }

    @AfterEach void close() { host.close(); }

    /** Starts a plugin that gives the Editor's page over its real declarations, then opens the page as Settings would. */
    private FakeKitView page(Map<String, String> environment) {
        host.start(INFO, Set.of(), Set.of(), context -> {
            var settings = new EditorSettings.Declared(context.settings(), Set.of());
            defaults = new DefaultEditor(() -> new DefaultEditor.Preference(settings.defaultEditor(), settings.customCommand(),
                    settings.customInTerminal(), settings.keepEnvironmentEditor()),
                () -> new Detection.Probe("Mac OS X", List.of(bin), home, Map.of(), programs::contains), context.background(), Runnable::run,
                context.terminals(), (file, line, window) -> false, command -> false, Optional.empty(), "Mac OS X", context.log());
            context.settings().page(new EditorSettingsPanel(context.kit(), settings, defaults, environment));
        });
        host.settingsPanel(ID).orElseThrow();
        return host.settingsPanelView(ID).orElseThrow();
    }

    private void install(String name) { programs.add(bin.resolve(name).toAbsolutePath().normalize()); }

    /** Runs the detections the page and the tests asked for. */
    private void detect() { while (host.runBackground() > 0) { } }

    @Test void theStandardRowsKeepTheirGroupsAndDefaultEditorFollows() {
        FakeKitView view = page(Map.of());
        var page = (Nodes.Stack) view.spec().root();
        assertThat(page.role()).isEqualTo(Nodes.LayoutRole.SETTINGS_PAGE);
        assertThat(page.children()).extracting(node -> ((Nodes.Section) node).title()).containsExactly("Font", "Editing", "Files", "Default editor");
        assertThat(rows(view, EditorSettingsPanel.FONT)).containsExactly("settings:font_family:row", "settings:font_size:row");
        assertThat(rows(view, EditorSettingsPanel.EDITING)).containsExactly("settings:tab_size:row", "settings:soft_wrap:row", "settings:vim:row");
        assertThat(rows(view, EditorSettingsPanel.FILES)).containsExactly("settings:max_file_mib:row", "settings:recent_files_days:row");
        assertThat(rows(view, EditorSettingsPanel.DEFAULT))
            .containsExactly(EditorSettingsPanel.CHOICE + ":row", "settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        for (String section : List.of(EditorSettingsPanel.FONT, EditorSettingsPanel.EDITING, EditorSettingsPanel.FILES, EditorSettingsPanel.DEFAULT))
            assertThat(node(view, section + ".card", Nodes.Card.class).kind()).as(section).isEqualTo(Nodes.CardKind.SETTINGS);
        assertThat(node(view, "settings:font_family", Nodes.Choice.class).kind()).as("the font picker, as on the generated page")
            .isEqualTo(Nodes.ChoiceKind.SEARCHABLE);
        assertThat(host.failures()).isEmpty();
    }

    @Test void theDropdownListsTheJasperEditorWhatDetectionFoundTheSystemsAppAndACustomCommand() {
        install("nvim");
        install("code");
        FakeKitView view = page(Map.of());
        assertThat(options(view)).as("before detection").containsExactly("jasper=Jasper Editor", "system=System default app", "custom=Custom command");
        detect();
        assertThat(options(view)).as("detection re-renders the page")
            .containsExactly("jasper=Jasper Editor", "nvim=Neovim", "code=Visual Studio Code", "system=System default app", "custom=Custom command");
        assertThat(choice(view).kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
        assertThat(choice(view).selectedId()).isEqualTo("jasper");
    }

    /** Review Focus 5, the page part: the saved editor was uninstalled. */
    @Test void aSavedEditorThatIsNotInstalledIsListedAsNotFound() {
        host.setSetting(ID, "default_editor", "nvim");
        FakeKitView view = page(Map.of());
        detect();
        assertThat(options(view)).containsExactly("jasper=Jasper Editor", "nvim=Neovim (not found)", "system=System default app", "custom=Custom command");
        assertThat(choice(view).selectedId()).isEqualTo("nvim");
        install("nvim");
        defaults.detect();
        detect();
        assertThat(options(view)).as("installed again").contains("nvim=Neovim").doesNotContain("nvim=Neovim (not found)");
    }

    @Test void choosingStagesTheValueAndOnlyACustomCommandShowsItsRows() {
        FakeKitView view = page(Map.of());
        view.emit(new UiEvent.Selected(EditorSettingsPanel.CHOICE, Set.of("custom")));
        assertThat(host.pendingSettings(ID)).containsEntry("default_editor", "custom");
        assertThat(host.settingsPanelChanges(ID)).isPositive();
        assertThat(rows(view, EditorSettingsPanel.DEFAULT)).containsExactly(EditorSettingsPanel.CHOICE + ":row", EditorSettingsPanel.COMMAND + ":row",
            "settings:custom_in_terminal:row", "settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        assertThat(node(view, EditorSettingsPanel.COMMAND + ":row", Nodes.SettingRow.class).dependent()).isTrue();
        assertThat(node(view, "settings:custom_in_terminal:row", Nodes.SettingRow.class).dependent()).isTrue();
        assertThat(field(view).state().error()).as("an empty command").isEqualTo(EditorSettingsPanel.NO_COMMAND);
        view.emit(new UiEvent.Edited(EditorSettingsPanel.COMMAND, "subl -w"));
        assertThat(host.pendingSettings(ID)).containsEntry("custom_command", "subl -w");
        assertThat(field(view).state().error()).isEmpty();
        assertThat(field(view).value()).isEqualTo("subl -w");
        view.emit(new UiEvent.Edited(EditorSettingsPanel.COMMAND, "  "));
        assertThat(field(view).state().error()).as("a blank command").isEqualTo(EditorSettingsPanel.NO_COMMAND);
        view.emit(new UiEvent.Edited(EditorSettingsPanel.COMMAND, "subl -w"));
        host.applySettingsPanel(ID);
        assertThat(host.savedSettings(ID)).containsEntry("default_editor", "custom").containsEntry("custom_command", "subl -w");
        view.emit(new UiEvent.Selected(EditorSettingsPanel.CHOICE, Set.of("system")));
        assertThat(rows(view, EditorSettingsPanel.DEFAULT))
            .containsExactly(EditorSettingsPanel.CHOICE + ":row", "settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        view.emit(new UiEvent.Action(EditorSettingsPanel.CHOICE + ":reset"));
        assertThat(choice(view).selectedId()).as("Reset").isEqualTo("jasper");
        assertThat(host.failures()).isEmpty();
    }

    @Test void theNoticeSaysWhatJaspersEnvironmentHolds() {
        assertThat(EditorSettingsPanel.environmentText(Map.of("EDITOR", "nvim"))).isEqualTo("Jasper's environment has EDITOR=nvim");
        assertThat(EditorSettingsPanel.environmentText(Map.of("VISUAL", "code --wait"))).isEqualTo("Jasper's environment has VISUAL=code --wait");
        assertThat(EditorSettingsPanel.environmentText(Map.of("EDITOR", "vim", "VISUAL", "nvim")))
            .isEqualTo("Jasper's environment has EDITOR=vim and VISUAL=nvim");
        assertThat(EditorSettingsPanel.environmentText(Map.of("EDITOR", " "))).as("blank is unset")
            .isEqualTo("Jasper's environment has neither EDITOR nor VISUAL");
        assertThat(EditorSettingsPanel.environmentText(Map.of())).isEqualTo("Jasper's environment has neither EDITOR nor VISUAL");
    }

    /** Review Focus 2, the page part: Jasper started from a terminal with EDITOR=vim says so beside the setting that keeps it. */
    @Test void thePageShowsJaspersEditorBesideTheSettingThatKeepsIt() {
        FakeKitView view = page(Map.of("EDITOR", "vim"));
        var notice = node(view, EditorSettingsPanel.ENVIRONMENT, Nodes.SettingRow.class);
        assertThat(notice.title()).isEqualTo("Jasper's environment has EDITOR=vim");
        assertThat(notice.description()).isEqualTo("An export in a shell startup file, such as ~/.zshrc, still wins.");
        assertThat(rows(view, EditorSettingsPanel.DEFAULT)).containsSubsequence("settings:keep_environment_editor:row", EditorSettingsPanel.ENVIRONMENT);
        view.emit(new UiEvent.Toggled("settings:keep_environment_editor", false));
        assertThat(host.pendingSettings(ID)).containsEntry("keep_environment_editor", false);
    }

    @Test void searchMarksThePagesOwnRows() {
        FakeKitView view = page(Map.of());
        host.highlightSettingsPanel(ID, "default editor");
        assertThat(node(view, EditorSettingsPanel.CHOICE + ":row", Nodes.SettingRow.class).highlighted()).isTrue();
        host.highlightSettingsPanel(ID, "");
        assertThat(node(view, EditorSettingsPanel.CHOICE + ":row", Nodes.SettingRow.class).highlighted()).isFalse();
    }

    @Test void aClosedPageNoLongerFollowsDetection() {
        page(Map.of());
        detect();
        host.closeSettingsPanel(ID);
        install("vim");
        defaults.detect();
        detect();
        assertThat(defaults.detected()).hasSize(1);
        assertThat(host.failures()).as("the closed page was not rendered").isEmpty();
    }

    private static List<String> rows(FakeKitView view, String section) {
        return node(view, section + ".card", Nodes.Card.class).children().stream().map(Nodes.Node::id).toList();
    }

    private static Nodes.Choice choice(FakeKitView view) { return node(view, EditorSettingsPanel.CHOICE, Nodes.Choice.class); }

    private static Nodes.Field field(FakeKitView view) { return node(view, EditorSettingsPanel.COMMAND, Nodes.Field.class); }

    private static List<String> options(FakeKitView view) {
        return choice(view).options().stream().map(option -> option.id() + "=" + option.label()).toList();
    }

    private static <T extends Nodes.Node> T node(FakeKitView view, String id, Class<T> type) {
        Nodes.Node found = find(view.spec().root(), id);
        assertThat(found).as(id).isInstanceOf(type);
        return type.cast(found);
    }

    private static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<Nodes.Node> children = switch (node) {
            case Nodes.Stack stack -> stack.children();
            case Nodes.Section section -> List.of(section.body());
            case Nodes.Card card -> card.children();
            case Nodes.SettingRow row -> List.of(row.control());
            default -> List.of();
        };
        for (Nodes.Node child : children) {
            Nodes.Node found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
```

Add to `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java')
t = p.read_text(encoding='utf-8').rstrip()
assert t.endswith('}')
t = t[:-1] + r'''
    @Test void theSettingsPageIsTheEditorsOwn() {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsPanel("dev.jasper.editor")).isPresent();
            assertThat(host.settingsPanelView("dev.jasper.editor").orElseThrow().spec().root().id()).isEqualTo("editor.settings");
            drain(host);
            assertThat(host.failures()).isEmpty();
        }
    }
}
'''
p.write_text(t, encoding='utf-8')
PY
```

- [ ] **Step 6: Run the page tests to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorSettingsPanelTest' --tests 'dev.jasper.editor.EditorPluginTest'`
Expected: `compileTestJava` FAILED: `cannot find symbol` for `EditorSettingsPanel`.

- [ ] **Step 7: Write the page**

`plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettingsPanel.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.external.DefaultEditor;
import dev.jasper.editor.external.Detected;
import dev.jasper.editor.external.KnownEditors;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Editor's page in Settings > Tools > Editor: Font, Editing and Files as Settings' standard rows, grouped as the
 * generated page grouped them, then Default editor: a dropdown of the Jasper Editor, the editors detection found, the
 * system's default app and a custom command, the custom command's rows while it is chosen, and what Jasper's own
 * environment holds for {@code EDITOR} and {@code VISUAL}. Detection runs again each time the page opens, and the page
 * follows it. UI thread.
 */
public final class EditorSettingsPanel implements SettingsPanel {
    static final String PAGE = "editor.settings", FONT = "editor.settings.font", EDITING = "editor.settings.editing",
        FILES = "editor.settings.files", DEFAULT = "editor.settings.default", CHOICE = "editor.default-editor",
        COMMAND = "editor.custom-command", ENVIRONMENT = "editor.environment";
    /** The note under what Jasper's environment holds. */
    static final String NOTE = "An export in a shell startup file, such as ~/.zshrc, still wins.";
    /** The custom command field's error while it is blank. */
    static final String NO_COMMAND = "Enter a command; until then files open with your system's default app.";

    private final UiKit kit;
    private final EditorSettings.Declared settings;
    private final DefaultEditor defaults;
    private final Map<String, String> environment;
    private SettingsPanelHost host;
    private UiView view;
    private Subscription detecting = () -> { };
    private String query = "";

    /**
     * @param kit the plugin's kit
     * @param settings the Editor's declared settings
     * @param defaults the default editor, whose detection the dropdown lists
     * @param environment Jasper's own environment, for what the page says about {@code EDITOR} and {@code VISUAL}
     */
    public EditorSettingsPanel(UiKit kit, EditorSettings.Declared settings, DefaultEditor defaults, Map<String, String> environment) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.environment = Map.copyOf(environment);
    }

    @Override public UiView create(SettingsPanelHost host) {
        this.host = host;
        view = kit.create(new ViewSpec(node()), this::event);
        detecting = defaults.onDetected(this::render);
        defaults.detect();
        return view;
    }

    @Override public List<String> keywords() {
        return List.of("default editor", "EDITOR", "VISUAL", "git", "external editor", "custom command", "vim", "nvim", "code");
    }

    @Override public void highlight(String query) {
        this.query = Objects.requireNonNull(query, "query");
        render();
    }

    @Override public void dispose() {
        detecting.close();
        detecting = () -> { };
        view = null;
        host = null;
    }

    private void event(UiEvent event) {
        if (view == null) return;
        switch (event) {
            case UiEvent.Selected selected when selected.id().equals(CHOICE) ->
                selected.ids().stream().findFirst().ifPresent(id -> stage(settings.defaultEditor, id));
            case UiEvent.Action action when action.id().equals(CHOICE + ":reset") -> stage(settings.defaultEditor, EditorSettings.Declared.JASPER);
            case UiEvent.Edited edited when edited.id().equals(COMMAND) -> stage(settings.customCommand, edited.text());
            case UiEvent.Action action when action.id().equals(COMMAND + ":reset") -> stage(settings.customCommand, "");
            default -> { }
        }
    }

    private void stage(Setting<String> setting, String value) {
        host.stage(setting, value);
        host.changed();
        render();
    }

    private void render() { if (view != null) view.update(new ViewSpec(node())); }

    private Nodes.Stack node() {
        String chosen = host.pending(settings.defaultEditor);
        var defaultEditor = new ArrayList<Nodes.Node>();
        defaultEditor.add(choiceRow(chosen));
        if (chosen.equals("custom")) {
            defaultEditor.add(commandRow(host.pending(settings.customCommand)));
            defaultEditor.add(dependent(host.row(settings.customInTerminal)));
        }
        defaultEditor.add(host.row(settings.keepEnvironmentEditor));
        defaultEditor.add(environmentRow());
        return new Nodes.Stack(PAGE, ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, List.of(
            section(FONT, "Font", List.of(host.row(settings.fontFamily), host.row(settings.fontSize))),
            section(EDITING, "Editing", List.of(host.row(settings.tabSize), host.row(settings.softWrap), host.row(settings.vim))),
            section(FILES, "Files", List.of(host.row(settings.maxFileMib), host.row(settings.recentFilesDays))),
            section(DEFAULT, "Default editor", defaultEditor)));
    }

    /** The dropdown; a saved editor detection did not find is its own option, "(not found)". */
    private Nodes.SettingRow choiceRow(String chosen) {
        var options = new ArrayList<Option>();
        options.add(new Option(EditorSettings.Declared.JASPER, "Jasper Editor", Optional.empty()));
        List<Detected> found = defaults.detected();
        for (Detected editor : found) options.add(new Option(editor.editor().id(), editor.editor().name(), Optional.empty()));
        if (found.stream().noneMatch(editor -> editor.editor().id().equals(chosen)))
            KnownEditors.byId(chosen).ifPresent(missing -> options.add(new Option(missing.id(), missing.name() + " (not found)", Optional.empty())));
        options.add(new Option("system", "System default app", Optional.empty()));
        options.add(new Option("custom", "Custom command", Optional.empty()));
        var choice = new Nodes.Choice(CHOICE, ControlState.READY, Nodes.ChoiceKind.DROPDOWN, options, chosen,
            EditorSettings.Declared.DEFAULT_EDITOR_LABEL, CHOICE);
        return row(CHOICE, EditorSettings.Declared.DEFAULT_EDITOR_LABEL, EditorSettings.Declared.DEFAULT_EDITOR_DESCRIPTION, choice,
            !chosen.equals(EditorSettings.Declared.JASPER), false);
    }

    private Nodes.SettingRow commandRow(String command) {
        var field = new Nodes.Field(COMMAND, new ControlState(true, true, false, command.isBlank() ? NO_COMMAND : ""), Nodes.FieldKind.TEXT,
            command, "", EditorSettings.Declared.COMMAND_LABEL, "");
        return row(COMMAND, EditorSettings.Declared.COMMAND_LABEL, EditorSettings.Declared.COMMAND_DESCRIPTION, field, !command.isEmpty(), true);
    }

    /** A row arranged as Settings' standard rows: the control, then an icon-only Reset while the value is not the default. */
    private Nodes.SettingRow row(String id, String title, String description, Nodes.Node control, boolean resettable, boolean dependent) {
        var reset = new Nodes.Button(id + ":reset", new ControlState(resettable, true, false, ""), "", "reset", Nodes.ButtonKind.GHOST,
            id + ":reset", "Reset " + title + " to default");
        var trailing = new Nodes.Stack(id + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(control, reset));
        return new Nodes.SettingRow(id + ":row", ControlState.READY, title, description, trailing, dependent, matches(title, description));
    }

    private Nodes.SettingRow environmentRow() {
        return new Nodes.SettingRow(ENVIRONMENT, ControlState.READY, environmentText(environment), NOTE,
            new Nodes.Stack(ENVIRONMENT + ":controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of()), false, false);
    }

    /**
     * What the page says about Jasper's own {@code EDITOR} and {@code VISUAL}; blank values count as unset, as they do
     * for {@code keep_environment_editor}.
     */
    static String environmentText(Map<String, String> environment) {
        String editor = environment.get("EDITOR"), visual = environment.get("VISUAL");
        boolean hasEditor = editor != null && !editor.isBlank(), hasVisual = visual != null && !visual.isBlank();
        if (hasEditor && hasVisual) return "Jasper's environment has EDITOR=" + editor + " and VISUAL=" + visual;
        if (hasEditor) return "Jasper's environment has EDITOR=" + editor;
        if (hasVisual) return "Jasper's environment has VISUAL=" + visual;
        return "Jasper's environment has neither EDITOR nor VISUAL";
    }

    /** A standard row shown under the dropdown, indented as a setting that depends on it. */
    private static Nodes.Node dependent(Nodes.Node row) {
        return row instanceof Nodes.SettingRow standard ? new Nodes.SettingRow(standard.id(), standard.state(), standard.title(),
            standard.description(), standard.control(), true, standard.highlighted(), standard.titleNote()) : row;
    }

    private boolean matches(String... texts) {
        var words = Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) return false;
        String text = String.join(" ", texts).toLowerCase(Locale.ROOT);
        return words.stream().allMatch(text::contains);
    }

    private static Nodes.Section section(String id, String title, List<Nodes.Node> rows) {
        return new Nodes.Section(id, ControlState.READY, title, new Nodes.Card(id + ".card", ControlState.READY, Nodes.CardKind.SETTINGS, rows));
    }
}
```

Open the existing handles to the page in `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, and
give the page in `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java')
t = p.read_text(encoding='utf-8')
for old, new in [
    ("""        private final Setting<String> fontFamily;
        private final Setting<Integer> fontSize, tabSize, maxFileMib, recentFilesDays;
        private final Setting<Boolean> softWrap, vim;
""", """        final Setting<String> fontFamily;
        final Setting<Integer> fontSize, tabSize, maxFileMib, recentFilesDays;
        final Setting<Boolean> softWrap, vim;
"""),
    ("    /** The Editor's declared settings; Settings checks their values and reports bad ones. */\n",
     "    /** The Editor's declared settings; Settings checks their values and reports bad ones. {@link EditorSettingsPanel} shows them. */\n"),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
p.write_text(t, encoding='utf-8')
q = pathlib.Path('plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java')
t = q.read_text(encoding='utf-8')
for old, new in [
    ("import dev.jasper.editor.ui.EditorSettings;\n", "import dev.jasper.editor.ui.EditorSettings;\nimport dev.jasper.editor.ui.EditorSettingsPanel;\n"),
    ("        context.platform().editor(defaults::edit);\n",
     "        context.platform().editor(defaults::edit);\n"
     "        context.settings().page(new EditorSettingsPanel(context.kit(), settings, defaults, System.getenv()));\n"),
]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
q.write_text(t, encoding='utf-8')
PY
```

Append the ledger rows:

```bash
python3 - <<'PY'
import pathlib
csv = pathlib.Path('docs/design/theme-engine/migration.csv')
text = csv.read_text(encoding='utf-8')
main, test = 'plugins/editor/src/main/java/dev/jasper/editor/ui/', 'plugins/editor/src/test/java/dev/jasper/editor/ui/'
rows = [
    main + 'EditorSettingsPanel.java,dev.jasper.editor,EditorSettingsPanel,standard-ui,10,8,' + test + 'EditorSettingsPanelTest.java;plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java,New in the default editor plan Task 9; owned kit SETTINGS page: host rows for the declared settings and the Default editor dropdown and custom command field and environment notice of its own; no raw Swing',
    test + 'EditorSettingsPanelTest.java,dev.jasper.editor,EditorSettingsPanelTest,nonvisual-helper,none,none,' + test + 'EditorSettingsPanelTest.java,Test/fixture ownership; New in the default editor plan Task 9; descriptor assertions on the testkit fake',
]
assert text.endswith('\n')
for row in rows:
    assert row.count(',') == 7, row
    assert '\n' + row.split(',', 1)[0] + ',' not in text, row
csv.write_text(text + ''.join(row + '\n' for row in rows), encoding='utf-8')
PY
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-plugin-editor:test verifyPluginArchitecture :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS. `verifyPluginArchitecture`'s presentation check accepts `EditorSettingsPanel`: it builds only `Nodes`
descriptors and constructs no Swing control. `PluginSettingsPagesEvidenceTest` still finds the Editor page's first
card (`editor.settings.font.card`) at the same x, width and fill as the Sample and Project Index pages, its Font row
now the same font picker as the generated page showed.

- [ ] **Step 9: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsPage.java \
        jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java \
        jasper-app/src/main/java/dev/jasper/app/plugins/PluginPanelPage.java \
        jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java \
        jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java \
        jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/KitSettingRows.java \
        jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSettingsPanelHost.java \
        jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java \
        jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java \
        plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettingsPanel.java \
        plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java \
        plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java \
        plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsPanelTest.java \
        plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java \
        docs/design/theme-engine/migration.csv
git commit -m "$(cat <<'EOF'
Editor: its own settings page with a Default editor section

Host rows now give EditorHint.Font the generated page's font picker and
EditorHint.File its path with Browse, in the app and the testkit, through
one hint-to-editor choice (PluginSettingsPage.Hint), so the page's Font row
is unchanged. Font, Editing and Files keep Settings' standard rows. Default
editor lists
the Jasper Editor, the editors detection found (a saved one that is gone as
"(not found)"), the system's default app and a custom command, shows the
custom command's rows only while it is chosen, with an error while it is
empty, and says what EDITOR and VISUAL Jasper's own environment holds.
Opening the page detects again and the page follows.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: Documentation, status and the whole-branch check

**Files:**
- Modify: `docs/editor.md`, `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`,
  `jasper-sdk/README.md`, `docs/STATUS.md`,
  `docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md`, this plan's status line

**Interfaces:**
- Consumes: everything Tasks 1–9 shipped.
- Produces: documentation only. `AppDocumentationTest` checks the links and anchors (`editor.md#default-editor`), and
  `VersionRangeTest` checks that every `sdk = "..."` in `plugin-authoring.md` and `jasper-sdk/README.md` admits 0.17.1.

Follow AGENTS.md's documentation rules: the smallest edit that makes each doc accurate, describing only what the system
does.

- [ ] **Step 1: User and plugin documentation**

```bash
python3 - <<'PY'
import pathlib

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:70])
    p.write_text(t.replace(old, new), encoding='utf-8')

# docs/editor.md: the four keys, and what the default editor does.
edit('docs/editor.md',
    "| `vim` | false | Vim mode (below); the Vim status item and Toggle Vim Mode change it |\n",
    "| `vim` | false | Vim mode (below); the Vim status item and Toggle Vim Mode change it |\n"
    "| `default_editor` | `\"jasper\"` | the [default editor](#default-editor): `jasper`, `system`, `custom` or an installed editor's id |\n"
    "| `custom_command` | `\"\"` | for `custom`: the command; the file's path is added at the end |\n"
    "| `custom_in_terminal` | true | for `custom`: run it in a terminal tab |\n"
    "| `keep_environment_editor` | true | when Jasper's own environment has `EDITOR` or `VISUAL`, its shells keep both |\n")
edit('docs/editor.md',
    "A bad value is reported and its default used.\n\n## Vim mode\n",
    """A bad value is reported and its default used.

## Default editor

**Default editor** on the Editor's page (Settings › Tools › Editor) chooses the editor for the files Jasper opens itself
(Open in Editor for `config.toml`, from the configuration banner and notices, for a plugin's settings file and for the
VM options file, and plugins' Open in Editor) and the `EDITOR` and `VISUAL` of Jasper's shells, which `git commit`,
`crontab -e` and `kubectl edit` use:

- **Jasper Editor**, the default: files open in a tab here. `EDITOR` and `VISUAL` are the `jasper` command's path
  followed by `editor --wait`.
- **An installed editor**, by name. Neovim, Vim, vi, nano, micro, Helix and Emacs open in a new terminal tab at the
  line, in the window the file was opened from, else the active one; the tab closes when the editor exits cleanly.
  Visual Studio Code, Cursor, Zed, Sublime Text, BBEdit, TextMate, IntelliJ IDEA, Notepad++ and Notepad start on their
  own at the line. `EDITOR` and `VISUAL` are the editor's path, with `-nw` for Emacs and the app editors' wait options
  (`--wait`, `-w`, `-multiInst -nosession`).
- **System default app**: files open with your system's app for them, and Jasper sets no `EDITOR` or `VISUAL`.
- **Custom command**: `custom_command` with the file's path added, run by the shell in a terminal tab, or on its own
  when `custom_in_terminal` is off. `EDITOR` and `VISUAL` are the command as written. Until you enter one, files open
  with the system's default app.

The page lists the editors Jasper finds on its `PATH`, in `/opt/homebrew/bin`, `/usr/local/bin` and `~/.local/bin`, as
the command-line tools inside the apps in `/Applications` and `~/Applications` on macOS, and in Notepad++'s and
Notepad's own folders on Windows; IntelliJ IDEA is found only through its `idea` script on `PATH`. It looks when the
Editor starts and each time the page opens. A saved editor it no longer finds shows "(not found)" and acts as System
default app. When Jasper itself was started with `EDITOR` or `VISUAL` set, for example from a terminal, its shells keep
both and Jasper sets neither until you turn off `keep_environment_editor`; the page shows what Jasper's environment
has, and an export in a shell startup file, such as `~/.zshrc`, still wins. When the chosen editor cannot open a file
(a terminal editor with no terminal window open, an app that does not start) or the Editor is off, the file opens with
the system's default app.

## Vim mode
""")

# docs/configuration.md
edit('docs/configuration.md',
    "then open it in your operating system's editor. Jasper tries Edit, then Open, then revealing the file in Finder/Explorer when supported.",
    "then open it in the [default editor](editor.md#default-editor), the Jasper Editor unless you choose another. When that editor does not open it, Jasper tries your operating system's Edit, then Open, then revealing the file in Finder/Explorer when supported.")
edit('docs/configuration.md',
    "`font_family`, `font_size`, `tab_size`, `soft_wrap`, `max_file_mib` and `vim`, applied live;",
    "`font_family`, `font_size`, `tab_size`, `soft_wrap`, `max_file_mib`, `vim` and the [default editor](editor.md#default-editor)'s `default_editor`, `custom_command`, `custom_in_terminal` and `keep_environment_editor`, applied live;")
edit('docs/configuration.md',
    "Environment entries overlay the inherited child environment.",
    "Environment entries overlay the inherited child environment and the variables plugins add, such as the Editor's `EDITOR` and `VISUAL` for the [default editor](editor.md#default-editor), which reach local shells and the editors' terminal tabs, not WSL tabs or plugin sessions.")
edit('docs/configuration.md',
    "Launch failures continue to show an error and remove the failed pane.\n",
    "Launch failures continue to show an error and remove the failed pane. A tab that runs the [default editor](editor.md#default-editor) closes after a code 0 exit and keeps its output otherwise, whatever this setting says.\n")

# docs/plugin-authoring.md
edit('docs/plugin-authoring.md',
    "on generated pages and in `host.row` rows alike. To build the page yourself,",
    "on generated pages and in `host.row` rows alike, and so do `EditorHint.font` (the font family picker) and `EditorHint.file` (a path with Browse…). To build the page yourself,")
edit('docs/plugin-authoring.md',
    "files.source, network.listen, clipboard.read\n",
    "files.source, network.listen, clipboard.read, terminal.environment, editor.provide\n")
edit('docs/plugin-authoring.md',
    "with `OpenRequest.local()` or `localProfile(id)`, and `Terminals.profiles()` and `onProfilesChanged` |",
    "with `OpenRequest.local()`, `localProfile(id)` or a program (`LocalSpec.running`, SDK 0.17.1), and `Terminals.profiles()` and `onProfilesChanged` |")
edit('docs/plugin-authoring.md',
    "| `clipboard.read` | `Platform.clipboardText()`: read the text on the clipboard (SDK 0.16.1) |\n",
    "| `clipboard.read` | `Platform.clipboardText()`: read the text on the clipboard (SDK 0.16.1) |\n"
    "| `terminal.environment` | `Terminals.environment`: add variables to new local shells (SDK 0.17.1) |\n"
    "| `editor.provide` | `Platform.editor`: be the editor Jasper opens files with (SDK 0.17.1) |\n")
edit('docs/plugin-authoring.md',
    "`context.platform().openInEditor(file)` opens a file with the user's editor (configured editor, then the desktop, then\nrevealing the file), in the background;",
    "`context.platform().openInEditor(file)` opens a file with the user's editor (the plugin editor `Platform.editor` registered,\nnormally the Editor's [default editor](editor.md#default-editor), then the desktop, then revealing the file), in the background;")
edit('docs/plugin-authoring.md',
    "handler. Declare `sdk = \">=0.17.0, <0.18\"`.\n\n## Rules that matter\n",
    """handler. Declare `sdk = ">=0.17.0, <0.18"`.

## Shell variables, program tabs and the file editor

SDK 0.17.1 adds four hooks. Declare `sdk = ">=0.17.1, <0.18"` when you use them.

- `context.terminals().environment(contributor)` (needs `terminal.environment`) adds variables to each new local
  shell and program tab. The contributor runs on the UI thread, must not block or do I/O, and gets Jasper's own
  environment, read-only. A name matches `[A-Za-z_][A-Za-z0-9_]*`; `TERM`, `COLORTERM`, `TERM_PROGRAM`,
  `TERM_PROGRAM_VERSION` and names starting `JASPER_` are reserved, and a value holding NUL is dropped; each dropped
  entry is logged. A `[terminal.env]` entry still wins, and when two plugins set one name the first by plugin id wins.
  WSL tabs and provided sessions get none.
- `context.platform().editor(fileEditor)` (needs `editor.provide`) makes your plugin the editor Jasper opens files with:
  Open in Editor for `config.toml`, plugin settings files and the VM options file, and every plugin's `openInEditor`.
  `FileEditor.edit(file, line, window)` runs on the UI thread and returns whether it opened the file; `false`, or a
  handler that throws, hands the file to the desktop. One plugin at a time: a bundled plugin's registration replaces
  another's, otherwise a later one throws `IllegalStateException`.
- `OpenRequest.local(LocalSpec.running(command, workingDirectory))` opens a tab or split that runs a program instead of
  the shell, with the default shell's environment; it needs `terminal.open`. The tab closes when the program exits with
  status 0 and stays open otherwise, whatever `terminal.on_exit` says. A `LocalSpec` names a profile or a command, not
  both.
- `context.commandLine().client()` is the `jasper` command's path (inside the installed app, or the build's in a
  development run), for a value such as `EDITOR`; empty when there is none.

## Rules that matter
""")
edit('docs/plugin-authoring.md',
    "`openRequests()` then ends a line with `|<profile id>` when the request named one. Give the\n`PluginInfo`",
    "`openRequests()` then ends a line with `|<profile id>` when the request named one, and a program tab's or split's\n"
    "line with `|run:<command>`, its words joined by spaces. `host.shellEnvironment(jasperEnvironment)` returns what the\n"
    "plugins' `Terminals.environment` contributors add for a new shell; `host.edit(file, line, windowId)` calls the\n"
    "`Platform.editor` file editor, `host.editorPlugin()` names its plugin and `host.declareBundled(id)`, before that\n"
    "plugin starts, makes it bundled; `host.setCommandClient(path)` sets `commandLine().client()`; a settings row's Browse…\n"
    "takes the next `host.queueFileSelection`. Give the\n`PluginInfo`")

# docs/sdk-architecture.md
edit('docs/sdk-architecture.md',
    "`platform().openInEditor` runs the application's\n`ConfigEditor` on the plugin's executor and posts a failure in the plugin's notification group, and\n`platform().browse` does the same",
    "`platform().openInEditor` asks the provided file\neditor (SDK 0.17.1, below) and then the application's `ConfigEditor`, from the plugin's executor, and posts a failure\nin the plugin's notification group, and `platform().browse` does the same")
edit('docs/sdk-architecture.md',
    "Explicit commands in `LocalSpec`, and a stronger locality signal than the host name, such as a\nper-session token from Jasper's own shell integration.",
    "A stronger locality signal than the host name, such as a per-session token from Jasper's own shell integration.")
edit('docs/sdk-architecture.md',
    "Plugins open only shells the application detected: explicit\ncommands in `LocalSpec` remain a separate decision. Remote uses this for its WSL group.",
    "A `LocalSpec` names a profile or, since SDK 0.17.1, a\ncommand, not both. Remote uses profiles for its WSL group.")
edit('docs/sdk-architecture.md',
    "\n## Contract suite\n",
    """
## Shell variables, the file editor and program tabs (SDK 0.17.1)

`Terminals.environment` (needs `terminal.environment`) registers a contributor in `plugins.ShellContributions`. For each
local launch the application asks every contributor on the UI thread, with Jasper's own environment, and merges the
answers: the first plugin by id keeps a name two set; reserved, malformed and NUL-holding entries are dropped and logged
once per plugin and name; a contributor that throws adds nothing and counts as a plugin failure. `LaunchSettings` puts
them after Jasper's own variables and before `[terminal.env]`; WSL profiles and provided sessions get none.
`Platform.editor` (needs `editor.provide`) registers a `FileEditor` in `plugins.FileEditors`, one at a time, a bundled
plugin's replacing another's. Every editor open in the application (`ConfigEditor`'s callers, the Plugin Manager's
settings files, the VM options file and `Platform.openInEditor`) runs on a worker, which asks the file editor on the UI
thread through `FileEditors.editFromWorker`, waits up to 30 s, and otherwise runs the desktop fallback itself.
`LocalSpec.command` becomes `terminals.OpenSpec.Local.program`; the workspace launches it through `ShellLauncher` with
the default profile's environment and pins close-on-success over `terminal.on_exit`. `CommandLine.client()` comes from
`commandline.CommandInstall.client`: the client inside the installed image, or the build's in a development run. The
testkit's `FakePluginHost` models all four, and `PluginContractTest` holds both hosts to them.

## Contract suite
""")

# jasper-sdk/README.md
edit('jasper-sdk/README.md', "`SyntaxStyle`, `Platform` |", "`SyntaxStyle`, `Platform`, `FileEditor` |")
edit('jasper-sdk/README.md',
    "Bundled manifests require `sdk = \">=0.17.0, <0.18\"` (0.17.0 adds",
    "Bundled manifests require `sdk = \">=0.17.0, <0.18\"`, the Editor `>=0.17.1` (0.17.1 adds `Terminals.environment`, `Platform.editor` with `FileEditor`, `LocalSpec.command` and `CommandLine.client()`; 0.17.0 adds")
edit('jasper-sdk/README.md',
    "\n## Command-line subcommands (SDK 0.17.0)\n",
    """
## Shell variables, the file editor and program tabs (SDK 0.17.1)

`Terminals.environment(contributor)` adds variables to new local shells (capability `terminal.environment`,
`Capabilities.TERMINAL_ENVIRONMENT`); `Platform.editor(FileEditor)` makes a plugin the editor Jasper opens files with
(`editor.provide`, `Capabilities.EDITOR_PROVIDE`); `LocalSpec.running(command, workingDirectory)` opens a tab that runs
a program instead of the shell; `CommandLine.client()` is the `jasper` command's path. Each is a default method or a
record component behind the old constructors. The testkit adds `FakePluginHost.shellEnvironment`, `edit`,
`editorPlugin`, `declareBundled` and `setCommandClient`. Plugins that use them declare `sdk = ">=0.17.1, <0.18"`.

## Command-line subcommands (SDK 0.17.0)
""")
PY
```

- [ ] **Step 2: The whole-branch check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :jasper-app:verifyPackage`
Expected: BUILD SUCCESSFUL (headless; no GUI, no AOT training).

Run the source hygiene check from AGENTS.md, extended to the Editor's settings template:

```bash
python3 - <<'PY'
import pathlib
roots = ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins")
paths = [p for root in roots for p in pathlib.Path(root).rglob("*.java")] + sorted(pathlib.Path("plugins/editor/src/main/resources").rglob("*.toml"))
for p in paths:
    text = p.read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 3: Status**

Add the entry at the top of `docs/STATUS.md`, with the totals read from every module's and plugin's JUnit XML from
the run in step 2, and set the spec's and this plan's status lines. `RULINGS` is the one sentence to change by hand:
when the executor recorded rulings or deviations in this plan's banner during Tasks 1–9, replace it with one sentence
naming them.

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
entry = f"""**The default editor (command line plan 2) — {today}** (`claude/default-editor`, worktree
`.claude/worktrees/default-editor`; [spec](superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md),
[plan](superpowers/plans/2026-10-14-jasper-command-line-2-default-editor.md)). Settings › Tools › Editor › Default editor
chooses the Jasper Editor (the default), an installed editor (Neovim, Vim, vi, nano, micro, Helix, Emacs, VS Code,
Cursor, Zed, Sublime Text, BBEdit, TextMate, IntelliJ IDEA, Notepad++, Notepad: found on `PATH`, in Homebrew's folder,
`/usr/local/bin` and `~/.local/bin`, and inside macOS apps), a custom command or the system's default app. Jasper's
local shells and program tabs get `EDITOR` and `VISUAL` for it (the Jasper Editor's are `<jasper command> editor
--wait`) unless Jasper's own environment has either and `keep_environment_editor` is on; `config.toml`, plugin settings
files, the VM options file and every plugin's Open in Editor open in it on the UI thread, with the desktop as the
fallback. Terminal editors run at the line in a program tab that closes on a clean exit; app editors start on their
own. SDK 0.17.1: `Terminals.environment` (`terminal.environment`), `Platform.editor` with `FileEditor`
(`editor.provide`), `LocalSpec.command` (`LocalSpec.running`) and `CommandLine.client()`; Editor 0.9.0 requires
`>=0.17.1, <0.18`, the other bundled plugins keep `>=0.17.0`. The Editor's settings page is its own
(`EditorSettingsPanel`); plugin pages' standard rows now give `EditorHint.font` the font picker and `EditorHint.file` a
path with Browse…, as the generated page does (app and testkit), so its Font row is unchanged. Deviations: the twelve in the plan's Planned deviations. {RULINGS} `./gradlew check
--continue`: **{tests} tests, {failures} failures, {errors} errors, {skipped} skipped**. No GUI launched. GUI checks for
the user, in the packaged app: `git commit` in a Jasper tab opens the Jasper Editor and returns when its tab closes;
choose Neovim and `git commit` again (a tab running nvim), then Visual Studio Code (VS Code opens, git waits); System
Settings › Open in Editor for `config.toml`, with the Jasper Editor and with a terminal editor chosen; and the Editor's
page after starting Jasper from iTerm with `EDITOR=vim` (the notice, and new shells keeping vim until the setting is
off). Not merged or pushed. Plan 3 (Cmd-click file paths) is next."""

def edit(path, old, new):
    p = pathlib.Path(path)
    t = p.read_text(encoding='utf-8')
    assert t.count(old) == 1, (path, old[:70])
    p.write_text(t.replace(old, new), encoding='utf-8')

edit('docs/STATUS.md', "# Jasper — Status and Handoff\n\n", "# Jasper — Status and Handoff\n\n" + entry + "\n\n")
edit('docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md',
    "its deviations are in the plan's banner. Branch:",
    f"its deviations are in the plan's banner. Plan 2 (the default editor) complete on {today}: [`2026-10-14-jasper-command-line-2-default-editor.md`](../plans/2026-10-14-jasper-command-line-2-default-editor.md); its deviations are in that plan's Planned deviations and `docs/STATUS.md`. Branch:")
# The banner may carry rulings recorded during execution: change only its state, keep the rest.
plan = pathlib.Path('docs/superpowers/plans/2026-10-14-jasper-command-line-2-default-editor.md')
t = plan.read_text(encoding='utf-8')
assert t.count("Status: **Written 2026-10-14**") == 1
t = t.replace("Status: **Written 2026-10-14**", f"Status: **Complete on {today}** (written 2026-10-14)", 1)
plan.write_text(t.replace(" plan 1 merged). Not started.", " plan 1 merged).", 1), encoding='utf-8')
print(entry.splitlines()[0], counts)
PY
```

Expected: it prints the entry's first line and four totals whose failures and errors are 0.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' --tests 'dev.jasper.app.plugins.VersionRangeTest'`
Expected: PASS (links, anchors and SDK ranges in the edited docs).

- [ ] **Step 4: Commit**

```bash
git add docs/editor.md docs/configuration.md docs/plugin-authoring.md docs/sdk-architecture.md jasper-sdk/README.md \
        docs/STATUS.md docs/superpowers/specs/2026-10-13-jasper-command-line-and-default-editor-design.md \
        docs/superpowers/plans/2026-10-14-jasper-command-line-2-default-editor.md
git commit -m "$(cat <<'EOF'
Document the default editor and SDK 0.17.1

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```
