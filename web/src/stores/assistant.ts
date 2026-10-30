import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAssistantStore = defineStore('assistant', () => {
  const visible = ref(false)
  const pendingMessage = ref('')
  const shopContext = ref<{ id: number; name: string } | null>(null)

  function open(message = '') {
    pendingMessage.value = message
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  function consumePending() {
    const message = pendingMessage.value
    pendingMessage.value = ''
    return message
  }

  function setShopContext(context: { id: number; name: string } | null) {
    shopContext.value = context
  }

  /**
   * 右下角悬浮球入口：在店铺详情页会自动带上店铺上下文
   */
  function openFromFab() {
    if (shopContext.value) {
      open(`这家店怎么样：${shopContext.value.name}（店铺ID：${shopContext.value.id}）`)
    } else {
      open()
    }
  }

  return {
    visible,
    pendingMessage,
    shopContext,
    open,
    close,
    consumePending,
    setShopContext,
    openFromFab
  }
})
