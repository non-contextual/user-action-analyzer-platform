package com.useranalyzer.spark.session;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.constant.Constants;
import com.useranalyzer.util.JDBCHelper;
import com.useranalyzer.util.ParamUtils;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 随机抽取 Session 分析
 *
 * 功能:
 *   1. 按日期范围过滤 Session
 *   2. 随机抽取 extractNumber 个 Session（全局均匀随机）
 *   3. 将 Session 摘要写入 session_random_extract 表
 *   4. 将 Session 行为明细写入 session_detail 表
 */
public class RandomSessionExtract {

    public static void analyze(JavaSparkContext sc, long taskId, JSONObject taskParam,
                               String userVisitActionPath, String userInfoPath) {

        System.out.println("[随机抽取Session] taskId=" + taskId + " 开始...");

        int    extractNumber = ParamUtils.getIntParam(taskParam, Constants.PARAM_EXTRACT_NUMBER, 100);
        String startDate     = ParamUtils.getParam(taskParam, Constants.PARAM_START_DATE);
        String endDate       = ParamUtils.getParam(taskParam, Constants.PARAM_END_DATE);

        System.out.println("[随机抽取Session] 抽取数量=" + extractNumber
                + " startDate=" + startDate + " endDate=" + endDate);

        SparkSession spark = SparkSession.builder()
                .sparkContext(sc.sc())
                .getOrCreate();

        // ----------------------------------------------------------------
        // 1. 加载数据（若 KaggleDataLoader 已预注册则复用）
        // ----------------------------------------------------------------
        if (!spark.catalog().tableExists("uva")) {
            spark.read()
                    .option("header", "true")
                    .option("encoding", "UTF-8")
                    .csv(userVisitActionPath)
                    .createOrReplaceTempView("uva");
        }

        // ----------------------------------------------------------------
        // 2. 按日期过滤，获取所有 session_id
        // ----------------------------------------------------------------
        StringBuilder dateWhere = new StringBuilder(
                "WHERE session_id IS NOT NULL AND trim(session_id) != ''");
        if (startDate != null) dateWhere.append(" AND trim(date) >= '").append(startDate).append("'");
        if (endDate   != null) dateWhere.append(" AND trim(date) <= '").append(endDate).append("'");

        List<Row> allRows = spark.sql(
                "SELECT DISTINCT session_id FROM uva " + dateWhere
        ).collectAsList();

        System.out.println("[随机抽取Session] 全量 Session 数: " + allRows.size());

        if (allRows.isEmpty()) {
            System.out.println("[随机抽取Session] 无可用 Session，跳过");
            return;
        }

        // ----------------------------------------------------------------
        // 3. 随机洗牌并取前 N 个
        // ----------------------------------------------------------------
        List<String> allIds = allRows.stream()
                .map(r -> r.getString(0))
                .collect(Collectors.toList());
        Collections.shuffle(allIds, new Random(42));
        int n = Math.min(extractNumber, allIds.size());
        List<String> sampledIds = new ArrayList<>(allIds.subList(0, n));

        System.out.println("[随机抽取Session] 实际抽取: " + sampledIds.size() + " 个 Session");

        // ----------------------------------------------------------------
        // 4. 注册抽样 Session ID 为临时视图（避免超长 IN 子句）
        // ----------------------------------------------------------------
        spark.createDataset(sampledIds, Encoders.STRING())
                .toDF("session_id")
                .createOrReplaceTempView("sampled_session_ids");

        // ----------------------------------------------------------------
        // 5. 计算每个 Session 的摘要
        // ----------------------------------------------------------------
        Dataset<Row> summaryDS = spark.sql(
            "SELECT u.session_id, " +
            "  MIN(u.action_time) AS start_time, " +
            "  MAX(u.action_time) AS end_time, " +
            "  concat_ws(',', collect_set(CASE WHEN u.search_keyword IS NOT NULL " +
            "    AND trim(u.search_keyword) != '' THEN trim(u.search_keyword) END)) AS search_keywords, " +
            "  concat_ws(',', collect_set(CASE WHEN u.click_category_id IS NOT NULL " +
            "    AND trim(CAST(u.click_category_id AS STRING)) != '' " +
            "    AND trim(CAST(u.click_category_id AS STRING)) != '-1' " +
            "    THEN trim(CAST(u.click_category_id AS STRING)) END)) AS click_category_ids " +
            "FROM uva u " +
            "JOIN sampled_session_ids s ON u.session_id = s.session_id " +
            "GROUP BY u.session_id"
        );
        List<Row> summaries = summaryDS.collectAsList();

        // ----------------------------------------------------------------
        // 6. 写入 session_random_extract 表
        // ----------------------------------------------------------------
        writeSessionSummary(taskId, summaries);

        // ----------------------------------------------------------------
        // 7. 获取 Session 行为明细
        // ----------------------------------------------------------------
        Dataset<Row> detailDS = spark.sql(
            "SELECT u.user_id, u.session_id, u.page_id, u.action_time, " +
            "  u.search_keyword, u.click_category_id, u.click_product_id, " +
            "  u.order_category_ids, u.order_product_ids, u.pay_category_ids, u.pay_product_ids " +
            "FROM uva u " +
            "JOIN sampled_session_ids s ON u.session_id = s.session_id"
        );
        List<Row> details = detailDS.collectAsList();

        // ----------------------------------------------------------------
        // 8. 写入 session_detail 表
        // ----------------------------------------------------------------
        writeSessionDetail(taskId, details);

        System.out.println("[随机抽取Session] 完成! session_random_extract=" + summaries.size()
                + " 条, session_detail=" + details.size() + " 条");
    }

