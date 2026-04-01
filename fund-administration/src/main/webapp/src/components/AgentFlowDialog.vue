<template>
  <el-dialog v-model="visible" :show-close="false" destroy-on-close width="90%" top="4vh"
             class="agent-flow-dialog" @opened="autoSelect">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center;width:100%">
        <span style="font-size:16px;font-weight:600">Agent 执行流程 — {{ report?.fundCode }}</span>
        <el-button text @click="visible = false">
          <el-icon :size="20"><Close/></el-icon>
        </el-button>
      </div>
    </template>
    <div class="flow-container">
      <!-- 左侧：流程图 -->
      <div class="flow-graph">
        <!-- 第0层：触发 -->
        <div class="flow-layer">
          <div class="flow-node trigger" @click="selectNode('summary')">
            <div class="node-icon">🚀</div>
            <div class="node-label">触发分析</div>
            <div class="node-summary">{{ report?.fundCode }}</div>
          </div>
        </div>
        <div class="flow-arrow"><div class="arrow-line"/></div>
        <!-- 第1层：数据采集 -->
        <div class="flow-layer">
          <div class="flow-node datasource" @click="selectNode('summary')">
            <div class="node-icon">📊</div>
            <div class="node-label">数据采集</div>
            <div class="node-summary">基本面·净值·持仓·经理·新闻</div>
          </div>
        </div>
        <div class="flow-arrow"><div class="arrow-line"/></div>
        <!-- 第2层：6个分析师并行 -->
        <div class="flow-layer-label">并行分析</div>
        <div class="flow-layer analysts">
          <div v-for="a in analysts" :key="a.key"
               :class="['flow-node', 'analyst', {active: activeNode === a.key, empty: !getResult(a.key)}]"
               @click="selectNode(a.key)">
            <div class="node-icon">{{ a.icon }}</div>
            <div class="node-label">{{ a.label }}</div>
          </div>
        </div>
        <div class="flow-arrow"><div class="arrow-line"/></div>
        <!-- 第3层：多空辩论 -->
        <div class="flow-layer-label">多空辩论</div>
        <div class="flow-layer debate">
          <div :class="['flow-node', 'bull', {active: activeNode === 'bullish', empty: !report?.bullishResearcherResult}]"
               @click="selectNode('bullish')">
            <div class="node-icon">📈</div>
            <div class="node-label">看多研究员</div>
          </div>
          <div class="debate-vs">⚡3轮</div>
          <div :class="['flow-node', 'bear', {active: activeNode === 'bearish', empty: !report?.bearishResearcherResult}]"
               @click="selectNode('bearish')">
            <div class="node-icon">📉</div>
            <div class="node-label">看空研究员</div>
          </div>
        </div>
        <div class="flow-arrow"><div class="arrow-line"/></div>
        <!-- 第4层：决策链 -->
        <div class="flow-layer-label">决策链</div>
        <div class="flow-layer decisions">
          <div v-for="d in decisions" :key="d.key"
               :class="['flow-node', 'decision', {active: activeNode === d.key, empty: !getResult(d.key)}]"
               @click="selectNode(d.key)">
            <div class="node-icon">{{ d.icon }}</div>
            <div class="node-label">{{ d.label }}</div>
          </div>
        </div>
        <div class="flow-arrow"><div class="arrow-line"/></div>
        <!-- 第5层：输出 -->
        <div class="flow-layer">
          <div :class="['flow-node', 'output', {active: activeNode === 'summary'}]"
               @click="selectNode('summary')">
            <div class="node-icon">📋</div>
            <div class="node-label">分析报告</div>
            <div class="node-summary">{{ report?.overallRating || '-' }} · {{ report?.recommendation || '-' }}</div>
          </div>
        </div>
      </div>
      <!-- 右侧：详情面板 -->
      <div class="flow-detail">
        <div class="detail-header">
          <span class="detail-title">{{ activeTitle }}</span>
          <el-tag size="small" :type="activeContent ? 'success' : 'info'">
            {{ activeContent ? '已完成' : '无数据' }}
          </el-tag>
        </div>
        <el-scrollbar class="detail-body">
          <div v-if="activeContent" class="detail-content" v-html="renderMd(activeContent)"/>
          <el-empty v-else description="该节点无输出数据" :image-size="60"/>
        </el-scrollbar>
      </div>
    </div>
  </el-dialog>
</template>
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {marked} from 'marked'

const visible = defineModel<boolean>({default: false})
const props = defineProps<{ report: any }>()

const activeNode = ref('')

const analysts = [
  {key: 'fundAnalystResult', label: '基金分析师', icon: '💰'},
  {key: 'technicalAnalystResult', label: '技术分析师', icon: '📐'},
  {key: 'industryAnalystResult', label: '行业分析师', icon: '🏭'},
  {key: 'managerAnalystResult', label: '经理分析', icon: '👤'},
  {key: 'sentimentAnalystResult', label: '情绪分析师', icon: '🎭'},
  {key: 'newsAnalystResult', label: '新闻分析师', icon: '📰'},
]
const decisions = [
  {key: 'traderResult', label: '交易决策', icon: '⚖️'},
  {key: 'riskManagerResult', label: '风控评估', icon: '🛡️'},
  {key: 'portfolioAdvisorResult', label: '组合建议', icon: '📦'},
]

