package com.useranalyzer;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.spark.category.Top10CategoryAnalyze;
import com.useranalyzer.spark.page.PageConvertRate;
import com.useranalyzer.spark.session.RandomSessionExtract;
import com.useranalyzer.spark.session.UserVisitSessionAnalyze;
import com.useranalyzer.util.JDBCHelper;
import com.useranalyzer.util.KaggleDataLoader;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.InputStream;
import java.util.Properties;

/**
 * 电商用户行为分析大数据平台 - 主入口
 *
 * 用法: spark-submit ... com.useranalyzer.UserActionAnalyzerApp <taskId>
 *
 * 任务类型路由（由 MySQL task.task_type 决定）:
 *   SESSION → Session聚合统计 + 热门品类Top10 + 页面单跳转化率（若有 targetPageFlow）
 *   TOP10   → 热门品类 Top10
 *   RANDOM  → 随机抽取 Session
 */
public class UserActionAnalyzerApp {

    public static void main(String[] args) throws Exception {
        // ----------------------------------------------------------------
        // 0. 参数检查
        // ----------------------------------------------------------------
        if (args.length < 1) {
            System.err.println("用法: UserActionAnalyzerApp <taskId>");
            System.exit(1);
        }
        long taskId = Long.parseLong(args[0]);
        System.out.println("===== 电商用户行为分析平台 =====");
        System.out.println("taskId = " + taskId);

        // ----------------------------------------------------------------
        // 1. 读取配置
        // ----------------------------------------------------------------
        Properties config = new Properties();
        InputStream is = UserActionAnalyzerApp.class.getClassLoader()
                .getResourceAsStream("config.properties");
        config.load(is);

        String dataFormat          = config.getProperty("data.format", "generated");
        String userVisitActionPath = config.getProperty("data.user_visit_action",
                "/opt/data/user_visit_action.csv");
        String userInfoPath        = config.getProperty("data.user_info",
                "/opt/data/user_info.csv");
        String kagglePath          = config.getProperty("data.kaggle.path",
                "/opt/data/2019-Oct.csv");
        double kaggleSample        = Double.parseDouble(
                config.getProperty("data.kaggle.sample", "1.0"));

        System.out.println("数据格式: " + dataFormat);
        System.out.println("数据路径: " + ("kaggle".equalsIgnoreCase(dataFormat) ? kagglePath : userVisitActionPath));

        // ----------------------------------------------------------------
        // 2. 从 MySQL 读取任务参数和任务类型
        // ----------------------------------------------------------------
        String taskType      = JDBCHelper.getTaskType(taskId);
        String taskParamJson = JDBCHelper.getTaskParam(taskId);
        System.out.println("任务类型: " + taskType);
        System.out.println("任务参数: " + taskParamJson);
        JSONObject taskParam = JSONObject.parseObject(taskParamJson);

        // ----------------------------------------------------------------
        // 3. 创建 SparkContext
        // ----------------------------------------------------------------
        SparkConf conf = new SparkConf()
                .setAppName("UserActionAnalyzer-task" + taskId)
                .set("spark.driver.extraJavaOptions",
                     "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8")
                .set("spark.executor.extraJavaOptions",
                     "-Dfile.encoding=UTF-8");
        if (!conf.contains("spark.master")) {
            conf.setMaster("local[*]");
        }
        JavaSparkContext sc = new JavaSparkContext(conf);
        sc.setLogLevel("WARN");

        try {
            // ----------------------------------------------------------------
            // 4. 更新任务状态为 RUNNING
            // ----------------------------------------------------------------
            JDBCHelper.executeUpdate(
                "UPDATE task SET status='RUNNING' WHERE task_id=?", taskId);

            // ----------------------------------------------------------------
            // 4.5 Kaggle 模式：预加载并转换数据（所有任务类型共用）
            // ----------------------------------------------------------------
            if ("kaggle".equalsIgnoreCase(dataFormat)) {
                System.out.println("\n----- [0/N] Kaggle 数据加载 -----");
                KaggleDataLoader.prepare(sc, kagglePath, kaggleSample);
            }

            // ----------------------------------------------------------------
            // 5. 按任务类型路由
            // ----------------------------------------------------------------
            switch (taskType) {

                case "SESSION":
                    System.out.println("\n----- [1/3] Session 聚合统计 -----");
                    UserVisitSessionAnalyze.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);

                    System.out.println("\n----- [2/3] 热门品类 Top10 -----");
                    Top10CategoryAnalyze.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);

                    System.out.println("\n----- [3/3] 页面单跳转化率 -----");
                    PageConvertRate.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);
                    break;

                case "TOP10":
                    System.out.println("\n----- [1/1] 热门品类 Top10 -----");
                    Top10CategoryAnalyze.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);
                    break;

                case "RANDOM":
                    System.out.println("\n----- [1/1] 随机抽取 Session -----");
                    RandomSessionExtract.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);
                    break;

                default:
                    System.err.println("[WARN] 未知任务类型: " + taskType + "，按 SESSION 处理");
                    UserVisitSessionAnalyze.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);
                    Top10CategoryAnalyze.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);
                    PageConvertRate.analyze(sc, taskId, taskParam,
                            userVisitActionPath, userInfoPath);
                    break;
            }

            // ----------------------------------------------------------------
            // 6. 任务完成
            // ----------------------------------------------------------------
            JDBCHelper.executeUpdate(
                "UPDATE task SET status='FINISHED', finish_time=NOW() WHERE task_id=?", taskId);
            System.out.println("\n===== 分析完成！taskId=" + taskId + " =====");

        } catch (Exception e) {
            JDBCHelper.executeUpdate(
                "UPDATE task SET status='FAILED' WHERE task_id=?", taskId);
            System.err.println("任务执行失败: " + e.getMessage());
            throw e;
        } finally {
            sc.stop();
        }
    }
}
