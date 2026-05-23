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

export function register(username, password, fingerprint = '') {
  return request('/auth/register', { method: 'POST', body: { username, password, fingerprint } })
}

export function login(username, password, fingerprint = '') {
  return request('/auth/login', { method: 'POST', body: { username, password, fingerprint } })
}

export function helloGuest() {
  return request('/auth/hello-guest')
}

export function guestLogin(fingerprintData) {
  return request('/auth/guest', { method: 'POST', body: fingerprintData })
}

function chatFetch(endpoint, message, chatId) {
  const sessionId = localStorage.getItem('sessionId')
  const fingerprint = localStorage.getItem('fingerprint') || ''
  return fetch(`${BASE}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Session-Id': sessionId || '',
      'X-Fingerprint': fingerprint
    },
    body: JSON.stringify({ message, chatId: chatId || null })
  })
}

export function streamChat(message, endpoint = '/chat/v1/paper', chatId = null) {
  return chatFetch(endpoint, message, chatId).then(res => {
    syncSessionId(res)
    return res
  })
}

export function checkHealth() {
  return request('/health')
}

export function invokeChat(message, endpoint = '/chat/v1/paper/invoke', chatId = null) {
  return chatFetch(endpoint, message, chatId).then(res => {
    syncSessionId(res)
    if (!res.ok) return res.text().then(t => { throw new Error(t) })
    return res.json()
  })
}

export function listConversations() {
  return request('/conversations')
}

export function getConversationMessages(chatId, turns = 3) {
  return request(`/conversations/${chatId}/messages?turns=${turns}`)
}

export function updateConversationTitle(chatId, title) {
  return request(`/conversations/${chatId}`, { method: 'PUT', body: { title } })
}

export function validateSession() {
  return request('/auth/me')
}

export function getFeedback(chatId, turnNum) {
  return request(`/feedback/${chatId}/${turnNum}`)
}

export function updateRating(chatId, turnNum, rating) {
  return request(`/feedback/${chatId}/${turnNum}/rating`, {
    method: 'PUT', body: { rating }
  })
}

export function updateFeedbackText(chatId, turnNum, feedbackText) {
  return request(`/feedback/${chatId}/${turnNum}/text`, {
    method: 'PUT', body: { feedbackText }
  })
}
