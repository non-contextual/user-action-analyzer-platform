package com.useranalyzer.spark.session;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.constant.Constants;
import com.useranalyzer.util.JDBCHelper;
import com.useranalyzer.util.ParamUtils;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.*;

/**
 * Session 聚合统计分析（基于 DataFrame/SQL 实现，避免编码和序列化问题）
 *
 * 功能：
 *   1. 按任务参数过滤 Session（时间范围、年龄、职业、城市、性别、关键词、品类）
 *   2. 统计 Session 访问时长分布和访问步长分布
 *   3. 将结果写入 MySQL session_aggr_stat 表
 */
public class UserVisitSessionAnalyze {

    public static void analyze(JavaSparkContext sc, long taskId, JSONObject taskParam,
                               String userVisitActionPath, String userInfoPath) {

        System.out.println("[Session分析] taskId=" + taskId + " 开始...");

        // ----------------------------------------------------------------
        // 1. 获取任务过滤参数
        // ----------------------------------------------------------------
        String startDate   = ParamUtils.getParam(taskParam, Constants.PARAM_START_DATE);
        String endDate     = ParamUtils.getParam(taskParam, Constants.PARAM_END_DATE);
        int    minAge      = ParamUtils.getIntParam(taskParam, Constants.PARAM_MIN_AGE, 0);
        int    maxAge      = ParamUtils.getIntParam(taskParam, Constants.PARAM_MAX_AGE, Constants.MAX_AGE_DEFAULT);
        String professions = ParamUtils.getParam(taskParam, Constants.PARAM_PROFESSIONS);
        String cities      = ParamUtils.getParam(taskParam, Constants.PARAM_CITIES);
        String sex         = ParamUtils.getParam(taskParam, Constants.PARAM_SEX);
        String keywords    = ParamUtils.getParam(taskParam, Constants.PARAM_KEYWORDS);
        String categoryIds = ParamUtils.getParam(taskParam, Constants.PARAM_CATEGORY_IDS);

        System.out.println("[Session分析] 过滤参数: startDate=" + startDate + " endDate=" + endDate
                + " minAge=" + minAge + " maxAge=" + maxAge
                + " professions=" + professions + " cities=" + cities
                + " sex=" + sex + " keywords=" + keywords + " categoryIds=" + categoryIds);

        // ----------------------------------------------------------------
        // 2. 创建 SparkSession（共享已有 SparkContext）
        // ----------------------------------------------------------------
        SparkSession spark = SparkSession.builder()
                .sparkContext(sc.sc())
                .getOrCreate();

        // ----------------------------------------------------------------
        // 3. 加载数据：若视图已由 KaggleDataLoader 预注册则跳过文件读取
        // ----------------------------------------------------------------
        boolean viewsPreloaded = spark.catalog().tableExists("uva")
                                  && spark.catalog().tableExists("user_info");

        if (!viewsPreloaded) {
            Dataset<Row> actionDF = spark.read()
                    .option("header", "true")
                    .option("encoding", "UTF-8")
                    .csv(userVisitActionPath);
            Dataset<Row> userDF = spark.read()
                    .option("header", "true")
                    .option("encoding", "UTF-8")
                    .csv(userInfoPath);
            actionDF.createOrReplaceTempView("uva");
            userDF.createOrReplaceTempView("user_info");
        }

        System.out.println("[Session分析] 行为数据总行数: " + spark.table("uva").count());
        System.out.println("[Session分析] 用户数据总行数: " + spark.table("user_info").count());

        // ----------------------------------------------------------------
        // 4. 构建 SQL 过滤条件（使用 trim() 防止 CRLF/空格问题）
        // ----------------------------------------------------------------
        StringBuilder where = new StringBuilder("WHERE 1=1");

        // 日期过滤
        if (startDate != null) where.append(" AND trim(a.date) >= '").append(startDate).append("'");
        if (endDate   != null) where.append(" AND trim(a.date) <= '").append(endDate).append("'");

        // 年龄过滤
        if (minAge > 0)   where.append(" AND u.age >= ").append(minAge);
        if (maxAge < Constants.MAX_AGE_DEFAULT) where.append(" AND u.age <= ").append(maxAge);

        // 性别过滤
        if (sex != null && !sex.isEmpty()) {
            where.append(" AND LOWER(trim(u.sex)) = '").append(sex.toLowerCase()).append("'");
        }

        // 职业过滤（IN 列表）
        if (professions != null && !professions.isEmpty()) {
            where.append(" AND trim(u.professional) IN (")
                 .append(toSqlInList(professions))
                 .append(")");
        }

        // 城市过滤（IN 列表）
        if (cities != null && !cities.isEmpty()) {
            where.append(" AND trim(u.city) IN (")
                 .append(toSqlInList(cities))
                 .append(")");
        }

        String joinSql = "SELECT a.session_id, a.action_time, a.page_id, "
                       + "a.search_keyword, a.click_category_id, "
                       + "a.order_category_ids, a.pay_category_ids "
                       + "FROM uva a JOIN user_info u ON CAST(a.user_id AS BIGINT) = CAST(u.user_id AS BIGINT) "
                       + where;

        Dataset<Row> filteredDF = spark.sql(joinSql);
        filteredDF.createOrReplaceTempView("filtered");

        long filteredCount = filteredDF.count();
        System.out.println("[Session分析] 用户&日期过滤后行为数: " + filteredCount);

        // ----------------------------------------------------------------
        // 5. 可选：按关键词/品类在 Session 粒度再次过滤
        //    只保留 Session 内至少有一条行为匹配关键词或品类的 Session
        // ----------------------------------------------------------------
        if ((keywords != null && !keywords.isEmpty()) || (categoryIds != null && !categoryIds.isEmpty())) {
            StringBuilder matchCond = new StringBuilder("WHERE (");
            boolean first = true;

            if (keywords != null && !keywords.isEmpty()) {
                matchCond.append("trim(search_keyword) IN (").append(toSqlInList(keywords)).append(")");
                first = false;
            }
            if (categoryIds != null && !categoryIds.isEmpty()) {
                if (!first) matchCond.append(" OR ");
                // 点击品类匹配
                matchCond.append("trim(click_category_id) IN (")
                         .append(toSqlInList(categoryIds)).append(")");
                first = false;
            }
            matchCond.append(")");

            String matchSql = "SELECT DISTINCT session_id FROM filtered " + matchCond;
            Dataset<Row> matchedSessions = spark.sql(matchSql);
            matchedSessions.createOrReplaceTempView("matched_sessions");

            spark.sql("SELECT f.* FROM filtered f "
                    + "JOIN matched_sessions ms ON f.session_id = ms.session_id")
                 .createOrReplaceTempView("filtered");

            long kwFiltered = spark.sql("SELECT COUNT(*) FROM filtered").first().getLong(0);
            System.out.println("[Session分析] 关键词/品类过滤后行为数: " + kwFiltered);
        }

        // ----------------------------------------------------------------
        // 6. 对每个 Session 计算时长和步长
        // ----------------------------------------------------------------
        // 时长 = 最后行为时间 - 第一行为时间 (秒)
        // 步长 = Session 内浏览的不同商品数量（page_id 在 Kaggle 模式下映射自 product_id）
        Dataset<Row> sessionStats = spark.sql(
            "SELECT session_id, "
            + "UNIX_TIMESTAMP(MAX(action_time)) - UNIX_TIMESTAMP(MIN(action_time)) AS visit_length, "
            + "COUNT(DISTINCT page_id) AS step_length "
            + "FROM filtered "
            + "GROUP BY session_id"
        );

        List<Row> sessions = sessionStats.collectAsList();
        System.out.println("[Session分析] 共统计 " + sessions.size() + " 个 Session");

        // ----------------------------------------------------------------
        // 7. 统计分布
        // ----------------------------------------------------------------
        Map<String, Long> stats = new HashMap<>();
        for (String key : getAllStatKeys()) {
            stats.put(key, 0L);
        }

        for (Row row : sessions) {
            long visitLength = row.isNullAt(1) ? 0L : ((Number) row.get(1)).longValue();
            long stepLength  = row.isNullAt(2) ? 0L : ((Number) row.get(2)).longValue();

            stats.merge(Constants.FIELD_SESSION_COUNT, 1L, Long::sum);

            // 访问时长分布（visitLength=0 为单动作 session，归入最短区间）
            if      (visitLength >= 1   && visitLength <= 3)    stats.merge(Constants.FIELD_VISIT_LENGTH_1S_3S,   1L, Long::sum);
            else if (visitLength >= 4   && visitLength <= 6)    stats.merge(Constants.FIELD_VISIT_LENGTH_4S_6S,   1L, Long::sum);
            else if (visitLength >= 7   && visitLength <= 9)    stats.merge(Constants.FIELD_VISIT_LENGTH_7S_9S,   1L, Long::sum);
            else if (visitLength >= 10  && visitLength <= 30)   stats.merge(Constants.FIELD_VISIT_LENGTH_10S_30S, 1L, Long::sum);
            else if (visitLength >= 31  && visitLength <= 60)   stats.merge(Constants.FIELD_VISIT_LENGTH_30S_60S, 1L, Long::sum);
            else if (visitLength >= 61  && visitLength <= 180)  stats.merge(Constants.FIELD_VISIT_LENGTH_1M_3M,   1L, Long::sum);
            else if (visitLength >= 181 && visitLength <= 600)  stats.merge(Constants.FIELD_VISIT_LENGTH_3M_10M,  1L, Long::sum);
            else if (visitLength >= 601 && visitLength <= 1800) stats.merge(Constants.FIELD_VISIT_LENGTH_10M_30M, 1L, Long::sum);
            else if (visitLength > 1800)                        stats.merge(Constants.FIELD_VISIT_LENGTH_30M,     1L, Long::sum);
            else /* visitLength == 0: 单动作 session */         stats.merge(Constants.FIELD_VISIT_LENGTH_1S_3S,   1L, Long::sum);

            // 访问步长分布
            if      (stepLength >= 1  && stepLength <= 3)   stats.merge(Constants.FIELD_STEP_LENGTH_1_3,  1L, Long::sum);
            else if (stepLength >= 4  && stepLength <= 6)   stats.merge(Constants.FIELD_STEP_LENGTH_4_6,  1L, Long::sum);
            else if (stepLength >= 7  && stepLength <= 9)   stats.merge(Constants.FIELD_STEP_LENGTH_7_9,  1L, Long::sum);
            else if (stepLength >= 10 && stepLength <= 30)  stats.merge(Constants.FIELD_STEP_LENGTH_10_30,1L, Long::sum);
            else if (stepLength >= 31 && stepLength <= 60)  stats.merge(Constants.FIELD_STEP_LENGTH_30_60,1L, Long::sum);
            else if (stepLength > 60)                       stats.merge(Constants.FIELD_STEP_LENGTH_60,   1L, Long::sum);
        }

        // ----------------------------------------------------------------
        // 8. 写入 MySQL
        // ----------------------------------------------------------------
        writeToMySQL(taskId, stats);
        System.out.println("[Session分析] 结果已写入 MySQL session_aggr_stat 表");
        System.out.println("[Session分析] 统计结果: sessionCount=" + stats.get(Constants.FIELD_SESSION_COUNT));
    }

