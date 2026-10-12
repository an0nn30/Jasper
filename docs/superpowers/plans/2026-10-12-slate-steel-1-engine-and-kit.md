# Slate Steel Plan 1: engine and kit — Implementation Plan

> **Status (2026-10-12): complete** on `claude/slate-steel`, executed inline. Deviations from the text below, each
> recorded as a ruling in the executor's ledger: the primary button's drop is painted outside the face by its kit view
> (decision 8 amended; the in-bounds drop moved the mock-calibrated face edge); the UI architecture check is the root
> `verifyUiArchitecture` task; tool windows paint `surface.panel`, so a vendor `ToolWindow.background` no longer reaches
> them; and existing tests that pinned the old roles were moved to the new roles (expected values only).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Slate Steel (Dark and Light UI palettes, three terminal schemes, two derived editor schemes) into the theme engine from one bundled file, and move the kit's components onto `theme.md`'s tokens, alpha treatments and shadows — alongside the legacy theme pipeline, which Plans 2 and 3 replace and remove.

**Architecture:** `jasper-ui`'s JDK-only `dev.jasper.ui.theme` package gains a strict JSON reader (`ThemeJson`), a loader (`SlateSteel`) that turns the bundled `slate-steel.json` into `SemanticPalette`s, terminal palettes (`TerminalScheme`, `SchemeColors`), editor schemes (`EditorColors` and syntax styles) and full `GeneratedTheme`s. Until Plan 3 removes the legacy layer, a Slate Steel `GeneratedTheme` also carries a legacy token view derived from its tokens, so every existing consumer and FlatLaf's UI defaults work unchanged. Ten semantic roles are added to the engine and SDK enums (SDK 0.15.5). Kit components then move onto the roles `theme.md` names.

**Tech Stack:** Java 25 on JBR 25 (`./gradlew` only), Swing + FlatLaf 3.7 in `dev.jasper.ui.swing`, JUnit 5 + AssertJ.

**Spec:** [`docs/superpowers/specs/2026-10-12-jasper-slate-steel-themes-design.md`](../specs/2026-10-12-jasper-slate-steel-themes-design.md) (sections 3, 4, 7, 8, 10, 11, phasing 13.1). Colour reference: `~/Downloads/theme.md`, copied into the repository by Task 1.

## Global Constraints

