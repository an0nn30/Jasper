# Brief for docs-sync agents

Give every writer, verifier and fixer this text, filled in, plus its own file list and task. Agents get AGENTS.md
automatically; this brief adds what a docs sync needs on top.

---

You are updating Jasper's documentation in the worktree `<worktree>` (never touch `~/projects/moray` itself; never run
gradle or launch the app; do not commit or change git state — <say whether a merge is in progress>). The guides must
describe the code at `<source sha>`. The range being documented is `<base>..<source>`: read
`git log --oneline <base>..<source>` and `git diff <base> <source> -- <path>`; the STATUS.md entries for these commits
explain them. The CODE is the authority: verify every statement in it; commit messages, entries and other guides are
only a map.

Jasper is a pluggable DevOps workstation: the workspace, terminal, command palette, notifications, Settings, themes
and looks are the core, and most features are bundled plugins on the public SDK. Describe it that way.

Conventions:
- British spelling; exact names from code (menu labels, command titles, setting keys and defaults, file names,
  capability strings); macOS shortcuts as Cmd, Option, Ctrl, Shift words and Linux/Windows as Ctrl, Alt, Shift, both
  given.
- Current guides describe the system now: no plan numbers, phases, task ids, rulings, review talk, SDK version history
  or migration advice ("needs SDK 0.18.3" as a minimum is fine). Delete what no longer exists; never negate it.
- Records are not rewritten: STATUS.md entries, `docs/superpowers/**`, `docs/design/**`, `docs/benchmarks/**`,
  `*-verification.md`, `docs/terminal-readiness.md`, `docs/documentation-audit.md`.
- `docs/sdk-reference.html` is generated: change `tools/sdk-reference/guide/*.html` or the Javadoc instead.
  Guide pages are HTML fragments: one `<section class="guide" id="...">` with one `<h2>`; link API elements with
  `{@link Type#member}`; code samples in `<pre><code class="language-java">`, HTML-escaped.

Test-pinned rules (the build fails otherwise):
- Never change a `<!-- example:name -->` block: its code is compared with a compiled source.
- `docs/configuration.md` keeps the literal row `| \`<key>\` |` for every CoreSettings key and none for an alias;
  `config.example.toml` keys and values equal the defaults (comments may change).
- Every `sdk = "..."` range in `docs/plugin-authoring.md`, `jasper-sdk/README.md` and `docs/sdk-icons.md` admits
  `JasperSdk.VERSION`, and each of those files keeps at least one.
- `docs/vim-divergences.md`'s corpus table is pinned; `docs/editor-vim-reference.md` is generated.
- Links and `#anchors` resolve; Settings' Help buttons open `docs/configuration.md` anchors, so keep those headings.
- Java edits are Javadoc only and doclint-clean, and must not add a string naming "enterprise" to production code
  (LookIdentityTest) or the words appearance/Appearance, UIManager, paintComponent, paintBorder, `new J<Upper>` or
  `new <Name>Panel/View/Window/Editor/Form/Field/Button/Menu/Picker/Bar/Tree/List/Table` to a Java file that had none
  (ThemeMigrationCoverageTest).

Commands: `python3 .claude/skills/docs-sync/scripts/linkcheck.py . <changed .md files>`; after guide-page or Javadoc
edits, `java tools/sdk-reference/SdkReference.java . <scratch>/gen.html` (it validates every `{@link}`, anchor and
relative link). Use the Edit tool: other agents edit other files at the same time.

Writers: own only `<files>`; report in `cross_doc` what other files need. Verifiers: read only; report only real
problems, each with code evidence (`path:line`) and an exact fix. Fixers: re-check each finding in the code; apply the
right ones, reject the wrong ones with evidence.
