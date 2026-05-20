<template>
  <div style="height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
    <n-card style="width: 400px;">
      <h2 style="text-align: center; margin-bottom: 24px; font-size: 24px;">
        {{ isRegister ? 'Create Account' : 'Welcome Back' }}
      </h2>
      <n-form ref="formRef" :model="form" :rules="rules">
        <n-form-item path="username" label="Username">
          <n-input v-model:value="form.username" placeholder="Enter username" size="large" />
        </n-form-item>
        <n-form-item path="password" label="Password">
          <n-input v-model:value="form.password" type="password" placeholder="Enter password" size="large" @keyup.enter="handleSubmit" />
        </n-form-item>
        <n-button type="primary" block size="large" :loading="loading" @click="handleSubmit">
          {{ isRegister ? 'Register' : 'Login' }}
        </n-button>
      </n-form>
      <div v-if="isRegister" style="margin-top: 12px;">
        <n-button text block @click="goBackAsGuest">
          不注册，直接使用
        </n-button>
      </div>
      <n-divider />
      <n-button text block @click="isRegister = !isRegister">
        {{ isRegister ? 'Already have an account? Login' : "Don't have an account? Register" }}
      </n-button>
    </n-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()

const isRegister = ref(false)
const loading = ref(false)
const form = ref({ username: '', password: '' })
const formRef = ref(null)

const rules = {
  username: [
    { required: true, message: 'Username is required', trigger: 'blur' },
    { min: 3, message: 'Min 3 characters', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Password is required', trigger: 'blur' },
    { min: 6, message: 'Min 6 characters', trigger: 'blur' }
  ]
}

onMounted(() => {
  if (route.query.register === 'true') {
    isRegister.value = true
  }
})

function goBackAsGuest() {
  router.push({ name: 'Chat' })
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    loading.value = true
    const fingerprint = localStorage.getItem('fingerprint') || ''
    if (isRegister.value) {
      await authStore.doRegister(form.value.username, form.value.password, fingerprint)
      message.success('Registration successful')
    } else {
      await authStore.doLogin(form.value.username, form.value.password, fingerprint)
      message.success('Login successful')
    }
    router.push({ name: 'Chat' })
  } catch (e) {
    message.error(e.message || 'Operation failed')
  } finally {
    loading.value = false
  }
}
</script>
