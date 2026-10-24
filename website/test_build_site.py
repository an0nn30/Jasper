"""Exercise the public bundle boundary without publishing or contacting GitHub."""
import shutil
import tempfile
import unittest
from pathlib import Path

import build_site


class PublicBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'website'
        shutil.copytree(Path(__file__).parent, self.source, ignore=shutil.ignore_patterns('__pycache__'))
        self.output = self.root / 'public'
        # The SDK reference lives in the repository's docs/, beside website/.
        (self.root / 'docs').mkdir()
        self.reference = self.root / 'docs/sdk-reference.html'
        self.reference.write_text('<a href="#top">Top</a> <a href="plugins.md#safe-mode">Plugins</a> '
                                  '<a href="../jasper-sdk-testkit/README.md">Testkit</a> <a href="https://example.com/x">Out</a>')

    def test_only_public_site_and_required_assets_ship(self):
        (self.source / 'assets/private-preview.png').write_text('must not ship')
        build_site.build(self.source, self.output)
        files = {str(p.relative_to(self.output)) for p in self.output.rglob('*') if p.is_file()}
        self.assertEqual({p for p in files if '/' not in p},
                         {'index.html', 'style.css', 'script.js', 'workspace-tour.js', 'meadow.css', 'meadow.js'})
        self.assertIn('assets/tabler-LICENSE.txt', files)
        self.assertIn('assets/tour-vault.png', files)
        self.assertIn('assets/workspace-typing/terminal-result.webp', files)
        self.assertEqual(len([p for p in files if p.startswith('assets/workspace-typing/')]), 35)
        self.assertIn('sdk/index.html', files)
        self.assertNotIn('assets/private-preview.png', files)
        self.assertFalse(any('preview' in p or p.startswith('demo/') for p in files))

    def test_missing_asset_fails_before_creating_bundle(self):
        (self.source / 'assets/macos.png').unlink()
        with self.assertRaises(FileNotFoundError):
            build_site.build(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_symlink_cannot_publish_an_external_file(self):
        asset = self.source / 'assets/macos.png'
        asset.unlink()
        asset.symlink_to(self.source / 'README.md')
        with self.assertRaises(ValueError):
            build_site.build(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_link_to_excluded_page_fails_before_creating_bundle(self):
        with (self.source / 'index.html').open('a') as page:
            page.write('<a href="palette-preview.html">Preview</a>')
        with self.assertRaises(ValueError):
            build_site.build(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_sdk_reference_ships_at_sdk_with_its_guide_links_on_github(self):
        build_site.build(self.source, self.output)
        page = (self.output / 'sdk/index.html').read_text()
        self.assertIn('href="#top"', page)
        self.assertIn('href="https://github.com/an0nn30/Jasper/blob/main/docs/plugins.md#safe-mode"', page)
        self.assertIn('href="https://github.com/an0nn30/Jasper/blob/main/jasper-sdk-testkit/README.md"', page)
        self.assertIn('href="https://example.com/x"', page)

    def test_homepage_sdk_links_lead_to_the_published_reference(self):
        self.assertIn('href="sdk/"', (Path(__file__).parent / 'index.html').read_text())
        self.assertNotIn('docs/plugin-authoring.md', (Path(__file__).parent / 'index.html').read_text())

    def test_missing_sdk_reference_fails_before_creating_bundle(self):
        self.reference.unlink()
        with self.assertRaises(FileNotFoundError):
            build_site.build(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_reference_link_outside_the_repository_fails_before_creating_bundle(self):
        self.reference.write_text('<a href="../../elsewhere.md">Out</a>')
        with self.assertRaises(ValueError):
            build_site.build(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_existing_destination_is_preserved(self):
        self.output.mkdir()
        sentinel = self.output / 'keep.txt'
        sentinel.write_text('keep')
        with self.assertRaises(FileExistsError):
            build_site.build(self.source, self.output)
        self.assertEqual(sentinel.read_text(), 'keep')


if __name__ == '__main__':
    unittest.main()
