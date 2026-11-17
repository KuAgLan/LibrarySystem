<template>
  <div class="card">
    <div class="card-title">我的借阅
      <el-radio-group v-model="status" size="small" @change="load(1)">
        <el-radio-button value="BORROWED">在借</el-radio-button>
        <el-radio-button value="RETURNED">已归还</el-radio-button>
        <el-radio-button value="ALL">全部</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="bookTitle" label="图书" min-width="200" show-overflow-tooltip />
      <el-table-column prop="borrowedAt" label="借出" width="160" />
      <el-table-column label="应还" width="160">
        <template #default="{ row }">
          <span :class="{ danger: row.status === 'BORROWED' && overdue(row) }">{{ row.dueAt }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="returnedAt" label="归还" width="160">
        <template #default="{ row }">{{ row.returnedAt || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'RETURNED' ? 'info' : (overdue(row) ? 'danger' : 'success')">
            {{ row.status === 'RETURNED' ? '已归还' : (overdue(row) ? '已逾期' : '在借') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="罚金" width="90" align="right">
        <template #default="{ row }">¥{{ row.fineAmount }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'BORROWED'" link type="primary" @click="renew(row)">续借</el-button>
          <span v-else class="tiny muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 14px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" :current-page="page" @current-change="load" />
  </div>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalApi } from '@/api'

const reloadProfile = inject('reloadProfile')
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const status = ref('BORROWED')
const page = ref(1)
const pageSize = 10

const overdue = (row) => new Date(String(row.dueAt).replace(/-/g, '/')) < new Date()

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const data = await portalApi.loans({ status: status.value, page: p, pageSize })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function renew(row) {
  await ElMessageBox.confirm(`续借《${row.bookTitle}》？`, '续借确认', { type: 'info' })
  const loan = await portalApi.renew(row.id)
  ElMessage.success(`续借成功，新应还 ${loan.dueAt}`)
  await reloadProfile()
  load()
}

onMounted(() => load(1))
</script>

<style scoped>
.danger { color: #d92d20; font-weight: 600; }
</style>