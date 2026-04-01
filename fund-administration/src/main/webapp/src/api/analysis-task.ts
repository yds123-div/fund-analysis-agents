import request from './request'

export const getAnalysisTasks = () => request.get('/tasks/config')
export const saveAnalysisTask = (data: any) => request.post('/tasks/config', data)
export const deleteAnalysisTask = (id: number) => request.delete(`/tasks/config/${id}`)
