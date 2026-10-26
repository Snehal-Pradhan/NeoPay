#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for f in .run/*.pid; do
  [ -e "$f" ] || continue
  pid="$(cat "$f")"
  kill "$pid" 2>/dev/null || true
  rm -f "$f"
done
echo "application processes stopped"
