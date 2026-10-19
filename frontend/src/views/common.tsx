import { type ReactNode, useCallback, useEffect, useState } from 'react'
import * as api from '../api'
import { ApiError } from '../api'

export const fmt = (s: string) => s.replace(/_/g, ' ').toLowerCase()

export function Badge({ text, kind }: { text: string; kind?: 'warn' | 'bad' | 'good' | 'off' }) {
  return <span className={'badge ' + (kind ?? '')}>{fmt(text)}</span>
}

const statusKind = (s: string) => (s === 'DECIDED' ? 'good' : s === 'EVIDENCE_REQUESTED' || s === 'AWAITING_ASSESSOR' ? 'warn' : undefined)
export const StatusBadge = ({ status }: { status: string }) => <Badge text={status} kind={statusKind(status)} />

/** Runs an async action, surfacing its error message. */
export function useAction() {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const run = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setError(''); setBusy(true)
    try { return await fn() } catch (e) { setError(e instanceof ApiError || e instanceof Error ? e.message : String(e)) } finally { setBusy(false) }
  }, [])
  return { error, busy, run, setError }
}

export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T>()
  const [error, setError] = useState('')
  const reload = useCallback(() => fn().then(d => { setData(d); setError('') }).catch(e => setError(e.message)), deps) // eslint-disable-line
  useEffect(() => { reload() }, [reload])
  return { data, error, reload, setData }
}

export const Err = ({ text }: { text: string }) => (text ? <p className="error" role="alert">{text}</p> : null)

export const Loading = () => <p className="loading">Loading…</p>

const STEPS = [
  { label: 'Submitted', statuses: ['NEW'] },
  { label: 'Qualification', statuses: ['AWAITING_QUALIFICATION'] },
  { label: 'Assessor', statuses: ['AWAITING_ASSESSOR'] },
  { label: 'Assessment', statuses: ['IN_ASSESSMENT', 'EVIDENCE_REQUESTED'] },
  { label: 'Decision', statuses: ['REPORT_PENDING', 'AWAITING_DECISION'] },
  { label: 'Decided', statuses: ['DECIDED'] },
]

/** Where a case is in its lifecycle. Drafts have not started yet. */
export function CaseProgress({ status }: { status: string }) {
  const current = STEPS.findIndex(s => s.statuses.includes(status))
  return (
    <ol className="steps" aria-label="Case progress">
      {STEPS.map((s, i) => (
        <li key={s.label} className={i < current || status === 'DECIDED' ? 'done' : i === current ? 'current' : ''} aria-current={i === current ? 'step' : undefined}>
          {s.label}
        </li>
      ))}
    </ol>
  )
}

type CaseTableProps = {
  cases: api.CaseRow[]
  actions: (c: api.CaseRow) => ReactNode
  extra?: { head: string; cell: (c: api.CaseRow) => ReactNode }
  empty?: string
}

/** The case list shared by every role. */
export function CaseTable({ cases, actions, extra, empty = 'No cases yet.' }: CaseTableProps) {
  if (cases.length === 0) return <div className="card empty">{empty}</div>
  return (
    <div className="table-wrap"><table>
      <thead><tr><th>Case</th><th>Trade</th><th>Status</th>{extra && <th>{extra.head}</th>}<th /></tr></thead>
      <tbody>{cases.map(c => (
        <tr key={c.id}>
          <td>{c.caseNumber}</td><td>{c.trade}</td><td><StatusBadge status={c.status} /></td>
          {extra && <td>{extra.cell(c)}</td>}
          <td className="actions">{actions(c)}</td>
        </tr>
      ))}</tbody>
    </table></div>
  )
}

let aiEnabledCache: Promise<boolean> | undefined

/** The backend's AI switch (app.ai.enabled). `undefined` while loading; defaults to enabled if unreachable. */
export function useAiEnabled() {
  const [enabled, setEnabled] = useState<boolean>()
  useEffect(() => {
    aiEnabledCache ??= api.config().then(c => c.aiEnabled).catch(() => true)
    aiEnabledCache.then(setEnabled)
  }, [])
  return enabled
}

const COUNTRY_CODE = '+91'

/** Full international number from the 10 digits a user types. */
export const toE164 = (digits: string) => COUNTRY_CODE + digits

export const isValidPhone = (digits: string) => /^[0-9]{10}$/.test(digits)

/** 10-digit mobile number entry with a fixed country-code selector (only India for now). */
export function PhoneInput({ value, onChange, disabled, label = 'Mobile number' }:
  { value: string; onChange: (digits: string) => void; disabled?: boolean; label?: string }) {
  return (
    <label>{label}
      <span className="phone">
        <select aria-label="Country code" disabled={disabled}><option>{COUNTRY_CODE}</option></select>
        <input value={value} onChange={e => onChange(e.target.value.replace(/\D/g, '').slice(0, 10))} placeholder="10-digit number"
          inputMode="numeric" autoComplete="tel-national" maxLength={10} pattern="[0-9]{10}" title="Enter a 10-digit mobile number"
          disabled={disabled} required />
      </span>
    </label>
  )
}
