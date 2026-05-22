<template>
  <n-message-provider>
    <n-notification-provider>
      <router-view />
    </n-notification-provider>
  </n-message-provider>
</template>

<script setup>
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
auth.initAuth()
</script>
