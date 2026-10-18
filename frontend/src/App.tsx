import { useEffect, useState } from 'react'
import * as api from './api'
import { startAutoSync } from './offline'
import Admin from './views/Admin'
import Assessor from './views/Assessor'
import Worker from './views/Worker'
import { Err } from './views/common'

function Login({ onDone }: { onDone: () => void }) {
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')
  const run = (fn: () => Promise<unknown>) => fn().catch(e => setError(e.message))
  return (
    <div className="card auth">
      <h1>RPL Hub</h1>
      <p className="INFO" style={{ textAlign: 'center' }}>Sign in with your phone number</p>
      <div className="row">
        <input value={phone} onChange={e => setPhone(e.target.value)} placeholder="Phone, e.g. +919999999999" inputMode="tel" />
        <button onClick={() => run(async () => { setError(''); await api.requestOtp(phone); setSent(true) })}>Send OTP</button>
      </div>
      {sent && (
        <div className="row">
          <input value={otp} onChange={e => setOtp(e.target.value)} placeholder="OTP (mock: 123456)" inputMode="numeric" />
          <button onClick={() => run(async () => { await api.verifyOtp(phone, otp); onDone() })}>Verify</button>
        </div>
      )}
      <Err text={error} />
    </div>
  )
}

export default function App() {
  const [signedIn, setSignedIn] = useState(api.isSignedIn())
  const [me, setMe] = useState<api.Me>()
  const [online, setOnline] = useState(navigator.onLine)

  useEffect(() => {
    const on = () => setOnline(true), off = () => setOnline(false)
    window.addEventListener('online', on); window.addEventListener('offline', off)
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off) }
  }, [])
  useEffect(() => { if (signedIn) api.me().then(setMe).catch(() => undefined) }, [signedIn])
  useEffect(() => (signedIn ? startAutoSync(() => undefined) : undefined), [signedIn])

  if (!signedIn) return <Login onDone={() => setSignedIn(true)} />
  if (!me) return <p>Loading…</p>
  return (
    <>
      <header className="topbar">
        <span className="brand">RPL Hub<small>{me.role.toLowerCase()} · {me.phone}</small></span>
        <button className="ghost" onClick={() => { api.signOut(); setSignedIn(false); setMe(undefined) }}>Sign out</button>
      </header>
      {!online && <div className="banner">You are offline. Declarations and evidence are saved on this device and sync when you reconnect.</div>}
      {me.role === 'ADMIN' && <Admin me={me} />}
      {me.role === 'ASSESSOR' && <Assessor me={me} />}
      {me.role === 'WORKER' && <Worker online={online} />}
    </>
  )
}
