import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('lib_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('lib_user') || 'null'))

  const isLogin = computed(() => Boolean(token.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isReader = computed(() => user.value?.role === 'READER')
  const homePath = computed(() => (isReader.value ? '/reader' : '/dashboard'))

  async function login(form) {
    const data = await authApi.login(form)
    token.value = data.token
    user.value = data.user
    localStorage.setItem('lib_token', data.token)
    localStorage.setItem('lib_user', JSON.stringify(data.user))
    return data
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('lib_token')
    localStorage.removeItem('lib_user')
  }

  return { token, user, isLogin, isAdmin, isReader, homePath, login, logout }
})