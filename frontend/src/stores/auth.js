import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, guestLogin as apiGuestLogin, helloGuest as apiHelloGuest, validateSession } from '../api'
import { generateFingerprint } from '../utils/fingerprint'

export const useAuthStore = defineStore('auth', () => {
  const sessionId = ref(localStorage.getItem('sessionId') || null)
  const username = ref(localStorage.getItem('username') || null)
  const userNumber = ref(localStorage.getItem('userNumber') ? Number(localStorage.getItem('userNumber')) : null)
  const guestInitializing = ref(false)

  const isLoggedIn = computed(() => !!sessionId.value)
  const isGuest = computed(() => username.value === 'Guest')

  function clearAuth() {
    sessionId.value = null
    username.value = null
    userNumber.value = null
    localStorage.removeItem('sessionId')
    localStorage.removeItem('username')
    localStorage.removeItem('userNumber')
  }

  function setAuth(data) {
    sessionId.value = data.sessionId
    username.value = data.username
    userNumber.value = data.userNumber || null
    localStorage.setItem('sessionId', data.sessionId)
    localStorage.setItem('username', data.username)
    if (data.userNumber) localStorage.setItem('userNumber', data.userNumber)
  }

  async function initAuth() {
    // Validate existing session on page load
    if (sessionId.value && username.value && username.value !== 'Guest') {
      try {
        const result = await validateSession()
        if (!result.valid) {
          console.log('[auth] stored session expired, restarting as guest')
          clearAuth()
        } else {
          // session valid, update userNumber if changed
          if (result.userNumber && userNumber.value !== result.userNumber) {
            userNumber.value = result.userNumber
            localStorage.setItem('userNumber', result.userNumber)
          }
        }
      } catch {
        // can't reach server, keep stored state (works when backend comes up)
      }
    }
    // Start guest session if not logged in
    if (!sessionId.value) {
      await initGuestSession()
    }
  }

  async function doLogin(user, pass, fingerprint = '') {
    const data = await apiLogin(user, pass, fingerprint)
    setAuth(data)
    import('./conversation.js').then(m => m.useConversationStore().loadConversations())
    return data
  }

  async function doRegister(user, pass, fingerprint = '') {
    const data = await apiRegister(user, pass, fingerprint)
    setAuth(data)
    import('./conversation.js').then(m => m.useConversationStore().loadConversations())
    return data
  }

  async function doGuestLogin() {
    if (guestInitializing.value) return
    guestInitializing.value = true
    try {
      const fp = await generateFingerprint()
      localStorage.setItem('fingerprint', fp.fingerprint)
      const data = await apiGuestLogin(fp)
      setAuth(data)
    } catch {
      // silent fail
    } finally {
      guestInitializing.value = false
    }
  }

  async function initGuestSession() {
    if (sessionId.value) return
    try {
      const data = await apiHelloGuest()
      setAuth(data)
      generateFingerprint().then(fp => {
        localStorage.setItem('fingerprint', fp.fingerprint)
      }).catch(() => {})
    } catch {
      // silent fail
    }
  }

  function logout() {
    clearAuth()
    localStorage.removeItem('fingerprint')
    initGuestSession()
  }

  return { sessionId, username, userNumber, isLoggedIn, isGuest, initAuth, doLogin, doRegister, doGuestLogin, initGuestSession, logout }
})
