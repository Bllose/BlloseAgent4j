<template>
  <n-layout-sider bordered v-model:collapsed="collapsed" collapse-mode="width" :collapsed-width="64" :width="220" show-trigger>
    <div style="padding: 16px; display: flex; flex-direction: column; align-items: center; gap: 8px;">
      <n-avatar :size="48" style="background: #18a058;">
        <n-icon size="28"><ChatbubblesOutline /></n-icon>
      </n-avatar>
      <h3 v-if="!collapsed" style="margin: 0; font-size: 16px; white-space: nowrap;">Bllose Agent</h3>
      <span v-if="!collapsed" style="font-size: 12px; color: var(--n-text-color-3); text-align: center;">
        AI-powered assistant
      </span>
    </div>
    <n-menu :options="menuOptions" :value="activeKey" @update:value="onMenuSelect" />
  </n-layout-sider>
</template>

<script setup>
import { ref, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ChatbubblesOutline, LogOutOutline } from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const activeKey = ref(route.name || 'Chat')

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
