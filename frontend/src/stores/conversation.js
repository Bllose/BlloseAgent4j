import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listConversations, getConversationMessages } from '../api'
import { useAuthStore } from './auth'

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref([])
  const currentChatId = ref(null)
  const messages = ref([])
  const loading = ref(false)

  const auth = useAuthStore()
  const isRegistered = computed(() => auth.isLoggedIn && !auth.isGuest)

  async function loadConversations() {
    console.log('[convStore] loadConversations called, isRegistered:', isRegistered.value, 'isLoggedIn:', auth.isLoggedIn, 'isGuest:', auth.isGuest, 'sessionId:', !!localStorage.getItem('sessionId'))
    if (!isRegistered.value) {
      console.log('[convStore] loadConversations skipped: not registered')
      return
    }
    try {
      const result = await listConversations()
      console.log('[convStore] loadConversations success:', result.length, 'conversations', result)
      conversations.value = result
    } catch (e) {
      console.error('[convStore] loadConversations failed:', e.message || e)
    }
  }

  async function selectConversation(chatId) {
    currentChatId.value = chatId
    messages.value = []
    if (!isRegistered.value || !chatId) return
    loading.value = true
    try {
      const data = await getConversationMessages(chatId, 3)
      const msgs = []
      for (const m of data) {
        if (m.type === 'user') {
          msgs.push({ role: 'user', content: m.message, turnNum: m.turnNum })
        } else {
          msgs.push({
            role: 'assistant',
            thinking: m.thinking || '',
            content: m.message || '',
            streaming: false,
            thinkingCollapsed: true,
            toolCalls: [],
            downloads: [],
            turnNum: m.turnNum,
            isHistory: true,
          })
        }
      }
      messages.value = msgs
    } catch { /* silent */ }
    finally { loading.value = false }
  }

  function newConversation() {
    currentChatId.value = null
    messages.value = []
  }

  function setChatId(chatId) {
    currentChatId.value = chatId
    if (chatId && !conversations.value.find(c => c.chatId === chatId)) {
      loadConversations()
    }
  }

  function addMessage(msg) {
    messages.value.push(msg)
  }

  function updateLastAssistant(updates) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      Object.assign(last, updates)
    }
  }

  return {
    conversations, currentChatId, messages, loading, isRegistered,
    loadConversations, selectConversation, newConversation, setChatId,
    addMessage, updateLastAssistant
  }
})
