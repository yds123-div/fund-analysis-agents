import request from './request'

export const getWatchList = () => request.get('/watchlist')
export const addWatch = (fundCode: string, notes = '') =>
    request.post(`/watchlist/${fundCode}`, null, {params: {notes}})
export const removeWatch = (fundCode: string) => request.delete(`/watchlist/${fundCode}`)
