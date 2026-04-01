import request from './request'

export const getPortfolioList = () => request.get('/portfolio')
export const addOrUpdatePortfolio = (data: any) => request.post('/portfolio', data)
export const removePortfolio = (fundCode: string) => request.delete(`/portfolio/${fundCode}`)
export const getPortfolioPnL = () => request.get('/portfolio/pnl')
export const getPortfolioAnalysis = () => request.get('/portfolio/analysis')
export const getPortfolioTrend = (days: number = 30) =>
    request.get('/portfolio/trend', {params: {days}})
export const aiPortfolioAnalysis = (budget: number = 0) =>
    request.post('/portfolio/ai-analysis', null, {params: {budget}})
export const getLatestAiAnalysis = () => request.get('/portfolio/ai-analysis/latest')
export const getPortfolioForecast = () => request.get('/portfolio/forecast')
