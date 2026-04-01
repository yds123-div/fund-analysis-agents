<template>
  <div class="arch-graph-wrap">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <div class="legend">
        <span class="legend-item"><span class="dot ok"/>正常</span>
        <span class="legend-item"><span class="dot err"/>异常</span>
        <span class="legend-item"><span class="dot unknown"/>未检测</span>
        <span class="legend-item"><span class="dot disabled"/>未配置</span>
      </div>
      <el-button :loading="refreshing" size="small" @click="refresh">
        <el-icon>
          <Refresh/>
        </el-icon>
        刷新状态
      </el-button>
    </div>
    <div ref="boxRef" class="canvas-box">
      <canvas ref="cvs" @click="onClick" @mousemove="onHover"/>
    </div>
    <!-- tooltip -->
    <div v-if="tip.show" :style="{left:tip.x+'px',top:tip.y+'px'}" class="graph-tip">
      <div style="font-weight:600;margin-bottom:4px">{{ tip.title }}</div>
      <div style="font-size:12px;color:#606266;line-height:1.6" v-html="tip.desc"/>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {nextTick, onMounted, onUnmounted, reactive, ref} from 'vue'
import {getBindings, getHealth, getProviders} from '@/api/ai-config'

/* ---- 类型 ---- */
interface Node {
  id: string;
  label: string;
  desc: string;
  group: string
  x: number;
  y: number;
  w: number;
  h: number
  status: 'ok' | 'err' | 'unknown' | 'disabled'
}

interface Edge {
  from: string;
  to: string;
  label?: string
}

/* ---- refs ---- */
const cvs = ref<HTMLCanvasElement>()
const boxRef = ref<HTMLElement>()
const refreshing = ref(false)
const tip = reactive({show: false, x: 0, y: 0, title: '', desc: ''})

/* ---- 状态数据 ---- */
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
let hitAreas: { node: Node; x: number; y: number; w: number; h: number }[] = []

/* ---- 常量 ---- */
const DPR = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1
const COLORS: Record<string, { bg: string; border: string; text: string }> = {
  ok: {bg: '#f0f9eb', border: '#67c23a', text: '#67c23a'},
  err: {bg: '#fef0f0', border: '#f56c6c', text: '#f56c6c'},
  unknown: {bg: '#f4f4f5', border: '#909399', text: '#909399'},
  disabled: {bg: '#fafafa', border: '#dcdfe6', text: '#c0c4cc'},
}
const GROUP_COLORS: Record<string, string> = {
  datasource: '#409eff', ai: '#e6a23c', analyst: '#67c23a',
  debate: '#9b59b6', decision: '#f56c6c', output: '#409eff', trigger: '#606266',
}

