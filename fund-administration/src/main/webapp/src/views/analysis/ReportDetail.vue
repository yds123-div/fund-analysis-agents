<template>
  <div class="page-container">
    <el-page-header style="margin-bottom: 20px" @back="$router.back()">
      <template #content>
        <span style="font-size: 16px; font-weight: 600">分析报告 — {{ report?.fundCode }} {{
            report?.fundName || ''
          }}</span>
      </template>
    </el-page-header>
    <el-skeleton :loading="loading" :rows="10" animated>
      <template #default>
        <ReportViewer v-if="report.fundCode" :report="report"/>
      </template>
    </el-skeleton>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {getReport} from '@/api/analysis'
import {ElMessage} from 'element-plus'
import ReportViewer from '@/components/ReportViewer.vue'

const route = useRoute()
const report = ref<any>({})
const loading = ref(true)

onMounted(async () => {
  try {
    report.value = await getReport(route.params.batchNo as string)
  } catch {
    ElMessage.error('报告加载失败')
  } finally {
    loading.value = false
  }
})
</script>
