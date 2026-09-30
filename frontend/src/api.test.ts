import { describe, expect, it } from 'vitest'
import { authorization } from './api'

describe('authorization', () => {
  it('builds an HTTP Basic header from username and password', () => {
    const header = authorization({ username: 'analyst', password: 'change-me-now' })

    expect(header).toBe(`Basic ${btoa('analyst:change-me-now')}`)
  })

  it('encodes non-ASCII passwords as UTF-8 before Base64', () => {
    const header = authorization({ username: 'analyst', password: 'पासवर्ड' })
    const decoded = new TextDecoder().decode(
      Uint8Array.from(atob(header.replace('Basic ', '')), (char) => char.charCodeAt(0)),
    )

    expect(decoded).toBe('analyst:पासवर्ड')
  })
})
