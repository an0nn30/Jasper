# Keymap defaults K1: contexts and defaults — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every Keymap action a context (Global, Terminal, a plugin's content tabs, a panel, a window) so that one key
can mean different things in different places and a key whose action doesn't apply, or is disabled, reaches the
focused component (the shell) instead of being swallowed. Also give every built-in action explicit macOS and
Linux/Windows defaults, and move the existing app and plugin actions to the approved defaults.

**Architecture:** A new app value `KeyContext` (package `commands`) is carried by `ActionId`, by
`KeyBindings.Extension` and by `ActionEntry`. `KeyBindings` keeps several actions per stroke when their contexts
don't conflict, and resolves a stroke against a "does this context contain focus" predicate. `WindowContent` builds
that predicate from the focus owner (terminal pane, selected content tab, panel). The root input map consumes a key
only when dispatch would use it. SDK 0.11.0 adds `ShortcutContext` and `ActionSpec.withContext` /
`withDefaultBindings`; `HostedUi` maps them onto the app types. The Keymap page gains a Where column.

**Tech Stack:** Java 25 (JetBrains Runtime 25), Swing/FlatLaf, Gradle wrapper, JUnit 5 and AssertJ.

**Spec:** [docs/superpowers/specs/2026-09-30-keymap-defaults-design.md](../specs/2026-09-30-keymap-defaults-design.md)
(the K1 entry under [Delivery](../specs/2026-09-30-keymap-defaults-design.md#delivery); default rows from
[Defaults](../specs/2026-09-30-keymap-defaults-design.md#defaults)).

## Global Constraints

- Work only in `.worktrees/keymap-defaults` on branch `claude/keymap-defaults`. Run `git branch --show-current`
  before every commit. Never commit on `main`. End every commit message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew`, never a system `gradle`. Tests are headless. Do not launch the GUI (`:jasper-app:run`, `bench`).
- Source hygiene: no raw control, private-use or unpaired surrogate characters in Java source. Check with the Python
  snippet in `AGENTS.md`. Extract code from this plan with a Python script rather than retyping escapes.
- SDK is JDK-only. Every public SDK type and member has Javadoc (`-Xdoclint:all`), and new ones carry `@since 0.11.0`.
  SDK types appear in the app only inside `dev.jasper.app.plugins`. Plugins compile against the SDK only.
- All bundled plugins declare `sdk = ">=0.11.0, <0.12"` (spec, SDK 0.11.0).
- macOS principle (spec): "every default uses ⌘, optionally with ⇧, ⌥ or ⌃. Bare ⌃ and ⌥ keys belong to the shell …
  Never used: ⌘H, ⌥⌘H, ⌘Space, ⌥⌘D, ⌃⌘D, ⌃⌘Q, ⇧⌘/ (Help search), ⇧⌘3/4/5."
- Linux/Windows principle, Global and Terminal contexts (spec): "Ctrl+Shift+key, then Ctrl+Alt+Shift+key; also
  Ctrl+Tab, Ctrl+1–9 (no terminal encoding), Shift+PageUp/PageDown, F11 (full screen), Alt+Shift+arrows (resize
  pane) and Alt+1–3 (panel toggles …)". Editor and panel contexts use plain Ctrl, F3/Shift+F3, F5 and Delete.
- Specificity (spec): "Panel, Window, Content, File chooser, Find bar (equal, they never nest) › Terminal › Global."
  Two defaults in the same context, or in Global and Terminal (which overlap in a terminal pane), conflict.
- Built-ins carry two explicit default strings (macOS, other). The other default names `ctrl`, `shift` and `alt`
  literally; `cmd` in user bindings keeps its meaning (Ctrl+Shift outside macOS). Plugin `withDefaultBinding` gets
  the Alt rule outside macOS (`cmd+shift+x` → Ctrl+Alt+Shift+X); `withDefaultBindings(macOs, other)` gives a pair.
- `[keybindings]` in `config.toml` is unchanged: `action_id = "binding"`, one binding per action. No chords.

## Deviations from the spec (recorded here and in `docs/STATUS.md`)

1. The SDK type is `dev.jasper.sdk.ui.ShortcutContext`, not `ActionContext`. `ActionContext` has been the SDK's
   action-invocation interface (window and pane) since SDK 0.1. The spec text is updated with this plan.
2. Find, Find Next, Find Previous, Copy and Paste stay **Global** in K1. They still route to the Editor's hosted
   content commands, and a Terminal context would lose that routing on Linux. K3 makes them Terminal when the
   Editor's commands become content actions.
3. Hiding content, panel and window actions from the palette's Commands scope while their context lacks focus is
   left for K3, where the first such actions arrive. The K1 SDK Javadoc does not promise it.
4. Testkit: `FakePluginHost.actionSpec(id)` records each action's context and defaults, and the fake and the
   contract suite check context ids. Invoking an action "from a context" and the fake's `DEFAULT_DROPPED` are left
   for K3/K4, when bundled plugins first declare owned contexts.
5. User bindings for contributed actions are still collision-checked as Global when the configuration loads,
   because contexts are unknown until the plugin registers. The Keymap's "overrides ⟨action⟩ here" note is left for
   K3, which brings the first same-stroke pairs.
6. Menu items keep their accelerators. As today, the accelerator of a Terminal action's menu item can still fire while
   focus is outside a terminal pane.
7. The general docs sweep (`configuration.md`, `keyboard-shortcuts.md`, `README.md`, `command-palette.md`, Editor,
   Remote and Vault docs) stays in K4, as the spec says. K1 updates the SDK docs, `app-maintenance.md` and STATUS.
8. `select_tab_9` keeps its id and behaviour. K2 turns it into Select Last Tab; its ⌘9 / Ctrl+9 default is already
   the approved one.

## File structure

| File | Responsibility |
| --- | --- |
| `jasper-app/.../commands/KeyContext.java` (create) | Where a shortcut applies; specificity level; conflict rule. |
| `jasper-app/.../commands/ActionId.java` (modify) | Each built-in: id, label, macOS default, Linux/Windows default, context. |
| `jasper-app/.../config/KeyBindings.java` (modify) | Several ids per stroke; contexts; `resolve`; `Extension` with other default, context and the Alt rule. |
| `jasper-app/.../config/ConfigTemplate.java` (modify) | Linux header text for literal defaults. |
| `jasper-sdk/.../ui/ShortcutContext.java` (create) | SDK value: Global, Terminal, Content, Panel(id), Window(id). |
| `jasper-sdk/.../ui/ActionSpec.java` (modify) | `otherDefaultBinding`, `context`, `withDefaultBindings`, `withContext`. |
| `jasper-sdk/.../JasperSdk.java` (modify) | `VERSION = "0.11.0"`. |
| `jasper-sdk-testkit/.../FakeUi.java`, `FakePluginHost.java` (modify) | Context id validation; `actionSpec(id)`. |
| `jasper-sdk-testkit/src/testFixtures/.../PluginContractTest.java` (modify) | Contract: context ids belong to the plugin. |
| `jasper-app/.../contributions/ActionEntry.java`, `Contributions.java`, `package-info.java` (modify) | Carry other default and context; `extension()`. |
| `jasper-app/.../plugins/HostedUi.java`, `package-info.java` (modify) | Validate and map `ShortcutContext`; pass the pair. |
| `jasper-app/.../workspace/WindowContent.java` (modify) | Focus-aware dispatch, pass-through, root bindings that consume only used keys. |
| `jasper-app/.../workspace/WindowContributions.java` (modify) | `enabled(id)`, `panelContains(panelId, focus)`, `extension()`. |
| `jasper-app/.../palette/PaletteKeyRouter.java` (modify) | Side-effect-free `claimsShortcut`. |
| `jasper-app/.../shortcuthelp/ShortcutEntry.java`, `ShortcutCatalog.java`, `ShortcutTree.java`, `ShortcutText.java`, `KeymapPage.java` (modify) | Where column. |
| `jasper-app/.../config/ConfigService.java`, `application/JasperApplication.java` (modify) | Use `extension()` and the platform default. |
| `plugins/remote/...`, `plugins/vault/...` (modify) | Approved defaults and contexts; versions 0.9.0 / 0.4.0. |
| `plugins/*/src/main/resources/plugin.toml` (modify) | `sdk = ">=0.11.0, <0.12"`. |
| Docs: `jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md` | SDK 0.11 and the moved defaults. |

`...` stands for `src/main/java/dev/jasper/<module package>` (for example `jasper-app/src/main/java/dev/jasper/app`).

---

### Task 1: Contexts and per-platform defaults in the Keymap engine

No default changes in this task: each built-in gets an explicit Linux/Windows string equal to today's effective
stroke, so every existing test still passes.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/commands/KeyContext.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/KeyBindings.java` (whole file)
- Test: `jasper-app/src/test/java/dev/jasper/app/commands/KeyContextTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsContextTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsTest.java:34` and `:58-69`

**Interfaces:**
- Produces: `KeyContext(Kind kind, String owner)` with `Kind {GLOBAL, TERMINAL, CONTENT, PANEL, WINDOW}`, constants
  `KeyContext.GLOBAL`, `KeyContext.TERMINAL`, factories `content(String pluginId)`, `panel(String panelId)`,
  `window(String windowId)`, `int level()`, `boolean conflictsWith(KeyContext)`.
- Produces: `ActionId.defaultBinding(boolean macOs)` (`"none"` when unbound), `ActionId.context()`. The no-argument
  `ActionId.defaultBinding()` is removed.
- Produces: `KeyBindings.Extension(String id, Optional<String> defaultBinding, Optional<String> otherDefault,
  KeyContext context)`, the old two-argument constructor (Global, no other default), and
  `Optional<String> defaultFor(boolean macOs)`.
- Produces: `KeyBindings.resolve(KeyStroke, Predicate<KeyContext>) : Optional<String>`,
  `KeyBindings.idsFor(KeyStroke) : List<String>`, `KeyBindings.contextFor(String id) : KeyContext`.
  `idFor(KeyStroke)` now returns only a Global or Terminal id.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/commands/KeyContextTest.java`:

```java
package dev.jasper.app.commands;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyContextTest {
    @Test void ownedContextsNameTheirOwnerAndTheOthersDoNot() {
        assertThat(KeyContext.content("dev.x").owner()).isEqualTo("dev.x");
        assertThat(KeyContext.panel("dev.x.hosts")).isEqualTo(new KeyContext(KeyContext.Kind.PANEL, "dev.x.hosts"));
        assertThatIllegalArgumentException().isThrownBy(() -> KeyContext.window(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new KeyContext(KeyContext.Kind.GLOBAL, "dev.x"));
        assertThatNullPointerException().isThrownBy(() -> KeyContext.panel(null));
    }

    @Test void specificityAndConflicts() {
        assertThat(KeyContext.GLOBAL.level()).isZero();
        assertThat(KeyContext.TERMINAL.level()).isEqualTo(1);
        assertThat(KeyContext.content("dev.x").level()).isEqualTo(2);
        assertThat(KeyContext.GLOBAL.conflictsWith(KeyContext.TERMINAL)).as("both apply in a terminal pane").isTrue();
        assertThat(KeyContext.panel("dev.x.a").conflictsWith(KeyContext.panel("dev.x.a"))).isTrue();
        assertThat(KeyContext.panel("dev.x.a").conflictsWith(KeyContext.panel("dev.x.b"))).isFalse();
        assertThat(KeyContext.content("dev.x").conflictsWith(KeyContext.GLOBAL)).as("a panel or tab may override").isFalse();
        assertThat(KeyContext.TERMINAL.conflictsWith(KeyContext.window("dev.x.w"))).isFalse();
    }
}
```

`jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsContextTest.java`:

```java
package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyBindingsContextTest {
    private static KeyStroke mac(String binding) { return KeyBindings.parse(binding, true).orElseThrow(); }

    private static KeyBindings.Extension extension(String id, String binding, KeyContext context) {
        return new KeyBindings.Extension(id, Optional.of(binding), Optional.empty(), context);
    }

    @Test void builtInsCarryTheirContexts() {
        assertThat(ActionId.CLEAR_SCROLLBACK.context()).isEqualTo(KeyContext.TERMINAL);
        assertThat(ActionId.PREVIOUS_PROMPT.context()).isEqualTo(KeyContext.TERMINAL);
        assertThat(ActionId.NEW_TAB.context()).isEqualTo(KeyContext.GLOBAL);
        KeyBindings bindings = KeyBindings.defaults(true);
        assertThat(bindings.contextFor("next_prompt")).isEqualTo(KeyContext.TERMINAL);
        assertThat(bindings.contextFor("dev.unknown.action")).isEqualTo(KeyContext.GLOBAL);
    }

    @Test void ownedContextsMayReuseAGlobalStrokeAndTheMostSpecificApplicableOneWins() {
        KeyBindings.Resolved resolved = KeyBindings.defaults(true).withExtensions(List.of(
            extension("dev.x.hosts.new", "cmd+n", KeyContext.panel("dev.x.hosts")),
            extension("dev.x.save", "cmd+n", KeyContext.content("dev.x"))));
        assertThat(resolved.problems()).isEmpty();
        KeyBindings bindings = resolved.bindings();
        assertThat(bindings.idsFor(mac("cmd+n"))).containsExactly("new_window", "dev.x.hosts.new", "dev.x.save");
        assertThat(bindings.idFor(mac("cmd+n"))).as("the Global or Terminal holder").hasValue("new_window");
        assertThat(bindings.resolve(mac("cmd+n"), context -> context.level() == 0)).hasValue("new_window");
        assertThat(bindings.resolve(mac("cmd+n"), context -> context.level() == 0
            || context.equals(KeyContext.panel("dev.x.hosts")))).hasValue("dev.x.hosts.new");
        assertThat(bindings.resolve(mac("cmd+n"), context -> context.kind() != KeyContext.Kind.PANEL)).hasValue("dev.x.save");
        assertThat(bindings.resolve(mac("cmd+n"), context -> false)).isEmpty();
        assertThat(bindings.resolve(mac("cmd+alt+shift+f9"), context -> true)).isEmpty();
        assertThat(bindings.contextFor("dev.x.save")).isEqualTo(KeyContext.content("dev.x"));
    }

    @Test void conflictingContextsDropALaterPluginDefault() {
        KeyBindings.Resolved resolved = KeyBindings.defaults(true).withExtensions(List.of(
            extension("dev.x.term", "cmd+up", KeyContext.TERMINAL),
            extension("dev.x.global", "cmd+down", KeyContext.GLOBAL),
            extension("dev.a.edit", "cmd+alt+j", KeyContext.content("dev.a")),
            extension("dev.b.edit", "cmd+alt+j", KeyContext.content("dev.b")),
            extension("dev.b.again", "cmd+alt+j", KeyContext.content("dev.b"))));
        assertThat(resolved.problems()).extracting(KeyBindings.Problem::actionId)
            .containsExactly("dev.x.term", "dev.x.global", "dev.b.again");
        assertThat(resolved.problems().get(0).message()).contains("previous_prompt");
        assertThat(resolved.problems().get(1).message()).as("Global and Terminal overlap").contains("next_prompt");
        assertThat(resolved.problems().get(2).message()).contains("dev.b.edit");
        assertThat(resolved.bindings().idsFor(mac("cmd+alt+j"))).containsExactly("dev.a.edit", "dev.b.edit");
    }

    @Test void pluginDefaultsGetTheAltRuleOrTheirExplicitOtherDefault() {
        var shifted = new KeyBindings.Extension("dev.x.a", Optional.of("cmd+shift+o"));
        var paired = new KeyBindings.Extension("dev.x.b", Optional.of("cmd+alt+1"), Optional.of("alt+1"), KeyContext.GLOBAL);
        var macOnly = new KeyBindings.Extension("dev.x.c", Optional.of("cmd+m"), Optional.of("none"), KeyContext.GLOBAL);
        var plain = new KeyBindings.Extension("dev.x.d", Optional.of("cmd+j"));
        assertThat(shifted.defaultFor(true)).hasValue("cmd+shift+o");
        assertThat(shifted.defaultFor(false)).hasValue("alt+cmd+shift+o");
        assertThat(paired.defaultFor(false)).hasValue("alt+1");
        assertThat(plain.defaultFor(false)).as("no shift, no alt").hasValue("cmd+j");
        assertThat(new KeyBindings.Extension("dev.x.e", Optional.empty()).defaultFor(false)).isEmpty();

        KeyBindings.Resolved linux = KeyBindings.defaults(false).withExtensions(List.of(shifted, paired, macOnly, plain));
        assertThat(linux.problems()).isEmpty();
        assertThat(linux.bindings().strokeFor("dev.x.a")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_O,
            InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(linux.bindings().strokeFor("dev.x.b")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK));
        assertThat(linux.bindings().strokeFor("dev.x.c")).as("explicitly none off macOS").isEmpty();
        assertThat(linux.bindings().strokeFor("dev.x.d")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_J,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        KeyBindings.Resolved macOs = KeyBindings.defaults(true).withExtensions(List.of(shifted, paired, macOnly, plain));
        assertThat(macOs.bindings().strokeFor("dev.x.c")).contains(mac("cmd+m"));
        assertThat(macOs.bindings().strokeFor("dev.x.b")).contains(mac("cmd+alt+1"));
    }

    @Test void everyBuiltInHasAnExplicitDefaultForEachPlatform() {
        for (ActionId action : ActionId.values()) for (boolean mac : new boolean[]{true, false}) {
            String binding = action.defaultBinding(mac);
            assertThat(binding).as("%s on %s", action, mac).isNotBlank();
            if (!mac) assertThat(binding).as("%s names its modifiers literally off macOS", action).doesNotContain("cmd");
        }
    }
}
```

In `KeyBindingsTest.java`, replace line 34

```java
        assertThat(ActionId.SPLIT_RIGHT.defaultBinding()).isEqualTo("cmd+d");
```

with

```java
        assertThat(ActionId.SPLIT_RIGHT.defaultBinding(true)).isEqualTo("cmd+d");
        assertThat(ActionId.SPLIT_RIGHT.defaultBinding(false)).isEqualTo("ctrl+shift+d");
```

and replace the body of `everyPlatformDefaultHasOneUniqueActiveStrokePerAction` (lines 58-69) with

```java
    @Test
    void everyPlatformDefaultHasOneUniqueActiveStrokePerAction() {
        for (boolean macOs : new boolean[]{true, false}) {
            KeyBindings bindings = KeyBindings.defaults(macOs);
            HashSet<KeyStroke> strokes = new HashSet<>();
            for (ActionId action : ActionId.values()) {
                bindings.strokeFor(action).ifPresent(stroke -> {
                    assertThat(strokes.add(stroke)).as("unique %s binding for %s", macOs, action).isTrue();
                    assertThat(bindings.actionFor(stroke)).contains(action);
                });
            }
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.commands.KeyContextTest' --tests 'dev.jasper.app.config.KeyBindings*'`
Expected: compilation fails (`KeyContext` and `ActionId.context()` don't exist, `defaultBinding(boolean)` is the
only remaining form, no four-argument `Extension`).

- [ ] **Step 3: Write `KeyContext`**

`jasper-app/src/main/java/dev/jasper/app/commands/KeyContext.java`:

```java
package dev.jasper.app.commands;

import java.util.Objects;

/**
 * Where an action's shortcut applies. {@link #GLOBAL} applies anywhere in a terminal window and {@link #TERMINAL} in
 * a terminal pane, its find bar included; the owned kinds apply inside one plugin's content tabs, one panel or one
 * plugin window, which never nest. A stroke may belong to several actions; the most specific context containing the
 * focus owner wins.
 *
 * @param kind the kind of place
 * @param owner the plugin id for {@link Kind#CONTENT}, the panel or window id for {@link Kind#PANEL} and
 *              {@link Kind#WINDOW}, empty for the others
 */
public record KeyContext(Kind kind, String owner) {
    public enum Kind { GLOBAL, TERMINAL, CONTENT, PANEL, WINDOW }

    public static final KeyContext GLOBAL = new KeyContext(Kind.GLOBAL, "");
    public static final KeyContext TERMINAL = new KeyContext(Kind.TERMINAL, "");

    public KeyContext {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        boolean owned = kind != Kind.GLOBAL && kind != Kind.TERMINAL;
        if (owned && owner.isBlank()) throw new IllegalArgumentException(kind + " context needs an owner");
        if (!owned && !owner.isEmpty()) throw new IllegalArgumentException(kind + " context has no owner");
    }

    /** The content tabs one plugin opened. */
    public static KeyContext content(String pluginId) { return new KeyContext(Kind.CONTENT, pluginId); }

    /** Inside one panel, in whichever window shows it. */
    public static KeyContext panel(String panelId) { return new KeyContext(Kind.PANEL, panelId); }

    /** Inside one plugin window. */
    public static KeyContext window(String windowId) { return new KeyContext(Kind.WINDOW, windowId); }

    /** Specificity: 0 for Global, 1 for Terminal, 2 for the owned kinds. */
    public int level() {
        return switch (kind) {
            case GLOBAL -> 0;
            case TERMINAL -> 1;
            case CONTENT, PANEL, WINDOW -> 2;
        };
    }

    /**
     * Whether two actions on one stroke would be ambiguous: the same context, or Global and Terminal, which both apply
     * in a terminal pane. An owned context may reuse a Global or Terminal stroke; it overrides it there.
     */
    public boolean conflictsWith(KeyContext other) {
        return equals(other) || level() <= 1 && other.level() <= 1;
    }
}
```

- [ ] **Step 4: Rewrite `ActionId` with explicit per-platform defaults (today's strokes) and contexts**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java` with:

```java
package dev.jasper.app.commands;

import java.util.Optional;

/**
 * The built-in application action catalog; plugins contribute further actions by namespaced id. Each action has an
 * explicit default for macOS and one for Linux and Windows, both in the configuration syntax ("none" for no default;
 * the second names ctrl, alt and shift literally), and a {@link KeyContext}.
 */
public enum ActionId {
    NEW_TAB("new_tab", "New Tab", "cmd+t", "ctrl+shift+t"),
    CLOSE_TAB("close_tab", "Close Tab", "cmd+w", "ctrl+shift+w"),
    NEW_WINDOW("new_window", "New Window", "cmd+n", "ctrl+shift+n"),
    SPLIT_RIGHT("split_right", "Split Right", "cmd+d", "ctrl+shift+d"),
    SPLIT_DOWN("split_down", "Split Down", "cmd+shift+d", "ctrl+alt+shift+d"),
    CLOSE_PANE("close_pane", "Close Pane", "cmd+shift+w", "ctrl+alt+shift+w"),
    ZOOM_PANE("zoom_pane", "Zoom Pane", "cmd+shift+enter", "ctrl+alt+shift+enter"),
    FOCUS_PANE_LEFT("focus_pane_left", "Focus Pane Left", "cmd+alt+left", "ctrl+alt+shift+left"),
    FOCUS_PANE_RIGHT("focus_pane_right", "Focus Pane Right", "cmd+alt+right", "ctrl+alt+shift+right"),
    FOCUS_PANE_UP("focus_pane_up", "Focus Pane Up", "cmd+alt+up", "ctrl+alt+shift+up"),
    FOCUS_PANE_DOWN("focus_pane_down", "Focus Pane Down", "cmd+alt+down", "ctrl+alt+shift+down"),
    NEXT_TAB("next_tab", "Next Tab", "cmd+shift+]", "ctrl+shift+]"),
    PREVIOUS_TAB("previous_tab", "Previous Tab", "cmd+shift+[", "ctrl+shift+["),
    SELECT_TAB_1("select_tab_1", "Select Tab 1", "cmd+1", "ctrl+1"),
    SELECT_TAB_2("select_tab_2", "Select Tab 2", "cmd+2", "ctrl+2"),
    SELECT_TAB_3("select_tab_3", "Select Tab 3", "cmd+3", "ctrl+3"),
    SELECT_TAB_4("select_tab_4", "Select Tab 4", "cmd+4", "ctrl+4"),
    SELECT_TAB_5("select_tab_5", "Select Tab 5", "cmd+5", "ctrl+5"),
    SELECT_TAB_6("select_tab_6", "Select Tab 6", "cmd+6", "ctrl+6"),
    SELECT_TAB_7("select_tab_7", "Select Tab 7", "cmd+7", "ctrl+7"),
    SELECT_TAB_8("select_tab_8", "Select Tab 8", "cmd+8", "ctrl+8"),
    SELECT_TAB_9("select_tab_9", "Select Tab 9", "cmd+9", "ctrl+9"),
    RENAME_TAB("rename_tab", "Rename Tab", "f2", "f2"),
    FIND("find", "Find", "cmd+f", "ctrl+shift+f"),
    FIND_NEXT("find_next", "Find Next", "cmd+g", "ctrl+shift+g"),
    FIND_PREVIOUS("find_previous", "Find Previous", "cmd+shift+g", "ctrl+alt+shift+g"),
    PREVIOUS_PROMPT("previous_prompt", "Previous Prompt", "cmd+up", "ctrl+shift+up", KeyContext.TERMINAL),
    NEXT_PROMPT("next_prompt", "Next Prompt", "cmd+down", "ctrl+shift+down", KeyContext.TERMINAL),
    COPY("copy", "Copy", "cmd+c", "ctrl+shift+c"),
    PASTE("paste", "Paste", "cmd+v", "ctrl+shift+v"),
    COMMAND_PALETTE("command_palette", "Command Palette", "cmd+k", "ctrl+k"),
    CLEAR_SCROLLBACK("clear_scrollback", "Clear Scrollback", "cmd+shift+k", "ctrl+shift+k", KeyContext.TERMINAL),
    FONT_BIGGER("font_bigger", "Increase Font Size", "cmd+=", "ctrl+shift+="),
    FONT_SMALLER("font_smaller", "Decrease Font Size", "cmd+-", "ctrl+shift+-"),
    FONT_RESET("font_reset", "Reset Font Size", "cmd+0", "ctrl+shift+0"),
    OPEN_SETTINGS("open_settings", "Settings", "cmd+,", "ctrl+shift+,"),
    RELOAD_CONFIG("reload_config", "Reload Config", "cmd+shift+r", "ctrl+alt+shift+r"),
    QUIT("quit", "Quit", "cmd+q", "ctrl+shift+q");

    private final String id;
    private final String label;
    private final String macDefault;
    private final String otherDefault;
    private final KeyContext context;

    ActionId(String id, String label, String macDefault, String otherDefault) {
        this(id, label, macDefault, otherDefault, KeyContext.GLOBAL);
    }

    ActionId(String id, String label, String macDefault, String otherDefault, KeyContext context) {
        this.id = id;
        this.label = label;
        this.macDefault = macDefault;
        this.otherDefault = otherDefault;
        this.context = context;
    }

    public String id() {
        return id;
    }

    /** The built-in action with this configuration id, if any; contributed actions are not in this catalog. */
    public static Optional<ActionId> forId(String id) {
        for (ActionId action : values()) if (action.id.equals(id)) return Optional.of(action);
        return Optional.empty();
    }

    public String label() {
        return label;
    }

    /** The default shortcut on this platform as written; "none" when the action has none there. */
    public String defaultBinding(boolean macOs) {
        return macOs ? macDefault : otherDefault;
    }

    /** Where the action's shortcut applies. */
    public KeyContext context() {
        return context;
    }
}
```

- [ ] **Step 5: Rewrite `KeyBindings`**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/config/KeyBindings.java` with:

```java
package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Parses and indexes application keybindings by action id, independently of the Swing action layer.
 * Built-in ids come from {@link ActionId}; namespaced (dotted) ids belong to contributed actions.
 * A user's binding for a dotted id is validated when the configuration loads but bound only once
 * {@link #withExtensions} learns that the action exists.
 * <p>Every action has a {@link KeyContext}. A stroke may belong to several actions whose contexts don't conflict;
 * {@link #resolve} picks the most specific one that applies where focus is.
 */
public final class KeyBindings {
    /**
     * A contributed action, the shortcuts its contributor would like and where they apply.
     *
     * @param defaultBinding the default as written, used as is on macOS
     * @param otherDefault the Linux and Windows default when the contributor named one ("none" for no default there);
     *                     empty means it follows from {@code defaultBinding}
     * @param context where the shortcut applies
     */
    public record Extension(String id, Optional<String> defaultBinding, Optional<String> otherDefault, KeyContext context) {
        public Extension {
            if (!extensionId(id)) throw new IllegalArgumentException("not a contributed action id: '" + id + "'");
            Objects.requireNonNull(defaultBinding, "defaultBinding");
            Objects.requireNonNull(otherDefault, "otherDefault");
            Objects.requireNonNull(context, "context");
        }

        /** A Global action with one default for every platform. */
        public Extension(String id, Optional<String> defaultBinding) {
            this(id, defaultBinding, Optional.empty(), KeyContext.GLOBAL);
        }

        /**
         * The default on this platform. macOS takes the default as written. Elsewhere an explicit other default wins;
         * without one, cmd means Ctrl+Shift and a default naming cmd and shift gains alt, as built-in defaults always
         * did, so {@code cmd+shift+x} does not land on the same keys as {@code cmd+x}.
         */
        public Optional<String> defaultFor(boolean macOs) {
            if (macOs) return defaultBinding;
            if (otherDefault.isPresent()) return otherDefault;
            return defaultBinding.map(KeyBindings::withAltRule);
        }
    }

    /** Something {@link #withExtensions} could not honor. */
    public record Problem(Kind kind, String actionId, String message) {
        public enum Kind {
            /** The user bound an id that no registered action has: a configuration warning. */
            UNKNOWN_ACTION,
            /** A contributor's default lost to an existing binding or was invalid: logged, not a warning. */
            DEFAULT_DROPPED
        }
    }

    /** Effective bindings and what was dropped on the way. */
    public record Resolved(KeyBindings bindings, List<Problem> problems) {
        public Resolved { problems = List.copyOf(problems); }
    }

    private static final Map<String, Integer> NAMED_KEYS = namedKeys();
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    private final boolean macOs;
    private final Map<String, KeyStroke> strokesById;
    private final Map<String, KeyContext> contexts;
    private final Map<KeyStroke, List<String>> idsByStroke;
    private final Map<String, Optional<KeyStroke>> extensionOverrides;

    private KeyBindings(boolean macOs, Map<String, KeyStroke> strokesById, Map<String, KeyContext> contexts,
                        Map<String, Optional<KeyStroke>> extensionOverrides) {
        this.macOs = macOs;
        this.strokesById = Collections.unmodifiableMap(new LinkedHashMap<>(strokesById));
        this.contexts = Map.copyOf(contexts);
        Map<KeyStroke, List<String>> reverse = new HashMap<>();
        strokesById.forEach((id, stroke) -> reverse.computeIfAbsent(stroke, ignored -> new ArrayList<>()).add(id));
        reverse.replaceAll((stroke, ids) -> List.copyOf(ids));
        idsByStroke = Map.copyOf(reverse);
        this.extensionOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(extensionOverrides));
    }

    /** Whether the id is well formed and namespaced with at least one dot, which no built-in id is. */
    public static boolean extensionId(String id) {
        return id != null && id.length() <= 128 && ID.matcher(id).matches();
    }

    public static KeyBindings defaults(boolean macOs) {
        Map<String, KeyStroke> strokes = new LinkedHashMap<>();
        for (ActionId action : ActionId.values()) {
            String binding = action.defaultBinding(macOs);
            Optional<KeyStroke> stroke = parse(binding, macOs);
            if (stroke.isPresent()) putWithoutConflict(strokes, Map.of(), action.id(), action.context(), stroke.get(), binding);
        }
        return new KeyBindings(macOs, strokes, Map.of(), Map.of());
    }

    /** A built-in's default for this platform as written; "none" when it has none there. */
    public static String effectiveDefaultBinding(ActionId action, boolean macOs) {
        return action.defaultBinding(macOs);
    }

    public static KeyBindings withOverrides(boolean macOs, Map<String, String> overrides) {
        if (overrides == null) {
            throw new IllegalArgumentException("keybinding overrides must not be null");
        }
        Map<String, KeyStroke> result = new LinkedHashMap<>(defaults(macOs).strokesById);
        Map<String, String> builtIn = new LinkedHashMap<>();
        Map<String, String> extensions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String id = entry.getKey();
            if (ActionId.forId(id).isPresent()) builtIn.put(id, entry.getValue());
            else if (extensionId(id)) extensions.put(id, entry.getValue());
            else throw new IllegalArgumentException("unknown action name: '" + id + "'");
        }
        builtIn.keySet().forEach(result::remove);
        for (Map.Entry<String, String> entry : builtIn.entrySet()) {
            parseFor(entry.getKey(), entry.getValue(), macOs).ifPresent(stroke -> putWithoutConflict(result, Map.of(),
                entry.getKey(), contextOf(entry.getKey(), Map.of()), stroke, entry.getValue()));
        }
        // A contributed action's shortcut must not collide with anything the user or the defaults claim, but it is
        // not bound here: the action may not exist in this launch. Its context is unknown until it registers, so the
        // check treats it as Global.
        Map<String, KeyStroke> claimed = new LinkedHashMap<>(result);
        Map<String, Optional<KeyStroke>> extensionOverrides = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            Optional<KeyStroke> parsed = parseFor(entry.getKey(), entry.getValue(), macOs);
            parsed.ifPresent(stroke -> putWithoutConflict(claimed, Map.of(), entry.getKey(), KeyContext.GLOBAL, stroke, entry.getValue()));
            extensionOverrides.put(entry.getKey(), parsed);
        }
        return new KeyBindings(macOs, result, Map.of(), extensionOverrides);
    }

    /**
     * Adds contributed actions in registration order. The user's binding for an action wins; otherwise its default
     * for this platform applies unless it is invalid or an existing binding holds the stroke in a conflicting context.
     * Always call this on the result of {@link #withOverrides} or {@link #defaults}, never on an already extended
     * instance.
     */
    public Resolved withExtensions(List<Extension> extensions) {
        Map<String, KeyStroke> strokes = new LinkedHashMap<>();
        strokesById.forEach((id, stroke) -> { if (!extensionId(id)) strokes.put(id, stroke); });
        Map<String, KeyContext> registered = new LinkedHashMap<>();
        for (Extension extension : extensions) registered.putIfAbsent(extension.id(), extension.context());
        List<Problem> problems = new ArrayList<>();
        for (Map.Entry<String, Optional<KeyStroke>> override : extensionOverrides.entrySet()) {
            if (!registered.containsKey(override.getKey())) {
                problems.add(new Problem(Problem.Kind.UNKNOWN_ACTION, override.getKey(), "Unknown action; ignored."));
                continue;
            }
            override.getValue().ifPresent(stroke -> strokes.put(override.getKey(), stroke));
        }
        for (Extension extension : extensions) {
            if (extensionOverrides.containsKey(extension.id())) continue;
            Optional<String> wanted = extension.defaultFor(macOs);
            if (wanted.isEmpty()) continue;
            String binding = wanted.get();
            Optional<KeyStroke> parsed;
            try { parsed = parse(binding, macOs); }
            catch (IllegalArgumentException invalid) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(),
                    "Default shortcut '" + binding + "' is not valid; the action is unbound."));
                continue;
            }
            if (parsed.isEmpty()) continue;
            String holder = conflicting(strokes, registered, extension.context(), parsed.get());
            if (holder != null) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(), "Default shortcut '" + binding
                    + "' is already used by '" + holder + "'; bind the action under [keybindings] to give it one."));
                continue;
            }
            strokes.put(extension.id(), parsed.get());
        }
        return new Resolved(new KeyBindings(macOs, strokes, registered, extensionOverrides), problems);
    }

    /** The Global or Terminal action bound to a stroke, if any; actions in owned contexts are found with {@link #resolve}. */
    public Optional<String> idFor(KeyStroke stroke) {
        for (String id : idsFor(stroke)) if (contextFor(id).level() <= 1) return Optional.of(id);
        return Optional.empty();
    }

    /** Every action bound to a stroke: built-ins first, then contributed actions in registration order. */
    public List<String> idsFor(KeyStroke stroke) {
        return idsByStroke.getOrDefault(stroke, List.of());
    }

    /**
     * The action a stroke runs where focus is: of the actions bound to it whose context applies, the most specific
     * (an owned context, then Terminal, then Global). Empty when none applies.
     */
    public Optional<String> resolve(KeyStroke stroke, Predicate<KeyContext> applies) {
        String best = null;
        int level = -1;
        for (String id : idsFor(stroke)) {
            KeyContext context = contextFor(id);
            if (context.level() > level && applies.test(context)) { best = id; level = context.level(); }
        }
        return Optional.ofNullable(best);
    }

    /** Where an id's shortcut applies: a built-in's own context, a registered contributed action's, otherwise Global. */
    public KeyContext contextFor(String id) {
        return contextOf(id, contexts);
    }

    public Optional<KeyStroke> strokeFor(String id) {
        return Optional.ofNullable(strokesById.get(id));
    }

    /** Every bound id with its shortcut, built-ins first, in a stable order. */
    public Map<String, KeyStroke> strokes() { return strokesById; }

    public Optional<ActionId> actionFor(KeyStroke stroke) {
        return idFor(stroke).flatMap(ActionId::forId);
    }

    public Optional<KeyStroke> strokeFor(ActionId action) {
        return strokeFor(action.id());
    }

    private static Optional<KeyStroke> parseFor(String id, String binding, boolean macOs) {
        try {
            return parse(binding, macOs);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "invalid keybinding for action '" + id + "': '" + binding + "' (" + exception.getMessage() + ")", exception);
        }
    }

    /** Serializes physical keys without cmd's non-macOS modifier translation. */
    public static String format(KeyStroke stroke, boolean macOs) {
        Objects.requireNonNull(stroke, "stroke");
        var text = new StringBuilder();
        int mods = stroke.getModifiers();
        if ((mods & InputEvent.CTRL_DOWN_MASK) != 0) text.append("ctrl+");
        if ((mods & InputEvent.ALT_DOWN_MASK) != 0) text.append("alt+");
        if ((mods & InputEvent.SHIFT_DOWN_MASK) != 0) text.append("shift+");
        if ((mods & InputEvent.META_DOWN_MASK) != 0) text.append(macOs ? "cmd+" : "meta+");
        if ((mods & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) text.append("altgraph+");
        String key = NAMED_KEYS.entrySet().stream().filter(e -> e.getValue() == stroke.getKeyCode())
            .map(Map.Entry::getKey).sorted().findFirst().orElseGet(() -> KeyEvent.getKeyText(stroke.getKeyCode()).toLowerCase(Locale.ROOT));
        String binding = text.append(key).toString();
        if (!parse(binding, macOs).filter(stroke::equals).isPresent())
            throw new IllegalArgumentException("This key combination cannot be saved. Try another shortcut.");
        return binding;
    }

    /** Parses a saved binding; none means explicitly unassigned. */
    public static Optional<KeyStroke> parse(String binding, boolean macOs) {
        if (binding == null) {
            throw new IllegalArgumentException("keybinding must not be null");
        }
        String normalized = binding.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("none")) {
            return Optional.empty();
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("keybinding must not be blank");
        }

        int modifiers = 0;
        Integer keyCode = null;
        for (String rawToken : normalized.split("\\+", -1)) {
            String token = rawToken.trim();
            switch (token) {
                case "cmd" -> modifiers |= macOs
                    ? InputEvent.META_DOWN_MASK
                    : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
                case "meta" -> modifiers |= InputEvent.META_DOWN_MASK;
                case "altgraph" -> modifiers |= InputEvent.ALT_GRAPH_DOWN_MASK;
                case "ctrl" -> modifiers |= InputEvent.CTRL_DOWN_MASK;
                case "alt" -> modifiers |= InputEvent.ALT_DOWN_MASK;
                case "shift" -> modifiers |= InputEvent.SHIFT_DOWN_MASK;
                default -> {
                    if (token.equals("{") || token.equals("}")) {
                        modifiers |= InputEvent.SHIFT_DOWN_MASK;
                        token = token.equals("{") ? "[" : "]";
                    }
                    int parsedKey = keyCode(token);
                    if (keyCode != null) {
                        throw new IllegalArgumentException("keybinding has more than one key: " + binding);
                    }
                    keyCode = parsedKey;
                }
            }
        }
        if (keyCode == null) {
            throw new IllegalArgumentException("keybinding has no key: " + binding);
        }
        return Optional.of(KeyStroke.getKeyStroke(keyCode, modifiers));
    }

    /** The non-macOS rule for a contributed cmd default: one naming cmd and shift gains alt. */
    static String withAltRule(String binding) {
        List<String> tokens = Arrays.stream(binding.trim().toLowerCase(Locale.ROOT).split("\\+", -1)).map(String::trim).toList();
        return tokens.contains("cmd") && tokens.contains("shift") && !tokens.contains("alt") ? "alt+" + binding : binding;
    }

    private static KeyContext contextOf(String id, Map<String, KeyContext> contexts) {
        return ActionId.forId(id).map(ActionId::context).orElseGet(() -> contexts.getOrDefault(id, KeyContext.GLOBAL));
    }

    /** The first action holding the stroke in a context that would make it ambiguous with this one, or null. */
    private static String conflicting(Map<String, KeyStroke> strokes, Map<String, KeyContext> contexts,
                                      KeyContext context, KeyStroke stroke) {
        for (Map.Entry<String, KeyStroke> existing : strokes.entrySet())
            if (existing.getValue().equals(stroke) && contextOf(existing.getKey(), contexts).conflictsWith(context))
                return existing.getKey();
        return null;
    }

    private static void putWithoutConflict(Map<String, KeyStroke> strokes, Map<String, KeyContext> contexts, String id,
                                           KeyContext context, KeyStroke stroke, String binding) {
        String holder = conflicting(strokes, contexts, context, stroke);
        if (holder != null) {
            throw new IllegalArgumentException(
                "keybinding collision for '" + binding + "': actions '" + holder + "' and '" + id + "'");
        }
        strokes.put(id, stroke);
    }

    private static int keyCode(String token) {
        Integer named = NAMED_KEYS.get(token);
        if (named != null) {
            return named;
        }
        if (token.length() == 1) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(token.charAt(0)));
            if (code != KeyEvent.VK_UNDEFINED) {
                return code;
            }
        }
        if (token.length() >= 2 && token.charAt(0) == 'f') {
            try {
                int number = Integer.parseInt(token.substring(1));
                if (number >= 1 && number <= 24) {
                    return number <= 12 ? KeyEvent.VK_F1 + number - 1 : KeyEvent.VK_F13 + number - 13;
                }
            } catch (NumberFormatException ignored) {
                // Report the complete token below.
            }
        }
        throw new IllegalArgumentException("unknown key: '" + token + "'");
    }

    private static Map<String, Integer> namedKeys() {
        Map<String, Integer> keys = new HashMap<>();
        keys.put("left", KeyEvent.VK_LEFT);
        keys.put("right", KeyEvent.VK_RIGHT);
        keys.put("up", KeyEvent.VK_UP);
        keys.put("down", KeyEvent.VK_DOWN);
        keys.put("enter", KeyEvent.VK_ENTER);
        keys.put("return", KeyEvent.VK_ENTER);
        keys.put("tab", KeyEvent.VK_TAB);
        keys.put("space", KeyEvent.VK_SPACE);
        keys.put("escape", KeyEvent.VK_ESCAPE);
        keys.put("esc", KeyEvent.VK_ESCAPE);
        keys.put("backspace", KeyEvent.VK_BACK_SPACE);
        keys.put("delete", KeyEvent.VK_DELETE);
        keys.put("insert", KeyEvent.VK_INSERT);
        keys.put("home", KeyEvent.VK_HOME);
        keys.put("end", KeyEvent.VK_END);
        keys.put("pageup", KeyEvent.VK_PAGE_UP);
        keys.put("pagedown", KeyEvent.VK_PAGE_DOWN);
        keys.put("[", KeyEvent.VK_OPEN_BRACKET);
        keys.put("]", KeyEvent.VK_CLOSE_BRACKET);
        keys.put("=", KeyEvent.VK_EQUALS);
        keys.put("-", KeyEvent.VK_MINUS);
        keys.put(",", KeyEvent.VK_COMMA);
        keys.put(".", KeyEvent.VK_PERIOD);
        keys.put("/", KeyEvent.VK_SLASH);
        keys.put("\\", KeyEvent.VK_BACK_SLASH);
        keys.put(";", KeyEvent.VK_SEMICOLON);
        keys.put("'", KeyEvent.VK_QUOTE);
        keys.put("`", KeyEvent.VK_BACK_QUOTE);
        return Map.copyOf(keys);
    }
}
```

- [ ] **Step 6: Run the focused tests, then the app suite**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.commands.KeyContextTest' --tests 'dev.jasper.app.config.*'`
Expected: PASS.
Run: `./gradlew :jasper-app:test`
Expected: PASS (defaults are unchanged; every other caller compiles against the same signatures).

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/keymap-defaults
git add jasper-app/src/main/java/dev/jasper/app/commands/KeyContext.java jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java \
  jasper-app/src/main/java/dev/jasper/app/config/KeyBindings.java jasper-app/src/test/java/dev/jasper/app/commands/KeyContextTest.java \
  jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsContextTest.java jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsTest.java
