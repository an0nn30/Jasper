# Theme import Plan 3: the theme converter — Implementation Plan

> **Status (2026-10-13): executed on `claude/theme-import`.** Task 1's review added a fix round beyond the code
> embedded below: bad input (a corrupt archive member, a file of the wrong shape or nested too deeply, an output
> path that cannot be written) ends in `error: <input>: …` and exit status 1 with the other inputs still written;
> `--theme` resolves parents against every theme in the plugin; an archive with no themes, and a theme whose file
> would be `slate-steel.json`, are refused; `--list` resolves Dark or Light through the parent chain; files are
> written through a temporary file; the by-name editor-scheme scan skips XML that is not a scheme; the entry limit
> counts nested jars together, and the `--scheme` file and a loose `.theme.json` are capped at 4 MiB; `--verbose`
> is described as printing where each UI role came from. The suite has 55 tests; the golden files are unchanged.
> Task 2's review added the golden files to `:jasper-ui:test`'s inputs, so a regenerated golden file reruns
> `ConvertedThemesTest`. The final whole-branch review's fixes are in docs/STATUS.md.

> **Deviations from the spec's section 5.4 (recorded here and in `docs/STATUS.md`), found while checking the table
> against IntelliJ's own themes, as 5.4 asks:** (1) IntelliJ themes set most colours through `*.` wildcards, and a
> child theme's value can be something that is not a colour, so a lookup walks the parent chain one theme at a time
> (exact key, then the longest matching wildcard) and skips values that are not colours, as IntelliJ ignores them;
> (2) every row that had only keys also has a fallback rule, because a theme can override the one key a base theme
> defines (Darcula has no `Borders.color`); (3) `surface.panel.raised` no longer reads `ToolWindow.Header.background`,
> which is IntelliJ's blue highlight for an active header, not a raised surface; (4) the `error` syntax role falls back
> to `ERRORS_ATTRIBUTES`' effect (underline) colour, since IntelliJ draws errors as a wave. The table in
> `jasper_theme.py` (`MAPPING`) is the result, and the golden files pin it.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A standard-library Python tool, `tools/themes/jasper_theme.py`, that converts Alacritty themes into
terminal-only Jasper themes and IntelliJ themes (loose `.theme.json`, plugin `.jar`, Marketplace `.zip`) into full
Jasper UI themes, tested from `./gradlew check` and pinned to the Java loader by a contract test.

**Architecture:** One Python file holds the format rules (a self-check mirroring `dev.jasper.ui.theme.JasperTheme`),
the Alacritty mapping, in-memory plugin reading with limits, IntelliJ parent-chain resolution against IntelliJ's own
themes and editor schemes (vendored byte for byte from git history), the role mapping table and the command line.
`unittest` tests with small fixtures compare every output byte for byte with committed golden files; a root Gradle
script runs them as `checkThemeTool` under `:jasper-ui:check`, and a jasper-ui test loads every golden file with the
real loader.

**Tech Stack:** Python 3.11+ (`tomllib`, `zipfile`, `xml.parsers.expat`, `unittest`), Gradle 9.7 Kotlin DSL, Java 25,
JUnit 6.1.3, AssertJ 3.27.7, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-13-jasper-theme-import-design.md` (sections 5, 6.1, 6.2 and 7).

**Workspace:** worktree `~/projects/moray/.claude/worktrees/theme-import`, branch `claude/theme-import`, **after Plan 1
is complete on it** (this plan needs Plan 1's format: the migrated `slate-steel.json` and `JasperTheme.parse`). It does
not need Plan 2's code; when both are to be done, run Plan 2 first (its documentation patch expects Plan 1's
`docs/themes.md`, while this plan's documentation step inserts by heading and works either way). Run every command from the worktree root. Never build in `~/projects/moray` itself (the user runs the
app there).

**Verified:** the tool, its tests, fixtures and golden files below were run before this plan was written (Python
3.12.10, macOS): 32 tests, 0 failures; the golden files' SHA-256 values in Task 1 Step 7 come from that run.

## Global Constraints

- Python 3.11 or later, standard library only; nothing is downloaded and the tool never touches the network.
- The tool is `tools/themes/jasper_theme.py`; its tests `tools/themes/test_jasper_theme.py`, run from the repository
  root with `python3 -m unittest discover -s tools/themes -p 'test_*.py'`; fixtures under `tools/themes/testdata/`;
  golden files under `tools/themes/testdata/expected/`.
- IntelliJ's themes and schemes are vendored under `tools/themes/intellij/`, restored byte for byte from `c1e968ee^`
  (`jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/`), Apache License 2.0, with `LICENSE.txt`,
  `NOTICE.txt`, `SOURCE.md` and `assets.tsv`.
- Output files are `<slug>.json`: Unicode-decomposed, accents dropped, lower-cased, runs of characters other than ASCII
  letters and digits replaced by `-`, leading and trailing `-` removed, cut to 64 characters (and a trailing `-`
  removed). Output is deterministic: the format's key order, two-space indentation, `\n` line endings, a final newline.
- Every written file passes the format check (spec section 2): `jasper-theme` 1, a name, schemes with `name` first and
  the 21 colours, every UI role, every editor entry.
- Archive limits: 64 MiB archive, 10,000 entries, 4 MiB per read file, 32 MiB per nested jar, 16 nested jars, 64 themes,
  256 MiB inflated in total. XML with a DOCTYPE or ENTITY declaration is refused (expat handlers, any encoding).
- Exit status 0 success, 1 a conversion failed (the others are still written), 2 usage error.
- `checkThemeTool` fails with exactly "The theme tool's tests need Python 3.11 or later on the PATH" when there is no
  such Python.
- Commit messages end with the trailer block:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- No GUI is launched; no push or merge without the user.

## Review Focus

1. A real plugin whose `plugin.xml` names a theme path that is not in the jar: the conversion fails naming the path,
   never with a traceback (test in Task 1: `Plugins.test_a_theme_plugin_xml_names_but_does_not_contain_is_named`).
2. A theme whose `editorScheme` names a scheme by its name, defined inside the plugin rather than by a path: the
   plugin's scheme is used (test in Task 1: `Plugins.test_an_editor_scheme_named_inside_the_plugin_is_found_by_name`).
3. A hand-edited `.theme.json` that is not strict JSON (a trailing comma) or starts with a byte-order mark: the first
   is refused naming the file, the second converts (tests in Task 1: `Inputs.*`).
4. A theme name with no ASCII letters or digits ("Ночь"): an error that says to use `--name`, and `--name` works (test
   in Task 1: `Plugins.test_a_name_with_no_usable_characters_needs_name`).
5. Output written on Windows: `\n` line endings regardless of platform, so the golden files and the Java loader see the
   same bytes (test in Task 1: `Inputs.test_output_is_byte_stable`).

---

### Task 1: The converter

**Files:**
- Create: `tools/themes/intellij/` (17 files restored from `c1e968ee^`, then `SOURCE.md` edited)
- Create: `tools/themes/testdata/alacritty/gruvbox_dark.toml`, `paper-light.toml`, `no_green.toml`
- Create: `tools/themes/testdata/intellij/plugin/META-INF/plugin.xml`, `plugin/themes/Ocean.theme.json`,
  `plugin/themes/Ocean.xml`, `plugin/themes/Dawn.theme.json`
- Create: `tools/themes/testdata/intellij/loose/Ocean.theme.json`, `loose/Ocean.xml` (copies), `loose/Stray.theme.json`,
  `loose/Doctype.xml`
- Create: `tools/themes/test_jasper_theme.py`
- Create: `tools/themes/jasper_theme.py`
- Create: `tools/themes/testdata/expected/` (five golden files, generated)

**Interfaces:**
- Consumes: Plan 1's `jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json` (the test
  `Format.test_the_roles_are_the_bundled_themes` validates it with the tool's format check).
- Produces: `python3 tools/themes/jasper_theme.py INPUT... [-o DIR] [--force] [--list] [--theme NAME] [--name NAME]
  [--scheme FILE] [--verbose]`; module functions `main(argv)`, `convert_alacritty(path, name=None) -> Result`,
  `convert_intellij_theme(theme, siblings, name, scheme_file=None, loose_dir=None) -> Result`, `validate(doc, where)`,
  `slug(name)`; golden files `gruvbox-dark.json`, `paper-light.json`, `ocean.json`, `ocean-dawn.json`, `stray.json`
  (Task 2's contract test loads them).

- [ ] **Step 1: Restore IntelliJ's themes and schemes**

```bash
python3 - <<'PY'
import pathlib, subprocess
source = "jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/"
names = subprocess.run(["git", "ls-tree", "-r", "--name-only", "c1e968ee^", "--", source], check=True,
                       capture_output=True, text=True).stdout.split()
for name in names:
    target = pathlib.Path("tools/themes/intellij") / name[len(source):]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(subprocess.run(["git", "show", "c1e968ee^:" + name], check=True, capture_output=True).stdout)
print(len(names), "files")
PY
```

Expected: `21 files` (17 recorded in `assets.tsv`, plus `LICENSE.txt`, `NOTICE.txt`, `SOURCE.md` and `assets.tsv`), under `tools/themes/intellij/` (`DefaultColorSchemesManager.xml`, `LICENSE.txt`,
`Light.theme.json`, `Light.xml`, `NOTICE.txt`, `SOURCE.md`, `assets.tsv`, `darcula.theme.json`,
`intellijlaf.theme.json`, `expUI/` (5 files), `islands/` (6 files)).

- [ ] **Step 2: Point `SOURCE.md` at its new home and user**

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path("tools/themes/intellij/SOURCE.md"); t = p.read_text(encoding="utf-8")
edits = [
    ('`DefaultColorSchemesManager.xml`) and 2026-09-29 (the rest) with `tools/themes/fetch-intellij-themes.py`.',
     "`DefaultColorSchemesManager.xml`) and 2026-09-29 (the rest), then restored byte for byte from Jasper's commit\n`c1e968ee^` (where they were `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/`) for the theme\nconverter, `tools/themes/jasper_theme.py`."),
    ("A plugin's `parentTheme` names the id; Jasper's ThemeLoader resolves the chain.",
     "A plugin's `parentTheme` names the id; jasper_theme.py resolves the chain."),
    ('Only IntelliJ Light is offered as a Jasper theme; the others are parents for theme plugins.',
     'None is offered as a Jasper theme: they are the parents and editor schemes that theme plugins build on.'),
    ("Jasper's EditorScheme reads the console colours a scheme leaves out from its parents.",
     'jasper_theme.py reads the colours a scheme leaves out from its parents.')
]
for old, new in edits:
    assert t.count(old) == 1, old[:60]
    t = t.replace(old, new)
p.write_text(t, encoding="utf-8")
PY
```

The file's remaining text (license, the theme and scheme lists) is unchanged; `assets.tsv` still names the
`resource` paths relative to this folder.

- [ ] **Step 3: Write the fixtures**

`tools/themes/testdata/alacritty/gruvbox_dark.toml`:

```toml
# A complete Alacritty theme with '#' colours.
[colors.primary]
background = '#282828'
foreground = '#ebdbb2'
bright_foreground = '#fbf1c7'

[colors.cursor]
text = '#282828'
cursor = '#d5c4a1'

[colors.selection]
text = 'CellForeground'
background = '#504945'

[colors.normal]
black = '#282828'
red = '#cc241d'
green = '#98971a'
yellow = '#d79921'
blue = '#458588'
magenta = '#b16286'
cyan = '#689d6a'
white = '#a89984'

[colors.bright]
black = '#928374'
red = '#fb4934'
green = '#b8bb26'
yellow = '#fabd2f'
blue = '#83a598'
magenta = '#d3869b'
cyan = '#8ec07c'
white = '#ebdbb2'

[colors.dim]
black = '#1d2021'
```

`tools/themes/testdata/alacritty/paper-light.toml`:

```toml
# 0x colours, Cell* references, no bright colours, an import.
import = ["~/.config/alacritty/base.toml"]

[colors.primary]
background = '0xF8F8F4'
foreground = '0x1C1C1C'

[colors.cursor]
cursor = 'CellForeground'

[colors.selection]
background = 'CellForeground'

[colors.normal]
black = '0x1C1C1C'
red = '0xB3261E'
green = '0x2E7D32'
yellow = '0x8A6100'
blue = '0x1F5FBF'
magenta = '0x8E3FA8'
cyan = '0x0F7A8A'
white = '0x5D6470'
```

