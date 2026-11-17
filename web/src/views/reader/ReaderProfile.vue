<template>
  <div class="two">
    <div class="card">
      <div class="card-title">我的资料</div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="姓名">{{ profile.name }}</el-descriptions-item>
        <el-descriptions-item label="借书证">{{ profile.cardNo }}</el-descriptions-item>
        <el-descriptions-item label="读者类型">{{ typeText(profile.type) }}</el-descriptions-item>
        <el-descriptions-item label="院系 / 单位">{{ profile.department || '—' }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ profile.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ profile.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="借阅额度">{{ profile.borrowedNow }} / {{ profile.maxBorrow }} 本</el-descriptions-item>
        <el-descriptions-item label="借期">{{ profile.loanDays }} 天</el-descriptions-item>
        <el-descriptions-item label="累计借阅">{{ profile.totalBorrow }} 次</el-descriptions-item>
        <el-descriptions-item label="未缴罚金">
          <span :class="{ danger: Number(profile.unpaidFine) > 0 }">¥{{ profile.unpaidFine }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="证件状态">
          <el-tag size="small" :type="profile.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ profile.status === 'ACTIVE' ? '正常' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="Number(profile.unpaidFine) > 0" type="warning" :closable="false" style="margin-top: 12px"
                title="有未缴罚金，请到服务台缴纳后再借阅" />
    </div>

    <div class="card">
      <div class="card-title">修改密码</div>
      <el-form label-width="86px">
        <el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" /></el-form-item>
        <el-form-item label="确认新密码"><el-input v-model="pwd.confirm" type="password" show-password /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
      <div class="tiny muted">初始密码由管理员分配（默认 123456），首次登录后建议立即修改。</div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const injected = inject('readerProfile')
const profile = computed(() => injected.value || {})
const saving = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const typeText = (t) => ({ STUDENT: '学生', TEACHER: '教师', PUBLIC: '社会读者' }[t] || '读者')

async function save() {
  if (pwd.newPassword !== pwd.confirm) return ElMessage.warning('两次输入的新密码不一致')
  if (pwd.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  saving.value = true
  try {
    await authApi.changePassword(pwd)
    ElMessage.success('密码已修改，请重新登录')
    auth.logout()
    location.hash = '#/login'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.two { display: grid; grid-template-columns: 1.2fr 1fr; gap: 16px; }
@media (max-width: 960px) { .two { grid-template-columns: 1fr; } }
.danger { color: #d92d20; font-weight: 600; }
</style>