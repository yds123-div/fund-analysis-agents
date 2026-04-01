<template>
  <div class="report-viewer">
    <!-- 报告头部 -->
    <div class="rv-header">
      <div class="rv-header-left">
        <h2 class="rv-title">{{ report.fundCode }} {{ fundName }}</h2>
        <div class="rv-meta">
          <el-tag :type="ratingType" class="rv-rating" effect="dark" size="large">
            {{ report.overallRating || '未评级' }}
          </el-tag>
          <span class="rv-date">{{ report.reportDate }}</span>
          <el-tag effect="plain" size="small">
            {{
              ({DAILY: '日报', WEEKLY: '周报', MONTHLY: '月报'} as Record<string, string>)[report.reportType] || '日报' }}
          </el-tag>
        </div>
      </div>
      <div class="rv-header-right">
        <div v-if="report.overallScore != null" class="rv-score-ring">
          <svg viewBox="0 0 80 80">
            <circle cx="40" cy="40" fill="none" r="34" stroke="#e4e7ed" stroke-width="6"/>
            <circle :stroke="scoreColor" :stroke-dasharray="scoreArc" cx="40" cy="40" fill="none" r="34"
                    stroke-dashoffset="0" stroke-linecap="round" stroke-width="6"
                    transform="rotate(-90 40 40)"/>
          </svg>
          <span :style="{color: scoreColor}" class="rv-score-val">{{ report.overallScore }}</span>
          <span class="rv-score-label">综合评分</span>
        </div>
      </div>
    </div>
    <!-- 指标卡片 -->
    <div class="rv-indicators">
      <div v-if="report.recommendation" class="rv-ind-item">
        <span class="rv-ind-label">投资建议</span>
        <span :style="{color: recColor}" class="rv-ind-value">{{ report.recommendation }}</span>
      </div>
      <div v-if="report.positionSuggestion != null" class="rv-ind-item">
        <span class="rv-ind-label">建议仓位</span>
        <span class="rv-ind-value">{{ report.positionSuggestion }}%</span>
      </div>
      <div v-if="report.confidenceLevel" class="rv-ind-item">
        <span class="rv-ind-label">置信度</span>
        <span class="rv-ind-value">{{ report.confidenceLevel }}</span>
      </div>
      <div v-if="report.timeHorizon" class="rv-ind-item">
        <span class="rv-ind-label">时间周期</span>
        <span class="rv-ind-value">{{ report.timeHorizon }}</span>
      </div>
    </div>
    <!-- 内容区：侧边导航 + 正文 -->
    <div class="rv-body">
      <nav class="rv-nav">
        <div v-for="(sec, i) in sections" :key="sec.key"
             :class="['rv-nav-item', {active: activeSection === i}]"
             @click="scrollTo(i)">
          <span class="rv-nav-dot"/>{{ sec.label }}
        </div>
      </nav>
      <div ref="contentRef" class="rv-content">
        <section v-for="(sec, i) in sections" :key="sec.key" :ref="el => sectionRefs[i] = el as HTMLElement"
                 class="rv-section">
          <h3 class="rv-section-title">{{ sec.label }}</h3>
          <div class="md-body" v-html="renderMd(sec.content)"/>
        </section>
      </div>
    </div>
    <!-- 操作栏 -->
    <div class="rv-actions">
      <el-button :loading="exporting" type="primary" @click="exportPdf">
        <el-icon>
          <Download/>
        </el-icon>
        导出 PDF
      </el-button>
      <slot name="actions"/>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, nextTick, ref} from 'vue'
import {marked} from 'marked'
import {ElMessage} from 'element-plus'

const props = defineProps<{ report: any; fundNameProp?: string }>()

const activeSection = ref(0)
const contentRef = ref<HTMLElement>()
const sectionRefs = ref<HTMLElement[]>([])
const exporting = ref(false)

const fundName = computed(() => props.fundNameProp || props.report.fundName || '')

const sections = computed(() => {
  const r = props.report
  if (!r) return []
  return [
    {key: 'summary', label: '综合报告', content: r.summary},
    {key: 'fund', label: '基本面分析', content: r.fundAnalystResult},
    {key: 'tech', label: '技术分析', content: r.technicalAnalystResult},
    {key: 'industry', label: '行业分析', content: r.industryAnalystResult},
    {key: 'manager', label: '经理分析', content: r.managerAnalystResult},
    {key: 'sentiment', label: '情绪分析', content: r.sentimentAnalystResult},
    {key: 'news', label: '新闻分析', content: r.newsAnalystResult},
    {key: 'debate', label: '多空辩论', content: r.debateSummary},
    {key: 'trader', label: '交易建议', content: r.traderResult},
    {key: 'risk', label: '风控评估', content: r.riskManagerResult},
    {key: 'portfolio', label: '组合建议', content: r.portfolioAdvisorResult},
  ].filter(s => s.content)
})

const ratingType = computed(() => {
  const r = props.report.overallRating || ''
  if (r.includes('强烈推荐') || r.includes('买入')) return 'success'
  if (r.includes('推荐') || r.includes('增持')) return 'primary'
  if (r.includes('中性') || r.includes('持有')) return 'warning'
  return 'danger'
})

