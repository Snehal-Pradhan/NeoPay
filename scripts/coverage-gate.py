#!/usr/bin/env python3
"""Layer-tiered JaCoCo coverage gate.

JaCoCo's own `check` goal accepts a single bundle-level floor but silently
ignores `includes`/`excludes` on BUNDLE rules, so per-layer thresholds cannot
be expressed in the POM. This script reads each module's `jacoco.csv` and
enforces the tier table from `coverage-tiers.json`.

Usage:
    python3 scripts/coverage-gate.py                 # every module in the config
    python3 scripts/coverage-gate.py common-lib      # one module

Exit code 0 when every configured floor is met, 1 otherwise.
Thresholds are ratchets: raise them, never lower them.
"""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "coverage-tiers.json"


def load_config() -> dict:
    if not CONFIG.exists():
        sys.exit(f"coverage-gate: missing config {CONFIG}")
    return json.loads(CONFIG.read_text())


def read_rows(csv_path: Path) -> list[dict]:
    if not csv_path.exists():
        sys.exit(f"coverage-gate: missing {csv_path}; run `mvn test jacoco:report` first")
    with csv_path.open(newline="") as handle:
        return list(csv.DictReader(handle))


def package_name(row: dict) -> str:
    """Last segment of the package, e.g. com.snehal.neopay.common.money -> money."""
    return row["PACKAGE"].rsplit(".", 1)[-1]


def ratio(covered: int, missed: int) -> float | None:
    total = covered + missed
    return None if total == 0 else covered / total


def aggregate(rows: list[dict]) -> tuple[int, int, int, int]:
    line_covered = line_missed = branch_covered = branch_missed = 0
    for row in rows:
        line_covered += int(row["LINE_COVERED"])
        line_missed += int(row["LINE_MISSED"])
        branch_covered += int(row["BRANCH_COVERED"])
        branch_missed += int(row["BRANCH_MISSED"])
    return line_covered, line_missed, branch_covered, branch_missed


def check(name: str, rows: list[dict], floor_line: float, floor_branch: float) -> list[str]:
    lc, lm, bc, bm = aggregate(rows)
    line = ratio(lc, lm)
    branch = ratio(bc, bm)
    failures: list[str] = []

    line_text = "n/a" if line is None else f"{line * 100:5.1f}%"
    branch_text = "n/a" if branch is None else f"{branch * 100:5.1f}%"
    status = "ok"
    if line is not None and line < floor_line:
        status = "FAIL"
        failures.append(
            f"{name}: line {line * 100:.1f}% < required {floor_line * 100:.1f}%"
        )
    if branch is not None and branch < floor_branch:
        status = "FAIL"
        failures.append(
            f"{name}: branch {branch * 100:.1f}% < required {floor_branch * 100:.1f}%"
        )
    print(
        f"  {name:16s} line {line_text:>7s} (>= {floor_line * 100:4.1f}%)"
        f"  branch {branch_text:>7s} (>= {floor_branch * 100:4.1f}%)  {status}"
    )
    return failures


def main(argv: list[str]) -> int:
    config = load_config()
    requested = argv[1:] or list(config)
    unknown = [m for m in requested if m not in config]
    if unknown:
        sys.exit(f"coverage-gate: no thresholds configured for {', '.join(unknown)}")

    failures: list[str] = []
    for module in requested:
        csv_path = ROOT / module / "target" / "site" / "jacoco" / "jacoco.csv"
        rows = read_rows(csv_path)
        spec = config[module]
        print(f"[coverage-gate] {module}")
        failures += check("bundle", rows, spec["bundle"]["line"], spec["bundle"]["branch"])
        for layer, rule in spec["layers"].items():
            packages = {p for p in rule["packages"]}
            layer_rows = [r for r in rows if package_name(r) in packages]
            if not layer_rows:
                failures.append(f"{layer}: configured packages not present in report")
                continue
            failures += check(layer, layer_rows, rule["line"], rule["branch"])
        print()

    if failures:
        print("[coverage-gate] FAILED")
        for failure in failures:
            print(f"  - {failure}")
        print("Fix the tests or the code. Never lower a threshold to pass the build.")
        return 1
    print("[coverage-gate] all thresholds met")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
