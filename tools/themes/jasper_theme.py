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
import os
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
    "text.onAccent", "text.onAccent.secondary", "text.onSelection", "text.onSelection.secondary",
    "text.link", "text.link.hover", "text.error", "text.success",
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
# Import refuses a theme file with a built-in theme's id
BUILT_IN = ("slate-steel", "catppuccin-latte", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha")
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


def toml_table(parent: dict, key: str, label: str, name: str) -> dict:
    """``parent[key]`` as a table (an absent one is empty); ``name`` is its dotted name in the message."""
    value = parent.get(key, {})
    if not isinstance(value, dict):
        raise ConversionError(f"{label}: {name} must be a table")
    return value


def convert_alacritty(path: pathlib.Path, name: str | None = None) -> Result:
    label = path.name
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except RecursionError:
        raise ConversionError(f"{label}: nested too deeply") from None
    except ValueError as failure:  # a TOMLDecodeError, a UnicodeDecodeError or too many digits in a number
        raise ConversionError(f"{label}: not a TOML file: {failure}") from None
    warnings = []
    if "import" in data or "import" in toml_table(data, "general", label, "general"):
        warnings.append(f"{label}: ignoring import; colours from imported files are missing")
    colors = data.get("colors")
    if not isinstance(colors, dict):
        raise ConversionError(f"{label}: no [colors] table")
    primary = toml_table(colors, "primary", label, "colors.primary")
    for required in ("background", "foreground"):
        if required not in primary:
            raise ConversionError(f"{label}: colors.primary.{required} is missing")
    bg = alacritty_colour(primary["background"], f"{label}: colors.primary.background")
    fg = alacritty_colour(primary["foreground"], f"{label}: colors.primary.foreground")
    bold = alacritty_colour(primary["bright_foreground"], f"{label}: colors.primary.bright_foreground") \
        if "bright_foreground" in primary else fg
    cursor_table = toml_table(colors, "cursor", label, "colors.cursor")
    selection_table = toml_table(colors, "selection", label, "colors.selection")
    cursor = alacritty_colour(cursor_table["cursor"], f"{label}: colors.cursor.cursor", fg, bg) \
        if "cursor" in cursor_table else fg
    selection = alacritty_colour(selection_table["background"], f"{label}: colors.selection.background", fg, bg) \
        if "background" in selection_table else mix(bg, fg, 0.25)
    normal = toml_table(colors, "normal", label, "colors.normal")
    bright = toml_table(colors, "bright", label, "colors.bright")
    ansi = []
    for colour in ANSI:
        if colour not in normal:
            raise ConversionError(f"{label}: colors.normal.{colour} is missing")
        ansi.append(alacritty_colour(normal[colour], f"{label}: colors.normal.{colour}"))
    for index, colour in enumerate(ANSI):
        ansi.append(alacritty_colour(bright[colour], f"{label}: colors.bright.{colour}") if colour in bright
                    else ansi[index])
    display = name or title_name(path.stem)
    if not display:
        raise ConversionError(f"{label}: no theme name can be made from the file name; use --name")
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


def read_file(path: pathlib.Path, label: str) -> bytes:
    """A local file's bytes, refused when larger than an archive member may be."""
    with path.open("rb") as stream:
        data = stream.read(MAX_FILE + 1)
    if len(data) > MAX_FILE:
        raise ConversionError(f"{label}: larger than {MAX_FILE >> 20} MiB")
    return data


class Files:
    """A plugin's files, read in memory within the limits; paths are archive paths without a leading '/'."""

    def __init__(self, label: str):
        self.label = label
        self.entries: dict[str, tuple[zipfile.ZipFile, zipfile.ZipInfo]] = {}
        self.loose: dict[str, bytes] = {}
        self.inflated = 0
        self.counted = 0  # entries in the archive and its nested jars together
        self._schemes: dict[str, ElementTree.Element] | None = None

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
        self.counted += len(infos)
        if self.counted > MAX_ENTRIES:
            raise ConversionError(f"{label}: more than 10,000 entries in all")
        for info in infos:
            if not info.is_dir():
                self.entries.setdefault(info.filename.lstrip("/"), (archive, info))
        return archive

    def read_entry(self, archive, info, limit: int, label: str) -> bytes:
        if info.file_size > limit:
            raise ConversionError(f"{label}: larger than {limit >> 20} MiB")
        try:
            with archive.open(info) as stream:
                data = stream.read(limit + 1)
        except Exception as failure:  # the decompressors raise many kinds of error for a damaged member
            raise ConversionError(f"{label}: cannot be read: {failure}") from None
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

    def schemes(self) -> dict[str, ElementTree.Element]:
        """The editor schemes in the plugin by name (the first of a name wins), each file parsed once; an .xml or
        .icls that does not parse or is not a scheme is skipped."""
        if self._schemes is None:
            found: dict[str, ElementTree.Element] = {}
            for name in self.names():
                if not name.endswith((".xml", ".icls")) or name.endswith("plugin.xml"):
                    continue
                data = self.read(name)
                try:
                    root = parse_xml(data, f"{self.label}!{name}")
                except ConversionError:
                    continue
                if root.tag == "scheme":
                    found.setdefault(root.get("name"), root)
            self._schemes = found
        return self._schemes


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
    except RecursionError:
        raise ConversionError(f"{label}: nested too deeply") from None
    except ValueError as failure:  # a JSONDecodeError, a UnicodeDecodeError or too many digits in a number
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

    @property
    def where(self) -> str:
        """The file as a message names it: ``plugin.jar!themes/A.theme.json``, or a loose file's own name."""
        if self.files is None or self.path == self.files.label:
            return self.path
        return f"{self.files.label}!{self.path}"


# The keys of a theme the converter reads, with the type each must have.
THEME_KEYS = {"name": (str, "a string"), "dark": (bool, "true or false"), "parentTheme": (str, "a string"),
              "editorScheme": (str, "a string"), "colors": (dict, "an object"), "ui": (dict, "an object")}


def check_types(theme: ThemeFile) -> None:
    """Raises ConversionError for a key of the wrong type; a null counts as an absent key."""
    for key, (kind, expected) in THEME_KEYS.items():
        if key not in theme.data:
            continue
        if theme.data[key] is None:
            del theme.data[key]
        elif not isinstance(theme.data[key], kind):
            raise ConversionError(f'{theme.where}: "{key}" must be {expected}')


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


def theme_chain(theme: ThemeFile, siblings: list[ThemeFile], warnings: list[str]) -> list[ThemeFile]:
    """The theme, then its parents (a sibling in the plugin, else one of IntelliJ's own), then its base."""
    chain: list[ThemeFile] = []
    current = theme
    while current is not None:
        if any(current is seen for seen in chain) or len(chain) > 16:
            raise ConversionError(f"{theme.path}: parentTheme loops")
        chain.append(current)
        if current.files is not None:
            check_types(current)
        parent = current.data.get("parentTheme")
        if not parent:
            if current.files is not None:
                dark = bool(current.data.get("dark", False))
                chain.append(vendored_theme("Darcula" if dark else "IntelliJ"))
            break
        sibling = next((t for t in siblings if t is not current and parent in (t.id, t.data.get("name"))), None)
        found = sibling or vendored_theme(parent)
        if found is None:
            dark = is_dark(chain)
            warnings.append(f"{theme.path}: parent theme \"{parent}\" is not one Jasper knows; "
                            f"using {'Darcula' if dark else 'IntelliJ'}")
            found = vendored_theme("Darcula" if dark else "IntelliJ")
        current = found
    return chain


def is_dark(chain: list[ThemeFile]) -> bool:
    """Whether the theme is dark: its own ``dark`` key, else the nearest parent's."""
    return next((bool(t.data["dark"]) for t in chain if "dark" in t.data), False)


def resolve(theme: ThemeFile, siblings: list[ThemeFile], warnings: list[str], scheme_file: pathlib.Path | None,
            loose_dir: pathlib.Path | None) -> Effective:
    chain = theme_chain(theme, siblings, warnings)
    dark = is_dark(chain)
    colors: dict[str, str] = {}
    for item in reversed(chain):
        for key, value in item.data.get("colors", {}).items():
            if isinstance(value, str):
                colors[key] = value
    levels = []
    for item in chain:
        flat: dict = {}
        try:
            flatten("", item.data.get("ui", {}), flat)
        except RecursionError:
            raise ConversionError(f'{item.where}: "ui" is nested too deeply') from None
        levels.append(("theme" if item is theme else str(item.data.get("name", item.id)), flat))
    scheme, scheme_from = find_scheme(chain, theme, warnings, scheme_file, loose_dir, dark)
    return Effective(dark, colors, levels, scheme, scheme_from, [str(t.data.get("name", t.id)) for t in chain])


def find_scheme(chain, theme, warnings, scheme_file, loose_dir, dark):
    if scheme_file is not None:
        return parse_xml(read_file(scheme_file, scheme_file.name), scheme_file.name), "theme"
    for item in chain:
        reference = item.data.get("editorScheme")
        if not reference:
            continue
        source = "theme" if item is theme else str(item.data.get("name", item.id))
        if item.files is not None and ("/" in reference or reference.endswith((".xml", ".icls"))):
            data = item.files.read(reference)
            if data is None and loose_dir is not None:
                candidate = loose_dir / pathlib.PurePosixPath(reference).name
                data = read_file(candidate, candidate.name) if candidate.is_file() else None
            if data is not None:
                return parse_xml(data, f"{item.files.label}!{reference}"), source
            warnings.append(f"{theme.path}: editor scheme {reference} is not in the plugin; "
                            f"using {'Darcula' if dark else 'Default'}")
            break
        if item.files is not None and reference in item.files.schemes():
            return item.files.schemes()[reference], source
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


def _onselection(v, dark, ansi):
    """The accent's text or the primary text, whichever reads better on the selection."""
    return max((v["text.onAccent"], v["text.primary"]), key=lambda color: contrast(color, v["selection.bg"]))


MAPPING = [
    ("surface.panel", ["ToolWindow.background", "Panel.background"],
     lambda v, dark, ansi: parse_hex("#3C3F41" if dark else "#F2F2F2")),
    ("text.primary", ["Label.foreground"], lambda v, dark, ansi: parse_hex("#BBBBBB" if dark else "#000000")),
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
    ("text.onSelection", ["List.selectionForeground", "Tree.selectionForeground", "Table.selectionForeground"], _onselection),
    ("text.onSelection.secondary", [], lambda v, dark, ansi: with_alpha(v["text.onSelection"], 0xA6 if dark else 0xBF)),
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
OPACITY_ROLES = {"surface.scrim", "border.subtle", "text.onAccent.secondary", "text.onSelection.secondary"}


def ui_roles(lookup: Lookup, dark: bool, terminal: dict, given: dict | None = None) -> tuple[dict, dict, list[str]]:
    """The UI roles in the format's order, each role's source ("theme", a parent's name, or "derived"), and one
    provenance line per role; ``given`` maps a role to the (colour, provenance, source) it takes instead."""
    ansi = {name: parse_hex(terminal[name]) for name in ANSI}
    ansi["bg"] = parse_hex(terminal["bg"])
    values: dict = {}
    sources: dict[str, str] = {}
    provenance: list[str] = []
    for role, keys, rule in MAPPING:
        found = (given or {}).get(role)
        if found is None:
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


def is_islands(effective: Effective) -> bool:
    """Whether a theme is drawn for IntelliJ's Islands UI, which marks it with "Islands": 1 (inherited from a parent)."""
    return any(ui.get("Islands") == 1 for _, ui in effective.levels)


def islands_variant(theme: ThemeFile, siblings: list[ThemeFile], dark: bool, loose_dir: pathlib.Path | None):
    """The plugin's Islands variant of a classic theme, (name, effective theme): an Islands theme of the same variant
    whose name without the word "Islands" is the theme's; None when there is none."""
    def plain(item: ThemeFile) -> str:
        return " ".join(word for word in str(item.data.get("name", item.id)).split() if word.casefold() != "islands").casefold()
    for sibling in siblings:
        if sibling is theme or plain(sibling) != plain(theme) or not isinstance(sibling.data.get("name"), str):
            continue
        try:
            effective = resolve(sibling, siblings, [], None, loose_dir)
        except ConversionError:
            continue  # its own conversion reports it
        if is_islands(effective) and effective.dark == dark:
            return sibling.data["name"], effective
    return None


# The roles a classic theme takes from its plugin's Islands variant: the chrome Jasper's tool-window islands stand on.
ISLANDS_CHROME = ["surface.app"]


def convert_intellij_theme(theme: ThemeFile, siblings: list[ThemeFile], name: str | None,
                           scheme_file: pathlib.Path | None = None, loose_dir: pathlib.Path | None = None,
                           roles: dict[str, str] | None = None) -> Result:
    """One IntelliJ theme as a Jasper theme. ``roles`` maps a role to the colour it takes instead of its IntelliJ key:
    ``#RRGGBB`` or a colour of the theme's own palette (hand-picked values, as the built-in Catppuccin themes have)."""
    warnings: list[str] = []
    effective = resolve(theme, siblings, warnings, scheme_file, loose_dir)
    display = name or str(theme.data.get("name") or theme.id)
    if not display:
        raise ConversionError(f"{theme.where}: the theme has no name; use --name")
    variant = "dark" if effective.dark else "light"
    scheme = Scheme(effective.scheme, warnings, theme.path)
    terminal = terminal_scheme(scheme, display)
    islands = None if is_islands(effective) else islands_variant(theme, siblings, effective.dark, loose_dir)
    given = {}
    if islands is not None:
        island_name, island = islands[0], Lookup(islands[1])
        for role in ISLANDS_CHROME:
            for key in next(keys for mapped, keys, _ in MAPPING if mapped == role):
                found = island.key(key)
                if found is not None:
                    colour, source = found
                    given[role] = (colour, f"{key} ({island_name if source == 'theme' else source})", source)
                    break
    for role, value in (roles or {}).items():
        if role not in ROLES:
            raise ConversionError(f'{theme.where}: unknown role "{role}"')
        colour = Lookup(effective).colour(value)
        if colour is None:
            raise ConversionError(f'{theme.where}: {role}: "{value}" is neither a colour nor one of the theme\'s colours')
        given[role] = (colour, f"{value} (chosen)", "theme")
    roles, sources, provenance = ui_roles(Lookup(effective), effective.dark, terminal, given)
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
               f"editor {from_scheme} of {len(EDITOR_SURFACES) + len(SYNTAX_ROLES)} from the scheme"
               + (f"; chrome from {islands[0]}" if islands is not None else ""))
    return Result(doc, summary, warnings, provenance)


# ---------------------------------------------------------------------------------------------------------------
# The command line.

def themes_in(path: pathlib.Path) -> tuple[list[ThemeFile], pathlib.Path | None]:
    """The IntelliJ themes in an input, and the directory a loose .theme.json's scheme is looked up in."""
    if path.suffix.lower() in (".jar", ".zip"):
        themes = plugin_themes(Files.archive(path))
        if not themes:
            raise ConversionError(f"{path.name}: no themes found")
        return themes, None
    loose = Files(path.name)
    loose.loose[path.name] = read_file(path, path.name)
    data = parse_json(loose.loose[path.name], path.name)
    return [ThemeFile(str(data.get("name", path.stem)), path.name, data, loose)], path.parent


def convert(path: pathlib.Path, args, problems: list[str]) -> list[Result]:
    """The input's themes; a theme of a plugin that cannot be converted is added to ``problems`` and the others are kept."""
    suffix = path.suffix.lower()
    if suffix == ".toml":
        return [convert_alacritty(path, args.name)]
    if suffix not in (".jar", ".zip", ".json"):
        raise ConversionError(f"{path.name}: unknown input type; expected .toml, .theme.json, .jar or .zip")
    themes, loose_dir = themes_in(path)
    selected = themes  # parents are resolved among all the plugin's themes, whichever are converted
    if args.theme:
        selected = [t for t in themes if t.id in args.theme or t.data.get("name") in args.theme]
        if not selected:
            raise ConversionError(f"{path.name}: no theme named {', '.join(args.theme)}")
    scheme = pathlib.Path(args.scheme) if args.scheme else None
    results = []
    for theme in selected:
        try:
            results.append(convert_intellij_theme(theme, themes, args.name, scheme, loose_dir))
        except Exception as failure:  # one theme's failure does not stop the plugin's others
            problems.append(describe(failure, path, theme))
    return results


def reason(failure: OSError) -> str:
    return failure.strerror or str(failure)


def describe(failure: Exception, path: pathlib.Path, theme: ThemeFile | None = None) -> str:
    """The text after "error: " for an input (or one theme of it) that failed; a ConversionError already names its file."""
    where = theme.where if theme else path.name
    if isinstance(failure, ConversionError):
        return str(failure)
    if isinstance(failure, OSError):
        return f"{failure.filename or where}: {reason(failure)}"
    return f"{where}: internal error: {type(failure).__name__}: {failure}"


def write_theme(target: pathlib.Path, text: str) -> None:
    """Writes ``target`` through a temporary file beside it, so a failure leaves any existing file as it was."""
    data = text.encode("utf-8")  # before anything is opened: a lone surrogate fails here
    temporary = target.with_name(f".{target.name}.{os.getpid()}.tmp")
    stream = open(temporary, "xb")  # a failure here has created nothing to remove
    try:
        with stream:
            stream.write(data)
        os.replace(temporary, target)
    except BaseException:
        try:
            temporary.unlink()
        except OSError:
            pass
        raise


def write_results(results: list[tuple[str, Result]], args) -> int:
    """Writes each converted theme (input name, result) to the output folder; 1 when any could not be written."""
    output = pathlib.Path(args.output)
    try:
        output.mkdir(parents=True, exist_ok=True)
    except OSError as failure:
        print(f"error: {output}: " + ("exists and is not a directory" if isinstance(failure, FileExistsError)
                                      else reason(failure)), file=sys.stderr)
        return 1
    status = 0
    written: dict[str, str] = {}
    for input_name, result in results:
        name = result.doc["name"]
        file_name = slug(name)
        if not file_name:
            print(f"error: {name}: the name has no letters or digits for a file name; use --name", file=sys.stderr)
            status = 1
            continue
        if file_name in BUILT_IN:
            print(f"error: {input_name}: \"{file_name}\" is a built-in Jasper theme; choose another name with --name",
                  file=sys.stderr)
            status = 1
            continue
        target = output / f"{file_name}.json"
        if file_name in written:
            print(f"error: {name} and {written[file_name]} would both be written to {target.name}", file=sys.stderr)
            status = 1
            continue
        try:
            if target.exists() and not args.force:
                print(f"error: {target} exists; use --force to overwrite it", file=sys.stderr)
                status = 1
                continue
            write_theme(target, dump(result.doc))
        except UnicodeEncodeError:
            print(f"error: {input_name}: {name!r} cannot be written: it has a lone surrogate, which is not valid Unicode",
                  file=sys.stderr)
            status = 1
            continue
        except OSError as failure:
            print(f"error: {target}: {reason(failure)}", file=sys.stderr)
            status = 1
            continue
        written[file_name] = name
        for warning in result.warnings:
            print(f"warning: {warning}", file=sys.stderr)
        print(f"{target.name}: {result.summary}")
        if args.verbose:
            for line in result.sources:
                print(f"  {line}")
    return status


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
    parser.add_argument("--verbose", action="store_true", help="print where each UI role came from (IntelliJ themes)")
    args = parser.parse_args(argv)
    for stream in (sys.stdout, sys.stderr):  # a console that cannot encode a name prints it escaped, not as a traceback
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(errors="backslashreplace")
    paths = [pathlib.Path(name) for name in args.inputs]
    for path in paths:
        if not path.is_file():
            parser.error(f"{path}: no such file")
    status = 0
    if args.list:
        for path in paths:
            try:
                if path.suffix.lower() == ".toml":
                    print(f"{path.name}: {title_name(path.stem)} (Alacritty terminal scheme)")
                    continue
                themes = themes_in(path)[0]
                for theme in themes:
                    variant = "Dark" if is_dark(theme_chain(theme, themes, [])) else "Light"
                    print(f"{path.name}: {theme.data.get('name', theme.id)} (id {theme.id}, {variant})")
            except Exception as failure:  # one input's failure does not stop the others
                print(f"error: {describe(failure, path)}", file=sys.stderr)
                status = 1
        return status
    results: list[tuple[str, Result]] = []
    for path in paths:
        problems: list[str] = []
        try:
            results.extend((path.name, result) for result in convert(path, args, problems))
        except Exception as failure:  # one input's failure does not stop the others
            problems.append(describe(failure, path))
        for problem in problems:
            print(f"error: {problem}", file=sys.stderr)
            status = 1
    if args.name and len(results) > 1:
        parser.error("--name needs exactly one theme; this run converts " + str(len(results)))
    return max(status, write_results(results, args))


if __name__ == "__main__":
    sys.exit(main())
