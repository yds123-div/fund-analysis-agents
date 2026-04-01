import request from './request'

export const getNotificationChannels = () => request.get('/notification/channels')
export const saveNotificationChannel = (data: any) => request.post('/notification/channels', data)
export const deleteNotificationChannel = (id: number) => request.delete(`/notification/channels/${id}`)
export const testNotificationChannel = (id: number) => request.post(`/notification/channels/${id}/test`)
export const getNotificationHistory = (page = 1, size = 20) =>
    request.get('/notification/history', {params: {page, size}})
