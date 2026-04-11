package com.useranalyzer.spark.association;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.util.JDBCHelper;
import com.useranalyzer.util.ParamUtils;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.ml.fpm.FPGrowth;
import org.apache.spark.ml.fpm.FPGrowthModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品关联规则挖掘（FP-Growth）
 *
 * 取舍说明：
 *   - Kaggle 数据每行为单条事件，不是天然的"购物篮"数据。
 *     Session 粒度的篮子极稀疏（大多数 session 购买 0-1 件），会导致 support 极低。
 *     因此选择**用户粒度**：每个用户历史购买过的所有品类组成一个篮子，
 *     保证篮子内平均有多件商品，support 有实际意义。
 *   - 使用 category_code（如 "electronics.smartphone"）而非 product_id，
 *     品类级别的规则更有业务可解释性（避免规则过于稀疏且无业务含义）。
 *   - 只保留 purchase 类型事件，cart/view 事件噪音大不纳入关联分析。
 *
 * 算法流程：
 *   1. 从 kaggle_raw 提取 purchase 事件，按 user_id 聚合得到品类列表（basket）
 *   2. 过滤掉只有 1 种品类的用户（无法形成关联规则）
 *   3. 用 Spark MLlib FPGrowth 挖掘频繁项集和关联规则
 *   4. 取置信度最高的 Top-20 规则写入 MySQL
 *
 * 输出表: product_association (task_id, antecedent, consequent, support, confidence, lift)
 */
public class ProductAssociationAnalyze {

    public static void analyze(JavaSparkContext sc, long taskId, JSONObject taskParam) {

        System.out.println("[关联分析] taskId=" + taskId + " 开始...");

        double minSupport    = taskParam.containsKey("minSupport")    ? taskParam.getDoubleValue("minSupport")    : 0.01;
        double minConfidence = taskParam.containsKey("minConfidence") ? taskParam.getDoubleValue("minConfidence") : 0.3;

        System.out.println("[关联分析] minSupport=" + minSupport + " minConfidence=" + minConfidence);

        SparkSession spark = SparkSession.builder()
                .sparkContext(sc.sc())
                .getOrCreate();

        boolean kaggleMode = spark.catalog().tableExists("kaggle_raw");
        if (!kaggleMode) {
            System.out.println("[关联分析] 未检测到 kaggle_raw 视图，跳过关联分析（需要 Kaggle 模式）");
            return;
        }

        // ----------------------------------------------------------------
        // 1. 构建用户购物篮：每个用户购买过的所有品类集合
        //    使用 collect_set 去重，同一品类多次购买只计一次
        //    过滤掉 category_code 为空的行（用数字 category_id 补充）
        // ----------------------------------------------------------------
        Dataset<Row> basketDF = spark.sql(
            "SELECT user_id, " +
            "  collect_set(COALESCE(NULLIF(trim(category_code),''), " +
            "              CAST(ABS(CAST(COALESCE(category_id,'0') AS BIGINT)) % 50 + 1 AS STRING))) AS items " +
            "FROM kaggle_raw " +
            "WHERE trim(event_type) = 'purchase' " +
            "  AND user_id IS NOT NULL AND trim(user_id) != ''" +
            " GROUP BY user_id" +
            " HAVING size(items) >= 2"  // 至少购买过 2 种品类才能产生关联规则
        );

        long basketCount = basketDF.count();
        System.out.println("[关联分析] 有效购物篮数（购买≥2品类的用户）: " + basketCount);

        if (basketCount < 10) {
            System.out.println("[关联分析] 购物篮数量过少（< 10），跳过 FP-Growth");
            return;
        }

        // ----------------------------------------------------------------
        // 2. 运行 FP-Growth
        // ----------------------------------------------------------------
        FPGrowth fpg = new FPGrowth()
                .setItemsCol("items")
                .setMinSupport(minSupport)
                .setMinConfidence(minConfidence);

        FPGrowthModel model = fpg.fit(basketDF);

        Dataset<Row> rules = model.associationRules();
        long ruleCount = rules.count();
        System.out.println("[关联分析] 发现关联规则数: " + ruleCount);

        if (ruleCount == 0) {
            System.out.println("[关联分析] 未发现满足阈值的关联规则，建议降低 minSupport 或 minConfidence");
            return;
        }

        // ----------------------------------------------------------------
        // 3. 取置信度最高的 Top-20 规则
        // ----------------------------------------------------------------
        List<Row> topRules = rules
                .orderBy(rules.col("confidence").desc(), rules.col("lift").desc())
                .limit(20)
                .collectAsList();

        System.out.println("[关联分析] Top-20 关联规则:");
        for (int i = 0; i < topRules.size(); i++) {
            Row row = topRules.get(i);
            System.out.printf("  #%-2d  %s => %s  conf=%.3f  lift=%.3f  support=%.4f%n",
                    i + 1,
                    row.getList(0),
                    row.getList(1),
                    ((Number) row.get(2)).doubleValue(),
                    ((Number) row.get(3)).doubleValue(),
                    ((Number) row.get(4)).doubleValue());
        }

        // ----------------------------------------------------------------
        // 4. 写入 MySQL
        // ----------------------------------------------------------------
        writeToMySQL(taskId, topRules);
        System.out.println("[关联分析] 结果已写入 MySQL product_association 表");
    }

    private static void writeToMySQL(long taskId, List<Row> rules) {
        JDBCHelper.executeUpdate("DELETE FROM product_association WHERE task_id=?", taskId);

        String sql = "INSERT INTO product_association " +
                     "(task_id, antecedent, consequent, support, confidence, lift) VALUES (?,?,?,?,?,?)";
        List<Object[]> batch = new ArrayList<>();
        for (Row row : rules) {
            // antecedent / consequent 是 List<String>，转逗号分隔字符串
            List<String> ant = row.getList(0);
            List<String> con = row.getList(1);
            String antStr = ant.stream().collect(Collectors.joining(","));
            String conStr = con.stream().collect(Collectors.joining(","));
            double confidence = ((Number) row.get(2)).doubleValue();
            double lift       = ((Number) row.get(3)).doubleValue();
            double support    = ((Number) row.get(4)).doubleValue();

            // 截断防止超长字符串超出 VARCHAR(500)
            if (antStr.length() > 490) antStr = antStr.substring(0, 490);
            if (conStr.length() > 490) conStr = conStr.substring(0, 490);

            batch.add(new Object[]{taskId, antStr, conStr, support, confidence, lift});
        }
        if (!batch.isEmpty()) {
            JDBCHelper.executeBatch(sql, batch);
        }
    }
}
