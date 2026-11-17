<template>
  <div class="layout">
    <aside class="side">
      <div class="brand">
        <div class="logo">L</div>
        <div>
          <div class="brand-name">图书管理系统</div>
          <div class="brand-sub">LIBRARY SYSTEM</div>
        </div>
      </div>
      <el-menu :default-active="$route.path" router class="menu">
        <div class="group">业务</div>
        <el-menu-item index="/dashboard"><el-icon><DataLine /></el-icon><span>数据看板</span></el-menu-item>
        <el-menu-item index="/books"><el-icon><Reading /></el-icon><span>图书管理</span></el-menu-item>
        <el-menu-item index="/readers"><el-icon><User /></el-icon><span>读者管理</span></el-menu-item>
        <el-menu-item index="/loans"><el-icon><Tickets /></el-icon><span>借还办理</span></el-menu-item>
        <el-menu-item index="/reservations"><el-icon><Bell /></el-icon><span>预约队列</span></el-menu-item>
        <div class="group">统计</div>
        <el-menu-item index="/reports"><el-icon><TrendCharts /></el-icon><span>统计报表</span></el-menu-item>
        <div class="group">系统</div>
        <el-menu-item index="/settings"><el-icon><Setting /></el-icon><span>系统设置</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/logs"><el-icon><Document /></el-icon><span>操作日志</span></el-menu-item>
      </el-menu>
      <div class="side-foot">
        <div class="who">
          <div class="avatar">{{ auth.user?.name?.slice(0, 1) }}</div>
          <div>
            <div class="who-name">{{ auth.user?.name }}</div>
            <div class="who-role">{{ auth.user?.role === 'ADMIN' ? '管理员' : '馆员' }}</div>
          </div>
        </div>
        <el-tooltip content="退出登录" placement="top">
          <el-icon class="logout" @click="logout"><SwitchButton /></el-icon>
        </el-tooltip>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="title">{{ $route.meta.title }}</div>
        <div class="right">
          <el-tag type="success" effect="plain" size="small">{{ today }}</el-tag>
          <el-button size="small" :icon="Refresh" @click="refresh">刷新</el-button>
        </div>
      </header>
      <div class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="refreshKey" />
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const refreshKey = ref(0)
const today = computed(() => new Date().toLocaleDateString('zh-CN'))

function refresh() {
  refreshKey.value++
}

async function logout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { display: flex; min-height: 100vh; }
.side {
  width: 216px; flex: none; background: #101828; color: #c8d2e0;
  display: flex; flex-direction: column; padding: 14px 10px;
}
.brand { display: flex; align-items: center; gap: 10px; padding: 6px 8px 14px; }
.logo { width: 34px; height: 34px; border-radius: 10px; background: var(--brand); color: #fff;
  display: grid; place-items: center; font-weight: 700; }
.brand-name { font-size: 14px; color: #fff; font-weight: 600; }
.brand-sub { font-size: 10px; letter-spacing: 1px; color: #7d8ba1; }
.menu { border: none; background: transparent; flex: 1; min-height: 0; overflow-y: auto; }
.group { font-size: 11px; color: #66748a; padding: 12px 12px 6px; }
:deep(.el-menu-item) { color: #c8d2e0; height: 38px; border-radius: 8px; margin: 2px 4px; }
:deep(.el-menu-item:hover) { background: #1b2534; color: #fff; }
:deep(.el-menu-item.is-active) { background: var(--brand); color: #fff; }
.side-foot { display: flex; align-items: center; gap: 8px; padding: 10px 8px 4px; border-top: 1px solid #1e2937; }
.who { display: flex; align-items: center; gap: 8px; flex: 1; }
.avatar { width: 30px; height: 30px; border-radius: 50%; background: #2b3a4f; color: #fff;
  display: grid; place-items: center; font-size: 13px; }
.who-name { font-size: 13px; color: #fff; }
.who-role { font-size: 11px; color: #7d8ba1; }
.logout { cursor: pointer; color: #8b98ac; }
.logout:hover { color: #fff; }
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar { height: 58px; background: #fff; border-bottom: 1px solid var(--line);
  display: flex; align-items: center; padding: 0 20px; }
.title { font-size: 17px; font-weight: 600; }
.topbar .right { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.content { padding: 18px 20px 28px; }
</style>