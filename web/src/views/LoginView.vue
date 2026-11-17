<template>
  <div class="login">
    <div class="panel">
      <div class="left">
        <div class="logo">L</div>
        <h1>图书管理系统</h1>
        <p class="sub">Library Management System</p>
        <ul>
          <li>图书 / 读者 / 借还 / 预约全流程</li>
          <li>借阅上限、逾期罚金、预约排队自动联动</li>
          <li>分类借阅、月度趋势、逾期名单报表</li>
        </ul>
      </div>
      <div class="right">
        <h2>登录</h2>
        <p class="tiny muted">请使用馆员账号登录</p>
        <el-form :model="form" @submit.prevent="submit">
          <el-form-item>
            <el-input v-model="form.username" size="large" placeholder="账号 / 借书证号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" :prefix-icon="Lock" @keyup.enter="submit" />
          </el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">登 录</el-button>
        </el-form>
        <div class="tips tiny muted">
          管理员：admin / admin123<br />
          读　者：R20241001 / 123456（借书证号登录）
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

async function submit() {
  if (!form.username || !form.password) return ElMessage.warning('请输入账号和密码')
  loading.value = true
  try {
    await auth.login({ ...form })
    ElMessage.success(`登录成功，欢迎 ${auth.user?.name}`)
    router.push(auth.homePath)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login { min-height: 100vh; display: grid; place-items: center;
  background: radial-gradient(1200px 600px at 15% 10%, #e8efff 0%, #f5f7fb 55%, #eef2f8 100%); }
.panel { width: 880px; max-width: 94vw; background: #fff; border-radius: 18px; overflow: hidden;
  display: grid; grid-template-columns: 1.1fr 1fr; box-shadow: 0 18px 50px rgba(28, 45, 80, 0.14); }
.left { padding: 46px 40px; background: linear-gradient(150deg, #1d3f8f, #2f6fed 65%, #4b8bf5); color: #fff; }
.logo { width: 46px; height: 46px; border-radius: 12px; background: rgba(255,255,255,0.18);
  display: grid; place-items: center; font-size: 22px; font-weight: 700; }
h1 { font-size: 26px; margin: 18px 0 4px; }
.sub { opacity: 0.75; font-size: 12px; letter-spacing: 1.2px; margin: 0 0 26px; }
.left ul { padding-left: 18px; line-height: 2; font-size: 13px; opacity: 0.92; }
.right { padding: 52px 44px; }
h2 { margin: 0 0 4px; font-size: 22px; }
.tips { margin-top: 18px; line-height: 1.7; }
</style>