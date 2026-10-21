#!/usr/bin/env python3
"""Prove that the docs branch changed only comments in Java files, compared with a reference commit.

Usage: python3 javadoc_only.py <ref> [--allow path ...]

Compares every *.java file that differs between <ref> and the working tree after stripping comments and
whitespace (string and text-block literals are kept, so a changed message counts as code). Prints the files whose
code differs; --allow names files where a code change is intended (a doc test, a generator). Exits 1 when any
other file's code differs or a Java file was added or deleted.
"""
import re
import subprocess
import sys


def strip(src):
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(src[i:j]); i = j; continue
        if c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1]); i = j + 1; continue
        if src.startswith("//", i):
            j = src.find("\n", i); i = n if j < 0 else j; continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2); i = n if j < 0 else j + 2; out.append(" "); continue
        out.append(c); i += 1
    return re.sub(r"\s+", " ", "".join(out)).strip()


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    ref, allow = sys.argv[1], set()
    if "--allow" in sys.argv:
        allow = set(sys.argv[sys.argv.index("--allow") + 1:])
    status = subprocess.run(["git", "diff", "--name-status", ref, "--", "*.java"], capture_output=True, text=True, check=True).stdout
    added_or_deleted, code_changed, checked = [], [], 0
    for line in status.splitlines():
        kind, path = line.split("\t", 1)
        path = path.split("\t")[-1]
        if path in allow:
            continue
        if not kind.startswith("M"):
            added_or_deleted.append(line)
            continue
        checked += 1
        before = subprocess.run(["git", "show", f"{ref}:{path}"], capture_output=True, text=True, check=True).stdout
        with open(path, encoding="utf-8") as handle:
            if strip(before) != strip(handle.read()):
                code_changed.append(path)
    print(f"{checked} changed Java file(s) compared with {ref}")
    for line in added_or_deleted:
        print("added or deleted:", line)
    for path in code_changed:
        print("code differs:", path)
    if added_or_deleted or code_changed:
        sys.exit(1)
    print("comments only")


main()
