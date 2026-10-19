import { useEffect, useState } from 'react'
import * as api from '../api'
import * as off from '../offline'
import CaseDetail from './CaseDetail'
import Declaration from './Declaration'
import { Badge, CaseTable, Err, Loading, useAction, useLoad } from './common'

export function SyncBadge({ d, online }: { d: off.LocalDraft; online: boolean }) {
  if (d.sync === 'synced') return <Badge text="synchronized" kind="good" />
  if (d.sync === 'pending') return <Badge text={online ? 'waiting to sync' : 'saved locally (offline)'} kind="warn" />
  return <Badge text={d.sync} kind="bad" />
}

function Profile() {
  const { data, reload } = useLoad(api.getProfile)
  const { error, run } = useAction()
  const [p, setP] = useState({ fullName: '', primaryTrade: '', yearsExperience: 0, skills: '' })
  const [saved, setSaved] = useState(false)
  useEffect(() => { if (data) setP({ fullName: data.fullName ?? '', primaryTrade: data.primaryTrade ?? '', yearsExperience: data.yearsExperience ?? 0, skills: data.skills ?? '' }) }, [data])
  return (
    <div className="card">
      <h2>My profile</h2>
      <label>Full name<input value={p.fullName} onChange={e => setP({ ...p, fullName: e.target.value })} /></label>
      <label>Primary trade<input value={p.primaryTrade} onChange={e => setP({ ...p, primaryTrade: e.target.value })} /></label>
      <label>Years of experience<input type="number" min={0} max={60} value={p.yearsExperience} onChange={e => setP({ ...p, yearsExperience: Number(e.target.value) })} /></label>
      <label>Skills<textarea rows={2} value={p.skills} onChange={e => setP({ ...p, skills: e.target.value })} /></label>
      <Err text={error} />
      <button onClick={async () => { if (await run(() => api.saveProfile(p))) { setSaved(true); reload() } }}>Save profile</button>
      {saved && <span className="ok"> Saved</span>}
    </div>
  )
}

export default function Worker({ online }: { online: boolean }) {
  const [drafts, setDrafts] = useState<off.LocalDraft[]>([])
  const [editing, setEditing] = useState<off.LocalDraft>()
  const [openId, setOpenId] = useState<string>()
  const cases = useLoad(api.listCases)
  const refresh = () => off.drafts().then(setDrafts).then(() => cases.reload())
  useEffect(() => { refresh() }, []) // eslint-disable-line
  useEffect(() => { const t = setInterval(refresh, 5000); return () => clearInterval(t) }) // pick up background sync + workflow progress

  const blank = (): off.LocalDraft => ({ id: crypto.randomUUID(), trade: '', experienceYears: 0, skills: '', details: '', baseVersion: 0, submit: false, sync: 'pending', updatedAt: 0 })
  const localIds = new Set(drafts.map(d => d.id))
  const pendingLocal = drafts.filter(d => d.sync !== 'synced' || d.server?.status === 'DRAFT')

  async function resolve(d: off.LocalDraft, keepMine: boolean) {
    if (keepMine && d.server && d.server.status === 'DRAFT') await off.putDraft({ ...d, baseVersion: d.server.version, sync: 'pending', error: undefined })
    else await off.removeDraft(d.id)
    await off.sync(); refresh()
  }

  if (openId) return <CaseDetail id={openId} role="WORKER" me={null} onBack={() => { setOpenId(undefined); refresh() }} online={online} />
  if (editing) return <Declaration draft={editing} onDone={() => { setEditing(undefined); refresh() }} />

  return (
    <div>
      <div className="row between">
        <h1>My assessments</h1>
        <button onClick={() => setEditing(blank())}>New declaration</button>
      </div>
      {pendingLocal.map(d => (
        <div className="card" key={d.id}>
          <div className="row"><b>{d.trade || '(no trade)'}</b> <SyncBadge d={d} online={online} /> {d.submit && <Badge text="submit requested" />}</div>
          <div className="muted">{d.skills}</div>
          {d.error && <p className="error">{d.error}</p>}
          {d.sync === 'conflict' && d.server && (
            <div className="banner">
              The case changed on the server{d.server.status !== 'DRAFT' ? ' (it was already submitted)' : ''}. Server copy: {d.server.trade}, {d.server.experienceYears} yrs – {d.server.skills}
              <div className="row">
                {d.server.status === 'DRAFT' && <button onClick={() => resolve(d, true)}>Keep my version</button>}
                <button className="ghost" onClick={() => resolve(d, false)}>Use server version</button>
              </div>
            </div>
          )}
          {d.sync !== 'conflict' && d.sync !== 'synced' && <button className="ghost" onClick={() => setEditing(d)}>Edit</button>}
        </div>
      ))}
      <Err text={cases.error} />
      {cases.data
        ? <CaseTable cases={cases.data.filter(c => !localIds.has(c.id) || c.status !== 'DRAFT')} empty="You have no assessments yet. Start with a new declaration."
            actions={c => (
              <>
                <button className="ghost" onClick={() => setOpenId(c.id)}>Open</button>
                {c.status === 'DRAFT' && !localIds.has(c.id) && <button onClick={async () => {
                  const s = await api.getCase(c.id)
                  setEditing({ id: s.id, trade: s.trade, experienceYears: s.experienceYears, skills: s.skills, details: s.details ?? '', baseVersion: s.version, submit: false, sync: 'pending', updatedAt: 0 })
                }}>Edit</button>}
              </>
            )} />
        : !cases.error && <Loading />}
      <Profile />
    </div>
  )
}
