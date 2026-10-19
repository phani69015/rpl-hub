import { useState } from 'react'
import * as api from '../api'
import CaseDetail from './CaseDetail'
import { CaseTable, Err, Loading, useLoad } from './common'

export default function Assessor({ me }: { me: api.Me }) {
  const [open, setOpen] = useState<string>()
  const list = useLoad(api.listCases)
  if (open) return <CaseDetail id={open} role="ASSESSOR" me={me} online onBack={() => { setOpen(undefined); list.reload() }} />
  return (
    <div>
      <div className="row between">
        <h1>My assigned assessments</h1>
        <button className="ghost" onClick={list.reload}>Refresh</button>
      </div>
      <Err text={list.error} />
      {list.data
        ? <CaseTable cases={list.data} empty="No assessments are assigned to you yet." actions={c => <button className="ghost" onClick={() => setOpen(c.id)}>Open</button>} />
        : !list.error && <Loading />}
    </div>
  )
}
