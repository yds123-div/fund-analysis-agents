-- ============================================================
-- V2__init_data.sql
-- Fund Analysis Agents - Initial Data
-- ============================================================

-- 1. Default admin user
INSERT INTO TM_USER (USERNAME, EMAIL, PASSWORD_HASH, ROLE, STATUS)
VALUES ('admin', 'admin@fund.local', '$2a$10$B1SSyN6tseu0U0aKKSgUme5EDA5wcylQbvIJ9/kUqhFug81pPw/Qy', 'ADMIN', 1);

-- 2. Default datasource configurations
INSERT INTO TM_DATASOURCE_CONFIG (SOURCE_CODE, SOURCE_NAME, SOURCE_TYPE, BASE_URL, DATA_LEVEL, PRIORITY, ENABLED)
VALUES ('eastmoney', '天天基金', 'HTTP', 'https://api.fund.eastmoney.com', 'L1', 1, 1),
       ('eastmoney_quote', '东方财富行情', 'HTTP', 'https://push2.eastmoney.com', 'L1', 2, 1),
       ('tushare', 'Tushare Pro', 'REST_API', 'https://api.tushare.pro', 'L2', 10, 0);

-- 3. Default system configurations
INSERT INTO TM_SYSTEM_CONFIG (CONFIG_GROUP, CONFIG_KEY, CONFIG_VALUE, CONFIG_TYPE, DESCRIPTION)
VALUES ('agent', 'debate_max_rounds', '3', 'INTEGER', 'Max debate rounds between bull/bear researchers'),
       ('agent', 'analysis_timeout', '300', 'INTEGER', 'Analysis task timeout in seconds'),
       ('agent', 'parallel_execution', 'true', 'BOOLEAN', 'Enable parallel agent execution'),
       ('report', 'default_type', 'DAILY', 'STRING', 'Default report type'),
       ('notification', 'bark_group', 'fund-analysis', 'STRING', 'Bark notification group name');
