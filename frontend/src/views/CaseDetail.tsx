import { useEffect, useState } from 'react'
import * as api from '../api'
import * as off from '../offline'
import { Badge, CaseProgress, Err, Loading, StatusBadge, fmt, useAction, useAiEnabled, useLoad } from './common'

type Props = { id: string; role: api.Me['role']; me: api.Me | null; onBack: () => void; online: boolean }

export default function CaseDetail({ id, role, me, onBack, online }: Props) {
  const { data: c, error, reload, setData } = useLoad(() => api.getCase(id), [id])
  const act = useAction()
  const aiEnabled = useAiEnabled()
  useEffect(() => { const t = setInterval(reload, 5000); return () => clearInterval(t) }, [reload])
  if (!c) return <div><button className="ghost" onClick={onBack}>← Back</button><Err text={error} />{!error && <Loading />}</div>

  const apply = async (fn: () => Promise<api.CaseView>) => { const r = await act.run(fn); if (r) setData(r) }
  const staff = role !== 'WORKER'

  return (
    <div>
      <button className="ghost" onClick={onBack}>← Back</button>
      <h1>{c.caseNumber} <StatusBadge status={c.status} /></h1>
      <CaseProgress status={c.status} />
      <div className="card">
        <b>{c.workerName ?? 'Candidate'}</b> {c.workerPhone && `(${c.workerPhone})`}<br />
        {c.trade}, {c.experienceYears} years<div className="pre">{c.skills}</div>
        {c.details && <div className="pre muted">{c.details}</div>}
        {c.qualification && <p>Qualification: <b>{c.qualification.summary.code} v{c.qualification.summary.version}</b> – {c.qualification.summary.title}</p>}
        {c.assessorPhone && <p>Assessor: {c.assessorPhone}</p>}
      </div>
      <Err text={act.error} />

      {c.suggestions.length > 0 && c.status !== 'DRAFT' && !c.qualification && role !== 'ASSESSOR' && (
        <>
          <h2>Suggested qualifications</h2>
          <p className="muted">
            {aiEnabled === false ? 'AI feature is turned off. These suggestions are keyword-based. ' : 'AI suggestions are advisory. '}
            An administrator confirms the qualification.
          </p>
          {c.suggestions.map(s => (
            <div className="card" key={s.versionId}>
              <b>{s.code} v{s.version}</b> – {s.title} <Badge text={`${Math.round(s.confidence * 100)}% (${s.source})`} kind={s.flagged ? 'warn' : 'good'} />
              {s.flagged && <Badge text="low confidence – review" kind="warn" />}
              <div>{s.explanation}</div>
              {role === 'ADMIN' && c.status === 'AWAITING_QUALIFICATION' && <button disabled={act.busy} onClick={() => apply(() => api.confirmQualification(c.id, s.versionId))}>Confirm this qualification</button>}
            </div>
          ))}
        </>
      )}

      {role === 'ADMIN' && c.status === 'AWAITING_QUALIFICATION' && <ManualQualification onConfirm={v => apply(() => api.confirmQualification(c.id, v))} />}
      {role === 'ADMIN' && c.status === 'AWAITING_ASSESSOR' && <AssignAssessor id={c.id} onAssign={a => apply(() => api.assign(c.id, a))} />}

      {role === 'WORKER' && <WorkerEvidence c={c} online={online} reload={reload} apply={apply} />}
      {staff && c.evidence.length > 0 && <EvidenceList c={c} />}
      {role === 'ASSESSOR' && (c.status === 'IN_ASSESSMENT' || c.status === 'EVIDENCE_REQUESTED') && <Assessment c={c} apply={apply} />}
      {c.status === 'IN_ASSESSMENT' || c.status === 'EVIDENCE_REQUESTED' || !staff ? null : <Scores c={c} />}
      {role === 'WORKER' && c.status === 'DECIDED' && <Scores c={c} />}

      {c.submission && (
        <div className="card">
          <h3>Assessor recommendation: {fmt(c.submission.recommendation)}</h3>
          <div className="pre">{c.submission.notes}</div>
          <h3>AI summary (advisory – not a certification decision)</h3>
          <div className="ai pre">{aiEnabled === false ? 'AI feature is turned off.' : c.submission.aiSummary ?? 'No AI summary available.'}</div>
        </div>
      )}
      {c.status === 'AWAITING_DECISION' && staff && (me?.canDecide ? <Decision onDecide={(o, r) => apply(() => api.decide(c.id, o, r))} /> : <p className="muted">Waiting for an authorised decision-maker.</p>)}
      {c.decision && (
        <div className="card">
          <h3>Final decision: {fmt(c.decision.outcome)}</h3>
          <div className="pre">{c.decision.rationale}</div>
          <div className="muted">Recorded by {c.decision.decidedBy} · {c.decision.decidedAt}</div>
        </div>
      )}
      {((staff && ['AWAITING_DECISION', 'DECIDED'].includes(c.status)) || (role === 'WORKER' && c.status === 'DECIDED')) && (
        <div className="row">
          <button onClick={() => act.run(() => api.openFile(`/cases/${c.id}/report`))}>View report</button>
          <button className="ghost" onClick={() => act.run(() => api.openFile(`/cases/${c.id}/report?format=pdf`))}>Report (PDF)</button>
        </div>
      )}
      {role === 'ADMIN' && <WorkflowPanel id={c.id} />}
    </div>
  )
}

