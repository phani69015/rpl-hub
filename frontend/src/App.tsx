import { useEffect, useState } from 'react'
import * as api from './api'
import { startAutoSync } from './offline'
import Admin from './views/Admin'
import Assessor from './views/Assessor'
import Worker from './views/Worker'
import Landing from './views/Landing'
import { Badge, Loading, useAiEnabled } from './views/common'

export default function App() {
  const [signedIn, setSignedIn] = useState(api.isSignedIn())
  const [me, setMe] = useState<api.Me>()
  const [online, setOnline] = useState(navigator.onLine)
  const aiEnabled = useAiEnabled()

  useEffect(() => {
    const on = () => setOnline(true), off = () => setOnline(false)
    window.addEventListener('online', on); window.addEventListener('offline', off)
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off) }
  }, [])
  useEffect(() => { if (signedIn) api.me().then(setMe).catch(() => undefined) }, [signedIn])
  useEffect(() => (signedIn ? startAutoSync(() => undefined) : undefined), [signedIn])

  if (!signedIn) return <Landing onDone={() => setSignedIn(true)} />
  if (!me) return <Loading />
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <span className="brand"><span className="logo" aria-hidden>R</span>RPL Hub</span>
          <div className="user">
            {aiEnabled === false && <Badge text="AI feature is turned off" kind="off" />}
            <div className="user-meta">{me.phone}<small>{me.role.toLowerCase()}</small></div>
            <button className="ghost" onClick={() => { api.signOut(); setSignedIn(false); setMe(undefined) }}>Sign out</button>
          </div>
        </div>
      </header>
      <main>
        {!online && <div className="banner">You are offline. Declarations and evidence are saved on this device and sync when you reconnect.</div>}
        {me.role === 'ADMIN' && <Admin me={me} />}
        {me.role === 'ASSESSOR' && <Assessor me={me} />}
        {me.role === 'WORKER' && <Worker online={online} />}
      </main>
    </>
  )
}
