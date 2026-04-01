import request from './request'

export const getTaskProgress = (batchNo: string) => request.get(`/task/progress/${batchNo}`)
export const listExecutions = (params?: { status?: string; fundCode?: string; page?: number; size?: number }) =>
    request.get('/task/executions', {params})
