<template>
  <div class="page-container">
    <!-- 市场温度扫描 -->
    <el-card shadow="hover" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">市场温度扫描</span>
          <el-tag v-if="scanTime" effect="plain" size="small" style="margin-left: 8px" type="info">
            {{ scanTime }}
          </el-tag>
          <el-button :loading="scanning" type="primary" @click="doScan">
            <el-icon>
              <Sunny/>
            </el-icon>
            开始扫描
          </el-button>
        </div>
      </template>
      <!-- 结构化展示 -->
      <div v-if="scanParsed" class="scan-dashboard">
        <el-row :gutter="20">
          <el-col :span="8">
            <div class="gauge-wrap">
              <v-chart :option="gaugeOption" autoresize style="height: 220px"/>
              <el-tag :type="levelTagType" class="level-tag" effect="dark" size="large">
                {{ scanParsed.level }}
              </el-tag>
            </div>
          </el-col>
          <el-col :span="16">
            <div class="summary-text" v-html="renderMd(scanParsed.summary || '')"/>
            <!-- 关键因素 -->
            <div v-if="scanParsed.keyFactors?.length" class="factors-grid">
              <div v-for="(f, i) in scanParsed.keyFactors" :key="i" class="factor-card">
                <el-icon color="#409eff">
                  <Flag/>
                </el-icon>
                <span>{{ typeof f === 'string' ? f : f.name || f }}</span>
              </div>
            </div>
            <!-- 建议 -->
            <div v-if="scanParsed.recommendation" class="recommendation-box">
              <el-icon color="#e6a23c">
                <InfoFilled/>
              </el-icon>
              <span v-html="renderMd(scanParsed.recommendation)"/>
            </div>
          </el-col>
        </el-row>
      </div>
      <!-- Markdown 降级 -->
      <div v-else-if="scanRaw" class="report-content" v-html="renderMd(scanRaw)"/>
      <el-empty v-if="!scanRaw && !scanning" :image-size="60" description="点击上方按钮开始市场扫描"/>
    </el-card>

    <!-- 智能选基推荐 -->
    <el-card shadow="hover" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div style="display: flex; align-items: center; gap: 8px">
            <span style="font-weight: 600">智能选基推荐</span>
            <el-tag v-if="screenTime" effect="plain" size="small" type="info">{{ screenTime }}</el-tag>
          </div>
          <el-form inline style="margin: 0">
            <el-form-item label="风险偏好" style="margin-bottom: 0">
              <el-select v-model="riskPreference" size="small" style="width: 130px">
                <el-option label="保守型" value="保守型"/>
                <el-option label="中等风险" value="中等风险"/>
                <el-option label="积极型" value="积极型"/>
              </el-select>
            </el-form-item>
            <el-form-item style="margin-bottom: 0">
              <el-button :loading="screening" size="small" type="success" @click="doScreen">
                <el-icon>
                  <Search/>
                </el-icon>
                智能选基
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </template>
      <!-- 结构化基金卡片 -->
      <div v-if="screenFundList.length" class="fund-cards">
        <div v-for="(fund, i) in screenFundList" :key="i" class="fund-card">
          <div class="fund-card-header">
            <span class="fund-name">{{ fund.name || fund.fundName || `推荐${i + 1}` }}</span>
            <el-tag v-if="fund.riskLevel" :type="riskTagType(fund.riskLevel)" size="small">
              {{ fund.riskLevel }}
            </el-tag>
          </div>
          <div v-if="fund.score != null" class="fund-score">
            <span class="score-label">评分</span>
            <el-progress :color="scoreColor(fund.score)" :percentage="Number(fund.score)"
                         :stroke-width="10" style="flex: 1"/>
          </div>
          <div v-if="fund.holdPeriod" class="fund-meta">
            <el-icon>
              <Timer/>
            </el-icon>
            建议持有：{{ fund.holdPeriod }}
          </div>
          <div v-if="fund.reason" class="fund-reason" v-html="renderMd(fund.reason)"/>
        </div>
      </div>
      <!-- Markdown 降级 -->
      <div v-else-if="screenRaw" class="report-content" v-html="renderMd(screenRaw)"/>
      <el-empty v-if="!screenRaw && !screening" :image-size="60" description="点击上方按钮开始智能选基"/>
    </el-card>

    <!-- 推荐历史 -->
    <el-card shadow="hover">
      <template #header><span style="font-weight: 600">推荐历史</span></template>
      <el-empty v-if="!recommendations.length" :image-size="60" description="暂无推荐记录"/>
      <el-table v-else :data="recommendations" size="small" stripe>
        <el-table-column label="批次号" prop="batchNo" show-overflow-tooltip width="160"/>
        <el-table-column label="时间" prop="createdAt" width="180"/>
        <el-table-column label="市场温度" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.marketTemperature" :type="row.marketTemperature === '过热' ? 'danger' : row.marketTemperature === '低迷' ? 'info' : 'warning'"
                    size="small">
              {{ row.marketTemperature }}
            </el-tag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="300">
          <template #default="{ row }">
            <el-popover placement="left" trigger="click" width="560">
              <template #reference>
                <span class="content-preview">{{ extractSummary(row.reportContent) }}</span>
              </template>
              <div style="max-height: 400px; overflow-y: auto; font-size: 13px; line-height: 1.7"
                   v-html="renderMd(extractFullContent(row.reportContent))"/>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {getLatestScan, listRecommendations, scanMarket, screenFunds} from '@/api/market'
