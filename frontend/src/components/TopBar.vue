<template>
  <n-header style="height: 46px; padding: 0 12px; display: flex; align-items: center; border-bottom: 1px solid var(--n-border-color);">
    <n-tabs
      type="card"
      :value="activeTab"
      :closable="tabs.length > 1"
      @close="closeTab"
      @update:value="switchTab"
      style="flex: 1;"
    >
      <n-tab-pane v-for="tab in tabs" :key="tab.key" :name="tab.key" :tab="tab.label" />
    </n-tabs>
    <div style="margin-left: auto; display: flex; align-items: center; gap: 8px;">
      <span style="color: var(--n-text-color-3); font-size: 14px;">{{ username }}</span>
      <n-button v-if="username === 'Guest'" size="tiny" secondary @click="goRegister">
        注册
      </n-button>
      <n-button v-if="username === 'Guest'" size="tiny" secondary @click="goLogin">
        登录
      </n-button>
      <n-button v-else-if="username" size="tiny" secondary @click="goLogout">
        登出
      </n-button>
    </div>
  </n-header>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const tabs = ref([])
const activeTab = ref('')
const username = ref(auth.username || '')

watch(() => auth.username, (val) => {
  username.value = val || ''
})

function goRegister() {
  router.push({ name: 'Login', query: { register: 'true' } })
}

function goLogin() {
  router.push({ name: 'Login' })
}

async function goLogout() {
  auth.logout()
  window.location.href = '/'
}

function ensureTab(key, label) {
  if (!tabs.value.find(t => t.key === key)) {
    tabs.value.push({ key, label })
  }
  activeTab.value = key
}

function switchTab(key) {
  router.push({ name: key })
}

function closeTab(key) {
  tabs.value = tabs.value.filter(t => t.key !== key)
  if (activeTab.value === key && tabs.value.length > 0) {
    const last = tabs.value[tabs.value.length - 1]
    router.push({ name: last.key })
  }
}

watch(() => route.name, (name) => {
  if (name) ensureTab(name, name)
}, { immediate: true })
</script>
