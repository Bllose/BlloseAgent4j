<template>
  <n-layout-sider bordered v-model:collapsed="collapsed" collapse-mode="width" :collapsed-width="64" :width="220" show-trigger>
    <div style="padding: 16px; display: flex; flex-direction: column; align-items: center; gap: 8px;">
      <n-avatar :size="48" style="background: #18a058;">
        <n-icon size="28"><ChatbubblesOutline /></n-icon>
      </n-avatar>
      <h3 v-if="!collapsed" style="margin: 0; font-size: 16px; white-space: nowrap;">论文检索</h3>
      <span v-if="!collapsed" style="font-size: 12px; color: var(--n-text-color-3); text-align: center;">
        AI-powered assistant
      </span>
      <div v-if="!collapsed" style="display: flex; align-items: center; gap: 6px; font-size: 12px;">
        <span :style="{ width: '8px', height: '8px', borderRadius: '50%', background: dotColor(), display: 'inline-block', flexShrink: 0 }" />
        <span style="color: var(--n-text-color-3);">{{ statusLabel() }}</span>
      </div>
    </div>
    <n-menu :options="menuOptions" :value="activeKey" @update:value="onMenuSelect" />
  </n-layout-sider>
</template>

<script setup>
import { ref, h, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ChatbubblesOutline, TimeOutline, AddCircleOutline } from '@vicons/ionicons5'
import { checkHealth } from '../api'
import { useAuthStore } from '../stores/auth'
import { useConversationStore } from '../stores/conversation'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const activeKey = ref(route.name || 'Chat')

const auth = useAuthStore()
const convStore = useConversationStore()

const health = ref({ status: null })

let timer = null

async function fetchHealth() {
  try {
    const data = await checkHealth()
    health.value = data
  } catch {
    health.value = { status: 'DOWN' }
  }
}

onMounted(() => {
  fetchHealth()
  timer = setInterval(fetchHealth, 30_000)
  if (auth.isLoggedIn && !auth.isGuest) {
    convStore.loadConversations()
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

watch(() => auth.isLoggedIn && !auth.isGuest, (isReg) => {
  if (isReg) convStore.loadConversations()
}, { immediate: true })

function dotColor() {
  if (health.value.status === 'UP') return '#18a058'
  if (health.value.status === 'DEGRADED') return '#f0a020'
  return '#d03050'
}

function statusLabel() {
  if (health.value.status === 'UP') return '服务正常'
  if (health.value.status === 'DEGRADED') return '服务异常'
  if (health.value.status === 'DOWN') return '服务未启动'
  return '检测中...'
}

const menuOptions = computed(() => {
  const items = [
    { label: 'Chat', key: 'Chat', icon: () => h(NIcon, null, { default: () => h(ChatbubblesOutline) }) },
    { label: '+ 新建会话', key: 'new-chat', icon: () => h(NIcon, null, { default: () => h(AddCircleOutline) }) },
  ]
  if (auth.isLoggedIn && !auth.isGuest && convStore.conversations.length > 0) {
    items.push({ type: 'divider', key: 'conv-divider' })
    for (const c of convStore.conversations) {
      items.push({
        label: c.title || '未命名对话',
        key: `conv:${c.chatId}`,
        icon: () => h(NIcon, null, { default: () => h(TimeOutline) })
      })
    }
  }
  return items
})

function onMenuSelect(key) {
  activeKey.value = key
  if (key === 'new-chat') {
    convStore.newConversation()
    activeKey.value = 'Chat'
    router.push({ name: 'Chat' })
  } else if (key.startsWith('conv:')) {
    const chatId = key.slice(5)
    convStore.selectConversation(chatId)
    router.push({ name: 'Chat' })
  } else {
    convStore.newConversation()
    router.push({ name: key })
  }
}
</script>
