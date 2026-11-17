<template>
  <div class="card stat">
    <div class="head">
      <span class="label">{{ label }}</span>
      <el-icon class="icon"><component :is="icon" /></el-icon>
    </div>
    <div class="value">
      <span v-if="prefix" class="prefix">{{ prefix }}</span>{{ value }}
      <span v-if="unit" class="unit">{{ unit }}</span>
    </div>
    <div class="hint tiny muted">{{ hint }}</div>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number],
  unit: String,
  prefix: String,
  hint: String,
  icon: { type: String, default: 'DataLine' }
})
</script>

<style scoped>
.stat { padding: 14px 16px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.label { font-size: 13px; color: var(--muted); }
.icon { color: #b6c2d4; }
.value { font-size: 24px; font-weight: 700; margin: 6px 0 2px; }
.prefix { font-size: 15px; margin-right: 2px; }
.unit { font-size: 13px; color: var(--muted); margin-left: 3px; font-weight: 500; }
</style>