    // ----------------------------------------------------------------
    // 写 session_random_extract
    // ----------------------------------------------------------------
    private static void writeSessionSummary(long taskId, List<Row> summaries) {
        JDBCHelper.executeUpdate("DELETE FROM session_random_extract WHERE task_id=?", taskId);

        String sql = "INSERT INTO session_random_extract "
                + "(task_id, session_id, start_time, end_time, search_keywords, click_category_ids) "
                + "VALUES (?,?,?,?,?,?)";

        List<Object[]> batch = new ArrayList<>();
        for (Row row : summaries) {
            batch.add(new Object[]{
                taskId,
                row.get(0) != null ? row.get(0).toString() : "",
                row.get(1) != null ? row.get(1).toString() : null,
                row.get(2) != null ? row.get(2).toString() : null,
                row.get(3) != null ? row.get(3).toString() : "",
                row.get(4) != null ? row.get(4).toString() : ""
            });
        }
        if (!batch.isEmpty()) {
            JDBCHelper.executeBatch(sql, batch);
        }
    }

    // ----------------------------------------------------------------
    // 写 session_detail（分批次避免内存压力）
    // ----------------------------------------------------------------
    private static void writeSessionDetail(long taskId, List<Row> details) {
        JDBCHelper.executeUpdate("DELETE FROM session_detail WHERE task_id=?", taskId);

        String sql = "INSERT INTO session_detail "
                + "(task_id, user_id, session_id, page_id, action_time, search_keyword, "
                + " click_category_id, click_product_id, order_category_ids, "
                + " order_product_ids, pay_category_ids, pay_product_ids) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

        List<Object[]> batch = new ArrayList<>();
        for (Row row : details) {
            // uva columns: user_id, session_id, page_id, action_time, search_keyword,
            //              click_category_id, click_product_id, order_category_ids,
            //              order_product_ids, pay_category_ids, pay_product_ids
            batch.add(new Object[]{
                taskId,
                row.get(0) != null ? toLong(row.get(0)) : null,
                row.get(1) != null ? row.get(1).toString() : null,
                row.get(2) != null ? toLong(row.get(2)) : null,
                row.get(3) != null ? row.get(3).toString() : null,
                row.get(4) != null ? row.get(4).toString() : null,
                row.get(5) != null ? row.get(5).toString() : "-1",  // click_category_id: VARCHAR
                row.get(6) != null ? toLong(row.get(6)) : -1L,       // click_product_id
                row.get(7) != null ? row.get(7).toString() : null,
                row.get(8) != null ? row.get(8).toString() : null,
                row.get(9) != null ? row.get(9).toString() : null,
                row.get(10) != null ? row.get(10).toString() : null
            });
        }

        // Write in sub-batches to avoid JDBC memory pressure
        int batchSize = 5000;
        for (int i = 0; i < batch.size(); i += batchSize) {
            List<Object[]> sub = batch.subList(i, Math.min(i + batchSize, batch.size()));
            JDBCHelper.executeBatch(sql, sub);
        }
    }

    private static long toLong(Object val) {
        if (val instanceof Long)    return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return -1L; }
    }
}