/* ---- 构建节点和边 ---- */
const buildGraph = (providerStatus: Record<string, string>,
                    dsStatus: Record<string, boolean>,
                    bindingMap: Record<string, boolean>,
                    containerW: number) => {
  const W = 130, H = 52, GAP_X = 24, GAP_Y = 60
  const n: Node[] = []
  const e: Edge[] = []
  // 6 个分析师需要的最小宽度
  const minW = 6 * 100 + 5 * 14 + 40 // 670 + padding
  const canvasW = Math.max(containerW, minW)
  const CX = canvasW / 2
  let y = 30

  // --- 第 0 层：触发入口 ---
  n.push({
    id: 'trigger', label: '用户触发分析', desc: '用户输入基金代码，点击"开始AI分析"', group: 'trigger',
    x: CX - W / 2, y, w: W, h: H, status: 'ok'
  })
  y += H + GAP_Y

  // --- 第 1 层：数据采集 ---
  const dsKeys = Object.keys(dsStatus)
  const dsW = 110
  const dsTotal = Math.max(dsKeys.length, 2)
  const dsGroupW = dsTotal * dsW + (dsTotal - 1) * GAP_X
  let dsX = CX - dsGroupW / 2
  // 数据采集节点
  n.push({
    id: 'data_collect', label: '数据采集', desc: '从多个数据源聚合基金数据：基本面、净值、持仓、经理、新闻',
    group: 'datasource', x: CX - W / 2, y, w: W, h: H,
    status: dsKeys.some(k => dsStatus[k]) ? 'ok' : dsKeys.length ? 'err' : 'disabled'
  })
  e.push({from: 'trigger', to: 'data_collect', label: '基金代码'})
  y += H + GAP_Y * 0.6
  // 各数据源
  dsKeys.forEach((k, i) => {
    const st = dsStatus[k] ? 'ok' : 'err'
    n.push({
      id: `ds_${k}`, label: k, desc: `数据源: ${k}\n状态: ${st === 'ok' ? '在线' : '离线'}`,
      group: 'datasource', x: dsX + i * (dsW + GAP_X), y, w: dsW, h: 40, status: st
    })
    e.push({from: 'data_collect', to: `ds_${k}`})
  })
  if (!dsKeys.length) {
    n.push({
      id: 'ds_none', label: '未配置数据源', desc: '请在数据源管理中配置', group: 'datasource',
      x: CX - dsW / 2, y, w: dsW + 20, h: 40, status: 'disabled'
    })
    e.push({from: 'data_collect', to: 'ds_none'})
  }
  y += 40 + GAP_Y

  // --- 第 2 层：AI 提供商 ---
  const provKeys = Object.keys(providerStatus)
  const aiW = 120
  const aiTotal = Math.max(provKeys.length, 1)
  const aiGroupW = aiTotal * aiW + (aiTotal - 1) * GAP_X
  let aiX = CX - aiGroupW / 2
  n.push({
    id: 'ai_layer', label: 'AI 模型层', desc: 'Agent 通过 AI 提供商调用 LLM 完成分析',
    group: 'ai', x: CX - W / 2, y, w: W, h: H,
    status: provKeys.some(k => providerStatus[k] === 'ONLINE') ? 'ok' : provKeys.length ? 'err' : 'disabled'
  })
  y += H + GAP_Y * 0.6
  provKeys.forEach((k, i) => {
    const st = providerStatus[k] === 'ONLINE' ? 'ok' : providerStatus[k] === 'ERROR' ? 'err' : 'unknown'
    n.push({
      id: `prov_${k}`, label: k, desc: `AI 提供商: ${k}\n状态: ${providerStatus[k]}`,
      group: 'ai', x: aiX + i * (aiW + GAP_X), y, w: aiW, h: 40, status: st
    })
    e.push({from: 'ai_layer', to: `prov_${k}`})
  })
  if (!provKeys.length) {
    n.push({
      id: 'prov_none', label: '未配置提供商', desc: '请在 AI 配置中添加提供商', group: 'ai',
      x: CX - aiW / 2, y, w: aiW + 20, h: 40, status: 'disabled'
    })
    e.push({from: 'ai_layer', to: 'prov_none'})
  }
  y += 40 + GAP_Y

  // --- 第 3 层：6 个分析师并行 ---
  const analysts = [
    {id: 'fund_analyst', label: '基金分析师', desc: '基本面与业绩评估'},
    {id: 'technical_analyst', label: '技术分析师', desc: '趋势与技术指标'},
    {id: 'industry_analyst', label: '行业分析师', desc: '行业前景与竞争'},
    {id: 'manager_analyst', label: '经理分析', desc: '管理能力评估'},
    {id: 'sentiment_analyst', label: '情绪分析师', desc: '市场情绪研判'},
    {id: 'news_analyst', label: '新闻分析师', desc: '舆情与事件影响'},
  ]
  const aW = 100, aH = 44
  const aGap = 14
  const aGroupW = analysts.length * aW + (analysts.length - 1) * aGap
  let aX = CX - aGroupW / 2
  // 并行标签
  n.push({
    id: 'parallel_label', label: '并行分析', desc: '6 个分析师 Agent 同时执行，各自独立调用 LLM',
    group: 'analyst', x: CX - 60, y, w: 120, h: 32, status: 'ok'
  })
  e.push({from: 'data_collect', to: 'parallel_label', label: '基金数据'})
  y += 32 + GAP_Y * 0.5
  analysts.forEach((a, i) => {
    const hasBind = bindingMap[a.id] !== undefined ? bindingMap[a.id] : false
    const st = hasBind ? 'ok' : 'disabled'
    n.push({
      id: a.id, label: a.label, desc: `${a.desc}\n模型绑定: ${hasBind ? '已配置' : '未配置'}`,
      group: 'analyst', x: aX + i * (aW + aGap), y, w: aW, h: aH, status: st
    })
    e.push({from: 'parallel_label', to: a.id})
  })
  y += aH + GAP_Y

  // --- 第 4 层：多空辩论 ---
  const debateW = 120
  const debateGap = 80
  n.push({
    id: 'debate_label', label: '多空辩论', desc: '看多/看空研究员进行 3 轮结构化辩论',
    group: 'debate', x: CX - 60, y, w: 120, h: 32, status: 'ok'
  })
  analysts.forEach(a => e.push({from: a.id, to: 'debate_label'}))
  y += 32 + GAP_Y * 0.5
  const bullX = CX - debateGap / 2 - debateW
  const bearX = CX + debateGap / 2
  n.push({
    id: 'bullish', label: '看多研究员', desc: '论证投资价值与上涨逻辑',
    group: 'debate', x: bullX, y, w: debateW, h: aH, status: 'ok'
  })
  n.push({
    id: 'bearish', label: '看空研究员', desc: '识别风险与下跌因素',
    group: 'debate', x: bearX, y, w: debateW, h: aH, status: 'ok'
  })
  e.push({from: 'debate_label', to: 'bullish'})
  e.push({from: 'debate_label', to: 'bearish'})
  e.push({from: 'bullish', to: 'bearish', label: '3轮辩论'})
  y += aH + GAP_Y

  // --- 第 5 层：决策链 ---
  const decisions = [
    {id: 'trader', label: '交易决策', desc: '综合所有分析生成买卖建议'},
    {id: 'risk_manager', label: '风控评估', desc: '风险评估与控制建议'},
    {id: 'portfolio_advisor', label: '组合建议', desc: '组合层面再平衡建议'},
    {id: 'report_generator', label: '报告生成', desc: '汇总生成最终分析报告'},
  ]
  let prevId = 'bearish'
  decisions.forEach((d, i) => {
    const hasBind = bindingMap[d.id] !== undefined ? bindingMap[d.id] : false
    const st = hasBind ? 'ok' : 'disabled'
    n.push({
      id: d.id, label: d.label, desc: `${d.desc}\n模型绑定: ${hasBind ? '已配置' : '未配置'}`,
      group: 'decision', x: CX - W / 2, y, w: W, h: H, status: st
    })
    if (i === 0) {
      e.push({from: 'bullish', to: d.id, label: '辩论结论'})
      e.push({from: 'bearish', to: d.id})
    } else {
      e.push({from: prevId, to: d.id})
    }
    prevId = d.id
    y += H + (i < decisions.length - 1 ? GAP_Y * 0.7 : GAP_Y)
  })

  // --- 第 6 层：输出 ---
  const outW = 110
  const outGap = 30
  const outputs = [
    {id: 'out_report', label: '分析报告', desc: '完整的多维度分析报告'},
    {id: 'out_notify', label: '通知推送', desc: 'Bark / 邮件通知用户'},
  ]
  const outGroupW = outputs.length * outW + (outputs.length - 1) * outGap
  let outX = CX - outGroupW / 2
  outputs.forEach((o, i) => {
    n.push({
      id: o.id, label: o.label, desc: o.desc, group: 'output',
      x: outX + i * (outW + outGap), y, w: outW, h: 42, status: 'ok'
    })
    e.push({from: 'report_generator', to: o.id})
  })

  nodes.value = n
  edges.value = e
  return {h: y + 42 + 30, w: canvasW}
}

