import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchMe } from '@/api'

export interface CurrentUser {
  id: number
  nickName: string
  icon?: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('hmdp_token') || '')
  const user = ref<CurrentUser | null>(null)

  function setToken(value: string) {
    token.value = value
    localStorage.setItem('hmdp_token', value)
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem('hmdp_token')
  }

  async function loadUser() {
    if (!token.value) {
      return
    }
    try {
      user.value = await fetchMe()
    } catch {
      clear()
    }
  }

  return { token, user, setToken, clear, loadUser }
})
