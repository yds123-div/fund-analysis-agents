import request from './request'

export const triggerAnalysis = (fundCode: string, fundName = '') =>
    request.post(`/analysis/${fundCode}`, null, {params: {fundName}})
export const getReport = (batchNo: string) => request.get(`/analysis/report/${batchNo}`)
export const listReports = (fundCode?: string, page = 1, size = 20) =>
    request.get('/analysis/reports', {params: {fundCode, page, size}})