git commit -m "Keymap: shortcut contexts, explicit per-platform defaults and focus resolution

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The approved defaults for built-in actions

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java` (the constants)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigTemplate.java:50-52`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsTest.java` (tab and brace tests)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsExtensionTest.java:54-67`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigTemplateTest.java:30-35`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/CommandPaletteShortcutsTest.java`, `PaletteKeyRouterTest.java`,
  `PaletteScopesTest.java`, `TabShortcutsTest.java`

**Interfaces:**
- Consumes: `ActionId.defaultBinding(boolean)`, `ActionId.context()`, `KeyBindings.parse`, `KeyBindings.defaults`
  (Task 1).
- Produces: the defaults in the table below. Later tasks rely on macOS ⌘W = `close_pane`, ⇧⌘P = `command_palette`,
  ⌘K = `clear_scrollback` (Terminal), Linux Ctrl+Tab / Ctrl+Shift+Tab = next/previous tab, and no Linux default on
  Ctrl+K or F2.

- [ ] **Step 1: Write the failing defaults test**

`jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java`:

```java
package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The approved defaults (spec 2026-09-30-keymap-defaults-design.md, Defaults) for the built-in actions plan K1
 * covers; the comment on each row is the spec's row number. Find, Find Next, Find Previous, Copy and Paste stay Global
 * until plan K3.
 */
class KeymapDefaultsTest {
    private record Row(String id, KeyContext context, String macOs, String other) { }

    private static final List<Row> ROWS = List.of(
        new Row("new_window", KeyContext.GLOBAL, "cmd+n", "ctrl+shift+n"),                     // 1
        new Row("quit", KeyContext.GLOBAL, "cmd+q", "ctrl+alt+shift+q"),                       // 3
        new Row("open_settings", KeyContext.GLOBAL, "cmd+,", "ctrl+shift+,"),                  // 4
        new Row("reload_config", KeyContext.GLOBAL, "none", "none"),                           // 5
        new Row("command_palette", KeyContext.GLOBAL, "cmd+shift+p", "ctrl+shift+p"),          // 14
        new Row("new_tab", KeyContext.GLOBAL, "cmd+t", "ctrl+shift+t"),                        // 22
        new Row("close_tab", KeyContext.GLOBAL, "alt+cmd+w", "ctrl+alt+shift+w"),              // 23
        new Row("next_tab", KeyContext.GLOBAL, "cmd+shift+]", "ctrl+tab"),                     // 24
        new Row("previous_tab", KeyContext.GLOBAL, "cmd+shift+[", "ctrl+shift+tab"),           // 25
        new Row("select_tab_1", KeyContext.GLOBAL, "cmd+1", "ctrl+1"),                         // 26
        new Row("select_tab_2", KeyContext.GLOBAL, "cmd+2", "ctrl+2"),
        new Row("select_tab_3", KeyContext.GLOBAL, "cmd+3", "ctrl+3"),
        new Row("select_tab_4", KeyContext.GLOBAL, "cmd+4", "ctrl+4"),
        new Row("select_tab_5", KeyContext.GLOBAL, "cmd+5", "ctrl+5"),
        new Row("select_tab_6", KeyContext.GLOBAL, "cmd+6", "ctrl+6"),
        new Row("select_tab_7", KeyContext.GLOBAL, "cmd+7", "ctrl+7"),
        new Row("select_tab_8", KeyContext.GLOBAL, "cmd+8", "ctrl+8"),
        new Row("select_tab_9", KeyContext.GLOBAL, "cmd+9", "ctrl+9"),                         // 27, Select Last Tab in K2
        new Row("rename_tab", KeyContext.GLOBAL, "cmd+i", "ctrl+shift+i"),                     // 28
        new Row("split_right", KeyContext.GLOBAL, "cmd+d", "ctrl+shift+d"),                    // 32
        new Row("split_down", KeyContext.GLOBAL, "cmd+shift+d", "ctrl+alt+shift+d"),           // 33
        new Row("close_pane", KeyContext.GLOBAL, "cmd+w", "ctrl+shift+w"),                     // 34
        new Row("zoom_pane", KeyContext.GLOBAL, "cmd+shift+enter", "ctrl+alt+shift+enter"),    // 35
        new Row("focus_pane_left", KeyContext.GLOBAL, "cmd+alt+left", "ctrl+alt+shift+left"),  // 36
        new Row("focus_pane_right", KeyContext.GLOBAL, "cmd+alt+right", "ctrl+alt+shift+right"),
        new Row("focus_pane_up", KeyContext.GLOBAL, "cmd+alt+up", "ctrl+alt+shift+up"),
        new Row("focus_pane_down", KeyContext.GLOBAL, "cmd+alt+down", "ctrl+alt+shift+down"),
        new Row("copy", KeyContext.GLOBAL, "cmd+c", "ctrl+shift+c"),                           // 42
        new Row("paste", KeyContext.GLOBAL, "cmd+v", "ctrl+shift+v"),                          // 43
        new Row("clear_scrollback", KeyContext.TERMINAL, "cmd+k", "ctrl+shift+k"),             // 45
        new Row("find", KeyContext.GLOBAL, "cmd+f", "ctrl+shift+f"),                           // 47
        new Row("find_next", KeyContext.GLOBAL, "cmd+g", "ctrl+shift+g"),                      // 48
        new Row("find_previous", KeyContext.GLOBAL, "cmd+shift+g", "ctrl+alt+shift+g"),        // 49
        new Row("previous_prompt", KeyContext.TERMINAL, "cmd+up", "ctrl+shift+up"),            // 50
        new Row("next_prompt", KeyContext.TERMINAL, "cmd+down", "ctrl+shift+down"),            // 51
        new Row("font_bigger", KeyContext.GLOBAL, "cmd+=", "ctrl+shift+="),                    // 55
        new Row("font_smaller", KeyContext.GLOBAL, "cmd+-", "ctrl+shift+-"),
        new Row("font_reset", KeyContext.GLOBAL, "cmd+0", "ctrl+shift+0"));

    @Test void everyBuiltInActionHasItsApprovedDefaultsAndContext() {
        Set<String> seen = new HashSet<>();
        for (Row row : ROWS) {
            ActionId action = ActionId.forId(row.id()).orElseThrow(() -> new AssertionError("no built-in " + row.id()));
            assertThat(seen.add(row.id())).as("listed once: %s", row.id()).isTrue();
            assertThat(action.context()).as("context of %s", row.id()).isEqualTo(row.context());
            assertThat(KeyBindings.parse(action.defaultBinding(true), true)).as("macOS default of %s", row.id())
                .isEqualTo(KeyBindings.parse(row.macOs(), true));
            assertThat(KeyBindings.parse(action.defaultBinding(false), false)).as("Linux/Windows default of %s", row.id())
                .isEqualTo(KeyBindings.parse(row.other(), false));
        }
        assertThat(seen).as("every built-in action is in the table").hasSize(ActionId.values().length);
    }

    @Test void bothKeymapsBuildAndBindEveryDefault() {
        for (boolean mac : new boolean[]{true, false}) {
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (ActionId action : ActionId.values())
                assertThat(bindings.strokeFor(action)).as("%s on %s", action, mac ? "macOS" : "Linux")
                    .isEqualTo(KeyBindings.parse(action.defaultBinding(mac), mac));
        }
    }

    @Test void macDefaultsUseCmdAndLeaveMacOsItsKeys() {
        Set<KeyStroke> system = new HashSet<>();
        for (String key : List.of("cmd+h", "alt+cmd+h", "cmd+space", "alt+cmd+d", "ctrl+cmd+d", "ctrl+cmd+q",
                "shift+cmd+/", "shift+cmd+3", "shift+cmd+4", "shift+cmd+5"))
            system.add(KeyBindings.parse(key, true).orElseThrow());
        for (ActionId action : ActionId.values()) KeyBindings.parse(action.defaultBinding(true), true).ifPresent(stroke -> {
            assertThat(stroke.getModifiers() & InputEvent.META_DOWN_MASK).as("%s uses Cmd", action).isNotZero();
            assertThat(system).as("%s leaves macOS its keys", action).doesNotContain(stroke);
        });
    }

    @Test void linuxDefaultsLeaveTheShellItsKeys() {
        int mask = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
        for (ActionId action : ActionId.values()) KeyBindings.parse(action.defaultBinding(false), false).ifPresent(stroke -> {
            int modifiers = stroke.getModifiers() & mask, key = stroke.getKeyCode();
            boolean ctrlShift = (modifiers & InputEvent.CTRL_DOWN_MASK) != 0 && (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
            boolean ctrlDigit = modifiers == InputEvent.CTRL_DOWN_MASK && key >= KeyEvent.VK_1 && key <= KeyEvent.VK_9;
            boolean ctrlTab = key == KeyEvent.VK_TAB && (modifiers & ~InputEvent.SHIFT_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK;
            assertThat(ctrlShift || ctrlDigit || ctrlTab).as("%s: %s", action, KeyBindings.format(stroke, false)).isTrue();
            assertThat(modifiers & InputEvent.META_DOWN_MASK).as("%s avoids the Super key", action).isZero();
        });
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.KeymapDefaultsTest'`
Expected: FAIL. For example, the macOS default of `close_tab` is ⌘W where the table wants ⌥⌘W, and `rename_tab` is
F2 on Linux.

