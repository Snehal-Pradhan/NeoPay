#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MAX_LINES=300
fail=0

while IFS= read -r file; do
    lines=$(wc -l < "$file" | tr -d ' ')
    if [ "$lines" -gt "$MAX_LINES" ]; then
        echo "OVER BUDGET: $file ($lines > $MAX_LINES)"
        fail=1
    fi
done < <(find . -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*')

if [ "$fail" -eq 0 ]; then
    echo "complexity budget: OK (all files <= ${MAX_LINES} lines)"
fi

exit "$fail"
