<template>
  <div class="page-container">
    <el-card shadow="hover" style="margin-bottom: 20px">
      <template #header><span style="font-weight: 600">基金搜索与分析</span></template>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-input v-model="fundCode" clearable placeholder="输入基金代码，如 110011" @keyup.enter="fetchFund">
            <template #prepend>基金代码</template>
          </el-input>
        </el-col>
        <el-col :span="4">
          <el-button :loading="loading" type="primary" @click="fetchFund">
            <el-icon>
              <Search/>
            </el-icon>
            查询
          </el-button>
        </el-col>
      </el-row>
    </el-card>
    <!-- 基金详情 -->
    <el-card v-if="fundInfo" shadow="hover" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">
            {{ fundInfo.fundName || fundInfo.fundCode }}
            <el-tag v-if="fundInfo.fundType" size="small" style="margin-left: 8px">{{ fundInfo.fundType }}</el-tag>
          </span>
          <el-button :loading="analyzing" type="success" @click="doAnalysis">
            <el-icon>
              <DataAnalysis/>
            </el-icon>
            开始 AI 分析
          </el-button>
        </div>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="基金代码">{{ fundInfo.fundCode }}</el-descriptions-item>
        <el-descriptions-item label="基金名称">{{ fundInfo.fundName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="基金经理">{{ fundInfo.fundManager || '-' }}</el-descriptions-item>
        <el-descriptions-item label="管理公司">{{ fundInfo.managementCompany || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最新净值">
          <span style="font-weight: 600; color: #409eff">{{ fundInfo.nav || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="累计净值">{{ fundInfo.accumulatedNav || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日涨跌">
          <span :style="{ color: (fundInfo.dayGrowthRate || 0) >= 0 ? '#f56c6c' : '#67c23a', fontWeight: 600 }">
            {{
              fundInfo.dayGrowthRate != null ? (fundInfo.dayGrowthRate > 0 ? '+' : '') + Number(fundInfo.dayGrowthRate).toFixed(2) + '%' : '-'
            }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="基金规模">{{
            fundInfo.fundScale ? fundInfo.fundScale + '亿' : '-'
          }}
        </el-descriptions-item>
      </el-descriptions>
      <!-- 实时估值 -->
      <el-descriptions v-if="estimate" :column="3" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="估算净值">
          <span style="font-weight: 600">{{ estimate.estimateNav }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="估算涨跌">
          <span :style="{ color: (estimate.estimateGrowthRate || 0) >= 0 ? '#f56c6c' : '#67c23a', fontWeight: 600 }">
            {{
              estimate.estimateGrowthRate ? (estimate.estimateGrowthRate > 0 ? '+' : '') + estimate.estimateGrowthRate + '%' : '-'
            }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="估算时间">{{ estimate.estimateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <!-- 净值走势图 -->
    <el-card v-if="navData.length" shadow="hover" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">净值走势</span>
          <el-radio-group v-model="navDays" size="small" @change="loadNavData">
            <el-radio-button :value="7">7天</el-radio-button>
            <el-radio-button :value="30">30天</el-radio-button>
            <el-radio-button :value="90">90天</el-radio-button>
            <el-radio-button :value="365">1年</el-radio-button>
            <el-radio-button :value="730">2年</el-radio-button>
            <el-radio-button :value="1095">3年</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <v-chart :option="navChartOption" autoresize style="height: 280px"/>
    </el-card>
    <!-- 分析失败提示 -->
    <el-alert v-if="currentProgress < 0" :closable="false" style="margin-bottom: 20px" type="error">
      分析失败：{{ currentStage }}
    </el-alert>
    <!-- 报告历史（含进行中任务） -->
    <el-card shadow="hover">
      <template #header><span style="font-weight: 600">分析记录</span></template>
      <el-skeleton :loading="historyLoading" :rows="4" animated>
        <template #default>
          <!-- 进行中的任务 -->
          <div v-if="analyzing" style="padding: 12px 0; border-bottom: 1px solid #ebeef5; margin-bottom: 12px">
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px">
              <el-tag size="small" type="warning">分析中</el-tag>
              <span style="font-size: 13px; color: #606266">{{ fundCode }} — {{ currentStage }}</span>
            </div>
            <el-progress :percentage="Math.max(0, currentProgress)" :stroke-width="12" striped striped-flow/>
          </div>
          <!-- 已完成的分析成功提示 -->
          <div v-if="analysisBatch && currentProgress >= 100"
               style="padding: 8px 12px; background: #f0f9eb; border-radius: 4px; margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between">
            <span style="color: #67c23a; font-size: 13px">分析完成！批次号：{{ analysisBatch }}</span>
            <el-button size="small" type="primary" @click="openReport(analysisBatch, fundInfo?.fundName)">查看报告
            </el-button>
          </div>
          <el-empty v-if="!reportHistory.length && !analyzing" description="暂无分析记录"/>
          <el-table v-if="reportHistory.length" :data="reportHistory" size="small" stripe>
            <el-table-column label="基金代码" prop="fundCode" width="100"/>
            <el-table-column label="基金名称" min-width="140" prop="fundName" show-overflow-tooltip/>
            <el-table-column label="日期" prop="reportDate" width="110"/>
            <el-table-column label="类型" prop="reportType" width="80">
              <template #default="{ row }">
                <el-tag size="small">{{
                    ({DAILY: '日报', WEEKLY: '周报', MONTHLY: '月报'} as Record<string, string>)[row.reportType] || row.reportType || '日报' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="批次号" min-width="160" prop="batchNo" show-overflow-tooltip/>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link size="small" type="primary"
                           @click="openReport(row.batchNo, row.fundName)">查看
                </el-button>
                <el-button link size="small" type="warning"
                           @click="openAgentFlow(row.batchNo)">流程
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="reportHistory.length" style="margin-top: 12px; text-align: right">
            <el-pagination v-model:current-page="historyPage" :page-size="10"
                           :total="reportHistory.length >= 10 ? 100 : reportHistory.length"
                           layout="prev, pager, next" small @current-change="loadHistory"/>
          </div>
        </template>
      </el-skeleton>
    </el-card>
    <!-- 报告预览弹窗 -->
    <el-dialog v-model="reportDialogVisible" :show-close="false" class="report-dialog"
               destroy-on-close fullscreen>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;width:100%">
          <span style="font-size:16px;font-weight:600">分析报告 — {{ previewReport?.fundCode }} {{
              previewFundName
            }}</span>
          <el-button text @click="reportDialogVisible = false">
            <el-icon :size="20">
              <Close/>
            </el-icon>
          </el-button>
        </div>
      </template>
      <el-skeleton :loading="reportLoading" :rows="10" animated>
        <template #default>
          <ReportViewer v-if="previewReport" :fund-name-prop="previewFundName" :report="previewReport"/>
        </template>
      </el-skeleton>
    </el-dialog>
    <!-- Agent 日志流程图弹窗 -->
    <AgentFlowDialog v-model="agentFlowVisible" :report="agentFlowReport"/>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {getFundBasic, getFundEstimate, getFundNav} from '@/api/fund'
import {getReport, listReports, triggerAnalysis} from '@/api/analysis'
import {ElMessage} from 'element-plus'
import {useRoute} from 'vue-router'
import ReportViewer from '@/components/ReportViewer.vue'
import AgentFlowDialog from '@/components/AgentFlowDialog.vue'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {LineChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const route = useRoute()
const fundCode = ref('')
const fundInfo = ref<any>(null)
const estimate = ref<any>(null)
const loading = ref(false)
const analyzing = ref(false)
const analysisBatch = ref('')
const currentProgress = ref(0)
const currentStage = ref('')
const reportHistory = ref<any[]>([])
const historyLoading = ref(true)
const historyPage = ref(1)
const navData = ref<any[]>([])
const navDays = ref(90)
let pollTimer: ReturnType<typeof setInterval> | null = null

const navChartOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  grid: {left: 60, right: 20, top: 20, bottom: 30},
  xAxis: {type: 'category', data: navData.value.map((n: any) => n.navDate).reverse(), axisLabel: {fontSize: 11}},
  yAxis: {type: 'value', scale: true, axisLabel: {fontSize: 11}},
  series: [
    {
      name: '单位净值', type: 'line', data: navData.value.map((n: any) => n.unitNav).reverse(),
      smooth: true, lineStyle: {width: 2, color: '#409eff'}, areaStyle: {color: 'rgba(64,158,255,0.1)'}
    },
    {
      name: '累计净值', type: 'line', data: navData.value.map((n: any) => n.accumulatedNav).reverse(),
      smooth: true, lineStyle: {width: 1, color: '#e6a23c', type: 'dashed'}
    }
  ]
}))

onMounted(() => {
  if (route.query.code) {
    fundCode.value = route.query.code as string;
    fetchFund()
  }
  loadHistory()
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

const loadHistory = async () => {
  historyLoading.value = true
  try {
    reportHistory.value = ((await listReports(undefined, historyPage.value, 10)) as any) || []
  } catch { /* ignore */
  } finally {
    historyLoading.value = false
  }
}

const fetchFund = async () => {
  if (!fundCode.value.trim()) return ElMessage.warning('请输入基金代码')
  loading.value = true;
  fundInfo.value = null;
  estimate.value = null;
  navData.value = []
  analysisBatch.value = '';
  currentProgress.value = 0;
  currentStage.value = ''
  try {
    fundInfo.value = await getFundBasic(fundCode.value.trim())
    try {
      estimate.value = await getFundEstimate(fundCode.value.trim())
    } catch {
    }
    try {
      navData.value = ((await getFundNav(fundCode.value.trim(), navDays.value)) as any) || []
    } catch {
    }
  } catch {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

/** 切换时间区间重新加载净值 */
const loadNavData = async () => {
  if (!fundCode.value.trim()) return
  try {
    navData.value = ((await getFundNav(fundCode.value.trim(), navDays.value)) as any) || []
  } catch {
  }
}

const doAnalysis = async () => {
  analyzing.value = true;
  analysisBatch.value = '';
  currentProgress.value = 0;
  currentStage.value = '提交中...'
  try {
    const res: any = await triggerAnalysis(fundCode.value.trim(), fundInfo.value?.fundName || '')
    analysisBatch.value = res.batchNo
    // 使用 SSE 推送进度，而不是轮询
    const eventSource = new EventSource(`/api/task/progress-stream/${res.batchNo}`)
    eventSource.addEventListener('progress', (event: any) => {
      try {
        const data = JSON.parse(event.data)
        currentProgress.value = data.progress
        currentStage.value = data.stage
        if (data.progress >= 100 || data.progress < 0) {
          eventSource.close()
          analyzing.value = false
          if (data.progress >= 100) {
            ElMessage.success('分析完成');
            loadHistory()
          } else ElMessage.error('分析失败: ' + data.stage)
        }
      } catch { /* ignore */
      }
    })
    eventSource.onerror = () => {
      eventSource.close()
      analyzing.value = false
      ElMessage.error('连接中断')
    }
  } catch (e: any) {
    ElMessage.error('触发失败: ' + e.message);
    analyzing.value = false
  }
}

// 报告弹窗
const reportDialogVisible = ref(false)
const reportLoading = ref(false)
const previewReport = ref<any>(null)
const previewFundName = ref('')
const openReport = async (batchNo: string, name?: string) => {
  reportDialogVisible.value = true
  reportLoading.value = true
  previewReport.value = null
  previewFundName.value = name || ''
  try {
    previewReport.value = await getReport(batchNo)
    if (!previewFundName.value && previewReport.value?.fundCode) {
      const match = reportHistory.value.find((r: any) => r.batchNo === batchNo)
      if (match?.fundName) previewFundName.value = match.fundName
    }
  } catch {
    ElMessage.error('报告加载失败')
  } finally {
    reportLoading.value = false
  }
}

// Agent 日志流程图
const agentFlowVisible = ref(false)
const agentFlowReport = ref<any>(null)
const openAgentFlow = async (batchNo: string) => {
  agentFlowVisible.value = true
  agentFlowReport.value = null
  try {
    agentFlowReport.value = await getReport(batchNo)
  } catch {
    ElMessage.error('报告加载失败')
  }
}
</script>
