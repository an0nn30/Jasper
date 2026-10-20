import unittest
from seed_refresh import resolve

class ClosureTest(unittest.TestCase):
    def test_base_sources_are_not_binary_packages(self):
        text = '\n@ a\ncategory: Base\nversion: 1\ninstall: a 1 a\nsource: s 1 a\n\n@ a-src\ncategory: Base\nversion: 1\nsource: s 1 a\n'
        self.assertEqual(set(resolve(text, [])), {'a'})
    def test_virtual_and_version_constraints(self):
        text = '\n@ a\ncategory: Base\nversion: 1\ninstall: a 1 a\nsource: s 1 a\ndepends2: cap, b ( >= 2 ), _windows ( >= 6.3 )\n\n@ b\nversion: 2\ninstall: b 1 a\nsource: s 1 a\nprovides: cap\n'
        self.assertEqual(resolve(text, [])['a']['dependencies'], ['b'])
        with self.assertRaises(ValueError): resolve(text.replace('version: 2', 'version: 1'), [])
    def test_missing_and_ambiguous_dependencies_fail(self):
        text = '\n@ a\ncategory: Base\nversion: 1\ninstall: a 1 a\nsource: s 1 a\ndepends2: missing\n'
        with self.assertRaises(ValueError): resolve(text, [])
    def test_base_with_only_test_version_matches_setup_default(self):
        text = '\n@ a\ncategory: Base\n[test]\nversion: 2\ninstall: a 1 a\nsource: s 1 a\n'
        self.assertEqual(resolve(text, [])['a']['version'], '2')
    def test_previous_version_does_not_replace_current(self):
        text = '\n@ a\ncategory: Base\nversion: 2\ninstall: a 1 a\nsource: s 1 a\n[prev]\nversion: 1\ninstall: old 1 a\n'
        self.assertEqual(resolve(text, [])['a']['version'], '2')
if __name__ == '__main__': unittest.main()
