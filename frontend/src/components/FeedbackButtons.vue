<template>
  <div style="display: flex; align-items: center; gap: 4px; margin-top: 6px; margin-left: 4px;">
    <span style="font-size: 11px; color: var(--n-text-color-3); margin-right: 2px;">反馈:</span>
    <n-button
      size="tiny"
      :type="rating === 'up' ? 'success' : 'default'"
      :disabled="readonly && rating !== 'up'"
      @click="toggleRating('up')"
      title="有帮助"
      style="font-size: 16px;"
    >
      👍
    </n-button>
    <n-button
      size="tiny"
      :type="rating === 'down' ? 'error' : 'default'"
      :disabled="readonly && rating !== 'down'"
      @click="toggleRating('down')"
      title="不满意"
      style="font-size: 16px;"
    >
      👎
    </n-button>
    <n-button
      size="tiny"
      :type="hasFeedbackText ? 'warning' : 'default'"
      @click="openModal"
      title="写反馈"
      style="font-size: 16px;"
    >
      ✏️
    </n-button>

    <FeedbackModal
      :show="showModal"
      :feedback-text="localFeedbackText"
      :readonly="readonly"
      @update:show="showModal = $event"
      @submit="submitFeedbackText"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { NButton } from 'naive-ui'
import { getFeedback, updateRating, updateFeedbackText } from '../api'
import FeedbackModal from './FeedbackModal.vue'

const props = defineProps({
  chatId: { type: String, required: true },
  turnNum: { type: Number, required: true },
  readonly: { type: Boolean, default: false }
})

const rating = ref(null)
const hasFeedbackText = ref(false)
const localFeedbackText = ref('')
const showModal = ref(false)
const loading = ref(false)

onMounted(async () => {
  console.log('[FeedbackButtons] mounted, chatId:', props.chatId, 'turnNum:', props.turnNum, 'readonly:', props.readonly)
  if (!props.chatId || !props.turnNum) return
  try {
    const fb = await getFeedback(props.chatId, props.turnNum)
    console.log('[FeedbackButtons] loaded feedback:', fb)
    rating.value = fb.rating || null
    localFeedbackText.value = fb.feedbackText || ''
    hasFeedbackText.value = !!fb.feedbackText
  } catch (e) {
    console.log('[FeedbackButtons] load feedback failed:', e.message || e)
  }
})

async function toggleRating(newRating) {
  if (props.readonly) return
  if (loading.value) return
  loading.value = true
  try {
    if (rating.value === newRating) {
      await updateRating(props.chatId, props.turnNum, '')
      rating.value = null
    } else {
      await updateRating(props.chatId, props.turnNum, newRating)
      rating.value = newRating
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openModal() {
  if (!props.readonly) {
    getFeedback(props.chatId, props.turnNum).then(fb => {
      localFeedbackText.value = fb.feedbackText || ''
      hasFeedbackText.value = !!fb.feedbackText
    }).catch(() => {})
  }
  showModal.value = true
}

async function submitFeedbackText(text) {
  if (props.readonly) return
  if (!text.trim()) return
  try {
    await updateFeedbackText(props.chatId, props.turnNum, text)
    localFeedbackText.value = text
    hasFeedbackText.value = true
  } catch { /* ignore */ }
}
</script>