/* ---- 绘制 ---- */
const nodeMap = () => {
  const m: Record<string, Node> = {}
  nodes.value.forEach(n => m[n.id] = n)
  return m
}

const draw = () => {
  const canvas = cvs.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  const cw = canvas.width / DPR, ch = canvas.height / DPR
  ctx.save()
  ctx.scale(DPR, DPR)
  ctx.clearRect(0, 0, cw, ch)
  const nm = nodeMap()
  hitAreas = []

  // 画边
  edges.value.forEach(edge => {
    const from = nm[edge.from], to = nm[edge.to]
    if (!from || !to) return
    ctx.beginPath()
    ctx.strokeStyle = '#c0c4cc'
    ctx.lineWidth = 1.5
    // 同行节点：水平连线（双向箭头）
    if (Math.abs(from.y - to.y) < 5) {
      const fy = from.y + from.h / 2
      const fx = from.x + from.w
      const tx = to.x
      ctx.moveTo(fx, fy)
      ctx.lineTo(tx, fy)
      ctx.stroke()
      // 双向箭头
      const arrowSize = 5
      ctx.beginPath();
      ctx.fillStyle = '#c0c4cc'
      ctx.moveTo(tx, fy);
      ctx.lineTo(tx - arrowSize, fy - arrowSize);
      ctx.lineTo(tx - arrowSize, fy + arrowSize);
      ctx.closePath();
      ctx.fill()
      ctx.beginPath();
      ctx.fillStyle = '#c0c4cc'
      ctx.moveTo(fx, fy);
      ctx.lineTo(fx + arrowSize, fy - arrowSize);
      ctx.lineTo(fx + arrowSize, fy + arrowSize);
      ctx.closePath();
      ctx.fill()
      if (edge.label) {
        ctx.font = '10px system-ui';
        ctx.fillStyle = '#909399';
        ctx.textAlign = 'center'
        ctx.fillText(edge.label, (fx + tx) / 2, fy - 8)
      }
      return
    }
    const fx = from.x + from.w / 2, fy = from.y + from.h
    const tx = to.x + to.w / 2, ty = to.y
    const midY = (fy + ty) / 2
    ctx.moveTo(fx, fy)
    ctx.bezierCurveTo(fx, midY, tx, midY, tx, ty)
    ctx.stroke()
    // 箭头
    const arrowSize = 5
    ctx.beginPath();
    ctx.fillStyle = '#c0c4cc'
    ctx.moveTo(tx, ty)
    ctx.lineTo(tx - arrowSize, ty - arrowSize)
    ctx.lineTo(tx + arrowSize, ty - arrowSize)
    ctx.closePath();
    ctx.fill()
    // 边标签
    if (edge.label) {
      const lx = (fx + tx) / 2, ly = midY - 6
      ctx.font = '10px system-ui';
      ctx.fillStyle = '#909399';
      ctx.textAlign = 'center'
      ctx.fillText(edge.label, lx, ly)
    }
  })

  // 画节点
  nodes.value.forEach(node => {
    const c = COLORS[node.status]
    const gc = GROUP_COLORS[node.group] || '#409eff'
    const r = 8
    // 圆角矩形
    ctx.beginPath()
    ctx.moveTo(node.x + r, node.y)
    ctx.lineTo(node.x + node.w - r, node.y)
    ctx.quadraticCurveTo(node.x + node.w, node.y, node.x + node.w, node.y + r)
    ctx.lineTo(node.x + node.w, node.y + node.h - r)
    ctx.quadraticCurveTo(node.x + node.w, node.y + node.h, node.x + node.w - r, node.y + node.h)
    ctx.lineTo(node.x + r, node.y + node.h)
    ctx.quadraticCurveTo(node.x, node.y + node.h, node.x, node.y + node.h - r)
    ctx.lineTo(node.x, node.y + r)
    ctx.quadraticCurveTo(node.x, node.y, node.x + r, node.y)
    ctx.closePath()
    ctx.fillStyle = c.bg
    ctx.fill()
    ctx.strokeStyle = c.border
    ctx.lineWidth = 2
    ctx.stroke()
    // 左侧色条
    ctx.beginPath()
    ctx.moveTo(node.x + r, node.y)
    ctx.lineTo(node.x + 4, node.y)
    ctx.quadraticCurveTo(node.x, node.y, node.x, node.y + r)
    ctx.lineTo(node.x, node.y + node.h - r)
    ctx.quadraticCurveTo(node.x, node.y + node.h, node.x + 4, node.y + node.h)
    ctx.lineTo(node.x + r, node.y + node.h)
    ctx.closePath()
    ctx.fillStyle = gc
    ctx.fill()
    // 状态圆点
    const dotR = 4
    ctx.beginPath()
    ctx.arc(node.x + node.w - 12, node.y + 12, dotR, 0, Math.PI * 2)
    ctx.fillStyle = c.border
    ctx.fill()
    // 文字
    ctx.font = '600 13px system-ui'
    ctx.fillStyle = '#303133'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(node.label, node.x + node.w / 2, node.y + node.h / 2)
    hitAreas.push({node, x: node.x, y: node.y, w: node.w, h: node.h})
  })

  ctx.restore()
}

