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


MESSY_HEADER = ["Txn Ref", "Customer Email", "Acct No", "Amount (INR)", "Settlement Dt", "Status"]


def generate(records: int, prefix: str, output_dir: Path, day: str, layout: str) -> dict[str, object]:
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
        "missing_in_gateway": 0,
        "suggested_pairs": 0,
    }
    messy = layout == "messy"
    # The messy layout writes dates as dd/MM/yyyy, like many Indian gateway exports.
    gateway_day = f"{day[8:10]}/{day[5:7]}/{day[0:4]}" if messy else day

    with gateway_path.open("w", newline="", encoding="utf-8") as gateway_file, ledger_path.open(
        "w", newline="", encoding="utf-8"
    ) as ledger_file:
        plain_writer = csv.writer(gateway_file)

        class GatewayWriter:
            """Writes standard rows, or the same data in the messy 6-column layout."""

            @staticmethod
            def writerow(row):
                if not messy or row[0] == "transaction_id":
                    plain_writer.writerow(MESSY_HEADER if messy and row[0] == "transaction_id" else row)
                    return
                tx, account, amount, date = row
                date = gateway_day if date == day else date
                plain_writer.writerow([tx, f"customer{len(tx)}@example.in", account, amount, date, "SUCCESS"])

        gateway = GatewayWriter()
        ledger = csv.writer(ledger_file)
        gateway.writerow(["transaction_id", "account_number", "amount", "transaction_date"])
        ledger.writerow(
            ["transaction_id", "account_number", "amount", "transaction_date", "description"]
        )

        for index in range(records):
            category = index % 100
            account = f"ACC-{index % 10000:04d}"
            # 49999, not 50000: with 50000 (a multiple of the 10000 accounts), rows 50000 apart had the
            # same account AND amount, so typo rows fit two ledger rows and became ambiguous pairs.
            amount = Decimal("10.00") + Decimal(index % 49999) / Decimal("100")
            tx_id = transaction_id(prefix, index)

            if category < 80:
                gateway.writerow([tx_id, account, amount, day])
                ledger.writerow([tx_id, account, amount, day, "Benchmark match"])
                expected["matched"] += 1
            elif category < 90:
                gateway.writerow([tx_id, account, amount, day])
                ledger.writerow(
                    [tx_id, account, amount + Decimal("1.00"), day, "Benchmark mismatch"]
                )
                expected["amount_mismatch"] += 1
            elif category < 96:
                gateway.writerow([tx_id, account, amount, day])
                expected["missing_in_ledger"] += 1
            elif category == 96:
                # Same payment, but the gateway's ID has one extra character: a typo.
                # Missing in the ledger AND missing in the gateway, and suggested as a pair.
                gateway.writerow([tx_id + "X", account, amount, day])
                ledger.writerow([tx_id, account, amount, day, "Benchmark typo pair"])
                expected["missing_in_ledger"] += 1
                expected["missing_in_gateway"] += 1
                expected["suggested_pairs"] += 1
            elif category == 97:
                gateway.writerow([tx_id, account, "not-a-number", day])
                expected["invalid"] += 1
            elif category == 98:
                gateway.writerow([tx_id, account, amount, "not-a-date"])
                expected["invalid"] += 1
            else:
                # Repeats the ID of category 95 (a plain row) from 4 lines earlier.
                duplicate_id = transaction_id(prefix, index - 4)
                gateway.writerow([duplicate_id, account, amount, day])
                expected["duplicate"] += 1
                # The ledger has this row's own ID, but the gateway file never mentions it.
                ledger.writerow([tx_id, account, amount, day, "Benchmark ledger only"])
                expected["missing_in_gateway"] += 1

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
    parser.add_argument(
        "--layout",
        choices=["standard", "messy"],
        default="standard",
        help="messy = 6 columns with other names and dd/MM/yyyy dates (needs a gateway format)",
    )
    parser.add_argument(
        "--date",
        default="2026-08-01",
        help="transaction date for every row; use a different date per run so earlier runs' "
        "ledger rows don't count as missing from this file",
    )
    args = parser.parse_args()
    if args.records < 1:
        parser.error("--records must be positive")
    if len(args.prefix) > 40:
        parser.error("--prefix must be at most 40 characters")
    print(json.dumps(generate(args.records, args.prefix, args.output_dir, args.date, args.layout)))


if __name__ == "__main__":
    main()