- Work in the git worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay` on branch `claude/slate-steel`. Never build in `/Users/RQ7RQVF/projects/moray` (the user runs `:jasper-app:run` there). Never launch the GUI. Never push.
- Components reference token keys (`dev.jasper.ui.theme.SemanticColor`), never hex values. No task adds a colour hex literal to production Java. Colour values live only in `slate-steel.json` (legacy TOMLs stay until Plan 3).
- `#RRGGBB @ N%` means that colour at N% opacity over whatever is beneath it; the JSON uses 8-digit `#RRGGBBAA`.
- Dark and Light share the same keys and are resolved by key, never by assuming a lighter or darker direction. `hover.overlay` is white in Dark and black in Light; components apply its alpha.
- Opacity is applied once: a token that carries alpha is painted as given; component treatments (focus ring, error ring, hover, tiles) apply a kit metric to a base token.
- `dev.jasper.ui.theme` is JDK-only (`gradle/ui-architecture.gradle.kts`): no FlatLaf, no Swing, no other vendor classes. It may not depend on `themeio`/`themeimport`.
- The engine `SemanticColor` and the SDK `dev.jasper.sdk.ui.SemanticColor` stay identical in names **and order** (`KitValuesTest` compares them).
- SDK: `JasperSdk.VERSION = "0.15.5"`; every bundled manifest requires `sdk = ">=0.15.5, <0.16"`.
- Geometry stays as calibrated; only colours, alpha treatments and the shadows of spec section 8 change.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in source (AGENTS.md's Python check).
- Commit messages end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Tests: `./gradlew :jasper-ui:test --tests '<pattern>'`, `./gradlew :jasper-app:test --tests '<pattern>'`; read results from `*/build/test-results/test/*.xml` when a run fails.

## Decisions recorded by this plan (for the user's review)

These were open in the component audit; each is the plan's choice, recorded in the acceptance record by Task 15:

1. Manager-rail selected rows (Vault categories, file-chooser Places) take `surface.control.selected` (the Vault mock's grey "All Items" row), not `selection.bg`.
2. Tab dividers stay `border.window` (theme.md is silent); terminal split dividers take `border.window` too (Plan 1 maps the legacy divider token to it; Plan 3 moves the painter).
3. The equal-width tab strip gets no accent "active tab indicator": the workspace mocks show none (the active tab is the lighter `surface.terminal` slot). Inactive tabs gain the hover overlay.
4. The command palette gets no backdrop scrim: the palette mock leaves the window undimmed (pixel-identical to the plain workspace), although theme.md lists the palette among the scrim's users.
5. Chrome icons (title bar toolbar, rails) keep `text.primary`, as the workspace mocks show them bright; kit icons already tint `text.secondary`.
6. The segmented control's Light "soft shadow" (theme.md gives no values) is black at 12%, 1 px down, 2 px blur.
7. theme.md's "current breadcrumb" (`text.strong.secondary`) applies to the kit's breadcrumb control (file browsers).
   The Settings header's current crumb is the page title (a `WINDOW_TITLE` text the app owns) and stays
   `text.primary`, which is what the Settings mock paints (`#EFF1F4`, probed at 2x in `settings_dialog_mock.png`).
8. The primary button's 1 px drop is painted by its kit view outside the face, as CSS `box-shadow` does, so the face keeps
   its calibrated extent (amended during execution: the plan first drew it inside the bounds with a 1 px shorter face).

## Review Focus

1. A bundled file with an unknown key, a duplicate key, a missing role or a malformed colour must fail at load with a message naming the origin and the key — pinned in Tasks 2 and 4.
2. An editor reference that is unknown, uses `@` on the background, or has a share outside 1–100% must fail at load — pinned in Task 6.
3. A translucent workspace (window opacity below 1) must keep the tab strip at the window's alpha after the move to `surface.tabstrip`/`surface.terminal` — pinned in Task 10 (and the existing `WorkspaceGlassTest`).
4. Legacy themes, still active until Plan 2, must not paint opaque scrims once scrims are painted as given — pinned in Task 8 (legacy default black at 55%/25%, calibration TOMLs at 55%).
5. The Light variant, not only Dark, must generate and install into FlatLaf with its roles — pinned in Task 7.

---

### Task 1: Slate Steel reference copy and bundled file

**Files:**
- Create: `docs/design/theme-engine/slate-steel.md` (copy of `~/Downloads/theme.md`, extended)
- Create: `jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelFileTest.java`

**Interfaces:**
- Produces: the classpath resource `/dev/jasper/ui/theme/slate-steel.json` with root keys `ui` (`slate-steel-dark`, `slate-steel-light`), `terminal` (`slate-steel-dark`, `slate-steel-light`, `high-contrast`) and `editor` (`slate-steel-dark`, `slate-steel-light`).

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The bundled theme file is the colour reference's machine-readable block, so the two cannot drift. */
class SlateSteelFileTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();

    @Test void theBundledFileIsTheReferenceCopysJsonBlock() throws Exception {
        String reference = Files.readString(ROOT.resolve("docs/design/theme-engine/slate-steel.md"));
        var block = Pattern.compile("```json\\n(.*?)\\n```", Pattern.DOTALL).matcher(reference);
        assertThat(block.find()).as("the reference's machine-readable block").isTrue();
        String json = block.group(1);
        assertThat(block.find()).as("one machine-readable block").isFalse();
        String bundled;
        try (var in = SlateSteelFileTest.class.getResourceAsStream("/dev/jasper/ui/theme/slate-steel.json")) {
            assertThat(in).as("the bundled theme file").isNotNull();
            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(bundled.strip()).isEqualTo(json.strip());
        assertThat(json).contains("\"editor\"", "\"slate-steel-dark\"", "\"high-contrast\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelFileTest'`
Expected: FAIL (`docs/design/theme-engine/slate-steel.md` does not exist).

- [ ] **Step 3: Create the reference copy**

```bash
cp ~/Downloads/theme.md docs/design/theme-engine/slate-steel.md
```

Then edit `docs/design/theme-engine/slate-steel.md`:

1. In the opening list, after `2. **Terminal color schemes:** Slate Steel Dark, Slate Steel Light, and High Contrast.` add the line:
   `3. **Editor color schemes:** Slate Steel Dark and Slate Steel Light, derived from the colors above.`
2. Insert this section immediately before the `---` line that precedes `# 3. Machine-readable`:

```markdown
## Editor color schemes (derived)

Slate Steel Dark and Slate Steel Light each include an editor scheme, so the UI, the terminal and the editor match.
They are derived from the Slate Steel colors and add no new values: each entry names a token. `terminal.*` resolves in
the variant's own terminal scheme (Slate Steel Dark for Dark, Slate Steel Light for Light), any other key in the
variant's UI theme, and `key @ N%` is that color at N% over the editor background. The editor always follows the UI
theme; High Contrast has no editor scheme.

| Editor color | Slate Steel Dark | Slate Steel Light |
|---|---|---|
| Background, gutter | `terminal.bg` | `terminal.bg` |
| Text, variables | `terminal.fg` | `terminal.fg` |
| Caret | `terminal.cursor` | `terminal.cursor` |
| Selection | `terminal.selection` | `terminal.selection` |
| Current line | `hover.overlay @ 5%` | `hover.overlay @ 5%` |
| Line numbers | `text.tertiary` | `text.tertiary` |
| Keywords | `terminal.ansi.magenta` | `terminal.ansi.magenta` |
| Types, regular expressions | `terminal.ansi.cyan` | `terminal.ansi.cyan` |
| Functions | `terminal.ansi.blue` | `terminal.ansi.blue` |
| Strings | `terminal.ansi.green` | `terminal.ansi.green` |
| Numbers, constants, annotations, attributes | `terminal.ansi.yellow` | `terminal.ansi.yellow` |
| Tags, errors | `terminal.ansi.red` | `terminal.ansi.red` |
| Operators, punctuation | `text.secondary` | `text.secondary` |
| Comments, doc comments (italic) | `terminal.ansi.bright.black` | `terminal.ansi.white` |

Against the editor background, every text role is at least 4.5:1 (lowest: Light types, 4.87:1), comments at least
3:1 (5.89:1 Dark, 5.77:1 Light) and line numbers at least 3:1 (4.79:1 Dark, 4.24:1 Light).
```

3. Replace the whole ```` ```json ```` block with exactly:

```json
{
  "ui": {
    "slate-steel-dark": {
      "surface.app": "#1E2127", "surface.panel": "#272A31", "surface.panel.raised": "#2D3139",
      "surface.dialog": "#2D3139", "surface.card": "#272A31", "surface.sidebar": "#23262C",
      "surface.input": "#1F2228", "surface.field.raised": "#30343C", "surface.control": "#363B45",
      "surface.control.hover": "#3E444F", "surface.control.selected": "#3A3F4A", "surface.menu": "#30343C",
      "surface.terminal": "#23262C", "surface.tabstrip": "#1F2228", "surface.emptyTile": "#343841",
      "surface.scrim": "#0A0C108C",
      "border.window": "#3A3F49", "border.subtle": "#FFFFFF0F", "border.input": "#3B404A",
      "border.field.raised": "#474D59", "border.field.raised.hover": "#58606D", "border.control": "#474D59",
      "border.menu": "#4B515D", "border.error": "#E0605A",
      "text.primary": "#EFF1F4", "text.secondary": "#A7ADB8", "text.strong.secondary": "#D1D5DC",
      "text.tertiary": "#8B919C", "text.placeholder": "#6D737E", "text.field.raised": "#CDD2DA",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFA6", "text.link": "#8FB4DE",
      "text.link.hover": "#B0CBEA", "text.error": "#F07F78", "text.success": "#4FD06C",
      "accent": "#4E7DB4", "accent.hover": "#5C8BC2", "selection.bg": "#2E4664", "hover.overlay": "#FFFFFF",
      "segment.track": "#1F2228", "segment.selected.bg": "#474D59", "toggle.track.off": "#474D59", "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#363B45", "button.secondary.bg.hover": "#3E444F", "button.secondary.border": "#474D59",
      "status.success": "#4FD06C", "status.warning": "#F2C55C", "status.danger": "#F07F78", "status.idle": "#5B616C",
      "hue.blue": "#94B4D6", "hue.green": "#72DE8F", "hue.amber": "#F2C55C", "hue.purple": "#C8A4FF", "hue.neutral": "#D1D5DC"
    },
    "slate-steel-light": {
      "surface.app": "#E4E7EB", "surface.panel": "#F5F6F8", "surface.panel.raised": "#ECEEF1",
      "surface.dialog": "#F5F6F8", "surface.card": "#FFFFFF", "surface.sidebar": "#ECEEF1",
      "surface.input": "#FFFFFF", "surface.field.raised": "#FFFFFF", "surface.control": "#FFFFFF",
      "surface.control.hover": "#F0F2F5", "surface.control.selected": "#D3D8DE", "surface.menu": "#FFFFFF",
      "surface.terminal": "#FBFBFC", "surface.tabstrip": "#E9ECEF", "surface.emptyTile": "#E3E6EA",
      "surface.scrim": "#1C202740",
      "border.window": "#C6CBD2", "border.subtle": "#0000000F", "border.input": "#D2D6DC",
      "border.field.raised": "#C3C8D0", "border.field.raised.hover": "#A9AFB9", "border.control": "#C3C8D0",
      "border.menu": "#C9CED5", "border.error": "#C8423A",
      "text.primary": "#1C2027", "text.secondary": "#5D6470", "text.strong.secondary": "#383E48",
      "text.tertiary": "#727985", "text.placeholder": "#8E95A0", "text.field.raised": "#4A515C",
      "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#FFFFFFBF", "text.link": "#3F6FA8",
      "text.link.hover": "#2F5A8C", "text.error": "#B93A33", "text.success": "#2E9A4C",
      "accent": "#3F6FA8", "accent.hover": "#4A7BB5", "selection.bg": "#5482B9", "hover.overlay": "#000000",
      "segment.track": "#E4E7EB", "segment.selected.bg": "#FFFFFF", "toggle.track.off": "#C3C8D0", "toggle.knob": "#FFFFFF",
      "button.secondary.bg": "#FFFFFF", "button.secondary.bg.hover": "#F0F2F5", "button.secondary.border": "#C3C8D0",
      "status.success": "#2E9A4C", "status.warning": "#A8740F", "status.danger": "#C8423A", "status.idle": "#A8AEB7",
      "hue.blue": "#3F6FA8", "hue.green": "#2C8A49", "hue.amber": "#A8740F", "hue.purple": "#7B53BF", "hue.neutral": "#5D6470"
    }
  },
  "terminal": {
    "slate-steel-dark": {
      "bg": "#23262C", "fg": "#F4F6F8", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#3D5F8A",
      "black": "#3A3F4A", "red": "#FF928A", "green": "#5FE88A", "yellow": "#FFD75F",
      "blue": "#6CB6FF", "magenta": "#E99CFF", "cyan": "#4FE0E8", "white": "#D9DEE5",
      "bright.black": "#99A2B1", "bright.red": "#FFC6C0", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
      "bright.blue": "#B2D9FF", "bright.magenta": "#F6C8FF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
    },
    "slate-steel-light": {
      "bg": "#FBFBFC", "fg": "#1C2027", "fg.bold": "#000000", "cursor": "#1C2027", "selection": "#C7D8EE",
      "black": "#1C2027", "red": "#C8302C", "green": "#1F7A35", "yellow": "#8A6200",
      "blue": "#1F5FBF", "magenta": "#9B3FB5", "cyan": "#0F7A8A", "white": "#5D6470",
      "bright.black": "#6B7280", "bright.red": "#B8342F", "bright.green": "#23853C", "bright.yellow": "#946800",
      "bright.blue": "#2867C8", "bright.magenta": "#A847C2", "bright.cyan": "#127F91", "bright.white": "#000000"
    },
    "high-contrast": {
      "bg": "#000000", "fg": "#FFFFFF", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#264F9E",
      "black": "#4D4D4D", "red": "#FF7B72", "green": "#3CFF7A", "yellow": "#FFE14D",
      "blue": "#7CB8FF", "magenta": "#FF8CFF", "cyan": "#4DF0FF", "white": "#E6E6E6",
      "bright.black": "#A6A6A6", "bright.red": "#FFA8A2", "bright.green": "#9DFFBC", "bright.yellow": "#FFF08A",
      "bright.blue": "#B3D6FF", "bright.magenta": "#FFB8FF", "bright.cyan": "#A6F7FF", "bright.white": "#FFFFFF"
    }
  },
  "editor": {
    "slate-steel-dark": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
      "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
      "variable": "terminal.fg", "constant": "terminal.ansi.yellow", "number": "terminal.ansi.yellow",
      "string": "terminal.ansi.green", "comment": "terminal.ansi.bright.black, italic",
      "doc_comment": "terminal.ansi.bright.black, italic", "annotation": "terminal.ansi.yellow",
      "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
      "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
    },
    "slate-steel-light": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
      "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
      "variable": "terminal.fg", "constant": "terminal.ansi.yellow", "number": "terminal.ansi.yellow",
      "string": "terminal.ansi.green", "comment": "terminal.ansi.white, italic",
      "doc_comment": "terminal.ansi.white, italic", "annotation": "terminal.ansi.yellow",
      "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
      "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
    }
  }
}
```

4. Change the closing note `In the JSON, colors with transparency use 8-digit #RRGGBBAA hex. For example, #FFFFFF0F is white at 6%.` to add, as a second sentence: `The editor object holds the derived editor schemes as token references.`

- [ ] **Step 4: Write the bundled file from the reference's block**

```bash
mkdir -p jasper-ui/src/main/resources/dev/jasper/ui/theme
python3 -I - <<'PY'
import pathlib, re
text = pathlib.Path("docs/design/theme-engine/slate-steel.md").read_text(encoding="utf-8")
block = re.search(r"```json\n(.*?)\n```", text, re.S).group(1)
pathlib.Path("jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json").write_text(block + "\n", encoding="utf-8")
PY
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelFileTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add docs/design/theme-engine/slate-steel.md jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelFileTest.java
git commit -m "Slate Steel colour reference and bundled theme file

The user's theme.md becomes docs/design/theme-engine/slate-steel.md, extended
with derived editor schemes for Slate Steel Dark and Light (token references,
no new colours). The bundled slate-steel.json is its machine-readable block;
a test keeps the two identical.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: A strict theme JSON reader

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeJson.java`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/ThemeJsonTest.java`

**Interfaces:**
- Produces: `static Map<String, Object> ThemeJson.parse(String text, String origin)` (package-private): ordered `LinkedHashMap`s whose values are `String` or nested `Map<String, Object>`; anything else throws `ThemeFileException(origin, line, message)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThemeJsonTest {
    @Test void readsNestedObjectsOfStringsInOrder() {
        var root = ThemeJson.parse("""
            { "ui": { "b": "#000000", "a": "x \\"q\\" \\u0041" }, "z": {} }
            """, "t.json");
        assertThat(root.keySet()).containsExactly("ui", "z");
        @SuppressWarnings("unchecked") var ui = (Map<String, Object>) root.get("ui");
        assertThat(ui).containsExactly(Map.entry("b", "#000000"), Map.entry("a", "x \"q\" A"));
        assertThat(root.get("z")).isEqualTo(Map.of());
    }

    @Test void rejectsWhatTheThemeFileNeverHoldsNamingTheOriginAndLine() {
        for (var bad : List.of(
                Map.entry("{ \"a\": 1 }", "Expected a string or an object for \"a\""),
                Map.entry("{ \"a\": [\"x\"] }", "Expected a string or an object for \"a\""),
                Map.entry("{ \"a\": \"x\",\n  \"a\": \"y\" }", "t.json:2: Duplicate key \"a\""),
                Map.entry("{ \"a\": \"x\" } x", "Unexpected content after the theme object"),
                Map.entry("{ \"a\": \"x\" ", "Unexpected end"),
                Map.entry("{ \"a\": \"x\" \"b\": \"y\" }", "Expected ',' or '}'"),
                Map.entry("{ \"a\": \"\\q\" }", "Unknown escape \\q"))) {
            assertThatThrownBy(() -> ThemeJson.parse(bad.getKey(), "t.json")).as(bad.getKey())
                .isInstanceOf(ThemeFileException.class).hasMessageContaining(bad.getValue()).hasMessageStartingWith("t.json");
        }
    }

    @Test void readsTheBundledFile() throws Exception {
        try (var in = ThemeJsonTest.class.getResourceAsStream("/dev/jasper/ui/theme/slate-steel.json")) {
            var root = ThemeJson.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), "slate-steel.json");
            assertThat(root.keySet()).containsExactly("ui", "terminal", "editor");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*ThemeJsonTest'`
Expected: FAIL to compile (`ThemeJson` does not exist).

- [ ] **Step 3: Write the reader**

```java
package dev.jasper.ui.theme;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A strict reader for Jasper's bundled theme file: JSON objects whose values are strings or objects, nothing else.
 * Numbers, arrays, booleans, null, duplicate keys and trailing content are errors naming the origin and line. The
 * theme package is JDK-only, so it cannot use FlatLaf's parser.
 */
final class ThemeJson {
    private final String text, origin;
    private int at;

    private ThemeJson(String text, String origin) { this.text = text; this.origin = origin; }

    /** The top-level object, as ordered maps of strings and maps. */
    static Map<String, Object> parse(String text, String origin) {
        var reader = new ThemeJson(text, origin);
        reader.space();
        var root = reader.object();
        reader.space();
        if (reader.at != text.length()) throw reader.error("Unexpected content after the theme object");
        return root;
    }

    private Map<String, Object> object() {
        expect('{');
        var map = new LinkedHashMap<String, Object>();
        space();
        if (peek() == '}') { at++; return map; }
        while (true) {
            space();
            int keyAt = at;
            String key = string();
            space(); expect(':'); space();
            char first = peek();
            Object value = first == '{' ? object() : first == '"' ? string() : null;
            if (value == null) throw error("Expected a string or an object for \"" + key + "\"");
            if (map.putIfAbsent(key, value) != null) { at = keyAt; throw error("Duplicate key \"" + key + "\""); }
            space();
            char next = peek();
            if (next == '}') { at++; return map; }
            if (next != ',') throw error("Expected ',' or '}'");
            at++;
        }
    }

    private String string() {
        expect('"');
        var out = new StringBuilder();
        while (true) {
            if (at >= text.length()) throw error("Unterminated string");
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c < 0x20) { at--; throw error("Control character in a string"); }
            if (c != '\\') { out.append(c); continue; }
            if (at >= text.length()) throw error("Unterminated escape");
            char escape = text.charAt(at++);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (at + 4 > text.length()) throw error("Short unicode escape");
                    try { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                    catch (NumberFormatException failure) { throw error("Bad unicode escape"); }
                    at += 4;
                }
                default -> throw error("Unknown escape \\" + escape);
            }
        }
    }

    private void space() {
        while (at < text.length() && " \t\n\r".indexOf(text.charAt(at)) >= 0) at++;
    }

    private char peek() {
        if (at >= text.length()) throw error("Unexpected end");
        return text.charAt(at);
    }

    private void expect(char c) {
        if (peek() != c) throw error("Expected '" + c + "'");
        at++;
    }

    private ThemeFileException error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(at, text.length()); i++) if (text.charAt(i) == '\n') line++;
        return new ThemeFileException(origin, line, message);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-ui:test --tests '*ThemeJsonTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeJson.java jasper-ui/src/test/java/dev/jasper/ui/theme/ThemeJsonTest.java
git commit -m "A strict JSON reader for the bundled theme file

The JDK-only theme package cannot use FlatLaf's parser. ThemeJson reads
objects of strings and objects only, and reports duplicate keys, other value
types and trailing content with the origin and line.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Ten new semantic roles and SDK 0.15.5

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SemanticColor.java` (append ten constants)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SemanticColor.java` (same ten, same order)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SemanticDefaults.java:57-91` (`base()` cases)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java:8`, `ui/KitBoundaryTest.java:5`, `files/FilesValuesTest.java:54`, `palette/PaletteValuesTest.java:70`
- Modify: `plugins/{editor,fileserver,history,indexer,remote,sample,snippets,vault}/src/main/resources/plugin.toml:5`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java:8`, `jasper-ui/src/test/java/dev/jasper/ui/theme/SemanticPaletteTest.java:68`
- Modify: `jasper-sdk/README.md:38`, `docs/plugin-authoring.md` (after the 0.15.4 section)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SemanticPaletteTest.java` (new test)

**Interfaces:**
- Produces (both enums, appended after `BUTTON_SECONDARY_BG_HOVER`, in this order): `SURFACE_FIELD_RAISED("surface.field.raised")`, `SURFACE_CONTROL_SELECTED("surface.control.selected")`, `SURFACE_TABSTRIP("surface.tabstrip")`, `SURFACE_EMPTY_TILE("surface.emptyTile")`, `BORDER_FIELD_RAISED("border.field.raised")`, `BORDER_FIELD_RAISED_HOVER("border.field.raised.hover")`, `TEXT_STRONG_SECONDARY("text.strong.secondary")`, `TEXT_FIELD_RAISED("text.field.raised")`, `SEGMENT_TRACK("segment.track")`, `BUTTON_SECONDARY_BORDER("button.secondary.border")`. The engine enum then has 64 roles.

- [ ] **Step 1: Write the failing test** — add to `SemanticPaletteTest`:

```java
    /** Legacy themes, still installed until Plan 2, give each new role the colour its component paints today. */
    @Test void legacyThemesGiveTheTenNewRolesTheirCurrentSources() {
        var theme = dev.jasper.ui.testsupport.KitFixtures.dark();
        var semantic = theme.semantic(); var tokens = theme.tokens();
        assertThat(semantic.color(SemanticColor.SURFACE_FIELD_RAISED)).isEqualTo(tokens.get(Token.INPUT_BACKGROUND));
        assertThat(semantic.color(SemanticColor.SURFACE_CONTROL_SELECTED)).isEqualTo(tokens.get(Token.BUTTON_BACKGROUND));
        assertThat(semantic.color(SemanticColor.SURFACE_TABSTRIP)).isEqualTo(tokens.get(Token.TAB_BAR));
        assertThat(semantic.color(SemanticColor.SURFACE_EMPTY_TILE)).isEqualTo(tokens.get(Token.BUTTON_BACKGROUND));
        assertThat(semantic.color(SemanticColor.BORDER_FIELD_RAISED)).isEqualTo(tokens.get(Token.BORDER));
        assertThat(semantic.color(SemanticColor.BORDER_FIELD_RAISED_HOVER)).isEqualTo(tokens.get(Token.BUTTON_BORDER));
        assertThat(semantic.color(SemanticColor.TEXT_STRONG_SECONDARY)).isEqualTo(tokens.get(Token.TEXT));
        assertThat(semantic.color(SemanticColor.TEXT_FIELD_RAISED)).isEqualTo(tokens.get(Token.TEXT));
        assertThat(semantic.color(SemanticColor.SEGMENT_TRACK)).isEqualTo(tokens.get(Token.INPUT_BACKGROUND));
        assertThat(semantic.color(SemanticColor.BUTTON_SECONDARY_BORDER)).isEqualTo(tokens.get(Token.BUTTON_BORDER));
        assertThat(SemanticColor.of("surface.emptyTile")).contains(SemanticColor.SURFACE_EMPTY_TILE);
    }
```

and change line 68's `assertThat(SemanticColor.values()).hasSize(54);` to `hasSize(64)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*SemanticPaletteTest'`
Expected: FAIL to compile (`SURFACE_FIELD_RAISED` is not a constant).

- [ ] **Step 3: Add the roles to both enums**

In `jasper-ui/src/main/java/dev/jasper/ui/theme/SemanticColor.java` and `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SemanticColor.java`, replace

```java
    BUTTON_SECONDARY_BG_HOVER("button.secondary.bg.hover");
```

with

```java
    BUTTON_SECONDARY_BG_HOVER("button.secondary.bg.hover"),
    SURFACE_FIELD_RAISED("surface.field.raised"),
    SURFACE_CONTROL_SELECTED("surface.control.selected"),
    SURFACE_TABSTRIP("surface.tabstrip"),
    SURFACE_EMPTY_TILE("surface.emptyTile"),
    BORDER_FIELD_RAISED("border.field.raised"),
    BORDER_FIELD_RAISED_HOVER("border.field.raised.hover"),
    TEXT_STRONG_SECONDARY("text.strong.secondary"),
    TEXT_FIELD_RAISED("text.field.raised"),
    SEGMENT_TRACK("segment.track"),
    BUTTON_SECONDARY_BORDER("button.secondary.border");
```

- [ ] **Step 4: Give legacy themes the roles** — in `SemanticDefaults.base(...)`, add these cases before the `case BORDER_IDENTITY, HUE_BLUE_SURFACE, …` throw case:

```java
            // The ten Slate Steel roles: for legacy themes, the colour their component paints today.
            case SURFACE_FIELD_RAISED, SEGMENT_TRACK -> legacy.get(Token.INPUT_BACKGROUND);
            case SURFACE_CONTROL_SELECTED, SURFACE_EMPTY_TILE -> legacy.get(Token.BUTTON_BACKGROUND);
            case SURFACE_TABSTRIP -> legacy.get(Token.TAB_BAR);
            case BORDER_FIELD_RAISED -> legacy.get(Token.BORDER);
            case BORDER_FIELD_RAISED_HOVER, BUTTON_SECONDARY_BORDER -> legacy.get(Token.BUTTON_BORDER);
            case TEXT_STRONG_SECONDARY, TEXT_FIELD_RAISED -> legacy.get(Token.TEXT);
```

- [ ] **Step 5: SDK 0.15.5** — make these exact edits:
  - `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`: `"0.15.4"` → `"0.15.5"`.
  - The four SDK tests named in **Files**: `"0.15.4"` → `"0.15.5"`.
  - Each of the eight `plugin.toml` files: `sdk = ">=0.15.4, <0.16"` → `sdk = ">=0.15.5, <0.16"`.
  - `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java:8`: `hasSize(54)` → `hasSize(64)`.
  - `jasper-sdk/README.md:38`: `` `sdk = ">=0.15.4, <0.16"` (0.15.4 adds the modal overlay presentation, `` → `` `sdk = ">=0.15.5, <0.16"` (0.15.5 adds ten semantic colours for Slate Steel; 0.15.4 added the modal overlay presentation, ``
  - `docs/plugin-authoring.md`: after the "Modal overlays (SDK 0.15.4)" section add:

```markdown
### Slate Steel colours (SDK 0.15.5)

`SemanticColor` gains ten roles for the Slate Steel theme: `SURFACE_FIELD_RAISED`, `SURFACE_CONTROL_SELECTED`,
`SURFACE_TABSTRIP`, `SURFACE_EMPTY_TILE`, `BORDER_FIELD_RAISED`, `BORDER_FIELD_RAISED_HOVER`,
`TEXT_STRONG_SECONDARY`, `TEXT_FIELD_RAISED`, `SEGMENT_TRACK` and `BUTTON_SECONDARY_BORDER`. Read them with
`context.appearance().color(role)` like any other role; the kit already paints its controls with them. Declare
`sdk = ">=0.15.5, <0.16"` to use them; every bundled manifest does.
```

  and change the earlier sentence ``Declare `sdk = ">=0.15.4, <0.16"` to use it; every bundled manifest does.`` to ``Declare `sdk = ">=0.15.4, <0.16"` or later to use it.``

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-ui:test --tests '*SemanticPaletteTest' :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*KitValuesTest' --tests '*Plugin*'`
Expected: PASS. (`DialogSpec`'s and `FakePluginHost`'s `@since 0.15.4` stay: they record when the overlay arrived.)

- [ ] **Step 7: Commit**

```bash
git add -A jasper-ui/src jasper-sdk plugins/*/src/main/resources/plugin.toml docs/plugin-authoring.md
git commit -m "Ten semantic roles for Slate Steel, SDK 0.15.5

surface.field.raised, surface.control.selected, surface.tabstrip,
surface.emptyTile, border.field.raised, border.field.raised.hover,
text.strong.secondary, text.field.raised, segment.track and
button.secondary.border join the engine and SDK enums in the same order.
Legacy themes give each the colour its component paints today.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Slate Steel UI palettes

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelPaletteTest.java`

**Interfaces:**
- Consumes: `ThemeJson.parse` (Task 2); the resource (Task 1); the 64 roles (Task 3).
- Produces: `public final class SlateSteel` with `public static SlateSteel bundled()`, `static SlateSteel parse(String json, String origin)`, `public SemanticPalette palette(boolean dark)`, and package-private `static Color color(String text, String origin, String where)` (`#RRGGBB` or `#RRGGBBAA`). Later tasks add methods to this class.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SlateSteelPaletteTest {
    static String bundledJson() throws Exception {
        try (var in = SlateSteelPaletteTest.class.getResourceAsStream(SlateSteel.RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void bothVariantsCoverEveryRoleByValueOrRule() {
        for (boolean dark : new boolean[] {true, false}) {
            var palette = SlateSteel.bundled().palette(dark);
            for (var role : SemanticColor.values()) assertThat(palette.color(role)).as("%s %s", dark, role.key()).isNotNull();
            assertThat(palette.color(SemanticColor.BORDER_FOCUS)).isEqualTo(palette.color(SemanticColor.ACCENT));
            assertThat(palette.color(SemanticColor.SURFACE_STATUS)).isEqualTo(palette.color(SemanticColor.SURFACE_APP));
            assertThat(palette.color(SemanticColor.BORDER_IDENTITY)).isEqualTo(palette.color(SemanticColor.BORDER_CONTROL));
            assertThat(palette.color(SemanticColor.HUE_BLUE_SURFACE)).isEqualTo(palette.color(SemanticColor.HUE_BLUE));
            assertThat(palette.color(SemanticColor.HUE_NEUTRAL_SURFACE)).isEqualTo(palette.color(SemanticColor.HUE_NEUTRAL));
        }
    }

    @Test void valuesAreTheFilesIncludingTheirAlpha() {
        var dark = SlateSteel.bundled().palette(true); var light = SlateSteel.bundled().palette(false);
        assertThat(dark.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0x1E2127));
        assertThat(light.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0xE4E7EB));
        assertThat(dark.color(SemanticColor.SURFACE_SCRIM)).isEqualTo(new Color(0x0A, 0x0C, 0x10, 0x8C));
        assertThat(light.color(SemanticColor.SURFACE_SCRIM)).isEqualTo(new Color(0x1C, 0x20, 0x27, 0x40));
        assertThat(dark.color(SemanticColor.BORDER_SUBTLE)).isEqualTo(new Color(255, 255, 255, 0x0F));
        assertThat(dark.color(SemanticColor.HOVER_OVERLAY)).isEqualTo(Color.WHITE);
        assertThat(light.color(SemanticColor.HOVER_OVERLAY)).isEqualTo(Color.BLACK);
        assertThat(dark.color(SemanticColor.SURFACE_TABSTRIP)).isEqualTo(new Color(0x1F2228));
        assertThat(light.color(SemanticColor.TEXT_STRONG_SECONDARY)).isEqualTo(new Color(0x383E48));
    }

    @Test void aFileTheEngineCannotUseFailsNamingTheKey() throws Exception {
        String json = bundledJson();
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"surface.app\": \"#1E2127\",", "\"surface.app\": \"#1E2127\", \"surface.nope\": \"#000000\","), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("ui.slate-steel-dark").hasMessageContaining("Unknown colour \"surface.nope\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"surface.panel\": \"#272A31\", ", ""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("Missing colour \"surface.panel\"");
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"#1E2127\"", "\"#1E21\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("surface.app").hasMessageContaining("#RRGGBB");
        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"slate-steel-light\": {\n      \"surface.app\"", "\"slate-steel-pale\": {\n      \"surface.app\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("ui");
        assertThatThrownBy(() -> SlateSteel.parse("{ \"ui\": {} }", "t.json")).isInstanceOf(ThemeFileException.class)
            .hasMessageContaining("Expected the objects");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelPaletteTest'`
Expected: FAIL to compile (`SlateSteel` does not exist).

- [ ] **Step 3: Write the loader**

```java
package dev.jasper.ui.theme;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Jasper's theme, Slate Steel, from its bundled file {@code slate-steel.json} (the colour reference
 * {@code docs/design/theme-engine/slate-steel.md}'s machine-readable block): the Dark and Light UI palettes, the
 * terminal schemes and the derived editor schemes. Roles the file does not list follow fixed rules, never colour
 * values: {@code border.focus} is {@code accent}, {@code surface.status} is {@code surface.app}, {@code border.identity}
 * is {@code border.control} and each {@code hue.<name>.surface} is its hue (the kit applies the tile alpha). A role with
 * neither a value nor a rule, an unknown key or a malformed colour fails loading. Immutable.
 */
public final class SlateSteel {
    static final String RESOURCE = "/dev/jasper/ui/theme/slate-steel.json";
    static final String DARK = "slate-steel-dark", LIGHT = "slate-steel-light";
    private static final Set<SemanticColor> RULED = Set.of(SemanticColor.BORDER_FOCUS, SemanticColor.SURFACE_STATUS,
        SemanticColor.BORDER_IDENTITY, SemanticColor.HUE_BLUE_SURFACE, SemanticColor.HUE_GREEN_SURFACE,
        SemanticColor.HUE_AMBER_SURFACE, SemanticColor.HUE_PURPLE_SURFACE, SemanticColor.HUE_NEUTRAL_SURFACE);

    private final String origin;
    private final Map<String, Object> ui, terminal, editor;
    private final SemanticPalette dark, light;

    private SlateSteel(Map<String, Object> root, String origin) {
        this.origin = origin;
        objects(root, "", List.of("ui", "terminal", "editor"));
        ui = objects(root.get("ui"), "ui", List.of(DARK, LIGHT));
        terminal = objects(root.get("terminal"), "terminal", List.of(DARK, LIGHT, "high-contrast"));
        editor = objects(root.get("editor"), "editor", List.of(DARK, LIGHT));
        dark = palette(DARK);
        light = palette(LIGHT);
    }

    /** The bundled theme, read once. */
    public static SlateSteel bundled() { return Bundled.THEME; }

    private static final class Bundled {
        static final SlateSteel THEME = load();
        private static SlateSteel load() {
            try (var in = SlateSteel.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing bundled theme " + RESOURCE);
                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "slate-steel.json");
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }

    /** A theme file in the bundled file's shape; {@code origin} names it in errors. */
    static SlateSteel parse(String json, String origin) { return new SlateSteel(ThemeJson.parse(json, origin), origin); }

    /** The Dark or Light UI palette. */
    public SemanticPalette palette(boolean dark) { return dark ? this.dark : light; }

    private SemanticPalette palette(String variant) {
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) ui.get(variant);
        String where = "ui." + variant;
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        values.forEach((key, value) -> {
            var role = SemanticColor.of(key).filter(r -> !RULED.contains(r))
                .orElseThrow(() -> error(where + ": Unknown colour \"" + key + "\""));
            if (!(value instanceof String text)) throw error(where + "." + key + ": Expected a colour");
            colors.put(role, color(text, origin, where + "." + key));
        });
        for (var role : SemanticColor.values()) {
            if (RULED.contains(role)) continue;
            if (!colors.containsKey(role)) throw error(where + ": Missing colour \"" + role.key() + "\"");
        }
        colors.put(SemanticColor.BORDER_FOCUS, colors.get(SemanticColor.ACCENT));
        colors.put(SemanticColor.SURFACE_STATUS, colors.get(SemanticColor.SURFACE_APP));
        colors.put(SemanticColor.BORDER_IDENTITY, colors.get(SemanticColor.BORDER_CONTROL));
        colors.put(SemanticColor.HUE_BLUE_SURFACE, colors.get(SemanticColor.HUE_BLUE));
        colors.put(SemanticColor.HUE_GREEN_SURFACE, colors.get(SemanticColor.HUE_GREEN));
        colors.put(SemanticColor.HUE_AMBER_SURFACE, colors.get(SemanticColor.HUE_AMBER));
        colors.put(SemanticColor.HUE_PURPLE_SURFACE, colors.get(SemanticColor.HUE_PURPLE));
        colors.put(SemanticColor.HUE_NEUTRAL_SURFACE, colors.get(SemanticColor.HUE_NEUTRAL));
        return new SemanticPalette(colors);
    }

    /** {@code #RRGGBB}, or {@code #RRGGBBAA} with alpha; {@code where} names the entry in errors. */
    static Color color(String text, String origin, String where) {
        if (text.matches("#[0-9A-Fa-f]{6}")) return new Color(Integer.parseInt(text.substring(1), 16));
        if (text.matches("#[0-9A-Fa-f]{8}")) {
            long value = Long.parseLong(text.substring(1), 16);
            return new Color((int) (value >> 24) & 0xFF, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF);
        }
        throw new ThemeFileException(origin, 0, where + ": Expected #RRGGBB or #RRGGBBAA, got \"" + text + "\"");
    }

    /** The object at {@code value}, whose keys must be exactly {@code keys}, each an object. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> objects(Object value, String where, List<String> keys) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(Set.copyOf(keys)))
            throw error((where.isEmpty() ? "" : where + ": ") + "Expected the objects " + keys);
        for (String key : keys) if (!(map.get(key) instanceof Map)) throw error(where + "." + key + ": Expected an object");
        return (Map<String, Object>) map;
    }

    ThemeFileException error(String message) { return new ThemeFileException(origin, 0, Objects.requireNonNull(message)); }

    Map<String, Object> terminalTable() { return terminal; }
    Map<String, Object> editorTable() { return editor; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelPaletteTest' :jasper-ui:verifyUiArchitecture`
Expected: PASS (`verifyUiArchitecture`, from `gradle/ui-architecture.gradle.kts`, keeps `dev.jasper.ui.theme` JDK-only).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelPaletteTest.java
git commit -m "Slate Steel UI palettes from the bundled file

SlateSteel reads slate-steel.json into the Dark and Light semantic palettes.
Roles the file does not list follow rules (border.focus = accent,
surface.status = surface.app, border.identity = border.control, hue surfaces
= their hue); unknown keys, missing roles and malformed colours fail loading.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Terminal schemes

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalScheme.java`, `jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeColors.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java` (add `terminal(...)`)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelTerminalTest.java`

**Interfaces:**
- Produces: `public enum TerminalScheme { SLATE_STEEL_DARK, SLATE_STEEL_LIGHT, HIGH_CONTRAST; String key(); static Optional<TerminalScheme> of(String); static TerminalScheme matching(boolean dark) }`; `public record SchemeColors(TerminalColors colors, Color boldForeground)`; `public SchemeColors SlateSteel.terminal(TerminalScheme scheme)`. ANSI order: black, red, green, yellow, blue, magenta, cyan, white, then the bright eight (indices 8–15).

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlateSteelTerminalTest {
    @Test void eachSchemeIsItsPaletteInAnsiOrder() {
        var dark = SlateSteel.bundled().terminal(TerminalScheme.SLATE_STEEL_DARK);
        assertThat(dark.colors().background()).isEqualTo(new Color(0x23262C));
        assertThat(dark.colors().foreground()).isEqualTo(new Color(0xF4F6F8));
        assertThat(dark.colors().cursor()).isEqualTo(Color.WHITE);
        assertThat(dark.colors().selection()).isEqualTo(new Color(0x3D5F8A));
        assertThat(dark.boldForeground()).isEqualTo(Color.WHITE);
        assertThat(dark.colors().ansi().get(1)).isEqualTo(new Color(0xFF928A));
        assertThat(dark.colors().ansi().get(8)).isEqualTo(new Color(0x99A2B1));
        assertThat(dark.colors().ansi().get(15)).isEqualTo(Color.WHITE);
        var light = SlateSteel.bundled().terminal(TerminalScheme.SLATE_STEEL_LIGHT);
        assertThat(light.boldForeground()).isEqualTo(Color.BLACK);
        assertThat(light.colors().ansi().get(7)).isEqualTo(new Color(0x5D6470));
        assertThat(SlateSteel.bundled().terminal(TerminalScheme.HIGH_CONTRAST).colors().background()).isEqualTo(Color.BLACK);
        assertThat(SlateSteel.bundled().palette(true).color(SemanticColor.SURFACE_TERMINAL)).isEqualTo(dark.colors().background());
    }

    @Test void schemesHaveKeysAndFollowTheUiVariantByDefault() {
        assertThat(TerminalScheme.of("high-contrast")).contains(TerminalScheme.HIGH_CONTRAST);
        assertThat(TerminalScheme.of("nord")).isEmpty();
        assertThat(TerminalScheme.matching(true)).isEqualTo(TerminalScheme.SLATE_STEEL_DARK);
        assertThat(TerminalScheme.matching(false)).isEqualTo(TerminalScheme.SLATE_STEEL_LIGHT);
    }

    /**
     * theme.md's contrast targets against each scheme's own background. The exempt entries are theme.md's exceptions
     * (black as a background, the dim tones) and the two colours shipped as authored below target; each exempt entry
     * must really be below target, so the list cannot outlive a fix.
     */
    @Test void eachSchemeMeetsItsContrastTargetsWithTheNamedExceptions() {
        record Target(double normal, double bright, Set<Integer> exempt) { }
        var targets = Map.of(
            TerminalScheme.SLATE_STEEL_DARK, new Target(7, 9.5, Set.of(0, 8)),
            TerminalScheme.SLATE_STEEL_LIGHT, new Target(4.9, 4.5, Set.of(6)),
            TerminalScheme.HIGH_CONTRAST, new Target(8, 12, Set.of(0, 8, 9)));
        targets.forEach((scheme, target) -> {
            var colors = SlateSteel.bundled().terminal(scheme).colors();
            for (int i = 0; i < 16; i++) {
                double ratio = ColorMath.contrast(colors.ansi().get(i), colors.background()), floor = i < 8 ? target.normal() : target.bright();
                if (target.exempt().contains(i)) assertThat(ratio).as("%s ansi %s is a named exception", scheme, i).isLessThan(floor);
                else assertThat(ratio).as("%s ansi %s", scheme, i).isGreaterThanOrEqualTo(floor);
            }
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelTerminalTest'`
Expected: FAIL to compile (`TerminalScheme` does not exist).

- [ ] **Step 3: Write the scheme types and the loader method**

`TerminalScheme.java`:

```java
package dev.jasper.ui.theme;

import java.util.Optional;

/** The terminal colour schemes Jasper ships; each UI variant pairs with its own by default. */
public enum TerminalScheme {
    SLATE_STEEL_DARK("slate-steel-dark"), SLATE_STEEL_LIGHT("slate-steel-light"), HIGH_CONTRAST("high-contrast");

    private final String key;
    TerminalScheme(String key) { this.key = key; }

    /** The scheme's key in the theme file and in configuration, for example {@code high-contrast}. */
    public String key() { return key; }

    /** The scheme with this key. */
    public static Optional<TerminalScheme> of(String key) {
        for (var scheme : values()) if (scheme.key.equals(key)) return Optional.of(scheme);
        return Optional.empty();
    }

    /** The scheme that matches a UI variant: Slate Steel Dark with Dark, Slate Steel Light with Light. */
    public static TerminalScheme matching(boolean dark) { return dark ? SLATE_STEEL_DARK : SLATE_STEEL_LIGHT; }
}
```

`SchemeColors.java`:

```java
package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * A terminal scheme's colours: the terminal palette and the foreground for bold or emphasized text.
 * @param colors background, foreground, cursor, selection and the 16 ANSI colours
 * @param boldForeground the default foreground for bold text
 */
public record SchemeColors(TerminalColors colors, Color boldForeground) {
    public SchemeColors { Objects.requireNonNull(colors, "colors"); Objects.requireNonNull(boldForeground, "boldForeground"); }
}
```

In `SlateSteel`, add the field `private final Map<TerminalScheme, SchemeColors> schemes;`, set it in the constructor after `terminal = …` with

```java
        var parsed = new EnumMap<TerminalScheme, SchemeColors>(TerminalScheme.class);
        for (var scheme : TerminalScheme.values()) parsed.put(scheme, scheme(scheme));
        schemes = Map.copyOf(parsed);
```

and add:

```java
    static final List<String> ANSI = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");
    static final List<String> TERMINAL_KEYS = terminalKeys();
    private static List<String> terminalKeys() {
        var keys = new java.util.ArrayList<>(List.of("bg", "fg", "fg.bold", "cursor", "selection"));
        keys.addAll(ANSI);
        for (String name : ANSI) keys.add("bright." + name);
        return List.copyOf(keys);
    }

    /** A terminal scheme's colours. */
    public SchemeColors terminal(TerminalScheme scheme) { return schemes.get(Objects.requireNonNull(scheme)); }

    private SchemeColors scheme(TerminalScheme scheme) {
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) terminal.get(scheme.key());
        String where = "terminal." + scheme.key();
        if (!values.keySet().equals(Set.copyOf(TERMINAL_KEYS))) throw error(where + ": Expected the colours " + TERMINAL_KEYS);
        java.util.function.Function<String, Color> get = key -> {
            if (!(values.get(key) instanceof String text)) throw error(where + "." + key + ": Expected a colour");
            return color(text, origin, where + "." + key);
        };
        var ansi = new java.util.ArrayList<Color>(16);
        for (String name : ANSI) ansi.add(get.apply(name));
        for (String name : ANSI) ansi.add(get.apply("bright." + name));
        return new SchemeColors(new TerminalColors(get.apply("bg"), get.apply("fg"), get.apply("cursor"), get.apply("selection"), ansi),
            get.apply("fg.bold"));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelTerminalTest' --tests '*SlateSteelPaletteTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalScheme.java jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeColors.java jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelTerminalTest.java
git commit -m "Slate Steel terminal schemes

Slate Steel Dark, Slate Steel Light and High Contrast become terminal palettes
with a bold foreground, read from the bundled file. A test holds each to
theme.md's contrast targets, with its exceptions named.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Editor schemes

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/theme/EditorColors.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java` (add `editor(...)`, `syntax(...)`)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelEditorTest.java`

**Interfaces:**
- Produces: `public record EditorColors(Color background, Color foreground, Color caret, Color selection, Color currentLine, Color gutter, Color lineNumbers)`; `public EditorColors SlateSteel.editor(boolean dark)`; `public Map<SyntaxRole, SyntaxStyle> SlateSteel.syntax(boolean dark)` (all 16 roles).
- Value grammar: `<reference>[ @ <N>%][, italic][, bold]`. A reference is a UI key (`text.secondary`, including ruled roles) or `terminal.bg | terminal.fg | terminal.fg.bold | terminal.cursor | terminal.selection | terminal.ansi.<name> | terminal.ansi.bright.<name>`, resolved in the variant's own scheme. `@ N%` (1–100) is the reference at N% over the editor background; the background itself cannot use it.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class SlateSteelEditorTest {
    @Test void theEditorSchemesAreTheirVariantsTokens() {
        var dark = SlateSteel.bundled().editor(true);
        assertThat(dark.background()).isEqualTo(new Color(0x23262C)).isEqualTo(dark.gutter());
        assertThat(dark.foreground()).isEqualTo(new Color(0xF4F6F8));
        assertThat(dark.caret()).isEqualTo(Color.WHITE);
        assertThat(dark.selection()).isEqualTo(new Color(0x3D5F8A));
        assertThat(dark.currentLine()).isEqualTo(ColorMath.mix(Color.WHITE, new Color(0x23262C), .05));
        assertThat(dark.lineNumbers()).isEqualTo(new Color(0x8B919C));
        var syntax = SlateSteel.bundled().syntax(true);
        assertThat(syntax).containsOnlyKeys(SyntaxRole.values());
        assertThat(syntax.get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xE99CFF), false, false));
        assertThat(syntax.get(SyntaxRole.COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x99A2B1), false, true));
        assertThat(syntax.get(SyntaxRole.OPERATOR).color()).isEqualTo(new Color(0xA7ADB8));
        var light = SlateSteel.bundled().syntax(false);
        assertThat(light.get(SyntaxRole.DOC_COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x5D6470), false, true));
        assertThat(SlateSteel.bundled().editor(false).currentLine()).isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xFBFBFC), .05));
    }

    @Test void everyRoleReadsOnItsBackground() {
        for (boolean dark : new boolean[] {true, false}) {
            var editor = SlateSteel.bundled().editor(dark);
            SlateSteel.bundled().syntax(dark).forEach((role, style) -> {
                boolean comment = role == SyntaxRole.COMMENT || role == SyntaxRole.DOC_COMMENT;
                assertThat(ColorMath.contrast(style.color(), editor.background())).as("%s %s", dark, role)
                    .isGreaterThanOrEqualTo(comment ? 3 : 4.5);
            });
            assertThat(ColorMath.contrast(editor.foreground(), editor.background())).isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(editor.lineNumbers(), editor.background())).isGreaterThanOrEqualTo(3);
        }
    }

    @Test void aReferenceTheEngineCannotResolveFailsLoading() throws Exception {
        String json = SlateSteelPaletteTest.bundledJson();
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"terminal.ansi.pink\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("editor.slate-steel-dark.keyword").hasMessageContaining("terminal.ansi.pink");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"currentLine\": \"hover.overlay @ 5%\"", "\"currentLine\": \"hover.overlay @ 105%\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("currentLine");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"background\": \"terminal.bg\"", "\"background\": \"terminal.bg @ 50%\""), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("background");
        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("terminal.ansi.bright.black, italic", "terminal.ansi.bright.black, wavy"), "t.json"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("wavy");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelEditorTest'`
Expected: FAIL to compile (`EditorColors` does not exist).

- [ ] **Step 3: Write the record and the resolution**

`EditorColors.java`:

```java
package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Objects;

/**
 * An editor scheme's surfaces.
 * @param background document background
 * @param foreground plain text
 * @param caret caret
 * @param selection selected text background
 * @param currentLine current line background
 * @param gutter line-number gutter background
 * @param lineNumbers line-number foreground
 */
public record EditorColors(Color background, Color foreground, Color caret, Color selection, Color currentLine,
                           Color gutter, Color lineNumbers) {
    public EditorColors {
        Objects.requireNonNull(background); Objects.requireNonNull(foreground); Objects.requireNonNull(caret);
        Objects.requireNonNull(selection); Objects.requireNonNull(currentLine); Objects.requireNonNull(gutter);
        Objects.requireNonNull(lineNumbers);
    }
}
```

In `SlateSteel`, add the fields and parse them at the end of the constructor, after `light = palette(LIGHT);` (Task 5 set `schemes` earlier, right after `terminal = …`, so the editor can resolve `terminal.*`):

```java
    private final EditorColors darkEditor, lightEditor;
    private final Map<SyntaxRole, SyntaxStyle> darkSyntax, lightSyntax;
```

```java
        var darkStyles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        darkEditor = editor(true, darkStyles);
        darkSyntax = Map.copyOf(darkStyles);
        var lightStyles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        lightEditor = editor(false, lightStyles);
        lightSyntax = Map.copyOf(lightStyles);
```

and add:

```java
    static final List<String> EDITOR_SURFACES = List.of("background", "foreground", "caret", "selection", "currentLine", "gutter", "lineNumbers");

    /** The Dark or Light editor scheme's surfaces. */
    public EditorColors editor(boolean dark) { return dark ? darkEditor : lightEditor; }

    /** The Dark or Light editor scheme's syntax styles, one for every role. */
    public Map<SyntaxRole, SyntaxStyle> syntax(boolean dark) { return dark ? darkSyntax : lightSyntax; }

    private EditorColors editor(boolean dark, Map<SyntaxRole, SyntaxStyle> syntax) {
        String variant = dark ? DARK : LIGHT, where = "editor." + variant;
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) editor.get(variant);
        var expected = new java.util.HashSet<>(EDITOR_SURFACES);
        for (var role : SyntaxRole.values()) expected.add(role.key());
        if (!values.keySet().equals(expected)) throw error(where + ": Expected the entries " + EDITOR_SURFACES + " and every syntax role");
        java.util.function.Function<String, String> text = key -> {
            if (!(values.get(key) instanceof String value)) throw error(where + "." + key + ": Expected a reference");
            return value;
        };
        Color background = reference(text.apply("background"), dark, null, where + ".background").color();
        java.util.function.Function<String, Color> surface = key -> {
            var resolved = reference(text.apply(key), dark, background, where + "." + key);
            if (resolved.bold() || resolved.italic()) throw error(where + "." + key + ": A surface takes no style");
            return resolved.color();
        };
        for (var role : SyntaxRole.values()) syntax.put(role, reference(text.apply(role.key()), dark, background, where + "." + role.key()));
        return new EditorColors(background, surface.apply("foreground"), surface.apply("caret"), surface.apply("selection"),
            surface.apply("currentLine"), surface.apply("gutter"), surface.apply("lineNumbers"));
    }

    /** {@code <reference>[ @ <N>%][, italic][, bold]} resolved for a variant; a share lays it over {@code background}. */
    private SyntaxStyle reference(String value, boolean dark, Color background, String where) {
        String[] parts = value.split(",");
        boolean bold = false, italic = false;
        for (int i = 1; i < parts.length; i++) {
            switch (parts[i].strip()) {
                case "italic" -> italic = true;
                case "bold" -> bold = true;
                default -> throw error(where + ": Unknown style \"" + parts[i].strip() + "\"");
            }
        }
        String[] shared = parts[0].split("@", -1);
        if (shared.length > 2) throw error(where + ": Expected one share, got \"" + value + "\"");
        String key = shared[0].strip();
        Color base = key.startsWith("terminal.") ? terminalReference(key, dark, where)
            : SemanticColor.of(key).map(role -> palette(dark).color(role))
                .orElseThrow(() -> error(where + ": Unknown reference \"" + key + "\""));
        if (shared.length == 2) {
            String share = shared[1].strip();
            if (background == null) throw error(where + ": The background cannot be laid over itself");
            if (!share.matches("\\d{1,3}%")) throw error(where + ": Expected a share such as 5%, got \"" + share + "\"");
            int percent = Integer.parseInt(share.substring(0, share.length() - 1));
            if (percent < 1 || percent > 100) throw error(where + ": A share is from 1% to 100%, got " + share);
            base = ColorMath.mix(base, background, percent / 100.0);
        }
        return new SyntaxStyle(base, bold, italic);
    }

    private Color terminalReference(String key, boolean dark, String where) {
        var colors = terminal(TerminalScheme.matching(dark));
        String name = key.substring("terminal.".length());
        switch (name) {
            case "bg": return colors.colors().background();
            case "fg": return colors.colors().foreground();
            case "fg.bold": return colors.boldForeground();
            case "cursor": return colors.colors().cursor();
            case "selection": return colors.colors().selection();
            default:
        }
        if (name.startsWith("ansi.bright.") && ANSI.contains(name.substring("ansi.bright.".length())))
            return colors.colors().ansi().get(8 + ANSI.indexOf(name.substring("ansi.bright.".length())));
        if (name.startsWith("ansi.") && ANSI.contains(name.substring("ansi.".length())))
            return colors.colors().ansi().get(ANSI.indexOf(name.substring("ansi.".length())));
        throw error(where + ": Unknown reference \"" + key + "\"");
    }
```

(`SyntaxStyle` doubles as the resolved reference: colour plus style flags.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteel*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/EditorColors.java jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelEditorTest.java
git commit -m "Slate Steel editor schemes

The bundled file's derived editor schemes resolve their token references in
each variant's UI palette and terminal scheme: surfaces, all sixteen syntax
roles and their styles. Unknown references, bad shares and styles fail
loading; a test holds every role to its contrast floor.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Slate Steel generated themes

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java` (add `generated(...)`, the legacy view and UI keys)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelGeneratedTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/swing/SlateSteelInstallTest.java`

**Interfaces:**
- Consumes: `palette`, `terminal`, `editor`, `syntax` (Tasks 4–6); package-private `UiTemplate.FLATLAF`, `UiTemplate.JASPER`, `UiTemplate.hex(Color)`.
- Produces: `public GeneratedTheme SlateSteel.generated(boolean dark)` — name `"Slate Steel"`, author `"Jasper"`, the variant's palette, syntax and matching terminal scheme, opaque glass, no icons, a complete legacy token map and FlatLaf UI keys. Two calls with the same variant return the same instance.

- [ ] **Step 1: Write the failing tests**

`SlateSteelGeneratedTest.java`:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SlateSteelGeneratedTest {
    @Test void eachVariantIsACompleteGeneratedTheme() {
        for (boolean dark : new boolean[] {true, false}) {
            var steel = SlateSteel.bundled();
            var theme = steel.generated(dark);
            assertThat(theme).isSameAs(steel.generated(dark));
            assertThat(theme.name()).isEqualTo("Slate Steel");
            assertThat(theme.dark()).isEqualTo(dark);
            assertThat(theme.semantic()).isEqualTo(steel.palette(dark));
            assertThat(theme.terminal()).isEqualTo(steel.terminal(TerminalScheme.matching(dark)).colors());
            assertThat(theme.syntax()).isEqualTo(steel.syntax(dark));
            assertThat(theme.glass()).isEqualTo(GlassLevels.OPAQUE);
            assertThat(theme.tokens()).as("the legacy view, until Plan 3").containsOnlyKeys(Token.values());
            var editor = steel.editor(dark);
            assertThat(theme.tokens().get(Token.EDITOR_BACKGROUND)).isEqualTo(editor.background());
            assertThat(theme.tokens().get(Token.EDITOR_LINE_NUMBERS)).isEqualTo(editor.lineNumbers());
            assertThat(theme.tokens().get(Token.SURFACE)).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_PANEL));
            assertThat(theme.tokens().get(Token.TAB_BAR)).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_TABSTRIP));
            assertThat(theme.tokens().get(Token.DIVIDER)).isEqualTo(theme.semantic().color(SemanticColor.BORDER_WINDOW));
            assertThat(theme.uiKeys()).containsKeys("*.background", "Panel.background", "Jasper.editorBackground");
            assertThat(theme.uiKeys().get("Panel.background")).isEqualTo(ColorMath.hex(theme.tokens().get(Token.SURFACE)));
        }
    }
}
```

`SlateSteelInstallTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.Test;

class SlateSteelInstallTest {
    @Test void eachVariantInstallsIntoFlatLafWithItsRoles() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[] {true, false}) {
                    var theme = SlateSteel.bundled().generated(dark);
                    FlatLafInstaller.install(theme);
                    assertThat(UIManager.get("Jasper.installedTheme")).isSameAs(theme);
                    assertThat(UIManager.getColor("Panel.background")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_PANEL));
                    assertThat(UIManager.getColor("Jasper.semantic.surface.tabstrip")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_TABSTRIP));
                    assertThat(UIManager.getColor("Jasper.semantic.surface.scrim").getAlpha()).isEqualTo(dark ? 0x8C : 0x40);
                }
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteelGeneratedTest' --tests '*SlateSteelInstallTest'`
Expected: FAIL to compile (`generated` does not exist).

- [ ] **Step 3: Assemble the generated themes** — in `SlateSteel`, add fields `private final GeneratedTheme darkTheme, lightTheme;`, set them last in the constructor (`darkTheme = generate(true); lightTheme = generate(false);`), and add:

```java
    /** The Dark or Light variant as a complete generated theme. */
    public GeneratedTheme generated(boolean dark) { return dark ? darkTheme : lightTheme; }

    private GeneratedTheme generate(boolean dark) {
        var palette = palette(dark);
        var scheme = terminal(TerminalScheme.matching(dark));
        var tokens = legacy(palette, scheme, editor(dark));
        return new GeneratedTheme("Slate Steel", dark, "Jasper", tokens, syntax(dark), scheme.colors(), uiKeys(tokens),
            Map.of(), GlassLevels.OPAQUE, palette);
    }

    /**
     * The legacy token view of a variant, so consumers of {@link GeneratedTheme#tokens()} and the FlatLaf template keep
     * working until Plan 3 removes the legacy layer. Every token is one of the variant's colours.
     */
    private static Map<Token, Color> legacy(SemanticPalette p, SchemeColors scheme, EditorColors editor) {
        var t = new EnumMap<Token, Color>(Token.class);
        t.put(Token.SURFACE, p.color(SemanticColor.SURFACE_PANEL));
        t.put(Token.SURFACE_RAISED, p.color(SemanticColor.SURFACE_PANEL_RAISED));
        t.put(Token.SURFACE_SUNKEN, p.color(SemanticColor.SURFACE_CARD));
        t.put(Token.TITLE_BAR, p.color(SemanticColor.SURFACE_APP));
        t.put(Token.STATUS_BAR, p.color(SemanticColor.SURFACE_STATUS));
        t.put(Token.SIDEBAR, p.color(SemanticColor.SURFACE_SIDEBAR));
        t.put(Token.TEXT, p.color(SemanticColor.TEXT_PRIMARY));
        t.put(Token.TEXT_MUTED, p.color(SemanticColor.TEXT_SECONDARY));
        t.put(Token.TEXT_DISABLED, p.color(SemanticColor.TEXT_PLACEHOLDER));
        t.put(Token.TEXT_ON_ACCENT, p.color(SemanticColor.TEXT_ONACCENT));
        t.put(Token.LINK, p.color(SemanticColor.TEXT_LINK));
        t.put(Token.BORDER, p.color(SemanticColor.BORDER_WINDOW));
        t.put(Token.SEPARATOR, p.color(SemanticColor.BORDER_SUBTLE));
        t.put(Token.DIVIDER, p.color(SemanticColor.BORDER_WINDOW));
        t.put(Token.FOCUS_RING, p.color(SemanticColor.BORDER_FOCUS));
        t.put(Token.HOVER, p.color(SemanticColor.SURFACE_CONTROL_HOVER));
        t.put(Token.PRESSED, p.color(SemanticColor.SURFACE_CONTROL_SELECTED));
        t.put(Token.SELECTION, p.color(SemanticColor.SELECTION_BG));
        t.put(Token.SELECTION_TEXT, p.color(SemanticColor.TEXT_ONACCENT));
        t.put(Token.SELECTION_INACTIVE, p.color(SemanticColor.SURFACE_CONTROL_SELECTED));
        t.put(Token.TEXT_SELECTION, scheme.colors().selection());
        t.put(Token.TEXT_SELECTION_TEXT, p.color(SemanticColor.TEXT_PRIMARY));
        t.put(Token.TAB_BAR, p.color(SemanticColor.SURFACE_TABSTRIP));
        t.put(Token.TAB_ACTIVE, p.color(SemanticColor.SURFACE_TERMINAL));
        t.put(Token.TAB_ACTIVE_TEXT, p.color(SemanticColor.TEXT_PRIMARY));
        t.put(Token.TAB_ACTIVE_BORDER, p.color(SemanticColor.BORDER_WINDOW));
        t.put(Token.TAB_HOVER, p.color(SemanticColor.SURFACE_CONTROL_HOVER));
        t.put(Token.TAB_UNDERLINE, p.color(SemanticColor.ACCENT));
        t.put(Token.TAB_UNDERLINE_INACTIVE, p.color(SemanticColor.TEXT_TERTIARY));
        t.put(Token.INPUT_BACKGROUND, p.color(SemanticColor.SURFACE_INPUT));
        t.put(Token.BUTTON_BACKGROUND, p.color(SemanticColor.BUTTON_SECONDARY_BG));
        t.put(Token.BUTTON_BORDER, p.color(SemanticColor.BUTTON_SECONDARY_BORDER));
        t.put(Token.BUTTON_TEXT, p.color(SemanticColor.TEXT_STRONG_SECONDARY));
        t.put(Token.BUTTON_DEFAULT, p.color(SemanticColor.ACCENT));
        t.put(Token.SCROLLBAR_THUMB, p.color(SemanticColor.BORDER_CONTROL));
        t.put(Token.SCROLLBAR_TRACK, p.color(SemanticColor.SURFACE_PANEL));
        t.put(Token.ACCENT, p.color(SemanticColor.ACCENT));
        t.put(Token.SUCCESS, p.color(SemanticColor.STATUS_SUCCESS));
        t.put(Token.WARNING, p.color(SemanticColor.STATUS_WARNING));
        t.put(Token.ERROR, p.color(SemanticColor.STATUS_DANGER));
        // The find bar's no-match tint: the error border laid lightly over the input surface.
        t.put(Token.ERROR_BACKGROUND, ColorMath.mix(p.color(SemanticColor.BORDER_ERROR), p.color(SemanticColor.SURFACE_INPUT), .16));
        t.put(Token.EDITOR_BACKGROUND, editor.background());
        t.put(Token.EDITOR_FOREGROUND, editor.foreground());
        t.put(Token.EDITOR_CARET, editor.caret());
        t.put(Token.EDITOR_SELECTION, editor.selection());
        t.put(Token.EDITOR_CURRENT_LINE, editor.currentLine());
        t.put(Token.EDITOR_GUTTER, editor.gutter());
        t.put(Token.EDITOR_LINE_NUMBERS, editor.lineNumbers());
        return t;
    }

    /** The FlatLaf and Jasper UI keys of a token view, as the legacy generator writes them. */
    private static Map<String, Object> uiKeys(Map<Token, Color> tokens) {
        var keys = new java.util.LinkedHashMap<String, Object>();
        UiTemplate.FLATLAF.forEach((key, value) -> keys.put(key, value instanceof Token token ? UiTemplate.hex(tokens.get(token)) : value));
        UiTemplate.JASPER.forEach((key, token) -> keys.put(key, UiTemplate.hex(tokens.get(token))));
        return keys;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests '*SlateSteel*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelGeneratedTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/SlateSteelInstallTest.java
git commit -m "Slate Steel generated themes

Each variant becomes a complete GeneratedTheme: its palette, matching terminal
scheme, editor syntax and FlatLaf keys. Until Plan 3 removes the legacy layer
it also carries a legacy token view derived from its tokens, so existing
consumers and the FlatLaf template work unchanged. Both variants install.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Opacity applied once

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (remove `OVERLAY_SCRIM_PERCENT`; add halo and tile metrics)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java:133-145` (legacy scrim default)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ModalOverlay.java:260-262`, `ThemePainting.java:102`, `Switches.java:86,95`, `Indicators.java:22,38`, `PickerMenus.java:44`, `Choices.java` (add `hueSurface`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java:205`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/BodyScrim.java:42`
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/gallery/calibration.toml:21`, `jasper-app/src/test/resources/dev/jasper/app/plugins/desktop-picker.toml:23`, `jasper-app/src/test/resources/dev/jasper/app/appearance/{desktop-server,desktop-tunnel,desktop-host}.toml:23`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/AlphaOnceTest.java`; update `ModalOverlayTest.java:88-95`

**Interfaces:**
- Produces: `DesignMetrics.Role.FOCUS_HALO_PERCENT_DARK(32)`, `FOCUS_HALO_PERCENT_LIGHT(25)`, `ERROR_HALO_PERCENT(22)`, `TILE_PERCENT_DARK(17)`, `TILE_PERCENT_LIGHT(13)`; `static Color Choices.hueSurface(KitContext, Nodes.Hue)`; `static double ThemePainting.tileShare(KitContext)` (the tile alpha as a 0–1 share).

- [ ] **Step 1: Write the failing tests**

`AlphaOnceTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import org.junit.jupiter.api.Test;

/** spec 3.3: a token's own alpha is painted as given; component treatments apply one kit metric to a base token. */
class AlphaOnceTest {
    @Test void legacyThemesGetATranslucentScrimSoItCanBePaintedAsGiven() {
        assertThat(KitFixtures.dark().semantic().color(SemanticColor.SURFACE_SCRIM)).isEqualTo(ColorMath.opacity(Color.BLACK, .55));
        assertThat(KitFixtures.light().semantic().color(SemanticColor.SURFACE_SCRIM)).isEqualTo(ColorMath.opacity(Color.BLACK, .25));
    }

    @Test void focusHalosAndTilesUseTheirVariantsShare() {
        var dark = KitFixtures.context();
        assertThat(ThemePainting.focusHaloShare(dark, false)).isEqualTo(.32);
        assertThat(ThemePainting.focusHaloShare(dark, true)).isEqualTo(.22);
        assertThat(ThemePainting.tileShare(dark)).isEqualTo(.17);
        var light = KitFixtures.context(KitFixtures.light());
        try {
            assertThat(ThemePainting.focusHaloShare(light, false)).isEqualTo(.25);
            assertThat(ThemePainting.tileShare(light)).isEqualTo(.13);
            assertThat(Choices.hueSurface(light, dev.jasper.ui.model.Nodes.Hue.BLUE)).isEqualTo(light.theme().semantic().color(SemanticColor.HUE_BLUE_SURFACE));
        } finally { KitViewTest.release(light); }
        KitViewTest.release(dark);
    }
}
```

Add to `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java`:

```java
    /** A context over {@code theme} for both routes; caller owns and closes the binding and icon registry; EDT. */
    public static KitContext context(GeneratedTheme theme) {
        return new KitContext(new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }),
            ThemeRoute.WORKSPACE, DesignMetrics.DEFAULT, new IconRegistry(), () -> false);
    }
```

In `ModalOverlayTest.theCardFadesUpSixteenIntoPlaceOverTheEntranceAsTheScrimDims`, replace the 40% assertion

```java
            assertThat(dimmed.getAlpha()).as("the scrim dims everything else at 40%").isEqualTo(102);
```

with

```java
            assertThat(dimmed.getAlpha()).as("the scrim dims everything else at its own alpha").isEqualTo(scrim.getAlpha());
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests '*AlphaOnceTest' --tests '*ModalOverlayTest'`
Expected: FAIL (`focusHaloShare` does not exist).

- [ ] **Step 3: Metrics** — in `DesignMetrics`, delete `OVERLAY_SCRIM_PERCENT(40), ` from the overlay line, and add after the overlay lines:

```java
        // theme.md's alpha treatments on base tokens: the focus ring (about 32% in Dark), the error ring, and category
        // tiles and chips (16–18% in Dark, 12–14% in Light).
        FOCUS_HALO_PERCENT_DARK(32), FOCUS_HALO_PERCENT_LIGHT(25), ERROR_HALO_PERCENT(22), TILE_PERCENT_DARK(17), TILE_PERCENT_LIGHT(13),
```

- [ ] **Step 4: Legacy scrim default** — in `ThemeGenerator.Generation.semanticPalette(...)`, immediately after `var overrides = new EnumMap<SemanticColor,Color>(SemanticColor.class);` (so a vendor key or an authored `[semantic]` scrim, applied below it, still wins) add:

```java
            // A scrim is painted as given, so a theme that names none dims at theme.md's strengths.
            overrides.put(SemanticColor.SURFACE_SCRIM, ColorMath.opacity(java.awt.Color.BLACK, dark ? .55 : .25));
```

and in the five calibration TOMLs change `"surface.scrim" = "#0A0C10"` to `"surface.scrim" = "#0A0C108C"`.

- [ ] **Step 5: Paint scrims as given**
  - `ModalOverlay.paintComponent`: replace `g.setColor(ColorMath.opacity(scrim, context.metrics().value(DesignMetrics.Role.OVERLAY_SCRIM_PERCENT) / 100.0 * shown));` with `g.setColor(ColorMath.opacity(scrim, shown));` and its javadoc phrase "the scrim lifts" stays.
  - `BodyScrim.java:42`: replace `g.setColor(ColorMath.opacity(ThemeZone.color(this, SemanticColor.SURFACE_SCRIM), .35));` with `g.setColor(ThemeZone.color(this, SemanticColor.SURFACE_SCRIM));` and drop the now-unused `ColorMath` import.
  - `Switches.java:95` (knob shadow, a shadow and so black, not a theme colour): replace `ColorMath.opacity(ThemePainting.color(context, SemanticColor.SURFACE_SCRIM), .15)` with `ColorMath.opacity(Color.BLACK, .15)`.

- [ ] **Step 6: Halos and tiles** — add to `ThemePainting`:

```java
    /** The focus ring's share of {@code border.focus} (about 32% in Dark, 25% in Light), or the error ring's of {@code border.error}. */
    static double focusHaloShare(KitContext context, boolean error) {
        return context.metrics().value(error ? DesignMetrics.Role.ERROR_HALO_PERCENT
            : context.theme().dark() ? DesignMetrics.Role.FOCUS_HALO_PERCENT_DARK : DesignMetrics.Role.FOCUS_HALO_PERCENT_LIGHT) / 100.0;
    }

    /** A category tile's or chip's share of its {@code hue.<name>.surface}: 17% in Dark, 13% in Light. */
    static double tileShare(KitContext context) {
        return context.metrics().value(context.theme().dark() ? DesignMetrics.Role.TILE_PERCENT_DARK : DesignMetrics.Role.TILE_PERCENT_LIGHT) / 100.0;
    }
```

  - `ThemePainting.java:102`: replace `halo.error() ? .22 : .25)` with `focusHaloShare(halo.context(), halo.error()))`.
  - `Switches.java:86`: replace `error ? .22 : .25)` with `ThemePainting.focusHaloShare(context, error))`.
  - Add to `Choices` (next to `hue(...)`):

```java
    /** A hue's tile surface role colour, the base a tile or chip applies {@link ThemePainting#tileShare} to. */
    static Color hueSurface(KitContext context, Nodes.Hue hue) {
        return ThemePainting.color(context, switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE_SURFACE; case GREEN -> SemanticColor.HUE_GREEN_SURFACE; case AMBER -> SemanticColor.HUE_AMBER_SURFACE;
            case PURPLE -> SemanticColor.HUE_PURPLE_SURFACE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL_SURFACE;
        });
    }
```

  - `Indicators.ChipLabel`: add a field `Color fill;`; in `ChipControl.refresh()` after `label.setForeground(Choices.hue(context(), chip.hue()));` add `((ChipLabel) label).fill = Choices.hueSurface(context(), chip.hue());`; in `ChipLabel.paintComponent` replace `g.setColor(ColorMath.opacity(getForeground(), .16));` with `g.setColor(ColorMath.opacity(fill == null ? getForeground() : fill, ThemePainting.tileShare(context)));`.
  - `PickerMenus.java:44`: replace `ColorMath.opacity(active?ThemePainting.color(c,SemanticColor.TEXT_ONACCENT):Choices.hue(c,hue),.18)` with `ColorMath.opacity(active?ThemePainting.color(c,SemanticColor.TEXT_ONACCENT):Choices.hueSurface(c,hue),ThemePainting.tileShare(c))`.
  - `IconRegistry.java:205`: the palette-only `tile(...)` overloads pass `theme == null`, so the variant comes from the theme when there is one and otherwise from the palette's panel surface. Before the `return painted(...)` line add
    ```java
        // A palette-only tile has no theme: decide the variant as ordinary(...) does, from the panel surface.
        boolean darkTile = theme != null ? theme.dark() : ColorMath.luminance(palette.color(SemanticColor.SURFACE_PANEL)) < .5;
    ```
    and replace `ColorMath.opacity(fill, .18)` with `ColorMath.opacity(fill, DesignMetrics.DEFAULT.value(darkTile ? DesignMetrics.Role.TILE_PERCENT_DARK : DesignMetrics.Role.TILE_PERCENT_LIGHT) / 100.0)` (import `dev.jasper.ui.metrics.DesignMetrics` if absent).

- [ ] **Step 7: Run tests**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests '*BodyScrim*' --tests '*Sheet*' --tests '*WindowOverlay*' --tests '*VaultSheetRenderTest' --tests '*IconRegistry*' --tests '*Identity*'`
Expected: PASS. Tests that asserted the old 18%/16% tile alpha or the 25% halo in Dark must be updated to `ThemePainting.tileShare`/`focusHaloShare` values; change only the expected numbers, never the treatment.

- [ ] **Step 8: Commit**

```bash
git add -A jasper-ui/src jasper-app/src
git commit -m "Opacity applied once

Scrims are painted as their token gives them; legacy themes default to black
at 55%/25% so nothing turns opaque. The focus ring applies about 32% of
border.focus in Dark and 25% in Light, the error ring 22%, and category tiles
and chips 17%/13% of their hue surface. The switch knob's shadow is black.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Title bar, rails and tool windows on their tokens

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeChromePainting.java:26,56-79,201`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java:89`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/InteractiveRows.java:1235`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowSurface.java:12`, `ToolWindowHeader.java:97`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ChromeRolesTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java` (marker fixture), `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowHeaderTest.java` (new test)

**Interfaces:**
- Produces: `KitFixtures.marked()` (a dark fixture whose every role has its own colour) and `KitFixtures.marker(SemanticColor)`, used by Tasks 10–13.

- [ ] **Step 1: Write the marker fixture and failing tests** — add to `KitFixtures`:

```java
    /** The dark fixture with every semantic role in its own colour, so a painted pixel names the role it came from. */
    public static GeneratedTheme marked() {
        var base = dark();
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, marker(role));
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), base.glass(), new SemanticPalette(values));
    }

    /** {@code role}'s marker colour in {@link #marked()}: opaque, its red channel unique to the role. */
    public static java.awt.Color marker(SemanticColor role) {
        return new java.awt.Color(16 + 3 * role.ordinal(), 64 + role.ordinal(), 200 - role.ordinal());
    }
```

(import `dev.jasper.ui.theme.SemanticColor` and `dev.jasper.ui.theme.SemanticPalette` if absent.)

`ChromeRolesTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** spec 7: the title bar's command field, rails and tool windows paint theme.md's roles. */
class ChromeRolesTest {
    static final Icon NONE = new Icon() {
        @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) { }
        @Override public int getIconWidth() { return 0; }
        @Override public int getIconHeight() { return 0; }
    };

    static BufferedImage search(boolean hover) {
        var field = new JPanel(); field.setSize(200, 24);
        var image = new BufferedImage(200, 24, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.search(field, g, KitFixtures.marked(), hover, false, "", "", NONE, null); } finally { g.dispose(); }
        return image;
    }

    @Test void theCommandFieldIsARaisedFieldWhoseEdgeAloneAnswersHover() {
        for (boolean hover : new boolean[] {false, true}) {
            var image = search(hover);
            assertThat(image.getRGB(100, 12)).as("fill, hover %s", hover).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_FIELD_RAISED).getRGB());
            assertThat(image.getRGB(100, 0)).as("edge, hover %s", hover).isEqualTo(KitFixtures.marker(
                hover ? SemanticColor.BORDER_FIELD_RAISED_HOVER : SemanticColor.BORDER_FIELD_RAISED).getRGB());
        }
    }

    @Test void selectedToolbarActionsAndRailRowsUseTheSelectedControlSurface() {
        var button = new javax.swing.JToggleButton(); button.setIcon(NONE); button.setSize(28, 28); button.setSelected(true);
        var image = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.action(button, g, KitFixtures.marked(), false, null); } finally { g.dispose(); }
        assertThat(image.getRGB(14, 14)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_CONTROL_SELECTED).getRGB());
    }
}
```

Add to `ToolWindowHeaderTest`:

```java
    /** The class runs on the EDT (EdtTestExtension) with the module theme installed. */
    @Test void toolWindowsSitOnThePanelSurface() {
        var header = new ToolWindowHeader("Sessions", javax.swing.JPopupMenu::new, () -> { });
        assertThat(header.getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(header, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
        var root = new javax.swing.JPanel();
        ToolWindowSurface.apply(root);
        assertThat(root.getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(root, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
        assertThat(dev.jasper.app.platform.ThemeZone.color(root, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL))
            .as("the module theme tells panel from sidebar").isNotEqualTo(dev.jasper.app.platform.ThemeZone.color(root, dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests '*ChromeRolesTest' :jasper-app:test --tests '*ToolWindowHeaderTest'`
Expected: FAIL (fill is `SURFACE_INPUT`; selected action is `SEGMENT_SELECTED_BG`; header is `SURFACE_SIDEBAR`).

- [ ] **Step 3: Implement**
  - `NativeChromePainting.search`: replace `g.setColor(palette.color(hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_INPUT));` with `g.setColor(palette.color(SemanticColor.SURFACE_FIELD_RAISED));`; replace `g.setColor(palette.color(SemanticColor.BORDER_INPUT));` with `g.setColor(palette.color(hover ? SemanticColor.BORDER_FIELD_RAISED_HOVER : SemanticColor.BORDER_FIELD_RAISED));`; replace the context text's `g.setColor(palette.color(SemanticColor.TEXT_PRIMARY));` with `g.setColor(palette.color(SemanticColor.TEXT_FIELD_RAISED));`; in the hint's `SurfaceContrast.on(...)` replace `hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_INPUT` with `SemanticColor.SURFACE_FIELD_RAISED`.
  - `NativeChromePainting.action`: replace `palette.color(SemanticColor.SEGMENT_SELECTED_BG)` with `palette.color(SemanticColor.SURFACE_CONTROL_SELECTED)`.
  - `NativeChromePainting.connectedDot`: replace `SemanticColor.SURFACE_SIDEBAR` with `SemanticColor.SURFACE_PANEL`, and its javadoc "on the sidebar surface" with "on the panel surface".
  - `NativeControlStyles.java:89`: replace `palette.color(SemanticColor.SEGMENT_SELECTED_BG)` with `palette.color(SemanticColor.SURFACE_CONTROL_SELECTED)`.
  - `InteractiveRows.java:1235`: replace `owner.column == 0 ? SemanticColor.SURFACE_CONTROL : SemanticColor.SELECTION_BG` with `owner.column == 0 ? SemanticColor.SURFACE_CONTROL_SELECTED : SemanticColor.SELECTION_BG`.
  - `ToolWindowSurface.java:12` and `ToolWindowHeader.java:97`: replace `SemanticColor.SURFACE_SIDEBAR` with `SemanticColor.SURFACE_PANEL`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-ui:test --tests '*ChromeRolesTest' --tests '*NativeChromePaintingTest' --tests '*InteractiveRowsTest' :jasper-app:test --tests '*ToolWindow*' --tests '*TitleSearch*' --tests '*Workspace*'`
Expected: PASS. A test asserting the old role (for example a manager rail row painting `SURFACE_CONTROL`) changes its expected role to the new one.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-ui/src jasper-app/src
git commit -m "Title bar, rails and tool windows on theme.md's tokens

The command field is a raised field (fill, edge, hover edge and label), the
selected rail item and toolbar action take surface.control.selected, as do
manager-rail rows (Vault categories, Places), and tool windows sit on
surface.panel.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: The tab strip on its tokens

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeChromePainting.java:127-160`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (remove `TAB_SELECTED_LIFT_DARK`, `TAB_SELECTED_LIFT_LIGHT`, `TAB_IDLE_SHADE_DARK`, `TAB_IDLE_SHADE_LIGHT`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTabs.java:139-152`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/swing/NativeChromePaintingTest.java:15-37`, `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java:62-63`

**Interfaces:**
- Produces: `public static Color NativeChromePainting.tabSurface(GeneratedTheme workspace, Color base, boolean selected)` (the slot's role at `base`'s alpha) and `public static void tabStrip(JComponent strip, Graphics graphics, GeneratedTheme workspace, Color base, List<Rectangle> slots, int selected, int hovered)`.

- [ ] **Step 1: Write the failing test** — replace the two tab tests in `NativeChromePaintingTest` with:

```java
    @Test void tabSurfacesAreTheStripAndTerminalRolesAtTheWorkspacesAlpha() {
        var theme = KitFixtures.marked();
        var glass = new Color(0x22, 0x26, 0x29, 200);
        var idle = NativeChromePainting.tabSurface(theme, glass, false), chosen = NativeChromePainting.tabSurface(theme, glass, true);
        assertThat(idle.getRGB() & 0xFFFFFF).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_TABSTRIP).getRGB() & 0xFFFFFF);
        assertThat(chosen.getRGB() & 0xFFFFFF).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_TERMINAL).getRGB() & 0xFFFFFF);
        assertThat(idle.getAlpha()).isEqualTo(200);
        assertThat(chosen.getAlpha()).isEqualTo(200);
    }

    @Test void tabStripPaintsSlotsAHoveredSlotDividersAndTheLowerRule() {
        var theme = KitFixtures.marked();
        var base = new Color(0x22, 0x26, 0x29);
        var strip = new JPanel(); strip.setSize(300, 28);
        var image = new BufferedImage(300, 28, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.tabStrip(strip, g, theme, base, List.of(new Rectangle(0, 0, 100, 28), new Rectangle(100, 0, 100, 28), new Rectangle(200, 0, 100, 28)), 1, 2); }
        finally { g.dispose(); }
        int border = KitFixtures.marker(SemanticColor.BORDER_WINDOW).getRGB();
        assertThat(image.getRGB(50, 10)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_TABSTRIP).getRGB());
        assertThat(image.getRGB(150, 10)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_TERMINAL).getRGB());
        var hoverOver = new Color(image.getRGB(250, 10), true);
        assertThat(hoverOver.getRGB()).as("a hovered inactive tab takes the hover overlay").isNotEqualTo(KitFixtures.marker(SemanticColor.SURFACE_TABSTRIP).getRGB());
        assertThat(image.getRGB(99, 10)).as("divider between slots").isEqualTo(border);
        assertThat(image.getRGB(150, 27)).as("lower rule").isEqualTo(border);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*NativeChromePaintingTest'`
Expected: FAIL to compile (the new signatures do not exist).

- [ ] **Step 3: Implement** — in `NativeChromePainting`, replace `tabSurface(Color base, boolean dark, boolean selected)` and `tabStrip(...)` with:

```java
    /**
     * An equal-width workspace tab's surface: the selected tab is the terminal's surface and the others the tab strip's,
     * both at {@code base}'s alpha (the window's opacity while it is translucent).
     */
    public static Color tabSurface(GeneratedTheme workspace, Color base, boolean selected) {
        Color role = workspace.semantic().color(selected ? SemanticColor.SURFACE_TERMINAL : SemanticColor.SURFACE_TABSTRIP);
        return new Color(role.getRed(), role.getGreen(), role.getBlue(), base.getAlpha());
    }

    /**
     * The equal-width tab strip: the strip's surface behind everything, each slot's surface (only {@code selected} the
     * terminal's), the hover overlay over a hovered inactive slot, the window-border dividers between slots and the rule
     * along the bottom. Slot bounds are the host's retained motion geometry; {@code selected} and {@code hovered} are -1
     * when no slot is.
     */
    public static void tabStrip(JComponent strip, Graphics graphics, GeneratedTheme workspace, Color base,
                                java.util.List<Rectangle> slots, int selected, int hovered) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(tabSurface(workspace, base, false)); g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
            for (int i = 0; i < slots.size(); i++) { g.setColor(tabSurface(workspace, base, i == selected)); g.fill(slots.get(i)); }
            g.setComposite(AlphaComposite.SrcOver);
            if (hovered >= 0 && hovered < slots.size() && hovered != selected) { g.setColor(hoverOverlay(workspace)); g.fill(slots.get(hovered)); }
            g.setColor(workspace.semantic().color(SemanticColor.BORDER_WINDOW));
            int line = NativeControlStyles.metric(DesignMetrics.Role.TAB_DIVIDER);
            for (int i = 0; i < slots.size() - 1; i++) {
                Rectangle bounds = slots.get(i);
                if (bounds.width > 0) g.fillRect(bounds.x + bounds.width - line, 0, line, strip.getHeight());
            }
            g.fillRect(0, strip.getHeight() - line, strip.getWidth(), line);
        } finally { g.dispose(); }
    }
```

  Remove the four `TAB_SELECTED_LIFT_*`/`TAB_IDLE_SHADE_*` roles from `DesignMetrics`.

  In `WindowTabs.paintComponent`, replace the body with:

```java
        var bounds = new ArrayList<Rectangle>(slots.size());
        int chosen = -1, hovered = -1;
        for (Slot slot : slots) {
            if (slot.entry != null && slot.entry.tab == selected) chosen = bounds.size();
            if (slot.entry != null && slot.entry.hovered) hovered = bounds.size();
            bounds.add(slot.bounds);
        }
        NativeChromePainting.tabStrip(this, graphics, owner.theme().workspace(), owner.workspaceSurface(), bounds, chosen, hovered);
```

  and `surface(boolean selected)` with `return NativeChromePainting.tabSurface(owner.theme().workspace(), owner.workspaceSurface(), selected);`.

  In `WorkspaceMetricsTest`, replace `"TAB_SELECTED_LIFT_DARK", "TAB_IDLE_SHADE_DARK",` with `"SURFACE_TABSTRIP", "SURFACE_TERMINAL",`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-ui:test --tests '*NativeChromePaintingTest' :jasper-app:test --tests '*WindowTabs*' --tests '*WorkspaceGlassTest' --tests '*WorkspaceMetricsTest' --tests '*TabHeightTest'`
Expected: PASS (`WorkspaceGlassTest.tabSurfacesRetainWorkspaceAlphaWhetherOpaqueOrGlass` still holds the window's alpha).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-ui/src jasper-app/src
git commit -m "The tab strip on surface.tabstrip and surface.terminal

Inactive tabs and the strip take surface.tabstrip and the selected tab
surface.terminal, at the window's alpha while it is translucent, replacing
the lift/shade of the terminal background. A hovered inactive tab takes the
hover overlay. No accent indicator: the mocks show none.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: Controls on their tokens

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/EmptyStates.java:133`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java:337,398` and `PickerButton` (`border()` override)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java:157,209`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java:102,106`, `FlatLafInstaller.java:66`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ControlRolesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import org.junit.jupiter.api.Test;

/** spec 7: empty tiles, segmented controls, secondary buttons and dropdowns paint theme.md's roles. */
class ControlRolesTest {
    static int pixel(javax.swing.JComponent component, int x, int y) {
        var image = new java.awt.image.BufferedImage(component.getWidth(), component.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image.getRGB(x, y);
    }

    @Test void secondaryButtonsHaveTheirOwnEdgeAndStrongSecondaryLabels() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try (var view = new KitView(context, stack(new Button("cancel", ControlState.READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", "")), e -> {})) {
                var button = find(view, "cancel", Buttons.KitButton.class);
                assertThat(button.textRole(false)).isEqualTo(SemanticColor.TEXT_STRONG_SECONDARY);
                assertThat(button.border()).isEqualTo(KitFixtures.marker(SemanticColor.BUTTON_SECONDARY_BORDER));
            } finally { release(context); }
        });
    }

    @Test void theSegmentedTrackIsItsOwnSurfaceWithAnInputEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try {
                var track = new Choices.SegmentTrack(context); track.setSize(120, 30);
                assertThat(pixel(track, 60, 15)).isEqualTo(KitFixtures.marker(SemanticColor.SEGMENT_TRACK).getRGB());
                assertThat(pixel(track, 60, 0)).isEqualTo(KitFixtures.marker(SemanticColor.BORDER_INPUT).getRGB());
            } finally { release(context); }
        });
    }

    /** A panel body's empty state (PanelSurfacesTest's fixture) takes the empty tile; a window's keeps the control surface. */
    @Test void panelEmptyTilesUseTheEmptyTileSurface() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try (var view = new KitView(context, new ViewSpec(PanelSurfacesTest.plain(PanelCompositionTest.empty(), Optional.empty())), e -> {});
                 var window = new KitView(context, stack(PanelCompositionTest.empty()), e -> {})) {
                KitFixtures.render(view, 260, 395, 1);
                KitFixtures.render(window, 400, 300, 1);
                var tile = find(view, "empty.tile", javax.swing.JComponent.class);
                assertThat(pixel(tile, tile.getWidth() / 2, 2)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_EMPTY_TILE).getRGB());
                var hero = find(window, "empty.tile", javax.swing.JComponent.class);
                assertThat(pixel(hero, 3, hero.getHeight() / 2)).as("the window's hero tile, inside its outline").isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_CONTROL).getRGB());
            } finally { release(context); }
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*ControlRolesTest'`
Expected: FAIL (label `TEXT_PRIMARY`, edge `BORDER_CONTROL`, track `SURFACE_INPUT`, tile `SURFACE_CONTROL`).

- [ ] **Step 3: Implement**
  - `Buttons.java:157`: `case SECONDARY -> SemanticColor.TEXT_PRIMARY;` → `case SECONDARY -> SemanticColor.TEXT_STRONG_SECONDARY;`.
  - `Buttons.java:209` (`border()`): `ThemePainting.color(context, SemanticColor.BORDER_CONTROL)` → `ThemePainting.color(context, SemanticColor.BUTTON_SECONDARY_BORDER)`.
  - The picker button keeps the control edge (it is a dropdown trigger): in `Choices`' `PickerButton` class add
    ```java
        @Override Color border() { return ThemePainting.color(context, SemanticColor.BORDER_CONTROL); }
    ```
  - `Choices.java:398` (`SegmentTrack.paintComponent`): `ThemePainting.color(context, SemanticColor.SURFACE_INPUT), ThemePainting.color(context, SemanticColor.BORDER_SUBTLE)` → `ThemePainting.color(context, SemanticColor.SEGMENT_TRACK), ThemePainting.color(context, SemanticColor.BORDER_INPUT)`.
  - `Choices.java:337` (combobox trigger): `ThemePainting.color(context, SemanticColor.SURFACE_CONTROL)` → `ThemePainting.color(context, hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_CONTROL)`.
  - `EmptyStates.java:133`: `SemanticColor.SURFACE_CONTROL` → `owner.panel ? SemanticColor.SURFACE_EMPTY_TILE : SemanticColor.SURFACE_CONTROL` (the hero tile keeps the control surface).
  - `NativeControlStyles.framedButton`: `button.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));` → `TEXT_STRONG_SECONDARY`; `"borderColor", palette.color(SemanticColor.BORDER_CONTROL)` → `palette.color(SemanticColor.BUTTON_SECONDARY_BORDER)`.
  - `FlatLafInstaller.java:66`: `Map.entry("Button.foreground", SemanticColor.TEXT_PRIMARY)` → `Map.entry("Button.foreground", SemanticColor.TEXT_STRONG_SECONDARY)`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-ui:test`
Expected: PASS; tests asserting the old secondary label/edge or track roles change their expected role only.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-ui/src
git commit -m "Controls on theme.md's tokens

Secondary buttons take button.secondary.border and text.strong.secondary
(dropdown triggers keep border.control and gain their hover fill), the
segmented track takes segment.track with a border.input edge, and panel empty
tiles take surface.emptyTile.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: Text roles

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/SettingRows.java:42-44`, `BreadcrumbControls.java:171-205`, `Labels.java:119-120`, `Tables.java:158`, `ItemLists.java:442`, `Choices.java:81,218`, `PickerMenus.java:52`, `NativeChromePainting.java:170`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowHeader.java:47`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/TextRolesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** spec 7: captions and counts are tertiary, Settings row titles and the current crumb strong secondary. */
class TextRolesTest {
    @Test void captionsAndCountsAreTertiaryAndLabelsSecondary() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try (var view = new KitView(context, stack(
                    new Text("caption", ControlState.READY, "3 items", TextRole.CAPTION),
                    new Text("count", ControlState.READY, "3", TextRole.COUNT),
                    new Text("label", ControlState.READY, "Name", TextRole.LABEL)), e -> {})) {
                assertThat(find(view, "caption", JLabel.class).getForeground()).isEqualTo(KitFixtures.marker(SemanticColor.TEXT_TERTIARY));
                assertThat(find(view, "count", JLabel.class).getForeground()).isEqualTo(KitFixtures.marker(SemanticColor.TEXT_TERTIARY));
                assertThat(find(view, "label", JLabel.class).getForeground()).isEqualTo(KitFixtures.marker(SemanticColor.TEXT_SECONDARY));
            } finally { release(context); }
        });
    }

    @Test void theCurrentCrumbIsStrongSecondary() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            var crumbs = new Breadcrumbs("crumbs", ControlState.READY, List.of(new Crumb("group", "Appearance & Behavior", List.of()),
                new Crumb("page", "Appearance", List.of())), "Location", false, "", "edit", "go", "done");
            try (var view = new KitView(context, stack(crumbs), e -> {})) {
                var current = find(view, "@crumbs/crumbs/page", Buttons.KitButton.class);
                var parent = find(view, "@crumbs/crumbs/group", Buttons.KitButton.class);
                assertThat(current.textRole(false)).isEqualTo(SemanticColor.TEXT_STRONG_SECONDARY);
                assertThat(parent.textRole(false)).isEqualTo(SemanticColor.TEXT_SECONDARY);
            } finally { release(context); }
        });
    }
}
```

(`KitOwnership.mark` names each crumb button `@crumbs/<node>/<crumb>`, which `KitViewTest.find` matches, as `BrowserKitTest` does.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*TextRolesTest'`
Expected: FAIL (captions secondary; every crumb a plain ghost button).

- [ ] **Step 3: Implement**
  - `Labels.java:119-120`: change the two arms to
    ```java
                case LABEL, DESCRIPTION -> SemanticColor.TEXT_SECONDARY;
                case CAPTION, COUNT, SECTION_LABEL, MONO_CAPTION, SUPPORTING -> SemanticColor.TEXT_TERTIARY;
    ```
  - `SettingRows.java:42`: `detail ? SemanticColor.TEXT_SECONDARY : SemanticColor.TEXT_PRIMARY` → `detail ? SemanticColor.TEXT_SECONDARY : SemanticColor.TEXT_STRONG_SECONDARY`; line 44: `detail ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_TERTIARY` → `detail ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY`.
  - `BreadcrumbControls`: before the `for (Crumb crumb : value().crumbs())` loop add `String currentId = value().crumbs().isEmpty() ? "" : value().crumbs().getLast().id();`; replace `var button = new Buttons.KitButton(context());` with
    ```java
                boolean current = crumb.id().equals(currentId);
                var button = new Buttons.KitButton(context()) {
                    // The current crumb reads strong secondary; the others are ghost buttons.
                    @Override SemanticColor textRole(boolean hover) { return current && !hover ? SemanticColor.TEXT_STRONG_SECONDARY : super.textRole(hover); }
                };
    ```
  - `Tables.java:158`: `SemanticColor.TEXT_SECONDARY` → `SemanticColor.TEXT_TERTIARY`.
  - `ItemLists.java:442`: `SemanticColor.TEXT_SECONDARY` → `SemanticColor.TEXT_TERTIARY`.
  - Empty picker values: `Choices.java:218` and `:81` replace `SemanticColor.TEXT_TERTIARY` in the empty branch with `SemanticColor.TEXT_PLACEHOLDER`; `PickerMenus.java:52` replace `empty?SemanticColor.TEXT_TERTIARY` with `empty?SemanticColor.TEXT_PLACEHOLDER`.
  - `NativeChromePainting.statusDot`: `palette.color(SemanticColor.TEXT_SECONDARY)` → `palette.color(SemanticColor.STATUS_IDLE)`; javadoc "secondary text otherwise" → "the idle status otherwise".
  - `ToolWindowHeader.java:47`: `SemanticColor.TEXT_SECONDARY` → `SemanticColor.TEXT_TERTIARY`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests '*ToolWindowHeaderTest' --tests '*Settings*' --tests '*StatusBar*'`
Expected: PASS; tests asserting the previous text roles change their expected role only.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-ui/src jasper-app/src
git commit -m "Text on theme.md's roles

Captions, counts, table headers and row subtitles are text.tertiary; Settings
row titles and the current breadcrumb text.strong.secondary, Settings row
descriptions text.secondary; empty picker values text.placeholder; the idle
status dot status.idle.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: Menus, the palette, selection and window surfaces

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java:133`, `RichOptions.java:54,59`, `PickerMenus.java:54,136`, `InteractiveRows.java:1220`, `ItemLists.java:470`, `SurfacePanel.java` (fill role, row separator role), `SettingsShell.java:53`
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java:147`, `jasper-app/src/main/java/dev/jasper/app/files/FileChooserPanel.java:285`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/MenuRolesTest.java`

**Interfaces:**
- Produces: `public void SurfacePanel.fill(SemanticColor role)` (default `SURFACE_MENU` for `POPUP`, `SURFACE_CARD` otherwise); `public static JPanel SurfacePanel.row(KitContext context, DesignMetrics.Role minimum, boolean separator, SemanticColor separatorRole)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import org.junit.jupiter.api.Test;

/** spec 7: the palette is a menu surface; window bodies and sidebars take their roles. */
class MenuRolesTest {
    static int center(javax.swing.JComponent component) {
        var image = new java.awt.image.BufferedImage(component.getWidth(), component.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image.getRGB(component.getWidth() / 2, component.getHeight() / 2);
    }

    @Test void popupsAreMenuSurfacesAndContentTakesTheRoleItIsGiven() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try {
                var popup = new SurfacePanel(context, SurfacePanel.Kind.POPUP); popup.setSize(200, 100);
                assertThat(center(popup)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_MENU).getRGB());
                var places = new SurfacePanel(context, SurfacePanel.Kind.CONTENT); places.setSize(200, 100);
                places.fill(SemanticColor.SURFACE_SIDEBAR);
                assertThat(center(places)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_SIDEBAR).getRGB());
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theSettingsWindowBodyIsTheDialogSurface() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try {
                var parts = new javax.swing.JComponent[6];
                for (int i = 0; i < parts.length; i++) parts[i] = new javax.swing.JPanel();
                try (var shell = new SettingsShell(context, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])) {
                    shell.setSize(900, 600); shell.doLayout();
                    var image = new java.awt.image.BufferedImage(900, 600, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics();
                    try { shell.paintComponent(g); } finally { g.dispose(); }
                    assertThat(image.getRGB(700, 300)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_DIALOG).getRGB());
                }
            } finally { KitViewTest.release(context); }
        });
    }
}
```

(`SettingsShell.paintComponent` is `protected`, which includes package access: the test sits in `dev.jasper.ui.swing`. The point (700, 300) is right of the sidebar, below the header and above the footer.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*MenuRolesTest'`
Expected: FAIL to compile (`fill` does not exist).

- [ ] **Step 3: Implement**
  - `SurfacePanel`: add a field `private SemanticColor fill;` initialised in the constructor to `kind == Kind.POPUP ? SemanticColor.SURFACE_MENU : SemanticColor.SURFACE_CARD`; add
    ```java
    /** The surface's fill role: the menu surface for a popup, the card surface otherwise unless the owner names another. */
    public void fill(SemanticColor role) { fill = java.util.Objects.requireNonNull(role); repaint(); }
    ```
    and in `paintComponent` replace `g.setColor(ThemePainting.color(context,SemanticColor.SURFACE_CARD));` with `g.setColor(ThemePainting.color(context,fill));`.
  - `SurfacePanel.row`: keep `row(context, minimum, separator)` delegating to a new `row(context, minimum, separator, SemanticColor.BORDER_SUBTLE)`; the 4-argument overload passes the role into `NativeRow`, whose separator painting (`SurfacePanel.java:379`) uses it instead of `SemanticColor.BORDER_SUBTLE`.
  - `CommandPalette.java:147`: `SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_HINT_HEIGHT, true)` → `SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_HINT_HEIGHT, true, dev.jasper.ui.theme.SemanticColor.BORDER_MENU)`.
  - `FileChooserPanel.java:285`: after `var navigationPane = rounded(navigation, () -> headingView.getHeight());` add `navigationPane.fill(dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR);` (the Places pane is a sidebar; the files and message panes keep the card surface).
  - `SettingsShell.java:53`: `SemanticColor.SURFACE_APP` → `SemanticColor.SURFACE_DIALOG`.
  - `NativeControlStyles.java:133`: `"selectionBackground", palette.color(SemanticColor.SELECTION_BG)` → `palette.color(SemanticColor.ACCENT)`.
  - `RichOptions.java:59` and `PickerMenus.java:54`: the highlighted subtitle `SemanticColor.TEXT_ONACCENT` → `SemanticColor.TEXT_ONACCENT_SECONDARY` (only in those two subtitle colour expressions).
  - Checkmarks: `PickerMenus.java:136` `active?SemanticColor.TEXT_ONACCENT:SemanticColor.ACCENT` → `active?SemanticColor.TEXT_ONACCENT:SemanticColor.TEXT_LINK`; `RichOptions.java:56` pass `selected ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_LINK` as the check icon's role instead of `role`.
  - The palette's results highlight: `InteractiveRows.java:1220` `SemanticColor.SELECTION_BG` → `SemanticColor.ACCENT`; the contrast basis `ItemLists.java:470` `SemanticColor.SELECTION_BG` → `SemanticColor.ACCENT`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests '*CommandPalette*' --tests '*FileChooser*' --tests '*Settings*'`
Expected: PASS; tests asserting the previous roles change their expected role only.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-ui/src jasper-app/src
git commit -m "Menus, the palette and window surfaces on theme.md's roles

The command palette is a surface.menu card with a border.menu separator and
an accent highlight; native menus highlight with the accent; subtitles on
highlighted rows take text.onAccent.secondary and checkmarks text.link. The
Settings body is surface.dialog and the file chooser's Places surface.sidebar.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 14: Shadows

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (shadow metrics; remove `OVERLAY_SHADOW`, `OVERLAY_SHADOW_PERCENT`, `PALETTE_SHADOW_SPREAD`, `PALETTE_SHADOW_ALPHA_DARK`, `PALETTE_SHADOW_ALPHA_LIGHT`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ThemePainting.java` (add `softShadow`), `Buttons.java` (primary), `Choices.java` (`SegmentButton`), `ModalOverlay.java:264-273`, `NativeChromePainting.java:250-266`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java` (`uiKeys`: popup shadow keys)
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/swing/NativeChromePaintingTest.java:55-65`, `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java:63`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ShadowsTest.java`

**Interfaces:**
- Produces: metrics `BUTTON_INSET_PERCENT(14)`, `BUTTON_DROP_PERCENT_DARK(30)`, `BUTTON_DROP_PERCENT_LIGHT(12)`, `POPOVER_SHADOW_OFFSET(12)`, `POPOVER_SHADOW_BLUR(32)`, `POPOVER_SHADOW_PERCENT_DARK(50)`, `POPOVER_SHADOW_PERCENT_LIGHT(18)`, `DIALOG_SHADOW_OFFSET(30)`, `DIALOG_SHADOW_BLUR(80)`, `DIALOG_SHADOW_PERCENT_DARK(58)`, `DIALOG_SHADOW_PERCENT_LIGHT(22)`, `SEGMENT_SHADOW_PERCENT_LIGHT(12)`, `SEGMENT_SHADOW_BLUR(2)`; `static void ThemePainting.softShadow(Graphics2D g, java.awt.geom.RectangularShape face, double radius, int offset, int blur, double strength)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.*;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** spec 8: shadows are black at theme.md's strengths, never theme colours. */
class ShadowsTest {
    @Test void aSoftShadowReachesItsStrengthUnderTheFaceAndFadesOutward() {
        var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { ThemePainting.softShadow(g, new Rectangle2D.Double(50, 50, 100, 100), 8, 10, 20, .5); } finally { g.dispose(); }
        var under = new Color(image.getRGB(100, 110), true);
        assertThat(under.getRed()).isZero();
        assertThat(under.getAlpha()).as("full strength under the face").isBetween(120, 135);
        var edge = new Color(image.getRGB(100, 160 + 15), true);
        assertThat(edge.getAlpha()).as("fading toward the blur's reach").isBetween(1, 120);
        assertThat(new Color(image.getRGB(5, 5), true).getAlpha()).as("nothing beyond the blur").isZero();
    }

    @Test void popoverShadowKeysCarryTheVariantsStrength() {
        var dark = dev.jasper.ui.theme.SlateSteel.bundled().generated(true).uiKeys();
        var light = dev.jasper.ui.theme.SlateSteel.bundled().generated(false).uiKeys();
        assertThat(dark).containsEntry("Popup.dropShadowColor", "#000000").containsEntry("Popup.dropShadowOpacity", .5);
        assertThat(light).containsEntry("Popup.dropShadowOpacity", .18);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests '*ShadowsTest'`
Expected: FAIL to compile (`softShadow` does not exist).

- [ ] **Step 3: Metrics** — in `DesignMetrics`, delete `OVERLAY_SHADOW(16),` and `OVERLAY_SHADOW_PERCENT(24),` from the overlay lines and `PALETTE_SHADOW_SPREAD(12), PALETTE_SHADOW_ALPHA_DARK(4), PALETTE_SHADOW_ALPHA_LIGHT(2)` from line 62, then add:

```java
        // theme.md's shadows, black at these strengths (not theme colours): the primary button's inset line and 1 px drop,
        // popovers and menus, the modal overlay's card, and the segmented control's selected segment in Light.
        BUTTON_INSET_PERCENT(14), BUTTON_DROP_PERCENT_DARK(30), BUTTON_DROP_PERCENT_LIGHT(12),
        POPOVER_SHADOW_OFFSET(12), POPOVER_SHADOW_BLUR(32), POPOVER_SHADOW_PERCENT_DARK(50), POPOVER_SHADOW_PERCENT_LIGHT(18),
        DIALOG_SHADOW_OFFSET(30), DIALOG_SHADOW_BLUR(80), DIALOG_SHADOW_PERCENT_DARK(58), DIALOG_SHADOW_PERCENT_LIGHT(22),
        SEGMENT_SHADOW_PERCENT_LIGHT(12), SEGMENT_SHADOW_BLUR(2),
```

- [ ] **Step 4: The soft shadow** — a CSS-like shadow is a blurred mask, not nested fills: an 80 px blur drawn as rings
would mean 80 large fills on every animation frame of the modal overlay. The mask is blurred once per size and strength
and kept in a small cache (EDT only, like all kit painting), so a frame costs one image draw. Add to `ThemePainting`
(imports `java.awt.image.BufferedImage`, `java.util.LinkedHashMap`, `java.util.Map` if absent):

```java
    private record ShadowKey(int width, int height, double radius, int blur, double strength) { }
    /** Blurred shadow masks by face size and treatment; a few faces are on screen at once. EDT only. */
    private static final Map<ShadowKey, BufferedImage> SHADOWS = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ShadowKey, BufferedImage> eldest) { return size() > 16; }
    };

    /**
     * A soft black shadow under {@code face}, like CSS {@code box-shadow: 0 offset blur rgba(0,0,0,strength)}: the
     * rounded face at {@code strength}, lowered by {@code offset} and blurred by {@code blur} (three box passes each way,
     * a Gaussian of sigma {@code blur / 2}). Paint it before the face; the graphics' composite scales it (a fading card).
     */
    static void softShadow(Graphics2D graphics, java.awt.geom.RectangularShape face, double radius, int offset, int blur, double strength) {
        if (blur <= 0 || strength <= 0 || face.isEmpty()) return;
        int width = (int) Math.ceil(face.getWidth()), height = (int) Math.ceil(face.getHeight()), pad = blur + blur / 2;
        var mask = SHADOWS.computeIfAbsent(new ShadowKey(width, height, radius, blur, Math.min(1, strength)), key -> shadow(key, pad));
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(mask, (int) Math.floor(face.getX()) - pad, (int) Math.floor(face.getY()) - pad + offset, null);
        } finally { g.dispose(); }
    }

    private static BufferedImage shadow(ShadowKey key, int pad) {
        int w = key.width() + 2 * pad, h = key.height() + 2 * pad;
        var mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = mask.createGraphics();
        try {
            antialias(g);
            g.setColor(ColorMath.opacity(Color.BLACK, key.strength()));
            g.fill(new RoundRectangle2D.Double(pad, pad, key.width(), key.height(), 2 * key.radius(), 2 * key.radius()));
        } finally { g.dispose(); }
        int[] pixels = mask.getRGB(0, 0, w, h, null, 0, w), alpha = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) alpha[i] = pixels[i] >>> 24;
        int box = Math.max(1, key.blur() / 2);
        for (int pass = 0; pass < 3; pass++) { boxBlur(alpha, w, h, box, true); boxBlur(alpha, w, h, box, false); }
        for (int i = 0; i < pixels.length; i++) pixels[i] = alpha[i] << 24;
        mask.setRGB(0, 0, w, h, pixels, 0, w);
        return mask;
    }

    /** One running-sum box blur of {@code radius} along each row ({@code horizontal}) or column, in place. */
    private static void boxBlur(int[] alpha, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width, length = horizontal ? width : height, span = 2 * radius + 1;
        int[] line = new int[length];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < length; i++) line[i] = alpha[horizontal ? l * width + i : i * width + l];
            int sum = 0;
            for (int i = 0; i <= Math.min(radius, length - 1); i++) sum += line[i];
            for (int i = 0; i < length; i++) {
                alpha[horizontal ? l * width + i : i * width + l] = (sum + span / 2) / span;
                if (i - radius >= 0) sum -= line[i - radius];
                if (i + radius + 1 < length) sum += line[i + radius + 1];
            }
        }
    }
```

Add to `ShadowsTest`:

```java
    @Test void aShadowFollowsTheCompositeSoAFadingCardsShadowFadesWithIt() {
        var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive(.5f));
            ThemePainting.softShadow(g, new Rectangle2D.Double(50, 50, 100, 100), 8, 10, 20, .5);
        } finally { g.dispose(); }
        assertThat(new Color(image.getRGB(100, 110), true).getAlpha()).isBetween(60, 68);
    }
```

- [ ] **Step 5: Use it**
  - `ModalOverlay.paintComponent`: replace the block from `// A soft shadow: nested rounded fills…` through the closing `}` of its `if (reach > 0)` with
    ```java
            // theme.md's dialog shadow, black, fading with the card.
            var shadow = (Graphics2D) g.create();
            try {
                shadow.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, Math.max(0, shown))));
                ThemePainting.softShadow(shadow, new java.awt.geom.Rectangle2D.Double(at.x, at.y, at.width, at.height),
                    ThemePainting.metric(context, DesignMetrics.Role.WINDOW_RADIUS), ThemePainting.metric(context, DesignMetrics.Role.DIALOG_SHADOW_OFFSET),
                    ThemePainting.metric(context, DesignMetrics.Role.DIALOG_SHADOW_BLUR), context.metrics().value(context.theme().dark()
                        ? DesignMetrics.Role.DIALOG_SHADOW_PERCENT_DARK : DesignMetrics.Role.DIALOG_SHADOW_PERCENT_LIGHT) / 100.0);
            } finally { shadow.dispose(); }
    ```
  - `ModalOverlayTest`, the exit test's `// Its picture at .75 over the scrim and shadow …` assertion: the card's picture at .75 now lies over the
    scrim (black at 55% × .75) and the dialog shadow (black at 58% × .75), so `isBetween(208, 228)` becomes
    `isBetween(226, 240)` (.75 + .25 × (1 − (1 − .41)(1 − .44)) ≈ .92 of 255 is 234).
  - `NativeChromePainting.popupShadow`: replace its body with
    ```java
        ThemePainting.softShadow((Graphics2D) graphics, new java.awt.geom.Rectangle2D.Double(card.x, card.y, card.width, card.height),
            NativeControlStyles.metric(DesignMetrics.Role.PALETTE_CORNER_ARC) / 2.0, NativeControlStyles.metric(DesignMetrics.Role.POPOVER_SHADOW_OFFSET),
            NativeControlStyles.metric(DesignMetrics.Role.POPOVER_SHADOW_BLUR),
            DesignMetrics.DEFAULT.value(dark ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100.0);
    ```
    and change its javadoc's description of stacked scrim rings to "theme.md's popover shadow: black, 12 px down, 32 px blur, 50% in Dark and 18% in Light". (`ThemePainting` is package-private in the same package; `softShadow` copies the graphics itself.)
  - `NativeChromePaintingTest.popupShadowAndDropGuidesUseTheirNamedAlphas`: keep its two `dropGuide` assertions and replace everything after them (from `var image = new BufferedImage(80, 80, …` to the end of the method) with
    ```java
        // theme.md's popover shadow: black, lowered 12 and blurred 32, so it reaches 48 beyond the face and no further.
        var image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.popupShadow(g, new Rectangle(100, 80, 40, 40), theme, true); } finally { g.dispose(); }
        var below = new Color(image.getRGB(120, 122), true);
        assertThat(below.getAlpha()).as("below the card").isPositive();
        assertThat(below.getRGB() & 0xFFFFFF).as("black").isZero();
        assertThat(image.getRGB(100 - 49, 100) >>> 24).as("nothing beyond the blur").isZero();
    ```
    (import `java.awt.Color` if absent).
  - `WorkspaceMetricsTest:63`: `"PALETTE_SHADOW_SPREAD"` → `"POPOVER_SHADOW_BLUR"`.
  - Primary button: in `Buttons.KitButton.paintComponent`, paint the 1 px drop and the inset line with the face; the face is drawn 1 px shorter (`bottomTrim` 1) so the drop sits below it inside the bounds. Immediately before the existing `if(circular)ThemePainting.rounded(...)` chain, add the following, so that chain becomes this `if`'s `else` branch (every other button is unchanged):
    ```java
                if (kind == Nodes.ButtonKind.PRIMARY && !embedded && fill != null && !circular && !panelDetail) {
                    int line = UIScale.scale(1);
                    double radius = ThemePainting.metric(context, DesignMetrics.Role.CONTROL_RADIUS);
                    // theme.md's primary button: a 1 px black drop below the face and a white inset line along its top.
                    g.setColor(ColorMath.opacity(Color.BLACK, context.metrics().value(context.theme().dark()
                        ? DesignMetrics.Role.BUTTON_DROP_PERCENT_DARK : DesignMetrics.Role.BUTTON_DROP_PERCENT_LIGHT) / 100.0));
                    ThemePainting.antialias(g);
                    g.fill(new java.awt.geom.RoundRectangle2D.Double(0, line, getWidth(), getHeight() - line, radius * 2, radius * 2));
                    ThemePainting.rounded(g, this, context, fill, border, focused, false, radius, 0, line);
                    var top = (Graphics2D) g.create();
                    try {
                        top.clip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight() - line, radius * 2, radius * 2));
                        top.setColor(ColorMath.opacity(Color.WHITE, context.metrics().value(DesignMetrics.Role.BUTTON_INSET_PERCENT) / 100.0));
                        top.fillRect(0, 0, getWidth(), line);
                    } finally { top.dispose(); }
                } else
    ```
  - Selected segment (Light only): the segment's own bounds would clip a shadow around it, so the track paints it under its selected child. In `Choices.SegmentTrack.paintComponent`, after the `ThemePainting.rounded(...)` call and inside the same `try`, add
    ```java
                if (!context.theme().dark()) for (var child : getComponents())
                    if (child instanceof SegmentButton segment && segment.isVisible() && segment.isSelected())
                        ThemePainting.softShadow(g, new java.awt.geom.Rectangle2D.Double(segment.getX(), segment.getY(), segment.getWidth(), segment.getHeight()),
                            ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_ITEM_RADIUS), UIScale.scale(1),
                            ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_SHADOW_BLUR), context.metrics().value(DesignMetrics.Role.SEGMENT_SHADOW_PERCENT_LIGHT) / 100.0);
    ```
    (import `com.formdev.flatlaf.util.UIScale` in `Choices` if absent).
  - `SlateSteel.uiKeys(...)` gains the variant: change its signature to `uiKeys(Map<Token, Color> tokens, boolean dark)`, call it as `uiKeys(tokens, dark)` in `generate`, and add at its end:
    ```java
        // Popovers and menus: FlatLaf's painted popup shadow (macOS keeps its native one), black at theme.md's strength.
        keys.put("Popup.dropShadowColor", ColorMath.hex(Color.BLACK));
        keys.put("Popup.dropShadowOpacity", dark ? .5 : .18);
    ```

- [ ] **Step 6: Run tests**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests '*WorkspaceMetricsTest' --tests '*WindowCommandPalette*' --tests '*WindowOverlay*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-ui/src jasper-app/src
git commit -m "theme.md's shadows

A soft black shadow (a blurred mask, cached per size) serves the modal
overlay's card (30 px down, 80 px blur, 58%/22%) and the command palette (12
px down, 32 px blur, 50%/18%); FlatLaf's popup shadow takes the popover
strength. The primary button gains its inset top line and 1 px drop, and the
selected segment a soft shadow in Light.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 15: Documentation and the full check

**Files:**
- Modify: `docs/ui-architecture.md` (a Slate Steel paragraph), `docs/plugin-ui.md:190` (the role list sentence), `docs/design/theme-engine/acceptance.md` (Plan 1 note and the six decisions), `docs/STATUS.md` (entry)
- Modify: `docs/superpowers/specs/2026-10-12-jasper-slate-steel-themes-design.md` (status line: Plan 1 complete)

- [ ] **Step 1: Documentation**
  - `docs/ui-architecture.md`: after the `ModalOverlay` paragraph, add:
    ```markdown
    `SlateSteel` reads Jasper's theme from the bundled `slate-steel.json` (the colour reference
    `docs/design/theme-engine/slate-steel.md`'s machine-readable block, kept identical by a test) with the JDK-only
    `ThemeJson` reader: the Dark and Light semantic palettes (roles the file omits follow fixed rules), the terminal
    schemes (`TerminalScheme`, `SchemeColors` with a bold foreground) and the derived editor schemes (`EditorColors`
    and syntax styles, written as token references). `generated(dark)` assembles a full `GeneratedTheme`; until the
    legacy layer is removed it also carries a legacy token view derived from its tokens. Components paint theme.md's
    roles, alpha treatments apply one kit metric to a base token, and shadows are black at the kit's shadow metrics.
    ```
  - `docs/plugin-ui.md:190`: after "Use `context.appearance().color(SemanticColor.X)` for canonical roles" add ", including the ten Slate Steel roles of SDK 0.15.5 (raised fields, the selected control, the tab strip, empty tiles, the segmented track, strong secondary text and the secondary button edge)".
  - `docs/design/theme-engine/acceptance.md`: add a "Slate Steel Plan 1 (engine and kit)" section listing what moved (Tasks 8–14) and the six decisions from this plan's "Decisions recorded" section, verbatim.
  - `docs/STATUS.md`: add a dated entry under the follow-up entry: "Slate Steel Plan 1 (engine and kit) complete on `claude/slate-steel`: bundled theme file, palettes, terminal and editor schemes, generated themes, ten roles (SDK 0.15.5), opacity once, components on theme.md's roles, shadows. Plan 2 (app switch) next."
  - Spec status line: append "Plan 1 (engine and kit) complete on <date>."

- [ ] **Step 2: Source hygiene**

Run the Python check from AGENTS.md over `jasper-ui/src`, `jasper-app/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and `plugins`.
Expected: no output.

- [ ] **Step 3: Full check**

Run: `./gradlew check --continue` (in the worktree; about 8 minutes).
Expected: BUILD SUCCESSFUL. Read failures from `*/build/test-results/test/*.xml`; fix each at its owner and rerun.

- [ ] **Step 4: Commit**

```bash
git add -A docs
git commit -m "Slate Steel Plan 1: documentation and verification

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Not in Plan 1 (Plans 2 and 3)

- Plan 2: `ui.appearance` and `terminal.scheme`, their resolution, terminal panes on the scheme palette (including the bold foreground in `jasper-terminal`), the Appearance page, Slate Steel as the only installed theme.
- Plan 3: removal of the legacy layer (`Token`, `GeneratedTheme.tokens()`, `UiTemplate`'s token tables rewritten onto roles, the find bar's error tint and the split divider moved onto semantic roles), `themeio`, `themeimport`, the catalog and import UI, old themes and assets, the colour-hex source scan, test migration and the re-rendered mock comparisons.
