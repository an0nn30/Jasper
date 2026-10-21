#!/usr/bin/env python3
"""Check Markdown links and heading anchors the way AppDocumentationTest does, over every Markdown file in the repo.

Usage: python3 linkcheck.py [repo] [file.md ...]

With no files, checks every *.md outside build/, .git/, .claude/, .worktrees/, .superpowers/ and test resources.
For named files it also reports links from other files to anchors in them that no longer exist, so renaming a
heading cannot silently break an inbound link. Exits 1 when it finds a problem.
"""
import pathlib
import re
import sys
import urllib.parse

args = sys.argv[1:]
root = pathlib.Path(args.pop(0) if args and pathlib.Path(args[0]).is_dir() else ".").resolve()
SKIP_PARTS = {"build", ".git", ".claude", ".worktrees", ".superpowers", "node_modules", ".gradle"}


def tracked_md():
    for path in root.rglob("*.md"):
        rel = path.relative_to(root)
        if SKIP_PARTS & set(rel.parts):
            continue
        if "/src/test/resources/" in "/" + rel.as_posix():  # test fixtures hold deliberately broken links
            continue
        yield path


def without_code(text):
    # AppDocumentationTest.withoutCode: a fence closes only on a line of at least as many backticks.
    return re.sub(r"(?ms)^[ \t]*(`{3,})[^\n]*$.*?^[ \t]*\1`*[ \t]*$", "", text)


def anchors(path):
    found = set()
    for line in path.read_text(encoding="utf-8").split("\n"):
        line = line.rstrip("\r")
        if line.startswith("#"):  # the test counts every line starting with '#', even inside code fences
            anchor = re.sub(r"^#+ +", "", line).lower()
            found.add(re.sub(r"[^\w \-]", "", anchor).replace(" ", "-"))
    return found


LINK = re.compile(r"\[[^\]]*]\(([^)]+)\)")
scan = list(tracked_md())
named = [(root / f).resolve() for f in args] or scan
cache, problems, inbound = {}, [], {}
for guide in scan:
    for match in LINK.finditer(without_code(guide.read_text(encoding="utf-8"))):
        link = match.group(1)
        if re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", link):
            continue
        target_part, _, fragment = link.partition("#")
        target = guide if not target_part else (guide.parent / urllib.parse.unquote(target_part.replace("+", "%2B"))).resolve()
        if fragment:
            inbound.setdefault(target, set()).add((fragment, guide.relative_to(root).as_posix()))
        if guide not in named:
            continue
        if not target.exists():
            problems.append(f"{guide.relative_to(root)}: link to missing {link}")
        elif fragment and target.suffix == ".md":
            cache.setdefault(target, anchors(target))
            if fragment not in cache[target]:
                problems.append(f"{guide.relative_to(root)}: link {link} -> no heading makes anchor #{fragment}")
for target, refs in inbound.items():
    if target in named and target.exists() and target.suffix == ".md":
        have = anchors(target)
        problems += [f"{src}: links to {target.relative_to(root)}#{a}, which no heading makes" for a, src in sorted(refs) if a not in have]
if problems:
    print("\n".join(sorted(set(problems))))
    sys.exit(1)
print(f"links ok ({len(named)} file(s) checked)")
