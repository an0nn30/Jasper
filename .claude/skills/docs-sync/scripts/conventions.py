#!/usr/bin/env python3
"""Flag text in the current guides that breaks the documentation conventions.

Usage: python3 conventions.py [repo] [--pattern NAME=REGEX ...]

Scans the current guides (README.md, AGENTS.md, docs/*.md, module and packaging READMEs, the SDK reference's guide
pages and config.example.toml) and skips the dated records (docs/STATUS.md, *-verification.md,
docs/documentation-audit.md, docs/terminal-readiness.md, docs/superpowers, docs/design, docs/benchmarks) and the
generated docs/editor-vim-reference.md. Prints each hit as file:line; exits 1 when any check finds one. Add
one-off checks for a sync with --pattern, for example --pattern 'old-default=Slate Cobalt \\(the default'.
"""
import pathlib
import re
import sys

CHECKS = {
    # Shortcuts are written with words, so screen readers and search work: Cmd, Option, Ctrl, Shift.
    "glyph": r"[⌘⌥⌃⇧]",
    # Current guides describe the system as it is; plan numbers, phases and review talk belong in the records.
    "plan-talk": r"\b[Pp]hase [0-9]|\b[Pp]lan [0-9]+[a-z]?\b|\bTask ?[0-9]+[a-z]?\b|\bRulings?\b|review round",
    # The SDK reference is generated; a guide should never tell readers to hand-edit it.
    "edit-generated": r"edit (the )?(docs/)?sdk-reference\.html",
}
RECORDS = re.compile(r"(^|/)(STATUS\.md|documentation-audit\.md|terminal-readiness\.md|editor-vim-reference\.md|[^/]*-verification\.md)$")


def guides(root):
    candidates = [root / "README.md", root / "AGENTS.md", root / "config.example.toml"]
    candidates += sorted((root / "docs").glob("*.md")) + sorted(root.glob("jasper-*/README.md"))
    candidates += sorted(root.glob("packaging/**/README.md")) + sorted(root.glob("tools/**/README.md"))
    candidates += sorted((root / "tools/sdk-reference/guide").glob("*.html")) + sorted(root.glob("docs/examples/**/README.md"))
    for path in candidates:
        rel = path.relative_to(root).as_posix()
        if path.is_file() and not RECORDS.search(rel) and "/build/" not in rel and "seed-support" not in rel:
            yield path


def main():
    args = sys.argv[1:]
    root = pathlib.Path(args.pop(0) if args and not args[0].startswith("--") else ".").resolve()
    checks = dict(CHECKS)
    while args:
        flag = args.pop(0)
        if flag == "--pattern" and args:
            name, _, regex = args.pop(0).partition("=")
            checks[name] = regex
    hits = 0
    for name, regex in checks.items():
        pattern = re.compile(regex)
        for path in guides(root):
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if pattern.search(line):
                    hits += 1
                    print(f"[{name}] {path.relative_to(root)}:{number}: {line.strip()[:160]}")
    if hits:
        sys.exit(1)
    print("conventions ok")


main()