function ManualQualification({ onConfirm }: { onConfirm: (v: string) => void }) {
  const { data } = useLoad(() => api.listVersions('PUBLISHED'))
  const [v, setV] = useState('')
  return (
    <div className="card">
      <h3>Choose a published qualification</h3>
      <div className="row">
        <select value={v} onChange={e => setV(e.target.value)}>
          <option value="">Select…</option>
          {(data ?? []).map(x => <option key={x.id} value={x.id}>{x.code} v{x.version} – {x.title}</option>)}
        </select>
        <button disabled={!v} onClick={() => onConfirm(v)}>Confirm</button>
      </div>
    </div>
  )
}

function AssignAssessor({ id, onAssign }: { id: string; onAssign: (a: string) => void }) {
  const { data, error } = useLoad(() => api.candidates(id), [id])
  return (
    <div className="card">
      <h3>Recommended assessors</h3>
      <Err text={error} />
      {data && data.length === 0 && <p className="banner">No eligible assessor is available. The case stays pending until an assessor authorised for this trade is added.</p>}
      {(data ?? []).map(a => (
        <div className="row" key={a.id}><span>{a.phone} · {a.trades.join(', ')}</span><button onClick={() => onAssign(a.id)}>Assign</button></div>
      ))}
    </div>
  )
}

function EvidenceList({ c }: { c: api.CaseView }) {
  const act = useAction()
  return (
    <div className="card">
      <h3>Evidence</h3>
      <Err text={act.error} />
      {c.evidence.map(e => (
        <div key={e.id}>{e.filename}{e.note && ` – ${e.note}`} <button className="ghost" onClick={() => act.run(() => api.openFile(`/cases/${c.id}/evidence/${e.id}/file`))}>Open</button></div>
      ))}
    </div>
  )
}

