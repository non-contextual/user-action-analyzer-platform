package com.useranalyzer.constant;

/**
 * 全局常量定义
 */
public interface Constants {

    // ======== 默认值 ========
    int MAX_AGE_DEFAULT = 999;  // maxAge 无限制时的哨兵值

    // ======== 任务参数 JSON key ========
    String PARAM_START_DATE       = "startDate";
    String PARAM_END_DATE         = "endDate";
    String PARAM_MIN_AGE          = "minAge";
    String PARAM_MAX_AGE          = "maxAge";
    String PARAM_PROFESSIONS      = "professions";
    String PARAM_CITIES           = "cities";
    String PARAM_SEX              = "sex";
    String PARAM_KEYWORDS         = "keywords";
    String PARAM_CATEGORY_IDS     = "categoryIds";
    String PARAM_TARGET_PAGE_FLOW = "targetPageFlow";
    String PARAM_EXTRACT_NUMBER   = "extractNumber";

    // ======== Session 聚合统计 accumulator key ========
    // 访问时长分布
    String FIELD_VISIT_LENGTH_1S_3S   = "visit_length_1s_3s";
    String FIELD_VISIT_LENGTH_4S_6S   = "visit_length_4s_6s";
    String FIELD_VISIT_LENGTH_7S_9S   = "visit_length_7s_9s";
    String FIELD_VISIT_LENGTH_10S_30S = "visit_length_10s_30s";
    String FIELD_VISIT_LENGTH_30S_60S = "visit_length_30s_60s";
    String FIELD_VISIT_LENGTH_1M_3M   = "visit_length_1m_3m";
    String FIELD_VISIT_LENGTH_3M_10M  = "visit_length_3m_10m";
    String FIELD_VISIT_LENGTH_10M_30M = "visit_length_10m_30m";
    String FIELD_VISIT_LENGTH_30M     = "visit_length_30m";

    // 访问步长分布
    String FIELD_STEP_LENGTH_1_3   = "step_length_1_3";
    String FIELD_STEP_LENGTH_4_6   = "step_length_4_6";
    String FIELD_STEP_LENGTH_7_9   = "step_length_7_9";
    String FIELD_STEP_LENGTH_10_30 = "step_length_10_30";
    String FIELD_STEP_LENGTH_30_60 = "step_length_30_60";
    String FIELD_STEP_LENGTH_60    = "step_length_60";

    // Session 总数
    String FIELD_SESSION_COUNT = "session_count";
}
