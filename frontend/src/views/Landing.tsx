import { useState } from 'react'
import * as api from '../api'
import { Err, PhoneInput, isValidPhone, toE164 } from './common'

const STEPS = [
  { icon: '✍️', title: 'Declare', text: 'Tell us your trade, years of experience and skills. Works offline and syncs later.' },
  { icon: '🔎', title: 'Match', text: 'We suggest official qualifications that fit. An administrator confirms the right one.' },
  { icon: '🧰', title: 'Prove', text: 'Upload photos, videos or documents of your real work as evidence.' },
  { icon: '🏅', title: 'Get recognised', text: 'An assessor scores you, a decision-maker signs off, and you get your report.' },
]

const AUDIENCES = [
  { who: 'Workers', text: 'Turn years on the job into a recognised result, with no classroom required.' },
  { who: 'Assessors', text: 'Score competencies against clear rubrics and request more evidence when needed.' },
  { who: 'Administrators', text: 'Manage qualifications, assign assessors and follow every case end to end.' },
]

const TRUST = [
  ['📶', 'Offline-first', 'Fill in forms with no signal. They sync when you reconnect.'],
  ['👤', 'People decide', 'Every outcome is recorded by an authorised person.'],
  ['🧾', 'Fully audited', 'Each action leaves a trail you can inspect.'],
  ['✨', 'AI, optional', 'Suggestions only, and it can be switched off.'],
]

function SignIn({ onDone }: { onDone: () => void }) {
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function run(fn: () => Promise<unknown>) {
    if (!isValidPhone(phone)) return setError('Enter a 10-digit mobile number')
    setError(''); setBusy(true)
    try { await fn() } catch (e) { setError(e instanceof Error ? e.message : String(e)) } finally { setBusy(false) }
  }

  return (
    <form className="card signin" onSubmit={e => { e.preventDefault(); run(sent ? async () => { await api.verifyOtp(toE164(phone), otp); onDone() } : async () => { await api.requestOtp(toE164(phone)); setSent(true) }) }}>
      <h2>{sent ? 'Enter your code' : 'Get started'}</h2>
      <p className="muted">{sent ? `We sent a one-time code to ${toE164(phone)}.` : 'Sign in or create an account with your phone number.'}</p>
      <PhoneInput value={phone} onChange={setPhone} disabled={sent} />
      {sent && (
        <label>One-time code
          <input value={otp} onChange={e => setOtp(e.target.value)} placeholder="123456 (demo code)" inputMode="numeric" autoComplete="one-time-code" autoFocus required />
        </label>
      )}
      <Err text={error} />
      <button className="wide" disabled={busy}>{sent ? 'Verify and continue' : 'Send code'}</button>
      {sent && <button type="button" className="link" onClick={() => { setSent(false); setOtp('') }}>Use a different number</button>}
    </form>
  )
}

export default function Landing({ onDone }: { onDone: () => void }) {
  return (
    <div className="landing">
      <header className="land-nav"><span className="brand"><span className="logo" aria-hidden>R</span>RPL Hub</span></header>

      <section className="hero">
        <div className="hero-text">
          <span className="eyebrow">Recognition of Prior Learning</span>
          <h1>Years of hands-on work, <em>finally on paper.</em></h1>
          <p className="lead">
            You already know your trade. RPL Hub helps you prove it, so your experience can be assessed against
            official qualifications without sitting through a course.
          </p>
          <div className="cert" aria-hidden>
            <div className="cert-badge">✓</div>
            <div>
              <b>Electrician · NSQF Level 4</b>
              <span>8 years experience · 6 of 6 competencies</span>
            </div>
            <span className="badge good">Competent</span>
          </div>
        </div>
        <SignIn onDone={onDone} />
      </section>

      <section className="band">
        <h2>How it works</h2>
        <ol className="journey">
          {STEPS.map((s, i) => (
            <li key={s.title}>
              <span className="journey-icon" aria-hidden>{s.icon}</span>
              <span className="journey-step">Step {i + 1}</span>
              <h3>{s.title}</h3>
              <p className="muted">{s.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="band alt">
        <h2>Built for everyone in the process</h2>
        <div className="audiences">
          {AUDIENCES.map(a => <div className="card" key={a.who}><h3>{a.who}</h3><p className="muted">{a.text}</p></div>)}
        </div>
      </section>

      <section className="band">
        <div className="trust">
          {TRUST.map(([icon, title, text]) => (
            <div key={title}><span aria-hidden>{icon}</span><b>{title}</b><p className="muted">{text}</p></div>
          ))}
        </div>
      </section>

      <footer className="land-foot muted">RPL Hub · Recognising skills that were learned on the job</footer>
    </div>
  )
}
