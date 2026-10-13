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
