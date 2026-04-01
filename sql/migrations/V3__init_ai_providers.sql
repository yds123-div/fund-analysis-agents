-- ============================================================
-- V3__init_ai_providers.sql
-- Add PROVIDER_TYPE column and initialize AI provider configs
-- ============================================================

-- Add provider type column
ALTER TABLE TM_AI_PROVIDER_CONFIG
    ADD COLUMN PROVIDER_TYPE VARCHAR(20) NOT NULL DEFAULT 'openai' AFTER PROVIDER_NAME;

-- Insert AI provider configurations (API keys will be set via REST API)
INSERT INTO TM_AI_PROVIDER_CONFIG (PROVIDER_CODE, PROVIDER_NAME, PROVIDER_TYPE, BASE_URL, ENABLED)
VALUES ('deepseek', 'DeepSeek', 'openai', 'https://api.deepseek.com', 1),
       ('zhipu', '智谱GLM', 'openai', 'https://open.bigmodel.cn/api/paas/v4', 1),
       ('dashscope', '百炼', 'dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 0);

-- Default agent model bindings (all agents use DeepSeek by default)
INSERT INTO TM_AGENT_MODEL_BINDING (AGENT_ID, THINK_LEVEL, PROVIDER_CODE, MODEL_ID, ENABLED)
VALUES ('default', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('default', 'quick_think', 'deepseek', 'deepseek-chat', 1),
       ('fund_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('technical_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('industry_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('manager_analyst', 'deep_think', 'deepseek', 'deepseek-chat', 1),
       ('sentiment_analyst', 'quick_think', 'deepseek', 'deepseek-chat', 1),
       ('news_analyst', 'quick_think', 'deepseek', 'deepseek-chat', 1);
