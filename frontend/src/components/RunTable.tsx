import { StatusBadge } from './StatusBadge'
import type { ReconciliationRun } from '../types'

interface RunTableProps {
  runs: ReconciliationRun[]
  selectedId: string | null
  onSelect: (run: ReconciliationRun) => void
}

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export function RunTable({ runs, selectedId, onSelect }: RunTableProps) {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-teal-700">Import activity</p>
          <h2 className="mt-1 text-lg font-extrabold tracking-tight text-slate-950">Run history</h2>
          <p className="mt-1 text-sm text-slate-500">Select a file to inspect its results</p>
        </div>
        <span className="rounded-lg bg-slate-100 px-2.5 py-1.5 text-xs font-bold text-slate-600">
          {runs.length} {runs.length === 1 ? 'run' : 'runs'}
        </span>
      </div>
      {runs.length === 0 ? (
        <div className="px-6 py-14 text-center">
          <p className="font-semibold text-slate-700">No reconciliation runs yet</p>
          <p className="mt-1 text-sm text-slate-500">Upload the sample CSV to create your first traceable run.</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="bg-slate-50/80 text-[10px] uppercase tracking-[0.12em] text-slate-500">
              <tr>
                <th className="px-6 py-3 font-bold">File</th>
                <th className="px-4 py-3 font-bold">Status</th>
                <th className="px-4 py-3 text-right font-bold">Records</th>
                <th className="px-4 py-3 text-right font-bold">Issues</th>
                <th className="px-6 py-3 font-bold">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {runs.map((run) => {
                const issues = run.amountMismatchCount + run.missingCount + run.invalidCount + run.duplicateCount
                const selected = run.id === selectedId
                return (
                  <tr
                    key={run.id}
                    aria-selected={selected}
                    className={`transition hover:bg-slate-50 ${selected ? 'bg-teal-50/70 shadow-[inset_3px_0_0_#0f766e]' : ''}`}
                  >
                    <td className="px-6 py-4">
                      <button
                        className="max-w-64 truncate text-left font-bold text-slate-900 underline-offset-4 hover:text-teal-700 hover:underline"
                        type="button"
                        onClick={() => onSelect(run)}
                      >
                        {run.fileName}
                      </button>
                    </td>
                    <td className="px-4 py-4"><StatusBadge status={run.status} /></td>
                    <td className="px-4 py-4 text-right font-semibold tabular-nums text-slate-700">
                      {run.totalCount.toLocaleString()}
                    </td>
                    <td className={`px-4 py-4 text-right font-semibold tabular-nums ${issues > 0 ? 'text-amber-700' : 'text-slate-700'}`}>
                      {issues.toLocaleString()}
                    </td>
                    <td className="px-6 py-4 text-slate-500">
                      {dateFormatter.format(new Date(run.createdAt))}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
