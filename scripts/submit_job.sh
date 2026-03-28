#!/bin/bash
# ============================================================
# Spark 作业提交脚本
# 用法: ./submit_job.sh <jar文件名> <taskId>
# 示例: ./submit_job.sh user-analyzer-1.0-SNAPSHOT.jar 1
# ============================================================

set -e

JAR_NAME="${1:-user-analyzer-1.0-SNAPSHOT.jar}"
MAIN_CLASS="com.useranalyzer.UserActionAnalyzerApp"
TASK_ID="${2:-1}"
JAR_PATH="/opt/spark-apps/${JAR_NAME}"

if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] JAR 文件不存在: $JAR_PATH"
    echo "请将编译好的 JAR 放入 ./spark-apps/ 目录"
    exit 1
fi

echo "=== 提交 Spark 作业 ==="
echo "  JAR: $JAR_PATH"
echo "  主类: $MAIN_CLASS"
echo "  任务ID: $TASK_ID"

# 杀掉残留的旧 driver 进程，防止重启时双 driver 爆内存
echo "--- 清理残留 Spark 进程 ---"
pkill -f 'SparkSubmit' 2>/dev/null || true
pkill -f 'UserActionAnalyzerApp' 2>/dev/null || true
# 等待 Spark Master 注销旧应用（最多 30s）
for i in $(seq 1 15); do
    RUNNING=$(curl -sf http://localhost:8080/api/v1/applications 2>/dev/null \
        | grep -c '"state":"RUNNING"' || echo 0)
    if [ "$RUNNING" = "0" ]; then break; fi
    echo "  等待旧作业退出... ($i/15)"
    sleep 2
done

${SPARK_HOME}/bin/spark-submit \
    --master spark://spark-master:7077 \
    --deploy-mode client \
    --driver-memory 1g \
    --executor-memory 1g \
    --executor-cores 1 \
    --num-executors 1 \
    --conf "spark.driver.extraJavaOptions=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8" \
    --conf "spark.executor.extraJavaOptions=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8" \
    --class "$MAIN_CLASS" \
    "$JAR_PATH" \
    "$TASK_ID"
