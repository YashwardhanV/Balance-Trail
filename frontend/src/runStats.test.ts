import { describe, expect, it } from 'vitest'
import { displayId, formatProblems, issueCount, sourceLabel } from './runStats'
import type { Discrepancy, ReconciliationRun } from './types'

const run = {
  matchedCount: 100,
  amountMismatchCount: 1,
  accountMismatchCount: 2,
  dateMismatchCount: 3,
  missingCount: 4,
  missingInGatewayCount: 5,
  invalidCount: 6,
  duplicateCount: 7,
} as ReconciliationRun

const ledgerOnly = {
  lineNumber: null,
  transactionId: null,
  ledgerTransactionId: 'TXN-9',
} as Discrepancy

describe('issueCount', () => {
  it('counts every non-matched outcome, including ledger-only rows', () => {
    expect(issueCount(run)).toBe(1 + 2 + 3 + 4 + 5 + 6 + 7)
  })
})

describe('discrepancy labels', () => {
  it('shows the CSV line for rows from the file', () => {
    expect(sourceLabel({ ...ledgerOnly, lineNumber: 7 })).toBe('CSV line 7')
  })

  it('marks ledger-only rows and shows their ledger ID', () => {
    expect(sourceLabel(ledgerOnly)).toBe('Ledger only, not in file')
    expect(displayId(ledgerOnly)).toBe('TXN-9')
  })

  it('falls back for rows that could not be parsed at all', () => {
    expect(displayId({ ...ledgerOnly, ledgerTransactionId: null })).toBe('Unparseable row')
  })
})

describe('formatProblems', () => {
  const complete = {
    name: 'Settlement report',
    transactionIdColumn: 'Txn Ref',
    accountNumberColumn: 'Acct No',
    amountColumn: 'Amount (INR)',
    transactionDateColumn: 'Settlement Dt',
    dateFormat: 'dd/MM/yyyy',
  }

  it('accepts a complete mapping', () => {
    expect(formatProblems(complete)).toEqual([])
  })

  it('lists every missing piece', () => {
    expect(formatProblems({ ...complete, name: ' ', amountColumn: '', dateFormat: '' })).toEqual([
      'Give the format a name',
      'Pick the amount column',
      'Pick the date format',
    ])
  })

  it('refuses one column for two fields', () => {
    expect(formatProblems({ ...complete, accountNumberColumn: 'Txn Ref' })).toContain(
      'Each column can be used for only one field',
    )
  })
})
