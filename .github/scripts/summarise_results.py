#!/usr/bin/env python3
"""Write a Markdown summary of the run from Allure's raw results.

Gating tests and findings are reported separately. Findings are tests that
assert what a correct service would do, so they fail by design; each failure
message is the defect report, and it is listed here so a reviewer sees the
defects on the run page without downloading anything. A run with no findings,
such as the framework self-tests, gets no findings row or section.

Usage: summarise_results.py RESULTS_DIR >> "$GITHUB_STEP_SUMMARY"
"""
import collections
import glob
import json
import sys


def is_finding(result):
    return any(label.get("name") == "tag" and label.get("value") == "finding"
               for label in result.get("labels", []))


def first_line(result, limit=300):
    message = (result.get("statusDetails") or {}).get("message") or ""
    line = message.strip().splitlines()[0].strip() if message.strip() else ""
    if line.startswith("[") and line.endswith("]"):  # AssertJ wraps .as() descriptions
        line = line[1:-1]
    return line if len(line) <= limit else line[:limit] + "…"


def main(results_dir):
    results = [json.load(open(f)) for f in glob.glob(f"{results_dir}/*-result.json")]
    if not results:
        print("## Test results\n\nNo results were produced.")
        return
    gating = [r for r in results if not is_finding(r)]
    findings = [r for r in results if is_finding(r)]
    count = lambda rs: collections.Counter(r["status"] for r in rs)
    g, f = count(gating), count(findings)

    print("## Test results\n")
    print("| Run | Total | Passed | Failed | Broken | Retried attempts |")
    print("| --- | ---: | ---: | ---: | ---: | ---: |")
    print(f"| Gating — must pass | {len(gating)} | {g['passed']} | {g['failed']} | {g['broken']} | {g['skipped']} |")
    if findings:
        print(f"| Findings — fail by design | {len(findings)} | {f['passed']} | {f['failed']} | {f['broken']} | {f['skipped']} |")

    broken_gate = [r for r in gating if r["status"] in ("failed", "broken")]
    if broken_gate:
        print("\n### Gating failures\n")
        for r in sorted(broken_gate, key=lambda r: r["name"]):
            print(f"- **{r['name']}** — {first_line(r)}")

    if not findings:
        return
    print("\n### Findings: defects in the systems under test\n")
    for r in sorted(findings, key=lambda r: r["name"]):
        mark = "❌" if r["status"] in ("failed", "broken") else "✅ now passing — the defect may be fixed"
        print(f"- {mark} **{r['name']}**  \n  `{first_line(r)}`")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "target/allure-results")