import {ElMessage} from 'element-plus'
import {marked} from 'marked'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {GaugeChart} from 'echarts/charts'
import {CanvasRenderer} from 'echarts/renderers'

use([GaugeChart, CanvasRenderer])

const scanning = ref(false)
const scanRaw = ref('')
const scanParsed = ref<any>(null)
const scanTime = ref('')
const screening = ref(false)
const screenRaw = ref('')
const screenFundList = ref<any[]>([])
const riskPreference = ref('中等风险')
const screenTime = ref('')
const recommendations = ref<any[]>([])

const renderMd = (text: string) => marked.parse(text || '', {async: false}) as string

/** 从推荐历史内容中提取摘要 */
const extractSummary = (content: string): string => {
  if (!content) return '-'
  const parsed = tryParseJson(content)
  if (parsed) {
    return parsed.summary || parsed.marketTemperature || JSON.stringify(parsed).substring(0, 80) + '...'
  }
  return content.replace(/```json\s*/g, '').replace(/```/g, '').substring(0, 80) + '...'
}
/** 从推荐历史内容中提取完整可读文本 */
const extractFullContent = (content: string): string => {
  if (!content) return ''
  const parsed = tryParseJson(content)
  if (!parsed) return content.replace(/```json\s*/g, '').replace(/```/g, '')
  const parts: string[] = []
  if (parsed.summary) parts.push(`**市场概况**\n\n${parsed.summary}`)
  if (parsed.recommendations?.length) {
    parts.push('**推荐基金**\n')
    parsed.recommendations.forEach((f: any, i: number) => {
      parts.push(`${i + 1}. **${f.fundName || f.name || f.fundCode || ''}** ${f.fundCode ? `(${f.fundCode})` : ''}`)
      if (f.reason) parts.push(`   ${f.reason}`)
    })
  }
  return parts.length ? parts.join('\n\n') : JSON.stringify(parsed, null, 2)
}

/** 尝试从文本中提取 JSON */
const tryParseJson = (text: string): any => {
  if (!text) return null
  // 直接解析
  try {
    return JSON.parse(text)
  } catch {
  }
  // 从 markdown code block 中提取
  const match = text.match(/```(?:json)?\s*([\s\S]*?)```/)
  if (match) try {
    return JSON.parse(match[1].trim())
  } catch {
  }
  return null
}

