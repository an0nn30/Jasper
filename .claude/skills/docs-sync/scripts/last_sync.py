#!/usr/bin/env python3
"""Print the base of the next docs sync: the newest sync commit reachable from ref.

Usage: python3 last_sync.py [ref]   (ref defaults to origin/main)

Each docs sync commit ends with a trailer `Docs-Synced-Through: <commit>` naming the main commit it covered. Once the
sync is merged, everything up to the sync commit itself is documented, so this prints that commit's full sha (and,
on stderr, the trailer's value). Pass it to doc_areas.py as the base. Exits 1 with a hint when no sync commit is
reachable (find the main commit the last sync merged by hand then).
"""
import re
import subprocess
import sys

ref = sys.argv[1] if len(sys.argv) > 1 else "origin/main"
log = subprocess.run(["git", "log", ref, "--grep=^Docs-Synced-Through: ", "--format=%H%n%B%n--end--", "-n", "1"],
                     capture_output=True, text=True, check=True).stdout
if not log.strip():
    sys.exit(f"no Docs-Synced-Through trailer reachable from {ref}; find the main commit the last docs sync merged by hand")
sync_commit = log.split("\n", 1)[0].strip()
match = re.search(r"^Docs-Synced-Through: ([0-9a-f]{7,40})\s*$", log, re.M)
print(f"last sync {sync_commit[:9]} covered main through {match.group(1)[:9] if match else '?'}", file=sys.stderr)
print(sync_commit)