const nodeLabels: Record<string, string> = {
  fundAnalystResult: '基金分析师', technicalAnalystResult: '技术分析师',
  industryAnalystResult: '行业分析师', managerAnalystResult: '经理分析',
  sentimentAnalystResult: '情绪分析师', newsAnalystResult: '新闻分析师',
  bullish: '看多研究员', bearish: '看空研究员', debateSummary: '辩论总结',
  traderResult: '交易决策', riskManagerResult: '风控评估',
  portfolioAdvisorResult: '组合建议', summary: '分析报告',
}

const getResult = (key: string): string => {
  if (!props.report) return ''
  if (key === 'bullish') return props.report.bullishResearcherResult || ''
  if (key === 'bearish') return props.report.bearishResearcherResult || ''
  return props.report[key] || ''
}

const selectNode = (key: string) => { activeNode.value = key }

/** 弹窗打开时自动选中第一个有数据的节点 */
const autoSelect = () => {
  const allKeys = [...analysts.map(a => a.key), 'bullish', 'bearish',
    ...decisions.map(d => d.key), 'summary']
  activeNode.value = allKeys.find(k => getResult(k)) || 'summary'
}

const activeTitle = computed(() => nodeLabels[activeNode.value] || '')
const activeContent = computed(() => getResult(activeNode.value))

const renderMd = (text: string): string => {
  if (!text) return ''
  const cleaned = text.replace(/^"|"$/g, '').replace(/\\n/g, '\n').replace(/\\"/g, '"')
  return marked.parse(cleaned) as string
}
</script>
<style lang="scss" scoped>
:deep(.el-dialog) { border-radius: 16px; overflow: hidden; }
:deep(.el-dialog__header) { padding: 16px 20px; margin: 0; }
:deep(.el-dialog__body) { padding: 0 20px 20px; }
.flow-container {
  display: flex; height: 78vh; gap: 16px;
}
.flow-graph {
  flex: 0 0 480px; overflow-y: auto; padding: 12px 8px;
  display: flex; flex-direction: column; align-items: center;
  background: #fafbfc; border-radius: 12px;
}
.flow-layer {
  display: flex; gap: 12px; justify-content: center; flex-wrap: wrap;
}
.flow-layer-label {
  font-size: 12px; color: #909399; margin: 4px 0 8px;
  padding: 2px 12px; background: #f4f4f5; border-radius: 10px;
}
.flow-arrow {
  display: flex; justify-content: center; padding: 6px 0;
  .arrow-line {
    width: 2px; height: 20px; background: #dcdfe6; position: relative;
    &::after {
      content: ''; position: absolute; bottom: -4px; left: -3px;
      border-left: 4px solid transparent; border-right: 4px solid transparent;
      border-top: 5px solid #dcdfe6;
    }
  }
}
.flow-node {
  border: 2px solid #e4e7ed; border-radius: 10px; padding: 10px 14px;
  cursor: pointer; transition: all 0.2s; text-align: center;
  min-width: 120px; max-width: 160px; background: #fff;
  &:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64,158,255,0.15); }
  &.active { border-color: #409eff; background: #ecf5ff; }
  &.empty { opacity: 0.5; }
  .node-icon { font-size: 20px; margin-bottom: 4px; }
  .node-label { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 4px; }
  .node-summary {
    font-size: 11px; color: #909399; line-height: 1.4;
    display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  }
}
.flow-node.trigger { border-color: #606266; background: #fafafa; min-width: 140px; }
.flow-node.datasource { border-color: #409eff; background: #f0f7ff; }
.flow-node.analyst { min-width: 72px; max-width: 80px; padding: 8px 6px;
  .node-label { font-size: 11px; }
  .node-summary { display: none; }
}
.flow-node.bull { border-color: #f56c6c; background: #fef0f0; }
.flow-node.bear { border-color: #67c23a; background: #f0f9eb; }
.flow-node.decision { min-width: 100px; max-width: 130px; }
.flow-node.output { border-color: #e6a23c; background: #fdf6ec; min-width: 160px; }
.debate-vs {
  display: flex; align-items: center; font-size: 13px; font-weight: 600; color: #e6a23c;
}
.flow-detail {
  flex: 1; border: 1px solid #ebeef5; border-radius: 12px; display: flex;
  flex-direction: column; overflow: hidden; background: #fff;
}
.detail-header {
  padding: 12px 16px; border-bottom: 1px solid #ebeef5; display: flex;
  justify-content: space-between; align-items: center;
  .detail-title { font-size: 15px; font-weight: 600; color: #303133; }
}
.detail-body { flex: 1; padding: 16px; }
.detail-content {
  font-size: 14px; line-height: 1.8; color: #303133;
  :deep(h1), :deep(h2), :deep(h3) { margin: 16px 0 8px; color: #1a1a1a; }
  :deep(h3) { font-size: 15px; border-left: 3px solid #409eff; padding-left: 8px; }
  :deep(p) { margin: 8px 0; }
  :deep(ul), :deep(ol) { padding-left: 20px; margin: 8px 0; }
  :deep(code) { background: #f5f7fa; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
  :deep(pre) { background: #f5f7fa; padding: 12px; border-radius: 6px; overflow-x: auto; }
  :deep(table) { width: 100%; border-collapse: collapse; margin: 12px 0; }
  :deep(th), :deep(td) { border: 1px solid #ebeef5; padding: 8px; text-align: left; font-size: 13px; }
  :deep(th) { background: #fafafa; font-weight: 600; }
  :deep(blockquote) { border-left: 3px solid #dcdfe6; padding-left: 12px; color: #606266; margin: 8px 0; }
  :deep(hr) { border: none; border-top: 1px solid #ebeef5; margin: 16px 0; }
}
</style>
