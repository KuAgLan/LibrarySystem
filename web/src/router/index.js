import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  {
    path: '/reader',
    component: () => import('@/layouts/ReaderLayout.vue'),
    redirect: '/reader/home',
    meta: { roles: ['READER'] },
    children: [
      { path: 'home', name: 'reader-home', component: () => import('@/views/reader/ReaderHome.vue'), meta: { title: '馆藏检索', roles: ['READER'] } },
      { path: 'loans', name: 'reader-loans', component: () => import('@/views/reader/ReaderLoans.vue'), meta: { title: '我的借阅', roles: ['READER'] } },
      { path: 'reservations', name: 'reader-reservations', component: () => import('@/views/reader/ReaderReservations.vue'), meta: { title: '我的预约', roles: ['READER'] } },
      { path: 'profile', name: 'reader-profile', component: () => import('@/views/reader/ReaderProfile.vue'), meta: { title: '个人中心', roles: ['READER'] } }
    ]
  },
  {
    path: '/',
    meta: { roles: ['ADMIN'] },
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '数据看板' } },
      { path: 'books', name: 'books', component: () => import('@/views/BooksView.vue'), meta: { title: '图书管理' } },
      { path: 'readers', name: 'readers', component: () => import('@/views/ReadersView.vue'), meta: { title: '读者管理' } },
      { path: 'loans', name: 'loans', component: () => import('@/views/LoansView.vue'), meta: { title: '借还办理' } },
      { path: 'reservations', name: 'reservations', component: () => import('@/views/ReservationsView.vue'), meta: { title: '预约队列' } },
      { path: 'reports', name: 'reports', component: () => import('@/views/ReportsView.vue'), meta: { title: '统计报表' } },
      { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '系统设置' } },
      { path: 'logs', name: 'logs', component: () => import('@/views/LogsView.vue'), meta: { title: '操作日志', roles: ['ADMIN'] } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLogin) return { path: '/login' }
  if (to.path === '/login' && auth.isLogin) return { path: auth.homePath }
  if (to.meta.roles && !to.meta.roles.includes(auth.user?.role)) return { path: auth.homePath }
  return true
})

export default router