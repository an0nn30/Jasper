# Gotchas from earlier syncs

## Tests and scanners that read docs or Javadoc

| Check | What fails it |
| --- | --- |
| `AppDocumentationTest` | A broken link or `#anchor` in any Markdown under `docs/`, the root READMEs and the module, CLI, launcher and mock-tool READMEs; a changed `<!-- example:name -->` block; a missing file from its GUIDES list |
| `TerminalDocumentationTest`, `UiDocumentationTest` | The same for the terminal and UI guides and their pinned examples |
| `ConfigTemplateTest` | A `CoreSettings` key without its `| \`key\` |` row in `docs/configuration.md`, or `config.example.toml` keys or values that differ from the defaults |
| `VersionRangeTest` | An `sdk = "..."` range in the SDK guides that excludes `JasperSdk.VERSION`; the external example's `jasperSdkVersion` default not equal to it |
| `VimDocsTest` | A changed corpus row in `docs/vim-divergences.md`; a stale `docs/editor-vim-reference.md` (regenerate with `-PupdateDocs=true`) |
| `verifySdkReference` | A stale `docs/sdk-reference.html`, an unresolved `{@link}`, a missing anchor, a dead relative link, a block tag written mid-line |
| `:jasper-sdk:javadoc` | Doclint errors in SDK Javadoc |
| `LookIdentityTest` | Any production string (setting descriptions included) that names "enterprise" |
| `ThemeMigrationCoverageTest` | A Java file that newly matches its scanner pattern (for example Javadoc that mentions `Appearance`) without a row in its migration inventory |
| `ThirdPartyNoticesTest` | `LICENSE.md`'s first and last lines |

## Things that bit earlier syncs

- `origin/main` moves while `check` runs. Re-fetch just before pushing; if it moved, merge again and re-check.
- Feature commits often update guides themselves. Review their edits like any other: they can carry plan talk, miss
  related guides (keyboard shortcuts, configuration, the SDK reference's guide pages) or contradict a rewritten guide.
- A version bump on a plugin or the SDK touches many guides: `plugins.md`, `README.md`, the SDK README's versioning,
  `tools/sdk-reference/guide/01-overview.html` and `15-versions.html`, `STATUS.md`'s current state. Grep for the old
  version and for "every bundled plugin declares".
- A removed API or default leaves mentions everywhere; use `conventions.py --pattern` with the old name until it finds
  nothing.
- `plugins/editor/src/test/resources/markdown/everyday.md` holds a deliberately broken link; the link checker skips
  test resources.
- `StagedTransferLifecycleTest` and `FileServerPluginTest.addsStartsServesAndStopsAServer` time out under heavy load.
  Re-run the class alone before treating a failure as real.
- `checkThemeTool` needs Python 3.11 to 3.13 (`-Pjasper.python=<interpreter>`); 3.14 fails one of its tests.
- The auto-mode permission classifier blocks pushing to `main` without an approval in the conversation; ask.
