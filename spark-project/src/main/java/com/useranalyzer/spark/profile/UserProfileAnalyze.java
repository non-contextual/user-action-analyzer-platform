package com.useranalyzer.spark.profile;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.util.JDBCHelper;
import com.useranalyzer.util.ParamUtils;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户画像与行为分层分析
 *
 * 取舍说明：
 *   - Kaggle 数据无真实用户人口属性（年龄/城市 均为 user_id 哈希合成），
 *     因此画像维度选择**行为特征**（购买次数、消费金额、活跃天数等），
 *     而非人口特征，确保分析结论有真实数据支撑。
 *   - 存储粒度为**分层汇总统计**（每个等级一行，共5行），而非全量用户明细
 *     （Kaggle 模式下有 140 万用户，存全量会使数据库过重）。
 *
 * 用户分层规则（基于行为 RFM 简化版）：
 *   VIP     : purchase_count >= 5 且 total_spend >= 100
 *   高价值   : purchase_count >= 2
 *   潜力用户 : cart_count >= 1 且 purchase_count = 0（加购但未付款）
 *   普通用户 : view_count >= 3 且 purchase_count = 0
 *   沉默用户 : 其余（行为极少）
 *
 * 输出表: user_level_stat (task_id, user_level, user_count, avg_spend)
 */
public class UserProfileAnalyze {

    public static void analyze(JavaSparkContext sc, long taskId, JSONObject taskParam) {

        System.out.println("[用户画像] taskId=" + taskId + " 开始...");

        SparkSession spark = SparkSession.builder()
                .sparkContext(sc.sc())
                .getOrCreate();

        // ----------------------------------------------------------------
        // 1. 检查 kaggle_raw 视图是否可用（kaggle 模式）
        //    若不存在则尝试使用已有 uva 视图近似计算
        // ----------------------------------------------------------------
        boolean kaggleMode = spark.catalog().tableExists("kaggle_raw");

        Dataset<Row> levelStats;

        if (kaggleMode) {
            // Kaggle 模式：直接从原始事件表计算行为特征
            // 保留 price 字段以计算真实消费金额
            levelStats = spark.sql(
                "WITH user_features AS (" +
                "  SELECT " +
                "    user_id," +
                "    SUM(CASE WHEN trim(event_type)='view'     THEN 1 ELSE 0 END) AS view_count," +
                "    SUM(CASE WHEN trim(event_type)='cart'     THEN 1 ELSE 0 END) AS cart_count," +
                "    SUM(CASE WHEN trim(event_type)='purchase' THEN 1 ELSE 0 END) AS purchase_count," +
                "    SUM(CASE WHEN trim(event_type)='purchase' AND price IS NOT NULL" +
                "             THEN CAST(price AS DOUBLE) ELSE 0.0 END) AS total_spend," +
                "    COUNT(DISTINCT substr(trim(event_time), 1, 10)) AS active_days," +
                "    COUNT(DISTINCT trim(user_session)) AS session_count" +
                "  FROM kaggle_raw" +
                "  WHERE user_id IS NOT NULL AND trim(user_id) != ''" +
                "  GROUP BY user_id" +
                ")," +
                "user_levels AS (" +
                "  SELECT " +
                "    user_id, total_spend," +
                "    CASE" +
                "      WHEN purchase_count >= 5 AND total_spend >= 100 THEN 'VIP'" +
                "      WHEN purchase_count >= 2 THEN '高价值'" +
                "      WHEN cart_count >= 1 AND purchase_count = 0    THEN '潜力'" +
                "      WHEN view_count  >= 3 AND purchase_count = 0   THEN '普通'" +
                "      ELSE '沉默'" +
                "    END AS user_level" +
                "  FROM user_features" +
                ")" +
                "SELECT user_level," +
                "       COUNT(*) AS user_count," +
                "       ROUND(AVG(total_spend), 2) AS avg_spend" +
                " FROM user_levels" +
                " GROUP BY user_level" +
                " ORDER BY user_count DESC"
            );
        } else {
            // 生成数据模式：用 uva 视图近似计算（无 price，消费为 0）
            System.out.println("[用户画像] 未检测到 kaggle_raw，使用 uva 视图近似计算...");
            levelStats = spark.sql(
                "WITH user_features AS (" +
                "  SELECT " +
                "    user_id," +
                "    COUNT(DISTINCT page_id) AS view_count," +
                "    SUM(CASE WHEN order_product_ids IS NOT NULL AND order_product_ids != '' THEN 1 ELSE 0 END) AS cart_count," +
                "    SUM(CASE WHEN pay_product_ids   IS NOT NULL AND pay_product_ids   != '' THEN 1 ELSE 0 END) AS purchase_count," +
                "    0.0 AS total_spend" +
                "  FROM uva" +
                "  GROUP BY user_id" +
                ")," +
                "user_levels AS (" +
                "  SELECT" +
                "    total_spend," +
                "    CASE" +
                "      WHEN purchase_count >= 5 THEN 'VIP'" +
                "      WHEN purchase_count >= 2 THEN '高价值'" +
                "      WHEN cart_count >= 1 AND purchase_count = 0  THEN '潜力'" +
                "      WHEN view_count  >= 3 AND purchase_count = 0 THEN '普通'" +
                "      ELSE '沉默'" +
                "    END AS user_level" +
                "  FROM user_features" +
                ")" +
                "SELECT user_level, COUNT(*) AS user_count, 0.0 AS avg_spend" +
                " FROM user_levels" +
                " GROUP BY user_level" +
                " ORDER BY user_count DESC"
            );
        }

        List<Row> rows = levelStats.collectAsList();
        System.out.println("[用户画像] 分层统计结果（共 " + rows.size() + " 个等级）:");
        for (Row row : rows) {
            System.out.printf("  %-8s  用户数=%-8s  平均消费=%.2f%n",
                    row.get(0), row.get(1), ((Number) row.get(2)).doubleValue());
        }

        // ----------------------------------------------------------------
        // 2. 写入 MySQL user_level_stat 表
        // ----------------------------------------------------------------
        writeToMySQL(taskId, rows);
        System.out.println("[用户画像] 结果已写入 MySQL user_level_stat 表");
    }

    private static void writeToMySQL(long taskId, List<Row> rows) {
        JDBCHelper.executeUpdate("DELETE FROM user_level_stat WHERE task_id=?", taskId);

        String sql = "INSERT INTO user_level_stat (task_id, user_level, user_count, avg_spend) VALUES (?,?,?,?)";
        List<Object[]> batch = new ArrayList<>();
        for (Row row : rows) {
            String level    = row.get(0).toString();
            long   count    = ((Number) row.get(1)).longValue();
            double avgSpend = ((Number) row.get(2)).doubleValue();
            batch.add(new Object[]{taskId, level, count, avgSpend});
        }
        if (!batch.isEmpty()) {
            JDBCHelper.executeBatch(sql, batch);
        }
    }
}
