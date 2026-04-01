<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">任务执行记录</span>
          <div style="display: flex; gap: 12px; align-items: center">
            <el-input v-model="filterCode" clearable placeholder="基金代码" size="small" style="width: 140px"
                      @clear="loadList" @keyup.enter="loadList"/>
            <el-button size="small" @click="loadList">
              <el-icon>
                <Search/>
              </el-icon>
              搜索
            </el-button>
          </div>
        </div>
      </template>
      <el-tabs v-model="activeTab" @tab-change="loadList">
        <el-tab-pane label="全部" name=""/>
        <el-tab-pane label="执行中" name="RUNNING"/>
        <el-tab-pane label="成功" name="SUCCESS"/>
        <el-tab-pane label="失败" name="FAILED"/>
        <el-tab-pane label="超时" name="TIMEOUT"/>
      </el-tabs>
      <el-skeleton :loading="listLoading" :rows="6" animated>
        <template #default>
          <el-empty v-if="!executions.length" description="暂无任务记录"/>
          <el-table v-else :data="executions" stripe>
            <el-table-column label="基金代码" prop="fundCode" width="100"/>
            <el-table-column label="基金名称" min-width="120" prop="fundName" show-overflow-tooltip/>
            <el-table-column label="触发方式" width="90">
              <template #default="{ row }">
                <el-tag :type="row.triggerType === 'MANUAL' ? 'info' : 'warning'" size="small">
                  {{ row.triggerType === 'MANUAL' ? '手动' : row.triggerType === 'SCHEDULED' ? '定时' : '批量' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="180">
              <template #default="{ row }">
                <el-progress :percentage="Math.max(0, liveProgress[row.batchNo] ?? row.progress ?? 0)"
                             :status="row.status === 'SUCCESS' ? 'success' : (row.status === 'FAILED' || row.status === 'TIMEOUT') ? 'exception' : ''"
                             :stroke-width="16" :text-inside="true" style="width: 100%"/>
              </template>
            </el-table-column>
            <el-table-column label="阶段" width="100">
              <template #default="{ row }">
                <span style="color: #909399; font-size: 12px">
                  {{ liveStage[row.batchNo] || row.currentStage || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="开始时间" prop="startTime" width="160"/>
            <el-table-column label="耗时" width="90">
              <template #default="{ row }">
                {{
                  row.endTime ? calcDuration(row.startTime, row.endTime) : '-'
                }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button v-if="row.status === 'SUCCESS'" size="small" type="primary"
                           @click="$router.push(`/analysis/report/${row.batchNo}`)">查看报告
                </el-button>
                <el-tooltip v-if="row.status === 'FAILED' || row.status === 'TIMEOUT'"
                            :content="row.errorMessage || '未知错误'" placement="top">
                  <el-button size="small" type="danger">{{ row.status === 'TIMEOUT' ? '超时详情' : '详情' }}</el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-skeleton>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, onUnmounted, reactive, ref} from 'vue'
import {getTaskProgress, listExecutions} from '@/api/task'
import {ElMessage} from 'element-plus'

const executions = ref<any[]>([])
const listLoading = ref(true)
const activeTab = ref('')
const filterCode = ref('')
const liveProgress = reactive<Record<string, number>>({})
const liveStage = reactive<Record<string, string>>({})
let pollTimers: ReturnType<typeof setInterval>[] = []

onMounted(() => loadList())
onUnmounted(() => pollTimers.forEach(t => clearInterval(t)))

const loadList = async () => {
  listLoading.value = true
  pollTimers.forEach(t => clearInterval(t));
  pollTimers = []
  try {
    const params: any = {page: 1, size: 50}
    if (activeTab.value) params.status = activeTab.value
    if (filterCode.value.trim()) params.fundCode = filterCode.value.trim()
    executions.value = (await listExecutions(params)) as any || []
    startRunningPolls()
  } catch {
    ElMessage.error('加载任务列表失败')
  } finally {
    listLoading.value = false
  }
}

const startRunningPolls = () => {
  const running = executions.value.filter(e => e.status === 'RUNNING')
  for (const exec of running) {
    const timer = setInterval(async () => {
      try {
        const data: any = await getTaskProgress(exec.batchNo)
        liveProgress[exec.batchNo] = data.progress
        liveStage[exec.batchNo] = data.stage
        if (data.progress >= 100 || data.progress < 0) {
          clearInterval(timer)
          await loadList()
        }
      } catch { /* ignore */
      }
    }, 3000)
    pollTimers.push(timer)
  }
}

const statusType = (s: string) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  RUNNING: 'warning',
  PENDING: 'info',
  TIMEOUT: 'danger'
}[s] || 'info') as 'success' | 'danger' | 'warning' | 'info'
const statusLabel = (s: string) => ({
  SUCCESS: '成功',
  FAILED: '失败',
  RUNNING: '执行中',
  PENDING: '等待',
  TIMEOUT: '超时'
}[s] || s)
const calcDuration = (start: string, end: string) => {
  if (!start || !end) return '-'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  return ms < 60000 ? `${Math.round(ms / 1000)}s` : `${Math.round(ms / 60000)}m`
}
</script>
