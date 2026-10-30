<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAssistantStore } from '@/stores/assistant'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  tools?: string[]
  usage?: { prompt: number; completion: number }
}

const TOOL_LABELS: Record<string, string> = {
  list_shop_types: '查询分类',
  search_shop_by_name: '搜索店铺',
  list_shops_by_type: '浏览分类店铺',
  find_nearby_shops: '查找附近店铺',
  get_shop_detail: '查看店铺详情',
  list_vouchers_of_shop: '查询优惠券',
  list_hot_blogs: '浏览热门笔记',
  get_blog_detail: '阅读探店笔记',
  semantic_search_shops: '语义检索店铺'
}

const assistant = useAssistantStore()
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)

function costText(usage: { prompt: number; completion: number }) {
  const yuan = (usage.prompt / 1_000_000) * 3 + (usage.completion / 1_000_000) * 9
  return `本次 ${usage.prompt + usage.completion} tokens · 约 ¥${yuan.toFixed(4)}`
}

const SESSION_KEY = 'hmdp_agent_session'

function sessionId() {
  let id = localStorage.getItem(SESSION_KEY)
  if (!id) {
    id = `web-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
    localStorage.setItem(SESSION_KEY, id)
  }
  return id
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

async function send(text?: string) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) {
    return
  }
  input.value = ''
  messages.value.push({ role: 'user', content })
  const reply: ChatMessage = { role: 'assistant', content: '', streaming: true }
  messages.value.push(reply)
  sending.value = true
  scrollToBottom()

  const token = localStorage.getItem('hmdp_token')
  try {
    const response = await fetch('/api/agent/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { authorization: token } : {})
      },
      body: JSON.stringify({ sessionId: sessionId(), message: content })
    })
    if (!response.ok || !response.body) {
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const line = block.split('\n').find((item) => item.startsWith('data:'))
        if (!line) {
          continue
        }
        let event: { type: string; content: string }
        try {
          event = JSON.parse(line.slice(5).trim())
        } catch {
          continue
        }
        if (event.type === 'delta') {
          reply.content += event.content
          scrollToBottom()
        } else if (event.type === 'tool') {
          reply.tools = reply.tools || []
          reply.tools.push(TOOL_LABELS[event.content] || event.content)
          scrollToBottom()
        } else if (event.type === 'usage') {
          const [prompt, completion] = event.content.split(',').map((item) => Number(item) || 0)
          reply.usage = { prompt, completion }
        } else if (event.type === 'session') {
          localStorage.setItem(SESSION_KEY, event.content)
        } else if (event.type === 'error') {
          reply.content = reply.content || `出错了：${event.content}`
        }
      }
    }
    if (!reply.content) {
      reply.content = '没有拿到回复，请稍后重试'
    }
  } catch (error) {
    reply.content = `请求失败：${(error as Error).message}`
    ElMessage.error('AI 助手请求失败')
  } finally {
    reply.streaming = false
    sending.value = false
    scrollToBottom()
  }
}

function reset() {
  messages.value = []
  localStorage.removeItem(SESSION_KEY)
  ElMessage.success('已开始新对话')
}

watch(
  () => assistant.visible,
  async (visible) => {
    if (!visible) {
      return
    }
    const pending = assistant.consumePending()
    if (pending) {
      await nextTick()
      send(pending)
    } else {
      scrollToBottom()
    }
  }
)

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}
</script>

<template>
  <div class="assistant">
    <button class="fab" type="button" @click="assistant.openFromFab()">
      <el-icon :size="22"><ChatDotRound /></el-icon>
      <span>问 AI</span>
    </button>

    <el-drawer
      v-model="assistant.visible"
      direction="rtl"
      size="420px"
      :with-header="false"
      class="assistant-drawer"
    >
      <div class="panel">
        <header class="panel-head">
          <div>
            <strong>AI 找店助手</strong>
            <p>基于真实店铺数据，不会编造</p>
          </div>
          <el-button link type="primary" @click="reset">新对话</el-button>
        </header>

        <div ref="listRef" class="messages">
          <div v-if="!messages.length" class="welcome">
            <el-icon :size="28" color="#ff6633"><Promotion /></el-icon>
            <p>试试问我：</p>
            <button class="chip" @click="send('适合约会的安静餐厅，人均 200 以内')">
              适合约会的安静餐厅
            </button>
            <button class="chip" @click="send('带小孩玩一整天的地方')">带小孩玩一整天的地方</button>
          </div>

          <div
            v-for="(message, index) in messages"
            :key="index"
            class="bubble-row"
            :class="message.role"
          >
            <div class="bubble">
              <div v-if="message.role === 'assistant' && message.tools?.length" class="tool-line">
                <span class="pulse" />
                {{ message.tools[message.tools.length - 1] }}
              </div>
              <span v-if="message.streaming && !message.content" class="typing">思考中…</span>
              <template v-else>{{ message.content }}</template>
            </div>
            <div v-if="message.role === 'assistant' && message.usage" class="usage">
              {{ costText(message.usage) }}
            </div>
          </div>
        </div>

        <footer class="composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="想找什么样的店？回车发送，Shift+回车换行"
            @keydown="handleKeydown"
          />
          <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
        </footer>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.fab {
  position: fixed;
  right: 24px;
  bottom: 28px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 60px;
  height: 60px;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: var(--brand);
  box-shadow: 0 8px 20px rgba(255, 102, 51, 0.35);
  cursor: pointer;
  transition: transform 0.18s ease;
}

.fab:hover {
  transform: translateY(-2px);
}

.fab span {
  font-size: 11px;
  line-height: 1;
}

.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.panel-head strong {
  font-size: 16px;
}

.panel-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 2px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.welcome {
  color: var(--text-sub);
  font-size: 13px;
  text-align: center;
  padding-top: 40px;
}

.welcome p {
  margin: 12px 0;
}

.chip {
  display: block;
  width: 100%;
  margin-bottom: 8px;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--text-main);
  background: #f7f8fa;
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
}

.chip:hover {
  border-color: rgba(255, 102, 51, 0.4);
  color: var(--brand);
}

.bubble-row {
  display: flex;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 84%;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  border-radius: 12px;
}

.bubble-row.assistant .bubble {
  background: #f7f8fa;
  color: var(--text-main);
}

.bubble-row.user .bubble {
  background: var(--brand);
  color: #fff;
}

.typing {
  color: #9ca3af;
}

.bubble-row {
  flex-direction: column;
}

.bubble-row.assistant {
  align-items: flex-start;
}

.bubble-row.user {
  align-items: flex-end;
}

.tool-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--brand-dark);
  background: var(--brand-soft);
  border-radius: 999px;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  animation: pulse 1.1s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1.15);
  }
}

.usage {
  margin-top: 4px;
  font-size: 11px;
  color: #9ca3af;
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.composer :deep(.el-textarea) {
  flex: 1;
}
</style>