    /** 将逗号分隔的字符串转为 SQL IN 列表（带单引号） */
    private static String toSqlInList(String csv) {
        StringBuilder sb = new StringBuilder();
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("'").append(parts[i].trim().replace("'", "\\'")).append("'");
        }
        return sb.toString();
    }

    private static List<String> getAllStatKeys() {
        return Arrays.asList(
            Constants.FIELD_SESSION_COUNT,
            Constants.FIELD_VISIT_LENGTH_1S_3S,   Constants.FIELD_VISIT_LENGTH_4S_6S,
            Constants.FIELD_VISIT_LENGTH_7S_9S,   Constants.FIELD_VISIT_LENGTH_10S_30S,
            Constants.FIELD_VISIT_LENGTH_30S_60S, Constants.FIELD_VISIT_LENGTH_1M_3M,
            Constants.FIELD_VISIT_LENGTH_3M_10M,  Constants.FIELD_VISIT_LENGTH_10M_30M,
            Constants.FIELD_VISIT_LENGTH_30M,
            Constants.FIELD_STEP_LENGTH_1_3,  Constants.FIELD_STEP_LENGTH_4_6,
            Constants.FIELD_STEP_LENGTH_7_9,  Constants.FIELD_STEP_LENGTH_10_30,
            Constants.FIELD_STEP_LENGTH_30_60, Constants.FIELD_STEP_LENGTH_60
        );
    }

    private static void writeToMySQL(long taskId, Map<String, Long> stats) {
        String sql = "INSERT INTO session_aggr_stat ("
                + "task_id, session_count, "
                + "visit_length_1s_3s, visit_length_4s_6s, visit_length_7s_9s, "
                + "visit_length_10s_30s, visit_length_30s_60s, visit_length_1m_3m, "
                + "visit_length_3m_10m, visit_length_10m_30m, visit_length_30m, "
                + "step_length_1_3, step_length_4_6, step_length_7_9, "
                + "step_length_10_30, step_length_30_60, step_length_60"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE "
                + "session_count=VALUES(session_count), "
                + "visit_length_1s_3s=VALUES(visit_length_1s_3s), "
                + "visit_length_4s_6s=VALUES(visit_length_4s_6s), "
                + "visit_length_7s_9s=VALUES(visit_length_7s_9s), "
                + "visit_length_10s_30s=VALUES(visit_length_10s_30s), "
                + "visit_length_30s_60s=VALUES(visit_length_30s_60s), "
                + "visit_length_1m_3m=VALUES(visit_length_1m_3m), "
                + "visit_length_3m_10m=VALUES(visit_length_3m_10m), "
                + "visit_length_10m_30m=VALUES(visit_length_10m_30m), "
                + "visit_length_30m=VALUES(visit_length_30m), "
                + "step_length_1_3=VALUES(step_length_1_3), "
                + "step_length_4_6=VALUES(step_length_4_6), "
                + "step_length_7_9=VALUES(step_length_7_9), "
                + "step_length_10_30=VALUES(step_length_10_30), "
                + "step_length_30_60=VALUES(step_length_30_60), "
                + "step_length_60=VALUES(step_length_60)";

        JDBCHelper.executeUpdate(sql,
                taskId,
                stats.getOrDefault(Constants.FIELD_SESSION_COUNT,        0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_1S_3S,   0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_4S_6S,   0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_7S_9S,   0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_10S_30S, 0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_30S_60S, 0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_1M_3M,   0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_3M_10M,  0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_10M_30M, 0L),
                stats.getOrDefault(Constants.FIELD_VISIT_LENGTH_30M,     0L),
                stats.getOrDefault(Constants.FIELD_STEP_LENGTH_1_3,   0L),
                stats.getOrDefault(Constants.FIELD_STEP_LENGTH_4_6,   0L),
                stats.getOrDefault(Constants.FIELD_STEP_LENGTH_7_9,   0L),
                stats.getOrDefault(Constants.FIELD_STEP_LENGTH_10_30, 0L),
                stats.getOrDefault(Constants.FIELD_STEP_LENGTH_30_60, 0L),
                stats.getOrDefault(Constants.FIELD_STEP_LENGTH_60,    0L)
        );
    }
}
