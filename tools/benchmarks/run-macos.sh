#!/bin/sh
# Explicit native invocation. Caller first checks the no-game/no-VM prerequisite.
set -eu
if [ "$#" -lt 4 ]; then
    echo "Usage: $0 /absolute/Jasper.app throughput|memory REVISION /absolute/result.json [benchmark options...]" >&2
    exit 2
fi
image=$1
mode=$2
revision=$3
output=$4
shift 4
case "$image" in /*) ;; *) echo 'Use an absolute package path' >&2; exit 2;; esac
case "$output" in /*) ;; *) echo 'Use an absolute output path' >&2; exit 2;; esac
case "$mode" in throughput) entry=Bench;; memory) entry=MemoryBench;; *) echo 'Expected throughput or memory' >&2; exit 2;; esac
exec "$image/Contents/runtime/Contents/Home/bin/java" --enable-native-access=ALL-UNNAMED \
    -Dapple.awt.application.name='Jasper benchmark' -cp "$image/Contents/app/*" \
    "dev.jasper.app.$entry" --revision "$revision" --output "$output" "$@"
