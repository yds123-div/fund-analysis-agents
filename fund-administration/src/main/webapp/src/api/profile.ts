import request from './request'

export const getProfile = () => request.get('/profile')
export const saveProfile = (data: any) => request.post('/profile', data)