- [ ] **Step 3: Set the approved defaults**

In `ActionId.java`, replace the constants (from `NEW_TAB(` through `QUIT(…);`) with:

```java
    NEW_TAB("new_tab", "New Tab", "cmd+t", "ctrl+shift+t"),
    CLOSE_TAB("close_tab", "Close Tab", "alt+cmd+w", "ctrl+alt+shift+w"),
    NEW_WINDOW("new_window", "New Window", "cmd+n", "ctrl+shift+n"),
    SPLIT_RIGHT("split_right", "Split Right", "cmd+d", "ctrl+shift+d"),
    SPLIT_DOWN("split_down", "Split Down", "cmd+shift+d", "ctrl+alt+shift+d"),
    CLOSE_PANE("close_pane", "Close Pane", "cmd+w", "ctrl+shift+w"),
    ZOOM_PANE("zoom_pane", "Zoom Pane", "cmd+shift+enter", "ctrl+alt+shift+enter"),
    FOCUS_PANE_LEFT("focus_pane_left", "Focus Pane Left", "cmd+alt+left", "ctrl+alt+shift+left"),
    FOCUS_PANE_RIGHT("focus_pane_right", "Focus Pane Right", "cmd+alt+right", "ctrl+alt+shift+right"),
    FOCUS_PANE_UP("focus_pane_up", "Focus Pane Up", "cmd+alt+up", "ctrl+alt+shift+up"),
    FOCUS_PANE_DOWN("focus_pane_down", "Focus Pane Down", "cmd+alt+down", "ctrl+alt+shift+down"),
    NEXT_TAB("next_tab", "Next Tab", "cmd+shift+]", "ctrl+tab"),
    PREVIOUS_TAB("previous_tab", "Previous Tab", "cmd+shift+[", "ctrl+shift+tab"),
    SELECT_TAB_1("select_tab_1", "Select Tab 1", "cmd+1", "ctrl+1"),
    SELECT_TAB_2("select_tab_2", "Select Tab 2", "cmd+2", "ctrl+2"),
    SELECT_TAB_3("select_tab_3", "Select Tab 3", "cmd+3", "ctrl+3"),
    SELECT_TAB_4("select_tab_4", "Select Tab 4", "cmd+4", "ctrl+4"),
    SELECT_TAB_5("select_tab_5", "Select Tab 5", "cmd+5", "ctrl+5"),
    SELECT_TAB_6("select_tab_6", "Select Tab 6", "cmd+6", "ctrl+6"),
    SELECT_TAB_7("select_tab_7", "Select Tab 7", "cmd+7", "ctrl+7"),
    SELECT_TAB_8("select_tab_8", "Select Tab 8", "cmd+8", "ctrl+8"),
    SELECT_TAB_9("select_tab_9", "Select Tab 9", "cmd+9", "ctrl+9"),
    RENAME_TAB("rename_tab", "Rename Tab", "cmd+i", "ctrl+shift+i"),
    FIND("find", "Find", "cmd+f", "ctrl+shift+f"),
    FIND_NEXT("find_next", "Find Next", "cmd+g", "ctrl+shift+g"),
    FIND_PREVIOUS("find_previous", "Find Previous", "cmd+shift+g", "ctrl+alt+shift+g"),
    PREVIOUS_PROMPT("previous_prompt", "Previous Prompt", "cmd+up", "ctrl+shift+up", KeyContext.TERMINAL),
    NEXT_PROMPT("next_prompt", "Next Prompt", "cmd+down", "ctrl+shift+down", KeyContext.TERMINAL),
    COPY("copy", "Copy", "cmd+c", "ctrl+shift+c"),
    PASTE("paste", "Paste", "cmd+v", "ctrl+shift+v"),
    COMMAND_PALETTE("command_palette", "Command Palette", "cmd+shift+p", "ctrl+shift+p"),
    CLEAR_SCROLLBACK("clear_scrollback", "Clear Scrollback", "cmd+k", "ctrl+shift+k", KeyContext.TERMINAL),
    FONT_BIGGER("font_bigger", "Increase Font Size", "cmd+=", "ctrl+shift+="),
    FONT_SMALLER("font_smaller", "Decrease Font Size", "cmd+-", "ctrl+shift+-"),
    FONT_RESET("font_reset", "Reset Font Size", "cmd+0", "ctrl+shift+0"),
    OPEN_SETTINGS("open_settings", "Settings", "cmd+,", "ctrl+shift+,"),
    RELOAD_CONFIG("reload_config", "Reload Config", "none", "none"),
    QUIT("quit", "Quit", "cmd+q", "ctrl+alt+shift+q");
```

