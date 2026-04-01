<template>
  <div class="page-container">
    <!-- 市场指数卡片 -->
    <div class="card-grid card-grid-3" style="margin-bottom: 20px">
      <el-card v-for="idx in marketIndices" :key="idx.code" class="index-card" shadow="hover">
        <div class="index-name">{{ idx.name }}</div>
        <div :style="{color: idx.changePercent >= 0 ? '#f56c6c' : '#67c23a'}" class="index-price">
          {{ idx.price?.toFixed(2) }}
        </div>
        <div :style="{color: idx.changePercent >= 0 ? '#f56c6c' : '#67c23a'}" class="index-change">
          {{ idx.changePercent >= 0 ? '+' : '' }}{{ idx.change?.toFixed(2) }}
          ({{ idx.changePercent >= 0 ? '+' : '' }}{{ idx.changePercent?.toFixed(2) }}%)
          <span class="index-arrow">{{ idx.changePercent >= 0 ? '▲' : '▼' }}</span>
        </div>
      </el-card>
    </div>
    <!-- 统计卡片 -->
    <div class="dash-stat-grid" style="margin-bottom: 20px">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-label">数据源状态</div>
        <div :style="{color: dsOnline ? '#67c23a' : '#f56c6c'}" class="stat-value">
          {{ dsOnline ? '在线' : '离线' }}
        </div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-label">AI 提供商</div>
        <div class="stat-value">{{ providerCount }}</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-label">自选基金</div>
        <div class="stat-value">{{ watchCount }}</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-label">持仓数量</div>
        <div class="stat-value">{{ overview.portfolioSummary?.count || 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-label">今日分析</div>
        <div class="stat-value">{{ overview.taskStats?.todayTotal || 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-label">运行中任务</div>
        <div class="stat-value" style="color: #e6a23c">{{ overview.taskStats?.running || 0 }}</div>
      </el-card>
    </div>
    <!-- 图表行 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span style="font-weight: 600">持仓收益趋势（近30天）</span></template>
          <div ref="trendChartRef" style="height: 280px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span style="font-weight: 600">持仓分布</span></template>
          <div ref="pieChartRef" style="height: 280px"></div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 快速分析 + 最近报告 -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span style="font-weight: 600">快速分析</span></template>
          <el-form inline @submit.prevent="doAnalysis">
            <el-form-item label="基金代码">
              <el-input v-model="fundCode" placeholder="如 110011" style="width: 160px"/>
            </el-form-item>
            <el-form-item>
              <el-button :loading="analyzing" type="primary" @click="doAnalysis">
                <el-icon>
                  <DataAnalysis/>
                </el-icon>
                开始分析
              </el-button>
            </el-form-item>
          </el-form>
          <el-progress v-if="analyzing || (dashProgress > 0 && dashProgress < 100)"
                       :percentage="dashProgress" :stroke-width="16" striped striped-flow
                       style="margin-top: 12px"/>
          <div v-if="analyzing" style="text-align: center; color: #909399; font-size: 13px; margin-top: 4px">
            {{ dashStage }}
          </div>
          <el-alert v-if="analysisBatch && dashProgress >= 100" :closable="false" style="margin-top: 12px"
                    type="success">
            分析完成！
            <el-link type="primary" @click="$router.push(`/analysis/report/${analysisBatch}`)">查看报告</el-link>
          </el-alert>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span style="font-weight: 600">数据源健康</span></template>
          <div v-for="(status, name) in dataSources" :key="name"
               style="padding: 8px 0; display: flex; align-items: center; justify-content: space-between">
            <span>{{ name }}</span>
            <el-tag :type="status ? 'success' : 'danger'" size="small">{{ status ? '在线' : '离线' }}</el-tag>
          </div>
          <el-empty v-if="!Object.keys(dataSources).length" :image-size="60" description="加载中..."/>
        </el-card>
      </el-col>
    </el-row>
    <!-- 最近报告 -->
    <el-card shadow="hover" style="margin-top: 20px">
      <template #header><span style="font-weight: 600">最近分析报告</span></template>
      <el-skeleton :loading="reportsLoading" :rows="4" animated>
        <template #default>
          <el-empty v-if="!recentReports.length" description="暂无分析报告"/>
          <el-table v-else :data="recentReports" stripe>
            <el-table-column label="基金代码" prop="fundCode" width="100"/>
            <el-table-column label="基金名称" min-width="140" prop="fundName" show-overflow-tooltip/>
            <el-table-column label="报告日期" prop="reportDate" width="120"/>
            <el-table-column label="类型" prop="reportType" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{
                    ({DAILY: '日报', WEEKLY: '周报', MONTHLY: '月报'} as Record<string, string>)[row.reportType] || row.reportType || '日报' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="批次号" min-width="200" prop="batchNo" show-overflow-tooltip/>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="primary"
                           @click="$router.push(`/analysis/report/${row.batchNo}`)">查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-skeleton>
    </el-card>
    <!-- 系统架构 -->
    <el-card shadow="hover" style="margin-top: 20px">
      <template #header><span style="font-weight: 600">系统架构</span></template>
      <SystemArchGraph/>
    </el-card>
  </div>
</template>
<script lang="ts" setup>
import {nextTick, onMounted, onUnmounted, ref} from 'vue'
import {getHealth, getProviders} from '@/api/ai-config'
import {listReports, triggerAnalysis} from '@/api/analysis'
import {getWatchList} from '@/api/watchlist'
import {getPortfolioAnalysis, getPortfolioTrend} from '@/api/portfolio'
import SystemArchGraph from '@/components/SystemArchGraph.vue'
import {getDashboardOverview} from '@/api/dashboard'
import {getTaskProgress} from '@/api/task'
import {ElMessage} from 'element-plus'
import * as echarts from 'echarts'

const trendChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

const dsOnline = ref(false)
const dataSources = ref<Record<string, boolean>>({})
const providerCount = ref(0)
const watchCount = ref(0)
const overview = ref<any>({})
const marketIndices = ref<any[]>([])
const fundCode = ref('')
const analyzing = ref(false)
const analysisBatch = ref('')
const dashProgress = ref(0)
const dashStage = ref('')
const recentReports = ref<any[]>([])
const reportsLoading = ref(true)
let pollTimer: ReturnType<typeof setInterval> | null = null

/** 初始化收益趋势图 */
const initTrendChart = (data: any[]) => {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: {
      trigger: 'axis', formatter: (p: any) => {
        const d = p[0]
        return `${d.axisValue}<br/>收益率: <b style="color:${d.value >= 0 ? '#f56c6c' : '#67c23a'}">${(d.value * 100).toFixed(2)}%</b>`
      }
    },
    grid: {left: 50, right: 20, top: 16, bottom: 30},
    xAxis: {
      type: 'category', data: data.map(d => d.date), boundaryGap: false,
      axisLabel: {fontSize: 11}
    },
    yAxis: {
      type: 'value', splitLine: {lineStyle: {type: 'dashed'}},
      axisLabel: {formatter: (v: number) => (v * 100).toFixed(1) + '%', fontSize: 11}
    },
    series: [{
      type: 'line', data: data.map(d => d.returnRate), smooth: true,
      lineStyle: {width: 2, color: '#409eff'},
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          {offset: 0, color: 'rgba(64,158,255,0.05)'}, {offset: 1, color: 'rgba(64,158,255,0.45)'}
        ])
      },
      itemStyle: {color: '#409eff'}
    }]
  })
}
/** 初始化持仓饼图 */
const initPieChart = (distribution: any[]) => {
  if (!pieChartRef.value) return
  pieChart = echarts.init(pieChartRef.value)
  pieChart.setOption({
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    legend: {bottom: 0, type: 'scroll'},
    color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'],
    series: [{
      type: 'pie', radius: ['30%', '60%'], center: ['50%', '42%'],
      label: {formatter: '{b}\n{d}%', fontSize: 11},
      data: distribution.map(d => ({
        name: d.fundName ? `${d.fundName}(${d.fundCode})` : d.fundCode,
        value: d.marketValue
      }))
    }]
  })
}
const handleResize = () => {
  trendChart?.resize();
  pieChart?.resize()
}

