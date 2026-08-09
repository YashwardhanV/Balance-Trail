import { useState, type FormEvent } from 'react'
import type { Credentials } from '../types'
import { BrandMark } from './BrandMark'

interface LoginPageProps {
  busy: boolean
  error: string | null
  onLogin: (credentials: Credentials) => Promise<void>
}

export function LoginPage({ busy, error, onLogin }: LoginPageProps) {
  const [username, setUsername] = useState('analyst')
  const [password, setPassword] = useState('change-me-now')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await onLogin({ username, password })
  }

  return (
    <main className="login-canvas grid min-h-screen place-items-center px-4 py-6 sm:px-8 sm:py-10">
      <section className="grid w-full max-w-6xl overflow-hidden rounded-[2rem] border border-slate-200/80 bg-white shadow-2xl shadow-slate-900/10 lg:grid-cols-[1.08fr_0.92fr]">
        <div className="brand-hero relative hidden min-h-[680px] flex-col justify-between overflow-hidden bg-slate-950 p-12 text-white lg:flex">
          <div className="relative z-10">
            <BrandMark inverse />
            <div className="mt-24 max-w-lg">
              <p className="text-xs font-bold uppercase tracking-[0.22em] text-teal-300">Transaction operations</p>
              <h1 className="mt-5 text-5xl font-extrabold leading-[1.08] tracking-[-0.035em]">
                Every transaction,<br />clearly accounted for.
              </h1>
              <p className="mt-6 max-w-md text-base leading-7 text-slate-300">
                Import gateway records, trace reconciliation outcomes, and review discrepancies from one focused workspace.
              </p>
            </div>
          </div>
          <div className="relative z-10 grid grid-cols-3 gap-3">
            {['Chunk-safe imports', 'Idempotent reruns', 'Auditable results'].map((feature) => (
              <div key={feature} className="rounded-2xl border border-white/10 bg-white/[0.06] px-4 py-4 text-xs font-semibold text-slate-200 backdrop-blur-sm">
                <span className="mb-3 block h-1.5 w-6 rounded-full bg-teal-400" aria-hidden="true" />
                {feature}
              </div>
            ))}
          </div>
        </div>

        <div className="flex min-h-[620px] flex-col justify-center px-6 py-12 sm:px-12 lg:px-16">
          <div className="mb-12 lg:hidden">
            <BrandMark />
          </div>
          <div className="max-w-md">
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-700">Secure workspace</p>
            <h2 className="mt-3 text-3xl font-extrabold tracking-[-0.025em] text-slate-950">Welcome back</h2>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              Sign in with the analyst credentials configured for your local environment.
            </p>

            <form className="mt-8 space-y-5" onSubmit={submit}>
              <label className="block text-sm font-semibold text-slate-700">
                Username
                <input
                  className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-slate-950 outline-none transition focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                  autoComplete="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  required
                />
              </label>
              <label className="block text-sm font-semibold text-slate-700">
                Password
                <input
                  className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-slate-950 outline-none transition focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
              </label>
              {error ? (
                <p className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
                  {error}
                </p>
              ) : null}
              <button
                className="w-full rounded-xl bg-slate-950 px-4 py-3.5 font-bold text-white shadow-lg shadow-slate-950/10 transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={busy}
                type="submit"
              >
                {busy ? 'Signing in...' : 'Sign in to BalanceTrail'}
              </button>
            </form>
            <p className="mt-7 text-xs leading-5 text-slate-500">
              Demo access is intended for local development. Change the default password before sharing a deployment.
            </p>
          </div>
        </div>
      </section>
    </main>
  )
}