In `ConfigTemplate.java`, replace

```java
            : "# cmd means Ctrl+Shift; some defaults add Alt to keep actions distinct.\n# Command Palette uses plain Ctrl+K; Clear Scrollback uses Ctrl+Shift+K.\n");
```

with

```java
            : "# cmd means Ctrl+Shift outside macOS. The defaults below name ctrl, alt and shift literally.\n");
```

- [ ] **Step 4: Update the tests that pinned the old defaults**

In `KeyBindingsTest.java`, replace the whole `tabDefaultsUsePlatformModifierWithoutCompatibilityAlt` method with:

```java
    @Test
    void tabDefaultsFollowEachPlatformsTerminals() {
        for (boolean mac : new boolean[]{true, false}) {
            int modifier = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
            KeyBindings bindings = KeyBindings.defaults(mac);
            for (int number = 1; number <= 9; number++) {
                KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_1 + number - 1, modifier);
                assertThat(bindings.actionFor(stroke)).contains(ActionId.valueOf("SELECT_TAB_" + number));
            }
            KeyStroke previous = mac
                ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
            KeyStroke next = mac
                ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
            assertThat(bindings.actionFor(previous)).contains(ActionId.PREVIOUS_TAB);
            assertThat(bindings.actionFor(next)).contains(ActionId.NEXT_TAB);
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier))).isEmpty();
            assertThat(bindings.actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                modifier | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK))).isEmpty();
        }
    }
```

In `braceOverridesResolvePhysicalBracketEventsAndDetectEquivalentCollisions`, replace

```java
            .isThrownBy(() -> KeyBindings.withOverrides(false, Map.of("copy", "ctrl+{")))
```

with

```java
            .isThrownBy(() -> KeyBindings.withOverrides(true, Map.of("copy", "cmd+{")))
```

In `KeyBindingsExtensionTest.builtInsBeatPluginDefaultsAndEarlierPluginsBeatLaterOnes`, ⌘K now belongs to
Clear Scrollback (Terminal), which a Global plugin default conflicts with. Replace

```java
        assertThat(bindings.idFor(stroke("cmd+k"))).hasValue("command_palette");
```

with

```java
        assertThat(bindings.idFor(stroke("cmd+k"))).hasValue("clear_scrollback");
```

and

```java
        assertThat(resolved.problems().get(0).message()).contains("cmd+k", "command_palette");
```

with

```java
        assertThat(resolved.problems().get(0).message()).contains("cmd+k", "clear_scrollback");
```

In `ConfigTemplateTest.java`, replace the whole `paletteAndClearCommentsShowTheirLiteralPlatformDefaults` method with:

```java
    @Test void templateCommentsShowEachPlatformsLiteralDefaults() {
        assertThat(ConfigTemplate.text(true)).contains("# command_palette = \"cmd+shift+p\"", "# clear_scrollback = \"cmd+k\"",
            "# close_pane = \"cmd+w\"", "# reload_config = \"none\"");
        assertThat(ConfigTemplate.text(true)).doesNotContain("history_palette", "snippets_palette");
        assertThat(ConfigTemplate.text(false)).contains("# command_palette = \"ctrl+shift+p\"", "# clear_scrollback = \"ctrl+shift+k\"",
            "# next_tab = \"ctrl+tab\"", "cmd means Ctrl+Shift outside macOS");
        assertThat(ConfigTemplate.text(false)).doesNotContain("Ctrl+K");
    }
```

In `TabShortcutsTest.java`, replace the whole `platformDefaultsDispatchRealSelectionWrapAndIgnoreMissingTabs` method
with:

```java
    @Test void platformDefaultsDispatchRealSelectionWrapAndIgnoreMissingTabs() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            var keys = KeyBindings.defaults(mac);
            var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), keys);
            try {
                int modifier = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                KeyStroke next = keys.strokeFor(ActionId.NEXT_TAB).orElseThrow();
                KeyStroke previous = keys.strokeFor(ActionId.PREVIOUS_TAB).orElseThrow();
                assertThat(next).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
                assertThat(previous).isEqualTo(mac
                    ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, modifier | InputEvent.SHIFT_DOWN_MASK)
                    : KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTab(); owner.newTab(HOME); var second = owner.currentTab();
                owner.selectTab(first);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifier))).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(root.activate(next)).isTrue();
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(root.activate(previous)).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, modifier))).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
                    modifier | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK), owner)).isFalse();
                assertThat(owner.currentTab()).isSameAs(second);
                assertThat(owner.action(ActionId.SELECT_TAB_2).getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifier));
                owner.close();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifier))).isFalse();
            } finally { owner.close(); }
        });
    }
```

The palette tests open the palette with ⌘K / Ctrl+K. Move them to ⇧⌘P / Ctrl+Shift+P with this script: it replaces
the first `CommandPaletteShortcutsTest` method, then rewrites every palette press, release and typed event.

```bash
python3 - <<'PY'
import pathlib, re
root = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace")
first = '''    @Test void paletteAndClearUseEachPlatformsLiteralDefaults() {
        for (boolean mac : new boolean[]{true, false}) {
            var keys = KeyBindings.defaults(mac);
            int primary = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
            int clearModifiers = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
            assertThat(keys.strokeFor(ActionId.COMMAND_PALETTE)).contains(
                KeyStroke.getKeyStroke(KeyEvent.VK_P, primary | InputEvent.SHIFT_DOWN_MASK));
            assertThat(keys.strokeFor(ActionId.CLEAR_SCROLLBACK)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_K, clearModifiers));
        }
        var override = KeyBindings.withOverrides(true,
            Map.of("command_palette", "none", "clear_scrollback", "cmd+shift+p"));
        assertThat(override.strokeFor(ActionId.COMMAND_PALETTE)).isEmpty();
        assertThat(override.strokeFor(ActionId.CLEAR_SCROLLBACK)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    }

'''
path = root / "CommandPaletteShortcutsTest.java"
text = path.read_text(encoding="utf-8")
start = text.index("    @Test void paletteUsesLiteralPlatformModifierAndPreservesClearShortcut()")
end = text.index("    @Test void realRootBindingUsesPalettePolicyThenReturnsToNumberedTabs()")
path.write_text(text[:start] + first + text[end:], encoding="utf-8")
for name in ("CommandPaletteShortcutsTest.java", "PaletteKeyRouterTest.java", "PaletteScopesTest.java"):
    path = root / name
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"KeyEvent\.VK_K, (primary\((?:mac|true|false)\)|mod|primary)\)",
                  r"KeyEvent.VK_P, \1 | InputEvent.SHIFT_DOWN_MASK)", text)
    text = re.sub(r"(release\([^,()]+, )KeyEvent\.VK_K\)", r"\1KeyEvent.VK_P)", text)
    text = re.sub(r"(typed\([^,()]+, )'k'\)", r"\1'P')", text)
    path.write_text(text, encoding="utf-8")
    print(name, "VK_K left:", text.count("KeyEvent.VK_K"))
PY
```

Expected output: `CommandPaletteShortcutsTest.java VK_K left: 1` (the Clear Scrollback assertion), and `0` for the
other two files.

- [ ] **Step 5: Run the app suite**

Run: `./gradlew :jasper-app:test`
Expected: PASS. If a test still pins an old default (⌘K or Ctrl+K palette, ⌘W Close Tab, ⇧⌘W Close Pane, F2 Rename
Tab, ⇧⌘R Reload Config, Linux Ctrl+Shift+[ ] tabs or Ctrl+Shift+Q Quit), change its expectation to the table above.
Note the file and line in the commit message.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/keymap-defaults
git add jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java jasper-app/src/main/java/dev/jasper/app/config/ConfigTemplate.java \
  jasper-app/src/test/java/dev/jasper/app/config jasper-app/src/test/java/dev/jasper/app/workspace
git commit -m "Keymap: the approved defaults for built-in actions