const scoreColor = computed(() => {
  const s = props.report.overallScore || 0
  if (s >= 80) return '#67c23a'
  if (s >= 60) return '#409eff'
  if (s >= 40) return '#e6a23c'
  return '#f56c6c'
})

const scoreArc = computed(() => {
  const s = Math.min(100, Math.max(0, props.report.overallScore || 0))
  const circumference = 2 * Math.PI * 34
  return `${circumference * s / 100} ${circumference}`
})

const recColor = computed(() => {
  const r = props.report.recommendation || ''
  if (r.includes('买') || r.includes('增')) return '#67c23a'
  if (r.includes('持有') || r.includes('中性')) return '#e6a23c'
  return '#f56c6c'
})

const renderMd = (text: string) => {
  if (!text) return '<span style="color:#909399">暂无数据</span>'
  // 如果是 JSON 字符串，尝试提取 markdown 内容
  if (text.startsWith('{') || text.startsWith('[')) {
    try {
      const obj = JSON.parse(text)
      // 尝试常见的字段名
      const md = obj.analysis || obj.content || obj.summary || obj.result || obj.markdown
      if (typeof md === 'string') return marked.parse(md, {async: false}) as string
      return marked.parse('```json\n' + JSON.stringify(obj, null, 2) + '\n```', {async: false}) as string
    } catch { /* not JSON, render as markdown */
    }
  }
  return marked.parse(text, {async: false}) as string
}

const scrollTo = (index: number) => {
  activeSection.value = index
  sectionRefs.value[index]?.scrollIntoView({behavior: 'smooth', block: 'start'})
}

// PDF 导出
const exportPdf = async () => {
  exporting.value = true
  try {
    const {default: html2canvas} = await import('html2canvas')
    const {jsPDF} = await import('jspdf')
    // 创建临时渲染容器
    const container = document.createElement('div')
    container.className = 'pdf-render-zone'
    container.style.cssText = 'position:fixed;left:-9999px;top:0;width:750px;background:#fff;padding:40px;font-family:system-ui,sans-serif;'
    // 封面
    const cover = document.createElement('div')
    cover.innerHTML = `
      <div style="text-align:center;padding:120px 0 60px">
        <div style="font-size:14px;color:#909399;letter-spacing:4px;margin-bottom:20px">FUND ANALYSIS REPORT</div>
        <div style="font-size:32px;font-weight:700;color:#1a2744;margin-bottom:12px">${props.report.fundCode} ${fundName.value}</div>
        <div style="font-size:16px;color:#606266;margin-bottom:40px">${props.report.reportDate || ''}</div>
        <div style="display:inline-block;padding:8px 32px;background:#409eff;color:#fff;border-radius:20px;font-size:18px;font-weight:600">
          ${props.report.overallRating || '综合分析'} ${props.report.overallScore != null ? '· ' + props.report.overallScore + '分' : ''}
        </div>
        ${props.report.recommendation ? `<div style="margin-top:20px;font-size:15px;color:#606266">投资建议：${props.report.recommendation}</div>` : ''}
      </div>
      <div style="border-top:2px solid #409eff;margin:40px 0"></div>
    `
    container.appendChild(cover)
    // 各章节
    sections.value.forEach(sec => {
      const secDiv = document.createElement('div')
      secDiv.style.cssText = 'margin-bottom:30px;page-break-inside:avoid;'
      secDiv.innerHTML = `
        <h2 style="font-size:18px;color:#1a2744;border-left:4px solid #409eff;padding-left:12px;margin:24px 0 12px">${sec.label}</h2>
        <div style="font-size:13px;line-height:1.8;color:#303133">${renderMd(sec.content)}</div>
      `
      container.appendChild(secDiv)
    })
    // 页脚
    const footer = document.createElement('div')
    footer.innerHTML = `<div style="text-align:center;color:#909399;font-size:11px;margin-top:40px;padding-top:16px;border-top:1px solid #ebeef5">
      Fund Analysis Agents · ${props.report.fundCode} ${fundName.value} · ${props.report.reportDate || ''}</div>`
    container.appendChild(footer)
    document.body.appendChild(container)
    await nextTick()
    // 渲染 canvas
    const canvas = await html2canvas(container, {scale: 2, useCORS: true, logging: false})
    document.body.removeChild(container)
    // 生成 PDF（A4）
    const pdf = new jsPDF('p', 'mm', 'a4')
    const pageW = 210, pageH = 297
    const margin = 10
    const contentW = pageW - margin * 2
    const imgW = canvas.width, imgH = canvas.height
    const ratio = contentW / imgW
    const totalH = imgH * ratio
    let offsetY = 0
    let pageNum = 0
    while (offsetY < totalH) {
      if (pageNum > 0) pdf.addPage()
      const srcY = offsetY / ratio
      const srcH = Math.min((pageH - margin * 2) / ratio, imgH - srcY)
      const drawH = srcH * ratio
      // 裁切当前页内容
      const pageCanvas = document.createElement('canvas')
      pageCanvas.width = imgW
      pageCanvas.height = srcH
      pageCanvas.getContext('2d')!.drawImage(canvas, 0, srcY, imgW, srcH, 0, 0, imgW, srcH)
      pdf.addImage(pageCanvas.toDataURL('image/jpeg', 0.92), 'JPEG', margin, margin, contentW, drawH)
      offsetY += drawH
      pageNum++
    }
    const name = fundName.value ? `${fundName.value}(${props.report.fundCode})` : props.report.fundCode
    const date = props.report.reportDate || new Date().toISOString().slice(0, 10)
    pdf.save(`${name}_AI分析报告_${date}.pdf`)
    ElMessage.success('PDF 导出成功')
  } catch (e: any) {
    console.error(e)
    ElMessage.error('导出失败: ' + e.message)
  } finally {
    exporting.value = false
  }
}
</script>