/* ---- 交互 ---- */
const getMousePos = (e: MouseEvent) => {
  const rect = cvs.value!.getBoundingClientRect()
  return {x: e.clientX - rect.left, y: e.clientY - rect.top}
}
const hitTest = (mx: number, my: number) => hitAreas.find(a =>
    mx >= a.x && mx <= a.x + a.w && my >= a.y && my <= a.y + a.h)

const onHover = (e: MouseEvent) => {
  const {x, y} = getMousePos(e)
  const hit = hitTest(x, y)
  if (hit) {
    tip.show = true
    tip.x = Math.min(e.offsetX + 12, (boxRef.value?.clientWidth || 960) - 200)
    tip.y = e.offsetY + 12
    tip.title = hit.node.label
    tip.desc = hit.node.desc.replace(/\n/g, '<br/>')
    cvs.value!.style.cursor = 'pointer'
  } else {
    tip.show = false
    cvs.value!.style.cursor = 'default'
  }
}
const onClick = (e: MouseEvent) => {
  const {x, y} = getMousePos(e)
  const hit = hitTest(x, y)
  if (hit) {
    tip.show = true
    tip.x = Math.min(e.offsetX + 12, (boxRef.value?.clientWidth || 960) - 200)
    tip.y = e.offsetY + 12
    tip.title = hit.node.label
    tip.desc = hit.node.desc.replace(/\n/g, '<br/>')
  }
}

