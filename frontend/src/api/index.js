const BASE = '/api'

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

function chatFetch(endpoint, message) {
  const sessionId = localStorage.getItem('sessionId')
  return fetch(`${BASE}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Session-Id': sessionId || ''
    },
    body: JSON.stringify({ message })
  })
}

export function streamChat(message, endpoint = '/chat/v1/paper') {
  return chatFetch(endpoint, message)
}

export function invokeChat(message, endpoint = '/chat/v1/paper/invoke') {
  return chatFetch(endpoint, message).then(res => {
    if (!res.ok) return res.text().then(t => { throw new Error(t) })
    return res.json()
  })
}