`tools/themes/testdata/alacritty/no_green.toml`:

```toml
[colors.primary]
background = '#000000'
foreground = '#ffffff'

[colors.normal]
black = '#000000'
red = '#ff0000'
yellow = '#ffff00'
blue = '#0000ff'
magenta = '#ff00ff'
cyan = '#00ffff'
white = '#ffffff'
```

`tools/themes/testdata/intellij/plugin/META-INF/plugin.xml`:

```xml
<idea-plugin>
  <id>dev.jasper.fixture.ocean</id>
  <name>Ocean Themes</name>
  <extensions defaultExtensionNs="com.intellij">
    <themeProvider id="ocean-dark" path="/themes/Ocean.theme.json"/>
    <themeProvider id="ocean-dawn" path="/themes/Dawn.theme.json"/>
  </extensions>
</idea-plugin>
```

`tools/themes/testdata/intellij/plugin/themes/Ocean.theme.json`:

```json
{
  "name": "Ocean",
  "dark": true,
  "author": "Jasper fixtures",
  "editorScheme": "/themes/Ocean.xml",
  "colors": {
    "deep": "#0F1A24",
    "sea": "#16242F",
    "foam": "#D8E4EC",
    "coral": "#FF7A6B",
    "wave": "seaAccent",
    "seaAccent": "#3A9AD9"
  },
  "ui": {
    "*": {
      "background": "sea",
      "foreground": "foam",
      "selectionBackground": "#24496B"
    },
    "StatusBar": { "background": "deep" },
    "Button": {
      "startBackground": "#1E3140",
      "startBorderColor": "#2E4A60",
      "default": { "startBackground": "wave", "foreground": "#FFFFFF" }
    },
    "ActionButton.hoverBackground": "#FFFFFF1A",
    "Component.borderColor": "#2E4A60",
    "Component.errorFocusColor": "coral",
    "Label.infoForeground": "#8FA6B6",
    "Link.activeForeground": { "os.default": "#6CC4FF", "os.windows": "#5AB0F0" },
    "ProgressBar.failedColor": "coral",
    "TextField.background": "#0B141C",
    "Popup.borderColor": "not-a-colour"
  }
}
```

`tools/themes/testdata/intellij/plugin/themes/Ocean.xml`:

```xml
<scheme name="Ocean" version="142" parent_scheme="Darcula">
  <colors>
    <option name="CARET_COLOR" value="ffcc66" />
    <option name="CARET_ROW_COLOR" value="13202b" />
    <option name="CONSOLE_BACKGROUND_KEY" value="0b141c" />
    <option name="LINE_NUMBERS_COLOR" value="4a6274" />
    <option name="SELECTION_BACKGROUND" value="24496b" />
  </colors>
  <attributes>
    <option name="TEXT">
      <value>
        <option name="FOREGROUND" value="d8e4ec" />
        <option name="BACKGROUND" value="0f1a24" />
      </value>
    </option>
    <option name="CONSOLE_RED_OUTPUT">
      <value>
        <option name="FOREGROUND" value="ff7a6b" />
      </value>
    </option>
    <option name="CONSOLE_BLUE_BRIGHT_OUTPUT">
      <value>
        <option name="FOREGROUND" value="8000" />
      </value>
    </option>
    <option name="DEFAULT_KEYWORD">
      <value>
        <option name="FOREGROUND" value="c792ea" />
        <option name="FONT_TYPE" value="1" />
      </value>
    </option>
    <option name="DEFAULT_LINE_COMMENT">
      <value>
        <option name="FOREGROUND" value="5f7e97" />
        <option name="FONT_TYPE" value="2" />
      </value>
    </option>
    <option name="DEFAULT_STRING">
      <value>
        <option name="FOREGROUND" value="c3e88d" />
      </value>
    </option>
    <option name="DEFAULT_FUNCTION_CALL" baseAttributes="DEFAULT_KEYWORD" />
  </attributes>
</scheme>
```

`tools/themes/testdata/intellij/plugin/themes/Dawn.theme.json`:

```json
{
  "name": "Ocean Dawn",
  "dark": false,
  "parentTheme": "ExperimentalLight",
  "editorScheme": "Light",
  "ui": {
    "*": { "background": "#FBF7F2" },
    "Button.default.startBackground": "#C2410C"
  }
}
```

`tools/themes/testdata/intellij/loose/Stray.theme.json`:

```json
{
  "name": "Stray",
  "dark": true,
  "parentTheme": "Some Theme Jasper Does Not Know",
  "ui": { "*": { "background": "#202020" } }
}
```

`tools/themes/testdata/intellij/loose/Doctype.xml`:

```xml
<?xml version="1.0"?>
<!DOCTYPE scheme [ <!ENTITY boom "boom"> ]>
<scheme name="Doctype" version="142" parent_scheme="Darcula"><colors/></scheme>
```

Then copy the Ocean pair for the loose-file tests:

```bash
cp tools/themes/testdata/intellij/plugin/themes/Ocean.theme.json tools/themes/testdata/intellij/plugin/themes/Ocean.xml \
   tools/themes/testdata/intellij/loose/
```

- [ ] **Step 4: Write the failing tests**

`tools/themes/test_jasper_theme.py`:

