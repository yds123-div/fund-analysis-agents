import request from './request'

export const getSystemConfig = (group?: string) =>
    request.get('/config/system', {params: group ? {group} : {}})
export const updateSystemConfig = (data: any) => request.put('/config/system', data)
export const getDatasourceConfig = () => request.get('/config/datasource')
export const updateDatasourceConfig = (data: any) => request.put('/config/datasource', data)
