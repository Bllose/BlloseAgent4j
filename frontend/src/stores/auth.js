import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, guestLogin as apiGuestLogin, helloGuest as apiHelloGuest } from '../api'
import { generateFingerprint } from '../utils/fingerprint'

export const useAuthStore = defineStore('auth', () => {
  const sessionId = ref(localStorage.getItem('sessionId') || null)
  const username = ref(localStorage.getItem('username') || null)
  const guestInitializing = ref(false)

  const isLoggedIn = computed(() => !!sessionId.value)
  const isGuest = computed(() => username.value === 'Guest')

  async function doLogin(user, pass, fingerprint = '') {
    const data = await apiLogin(user, pass, fingerprint)
    sessionId.value = data.sessionId
    username.value = data.username
    localStorage.setItem('sessionId', data.sessionId)
    localStorage.setItem('username', data.username)
    return data
  }

  async function doRegister(user, pass, fingerprint = '') {
    const data = await apiRegister(user, pass, fingerprint)
    sessionId.value = data.sessionId
    username.value = data.username
    localStorage.setItem('sessionId', data.sessionId)
    localStorage.setItem('username', data.username)
    return data
  }

  async function doGuestLogin() {
    if (guestInitializing.value) return
    guestInitializing.value = true
    try {
      const fp = await generateFingerprint()
      localStorage.setItem('fingerprint', fp.fingerprint)
      const data = await apiGuestLogin(fp)
      sessionId.value = data.sessionId
      username.value = data.username
      localStorage.setItem('sessionId', data.sessionId)
      localStorage.setItem('username', data.username)
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
      sessionId.value = data.sessionId
      username.value = data.username
      localStorage.setItem('sessionId', data.sessionId)
      localStorage.setItem('username', data.username)
      // generate fingerprint in background for subsequent X-Fingerprint fallback
      generateFingerprint().then(fp => {
        localStorage.setItem('fingerprint', fp.fingerprint)
      }).catch(() => {})
    } catch {
      // silent fail
    }
  }

  function logout() {
    sessionId.value = null
    username.value = null
    localStorage.removeItem('sessionId')
    localStorage.removeItem('username')
    localStorage.removeItem('fingerprint')
  }

  return { sessionId, username, isLoggedIn, isGuest, doLogin, doRegister, doGuestLogin, initGuestSession, logout }
})
