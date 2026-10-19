import { useState } from 'react'
import * as api from '../api'
import CaseDetail from './CaseDetail'
import { Badge, CaseTable, Err, Loading, PhoneInput, isValidPhone, toE164, useAction, useLoad } from './common'

/** Workflow health of a case: healthy states are neutral/green, anything else needs attention. */
function WorkflowState({ workflow }: { workflow?: api.WorkflowStatus }) {
  if (!workflow) return null
  const kind = workflow.state === 'COMPLETED' ? 'good' : workflow.state === 'RUNNING' ? undefined : 'bad'
  return <><Badge text={workflow.state} kind={kind} />{workflow.detail && <div className="error">{workflow.detail}</div>}</>
}

function Import({ canPublish }: { canPublish: boolean }) {
  const [result, setResult] = useState<api.ImportResult>()
  const versions = useLoad(() => api.listVersions())
  const act = useAction()
  return (
    <div>
      <h1>Qualifications</h1>
      <div className="card">
      <h3>Import from file</h3>
      <p className="muted">CSV, Excel or JSON. Imported qualifications are saved as drafts until published.</p>
      <input type="file" accept=".csv,.xlsx,.json" onChange={e => { const f = e.target.files?.[0]; if (f) act.run(async () => { setResult(await api.uploadFile(f)); versions.reload() }); e.target.value = '' }} />
      <Err text={act.error || versions.error} />
      </div>
      {result && (
        <>
          <p className="muted">{result.filename}: {result.totalRows} rows · {result.versionsCreated} drafts created · {result.duplicatesSkipped} duplicates skipped · {result.qualificationsRejected} rejected</p>
          <div className="table-wrap"><table>
            <thead><tr><th>Row</th><th>Qualification</th><th>Severity</th><th>Field</th><th>Message</th></tr></thead>
            <tbody>{result.issues.map((i, n) => (
              <tr key={n} className={`sev-${i.severity.toLowerCase()}`}><td>{i.rowNumber ?? ''}</td><td>{i.qualificationCode}</td><td>{i.severity}</td><td>{i.field}</td><td>{i.message}</td></tr>
            ))}</tbody>
          </table></div>
        </>
      )}
      <h2>Qualification versions</h2>
      <div className="table-wrap"><table>
        <thead><tr><th>Code</th><th>Ver</th><th>Title</th><th>Trade</th><th>NSQF</th><th>Status</th><th /></tr></thead>
        <tbody>{(versions.data ?? []).map(v => (
          <tr key={v.id}>
            <td>{v.code}</td><td>{v.version}</td><td>{v.title}</td><td>{v.trade}</td><td>{v.nsqfLevel}</td><td><Badge text={v.status} kind={v.status === 'PUBLISHED' ? 'good' : v.status === 'DRAFT' ? 'warn' : 'off'} /></td>
            <td className="actions">{v.status === 'DRAFT' && canPublish && <button onClick={() => act.run(() => api.publish(v.id)).then(versions.reload)}>Publish</button>}</td>
          </tr>
        ))}</tbody>
      </table></div>
    </div>
  )
}

function Cases({ me }: { me: api.Me }) {
  const [open, setOpen] = useState<string>()
  const list = useLoad(api.adminCases)
  const workflows = new Map((list.data ?? []).map(r => [r.case.id, r.workflow]))
  if (open) return <CaseDetail id={open} role="ADMIN" me={me} online onBack={() => { setOpen(undefined); list.reload() }} />
  return (
    <div>
      <div className="row between">
        <h1>Assessment cases</h1>
        <button className="ghost" onClick={list.reload}>Refresh</button>
      </div>
      <Err text={list.error} />
      {list.data ? (
        <CaseTable cases={list.data.map(r => r.case)} empty="No submitted cases yet."
          extra={{ head: 'Workflow', cell: c => <WorkflowState workflow={workflows.get(c.id)} /> }}
          actions={c => <button className="ghost" onClick={() => setOpen(c.id)}>Open</button>} />
      ) : !list.error && <Loading />}
    </div>
  )
}

function Assessors() {
  const list = useLoad(api.assessors)
  const act = useAction()
  const [phone, setPhone] = useState('')
  const [trades, setTrades] = useState('')
  const [canDecide, setCanDecide] = useState(false)
  return (
    <div>
      <h1>Assessors</h1>
      <div className="table-wrap"><table>
        <thead><tr><th>Phone</th><th>Authorised trades</th><th>Can decide</th></tr></thead>
        <tbody>{(list.data ?? []).map(a => <tr key={a.id}><td>{a.phone}</td><td>{a.trades.join(', ')}</td><td>{a.canDecide ? <Badge text="yes" kind="good" /> : <Badge text="no" kind="off" />}</td></tr>)}</tbody>
      </table></div>
      <div className="card">
        <h3>Add or update assessor</h3>
        <PhoneInput label="Phone" value={phone} onChange={setPhone} />
        <label>Trades (comma separated)<input value={trades} onChange={e => setTrades(e.target.value)} placeholder="Electrical, Plumbing" /></label>
        <label><input type="checkbox" checked={canDecide} onChange={e => setCanDecide(e.target.checked)} /> Authorised to record final decisions</label>
        <Err text={act.error} />
        <button disabled={!isValidPhone(phone)} onClick={() => act.run(() => api.saveAssessor(toE164(phone), trades.split(',').map(t => t.trim()).filter(Boolean), canDecide)).then(list.reload)}>Save</button>
      </div>
    </div>
  )
}

function Audit() {
  const log = useLoad(api.audit)
  return (
    <div>
      <div className="row between">
        <h1>Audit log <span className="muted">latest 200</span></h1>
        <button className="ghost" onClick={log.reload}>Refresh</button>
      </div>
      <div className="table-wrap"><table>
        <thead><tr><th>Time</th><th>Action</th><th>By</th><th>Entity</th><th>Detail</th></tr></thead>
        <tbody>{(log.data ?? []).map((e, i) => <tr key={i}><td>{e.created_at}</td><td>{e.action}</td><td>{e.actor ?? 'system'}</td><td>{e.entity_type} {String(e.entity_id).slice(0, 8)}</td><td>{e.detail}</td></tr>)}</tbody>
      </table></div>
    </div>
  )
}

export default function Admin({ me }: { me: api.Me }) {
  const [tab, setTab] = useState('cases')
  const tabs: Record<string, React.ReactElement> = {
    cases: <Cases me={me} />, import: <Import canPublish={me.canPublish} />, assessors: <Assessors />, audit: <Audit />,
  }
  return (
    <div>
      <nav className="tabs">{Object.keys(tabs).map(t => <button key={t} className={t === tab ? 'active' : ''} aria-current={t === tab ? 'page' : undefined} onClick={() => setTab(t)}>{t}</button>)}</nav>
      {tabs[tab]}
    </div>
  )
}
