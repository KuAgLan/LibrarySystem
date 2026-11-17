<template>
  <div class="card" style="max-width: 780px">
    <div class="card-title">借阅规则
      <span class="tiny muted">{{ auth.isAdmin ? '修改后立即对新借阅生效' : '仅管理员可修改' }}</span>
    </div>

    <el-form label-width="150px" :disabled="!auth.isAdmin">
      <el-divider content-position="left">借期（天）</el-divider>
      <el-form-item label="学生"><el-input-number v-model="form.loan_days_student" :min="1" :max="365" /></el-form-item>
      <el-form-item label="教师"><el-input-number v-model="form.loan_days_teacher" :min="1" :max="365" /></el-form-item>
      <el-form-item label="社会读者"><el-input-number v-model="form.loan_days_public" :min="1" :max="365" /></el-form-item>

      <el-divider content-position="left">借阅上限（本）</el-divider>
      <el-form-item label="学生"><el-input-number v-model="form.max_borrow_student" :min="1" :max="50" /></el-form-item>
      <el-form-item label="教师"><el-input-number v-model="form.max_borrow_teacher" :min="1" :max="50" /></el-form-item>
      <el-form-item label="社会读者"><el-input-number v-model="form.max_borrow_public" :min="1" :max="50" /></el-form-item>

      <el-divider content-position="left">续借</el-divider>
      <el-form-item label="最多续借次数"><el-input-number v-model="form.renew_limit" :min="0" :max="5" /></el-form-item>
      <el-form-item label="每次续借天数"><el-input-number v-model="form.renew_days" :min="1" :max="90" /></el-form-item>

      <el-divider content-position="left">罚金</el-divider>
      <el-form-item label="每日罚金（元）"><el-input-number v-model="form.fine_per_day" :min="0" :precision="2" :step="0.5" /></el-form-item>
      <el-form-item label="单本上限（元）"><el-input-number v-model="form.max_fine" :min="0" :precision="2" :step="5" /></el-form-item>
      <el-form-item label="欠费禁借阈值（元）"><el-input-number v-model="form.block_fine" :min="0" :precision="2" :step="5" /></el-form-item>

      <el-form-item v-if="auth.isAdmin">
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
        <el-button @click="load">恢复</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { settingApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const saving = ref(false)
const form = reactive({
  loan_days_student: 30, loan_days_teacher: 60, loan_days_public: 15,
  max_borrow_student: 5, max_borrow_teacher: 10, max_borrow_public: 3,
  renew_limit: 1, renew_days: 15, fine_per_day: 0.5, max_fine: 50, block_fine: 10
})

async function load() {
  const data = await settingApi.get()
  Object.keys(form).forEach((k) => {
    if (data[k] !== undefined) form[k] = Number(data[k])
  })
}

async function save() {
  saving.value = true
  try {
    const payload = {}
    Object.keys(form).forEach((k) => { payload[k] = String(form[k]) })
    await settingApi.save(payload)
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>