#!/usr/bin/env python3
"""Static integrity guard for the one project feedback index; it never reads product data."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
ledger = root / "docs" / "PRODUCT_FEEDBACK_DECISION_LEDGER.md"
rows = [line for line in ledger.read_text(encoding="utf-8").splitlines() if line.startswith("| FB-")]
ids: set[str] = set()
errors: list[str] = []
for index, row in enumerate(rows, 1):
    cells = [cell.strip() for cell in row.strip("|").split("|")]
    if len(cells) != 11:
        errors.append(f"row {index}: expected 11 cells")
        continue
    feedback_id, _, _, _, contract, platform, phase, status, *_ = cells
    if not re.fullmatch(r"FB-P6-\d{3}", feedback_id) or feedback_id in ids:
        errors.append(f"row {index}: invalid or duplicate ID {feedback_id}")
    ids.add(feedback_id)
    contracts = [item.strip() for item in re.split(r"[；;]", contract) if item.strip()]
    if not contracts or any(not item.startswith("docs/") or not (root / item).is_file() for item in contracts):
        errors.append(f"row {index}: missing contract {contract}")
    if not platform or not phase or not status:
        errors.append(f"row {index}: platform, phase, status are required")
if not rows:
    errors.append("no feedback rows")
forbidden = re.compile(r"(?i)(api[ _-]?key|authorization|content://|file://|/Users/|token=)")
if forbidden.search(ledger.read_text(encoding="utf-8")):
    errors.append("ledger contains forbidden sensitive-looking content")
if errors:
    print("FEEDBACK_LEDGER_CHECK FAILED")
    print("\n".join(errors))
    sys.exit(1)
print(f"FEEDBACK_LEDGER_CHECK OK rows={len(rows)}")
