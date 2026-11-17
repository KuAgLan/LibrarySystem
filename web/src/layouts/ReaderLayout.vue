<template>
  <div class="reader-shell">
    <header class="topbar">
      <div class="brand" @click="$router.push('/reader/home')">
        <div class="logo">L</div>
        <div>
          <div class="name">图书馆 · 读者服务</div>
          <div class="sub">READER PORTAL</div>
        </div>
      </div>
      <nav class="nav">
        <router-link v-for="item in navs" :key="item.path" :to="item.path" class="nav-item"
                     :class="{ active: $route.path === item.path }">
          <el-icon><component :is="item.icon" /></el-icon>{{ item.label }}
          <el-badge v-if="item.path === '/reader/loans' && profile.overdue > 0" :value="profile.overdue"
                    class="badge" type="danger" />
        </router-link>
      </nav>
      <div class="who">
        <span class="hello">你好，{{ auth.user?.name }}</span>
        <el-tag size="small" effect="plain">{{ typeText(auth.user?.readerType) }}</el-tag>
        <el-tooltip content="退出登录" placement="bottom">
          <el-icon class="logout" @click="logout"><SwitchButton /></el-icon>
        </el-tooltip>
      </div>
    </header>

    <main class="body">
      <router-view v-slot="{ Component }">
        <component :is="Component" :key="$route.path" :profile="profile" @refresh="loadProfile" />
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { onMounted, provide, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import { portalApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const profile = ref({ borrowing: 0, overdue: 0, waitingReservations: 0, unpaidFine: 0, maxBorrow: 0 })

const navs = [
  { path: '/reader/home', label: '馆藏检索', icon: 'Search' },
  { path: '/reader/loans', label: '我的借阅', icon: 'Tickets' },
  { path: '/reader/reservations', label: '我的预约', icon: 'Bell' },
  { path: '/reader/profile', label: '个人中心', icon: 'User' }
]

const typeText = (t) => ({ STUDENT: '学生', TEACHER: '教师', PUBLIC: '社会读者' }[t] || '读者')

async function loadProfile() {
  profile.value = await portalApi.profile()
}
provide('readerProfile', profile)
provide('reloadProfile', loadProfile)

async function logout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  auth.logout()
  router.push('/login')
}

onMounted(loadProfile)
</script>

<style scoped>
.reader-shell { min-height: 100vh; background: #f5f7fb; }
.topbar { height: 62px; background: #fff; border-bottom: 1px solid #e6eaf0; display: flex; align-items: center;
  padding: 0 24px; gap: 24px; position: sticky; top: 0; z-index: 10; }
.brand { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.logo { width: 34px; height: 34px; border-radius: 10px; background: #2f6fed; color: #fff; display: grid;
  place-items: center; font-weight: 700; }
.name { font-size: 14px; font-weight: 600; }
.sub { font-size: 10px; letter-spacing: 1px; color: #98a2b3; }
.nav { display: flex; gap: 4px; margin-left: 12px; flex: 1; }
.nav-item { display: flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 8px;
  color: #475467; font-size: 14px; text-decoration: none; }
.nav-item:hover { background: #f2f5fb; }
.nav-item.active { background: #eaf0ff; color: #2f6fed; font-weight: 600; }
.badge { margin-left: 2px; }
.who { display: flex; align-items: center; gap: 10px; }
.hello { font-size: 13px; color: #475467; }
.logout { cursor: pointer; color: #98a2b3; font-size: 18px; }
.logout:hover { color: #d92d20; }
.body { max-width: 1160px; margin: 0 auto; padding: 22px 20px 40px; }
</style>