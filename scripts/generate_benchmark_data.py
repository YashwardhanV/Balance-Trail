#!/usr/bin/env python3
"""Generate deterministic BalanceTrail gateway and ledger CSV benchmark data."""

from __future__ import annotations

import argparse
import csv
import json
from decimal import Decimal
from pathlib import Path


def transaction_id(prefix: str, index: int) -> str:
    return f"{prefix}-{index:07d}"


def generate(records: int, prefix: str, output_dir: Path) -> dict[str, object]:
    output_dir.mkdir(parents=True, exist_ok=True)
    gateway_path = output_dir / f"gateway-{prefix}.csv"
    ledger_path = output_dir / f"ledger-{prefix}.csv"
    expected = {
        "records": records,
        "matched": 0,
        "amount_mismatch": 0,
        "missing_in_ledger": 0,
        "invalid": 0,
        "duplicate": 0,
    }

    with gateway_path.open("w", newline="", encoding="utf-8") as gateway_file, ledger_path.open(
        "w", newline="", encoding="utf-8"
    ) as ledger_file:
        gateway = csv.writer(gateway_file)
        ledger = csv.writer(ledger_file)
        gateway.writerow(["transaction_id", "account_number", "amount", "transaction_date"])
        ledger.writerow(
            ["transaction_ref", "account_number", "amount", "transaction_date", "description"]
        )

        for index in range(records):
            category = index % 100
            account = f"ACC-{index % 10000:04d}"
            amount = Decimal("10.00") + Decimal(index % 50000) / Decimal("100")
            tx_id = transaction_id(prefix, index)

            if category < 80:
                gateway.writerow([tx_id, account, amount, "2026-08-01"])
                ledger.writerow([tx_id, account, amount, "2026-08-01", "Benchmark match"])
                expected["matched"] += 1
            elif category < 90:
                gateway.writerow([tx_id, account, amount, "2026-08-01"])
                ledger.writerow(
                    [tx_id, account, amount + Decimal("1.00"), "2026-08-01", "Benchmark mismatch"]
                )
                expected["amount_mismatch"] += 1
            elif category < 97:
                gateway.writerow([tx_id, account, amount, "2026-08-01"])
                expected["missing_in_ledger"] += 1
            elif category == 97:
                gateway.writerow([tx_id, account, "not-a-number", "2026-08-01"])
                expected["invalid"] += 1
            elif category == 98:
                gateway.writerow([tx_id, account, amount, "not-a-date"])
                expected["invalid"] += 1
            else:
                duplicate_id = transaction_id(prefix, index - 3)
                gateway.writerow([duplicate_id, account, amount, "2026-08-01"])
                expected["duplicate"] += 1

    manifest_path = output_dir / f"expected-{prefix}.json"
    manifest_path.write_text(json.dumps(expected, indent=2) + "\n", encoding="utf-8")
    return {
        "gateway": str(gateway_path),
        "ledger": str(ledger_path),
        "expected": str(manifest_path),
        **expected,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--records", type=int, required=True)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--output-dir", type=Path, default=Path("benchmark-data"))
    args = parser.parse_args()
    if args.records < 1:
        parser.error("--records must be positive")
    if len(args.prefix) > 40:
        parser.error("--prefix must be at most 40 characters")
    print(json.dumps(generate(args.records, args.prefix, args.output_dir)))


if __name__ == "__main__":
    main()