```python
"""Tests for jasper_theme.py. Run from the repository root:

    python3 -m unittest discover -s tools/themes -p 'test_*.py'

Set JASPER_THEME_UPDATE_GOLDEN=1 to rewrite testdata/expected/ from the current output (then review the diff).
"""

import contextlib
import io
import json
import os
import pathlib
import tempfile
import unittest
import zipfile

import jasper_theme as tool

DATA = pathlib.Path(__file__).resolve().parent / "testdata"
EXPECTED = DATA / "expected"
UPDATE = os.environ.get("JASPER_THEME_UPDATE_GOLDEN") == "1"


def run(*args):
    """Runs the command line; returns (status, stdout, stderr)."""
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            status = tool.main([str(arg) for arg in args])
        except SystemExit as exit_:
            status = exit_.code
    return status, out.getvalue(), err.getvalue()


def plugin_jar(target: pathlib.Path) -> pathlib.Path:
    """testdata/intellij/plugin as a jar, entries in sorted order."""
    root = DATA / "intellij" / "plugin"
    with zipfile.ZipFile(target, "w") as archive:
        for path in sorted(p for p in root.rglob("*") if p.is_file()):
            archive.write(path, path.relative_to(root).as_posix())
    return target


def marketplace_zip(target: pathlib.Path, jar: pathlib.Path) -> pathlib.Path:
    """A Marketplace download: the plugin jar under <name>/lib/."""
    with zipfile.ZipFile(target, "w") as archive:
        archive.write(jar, "Ocean/lib/ocean.jar")
    return target


class Golden(unittest.TestCase):
    """Every fixture's output is committed under testdata/expected/ and compared byte for byte."""

    def convert(self, *inputs, extra=()):
        with tempfile.TemporaryDirectory() as out:
            status, stdout, stderr = run(*inputs, "-o", out, *extra)
            self.assertEqual(status, 0, stderr)
            return {p.name: p.read_text(encoding="utf-8") for p in pathlib.Path(out).iterdir()}, stdout, stderr

    def check(self, written: dict):
        for name, text in written.items():
            golden = EXPECTED / name
            if UPDATE:
                EXPECTED.mkdir(exist_ok=True)
                golden.write_text(text, encoding="utf-8")
            self.assertTrue(golden.is_file(), f"missing golden file {name}; run with JASPER_THEME_UPDATE_GOLDEN=1")
            self.assertEqual(text, golden.read_text(encoding="utf-8"), name)
            tool.validate(json.loads(text), name)

    def test_alacritty_fixtures(self):
        written, stdout, stderr = self.convert(DATA / "alacritty" / "gruvbox_dark.toml",
                                               DATA / "alacritty" / "paper-light.toml")
        self.assertEqual(sorted(written), ["gruvbox-dark.json", "paper-light.json"])
        self.check(written)
        self.assertIn("gruvbox-dark.json: Gruvbox Dark (terminal scheme, dark)", stdout)
        self.assertIn("paper-light.toml: ignoring import", stderr)

    def test_intellij_plugin(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = plugin_jar(pathlib.Path(scratch) / "ocean.jar")
            written, stdout, _ = self.convert(jar)
        self.assertEqual(sorted(written), ["ocean-dawn.json", "ocean.json"])
        self.check(written)
        self.assertRegex(stdout, r"ocean\.json: Ocean \(dark\): \d+ roles from the theme, \d+ from Darcula, \d+ derived; "
                                 r"editor \d+ of 23 from the scheme")

    def test_unknown_parent(self):
        written, _, stderr = self.convert(DATA / "intellij" / "loose" / "Stray.theme.json")
        self.check(written)
        self.assertIn('parent theme "Some Theme Jasper Does Not Know" is not one Jasper knows; using Darcula', stderr)
        self.assertIn("no editor scheme; using Darcula", stderr)


class Alacritty(unittest.TestCase):
    def doc(self, file_name, **kw):
        return tool.convert_alacritty(DATA / "alacritty" / file_name, **kw).doc

    def test_colours_and_keys(self):
        scheme = self.doc("gruvbox_dark.toml")["terminal"]["dark"]
        self.assertEqual(scheme["name"], "Gruvbox Dark")
        self.assertEqual((scheme["bg"], scheme["fg"], scheme["fg.bold"]), ("#282828", "#EBDBB2", "#FBF1C7"))
        self.assertEqual((scheme["cursor"], scheme["selection"]), ("#D5C4A1", "#504945"))
        self.assertEqual((scheme["red"], scheme["bright.red"], scheme["bright.white"]), ("#CC241D", "#FB4934", "#EBDBB2"))

    def test_hex_cell_references_missing_brights_and_light_keying(self):
        doc = self.doc("paper-light.toml")
        self.assertEqual(list(doc["terminal"]), ["light"])
        scheme = doc["terminal"]["light"]
        self.assertEqual(scheme["bg"], "#F8F8F4")
        self.assertEqual(scheme["cursor"], scheme["fg"], "CellForeground is the foreground")
        self.assertEqual(scheme["selection"], scheme["fg"])
        self.assertEqual(scheme["fg.bold"], scheme["fg"], "no bright_foreground")
        for name in tool.ANSI:
            self.assertEqual(scheme["bright." + name], scheme[name], "a missing bright colour is its normal one")

    def test_a_missing_normal_colour_fails(self):
        with self.assertRaisesRegex(tool.ConversionError, r"no_green\.toml: colors\.normal\.green is missing"):
            self.doc("no_green.toml")

    def test_name_override(self):
        self.assertEqual(self.doc("gruvbox_dark.toml", name="Retro")["terminal"]["dark"]["name"], "Retro")

    def test_title_names(self):
        self.assertEqual(tool.title_name("gruvbox_dark"), "Gruvbox Dark")
        self.assertEqual(tool.title_name("paper-light"), "Paper Light")


class IntelliJ(unittest.TestCase):
    def ocean(self, **kw):
        themes, loose = tool.themes_in(DATA / "intellij" / "loose" / "Ocean.theme.json")
        return tool.convert_intellij_theme(themes[0], themes, None, loose_dir=loose, **kw).doc

    def test_named_colours_wildcards_nested_keys_and_os_values(self):
        ui = self.ocean()["ui"]["dark"]
        self.assertEqual(ui["surface.panel"], "#16242F", "*.background through the named colour sea")
        self.assertEqual(ui["surface.app"], "#0F1A24", "StatusBar.background, nested, through deep")
        self.assertEqual(ui["accent"], "#3A9AD9", "Button.default.startBackground, named colour of a named colour")
        self.assertEqual(ui["text.onAccent"], "#FFFFFF")
        self.assertEqual(ui["text.link"], "#6CC4FF", "the os.default value")
        self.assertEqual(ui["selection.bg"], "#24496B", "*.selectionBackground")

    def test_alpha_is_laid_over_the_panel_and_an_invalid_value_falls_through_to_the_parent(self):
        ui = self.ocean()["ui"]["dark"]
        self.assertEqual(ui["surface.control.hover"], tool.hex_of(tool.over((255, 255, 255, 0x1A), (0x16, 0x24, 0x2F, 255))))
        self.assertEqual(ui["border.menu"], "#616161", "Popup.borderColor is not a colour, so Darcula's is used")
        self.assertEqual(ui["surface.scrim"], "#0000008C", "an opacity role keeps its alpha")

    def test_terminal_and_editor_from_the_scheme_chain(self):
        doc = self.ocean()
        terminal, editor = doc["terminal"]["dark"], doc["editor"]["dark"]
        self.assertEqual(terminal["name"], "Ocean")
        self.assertEqual(terminal["bg"], "#0B141C", "CONSOLE_BACKGROUND_KEY")
        darcula = tool.Scheme(tool.vendored_schemes()["Darcula"], [], "")
        self.assertEqual(terminal["fg"], tool.hex_of(darcula.foreground("CONSOLE_NORMAL_OUTPUT")),
                         "the parent's console colour outranks the scheme's own TEXT, as in IntelliJ")
        self.assertEqual(terminal["red"], "#FF7A6B")
        self.assertEqual(terminal["bright.blue"], "#008000", "a short value is hexadecimal RGB")
        self.assertEqual(terminal["green"], tool.hex_of(darcula.foreground("CONSOLE_GREEN_OUTPUT")), "the rest come from Darcula")
        self.assertEqual(doc["ui"]["dark"]["surface.terminal"], terminal["bg"])
        self.assertEqual(editor["background"], "#0F1A24")
        self.assertEqual(editor["caret"], "#FFCC66")
        self.assertEqual(editor["keyword"], "#C792EA, bold")
        self.assertEqual(editor["comment"], "#5F7E97, italic")
        self.assertEqual(editor["function"], tool.hex_of(darcula.foreground("DEFAULT_FUNCTION_DECLARATION")),
                         "the first attribute in the chain wins: Darcula's declaration colour")
        scheme = tool.Scheme(tool.parse_xml((DATA / "intellij" / "loose" / "Ocean.xml").read_bytes(), "Ocean.xml"), [], "")
        self.assertEqual(scheme.foreground("DEFAULT_FUNCTION_CALL"), (0xC7, 0x92, 0xEA, 255), "baseAttributes is followed")
        self.assertEqual(scheme.attribute("DEFAULT_FUNCTION_CALL", "FONT_TYPE"), "1")
        self.assertEqual(editor["string"], "#C3E88D")

    def test_a_light_theme_on_a_vendored_parent_and_scheme(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = plugin_jar(pathlib.Path(scratch) / "ocean.jar")
            themes = tool.plugin_themes(tool.Files.archive(jar))
            dawn = next(t for t in themes if t.id == "ocean-dawn")
            doc = tool.convert_intellij_theme(dawn, themes, None).doc
        self.assertEqual(list(doc["ui"]), ["light"])
        self.assertEqual(doc["ui"]["light"]["accent"], "#C2410C")
        self.assertEqual(doc["ui"]["light"]["surface.panel"], "#FBF7F2")
        self.assertEqual(doc["ui"]["light"]["hover.overlay"], "#000000")
        self.assertNotIn("author", doc)

    def test_loose_theme_with_scheme_option(self):
        themes, loose = tool.themes_in(DATA / "intellij" / "loose" / "Ocean.theme.json")
        doc = tool.convert_intellij_theme(themes[0], themes, "Sea", scheme_file=DATA / "intellij" / "loose" / "Ocean.xml",
                                          loose_dir=loose).doc
        self.assertEqual(doc["name"], "Sea")
        self.assertEqual(doc["terminal"]["dark"]["name"], "Sea")

    def test_the_loose_and_packaged_theme_agree(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = plugin_jar(pathlib.Path(scratch) / "ocean.jar")
            themes = tool.plugin_themes(tool.Files.archive(jar))
            packaged = tool.convert_intellij_theme(next(t for t in themes if t.id == "ocean-dark"), themes, None).doc
        self.assertEqual(packaged, self.ocean())

    def test_marketplace_zip_list_and_theme(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = plugin_jar(pathlib.Path(scratch) / "ocean.jar")
            download = marketplace_zip(pathlib.Path(scratch) / "Ocean.zip", jar)
            status, stdout, _ = run("--list", download)
            self.assertEqual(status, 0)
            self.assertIn("Ocean.zip: Ocean (id ocean-dark, Dark)", stdout)
            self.assertIn("Ocean.zip: Ocean Dawn (id ocean-dawn, Light)", stdout)
            out = pathlib.Path(scratch) / "out"
            status, stdout, stderr = run(download, "--theme", "Ocean Dawn", "-o", out)
            self.assertEqual(status, 0, stderr)
            self.assertEqual([p.name for p in out.iterdir()], ["ocean-dawn.json"])


class Refusals(unittest.TestCase):
    def test_xml_with_a_doctype_or_entity_is_refused_in_any_encoding(self):
        with self.assertRaisesRegex(tool.ConversionError, "DOCTYPE or ENTITY"):
            tool.parse_xml((DATA / "intellij" / "loose" / "Doctype.xml").read_bytes(), "Doctype.xml")
        utf16 = '<?xml version="1.0" encoding="UTF-16"?><!DOCTYPE x [<!ENTITY a "b">]><x>&a;</x>'.encode("utf-16")
        with self.assertRaisesRegex(tool.ConversionError, "DOCTYPE or ENTITY"):
            tool.parse_xml(utf16, "utf16.xml")

    def test_archive_limits(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = plugin_jar(pathlib.Path(scratch) / "ocean.jar")
            for name, value, message in (("MAX_ENTRIES", 3, "more than 10,000 entries"),
                                         ("MAX_FILE", 100, "larger than"),
                                         ("MAX_THEMES", 1, "more than 64 themes"),
                                         ("MAX_ARCHIVE", 100, "larger than 64 MiB")):
                with self.subTest(name), unittest.mock.patch.object(tool, name, value):
                    with self.assertRaisesRegex(tool.ConversionError, message):
                        tool.plugin_themes(tool.Files.archive(jar))

    def test_a_file_that_is_not_a_zip(self):
        with tempfile.TemporaryDirectory() as scratch:
            bad = pathlib.Path(scratch) / "bad.jar"
            bad.write_bytes(b"not a zip")
            with self.assertRaisesRegex(tool.ConversionError, "bad.jar: not a zip or jar file"):
                tool.Files.archive(bad)

    def test_existing_output_needs_force(self):
        with tempfile.TemporaryDirectory() as out:
            source = DATA / "alacritty" / "gruvbox_dark.toml"
            self.assertEqual(run(source, "-o", out)[0], 0)
            status, _, stderr = run(source, "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("exists; use --force", stderr)
            self.assertEqual(run(source, "-o", out, "--force")[0], 0)

    def test_name_needs_one_theme(self):
        with tempfile.TemporaryDirectory() as out:
            status, _, stderr = run(DATA / "alacritty" / "gruvbox_dark.toml", DATA / "alacritty" / "paper-light.toml",
                                    "--name", "X", "-o", out)
            self.assertEqual(status, 2)
            self.assertIn("--name needs exactly one theme", stderr)

    def test_an_unknown_input_type_and_a_failed_input_keep_the_others(self):
        with tempfile.TemporaryDirectory() as out:
            status, stdout, stderr = run(DATA / "intellij" / "plugin" / "META-INF" / "plugin.xml",
                                         DATA / "alacritty" / "no_green.toml", DATA / "alacritty" / "gruvbox_dark.toml",
                                         "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("plugin.xml: unknown input type", stderr)
            self.assertIn("colors.normal.green is missing", stderr)
            self.assertEqual([p.name for p in pathlib.Path(out).iterdir()], ["gruvbox-dark.json"])


class Inputs(unittest.TestCase):
    def test_a_theme_json_that_is_not_strict_json_names_its_file(self):
        with tempfile.TemporaryDirectory() as scratch:
            loose = pathlib.Path(scratch) / "Loose.theme.json"
            loose.write_text('{ "name": "Loose", "dark": true, }', encoding="utf-8")
            status, _, stderr = run(loose, "-o", pathlib.Path(scratch) / "out")
            self.assertEqual(status, 1)
            self.assertIn("Loose.theme.json: not a JSON file", stderr)

    def test_a_byte_order_mark_is_accepted(self):
        with tempfile.TemporaryDirectory() as scratch:
            bom = pathlib.Path(scratch) / "Bom.theme.json"
            bom.write_bytes(b"\xef\xbb\xbf" + b'{ "name": "Bom", "dark": true }')
            status, stdout, stderr = run(bom, "-o", pathlib.Path(scratch) / "out")
            self.assertEqual(status, 0, stderr)
            self.assertIn("bom.json: Bom (dark)", stdout)

    def test_two_themes_with_one_file_name_are_refused(self):
        with tempfile.TemporaryDirectory() as scratch:
            out = pathlib.Path(scratch) / "out"
            status, _, stderr = run(DATA / "alacritty" / "gruvbox_dark.toml", DATA / "alacritty" / "gruvbox_dark.toml", "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("would both be written to gruvbox-dark.json", stderr)
            self.assertEqual([p.name for p in out.iterdir()], ["gruvbox-dark.json"])

    def test_output_is_byte_stable(self):
        with tempfile.TemporaryDirectory() as out:
            run(DATA / "alacritty" / "gruvbox_dark.toml", "-o", out)
            data = (pathlib.Path(out) / "gruvbox-dark.json").read_bytes()
            self.assertNotIn(b"\r", data)
            self.assertTrue(data.endswith(b"}\n"))


class Plugins(unittest.TestCase):
    def jar(self, scratch, edit=None):
        """The fixture plugin as a jar, with {path: text} replacing or adding entries."""
        root = DATA / "intellij" / "plugin"
        target = pathlib.Path(scratch) / "edited.jar"
        entries = {p.relative_to(root).as_posix(): p.read_bytes() for p in sorted(root.rglob("*")) if p.is_file()}
        for path, text in (edit or {}).items():
            entries[path] = text.encode("utf-8")
        with zipfile.ZipFile(target, "w") as archive:
            for path, data in entries.items():
                archive.writestr(path, data)
        return target

    def test_a_theme_plugin_xml_names_but_does_not_contain_is_named(self):
        with tempfile.TemporaryDirectory() as scratch:
            plugin_xml = (DATA / "intellij" / "plugin" / "META-INF" / "plugin.xml").read_text(encoding="utf-8")
            jar = self.jar(scratch, {"META-INF/plugin.xml": plugin_xml.replace("/themes/Dawn.theme.json", "/themes/Missing.theme.json")})
            with self.assertRaisesRegex(tool.ConversionError, "plugin.xml names themes/Missing.theme.json, which is not in the plugin"):
                tool.plugin_themes(tool.Files.archive(jar))

    def test_an_editor_scheme_named_inside_the_plugin_is_found_by_name(self):
        with tempfile.TemporaryDirectory() as scratch:
            dawn = json.loads((DATA / "intellij" / "plugin" / "themes" / "Dawn.theme.json").read_text(encoding="utf-8"))
            dawn["editorScheme"] = "Ocean"
            jar = self.jar(scratch, {"themes/Dawn.theme.json": json.dumps(dawn)})
            themes = tool.plugin_themes(tool.Files.archive(jar))
            doc = tool.convert_intellij_theme(next(t for t in themes if t.id == "ocean-dawn"), themes, None).doc
        self.assertEqual(doc["terminal"]["light"]["bg"], "#0B141C", "Ocean.xml's console background")

    def test_a_name_with_no_usable_characters_needs_name(self):
        with tempfile.TemporaryDirectory() as scratch:
            night = pathlib.Path(scratch) / "night.theme.json"
            night.write_text('{ "name": "\u041d\u043e\u0447\u044c", "dark": true }', encoding="utf-8")
            status, _, stderr = run(night, "-o", pathlib.Path(scratch) / "out")
            self.assertEqual(status, 1)
            self.assertIn("the name has no letters or digits for a file name; use --name", stderr)
            self.assertEqual(run(night, "--name", "Night", "-o", pathlib.Path(scratch) / "out")[0], 0)


class Format(unittest.TestCase):
    def test_slugs(self):
        self.assertEqual(tool.slug("Dracula"), "dracula")
        self.assertEqual(tool.slug("Caf\u00e9  Noir!"), "cafe-noir")
        self.assertEqual(tool.slug("\u041d\u043e\u0447\u044c"), "")
        self.assertEqual(len(tool.slug("x" * 80)), 64)
        self.assertEqual(tool.slug(("a" * 63) + " b"), "a" * 63)

    def test_the_check_rejects_a_missing_entry(self):
        themes, loose = tool.themes_in(DATA / "intellij" / "loose" / "Ocean.theme.json")
        doc = tool.convert_intellij_theme(themes[0], themes, None, loose_dir=loose).doc
        del doc["ui"]["dark"]["accent"]
        with self.assertRaisesRegex(tool.ConversionError, "internal: ui.dark roles"):
            tool.validate(doc, "ocean.json")
        scheme = tool.convert_alacritty(DATA / "alacritty" / "gruvbox_dark.toml").doc
        del scheme["terminal"]["dark"]["fg.bold"]
        with self.assertRaisesRegex(tool.ConversionError, "internal: terminal.dark keys"):
            tool.validate(scheme, "gruvbox-dark.json")

    def test_the_vendored_intellij_files_are_the_recorded_upstream_copies(self):
        import hashlib
        rows = (tool.INTELLIJ / "assets.tsv").read_text(encoding="utf-8").splitlines()[1:]
        self.assertEqual(len(rows), 17)
        for row in rows:
            resource, _, sha256 = row.split("\t")
            with self.subTest(resource):
                self.assertEqual(hashlib.sha256((tool.INTELLIJ / resource).read_bytes()).hexdigest(), sha256)

    def test_the_roles_are_the_bundled_themes(self):
        bundled = pathlib.Path(__file__).resolve().parents[2] / "jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json"
        if not bundled.is_file():
            self.skipTest("not in the repository")
        doc = json.loads(bundled.read_text(encoding="utf-8"))
        tool.validate(doc, "slate-steel.json")
        self.assertEqual(list(doc["editor"]["dark"]), tool.EDITOR_SURFACES + tool.SYNTAX_ROLES)


import unittest.mock  # noqa: E402  (used by Refusals)

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 5: Run the tests to see them fail**

Run: `python3 -m unittest discover -s tools/themes -p 'test_*.py'`
Expected: `ModuleNotFoundError: No module named 'jasper_theme'` (one error for the module).

- [ ] **Step 6: Write the tool**

`tools/themes/jasper_theme.py`:

```python
#!/usr/bin/env python3
"""Convert Alacritty and IntelliJ themes into Jasper theme files.

    python3 tools/themes/jasper_theme.py Dracula.jar gruvbox_dark.toml -o ~/Desktop/themes

Alacritty themes (.toml) become terminal-only Jasper themes; IntelliJ themes (.theme.json, or a plugin .jar or
Marketplace .zip) become UI themes with their own terminal and editor schemes. The format is described in
docs/themes.md. Standard library only; Python 3.11 or later.
"""

