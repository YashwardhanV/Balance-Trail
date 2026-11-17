export type RunStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'

export type ItemStatus =
  | 'MATCHED'
  | 'AMOUNT_MISMATCH'
  | 'ACCOUNT_MISMATCH'
  | 'DATE_MISMATCH'
  | 'MISSING_IN_LEDGER'
  | 'MISSING_IN_GATEWAY'
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
  accountMismatchCount: number
  dateMismatchCount: number
  /** Missing in the ledger (old name kept for compatibility). */
  missingCount: number
  /** Ledger rows the file never mentions. Not part of totalCount. */
  missingInGatewayCount: number
  invalidCount: number
  duplicateCount: number
  /** "Probably the same payment" pairs suggested for this run. */
  suggestedPairCount: number
  /** The ledger import this run was compared against (0 = seeded demo ledger). */
  ledgerVersion: number
  /** The saved gateway format used to read the file, or null for the standard layout. */
  gatewayFormatId: number | null
  failureMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface Discrepancy {
  /** Null for MISSING_IN_GATEWAY rows, which come from the ledger. */
  lineNumber: number | null
  transactionId: string | null
  accountNumber: string | null
  gatewayAmount: number | null
  transactionDate: string | null
  ledgerTransactionId: string | null
  ledgerAccountNumber: string | null
  ledgerAmount: number | null
  ledgerTransactionDate: string | null
  status: ItemStatus
  reason: string
}

export interface LedgerImport {
  id: number
  fileName: string
  uploadedBy: string
  status: RunStatus
  totalCount: number
  importedCount: number
  invalidCount: number
  failureMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export type SuggestionStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

export interface MatchSuggestion {
  id: number
  gatewayLineNumber: number
  gatewayTransactionId: string
  gatewayAccountNumber: string
  gatewayAmount: number
  gatewayDate: string
  ledgerTransactionId: string
  ledgerAccountNumber: string
  ledgerAmount: number
  ledgerDate: string
  idDistance: number
  ambiguous: boolean
  status: SuggestionStatus
  decidedBy: string | null
  decidedAt: string | null
  decisionNote: string | null
}

export type SuggestedBy = 'GEMINI' | 'HEADER_RULES' | 'MANUAL'

export interface GatewayFormat {
  id: number
  name: string
  headers: string[]
  transactionIdColumn: string
  accountNumberColumn: string
  amountColumn: string
  transactionDateColumn: string
  dateFormat: string
  suggestedBy: SuggestedBy
  createdBy: string
  createdAt: string
}

export interface MappingSuggestion {
  headers: string[]
  maskedSampleRows: string[][]
  transactionIdColumn: string | null
  accountNumberColumn: string | null
  amountColumn: string | null
  transactionDateColumn: string | null
  dateFormat: string | null
  dateFormatCandidates: string[]
  allDateFormats: string[]
  aiEnabled: boolean
  aiUsed: boolean
  suggestedBy: SuggestedBy
  warnings: string[]
}

/** What the analyst edits before saving a format. Empty string = not chosen yet. */
export interface FormatForm {
  name: string
  transactionIdColumn: string
  accountNumberColumn: string
  amountColumn: string
  transactionDateColumn: string
  dateFormat: string
}

export interface LedgerImportError {
  lineNumber: number
  transactionId: string | null
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
