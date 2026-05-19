<template>
  <div style="display: flex; flex-direction: column; height: 100%;">
    <div ref="msgContainer" style="flex: 1; overflow-y: auto; padding: 8px;">
      <div v-for="(msg, idx) in messages" :key="idx" style="margin-bottom: 16px;">
        <div v-if="msg.role === 'user'" style="display: flex; justify-content: flex-end;">
          <n-card size="small" style="max-width: 70%; background: var(--n-color-target-checked);">
            {{ msg.content }}
          </n-card>
        </div>
        <div v-else style="display: flex; flex-direction: column; align-items: flex-start; max-width: 100%;">
          <!-- Thinking section -->
          <div v-if="msg.thinking" style="width: 100%; margin-bottom: 6px;">
            <div
              @click="msg.thinkingCollapsed = !msg.thinkingCollapsed"
              style="cursor: pointer; font-size: 12px; color: var(--n-text-color-3); margin-bottom: 2px; user-select: none; display: flex; align-items: center; gap: 4px;"
            >
              <span :style="{ transform: msg.thinkingCollapsed ? 'rotate(-90deg)' : 'rotate(0deg)', transition: 'transform 0.15s', display: 'inline-block' }">▼</span>
              Thinking
              <span v-if="msg.toolCalls && msg.toolCalls.length" style="opacity: 0.7;">
                ({{ msg.toolCalls.length }} tool call{{ msg.toolCalls.length > 1 ? 's' : '' }})
              </span>
            </div>
            <div
              v-show="!msg.thinkingCollapsed"
              style="font-size: 13px; line-height: 1.6; color: var(--n-text-color-3); white-space: pre-wrap; border-left: 3px solid #f0a020; padding: 8px 12px; background: var(--n-color-target); border-radius: 0 4px 4px 0; max-height: 400px; overflow-y: auto;"
            >{{ msg.thinking }}</div>
          </div>
          <!-- Response section -->
          <n-card v-if="msg.content" size="small" style="max-width: 100%;">
            <div style="font-size: 12px; color: var(--n-text-color-3); margin-bottom: 4px;">Response</div>
            <div class="markdown-body" v-html="renderContent(msg.content)" />
          </n-card>
          <n-spin v-if="msg.streaming" size="small" style="margin-left: 8px; margin-top: 4px;" />
        </div>
      </div>
      <div ref="bottomAnchor" />
    </div>

    <div style="border-top: 1px solid var(--n-border-color); padding: 12px; display: flex; gap: 10px; align-items: flex-end;">
      <n-input
        v-model:value="inputText"
        placeholder="Type a message... (Enter to send)"
        :disabled="isStreaming"
        @keyup.enter.exact="sendMessage"
        size="large"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        style="flex: 1;"
      />
      <n-select
        v-model:value="selectedEndpoint"
        :options="endpoints"
        :disabled="isStreaming"
        size="large"
        style="width: 170px; flex-shrink: 0;"
      />
      <n-button type="primary" :disabled="isStreaming || !inputText.trim()" @click="sendMessage" size="large" style="flex-shrink: 0;">
        Send
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { useMessage } from 'naive-ui'
import { marked } from 'marked'
import { streamChat, invokeChat } from '../api'

const message = useMessage()
const messages = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const msgContainer = ref(null)
const bottomAnchor = ref(null)
let abortController = null

const endpoints = [
  { label: 'Paper (流式)', value: '/chat/v1/paper', mode: 'stream' },
  { label: 'Paper (非流式)', value: '/chat/v1/paper/invoke', mode: 'invoke' },
  { label: '通用 (流式)', value: '/chat/v1/stream', mode: 'stream' },
]
const selectedEndpoint = ref(endpoints[0].value)

function scrollToBottom() {
  nextTick(() => {
    bottomAnchor.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

function currentEndpoint() {
  return endpoints.find(e => e.value === selectedEndpoint.value) || endpoints[0]
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return
  inputText.value = ''

  messages.value.push({ role: 'user', content: text })
  scrollToBottom()

  const assistantMsg = {
    role: 'assistant',
    thinking: '',
    content: '',
    streaming: true,
    thinkingCollapsed: false,
    toolCalls: [],
  }
  messages.value.push(assistantMsg)
  const idx = messages.value.length - 1
  scrollToBottom()

  isStreaming.value = true
  abortController = new AbortController()

  const ep = currentEndpoint()

  try {
    if (ep.mode === 'invoke') {
      // ── Non-streaming (invoke) path ──
      const data = await invokeChat(text, ep.value)
      messages.value[idx].content = data.content || ''
    } else {
      // ── Streaming (SSE) path ──
      const response = await streamChat(text, ep.value)
      if (!response.ok) {
        const errText = await response.text()
        throw new Error(errText || 'Stream failed')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split('\n\n')
        buffer = blocks.pop() || ''

        for (const block of blocks) {
          let eventName = ''
          let data = ''
          const lines = block.split('\n')
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              let d = line.slice(5)
              if (d.startsWith(' ')) d = d.slice(1)
              data += (data ? '\n' : '') + d
            }
          }
          if (eventName && data) {
            // 兼容 JSON 包装格式 {"t":"..."} 和裸字符串格式
            let payload = data
            try {
              const parsed = JSON.parse(data)
              if (parsed.t !== undefined) payload = parsed.t
            } catch (_) { /* 裸字符串，直接使用 */ }
            const cur = messages.value[idx]
            if (eventName === 'thinking') {
              cur.thinking += payload
            } else if (eventName === 'tool') {
              cur.toolCalls.push(payload)
              cur.thinking += '\n\n──  Calling: ' + payload + '  ──\n\n'
            } else if (eventName === 'message') {
              cur.content += payload
            } else if (eventName === 'done') {
              cur.streaming = false
            }
          }
        }
        scrollToBottom()
      }
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      message.error(e.message || 'Chat error')
      if (messages.value[idx]) {
        messages.value[idx].content += ' [Error: ' + (e.message || 'unknown') + ']'
      }
    }
  } finally {
    if (messages.value[idx]) {
      messages.value[idx].streaming = false
    }
    isStreaming.value = false
    abortController = null
    scrollToBottom()
  }
}

function renderContent(text) {
  // Fix common LLM markdown formatting issues:
  // 1. Ensure space after # for ATX headings (##text → ## text)
  let fixed = text.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  // 2. Ensure blank line before table rows so marked can detect them
  fixed = fixed.replace(/([^\n|])\n(\|[^\n]+\|)/g, '$1\n\n$2')
  return marked.parse(fixed)
}

onBeforeUnmount(() => {
  if (abortController) abortController.abort()
})
</script>

<style scoped>
.markdown-body :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
}

.markdown-body :deep(code) {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 13px;
}

.markdown-body :deep(p) {
  margin: 0 0 8px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--n-border-color);
  padding-left: 12px;
  margin: 8px 0;
  color: var(--n-text-color-3);
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--n-border-color);
  padding: 6px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--n-color-target);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--n-border-color);
  margin: 12px 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 8px 0;
}

.markdown-body :deep(a) {
  color: var(--n-color-target-checked);
}
</style>
