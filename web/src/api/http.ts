import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一 http 实例：
 * - baseURL 走 /api，由 vite 代理去掉前缀转发到 8081
 * - 请求头带 authorization
 * - 响应统一拆 { success, errorMsg, data }
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('hmdp_token')
  if (token) {
    config.headers.authorization = token
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success) {
        return body.data
      }
      ElMessage.error(body.errorMsg || '请求失败')
      return Promise.reject(new Error(body.errorMsg || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('hmdp_token')
      ElMessage.warning('登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default http
