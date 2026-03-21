-- ============================================================
-- 电商用户行为分析大数据平台 - 数据库初始化脚本
-- 数据库: user_action_db
-- ============================================================

SET NAMES utf8mb4;
USE user_action_db;

-- ============================================================
-- 任务参数表: task
-- Spark 作业启动时从此表读取过滤条件（JSON格式）
-- ============================================================
CREATE TABLE IF NOT EXISTS task (
    task_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    task_name VARCHAR(255) NOT NULL               COMMENT '任务名称',
    task_type VARCHAR(50)  DEFAULT 'SESSION'      COMMENT '任务类型: SESSION / TOP10 / RANDOM',
    task_param TEXT                               COMMENT 'JSON格式任务参数',
    create_time DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finish_time DATETIME                          COMMENT '完成时间',
    status    VARCHAR(20)  DEFAULT 'CREATED'      COMMENT '状态: CREATED/RUNNING/FINISHED/FAILED',
    PRIMARY KEY (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Spark任务参数表';

-- ============================================================
-- Session 聚合统计结果表
-- 统计访问时长分布 & 访问步长分布
-- ============================================================
CREATE TABLE IF NOT EXISTS session_aggr_stat (
    task_id             BIGINT NOT NULL COMMENT '任务ID',
    session_count       INT    DEFAULT 0 COMMENT 'Session总数',
    -- 访问时长分布
    visit_length_1s_3s  INT    DEFAULT 0 COMMENT '1-3秒',
    visit_length_4s_6s  INT    DEFAULT 0 COMMENT '4-6秒',
    visit_length_7s_9s  INT    DEFAULT 0 COMMENT '7-9秒',
    visit_length_10s_30s INT   DEFAULT 0 COMMENT '10-30秒',
    visit_length_30s_60s INT   DEFAULT 0 COMMENT '30-60秒',
    visit_length_1m_3m  INT    DEFAULT 0 COMMENT '1-3分钟',
    visit_length_3m_10m INT    DEFAULT 0 COMMENT '3-10分钟',
    visit_length_10m_30m INT   DEFAULT 0 COMMENT '10-30分钟',
    visit_length_30m    INT    DEFAULT 0 COMMENT '30分钟以上',
    -- 访问步长分布
    step_length_1_3     INT    DEFAULT 0 COMMENT '1-3页',
    step_length_4_6     INT    DEFAULT 0 COMMENT '4-6页',
    step_length_7_9     INT    DEFAULT 0 COMMENT '7-9页',
    step_length_10_30   INT    DEFAULT 0 COMMENT '10-30页',
    step_length_30_60   INT    DEFAULT 0 COMMENT '30-60页',
    step_length_60      INT    DEFAULT 0 COMMENT '60页以上',
    PRIMARY KEY (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Session聚合统计结果';

-- ============================================================
-- 热门品类 Top10 结果表
-- ============================================================
CREATE TABLE IF NOT EXISTS top10_category (
    task_id       BIGINT NOT NULL COMMENT '任务ID',
    category_id   VARCHAR(150) NOT NULL COMMENT '品类ID（Kaggle模式为category_code字符串）',
    click_count   BIGINT DEFAULT 0 COMMENT '点击次数',
    order_count   BIGINT DEFAULT 0 COMMENT '下单次数',
    pay_count     BIGINT DEFAULT 0 COMMENT '支付次数',
    PRIMARY KEY (task_id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门品类Top10结果';

-- ============================================================
-- 随机抽取 Session 表（可选功能）
-- ============================================================
CREATE TABLE IF NOT EXISTS session_random_extract (
    task_id           BIGINT       NOT NULL COMMENT '任务ID',
    session_id        VARCHAR(255) NOT NULL COMMENT 'SessionID',
    start_time        VARCHAR(50)           COMMENT 'Session开始时间',
    end_time          VARCHAR(50)           COMMENT 'Session结束时间',
    search_keywords   VARCHAR(500)          COMMENT '搜索词列表（逗号分隔）',
    click_category_ids VARCHAR(255)         COMMENT '点击品类ID列表',
    PRIMARY KEY (task_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随机抽取Session结果';

-- ============================================================
-- Session 明细表（可选功能）
-- ============================================================
CREATE TABLE IF NOT EXISTS session_detail (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    task_id           BIGINT       NOT NULL COMMENT '任务ID',
    user_id           BIGINT                COMMENT '用户ID',
    session_id        VARCHAR(255)          COMMENT 'SessionID',
    page_id           BIGINT                COMMENT '页面ID',
    action_time       VARCHAR(50)           COMMENT '行为时间',
    search_keyword    VARCHAR(255)          COMMENT '搜索词',
    click_category_id VARCHAR(150)  DEFAULT '-1' COMMENT '点击品类ID（Kaggle模式为category_code字符串）',
    click_product_id  BIGINT       DEFAULT -1 COMMENT '点击商品ID',
    order_category_ids VARCHAR(255)         COMMENT '下单品类IDs',
    order_product_ids  VARCHAR(255)         COMMENT '下单商品IDs',
    pay_category_ids   VARCHAR(255)         COMMENT '支付品类IDs',
    pay_product_ids    VARCHAR(255)         COMMENT '支付商品IDs',
    PRIMARY KEY (id),
    INDEX idx_task_session (task_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Session行为明细';

-- ============================================================
-- 页面单跳转化率表（可选功能）
-- ============================================================
CREATE TABLE IF NOT EXISTS page_convert_rate (
    task_id      BIGINT       NOT NULL COMMENT '任务ID',
    page_flow    VARCHAR(255) NOT NULL COMMENT '页面流（如1_2）',
    convert_rate DOUBLE                COMMENT '转化率',
    PRIMARY KEY (task_id, page_flow)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面单跳转化率';

-- ============================================================
-- 插入示例任务参数
-- ============================================================
INSERT INTO task (task_id, task_name, task_type, task_param) VALUES
(1, 'session_analysis_2019', 'SESSION',
 '{"startDate":"2019-01-01","endDate":"2019-12-31","minAge":10,"maxAge":60,"professions":"学生,白领,工人,自由职业","cities":"北京,上海,深圳,广州","sex":"male","keywords":"女装,男装,童装,手机,电脑","categoryIds":"1,2,3,4,5","targetPageFlow":"1,2,3,4,5,6,7"}'),
(2, 'top10_category_2019', 'TOP10',
 '{"startDate":"2019-01-01","endDate":"2019-12-31"}'),
(3, 'random_extract_2019', 'RANDOM',
 '{"startDate":"2019-01-01","endDate":"2019-12-31","extractNumber":1000}')
ON DUPLICATE KEY UPDATE task_name = VALUES(task_name);

-- 授权 spark 用户
GRANT ALL PRIVILEGES ON user_action_db.* TO 'spark'@'%';
FLUSH PRIVILEGES;
