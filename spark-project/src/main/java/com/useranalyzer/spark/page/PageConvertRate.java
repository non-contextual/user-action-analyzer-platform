package com.useranalyzer.spark.page;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.constant.Constants;
import com.useranalyzer.util.JDBCHelper;
import com.useranalyzer.util.ParamUtils;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 页面单跳转化率分析
 *
 * 功能:
 *   给定 targetPageFlow (如 "1,2,3,4,5,6,7")，计算相邻页面对的单跳转化率:
 *     rate(A→B) = 在同一 Session 中 B 紧跟 A 之后被访问的 Session 数 / 访问过 A 的 Session 数
 *
 *   结果写入 MySQL page_convert_rate 表:
 *     (task_id, page_flow, convert_rate)  例: (1, "1_2", 0.3256)
 */
public class PageConvertRate {

    public static void analyze(JavaSparkContext sc, long taskId, JSONObject taskParam,
                               String userVisitActionPath, String userInfoPath) {

        System.out.println("[页面转化率] taskId=" + taskId + " 开始...");

        String targetPageFlow = ParamUtils.getParam(taskParam, Constants.PARAM_TARGET_PAGE_FLOW);
        if (targetPageFlow == null || targetPageFlow.isEmpty()) {
            System.out.println("[页面转化率] 未配置 targetPageFlow，跳过");
            return;
        }

        String startDate = ParamUtils.getParam(taskParam, Constants.PARAM_START_DATE);
        String endDate   = ParamUtils.getParam(taskParam, Constants.PARAM_END_DATE);

        System.out.println("[页面转化率] targetPageFlow=" + targetPageFlow);

        SparkSession spark = SparkSession.builder()
                .sparkContext(sc.sc())
                .getOrCreate();

        // ----------------------------------------------------------------
        // 1. 加载数据
        // ----------------------------------------------------------------
        if (!spark.catalog().tableExists("uva")) {
            spark.read()
                    .option("header", "true")
                    .option("encoding", "UTF-8")
                    .csv(userVisitActionPath)
                    .createOrReplaceTempView("uva");
        }

        // ----------------------------------------------------------------
        // 2. 过滤日期，只保留 session_id + page_id + action_time
        // ----------------------------------------------------------------
        StringBuilder dateWhere = new StringBuilder("WHERE 1=1");
        if (startDate != null) dateWhere.append(" AND trim(date) >= '").append(startDate).append("'");
        if (endDate   != null) dateWhere.append(" AND trim(date) <= '").append(endDate).append("'");

        spark.sql(
            "SELECT CAST(session_id AS STRING) AS session_id, " +
            "  CAST(page_id AS BIGINT) AS page_id, " +
            "  action_time " +
            "FROM uva " + dateWhere +
            " AND session_id IS NOT NULL AND page_id IS NOT NULL"
        ).createOrReplaceTempView("uva_page");

        // ----------------------------------------------------------------
        // 3. 计算每行的前一页（LAG 窗口函数，同一 Session 内按时间排序）
        // ----------------------------------------------------------------
        spark.sql(
            "SELECT session_id, page_id, " +
            "  LAG(page_id, 1) OVER (PARTITION BY session_id ORDER BY action_time) AS prev_page " +
            "FROM uva_page"
        ).createOrReplaceTempView("page_transitions");

        System.out.println("[页面转化率] 页面转移视图已创建");

        // ----------------------------------------------------------------
        // 4. 解析 targetPageFlow，逐对计算转化率
        // ----------------------------------------------------------------
        String[] pages = targetPageFlow.split(",");
        JDBCHelper.executeUpdate("DELETE FROM page_convert_rate WHERE task_id=?", taskId);

        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < pages.length - 1; i++) {
            long fromPage, toPage;
            try {
                fromPage = Long.parseLong(pages[i].trim());
                toPage   = Long.parseLong(pages[i + 1].trim());
            } catch (NumberFormatException e) {
                System.out.println("[页面转化率] 跳过无效页面对: " + pages[i] + "→" + pages[i + 1]);
                continue;
            }

            // 分母：访问过 fromPage 的不重复 Session 数
            long denominator = spark.sql(
                "SELECT COUNT(DISTINCT session_id) FROM uva_page WHERE page_id = " + fromPage
            ).first().getLong(0);

            if (denominator == 0) {
                System.out.println("[页面转化率] 页面 " + fromPage + " 无访问记录，跳过");
                continue;
            }

            // 分子：在同 Session 中 toPage 紧随 fromPage 的 Session 数
            long numerator = spark.sql(
                "SELECT COUNT(DISTINCT session_id) FROM page_transitions " +
                "WHERE prev_page = " + fromPage + " AND page_id = " + toPage
            ).first().getLong(0);

            double rate = (double) numerator / denominator;
            String pageFlow = fromPage + "_" + toPage;

            System.out.printf("[页面转化率] %s: numerator=%d denominator=%d rate=%.4f%n",
                    pageFlow, numerator, denominator, rate);

            batch.add(new Object[]{taskId, pageFlow, rate});
        }

        // ----------------------------------------------------------------
        // 5. 写入 MySQL
        // ----------------------------------------------------------------
        if (!batch.isEmpty()) {
            String sql = "INSERT INTO page_convert_rate (task_id, page_flow, convert_rate) "
                       + "VALUES (?,?,?) "
                       + "ON DUPLICATE KEY UPDATE convert_rate=VALUES(convert_rate)";
            JDBCHelper.executeBatch(sql, batch);
        }

        System.out.println("[页面转化率] 完成! 写入 " + batch.size() + " 条转化率数据");
    }
}
