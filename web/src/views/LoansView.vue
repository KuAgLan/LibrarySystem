<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="load(1)">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="BORROWED">在借</el-radio-button>
          <el-radio-button value="OVERDUE">逾期</el-radio-button>
          <el-radio-button value="RETURNED">已归还</el-radio-button>
        </el-radio-group>
        <el-input v-model="query.keyword" placeholder="书名 / 读者 / 借书证" clearable style="width: 230px" :prefix-icon="Search" @keyup.enter="load(1)" @clear="load(1)" />
        <div class="right">
          <el-button type="primary" :icon="Plus" @click="openBorrow">办理借书</el-button>
          <el-button :icon="Download" @click="exportCsv">导出 CSV</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="bookTitle" label="图书" min-width="180" show-overflow-tooltip />
        <el-table-column label="读者" width="150">
          <template #default="{ row }">
            <div>{{ row.readerName }}</div>
            <div class="tiny muted">{{ row.readerCardNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="borrowedAt" label="借出时间" width="160" />
        <el-table-column label="应还时间" width="160">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.status === 'BORROWED' && isOverdue(row) }">{{ row.dueAt }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="returnedAt" label="归还时间" width="160">
          <template #default="{ row }">{{ row.returnedAt || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="tagType(row)">{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="罚金" width="90" align="right">
          <template #default="{ row }">¥{{ row.fineAmount }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'BORROWED'">
              <el-button link type="primary" @click="doReturn(row)">还书</el-button>
              <el-button link type="success" @click="doRenew(row)">续借</el-button>
            </template>
            <span v-else class="tiny muted">已结束</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 14px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="total" :page-size="query.pageSize" :current-page="query.page" @current-change="load" />
    </div>

    <el-dialog v-model="borrow.visible" title="办理借书" width="520px">
      <el-form label-width="80px">
        <el-form-item label="读者" required>
          <el-select v-model="borrow.readerId" filterable remote :remote-method="searchReaders" placeholder="输入姓名 / 借书证" style="width: 100%">
            <el-option v-for="r in readerOptions" :key="r.id" :label="`${r.name}（${r.cardNo}）· 在借 ${r.borrowedNow}`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="图书" required>
          <el-select v-model="borrow.bookId" filterable remote :remote-method="searchBooks" placeholder="输入书名 / ISBN" style="width: 100%">
            <el-option v-for="b in bookOptions" :key="b.id" :label="`${b.title}（可借 ${b.availableCopies}）`" :value="b.id" :disabled="b.availableCopies <= 0" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="borrow.remark" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="borrow.visible = false">取消</el-button>
        <el-button type="primary" :loading="borrow.saving" @click="submitBorrow">确认借出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Search } from '@element-plus/icons-vue'
import { bookApi, loanApi, readerApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ status: 'ALL', keyword: '', page: 1, pageSize: 10 })
const borrow = reactive({ visible: false, saving: false, readerId: null, bookId: null, remark: '' })
const readerOptions = ref([])
const bookOptions = ref([])

const isOverdue = (row) => new Date(row.dueAt.replace(/-/g, '/')) < new Date()
const statusText = (row) => row.status === 'RETURNED' ? '已归还' : (isOverdue(row) ? '逾期' : '在借')
const tagType = (row) => row.status === 'RETURNED' ? 'info' : (isOverdue(row) ? 'danger' : 'success')

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await loanApi.page({ ...query })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openBorrow() {
  borrow.readerId = null
  borrow.bookId = null
  borrow.remark = ''
  borrow.visible = true
  searchReaders('')
  searchBooks('')
}

async function searchReaders(keyword) {
  const data = await readerApi.page({ keyword, pageSize: 20, status: 'ACTIVE' })
  readerOptions.value = data.items
}

async function searchBooks(keyword) {
  const data = await bookApi.page({ keyword, pageSize: 20, status: 'ACTIVE' })
  bookOptions.value = data.items
}

async function submitBorrow() {
  if (!borrow.readerId || !borrow.bookId) return ElMessage.warning('请选择读者与图书')
  borrow.saving = true
  try {
    const loan = await loanApi.borrow({ readerId: borrow.readerId, bookId: borrow.bookId, remark: borrow.remark })
    ElMessage.success(`借出成功，应还 ${loan.dueAt}`)
    borrow.visible = false
    load(1)
  } finally {
    borrow.saving = false
  }
}

async function doReturn(row) {
  await ElMessageBox.confirm(`确认归还《${row.bookTitle}》？`, '还书确认', { type: 'warning' })
  const loan = await loanApi.returnBook(row.id)
  ElMessage.success(Number(loan.fineAmount) > 0 ? `已归还，产生罚金 ¥${loan.fineAmount}` : '已归还，无罚金')
  load()
}

async function doRenew(row) {
  await ElMessageBox.confirm(`续借《${row.bookTitle}》？`, '续借确认', { type: 'warning' })
  const loan = await loanApi.renew(row.id)
  ElMessage.success(`续借成功，新应还时间 ${loan.dueAt}`)
  load()
}

async function exportCsv() {
  const res = await fetch(`/reports/loans/export?status=${query.status}`, {
    headers: { Authorization: `Bearer ${auth.token}` }
  })
  if (!res.ok) return ElMessage.error('导出失败')
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `loans-${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => load(1))
</script>

<style scoped>
.text-danger { color: #d92d20; font-weight: 600; }
</style>