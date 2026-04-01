import request from './request'

export const getTaskConfigs = () => request.get('/tasks/config')
export const saveTaskConfig = (data: any) => request.post('/tasks/config', data)
export const deleteTaskConfig = (id: number) => request.delete(`/tasks/config/${id}`)
export const triggerTask = (id: number) => request.post(`/tasks/config/${id}/trigger`)
export const getSchedulerStatus = () => request.get('/tasks/config/status')