// 仪表盘配色
const tempColor = (val: number) => {
  if (val <= 30) return '#409eff'
  if (val <= 60) return '#67c23a'
  if (val <= 80) return '#e6a23c'
  return '#f56c6c'
}
const gaugeOption = computed(() => {
  const temp = scanParsed.value?.temperature ?? 50
  return {
    series: [{
      type: 'gauge', startAngle: 200, endAngle: -20, min: 0, max: 100,
      pointer: {length: '60%', width: 6, itemStyle: {color: tempColor(temp)}},
      axisLine: {lineStyle: {width: 16, color: [[0.3, '#409eff'], [0.6, '#67c23a'], [0.8, '#e6a23c'], [1, '#f56c6c']]}},
      axisTick: {show: false}, splitLine: {show: false},
      axisLabel: {distance: 20, fontSize: 11, color: '#909399'},
      detail: {
        fontSize: 28, fontWeight: 700, offsetCenter: [0, '60%'], color: tempColor(temp),
        formatter: '{value}°'
      },
      data: [{value: temp}],
    }],
  }
})
const levelTagType = computed(() => {
  const l = scanParsed.value?.level || ''
  if (l.includes('过热') || l.includes('危')) return 'danger'
  if (l.includes('低迷') || l.includes('冷')) return 'info'
  if (l.includes('适中') || l.includes('震荡')) return 'warning'
  return 'success'
})

const riskTagType = (level: string) => {
  if (level.includes('高') || level.includes('积极')) return 'danger'
  if (level.includes('低') || level.includes('保守')) return 'success'
  return 'warning'
}
const scoreColor = (s: number) => s >= 80 ? '#67c23a' : s >= 60 ? '#409eff' : '#e6a23c'

onMounted(async () => {
  try {
    recommendations.value = ((await listRecommendations()) as any) || []
  } catch {
  }
  // 加载最近一次扫描记录
  try {
    const latest: any = await getLatestScan()
    if (latest?.marketAnalysis) {
      const raw = latest.marketAnalysis
      scanRaw.value = typeof raw === 'string' ? raw : JSON.stringify(raw)
      const parsed = typeof raw === 'object' ? raw : tryParseJson(scanRaw.value)
      if (parsed && parsed.temperature != null) scanParsed.value = parsed
      scanTime.value = latest.createdAt || ''
    }
  } catch {
  }
})

const doScan = async () => {
  scanning.value = true;
  scanRaw.value = '';
  scanParsed.value = null
  try {
    const raw: any = await scanMarket()
    scanRaw.value = typeof raw === 'string' ? raw : JSON.stringify(raw)
    const parsed = typeof raw === 'object' ? raw : tryParseJson(scanRaw.value)
    if (parsed && parsed.temperature != null) scanParsed.value = parsed
    scanTime.value = new Date().toLocaleString('zh-CN', {hour12: false})
  } catch {
    ElMessage.error('市场扫描失败')
  } finally {
    scanning.value = false
  }
}

const doScreen = async () => {
  screening.value = true;
  screenRaw.value = '';
  screenFundList.value = []
  try {
    const raw: any = await screenFunds(riskPreference.value)
    screenRaw.value = typeof raw === 'string' ? raw : JSON.stringify(raw)
    const parsed = typeof raw === 'object' ? raw : tryParseJson(screenRaw.value)
    if (Array.isArray(parsed)) screenFundList.value = parsed
    else if (parsed?.funds) screenFundList.value = parsed.funds
    else if (parsed?.recommendations) screenFundList.value = parsed.recommendations
    screenTime.value = new Date().toLocaleString('zh-CN', {hour12: false})
    recommendations.value = ((await listRecommendations()) as any) || []
  } catch {
    ElMessage.error('智能选基失败')
  } finally {
    screening.value = false
  }
}
</script>

<style lang="scss" scoped>
.scan-dashboard {
  padding: 8px 0;
}

.gauge-wrap {
  text-align: center;
}

.level-tag {
  margin-top: 4px;
  font-size: 14px;
  letter-spacing: 2px;
}

.summary-text {
  line-height: 1.8;
  color: #606266;
  font-size: 14px;
  margin-bottom: 16px;
}

.factors-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.factor-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  color: #303133;
  transition: background 0.2s;

  &:hover {
    background: #ecf5ff;
  }
}

.recommendation-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  background: #fdf6ec;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

.fund-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.fund-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }
}

.fund-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.fund-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.fund-score {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;

  .score-label {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
}

.fund-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.fund-reason {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
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
