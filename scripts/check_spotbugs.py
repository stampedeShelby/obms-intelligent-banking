"""Parse a SpotBugs XML report, summarise it, and gate the build.

Why this is a Python script and not a shell one-liner: the shell version this
replaces used `grep | wc -l` under `bash -e` with `pipefail`. A grep that
matches nothing exits non-zero, so the pipeline failed and the script aborted
*precisely when the report was clean* - the build failed hardest when the code
was cleanest, and printed nothing to say so.

Severity model (SpotBugs "bug rank", 1 = worst, 20 = mildest):

    rank  1-4   scariest    -> treated as CRITICAL, fails the build
    rank  5-9   scary       -> reported, does not fail
    rank 10-14  troubling   -> reported
    rank 15-20  of concern  -> reported

The project's quality bar is zero critical findings, which is what the gate
enforces. Lower-severity findings are printed in full so they stay visible and
can be triaged rather than silently tolerated.
"""
from __future__ import annotations

import argparse
import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter

CRITICAL_MAX_RANK = 4

BUCKETS = (
    ("critical", 1, 4, "scariest"),
    ("scary", 5, 9, "scary"),
    ("troubling", 10, 14, "troubling"),
    ("concern", 15, 20, "of concern"),
)


def bucket_for(rank: int) -> str:
    for name, low, high, _ in BUCKETS:
        if low <= rank <= high:
            return name
    return "concern"


def describe(bug: ET.Element) -> str:
    """Build a one-line human description of a finding."""
    kind = bug.get("type", "UNKNOWN")
    rank = bug.get("rank", "?")
    message = bug.findtext("ShortMessage") or bug.findtext("LongMessage") or ""

    location = ""
    source = bug.find(".//SourceLine")
    if source is not None:
        cls = source.get("classname", "")
        start = source.get("start")
        location = f"{cls}:{start}" if start else cls

    return f"[rank {rank}] {kind} - {location} - {message}".strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", nargs="?", default="target/spotbugsXml.xml")
    args = parser.parse_args()

    if not os.path.isfile(args.report):
        print(f"SpotBugs report not found at {args.report}", file=sys.stderr)
        return 1

    root = ET.parse(args.report).getroot()
    bugs = root.findall("BugInstance")

    counts: Counter = Counter()
    by_bucket: dict = {name: [] for name, _, _, _ in BUCKETS}

    for bug in bugs:
        try:
            rank = int(bug.get("rank", "20"))
        except ValueError:
            rank = 20
        name = bucket_for(rank)
        counts[name] += 1
        by_bucket[name].append(describe(bug))

    critical = counts["critical"]
    total = len(bugs)

    print(f"SpotBugs analysed the backend and reported {total} finding(s).")
    for name, _, _, label in BUCKETS:
        print(f"  {label:<12} ({name:<9}): {counts[name]}")

    for name, _, _, label in BUCKETS:
        if by_bucket[name]:
            print(f"\n--- {label} ---")
            for line in by_bucket[name]:
                print(f"  {line}")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as handle:
            handle.write("### SpotBugs static analysis\n\n")
            handle.write("| Severity (bug rank) | Count |\n|---|---:|\n")
            for name, low, high, label in BUCKETS:
                handle.write(f"| {label} (rank {low}-{high}) | {counts[name]} |\n")
            handle.write(f"| **Total** | **{total}** |\n\n")
            verdict = "PASS" if critical == 0 else "FAIL"
            handle.write(
                f"**Critical bugs: {critical} - {verdict}** "
                f"(gate fails on any finding of rank 1-{CRITICAL_MAX_RANK})\n\n"
            )
            for name, _, _, label in BUCKETS:
                if by_bucket[name] and name != "critical":
                    handle.write(f"<details><summary>{label} ({counts[name]})</summary>\n\n")
                    for line in by_bucket[name]:
                        handle.write(f"- `{line}`\n")
                    handle.write("\n</details>\n\n")
            if by_bucket["critical"]:
                handle.write("#### Critical findings\n\n")
                for line in by_bucket["critical"]:
                    handle.write(f"- `{line}`\n")

    if critical:
        print(f"\nFAIL: {critical} critical finding(s) must be fixed.", file=sys.stderr)
        return 1

    print("\nPASS: zero critical findings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
