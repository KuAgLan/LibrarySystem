<template>
  <div>
    <div v-if="auth.isAdmin" class="card" style="max-width: 780px; margin-bottom: 16px">
      <div class="card-title">任务调度
        <span class="tiny muted">分布式锁保护，多实例部署只执行一次</span>
      </div>
      <div class="job-row">
        <div>
          <div class="job-name">每日结算任务</div>
          <div class="tiny muted">逾期统计 · 超期读者停用 · 过期预约清理（每天 02:00 自动执行）</div>
        </div>
        <div class="job-actions">
          <el-tag size="small" type="info" effect="plain">锁：{{ lockProvider }}</el-tag>
          <el-button size="small" :loading="running" @click="runJob()">立即执行</el-button>
          <el-button size="small" type="primary" plain :loading="running" @click="runJob(2500)">并发演练（持锁 2.5s）</el-button>
        </div>
      </div>
      <div v-if="jobResult" class="job-result tiny">
        最近执行：{{ jobResult.executedAt }} · 逾期 {{ jobResult.overdueLoans }} 条 · 停用 {{ jobResult.suspendedReaders }} 位 ·
        取消预约 {{ jobResult.expiredReservations }} 条 · 耗时 {{ jobResult.costMs }}ms
      </div>
    </div>

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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { jobApi, settingApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const saving = ref(false)
const running = ref(false)
const jobResult = ref(null)
const lockProvider = ref('mysql')
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

async function runJob(holdMs = 0) {
  running.value = true
  try {
    jobResult.value = await jobApi.settle(holdMs)
    ElMessage.success('任务执行完成')
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    const info = await jobApi.locks()
    lockProvider.value = info.provider
  } catch (e) { /* 非管理员忽略 */ }
})
</script>

<style scoped>
.job-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 4px 0 10px; }
.job-name { font-size: 14px; font-weight: 600; margin-bottom: 2px; }
.job-actions { display: flex; align-items: center; gap: 8px; }
.job-result { background: #f4f7fd; border-radius: 8px; padding: 8px 10px; color: #475467; }
</style>