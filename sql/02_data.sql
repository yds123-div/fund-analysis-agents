-- ============================================================
-- data.sql
-- Fund Analysis Agents - Full DML (merged from V2/V3/V4)
-- ============================================================

-- 1. Default admin user (password: admin2026, BCrypt encrypted)
INSERT IGNORE INTO TM_USER (USERNAME, EMAIL, PASSWORD_HASH, ROLE, STATUS)
VALUES ('admin', 'admin@fund.local', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', 1);

-- 2. Default datasource configurations
INSERT IGNORE INTO TM_DATASOURCE_CONFIG (SOURCE_CODE, SOURCE_NAME, SOURCE_TYPE, BASE_URL, DATA_LEVEL, PRIORITY, ENABLED)
VALUES ('eastmoney', '天天基金', 'HTTP', 'https://api.fund.eastmoney.com', 'L1', 1, 1),
       ('eastmoney_quote', '东方财富行情', 'HTTP', 'https://push2.eastmoney.com', 'L1', 2, 1),
       ('tushare', 'Tushare Pro', 'REST_API', 'https://api.tushare.pro', 'L2', 10, 0);

-- 3. Default system configurations
INSERT IGNORE INTO TM_SYSTEM_CONFIG (CONFIG_GROUP, CONFIG_KEY, CONFIG_VALUE, CONFIG_TYPE, DESCRIPTION)
VALUES ('agent', 'debate_max_rounds', '3', 'INTEGER', 'Max debate rounds between bull/bear researchers'),
       ('agent', 'analysis_timeout', '300', 'INTEGER', 'Analysis task timeout in seconds'),
       ('agent', 'parallel_execution', 'true', 'BOOLEAN', 'Enable parallel agent execution'),
       ('report', 'default_type', 'DAILY', 'STRING', 'Default report type'),
       ('notification', 'bark_group', 'fund-analysis', 'STRING', 'Bark notification group name');

-- 4. AI provider configurations
INSERT IGNORE INTO TM_AI_PROVIDER_CONFIG (PROVIDER_CODE, PROVIDER_NAME, PROVIDER_TYPE, BASE_URL, ENABLED)
VALUES ('deepseek', 'DeepSeek', 'openai', 'https://api.deepseek.com', 1),
       ('zhipu', '智谱GLM', 'openai', 'https://open.bigmodel.cn/api/paas/v4', 1),
       ('dashscope', '百炼', 'dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 0);

-- 5. Default agent model bindings
INSERT IGNORE INTO TM_AGENT_MODEL_BINDING (AGENT_ID, THINK_LEVEL, PROVIDER_CODE, MODEL_ID, ENABLED)
VALUES ('default', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('default', 'quick_think', 'deepseek', 'deepseek-chat', 1),
       ('fund_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('technical_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('industry_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('manager_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('sentiment_analyst', 'quick_think', 'deepseek', 'deepseek-chat', 1),
       ('news_analyst', 'quick_think', 'deepseek', 'deepseek-chat', 1);