function WorkerEvidence({ c, online, reload, apply }: { c: api.CaseView; online: boolean; reload: () => void; apply: (f: () => Promise<api.CaseView>) => void }) {
  const [queued, setQueued] = useState<off.LocalEvidence[]>([])
  const [note, setNote] = useState('')
  const [competency, setCompetency] = useState('')
  const act = useAction()
  const refresh = () => off.evidenceFor(c.id).then(setQueued)
  useEffect(() => { refresh() }, [c.id, c.evidence.length]) // eslint-disable-line
  if (c.status === 'DECIDED') return <EvidenceList c={c} />
  const open = c.evidenceRequests.filter(r => !r.fulfilledAt)

  async function add(file: File) {
    await off.queueEvidence({ id: crypto.randomUUID(), caseId: c.id, competencyId: competency || undefined, note: note || undefined, filename: file.name, blob: file })
    setNote(''); await refresh()
    off.sync().then(() => { refresh(); reload() })
  }

  return (
    <div className="card">
      <h3>Evidence</h3>
      {open.map(r => <div className="banner" key={r.id}>Evidence requested: {r.message}</div>)}
      {c.evidence.map(e => <div key={e.id}>{e.filename}{e.note && ` – ${e.note}`} <Badge text="synchronized" kind="good" /></div>)}
      {queued.map(e => <div key={e.id}>{e.filename} <Badge text={e.sync === 'pending' ? (online ? 'waiting to sync' : 'saved locally (offline)') : e.sync} kind={e.sync === 'error' ? 'bad' : 'warn'} /> {e.error && <span className="error">{e.error}</span>}</div>)}
      {c.qualification && (
        <label>For competency (optional)
          <select value={competency} onChange={e => setCompetency(e.target.value)}>
            <option value="">General</option>
            {c.qualification.competencies.map(x => <option key={x.id} value={x.id}>{x.code} – {x.title}</option>)}
          </select>
        </label>
      )}
      <label>Note<input value={note} onChange={e => setNote(e.target.value)} /></label>
      <input type="file" accept="image/*,video/*,application/pdf,text/plain" onChange={e => { const f = e.target.files?.[0]; if (f) add(f); e.target.value = '' }} />
      <Err text={act.error} />
      {c.status === 'EVIDENCE_REQUESTED' && <button disabled={!online || act.busy || queued.length > 0} onClick={() => apply(() => api.submitEvidence(c.id))}>Submit requested evidence</button>}
      {!c.qualification && c.status !== 'DRAFT' && <p className="muted">You can attach evidence now; the assessor will review it.</p>}
    </div>
  )
}

function Scores({ c }: { c: api.CaseView }) {
  if (!c.qualification || c.scores.length === 0) return null
  return (
    <div className="card">
      <h3>Competency scores</h3>
      <div className="table-wrap"><table><tbody>
        {c.qualification.competencies.map(k => {
          const s = c.scores.find(x => x.competencyId === k.id)
          return <tr key={k.id}><td>{k.code} {k.title}</td><td>{s ? `${s.score}/${k.maxScore}` : 'not scored'}</td><td>{s?.comment}</td></tr>
        })}
      </tbody></table></div>
    </div>
  )
}

function Assessment({ c, apply }: { c: api.CaseView; apply: (f: () => Promise<api.CaseView>) => void }) {
  const [msg, setMsg] = useState('')
  const [rec, setRec] = useState('COMPETENT')
  const [notes, setNotes] = useState('')
  if (!c.qualification) return null
  return (
    <div>
      <h2>Assessment</h2>
      {c.evidenceRequests.filter(r => !r.fulfilledAt).map(r => <div className="banner" key={r.id}>Waiting for worker evidence: {r.message}</div>)}
      {c.qualification.competencies.map(k => <CompetencyRow key={k.id} c={c} k={k} apply={apply} />)}
      {c.status === 'IN_ASSESSMENT' && (
        <>
          <div className="card">
            <h3>Request additional evidence</h3>
            <textarea rows={2} value={msg} onChange={e => setMsg(e.target.value)} />
            <button disabled={!msg.trim()} onClick={() => { apply(() => api.requestEvidence(c.id, msg)); setMsg('') }}>Request evidence</button>
          </div>
          <div className="card">
            <h3>Submit recommendation</h3>
            <select value={rec} onChange={e => setRec(e.target.value)}>
              <option value="COMPETENT">Competent</option><option value="NOT_YET_COMPETENT">Not yet competent</option>
            </select>
            <textarea rows={3} value={notes} onChange={e => setNotes(e.target.value)} placeholder="Observations" />
            <button onClick={() => apply(() => api.submitAssessment(c.id, rec, notes))}>Submit assessment</button>
            <p className="muted">All mandatory competencies must be scored before submitting.</p>
          </div>
        </>
      )}
    </div>
  )
}

