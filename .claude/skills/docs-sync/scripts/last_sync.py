#!/usr/bin/env python3
"""Print the main commit the guides were last brought up to.

Usage: python3 last_sync.py [ref]   (ref defaults to origin/main)

Each docs sync commit ends with a trailer `Docs-Synced-Through: <commit>` naming the main commit it covered. This
prints the newest such commit reachable from ref, or exits 1 with a hint when there is none (pass the base to
doc_areas.py by hand then: the main commit the last sync merged).
"""
import re
import subprocess
import sys

ref = sys.argv[1] if len(sys.argv) > 1 else "origin/main"
log = subprocess.run(["git", "log", ref, "--grep=^Docs-Synced-Through: ", "--format=%H%n%B%n--end--", "-n", "1"],
                     capture_output=True, text=True, check=True).stdout
match = re.search(r"^Docs-Synced-Through: ([0-9a-f]{7,40})\s*$", log, re.M)
if not match:
    sys.exit(f"no Docs-Synced-Through trailer reachable from {ref}; find the main commit the last docs sync merged by hand")
synced = match.group(1)
full = subprocess.run(["git", "rev-parse", "--verify", f"{synced}^{{commit}}"], capture_output=True, text=True)
if full.returncode != 0:
    sys.exit(f"the trailer names {synced}, which this clone does not have; fetch first")
print(full.stdout.strip())
