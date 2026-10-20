#!/usr/bin/env python3
"""Fetch the IntelliJ classic icons Jasper vendors from JetBrains/intellij-community at a pinned commit.

    python3 -I tools/icons/fetch-classic-icons.py

Reads jasper-ui's assets-classic.tsv (resource, upstream path, SHA-256), downloads every file with curl into memory,
checks each SHA-256 and only then writes them, byte for byte, so each stays the upstream file (with its JetBrains
copyright comment where upstream has one). Builds and runtime never run this; it reproduces the vendoring. Apache-2.0:
the shipped licence is icons/LICENSE.txt, never the repository's root LICENSE.txt.
"""
import hashlib
import pathlib
import subprocess
import sys

COMMIT = "8062c061ff33e22041d9f1844db5b8b8a6b5e4d7"  # tag idea/2026.2.3
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{COMMIT}/"
ICONS = pathlib.Path(__file__).resolve().parents[2] / "jasper-ui/src/main/resources/dev/jasper/ui/icons"


def main() -> int:
    rows = [line.split("\t") for line in (ICONS / "assets-classic.tsv").read_text(encoding="utf-8").splitlines()]
    staged = {}
    for resource, source, expected in rows:
        data = subprocess.run(["curl", "-fsSL", BASE + source], capture_output=True, check=True).stdout
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected:
            print(f"{source}: SHA-256 {actual}, the manifest records {expected}", file=sys.stderr)
            return 1
        staged[resource] = data
    for resource, data in staged.items():
        target = ICONS / resource
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print(f"{len(staged)} files verified and written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