<style lang="scss" scoped>
.report-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.rv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px;
  background: linear-gradient(135deg, #1a2744 0%, #2c3e6b 100%);
  border-radius: 12px;
  margin-bottom: 16px;
  color: #fff;
}

.rv-header-left {
  flex: 1;
}

.rv-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 10px;
  letter-spacing: 0.5px;
}

.rv-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rv-rating {
  font-size: 14px;
  letter-spacing: 1px;
}

.rv-date {
  font-size: 13px;
  opacity: 0.8;
}

.rv-header-right {
  flex-shrink: 0;
  margin-left: 24px;
}

.rv-score-ring {
  position: relative;
  width: 80px;
  height: 80px;
  text-align: center;

  svg {
    width: 80px;
    height: 80px;
  }
}

.rv-score-val {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -60%);
  font-size: 22px;
  font-weight: 700;
}

.rv-score-label {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, 50%);
  font-size: 10px;
  color: rgba(255, 255, 255, 0.7);
}

.rv-indicators {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;

  .rv-ind-item {
    flex: 1;
    min-width: 120px;
    padding: 14px 18px;
    background: #f5f7fa;
    border-radius: 8px;
    text-align: center;
  }

  .rv-ind-label {
    display: block;
    font-size: 12px;
    color: #909399;
    margin-bottom: 4px;
  }

  .rv-ind-value {
    font-size: 16px;
    font-weight: 700;
    color: #303133;
  }
}

.rv-body {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 16px;
  margin-bottom: 16px;
}

.rv-nav {
  width: 160px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  align-self: flex-start;
  background: #fafbfc;
  border-radius: 8px;
  padding: 12px 0;
  max-height: 70vh;
  overflow-y: auto;
}

.rv-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: #409eff;
    background: #ecf5ff;
  }

  &.active {
    color: #409eff;
    font-weight: 600;
    background: #ecf5ff;
  }
}

.rv-nav-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
  flex-shrink: 0;

  .rv-nav-item.active & {
    background: #409eff;
  }
}

.rv-content {
  flex: 1;
  min-width: 0;
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 8px;
  scroll-behavior: smooth;
}

.rv-section {
  margin-bottom: 28px;
}

.rv-section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a2744;
  margin: 0 0 12px;
  padding-left: 12px;
  border-left: 3px solid #409eff;
}

.rv-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

/* Markdown 渲染样式 */
.md-body {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
}

.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3) {
  margin: 16px 0 8px;
  color: #303133;
}

.md-body :deep(h1) {
  font-size: 18px;
}

.md-body :deep(h2) {
  font-size: 16px;
}

.md-body :deep(h3) {
  font-size: 15px;
}

.md-body :deep(ul), .md-body :deep(ol) {
  padding-left: 20px;
}

.md-body :deep(li) {
  margin-bottom: 4px;
}

.md-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 13px;
}

.md-body :deep(th), .md-body :deep(td) {
  border: 1px solid #ebeef5;
  padding: 8px 12px;
  text-align: left;
}

.md-body :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}

.md-body :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding-left: 12px;
  color: #606266;
  margin: 12px 0;
  background: #f9fafc;
  padding: 8px 12px;
  border-radius: 0 4px 4px 0;
}

.md-body :deep(code) {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
}

.md-body :deep(pre) {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}

.md-body :deep(strong) {
  color: #1a2744;
}

.md-body :deep(p) {
  margin: 8px 0;
}

/* 移动端适配 */
@media (max-width: 767px) {
  .rv-header {
    flex-direction: column;
    padding: 16px;
  }
  .rv-header-right {
    margin: 12px 0 0;
  }
  .rv-indicators {
    .rv-ind-item {
      min-width: 80px;
      padding: 10px;
    }
  }
  .rv-body {
    flex-direction: column;
  }
  .rv-nav {
    width: 100%;
    max-height: none;
    flex-direction: row;
    overflow-x: auto;
    display: flex;
    gap: 0;
    padding: 0;
  }
  .rv-nav-item {
    white-space: nowrap;
    padding: 8px 12px;
  }
  .rv-content {
    max-height: none;
  }
}
</style>
