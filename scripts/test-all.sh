#!/usr/bin/env bash
# Runs the independent Maven projects, fast by default.
#
#   ./scripts/test-all.sh                     # unit suites, 6 projects in parallel
#   ./scripts/test-all.sh --full              # every suite, run serially
#   ./scripts/test-all.sh --only payment-service
#   JOBS=3 ./scripts/test-all.sh              # cap parallelism
#
# Integration suites (@Tag("integration"), Testcontainers) are excluded in fast
# mode. They do not parallelise well: six concurrent Maven builds plus Docker
# measured 17 minutes versus roughly four serially, so --full is serial on
# purpose. Use ./scripts/verify-all.sh for the full gate (tests + static
# analysis + coverage check).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODULES=(common-lib merchant-service payment-service vault-service
         operations-service api-gateway-service discovery-service config-service)

FULL=0
ONLY=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --full) FULL=1 ;;
        --only) shift; ONLY="${1:-}" ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

if [[ -n "$ONLY" ]]; then
    MODULES=("$ONLY")
fi

ARGS=()
if [[ $FULL -eq 0 ]]; then
    ARGS+=("-Dgroups=!integration")
fi

mkdir -p target/test-all
rm -f target/test-all/*.exit target/test-all/*.time

echo "== installing common-lib =="
if ! mvn -q -f common-lib/pom.xml -DskipTests install > target/test-all-install.log 2>&1; then
    echo "common-lib install FAILED (see target/test-all-install.log)"
    exit 1
fi

run_module() {
    local module="$1"
    local log="target/test-all/${module}.log"
    local start end
    rm -rf "$module/target/surefire-reports"
    start=$(date +%s)
    mvn -q -f "$module/pom.xml" test ${ARGS[@]+"${ARGS[@]}"} > "$log" 2>&1
    local rc=$?
    end=$(date +%s)
    echo "$rc" > "${log}.exit"
    echo "$((end - start))" > "${log}.time"
}

if [[ $FULL -eq 1 ]]; then
    echo "== full suite, serial =="
    for module in "${MODULES[@]}"; do
        [[ -f "$module/pom.xml" ]] || continue
        echo "running $module"
        run_module "$module"
    done
else
    JOBS="${JOBS:-4}"
    echo "== unit suite, parallel up to $JOBS =="
    declare -a PIDS=() NAMES=()
    for module in "${MODULES[@]}"; do
        [[ -f "$module/pom.xml" ]] || continue
        while [[ ${#PIDS[@]} -ge $JOBS ]]; do
            wait "${PIDS[0]}"
            PIDS=("${PIDS[@]:1}")
        done
        run_module "$module" &
        PIDS+=($!)
        NAMES+=("$module")
        echo "started $module"
    done
    for pid in "${PIDS[@]+"${PIDS[@]}"}"; do
        wait "$pid"
    done
fi

printf '\n%-24s %-8s %-8s %-8s %s\n' "PROJECT" "STATUS" "TESTS" "SECONDS" "LOG"
FAILED=0
for module in "${MODULES[@]}"; do
    [[ -f "$module/pom.xml" ]] || continue
    log="target/test-all/${module}.log"
    status="PASS"
    rc=$(cat "${log}.exit" 2>/dev/null || echo 1)
    if [[ "$rc" != "0" ]]; then
        status="FAIL"
        FAILED=1
    fi
    tests=$(grep -hE "^Tests run:" "$module"/target/surefire-reports/*.txt 2>/dev/null \
        | awk -F'[,: ]+' '{t+=$3} END {print t+0}')
    secs=$(cat "${log}.time" 2>/dev/null || echo "?")
    printf '%-24s %-8s %-8s %-8s %s\n' "$module" "$status" "${tests:-0}" "$secs" "$log"
done

if [[ $FAILED -ne 0 ]]; then
    echo
    echo "one or more projects failed; inspect the logs above"
    exit 1
fi

echo
echo "all projects passed"
