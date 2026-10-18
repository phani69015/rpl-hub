import { openDB } from 'idb'
import * as api from './api'

/**
 * Offline outbox. Declarations and evidence are written to IndexedDB first, then synchronised.
 * Ids are client-generated, so a repeated sync is harmless (the server upserts by id).
 *   pending   = saved on this device, waiting to sync
 *   synced    = acknowledged by the server
 *   conflict  = server copy changed (or case already submitted); user must choose
 *   error     = server rejected the data; user must fix it
 */
export type SyncState = 'pending' | 'synced' | 'conflict' | 'error'
export type LocalDraft = {
  id: string; trade: string; experienceYears: number; skills: string; details: string
  baseVersion: number; submit: boolean; sync: SyncState; error?: string; server?: api.CaseView; updatedAt: number
}
export type LocalEvidence = {
  id: string; caseId: string; competencyId?: string; note?: string; filename: string; blob: Blob; sync: SyncState; error?: string
}

const db = openDB('rpl-hub', 1, {
  upgrade(d) {
    d.createObjectStore('drafts', { keyPath: 'id' })
    d.createObjectStore('evidence', { keyPath: 'id' })
  },
})

export const drafts = async () => ((await (await db).getAll('drafts')) as LocalDraft[]).sort((a, b) => b.updatedAt - a.updatedAt)
export const getDraft = async (id: string) => (await db).get('drafts', id) as Promise<LocalDraft | undefined>
export const putDraft = async (d: LocalDraft) => (await db).put('drafts', { ...d, updatedAt: Date.now() })
export const removeDraft = async (id: string) => (await db).delete('drafts', id)
export const evidenceFor = async (caseId: string) => ((await (await db).getAll('evidence')) as LocalEvidence[]).filter(e => e.caseId === caseId)
export const queueEvidence = async (e: Omit<LocalEvidence, 'sync'>) => (await db).put('evidence', { ...e, sync: 'pending' })

const isNetwork = (e: unknown) => !(e instanceof api.ApiError)
let running: Promise<void> | null = null

/** Pushes everything pending. Stops quietly when offline. Concurrent calls share one run. */
export function sync(): Promise<void> {
  running ??= doSync().finally(() => { running = null })
  return running
}

async function doSync() {
  if (!api.isSignedIn()) return
  const d = await db
  for (const draft of (await drafts()).filter(x => x.sync === 'pending').reverse()) {
    try {
      const server = await api.saveCase(draft.id, {
        trade: draft.trade, experienceYears: draft.experienceYears, skills: draft.skills, details: draft.details,
        baseVersion: draft.baseVersion, submit: draft.submit,
      })
      await putDraft({ ...draft, sync: 'synced', baseVersion: server.version, server, error: undefined })
    } catch (e) {
      if (isNetwork(e)) return
      const err = e as api.ApiError
      await putDraft({ ...draft, sync: err.status === 409 ? 'conflict' : 'error', error: err.message, server: err.body?.server ?? draft.server })
    }
  }
  const blocked = new Set((await drafts()).filter(x => x.sync !== 'synced').map(x => x.id))
  for (const ev of ((await d.getAll('evidence')) as LocalEvidence[]).filter(x => x.sync === 'pending' && !blocked.has(x.caseId))) {
    try {
      await api.putEvidence(ev.caseId, ev.id, ev.blob, ev.filename, ev.note, ev.competencyId)
      await d.delete('evidence', ev.id) // acknowledged; the server list now shows it
    } catch (e) {
      if (isNetwork(e)) return
      await d.put('evidence', { ...ev, sync: 'error', error: (e as Error).message })
    }
  }
}

export function startAutoSync(onChange: () => void) {
  const run = () => sync().then(onChange)
  window.addEventListener('online', run)
  const t = setInterval(run, 30000)
  run()
  return () => { window.removeEventListener('online', run); clearInterval(t) }
}
