import {createRouter, createWebHistory} from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import {isAuthenticated} from '@/stores/auth'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/login',
            name: 'Login',
            component: () => import('@/views/login/LoginView.vue'),
            meta: {title: '登录', public: true},
        },
        {
            path: '/',
            component: AppLayout,
            redirect: '/dashboard',
            children: [
                {
                    path: 'dashboard',
                    name: 'Dashboard',
                    component: () => import('@/views/dashboard/DashboardView.vue'),
                    meta: {title: '仪表盘', icon: 'Odometer'}
                },
                {
                    path: 'analysis',
                    name: 'Analysis',
                    component: () => import('@/views/analysis/AnalysisView.vue'),
                    meta: {title: '基金分析', icon: 'DataAnalysis'}
                },
                {
                    path: 'analysis/report/:batchNo',
                    name: 'ReportDetail',
                    component: () => import('@/views/analysis/ReportDetail.vue'),
                    meta: {title: '分析报告', hidden: true}
                },
                {
                    path: 'tasks',
                    name: 'TaskCenter',
                    component: () => import('@/views/task/TaskCenterView.vue'),
                    meta: {title: '任务中心', icon: 'List'}
                },
                {
                    path: 'market',
                    name: 'Market',
                    component: () => import('@/views/market/MarketView.vue'),
                    meta: {title: '市场扫描', icon: 'TrendCharts'}
                },
                {
                    path: 'news',
                    name: 'News',
                    component: () => import('@/views/news/NewsView.vue'),
                    meta: {title: '基金动态', icon: 'Notification'}
                },
                {
                    path: 'portfolio',
                    name: 'Portfolio',
                    component: () => import('@/views/portfolio/PortfolioView.vue'),
                    meta: {title: '自选持仓', icon: 'Wallet'}
                },
                {
                    path: 'scheduler',
                    name: 'Scheduler',
                    component: () => import('@/views/scheduler/SchedulerView.vue'),
                    meta: {title: '定时任务', icon: 'Timer'}
                },
                {
                    path: 'logs',
                    name: 'Logs',
                    component: () => import('@/views/logs/LogsView.vue'),
                    meta: {title: '系统日志', icon: 'Document'}
                },
                {
                    path: 'settings',
                    name: 'Settings',
                    component: () => import('@/views/settings/AiConfigView.vue'),
                    meta: {title: '系统设置', icon: 'Setting'}
                },
            ],
        },
    ],
})

// 全局路由守卫
router.beforeEach((to, _from, next) => {
    if (to.meta.public) {
        // 已登录访问 login 页则跳转首页
        return isAuthenticated.value ? next('/dashboard') : next()
    }
    // 未登录跳转 login
    if (!isAuthenticated.value) return next('/login')
    next()
})

export default router