Palette Shift+Cmd+P, Clear Scrollback Cmd+K (terminal only), Cmd+W closes the pane, Close Tab
Alt+Cmd+W, Rename Tab Cmd+I, Reload Config unbound; Linux tabs on Ctrl+Tab and Quit on
Ctrl+Alt+Shift+Q, so no default takes Ctrl+K or F2 from the shell.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: SDK 0.11 — `ShortcutContext`, explicit default pairs, testkit and contract

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ShortcutContext.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ActionSpec.java` (whole file)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java:175-191`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` (after `actions()`, ~line 330)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java` (after
  `actionsAreNamespacedUniqueInvocableAndContained`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:140-145`
- Modify: all six `plugins/*/src/main/resources/plugin.toml`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/ShortcutContextTest.java` (create),
  `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`, version pins in
  `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java:21`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java:54`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java:70`,
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeUiTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java:32-36`

**Interfaces:**
- Produces (SDK, `@since 0.11.0`): `ShortcutContext(Kind kind, Optional<String> id)` with
  `Kind {GLOBAL, TERMINAL, CONTENT, PANEL, WINDOW}` and factories `global()`, `terminal()`, `content()`,
  `panel(String panelId)`, `window(String windowId)`.
- Produces (SDK): `ActionSpec(id, title, icon, keywords, defaultBinding, Optional<String> otherDefaultBinding,
  ShortcutContext context)`, the old five-argument constructor, `withDefaultBindings(String macOs, String other)`
  (null `other` stores `"none"`), `withContext(ShortcutContext)`. `withDefaultBinding` clears `otherDefaultBinding`.
- Produces (testkit, `@since 0.11.0`): `Optional<ActionSpec> FakePluginHost.actionSpec(String actionId)`.
- Produces (app): `HostedUi` rejects a panel or window context whose id doesn't start with the plugin id and a dot.

- [ ] **Step 1: Write the failing SDK tests**

`jasper-sdk/src/test/java/dev/jasper/sdk/ui/ShortcutContextTest.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShortcutContextTest {
    @Test void panelAndWindowContextsNameAnIdAndTheOthersDoNot() {
        assertThat(ShortcutContext.global().kind()).isEqualTo(ShortcutContext.Kind.GLOBAL);
        assertThat(ShortcutContext.terminal().id()).isEmpty();
        assertThat(ShortcutContext.content().kind()).isEqualTo(ShortcutContext.Kind.CONTENT);
        assertThat(ShortcutContext.panel("dev.x.hosts").id()).hasValue("dev.x.hosts");
        assertThat(ShortcutContext.window("dev.x.manager"))
            .isEqualTo(new ShortcutContext(ShortcutContext.Kind.WINDOW, Optional.of("dev.x.manager")));
        assertThatIllegalArgumentException().isThrownBy(() -> ShortcutContext.panel(null));
        assertThatIllegalArgumentException().isThrownBy(() -> ShortcutContext.window(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShortcutContext(ShortcutContext.Kind.GLOBAL, Optional.of("dev.x")));
        assertThatNullPointerException().isThrownBy(() -> new ShortcutContext(null, Optional.empty()));
    }
}
```

In `UiValuesTest.java`, add after `actionSpecValidatesAndCopies`:

```java
    @Test void actionSpecCarriesPlatformDefaultsAndAShortcutContext() {
        var spec = ActionSpec.of("dev.x.tool.run", "Run");
        assertThat(spec.otherDefaultBinding()).isEmpty();
        assertThat(spec.context()).isEqualTo(ShortcutContext.global());
        var paired = spec.withDefaultBindings("cmd+alt+1", "alt+1").withContext(ShortcutContext.panel("dev.x.tool.hosts"))
            .withKeywords(List.of("hosts")).withIcon(new javax.swing.ImageIcon());
        assertThat(paired.defaultBinding()).hasValue("cmd+alt+1");
        assertThat(paired.otherDefaultBinding()).hasValue("alt+1");
        assertThat(paired.context()).isEqualTo(ShortcutContext.panel("dev.x.tool.hosts"));
        assertThat(paired.keywords()).containsExactly("hosts");
        assertThat(spec.withDefaultBindings("cmd+m", null).otherDefaultBinding()).as("no default off macOS").hasValue("none");
        assertThat(paired.withDefaultBinding("cmd+j").otherDefaultBinding()).as("one default again").isEmpty();
        assertThat(new ActionSpec("dev.x.run", "Run", Optional.empty(), List.of(), Optional.of("cmd+j")).context())
            .isEqualTo(ShortcutContext.global());
        assertThatNullPointerException().isThrownBy(() -> spec.withContext(null));
    }
```

In `AppearanceContractTest.java:21`, `FilesValuesTest.java:54` and `PaletteValuesTest.java:70`, change `"0.10.1"` to
`"0.11.0"`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test`
Expected: compilation fails (`ShortcutContext`, `otherDefaultBinding`, `withDefaultBindings`, `withContext` don't
exist).

- [ ] **Step 3: Write the SDK types**

`jasper-sdk/src/main/java/dev/jasper/sdk/ui/ShortcutContext.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;

/**
 * Where an action's shortcut applies. A key can belong to several actions in different contexts. Where focus is, the
 * most specific applicable one runs: a panel, window or content context, then {@link #terminal()}, then
 * {@link #global()}. A key whose action does not apply where focus is, or is disabled, reaches the focused component
 * instead, for example the shell in a terminal pane.
 *
 * @param kind which kind of place
 * @param id the panel's or window's id for {@link Kind#PANEL} and {@link Kind#WINDOW}; empty for the other kinds
 * @since 0.11.0
 */
public record ShortcutContext(Kind kind, Optional<String> id) {
    /** The places a shortcut can apply. */
    public enum Kind {
        /** Anywhere in a terminal window. */
        GLOBAL,
        /** In a terminal pane, including its find bar. */
        TERMINAL,
        /** In a content tab the plugin opened. */
        CONTENT,
        /** Inside one of the plugin's panels. */
        PANEL,
        /** Inside one of the plugin's windows. */
        WINDOW
    }

    /** Checks that panel and window contexts name an id and the other kinds do not. */
    public ShortcutContext {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        boolean named = kind == Kind.PANEL || kind == Kind.WINDOW;
        if (named && id.filter(value -> !value.isBlank()).isEmpty())
            throw new IllegalArgumentException("A " + kind + " context needs the id of one of the plugin's "
                + (kind == Kind.PANEL ? "panels" : "windows"));
        if (!named && id.isPresent()) throw new IllegalArgumentException("A " + kind + " context takes no id");
    }

    /**
     * Anywhere in a terminal window; the default.
     *
     * @return the context
     */
    public static ShortcutContext global() { return new ShortcutContext(Kind.GLOBAL, Optional.empty()); }

    /**
     * In a terminal pane, including its find bar.
     *
     * @return the context
     */
    public static ShortcutContext terminal() { return new ShortcutContext(Kind.TERMINAL, Optional.empty()); }

    /**
     * In a content tab the plugin opened.
     *
     * @return the context
     */
    public static ShortcutContext content() { return new ShortcutContext(Kind.CONTENT, Optional.empty()); }

    /**
     * Inside one of the plugin's panels.
     *
     * @param panelId the panel's id, which starts with the plugin's id and a dot
     * @return the context
     * @throws IllegalArgumentException when the id is null or blank
     */
    public static ShortcutContext panel(String panelId) { return new ShortcutContext(Kind.PANEL, Optional.ofNullable(panelId)); }

    /**
     * Inside one of the plugin's windows.
     *
     * @param windowId the window's id, which starts with the plugin's id and a dot
     * @return the context
     * @throws IllegalArgumentException when the id is null or blank
     */
    public static ShortcutContext window(String windowId) { return new ShortcutContext(Kind.WINDOW, Optional.ofNullable(windowId)); }
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ActionSpec.java` with:

```java
package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * An action as the user sees it in the command palette, menus and toolbar.
 *
 * @param id namespaced id that starts with the plugin's id and a dot, for example {@code dev.example.tool.run};
 *           it is also the key users write under {@code [keybindings]}
 * @param title non-blank title
 * @param icon icon for the palette and toolbar, normally from {@link Appearance#icon}
 * @param keywords extra palette search words; copied
 * @param defaultBinding shortcut in the configuration syntax, for example {@code cmd+alt+j}. The user's
 *                       configuration and the built-in shortcuts win; a default that collides is dropped. On Linux and
 *                       Windows, unless {@code otherDefaultBinding} names one, cmd means Ctrl+Shift, and a default
 *                       naming cmd and shift also gets alt ({@code cmd+shift+x} becomes Ctrl+Alt+Shift+X)
 * @param otherDefaultBinding the Linux and Windows default when it should differ from {@code defaultBinding}, naming
 *                            ctrl, alt and shift literally; {@code none} for no default there (since 0.11.0)
 * @param context where the shortcut applies; {@link ShortcutContext#global()} unless set (since 0.11.0)
 */
public record ActionSpec(String id, String title, Optional<Icon> icon, List<String> keywords, Optional<String> defaultBinding,
                         Optional<String> otherDefaultBinding, ShortcutContext context) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and title and copies the keywords. */
    public ActionSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced action id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An action needs a title");
        Objects.requireNonNull(icon, "icon");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        Objects.requireNonNull(defaultBinding, "defaultBinding");
        Objects.requireNonNull(otherDefaultBinding, "otherDefaultBinding");
        Objects.requireNonNull(context, "context");
    }

    /**
     * A Global action whose default, if any, applies on every platform: the form SDK 0.10 had.
     *
     * @param id namespaced id
     * @param title non-blank title
     * @param icon icon for the palette and toolbar
     * @param keywords extra palette search words
     * @param defaultBinding shortcut in the configuration syntax
     */
    public ActionSpec(String id, String title, Optional<Icon> icon, List<String> keywords, Optional<String> defaultBinding) {
        this(id, title, icon, keywords, defaultBinding, Optional.empty(), ShortcutContext.global());
    }

    /**
     * An action with only an id and a title.
     *
     * @param id namespaced id
     * @param title non-blank title
     * @return the spec
     */
    public static ActionSpec of(String id, String title) {
        return new ActionSpec(id, title, Optional.empty(), List.of(), Optional.empty());
    }

    /**
     * A copy with an icon.
     *
     * @param value the icon, or null for none
     * @return the copy
     */
    public ActionSpec withIcon(Icon value) {
        return new ActionSpec(id, title, Optional.ofNullable(value), keywords, defaultBinding, otherDefaultBinding, context);
    }

    /**
     * A copy with palette keywords.
     *
     * @param value the keywords
     * @return the copy
     */
    public ActionSpec withKeywords(List<String> value) {
        return new ActionSpec(id, title, icon, value, defaultBinding, otherDefaultBinding, context);
    }

    /**
     * A copy with one default shortcut for every platform; it replaces a pair set by {@link #withDefaultBindings}.
     *
     * @param value the shortcut, or null for none
     * @return the copy
     */
    public ActionSpec withDefaultBinding(String value) {
        return new ActionSpec(id, title, icon, keywords, Optional.ofNullable(value), Optional.empty(), context);
    }

    /**
     * A copy with separate defaults for macOS and for Linux and Windows, for a key that differs between them, such as
     * plain Ctrl in an editor or a panel.
     *
     * @param macOs the macOS shortcut, or null for none there
     * @param other the Linux and Windows shortcut, naming ctrl, alt and shift literally, or null for none there
     * @return the copy
     * @since 0.11.0
     */
    public ActionSpec withDefaultBindings(String macOs, String other) {
        return new ActionSpec(id, title, icon, keywords, Optional.ofNullable(macOs), Optional.of(other == null ? "none" : other), context);
    }

    /**
     * A copy whose shortcut applies only in a context. Panel and window contexts must name the plugin's own panel or
     * window.
     *
     * @param value where the shortcut applies
     * @return the copy
     * @since 0.11.0
     */
    public ActionSpec withContext(ShortcutContext value) {
        return new ActionSpec(id, title, icon, keywords, defaultBinding, otherDefaultBinding, value);
    }
}
```

In `JasperSdk.java`, set `public static final String VERSION = "0.11.0";`.

- [ ] **Step 4: Run the SDK tests**

Run: `./gradlew :jasper-sdk:check`
Expected: PASS (tests and Javadoc lint).

- [ ] **Step 5: Write the failing testkit and contract tests**

In `FakeUiTest.java`, add the import `dev.jasper.sdk.ui.ShortcutContext` and this test:

```java
    @Test void theHostKeepsEachActionsSpecAndChecksItsShortcutContext() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            context.actions().register(ActionSpec.of("dev.x.tool.new", "New").withDefaultBindings("cmd+n", "ctrl+n")
                .withContext(ShortcutContext.panel("dev.x.tool.hosts")), invoked -> { });
            assertThat(host.actionSpec("dev.x.tool.new")).hasValueSatisfying(spec -> {
                assertThat(spec.defaultBinding()).hasValue("cmd+n");
                assertThat(spec.otherDefaultBinding()).hasValue("ctrl+n");
                assertThat(spec.context()).isEqualTo(ShortcutContext.panel("dev.x.tool.hosts"));
            });
            assertThat(host.actionSpec("dev.x.tool.absent")).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("dev.x.tool.other", "Other")
                .withContext(ShortcutContext.window("dev.y.manager")), invoked -> { }));
        }
    }
```

In `PluginContractTest.java`, add the import `dev.jasper.sdk.ui.ShortcutContext` and, after
`actionsAreNamespacedUniqueInvocableAndContained`:

```java
    @Test void shortcutContextsNameOnlyThePluginsOwnPanelsAndWindows() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.new", "New").withDefaultBindings("cmd+n", "ctrl+n")
                .withContext(ShortcutContext.panel("test.alpha.hosts")), invoked -> { });
            context.actions().register(ActionSpec.of("test.alpha.save", "Save").withContext(ShortcutContext.content()), invoked -> { });
            context.actions().register(ActionSpec.of("test.alpha.copy", "Copy")
                .withContext(ShortcutContext.window("test.alpha.manager")), invoked -> { });
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.foreign", "Foreign")
                .withContext(ShortcutContext.panel("test.beta.hosts")), invoked -> { }));
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.near", "Near")
                .withContext(ShortcutContext.window("test.alphabet.manager")), invoked -> { }));
        });
        assertThat(h.active("test.alpha")).isTrue();
        assertThat(h.actions()).containsExactly("test.alpha.new|New|true", "test.alpha.save|Save|true", "test.alpha.copy|Copy|true");
    }
```

- [ ] **Step 6: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.AppContractTest'`
Expected: FAIL. `actionSpec` doesn't exist, and neither host rejects a foreign context id.

- [ ] **Step 7: Validate contexts in both hosts and record specs in the fake**

In `FakeUi.java`, add the import `dev.jasper.sdk.ui.ShortcutContext` and in `actions()` replace

```java
            requireNamespace(spec.id(), "An action");
```

with

```java
            requireNamespace(spec.id(), "An action");
            spec.context().id().ifPresent(owned -> requireNamespace(owned,
                spec.context().kind() == ShortcutContext.Kind.PANEL ? "A panel context" : "A window context"));
```

In `FakePluginHost.java`, add after the `actions()` method:

```java
    /**
     * The spec an action was registered with, including its shortcut context and platform defaults.
     *
     * @param actionId the action
     * @return the spec, or empty when no started plugin registered the action
     * @since 0.11.0
     */
    public Optional<ActionSpec> actionSpec(String actionId) {
        for (FakePluginContext context : contexts.values()) {
            FakeUi.Action action = context.ui.actions.get(actionId);
            if (action != null) return Optional.of(action.spec);
        }
        return Optional.empty();
    }
```

(Add `import dev.jasper.sdk.ui.ActionSpec;` and `import java.util.Optional;` if `FakePluginHost` lacks them.)

In `HostedUi.java`, add the import `dev.jasper.sdk.ui.ShortcutContext`. In `actions()`, replace

```java
            requireNamespace(spec.id(), "An action");
```

with

```java
            requireNamespace(spec.id(), "An action");
            spec.context().id().ifPresent(owned -> requireNamespace(owned,
                spec.context().kind() == ShortcutContext.Kind.PANEL ? "A panel context" : "A window context"));
```

- [ ] **Step 8: Move the bundled plugins to SDK 0.11**

In each of `plugins/editor`, `plugins/history`, `plugins/remote`, `plugins/sample`, `plugins/snippets` and
`plugins/vault` (`src/main/resources/plugin.toml`), set the `sdk` line to:

```toml
sdk = ">=0.11.0, <0.12"
```

In `VersionRangeTest.java`, replace the method `pluginsBuiltForSdkZeroNineAreIncompatibleWithZeroTen` with:

```java
    @Test void pluginsBuiltForSdkZeroTenAreIncompatibleWithZeroEleven() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.10.0, <0.11").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.11.0, <0.12").contains(host)).isTrue();
    }
```

- [ ] **Step 9: Run the affected suites**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test`
Expected: PASS (the contract suite runs against both the fake and the app).

- [ ] **Step 10: Commit**

```bash
git branch --show-current   # must print claude/keymap-defaults
git add jasper-sdk jasper-sdk-testkit jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java plugins/*/src/main/resources/plugin.toml
git commit -m "SDK 0.11.0: ShortcutContext and explicit macOS / Linux-Windows default pairs

ActionSpec.withContext and withDefaultBindings; both hosts reject a panel or window context the
plugin does not own; FakePluginHost.actionSpec records contexts and defaults; bundled plugins
declare sdk >=0.11.0, <0.12.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The app carries each action's context and explicit defaults

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/ActionEntry.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java:63-71`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` (`actions()`), `plugins/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java:78-83`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java:19-29`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java:288-290`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:442-444` and `:550-551`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/config/KeybindingSaveTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java:50-53`

**Interfaces:**
- Consumes: `KeyContext`, `KeyBindings.Extension(id, default, otherDefault, context)`, `defaultFor` (Task 1);
  `ActionSpec.otherDefaultBinding()`, `ActionSpec.context()`, `ShortcutContext` (Task 3).
- Produces: `ActionEntry.otherDefault() : Optional<String>`, `ActionEntry.context() : KeyContext`,
  `ActionEntry.extension() : KeyBindings.Extension` (dotted ids only);
  `Contributions.addAction(String id, String title, Icon iconOrNull, List<String> keywords,
  Optional<String> defaultBinding, Optional<String> otherDefault, KeyContext context, Consumer<Invocation> handler)`.
  The six-argument `addAction` stays, as Global with no other default.

- [ ] **Step 1: Write the failing tests**

In `HostedUiTest.java`, add the imports `dev.jasper.app.commands.KeyContext` and `dev.jasper.sdk.ui.ShortcutContext`
and this test:

```java
    @Test void actionsCarryTheirShortcutContextAndPlatformDefaults() {
        ui.actions().register(ActionSpec.of("dev.x.tool.new", "New").withDefaultBindings("cmd+n", "ctrl+n")
            .withContext(ShortcutContext.panel("dev.x.tool.hosts")), context -> { });
        ui.actions().register(ActionSpec.of("dev.x.tool.save", "Save").withDefaultBinding("cmd+s")
            .withContext(ShortcutContext.content()), context -> { });
        ui.actions().register(ActionSpec.of("dev.x.tool.here", "Here").withContext(ShortcutContext.terminal()), context -> { });
        ActionEntry created = model.action("dev.x.tool.new").orElseThrow();
        assertThat(created.defaultBinding()).contains("cmd+n");
        assertThat(created.otherDefault()).contains("ctrl+n");
        assertThat(created.context()).isEqualTo(KeyContext.panel("dev.x.tool.hosts"));
        assertThat(created.extension().defaultFor(false)).contains("ctrl+n");
        assertThat(model.action("dev.x.tool.save").orElseThrow().context()).as("content means this plugin's tabs")
            .isEqualTo(KeyContext.content("dev.x.tool"));
        assertThat(model.action("dev.x.tool.save").orElseThrow().extension().defaultFor(false)).contains("cmd+s");
        assertThat(model.action("dev.x.tool.here").orElseThrow().context()).isEqualTo(KeyContext.TERMINAL);
        assertThatIllegalArgumentException().isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.x.tool.far", "Far")
            .withContext(ShortcutContext.panel("dev.y.hosts")), context -> { }));
    }
```

In `KeybindingSaveTest.java`, add:

```java
    @Test void restoringAPluginDefaultUsesItsLinuxAndWindowsDefault() throws Exception {
        Path file = directory.resolve("config.toml");
        var extensions = List.of(new KeyBindings.Extension("test.plugin", Optional.of("cmd+alt+1"), Optional.of("alt+1"),
            dev.jasper.app.commands.KeyContext.GLOBAL));
        try (var service = new ConfigService(file, false)) {
            service.saveKeybindings(Map.of(), Map.of("test.plugin", Optional.of("ctrl+alt+shift+x")), extensions).get(5, TimeUnit.SECONDS);
            var restored = service.saveKeybindings(Map.of("test.plugin", "ctrl+alt+shift+x"),
                Map.of("test.plugin", Optional.empty()), extensions).get(5, TimeUnit.SECONDS);
            assertThat(restored.snapshot().keybindings()).doesNotContainKey("test.plugin");
            assertThat(restored.snapshot().bindings(false).withExtensions(extensions).bindings().strokeFor("test.plugin"))
                .contains(KeyStroke.getKeyStroke("alt 1"));
        }
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.HostedUiTest' --tests 'dev.jasper.app.config.KeybindingSaveTest'`
Expected: compilation fails (`otherDefault()`, `context()` and `extension()` don't exist). Once that compiles, the
save test fails with "The default shortcut for test.plugin is unavailable". `ConfigService` still parses the macOS
string.

- [ ] **Step 3: Carry the context and the pair in the contributions model**

Replace the whole of `ActionEntry.java` with:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;

/** A contributed action. Mutators notify every window; all of them do nothing after {@link #close()}. */
public final class ActionEntry {
    private final Contributions owner;
    private final String id;
    private final Icon icon;
    private final List<String> keywords;
    private final Optional<String> defaultBinding;
    private final Optional<String> otherDefault;
    private final KeyContext context;
    private Consumer<Contributions.Invocation> handler;
    private String title;
    private boolean enabled = true;
    private boolean closed;

