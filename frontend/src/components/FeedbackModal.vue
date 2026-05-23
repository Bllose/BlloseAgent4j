<template>
  <n-modal
    :show="show"
    @update:show="emit('update:show', $event)"
  >
    <n-card
      style="width: 500px;"
      title="对当前对话进行反馈"
      :bordered="true"
      size="huge"
      role="dialog"
    >
      <template #header-extra>
        <n-button text @click="emit('update:show', false)">✕</n-button>
      </template>
      <n-input
        v-model:value="localText"
        type="textarea"
        :disabled="readonly"
        placeholder="请输入您的反馈意见..."
        :autosize="{ minRows: 4, maxRows: 12 }"
      />
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button v-if="!readonly" type="primary" @click="handleSubmit">提交</n-button>
          <n-button @click="emit('update:show', false)">{{ readonly ? '关闭' : '取消' }}</n-button>
        </div>
      </template>
    </n-card>
  </n-modal>
</template>

<script setup>
import { ref, watch } from 'vue'
import { NModal, NCard, NInput, NButton } from 'naive-ui'

const props = defineProps({
  show: Boolean,
  feedbackText: { type: String, default: '' },
  readonly: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show', 'submit'])

const localText = ref('')

watch(() => props.show, (val) => {
  if (val) {
    localText.value = props.feedbackText || ''
  }
})

function handleSubmit() {
  emit('submit', localText.value)
  emit('update:show', false)
}
</script>
