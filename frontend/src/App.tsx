import { useState } from 'react'
import { api } from './api'
import { Dashboard } from './Dashboard'
import { LoginPage } from './components/LoginPage'
import type { Credentials, CurrentUser } from './types'

interface AuthState {
  credentials: Credentials
  user: CurrentUser
}

export default function App() {
  const [auth, setAuth] = useState<AuthState | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function login(credentials: Credentials) {
    setBusy(true)
    setError(null)
    try {
      const user = await api.me(credentials)
      setAuth({ credentials, user })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not sign in')
    } finally {
      setBusy(false)
    }
  }

  if (!auth) {
    return <LoginPage busy={busy} error={error} onLogin={login} />
  }

  return (
    <Dashboard
      credentials={auth.credentials}
      user={auth.user}
      onLogout={() => setAuth(null)}
    />
  )
}
