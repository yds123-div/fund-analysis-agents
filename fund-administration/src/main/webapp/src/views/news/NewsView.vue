<template>
  <div class="page-container">
    <el-row :gutter="16">
      <!-- 左侧：市场要闻 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600;font-size:15px">市场要闻</span>
              <el-button :loading="marketLoading" size="small" @click="loadMarketNews">刷新</el-button>
            </div>
          </template>
          <div v-if="marketLoading" style="text-align:center;padding:40px">
            <el-skeleton :rows="8" animated/>
          </div>
          <div v-else-if="marketNews.length === 0">
            <el-empty description="暂无新闻数据"/>
          </div>
          <div v-else class="news-list">
            <div v-for="(item, idx) in marketNews" :key="idx" class="news-item">
              <div class="news-title">
                <a :href="item.url" rel="noopener" target="_blank">{{ item.title }}</a>
              </div>
              <div class="news-meta">
                <el-tag effect="plain" size="small" type="info">{{ item.source }}</el-tag>
                <span class="news-time">{{ item.time }}</span>
              </div>
              <div v-if="item.summary" class="news-summary">{{ item.summary }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <!-- 右侧：自选基金新闻 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600;font-size:15px">自选基金动态</span>
              <el-button :loading="watchLoading" size="small" @click="loadWatchlistNews">刷新</el-button>
            </div>
          </template>
          <div v-if="watchLoading" style="text-align:center;padding:40px">
            <el-skeleton :rows="6" animated/>
          </div>
          <div v-else-if="watchlistNews.length === 0">
            <el-empty description="暂无自选基金新闻，请先添加自选基金"/>
          </div>
          <div v-else>
            <div v-for="group in watchlistNews" :key="group.fundCode" class="fund-news-group">
              <div class="fund-news-header">
                <el-tag effect="dark" size="small" type="primary">{{ group.fundCode }}</el-tag>
                <span v-if="group.fundName" style="margin-left: 6px; font-size: 13px; color: #606266">{{
                    group.fundName
                  }}</span>
              </div>
              <div v-for="(item, idx) in group.news" :key="idx" class="news-item compact">
                <div class="news-title">
                  <a :href="item.url" rel="noopener" target="_blank">{{ item.title }}</a>
                </div>
                <div class="news-meta">
                  <span class="news-time">{{ item.time }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
        <!-- 基金新闻搜索 -->
        <el-card shadow="never" style="margin-top:16px">
          <template #header>
            <span style="font-weight:600;font-size:15px">新闻搜索</span>
          </template>
          <div style="display:flex;gap:8px;margin-bottom:12px">
            <el-input v-model="searchKeyword" clearable placeholder="输入基金代码或关键词" @keyup.enter="doSearch"/>
            <el-button :loading="searchLoading" type="primary" @click="doSearch">搜索</el-button>
          </div>
          <div v-if="searchResults.length > 0" class="news-list">
            <div v-for="(item, idx) in searchResults" :key="idx" class="news-item compact">
              <div class="news-title">
                <a :href="item.url" rel="noopener" target="_blank">{{ item.title }}</a>
              </div>
              <div class="news-meta">
                <el-tag effect="plain" size="small" type="info">{{ item.source }}</el-tag>
                <span class="news-time">{{ item.time }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {getFundNews, getMarketNews, getWatchlistNews} from '@/api/news'

const marketNews = ref<any[]>([])
const watchlistNews = ref<any[]>([])
const searchResults = ref<any[]>([])
const marketLoading = ref(false)
const watchLoading = ref(false)
const searchLoading = ref(false)
const searchKeyword = ref('')

const loadMarketNews = async () => {
  marketLoading.value = true
  try {
    marketNews.value = (await getMarketNews(20)) as any || []
  } catch {
    marketNews.value = []
  } finally {
    marketLoading.value = false
  }
}

const loadWatchlistNews = async () => {
  watchLoading.value = true
  try {
    watchlistNews.value = (await getWatchlistNews()) as any || []
  } catch {
    watchlistNews.value = []
  } finally {
    watchLoading.value = false
  }
}

const doSearch = async () => {
  if (!searchKeyword.value.trim()) return
  searchLoading.value = true
  try {
    searchResults.value = (await getFundNews(searchKeyword.value.trim(), 15)) as any || []
  } catch {
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

onMounted(() => {
  loadMarketNews();
  loadWatchlistNews()
})
</script>

<style lang="scss" scoped>
.news-list {
  max-height: 600px;
  overflow-y: auto;
}

.news-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;

  &:last-child {
    border-bottom: none;
  }

  &.compact {
    padding: 8px 0;
  }
}

.news-title a {
  color: #303133;
  text-decoration: none;
  font-size: 14px;
  line-height: 1.6;

  &:hover {
    color: #409eff;
  }
}

.news-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;

  .news-time {
    font-size: 12px;
    color: #909399;
  }
}

.news-summary {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.fund-news-group {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f0f0;

  &:last-child {
    border-bottom: none;
  }
}

.fund-news-header {
  margin-bottom: 8px;
}
</style>