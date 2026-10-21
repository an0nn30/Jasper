---
name: docs-sync
description: Bring Jasper's documentation and SDK reference back in line with the code on main, then merge the docs into main. Use this whenever the user asks to update, sync, refresh, review or audit the docs against main ("pull main and update the docs", "document the new commits", "are the docs still right?", "check the SDK docs", "docs review before release"), after features land on main, or when docs/sdk-reference.html, the guides in docs/, the READMEs or the SDK Javadoc may have drifted from what the code does — even if the user does not name this skill.
---

# Docs sync

Jasper's guides describe the code as it is on `main`, and `docs/sdk-reference.html` is generated from the SDK's
Javadoc. Features land on `main` faster than the guides follow, so this skill runs the loop that brings them back in
line: find what changed since the last sync, map it to the guides that describe it, update them against the code,
verify them adversarially, run the mechanical and build checks, and merge the result into `main`.

Read `AGENTS.md` first if it is not already in context: its "Documentation edits" rules and "Never do these without
the user" list apply throughout. The scripts below live in `.claude/skills/docs-sync/scripts/` and run from the
repository root of the docs worktree.

## 1. Work in a docs worktree

Builds in `~/projects/moray` replace the jars of an app the user may be running from it, so never build or edit there.
Reuse the docs worktree if one exists (`git worktree list`; earlier syncs used `.claude/worktrees/v1-docs` on
`claude/v1-docs`), or create one on its own branch:

```bash
git worktree add -b claude/docs-sync .claude/worktrees/docs-sync origin/main   # first time only
cd .claude/worktrees/docs-sync && git fetch origin
```

Decide what "main" means for this run. The source is `origin/main`; if the user's local `main` has commits that are
not on `origin` and the user asked to "pull main", include them too, and say that pushing `main` later will publish
them. Merge the source into the docs branch (`git merge --no-ff --no-commit <source>`) or fast-forward when the docs
branch has nothing of its own.

## 2. Find the range and the guides it touches

```bash
python3 .claude/skills/docs-sync/scripts/last_sync.py            # the main commit the guides were last brought up to
python3 .claude/skills/docs-sync/scripts/doc_areas.py <base> <source>
```

`last_sync.py` reads the `Docs-Synced-Through:` trailer of the last sync commit. Without one, use the main commit the
previous sync merged. `doc_areas.py` groups the range's changed files into areas, names the guides each area needs,
lists the current guides the range edited (feature authors often update docs in the same commit, and their wording
still needs the convention check) and the paths no rule covers. Read the commit messages and the STATUS.md entries of
the range too: they say what each change meant. When a module, package or plugin appears that no rule covers, add a
rule to `doc_areas.py` in the same change.

## 3. Resolve conflicts

Conflicts come from feature authors editing guides that the docs branch rewrote. Keep the docs branch's text and
structure, and fold in every fact the other side adds or changes, checked in the code. `docs/STATUS.md` is the usual
one: keep the opening "Current state" section and the handoff log's order (newest first), insert the new entries
verbatim at the top of the log, and refresh "Current state" from the entries and the code. Never resolve by taking a
side wholesale.

## 4. Update and verify the guides

Split the guides from step 2 into groups that share no file, and give each group a writer, an adversarial accuracy
verifier and a fixer (a Workflow when the session allows one, otherwise parallel subagents). Writers own only their
files and report what other files need; verifiers read only and must prove each finding in code with `path:line`;
fixers re-check each finding before applying it. Use `references/agent-brief.md` for the shared brief: it carries
the conventions, the test-pinned rules and the commands every agent needs. Then apply the cross-file follow-ups the
writers reported (or run one more round for them).

Two rules matter most, because skipping them is how docs drift again. The code is the authority: a commit message,
a fact sheet or another guide is only a map. And current guides describe what the system does now: no plan
numbers, phases, review talk, version history or migration advice; dated records (STATUS.md entries,
`docs/superpowers/**`, `docs/design/**`, `docs/benchmarks/**`, `*-verification.md`) are history and stay as written.

## 5. Mechanical checks

Run these after every writing round and before the build:

```bash
./gradlew sdkReference                                           # regenerate docs/sdk-reference.html
java -DsdkReference.listUndocumented=1 tools/sdk-reference/SdkReference.java . docs/sdk-reference.html   # must list nothing
python3 .claude/skills/docs-sync/scripts/linkcheck.py .           # links and anchors, as AppDocumentationTest checks them
python3 .claude/skills/docs-sync/scripts/conventions.py .         # glyph shortcuts, plan talk; add --pattern for this sync's stale facts
python3 .claude/skills/docs-sync/scripts/javadoc_only.py <source> --allow <files meant to change code>
```

`javadoc_only.py` proves the docs branch changed only comments in Java files; a docs sync changes no behaviour. Use
`conventions.py --pattern` for facts the range made stale (an old default, a removed API, an old version) so
nothing that still says them survives. Also run AGENTS.md's source-hygiene check on changed Java files.

## 6. Build check

Run `./gradlew check --continue` in the worktree, in the background (12 to 15 minutes), and read the test XML for
counts. Doc-reading tests and scanners fail on doc and Javadoc edits; `references/gotchas.md` lists them with their
rules, and the known timing flakes to re-run alone before treating them as real.

## 7. Commit, push and merge

Commit on the docs branch, ending the message with the trailer the next sync reads, then the repository's
attribution lines:

```
Docs: bring the guides up to main (<one-line summary of what the range changed>)

<what changed in the guides, briefly>

Docs-Synced-Through: <full sha of the main commit this sync covers>

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
```

Push the docs branch. Pushing `main` needs the user's approval for this run (an approval for an earlier run does not
carry over); with it, re-fetch and fast-forward only:

```bash
git fetch origin && git merge-base --is-ancestor origin/main HEAD && git push origin HEAD:main
```

If `origin/main` moved while the checks ran, merge it again, go back to step 2 for the new commits, and re-run the
checks before pushing. `gh` cannot open pull requests on this repository from the user's account; give a compare URL
(`https://github.com/an0nn30/Jasper/compare/main...<branch>`) when a pull request is wanted. Leave the user's own
checkout alone; tell them to `git pull`.

## 8. Report

Tell the user, briefly: the range covered, what changed in the guides, what the checks showed (test counts,
anything re-run), what was merged where, and anything not verified (GUI, Windows or Linux behaviour rests on the
code).
