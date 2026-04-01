import request from './request'

export const getFundBasic = (code: string) => request.get(`/funds/${code}`)
export const getFundEstimate = (code: string) => request.get(`/funds/${code}/estimate`)
export const getFundNav = (code: string, days = 30) => request.get(`/funds/${code}/nav`, {params: {days}})
export const searchFunds = (keyword: string) => request.get('/funds/search', {params: {keyword}})
