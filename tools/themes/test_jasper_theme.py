"""Tests for jasper_theme.py. Run from the repository root:

    python3 -m unittest discover -s tools/themes -p 'test_*.py'

Set JASPER_THEME_UPDATE_GOLDEN=1 to rewrite testdata/expected/, the built-in Catppuccin themes and the Enterprise theme
from the current output (then review the diff).
"""

import contextlib
import io
import json
import os
import pathlib
import struct
import tempfile
import unittest
import unittest.mock
import zipfile

import jasper_theme as tool

DATA = pathlib.Path(__file__).resolve().parent / "testdata"
EXPECTED = DATA / "expected"
UPDATE = os.environ.get("JASPER_THEME_UPDATE_GOLDEN") == "1"
CATPPUCCIN = pathlib.Path(__file__).resolve().parent / "catppuccin"
BUILT_IN_THEMES = pathlib.Path(__file__).resolve().parents[2] / "jasper-ui/src/main/resources/dev/jasper/ui/theme"


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


def theme_jar(target: pathlib.Path, themes: dict, extra: dict | None = None) -> pathlib.Path:
    """A plugin jar whose plugin.xml declares each theme of {id: theme dict}; extra maps more paths to their bytes."""
    providers = "".join(f'<themeProvider id="{theme_id}" path="/themes/{theme_id}.theme.json"/>' for theme_id in themes)
    with zipfile.ZipFile(target, "w") as archive:
        archive.writestr("META-INF/plugin.xml", f"<idea-plugin><extensions>{providers}</extensions></idea-plugin>")
        for theme_id, theme in themes.items():
            archive.writestr(f"themes/{theme_id}.theme.json", json.dumps(theme))
        for path, data in (extra or {}).items():
            archive.writestr(path, data)
    return target


def loose_file(directory, name: str, text: str) -> pathlib.Path:
    path = pathlib.Path(directory) / name
    path.write_text(text, encoding="utf-8")
    return path


def listing(directory) -> list:
    return sorted(p.name for p in pathlib.Path(directory).iterdir())


BASE = {"name": "Base Theme", "dark": True, "ui": {"ToolWindow.background": "#102030"}}
CHILD = {"name": "Child Theme", "parentTheme": "Base Theme"}
MEMBER = "themes/Bad.theme.json"


# Damage to the one member of a jar made by damaged_jar: (raw jar bytes, the member's ZipInfo, where its data starts,
# where its central directory header starts).
def flip_a_data_byte(raw, info, data, header):
    raw[data] ^= 0xFF


def garble_the_deflate_stream(raw, info, data, header):
    raw[data:data + info.compress_size] = b"\xff" * info.compress_size


def mark_encrypted(raw, info, data, header):
    raw[header + 8] |= 1


def claim_an_unsupported_method(raw, info, data, header):
    struct.pack_into("<H", raw, header + 10, 99)


def claim_a_smaller_size(raw, info, data, header):
    struct.pack_into("<I", raw, header + 24, 5)


def damaged_jar(target: pathlib.Path, method: int, damage) -> pathlib.Path:
    """A jar whose only member, themes/Bad.theme.json, is damaged by ``damage``."""
    content = json.dumps({"name": "Bad", "dark": True, "pad": "x" * 200}).encode("utf-8")
    with zipfile.ZipFile(target, "w", method) as archive:
        archive.writestr(MEMBER, content)
    with zipfile.ZipFile(target) as archive:
        info = archive.getinfo(MEMBER)
    raw = bytearray(target.read_bytes())
    local_extra = struct.unpack_from("<H", raw, info.header_offset + 28)[0]
    damage(raw, info, info.header_offset + 30 + len(MEMBER) + local_extra, raw.rindex(b"PK\x01\x02"))
    target.write_bytes(raw)
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


