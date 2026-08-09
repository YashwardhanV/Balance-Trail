import { StatusBadge } from './StatusBadge'
import type { Discrepancy, ReconciliationRun } from '../types'

interface DiscrepancyTableProps {
  selectedRun: ReconciliationRun | null
  discrepancies: Discrepancy[]
  page: number
  totalPages: number
  loading: boolean
  onPageChange: (page: number) => void
}

const money = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

function displayAmount(value: number | null) {
  return value === null ? '\u2014' : money.format(value)
}

export function DiscrepancyTable({
  selectedRun,
  discrepancies,
  page,
  totalPages,
  loading,
  onPageChange,
}: DiscrepancyTableProps) {
  const issueCount = selectedRun
    ? selectedRun.amountMismatchCount + selectedRun.missingCount + selectedRun.invalidCount + selectedRun.duplicateCount
    : 0

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
        <div className="min-w-0">
          <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-amber-700">Exception review</p>
          <h2 className="mt-1 text-lg font-extrabold tracking-tight text-slate-950">Discrepancies</h2>
          <p className="mt-1 max-w-lg truncate text-sm text-slate-500">
            {selectedRun ? selectedRun.fileName : 'Select a completed run'}
          </p>
        </div>
        <span className="shrink-0 rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs font-bold text-amber-800 ring-1 ring-inset ring-amber-200">
          {loading ? 'Loading...' : `${issueCount.toLocaleString()} ${issueCount === 1 ? 'issue' : 'issues'}`}
        </span>
      </div>
      {!selectedRun ? (
        <div className="px-6 py-14 text-center">
          <p className="font-semibold text-slate-700">No run selected</p>
          <p className="mt-1 text-sm text-slate-500">Choose a row from run history to inspect its exceptions.</p>
        </div>
      ) : discrepancies.length === 0 ? (
        <div className="px-6 py-14 text-center">
          <p className="font-semibold text-slate-700">
            {selectedRun.status === 'RUNNING' || selectedRun.status === 'PENDING'
              ? 'Reconciliation in progress'
              : 'No discrepancies found'}
          </p>
          <p className="mt-1 text-sm text-slate-500">
            {selectedRun.status === 'RUNNING' || selectedRun.status === 'PENDING'
              ? 'Results will appear here as soon as processing finishes.'
              : 'Every valid row in this file matched its ledger entry.'}
          </p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-left text-sm">
              <thead className="bg-slate-50/80 text-[10px] uppercase tracking-[0.12em] text-slate-500">
                <tr>
                  <th className="px-6 py-3 font-bold">Line / transaction</th>
                  <th className="px-4 py-3 font-bold">Status</th>
                  <th className="px-4 py-3 text-right font-bold">Gateway</th>
                  <th className="px-4 py-3 text-right font-bold">Ledger</th>
                  <th className="px-6 py-3 font-bold">Reason</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {discrepancies.map((item) => (
                  <tr key={`${item.lineNumber}-${item.status}`} className="transition hover:bg-slate-50/80">
                    <td className="px-6 py-4">
                      <p className="font-bold text-slate-900">{item.transactionId ?? 'Unparseable row'}</p>
                      <p className="mt-1 text-xs text-slate-500">CSV line {item.lineNumber}</p>
                    </td>
                    <td className="px-4 py-4"><StatusBadge status={item.status} /></td>
                    <td className="px-4 py-4 text-right font-semibold tabular-nums text-slate-700">
                      {displayAmount(item.gatewayAmount)}
                    </td>
                    <td className="px-4 py-4 text-right font-semibold tabular-nums text-slate-700">
                      {displayAmount(item.ledgerAmount)}
                    </td>
                    <td className="max-w-sm px-6 py-4 leading-5 text-slate-600">{item.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between border-t border-slate-200 px-6 py-4 text-sm">
            <span className="text-slate-500">Page {page + 1} of {Math.max(totalPages, 1)}</span>
            <div className="flex gap-2">
              <button
                className="rounded-lg border border-slate-300 px-3 py-2 font-semibold text-slate-700 transition hover:border-teal-600 hover:text-teal-700 disabled:opacity-40"
                type="button"
                disabled={page === 0}
                onClick={() => onPageChange(page - 1)}
              >
                Previous
              </button>
              <button
                className="rounded-lg border border-slate-300 px-3 py-2 font-semibold text-slate-700 transition hover:border-teal-600 hover:text-teal-700 disabled:opacity-40"
                type="button"
                disabled={page + 1 >= totalPages}
                onClick={() => onPageChange(page + 1)}
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
