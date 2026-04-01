import request from './request'

export const scanMarket = () => request.post('/market/scan')
export const getLatestScan = () => request.get('/market/scan/latest')
export const screenFunds = (riskPreference = '中等风险') =>
    request.post('/market/screen', null, {params: {riskPreference}})
export const listRecommendations = (page = 1, size = 10) =>
    request.get('/market/recommendations', {params: {page, size}})