    ActionEntry(Contributions owner, String id, String title, Icon icon, List<String> keywords,
                Optional<String> defaultBinding, Optional<String> otherDefault, KeyContext context,
                Consumer<Contributions.Invocation> handler) {
        this.owner = owner; this.id = id; this.title = title; this.icon = icon;
        this.keywords = List.copyOf(keywords); this.defaultBinding = defaultBinding; this.otherDefault = otherDefault;
        this.context = context; this.handler = handler;
    }

    public String id() { return id; }
    public String title() { return title; }
    /** The icon, or null. */
    public Icon icon() { return icon; }
    public List<String> keywords() { return keywords; }
    public Optional<String> defaultBinding() { return defaultBinding; }
    /** The Linux and Windows default when the contributor named one; otherwise it follows from {@link #defaultBinding()}. */
    public Optional<String> otherDefault() { return otherDefault; }
    /** Where the action's shortcut applies. */
    public KeyContext context() { return context; }
    /** The action as the Keymap resolves it; only for namespaced (dotted) ids. */
    public KeyBindings.Extension extension() { return new KeyBindings.Extension(id, defaultBinding, otherDefault, context); }
    public boolean enabled() { return enabled && !closed; }

    /** Runs the contributor's handler unless the action is disabled or closed. */
    public void invoke(Contributions.Invocation invocation) {
        if (enabled()) handler.accept(invocation);
    }

    public void setEnabled(boolean value) {
        if (closed || enabled == value) return;
        enabled = value;
        owner.changed(Contributions.Kind.ACTIONS);
    }

    public void setTitle(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("An action needs a title");
        if (closed || title.equals(value)) return;
        title = value;
        owner.changed(Contributions.Kind.ACTIONS);
    }

    public void close() {
        if (closed) return;
        closed = true;
        handler = invocation -> { };
        owner.remove(this);
    }
}
```

In `Contributions.java`, add `import dev.jasper.app.commands.KeyContext;` and replace the `addAction` method with:

```java
    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords,
                                 Optional<String> defaultBinding, Consumer<Invocation> handler) {
        return addAction(id, title, iconOrNull, keywords, defaultBinding, Optional.empty(), KeyContext.GLOBAL, handler);
    }

    /** An action with a separate Linux and Windows default (empty: derived) and the context its shortcut applies in. */
    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords, Optional<String> defaultBinding,
                                 Optional<String> otherDefault, KeyContext context, Consumer<Invocation> handler) {
        requireEdt();
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(otherDefault, "otherDefault");
        Objects.requireNonNull(context, "context");
        if (actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
        var entry = new ActionEntry(this, id, title, iconOrNull, keywords, defaultBinding, otherDefault, context, handler);
        actions.put(id, entry);
        changed(Kind.ACTIONS);
        return entry;
    }
```

In `contributions/package-info.java`, change the dependency sentence to
`Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.files, dev.jasper.app.lifecycle, dev.jasper.app.palette.`

- [ ] **Step 4: Map the SDK context in `HostedUi`**

In `HostedUi.java`, add `import dev.jasper.app.commands.KeyContext;`. In `actions()`, replace

```java
            ActionEntry entry = model.addAction(spec.id(), spec.title(), spec.icon().orElse(null), spec.keywords(),
                spec.defaultBinding(), invocation -> containment.run(pluginId, "action " + spec.id(), () ->
```

with

```java
            ActionEntry entry = model.addAction(spec.id(), spec.title(), spec.icon().orElse(null), spec.keywords(),
                spec.defaultBinding(), spec.otherDefaultBinding(), keyContext(spec.context()),
                invocation -> containment.run(pluginId, "action " + spec.id(), () ->
```

and add this method after `actions()`:

```java
    /** The app's form of a plugin action's shortcut context; content means this plugin's own tabs. */
    private KeyContext keyContext(ShortcutContext context) {
        return switch (context.kind()) {
            case GLOBAL -> KeyContext.GLOBAL;
            case TERMINAL -> KeyContext.TERMINAL;
            case CONTENT -> KeyContext.content(pluginId);
            case PANEL -> KeyContext.panel(context.id().orElseThrow());
            case WINDOW -> KeyContext.window(context.id().orElseThrow());
        };
    }
```

In `plugins/package-info.java`, add `dev.jasper.app.commands, ` at the start of the allowed dependency list.

- [ ] **Step 5: Use `extension()` and the platform default everywhere an `Extension` is built**

`WindowContributions.extensions()`:

```java
    /** Contributed actions in registration order, which is plugin load order: the keybinding precedence. */
    List<KeyBindings.Extension> extensions() {
        List<KeyBindings.Extension> result = new ArrayList<>();
        for (ActionEntry entry : model.actions())
            if (KeyBindings.extensionId(entry.id())) result.add(entry.extension());
        return result;
    }
```

`ShortcutCatalog.rows`: replace the first statement and the extension-row default with

```java
        var resolved = base.withExtensions(actions.stream().filter(a -> KeyBindings.extensionId(a.id()))
            .map(ActionEntry::extension).toList());
```

and

```java
            String fallback = KeyBindings.extensionId(action.id()) ? action.extension().defaultFor(macOs).orElse("none") : "none";
            rows.add(new ShortcutEntry(action.id(), pluginGroup(action.id(), plugins), action.title(),
                resolved.bindings().strokeFor(action.id()).orElse(null), context, String.join(" ", action.keywords()), true, validDefault(fallback, macOs)));
```

`ConfigService.saveKeybindings`: replace

```java
                            .orElseGet(() -> extension.orElseThrow().defaultBinding().orElse("none")));
```

with

```java
                            .orElseGet(() -> extension.orElseThrow().defaultFor(macOs).orElse("none")));
```

`JasperApplication.java`: in the Keymap hooks (~line 444), replace

```java
                    .map(a -> new KeyBindings.Extension(a.id(), a.defaultBinding())).toList()));
```

with

```java
                    .map(ActionEntry::extension).toList()));
```

and in `bindingProblems()` (~line 551), replace

```java
            if (KeyBindings.extensionId(action.id())) extensions.add(new KeyBindings.Extension(action.id(), action.defaultBinding()));
```

with

```java
            if (KeyBindings.extensionId(action.id())) extensions.add(action.extension());
```

(`ActionEntry` is already imported there; add `import dev.jasper.app.contributions.ActionEntry;` if not.)

`RemoteShortcutConfigTest.resolve` (lines 50-53):

```java
    private KeyBindings.Resolved resolve(Map<String, String> overrides) {
        return KeyBindings.withOverrides(true, overrides).withExtensions(contributions.actions().stream()
            .map(dev.jasper.app.contributions.ActionEntry::extension).toList());
    }
```

- [ ] **Step 6: Run the app suite**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/keymap-defaults
git add jasper-app/src
git commit -m "App: contributed actions carry their shortcut context and Linux/Windows default

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Keys follow focus — focus-aware dispatch, and keys pass through

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (fields ~line 69,
  `installRootBindings` 281-295, `syncContentBindings` 340-363, `removeRootBindings` 365-375, `dispatchShortcut`
  721-743)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (after `invoke`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteKeyRouter.java` (after `dispatchShortcut`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ShortcutContextDispatchTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TabShortcutsTest.java` (the ⌘9 assertion)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowContributionsTest.java:79`

**Interfaces:**
- Consumes: `KeyBindings.resolve`, `KeyContext`, `ActionEntry.enabled()`, `Contributions.addAction` (8 arguments),
  the Task 2 defaults (⌘W `close_pane`, ⌘K Terminal `clear_scrollback`).
- Produces: `WindowContent.dispatchShortcut(KeyStroke, Component)` returns false (key not consumed) when no
  applicable action is bound or the action is disabled. Also `boolean WindowContent.claimsShortcut(KeyStroke,
  Component)`, `boolean WindowContent.applies(KeyContext, Component)`, `boolean WindowContributions.enabled(String)`,
  `boolean WindowContributions.panelContains(String panelId, Component focus)` and
  `boolean PaletteKeyRouter.claimsShortcut(KeyStroke)`.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/workspace/ShortcutContextDispatchTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelRegion;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ShortcutContextDispatchTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static KeyStroke mac(String binding) { return KeyBindings.parse(binding, true).orElseThrow(); }

    @Test void theMostSpecificContextAroundFocusWins() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            var model = new Contributions(); owner.connectContributions(model);
            List<String> ran = new ArrayList<>();
            model.addAction("dev.x.everywhere", "Everywhere", null, List.of(), Optional.of("cmd+alt+j"), Optional.empty(),
                KeyContext.GLOBAL, invocation -> ran.add("global"));
            model.addAction("dev.x.edit", "Edit", null, List.of(), Optional.of("cmd+alt+j"), Optional.empty(),
                KeyContext.content("dev.x"), invocation -> ran.add("content"));
            model.addAction("dev.x.hosts.new", "New Host", null, List.of(), Optional.of("cmd+alt+j"), Optional.empty(),
                KeyContext.panel("dev.x.hosts"), invocation -> ran.add("panel"));
            var panelField = new JTextField();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> panelField);
            model.requestPanel(new Contributions.PanelRequest(owner.id(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
            TerminalPane pane = owner.currentPane();

            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), pane)).isTrue();
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), panelField)).isTrue();
            var editor = new JPanel(); var inside = new JTextArea(); editor.add(inside);
            model.content().open("dev.x", "a", "A", owner.id(), editor);
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), inside)).isTrue();
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), owner)).isTrue();
            assertThat(ran).containsExactly("global", "panel", "content", "global");
        });
    }

    @Test void disabledAndInapplicableActionsLeaveTheirKeysToTheFocusedComponent() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), false);
            var model = new Contributions(); owner.connectContributions(model);
            List<String> ran = new ArrayList<>();
            var here = model.addAction("dev.x.here", "Here", null, List.of(), Optional.of("cmd+shift+o"), Optional.empty(),
                KeyContext.TERMINAL, invocation -> ran.add("here"));
            KeyStroke stroke = KeyBindings.parse("ctrl+alt+shift+o", false).orElseThrow();
            TerminalPane pane = owner.currentPane();
            assertThat(owner.dispatchShortcut(stroke, pane)).as("the Alt rule, in a terminal pane").isTrue();
            assertThat(owner.dispatchShortcut(stroke, new JTextField())).as("outside a terminal pane").isFalse();
            here.setEnabled(false);
            assertThat(owner.dispatchShortcut(stroke, pane)).as("disabled").isFalse();
            assertThat(ran).containsExactly("here");
            assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), pane))
                .as("Ctrl+K belongs to the shell on Linux").isFalse();
            assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), pane)).isFalse();
        });
    }

    @Test void theRootInputMapConsumesOnlyKeysAnActionTakes() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
            assertThat(root.activate(mac("cmd+t"))).as("New Tab").isTrue();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(root.activate(mac("cmd+9"))).as("no ninth tab: the key passes").isFalse();
            assertThat(root.activate(mac("cmd+k"))).as("Clear Scrollback applies only in a terminal pane").isFalse();
        });
    }

    @Test void closePaneClosesTheFocusedPaneThenTheTabWithItsLastPane() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            owner.newTab(HOME);
            var tab = owner.currentTab();
            tab.split(SplitTree.Axis.RIGHT);
            assertThat(tab.panes()).hasSize(2);
            assertThat(owner.dispatchShortcut(mac("cmd+w"), owner.currentPane())).isTrue();
            assertThat(tab.panes()).hasSize(1);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(owner.dispatchShortcut(mac("cmd+w"), owner.currentPane())).isTrue();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
        });
    }

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
```

In `TabShortcutsTest.platformDefaultsDispatchRealSelectionWrapAndIgnoreMissingTabs`, replace

```java
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, modifier))).isTrue();
```

with

```java
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_9, modifier)))
                    .as("no ninth tab: the key reaches the focused component").isFalse();
