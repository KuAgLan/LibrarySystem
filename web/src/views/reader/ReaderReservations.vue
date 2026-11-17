<template>
  <div class="card">
    <div class="card-title">我的预约<span class="tiny muted">有书归还后按预约顺序通知</span></div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column type="index" label="#" width="56" />
      <el-table-column prop="bookTitle" label="图书" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="{ WAITING: 'warning', NOTIFIED: 'success', FULFILLED: 'info', CANCELLED: 'info' }[row.status]">
            {{ { WAITING: '排队中', NOTIFIED: '已到书', FULFILLED: '已完成', CANCELLED: '已取消' }[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="预约时间" width="170" />
      <el-table-column prop="notifiedAt" label="通知时间" width="170">
        <template #default="{ row }">{{ row.notifiedAt || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="['WAITING', 'NOTIFIED'].includes(row.status)" link type="danger" @click="cancel(row)">取消</el-button>
          <span v-else class="tiny muted">—</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && rows.length === 0" description="还没有预约记录，去馆藏检索看看吧" />
  </div>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalApi } from '@/api'

const reloadProfile = inject('reloadProfile')
const rows = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try { rows.value = await portalApi.reservations() } finally { loading.value = false }
}

async function cancel(row) {
  await ElMessageBox.confirm(`取消对《${row.bookTitle}》的预约？`, '提示', { type: 'warning' })
  await portalApi.cancelReservation(row.id)
  ElMessage.success('已取消')
  await reloadProfile()
  load()
}

onMounted(load)
</script>