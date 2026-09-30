export type RunStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'

export type ItemStatus =
  | 'MATCHED'
  | 'AMOUNT_MISMATCH'
  | 'MISSING_IN_LEDGER'
  | 'INVALID'
  | 'DUPLICATE'

export interface Credentials {
  username: string
  password: string
}

export interface CurrentUser {
  username: string
  role: string
}

export interface ReconciliationRun {
  id: string
  fileName: string
  fileSha256: string
  status: RunStatus
  totalCount: number
  matchedCount: number
  amountMismatchCount: number
  missingCount: number
  invalidCount: number
  duplicateCount: number
  failureMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface Discrepancy {
  lineNumber: number
  transactionId: string | null
  accountNumber: string | null
  gatewayAmount: number | null
  ledgerAmount: number | null
  transactionDate: string | null
  status: ItemStatus
  reason: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ReconciliationStartResponse {
  reconciliation: ReconciliationRun
  idempotentReplay: boolean
}

export interface ApiProblem {
  title?: string
  detail?: string
  status?: number
}
