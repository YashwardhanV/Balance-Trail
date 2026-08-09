import { useRef, useState, type ChangeEvent } from 'react'

interface UploadPanelProps {
  busy: boolean
  onUpload: (file: File) => Promise<void>
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  return `${(bytes / 1024).toFixed(bytes >= 1024 * 10 ? 0 : 1)} KB`
}

export function UploadPanel({ busy, onUpload }: UploadPanelProps) {
  const [selected, setSelected] = useState<File | null>(null)
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

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="grid lg:grid-cols-[0.8fr_1.2fr]">
        <div className="border-b border-slate-200 bg-slate-950 px-6 py-6 text-white lg:border-b-0 lg:border-r lg:px-7">
          <div className="flex items-center gap-3">
            <span className="grid h-9 w-9 place-items-center rounded-lg bg-teal-400/15 text-teal-300" aria-hidden="true">
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path d="M12 16V4m0 0L8 8m4-4 4 4" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M5 14v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4" strokeLinecap="round" />
              </svg>
            </span>
            <div>
              <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-teal-300">Start a new run</p>
              <h2 className="mt-0.5 text-lg font-bold tracking-tight">Import gateway records</h2>
            </div>
          </div>
          <p className="mt-4 text-sm leading-6 text-slate-300">
            Upload a CSV and compare it with the seeded ledger. An identical file safely reopens its original run.
          </p>
        </div>

        <div className="flex flex-col justify-between gap-4 px-6 py-6 sm:flex-row sm:items-center lg:px-7">
          <label className="group flex min-w-0 flex-1 cursor-pointer items-center gap-4 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-3 transition hover:border-teal-600 hover:bg-teal-50/40">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg border border-slate-200 bg-white text-xs font-extrabold text-slate-500 group-hover:text-teal-700" aria-hidden="true">
              CSV
            </span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-bold text-slate-800">
                {selected?.name ?? 'Choose a CSV file'}
              </span>
              <span className="mt-0.5 block text-xs text-slate-500">
                {selected ? `${formatFileSize(selected.size)} selected` : 'Use the documented gateway CSV columns'}
              </span>
            </span>
            <input
              ref={inputRef}
              className="sr-only"
              type="file"
              accept=".csv,text/csv"
              onChange={selectFile}
            />
          </label>
          <button
            className="shrink-0 rounded-xl bg-teal-700 px-5 py-3.5 text-sm font-bold text-white shadow-sm transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
            type="button"
            disabled={!selected || busy}
            onClick={upload}
          >
            {busy ? 'Starting...' : 'Start reconciliation'}
          </button>
        </div>
      </div>
    </section>
  )
}
