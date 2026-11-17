import type { Discrepancy, FormatForm, ReconciliationRun } from './types'

/** Every result that is not MATCHED, including ledger rows missing from the file. */
export function issueCount(run: ReconciliationRun): number {
  return (
    run.amountMismatchCount +
    run.accountMismatchCount +
    run.dateMismatchCount +
    run.missingCount +
    run.missingInGatewayCount +
    run.invalidCount +
    run.duplicateCount
  )
}

/** Where a discrepancy came from, for the first column of the table. */
export function sourceLabel(item: Discrepancy): string {
  return item.lineNumber === null ? 'Ledger only, not in file' : `CSV line ${item.lineNumber}`
}

/** The transaction ID to show: the gateway's, or the ledger's for ledger-only rows. */
export function displayId(item: Discrepancy): string {
  return item.transactionId ?? item.ledgerTransactionId ?? 'Unparseable row'
}

/**
 * What still stops a format from being saved. The server checks all of this again; this only
 * keeps the Save button honest.
 */
export function formatProblems(form: FormatForm): string[] {
  const problems: string[] = []
  if (!form.name.trim()) problems.push('Give the format a name')
  const columns = [
    ['transaction ID', form.transactionIdColumn],
    ['account number', form.accountNumberColumn],
    ['amount', form.amountColumn],
    ['transaction date', form.transactionDateColumn],
  ]
  for (const [field, column] of columns) {
    if (!column) problems.push(`Pick the ${field} column`)
  }
  const chosen = columns.map(([, column]) => column).filter(Boolean)
  if (new Set(chosen).size !== chosen.length) problems.push('Each column can be used for only one field')
  if (!form.dateFormat) problems.push('Pick the date format')
  return problems
}
