import { useRef, useState, type ChangeEvent } from 'react'
import { StatusBadge } from './StatusBadge'
import type { LedgerImport, LedgerImportError } from '../types'

interface LedgerPanelProps {
  imports: LedgerImport[]
  busy: boolean
  onUpload: (file: File) => Promise<void>
  loadErrors: (importId: number) => Promise<LedgerImportError[]>
}

const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** Upload the company ledger and see the latest imports, with their rejected lines on request. */
export function LedgerPanel({ imports, busy, onUpload, loadErrors }: LedgerPanelProps) {
  const [selected, setSelected] = useState<File | null>(null)
  const [openId, setOpenId] = useState<number | null>(null)
  const [errors, setErrors] = useState<LedgerImportError[]>([])
  const inputRef = useRef<HTMLInputElement>(null)

  function selectFile(event: ChangeEvent<HTMLInputElement>) {
    setSelected(event.target.files?.[0] ?? null)
  }

  async function upload() {
    if (!selected) return
    await onUpload(selected)
    setSelected(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  async function toggleErrors(item: LedgerImport) {
    if (openId === item.id) {
      setOpenId(null)
      return
    }
    setOpenId(item.id)
    setErrors([])
    setErrors(await loadErrors(item.id))
  }

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-col gap-4 border-b border-slate-200 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-indigo-700">Company books</p>
          <h2 className="mt-1 text-lg font-extrabold tracking-tight text-slate-950">Ledger</h2>
          <p className="mt-1 max-w-xl text-sm text-slate-500">
            Columns: transaction_id, account_number, amount, transaction_date, description. New IDs are
            added and existing IDs are updated. Shared by all analysts.
          </p>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <label className="flex min-w-0 cursor-pointer items-center gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-2.5 text-sm transition hover:border-indigo-500">
            <span className="truncate font-bold text-slate-800">{selected?.name ?? 'Choose ledger CSV'}</span>
            <input ref={inputRef} className="sr-only" type="file" accept=".csv,text/csv" onChange={selectFile} />
          </label>
          <button
            className="shrink-0 rounded-xl bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white transition hover:bg-indigo-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            type="button"
            disabled={!selected || busy}
            onClick={upload}
          >
            {busy ? 'Uploading...' : 'Import ledger'}
          </button>
        </div>
      </div>

      {imports.length === 0 ? (
        <p className="px-6 py-5 text-sm text-slate-500">
          No ledger imports yet. Runs are compared with the seeded demo ledger.
        </p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {imports.map((item) => (
            <li key={item.id} className="px-6 py-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-bold text-slate-900">
                    v{item.id} &middot; {item.fileName}
                  </p>
                  <p className="text-xs text-slate-500">
                    {item.uploadedBy} &middot; {dateFormatter.format(new Date(item.createdAt))}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  {item.status === 'COMPLETED' ? (
                    <span className="text-xs tabular-nums text-slate-600">
                      {item.importedCount.toLocaleString()} saved, {item.invalidCount.toLocaleString()} rejected
                    </span>
                  ) : null}
                  {item.invalidCount > 0 ? (
                    <button
                      className="text-xs font-bold text-indigo-700 underline-offset-4 hover:underline"
                      type="button"
                      onClick={() => toggleErrors(item)}
                    >
                      {openId === item.id ? 'Hide rejected lines' : 'Show rejected lines'}
                    </button>
                  ) : null}
                  <StatusBadge status={item.status} />
                </div>
              </div>
              {item.failureMessage ? <p className="mt-2 text-xs text-rose-700">{item.failureMessage}</p> : null}
              {openId === item.id ? (
                <ul className="mt-3 space-y-1 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                  {errors.length === 0 ? <li>Loading...</li> : null}
                  {errors.map((error) => (
                    <li key={error.lineNumber}>
                      Line {error.lineNumber}
                      {error.transactionId ? ` (${error.transactionId})` : ''}: {error.reason}
                    </li>
                  ))}
                  {item.invalidCount > errors.length && errors.length > 0 ? (
                    <li className="text-slate-500">First {errors.length} of {item.invalidCount.toLocaleString()} shown.</li>
                  ) : null}
                </ul>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