```

In `WindowContributionsTest.java:79`, replace

```java
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).as("a disabled app key is still not terminal input").isTrue();
```

with

```java
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).as("a disabled action leaves its key to the focused component").isFalse();
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ShortcutContextDispatchTest' --tests 'dev.jasper.app.workspace.TabShortcutsTest' --tests 'dev.jasper.app.workspace.WindowContributionsTest'`
Expected: FAIL. The panel and content actions never run (dispatch still takes the stroke's single id), disabled keys
return true, and the root map consumes ⌘9 and ⌘K.

- [ ] **Step 3: `WindowContributions` and `PaletteKeyRouter` helpers**

In `WindowContributions.java`, add after `invoke(String id)`:

```java
    /** Whether the contributed action exists and is enabled, so its shortcut takes the key. */
    boolean enabled(String id) { return model.action(id).map(ActionEntry::enabled).orElse(false); }

    /** Whether focus is inside this window's instance of the panel. */
    boolean panelContains(String panelId, java.awt.Component focus) {
        PanelInstance panel = panels.get(panelId);
        return focus != null && panel != null && panel.component != null && SwingUtilities.isDescendingFrom(focus, panel.component);
    }
```

In `PaletteKeyRouter.java`, add after `dispatchShortcut`:

```java
    /** Side-effect free: whether {@link #dispatchShortcut} would take this stroke (an opening shortcut, or any while open). */
    public boolean claimsShortcut(KeyStroke stroke) {
        return !closed && (palette.isOpen() || scopeFor(bindings.get().actionFor(stroke).orElse(null)) != null);
    }
```

- [ ] **Step 4: Focus-aware dispatch in `WindowContent`**

Add `import dev.jasper.app.commands.KeyContext;`. After `private KeyBindings baseBindings;` add:

```java
    // One root binding per stroke; dispatch picks the action for where focus is.
    private final java.util.Map<KeyStroke, String> rootKeys = new java.util.HashMap<>();
```

Replace `installRootBindings` with:

```java
    void installRootBindings(JRootPane root) {
        removeRootBindings();
        bindingRoot = root;
        bindings.strokes().forEach((actionId, stroke) -> {
            if (rootKeys.putIfAbsent(stroke, actionId) != null) return;
            root.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(stroke, actionId);
            root.getActionMap().put(actionId, new RootShortcut(stroke));
        });
        syncContentBindings();
        commandPalette.install(root);
        syncPaletteDispatcher();
    }

    /** The root input map's action for one stroke: it takes the key only when dispatch would use it. */
    private final class RootShortcut extends AbstractAction {
        private final KeyStroke stroke;
        RootShortcut(KeyStroke stroke) { this.stroke = stroke; }
        @Override public boolean accept(Object sender) { return isEnabled() && claimsShortcut(stroke, focusOwner()); }
        @Override public void actionPerformed(ActionEvent event) { dispatchShortcut(stroke, focusOwner()); }
    }

    private static Component focusOwner() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(); }
```

In `syncContentBindings`, replace

```java
            bindings.idFor(stroke).ifPresent(id -> input.put(stroke, id));
```

with

```java
            String rootKey = rootKeys.get(stroke);
            if (rootKey != null) input.put(stroke, rootKey);
```

In `removeRootBindings`, replace

```java
        bindings.strokes().forEach((actionId, stroke) -> {
            bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke);
            bindingRoot.getActionMap().remove(actionId);
        });
```

with

```java
        rootKeys.forEach((stroke, actionId) -> {
            bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke);
            bindingRoot.getActionMap().remove(actionId);
        });
        rootKeys.clear();
```

Replace the whole `dispatchShortcut` method with:

```java
    boolean dispatchShortcut(KeyStroke stroke, Component source) {
        if (dev.jasper.app.platform.WindowInput.blocked(source == null ? this : source)) return true;
        if (paletteKeys.dispatchShortcut(stroke, source == null ? this : source)) return true;
        var document = selectedContent();
        if (document != null) {
            // Text controls retain their native selection/clipboard behavior (including find fields).
            if (source instanceof JTextComponent && nativeContentClipboard(document, stroke)) return false;
            for (var command : document.commands()) if (stroke.equals(command.shortcut())) {
                if (command.enabled()) command.run().run(); return true;
            }
        }
        Optional<String> target = shortcutTarget(stroke, source);
        if (target.isEmpty()) return false;
        Optional<ActionId> builtIn = ActionId.forId(target.get());
        if (builtIn.isPresent()) invoke(builtIn.get()); else contributed.invoke(target.get());
        return true;
    }

    /** Side-effect free apart from refreshing enablement: whether {@link #dispatchShortcut} would take the key. */
    boolean claimsShortcut(KeyStroke stroke, Component source) {
        if (dev.jasper.app.platform.WindowInput.blocked(source == null ? this : source)) return true;
        if (paletteKeys.claimsShortcut(stroke)) return true;
        var document = selectedContent();
        if (document != null) {
            if (source instanceof JTextComponent && nativeContentClipboard(document, stroke)) return false;
            if (document.commands().stream().anyMatch(command -> stroke.equals(command.shortcut()))) return true;
        }
        return shortcutTarget(stroke, source).isPresent();
    }

    private static boolean nativeContentClipboard(dev.jasper.app.contributions.ContentEntry document, KeyStroke stroke) {
        return document.commands().stream().anyMatch(command ->
            stroke.equals(command.shortcut()) && (command.id().equals("copy") || command.id().equals("paste")));
    }

    /**
     * The enabled action a stroke runs from this focus owner: of the actions bound to it whose context contains the
     * focus owner, the most specific. Empty when none applies or that action is disabled, so the key reaches the
     * focused component (the shell, in a terminal pane).
     */
    private Optional<String> shortcutTarget(KeyStroke stroke, Component source) {
        Optional<String> found = bindings.resolve(stroke, context -> applies(context, source));
        if (found.isEmpty()) return Optional.empty();
        Optional<ActionId> builtIn = ActionId.forId(found.get());
        if (builtIn.isEmpty()) return contributed != null && contributed.enabled(found.get()) ? found : Optional.empty();
        if (source instanceof JTextComponent && (builtIn.get() == ActionId.COPY || builtIn.get() == ActionId.PASTE)) return Optional.empty();
        updateActions();
        return action(builtIn.get()).isEnabled() ? found : Optional.empty();
    }

    /** Whether a shortcut context contains the focus owner. */
    boolean applies(KeyContext context, Component focus) {
        return switch (context.kind()) {
            case GLOBAL -> true;
            case TERMINAL -> within(TerminalPane.class, focus);
            case CONTENT -> {
                var entry = selectedContent();
                yield entry != null && focus != null && entry.plugin().equals(context.owner())
                    && SwingUtilities.isDescendingFrom(focus, entry.component());
            }
            case PANEL -> contributed != null && contributed.panelContains(context.owner(), focus);
            // Plugin windows get the Keymap in plan K4; nothing in a terminal window is inside one.
            case WINDOW -> false;
        };
    }

    private static boolean within(Class<?> type, Component focus) {
        for (Component component = focus; component != null; component = component.getParent())
            if (type.isInstance(component)) return true;
        return false;
    }
```

- [ ] **Step 5: Run the app suite**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/keymap-defaults
git add jasper-app/src
git commit -m "Keys follow focus: the most specific applicable action runs, others reach the shell

A disabled or inapplicable action no longer swallows its key; the root input map consumes a key
only when dispatch would use it; panel and content-tab actions may reuse Global strokes.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The Keymap's Where column

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutEntry.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java` (`rows`, new `where` and
  `description`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutTree.java` (`Row`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutText.java:50-51`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapPage.java:150-156`
- Test: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutHelpTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutSectionsTest.java`

**Interfaces:**
- Consumes: `ActionId.context()`, `ActionEntry.context()`, `ActionEntry.extension()`, `KeyContext`.
- Produces: `ShortcutEntry.where()`, a new ninth record component. The eight-argument constructor defaults it to
  `"Global"`, and the six-argument one copies `context`. Also
  `ShortcutCatalog.rows(KeyBindings, List<ActionEntry>, Map<String,String> plugins, Map<String,String> panels, boolean)`
  (the four-argument form remains) and `ShortcutCatalog.where(KeyContext, Map, Map)`.

- [ ] **Step 1: Write the failing tests**

In `ShortcutHelpTest.java`, add the import `dev.jasper.app.commands.KeyContext` and:

```java
    @Test void catalogNamesWhereEachShortcutApplies() throws Exception {
        edt(() -> {
            var c = new Contributions();
            c.addAction("dev.x.save", "Save", null, List.of(), Optional.of("cmd+s"), Optional.empty(), KeyContext.content("dev.x"), ignored -> {});
            c.addAction("dev.x.hosts.new", "New Host", null, List.of(), Optional.of("cmd+n"), Optional.of("ctrl+n"),
                KeyContext.panel("dev.x.hosts"), ignored -> {});
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(false), c.actions(), Map.of("dev.x", "Tool"),
                Map.of("dev.x.hosts", "Hosts"), false);
            assertThat(rows).filteredOn(row -> row.id().equals("new_tab")).singleElement().satisfies(row -> {
                assertThat(row.where()).isEqualTo("Global");
                assertThat(ShortcutText.matches(row, "global", null)).isTrue();
            });
            assertThat(rows).filteredOn(row -> row.id().equals("clear_scrollback")).singleElement().satisfies(row -> {
                assertThat(row.where()).isEqualTo("Terminal");
                assertThat(row.context()).isEqualTo("In a terminal pane");
            });
            assertThat(rows).filteredOn(row -> row.id().equals("dev.x.save")).singleElement()
                .satisfies(row -> assertThat(row.where()).isEqualTo("Tool tabs"));
            assertThat(rows).filteredOn(row -> row.id().equals("dev.x.hosts.new")).singleElement().satisfies(row -> {
                assertThat(row.where()).isEqualTo("Hosts panel");
                assertThat(row.defaultBinding()).isEqualTo("ctrl+n");
                assertThat(ShortcutText.format(row.stroke())).isEqualTo("Ctrl+N");
                assertThat(row.customized(false)).isFalse();
            });
            assertThat(rows).filteredOn(row -> row.id().equals("palette.primary")).singleElement()
                .satisfies(row -> assertThat(row.where()).isEqualTo("Command palette"));
        });
    }
```

In `ShortcutSectionsTest.java`, add:

```java
    @Test void rowsShowWhereTheirShortcutApplies() throws Exception {
        edt(() -> {
            new ThemeController();
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("clear_scrollback", "Terminal", "Clear Scrollback",
                    KeyStroke.getKeyStroke("meta K"), "In a terminal pane", "", true, "cmd+k", "Terminal")));
                panel.tree.expandSections(true);
                assertThat(labels(panel.tree, "Clear Scrollback"))
                    .contains("Clear Scrollback", "Terminal", ShortcutText.format(KeyStroke.getKeyStroke("meta K")));
            } finally { panel.close(); }
        });
    }

    private static List<String> labels(JTree tree, String name) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object node = tree.getPathForRow(row).getLastPathComponent();
            var cell = (Container) tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, tree.isExpanded(row),
                tree.getModel().isLeaf(node), row, false);
            if (!((JLabel) cell.getComponent(0)).getText().equals(name)) continue;
            List<String> texts = new java.util.ArrayList<>();
            collect(cell, texts);
            return texts;
        }
        throw new AssertionError("no row named " + name);
    }

    private static void collect(Container root, List<String> texts) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) texts.add(label.getText());
            if (child instanceof Container nested) collect(nested, texts);
        }
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*'`
Expected: compilation fails (no `where()`, no five-argument `rows`, no nine-argument `ShortcutEntry`).

- [ ] **Step 3: Implement the column**

Replace the whole of `ShortcutEntry.java` with:

```java
package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import java.util.Objects;
import javax.swing.KeyStroke;

/** One reference row; a null stroke means the action is currently unassigned. {@code where} names the context. */
record ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords,
                     boolean editable, String defaultBinding, String where) {
    ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords,
                  boolean editable, String defaultBinding) {
        this(id, group, name, stroke, context, keywords, editable, defaultBinding, "Global");
    }

    ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords) {
        this(id, group, name, stroke, context, keywords, false, "none", context);
    }

    /** Whether an editable row's shortcut differs from its default. */
    boolean customized(boolean macOs) {
        return editable && !Objects.equals(stroke, KeyBindings.parse(defaultBinding, macOs).orElse(null));
    }
}
```

In `ShortcutCatalog.java`, add `import dev.jasper.app.commands.KeyContext;` and replace the `rows` method with:

```java
    static List<ShortcutEntry> rows(KeyBindings base, List<ActionEntry> actions, Map<String, String> plugins, boolean macOs) {
        return rows(base, actions, plugins, Map.of(), macOs);
    }

    /** @param panels panel titles by panel id, for actions whose shortcut applies inside a panel */
    static List<ShortcutEntry> rows(KeyBindings base, List<ActionEntry> actions, Map<String, String> plugins,
                                    Map<String, String> panels, boolean macOs) {
        var resolved = base.withExtensions(actions.stream().filter(a -> KeyBindings.extensionId(a.id()))
            .map(ActionEntry::extension).toList());
        var rows = new ArrayList<ShortcutEntry>();
        for (ActionId id : ActionId.values()) rows.add(new ShortcutEntry(id.id(), category(id), id.label(),
            resolved.bindings().strokeFor(id).orElse(null), description(id.context(), plugins, panels), id.id().replace('_', ' '),
            true, KeyBindings.effectiveDefaultBinding(id, macOs), where(id.context(), plugins, panels)));
        for (ActionEntry action : actions) {
            String context = resolved.problems().stream().filter(problem -> problem.actionId().equals(action.id()))
                .map(KeyBindings.Problem::message).findFirst().orElseGet(() -> description(action.context(), plugins, panels));
            String fallback = KeyBindings.extensionId(action.id()) ? action.extension().defaultFor(macOs).orElse("none") : "none";
            rows.add(new ShortcutEntry(action.id(), pluginGroup(action.id(), plugins), action.title(),
                resolved.bindings().strokeFor(action.id()).orElse(null), context, String.join(" ", action.keywords()), true,
                validDefault(fallback, macOs), where(action.context(), plugins, panels)));
        }
        contextual(rows, actions, plugins, macOs);
        rows.sort(Comparator.comparing(ShortcutEntry::group, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ShortcutEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(ShortcutEntry::id));
        return List.copyOf(rows);
    }

    /** The Where column: Global, Terminal, a plugin's tabs, a panel or a window. */
    static String where(KeyContext context, Map<String, String> plugins, Map<String, String> panels) {
        return switch (context.kind()) {
            case GLOBAL -> "Global";
            case TERMINAL -> "Terminal";
            case CONTENT -> plugins.getOrDefault(context.owner(), context.owner()) + " tabs";
            case PANEL -> panels.getOrDefault(context.owner(), context.owner()) + " panel";
            case WINDOW -> context.owner() + " window";
        };
    }

    private static String description(KeyContext context, Map<String, String> plugins, Map<String, String> panels) {
        return switch (context.kind()) {
            case GLOBAL -> "Anywhere in a terminal window";
            case TERMINAL -> "In a terminal pane";
            case CONTENT, PANEL, WINDOW -> "Only in the " + where(context, plugins, panels);
        };
    }
```

In `ShortcutText.matches`, replace

```java
        String haystack = normalize(row.name() + " " + row.group() + " " + row.context() + " "
```

with

```java
        String haystack = normalize(row.name() + " " + row.group() + " " + row.context() + " " + row.where() + " "
```

In `KeymapPage.rows()`, replace the `return` line with:

```java
        var panels = new java.util.LinkedHashMap<String, String>();
        for (var entry : hooks.contributions().panels()) panels.put(entry.id(), entry.title());
        return ShortcutCatalog.rows(base, hooks.contributions().actions(), hooks.pluginNames().get(), panels, hooks.macOs());
```

In `ShortcutTree.java`, replace the whole `Row` class with:

```java
    /** Wide rows keep the binding aligned to the right with where it applies beside it; long action names ellipsize. */
    private final class Row extends JPanel implements TreeCellRenderer {
        private final JLabel name = new JLabel(), where = new JLabel(), binding = new JLabel();
        private final JPanel trailing = new JPanel(new BorderLayout(UIScale.scale(12), 0));
        Row() {
            super(new BorderLayout(UIScale.scale(20), 0));
            setOpaque(false);
            name.putClientProperty("html.disable", true); where.putClientProperty("html.disable", true);
            binding.putClientProperty("html.disable", true);
            name.setIconTextGap(UIScale.scale(8));
            binding.setHorizontalAlignment(SwingConstants.TRAILING);
            trailing.setOpaque(false);
            trailing.add(where, BorderLayout.CENTER); trailing.add(binding, BorderLayout.EAST);
            add(name, BorderLayout.CENTER); add(trailing, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(4), 0, UIScale.scale(12)));
        }
        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean open, boolean leaf, int row, boolean focused) {
            var node = (DefaultMutableTreeNode) value;
            var item = node.getUserObject();
            Color foreground = UIManager.getColor(selected && tree.hasFocus() ? "Tree.selectionForeground" : "Label.foreground");
            Color link = UIManager.getColor("Component.linkColor");
            // A row whose shortcut differs from its default is blue, and so is a section holding one, as IntelliJ shows them.
            boolean customized = item instanceof ShortcutEntry shortcut ? shortcut.customized(macOs)
                : java.util.Collections.list(node.children()).stream().anyMatch(child ->
                    ((DefaultMutableTreeNode) child).getUserObject() instanceof ShortcutEntry entry && entry.customized(macOs));
            name.setForeground(customized && link != null && !(selected && tree.hasFocus()) ? link : foreground);
            Color dim = selected && tree.hasFocus() ? foreground : UIManager.getColor("Label.disabledForeground");
            binding.setForeground(dim); where.setForeground(dim);
            name.setFont(tree.getFont().deriveFont(item instanceof ShortcutEntry ? Font.PLAIN : Font.BOLD));
            binding.setFont(tree.getFont());
            where.setFont(tree.getFont().deriveFont(tree.getFont().getSize2D() - 1f));
            if (item instanceof ShortcutEntry shortcut) {
                name.setText(shortcut.name()); name.setIcon(null);
                binding.setText(ShortcutText.format(shortcut.stroke()));
                where.setText(shortcut.where());
                setToolTipText(shortcut.context() + (shortcut.editable() ? " · Click to edit" : " · Read-only contextual key"));
            } else {
                name.setText(String.valueOf(item));
                name.setIcon(AppIcons.bundled(open ? "general/open" : "nodes/folder"));
                binding.setText(String.valueOf(node.getChildCount()));
                where.setText("");
                setToolTipText(open ? "Collapse section" : "Expand section");
            }
            column(where, 88); column(binding, 96);
            getAccessibleContext().setAccessibleName(name.getText() + ", " + binding.getText()
                + (where.getText().isEmpty() ? "" : ", " + where.getText()));
            getAccessibleContext().setAccessibleDescription(getToolTipText());
            return this;
        }
        /** A minimum width for a trailing label, so the Where and binding columns line up from row to row. */
        private void column(JLabel label, int minimum) {
            label.setPreferredSize(null);
            Dimension natural = label.getPreferredSize();
            label.setPreferredSize(new Dimension(Math.max(natural.width, UIScale.scale(minimum)), natural.height));
        }
        @Override public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            doLayout(); trailing.doLayout(); // CellRendererPane does not validate compound tree renderers.
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(320), getRowHeight());
        }
    }
```

- [ ] **Step 4: Run the Keymap tests, then the app suite**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*'`
Expected: PASS.
Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/keymap-defaults
git add jasper-app/src
git commit -m "Keymap page: a Where column names each shortcut's context

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Bundled plugin defaults (Remote, Vault)

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java:188` (Open SFTP Here), `:192` (Split
  with Same Host), `:236-246` (`registerNavigation`)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferUi.java:42-43`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java:123-125`
- Modify: `plugins/remote/src/main/resources/plugin.toml` (`version = "0.9.0"`), `plugins/vault/src/main/resources/plugin.toml`
  (`version = "0.4.0"`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java:79-109`,
  `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java:62-87`

**Interfaces:**
- Consumes: `ActionSpec.withDefaultBindings`, `withContext`, `ShortcutContext.terminal()`,
  `FakePluginHost.actionSpec` (Task 3); `ActionEntry.otherDefault()`, `extension()` (Task 4).
