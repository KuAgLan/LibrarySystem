<template>
  <div class="card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="动作 / 详情 / 操作人" clearable style="width: 260px" :prefix-icon="Search" @keyup.enter="load(1)" @clear="load(1)" />
      <div class="right"><span class="tiny muted">共 {{ total }} 条</span></div>
    </div>
    <el-table :data="rows" v-loading="loading" size="small">
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column prop="userName" label="操作人" width="120" />
      <el-table-column prop="action" label="动作" width="180" />
      <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="140" />
    </el-table>
    <el-pagination style="margin-top: 14px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.pageSize" :current-page="query.page" @current-change="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { logApi } from '@/api'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', page: 1, pageSize: 20 })

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await logApi.page({ ...query })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(() => load(1))
</script>