/* ---- 数据加载 ---- */
const refresh = async () => {
  refreshing.value = true
  try {
    const [provs, binds, health]: any[] = await Promise.allSettled([
      getProviders(), getBindings(), getHealth()
    ]).then(rs => rs.map(r => r.status === 'fulfilled' ? r.value : null))
    const providerStatus: Record<string, string> = {}
    ;(provs || []).forEach((p: any) => {
      providerStatus[p.providerCode] = p.connectivityStatus || 'UNKNOWN'
    })
    const dsStatus: Record<string, boolean> = health?.dataSources || {}
    const containerW = boxRef.value?.clientWidth || 960
    const bindingMap: Record<string, boolean> = {}
    ;(binds || []).forEach((b: any) => {
      if (b.enabled) bindingMap[b.agentId] = true
    })
    // default 绑定视为所有未单独绑定的 agent 的 fallback
    if (bindingMap['default']) {
      for (const id of ['fund_analyst', 'technical_analyst', 'industry_analyst', 'manager_analyst',
        'sentiment_analyst', 'news_analyst', 'trader', 'risk_manager', 'portfolio_advisor', 'report_generator']) {
        if (bindingMap[id] === undefined) bindingMap[id] = true
      }
    }
    const size = buildGraph(providerStatus, dsStatus, bindingMap, containerW)
    await nextTick()
    const box = boxRef.value
    if (box && cvs.value) {
      cvs.value.width = size.w * DPR
      cvs.value.height = size.h * DPR
      cvs.value.style.width = size.w + 'px'
      cvs.value.style.height = size.h + 'px'
      draw()
    }
  } catch {
  }
  refreshing.value = false
}

const handleResize = () => {
  refresh()
}
onMounted(() => {
  refresh();
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style lang="scss" scoped>
.arch-graph-wrap {
  padding: 8px 0;
}

.legend {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #606266;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.dot.ok {
  background: #67c23a;
}

.dot.err {
  background: #f56c6c;
}

.dot.unknown {
  background: #909399;
}

.dot.disabled {
  background: #dcdfe6;
}

.canvas-box {
  position: relative;
  overflow-x: auto;
}

canvas {
  display: block;
}

.graph-tip {
  position: absolute;
  z-index: 10;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  max-width: 220px;
  pointer-events: none;
}
</style>