- Produces: the approved rows: 17 Connect to SSH Host ⇧⌘H / Ctrl+Shift+H; 66 SSH Sessions ⌥⌘1 / Alt+1; 67 SFTP
  ⌥⌘2 / Alt+2; 68 Transfers ⌥⌘3 / Alt+3; 56 Open SFTP Here ⇧⌘O / Ctrl+Alt+Shift+O (Terminal); 41 Split with Same
  Host (Terminal, no default); 19 Open Vault ⌘\ / Ctrl+Shift+\; 106 Lock Vault ⇧⌘L / Ctrl+Alt+Shift+L.

- [ ] **Step 1: Write the failing tests**

In `RemotePluginTest.java`, replace the whole `navigationActionsKeepTheirDefaultBindingsAndAnOldShortcutsTableIsUnknown`
method with:

```java
    @Test void navigationActionsCarryTheirDefaultsAndAnOldShortcutsTableIsUnknown(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.remote", "shortcuts.toggle_panel", "cmd+alt+p");
            host.start(INFO, Set.of(), Set.of(), plugin(dir.resolve("ssh")));
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(defaults(host, RemotePlugin.CONNECT)).isEqualTo("cmd+shift+h|ctrl+shift+h|GLOBAL");
            assertThat(defaults(host, RemotePlugin.HOSTS)).isEqualTo("cmd+alt+1|alt+1|GLOBAL");
            assertThat(defaults(host, RemotePlugin.SFTP_TOGGLE)).isEqualTo("cmd+alt+2|alt+2|GLOBAL");
            assertThat(defaults(host, dev.jasper.remote.ui.transfers.TransferUi.SHOW)).isEqualTo("cmd+alt+3|alt+3|GLOBAL");
            assertThat(defaults(host, RemotePlugin.SFTP)).as("Alt rule off macOS").isEqualTo("cmd+shift+o|derived|TERMINAL");
            assertThat(defaults(host, RemotePlugin.SPLIT)).isEqualTo("none|derived|TERMINAL");
            assertThat(host.settingsReports("dev.jasper.remote")).containsExactly("shortcuts.toggle_panel: Unknown setting or table; ignored.");
        }
    }

    private static String defaults(FakePluginHost host, String actionId) {
        var spec = host.actionSpec(actionId).orElseThrow();
        return spec.defaultBinding().orElse("none") + "|" + spec.otherDefaultBinding().orElse("derived") + "|" + spec.context().kind();
    }
```

In `VaultPluginTest.java`, add:

```java
    @Test void openAndLockHaveTheirDefaultShortcuts() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            var open = host.actionSpec(VaultPlugin.OPEN).orElseThrow();
            assertThat(open.defaultBinding()).as("cmd is Ctrl+Shift off macOS: Ctrl+Shift+\\").contains("cmd+\\");
            assertThat(open.otherDefaultBinding()).isEmpty();
            var lock = host.actionSpec(VaultPlugin.LOCK).orElseThrow();
            assertThat(lock.defaultBinding()).as("the Alt rule makes it Ctrl+Alt+Shift+L off macOS").contains("cmd+shift+l");
            assertThat(lock.otherDefaultBinding()).isEmpty();
        }
    }
```

In `RemoteShortcutConfigTest.java`, in `defaultsHoldAcrossReloadsAndAppOverridesRebindWithoutMovingMenuPlacements`,
replace both occurrences of the pair

```java
            assertThat(contributions.action(HOSTS).orElseThrow().defaultBinding()).contains("cmd+shift+s");
            assertThat(contributions.action(CONNECT).orElseThrow().defaultBinding()).contains("cmd+shift+h");
```

with

```java
            assertThat(contributions.action(HOSTS).orElseThrow().defaultBinding()).contains("cmd+alt+1");
            assertThat(contributions.action(HOSTS).orElseThrow().otherDefault()).contains("alt+1");
            assertThat(contributions.action(CONNECT).orElseThrow().defaultBinding()).contains("cmd+shift+h");
            assertThat(contributions.action(CONNECT).orElseThrow().otherDefault()).contains("ctrl+shift+h");
```

Then add the import `dev.jasper.app.commands.KeyContext` and this test:

```java
    @Test void linuxDefaultsUseTheExplicitPairsAndTheAltRule() throws Exception {
        start();
        onEdt(() -> {
            var resolved = KeyBindings.defaults(false).withExtensions(contributions.actions().stream()
                .map(dev.jasper.app.contributions.ActionEntry::extension).toList());
            assertThat(resolved.problems()).noneMatch(problem -> problem.actionId().startsWith("dev.jasper.remote."));
            var bindings = resolved.bindings();
            assertThat(bindings.strokeFor(HOSTS)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK));
            assertThat(bindings.strokeFor(SFTP_TOGGLE)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK));
            assertThat(bindings.strokeFor(CONNECT)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_H,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
            assertThat(bindings.strokeFor("dev.jasper.remote.sftp")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_O,
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
            assertThat(bindings.contextFor("dev.jasper.remote.sftp")).isEqualTo(KeyContext.TERMINAL);
        });
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.RemotePluginTest' :jasper-plugin-vault:test --tests 'dev.jasper.vault.VaultPluginTest'`
Expected: FAIL. HOSTS is still `cmd+shift+s`, SFTP_TOGGLE and Transfers have no default, Open SFTP Here has no
default or context, and Vault opens with F8.

- [ ] **Step 3: Set the plugin defaults**

In `RemotePlugin.registerNavigation()`, replace the three registrations with:

```java
        context.actions().register(ActionSpec.of(CONNECT, "Connect to SSH Host...").withIcon(icon)
                .withKeywords(List.of("ssh", "remote", "host", "connect")).withDefaultBindings("cmd+shift+h", "ctrl+shift+h"),
            invoked -> context.palette().open(invoked.window(), RemoteScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(HOSTS, "Toggle SSH hosts").withIcon(icon)
                .withKeywords(List.of("ssh", "hosts", "panel", "show", "hide")).withDefaultBindings("cmd+alt+1", "alt+1"),
            invoked -> showPanel(invoked.window()));
        context.actions().register(ActionSpec.of(SFTP_TOGGLE, "Toggle SFTP")
                .withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER)).withKeywords(List.of("sftp", "files", "panel", "show", "hide"))
                .withDefaultBindings("cmd+alt+2", "alt+2"),
            invoked -> sftpUi.toggle(invoked.window()));
```

Replace the Open SFTP Here registration line

```java
        context.actions().register(ActionSpec.of(SFTP,"Open SFTP here").withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER)).withKeywords(List.of("sftp","files","browse","remote")),invoked->{
```

with

```java
        context.actions().register(ActionSpec.of(SFTP,"Open SFTP here").withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER)).withKeywords(List.of("sftp","files","browse","remote"))
                .withDefaultBinding("cmd+shift+o").withContext(dev.jasper.sdk.ui.ShortcutContext.terminal()),invoked->{
```

and the Split with Same Host registration

```java
        splitAction = context.actions().register(ActionSpec.of(SPLIT, "Split with Same Host").withKeywords(List.of("ssh", "split")), invoked -> invoked.pane().ifPresent(this::splitSameHost));
```

with

```java
        splitAction = context.actions().register(ActionSpec.of(SPLIT, "Split with Same Host").withKeywords(List.of("ssh", "split"))
            .withContext(dev.jasper.sdk.ui.ShortcutContext.terminal()), invoked -> invoked.pane().ifPresent(this::splitSameHost));
```

In `TransferUi.java`, replace

```java
            .withKeywords(List.of("sftp", "upload", "download", "queue")), invoked -> showSidebar.accept(invoked.window()));
```

with

```java
            .withKeywords(List.of("sftp", "upload", "download", "queue")).withDefaultBindings("cmd+alt+3", "alt+3"),
            invoked -> showSidebar.accept(invoked.window()));
```

In `VaultPlugin.java`, replace `.withDefaultBinding("F8")` with `.withDefaultBinding("cmd+\\")`, and replace

```java
        lockAction = context.actions().register(ActionSpec.of(LOCK, "Lock Vault").withKeywords(List.of("vault", "lock")), invoked -> lock.lock());
```

with

```java
        lockAction = context.actions().register(ActionSpec.of(LOCK, "Lock Vault").withKeywords(List.of("vault", "lock"))
            .withDefaultBinding("cmd+shift+l"), invoked -> lock.lock());
```

Set `version = "0.9.0"` in `plugins/remote/src/main/resources/plugin.toml` and `version = "0.4.0"` in
`plugins/vault/src/main/resources/plugin.toml`.

- [ ] **Step 4: Run the plugin and app suites**

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-vault:test :jasper-app:test`
Expected: PASS. `RemoteShortcutConfigTest` runs against the staged Remote jar, which Gradle rebuilds.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/keymap-defaults
git add plugins/remote plugins/vault jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java
git commit -m "Remote 0.9.0 and Vault 0.4.0: the approved shortcut defaults

SSH Sessions Alt+Cmd+1 (Alt+1), SFTP Alt+Cmd+2, Transfers Alt+Cmd+3, Connect Shift+Cmd+H with
Ctrl+Shift+H, Open SFTP Here Shift+Cmd+O in terminals, Open Vault Cmd+\\, Lock Vault Shift+Cmd+L.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: SDK docs, maintenance notes and status; full check

**Files:**
- Modify: `jasper-sdk/README.md` (after the SDK 0.10.1 paragraph)
- Modify: `docs/plugin-authoring.md:104-106` (the Shortcuts bullet)
- Modify: `docs/sdk-architecture.md` (new section before `## Plugins manager, install and restart`)
- Modify: `docs/app-maintenance.md:11`
- Modify: `docs/STATUS.md` (new first bullet under `## Current state — 2026-09-30`)
- Modify: this plan's status banner

- [ ] **Step 1: SDK README**

After the paragraph that starts "SDK 0.10.1 lets `FileLocation.Sourced` carry", add:

```markdown
SDK 0.11.0 adds shortcut contexts. `ActionSpec.withContext(ShortcutContext)` limits where an action's shortcut applies:
`global()` (the default), `terminal()` (a terminal pane), `content()` (the plugin's content tabs), `panel(id)` or
`window(id)` (one of the plugin's own panels or windows). Where focus is, the most specific applicable action takes a
key, and a key whose action doesn't apply, or is disabled, reaches the focused component, such as the shell.
`withDefaultBindings(macOs, other)` gives a separate Linux and Windows default; with `withDefaultBinding` alone, a
default naming cmd and shift gets alt outside macOS (`cmd+shift+x` becomes Ctrl+Alt+Shift+X).
`FakePluginHost.actionSpec(id)` returns the registered spec. Plugins declare `sdk = ">=0.11.0, <0.12"`.
```

- [ ] **Step 2: Plugin authoring**

Replace the Shortcuts bullet with:

```markdown
- **Shortcuts.** `withDefaultBinding` uses the `[keybindings]` syntax. The user's configuration
  wins, then Jasper's own shortcuts, then plugin defaults in load order; a default that loses is
  dropped and logged. Users rebind an action under `[keybindings]` with its quoted id. Outside macOS,
  `cmd` means Ctrl+Shift and a default naming cmd and shift also gets alt; give a different Linux and
  Windows key with `withDefaultBindings("cmd+s", "ctrl+s")`. `withContext(ShortcutContext.terminal())`,
  `content()`, `panel(id)` or `window(id)` limits where the key applies (SDK 0.11.0): the most specific
  context around focus wins, so a panel action may reuse a Global key, and a key whose action does not
  apply reaches the focused component.
```

- [ ] **Step 3: SDK architecture**

Before `## Plugins manager, install and restart`, add:

```markdown
## Shortcut contexts (SDK 0.11.0)

`ShortcutContext` (`global`, `terminal`, `content`, `panel(id)`, `window(id)`) travels with `ActionSpec`. `HostedUi`
rejects a panel or window id outside the plugin's namespace and maps the context onto the app's
`dev.jasper.app.commands.KeyContext` (content becomes the plugin's own tabs), which `ActionEntry` and
`KeyBindings.Extension` carry with the optional Linux/Windows default. `KeyBindings` keeps several actions on one
stroke when their contexts don't conflict (same context, or Global with Terminal). `WindowContent.dispatchShortcut`
resolves the stroke against the focus owner: an enabled action in the most specific containing context runs;
otherwise the key is not consumed. Window contexts apply once plugin windows get the Keymap (plan K4).
```

- [ ] **Step 4: App maintenance**

In `docs/app-maintenance.md` line 11, replace the sentence "Register a real Swing action, add its stable ID/keywords,
then define the default shortcut in ActionId and check platform resolution/collision validation in KeyBindings. If a
new action needs nonstandard platform modifiers, update KeyBindings.effectiveDefaultBinding as well." with:

```markdown
Register a real Swing action and add its stable ID and keywords. Then give it, in ActionId, both defaults (macOS, and
Linux/Windows naming ctrl, alt and shift literally; "none" for no default) and its KeyContext (Global, or Terminal
for pane-only actions). `KeymapDefaultsTest` holds the approved table and the platform principles; `KeyBindings`
rejects two defaults on one stroke in conflicting contexts.
```

- [ ] **Step 5: Run the full check and collect the counts**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

Collect the counts:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
for module in ["jasper-app", "jasper-terminal", "plugins/editor", "plugins/remote", "plugins/vault", "jasper-buddy",
               "plugins/history", "plugins/snippets", "plugins/sample", "jasper-sdk", "jasper-sdk-testkit"]:
    tests = failures = skipped = 0
    for f in pathlib.Path(module, "build/test-results/test").glob("*.xml"):
        r = ET.parse(f).getroot()
        tests += int(r.get("tests")); failures += int(r.get("failures")) + int(r.get("errors")); skipped += int(r.get("skipped"))
    print(f"{module}: {tests}/{failures}/{skipped}")
PY
```

- [ ] **Step 6: STATUS entry and plan banner**

Add this bullet first under `## Current state — 2026-09-30` in `docs/STATUS.md`. Put the counts from Step 5 in the
"green:" list, in the same `tests/failures/skipped` form as the other entries:

```markdown
- **Keymap defaults K1: contexts and defaults** (branch `claude/keymap-defaults`, spec
  `docs/superpowers/specs/2026-09-30-keymap-defaults-design.md`, plan `docs/superpowers/plans/2026-09-30-keymap-defaults-k1.md`):
  actions carry a context (Global, Terminal, a plugin's tabs, a panel, a window). The most specific applicable action
  takes a key, and a disabled or inapplicable one lets it reach the shell. Built-ins have explicit macOS and
  Linux/Windows defaults; plugin cmd+shift defaults get Alt outside macOS. Settings › Keymap has a Where column.
  SDK 0.11.0: `ShortcutContext`, `ActionSpec.withContext` / `withDefaultBindings`, `FakePluginHost.actionSpec`;
  bundled plugins declare `sdk = ">=0.11.0, <0.12"`; Remote 0.9.0, Vault 0.4.0. **Moved defaults** (macOS / Linux and
  Windows): Command Palette ⇧⌘P / Ctrl+Shift+P (was ⌘K / Ctrl+K); Clear Scrollback ⌘K, terminal only (was ⇧⌘K);
  Close Pane ⌘W / Ctrl+Shift+W, closing the tab with its last pane (was ⇧⌘W / Ctrl+Alt+Shift+W); Close Tab ⌥⌘W /
  Ctrl+Alt+Shift+W (was ⌘W / Ctrl+Shift+W); Rename Tab ⌘I / Ctrl+Shift+I (was F2); Reload Config unbound (was ⇧⌘R);
  Linux Next/Previous Tab Ctrl+Tab / Ctrl+Shift+Tab (was Ctrl+Shift+] / [); Linux Quit Ctrl+Alt+Shift+Q (was
  Ctrl+Shift+Q); SSH Sessions ⌥⌘1 / Alt+1 (was ⇧⌘S); new: SFTP ⌥⌘2 / Alt+2, Transfers ⌥⌘3 / Alt+3, Open SFTP Here
  ⇧⌘O / Ctrl+Alt+Shift+O, Lock Vault ⇧⌘L / Ctrl+Alt+Shift+L; Open Vault ⌘\ / Ctrl+Shift+\ (was F8). Deviations
  (plan banner): the SDK type is `ShortcutContext` (`ActionContext` was taken); Find/Copy/Paste stay Global until K3;
  palette hiding of owned-context actions, the testkit's context invocation and DEFAULT_DROPPED, and the Keymap's
  "overrides … here" wait for K3/K4; the docs sweep is K4. `./gradlew check` green: app …, terminal …, Editor …,
  Remote …, Vault …, Buddy …, History …, Snippets …, Sample …, SDK …, testkit …. Not merged. GUI checks pending with
  the user: ⇧⌘P / Ctrl+Shift+P opens the palette; ⌘K clears in a terminal and does nothing in the SSH Sessions panel's
  search; ⌘W closes a split pane, then the tab; on Linux Ctrl+K and F2 reach the shell and Ctrl+Tab switches tabs from
  a terminal; ⌥⌘1 / Alt+1 toggles SSH Sessions; the Keymap shows the Where column.
```

At the top of this plan, under the title, add:

```markdown
> **Status (2026-09-30):** executed on `claude/keymap-defaults`; see `docs/STATUS.md`. Deviations as listed below.
```

- [ ] **Step 7: Check the docs and source hygiene, then commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS.
Run the source-hygiene snippet from `AGENTS.md`.
Expected: no output.

```bash
git branch --show-current   # must print claude/keymap-defaults
git add jasper-sdk/README.md docs
git commit -m "Docs: SDK 0.11 shortcut contexts, maintenance note and keymap K1 status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
