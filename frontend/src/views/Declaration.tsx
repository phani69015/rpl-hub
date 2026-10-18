import { useState } from 'react'
import { type LocalDraft, putDraft, sync } from '../offline'
import { Err } from './common'

/** Offline-capable declaration form: always saves to the device first, then syncs. */
export default function Declaration({ draft, onDone }: { draft: LocalDraft; onDone: () => void }) {
  const [f, setF] = useState(draft)
  const [error, setError] = useState('')
  const set = (k: keyof LocalDraft, v: string | number) => setF({ ...f, [k]: v })

  async function save(submit: boolean) {
    if (!f.trade.trim() || !f.skills.trim()) return setError('Trade and skills are required')
    if (!(f.experienceYears >= 0 && f.experienceYears <= 60)) return setError('Experience must be 0 to 60 years')
    setError('')
    await putDraft({ ...f, submit, sync: 'pending' })
    onDone()
    sync().then(onDone)
  }

  return (
    <div className="card">
      <h2>Self-declaration</h2>
      <label>Trade<input value={f.trade} onChange={e => set('trade', e.target.value)} placeholder="e.g. Electrical" /></label>
      <label>Years of experience<input type="number" min={0} max={60} value={f.experienceYears} onChange={e => set('experienceYears', Number(e.target.value))} /></label>
      <label>Skills<textarea rows={3} value={f.skills} onChange={e => set('skills', e.target.value)} placeholder="What work can you do?" /></label>
      <label>Supporting details<textarea rows={3} value={f.details} onChange={e => set('details', e.target.value)} /></label>
      <Err text={error} />
      <div className="row">
        <button onClick={() => save(false)}>Save draft</button>
        <button onClick={() => save(true)}>Submit for assessment</button>
        <button className="ghost" onClick={onDone}>Cancel</button>
      </div>
      <p className="INFO">Saved on this device first. It syncs automatically when you are online.</p>
    </div>
  )
}
