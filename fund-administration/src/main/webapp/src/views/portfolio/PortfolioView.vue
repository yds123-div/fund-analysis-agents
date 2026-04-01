<template>
  <div class="page-container">
    <!-- 持仓管理 -->
    <el-card shadow="hover" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span class="card-title">持仓管理</span>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-input-number v-model="aiBudget" :min="0" :precision="0" :step="10000" controls-position="right"
                             placeholder="可投入预算" size="small" style="width: 160px"/>
            <el-button size="small" type="warning" @click="openAiDialog">
              <el-icon>
                <MagicStick/>
              </el-icon>
              AI 分析
            </el-button>
            <el-button size="small" type="primary" @click="openPortfolioDialog()">
              <el-icon>
                <Plus/>
              </el-icon>
              添加持仓
            </el-button>
          </div>
        </div>
      </template>
      <el-skeleton :loading="portfolioLoading" :rows="3" animated>
        <template #default>
          <el-empty v-if="!portfolioList.length" description="暂无持仓记录"/>
          <el-table v-else :data="mergedPortfolio" stripe>
            <el-table-column label="基金代码" prop="fundCode" width="110"/>
            <el-table-column label="基金名称" min-width="160" prop="fundName" show-overflow-tooltip/>
            <el-table-column label="持有份额" prop="holdingAmount" width="120"/>
            <el-table-column label="平均成本" prop="avgCost" width="100"/>
            <el-table-column label="当前净值" width="100">
              <template #default="{ row }">{{ row.currentNav?.toFixed(4) ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="市值" width="120">
              <template #default="{ row }">{{ row.marketValue?.toFixed(2) ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="总收益" width="120">
              <template #default="{ row }">
                <span v-if="row.pnl != null" :style="{color: row.pnl >= 0 ? '#f56c6c' : '#67c23a', fontWeight: 600}">
                  {{ row.pnl >= 0 ? '+' : '' }}{{ row.pnl?.toFixed(2) }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="收益率" width="100">
              <template #default="{ row }">
                <span v-if="row.pnlRate != null && row.avgCost > 0"
                      :style="{color: row.pnlRate >= 0 ? '#f56c6c' : '#67c23a', fontWeight: 600}">
                  {{ (row.pnlRate * 100).toFixed(2) }}%
                </span>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="定投" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.autoDip" size="small" type="success">
                  {{ row.dipFrequency === 'WEEKLY' ? '每周' : row.dipFrequency === 'BIWEEKLY' ? '每两周' : '每月' }}
                  {{ row.dipAmount }}元
                </el-tag>
                <span v-else style="color: #c0c4cc; font-size: 12px">未开启</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" @click="openPortfolioDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="doRemovePortfolio(row.fundCode)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-skeleton>
    </el-card>
    <!-- 收益明细 -->
    <el-card v-if="pnlList.length" shadow="hover" style="margin-bottom: 20px">
      <template #header><span class="card-title">收益明细</span></template>
      <el-table :data="pnlList" stripe>
        <el-table-column label="基金代码" prop="fundCode" width="120"/>
        <el-table-column label="基金名称" min-width="160" prop="fundName" show-overflow-tooltip/>
        <el-table-column label="当前净值" prop="currentNav" width="120"/>
        <el-table-column label="市值" prop="marketValue" width="140"/>
        <el-table-column label="盈亏" width="140">
          <template #default="{ row }">
            <span v-if="row.pnl != null" :style="{color: row.pnl >= 0 ? '#f56c6c' : '#67c23a', fontWeight: 600}">
              {{ row.pnl >= 0 ? '+' : '' }}{{ row.pnl?.toFixed(2) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="收益率" width="120">
          <template #default="{ row }">
            <span v-if="row.pnlRate != null"
                  :style="{color: row.pnlRate >= 0 ? '#f56c6c' : '#67c23a', fontWeight: 600}">
              {{ (row.pnlRate * 100).toFixed(2) }}%
            </span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 图表区域 -->
    <div class="chart-grid">
      <el-card class="chart-card" shadow="hover">
        <template #header><span class="card-title">持仓比例分布</span></template>
        <div ref="pieChartRef" class="chart-box"></div>
      </el-card>
      <el-card class="chart-card" shadow="hover">
        <template #header><span class="card-title">持仓风格雷达</span></template>
        <div ref="radarChartRef" class="chart-box"></div>
      </el-card>
      <el-card class="chart-card" shadow="hover">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span class="card-title">收益走势</span>
            <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
              <el-radio-button :value="7">7天</el-radio-button>
              <el-radio-button :value="30">30天</el-radio-button>
              <el-radio-button :value="90">90天</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <div v-if="!portfolioList.length" class="chart-box"
             style="display:flex;align-items:center;justify-content:center;color:#909399">
          暂无持仓数据，添加持仓后开始记录收益
        </div>
        <div v-else ref="lineChartRef" class="chart-box"></div>
      </el-card>
      <el-card class="chart-card" shadow="hover">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span class="card-title">盈利预估</span>
            <el-tag v-if="forecastTime" effect="plain" size="small" type="info">{{ forecastTime }}</el-tag>
          </div>
        </template>
        <div v-if="!portfolioList.length" class="chart-box"
             style="display:flex;align-items:center;justify-content:center;color:#909399">
          暂无持仓数据
        </div>
        <div v-else-if="forecastLoading" class="chart-box"
             style="display:flex;align-items:center;justify-content:center;color:#909399">
          <el-icon class="is-loading">
            <Loading/>
          </el-icon>&nbsp;预估计算中...
        </div>
        <div v-else ref="forecastChartRef" class="chart-box"></div>
      </el-card>
      <el-card class="chart-card" shadow="hover">
        <template #header><span class="card-title">持仓集中度</span></template>
        <div ref="barChartRef" class="chart-box"></div>
      </el-card>
    </div>
    <!-- AI 分析弹框 -->
    <el-dialog v-model="aiDialogVisible" destroy-on-close title="AI 持仓分析" top="5vh" width="800px">
      <div v-if="aiAnalyzing" style="text-align: center; padding: 40px 0">
        <el-skeleton :rows="8" animated/>
        <p style="color: #909399; margin-top: 16px">AI 正在分析您的持仓组合，请稍候...</p>
      </div>
      <div v-else-if="aiResult">
        <el-tag v-if="aiTime" effect="plain" size="small" style="margin-bottom: 12px" type="info">{{ aiTime }}</el-tag>
        <div class="ai-result" v-html="renderMd(aiResult)"/>
      </div>
      <div v-else style="text-align: center; padding: 40px; color: #909399">暂无分析结果</div>
    </el-dialog>
    <!-- 添加/编辑持仓对话框 -->
    <el-dialog v-model="showPortfolioDialog" :title="editingPortfolio._isEdit ? '编辑持仓' : '添加持仓'" width="450px">
      <el-form :model="editingPortfolio" label-width="100px">
        <el-form-item label="基金代码">
          <el-input v-model="editingPortfolio.fundCode" :disabled="!!editingPortfolio._isEdit" placeholder="如 110011"/>
        </el-form-item>
        <template v-if="!editingPortfolio._isEdit">
          <el-form-item label="投入总金额">
            <el-input-number v-model="editingPortfolio.totalAmount" :min="100" :precision="0" :step="1000"
                             controls-position="right" placeholder="投入金额(元)" style="width: 100%"/>
          </el-form-item>
          <el-form-item label="包含今日收益">
            <el-switch v-model="editingPortfolio.includeTodayReturn"/>
            <span style="color: #909399; font-size: 12px; margin-left: 8px">开启则按实时估值计算份额</span>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="持有份额">
            <el-input-number v-model="editingPortfolio.holdingAmount" :min="0" :precision="2" style="width: 100%"/>
          </el-form-item>
          <el-form-item label="平均成本">
            <el-input-number v-model="editingPortfolio.avgCost" :min="0" :precision="4" style="width: 100%"/>
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input v-model="editingPortfolio.notes"/>
        </el-form-item>
        <el-form-item label="开启定投">
          <el-switch v-model="editingPortfolio.autoDip"/>
        </el-form-item>
        <el-form-item v-if="editingPortfolio.autoDip" label="定投金额">
          <el-input-number v-model="editingPortfolio.dipAmount" :min="100" :precision="0" :step="100"
                           placeholder="每期定投金额(元)" style="width: 100%"/>
        </el-form-item>
        <el-form-item v-if="editingPortfolio.autoDip" label="定投频率">
          <el-select v-model="editingPortfolio.dipFrequency" style="width: 100%">
            <el-option label="每周" value="WEEKLY"/>
            <el-option label="每两周" value="BIWEEKLY"/>
            <el-option label="每月" value="MONTHLY"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPortfolioDialog = false">取消</el-button>
        <el-button :loading="savingPortfolio" type="primary" @click="doSavePortfolio">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup>
import {computed, nextTick, onMounted, onUnmounted, reactive, ref} from 'vue'
import {
  addOrUpdatePortfolio,
  aiPortfolioAnalysis,
  getLatestAiAnalysis,
  getPortfolioAnalysis,
  getPortfolioForecast,
  getPortfolioList,
  getPortfolioPnL,
  getPortfolioTrend,
  removePortfolio
} from '@/api/portfolio'
import {ElMessage, ElMessageBox} from 'element-plus'
import {marked} from 'marked'
import * as echarts from 'echarts'

const pieChartRef = ref<HTMLElement>()
const radarChartRef = ref<HTMLElement>()
const lineChartRef = ref<HTMLElement>()
const barChartRef = ref<HTMLElement>()
const forecastChartRef = ref<HTMLElement>()
let pieChart: echarts.ECharts | null = null
let radarChart: echarts.ECharts | null = null
let lineChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null
let forecastChart: echarts.ECharts | null = null
const trendDays = ref(30)
const forecastLoading = ref(false)
const forecastTime = ref('')

const portfolioList = ref<any[]>([])
const portfolioLoading = ref(true)
const showPortfolioDialog = ref(false)
const savingPortfolio = ref(false)
const editingPortfolio = reactive<any>({
  fundCode: '', totalAmount: 10000, includeTodayReturn: false,
  holdingAmount: 0, avgCost: 0, notes: '', _isEdit: false,
  autoDip: false, dipAmount: 500, dipFrequency: 'MONTHLY'
})
const pnlList = ref<any[]>([])
const aiBudget = ref(0)
const aiAnalyzing = ref(false)
const aiResult = ref('')
const aiTime = ref('')
const aiDialogVisible = ref(false)
const renderMd = (text: string) => marked.parse(text || '', {async: false}) as string
const mergedPortfolio = computed(() => {
  if (!pnlList.value.length) return portfolioList.value
  const pnlMap = new Map(pnlList.value.map(p => [p.fundCode, p]))
  return portfolioList.value.map(p => {
    const pnl = pnlMap.get(p.fundCode)
    return pnl ? {
      ...p,
      currentNav: pnl.currentNav,
      marketValue: pnl.marketValue,
      pnl: pnl.pnl,
      pnlRate: pnl.pnlRate
    } : p
  })
})
const initPieChart = (distribution: any[]) => {
  if (!pieChartRef.value) return
  pieChart = echarts.init(pieChartRef.value)
  pieChart.setOption({
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    legend: {bottom: 0, type: 'scroll'},
    color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'],
    series: [{
      type: 'pie', radius: ['35%', '65%'], center: ['50%', '45%'],
      label: {formatter: '{b}\n{d}%', fontSize: 11},
      data: distribution.map(d => ({
        name: d.fundName ? `${d.fundName}(${d.fundCode})` : d.fundCode,
        value: d.marketValue
      }))
    }]
  })
}
const initRadarChart = (style: any) => {
  if (!radarChartRef.value) return
  radarChart = echarts.init(radarChartRef.value)
  radarChart.setOption({
    tooltip: {},
    radar: {
      indicator: [{name: '成长', max: 100}, {name: '价值', max: 100}, {name: '均衡', max: 100},
        {name: '大盘', max: 100}, {name: '中盘', max: 100}, {name: '小盘', max: 100}],
      shape: 'circle',
      splitArea: {areaStyle: {color: ['rgba(64,158,255,0.02)', 'rgba(64,158,255,0.06)']}}
    },
    series: [{
      type: 'radar', symbol: 'circle', symbolSize: 6, lineStyle: {width: 2}, areaStyle: {opacity: 0.25},
      data: [{
        value: [style.growth || 0, style.value || 0, style.balanced || 0, style.largeCap || 0, style.midCap || 0, style.smallCap || 0],
        name: '风格分布', itemStyle: {color: '#409eff'}
      }]
    }]
  })
}
const initLineChart = (trend: any[]) => {
  if (!lineChartRef.value || !trend.length) return
  if (!lineChart) lineChart = echarts.init(lineChartRef.value)
  lineChart.setOption({
    tooltip: {
      trigger: 'axis', formatter: (p: any) => {
        const d = p[0];
        return `${d.axisValue}<br/>收益率: <b>${(d.value * 100).toFixed(2)}%</b>`
      }
    },
    grid: {left: 50, right: 20, top: 20, bottom: 30},
    xAxis: {type: 'category', data: trend.map(t => t.date), boundaryGap: false, axisLabel: {fontSize: 11}},
    yAxis: {
      type: 'value', axisLabel: {formatter: (v: number) => (v * 100).toFixed(1) + '%', fontSize: 11},
      splitLine: {lineStyle: {type: 'dashed'}}
    },
    series: [{
      type: 'line', data: trend.map(t => t.returnRate), smooth: true,
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
const initForecastChart = (data: any[]) => {
  if (!forecastChartRef.value || !data.length) return
  forecastChart = echarts.init(forecastChartRef.value)
  const days = data.map(d => `${d.day}天`)
  forecastChart.setOption({
    tooltip: {
      trigger: 'axis', formatter: (params: any) => {
        const item = data[params[0]?.dataIndex] || {}
        let html = `<b>${params[0]?.axisValue}后</b><br/>`
        params.forEach((p: any) => {
          html += `${p.marker}${p.seriesName}: ${p.value?.toFixed(1)}%<br/>`
        })
        if (item.reason) html += `<br/><span style="color:#909399;font-size:12px">📊 ${item.reason}</span>`
        return html
      }
    },
    legend: {bottom: 0},
    grid: {left: 50, right: 20, top: 20, bottom: 40},
    xAxis: {type: 'category', data: days, axisLabel: {fontSize: 11}},
    yAxis: {type: 'value', axisLabel: {formatter: '{value}%', fontSize: 11}, splitLine: {lineStyle: {type: 'dashed'}}},
    series: [
      {
        name: '乐观', type: 'line', data: data.map(d => d.optimistic), smooth: true,
        lineStyle: {color: '#67c23a', type: 'dashed'}, itemStyle: {color: '#67c23a'}
      },
      {
        name: '中性', type: 'line', data: data.map(d => d.neutral), smooth: true,
        lineStyle: {color: '#409eff', width: 2}, itemStyle: {color: '#409eff'},
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            {offset: 0, color: 'rgba(64,158,255,0.02)'}, {offset: 1, color: 'rgba(64,158,255,0.15)'}])
        }
      },
      {
        name: '悲观', type: 'line', data: data.map(d => d.pessimistic), smooth: true,
        lineStyle: {color: '#f56c6c', type: 'dashed'}, itemStyle: {color: '#f56c6c'}
      }
    ]
  })
}
const initBarChart = (distribution: any[]) => {
  if (!barChartRef.value || !distribution.length) return
  barChart = echarts.init(barChartRef.value)
  const sorted = [...distribution].sort((a, b) => b.percent - a.percent)
  const names = sorted.map(d => d.fundName || d.fundCode)
  const values = sorted.map(d => +Number(d.percent).toFixed(1))
  const colors = values.map(v => v > 50 ? '#f56c6c' : v > 30 ? '#e6a23c' : '#67c23a')
  barChart.setOption({
    tooltip: {trigger: 'axis', formatter: (p: any) => `${p[0].name}: ${p[0].value}%`},
    grid: {left: 120, right: 40, top: 10, bottom: 20},
    xAxis: {type: 'value', max: 100, axisLabel: {formatter: '{value}%', fontSize: 11}},
    yAxis: {type: 'category', data: names, axisLabel: {fontSize: 11, width: 100, overflow: 'truncate'}},
    series: [{
      type: 'bar', data: values.map((v, i) => ({value: v, itemStyle: {color: colors[i]}})),
      barMaxWidth: 24, label: {show: true, position: 'right', formatter: '{c}%', fontSize: 11}
    }]
  })
}
const loadAnalysis = async () => {
  try {
    const res: any = await getPortfolioAnalysis()
    await nextTick()
    initPieChart(res.distribution || [])
    initRadarChart(res.styleAnalysis || {})
    initBarChart(res.distribution || [])
  } catch {
  }
}
const loadTrend = async () => {
  try {
    const res: any = await getPortfolioTrend(trendDays.value)
    await nextTick()
    initLineChart(res || [])
  } catch {
  }
}
const loadForecast = async () => {
  if (!portfolioList.value.length) return
  forecastLoading.value = true
  try {
    const res: any = await getPortfolioForecast()
    forecastTime.value = res?.time || ''
    let data = res?.data
    if (typeof data === 'string') {
      const cleaned = data.replace(/```(?:json)?\s*/g, '').replace(/```/g, '').trim()
      try {
        data = JSON.parse(cleaned)
      } catch {
        data = []
      }
    }
    if (Array.isArray(data) && data.length) {
      await nextTick();
      initForecastChart(data)
    }
  } catch {
  } finally {
    forecastLoading.value = false
  }
}
const loadPortfolioList = async () => {
  portfolioLoading.value = true
  try {
    portfolioList.value = (await getPortfolioList()) as any
  } catch {
  } finally {
    portfolioLoading.value = false
  }
}
const loadPnL = async () => {
  try {
    pnlList.value = (await getPortfolioPnL()) as any
  } catch {
  }
}
const openPortfolioDialog = (row?: any) => {
  if (row) {
    Object.assign(editingPortfolio, {
      fundCode: row.fundCode, holdingAmount: row.holdingAmount, avgCost: row.avgCost,
      notes: row.notes || '', _isEdit: true, totalAmount: 0, includeTodayReturn: false,
      autoDip: row.autoDip || false, dipAmount: row.dipAmount || 500, dipFrequency: row.dipFrequency || 'MONTHLY'
    })
  } else {
    Object.assign(editingPortfolio, {
      fundCode: '', holdingAmount: 0, avgCost: 0, notes: '', _isEdit: false,
      totalAmount: 10000, includeTodayReturn: false,
      autoDip: false, dipAmount: 500, dipFrequency: 'MONTHLY'
    })
  }
  showPortfolioDialog.value = true
}
const doSavePortfolio = async () => {
  if (!editingPortfolio.fundCode.trim()) return ElMessage.warning('请输入基金代码')
  savingPortfolio.value = true
  try {
    const {_isEdit, ...data} = editingPortfolio
    await addOrUpdatePortfolio(data)
    ElMessage.success('保存成功');
    showPortfolioDialog.value = false
    await loadPortfolioList();
    await loadPnL();
    await loadAnalysis()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    savingPortfolio.value = false
  }
}
const doRemovePortfolio = async (fundCode: string) => {
  await ElMessageBox.confirm('确定删除该持仓？', '提示', {type: 'warning'})
  try {
    await removePortfolio(fundCode);
    await loadPortfolioList();
    await loadPnL();
    ElMessage.success('已删除')
  } catch {
  }
}
const openAiDialog = () => {
  aiDialogVisible.value = true
  if (!aiResult.value) doAiAnalysis()
}
const doAiAnalysis = async () => {
  aiAnalyzing.value = true;
  aiResult.value = ''
  try {
    const res: any = await aiPortfolioAnalysis(aiBudget.value)
    aiResult.value = res?.content || (typeof res === 'string' ? res : JSON.stringify(res))
    aiTime.value = res?.time || ''
  } catch {
    ElMessage.error('AI 分析失败')
  } finally {
    aiAnalyzing.value = false
  }
}
const handleResize = () => {
  pieChart?.resize();
  radarChart?.resize();
  lineChart?.resize();
  barChart?.resize();
  forecastChart?.resize()
}
onMounted(async () => {
  await loadPortfolioList()
  loadPnL();
  loadAnalysis();
  loadTrend();
  loadForecast()
  getLatestAiAnalysis().then((res: any) => {
    if (res?.content) {
      aiResult.value = res.content;
      aiTime.value = res.time || ''
    }
  }).catch(() => {
  })
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose();
  radarChart?.dispose();
  lineChart?.dispose();
  barChart?.dispose();
  forecastChart?.dispose()
})
</script>
<style lang="scss" scoped>
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.chart-card {
  min-height: 320px;
}

.chart-box {
  width: 100%;
  height: 260px;
}

.card-title {
  font-weight: 600;
}

.ai-result {
  line-height: 1.8;
  color: #303133;
  font-size: 14px;

  :deep(h1), :deep(h2), :deep(h3) {
    margin: 16px 0 8px;
    color: #1a1a1a;
  }

  :deep(ul), :deep(ol) {
    padding-left: 20px;
  }

  :deep(li) {
    margin: 4px 0;
  }

  :deep(strong) {
    color: #409eff;
  }

  :deep(code) {
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 13px;
  }
}
</style>
