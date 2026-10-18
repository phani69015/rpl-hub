let token = sessionStorage.getItem('token') ?? ''

export const isSignedIn = () => !!token
export const signOut = () => { token = ''; sessionStorage.removeItem('token') }

/** Thrown for HTTP errors; `network` errors are plain TypeErrors from fetch (offline). */
export class ApiError extends Error {
  status: number
  body: any
  constructor(status: number, message: string, body: any) { super(message); this.status = status; this.body = body }
}

async function raw(path: string, init: RequestInit = {}): Promise<Response> {
  const res = await fetch('/api' + path, {
    ...init,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
  })
  if (res.status === 401 && token) { signOut(); location.reload() }
  return res
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await raw(path, init)
  const body = await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(res.status, body?.error ?? body?.message ?? `Request failed (${res.status})`, body)
  return body as T
}

const json = (method: string, data?: unknown): RequestInit => ({
  method, headers: { 'Content-Type': 'application/json' }, body: data === undefined ? undefined : JSON.stringify(data),
})
const form = (method: string, fields: Record<string, string | Blob | undefined>, filename?: string): RequestInit => {
  const f = new FormData()
  for (const [k, v] of Object.entries(fields)) if (v !== undefined) v instanceof Blob ? f.append(k, v, filename) : f.append(k, v)
  return { method, body: f }
}

/** Opens a protected file (report/evidence) in a new tab using the bearer token. */
export async function openFile(path: string) {
  const res = await raw(path)
  if (!res.ok) throw new ApiError(res.status, (await res.json().catch(() => null))?.error ?? 'Could not open file', null)
  window.open(URL.createObjectURL(await res.blob()), '_blank')
}

export type Me = { phone: string; role: 'WORKER' | 'ADMIN' | 'ASSESSOR'; canDecide: boolean; canPublish: boolean }
export type Issue = { rowNumber: number | null; qualificationCode: string | null; severity: string; field: string | null; message: string }
export type ImportResult = {
  batchId: string; filename: string; totalRows: number; versionsCreated: number
  duplicatesSkipped: number; qualificationsRejected: number; issues: Issue[]
}
export type Version = { id: string; code: string; version: number; title: string; trade: string; nsqfLevel: number | null; status: string }
export type Competency = { id: string; code: string; title: string; mandatory: boolean; maxScore: number; rubric: string | null; evidenceRequirements: string[] }
export type CaseRow = { id: string; caseNumber: string; status: string; trade: string; version: number; createdAt: string }
export type CaseView = {
  id: string; caseNumber: string; status: string; version: number; trade: string; experienceYears: number; skills: string; details: string | null
  workerPhone: string | null; workerName: string | null; assessorId: string | null; assessorPhone: string | null
  qualification: { summary: Version; competencies: Competency[] } | null
  suggestions: { versionId: string; code: string; title: string; version: number; confidence: number; explanation: string; flagged: boolean; source: string }[]
  scores: { competencyId: string; score: number; comment: string | null }[]
  evidence: { id: string; competencyId: string | null; filename: string; note: string | null }[]
  evidenceRequests: { id: string; message: string; fulfilledAt: string | null }[]
  submission: { recommendation: string; notes: string | null; aiSummary: string | null } | null
  decision: { outcome: string; rationale: string | null; decidedBy: string; decidedAt: string } | null
}
export type Declaration = { trade: string; experienceYears: number; skills: string; details: string; baseVersion: number; submit: boolean }
export type Assessor = { id: string; phone: string; canDecide: boolean; trades: string[] }
export type WorkflowStatus = { state: string; detail: string | null }

export const requestOtp = (phone: string) => call<void>('/auth/otp/request', json('POST', { phone }))
export async function verifyOtp(phone: string, otp: string) {
  const r = await call<{ token: string }>('/auth/otp/verify', json('POST', { phone, otp }))
  token = r.token
  sessionStorage.setItem('token', token)
}
export const me = () => call<Me>('/auth/me')

export const getProfile = () => call<any>('/profile')
export const saveProfile = (p: unknown) => call<any>('/profile', json('PUT', p))

export const listCases = () => call<CaseRow[]>('/cases')
export const getCase = (id: string) => call<CaseView>(`/cases/${id}`)
export const saveCase = (id: string, d: Declaration) => call<CaseView>(`/cases/${id}`, json('PUT', d))
export const putEvidence = (caseId: string, id: string, file: Blob, filename: string, note?: string, competencyId?: string) =>
  call<CaseView>(`/cases/${caseId}/evidence/${id}`, form('PUT', { file, note, competencyId }, filename))
export const submitEvidence = (id: string) => call<CaseView>(`/cases/${id}/evidence/submit`, json('POST'))
export const score = (id: string, comp: string, s: number, comment: string) => call<CaseView>(`/cases/${id}/scores/${comp}`, json('PUT', { score: s, comment }))
export const requestEvidence = (id: string, message: string) => call<CaseView>(`/cases/${id}/request-evidence`, json('POST', { message }))
export const submitAssessment = (id: string, recommendation: string, notes: string) => call<CaseView>(`/cases/${id}/submit`, json('POST', { recommendation, notes }))
export const decide = (id: string, outcome: string, rationale: string) => call<CaseView>(`/cases/${id}/decision`, json('POST', { outcome, rationale }))

export const uploadFile = (file: File) => call<ImportResult>('/imports', form('POST', { file }))
export const listVersions = (status?: string) => call<Version[]>('/qualification-versions' + (status ? `?status=${status}` : ''))
export const publish = (id: string) => call<unknown>(`/qualification-versions/${id}/publish`, json('POST'))

export const adminCases = () => call<{ case: CaseRow; workflow: WorkflowStatus }[]>('/admin/cases')
export const confirmQualification = (id: string, versionId: string) => call<CaseView>(`/admin/cases/${id}/confirm-qualification`, json('POST', { versionId }))
export const candidates = (id: string) => call<Assessor[]>(`/admin/cases/${id}/assessor-candidates`)
export const assign = (id: string, assessorId: string) => call<CaseView>(`/admin/cases/${id}/assign`, json('POST', { assessorId }))
export const workflow = (id: string) => call<WorkflowStatus>(`/admin/cases/${id}/workflow`)
export const retryWorkflow = (id: string) => call<WorkflowStatus>(`/admin/cases/${id}/workflow/retry`, json('POST'))
export const caseAudit = (id: string) => call<any[]>(`/admin/cases/${id}/audit`)
export const audit = () => call<any[]>('/admin/audit')
export const assessors = () => call<Assessor[]>('/admin/assessors')
export const saveAssessor = (phone: string, trades: string[], canDecide: boolean) => call<Assessor>('/admin/assessors', json('POST', { phone, trades, canDecide }))
