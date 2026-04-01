import axios from 'axios'
import {ElMessage, ElMessageBox} from 'element-plus'
import {getToken, logout} from '@/stores/auth'
import router from '@/router'

const request = axios.create({baseURL: '/api', timeout: 300000})

// 请求拦截：注入 Token
request.interceptors.request.use((config) => {
    const token = getToken()
    if (token && !config.url?.includes('/auth/login')) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// 响应拦截：统一错误处理 + 401 跳转
request.interceptors.response.use(
    (res) => {
        const {code, message, data} = res.data
        if (code !== 0) {
            // AI 配置缺失 — 引导用户去配置
            if (code === 50010) {
                ElMessageBox.confirm(message || 'AI 模型未配置', '配置缺失', {
                    confirmButtonText: '前往配置', cancelButtonText: '取消', type: 'warning',
                }).then(() => router.push('/settings')).catch(() => {
                })
            } else {
                ElMessage.error(message || '请求失败')
            }
            return Promise.reject(new Error(message))
        }
        return data
    },
    (err) => {
        if (err.response?.status === 401) {
            logout()
            router.push('/login')
            ElMessage.warning('登录已过期，请重新登录')
        } else if (err.response?.data?.code === 50010) {
            const msg = err.response.data.message || 'AI 模型未配置'
            ElMessageBox.confirm(msg, '配置缺失', {
                confirmButtonText: '前往配置', cancelButtonText: '取消', type: 'warning',
            }).then(() => router.push('/settings')).catch(() => {
            })
        } else {
            ElMessage.error(err.response?.data?.message || err.message || '网络错误')
        }
        return Promise.reject(err)
    }
)
export default request
