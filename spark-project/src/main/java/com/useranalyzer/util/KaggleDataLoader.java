package com.useranalyzer.util;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * Kaggle 真实电商数据加载器
 *
 * 数据集: eCommerce behavior data from multi-category store
 * 来源: https://www.kaggle.com/datasets/mkechinov/ecommerce-behavior-data-from-multi-category-store
 *
 * Kaggle 原始字段:
 *   event_time, event_type, product_id, category_id, category_code, brand, price, user_id, user_session
 *
 * 转换规则:
 *   event_time  -> date (yyyy-MM-dd) + action_time (yyyy-MM-dd HH:mm:ss, 去掉 " UTC")
 *   user_id     -> user_id
 *   user_session-> session_id
 *   product_id  -> page_id / click_product_id / order_product_ids / pay_product_ids
 *   category_id -> click_category_id / order_category_ids / pay_category_ids
 *                  (映射到 1-50 范围: ABS(category_id) % 50 + 1，便于与任务过滤参数兼容)
 *   event_type  -> view=点击, cart=下单, purchase=支付
 *   无 search_keyword / city 数据 -> 留空或默认值
 *
 * user_info 处理:
 *   Kaggle 数据无用户画像，根据 user_id 哈希值合成年龄、职业、城市、性别
 *   使得与任务参数的用户过滤条件兼容
 *
 * 注意事项:
 *   - 2019-Oct.csv 约 42M 行 / 9GB，建议配置 data.kaggle.sample < 1.0 进行采样
 *   - 推荐使用 task_id=2（仅日期过滤）运行分析
 */
public class KaggleDataLoader {

