import request from './request'

/** 登录 */
export const loginApi = (username: string, password: string) =>
    request.post('/auth/login', {username, password})

/** 刷新 Token */
export const refreshToken = () => request.post('/auth/refresh')

/** 获取当前用户 */
export const getCurrentUser = () => request.get('/auth/me')

/** 用户管理 */
export const getUsers = () => request.get('/users')
export const createUser = (data: Record<string, any>) => request.post('/users', data)
export const updateUser = (id: number, data: Record<string, any>) => request.put(`/users/${id}`, data)
export const deleteUser = (id: number) => request.delete(`/users/${id}`)
