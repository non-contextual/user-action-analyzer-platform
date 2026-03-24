package com.useranalyzer.spark.category;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.constant.Constants;
import com.useranalyzer.util.JDBCHelper;
import com.useranalyzer.util.ParamUtils;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 热门品类 Top10 分析（基于 DataFrame/SQL 实现，避免 Lambda 序列化问题）
 *
 * 计算逻辑：
 *   统计每个品类的点击次数、下单次数、支付次数
 *   按 (点击次数 DESC, 下单次数 DESC, 支付次数 DESC) 排序取 Top 10
 *   写入 MySQL top10_category 表
 */
public class Top10CategoryAnalyze {

    public static void analyze(JavaSparkContext sc, long taskId, JSONObject taskParam,
                               String userVisitActionPath) {

        System.out.println("[Top10品类] taskId=" + taskId + " 开始...");

        String startDate = ParamUtils.getParam(taskParam, Constants.PARAM_START_DATE);
        String endDate   = ParamUtils.getParam(taskParam, Constants.PARAM_END_DATE);

        // ----------------------------------------------------------------
        // 1. 创建 SparkSession（共享已有 SparkContext）
        // ----------------------------------------------------------------
        SparkSession spark = SparkSession.builder()
                .sparkContext(sc.sc())
                .getOrCreate();

        // ----------------------------------------------------------------
        // 2. 加载数据：若视图已由 KaggleDataLoader 预注册则跳过文件读取
        // ----------------------------------------------------------------
        if (!spark.catalog().tableExists("uva")) {
            spark.read()
                    .option("header", "true")
                    .option("encoding", "UTF-8")
                    .csv(userVisitActionPath)
                    .createOrReplaceTempView("uva");
        }
        Dataset<Row> df = spark.table("uva");

        // ----------------------------------------------------------------
        // 3. 按日期范围过滤
        //    注意：使用独立视图 uva_top10，避免覆盖原始 uva 视图
        //          （SESSION 任务中 PageConvertRate 仍需访问原始 uva）
        // ----------------------------------------------------------------
        if (startDate != null && endDate != null) {
            df = df.filter(
                df.col("date").$greater$eq(startDate)
                .and(df.col("date").$less$eq(endDate))
            );
        }
        df.createOrReplaceTempView("uva_top10");

        System.out.println("[Top10品类] 行为数据行数: " + df.count());

        // ----------------------------------------------------------------
        // 4. 点击次数统计
        // ----------------------------------------------------------------
        spark.sql(
            "SELECT click_category_id AS cat_id, count(*) AS click_count " +
            "FROM uva_top10 " +
            "WHERE click_category_id IS NOT NULL AND trim(click_category_id) != '' " +
            "GROUP BY click_category_id"
        ).createOrReplaceTempView("click_counts");

        // ----------------------------------------------------------------
        // 5. 下单次数统计（展开逗号分隔的 order_category_ids）
        // ----------------------------------------------------------------
        spark.sql(
            "SELECT trim(cat) AS cat_id, count(*) AS order_count FROM (" +
            "  SELECT explode(split(order_category_ids, ',')) AS cat " +
            "  FROM uva_top10 WHERE order_category_ids IS NOT NULL AND order_category_ids != ''" +
            ") WHERE trim(cat) != '' GROUP BY trim(cat)"
        ).createOrReplaceTempView("order_counts");

        // ----------------------------------------------------------------
        // 6. 支付次数统计（展开逗号分隔的 pay_category_ids）
        // ----------------------------------------------------------------
        spark.sql(
            "SELECT trim(cat) AS cat_id, count(*) AS pay_count FROM (" +
            "  SELECT explode(split(pay_category_ids, ',')) AS cat " +
            "  FROM uva_top10 WHERE pay_category_ids IS NOT NULL AND pay_category_ids != ''" +
            ") WHERE trim(cat) != '' GROUP BY trim(cat)"
        ).createOrReplaceTempView("pay_counts");

        // ----------------------------------------------------------------
        // 7. Full Outer Join 三个结果 + 排序取 Top10
        // ----------------------------------------------------------------
        List<Row> top10 = spark.sql(
            "SELECT " +
            "  COALESCE(c.cat_id, o.cat_id, p.cat_id) AS category_id, " +
            "  COALESCE(c.click_count, 0) AS click_count, " +
            "  COALESCE(o.order_count, 0) AS order_count, " +
            "  COALESCE(p.pay_count,   0) AS pay_count " +
            "FROM click_counts c " +
            "FULL OUTER JOIN order_counts o ON c.cat_id = o.cat_id " +
            "FULL OUTER JOIN pay_counts p   ON COALESCE(c.cat_id, o.cat_id) = p.cat_id " +
            "ORDER BY click_count DESC, order_count DESC, pay_count DESC " +
            "LIMIT 10"
        ).collectAsList();

        // ----------------------------------------------------------------
        // 8. 打印并写入 MySQL
        // ----------------------------------------------------------------
        System.out.println("[Top10品类] 结果（Top" + top10.size() + "）:");
        for (int i = 0; i < top10.size(); i++) {
            Row row = top10.get(i);
            System.out.printf("  #%d  categoryId=%-4s  click=%-6s  order=%-6s  pay=%s%n",
                    i + 1, row.get(0), row.get(1), row.get(2), row.get(3));
        }

        writeToMySQL(taskId, top10);
        System.out.println("[Top10品类] 结果已写入 MySQL top10_category 表");
    }

    private static void writeToMySQL(long taskId, List<Row> top10) {
        JDBCHelper.executeUpdate("DELETE FROM top10_category WHERE task_id=?", taskId);

        String sql = "INSERT INTO top10_category (task_id, category_id, click_count, order_count, pay_count) "
                   + "VALUES (?,?,?,?,?)";
        List<Object[]> batch = new ArrayList<>();
        for (Row row : top10) {
            if (row.get(0) != null) {
                Object catId  = row.get(0);
                Object click  = row.get(1);
                Object order  = row.get(2);
                Object pay    = row.get(3);
                batch.add(new Object[]{
                    taskId,
                    catId.toString(),
                    click instanceof Long  ? (Long) click  : Long.parseLong(click.toString()),
                    order instanceof Long  ? (Long) order  : Long.parseLong(order.toString()),
                    pay   instanceof Long  ? (Long) pay    : Long.parseLong(pay.toString())
                });
            }
        }
        if (!batch.isEmpty()) {
            JDBCHelper.executeBatch(sql, batch);
        }
    }
}
