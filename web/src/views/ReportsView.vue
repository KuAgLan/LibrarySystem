<template>
  <div>
    <div class="row2">
      <div class="card">
        <div class="card-title">月度借阅趋势</div>
        <EChart :option="trendOption" height="300px" />
      </div>
      <div class="card">
        <div class="card-title">分类借阅量</div>
        <EChart :option="categoryOption" height="300px" />
      </div>
    </div>

    <div class="row2" style="margin-top: 16px">
      <div class="card">
        <div class="card-title">热门图书 TOP 10<div class="tiny muted">按借阅次数</div></div>
        <el-table :data="topBooks" size="small">
          <el-table-column type="index" width="52" label="#" />
          <el-table-column prop="title" label="书名" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="loans" label="借阅次数" width="100" align="right" />
        </el-table>
      </div>
      <div class="card">
        <div class="card-title">
          逾期名单
          <el-button size="small" :icon="Download" @click="exportCsv">导出借阅 CSV</el-button>
        </div>
        <el-table :data="overdue" size="small" max-height="330">
          <el-table-column prop="bookTitle" label="图书" show-overflow-tooltip />
          <el-table-column prop="readerName" label="读者" width="100" />
          <el-table-column prop="dueAt" label="应还时间" width="160" />
          <el-table-column label="逾期天数" width="90" align="right">
            <template #default="{ row }">{{ lateDays(row.dueAt) }} 天</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import EChart from '@/components/EChart.vue'
import { reportApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const categories = ref([])
const trend = ref([])
const topBooks = ref([])
const overdue = ref([])

const trendOption = computed(() => ({
  grid: { left: 42, right: 16, top: 20, bottom: 26 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: trend.value.map((t) => t.month) },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
  series: [{ type: 'bar', barWidth: 16, data: trend.value.map((t) => t.loans),
    itemStyle: { color: '#2f6fed', borderRadius: [6, 6, 0, 0] } }]
}))

const categoryOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, textStyle: { fontSize: 11 } },
  series: [{
    type: 'pie', radius: ['46%', '70%'], center: ['50%', '44%'],
    label: { formatter: '{b}\n{c}', fontSize: 11 },
    data: categories.value.map((c) => ({ name: c.category, value: Number(c.loans) }))
  }]
}))

function lateDays(dueAt) {
  const diff = Date.now() - new Date(String(dueAt).replace(/-/g, '/')).getTime()
  return Math.max(0, Math.floor(diff / 86400000))
}

async function exportCsv() {
  const res = await fetch('/reports/loans/export?status=ALL', { headers: { Authorization: `Bearer ${auth.token}` } })
  if (!res.ok) return ElMessage.error('导出失败')
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `loans-${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  const data = await reportApi.dashboard()
  categories.value = data.categories || []
  trend.value = data.trend || []
  topBooks.value = data.topBooks || []
  overdue.value = data.overdueList || []
})
</script>

<style scoped>
.row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 1100px) { .row2 { grid-template-columns: 1fr; } }
</style>