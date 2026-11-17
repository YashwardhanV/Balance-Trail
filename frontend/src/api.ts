import type {
  ApiProblem,
  Credentials,
  CurrentUser,
  Discrepancy,
  GatewayFormat,
  LedgerImport,
  LedgerImportError,
  MappingSuggestion,
  MatchSuggestion,
  PageResponse,
  ReconciliationRun,
  ReconciliationStartResponse,
} from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function authorization(credentials: Credentials): string {
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`)
  let binary = ''
  bytes.forEach((value) => {
    binary += String.fromCharCode(value)
  })
  return `Basic ${btoa(binary)}`
}

async function send(path: string, credentials: Credentials, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Authorization: authorization(credentials),
      ...init.headers,
    },
  })

  if (!response.ok) {
    let problem: ApiProblem | undefined
    try {
      problem = (await response.json()) as ApiProblem
    } catch {
      problem = undefined
    }
    const fallback = response.status === 401 ? 'Username or password is incorrect' : 'Request failed'
    throw new ApiError(response.status, problem?.detail ?? problem?.title ?? fallback)
  }
  return response
}

async function request<T>(path: string, credentials: Credentials, init: RequestInit = {}): Promise<T> {
  const response = await send(path, credentials, init)
  return (await response.json()) as T
}

function csvForm(file: File) {
  const body = new FormData()
  body.append('file', file)
  return body
}

export const api = {
  me: (credentials: Credentials) => request<CurrentUser>('/auth/me', credentials),

  listRuns: (credentials: Credentials, signal?: AbortSignal) =>
    request<PageResponse<ReconciliationRun>>('/reconciliations?page=0&size=50', credentials, {
      signal,
    }),

  discrepancies: (
    credentials: Credentials,
    runId: string,
    page: number,
    signal?: AbortSignal,
  ) =>
    request<PageResponse<Discrepancy>>(
      `/reconciliations/${runId}/discrepancies?page=${page}&size=20`,
      credentials,
      { signal },
    ),

  /** formatId: a saved gateway format, or null for the standard 4-column layout. */
  upload: (credentials: Credentials, file: File, formatId: number | null = null) =>
    request<ReconciliationStartResponse>(
      formatId === null ? '/reconciliations' : `/reconciliations?formatId=${formatId}`,
      credentials,
      { method: 'POST', body: csvForm(file) },
    ),

  suggestions: (credentials: Credentials, runId: string, signal?: AbortSignal) =>
    request<PageResponse<MatchSuggestion>>(`/reconciliations/${runId}/suggestions?page=0&size=50`, credentials, {
      signal,
    }),

  decideSuggestion: (
    credentials: Credentials,
    runId: string,
    suggestionId: number,
    decision: 'confirm' | 'reject',
    note?: string,
  ) =>
    request<MatchSuggestion>(`/reconciliations/${runId}/suggestions/${suggestionId}/${decision}`, credentials, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(note ? { note } : {}),
    }),

  listFormats: (credentials: Credentials, signal?: AbortSignal) =>
    request<GatewayFormat[]>('/gateway-formats', credentials, { signal }),

  suggestMapping: (credentials: Credentials, file: File) =>
    request<MappingSuggestion>('/gateway-formats/suggest', credentials, { method: 'POST', body: csvForm(file) }),

  createFormat: (credentials: Credentials, body: object) =>
    request<GatewayFormat>('/gateway-formats', credentials, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),

  /** Fetches the CSV with the auth header (a plain link can't send it) and returns it as a file. */
  exportDiscrepancies: async (credentials: Credentials, runId: string) => {
    const response = await send(`/reconciliations/${runId}/discrepancies/export`, credentials)
    return response.blob()
  },

  listLedgerImports: (credentials: Credentials, signal?: AbortSignal) =>
    request<PageResponse<LedgerImport>>('/ledger-imports?page=0&size=5', credentials, { signal }),

  uploadLedger: (credentials: Credentials, file: File) =>
    request<LedgerImport>('/ledger-imports', credentials, { method: 'POST', body: csvForm(file) }),

  ledgerImportErrors: (credentials: Credentials, importId: number) =>
    request<PageResponse<LedgerImportError>>(
      `/ledger-imports/${importId}/errors?page=0&size=20`,
      credentials,
    ),
}
