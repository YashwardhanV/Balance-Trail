interface StatCardProps {
  label: string
  value: number
  accent: 'green' | 'orange' | 'violet' | 'rose' | 'slate'
  hint: string
}

const accents: Record<StatCardProps['accent'], { dot: string; wash: string }> = {
  green: { dot: 'bg-teal-500', wash: 'from-teal-50' },
  orange: { dot: 'bg-amber-500', wash: 'from-amber-50' },
  violet: { dot: 'bg-violet-500', wash: 'from-violet-50' },
  rose: { dot: 'bg-rose-500', wash: 'from-rose-50' },
  slate: { dot: 'bg-slate-700', wash: 'from-slate-100' },
}

export function StatCard({ label, value, accent, hint }: StatCardProps) {
  return (
    <div className={`relative overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-br ${accents[accent].wash} via-white to-white p-5 shadow-sm`}>
      <div className="flex items-center gap-2">
        <span className={`h-2 w-2 rounded-full ${accents[accent].dot}`} aria-hidden="true" />
        <p className="text-[11px] font-bold uppercase tracking-[0.15em] text-slate-600">{label}</p>
      </div>
      <p className="mt-3 text-3xl font-extrabold tracking-[-0.03em] text-slate-950 tabular-nums">
        {value.toLocaleString()}
      </p>
      <p className="mt-1 text-xs text-slate-500">{hint}</p>
    </div>
  )
}
