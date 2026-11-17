import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const http = axios.create({ baseURL: '/', timeout: 20000 })

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'ok' in body) {
      if (body.ok) return body.data
      const msg = body.error?.message || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return body
  },
  async (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const url = error.config?.url || ''
    if (status === 401 && !url.includes('/auth/login')) {
      const auth = useAuthStore()
      auth.logout()
      if (location.hash !== '#/login') location.hash = '#/login'
    }
    const msg = body?.error?.message || (status === 403 ? '没有操作权限' : '网络或服务异常')
    if (!(status === 401 && !url.includes('/auth/login'))) {
      ElMessage.error(msg)
    }
    return Promise.reject(new Error(msg))
  }
)

export default http