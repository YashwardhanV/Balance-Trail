import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import { BrandMark } from './components/BrandMark'
import { DiscrepancyTable } from './components/DiscrepancyTable'
import { RunTable } from './components/RunTable'
import { StatCard } from './components/StatCard'
import { StatusBadge } from './components/StatusBadge'
import { UploadPanel } from './components/UploadPanel'
import type { Credentials, CurrentUser, Discrepancy, ReconciliationRun } from './types'

interface DashboardProps {
  credentials: Credentials
  user: CurrentUser
  onLogout: () => void
}

export function Dashboard({ credentials, user, onLogout }: DashboardProps) {
  const [runs, setRuns] = useState<ReconciliationRun[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [discrepancies, setDiscrepancies] = useState<Discrepancy[]>([])
  const [discrepancyPage, setDiscrepancyPage] = useState(0)
  const [discrepancyPages, setDiscrepancyPages] = useState(0)
  const [loadingDiscrepancies, setLoadingDiscrepancies] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const selectedRun = runs.find((run) => run.id === selectedId) ?? null
  const hasActiveRun = runs.some((run) => run.status === 'PENDING' || run.status === 'RUNNING')

  const loadRuns = useCallback(
    async (signal?: AbortSignal) => {
      const page = await api.listRuns(credentials, signal)
      setRuns(page.content)
      setSelectedId((current) => current ?? page.content[0]?.id ?? null)
    },
    [credentials],
  )

  useEffect(() => {
    const controller = new AbortController()
    loadRuns(controller.signal).catch((reason: unknown) => {
      if (reason instanceof DOMException && reason.name === 'AbortError') return
      setError(reason instanceof Error ? reason.message : 'Could not load reconciliation history')
    })
    return () => controller.abort()
  }, [loadRuns])

  useEffect(() => {
    if (!hasActiveRun) return undefined
    const timer = window.setInterval(() => {
      loadRuns().catch((reason: unknown) => {
        setError(reason instanceof Error ? reason.message : 'Could not refresh run status')
      })
    }, 2000)
    return () => window.clearInterval(timer)
  }, [hasActiveRun, loadRuns])

  useEffect(() => {
    if (!selectedId) {
      setDiscrepancies([])
      return undefined
    }
    const controller = new AbortController()
    setLoadingDiscrepancies(true)
    api
      .discrepancies(credentials, selectedId, discrepancyPage, controller.signal)
      .then((page) => {
        setDiscrepancies(page.content)
        setDiscrepancyPages(page.totalPages)
      })
      .catch((reason: unknown) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return
        setError(reason instanceof Error ? reason.message : 'Could not load discrepancies')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingDiscrepancies(false)
      })
    return () => controller.abort()
  }, [credentials, selectedId, discrepancyPage, selectedRun?.status])

  async function upload(file: File) {
    setUploading(true)
    setError(null)
    setMessage(null)
    try {
      const response = await api.upload(credentials, file)
      setSelectedId(response.reconciliation.id)
      setDiscrepancyPage(0)
      setMessage(
        response.idempotentReplay
          ? 'Identical file detected. Opened the existing reconciliation run.'
          : 'Upload accepted. The batch job is now processing.',
      )
      await loadRuns()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not upload the CSV')
    } finally {
      setUploading(false)
    }
  }

  function selectRun(run: ReconciliationRun) {
    setSelectedId(run.id)
    setDiscrepancyPage(0)
  }

  return (
    <div className="min-h-screen bg-[#f4f7f8] text-slate-950">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-[1500px] items-center justify-between px-5 py-3.5 sm:px-8">
          <BrandMark />
          <div className="flex items-center gap-3 sm:gap-5">
            <div className="hidden items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600 md:flex">
              <span className={`h-2 w-2 rounded-full ${hasActiveRun ? 'animate-pulse bg-cyan-500' : 'bg-teal-500'}`} aria-hidden="true" />
              {hasActiveRun ? 'Live job polling' : 'Workspace ready'}
            </div>
            <div className="hidden text-right sm:block">
              <p className="text-sm font-bold text-slate-800">{user.username}</p>
              <p className="text-[11px] uppercase tracking-wider text-slate-500">{user.role}</p>
            </div>
            <button
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-bold text-slate-700 transition hover:border-teal-700 hover:text-teal-800"
              type="button"
              onClick={onLogout}
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-[1500px] space-y-6 px-5 py-7 sm:px-8 sm:py-9">
        <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-700">Daily operations</p>
            <h1 className="mt-2 text-3xl font-extrabold tracking-[-0.03em] text-slate-950 sm:text-4xl">Reconciliation control room</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
              Import transaction files, monitor Spring Batch processing, and investigate every unmatched record.
            </p>
          </div>
          <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
            <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Refresh policy</p>
            <p className="mt-1 font-semibold text-slate-700">Every 2 seconds while a job is active</p>
          </div>
        </div>

        <UploadPanel busy={uploading} onUpload={upload} />

        <div aria-live="polite" className="space-y-3">
          {message ? (
            <p className="rounded-xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm font-medium text-teal-900" role="status">
              {message}
            </p>
          ) : null}
          {error ? (
            <div className="flex items-start justify-between gap-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800" role="alert">
              <span>{error}</span>
              <button className="font-extrabold" type="button" onClick={() => setError(null)} aria-label="Dismiss error">
                X
              </button>
            </div>
          ) : null}
        </div>

        <section aria-labelledby="summary-heading">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 id="summary-heading" className="text-sm font-extrabold text-slate-900">Selected run summary</h2>
              <p className="mt-0.5 max-w-xl truncate text-xs text-slate-500">
                {selectedRun?.fileName ?? 'Upload or select a run to populate these totals'}
              </p>
            </div>
            {selectedRun ? <StatusBadge status={selectedRun.status} /> : null}
          </div>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <StatCard label="Total records" value={selectedRun?.totalCount ?? 0} accent="slate" hint="Rows accepted by the job" />
            <StatCard label="Matched" value={selectedRun?.matchedCount ?? 0} accent="green" hint="Amount and ledger aligned" />
            <StatCard label="Amount mismatch" value={selectedRun?.amountMismatchCount ?? 0} accent="orange" hint="Transaction found, value differs" />
            <StatCard label="Missing" value={selectedRun?.missingCount ?? 0} accent="violet" hint="No ledger entry located" />
            <StatCard
              label="Invalid / duplicate"
              value={(selectedRun?.invalidCount ?? 0) + (selectedRun?.duplicateCount ?? 0)}
              accent="rose"
              hint="Rows isolated from matching"
            />
          </div>
        </section>

        {selectedRun?.failureMessage ? (
          <p className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900" role="alert">
            Run failed: {selectedRun.failureMessage}
          </p>
        ) : null}

        <div className="grid items-start gap-6 2xl:grid-cols-[0.95fr_1.3fr]">
          <RunTable runs={runs} selectedId={selectedId} onSelect={selectRun} />
          <DiscrepancyTable
            selectedRun={selectedRun}
            discrepancies={discrepancies}
            page={discrepancyPage}
            totalPages={discrepancyPages}
            loading={loadingDiscrepancies}
            onPageChange={setDiscrepancyPage}
          />
        </div>
      </main>
    </div>
  )
}
