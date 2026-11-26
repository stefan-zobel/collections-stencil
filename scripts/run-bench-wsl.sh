#!/usr/bin/env bash
# Runs the interleaved benchmarks inside WSL (Linux JDK).
#   bash scripts/run-bench-wsl.sh <out-dir> [InterleavedRunner args...]
# The jar is copied to the Linux file system first; forking JVMs from /mnt/<drive> is slow.
# Results are written to the Linux file system and copied to <out-dir> at the end.
# Override the JDK with JAVA=/path/to/bin/java.
set -euo pipefail

OUT_DIR="${1:?usage: run-bench-wsl.sh <out-dir> [runner args...]}"
shift
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="${JAVA:-java}"
WORK="$HOME/stencil-bench"

mkdir -p "$WORK" "$OUT_DIR"
cp "$ROOT/bench/target/benchmarks.jar" "$WORK/benchmarks.jar"
rm -f "$WORK/wsl.csv" "$WORK/wsl.log"

{
  echo "CPU:    $(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2- | sed 's/^ //')"
  echo "Kernel: $(uname -r)"
  echo "nproc:  $(nproc)"
  echo "Memory: $(free -g | awk '/^Mem:/ {print $2 " GiB"}')"
  echo "JDK:    $("$JAVA" -version 2>&1 | tr '\n' ' ')"
  echo "Start:  $(date -Iseconds)"
} > "$OUT_DIR/wsl-env.txt"

echo "WSL benchmark -> $OUT_DIR (progress: $WORK/wsl.log)"
rc=0
# --add-opens lets the footprint measurement walk the nodes of java.util.TreeMap and of the
# concurrent maps (nodes of ConcurrentHashMap, locks of the segments).
"$JAVA" -Duser.language=en -Duser.country=US --sun-misc-unsafe-memory-access=allow \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
  -cp "$WORK/benchmarks.jar" net.collections.stencil.bench.InterleavedRunner \
  --out="$WORK" --label=wsl "$@" > /dev/null || rc=$?

cp "$WORK/wsl.csv" "$WORK/wsl.log" "$OUT_DIR/" 2>/dev/null || true
echo "End:    $(date -Iseconds)  exit=$rc" >> "$OUT_DIR/wsl-env.txt"
exit $rc