onMounted(async () => {
  const tasks = [
    getDashboardOverview().then((d: any) => {
      overview.value = d || {}
      marketIndices.value = d?.marketIndices || []
    }).catch(() => {
    }),
    getHealth().then((h: any) => {
      dsOnline.value = h.status === 'UP';
      dataSources.value = h.dataSources || {}
    }).catch(() => {
    }),
    getProviders().then((p: any) => {
      providerCount.value = p?.length || 0
    }).catch(() => {
    }),
    getWatchList().then((w: any) => {
      watchCount.value = w?.length || 0
    }).catch(() => {
    }),
    listReports(undefined, 1, 10).then((r: any) => {
      recentReports.value = r || []
    }).catch(() => {
    }),
  ]
  await Promise.allSettled(tasks)
  reportsLoading.value = false
  // 加载图表数据
  await nextTick()
  try {
    const trend: any = await getPortfolioTrend(30)
    initTrendChart(trend || [])
  } catch {
  }
  try {
    const analysis: any = await getPortfolioAnalysis()
    initPieChart(analysis?.distribution || [])
  } catch {
  }
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose();
  pieChart?.dispose()
})

const doAnalysis = async () => {
  if (!fundCode.value.trim()) return ElMessage.warning('请输入基金代码')
  analyzing.value = true;
  analysisBatch.value = '';
  dashProgress.value = 0;
  dashStage.value = '提交中...'
  try {
    const res: any = await triggerAnalysis(fundCode.value.trim())
    analysisBatch.value = res.batchNo
    pollTimer = setInterval(async () => {
      try {
        const data: any = await getTaskProgress(res.batchNo)
        dashProgress.value = data.progress;
        dashStage.value = data.stage
        if (data.progress >= 100 || data.progress < 0) {
          clearInterval(pollTimer!);
          pollTimer = null;
          analyzing.value = false
          if (data.progress >= 100) {
            ElMessage.success('分析完成')
            recentReports.value = ((await listReports(undefined, 1, 10)) as any) || recentReports.value
          }
        }
      } catch {
      }
    }, 3000)
  } catch (e: any) {
    ElMessage.error('触发失败: ' + e.message);
    analyzing.value = false
  }
}
</script>

<style lang="scss" scoped>
.index-card {
  text-align: center;
  padding: 8px 0;
}

.index-name {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.index-price {
  font-size: 28px;
  font-weight: 700;
}

.index-change {
  font-size: 13px;
  margin-top: 2px;
}

.index-arrow {
  font-size: 11px;
}

.dash-stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

@media (max-width: 767px) {
  .index-price {
    font-size: 20px;
  }
  .dash-stat-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 480px) {
  .dash-stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
