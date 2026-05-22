<template>
  <div style="display: flex; flex-direction: column; height: 100%;">
    <div ref="msgContainer" style="flex: 1; overflow-y: auto; padding: 8px; position: relative;">
      <div v-for="(msg, idx) in convStore.messages" :key="idx" style="margin-bottom: 16px;">
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
            >{{ displayThinking(msg.thinking) }}</div>
          </div>
          <!-- Response section -->
          <n-card v-if="msg.content" size="small" style="max-width: 100%;">
            <div style="font-size: 12px; color: var(--n-text-color-3); margin-bottom: 4px;">Response</div>
            <div class="markdown-body" v-html="renderContent(msg.content)" />
          </n-card>
          <!-- Downloads -->
          <div v-if="msg.downloads && msg.downloads.length" style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px;">
            <a
              v-for="dl in msg.downloads" :key="dl.filename"
              :href="dl.url"
              target="_blank"
              style="display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px; background: var(--n-color-target); border: 1px solid var(--n-border-color); border-radius: 4px; text-decoration: none; color: var(--n-text-color); font-size: 13px;"
            >
              <span style="font-size: 16px;">📥</span>
              {{ dl.filename }}
            </a>
          </div>
          <n-spin v-if="msg.streaming" size="small" style="margin-left: 8px; margin-top: 4px;" />
        </div>
      </div>
      <div v-if="convStore.messages.length === 0" style="position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; padding: 0 48px;">
        <div style="text-align: center;">
          <!-- Decorative icon with glow -->
          <div style="position: relative; display: inline-block; margin-bottom: 20px;">
            <div style="position: absolute; inset: -20px; background: radial-gradient(circle, rgba(102,126,234,0.15) 0%, transparent 70%); border-radius: 50%;"></div>
            <span style="font-size: 52px; position: relative;">📚</span>
          </div>
          <p style="font-size: 24px; font-weight: 700; margin-bottom: 6px; color: var(--n-text-color); letter-spacing: 1px;">欢迎使用 Bllose 论文检索助手</p>
          <p style="font-size: 14px; margin-bottom: 24px; color: var(--n-text-color-3);">告诉我你想了解的方向，我来帮你找论文</p>

          <!-- Quick feature hints -->
          <div style="display: flex; justify-content: center; gap: 24px; margin-bottom: 24px; font-size: 13px; color: var(--n-text-color-3);">
            <div style="display: flex; align-items: center; gap: 6px;"><span>🔍</span> 智能检索</div>
            <div style="display: flex; align-items: center; gap: 6px;"><span>📥</span> 论文下载</div>
            <div style="display: flex; align-items: center; gap: 6px;"><span>📖</span> 元数据提取</div>
          </div>

          <!-- Example queries -->
          <div style="display: inline-block; background: var(--n-color-target); border: 1px solid var(--n-border-color); border-radius: 10px; padding: 18px 28px; text-align: left; font-size: 14px; line-height: 2.2; color: var(--n-text-color-2);">
            <div style="margin-bottom: 8px; font-weight: 600; color: var(--n-text-color-3); font-size: 13px; letter-spacing: 0.5px;">💡 试试这么说：</div>
            <div>🔹 帮我找近五年关于<mark style="background: rgba(102,126,234,0.12); color: var(--n-color-target-checked); padding: 2px 6px; border-radius: 3px; font-weight: 500;">大语言模型注意力机制</mark>的论文</div>
            <div>🔹 我想了解 <mark style="background: rgba(102,126,234,0.12); color: var(--n-color-target-checked); padding: 2px 6px; border-radius: 3px; font-weight: 500;">多模态学习</mark>的最新研究进展</div>
            <div>🔹 有没有关于 <mark style="background: rgba(102,126,234,0.12); color: var(--n-color-target-checked); padding: 2px 6px; border-radius: 3px; font-weight: 500;">知识蒸馏</mark>的综述文章？</div>
          </div>
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
import { useConversationStore } from '../stores/conversation'

marked.use({
  renderer: {
    link({ href, title, text }) {
      const titleAttr = title ? ` title="${title}"` : ''
      return `<a href="${href}" target="_blank" rel="noopener noreferrer"${titleAttr}>${text}</a>`
    }
  }
})

const message = useMessage()
const convStore = useConversationStore()
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

  convStore.messages.push({ role: 'user', content: text })
  scrollToBottom()

  const assistantMsg = {
    role: 'assistant',
    thinking: '',
    content: '',
    streaming: true,
    thinkingCollapsed: false,
    toolCalls: [],
    downloads: [],
  }
  convStore.messages.push(assistantMsg)
  const idx = convStore.messages.length - 1
  scrollToBottom()

  isStreaming.value = true
  abortController = new AbortController()

  const ep = currentEndpoint()

  try {
    if (ep.mode === 'invoke') {
      const data = await invokeChat(text, ep.value, convStore.currentChatId)
      convStore.messages[idx].content = data.content || ''
      if (data.chatId) convStore.setChatId(data.chatId)
    } else {
      const response = await streamChat(text, ep.value, convStore.currentChatId)
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
            let payload = data
            try {
              const parsed = JSON.parse(data)
              if (parsed.t !== undefined) payload = parsed.t
              else payload = parsed
            } catch (_) { /* 裸字符串 */ }
            const cur = convStore.messages[idx]
            if (eventName === 'chatId') {
              convStore.setChatId(payload)
            } else if (eventName === 'thinking') {
              cur.thinking += payload
            } else if (eventName === 'tool') {
              cur.toolCalls.push(payload)
            } else if (eventName === 'message') {
              cur.content += payload
            } else if (eventName === 'download') {
              cur.downloads.push(payload)
            } else if (eventName === 'error') {
              cur.content += '\n\n> ⚠️ ' + payload
              cur.streaming = false
            } else if (eventName === 'done') {
              cur.streaming = false
              cur.thinking = cleanupThinking(cur.thinking)
              cur.content = normalizeContent(cur.content)
            }
          }
        }
        scrollToBottom()
      }
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      message.error(e.message || 'Chat error')
      if (convStore.messages[idx]) {
        convStore.messages[idx].content += ' [Error: ' + (e.message || 'unknown') + ']'
      }
    }
  } finally {
    if (convStore.messages[idx]) {
      convStore.messages[idx].streaming = false
    }
    isStreaming.value = false
    abortController = null
    scrollToBottom()
  }
}

function cleanupThinking(text) {
  if (!text) return ''
  let filtered = text.replace(/^\s*[─━═]+.*[─━═]+\s*$/gm, '')
  filtered = filtered.replace(/^\s*[─━═]{3,}\s*$/gm, '')
  filtered = filtered.replace(/\n{3,}/g, '\n\n')
  return filtered.trim()
}

function displayThinking(text) {
  return cleanupThinking(text)
}

function normalizeContent(text) {
  if (!text) return ''
  return text.replace(/\n{3,}/g, '\n\n')
}

function renderContent(text) {
  let fixed = text.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
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