from __future__ import annotations

import argparse
import io
import json
import pathlib
import re
import sys
import unicodedata
import xml.etree.ElementTree as ElementTree
import xml.parsers.expat
import zipfile
from dataclasses import dataclass, field

if sys.version_info < (3, 11):
    sys.exit("jasper_theme.py needs Python 3.11 or later")

import tomllib  # noqa: E402  (3.11+)

HERE = pathlib.Path(__file__).resolve().parent
INTELLIJ = HERE / "intellij"

# ---------------------------------------------------------------------------------------------------------------
# The Jasper theme format (docs/themes.md; the Java loader is dev.jasper.ui.theme.JasperTheme).

FORMAT = 1
ROLES = [
    "surface.app", "surface.panel", "surface.panel.raised", "surface.dialog", "surface.card", "surface.sidebar",
    "surface.input", "surface.field.raised", "surface.control", "surface.control.hover", "surface.control.selected",
    "surface.menu", "surface.terminal", "surface.tabstrip", "surface.emptyTile", "surface.scrim",
    "border.window", "border.subtle", "border.input", "border.field.raised", "border.field.raised.hover",
    "border.control", "border.menu", "border.error",
    "text.primary", "text.secondary", "text.strong.secondary", "text.tertiary", "text.placeholder", "text.field.raised",
    "text.onAccent", "text.onAccent.secondary", "text.link", "text.link.hover", "text.error", "text.success",
    "accent", "accent.hover", "selection.bg", "hover.overlay",
    "segment.track", "segment.selected.bg", "toggle.track.off", "toggle.knob",
    "button.secondary.bg", "button.secondary.bg.hover", "button.secondary.border",
    "status.success", "status.warning", "status.danger", "status.idle",
    "hue.blue", "hue.green", "hue.amber", "hue.purple", "hue.neutral",
]
ANSI = ["black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"]
TERMINAL_KEYS = ["bg", "fg", "fg.bold", "cursor", "selection"] + ANSI + ["bright." + name for name in ANSI]
EDITOR_SURFACES = ["background", "foreground", "caret", "selection", "currentLine", "gutter", "lineNumbers"]
SYNTAX_ROLES = ["keyword", "type", "function", "variable", "constant", "number", "string", "comment", "doc_comment",
                "annotation", "operator", "punctuation", "tag", "attribute", "regex", "error"]
# Slate Steel's editor references (slate-steel.json, editor.dark): used for an entry the editor scheme leaves out.
EDITOR_REFERENCES = {
    "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor",
    "selection": "terminal.selection", "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg",
    "lineNumbers": "text.tertiary", "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan",
    "function": "terminal.ansi.blue", "variable": "terminal.fg", "constant": "terminal.ansi.yellow",
    "number": "terminal.ansi.yellow", "string": "terminal.ansi.green", "comment": "terminal.ansi.bright.black, italic",
    "doc_comment": "terminal.ansi.bright.black, italic", "annotation": "terminal.ansi.yellow",
    "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
    "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red",
}
KEY = re.compile(r"[a-z0-9]+(-[a-z0-9]+)*")
HEX = re.compile(r"#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")
EDITOR_VALUE = re.compile(r"(#[0-9A-Fa-f]{6}|[A-Za-z][A-Za-z0-9.]*)( @ \d{1,3}%)?(, italic)?(, bold)?")


class ConversionError(Exception):
    """A theme that cannot be converted; the message names the file and the problem."""


# ---------------------------------------------------------------------------------------------------------------
# Colours: (r, g, b, a) tuples of 0-255 ints.

def parse_hex(text: str) -> tuple[int, int, int, int] | None:
    """#RGB, #RRGGBB or #RRGGBBAA, with or without '#'; None when the text is not one."""
    digits = text[1:] if text.startswith("#") else text
    if not re.fullmatch(r"[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}", digits):
        return None
    if len(digits) == 3:
        digits = "".join(c * 2 for c in digits)
    alpha = int(digits[6:8], 16) if len(digits) == 8 else 255
    return int(digits[0:2], 16), int(digits[2:4], 16), int(digits[4:6], 16), alpha


def hex_of(color: tuple[int, int, int, int]) -> str:
    r, g, b, a = color
    return f"#{r:02X}{g:02X}{b:02X}" + ("" if a == 255 else f"{a:02X}")


def over(top, bottom):
    """``top`` laid over the opaque ``bottom``."""
    t = top[3] / 255
    return tuple(round(top[i] * t + bottom[i] * (1 - t)) for i in range(3)) + (255,)


def mix(a, b, share: float):
    """``a`` moved ``share`` (0-1) toward ``b``; opaque."""
    return tuple(round(a[i] * (1 - share) + b[i] * share) for i in range(3)) + (255,)


def with_alpha(color, alpha: int):
    return color[:3] + (alpha,)


def luminance(color) -> float:
    def channel(value):
        c = value / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * channel(color[0]) + 0.7152 * channel(color[1]) + 0.0722 * channel(color[2])


def contrast(a, b) -> float:
    first, second = luminance(a), luminance(b)
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)


WHITE, BLACK = (255, 255, 255, 255), (0, 0, 0, 255)


# ---------------------------------------------------------------------------------------------------------------
# Names and output.

def slug(name: str) -> str:
    """The file name (theme id) for a display name, as Import derives it; '' when nothing usable remains."""
    plain = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode("ascii").lower()
    text = re.sub(r"[^a-z0-9]+", "-", plain).strip("-")
    return text[:64].rstrip("-")


def title_name(stem: str) -> str:
    """``gruvbox_dark`` is "Gruvbox Dark"."""
    words = [word for word in re.split(r"[_\-\s]+", stem) if word]
    return " ".join(word[:1].upper() + word[1:] for word in words)


def validate(doc: dict, where: str) -> None:
    """The format's rules, so the tool never writes a file Import rejects. Raises ConversionError."""
    def fail(message):
        raise ConversionError(f"{where}: internal: {message}")
    allowed = {"jasper-theme", "name", "author", "ui", "terminal", "editor"}
    if set(doc) - allowed:
        fail(f"unknown keys {sorted(set(doc) - allowed)}")
    if doc.get("jasper-theme") != FORMAT or not isinstance(doc.get("name"), str) or not doc["name"]:
        fail("marker or name")
    terminal = doc.get("terminal")
    if not isinstance(terminal, dict) or not terminal:
        fail("no terminal schemes")
    for key, scheme in terminal.items():
        if not KEY.fullmatch(key) or len(key) > 32:
            fail(f"scheme key {key!r}")
        if list(scheme) != ["name"] + TERMINAL_KEYS or not scheme["name"]:
            fail(f"terminal.{key} keys")
        for name in TERMINAL_KEYS:
            if not re.fullmatch(r"#[0-9A-F]{6}", scheme[name]):
                fail(f"terminal.{key}.{name} = {scheme[name]!r}")
    ui = doc.get("ui")
    if ui is None:
        if "editor" in doc:
            fail("editor without ui")
        return
    if not ui or set(ui) - {"dark", "light"} or set(doc.get("editor", {})) != set(ui):
        fail("ui/editor variants")
    for variant, roles in ui.items():
        if variant not in terminal:
            fail(f"ui.{variant} has no matching scheme")
        if list(roles) != ROLES:
            fail(f"ui.{variant} roles")
        for role, value in roles.items():
            if not HEX.fullmatch(value):
                fail(f"ui.{variant}.{role} = {value!r}")
        editor = doc["editor"][variant]
        if list(editor) != EDITOR_SURFACES + SYNTAX_ROLES:
            fail(f"editor.{variant} entries")
        for entry, value in editor.items():
            match = EDITOR_VALUE.fullmatch(value)
            if not match or (entry in EDITOR_SURFACES and (match.group(3) or match.group(4))):
                fail(f"editor.{variant}.{entry} = {value!r}")


def dump(doc: dict) -> str:
    return json.dumps(doc, indent=2, ensure_ascii=False) + "\n"


# ---------------------------------------------------------------------------------------------------------------
# Alacritty.

def alacritty_colour(value, where: str, fg=None, bg=None):
    if not isinstance(value, str):
        raise ConversionError(f"{where}: expected a colour such as \"#282a36\", got {value!r}")
    if value == "CellForeground" and fg is not None:
        return fg
    if value == "CellBackground" and bg is not None:
        return bg
    text = value.strip()
    if re.fullmatch(r"(#|0[xX])[0-9A-Fa-f]{6}", text):
        digits = text[-6:]
        return int(digits[0:2], 16), int(digits[2:4], 16), int(digits[4:6], 16), 255
    raise ConversionError(f"{where}: expected #rrggbb or 0xrrggbb, got {value!r}")


@dataclass
class Result:
    """One converted theme: its document, summary line and warnings."""
    doc: dict
    summary: str
    warnings: list[str] = field(default_factory=list)
    sources: list[str] = field(default_factory=list)


