const BASE = '/api'

function syncSessionId(res) {
  const newSessionId = res.headers.get('X-Session-Id')
  if (newSessionId) {
    const current = localStorage.getItem('sessionId')
    if (newSessionId !== current) {
      localStorage.setItem('sessionId', newSessionId)
    }
  }
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const sessionId = localStorage.getItem('sessionId')
  const opts = {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(sessionId ? { 'X-Session-Id': sessionId } : {}),
      ...headers
    }
  }
  if (body) opts.body = JSON.stringify(body)
  const res = await fetch(`${BASE}${path}`, opts)
  syncSessionId(res)
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || res.statusText)
  }
  return res.json()
}

export function register(username, password) {
  return request('/auth/register', { method: 'POST', body: { username, password } })
}

export function login(username, password) {
  return request('/auth/login', { method: 'POST', body: { username, password } })
}

export function helloGuest() {
  return request('/auth/hello-guest')
}

export function guestLogin(fingerprintData) {
  return request('/auth/guest', { method: 'POST', body: fingerprintData })
}

function chatFetch(endpoint, message) {
  const sessionId = localStorage.getItem('sessionId')
  const fingerprint = localStorage.getItem('fingerprint') || ''
  return fetch(`${BASE}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Session-Id': sessionId || '',
      'X-Fingerprint': fingerprint
    },
    body: JSON.stringify({ message })
  })
}

export function streamChat(message, endpoint = '/chat/v1/paper') {
  return chatFetch(endpoint, message).then(res => {
    syncSessionId(res)
    return res
  })
}

export function checkHealth() {
  return request('/health')
}

export function invokeChat(message, endpoint = '/chat/v1/paper/invoke') {
  return chatFetch(endpoint, message).then(res => {
    syncSessionId(res)
    if (!res.ok) return res.text().then(t => { throw new Error(t) })
    return res.json()
  })
}
