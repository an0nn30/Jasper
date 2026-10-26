#!/usr/bin/env bash
# Explicit opt-in SDK preparation; no Gradle task calls this script.
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec "${DC_PYTHON:-python3}" "$script_dir/build_baseline.py" "$@"
