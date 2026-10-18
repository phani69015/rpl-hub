import { useState } from 'react'
import * as api from '../api'
import CaseDetail from './CaseDetail'
import { Err, StatusBadge, useLoad } from './common'

export default function Assessor({ me }: { me: api.Me }) {
  const [open, setOpen] = useState<string>()
  const list = useLoad(api.listCases)
  if (open) return <CaseDetail id={open} role="ASSESSOR" me={me} online onBack={() => { setOpen(undefined); list.reload() }} />
  return (
    <div>
      <h2>My assigned assessments</h2>
      <button className="ghost" onClick={list.reload}>Refresh</button>
      <Err text={list.error} />
      <div className="table-wrap"><table>
        <thead><tr><th>Case</th><th>Trade</th><th>Status</th><th /></tr></thead>
        <tbody>{(list.data ?? []).map(c => (
          <tr key={c.id}><td>{c.caseNumber}</td><td>{c.trade}</td><td><StatusBadge status={c.status} /></td><td><button className="ghost" onClick={() => setOpen(c.id)}>Open</button></td></tr>
        ))}</tbody>
      </table></div>
    </div>
  )
}
