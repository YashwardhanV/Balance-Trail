import { useState } from 'react'
import type { MatchSuggestion, ReconciliationRun } from '../types'

interface SuggestionPanelProps {
  run: ReconciliationRun | null
  suggestions: MatchSuggestion[]
  onDecide: (suggestion: MatchSuggestion, decision: 'confirm' | 'reject', note?: string) => Promise<void>
}

const money = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const statusStyle: Record<MatchSuggestion['status'], string> = {
  PENDING: 'bg-amber-50 text-amber-800 ring-amber-200',
  CONFIRMED: 'bg-teal-50 text-teal-800 ring-teal-200',
  REJECTED: 'bg-slate-100 text-slate-600 ring-slate-200',
}

/**
 * "Probably the same payment": a gateway line missing from the ledger next to a ledger row
 * missing from the file, with the same account, amount and date and an ID that differs by a
 * character or two. The analyst decides; nothing is matched automatically.
 */
export function SuggestionPanel({ run, suggestions, onDecide }: SuggestionPanelProps) {
  const [busyId, setBusyId] = useState<number | null>(null)

  if (!run || run.status !== 'COMPLETED' || run.suggestedPairCount === 0) return null
  const pending = suggestions.filter((item) => item.status === 'PENDING').length

  async function decide(suggestion: MatchSuggestion, decision: 'confirm' | 'reject') {
    const note = decision === 'reject' ? window.prompt('Why is this not the same payment? (optional)') ?? undefined : undefined
    setBusyId(suggestion.id)
    try {
      await onDecide(suggestion, decision, note)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-amber-700">Needs a decision</p>
          <h2 className="mt-1 text-lg font-extrabold tracking-tight text-slate-950">Probably the same payment</h2>
          <p className="mt-1 max-w-2xl text-sm text-slate-500">
            Same account, amount and date, and the transaction IDs differ by at most 2 characters. Confirming records
            your decision; the original results stay as they are.
          </p>
        </div>
        <span className="shrink-0 rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs font-bold text-amber-800 ring-1 ring-inset ring-amber-200">
          {pending} open of {run.suggestedPairCount}
        </span>
      </div>
      <ul className="divide-y divide-slate-100">
        {suggestions.map((item) => (
          <li key={item.id} className="grid gap-3 px-6 py-4 text-sm lg:grid-cols-[1fr_1fr_auto] lg:items-center">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">In the file (line {item.gatewayLineNumber})</p>
              <p className="font-bold text-slate-900">{item.gatewayTransactionId}</p>
              <p className="text-xs text-slate-500">
                {item.gatewayAccountNumber} &middot; {money.format(item.gatewayAmount)} &middot; {item.gatewayDate}
              </p>
            </div>
            <div>
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">In the ledger</p>
              <p className="font-bold text-slate-900">{item.ledgerTransactionId}</p>
              <p className="text-xs text-slate-500">
                {item.ledgerAccountNumber} &middot; {money.format(item.ledgerAmount)} &middot; {item.ledgerDate}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2 lg:justify-end">
              <span className="text-xs text-slate-500">
                IDs differ by {item.idDistance} {item.idDistance === 1 ? 'character' : 'characters'}
              </span>
              {item.ambiguous ? (
                <span className="rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-700 ring-1 ring-inset ring-rose-200">
                  ONE OF SEVERAL
                </span>
              ) : null}
              {item.status === 'PENDING' ? (
                <>
                  <button
                    className="rounded-lg bg-teal-700 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-teal-800 disabled:opacity-40"
                    type="button"
                    disabled={busyId !== null}
                    onClick={() => decide(item, 'confirm')}
                  >
                    Same payment
                  </button>
                  <button
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-bold text-slate-700 transition hover:border-rose-400 hover:text-rose-700 disabled:opacity-40"
                    type="button"
                    disabled={busyId !== null}
                    onClick={() => decide(item, 'reject')}
                  >
                    Not the same
                  </button>
                </>
              ) : (
                <span className={`rounded-full px-2.5 py-1 text-[10px] font-extrabold ring-1 ring-inset ${statusStyle[item.status]}`}>
                  {item.status}
                  {item.decidedBy ? ` by ${item.decidedBy}` : ''}
                </span>
              )}
            </div>
            {item.decisionNote ? <p className="text-xs text-slate-500 lg:col-span-3">Note: {item.decisionNote}</p> : null}
          </li>
        ))}
      </ul>
    </section>
  )
}
