import type { ItemStatus, RunStatus } from '../types'

const styles: Record<RunStatus | ItemStatus, { badge: string; dot: string }> = {
  PENDING: { badge: 'bg-slate-100 text-slate-700 ring-slate-200', dot: 'bg-slate-400' },
  RUNNING: { badge: 'bg-cyan-50 text-cyan-800 ring-cyan-200', dot: 'bg-cyan-500 animate-pulse' },
  COMPLETED: { badge: 'bg-teal-50 text-teal-800 ring-teal-200', dot: 'bg-teal-500' },
  COMPLETED_WITH_SKIPS: { badge: 'bg-amber-50 text-amber-800 ring-amber-200', dot: 'bg-amber-500' },
  FAILED: { badge: 'bg-rose-50 text-rose-700 ring-rose-200', dot: 'bg-rose-500' },
  MATCHED: { badge: 'bg-teal-50 text-teal-800 ring-teal-200', dot: 'bg-teal-500' },
  AMOUNT_MISMATCH: { badge: 'bg-amber-50 text-amber-800 ring-amber-200', dot: 'bg-amber-500' },
  MISSING_IN_LEDGER: { badge: 'bg-violet-50 text-violet-700 ring-violet-200', dot: 'bg-violet-500' },
  INVALID: { badge: 'bg-rose-50 text-rose-700 ring-rose-200', dot: 'bg-rose-500' },
  DUPLICATE: { badge: 'bg-slate-100 text-slate-700 ring-slate-200', dot: 'bg-slate-400' },
}

interface StatusBadgeProps {
  status: RunStatus | ItemStatus
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const style = styles[status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-extrabold tracking-[0.06em] ring-1 ring-inset ${style.badge}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} aria-hidden="true" />
      {status.replaceAll('_', ' ')}
    </span>
  )
}
