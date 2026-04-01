import request from './request'

export const getProviders = () => request.get('/ai/providers')
export const saveProvider = (data: any) => request.post('/ai/providers', data)
export const testConnectivity = (providerCode: string) => request.post(`/ai/test/${providerCode}`)
export const getBindings = () => request.get('/ai/bindings')
export const saveBinding = (data: any) => request.post('/ai/bindings', data)
export const getHealth = () => request.get('/health')
