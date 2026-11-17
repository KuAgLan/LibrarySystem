<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="书名 / 作者 / ISBN" clearable style="width: 240px" :prefix-icon="Search" @keyup.enter="load(1)" @clear="load(1)" />
        <el-select v-model="query.category" style="width: 130px" @change="load(1)">
          <el-option label="全部分类" value="ALL" />
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="query.status" style="width: 120px" @change="load(1)">
          <el-option label="全部状态" value="ALL" />
          <el-option label="在架" value="ACTIVE" />
          <el-option label="已下架" value="OFF_SHELF" />
        </el-select>
        <div class="right">
          <el-button type="primary" :icon="Plus" @click="openCreate">新增图书</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" size="default">
        <el-table-column prop="title" label="书名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="isbn" label="ISBN" width="150" />
        <el-table-column prop="author" label="作者" width="150" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90" />
        <el-table-column label="馆藏/可借" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.availableCopies > 0 ? 'success' : 'info'" effect="plain" size="small">
              {{ row.availableCopies }} / {{ row.totalCopies }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '在架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="auth.isAdmin" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 14px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="total" :page-size="query.pageSize" :current-page="query.page" @current-change="load" />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑图书' : '新增图书'" width="560px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="ISBN" required><el-input v-model="form.isbn" placeholder="如 9787111213826" /></el-form-item>
        <el-form-item label="书名" required><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
        <el-form-item label="出版社"><el-input v-model="form.publisher" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" allow-create filterable style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="馆藏位置"><el-input v-model="form.location" placeholder="如 A 区-101" /></el-form-item>
        <el-form-item label="馆藏总数"><el-input-number v-model="form.totalCopies" :min="1" :max="999" /></el-form-item>
        <el-form-item label="定价"><el-input-number v-model="form.price" :min="0" :precision="2" :step="10" /></el-form-item>
        <el-form-item label="出版年"><el-input-number v-model="form.publishYear" :min="1900" :max="2100" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">在架</el-radio>
            <el-radio value="OFF_SHELF">下架</el-radio>
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
import { bookApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const categories = ['计算机', '文学', '历史', '经济', '综合']
const query = reactive({ keyword: '', category: 'ALL', status: 'ALL', page: 1, pageSize: 10 })
const dialog = reactive({ visible: false, id: null })
const form = reactive({ isbn: '', title: '', author: '', publisher: '', category: '综合', location: '',
  totalCopies: 1, price: 0, publishYear: new Date().getFullYear(), status: 'ACTIVE', remark: '' })

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await bookApi.page({ ...query })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(form, { isbn: '', title: '', author: '', publisher: '', category: '综合', location: '',
    totalCopies: 1, price: 0, publishYear: new Date().getFullYear(), status: 'ACTIVE', remark: '' })
}

function openCreate() {
  dialog.id = null
  reset()
  dialog.visible = true
}

function openEdit(row) {
  dialog.id = row.id
  Object.assign(form, {
    isbn: row.isbn, title: row.title, author: row.author, publisher: row.publisher, category: row.category,
    location: row.location, totalCopies: row.totalCopies, price: Number(row.price || 0),
    publishYear: row.publishYear, status: row.status, remark: row.remark
  })
  dialog.visible = true
}

async function save() {
  if (!form.isbn || !form.title) return ElMessage.warning('ISBN 与书名为必填')
  saving.value = true
  try {
    if (dialog.id) await bookApi.update(dialog.id, form)
    else await bookApi.create(form)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除《${row.title}》？`, '提示', { type: 'warning' })
  await bookApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => load(1))
</script>