class AlacrittyNames(unittest.TestCase):
    def test_a_file_name_with_no_letters_or_digits_needs_name(self):
        with tempfile.TemporaryDirectory() as scratch:
            blank = pathlib.Path(scratch) / "_.toml"
            blank.write_bytes((DATA / "alacritty" / "gruvbox_dark.toml").read_bytes())
            out = pathlib.Path(scratch) / "out"
            status, _, stderr = run(blank, "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("use --name", stderr)
            self.assertNotIn("internal", stderr)
            self.assertEqual(run(blank, "--name", "Gruvbox", "-o", out)[0], 0)


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

    def test_an_alpha_text_colour_is_laid_over_the_panel(self):
        with tempfile.TemporaryDirectory() as scratch:
            path = loose_file(scratch, "Fade.theme.json", json.dumps(
                {"name": "Fade", "dark": True, "ui": {"Label.foreground": "#FFFFFF80", "ToolWindow.background": "#3C3F41"}}))
            themes, loose = tool.themes_in(path)
            ui = tool.convert_intellij_theme(themes[0], themes, None, loose_dir=loose).doc["ui"]["dark"]
        self.assertEqual(ui["surface.panel"], "#3C3F41")
        self.assertEqual(ui["text.primary"], "#9E9FA0", "spec 5.3 step 4: #FFFFFF80 over the panel, not over black")

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

    def test_entries_are_counted_across_the_archive_and_its_nested_jars(self):
        with tempfile.TemporaryDirectory() as scratch:
            inner = io.BytesIO()
            with zipfile.ZipFile(inner, "w") as archive:
                for index in range(3):
                    archive.writestr(f"f{index}.txt", "x")
            download = pathlib.Path(scratch) / "Pack.zip"
            with zipfile.ZipFile(download, "w") as outer:
                outer.writestr("Pack/lib/a.jar", inner.getvalue())
                outer.writestr("Pack/lib/b.jar", inner.getvalue())
            with unittest.mock.patch.object(tool, "MAX_ENTRIES", 8):  # 2 + 3 + 3 entries in all
                tool.Files.archive(download)
            with unittest.mock.patch.object(tool, "MAX_ENTRIES", 7):
                with self.assertRaisesRegex(tool.ConversionError, "more than 10,000 entries"):
                    tool.Files.archive(download)

    def test_a_scheme_file_and_a_loose_theme_are_capped_like_archive_members(self):
        with tempfile.TemporaryDirectory() as scratch:
            out = pathlib.Path(scratch) / "out"
            tiny = loose_file(scratch, "Tiny.theme.json", '{ "name": "Tiny", "dark": true }')
            big_scheme = loose_file(scratch, "big.xml", "<scheme>" + " " * 200 + "</scheme>")
            big_theme = loose_file(scratch, "Big.theme.json", '{ "name": "Big", "pad": "' + "x" * 200 + '" }')
            beside = loose_file(scratch, "Beside.theme.json", '{ "name": "Beside", "editorScheme": "Aside.xml" }')
            loose_file(scratch, "Aside.xml", "<scheme>" + " " * 200 + "</scheme>")
            with unittest.mock.patch.object(tool, "MAX_FILE", 100):
                for args, message in (((tiny, "--scheme", big_scheme), "error: big.xml: larger than"),
                                      ((big_theme,), "error: Big.theme.json: larger than"),
                                      ((beside,), "error: Aside.xml: larger than")):
                    with self.subTest(message):
                        status, _, stderr = run(*args, "-o", out)
                        self.assertEqual(status, 1, stderr)
                        self.assertIn(message, stderr)

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

    def test_verbose_prints_where_each_ui_role_came_from_for_an_intellij_theme_only(self):
        with tempfile.TemporaryDirectory() as out:
            status, stdout, _ = run(DATA / "intellij" / "loose" / "Ocean.theme.json", "-o", out, "--verbose")
            self.assertEqual(status, 0)
            self.assertIn("  surface.panel: #16242F from ", stdout)
            status, stdout, _ = run(DATA / "alacritty" / "gruvbox_dark.toml", "-o", out, "--verbose")
            self.assertEqual(stdout.splitlines(), ["gruvbox-dark.json: Gruvbox Dark (terminal scheme, dark)"])
        status, stdout, _ = run("--help")
        self.assertEqual(status, 0)
        self.assertIn("print where each UI role came from (IntelliJ themes)", " ".join(stdout.split()))

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

    def test_theme_resolves_parents_among_all_the_plugins_themes(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = theme_jar(pathlib.Path(scratch) / "family.jar", {"base": BASE, "child": CHILD})
            out = pathlib.Path(scratch) / "out"
            status, _, stderr = run(jar, "--theme", "Child Theme", "-o", out)
            self.assertEqual(status, 0, stderr)
            self.assertNotIn("not one Jasper knows", stderr)
            self.assertEqual(listing(out), ["child-theme.json"])
            doc = json.loads((out / "child-theme.json").read_text(encoding="utf-8"))
        self.assertEqual(list(doc["ui"]), ["dark"], "dark, as its parent is")
        self.assertEqual(doc["ui"]["dark"]["surface.panel"], "#102030", "the parent's colour")

    def test_a_classic_theme_takes_its_chrome_from_the_plugins_islands_variant_of_it(self):
        """Jasper's tool windows are islands: a plugin's Islands variant says which chrome they stand on."""
        night = {"name": "Night", "dark": True, "ui": {"ToolWindow.background": "#333333", "StatusBar.background": "#333333"}}
        islands = {"name": "Islands Night", "dark": True, "parentTheme": "Islands Dark",
                   "ui": {"ToolWindow.background": "#333333", "StatusBar.background": "#111111"}}
        other = {"name": "Islands Day", "dark": True, "parentTheme": "Islands Dark", "ui": {"StatusBar.background": "#222222"}}
        with tempfile.TemporaryDirectory() as scratch:
            jar = theme_jar(pathlib.Path(scratch) / "night.jar", {"night": night, "islands": islands, "day": other})
            themes = tool.plugin_themes(tool.Files.archive(jar))
            classic = tool.convert_intellij_theme(themes[0], themes, None)
            own = tool.convert_intellij_theme(themes[1], themes, None)
            alone = tool.convert_intellij_theme(themes[0], themes[:1], None)
        self.assertEqual(classic.doc["ui"]["dark"]["surface.app"], "#111111", "the Islands variant's chrome")
        self.assertEqual(classic.doc["ui"]["dark"]["surface.panel"], "#333333", "the classic theme's own tool windows")
        self.assertIn("surface.app: #111111 from StatusBar.background (Islands Night)", classic.sources)
        self.assertTrue(classic.summary.endswith("; chrome from Islands Night"), classic.summary)
        self.assertEqual(own.doc["ui"]["dark"]["surface.app"], "#111111")
        self.assertNotIn("chrome from", own.summary, "an Islands theme keeps its own chrome")
        self.assertEqual(alone.doc["ui"]["dark"]["surface.app"], "#333333", "without an Islands variant")

    def test_a_main_toolbar_apart_from_the_chrome_gives_the_title_bar_its_own_colours(self):
        """IntelliJ New UI Light's dark header: MainToolbar Gray2 under Gray12 on a light window."""
        header = {"name": "Day", "dark": False, "parentTheme": "ExperimentalLight", "ui": {"StatusBar.background": "#F7F8FA"}}
        flat = {"name": "Even", "dark": False, "ui": {"StatusBar.background": "#F7F8FA", "MainToolbar.background": "#F5F6F7"}}
        with tempfile.TemporaryDirectory() as scratch:
            themes = tool.plugin_themes(tool.Files.archive(theme_jar(pathlib.Path(scratch) / "day.jar", {"day": header, "even": flat})))
            day = tool.convert_intellij_theme(themes[0], themes, None).doc["ui"]["light"]
            even = tool.convert_intellij_theme(themes[1], themes, None).doc["ui"]["light"]
        self.assertEqual(list(day)[-2:], tool.OPTIONAL_ROLES)
        self.assertLess(tool.lightness(tool.parse_hex(day["surface.titlebar"])), 30, "the dark header")
        self.assertGreater(tool.contrast(tool.parse_hex(day["text.titlebar"]), tool.parse_hex(day["surface.titlebar"])), 7)
        self.assertEqual(list(even), tool.ROLES, "a main toolbar like the chrome adds nothing")

    def test_keys_the_theme_sets_itself_become_override_keys_after_the_roles(self):
        """Dawn sets three of the IntelliJ keys; ExperimentalLight, its IntelliJ parent, sets others that do not count."""
        with tempfile.TemporaryDirectory() as scratch:
            themes = tool.plugin_themes(tool.Files.archive(plugin_jar(pathlib.Path(scratch) / "ocean.jar")))
            dawn = tool.convert_intellij_theme(next(t for t in themes if t.id == "ocean-dawn"), themes, None)
        light = dawn.doc["ui"]["light"]
        self.assertEqual(list(light), tool.ROLES + ["surface.tableHeader", "warning.ground", "warning.border"])
        self.assertEqual(light["surface.tableHeader"], "#F2EBE1")
        self.assertEqual(light["warning.ground"],
                         tool.hex_of(tool.over(tool.parse_hex("#E0A0301A"), tool.parse_hex(light["surface.card"]))), "laid over the card")
        self.assertIn("surface.tableHeader: #F2EBE1 from TableHeader.background", dawn.sources)

    def test_a_wildcard_or_an_intellij_parent_fills_no_override_and_a_parent_in_the_plugin_does(self):
        night = {"name": "Night", "dark": True, "parentTheme": "ExperimentalDark",
                 "ui": {"*": {"stripeColor": "#123456", "hoverBackground": "#654321"}}}
        base = {"name": "Base Theme", "dark": True,
                "ui": {"ToolWindow.background": "#102030", "TableHeader.background": "#203040", "Table.stripeColor": "#151515"}}
        child = {"name": "Child Theme", "parentTheme": "Base Theme", "ui": {"Table.stripeColor": "#181818"}}
        with tempfile.TemporaryDirectory() as scratch:
            themes = tool.plugin_themes(tool.Files.archive(theme_jar(pathlib.Path(scratch) / "t.jar",
                                                                     {"night": night, "base": base, "child": child})))
            converted = {theme.id: tool.convert_intellij_theme(theme, themes, None).doc["ui"]["dark"] for theme in themes}
        self.assertFalse(set(converted["night"]) & set(tool.OVERRIDE_KEYS), "wildcards and IntelliJ's own keys do not count")
        self.assertEqual(converted["child"]["surface.tableHeader"], "#203040", "from its parent in the plugin")
        self.assertEqual(converted["child"]["surface.zebra"], "#181818", "its own key first")

    def test_the_check_takes_override_keys_after_the_roles_in_order_and_opaque(self):
        themes, loose = tool.themes_in(DATA / "intellij" / "loose" / "Ocean.theme.json")
        doc = tool.convert_intellij_theme(themes[0], themes, None, loose_dir=loose).doc
        dark = doc["ui"]["dark"]
        self.assertEqual(list(dark)[len(tool.ROLES):], ["surface.header", "surface.header.active", "surface.rowHover"])
        tool.validate(doc, "ocean.json")
        def check(roles, message):
            doc["ui"]["dark"] = roles
            with self.assertRaisesRegex(tool.ConversionError, message):
                tool.validate(doc, "ocean.json")
        keys = list(dark)
        check({key: dark[key] for key in keys[:-3] + [keys[-2], keys[-3], keys[-1]]}, "internal: ui.dark roles")
        check({key: dark[key] for key in [keys[-1]] + keys[:-1]}, "internal: ui.dark roles")
        check(dict(dark, **{"surface.header": "#16242F80"}), "internal: ui.dark.surface.header")
        titled = {key: dark[key] for key in tool.ROLES} | {"surface.titlebar": "#000000", "text.titlebar": "#FFFFFF"} \
            | {key: dark[key] for key in keys[len(tool.ROLES):]}
        doc["ui"]["dark"] = titled
        tool.validate(doc, "ocean.json")
        check({key: dark[key] for key in tool.ROLES} | {"surface.header": "#16242F", "surface.titlebar": "#000000"},
              "internal: ui.dark roles")

    def test_chosen_roles_take_a_colour_of_the_themes_own_palette_or_a_hex_colour(self):
        night = {"name": "Night", "dark": True, "colors": {"deep": "#0A0B0C"},
                 "ui": {"Panel.background": "#333333", "SidePanel.background": "#333333"}}
        with tempfile.TemporaryDirectory() as scratch:
            themes = tool.plugin_themes(tool.Files.archive(theme_jar(pathlib.Path(scratch) / "night.jar", {"night": night})))
            result = tool.convert_intellij_theme(themes[0], themes, None, roles={"surface.sidebar": "deep", "surface.card": "#123456"})
            with self.assertRaisesRegex(tool.ConversionError, 'surface.card: "abyss" is neither a colour nor one of the theme\'s colours'):
                tool.convert_intellij_theme(themes[0], themes, None, roles={"surface.card": "abyss"})
            with self.assertRaisesRegex(tool.ConversionError, 'unknown role "surface.nowhere"'):
                tool.convert_intellij_theme(themes[0], themes, None, roles={"surface.nowhere": "deep"})
        self.assertEqual(result.doc["ui"]["dark"]["surface.sidebar"], "#0A0B0C")
        self.assertEqual(result.doc["ui"]["dark"]["surface.card"], "#123456")
        self.assertEqual(result.doc["ui"]["dark"]["surface.dialog"], "#333333", "a role not chosen keeps its IntelliJ key")
        self.assertIn("surface.sidebar: #0A0B0C from deep (chosen)", result.sources)

    def test_list_resolves_dark_and_light_through_the_parent_chain(self):
        themes = {"base": BASE, "child": CHILD, "night": {"name": "Night", "parentTheme": "Darcula"},
                  "plain": {"name": "Plain"}}
        with tempfile.TemporaryDirectory() as scratch:
            status, stdout, stderr = run("--list", theme_jar(pathlib.Path(scratch) / "family.jar", themes))
        self.assertEqual(status, 0, stderr)
        self.assertEqual(stdout.splitlines(), ["family.jar: Base Theme (id base, Dark)",
                                               "family.jar: Child Theme (id child, Dark)",
                                               "family.jar: Night (id night, Dark)",
                                               "family.jar: Plain (id plain, Light)"])

    def test_an_archive_without_themes_is_an_error(self):
        with tempfile.TemporaryDirectory() as scratch:
            root = pathlib.Path(scratch)
            with zipfile.ZipFile(root / "empty.jar", "w"):
                pass
            theme_jar(root / "none.jar", {})
            inner = io.BytesIO()
            with zipfile.ZipFile(inner, "w") as archive:
                archive.writestr("themes/A.theme.json", '{ "name": "A", "dark": true }')
            with zipfile.ZipFile(root / "Outside.zip", "w") as archive:
                archive.writestr("Outside/extra/inner.jar", inner.getvalue())
            for name in ("empty.jar", "none.jar", "Outside.zip"):
                with self.subTest(name):
                    out = root / "out"
                    status, _, stderr = run(root / name, "-o", out)
                    self.assertEqual(status, 1, stderr)
                    self.assertIn(f"error: {name}: no themes found", stderr)
                    self.assertFalse(out.exists() and listing(out), "nothing is written")
                    status, _, stderr = run("--list", root / name)
                    self.assertEqual(status, 1, stderr)
                    self.assertIn(f"error: {name}: no themes found", stderr)

    def test_the_by_name_scheme_scan_skips_other_xml_and_parses_each_file_once(self):
        ocean = (DATA / "intellij" / "plugin" / "themes" / "Ocean.xml").read_bytes()
        by_name = {"name": "A", "dark": True, "editorScheme": "Ocean"}
        extra = {"aaa/Unrelated.xml": '<!DOCTYPE x [<!ENTITY a "b">]><x>&a;</x>', "aab/Other.xml": "<not-a-scheme/>",
                 "aac/Broken.xml": "<scheme", "schemes/Ocean.xml": ocean}
        with tempfile.TemporaryDirectory() as scratch:
            jar = theme_jar(pathlib.Path(scratch) / "scan.jar", {"a": by_name, "b": {**by_name, "name": "B"}}, extra)
            out = pathlib.Path(scratch) / "out"
            with unittest.mock.patch.object(tool, "parse_xml", wraps=tool.parse_xml) as parse:
                status, _, stderr = run(jar, "-o", out)
            self.assertEqual(status, 0, stderr)
            for name in ("a.json", "b.json"):
                doc = json.loads((out / name).read_text(encoding="utf-8"))
                self.assertEqual(doc["terminal"]["dark"]["bg"], "#0B141C", "Ocean.xml's console background")
        labels = [call.args[1] for call in parse.call_args_list if call.args[1].startswith("scan.jar!")]
        self.assertEqual(sorted(labels), sorted(set(labels)), "no file is parsed twice")
        self.assertIn("scan.jar!schemes/Ocean.xml", labels)

    def test_a_theme_with_an_empty_name_needs_name(self):
        with tempfile.TemporaryDirectory() as scratch:
            out = pathlib.Path(scratch) / "out"
            theme = loose_file(scratch, "Empty.theme.json", '{ "name": "", "dark": true }')
            status, _, stderr = run(theme, "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("error: Empty.theme.json: the theme has no name; use --name", stderr)
            self.assertEqual(run(theme, "--name", "Filled", "-o", out)[0], 0)

    def test_a_name_with_no_usable_characters_needs_name(self):
        with tempfile.TemporaryDirectory() as scratch:
            night = pathlib.Path(scratch) / "night.theme.json"
            night.write_text('{ "name": "\u041d\u043e\u0447\u044c", "dark": true }', encoding="utf-8")
            status, _, stderr = run(night, "-o", pathlib.Path(scratch) / "out")
            self.assertEqual(status, 1)
            self.assertIn("the name has no letters or digits for a file name; use --name", stderr)
            self.assertEqual(run(night, "--name", "Night", "-o", pathlib.Path(scratch) / "out")[0], 0)


PRIMARY = '[colors.primary]\nbackground = "#000000"\nforeground = "#ffffff"\n'
NORMAL = "[colors.normal]\n" + "".join(f'{name} = "#101010"\n' for name in tool.ANSI)


class Failures(unittest.TestCase):
    """Bad input is a message and a status, never a traceback, and never costs another input its output."""

    def test_a_damaged_zip_member_is_a_message(self):
        cases = (("a bad CRC", zipfile.ZIP_STORED, flip_a_data_byte),
                 ("a corrupt deflate stream", zipfile.ZIP_DEFLATED, garble_the_deflate_stream),
                 ("an encrypted member", zipfile.ZIP_STORED, mark_encrypted),
                 ("an unsupported method", zipfile.ZIP_STORED, claim_an_unsupported_method),
                 ("a header that declares less than the real size", zipfile.ZIP_STORED, claim_a_smaller_size))
        for case, method, damage in cases:
            with self.subTest(case), tempfile.TemporaryDirectory() as scratch:
                jar = damaged_jar(pathlib.Path(scratch) / "bad.jar", method, damage)
                out = pathlib.Path(scratch) / "out"
                status, _, stderr = run(jar, "-o", out)
                self.assertEqual(status, 1, stderr)
                self.assertIn(f"error: bad.jar!{MEMBER}: cannot be read", stderr)
                self.assertNotIn("Traceback", stderr)
                self.assertEqual(listing(out), [])
                status, _, stderr = run("--list", jar)
                self.assertEqual(status, 1, stderr)
                self.assertIn(f"error: bad.jar!{MEMBER}: cannot be read", stderr)

    def test_a_member_that_ends_early_is_a_message(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = damaged_jar(pathlib.Path(scratch) / "bad.jar", zipfile.ZIP_STORED, lambda *damage: None)
            with unittest.mock.patch.object(zipfile.ZipFile, "open", side_effect=EOFError("ended early")):
                status, _, stderr = run(jar, "-o", pathlib.Path(scratch) / "out")
        self.assertEqual(status, 1)
        self.assertIn(f"error: bad.jar!{MEMBER}: cannot be read: ended early", stderr)

    def test_a_wrong_typed_key_is_a_message_naming_the_key_and_the_file(self):
        cases = (("colors", []), ("ui", []), ("ui", "x"), ("parentTheme", ["a"]), ("editorScheme", 5),
                 ("editorScheme", ["x"]), ("dark", "yes"), ("name", 5))
        with tempfile.TemporaryDirectory() as scratch:
            for key, value in cases:
                with self.subTest(f"{key} = {value!r}"):
                    theme = loose_file(scratch, "Wrong.theme.json", json.dumps({"name": "Wrong", "dark": True, key: value}))
                    status, _, stderr = run(theme, "-o", pathlib.Path(scratch) / "out")
                    self.assertEqual(status, 1, stderr)
                    self.assertIn(f'error: Wrong.theme.json: "{key}" must be', stderr)
                    self.assertNotIn("Traceback", stderr)
            jar = theme_jar(pathlib.Path(scratch) / "bad.jar", {"x": {"name": "X", "ui": []}})
            status, _, stderr = run(jar, "-o", pathlib.Path(scratch) / "out")
            self.assertEqual(status, 1)
            self.assertIn('error: bad.jar!themes/x.theme.json: "ui" must be an object', stderr)
            self.assertEqual(listing(pathlib.Path(scratch) / "out"), [])

    def test_a_null_is_the_same_as_an_absent_key(self):
        with tempfile.TemporaryDirectory() as scratch:
            nulls = {"name": "Nulls", "dark": None, "parentTheme": None, "editorScheme": None, "colors": None, "ui": None}
            theme = loose_file(scratch, "Nulls.theme.json", json.dumps(nulls))
            status, _, stderr = run(theme, "-o", pathlib.Path(scratch) / "out")
            self.assertEqual(status, 0, stderr)
            self.assertEqual(listing(pathlib.Path(scratch) / "out"), ["nulls.json"])

    def test_a_wrong_typed_toml_table_is_a_message(self):
        cases = (("general", "general = 5\n" + PRIMARY + NORMAL),
                 ("colors.primary", "[colors]\nprimary = 5\n" + NORMAL),
                 ("colors.cursor", '[colors]\ncursor = ["cursor"]\n' + PRIMARY + NORMAL),
                 ("colors.selection", "[colors]\nselection = 5\n" + PRIMARY + NORMAL),
                 ("colors.normal", '[colors]\nnormal = "x"\n' + PRIMARY),
                 ("colors.bright", "[colors]\nbright = 1\n" + PRIMARY + NORMAL))
        with tempfile.TemporaryDirectory() as scratch:
            valid = loose_file(scratch, "Valid.toml", PRIMARY + NORMAL)
            self.assertEqual(run(valid, "-o", pathlib.Path(scratch) / "out")[0], 0, "the base file converts")
            for key, text in cases:
                with self.subTest(key):
                    wrong = loose_file(scratch, "Wrong.toml", text)
                    status, _, stderr = run(wrong, "-o", pathlib.Path(scratch) / "out")
                    self.assertEqual(status, 1, stderr)
                    self.assertIn(f"error: Wrong.toml: {key} must be a table", stderr)
                    self.assertNotIn("Traceback", stderr)

    def test_deeply_nested_input_is_a_message(self):
        depth = 100_000
        nested_ui = '{ "name": "Deep", "dark": true, "ui": ' + '{ "a": ' * 2000 + '"#ffffff"' + " }" * 2000 + " }"
        with tempfile.TemporaryDirectory() as scratch:
            for name, text in (("Deep.theme.json", "[" * depth + "]" * depth), ("Ui.theme.json", nested_ui),
                               ("Deep.toml", "a = " + "[" * depth + "]" * depth + "\n")):
                with self.subTest(name):
                    status, _, stderr = run(loose_file(scratch, name, text), "-o", pathlib.Path(scratch) / "out")
                    self.assertEqual(status, 1, stderr)
                    self.assertIn(f"error: {name}: ", stderr)
                    self.assertIn("nested too deeply", stderr)
                    self.assertNotIn("Traceback", stderr)

    def test_a_number_with_too_many_digits_is_a_message(self):
        with tempfile.TemporaryDirectory() as scratch:
            status, _, stderr = run(loose_file(scratch, "Big.theme.json", '{ "name": "Big", "n": ' + "1" * 5000 + " }"),
                                    "-o", pathlib.Path(scratch) / "out")
        self.assertEqual(status, 1)
        self.assertIn("error: Big.theme.json: not a JSON file", stderr)

    def test_a_broken_input_does_not_lose_the_next_one(self):
        with tempfile.TemporaryDirectory() as scratch:
            jar = damaged_jar(pathlib.Path(scratch) / "bad.jar", zipfile.ZIP_STORED, flip_a_data_byte)
            out = pathlib.Path(scratch) / "out"
            status, stdout, stderr = run(jar, DATA / "alacritty" / "gruvbox_dark.toml", "-o", out)
            self.assertEqual(status, 1)
            self.assertIn(f"error: bad.jar!{MEMBER}: cannot be read", stderr)
            self.assertEqual(listing(out), ["gruvbox-dark.json"])

    def test_an_unexpected_exception_is_reported_for_its_input_and_the_others_are_written(self):
        real = tool.convert_alacritty

        def convert(path, name=None):
            if path.name == "gruvbox_dark.toml":
                raise KeyError("boom")
            return real(path, name)
        with tempfile.TemporaryDirectory() as out, unittest.mock.patch.object(tool, "convert_alacritty", convert):
            status, _, stderr = run(DATA / "alacritty" / "gruvbox_dark.toml", DATA / "alacritty" / "paper-light.toml", "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("error: gruvbox_dark.toml: internal error: KeyError: 'boom'", stderr)
            self.assertNotIn("Traceback", stderr)
            self.assertEqual(listing(out), ["paper-light.json"])

    def test_a_malformed_theme_does_not_lose_the_others_of_its_plugin(self):
        themes = {"good": {"name": "Good", "dark": True}, "bad": {"name": "Bad", "ui": []}, "fine": {"name": "Fine", "dark": False}}
        with tempfile.TemporaryDirectory() as scratch:
            jar = theme_jar(pathlib.Path(scratch) / "family.jar", themes)
            out = pathlib.Path(scratch) / "out"
            status, stdout, stderr = run(jar, DATA / "alacritty" / "gruvbox_dark.toml", "-o", out)
            self.assertEqual(status, 1, stderr)
            self.assertIn('error: family.jar!themes/bad.theme.json: "ui" must be an object', stderr)
            self.assertNotIn("Traceback", stderr)
            self.assertEqual(listing(out), ["fine.json", "good.json", "gruvbox-dark.json"])
            self.assertIn("good.json: Good (dark)", stdout)

    def test_an_unexpected_exception_in_one_theme_is_reported_for_that_theme_and_its_plugins_others_are_written(self):
        real = tool.convert_intellij_theme

        def convert(theme, *rest, **named):
            if theme.id == "bad":
                raise KeyError("boom")
            return real(theme, *rest, **named)
        themes = {"good": {"name": "Good", "dark": True}, "bad": {"name": "Bad", "dark": True}}
        with tempfile.TemporaryDirectory() as scratch, unittest.mock.patch.object(tool, "convert_intellij_theme", convert):
            out = pathlib.Path(scratch) / "out"
            status, _, stderr = run(theme_jar(pathlib.Path(scratch) / "family.jar", themes), "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("error: family.jar!themes/bad.theme.json: internal error: KeyError: 'boom'", stderr)
            self.assertNotIn("Traceback", stderr)
            self.assertEqual(listing(out), ["good.json"])

    def test_output_that_the_console_cannot_encode_is_escaped_and_the_next_input_is_still_written(self):
        with tempfile.TemporaryDirectory() as scratch:
            first = pathlib.Path(scratch) / "\u6771\u4eac_night.toml"
            first.write_text((DATA / "alacritty" / "gruvbox_dark.toml").read_text(encoding="utf-8"), encoding="utf-8")
            second = pathlib.Path(scratch) / "zz_paper.toml"
            second.write_text((DATA / "alacritty" / "paper-light.toml").read_text(encoding="utf-8"), encoding="utf-8")
            broken = loose_file(scratch, "\u6771\u4eac_broken.toml", "this is not [toml")
            out = pathlib.Path(scratch) / "out"
            console = io.TextIOWrapper(io.BytesIO(), encoding="cp1252", errors="strict")
            problems = io.TextIOWrapper(io.BytesIO(), encoding="cp1252", errors="strict")
            with contextlib.redirect_stdout(console), contextlib.redirect_stderr(problems):
                status = tool.main([str(first), str(broken), str(second), "-o", str(out)])
            console.flush()
            problems.flush()
            self.assertEqual(status, 1)
            self.assertEqual(listing(out), ["night.json", "zz-paper.json"])
            self.assertIn("\\u6771\\u4eac", console.buffer.getvalue().decode("ascii"))
            self.assertIn("error: \\u6771\\u4eac_broken.toml: ", problems.buffer.getvalue().decode("ascii"))

    def test_an_output_that_is_a_file_or_a_directory_at_the_target_is_a_message(self):
        source = DATA / "alacritty" / "gruvbox_dark.toml"
        with tempfile.TemporaryDirectory() as scratch:
            plain = loose_file(scratch, "plain", "not a folder")
            status, _, stderr = run(source, "-o", plain)
            self.assertEqual(status, 1)
            self.assertIn(f"error: {plain}: ", stderr)
            self.assertNotIn("Traceback", stderr)
            out = pathlib.Path(scratch) / "out"
            (out / "gruvbox-dark.json").mkdir(parents=True)
            status, _, stderr = run(source, "-o", out, "--force")
            self.assertEqual(status, 1)
            self.assertIn(f"error: {out / 'gruvbox-dark.json'}: ", stderr)
            self.assertNotIn("Traceback", stderr)
            self.assertEqual(listing(out), ["gruvbox-dark.json"], "the directory stays and no temporary file is left")
            self.assertTrue((out / "gruvbox-dark.json").is_dir())

    def test_an_output_directory_that_cannot_be_written_or_searched_is_a_message(self):
        for mode in (0o500, 0o000):
            with self.subTest(oct(mode)), tempfile.TemporaryDirectory() as scratch:
                locked = pathlib.Path(scratch) / "locked"
                locked.mkdir()
                locked.chmod(mode)
                try:
                    if os.access(locked, os.W_OK if mode == 0o500 else os.X_OK):
                        self.skipTest("this account can use a directory whatever its mode")
                    status, _, stderr = run(DATA / "alacritty" / "gruvbox_dark.toml", "-o", locked)
                finally:
                    locked.chmod(0o700)
                self.assertEqual(status, 1)
                self.assertIn(f"error: {locked / 'gruvbox-dark.json'}: ", stderr)
                self.assertNotIn("Traceback", stderr)


class Writes(unittest.TestCase):
    def test_a_lone_surrogate_in_a_name_fails_before_any_file_exists_and_force_keeps_a_good_file(self):
        with tempfile.TemporaryDirectory() as scratch:
            theme = loose_file(scratch, "Bad.theme.json", '{ "name": "Bad\\ud800", "dark": true }')
            out = pathlib.Path(scratch) / "out"
            status, _, stderr = run(theme, "-o", out)
            self.assertEqual(status, 1)
            self.assertIn("error: Bad.theme.json: ", stderr)
            self.assertEqual(listing(out), [], "nothing was created")
            (out / "bad.json").write_text("GOOD", encoding="utf-8")
            status, _, stderr = run(theme, "-o", out, "--force")
            self.assertEqual(status, 1)
            self.assertEqual((out / "bad.json").read_text(encoding="utf-8"), "GOOD", "--force never truncates a good file")
            self.assertEqual(listing(out), ["bad.json"])

    def test_a_failed_replace_keeps_the_good_file_and_leaves_no_temporary_file(self):
        source = DATA / "alacritty" / "gruvbox_dark.toml"
        with tempfile.TemporaryDirectory() as out:
            self.assertEqual(run(source, "-o", out)[0], 0)
            good = (pathlib.Path(out) / "gruvbox-dark.json").read_bytes()
            with unittest.mock.patch("os.replace", side_effect=PermissionError(13, "Permission denied")):
                status, _, stderr = run(source, "-o", out, "--force")
            self.assertEqual(status, 1)
            self.assertIn(f"error: {pathlib.Path(out) / 'gruvbox-dark.json'}: Permission denied", stderr)
            self.assertEqual((pathlib.Path(out) / "gruvbox-dark.json").read_bytes(), good)
            self.assertEqual(listing(out), ["gruvbox-dark.json"])

    def test_the_built_in_theme_name_is_refused(self):
        with tempfile.TemporaryDirectory() as scratch:
            out = pathlib.Path(scratch) / "out"
            theme = loose_file(scratch, "Cobalt.theme.json", '{ "name": "Slate Cobalt", "dark": true }')
            alacritty = pathlib.Path(scratch) / "slate_cobalt.toml"
            alacritty.write_bytes((DATA / "alacritty" / "gruvbox_dark.toml").read_bytes())
            for source in (theme, alacritty):
                with self.subTest(source.name):
                    status, _, stderr = run(source, "-o", out)
                    self.assertEqual(status, 1)
                    self.assertIn(f'error: {source.name}: "slate-cobalt" is a built-in Jasper theme; choose another name '
                                  "with --name", stderr)
                    self.assertEqual(listing(out), [])
            self.assertEqual(run(theme, "--name", "Slate Cobalt Two", "-o", out)[0], 0)
            self.assertEqual(listing(out), ["slate-cobalt-two.json"])


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
        bundled = pathlib.Path(__file__).resolve().parents[2] / "jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-cobalt.json"
        if not bundled.is_file():
            self.skipTest("not in the repository")
        doc = json.loads(bundled.read_text(encoding="utf-8"))
        tool.validate(doc, "slate-cobalt.json")
        self.assertEqual(list(doc["editor"]["dark"]), tool.EDITOR_SURFACES + tool.SYNTAX_ROLES)


class BuiltIn(unittest.TestCase):
    """The built-in themes besides Slate Cobalt are the converter's output for the vendored Catppuccin plugin."""

    FLAVOURS = ["latte", "frappe", "macchiato", "mocha"]  # the classic themes; the Islands variants are not built in

    def setUp(self):
        if not BUILT_IN_THEMES.is_dir():
            self.skipTest("not in the repository")

    def test_the_vendored_catppuccin_plugin_is_the_recorded_release(self):
        import hashlib
        jar = CATPPUCCIN / "Catppuccin.Theme-3.6.1.jar"
        self.assertEqual(hashlib.sha256(jar.read_bytes()).hexdigest(),
                         "3a1104e0db53ff5ba216c94c9e730a85c7ac2847f4e5fe82d91b99dc91045fff")

    def test_the_built_in_catppuccin_themes_are_the_converters_output(self):
        """With roles.json's hand-picked dialog, card and sidebar colours, named from each flavour's palette."""
        themes = tool.plugin_themes(tool.Files.archive(CATPPUCCIN / "Catppuccin.Theme-3.6.1.jar"))
        roles = json.loads((CATPPUCCIN / "roles.json").read_text(encoding="utf-8"))
        results = []
        for flavour in self.FLAVOURS:
            theme = next(t for t in themes if t.id == f"com.github.catppuccin.{flavour}.jetbrains")
            results.append(tool.convert_intellij_theme(theme, themes, None, roles=roles["light" if flavour == "latte" else "dark"]))
        self.assertEqual([tool.slug(result.doc["name"]) for result in results],
                         [f"catppuccin-{flavour}" for flavour in self.FLAVOURS])
        for result in results:
            name = f"{tool.slug(result.doc['name'])}.json"
            text = tool.dump(result.doc)
            target = BUILT_IN_THEMES / name
            if UPDATE:
                target.write_text(text, encoding="utf-8")
            with self.subTest(name):
                self.assertEqual(text, target.read_text(encoding="utf-8"), name)

    def test_the_built_in_theme_files_are_the_ids_the_converter_refuses(self):
        self.assertEqual(sorted(path.stem for path in BUILT_IN_THEMES.glob("*.json")), sorted(tool.BUILT_IN))

    def test_the_shipped_catppuccin_license_is_the_vendored_one(self):
        self.assertEqual((BUILT_IN_THEMES / "catppuccin.LICENSE.txt").read_bytes(), (CATPPUCCIN / "LICENSE").read_bytes())

    ENTERPRISE = pathlib.Path(__file__).resolve().parents[2] / "plugins/enterprise/src/main/resources/themes/enterprise.json"
    PORT = pathlib.Path(__file__).resolve().parent / "enterprise" / "port.json"

    def test_a_port_fills_override_keys_from_exact_keys_only(self):
        spec = json.loads(self.PORT.read_text(encoding="utf-8"))
        doc = tool.convert_port(spec, "port.json")
        light, dark = doc["ui"]["light"], doc["ui"]["dark"]
        self.assertEqual(light["surface.header"], "#F2F2F2")         # intellijlaf: ToolWindow.Header.inactiveBackground = panel
        self.assertEqual(light["surface.header.active"], "#DFE2E9")  # port.json replaces IntelliJ Light's #E2E6EC
        self.assertNotIn("surface.header.active", dark)               # New UI Dark has only "*.background": the recipe
        self.assertEqual(dark["surface.header"], "#2B2D30")
        self.assertEqual((light["accent"], dark["accent"]), ("#2675BF", "#366ACE"))
        self.assertEqual(list(doc["terminal"]), ["light", "dark"])
        self.assertEqual((doc["terminal"]["light"]["name"], doc["terminal"]["dark"]["name"]), ("Enterprise Light", "Enterprise Dark"))

    def test_the_enterprise_theme_is_the_port_of_intellij_light_and_new_ui_dark(self):
        text = tool.dump(tool.convert_port(json.loads(self.PORT.read_text(encoding="utf-8")), "port.json"))
        if UPDATE:
            self.ENTERPRISE.write_text(text, encoding="utf-8")
        self.assertEqual(text, self.ENTERPRISE.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
