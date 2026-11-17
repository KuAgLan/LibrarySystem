<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <el-radio-group v-model="status" @change="load">
          <el-radio-button value="WAITING">排队中</el-radio-button>
          <el-radio-button value="NOTIFIED">待取书</el-radio-button>
          <el-radio-button value="ALL">全部</el-radio-button>
        </el-radio-group>
        <div class="right"><el-button type="primary" :icon="Plus" @click="openCreate">新增预约</el-button></div>
      </div>

      <el-table :data="rows" v-loading="loading">
        <el-table-column type="index" label="#" width="56" />
        <el-table-column prop="bookTitle" label="图书" min-width="200" show-overflow-tooltip />
        <el-table-column prop="readerName" label="读者" width="120" />
        <el-table-column prop="readerCardNo" label="借书证" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="{ WAITING: 'warning', NOTIFIED: 'success', FULFILLED: 'info', CANCELLED: 'info' }[row.status]">
              {{ { WAITING: '排队中', NOTIFIED: '待取书', FULFILLED: '已完成', CANCELLED: '已取消' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="预约时间" width="170" />
        <el-table-column prop="notifiedAt" label="通知时间" width="170">
          <template #default="{ row }">{{ row.notifiedAt || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="['WAITING', 'NOTIFIED'].includes(row.status)" link type="danger" @click="cancel(row)">取消</el-button>
            <span v-else class="tiny muted">已结束</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" title="新增预约" width="480px">
      <el-form label-width="80px">
        <el-form-item label="图书" required>
          <el-select v-model="form.bookId" filterable remote :remote-method="searchBooks" placeholder="输入书名（仅无可借副本时可预约）" style="width: 100%">
            <el-option v-for="b in bookOptions" :key="b.id" :label="`${b.title}（可借 ${b.availableCopies}）`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="读者" required>
          <el-select v-model="form.readerId" filterable remote :remote-method="searchReaders" placeholder="输入姓名 / 借书证" style="width: 100%">
            <el-option v-for="r in readerOptions" :key="r.id" :label="`${r.name}（${r.cardNo}）`" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { bookApi, readerApi, reservationApi } from '@/api'

const rows = ref([])
const loading = ref(false)
const status = ref('WAITING')
const saving = ref(false)
const dialog = reactive({ visible: false })
const form = reactive({ bookId: null, readerId: null })
const bookOptions = ref([])
const readerOptions = ref([])

async function load() {
  loading.value = true
  try { rows.value = await reservationApi.list(status.value) } finally { loading.value = false }
}

function openCreate() {
  form.bookId = null
  form.readerId = null
  dialog.visible = true
  searchBooks('')
  searchReaders('')
}

async function searchBooks(keyword) {
  const data = await bookApi.page({ keyword, pageSize: 20 })
  bookOptions.value = data.items
}

async function searchReaders(keyword) {
  const data = await readerApi.page({ keyword, pageSize: 20, status: 'ACTIVE' })
  readerOptions.value = data.items
}

async function save() {
  if (!form.bookId || !form.readerId) return ElMessage.warning('请选择图书与读者')
  saving.value = true
  try {
    await reservationApi.create({ ...form })
    ElMessage.success('预约成功，归还后将按顺序通知')
    dialog.visible = false
    status.value = 'WAITING'
    load()
  } finally {
    saving.value = false
  }
}

async function cancel(row) {
  await ElMessageBox.confirm(`取消 ${row.readerName} 对《${row.bookTitle}》的预约？`, '提示', { type: 'warning' })
  await reservationApi.cancel(row.id)
  ElMessage.success('已取消')
  load()
}

onMounted(load)
</script>