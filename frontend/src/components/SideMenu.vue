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
import { ref, h, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ChatbubblesOutline, LogOutOutline } from '@vicons/ionicons5'
import { checkHealth } from '../api'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const activeKey = ref(route.name || 'Chat')

const health = ref({ status: null }) // null=loading, 'UP'|'DEGRADED'|'DOWN'

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
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

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

const menuOptions = [
  { label: 'Chat', key: 'Chat', icon: () => h(NIcon, null, { default: () => h(ChatbubblesOutline) }) },
  { label: 'Logout', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }) }
]

function onMenuSelect(key) {
  if (key === 'logout') {
    localStorage.clear()
    router.push('/login')
  } else {
    activeKey.value = key
    router.push({ name: key })
  }
}
</script>
