<template>
  <el-container class="app-layout">
    <!-- 移动端遮罩 -->
    <div v-if="isMobile && sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false"/>
    <el-aside :class="{open: sidebarOpen, mobile: isMobile}" :width="sidebarWidth" class="app-aside">
      <div class="logo">
        <el-icon :size="24">
          <DataAnalysis/>
        </el-icon>
        <span v-if="!collapsed">Fund Agents</span>
      </div>
      <el-menu :collapse="collapsed && !isMobile" :default-active="activeMenu" active-text-color="#409eff"
               background-color="#1d1e1f" router text-color="#bfcbd9"
               @select="isMobile && (sidebarOpen = false)">
        <template v-for="item in menuItems" :key="item.path">
          <el-menu-item :index="item.path">
            <el-icon>
              <component :is="item.icon"/>
            </el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon :size="20" class="menu-toggle" @click="toggleSidebar">
            <Fold v-if="!collapsed && !isMobile"/>
            <Expand v-else/>
          </el-icon>
          <span class="header-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag class="hide-xs" effect="dark" size="small" type="success">v0.1.0</el-tag>
          <span class="user-name hide-xs">{{ username }}</span>
          <el-button size="small" text type="danger" @click="handleLogout">
            <el-icon>
              <SwitchButton/>
            </el-icon>
            <span class="hide-xs">退出</span>
          </el-button>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view/>
      </el-main>
    </el-container>
  </el-container>
</template>
<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {getUser, logout} from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const isMobile = ref(false)
const sidebarOpen = ref(false)

const menuItems = [
  {path: '/dashboard', title: '仪表盘', icon: 'Odometer'},
  {path: '/analysis', title: '基金分析', icon: 'DataAnalysis'},
  {path: '/tasks', title: '任务中心', icon: 'List'},
  {path: '/market', title: '市场扫描', icon: 'TrendCharts'},
  {path: '/news', title: '基金动态', icon: 'Notification'},
  {path: '/portfolio', title: '自选持仓', icon: 'Wallet'},
  {path: '/scheduler', title: '定时任务', icon: 'Timer'},
  {path: '/logs', title: '系统日志', icon: 'Document'},
  {path: '/settings', title: '系统设置', icon: 'Setting'},
]
const sidebarWidth = computed(() => isMobile.value ? '220px' : collapsed.value ? '64px' : '220px')
const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
const currentTitle = computed(() => {
  const item = menuItems.find(m => route.path.startsWith(m.path))
  return item?.title || (route.meta?.title as string) || ''
})
const username = computed(() => getUser()?.nickname || getUser()?.username || '用户')
const toggleSidebar = () => {
  isMobile.value ? (sidebarOpen.value = !sidebarOpen.value) : (collapsed.value = !collapsed.value)
}
const handleLogout = () => {
  logout();
  router.push('/login')
}
const checkMobile = () => {
  const wasMobile = isMobile.value
  isMobile.value = window.innerWidth < 768
  if (isMobile.value && !wasMobile) sidebarOpen.value = false
  if (!isMobile.value) sidebarOpen.value = false
}
onMounted(() => {
  checkMobile();
  window.addEventListener('resize', checkMobile)
})
onUnmounted(() => window.removeEventListener('resize', checkMobile))
</script>
<style lang="scss" scoped>
.app-layout {
  height: 100vh;
}

.app-aside {
  background: linear-gradient(180deg, #1a1c2e 0%, #1d1e2c 100%);
  overflow-y: auto;
  border-right: none;
  transition: width 0.3s;

  .logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #e0e6ed;
    font-size: 17px;
    font-weight: 700;
    letter-spacing: 1px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    white-space: nowrap;
    overflow: hidden;
  }

  .el-menu {
    border-right: none;
    background: transparent;
  }

  &.mobile {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 2000;
    transform: translateX(-100%);
    transition: transform 0.3s ease;

    &.open {
      transform: translateX(0);
    }
  }
}

.sidebar-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 1999;
}

.app-header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  height: 56px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .header-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .user-name {
    font-size: 13px;
    color: #606266;
  }

  .menu-toggle {
    cursor: pointer;
    color: #606266;

    &:hover {
      color: #409eff;
    }
  }
}

.app-main {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}

@media (max-width: 767px) {
  .app-main {
    padding: 12px;
  }
  .hide-xs {
    display: none !important;
  }
}
</style>