def convert_alacritty(path: pathlib.Path, name: str | None = None) -> Result:
    label = path.name
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except (tomllib.TOMLDecodeError, UnicodeDecodeError) as failure:
        raise ConversionError(f"{label}: not a TOML file: {failure}") from None
    warnings = []
    if "import" in data or "import" in data.get("general", {}):
        warnings.append(f"{label}: ignoring import; colours from imported files are missing")
    colors = data.get("colors")
    if not isinstance(colors, dict):
        raise ConversionError(f"{label}: no [colors] table")
    primary = colors.get("primary", {})
    for required in ("background", "foreground"):
        if required not in primary:
            raise ConversionError(f"{label}: colors.primary.{required} is missing")
    bg = alacritty_colour(primary["background"], f"{label}: colors.primary.background")
    fg = alacritty_colour(primary["foreground"], f"{label}: colors.primary.foreground")
    bold = alacritty_colour(primary["bright_foreground"], f"{label}: colors.primary.bright_foreground") \
        if "bright_foreground" in primary else fg
    cursor_table, selection_table = colors.get("cursor", {}), colors.get("selection", {})
    cursor = alacritty_colour(cursor_table["cursor"], f"{label}: colors.cursor.cursor", fg, bg) \
        if "cursor" in cursor_table else fg
    selection = alacritty_colour(selection_table["background"], f"{label}: colors.selection.background", fg, bg) \
        if "background" in selection_table else mix(bg, fg, 0.25)
    normal, bright = colors.get("normal", {}), colors.get("bright", {})
    ansi = []
    for colour in ANSI:
        if colour not in normal:
            raise ConversionError(f"{label}: colors.normal.{colour} is missing")
        ansi.append(alacritty_colour(normal[colour], f"{label}: colors.normal.{colour}"))
    for index, colour in enumerate(ANSI):
        ansi.append(alacritty_colour(bright[colour], f"{label}: colors.bright.{colour}") if colour in bright
                    else ansi[index])
    display = name or title_name(path.stem)
    key = "dark" if luminance(bg) < luminance(fg) else "light"
    scheme = {"name": display}
    for k, colour in zip(TERMINAL_KEYS, [bg, fg, bold, cursor, selection] + ansi):
        scheme[k] = hex_of(colour[:3] + (255,))
    doc = {"jasper-theme": FORMAT, "name": display, "terminal": {key: scheme}}
    validate(doc, label)
    return Result(doc, f"{display} (terminal scheme, {key})", warnings)


# ---------------------------------------------------------------------------------------------------------------
# Reading IntelliJ plugins (ported from the removed Java ThemeArchive).

MAX_ARCHIVE = 64 << 20
MAX_ENTRIES = 10_000
MAX_FILE = 4 << 20
MAX_NESTED = 32 << 20
MAX_NESTED_JARS = 16
MAX_THEMES = 64
MAX_INFLATED = 256 << 20


class Files:
    """A plugin's files, read in memory within the limits; paths are archive paths without a leading '/'."""

    def __init__(self, label: str):
        self.label = label
        self.entries: dict[str, tuple[zipfile.ZipFile, zipfile.ZipInfo]] = {}
        self.loose: dict[str, bytes] = {}
        self.inflated = 0

    @staticmethod
    def archive(path: pathlib.Path) -> "Files":
        files = Files(path.name)
        if path.stat().st_size > MAX_ARCHIVE:
            raise ConversionError(f"{path.name}: larger than 64 MiB")
        outer = files.open_zip(path.read_bytes(), path.name)
        jars = sorted(name for name in outer.namelist() if re.fullmatch(r"[^/]+/lib/[^/]+\.jar|lib/[^/]+\.jar", name))
        if jars and not any(name.endswith("META-INF/plugin.xml") for name in outer.namelist()):
            if len(jars) > MAX_NESTED_JARS:
                raise ConversionError(f"{path.name}: more than 16 jars in lib/")
            for jar in jars:
                info = outer.getinfo(jar)
                files.open_zip(files.read_entry(outer, info, MAX_NESTED, f"{path.name}!{jar}"), f"{path.name}!{jar}")
        return files

    def open_zip(self, data: bytes, label: str) -> zipfile.ZipFile:
        try:
            archive = zipfile.ZipFile(io.BytesIO(data))
        except zipfile.BadZipFile:
            raise ConversionError(f"{label}: not a zip or jar file") from None
        infos = archive.infolist()
        if len(infos) > MAX_ENTRIES:
            raise ConversionError(f"{label}: more than 10,000 entries")
        for info in infos:
            if not info.is_dir():
                self.entries.setdefault(info.filename.lstrip("/"), (archive, info))
        return archive

    def read_entry(self, archive, info, limit: int, label: str) -> bytes:
        if info.file_size > limit:
            raise ConversionError(f"{label}: larger than {limit >> 20} MiB")
        with archive.open(info) as stream:
            data = stream.read(limit + 1)
        if len(data) > limit:
            raise ConversionError(f"{label}: larger than {limit >> 20} MiB")
        self.inflated += len(data)
        if self.inflated > MAX_INFLATED:
            raise ConversionError(f"{self.label}: inflates to more than 256 MiB")
        return data

    def names(self) -> list[str]:
        return sorted(set(self.entries) | set(self.loose))

    def read(self, name: str) -> bytes | None:
        name = name.lstrip("/")
        if name in self.loose:
            return self.loose[name]
        if name not in self.entries:
            return None
        archive, info = self.entries[name]
        return self.read_entry(archive, info, MAX_FILE, f"{self.label}!{name}")


def parse_xml(data: bytes, label: str) -> ElementTree.Element:
    """An element tree built straight from expat, which refuses any DOCTYPE or entity declaration (in any encoding),
    so no entity can be defined, expanded or fetched."""
    builder = ElementTree.TreeBuilder()
    parser = xml.parsers.expat.ParserCreate()

    def refuse(*_):
        raise ConversionError(f"{label}: XML with a DOCTYPE or ENTITY declaration is refused")
    parser.StartDoctypeDeclHandler = refuse
    parser.EntityDeclHandler = refuse
    parser.StartElementHandler = builder.start
    parser.EndElementHandler = builder.end
    try:
        parser.Parse(data, True)
        return builder.close()
    except (xml.parsers.expat.ExpatError, AssertionError) as failure:
        raise ConversionError(f"{label}: not well-formed XML: {failure}") from None