    public static void prepare(JavaSparkContext sc, String kagglePath, double sampleRate) {

        System.out.println("[KaggleLoader] 开始加载 Kaggle 数据...");
        System.out.println("[KaggleLoader] 文件路径: " + kagglePath);
        System.out.println("[KaggleLoader] 采样率: " + sampleRate);

        SparkSession spark = SparkSession.builder()
                .sparkContext(sc.sc())
                .getOrCreate();

        // ----------------------------------------------------------------
        // 1. 读取 Kaggle 原始 CSV
        // ----------------------------------------------------------------
        Dataset<Row> raw = spark.read()
                .option("header", "true")
                .option("encoding", "UTF-8")
                .csv(kagglePath);

        // 采样（大文件时降低数据量）
        if (sampleRate > 0.0 && sampleRate < 1.0) {
            raw = raw.sample(false, sampleRate, 42L);
            System.out.println("[KaggleLoader] 采样后注册原始视图...");
        }

        // 缓存采样结果：让后续所有查询（uva、user_info、各分析模块）都从内存读取，
        // 避免每次触发 action 时重新扫描整个 9GB CSV 文件。
        raw = raw.cache();
        raw.createOrReplaceTempView("kaggle_raw");

        long rawCount = raw.count(); // 同时触发 cache 物化，只扫一次文件
        System.out.println("[KaggleLoader] 原始行数（已缓存）: " + rawCount);

        // ----------------------------------------------------------------
        // 2. 转换为 UVA 格式，注册 uva 视图
        //
        //    category_id 映射: 原始 bigint → (ABS(category_id) % 50 + 1)
        //    将 Kaggle 的大整数类目ID映射到 1-50，与任务参数 categoryIds="1,2,3,4,5" 兼容
        //
        //    event_time 格式 "2019-10-01 00:00:00 UTC"
        //      -> date:        substr(trim(event_time), 1, 10)  = "2019-10-01"
        //      -> action_time: substr(trim(event_time), 1, 19)  = "2019-10-01 00:00:00"
        // ----------------------------------------------------------------
        spark.sql(
            "SELECT " +
            "  substr(trim(event_time), 1, 10) AS date, " +
            "  CAST(user_id AS BIGINT) AS user_id, " +
            "  trim(user_session) AS session_id, " +
            "  ABS(CAST(COALESCE(product_id, '0') AS BIGINT)) % 200 + 1 AS page_id, " +
            "  substr(trim(event_time), 1, 19) AS action_time, " +
            "  '' AS search_keyword, " +
            "  CASE WHEN trim(event_type)='view' " +
            "    THEN COALESCE(NULLIF(trim(category_code),''), CAST(ABS(CAST(COALESCE(category_id,'0') AS BIGINT)) % 50 + 1 AS STRING)) " +
            "    ELSE NULL END AS click_category_id, " +
            "  CASE WHEN trim(event_type)='view' " +
            "    THEN ABS(CAST(COALESCE(product_id,'0') AS BIGINT)) % 200 + 1 " +
            "    ELSE -1 END AS click_product_id, " +
            "  CASE WHEN trim(event_type)='cart' " +
            "    THEN COALESCE(NULLIF(trim(category_code),''), CAST(ABS(CAST(COALESCE(category_id,'0') AS BIGINT)) % 50 + 1 AS STRING)) " +
            "    ELSE '' END AS order_category_ids, " +
            "  CASE WHEN trim(event_type)='cart' " +
            "    THEN CAST(ABS(CAST(COALESCE(product_id,'0') AS BIGINT)) % 200 + 1 AS STRING) " +
            "    ELSE '' END AS order_product_ids, " +
            "  CASE WHEN trim(event_type)='purchase' " +
            "    THEN COALESCE(NULLIF(trim(category_code),''), CAST(ABS(CAST(COALESCE(category_id,'0') AS BIGINT)) % 50 + 1 AS STRING)) " +
            "    ELSE '' END AS pay_category_ids, " +
            "  CASE WHEN trim(event_type)='purchase' " +
            "    THEN CAST(ABS(CAST(COALESCE(product_id,'0') AS BIGINT)) % 200 + 1 AS STRING) " +
            "    ELSE '' END AS pay_product_ids, " +
            "  ABS(CAST(COALESCE(user_id,'0') AS BIGINT)) % 10 + 1 AS city_id " +
            "FROM kaggle_raw " +
            "WHERE user_id IS NOT NULL AND event_time IS NOT NULL AND trim(event_time) != ''"
        ).createOrReplaceTempView("uva");

        long uvaCount = spark.sql("SELECT COUNT(*) FROM uva").first().getLong(0);
        System.out.println("[KaggleLoader] UVA 视图行数: " + uvaCount);

        // ----------------------------------------------------------------
        // 3. 合成 user_info 视图
        //    基于 user_id 哈希值生成年龄、职业、城市、性别
        //    10 种职业各约 10%，10 个城市各约 10%，男女各 50%
        // ----------------------------------------------------------------
        spark.sql(
            "SELECT DISTINCT " +
            "  CAST(user_id AS BIGINT) AS user_id, " +
            "  CONCAT('kaggle_', user_id) AS username, " +
            "  CONCAT('user_', user_id) AS name, " +
            "  ABS(HASH(user_id)) % 50 + 18 AS age, " +
            "  CASE (ABS(HASH(CONCAT('p', user_id))) % 10) " +
            "    WHEN 0 THEN '学生' WHEN 1 THEN '白领' WHEN 2 THEN '工人' WHEN 3 THEN '自由职业' " +
            "    WHEN 4 THEN '教师' WHEN 5 THEN '医生' WHEN 6 THEN '商人' WHEN 7 THEN '程序员' " +
            "    WHEN 8 THEN '设计师' ELSE '运营' END AS professional, " +
            "  CASE (ABS(HASH(CONCAT('c', user_id))) % 10) " +
            "    WHEN 0 THEN '北京' WHEN 1 THEN '上海' WHEN 2 THEN '深圳' WHEN 3 THEN '广州' " +
            "    WHEN 4 THEN '杭州' WHEN 5 THEN '成都' WHEN 6 THEN '武汉' WHEN 7 THEN '南京' " +
            "    WHEN 8 THEN '重庆' ELSE '西安' END AS city, " +
            "  CASE (ABS(HASH(CONCAT('s', user_id))) % 2) " +
            "    WHEN 0 THEN 'male' ELSE 'female' END AS sex " +
            "FROM kaggle_raw " +
            "WHERE user_id IS NOT NULL AND trim(user_id) != ''"
        ).createOrReplaceTempView("user_info");

        long userCount = spark.sql("SELECT COUNT(*) FROM user_info").first().getLong(0);
        System.out.println("[KaggleLoader] user_info 视图行数 (合成): " + userCount);
        System.out.println("[KaggleLoader] 数据加载完成");
    }
}
