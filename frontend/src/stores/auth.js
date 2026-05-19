import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister } from '../api'

export const useAuthStore = defineStore('auth', () => {
  const sessionId = ref(localStorage.getItem('sessionId') || null)
  const username = ref(localStorage.getItem('username') || null)

  const isLoggedIn = computed(() => !!sessionId.value)

  async function doLogin(user, pass) {
    const data = await apiLogin(user, pass)
    sessionId.value = data.sessionId
    username.value = data.username
    localStorage.setItem('sessionId', data.sessionId)
    localStorage.setItem('username', data.username)
    return data
  }

  async function doRegister(user, pass) {
    const data = await apiRegister(user, pass)
    sessionId.value = data.sessionId
    username.value = data.username
    localStorage.setItem('sessionId', data.sessionId)
    localStorage.setItem('username', data.username)
    return data
  }

  function logout() {
    sessionId.value = null
    username.value = null
    localStorage.removeItem('sessionId')
    localStorage.removeItem('username')
  }

  return { sessionId, username, isLoggedIn, doLogin, doRegister, logout }
})