def parse_json(data: bytes, label: str) -> dict:
    try:
        value = json.loads(data.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as failure:
        raise ConversionError(f"{label}: not a JSON file: {failure}") from None
    if not isinstance(value, dict):
        raise ConversionError(f"{label}: expected a JSON object")
    return value


@dataclass
class ThemeFile:
    """A .theme.json in a plugin (or vendored): its IntelliJ id, path and parsed content."""
    id: str
    path: str
    data: dict
    files: Files | None  # None for vendored themes


def plugin_themes(files: Files) -> list[ThemeFile]:
    """The themes a plugin declares in plugin.xml's themeProvider entries, else every *.theme.json in it."""
    providers = []
    for name in files.names():
        if name.endswith("META-INF/plugin.xml"):
            root = parse_xml(files.read(name), f"{files.label}!{name}")
            for element in root.iter("themeProvider"):
                if element.get("path"):
                    providers.append((element.get("id") or "", element.get("path").lstrip("/")))
    if not providers:
        providers = [("", name) for name in files.names() if name.endswith(".theme.json")]
    if len(providers) > MAX_THEMES:
        raise ConversionError(f"{files.label}: more than 64 themes")
    themes = []
    for theme_id, path in providers:
        data = files.read(path)
        if data is None:
            raise ConversionError(f"{files.label}: plugin.xml names {path}, which is not in the plugin")
        content = parse_json(data, f"{files.label}!{path}")
        themes.append(ThemeFile(theme_id or str(content.get("name", path)), path, content, files))
    return themes


# ---------------------------------------------------------------------------------------------------------------
# IntelliJ's own themes and editor schemes (vendored, Apache License 2.0; see intellij/SOURCE.md).

VENDORED_THEMES = {
    "Darcula": "darcula.theme.json", "IntelliJ": "intellijlaf.theme.json", "JetBrainsLightTheme": "Light.theme.json",
    "ExperimentalDark": "expUI/expUI_dark.theme.json", "ExperimentalLight": "expUI/expUI_light.theme.json",
    "ExperimentalLightWithLightHeader": "expUI/expUI_light_with_light_header.theme.json",
    "Islands Dark": "islands/ManyIslandsDark.theme.json", "Islands Light": "islands/ManyIslandsLight.theme.json",
    "Islands Darcula": "islands/ManyIslandsDarcula.theme.json",
    "JetBrainsHighContrastTheme": "islands/HighContrast.theme.json",
}
VENDORED_SCHEME_FILES = [
    "DefaultColorSchemesManager.xml", "Light.xml", "expUI/expUI_lightScheme.xml", "expUI/expUI_darkScheme.xml",
    "expUI/expUI_darculaContrastScheme.xml", "islands/highContrastScheme.xml", "islands/IslandSchemeDark.xml",
]
_vendored_themes: dict[str, ThemeFile] = {}
_vendored_schemes: dict[str, ElementTree.Element] = {}


def vendored_theme(theme_id: str) -> ThemeFile | None:
    if theme_id not in VENDORED_THEMES:
        return None
    if theme_id not in _vendored_themes:
        path = INTELLIJ / VENDORED_THEMES[theme_id]
        _vendored_themes[theme_id] = ThemeFile(theme_id, path.name, parse_json(path.read_bytes(), path.name), None)
    return _vendored_themes[theme_id]


def vendored_schemes() -> dict[str, ElementTree.Element]:
    if not _vendored_schemes:
        for name in VENDORED_SCHEME_FILES:
            root = parse_xml((INTELLIJ / name).read_bytes(), name)
            for scheme in ([root] if root.tag == "scheme" else root.iter("scheme")):
                _vendored_schemes[scheme.get("name")] = scheme
    return _vendored_schemes


# ---------------------------------------------------------------------------------------------------------------
# Resolving an IntelliJ theme.

@dataclass
class Effective:
    """A theme with its parents applied: the variant, named colours, flattened ui keys and the editor scheme."""
    dark: bool
    colors: dict[str, str]
    levels: list[tuple[str, dict]]  # (source, flattened ui), the theme itself first, its base last
    scheme: ElementTree.Element | None
    scheme_from: str
    chain: list[str]


def flatten(prefix: str, value: dict, out: dict) -> None:
    for key, item in value.items():
        name = f"{prefix}.{key}" if prefix else key
        if isinstance(item, dict) and not any(k.startswith("os.") for k in item):
            flatten(name, item, out)
        else:
            out[name] = item


def resolve(theme: ThemeFile, siblings: list[ThemeFile], warnings: list[str], scheme_file: pathlib.Path | None,
            loose_dir: pathlib.Path | None) -> Effective:
    chain: list[ThemeFile] = []
    current = theme
    while current is not None:
        if any(current is seen for seen in chain) or len(chain) > 16:
            raise ConversionError(f"{theme.path}: parentTheme loops")
        chain.append(current)
        parent = current.data.get("parentTheme")
        if not parent:
            if current.files is not None:
                dark = bool(current.data.get("dark", False))
                chain.append(vendored_theme("Darcula" if dark else "IntelliJ"))
            break
        sibling = next((t for t in siblings if t is not current and parent in (t.id, t.data.get("name"))), None)
        found = sibling or vendored_theme(parent)
        if found is None:
            dark = bool(next((t.data["dark"] for t in chain if "dark" in t.data), False))
            warnings.append(f"{theme.path}: parent theme \"{parent}\" is not one Jasper knows; "
                            f"using {'Darcula' if dark else 'IntelliJ'}")
            found = vendored_theme("Darcula" if dark else "IntelliJ")
        current = found
    dark = next((bool(t.data["dark"]) for t in chain if "dark" in t.data), False)
    colors: dict[str, str] = {}
    for item in reversed(chain):
        for key, value in item.data.get("colors", {}).items():
            if isinstance(value, str):
                colors[key] = value
    levels = []
    for item in chain:
        flat: dict = {}
        flatten("", item.data.get("ui", {}), flat)
        levels.append(("theme" if item is theme else str(item.data.get("name", item.id)), flat))
    scheme, scheme_from = find_scheme(chain, theme, warnings, scheme_file, loose_dir, dark)
    return Effective(dark, colors, levels, scheme, scheme_from, [str(t.data.get("name", t.id)) for t in chain])


def find_scheme(chain, theme, warnings, scheme_file, loose_dir, dark):
    if scheme_file is not None:
        return parse_xml(scheme_file.read_bytes(), scheme_file.name), "theme"
    for item in chain:
        reference = item.data.get("editorScheme")
        if not reference:
            continue
        source = "theme" if item is theme else str(item.data.get("name", item.id))
        if item.files is not None and ("/" in reference or reference.endswith((".xml", ".icls"))):
            data = item.files.read(reference)
            if data is None and loose_dir is not None:
                candidate = loose_dir / pathlib.PurePosixPath(reference).name
                data = candidate.read_bytes() if candidate.is_file() else None
            if data is not None:
                return parse_xml(data, f"{item.files.label}!{reference}"), source
            warnings.append(f"{theme.path}: editor scheme {reference} is not in the plugin; "
                            f"using {'Darcula' if dark else 'Default'}")
            break
        if item.files is not None:
            for name in item.files.names():
                if name.endswith((".xml", ".icls")) and not name.endswith("plugin.xml"):
                    root = parse_xml(item.files.read(name), f"{item.files.label}!{name}")
                    if root.tag == "scheme" and root.get("name") == reference:
                        return root, source
        if reference in vendored_schemes():
            return vendored_schemes()[reference], source
        warnings.append(f"{theme.path}: editor scheme \"{reference}\" is not one Jasper knows; "
                        f"using {'Darcula' if dark else 'Default'}")
        break
    else:
        if theme.files is not None and not theme.data.get("editorScheme"):
            warnings.append(f"{theme.path}: no editor scheme; using {'Darcula' if dark else 'Default'}")
    return vendored_schemes()["Darcula" if dark else "Default"], "base"


class Lookup:
    """Colour lookups in an effective theme, with IntelliJ's named colours, wildcards and os.* values."""

    def __init__(self, effective: Effective):
        self.effective = effective

    def named(self, value):
        seen = set()
        while isinstance(value, str) and value in self.effective.colors and value not in seen:
            seen.add(value)
            value = self.effective.colors[value]
        return value

    def colour(self, value):
        if isinstance(value, dict):
            value = value.get("os.default")
        value = self.named(value)
        return parse_hex(value) if isinstance(value, str) else None

    def key(self, name: str):
        """(colour, source) for an IntelliJ key: the theme first and its base last; at each level the exact key, then
        the longest matching '*.' wildcard. A value that is not a colour is skipped, as IntelliJ ignores it."""
        for source, ui in self.effective.levels:
            if name in ui:
                colour = self.colour(ui[name])
                if colour is not None:
                    return colour, source
            best = None
            for key in ui:
                if key.startswith("*.") and name.endswith(key[1:]) and (best is None or len(key) > len(best)):
                    best = key
            if best is not None:
                colour = self.colour(ui[best])
                if colour is not None:
                    return colour, source
        return None


class Scheme:
    """An IntelliJ editor scheme with its parent_scheme chain, child first."""

    def __init__(self, root: ElementTree.Element, warnings: list[str], label: str):
        self.chain = [root]
        while self.chain[-1].get("parent_scheme"):
            parent = self.chain[-1].get("parent_scheme")
            found = vendored_schemes().get(parent)
            if found is None:
                warnings.append(f"{label}: parent scheme \"{parent}\" is not one Jasper knows; using Default")
                found = vendored_schemes()["Default"]
            if any(found is seen for seen in self.chain) or len(self.chain) > 16:
                break
            self.chain.append(found)

    @staticmethod
    def value(text: str | None):
        if text is None or not re.fullmatch(r"[0-9A-Fa-f]{1,6}", text.strip()):
            return None
        rgb = int(text.strip(), 16)
        return (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255

    def color(self, name: str):
        for scheme in self.chain:
            for option in scheme.findall("./colors/option"):
                if option.get("name") == name:
                    colour = self.value(option.get("value"))
                    if colour is not None:
                        return colour
        return None

    def attribute(self, name: str, field_name: str, depth: int = 0):
        """A field (FOREGROUND, BACKGROUND, FONT_TYPE) of an attribute, following baseAttributes."""
        if depth > 8:
            return None
        for scheme in self.chain:
            for option in scheme.findall("./attributes/option"):
                if option.get("name") != name:
                    continue
                for item in option.findall("./value/option"):
                    if item.get("name") == field_name:
                        return item.get("value")
                if option.get("baseAttributes"):
                    return self.attribute(option.get("baseAttributes"), field_name, depth + 1)
                if option.find("./value") is not None:
                    return None
        return None

    def foreground(self, name: str):
        return self.value(self.attribute(name, "FOREGROUND"))

    def attribute_names(self) -> list[str]:
        names = []
        for scheme in self.chain:
            for option in scheme.findall("./attributes/option"):
                if option.get("name") not in names:
                    names.append(option.get("name"))
        return names


CONSOLE = ["CONSOLE_BLACK_OUTPUT", "CONSOLE_RED_OUTPUT", "CONSOLE_GREEN_OUTPUT", "CONSOLE_YELLOW_OUTPUT",
           "CONSOLE_BLUE_OUTPUT", "CONSOLE_MAGENTA_OUTPUT", "CONSOLE_CYAN_OUTPUT", "CONSOLE_GRAY_OUTPUT",
           "CONSOLE_DARKGRAY_OUTPUT", "CONSOLE_RED_BRIGHT_OUTPUT", "CONSOLE_GREEN_BRIGHT_OUTPUT",
           "CONSOLE_YELLOW_BRIGHT_OUTPUT", "CONSOLE_BLUE_BRIGHT_OUTPUT", "CONSOLE_MAGENTA_BRIGHT_OUTPUT",
           "CONSOLE_CYAN_BRIGHT_OUTPUT", "CONSOLE_WHITE_OUTPUT"]
SYNTAX_ATTRIBUTES = {
    "keyword": ["DEFAULT_KEYWORD"], "type": ["DEFAULT_CLASS_NAME"],
    "function": ["DEFAULT_FUNCTION_DECLARATION", "DEFAULT_FUNCTION_CALL"], "variable": ["DEFAULT_IDENTIFIER"],
    "constant": ["DEFAULT_CONSTANT"], "number": ["DEFAULT_NUMBER"], "string": ["DEFAULT_STRING"],
    "comment": ["DEFAULT_LINE_COMMENT", "DEFAULT_BLOCK_COMMENT"], "doc_comment": ["DEFAULT_DOC_COMMENT"],
    "annotation": ["DEFAULT_METADATA"], "operator": ["DEFAULT_OPERATION_SIGN"],
    "punctuation": ["DEFAULT_COMMA", "DEFAULT_SEMICOLON"], "tag": ["HTML_TAG", "XML_TAG"],
    "attribute": ["HTML_ATTRIBUTE_NAME", "XML_ATTRIBUTE_NAME"], "regex": ["REGEXP_CHAR_CLASS", "REGEXP_CHARACTER"],
    "error": ["ERRORS_ATTRIBUTES"],
}


def terminal_scheme(scheme: Scheme, name: str) -> dict:
    bg = scheme.color("CONSOLE_BACKGROUND_KEY") or scheme.value(scheme.attribute("TEXT", "BACKGROUND"))
    fg = scheme.foreground("CONSOLE_NORMAL_OUTPUT") or scheme.foreground("TEXT")
    if bg is None or fg is None:
        raise ConversionError(f"{name}: the editor scheme has no console or text colours")
    cursor = scheme.color("CARET_COLOR") or fg
    selection = scheme.color("SELECTION_BACKGROUND") or mix(bg, fg, 0.25)
    ansi = []
    for key in CONSOLE:
        colour = scheme.foreground(key)
        if colour is None:
            raise ConversionError(f"{name}: the editor scheme chain has no {key}")
        ansi.append(colour)
    result = {"name": name}
    for key, colour in zip(TERMINAL_KEYS, [bg, fg, fg, cursor, selection] + ansi):
        result[key] = hex_of(colour)
    return result


def editor_scheme(scheme: Scheme) -> tuple[dict, int]:
    text_bg = scheme.value(scheme.attribute("TEXT", "BACKGROUND"))
    surfaces = {
        "background": text_bg, "foreground": scheme.foreground("TEXT"), "caret": scheme.color("CARET_COLOR"),
        "selection": scheme.color("SELECTION_BACKGROUND"), "currentLine": scheme.color("CARET_ROW_COLOR"),
        "gutter": scheme.color("GUTTER_BACKGROUND") or text_bg, "lineNumbers": scheme.color("LINE_NUMBERS_COLOR"),
    }
    editor, found = {}, 0
    for entry in EDITOR_SURFACES:
        colour = surfaces[entry]
        editor[entry] = hex_of(colour) if colour is not None else EDITOR_REFERENCES[entry]
        found += colour is not None
    regex_extra = [name for name in scheme.attribute_names() if name.startswith("REGEXP_")]
    for role in SYNTAX_ROLES:
        candidates = SYNTAX_ATTRIBUTES[role] + (regex_extra if role == "regex" else [])
        value = None
        for attribute in candidates:
            colour = scheme.foreground(attribute)
            if colour is None and role == "error":
                colour = scheme.value(scheme.attribute(attribute, "EFFECT_COLOR"))
            if colour is not None:
                font = scheme.attribute(attribute, "FONT_TYPE") or "0"
                style = {"1": ", bold", "2": ", italic", "3": ", italic, bold"}.get(font.strip(), "")
                value = hex_of(colour) + style
                break
        editor[role] = value if value is not None else EDITOR_REFERENCES[role]
        found += value is not None
    return editor, found


# The mapping table (docs/superpowers/specs/2026-10-13-jasper-theme-import-design.md, section 5.4), in an order
# where every rule names only roles computed before it. Each entry: role, IntelliJ keys, rule(values, dark, ansi).
def _onaccent(v, dark, ansi):
    return WHITE if contrast(WHITE, v["accent"]) >= contrast(BLACK, v["accent"]) else BLACK


MAPPING = [
    ("text.primary", ["Label.foreground"], lambda v, dark, ansi: parse_hex("#BBBBBB" if dark else "#000000")),
    ("surface.panel", ["ToolWindow.background", "Panel.background"],
     lambda v, dark, ansi: parse_hex("#3C3F41" if dark else "#F2F2F2")),
    ("surface.app", ["StatusBar.background", "MainToolbar.background", "Panel.background"],
     lambda v, dark, ansi: v["surface.panel"]),
    ("hover.overlay", [], lambda v, dark, ansi: WHITE if dark else BLACK),
    ("surface.panel.raised", [], lambda v, dark, ansi: mix(v["surface.panel"], v["text.primary"], 0.04)),
    ("surface.dialog", ["Panel.background"], lambda v, dark, ansi: v["surface.panel"]),
    ("surface.card", ["Panel.background"], lambda v, dark, ansi: v["surface.panel"]),
    ("surface.sidebar", ["SidePanel.background", "ToolWindow.background"], lambda v, dark, ansi: v["surface.panel"]),
    ("surface.input", ["TextField.background", "TextArea.background"], lambda v, dark, ansi: v["surface.panel"]),
    ("surface.field.raised", ["ComboBox.nonEditableBackground", "ComboBox.background"],
     lambda v, dark, ansi: v["surface.input"]),
    ("surface.control", ["Button.startBackground", "Button.background"], lambda v, dark, ansi: v["surface.panel"]),
    ("surface.control.hover", ["ActionButton.hoverBackground"],
     lambda v, dark, ansi: mix(v["surface.control"], v["text.primary"], 0.08)),
    ("surface.control.selected", ["ActionButton.pressedBackground"],
     lambda v, dark, ansi: mix(v["surface.control"], v["text.primary"], 0.14)),
    ("surface.menu", ["PopupMenu.background", "Popup.background", "Menu.background"],
     lambda v, dark, ansi: v["surface.panel"]),
    ("surface.terminal", [], lambda v, dark, ansi: ansi["bg"]),
    ("surface.tabstrip", ["EditorTabs.background"], lambda v, dark, ansi: v["surface.app"]),
    ("surface.emptyTile", [], lambda v, dark, ansi: mix(v["surface.panel"], v["text.primary"], 0.08)),
    ("surface.scrim", [], lambda v, dark, ansi: with_alpha(BLACK, 0x8C) if dark else with_alpha(v["text.primary"], 0x40)),
    ("status.success", ["ProgressBar.passedColor"], lambda v, dark, ansi: ansi["green"]),
    ("status.warning", ["Component.warningFocusColor"], lambda v, dark, ansi: ansi["yellow"]),
    ("status.danger", ["ProgressBar.failedColor", "Component.errorFocusColor"], lambda v, dark, ansi: ansi["red"]),
    ("border.window", ["Borders.ContrastBorderColor", "Borders.color"],
     lambda v, dark, ansi: mix(v["surface.panel"], v["text.primary"], 0.15)),
    ("border.subtle", [], lambda v, dark, ansi: with_alpha(v["hover.overlay"], 0x0F)),
    ("border.input", ["TextField.borderColor", "Component.borderColor"],
     lambda v, dark, ansi: mix(v["surface.panel"], v["text.primary"], 0.25)),
    ("border.field.raised", ["ComboBox.borderColor", "Component.borderColor"],
     lambda v, dark, ansi: v["border.input"]),
    ("border.field.raised.hover", ["Component.hoverBorderColor"],
     lambda v, dark, ansi: mix(v["border.field.raised"], v["text.primary"], 0.15)),
    ("border.control", ["Button.startBorderColor", "Button.borderColor", "Component.borderColor"],
     lambda v, dark, ansi: v["border.input"]),
    ("border.menu", ["Popup.borderColor", "Borders.color"], lambda v, dark, ansi: v["border.control"]),
    ("border.error", ["Component.errorFocusColor"], lambda v, dark, ansi: v["status.danger"]),
    ("text.secondary", ["Label.infoForeground", "Component.infoForeground"],
     lambda v, dark, ansi: mix(v["text.primary"], v["surface.panel"], 0.35)),
    ("text.strong.secondary", [], lambda v, dark, ansi: mix(v["text.secondary"], v["text.primary"], 0.5)),
    ("text.tertiary", ["Label.disabledForeground"],
     lambda v, dark, ansi: mix(v["text.secondary"], v["surface.panel"], 0.3)),
    ("text.placeholder", ["TextField.placeholderForeground", "TextField.inactiveForeground"],
     lambda v, dark, ansi: mix(v["text.tertiary"], v["surface.input"], 0.25)),
    ("text.field.raised", ["ComboBox.foreground"],
     lambda v, dark, ansi: mix(v["text.primary"], v["text.secondary"], 0.3)),
    ("accent", ["Button.default.startBackground", "Button.default.background", "Component.focusColor"],
     lambda v, dark, ansi: parse_hex("#3574F0")),
    ("accent.hover", [], lambda v, dark, ansi: mix(v["accent"], v["text.primary"], 0.1)),
    ("text.onAccent", ["Button.default.foreground"], _onaccent),
    ("text.onAccent.secondary", [], lambda v, dark, ansi: with_alpha(v["text.onAccent"], 0xA6 if dark else 0xBF)),
    ("text.link", ["Link.activeForeground"], lambda v, dark, ansi: v["accent"]),
    ("text.link.hover", ["Link.hoverForeground"], lambda v, dark, ansi: mix(v["text.link"], v["text.primary"], 0.25)),
    ("text.error", ["Label.errorForeground"], lambda v, dark, ansi: v["status.danger"]),
    ("text.success", ["Label.successForeground"], lambda v, dark, ansi: v["status.success"]),
    ("selection.bg", ["List.selectionBackground", "Tree.selectionBackground", "Table.selectionBackground"],
     lambda v, dark, ansi: mix(v["accent"], v["surface.panel"], 0.5)),
    ("segment.track", [], lambda v, dark, ansi: v["surface.input"]),
    ("segment.selected.bg", ["SegmentedButton.selectedButtonColor"],
     lambda v, dark, ansi: v["border.control"] if dark else v["surface.control"]),
    ("toggle.track.off", ["ToggleButton.offBackground"], lambda v, dark, ansi: v["border.control"]),
    ("toggle.knob", ["ToggleButton.buttonColor"], lambda v, dark, ansi: WHITE),
    ("button.secondary.bg", ["Button.startBackground", "Button.background"], lambda v, dark, ansi: v["surface.control"]),
    ("button.secondary.bg.hover", [], lambda v, dark, ansi: v["surface.control.hover"]),
    ("button.secondary.border", ["Button.startBorderColor", "Button.borderColor"],
     lambda v, dark, ansi: v["border.control"]),
    ("status.idle", [], lambda v, dark, ansi: mix(v["text.tertiary"], v["surface.panel"], 0.4)),
    ("hue.blue", [], lambda v, dark, ansi: mix(ansi["blue"], v["text.primary"], 0.3)),
    ("hue.green", [], lambda v, dark, ansi: mix(ansi["green"], v["text.primary"], 0.3)),
    ("hue.amber", [], lambda v, dark, ansi: mix(ansi["yellow"], v["text.primary"], 0.3)),
    ("hue.purple", [], lambda v, dark, ansi: mix(ansi["magenta"], v["text.primary"], 0.3)),
    ("hue.neutral", [], lambda v, dark, ansi: v["text.strong.secondary"]),
]
OPACITY_ROLES = {"surface.scrim", "border.subtle", "text.onAccent.secondary"}


def ui_roles(lookup: Lookup, dark: bool, terminal: dict) -> tuple[dict, dict, list[str]]:
    """The UI roles in the format's order, each role's source ("theme", a parent's name, or "derived"), and one
    provenance line per role."""
    ansi = {name: parse_hex(terminal[name]) for name in ANSI}
    ansi["bg"] = parse_hex(terminal["bg"])
    values: dict = {}
    sources: dict[str, str] = {}
    provenance: list[str] = []
    for role, keys, rule in MAPPING:
        found = None
        for key in keys:
            found = lookup.key(key)
            if found is not None:
                colour, source = found
                found = (colour, f"{key} ({source})", source)
                break
        if found is None and rule is not None:
            found = (rule(values, dark, ansi), "derived", "derived")
        if found is None:
            raise ConversionError(f"internal: no value for {role}")
        colour, how, source = found
        if colour[3] != 255 and role not in OPACITY_ROLES:
            if role in ("surface.panel", "surface.app"):
                colour = over(colour, BLACK if dark else WHITE)
            else:
                colour = over(colour, values.get("surface.panel", BLACK if dark else WHITE))
        values[role] = colour
        sources[role] = source
        provenance.append(f"{role}: {hex_of(colour)} from {how}")
    return {role: hex_of(values[role]) for role in ROLES}, sources, provenance


def convert_intellij_theme(theme: ThemeFile, siblings: list[ThemeFile], name: str | None,
                           scheme_file: pathlib.Path | None = None, loose_dir: pathlib.Path | None = None) -> Result:
    warnings: list[str] = []
    effective = resolve(theme, siblings, warnings, scheme_file, loose_dir)
    display = name or str(theme.data.get("name") or theme.id)
    variant = "dark" if effective.dark else "light"
    scheme = Scheme(effective.scheme, warnings, theme.path)
    terminal = terminal_scheme(scheme, display)
    roles, sources, provenance = ui_roles(Lookup(effective), effective.dark, terminal)
    editor, from_scheme = editor_scheme(scheme)
    doc = {"jasper-theme": FORMAT, "name": display}
    if isinstance(theme.data.get("author"), str) and theme.data["author"]:
        doc["author"] = theme.data["author"]
    doc["ui"] = {variant: roles}
    doc["terminal"] = {variant: terminal}
    doc["editor"] = {variant: editor}
    validate(doc, theme.path)
    own = sum(1 for source in sources.values() if source == "theme")
    derived = sum(1 for source in sources.values() if source == "derived")
    inherited = len(ROLES) - own - derived
    parent = effective.chain[1] if len(effective.chain) > 1 else "its base"
    summary = (f"{display} ({variant}): {own} roles from the theme, {inherited} from {parent}, {derived} derived; "
               f"editor {from_scheme} of {len(EDITOR_SURFACES) + len(SYNTAX_ROLES)} from the scheme")
    return Result(doc, summary, warnings, provenance)


# ---------------------------------------------------------------------------------------------------------------
# The command line.

def themes_in(path: pathlib.Path) -> tuple[list[ThemeFile], pathlib.Path | None]:
    """The IntelliJ themes in an input, and the directory a loose .theme.json's scheme is looked up in."""
    if path.suffix.lower() in (".jar", ".zip"):
        return plugin_themes(Files.archive(path)), None
    loose = Files(path.name)
    loose.loose[path.name] = path.read_bytes()
    data = parse_json(loose.loose[path.name], path.name)
    return [ThemeFile(str(data.get("name", path.stem)), path.name, data, loose)], path.parent


def convert(path: pathlib.Path, args) -> list[Result]:
    suffix = path.suffix.lower()
    if suffix == ".toml":
        return [convert_alacritty(path, args.name)]
    if suffix not in (".jar", ".zip", ".json"):
        raise ConversionError(f"{path.name}: unknown input type; expected .toml, .theme.json, .jar or .zip")
    themes, loose_dir = themes_in(path)
    if args.theme:
        themes = [t for t in themes if t.id in args.theme or t.data.get("name") in args.theme]
        if not themes:
            raise ConversionError(f"{path.name}: no theme named {', '.join(args.theme)}")
    scheme = pathlib.Path(args.scheme) if args.scheme else None
    return [convert_intellij_theme(t, themes, args.name, scheme, loose_dir) for t in themes]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="jasper_theme.py", description="Convert Alacritty and IntelliJ themes into "
                                     "Jasper theme files, for Settings > Appearance > Import.")
    parser.add_argument("inputs", nargs="+", metavar="INPUT", help=".toml (Alacritty), .theme.json, .jar or .zip (IntelliJ)")
    parser.add_argument("-o", "--output", default=".", help="where to write (default: the current directory)")
    parser.add_argument("--force", action="store_true", help="overwrite existing files")
    parser.add_argument("--list", action="store_true", help="list the themes in each input and write nothing")
    parser.add_argument("--theme", action="append", metavar="NAME", help="convert only this plugin theme (repeatable)")
    parser.add_argument("--name", help="the display name (only when one theme is converted)")
    parser.add_argument("--scheme", metavar="FILE", help="the editor scheme for a loose .theme.json")
    parser.add_argument("--verbose", action="store_true", help="print where every colour came from")
    args = parser.parse_args(argv)
    paths = [pathlib.Path(name) for name in args.inputs]
    for path in paths:
        if not path.is_file():
            parser.error(f"{path}: no such file")
    if args.list:
        status = 0
        for path in paths:
            try:
                if path.suffix.lower() == ".toml":
                    print(f"{path.name}: {title_name(path.stem)} (Alacritty terminal scheme)")
                    continue
                for theme in themes_in(path)[0]:
                    variant = "Dark" if theme.data.get("dark") else "Light"
                    print(f"{path.name}: {theme.data.get('name', theme.id)} (id {theme.id}, {variant})")
            except ConversionError as failure:
                print(f"error: {failure}", file=sys.stderr)
                status = 1
        return status
    status, results = 0, []
    for path in paths:
        try:
            results.extend(convert(path, args))
        except (ConversionError, OSError) as failure:
            print(f"error: {failure}", file=sys.stderr)
            status = 1
    if args.name and len(results) > 1:
        parser.error("--name needs exactly one theme; this run converts " + str(len(results)))
    output = pathlib.Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    written: dict[str, str] = {}
    for result in results:
        file_name = slug(result.doc["name"])
        if not file_name:
            print(f"error: {result.doc['name']}: the name has no letters or digits for a file name; use --name",
                  file=sys.stderr)
            status = 1
            continue
        target = output / f"{file_name}.json"
        if file_name in written:
            print(f"error: {result.doc['name']} and {written[file_name]} would both be written to {target.name}",
                  file=sys.stderr)
            status = 1
            continue
        if target.exists() and not args.force:
            print(f"error: {target} exists; use --force to overwrite it", file=sys.stderr)
            status = 1
            continue
        target.write_text(dump(result.doc), encoding="utf-8", newline="\n")
        written[file_name] = result.doc["name"]
        for warning in result.warnings:
            print(f"warning: {warning}", file=sys.stderr)
        print(f"{target.name}: {result.summary}")
        if args.verbose:
            for line in result.sources:
                print(f"  {line}")
    return status


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 7: Generate the golden files and check them**

```bash
JASPER_THEME_UPDATE_GOLDEN=1 python3 -m unittest discover -s tools/themes -p 'test_*.py'
shasum -a 256 tools/themes/testdata/expected/*.json
```

Expected: `OK`, and exactly these hashes (the tool is deterministic; a different hash means the code or a fixture
differs from this plan, so stop and compare):

```
d45a78ed689d3e7170050a513690b62dc18a1cab7a67a04bd71ab4d81d2e511b  tools/themes/testdata/expected/gruvbox-dark.json
e840678f98727999e782d21313bf0bdb86724b93e25829b87954c2a44465abca  tools/themes/testdata/expected/ocean-dawn.json
18e880bed71597efb938c5dee1d1981f1f8cf23d7f60e020b3fba98c61670fdf  tools/themes/testdata/expected/ocean.json
c547aa14a75f19d37f99fab8d5ba3cb39652a4ca8054a7b35e1a3f24f492e6cc  tools/themes/testdata/expected/paper-light.json
f282568caab0a4112d5e744d0c18dd225a558e040075163c4b534e8600dc5bb7  tools/themes/testdata/expected/stray.json
```

Read `ocean.json` once: `"ui"`, `"terminal"` and `"editor"` each hold only `"dark"`; the terminal scheme is named
"Ocean"; `surface.terminal` equals the scheme's `bg`, `#0B141C`.

- [ ] **Step 8: Run the tests to see them pass**

Run: `python3 -m unittest discover -s tools/themes -p 'test_*.py'`
Expected: `Ran 32 tests` and `OK`, with no skip (Plan 1's `slate-steel.json` is present, so
`test_the_roles_are_the_bundled_themes` runs).

- [ ] **Step 9: Try the command line by hand**

```bash
python3 tools/themes/jasper_theme.py --list tools/themes/testdata/intellij/loose/Ocean.theme.json
python3 tools/themes/jasper_theme.py tools/themes/testdata/alacritty/gruvbox_dark.toml -o build/theme-tool-try --verbose
```

Expected: `Ocean.theme.json: Ocean (id Ocean, Dark)`, then `gruvbox-dark.json: Gruvbox Dark (terminal scheme, dark)`.
Delete `build/theme-tool-try` afterwards.

- [ ] **Step 10: Commit**

```bash
git add tools/themes
git commit -m "Convert Alacritty and IntelliJ themes into Jasper themes

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: The tool in `check`, CI and the contract with the Java loader

**Files:**
- Create: `gradle/theme-tool.gradle.kts`
- Modify: `build.gradle.kts` (apply it)
- Modify: `.github/workflows/ci.yml` (install Python)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/theme/ConvertedThemesTest.java`

**Interfaces:**
- Consumes: Task 1's golden files; Plan 1's `JasperTheme.parse(String json, String id)`, `has(boolean)`, `schemes()`,
  `generated(boolean)`.
- Produces: root task `checkThemeTool` (a dependency of `:jasper-ui:check`).

- [ ] **Step 1: Write the failing contract test**

`jasper-ui/src/test/java/dev/jasper/ui/theme/ConvertedThemesTest.java`:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 6.2: every file the converter's tests expect it to write is a theme the Java loader reads as intended. */
class ConvertedThemesTest {
    static final Path EXPECTED = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize()
        .resolve("tools/themes/testdata/expected");

    /** A golden file's UI variants and its schemes' keys. */
    record Shape(boolean dark, boolean light, List<String> schemes) { }

    static final Map<String, Shape> SHAPES = Map.of(
        "gruvbox-dark", new Shape(false, false, List.of("dark")),
        "paper-light", new Shape(false, false, List.of("light")),
        "ocean", new Shape(true, false, List.of("dark")),
        "ocean-dawn", new Shape(false, true, List.of("light")),
        "stray", new Shape(true, false, List.of("dark")));

    @Test void everyConvertedThemeLoadsWithItsVariantsAndSchemes() throws Exception {
        List<Path> files;
        try (var listing = Files.list(EXPECTED)) { files = listing.filter(path -> path.toString().endsWith(".json")).sorted().toList(); }
        assertThat(files).extracting(path -> path.getFileName().toString().replace(".json", ""))
            .containsExactlyInAnyOrderElementsOf(SHAPES.keySet());
        for (Path file : files) {
            String id = file.getFileName().toString().replace(".json", "");
            var theme = JasperTheme.parse(Files.readString(file), id);
            var shape = SHAPES.get(id);
            assertThat(theme.has(true)).as(id).isEqualTo(shape.dark());
            assertThat(theme.has(false)).as(id).isEqualTo(shape.light());
            assertThat(theme.schemes()).as(id).extracting(scheme -> scheme.id().key()).isEqualTo(shape.schemes());
            for (boolean dark : new boolean[] {true, false})
                if (theme.has(dark)) assertThat(theme.generated(dark).semantic().color(SemanticColor.SURFACE_TERMINAL))
                    .as("%s: the terminal surface meets the text area", id).isEqualTo(theme.matching(dark).colors().background());
        }
    }

    @Test void oceanKeepsItsOwnColoursAndItsEditorScheme() throws Exception {
        var ocean = JasperTheme.parse(Files.readString(EXPECTED.resolve("ocean.json")), "ocean");
        assertThat(ocean.name()).isEqualTo("Ocean");
        assertThat(ocean.author()).isEqualTo("Jasper fixtures");
        assertThat(ocean.palette(true).color(SemanticColor.SURFACE_PANEL)).isEqualTo(new Color(0x16242F));
        assertThat(ocean.syntax(true).get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xC792EA), true, false));
        assertThat(ocean.matching(true).name()).isEqualTo("Ocean");
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew :jasper-ui:test --tests '*ConvertedThemesTest'`
Expected: PASS: the converter and the loader already agree. (It fails only if Task 1's golden files are missing or
Plan 1's loader differs from the format; then stop and compare against the spec's section 2.)

- [ ] **Step 3: Write the Gradle task**

`gradle/theme-tool.gradle.kts`:

```kotlin
// The theme converter's tests (tools/themes: Python 3.11+, standard library only), run by :jasper-ui:check.
// -Pjasper.python=<interpreter> chooses the interpreter; otherwise python3, then python, from the PATH.
val themeToolUi = project(":jasper-ui")
val themeToolPassed = layout.buildDirectory.file("theme-tool/passed")
val checkThemeTool = tasks.register("checkThemeTool") {
    group = "verification"
    description = "Runs the theme converter's tests (tools/themes) with Python 3.11 or later."
    inputs.files(fileTree("tools/themes") { exclude("**/__pycache__/**") }).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json").withPathSensitivity(PathSensitivity.RELATIVE)
    val chosen = providers.gradleProperty("jasper.python")
    inputs.property("python", chosen.orElse(""))
    outputs.file(themeToolPassed)
    val root = layout.projectDirectory.asFile
    doLast {
        fun usable(python: String) = runCatching {
            val probe = ProcessBuilder(python, "-c", "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)")
                .redirectErrorStream(true).start()
            probe.inputStream.readAllBytes()
            probe.waitFor() == 0
        }.getOrDefault(false)
        val python = (chosen.orNull?.let { listOf(it) } ?: listOf("python3", "python")).firstOrNull { usable(it) }
            ?: throw GradleException("The theme tool's tests need Python 3.11 or later on the PATH")
        val run = ProcessBuilder(python, "-B", "-m", "unittest", "discover", "-s", "tools/themes", "-p", "test_*.py")
            .directory(root).redirectErrorStream(true).start()
        val output = run.inputStream.bufferedReader().readText()
        if (run.waitFor() != 0) throw GradleException("The theme tool's tests failed:\n$output")
        logger.lifecycle(output.trim().lines().takeLast(3).joinToString("\n"))
        themeToolPassed.get().asFile.apply { parentFile.mkdirs(); writeText("ok\n") }
    }
}
themeToolUi.tasks.named("check") { dependsOn(checkThemeTool) }
```

In `build.gradle.kts`, after `apply(from = "gradle/ui-architecture.gradle.kts")`, add:

```kotlin
apply(from = "gradle/theme-tool.gradle.kts")
```

- [ ] **Step 4: Run it, and its failure message**

```bash
./gradlew checkThemeTool
./gradlew checkThemeTool
./gradlew checkThemeTool -Pjasper.python=/nonexistent/python3
```

Expected: the first prints the unittest summary (`Ran 32 tests`, `OK`) and `BUILD SUCCESSFUL`; the second is
`UP-TO-DATE`; the third fails with "The theme tool's tests need Python 3.11 or later on the PATH".

- [ ] **Step 5: Install Python in CI**

In `.github/workflows/ci.yml`, after the `actions/setup-java@v6` step, add:

```yaml
      - uses: actions/setup-python@v6
        with:
          python-version: '3.12'
```

- [ ] **Step 6: Run the module check**

Run: `./gradlew :jasper-ui:check`
Expected: `BUILD SUCCESSFUL`, with `checkThemeTool` among the executed tasks.

- [ ] **Step 7: Commit**

```bash
git add gradle/theme-tool.gradle.kts build.gradle.kts .github/workflows/ci.yml jasper-ui/src/test/java/dev/jasper/ui/theme/ConvertedThemesTest.java
git commit -m "Run the theme converter's tests in check and pin them to the loader

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: Documentation and verification

**Files:**
- Modify: `docs/themes.md` (a "Converting Alacritty and IntelliJ themes" section)
- Modify: `docs/STATUS.md` (an entry at the top)

**Interfaces:**
- Consumes: Tasks 1–2; Plan 1's `docs/themes.md` (which has a "## Installed themes" section) and, when present,
  Plan 2's import section.
- Produces: nothing code depends on.

- [ ] **Step 1: Document the converter**

In `docs/themes.md`, insert this section directly before `## The Jasper theme format`:

```markdown
## Converting Alacritty and IntelliJ themes

`tools/themes/jasper_theme.py` (in a checkout of Jasper; Python 3.11 or later and nothing else) turns themes made for
other programs into Jasper theme files:

```bash
python3 tools/themes/jasper_theme.py Dracula.jar gruvbox_dark.toml -o ~/Desktop/themes
```

- An **Alacritty** theme (`.toml`) becomes a terminal-only theme: one scheme, named after the file
  (`gruvbox_dark.toml` is "Gruvbox Dark") and keyed `dark` or `light` by its background.
- An **IntelliJ** theme (a `.theme.json`, a plugin `.jar`, or a `.zip` downloaded from the JetBrains Marketplace)
  becomes a UI theme with its one variant, Dark or Light: its colours resolved through its parent themes (IntelliJ's
  own, in `tools/themes/intellij/`), its console colours as its terminal scheme and its editor scheme's colours for the
  Editor. Every theme in a plugin is written; `--list` shows them and `--theme NAME` converts one.
- `--name` sets the display name, `--scheme FILE` gives a loose `.theme.json` its editor scheme, `--force` overwrites
  existing files and `--verbose` prints where every colour came from.

Each theme is written as `<id>.json`, its name lower-cased with dashes, so the file can go straight into the themes
folder. A conversion prints a summary such as `Dracula (dark): 41 roles from the theme, 12 from Darcula, 4 derived;
editor 22 of 23 from the scheme`; "derived" roles had no IntelliJ key and follow a rule instead.
```

- [ ] **Step 2: Record the work in `docs/STATUS.md`**

Add at the top of `docs/STATUS.md`, below the `# Jasper — Status and Handoff` heading (fill in the test counts from
Step 3):

```markdown
**Theme import Plan 3: the theme converter — 2026-10-13** (`claude/theme-import`;
[spec](superpowers/specs/2026-10-13-jasper-theme-import-design.md),
[plan](superpowers/plans/2026-10-13-theme-import-3-converter.md)). `tools/themes/jasper_theme.py` (Python 3.11+,
standard library) converts Alacritty `.toml` themes into terminal-only Jasper themes and IntelliJ themes (`.theme.json`,
plugin `.jar`, Marketplace `.zip`) into UI themes with their console colours as the terminal scheme and their editor
scheme's syntax colours, resolving parents against IntelliJ's own themes vendored in `tools/themes/intellij/` (restored
from `c1e968ee^`). `checkThemeTool` runs its tests under `:jasper-ui:check` and needs Python 3.11+ on the PATH (CI
installs it); `ConvertedThemesTest` loads its golden files with the Java loader. The mapping departs from the spec's
section 5.4 starting table in four recorded ways (the plan's header). `./gradlew check --continue`:
**N tests, 0 failures**. No GUI launched. For the user: convert a favourite theme and import it (Plan 2) or copy it into
the themes folder. Not merged or pushed.
```

- [ ] **Step 3: Run the full check**

Run: `./gradlew check --continue`
Expected: `BUILD SUCCESSFUL`. Count the tests from the XML (AGENTS.md):

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as E
t = f = e = s = 0
for x in glob.glob("*/build/test-results/test/*.xml") + glob.glob("plugins/*/build/test-results/test/*.xml"):
    r = E.parse(x).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(t, "tests,", f, "failures,", e, "errors,", s, "skipped")
PY
```

Put the numbers into the STATUS entry.

- [ ] **Step 4: Commit**

```bash
git add docs/themes.md docs/STATUS.md
git commit -m "Document the theme converter

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