function CompetencyRow({ c, k, apply }: { c: api.CaseView; k: api.Competency; apply: (f: () => Promise<api.CaseView>) => void }) {
  const cur = c.scores.find(s => s.competencyId === k.id)
  const [score, setScore] = useState(cur?.score?.toString() ?? '')
  const [comment, setComment] = useState(cur?.comment ?? '')
  const n = Number(score)
  const valid = score !== '' && Number.isInteger(n) && n >= 0 && n <= k.maxScore
  const evidence = c.evidence.filter(e => e.competencyId === k.id)
  return (
    <div className="card">
      <b>{k.code} – {k.title}</b> {k.mandatory ? <Badge text="mandatory" kind="warn" /> : <Badge text="optional" />}
      {k.rubric && <div className="muted">Rubric: {k.rubric}</div>}
      {k.evidenceRequirements.length > 0 && <div className="muted">Evidence required: {k.evidenceRequirements.join('; ')} ({evidence.length} attached)</div>}
      <div className="row">
        <input type="number" min={0} max={k.maxScore} value={score} onChange={e => setScore(e.target.value)} aria-label={`Score for ${k.code}`} style={{ width: '5rem' }} /> / {k.maxScore}
        <input value={comment} onChange={e => setComment(e.target.value)} placeholder="Comment" style={{ flex: 1 }} />
        <button disabled={!valid} onClick={() => apply(() => api.score(c.id, k.id, n, comment))}>Save</button>
      </div>
      {score !== '' && !valid && <span className="error">Score must be 0 to {k.maxScore}</span>}
    </div>
  )
}

function Decision({ onDecide }: { onDecide: (o: string, r: string) => void }) {
  const [o, setO] = useState('COMPETENT')
  const [r, setR] = useState('')
  return (
    <div className="card">
      <h3>Record final decision</h3>
      <select value={o} onChange={e => setO(e.target.value)}>
        <option value="COMPETENT">Competent</option><option value="NOT_YET_COMPETENT">Not yet competent</option>
      </select>
      <textarea rows={2} value={r} onChange={e => setR(e.target.value)} placeholder="Rationale" />
      <button onClick={() => onDecide(o, r)}>Record decision</button>
    </div>
  )
}

function WorkflowPanel({ id }: { id: string }) {
  const { data, reload } = useLoad(() => api.workflow(id), [id])
  const audit = useLoad(() => api.caseAudit(id), [id])
  const act = useAction()
  const [show, setShow] = useState(false)
  const bad = data && !['RUNNING', 'COMPLETED'].includes(data.state)
  return (
    <div className="card">
      <h3>Workflow <Badge text={data?.state ?? '…'} kind={bad ? 'bad' : data?.state === 'COMPLETED' ? 'good' : undefined} /></h3>
      {data?.detail && <p className="error">{data.detail}</p>}
      <Err text={act.error} />
      <div className="row">
        <button onClick={() => act.run(() => api.retryWorkflow(id)).then(reload)}>Retry / resume</button>
        <button className="ghost" onClick={() => { setShow(!show); audit.reload() }}>{show ? 'Hide' : 'Show'} audit trail</button>
      </div>
      {show && <div className="table-wrap"><table><thead><tr><th>Time</th><th>Action</th><th>By</th><th>Detail</th></tr></thead><tbody>
        {(audit.data ?? []).map((e, i) => <tr key={i}><td>{e.created_at}</td><td>{e.action}</td><td>{e.actor ?? 'system'}</td><td>{e.detail}</td></tr>)}
      </tbody></table></div>}
    </div>
  )
}
