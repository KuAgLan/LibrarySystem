<template>
  <div>
    <div class="hero card">
      <div>
        <div class="hero-title">你好，{{ auth.user?.name }}</div>
        <div class="hero-sub tiny">
          借书证 {{ auth.user?.cardNo }} · 可借 {{ profile.maxBorrow - (profile.borrowing || 0) }} / {{ profile.maxBorrow }} 本
          <span v-if="profile.overdue > 0" class="warn">· {{ profile.overdue }} 本已逾期</span>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat"><div class="num">{{ profile.borrowing || 0 }}</div><div class="lbl">在借</div></div>
        <div class="stat"><div class="num" :class="{ warn: profile.overdue > 0 }">{{ profile.overdue || 0 }}</div><div class="lbl">逾期</div></div>
        <div class="stat"><div class="num">{{ profile.waitingReservations || 0 }}</div><div class="lbl">预约中</div></div>
        <div class="stat"><div class="num" :class="{ warn: Number(profile.unpaidFine) > 0 }">¥{{ profile.unpaidFine || '0.00' }}</div><div class="lbl">罚金</div></div>
      </div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="书名 / 作者 / ISBN" clearable style="width: 260px"
                  :prefix-icon="Search" @keyup.enter="load(1)" @clear="load(1)" />
        <el-select v-model="query.category" style="width: 140px" @change="load(1)">
          <el-option label="全部分类" value="ALL" />
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-checkbox v-model="query.onlyAvailable" @change="load(1)">只看可借</el-checkbox>
        <div class="right"><el-button :icon="Search" @click="load(1)">检索</el-button></div>
      </div>

      <div class="book-grid" v-loading="loading">
        <div v-for="b in books" :key="b.id" class="book">
          <div class="book-cover" :style="{ background: cover(b.title) }">{{ b.title.slice(0, 1) }}</div>
          <div class="book-body">
            <div class="book-title ellipsis" :title="b.title">{{ b.title }}</div>
            <div class="tiny muted ellipsis">{{ b.author || '—' }} · {{ b.publisher || '—' }}</div>
            <div class="book-meta tiny">
              <el-tag size="small" effect="plain">{{ b.category }}</el-tag>
              <span :class="b.availableCopies > 0 ? 'ok' : 'muted'">可借 {{ b.availableCopies }}/{{ b.totalCopies }}</span>
            </div>
            <div class="book-actions">
              <el-button size="small" type="primary" :disabled="b.availableCopies <= 0" :loading="acting === b.id"
                         @click="borrow(b)">借阅</el-button>
              <el-button size="small" :disabled="b.availableCopies > 0" :loading="acting === b.id"
                         @click="reserve(b)">预约</el-button>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && books.length === 0" description="没有找到符合条件的图书" />
      </div>

      <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="total" :page-size="query.pageSize" :current-page="query.page" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { portalApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const profile = inject('readerProfile')
const reloadProfile = inject('reloadProfile')

const categories = ['计算机', '文学', '历史', '经济', '综合']
const books = ref([])
const total = ref(0)
const loading = ref(false)
const acting = ref(0)
const query = reactive({ keyword: '', category: 'ALL', onlyAvailable: false, page: 1, pageSize: 8 })

const cover = (title) => {
  const palette = ['#e8efff', '#fdeee3', '#e6f5ec', '#f2eafc', '#e3f2fd', '#fdf0f4']
  let sum = 0
  for (const ch of String(title)) sum += ch.charCodeAt(0)
  return palette[sum % palette.length]
}

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await portalApi.books({ ...query })
    books.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function borrow(book) {
  await ElMessageBox.confirm(`确认借阅《${book.title}》？`, '借阅确认', { type: 'info' })
  acting.value = book.id
  try {
    const loan = await portalApi.borrow(book.id)
    ElMessage.success(`借阅成功，请于 ${loan.dueAt} 前归还`)
    await reloadProfile()
    load()
  } finally {
    acting.value = 0
  }
}

async function reserve(book) {
  await ElMessageBox.confirm(`《${book.title}》暂无可借副本，加入预约队列？`, '预约确认', { type: 'info' })
  acting.value = book.id
  try {
    await portalApi.reserve(book.id)
    ElMessage.success('预约成功，有书时会通知你（归还后按顺序）')
    await reloadProfile()
  } finally {
    acting.value = 0
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.hero { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.hero-title { font-size: 18px; font-weight: 700; }
.hero-sub { color: #667085; margin-top: 4px; }
.hero-stats { display: flex; gap: 24px; }
.stat { text-align: center; }
.num { font-size: 20px; font-weight: 700; }
.num.warn { color: #d92d20; }
.lbl { font-size: 12px; color: #98a2b3; }
.warn { color: #d92d20; }
.book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(232px, 1fr)); gap: 14px; }
.book { border: 1px solid #e6eaf0; border-radius: 12px; overflow: hidden; display: flex; background: #fff; }
.book-cover { width: 64px; flex: none; display: grid; place-items: center; font-size: 26px; font-weight: 700; color: #2f6fed; }
.book-body { padding: 10px 12px; min-width: 0; flex: 1; }
.book-title { font-size: 14px; font-weight: 600; }
.book-meta { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.ok { color: #039855; }
.book-actions { margin-top: 10px; display: flex; gap: 8px; }
</style>