<template>
  <div class="page-container">
    <!-- 调度器状态 -->
    <el-card shadow="hover" style="margin-bottom: 20px">
      <div style="display: flex; align-items: center; justify-content: space-between">
        <div style="display: flex; align-items: center; gap: 12px">
          <span :class="schedulerOnline ? 'online' : 'offline'" class="status-dot"/>
          <span style="font-weight: 600">调度器状态：{{ schedulerOnline ? '运行中' : '已停止' }}</span>
          <el-tag v-if="schedulerStatus.taskCount != null" size="small" type="info">
            {{ schedulerStatus.taskCount }} 个任务
          </el-tag>
        </div>
        <el-button size="small" type="primary" @click="openTaskDialog()">
          <el-icon>
            <Plus/>
          </el-icon>
          新增任务
        </el-button>
      </div>
    </el-card>
    <!-- 任务列表 -->
    <el-card shadow="hover">
      <template #header><span style="font-weight: 600">任务列表</span></template>
      <el-skeleton :loading="loading" :rows="4" animated>
        <template #default>
          <el-empty v-if="!taskList.length" description="暂无定时任务"/>
          <el-table v-else :data="taskList" stripe>
            <el-table-column label="基金代码" prop="fundCode" width="110"/>
            <el-table-column label="任务类型" prop="taskType" width="120">
              <template #default="{ row }">
                <el-tag :type="taskTypeTag(row.taskType)" size="small">{{ taskTypeLabel(row.taskType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Cron 表达式" prop="cronExpression" width="200">
              <template #default="{ row }">
                <el-tooltip :content="cronReadable(row.cronExpression)" placement="top">
                  <code class="cron-code">{{ row.cronExpression }}</code>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="下次触发" width="170">
              <template #default="{ row }">
                <span style="font-size: 12px; color: #909399">{{ nextFireTime(row.cronExpression, row.enabled) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="推送渠道" width="140">
              <template #default="{ row }">
                <el-tag v-for="ch in (row.channels || [])" :key="ch" size="small" style="margin-right: 4px">
                  {{ ch }}
                </el-tag>
                <span v-if="!row.channels?.length" style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" @change="toggleEnabled(row)"/>
              </template>
            </el-table-column>
            <el-table-column label="超时" width="80">
              <template #default="{ row }">
                <span style="font-size: 12px; color: #909399">{{ row.timeoutMinutes || 30 }}分钟</span>
              </template>
            </el-table-column>
            <el-table-column label="描述" min-width="120" prop="description" show-overflow-tooltip/>
            <el-table-column fixed="right" label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="openTaskDialog(row)">编辑</el-button>
                <el-button :loading="triggeringId === row.id" size="small" type="success"
                           @click="doTrigger(row.id)">触发
                </el-button>
                <el-tooltip v-if="row.taskType === 'SYSTEM'" content="系统任务不建议删除" placement="top">
                  <el-button disabled size="small" type="danger">删除</el-button>
                </el-tooltip>
                <el-button v-else size="small" type="danger" @click="doDelete(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-skeleton>
    </el-card>
    <!-- 推送历史 -->
    <el-card shadow="hover" style="margin-top: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">推送历史</span>
          <el-button size="small" @click="loadHistory">刷新</el-button>
        </div>
      </template>
      <el-empty v-if="!historyList.length" :image-size="60" description="暂无推送记录"/>
      <el-table v-else :data="historyList" size="small" stripe>
        <el-table-column label="渠道" prop="channel" width="80">
          <template #default="{ row }">
            <el-tag :type="row.channel === 'BARK' ? 'primary' : 'warning'" size="small">{{ row.channel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="title" show-overflow-tooltip width="200"/>
        <el-table-column label="内容" min-width="300" prop="content">
          <template #default="{ row }">
            <el-popover placement="left" trigger="click" width="500">
              <template #reference>
                <span class="content-preview">{{
                    row.content?.substring(0, 80)
                  }}{{ row.content?.length > 80 ? '...' : '' }}</span>
              </template>
              <div
                  style="max-height: 400px; overflow-y: auto; white-space: pre-wrap; font-size: 13px; line-height: 1.6">
                {{ row.content }}
              </div>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SENT' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'"
                    size="small">
              {{ row.status === 'SENT' ? '已发送' : row.status === 'FAILED' ? '失败' : '待发送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" prop="sentTime" width="170"/>
        <el-table-column label="错误信息" prop="errorMessage" show-overflow-tooltip width="160">
          <template #default="{ row }">
            <span v-if="row.errorMessage" style="color: #f56c6c; font-size: 12px">{{ row.errorMessage }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="historyList.length >= historyPageSize" style="text-align: center; margin-top: 12px">
        <el-button size="small" @click="loadMoreHistory">加载更多</el-button>
      </div>
    </el-card>
    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="form.id ? '编辑任务' : '新增任务'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="基金代码">
          <el-input v-model="form.fundCode" placeholder="如 110011"/>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" style="width: 100%" @change="onTaskTypeChange">
            <el-option label="盘后分析" value="POST_MARKET"/>
            <el-option label="每日报告" value="DAILY_REPORT"/>
            <el-option label="每周报告" value="WEEKLY_REPORT"/>
            <el-option label="自定义" value="CUSTOM"/>
          </el-select>
        </el-form-item>
        <el-form-item label="调度模式">
          <el-select v-model="form.scheduleMode" style="width: 100%" @change="onScheduleModeChange">
            <el-option label="每天定时" value="daily"/>
            <el-option label="工作日定时" value="weekday"/>
            <el-option label="每小时" value="hourly"/>
            <el-option label="每N分钟" value="interval"/>
            <el-option label="每周指定" value="weekly"/>
            <el-option label="自定义 Cron" value="custom"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode === 'daily' || form.scheduleMode === 'weekday'" label="执行时间">
          <el-time-picker v-model="form.scheduleTime" format="HH:mm" placeholder="选择时间"
                          style="width: 100%" value-format="HH:mm" @change="buildCron"/>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode === 'hourly'" label="分钟偏移">
          <el-select v-model="form.minuteOffset" style="width: 100%" @change="buildCron">
            <el-option v-for="m in [0,5,10,15,20,30,45]" :key="m" :label="`每小时第 ${m} 分钟`" :value="m"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode === 'interval'" label="间隔">
          <el-select v-model="form.intervalMinutes" style="width: 100%" @change="buildCron">
            <el-option v-for="m in [5,10,15,30,60]" :key="m" :label="`每 ${m} 分钟`" :value="m"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode === 'weekly'" label="星期">
          <el-select v-model="form.weekDay" style="width: 100%" @change="buildCron">
            <el-option label="周一" value="MON"/>
            <el-option label="周二" value="TUE"/>
            <el-option label="周三" value="WED"/>
            <el-option label="周四" value="THU"/>
            <el-option label="周五" value="FRI"/>
            <el-option label="周六" value="SAT"/>
            <el-option label="周日" value="SUN"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode === 'weekly'" label="执行时间">
          <el-time-picker v-model="form.scheduleTime" format="HH:mm" placeholder="选择时间"
                          style="width: 100%" value-format="HH:mm" @change="buildCron"/>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode === 'custom'" label="Cron 表达式">
          <el-input v-model="form.cronExpression" placeholder="0 30 16 * * MON-FRI"/>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode !== 'custom'" label="生成的 Cron">
          <code class="cron-code">{{ form.cronExpression }}</code>
        </el-form-item>
        <el-form-item label="推送渠道">
          <el-checkbox-group v-model="form.channels">
            <el-checkbox value="Bark">Bark</el-checkbox>
            <el-checkbox value="邮箱">邮箱</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="form.timeoutMinutes" :max="120" :min="5" :step="5" style="width: 100%"/>
          <span style="color: #909399; font-size: 12px; margin-top: 4px; display: block">
            任务执行超过该时间（分钟）将自动标记为超时，默认 30 分钟
          </span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled"/>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" :rows="2" placeholder="可选" type="textarea"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup>
import {onMounted, reactive, ref} from 'vue'
import {deleteTaskConfig, getSchedulerStatus, getTaskConfigs, saveTaskConfig, triggerTask} from '@/api/scheduler'
import {getNotificationHistory} from '@/api/notification'
import {ElMessage, ElMessageBox} from 'element-plus'

const taskList = ref<any[]>([])
const loading = ref(true)
const showDialog = ref(false)
const saving = ref(false)
const triggeringId = ref<number | null>(null)
const schedulerOnline = ref(false)
const schedulerStatus = ref<any>({})
const historyList = ref<any[]>([])
const historyPage = ref(1)
const historyPageSize = 20
const defaultForm = {
  id: null, fundCode: '', taskType: 'POST_MARKET',
  cronExpression: '0 30 16 * * MON-FRI', channels: [] as string[],
  enabled: true, description: '', timeoutMinutes: 30,
  scheduleMode: 'weekday', scheduleTime: '16:30', minuteOffset: 0,
  intervalMinutes: 30, weekDay: 'FRI'
}
const form = reactive<any>({...defaultForm})

const cronPresets: Record<string, string> = {
  POST_MARKET: '0 30 16 * * MON-FRI',
  DAILY_REPORT: '0 0 20 * * MON-FRI',
  WEEKLY_REPORT: '0 0 21 * * FRI'
}
const taskTypeLabel = (t: string) =>
    ({
      POST_MARKET: '盘后分析',
      DAILY_REPORT: '每日报告',
      WEEKLY_REPORT: '每周报告',
      CUSTOM: '自定义',
      SYSTEM: '系统任务'
    }[t] || t)
const taskTypeTag = (t: string) =>
    ({
      POST_MARKET: '',
      DAILY_REPORT: 'success',
      WEEKLY_REPORT: 'warning',
      CUSTOM: 'info',
      SYSTEM: 'primary'
    }[t] || 'info') as any
const cronReadable = (cron: string) => {
  const map: Record<string, string> = {
    '0 30 16 * * MON-FRI': '工作日 16:30',
    '0 0 20 * * MON-FRI': '工作日 20:00',
    '0 0 21 * * FRI': '每周五 21:00'
  }
  return map[cron] || cron
}
const onTaskTypeChange = (type: string) => {
  if (cronPresets[type]) {
    form.cronExpression = cronPresets[type]
    form.scheduleMode = type === 'WEEKLY_REPORT' ? 'weekly' : 'weekday'
    form.scheduleTime = type === 'POST_MARKET' ? '16:30' : type === 'DAILY_REPORT' ? '20:00' : '21:00'
    if (type === 'WEEKLY_REPORT') form.weekDay = 'FRI'
  }
}
const onScheduleModeChange = () => {
  buildCron()
}
const buildCron = () => {
  const mode = form.scheduleMode
  if (mode === 'custom') return
  const [h, m] = (form.scheduleTime || '16:30').split(':').map(Number)
  if (mode === 'daily') form.cronExpression = `0 ${m} ${h} * * *`
  else if (mode === 'weekday') form.cronExpression = `0 ${m} ${h} * * MON-FRI`
  else if (mode === 'hourly') form.cronExpression = `0 ${form.minuteOffset} * * * *`
  else if (mode === 'interval') form.cronExpression = `0 */${form.intervalMinutes} * * * *`
  else if (mode === 'weekly') form.cronExpression = `0 ${m} ${h} * * ${form.weekDay}`
}
const loadData = async () => {
  loading.value = true
  try {
    const list = (await getTaskConfigs()) as any || []
    taskList.value = list.map((t: any) => ({
      ...t,
      channels: t.notificationChannels ? t.notificationChannels.split(',').filter(Boolean) : []
    }))
  } catch {
  } finally {
    loading.value = false
  }
  try {
    const s: any = await getSchedulerStatus()
    schedulerOnline.value = (s?.runningTasks ?? 0) >= 0
    schedulerStatus.value = {taskCount: s?.runningTasks ?? 0}
  } catch {
  }
}
const parseCronToMode = (cron: string) => {
  if (!cron) return
  const parts = cron.split(' ')
  if (parts.length < 6) {
    form.scheduleMode = 'custom';
    return
  }
  const [, min, hour, , , dow] = parts
  if (min.startsWith('*/')) {
    form.scheduleMode = 'interval';
    form.intervalMinutes = parseInt(min.slice(2));
    return
  }
  if (hour === '*') {
    form.scheduleMode = 'hourly';
    form.minuteOffset = parseInt(min);
    return
  }
  if (dow === 'MON-FRI') {
    form.scheduleMode = 'weekday';
    form.scheduleTime = `${hour.padStart(2, '0')}:${min.padStart(2, '0')}`;
    return
  }
  if (dow === '*') {
    form.scheduleMode = 'daily';
    form.scheduleTime = `${hour.padStart(2, '0')}:${min.padStart(2, '0')}`;
    return
  }
  if (/^(MON|TUE|WED|THU|FRI|SAT|SUN)$/.test(dow)) {
    form.scheduleMode = 'weekly';
    form.weekDay = dow;
    form.scheduleTime = `${hour.padStart(2, '0')}:${min.padStart(2, '0')}`;
    return
  }
  form.scheduleMode = 'custom'
}
const openTaskDialog = (row?: any) => {
  if (row) {
    Object.assign(form, {...row, channels: row.channels || []})
    parseCronToMode(row.cronExpression)
  } else {
    Object.assign(form, {...defaultForm, channels: []})
  }
  showDialog.value = true
}
const doSave = async () => {
  if (!form.fundCode.trim()) return ElMessage.warning('请输入基金代码')
  if (!form.channels?.length) return ElMessage.warning('请选择推送渠道')
  saving.value = true
  try {
    const payload = {...form, notificationChannels: (form.channels || []).join(',')}
    delete payload.channels
    delete payload.scheduleMode
    delete payload.scheduleTime
    delete payload.minuteOffset
    delete payload.intervalMinutes
    delete payload.weekDay
    await saveTaskConfig(payload)
    ElMessage.success('保存成功');
    showDialog.value = false;
    await loadData()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
const doDelete = async (id: number) => {
  await ElMessageBox.confirm('确定删除该任务？', '提示', {type: 'warning'})
  try {
    await deleteTaskConfig(id);
    await loadData();
    ElMessage.success('已删除')
  } catch { /* cancelled */
  }
}
const doTrigger = async (id: number) => {
  triggeringId.value = id
  try {
    await triggerTask(id);
    ElMessage.success('已触发执行')
  } catch {
    ElMessage.error('触发失败')
  } finally {
    triggeringId.value = null
  }
}
const toggleEnabled = async (row: any) => {
  try {
    const payload = {...row, notificationChannels: (row.channels || []).join(',')}
    delete payload.channels
    await saveTaskConfig(payload)
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch {
    row.enabled = !row.enabled;
    ElMessage.error('操作失败')
  }
}
/** 根据 cron 表达式计算下次触发时间（前端简易实现） */
const nextFireTime = (cron: string, enabled: any): string => {
  if (!enabled) return '已禁用'
  if (!cron) return '-'
  try {
    const parts = cron.split(' ')
    if (parts.length < 6) return '-'
    const [, min, hour, , , dow] = parts
    const now = new Date()
    const dayNames = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']
    // 每N分钟
    if (min.startsWith('*/')) {
      const interval = parseInt(min.slice(2))
      const next = new Date(now)
      next.setSeconds(0, 0)
      next.setMinutes(Math.ceil(now.getMinutes() / interval) * interval)
      if (next <= now) next.setMinutes(next.getMinutes() + interval)
      return formatTime(next)
    }
    // 每小时
    if (hour === '*') {
      const m = parseInt(min)
      const next = new Date(now)
      next.setSeconds(0, 0);
      next.setMinutes(m)
      if (next <= now) next.setHours(next.getHours() + 1)
      return formatTime(next)
    }
    // 指定时间
    const h = parseInt(hour), m = parseInt(min)
    const allowedDays = parseDow(dow)
    for (let d = 0; d < 8; d++) {
      const candidate = new Date(now)
      candidate.setDate(candidate.getDate() + d)
      candidate.setHours(h, m, 0, 0)
      if (candidate <= now) continue
      if (allowedDays.length && !allowedDays.includes(candidate.getDay())) continue
      return formatTime(candidate)
    }
    return '-'
  } catch {
    return '-'
  }
}
const parseDow = (dow: string): number[] => {
  if (dow === '*') return []
  const map: Record<string, number> = {SUN: 0, MON: 1, TUE: 2, WED: 3, THU: 4, FRI: 5, SAT: 6}
  if (dow === 'MON-FRI') return [1, 2, 3, 4, 5]
  if (map[dow] !== undefined) return [map[dow]]
  return []
}
const formatTime = (d: Date): string => {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const loadHistory = async () => {
  historyPage.value = 1
  try {
    historyList.value = (await getNotificationHistory(1, historyPageSize)) as any || []
  } catch {
  }
}
const loadMoreHistory = async () => {
  historyPage.value++
  try {
    const more = (await getNotificationHistory(historyPage.value, historyPageSize)) as any || []
    historyList.value.push(...more)
  } catch {
  }
}
onMounted(async () => {
  await loadData();
  loadHistory()
})
</script>

<style lang="scss" scoped>
.cron-code {
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #606266;
}

.content-preview {
  color: #606266;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    color: #409eff;
  }
}
</style>
