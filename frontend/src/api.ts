import type {
  ApiProblem,
  Credentials,
  CurrentUser,
  Discrepancy,
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

async function request<T>(
  path: string,
  credentials: Credentials,
  init: RequestInit = {},
): Promise<T> {
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
  return (await response.json()) as T
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

  upload: (credentials: Credentials, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<ReconciliationStartResponse>('/reconciliations', credentials, {
      method: 'POST',
      body,
    })
  },
}
