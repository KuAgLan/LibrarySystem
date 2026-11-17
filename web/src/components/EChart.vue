<template>
  <div ref="el" :style="{ height: height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ option: { type: Object, required: true }, height: { type: String, default: '280px' } })
const el = ref(null)
let chart = null

function render() {
  if (!chart) return
  chart.setOption(props.option, true)
}

onMounted(() => {
  chart = echarts.init(el.value, null, { renderer: 'svg' })
  render()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
watch(() => props.option, render, { deep: true })
function resize() { chart?.resize() }
</script>