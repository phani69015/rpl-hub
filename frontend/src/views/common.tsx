import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api'

export const fmt = (s: string) => s.replace(/_/g, ' ').toLowerCase()

export function Badge({ text, kind }: { text: string; kind?: 'warn' | 'bad' | 'good' }) {
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
