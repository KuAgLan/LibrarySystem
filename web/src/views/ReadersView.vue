<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="姓名 / 借书证 / 手机" clearable style="width: 240px" :prefix-icon="Search" @keyup.enter="load(1)" @clear="load(1)" />
        <el-select v-model="query.type" style="width: 120px" @change="load(1)">
          <el-option label="全部类型" value="ALL" />
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="社会" value="PUBLIC" />
        </el-select>
        <el-select v-model="query.status" style="width: 120px" @change="load(1)">
          <el-option label="全部状态" value="ALL" />
          <el-option label="正常" value="ACTIVE" />
          <el-option label="停用" value="SUSPENDED" />
        </el-select>
        <div class="right"><el-button type="primary" :icon="Plus" @click="openCreate">登记读者</el-button></div>
      </div>

      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="cardNo" label="借书证" width="130" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'TEACHER' ? 'warning' : row.type === 'STUDENT' ? 'primary' : 'info'">
              {{ typeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="院系 / 单位" min-width="140" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机" width="130" />
        <el-table-column label="在借 / 上限" width="110" align="center">
          <template #default="{ row }">{{ row.borrowedNow }} / {{ maxBorrow(row.type) }}</template>
        </el-table-column>
        <el-table-column label="未缴罚金" width="110" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': Number(row.unpaidFine) > 0 }">¥{{ row.unpaidFine }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="toggle(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
            <el-button link type="success" :disabled="Number(row.unpaidFine) <= 0" @click="payFine(row)">缴罚金</el-button>
            <el-button v-if="auth.isAdmin" link @click="resetPassword(row)">重置密码</el-button>
            <el-button v-if="auth.isAdmin" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 14px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="total" :page-size="query.pageSize" :current-page="query.page" @current-change="load" />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑读者' : '登记读者'" width="520px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" placeholder="11 位手机号" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="学生（30 天 / 5 本）" value="STUDENT" />
            <el-option label="教师（60 天 / 10 本）" value="TEACHER" />
            <el-option label="社会读者（15 天 / 3 本）" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="院系/单位"><el-input v-model="form.department" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">正常</el-radio>
            <el-radio value="SUSPENDED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { readerApi, settingApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const rules = ref({})
const query = reactive({ keyword: '', type: 'ALL', status: 'ALL', page: 1, pageSize: 10 })
const dialog = reactive({ visible: false, id: null })
const form = reactive({ name: '', phone: '', email: '', type: 'STUDENT', department: '', status: 'ACTIVE', remark: '' })

const typeText = (t) => ({ STUDENT: '学生', TEACHER: '教师', PUBLIC: '社会' }[t] || t)
const maxBorrow = (t) => Number(rules.value[`max_borrow_${String(t).toLowerCase()}`] || 5)

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await readerApi.page({ ...query })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.id = null
  Object.assign(form, { name: '', phone: '', email: '', type: 'STUDENT', department: '', status: 'ACTIVE', remark: '' })
  dialog.visible = true
}

function openEdit(row) {
  dialog.id = row.id
  Object.assign(form, { name: row.name, phone: row.phone, email: row.email, type: row.type,
    department: row.department, status: row.status, remark: row.remark })
  dialog.visible = true
}

async function save() {
  if (!form.name) return ElMessage.warning('请输入读者姓名')
  saving.value = true
  try {
    if (dialog.id) await readerApi.update(dialog.id, form)
    else await readerApi.create(form)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  await readerApi.setStatus(row.id, row.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE')
  ElMessage.success('状态已更新')
  load()
}

async function payFine(row) {
  await ElMessageBox.confirm(`确认收取 ${row.name} 的罚金 ¥${row.unpaidFine}？`, '收款确认', { type: 'warning' })
  await readerApi.payFine(row.id)
  ElMessage.success('罚金已结清')
  load()
}

async function resetPassword(row) {
  await ElMessageBox.confirm(`重置 ${row.name} 的门户密码为 123456？`, '提示', { type: 'warning' })
  await readerApi.resetPassword(row.id)
  ElMessage.success('已重置为 123456')
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除读者 ${row.name}？`, '提示', { type: 'warning' })
  await readerApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  rules.value = await settingApi.get()
  load(1)
})
</script>

<style scoped>
.text-danger { color: #d92d20; font-weight: 600; }
</style>