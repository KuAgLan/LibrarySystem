<template>
  <div>
    <div class="grid-stats" style="margin-bottom: 16px">
      <StatCard label="馆藏" :value="stat.titleCount" unit="种" icon="Reading" :hint="`共 ${stat.copyCount} 册，可借 ${stat.availableCount} 册`" />
      <StatCard label="当前在借" :value="stat.borrowing" unit="本" icon="Tickets" :hint="`今日借出 ${stat.todayBorrow} · 今日归还 ${stat.todayReturn}`" />
      <StatCard label="逾期未还" :value="stat.overdue" unit="本" icon="Warning" :hint="`今日罚金 ¥${stat.fineToday}`" />
      <StatCard label="注册读者" :value="stat.readerCount" unit="位" icon="User" :hint="`停用 ${stat.suspendedReaders} 位`" />
      <StatCard label="未缴罚金" :value="`¥${stat.unpaidFine}`" icon="Money" hint="读者欠费合计" />
    </div>

    <div class="row2">
      <div class="card">
        <div class="card-title">月度借阅趋势<span class="tiny muted">近 12 个月</span></div>
        <EChart :option="trendOption" height="270px" />
      </div>
      <div class="card">
        <div class="card-title">分类借阅量</div>
        <EChart :option="categoryOption" height="270px" />
      </div>
    </div>

    <div class="row2" style="margin-top: 16px">
      <div class="card">
        <div class="card-title">热门图书 TOP 10</div>
        <el-table :data="topBooks" size="small" :show-header="true">
          <el-table-column type="index" width="52" label="#" />
          <el-table-column prop="title" label="书名" show-overflow-tooltip />
          <el-table-column prop="author" label="作者" width="140" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="loans" label="借阅" width="80" align="right" />
        </el-table>
      </div>
      <div class="card">
        <div class="card-title">逾期名单<span class="tiny muted">最近 {{ overdue.length }} 条</span></div>
        <el-table :data="overdue" size="small">
          <el-table-column prop="bookTitle" label="图书" show-overflow-tooltip />
          <el-table-column prop="readerName" label="读者" width="100" />
          <el-table-column prop="dueAt" label="应还时间" width="160" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import StatCard from '@/components/StatCard.vue'
import EChart from '@/components/EChart.vue'
import { reportApi } from '@/api'

const stat = ref({ titleCount: 0, copyCount: 0, availableCount: 0, readerCount: 0, suspendedReaders: 0,
  borrowing: 0, overdue: 0, todayBorrow: 0, todayReturn: 0, unpaidFine: '0.00', fineToday: '0.00' })
const categories = ref([])
const trend = ref([])
const topBooks = ref([])
const overdue = ref([])

const trendOption = computed(() => ({
  grid: { left: 40, right: 16, top: 24, bottom: 28 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: trend.value.map((t) => t.month), axisLine: { lineStyle: { color: '#dfe5ee' } } },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
  series: [{ type: 'line', smooth: true, showSymbol: false, data: trend.value.map((t) => t.loans),
    itemStyle: { color: '#2f6fed' }, areaStyle: { color: 'rgba(47,111,237,0.12)' } }]
}))

const categoryOption = computed(() => ({
  grid: { left: 70, right: 24, top: 16, bottom: 24 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
  yAxis: { type: 'category', data: categories.value.map((c) => c.category) },
  series: [{ type: 'bar', barWidth: 14, data: categories.value.map((c) => c.loans),
    itemStyle: { color: '#4b8bf5', borderRadius: [0, 6, 6, 0] } }]
}))

onMounted(async () => {
  const data = await reportApi.dashboard()
  stat.value = data.stat
  categories.value = data.categories || []
  trend.value = data.trend || []
  topBooks.value = data.topBooks || []
  overdue.value = data.overdueList || []
})
</script>

<style scoped>
.row2 { display: grid; grid-template-columns: 1.15fr 1fr; gap: 16px; }
@media (max-width: 1100px) { .row2 { grid-template-columns: 1fr; } }
</style>