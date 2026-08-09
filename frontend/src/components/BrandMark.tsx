interface BrandMarkProps {
  compact?: boolean
  inverse?: boolean
}

export function BrandMark({ compact = false, inverse = false }: BrandMarkProps) {
  return (
    <div className="flex items-center gap-3">
      <span
        className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl shadow-sm ${
          inverse ? 'bg-white text-slate-950' : 'bg-slate-950 text-white'
        }`}
        aria-hidden="true"
      >
        <svg viewBox="0 0 32 32" className="h-6 w-6" fill="none">
          <path d="M7 9h10" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
          <path d="M7 16h15" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
          <path d="M7 23h8" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
          <circle cx="21.5" cy="9" r="2.5" fill="#2dd4bf" />
          <circle cx="26" cy="16" r="2.5" fill="#f59e0b" />
          <circle cx="19.5" cy="23" r="2.5" fill="#2dd4bf" />
        </svg>
      </span>
      {!compact ? (
        <span>
          <span className={`block text-base font-extrabold tracking-tight ${inverse ? 'text-white' : 'text-slate-950'}`}>
            BalanceTrail
          </span>
          <span className={`block text-[11px] font-medium tracking-wide ${inverse ? 'text-slate-400' : 'text-slate-500'}`}>
            Reconciliation workspace
          </span>
        </span>
      ) : null}
    </div>
  )
}
