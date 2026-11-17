import { useRef, useState, type ChangeEvent } from 'react'
import { formatProblems } from '../runStats'
import type { FormatForm, MappingSuggestion } from '../types'

interface FormatPanelProps {
  onSuggest: (file: File) => Promise<MappingSuggestion | null>
  onSave: (suggestion: MappingSuggestion, form: FormatForm) => Promise<boolean>
}

const fields: { key: keyof FormatForm; label: string }[] = [
  { key: 'transactionIdColumn', label: 'Transaction ID' },
  { key: 'accountNumberColumn', label: 'Account number' },
  { key: 'amountColumn', label: 'Amount' },
  { key: 'transactionDateColumn', label: 'Transaction date' },
]

const sourceLabel: Record<MappingSuggestion['suggestedBy'], string> = {
  GEMINI: 'Suggested by Gemini, check it',
  HEADER_RULES: 'Suggested by header-name rules',
  MANUAL: 'Entered by hand',
}

function aiNote(suggestion: MappingSuggestion) {
  if (suggestion.aiUsed) return 'AI was asked because header rules could not place every column.'
  const rulesPlacedAll =
    suggestion.transactionIdColumn && suggestion.accountNumberColumn && suggestion.amountColumn && suggestion.transactionDateColumn
  if (rulesPlacedAll) return 'Header rules placed every column, so no AI was needed.'
  return suggestion.aiEnabled ? 'AI could not help with this file.' : 'AI suggestions are off, so pick the missing columns yourself.'
}

/**
 * Teach the app a new gateway layout: upload one sample file, review the suggested mapping,
 * fix anything wrong, save. Later files from that gateway are read with the saved format.
 */
export function FormatPanel({ onSuggest, onSave }: FormatPanelProps) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [suggestion, setSuggestion] = useState<MappingSuggestion | null>(null)
  const [form, setForm] = useState<FormatForm | null>(null)
  const [showSent, setShowSent] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  async function pickSample(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setBusy(true)
    try {
      const result = await onSuggest(file)
      setSuggestion(result)
      setForm(
        result
          ? {
              name: file.name.replace(/\.csv$/i, ''),
              transactionIdColumn: result.transactionIdColumn ?? '',
              accountNumberColumn: result.accountNumberColumn ?? '',
              amountColumn: result.amountColumn ?? '',
              transactionDateColumn: result.transactionDateColumn ?? '',
              dateFormat: result.dateFormat ?? '',
            }
          : null,
      )
    } finally {
      setBusy(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  async function save() {
    if (!suggestion || !form) return
    setBusy(true)
    try {
      if (await onSave(suggestion, form)) {
        setSuggestion(null)
        setForm(null)
        setOpen(false)
      }
    } finally {
      setBusy(false)
    }
  }

  const problems = form ? formatProblems(form) : []
  const dateOptions = suggestion
    ? [...suggestion.dateFormatCandidates, ...suggestion.allDateFormats.filter((f) => !suggestion.dateFormatCandidates.includes(f))]
    : []

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-5">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-cyan-700">Other file layouts</p>
          <h2 className="mt-1 text-lg font-extrabold tracking-tight text-slate-950">Gateway formats</h2>
          <p className="mt-1 max-w-2xl text-sm text-slate-500">
            For exports with different column names, extra columns or dates like 09/07/2026. Upload one sample, check
            the mapping, save it once, then pick it when you upload that gateway's files.
          </p>
        </div>
        <button
          className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-bold text-slate-700 transition hover:border-cyan-600 hover:text-cyan-700"
          type="button"
          onClick={() => setOpen(!open)}
        >
          {open ? 'Close' : 'Add a format'}
        </button>
      </div>

      {open ? (
        <div className="space-y-4 border-t border-slate-200 px-6 py-5 text-sm">
          <label className="flex w-fit cursor-pointer items-center gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-2.5 transition hover:border-cyan-600">
            <span className="font-bold text-slate-800">{busy && !form ? 'Reading sample...' : 'Choose a sample CSV'}</span>
            <input ref={inputRef} className="sr-only" type="file" accept=".csv,text/csv" onChange={pickSample} disabled={busy} />
          </label>

          {suggestion && form ? (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={`rounded-full px-2.5 py-1 text-[11px] font-bold ring-1 ring-inset ${
                    suggestion.suggestedBy === 'GEMINI' ? 'bg-violet-50 text-violet-800 ring-violet-200' : 'bg-slate-100 text-slate-700 ring-slate-200'
                  }`}
                >
                  {sourceLabel[suggestion.suggestedBy]}
                </span>
                <span className="text-xs text-slate-500">
                  {aiNote(suggestion)}
                </span>
              </div>

              {suggestion.warnings.length > 0 ? (
                <ul className="list-disc space-y-1 rounded-lg bg-amber-50 py-2 pl-8 pr-3 text-xs text-amber-900">
                  {suggestion.warnings.map((warning) => (
                    <li key={warning}>{warning}</li>
                  ))}
                </ul>
              ) : null}

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <label className="block font-semibold text-slate-700">
                  Format name
                  <input
                    className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 font-normal"
                    value={form.name}
                    maxLength={80}
                    onChange={(event) => setForm({ ...form, name: event.target.value })}
                  />
                </label>
                {fields.map((field) => (
                  <label key={field.key} className="block font-semibold text-slate-700">
                    {field.label} column
                    <select
                      className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 font-normal"
                      value={form[field.key]}
                      onChange={(event) => setForm({ ...form, [field.key]: event.target.value })}
                    >
                      <option value="">Choose...</option>
                      {suggestion.headers.map((header) => (
                        <option key={header} value={header}>
                          {header}
                        </option>
                      ))}
                    </select>
                  </label>
                ))}
                <label className="block font-semibold text-slate-700">
                  Date format
                  <select
                    className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 font-normal"
                    value={form.dateFormat}
                    onChange={(event) => setForm({ ...form, dateFormat: event.target.value })}
                  >
                    <option value="">Choose...</option>
                    {dateOptions.map((pattern) => (
                      <option key={pattern} value={pattern}>
                        {pattern}
                        {suggestion.dateFormatCandidates.includes(pattern) ? ' (fits the sample)' : ''}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <button className="text-xs font-bold text-cyan-700 underline-offset-4 hover:underline" type="button" onClick={() => setShowSent(!showSent)}>
                {showSent ? 'Hide' : 'Show'} what an AI service would see
              </button>
              {showSent ? (
                <pre className="overflow-x-auto rounded-lg bg-slate-950 p-3 text-[11px] leading-5 text-slate-200">
                  {[suggestion.headers.join(', '), ...suggestion.maskedSampleRows.map((row) => row.join(', '))].join('\n')}
                </pre>
              ) : null}

              <div className="flex flex-wrap items-center gap-3">
                <button
                  className="rounded-xl bg-cyan-700 px-4 py-2.5 font-bold text-white transition hover:bg-cyan-800 disabled:cursor-not-allowed disabled:bg-slate-300"
                  type="button"
                  disabled={busy || problems.length > 0}
                  onClick={save}
                >
                  Save format
                </button>
                {problems.length > 0 ? <span className="text-xs text-slate-500">{problems.join(' · ')}</span> : null}
              </div>
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}
