import request from './request'

export const getMarketNews = (count: number = 20) => request.get('/news/market', {params: {count}})
export const getFundNews = (keyword: string, count: number = 10) => request.get('/news/fund', {
    params: {
        keyword,
        count
    }
})
export const getWatchlistNews = () => request.get('/news/watchlist')
