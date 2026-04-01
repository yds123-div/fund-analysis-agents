import request from './request'

export const getLogTypes = () => request.get('/logs/types')
export const getRecentLogs = (lines = 200, type = 'app') =>
    request.get('/logs/recent', {params: {lines, type}})
