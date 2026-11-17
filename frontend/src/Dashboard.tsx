import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import { BrandMark } from './components/BrandMark'
import { DiscrepancyTable } from './components/DiscrepancyTable'
import { FormatPanel } from './components/FormatPanel'
import { SuggestionPanel } from './components/SuggestionPanel'
import { LedgerPanel } from './components/LedgerPanel'
import { RunTable } from './components/RunTable'
import { StatCard } from './components/StatCard'
import { StatusBadge } from './components/StatusBadge'
import { UploadPanel } from './components/UploadPanel'
import type {
  Credentials,
  CurrentUser,
  Discrepancy,
  FormatForm,
  GatewayFormat,
  LedgerImport,
  MappingSuggestion,
  MatchSuggestion,
  ReconciliationRun,
} from './types'

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
  const [ledgerImports, setLedgerImports] = useState<LedgerImport[]>([])
  const [ledgerUploading, setLedgerUploading] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [formats, setFormats] = useState<GatewayFormat[]>([])
  const [formatId, setFormatId] = useState<number | null>(null)
  const [suggestions, setSuggestions] = useState<MatchSuggestion[]>([])
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const selectedRun = runs.find((run) => run.id === selectedId) ?? null
  const hasActiveRun =
    runs.some((run) => run.status === 'PENDING' || run.status === 'RUNNING') ||
    ledgerImports.some((item) => item.status === 'PENDING' || item.status === 'RUNNING')

  const loadRuns = useCallback(
    async (signal?: AbortSignal) => {
      const [page, ledgerPage, formatList] = await Promise.all([
        api.listRuns(credentials, signal),
        api.listLedgerImports(credentials, signal),
        api.listFormats(credentials, signal),
      ])
      setRuns(page.content)
      setLedgerImports(ledgerPage.content)
      setFormats(formatList)
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

  const suggestionsToLoad =
    selectedRun?.status === 'COMPLETED' && selectedRun.suggestedPairCount > 0 ? selectedRun.id : null

  useEffect(() => {
    if (!suggestionsToLoad) {
      setSuggestions([])
      return undefined
    }
    const controller = new AbortController()
    api
      .suggestions(credentials, suggestionsToLoad, controller.signal)
      .then((page) => setSuggestions(page.content))
      .catch((reason: unknown) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return
        setError(reason instanceof Error ? reason.message : 'Could not load suggested pairs')
      })
    return () => controller.abort()
  }, [credentials, suggestionsToLoad])

  async function decideSuggestion(suggestion: MatchSuggestion, decision: 'confirm' | 'reject', note?: string) {
    if (!selectedRun) return
    try {
      await api.decideSuggestion(credentials, selectedRun.id, suggestion.id, decision, note)
      // Confirming can close other suggestions too, so reload the whole list.
      setSuggestions((await api.suggestions(credentials, selectedRun.id)).content)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not save the decision')
    }
  }

  async function suggestMapping(file: File): Promise<MappingSuggestion | null> {
    setError(null)
    try {
      return await api.suggestMapping(credentials, file)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not read the sample file')
      return null
    }
  }

  async function saveFormat(suggestion: MappingSuggestion, form: FormatForm): Promise<boolean> {
    setError(null)
    try {
      const created = await api.createFormat(credentials, {
        ...form,
        name: form.name.trim(),
        headers: suggestion.headers,
        // If the analyst changed a suggested column, the mapping is theirs, not the suggester's.
        suggestedBy:
          form.transactionIdColumn === suggestion.transactionIdColumn &&
          form.accountNumberColumn === suggestion.accountNumberColumn &&
          form.amountColumn === suggestion.amountColumn &&
          form.transactionDateColumn === suggestion.transactionDateColumn
            ? suggestion.suggestedBy
            : 'MANUAL',
      })
      setFormats(await api.listFormats(credentials))
      setFormatId(created.id)
      setMessage(`Format "${created.name}" saved and selected for your next upload.`)
      return true
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not save the format')
      return false
    }
  }

  async function upload(file: File) {
    setUploading(true)
    setError(null)
    setMessage(null)
    try {
      const response = await api.upload(credentials, file, formatId)
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

  async function uploadLedger(file: File) {
    setLedgerUploading(true)
    setError(null)
    setMessage(null)
    try {
      const created = await api.uploadLedger(credentials, file)
      setMessage(
        `Ledger import v${created.id} accepted. Runs started after it finishes are compared with the new ledger.`,
      )
      await loadRuns()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not upload the ledger')
    } finally {
      setLedgerUploading(false)
    }
  }

  async function exportDiscrepancies() {
    if (!selectedRun) return
    setExporting(true)
    try {
      const blob = await api.exportDiscrepancies(credentials, selectedRun.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `discrepancies-${selectedRun.fileName.replace(/\.csv$/i, '')}.csv`
      link.click()
      URL.revokeObjectURL(url)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not download the discrepancies')
    } finally {
      setExporting(false)
    }
  }

  async function loadLedgerErrors(importId: number) {
    try {
      return (await api.ledgerImportErrors(credentials, importId)).content
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not load rejected ledger lines')
      return []
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
              Keep the ledger current, import gateway files, and investigate every record that does not line up, in both directions.
            </p>
          </div>
          <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
            <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Refresh policy</p>
            <p className="mt-1 font-semibold text-slate-700">Every 2 seconds while a job is active</p>
          </div>
        </div>

        <UploadPanel
          busy={uploading}
          formats={formats}
          formatId={formatId}
          onFormatChange={setFormatId}
          onUpload={upload}
        />

        <FormatPanel onSuggest={suggestMapping} onSave={saveFormat} />

        <LedgerPanel
          imports={ledgerImports}
          busy={ledgerUploading}
          onUpload={uploadLedger}
          loadErrors={loadLedgerErrors}
        />

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
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
            <StatCard label="File lines" value={selectedRun?.totalCount ?? 0} accent="slate" hint="Rows read from the gateway CSV" />
            <StatCard label="Matched" value={selectedRun?.matchedCount ?? 0} accent="green" hint="Amount, account and date agree" />
            <StatCard
              label="Mismatched"
              value={
                (selectedRun?.amountMismatchCount ?? 0) +
                (selectedRun?.accountMismatchCount ?? 0) +
                (selectedRun?.dateMismatchCount ?? 0)
              }
              accent="orange"
              hint={
                selectedRun
                  ? `Amount ${selectedRun.amountMismatchCount}, account ${selectedRun.accountMismatchCount}, date ${selectedRun.dateMismatchCount}`
                  : 'Amount, account or date differs'
              }
            />
            <StatCard label="Missing in ledger" value={selectedRun?.missingCount ?? 0} accent="violet" hint="In the file, not in the ledger" />
            <StatCard label="Missing in file" value={selectedRun?.missingInGatewayCount ?? 0} accent="indigo" hint="In the ledger, not in the file" />
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

        <SuggestionPanel run={selectedRun} suggestions={suggestions} onDecide={decideSuggestion} />

        <div className="grid items-start gap-6 2xl:grid-cols-[0.95fr_1.3fr]">
          <RunTable runs={runs} selectedId={selectedId} onSelect={selectRun} />
          <DiscrepancyTable
            selectedRun={selectedRun}
            discrepancies={discrepancies}
            page={discrepancyPage}
            totalPages={discrepancyPages}
            loading={loadingDiscrepancies}
            exporting={exporting}
            onPageChange={setDiscrepancyPage}
            onExport={exportDiscrepancies}
          />
        </div>
      </main>
    </div>
  )
}
